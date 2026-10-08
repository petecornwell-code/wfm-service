---
phase: 21-overnight-shift-templates
plan: 04
subsystem: api
tags: [erlang-c, erlang-x, business-date, staffing-requirement, jpa]

# Dependency graph
requires:
  - phase: 21-overnight-shift-templates (plan 21-02)
    provides: a reachable non-midnight-anchored desk, which turned this plan's gap from latent to live
provides:
  - "calculateErlangC and calculateErlangX clear live staffing requirements by business-date range, matching the demand-save path"
  - "a recorded migrate-now decision, with the operator's reasoning, for the two Erlang calculators' date-range semantics"
  - "bday-join-guard.md's inherited open item closed: it now records the migration instead of handing the callers to a future phase"
affects: [staffing-requirement, erlang-calculators, overnight-shift-templates]

# Actuals (#2632)
actuals:
  tokens: 6820
  tasks: 2
  commits: 1

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A destructive range-delete's range derivation and its filter column are one decision that must move together -- verified by a survivor-observing test, never by reading the query alone (same pattern 20-07 established for the demand-save path, now extended to both Erlang calculators)."

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/service/StaffingRequirementService.java
    - src/main/java/com/wfm/repository/StaffingRequirementRepository.java
    - src/test/resources/bday-join-guard.md
    - src/test/java/com/wfm/service/StaffingRequirementBusinessDateDeleteTest.java

key-decisions:
  - "migrate-now: both Erlang C and Erlang X were re-pointed to deleteLiveByDeskAndBusinessDateRange in this same plan, rather than deferred with a recorded gap. See the Decision section below for the operator's full reasoning."

patterns-established: []

requirements-completed: [OVNT-01, OVNT-02]

coverage:
  - id: D1
    description: "calculateErlangC and calculateErlangX clear live staffing requirements by the business-date range instead of the calendar-date range"
    requirement: "OVNT-02"
    verification:
      - kind: unit
        ref: "StaffingRequirementBusinessDateDeleteTest#erlangC_anchor21_recalculationForBusinessDayD_leavesBusinessDayDMinus1Untouched"
        status: pass
      - kind: unit
        ref: "StaffingRequirementBusinessDateDeleteTest#erlangC_anchor21_recalculationForBusinessDayD_replacesExactlyThatBusinessDaysRows"
        status: pass
      - kind: unit
        ref: "StaffingRequirementBusinessDateDeleteTest#erlangC_anchor00_recalculation_isByteIdenticalToCalendarDateScoping"
        status: pass
      - kind: unit
        ref: "StaffingRequirementBusinessDateDeleteTest#erlangX_mirrorsErlangCsThreePropertiesSoTheTwoCalculatorsCannotDriftApart"
        status: pass
    human_judgment: false
  - id: D2
    description: "The two out-of-scope callers of the calendar-date twin (TimeslotGeneratorService generation path, FteUploadService FTE upload path) are unchanged"
    requirement: "OVNT-01"
    verification:
      - kind: other
        ref: "grep -c 'deleteLiveByDeskAndDateRange' src/main/java/com/wfm/service/TimeslotGeneratorService.java src/main/java/com/wfm/service/FteUploadService.java -- each prints 1"
        status: pass
    human_judgment: false
  - id: D3
    description: "bday-join-guard.md records the migration decision rather than deferring these two callers as an open item"
    requirement: "OVNT-01"
    verification:
      - kind: other
        ref: "grep -c 'pick them up' src/test/resources/bday-join-guard.md -- prints 0"
        status: pass
    human_judgment: false

duration: ~25min
completed: 2026-10-02
status: complete
---

# Phase 21 Plan 04: Erlang Calculator Business-Date Migration Decision Summary

**Migrated both Erlang C and Erlang X staffing calculators onto `deleteLiveByDeskAndBusinessDateRange`, per the operator's `migrate-now` decision at this plan's checkpoint.**

## Performance

- **Duration:** ~25 min (this continuation agent's portion; the decision itself was gathered by a prior executor instance that made no file changes)
- **Completed:** 2026-10-02
- **Tasks:** 2 (checkpoint:decision, auto)
- **Files modified:** 4

## Accomplishments
- `calculateErlangC` and `calculateErlangX` in `StaffingRequirementService` now clear live staffing requirements over the business-date range (`deleteLiveByDeskAndBusinessDateRange`) instead of the calendar-date range, matching the demand-save path and closing the gap plan 21-02 made reachable
- `StaffingRequirementRepository`'s calendar-date twin comment updated to name its two remaining callers (`TimeslotGeneratorService` generation path, `FteUploadService` FTE upload path) and why each legitimately keeps calendar-date semantics
- `src/test/resources/bday-join-guard.md` updated at both the primary bullet and the "third shape" cross-reference so neither any longer hands these two callers to a future phase as an undecided open item
- Four new tests added to the existing `StaffingRequirementBusinessDateDeleteTest` (no new test class), proving the 21:00-anchor sibling-business-day isolation and exact-replacement properties for Erlang C, the 00:00-anchor no-op control for Erlang C, and all three properties again for Erlang X so the two calculators cannot drift apart

## Task Commits

Each task was committed atomically:

1. **Task 1: Decision checkpoint (`migrate-now`)** — no file changes; the decision itself, gathered at the checkpoint and recorded below, required no commit of its own.
2. **Task 2: Implement the recorded decision and update the inherited note** - `487fa5c` (feat)

**Plan metadata:** (this commit)

## Files Created/Modified
- `src/main/java/com/wfm/service/StaffingRequirementService.java` - both Erlang calculators' delete call re-pointed to the business-date twin, with an inline comment at each call site explaining the invariant
- `src/main/java/com/wfm/repository/StaffingRequirementRepository.java` - the calendar-date twin's comment rewritten to name its two remaining, deliberately-calendar-date callers
- `src/test/resources/bday-join-guard.md` - the primary deferred-callers bullet rewritten to record the migration, and the "third shape" cross-reference bullet updated so it no longer lists the Erlang calculators among sites that would still need a calendar-date allowlist entry
- `src/test/java/com/wfm/service/StaffingRequirementBusinessDateDeleteTest.java` - four new test methods plus a shared `BusinessDayDFixture` fixture-builder and two request-builder helpers, reusing the class's existing `@DataJpaTest` wiring

## Decisions Made

**Decision: `migrate-now`.** The operator chose to re-point both Erlang calculators (`calculateErlangC` and `calculateErlangX`) to `deleteLiveByDeskAndBusinessDateRange` — business-date semantics — rather than `defer-with-record`, a partial migration of only one calculator, or `migrate-now` plus a staffing-screen copy change. The scope stayed exactly as the plan's `migrate-now` branch defined it: no staffing-screen copy change in this phase, and no split between the two calculators.

**Operator's stated reasoning (verbatim from the checkpoint resume):**

> 21-02 (merged earlier in this same wave) ended the unreachability the deferral rested on, so the defect is live rather than latent from the moment a non-midnight-anchored desk exists — which can be the same session. On a `21:00`-anchored desk a calendar-scoped clear both misses post-midnight rows belonging to the business day the operator asked to replace and reaches rows belonging to the business day before it, with nothing failing or logging. The environment named `dev` is the live system carrying real tenant data, so a silently wrong row set there is a production-grade correctness problem, which outweighs the fact that the staffing screen's date picker will now mean business dates without copy explaining it. Both 21-CONTEXT and 21-RESEARCH recommended this option. On a `00:00`-anchored desk the change is byte-identical to today, which is the no-op control the plan's tests assert.

## Deviations from Plan

None - plan executed exactly as written. The operator's decision determined which branch of the plan's `<action>` to carry out; the `migrate-now` branch was followed in full, with no additional fixes or scope changes beyond what that branch specified.

## Issues Encountered

**Local test-infrastructure flake, not a code issue.** The first full, unfiltered `./gradlew test` run failed with `Could not write XML test results` for eleven heavy `com.wfm.solver` benchmark/quality-guard test classes (e.g. `SolverQualityGuardTest`, `ShiftStartMixSteerTest`, `NinetyAgent12HourTest`) — all unrelated to this plan's files. Two stale Gradle daemons were found running concurrently at the time (one left over from an earlier `./gradlew --stop` that didn't fully clear before the next invocation started), which is the likely cause of the resource contention. After `./gradlew --stop` and a clean rerun (`./gradlew test --rerun`), the full suite completed cleanly: **1246 tests, 0 failures, 0 errors, 4 skipped** (the skipped tests are pre-existing, deliberately-gated benchmark cases in `UsualShiftConsistencyBenchmarkTest` and `ShiftModelBenchmarkTest`, unrelated to this plan).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All three write paths that replace live staffing requirements over a date range (demand-save, Erlang C, Erlang X) now agree on business-date semantics.
- The two remaining calendar-date-twin callers (`TimeslotGeneratorService` generation, `FteUploadService` FTE upload) are unchanged and documented as a deliberate, separate decision — not an inherited open item.
- No blockers for subsequent phase 21 plans.

---
*Phase: 21-overnight-shift-templates*
*Completed: 2026-10-02*

## Self-Check: PASSED
