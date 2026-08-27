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
                SchedulingMode.SHIFT, 1L, UUID.randomUUID(), UUID.randomUUID(), agentById, configs, List.of());

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
                SchedulingMode.SHIFT, 1L, UUID.randomUUID(), UUID.randomUUID(), agentById, configs, pairs);

        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).getDeskShiftBandPairs()).isSameAs(pairs);
    }

    @Test
    void buildShiftAssignments_slotMode_returnsEmpty() {
        Agent a1 = agent();
        Map<UUID, Agent> agentById = Map.of(a1.getId(), a1);
        List<AgentDayConfig> configs = List.of(dayConfig(a1.getId(), MON, new BigDecimal("8.00")));

        List<AgentShiftAssignment> assignments = SolverService.buildShiftAssignments(
                SchedulingMode.SLOT, 1L, UUID.randomUUID(), UUID.randomUUID(), agentById, configs, List.of());

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
        sa.setDayConfig(dayConfig(UUID.randomUUID(), MON, new BigDecimal("8.00")));
        sa.setDeskShiftBandPairs(List.of(pair));

        assertThat(sa.getEligibleShiftBandPairs()).containsExactly(pair);
    }
}
