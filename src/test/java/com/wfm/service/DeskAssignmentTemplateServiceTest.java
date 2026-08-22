package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.model.Desk;
import com.wfm.repository.DeskRepository;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeskAssignmentTemplateServiceTest {

    private static final long TENANT_ID = 1L;

    private DeskRepository deskRepository;
    private DeskAgentService deskAgentService;
    private AgentEligibilityService agentEligibilityService;
    private DeskAssignmentTemplateService service;

    @BeforeEach
    void setUp() {
        deskRepository = mock(DeskRepository.class);
        deskAgentService = mock(DeskAgentService.class);
        agentEligibilityService = mock(AgentEligibilityService.class);
        // Default: allowlist inactive, so these pre-existing tests keep asserting the
        // unfiltered seeding behaviour. The allowlist itself is covered by
        // DeskAssignmentTemplateFilterTest.
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);
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

    private DeskAgentResponse rosterAgent(UUID deskId, String bamboohrId, String jobTitle) {
        return new DeskAgentResponse(
                UUID.randomUUID(), deskId, bamboohrId, "Mary Watson",
                "Mary", "Watson", "mary.watson@example.com",
                "Billing", jobTitle, true,
                null, null, List.of(),
                null, null, null,
                0, List.of(), Map.of());
    }

    private List<String> expectedHeaders() {
        List<String> headers = new ArrayList<>(EnrichedColumnLayout.identityHeaders());
        for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
            headers.add(EnrichedColumnLayout.dayHeader(day));
        }
        headers.add("Specialty 1");
        headers.add("Specialty 2");
        return headers;
    }

    private XSSFWorkbook generateAndReadBack() throws Exception {
        byte[] xlsx = service.generateTemplate();
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx));
    }

    private String cellString(Row row, int index) {
        if (row == null) return null;
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        return cell.getStringCellValue();
    }

    @Test
    void generateTemplate_producesOneSheetPerDeskNamedAfterTheDesk() throws Exception {
        Desk billing = desk("Billing");
        Desk support = desk("Support");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing, support));
        when(deskAgentService.listDeskAgentResponses(any(UUID.class), eq(null), eq(null), anyInt()))
                .thenReturn(List.of());

        XSSFWorkbook workbook = generateAndReadBack();

        assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
        assertThat(workbook.getSheet("Billing")).isNotNull();
        assertThat(workbook.getSheet("Support")).isNotNull();
    }

    @Test
    void generateTemplate_headerRowMatchesEnrichedColumnLayoutPlusDaysAndSpecialties() throws Exception {
        Desk billing = desk("Billing");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        when(deskAgentService.listDeskAgentResponses(any(UUID.class), eq(null), eq(null), anyInt()))
                .thenReturn(List.of());

        XSSFWorkbook workbook = generateAndReadBack();
        Sheet sheet = workbook.getSheet("Billing");
        Row headerRow = sheet.getRow(0);

        List<String> expected = expectedHeaders();
        assertThat(expected).hasSize(16); // 7 identity + 7 days + 2 specialty
        for (int i = 0; i < expected.size(); i++) {
            assertThat(cellString(headerRow, i)).isEqualTo(expected.get(i));
        }
    }

    @Test
    void generateTemplate_seededAgent_identityFilled_dayAndSpecialtyCellsBlank() throws Exception {
        Desk billing = desk("Billing");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        DeskAgentResponse agent = rosterAgent(billing.getId(), "4517", "Agent");
        when(deskAgentService.listDeskAgentResponses(eq(billing.getId()), eq(null), eq(null), anyInt()))
                .thenReturn(List.of(agent));

        XSSFWorkbook workbook = generateAndReadBack();
        Sheet sheet = workbook.getSheet("Billing");
        Row dataRow = sheet.getRow(1);

        assertThat(cellString(dataRow, 0)).isEqualTo("4517");
        assertThat(cellString(dataRow, 1)).isEqualTo("Mary");
        assertThat(cellString(dataRow, 2)).isEqualTo("Watson");
        assertThat(cellString(dataRow, 3)).isEqualTo("Agent");
        assertThat(cellString(dataRow, 4)).isEqualTo("mary.watson@example.com");
        assertThat(cellString(dataRow, 5)).isEqualTo("Billing");
        assertThat(cellString(dataRow, 6)).isEqualTo("Yes");

        // Columns 7-13 = Monday..Sunday, 14-15 = Specialty 1/2 — must all be blank
        for (int i = 7; i <= 15; i++) {
            assertThat(cellString(dataRow, i)).isNull();
        }
    }

    @Test
    void generateTemplate_formulaLikeValue_isSanitizedWithLeadingQuote() throws Exception {
        Desk billing = desk("Billing");
        when(deskRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(billing));
        DeskAgentResponse agent = rosterAgent(billing.getId(), "4517", "=SUM(A1)");
        when(deskAgentService.listDeskAgentResponses(eq(billing.getId()), eq(null), eq(null), anyInt()))
                .thenReturn(List.of(agent));

        XSSFWorkbook workbook = generateAndReadBack();
        Sheet sheet = workbook.getSheet("Billing");
        Row dataRow = sheet.getRow(1);

        Cell jobTitleCell = dataRow.getCell(3);
        assertThat(jobTitleCell.getCellType()).isEqualTo(CellType.STRING);
        assertThat(jobTitleCell.getStringCellValue()).isEqualTo("'=SUM(A1)");
    }
}
