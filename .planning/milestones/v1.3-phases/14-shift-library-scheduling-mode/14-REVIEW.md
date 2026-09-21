---
phase: 14-shift-library-scheduling-mode
reviewed: 2026-08-26T13:05:00Z
depth: deep
files_reviewed: 28
files_reviewed_list:
  - frontend/src/App.tsx
  - frontend/src/api/client.ts
  - frontend/src/pages/DeskAgents.tsx
  - frontend/src/pages/DeskManagement.tsx
  - frontend/src/pages/ShiftLibrary.tsx
  - src/main/java/com/wfm/controller/DeskController.java
  - src/main/java/com/wfm/controller/ShiftLibraryValidationController.java
  - src/main/java/com/wfm/controller/ShiftTemplateController.java
  - src/main/java/com/wfm/dto/DeskResponse.java
  - src/main/java/com/wfm/dto/SchedulingModeRequest.java
  - src/main/java/com/wfm/dto/ShiftLibraryValidationResponse.java
  - src/main/java/com/wfm/dto/ShiftTemplateRequest.java
  - src/main/java/com/wfm/dto/ShiftTemplateResponse.java
  - src/main/java/com/wfm/model/Desk.java
  - src/main/java/com/wfm/model/SchedulingMode.java
  - src/main/java/com/wfm/model/ShiftTemplate.java
  - src/main/java/com/wfm/repository/ShiftTemplateRepository.java
  - src/main/java/com/wfm/repository/StaffingRequirementRepository.java
  - src/main/java/com/wfm/service/DeskService.java
  - src/main/java/com/wfm/service/ShiftLibraryValidationService.java
  - src/main/java/com/wfm/service/ShiftTemplateService.java
  - src/main/resources/db/migration/V39__add_shift_template_and_scheduling_mode.sql
  - src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java
  - src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java
  - src/test/java/com/wfm/service/ShiftTemplateServiceTest.java
  - src/test/java/com/wfm/service/ShiftTemplateTracerTest.java
  - src/test/java/com/wfm/solver/ScheduleConstraintClassification.java
  - src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java
findings:
  critical: 0
  warning: 1
  info: 5
  total: 6
status: issues_found
---

# Phase 14: Code Review Report (Re-review)

**Reviewed:** 2026-08-26T13:05:00Z
**Depth:** deep
**Files Reviewed:** 28
**Status:** issues_found

## Summary

This is a re-review of the same 28-file scope covered by the prior deep review
(`abf65a2:.planning/phases/14-shift-library-scheduling-mode/14-REVIEW.md`, 9 findings: CR-01,
WR-01..WR-04, IN-01..IN-04). Five fixes landed since then (commits `10303e2`, `461e553`,
`9180810`, `e1ab41f`, `d2230d8`), one per finding for CR-01 and WR-01..WR-04. All five were
verified by direct code reading against the original finding text, and by running the full
affected test suite (`ShiftTemplateServiceTest` 31/31, `ShiftLibraryValidationServiceTest` 27/27,
`DeskServiceSchedulingModeTest` 17/17, `ShiftTemplateTracerTest` 6/6,
`ScheduleConstraintClassificationTest` 6/6 — all green, 0 failures/errors) and `tsc -b --noEmit`
(clean) for the frontend fix.

**Fix verification results — all five resolve their finding correctly, no regressions found:**

- **CR-01 (fixed):** `ShiftLibrary.tsx`'s `handleModeSwitch` 400-branch (now lines 357-382) reads
  all four `ErrorDetail.field` values the server can emit (`demand`, `coverage`, `grid`,
  `contractedHours`), derives `hasLiveDemand` from whether a `demand`-field detail is present
  instead of forcing it `true`, and folds `grid` details into `misalignedTemplates`. Verified
  against `ShiftLibraryValidationService.requireShiftModeReady` (lines 94-116), which confirms
  `demand` is mutually exclusive with `coverage`/`grid`/`contractedHours` (the `else` branch at
  line 100 only runs when demand exists), so the frontend's field-presence logic cannot see a
  contradictory combination. `tsc -b --noEmit` is clean.
- **WR-01 (fixed):** `ShiftLibraryValidationService.findMisalignedTemplates` (lines 191-207) now
  skips any template whose `effectiveTo` is before today before running the grid-alignment check.
  The boundary condition (`effectiveTo == today` still checked, `effectiveTo < today` skipped)
  matches `ShiftTemplateController.eraStatus`'s PAST predicate exactly (`today.isAfter(effectiveTo)`
  ⇔ `effectiveTo.isBefore(today)`), so a retired template can no longer block a mode switch, and
  the "retired" boundary the fix uses agrees with the "PAST" boundary the UI shows elsewhere —
  no new era-status disagreement introduced.
- **WR-02 (fixed):** `ShiftTemplateService.createShiftTemplate` (lines 60-72) now calls
  `deskRepository.findByIdAndTenantId(deskId, tenantId).orElseThrow(...)` before validating and
  saving, closing the cross-tenant desk-scoping gap. `DeskRepository` is now injected via the
  constructor (line 36-44).
- **WR-03 (fixed):** `ShiftTemplate.getNetHours()` (lines 143-158) now rounds directly to scale 2
  in one `divide(..., 2, RoundingMode.HALF_UP)` call — the scale-4 intermediate is gone, closing
  the double-rounding hazard.
- **WR-04 (fixed):** `ShiftTemplateRepository.deleteByTenantIdAndDeskId` is removed (replaced with
  an explanatory comment, lines 23-25); `existsByTenantIdAndDeskIdAndNameAndEffectiveFrom` is now
  actually used by `ShiftTemplateService.validateIdentityAndNonOverlap`'s create-path identity
  check (lines 210-212); `DeskService.deleteDesk` carries a matching comment (lines 156-157)
  explaining the DB-level `ON DELETE CASCADE` reliance. No unused repository methods remain.

None of the five fix commits added or modified a test. I re-ran the pre-existing suites for every
touched file and all pass, but there is no test that would fail if WR-01's retired-template
exclusion or WR-02's tenant check were reverted — see WR-05 below.

Three of the four Info items from the prior review are still present and are restated below with
current line numbers (IN-01, IN-03, IN-04). IN-02 is restated and is now measurably more relevant,
since the WR-01 fix added a second `LocalDate.now()` call to the exact validator this finding
warned about. One new Info item (IN-05) was found in this pass.

## Warnings

### WR-05: All five fixes shipped with no added or updated tests

**File:** `src/main/java/com/wfm/service/ShiftLibraryValidationService.java:191-207` (WR-01),
`src/main/java/com/wfm/service/ShiftTemplateService.java:60-72` (WR-02) — representative of all
five fix commits
**Issue:** `git show --stat` on each of `10303e2`, `461e553`, `9180810`, `e1ab41f`, `d2230d8`
touches only production source files — zero test files across all five commits. Concretely:

- `ShiftLibraryValidationServiceTest` has grid-alignment tests
  (`validate_boundsPresent_offGridTemplate_appearsInMisalignedTemplates`,
  `requireShiftModeReady_offGridTemplate_withLiveDemand_throwsWithGridDetail`) but none constructs
  a template whose `effectiveTo` is in the past and asserts it is excluded from
  `misalignedTemplates` — the exact scenario WR-01 fixes. Reverting the fix's three added lines
  would not fail any existing test.
- `ShiftTemplateServiceTest` has a cross-tenant test
  (`crossTenant_invisibleToListing_updateThrowsEntityNotFound`) that covers `updateShiftTemplate`,
  but no test creates a template against a `deskId` belonging to a different tenant and asserts
  `EntityNotFoundException` from `createShiftTemplate` — the exact scenario WR-02 fixes. Reverting
  WR-02's `deskRepository.findByIdAndTenantId` check would not fail any existing test.

This codebase's own test style for this phase is explicitly RED-first/TDD (see
`ShiftTemplateTracerTest`'s class Javadoc: "Written RED-first per the plan's tdd=\"true\" flag").
Shipping five bug fixes with zero regression coverage is inconsistent with that stated standard
and leaves both fixes free to silently regress on a future refactor of either method.

**Fix:** Add one regression test per fix:
```java
// ShiftLibraryValidationServiceTest
@Test
void validate_retiredTemplateOffGrid_excludedFromMisalignedTemplates() {
    UUID deskId = saveDesk(TENANT_A);
    saveTemplate(deskId, "S1", LocalTime.of(8, 15), LocalTime.of(17, 0), 0, 0,
            Set.of(DayOfWeek.MONDAY), LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31));
    when(timeslotGeneratorService.getLiveBounds(deskId)).thenReturn(Optional.of(
            new TimeslotBoundsResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                    LocalTime.of(8, 0), LocalTime.of(20, 0), 30)));

    ShiftLibraryValidationResponse response = service.validate(deskId);

    assertThat(response.misalignedTemplates()).isEmpty();
}

// ShiftTemplateServiceTest
@Test
void create_crossTenantDeskId_throwsEntityNotFound() {
    UUID deskId = saveDesk(TENANT_B);
    assertThatThrownBy(() -> service.createShiftTemplate(deskId, request("S1", LocalDate.of(2026, 1, 1), null)))
            .isInstanceOf(EntityNotFoundException.class);
}
```

## Info

### IN-01: `ShiftLibrary.tsx` clears the wrong field error on break-duration change

**File:** `frontend/src/pages/ShiftLibrary.tsx:422`
**Issue:** Still present, unchanged from the prior review (line moved from 412 to 422 due to
the CR-01 fix's insertions earlier in the file). The "Break duration (minutes)" input's `onChange`
calls `clearFieldError('breakEndTime')` rather than clearing an error keyed on the duration field
itself:
```javascript
<input type="number" min="0" value={f.breakDurationMinutes} onChange={e => { setF(prev => ({ ...prev, breakDurationMinutes: e.target.value })); clearFieldError('breakEndTime') }} style={{ width: '80px' }} />
```
Defensible today (duration changes shift where `breakEndTime` lands, so invalidating a stale
`breakEndTime` error is reasonable) and currently harmless (no server-side `breakDurationMinutes`
field key exists to be silently swallowed by this mismatch — `ShiftTemplateService.validate`'s
negative/envelope checks throw `IllegalArgumentException`, which carries no `details`). Still
worth a one-line comment so a future `ErrorDetail("breakDurationMinutes", ...)` addition doesn't
get silently dropped by this clear target.
**Fix:** Add a comment above the `onChange` explaining the `breakEndTime` clear target is
deliberate, or clear both keys defensively.

### IN-02: `LocalDate.now()` (no injected `Clock`) is now used in two places, including the WR-01 fix itself

**File:** `src/main/java/com/wfm/controller/ShiftTemplateController.java:74` (`eraStatus`),
`src/main/java/com/wfm/service/ShiftLibraryValidationService.java:197` (`findMisalignedTemplates`,
added by the WR-01 fix)
**Issue:** Both call sites compute "today" via `LocalDate.now()` with no injected
`Clock`/timezone. On ECS Fargate this is very likely UTC; the operator base is London
(`eu-west-2`). The WR-01 fix (commit `461e553`) introduced a *second* independent `LocalDate.now()`
call into exactly the validator this finding originally flagged as exposed by any future use of
"today" for retirement logic — the prediction in the prior review's IN-02 has now materialized.
The two call sites use the same boundary predicate (`effectiveTo.isBefore(today)` /
`today.isAfter(effectiveTo)` are equivalent), so they cannot disagree with each other, but both
can disagree with a London-based operator's wall-clock expectation around midnight UK time
(23:00-01:00 UTC during BST) — e.g. a template retiring "today" per the UI's `eraStatus` could
still gate (or fail to gate) a mode switch differently than the operator expects for up to an
hour, depending on which side of UTC midnight the request lands.
**Fix:** Not blocking. If a `Clock` bean is ever introduced for timezone correctness, both call
sites should be updated together so they cannot drift from each other.

### IN-03: `isAligned` divides by `incrementMinutes` with no zero-guard

**File:** `src/main/java/com/wfm/service/ShiftTemplateService.java:191-194`
**Issue:** Still present, unchanged from the prior review (line moved from 185-188 to 191-194 due
to the WR-02 fix's constructor/field insertions earlier in the file):
```java
static boolean isAligned(LocalTime gridStart, int incrementMinutes, LocalTime candidate) {
    long diffMinutes = ChronoUnit.MINUTES.between(gridStart, candidate);
    return diffMinutes >= 0 && diffMinutes % incrementMinutes == 0;
}
```
If `TimeslotGeneratorService.getLiveBounds` ever returned `incrementMinutes == 0`, this throws
`ArithmeticException` (a 500) on every save and every mode-switch attempt for that desk, instead
of the intended validation-failure path (400). No test exercises `incrementMinutes == 0`.
**Fix:** Treat `incrementMinutes <= 0` the same as "no live bounds" (skip the grid check).

### IN-04: `CoveragePanel`'s uncovered-window list has no upper bound on rendered rows beyond a scroll box

**File:** `frontend/src/pages/ShiftLibrary.tsx:116-118`
**Issue:** Still present, unchanged from the prior review (line moved from 113-119 to 116-118).
`validation.uncoveredWindows.map(...)` renders one `<li>` per uncovered window with no cap inside
a `maxHeight: 220px, overflowY: auto` box. Functionally correct (scrolls); noted only for
completeness, not requiring action (performance/UX ceiling, not a bug).
**Fix:** None required; note only.

### IN-05: A mode-switch refusal for `coverage`/`grid` fields is silently dropped if the initial validation report has not finished loading

**File:** `frontend/src/pages/ShiftLibrary.tsx:367-374`
**Issue:** `handleModeSwitch`'s 400-branch guards its `setValidation` call with
`prev ? { ... } : prev` — if the initial `fetchValidation()` call (fired in the mount `useEffect`,
line 168) has not yet resolved when the operator clicks the mode-switch button, `validation` is
still `null`, and the update is a no-op:
```javascript
setValidation(prev => (prev
  ? { ...prev, hasLiveDemand: demandMessage === null, uncoveredWindows: ..., misalignedTemplates: ... }
  : prev))
```
The mode-switch button itself is only gated by `switchingMode` (line 599/608's `disabled`
attribute), not by whether `validation` has loaded, so this race is reachable: an operator who
clicks "Shift-scheduled" within the first network round trip of landing on the page, and whose
switch is refused for a `coverage`- or `grid`-field reason (not `demand`), sees the toggle
optimistically flip and then silently revert with no toast and no panel update — the same
category of "silent refusal" CR-01 fixed, reintroduced through a narrower timing window. (A
`demand`-field refusal is unaffected — its toast at line 376 fires unconditionally.)
**Fix:** Fall back to constructing a minimal validation object from the error details when `prev`
is `null`, rather than dropping the update:
```javascript
setValidation(prev => ({
  hasLiveDemand: demandMessage === null,
  uncoveredWindows: refusalWindowMessages.length > 0 ? refusalWindowMessages : (prev?.uncoveredWindows ?? []),
  misalignedTemplates: gridMessages.length > 0 ? gridMessages : (prev?.misalignedTemplates ?? []),
  hoursAdvisories: prev?.hoursAdvisories ?? [],
  unsatisfiableWeekdays: prev?.unsatisfiableWeekdays ?? [],
}))
```

---

_Reviewed: 2026-08-26T13:05:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
