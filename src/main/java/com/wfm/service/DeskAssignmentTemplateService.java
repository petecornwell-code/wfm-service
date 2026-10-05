package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.DeskAgentResponse;
import com.wfm.dto.DeskAssignmentSelectionRequest;
import com.wfm.dto.DeskAgentResponse.UsualShiftEntry;
import com.wfm.integration.BambooHRClient;
import com.wfm.integration.BambooTimeOff;
import com.wfm.model.DayOffType;
import com.wfm.model.Desk;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.DeskRepository;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.exception.UnprocessableException;
import com.wfm.repository.ShiftTemplateRepository;
import com.wfm.util.AgentNameSplitter;
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
import java.util.UUID;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final BambooHRClient bambooHRClient;

    public DeskAssignmentTemplateService(DeskRepository deskRepository,
                                         DeskAgentService deskAgentService,
                                         AgentEligibilityService agentEligibilityService,
                                         ShiftTemplateRepository shiftTemplateRepository,
                                         BambooHRClient bambooHRClient) {
        this.deskRepository = deskRepository;
        this.deskAgentService = deskAgentService;
        this.agentEligibilityService = agentEligibilityService;
        this.shiftTemplateRepository = shiftTemplateRepository;
        this.bambooHRClient = bambooHRClient;
    }

    /**
     * What to write into the seven day-hour columns.
     *
     * <p>{@code null} anywhere a {@code WeekDayFill} is expected means "leave every day cell
     * blank". That was the only behaviour before this type existed, and it is the shape the upload
     * parser skips row-by-row: every day cell is REQUIRED, so one blank cell loses the whole person
     * — after {@code clearDesk} has already run. Populating the cells is what makes a downloaded
     * template re-uploadable without hand-editing.
     *
     * @param offByBamboohrId approved time off only, keyed BambooHR id then day of week
     * @param workingDayHours written into every day with no approved time off
     */
    record WeekDayFill(Map<String, Map<DayOfWeek, DayOffType>> offByBamboohrId,
                       BigDecimal workingDayHours) {}

    /**
     * Resolves one week of BambooHR time off for the given people into day-off words.
     *
     * <p>The mapping deliberately mirrors {@code BambooRefreshService}'s, so a template agrees with
     * what a BambooHR refresh would have written: {@code holiday} becomes {@code MANDATORY},
     * everything else {@code PTO}, and overlapping entries for one person-day resolve MANDATORY
     * over PTO.
     *
     * <p><b>Approved time off only.</b> {@code listTimeOff} returns {@code requested} entries
     * alongside {@code approved} ones (see {@code HttpBambooHRClient}), and a pending request is not
     * leave yet. Writing one as {@code PTO} here would have the upload commit it to
     * {@code agent_day_hours} — silently granting leave nobody approved. Pending days are therefore
     * left as ordinary working days; the caller surfaces them separately so the operator can decide
     * person by person.
     *
     * <p>Filtering by the selected ids rather than by department is what lets this work for a
     * selection assembled from several department searches, which is the whole point of the basket.
     */
    private WeekDayFill resolveWeekFill(long tenantId, Set<String> bamboohrIds,
                                        LocalDate weekStart, BigDecimal workingDayHours) {
        LocalDate monday = weekStart.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        Map<String, Map<DayOfWeek, DayOffType>> off = new HashMap<>();
        if (!bamboohrIds.isEmpty()) {
            for (BambooTimeOff t : bambooHRClient.listTimeOff(String.valueOf(tenantId), monday, sunday)) {
                if (t.employeeId() == null || !bamboohrIds.contains(t.employeeId())) continue;
                if (!"approved".equalsIgnoreCase(t.status())) continue;
                if (t.date() == null || t.date().isBefore(monday) || t.date().isAfter(sunday)) continue;

                DayOffType type = "holiday".equalsIgnoreCase(t.type())
                        ? DayOffType.MANDATORY : DayOffType.PTO;
                Map<DayOfWeek, DayOffType> byDay =
                        off.computeIfAbsent(t.employeeId(), k -> new EnumMap<>(DayOfWeek.class));
                // MANDATORY beats PTO for the same person-day, as in BambooRefreshService.
                byDay.merge(t.date().getDayOfWeek(), type,
                        (a, b) -> a == DayOffType.MANDATORY || b == DayOffType.MANDATORY
                                ? DayOffType.MANDATORY : DayOffType.PTO);
            }
        }
        return new WeekDayFill(off, workingDayHours);
    }

    /**
     * Writes the seven day-hour cells for one person: the day-off word where they have approved time
     * off that week, otherwise the chosen working-day hours. A {@code null} fill writes nothing,
     * leaving the cells blank.
     */
    private void writeDayHourCells(Row row, String bamboohrId, WeekDayFill fill) {
        if (fill == null) {
            return;
        }
        int base = EnrichedColumnLayout.identityHeaders().size();
        Map<DayOfWeek, DayOffType> off =
                fill.offByBamboohrId().getOrDefault(bamboohrId, Map.of());
        for (int i = 0; i < EnrichedColumnLayout.DAY_ORDER.length; i++) {
            DayOfWeek day = EnrichedColumnLayout.DAY_ORDER[i];
            Cell cell = row.createCell(base + i);
            DayOffType type = off.get(day);
            if (type != null) {
                // DayOffType.name() is exactly the word parseDayCell accepts.
                cell.setCellValue(type.name());
            } else {
                cell.setCellValue(fill.workingDayHours().doubleValue());
            }
        }
    }

    /** The desk default when the caller named no working-day hours; rejects a value outside 0-24. */
    private BigDecimal resolveWorkingDayHours(BigDecimal requested, Desk desk) {
        BigDecimal hours = requested != null ? requested : desk.getDefaultContractedHoursPerDay();
        if (hours == null) {
            throw new IllegalArgumentException(
                    "No working-day hours given and desk '" + desk.getName()
                            + "' has no default contracted hours per day");
        }
        if (hours.signum() < 0 || hours.compareTo(new BigDecimal("24")) > 0) {
            throw new IllegalArgumentException(
                    "Working-day hours must be between 0 and 24, but was " + hours.toPlainString());
        }
        return hours;
    }

    /**
     * One workbook holding one sheet per desk. Retained because it is the multi-desk shape the
     * template tests pin; the REST endpoint no longer serves it. A single file spanning every desk
     * is a hazard on re-upload — the parser clears EVERY desk it finds a matching sheet for — so
     * {@link #generateTemplateForDesk(UUID)} is what an operator downloads.
     */
    public byte[] generateTemplate() {
        long tenantId = TenantContext.getTenantId();
        List<Desk> desks = deskRepository.findByTenantId(tenantId);
        List<String> headers = buildHeaders();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            for (Desk desk : desks) {
                writeDeskSheet(workbook, headerStyle, headers, desk, tenantId, null);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate desk assignment template", e);
        }
    }

    /**
     * A workbook for ONE desk, seeded with that desk's current roster — the shape the Client
     * Management page downloads.
     *
     * <p>One desk per file, deliberately. The upload parser walks every sheet in the workbook and
     * calls {@code clearDesk} for each one whose name matches a desk, so a file spanning all desks
     * rewrites all of them on re-upload. A single-sheet file can only ever affect the desk the
     * operator actually chose, and the sheet name still carries the desk identity the parser maps
     * on, so this file uploads through the existing path unchanged.
     */
    public byte[] generateTemplateForDesk(UUID deskId) {
        return generateTemplateForDesk(deskId, null, null);
    }

    /**
     * As above, with the seven day-hour columns pre-populated for one week: approved BambooHR time
     * off as {@code PTO}/{@code MANDATORY}, every other day the chosen working-day hours.
     *
     * <p>A {@code null} {@code weekStart} leaves the day cells blank, which is the shape the upload
     * parser skips row-by-row after having cleared the desk. Populating them is what makes a
     * download-edit-reupload round trip work at all.
     */
    public byte[] generateTemplateForDesk(UUID deskId, LocalDate weekStart, BigDecimal workingDayHours) {
        long tenantId = TenantContext.getTenantId();
        Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));
        List<String> headers = buildHeaders();

        WeekDayFill fill = null;
        if (weekStart != null) {
            Set<String> ids = deskAgentService
                    .listDeskAgentResponses(desk.getId(), null, null, Integer.MAX_VALUE).stream()
                    .filter(a -> isSeedable(tenantId, a))
                    .map(DeskAgentResponse::bamboohrId)
                    .filter(id -> id != null && !id.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toSet());
            fill = resolveWeekFill(tenantId, ids, weekStart, resolveWorkingDayHours(workingDayHours, desk));
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            writeDeskSheet(workbook, createHeaderStyle(workbook), headers, desk, tenantId, fill);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate desk assignment template", e);
        }
    }

    /**
     * {@code desk-assignment-<desk name>.xlsx}, with every run of non-alphanumerics reduced to a
     * single hyphen. Derived from the STORED desk name, never from caller input, and free of
     * quotes, semicolons, CR and LF so it cannot break out of a Content-Disposition header.
     */
    public String templateFilenameForDesk(UUID deskId) {
        Desk desk = deskRepository.findByIdAndTenantId(deskId, TenantContext.getTenantId())
                .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));
        String slug = desk.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return "desk-assignment-" + (slug.isEmpty() ? "desk" : slug) + ".xlsx";
    }

    /**
     * The single implementation of "write this desk's roster as a sheet", shared by the per-desk
     * download and the multi-desk form so the two can never drift in header set, seeding rule or
     * usual-shift pre-fill.
     */
    private void writeDeskSheet(XSSFWorkbook workbook, CellStyle headerStyle, List<String> headers,
                                Desk desk, long tenantId, WeekDayFill fill) {
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
            writeDayHourCells(row, agent.bamboohrId() == null ? null : agent.bamboohrId().trim(), fill);
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

    /** Rows past this are a mistake, not a roster: the largest live desk holds under 300 people. */
    static final int MAX_SELECTION_ROWS = 5_000;

    /**
     * A workbook for ONE desk, seeded with an explicitly chosen set of people rather than with the
     * desk's current roster. This is what the Client Management page's staged selection downloads,
     * and it is how a desk gets built from several department searches at once: the people need not
     * be on the desk yet, or on any desk.
     *
     * <p>A single sheet, named after the desk, because the upload parser reads a sheet name as a
     * desk name. Day-hour cells are left blank exactly as {@link #generateTemplate()} leaves them.
     *
     * <p><b>Ineligible people are refused, not written and not silently dropped.</b> Only active
     * employees whose job title passes the tenant's allowlist may appear — the same rule the search
     * that produced the selection applies, so in normal use this rejection never fires. It exists
     * for a selection gone stale in the browser: someone who has since left, or a title that
     * stopped matching after the allowlist changed. Writing those rows would produce a workbook the
     * upload silently skips; dropping them quietly would lose an operator's pick without telling
     * them. The exception names every offender and why.
     *
     * <p>Usual-shift cells are pre-filled only for people already on this desk, from the same
     * stored value {@link #generateTemplate()} uses. For everyone else they are blank, which the
     * parser reads as "no usual shift" — correct for someone who has never had one.
     */
    public byte[] generateTemplateForSelection(DeskAssignmentSelectionRequest request) {
        long tenantId = TenantContext.getTenantId();

        if (request.employees() == null || request.employees().isEmpty()) {
            throw new IllegalArgumentException("Select at least one employee for the template");
        }
        if (request.employees().size() > MAX_SELECTION_ROWS) {
            throw new IllegalArgumentException("A template cannot hold more than "
                    + MAX_SELECTION_ROWS + " people, but " + request.employees().size()
                    + " were selected");
        }
        if (request.deskId() == null) {
            throw new IllegalArgumentException("Choose the desk this template is for");
        }

        Desk desk = deskRepository.findByIdAndTenantId(request.deskId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Desk", request.deskId()));

        // Whoever is already on the desk, so their stored usual shifts survive a re-upload. Keyed
        // by BambooHR id, the same field the selection carries and the parser matches on.
        Map<String, DeskAgentResponse> existing = new HashMap<>();
        for (DeskAgentResponse agent : deskAgentService.listDeskAgentResponses(
                desk.getId(), null, null, Integer.MAX_VALUE)) {
            if (agent.bamboohrId() != null) {
                existing.putIfAbsent(agent.bamboohrId().trim(), agent);
            }
        }

        // Checked before a single cell is written, and reported in one exception rather than one
        // at a time: an operator fixing a stale selection wants the whole list, not a queue.
        List<String> ineligible = new ArrayList<>();
        for (DeskAssignmentSelectionRequest.Employee employee : request.employees()) {
            String who = employee.displayName() == null || employee.displayName().isBlank()
                    ? "id " + employee.bamboohrId() : employee.displayName();
            if (!"Active".equalsIgnoreCase(employee.status())) {
                ineligible.add(who + " is not active in BambooHR");
            } else if (!agentEligibilityService.isIncludedByTitleAllowlist(
                    tenantId, employee.jobTitle())) {
                ineligible.add(who + " has job title \"" + employee.jobTitle()
                        + "\", which is not on this tenant's allowlist");
            }
        }
        if (!ineligible.isEmpty()) {
            throw new UnprocessableException(
                    ineligible.size() + " of " + request.employees().size()
                            + " selected people cannot be scheduled, so no template was built",
                    ineligible);
        }

        List<String> headers = buildHeaders();

        WeekDayFill fill = null;
        if (request.weekStart() != null) {
            Set<String> ids = request.employees().stream()
                    .map(DeskAssignmentSelectionRequest.Employee::bamboohrId)
                    .filter(id -> id != null && !id.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toSet());
            fill = resolveWeekFill(tenantId, ids, request.weekStart(),
                    resolveWorkingDayHours(request.workingDayHours(), desk));
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            Sheet sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(desk.getName()));

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // First occurrence wins: the same person reached through two department searches is one
            // row, not two. Two rows with one id would have the parser apply the second over the
            // first, which is a silent way to lose whatever the operator typed on the first.
            Set<String> seenIds = new LinkedHashSet<>();
            int rowNum = 1;
            for (DeskAssignmentSelectionRequest.Employee employee : request.employees()) {
                String id = employee.bamboohrId() == null ? null : employee.bamboohrId().trim();
                if (id == null || id.isBlank() || !seenIds.add(id)) {
                    continue;
                }
                AgentNameSplitter.Split name = AgentNameSplitter.split(employee.displayName());

                Row row = sheet.createRow(rowNum++);
                writeSanitized(row, 0, id);
                writeSanitized(row, 1, name.firstName());
                writeSanitized(row, 2, name.lastName());
                writeSanitized(row, 3, employee.jobTitle());
                writeSanitized(row, 4, employee.workEmail());
                writeSanitized(row, 5, employee.department());
                writeSanitized(row, 6, "Active".equalsIgnoreCase(employee.status()) ? "Yes" : "No");
                writeDayHourCells(row, id, fill);

                DeskAgentResponse onDesk = existing.get(id);
                if (onDesk != null) {
                    writeUsualShiftCells(row, onDesk);
                }
            }

            attachUsualShiftDropdown(sheet, tenantId, desk);
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
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
