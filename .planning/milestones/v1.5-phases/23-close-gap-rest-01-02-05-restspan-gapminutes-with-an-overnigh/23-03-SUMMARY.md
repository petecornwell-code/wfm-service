---
phase: 23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh
plan: 03
subsystem: testing
tags: [timefold, rest-constraint, day-window, structural-guard, junit5]

# Dependency graph
requires:
  - phase: 23-01
    provides: DayWindow.anchoredWrappedEndMinute, the corrected RestSpan.gapMinutes, and requireRestFeasibility's corrected SLOT pre-horizon branch
  - phase: 23-02
    provides: Overnight-predecessor fixture coverage across all six affected rest-gap test classes
provides:
  - "src/test/resources/rest-gap-arithmetic-guard.md -- the call-site registry and allowlist for the wrap-aware primitive"
  - "RestGapArithmeticGuardTest -- the structural tripwire that fails the build on a re-inlined composition or a drifted call-site set"
  - "The single full-suite phase gate: 215 classes, 1483 tests, 0 failures, 0 errors, 4 skipped"
affects: []

actuals:
  tokens: 6992
  tasks: 2
  commits: 2
  plan_head_before: b2aed21086d380ecb985b9eeb1c7dcbad0424915
  plan_head_after: 46de29c538e045b1208d2b8adec3828e141e7faf

tech-stack:
  added: []
  patterns:
    - "Registry-plus-scanner structural guard, hybridized from two existing precedents: rest-waiver-predicate-guard.md's table/allowlist parsing mechanism plus midnight-time-arithmetic.md's comment-stripping line-level composition scan"
    - "A forbidden-token string built by concatenation (\"anchored\" + \"End\" + \"Minute(\") inside the guard's own matcher, so the guard's source file is never itself a textual match for the shape it forbids"

key-files:
  created:
    - src/test/resources/rest-gap-arithmetic-guard.md
    - src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java
  modified: []

key-decisions:
  - "Allowlist heading named '### anchoredWrappedEndMinute call sites' (new to this registry, not copied verbatim from the waiver precedent's '### RestWaiverLookup call sites') -- self-consistent between the registry file and the test's CALL_SITE_ALLOWLIST_HEADING constant, no behavior difference from the precedent's naming choice"
  - "The class javadoc's 'set equality only' warning paraphrases the precedent's isSubsetOf/containsAnyOf method-name mentions (e.g. 'a weaker one-directional containment check -- a subset test, an any-of test, or a bare .contains(...)') rather than quoting them literally -- see Deviations below"
  - "Two call-site allowlist derived from a live grep before writing the registry (com.wfm.model.RestSpan, com.wfm.service.SolverService), matching the plan's required set exactly"

requirements-completed: [REST-01, REST-02, REST-05, OVNT-01, OVNT-03]

coverage:
  - id: D1
    description: "The call-site registry (rest-gap-arithmetic-guard.md) exists with a parseable two-row call-site table, a single two-entry allowlist derived from live source, the measured defect numbers (1500/60) in prose, and four stated scope boundaries including PF-02"
    requirement: "REST-02"
    verification:
      - kind: other
        ref: "grep -c '^| Entry point' / '^com\\.wfm\\.model\\.RestSpan$|^com\\.wfm\\.service\\.SolverService$' / 'com\\.wfm\\.util\\.DayWindow' / '1500' / 'Known scope boundaries' against rest-gap-arithmetic-guard.md"
        status: pass
    human_judgment: false
  - id: D2
    description: "RestGapArithmeticGuardTest's composition scan returns EMPTY against live src/main/java -- independent structural confirmation that both call sites corrected in 23-01 are the only matches, and nothing else in the codebase carries the wrap-blind composition"
    requirement: "REST-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java#wrapBlindCompositionScan_returnsEmptySet"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java#wrapBlindMatcher_isLiveAgainstSyntheticStrings"
        status: pass
    human_judgment: false
  - id: D3
    description: "The call-site allowlist matches the derived set exactly (set equality, not subset), and the guard can provably go red via two tests-of-the-test"
    requirement: "REST-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java#callSiteSet_matchesTheRegistryAllowlistExactly"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java#deliberatelyBrokenAllowlist_isDetectedAsAMismatch"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java#allowlist_parsesAsNonEmpty"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java#everyProvingTestNamedInTheTable_resolvesToAnExistingClass"
        status: pass
    human_judgment: false
  - id: D4
    description: "The single full-suite gate is green: 215 test classes (+1 from baseline), 1483 tests (+6, exactly the new guard's test count), 0 failures, 0 errors, 4 skipped (baseline unchanged) -- MidnightTimeArithmeticGuardTest passes with its allowlist byte-identical, and Phase 21's overnight-template and day-off suites pass unchanged (OVNT-01, OVNT-03 regression safety)"
    requirement: "OVNT-01"
    verification:
      - kind: unit
        ref: "./gradlew test --tests com.wfm.service.RestGapArithmeticGuardTest (6/6)"
        status: pass
      - kind: unit
        ref: "./gradlew test --tests com.wfm.service.MidnightTimeArithmeticGuardTest (12/12)"
        status: pass
      - kind: other
        ref: "git diff --exit-code -- src/test/resources/midnight-time-arithmetic.md (silent)"
        status: pass
      - kind: other
        ref: "./gradlew --stop && ./gradlew test -- full suite aggregated across build/test-results/test/TEST-*.xml"
        status: pass
    human_judgment: false
  - id: D5
    description: "An operator confirms on a real desk that the illegal overnight-predecessor roster no longer scores 0hard, closing audit gap G-1 and flow F-1 with evidence rather than inference"
    requirement: "REST-05"
    verification: []
    human_judgment: true
    rationale: "Needs a real solve against a desk with an overnight shift template, which no unit fixture substitutes for, and a full solve competes with the application for its two cores. workflow.human_verify_mode is unset (project default end-of-phase) -- this plan deliberately carries no checkpoint:human-verify task; the operator steps are recorded below for the verifier to harvest into 23-UAT.md at end of phase."

duration: 30min
completed: 2026-10-04
status: complete
---

# Phase 23 Plan 3: Rest-gap arithmetic structural guard and phase gate Summary

**A registry-plus-scanner structural guard (`RestGapArithmeticGuardTest`) that fails the build if the overnight-predecessor rest-gap composition defect is ever re-inlined a third time, proven able to go red, plus the phase's single full-suite gate at 215 classes / 1483 tests / 0 failures.**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-10-04T20:31:19Z (session start, per STATE.md)
- **Completed:** 2026-10-04T21:00:34Z
- **Tasks:** 2
- **Files modified:** 2 (both created)

## Accomplishments
- `src/test/resources/rest-gap-arithmetic-guard.md` — the call-site registry documenting the measured defect (1500-minute overstatement collapsing to the true 60), its second independent occurrence in `requireRestFeasibility`'s SLOT pre-horizon branch under the "the two must never drift" comment, the correct-primitive/wrong-composition distinction, a two-row call-site table, a two-entry allowlist derived from live source, and four scope boundaries including PF-02
- `RestGapArithmeticGuardTest` — the scanner: one comment-stripped, self-reference-safe composition matcher (`isWrapBlindPredecessorEndComposition`), a set-equality call-site assertion, table-integrity checks, and two tests-of-the-test, all proven green
- The composition scan returns the EMPTY set against live `src/main/java` — independent structural confirmation that 23-01's two fixed call sites are the only ones, and nothing else in the codebase carries the wrap-blind idiom
- The call-site derivation (classes calling `DayWindow.anchoredWrappedEndMinute`, excluding `DayWindow` itself) equals exactly `{com.wfm.model.RestSpan, com.wfm.service.SolverService}` — the registry's allowlist, by `containsExactlyInAnyOrderElementsOf`
- Single full-suite gate run: `./gradlew --stop` then `./gradlew test` — 215 test classes (+1 over the 23-02 baseline of 214), 1483 tests (+6, exactly the new guard's test count), 0 failures, 0 errors, 4 skipped (unchanged), build completed in 11m32s
- `MidnightTimeArithmeticGuardTest` passes (12/12) with `midnight-time-arithmetic.md` byte-identical — the new primitive introduced no raw time arithmetic and no raw time comparison, so its allowlists needed no edit

## Task Commits

Each task was committed atomically:

1. **Task 1: The call-site registry — src/test/resources/rest-gap-arithmetic-guard.md** - `dd32748` (test)
2. **Task 2: The scanner — RestGapArithmeticGuardTest, plus the single full-suite phase gate** - `46de29c` (test)

**Plan metadata:** (pending — docs commit below)

## Files Created/Modified
- `src/test/resources/rest-gap-arithmetic-guard.md` - the call-site registry: title/parse-warning, "Why this guard exists" (1500/60, second occurrence, correct-primitive/wrong-composition), the two-row call-site table, the two-entry Guard Allowlists fence, and four "Known scope boundaries" bullets
- `src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java` - the scanner: registry/table/allowlist parsing copied from `RestWaiverPredicateGuardTest`, the comment-stripping scan copied from `MidnightTimeArithmeticGuardTest`, one composition matcher, and six tests (composition-scan-empty, call-site-set-exact, allowlist-nonempty, table-integrity, matcher-liveness, deliberately-broken-allowlist)

## Decisions Made
- `CALL_SITE_ALLOWLIST_HEADING` named `### anchoredWrappedEndMinute call sites` — new wording specific to this registry (not a verbatim copy of the waiver precedent's heading text), kept identical between the registry file and the test's constant so parsing is unambiguous
- The end-accessor token the matcher looks for (`anchoredEndMinute(`) is built via string concatenation (`"anchored" + "End" + "Minute("`) rather than spelled out literally, per the plan's explicit instruction, so this test file itself is never a textual match for the forbidden shape it scans for
- The class javadoc's "set equality only" warning paraphrases rather than quotes the precedents' literal `isSubsetOf`/`containsAnyOf` method names — see Deviations below for why

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Paraphrased the class javadoc's weakened-assertion warning instead of quoting it verbatim**
- **Found during:** Task 2 (writing `RestGapArithmeticGuardTest`'s class javadoc)
- **Issue:** The plan's `<read_first>` instructs reproducing `RestWaiverPredicateGuardTest`'s "set equality only, never subset or containment" warning, which in that precedent spells out `{@code isSubsetOf}` and `{@code containsAnyOf}` literally. Doing so verbatim here would make Task 2's own automated verify command — `grep -c 'isSubsetOf\|containsAnyOf' ... <fails_when>prints anything other than 0` — fail against this very file, since that grep has no comment/javadoc awareness and matches any textual occurrence of those two substrings anywhere in the file.
- **Fix:** Rewrote the warning paragraph to convey the identical meaning without using the literal method-name substrings — "a weaker one-directional containment check — a subset test, an any-of test, or a bare `.contains(...)`" — preserving the warning's content and its reference to both precedent classes' own javadoc, while satisfying the literal grep.
- **Files modified:** `src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java`
- **Verification:** `grep -c 'isSubsetOf\|containsAnyOf' src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java` prints `0`
- **Committed in:** `46de29c` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking).
**Impact on plan:** None on correctness or guard behavior — a documentation-only rewording. All other acceptance criteria and verify commands passed exactly as specified with no retries.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Human-Check — deferred to end-of-phase harvest

`23-VALIDATION.md`'s Manual-Only table names one operator verification this phase cannot automate. `workflow.human_verify_mode` is unset, so the project default `end-of-phase` applies: this plan deliberately carries no `checkpoint:human-verify` task and stays `autonomous: true`. The verifier harvests the steps below into `23-UAT.md` at end of phase.

**What was built.** The hard minimum-rest constraint now measures the true gap when the PREDECESSOR shift spans midnight. `DayWindow.anchoredWrappedEndMinute` is the one wrap-aware end-offset primitive; `RestSpan.gapMinutes` and `SolverService.requireRestFeasibility`'s SLOT pre-horizon branch both call it; six test classes carry overnight-predecessor fixtures; and `RestGapArithmeticGuardTest` now fails the build if the composition is ever re-inlined. Measured in the unit fixtures: prev 22:00-06:00 into a 07:00 successor reports 60 minutes of rest, where it previously reported 1500.

**Why this cannot be automated.** It needs a real solve against a desk with an overnight shift template, which no unit fixture substitutes for, and a full solve competes with the application for its two cores.

**Operator steps.**
1. On a desk that has an overnight shift template (end time earlier in the clock than its start time) and a configured minimum rest of 660 minutes, construct a roster where an agent works the overnight template on one business date and starts around 07:00 the next.
2. Start a solve.
3. Read the result through the schedule `/summary` endpoint. Do NOT poll `GET /schedules/{id}` — that payload is roughly 4 MB and competes with the solver for its two cores.
4. Confirm the hard score is NO LONGER `0hard` and that the `Minimum rest (shift)` constraint is among those that fired.

**Expected outcome.** A non-zero hard penalty on the rest constraint. Before this phase the identical roster scored `0hard`, which is audit gap G-1 and broken flow F-1. If the desk is SLOT-mode instead, the equivalent check is that a pre-solve refusal now names a smaller best-achievable gap for an agent-day whose accepted predecessor wrapped, where previously the solve started without complaint.

**If it is still `0hard`:** record it as an open gap with the desk id, both shift times, the configured minimum and the reported score. It must never be closed as passed on the strength of the unit fixtures alone.

## Next Phase Readiness
- This is the phase's final plan. All three plans (23-01, 23-02, 23-03) are complete: the fix (23-01), its fixture coverage across all six affected test classes (23-02), and the structural tripwire plus phase gate (23-03).
- D-02 is delivered: both guard files exist, the guard runs in the ordinary `./gradlew test` suite, and it fails the build on a re-inlined composition or a drifted call-site set.
- The full suite is green under one run: 215 classes, 1483 tests, 0 failures, 0 errors, 4 skipped — `MidnightTimeArithmeticGuardTest` passing with its allowlists unmodified.
- One blocker to close before the phase can be marked fully verified: the operator confirmation above (D5) is outstanding and is this phase's only remaining open item, by design deferred to end-of-phase UAT harvest rather than a mid-flight checkpoint.
- Phase 23 is ready for `/gsd-verify-work 23` and, pending the operator confirmation, closes REST-01/REST-02/REST-05 (audit gap G-1) for good.

## Self-Check: PASSED

Both created files confirmed present on disk (`test -f` on each). Both task commits (`dd32748`, `46de29c`) confirmed in `git log --oneline`. All plan-level `<acceptance_criteria>` and `<verify>` commands re-run fresh and passing: registry structural greps (header=1, allowlist=2, DayWindow-excluded=1, 1500=1, scope-boundaries=1); guard-class structural greps (6 test methods, containsExactlyInAnyOrderElementsOf present, isSubsetOf/containsAnyOf=0); `RestGapArithmeticGuardTest` 6/6; `MidnightTimeArithmeticGuardTest` 12/12; `midnight-time-arithmetic.md` byte-identical; `./gradlew --stop` succeeded; full `./gradlew test` 215 classes / 1483 tests / 0 failures / 0 errors / 4 skipped. `git diff --exit-code -- src/main/java` silent, confirming this plan modified no production source.

---
*Phase: 23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh*
*Completed: 2026-10-04*
