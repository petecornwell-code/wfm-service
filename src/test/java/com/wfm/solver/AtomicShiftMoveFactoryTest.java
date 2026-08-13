package com.wfm.solver;

import ai.timefold.solver.core.impl.heuristic.move.CompositeMove;
import ai.timefold.solver.core.impl.heuristic.move.Move;
import com.wfm.model.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AtomicShiftMoveFactory} contract coverage: under-hours filtering,
 * the atomic clear-and-replace rewrite for a pinned agent-day, foreign-seat
 * and specialization exclusion, date isolation, the move-pool bound, and
 * read-only generation. {@code AtomicShiftMoveFactory} is instantiated
 * directly and {@code createMoveList(Schedule)} is called without a solver
 * bootstrap, reusing the fixture-construction style of
 * {@code BreakAwareConstructionTest}.
 */
class AtomicShiftMoveFactoryTest {

    private static final long TENANT = 1L;
    private static final UUID DESK_ID = UUID.randomUUID();
    private static final UUID SCHEDULE_ID = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 3, 16);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 3, 17);
    private static final LocalTime START = LocalTime.of(9, 0);
    private static final int INCREMENT = 60;
    private static final int BREAK_DURATION = 60;
    private static final BigDecimal BREAK_BLOCKED = new BigDecimal("1.00");
    private static final BigDecimal BREAK_MIN_SHIFT = new BigDecimal("4.00");
    private static final BreakAlignment BREAK_ALIGNMENT = BreakAlignment.ON_HOUR;
    private static final BigDecimal CONTRACTED_HOURS = new BigDecimal("8.00");

    @Test
    void satisfiedAgentDay_producesNoMoves() {
        Specialization spec = spec("IT Support");
        Agent agent = agent(spec);
        List<AgentAssignment> seats = hourlySeats(spec, DAY, START, 8); // exactly requiredWorkSlots
        seats.forEach(s -> s.setAgent(agent));

        Schedule schedule = schedule(List.of(agent), seats, List.of(dayConfig(agent.getId(), DAY)));

        List<? extends Move<Schedule>> moves = new AtomicShiftMoveFactory().createMoveList(schedule);

        assertThat(moves).isEmpty();
    }

    @Test
    void overHoursAgentDay_producesNoMoves() {
        Specialization spec = spec("IT Support");
        Agent agent = agent(spec);
        List<AgentAssignment> seats = hourlySeats(spec, DAY, START, 10); // more than requiredWorkSlots (8)
        seats.forEach(s -> s.setAgent(agent));

        Schedule schedule = schedule(List.of(agent), seats, List.of(dayConfig(agent.getId(), DAY)));

        List<? extends Move<Schedule>> moves = new AtomicShiftMoveFactory().createMoveList(schedule);

        assertThat(moves).isEmpty();
    }

    @Test
    void emptyAgentDay_producesMoves() {
        Specialization spec = spec("IT Support");
        Agent agent = agent(spec);
        List<AgentAssignment> seats = hourlySeats(spec, DAY, START, 12); // all free

        Schedule schedule = schedule(List.of(agent), seats, List.of(dayConfig(agent.getId(), DAY)));

        List<? extends Move<Schedule>> moves = new AtomicShiftMoveFactory().createMoveList(schedule);

        assertThat(moves).isNotEmpty();
    }

    @Test
    void pinnedAgentDay_isRewrittenAtomically() {
        Specialization spec = spec("IT Support");
        Agent agent = agent(spec);
        List<AgentAssignment> seats = hourlySeats(spec, DAY, START, 12);
        // breakThresholdSlots = ceil(4.00h * 60 / 60) = 4; pin the agent at
        // breakThresholdSlots - 1 = 3 contiguous slots with no break — the
        // live symptom from the ROADMAP evidence.
        List<AgentAssignment> pinned = new ArrayList<>(seats.subList(0, 3));
        pinned.forEach(s -> s.setAgent(agent));
        AgentDayConfig dayConfig = dayConfig(agent.getId(), DAY);

        Schedule schedule = schedule(List.of(agent), seats, List.of(dayConfig));

        List<? extends Move<Schedule>> moves = new AtomicShiftMoveFactory().createMoveList(schedule);
        assertThat(moves).as("pinned agent-day should generate at least one rewrite move").isNotEmpty();

        // Recompute the same candidate pool/window the factory would have
        // used and assert buildSeatMoves' unassign-then-assign shape
        // directly.
        List<AgentAssignment> candidateSeats = seats.stream()
                .filter(s -> s.getAgent() == null || agent.equals(s.getAgent()))
                .toList();
        List<ShiftWindowFinder.ShiftWindow> windows = ShiftWindowFinder.findWindows(candidateSeats, dayConfig);
        assertThat(windows).isNotEmpty();
        ShiftWindowFinder.ShiftWindow window = windows.get(0);

        List<AssignSeatMove> seatMoves = AtomicShiftMoveFactory.buildSeatMoves(agent, pinned, window);

        List<AgentAssignment> unassignTargets = seatMoves.stream()
                .filter(m -> targetOf(m) == null)
                .map(AtomicShiftMoveFactoryTest::seatOf)
                .toList();
        List<AgentAssignment> assignTargets = seatMoves.stream()
                .filter(m -> targetOf(m) != null)
                .map(AtomicShiftMoveFactoryTest::seatOf)
                .toList();

        List<AgentAssignment> expectedUnassign = pinned.stream()
                .filter(s -> !window.workSeats().contains(s))
                .toList();
        List<AgentAssignment> expectedAssign = window.workSeats().stream()
                .filter(s -> !pinned.contains(s))
                .toList();

        assertThat(unassignTargets).containsExactlyInAnyOrderElementsOf(expectedUnassign);
        assertThat(assignTargets).containsExactlyInAnyOrderElementsOf(expectedAssign);
        assertThat(seatMoves).hasSize(expectedUnassign.size() + expectedAssign.size());
    }

    @Test
    void foreignSeats_areNeverTargetedByGeneratedMoves() {
        Specialization spec = spec("IT Support");
        Agent agent = agent(spec);
        Agent otherAgent = agent(spec);
        // 20 hourly seats (02:00-21:00) so excluding one mid-run seat still
        // leaves an 11-slot run (02:00-12:00) long enough for a window,
        // rather than starving the whole fixture of candidates.
        List<AgentAssignment> seats = hourlySeats(spec, DAY, LocalTime.of(2, 0), 20);
        AgentAssignment foreignSeat = seats.get(11); // 13:00, held by a different agent
        foreignSeat.setAgent(otherAgent);

        Schedule schedule = schedule(List.of(agent, otherAgent), seats, List.of(dayConfig(agent.getId(), DAY)));

        List<? extends Move<Schedule>> moves = new AtomicShiftMoveFactory().createMoveList(schedule);
        assertThat(moves).isNotEmpty();

        for (Move<Schedule> move : moves) {
            for (AssignSeatMove seatMove : flatten(move)) {
                assertThat(seatOf(seatMove)).isNotSameAs(foreignSeat);
            }
        }
    }

    @Test
    void specializationMismatch_seatNeverAppearsInGeneratedMoves() {
        Specialization matching = spec("IT Support");
        Specialization mismatched = spec("Billing");
        Agent agent = agent(matching);
        // 20 hourly seats (02:00-21:00) — see foreignSeats_... above for why.
        List<AgentAssignment> seats = hourlySeats(matching, DAY, LocalTime.of(2, 0), 20);
        AgentAssignment mismatchedSeat = seats.get(11); // 13:00 — agent holds neither primary nor secondary
        mismatchedSeat.setRequiredSpecialization(mismatched);

        Schedule schedule = schedule(List.of(agent), seats, List.of(dayConfig(agent.getId(), DAY)));

        List<? extends Move<Schedule>> moves = new AtomicShiftMoveFactory().createMoveList(schedule);
        assertThat(moves).isNotEmpty();

        for (Move<Schedule> move : moves) {
            for (AssignSeatMove seatMove : flatten(move)) {
                assertThat(seatOf(seatMove)).isNotSameAs(mismatchedSeat);
            }
        }
    }

    @Test
    void dateIsolation_noGeneratedMoveSpansTwoDates() {
        Specialization spec = spec("IT Support");
        Agent agent = agent(spec);
        List<AgentAssignment> day1Seats = hourlySeats(spec, DAY, START, 12);
        List<AgentAssignment> day2Seats = hourlySeats(spec, DAY_2, START, 12);
        List<AgentAssignment> allSeats = new ArrayList<>(day1Seats);
        allSeats.addAll(day2Seats);

        Schedule schedule = schedule(List.of(agent), allSeats,
                List.of(dayConfig(agent.getId(), DAY), dayConfig(agent.getId(), DAY_2)));

        List<? extends Move<Schedule>> moves = new AtomicShiftMoveFactory().createMoveList(schedule);
        assertThat(moves).isNotEmpty();

        for (Move<Schedule> move : moves) {
            Set<LocalDate> datesTouched = flatten(move).stream()
                    .map(AtomicShiftMoveFactoryTest::seatOf)
                    .map(a -> a.getTimeslot().getDate())
                    .collect(Collectors.toSet());
            assertThat(datesTouched).as("a single composite move must not span two dates").hasSizeLessThanOrEqualTo(1);
        }
    }

    @Test
    void poolBound_capsGeneratedMovesAndSpreadsAcrossSpanStarts() {
        Specialization spec = spec("IT Support");
        Agent agent = agent(spec);
        List<AgentAssignment> seats = hourlySeats(spec, DAY, START, 12); // 28-window reference fixture
        AgentDayConfig dayConfig = dayConfig(agent.getId(), DAY);

        List<ShiftWindowFinder.ShiftWindow> allWindows = ShiftWindowFinder.findWindows(seats, dayConfig);
        assertThat(allWindows).as("reference fixture should yield 28 candidate windows").hasSize(28);

        Schedule schedule = schedule(List.of(agent), seats, List.of(dayConfig));

        List<? extends Move<Schedule>> moves = new AtomicShiftMoveFactory().createMoveList(schedule);

        assertThat(moves.size()).isLessThanOrEqualTo(AtomicShiftMoveFactory.MAX_WINDOWS_PER_AGENT_DAY);

        List<LocalTime> spanStarts = moves.stream()
                .map(m -> flatten(m).stream()
                        .map(AtomicShiftMoveFactoryTest::seatOf)
                        .map(a -> a.getTimeslot().getStartTime())
                        .min(LocalTime::compareTo)
                        .orElseThrow())
                .toList();
        assertThat(spanStarts.stream().distinct().count())
                .as("retained windows should not all cluster at the earliest span start")
                .isGreaterThan(1);
    }

    @Test
    void readOnlyGeneration_repeatedCallsLeaveAssignmentsUnchanged() {
        Specialization spec = spec("IT Support");
        Agent agent = agent(spec);
        List<AgentAssignment> seats = hourlySeats(spec, DAY, START, 12);
        List<AgentAssignment> pinned = new ArrayList<>(seats.subList(0, 3));
        pinned.forEach(s -> s.setAgent(agent));

        Schedule schedule = schedule(List.of(agent), seats, List.of(dayConfig(agent.getId(), DAY)));

        List<Agent> before = new ArrayList<>();
        for (AgentAssignment seat : seats) {
            before.add(seat.getAgent());
        }

        AtomicShiftMoveFactory factory = new AtomicShiftMoveFactory();
        List<? extends Move<Schedule>> firstCall = factory.createMoveList(schedule);
        List<? extends Move<Schedule>> secondCall = factory.createMoveList(schedule);

        assertThat(firstCall).hasSameSizeAs(secondCall);
        for (int i = 0; i < seats.size(); i++) {
            assertThat(seats.get(i).getAgent())
                    .as("createMoveList must never mutate the working solution")
                    .isEqualTo(before.get(i));
        }
    }

    // ------------------------------------------------------------------
    //  Move introspection helpers
    // ------------------------------------------------------------------

    /** Flattens a top-level {@code Move<Schedule>} into its individual {@link AssignSeatMove}s. */
    private static List<AssignSeatMove> flatten(Move<Schedule> move) {
        if (move instanceof CompositeMove<Schedule> composite) {
            List<AssignSeatMove> result = new ArrayList<>();
            for (Move<Schedule> sub : composite.getMoves()) {
                result.addAll(flatten(sub));
            }
            return result;
        }
        if (move instanceof AssignSeatMove seatMove) {
            return List.of(seatMove);
        }
        return List.of();
    }

    private static AgentAssignment seatOf(AssignSeatMove move) {
        return (AgentAssignment) move.getPlanningEntities().iterator().next();
    }

    private static Agent targetOf(AssignSeatMove move) {
        return (Agent) move.getPlanningValues().iterator().next();
    }

    // ------------------------------------------------------------------
    //  Fixture helpers
    // ------------------------------------------------------------------

    private Specialization spec(String name) {
        Specialization s = new Specialization();
        s.setId(UUID.randomUUID());
        s.setTenantId(TENANT);
        s.setDeskId(DESK_ID);
        s.setName(name);
        return s;
    }

    private Agent agent(Specialization primary) {
        Agent a = new Agent();
        a.setId(UUID.randomUUID());
        a.setTenantId(TENANT);
        a.setBamboohrId("A-" + UUID.randomUUID());
        a.setName("Agent");
        a.setActive(true);
        a.setDeskId(DESK_ID);
        a.setPrimarySpecialization(primary);
        a.setSecondarySpecializations(new ArrayList<>());
        a.setContractedHoursPerDay(CONTRACTED_HOURS);
        return a;
    }

    private List<AgentAssignment> hourlySeats(Specialization spec, LocalDate date, LocalTime start, int count) {
        List<AgentAssignment> seats = new ArrayList<>();
        LocalTime t = start;
        for (int i = 0; i < count; i++) {
            Timeslot ts = new Timeslot();
            ts.setId(UUID.randomUUID());
            ts.setTenantId(TENANT);
            ts.setDeskId(DESK_ID);
            ts.setScheduleId(SCHEDULE_ID);
            ts.setDate(date);
            ts.setStartTime(t);
            ts.setEndTime(t.plusMinutes(INCREMENT));

            AgentAssignment aa = new AgentAssignment();
            aa.setId(UUID.randomUUID());
            aa.setTenantId(TENANT);
            aa.setDeskId(DESK_ID);
            aa.setScheduleId(SCHEDULE_ID);
            aa.setTimeslot(ts);
            aa.setRequiredSpecialization(spec);
            seats.add(aa);

            t = t.plusMinutes(INCREMENT);
        }
        return seats;
    }

    private AgentDayConfig dayConfig(UUID agentId, LocalDate date) {
        return new AgentDayConfig(
                agentId, date, CONTRACTED_HOURS, INCREMENT, BREAK_DURATION,
                BREAK_MIN_SHIFT, BREAK_BLOCKED, BREAK_ALIGNMENT, 130, 70);
    }

    private Schedule schedule(List<Agent> agents, List<AgentAssignment> assignments, List<AgentDayConfig> dayConfigs) {
        Schedule schedule = new Schedule();
        schedule.setId(SCHEDULE_ID);
        schedule.setTenantId(TENANT);
        schedule.setDeskId(DESK_ID);
        schedule.setAgents(agents);
        schedule.setAssignments(assignments);
        schedule.setAgentDayConfigs(dayConfigs);
        return schedule;
    }
}
