package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.ErlangCRequest;
import com.wfm.dto.ErlangXRequest;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingSource;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.repository.SpecializationRepository;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The wiring between a timeslot's length and the Erlang X calculation, which is where the interval
 * fix actually reaches an operator. {@link ErlangXServiceTest} proves the maths responds to the
 * interval; this proves the persisting endpoint hands it the RIGHT interval — the one belonging to
 * the row the number was typed against.
 *
 * <p>Mocks the repositories and uses the REAL {@link ErlangXService}, so the numbers asserted here
 * are the ones that would be written to {@code staffing_requirement} and read by the solver.
 *
 * <p>Note what this test does not defend: it captures what is saved, not that the whole endpoint
 * behaves. {@code calculateErlangX} still deletes every live requirement across its date range
 * before inserting, including rows for timeslots the request never mentions.
 */
class StaffingRequirementErlangXIntervalTest {

    private static final long TENANT = 1L;
    private static final UUID DESK = UUID.randomUUID();

    private final StaffingRequirementRepository staffingRequirementRepository =
            mock(StaffingRequirementRepository.class);
    private final TimeslotRepository timeslotRepository = mock(TimeslotRepository.class);
    private final SpecializationRepository specializationRepository =
            mock(SpecializationRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private final StaffingRequirementService service = new StaffingRequirementService(
            staffingRequirementRepository, timeslotRepository, specializationRepository,
            new ErlangXService(), entityManager);

    @BeforeEach
    void setTenant() {
        TenantContext.setTenantId(TENANT);
        when(staffingRequirementRepository.save(any(StaffingRequirement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private UUID lastTimeslotId;
    private UUID lastSpecId;

    /** One timeslot of the given length, plus the specialization the request points at. */
    private ErlangXRequest requestFor(LocalTime start, LocalTime end) {
        UUID timeslotId = UUID.randomUUID();
        UUID specId = UUID.randomUUID();
        lastTimeslotId = timeslotId;
        lastSpecId = specId;

        Timeslot ts = new Timeslot();
        ts.setId(timeslotId);
        ts.setTenantId(TENANT);
        ts.setDeskId(DESK);
        ts.setScheduleId(null);
        ts.setDate(LocalDate.of(2026, 9, 21));
        ts.setStartTime(start);
        ts.setEndTime(end);

        Specialization spec = new Specialization();
        spec.setId(specId);
        spec.setTenantId(TENANT);
        spec.setDeskId(DESK);
        spec.setName("Security and Item Quality");

        when(timeslotRepository.findById(timeslotId)).thenReturn(Optional.of(ts));
        when(specializationRepository.findByIdAndTenantIdAndDeskId(specId, TENANT, DESK))
                .thenReturn(Optional.of(spec));

        // 100 contacts, 180 s AHT, 90 s patience, 25% retry, 80% within 20 s.
        return new ErlangXRequest(
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21),
                List.of(new ErlangXRequest.Item(timeslotId, specId, 100, 180, 90, 25, 80, 20)));
    }

    private int savedFtes() {
        ArgumentCaptor<StaffingRequirement> captor = ArgumentCaptor.forClass(StaffingRequirement.class);
        verify(staffingRequirementRepository).save(captor.capture());
        return captor.getValue().getRequiredFTEs();
    }

    @Test
    @DisplayName("an hourly timeslot stores the same number it always did")
    void hourlyTimeslotUnchanged() {
        service.calculateErlangX(DESK, requestFor(LocalTime.of(8, 0), LocalTime.of(9, 0)));

        // Every live desk is hourly, so this is the row that must not move.
        assertThat(savedFtes()).isEqualTo(8);
    }

    @Test
    @DisplayName("a quarter-hour timeslot stores three times the agents, not the hourly answer")
    void quarterHourTimeslotUsesItsOwnLength() {
        service.calculateErlangX(DESK, requestFor(LocalTime.of(8, 0), LocalTime.of(8, 15)));

        // 100 contacts in 15 minutes is four times the load of 100 in an hour. Before the fix this
        // row also stored 8 -- a schedule that looked fine and missed service level all morning.
        assertThat(savedFtes()).isEqualTo(24);
    }

    @Test
    @DisplayName("a half-hour timeslot lands between the two")
    void halfHourTimeslot() {
        service.calculateErlangX(DESK, requestFor(LocalTime.of(8, 0), LocalTime.of(8, 30)));

        assertThat(savedFtes()).isEqualTo(13);
    }

    @Test
    @DisplayName("the last timeslot of a desk running to midnight is an hour, not a negative")
    void midnightEndingTimeslot() {
        // 23:00-00:00 is the case LocalTime cannot express: 00:00 is the SMALLEST value in the
        // type, so a raw Duration.between gives -1380 minutes. Routed through DayWindow it is 60,
        // and a negative would otherwise have reached the interval divisor.
        service.calculateErlangX(DESK, requestFor(LocalTime.of(23, 0), LocalTime.MIDNIGHT));

        assertThat(savedFtes()).isEqualTo(8);
    }

    @Test
    @DisplayName("a timeslot that crosses midnight fails loudly rather than being guessed at")
    void midnightCrossingTimeslotIsRejected() {
        // Not a supported shape anywhere in this codebase; DayWindow throws rather than wrapping to
        // a plausible positive number, and the global handler turns that into a 400.
        assertThatThrownBy(() -> service.calculateErlangX(
                DESK, requestFor(LocalTime.of(23, 0), LocalTime.of(1, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single day");
    }

    @Test
    @DisplayName("an empty parameter list writes nothing at all")
    void emptyRequestIsANoOp() {
        var response = service.calculateErlangX(DESK,
                new ErlangXRequest(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21), List.of()));

        assertThat(response.requirements()).isEmpty();
        // Crucially it must also not reach the delete: an empty request is not an instruction to
        // clear the desk's whole date range, and that delete is the destructive half of this
        // endpoint.
        verify(staffingRequirementRepository, never())
                .deleteLiveByDeskAndDateRange(anyLong(), any(), any(), any());
        verify(staffingRequirementRepository, never()).save(any(StaffingRequirement.class));
    }

    /** The same grid, asked of Erlang C: no patience, no retrials, 80% within 20 s. */
    private ErlangCRequest erlangCRequestFor(LocalTime start, LocalTime end) {
        requestFor(start, end);   // reuses the timeslot and specialization stubbing
        return new ErlangCRequest(
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21),
                List.of(new ErlangCRequest.Item(lastTimeslotId, lastSpecId, 100, 180, 80, 20)));
    }

    @Test
    @DisplayName("Erlang C converts on the timeslot's own length too")
    void erlangCUsesTheTimeslotInterval() {
        // 100 contacts in an hour at 180 s AHT is 5 Erlangs; 80% within 20 s needs 8 agents.
        service.calculateErlangC(DESK, erlangCRequestFor(LocalTime.of(8, 0), LocalTime.of(9, 0)));
        assertThat(savedFtes()).isEqualTo(8);
    }

    @Test
    @DisplayName("Erlang C asks for more agents than Erlang X on the same numbers")
    void erlangCIsTheConservativeBaseline() {
        // Same 100 contacts in a half hour at 180 s AHT and an 80/20 target. Erlang C assumes every
        // caller waits forever; Erlang X lets the impatient ones leave, which is work that never
        // arrives. A C answer BELOW X would mean one of the two models is wired up wrong.
        service.calculateErlangC(DESK, erlangCRequestFor(LocalTime.of(8, 0), LocalTime.of(8, 30)));
        int erlangC = savedFtes();

        org.mockito.Mockito.reset(staffingRequirementRepository);
        when(staffingRequirementRepository.save(any(StaffingRequirement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UUID tsId = UUID.randomUUID();
        UUID specId = UUID.randomUUID();
        Timeslot ts = new Timeslot();
        ts.setId(tsId);
        ts.setTenantId(TENANT);
        ts.setDeskId(DESK);
        ts.setDate(LocalDate.of(2026, 9, 21));
        ts.setStartTime(LocalTime.of(8, 0));
        ts.setEndTime(LocalTime.of(8, 30));
        Specialization spec = new Specialization();
        spec.setId(specId);
        spec.setTenantId(TENANT);
        spec.setDeskId(DESK);
        spec.setName("Security and Item Quality");
        when(timeslotRepository.findById(tsId)).thenReturn(Optional.of(ts));
        when(specializationRepository.findByIdAndTenantIdAndDeskId(specId, TENANT, DESK))
                .thenReturn(Optional.of(spec));

        service.calculateErlangX(DESK, new ErlangXRequest(
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21),
                List.of(new ErlangXRequest.Item(tsId, specId, 100, 180, 90, 25, 80, 20))));
        int erlangX = savedFtes();

        assertThat(erlangC).isGreaterThan(erlangX);
    }

    @Test
    @DisplayName("an Erlang C row is stamped ERLANG_C, not ERLANG_X")
    void erlangCRowsRecordTheirSource() {
        service.calculateErlangC(DESK, erlangCRequestFor(LocalTime.of(8, 0), LocalTime.of(9, 0)));

        ArgumentCaptor<StaffingRequirement> captor = ArgumentCaptor.forClass(StaffingRequirement.class);
        verify(staffingRequirementRepository).save(captor.capture());
        // The source column is how an operator tells later which model produced a number.
        assertThat(captor.getValue().getSource()).isEqualTo(StaffingSource.ERLANG_C);
    }

    @Test
    @DisplayName("the service level target is a percentage on the way in, a fraction on the way down")
    void erlangCConvertsTheTargetToAFraction() {
        // ErlangC.Input rejects anything above 1, so passing 80 through unconverted would throw
        // rather than quietly mis-staff -- this asserts the conversion exists at all.
        requestFor(LocalTime.of(8, 0), LocalTime.of(9, 0));
        service.calculateErlangC(DESK, new ErlangCRequest(
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21),
                List.of(new ErlangCRequest.Item(lastTimeslotId, lastSpecId, 100, 180, 99, 20))));

        // A 99% target needs more agents than the 80% one above (8), which is only true if both
        // were read as percentages rather than clamped or rejected.
        assertThat(savedFtes()).isGreaterThan(8);
    }

    @Test
    @DisplayName("a fraction sent where a percentage belongs is refused, not quietly understaffed")
    void fractionTargetIsRejected() {
        // 0.8 reads as a 0.8% target, which the minimum stable headcount already meets: on this
        // fixture it stores 11 instead of 14, and nothing in the response says so. That is how the
        // frontend understaffed every Erlang X calculation until this guard existed.
        requestFor(LocalTime.of(8, 0), LocalTime.of(8, 30));
        assertThatThrownBy(() -> service.calculateErlangC(DESK, new ErlangCRequest(
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21),
                List.of(new ErlangCRequest.Item(lastTimeslotId, lastSpecId, 100, 180, 0.8, 20)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percentage");

        requestFor(LocalTime.of(8, 0), LocalTime.of(8, 30));
        assertThatThrownBy(() -> service.calculateErlangX(DESK, new ErlangXRequest(
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21),
                List.of(new ErlangXRequest.Item(lastTimeslotId, lastSpecId, 100, 180, 90, 25, 0.8, 20)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percentage");

        // Nothing was deleted on the way to the rejection -- the guard runs before the replace.
        verify(staffingRequirementRepository, never())
                .deleteLiveByDeskAndDateRange(anyLong(), any(), any(), any());
    }
}
