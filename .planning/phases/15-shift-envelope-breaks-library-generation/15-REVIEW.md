---
phase: 15-shift-envelope-breaks-library-generation
reviewed: 2026-09-03T01:32:21Z
depth: standard
files_reviewed: 39
files_reviewed_list:
  - src/main/java/com/wfm/service/SolverService.java
  - src/main/java/com/wfm/service/ScheduleOutputService.java
  - src/main/java/com/wfm/service/ScheduleService.java
  - src/main/java/com/wfm/service/ShiftLibraryValidationService.java
  - src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
  - src/main/java/com/wfm/service/ShiftTemplateService.java
  - src/main/java/com/wfm/service/ScheduleExportService.java
  - src/main/java/com/wfm/service/ConstraintWeightsService.java
  - src/main/java/com/wfm/repository/AgentRepository.java
  - src/main/java/com/wfm/repository/AgentShiftAssignmentRepository.java
  - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
  - src/main/java/com/wfm/model/AgentShiftAssignment.java
  - src/main/java/com/wfm/model/ShiftBandPair.java
  - src/main/java/com/wfm/model/AgentDayConfig.java
  - src/main/java/com/wfm/model/ShiftTemplate.java
  - src/main/java/com/wfm/model/ShiftTemplateBreakBand.java
  - src/main/java/com/wfm/model/ConstraintWeights.java
  - src/main/java/com/wfm/model/Schedule.java
  - src/main/java/com/wfm/model/ScheduleConfig.java
  - src/main/java/com/wfm/controller/ShiftLibraryValidationController.java
  - src/main/java/com/wfm/controller/ShiftTemplateController.java
  - src/main/java/com/wfm/controller/GlobalExceptionHandler.java
  - src/main/java/com/wfm/dto/ConstraintWeightsDto.java
  - src/main/java/com/wfm/dto/SolveRequest.java
  - src/main/java/com/wfm/dto/ScheduleDetailResponse.java
  - src/main/java/com/wfm/dto/ShiftLibraryValidationResponse.java
  - src/main/java/com/wfm/dto/ShiftTemplateResponse.java
  - src/main/java/com/wfm/dto/ShiftLibrarySuggestionResponse.java
  - frontend/src/pages/ScheduleResults.tsx
  - frontend/src/pages/ShiftLibrary.tsx
  - frontend/src/pages/ConstraintWeightsPage.tsx
  - frontend/src/api/client.ts
  - src/main/resources/solverConfig.xml
  - src/main/resources/db/migration/V40__shift_template_break_bands.sql
  - src/main/resources/db/migration/V42__add_band_capacity_weight.sql
  - src/main/resources/db/migration/V43__schedule_scheduling_mode.sql
  - src/main/resources/db/migration/V44__schedule_shift_envelope_slack.sql
  - src/test/java/com/wfm/support/PostgresBackedTest.java
  - src/test/java/com/wfm/repository/AgentRepositoryPostgresTest.java
findings:
  critical: 1
  warning: 2
  info: 0
  total: 3
status: issues_found
---

# Phase 15: Code Review Report

**Reviewed:** 2026-09-03T01:32:21Z
**Depth:** standard
**Files Reviewed:** 39 read in full (of the 94-file scope). Remaining files (build.gradle, V41/V45/V46 migrations, ~50 test files, `ShiftTemplateRequest`/model classes not listed above) were triaged by name/grep against the priority order given in the review brief but not individually read line-by-line; nothing suspicious surfaced in that triage.
**Status:** issues_found

## Summary

This phase adds shift-envelope scheduling (bands, seat-supply gating, envelope-compliance/contiguity/band-capacity constraints) on top of the existing slot-scheduling solver, plus a shift-library validator/generator and matching frontend surfaces. The core solver-side work (`ScheduleConstraintProvider`, `SolverService`'s seat-supply gate, `AgentShiftAssignment`'s eligibility filter, `ShiftBandPair.covers`) is unusually well-reasoned and internally consistent — most of the defects an adversarial pass would normally expect (null-unsafe `forEach` chains, double-counted day-off facts, mismatched rounding, weight-scale confusion between `shiftEnvelopeComplianceWeight` and `shiftWorkContiguityWeight`) are already the subject of extensive in-code postmortems and appear correctly fixed as described. The three fixes named in the review brief (`AgentRepository` null-`:search` CAST, `ShiftLibraryValidationService.loadHoursByWeekday` desk-default fallback, `ScheduleResults.tsx` divergence-marker gating) are all present and correctly implemented.

The defect that did surface is a **backend/frontend contract gap**: two new advisory categories the backend computes specifically to prevent live production incidents documented in this phase's own code (`breakConcentrationAdvisories`, `peakShortfallAdvisories`) are never declared in the frontend's TypeScript API types and never rendered anywhere in `ShiftLibrary.tsx`. The feature is fully built server-side and completely invisible client-side. Given "this project has NO frontend test framework," nothing would catch this short of an operator noticing the advisories never appear.

Two lower-severity gaps are also reported below (a constraint weight the UI still cannot tune, and a validated-but-silently-swallowed request field), both narrow in impact.

## Critical Issues

### CR-01: `breakConcentrationAdvisories` and `peakShortfallAdvisories` are computed by the backend but never reach the UI

**File:** `frontend/src/api/client.ts:336`, `frontend/src/pages/ShiftLibrary.tsx` (whole file)

**Issue:** `ShiftLibraryValidationResponse` (backend, `src/main/java/com/wfm/dto/ShiftLibraryValidationResponse.java:15-24`) has 8 fields, including `breakConcentrationAdvisories` and `peakShortfallAdvisories` — both new in this phase, both produced by dedicated computations in `ShiftLibraryValidationService` (`findBreakConcentrationAdvisories`, `findPeakShortfalls`). Their own javadoc documents the exact live incidents they exist to prevent:
- Break concentration: "18 of 18 Late agents on a 16:00 break simultaneously, emptied the hour... 13 hard violations that no advisory had predicted."
- Peak shortfall: "the desk ran 143 demand-hours against 200 staffed (140% coverage, every aggregate check clean) while Saturday 11:00 required 44 FTE against 25 agents on the entire desk. Short by 19 people, invisible to everything."

The frontend `ShiftLibraryValidation` TypeScript interface (`frontend/src/api/client.ts:336`) declares only 6 of the 8 fields:
```ts
export interface ShiftLibraryValidation { hasLiveDemand: boolean; uncoveredWindows: string[]; misalignedTemplates: string[]; hoursAdvisories: HoursAdvisory[]; unsatisfiableWeekdays: string[]; capacityAdvisories: CapacityAdvisory[] }
```
`breakConcentrationAdvisories` and `peakShortfallAdvisories` are absent. A repo-wide grep for both identifiers (and their PascalCase record types) under `frontend/src/` returns zero matches — nothing in `ShiftLibrary.tsx`'s `CoveragePanel`, the templates table's "Hours match"/"Capacity" advisory columns, or anywhere else reads or renders either advisory list.

**Failure scenario:** An operator builds a shift library that puts every agent's break in the same hour (the default, blank-capacity, one-band shape this migration's own comments call "the single most damaging configuration"). The backend computes a `BreakConcentrationAdvisory` for it on every save (`GET /shift-library/validation` includes it in the JSON payload), but the Shift Library page shows a clean "✓ All staffing-demand windows are covered" panel with no warning glyph anywhere, because nothing in the component tree reads that field. The operator only discovers the problem after solving, when `bandCapacity`/`shiftEnvelopeCompliance` hard violations appear — exactly the failure mode this advisory was built to head off. Same for `peakShortfallAdvisories`: a per-hour shortfall that no per-date aggregate check can see is computed and silently dropped.

**Fix:**
```ts
// frontend/src/api/client.ts
export interface BreakConcentrationAdvisory { templateId: string; templateName: string; weekday: string; bandCount: number; admissibleHeadcount: number; worstCaseSimultaneousBreak: number; message: string }
export interface PeakShortfallAdvisory { date: string; startTime: string; endTime: string; requiredFTEs: number; reachableAgents: number; shortfall: number; message: string }
export interface ShiftLibraryValidation {
  hasLiveDemand: boolean
  uncoveredWindows: string[]
  misalignedTemplates: string[]
  hoursAdvisories: HoursAdvisory[]
  unsatisfiableWeekdays: string[]
  capacityAdvisories: CapacityAdvisory[]
  breakConcentrationAdvisories: BreakConcentrationAdvisory[]
  peakShortfallAdvisories: PeakShortfallAdvisory[]
}
```
Then surface both lists in `ShiftLibrary.tsx` — e.g. a warning glyph per template row (mirroring the existing "Hours match"/"Capacity" columns) for `breakConcentrationAdvisories`, and a new panel or list item alongside `CoveragePanel` for `peakShortfallAdvisories` (which is date/hour-scoped, not template-scoped, so it needs its own rendering rather than a per-row glyph). Every three places that build a partial `ShiftLibraryValidation` object client-side (the `handleModeSwitch` 400-error reconstruction, and the draft-row `CoveragePanel` reuse) will also need the two new fields added (empty arrays are fine there, since neither refusal path currently emits them).

## Warnings

### WR-01: `contractedHoursUnderZeroWeight` remains unreadable/untunable through the constraint-weights API

**File:** `src/main/java/com/wfm/dto/ConstraintWeightsDto.java:6-32`, `src/main/java/com/wfm/service/ConstraintWeightsService.java`

**Issue:** `ConstraintWeights.java` defines its own `@ConstraintWeight("Contracted hours (under, zero)")` field (`contractedHoursUnderZeroWeight`, hard 100 by default, line 97-100) — a distinct constraint from `contractedHoursUnderWeight` that penalises an agent-day with *zero* assignments rather than a short one. This phase's own `ConstraintWeightsDto` comment states the motivating principle explicitly: the three new Phase-15 weights were added because their previous absence meant "the three constraints the shift model rests on could be neither read nor tuned through the API — directly against the intent their own migrations state ('hard-vs-soft is this column's value, never a code decision')." `contractedHoursUnderZeroWeight` sits in exactly the same class of gap (it is an equally weight-configurable hard constraint per `ConstraintWeights`'s own `@ConstraintConfiguration` design) but was not picked up in the same pass — it has no field in `ConstraintWeightsDto`, no branch in `ConstraintWeightsService.updateWeights`/`toDto`, and no row in `ConstraintWeightsPage.tsx`'s `CONSTRAINTS`/`DEFAULTS` tables.

**Failure scenario:** An operator tuning constraint weights to fix a live scheduling problem involving agents left completely unassigned for a day has no way to see or change `contractedHoursUnderZeroWeight` — the UI simply doesn't offer it, and a `PUT /constraint-weights` body cannot set it (the field is silently ignored by Jackson since the DTO doesn't declare it). This is pre-existing (predates this phase), but the file was touched in this phase specifically to close this class of gap, and it wasn't closed completely.

**Fix:** Add `contractedHoursUnderZeroWeight` to `ConstraintWeightsDto`, the `updateWeights`/`toDto` branches in `ConstraintWeightsService`, and a `CONSTRAINTS`/`DEFAULTS` row in `ConstraintWeightsPage.tsx` (default `{ hardScore: 100, softScore: 0 }`, matching `ConstraintWeights.java`'s field default), mirroring the pattern already used for the three Phase-15 weights.

### WR-02: `SolveRequest.shiftEnvelopeSlackSlots` silently falls back to the schedule default on a negative value instead of being rejected

**File:** `src/main/java/com/wfm/service/SolverService.java:539`

**Issue:** In `buildSchedule`, every other optional numeric request field is applied unconditionally when non-null (`if (request.overallocationHardLimitPct() != null) s.setOverallocationHardLimitPct(...)`, etc.) — validation of these values (if any) happens downstream in `runPreSolveValidation`. `shiftEnvelopeSlackSlots` is the one field given an inline guard against its own valid range:
```java
if (request.shiftEnvelopeSlackSlots() != null && request.shiftEnvelopeSlackSlots() >= 0) s.setShiftEnvelopeSlackSlots(request.shiftEnvelopeSlackSlots());
```
A caller who supplies a negative value (e.g. `-1`, perhaps intending "no slack" and mistyping, or a client-side bug) gets no error — the request is silently accepted and the schedule solves with the field's default (1) instead. Every other field in this method either has no range guard, or (for `incrementMinutes`) throws `IllegalArgumentException` at the top of `buildSchedule` for an out-of-range value. This field is the only one that validates inline but converts a validation failure into silent substitution rather than an error surfaced to the caller.

**Failure scenario:** An automated caller of `POST /schedules/solve` passes `shiftEnvelopeSlackSlots: -1` (e.g. a bug computing slack from some other quantity that went negative). The solve proceeds using slack=1 instead, with no indication in the response that the requested value was rejected — the operator debugging "why is my slack setting not taking effect" has no error message to find, only a schedule whose behaviour doesn't match the request they believe they sent.

**Fix:** Either validate and throw for a negative value (consistent with `incrementMinutes`'s pattern at the top of the method):
```java
if (request.shiftEnvelopeSlackSlots() != null) {
    if (request.shiftEnvelopeSlackSlots() < 0) {
        throw new IllegalArgumentException("shiftEnvelopeSlackSlots must be >= 0");
    }
    s.setShiftEnvelopeSlackSlots(request.shiftEnvelopeSlackSlots());
}
```
or, if silent clamping is intentional, document why inline (the current comment only explains the *default*, not the negative-value behaviour).

---

_Reviewed: 2026-09-03T01:32:21Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
