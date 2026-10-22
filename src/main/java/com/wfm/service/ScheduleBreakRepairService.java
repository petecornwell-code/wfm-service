package com.wfm.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.Schedule;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Timeslot;
import com.wfm.model.TimeslotDemandConfig;
import com.wfm.util.DayWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Post-solve break repair — moves an agent-day's break from an over-full break band to another band
 * of the same shift, moving the agent's seats with it, so the agent's worked hours are unchanged.
 *
 * <p><b>Why this exists.</b> Vinted (P&A), 2026-10-22: with First-Shift's 11:00 band capped at 15,
 * a 74-minute solve settled at -6 hard, every point of it "Band capacity" — 16-17 First-Shift
 * breaks at 11:00 on five days while the 13:00 band sat nearly empty with roughly double the cover
 * it needed. An earlier run with no cap settled at -2 hard for the mirror-image reason: 18 breaks
 * bunched at 11:00 on Sunday left that hour below the under-allocation floor.
 *
 * <p><b>Why the solver does not find it.</b> Moving one break is three coordinated changes: the
 * agent-day's band, plus vacating the seat the agent holds in the new break hour, plus taking a
 * seat in the old break hour. A band change on its own seats the agent inside their own break (an
 * envelope violation) and leaves a hole where the old break was (a contiguity violation), so every
 * single-variable step towards the fix scores worse than standing still, and the search never
 * crosses that valley. {@link ScheduleEnvelopeRepairService} does not cover it either: it only
 * moves seats within a fixed envelope, never the band.
 *
 * <p><b>Safety.</b> Same contract as the other two post-solve repairs: it runs on a settled
 * solution, checks only the cheap necessary conditions itself (same template, same break length so
 * net hours cannot change, the target band is eligible for this agent-day, a free seat the agent
 * qualifies for exists in every slot the old break frees up), and lets the real constraint provider
 * decide everything else by re-scoring. A move is kept only if the hard score strictly improves;
 * otherwise it is undone exactly. The schedule's hard score can only improve or stay where the
 * solver left it.
 */
@Service
public class ScheduleBreakRepairService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleBreakRepairService.class);

    /**
     * Re-score budget for the whole repair. A re-score of a large schedule costs on the order of a
     * second, and this runs while the schedule reads FINISHING, so the work is bounded. The happy
     * path (every planned move verified as one batch) costs two re-scores.
     */
    private static final int MAX_RESCORES = 40;

    /** What a repair did, for logging and for the caller. */
    public record RepairResult(int candidatesFound, int breaksMoved, int rescores) {
        public boolean changedAnything() {
            return breaksMoved > 0;
        }
    }

    private static final RepairResult NOTHING = new RepairResult(0, 0, 0);

    /** A band on a business date — the unit band capacity is counted per. */
    private record DateBand(LocalDate date, UUID templateId, UUID bandId) {}

    /**
     * One break move: the agent-day's pair changes from {@code from} to {@code to}; the agent gives up
     * {@code vacate} (seats inside the new break) and takes {@code take} (free seats inside the old
     * break).
     */
    private record Move(AgentShiftAssignment row, Agent agent, ShiftBandPair from, ShiftBandPair to,
                        List<AgentAssignment> vacate, List<AgentAssignment> take) {}

    /**
     * Repairs {@code schedule} in place, verifying by re-score. A SLOT-mode schedule, or one with no
     * shift rows, is returned untouched.
     *
     * @param rescore recalculates and returns the schedule's score; typically
     *                {@code sol -> solutionManager.update(sol)}
     */
    public RepairResult repairVerified(Schedule schedule, Function<Schedule, HardSoftScore> rescore) {
        if (schedule.getSchedulingMode() != SchedulingMode.SHIFT
                || schedule.getShiftAssignments() == null || schedule.getShiftAssignments().isEmpty()) {
            return NOTHING;
        }
        DayWindow window = DayWindow.anchoredAt(schedule.getScheduleConfig().dayStart());
        State state = State.of(schedule);

        List<AgentShiftAssignment> candidates = findCandidates(schedule, state, window);
        if (candidates.isEmpty()) {
            return NOTHING;
        }
        log.info("Break repair — {} agent-day(s) in an over-capacity band or with a break in an "
                + "under-staffed hour", candidates.size());

        // Pass 1: plan one move per candidate against shared bookkeeping, apply them together, and
        // verify with a single re-score — the common case.
        List<Move> batch = new ArrayList<>();
        for (AgentShiftAssignment row : candidates) {
            Move move = planMove(row, schedule, state, window);
            if (move != null) {
                state.record(move);
                batch.add(move);
            }
        }
        if (batch.isEmpty()) {
            log.info("Break repair — no band with spare room and free seats for any candidate; "
                    + "leaving the solver's result untouched");
            return new RepairResult(candidates.size(), 0, 0);
        }

        HardSoftScore before = rescore.apply(schedule);
        batch.forEach(ScheduleBreakRepairService::apply);
        HardSoftScore after = rescore.apply(schedule);
        int rescores = 2;
        if (after.hardScore() > before.hardScore()) {
            log.info("Break repair kept — {} break(s) moved, score {} -> {}", batch.size(), before, after);
            return new RepairResult(candidates.size(), batch.size(), rescores);
        }

        // Pass 2: the batch as a whole did not pay. Undo it and retry one move at a time, so one bad
        // move cannot cost the good ones.
        for (int i = batch.size() - 1; i >= 0; i--) {
            undo(batch.get(i));
        }
        state = State.of(schedule);
        log.info("Break repair — batch of {} did not improve hard score ({} -> {}); retrying move by move",
                batch.size(), before.hardScore(), after.hardScore());

        HardSoftScore current = rescore.apply(schedule);
        rescores++;
        int moved = 0;
        for (AgentShiftAssignment row : candidates) {
            if (rescores >= MAX_RESCORES) {
                log.info("Break repair — stopping at the {}-rescore budget", MAX_RESCORES);
                break;
            }
            Move move = planMove(row, schedule, state, window);
            if (move == null) {
                continue;
            }
            apply(move);
            HardSoftScore trial = rescore.apply(schedule);
            rescores++;
            if (trial.hardScore() > current.hardScore()) {
                current = trial;
                state.record(move);
                moved++;
            } else {
                undo(move);
            }
        }

        // Unconditional final re-score: the loop may have ended on an undone trial, and the score
        // the caller stores must describe the schedule's actual contents.
        HardSoftScore finalScore = rescore.apply(schedule);
        rescores++;
        if (moved == 0) {
            log.info("Break repair — no single move improved the hard score; schedule unchanged at {}", finalScore);
        } else {
            log.info("Break repair kept — {}/{} break(s) moved, score {} -> {}",
                    moved, candidates.size(), before, finalScore);
        }
        return new RepairResult(candidates.size(), moved, rescores);
    }

    /**
     * Agent-days worth trying: the excess members of an over-capacity band (only as many as the
     * excess, latest first by id for determinism), plus every agent-day whose break sits in an hour
     * already below the under-allocation floor. Ordered deterministically.
     */
    private List<AgentShiftAssignment> findCandidates(Schedule schedule, State state, DayWindow window) {
        Map<DateBand, List<AgentShiftAssignment>> byBand = new HashMap<>();
        for (AgentShiftAssignment row : schedule.getShiftAssignments()) {
            if (row.getAgent() == null || row.getShiftBandPair() == null || row.getShiftBandPair().band() == null) {
                continue;
            }
            byBand.computeIfAbsent(key(row.getDate(), row.getShiftBandPair()), k -> new ArrayList<>()).add(row);
        }

        Set<AgentShiftAssignment> picked = new HashSet<>();
        List<AgentShiftAssignment> out = new ArrayList<>();
        Comparator<AgentShiftAssignment> byId = Comparator.comparing(r -> r.getId().toString());

        for (List<AgentShiftAssignment> rows : byBand.values()) {
            Integer capacity = rows.get(0).getShiftBandPair().band().getCapacity();
            if (capacity != null && rows.size() > capacity) {
                rows.sort(byId);
                for (AgentShiftAssignment r : rows.subList(capacity, rows.size())) {
                    if (picked.add(r)) out.add(r);
                }
            }
        }
        for (List<AgentShiftAssignment> rows : byBand.values()) {
            for (AgentShiftAssignment r : rows) {
                if (!picked.contains(r) && breakSlots(r.getShiftBandPair(), r.getDate(), state, window)
                        .stream().anyMatch(state::belowFloor)) {
                    picked.add(r);
                    out.add(r);
                }
            }
        }
        out.sort(Comparator.comparing(AgentShiftAssignment::getDate).thenComparing(byId));
        return out;
    }

    /**
     * The best legal alternative band for {@code row}, or null. Alternatives come from the row's own
     * eligible pairs (so ENFORCE-mode narrowing and every other eligibility rule still hold): same
     * template, a different band of the same duration, with room under its capacity. For each, the
     * agent must hold a seat in every slot the new break covers (those seats are vacated) and a free
     * seat they qualify for must exist in every slot the old break frees up. Among those, the band
     * whose break hours have the most cover above the under-allocation floor wins.
     */
    private Move planMove(AgentShiftAssignment row, Schedule schedule, State state, DayWindow window) {
        ShiftBandPair from = row.getShiftBandPair();
        Agent agent = row.getAgent();
        if (from == null || from.band() == null || agent == null) {
            return null;
        }
        LocalDate date = row.getDate();
        List<Timeslot> oldBreak = breakSlots(from, date, state, window);

        Move best = null;
        int bestHeadroom = Integer.MIN_VALUE;
        for (ShiftBandPair to : Objects.requireNonNullElse(row.getEligibleShiftBandPairs(), List.<ShiftBandPair>of())) {
            ShiftTemplateBreakBand band = to.band();
            boolean sameTemplate = to.template().getId().equals(from.template().getId());
            if (band == null || !sameTemplate
                    || band.getId().equals(from.band().getId())
                    || !Objects.equals(band.getDurationMinutes(), from.band().getDurationMinutes())) {
                continue;   // only another break of the same length in the same shift keeps net hours
            }
            Integer capacity = band.getCapacity();
            if (capacity != null && state.bandCount(key(date, to)) >= capacity) {
                continue;
            }
            List<Timeslot> newBreak = breakSlots(to, date, state, window);
            if (newBreak.isEmpty()) {
                continue;
            }

            List<AgentAssignment> vacate = new ArrayList<>();
            boolean ok = true;
            for (Timeslot ts : newBreak) {
                if (oldBreak.contains(ts)) continue;          // overlap: stays a break either way
                AgentAssignment seat = state.seatOf(agent.getId(), ts);
                if (seat == null) { ok = false; break; }      // agent not working there now
                vacate.add(seat);
            }
            List<AgentAssignment> take = new ArrayList<>();
            Set<AgentAssignment> claimed = new HashSet<>();
            for (Timeslot ts : oldBreak) {
                if (!ok) break;
                if (newBreak.contains(ts)) continue;
                AgentAssignment free = state.freeSeat(ts, agent, claimed);
                if (free == null) { ok = false; break; }
                claimed.add(free);
                take.add(free);
            }
            // Worked hours must be unchanged: exactly as many seats taken in the old break as given up
            // in the new one. Also refuses a move whose old break frees nothing (no timeslot there),
            // which would otherwise just cost the agent hours.
            if (!ok || take.isEmpty() || take.size() != vacate.size()) {
                continue;
            }
            int headroom = newBreak.stream().mapToInt(state::headroom).min().orElse(0);
            if (headroom > bestHeadroom) {
                bestHeadroom = headroom;
                best = new Move(row, agent, from, to, vacate, take);
            }
        }
        return best;
    }

    /** The timeslots on {@code date} that fall inside the envelope but are excluded by the break. */
    private static List<Timeslot> breakSlots(ShiftBandPair pair, LocalDate date, State state, DayWindow window) {
        ShiftBandPair noBreak = new ShiftBandPair(pair.template(), null);
        List<Timeslot> out = new ArrayList<>();
        for (Timeslot ts : state.slotsOn(date)) {
            if (noBreak.covers(ts, window) && !pair.covers(ts, window)) {
                out.add(ts);
            }
        }
        return out;
    }

    private static DateBand key(LocalDate date, ShiftBandPair pair) {
        return new DateBand(date, pair.template().getId(), pair.band().getId());
    }

    private static void apply(Move move) {
        move.row().setShiftBandPair(move.to());
        move.vacate().forEach(s -> s.setAgent(null));
        move.take().forEach(s -> s.setAgent(move.agent()));
    }

    private static void undo(Move move) {
        move.take().forEach(s -> s.setAgent(null));
        move.vacate().forEach(s -> s.setAgent(move.agent()));
        move.row().setShiftBandPair(move.from());
    }

    /** Mirrors {@code ScheduleConstraintProvider.specializationMatch}: primary or any secondary. */
    private static boolean qualifies(Agent agent, AgentAssignment seat) {
        if (seat.getRequiredSpecialization() == null) return true;
        UUID required = seat.getRequiredSpecialization().getId();
        if (agent.getPrimarySpecialization() != null && agent.getPrimarySpecialization().getId().equals(required)) {
            return true;
        }
        return agent.getSecondarySpecializations().stream().anyMatch(s -> s.getId().equals(required));
    }

    /**
     * Bookkeeping over the settled schedule, keyed by BUSINESS date throughout (the key both the shift
     * rows and the timeslots carry): timeslots per date, each agent's seat per timeslot, free seats
     * per timeslot, seated counts and under-allocation floors per timeslot, and agent-days per band.
     * {@link #record} keeps it true as pass-1 moves are planned, so two moves never claim the same
     * free seat or overfill the same band.
     */
    private static final class State {
        private final Map<LocalDate, List<Timeslot>> slotsByDate = new HashMap<>();
        private final Map<UUID, Map<UUID, AgentAssignment>> seatByAgentSlot = new HashMap<>();
        private final Map<UUID, List<AgentAssignment>> freeBySlot = new HashMap<>();
        private final Map<UUID, Integer> seatedBySlot = new HashMap<>();
        private final Map<UUID, Integer> floorBySlot = new HashMap<>();
        private final Map<DateBand, Integer> bandCounts = new HashMap<>();

        static State of(Schedule schedule) {
            State s = new State();
            // Timeslots come from the schedule itself, not from the seats: an hour with no seat at
            // all must still be known, or a break there would look like it frees nothing.
            Set<UUID> seenSlots = new HashSet<>();
            if (schedule.getTimeslots() != null) {
                for (Timeslot ts : schedule.getTimeslots()) {
                    if (seenSlots.add(ts.getId())) {
                        s.slotsByDate.computeIfAbsent(ts.getBusinessDate(), k -> new ArrayList<>()).add(ts);
                    }
                }
            }
            for (AgentAssignment a : schedule.getAssignments()) {
                Timeslot ts = a.getTimeslot();
                if (ts == null) continue;
                if (seenSlots.add(ts.getId())) {
                    s.slotsByDate.computeIfAbsent(ts.getBusinessDate(), k -> new ArrayList<>()).add(ts);
                }
                if (a.getAgent() == null) {
                    s.freeBySlot.computeIfAbsent(ts.getId(), k -> new ArrayList<>()).add(a);
                } else {
                    s.seatByAgentSlot.computeIfAbsent(a.getAgent().getId(), k -> new HashMap<>()).put(ts.getId(), a);
                    s.seatedBySlot.merge(ts.getId(), 1, Integer::sum);
                }
            }
            int pct = schedule.getUnderallocationHardLimitPct();
            if (schedule.getTimeslotDemandConfigs() != null) {
                for (TimeslotDemandConfig d : schedule.getTimeslotDemandConfigs()) {
                    // Same integer arithmetic as ScheduleConstraintProvider's "Bulk under-allocation hard".
                    s.floorBySlot.put(d.timeslot().getId(), d.totalDemandFTEs() * pct / 100);
                }
            }
            for (AgentShiftAssignment row : schedule.getShiftAssignments()) {
                if (row.getAgent() != null && row.getShiftBandPair() != null && row.getShiftBandPair().band() != null) {
                    s.bandCounts.merge(key(row.getDate(), row.getShiftBandPair()), 1, Integer::sum);
                }
            }
            return s;
        }

        List<Timeslot> slotsOn(LocalDate date) {
            return slotsByDate.getOrDefault(date, List.of());
        }

        AgentAssignment seatOf(UUID agentId, Timeslot ts) {
            return seatByAgentSlot.getOrDefault(agentId, Map.of()).get(ts.getId());
        }

        AgentAssignment freeSeat(Timeslot ts, Agent agent, Set<AgentAssignment> exclude) {
            for (AgentAssignment f : freeBySlot.getOrDefault(ts.getId(), List.of())) {
                if (!exclude.contains(f) && qualifies(agent, f)) return f;
            }
            return null;
        }

        int bandCount(DateBand band) {
            return bandCounts.getOrDefault(band, 0);
        }

        int headroom(Timeslot ts) {
            return seatedBySlot.getOrDefault(ts.getId(), 0) - floorBySlot.getOrDefault(ts.getId(), 0);
        }

        boolean belowFloor(Timeslot ts) {
            return headroom(ts) < 0;
        }

        void record(Move m) {
            LocalDate date = m.row().getDate();
            bandCounts.merge(key(date, m.from()), -1, Integer::sum);
            bandCounts.merge(key(date, m.to()), 1, Integer::sum);
            Map<UUID, AgentAssignment> mine = seatByAgentSlot.computeIfAbsent(m.agent().getId(), k -> new HashMap<>());
            for (AgentAssignment v : m.vacate()) {
                UUID slot = v.getTimeslot().getId();
                mine.remove(slot);
                freeBySlot.computeIfAbsent(slot, k -> new ArrayList<>()).add(v);
                seatedBySlot.merge(slot, -1, Integer::sum);
            }
            for (AgentAssignment t : m.take()) {
                UUID slot = t.getTimeslot().getId();
                mine.put(slot, t);
                freeBySlot.getOrDefault(slot, new ArrayList<>()).remove(t);
                seatedBySlot.merge(slot, 1, Integer::sum);
            }
        }
    }
}
