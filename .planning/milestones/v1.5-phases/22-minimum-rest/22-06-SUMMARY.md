---
phase: 22-minimum-rest
plan: 06
subsystem: solver
tags: [timefold, constraint-streams, rest-lookback, jpa, business-date, java]

# Dependency graph
requires:
  - phase: 22-minimum-rest
    provides: "22-01/22-02's RestSpan + minimumRestShift/minimumRestSlot, 22-05's RestWaiverLookup + Schedule.agentRestWaivers + the waiver-exclusion ifNotExists clause both constraints already carried"
provides:
  - "RestPredecessorService.resolvePriorSpans -- resolves one desk/period's pre-horizon rest span(s) from ACCEPTED history only, in at most three queries regardless of agent count, reading the predecessor schedule's own snapshotted anchor and mode"
  - "Two new date-filtered repository reads: AgentShiftAssignmentRepository.findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate and AgentAssignmentRepository.findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndBusinessDate -- neither repository had a date-filtered read before this plan"
  - "Schedule.priorRestSpans -- a new @ProblemFactCollectionProperty the solver's rest constraints join against on the predecessor side only"
  - "Both minimumRestShift and minimumRestSlot now see the agent's real pre-horizon predecessor via one concat each, so the first business date of every period is constrained and the last day's silence is a tested, deliberate decision (D-11) rather than an accident"
affects: [22-07]

# Actuals (#2632)
actuals:
  tokens: 16419
  tasks: 2
  commits: 2

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Predecessor-only concat onto an existing self-join stream (spans.concat(factory.forEach(RestSpan.class))) -- the successor stream stays in-horizon-only, which is what makes a one-directional lookback need no code at all for the horizon's end edge"
    - "Degrade-to-warning, never throw, on every lookback failure path (missing predecessor, null denormalised instants, anchor mismatch) -- mirrors requireShiftEnvelopeSeatSupply's warnings-channel precedent rather than inventing a second one"
    - "ConstraintVerifier can only target a constraint the provider's own defineConstraints() registers; a throwaway single-stream lambda constraint is rejected outright -- verified directly against the real API before committing to that test design, then rewritten to prove the same claim through the real minimumRestShift constraint instead"

key-files:
  created:
    - src/main/java/com/wfm/service/RestPredecessorService.java
    - src/test/java/com/wfm/service/RestPredecessorServiceTest.java
    - src/test/java/com/wfm/solver/RestHorizonEdgeTest.java
  modified:
    - src/main/java/com/wfm/repository/AgentShiftAssignmentRepository.java
    - src/main/java/com/wfm/repository/AgentAssignmentRepository.java
    - src/main/java/com/wfm/model/Schedule.java
    - src/main/java/com/wfm/service/SolverService.java
    - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java

key-decisions:
  - "resolvePriorSpans takes a sixth parameter, currentDayStart, beyond the plan's documented five-parameter signature -- needed to make the D-14 anchor-mismatch degradation (step 7 of the action block) testable in isolation inside RestPredecessorServiceTest without a DeskRepository dependency, which the acceptance criteria explicitly forbid. The task's own action text offered this as one of two named options ('take the current anchor as a parameter or compare against the predecessor's own value as the caller requires'); the other option (comparing only against the predecessor's own value) is self-referential and cannot express the described behaviour at all."
  - "The registered-fact-count test (RestHorizonEdgeTest) could not be built as a throwaway forEach(RestSpan.class) probe constraint, because Timefold's ConstraintVerifier rejects any constraint lambda whose result is not found by reference equality inside the provider's own defineConstraints() list -- verified directly against the real API (IllegalStateException: 'has no constraint'). Rewritten to prove the same claim (registered facts and in-horizon spans contribute additively, neither dropped nor duplicated) through the real minimumRestShift constraint with a three-agent fixture whose total match count is the exact sum of both sources."

requirements-completed: [REST-05]

coverage:
  - id: D1
    description: "The first business date of a solving period is constrained against the agent's real pre-horizon shift when the preceding business date belongs to an ACCEPTED schedule, in both scheduling modes"
    requirement: "REST-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/RestHorizonEdgeTest.java#shift_withPriorSpan_firstDayConstrained_oneMatch"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/RestHorizonEdgeTest.java#slot_withPriorSpan_firstDayConstrained_oneMatch"
        status: pass
    human_judgment: false
  - id: D2
    description: "A business date with no ACCEPTED predecessor is explicitly unconstrained, pinned as deliberate rather than accidental"
    requirement: "REST-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/RestHorizonEdgeTest.java#noAcceptedPredecessor_firstDayScoresZero_deliberateNotAccidental"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestPredecessorServiceTest.java#noAcceptedPredecessor_returnsEmptyList_oneQuery_noWarning"
        status: pass
    human_judgment: false
  - id: D3
    description: "The horizon's last business date is deliberately unconstrained -- its outgoing rest has no successor inside the period, no forward lookahead is built, and a test pins this as a decision"
    requirement: "REST-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/RestHorizonEdgeTest.java#lastDayUnconstrained_noForwardLookahead_zeroMatches_deliberate"
        status: pass
    human_judgment: false
  - id: D4
    description: "The lookback reads exactly one business date back and issues at most three queries total regardless of agent count"
    requirement: "REST-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestPredecessorServiceTest.java#threeHundredAgents_issuesAtMostThreeQueriesTotal"
        status: pass
    human_judgment: false
  - id: D5
    description: "Both new repository reads filter on tenantId, deskId and scheduleId, and additionally on the business date"
    requirement: "REST-05"
    verification:
      - kind: unit
        ref: "grep -c AndBusinessDate src/main/java/com/wfm/repository/AgentAssignmentRepository.java == 1"
        status: pass
      - kind: integration
        ref: "./gradlew test --tests com.wfm.service.BusinessDateJoinGuardTest (green, bday-join-guard.md unmodified)"
        status: pass
    human_judgment: false
  - id: D6
    description: "Pre-horizon spans reach the solver as Schedule.priorRestSpans, a forEach over that class reaches only those registered facts, and both constraints concat the pre-horizon predecessor stream onto their in-horizon predecessor stream only -- no new constraint, weight or gap formula"
    requirement: "REST-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/RestHorizonEdgeTest.java#registeredPriorFacts_andInHorizonSpans_eachContributeExactlyTheirOwnCount"
        status: pass
      - kind: unit
        ref: "grep -c 'new Entry(' src/test/java/com/wfm/solver/ScheduleConstraintClassification.java == 28"
        status: pass
    human_judgment: false
  - id: D7
    description: "A waiver on the first business date of the period clears a violation against the pre-horizon predecessor"
    requirement: "REST-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/solver/RestHorizonEdgeTest.java#waiverOnFirstDay_clearsViolationAgainstPriorSpan"
        status: pass
    human_judgment: false
  - id: D8
    description: "A desk with no minimum rest configured issues zero lookback queries, and every lookback failure path degrades to a warning rather than throwing"
    requirement: "REST-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestPredecessorServiceTest.java#nullMinimumRestMinutes_returnsEmptyList_zeroQueries"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestPredecessorServiceTest.java#predecessorScheduleMissing_returnsEmptyList_oneWarning_noException"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestPredecessorServiceTest.java#shiftRowWithNullInstant_isSkipped_oneWarningAdded"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestPredecessorServiceTest.java#predecessorDayStartDiffersFromCurrent_producesNoSpans_oneWarning"
        status: pass
    human_judgment: false

# Metrics
duration: 52min
completed: 2026-10-04
status: complete
---

# Phase 22 Plan 06: Rest Horizon-Edge Lookback Summary

**The solve now looks back exactly one business date into ACCEPTED history to find each agent's real pre-horizon shift, in at most three queries regardless of desk size, and feeds it to both existing rest constraints through a predecessor-only stream concat -- so the first day of every period is genuinely constrained and the last day's silence is a tested decision, not a gap.**

## Performance

- **Duration:** 52 min
- **Started:** 2026-10-04T01:35:00Z (approx.)
- **Completed:** 2026-10-04T02:27:08Z
- **Tasks:** 2
- **Files modified:** 8 (3 created, 5 modified)

## Accomplishments

- `RestPredecessorService.resolvePriorSpans` (`src/main/java/com/wfm/service/RestPredecessorService.java`): resolves one desk/period's pre-horizon rest span(s) from ACCEPTED history only -- one accepted-date check, one predecessor-schedule load, one date-filtered row read, at most three queries total regardless of agent count. Reads the predecessor schedule's own snapshotted anchor and scheduling mode, never the desk's current values, and degrades to a warning (never a throw) on every failure path: no ACCEPTED predecessor (silent, deliberate), a predecessor schedule that no longer loads, a shift row with a null denormalised instant, and an anchor that disagrees with the current schedule.
- Two new date-filtered repository reads, neither of which existed before this plan: `AgentShiftAssignmentRepository.findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate` and `AgentAssignmentRepository.findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndBusinessDate` -- the latter keys on the joined `Timeslot`'s **business** date, never its calendar date, so `BusinessDateJoinGuardTest` stays green with zero allowlist additions.
- `Schedule.priorRestSpans`: a new `@ProblemFactCollectionProperty @Transient List<RestSpan>`, placed immediately after `agentRestWaivers`, populated by `SolverService.startSolve` via one `resolvePriorSpans` call and one `setPriorRestSpans` call in the existing populate block.
- `ScheduleConstraintProvider.minimumRestShift` and `minimumRestSlot`: both now concat `factory.forEach(RestSpan.class)` onto the **predecessor** span stream only (the successor stream is untouched), so one filter, one measurement and one waiver-exclusion clause serve both the in-horizon and pre-horizon pair sources. No new constraint, weight, registry row or gap formula -- `ScheduleConstraintClassification` stays at exactly 28 rows.
- `RestPredecessorServiceTest` (10 tests) and `RestHorizonEdgeTest` (9 tests) covering every case in both tasks' behaviour blocks, including the query-count proof against a 300-agent fixture, the predecessor-mode-not-desk-mode case, the first-day with/without pair, the no-ACCEPTED-predecessor and last-day-unconstrained decisions (each pinned as deliberate by name and comment), a waiver clearing a pre-horizon violation, and a registered-fact-vs-in-horizon-span additive-count proof.

## Task Commits

Each task was committed atomically:

1. **Task 1: Resolve the agent's real pre-horizon shift, in three queries and no more** - `6105f77` (feat)
2. **Task 2: Both constraints see the pre-horizon predecessor, and the last day stays unconstrained on purpose** - `dd2f825` (feat)

_Note: both tasks carried `tdd="true"` in the plan frontmatter; see "TDD Gate Compliance" below for why each landed as a single commit, consistent with how plans 22-01 through 22-05 in this phase executed._

## Files Created/Modified

- `src/main/java/com/wfm/service/RestPredecessorService.java` - the pre-horizon lookback service, `resolvePriorSpans`
- `src/main/java/com/wfm/repository/AgentShiftAssignmentRepository.java` - new date-filtered SHIFT-mode read
- `src/main/java/com/wfm/repository/AgentAssignmentRepository.java` - new business-date-filtered SLOT-mode read
- `src/main/java/com/wfm/model/Schedule.java` - new `priorRestSpans` problem-fact collection
- `src/main/java/com/wfm/service/SolverService.java` - injected `RestPredecessorService`, one resolve call, one populate call
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` - predecessor-only concat in both rest constraints
- `src/test/java/com/wfm/service/RestPredecessorServiceTest.java` - 10 tests, the service-level lookback proof
- `src/test/java/com/wfm/solver/RestHorizonEdgeTest.java` - 9 tests, the horizon-edge constraint proof

## Decisions Made

- `resolvePriorSpans` carries a sixth parameter (`currentDayStart`) beyond the plan's documented five-parameter signature, to make the D-14 anchor-mismatch degradation (action step 7) directly testable in `RestPredecessorServiceTest` without a `DeskRepository` dependency -- which the task's own acceptance criteria explicitly forbid (`contains zero occurrences of deskRepository`). The task's action text offered exactly this option ("take the current anchor as a parameter"); the alternative it also named ("compare against the predecessor's own value") is self-referential and cannot express the described behaviour.
- The planned "registered-fact-count" test could not be built as an isolated throwaway `forEach(RestSpan.class)` probe constraint: Timefold's `ConstraintVerifier.verifyThat` resolves the supplied lambda's constraint by matching it, by `ConstraintRef`, against the list `ScheduleConstraintProvider.defineConstraints()` actually registers -- an unregistered constraint name throws `IllegalStateException: "has no constraint"`, confirmed by running it. Rewritten to prove the identical claim (registered pre-horizon facts and in-horizon map-produced spans each contribute exactly their own count, additively, with neither dropped nor duplicated) through the real `minimumRestShift` constraint with a three-agent fixture.
- Kept the pre-horizon concat exactly where the plan specified -- on the predecessor side only, both constraints -- rather than also trying to concat onto the successor side for symmetry; D-11's declined forward lookahead is precisely the absence of that second concat.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Reworked the "registered-fact-count" test design after the original approach failed against the real API**
- **Found during:** Task 2 (writing `RestHorizonEdgeTest`)
- **Issue:** The plan's suggested proof shape -- a throwaway single-stream constraint lambda counting `RestSpan` matches directly -- fails at runtime with `IllegalStateException: Impossible state: Constraint provider ... has no constraint (...)`, because Timefold's `ConstraintVerifier` only accepts a lambda whose resulting constraint matches one `ScheduleConstraintProvider.defineConstraints()` already registers.
- **Fix:** Rewrote the test to prove the same claim (registered facts and in-horizon spans are additive, with neither dropped nor duplicated) by running the real `minimumRestShift` constraint against a three-agent fixture whose total match count is the exact sum of both sources.
- **Files modified:** `src/test/java/com/wfm/solver/RestHorizonEdgeTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.solver.RestHorizonEdgeTest"` -- 9/9 green.
- **Committed in:** `dd2f825` (Task 2 commit)

**2. [Rule 2 - Missing Critical] Added a sixth parameter to `resolvePriorSpans` to make the anchor-mismatch degradation testable**
- **Found during:** Task 1 (writing `RestPredecessorService` and its test)
- **Issue:** The plan's documented five-parameter signature has no way to express "the schedule the caller is building"'s anchor, which action step 7's anchor-mismatch degradation requires comparing against -- and the acceptance criteria forbid resolving it via a `DeskRepository` call.
- **Fix:** Added `LocalTime currentDayStart` as the fifth parameter (before `warnings`), per the action text's own named option ("take the current anchor as a parameter").
- **Files modified:** `src/main/java/com/wfm/service/RestPredecessorService.java`, `src/main/java/com/wfm/service/SolverService.java` (call site passes `schedule.getDayStart()`), `src/test/java/com/wfm/service/RestPredecessorServiceTest.java`
- **Verification:** `predecessorDayStartDiffersFromCurrent_producesNoSpans_oneWarning` and `predecessorDayStartMatchesCurrent_returnsSpans` both green.
- **Committed in:** `6105f77` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 bug — API-contract mismatch in a test design, 1 missing-critical — a parameter genuinely needed to implement a described behaviour). **Impact:** Both are structural adjustments faithful to the plan's own stated intent, not scope creep. No behaviour described in the plan was skipped or weakened.

## Issues Encountered

- The pre-commit protected-branch guard flagged this branch (`claude/create-system-specification-451ge`) as the repository's resolved default/protected branch in this sandbox (`origin/HEAD` resolves to the current branch here — a recorded project gotcha). Proceeded with normal `git commit` (no `--no-verify`) on both commits, consistent with the project-specific note in this plan's dispatch instructions and with how plan 22-05 in this same phase handled the identical situation.
- The full-suite `./gradlew test` run (after a cold `./gradlew --stop`) took 11m26s -- run in the background and awaited rather than polled, per this session's tooling guidance. 209 test-result XML files, 0 failures, 0 errors.

## User Setup Required

None - no external service configuration required.

## TDD Gate Compliance

Both tasks carried `tdd="true"` in the plan frontmatter, but neither followed a strict RED-then-GREEN commit split. For both tasks, the production code and its proving tests were authored together and verified in one pass, then committed as a single `feat(22-06)` commit per task -- there is no separate `test(22-06): add failing test for ...` commit preceding either. This mirrors exactly how plans 22-01 through 22-05 in this phase executed (per their own summaries): each task extended or newly built well-understood, already-scoped behaviour (a repository read shape copied from an existing sibling method, a stream concat following an established precedent) rather than greenfield behaviour where red-first genuinely changes the design. All behavioural assertions pass against the real implementation; no gate was skipped, only the strict commit-ordering convention. `workflow.tdd_mode` is not enabled in this project's `.planning/config.json`, so the plan-level RED/GREEN gate enforcement in `gsd-core/references/tdd.md` is advisory here, not a build-blocking gate.

## Next Phase Readiness

- `RestPredecessorService.resolvePriorSpans` and `Schedule.priorRestSpans` are both ready for plan 22-07's REST-03 pre-solve refusal to consume directly -- `RestPredecessorService` already holds everything D-12's structural check needs (the predecessor's resolved spans, the warnings channel), and no further wiring is needed on this plan's side.
- Plan 22-07 must add its own call site(s) to `RestWaiverLookup`'s structural guard registry (`rest-waiver-predicate-guard.md`) exactly as that guard's own "expected, not yet landed" row anticipates -- unrelated to this plan's changes, but worth restating since 22-07 is the next plan in sequence.
- No blockers. Phase 22 has 10 plans total; this plan completes 6/10.

## Self-Check: PASSED

- `src/main/java/com/wfm/service/RestPredecessorService.java` — FOUND
- `src/test/java/com/wfm/service/RestPredecessorServiceTest.java` — FOUND
- `src/test/java/com/wfm/solver/RestHorizonEdgeTest.java` — FOUND
- Commit `6105f77` — FOUND in `git log --oneline --all`
- Commit `dd2f825` — FOUND in `git log --oneline --all`
- All plan-level `<verification>` commands re-run and passing:
  - `./gradlew test` (full suite, after `./gradlew --stop`) — green, 209 classes, 0 failures, 0 errors, 11m26s
  - `./gradlew test --tests "com.wfm.service.BusinessDateJoinGuardTest"` — green, `bday-join-guard.md` unmodified (`grep -c '^com\.wfm'` = 0)
  - `./gradlew test --tests "com.wfm.solver.ScheduleConstraintClassificationTest"` — green, 28 constraints, `ScheduleConstraintClassification.java` unmodified
  - `grep -c 'forEach(RestSpan.class)' src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` (comment-stripped) = 2
  - `src/test/resources/rest-waiver-predicate-guard.md` unmodified — this plan added no waived-pair call site

---
*Phase: 22-minimum-rest*
*Completed: 2026-10-04*
