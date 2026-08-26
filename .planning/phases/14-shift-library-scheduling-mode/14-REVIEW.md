---
phase: 14-shift-library-scheduling-mode
reviewed: 2026-08-26T01:48:39Z
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
  critical: 1
  warning: 4
  info: 4
  total: 9
status: issues_found
---

# Phase 14: Code Review Report

**Reviewed:** 2026-08-26T01:48:39Z
**Depth:** deep
**Files Reviewed:** 28
**Status:** issues_found

## Summary

Phase 14 is a well-executed, disciplined implementation of the shift library / scheduling-mode
slice. The high-value structural checks called out in the review brief hold up:

- **Solver untouched.** `git diff --name-only 823c193..HEAD -- src/main/java/com/wfm/solver/
  src/main/resources/solverConfig.xml` is empty. `ScheduleConstraintClassification`/`Test` live
  under `src/test/`, are read-only against `ScheduleConstraintProvider`/`ConstraintWeights`, and
  enforce completeness via two independent reflective derivations rather than a hand-maintained
  count — a genuinely good piece of engineering.
- **Flyway forward-only.** V38 is byte-identical; V39 is the only new migration. The
  `shift_template` DDL and the `ShiftTemplate` entity mapping agree column-for-column (name,
  type, nullability, the `(tenant_id, desk_id, name, effective_from)` unique key, the `CHAR(7)`
  weekday mask). This is the check the brief flagged as highest-value, and it holds — I could not
  find a disagreement that would ship green (tests never exercise V39) and break in production.
- **No second retirement mechanism.** No `is_active`/`enabled`/`retired` field exists anywhere on
  `ShiftTemplate`; the migration and entity comments both explicitly call out why, and no created
  method (`delete`, `retire`) exists on the service or controller.
- **One validator, two callers.** `ShiftLibraryValidationService.validate` is the single coverage
  computation; `requireShiftModeReady` and `ShiftLibraryValidationController` both call it (the
  latter indirectly via `validate`, the former directly), and `DeskService.switchSchedulingMode`
  calls `requireShiftModeReady`. No duplicated logic.
- **Inclusive effective-range boundaries.** `validateIdentityAndNonOverlap`'s overlap predicate
  (`!candidateFrom.isAfter(otherTo) && !otherFrom.isAfter(candidateTo)`, `LocalDate.MAX` sentinel
  for open-ended) is correct: touching eras coexist, one-day overlaps collide, both are covered by
  tests with the exact boundary dates named in the review brief.
- **Exact-equality hours matching.** `BigDecimals.normalize(...).compareTo(...) == 0` is used
  throughout, never `.equals()` — the classic `8.0` vs `8.00` scale trap is avoided.
- **Frontend styling/interaction contract.** No `#ef4444`/destructive red anywhere in
  `ShiftLibrary.tsx`; no `confirm()` dialog on the mode toggle or the retire flow; `eraStatus` and
  the coverage/hours-advisory verdicts are read from server responses, never recomputed
  client-side; the mode toggle is optimistic-with-revert on error.

Despite that, there is one genuine functional bug in the frontend's mode-switch error handling
that actively misleads the operator about *why* a switch was refused — directly undermining the
phase's own stated thesis ("problems are reported at definition time, never discovered by a
solve") in the one moment that thesis is supposed to be proven. There are also a few real, if
lower-severity, gaps: a grid-alignment check that can block a mode switch over a template nobody
will ever use again, a tenant-scoping gap on shift-template creation (mirrors a pre-existing
pattern elsewhere in the codebase, not new to this phase, but real), and some dead repository
code.

## Critical Issues

### CR-01: Mode-switch refusal panel shows a false "all covered" success state when the refusal is for missing demand or grid misalignment

**File:** `frontend/src/pages/ShiftLibrary.tsx:355-372` (specifically 357-366)
**Issue:**

`handleModeSwitch`'s 400-error branch only reads two of the four possible `ErrorDetail.field`
values `ShiftLibraryValidationService.requireShiftModeReady` can emit (`coverage`,
`contractedHours` — see `src/main/java/com/wfm/service/ShiftLibraryValidationService.java:98-108`,
which also emits `demand` and `grid`):

```javascript
if (err instanceof ApiRequestError && err.status === 400) {
  const refusalWindowMessages = err.details.filter(d => d.field === 'coverage').map(d => d.message)
  const hoursMessage = err.details.find(d => d.field === 'contractedHours')?.message ?? null
  setValidation(prev => (prev
    ? { ...prev, hasLiveDemand: true, uncoveredWindows: refusalWindowMessages.length > 0 ? refusalWindowMessages : prev.uncoveredWindows }
    : prev))
  setModeSwitchHoursError(hoursMessage)
}
```

Two concrete failure paths:

1. **Refusal is "no staffing demand loaded" (`field: "demand"`).** `hasLiveDemand` is
   unconditionally forced to `true` regardless of the server's actual finding. Since
   `uncoveredWindows` has no `coverage`-field entries to filter, it falls back to
   `prev.uncoveredWindows`, which is empty (a desk with no live demand always has an empty
   `uncoveredWindows` list — see `ShiftLibraryValidationService.validate`, `findUncoveredWindows`
   iterates an empty `demand` list). `misalignedTemplates` is untouched (also empty in `prev`).
   `extraLine` is `null` (no `contractedHours` detail present). Result:
   `CoveragePanel`'s `hasProblems` evaluates to `false`, and the panel renders **"✓ All
   staffing-demand windows are covered by the current shift library."** — immediately after the
   server refused the switch specifically because there is *no demand data to check coverage
   against at all*. No toast is shown for the 400 branch either, so the operator sees the mode
   toggle silently revert with a green checkmark and no visible explanation.
2. **Refusal is grid misalignment only (`field: "grid"`)** — a demand-covered desk whose only
   problem is a template whose times no longer land on the (possibly regenerated) timeslot grid.
   Same mechanism: no `coverage`/`contractedHours` details exist to surface the `grid` details, so
   the panel again renders the "all covered" success message while masking the actual, named
   reason (`ShiftTemplateService`'s own grid-alignment messages) the switch was refused.

This directly contradicts the phase's design thesis (14-CONTEXT.md: "problems are *reported at
definition time*, never *discovered by a solve*") at the exact moment — a real mode-switch
refusal — that thesis is supposed to hold. An operator reading this panel has no way to discover
why their switch failed short of opening devtools or re-reading the (unrelated) toast text from
`getErrorMessage(err)`, which is never called in this branch.

**Fix:** Handle all four `ErrorDetail.field` values the server can emit, and never force
`hasLiveDemand: true` — derive it from whether a `demand`-field detail is present:

```javascript
if (err instanceof ApiRequestError && err.status === 400) {
  const demandMessage = err.details.find(d => d.field === 'demand')?.message ?? null
  const refusalWindowMessages = err.details.filter(d => d.field === 'coverage').map(d => d.message)
  const gridMessages = err.details.filter(d => d.field === 'grid').map(d => d.message)
  const hoursMessage = err.details.find(d => d.field === 'contractedHours')?.message ?? null
  setValidation(prev => (prev
    ? {
        ...prev,
        hasLiveDemand: demandMessage === null,
        uncoveredWindows: refusalWindowMessages.length > 0 ? refusalWindowMessages : prev.uncoveredWindows,
        misalignedTemplates: gridMessages.length > 0 ? gridMessages : prev.misalignedTemplates,
      }
    : prev))
  setModeSwitchHoursError(hoursMessage)
  if (demandMessage) showToast('error', demandMessage)
}
```

## Warnings

### WR-01: Grid-alignment re-check at mode switch includes retired (PAST-era) templates, which can block a legitimate switch over data that will never be scheduled again

**File:** `src/main/java/com/wfm/service/ShiftLibraryValidationService.java:191-203`
**Issue:** `findMisalignedTemplates` iterates every template `shiftTemplateRepository
.findByTenantIdAndDeskId` returns for the desk — every era, with no effective-range filter:

```java
private List<String> findMisalignedTemplates(UUID deskId, List<ShiftTemplate> templates) {
    ...
    for (ShiftTemplate template : templates) {
        if (!isTemplateAligned(template, bounds.get())) {
            misaligned.add(template.getName() + " (" + template.getEffectiveFrom() + ")");
        }
    }
    return misaligned;
}
```

Contrast with `findUncoveredWindows`/`covers`, which correctly bounds every check by
`withinEffectiveRange(template, window.date())`, and `findUnsatisfiableWeekdays`, which bounds by
`demandedDates.stream().anyMatch(d -> withinEffectiveRange(t, d))`. Grid alignment is the one
check in this validator with no date/era bound at all.

Since D-02's own rationale is that the timeslot grid can be regenerated at a different
`incrementMinutes` after a template was saved ("the grid can be refined later, so alignment is
not a constant"), a template that is legitimately retired (`effectiveTo` in the past, `eraStatus
= PAST`) — and therefore will never again be used to satisfy demand — can still fail this check
today and, via `requireShiftModeReady`/`addGridDetails`, block a mode switch to SHIFT on a desk
whose *currently active* library is perfectly fine. There is no test exercising a
retired-and-now-misaligned template against the mode-switch gate (`ShiftLibraryValidationServiceTest`'s
misalignment tests only cover a single, currently-effective template).

**Fix:** Filter `findMisalignedTemplates` (or its caller) to templates whose effective range could
still apply to a live demand date — e.g. reuse the same `withinEffectiveRange` bound already
applied to coverage, restricted to today-or-later (or to the union of live demand dates), so a
retired era's stale alignment cannot gate a switch it can no longer affect:

```java
LocalDate today = LocalDate.now();
for (ShiftTemplate template : templates) {
    if (template.getEffectiveTo() != null && template.getEffectiveTo().isBefore(today)) {
        continue; // retired — cannot be scheduled again, its alignment is moot
    }
    if (!isTemplateAligned(template, bounds.get())) {
        misaligned.add(template.getName() + " (" + template.getEffectiveFrom() + ")");
    }
}
```

### WR-02: `ShiftTemplateService.createShiftTemplate` never verifies the target desk belongs to the caller's tenant

**File:** `src/main/java/com/wfm/service/ShiftTemplateService.java:56-66`
**Issue:** `createShiftTemplate(UUID deskId, ShiftTemplateRequest request)` sets
`template.setTenantId(tenantId)` (the caller's own tenant) and `template.setDeskId(deskId)`
(caller-supplied, unchecked) and saves — it never calls `deskRepository.findByIdAndTenantId(deskId,
tenantId)` or otherwise confirms `deskId` belongs to the requesting tenant. A caller from tenant A
who knows (or guesses) tenant B's `deskId` can create a `shift_template` row with
`tenant_id = A, desk_id = <tenant B's desk>`. Every subsequent read is still correctly
double-filtered by `(tenantId, deskId)` so this does not leak tenant B's data back to tenant A or
vice versa, but it is a real data-integrity gap: an orphaned/cross-tenant-referencing row that no
query will ever surface to either tenant cleanly, and that silently pollutes the counts any
future admin tooling computes per desk.

This exact gap pre-exists in `SpecializationService.createSpecialization` (same pattern, same
missing check) and is not introduced by this phase — `ShiftTemplateService` mirrors an existing
weakness rather than inventing a new one. Flagged because the review brief calls out tenant
scoping as the single highest-priority security check for this phase, and a new desk-scoped
write surface should not carry the gap forward uncorrected, even if it wasn't this phase's job to
fix the original.

**Fix:** Verify desk ownership before create, mirroring the pattern `updateShiftTemplate` already
uses via `findByIdAndTenantIdAndDeskId`:

```java
deskRepository.findByIdAndTenantId(deskId, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));
```

(Requires injecting `DeskRepository` into `ShiftTemplateService`.)

### WR-03: `netHours` is rounded twice at different scales, a latent double-rounding hazard for the D-07 exact-equality comparison

**File:** `src/main/java/com/wfm/model/ShiftTemplate.java:144-154`
**Issue:**

```java
BigDecimal hours = BigDecimal.valueOf(netMinutes).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
return BigDecimals.normalize(hours); // setScale(2, HALF_UP) on the already-scale-4 value
```

`getNetHours()` rounds to 4 decimal places, and every caller that needs the exact-equality
comparison (`ShiftLibraryValidationService.anyHoursMatch`) then rounds that result to 2 decimal
places via `BigDecimals.normalize`. For the grid increments this codebase actually generates
(typically 15/30/60-minute), the current values happen not to produce a divergent result (verified
by hand for the boundary case: `netMinutes % 60` giving a repeating `.1666...`/`.8333...`
fraction rounds identically whether done directly to scale 2 or via the scale-4 intermediate).
But this is incidental to the current grid increments, not guaranteed by the code — a future grid
increment (or `break_duration_minutes` set to a value not a multiple of the desk's increment,
which nothing here prevents for a template with `breakDurationMinutes == 0`, i.e. no grid check
applies to duration at all) could produce a `netMinutes` value whose direct-to-scale-2 rounding
disagrees with its round-to-4-then-round-to-2 result, silently changing which agents' contracted
hours a template is reported to "match".

**Fix:** Compute `netHours` directly to scale 2 in one rounding step (`divide(BigDecimal.valueOf(60),
2, RoundingMode.HALF_UP)`), or make the double-rounding explicit and intentional with a comment if
scale-4 precision is needed elsewhere (it currently is not — every consumer normalizes to scale 2
before comparing).

### WR-04: `ShiftTemplateRepository` carries two methods with zero callers

**File:** `src/main/java/com/wfm/repository/ShiftTemplateRepository.java:19,23`
**Issue:** `existsByTenantIdAndDeskIdAndNameAndEffectiveFrom` and `deleteByTenantIdAndDeskId` are
declared but never invoked anywhere in `src/main` or `src/test` (confirmed by
`grep -rn` across the whole tree — only their own declarations match). The identity-collision
check in `ShiftTemplateService.validateIdentityAndNonOverlap` re-derives the same fact via
`findByTenantIdAndDeskIdAndName` + a stream filter instead of using the `exists` method; the
delete method has no caller because `shift_template`'s FK is `ON DELETE CASCADE` at the DB level
(`DeskService.deleteDesk` relies on that cascade rather than an explicit repository call, unlike
every other desk-scoped table it cleans up).

**Fix:** Remove both unused methods, or use `existsByTenantIdAndDeskIdAndNameAndEffectiveFrom` in
place of the `findByTenantIdAndDeskIdAndName(...).stream().anyMatch(...)` identity check for a
cheaper existence query. If the `ON DELETE CASCADE` reliance for `deleteDesk` is intentional
(reasonable, since every other desk-scoped table's cascade is done in application code, not DB
`ON DELETE CASCADE`), a one-line comment at `DeskService.deleteDesk` noting that `shift_template`
is deliberately the one table cleaned up at the DB level, not here, would keep the inconsistency
from reading as an oversight to the next reader.

## Info

### IN-01: `ShiftLibrary.tsx` clears the wrong field error on break-duration change

**File:** `frontend/src/pages/ShiftLibrary.tsx:412`
**Issue:** The "Break duration (minutes)" input's `onChange` calls `clearFieldError('breakEndTime')`
rather than clearing any error keyed on the duration field itself. This happens to be defensible
(duration changes shift where `breakEndTime` lands, so a stale `breakEndTime` misalignment error
is the right one to invalidate), but the server never emits a `breakDurationMinutes`-or similarly
named field key for the negative/envelope-exceeding break checks in
`ShiftTemplateService.validate` (those throw `IllegalArgumentException`, which
`GlobalExceptionHandler` maps to 400 with an empty `details` array, so `applyErrorResponse` falls
through to a toast, never a field-level message) — so there is currently no server-side error this
field could receive and fail to clear. Low-risk today, but worth a short comment so a future
`ErrorDetail("breakDurationMinutes", ...)` addition doesn't get silently swallowed by this
mismatch.

**Fix:** Add a one-line comment above the `onChange` explaining the `breakEndTime` clear target is
deliberate (duration recompute invalidates `breakEndTime`'s alignment), or clear both keys
defensively.

### IN-02: `eraStatus`/`LocalDate.now()` uses the server's default timezone, not a fixed clock

**File:** `src/main/java/com/wfm/controller/ShiftTemplateController.java:73-82`,
`src/main/java/com/wfm/service/ShiftLibraryValidationService.java` (none directly, but
`findMisalignedTemplates`' proposed fix in WR-01 would introduce the same pattern)
**Issue:** `eraStatus` computes "today" via `LocalDate.now()` with no injected `Clock`/timezone.
On ECS Fargate this is very likely UTC; the operator base is London (`eu-west-2`). Around
midnight UK time (23:00–01:00 UTC during BST), a template's `eraStatus` could read one day off
from what a London-based operator expects, and — combined with WR-01's proposed fix — could also
affect what counts as "retired" for the grid re-check. This is a pattern already used elsewhere
in the codebase (not introduced by this phase) and is not itself a phase-14 regression, but it is
new *exposure* of that pattern (a brand-new UI surface computing day boundaries) worth a note.
**Fix:** Not blocking for this phase; worth tracking alongside any future timezone-correctness
pass. No action required here beyond awareness.

### IN-03: `isAligned` divides by `incrementMinutes` with no zero-guard

**File:** `src/main/java/com/wfm/service/ShiftTemplateService.java:185-188`
**Issue:**

```java
static boolean isAligned(LocalTime gridStart, int incrementMinutes, LocalTime candidate) {
    long diffMinutes = ChronoUnit.MINUTES.between(gridStart, candidate);
    return diffMinutes >= 0 && diffMinutes % incrementMinutes == 0;
}
```

If `TimeslotGeneratorService.getLiveBounds` ever returned `incrementMinutes == 0` (e.g. a desk
whose live timeslots all share an identical `start_time`/`end_time`, which the native
`MIN(EXTRACT(EPOCH FROM (end_time - start_time))/60)` query would produce as `0` rather than
`null`), this throws `ArithmeticException` on every save and on every mode-switch attempt for that
desk, instead of the intended validation-failure path. No test exercises `incrementMinutes == 0`.
Low probability (requires zero-duration live timeslots, which nothing in this phase creates), but
worth a defensive guard given the blast radius (an uncaught `ArithmeticException` is a 500, not a
400).
**Fix:** Treat `incrementMinutes <= 0` the same as "no live bounds" (skip the grid check) rather
than dividing by it.

### IN-04: `CoveragePanel`'s uncovered-window list has no upper bound on rendered rows beyond a scroll box

**File:** `frontend/src/pages/ShiftLibrary.tsx:113-119`
**Issue:** `validation.uncoveredWindows.map(...)` renders one `<li>` per uncovered window with no
cap; a desk with a large demand period and a sparse library could produce hundreds of rows inside
the `maxHeight: 220px, overflowY: auto` box. Functionally correct (scrolls), but worth noting as a
usability ceiling — not a bug, out of scope per the review's performance exclusion, listed here
only because it borders on quality/maintainability for an operator-facing failure list that is the
phase's own centerpiece UX.
**Fix:** None required; note only.

---

_Reviewed: 2026-08-26T01:48:39Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
