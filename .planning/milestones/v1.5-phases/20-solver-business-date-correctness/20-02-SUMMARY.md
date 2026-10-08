---
phase: 20-solver-business-date-correctness
plan: 02
subsystem: solver
tags: [timefold, constraint-streams, business-date, structural-guard, static-scan, timeslot]

# Dependency graph
requires:
  - phase: 20-01
    provides: "AgentDayConfig.dayStart Quad-arity carrier (unused by this plan, but the desk-anchor channel plans 20-05/20-08 share); the project's four-guard precedent lineage this plan extends to a fifth and sixth"
provides:
  - "BusinessDateJoinGuardTest — a structural, two-directional guard over join/equal/groupBy/computeIfAbsent key positions in ScheduleConstraintProvider, ScheduleOutputService, ShiftLibraryGenerationService and StaffingRequirementService, EXPECTED RED until plans 20-05/20-06/20-07 migrate all four files"
  - "AgentDayDerivationGuardTest — a structural, two-directional guard proving AgentDayConfig.date derives only from the schedule period and AgentShiftAssignment.date only from AgentDayConfig or another AgentShiftAssignment, GREEN today"
  - "AgentShiftAssignment.date javadoc stating the field IS the business date and naming its two-link derivation chain (SOLV-07)"
  - "A documented, measured catalogue of this guard's scope boundaries — five required plus three additional blind spots found while building it, most significantly that the DATE lambda + seven groupBy(AGENT_ID, DATE, ...) consumers in ScheduleConstraintProvider are invisible to a single-line textual scan"
affects: [20-05-join-and-anchor-migration, 20-06-shift-library-and-coverage-migration, 20-07-staffing-requirement-migration, 20-08-day-start-generation-reachability]

# Actuals (#2632)
actuals:
  tokens: 16640
  tasks: 2
  commits: 2

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A sixth structural guard (BusinessDateJoinGuardTest) follows the exact two-directional set-equality technique proven five times already in this codebase, as a NEW class copying the scanning core rather than a fourth scan inside MidnightTimeArithmeticGuardTest (D-08 discretion, following BusinessDateWritePathGuardTest's own precedent of the same choice)"
    - "An intentionally EMPTY allowlist, kept honest by two independent liveness proofs (matcher-level synthetic strings, pipeline-level tracked offender fixture) rather than by a non-empty-allowlist guard — the correct substitute when the guard is meant to start red, not green"
    - "A simple brace-depth method-body extractor (findEnclosingMethodLines) for the 'writer reads the schedule period' second-order assertion, safe for this codebase's one-class-per-file, no-nested-top-level-declaration formatting without full Java parsing"
    - "Exhaustive two-way classification by argument shape (contains .getDate() => propagating, else => deriving by default) rather than positive-match-only classification, so a hypothetical third write path falls into a bucket and fails the build instead of being silently dropped from both"

key-files:
  created:
    - src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java
    - src/test/resources/bday-join-guard.md
    - src/test/resources/bday-join-guard-offender/OffendingSample.java
    - src/test/java/com/wfm/service/AgentDayDerivationGuardTest.java
    - src/test/resources/agent-day-derivation.md
  modified:
    - src/main/java/com/wfm/model/AgentShiftAssignment.java

key-decisions:
  - "BusinessDateJoinGuardTest built as a new class (not a fourth MidnightTimeArithmeticGuardTest scan), matching the plan's D-08 recommendation and BusinessDateWritePathGuardTest's own precedent of the same choice"
  - "Verb+receiver co-occurrence required on the SAME physical source line, matching every <behavior> example in the plan literally — this means the scan finds 12 distinct matched lines today (14 raw occurrences, 2 collapse via identical ScheduleOutputService text), not the plan's narrative '8 ScheduleConstraintProvider sites' / 'two reachable ShiftLibraryGenerationService sites' counts, which conflated edit-surface size with scan-match count"
  - "StaffingRequirementService contributes ZERO matched lines despite being one of the four scanned files — its D-15 delete-range derivation uses .map(Timeslot::getDate).min()/.max(), and neither .min( nor .max( is among the four locked verbs, so this specific site is structurally unreachable by this guard. Documented explicitly in bday-join-guard.md rather than silently accepted"
  - "Scan B's two allowlists classify every matched .setDate( call EXHAUSTIVELY by argument shape (propagating if it contains .getDate(), deriving otherwise) rather than only recognizing the two known-good shapes positively — closes the vacuous-guard gap where a hypothetical rogue calendar-date argument would otherwise match neither bucket and be silently invisible to both scans"
  - "Appended a WINDOWS.md deviation entry (id 10) for the single largest guard blind spot — ScheduleConstraintProvider's shared DATE lambda and its seven groupBy(AGENT_ID, DATE, ...) consumers — so plan 20-05's author sees it before relying on this guard's green to prove that migration correct"

patterns-established:
  - "A guard shipped intentionally RED documents its own expected-red state and liveness proofs in its resource .md file's own prose, not merely in the test class javadoc, so a reader of the allowlist alone understands why zero entries does not mean the guard passes"

requirements-completed: [SOLV-02, SOLV-07]

coverage:
  - id: D1
    description: "BusinessDateJoinGuardTest — structural join-key guard over four files, with two liveness proofs and documented scope boundaries, correctly RED today (12 distinct un-migrated key positions, zero allowlist entries)"
    requirement: "SOLV-02"
    verification:
      - kind: unit
        ref: "com.wfm.service.BusinessDateJoinGuardTest#businessDateJoinKeyPositionsInProductionCode_matchTheAllowlistExactly"
        status: fail
      - kind: unit
        ref: "com.wfm.service.BusinessDateJoinGuardTest#theMatcherDetectsEachReceiverShapeAndRejectsNonKeyPositions"
        status: pass
      - kind: unit
        ref: "com.wfm.service.BusinessDateJoinGuardTest#pipelineRedProof_walkStripMatchAndSetCompareAreAllLive"
        status: pass
      - kind: unit
        ref: "com.wfm.service.BusinessDateJoinGuardTest#setEquality_failsOnBothAnUnlistedOccurrenceAndAStaleEntry"
        status: pass
      - kind: unit
        ref: "com.wfm.service.BusinessDateJoinGuardTest#missingAllowlistHeading_failsLoudly"
        status: pass
      - kind: unit
        ref: "com.wfm.service.BusinessDateJoinGuardTest#allFourTargetFilesExist"
        status: pass
    human_judgment: true
    rationale: "The headline set-equality test is EXPECTED to fail until plans 20-05/20-06/20-07 migrate all four guarded files — a human must confirm this failure is the designed RED state (D-08), since an automated pass/fail check cannot itself distinguish an intentional red guard from a broken one. The five other tests in this class (liveness, both-directions, missing-heading) all pass and ARE the automated proof that the red state is meaningful rather than broken."
  - id: D2
    description: "AgentDayDerivationGuardTest — two-link derivation-chain guard (D-05/D-06), both scans plus the per-occurrence source-expression check, GREEN on first commit"
    requirement: "SOLV-07"
    verification:
      - kind: unit
        ref: "com.wfm.service.AgentDayDerivationGuardTest (11 tests)"
        status: pass
      - kind: other
        ref: "./gradlew test --tests com.wfm.service.BusinessDateWritePathGuardTest --tests com.wfm.service.UsualShiftWritePathGuardTest --tests com.wfm.service.SolverUsualShiftWritePathGuardTest --tests com.wfm.service.MidnightTimeArithmeticGuardTest"
        status: pass
    human_judgment: false
  - id: D3
    description: "AgentShiftAssignment.date field javadoc states it IS the business date and names its two-link derivation, citing SOLV-07"
    verification: []
    human_judgment: true
    rationale: "Prose/documentation quality is not mechanically verifiable — a human confirms the stated semantics match AgentDayDerivationGuardTest's own enforced chain."

duration: 22min
completed: 2026-10-01
status: complete
---

# Phase 20 Plan 2: Business-Date Join Guard and Derivation-Chain Guard Summary

**Two structural policemen: `BusinessDateJoinGuardTest` (RED today, 12 un-migrated key positions across four files, two liveness proofs) and `AgentDayDerivationGuardTest` (GREEN today, proving `AgentShiftAssignment.date` stays derived rather than stored).**

## Performance

- **Duration:** 22 min
- **Started:** 2026-10-01T18:30:24Z
- **Completed:** 2026-10-01T18:51:55Z
- **Tasks:** 2 completed
- **Files modified:** 6 (5 created, 1 modified)

## Accomplishments

- `BusinessDateJoinGuardTest` scans four explicit files (never a tree walk) for
  `join`/`equal`/`groupBy`/`computeIfAbsent` key positions resolving a `Timeslot`'s calendar
  `getDate()`, via a three-shape receiver predicate (chained `.getTimeslot()`, bare `ts`, literal
  `Timeslot::getDate`) that correctly accepts every `<behavior>`-specified positive case and rejects
  the bare-`sa` negative case, a commented-out occurrence, and a string-concatenated display label.
- Two liveness proofs — a matcher-level test against synthetic strings and a pipeline-level test
  against a tracked, never-compiled `OffendingSample.java` fixture — keep the intentionally EMPTY
  allowlist from being vacuously satisfiable.
- `bday-join-guard.md` documents the five scope boundaries the plan required (two display labels,
  one response-DTO field, two non-verb `ShiftLibraryGenerationService` sites, the two Erlang-caller
  exemptions, the three D-09-rejected tokens) **plus three additional, independently measured blind
  spots** found by running the scan against the real tree rather than trusting the plan's narrative
  counts: `ScheduleConstraintProvider`'s shared `DATE` lambda and the seven `groupBy(AGENT_ID, DATE,
  ...)` consumers it feeds are entirely invisible to a single-line scan (symbolic-constant
  indirection); `ShiftLibraryGenerationService:180` uses `.filter(`/`.contains(`, not a scanned verb;
  `StaffingRequirementService:172-175` (the whole reason D-15 added that file) uses
  `.map(Timeslot::getDate).min()/.max()`, also not a scanned verb — so `StaffingRequirementService`
  currently contributes zero matched lines regardless of migration state.
- Confirmed by direct scan run: the guard is RED as designed, with **12 distinct un-migrated key
  positions** (7 in `ScheduleConstraintProvider`, 4 distinct in `ScheduleOutputService` — 3 raw
  occurrences collapse to 1 set entry via identical line text — 1 in `ShiftLibraryGenerationService`).
- `AgentDayDerivationGuardTest` ships two scans, each two-directional: Scan A proves
  `AgentDayConfig` has exactly one construction site (`SolverService.computeAgentDayConfigs`) whose
  enclosing method body reads both `schedule.getPeriodStartDate()` and `schedule.getPeriodEndDate()`
  (via a brace-depth method-body extractor, not merely a count); Scan B proves
  `AgentShiftAssignment#setDate` has exactly two call sites, classified EXHAUSTIVELY by argument
  shape into deriving (`SolverService`, reads `config.date()`) and snapshot-copy/propagating
  (`ScheduleService`, reads `shiftAssignment.getDate()`), with a positive per-occurrence
  source-expression assertion closing the gap a class-name-only equality check would miss.
- `AgentShiftAssignment.date` gained a javadoc line stating it IS the business date and naming its
  two-link derivation, citing `SOLV-07`.
- Green on first commit: 11/11 `AgentDayDerivationGuardTest` tests pass; the three sibling write-path
  guards (`BusinessDateWritePathGuardTest`, `UsualShiftWritePathGuardTest`,
  `SolverUsualShiftWritePathGuardTest`) and `MidnightTimeArithmeticGuardTest` all confirmed
  undisturbed.
- **Wave-close gate (inverted, deliberate per the plan): full unfiltered `./gradlew test` ran 1159
  tests, 4 failed, 4 skipped, 0 errors across all 186 JUnit XML files.** The four failures are
  EXACTLY the two expected classes and nothing else: `BusinessDateJoinGuardTest` (1, this plan's own
  intentional RED) and `MidnightBoundaryRegressionTest$NonMidnightAnchor` (3, plan 20-01's RED guard,
  unresolved until plan 20-05). No other class reports a failure or an error.

## Task Commits

Each task was committed atomically:

1. **Task 1: BusinessDateJoinGuardTest — four files, four verbs, three receiver shapes, set equality** - `d3eb539` (test)
2. **Task 2: AgentDayDerivationGuardTest — the chain that makes "derived, not stored" safe** - `e15ab25` (test)

**Plan metadata:** pending (this commit)

## Files Created/Modified

- `src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java` — the four-file join-key guard, empty allowlist, two liveness proofs
- `src/test/resources/bday-join-guard.md` — its empty allowlist plus five required + three additional documented scope boundaries
- `src/test/resources/bday-join-guard-offender/OffendingSample.java` — tracked, never-compiled synthetic offender for the pipeline liveness proof
- `src/test/java/com/wfm/service/AgentDayDerivationGuardTest.java` — the two-link derivation-chain guard, both scans green
- `src/test/resources/agent-day-derivation.md` — its three allowlists (construction, deriving, propagating), each with exactly one entry
- `src/main/java/com/wfm/model/AgentShiftAssignment.java` — javadoc on the `date` field naming it the business date

## Decisions Made

1. **Verb+receiver co-occurrence required on the same physical line, applied literally.** Every
   `<behavior>` example in the plan shows the verb token and the Timeslot-receiver `.getDate()` on
   one line together, so the matcher requires both on the same comment-stripped line — matching the
   precedent guards' own single-line scanning convention exactly. This produces 12 distinct matched
   entries today, not the plan's narrative counts ("eight `ScheduleConstraintProvider` key
   positions", "two reachable `ShiftLibraryGenerationService` sites") — those counts described the
   *edit surface* a later migration plan must touch, not what THIS guard's literal single-line scan
   can detect. Verified empirically (direct grep against the actual tree) rather than assumed; the
   discrepancy is documented in `bday-join-guard.md`'s "Known scope boundaries" section rather than
   silently absorbed, matching this project's established pattern for plan-narrative-vs-tree drift
   (see `20-01-SUMMARY.md`'s "Issues Encountered").
2. **Scan B's two allowlists classify exhaustively, not by positive match alone.** An occurrence is
   PROPAGATING if its argument contains `.getDate()`; everything else (including a hypothetical
   future rogue calendar-date argument) is DERIVING by default. This mirrors
   `BusinessDateWritePathGuardTest`'s own `isPropagatingCall`/fallback-to-deriving shape exactly, and
   is what keeps a third, undocumented write path from being silently invisible to both scans — it
   would instead surface as an unlisted DERIVING-classified class, failing the build.
3. **A brace-depth method-body extractor, not a fixed-size line window, for Scan A's second
   assertion.** Reusing this codebase's one-class-per-file formatting convention (no nested
   top-level declarations), tracking depth transitions from `<= 1` to `>= 2` safely identifies a
   method's exact line range without full Java parsing. Verified correct on first run against the
   real `SolverService.computeAgentDayConfigs` method.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `bday-join-guard.md` allowlist heading mismatch caused the empty-allowlist parse to throw before the first full test run**
- **Found during:** Task 1, first `./gradlew test` run
- **Issue:** The resource file's heading read `## The allowlist` while the test class's
  `ALLOWLIST_HEADING` constant was `### Allowlist` — `parseFencedBlock` threw
  `IllegalStateException` instead of returning an empty set, masking the intended RED
  (`AssertionError`) state behind a loud parse failure instead.
- **Fix:** Corrected the resource heading to `### Allowlist`, matching the constant.
- **Files modified:** `src/test/resources/bday-join-guard.md`
- **Verification:** Re-ran `./gradlew test --tests "com.wfm.service.BusinessDateJoinGuardTest"` —
  failure mode changed from `IllegalStateException` to the expected `AssertionError` on the
  set-equality test, with the other five tests in the class passing.
- **Committed in:** `d3eb539` (Task 1 commit — caught before the commit, not a follow-up fix)

**2. [Rule 1 - Bug] Javadoc prose literally spelling the plan's own forbidden-pattern grep tokens**
- **Found during:** Task 1, running the plan's `<verify>` acceptance-criteria grep commands
- **Issue:** `grep -cE 'containsAll|isSubsetOf|containsAnyOf'` printed 2, not the required 0 — my own
  javadoc explained what NOT to do ("widening to `isSubsetOf`, `containsAnyOf`...") using the exact
  literal substrings the grep checks for, even though no such weak assertion was ever used in actual
  code. The two existing precedent files (`BusinessDateWritePathGuardTest`,
  `MidnightTimeArithmeticGuardTest`) carry the identical pattern in their own javadoc prose and would
  also fail this exact grep if run against them — a known imprecision in the literal check, not a
  real violation of the underlying principle.
- **Fix:** Rephrased the explanatory javadoc to describe the same forbidden patterns without
  spelling their exact literal method-name substrings ("a looser membership-style check" instead of
  naming `isSubsetOf`/`containsAnyOf` verbatim).
- **Files modified:** `src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java`
- **Verification:** `grep -cE 'containsAll|isSubsetOf|containsAnyOf' ...` now prints 0;
  `grep -c 'containsExactlyInAnyOrderElementsOf' ...` prints 4 (unchanged, still comfortably >= 1).
- **Committed in:** `d3eb539` (Task 1 commit — caught before the commit, not a follow-up fix)

**3. [Rule 2 - Missing Critical] Documented three guard blind spots beyond the plan's required five scope boundaries**
- **Found during:** Task 1, verifying the scan's actual output against the plan's narrative counts
- **Issue:** The plan's `<action>` text states "the eight `ScheduleConstraintProvider` key positions"
  and "the two reachable `ShiftLibraryGenerationService` sites" are part of what makes the guard red.
  Direct verification (grepping the real tree for the four verb tokens) showed only 7 of
  `ScheduleConstraintProvider`'s occurrences, and only 1 of `ShiftLibraryGenerationService`'s,
  actually satisfy the literal verb-on-the-same-line-as-receiver matcher the plan's own `<behavior>`
  block specifies. The missing eighth `ScheduleConstraintProvider` "site" is the shared `DATE` lambda
  (feeding seven `groupBy(AGENT_ID, DATE, ...)` consumers via a symbolic constant, invisible to a
  single-line scan) and the missing second `ShiftLibraryGenerationService` site is line 180
  (`.filter(`/`.contains(`, not a scanned verb). Separately, `StaffingRequirementService` — the file
  D-15 added specifically for its demand-upload delete-range derivation — turns out to contribute
  ZERO matched lines at all, since that derivation uses `.map(Timeslot::getDate).min()/.max()`,
  neither of which is among the four locked verbs.
- **Fix:** Documented all three findings explicitly in `bday-join-guard.md`'s "Known scope
  boundaries" section, under a clearly labelled "Two additional, measured blind spots" subsection
  distinct from the plan's five required entries, naming exact line numbers and the mechanical
  reason each is unreachable — so a future reader finds a documented decision rather than
  rediscovering a gap. Also appended WINDOWS.md deviation entry id 10 for the most consequential one
  (the DATE lambda, covering roughly half of SOLV-01's edit surface) so plan 20-05's author sees it
  before relying on this guard's green to prove that migration correct.
- **Files modified:** `src/test/resources/bday-join-guard.md`; `.planning/WINDOWS.md` (via `gsd_run
  windows append`, not a direct file edit)
- **Verification:** The scan's actual `derived` set (12 entries) was inspected directly from a real
  test run's failure message, not inferred; every entry traces to a verified source line via direct
  `grep -n` against the four target files.
- **Committed in:** `d3eb539` (Task 1 commit)

---

**Total deviations:** 3 auto-fixed (2 Rule 1 bugs caught before commit, 1 Rule 2 documentation
addition). **Impact on plan:** None of the three affected the guard's actual enforcement behavior or
required acceptance criteria — all required acceptance-criteria text in `bday-join-guard.md` is
present verbatim, and both deviation fixes were applied and verified before either task's commit, so
no commit needed a follow-up correction. The Rule 2 addition is purely more-honest documentation of
what the shipped guard does and does not catch, consistent with the guard's own stated design
philosophy that its scope boundaries are "a deliverable, not a side-effect."

## Issues Encountered

None beyond the three documented deviations above — no blockers, no auth gates, no unresolved
ambiguity requiring a checkpoint.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

**Ready for plan 20-03.** Both guards this plan builds are now live structural policemen for the
remainder of the phase:

- `AgentDayDerivationGuardTest` is green and will fail the build the moment a future writer sets
  `AgentDayConfig.date` or `AgentShiftAssignment.date` from anything other than the schedule period
  or an already-derived date — D-05's "no stored column" trade is now backed by a test.
- `BusinessDateJoinGuardTest`'s new-and-unlisted set, recorded verbatim below per this plan's
  `<output>` instruction, is plan 20-05/20-06's literal work order — every line listed there must
  either be re-pointed to `getBusinessDate()` or receive a documented allowlist entry before the
  guard goes green:

```
com.wfm.solver.ScheduleConstraintProvider :: equal(a -> a.getTimeslot().getDate(), AgentDayOff::getDate))
com.wfm.solver.ScheduleConstraintProvider :: equal(a -> a.getTimeslot().getDate(), AgentDayConfig::date))
com.wfm.solver.ScheduleConstraintProvider :: equal((sa, cfg) -> sa.getDate(), a -> a.getTimeslot().getDate()))
com.wfm.solver.ScheduleConstraintProvider :: equal(AgentShiftAssignment::getDate, a -> a.getTimeslot().getDate()))
com.wfm.solver.ScheduleConstraintProvider :: equal(AgentDayConfig::date, a -> a.getTimeslot().getDate()))
com.wfm.solver.ScheduleConstraintProvider :: equal(a -> a.getTimeslot().getDate(), AgentPreference::getDate))
com.wfm.solver.ScheduleConstraintProvider :: .join(Timeslot.class, equal((sa, cfg) -> sa.getDate(), Timeslot::getDate))
com.wfm.service.ScheduleOutputService :: .computeIfAbsent(sr.getTimeslot().getDate(), k -> new LinkedHashMap<>())
com.wfm.service.ScheduleOutputService :: .computeIfAbsent(a.getTimeslot().getDate(), k -> new LinkedHashMap<>())
com.wfm.service.ScheduleOutputService :: timeslotsByDate.computeIfAbsent(ts.getDate(), k -> new ArrayList<>()).add(ts);
com.wfm.service.ScheduleOutputService :: .computeIfAbsent(a.getTimeslot().getDate(), k -> new ArrayList<>())
com.wfm.service.ShiftLibraryGenerationService :: byWeekday.computeIfAbsent(sr.getTimeslot().getDate().getDayOfWeek(), k -> new TreeMap<>())
```

(The last `ScheduleOutputService` entry represents three identical raw occurrences — lines 173, 323
and 739 — collapsed to one set member by identical line text; all three physical lines need the same
edit.) **Important for plan 20-05/20-06's own planning**: this guard's green will NOT by itself prove
`ScheduleConstraintProvider`'s `DATE` lambda (lines 91-92) and its seven `groupBy(AGENT_ID, DATE,
...)` consumers (lines 299, 356, 389, 438, 692, 718, 1109) are correctly migrated, nor
`ShiftLibraryGenerationService:180`, nor `StaffingRequirementService:172-175` — all documented as
blind spots in `bday-join-guard.md`; those sites' correctness must be verified by direct code review.

No blockers.

---
*Phase: 20-solver-business-date-correctness*
*Completed: 2026-10-01*

## Self-Check: PASSED

- FOUND: src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java
- FOUND: src/test/resources/bday-join-guard.md
- FOUND: src/test/resources/bday-join-guard-offender/OffendingSample.java
- FOUND: src/test/java/com/wfm/service/AgentDayDerivationGuardTest.java
- FOUND: src/test/resources/agent-day-derivation.md
- FOUND: src/main/java/com/wfm/model/AgentShiftAssignment.java
- FOUND: commit d3eb539 (Task 1)
- FOUND: commit e15ab25 (Task 2)
- Acceptance criteria re-verified: `grep -cE 'containsAll|isSubsetOf|containsAnyOf'` → 0 in both new
  test files; `grep -c 'containsExactlyInAnyOrderElementsOf'` → 4 (join guard), 3 (derivation guard);
  `grep -cE 'getPeriodStartDate|getPeriodEndDate'` in derivation guard → 6;
  `git ls-files --error-unmatch src/test/resources/bday-join-guard-offender/OffendingSample.java` → exit 0.
- Plan-level `<verification>` re-run: `./gradlew compileJava compileTestJava` → BUILD SUCCESSFUL;
  `./gradlew test --tests "com.wfm.service.AgentDayDerivationGuardTest"` → BUILD SUCCESSFUL (11/11
  pass); `./gradlew test --tests "com.wfm.service.BusinessDateJoinGuardTest"` → BUILD FAILED, failing
  only the set-equality test (5/6 pass); sibling guards
  (`BusinessDateWritePathGuardTest`/`UsualShiftWritePathGuardTest`/`SolverUsualShiftWritePathGuardTest`/`MidnightTimeArithmeticGuardTest`)
  → BUILD SUCCESSFUL; unfiltered `./gradlew test` → 1159 tests, 4 failed, 4 skipped, 0 errors across
  all 186 JUnit XML result files, failures confined to exactly `BusinessDateJoinGuardTest` and
  `MidnightBoundaryRegressionTest$NonMidnightAnchor`.
