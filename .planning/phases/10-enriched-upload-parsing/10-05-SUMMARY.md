---
phase: 10-enriched-upload-parsing
plan: 05
subsystem: testing
tags: [junit, mockito, poi, xlsx, desk-assignment-upload]

# Dependency graph
requires:
  - phase: 10-enriched-upload-parsing (plan 03)
    provides: rewritten DeskAssignmentUploadService (multi-sheet parser, EnrichedColumnLayout, day-cell parsing, BambooHR-ID-only match)
provides:
  - Behavioral JUnit regression suite for every parser requirement (UPL-01..UPL-07)
  - Fractional-hours regression guard (7.5 must persist as 7.50, not 7)
  - D-16 structural guard proving DeskAssignmentUploadService has no AgentDayOffRepository dependency
  - D-17 colliding-sheet-name last-wins regression coverage
affects: [10-06, phase-11-bamboohr-merge-engine]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Stateful Mockito registry (Map<bamboohrId, Agent> backed thenAnswer stubs) to model clearDesk/re-import round trips across multiple sheets in a single test"
    - "ArgumentCaptor<AgentDayHours> / ArgumentCaptor<Agent> to assert on parser-internal writes not exposed via the result DTO"

key-files:
  created:
    - src/test/java/com/wfm/service/DeskAssignmentUploadMultiSheetTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadDayCellTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadSpecialtyTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadValidationTest.java
  modified:
    - src/test/java/com/wfm/service/DeskAssignmentUploadNonSchedulableRejectTest.java

key-decisions:
  - "Colliding-sheet-name last-wins test uses a stateful Mockito registry (Map<bamboohrId,Agent> backing thenAnswer stubs for findByTenantIdAndBamboohrId/save/findByTenantIdAndDeskId) instead of static stubs, so clearDesk's unassign-then-reimport behavior across two sheets targeting the same desk is faithfully modeled"
  - "D-16 guard implemented via reflection (Class.getDeclaredFields()) asserting no AgentDayOffRepository-typed field exists on DeskAssignmentUploadService, since Mockito verify(...never()) cannot assert against a collaborator that was never injected"
  - "Blank/negative/unrecognized-word day-cell skip reasons share one assertion pattern (\"<Day> cell blank or invalid\") since parseDayCell returns Optional.empty() for all three and the caller does not further distinguish them in the message"

requirements-completed: [UPL-01, UPL-02, UPL-03, UPL-04, UPL-05, UPL-06, UPL-07]

# Metrics
duration: 20min
completed: 2026-07-31
---

# Phase 10 Plan 05: Enriched Upload Parser Behavioral Test Suite Summary

**JUnit/Mockito/POI regression suite covering every rewritten-parser requirement (UPL-01..07), including the fractional-hours truncation regression and a reflection-based guard proving the parser can never delete BambooHR MANDATORY blocks**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-07-31T22:05:00Z (approx, from STATE.md prior session)
- **Completed:** 2026-07-31T22:20:00Z
- **Tasks:** 3
- **Files modified:** 5 (4 created, 1 extended)

## Accomplishments
- Full behavioral coverage of the rewritten `DeskAssignmentUploadService` parser: multi-sheet iteration + unmatched-sheet skip (UPL-01), unbounded `Specialty N` detection (UPL-02), day-cell parsing incl. the fractional-hours regression and MANDATORY/PTO labeling (UPL-03/04/05), validation skip reasons + non-silent clamp warning + per-sheet rollup (UPL-06), and BambooHR-ID-only rejection (UPL-07)
- Pinned the fractional-hours regression explicitly: `7.5` must persist as `BigDecimal("7.50")`, not truncate to `7` via the old `getCellString` `(long)` cast bug
- Added plan-checker-requested coverage beyond the base requirement list: colliding sheet names that normalize to the same desk (last-sheet-wins, D-17) and a structural reflection guard proving `DeskAssignmentUploadService` has no `AgentDayOffRepository` field (D-16 union — upload can never delete BambooHR field-4517 MANDATORY blocks)
- Full `DeskAssignmentUpload*` suite (8 test classes) green; full project `./gradlew test` suite (7m 51s) green with no regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: MultiSheet + NonSchedulable/ID-reject tests (UPL-01, UPL-07)** - `d70a1ac` (test)
2. **Task 2: DayCell + Specialty tests (UPL-02, UPL-03, UPL-04, UPL-05)** - `57459dc` (test)
3. **Task 3: Validation test — skip reasons, clamp warning, per-sheet rollup (UPL-06)** - `93d3be1` (test)

**Plan metadata:** (this commit)

## Files Created/Modified
- `src/test/java/com/wfm/service/DeskAssignmentUploadMultiSheetTest.java` - Multi-sheet import + unmatched-sheet skip notice, case/whitespace-insensitive sheet-to-desk matching, colliding-sheet-name last-wins (D-17), D-16 structural guard (no AgentDayOffRepository field)
- `src/test/java/com/wfm/service/DeskAssignmentUploadDayCellTest.java` - Numeric day-cell parsing incl. fractional-hours regression (7.5 -> 7.50), MANDATORY/PTO keyword -> `day_off_type` label storage, case-insensitive keyword matching
- `src/test/java/com/wfm/service/DeskAssignmentUploadSpecialtyTest.java` - Unbounded `Specialty N` header detection; first non-blank = primary, rest = secondary; single-Specialty-column sheets still resolve
- `src/test/java/com/wfm/service/DeskAssignmentUploadValidationTest.java` - Mixed-validity workbook: blank/negative/unrecognized-word skip reasons, non-silent >24 clamp warning, per-sheet imported/skipped rollup counts
- `src/test/java/com/wfm/service/DeskAssignmentUploadNonSchedulableRejectTest.java` - Extended with `unmatchedBambooHrId_isRejected_noAgentCreated`: reason "BambooHR ID not found", `agentRepository.findByTenantIdAndBamboohrId`/`save` never invoked for that row

## Decisions Made
- Colliding-sheet-name test models `clearDesk`'s unassign-then-reimport round trip with a stateful Mockito registry (`Map<bamboohrId, Agent>` backing `thenAnswer` stubs) rather than static `thenReturn` stubs, since the desk roster must reflect the *live* state after the first sheet's agent is processed and then unassigned when the second sheet's `clearDesk` runs
- D-16 (no `AgentDayOffRepository` dependency) verified via reflection over `DeskAssignmentUploadService.class.getDeclaredFields()` rather than a Mockito `verify(...never())`, since the collaborator doesn't exist to verify against — mirrors the structural-absence approach used in plan 10-01 Task 3
- All three invalid-day-cell skip reasons (blank, negative, unrecognized word) share the exact same message pattern (`"<Day> cell blank or invalid"`) because the parser's `parseDayCell` collapses all three cases to `Optional.empty()` with no further distinction in the caller — tests assert on this shared pattern rather than inventing per-case wording the code doesn't produce

## Deviations from Plan

None - plan executed exactly as written. All four new test files and the one extension were built to the plan's specified behaviors; two Mockito `any()`/`anyLong()` primitive-matcher mismatches were caught and fixed during the normal test-writing/verify loop (not deviations from the plan's scope, just getting the test code itself correct before the first commit).

## Issues Encountered
- Initial `DeskAssignmentUploadMultiSheetTest` draft used `any()` for a primitive `long` parameter on `findByTenantIdAndDeskId(long, UUID)`, which Mockito auto-unboxes to `null` -> NPE. Fixed by switching to `anyLong()` for the tenant-id argument before the first commit; no functional impact, caught by the task's own `--tests` verification run before committing.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All Wave 0 parser-requirement tests named in `10-VALIDATION.md` for this plan (UPL-01 through UPL-07) now exist and are green; remaining Wave 0 gaps (UPL-08 retired-shape, UPL-09 template round-trip, D-12 BambooRefreshService regression guard) are covered by plans 10-03/10-04 per `10-VALIDATION.md`'s file-exists column, not this plan's scope
- `./gradlew test --tests "com.wfm.service.DeskAssignmentUpload*"` and the full `./gradlew test` suite are both green — no blockers for the next plan in this phase

---
*Phase: 10-enriched-upload-parsing*
*Completed: 2026-07-31*
