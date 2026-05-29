package com.wfm.service;

import com.wfm.dto.SkippedRow;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests that the 16-col enriched spreadsheet shape is accepted and produces
 * correct SkippedRow records, including:
 * - Missing desk (blank "Desk" cell)
 * - Tie-breaker: both shape markers present → enriched wins
 * - Unknown shape → IllegalArgumentException with descriptive message
 */
class DeskAssignmentUploadEnrichedShapeTest {

    private AgentRepository agentRepository;
    private DeskRepository deskRepository;
    private ClientManagementService clientManagementService;
    private AgentPreferenceRepository agentPreferenceRepository;
    private AgentExceptionRepository agentExceptionRepository;
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
        specializationRepository = mock(SpecializationRepository.class);
        agentEligibilityService = mock(AgentEligibilityService.class);

        service = new DeskAssignmentUploadService(
                agentRepository, deskRepository, clientManagementService,
                agentPreferenceRepository, agentExceptionRepository,
                specializationRepository, agentEligibilityService);

        com.wfm.config.TenantContext.setTenantId(TENANT_ID);

        when(agentEligibilityService.isNonSchedulable(anyLong(), anyString())).thenReturn(false);
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    /**
     * Builds an enriched 16-col workbook with a single data row.
     * Headers: Employee ID | Name | Email | Desk | Monday ... Sunday
     * dataValues: [employeeId, name, email, desk] (null for blank cell)
     */
    private MockMultipartFile enrichedWorkbook(String employeeId, String name, String email, String desk)
            throws Exception {
        String[] headers = {"Employee ID", "Name", "Email", "Desk",
                "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        String[] data = {employeeId, name, email, desk};
        return buildWorkbook(headers, data);
    }

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

    private MockMultipartFile buildHeaderOnlyWorkbook(String[] headers) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Sheet1");

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
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
    void enrichedShape_missingDesk_producesSkippedRowWithMissingDeskReason() throws Exception {
        // Row has blank "Desk" cell → "Missing Desk"
        MockMultipartFile file = enrichedWorkbook("E001", "Alice", "alice@example.com", null);

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());
        when(clientManagementService.findCachedEmployee(any(), any(), any())).thenReturn(null);

        DeskAssignmentUploadService.DeskAssignmentUploadResult result =
                service.uploadDeskAssignments(file);

        assertThat(result.skippedCount()).isEqualTo(1);
        SkippedRow skipped = result.skippedDetails().get(0);
        assertThat(skipped.rowNumber()).isEqualTo(2);
        assertThat(skipped.reason()).isEqualTo("Missing Desk");
    }

    @Test
    void enrichedShape_tieBreakerBothMarkersPresent_enrichedWins() throws Exception {
        // Headers contain BOTH "Desk Assignment" (legacy marker) AND "Desk"+"Monday"+"Sunday"
        // → enriched wins; the "Desk" column is used (not "Desk Assignment")
        String[] hybridHeaders = {
                "Employee ID", "Name", "Email", "Desk Assignment", "Desk",
                "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
        };
        // Column 4 = "Desk" is blank → "Missing Desk" (enriched wins, uses "Desk" column)
        String[] dataRow = {"E002", "Bob", "bob@example.com", "Legacy Value", null};
        MockMultipartFile file = buildWorkbook(hybridHeaders, dataRow);

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        // Should NOT throw; enriched shape detected; blank "Desk" cell → "Missing Desk"
        DeskAssignmentUploadService.DeskAssignmentUploadResult result =
                service.uploadDeskAssignments(file);

        assertThat(result.skippedCount()).isEqualTo(1);
        SkippedRow skipped = result.skippedDetails().get(0);
        assertThat(skipped.reason()).isEqualTo("Missing Desk");
    }

    @Test
    void unknownShape_throwsIllegalArgumentExceptionWithHeadersInMessage() throws Exception {
        // Headers match neither legacy nor enriched shape
        String[] unknownHeaders = {"FirstName", "LastName", "Phone"};
        MockMultipartFile file = buildHeaderOnlyWorkbook(unknownHeaders);

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.uploadDeskAssignments(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unrecognised spreadsheet shape")
                .satisfies(ex -> assertThat(ex.getMessage()).containsIgnoringCase("Got headers:"));
    }

    @Test
    void enrichedShape_deskNotFound_producesSkippedRow() throws Exception {
        MockMultipartFile file = enrichedWorkbook("E003", "Carol", "carol@example.com", "Unknown Desk");

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        DeskAssignmentUploadService.DeskAssignmentUploadResult result =
                service.uploadDeskAssignments(file);

        assertThat(result.skippedDetails()).hasSize(1);
        SkippedRow skipped = result.skippedDetails().get(0);
        assertThat(skipped.reason()).contains("Unknown Desk").contains("not found");
    }
}
