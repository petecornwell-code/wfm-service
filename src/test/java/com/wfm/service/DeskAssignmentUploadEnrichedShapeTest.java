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
     * Enriched 16-col shape headers include "Desk", "Monday" and "Sunday" (day columns).
     * Required enriched columns: Employee ID | Name | Email | Desk | Monday | Tuesday | ... | Sunday
     */
    private MockMultipartFile enrichedWorkbook(List<String[]> dataRows) throws Exception {
        return enrichedWorkbookWithHeaders(
                new String[]{"Employee ID", "Name", "Email", "Desk",
                        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"},
                dataRows);
    }

    private MockMultipartFile enrichedWorkbookWithHeaders(String[] headers, List<String[]> dataRows) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Sheet1");

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        int rowIdx = 1;
        for (String[] data : dataRows) {
            Row row = sheet.createRow(rowIdx++);
            for (int i = 0; i < data.length; i++) {
                if (data[i] != null) row.createCell(i).setCellValue(data[i]);
            }
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
        MockMultipartFile file = enrichedWorkbook(List.of(
                new String[]{"E001", "Alice", "alice@example.com", null}
        ));

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
        // Headers contain both "Desk Assignment" (legacy marker) AND "Desk"+"Monday"+"Sunday"
        // → enriched wins; Desk column is used (not "Desk Assignment")
        String[] hybridHeaders = new String[]{
                "Employee ID", "Name", "Email", "Desk Assignment", "Desk",
                "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
        };
        // Row with a valid enriched Desk (column "Desk") but blank "Desk Assignment"
        MockMultipartFile file = enrichedWorkbookWithHeaders(hybridHeaders, List.of(
                new String[]{"E002", "Bob", "bob@example.com", "Legacy Desk", null}
        ));

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
        String[] unknownHeaders = new String[]{"FirstName", "LastName", "Phone"};
        MockMultipartFile file = enrichedWorkbookWithHeaders(unknownHeaders, List.of());

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.uploadDeskAssignments(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unrecognised spreadsheet shape")
                .hasMessageContaining("firstname")   // lowercase after trim
                .satisfies(ex -> assertThat(ex.getMessage()).containsIgnoringCase("Got headers:"));
    }

    @Test
    void enrichedShape_deskNotFound_producesSkippedRow() throws Exception {
        MockMultipartFile file = enrichedWorkbook(List.of(
                new String[]{"E003", "Carol", "carol@example.com", "Unknown Desk"}
        ));

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        DeskAssignmentUploadService.DeskAssignmentUploadResult result =
                service.uploadDeskAssignments(file);

        assertThat(result.skippedDetails()).hasSize(1);
        SkippedRow skipped = result.skippedDetails().get(0);
        assertThat(skipped.reason()).contains("Unknown Desk").contains("not found");
    }
}
