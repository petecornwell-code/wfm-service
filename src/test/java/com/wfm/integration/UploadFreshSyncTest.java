package com.wfm.integration;

import com.wfm.dto.MergeReportEntry;
import com.wfm.model.Agent;
import com.wfm.model.Desk;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentExceptionRepository;
import com.wfm.repository.AgentPreferenceRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.service.AgentEligibilityService;
import com.wfm.service.ClientManagementService;
import com.wfm.service.DeskAssignmentUploadService;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MRG-01/D-01/D-02: end-to-end proof that a single upload issues exactly one
 * {@code listEmployees} and one {@code listTimeOff} call, that both complete before the
 * transactional write pass opens, and that the merged value (BambooHR-first for Email)
 * reaches both the saved {@link Agent} and the returned merge report.
 */
class UploadFreshSyncTest {

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

    private Desk desk;

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
        agentMergeService = spy(new AgentMergeService(bambooHRClient));

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

        desk = new Desk();
        desk.setId(UUID.randomUUID());
        desk.setTenantId(TENANT_ID);
        desk.setName("Support");

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(desk));
        when(specializationRepository.findByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndDeskId(anyLong(), any())).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndBamboohrId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));
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

        // bamboohrId, firstName, lastName, jobTitle, email, department, active, Mon..Sun
        List<String> rowValues = new ArrayList<>(List.of(
                "B1", "Alice", "A", "", "sheet-email@example.com", "", ""));
        rowValues.addAll(List.of("8", "8", "8", "8", "8", "0", "0"));

        Row row = sheet.createRow(1);
        for (int c = 0; c < rowValues.size(); c++) {
            String value = rowValues.get(c);
            if (value == null || value.isBlank()) continue;
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

    @Test
    void upload_fetchesFreshSnapshotBeforeTransaction_andMergesEmailBambooHRFirst() throws Exception {
        BambooEmployee employee = new BambooEmployee("B1", "Alice A", "bamboo-email@example.com",
                "Support", "Agent", "Active", "Full-Time", null, null, null);
        when(bambooHRClient.listEmployees(eq(String.valueOf(TENANT_ID)), isNull()))
                .thenReturn(List.of(employee));
        when(bambooHRClient.listTimeOff(eq(String.valueOf(TENANT_ID)), any(), any()))
                .thenReturn(List.of());

        MockMultipartFile file = buildWorkbook();

        DeskAssignmentUploadService.DeskAssignmentUploadResult result = service.uploadDeskAssignments(file);

        // Exactly one listEmployees + one listTimeOff, both before the transaction opened.
        verify(bambooHRClient, times(1)).listEmployees(anyString(), isNull());
        verify(bambooHRClient, times(1)).listTimeOff(anyString(), any(), any());
        InOrder inOrder = inOrder(bambooHRClient, transactionTemplate);
        inOrder.verify(bambooHRClient).listEmployees(anyString(), isNull());
        inOrder.verify(bambooHRClient).listTimeOff(anyString(), any(), any());
        inOrder.verify(transactionTemplate).executeWithoutResult(any());

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("bamboo-email@example.com");

        assertThat(result.mergeReport()).hasSize(1);
        MergeReportEntry entry = result.mergeReport().get(0);
        assertThat(entry.field()).isEqualTo("Email");
        assertThat(entry.bambooValue()).isEqualTo("bamboo-email@example.com");
        assertThat(entry.sheetValue()).isEqualTo("sheet-email@example.com");
        assertThat(entry.outcome()).isEqualTo(AgentMergeService.OUTCOME_BAMBOOHR_OVERRIDE);
    }
}
