# Phase 10: Enriched Upload Parsing - Pattern Map

**Mapped:** 2026-07-31
**Files analyzed:** 11 (5 modified, 6 new)
**Analogs found:** 11 / 11

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` (rewrite) | service (parser) | file-I/O + CRUD | itself (current version) + `FteUploadService.java` (multi-sheet loop) | exact (self) / role-match (multi-sheet) |
| `src/main/java/com/wfm/util/EnrichedColumnLayout.java` (NEW) | utility | transform | header-map construction in `DeskAssignmentUploadService.java` lines 110-118 | role-match (no existing standalone layout class — closest is the inline header→index builder) |
| `src/main/java/com/wfm/service/DeskAssignmentTemplateService.java` (NEW) | service (generator) | file-I/O | `DeskAgentExportService.java` (whole file) | exact — same shape (POI workbook writer, byte[] output) |
| `src/main/java/com/wfm/service/DeskAgentExportService.java` (modify) | service (export) | file-I/O | itself (extend column-building to use `EnrichedColumnLayout`) | exact (self) |
| `src/main/java/com/wfm/model/AgentDayHours.java` (modify: + `dayOffType`) | model | CRUD | `AgentDayOff.java` (sibling entity, same `@Enumerated(EnumType.STRING)` field pattern) | exact |
| `src/main/resources/db/migration/V30__*.sql` (NEW) | migration | batch | `V29__agent_first_last_name_and_day_hours.sql` | exact |
| `src/main/java/com/wfm/dto/DeskAssignmentUploadResult` (record, currently nested in `DeskAssignmentUploadService`) (extend) | model/DTO | request-response | `FteUploadResult.java` (record with counts + detail lists + extra metadata fields) | exact |
| `src/main/java/com/wfm/dto/SkippedRow.java` (extend / add sibling `SkippedSheet`) | model/DTO | request-response | itself + `FteUploadResult`'s plain-`String` skipped-list convention | exact (self) |
| `src/main/java/com/wfm/controller/ClientManagementController.java` (add template-download endpoint) | controller | request-response | `exportEmployees()` in the same file (lines 78-92) and `DeskAgentController.exportDeskAgents()` (lines 88-97) | exact |
| `frontend/src/pages/ClientManagement.tsx` (extend Upload Results modal) | component | request-response | itself — existing Upload Results modal (lines 453-501) | exact (self) |
| `frontend/src/api/client.ts` (extend DTO types + add template download call) | utility (API client) | request-response | itself — `clientManagement.uploadDeskAssignments` / `exportEmployees` (lines 402-428) | exact (self) |
| NEW test files (`DeskAssignmentUploadMultiSheetTest`, `...SpecialtyTest`, `...DayCellTest`, `...ValidationTest`, `...RetiredShapeTest`, `DeskAssignmentTemplateServiceTest`) | test | request-response | `DeskAssignmentUploadEnrichedShapeTest.java` / `DeskAssignmentUploadLegacyShapeTest.java` (whole files) | exact |
| `src/test/java/com/wfm/integration/BambooRefreshServiceTest.java` (extend) | test | event-driven | itself | exact (self) |

## Pattern Assignments

### `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` (service, file-I/O + CRUD — rewrite in place)

**Analog A (structure/helpers to keep):** itself, current version, full file read.
**Analog B (multi-sheet loop to add):** `src/main/java/com/wfm/service/FteUploadService.java`

**Current imports** (lines 1-26) — keep this exact import block shape, add `com.wfm.util.EnrichedColumnLayout`, `com.wfm.model.DayOffType`, `java.time.DayOfWeek`, `java.math.BigDecimal`, `java.math.RoundingMode`:
```java
package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.BambooEmployeeResponse;
import com.wfm.dto.SkippedRow;
import com.wfm.model.Agent;
import com.wfm.model.Desk;
import com.wfm.model.Specialization;
import com.wfm.repository.AgentDayHoursRepository;
import com.wfm.repository.AgentExceptionRepository;
import com.wfm.repository.AgentPreferenceRepository;
import com.wfm.repository.AgentRepository;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.SpecializationRepository;
import com.wfm.util.AgentNameSplitter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
```

**Multi-sheet iteration pattern to copy** (`FteUploadService.java` lines 82-90, 142-147 — confirmed precedent, adapt `parseSheetDate` → desk lookup):
```java
for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
    Sheet sheet = workbook.getSheetAt(s);
    String sheetName = sheet.getSheetName().trim();
    // Phase 10 adaptation: replace parseSheetDate(sheetName) with
    // deskByName.get(sheetName.toLowerCase()) (mirror existing deskByName build at
    // DeskAssignmentUploadService.java lines 80-85, which is ALREADY lowercase-keyed).
    // On no match: record a SkippedSheet notice (D-02) and `continue` — do not throw.
}
```

**Header→index map pattern to keep as-is** (current lines 110-118 — this becomes the input feed for `EnrichedColumnLayout`-driven lookups instead of the two-shape branch below it):
```java
Map<String, Integer> col = new LinkedHashMap<>();
for (int c = 0; c < headerRow.getLastCellNum(); c++) {
    Cell cell = headerRow.getCell(c);
    String hdr = getCellString(cell);
    if (hdr != null && !hdr.isBlank()) {
        col.put(hdr.trim().toLowerCase(), c);
    }
}
```

**Null-safe cell read to keep unchanged** (current lines 385-388):
```java
private String cellAt(Row row, Map<String, Integer> col, String header) {
    int idx = col.getOrDefault(header, -1);
    return idx >= 0 ? getCellString(row.getCell(idx)) : null;
}
```

**`getCellString()` — MUST FIX fractional-hours truncation bug before reuse for day cells** (current lines 390-403):
```java
// BUG: (long) cell.getNumericCellValue() truncates 7.5 -> "7". Fine to keep for
// non-day-cell string reads (IDs, names), but the day-cell parser MUST call
// cell.getNumericCellValue() directly as a double/BigDecimal, never through this helper.
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
```

**Specialty N assignment logic to extend, not replace** (current lines 344-353 — first-non-blank=primary, rest=secondary; only the *upstream* detection loop changes from two hardcoded `spec1Col`/`spec2Col` lookups to an `EnrichedColumnLayout.specialtyIndex()` header scan):
```java
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
```

**`clearDesk` — keep as the D-17 removal mechanism, only needs to know the new `day_off_type` column is auto-cleared by the existing delete** (current lines 363-378):
```java
private void clearDesk(long tenantId, UUID deskId) {
    log.info("Clearing desk {} for tenant {} before spreadsheet re-import", deskId, tenantId);
    agentPreferenceRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
    agentExceptionRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
    List<Agent> deskAgents = agentRepository.findByTenantIdAndDeskId(tenantId, deskId);
    for (Agent agent : deskAgents) {
        agent.setDeskId(null);
        agent.setPrimarySpecialization(null);
        agent.getSecondarySpecializations().clear();
        agent.setContractedHoursPerDay(null);
        agentDayHoursRepository.deleteByAgent_Id(agent.getId());  // also clears day_off_type — no extra code needed
        agentRepository.save(agent);
    }
}
```

**Shape rejection pattern to extend (D-15 — both legacy shapes rejected with new message)** (current lines 120-137, structure to keep, outcome to change):
```java
Set<String> headers = col.keySet();
boolean hasEnrichedMarkers = headers.contains("desk") && headers.contains("monday") && headers.contains("sunday");
boolean hasLegacyMarkers = headers.contains("desk assignment");
// Phase 10: both of the above are now REJECTED (D-15), not accepted —
// throw IllegalArgumentException("...download the new template...") for either.
// New acceptance check: headers.contains("bamboohr id") && headers.contains("monday")
//   && headers.contains("sunday") && !headers.contains("desk") (no per-row Desk column, D-01).
```
**Test precedent for this exact assertion style:** `DeskAssignmentUploadEnrichedShapeTest.java` lines 165-177 (`assertThatThrownBy(...).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(...)`).

**BambooHR-ID-only matching to REPLACE the current fuzzy fallback** (current lines 249-293 — this ~45-line block is deleted per D-08; new logic is a single `findByTenantIdAndBamboohrId` lookup + reject-with-reason on miss, no agent creation on miss per D-08. Do not reuse the `agent == null` creation branch at all when ID is not found).

**Day-cell parsing — net-new logic, no direct existing analog; use the RESEARCH.md-provided adaptation of the null-safe cell pattern** (see RESEARCH.md `## Code Examples` — `parseDayCell` — this is the concrete code to implement, already reviewed and consistent with the codebase's null-safety/`Optional` conventions used elsewhere in this file):
```java
private record DayCellResult(BigDecimal hours, DayOffType type, String clampWarning) {}

private Optional<DayCellResult> parseDayCell(Row row, Map<String, Integer> col, DayOfWeek day) {
    int idx = col.getOrDefault(EnrichedColumnLayout.normalize(EnrichedColumnLayout.dayHeader(day)), -1);
    Cell cell = idx >= 0 ? row.getCell(idx) : null;
    if (cell == null) return Optional.empty(); // blank -> caller skips row (D-04)

    if (cell.getCellType() == CellType.STRING) {
        String raw = cell.getStringCellValue().trim();
        if (raw.equalsIgnoreCase("MANDATORY")) return Optional.of(new DayCellResult(BigDecimal.ZERO, DayOffType.MANDATORY, null));
        if (raw.equalsIgnoreCase("PTO")) return Optional.of(new DayCellResult(BigDecimal.ZERO, DayOffType.PTO, null));
        return Optional.empty(); // unrecognized word -> caller skips row (D-04)
    }
    if (cell.getCellType() == CellType.NUMERIC) {
        BigDecimal value = BigDecimal.valueOf(cell.getNumericCellValue()); // NOT (long) — preserves fractional hours
        if (value.signum() < 0) return Optional.empty(); // negative -> caller skips row (D-10)
        if (value.compareTo(new BigDecimal("24")) > 0) {
            return Optional.of(new DayCellResult(new BigDecimal("24.00"), null,
                    day + ": " + value + " -> 24")); // clamp, non-silent (D-10)
        }
        return Optional.of(new DayCellResult(value.setScale(2, RoundingMode.HALF_UP), null, null));
    }
    return Optional.empty(); // blank/boolean/other -> caller skips row (D-04)
}
```

**Error handling pattern:** this service does not use try/catch for row-level errors — it uses skip-and-continue with `SkippedRow` accumulation (see `DeskAssignmentUploadResult` below), and only throws `IllegalArgumentException` for whole-file structural failures (no sheets, no header row, unrecognised/retired shape). Keep this two-tier error model exactly as-is.

---

### `src/main/java/com/wfm/util/EnrichedColumnLayout.java` (NEW — utility, transform)

**Analog:** none pre-existing (net-new single-source-of-truth class per D-13); closest precedent is the inline header→index map builder in `DeskAssignmentUploadService.java` (lines 110-118, reproduced above) whose *convention* (lowercase+trim keys) this class must formalize into a static `normalize()` method.

**Concrete class to implement** (from RESEARCH.md `## Architecture Patterns` Pattern 1 — reviewed, self-consistent, dependency-free):
```java
package com.wfm.util;

public final class EnrichedColumnLayout {
    public static final String COL_BAMBOOHR_ID = "BambooHR ID";
    public static final String COL_FIRST_NAME  = "First Name";
    public static final String COL_LAST_NAME   = "Last Name";
    public static final String COL_JOB_TITLE   = "Job Title";
    public static final String COL_EMAIL       = "Email";
    public static final String COL_DEPARTMENT  = "Department";
    public static final String COL_ACTIVE      = "Active";

    public static final java.time.DayOfWeek[] DAY_ORDER = {
        java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
        java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.SATURDAY,
        java.time.DayOfWeek.SUNDAY
    };

    public static String dayHeader(java.time.DayOfWeek d) {
        String name = d.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    public static java.util.List<String> identityHeaders() {
        return java.util.List.of(COL_BAMBOOHR_ID, COL_FIRST_NAME, COL_LAST_NAME,
                COL_JOB_TITLE, COL_EMAIL, COL_DEPARTMENT, COL_ACTIVE);
    }

    private static final java.util.regex.Pattern SPECIALTY_HEADER =
        java.util.regex.Pattern.compile("^specialty\\s*(\\d+)$", java.util.regex.Pattern.CASE_INSENSITIVE);

    public static java.util.Optional<Integer> specialtyIndex(String headerLowerTrimmed) {
        var m = SPECIALTY_HEADER.matcher(headerLowerTrimmed);
        return m.matches() ? java.util.Optional.of(Integer.parseInt(m.group(1))) : java.util.Optional.empty();
    }

    public static String normalize(String header) {
        return header == null ? "" : header.trim().toLowerCase();
    }

    private EnrichedColumnLayout() {}
}
```
**Consumed by:** `DeskAssignmentUploadService` (header→index), `DeskAssignmentTemplateService` (index→header), `DeskAgentExportService` (export column order) — none of the three may hardcode header strings independently (RESEARCH.md Anti-Patterns).

---

### `src/main/java/com/wfm/service/DeskAssignmentTemplateService.java` (NEW — service, file-I/O)

**Analog:** `src/main/java/com/wfm/service/DeskAgentExportService.java` (full file, 84 lines) — near-identical shape: POI `XSSFWorkbook` in try-with-resources, header row with a bold/grey style, one row per data item, `ByteArrayOutputStream` → `byte[]`.

**Imports pattern to copy** (`DeskAgentExportService.java` lines 1-11):
```java
package com.wfm.service;

import com.wfm.dto.DeskAgentResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
```

**Header style + workbook-per-desk-sheet pattern to copy and adapt (one sheet per desk instead of one flat sheet)** (`DeskAgentExportService.java` lines 16-34, 75-83):
```java
try (XSSFWorkbook workbook = new XSSFWorkbook()) {
    CellStyle headerStyle = createHeaderStyle(workbook);
    // Phase 10 adaptation: loop `for (Desk desk : allDesks) { Sheet sheet = workbook.createSheet(desk.getName()); ... }`
    // instead of a single `workbook.createSheet("Desk Agents")`.
    Sheet sheet = workbook.createSheet(/* desk.getName() */);
    Row header = sheet.createRow(0);
    // header text comes from EnrichedColumnLayout.identityHeaders() + dayHeader(d) for each DAY_ORDER entry
    // + "Specialty 1"/"Specialty 2" (seed 2 blank specialty columns per D-14's "leave specialties blank")
    for (int i = 0; i < columns.length; i++) {
        Cell cell = header.createCell(i);
        cell.setCellValue(columns[i]);
        cell.setCellStyle(headerStyle);
    }
} catch (IOException e) {
    throw new RuntimeException("Failed to generate Excel export", e);
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
```
**Pre-seeding pattern (D-14):** for each desk, iterate its current roster (reuse whatever query `DeskAgentExportService`'s caller uses — `DeskAgentService.listDeskAgentResponses(deskId, null, null, Integer.MAX_VALUE)`, see `DeskAgentController.java` lines 88-91) and write identity columns filled (`row.createCell(i).setCellValue(agent.bamboohrId())` etc., mirroring `DeskAgentExportService.java` lines 39-53's null-guarded `setCellValue` calls), leaving day cells + specialty cells blank (no `createCell` call, or empty string).

---

### `src/main/java/com/wfm/service/DeskAgentExportService.java` (modify — share `EnrichedColumnLayout`)

**Analog:** itself (full file above). Change: the `columns` array (lines 21-27) and the per-row `row.createCell(N).setCellValue(...)` block (lines 39-53) should pull identity header text from `EnrichedColumnLayout.identityHeaders()` where the two column sets overlap (BambooHR ID, Name/First/Last, Email, Department, Job Title, Active), for round-trip symmetry with the new template and parser (D-13). Existing agent-metadata-only columns (ID, Desk ID, Last Refreshed At, contracted hours) that are NOT part of the enriched upload shape stay hardcoded — only the columns shared with the upload/template shape move to the shared constants.

---

### `src/main/java/com/wfm/model/AgentDayHours.java` (modify: + `dayOffType`)

**Analog:** `src/main/java/com/wfm/model/AgentDayOff.java` (full file, 55 lines) — the sibling entity's `@Enumerated(EnumType.STRING)` field is the exact pattern to copy:
```java
// AgentDayOff.java lines 27-29 — the pattern to mirror on AgentDayHours
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private DayOffType type;
```

**Field to add to `AgentDayHours.java`** (current file, 48 lines, add after `hours` field at line 30):
```java
@Enumerated(EnumType.STRING)
@Column(name = "day_off_type", length = 9)
private DayOffType dayOffType;   // null | MANDATORY | PTO — reuses com.wfm.model.DayOffType

public DayOffType getDayOffType() { return dayOffType; }
public void setDayOffType(DayOffType t) { this.dayOffType = t; }
```
Note the field is **nullable** here (unlike `AgentDayOff.type`, which is `nullable = false`) — this is the documented, intentional divergence (RESEARCH.md "Alternatives Considered": `agent_day_hours` has an implicit third state, `null` = plain worked/unlabelled-0, that `agent_day_off` never has).

**`DayOffType` enum — reused as-is, no changes needed** (`src/main/java/com/wfm/model/DayOffType.java`, full file):
```java
package com.wfm.model;

public enum DayOffType {
    MANDATORY,
    PTO
}
```

---

### `src/main/resources/db/migration/V30__agent_day_hours_recurring_status.sql` (NEW)

**Analog:** `src/main/resources/db/migration/V29__agent_first_last_name_and_day_hours.sql` (full file) — additive-column style, forward-only, no down-migration. V29's step 3 (`CREATE TABLE agent_day_hours (...)`) is the table this migration adds a column to.

**Concrete migration** (per RESEARCH.md D-12 decision):
```sql
-- V30__agent_day_hours_recurring_status.sql
ALTER TABLE agent_day_hours
    ADD COLUMN day_off_type VARCHAR(9);
-- NULL = a normal worked day (hours > 0) or a plain unlabelled 0 (no descriptive reason).
-- 'MANDATORY' or 'PTO' = the spreadsheet cell used that keyword (D-03); reuses the existing
-- DayOffType enum values so the label is consistent with AgentDayOff's vocabulary without
-- reusing AgentDayOff's dated materialization mechanism.
-- NULL for all existing rows — no backfill needed since no spreadsheet has ever populated
-- this column before this migration (mirrors V29's "no backfill for pre-existing gap" style).
```
**Sequencing:** confirm no `V30` already exists at plan time (`V29` was latest at research time — verify before writing the plan file, per canonical_refs note "any new recurring-PTO storage (D-12) follows this sequence").

---

### `src/main/java/com/wfm/dto/DeskAssignmentUploadResult` (record, extend for D-11 rollup + warnings)

**Analog:** `src/main/java/com/wfm/dto/FteUploadResult.java` (full file) — the closest existing "upload result" DTO that already carries counts + detail lists + extra metadata beyond the two lists:
```java
package com.wfm.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record FteUploadResult(
        int savedCount,
        int skippedCount,
        List<String> savedDetails,
        List<String> skippedDetails,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalTime startTime,
        LocalTime endTime,
        int incrementMinutes
) {}
```

**Current `DeskAssignmentUploadResult`** (nested record in `DeskAssignmentUploadService.java`, lines 405-410 — extend, do not replace):
```java
public record DeskAssignmentUploadResult(
        int assignedCount,
        int skippedCount,
        List<String> assignedDetails,
        List<SkippedRow> skippedDetails
) {}
```
**Extension for D-11** (add fields, following `FteUploadResult`'s "extra metadata fields after the four base fields" convention):
```java
public record DeskAssignmentUploadResult(
        int assignedCount,
        int skippedCount,
        List<String> assignedDetails,
        List<SkippedRow> skippedDetails,
        List<SheetSummary> sheetSummaries,   // NEW — per-sheet rollup (D-11)
        List<String> warnings,               // NEW — clamp warnings + skipped-sheet notices (D-10/D-11)
        List<SkippedSheet> skippedSheets      // NEW — "Sheet 'X': no matching desk — skipped" (D-02)
) {}

public record SheetSummary(String deskName, int importedCount, int skippedCount) {}
```

---

### `src/main/java/com/wfm/dto/SkippedRow.java` (existing, extend with sibling `SkippedSheet`)

**Analog:** itself (existing file, full):
```java
package com.wfm.dto;

public record SkippedRow(
        int rowNumber,
        String bamboohrId,
        String name,
        String reason
) {}
```
**New sibling record `SkippedSheet.java`** (same package/style, mirrors `FteUploadService`'s plain-`String` skipped-sheet notices but as a structured record for D-11's "non-blocking warning" surfacing):
```java
package com.wfm.dto;

public record SkippedSheet(String sheetName, String reason) {}
```

---

### `src/main/java/com/wfm/controller/ClientManagementController.java` (add template-download endpoint)

**Analog:** `exportEmployees()` in the same file (lines 78-92) and the byte-array-attachment convention also used by `DeskAgentController.exportDeskAgents()` (lines 88-97). Both share the identical response-building idiom:
```java
@GetMapping("/employees/export")
public ResponseEntity<byte[]> exportEmployees(@RequestParam String department) {
    String tenantId = String.valueOf(TenantContext.getTenantId());
    List<BambooEmployeeResponse> employees = clientManagementService.listEmployeesByDepartment(tenantId, department, false);
    byte[] xlsx = clientManagementExportService.exportEmployeesToExcel(employees);
    String sanitizedDepartment = department.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    String filename = sanitizedDepartment + "-employees.xlsx";
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(xlsx);
}
```
**New endpoint to add** (mirror exactly, swap in `DeskAssignmentTemplateService`, no `@RequestParam` needed since the template covers all desks for the tenant — per D-13/D-14 "one workbook, one sheet per desk"):
```java
@GetMapping("/desk-assignments/template")
public ResponseEntity<byte[]> downloadDeskAssignmentTemplate() {
    byte[] xlsx = deskAssignmentTemplateService.generateTemplate();
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"desk-assignment-template.xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(xlsx);
}
```
The existing `uploadDeskAssignments` endpoint (lines 103-107) stays unchanged in signature (still `MultipartFile file` → `DeskAssignmentUploadService.DeskAssignmentUploadResult`), only the return DTO's shape grows (see above).

---

### `frontend/src/pages/ClientManagement.tsx` (extend Upload Results modal, D-11)

**Analog:** itself — existing Upload Results modal, lines 453-501, and `handleDownloadSkippedCsv` (lines 201-225).

**Modal structure to extend, not replace** (lines 453-470, 483-493 — add a per-sheet rollup block and a warnings block using the same inline-style/table conventions already used for `skippedDetails`):
```tsx
{uploadResult !== null && (
  <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
    <div style={{ background: '#fff', borderRadius: '8px', padding: '1.5rem', width: '600px', maxHeight: '80vh', display: 'flex', flexDirection: 'column' }}>
      <h3>Upload Results</h3>
      <div style={{ marginBottom: '0.5rem' }}>
        <span style={{ color: '#16a34a', fontWeight: 600, fontSize: '1.1rem' }}>{uploadResult.assignedCount} assigned</span>
        {uploadResult.skippedCount > 0 && (
          <> <span style={{ color: '#dc2626', fontWeight: 600, fontSize: '1.1rem' }}>{uploadResult.skippedCount} skipped</span></>
        )}
      </div>
      {/* NEW: per-sheet rollup (D-11) — same list style as skippedDetails table below */}
      {/* NEW: warnings block (clamp + skipped-sheet notices, D-10/D-11) — non-blocking, distinct color e.g. amber */}
      {uploadResult.skippedCount > 0 && (
        <div style={{ overflowY: 'auto', maxHeight: '300px', marginTop: '1rem', border: '1px solid #e5e7eb', borderRadius: '4px' }}>
          <table style={{ width: '100%', fontSize: '0.85rem' }}>
            <thead><tr><th>Row</th><th>BambooHR ID</th><th>Name</th><th>Reason</th></tr></thead>
            <tbody>
              {uploadResult.skippedDetails.map((row: SkippedRow, idx: number) => (
                <tr key={idx}><td>{row.rowNumber}</td><td>{row.bamboohrId ?? '—'}</td><td>{row.name ?? '—'}</td><td>{row.reason}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.75rem', justifyContent: 'flex-end' }}>
        {uploadResult.skippedCount > 0 && (<button onClick={handleDownloadSkippedCsv}>Download skipped as CSV</button>)}
        <button onClick={() => setUploadResult(null)}>Close</button>
      </div>
    </div>
  </div>
)}
```

**CSV-injection sanitization pattern — reuse verbatim for any new template/export string writes** (`handleDownloadSkippedCsv`, lines 201-210):
```tsx
const sanitize = (val: string | null | undefined): string => {
  if (val == null) return ''
  // CSV-injection mitigation (T-05-05-02): prefix dangerous leading chars with single quote
  const s = String(val)
  const sanitized = /^[=+\-@]/.test(s) ? "'" + s : s
  // Escape inner double-quotes by doubling them
  return sanitized.replace(/"/g, '""')
}
```
RESEARCH.md's Security Domain section flags this exact function as the mitigation to mirror server-side when writing operator-supplied strings into the new template `.xlsx` cells.

**Upload trigger + result-set pattern to keep unchanged** (lines 159-176):
```tsx
const handleUploadDeskAssignments = async (e: React.ChangeEvent<HTMLInputElement>) => {
  const file = e.target.files?.[0]
  if (!file) return
  setUploading(true)
  try {
    const result: DeskAssignmentUploadResult = await clientManagement.uploadDeskAssignments(file)
    setUploadResult(result)
    if (viewDeskId) { loadDeskAgents(viewDeskId) }
  } catch (err) {
    showToast('error', getErrorMessage(err))
  } finally {
    setUploading(false)
    if (fileInputRef.current) fileInputRef.current.value = ''
  }
}
```
**New template-download handler — mirror `handleExportEmployees`** (lines 178-199, blob-download pattern):
```tsx
const handleDownloadTemplate = async () => {
  try {
    const res = await clientManagement.downloadDeskAssignmentTemplate()
    if (!res.ok) { showToast('error', 'Template download failed'); return }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'desk-assignment-template.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (err) {
    showToast('error', getErrorMessage(err))
  }
}
```
Also update the static helper text at lines 326-328 (currently describes the retired 6-col shape: "columns: BambooHR ID, Name, Email, Desk Assignment...") to describe the new per-desk-sheet shape and point at the template download button (D-15's one-time re-download messaging).

---

### `frontend/src/api/client.ts` (extend DTO types + add template-download call)

**Analog:** itself — `clientManagement.exportEmployees` (lines 409-412, blob-fetch pattern) and `clientManagement.uploadDeskAssignments` (lines 413-427, JSON-response pattern).

**New API call — mirror `exportEmployees`'s fetch-blob pattern exactly:**
```ts
downloadDeskAssignmentTemplate: () =>
  fetch(`${API_BASE}/client-management/desk-assignments/template`, {
    headers: { 'X-Tenant-ID': currentTenantId },
  }),
```

**Existing DTO types to extend** (lines 430, 444):
```ts
export interface SkippedRow { rowNumber: number; bamboohrId: string | null; name: string | null; reason: string }
export interface DeskAssignmentUploadResult { assignedCount: number; skippedCount: number; assignedDetails: string[]; skippedDetails: SkippedRow[] }
```
**Extension (mirror `DeskAssignmentUploadResult`'s backend record extension above):**
```ts
export interface SkippedSheet { sheetName: string; reason: string }
export interface SheetSummary { deskName: string; importedCount: number; skippedCount: number }
export interface DeskAssignmentUploadResult {
  assignedCount: number
  skippedCount: number
  assignedDetails: string[]
  skippedDetails: SkippedRow[]
  sheetSummaries: SheetSummary[]   // NEW
  warnings: string[]                // NEW
  skippedSheets: SkippedSheet[]     // NEW
}
```

---

### NEW test files (Wave 0 gaps per RESEARCH.md Validation Architecture)

**Analog:** `src/test/java/com/wfm/service/DeskAssignmentUploadEnrichedShapeTest.java` (full file, 192 lines) and `DeskAssignmentUploadLegacyShapeTest.java` (194 lines) — both already establish the exact test scaffolding (mock-based, no Spring context) to copy for every new `DeskAssignmentUpload*Test`.

**Test scaffolding to copy verbatim** (`DeskAssignmentUploadEnrichedShapeTest.java` lines 1-63):
```java
package com.wfm.service;

import com.wfm.dto.SkippedRow;
import com.wfm.model.*;
import com.wfm.repository.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeskAssignmentUploadXyzTest {
    private AgentRepository agentRepository;
    private DeskRepository deskRepository;
    private ClientManagementService clientManagementService;
    private AgentPreferenceRepository agentPreferenceRepository;
    private AgentExceptionRepository agentExceptionRepository;
    private AgentDayHoursRepository agentDayHoursRepository;
    private SpecializationRepository specializationRepository;
    private AgentEligibilityService agentEligibilityService;
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
        service = new DeskAssignmentUploadService(
                agentRepository, deskRepository, clientManagementService,
                agentPreferenceRepository, agentExceptionRepository, agentDayHoursRepository,
                specializationRepository, agentEligibilityService);
        com.wfm.config.TenantContext.setTenantId(TENANT_ID);
        when(agentEligibilityService.isNonSchedulable(anyLong(), anyString())).thenReturn(false);
    }
}
```
**Workbook-builder helper to copy verbatim** (same file, lines 82-119 — `buildWorkbook(headers, dataRow)` and `buildHeaderOnlyWorkbook(headers)`); for the new multi-sheet tests, add a `buildMultiSheetWorkbook(Map<String sheetName, String[][] rows>)` variant following the identical `XSSFWorkbook` / `MockMultipartFile` construction idiom.

**Shape-rejection assertion style to copy** (lines 165-177):
```java
assertThatThrownBy(() -> service.uploadDeskAssignments(file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unrecognised spreadsheet shape")
        .satisfies(ex -> assertThat(ex.getMessage()).containsIgnoringCase("Got headers:"));
```
Use the same pattern for `DeskAssignmentUploadRetiredShapeTest`, swapping the expected message for D-15's "download the new template" wording.

**Fractional-hours regression test (Pitfall 1) — new, no direct analog, but follows the same `SkippedRow`/result-assertion idiom** used throughout `DeskAssignmentUploadEnrichedShapeTest` (e.g. lines 136-139):
```java
assertThat(result.skippedCount()).isEqualTo(1);
SkippedRow skipped = result.skippedDetails().get(0);
assertThat(skipped.rowNumber()).isEqualTo(2);
assertThat(skipped.reason()).isEqualTo("...");
```

---

### `src/test/java/com/wfm/integration/BambooRefreshServiceTest.java` (extend — D-12 regression guard)

**Analog:** itself. Add a test asserting `refreshDeskAgents()` does not delete or alter `agent_day_hours` rows (the D-12 window-wipe hazard this phase's storage decision is designed to avoid). The relevant production code path to assert against is `BambooRefreshService.java` lines 251-257 (`agentDayOffRepository.deleteByAgent_IdAndDateBetween`) — confirm this delete call is scoped to `AgentDayOffRepository` only and never touches `AgentDayHoursRepository`.

## Shared Patterns

### Multi-tenant scoping
**Source:** every repository call across all analog files (`agentRepository.findByTenantIdAndDeskId(tenantId, deskId)`, `deskRepository.findByTenantId(tenantId)`, etc.)
**Apply to:** every new repository method added for `agent_day_hours`/desk-sheet lookups — must be tenant-scoped like its Phase 9 siblings (RESEARCH.md ASVS V4 note).
```java
long tenantId = TenantContext.getTenantId();
List<Desk> allDesks = deskRepository.findByTenantId(tenantId);
```

### Skip-and-continue row validation with `SkippedRow`
**Source:** `DeskAssignmentUploadService.java` (throughout the row loop, e.g. lines 200-203, 208-211, 220-224, 243-245)
**Apply to:** `DeskAssignmentUploadService` rewrite — every day-cell/identity validation failure appends a `SkippedRow(rowNum, bamboohrId, name, reason)` and `continue`s to the next row; whole-sheet failures use the new `SkippedSheet` record instead.
```java
skipped.add(new SkippedRow(i + 1, bamboohrId, name, "Desk '" + deskName.trim() + "' not found"));
continue;
```

### POI try-with-resources workbook handling
**Source:** `DeskAssignmentUploadService.java` line 98, `FteUploadService.java` line 70, `DeskAgentExportService.java` line 17
**Apply to:** `DeskAssignmentTemplateService` (new) and the rewritten parser.
```java
try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) { ... }
// or, for writing:
try (XSSFWorkbook workbook = new XSSFWorkbook()) { ... }
```

### Flyway forward-only, additive migrations
**Source:** `src/main/resources/db/migration/V29__agent_first_last_name_and_day_hours.sql` (and the whole `db/migration/` directory convention — no down-migrations found)
**Apply to:** `V30__agent_day_hours_recurring_status.sql`.

### CSV/XLSX formula-injection sanitization
**Source:** `frontend/src/pages/ClientManagement.tsx` lines 203-210 (`handleDownloadSkippedCsv`'s `sanitize()`)
**Apply to:** any new server-side or client-side code that writes operator-supplied strings (identity fields echoed into Upload Results, or re-exported into the new template `.xlsx`) — RESEARCH.md flags this as a required control (Known Threat Patterns, Tampering).

### Byte-array XLSX attachment response
**Source:** `ClientManagementController.java` lines 78-92 (`exportEmployees`), `DeskAgentController.java` lines 88-97 (`exportDeskAgents`)
**Apply to:** the new `downloadDeskAssignmentTemplate()` endpoint.
```java
return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(xlsx);
```

## No Analog Found

None — every file in scope has at least a role-match analog in the existing codebase. The single genuinely novel piece of logic (day-cell → hours/DayOffType parsing) has no direct precedent but is fully specified in RESEARCH.md's `## Code Examples` section and cross-checked against this file's existing null-safety/`Optional` conventions.

## Metadata

**Analog search scope:** `src/main/java/com/wfm/{service,model,controller,dto,util}/`, `src/main/resources/db/migration/`, `src/test/java/com/wfm/{service,integration}/`, `frontend/src/{pages,api}/`
**Files scanned:** `DeskAssignmentUploadService.java`, `FteUploadService.java`, `DeskAgentExportService.java`, `AgentDayHours.java`, `AgentDayOff.java`, `DayOffType.java`, `ClientManagementController.java`, `DeskAgentController.java`, `FteUploadResult.java`, `SkippedRow.java`, `V29__agent_first_last_name_and_day_hours.sql`, `DeskAssignmentUploadEnrichedShapeTest.java`, `DeskAssignmentUploadLegacyShapeTest.java` (listed), `DeskAssignmentUploadNonSchedulableRejectTest.java` (listed), `BambooRefreshService.java` (lines 240-339), `frontend/src/pages/ClientManagement.tsx` (lines 140-501), `frontend/src/api/client.ts` (lines 395-444)
**Pattern extraction date:** 2026-07-31
**No CLAUDE.md or .claude/skills found** — project has no additional convention files beyond the codebase itself; `.claude/` in this repo only contains `settings.local.json` and a `worktrees/` directory, no `skills/`.
