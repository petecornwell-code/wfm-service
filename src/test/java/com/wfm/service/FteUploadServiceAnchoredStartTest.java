package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.FteUploadResult;
import com.wfm.model.Desk;
import com.wfm.model.Specialization;
import com.wfm.model.StaffingRequirement;
import com.wfm.model.Timeslot;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OVNT-02/plan 21-12: anchored-verdict and midnight-no-op control pair for {@code
 * FteUploadService.uploadFtes}'s min-start tracking line, converted from a raw {@code
 * slotStart.isBefore(startTime)} comparison to one routed through the bound {@code DayWindow}'s
 * anchored minute.
 *
 * <p>No test exercising {@code uploadFtes} existed before this plan (confirmed by a repo-wide
 * search for the method name under {@code src/test/java}) — this is a NEW test class, not an
 * addition to an existing one, per the plan's own instruction to say so when none exists.
 *
 * <p>The fixture's four header columns ({@code 22:00, 23:00, 06:00, 07:00}) are deliberately
 * chosen so clock order and anchored order DISAGREE at a {@code 21:00} anchor: {@code 06:00} is
 * the clock-earliest of the four, but at a {@code 21:00} anchor it is nine hours INTO the business
 * day while {@code 22:00} is only one hour in, so the anchored-earliest start is {@code 22:00}, not
 * {@code 06:00}. The pre-conversion raw comparison would have picked {@code 06:00} (the
 * clock-earliest), which is exactly the bug this plan's objective names: a desk anchored away from
 * midnight would have its stored operating-window start computed from the clock-earliest slot
 * rather than the business day's first.
 *
 * <p>The {@code FteUploadService} constructor takes only repository/collaborator interfaces and
 * {@link EntityManager} — no Spring context is needed to exercise it directly, matching the plain
 * {@code Mockito.mock(...)}-based construction style {@code DeskAssignmentUploadValidationTest}
 * already uses for a sibling upload service, and the {@code MockMultipartFile}/{@code
 * XSSFWorkbook} workbook-building idiom that file's {@code buildWorkbook} helper establishes.
 */
class FteUploadServiceAnchoredStartTest {

    private static final long TENANT_ID = 1L;
    private static final UUID DESK_ID = UUID.randomUUID();

    private DeskRepository deskRepository;
    private SpecializationRepository specializationRepository;
    private StaffingRequirementRepository staffingRequirementRepository;
    private TimeslotGeneratorService timeslotGeneratorService;
    private TimeslotRepository timeslotRepository;
    private EntityManager entityManager;
    private FteUploadService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);

        deskRepository = mock(DeskRepository.class);
        specializationRepository = mock(SpecializationRepository.class);
        staffingRequirementRepository = mock(StaffingRequirementRepository.class);
        timeslotGeneratorService = mock(TimeslotGeneratorService.class);
        timeslotRepository = mock(TimeslotRepository.class);
        entityManager = mock(EntityManager.class);

        when(specializationRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID)).thenReturn(List.of());
        when(timeslotGeneratorService.generateTimeslots(
                any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        service = new FteUploadService(timeslotRepository, specializationRepository,
                staffingRequirementRepository, timeslotGeneratorService, deskRepository, entityManager);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static Desk desk(LocalTime dayStart) {
        Desk d = new Desk();
        d.setId(DESK_ID);
        d.setTenantId(TENANT_ID);
        d.setName("Anchored FTE upload desk " + dayStart);
        d.setDayStart(dayStart); // bypasses DeskService's gated setter, on purpose (same precedent
                                 // as DeskAnchorReachesConstraintTest -- this proves the constraint/
                                 // service-layer consumption of an anchor, not the save-path gate)
        return d;
    }

    /**
     * One sheet, header-only (no data rows needed -- this test is about the first-pass time-range
     * derivation, not FTE value parsing), four time columns whose clock order and anchored order
     * disagree at a {@code 21:00} anchor: {@code 22:00, 23:00, 06:00, 07:00}.
     */
    private static MockMultipartFile anchorProbeWorkbook() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("2026-01-05");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Specialization");
        header.createCell(1).setCellValue("22:00");
        header.createCell(2).setCellValue("23:00");
        header.createCell(3).setCellValue("06:00");
        header.createCell(4).setCellValue("07:00");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new MockMultipartFile("file", "fte.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(out.toByteArray()));
    }

    @Test
    @DisplayName("anchored verdict: at a 21:00 anchor, the stored window start is the "
            + "anchored-earliest slot (22:00), not the clock-earliest one (06:00)")
    void uploadFtes_21_00AnchoredDesk_minStartTrackedByAnchoredOrder() throws Exception {
        when(deskRepository.findByIdAndTenantId(DESK_ID, TENANT_ID))
                .thenReturn(java.util.Optional.of(desk(LocalTime.of(21, 0))));

        FteUploadResult result = service.uploadFtes(DESK_ID, anchorProbeWorkbook());

        assertThat(result.startTime())
                .as("22:00 is one hour into a 21:00-anchored business day; 06:00 is nine hours in, "
                        + "so the anchored-earliest start must be 22:00, not the clock-earliest 06:00 "
                        + "a raw isBefore comparison would have picked")
                .isEqualTo(LocalTime.of(22, 0));
        assertThat(result.endTime()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    @DisplayName("midnight-anchor no-op control: the same four columns still report the "
            + "clock-earliest slot (06:00) as the window start, unchanged from today")
    void uploadFtes_midnightAnchoredDesk_minStartUnchangedFromClockOrder() throws Exception {
        when(deskRepository.findByIdAndTenantId(DESK_ID, TENANT_ID))
                .thenReturn(java.util.Optional.of(desk(LocalTime.MIDNIGHT)));

        FteUploadResult result = service.uploadFtes(DESK_ID, anchorProbeWorkbook());

        assertThat(result.startTime())
                .as("at a 00:00 anchor the anchored minute equals the clock minute, so this is a "
                        + "byte-identical no-op against the pre-conversion raw isBefore behaviour")
                .isEqualTo(LocalTime.of(6, 0));
        assertThat(result.endTime()).isEqualTo(LocalTime.of(23, 0));
    }

    // --- CR-01: second-pass lookup keying (calendar date vs. business date) -----------------

    /**
     * CR-01: builds a fixed, hand-authored {@link Timeslot} fixture for a {@code 21:00}-anchored
     * desk covering two adjacent business days ({@code 2026-01-04} and {@code 2026-01-05}), exactly
     * as {@code TimeslotGeneratorService} would produce them per its own documented rule
     * ("A 21:00-anchored desk's post-midnight rows carry the FOLLOWING calendar date but the
     * ORIGINAL business date"):
     *
     * <pre>
     * business date 2026-01-04: 22:00/23:00 -> calendar 2026-01-04 ; 06:00/07:00 -> calendar 2026-01-05
     * business date 2026-01-05: 22:00/23:00 -> calendar 2026-01-05 ; 06:00/07:00 -> calendar 2026-01-06
     * </pre>
     *
     * Calendar date {@code 2026-01-05} is therefore a MIXED bucket under the buggy calendar-date
     * keying: it holds business-date-2026-01-04's 06:00/07:00 rows AND business-date-2026-01-05's
     * own 22:00/23:00 rows. A sheet uploaded for business date {@code 2026-01-05} that queries that
     * bucket does not merely miss its 06:00/07:00 columns -- it RESOLVES them to business date
     * 2026-01-04's timeslots, mis-attributing the FTE value into the wrong business day.
     */
    private static List<Timeslot> twoBusinessDayAnchoredFixture() {
        List<Timeslot> timeslots = new ArrayList<>();
        timeslots.add(timeslot(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 4), LocalTime.of(22, 0)));
        timeslots.add(timeslot(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 4), LocalTime.of(23, 0)));
        timeslots.add(timeslot(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 4), LocalTime.of(6, 0)));
        timeslots.add(timeslot(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 4), LocalTime.of(7, 0)));
        timeslots.add(timeslot(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), LocalTime.of(22, 0)));
        timeslots.add(timeslot(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), LocalTime.of(23, 0)));
        timeslots.add(timeslot(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 5), LocalTime.of(6, 0)));
        timeslots.add(timeslot(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 5), LocalTime.of(7, 0)));
        return timeslots;
    }

    private static Timeslot timeslot(LocalDate calendarDate, LocalDate businessDate, LocalTime startTime) {
        Timeslot ts = new Timeslot();
        ts.setId(UUID.randomUUID());
        ts.setTenantId(TENANT_ID);
        ts.setDeskId(DESK_ID);
        ts.setDate(calendarDate);
        ts.setBusinessDate(businessDate);
        ts.setStartTime(startTime);
        ts.setEndTime(startTime.plusHours(1));
        return ts;
    }

    /**
     * One sheet per business day, same four time columns (22:00, 23:00, 06:00, 07:00) but a
     * DISTINCT, non-overlapping range of required-FTE values per sheet, so each saved {@link
     * StaffingRequirement} can be traced back to the exact sheet (and therefore the exact intended
     * business date) it came from, independent of which timeslot it actually got attached to.
     */
    private static MockMultipartFile twoSheetWorkbook(String sheetNameA, int[] valuesA,
            String sheetNameB, int[] valuesB) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        for (var entry : List.of(Map.entry(sheetNameA, valuesA), Map.entry(sheetNameB, valuesB))) {
            Sheet sheet = wb.createSheet(entry.getKey());
            int[] values = entry.getValue();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Specialization");
            header.createCell(1).setCellValue("22:00");
            header.createCell(2).setCellValue("23:00");
            header.createCell(3).setCellValue("06:00");
            header.createCell(4).setCellValue("07:00");

            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("Voice");
            dataRow.createCell(1).setCellValue(values[0]);
            dataRow.createCell(2).setCellValue(values[1]);
            dataRow.createCell(3).setCellValue(values[2]);
            dataRow.createCell(4).setCellValue(values[3]);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new MockMultipartFile("file", "fte.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(out.toByteArray()));
    }

    @Test
    @DisplayName("CR-01: on a 21:00-anchored desk, every FTE value in the sheet for business date "
            + "2026-01-05 lands on a 2026-01-05 timeslot -- none mis-attributed to the neighbouring "
            + "business date 2026-01-04, which the buggy calendar-date keying resolves to instead")
    void uploadFtes_21_00AnchoredDesk_dataRowsResolveToOwnBusinessDate_notNeighbouringDay() throws Exception {
        when(deskRepository.findByIdAndTenantId(DESK_ID, TENANT_ID))
                .thenReturn(Optional.of(desk(LocalTime.of(21, 0))));

        Specialization voice = new Specialization();
        voice.setId(UUID.randomUUID());
        voice.setTenantId(TENANT_ID);
        voice.setDeskId(DESK_ID);
        voice.setName("Voice");
        when(specializationRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID))
                .thenReturn(List.of(voice));

        List<Timeslot> fixture = twoBusinessDayAnchoredFixture();
        when(timeslotGeneratorService.generateTimeslots(
                any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(fixture);

        // entityManager.getReference(Timeslot.class, id) must return an object carrying the real
        // businessDate so the assertion below can read back which business day each saved FTE
        // value actually landed on.
        when(entityManager.getReference(eq(Timeslot.class), any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(1);
            return fixture.stream().filter(t -> t.getId().equals(id)).findFirst().orElseThrow();
        });

        // Sheet 2026-01-04 -> values 11/12/13/14 (22:00/23:00/06:00/07:00).
        // Sheet 2026-01-05 -> values 21/22/23/24 (22:00/23:00/06:00/07:00).
        // The two ranges never overlap, so every saved requirement's requiredFTEs value identifies
        // exactly which sheet -- and therefore which business date -- it was uploaded for.
        service.uploadFtes(DESK_ID, twoSheetWorkbook(
                "2026-01-04", new int[] {11, 12, 13, 14},
                "2026-01-05", new int[] {21, 22, 23, 24}));

        ArgumentCaptor<StaffingRequirement> captor = ArgumentCaptor.forClass(StaffingRequirement.class);
        verify(staffingRequirementRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());

        Map<Integer, LocalDate> resolvedBusinessDateByFte = new HashMap<>();
        for (StaffingRequirement sr : captor.getAllValues()) {
            resolvedBusinessDateByFte.put(sr.getRequiredFTEs(), sr.getTimeslot().getBusinessDate());
        }

        // Sheet 2026-01-04's own values (11-14) are deliberately NOT asserted here: 2026-01-04 is
        // the earliest business date in this fixture, so its own 06:00/07:00 columns have no
        // predecessor business date to collide with under the buggy keying and simply skip as
        // "no timeslot" (pure absence, not mis-attribution) -- a secondary, uninteresting artifact
        // of this fixture's boundary, not the defect this test targets. The sheet exists solely to
        // populate calendar date 2026-01-05 as a MIXED bucket for the assertions below.

        // Sheet 2026-01-05's 22:00/23:00 values are unaffected by the bug (calendar date ==
        // business date for those two hours even at a 21:00 anchor) and must resolve to 2026-01-05.
        assertThat(resolvedBusinessDateByFte.get(21))
                .as("22:00 column of the 2026-01-05 sheet must resolve to business date 2026-01-05")
                .isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(resolvedBusinessDateByFte.get(22))
                .as("23:00 column of the 2026-01-05 sheet must resolve to business date 2026-01-05")
                .isEqualTo(LocalDate.of(2026, 1, 5));

        // THE CR-01 ASSERTION: the 2026-01-05 sheet's post-midnight columns (06:00, 07:00) must
        // resolve to business date 2026-01-05 -- NOT mis-attributed to the neighbouring business
        // date 2026-01-04, which is what the buggy calendar-date-keyed lookup actually resolves
        // them to (calendar date 2026-01-05 is a mixed bucket holding both business date
        // 2026-01-04's 06:00/07:00 rows and business date 2026-01-05's own 22:00/23:00 rows).
        assertThat(resolvedBusinessDateByFte.get(23))
                .as("06:00 column of the 2026-01-05 sheet must resolve to business date 2026-01-05, "
                        + "not mis-attribute into the neighbouring business date 2026-01-04")
                .isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(resolvedBusinessDateByFte.get(24))
                .as("07:00 column of the 2026-01-05 sheet must resolve to business date 2026-01-05, "
                        + "not mis-attribute into the neighbouring business date 2026-01-04")
                .isEqualTo(LocalDate.of(2026, 1, 5));
    }

    @Test
    @DisplayName("CR-01 no-op control: at a 00:00 anchor, calendar date equals business date "
            + "everywhere, so the lookup-keying fix is byte-identical to today's behaviour")
    void uploadFtes_midnightAnchoredDesk_dataRowsResolveToOwnBusinessDate_controlUnchanged() throws Exception {
        when(deskRepository.findByIdAndTenantId(DESK_ID, TENANT_ID))
                .thenReturn(Optional.of(desk(LocalTime.MIDNIGHT)));

        Specialization voice = new Specialization();
        voice.setId(UUID.randomUUID());
        voice.setTenantId(TENANT_ID);
        voice.setDeskId(DESK_ID);
        voice.setName("Voice");
        when(specializationRepository.findByTenantIdAndDeskId(TENANT_ID, DESK_ID))
                .thenReturn(List.of(voice));

        // At a 00:00 anchor, calendar date == business date for every slot -- no mixed buckets.
        List<Timeslot> fixture = List.of(
                timeslot(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 4), LocalTime.of(22, 0)),
                timeslot(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 4), LocalTime.of(23, 0)),
                timeslot(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 4), LocalTime.of(6, 0)),
                timeslot(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 4), LocalTime.of(7, 0)),
                timeslot(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), LocalTime.of(22, 0)),
                timeslot(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), LocalTime.of(23, 0)),
                timeslot(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), LocalTime.of(6, 0)),
                timeslot(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), LocalTime.of(7, 0)));
        when(timeslotGeneratorService.generateTimeslots(
                any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(fixture);

        when(entityManager.getReference(eq(Timeslot.class), any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(1);
            return fixture.stream().filter(t -> t.getId().equals(id)).findFirst().orElseThrow();
        });

        service.uploadFtes(DESK_ID, twoSheetWorkbook(
                "2026-01-04", new int[] {11, 12, 13, 14},
                "2026-01-05", new int[] {21, 22, 23, 24}));

        ArgumentCaptor<StaffingRequirement> captor = ArgumentCaptor.forClass(StaffingRequirement.class);
        verify(staffingRequirementRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());

        Map<Integer, LocalDate> resolvedBusinessDateByFte = new HashMap<>();
        for (StaffingRequirement sr : captor.getAllValues()) {
            resolvedBusinessDateByFte.put(sr.getRequiredFTEs(), sr.getTimeslot().getBusinessDate());
        }

        assertThat(resolvedBusinessDateByFte.get(11)).isEqualTo(LocalDate.of(2026, 1, 4));
        assertThat(resolvedBusinessDateByFte.get(12)).isEqualTo(LocalDate.of(2026, 1, 4));
        assertThat(resolvedBusinessDateByFte.get(13)).isEqualTo(LocalDate.of(2026, 1, 4));
        assertThat(resolvedBusinessDateByFte.get(14)).isEqualTo(LocalDate.of(2026, 1, 4));
        assertThat(resolvedBusinessDateByFte.get(21)).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(resolvedBusinessDateByFte.get(22)).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(resolvedBusinessDateByFte.get(23)).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(resolvedBusinessDateByFte.get(24)).isEqualTo(LocalDate.of(2026, 1, 5));
    }
}
