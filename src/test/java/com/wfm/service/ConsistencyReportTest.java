package com.wfm.service;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import com.wfm.dto.ScheduleDetailResponse.ConsistencyReport;
import com.wfm.dto.ScheduleDetailResponse.ConsistencyReportEntry;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.Schedule;
import com.wfm.model.Timeslot;
import com.wfm.solver.ScheduleConstraintProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link ScheduleOutputService#buildConsistencyReport}, Stage 3 of the consistency plan.
 *
 * <p>Stages 0 to 2 shape start and break consistency, but their effect only shows up inside an
 * aggregate soft score that conflates all fourteen constraints — a solve could look better
 * while consistency got worse. This report is what makes the plan's target checkable: at least
 * 80% of agents on an identical start every worked day, and no spread over two hours.
 *
 * <p>The report must agree with the solver's definitions, so these tests pin the same ones:
 * a day's start is its earliest assigned slot, and a break offset is the distance from that
 * start to the first gap.
 */
class ConsistencyReportTest {

    private static final LocalDate MON = LocalDate.of(2026, 1, 5);
    private static final LocalDate TUE = LocalDate.of(2026, 1, 6);
    private static final LocalDate WED = LocalDate.of(2026, 1, 7);

    private final ScheduleOutputService service = new ScheduleOutputService(solverFactory());

    private static SolverFactory<Schedule> solverFactory() {
        return SolverFactory.create(new SolverConfig()
                .withSolutionClass(Schedule.class)
                .withEntityClasses(AgentAssignment.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(ScheduleConstraintProvider.class)));
    }

    private static Agent agent(String name) {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        a.setName(name);
        return a;
    }

    private static AgentAssignment assignment(Agent agent, LocalDate date, LocalTime start) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(date);
        ts.setStartTime(start);
        ts.setEndTime(start.plusHours(1));

        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(ts);
        a.setAgent(agent);
        return a;
    }

    /** A shift with {@code beforeBreak} hours worked, a one-hour break, then {@code afterBreak}. */
    private static List<AgentAssignment> shiftWithBreak(Agent agent, LocalDate date, LocalTime from,
                                                        int beforeBreak, int afterBreak) {
        List<AgentAssignment> assignments = new ArrayList<>();
        LocalTime t = from;
        for (int i = 0; i < beforeBreak; i++) {
            assignments.add(assignment(agent, date, t));
            t = t.plusHours(1);
        }
        t = t.plusHours(1);
        for (int i = 0; i < afterBreak; i++) {
            assignments.add(assignment(agent, date, t));
            t = t.plusHours(1);
        }
        return assignments;
    }

    private static List<AgentAssignment> unbrokenShift(Agent agent, LocalDate date, LocalTime from,
                                                       int hours) {
        List<AgentAssignment> assignments = new ArrayList<>();
        LocalTime t = from;
        for (int i = 0; i < hours; i++) {
            assignments.add(assignment(agent, date, t));
            t = t.plusHours(1);
        }
        return assignments;
    }

    private static Schedule schedule(List<AgentAssignment> assignments) {
        Schedule s = new Schedule();
        s.setId(UUID.randomUUID());
        s.setIncrementMinutes(60);
        s.setBreakDurationMinutes(60);
        s.setAssignments(assignments);
        s.setAgentPreferences(List.of());
        return s;
    }

    private static ConsistencyReportEntry entryFor(ConsistencyReport report, Agent agent) {
        return report.entries().stream()
                .filter(e -> e.agentId().equals(agent.getId()))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("a perfectly steady agent reports zero spread on both measures")
    void steadyAgentHasNoSpread() {
        Agent a = agent("Agent One");
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(shiftWithBreak(a, MON, LocalTime.of(9, 0), 4, 4));
        all.addAll(shiftWithBreak(a, TUE, LocalTime.of(9, 0), 4, 4));

        ConsistencyReportEntry e = entryFor(service.buildConsistencyReport(schedule(all)), a);
        assertThat(e.daysWorked()).isEqualTo(2);
        assertThat(e.earliestStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(e.latestStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(e.startSpreadMinutes()).isZero();
        assertThat(e.daysWithBreak()).isEqualTo(2);
        assertThat(e.breakOffsetSpreadMinutes()).isZero();
    }

    @Test
    @DisplayName("start spread is reported in minutes, end to end")
    void startSpreadIsReportedInMinutes() {
        Agent a = agent("Agent One");
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(shiftWithBreak(a, MON, LocalTime.of(9, 0), 4, 4));
        all.addAll(shiftWithBreak(a, TUE, LocalTime.of(11, 0), 4, 4));
        all.addAll(shiftWithBreak(a, WED, LocalTime.of(10, 0), 4, 4));

        ConsistencyReportEntry e = entryFor(service.buildConsistencyReport(schedule(all)), a);
        assertThat(e.earliestStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(e.latestStart()).isEqualTo(LocalTime.of(11, 0));
        assertThat(e.startSpreadMinutes()).isEqualTo(120);
    }

    @Test
    @DisplayName("break spread is an offset, so a shifted start with the same rhythm reads zero")
    void breakSpreadFollowsTheShiftNotTheClock() {
        // Matches consistentBreakOffset: 09:00 breaking at 13:00 and 10:00 breaking at 14:00 are
        // both four hours in. The start inconsistency shows up in startSpreadMinutes alone.
        Agent a = agent("Agent One");
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(shiftWithBreak(a, MON, LocalTime.of(9, 0), 4, 4));
        all.addAll(shiftWithBreak(a, TUE, LocalTime.of(10, 0), 4, 4));

        ConsistencyReportEntry e = entryFor(service.buildConsistencyReport(schedule(all)), a);
        assertThat(e.startSpreadMinutes()).isEqualTo(60);
        assertThat(e.minBreakOffsetMinutes()).isEqualTo(240);
        assertThat(e.maxBreakOffsetMinutes()).isEqualTo(240);
        assertThat(e.breakOffsetSpreadMinutes()).isZero();
    }

    @Test
    @DisplayName("a day's start is its earliest slot, even on a split shift")
    void splitShiftStartsAtItsEarliestSlot() {
        Agent a = agent("Agent One");
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(unbrokenShift(a, MON, LocalTime.of(9, 0), 8));
        all.addAll(unbrokenShift(a, TUE, LocalTime.of(8, 0), 1));
        all.addAll(unbrokenShift(a, TUE, LocalTime.of(14, 0), 6));

        ConsistencyReportEntry e = entryFor(service.buildConsistencyReport(schedule(all)), a);
        assertThat(e.earliestStart()).isEqualTo(LocalTime.of(8, 0));
        assertThat(e.startSpreadMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("an agent whose days carry no break reports null break fields")
    void agentWithoutBreaksHasNullBreakFields() {
        Agent a = agent("Agent One");
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(unbrokenShift(a, MON, LocalTime.of(9, 0), 3));
        all.addAll(unbrokenShift(a, TUE, LocalTime.of(9, 0), 3));

        ConsistencyReportEntry e = entryFor(service.buildConsistencyReport(schedule(all)), a);
        assertThat(e.daysWithBreak()).isZero();
        assertThat(e.minBreakOffsetMinutes()).isNull();
        assertThat(e.maxBreakOffsetMinutes()).isNull();
        assertThat(e.breakOffsetSpreadMinutes()).isNull();
    }

    @Test
    @DisplayName("an agent with no assignments is absent, not present with zero spread")
    void unscheduledAgentIsAbsent() {
        // Counting unscheduled agents as perfectly consistent would inflate every percentage.
        Agent scheduled = agent("Agent One");
        ConsistencyReport report = service.buildConsistencyReport(
                schedule(new ArrayList<>(unbrokenShift(scheduled, MON, LocalTime.of(9, 0), 8))));

        assertThat(report.entries()).hasSize(1);
        assertThat(report.summary().totalAgents()).isEqualTo(1);
    }

    @Test
    @DisplayName("unassigned seats do not create an agent row")
    void unassignedSeatsAreIgnored() {
        Agent a = agent("Agent One");
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(unbrokenShift(a, MON, LocalTime.of(9, 0), 8));
        all.addAll(unbrokenShift(null, MON, LocalTime.of(9, 0), 8));

        ConsistencyReport report = service.buildConsistencyReport(schedule(all));
        assertThat(report.entries()).hasSize(1);
        assertThat(entryFor(report, a).daysWorked()).isEqualTo(1);
    }

    @Test
    @DisplayName("the summary answers the plan's target directly")
    void summaryScoresAgainstTheTarget() {
        // Three agents: one identical, one 1h out (inside the 2h target), one 4h out.
        Agent steady = agent("Agent One");
        Agent slight = agent("Agent Two");
        Agent wild = agent("Agent Three");
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(shiftWithBreak(steady, MON, LocalTime.of(9, 0), 4, 4));
        all.addAll(shiftWithBreak(steady, TUE, LocalTime.of(9, 0), 4, 4));
        all.addAll(shiftWithBreak(slight, MON, LocalTime.of(9, 0), 4, 4));
        all.addAll(shiftWithBreak(slight, TUE, LocalTime.of(10, 0), 4, 4));
        all.addAll(shiftWithBreak(wild, MON, LocalTime.of(9, 0), 4, 4));
        all.addAll(shiftWithBreak(wild, TUE, LocalTime.of(13, 0), 4, 4));

        var summary = service.buildConsistencyReport(schedule(all)).summary();
        assertThat(summary.totalAgents()).isEqualTo(3);
        assertThat(summary.identicalStartAgents()).isEqualTo(1);
        assertThat(summary.identicalStartPct()).isEqualByComparingTo("33.33");
        assertThat(summary.startSpreadTargetMinutes()).isEqualTo(120);
        assertThat(summary.startSpreadWithinTargetAgents()).isEqualTo(2);
        assertThat(summary.startSpreadWithinTargetPct()).isEqualByComparingTo("66.67");
        assertThat(summary.maxStartSpreadMinutes()).isEqualTo(240);
    }

    @Test
    @DisplayName("a spread exactly on the target counts as within it")
    void targetBoundaryIsInclusive() {
        Agent a = agent("Agent One");
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(shiftWithBreak(a, MON, LocalTime.of(9, 0), 4, 4));
        all.addAll(shiftWithBreak(a, TUE, LocalTime.of(11, 0), 4, 4));

        var summary = service.buildConsistencyReport(schedule(all)).summary();
        assertThat(summary.maxStartSpreadMinutes()).isEqualTo(120);
        assertThat(summary.startSpreadWithinTargetAgents()).isEqualTo(1);
    }

    @Test
    @DisplayName("single-day agents are counted but surfaced separately")
    void singleDayAgentsAreSurfaced() {
        // They are trivially consistent. Counting them silently would let a headline percentage
        // rest on agents who never had a chance to be inconsistent.
        Agent oneDay = agent("Agent One");
        Agent fullWeek = agent("Agent Two");
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(shiftWithBreak(oneDay, MON, LocalTime.of(9, 0), 4, 4));
        all.addAll(shiftWithBreak(fullWeek, MON, LocalTime.of(9, 0), 4, 4));
        all.addAll(shiftWithBreak(fullWeek, TUE, LocalTime.of(14, 0), 4, 4));

        var summary = service.buildConsistencyReport(schedule(all)).summary();
        assertThat(summary.totalAgents()).isEqualTo(2);
        assertThat(summary.singleDayAgents()).isEqualTo(1);
        assertThat(summary.identicalStartAgents()).isEqualTo(1);
    }

    @Test
    @DisplayName("break percentages are measured over agents that have break data")
    void breakPercentagesExcludeBreaklessAgents() {
        Agent withBreaks = agent("Agent One");
        Agent withoutBreaks = agent("Agent Two");
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(shiftWithBreak(withBreaks, MON, LocalTime.of(9, 0), 4, 4));
        all.addAll(shiftWithBreak(withBreaks, TUE, LocalTime.of(9, 0), 4, 4));
        all.addAll(unbrokenShift(withoutBreaks, MON, LocalTime.of(9, 0), 3));
        all.addAll(unbrokenShift(withoutBreaks, TUE, LocalTime.of(9, 0), 3));

        var summary = service.buildConsistencyReport(schedule(all)).summary();
        assertThat(summary.totalAgents()).isEqualTo(2);
        assertThat(summary.agentsWithBreakData()).isEqualTo(1);
        assertThat(summary.identicalBreakOffsetAgents()).isEqualTo(1);
        assertThat(summary.identicalBreakOffsetPct()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("an empty schedule reports 0%, not 100%")
    void emptyScheduleReportsZeroPercent() {
        var report = service.buildConsistencyReport(schedule(List.of()));
        assertThat(report.entries()).isEmpty();
        assertThat(report.summary().totalAgents()).isZero();
        assertThat(report.summary().identicalStartPct()).isEqualByComparingTo("0.00");
        assertThat(report.summary().identicalBreakOffsetPct()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("entries are sorted by agent name")
    void entriesAreSortedByName() {
        Agent zoe = agent("Zoe");
        Agent adam = agent("Adam");
        List<AgentAssignment> all = new ArrayList<>();
        all.addAll(unbrokenShift(zoe, MON, LocalTime.of(9, 0), 8));
        all.addAll(unbrokenShift(adam, MON, LocalTime.of(9, 0), 8));

        var report = service.buildConsistencyReport(schedule(all));
        assertThat(report.entries()).extracting(ConsistencyReportEntry::agentName)
                .containsExactly("Adam", "Zoe");
    }
}
