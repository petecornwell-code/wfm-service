package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.ErlangCRequest;
import com.wfm.dto.ErlangXRequest;
import com.wfm.dto.StaffingAdjustmentOptionsDto;
import com.wfm.model.Desk;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.StaffingSource;
import com.wfm.model.Timeslot;
import com.wfm.repository.DeskRepository;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the two calculating buttons on Staffing Requirements actually write.
 *
 * <p>Both delegate to {@link ErlangCalculatorService}, the same service behind the read-only
 * calculator page, so this class uses the REAL one rather than a mock: the numbers asserted here
 * are the numbers that reach {@code staffing_requirement} and, through it, the solver — and they
 * are by construction the numbers the calculator page previews.
 *
 * <p>Three things are worth pinning. The interval comes from each row's own timeslot. The
 * adjustments, when given, are applied before the row is written. And the destructive half of these
 * endpoints — a delete across the whole date range — must not run when the request is rejected.
 */
class StaffingRequirementErlangTest {

    private static final long TENANT = 1L;
    private static final UUID DESK = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 9, 21);

    private final StaffingRequirementRepository staffingRequirementRepository =
            mock(StaffingRequirementRepository.class);
    private final TimeslotRepository timeslotRepository = mock(TimeslotRepository.class);
    private final SpecializationRepository specializationRepository =
            mock(SpecializationRepository.class);
    private final DeskRepository deskRepository = mock(DeskRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final com.wfm.repository.ErlangDemandInputRepository erlangDemandInputRepository =
            mock(com.wfm.repository.ErlangDemandInputRepository.class);

    private final StaffingRequirementService service = new StaffingRequirementService(
            staffingRequirementRepository, timeslotRepository, specializationRepository,
            new ErlangCalculatorService(), deskRepository, entityManager, erlangDemandInputRepository);

    private UUID timeslotId;
    private UUID specId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        when(staffingRequirementRepository.save(any(StaffingRequirement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(erlangDemandInputRepository.save(any(com.wfm.model.ErlangDemandInput.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // BDAY-04: every Erlang calculation now binds a DayWindow from the desk -- a desk at its
        // default (MIDNIGHT) anchor, matching every fixture's implicit assumption before this plan.
        Desk desk = new Desk();
        desk.setId(DESK);
        when(deskRepository.findByIdAndTenantId(DESK, TENANT)).thenReturn(Optional.of(desk));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Stubs one timeslot of the given length and the specialization the requests point at. */
    private void givenTimeslot(LocalTime start, LocalTime end) {
        timeslotId = UUID.randomUUID();
        specId = UUID.randomUUID();

        Timeslot ts = new Timeslot();
        ts.setId(timeslotId);
        ts.setTenantId(TENANT);
        ts.setDeskId(DESK);
        ts.setScheduleId(null);
        ts.setDate(DATE);
        ts.setBusinessDate(DATE);
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
    }

    /** 100 contacts, 180 s AHT, 80% within 20 s. */
    private ErlangCRequest erlangC(StaffingAdjustmentOptionsDto adjustments) {
        return new ErlangCRequest(DATE,
                List.of(new ErlangCRequest.Item(timeslotId, specId, 100, 180, 80, 20)), adjustments, null);
    }

    /** The same, plus 90 s patience and a 25% retry rate. */
    private ErlangXRequest erlangX(StaffingAdjustmentOptionsDto adjustments) {
        return new ErlangXRequest(DATE,
                List.of(new ErlangXRequest.Item(timeslotId, specId, 100, 180, 90, 25, 80, 20)),
                adjustments, null);
    }

    /** The most recent row written, so a test may calculate more than once without bookkeeping. */
    private StaffingRequirement lastSaved() {
        ArgumentCaptor<StaffingRequirement> captor = ArgumentCaptor.forClass(StaffingRequirement.class);
        verify(staffingRequirementRepository, atLeastOnce()).save(captor.capture());
        List<StaffingRequirement> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }

    private int savedFtes() {
        return lastSaved().getRequiredFTEs();
    }

    private StaffingSource savedSource() {
        return lastSaved().getSource();
    }

    @Test
    @DisplayName("the volume is converted on the timeslot's own length, not on an hour")
    void intervalComesFromTheTimeslot() {
        givenTimeslot(LocalTime.of(8, 0), LocalTime.of(9, 0));
        service.calculateErlangC(DESK, erlangC(null));
        assertThat(savedFtes()).isEqualTo(8);          // 5 Erlangs

        givenTimeslot(LocalTime.of(8, 0), LocalTime.of(8, 30));
        service.calculateErlangC(DESK, erlangC(null));
        // 100 contacts in half the time is twice the load, so more agents -- the fault that made
        // this test class exist was storing 8 here too.
        assertThat(savedFtes()).isEqualTo(14);         // 10 Erlangs
    }

    @Test
    @DisplayName("a quarter-hour timeslot needs three times the hourly answer")
    void quarterHourTimeslot() {
        givenTimeslot(LocalTime.of(8, 0), LocalTime.of(8, 15));
        service.calculateErlangC(DESK, erlangC(null));
        assertThat(savedFtes()).isEqualTo(24);         // 20 Erlangs
    }

    @Test
    @DisplayName("Erlang C never asks for fewer agents than Erlang X")
    void erlangCIsTheConservativeBaseline() {
        givenTimeslot(LocalTime.of(8, 0), LocalTime.of(8, 30));
        service.calculateErlangC(DESK, erlangC(null));
        int c = savedFtes();

        givenTimeslot(LocalTime.of(8, 0), LocalTime.of(8, 30));
        service.calculateErlangX(DESK, erlangX(null));
        int x = savedFtes();

        // Impatient callers are work that never arrives. A C answer BELOW X would mean one of the
        // two is wired to the wrong model.
        assertThat(c).isEqualTo(14);
        assertThat(x).isEqualTo(13);
        assertThat(c).isGreaterThanOrEqualTo(x);
    }

    @Test
    @DisplayName("with no adjustments the stored number is the raw queueing answer")
    void noAdjustmentsMeansNoInflation() {
        givenTimeslot(LocalTime.of(8, 0), LocalTime.of(9, 0));
        service.calculateErlangC(DESK, erlangC(null));
        assertThat(savedFtes()).isEqualTo(8);

        givenTimeslot(LocalTime.of(8, 0), LocalTime.of(9, 0));
        service.calculateErlangC(DESK, erlangC(StaffingAdjustmentOptionsDto.NONE));
        assertThat(savedFtes()).isEqualTo(8);
    }

    @Test
    @DisplayName("shrinkage is stored as the number to roster, not the number handling contacts")
    void shrinkageReachesTheStoredRow() {
        givenTimeslot(LocalTime.of(8, 0), LocalTime.of(9, 0));
        service.calculateErlangC(DESK,
                erlangC(new StaffingAdjustmentOptionsDto(0.30, null, null)));

        // 8 handling / 0.70 = 12 rostered. Storing 8 would roster exactly the people who must be
        // answering and leave nobody for absence, training or coaching.
        assertThat(savedFtes()).isEqualTo(12);
    }

    @Test
    @DisplayName("each mode stamps its own source")
    void sourceRecordsTheModel() {
        givenTimeslot(LocalTime.of(8, 0), LocalTime.of(9, 0));
        service.calculateErlangC(DESK, erlangC(null));
        assertThat(savedSource()).isEqualTo(StaffingSource.ERLANG_C);

        givenTimeslot(LocalTime.of(8, 0), LocalTime.of(9, 0));
        service.calculateErlangX(DESK, erlangX(null));
        assertThat(savedSource()).isEqualTo(StaffingSource.ERLANG_X);
    }

    @Test
    @DisplayName("the last timeslot of a desk running to midnight is an hour, not a negative")
    void midnightEndingTimeslot() {
        // 00:00 is the SMALLEST value LocalTime has, so a raw Duration.between gives -1380 minutes.
        givenTimeslot(LocalTime.of(23, 0), LocalTime.MIDNIGHT);
        service.calculateErlangC(DESK, erlangC(null));
        assertThat(savedFtes()).isEqualTo(8);
    }

    @Test
    @DisplayName("a timeslot crossing midnight fails loudly rather than being guessed at")
    void midnightCrossingTimeslotIsRejected() {
        givenTimeslot(LocalTime.of(23, 0), LocalTime.of(1, 0));
        assertThatThrownBy(() -> service.calculateErlangC(DESK, erlangC(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single day");
    }

    @Test
    @DisplayName("a fraction sent where a percentage belongs is refused before anything is deleted")
    void fractionTargetIsRejectedBeforeTheDelete() {
        givenTimeslot(LocalTime.of(8, 0), LocalTime.of(8, 30));
        assertThatThrownBy(() -> service.calculateErlangC(DESK, new ErlangCRequest(DATE,
                List.of(new ErlangCRequest.Item(timeslotId, specId, 100, 180, 0.8, 20)), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percentage");

        givenTimeslot(LocalTime.of(8, 0), LocalTime.of(8, 30));
        assertThatThrownBy(() -> service.calculateErlangX(DESK, new ErlangXRequest(DATE,
                List.of(new ErlangXRequest.Item(timeslotId, specId, 100, 180, 90, 25, 0.8, 20)), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percentage");

        // The guard runs before the replace, so a rejected request leaves the desk's existing
        // requirements alone. This is the assertion that makes the endpoint safe to call wrongly.
        verify(staffingRequirementRepository, never())
                .deleteLiveByDeskAndBusinessDateRange(anyLong(), any(), any(), any());
        verify(staffingRequirementRepository, never()).save(any(StaffingRequirement.class));
    }

    @Test
    @DisplayName("an empty parameter list writes nothing and deletes nothing")
    void emptyRequestIsANoOp() {
        assertThat(service.calculateErlangC(DESK, new ErlangCRequest(DATE, List.of(), null, null))
                .requirements()).isEmpty();
        assertThat(service.calculateErlangX(DESK, new ErlangXRequest(DATE, List.of(), null, null))
                .requirements()).isEmpty();

        verify(staffingRequirementRepository, never())
                .deleteLiveByDeskAndBusinessDateRange(anyLong(), any(), any(), any());
        verify(staffingRequirementRepository, never()).save(any(StaffingRequirement.class));
    }
}
