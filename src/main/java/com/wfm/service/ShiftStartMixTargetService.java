package com.wfm.service;

import com.wfm.model.Agent;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.ResolvedUsualShiftTarget;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftStartMixTarget;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Pre-solve choice of the shift-start mix — how many agent-days start at each template start time
 * on each date, decided before the solver runs.
 *
 * <p><b>Why this is decided up front.</b> The multiset of envelopes worked on a date is the single
 * decision that fixes both how well the day is covered and how many agents can possibly work their
 * usual start. The solver cannot revise it: envelope and seats are coupled planning entities, so
 * moving one agent-day to a different start means re-pointing its {@code shiftBandPair} AND all
 * ~9 seats that fill it, and {@code solverConfig.xml}'s {@code 0hard} annealing temperature
 * refuses every intermediate state. Whatever the construction heuristic picks therefore stands for
 * the entire solve, and it picks blind.
 *
 * <p><b>Measured cost of picking blind</b> (live Saferide desk, accepted schedule
 * {@code 97579464}, 31 Aug–4 Sep 2026, 61 agents in one substitutability class):
 *
 * <pre>
 *                                    solver's mix      best mix
 *   usual-shift exact matches        236 / 271         267 / 271
 *   uncovered agent-hours            106                41
 *   peak over-allocation             156-183%          130-150%
 * </pre>
 *
 * It is not a trade-off — the solver's mix is worse on BOTH objectives at once. It over-supplies
 * 10:00-12:00 starts and under-supplies 06:00-09:00 (4 Sep: 9 agents start 11:00 where 3 want it,
 * 12 start 08:00 where 16 want it), while the requirement curve peaks at 09:00-10:00 and
 * 14:00-15:00 and collapses in the evening. Late starts over-cover cheap hours and starve the
 * morning.
 *
 * <p><b>Objective is lexicographic: coverage first, consistency second.</b> Minimise uncovered
 * agent-slots, then — among mixes tied on coverage — maximise the number of agent-days that CAN
 * be given their usual start. Consistency never buys an uncovered hour, which is the invariant
 * V48's migration comment insists on. The second term is a ceiling, not an assignment: it counts
 * {@code sum over start times of min(agents wanting it, slots offering it)}, which
 * {@link ScheduleConsistencyRepairService} then attains exactly.
 *
 * <p><b>Scope guard — a single substitutability class.</b> Targets are emitted only when every
 * working agent on the desk shares one {@link ScheduleConsistencyRepairService#substitutabilityKey}
 * (identical contracted hours, identical specialization profile). That is precisely the condition
 * under which "who holds which envelope is interchangeable" holds, and so the condition under
 * which a start-time count is a meaningful target at all. A mixed desk gets an empty list and the
 * constraint stays inert — see {@code ScheduleConstraintProvider.shiftStartMix}.
 *
 * <p><b>Two further guards, both verified rather than assumed.</b> A date carrying timeslots but
 * zero total staffing requirement is skipped: with nothing to cover, the coverage term is
 * identically zero for every mix and the objective would silently reduce to consistency alone. And
 * a date whose working agent-days do not all share one eligible-envelope set is skipped, because
 * the substitutability key gates on CONTRACTED hours while the value range filters on EFFECTIVE
 * hours — a partial-day exception changes the latter without changing the former, and a per-date
 * head count means nothing if the agents on that date cannot all reach the same starts.
 *
 * <p><b>The result is a steer, not a cage.</b> The constraint that reads these targets is soft and
 * weight-driven, so a solve whose real constraints disagree with this model can still overrule it.
 * That matters because this model is deliberately simpler than the constraint set: it knows about
 * coverage, band capacity and start-time preference, and nothing else.
 */
@Service
public class ShiftStartMixTargetService {

    private static final Logger log = LoggerFactory.getLogger(ShiftStartMixTargetService.class);

    /**
     * Hill-climb restarts per date. The first two seeds are deterministic and carry the structure
     * (demand-proportional, then uniform); the rest are pseudo-random from a FIXED seed, so two
     * solves of the same problem always produce the same targets. A solver run that is not
     * reproducible is not debuggable.
     */
    private static final int RESTARTS = 8;

    /** Guard against a pathological instance spinning; each restart converges in far fewer. */
    private static final int MAX_STEPS_PER_RESTART = 2_000;

    /**
     * Computes one target per (date, start time), or an empty list when the desk is out of scope.
     *
     * @param shiftAssignments one row per working agent-day, already built and carrying each row's
     *                         eligible {@link ShiftBandPair} value range
     */
    public List<ShiftStartMixTarget> computeTargets(SchedulingMode schedulingMode,
                                                    List<AgentShiftAssignment> shiftAssignments,
                                                    List<ResolvedUsualShiftTarget> usualTargets,
                                                    List<StaffingRequirement> staffingRequirements,
                                                    List<Timeslot> timeslots) {
        if (schedulingMode != SchedulingMode.SHIFT || shiftAssignments == null || shiftAssignments.isEmpty()) {
            return List.of();
        }
        if (usualTargets == null || usualTargets.isEmpty()) {
            log.debug("Shift-start mix targeting skipped — no resolved usual-shift targets");
            return List.of();
        }
        if (!singleSubstitutabilityClass(shiftAssignments)) {
            log.info("Shift-start mix targeting skipped — the desk's working agents span more than "
                    + "one substitutability class, so a start-time head count is not a meaningful "
                    + "target. Solve proceeds unchanged.");
            return List.of();
        }

        // Requirements summed across specializations: within one substitutability class every
        // agent can serve every specialization, so the headcount a slot needs is the total.
        Map<LocalDate, Map<LocalTime, Integer>> reqByDate = new HashMap<>();
        for (StaffingRequirement r : staffingRequirements) {
            Timeslot ts = r.getTimeslot();
            if (ts == null) {
                continue;
            }
            reqByDate.computeIfAbsent(ts.getDate(), k -> new HashMap<>())
                    .merge(ts.getStartTime(), r.getRequiredFTEs(), Integer::sum);
        }

        Map<LocalDate, List<Timeslot>> slotsByDate = new HashMap<>();
        for (Timeslot ts : timeslots) {
            slotsByDate.computeIfAbsent(ts.getDate(), k -> new ArrayList<>()).add(ts);
        }
        slotsByDate.values().forEach(l -> l.sort(Comparator.comparing(Timeslot::getStartTime)));

        Map<LocalDate, Map<LocalTime, Integer>> wantByDate = new HashMap<>();
        Set<String> workingAgentDays = new HashSet<>();
        for (AgentShiftAssignment sa : shiftAssignments) {
            workingAgentDays.add(sa.getAgent().getId() + "|" + sa.getDate());
        }
        for (ResolvedUsualShiftTarget t : usualTargets) {
            // Only agent-days that actually work — a usual shift on a day off wants nothing.
            if (t.usualStartTime() != null && workingAgentDays.contains(t.agentId() + "|" + t.date())) {
                wantByDate.computeIfAbsent(t.date(), k -> new HashMap<>())
                        .merge(t.usualStartTime(), 1, Integer::sum);
            }
        }

        Map<LocalDate, List<AgentShiftAssignment>> rowsByDate = new TreeMap<>();
        for (AgentShiftAssignment sa : shiftAssignments) {
            rowsByDate.computeIfAbsent(sa.getDate(), k -> new ArrayList<>()).add(sa);
        }

        List<ShiftStartMixTarget> out = new ArrayList<>();
        for (Map.Entry<LocalDate, List<AgentShiftAssignment>> e : rowsByDate.entrySet()) {
            LocalDate date = e.getKey();
            Map<LocalTime, Integer> want = wantByDate.getOrDefault(date, Map.of());
            if (want.isEmpty()) {
                continue; // nothing to steer toward on this date; leave the solver free
            }
            out.addAll(solveDate(date, e.getValue(), want,
                    reqByDate.getOrDefault(date, Map.of()),
                    slotsByDate.getOrDefault(date, List.of())));
        }
        return out;
    }

    /** True when every working agent-day's agent shares one substitutability key. */
    private boolean singleSubstitutabilityClass(List<AgentShiftAssignment> rows) {
        String first = null;
        for (AgentShiftAssignment sa : rows) {
            Agent agent = sa.getAgent();
            if (agent == null) {
                return false;
            }
            String key = ScheduleConsistencyRepairService.substitutabilityKey(agent);
            if (first == null) {
                first = key;
            } else if (!first.equals(key)) {
                return false;
            }
        }
        return first != null;
    }

    /**
     * The eligible-envelope list shared by every working agent-day on a date, or {@code null} when
     * they do not all share one. Compared by list equality, which is sound here because every row
     * draws from the SAME {@code deskShiftBandPairs} instance list, so equal envelopes are
     * identical instances.
     */
    private List<ShiftBandPair> uniformEligiblePairs(List<AgentShiftAssignment> rows) {
        List<ShiftBandPair> first = rows.get(0).getEligibleShiftBandPairs();
        for (int i = 1; i < rows.size(); i++) {
            if (!first.equals(rows.get(i).getEligibleShiftBandPairs())) {
                return null;
            }
        }
        return first;
    }

    private List<ShiftStartMixTarget> solveDate(LocalDate date, List<AgentShiftAssignment> rows,
                                                Map<LocalTime, Integer> want,
                                                Map<LocalTime, Integer> reqByStart,
                                                List<Timeslot> slots) {
        int W = rows.size();

        // GUARD 1 — a date with timeslots but no demand. Uncovered is then identically zero
        // whatever the mix, the coverage term of the objective vanishes, and the lexicographic
        // ordering collapses to pure consistency-maximisation: every agent on their usual start,
        // coverage-blind. Harmless while this constraint is inert, actively wrong the moment a
        // target is allowed to restrict what the solver may build. Timeslots are generated across
        // the operating window independently of demand, so a schedule period extending past
        // uploaded staffing requirements reaches this -- it is not a theoretical case. Neither
        // live desk has such a date today (Saferide 439 required FTE/day, Stubhub 102-147).
        int totalRequired = 0;
        for (int required : reqByStart.values()) {
            totalRequired += required;
        }
        if (totalRequired == 0) {
            log.warn("Shift-start mix targeting skipped for {} — {} timeslot(s) but zero total "
                    + "staffing requirement, so no mix is better-covered than any other and the "
                    + "objective would reduce to consistency alone. Solve proceeds unchanged.",
                    date, slots.size());
            return List.of();
        }

        // GUARD 2 — one value range for the whole date, verified rather than assumed.
        //
        // The substitutability key gates on CONTRACTED hours; getEligibleShiftBandPairs filters on
        // dayConfig.effectiveHours(). Those are not the same number. A partial-day exception or a
        // per-weekday hours row changes a single agent-day's effective hours WITHOUT changing its
        // substitutability class, and that row's eligible envelopes then differ from its
        // classmates'. A per-date start-time head count is not meaningful when agents on the date
        // cannot all reach the same starts, and taking the first row's value range as the date's
        // -- which this method used to do -- would silently target starts some agents can never be
        // given. Both live desks are uniform today (effective hours are only ever 8.0, or 0.0 for
        // a day off, which never produces a shift row at all), which is exactly why this has to be
        // checked rather than relied on.
        List<ShiftBandPair> pairs = uniformEligiblePairs(rows);
        if (pairs == null) {
            log.warn("Shift-start mix targeting skipped for {} — working agent-days on this date do "
                    + "not all share one eligible-envelope set, most likely differing effective "
                    + "hours within one substitutability class. Solve proceeds unchanged.", date);
            return List.of();
        }
        if (pairs.isEmpty() || slots.isEmpty()) {
            log.debug("Shift-start mix targeting skipped for {} — {} eligible pairs, {} timeslots",
                    date, pairs.size(), slots.size());
            return List.of();
        }

        int P = pairs.size();
        int S = slots.size();
        int[] req = new int[S];
        for (int s = 0; s < S; s++) {
            req[s] = reqByStart.getOrDefault(slots.get(s).getStartTime(), 0);
        }

        // Coverage matrix, derived from ShiftBandPair.covers so this model and
        // shiftEnvelopeCompliance can never disagree about which slots an envelope works.
        int[][] cov = new int[P][];
        for (int p = 0; p < P; p++) {
            List<Integer> c = new ArrayList<>();
            for (int s = 0; s < S; s++) {
                if (pairs.get(p).covers(slots.get(s))) {
                    c.add(s);
                }
            }
            cov[p] = c.stream().mapToInt(Integer::intValue).toArray();
        }

        // A null band capacity is genuinely unlimited (ENVL-08) — W is the real ceiling.
        int[] cap = new int[P];
        int totalCap = 0;
        for (int p = 0; p < P; p++) {
            Integer c = pairs.get(p).band() == null ? null : pairs.get(p).band().getCapacity();
            cap[p] = c == null ? W : Math.min(W, Math.max(0, c));
            totalCap += cap[p];
        }
        if (totalCap < W) {
            log.warn("Shift-start mix targeting skipped for {} — band capacities total {} but {} "
                    + "agent-days must be placed. Solve proceeds unchanged.", date, totalCap, W);
            return List.of();
        }

        // Start-time grouping. Index order is by clock time so seeding and logs read naturally.
        List<LocalTime> starts = pairs.stream().map(p -> p.template().getStartTime())
                .distinct().sorted().toList();
        Map<LocalTime, Integer> startIndex = new LinkedHashMap<>();
        for (int i = 0; i < starts.size(); i++) {
            startIndex.put(starts.get(i), i);
        }
        int[] pairStart = new int[P];
        for (int p = 0; p < P; p++) {
            pairStart[p] = startIndex.get(pairs.get(p).template().getStartTime());
        }
        int[] wantByStart = new int[starts.size()];
        for (Map.Entry<LocalTime, Integer> w : want.entrySet()) {
            Integer idx = startIndex.get(w.getKey());
            if (idx != null) {
                wantByStart[idx] = w.getValue();
            }
            // A usual start with no template offering it is simply unreachable — it contributes
            // nothing to the ceiling and needs no special case.
        }

        Mix best = null;
        Random rnd = new Random(date.toEpochDay()); // fixed seed: same problem, same targets
        for (int restart = 0; restart < RESTARTS; restart++) {
            Mix m = seed(restart, P, W, cap, pairStart, wantByStart, starts.size(), rnd);
            if (m == null) {
                continue;
            }
            m.evaluate(cov, req, S, wantByStart, pairStart, starts.size());
            hillClimb(m, cov, req, S, cap, wantByStart, pairStart, starts.size());
            if (best == null || m.score > best.score) {
                best = m;
            }
        }
        if (best == null) {
            log.warn("Shift-start mix targeting produced no feasible mix for {} — solve proceeds "
                    + "unchanged.", date);
            return List.of();
        }

        int[] countByStart = new int[starts.size()];
        for (int p = 0; p < P; p++) {
            countByStart[pairStart[p]] += best.n[p];
        }
        int ceiling = 0;
        for (int i = 0; i < starts.size(); i++) {
            ceiling += Math.min(wantByStart[i], countByStart[i]);
        }
        log.info("Shift-start mix target {} — {} agent-days, uncovered {} slot(s), usual-start "
                        + "ceiling {}/{}; want={} target={}",
                date, W, best.uncovered, ceiling, sum(wantByStart),
                render(starts, wantByStart), render(starts, countByStart));

        List<ShiftStartMixTarget> out = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++) {
            // Zero-count starts are emitted deliberately: "nobody should start here" only binds if
            // there is a row for the constraint to compare an over-count against.
            out.add(new ShiftStartMixTarget(date, starts.get(i), countByStart[i]));
        }
        return out;
    }

    /** Distributes W agent-days over the pairs; null when the seed cannot be completed. */
    private Mix seed(int restart, int P, int W, int[] cap, int[] pairStart, int[] wantByStart,
                     int nStarts, Random rnd) {
        int[] n = new int[P];
        int left = W;
        if (restart == 0) {
            // Demand-proportional: give each start its wanted head count, spread across its bands.
            List<List<Integer>> byStart = new ArrayList<>();
            for (int i = 0; i < nStarts; i++) {
                byStart.add(new ArrayList<>());
            }
            for (int p = 0; p < P; p++) {
                byStart.get(pairStart[p]).add(p);
            }
            for (int i = 0; i < nStarts && left > 0; i++) {
                int give = Math.min(wantByStart[i], left);
                List<Integer> ps = byStart.get(i);
                for (int k = 0; k < give && left > 0; k++) {
                    int p = ps.get(k % ps.size());
                    if (n[p] < cap[p]) {
                        n[p]++;
                        left--;
                    }
                }
            }
        } else if (restart > 1) {
            for (int k = 0; k < W && left > 0; k++) {
                int p = rnd.nextInt(P);
                if (n[p] < cap[p]) {
                    n[p]++;
                    left--;
                }
            }
        }
        // Round-robin the remainder (and the whole of the uniform seed) into any pair with room.
        int guard = 0;
        for (int p = 0; left > 0; p = (p + 1) % P) {
            if (n[p] < cap[p]) {
                n[p]++;
                left--;
            }
            if (++guard > (long) P * (W + 1)) {
                return null; // capacity exhausted — caller already checked the total, so defensive
            }
        }
        return new Mix(n);
    }

    /** Steepest descent over "move one agent-day from pair a to pair b". */
    private void hillClimb(Mix m, int[][] cov, int[] req, int S, int[] cap,
                           int[] wantByStart, int[] pairStart, int nStarts) {
        int P = m.n.length;
        int[] scratch = new int[S];
        int[] touched = new int[S];
        for (int step = 0; step < MAX_STEPS_PER_RESTART; step++) {
            long bestScore = m.score;
            int bestA = -1, bestB = -1;
            for (int a = 0; a < P; a++) {
                if (m.n[a] == 0) {
                    continue;
                }
                for (int b = 0; b < P; b++) {
                    if (a == b || m.n[b] >= cap[b]) {
                        continue;
                    }
                    long s = m.score + delta(m, a, b, cov, req, scratch, touched,
                            wantByStart, pairStart);
                    if (s > bestScore) {
                        bestScore = s;
                        bestA = a;
                        bestB = b;
                    }
                }
            }
            if (bestA < 0) {
                return;
            }
            m.n[bestA]--;
            m.n[bestB]++;
            m.evaluate(cov, req, S, wantByStart, pairStart, nStarts);
        }
    }

    /** Score change of moving one agent-day from pair {@code a} to pair {@code b}. */
    private long delta(Mix m, int a, int b, int[][] cov, int[] req, int[] scratch, int[] touched,
                       int[] wantByStart, int[] pairStart) {
        int t = 0;
        for (int s : cov[a]) {
            if (scratch[s] == 0) {
                touched[t++] = s;
            }
            scratch[s]--;
        }
        for (int s : cov[b]) {
            if (scratch[s] == 0) {
                touched[t++] = s;
            }
            scratch[s]++;
        }
        int deltaUncovered = 0;
        for (int i = 0; i < t; i++) {
            int s = touched[i];
            int before = Math.max(0, req[s] - m.staffed[s]);
            int after = Math.max(0, req[s] - (m.staffed[s] + scratch[s]));
            deltaUncovered += after - before;
            scratch[s] = 0;
        }
        // A slot touched by both cov[a] and cov[b] nets to zero and is harmlessly re-zeroed above.

        int sa = pairStart[a];
        int sb = pairStart[b];
        int deltaMatches = 0;
        if (sa != sb) {
            deltaMatches = Math.min(wantByStart[sa], m.countByStart[sa] - 1) - Math.min(wantByStart[sa], m.countByStart[sa])
                    + Math.min(wantByStart[sb], m.countByStart[sb] + 1) - Math.min(wantByStart[sb], m.countByStart[sb]);
        }
        return -(long) deltaUncovered * m.coverageWeight + deltaMatches;
    }

    private static int sum(int[] a) {
        int t = 0;
        for (int v : a) {
            t += v;
        }
        return t;
    }

    private static String render(List<LocalTime> starts, int[] counts) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < starts.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(starts.get(i)).append('=').append(counts[i]);
        }
        return sb.append(']').toString();
    }

    /**
     * One candidate mix. {@code score} is lexicographic by construction: coverage is multiplied by
     * a weight strictly greater than the largest possible match count, so no number of usual-start
     * matches can ever pay for one uncovered slot.
     */
    private static final class Mix {
        final int[] n;
        int[] staffed;
        int[] countByStart;
        int uncovered;
        int matches;
        long score;
        long coverageWeight;

        Mix(int[] n) {
            this.n = n;
        }

        void evaluate(int[][] cov, int[] req, int S, int[] wantByStart, int[] pairStart, int nStarts) {
            staffed = new int[S];
            for (int p = 0; p < n.length; p++) {
                if (n[p] == 0) {
                    continue;
                }
                for (int s : cov[p]) {
                    staffed[s] += n[p];
                }
            }
            uncovered = 0;
            for (int s = 0; s < S; s++) {
                uncovered += Math.max(0, req[s] - staffed[s]);
            }
            countByStart = new int[nStarts];
            int total = 0;
            for (int p = 0; p < n.length; p++) {
                countByStart[pairStart[p]] += n[p];
                total += n[p];
            }
            matches = 0;
            for (int i = 0; i < nStarts; i++) {
                matches += Math.min(wantByStart[i], countByStart[i]);
            }
            coverageWeight = total + 1L;
            score = -(long) uncovered * coverageWeight + matches;
        }
    }
}
