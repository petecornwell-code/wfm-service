package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.dto.DeskAgentResponse.UsualShiftEntry;
import com.wfm.model.Desk;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.util.EnrichedColumnLayout;
import com.wfm.util.FormulaInjectionSanitizer;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the pre-seeded per-desk blank template (D-13/D-14/UPL-09): one worksheet per desk,
 * named after the desk, with the current roster's identity columns filled, the 7 Usual Shift
 * columns pre-filled with each agent's stored values (D-09) with a sheet-scoped dropdown of the
 * desk's live template names (D-10), and the 7 day-hours + 2 specialty columns left blank for the
 * operator to fill in before re-uploading.
 *
 * Shares {@link EnrichedColumnLayout} with the parser ({@code DeskAssignmentUploadService}) and
 * the export ({@code DeskAgentExportService}) so the template/parser/export shapes can never drift.
 */
@Service
public class DeskAssignmentTemplateService {

    private static final Logger log = LoggerFactory.getLogger(DeskAssignmentTemplateService.class);

    /** First of the seven Usual Shift columns (0-indexed): 7 identity + 7 day hours (P-15). */
    private static final int FIRST_USUAL_SHIFT_COLUMN = 14;

    /** Excel's data-validation formula1 text limit (16-RESEARCH.md Pitfall 5) — exceeding it
     *  silently corrupts the generated workbook rather than producing a friendly error. */
    private static final int MAX_EXPLICIT_LIST_LENGTH = 255;

    private final DeskRepository deskRepository;
    private final DeskAgentService deskAgentService;
    private final AgentEligibilityService agentEligibilityService;
    private final ShiftTemplateRepository shiftTemplateRepository;

    public DeskAssignmentTemplateService(DeskRepository deskRepository,
                                         DeskAgentService deskAgentService,
                                         AgentEligibilityService agentEligibilityService,
                                         ShiftTemplateRepository shiftTemplateRepository) {
        this.deskRepository = deskRepository;
        this.deskAgentService = deskAgentService;
        this.agentEligibilityService = agentEligibilityService;
        this.shiftTemplateRepository = shiftTemplateRepository;
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
                    // Columns 7-13 (Monday..Sunday day hours) and 21-22 (Specialty 1/2) are
                    // intentionally left blank for the operator to fill in (D-14). Columns 14-20
                    // (Usual Shift Monday..Sunday) are DIFFERENT: they are pre-filled with each
                    // agent's stored usual-shift template name (D-09). Without this pre-fill,
                    // clearDesk's Usual Shift wipe (D-11) plus D-07's blank-means-none rule would
                    // mean an operator downloading this template to fix one agent's hours would
                    // silently wipe every stored usual shift on the desk on re-upload -- the
                    // pre-fill is what makes a download-then-immediate-re-upload a safe no-op.
                    writeUsualShiftCells(row, agent);
                }

                attachUsualShiftDropdown(sheet, tenantId, desk);

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
        // P-15: Usual Shift columns sit immediately after day hours (indices 14-20), matching
        // D-18's export placement, so template and export are one shape and an exported sheet
        // is directly re-uploadable. Specialty headers move from 14/15 to 21/22.
        for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
            headers.add(EnrichedColumnLayout.usualShiftHeader(day));
        }
        headers.add(EnrichedColumnLayout.specialtyHeader(1));
        headers.add(EnrichedColumnLayout.specialtyHeader(2));
        return headers;
    }

    /**
     * D-09 pre-fill: writes the seven Usual Shift cells with the RAW stored template name --
     * the identical value function {@code DeskAgentExportService.writeUsualShiftCells} uses, so
     * there is one derivation of "what does this agent's stored usual shift look like as text",
     * not two. A null entry or a null name leaves the cell absent (via {@link #writeSanitized}),
     * which is what makes D-07's blank-means-none round trip correctly on re-upload.
     */
    private void writeUsualShiftCells(Row row, DeskAgentResponse agent) {
        var usualShift = agent.usualShift();
        DayOfWeek[] order = EnrichedColumnLayout.DAY_ORDER;
        for (int i = 0; i < order.length; i++) {
            UsualShiftEntry entry = usualShift != null ? usualShift.get(order[i]) : null;
            writeSanitized(row, FIRST_USUAL_SHIFT_COLUMN + i, entry != null ? entry.name() : null);
        }
    }

    /**
     * D-10: attaches a sheet-scoped Excel explicit-list data-validation dropdown to each of the
     * seven Usual Shift columns, listing this desk's live (currently effective) template names.
     * {@code addValidationData} is a {@link Sheet}-level call, so the sheet-scoped requirement is
     * satisfied structurally -- desk A's sheet can never carry desk B's names (USHF-02/adjacency).
     *
     * <p>P-14: degrades gracefully (skips the dropdown, writes headers/pre-fill as normal) in
     * three named cases -- zero live templates, the comma-joined name list over 255 characters
     * (16-RESEARCH.md Pitfall 5 -- Excel silently treats an oversized validation formula as a
     * corrupt file, not a friendly error), or any live template name containing a comma or a
     * double-quote (POI's explicit-list constraint joins names with commas, so such a name would
     * silently split into bogus options). D-10 states the dropdown "does not replace parser
     * validation" (D-08 still applies on every path), which is what makes this degradation
     * acceptable rather than a gap. The hidden-sheet/named-range formula-list fallback
     * (16-RESEARCH.md Pattern 5) is a documented, deliberately UNBUILT escape hatch -- no desk in
     * this project has a library anywhere near the size that would need it.
     */
    private void attachUsualShiftDropdown(Sheet sheet, long tenantId, Desk desk) {
        List<String> templateNames = shiftTemplateRepository
                .findByTenantIdAndDeskId(tenantId, desk.getId())
                .stream()
                .filter(t -> t.isEffectiveOn(LocalDate.now()))
                .map(ShiftTemplate::getName)
                .distinct()
                .sorted()
                .toList();

        if (templateNames.isEmpty()) {
            log.warn("Desk {} has zero live shift templates -- skipping Usual Shift dropdown (P-14); "
                    + "parser validation (D-08) still applies", desk.getId());
            return;
        }

        String joined = String.join(",", templateNames);
        if (joined.length() > MAX_EXPLICIT_LIST_LENGTH) {
            log.warn("Desk {} live template names joined exceed the {}-char Excel data-validation "
                    + "limit ({} chars) -- skipping Usual Shift dropdown (P-14); parser validation "
                    + "(D-08) still applies", desk.getId(), MAX_EXPLICIT_LIST_LENGTH, joined.length());
            return;
        }

        boolean hasIllegalCharacter = templateNames.stream()
                .anyMatch(name -> name.contains(",") || name.contains("\""));
        if (hasIllegalCharacter) {
            log.warn("Desk {} has a live shift template name containing a comma or double-quote -- "
                    + "skipping Usual Shift dropdown (P-14); parser validation (D-08) still applies",
                    desk.getId());
            return;
        }

        DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint =
                dvHelper.createExplicitListConstraint(templateNames.toArray(new String[0]));
        for (int i = 0; i < EnrichedColumnLayout.DAY_ORDER.length; i++) {
            int columnIndex = FIRST_USUAL_SHIFT_COLUMN + i;
            CellRangeAddressList addressList =
                    new CellRangeAddressList(1, 1048575, columnIndex, columnIndex);
            DataValidation validation = dvHelper.createValidation(constraint, addressList);
            sheet.addValidationData(validation);
        }
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
