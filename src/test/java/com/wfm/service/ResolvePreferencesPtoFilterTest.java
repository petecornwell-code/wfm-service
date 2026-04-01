package com.wfm.service;

import com.wfm.model.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that resolvePreferences excludes preferences on PTO / day-off dates.
 * The preference stays in the database but is not passed to the solver.
 */
class ResolvePreferencesPtoFilterTest {

    private static final long TENANT = 1L;
    // Monday 2026-03-09 through Friday 2026-03-13
    private static final LocalDate MON = LocalDate.of(2026, 3, 9);
    private static final LocalDate TUE = LocalDate.of(2026, 3, 10);
    private static final LocalDate WED = LocalDate.of(2026, 3, 11);
    private static final LocalDate THU = LocalDate.of(2026, 3, 12);
    private static final LocalDate FRI = LocalDate.of(2026, 3, 13);

    @Test
    void standingPreference_excludedOnPtoDay() throws Exception {
        UUID deskId = UUID.randomUUID();
        Agent agent = agent("A1", "Alice", deskId);

        // Standing preference for every Tuesday at 09:00
        AgentPreference standing = standingPref(agent, deskId, DayOfWeek.TUESDAY, LocalTime.of(9, 0));

        // Agent has PTO on Tuesday 2026-03-10
        Map<UUID, Set<LocalDate>> daysOffMap = Map.of(agent.getId(), Set.of(TUE));

        Schedule schedule = schedule(deskId, MON, FRI);

        List<AgentPreference> resolved = invokeResolvePreferences(
                List.of(standing), schedule, daysOffMap);

        // Tuesday preference should be excluded; no other days have a Tuesday standing pref
        assertThat(resolved).isEmpty();
    }

    @Test
    void weeklyPreference_excludedOnPtoDay() throws Exception {
        UUID deskId = UUID.randomUUID();
        Agent agent = agent("A1", "Alice", deskId);

        // Weekly preference specifically for Wednesday 2026-03-11
        AgentPreference weekly = weeklyPref(agent, deskId, WED, LocalTime.of(10, 0));

        // Agent has PTO on that Wednesday
        Map<UUID, Set<LocalDate>> daysOffMap = Map.of(agent.getId(), Set.of(WED));

        Schedule schedule = schedule(deskId, MON, FRI);

        List<AgentPreference> resolved = invokeResolvePreferences(
                List.of(weekly), schedule, daysOffMap);

        assertThat(resolved).isEmpty();
    }

    @Test
    void preference_includedOnNonPtoDay() throws Exception {
        UUID deskId = UUID.randomUUID();
        Agent agent = agent("A1", "Alice", deskId);

        // Standing preference for Tuesday at 09:00
        AgentPreference standing = standingPref(agent, deskId, DayOfWeek.TUESDAY, LocalTime.of(9, 0));

        // Agent has PTO on Wednesday (not Tuesday)
        Map<UUID, Set<LocalDate>> daysOffMap = Map.of(agent.getId(), Set.of(WED));

        Schedule schedule = schedule(deskId, MON, FRI);

        List<AgentPreference> resolved = invokeResolvePreferences(
                List.of(standing), schedule, daysOffMap);

        // Tuesday preference should still be included
        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).getDate()).isEqualTo(TUE);
        assertThat(resolved.get(0).getPreferredStartTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void mixedPreferences_onlyPtoDaysExcluded() throws Exception {
        UUID deskId = UUID.randomUUID();
        Agent agent = agent("A1", "Alice", deskId);

        // Standing preference for every weekday at 09:00
        List<AgentPreference> prefs = new ArrayList<>();
        for (DayOfWeek dow : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            prefs.add(standingPref(agent, deskId, dow, LocalTime.of(9, 0)));
        }

        // Agent has PTO on Tuesday and Thursday
        Map<UUID, Set<LocalDate>> daysOffMap = Map.of(agent.getId(), Set.of(TUE, THU));

        Schedule schedule = schedule(deskId, MON, FRI);

        List<AgentPreference> resolved = invokeResolvePreferences(
                prefs, schedule, daysOffMap);

        // Only Mon, Wed, Fri should have resolved preferences
        assertThat(resolved).hasSize(3);
        Set<LocalDate> resolvedDates = new HashSet<>();
        for (AgentPreference rp : resolved) {
            resolvedDates.add(rp.getDate());
        }
        assertThat(resolvedDates).containsExactlyInAnyOrder(MON, WED, FRI);
    }

    @Test
    void noPto_allPreferencesIncluded() throws Exception {
        UUID deskId = UUID.randomUUID();
        Agent agent = agent("A1", "Alice", deskId);

        AgentPreference standing = standingPref(agent, deskId, DayOfWeek.TUESDAY, LocalTime.of(9, 0));

        // No PTO at all
        Map<UUID, Set<LocalDate>> daysOffMap = Map.of();

        Schedule schedule = schedule(deskId, MON, FRI);

        List<AgentPreference> resolved = invokeResolvePreferences(
                List.of(standing), schedule, daysOffMap);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).getDate()).isEqualTo(TUE);
    }

    // ------------------------------------------------------------------
    //  Invoke the private resolvePreferences via reflection (SolverService)
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<AgentPreference> invokeResolvePreferences(
            List<AgentPreference> allPreferences,
            Schedule schedule,
            Map<UUID, Set<LocalDate>> agentDaysOffMap) throws Exception {

        // SolverService has many constructor dependencies; we only need the private method,
        // so instantiate via Unsafe-style: get declared constructor, pass nulls for deps.
        SolverService service = createSolverServiceWithNullDeps();

        Method method = SolverService.class.getDeclaredMethod(
                "resolvePreferences", List.class, Schedule.class, Map.class);
        method.setAccessible(true);
        return (List<AgentPreference>) method.invoke(service, allPreferences, schedule, agentDaysOffMap);
    }

    private SolverService createSolverServiceWithNullDeps() throws Exception {
        var ctors = SolverService.class.getDeclaredConstructors();
        var ctor = ctors[0];
        ctor.setAccessible(true);
        Object[] nullArgs = new Object[ctor.getParameterCount()];
        return (SolverService) ctor.newInstance(nullArgs);
    }

    // ------------------------------------------------------------------
    //  Factory helpers
    // ------------------------------------------------------------------

    private Agent agent(String bambooId, String name, UUID deskId) {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        a.setTenantId(TENANT);
        a.setBamboohrId(bambooId);
        a.setName(name);
        a.setDeskId(deskId);
        a.setActive(true);
        return a;
    }

    private AgentPreference standingPref(Agent agent, UUID deskId, DayOfWeek dow, LocalTime startTime) {
        AgentPreference p = new AgentPreference();
        p.setId(UUID.randomUUID());
        p.setTenantId(TENANT);
        p.setDeskId(deskId);
        p.setAgent(agent);
        p.setDayOfWeek(dow);
        p.setStanding(true);
        p.setPreferredStartTime(startTime);
        return p;
    }

    private AgentPreference weeklyPref(Agent agent, UUID deskId, LocalDate date, LocalTime startTime) {
        AgentPreference p = new AgentPreference();
        p.setId(UUID.randomUUID());
        p.setTenantId(TENANT);
        p.setDeskId(deskId);
        p.setAgent(agent);
        p.setDayOfWeek(date.getDayOfWeek());
        p.setDate(date);
        p.setStanding(false);
        p.setPreferredStartTime(startTime);
        return p;
    }

    private Schedule schedule(UUID deskId, LocalDate start, LocalDate end) {
        Schedule s = new Schedule();
        s.setId(UUID.randomUUID());
        s.setTenantId(TENANT);
        s.setDeskId(deskId);
        s.setPeriodStartDate(start);
        s.setPeriodEndDate(end);
        return s;
    }
}
