---
phase: 15-shift-envelope-breaks-library-generation
plan: 16
subsystem: api
tags: [timefold, junit, assertj, spring-web, error-handling, shift-envelope, gap-closure]

requires:
  - phase: 15 (plans 15-07, 15-10)
    provides: accept-time shift snapshot (D-07 denormalised columns), resolveShiftDescriptor, and computeDivergence -- all reused unchanged by this plan's accepted-path report
provides:
  - "ScheduleOutputService.buildConstraintViolations takes an explicit isAcceptedSnapshot parameter (threaded from ScheduleService.getScheduleDetail's own fromDb), replacing the null-weights proxy that let accepted schedules reach solutionManager.explain"
  - "buildAcceptedConstraintViolations: the accepted/DB-path violation report, derived from the persisted snapshot via the same resolveShiftDescriptor/computeDivergence coverage walk the agent-schedule view already uses (factored into outOfEnvelopeAssignments), never a second definition of \"covered\""
  - "assertFeasibleImpliesNoViolatedHardConstraints: a reusable read-path invariant helper (feasible == true implies violatedHardConstraints is empty), applied to every accepted- and live-schedule assertion in ScheduleServiceShiftSnapshotTest"
  - "A regression + constant-1104 pin + red-proof for the accepted-path violation report, in both ScheduleOutputServiceShiftReportingTest (unit) and ScheduleServiceShiftSnapshotTest (end-to-end through the real accept+reload round trip)"
  - "GlobalExceptionHandler.handleMethodNotSupported: HttpRequestMethodNotSupportedException now answers 405 METHOD_NOT_ALLOWED with a comma-joined Allow header naming only server-derived supported methods, never the client-supplied verb"
affects: []

actuals:
  tokens: 12216
  tasks: 3
  commits: 5

tech-stack:
  added: []
  patterns:
    - "D-08 one-predicate/two-callers discipline extended to a third caller: outOfEnvelopeAssignments factors the AgentAssignment-identity-preserving half of computeDivergence's coverage walk so the accepted-path violation report can recover agent/timeslot identity without a second implementation of ShiftBandPair.covers"
    - "Provenance threaded explicitly as a boolean parameter (isAcceptedSnapshot) rather than inferred from a proxy (schedule.getConstraintWeights() == null) -- the proxy silently stopped holding once loadSnapshotData started loading ConstraintWeights onto accepted schedules too"
    - "Allow header built as ONE comma-joined value, never one header entry per supported method -- HttpHeaders.getAllow() reads only the FIRST Allow value and tokenizes it, so a naive varargs .header(ALLOW, array) call silently truncates to the first method"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/service/ScheduleOutputService.java
    - src/main/java/com/wfm/service/ScheduleService.java
    - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
    - src/main/java/com/wfm/controller/GlobalExceptionHandler.java
    - src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java
    - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
    - src/test/java/com/wfm/controller/GlobalExceptionHandlerTest.java
    - .planning/phases/15-shift-envelope-breaks-library-generation/15-UAT.md

key-decisions:
  - "The accepted path never calls solutionManager.explain -- explaining a schedule whose shift problem facts are not reconstituted fails the coverage predicate for every held seat (the file's own discriminator identity for a null band pair), which is exactly where the constant 1104 came from"
  - "The null-weights guard is kept but demoted to a secondary safety net on the LIVE-SOLVER path only, placed AFTER the provenance branch, with a comment recording it can never again serve as the discriminator (accepted schedules DO carry ConstraintWeights via loadSnapshotData)"
  - "violatedHardConstraints stays derived from constraintViolations, never filtered on feasible -- the invariant is asserted by making the source (constraintViolations) correct, never by suppression, which would hide a genuinely infeasible schedule"
  - "GlobalExceptionHandler's 405 handler never echoes ex.getMethod() (the client-supplied verb), following the file's own handleTypeMismatch T-13-25/26 precedent; only ex.getSupportedMethods() (server-derived) feeds the message and Allow header"

requirements-completed: [ENVL-02, ENVL-07, XCUT-01]

coverage:
  - id: D1
    description: "An accepted schedule's constraint-violation report is derived from its own persisted snapshot instead of a mis-explained score director -- a clean accepted schedule reports zero envelope violations (never the constant 1104), and a genuinely out-of-envelope seat still reports exactly one violation naming that agent and timeslot"
    requirement: "ENVL-07"
    verification:
      - kind: unit
        ref: "ScheduleOutputServiceShiftReportingTest#buildConstraintViolations_acceptedNamedRowShape_reportsNoEnvelopeViolation"
        status: pass
      - kind: unit
        ref: "ScheduleOutputServiceShiftReportingTest#buildConstraintViolations_acceptedCleanMultiAgentDay_countIsZeroNeverTheStaffedSeatConstant"
        status: pass
      - kind: unit
        ref: "ScheduleOutputServiceShiftReportingTest#buildConstraintViolations_acceptedRedProof_oneRelocatedSeatReportsExactlyOneViolationNamingIt"
        status: pass
      - kind: unit
        ref: "ScheduleOutputServiceShiftReportingTest#buildConstraintViolations_liveUnaccepted_nullWeightsGuardStaysScopedToLivePath"
        status: pass
      - kind: integration
        ref: "ScheduleServiceShiftSnapshotTest#getScheduleDetail_acceptedNamedRowShape_feasibleTrueImpliesNoViolatedHardConstraints"
        status: pass
      - kind: integration
        ref: "ScheduleServiceShiftSnapshotTest#getScheduleDetail_acceptedRedProof_oneOutOfEnvelopeSeatReportsExactlyOneNamedViolation"
        status: pass
    human_judgment: false
  - id: D2
    description: "The read-path invariant this gap's own test_gap named now exists and is applied everywhere: feasible == true implies violatedHardConstraints is empty, asserted on every accepted- and live-schedule case in ScheduleServiceShiftSnapshotTest, not only the two new dedicated cases"
    requirement: "ENVL-02"
    verification:
      - kind: unit
        ref: "ScheduleServiceShiftSnapshotTest#assertFeasibleImpliesNoViolatedHardConstraints (helper applied to 6 call sites)"
        status: pass
      - kind: integration
        ref: "ScheduleServiceShiftSnapshotTest#getScheduleDetail_inMemoryFeasibleSchedule_feasibleTrueImpliesNoViolatedHardConstraints"
        status: pass
    human_judgment: false
  - id: D3
    description: "The red-proof that the accepted path can still go non-empty was manually verified before commit by temporarily forcing buildAcceptedConstraintViolations to return List.of() unconditionally (both red-proofs failed as expected) and by temporarily restoring the pre-fix unconditional explain() call for accepted schedules (the named-row/constant-1104 regressions failed as expected); both reverted, confirmed no residual diff"
    requirement: "XCUT-01"
    verification:
      - kind: other
        ref: "manual git diff confirmation that ScheduleOutputService.java carries no residual change from the red-proof check, plus the done-criterion's own two red/green cycles"
        status: pass
    human_judgment: true
    rationale: "The manual revert-observe-restore cycle is a one-time authorial verification step, not a repeatable automated test artifact -- a human should confirm the reasoning in this SUMMARY and the plan's <done> block match, not just that the suite is currently green."
  - id: D4
    description: "A wrong HTTP verb answers 405 METHOD_NOT_ALLOWED with an Allow header naming only server-derived supported methods (never the client-supplied verb), including the empty-supported-methods edge case, with every pre-existing handler mapping proven unaffected"
    verification:
      - kind: unit
        ref: "GlobalExceptionHandlerTest#handleMethodNotSupported_returns405WithAllowHeaderNamingSupportedMethods"
        status: pass
      - kind: unit
        ref: "GlobalExceptionHandlerTest#handleMethodNotSupported_doesNotEchoTheClientSuppliedVerb"
        status: pass
      - kind: unit
        ref: "GlobalExceptionHandlerTest#handleMethodNotSupported_emptySupportedMethods_stillReturns405WithoutMalformedAllowHeader"
        status: pass
      - kind: unit
        ref: "GlobalExceptionHandlerTest#preExistingMappings_stillReturnTheirOriginalStatuses (broadened)"
        status: pass
    human_judgment: false

duration: "~25min (continuation only; Task 1 commit 579b090 authored 2026-09-01T20:19:20-04:00 by a separate, earlier interrupted session)"
completed: 2026-09-02
status: complete
---

# Phase 15 Plan 16: Read-Path Truthfulness — Accepted-Schedule Violations and 405-Not-500 Summary

**The accepted-path violation report now reads the persisted snapshot instead of mis-explaining an accepted schedule (closing the constant-1104 misreport, G-15-32), and a wrong HTTP verb answers 405 with a named-methods Allow header instead of 500 (G-15-26) — both proven with red-proofs, not just green tests.**

## Performance

- **Duration:** ~25 min (this continuation) + Task 1 from a separate, earlier interrupted session (commit `579b090`, 2026-09-01T20:19:20-04:00)
- **Tasks:** 3 (all complete)
- **Files modified:** 7 source/test files + `15-UAT.md` gap-closure entries
- **Commits:** 5 (4 task commits + this plan's metadata commit)

## Accomplishments

- **G-15-32 closed.** `ScheduleOutputService.buildConstraintViolations` takes an explicit
  `isAcceptedSnapshot` parameter threaded from `ScheduleService.getScheduleDetail`'s own `fromDb`
  local. The accepted path never calls `solutionManager.explain` and instead derives the report
  from the persisted snapshot (`buildAcceptedConstraintViolations`), reusing
  `resolveShiftDescriptor` and `computeDivergence`'s coverage walk (factored into
  `outOfEnvelopeAssignments`) with no second definition of "covered" (D-08 discipline).
- **The constant-1104 arithmetic is now pinned as impossible**, not merely absent: a dedicated
  regression asserts the reported count for N clean agent-days x H legal seats is explicitly NOT
  `N*H`, and is zero.
- **The read-path invariant this gap's own `test_gap` named now exists**:
  `assertFeasibleImpliesNoViolatedHardConstraints` (`feasible == true` implies
  `violatedHardConstraints` is empty) is asserted on every accepted- and live-schedule case in
  `ScheduleServiceShiftSnapshotTest` — 6 call sites, not only the two new dedicated ones.
- **The red-proof is load-bearing and was verified manually before commit**: forcing the accepted
  path to return an unconditionally empty list failed both dedicated red-proof tests; restoring the
  pre-fix unconditional `explain()` call for accepted schedules failed the named-row and
  constant-1104 regressions. Both changes were reverted before committing, confirmed via `git diff`
  showing no residual change.
- **G-15-26 closed.** `GlobalExceptionHandler.handleMethodNotSupported` answers 405
  `METHOD_NOT_ALLOWED` with an `Allow` header built as one comma-joined value from the exception's
  own supported-methods set — never one header entry per method, since `HttpHeaders.getAllow()`
  only reads the first `Allow` value and tokenizes it. The message names the supported methods and
  never echoes the client-supplied verb (T-13-25/26 precedent). The empty-supported-methods case is
  covered without throwing or emitting a malformed header.
- **Both gaps marked `status: resolved` in `15-UAT.md`** with full `resolved_by`/`resolved_evidence`
  blocks, in the shape established by G-15-22/G-15-27/G-15-29/G-15-30. Only those two entries
  touched (confirmed via `git diff` hunk count — 2 hunks, both inside the intended blocks).
- **Full suite green: 610 tests, 0 failures, 0 errors, 2 skipped** — up from the 15-15 baseline of
  600, by exactly the 10 new tests this plan added (4 + 3 in the two `ScheduleOutputService`-family
  test classes, 3 in `GlobalExceptionHandlerTest`).

## Task Commits

Each task was committed atomically:

1. **Task 1: The accepted path stops explaining and starts reading the snapshot it already has** —
   `579b090` (fix) — completed in a separate, earlier interrupted session; verified, not redone, by
   this continuation.
2. **Task 2: The read-path invariant, plus a red-proof that the new path can still go non-empty** —
   `e07d036` (test) — this continuation verified the uncommitted working-tree diff against the
   plan's requirements, ran it, manually exercised both red-proof directions, then committed.
3. **Task 3: A wrong HTTP verb answers 405, not 500** — `7eb77cf` (test, RED) then `614f8f0` (feat,
   GREEN).

**Plan metadata:** committed alongside this SUMMARY — see `git_commit_metadata` step below.

_Note: Task 2's tests were TDD in shape (behavior described, then verified) but not RED/GREEN in
sequence, because Task 1's implementation already existed when Task 2 began — the plan's own
task ordering (implementation task, then a dedicated proving-tests task) is itself the checkpoint,
not a per-task RED/GREEN cycle. Task 3 followed the literal RED/GREEN cycle since no
implementation preceded it._

## Files Created/Modified

- `src/main/java/com/wfm/service/ScheduleOutputService.java` — `buildConstraintViolations` gains
  the `isAcceptedSnapshot` parameter; new `buildAcceptedConstraintViolations` and
  `outOfEnvelopeAssignments` helpers
- `src/main/java/com/wfm/service/ScheduleService.java` — `getScheduleDetail` passes its own
  `fromDb` through to `buildConstraintViolations`; comment recording the G-15-32 invariant at the
  `violatedHardConstraints` derivation site
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — exposes
  `SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME` as a public constant (see Deviations — this file was
  not in the plan's `files_modified` frontmatter)
- `src/main/java/com/wfm/controller/GlobalExceptionHandler.java` — new
  `handleMethodNotSupported` handler for `HttpRequestMethodNotSupportedException`
- `src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java` — 4 new tests plus
  fixture plumbing (`AgentDayFixture`, `acceptedScheduleWithEnvelope`)
- `src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java` — 3 new end-to-end tests,
  the `assertFeasibleImpliesNoViolatedHardConstraints` helper applied to 6 call sites, plus
  `stubRealConstraintViolations`/`saveDefaultConstraintWeights`/`saveTimeslots`/`heldSeatAssignments`
  fixture helpers
- `src/test/java/com/wfm/controller/GlobalExceptionHandlerTest.java` — 3 new tests, and the
  `preExistingMappings_stillReturnTheirOriginalStatuses` regression pin broadened to cover
  `handleNotFound`/`handleConflict` alongside the pre-existing checks
- `.planning/phases/15-shift-envelope-breaks-library-generation/15-UAT.md` — `G-15-32` and
  `G-15-26` marked `status: resolved` with `resolved_by`/`resolved_evidence`

## Decisions Made

- The accepted path never calls `solutionManager.explain`; the null-weights guard is demoted to a
  secondary safety net on the live-solver path only, since accepted schedules DO carry
  `ConstraintWeights` (loaded by `loadSnapshotData`) and can no longer serve as the provenance
  discriminator.
- `violatedHardConstraints` stays derived from `constraintViolations`, never filtered on
  `feasible` — the invariant is asserted by making the source correct, never by suppression.
- The `Allow` header is one comma-joined value, not one header entry per method, because
  `HttpHeaders.getAllow()` reads only the first `Allow` value.
- The 405 handler never echoes the client-supplied verb, following the file's own
  `handleTypeMismatch` T-13-25/26 precedent.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical functionality] `ScheduleConstraintProvider.java` modified, outside the plan's declared `files_modified`**
- **Found during:** Task 1 (completed by the prior, interrupted executor; verified by this
  continuation before proceeding to Task 2)
- **Issue:** The plan's frontmatter `files_modified` lists only `ScheduleOutputService.java`,
  `ScheduleService.java`, `GlobalExceptionHandler.java`, and the three test files. It does not
  list `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`. However, the plan's own
  Task 1 `<action>` text explicitly instructs: "take the constant from `ScheduleConstraintProvider`
  rather than retyping the string — the UI keys on this value and a divergence here would be
  silent." Satisfying that instruction requires exposing the constraint-name literal as a public
  constant in that file.
- **Fix:** `ScheduleConstraintProvider` gained `SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME` as a
  public constant, and the pre-existing `.asConstraint("Shift envelope compliance")` call was
  swapped to use the constant. Same string value, same registered constraint name — no solver
  behaviour change.
- **Files modified:** `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`
- **Verification:** `git diff --stat` on the change shows +10/-1 lines, purely the constant
  declaration and one literal-to-constant substitution. Confirmed the string value is
  byte-identical to what it replaces. Full suite (610 tests) green, including
  `SolverQualityGuardTest` (10/10, unaffected).
- **Committed in:** `579b090` (Task 1 commit, by the prior interrupted executor)
- **Consequence flagged in the plan's own `<verification>` section:** the plan states `git diff
  --stat -- src/main/java/com/wfm/solver src/main/resources/solverConfig.xml` "must be empty."
  It is NOT empty for this plan — `ScheduleConstraintProvider.java` shows +10/-1. The underlying
  claim the verification step protects ("no solver behaviour is touched") still holds: the change
  is a pure refactor (extract constant, same value), not a constraint-logic change, and
  `SolverQualityGuardTest` and the rest of the solver test suite are unaffected. This is recorded
  explicitly here, and in `15-UAT.md`'s `G-15-32` `resolved_evidence`, rather than silently
  reporting the verification step as fully satisfied.

---

**Total deviations:** 1 auto-fixed (1 missing-critical-functionality-adjacent file addition,
required by the plan's own action text; already committed by the prior session, verified and
accepted by this continuation rather than reverted).
**Impact on plan:** Necessary for correctness (D-08 single-source-of-truth discipline for the
constraint name) and explicitly directed by the plan's own prose, despite the frontmatter listing
gap. No behavioural risk — same string constant, confirmed by the full suite. No scope creep.

## Issues Encountered

**This was a continuation of an interrupted plan.** Task 1 (`579b090`) was committed in a separate,
earlier session that was interrupted mid-Task-2, leaving ~370 lines of uncommitted test code in the
working tree across `ScheduleOutputServiceShiftReportingTest.java` and
`ScheduleServiceShiftSnapshotTest.java`. This continuation's first action on Task 2 was to verify
(not assume) that uncommitted diff against the plan's actual Task 2 requirements: read it in full,
confirmed it matched the `<behavior>` block's five bullets (named-row regression, constant-1104
regression, red-proof, live-path guard scoping, and the read-path invariant applied to every
accepted-schedule assertion), ran it green, then manually exercised both red-proof directions
before committing it as Task 2's commit. No corrections were needed to the pre-existing test code.

**A pre-existing, uncommitted change to `.planning/STATE.md` was found already present in the
working tree before this continuation's first read** (a legitimate update from the prior gap-closure
planning commit `0184a6a`, updating `total_plans` 21→26 and `state_head`/`last_updated` for the five
new gap-closure plans 15-16…15-20). Per this continuation's explicit instructions, `.planning/STATE.md`
is this plan's to update and commit as part of its own tracking step — that pre-existing change was
carried forward and folded into this plan's own STATE.md updates (advance-plan, record-metric,
add-decision, record-session) in the final metadata commit, not discarded or committed separately.

**Two unrelated, pre-existing working-tree modifications were explicitly left untouched and
uncommitted**, per this continuation's instructions: `.planning/phases/14-shift-library-scheduling-mode/14-VERIFICATION.md`
(status/behavior_unverified_items changes, not part of this plan) and
`src/main/resources/sample-data/preferences.xlsx` (binary diff, not part of this plan). Neither was
staged by any commit in this plan.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- `G-15-32` and `G-15-26` are both `status: resolved` in `15-UAT.md`. Five gaps remain open from
  the seven-gap closure round: G-15-21, G-15-23, G-15-24, G-15-25, G-15-31 — addressed by plans
  15-17 through 15-20.
- `ENVL-02` is declared by multiple sibling plans in this phase's gap-closure round (15-01, 15-03,
  15-04, 15-09, 15-11, 15-13, 15-14, 15-18, 15-19, 15-20) and is correctly held `blocked` (not yet
  marked complete a second time) by `requirements.ready-ids` until every declaring plan has its own
  SUMMARY — this plan's individual contribution to it is proven by D2's tests above, but the
  requirement-level checkbox and traceability row were already `[x]`/tracked from an earlier plan,
  so `requirements.mark-complete` for `ENVL-07`/`XCUT-01` was a correct no-op (already complete;
  `git status` confirms `REQUIREMENTS.md` unmodified by this plan).
- Full suite verified green: `./gradlew test` — **610 tests, 0 failures, 0 errors, 2 skipped**, up
  from `15-15`'s recorded 600 by the 10 tests this plan added.
- **Not pushed** — this session did not run `git push`, consistent with prior sessions' caution
  that pushing this branch triggers `deploy.yml` and redeploys ECS with no review gate.

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-09-02*

## Self-Check: PASSED

- FOUND: `src/main/java/com/wfm/service/ScheduleOutputService.java`
- FOUND: `src/main/java/com/wfm/service/ScheduleService.java`
- FOUND: `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`
- FOUND: `src/main/java/com/wfm/controller/GlobalExceptionHandler.java`
- FOUND: `src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java`
- FOUND: `src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java`
- FOUND: `src/test/java/com/wfm/controller/GlobalExceptionHandlerTest.java`
- FOUND commit `579b090` (Task 1)
- FOUND commit `e07d036` (Task 2)
- FOUND commit `7eb77cf` (Task 3, RED)
- FOUND commit `614f8f0` (Task 3, GREEN)
- `./gradlew test` — 610 tests, 0 failures, 0 errors, 2 skipped (up from 600 baseline by exactly
  the 10 tests this plan added)
- `./gradlew test --tests "com.wfm.service.ScheduleOutputServiceShiftReportingTest" --tests
  "com.wfm.service.ScheduleServiceShiftSnapshotTest" --tests
  "com.wfm.controller.GlobalExceptionHandlerTest"` — all green in isolation
