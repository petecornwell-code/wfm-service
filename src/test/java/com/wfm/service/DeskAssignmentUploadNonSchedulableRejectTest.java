package com.wfm.service;

import com.wfm.dto.SkippedRow;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Desk-assignment upload happy path and BambooHR-ID matching.
 *
 * The non-schedulable rejection tests that used to live here were removed on 2026-08-12: the
 * job-title allowlist replaced the non-schedulable denylist as the single control for
 * schedulability, and that rejection path no longer exists. Allowlist rejection is covered by
 * DeskAssignmentUploadAllowlistTest.
 *
 * Updated for Phase 10 (D-01/D-08): the sheet name IS the desk (no per-row
 * "Desk" column) and matching is by BambooHR ID only, via
 * findCachedEmployee(bamboohrId, null, null).
 */
class DeskAssignmentUploadNonSchedulableRejectTest {

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
    private static final String NON_SCHEDULABLE_TITLE = "Quality Assurance";
    private static final String[] DAY_HEADERS =
            {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final String[] FULL_WEEK = {"8", "8", "8", "8", "8", "0", "0"};

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

        // Configure non-schedulable title
        when(agentEligibilityService.isNonSchedulable(TENANT_ID, NON_SCHEDULABLE_TITLE)).thenReturn(true);
        when(agentEligibilityService.isNonSchedulable(TENANT_ID, "Agent")).thenReturn(false);
        // Handle null jobTitle gracefully
        when(agentEligibilityService.isNonSchedulable(anyLong(), isNull())).thenReturn(false);
        // Job-title allowlist inactive — this suite covers the non-schedulable denylist only.
        // Without this stub the mock would default to false and skip every row.
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    /**
     * Builds a one-sheet-per-desk workbook (D-01): sheet name = desk name, headers =
     * BambooHR ID + Monday..Sunday (no per-row Desk column), one row per agent.
     */
    private MockMultipartFile buildDeskWorkbook(String deskName, String[][] rows) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet(deskName);

        List<String> headers = new ArrayList<>(List.of("BambooHR ID", "Name", "Email"));
        headers.addAll(List.of(DAY_HEADERS));

        Row header = sheet.createRow(0);
        for (int c = 0; c < headers.size(); c++) {
            header.createCell(c).setCellValue(headers.get(c));
        }

        for (int r = 0; r < rows.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < rows[r].length; c++) {
                String value = rows[r][c];
                if (value == null) continue;
                // Day cells (D-03) hold a real number for hours, or the MANDATORY/PTO
                // keyword as text — mirror that here instead of always writing a string,
                // since the parser reads day cells via getNumericCellValue() (D-10).
                if (value.matches("-?\\d+(\\.\\d+)?")) {
                    row.createCell(c).setCellValue(Double.parseDouble(value));
                } else {
                    row.createCell(c).setCellValue(value);
                }
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(out.toByteArray()));
    }

    private String[] agentRow(String bamboohrId, String name, String email) {
        List<String> row = new ArrayList<>(List.of(bamboohrId, name, email));
        row.addAll(List.of(FULL_WEEK));
        return row.toArray(new String[0]);
    }

    // ------------------------------------------------------------------ //
    //  Tests                                                               //
    // ------------------------------------------------------------------ //


    @Test
    void schedulableAgent_isAssigned() throws Exception {
        UUID deskId = UUID.randomUUID();
        Desk desk = new Desk();
        desk.setId(deskId);
        desk.setTenantId(TENANT_ID);
        desk.setName("Sales Desk");

        Agent agent = new Agent();
        agent.setId(UUID.randomUUID());
        agent.setTenantId(TENANT_ID);
        agent.setBamboohrId("B200");
        agent.setName("Frank");
        agent.setEmail("frank@example.com");
        agent.setJobTitle("Agent");
        agent.setActive(true);

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(desk));
        when(specializationRepository.findByTenantIdAndDeskId(TENANT_ID, deskId)).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndDeskId(TENANT_ID, deskId)).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndBamboohrId(TENANT_ID, "B200"))
                .thenReturn(Optional.of(agent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));

        bambooEmployees.put("B200", new BambooEmployee(
                "B200", "Frank", "frank@example.com", "Sales", "Agent", "Active",
                "Full-Time", null, null, null));

        MockMultipartFile file = buildDeskWorkbook("Sales Desk",
                new String[][] { agentRow("B200", "Frank", "frank@example.com") });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result =
                service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(0);
    }


    @Test
    void unmatchedBambooHrId_isRejected_noAgentCreated() throws Exception {
        // UPL-07 / D-08: a row whose BambooHR ID is not in the cache is rejected
        // with a specific reason and no agent is created -- matching is by
        // BambooHR ID only, no fuzzy/name fallback creation path.
        UUID deskId = UUID.randomUUID();
        Desk desk = new Desk();
        desk.setId(deskId);
        desk.setTenantId(TENANT_ID);
        desk.setName("Orphan Desk");

        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(desk));
        when(specializationRepository.findByTenantIdAndDeskId(TENANT_ID, deskId)).thenReturn(List.of());
        when(agentRepository.findByTenantIdAndDeskId(TENANT_ID, deskId)).thenReturn(List.of());
        // No BambooHR employee for this ID -- the snapshot lookup misses (bambooEmployees stays empty)

        MockMultipartFile file = buildDeskWorkbook("Orphan Desk",
                new String[][] { agentRow("B999", "Ghost", "ghost@example.com") });

        DeskAssignmentUploadService.DeskAssignmentUploadResult result =
                service.uploadDeskAssignments(file);

        assertThat(result.assignedCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(1);
        SkippedRow skipped = result.skippedDetails().get(0);
        assertThat(skipped.bamboohrId()).isEqualTo("B999");
        assertThat(skipped.reason()).isEqualTo("BambooHR ID not found");

        // No agent lookup/creation/save happens for a row rejected before the
        // ID-match stage (parser checks the cache before touching agentRepository).
        verify(agentRepository, never()).findByTenantIdAndBamboohrId(anyLong(), anyString());
        verify(agentRepository, never()).save(any(Agent.class));
    }
}
