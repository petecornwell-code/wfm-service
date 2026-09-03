package com.wfm.service;

import com.wfm.integration.AgentMergeService;
import com.wfm.integration.BambooEmployee;
import com.wfm.integration.BambooHRClient;
import com.wfm.model.*;
import com.wfm.repository.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * D-15 retires the 6-col legacy shape entirely — it is rejected file-wide with a
 * "download the new template" message rather than imported row-by-row as it was
 * pre-Phase-10. The row-level skip/import assertions this class previously carried
 * (missing desk, desk-not-found, successful assignment) no longer apply, since the
 * whole file is now rejected before any row is parsed.
 *
 * Legacy headers: BambooHR ID | Name | Email | Desk Assignment | Specialty 1 | ...
 */
class DeskAssignmentUploadLegacyShapeTest {

    private AgentRepository agentRepository;
    private DeskRepository deskRepository;
    private ClientManagementService clientManagementService;
    private AgentPreferenceRepository agentPreferenceRepository;
    private AgentExceptionRepository agentExceptionRepository;
    private AgentDayHoursRepository agentDayHoursRepository;
    private SpecializationRepository specializationRepository;
    private AgentEligibilityService agentEligibilityService;
    private BambooHRClient bambooHRClient;
    private AgentMergeService agentMergeService;
    private TransactionTemplate transactionTemplate;
    private AgentUsualShiftRepository agentUsualShiftRepository;
    private ShiftTemplateRepository shiftTemplateRepository;
    private UsualShiftService usualShiftService;
    private Map<String, BambooEmployee> bambooEmployees;

    private DeskAssignmentUploadService service;

    private static final long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        agentRepository = mock(AgentRepository.class);
        deskRepository = mock(DeskRepository.class);
        clientManagementService = mock(ClientManagementService.class);
        agentPreferenceRepository = mock(AgentPreferenceRepository.class);
        agentExceptionRepository = mock(AgentExceptionRepository.class);
        agentDayHoursRepository = mock(AgentDayHoursRepository.class);
        specializationRepository = mock(SpecializationRepository.class);
        agentEligibilityService = mock(AgentEligibilityService.class);
        // Job-title allowlist inactive for this suite; without this stub the mock defaults
        // to false and every row would be skipped as "not in the configured allowlist".
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);

        bambooEmployees = new LinkedHashMap<>();
        bambooHRClient = mock(BambooHRClient.class);
        when(bambooHRClient.listEmployees(anyString(), isNull()))
                .thenAnswer(inv -> new ArrayList<>(bambooEmployees.values()));
        when(bambooHRClient.listTimeOff(anyString(), any(), any())).thenReturn(List.of());
        agentMergeService = new AgentMergeService(bambooHRClient);

        transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(inv -> {
            java.util.function.Consumer<Object> action = inv.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        agentUsualShiftRepository = mock(AgentUsualShiftRepository.class);
        shiftTemplateRepository = mock(ShiftTemplateRepository.class);
        usualShiftService = mock(UsualShiftService.class);
        when(shiftTemplateRepository.findByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());

        service = new DeskAssignmentUploadService(
                agentRepository, deskRepository, clientManagementService,
                agentPreferenceRepository, agentExceptionRepository, agentDayHoursRepository,
                specializationRepository, agentEligibilityService, agentMergeService, transactionTemplate,
                agentUsualShiftRepository, shiftTemplateRepository, usualShiftService);

        // Set up TenantContext — service calls TenantContext.getTenantId()
        com.wfm.config.TenantContext.setTenantId(TENANT_ID);

        // By default, agents are not non-schedulable
        when(agentEligibilityService.isNonSchedulable(anyLong(), anyString())).thenReturn(false);
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());
    }

    // ------------------------------------------------------------------ //
    //  Helper: build a legacy 6-col workbook as MockMultipartFile          //
    // ------------------------------------------------------------------ //

    /**
     * Builds a legacy workbook with a single data row.
     * Row values: [bamboohrId, name, email, deskAssignment] (nulls -> empty cells)
     */
    private MockMultipartFile legacyWorkbook(String bamboohrId, String name, String email, String desk)
            throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Sheet1");

        // Header row (legacy 6-col)
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("BambooHR ID");
        header.createCell(1).setCellValue("Name");
        header.createCell(2).setCellValue("Email");
        header.createCell(3).setCellValue("Desk Assignment");
        header.createCell(4).setCellValue("Specialty 1");
        header.createCell(5).setCellValue("Specialty 2");

        Row row = sheet.createRow(1);
        if (bamboohrId != null) row.createCell(0).setCellValue(bamboohrId);
        if (name != null) row.createCell(1).setCellValue(name);
        if (email != null) row.createCell(2).setCellValue(email);
        if (desk != null) row.createCell(3).setCellValue(desk);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(out.toByteArray()));
    }

    // ------------------------------------------------------------------ //
    //  Tests                                                               //
    // ------------------------------------------------------------------ //

    @Test
    void legacyShape_isRejectedWithDownloadTemplateMessage_regardlessOfRowContent() throws Exception {
        // Row content is irrelevant — shape classification happens against the header
        // row before any row is parsed, so a valid-looking legacy row is still rejected.
        MockMultipartFile file = legacyWorkbook("B003", "Carol", "carol@example.com", "Support Desk");

        assertThatThrownBy(() -> service.uploadDeskAssignments(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template")
                .satisfies(ex -> assertThat(ex.getMessage()).containsIgnoringCase("retired"));
    }

    @Test
    void legacyShape_isRejectedEvenWithBlankDeskAssignmentCell() throws Exception {
        MockMultipartFile file = legacyWorkbook("B001", "Alice", "alice@example.com", null);

        assertThatThrownBy(() -> service.uploadDeskAssignments(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template");
    }
}
