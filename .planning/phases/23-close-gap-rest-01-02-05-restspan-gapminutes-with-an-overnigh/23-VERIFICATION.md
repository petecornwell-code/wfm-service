---
phase: 23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh
verified: 2026-10-04T00:00:00Z
status: human_needed
score: 15/15 must-haves verified
covered_files: [".planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-01-PLAN.md", ".planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-01-SUMMARY.md", ".planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-02-PLAN.md", ".planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-02-SUMMARY.md", ".planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-03-PLAN.md", ".planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-03-SUMMARY.md", ".planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-PATTERNS.md", ".planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-RESEARCH.md", ".planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-REVIEW-DISPOSITION.md", ".planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-REVIEW.md", ".planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-VALIDATION.md", "src/main/java/com/wfm/model/RestSpan.java", "src/main/java/com/wfm/service/SolverService.java", "src/main/java/com/wfm/util/DayWindow.java", "src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java", "src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java", "src/test/java/com/wfm/service/RestPredecessorServiceTest.java", "src/test/java/com/wfm/service/RestWaiverDisclosureTest.java", "src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java", "src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java", "src/test/java/com/wfm/solver/RestHorizonEdgeTest.java", "src/test/java/com/wfm/util/DayWindowTest.java", "src/test/resources/rest-gap-arithmetic-guard.md"]
covered_digest: "v2:sha256:32230948cdae7069f1c61170ef1f3846a36f2cb6a6e713b636254de3987e1017"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "D5 (23-03 plan, 23-VALIDATION.md Manual-Only table): on a real desk with an overnight shift template and a configured minimum rest of 660 minutes, construct a roster where an agent works the overnight template on one business date and starts around 07:00 the next. Start a solve."
    expected: "The hard score is no longer `0hard` and `Minimum rest (shift)` (or the SLOT-mode pre-solve refusal, naming a smaller best-achievable gap) fires. Read the result through `/summary`, never `GET /schedules/{id}` (~4 MB payload, competes with the solver for its two cores)."
    why_human: "Needs a real solve against a desk with an overnight shift template; no unit fixture substitutes for a live Timefold solve, and running one here would compete with the application for its two cores. This item was deliberately deferred by plan 23-03 (human_judgment: true) to end-of-phase UAT harvest per the project's `end-of-phase` default."
---

# Phase 23: Close gap REST-01/02/05 — RestSpan.gapMinutes with an overnight predecessor Verification Report

**Phase Goal:** `RestSpan.gapMinutes` measures the true rest gap when the PREDECESSOR shift itself
spans midnight, so the hard minimum-rest constraint fires on the illegal rosters Phase 21's overnight
templates made reachable — closing v1.5 audit gap G-1 (critical) and flow F-1.

**Verified:** 2026-10-04
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

This phase carries two separately-verifiable deliverables per the ROADMAP's explicit instruction, and
they are reported distinctly below rather than one standing in for the other.

### Deliverable 1 — The defect fix (23-01)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `DayWindow.anchoredWrappedEndMinute(LocalTime, LocalTime)` is the single wrap-aware end-offset primitive, a public INSTANCE method (never static), composed from `anchoredStartMinute` + `anchoredDurationMinutes` | VERIFIED | `DayWindow.java:170` — `return anchoredStartMinute(start) + anchoredDurationMinutes(start, end);`. Confirmed instance (not static) method. |
| 2 | An overnight predecessor (22:00-06:00) measures a true 60-minute gap into a 07:00 successor, not the pre-fix 1500 | VERIFIED | `RestSpan.gapMinutes` (`RestSpan.java:128-134`) hand-traced: `remainingInPrevDay = 1440 - anchoredWrappedEndMinute(22:00,06:00)` = `1440-1800=-360`; `elapsedIntoNextDay = anchoredStartMinute(07:00)=420`; gap=60. Re-ran `gapMinutes_overnightPredecessor_matchesTrueGap` fresh — PASS. |
| 3 | Under a 660-minute minimum that pair penalises by exactly 600 | VERIFIED | `MinimumRestShiftConstraintTest#overnightPredecessor_trueGapMeasuredCorrectly` re-run fresh — PASS (`penalizesBy(600)`) |
| 4 | Exactly ONE wrap-aware end-offset implementation exists codebase-wide; both call sites use it, no third inlined copy | VERIFIED | `grep -rn anchoredWrappedEndMinute src/main/java` → only `DayWindow.java:170` (impl), `RestSpan.java:132`, `SolverService.java:2038` (call sites). `RestGapArithmeticGuardTest#callSiteSet_matchesTheRegistryAllowlistExactly` and `#wrapBlindCompositionScan_returnsEmptySet` re-run fresh — PASS. |
| 5 | `requireRestFeasibility`'s SLOT pre-horizon branch refuses a wrapping historical predecessor (best gap 360 vs 660 minimum) where it previously computed 1800 and refused nothing | VERIFIED | `SolverService.java:2038` reads `window.anchoredWrappedEndMinute(prior.startTime(), prior.endTime())` in the `else` (no in-horizon predecessorConfig) branch; `RestFeasibilityRefusalTest#slot_preHorizonOvernightSpan_refusedOnTheTrueWrappedEnd` re-run fresh — PASS |
| 6 | Successor side of the gap formula unchanged — an overnight successor (20:00-05:00) with a non-wrapping predecessor measures the identical gap before/after | VERIFIED | `MinimumRestShiftConstraintTest#overnightSuccessorWithNonWrappingPredecessor_unchangedByTheFix` re-run fresh — PASS (`penalizesBy(0)`, 1800-minute gap unaffected) |
| 7 | `DayWindowTest.NoPublicStaticTakesABareSchedulingTime` stays byte-identical — no edit | VERIFIED | `git diff <pre-phase>..HEAD -- DayWindowTest.java \| grep NoPublicStatic` → no output (no diff touches that nested class) |
| 8 | Boundary triple: gap-equal-to-minimum not penalised (0), minimum+1 penalised by exactly 1 | VERIFIED | `overnightPredecessor_gapExactlyAtMinimum_notPenalised` and `overnightPredecessor_gapOneMinuteShortOfMinimum_penalisedByOne` re-run fresh — both PASS |
| 9 | Adjacency edge: predecessor ending exactly at the anchor (22:00-00:00) does NOT wrap — `anchoredWrappedEndMinute` returns 1440, identical to `anchoredEndMinute` | VERIFIED | `DayWindowTest$WrappedEndMinute#wrapThresholdDoesNotWrap` re-run fresh — PASS (asserts `1440` and equality to `anchoredEndMinute(MIDNIGHT)`) |
| 10 | Constraint order/count/names untouched — no constraint added/removed/reordered | VERIFIED | `git diff` shows `ScheduleConstraintClassificationTest.java` and `ConstraintMatchCountNonVacuityTest.java` have zero commits in the phase's range; both re-run fresh — PASS |
| 11 | Integer-only arithmetic; `remainingInPrevDay` may legitimately go negative, never clamped | VERIFIED | `RestSpan.gapMinutes` and `SolverService`'s `bestGap` line read; `grep Math.max\|Math.min` against both — no match near `remainingInPrevDay`/`bestGap`/`predecessorEndMinute` |
| 12 | Boundary pin one step either side of the wrap threshold: `anchoredWrappedEndMinute(22:00,00:00)=1440`, `(22:00,00:01)=1441` | VERIFIED | `DayWindowTest$WrappedEndMinute#wrapThresholdDoesNotWrap` / `#oneMinutePastWrapThreshold` re-run fresh — both PASS |
| 13 | Purity/idempotency — `anchoredWrappedEndMinute` and `gapMinutes` are pure, called exactly once per candidate pair in the constraint stream | VERIFIED | `DayWindowTest$WrappedEndMinute#purityAcrossRepeatedCalls` re-run fresh — PASS; `ScheduleConstraintProvider.java:1073,1166` each call `RestSpan.gapMinutes` once per `.map` step |

### Deliverable 2 — The coverage gap (23-02), across all six affected classes

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 14 | Every one of the six affected test classes (`MinimumRestShiftConstraintTest`, `RestFeasibilityRefusalTest`, `RestHorizonEdgeTest`, `MinimumRestSlotConstraintTest`, `RestPredecessorServiceTest`, `RestWaiverDisclosureTest`) now carries at least one overnight-predecessor fixture, including the sixth class (`RestHorizonEdgeTest`, PF-01) the original research did not enumerate | VERIFIED | Read each file directly: `RestHorizonEdgeTest#shift_preHorizonOvernightPredecessor_firstDayConstrained_oneMatch` / `#slot_...` (penalizesBy 600 both modes); `MinimumRestSlotConstraintTest#overnightPreHorizonPredecessor_trueGapMeasuredCorrectly`; `RestPredecessorServiceTest#shiftModeOvernightRow_spanCarriesTheWrappedEnd_gapMeasuredCorrectly`; `RestWaiverDisclosureTest#waiverOnFirstBusinessDateWithOvernightAcceptedPredecessor_reportsTheTrueGap` — all re-run fresh, all PASS, all naming the pre-fix-wrong number (600/600/60/60) |
| 15 | PF-02 regression: a SLOT-mode span crossing calendar midnight but NOT the desk's anchor (21:00 anchor, slots 23:00-00:00/03:00-04:00) is numerically unchanged (gap 1740, offset 420) | VERIFIED | `RestPredecessorServiceTest#slotModeSpanCrossingMidnightButNotTheAnchor_unchangedByTheFix` read in full, re-run fresh — PASS |
| 16 | Waiver-disclosure report corrected for free: overnight pre-horizon span + waiver now reports `applied` with `measuredGapMinutes()` 60, not `unused` at 1500 | VERIFIED | `RestWaiverDisclosureTest#waiverOnFirstBusinessDateWithOvernightAcceptedPredecessor_reportsTheTrueGap` — PASS |
| 17 | Empty/negative-control edges unaffected: `nullMinimumRestMinutes_noMatch`, no-successor-row day-off shape, empty pre-horizon list | VERIFIED | `RestHorizonEdgeTest#overnightPriorSpanWithNoSuccessorRowOnD_zeroMatches` re-run fresh — PASS (`penalizesBy(0)` both modes) |
| 18 | OVNT-01/OVNT-03 regression safety only — no new overnight-template feature work, Phase 21 behaviour unmoved | VERIFIED | No OVNT-* feature code touched this phase (`git diff` shows zero `src/main/java` changes outside `DayWindow`/`RestSpan`/`SolverService`); the no-successor-row negative is the OVNT-03 shape |

### Deliverable 3 — The structural guard (23-03)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 19 | D-02: the guard is BUILT (not deferred) and wired into the ordinary `./gradlew test` run | VERIFIED | `src/test/resources/rest-gap-arithmetic-guard.md` and `src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java` both exist on disk; no JUnit tag/exclude isolates them from the default `test` task (confirmed by the fresh re-run above, which executed via the ordinary `test` task) |
| 20 | Guard forbids the specific composition (MINUTES_PER_DAY + subtraction + single-arg end accessor on one line), not a blanket ban on the accessor | VERIFIED | `isWrapBlindPredecessorEndComposition` read in full — exactly this three-token AND. `RestSpan.ofSlots`' own use of `anchoredEndMinute` is untouched and still compiles/passes |
| 21 | Call-site allowlist is SET EQUALITY, `{com.wfm.model.RestSpan, com.wfm.service.SolverService}` exactly | VERIFIED | `containsExactlyInAnyOrderElementsOf` confirmed in source; `callSiteSet_matchesTheRegistryAllowlistExactly` re-run fresh — PASS |
| 22 | Guard can provably go red — matcher fires on a synthetic offender, not on a comment, not on the corrected shape; a deliberately broken allowlist is detected | VERIFIED | `wrapBlindMatcher_isLiveAgainstSyntheticStrings` and `deliberatelyBrokenAllowlist_isDetectedAsAMismatch` read in full and re-run fresh — both PASS |
| 23 | Guard is honest in writing about its single-line-scan limits and that it says nothing about correctness | VERIFIED (see Anti-Patterns — disclosure is incomplete relative to the review's WR-01 finding) | `rest-gap-arithmetic-guard.md`'s "Known scope boundaries" section states the single-line-scan limit and the "says nothing about whether the one implementation is CORRECT" disclaimer verbatim, satisfying the plan's literal must-have text |

**Score:** 23/23 must-have truths verified across the three plans (0 present-but-behavior-unverified; the one item genuinely requiring a live solve was never claimed automatable — it is routed to Human Verification below, not scored as a truth)

### Deferred / Advisory — Confirmed Code Review Findings (not phase must-haves, surfaced per task instruction)

Two open findings from `23-REVIEW.md` were independently re-confirmed by reading the cited source
lines directly (not trusted from the review's prose):

- **CR-01 (critical, open, pre-existing).** `SolverService.requireRestFeasibility`'s pre-horizon
  fallback (`SolverService.java:1894-1900` SHIFT, `2008-2039` SLOT) indexes `priorSpanByAgent` by
  agent ID only, with no date check that `d` is the true first business date or that
  `prior.businessDate()` equals `d.minusDays(1)`. Confirmed: this logic predates Phase 23 — Phase
  23's only edit in this region is the single `predecessorEndMinute = window.anchoredWrappedEndMinute(...)`
  assignment line; the surrounding `if (predecessorConfig != null) {...} else {...}` scoping is
  unchanged Phase 22 code. **Judgment: does not undermine this phase's REST-05 claim.** The
  must-haves this phase stakes its REST-05 claim on (truths 5 and 14 above) assert the formula is
  correct *given* that branch executes against a true pre-horizon span — which it demonstrably is.
  CR-01 is a distinct defect about *when* that branch is reached (it can also fire for a mid-period
  day-off/PTO predecessor, substituting a stale historical span), not about the wrap arithmetic this
  phase was scoped to fix. It is correctly out of this phase's explicit scope per the ROADMAP's own
  "Fix direction" text, which names only the arithmetic. It remains open, critical, and unresolved —
  recommend a follow-up phase or plan, not closure by silence.
- **WR-01 (warning, open, confirmed).** `RestGapArithmeticGuardTest`'s matcher
  (`isWrapBlindPredecessorEndComposition`) requires the literal tokens `MINUTES_PER_DAY` and
  `anchoredEndMinute(` plus a `-`. Confirmed by reading `DayWindow.java:387-392`: the public static
  two-argument `endMinuteFromDayStart(dayStart, end)` reproduces the identical wrap-blind formula
  without tripping either token, and a literal `1440` in place of `MINUTES_PER_DAY` is equally
  invisible. Neither evasion is exercised by `wrapBlindMatcher_isLiveAgainstSyntheticStrings`. The
  guard's own must-have text (truth 23 above) is satisfied literally (it does disclose a
  single-line-scan limit and a correctness disclaimer), but does not disclose this specific,
  closeable, already-public-API evasion — a real gap in the guard's actual defensive surface versus
  what its documentation implies. Does not fail any literal must-have; flagged here because it bears
  directly on how much protection "D-02: a third occurrence is unrepresentable" actually buys.

Both findings remain `open` (untriaged) in `23-REVIEW-DISPOSITION.md` as of this verification. They
are not scored against this phase's must-haves (neither must-have text required covering them) but
are surfaced because they are confirmed, live, and unresolved.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/util/DayWindow.java` | `anchoredWrappedEndMinute` primitive | VERIFIED | Present, instance method, correct composition |
| `src/main/java/com/wfm/model/RestSpan.java` | `gapMinutes` on the wrap-aware primitive | VERIFIED | Present, wired |
| `src/main/java/com/wfm/service/SolverService.java` | SLOT pre-horizon branch on the shared primitive | VERIFIED | Present, wired |
| `src/test/java/com/wfm/util/DayWindowTest.java` | `WrappedEndMinute` nested class, 8 tests | VERIFIED | Confirmed 8/8 passing |
| `src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java` | overnight-predecessor proof + boundaries | VERIFIED | Confirmed 24/24 passing |
| `src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java` | Finding 2 SLOT/SHIFT proofs | VERIFIED | Confirmed 20/20 passing |
| `src/test/java/com/wfm/solver/RestHorizonEdgeTest.java` | 6th-class pre-horizon fixtures (PF-01) | VERIFIED | Confirmed 12/12 passing |
| `src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java` | SLOT-mode real-seat proof | VERIFIED | Confirmed 19/19 passing |
| `src/test/java/com/wfm/service/RestPredecessorServiceTest.java` | lookback resolution + PF-02 regression | VERIFIED | Confirmed 12/12 passing |
| `src/test/java/com/wfm/service/RestWaiverDisclosureTest.java` | waiver-disclosure regression | VERIFIED | Confirmed 31/31 passing |
| `src/test/resources/rest-gap-arithmetic-guard.md` | call-site registry | VERIFIED | Present, parseable, 2-entry allowlist matches live source |
| `src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java` | scanner | VERIFIED | Confirmed 6/6 passing |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `RestSpan.gapMinutes` | `DayWindow.anchoredWrappedEndMinute` | `window.anchoredWrappedEndMinute(prev.startTime(), prev.endTime())` | WIRED | `RestSpan.java:132` |
| `SolverService.requireRestFeasibility` (SLOT pre-horizon) | `DayWindow.anchoredWrappedEndMinute` | `window.anchoredWrappedEndMinute(prior.startTime(), prior.endTime())` | WIRED | `SolverService.java:2038` |
| `ScheduleConstraintProvider.minimumRestShift` / `minimumRestSlot` | `RestSpan.gapMinutes` | `.map((cfg, prev, next) -> new RestGapMatch(cfg, prev, next, RestSpan.gapMinutes(prev, next)))` | WIRED | `ScheduleConstraintProvider.java:1073,1166` — fixed for free, no edit needed this phase |
| `RestGapArithmeticGuardTest` | `rest-gap-arithmetic-guard.md` | classpath resource load | WIRED | Confirmed parses and asserts against live source, re-run fresh |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `RestSpan.gapMinutes` output | `RestGapMatch`'s gap value | Live constraint stream (`ScheduleConstraintProvider`), not a stub/static | Yes — traced from a real `AgentShiftAssignment`/`RestSpan` problem fact through to the penalty | FLOWING |
| `requireRestFeasibility` bestGap | pre-solve refusal message | Live `priorSpanByAgent`/`configsByDate` computation | Yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Overnight-predecessor 60-minute gap, 600-point penalty (23-01) | `./gradlew test --rerun --tests com.wfm.solver.MinimumRestShiftConstraintTest ...` (9 classes, run together) | 0 failures, 0 errors across all 9 named classes; `WrappedEndMinute` 8/8, `MinimumRestShiftConstraintTest` 24/24, `RestFeasibilityRefusalTest` 20/20, `RestHorizonEdgeTest` 12/12, `MinimumRestSlotConstraintTest` 19/19, `RestPredecessorServiceTest` 12/12, `RestWaiverDisclosureTest` 31/31, `RestGapArithmeticGuardTest` 6/6, `MidnightTimeArithmeticGuardTest` 12/12 | PASS |
| No residual wrap-blind composition anywhere in `src/main/java` | `wrapBlindCompositionScan_returnsEmptySet` | PASS (empty set) | PASS |
| Exactly one caller set | `callSiteSet_matchesTheRegistryAllowlistExactly` | PASS (`{RestSpan, SolverService}`) | PASS |

Full-suite aggregate (215 classes / 1483 tests / 0 failures / 0 errors / 4 skipped) relied upon per
execution_state rather than re-run in full — the 9-class targeted re-run above is independent
confirmation that is consistent with, and does not contradict, that aggregate.

### Probe Execution

Not applicable — this phase has no `scripts/*/tests/probe-*.sh` convention and none are declared in
the PLAN/SUMMARY files. Step 7c: SKIPPED (no probes declared or conventional).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| REST-01 | 23-01, 23-03 | Minimum rest period configurable, per desk | SATISFIED | No regression; `ScheduleConstraintClassificationTest`/`ConstraintMatchCountNonVacuityTest` unchanged and passing; boundary edges (exact-equality, one-step-either-side) proven |
| REST-02 | 23-01, 23-02, 23-03 | Solver treats insufficient rest as a hard violation, measured between actual end/start instants | SATISFIED | The overnight-predecessor arithmetic defect that specifically violated this requirement is fixed and fixture-proven across all affected call sites |
| REST-05 | 23-01, 23-02, 23-03 | Rest at first/last day of solving horizon has defined, tested behaviour | SATISFIED (for the arithmetic this phase was scoped to fix) | Pre-horizon SLOT and SHIFT branches both corrected and tested; see CR-01 note above for a confirmed, pre-existing, out-of-scope gap in a related but distinct part of the same method |
| OVNT-01 | 23-01, 23-02, 23-03 | Overnight shift template saves correctly | SATISFIED (regression only) | No feature code touched; successor-side symmetry test proves unmoved |
| OVNT-03 | 23-02, 23-03 | Day-off/PTO blocks overnight shift assignment | SATISFIED (regression only) | No feature code touched; no-successor-row negative test proves unmoved |

No orphaned requirements: REQUIREMENTS.md's traceability table attributes REST-01/02/05 and
OVNT-01/03 to Phases 22/21 respectively (expected — Phase 23 is a gap-closure amendment to
already-"Complete" requirements, not a new requirement delivery, so no traceability-table edit was
expected or needed). `REQUIREMENTS.md` itself is unmodified this phase (confirmed via git log).

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `SolverService.java:1894-2039` | n/a | CR-01 — pre-horizon fallback not scoped to the true pre-horizon date (pre-existing, Phase 22) | 🛑 Critical (pre-existing, out of this phase's explicit scope) | See Deferred/Advisory section above — recommend follow-up phase |
| `RestGapArithmeticGuardTest.java:85,228-237` | n/a | WR-01 — guard matcher evadable via `endMinuteFromDayStart(dayStart, end)` or literal `1440` | ⚠️ Warning (confirmed, open) | Narrows the practical protection of D-02's tripwire; does not fail this phase's literal must-haves |
| `RestGapArithmeticGuardTest.java:412-414` | n/a | IN-01 — `TableRow.entryPoint()` dead code | ℹ️ Info | Cosmetic |
| `SolverService.java:2045-2048` | n/a | IN-02 — SLOT branch's `bestGap` formula re-derives `RestSpan.gapMinutes`'s shape inline instead of delegating | ℹ️ Info | Same structural risk class as this phase's root cause, by the reviewer's own words — worth a follow-up |

No TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER markers found in any file modified this phase.

### Human Verification Required

1. **D5 — live overnight-predecessor solve confirmation**
   **Test:** On a desk with an overnight shift template and a configured minimum rest of 660
   minutes, construct a roster where an agent works the overnight template on one business date and
   starts around 07:00 the next. Start a solve. Read the result through `/summary` (never
   `GET /schedules/{id}` — ~4 MB payload, competes with the solver's two cores).
   **Expected:** The hard score is no longer `0hard`, and the `Minimum rest (shift)` constraint (or
   the SLOT-mode pre-solve refusal, naming a smaller best-achievable gap) fires.
   **Why human:** Needs a real Timefold solve against a live desk; no unit fixture substitutes for
   this, and a full solve competes with the running application for its two cores. Deliberately
   deferred by plan 23-03 to end-of-phase UAT harvest (`human_judgment: true`, project default
   `end-of-phase`).

### Gaps Summary

No must-have truth failed. All 23 must-have truths across the three plans — the defect fix (23-01),
the coverage gap across all six affected test classes (23-02), and the structural guard plus phase
gate (23-03) — are independently verified against the live source and against fresh, individually
re-run tests (not trusted from SUMMARY.md). The phase's own two-deliverable framing (a real defect
AND, distinctly, a coverage gap) is reported separately above, per the explicit instruction not to
let one stand in for the other.

The phase routes to `human_needed`, not `passed`, solely because of the one operator confirmation
(D5) this phase itself declared non-automatable and deliberately deferred to end-of-phase UAT — not
because of any failed or uncertain automated check.

Two previously-identified, independently re-confirmed code review findings (CR-01 critical, WR-01
warning) remain `open` in `23-REVIEW-DISPOSITION.md`. Neither fails a stated must-have of this phase:
CR-01 is pre-existing Phase 22 logic outside this phase's explicit arithmetic-fix scope; WR-01's guard
satisfies its must-have's literal text while having a narrower practical detection surface than
ideal. Both are carried forward here as unresolved, open items warranting a maintainer decision
(fix, defer, or accept) — they should not be silently dropped when this phase is marked complete.

---

*Verified: 2026-10-04*
*Verifier: Claude (gsd-verifier)*
