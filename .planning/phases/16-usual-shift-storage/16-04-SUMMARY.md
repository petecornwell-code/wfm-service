---
phase: 16-usual-shift-storage
plan: 04
subsystem: scheduling
tags: [reflection-guard, structural-completeness, timefold, testcontainers, junit5]

# Dependency graph
requires:
  - phase: 16-usual-shift-storage
    provides: "plan 16-01's AgentUsualShift/AgentUsualShiftRepository/UsualShiftService.setUsualShift+clearUsualShifts; plan 16-02's DeskAgentService.removeDeskAgent clearUsualShifts call site and ShiftTemplateService.deleteShiftTemplate guard; plan 16-03's DeskAssignmentUploadService row-import/clearDesk call sites"
provides:
  - "The USHF-05 canonical write-path table (src/test/resources/ushf-05-write-paths.md) — nine
    rows, D-14's seven plus P-18's two planner additions (template delete, desk delete) — parsed
    at test time, not decorative"
  - "UsualShiftWritePathGuardTest — the D-14 structural completeness guard: dual independent
    derivations (repository-type scan, entity-type scan with word-boundary exclusion), set
    equality only, red-proven by a test-of-the-test and a recorded manual deliberate-break check"
  - "UsualShiftWritePathTest — behavioural + structural proofs for the three table rows no earlier
    plan exercises: BambooHR refresh, scheduling-mode switch, and an actual bounded Timefold solve"
affects: [17-consistency-constraint-drift-reporting]

# Actuals (#2632)
actuals:
  tokens: 12643
  tasks: 2
  commits: 2

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Static source-scan structural completeness guard (RESEARCH.md Pitfall 3 option 2, P-16):
      Files.walk over src/main/java, dual literal/word-boundary-regex derivation, set equality
      against a table-parsed allowlist rather than a hard-coded Java list — no Spring context,
      sub-second, mirrors MigrationEntityConsistencyTest's classpath-resource-reading posture
      extended to a raw source-tree walk"
    - "Solver-level wall-clock safety cap (SecondsSpentLimit) layered ON TOP OF a per-phase
      step-count termination override, not instead of it — the step-count-only technique
      SolverQualityGuardTest uses hung indefinitely on a trivial 4-entity SLOT-mode fixture built
      from solverConfig.xml's two-construction-heuristic-phase shape with one phase's entity
      collection deliberately empty; the solver-level termination is the only thing that reliably
      bounded it (see Deviations)"

key-files:
  created:
    - src/test/resources/ushf-05-write-paths.md
    - src/test/java/com/wfm/service/UsualShiftWritePathGuardTest.java
    - src/test/java/com/wfm/service/UsualShiftWritePathTest.java
  modified: []

key-decisions:
  - "P-16 adopted verbatim: static source scan over a Spring-bean reflective scan for the D-14
    guard (no Spring context needed, sub-second, matches the codebase's structural-absence idiom
    widened from one file to a directory walk)"
  - "Row 7 (the solver) cannot reuse LiveShapeShiftDeskFixture as the plan's P-19 anticipated as the
    likely choice: that class is package-private in com.wfm.solver, inaccessible from this plan's
    fixed file path (src/test/java/com/wfm/service/). Built the smallest possible SLOT-mode
    fixture instead (1 agent, 4 timeslots), matching SingleDaySolvableTest's construction shape
    scaled down, and ran it through the real solverConfig.xml. Documented as a P-19 finding, not a
    deviation from the plan's intent — the plan's own text names this exact possibility ('If no
    existing fixture can be reused under that budget, report it as a finding')"
  - "The guard's two fenced allowlists include the AgentUsualShift/AgentUsualShiftRepository
    declaration files themselves (the entity and repository classes), not only the nine table
    rows' writer classes — the guard's textual scan is 'does this file reference the type at all',
    and UsualShiftResolutionService (a reader, not a writer) also appears in the entity-reference
    allowlist for the same reason. This is correct per the plan's own instruction ('an explicit
    list of the production classes that legitimately reference...') and does not weaken the guard:
    a NEW writer still cannot land without its own row"

requirements-completed: []  # USHF-05 and XCUT-02 were already marked complete by 16-02-SUMMARY.md
# (the first of three plans declaring them to finish, per the shared-ID gate #2388); this plan's
# own share of that work -- the structural guard and the three previously-unexercised rows -- is
# the LAST piece, closing USHF-05/XCUT-02 out fully, but there is nothing left to newly mark.

coverage:
  - id: D1
    description: "A single canonical table enumerates all nine reachable usual-shift write paths (D-14's seven plus P-18's two planner additions), each stating what must hold and naming a test that actually exercises that path"
    requirement: USHF-05
    verification:
      - kind: unit
        ref: "UsualShiftWritePathGuardTest#theTableHasExactlyNineDataRows_withNoBlankRequiredCells"
        status: pass
      - kind: unit
        ref: "UsualShiftWritePathGuardTest#everyProvingTestNamedInTheTable_resolvesToAnExistingClass"
        status: pass
    human_judgment: false
  - id: D2
    description: "A structural completeness guard fails the build when a new writer of AgentUsualShiftRepository or the AgentUsualShift entity type appears anywhere in src/main/java without a corresponding table row, using set equality only (never subset/containment)"
    requirement: XCUT-02
    verification:
      - kind: unit
        ref: "UsualShiftWritePathGuardTest#repositoryReferenceSet_matchesTheTableAllowlistExactly"
        status: pass
      - kind: unit
        ref: "UsualShiftWritePathGuardTest#entityReferenceSet_matchesTheTableAllowlistExactly"
        status: pass
    human_judgment: false
  - id: D3
    description: "The guard is proven able to actually fail: a test-of-the-test removes one entry from a copy of the allowlist and asserts the equality check trips, AND a real deliberate-break manual check (a field temporarily added to a class outside the allowlist) produces a failure message naming that exact class"
    requirement: XCUT-02
    verification:
      - kind: unit
        ref: "UsualShiftWritePathGuardTest#deliberatelyBrokenAllowlist_isDetectedAsAMismatch"
        status: pass
      - kind: manual_procedural
        ref: "Deliberate-break check against UsualShiftResolutionService.java, see Deviations section for the exact captured failure message"
        status: pass
    human_judgment: false
  - id: D4
    description: "BambooHR refresh (row 5) leaves stored usual shifts byte-identical, proven both behaviourally (real persisted rows, real refreshDeskAgents call, re-read and compared) and structurally (no AgentUsualShiftRepository-typed field on BambooRefreshService)"
    requirement: USHF-05
    verification:
      - kind: integration
        ref: "UsualShiftWritePathTest#refreshDeskAgents_leavesStoredUsualShiftsByteIdentical_behavioural"
        status: pass
      - kind: unit
        ref: "UsualShiftWritePathTest#refreshDeskAgents_declaresNoAgentUsualShiftRepositoryField_structural"
        status: pass
    human_judgment: false
  - id: D5
    description: "A SLOT-SHIFT-SLOT mode-switch round trip leaves every stored usual-shift row field-identical in both directions (D-13), proven the same field-by-field way MODE-04 was"
    requirement: USHF-05
    verification:
      - kind: integration
        ref: "UsualShiftWritePathTest#switchSchedulingMode_roundTrip_leavesStoredUsualShiftsFieldIdentical"
        status: pass
    human_judgment: false
  - id: D6
    description: "An actual Timefold solve run leaves stored usual-shift rows field-identical and produces non-vacuous output, plus the solver package and SolverService structurally hold no reference to AgentUsualShiftRepository/AgentUsualShift"
    requirement: USHF-05
    verification:
      - kind: integration
        ref: "UsualShiftWritePathTest#solve_leavesStoredUsualShiftsUntouched_andProducesRealOutput"
        status: pass
      - kind: unit
        ref: "UsualShiftWritePathTest#solverPackageAndSolverService_declareNoAgentUsualShiftReference_structural"
        status: pass
    human_judgment: false
  - id: D7
    description: "Full backend suite stays green at or above the 709-test baseline after this plan's additions"
    verification:
      - kind: integration
        ref: "./gradlew test (720 tests, 0 failures, 2 pre-existing ignored)"
        status: pass
    human_judgment: false

# Metrics
duration: ~43min
completed: 2026-09-03
status: complete
---

# Phase 16 Plan 04: Write-Path Structural Guard Summary

**Nine-row USHF-05 write-path table plus a static-source-scan structural completeness guard (dual independent derivation, set-equality-only, red-proven by both a code-level test-of-the-test and a real deliberate-break manual check) — closing the exact shape of v1.2 audit finding I-2, where a guarantee held on one write path and stayed open across two consecutive audits.**

## Performance

- **Duration:** ~43 min
- **Started:** 2026-09-03T17:20:00Z (approx.)
- **Completed:** 2026-09-03T18:03:28Z
- **Tasks:** 2
- **Files modified:** 3 (all created, zero production code)

## Accomplishments
- `src/test/resources/ushf-05-write-paths.md` — the canonical USHF-05 document: nine rows (D-14's seven paths plus P-18's two planner additions, template delete and desk delete), each naming its entry point, source file, required guarantee, and proving test, plus two fenced allowlists the guard parses.
- `UsualShiftWritePathGuardTest` — the D-14 structural completeness guard. Two independent derivations over a live `Files.walk` of `src/main/java` (files containing `AgentUsualShiftRepository`; files containing `AgentUsualShift` via a word-boundary regex excluding the Repository suffix), asserted with `containsExactlyInAnyOrderElementsOf` against the table's parsed allowlists — never a subset/containment check. Six tests, all passing.
- `UsualShiftWritePathTest` — discharges the three table rows no earlier plan exercises: BambooHR refresh (behavioural real-DB proof + structural reflection proof), scheduling-mode switch (field-by-field round trip, mirroring MODE-04's shape), and an actual bounded Timefold solve (production `solverConfig.xml`, real persisted usual-shift row proven untouched, non-vacuous solved output asserted).
- The guard is proven able to fail, twice over: a code-level test-of-the-test (`deliberatelyBrokenAllowlist_isDetectedAsAMismatch`) and a real manual deliberate-break (a field temporarily added to `UsualShiftResolutionService.java`, run, confirmed red, reverted — exact message below).
- Full backend suite: **720 tests, 0 failures, 2 pre-existing ignored, in 13m 31s** — up from the 709-test baseline `16-03-SUMMARY.md` recorded (709 + 6 guard-test methods + 5 write-path-test methods = 720, exact match).

## Task Commits

Each task was committed atomically:

1. **Task 1: The USHF-05 table and the structural completeness guard (D-14)** - `fa3d73a` (test)
2. **Task 2: Exercise the three paths no earlier plan runs (refresh, mode switch, solver)** - `461c7f4` (test)

**Plan metadata:** committed separately after this SUMMARY (see below).

_Note: both files for Task 2 (the write-path table's rows 5-7 proofs) were written and locally verified together with Task 1's guard before either commit, since the guard's `Class.forName` check on the `Proving test` column requires `UsualShiftWritePathTest` to already exist and compile — see Deviations for why this ordering constraint is structural, not a scope violation._

## Files Created/Modified
- `src/test/resources/ushf-05-write-paths.md` — the nine-row table + two fenced allowlists
- `src/test/java/com/wfm/service/UsualShiftWritePathGuardTest.java` — new, 6 test methods
- `src/test/java/com/wfm/service/UsualShiftWritePathTest.java` — new, 5 test methods

## Decisions Made
- Adopted P-16 verbatim: static source scan (RESEARCH.md Pitfall 3 option 2) over a Spring-bean reflective scan for the D-14 guard — no Spring context needed, sub-second, and the only option with any precedent shape in this codebase (`DeskAssignmentUploadMultiSheetTest`'s structural-absence idiom, `ScheduleConstraintClassificationTest`'s dual-derivation-equality idiom).
- Row 7's fixture choice deviated from the plan's most-likely-anticipated path (`LiveShapeShiftDeskFixture`) for a discovered structural reason: that class is package-private in `com.wfm.solver`, and this plan's file lives in `com.wfm.service` — Java's own access rules rule it out, not a judgment call. Built the smallest workable SLOT-mode fixture instead (1 agent, 4 timeslots, matching `SingleDaySolvableTest`'s shape scaled down) and ran it through the real `solverConfig.xml`. This is exactly the finding P-19's own escape clause anticipates ("If no existing fixture can be reused under that budget, report it as a finding rather than building a bespoke full-scale solve") — reported here rather than silently substituted.
- The two fenced allowlists include the `AgentUsualShift`/`AgentUsualShiftRepository` declaration files themselves and `UsualShiftResolutionService` (a reader, not a writer) — correct per the plan's literal instruction ("an explicit list of the production classes that legitimately reference..."), since the guard's textual scan finds every reference, not only writes. This does not weaken the guard: a genuinely new WRITER still has no row to hide behind.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Row 7's solve harness hung indefinitely with only a per-phase step-count termination override; fixed by adding a solver-level wall-clock safety cap**
- **Found during:** Task 2, first attempt at `solve_leavesStoredUsualShiftsUntouched_andProducesRealOutput`
- **Issue:** Following `SolverQualityGuardTest#solve`'s exact technique (`SolverConfig.createFromXmlResource("solverConfig.xml")`, then overriding only the LAST phase's `TerminationConfig` with a `stepCountLimit`), the minimal SLOT-mode fixture (1 agent, 4 timeslots, `AgentShiftAssignment` list left at its default-empty state) never returned — a real `./gradlew test` run on this single method ran for 6+ minutes at 150%+ CPU before being killed. `SolverQualityGuardTest`'s own fixture always carries a non-empty `AgentShiftAssignment` list; this codebase has no existing precedent for solving `solverConfig.xml`'s two-construction-heuristic-phase config with one phase's entity collection deliberately empty, and the per-phase-only termination override was not sufficient to bound it.
- **Fix:** Added a solver-LEVEL `TerminationConfig` (`solverConfig.setTerminationConfig(new TerminationConfig().withSecondsSpentLimit(5L))`) layered on top of the existing per-phase step-count override, guaranteeing termination regardless of which phase the slowdown was in. Also reduced the per-phase step count from 200 to 50, though the wall-clock cap is what actually bounds the run in practice (the solve method now measures and asserts its own elapsed time is under the 90-second budget on every run).
- **Files modified:** `src/test/java/com/wfm/service/UsualShiftWritePathTest.java` (the `solve()` private helper only).
- **Verification:** Isolated re-run of the single test method: green, 5.631s. Re-run alongside the rest of `UsualShiftWritePathTest` and `UsualShiftWritePathGuardTest`: all 11 tests green, class total 6.045s (`UsualShiftWritePathTest`) + 1.44s (`UsualShiftWritePathGuardTest`).
- **Committed in:** `461c7f4` (Task 2's own commit — the harness never existed in a passing state before this fix, so there is no separate "before" commit to distinguish it from).

---

**Total deviations:** 1 auto-fixed (Rule 3 — a blocking build-hang risk in test-only code, fixed before either task was committed). **Impact:** No production-code change, no scope creep — the fix is confined to one private test-helper method. Without it, this plan could not have committed a passing state at all.

## Issues Encountered

**The `Class.forName`-resolution requirement on Task 1's own guard created a forward reference to Task 2's not-yet-written file.** The plan's Task 1 acceptance criteria require `UsualShiftWritePathGuardTest` to pass in isolation, including its check that every `Proving test` value in the table resolves via `Class.forName` — and three of the nine table rows name `UsualShiftWritePathTest`, the class Task 2 (not Task 1) creates. Resolved by writing both files' full content before running either task's `<verify>` command, then splitting the two commits exactly as the plan's `<files>` lists specify (Task 1's commit stages only the table and the guard; Task 2's commit stages only the write-path test) — git staging is independent of the order code was authored on disk, so both tasks' own verification commands genuinely passed in isolation at commit time. Not a deviation from the plan's intent, just the mechanical resolution of an ordering dependency the plan's own acceptance criteria created.

## Deliberate-Break Check (Task 1 acceptance criterion, recorded verbatim)

Temporarily added `private final com.wfm.repository.AgentUsualShiftRepository deliberateBreakCheckField;` (initialized to `null` in the constructor) to `src/main/java/com/wfm/service/UsualShiftResolutionService.java` — a class that legitimately references the `AgentUsualShift` ENTITY type (it is in the Set B allowlist) but was NOT in the Set A (repository) allowlist. Ran `./gradlew test --tests UsualShiftWritePathGuardTest`. Result: **BUILD FAILED**, with this exact failure message:

```
java.lang.AssertionError: [Classes referencing AgentUsualShiftRepository in src/main/java must
equal the table's Set A allowlist exactly. Missing a row for (add a row to
ushf-05-write-paths.md describing what the new writer guarantees, do NOT just add the class name
and move on): [com.wfm.service.UsualShiftResolutionService]. Stale rows naming a class that no
longer references the type (remove the row): [].]
Expecting actual:
  ["com.wfm.service.DeskAgentService",
    "com.wfm.service.DeskAssignmentUploadService",
    "com.wfm.service.ShiftTemplateService",
    "com.wfm.service.UsualShiftService",
    "com.wfm.repository.AgentUsualShiftRepository",
    "com.wfm.service.UsualShiftResolutionService"]
to contain exactly in any order:
  ["com.wfm.service.DeskAgentService",
    "com.wfm.service.DeskAssignmentUploadService",
    "com.wfm.service.ShiftTemplateService",
    "com.wfm.service.UsualShiftService",
    "com.wfm.repository.AgentUsualShiftRepository"]
but the following elements were unexpected:
  ["com.wfm.service.UsualShiftResolutionService"]
```

The message names the exact offending class and directs the reader to add a table row (not just extend the allowlist), matching the acceptance criterion. Reverted immediately after capture (`git diff --stat` confirmed a clean, zero-diff file afterward); the guard was re-run green before proceeding.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All nine USHF-05 table rows are now backed by a passing test, and the table's completeness is enforced by a guard proven able to fail — the phase's most-named risk (repeating v1.2 audit finding I-2) is closed.
- Phase 17 (Consistency Constraint & Drift Reporting) inherits a proven, guarded write-path inventory: any new solver-side write to `agent_usual_shift` this guard would catch immediately, by design — Phase 17 is explicitly told not to add such a write (target vs. result stay distinct fields), and this guard is now the mechanical backstop for that rule, not just a written intention.
- No blockers. One item worth a human's eventual attention, not blocking: the solve harness's wall-clock safety cap (5s) means `solve_leavesStoredUsualShiftsUntouched_andProducesRealOutput`'s runtime is dominated by the cap itself rather than genuine convergence — functionally correct and safely bounded, but if a future maintainer wants the test to demonstrate genuine local-search convergence rather than a timeout-bounded partial search, the fixture would need enough entities/constraints to make convergence meaningful within a shorter, non-safety-net-dominated window.

## Self-Check: PASSED

All 3 created files verified present on disk (`ushf-05-write-paths.md`, `UsualShiftWritePathGuardTest.java`,
`UsualShiftWritePathTest.java`); both commit hashes (`fa3d73a`, `461c7f4`) verified present in
`git log --oneline`; plan-level `<verification>` commands re-run green after all edits
(`./gradlew test --tests UsualShiftWritePathGuardTest --tests UsualShiftWritePathTest` and the
mandatory full `./gradlew test`, 720 tests, 0 failures, 2 pre-existing ignored, 13m 31s).

---
*Phase: 16-usual-shift-storage*
*Completed: 2026-09-03*
