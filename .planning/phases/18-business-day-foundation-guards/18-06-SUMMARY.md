---
phase: 18-business-day-foundation-guards
plan: 06
subsystem: testing
tags: [structural-guard, midnight-boundary, receiver-heuristic, set-equality, red-proof, parsed-allowlist]

# Dependency graph
requires:
  - phase: 18-01
    provides: "desk.day_start / timeslot.business_date columns, DayWindow anchored functions"
  - phase: 18-02
    provides: "DeskService.setDayStart gate, no confirmOverride surface"
  - phase: 18-03
    provides: "TimeslotGeneratorService's re-authored anchored generation walk -- the settled tree this plan's comparison allowlist reconciles against"
provides:
  - "MidnightTimeArithmeticGuardTest's second token family (.isAfter/.isBefore/.compareTo), gated by a documented name-based receiver heuristic, asserted set-equal against a second allowlist section in the same resource -- one guard, one resource, one shared filesystem walk"
  - "A parameterised scan root (scanProductionSources/toFullyQualifiedName both take Path root explicitly) with SOURCE_ROOT surviving as the one named production-root constant every real assertion passes"
  - "A pipeline-level red-proof (src/test/resources/midnight-guard-offender/OffendingSample.java + pipelineRedProof_walkStripMatchAndSetCompareAreAllLive) proving walk, comment-strip, match and set-compare are all live together, not only the matcher predicate"
affects: [19-daywindow-re-anchoring, 20-solver-business-date-correctness]

# Actuals (#2632) -- pairs with the plan's estimate to calibrate future estimates.
actuals:
  tokens: 6959
  tasks: 2
  commits: 2
  plan_head_before: 19267fa0d65d5034f2369c1608fec91d77f76be2

tech-stack:
  added: []
  patterns:
    - "Salvaged-commit landing with settled-tree reconciliation (P-30): 63d85a6's comparison extension landed close to verbatim, but one of its ten salvaged allowlist entries (TimeslotGeneratorService) was genuinely STALE against the tree as 18-03 left it -- its raw LocalTime comparison had already been replaced by anchored integer-offset arithmetic through DayWindow. Removed the entry and its justification rather than forcing a synthetic fix; nine entries remain."
    - "Receiver-name heuristic, receiver-only, one trailing empty-arg-list tolerance, three limitations documented in the parsed resource itself (name-based not type-aware; receiver-only so an argument-only occurrence escapes; a two-level accessor chain resolves to the nearest name) -- not only in the test, because the heuristic is the part most likely to rot"
    - "Parameterised scan root (Path root passed explicitly to scanProductionSources/toFullyQualifiedName) with the production constant (SOURCE_ROOT) kept as the one thing every real assertion passes by name, so a parameterisation mistake fails loudly via theScanActuallySeesProductionSource rather than passing vacuously"
    - "Pipeline-level red-proof: a tracked, never-compiled synthetic offender under src/test/resources (never src/main/java) with exactly two interesting lines (one real match, one commented occurrence of the same token), pointed at by the same shared walk used in production, proving walk+strip+match+compare together rather than only the matcher predicate against synthetic strings"

key-files:
  created:
    - src/test/resources/midnight-guard-offender/OffendingSample.java
  modified:
    - src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java
    - src/test/resources/midnight-time-arithmetic.md

key-decisions:
  - "Landed 63d85a6's comparison-operator extension (second token family, receiver heuristic, second set-equality assertion) with D-24's rewrite pass applied to its two decision-ID citations ('(D-12)' and 'D-14... in Phase 21') and the resource's one 'Phase 21' citation -- all rewritten to state the reason inline and cite SOLV-02 with no phase number or decision-document identifier anywhere."
  - "Reconciled the salvaged ten-entry comparison allowlist against the settled tree per P-30: the TimeslotGeneratorService entry ('if (slotStart.isBefore(startTime) || !DayWindow.startsBefore(slotStart, endTime)) return false;') no longer matches -- 18-03's generator re-authoring replaced it with anchored integer-offset comparisons (slotStartOffset/windowStartOffset/windowEndOffset), which route through DayWindow already and are correctly invisible to this guard. Removed the entry and its justification paragraph; nine entries remain, all still correct against the live tree (confirmed by a green run, not by inspection alone)."
  - "Followed P-28 literally over the task action text's conditional 'add a raw-arithmetic pipeline proof too, if the same fixture can serve both families': P-28 pins the fixture's shape to exactly two interesting lines and the pipeline result to exactly one entry. A single fixture line cannot trigger both COMPARISON_TOKENS and RAW_ARITHMETIC_TOKENS without becoming two lines' worth of complexity, which would contradict P-28's explicit two-line, one-entry contract. Resolved in favor of the more specific, numbered planner decision -- the same resolution style 18-05's SUMMARY used for its own plan-text count inconsistency. The comparison pipeline is the one D-25 names as the uncovered failure mode (the arithmetic family's own matcher-level proof, theScanDetectsAFreshOccurrence, is unchanged and untouched by this plan)."
  - "Named the new fixture-root constant COMPARISON_OFFENDER_ROOT and the shared-walk/name-derivation parameters literally 'root' (not 'scanRoot') to keep scanProductionSources and toFullyQualifiedName's signatures symmetrical and both greppable as 'Path root'."

requirements-completed: [BDAY-05]

coverage:
  - id: D1
    description: "MidnightTimeArithmeticGuardTest enforces a second token family (.isAfter/.isBefore/.compareTo) gated by a documented name-based receiver heuristic, asserted set-equal in both directions against a second allowlist section in the same resource -- one guard, one resource, one shared filesystem walk"
    requirement: "BDAY-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java (9 tests total: both set-equality assertions, both matcher-level red-proofs, non-vacuity check covering both allowlists, scan-sees-production-source check, missing-heading-throws check covering both headings generically and by name, plus the pipeline-level red-proof)"
        status: pass
      - kind: other
        ref: "./gradlew test --tests \"com.wfm.service.MidnightTimeArithmeticGuardTest\" -> BUILD SUCCESSFUL; build/test-results/test/TEST-com.wfm.service.MidnightTimeArithmeticGuardTest.xml -> tests=9 skipped=0 failures=0 errors=0"
        status: pass
    human_judgment: false
  - id: D2
    description: "The receiver heuristic's pattern list and its three named limitations (name-based not type-aware, receiver-only, one-trailing-empty-arg-list tolerance) are documented in the parsed resource, not only in the test"
    requirement: "BDAY-05"
    verification:
      - kind: other
        ref: "grep -ciE 'limitation|name-based|not type-aware' src/test/resources/midnight-time-arithmetic.md -> 4 (>= 2 required)"
        status: pass
    human_judgment: false
  - id: D3
    description: "The guard is proven able to go red through its whole pipeline (walk, comment-strip, match, set-compare), not only through its matcher predicate -- closing the exact gap where weakening the final assertion to a subset check would leave every prior red-proof passing"
    requirement: "BDAY-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java#pipelineRedProof_walkStripMatchAndSetCompareAreAllLive -- asserts the fixture directory holds exactly one .java file, that the shared walk finds exactly one entry there, and that set-equality against an empty expected set throws AssertionError"
        status: pass
      - kind: other
        ref: "./gradlew compileTestJava 2>&1 | grep -c OffendingSample -> 0 (the synthetic offender is a resource, never compiled)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Every comment touched or added in this plan cites requirement IDs only -- no D-nn decision-document identifiers, no bare phase numbers -- across both the arithmetic and comparison sections"
    requirement: "BDAY-05"
    verification:
      - kind: other
        ref: "grep -cE '\\bD-[0-9]+\\b' / 'Phase [0-9]+' on both touched files -> 0/0; whole-phase re-check across all 39 files touched since PHASE_START -> 0/0"
        status: pass
    human_judgment: false

duration: ~40min
completed: 2026-09-30
status: complete
---

# Phase 18 Plan 6: Midnight Comparison Guard + Pipeline-Level Red-Proof Summary

**Extended `MidnightTimeArithmeticGuardTest` to `.isAfter`/`.isBefore`/`.compareTo` via a documented receiver-name heuristic, reconciled the salvaged ten-entry comparison allowlist down to nine against the settled Phase 18 tree, and added a pipeline-level red-proof (synthetic offender under `src/test/resources`) proving the guard can go red through walk, comment-strip, match and set-compare together, not only through its matcher.**

## Performance

- **Duration:** ~40 min
- **Started:** 2026-09-30T13:30:00-04:00 (approx.)
- **Completed:** 2026-09-30T14:15:00-04:00 (approx.)
- **Tasks:** 2 (both `type="auto"`)
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments

- `MidnightTimeArithmeticGuardTest` now enforces a second token family (`.isAfter(`, `.isBefore(`, `.compareTo(`) gated by `isRawComparison`'s receiver-name heuristic (`looksLikeSchedulingTime`), with its own `rawTimeComparisonsInProductionCode_matchesTheComparisonAllowlistExactly` set-equality assertion sharing the same filesystem walk (`scanProductionSources`) as the pre-existing raw-arithmetic assertion — one guard, one resource, one walk.
- **The salvaged ten-entry comparison allowlist was reconciled against the settled tree per P-30, not landed blind.** Running the test immediately surfaced one STALE entry: `TimeslotGeneratorService`'s old `if (slotStart.isBefore(startTime) || !DayWindow.startsBefore(slotStart, endTime)) return false;` no longer exists — Phase 18 Plan 3's generator re-authoring replaced it with anchored integer-offset comparisons (`slotStartOffset < windowStartOffset`, etc.) that already route through `DayWindow`. Removed the entry and its justification; nine entries remain, all confirmed live against the current tree by a green test run.
- `midnight-time-arithmetic.md` gained a `### Permitted raw time comparisons` section (nine entries) plus explicit documentation of the receiver heuristic's three named limitations — name-based rather than type-aware, receiver-only (an argument-only occurrence escapes), and a one-trailing-empty-arg-list tolerance that resolves a two-level accessor chain to the nearest name — written where a future allowlist editor will actually read them, not only in the test's javadoc.
- `scanProductionSources` and `toFullyQualifiedName` were parameterised to take the scan root explicitly (`Path root`), with `SOURCE_ROOT` surviving as the one named production-root constant every real assertion passes by name. `theScanActuallySeesProductionSource` keeps asserting against `SOURCE_ROOT` specifically, so a parameterisation mistake that pointed a real assertion at a fixture directory would fail loudly rather than pass vacuously.
- A tracked, never-compiled synthetic offender — `src/test/resources/midnight-guard-offender/OffendingSample.java` — was added with exactly two interesting lines (one real `slotStart.isBefore(cutoff)` match, one commented-out occurrence of the same token). A new `pipelineRedProof_walkStripMatchAndSetCompareAreAllLive` test points the same shared walk at this fixture and proves, in sequence: the fixture holds exactly one `.java` file; the walk + comment-strip + match yields exactly one entry (proving comment-stripping, not just matching); and asserting set-equality between that entry and an empty expected set throws `AssertionError` — the step the existing matcher-level red-proofs (`theScanDetectsAFreshOccurrence`, `theComparisonScanDetectsAFreshOccurrence`) cannot reach, since both test the predicate against synthetic strings only.
- D-24's rewrite pass applied throughout: the salvaged commit's two decision-ID citations (`(D-12)` and `D-14... SOLV-02 in Phase 21`) and the resource's one `Phase 21` citation were all rewritten to state the reason inline and cite `SOLV-02` with no phase number or decision-document identifier anywhere. A whole-phase re-check across all 39 files touched since Phase 18 started confirms zero `D-nn` and zero bare `Phase N` citations phase-wide.
- Full suite green: **1108 tests** (up from 1105 before this plan), 0 failures, 0 errors, 4 pre-existing skips (unchanged benchmark classes). `npm --prefix frontend run build` succeeds. `git diff --name-only HEAD -- src/main` is empty for both tasks — extending a guard changed no production code.

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend the midnight guard to comparison operators with a documented receiver heuristic** — `7edb4be` (test)
2. **Task 2: Prove the guard red through its whole pipeline, not only its matcher** — `6a7f4d1` (test)

**Plan metadata:** commit pending (this SUMMARY + STATE.md/ROADMAP.md/REQUIREMENTS.md update)

## Files Created/Modified

- `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` (modified) — comparison token family, receiver heuristic, second set-equality assertion, matcher-level and pipeline-level red-proofs, parameterised scan root
- `src/test/resources/midnight-time-arithmetic.md` (modified) — `### Permitted raw time comparisons` section (nine entries), receiver-heuristic documentation with its three limitations
- `src/test/resources/midnight-guard-offender/OffendingSample.java` (new) — the tracked, never-compiled synthetic offender for the pipeline-level red-proof

## Final Comparison Allowlist Contents (as required by this plan's `<output>`)

Nine entries (down from the salvaged commit's ten — one reconciled away, see Decisions):

```
com.wfm.service.FteUploadService :: if (startTime == null || slotStart.isBefore(startTime)) startTime = slotStart;
com.wfm.service.ScheduleExportService :: if (earliest == null || ad.startTime().isBefore(earliest)) earliest = ad.startTime();
com.wfm.service.ScheduleOutputService :: startOk = actStart != null && !actStart.isBefore(prefStart);
com.wfm.service.ScheduleOutputService :: int sign = actualStartTime.isAfter(usualStartTime) ? 1
com.wfm.service.ScheduleOutputService :: : actualStartTime.isBefore(usualStartTime) ? -1 : 0;
com.wfm.service.ShiftLibraryGenerationService :: if (start.isBefore(earliestStart) || DayWindow.endMinute(end) > DayWindow.endMinute(latestEnd)) {
com.wfm.service.ShiftLibraryGenerationService :: int startCompare = candidate.spanStart().compareTo(currentBest.spanStart());
com.wfm.solver.AgentAssignmentDifficultyComparator :: int timeCompare = a.getTimeslot().getStartTime().compareTo(b.getTimeslot().getStartTime());
com.wfm.solver.ScheduleConstraintProvider :: return a.getTimeslot().getStartTime().isBefore(p.getPreferredStartTime());
```

**Reconciliation against the salvaged tree (P-30):** the salvaged commit's `com.wfm.service.TimeslotGeneratorService :: if (slotStart.isBefore(startTime) || !DayWindow.startsBefore(slotStart, endTime)) return false;` entry was STALE — genuinely absent from the settled tree, not a scanning artifact. Phase 18 Plan 3 re-authored `TimeslotGeneratorService.isDesired` to derive an anchored business date and compare integer minute-offsets (`slotStartOffset`, `windowStartOffset`, `windowEndOffset`) rather than raw `LocalTime` values, so the line this entry described no longer exists in any form — there is nothing to route through `DayWindow` because the replacement already routes through it. The entry and its justification paragraph were removed rather than replaced.

## Observed `AssertionError` Message (as required by this plan's `<output>`)

Captured by temporarily instrumenting the pipeline-level red-proof to print the caught exception's message, running the class in isolation, then reverting the instrumentation via `git checkout --` (confirmed zero-diff against the committed state afterward — the committed test still only asserts `isInstanceOf(AssertionError.class)`):

```
Expecting actual:
  ["OffendingSample :: if (slotStart.isBefore(cutoff)) {"]
to contain exactly in any order:
  []
but the following elements were unexpected:
  ["OffendingSample :: if (slotStart.isBefore(cutoff)) {"]
```

This is AssertJ's standard `containsExactlyInAnyOrderElementsOf` failure shape, confirming the set-compare step itself — not merely the matcher predicate — is what throws when the pipeline is pointed at the fixture with a (deliberately, for the red-proof) empty expected set.

## Decisions Made

See `key-decisions` in frontmatter — reconciliation of the salvaged comparison allowlist against the settled tree (P-30), the D-24 rewrite pass, the P-28-literal resolution of the task action text's raw-arithmetic-pipeline-proof conditional, and the `Path root` naming choice for the parameterised scan.

## Deviations from Plan

### Plan-text resolution (not a code deviation, documented for the verifier)

**1. Task 2's `<action>` text offered a conditional ("if the fixture's offending line can serve both families... otherwise add a second offending line... so one fixture proves both pipelines") that is not reconcilable with P-28's literal, numbered fixture-shape contract**
- **Found during:** Task 2 planning, before writing the fixture
- **Issue:** P-28 (18-06-PLAN.md's own planner decision, and `18-PATTERNS.md`'s corresponding pattern assignment) pins the synthetic offender to "exactly two interesting lines: one real offending comparison... and one *commented* line naming the same token," with the pipeline required to "yield exactly one entry." Task 2's `<action>` prose separately suggests extending the same fixture to also prove the raw-arithmetic pipeline, "adjusting the expected entry count accordingly." A single line cannot trigger both `COMPARISON_TOKENS` and `RAW_ARITHMETIC_TOKENS` without becoming a second line, which would make the fixture's entry count depend on which matcher pointed at it — directly contradicting P-28's "exactly one entry" contract for the comparison pipeline.
- **Resolution:** Followed P-28 literally (the more specific, numbered decision) over the task action text's looser conditional — the same resolution style 18-05's SUMMARY used for its own internally-inconsistent test-count wording. The fixture holds exactly the two lines P-28 specifies and proves only the comparison pipeline, which is also the pipeline D-25 (18-CONTEXT.md) names as the uncovered failure mode; the raw-arithmetic family's own matcher-level red-proof (`theScanDetectsAFreshOccurrence`) is untouched by this plan and remains unchanged.
- **Files modified:** none beyond what Task 2 already specified (no extra file, no extra line)
- **Verification:** `find src/test/resources/midnight-guard-offender -name '*.java' | wc -l` prints `1`; the pipeline test asserts `hasSize(1)` twice (fixture file count, then derived-entry count), both satisfied; full suite green
- **Committed in:** `6a7f4d1` (Task 2 commit)

---

**Total deviations:** 0 code deviations (0 auto-fixed). 1 plan-text resolution (conditional action-text prose resolved in favor of the more specific, numbered P-28 decision).
**Impact on plan:** None on correctness or scope. The guard enforces both token families exactly, the allowlist matches the settled tree, the heuristic and its limitations are documented, and the guard is proven able to go red through its whole pipeline.

## Issues Encountered

**Gradle test-results XML write collision (contention-flaky suite, as recorded in this phase's context).** Running `./gradlew test` (full suite) and `./gradlew test --tests "com.wfm.service.MidnightTimeArithmeticGuardTest"` concurrently in two separate shell invocations produced a spurious `BUILD FAILED` from `Could not write XML test results for <ClassName> to file ...` errors across ~20 unrelated test classes — not a real test failure, the exact collision this phase's context warned about (plan 18-04 hit the same thing). Resolved by confirming no gradle processes were still running, then re-running the full suite alone: `BUILD SUCCESSFUL in 10m 59s`, 1108 tests, 0 failures, 0 errors, 4 pre-existing skips.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **Phase 18 (Business-Day Foundation & Guards) is now complete — all 6 plans executed and summarized.** This was the final plan (BDAY-05).
- The midnight-boundary guard now enforces both raw arithmetic and raw comparisons exactly, in both directions, over the settled Phase 18 tree, with its receiver heuristic and limitations documented and its pipeline proven able to go red end-to-end.
- The comparison allowlist's nine entries are a function of the finished tree — any future plan (Phase 19's `DayWindow` re-anchoring, Phase 20's solver business-date joins) that introduces a new raw scheduling-time comparison will fail this guard's set-equality assertion immediately, naming the new line and directing the author to route it through `DayWindow` unless both endpoints are genuinely start times.
- Date tokens (`getDate`, `getDayOfWeek`, `plusDays`, `ChronoUnit.DAYS`) remain explicitly out of this guard's scope, reserved for `SOLV-02`'s calendar-vs-business-date join guard in a later phase — confirmed by a zero-count grep against both touched files.
- Full suite green at 1108 tests (up from 1105 before this plan), 0 failures, 0 errors, 4 pre-existing benchmark skips (unrelated, unchanged). Frontend build unaffected.

---
*Phase: 18-business-day-foundation-guards*
*Completed: 2026-09-30*

## Self-Check: PASSED

- `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` — FOUND (modified)
- `src/test/resources/midnight-time-arithmetic.md` — FOUND (modified)
- `src/test/resources/midnight-guard-offender/OffendingSample.java` — FOUND (new, tracked: confirmed via `git ls-files`)
- Commit `7edb4be` — FOUND in `git log --oneline --all`
- Commit `6a7f4d1` — FOUND in `git log --oneline --all`
- Task 1's 9 literal `<acceptance_criteria>` re-verified: comparisons heading count `1`; arithmetic heading count `1`; `containsExactlyInAnyOrderElementsOf` count `3` (>= 2); weakened-assertion grep `0`; `com.wfm.util.DayWindow` grep `4` (>= 1); allowlist entry count `9` (in range 1-15); limitations-doc grep `4` (>= 2); `D-nn`/`Phase N` grep `0`/`0` on both files; date-token grep `0`; `git diff --name-only HEAD -- src/main` empty
- Task 2's 13 literal `<acceptance_criteria>` re-verified: fixture tracked via `git ls-files`; fixture `.java` count `1`; offender absent from compiled source sets (`git ls-files` glob check `0`); `SOURCE_ROOT` constant count `1`; `Path root`/`Path scanRoot` grep `2`; `AssertionError` grep `2` (>= 1); `hasSize(1)` grep `2` (>= 1); `@Test` count `9` (> 8, the count after Task 1); pipeline behaviour confirmed (exactly one entry, `AssertionError` thrown — message captured above); production assertions still scan `src/main/java` by name; full suite green (`BUILD SUCCESSFUL`, 1108/1108 effective non-skip tests passing); `compileTestJava` emits zero diagnostics naming `OffendingSample`; `git diff --name-only HEAD -- src/main` empty; decision-ID diff grep `0` (after the one-line fix described in Deviations)
- Plan-level `<verification>` re-run: (1) `./gradlew test` green — `BUILD SUCCESSFUL in 10m 59s`, 1108 tests/0 failures/0 errors/4 skips; (2) `npm --prefix frontend run build` succeeds; (3) zero `D-nn`/`Phase N` citations across all 39 files touched since Phase 18 started (re-checked phase-wide, not just this plan); (4) `grep -rl 'confirmOverride' src/main src/test frontend/src` count `0`; (5) `MidnightTimeslotPostgresTest`'s JUnit XML shows `tests="6" skipped="0"`; (6) `git diff --name-only HEAD -- src/main` empty for both tasks
