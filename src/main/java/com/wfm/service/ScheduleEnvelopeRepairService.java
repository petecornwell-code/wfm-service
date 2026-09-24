package com.wfm.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.Schedule;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
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
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Post-solve shift-envelope repair — moves a seat that falls OUTSIDE its agent-day's envelope
 * onto a free seat INSIDE it, one-for-one, leaving the agent's worked hours unchanged.
 *
 * <p><b>Why this exists.</b> Live solves on both the Saferide and Vinted desks settle at a
 * residual {@code -1..-3} hard whose violations are all the same shape: an agent seated in their
 * own break hour (or otherwise outside their envelope) while a legal, unworked slot sits free in
 * that same envelope on that same day. On Vinted, 2026-09-24: Viktoriia Kudla, Sun 27 Sep,
 * envelope 15:00-00:00, seated at 19:00 which is her own break band, with 5 free seats at 23:00.
 * A three-hour solve never took the one move that fixes it.
 *
 * <p><b>The move IS in the solver's move set — that was checked, not assumed.</b>
 * {@code solverConfig.xml} configures no {@code <moveSelector>}, so
 * {@code DefaultLocalSearchPhaseFactory.determineDefaultMoveSelectorConfig} builds
 * {@code Union(ChangeMoveSelector, SwapMoveSelector)}; {@code FromSolutionEntitySelector} extracts
 * every entity with no assigned/unassigned filter, and {@code SwapMove.isMoveDoable} needs only
 * that the two values differ, which {@code null} and an {@link Agent} do. {@code agentRange} is a
 * solution-level value range, so the entity-dependent range check there does not apply. The
 * required assigned-to-unassigned swap is therefore generated, and
 * {@code SimulatedAnnealingAcceptor.isAccepted} returns true unconditionally for any move scoring
 * {@code >= } the last step, so a hard-improving swap is ALWAYS accepted whatever the weight scale.
 *
 * <p><b>So the cause is sampling, not weights and not time.</b> With ~23 000 seat entities the
 * swap neighbourhood is ~2.7e8 moves and the union draws swaps about half the time, so the handful
 * of pairs that repair a given violation are sampled with probability ~1e-8 per draw. Three hours
 * of local search buys roughly a coin-flip chance of ever trying one. Raising
 * {@code shiftEnvelopeComplianceWeight} cannot help: the move is already improving and already
 * accepted. This class simply enumerates the repair directly instead of waiting for a lottery.
 *
 * <p><b>Why it is safe to do post-solve.</b> This operates on a SETTLED solution, which is the
 * distinction that made {@link ScheduleConsistencyRepairService} sound and the parked
 * {@code AgentDaySwapMove} unsound mid-search: nothing is in flux, so the seat set an agent-day
 * holds is exactly what it will ship with. The move itself is deliberately minimal — one agent
 * gives up one seat and takes one other seat on the same date, so worked hours are unchanged by
 * construction and no contracted-hours constraint can move.
 *
 * <p><b>Every applied move is verified by re-scoring, and kept only if the hard score strictly
 * improves.</b> That is a stronger guarantee than a coverage-neutrality proof: the candidate
 * filter below checks only the cheap, obviously-necessary conditions (the envelope covers the
 * target slot, the agent holds the required specialization, the agent is not already seated in
 * that timeslot), and lets the real constraint provider adjudicate everything else —
 * {@code minimumStaffing}, {@code bulkOverallocationLimit}, {@code agentDayOff},
 * {@code shiftWorkContiguity} and the rest. A move that breaks any of them fails the re-score and
 * is rolled back. The schedule's hard score can therefore only improve or stay where the solver
 * left it, never regress.
 */
@Service
public class ScheduleEnvelopeRepairService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleEnvelopeRepairService.class);

    /**
     * Candidates tried per violation on the fallback path, and the ceiling on how many re-scores
     * the whole repair may spend. A re-score of a 24 000-entity schedule is not free (order of a
     * second), and this runs inside the solver's final-best-solution consumer while the operator
     * waits, so the work is bounded rather than exhaustive. The happy path costs exactly ONE
     * re-score regardless of violation count.
     */
    private static final int MAX_CANDIDATES_PER_VIOLATION = 3;
    private static final int MAX_RESCORES = 32;

    /** What a repair did, for logging and for the caller. */
    public record RepairResult(int violationsFound, int violationsRepaired, int rescores) {
        public boolean changedAnything() {
            return violationsRepaired > 0;
        }
    }

    private static final RepairResult NOTHING = new RepairResult(0, 0, 0);

    /** An agent-day. The unit both the envelope and the seat set are keyed by. */
    private record AgentDay(UUID agentId, LocalDate date) {}

    /** One seat-to-seat move: {@code from} is vacated, {@code to} takes the agent. */
    private record Move(AgentAssignment from, AgentAssignment to, Agent agent) {}

    /**
     * Repairs {@code schedule} in place, verifying by re-score. A SLOT-mode schedule, or one with
     * no shift rows, is returned untouched — there is no envelope to comply with.
     *
     * @param rescore recalculates and returns the schedule's score; typically
     *                {@code sol -> solutionManager.update(sol)}
     */
    public RepairResult repairVerified(Schedule schedule, Function<Schedule, HardSoftScore> rescore) {
        if (schedule.getSchedulingMode() != SchedulingMode.SHIFT
                || schedule.getShiftAssignments().isEmpty()) {
            return NOTHING;
        }

        Map<AgentDay, ShiftBandPair> envelopes = new HashMap<>();
        for (AgentShiftAssignment sa : schedule.getShiftAssignments()) {
            if (sa.getAgent() != null && sa.getShiftBandPair() != null) {
                envelopes.put(new AgentDay(sa.getAgent().getId(), sa.getDate()), sa.getShiftBandPair());
            }
        }
        if (envelopes.isEmpty()) {
            return NOTHING;
        }

        // Free seats by date, and the timeslot ids each agent-day already occupies. Both are
        // maintained as moves are applied, so a later violation never targets a seat an earlier
        // repair just took.
        Map<LocalDate, List<AgentAssignment>> freeByDate = new HashMap<>();
        Map<AgentDay, Set<UUID>> occupied = new HashMap<>();
        List<AgentAssignment> violations = new ArrayList<>();

        for (AgentAssignment a : schedule.getAssignments()) {
            if (a.getTimeslot() == null) {
                continue;
            }
            if (a.getAgent() == null) {
                freeByDate.computeIfAbsent(a.getTimeslot().getDate(), k -> new ArrayList<>()).add(a);
                continue;
            }
            AgentDay key = new AgentDay(a.getAgent().getId(), a.getTimeslot().getDate());
            occupied.computeIfAbsent(key, k -> new HashSet<>()).add(a.getTimeslot().getId());
            ShiftBandPair pair = envelopes.get(key);
            // A null pair is a violation the constraint counts too, but it is not one this repair
            // can fix: with no envelope there is no legal slot to move the seat to.
            if (pair != null && !pair.covers(a.getTimeslot())) {
                violations.add(a);
            }
        }

        if (violations.isEmpty()) {
            return NOTHING;
        }

        // Deterministic order, so the same schedule always repairs the same way.
        violations.sort(Comparator
                .comparing((AgentAssignment a) -> a.getTimeslot().getDate())
                .thenComparing(a -> a.getTimeslot().getStartTime())
                .thenComparing(a -> a.getId().toString()));

        log.info("Envelope repair — {} seat(s) outside their agent-day envelope", violations.size());

        // Pass 1: one best candidate per violation, applied together and verified with a single
        // re-score. This is the case that normally holds, and it costs one recalculation.
        List<Move> batch = new ArrayList<>();
        for (AgentAssignment v : violations) {
            Candidates scan = candidatesFor(v, envelopes, freeByDate, occupied);
            if (scan.seats().isEmpty()) {
                // Logged HERE and only here: pass 1 sees every violation exactly once, so this
                // reports each unfixable one without pass 2 repeating it.
                logNoCandidate(v, scan);
                continue;
            }
            batch.add(planMove(v, scan.seats().get(0), freeByDate, occupied));
        }
        if (batch.isEmpty()) {
            log.info("Envelope repair — no legal free seat for any of the {} violation(s); "
                    + "leaving the solver's result untouched", violations.size());
            return new RepairResult(violations.size(), 0, 0);
        }

        HardSoftScore before = rescore.apply(schedule);
        batch.forEach(this::apply);
        HardSoftScore after = rescore.apply(schedule);
        int rescores = 2;

        if (after.hardScore() > before.hardScore()) {
            log.info("Envelope repair kept — {} seat(s) moved inside their envelope, score {} -> {}",
                    batch.size(), before, after);
            return new RepairResult(violations.size(), batch.size(), rescores);
        }

        // Pass 2: the batch as a whole did not pay, which means at least one of its moves breaks
        // something the cheap filter does not model. Undo it and re-try one move at a time so a
        // single bad candidate cannot cost the good ones.
        batch.forEach(this::undo);
        restoreBookkeeping(batch, freeByDate, occupied);
        log.info("Envelope repair — batch of {} did not improve hard score ({} -> {}); "
                + "retrying move by move", batch.size(), before.hardScore(), after.hardScore());

        HardSoftScore current = rescore.apply(schedule);
        rescores++;
        int repaired = 0;

        for (AgentAssignment v : violations) {
            if (rescores >= MAX_RESCORES) {
                log.info("Envelope repair — stopping at the {}-rescore budget", MAX_RESCORES);
                break;
            }
            List<AgentAssignment> candidates = candidatesFor(v, envelopes, freeByDate, occupied).seats();
            int tried = 0;
            for (AgentAssignment c : candidates) {
                if (tried >= MAX_CANDIDATES_PER_VIOLATION || rescores >= MAX_RESCORES) {
                    break;
                }
                tried++;
                Move move = planMove(v, c, freeByDate, occupied);
                apply(move);
                HardSoftScore trial = rescore.apply(schedule);
                rescores++;
                if (trial.hardScore() > current.hardScore()) {
                    current = trial;
                    repaired++;
                    break;
                }
                undo(move);
                restoreBookkeeping(List.of(move), freeByDate, occupied);
            }
        }

        // One last recalculation, unconditionally: the loop above may have ended on a rolled-back
        // trial, which would leave the score the caller stores describing a move that is no longer
        // there. This makes the schedule's own score field true of its actual contents again.
        HardSoftScore finalScore = rescore.apply(schedule);
        rescores++;

        if (repaired == 0) {
            // Everything was rolled back; the schedule is the solver's own result, seat for seat.
            log.info("Envelope repair — no single move improved the hard score; schedule unchanged "
                    + "from the solver's result at {}", finalScore);
        } else {
            log.info("Envelope repair kept — {}/{} seat(s) moved inside their envelope, "
                    + "score {} -> {}", repaired, violations.size(), before, finalScore);
        }
        return new RepairResult(violations.size(), repaired, rescores);
    }

    /**
     * The outcome of scanning a date's free seats for one violation: the usable ones, best first,
     * plus a tally of why each of the others was passed over. The tally exists so that a violation
     * this repair CANNOT fix says so in the log with a reason — without it, an empty candidate list
     * is indistinguishable from a filter that is simply too strict, which is exactly the ambiguity
     * the first live run on Vinted left behind (2 of 12 violations had no candidate and no
     * explanation).
     */
    private record Candidates(List<AgentAssignment> seats, int freeOnDate, int notCovered,
                              int alreadySeated, int wrongSpecialization) {}

    /**
     * Free seats on the violation's own date that the agent could legally take instead, best
     * first. "Best" is the slot with the most free seats left on it — the hungriest hour — which
     * both puts the seat where coverage wants it and keeps the move clear of the bulk
     * overallocation ceiling. Only the cheap conditions are checked here; the re-score decides.
     *
     * <p>Rejections are classified in priority order, one reason per seat, so the counts sum to
     * the seats considered.
     */
    private Candidates candidatesFor(AgentAssignment violation,
            Map<AgentDay, ShiftBandPair> envelopes,
            Map<LocalDate, List<AgentAssignment>> freeByDate,
            Map<AgentDay, Set<UUID>> occupied) {
        Agent agent = violation.getAgent();
        LocalDate date = violation.getTimeslot().getDate();
        AgentDay key = new AgentDay(agent.getId(), date);
        ShiftBandPair pair = envelopes.get(key);
        List<AgentAssignment> free = freeByDate.get(date);
        if (pair == null || free == null || free.isEmpty()) {
            return new Candidates(List.of(), free == null ? 0 : free.size(), 0, 0, 0);
        }
        Set<UUID> taken = occupied.getOrDefault(key, Set.of());

        Map<UUID, Long> freePerSlot = new HashMap<>();
        for (AgentAssignment f : free) {
            freePerSlot.merge(f.getTimeslot().getId(), 1L, Long::sum);
        }

        List<AgentAssignment> out = new ArrayList<>();
        int notCovered = 0, alreadySeated = 0, wrongSpec = 0;
        for (AgentAssignment f : free) {
            if (!pair.covers(f.getTimeslot())) {
                notCovered++;
            } else if (taken.contains(f.getTimeslot().getId())) {
                alreadySeated++;
            } else if (!qualifies(agent, f)) {
                wrongSpec++;
            } else {
                out.add(f);
            }
        }
        out.sort(Comparator
                .comparingLong((AgentAssignment f) -> -freePerSlot.getOrDefault(f.getTimeslot().getId(), 0L))
                .thenComparing(f -> f.getTimeslot().getStartTime())
                .thenComparing(f -> f.getId().toString()));
        return new Candidates(out, free.size(), notCovered, alreadySeated, wrongSpec);
    }

    /**
     * Says, for one violation this repair cannot touch, exactly which filter emptied the list.
     * "No free seat on the date at all" means the schedule is saturated and only a three-way swap
     * could help; "every free seat is outside the envelope" or "the agent already works that hour"
     * point at the shape of the day instead; a non-zero specialization count means the seat exists
     * but the agent cannot fill it.
     */
    private void logNoCandidate(AgentAssignment violation, Candidates scan) {
        log.info("Envelope repair — no legal free seat for agent {} on {} at {} (envelope seat at "
                        + "{}): {} free seat(s) on the date, of which {} outside the envelope or in "
                        + "the break band, {} at an hour the agent already works, {} requiring a "
                        + "specialization the agent does not hold",
                violation.getAgent().getId(), violation.getTimeslot().getDate(),
                violation.getTimeslot().getStartTime(), violation.getTimeslot().getStartTime(),
                scan.freeOnDate(), scan.notCovered(), scan.alreadySeated(),
                scan.wrongSpecialization());
    }

    /** Mirrors {@code ScheduleConstraintProvider.specializationMatch}: primary or any secondary. */
    private boolean qualifies(Agent agent, AgentAssignment seat) {
        UUID required = seat.getRequiredSpecialization().getId();
        if (agent.getPrimarySpecialization() != null
                && agent.getPrimarySpecialization().getId().equals(required)) {
            return true;
        }
        return agent.getSecondarySpecializations().stream()
                .anyMatch(s -> s.getId().equals(required));
    }

    /**
     * Records a move against the free-seat and occupancy bookkeeping and returns it. The
     * bookkeeping is updated here rather than in {@link #apply} so that pass 1 can plan a whole
     * batch without two violations ever claiming the same free seat.
     */
    private Move planMove(AgentAssignment from, AgentAssignment to,
            Map<LocalDate, List<AgentAssignment>> freeByDate,
            Map<AgentDay, Set<UUID>> occupied) {
        Agent agent = from.getAgent();
        LocalDate date = from.getTimeslot().getDate();
        AgentDay key = new AgentDay(agent.getId(), date);
        List<AgentAssignment> free = freeByDate.get(date);
        free.remove(to);
        free.add(from);
        Set<UUID> slots = occupied.computeIfAbsent(key, k -> new HashSet<>());
        slots.remove(from.getTimeslot().getId());
        slots.add(to.getTimeslot().getId());
        return new Move(from, to, agent);
    }

    private void apply(Move move) {
        move.from().setAgent(null);
        move.to().setAgent(move.agent());
    }

    private void undo(Move move) {
        move.to().setAgent(null);
        move.from().setAgent(move.agent());
    }

    /** Puts the free-seat and occupancy maps back the way {@link #planMove} found them. */
    private void restoreBookkeeping(List<Move> moves,
            Map<LocalDate, List<AgentAssignment>> freeByDate,
            Map<AgentDay, Set<UUID>> occupied) {
        for (Move move : moves) {
            LocalDate date = move.from().getTimeslot().getDate();
            AgentDay key = new AgentDay(move.agent().getId(), date);
            List<AgentAssignment> free = freeByDate.get(date);
            free.remove(move.from());
            free.add(move.to());
            Set<UUID> slots = occupied.computeIfAbsent(key, k -> new HashSet<>());
            slots.remove(move.to().getTimeslot().getId());
            slots.add(move.from().getTimeslot().getId());
        }
    }
}
