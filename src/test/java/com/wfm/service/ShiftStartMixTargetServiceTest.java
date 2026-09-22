package com.wfm.service;

import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.ResolvedUsualShiftTarget;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftStartMixTarget;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Anchored to the live Saferide desk, 31 Aug 2026 — the measurement that motivated the feature.
 *
 * <p>Every number in {@link #REQUIRED}, {@link #WANT}, {@link #SOLVER_MIX} and the band capacities
 * is taken from the accepted schedule {@code 97579464} and the desk's shift library as they stood
 * on 2026-09-22, not invented. That is the point: a fixture that merely exercises the code would
 * not have caught what this feature exists to fix, because the blind mix the solver produced is
 * perfectly legal — it is only visibly bad next to the real requirement curve.
 */
class ShiftStartMixTargetServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 31); // a Monday
    private static final int FIRST_HOUR = 6;

    /** Required FTEs, 06:00 through 20:00, from the live desk's staffing requirements. */
    private static final int[] REQUIRED = {4, 10, 26, 44, 44, 43, 37, 41, 49, 47, 40, 27, 12, 9, 6};

    /** Agents wanting each start time 06:00..12:00 — the live usual-shift distribution. */
    private static final int[] WANT = {3, 7, 17, 17, 3, 1, 6}; // 54 of the 57 working agent-days

    /** What the solver actually built, for the comparison the test asserts we beat. */
    private static final int[] SOLVER_MIX = {2, 5, 15, 17, 4, 7, 7};

    /** Break-band capacities per template start, offsets +3h/+4h/+5h, from the live library. */
    private static final int[][] BAND_CAPACITY = {
            {16, 23, 21}, {16, 23, 21}, {17, 25, 18}, {18, 23, 20},
            {16, 25, 19}, {16, 22, 22}, {16, 25, 19}};

    private static final int WORKING_AGENT_DAYS = 57;

    private final ShiftStartMixTargetService service = new ShiftStartMixTargetService();

    @Test
    void beatsTheSolversOwnMixOnCoverageAndConsistencyAtOnce() {
        Fixture f = saferide();
        List<ShiftStartMixTarget> targets = service.computeTargets(SchedulingMode.SHIFT,
                f.rows, f.usualTargets, f.requirements, f.timeslots, f.seats);

        assertThat(targets).hasSize(7);
        int[] mix = mix(targets);

        // The invariant the constraint depends on: targets account for every working agent-day,
        // so an over-count on one start is an under-count on another and charging only the
        // overshoot is complete.
        assertThat(sum(mix)).isEqualTo(WORKING_AGENT_DAYS);

        // Strictly better than what the solver built, on BOTH objectives. This is the whole claim:
        // the blind mix is not trading coverage for consistency, it is worse at both.
        assertThat(uncovered(mix)).isLessThan(uncovered(SOLVER_MIX));
        assertThat(ceiling(mix)).isGreaterThan(ceiling(SOLVER_MIX));

        // Pinned yardstick values, so a regression in either direction is visible rather than
        // merely "still better than before". ceiling() does not depend on break placement and so
        // reproduces the live figure exactly: the solver's mix reaches 49 of 54 wanted starts.
        // uncovered() DOES depend on break placement, and this helper spreads breaks round-robin
        // where the live schedule placed them well — hence 20 here against the 17 actually
        // measured on the desk. The comparison stays honest because both mixes are scored by the
        // same naive rule.
        assertThat(ceiling(SOLVER_MIX)).isEqualTo(49);
        assertThat(uncovered(SOLVER_MIX)).isEqualTo(20);
        assertThat(uncovered(mix)).isLessThanOrEqualTo(2);
        assertThat(ceiling(mix)).isGreaterThanOrEqualTo(53);
    }

    @Test
    void coverageIsNeverTradedForConsistency() {
        // Every agent wants 12:00 — the only start that cannot cover the 06:00-08:00 requirement.
        // Honouring all of them would leave the morning empty, so the mix must refuse.
        Fixture f = saferide();
        List<ResolvedUsualShiftTarget> allLate = new ArrayList<>();
        for (AgentShiftAssignment row : f.rows) {
            allLate.add(new ResolvedUsualShiftTarget(row.getAgent().getId(), DAY, LocalTime.of(12, 0)));
        }
        int[] mix = mix(service.computeTargets(SchedulingMode.SHIFT, f.rows, allLate,
                f.requirements, f.timeslots, f.seats));

        assertThat(sum(mix)).isEqualTo(WORKING_AGENT_DAYS);
        // A consistency-first mix would put all 57 on 12:00 and uncover 182 agent-slots.
        assertThat(uncovered(new int[] {0, 0, 0, 0, 0, 0, 57})).isEqualTo(182);
        assertThat(uncovered(mix)).isLessThanOrEqualTo(uncovered(SOLVER_MIX));
    }

    @Test
    void emitsAZeroTargetForStartsNobodyShouldUse() {
        // A zero row is load-bearing: "nobody starts here" only binds if the constraint has a row
        // to compare an over-count against.
        Fixture f = saferide();
        List<ShiftStartMixTarget> targets = service.computeTargets(SchedulingMode.SHIFT,
                f.rows, f.usualTargets, f.requirements, f.timeslots, f.seats);
        assertThat(targets).allSatisfy(t -> assertThat(t.targetCount()).isGreaterThanOrEqualTo(0));
        assertThat(targets).extracting(ShiftStartMixTarget::startTime)
                .containsExactlyInAnyOrder(LocalTime.of(6, 0), LocalTime.of(7, 0), LocalTime.of(8, 0),
                        LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0));
    }

    @Test
    void isDeterministic() {
        // Two solves of one problem must produce identical targets, or a solve is not debuggable.
        Fixture f = saferide();
        assertThat(mix(service.computeTargets(SchedulingMode.SHIFT, f.rows, f.usualTargets,
                f.requirements, f.timeslots, f.seats)))
                .isEqualTo(mix(service.computeTargets(SchedulingMode.SHIFT, f.rows, f.usualTargets,
                        f.requirements, f.timeslots, f.seats)));
    }

    @Test
    void emitsNothingForASlotModeDesk() {
        Fixture f = saferide();
        assertThat(service.computeTargets(SchedulingMode.SLOT, f.rows, f.usualTargets,
                f.requirements, f.timeslots, f.seats)).isEmpty();
    }

    @Test
    void emitsNothingWhenAgentsSpanMoreThanOneSubstitutabilityClass() {
        // A start-time head count is only meaningful where agents are interchangeable across
        // envelopes. One 6-hour agent among 57 eight-hour ones is enough to void that.
        Fixture f = saferide();
        f.rows.get(0).getAgent().setContractedHoursPerDay(new BigDecimal("6.0"));
        assertThat(service.computeTargets(SchedulingMode.SHIFT, f.rows, f.usualTargets,
                f.requirements, f.timeslots, f.seats)).isEmpty();
    }

    @Test
    void emitsNothingForADateWithNoUsualShiftTargets() {
        Fixture f = saferide();
        assertThat(service.computeTargets(SchedulingMode.SHIFT, f.rows, List.of(),
                f.requirements, f.timeslots, f.seats)).isEmpty();
    }

    @Test
    void emitsNothingForADateWithTimeslotsButNoStaffingRequirement() {
        // With nothing to cover, every mix scores zero uncovered and the lexicographic objective
        // collapses to consistency alone — it would put all 57 agents on their usual start,
        // coverage-blind. Reachable whenever a schedule period runs past uploaded demand, because
        // timeslots are generated across the operating window independently of it.
        Fixture f = saferide();
        assertThat(service.computeTargets(SchedulingMode.SHIFT, f.rows, f.usualTargets,
                List.of(), f.timeslots, f.seats)).isEmpty();
    }

    @Test
    void emitsNothingWhenEffectiveHoursVaryInsideOneSubstitutabilityClass() {
        // The agent's CONTRACTED hours are untouched, so the substitutability gate still passes —
        // this is precisely the case that gate cannot see. A 6-hour effective day makes every
        // 8-hour envelope ineligible for that one row, so the date no longer has a single value
        // range and a per-date start head count stops being meaningful.
        Fixture f = saferide();
        AgentShiftAssignment odd = f.rows.get(0);
        odd.setDayConfig(new AgentDayConfig(odd.getAgent().getId(), DAY, new BigDecimal("6.0"),
                60, 60, new BigDecimal("4.0"), BigDecimal.ONE, BreakAlignment.ON_HALF_HOUR, 200, 70));
        assertThat(odd.getEligibleShiftBandPairs())
                .as("fixture guard: the short day must genuinely change this row's value range")
                .isNotEqualTo(f.rows.get(1).getEligibleShiftBandPairs());

        assertThat(service.computeTargets(SchedulingMode.SHIFT, f.rows, f.usualTargets,
                f.requirements, f.timeslots, f.seats)).isEmpty();
    }

    @Test
    void ignoresAUsualShiftOnADayTheAgentDoesNotWork() {
        // A usual shift on a day off wants nothing and must not inflate a start's target.
        Fixture f = saferide();
        List<ResolvedUsualShiftTarget> withGhost = new ArrayList<>(f.usualTargets);
        withGhost.add(new ResolvedUsualShiftTarget(UUID.randomUUID(), DAY, LocalTime.of(12, 0)));
        assertThat(mix(service.computeTargets(SchedulingMode.SHIFT, f.rows, withGhost,
                f.requirements, f.timeslots, f.seats)))
                .isEqualTo(mix(service.computeTargets(SchedulingMode.SHIFT, f.rows, f.usualTargets,
                        f.requirements, f.timeslots, f.seats)));
    }

    // ---------- scoring helpers, deliberately independent of the service's own arithmetic ----------

    /** Uncovered agent-slots for a start-time mix, spreading each start evenly over its bands. */
    private int uncovered(int[] mixByStart) {
        int[] staffed = new int[REQUIRED.length];
        for (int i = 0; i < mixByStart.length; i++) {
            int start = FIRST_HOUR + i;
            for (int k = 0; k < mixByStart[i]; k++) {
                int breakHour = start + 3 + (k % 3);
                for (int h = start; h < start + 9; h++) {
                    if (h != breakHour && h - FIRST_HOUR < staffed.length) {
                        staffed[h - FIRST_HOUR]++;
                    }
                }
            }
        }
        int total = 0;
        for (int s = 0; s < REQUIRED.length; s++) {
            total += Math.max(0, REQUIRED[s] - staffed[s]);
        }
        return total;
    }

    /** Usual-start matches reachable under a mix: sum over starts of min(wanted, offered). */
    private int ceiling(int[] mixByStart) {
        int total = 0;
        for (int i = 0; i < WANT.length; i++) {
            total += Math.min(WANT[i], mixByStart[i]);
        }
        return total;
    }

    private int[] mix(List<ShiftStartMixTarget> targets) {
        int[] m = new int[7];
        for (ShiftStartMixTarget t : targets) {
            m[t.startTime().getHour() - FIRST_HOUR] = t.targetCount();
        }
        return m;
    }

    private int sum(int[] a) {
        int t = 0;
        for (int v : a) {
            t += v;
        }
        return t;
    }

    // ---------- fixture ----------

    private record Fixture(List<AgentShiftAssignment> rows, List<ResolvedUsualShiftTarget> usualTargets,
                           List<StaffingRequirement> requirements, List<Timeslot> timeslots,
                           List<AgentAssignment> seats) {}

    private Fixture saferide() {
        Specialization spec = new Specialization();
        spec.setId(UUID.randomUUID());
        spec.setName("Saferide General");

        List<Timeslot> timeslots = new ArrayList<>();
        List<StaffingRequirement> requirements = new ArrayList<>();
        for (int i = 0; i < REQUIRED.length; i++) {
            Timeslot ts = new Timeslot();
            ts.setId(UUID.randomUUID());
            ts.setDate(DAY);
            ts.setStartTime(LocalTime.of(FIRST_HOUR + i, 0));
            ts.setEndTime(LocalTime.of(FIRST_HOUR + i + 1, 0));
            timeslots.add(ts);
            StaffingRequirement r = new StaffingRequirement();
            r.setId(UUID.randomUUID());
            r.setTimeslot(ts);
            r.setSpecialization(spec);
            r.setRequiredFTEs(REQUIRED[i]);
            requirements.add(r);
        }

        List<ShiftBandPair> pairs = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            ShiftTemplate t = new ShiftTemplate();
            t.setId(UUID.randomUUID());
            t.setName("Saferide " + LocalTime.of(FIRST_HOUR + i, 0));
            t.setStartTime(LocalTime.of(FIRST_HOUR + i, 0));
            t.setEndTime(LocalTime.of(FIRST_HOUR + i + 9, 0));
            t.setEffectiveFrom(DAY);
            t.setValidWeekdays(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
            for (int b = 0; b < 3; b++) {
                ShiftTemplateBreakBand band = new ShiftTemplateBreakBand();
                band.setId(UUID.randomUUID());
                band.setShiftTemplate(t);
                band.setOffsetMinutes(180 + b * 60);
                band.setDurationMinutes(60);
                band.setCapacity(BAND_CAPACITY[i][b]);
                pairs.add(new ShiftBandPair(t, band));
            }
        }

        // Usual starts handed out in the live proportions.
        List<LocalTime> wantQueue = new ArrayList<>();
        for (int i = 0; i < WANT.length; i++) {
            for (int k = 0; k < WANT[i]; k++) {
                wantQueue.add(LocalTime.of(FIRST_HOUR + i, 0));
            }
        }

        List<AgentShiftAssignment> rows = new ArrayList<>();
        List<ResolvedUsualShiftTarget> usualTargets = new ArrayList<>();
        for (int i = 0; i < WORKING_AGENT_DAYS; i++) {
            Agent a = new Agent();
            a.setId(UUID.randomUUID());
            a.setName("Agent " + i);
            a.setPrimarySpecialization(spec);
            a.setContractedHoursPerDay(new BigDecimal("8.0"));
            AgentDayConfig cfg = new AgentDayConfig(a.getId(), DAY, new BigDecimal("8.0"),
                    60, 60, new BigDecimal("4.0"), BigDecimal.ONE, BreakAlignment.ON_HALF_HOUR,
                    200, 70);
            AgentShiftAssignment sa = new AgentShiftAssignment();
            sa.setId(UUID.randomUUID());
            sa.setAgent(a);
            sa.setDate(DAY);
            sa.setDayConfig(cfg);
            sa.setDeskShiftBandPairs(pairs);
            sa.setEnvelopeSlackSlots(0);
            rows.add(sa);
            if (i < wantQueue.size()) {
                usualTargets.add(new ResolvedUsualShiftTarget(a.getId(), DAY, wantQueue.get(i)));
            }
        }
        // Seats: the live desk builds demand seats plus overflow up to overallocationHardLimitPct
        // (200%) plus a minimum-staffing floor. Modelled here as 2x requirement, which is that
        // ceiling — generous enough that these tests measure the coverage/consistency objective
        // rather than the seat bound, which has its own test below.
        List<AgentAssignment> seats = new ArrayList<>();
        for (int i = 0; i < REQUIRED.length; i++) {
            for (int k = 0; k < REQUIRED[i] * 2; k++) {
                seats.add(seatAt(timeslots.get(i), spec));
            }
        }
        return new Fixture(rows, usualTargets, requirements, timeslots, seats);
    }

    private static AgentAssignment seatAt(Timeslot ts, Specialization spec) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(ts);
        a.setRequiredSpecialization(spec);
        return a;
    }

    @Test
    void neverTargetsMoreEnvelopesOverASlotThanThereAreSeatsToWorkIt() {
        // The regression the first live ENFORCE run filed. An agent-day works EVERY non-break slot
        // its envelope covers, so a mix that puts more envelopes over a slot than there are seats
        // pushes the surplus OUTSIDE their envelope -- hard Shift envelope compliance, which no
        // amount of coverage or consistency may buy. Live result without this bound: 271/271 usual
        // starts and 20 uncovered hours at -5 hard.
        Fixture f = saferide();
        // Starve the 06:00 slot: 4 seats where the unconstrained optimum wants 4-6 envelopes on it.
        List<AgentAssignment> scarce = new ArrayList<>();
        for (AgentAssignment seat : f.seats) {
            if (!seat.getTimeslot().getStartTime().equals(LocalTime.of(6, 0))) {
                scarce.add(seat);
            }
        }
        for (int k = 0; k < 2; k++) {
            scarce.add(seatAt(f.timeslots.get(0), null));
        }

        int[] mix = mix(service.computeTargets(SchedulingMode.SHIFT, f.rows, f.usualTargets,
                f.requirements, f.timeslots, scarce));

        assertThat(sum(mix)).isEqualTo(WORKING_AGENT_DAYS);
        assertThat(mix[0])
                .as("only 06:00 envelopes cover the 06:00 slot, so at most 2 may be targeted there")
                .isLessThanOrEqualTo(2);
    }

    @Test
    void seatOverflowOutranksBothCoverageAndConsistency() {
        // No seats anywhere: every mix overflows, so the optimiser must still return a complete,
        // well-formed target rather than diverging or emitting nothing.
        Fixture f = saferide();
        int[] mix = mix(service.computeTargets(SchedulingMode.SHIFT, f.rows, f.usualTargets,
                f.requirements, f.timeslots, List.of()));
        assertThat(sum(mix)).isEqualTo(WORKING_AGENT_DAYS);
    }

    /** Guards the fixture itself: a wrong value range would make every assertion meaningless. */
    @Test
    void fixtureOffersAllTwentyOneEnvelopes() {
        Fixture f = saferide();
        assertThat(f.rows.get(0).getEligibleShiftBandPairs()).hasSize(21);
        Map<LocalTime, Integer> perStart = new LinkedHashMap<>();
        f.rows.get(0).getEligibleShiftBandPairs()
                .forEach(p -> perStart.merge(p.template().getStartTime(), 1, Integer::sum));
        assertThat(perStart).hasSize(7).containsValue(3);
    }
}
