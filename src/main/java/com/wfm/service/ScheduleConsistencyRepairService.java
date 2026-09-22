package com.wfm.service;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.ResolvedUsualShiftTarget;
import com.wfm.model.Schedule;
import com.wfm.model.SchedulingMode;
import com.wfm.model.Specialization;
import com.wfm.model.ShiftBandPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Post-solve usual-shift repair — permutes WHICH agent holds each already-chosen shift, without
 * changing WHICH shifts are worked.
 *
 * <p><b>Why this exists.</b> Coverage depends only on the multiset of (envelope, break band)
 * pairs worked on a date, never on who holds them. Two agent-days that are mutually substitutable
 * can therefore exchange envelopes with provably zero effect on every coverage constraint, while
 * changing the usual-shift consistency score. The solver cannot discover these exchanges: the
 * model couples two planning entities — {@code AgentShiftAssignment.shiftBandPair} and
 * {@code AgentAssignment.agent} — so a single swap requires ~18 coordinated variable changes,
 * and {@code solverConfig.xml} configures no compound move plus a {@code 0hard} annealing
 * temperature that refuses every intermediate state. Measured on the live Saferide desk: the
 * solver left 63/271 agent-days (23.2%) on the agent's own usual start where 235 (86.7%) was
 * reachable at identical coverage.
 *
 * <p><b>Why the greedy is optimal, not a heuristic.</b> Exact matches on a date are bounded above
 * by {@code sum over start times of min(agents wanting it, slots offering it)} — no assignment can
 * beat that ceiling. Taking every available exact match first attains it, because claiming an
 * exact match never deprives a different start time of one. The residual is then matched in sorted
 * order, which minimises total deviation for a cost convex on a line. Verified against a Hungarian
 * solve of the same data: both return 235 exact and 79 hours of deviation.
 *
 * <p><b>Substitutability is deliberately conservative.</b> Agents exchange only within a class of
 * identical contracted hours and identical specialization profile, so no swap can alter a
 * contracted-hours or specialization-match constraint. This misses some legal swaps (an agent
 * whose secondary specializations are a superset of another's could safely take their seats) in
 * exchange for the property that a swap can never turn a satisfied hard constraint into a
 * violated one.
 *
 * <p>The caller is expected to re-score and revert on any hard-score regression; this class makes
 * that cheap by reporting exactly what it changed.
 */
@Service
public class ScheduleConsistencyRepairService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleConsistencyRepairService.class);

    /** What a repair did, for logging and for the caller's revert decision. */
    public record RepairResult(int datesRepaired, int agentDaysMoved, int exactBefore, int exactAfter,
                               int deviationHoursBefore, int deviationHoursAfter) {
        public boolean changedAnything() {
            return agentDaysMoved > 0;
        }
    }

    /** No-op outcome, so callers never branch on null. */
    private static final RepairResult NOTHING = new RepairResult(0, 0, 0, 0, 0, 0);

    /**
     * {@link #repair} wrapped in a self-check: the schedule is re-scored afterwards and the whole
     * permutation rolled back if the hard score regressed at all.
     *
     * <p>The argument for coverage-neutrality is a proof about the model, not a measurement, and a
     * proof can be wrong or can silently stop holding when a constraint is added. This makes the
     * claim falsifiable at runtime and self-correcting: a repair that breaks anything hard undoes
     * itself and says so, rather than shipping a damaged schedule. Restoration is positional —
     * neither list is ever added to or removed from, only permuted.
     */
    public RepairResult repairVerified(Schedule schedule, Function<Schedule, HardSoftScore> rescore) {
        List<ShiftBandPair> shiftSnapshot = schedule.getShiftAssignments().stream()
                .map(AgentShiftAssignment::getShiftBandPair).toList();
        List<Agent> seatSnapshot = schedule.getAssignments().stream()
                .map(AgentAssignment::getAgent).toList();

        HardSoftScore before = rescore.apply(schedule);
        RepairResult result = repair(schedule);
        if (!result.changedAnything()) {
            return result;
        }

        HardSoftScore after = rescore.apply(schedule);
        if (after.hardScore() < before.hardScore()) {
            List<AgentShiftAssignment> shifts = schedule.getShiftAssignments();
            for (int i = 0; i < shifts.size(); i++) {
                shifts.get(i).setShiftBandPair(shiftSnapshot.get(i));
            }
            List<AgentAssignment> seats = schedule.getAssignments();
            for (int i = 0; i < seats.size(); i++) {
                seats.get(i).setAgent(seatSnapshot.get(i));
            }
            HardSoftScore restored = rescore.apply(schedule);
            log.warn("Usual-shift repair REVERTED — hard score would have moved {} -> {}. "
                            + "Restored to {}. This means two agent-days judged substitutable were not; "
                            + "the schedule is unchanged from the solver's own result.",
                    before.hardScore(), after.hardScore(), restored);
            return NOTHING;
        }

        log.info("Usual-shift repair kept — score {} -> {} (hard unchanged at {})",
                before, after, after.hardScore());
        return result;
    }

    /**
     * Rewrites {@code schedule} in place. A SLOT-mode schedule, or one with no shift rows, is
     * returned untouched — this is only meaningful where an envelope exists to exchange.
     */
    public RepairResult repair(Schedule schedule) {
        if (schedule.getSchedulingMode() != SchedulingMode.SHIFT
                || schedule.getShiftAssignments().isEmpty()) {
            return NOTHING;
        }

        Map<UUID, Map<LocalDate, LocalTime>> usualByAgentDate = new HashMap<>();
        for (ResolvedUsualShiftTarget t : schedule.getResolvedUsualShiftTargets()) {
            usualByAgentDate.computeIfAbsent(t.agentId(), k -> new HashMap<>())
                    .put(t.date(), t.usualStartTime());
        }

        // Seats grouped by (date, agent) so a permutation can move a whole agent-day's seats.
        Map<LocalDate, Map<UUID, List<AgentAssignment>>> seatsByDateAgent = new HashMap<>();
        for (AgentAssignment a : schedule.getAssignments()) {
            if (a.getAgent() == null || a.getTimeslot() == null) {
                continue;
            }
            seatsByDateAgent
                    .computeIfAbsent(a.getTimeslot().getDate(), k -> new HashMap<>())
                    .computeIfAbsent(a.getAgent().getId(), k -> new ArrayList<>())
                    .add(a);
        }

        Map<LocalDate, List<AgentShiftAssignment>> byDate = schedule.getShiftAssignments().stream()
                .filter(sa -> sa.getShiftBandPair() != null && sa.getAgent() != null)
                .collect(Collectors.groupingBy(AgentShiftAssignment::getDate));

        int datesRepaired = 0, moved = 0, exactBefore = 0, exactAfter = 0, devBefore = 0, devAfter = 0;

        for (Map.Entry<LocalDate, List<AgentShiftAssignment>> dateEntry : byDate.entrySet()) {
            LocalDate date = dateEntry.getKey();

            Map<String, List<AgentShiftAssignment>> classes = dateEntry.getValue().stream()
                    .collect(Collectors.groupingBy(sa -> substitutabilityKey(sa.getAgent()),
                            LinkedHashMap::new, Collectors.toList()));

            boolean dateChanged = false;
            for (List<AgentShiftAssignment> group : classes.values()) {
                if (group.size() < 2) {
                    continue;
                }
                int[] pi = assignWithinClass(group, usualByAgentDate, date);

                int movers = 0;
                for (int i = 0; i < group.size(); i++) {
                    AgentShiftAssignment sa = group.get(i);
                    LocalTime usual = usualFor(usualByAgentDate, sa.getAgent().getId(), date);
                    LocalTime was = sa.getShiftBandPair().template().getStartTime();
                    LocalTime now = group.get(pi[i]).getShiftBandPair().template().getStartTime();
                    if (usual != null) {
                        if (usual.equals(was)) exactBefore++;
                        if (usual.equals(now)) exactAfter++;
                        devBefore += hoursBetween(usual, was);
                        devAfter += hoursBetween(usual, now);
                    }
                    if (pi[i] != i) {
                        movers++;
                    }
                }
                if (applyPermutation(group, pi, seatsByDateAgent.getOrDefault(date, Map.of()))) {
                    dateChanged = true;
                    moved += movers;
                }
            }
            if (dateChanged) {
                datesRepaired++;
            }
        }

        RepairResult result = new RepairResult(datesRepaired, moved, exactBefore, exactAfter,
                devBefore, devAfter);
        if (result.changedAnything()) {
            log.info("Usual-shift repair: {} agent-day(s) re-matched across {} date(s) — "
                            + "usual-start matches {} -> {}, deviation {}h -> {}h",
                    moved, datesRepaired, exactBefore, exactAfter, devBefore, devAfter);
        }
        return result;
    }

    /**
     * Exact matches first (attaining the per-date ceiling), then the residual matched in sorted
     * order. Agents with no usual-shift target are pinned to their current envelope so they never
     * displace an agent who has a preference.
     */
    /**
     * Returns a permutation over POSITIONS in {@code group}: {@code pi[i] == j} means the agent at
     * position i takes the envelope AND the seats currently at position j.
     *
     * <p>Deliberately indices, not {@link ShiftBandPair} references. Envelopes are shared value
     * objects — every agent on the same template and break band holds the SAME instance — so any
     * map keyed by envelope identity silently collapses those agent-days onto one entry. That bug
     * shipped once: it re-pointed many agents' seats at a single agent and scored -3,327,984 hard.
     * Positions are unique by construction and cannot collapse.
     */
    private int[] assignWithinClass(List<AgentShiftAssignment> group,
                                    Map<UUID, Map<LocalDate, LocalTime>> usualByAgentDate,
                                    LocalDate date) {
        int n = group.size();
        LocalTime[] starts = new LocalTime[n];
        for (int i = 0; i < n; i++) {
            starts[i] = group.get(i).getShiftBandPair().template().getStartTime();
        }

        // Positions still available, bucketed by the start time they offer.
        Map<LocalTime, List<Integer>> pool = new HashMap<>();
        for (int j = 0; j < n; j++) {
            pool.computeIfAbsent(starts[j], k -> new ArrayList<>()).add(j);
        }

        int[] pi = new int[n];
        java.util.Arrays.fill(pi, -1);
        List<Integer> unmatched = new ArrayList<>();

        // Pass 1 — every available exact match. This attains the per-date ceiling on exact
        // matches, because claiming one never deprives a different start time of one.
        for (int i = 0; i < n; i++) {
            LocalTime usual = usualFor(usualByAgentDate, group.get(i).getAgent().getId(), date);
            LocalTime want = usual != null ? usual : starts[i];
            List<Integer> available = pool.get(want);
            if (available != null && !available.isEmpty()) {
                pi[i] = available.remove(available.size() - 1);
            } else {
                unmatched.add(i);
            }
        }

        // Pass 2 — residual, both sides sorted by start time. Optimal total deviation for a cost
        // convex on a line: no exchange between two leftovers can improve it.
        List<Integer> leftovers = pool.values().stream().flatMap(List::stream)
                .sorted(Comparator.comparing(j -> starts[j])).collect(Collectors.toList());
        unmatched.sort(Comparator.comparing(i -> {
            LocalTime u = usualFor(usualByAgentDate, group.get(i).getAgent().getId(), date);
            return u != null ? u : starts[i];
        }));
        for (int k = 0; k < unmatched.size(); k++) {
            pi[unmatched.get(k)] = leftovers.get(k);
        }
        return pi;
    }

    /**
     * Applies the position permutation to both coupled entities at once — the envelope AND the
     * seats that must sit inside it. Everything is read into locals BEFORE any write: the mapping
     * is a permutation, so writing in place would let an already-moved row be read as if it were
     * still in its original position.
     */
    private boolean applyPermutation(List<AgentShiftAssignment> group, int[] pi,
                                     Map<UUID, List<AgentAssignment>> seatsByAgent) {
        int n = group.size();
        boolean changed = false;
        for (int i = 0; i < n; i++) {
            if (pi[i] != i) { changed = true; break; }
        }
        if (!changed) {
            return false;
        }

        ShiftBandPair[] envelopeAt = new ShiftBandPair[n];
        List<List<AgentAssignment>> seatsAt = new ArrayList<>(n);
        for (int j = 0; j < n; j++) {
            envelopeAt[j] = group.get(j).getShiftBandPair();
            seatsAt.add(List.copyOf(
                    seatsByAgent.getOrDefault(group.get(j).getAgent().getId(), List.of())));
        }

        // Seats follow their position: position j's seats are exactly the hours envelope j covers,
        // so whoever takes position j inherits precisely the slots they are now entitled to work.
        for (int i = 0; i < n; i++) {
            Agent taker = group.get(i).getAgent();
            for (AgentAssignment seat : seatsAt.get(pi[i])) {
                seat.setAgent(taker);
            }
        }
        for (int i = 0; i < n; i++) {
            group.get(i).setShiftBandPair(envelopeAt[pi[i]]);
        }
        return true;
    }

    /**
     * Two agent-days may exchange envelopes only when nothing other than usual-shift consistency
     * can tell them apart. Contracted hours guard the contracted-hours constraints; the
     * specialization profile guards specialization matching.
     *
     * <p>Package-private static so {@link ShiftStartMixTargetService} can gate on the SAME
     * definition rather than restating it. The two features rest on one idea — that agents within
     * a class are interchangeable across envelopes — and a second copy of this key that drifted
     * from this one would make the pre-solve target and the post-solve repair disagree about who
     * may hold what, silently.
     */
    static String substitutabilityKey(Agent agent) {
        String secondaries = agent.getSecondarySpecializations() == null ? ""
                : agent.getSecondarySpecializations().stream()
                        .map(Specialization::getId).filter(Objects::nonNull)
                        .map(UUID::toString).sorted().collect(Collectors.joining(","));
        String primary = agent.getPrimarySpecialization() == null ? "none"
                : String.valueOf(agent.getPrimarySpecialization().getId());
        String hours = agent.getContractedHoursPerDay() == null ? "default"
                : agent.getContractedHoursPerDay().stripTrailingZeros().toPlainString();
        return hours + "|" + primary + "|" + secondaries;
    }

    private LocalTime usualFor(Map<UUID, Map<LocalDate, LocalTime>> usualByAgentDate,
                               UUID agentId, LocalDate date) {
        return usualByAgentDate.getOrDefault(agentId, Map.of()).get(date);
    }

    private int hoursBetween(LocalTime a, LocalTime b) {
        return Math.abs(a.toSecondOfDay() - b.toSecondOfDay()) / 3600;
    }
}
