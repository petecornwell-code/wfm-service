package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.model.Desk;
import com.wfm.repository.DeskRepository;
import com.wfm.util.EnrichedColumnLayout;
import com.wfm.util.FormulaInjectionSanitizer;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the pre-seeded per-desk blank template (D-13/D-14/UPL-09): one worksheet per desk,
 * named after the desk, with the current roster's identity columns filled and the 7 day cells +
 * specialty columns left blank for the operator to fill in before re-uploading.
 *
 * Shares {@link EnrichedColumnLayout} with the parser ({@code DeskAssignmentUploadService}) and
 * the export ({@code DeskAgentExportService}) so the template/parser/export shapes can never drift.
 */
@Service
public class DeskAssignmentTemplateService {

    private final DeskRepository deskRepository;
    private final DeskAgentService deskAgentService;
    private final AgentEligibilityService agentEligibilityService;

    public DeskAssignmentTemplateService(DeskRepository deskRepository,
                                         DeskAgentService deskAgentService,
                                         AgentEligibilityService agentEligibilityService) {
        this.deskRepository = deskRepository;
        this.deskAgentService = deskAgentService;
        this.agentEligibilityService = agentEligibilityService;
    }

    public byte[] generateTemplate() {
        long tenantId = TenantContext.getTenantId();
        List<Desk> desks = deskRepository.findByTenantId(tenantId);

        List<String> headers = buildHeaders();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            for (Desk desk : desks) {
                Sheet sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(desk.getName()));

                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers.get(i));
                    cell.setCellStyle(headerStyle);
                }

                List<DeskAgentResponse> roster = deskAgentService.listDeskAgentResponses(
                        desk.getId(), null, null, Integer.MAX_VALUE);

                int rowNum = 1;
                for (DeskAgentResponse agent : roster) {
                    // Seed only agents the operator can actually schedule: active, and passing the
                    // tenant's job-title allowlist. Keeps the template consistent with what the
                    // upload parser will accept, so a downloaded-then-reuploaded template cannot
                    // produce rows that are immediately skipped.
                    if (!isSeedable(tenantId, agent)) {
                        continue;
                    }
                    Row row = sheet.createRow(rowNum++);
                    writeSanitized(row, 0, agent.bamboohrId());
                    writeSanitized(row, 1, agent.firstName());
                    writeSanitized(row, 2, agent.lastName());
                    writeSanitized(row, 3, agent.jobTitle());
                    writeSanitized(row, 4, agent.email());
                    writeSanitized(row, 5, agent.department());
                    writeSanitized(row, 6, agent.active() ? "Yes" : "No");
                    // Columns 7-13 (Monday..Sunday) and 14-15 (Specialty 1/2) are intentionally
                    // left blank for the operator to fill in (D-14).
                }

                for (int i = 0; i < headers.size(); i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate desk assignment template", e);
        }
    }

    /**
     * Whether a roster agent should be pre-seeded into the template. Mirrors the corresponding
     * checks in {@code DeskAssignmentUploadService} — inactive agents and agents failing the
     * job-title allowlist are omitted rather than written as rows that would be skipped on
     * re-upload.
     *
     * Note the non-schedulable denylist is deliberately NOT applied here: those agents are still
     * roster members and the parser reports them with an explicit reason, so omitting them from
     * the template would hide an actionable configuration problem from the operator.
     */
    private boolean isSeedable(long tenantId, DeskAgentResponse agent) {
        return agent.active()
                && agentEligibilityService.isIncludedByTitleAllowlist(tenantId, agent.jobTitle());
    }

    private List<String> buildHeaders() {
        List<String> headers = new ArrayList<>(EnrichedColumnLayout.identityHeaders());
        for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
            headers.add(EnrichedColumnLayout.dayHeader(day));
        }
        headers.add(EnrichedColumnLayout.specialtyHeader(1));
        headers.add(EnrichedColumnLayout.specialtyHeader(2));
        return headers;
    }

    /**
     * Formula/CSV-injection guard (T-10-08/CR-02/WR-05): delegates to the shared
     * {@link FormulaInjectionSanitizer} so this generator and {@code DeskAgentExportService}
     * (and the frontend's mirrored {@code sanitize()} in ClientManagement.tsx) cannot drift on
     * the exact character set being neutralized.
     */
    private void writeSanitized(Row row, int columnIndex, String value) {
        if (value == null) {
            return; // leave the cell blank
        }
        row.createCell(columnIndex).setCellValue(FormulaInjectionSanitizer.sanitize(value));
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
