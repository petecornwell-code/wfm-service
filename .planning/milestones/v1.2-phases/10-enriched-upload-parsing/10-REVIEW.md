---
phase: 10-enriched-upload-parsing
reviewed: 2026-07-31T22:33:27Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - src/main/java/com/wfm/service/DeskAssignmentUploadService.java
  - src/main/java/com/wfm/service/DeskAssignmentTemplateService.java
  - src/main/java/com/wfm/service/DeskAgentExportService.java
  - src/main/java/com/wfm/util/EnrichedColumnLayout.java
  - src/main/java/com/wfm/model/AgentDayHours.java
  - src/main/java/com/wfm/dto/SkippedSheet.java
  - src/main/java/com/wfm/controller/ClientManagementController.java
  - src/main/resources/application.yml
  - src/main/resources/db/migration/V30__agent_day_hours_recurring_status.sql
  - frontend/src/api/client.ts
  - frontend/src/pages/ClientManagement.tsx
findings:
  critical: 3
  warning: 5
  info: 1
  total: 9
status: issues_found
---

# Phase 10: Code Review Report

**Reviewed:** 2026-07-31T22:33:27Z
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

The rewritten `DeskAssignmentUploadService` correctly implements most of the locked D-01..D-17 decisions: multi-sheet iteration, BambooHR-ID-only matching, the fractional-hours fix (day cells read `getNumericCellValue()` directly, not through the truncating `getCellString()`), clamp-with-warning (non-silent), the `agent_day_hours.day_off_type` union storage for MANDATORY/PTO (correctly avoiding `AgentDayOffRepository` so D-16's coexistence guarantee holds), and the multipart size limits are wired into `application.yml`. POI workbooks are opened with try-with-resources in all three services.

However, three concrete, provable defects undermine the phase's stated safety goals:

1. **`DeskAgentExportService` never sanitizes exported cell values**, even though `DeskAssignmentTemplateService` (this phase's sibling service, generated in the same phase) does — the two services disagree on the exact security control the phase's own research flagged as required for "every operator-supplied string cell."
2. **The template's sanitized sheet name and the parser's raw desk-name lookup can diverge**, breaking the round-trip the pre-seeded template exists to guarantee (D-14), for any desk with a long or Excel-invalid-character name.
3. **Per-sheet header validation does not happen before `clearDesk()` runs.** Workbook-shape classification only inspects sheet 0; if a matched desk's own sheet is missing a required header (e.g., a renamed/typo'd day column), the desk is unconditionally cleared and then every row fails to re-import, silently emptying that desk's roster.

## Critical Issues

### CR-01: Desk roster is wiped with zero successful re-imports when a per-sheet header is malformed

**File:** `src/main/java/com/wfm/service/DeskAssignmentUploadService.java:172-254` (clear call at line 197)
**Issue:** Workbook-shape classification (`isNewPerDeskShape`, lines 118-160) only reads the header row of **sheet 0** to validate the whole file has a `BambooHR ID` + `Monday..Sunday` shape. It never re-validates that an individual matched sheet actually contains those headers before processing it. `clearDesk(tenantId, desk.getId())` (line 197) runs unconditionally right after the sheet's own header row is confirmed non-null (line 172-176) — it does **not** check that `col` (the per-sheet header→index map, built at 178-185) actually contains `BambooHR ID` or the seven day headers.

If an operator's per-desk sheet has a typo'd/renamed/missing header (e.g. `Sunady` instead of `Sunday`, or a deleted `BambooHR ID` column on just one tab), `cellAt`/`parseDayCell` resolve that column to index `-1` for every row on that sheet, so **every row in the sheet is skipped** — but the desk was already cleared (agents unassigned, day-hours/preferences/exceptions deleted) at line 197. The net effect is the desk's entire previously-imported roster is silently deleted with zero replacement rows, and the only signal is a pile of generic per-row skip reasons, not a clear "this sheet is malformed" error.

**Fix:** Validate the sheet's own header set (contains `BambooHR ID` + all 7 day headers) before calling `clearDesk`; if missing, add the sheet to `skippedSheets` with a specific reason (e.g. `"missing required column(s): Sunday"`) and `continue` without clearing the desk.
```java
Set<String> required = new HashSet<>(EnrichedColumnLayout.identityHeaders().subList(0,1)); // BambooHR ID
for (DayOfWeek d : EnrichedColumnLayout.DAY_ORDER) required.add(EnrichedColumnLayout.dayHeader(d));
List<String> missing = required.stream()
        .map(EnrichedColumnLayout::normalize)
        .filter(h -> !col.containsKey(h))
        .toList();
if (!missing.isEmpty()) {
    skippedSheets.add(new SkippedSheet(sheetName, "missing required column(s): " + missing));
    continue; // do NOT clearDesk
}
clearDesk(tenantId, desk.getId());
```

### CR-02: `DeskAgentExportService` writes operator-controlled strings into `.xlsx` cells without formula-injection sanitization

**File:** `src/main/java/com/wfm/service/DeskAgentExportService.java:44-58`
**Issue:** `agent.name()`, `agent.email()`, `agent.department()`, `agent.jobTitle()`, `agent.primarySpecialization().name()`, `formatSecondarySpecializations(...)`, `agent.firstName()`, `agent.lastName()` are all written directly via `row.createCell(n).setCellValue(...)` with no sanitization. All of these fields are directly settable by an operator through the enriched upload this same phase implements (`DeskAssignmentUploadService.java:310-314` sets `agent.setFirstName/setLastName/setJobTitle/setEmail/setDepartment` verbatim from spreadsheet cells). This phase's own `DeskAssignmentTemplateService` applies a `sanitize()` guard (leading `= + - @` → single-quote-prefixed) to the identical class of identity fields (lines 66-72, 100-122) precisely because this is a known CSV/XLSX formula-injection vector — but the export service, touched in the same phase for `EnrichedColumnLayout` header symmetry, was never given the same treatment. A malicious spreadsheet row (e.g. `Last Name = =cmd|'/c calc'!A1`) round-trips through upload → storage → `GET /desks/{id}/agents/export` → opened by a different staff member in Excel, with formula/DDE evaluation risk.
**Fix:** Reuse (or extract to a shared utility) the same `sanitize()` logic from `DeskAssignmentTemplateService` and apply it to every operator-supplied string cell in `exportDeskAgentsToExcel`:
```java
row.createCell(3).setCellValue(sanitize(agent.name() != null ? agent.name() : ""));
row.createCell(4).setCellValue(sanitize(agent.email() != null ? agent.email() : ""));
// ...same for department, jobTitle, primarySpecialization name, secondarySpecializations, firstName, lastName
```
Recommend hoisting `sanitize()` into a shared `com.wfm.util` class so `DeskAssignmentTemplateService` and `DeskAgentExportService` cannot drift again.

### CR-03: Template sheet names are sanitized/truncated but the parser matches on the raw desk name — breaks round-trip for long or special-character desk names

**File:** `src/main/java/com/wfm/service/DeskAssignmentTemplateService.java:51` vs `src/main/java/com/wfm/service/DeskAssignmentUploadService.java:92-96,166`
**Issue:** The template generator creates each sheet with `workbook.createSheet(WorkbookUtil.createSafeSheetName(desk.getName()))` (line 51), which **replaces Excel-invalid characters** (`\ / ? * [ ] :`) and **truncates to Excel's 31-character sheet-name limit**. The parser, however, builds `deskByName` from the raw `d.getName().toLowerCase()` (lines 92-96) and matches sheets via `deskByName.get(sheetName.toLowerCase())` (line 166) with no equivalent sanitization/truncation applied to the desk-name side of the comparison. `Desk.java` has no `@Size`/`@Pattern` validation constraining desk names to ≤31 chars or to Excel-safe characters, so this is directly reachable: any desk named e.g. `"Support / Escalations Team (EU)"` (>31 chars, contains `/`) produces a template sheet named something like `"Support - Escalations Team (E"` (31 chars, `/`→space or similar), which will **never** match `deskByName` on re-upload — the operator's own pre-seeded, unmodified template silently fails to import with `"no matching desk — skipped"` for that entire desk, defeating D-14's round-trip guarantee.
**Fix:** Apply `WorkbookUtil.createSafeSheetName(...)` to the desk name on **both** sides of the comparison so the parser's expectation matches what the template generator actually wrote:
```java
// DeskAssignmentUploadService — build deskByName using the same normalization the template uses
deskByName.put(WorkbookUtil.createSafeSheetName(d.getName()).toLowerCase(), d);
...
Desk desk = deskByName.get(WorkbookUtil.createSafeSheetName(sheetName).toLowerCase());
```
Alternatively, enforce a desk-name length/character constraint at creation time so the two representations can never diverge.

## Warnings

### WR-01: Day-cell skip reason does not distinguish blank / negative / unrecognized-word (D-04/D-10 want a "specific reason")

**File:** `src/main/java/com/wfm/service/DeskAssignmentUploadService.java:244-245, 481-508`
**Issue:** `parseDayCell` returns `Optional.empty()` uniformly for a blank cell, a negative number, and an unrecognized string — the caller then always reports the same generic message: `"{Day} cell blank or invalid"`. D-10 explicitly calls out negative numbers as a distinct rejection case, and D-04/UPL-06 ask for a "specific reason." An operator who typed `-5` instead of `5` gets the same message as an operator who left the cell blank, making the sheet harder to fix.
**Fix:** Return a reason string (or enum) from `parseDayCell` instead of `Optional<DayCellResult>`/empty, e.g. a sealed `DayCellOutcome` with `BLANK`, `NEGATIVE`, `UNRECOGNIZED_WORD` variants, and surface the specific reason in the skip message.

### WR-02: Uncaught `NumberFormatException` on an overflowing `Specialty N` header crashes the whole upload with a 500

**File:** `src/main/java/com/wfm/service/DeskAssignmentUploadService.java:189-193`, `src/main/java/com/wfm/util/EnrichedColumnLayout.java:57-63`
**Issue:** `EnrichedColumnLayout.specialtyIndex` matches header text against `^specialty\s*(\d+)$` with no bound on digit count, then calls `Integer.parseInt(matcher.group(1))`. A header like `"Specialty 99999999999999999999"` (21 digits) matches the regex but overflows `int`, so `Integer.parseInt` throws an uncaught `NumberFormatException` inside the `.filter(...)`/`.sorted(...)` stream at lines 189-193. This propagates out of `uploadDeskAssignments` uncaught, hits `GlobalExceptionHandler`'s generic `Exception` handler, and the whole upload (all desks, not just the offending sheet) fails with an unhelpful `500 INTERNAL_ERROR` instead of a clean validation message.
**Fix:** Bound the regex digit group (e.g. `\d{1,6}`) or catch `NumberFormatException` in `specialtyIndex` and return `Optional.empty()` for unparseable/overflowing values.

### WR-03: File-type mismatch between frontend accept attribute and backend parser capability

**File:** `frontend/src/pages/ClientManagement.tsx:348`, `src/main/java/com/wfm/service/DeskAssignmentUploadService.java:109`
**Issue:** The upload `<input>` advertises `accept=".xlsx,.xls"`, but the backend only opens the stream via `new XSSFWorkbook(file.getInputStream())`, which parses **OOXML (.xlsx) only**. A user who selects a genuine legacy `.xls` (BIFF/OLE2) file will get a POI `NotOfficeXmlFileException` (unchecked), which is not caught by any specific `GlobalExceptionHandler` handler and falls through to the generic `500 "An unexpected error occurred"` response — a poor UX for a file type the UI itself offered.
**Fix:** Either drop `.xls` from `accept` (since only `.xlsx` is truly supported) or use `WorkbookFactory.create(inputStream)` server-side to support both, plus add a specific catch for POI format exceptions mapped to a clean 400.

### WR-04: Cross-desk "move" outcome depends on non-deterministic sheet iteration order

**File:** `src/main/java/com/wfm/service/DeskAssignmentUploadService.java:321-327`
**Issue:** The "already assigned to a different desk" guard (line 322) compares against the agent's currently-persisted `deskId`. If an operator moves an agent from Desk A to Desk B within the same workbook, the outcome depends on which sheet is processed first: if A (the agent's old desk) is processed first, `clearDesk` nulls the agent's `deskId` before B's sheet runs, so the move succeeds; if B is processed first, the agent is skipped from B with `"Agent already assigned to desk A"`, and then A's own `clearDesk` (since the agent is no longer listed on A's sheet) unassigns them entirely — net result: the agent ends up on **no** desk, silently, with no message indicating the workbook's tab order caused this. This is undocumented in D-17 and not obviously discoverable by an operator.
**Fix:** Either (a) two-pass the workbook — first pass clears every matched desk, second pass re-populates rows — so the desk-conflict check never fires for intra-workbook moves, or (b) explicitly detect and message this case ("Agent X is being moved from Desk A to Desk B in this upload").

### WR-05: Formula-injection sanitizer only checks leading `= + - @`, omitting tab/CR per defense-in-depth guidance

**File:** `src/main/java/com/wfm/service/DeskAssignmentTemplateService.java:113-122`, `frontend/src/pages/ClientManagement.tsx:226-233`
**Issue:** Both the backend `sanitize()` (template generator) and the frontend `sanitize()` (skipped-rows CSV export) only test the first character against `= + - @`. OWASP CSV-injection guidance also recommends neutralizing values that begin with a tab (`\t`, 0x09) or carriage return (`\r`, 0x0D), since some spreadsheet/CSV consumers can still trigger formula evaluation on lines that begin with those characters after leading-whitespace normalization. Lower severity than CR-02/CR-03 since primary risk (`=+-@`) is covered, but incomplete relative to the review's stated bar.
**Fix:** Extend both regexes: `/^[=+\-@\t\r]/`.

## Info

### IN-01: `EnrichedColumnLayout.RETIRED_COL_DESK` / `LEGACY_HEADER_DESK_ASSIGNMENT` constants only referenced from one call site each

**File:** `src/main/java/com/wfm/util/EnrichedColumnLayout.java:24-28`
**Issue:** Minor: these two constants exist purely to avoid a hardcoded literal in `DeskAssignmentUploadService`'s shape-classification block, which is good practice per D-13, but there's no test or other consumer that exercises `EnrichedColumnLayout` directly for these two values — low risk, just noting for completeness since D-13's stated goal is "no header string literal hardcoded... anywhere else," and these two are effectively single-use.
**Fix:** No action required; documenting only.

---

_Reviewed: 2026-07-31T22:33:27Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
