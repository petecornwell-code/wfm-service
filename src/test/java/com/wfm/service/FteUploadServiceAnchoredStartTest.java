package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.FteUploadResult;
import com.wfm.model.Desk;
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
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
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
}
