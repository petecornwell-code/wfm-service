package com.wfm.service;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import com.wfm.dto.ScheduleDetailResponse.PreferenceReport;
import com.wfm.dto.ScheduleDetailResponse.PreferenceReportEntry;
import com.wfm.model.Agent;
import com.wfm.model.AgentAssignment;
import com.wfm.model.AgentPreference;
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
 * Covers the start-time honour flag in {@link ScheduleOutputService#buildPreferenceReport} after
 * it stopped being one-directional.
 *
 * <p>The flag used to be {@code actualStart >= preferredStart}, which mirrored the old
 * {@code honourPreferredStartTime} constraint. Once that constraint began charging deviation in
 * both directions, the flag would have reported an agent placed hours late as honoured while the
 * solver was penalising it. It is now a symmetric band of one timeslot increment.
 */
class PreferenceReportToleranceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);

    private final ScheduleOutputService service = new ScheduleOutputService(solverFactory());

    private static SolverFactory<Schedule> solverFactory() {
        // buildPreferenceReport derives everything from raw assignments and never touches the
        // SolutionManager, but the constructor builds one, so a minimal real factory is enough.
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

    private static AgentAssignment assignment(Agent agent, LocalDate date, LocalTime start,
                                              int incrementMinutes) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(date);
        ts.setStartTime(start);
        ts.setEndTime(start.plusMinutes(incrementMinutes));

        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(ts);
        a.setAgent(agent);
        return a;
    }

    /** A contiguous run of {@code slots} assignments for {@code agent}, starting at {@code from}. */
    private static List<AgentAssignment> shift(Agent agent, LocalTime from, int slots,
                                               int incrementMinutes) {
        List<AgentAssignment> assignments = new ArrayList<>();
        LocalTime t = from;
        for (int i = 0; i < slots; i++) {
            assignments.add(assignment(agent, MONDAY, t, incrementMinutes));
            t = t.plusMinutes(incrementMinutes);
        }
        return assignments;
    }

    private static AgentPreference preference(Agent agent, LocalTime preferredStart) {
        AgentPreference p = new AgentPreference();
        p.setId(UUID.randomUUID());
        p.setAgent(agent);
        p.setDate(MONDAY);
        p.setDayOfWeek(MONDAY.getDayOfWeek());
        p.setStanding(true);
        p.setPreferredStartTime(preferredStart);
        return p;
    }

    private static Schedule schedule(int incrementMinutes, List<AgentAssignment> assignments,
                                     List<AgentPreference> preferences) {
        Schedule s = new Schedule();
        s.setId(UUID.randomUUID());
        s.setIncrementMinutes(incrementMinutes);
        s.setBreakDurationMinutes(60);
        s.setAssignments(assignments);
        s.setAgentPreferences(preferences);
        return s;
    }

    /** Runs the report for a single agent starting at {@code actualStart} and returns its entry. */
    private PreferenceReportEntry reportFor(int incrementMinutes, LocalTime actualStart,
                                            LocalTime preferredStart) {
        Agent a = agent("Agent One");
        List<AgentAssignment> assignments = shift(a, actualStart, 8, incrementMinutes);
        PreferenceReport report = service.buildPreferenceReport(
                schedule(incrementMinutes, assignments, List.of(preference(a, preferredStart))));
        assertThat(report.entries()).hasSize(1);
        return report.entries().get(0);
    }

    @Test
    @DisplayName("an exact start is honoured")
    void exactStartIsHonoured() {
        PreferenceReportEntry entry = reportFor(60, LocalTime.of(9, 0), LocalTime.of(9, 0));
        assertThat(entry.actualStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(entry.startTimeHonoured()).isTrue();
    }

    @Test
    @DisplayName("one increment early is inside the band")
    void oneIncrementEarlyIsHonoured() {
        assertThat(reportFor(60, LocalTime.of(8, 0), LocalTime.of(9, 0)).startTimeHonoured())
                .isTrue();
    }

    @Test
    @DisplayName("one increment late is inside the band")
    void oneIncrementLateIsHonoured() {
        assertThat(reportFor(60, LocalTime.of(10, 0), LocalTime.of(9, 0)).startTimeHonoured())
                .isTrue();
    }

    @Test
    @DisplayName("two increments early falls outside the band")
    void twoIncrementsEarlyIsNotHonoured() {
        assertThat(reportFor(60, LocalTime.of(7, 0), LocalTime.of(9, 0)).startTimeHonoured())
                .isFalse();
    }

    @Test
    @DisplayName("a late start is no longer honoured by default — this is the amendment")
    void lateStartIsNotHonoured() {
        // The old flag was actualStart >= preferredStart, so 14:00 against a 09:00 preference
        // counted as honoured. The solver now charges five increments for it.
        assertThat(reportFor(60, LocalTime.of(14, 0), LocalTime.of(9, 0)).startTimeHonoured())
                .isFalse();
    }

    @Test
    @DisplayName("the band is symmetric")
    void bandIsSymmetric() {
        assertThat(reportFor(60, LocalTime.of(6, 0), LocalTime.of(9, 0)).startTimeHonoured())
                .isEqualTo(reportFor(60, LocalTime.of(12, 0), LocalTime.of(9, 0)).startTimeHonoured());
    }

    @Test
    @DisplayName("the band scales with the schedule's increment")
    void bandFollowsTheIncrement() {
        // A one-hour miss is one increment on a 60-minute schedule and four on a 15-minute one.
        assertThat(reportFor(60, LocalTime.of(10, 0), LocalTime.of(9, 0)).startTimeHonoured())
                .isTrue();
        assertThat(reportFor(15, LocalTime.of(10, 0), LocalTime.of(9, 0)).startTimeHonoured())
                .isFalse();
    }

    @Test
    @DisplayName("a preference no slot boundary can hit is still honoured by the best placement")
    void unhittablePreferenceIsHonouredByTheClosestSlot() {
        // 09:30 against hourly slots: this is why the flag is a band rather than an exact match.
        assertThat(reportFor(60, LocalTime.of(9, 0), LocalTime.of(9, 30)).startTimeHonoured())
                .isTrue();
        assertThat(reportFor(60, LocalTime.of(10, 0), LocalTime.of(9, 30)).startTimeHonoured())
                .isTrue();
    }

    @Test
    @DisplayName("an agent not scheduled that day is not honoured")
    void unscheduledAgentIsNotHonoured() {
        Agent scheduled = agent("Agent One");
        Agent absent = agent("Agent Two");
        PreferenceReport report = service.buildPreferenceReport(schedule(
                60,
                shift(scheduled, LocalTime.of(9, 0), 8, 60),
                List.of(preference(scheduled, LocalTime.of(9, 0)),
                        preference(absent, LocalTime.of(9, 0)))));

        PreferenceReportEntry absentEntry = report.entries().stream()
                .filter(e -> e.agentId().equals(absent.getId()))
                .findFirst().orElseThrow();
        assertThat(absentEntry.actualStartTime()).isNull();
        assertThat(absentEntry.startTimeHonoured()).isFalse();
    }

    @Test
    @DisplayName("the summary counts only entries inside the band")
    void summaryCountsBandedEntries() {
        Agent onTime = agent("Agent One");
        Agent late = agent("Agent Two");
        List<AgentAssignment> assignments = new ArrayList<>();
        assignments.addAll(shift(onTime, LocalTime.of(9, 0), 8, 60));
        assignments.addAll(shift(late, LocalTime.of(15, 0), 8, 60));

        PreferenceReport report = service.buildPreferenceReport(schedule(
                60, assignments,
                List.of(preference(onTime, LocalTime.of(9, 0)),
                        preference(late, LocalTime.of(9, 0)))));

        assertThat(report.summary().totalPreferences()).isEqualTo(2);
        assertThat(report.summary().startTimeHonouredCount()).isEqualTo(1);
    }
}
