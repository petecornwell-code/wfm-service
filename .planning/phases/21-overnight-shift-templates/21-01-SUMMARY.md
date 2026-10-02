---
phase: 21-overnight-shift-templates
plan: 01
subsystem: api
tags: [java, spring, jpa, timefold-adjacent, shift-templates, day-window]

# Dependency graph
requires:
  - phase: 19-daywindow-re-anchoring
    provides: "DayWindow.anchoredAt/anchoredIsForwardWithinDay/anchoredDurationMinutes (anchored interval arithmetic) and Desk.getDayStart()"
  - phase: 20-solver-business-date-correctness
    provides: "business-date-correct solver joins this plan's templates will eventually feed, and the plan-20-05 before-picture precedent this plan's registry removal follows"
provides:
  - "ShiftTemplateService.createShiftTemplate/updateShiftTemplate accept a genuine overnight (end-earlier-than-start) envelope on an anchored (non-midnight day-start) desk, with correct net hours"
  - "D-02: the forward-interval refusal on a midnight-anchored desk now names that desk's own day start, both envelope times, and 'would span two business days' instead of a generic message"
  - "D-08: ShiftTemplateService.MAX_SPAN_MINUTES (16h) refuses any anchored envelope longer than 16 hours, inclusive boundary at exactly 16h"
  - "DayWindow.dayStart() read-only accessor for the bound anchor"
  - "midnight-boundary-scenarios.md's asserts-todays-behaviour registry reduced from 3 to 2 entries (OVNT-01's entry removed in the same change that flipped its assertion)"
affects: [21-02, 21-05, 21-06, 21-07, 21-09, 21-10, 21-11, 21-12]

# Actuals (#2632)
actuals:
  tokens: 4752
  tasks: 2
  commits: 4
  plan_head_before: 487ce2932bc335b94912b339edb802d8209d3871
  plan_head_after: 2b96aa49f46eebff745934076bf36336e11f9e6a

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Fixed desk-policy bounds (shift-length caps) live in the calling service's validate() method, never in DayWindow, which owns interval arithmetic only and must hold no opinion about shift length (19-CONTEXT D-11)"
    - "A refusal caused by desk state (the anchor) must name that desk state in its message rather than attributing the cause to the operator's own correctly-entered values"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/service/ShiftTemplateService.java
    - src/main/java/com/wfm/util/DayWindow.java
    - src/test/java/com/wfm/service/ShiftTemplateServiceTest.java
    - src/test/java/com/wfm/service/MidnightBoundaryPropertyTest.java
    - src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java
    - src/test/resources/midnight-boundary-scenarios.md

key-decisions:
  - "D-02's refusal message is the UI-SPEC Copywriting Contract's locked text verbatim, interpolating window.dayStart() (new accessor) rather than re-loading the desk a second time"
  - "D-08's 16-hour cap (MAX_SPAN_MINUTES) lives in ShiftTemplateService.validate, ordered after the anchoredDurationMinutes read and before validateBands, never in DayWindow"
  - "The OVNT-01 registry row and its @AssertsTodaysBehaviour marker were removed in the exact commit that flipped the assertion (5eebf62), not a follow-up commit"

patterns-established:
  - "Pattern: TDD RED/GREEN discipline per task within a multi-task plan — each task gets its own test(...) then feat(...) commit pair, even when both tasks share a plan number"

requirements-completed: [OVNT-01]

coverage:
  - id: D1
    description: "A 21:00-anchored desk saves a 22:00-06:00 template with net hours 8.00"
    requirement: "OVNT-01"
    verification:
      - kind: unit
        ref: "ShiftTemplateServiceTest#create_overnightEnvelopeOnAnchoredDesk_acceptedWithCorrectNetHours"
        status: pass
    human_judgment: false
  - id: D2
    description: "A 00:00-anchored desk refuses the same envelope, naming its own day start, both envelope times, and 'would span two business days' rather than blaming the operator's times"
    requirement: "OVNT-01"
    verification:
      - kind: unit
        ref: "ShiftTemplateServiceTest#create_overnightEnvelopeOnMidnightDesk_refusedNamingDeskDayStart"
        status: pass
      - kind: manual_procedural
        ref: "Human visual check of the Toast at normal and ~375px viewport widths"
        status: pass
    human_judgment: true
    rationale: "Full-sentence rendering without clipping inside the Toast container is a visual/layout property this frontend has no test runner to assert; the plan's own <verify> designates this a human-check backstop. Human confirmed (see Issues Encountered) at both viewport widths."
  - id: D3
    description: "A duplicate overnight template create on the same desk is refused by the existing identity/non-overlap rules, not a new rule"
    requirement: "OVNT-01"
    verification:
      - kind: unit
        ref: "ShiftTemplateServiceTest#create_duplicateOvernightTemplate_refusedByExistingIdentityRule"
        status: pass
    human_judgment: false
  - id: D4
    description: "A 22-hour anchored envelope (22:00-20:00) is refused by name on an anchored desk; a 16-hour one (22:00-14:00) is accepted at the inclusive boundary; an ordinary same-day template on a 00:00 desk still saves"
    requirement: "OVNT-01"
    verification:
      - kind: unit
        ref: "ShiftTemplateServiceTest#create_twentyTwoHourAnchoredSpan_refusedNamingSpanAndCap"
        status: pass
      - kind: unit
        ref: "ShiftTemplateServiceTest#create_sixteenHourAnchoredSpan_acceptedAtInclusiveBoundary"
        status: pass
      - kind: unit
        ref: "ShiftTemplateServiceTest#create_ordinarySameDayTemplateOnMidnightDesk_stillAccepted"
        status: pass
    human_judgment: false
  - id: D5
    description: "The OVNT-01 asserts-todays-behaviour registry entry is removed in the same change that flips its assertion; the registry's both-directions validator stays green"
    requirement: "OVNT-01"
    verification:
      - kind: unit
        ref: "com.wfm.support.MidnightBoundaryScenarioRegistryTest (full class, 5/5)"
        status: pass
      - kind: unit
        ref: "MidnightBoundaryPropertyTest.ShiftCrossingMidnight#anchoredDeskAcceptsTheCrossingEnvelope"
        status: pass
    human_judgment: false

duration: ~62min elapsed across two sessions (tracer-feedback-gate pause for human visual verification in between); ~14min active implementation work
completed: 2026-10-02
status: complete
---

# Phase 21 Plan 01: Overnight Shift Template Creation Summary

**An anchored desk (non-midnight day start) can now save a genuine overnight shift template with correct net hours, a midnight-anchored desk refuses one by naming its own day start, and no anchored envelope may exceed 16 hours.**

## Performance

- **Duration:** ~62 min elapsed (includes a tracer-feedback-gate pause for human Toast verification); ~14 min of active implementation
- **Tasks:** 2 of 2
- **Files modified:** 6

## Accomplishments
- An operator can save a `22:00`-`06:00` shift template on a `21:00`-anchored desk; its net hours read back as `8.00` (480 real elapsed minutes), not a wrapped or negative value.
- A `00:00`-anchored desk still refuses the same request, but the message now names that desk's own day start, both submitted times, and the phrase "would span two business days" — the refusal no longer misattributes an anchor-caused rejection to times the operator entered correctly (D-02).
- The same identity/non-overlap rules that already governed same-day templates now also catch a duplicate overnight create — no new rule was needed (OVNT-01 idempotency edge).
- Any anchored envelope longer than 16 hours is refused by name (`MAX_SPAN_MINUTES`), closing a gap the midnight-anchored refusal used to close for free: on an anchored desk, `22:00`-`20:00` is now a legitimately *forward* 22-hour interval that nothing else would have caught (D-08). The cap is inclusive of exactly 16 hours.
- The `midnight-boundary-scenarios.md` asserts-todays-behaviour registry dropped from 3 entries to 2 — OVNT-01's row was removed in the same commit that flipped `MidnightBoundaryPropertyTest.ShiftCrossingMidnight`'s assertion from "refuses" to "accepts," and `MidnightBoundaryScenarioRegistryTest` stays green in both directions.

## Task Commits

Each task followed RED → GREEN TDD discipline, one commit per gate:

1. **Task 1: End-to-end "an overnight template is creatable"**
   - `17d9bbb` (test/RED) — failing tests for overnight acceptance, D-02 refusal, idempotency, and the flipped `ShiftCrossingMidnight` assertion
   - `5eebf62` (feat/GREEN) — D-02 message rewrite, `DayWindow.dayStart()` accessor, registry row removal, `MidnightBoundaryScenarioRegistryTest` constant fix
2. **Task 2: Refuse a template whose anchored span exceeds 16 hours**
   - `f553194` (test/RED) — failing tests for the 22-hour refusal, the 16-hour inclusive boundary, and the same-day no-op control
   - `2b96aa4` (feat/GREEN) — `MAX_SPAN_MINUTES` constant and the span-cap refusal in `validate()`

**Plan metadata:** this SUMMARY commit (docs: complete plan)

_TDD discipline: each task's RED commit was confirmed to fail intentionally — exactly the target test, on the planned assertion, not a syntax error or unrelated failure — before its GREEN commit was written._

## Files Created/Modified
- `src/main/java/com/wfm/service/ShiftTemplateService.java` — D-02's anchor-naming refusal message; `MAX_SPAN_MINUTES = 16 * 60` and the D-08 span-cap refusal, ordered before `validateBands`
- `src/main/java/com/wfm/util/DayWindow.java` — added `dayStart()`, a read-only accessor for the bound anchor (no new arithmetic, no policy); otherwise unmodified by Task 2 as required
- `src/test/java/com/wfm/service/ShiftTemplateServiceTest.java` — 6 new tests: overnight acceptance, D-02 anchor-naming refusal, idempotent duplicate-create refusal, 22-hour refusal, 16-hour inclusive boundary, same-day no-op control
- `src/test/java/com/wfm/service/MidnightBoundaryPropertyTest.java` — `ShiftCrossingMidnight`'s save-path assertion flipped from refuse to accept; marker and its explanatory comment block removed; `@DisplayName` rewritten
- `src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java` — `EXPECTED_REGISTRY_SIZE` updated `3 -> 2` (not in the plan's `files_modified` frontmatter; required the moment the OVNT-01 row was removed — see Deviations)
- `src/test/resources/midnight-boundary-scenarios.md` — OVNT-01's registry row and its prose-table row deleted; surrounding prose updated to "two scenarios" and records the before-picture pointer to this SUMMARY

## Decisions Made
- D-02's message is the UI-SPEC Copywriting Contract's locked text, reproduced verbatim with its three interpolations (day start, start time, end time), reading the bound `window.dayStart()` rather than re-loading the desk a second time (T-21-01's tenant-scoped single-load guarantee stays intact).
- D-08's 16-hour cap lives in `ShiftTemplateService`, not `DayWindow` — `DayWindow` owns interval arithmetic only and must hold no opinion about shift length (19-CONTEXT D-11, re-affirmed here rather than revisited).
- The registry row removal and the assertion flip landed in one commit (`5eebf62`), matching plan 20-05's precedent for how a "scenarios asserting today's behaviour" entry retires.

## Deviations from Plan

Both deviations below were introduced and committed by the prior executor during Task 1 (commit `5eebf62`), before the tracer-feedback-gate pause this plan resumed from. Carried forward verbatim per the continuation brief.

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `MidnightBoundaryScenarioRegistryTest.EXPECTED_REGISTRY_SIZE` updated 3 -> 2**
- **Found during:** Task 1, verification step
- **Issue:** the plan's `files_modified` frontmatter omitted this file, but `registryHoldsExactlyTheExpectedEntryCount()` hardcodes the expected count and would fail the moment the OVNT-01 registry row was removed (as the plan's own action item 4 requires).
- **Fix:** updated the constant and its javadoc to record the removal.
- **Verification:** `./gradlew test --tests "com.wfm.support.MidnightBoundaryScenarioRegistryTest"` green (5/5).
- **Committed in:** `5eebf62`

**2. [Rule 1 - Bug, scope-consistent] Three pre-existing exact-message assertions updated**
- **Found during:** Task 1, GREEN phase
- **Issue:** `create_startTimeEqualsEndTime_rejected`, `create_startTimeAfterAMidnightEnd_stillRejected`, and `create_endTimeBeforeStartTime_rejected` asserted the old exact refusal string via `.hasMessage(...)`. D-02 changes that message for EVERY non-forward interval (the anchored model treats any backward interval as "crosses the anchor"), not only the overnight scenario, so these three now legitimately receive the new message.
- **Fix:** changed to `.hasMessageContaining(...)` asserting the invariant substrings (base sentence + "would span two business days"), since the interpolated values differ per test.
- **Verification:** `./gradlew test --tests "com.wfm.service.ShiftTemplateServiceTest"` green (44/44 at that point).
- **Committed in:** `5eebf62`

No new deviations were introduced during Task 2 (this continuation session) — it executed exactly as specified.

---

**Total deviations:** 2 auto-fixed (both from Task 1; 1 blocking, 1 bug/scope-consistent)
**Impact on plan:** Both necessary for correctness — the registry guard and the three pre-existing tests would otherwise fail against D-02's new, intentionally-changed message text. No scope creep.

## Issues Encountered

**Tracer feedback gate (Task 1):** this plan is `type="tracer"`, so after Task 1's GREEN commit the plan paused for the mandatory human verification gate rather than proceeding to Task 2. The human visually confirmed the D-02 error Toast at both a normal viewport and a narrow (~375px) viewport: the full interpolated sentence rendered without clipping and named `00:00` (the desk's own day start) as the cause rather than blaming the operator's entered times. This is recorded as **human-verified**, not a backstop-unverified item — see coverage entry D2.

Otherwise: none. Task 2 executed exactly as specified, with its own RED/GREEN TDD cycle, and both the scoped suite and the full unfiltered suite (`./gradlew test`) were green.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `ShiftTemplateService.validate` now accepts a genuine overnight envelope and bounds its maximum span — every other Phase 21 plan that renders, validates, scores, or discloses an overnight template can now build against a template that actually exists and persists.
- `DayWindow.dayStart()` is a new public accessor other Phase 21 plans may reuse rather than re-deriving the anchor.
- The asserts-todays-behaviour registry carries exactly 2 entries (OVNT-03, OVNT-04), owned by plans outside this one (21-03/21-06/21-09/21-10 etc. per the phase's flagged planner assumptions table) — no action required here.
- No blockers for 21-02 (DeskService.setDayStart interaction) per this plan's `coupling_justified` note: this plan only called `setDayStart` on template-free, schedule-free desks.

---
*Phase: 21-overnight-shift-templates*
*Completed: 2026-10-02*
