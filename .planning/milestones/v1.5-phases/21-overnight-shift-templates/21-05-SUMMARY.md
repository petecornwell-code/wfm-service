---
phase: 21-overnight-shift-templates
plan: 05
subsystem: api
tags: [java, spring, shift-templates, day-window, shift-library-generation]

# Dependency graph
requires:
  - phase: 21-overnight-shift-templates (plan 21-01)
    provides: "ShiftTemplateService.validate's forward-interval refusal, DayWindow.anchoredIsForwardWithinDay/anchoredContains/dayStart(), and the MAX_SPAN_MINUTES cap this plan's new checks are ordered alongside"
provides:
  - "ShiftTemplateService.isWithinOperatingWindow(TimeslotBoundsResponse, LocalTime, LocalTime, DayWindow): the single shared containment predicate against a desk's real generated operating window, [bounds.startTime(), bounds.endTime()]"
  - "ShiftTemplateService.crossesCalendarMidnight(LocalTime, LocalTime): the blocking/advisory split key (OVNT-05/D-07)"
  - "ShiftTemplateService.validate's save-path refusal: an overnight (calendar-midnight-crossing) envelope outside the operating window throws IllegalArgumentException; a same-day one still saves"
  - "ShiftLibraryValidationResponse.OperatingWindowFinding/operatingWindowFindings: the report's new finding list, carrying the blocking/non-blocking split"
  - "ShiftLibraryValidationService.findOperatingWindowEscapes, and the operatingWindow ErrorDetail requireShiftModeReady throws for blocking findings"
  - "ShiftLibraryGenerationService.enumerateCandidates: a third rejection condition (D-10) filtering every candidate against the shared predicate, and the anchored (not clock-order) derivation of earliestStart/latestStart (P-03)"
affects: [21-06, 21-09, 21-10, 21-11, 21-12]

# Actuals (#2632)
actuals:
  tokens: 12064
  tasks: 3
  commits: 6
  plan_head_before: e2bce87a53c2b5f8bdaf5ee7c9e3f21e42c4b0d5
  plan_head_after: 34a1587d9b140c9a69a4e9d9f86d9c027d1f762c

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "One containment predicate, three callers (save path, shift-library report/gate, candidate generation) -- a cross-class sharing shape this file already used for isAligned, now extended to containment so the report and the refusal can never disagree"
    - "Blocking/advisory split keyed on a single boolean predicate (crossesCalendarMidnight), computed once and reused by every caller that needs to know which side of the split a template is on"
    - "A planner-surfaced defect (P-03, not an inherited decision) found by reading the file during planning, fixed in the same plan rather than deferred, and recorded as such in the SUMMARY rather than left to look like an inherited item"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/service/ShiftTemplateService.java
    - src/main/java/com/wfm/service/ShiftLibraryValidationService.java
    - src/main/java/com/wfm/dto/ShiftLibraryValidationResponse.java
    - src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
    - src/test/java/com/wfm/service/ShiftTemplateServiceTest.java
    - src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java
    - src/test/java/com/wfm/service/ShiftLibraryGenerationServiceTest.java
    - src/test/java/com/wfm/service/SolverServiceBandCapacityRefusalTest.java
    - src/test/resources/midnight-time-arithmetic.md

key-decisions:
  - "isWithinOperatingWindow delegates to DayWindow.anchoredContains rather than re-deriving the offset comparison -- this is the first caller in either validation service to read TimeslotBoundsResponse.endTime(), which had zero call sites before this plan"
  - "crossesCalendarMidnight is deliberately bound to a 00:00 DayWindow regardless of the desk's own anchor -- it answers a fixed, anchor-independent question (does this pair cross CALENDAR midnight), not a per-desk business-day question. Documented as a second, narrower category in midnight-time-arithmetic.md's allowlist, distinct from that file's existing 'cannot reach a desk anchor' category, because this call site CAN reach the desk's own anchor (bound elsewhere in the same method as `window`) and still deliberately does not use it here"
  - "D-10's generation filter is unconditional (same-day escapes excluded too), stricter than the save path's overnight-only blocking rule -- a deliberate asymmetry: generation proposes NEW suggestions, so it can afford to never propose anything an operator would need to adjust, even where the save path would tolerate it as advisory"
  - "Two pre-existing ShiftLibraryGenerationServiceTest fixtures (weekdayAttributionUnchanged, postMidnightDemandBucketsUnderTheBusinessDayItBelongsTo) had a contracted-hours value whose mandatory-break-inflated envelope exceeded a window flush with the raw demand span -- legal only because nothing bounded a candidate to the window before D-10. Widened each grid by exactly the one hour the break needs, rather than touching the shared HOURLY_08_21_GRID constant other tests also depend on"

patterns-established:
  - "A containment/crossing predicate shared across three call sites (save path, report/gate, generation) rather than three independent implementations -- the same discipline this file's pre-existing isAligned already established for grid alignment"

requirements-completed: [OVNT-05]

coverage:
  - id: D1
    description: "An overnight template whose envelope reaches outside its desk's generated operating window is refused at the save path, naming the envelope and the window it escapes"
    requirement: "OVNT-05"
    verification:
      - kind: unit
        ref: "ShiftTemplateServiceTest#create_overnightEnvelopeOutsideOperatingWindow_refusedNamingEnvelopeAndWindow"
        status: pass
    human_judgment: false
  - id: D2
    description: "The same overnight escape is refused again at the SHIFT-mode gate, naming the template, via the identical shared predicate"
    requirement: "OVNT-05"
    verification:
      - kind: unit
        ref: "ShiftLibraryValidationServiceTest#requireShiftModeReady_overnightTemplateOutsideOperatingWindow_throwsNamingTemplate"
        status: pass
    human_judgment: false
  - id: D3
    description: "A same-day template reaching outside the operating window produces a non-blocking advisory in the shift-library report and no refusal anywhere"
    requirement: "OVNT-05"
    verification:
      - kind: unit
        ref: "ShiftLibraryValidationServiceTest#validate_sameDayTemplateOutsideOperatingWindow_reportsNonBlockingFinding_requireShiftModeReadyDoesNotThrowForIt"
        status: pass
      - kind: unit
        ref: "ShiftTemplateServiceTest#create_sameDayEnvelopeOutsideOperatingWindow_stillSavesAtSavePath"
        status: pass
    human_judgment: false
  - id: D4
    description: "A desk with no generated timeslots has no operating window, so the containment check does not run anywhere it could refuse"
    requirement: "OVNT-05"
    verification:
      - kind: unit
        ref: "ShiftTemplateServiceTest#create_overnightEnvelopeWithNoLiveBounds_accepted"
        status: pass
      - kind: unit
        ref: "ShiftLibraryValidationServiceTest#validate_boundsAbsent_operatingWindowFindingsEmpty"
        status: pass
    human_judgment: false
  - id: D5
    description: "Generated shift-library suggestions never propose an envelope the save path would refuse -- candidate enumeration filters against the shared containment predicate"
    requirement: "OVNT-05"
    verification:
      - kind: unit
        ref: "ShiftLibraryGenerationServiceTest#generateSuggestion_21_00AnchoredDesk_neverProposesAnEnvelopeReachingPastTheOperatingWindow"
        status: pass
    human_judgment: false
  - id: D6
    description: "On a desk whose day start is not midnight, candidate enumeration sweeps the real anchored range of demanded start times rather than their clock range, so an overnight demand window produces candidates instead of running zero times (P-03)"
    requirement: "OVNT-05"
    verification:
      - kind: unit
        ref: "ShiftLibraryGenerationServiceTest#generateSuggestion_21_00AnchoredDesk_sweepReachesTheRealEarliestAnchoredDemand"
        status: pass
      - kind: unit
        ref: "ShiftLibraryGenerationServiceTest#generateSuggestion_00_00AnchoredDesk_sweepBoundsUnchanged_noOpControl"
        status: pass
    human_judgment: false
  - id: D7
    description: "The shift-library report and the save-path refusal can never disagree, because exactly one containment predicate exists and every caller invokes it"
    requirement: "OVNT-05"
    verification:
      - kind: unit
        ref: "ShiftLibraryValidationServiceTest#requireShiftModeReady_and_createShiftTemplate_agreeOnTheSameOvernightEscape"
        status: pass
    human_judgment: false

duration: 30min
completed: 2026-10-03
status: complete
---

# Phase 21 Plan 05: Operating-Window Containment Summary

**One shared predicate (`ShiftTemplateService.isWithinOperatingWindow`/`crossesCalendarMidnight`) now refuses an overnight shift template whose envelope reaches past its desk's generated operating window — at the save path, at the SHIFT-mode gate, and in generated suggestions — while a same-day escape stays a non-blocking advisory everywhere.**

## Performance

- **Duration:** 30 min
- **Started:** 2026-10-02T23:36:00Z (approx.)
- **Completed:** 2026-10-03T00:06:45Z
- **Tasks:** 3 of 3
- **Files modified:** 9

## Accomplishments
- `ShiftTemplateService` gained two new package-private statics, placed immediately after `isAligned` and matching its style: `isWithinOperatingWindow` (delegates to `DayWindow.anchoredContains`, reading `TimeslotBoundsResponse.endTime()` for the first time anywhere in either validation service) and `crossesCalendarMidnight` (the blocking/advisory split key, OVNT-05/D-07).
- `validate()`'s save path now refuses an overnight template whose envelope escapes the desk's live operating window, naming the envelope and the window bounds — but never refuses a same-day escape, which is Task 2's advisory job. A desk with no generated timeslots is unaffected (the check does not run at all).
- `ShiftLibraryValidationResponse` carries a new `operatingWindowFindings` list (`OperatingWindowFinding` record: template, envelope, window, `blocking`, message). `ShiftLibraryValidationService.findOperatingWindowEscapes` is structurally built on the existing `findMisalignedTemplates` (same empty-bounds skip, same retired-row skip) and calls the shared predicate rather than writing new containment arithmetic. `requireShiftModeReady` adds one `operatingWindow` `ErrorDetail` per blocking finding; non-blocking findings are reported and never thrown.
- `ShiftLibraryGenerationService.enumerateCandidates` rejects any candidate escaping the operating window (D-10), in the same `if` as the two existing alignment tests, and derives `earliestStart`/`latestStart` through `Comparator.comparingInt(window::anchoredStartMinute)` instead of `Comparator.naturalOrder()` on the raw `LocalTime` (P-03) — a planner-surfaced defect, not an inherited decision: on an anchored desk the old clock-order derivation could invert the sweep range and silently generate zero candidates. Byte-identical on a `00:00` desk, where clock order and anchored order are the same sequence.
- Two pre-existing `ShiftLibraryGenerationServiceTest` fixtures needed their grid widened by exactly one hour each, because D-10 made visible a gap those fixtures were silently relying on (a contracted-hours-plus-mandatory-break envelope that exceeded a window flush with the raw demand span) — see Deviations.

## Task Commits

Each task followed RED → GREEN TDD discipline:

1. **Task 1: One containment predicate, and the save-path refusal that calls it**
   - `e855ad6` (test/RED) — six failing tests; confirmed RED via compile failure naming the exact missing static
   - `5d79b4a` (feat/GREEN) — `isWithinOperatingWindow`/`crossesCalendarMidnight`, the save-path refusal, and a new "deliberately midnight, not unreachable" category added to `midnight-time-arithmetic.md`'s allowlist
2. **Task 2: Report the finding in the shift library, and refuse it at the SHIFT-mode gate**
   - `9c39c7f` (test/RED) — six failing tests; confirmed RED via compile failure naming the missing `operatingWindowFindings()` accessor
   - `e898c32` (feat/GREEN) — `OperatingWindowFinding`/`operatingWindowFindings`, `findOperatingWindowEscapes`, the `operatingWindow` `ErrorDetail`, and the Rule-3 widening of three pre-existing `ShiftLibraryValidationResponse` constructor calls in `SolverServiceBandCapacityRefusalTest`
3. **Task 3: Stop the generator proposing what the save path refuses, and sweep the real anchored range**
   - `64247ed` (test/RED) — four new tests; confirmed RED via three assertion failures reproducing the pre-existing P-03 defect (the fourth, the 00:00-anchor no-op control, already passed as expected)
   - `34a1587` (feat/GREEN) — the D-10 filter, the P-03 comparator fix, and the Rule-1 widening of two pre-existing test fixtures

**Plan metadata:** this SUMMARY commit (docs: complete plan)

_TDD discipline: each RED commit was confirmed to fail intentionally for the planned reason (a missing static/accessor at compile time, or the exact pre-existing defect at runtime) before its GREEN commit landed._

## Files Created/Modified
- `src/main/java/com/wfm/service/ShiftTemplateService.java` — `isWithinOperatingWindow`, `crossesCalendarMidnight`, and the save-path refusal in `validate()`
- `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` — `findOperatingWindowEscapes`, its call from `validate()`, and the `operatingWindow` `ErrorDetail` in `requireShiftModeReady`
- `src/main/java/com/wfm/dto/ShiftLibraryValidationResponse.java` — `OperatingWindowFinding` record and `operatingWindowFindings` component
- `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` — the D-10 filter condition and the P-03 comparator fix in `enumerateCandidates`
- `src/test/java/com/wfm/service/ShiftTemplateServiceTest.java` — 6 new tests for the save-path predicate and refusal
- `src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java` — 6 new tests for the report/gate pair, plus `ShiftTemplateService` imported into the Spring test context for the agreement test
- `src/test/java/com/wfm/service/ShiftLibraryGenerationServiceTest.java` — 4 new tests for the generation filter and sweep fix, a `saveSchedule` helper, and two pre-existing fixtures widened by one hour each
- `src/test/java/com/wfm/service/SolverServiceBandCapacityRefusalTest.java` — three `ShiftLibraryValidationResponse` constructor calls widened for the new trailing component (Rule 3)
- `src/test/resources/midnight-time-arithmetic.md` — one new allowlist entry and its rationale, in a newly-documented second category distinct from the file's existing "cannot reach a desk anchor" one

## Decisions Made
- `crossesCalendarMidnight` is deliberately bound to a midnight-anchored `DayWindow` regardless of the desk's own reachable anchor — documented in `midnight-time-arithmetic.md` as a second legitimate category for the guard's allowlist, since the existing "cannot reach a desk anchor" category does not describe why this one is permitted.
- D-10's generation filter rejects both overnight AND same-day escapes, stricter than the save path's overnight-only blocking rule — generation proposes new suggestions, so it can afford to never offer anything an operator would need to adjust.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Widened three `ShiftLibraryValidationResponse` constructor calls in `SolverServiceBandCapacityRefusalTest`**
- **Found during:** Task 2, compile step
- **Issue:** the new `operatingWindowFindings` record component is positional; the three hand-constructed `ShiftLibraryValidationResponse` instances in this unrelated test file no longer compiled.
- **Fix:** appended `List.of()` to each of the three constructor calls.
- **Files modified:** `src/test/java/com/wfm/service/SolverServiceBandCapacityRefusalTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.service.SolverServiceBandCapacityRefusalTest"` green.
- **Committed in:** `e898c32` (Task 2 GREEN commit)

**2. [Rule 3 - Blocking] Added an allowlist entry and a new rationale category to `midnight-time-arithmetic.md`**
- **Found during:** Task 1, GREEN phase (test run)
- **Issue:** `MidnightTimeArithmeticGuardTest` failed: `crossesCalendarMidnight`'s deliberate `DayWindow.anchoredAt(LocalTime.MIDNIGHT)` binding was not in the "Permitted midnight anchors" allowlist. The file's existing guidance ("legitimate only when the call site genuinely cannot reach a desk's real day-start anchor") does not describe this case — the desk's anchor IS reachable here, bound elsewhere in the same method as `window`; the midnight binding is deliberate for a different, documented reason.
- **Fix:** added the allowlist line and a new "second, narrower kind of legitimate entry" subsection explaining the distinction (asking a fixed, anchor-independent question vs. an unreachability exception).
- **Files modified:** `src/test/resources/midnight-time-arithmetic.md`
- **Verification:** `./gradlew test --tests "com.wfm.service.MidnightTimeArithmeticGuardTest"` green.
- **Committed in:** `5d79b4a` (Task 1 GREEN commit)

**3. [Rule 1 - Bug] Widened two pre-existing `ShiftLibraryGenerationServiceTest` fixtures by one hour each**
- **Found during:** Task 3, GREEN phase (test run)
- **Issue:** `generateSuggestion_00_00AnchoredDesk_weekdayAttributionUnchanged` (13.00h contracted hours on a 13h-wide grid) and `generateSuggestion_21_00AnchoredDesk_postMidnightDemandBucketsUnderTheBusinessDayItBelongsTo` (9.00h contracted hours on a 9h-wide grid) each had a contracted-hours value whose mandatory 1-hour break, once added to the net duration, produced an envelope exactly one hour longer than their grid's span. Before D-10, nothing bounded a candidate to the operating window, so this silently generated (and accepted) a suggestion reaching past the grid's end. D-10 correctly rejects it, which emptied `response.templates()` and turned every demand window uncovered — a real regression these two tests surfaced, not a flaw in either fixture's actual purpose (weekday/business-date attribution, unrelated to window span).
- **Fix:** widened each fixture's own locally-scoped grid (not the shared `HOURLY_08_21_GRID` constant other tests depend on) by exactly the one hour the mandatory break needs, leaving demand, agents, and all other assertions unchanged.
- **Files modified:** `src/test/java/com/wfm/service/ShiftLibraryGenerationServiceTest.java`
- **Verification:** both tests green individually and in the full unfiltered suite.
- **Committed in:** `34a1587` (Task 3 GREEN commit)

---

**Total deviations:** 3 auto-fixed (2 blocking, 1 bug)
**Impact on plan:** All three necessary for correctness or for the build to compile at all. The third deviation is a direct, visible consequence of correctly implementing this plan's own D-10 requirement against pre-existing fixtures that had been silently relying on the gap it closes — not scope creep.

## Issues Encountered

None beyond the three deviations above, each resolved within its own task before proceeding.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `ShiftTemplateService.isWithinOperatingWindow`/`crossesCalendarMidnight` are the shared containment vocabulary other Phase 21 plans (21-06 onward) can reuse rather than re-deriving.
- `OVNT-05` is satisfied end-to-end: save path, shift-library report, SHIFT-mode gate, and candidate generation all agree, by construction, because they share one predicate.
- No blockers. Full unfiltered `./gradlew test` run: 198 classes, 1262 tests (1246 baseline + 16 new), 0 failures, 0 errors, 4 pre-existing benchmark skips — matching the wave's post-merge baseline exactly plus this plan's additions.

---
*Phase: 21-overnight-shift-templates*
*Completed: 2026-10-03*

## Self-Check: PASSED

All 9 modified files confirmed present on disk with the expected changes; all 6 plan commits
(`e855ad6`, `5d79b4a`, `9c39c7f`, `e898c32`, `64247ed`, `34a1587`) confirmed present in
`git log --oneline --all`. Full unfiltered `./gradlew test` run green (198 classes, 1262 tests,
0 failures, 0 errors, 4 pre-existing skips). All plan-level `<acceptance_criteria>` grep checks
re-verified passing.
