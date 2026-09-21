---
phase: 17-consistency-constraint-drift-reporting
fixed_at: 2026-09-17T22:18:55Z
review_path: .planning/phases/17-consistency-constraint-drift-reporting/17-REVIEW.md
iteration: 2
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 17: Code Review Fix Report

**Fixed at:** 2026-09-17T22:16:19Z (iteration 1: CR-01, WR-01); iteration 2 (this update) adds
IN-01 and IN-02.
**Source review:** .planning/phases/17-consistency-constraint-drift-reporting/17-REVIEW.md
**Iteration:** 2

**Fix scope:** `all` (CR-01, WR-01, IN-01, IN-02). Iteration 1 covered `critical_warning`
(CR-01, WR-01) only; this iteration widens scope to the two remaining Info findings, IN-01 and
IN-02, which iteration 1 explicitly left untouched.

**Summary:**
- Findings in scope: 4
- Fixed: 4
- Skipped: 0

**Verification environment:** Backend changes verified in the main checkout on branch
`claude/create-system-specification-451ge` (per this task's operational notes: no worktree
created, no push, no branch switch). Backend: `./gradlew compileJava compileTestJava` (clean) and
targeted `./gradlew test --tests com.wfm.service.ScheduleExportServiceTest --tests
com.wfm.model.ConstraintWeightsMigrationTest` (8 + 2 tests, 0 failures, 0 errors). Iteration 1's
frontend verification (`npx tsc --noEmit -p frontend/tsconfig.json`, clean) is unchanged by this
iteration, which touched no frontend files.

## Fixed Issues

### CR-01: `driftReport` is never null on a SLOT-scheduled desk, contradicting its own documented contract and leaking a populated popularity table into the Excel export

**Files modified:** `src/main/java/com/wfm/service/ScheduleService.java`,
`src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java`,
`src/test/java/com/wfm/service/ScheduleExportServiceTest.java`
**Commit:** `ea9b6ac`
**Applied fix:** Gated `response.setDriftReport(...)` in `ScheduleService.getScheduleDetail` on
`schedule.getSchedulingMode() == SchedulingMode.SHIFT`, matching the exact fix REVIEW.md
prescribed and restoring `ScheduleDetailResponse.DriftReport`'s documented null-on-SLOT contract.
`SchedulingMode` was already available via the existing `com.wfm.model.*` wildcard import, so no
import change was needed.

Added two regression tests to `ScheduleServiceShiftSnapshotTest`
(`getScheduleDetail_inMemorySlotModeSchedule_driftReportIsNullAndNeverBuilt` and
`getScheduleDetail_inMemoryShiftModeSchedule_driftReportIsBuilt`) that verify via Mockito
`verify(...)` whether `ScheduleOutputService.buildDriftReport` was invoked at all — not merely
that the response field is null — because the mocked `ScheduleOutputService` in that test class
is unstubbed and returns `null` from `buildDriftReport` by default regardless of gating, so
asserting only on the response field would not have been a meaningful regression check. Confirmed
by temporarily reverting the `ScheduleService` change (`git stash`) and re-running: the new SLOT
test failed with `NeverWantedButInvoked` pointing at the unconditional call site, then passed
again once the fix was restored — proving the test actually exercises the fixed code path.

Re-commented (did not rewrite, per REVIEW.md's "at minimum rename/re-comment it" option)
`ScheduleExportServiceTest`'s `exportToExcel_nullDriftReport_...` test: it is a pure
`ScheduleExportService` unit test with no Spring context and never called `ScheduleService`, so
routing it through the real end-to-end path was out of proportion to this test file's design.
Renamed it to `exportToExcel_nullDriftReport_writesOnlyTheMainHeaderRow` and added a comment
clarifying it only exercises `writeDriftReport`'s own `null` branch in isolation, pointing at the
new `ScheduleServiceShiftSnapshotTest` regression tests as the actual end-to-end CR-01 coverage.

### WR-01: `ConstraintWeightsData`'s `Score | number` widening is bridged entirely by unchecked casts, so a future numeric field would silently misrender rather than fail to compile

**Files modified:** `frontend/src/pages/ConstraintWeightsPage.tsx`
**Commit:** `7ac545e`
**Applied fix:** Took REVIEW.md's first fix option (runtime guard over the discriminated-type
option) — a discriminated type wouldn't have helped here anyway, since every row is indexed by a
plain `string` key drawn at runtime from the `CONSTRAINTS` array, which widens back to the index
signature type regardless of how precisely `ConstraintWeightsData` is typed. Added an `isScore`
type guard (`typeof value === 'object' && value !== null && 'hardScore' in value && 'softScore'
in value`) and replaced the blanket `(weights as Record<string, Score>)[key] || (DEFAULTS[key] as
Score)` cast with a per-row check: if neither the live value nor the default narrows to `Score`,
the row is skipped (returns `null` from the `.map` callback) with a `console.error` naming the
offending key, instead of silently reinterpreting a plain number as
`{ hardScore: undefined, softScore: undefined }` and rendering "Soft" with NaN/blank inputs.
Verified with `npx tsc --noEmit` (clean) — behavior for all 24 existing constraint rows is
unchanged since every existing key's value/default already narrows to `Score`.

### IN-01: V49's predicated `UPDATE` statements are self-referential no-ops

**Files modified:** `src/main/resources/db/migration/V49__set_consistency_weight_defaults.sql`
**Commit:** `0b484c2`
**Applied fix:** Documentation only, per REVIEW.md's own stated fix ("No code change required;
consider adding one sentence to the migration header"). Appended a clarifying paragraph to the
existing header comment block (after the T-17-06 paragraph, before the `ALTER TABLE` statements)
stating that both `UPDATE` statements' `SET` and `WHERE` clauses currently name the same literal,
so they are no-ops by construction today, exist to document/prove the predicate shape (exercised
as such by `ConstraintWeightsMigrationTest`), and would only perform real work if a future
migration changed the chosen default away from what V38/V48 already shipped. No SQL statement was
altered, reordered, or reformatted — the `ALTER COLUMN` clauses and both `UPDATE` statements are
byte-identical to before this change; confirmed by re-reading the file after the edit.

Flyway checksum safety was not re-litigated here per this task's instructions: V49's original
commit (`31fc5e7`) exists on no remote branch, this branch is ahead of origin, and deploys are
push-triggered, so V49 has never been applied to the live database — a comment-only edit changes
V49's checksum but that is inert since the migration has not yet run anywhere. No
`spring.flyway.validate-on-migrate: false` or other migration-config change was made or needed.

**Verification:** `./gradlew compileJava compileTestJava` (clean) and
`./gradlew test --tests com.wfm.model.ConstraintWeightsMigrationTest` (2 tests, 0 failures, 0
errors) — the test still round-trips the exact predicate shape against both a still-default row
and an operator-tuned row, confirming the comment-only edit did not alter migration behavior.

### IN-02: `writeDriftReport`'s null-report branch is now unreachable in production

**Files modified:** `src/main/java/com/wfm/service/ScheduleExportService.java`
**Commit:** `102e28f`
**Applied fix:** CR-01 (iteration 1) made this branch reachable again for real SLOT-scheduled
desks. Per this finding's own stated fix ("No action needed beyond fixing CR-01; flagged so the
fix for CR-01 doesn't also delete this branch as apparently-dead code"), no logic change was
needed or made. Added a short code comment directly above the `if (report == null ||
report.entries() == null)` early return in `writeDriftReport`, recording that the branch is
reachable specifically for SLOT-scheduled desks now that `ScheduleService.getScheduleDetail`
gates `driftReport` on `SchedulingMode.SHIFT` (CR-01), and must not be removed as dead code by a
future sweep. The branch's logic is unchanged — confirmed by re-reading the method after the edit
(the `if` condition, both statements inside it, and the surrounding code are unchanged).

**Test coverage confirmation:** `ScheduleExportServiceTest`'s
`exportToExcel_nullDriftReport_writesOnlyTheMainHeaderRow` (renamed from
`exportToExcel_nullDriftReport_sheetHasOnlyTheMainHeaderRow` during the CR-01 fix in iteration 1)
still directly covers this branch — it constructs a `ScheduleDetailResponse` with `driftReport`
left null and asserts the resulting sheet has only the header row (`getRow(1)` is null,
`getLastRowNum()` is 0). Confirmed by reading the test source in this iteration: it is unchanged
since iteration 1 and still exercises `writeDriftReport`'s `report == null` path directly (a
unit-level check, not the end-to-end `ScheduleService` path, which iteration 1's new
`ScheduleServiceShiftSnapshotTest` regression tests cover instead). This plainly does cover the
branch; no gap found.

**Verification:** `./gradlew compileJava compileTestJava` (clean) and
`./gradlew test --tests com.wfm.service.ScheduleExportServiceTest` (8 tests, 0 failures, 0
errors).

## Skipped Issues

None — all four in-scope findings were fixed.

---

_Fixed: 2026-09-17T22:16:19Z (iteration 1); updated for iteration 2_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2_
