package com.wfm.service;

import com.wfm.model.Agent;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@code SolverService.buildShiftBandPairs}/{@code buildShiftAssignments} (D-05,
 * ENVL-01/06/08), tested directly as package-private static helpers — no Spring context, mirroring
 * {@code SolverServiceEffectiveHoursResolutionTest}'s precedent.
 */
class SolverServiceShiftAssignmentTest {

    private static final LocalDate MON = LocalDate.of(2026, 9, 7);
    private static final LocalDate TUE = LocalDate.of(2026, 9, 8);

    private static ShiftTemplate template(String name, LocalTime start, LocalTime end, LocalDate effectiveFrom) {
        ShiftTemplate t = new ShiftTemplate();
        t.setValidWeekdays(java.util.EnumSet.allOf(java.time.DayOfWeek.class));
        t.setId(UUID.randomUUID());
        t.setName(name);
        t.setStartTime(start);
        t.setEndTime(end);
        t.setEffectiveFrom(effectiveFrom);
        return t;
    }

    private static ShiftTemplateBreakBand band(ShiftTemplate template, int offsetMinutes, int durationMinutes) {
        ShiftTemplateBreakBand b = new ShiftTemplateBreakBand();
        b.setId(UUID.randomUUID());
        b.setShiftTemplate(template);
        b.setOffsetMinutes(offsetMinutes);
        b.setDurationMinutes(durationMinutes);
        return b;
    }

    private static Agent agent() {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        return a;
    }

    private static AgentDayConfig dayConfig(UUID agentId, LocalDate date, BigDecimal effectiveHours) {
        return new AgentDayConfig(agentId, date, effectiveHours, 15, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 130, 70);
    }

    // --- buildShiftBandPairs ---

    @Test
    void buildShiftBandPairs_sortsByTemplateNameThenEffectiveFromThenBandOffset() {
        ShiftTemplate early = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0), LocalDate.of(2026, 1, 1));
        ShiftTemplate late = template("Late", LocalTime.of(9, 0), LocalTime.of(18, 0), LocalDate.of(2026, 1, 1));
        ShiftTemplateBreakBand earlyBand300 = band(early, 300, 60);
        ShiftTemplateBreakBand earlyBand240 = band(early, 240, 60);
        ShiftTemplateBreakBand lateBand = band(late, 240, 60);

        Map<UUID, List<ShiftTemplateBreakBand>> bandsByTemplateId = new HashMap<>();
        bandsByTemplateId.put(early.getId(), List.of(earlyBand300, earlyBand240)); // deliberately unsorted input
        bandsByTemplateId.put(late.getId(), List.of(lateBand));

        List<ShiftBandPair> pairs = SolverService.buildShiftBandPairs(
                SchedulingMode.SHIFT, List.of(late, early), bandsByTemplateId);

        assertThat(pairs).extracting(p -> p.template().getName() + "@" + p.band().getOffsetMinutes())
                .containsExactly("Early@240", "Early@300", "Late@240");
    }

    @Test
    void buildShiftBandPairs_zeroBandTemplate_contributesOnePairWithNullBand() {
        ShiftTemplate noBreak = template("NoBreak", LocalTime.of(8, 0), LocalTime.of(16, 0), LocalDate.of(2026, 1, 1));

        List<ShiftBandPair> pairs = SolverService.buildShiftBandPairs(
                SchedulingMode.SHIFT, List.of(noBreak), Map.of());

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).band()).isNull();
    }

    @Test
    void buildShiftBandPairs_slotMode_returnsEmpty() {
        ShiftTemplate t = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0), LocalDate.of(2026, 1, 1));
        List<ShiftBandPair> pairs = SolverService.buildShiftBandPairs(SchedulingMode.SLOT, List.of(t), Map.of());
        assertThat(pairs).isEmpty();
    }

    // --- buildShiftAssignments ---

    @Test
    void buildShiftAssignments_oneRowPerWorkingAgentDay_noneForZeroHours() {
        Agent a1 = agent();
        Agent a2 = agent();
        Agent a3 = agent();
        Map<UUID, Agent> agentById = new HashMap<>();
        agentById.put(a1.getId(), a1);
        agentById.put(a2.getId(), a2);
        agentById.put(a3.getId(), a3);

        List<AgentDayConfig> configs = new ArrayList<>();
        configs.add(dayConfig(a1.getId(), MON, new BigDecimal("8.00")));
        configs.add(dayConfig(a1.getId(), TUE, new BigDecimal("8.00")));
        configs.add(dayConfig(a2.getId(), MON, new BigDecimal("8.00")));
        configs.add(dayConfig(a2.getId(), TUE, BigDecimal.ZERO)); // contracted to zero -- defensive skip
        configs.add(dayConfig(a3.getId(), MON, new BigDecimal("6.00")));

        List<AgentShiftAssignment> assignments = SolverService.buildShiftAssignments(
                SchedulingMode.SHIFT, 1L, UUID.randomUUID(), UUID.randomUUID(), agentById, configs, List.of(), 0);

        assertThat(assignments).hasSize(4); // a1xMON, a1xTUE, a2xMON, a3xMON -- a2xTUE excluded
        assertThat(assignments).noneMatch(sa -> sa.getAgent().getId().equals(a2.getId()) && sa.getDate().equals(TUE));
    }

    @Test
    void buildShiftAssignments_everyRowSharesTheSameDeskPairListInstance() {
        Agent a1 = agent();
        Map<UUID, Agent> agentById = Map.of(a1.getId(), a1);
        List<AgentDayConfig> configs = List.of(dayConfig(a1.getId(), MON, new BigDecimal("8.00")));
        List<ShiftBandPair> pairs = List.of(new ShiftBandPair(
                template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0), LocalDate.of(2026, 1, 1)), null));

        List<AgentShiftAssignment> assignments = SolverService.buildShiftAssignments(
                SchedulingMode.SHIFT, 1L, UUID.randomUUID(), UUID.randomUUID(), agentById, configs, pairs, 0);

        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).getDeskShiftBandPairs()).isSameAs(pairs);
    }

    @Test
    void buildShiftAssignments_slotMode_returnsEmpty() {
        Agent a1 = agent();
        Map<UUID, Agent> agentById = Map.of(a1.getId(), a1);
        List<AgentDayConfig> configs = List.of(dayConfig(a1.getId(), MON, new BigDecimal("8.00")));

        List<AgentShiftAssignment> assignments = SolverService.buildShiftAssignments(
                SchedulingMode.SLOT, 1L, UUID.randomUUID(), UUID.randomUUID(), agentById, configs, List.of(), 0);

        assertThat(assignments).isEmpty();
    }

    // --- Entity-level value range: empty when hours match no template, never throws ---

    @Test
    void eligibleShiftBandPairs_noTemplateMatchesEffectiveHours_isEmpty_stillUnassigned() {
        ShiftTemplate template = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0), LocalDate.of(2026, 1, 1));
        ShiftTemplateBreakBand b = band(template, 240, 60); // 9h span - 1h break = 8.00h net
        ShiftBandPair pair = new ShiftBandPair(template, b);

        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setDayConfig(dayConfig(UUID.randomUUID(), MON, new BigDecimal("4.00"))); // no 4h template
        sa.setDeskShiftBandPairs(List.of(pair));

        assertThat(sa.getEligibleShiftBandPairs()).isEmpty();
        assertThat(sa.getShiftBandPair()).isNull(); // stays unassigned -- no exception
    }

    @Test
    void eligibleShiftBandPairs_matchingHours_returnsThePair() {
        ShiftTemplate template = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0), LocalDate.of(2026, 1, 1));
        ShiftTemplateBreakBand b = band(template, 240, 60); // 8.00h net
        ShiftBandPair pair = new ShiftBandPair(template, b);

        AgentShiftAssignment sa = new AgentShiftAssignment();
        // CR-01 gap closure: date must be set for eligibility filtering to run at all --
        // SolverService.buildShiftAssignments always sets it in production (mirrors dayConfig's
        // date), so a row with no date is a construction defect and correctly yields no eligible
        // pairs. This test asserts the matching-hours case, so date is required here to isolate
        // that behaviour from the (separately covered) missing-date case.
        sa.setDate(MON);
        sa.setDayConfig(dayConfig(UUID.randomUUID(), MON, new BigDecimal("8.00")));
        sa.setDeskShiftBandPairs(List.of(pair));

        assertThat(sa.getEligibleShiftBandPairs()).containsExactly(pair);
    }

    // --- Bounded envelope slack (reopens D-01's exact-equality rule) ---
    //
    // Exact equality made legal in-envelope slots EQUAL contracted slots, so an agent had to
    // occupy 100% of their legal slots with no margin to route around a single unavailable one.
    // Measured on the live desk: Sunday 10:00 carries demand of 1, so the over-allocation ceiling
    // admits 2 agents there, yet every agent on a 10:00-starting envelope was obliged to work it.
    // Agents beyond the second breached their envelope to reach contracted hours, and no library
    // shape avoids it — a 9-hour contiguous envelope starting 08:00, 09:00 or 10:00 necessarily
    // contains 10:00.

    @Test
    void eligibleShiftBandPairs_slackZero_reproducesExactEqualityByteForByte() {
        // The old rule must survive as a configuration, not merely as history: slack 0 is what a
        // desk sets to opt out entirely.
        ShiftTemplate nine = template("Nine", LocalTime.of(8, 0), LocalTime.of(18, 0), LocalDate.of(2026, 1, 1));
        ShiftBandPair ninePair = new ShiftBandPair(nine, band(nine, 240, 60)); // 10h - 1h = 9.00h net
        ShiftTemplate eight = template("Eight", LocalTime.of(8, 0), LocalTime.of(17, 0), LocalDate.of(2026, 1, 1));
        ShiftBandPair eightPair = new ShiftBandPair(eight, band(eight, 240, 60)); // 8.00h net

        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setDate(MON);
        sa.setDayConfig(dayConfig(UUID.randomUUID(), MON, new BigDecimal("8.00")));
        sa.setDeskShiftBandPairs(List.of(eightPair, ninePair));
        sa.setEnvelopeSlackSlots(0);

        assertThat(sa.getEligibleShiftBandPairs()).containsExactly(eightPair);
    }

    @Test
    void eligibleShiftBandPairs_oneSlotOfSlack_admitsTheLongerEnvelope() {
        // 60-minute grid, slack 1 -> an 8h agent may hold a 9h-net envelope, giving them 9 legal
        // slots to place 8 hours in: one slot of choice, which is the whole point.
        ShiftTemplate nine = template("Nine", LocalTime.of(8, 0), LocalTime.of(18, 0), LocalDate.of(2026, 1, 1));
        ShiftBandPair ninePair = new ShiftBandPair(nine, band(nine, 240, 60)); // 9.00h net

        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setDate(MON);
        sa.setDayConfig(hourlyDayConfig(UUID.randomUUID(), MON, new BigDecimal("8.00")));
        sa.setDeskShiftBandPairs(List.of(ninePair));
        sa.setEnvelopeSlackSlots(1);

        assertThat(sa.getEligibleShiftBandPairs()).containsExactly(ninePair);
    }

    @Test
    void eligibleShiftBandPairs_slackIsBounded_doesNotAdmitAnArbitrarilyLongerEnvelope() {
        // The upper bound is what stops a 4-hour agent being handed a 9-hour shift. Two slots
        // beyond contracted, with only one slot of slack allowed.
        ShiftTemplate ten = template("Ten", LocalTime.of(8, 0), LocalTime.of(19, 0), LocalDate.of(2026, 1, 1));
        ShiftBandPair tenPair = new ShiftBandPair(ten, band(ten, 240, 60)); // 11h - 1h = 10.00h net

        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setDate(MON);
        sa.setDayConfig(hourlyDayConfig(UUID.randomUUID(), MON, new BigDecimal("8.00")));
        sa.setDeskShiftBandPairs(List.of(tenPair));
        sa.setEnvelopeSlackSlots(1);

        assertThat(sa.getEligibleShiftBandPairs()).isEmpty();
    }

    @Test
    void eligibleShiftBandPairs_neverAdmitsAnEnvelopeShorterThanContractedHours() {
        // Slack is one-directional. An agent physically cannot reach contracted hours inside an
        // envelope whose net hours fall short, so a short pair must stay ineligible at any slack.
        ShiftTemplate seven = template("Seven", LocalTime.of(8, 0), LocalTime.of(16, 0), LocalDate.of(2026, 1, 1));
        ShiftBandPair sevenPair = new ShiftBandPair(seven, band(seven, 240, 60)); // 8h - 1h = 7.00h net

        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setDate(MON);
        sa.setDayConfig(hourlyDayConfig(UUID.randomUUID(), MON, new BigDecimal("8.00")));
        sa.setDeskShiftBandPairs(List.of(sevenPair));
        sa.setEnvelopeSlackSlots(4); // generous slack must still not reach downwards

        assertThat(sa.getEligibleShiftBandPairs()).isEmpty();
    }

    /** 60-minute grid, so one slack slot is exactly one hour. */
    private static AgentDayConfig hourlyDayConfig(UUID agentId, LocalDate date, BigDecimal effectiveHours) {
        return new AgentDayConfig(agentId, date, effectiveHours, 60, 60,
                new BigDecimal("4.00"), new BigDecimal("1.00"), BreakAlignment.ON_HOUR, 130, 70);
    }

    // --- validWeekdays enforcement, per agent-day (UAT test 10, second gap-closure round) ---
    //
    // Found in the field, not by review. On desk Stubhub (EN) for 2026-01-05..11 the solver seated
    // a MON-FRI "Late" template on a SUNDAY and a SAT/SUN "Weekend Late" on a Monday and a
    // Wednesday. All EIGHT residual hard violations of a frozen -8 solve were weekday-invalid
    // assignments. validWeekdays was consulted by ShiftLibraryValidationService (a page advisory)
    // and ShiftLibraryGenerationService (suggestions) but by NOTHING in the solver path, so the
    // field constrained what the library ADVISED and not what the solver could DO.
    //
    // The symptom was indirect, which is why neither review nor the suite caught it: giving a
    // Sunday agent the Late envelope (12:00-21:00) when Sunday demand opens at 11:00 forces one
    // seat outside the envelope and surrenders one legal slot inside it. It therefore presented as
    // a 1:1 seat-supply shortfall — the exact fingerprint of an already-diagnosed cause — rather
    // than as a weekday error.

    @Test
    void eligibleShiftBandPairs_templateNotValidOnThatWeekday_isExcluded() {
        // The live shape: a MON-FRI template offered to a SUNDAY agent-day.
        LocalDate sunday = LocalDate.of(2026, 1, 11);
        assertThat(sunday.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SUNDAY);

        ShiftTemplate late = template("Late", LocalTime.of(12, 0), LocalTime.of(21, 0), LocalDate.of(2026, 1, 1));
        late.setValidWeekdays(java.util.EnumSet.of(
                java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
                java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY));
        ShiftBandPair pair = new ShiftBandPair(late, band(late, 240, 60)); // 8.00h net

        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setDate(sunday);
        sa.setDayConfig(dayConfig(UUID.randomUUID(), sunday, new BigDecimal("8.00")));
        sa.setDeskShiftBandPairs(List.of(pair));

        // Hours match exactly and the template IS effective on this date — the ONLY reason to
        // exclude it is the weekday. Before the fix this returned the pair.
        assertThat(late.isEffectiveOn(sunday)).isTrue();
        assertThat(sa.getEligibleShiftBandPairs()).isEmpty();
    }

    @Test
    void eligibleShiftBandPairs_weekendTemplateOnAWeekday_isExcluded() {
        // The mirror-image live case: a SAT/SUN template offered to a Monday agent-day.
        ShiftTemplate weekendLate = template("Weekend Late", LocalTime.of(11, 0), LocalTime.of(20, 0),
                LocalDate.of(2026, 1, 1));
        weekendLate.setValidWeekdays(java.util.EnumSet.of(
                java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY));
        ShiftBandPair pair = new ShiftBandPair(weekendLate, band(weekendLate, 240, 60));

        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setDate(MON);
        sa.setDayConfig(dayConfig(UUID.randomUUID(), MON, new BigDecimal("8.00")));
        sa.setDeskShiftBandPairs(List.of(pair));

        assertThat(sa.getEligibleShiftBandPairs()).isEmpty();
    }

    @Test
    void eligibleShiftBandPairs_templateValidOnThatWeekday_isStillOffered() {
        // Non-regression: narrowing by weekday must not withhold a template on a day it DOES list.
        ShiftTemplate late = template("Late", LocalTime.of(12, 0), LocalTime.of(21, 0), LocalDate.of(2026, 1, 1));
        late.setValidWeekdays(java.util.EnumSet.of(java.time.DayOfWeek.MONDAY));
        ShiftBandPair pair = new ShiftBandPair(late, band(late, 240, 60));

        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setDate(MON);
        sa.setDayConfig(dayConfig(UUID.randomUUID(), MON, new BigDecimal("8.00")));
        sa.setDeskShiftBandPairs(List.of(pair));

        assertThat(sa.getEligibleShiftBandPairs()).containsExactly(pair);
    }

    // --- CR-01 gap closure: effective-range enforcement, per agent-day ---

    @Test
    void eligibleShiftBandPairs_templateNotYetEffective_isExcluded() {
        // effectiveFrom is three months after MON — an UPCOMING template, explicitly supported
        // by the UI (ShiftLibrary.tsx's "Upcoming" badge) but must not be assignable yet.
        ShiftTemplate upcoming = template("Upcoming", LocalTime.of(8, 0), LocalTime.of(17, 0),
                MON.plusMonths(3));
        ShiftTemplateBreakBand b = band(upcoming, 240, 60); // 8.00h net, matches dayConfig below
        ShiftBandPair pair = new ShiftBandPair(upcoming, b);

        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setDate(MON);
        sa.setDayConfig(dayConfig(UUID.randomUUID(), MON, new BigDecimal("8.00")));
        sa.setDeskShiftBandPairs(List.of(pair));

        assertThat(sa.getEligibleShiftBandPairs()).isEmpty();
        assertThat(sa.getShiftBandPair()).isNull(); // stays unassigned, never silently off-library
    }

    @Test
    void eligibleShiftBandPairs_templateRetired_isExcluded() {
        ShiftTemplate retired = template("Retired", LocalTime.of(8, 0), LocalTime.of(17, 0),
                LocalDate.of(2020, 1, 1));
        retired.setEffectiveTo(MON.minusDays(1)); // retired the day before this row's date
        ShiftTemplateBreakBand b = band(retired, 240, 60);
        ShiftBandPair pair = new ShiftBandPair(retired, b);

        AgentShiftAssignment sa = new AgentShiftAssignment();
        sa.setDate(MON);
        sa.setDayConfig(dayConfig(UUID.randomUUID(), MON, new BigDecimal("8.00")));
        sa.setDeskShiftBandPairs(List.of(pair));

        assertThat(sa.getEligibleShiftBandPairs()).isEmpty();
    }

    @Test
    void eligibleShiftBandPairs_templateEffectiveForOnlyPartOfSchedulePeriod_perAgentDayEnforced() {
        // One template, effective starting mid-period (TUE). Two AgentShiftAssignment rows for
        // the SAME agent share the SAME deskShiftBandPairs list instance (mirrors
        // SolverService.buildShiftAssignments), one dated MON (before effectiveFrom), one dated
        // TUE (on effectiveFrom) — proving eligibility is enforced per row's own date, not once
        // for the whole desk/period.
        ShiftTemplate midPeriod = template("MidPeriod", LocalTime.of(8, 0), LocalTime.of(17, 0), TUE);
        ShiftTemplateBreakBand b = band(midPeriod, 240, 60); // 8.00h net
        ShiftBandPair pair = new ShiftBandPair(midPeriod, b);
        List<ShiftBandPair> sharedPairs = List.of(pair);

        AgentShiftAssignment monRow = new AgentShiftAssignment();
        monRow.setDate(MON);
        monRow.setDayConfig(dayConfig(UUID.randomUUID(), MON, new BigDecimal("8.00")));
        monRow.setDeskShiftBandPairs(sharedPairs);

        AgentShiftAssignment tueRow = new AgentShiftAssignment();
        tueRow.setDate(TUE);
        tueRow.setDayConfig(dayConfig(UUID.randomUUID(), TUE, new BigDecimal("8.00")));
        tueRow.setDeskShiftBandPairs(sharedPairs);

        assertThat(monRow.getEligibleShiftBandPairs()).isEmpty(); // before effectiveFrom
        assertThat(tueRow.getEligibleShiftBandPairs()).containsExactly(pair); // on effectiveFrom
    }

    // --- filterLiveShiftTemplates (CR-01 gap closure — desk-level pre-filter against the
    //     schedule's actual period, never LocalDate.now()) ---

    @Test
    void filterLiveShiftTemplates_excludesUpcomingTemplate_whenNoOverlapWithPeriod() {
        ShiftTemplate upcoming = template("Upcoming", LocalTime.of(8, 0), LocalTime.of(17, 0),
                MON.plusMonths(3));

        List<ShiftTemplate> live = SolverService.filterLiveShiftTemplates(
                SchedulingMode.SHIFT, List.of(upcoming), MON, TUE);

        assertThat(live).isEmpty();
    }

    @Test
    void filterLiveShiftTemplates_includesTemplateEffectiveForOnlyPartOfPeriod() {
        // effectiveFrom lands ON the period's last day -- overlaps, so must still be loaded even
        // though it is not effective for most of the period (per-day enforcement narrows it down
        // further downstream).
        ShiftTemplate midPeriod = template("MidPeriod", LocalTime.of(8, 0), LocalTime.of(17, 0), TUE);

        List<ShiftTemplate> live = SolverService.filterLiveShiftTemplates(
                SchedulingMode.SHIFT, List.of(midPeriod), MON, TUE);

        assertThat(live).containsExactly(midPeriod);
    }

    @Test
    void filterLiveShiftTemplates_excludesRetiredTemplate() {
        ShiftTemplate retired = template("Retired", LocalTime.of(8, 0), LocalTime.of(17, 0),
                LocalDate.of(2020, 1, 1));
        retired.setEffectiveTo(MON.minusDays(1));

        List<ShiftTemplate> live = SolverService.filterLiveShiftTemplates(
                SchedulingMode.SHIFT, List.of(retired), MON, TUE);

        assertThat(live).isEmpty();
    }

    @Test
    void filterLiveShiftTemplates_slotMode_returnsEmpty() {
        ShiftTemplate t = template("Early", LocalTime.of(8, 0), LocalTime.of(17, 0), LocalDate.of(2020, 1, 1));

        List<ShiftTemplate> live = SolverService.filterLiveShiftTemplates(
                SchedulingMode.SLOT, List.of(t), MON, TUE);

        assertThat(live).isEmpty();
    }
}
