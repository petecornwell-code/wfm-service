package com.wfm.service;

import com.wfm.model.*;
import com.wfm.repository.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * D-15: both retired upload shapes — the 6-col legacy shape and the old flat
 * single-sheet enriched shape (per-row "Desk" column) — are rejected file-wide
 * with a "download the new template" message, not silently imported or lumped
 * into the generic "unrecognised shape" fallback.
 */
class DeskAssignmentUploadRetiredShapeTest {

    private AgentRepository agentRepository;
    private DeskRepository deskRepository;
    private ClientManagementService clientManagementService;
    private AgentPreferenceRepository agentPreferenceRepository;
    private AgentExceptionRepository agentExceptionRepository;
    private AgentDayHoursRepository agentDayHoursRepository;
    private SpecializationRepository specializationRepository;
    private AgentEligibilityService agentEligibilityService;
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
        service = new DeskAssignmentUploadService(
                agentRepository, deskRepository, clientManagementService,
                agentPreferenceRepository, agentExceptionRepository, agentDayHoursRepository,
                specializationRepository, agentEligibilityService);
        com.wfm.config.TenantContext.setTenantId(TENANT_ID);
        when(agentEligibilityService.isNonSchedulable(anyLong(), anyString())).thenReturn(false);
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private MockMultipartFile buildWorkbook(String[] headers, String[] dataRow) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Sheet1");

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        Row row = sheet.createRow(1);
        for (int i = 0; i < dataRow.length; i++) {
            if (dataRow[i] != null) row.createCell(i).setCellValue(dataRow[i]);
        }

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
    void legacy6ColShape_isRejectedWithDownloadTemplateMessage() throws Exception {
        // Legacy headers: BambooHR ID | Name | Email | Desk Assignment | Specialty 1 | Specialty 2
        String[] headers = {"BambooHR ID", "Name", "Email", "Desk Assignment", "Specialty 1", "Specialty 2"};
        String[] dataRow = {"B001", "Alice", "alice@example.com", "Support Desk", null, null};
        MockMultipartFile file = buildWorkbook(headers, dataRow);

        assertThatThrownBy(() -> service.uploadDeskAssignments(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template")
                .satisfies(ex -> assertThat(ex.getMessage()).containsIgnoringCase("retired"));
    }

    @Test
    void oldFlatEnrichedShape_isRejectedWithDownloadTemplateMessage() throws Exception {
        // Old flat-enriched headers: single sheet + per-row "Desk" column + Monday..Sunday
        String[] headers = {"Employee ID", "Name", "Email", "Desk",
                "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        String[] dataRow = {"E001", "Bob", "bob@example.com", "Billing", "8", "8", "8", "8", "8", "0", "0"};
        MockMultipartFile file = buildWorkbook(headers, dataRow);

        assertThatThrownBy(() -> service.uploadDeskAssignments(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template")
                .satisfies(ex -> assertThat(ex.getMessage()).containsIgnoringCase("retired"));
    }
}
