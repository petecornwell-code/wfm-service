---
phase: 14-shift-library-scheduling-mode
fixed_at: 2026-08-26T12:52:00Z
review_path: .planning/phases/14-shift-library-scheduling-mode/14-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase 14: Code Review Fix Report

**Fixed at:** 2026-08-26T12:52:00Z
**Source review:** .planning/phases/14-shift-library-scheduling-mode/14-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 5 (critical_warning scope — CR-01, WR-01..WR-04; IN-01..IN-04 excluded by scope)
- Fixed: 5
- Skipped: 0

**Verification environment:** All fixes applied and verified inside an isolated git worktree
(`.claude/worktrees/rf-14-*`, branch `gsd-reviewfix/14-*`), then fast-forwarded onto
`claude/create-system-specification-451ge`. Java changes were verified with `./gradlew
compileJava compileTestJava --offline` (0 errors) plus targeted `./gradlew test --offline`
runs of the directly affected test classes (all passed, 0 failures/errors). The TypeScript
change was verified with `npx tsc --noEmit` against the project's existing `tsconfig.json`
(0 errors, via a temporary symlink to the main checkout's `frontend/node_modules` inside the
worktree). All results are reproducible from the current main checkout — the worktree used
for isolation has been removed and its temp branch fast-forwarded/deleted per the standard
cleanup tail.

## Fixed Issues

### CR-01: Mode-switch refusal panel shows a false "all covered" success state when the refusal is for missing demand or grid misalignment

**Files modified:** `frontend/src/pages/ShiftLibrary.tsx`
**Commit:** `10303e2`
**Applied fix:** `handleModeSwitch`'s 400-error branch now reads all four possible
`ErrorDetail.field` values (`demand`, `coverage`, `grid`, `contractedHours`) instead of only
two. `hasLiveDemand` is now derived from whether a `demand`-field detail is present (`=== null`)
rather than unconditionally forced to `true`; `misalignedTemplates` is now populated from
`grid`-field details; and a toast is shown when the refusal is specifically "no demand loaded"
so the operator sees an explicit reason rather than a silently-reverted toggle and a false
"all covered" success panel.

### WR-01: Grid-alignment re-check at mode switch includes retired (PAST-era) templates, which can block a legitimate switch over data that will never be scheduled again

**Files modified:** `src/main/java/com/wfm/service/ShiftLibraryValidationService.java`
**Commit:** `461e553`
**Applied fix:** `findMisalignedTemplates` now skips any template whose `effectiveTo` is before
`LocalDate.now()` (a retired era) before running the grid-alignment check, matching the
effective-range bounding already applied by `findUncoveredWindows`/`findUnsatisfiableWeekdays`
elsewhere in the same validator. A retired template's stale grid alignment can no longer gate a
mode switch it can no longer affect.

### WR-02: `ShiftTemplateService.createShiftTemplate` never verifies the target desk belongs to the caller's tenant

**Files modified:** `src/main/java/com/wfm/service/ShiftTemplateService.java`
**Commit:** `9180810`
**Applied fix:** Injected `DeskRepository` into `ShiftTemplateService` and added a
`deskRepository.findByIdAndTenantId(deskId, tenantId).orElseThrow(...)` ownership check at the
top of `createShiftTemplate`, mirroring the pattern `updateShiftTemplate` already uses via
`findByIdAndTenantIdAndDeskId`. A cross-tenant `deskId` now yields `EntityNotFoundException`
instead of silently creating an orphaned/cross-tenant-referencing row. Verified against the
`@DataJpaTest`-backed `ShiftTemplateServiceTest` (31/31 tests pass, real `DeskRepository`
autowired through Spring, no mock changes needed).

### WR-03: `netHours` is rounded twice at different scales, a latent double-rounding hazard for the D-07 exact-equality comparison

**Files modified:** `src/main/java/com/wfm/model/ShiftTemplate.java`
**Commit:** `e1ab41f`
**Applied fix:** `getNetHours()` now divides directly to scale 2 with `RoundingMode.HALF_UP` in
a single step, instead of rounding to scale 4 and then calling `BigDecimals.normalize` (scale 2)
a second time. Removed the now-unused `BigDecimals` import. Every consumer (`anyHoursMatch`,
`ShiftTemplateController`, both test classes) already normalizes/compares at scale 2, so this
removes the double-rounding hazard without changing observable behavior for existing tests
(`ShiftTemplateServiceTest` 31/31, `ShiftLibraryValidationServiceTest` 27/27, both pass).

### WR-04: `ShiftTemplateRepository` carries two methods with zero callers

**Files modified:** `src/main/java/com/wfm/service/ShiftTemplateService.java`,
`src/main/java/com/wfm/repository/ShiftTemplateRepository.java`,
`src/main/java/com/wfm/service/DeskService.java`
**Commit:** `d2230d8`
**Applied fix:** `validateIdentityAndNonOverlap`'s create-path identity-collision check now calls
`shiftTemplateRepository.existsByTenantIdAndDeskIdAndNameAndEffectiveFrom(...)` (a cheaper
existence query) instead of re-deriving the same fact via `findByTenantIdAndDeskIdAndName(...)` +
a stream filter; the update path (which needs to exclude the row's own id) keeps the stream-based
check since `exists()` cannot express that exclusion. Removed the genuinely dead
`deleteByTenantIdAndDeskId` method from `ShiftTemplateRepository` and added a one-line comment
in both the repository interface and `DeskService.deleteDesk` explaining that `shift_template`
is deliberately cleaned up via the DB's `ON DELETE CASCADE`, not application code, unlike its
desk-scoped table siblings. Verified via `./gradlew compileJava compileTestJava` (0 errors) and
targeted test runs (`ShiftTemplateServiceTest` 31/31, `DeskServiceSchedulingModeTest` 17/17,
`ShiftLibraryValidationServiceTest` 27/27, all pass).

## Skipped Issues

None — all in-scope findings were fixed.

---

_Fixed: 2026-08-26T12:52:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
