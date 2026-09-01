---
phase: 15-shift-envelope-breaks-library-generation
plan: 15
subsystem: testing
tags: [timefold, junit, assertj, solver-quality-guard, shift-mode, gap-closure]

requires:
  - phase: 15 (plan 15-14)
    provides: LiveShapeShiftDeskFixture and SolverQualityGuardTest -- the guard this plan proves can fail
provides:
  - "Seven new tests in SolverQualityGuardTest: five red-proofs (each structural invariant walker demonstrated to flag exactly its own injected defect, plus a negative control that the break window is never mistaken for a hole), a thesis proof (score-derived evidence goes blind to a defect the structural walker still sees, at zero weight), and a failure-report content proof (every load-bearing element of buildQualityReport asserted individually on the returned string)"
  - "The documented Solver Comparison Rule (G-15-29) in 15-BENCHMARK.md, with measured evidence including a new third live run (aaf17313) not previously recorded anywhere"
  - "The Solver Quality Guard baseline (G-15-22) recorded verbatim in 15-BENCHMARK.md: fixture parameters, per-seed table, per-constraint table, ceiling arithmetic, runtime, and an explicit 'what this guard does not cover' statement"
  - "G-15-22 and G-15-29 closed in 15-UAT.md with measured resolved_evidence, including an explicit, honest statement that back-testing against the original failing acceptor commit was NOT attempted"
affects: []

actuals:
  tokens: 12000
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Red-proofs corrupt an ALREADY-SOLVED schedule (unseat/null-pair) rather than re-solving under a degraded configuration, so every proof is deterministic and carries no search variance of its own"
    - "Exact-identity assertions (hasSize(1) + agent/date/hour identity), never isNotEmpty() -- a walker that flags everything is as useless as one that flags nothing"
    - "Mechanical thesis proof: one fixed corrupted schedule, one solve, weight mutated in place between two explain() calls -- never a second solve, so the comparison carries no variance of its own"

key-files:
  created: []
  modified:
    - src/test/java/com/wfm/solver/SolverQualityGuardTest.java
    - .planning/phases/15-shift-envelope-breaks-library-generation/15-BENCHMARK.md
    - .planning/phases/15-shift-envelope-breaks-library-generation/15-UAT.md
    - .planning/phases/15-shift-envelope-breaks-library-generation/deferred-items.md

key-decisions:
  - "Task 1 and Task 2's test additions were made as one editing pass but split back into two atomic commits by hunk (git apply --cached against a hand-built patch), so each commit's tests independently compile and pass, matching the plan's per-task commit contract"
  - "The pre-existing, already-on-disk-but-uncommitted change to G-15-27's status in 15-UAT.md (not authored by this execution -- present before this session's first Read) was deliberately left untouched and unstaged, via hunk-level git apply --cached, rather than swept into this plan's commit or reverted -- the plan's own instruction is 'touch no other gap entry,' and reverting it would have been an equally unauthorized touch in the other direction. See Issues Encountered."
  - "solveCleanFixture() asserts all three structural walkers empty as a precondition, so no red-proof can accidentally measure a pre-existing defect rather than the one it just injected"
  - "relocateSeat() was added per the plan's explicit artifact list even though no red-proof in this round needs seat relocation (every corruption here is subtractive) -- it exists so a future corruption case that DOES need to move a seat does not reinvent ShiftEnvelopeGroundTruthTest's in-place-mutation mistake"

requirements-completed: [ENVL-04, XCUT-04]

coverage:
  - id: D1
    description: "Each of the three invariant walkers is proven able to go RED against a deliberately corrupted schedule, naming exactly the injected defect and nothing else"
    requirement: "ENVL-04"
    verification:
      - kind: unit
        ref: "SolverQualityGuardTest#redProof_INV1_aNonBreakHoleIsFlaggedOnceOnTheRightAgentDay"
        status: pass
      - kind: unit
        ref: "SolverQualityGuardTest#redProof_INV1_theBreakWindowItselfIsNotAHole"
        status: pass
      - kind: unit
        ref: "SolverQualityGuardTest#redProof_INV1_aNullShiftPairIsFlagged"
        status: pass
      - kind: unit
        ref: "SolverQualityGuardTest#redProof_INV2_aBreakWithNoWorkOnOneSideIsFlagged"
        status: pass
      - kind: unit
        ref: "SolverQualityGuardTest#redProof_INV3_anUnstaffedEdgeHourIsFlaggedForThatDateAndHourOnly"
        status: pass
    human_judgment: false
  - id: D2
    description: "The plan's central thesis demonstrated mechanically: on one fixed corrupted schedule, the per-constraint violation-count table goes blind when a weight is set to zero while the structural walker still reports the defect"
    requirement: "XCUT-04"
    verification:
      - kind: unit
        ref: "SolverQualityGuardTest#thesisProof_atZeroWeightTheViolationCountTableGoesBlind_butTheWalkerStillSeesTheSplit"
        status: pass
    human_judgment: false
  - id: D3
    description: "The failure report is asserted on directly -- invariant name, run parameters, offending rows, per-constraint table, and the compare-counts-not-scores guidance are each proven present, not assumed from a green test"
    verification:
      - kind: unit
        ref: "SolverQualityGuardTest#failureReport_namesTheBrokenInvariantAndCarriesTheComparisonGuidance"
        status: pass
    human_judgment: false
  - id: D4
    description: "The solver comparison rule G-15-29 asked for is written down where an engineer will find it, with measured evidence attached"
    requirement: "XCUT-04"
    verification:
      - kind: other
        ref: "15-BENCHMARK.md 'Solver Comparison Rule (G-15-29)' section, append-only diff verified"
        status: pass
    human_judgment: false
  - id: D5
    description: "G-15-22 and G-15-29 marked resolved in 15-UAT.md with measured evidence, in the resolved_by/resolved_evidence shape G-15-27/G-15-30 established, and only those two entries touched"
    verification:
      - kind: other
        ref: "git diff on 15-UAT.md, confirmed to touch only the G-15-22 and G-15-29 blocks (hunk-level staging)"
        status: pass
    human_judgment: true
    rationale: "The resolved_evidence text, including the honest back-test-not-attempted statement, is a judgment call about what counts as sufficient evidence and honest disclosure -- a human should read it, not just confirm the grep counts pass."

duration: 45min
completed: 2026-09-01
status: complete
---

# Phase 15 Plan 15: Solver Quality Guard Proven Able to Fail (G-15-22 / G-15-29 gap closure) Summary

**Seven new tests prove `SolverQualityGuardTest`'s three structural walkers can each go red on exactly their own injected defect, mechanically demonstrate that a weight change blinds the violation-count table to a defect the walker still sees, and the comparison rule G-15-29 demanded is now written down with measured evidence (including a new third live run) — both gaps closed in `15-UAT.md` with an explicit, honest statement of what was and was not back-tested.**

## Performance

- **Duration:** ~45 min
- **Tasks:** 3
- **Commits:** 3
- **Files modified:** 4 (1 test file, 3 planning documents)

## Accomplishments

- Five red-proofs corrupt an already-solved clean schedule and assert each of the three structural
  walkers (`findSplitShifts`, `findEdgeBreaks`, `findUnstaffedEdgeHours`) flags EXACTLY its own
  injected defect — exact size, exact identity, never merely `isNotEmpty()` — including the
  null-shift-pair laundering case and a negative control proving the break window itself is never
  mistaken for a hole.
- `thesisProof_atZeroWeightTheViolationCountTableGoesBlind_butTheWalkerStillSeesTheSplit` reduces
  G-15-29's whole argument to one deterministic assertion: on one fixed corrupted schedule (single
  solve, no re-solve), `hardMatchCountsByConstraint` shows the corrupted constraint at the live
  weight and loses it entirely at `ofHard(0)`, while the independent structural walker returns the
  identical hole in both readings.
- `failureReport_namesTheBrokenInvariantAndCarriesTheComparisonGuidance` asserts on the
  `buildQualityReport` string element by element — invariant identifier, run parameters, offending
  row identity, per-constraint table row, comparison guidance, and the `G-15-29` pointer.
- `15-BENCHMARK.md` gained two append-only sections (verified via diff to contain zero deletions
  above the previous end-of-file): the Solver Comparison Rule (G-15-29), five numbered items each
  with measured evidence, and the Solver Quality Guard (G-15-22) baseline, transcribed verbatim
  from `15-14-SUMMARY.md`.
- `G-15-22` and `G-15-29` are `status: resolved` in `15-UAT.md`, in the established
  `resolved_by`/`resolved_evidence` shape, with G-15-22 stating explicitly that the guard has NOT
  been back-tested against the original failing acceptor commit (which was already reverted before
  the guard existed) and why that back-test was not attempted.

## Task Commits

Each task was committed atomically:

1. **Task 1: Prove each invariant walker can go red, exactly once, on exactly the injected defect** — `5b19307` (test)
2. **Task 2: Demonstrate the score going blind while the walker does not, and assert the failure report's content** — `c243d78` (test)
3. **Task 3: Write down the comparison rule, record the baseline, and close the two gap entries** — `f452da6` (docs)

**Plan metadata:** not yet committed at the time of this writing — see `git_commit_metadata` step below.

_Note: Task 1's additions were written together with Task 2's in a single editing pass, then split
back into two independently-compiling, independently-passing commits via hunk-level
`git apply --cached` against a hand-built patch (Task 1's five red-proofs + three helpers first,
verified `10 tests` reduced to `8` and green; Task 2's two tests + `countOccurrences` helper
re-applied and verified `10/10` green before the second commit)._

## Files Created/Modified

- `src/test/java/com/wfm/solver/SolverQualityGuardTest.java` — seven new `@Test` methods, three
  new helpers (`solveCleanFixture`, `unseat`, `relocateSeat`), plus `heldStartCount` and
  `countOccurrences` test-utility helpers
- `.planning/phases/15-shift-envelope-breaks-library-generation/15-BENCHMARK.md` — two new
  append-only sections, dated 2026-09-01
- `.planning/phases/15-shift-envelope-breaks-library-generation/15-UAT.md` — `G-15-22` and
  `G-15-29` amended in place to `status: resolved`
- `.planning/phases/15-shift-envelope-breaks-library-generation/deferred-items.md` — new
  `## 15-14 / 15-15` section (staleness check + the `BreakAwareConstructionTest` cross-reference)

## Decisions Made

- **`relocateSeat` added but not called by any test in this round.** The plan's artifact list
  requires it (`Add the corruption helpers, both modelled on ShiftEnvelopeGroundTruthTest's:
  unseat(...), relocateSeat(...)`), but every red-proof here is subtractive (unseat / null a pair),
  none needs to MOVE a seat. Kept, `@SuppressWarnings("unused")`, documented as available for a
  future corruption case that does need relocation, so that case doesn't reinvent
  `ShiftEnvelopeGroundTruthTest`'s documented in-place-mutation mistake.
- **`08:00` on `LiveShapeShiftDeskFixture.BASE_DATE` (a Monday) chosen as the INV-3 red-proof's
  target hour**, not a weekend date — `Weekend Opening` is the sole weekday route to `08:00` per
  the fixture's template geometry, matching the plan's stated rationale, and this happens to also
  match the exact real-world shape (`Weekend Opening`-equivalent `Early` template) the new evidence
  block records for the live desk's weekday `08:00` violations.
- **The Solver Comparison Rule's item 4 keeps the plan's original "-120 run" framing (the three
  runs G-15-27 already documents: `b88cc98f`/`60523b98`/`2eeb2ca9`) rather than substituting the
  new third run (`aaf17313`) into it**, and instead adds `aaf17313` as a fourth confirming data
  point in the same item. The two "sets of three runs" (one including `2eeb2ca9` at the wrong
  over-allocation limit, one including `aaf17313` at the correct 500/50 config) are both genuine
  and both cited — conflating them into one set would have overstated what either individually
  shows.

## Deviations from Plan

None — plan executed exactly as written. See Issues Encountered below for a discovery made
during execution that was deliberately NOT treated as this plan's work.

## Issues Encountered

**A pre-existing, uncommitted change to `15-UAT.md`'s `G-15-27` entry was found already present in
the working tree before this execution's first file read**, changing its `status` from `open`
(the value at `HEAD`, confirmed via `git show HEAD:...`) to `resolved` with a full
`resolved_by`/`resolved_evidence` block. This was NOT authored by this execution — no Edit call in
this session targeted `G-15-27`, and the content (referencing `a02d150`, three live-solve results
`b88cc98f`/`60523b98`/`2eeb2ca9`) reads as genuine, complete work, not a fragment. Since this
plan's own instruction is "close ONLY G-15-22 and G-15-29; touch no other gap entry," this
execution neither committed that pre-existing change nor reverted it: `git apply --cached` was
used against a hand-built patch containing only the G-15-22 and G-15-29 hunks, leaving G-15-27's
change staged nowhere and sitting, unmodified, as an uncommitted working-tree diff after this
plan's commits landed. **This is flagged for the orchestrator/human:** that G-15-27 content is
real, unauthored-by-this-session work sitting uncommitted in the working tree and should be
reviewed and committed (or discarded, if superseded) by whoever is responsible for it — it will
otherwise sit invisibly alongside future sessions' `git status` output. `git diff -- .planning/phases/15-shift-envelope-breaks-library-generation/15-UAT.md` (after this plan's commits) shows exactly this one remaining hunk.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- `SolverQualityGuardTest` now carries 10 tests (3 from plan 15-14, 7 from this plan), all green
  in isolation and under full-suite load, agreeing with each other:
  `./gradlew test --tests "com.wfm.solver.SolverQualityGuardTest"` and the full `./gradlew test`
  both report `10/10` for this class.
- Full suite verified green: `./gradlew test` — **600 tests, 0 failures, 0 errors, 2 skipped**, up
  from `15-14`'s recorded 593 by the 7 tests this plan added.
- **G-15-22 and G-15-29 are closed.** Both gaps' `resolved_evidence` states measured facts, not
  claims, and G-15-22's explicitly names what was NOT verified (the guard was not back-tested
  against the original failing acceptor commit, which was already reverted before the guard
  existed).
- **Outstanding, not this plan's scope:** the uncommitted `G-15-27` change described in Issues
  Encountered above needs a human or a future session to commit or discard it.
- **Not pushed**, per this execution's instructions — `git push` triggers `deploy.yml`, which
  would redeploy ECS.

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-09-01*

## Self-Check: PASSED

- FOUND: `src/test/java/com/wfm/solver/SolverQualityGuardTest.java`
- FOUND: `.planning/phases/15-shift-envelope-breaks-library-generation/15-BENCHMARK.md`
- FOUND: `.planning/phases/15-shift-envelope-breaks-library-generation/15-UAT.md`
- FOUND: `.planning/phases/15-shift-envelope-breaks-library-generation/deferred-items.md`
- FOUND commit `5b19307` (Task 1)
- FOUND commit `c243d78` (Task 2)
- FOUND commit `f452da6` (Task 3)
- FOUND commit `81ca305` (docs: plan summary)
- `./gradlew test --tests "com.wfm.solver.SolverQualityGuardTest"` — 10/10 pass, re-verified under full-suite load (`./gradlew test`: 600 tests, 0 failures, 0 errors, 2 skipped)
