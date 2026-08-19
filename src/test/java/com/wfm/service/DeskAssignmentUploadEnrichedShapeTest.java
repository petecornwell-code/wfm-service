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
 * D-15 retires the old flat single-sheet 16-col enriched shape (per-row "Desk"
 * column) — it is now rejected file-wide by {@code DeskAssignmentUploadRetiredShapeTest},
 * not accepted. The only assertion still valid against this class's original scope
 * is the generic "unrecognised spreadsheet shape" fallback for headers that match
 * neither the retired shapes nor the new per-desk shape.
 */
class DeskAssignmentUploadEnrichedShapeTest {

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

        service = new DeskAssignmentUploadService(
                agentRepository, deskRepository, clientManagementService,
                agentPreferenceRepository, agentExceptionRepository, agentDayHoursRepository,
                specializationRepository, agentEligibilityService, agentMergeService, transactionTemplate);

        com.wfm.config.TenantContext.setTenantId(TENANT_ID);

        when(agentEligibilityService.isNonSchedulable(anyLong(), anyString())).thenReturn(false);
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

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
    void unknownShape_throwsIllegalArgumentExceptionWithHeadersInMessage() throws Exception {
        // Headers match neither a retired shape nor the new per-desk shape
        String[] unknownHeaders = {"FirstName", "LastName", "Phone"};
        MockMultipartFile file = buildHeaderOnlyWorkbook(unknownHeaders);

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.uploadDeskAssignments(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unrecognised spreadsheet shape")
                .satisfies(ex -> assertThat(ex.getMessage()).containsIgnoringCase("Got headers:"));
    }
}
