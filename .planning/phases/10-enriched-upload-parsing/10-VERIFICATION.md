---
phase: 10-enriched-upload-parsing
verified: 2026-07-31T23:15:00Z
status: human_needed
score: 5/5 roadmap success criteria verified; 8/8 code-review findings confirmed fixed in source
overrides_applied: 0
human_verification:
  - test: "Download template from Client Management page, fill a mixed-validity sheet (valid row, blank day cell, 32-hour clamp, unknown BambooHR ID, extra unmatched sheet), upload it, and inspect the Upload Results modal"
    expected: "Template downloads pre-seeded (identity filled, schedule blank), one sheet per desk; modal renders a per-sheet rollup, per-row skip reasons, an amber clamp warning (`... 32 -> 24` style), and an unmatched-sheet notice"
    why_human: "Visual/functional UI rendering in a running instance cannot be verified via static code inspection; this is Plan 10-06 Task 3, a checkpoint:human-verify task that was auto-approved under --auto execution and explicitly deferred to a human operator per its own SUMMARY.md"
---

# Phase 10: Enriched Upload Parsing Verification Report

**Phase Goal:** Operators upload one workbook — one worksheet per desk (sheet name = desk) — that captures full agent identity, an unbounded number of specializations, and a single Mon–Sun day-cell group whose per-cell value encodes status (a number >= 0 = contracted hours, MANDATORY = mandatory day off, PTO = recurring PTO; 0/MANDATORY/PTO all mean not-worked), with per-row/per-sheet validation, a downloadable pre-seeded template, and both the 6-column legacy shape and the old flat enriched shape retired.

**Verified:** 2026-07-31T23:15:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Operator can upload a workbook with one sheet per desk carrying BambooHR ID, first/last name, job title, email, department, active; every field parsed and stored (desk = sheet name) | ✓ VERIFIED | `DeskAssignmentUploadService.java:200-207` iterates every sheet via `getNumberOfSheets()`, resolves desk from `sheet.getSheetName()` (both raw and `WorkbookUtil.createSafeSheetName` keyed, CR-03 fix at lines 91-105); identity fields read via `EnrichedColumnLayout` constants (lines 267-378) and written onto `Agent`. Covered by `DeskAssignmentUploadMultiSheetTest`, full suite green (`./gradlew clean test` — BUILD SUCCESSFUL, 7m53s, fresh run). |
| 2 | Operator can list any number of Specialty N columns; all parsed and matched against desk specializations | ✓ VERIFIED | `EnrichedColumnLayout.specialtyIndex()` (regex `^specialty\s*(\d{1,9})$`, digit-bounded per WR-02 fix) + parser's ordered `specialtyColIndices` scan (lines 245-251), first-non-blank=primary/rest=secondary (lines 316-327), matched against `specsByDesk`. Covered by `DeskAssignmentUploadSpecialtyTest` (3-column + 1-column cases). |
| 3 | Each Mon–Sun cell parses as hours (>=0, 0=not worked), MANDATORY, or PTO; blank cell invalid | ✓ VERIFIED | `parseDayCell` (lines 570-604) reads `cell.getNumericCellValue()` directly (fractional-hours fix, not the truncating `getCellString`), matches MANDATORY/PTO case-insensitively, blank/negative/unrecognized → `DayCellOutcome.fail(...)` with a distinct reason per case (WR-01 fix). `DeskAssignmentUploadDayCellTest` asserts 7.5 → hours=7.50 (not 7), MANDATORY/PTO → 0.00 + label. |
| 4 | Failed-validation row skipped with specific reason; Upload Results shows per-sheet rollup, skip reasons, unmatched-sheet notices, clamp warnings (>24→24); other valid rows still import | ✓ VERIFIED | `DeskAssignmentUploadResult` extended with `sheetSummaries`, `warnings`, `skippedSheets` (lines 521-529); clamp path (line 597-599) adds a non-silent warning; unmatched sheet → `SkippedSheet` (D-02, lines 204-207); per-sheet counts assembled at line 447. `DeskAssignmentUploadValidationTest` and `DeskAssignmentUploadMultiSheetTest` cover blank/negative/unrecognized/clamp/rollup/unmatched-sheet. Frontend renders all four (`ClientManagement.tsx:503-527`, `sheetSummaries`/`warnings`/`skippedSheets` all wired to `client.ts` DTOs). |
| 5 | Unmatched BambooHR ID rejected with "BambooHR ID not found", no agent created; both retired shapes rejected; template download used | ✓ VERIFIED | Lines 335-341: `findCachedEmployee` miss → `SkippedRow(..., "BambooHR ID not found")`, `continue` before any `agentRepository.save`. Shape classification (lines 137-183) rejects legacy 6-col and old flat-enriched with "download the new template... retired format" message, driven entirely by `EnrichedColumnLayout` constants (no hardcoded header literals — grep confirms none). `DeskAssignmentUploadRetiredShapeTest`/`LegacyShapeTest` assert rejection, not import. `DeskAssignmentTemplateService.generateTemplate()` produces the pre-seeded per-desk template via `GET /desk-assignments/template`, wired in `ClientManagementController.java:113-120`. |

**Score:** 5/5 roadmap success criteria verified.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/db/migration/V30__agent_day_hours_recurring_status.sql` | nullable `day_off_type` column | ✓ VERIFIED | Present, additive, no version collision (latest migration confirmed V30) |
| `src/main/java/com/wfm/model/AgentDayHours.java` | nullable `dayOffType` field | ✓ VERIFIED | `@Column(name = "day_off_type", length = 9)`, no `nullable = false`; getter/setter present |
| `src/main/java/com/wfm/util/EnrichedColumnLayout.java` | single source of column truth | ✓ VERIFIED | `identityHeaders()`, `dayHeader()`, `specialtyIndex()` (digit-bounded), `normalize()`, retired-shape markers all present, consumed by parser/template/export |
| `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` | rewritten multi-sheet parser | ✓ VERIFIED | `getNumberOfSheets`, `parseDayCell`, `SkippedSheet`, extended result DTO all present and wired |
| `src/main/java/com/wfm/dto/SkippedSheet.java` | structured unmatched-sheet notice | ✓ VERIFIED | `record SkippedSheet(String sheetName, String reason)` |
| `src/main/java/com/wfm/service/DeskAssignmentTemplateService.java` | pre-seeded per-desk template | ✓ VERIFIED | `createSheet`, `EnrichedColumnLayout` usage, `sanitize`/`FormulaInjectionSanitizer` present |
| `src/main/java/com/wfm/controller/ClientManagementController.java` | template download endpoint | ✓ VERIFIED | `GET /desk-assignments/template` returns `desk-assignment-template.xlsx` attachment |
| `src/main/java/com/wfm/service/DeskAgentExportService.java` | shares `EnrichedColumnLayout`, sanitizes cells | ✓ VERIFIED | Post-CR-02-fix: all operator-controlled strings routed through `FormulaInjectionSanitizer.sanitize()` |
| `src/main/java/com/wfm/util/FormulaInjectionSanitizer.java` | shared sanitizer (`=+-@` + tab/CR) | ✓ VERIFIED | Created during review-fix pass (CR-02/WR-05); single source used by both export and template services |
| `frontend/src/api/client.ts` | extended DTOs + template download call | ✓ VERIFIED | `SkippedSheet`, `SheetSummary`, `downloadDeskAssignmentTemplate`, extended `DeskAssignmentUploadResult` |
| `frontend/src/pages/ClientManagement.tsx` | extended modal + download button + helper text | ✓ VERIFIED | `sheetSummaries`/`warnings`/`skippedSheets` rendered (lines 503-527); `handleDownloadTemplate` + button (lines 179-191, 351); helper text describes new per-desk shape and retirement (lines 354-360) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `DeskAssignmentUploadService` | `EnrichedColumnLayout` | header→index, dayHeader, specialtyIndex | ✓ WIRED | No hardcoded header literals confirmed via grep |
| `DeskAssignmentUploadService` | `AgentDayHoursRepository` | writes hours + dayOffType per weekday | ✓ WIRED | Lines 428-441 |
| `DeskAssignmentUploadService` | `AgentDayOffRepository` | must NOT exist (D-16 union) | ✓ VERIFIED ABSENT | grep confirms zero references; structural reflection test in `DeskAssignmentUploadMultiSheetTest` and `BambooRefreshServiceTest` both pin this |
| `DeskAssignmentTemplateService` | `EnrichedColumnLayout` | identityHeaders/dayHeader | ✓ WIRED | Lines 92-95 |
| `ClientManagementController` | `DeskAssignmentTemplateService` | generateTemplate() byte[] | ✓ WIRED | Lines 113-120 |
| `ClientManagement.tsx` | `client.ts` | downloadDeskAssignmentTemplate/result DTO | ✓ WIRED | Field names match backend exactly |
| `BambooRefreshService` | `AgentDayHoursRepository` | must NOT exist (D-12 durability) | ✓ VERIFIED ABSENT | Reflection test in `BambooRefreshServiceTest` passes |

### Code Review Findings (10-REVIEW.md) — Fix Verification

All 3 Critical + 5 Warning findings were checked directly against current source (not the SUMMARY.md claim of "fixed"):

| ID | Finding | Fix Verified In Source |
|----|---------|------------------------|
| CR-01 | Desk wiped with zero re-imports on malformed per-sheet header | ✓ `DeskAssignmentUploadService.java:230-243` — per-sheet `missingHeaders` check runs before `clearDesk` (line 255); missing → `SkippedSheet` + `continue`, desk untouched |
| CR-02 | `DeskAgentExportService` exports unsanitized operator strings | ✓ Every operator string field now wrapped in `sanitize()` → `FormulaInjectionSanitizer.sanitize()` (lines 48-60) |
| CR-03 | Template sheet-name sanitization vs. parser raw-name lookup mismatch | ✓ `deskByName` populated with both raw name and `WorkbookUtil.createSafeSheetName(...)` (lines 99-105) |
| WR-01 | Day-cell skip reason not specific (blank/negative/unrecognized indistinguishable) | ✓ `DayCellOutcome.fail(...)` returns distinct reasons: "is blank" / "is negative (...)" / "has an unrecognized value (...)" (lines 573-603) |
| WR-02 | Uncaught NumberFormatException on overflowing Specialty N header | ✓ Regex bounded to `\d{1,9}` + try/catch in `specialtyIndex` (EnrichedColumnLayout.java:40-41, 69-76) |
| WR-03 | `.xls` accepted by frontend but not backend | ✓ `WorkbookFactory.create(...)` replaces `new XSSFWorkbook(...)`, with a clean 400 IllegalArgumentException on unreadable format (lines 118-131) |
| WR-04 | Cross-desk move outcome non-deterministic / silent | ✓ Explicit `deskIdsInThisWorkbook` pre-scan + specific "being moved between desks" message (lines 185-197, 380-396) |
| WR-05 | Sanitizer omits leading tab/CR | ✓ `FormulaInjectionSanitizer.sanitize()` checks `= + - @ \t \r` (lines 24-27) |

All 8 findings have dedicated fix commits (`7f344a5`, `bf0a94d`, `61fdb0e`, `54e605d`, `5cdf3a0`, `0fac946`, `0936996`, `bfbf05a`) plus a consolidating commit `1645977`.

### Requirements Coverage

| Requirement | Source Plan(s) | Status | Evidence |
|---|---|---|---|
| UPL-01 | 10-03, 10-05 | ✓ SATISFIED | Multi-sheet iteration, sheet-name=desk, identity fields |
| UPL-02 | 10-02, 10-03, 10-05 | ✓ SATISFIED | Unbounded Specialty N |
| UPL-03 | 10-01, 10-03, 10-05 | ✓ SATISFIED | Numeric hours 0-24, fractional preserved |
| UPL-04 | 10-01, 10-03, 10-05 | ✓ SATISFIED | MANDATORY → agent_day_hours.dayOffType |
| UPL-05 | 10-01, 10-03, 10-05 | ✓ SATISFIED | PTO → agent_day_hours.dayOffType |
| UPL-06 | 10-03, 10-05, 10-06 | ✓ SATISFIED | Skip reasons, clamp warnings, rollup, UI |
| UPL-07 | 10-03, 10-05 | ✓ SATISFIED | BambooHR ID not found rejection, no agent created |
| UPL-08 | 10-03 | ✓ SATISFIED | Both retired shapes rejected file-wide |
| UPL-09 | 10-02, 10-04, 10-06 | ✓ SATISFIED | Pre-seeded template, shared EnrichedColumnLayout, download UI |

No orphaned requirements — REQUIREMENTS.md lists UPL-01..09 all mapped to Phase 10 and all marked Complete; all 9 IDs appear in at least one plan's `requirements` frontmatter.

### Anti-Patterns Found

None. Grep for `TODO|FIXME|XXX|TBD|HACK|PLACEHOLDER` across all phase-modified backend/frontend files returned zero matches. No stub returns, no hardcoded empty-array/object short-circuits found in the parser, template, or export services — all data paths trace to real repository/POI reads and writes.

### Behavioral Spot-Checks / Test Execution

- `./gradlew clean test` (full suite, fresh — not cached): **BUILD SUCCESSFUL in 7m 53s, 6 actionable tasks, all executed**. Includes all Phase 10 test classes: `EnrichedColumnLayoutTest`, `AgentDayHoursPersistenceTest`, `BambooRefreshServiceTest` (D-12 structural guard), `DeskAssignmentUpload{RetiredShape,LegacyShape,EnrichedShape,MultiSheet,DayCell,Specialty,Validation,NonSchedulableReject}Test`, `DeskAssignmentTemplateServiceTest`.
- `cd frontend && npm run build` — succeeded (tsc -b && vite build, no errors).
- Direct source inspection confirms `parseDayCell` reads `cell.getNumericCellValue()` (not the truncating cast), `clearDesk`/`DeskAssignmentUploadService` declare no `AgentDayOffRepository` field (D-16), and `BambooRefreshService` declares no `AgentDayHoursRepository` field (D-12) — both grep-confirmed absent.

### Human Verification Required

### 1. Upload Results modal + template download visual/functional check

**Test:** Run the app (backend + `cd frontend && npm run dev`), open Client Management. Click "Download template," confirm an `.xlsx` downloads with one worksheet per desk (identity filled, day cells + Specialty columns blank). Fill a sheet with a valid row, a blank-day-cell row, a `32`-hour row (clamp), an unknown BambooHR ID row, and optionally an extra unmatched-desk sheet. Upload it.
**Expected:** Upload Results modal shows a per-sheet rollup (imported/skipped counts), per-row skip reasons, an amber clamp warning (`... 32 -> 24` style), and an unmatched-sheet notice — all rendering correctly and legibly.
**Why human:** This is Plan 10-06's `checkpoint:human-verify` Task 3, explicitly gated for blocking human sign-off. It was auto-approved under `--auto` execution mode per the plan's own SUMMARY.md ("Task 3 was a checkpoint:human-verify gated task... auto-approved... the actual visual/functional verification is deferred to the human operator"). All underlying code (DTO wiring, modal JSX, download handler) is confirmed present and correctly wired via static inspection and passing automated tests, but actual rendering/UX in a running browser has not been observed by any agent.

### Gaps Summary

No blocking gaps. All 5 ROADMAP success criteria are verified against source with passing automated tests (full backend suite green on a fresh, non-cached run; frontend build green). All 8 code-review findings (3 critical, 5 warning) have confirmed fixes in the current source, not just SUMMARY.md claims. The single outstanding item is the Plan 10-06 human-verify checkpoint for the Upload Results modal + template download's visual rendering, which was auto-approved under automated execution and requires a human to actually run the app and confirm the UI renders as coded. This is a routine end-of-phase UI sign-off, not evidence of missing or broken functionality — the code path is fully implemented, tested at the DTO/wiring level, and matches the backend contract field-for-field.

---

_Verified: 2026-07-31T23:15:00Z_
_Verifier: Claude (gsd-verifier)_
