package com.wfm.service;

import com.wfm.model.AgentAssignment;
import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.model.ShiftTemplateBreakBand;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 15 plan 15-09, Task 1 (gap closure, G-15-10) — proves {@link SolverService
 * #expandMinimumStaffingSeats} is envelope-aware on a SHIFT desk: it suppresses seats on
 * timeslots no live {@link ShiftBandPair} covers (operator ruling OR-1) and guarantees enough
 * seats on covered-but-zero-demand timeslots for every working agent-day, while a SLOT desk's
 * output stays element-for-element identical to today's.
 *
 * <p>The desk shape shared by tests 1-3: operating 06:00-14:00 hourly, one shift template
 * 10:00-14:00 whose sole band sits at 13:00-14:00 (so the covered, non-break hours are
 * 10:00, 11:00 and 12:00), demand present at 10:00, 11:00 and 12:00 only.
 */
class ShiftModeMinimumStaffingSeatSupplyTest {

    private static final long TENANT = 1L;
    private static final UUID DESK = UUID.randomUUID();
    private static final UUID SCHEDULE = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 1, 12);

    private static final LocalTime OPERATING_START = LocalTime.of(6, 0);
    private static final LocalTime OPERATING_END = LocalTime.of(14, 0);
    private static final LocalTime TEMPLATE_START = LocalTime.of(10, 0);
    private static final LocalTime TEMPLATE_END = LocalTime.of(14, 0);

    private static Timeslot timeslot(LocalTime start) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setDate(DAY);
        ts.setStartTime(start);
        ts.setEndTime(start.plusHours(1));
        return ts;
    }

    private static List<Timeslot> operatingWindow() {
        List<Timeslot> slots = new ArrayList<>();
        for (LocalTime t = OPERATING_START; t.isBefore(OPERATING_END); t = t.plusHours(1)) {
            slots.add(timeslot(t));
        }
        return slots;
    }

    private static Specialization spec(String name) {
        Specialization s = new Specialization();
        s.setId(UUID.randomUUID());
        s.setName(name);
        return s;
    }

    private static ShiftTemplate template() {
        ShiftTemplate t = new ShiftTemplate();
        t.setValidWeekdays(java.util.EnumSet.allOf(java.time.DayOfWeek.class));
        t.setId(UUID.randomUUID());
        t.setName("Late");
        t.setStartTime(TEMPLATE_START);
        t.setEndTime(TEMPLATE_END);
        t.setEffectiveFrom(LocalDate.of(2020, 1, 1));
        return t;
    }

    /** Offset 180 (10:00 + 180m = 13:00), duration 60 -> break is exactly the last hour. */
    private static ShiftTemplateBreakBand band(ShiftTemplate t) {
        ShiftTemplateBreakBand b = new ShiftTemplateBreakBand();
        b.setId(UUID.randomUUID());
        b.setShiftTemplate(t);
        b.setOffsetMinutes(180);
        b.setDurationMinutes(60);
        return b;
    }

    private static StaffingRequirement requirement(Timeslot ts, Specialization s, int ftes) {
        StaffingRequirement sr = new StaffingRequirement();
        sr.setId(UUID.randomUUID());
        sr.setTimeslot(ts);
        sr.setSpecialization(s);
        sr.setRequiredFTEs(ftes);
        return sr;
    }

    private static AgentAssignment seat(Timeslot ts, Specialization s) {
        AgentAssignment a = new AgentAssignment();
        a.setId(UUID.randomUUID());
        a.setTimeslot(ts);
        a.setRequiredSpecialization(s);
        return a;
    }

    /** Builds the shared desk shape; demand covers 10:00, 11:00, 12:00 (not the 13:00 break). */
    private record Desk(List<Timeslot> window, ShiftTemplate template, ShiftBandPair pair,
                         Specialization english, List<StaffingRequirement> demand,
                         List<AgentAssignment> demandSeats) {}

    private static Desk desk() {
        Specialization english = spec("English");
        ShiftTemplate t = template();
        ShiftTemplateBreakBand b = band(t);
        ShiftBandPair pair = new ShiftBandPair(t, b);

        List<Timeslot> window = operatingWindow();
        List<StaffingRequirement> demand = new ArrayList<>();
        List<AgentAssignment> demandSeats = new ArrayList<>();
        for (Timeslot ts : window) {
            LocalTime start = ts.getStartTime();
            boolean hasDemand = !start.isBefore(TEMPLATE_START) && start.isBefore(LocalTime.of(13, 0));
            if (hasDemand) {
                demand.add(requirement(ts, english, 10));
                demandSeats.add(seat(ts, english));
            }
        }
        return new Desk(window, t, pair, english, demand, demandSeats);
    }

    // ------------------------------------------------------------------
    //  Test 1 -- SHIFT, uncovered hour: no seat at all
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: a timeslot no live pair covers gets ZERO filler seats (OR-1)")
    void uncoveredHourGetsNoSeats() {
        Desk d = desk();

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, d.window(), new ArrayList<>(d.demandSeats()),
                d.demand(), List.of(d.english()),
                SchedulingMode.SHIFT, List.of(d.pair()), Map.of(DAY, 3));

        List<AgentAssignment> atUncoveredHours = extra.stream()
                .filter(a -> a.getTimeslot().getStartTime().isBefore(TEMPLATE_START))
                .toList();

        assertThat(atUncoveredHours)
                .as("06:00, 07:00, 08:00, 09:00 -- no shift template reaches any of them")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    //  Test 2 -- SHIFT, covered zero-demand hour: one seat per working agent-day
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: a covered hour with no forecast demand gets one seat per working agent-day")
    void coveredZeroDemandHourGetsSeatPerWorkingAgentDay() {
        Specialization english = spec("English");
        ShiftTemplate t = template();
        ShiftTemplateBreakBand b = band(t);
        ShiftBandPair pair = new ShiftBandPair(t, b);

        List<Timeslot> window = operatingWindow();
        // Demand only at 10:00 and 11:00 -- 12:00 is covered but carries no forecast.
        List<StaffingRequirement> demand = new ArrayList<>();
        List<AgentAssignment> demandSeats = new ArrayList<>();
        Timeslot noon = null;
        for (Timeslot ts : window) {
            LocalTime start = ts.getStartTime();
            if (start.equals(LocalTime.of(12, 0))) {
                noon = ts;
                continue;
            }
            boolean hasDemand = !start.isBefore(TEMPLATE_START) && start.isBefore(LocalTime.of(13, 0));
            if (hasDemand) {
                demand.add(requirement(ts, english, 10));
                demandSeats.add(seat(ts, english));
            }
        }
        assertThat(noon).as("sanity: 12:00 must exist in the operating window").isNotNull();

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, window, new ArrayList<>(demandSeats),
                demand, List.of(english),
                SchedulingMode.SHIFT, List.of(pair), Map.of(DAY, 3));

        Timeslot finalNoon = noon;
        List<AgentAssignment> atNoon = extra.stream()
                .filter(a -> a.getTimeslot().equals(finalNoon))
                .toList();

        assertThat(atNoon)
                .as("3 working agent-days on this date -- 12:00 is covered but has zero forecast demand")
                .hasSize(3);
        assertThat(atNoon).allSatisfy(a -> assertThat(a.getAgent()).isNull());
    }

    // ------------------------------------------------------------------
    //  Test 3 -- SHIFT, covered hour that already has demand seats: unchanged
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: a covered hour that already has a demand seat gets no additional seats")
    void coveredHourWithDemandSeatIsUntouched() {
        Desk d = desk();

        List<AgentAssignment> extra = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, d.window(), new ArrayList<>(d.demandSeats()),
                d.demand(), List.of(d.english()),
                SchedulingMode.SHIFT, List.of(d.pair()), Map.of(DAY, 3));

        Timeslot ten = d.window().stream()
                .filter(ts -> ts.getStartTime().equals(LocalTime.of(10, 0)))
                .findFirst().orElseThrow();

        List<AgentAssignment> atTen = extra.stream()
                .filter(a -> a.getTimeslot().equals(ten))
                .toList();

        assertThat(atTen)
                .as("10:00 already carries a demand-derived seat -- the top-up must not leak past it")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    //  Test 4 -- specialization spread on a covered zero-demand timeslot
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SHIFT: filler seats at a covered zero-demand hour cycle specializations deterministically")
    void fillerSeatsCycleSpecializationsDeterministically() {
        Specialization chat = spec("Chat");
        Specialization voice = spec("Voice");
        List<Specialization> orderedById = new ArrayList<>(List.of(chat, voice));
        orderedById.sort((a, b) -> a.getId().toString().compareTo(b.getId().toString()));

        ShiftTemplate t = template();
        ShiftTemplateBreakBand b = band(t);
        ShiftBandPair pair = new ShiftBandPair(t, b);

        List<Timeslot> window = operatingWindow();
        Timeslot bare = window.stream()
                .filter(ts -> ts.getStartTime().equals(LocalTime.of(12, 0)))
                .findFirst().orElseThrow();

        // Demand exists only for "chat" at 10:00 and 11:00, so chat is predominant -- seat index 0
        // must match it (byte-identical to today's single filler seat).
        Timeslot ten = window.stream().filter(ts -> ts.getStartTime().equals(LocalTime.of(10, 0))).findFirst().orElseThrow();
        Timeslot eleven = window.stream().filter(ts -> ts.getStartTime().equals(LocalTime.of(11, 0))).findFirst().orElseThrow();
        List<StaffingRequirement> demand = List.of(
                requirement(ten, chat, 10), requirement(eleven, chat, 10));
        List<AgentAssignment> demandSeats = new ArrayList<>(List.of(seat(ten, chat), seat(eleven, chat)));

        List<AgentAssignment> extra1 = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, window, new ArrayList<>(demandSeats),
                demand, List.of(chat, voice),
                SchedulingMode.SHIFT, List.of(pair), Map.of(DAY, 3));

        List<AgentAssignment> atBare1 = extra1.stream()
                .filter(a -> a.getTimeslot().equals(bare))
                .sorted((a, b1) -> 0) // insertion order preserved by the implementation
                .toList();

        assertThat(atBare1).hasSize(3);
        assertThat(atBare1.get(0).getRequiredSpecialization().getId())
                .as("seat index 0 must match today's single predominant-specialization filler seat")
                .isEqualTo(chat.getId());

        List<UUID> sequence1 = atBare1.stream().map(a -> a.getRequiredSpecialization().getId()).toList();

        List<AgentAssignment> extra2 = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, window, new ArrayList<>(demandSeats),
                demand, List.of(chat, voice),
                SchedulingMode.SHIFT, List.of(pair), Map.of(DAY, 3));
        List<AgentAssignment> atBare2 = extra2.stream().filter(a -> a.getTimeslot().equals(bare)).toList();
        List<UUID> sequence2 = atBare2.stream().map(a -> a.getRequiredSpecialization().getId()).toList();

        assertThat(sequence2)
                .as("two calls with identical inputs must produce identical specialization sequences")
                .isEqualTo(sequence1);
    }

    // ------------------------------------------------------------------
    //  Test 5 -- SLOT invariance
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SLOT: output is element-for-element identical whether or not shift context is supplied")
    void slotModeOutputIsInvariantToShiftContext() {
        Desk d = desk();

        List<AgentAssignment> withoutShiftContext = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, d.window(), new ArrayList<>(d.demandSeats()),
                d.demand(), List.of(d.english()),
                SchedulingMode.SLOT, List.of(), Map.of());

        List<AgentAssignment> withShiftContext = SolverService.expandMinimumStaffingSeats(
                TENANT, DESK, SCHEDULE, d.window(), new ArrayList<>(d.demandSeats()),
                d.demand(), List.of(d.english()),
                SchedulingMode.SLOT, List.of(d.pair()), Map.of(DAY, 99));

        assertThat(withShiftContext).hasSameSizeAs(withoutShiftContext);
        for (int i = 0; i < withoutShiftContext.size(); i++) {
            AgentAssignment a = withoutShiftContext.get(i);
            AgentAssignment b = withShiftContext.get(i);
            assertThat(b.getTimeslot().getId()).isEqualTo(a.getTimeslot().getId());
            assertThat(b.getRequiredSpecialization().getId()).isEqualTo(a.getRequiredSpecialization().getId());
            assertThat(b.getAgent()).isNull();
            assertThat(a.getAgent()).isNull();
        }
    }

    // ------------------------------------------------------------------
    //  Test 6 -- structural: the method can now see the shift model
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the method now carries a SchedulingMode parameter and a List<ShiftBandPair> parameter")
    void methodNowCarriesShiftModeParameters() throws Exception {
        Method method = SolverService.class.getDeclaredMethod("expandMinimumStaffingSeats",
                long.class, UUID.class, UUID.class, List.class, List.class, List.class, List.class,
                SchedulingMode.class, List.class, Map.class);

        assertThat(method.getParameterTypes())
                .as("SchedulingMode must now be reachable from inside")
                .contains(SchedulingMode.class);

        boolean carriesShiftBandPairList = false;
        for (Type generic : method.getGenericParameterTypes()) {
            if (generic instanceof ParameterizedType pt
                    && pt.getRawType().equals(List.class)
                    && pt.getActualTypeArguments().length == 1
                    && pt.getActualTypeArguments()[0].equals(ShiftBandPair.class)) {
                carriesShiftBandPairList = true;
            }
        }
        assertThat(carriesShiftBandPairList)
                .as("a List<ShiftBandPair> parameter must be reachable from inside")
                .isTrue();
    }
}
