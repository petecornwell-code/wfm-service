---
phase: 14-shift-library-scheduling-mode
fixed_at: 2026-08-26T13:10:00Z
review_path: .planning/phases/14-shift-library-scheduling-mode/14-REVIEW.md
iteration: 1
findings_in_scope: 6
fixed: 4
skipped: 2
status: partial
---

# Phase 14: Code Review Fix Report

**Fixed at:** 2026-08-26T13:10:00Z
**Source review:** `.planning/phases/14-shift-library-scheduling-mode/14-REVIEW.md` (re-review, `reviewed: 2026-08-26T13:05:00Z`)
**Iteration:** 1

**Note:** This report covers the re-review's 6 findings (WR-05, IN-01..IN-05) and supersedes the
prior `14-REVIEW-FIX.md`, which covered the original review's CR-01/WR-01..WR-04 (all fixed in
commits `10303e2`, `461e553`, `9180810`, `e1ab41f`, `d2230d8`, and re-verified clean by this
re-review).

**Summary:**
- Findings in scope: 6 (fix_scope=all — the Warning plus all 5 Info items)
- Fixed: 4
- Skipped: 2 (both explicitly non-blocking/no-action per the review's own Fix guidance)

**Verification environment:** All fixes were applied and verified inside an isolated git worktree
(`.claude/worktrees/rf-14-*`, branch `gsd-reviewfix/14-*`, fast-forwarded onto
`claude/create-system-specification-451ge` after commit). Backend fixes (WR-05, IN-03) were
verified by running the affected Gradle test suites directly in that worktree
(`ShiftTemplateServiceTest` 32/32, `ShiftLibraryValidationServiceTest` 28/28, both 0
failures/errors — up from 31 and 27 respectively, confirming the two new regression tests execute
and pass). Frontend fixes (IN-01, IN-05) could not be `tsc --noEmit`-checked because this
worktree has no `node_modules` installed (Tier 2 unavailable for this file type here);
verification fell back to Tier 1 (re-read the modified section, confirmed the fix text is
present and surrounding JSX is intact). These frontend changes should get a `tsc -b --noEmit`
pass in the main checkout (which has `node_modules`) before this phase proceeds to the verifier.

## Fixed Issues

### WR-05: All five fixes shipped with no added or updated tests

**Files modified:** `src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java`,
`src/test/java/com/wfm/service/ShiftTemplateServiceTest.java`
**Commit:** `2f50e04`
**Applied fix:** Added `validate_retiredTemplateOffGrid_excludedFromMisalignedTemplates` (asserts
a template whose `effectiveTo` is in the past is excluded from `misalignedTemplates` even though
it is off-grid — the exact scenario the WR-01 fix covers) and
`create_crossTenantDeskId_throwsEntityNotFound` (asserts `createShiftTemplate` against a
different-tenant `deskId` throws `EntityNotFoundException` — the exact scenario the WR-02 fix
covers), using the review's suggested test bodies, adapted to the repo's existing helper
signatures (`saveDesk`, `saveTemplate`, `request`). Ran both full test classes:
`ShiftTemplateServiceTest` 32/32 passing (was 31), `ShiftLibraryValidationServiceTest` 28/28
passing (was 27), 0 failures/errors in either.

### IN-03: `isAligned` divides by `incrementMinutes` with no zero-guard

**Files modified:** `src/main/java/com/wfm/service/ShiftTemplateService.java`
**Commit:** `feb3369`
**Applied fix:** Added an early-return guard to `isAligned`: when `incrementMinutes <= 0`, treat
it the same as "no live bounds" and return `true` (skip the check) instead of falling through to
the modulo that would throw `ArithmeticException`. This single change fixes both call sites that
share the static helper (`ShiftTemplateService.addIfMisaligned` and
`ShiftLibraryValidationService.isTemplateAligned`), matching the fix suggestion's intent without
needing a separate guard at each call site. Verified via the same two test suites (still 32/32
and 28/28 passing — the guard does not change behavior for any positive `incrementMinutes`, so no
existing assertion moved).

### IN-01: `ShiftLibrary.tsx` clears the wrong field error on break-duration change

**Files modified:** `frontend/src/pages/ShiftLibrary.tsx`
**Commit:** `ee2cb48`
**Applied fix:** Added a comment directly above the break-duration `<input>`'s `onChange`
explaining that clearing `breakEndTime` (not a duration-keyed field) is deliberate, and noting
that a future server-side `breakDurationMinutes` `ErrorDetail` should be cleared here too if one
is ever added — the review's first fix option (comment, not the defensive dual-clear
alternative, since the review notes this is currently harmless and no such field key exists yet).

### IN-05: A mode-switch refusal for `coverage`/`grid` fields is silently dropped if the initial validation report has not finished loading

**Files modified:** `frontend/src/pages/ShiftLibrary.tsx`
**Commit:** `fca1587`
**Applied fix:** Replaced `handleModeSwitch`'s `prev ? {...prev, ...} : prev` no-op fallback with
the review's suggested construction of a full `ShiftLibraryValidation` object from the error's own
details every time, using `prev?.field ?? []` for fields the 400 response doesn't touch
(`hoursAdvisories`, `unsatisfiableWeekdays`, and `uncoveredWindows`/`misalignedTemplates` when the
error carried no `coverage`/`grid` details). This closes the race where a coverage/grid refusal
during the first network round trip (before `fetchValidation()` resolves) previously updated
nothing and showed no toast. **Status: fixed: requires human verification** — this is a
state-handling/timing fix (the original failure mode was silent, so no existing test would have
caught a revert), and the exact race window (click before the initial `validation` fetch
resolves) is difficult to exercise deterministically in the current test setup without a
deliberately delayed mock. A developer should manually confirm the toggle's
optimistic-revert-plus-panel-update behavior when a coverage/grid refusal lands before
`validation` has first loaded.

## Skipped Issues

### IN-02: `LocalDate.now()` (no injected `Clock`) is now used in two places, including the WR-01 fix itself

**File:** `src/main/java/com/wfm/controller/ShiftTemplateController.java:74`,
`src/main/java/com/wfm/service/ShiftLibraryValidationService.java:197`
**Reason:** No code change applied — the review's own Fix section states this explicitly:
"**Fix:** Not blocking. If a `Clock` bean is ever introduced for timezone correctness, both call
sites should be updated together so they cannot drift from each other." There is no concrete
action to take today; introducing a `Clock` bean speculatively (with no other caller needing one)
would be a larger architectural change out of scope for a targeted review-fix pass, and the two
existing call sites already agree with each other (same boundary predicate), so there is no
internal inconsistency to correct. Left for a future phase if/when a `Clock` bean is introduced.
**Original issue:** Both `eraStatus` and the WR-01-added retirement check in
`findMisalignedTemplates` compute "today" via unqualified `LocalDate.now()`; on UTC infrastructure
this can disagree with a London-based operator's wall-clock day for up to an hour around UTC
midnight during BST.

### IN-04: `CoveragePanel`'s uncovered-window list has no upper bound on rendered rows beyond a scroll box

**File:** `frontend/src/pages/ShiftLibrary.tsx:116-118`
**Reason:** No code change applied — the review's own Fix section states this explicitly:
"**Fix:** None required; note only." The review classifies this as a noted performance/UX ceiling,
not a bug, and explicitly says it is functionally correct as-is (the list scrolls within its
`maxHeight: 220px` box). Nothing to fix.
**Original issue:** `validation.uncoveredWindows.map(...)` renders one `<li>` per uncovered window
with no cap, inside a scrollable box — noted for completeness only.

---

_Fixed: 2026-08-26T13:10:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
