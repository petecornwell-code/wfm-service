---
phase: 20-solver-business-date-correctness
plan: 01
subsystem: solver
tags: [timefold, constraint-streams, daywindow, business-date, midnight-boundary, test-fixture]

# Dependency graph
requires:
  - phase: 19-daywindow-re-anchoring
    provides: "ScheduleConfig.dayStart populated from Desk.dayStart (BDAY-04), the bound-instance DayWindow.anchoredAt API, and the already-migrated shiftEnvelopeCompliance constraint as the worked anchor-binding example"
provides:
  - "AgentDayConfig.dayStart — the Quad-arity constraint carrier for a desk's real anchor, populated from the single production construction site, zero behaviour change"
  - "MidnightBoundaryFixture.NINE_PM_WINDOW and three 21:00-anchored constructed scenarios, each deriving businessDate exclusively through DayWindow.businessDateOf"
  - "MidnightBoundaryRegressionTest.NonMidnightAnchor — three RED scoring assertions proving the calendar-date join defect, plus one GREEN edge/adjacency/ordering test"
affects: [20-05-join-and-anchor-migration, 20-02-business-date-join-guard, 20-08-day-start-generation-reachability]

# Actuals (#2632)
actuals:
  tokens: 9348
  tasks: 2
  commits: 2

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Delegating-constructor arity preservation for record problem facts (ScheduleConfig's own precedent, now applied a second time to AgentDayConfig)"
    - "Anchor-bearing test-fixture overloads that derive businessDate via DayWindow.businessDateOf rather than hand-computing it, so fixture and production share one derivation site"
    - "A DayWindow parameter threaded through a test fixture's deterministic pinning rule so a non-midnight scenario's ordering key uses its own anchor, never a second rule"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/model/AgentDayConfig.java
    - src/main/java/com/wfm/service/SolverService.java
    - src/test/java/com/wfm/solver/MidnightBoundaryFixture.java
    - src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java

key-decisions:
  - "ninePmOvernightContiguityScenario's exact clock geometry departs from the plan's prose (\"20:00 through 02:00\"): a literal-midnight-crossing envelope would additionally trip a separate, out-of-scope TreeSet<LocalTime> ordering bug inside shiftWorkContiguity's private hole-scan (genuinely overnight SHIFT TEMPLATES are Phase 21/OVNT-01..07 territory). The scenario instead places all six timeslots in the calendar-date-shifted TAIL of a 21:00 business day (02:00-08:00, business date one day earlier than their shared calendar date) — this isolates SOLV-03's calendar-vs-business join defect cleanly and is still falsifiable: expected 1 hole (two real gaps, one exempted by the 'at most one free gap' rule), actual 0 (every seat silently fails to join)."
  - "The adjacency/null/ordering edge test (20:45-21:00 and 21:00-21:15 slots) is carried as two extra, unstaffed timeslots inside ninePmCoverageScenario rather than a fixture-external construction — minimumStaffing groups by AgentAssignment, never by a bare Timeslot, so the two slots add zero to any constraint's match count while still being readable off the scenario's own getTimeslots()."
  - "pinAgentAssignments and its eligibility lookup now key on Timeslot.getBusinessDate() instead of getDate() — byte-identical for every existing 00:00 scenario (businessDate == calendarDate there) and required for the three new 21:00 scenarios, where they differ."

patterns-established:
  - "A desk-anchor Quad-arity carrier is added to a problem-fact record via a delegating constructor, never a parameter widening at any of its many test construction sites — ScheduleConfig and now AgentDayConfig both follow this shape."

requirements-completed: []

coverage:
  - id: D1
    description: "AgentDayConfig.dayStart populated from the schedule's real anchor at the single production construction site, with zero score movement on the existing 34 test construction sites"
    requirement: "SOLV-03"
    verification:
      - kind: unit
        ref: "com.wfm.solver.ScheduleConfigAnchorPlumbingTest"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.DeskAnchorReachesConstraintTest"
        status: pass
      - kind: other
        ref: "./gradlew test (unfiltered, full suite, after Task 1)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Three 21:00-anchored constructed scenarios whose break-band, contiguity and envelope assertions are RED against the un-migrated solver, confined to the new NonMidnightAnchor methods"
    requirement: "SOLV-01"
    verification:
      - kind: unit
        ref: "com.wfm.solver.MidnightBoundaryRegressionTest$NonMidnightAnchor#envelopeComplianceAtNonMidnightAnchor_matchesMidnightAnchorGeometry"
        status: fail
      - kind: unit
        ref: "com.wfm.solver.MidnightBoundaryRegressionTest$NonMidnightAnchor#bandFlushToNonMidnightAnchor_everyRelevantConstraintReadsZero"
        status: fail
      - kind: unit
        ref: "com.wfm.solver.MidnightBoundaryRegressionTest$NonMidnightAnchor#earlyMorningAgentDay_readsItsTrueHoleCount"
        status: fail
      - kind: unit
        ref: "com.wfm.solver.MidnightBoundaryRegressionTest$NonMidnightAnchor#adjacencyNullAndOrderingEdges"
        status: pass
      - kind: other
        ref: "./gradlew test (unfiltered, full suite, after Task 2): 1142 tests, 3 failed, all confined to NonMidnightAnchor, 0 errors"
        status: pass
    human_judgment: false

duration: 48min
completed: 2026-10-01
status: complete
---

# Phase 20 Plan 1: Desk Anchor Carrier and 21:00-Anchored RED Scenarios Summary

**`AgentDayConfig.dayStart` reaches every Quad-arity constraint via a delegating constructor (zero behaviour change), and three 21:00-anchored constructed scenarios prove the solver's calendar-date join silently drops every seat whose calendar date differs from its agent-day's business date.**

## Performance

- **Duration:** 48 min
- **Started:** 2026-10-01T17:20:00Z
- **Completed:** 2026-10-01T18:08:24Z
- **Tasks:** 2 completed
- **Files modified:** 4 (2 production, 2 test)

## Accomplishments
- `AgentDayConfig` grew an 11th record component (`dayStart`) plus a 10-argument delegating constructor, mirroring `ScheduleConfig`'s own precedent exactly — every one of the 34 pre-existing test construction sites compiles unchanged, and the full unfiltered suite stayed green after this commit (additive, zero score movement).
- `SolverService.computeAgentDayConfigs` now passes `schedule.getDayStart()` as the carrier's value at the single production construction site — the Quad-arity channel plan 20-05's migration will consume.
- `MidnightBoundaryFixture` grew `NINE_PM_WINDOW` and three 21:00-anchored constructed scenarios (`ninePmCoverageScenario`, `ninePmBreakBandFlushToEnvelopeEndScenario`, `ninePmOvernightContiguityScenario`), each deriving every timeslot's business date through `DayWindow.businessDateOf` — never hand-computed — and carrying a real `dayStart` on their `Schedule`, `ScheduleConfig` and every `AgentDayConfig` row.
- `MidnightBoundaryRegressionTest.NonMidnightAnchor` adds three scoring assertions that are RED against the un-migrated solver (envelope compliance, the band-flush boundary, and shift-work contiguity — all three defeated by the same calendar-date join), plus a fourth GREEN test proving the anchor's adjacency rule, non-null business dates across all three new scenarios, and the pinning rule's ordering stability.
- Full unfiltered suite after Task 2: **1142 tests, 3 failed, 0 errors** — every failure confined to the three new `NonMidnightAnchor` scoring methods, exactly the wave-close gate the plan requires.

## Task Commits

Each task was committed atomically:

1. **Task 1: Carry the desk anchor into a Quad-arity constraint's reach** - `a2f9652` (feat)
2. **Task 2: Three 21:00-anchored scenarios and their assertions — red against today's solver** - `e08e2bd` (test)

**Plan metadata:** pending (this commit)

## Files Created/Modified
- `src/main/java/com/wfm/model/AgentDayConfig.java` — 11th record component `dayStart`, 10-argument delegating constructor (test-fixture-only default, D-07 carve-out)
- `src/main/java/com/wfm/service/SolverService.java` — `computeAgentDayConfigs` passes `schedule.getDayStart()` at the single production construction site
- `src/test/java/com/wfm/solver/MidnightBoundaryFixture.java` — `NINE_PM_WINDOW`, three 21:00-anchored scenario builders, anchor-bearing `timeslot`/`baseSchedule`/`dayConfig` overloads, pinning rule generalized to take a `DayWindow` and key on `getBusinessDate()`
- `src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java` — `NonMidnightAnchor` nested class, four tests

## Decisions Made

1. **`ninePmOvernightContiguityScenario`'s geometry departs from the plan's "20:00 through 02:00" prose.** A literal-midnight-crossing envelope would ALSO trip a second, separate defect: `shiftWorkContiguity`'s private hole-scan sorts assigned start times through a plain `TreeSet<LocalTime>`, which reorders a midnight-crossing span back into literal-clock order (`00:00` sorting before `21:00`) regardless of which `DayWindow` is eventually passed in — a real bug, but one belonging to a genuinely overnight SHIFT TEMPLATE (OVNT-01..07, Phase 21's explicit "Not this phase" territory per `20-CONTEXT.md`), not to SOLV-03's join migration. Mixing the two would make this scenario's RED-today/GREEN-after-20-05 story false (plan 20-05 fixes the join and the `PENDING_DESK_ANCHOR` usage, not the `TreeSet` ordering). The scenario instead places all six timeslots in the calendar-date-shifted TAIL of a 21:00 business day (clock 02:00-08:00, calendar date one day after the business date) — this isolates the join defect alone. Verified empirically, not assumed: expected 1 hole (two genuine one-hour gaps, one exempted by the "at most one free gap" fallback rule) once the join is fixed; actual 0 today, because every one of the four worked seats carries a calendar date that never equals the agent-day's business date, so none of them ever joins and the row drops out of the constraint's grouping entirely.
2. **The adjacency edge test (20:45-21:00 / 21:00-21:15) is carried as two extra, unstaffed timeslots inside `ninePmCoverageScenario`** rather than built ad hoc in the test — `minimumStaffing` groups by `AgentAssignment`, never by a bare `Timeslot`, so the two slots contribute nothing to any constraint's match count while still being directly readable off the scenario's own `getTimeslots()`, honoring the plan's "read directly off the constructed scenarios'" instruction.
3. **The deterministic pinning rule's eligibility lookup and sort key now use `Timeslot.getBusinessDate()`** instead of `getDate()` — byte-identical for the three existing 00:00 scenarios (where `businessDate == calendarDate` by construction) and required for the three new 21:00 scenarios, where the agent-day grouping key is genuinely the business date, not the calendar date.

## Deviations from Plan

### Auto-fixed Issues

None — plan executed as written for Task 1 and Task 2's structural requirements. The scenario-geometry departure documented above (Decision 1) is an exercise of the plan's own explicitly granted "Claude's Discretion" over scenario construction details (20-CONTEXT.md), not a Rule 1-4 auto-fix, and is recorded here for transparency since the literal prose differs from the final construction.

---

**Total deviations:** 0 auto-fixed. **Impact:** None — all changes are either additive (Task 1) or test-scope (Task 2); no production behaviour moved.

## Issues Encountered

**Two of Task 1's `<verify>` grep commands over-match on pre-existing content, unrelated to this plan's edits.** Both files are confirmed byte-unchanged by this plan (`git diff` shows zero lines touched in either):
- `grep -c 'DayWindow.anchoredAt(LocalTime.MIDNIGHT)' src/test/resources/midnight-time-arithmetic.md` prints **5**, not the plan's expected 4 — the file's "Permitted midnight anchors" fenced block still holds exactly its four pre-existing rows (verified directly), but the SAME literal string also appears once in explanatory prose above that block (line 94), which the plan's grep command does not distinguish from the registry itself.
- `grep -c 'dayStart()' src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` prints **2**, not the plan's expected 0 — both matches are the ALREADY-migrated `shiftEnvelopeCompliance`/`breakClustering` constraints' pre-existing `cfg.dayStart()` calls on `ScheduleConfig` (Phase 19), not the newly-added `AgentDayConfig.dayStart()`; `grep -c 'AgentDayConfig' ... | grep dayStart` style disambiguation confirms no constraint reads the new component in this task.

Neither is a regression introduced by this plan — both are pre-existing tree states the plan author's grep commands did not anticipate (the same category of drift the phase's own `20-CONTEXT.md` documents happening twice already this milestone). Recorded here so `/gsd-verify-work` does not mistake a stale verify command for a real defect; the acceptance criteria's actual INTENT (file unedited; no constraint reads the new component) is independently confirmed true.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

**Ready for plan 20-02** (`BusinessDateJoinGuardTest`, `AgentDayDerivationGuardTest` — SOLV-02/SOLV-07/D-06). The Quad-arity anchor carrier this plan ships is the channel plan 20-05's single migration commit will consume, and the three RED scoring assertions are now the falsifiable before-picture every later plan in this phase measures against.

**Verbatim RED failure output (D-11's falsifiability evidence, before-picture for plan 20-05):**

```
MidnightBoundaryRegressionTest > SOLV-03: assertions at a 21:00 (non-midnight) anchor > envelope at a non-midnight anchor: re-anchoring alone changes nothing about which seats a band covers FAILED
    org.opentest4j.AssertionFailedError: [21:00-anchor: moving the band one minute off flush must ALSO violate exactly one seat, matching the 00:00-anchor count -- today's solver still joins AgentShiftAssignment/Timeslot pairs on calendar date, which silently drops every seat on this scenario's business date from the join]
    expected: 1
     but was: 0
    at MidnightBoundaryRegressionTest$NonMidnightAnchor.envelopeComplianceAtNonMidnightAnchor_matchesMidnightAnchorGeometry(MidnightBoundaryRegressionTest.java:311)

MidnightBoundaryRegressionTest > SOLV-03: assertions at a 21:00 (non-midnight) anchor > band flush to the 21:00 anchor: legal at the flush position, violated one increment later FAILED
    org.opentest4j.AssertionFailedError: [moving the band one minute off the 21:00 flush boundary must violate exactly one seat]
    expected: 1
     but was: 0
    at MidnightBoundaryRegressionTest$NonMidnightAnchor.bandFlushToNonMidnightAnchor_everyRelevantConstraintReadsZero(MidnightBoundaryRegressionTest.java:344)

MidnightBoundaryRegressionTest > SOLV-03: assertions at a 21:00 (non-midnight) anchor > an agent-day deep in its business day's tail reads its true hole count, not zero FAILED
    org.opentest4j.AssertionFailedError: [an agent-day whose timeslots fall entirely in its business day's calendar-date-shifted tail must still read its true hole count (one, per the fallback's 'at most one free gap' rule) -- today's calendar-date join silently drops every one of this agent-day's seats, reading zero instead]
    expected: 1
     but was: 0
    at MidnightBoundaryRegressionTest$NonMidnightAnchor.earlyMorningAgentDay_readsItsTrueHoleCount(MidnightBoundaryRegressionTest.java:369)

10 tests completed, 3 failed (scoped run of MidnightBoundaryRegressionTest)
Full unfiltered suite: 1142 tests completed, 3 failed, 4 skipped (pre-existing, unrelated benchmark tests), 0 errors
```

No blockers.

---
*Phase: 20-solver-business-date-correctness*
*Completed: 2026-10-01*

## Self-Check: PASSED

- FOUND: src/main/java/com/wfm/model/AgentDayConfig.java
- FOUND: src/main/java/com/wfm/service/SolverService.java
- FOUND: src/test/java/com/wfm/solver/MidnightBoundaryFixture.java
- FOUND: src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java
- FOUND: commit a2f9652 (Task 1)
- FOUND: commit e08e2bd (Task 2)
- Acceptance criteria re-verified: `grep -rc 'new AgentDayConfig(' src/main/java` → 1; `schedule.getDayStart()` count in SolverService.java → 1; `DayWindow.businessDateOf` count in fixture → 5; `setDayStart(` count in fixture → 2; `.solve(` count in regression test → 0.
- Plan-level `<verification>` re-run: unfiltered `./gradlew test` → 1142 tests, 3 failed, 0 errors, failures confined to `MidnightBoundaryRegressionTest$NonMidnightAnchor`; both `midnight-time-arithmetic.md` and `midnight-boundary-scenarios.md` confirmed byte-unchanged via `git diff` against the plan's start commit.
