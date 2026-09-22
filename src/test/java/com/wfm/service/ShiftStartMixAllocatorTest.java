package com.wfm.service;

import com.wfm.model.Agent;
import com.wfm.model.AgentDayConfig;
import com.wfm.model.AgentShiftAssignment;
import com.wfm.model.BreakAlignment;
import com.wfm.model.ResolvedUsualShiftTarget;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftStartMixTarget;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Specialization;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The allocator's job is to be safe when it is wrong, not just correct when it is right.
 *
 * <p>{@code AgentShiftAssignment.shiftBandPair} is declared {@code allowsUnassigned = true}, so a
 * row narrowed to an empty value range does not throw — it stays quietly unassigned, its seats
 * then cannot comply with an envelope that does not exist, and contracted hours go under, far from
 * the cause. Most of what is asserted here is that no such row is ever produced, and that a
 * refusal leaves every row exactly as it found it.
 */
class ShiftStartMixAllocatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 31); // Monday
    private static final LocalTime EIGHT = LocalTime.of(8, 0);
    private static final LocalTime TWELVE = LocalTime.of(12, 0);

    private final ShiftStartMixAllocator allocator = new ShiftStartMixAllocator();

    @Test
    void narrowsEachRowToItsAllocatedStartAndNothingElse() {
        List<AgentShiftAssignment> rows = rows(6);
        List<ShiftStartMixTarget> targets = List.of(
                new ShiftStartMixTarget(DAY, EIGHT, 4), new ShiftStartMixTarget(DAY, TWELVE, 2));

        assertThat(allocator.allocate(rows, targets, List.of()).applied()).isTrue();

        assertThat(rows).allSatisfy(r -> assertThat(r.getAllocatedShiftBandPairs()).isNotEmpty());
        assertThat(rows).allSatisfy(r -> assertThat(r.getAllocatedShiftBandPairs())
                .allSatisfy(p -> assertThat(p.template().getStartTime())
                        .isEqualTo(r.getAllocatedShiftBandPairs().get(0).template().getStartTime())));
        assertThat(startCounts(rows)).containsExactlyInAnyOrderEntriesOf(
                java.util.Map.of(EIGHT, 4L, TWELVE, 2L));
        // Narrowing filters, never replaces: each row keeps every band of its allocated start.
        assertThat(rows.get(0).getAllocatedShiftBandPairs()).hasSize(2);
    }

    @Test
    void honoursUsualStartsWhenAllocatingWhoGetsWhich() {
        List<AgentShiftAssignment> rows = rows(6);
        List<ResolvedUsualShiftTarget> usual = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            usual.add(new ResolvedUsualShiftTarget(rows.get(i).getAgent().getId(), DAY, TWELVE));
        }
        List<ShiftStartMixTarget> targets = List.of(
                new ShiftStartMixTarget(DAY, EIGHT, 4), new ShiftStartMixTarget(DAY, TWELVE, 2));

        assertThat(allocator.allocate(rows, targets, usual).applied()).isTrue();

        // The only two agents wanting 12:00 must be the two who get it — the exact-match pass is
        // what makes the mix worth targeting at all.
        assertThat(rows.get(0).getAllocatedShiftBandPairs().get(0).template().getStartTime()).isEqualTo(TWELVE);
        assertThat(rows.get(1).getAllocatedShiftBandPairs().get(0).template().getStartTime()).isEqualTo(TWELVE);
    }

    @Test
    void refusesAndTouchesNothingWhenAStartHasNoEligibleEnvelope() {
        List<AgentShiftAssignment> rows = rows(6);
        // 10:00 is not in the library, so two agent-days would be narrowed to an empty range.
        List<ShiftStartMixTarget> targets = List.of(
                new ShiftStartMixTarget(DAY, EIGHT, 4),
                new ShiftStartMixTarget(DAY, LocalTime.of(10, 0), 2));

        ShiftStartMixAllocator.Allocation result = allocator.allocate(rows, targets, List.of());

        assertThat(result.applied()).isFalse();
        assertThat(result.skippedReason()).contains("10:00");
        assertThat(rows).allSatisfy(r -> assertThat(r.getAllocatedShiftBandPairs())
                .as("a refusal must leave every row unnarrowed, including rows on legal starts")
                .isNull());
    }

    @Test
    void refusesWhenTargetsDoNotAccountForEveryWorkingAgentDay() {
        List<AgentShiftAssignment> rows = rows(6);
        List<ShiftStartMixTarget> targets = List.of(new ShiftStartMixTarget(DAY, EIGHT, 5));

        ShiftStartMixAllocator.Allocation result = allocator.allocate(rows, targets, List.of());

        assertThat(result.applied()).isFalse();
        assertThat(result.skippedReason()).contains("5").contains("6");
        assertThat(rows).allSatisfy(r -> assertThat(r.getAllocatedShiftBandPairs()).isNull());
    }

    @Test
    void refusesWhenAgentDaysOnADateDoNotShareOneEligibleEnvelopeSet() {
        // Same substitutability class — contracted hours untouched — but a short effective day,
        // which the class key cannot see and which changes that row's eligible envelopes.
        List<AgentShiftAssignment> rows = rows(6);
        AgentShiftAssignment odd = rows.get(0);
        odd.setDayConfig(new AgentDayConfig(odd.getAgent().getId(), DAY, new BigDecimal("6.0"),
                60, 60, new BigDecimal("4.0"), BigDecimal.ONE, BreakAlignment.ON_HALF_HOUR, 200, 70));
        List<ShiftStartMixTarget> targets = List.of(
                new ShiftStartMixTarget(DAY, EIGHT, 4), new ShiftStartMixTarget(DAY, TWELVE, 2));

        ShiftStartMixAllocator.Allocation result = allocator.allocate(rows, targets, List.of());

        assertThat(result.applied()).isFalse();
        assertThat(result.skippedReason()).contains("eligible-envelope set");
        assertThat(rows).allSatisfy(r -> assertThat(r.getAllocatedShiftBandPairs()).isNull());
    }

    @Test
    void aLaterDatesFailureUnwindsAnEarlierDatesAllocation() {
        // The staging property, stated as a test: allocation is written only after every date has
        // been checked, so a refusal on day 2 cannot leave day 1 half-narrowed.
        List<AgentShiftAssignment> rows = new ArrayList<>(rows(6));
        List<AgentShiftAssignment> nextDay = rows(6, DAY.plusDays(1));
        rows.addAll(nextDay);
        List<ShiftStartMixTarget> targets = List.of(
                new ShiftStartMixTarget(DAY, EIGHT, 4), new ShiftStartMixTarget(DAY, TWELVE, 2),
                new ShiftStartMixTarget(DAY.plusDays(1), LocalTime.of(10, 0), 6));

        ShiftStartMixAllocator.Allocation result = allocator.allocate(rows, targets, List.of());

        assertThat(result.applied()).isFalse();
        assertThat(rows).allSatisfy(r -> assertThat(r.getAllocatedShiftBandPairs()).isNull());
    }

    @Test
    void leavesADateWithNoTargetEntirelyAlone() {
        List<AgentShiftAssignment> rows = new ArrayList<>(rows(6));
        List<AgentShiftAssignment> untargeted = rows(6, DAY.plusDays(1));
        rows.addAll(untargeted);
        List<ShiftStartMixTarget> targets = List.of(
                new ShiftStartMixTarget(DAY, EIGHT, 4), new ShiftStartMixTarget(DAY, TWELVE, 2));

        assertThat(allocator.allocate(rows, targets, List.of()).applied()).isTrue();

        assertThat(untargeted).allSatisfy(r -> assertThat(r.getAllocatedShiftBandPairs()).isNull());
    }

    // ---------- fixture ----------

    private static java.util.Map<LocalTime, Long> startCounts(List<AgentShiftAssignment> rows) {
        return rows.stream().collect(Collectors.groupingBy(
                r -> r.getAllocatedShiftBandPairs().get(0).template().getStartTime(), Collectors.counting()));
    }

    private List<AgentShiftAssignment> rows(int n) {
        return rows(n, DAY);
    }

    /** {@code n} working agent-days on {@code date}, one substitutability class, two 8h templates. */
    private List<AgentShiftAssignment> rows(int n, LocalDate date) {
        Specialization spec = new Specialization();
        spec.setId(UUID.randomUUID());
        spec.setName("Support");

        List<ShiftBandPair> pairs = new ArrayList<>();
        for (LocalTime start : List.of(EIGHT, TWELVE)) {
            ShiftTemplate t = new ShiftTemplate();
            t.setId(UUID.randomUUID());
            t.setName("T-" + start);
            t.setStartTime(start);
            t.setEndTime(start.plusHours(9));
            t.setEffectiveFrom(LocalDate.of(2026, 1, 1));
            t.setValidWeekdays(EnumSet.allOf(DayOfWeek.class));
            for (int b = 0; b < 2; b++) {
                ShiftTemplateBreakBand band = new ShiftTemplateBreakBand();
                band.setId(UUID.randomUUID());
                band.setShiftTemplate(t);
                band.setOffsetMinutes(180 + b * 60);
                band.setDurationMinutes(60);
                pairs.add(new ShiftBandPair(t, band));
            }
        }

        List<AgentShiftAssignment> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Agent a = new Agent();
            a.setId(UUID.randomUUID());
            a.setName("Agent-" + i);
            a.setPrimarySpecialization(spec);
            a.setContractedHoursPerDay(new BigDecimal("8.0"));
            AgentShiftAssignment sa = new AgentShiftAssignment();
            sa.setId(UUID.randomUUID());
            sa.setAgent(a);
            sa.setDate(date);
            sa.setDayConfig(new AgentDayConfig(a.getId(), date, new BigDecimal("8.0"), 60, 60,
                    new BigDecimal("4.0"), BigDecimal.ONE, BreakAlignment.ON_HALF_HOUR, 200, 70));
            sa.setDeskShiftBandPairs(pairs);
            sa.setEnvelopeSlackSlots(0);
            rows.add(sa);
        }
        return rows;
    }
}
