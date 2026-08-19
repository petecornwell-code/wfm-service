package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.MergeReportEntry;
import com.wfm.dto.SkippedRow;
import com.wfm.dto.SkippedSheet;
import com.wfm.integration.AgentMergeService;
import com.wfm.integration.BambooEmployee;
import com.wfm.model.Agent;
import com.wfm.model.AgentDayHours;
import com.wfm.model.DayOffType;
import com.wfm.model.Desk;
import com.wfm.model.Specialization;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentExceptionRepository;
import com.wfm.repository.AgentPreferenceRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.util.AgentNameSplitter;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.WorkbookUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Parses an operator-uploaded workbook where each worksheet is one desk (D-01) and
 * every row is one agent's identity + unbounded specialties + 7-column Mon-Sun day
 * cells. Header layout is driven entirely by {@link EnrichedColumnLayout} — no
 * enriched-shape header string literal is hardcoded in this class (D-13).
 *
 * Both the 6-col legacy shape and the old flat single-sheet enriched shape (per-row
 * "Desk" column) are retired and rejected with a "download the new template"
 * message (D-15).
 */
@Service
public class DeskAssignmentUploadService {

    private static final Logger log = LoggerFactory.getLogger(DeskAssignmentUploadService.class);

    private final AgentRepository agentRepository;
    private final DeskRepository deskRepository;
    private final ClientManagementService clientManagementService;
    private final AgentPreferenceRepository agentPreferenceRepository;
    private final AgentExceptionRepository agentExceptionRepository;
    private final AgentDayHoursRepository agentDayHoursRepository;
    private final SpecializationRepository specializationRepository;
    private final AgentEligibilityService agentEligibilityService;
    private final AgentMergeService agentMergeService;
    private final TransactionTemplate transactionTemplate;

    public DeskAssignmentUploadService(AgentRepository agentRepository,
                                        DeskRepository deskRepository,
                                        ClientManagementService clientManagementService,
                                        AgentPreferenceRepository agentPreferenceRepository,
                                        AgentExceptionRepository agentExceptionRepository,
                                        AgentDayHoursRepository agentDayHoursRepository,
                                        SpecializationRepository specializationRepository,
                                        AgentEligibilityService agentEligibilityService,
                                        AgentMergeService agentMergeService,
                                        TransactionTemplate transactionTemplate) {
        this.agentRepository = agentRepository;
        this.deskRepository = deskRepository;
        this.clientManagementService = clientManagementService;
        this.agentPreferenceRepository = agentPreferenceRepository;
        this.agentExceptionRepository = agentExceptionRepository;
        this.agentDayHoursRepository = agentDayHoursRepository;
        this.specializationRepository = specializationRepository;
        this.agentEligibilityService = agentEligibilityService;
        this.agentMergeService = agentMergeService;
        this.transactionTemplate = transactionTemplate;
    }

    public DeskAssignmentUploadResult uploadDeskAssignments(MultipartFile file) throws IOException {
        long tenantId = TenantContext.getTenantId();

        // Fresh BambooHR snapshot BEFORE any transaction opens (D-01/D-02/D-04) -- one
        // listEmployees + one listTimeOff call serves the whole workbook, unconditionally,
        // regardless of whether ClientManagementService's cache is already warm.
        AgentMergeService.BambooSnapshot snapshot = agentMergeService.fetchSnapshot(tenantId);

        List<String> assigned = new ArrayList<>();
        List<SkippedRow> skipped = new ArrayList<>();
        List<SkippedSheet> skippedSheets = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<SheetSummary> sheetSummaries = new ArrayList<>();
        List<MergeReportEntry> mergeReport = new ArrayList<>();

        // Pre-load all desks for this tenant keyed by name (case-insensitive). Also keyed by
        // the Excel-safe sheet name WorkbookUtil.createSafeSheetName(...) would produce for
        // this desk (DeskAssignmentTemplateService uses the same transformation when it names
        // the template sheet) so a desk whose name was sanitized/truncated in the template
        // still resolves back to the same desk on re-upload (CR-03 — preserves D-14's
        // round-trip guarantee for long or Excel-invalid-character desk names). Raw-name keys
        // are populated first so they take priority over any safe-name collision.
        List<Desk> allDesks = deskRepository.findByTenantId(tenantId);
        Map<String, Desk> deskByName = new HashMap<>();
        for (Desk d : allDesks) {
            deskByName.put(d.getName().toLowerCase(), d);
        }
        for (Desk d : allDesks) {
            deskByName.putIfAbsent(WorkbookUtil.createSafeSheetName(d.getName()).toLowerCase(), d);
        }

        // Pre-load specializations per desk: deskId -> (lowercased name -> Specialization)
        Map<UUID, Map<String, Specialization>> specsByDesk = new HashMap<>();
        for (Desk d : allDesks) {
            List<Specialization> specs = specializationRepository.findByTenantIdAndDeskId(tenantId, d.getId());
            Map<String, Specialization> specMap = new HashMap<>();
            for (Specialization s : specs) {
                specMap.put(s.getName().toLowerCase(), s);
            }
            specsByDesk.put(d.getId(), specMap);
        }

        Workbook workbook;
        try {
            // WorkbookFactory auto-detects OLE2 (.xls) vs OOXML (.xlsx) from the stream's
            // magic bytes, so both formats the frontend advertises (accept=".xlsx,.xls")
            // actually parse (WR-03) — new XSSFWorkbook(...) only ever supported .xlsx.
            workbook = WorkbookFactory.create(file.getInputStream());
        } catch (IOException | RuntimeException e) {
            // A stream that isn't a recognized OLE2/OOXML container, or a POI-specific
            // format issue (e.g. EncryptedDocumentException), previously propagated
            // uncaught to GlobalExceptionHandler's generic 500. Surface a clean 400
            // instead (WR-03).
            throw new IllegalArgumentException(
                    "Unable to read the uploaded file. Please upload a valid .xlsx or .xls spreadsheet.", e);
        }
        try (workbook) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("Spreadsheet has no sheets");
            }

            // --- Header-based shape classification, against the FIRST sheet only (D-15
            //     rejects file-wide — a legacy/old-enriched file has no per-desk sheets to
            //     iterate in the first place). Driven entirely by EnrichedColumnLayout
            //     constants + normalize() — no header string literal hardcoded here (D-13).
            Sheet classificationSheet = workbook.getSheetAt(0);
            Row classificationHeaderRow = classificationSheet.getRow(0);
            if (classificationHeaderRow == null) {
                throw new IllegalArgumentException("Spreadsheet has no header row");
            }

            Set<String> normalizedHeaders = new LinkedHashSet<>();
            for (int c = 0; c < classificationHeaderRow.getLastCellNum(); c++) {
                String hdr = getCellString(classificationHeaderRow.getCell(c));
                if (hdr != null && !hdr.isBlank()) {
                    normalizedHeaders.add(EnrichedColumnLayout.normalize(hdr));
                }
            }

            String normDesk = EnrichedColumnLayout.normalize(EnrichedColumnLayout.RETIRED_COL_DESK);
            String normLegacyDeskAssignment =
                    EnrichedColumnLayout.normalize(EnrichedColumnLayout.LEGACY_HEADER_DESK_ASSIGNMENT);
            String normMonday = EnrichedColumnLayout.normalize(EnrichedColumnLayout.dayHeader(DayOfWeek.MONDAY));
            String normSunday = EnrichedColumnLayout.normalize(EnrichedColumnLayout.dayHeader(DayOfWeek.SUNDAY));
            String normBamboohrId = EnrichedColumnLayout.normalize(EnrichedColumnLayout.COL_BAMBOOHR_ID);

            boolean isLegacy6Col = normalizedHeaders.contains(normLegacyDeskAssignment);
            boolean isOldFlatEnriched = normalizedHeaders.contains(normDesk)
                    && normalizedHeaders.contains(normMonday)
                    && normalizedHeaders.contains(normSunday);

            if (isLegacy6Col || isOldFlatEnriched) {
                throw new IllegalArgumentException(
                        "This spreadsheet uses a retired format. Please download the new template "
                        + "(one worksheet per desk) and re-enter your data.");
            }

            boolean isNewPerDeskShape = normalizedHeaders.contains(normBamboohrId)
                    && normalizedHeaders.contains(normMonday)
                    && normalizedHeaders.contains(normSunday)
                    && !normalizedHeaders.contains(normDesk);

            if (!isNewPerDeskShape) {
                throw new IllegalArgumentException(
                        "Unrecognised spreadsheet shape. Expected the per-desk enriched template "
                        + "(BambooHR ID + Monday..Sunday columns, no Desk column). Got headers: "
                        + normalizedHeaders);
            }

            // Cheap pre-scan of sheet names -> desk IDs matched anywhere in this workbook
            // (WR-04). Used below to give the "agent already assigned to a different desk"
            // skip a specific message when the conflicting desk is ALSO being re-imported
            // in this same upload — that outcome depends on which sheet happens to be
            // processed first (an intra-workbook desk move), which is otherwise a silent,
            // hard-to-diagnose gotcha for the operator.
            Set<UUID> deskIdsInThisWorkbook = new HashSet<>();
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Desk matched = deskByName.get(workbook.getSheetAt(s).getSheetName().trim().toLowerCase());
                if (matched != null) {
                    deskIdsInThisWorkbook.add(matched.getId());
                }
            }

            // Every write for the whole workbook runs inside this single transaction (D-02);
            // per-row/per-sheet parse failures still skip-and-continue exactly as before --
            // only a sync failure (already thrown above, before this point) aborts everything.
            transactionTemplate.executeWithoutResult(status -> {
            // --- Iterate every sheet: sheet name = desk, no per-row Desk column (D-01) ---
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName().trim();
                Desk desk = deskByName.get(sheetName.toLowerCase());
                if (desk == null) {
                    skippedSheets.add(new SkippedSheet(sheetName, "no matching desk — skipped"));
                    continue;
                }

                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    skippedSheets.add(new SkippedSheet(sheetName, "no header row — skipped"));
                    continue;
                }

                // Build map: normalized (trim+lowercase) header -> column index
                Map<String, Integer> col = new LinkedHashMap<>();
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    String hdr = getCellString(headerRow.getCell(c));
                    if (hdr != null && !hdr.isBlank()) {
                        col.put(EnrichedColumnLayout.normalize(hdr), c);
                    }
                }

                // Validate the sheet's OWN header set before clearing the desk (CR-01). The
                // file-wide shape classification above only inspects sheet 0's headers; an
                // individual sheet can still have a typo'd/renamed/missing required header.
                // If so, skip the sheet with a specific notice and do NOT clearDesk — CR-01
                // demonstrated that clearing first and then having every row fail to
                // re-import silently empties the desk's roster with zero replacements.
                List<String> missingHeaders = new ArrayList<>();
                if (!col.containsKey(normBamboohrId)) {
                    missingHeaders.add(EnrichedColumnLayout.COL_BAMBOOHR_ID);
                }
                for (DayOfWeek d : EnrichedColumnLayout.DAY_ORDER) {
                    if (!col.containsKey(EnrichedColumnLayout.normalize(EnrichedColumnLayout.dayHeader(d)))) {
                        missingHeaders.add(EnrichedColumnLayout.dayHeader(d));
                    }
                }
                if (!missingHeaders.isEmpty()) {
                    skippedSheets.add(new SkippedSheet(sheetName,
                            "missing required column(s): " + String.join(", ", missingHeaders) + " — skipped"));
                    continue; // desk is left untouched — no clearDesk
                }

                // Unbounded "Specialty N" header scan (D-06) — ordered by N, first
                // non-blank value becomes primary, the rest secondary.
                List<Integer> specialtyColIndices = col.entrySet().stream()
                        .filter(e -> EnrichedColumnLayout.specialtyIndex(e.getKey()).isPresent())
                        .sorted(Comparator.comparingInt(e -> EnrichedColumnLayout.specialtyIndex(e.getKey()).get()))
                        .map(Map.Entry::getValue)
                        .toList();

                // Clear-then-reimport (D-17): unassign agents, delete desk-scoped
                // preferences/exceptions/per-day-hours before re-adding the rows present.
                clearDesk(tenantId, desk.getId());

                Map<String, Specialization> deskSpecs = specsByDesk.getOrDefault(desk.getId(), Map.of());

                int sheetImportedCount = 0;
                int sheetSkippedCount = 0;

                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    String bamboohrId = cellAt(row, col, normBamboohrId);
                    String firstName = cellAt(row, col,
                            EnrichedColumnLayout.normalize(EnrichedColumnLayout.COL_FIRST_NAME));
                    String lastName = cellAt(row, col,
                            EnrichedColumnLayout.normalize(EnrichedColumnLayout.COL_LAST_NAME));
                    String jobTitle = cellAt(row, col,
                            EnrichedColumnLayout.normalize(EnrichedColumnLayout.COL_JOB_TITLE));
                    String email = cellAt(row, col,
                            EnrichedColumnLayout.normalize(EnrichedColumnLayout.COL_EMAIL));
                    String department = cellAt(row, col,
                            EnrichedColumnLayout.normalize(EnrichedColumnLayout.COL_DEPARTMENT));
                    String activeStr = cellAt(row, col,
                            EnrichedColumnLayout.normalize(EnrichedColumnLayout.COL_ACTIVE));
                    String rowName = joinName(firstName, lastName);

                    // Parse unbounded "Specialty N" columns (D-06) — first non-blank = primary,
                    // rest = secondary.
                    List<String> specialtyNames = new ArrayList<>();
                    for (int specColIdx : specialtyColIndices) {
                        String val = getCellString(row.getCell(specColIdx));
                        if (val != null && !val.isBlank()) specialtyNames.add(val.trim());
                    }

                    if (bamboohrId == null || bamboohrId.isBlank()) {
                        skipped.add(new SkippedRow(i + 1, bamboohrId, rowName, "BambooHR ID missing"));
                        sheetSkippedCount++;
                        continue;
                    }

                    // Parse all 7 Mon-Sun day cells (D-03/D-04/D-05/D-10). Every day cell is
                    // required — blank, negative, or an unrecognized word skips the whole row,
                    // each with its own specific reason (WR-01), not one generic message.
                    Map<DayOfWeek, DayCellResult> dayResults = new EnumMap<>(DayOfWeek.class);
                    String daySkipReason = null;
                    for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
                        DayCellOutcome outcome = parseDayCell(row, col, day);
                        if (outcome.failed()) {
                            daySkipReason = "Row " + (i + 1) + " (id " + bamboohrId.trim() + "): "
                                    + EnrichedColumnLayout.dayHeader(day) + " cell " + outcome.failureReason();
                            break;
                        }
                        dayResults.put(day, outcome.result());
                    }
                    if (daySkipReason != null) {
                        skipped.add(new SkippedRow(i + 1, bamboohrId, rowName, daySkipReason));
                        sheetSkippedCount++;
                        continue;
                    }

                    // Resolve specialty names against desk specializations
                    List<Specialization> resolvedSpecialties = new ArrayList<>();
                    boolean specialtyError = false;
                    for (String specName : specialtyNames) {
                        Specialization spec = deskSpecs.get(specName.toLowerCase());
                        if (spec == null) {
                            skipped.add(new SkippedRow(i + 1, bamboohrId, rowName,
                                    "Specialty '" + specName + "' not found on desk '" + desk.getName() + "'"));
                            specialtyError = true;
                            break;
                        }
                        resolvedSpecialties.add(spec);
                    }
                    if (specialtyError) {
                        sheetSkippedCount++;
                        continue;
                    }

                    // Match by BambooHR ID only (D-08) against the fresh whole-tenant snapshot
                    // fetched before this transaction opened — a row whose ID is unknown to
                    // BambooHR is rejected; no agent is created (D-07/UPL-07).
                    String trimmedBamboohrId = bamboohrId.trim();
                    BambooEmployee employee = snapshot.employeesById().get(trimmedBamboohrId);
                    if (employee == null) {
                        // The snapshot retains every employee regardless of status, so a miss
                        // here means the id genuinely doesn't exist in BambooHR (fix the cell),
                        // distinct from the inactive-employee case handled just below. Reporting
                        // both as "ID not found" sent operators hunting for a typo that did not
                        // exist (UAT 2026-08-12).
                        skipped.add(new SkippedRow(i + 1, bamboohrId, rowName, "BambooHR ID not found"));
                        sheetSkippedCount++;
                        continue;
                    }
                    if (!"Active".equalsIgnoreCase(employee.status())) {
                        skipped.add(new SkippedRow(i + 1, bamboohrId, rowName,
                                "Agent is not active in BambooHR (status: " + employee.status() + ")"));
                        sheetSkippedCount++;
                        continue;
                    }

                    Agent agent = agentRepository.findByTenantIdAndBamboohrId(tenantId, trimmedBamboohrId)
                            .orElse(null);
                    if (agent == null) {
                        agent = new Agent();
                        agent.setTenantId(tenantId);
                        agent.setBamboohrId(trimmedBamboohrId);
                        agent.setLastRefreshedAt(OffsetDateTime.now());
                    }

                    // Re-activate: the agent is Active in the fresh BambooHR snapshot, so mark
                    // active even if a prior refresh soft-deleted them
                    agent.setActive("Active".equalsIgnoreCase(employee.status()));

                    // Backfill missing identity fields from the fresh BambooHR snapshot
                    if (isBlank(agent.getDepartment())) agent.setDepartment(employee.department());
                    if (isBlank(agent.getJobTitle())) agent.setJobTitle(employee.jobTitle());
                    if (isBlank(agent.getFirstName()) || isBlank(agent.getLastName())) {
                        AgentNameSplitter.Split cachedSplit = AgentNameSplitter.split(employee.displayName());
                        if (isBlank(agent.getFirstName())) agent.setFirstName(cachedSplit.firstName());
                        if (isBlank(agent.getLastName())) agent.setLastName(cachedSplit.lastName());
                    }
                    if (isBlank(agent.getName())) agent.setName(employee.displayName());

                    // Spreadsheet-supplied identity fields are optional and override the
                    // snapshot when present (D-07) — EXCEPT Email, which now merges BambooHR
                    // first (MRG-02); Task 2 converts the remaining contested fields the same way.
                    if (!isBlank(firstName)) agent.setFirstName(firstName.trim());
                    if (!isBlank(lastName)) agent.setLastName(lastName.trim());
                    if (!isBlank(jobTitle)) agent.setJobTitle(jobTitle.trim());
                    agent.setEmail(agentMergeService.mergeIdentityFields(
                            employee.workEmail(), email, "Email", trimmedBamboohrId, rowName, mergeReport));
                    if (!isBlank(department)) agent.setDepartment(department.trim());
                    if (!isBlank(activeStr)) agent.setActive(parseActive(activeStr));
                    if (!isBlank(agent.getFirstName()) || !isBlank(agent.getLastName())) {
                        agent.setName(joinName(agent.getFirstName(), agent.getLastName()));
                    }
                    agent.setLastRefreshedAt(OffsetDateTime.now());

                    // Check if already assigned to a different desk. If that other desk is
                    // ALSO matched by a sheet in this same workbook, the outcome of this
                    // "move" depends on sheet processing order (WR-04) — surface a specific,
                    // actionable message instead of the generic conflict reason so the
                    // operator understands why and can fix it (e.g. reorder sheets, or
                    // re-run the upload once the source desk's sheet has cleared the agent).
                    if (agent.getDeskId() != null && !agent.getDeskId().equals(desk.getId())) {
                        String reason = deskIdsInThisWorkbook.contains(agent.getDeskId())
                                ? "Agent " + agent.getName() + " is being moved between desks in this "
                                        + "workbook (currently on desk " + agent.getDeskId() + "); this "
                                        + "row's outcome depends on sheet order — if the agent ends up "
                                        + "unassigned, re-run the upload"
                                : "Agent already assigned to desk " + agent.getDeskId();
                        skipped.add(new SkippedRow(i + 1, agent.getBamboohrId(), agent.getName(), reason));
                        sheetSkippedCount++;
                        continue;
                    }

                    // Inactive check — agent.active was refreshed from the BambooHR cache above,
                    // so this reflects current BambooHR status rather than what the sheet claims.
                    if (!agent.isActive()) {
                        skipped.add(new SkippedRow(i + 1, agent.getBamboohrId(), agent.getName(),
                                "Agent is not active"));
                        sheetSkippedCount++;
                        continue;
                    }

                    // Job-title allowlist — the single control for schedulability, shared with the
                    // solver and the template generator. Inactive (a no-op) only when the tenant
                    // has no patterns configured at all.
                    if (!agentEligibilityService.isIncludedByTitleAllowlist(tenantId, agent.getJobTitle())) {
                        skipped.add(new SkippedRow(i + 1, agent.getBamboohrId(), agent.getName(),
                                "Agent job title is not schedulable: " + agent.getJobTitle()));
                        sheetSkippedCount++;
                        continue;
                    }

                    agent.setDeskId(desk.getId());

                    // Assign specializations — modify the existing JPA-managed collection
                    // in place (clear + addAll) rather than replacing the reference, so
                    // Hibernate properly tracks changes to the join table.
                    if (resolvedSpecialties.isEmpty()) {
                        agent.setPrimarySpecialization(null);
                    } else {
                        agent.setPrimarySpecialization(resolvedSpecialties.get(0));
                    }
                    agent.getSecondarySpecializations().clear();
                    if (resolvedSpecialties.size() > 1) {
                        agent.getSecondarySpecializations().addAll(
                                resolvedSpecialties.subList(1, resolvedSpecialties.size()));
                    }

                    // Reaching here means all 7 Mon-Sun cells parsed (any failure skips the row
                    // above), so this upload has explicitly stated the agent's working days. Mark
                    // them known.
                    //
                    // Without this, workingDaysKnown stayed false for any agent whose BambooHR
                    // field 4517 is blank or "Variable", and SolverService.filterEligible silently
                    // dropped them — even though the operator had just supplied their hours for
                    // every day of the week. On a live desk that left 6 of 14 agents schedulable
                    // and made the enriched upload pointless for the rest (found in UAT 2026-08-12;
                    // the ~24%-parseable risk recorded in PROJECT.md).
                    agent.setWorkingDaysKnown(true);

                    agentRepository.save(agent);

                    // Write one agent_day_hours row per weekday (D-05/D-12): hours + nullable
                    // dayOffType label. This is a union with BambooHR field-4517 MANDATORY
                    // blocks (D-16) — clamp warnings are surfaced, never silently logged only
                    // (D-10/D-11 — Pitfall 3).
                    for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
                        DayCellResult dayResult = dayResults.get(day);
                        AgentDayHours agentDayHours = new AgentDayHours();
                        agentDayHours.setTenantId(tenantId);
                        agentDayHours.setAgent(agent);
                        agentDayHours.setDayOfWeek(day);
                        agentDayHours.setHours(dayResult.hours());
                        agentDayHours.setDayOffType(dayResult.type());
                        agentDayHoursRepository.save(agentDayHours);
                        if (dayResult.clampWarning() != null) {
                            warnings.add("Row " + (i + 1) + " (id " + bamboohrId.trim() + ") "
                                    + dayResult.clampWarning());
                        }
                    }

                    assigned.add("Row " + (i + 1) + ": " + agent.getName() + " -> " + desk.getName());
                    sheetImportedCount++;
                }

                sheetSummaries.add(new SheetSummary(desk.getName(), sheetImportedCount, sheetSkippedCount));
            }
            });
        }

        return new DeskAssignmentUploadResult(
                assigned.size(), skipped.size(), assigned, skipped,
                sheetSummaries, warnings, skippedSheets, mergeReport);
    }

    private void clearDesk(long tenantId, UUID deskId) {
        log.info("Clearing desk {} for tenant {} before spreadsheet re-import", deskId, tenantId);
        // Remove desk-scoped data
        agentPreferenceRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
        agentExceptionRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
        // Unassign all agents from the desk
        List<Agent> deskAgents = agentRepository.findByTenantIdAndDeskId(tenantId, deskId);
        for (Agent agent : deskAgents) {
            agent.setDeskId(null);
            agent.setPrimarySpecialization(null);
            agent.getSecondarySpecializations().clear();
            agent.setContractedHoursPerDay(null);
            agentDayHoursRepository.deleteByAgent_Id(agent.getId());
            agentRepository.save(agent);
        }
    }

    /**
     * Null-safe header-indexed cell read. Returns null when the header is absent
     * (col index -1) instead of calling row.getCell(-1), which throws
     * IllegalArgumentException and would abort the entire upload.
     */
    private String cellAt(Row row, Map<String, Integer> col, String header) {
        int idx = col.getOrDefault(header, -1);
        return idx >= 0 ? getCellString(row.getCell(idx)) : null;
    }

    /**
     * NOTE: numeric cells are truncated via (long) here — fine for identity/string
     * reads (IDs, names) but MUST NOT be used for day-cell hours parsing, which needs
     * to preserve fractional values (e.g. 7.5). Day-cell parsing reads
     * cell.getNumericCellValue() directly instead (see parseDayCell, Task 2).
     */
    private String getCellString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                yield String.valueOf((long) cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String joinName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String combined = (first + " " + last).trim();
        return combined.isBlank() ? null : combined;
    }

    private static boolean parseActive(String value) {
        String v = value.trim();
        return "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v)
                || "active".equalsIgnoreCase(v) || "1".equals(v);
    }

    public record DeskAssignmentUploadResult(
            int assignedCount,
            int skippedCount,
            List<String> assignedDetails,
            List<SkippedRow> skippedDetails,
            List<SheetSummary> sheetSummaries,
            List<String> warnings,
            List<SkippedSheet> skippedSheets,
            List<MergeReportEntry> mergeReport
    ) {}

    /** Per-sheet (per-desk) import rollup (D-11). */
    public record SheetSummary(String deskName, int importedCount, int skippedCount) {}

    /**
     * Result of parsing a single Mon-Sun day cell (D-03/D-04/D-05/D-10).
     * {@code type} is null for a plain numeric hours value (including 0); non-null
     * ({@code MANDATORY}/{@code PTO}) for the two off-day keywords. {@code clampWarning}
     * is non-null only when a value > 24 was clamped to 24.00 — surfaced (not just
     * logged) per Pitfall 3.
     */
    private record DayCellResult(BigDecimal hours, DayOffType type, String clampWarning) {}

    /**
     * Outcome of {@link #parseDayCell}. Exactly one of {@code result}/{@code failureReason}
     * is non-null. {@code failureReason} is a specific, distinct phrase for blank / negative /
     * unrecognized-word cells (WR-01/D-04/D-10 want a "specific reason", not one generic
     * message for all three cases).
     */
    private record DayCellOutcome(DayCellResult result, String failureReason) {
        static DayCellOutcome ok(DayCellResult result) {
            return new DayCellOutcome(result, null);
        }

        static DayCellOutcome fail(String reason) {
            return new DayCellOutcome(null, reason);
        }

        boolean failed() {
            return result == null;
        }
    }

    /**
     * Parses one Mon-Sun day cell into hours/MANDATORY/PTO. Reads numeric cells via
     * {@code cell.getNumericCellValue()} directly — NOT through {@link #getCellString},
     * whose {@code (long)} cast truncates fractional hours (e.g. 7.5 -> "7").
     * Returns a failed {@link DayCellOutcome} with a specific reason for blank, negative, or
     * unrecognized-word cells; the caller skips the whole row in that case (D-04/WR-01).
     */
    private DayCellOutcome parseDayCell(Row row, Map<String, Integer> col, DayOfWeek day) {
        int idx = col.getOrDefault(EnrichedColumnLayout.normalize(EnrichedColumnLayout.dayHeader(day)), -1);
        Cell cell = idx >= 0 ? row.getCell(idx) : null;
        if (cell == null) return DayCellOutcome.fail("is blank"); // caller skips row (D-04)

        if (cell.getCellType() == CellType.STRING) {
            String raw = cell.getStringCellValue().trim();
            if (raw.equalsIgnoreCase("MANDATORY")) {
                return DayCellOutcome.ok(new DayCellResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        DayOffType.MANDATORY, null));
            }
            if (raw.equalsIgnoreCase("PTO")) {
                return DayCellOutcome.ok(new DayCellResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        DayOffType.PTO, null));
            }
            if (raw.isEmpty()) {
                return DayCellOutcome.fail("is blank"); // caller skips row (D-04)
            }
            // unrecognized word -> caller skips row (D-04)
            return DayCellOutcome.fail("has an unrecognized value (\"" + raw + "\")");
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            BigDecimal value = BigDecimal.valueOf(cell.getNumericCellValue()); // NOT (long) — preserves fractional hours
            if (value.signum() < 0) {
                // caller skips row (D-10)
                return DayCellOutcome.fail("is negative (" + value.toPlainString() + ")");
            }
            if (value.compareTo(new BigDecimal("24")) > 0) {
                return DayCellOutcome.ok(new DayCellResult(new BigDecimal("24.00"), null,
                        EnrichedColumnLayout.dayHeader(day) + ": " + value + " clamped to 24")); // D-10, non-silent
            }
            return DayCellOutcome.ok(new DayCellResult(value.setScale(2, RoundingMode.HALF_UP), null, null));
        }
        return DayCellOutcome.fail("is blank"); // blank/boolean/other -> caller skips row (D-04)
    }
}
