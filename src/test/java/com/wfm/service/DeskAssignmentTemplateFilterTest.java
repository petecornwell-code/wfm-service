package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.model.Desk;
import com.wfm.repository.DeskRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The template pre-seeds only agents the operator can actually schedule: active, and passing
 * the tenant's job-title allowlist. Inactive or non-allowlisted roster members are omitted so a
 * downloaded-then-reuploaded template cannot contain rows the parser would immediately skip.
 */
class DeskAssignmentTemplateFilterTest {

    private static final long TENANT_ID = 1L;
    private static final String CSR = "Customer Support Representative";
    private static final int BAMBOOHR_ID_COL = 0;

    private DeskRepository deskRepository;
    private DeskAgentService deskAgentService;
    private AgentEligibilityService agentEligibilityService;
    private DeskAssignmentTemplateService service;

    @BeforeEach
    void setUp() {
        deskRepository = mock(DeskRepository.class);
        deskAgentService = mock(DeskAgentService.class);
        agentEligibilityService = mock(AgentEligibilityService.class);
        service = new DeskAssignmentTemplateService(
                deskRepository, deskAgentService, agentEligibilityService);
        TenantContext.setTenantId(TENANT_ID);
    }

    private Desk desk(String name) {
        Desk desk = new Desk();
        desk.setId(UUID.randomUUID());
        desk.setTenantId(TENANT_ID);
        desk.setName(name);
        return desk;
    }

    private DeskAgentResponse rosterAgent(UUID deskId, String bamboohrId, String jobTitle, boolean active) {
        return new DeskAgentResponse(
                UUID.randomUUID(), deskId, bamboohrId, "Mary Watson",
                "Mary", "Watson", "mary.watson@example.com",
                "Billing", jobTitle, active,
                null, null, List.of(),
                null, null, null,
                0, List.of());
    }

    /** BambooHR IDs of the data rows actually written to the sheet. */
    private List<String> seededIds(Sheet sheet) {
        List<String> ids = new ArrayList<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Cell cell = row.getCell(BAMBOOHR_ID_COL);
            if (cell == null || cell.getCellType() == CellType.BLANK) continue;
            ids.add(cell.getStringCellValue());
        }
        return ids;
    }

    private Sheet generate(String deskName) throws Exception {
        byte[] xlsx = service.generateTemplate();
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx)).getSheet(deskName);
    }

    @Test
    void inactiveAgentsAreNotSeeded() throws Exception {
        Desk billing = desk("Billing");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);
        when(deskAgentService.listDeskAgentResponses(any(UUID.class), eq(null), eq(null), anyInt()))
                .thenReturn(List.of(
                        rosterAgent(billing.getId(), "A1", CSR, true),
                        rosterAgent(billing.getId(), "A2", CSR, false),
                        rosterAgent(billing.getId(), "A3", CSR, true)));

        assertThat(seededIds(generate("Billing"))).containsExactly("A1", "A3");
    }

    @Test
    void agentsFailingTheAllowlistAreNotSeeded() throws Exception {
        Desk billing = desk("Billing");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(agentEligibilityService.isIncludedByTitleAllowlist(TENANT_ID, CSR)).thenReturn(true);
        when(agentEligibilityService.isIncludedByTitleAllowlist(TENANT_ID, "Team Lead")).thenReturn(false);
        when(deskAgentService.listDeskAgentResponses(any(UUID.class), eq(null), eq(null), anyInt()))
                .thenReturn(List.of(
                        rosterAgent(billing.getId(), "A1", CSR, true),
                        rosterAgent(billing.getId(), "A2", "Team Lead", true)));

        assertThat(seededIds(generate("Billing"))).containsExactly("A1");
    }

    @Test
    void inactiveAndNonAllowlisted_bothExcluded_leavingOnlyEligible() throws Exception {
        Desk billing = desk("Billing");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(agentEligibilityService.isIncludedByTitleAllowlist(TENANT_ID, CSR)).thenReturn(true);
        when(agentEligibilityService.isIncludedByTitleAllowlist(TENANT_ID, "Team Lead")).thenReturn(false);
        when(deskAgentService.listDeskAgentResponses(any(UUID.class), eq(null), eq(null), anyInt()))
                .thenReturn(List.of(
                        rosterAgent(billing.getId(), "A1", CSR, true),          // kept
                        rosterAgent(billing.getId(), "A2", CSR, false),         // inactive
                        rosterAgent(billing.getId(), "A3", "Team Lead", true),  // not allowlisted
                        rosterAgent(billing.getId(), "A4", "Team Lead", false), // both
                        rosterAgent(billing.getId(), "A5", CSR, true)));        // kept

        assertThat(seededIds(generate("Billing"))).containsExactly("A1", "A5");
    }

    @Test
    void allAgentsFiltered_leavesHeaderOnlySheet() throws Exception {
        Desk billing = desk("Billing");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(false);
        when(deskAgentService.listDeskAgentResponses(any(UUID.class), eq(null), eq(null), anyInt()))
                .thenReturn(List.of(rosterAgent(billing.getId(), "A1", "Team Lead", true)));

        Sheet sheet = generate("Billing");

        // Header row survives so the operator still gets a usable, correctly-shaped sheet.
        assertThat(sheet.getRow(0)).isNotNull();
        assertThat(seededIds(sheet)).isEmpty();
    }

    @Test
    void seededRowsAreContiguous_noBlankGapsWhereFilteredAgentsWere() throws Exception {
        // Regression guard: filtering must skip BEFORE createRow, otherwise excluded agents
        // leave empty rows that Excel renders as gaps in the middle of the roster.
        Desk billing = desk("Billing");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);
        when(deskAgentService.listDeskAgentResponses(any(UUID.class), eq(null), eq(null), anyInt()))
                .thenReturn(List.of(
                        rosterAgent(billing.getId(), "A1", CSR, false),
                        rosterAgent(billing.getId(), "A2", CSR, true)));

        Sheet sheet = generate("Billing");

        assertThat(sheet.getLastRowNum()).isEqualTo(1); // header + exactly one data row
        assertThat(sheet.getRow(1).getCell(BAMBOOHR_ID_COL).getStringCellValue()).isEqualTo("A2");
    }
}
