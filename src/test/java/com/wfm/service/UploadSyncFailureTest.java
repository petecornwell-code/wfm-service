package com.wfm.service;

import com.wfm.exception.BambooHRRateLimitedException;
import com.wfm.exception.BambooHRSyncFailedException;
import com.wfm.integration.AgentMergeService;
import com.wfm.integration.BambooHRClient;
import com.wfm.model.Agent;
import com.wfm.model.Desk;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentExceptionRepository;
import com.wfm.repository.AgentPreferenceRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MRG-07/D-02: every BambooHR sync-failure mode during an upload-triggered fetch aborts with
 * zero writes and a clear operator message stating that no changes were made. Covers both the
 * rate-limited and non-rate-limit failure shapes, and proves the transaction is never entered.
 */
class UploadSyncFailureTest {

    private static final long TENANT_ID = 1L;

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
    private DeskAssignmentUploadService service;

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
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);

        bambooHRClient = mock(BambooHRClient.class);
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

        Desk desk = new Desk();
        desk.setId(UUID.randomUUID());
        desk.setTenantId(TENANT_ID);
        desk.setName("Support");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(desk));
        when(specializationRepository.findByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());
    }

    private static String[] newShapeHeaders() {
        List<String> headers = new ArrayList<>(EnrichedColumnLayout.identityHeaders());
        for (DayOfWeek d : EnrichedColumnLayout.DAY_ORDER) headers.add(EnrichedColumnLayout.dayHeader(d));
        return headers.toArray(new String[0]);
    }

    private MockMultipartFile buildWorkbook() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Support");
        String[] headers = newShapeHeaders();

        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            headerRow.createCell(c).setCellValue(headers[c]);
        }

        List<String> rowValues = new ArrayList<>(List.of("B1", "Alice", "A", "", "", "", ""));
        rowValues.addAll(List.of("8", "8", "8", "8", "8", "0", "0"));

        Row row = sheet.createRow(1);
        for (int c = 0; c < rowValues.size(); c++) {
            String value = rowValues.get(c);
            if (value == null || value.isEmpty()) continue;
            if (value.matches("-?\\d+(\\.\\d+)?")) {
                row.createCell(c).setCellValue(Double.parseDouble(value));
            } else {
                row.createCell(c).setCellValue(value);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(out.toByteArray()));
    }

    private void assertZeroWrites() {
        verifyNoInteractions(agentDayHoursRepository);
        verify(agentRepository, never()).save(any(Agent.class));
        verify(transactionTemplate, never()).executeWithoutResult(any());
    }

    @Test
    void listEmployees_rateLimited_throws503WithNoChangesClauseAndPreservedRetryAfter() throws Exception {
        when(bambooHRClient.listEmployees(anyString(), isNull()))
                .thenThrow(new BambooHRRateLimitedException(
                        "BambooHR is rate-limiting requests. Retry in 42 seconds.", 42));

        MockMultipartFile file = buildWorkbook();

        assertThatThrownBy(() -> service.uploadDeskAssignments(file))
                .isInstanceOf(BambooHRRateLimitedException.class)
                .satisfies(ex -> {
                    BambooHRRateLimitedException rle = (BambooHRRateLimitedException) ex;
                    assertThat(rle.getMessage()).contains("no changes were made");
                    assertThat(rle.getRetryAfterSeconds()).isEqualTo(42);
                });
        assertZeroWrites();
    }

    @Test
    void listEmployeesSucceeds_listTimeOffThrows_neverEntersTransaction_zeroWrites() throws Exception {
        when(bambooHRClient.listEmployees(anyString(), isNull())).thenReturn(List.of());
        when(bambooHRClient.listTimeOff(anyString(), any(), any()))
                .thenThrow(new RuntimeException("connection reset"));

        MockMultipartFile file = buildWorkbook();

        assertThatThrownBy(() -> service.uploadDeskAssignments(file))
                .isInstanceOf(BambooHRSyncFailedException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).contains("no changes were made");
                    assertThat(ex.getMessage()).contains("connection reset");
                });
        assertZeroWrites();
    }

    @Test
    void listEmployees_nonRateLimitRuntimeError_throwsSyncFailedWith503AndUpstreamReason() throws Exception {
        when(bambooHRClient.listEmployees(anyString(), isNull()))
                .thenThrow(new RuntimeException("BambooHR returned HTTP 500"));

        MockMultipartFile file = buildWorkbook();

        assertThatThrownBy(() -> service.uploadDeskAssignments(file))
                .isInstanceOf(BambooHRSyncFailedException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).contains("no changes were made");
                    assertThat(ex.getMessage()).contains("BambooHR returned HTTP 500");
                });
        assertZeroWrites();
    }

    @Test
    void upstreamReasonEmpty_messageStillContainsNoChangesClause() throws Exception {
        when(bambooHRClient.listEmployees(anyString(), isNull()))
                .thenThrow(new RuntimeException((String) null));

        MockMultipartFile file = buildWorkbook();

        assertThatThrownBy(() -> service.uploadDeskAssignments(file))
                .isInstanceOf(BambooHRSyncFailedException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).contains("no changes were made");
                    assertThat(ex.getMessage()).contains("no detail available");
                });
        assertZeroWrites();
    }
}
