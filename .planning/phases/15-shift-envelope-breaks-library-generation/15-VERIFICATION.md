---
phase: 15-shift-envelope-breaks-library-generation
verified: 2026-09-01T23:10:00Z
status: human_needed
score: 7/7 success criteria verified (11/11 requirement-level truths) + 2/2 gap-closure plans'
  must-haves verified (G-15-22, G-15-29) — automated evidence only; two human-verification items
  from the prior report remain outstanding
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed (2026-08-27T18:45:00Z, 7/7 success criteria — this was BEFORE
    G-15-22 and G-15-29 had automated closure evidence; both were open gaps at that time)
  previous_score: 7/7 success criteria (11/11 requirement-level truths)
  gaps_closed:
    - "G-15-22 (major): a solver-tuning change that wrecks convergence (the acceptor 0hard->1hard
      regression, -9 to -66 on the live desk) shipped CI-green because no fixture in 590 tests was
      sensitive to acceptor behaviour. Closed by SolverQualityGuardTest, an ungated default-suite
      guard asserting three structural invariants (zero split shifts, zero edge breaks, full
      edge-hour coverage) plus a median-violation-count ceiling, on a live-library-shaped synthetic
      desk solved through the shipped solverConfig.xml."
    - "G-15-29 (methodological): a weight-tuning session burned ~12 live solves producing almost no
      usable signal because raw hard scores were compared across weight changes where they are not
      comparable. Closed by a documented comparison rule (15-BENCHMARK.md 'Solver Comparison Rule')
      plus a mechanical proof (thesisProof_atZeroWeightTheViolationCountTableGoesBlind...) that a
      weight change silences score-derived evidence while a structural walker still sees the same
      defect."
  gaps_remaining: []
  regressions: []
gaps: []
deferred:
  - truth: "Blocked-break-hours (breakBlockedHours) has no enforcement point on a shift-scheduled desk"
    addressed_in: "Not yet scheduled — filed in deferred-items.md by explicit operator ruling OR-2
      (out of scope, not rejected). Unaffected by the 15-14/15-15 round; deferred-items.md's own
      staleness check (added this round) reconfirms it is not superseded."
    evidence: "deferred-items.md '15-13' section, still current; ShiftTemplateService.validateBands
      never references breakBlockedHours (unchanged since prior report)."
  - truth: "A shift template's envelope is never validated against the desk's operating window at save time"
    addressed_in: "Not yet scheduled — filed in deferred-items.md by explicit operator ruling OR-2.
      Unaffected by the 15-14/15-15 round."
    evidence: "deferred-items.md '15-13' section, still current."
  - truth: "The seat-supply gate compares day-wide totals, not per-hour distribution, so a solve at
      overallocationHardLimitPct 250 (the UI's own default, not the load-bearing 500) is not
      refused and collapses to -120 hard (G-15-31)"
    addressed_in: "Not yet scheduled — explicitly out of scope for the 15-14/15-15 gap-closure
      round, which the plan itself scoped to G-15-22/G-15-29 only. Deliberately deferred, not a
      regression of this round's work; the round's own guard fixture deliberately does not route
      through the seat-supply gate for exactly this reason (key_links, 15-14-PLAN.md)."
    evidence: "15-UAT.md gap_id: G-15-31, status: open, severity: major, found 2026-09-01."
  - truth: "Calendar-blind and band-composition-blind seat-supply totals (G-15-21, G-15-25) and
      related open gaps (G-15-23, G-15-24, G-15-26, G-15-28)"
    addressed_in: "Not yet scheduled — status: open in 15-UAT.md, out of scope for this round by
      the same deliberate operator scoping decision."
    evidence: "15-UAT.md gap entries for G-15-21, G-15-23, G-15-24, G-15-25, G-15-26, G-15-28."
human_verification:
  - test: "Visually load a shift-mode schedule with a real envelope divergence (or force one) and
      confirm the Agent Schedule table's inline divergence marker and the Agent Allocation grid's
      per-cell E!/x marks and 'unstaffed by design' column header land on the correct cells, with
      legible tooltips and legend"
    expected: "Matches 15-12's own description: an out-of-envelope seat is marked distinctly from a
      surrendered legal slot, an hour no template reaches renders muted/italic and separate from
      the existing red unfilled-demand treatment, and the Agent Schedule Shift column agrees with
      the Agent Allocation group header for the same agent-day."
    why_human: "No frontend test framework exists in this codebase. UNCHANGED since the prior
      report — this item is still open. 15-UAT.md's own Test 19 ('Envelope divergence and
      unstaffed hours render correctly') carries `result: [pending]` as of this verification's own
      read of that file (updated timestamp 2026-09-01T19:20Z), so it has not been closed by any
      session since the prior VERIFICATION.md. Carried forward unchanged, not re-derived from
      scratch."
  - test: "Re-run live UAT Test 10 (shift-mode solve at production scale) against the dev deployment"
    expected: "The StubHub (EN) desk that previously converged on an irreducible -19 hard score
      (all on Shift envelope compliance) now either reaches 0 hard in acceptable time, or is
      refused BEFORE solving with a named shortfall and levers — never a completed/near-complete
      solve carrying residual envelope penalty."
    why_human: "PARTIALLY ADVANCED since the prior report, but NOT resolved — carried forward with
      updated evidence rather than dropped. 15-UAT.md's session_2026_09_01 block records three live
      re-runs at commit a320ca7 (post-contiguity-fix): Run 1 (8d67825c) reached 0 hard/FEASIBLE
      with operator requirements intact — the first run ever to do so. Run 2, BYTE-IDENTICAL
      configuration twenty minutes later, gave -20 hard/NOT FEASIBLE. Run 3, at the UI's
      un-overridden 250% default (not the load-bearing 500%), gave -120 hard (this is G-15-31,
      deferred above). The file's own verdict block states explicitly: 'VERDICT — NOT SET HERE,
      operator's call' — because criterion (a) ('reaches 0 hard') is itself a coin-flip property of
      one run at this variance, not a decidable property of the build. What WAS stable across all
      three runs, including the -120 one, is exactly the invariant set the 15-14/15-15 gap-closure
      round automated (0 split shifts, 0 edge breaks, full edge-hour coverage) — the file
      explicitly recommends restating Test 10 against those invariants plus a violation-count
      ceiling, and notes the automated guard now supplies exactly that measurement, but states the
      restatement itself 'remains the operator's call and is NOT applied here.' This verifier does
      not apply it either — that is a human, product-level decision about what the UAT pass
      criterion should be, not a fact this verifier can determine from source."
---

# Phase 15: Shift Envelope, Breaks & Library Generation Verification Report

**Phase Goal:** On a shift-scheduled desk, the solver assigns each agent exactly one shift per day
from that desk's library and never seats them outside it — a coupling proven sound against a real
fixture, not assumed, with its effect on schedule quality measured honestly rather than discovered
at UAT; breaks are distributed rather than taken simultaneously, the result is legible as shifts in
the Agent Allocation view, and an operator can have a starting library suggested from demand
instead of composing one by hand.

**Verified:** 2026-09-01T23:10:00Z
**Status:** human_needed
**Re-verification:** Yes. This is the third verification pass on this phase. The first (2026-08-27
13:15Z) reported `passed 11/11` before UAT surfaced gap G-15-10 (blocker). The second (2026-08-27
18:45Z) re-derived all seven ROADMAP success criteria from HEAD after G-15-10's gap-closure round
and reported `human_needed` on two outstanding human-verification items, with G-15-22 and G-15-29
still open as newly-discovered process gaps. This third report re-verifies against HEAD after two
additional gap-closure plans (15-14, 15-15) that close G-15-22 and G-15-29 specifically — nothing
else. It does not re-litigate the prior report's seven success criteria from scratch (they were
independently re-derived from source last time and are unaffected by this round's diff), but does
spot-check that nothing regressed and independently re-runs this round's own test evidence rather
than accepting it from either gap-closure SUMMARY.

## Note on this report's evidentiary basis

This round's two new files (`LiveShapeShiftDeskFixture.java`, `SolverQualityGuardTest.java`) and
the four modified planning documents (`15-BENCHMARK.md`, `15-UAT.md`, `deferred-items.md`, and the
plan/summary pair) were checked directly against source at current HEAD — not accepted from
15-14-SUMMARY.md or 15-15-SUMMARY.md prose. Independently re-run in this verification session:

- `./gradlew test --tests "com.wfm.solver.SolverQualityGuardTest"` — **10 tests, 0 failures, 0
  errors, 52.166s** (fresh JVM run, this session; matches both SUMMARYs' claimed counts exactly).
- The orchestrator's independently-run full suite (`cleanTest test`, not a cache hit) is also
  accepted as evidence for this report's regression check: **600 tests, 0 failures, 0 errors, 2
  skipped, BUILD SUCCESSFUL in 10m51s** — consistent with 15-15-SUMMARY's own claimed 600/0/0/2.
- Working tree confirmed clean (`git status --porcelain` empty for the phase directory and the
  solver test package) — the "uncommitted G-15-27 change" 15-15-SUMMARY.md flagged as an
  orchestrator/human follow-up has since been committed (`660408d`, verified by direct `git log`
  read, predating this verification session).
- Every grep/source check below was run fresh in this session against current HEAD, not copied
  from either SUMMARY's acceptance-criteria checklist.

## Goal Achievement

### Part A — The Seven ROADMAP Success Criteria (carried forward, spot-checked for regression)

The prior report (2026-08-27 18:45Z) independently re-derived all seven success criteria from
source and re-ran their supporting test evidence. This round's diff (`545e6f8..660408d`) touches
only `src/test/java/com/wfm/solver/{LiveShapeShiftDeskFixture,SolverQualityGuardTest}.java` and
four `.planning/` documents — no `src/main` file changed. Regression check performed:

```
git diff --stat 5bf636e..HEAD -- src/main
```

returns nothing. **No production code changed in this round.** All seven success criteria therefore
stand as independently verified in the prior report; re-litigating them from zero would not surface
new evidence, since the code they were verified against is byte-identical. What follows below (Part
B) is what actually changed and is verified fresh.

| # | Success Criterion | Status | Basis |
|---|---|---|---|
| SC1 | One shift/agent-day, `AgentShiftAssignment`, shift-grouped Agent Allocation, slot desk unchanged | ✓ VERIFIED | Unchanged since prior report; no `src/main` diff this round |
| SC2 | Hard-constraint envelope compliance, independent ground-truth check | ✓ VERIFIED | Unchanged; `ShiftEnvelopeGroundTruthTest`, `ShiftDeskEndToEndRegressionTest` untouched this round |
| SC3 | Contiguous working day, break from template, mode-gating unchanged for slot desks | ✓ VERIFIED | Unchanged; `ShiftModeBreakGatingTest`, `ShiftModeBreakGeometryGuardTest` untouched |
| SC4 | Break bands, per-agent-day band choice, real `breakClustering` body, starving-timeslot fixture | ✓ VERIFIED | Unchanged; not in this round's diff |
| SC5 | Feasible CH, two sequential phases, no pre-assignment pipeline, solver-build test | ✓ VERIFIED | Unchanged; `solverConfig.xml` untouched by this round (`git diff --stat` confirms) |
| SC6 | Seeded, step-count-terminated A/B benchmark with pre-committed threshold, honest plateau reporting | ✓ VERIFIED | `15-BENCHMARK.md`'s original Pass Rule/Results sections untouched (append-only diff, confirmed below); new content is additive |
| SC7 | Suggested shift library from demand, editable draft, reuses coverage predicate | ✓ VERIFIED | Unchanged; `ShiftLibraryGenerationService*` untouched this round |

### Part B — What This Round Actually Changed: G-15-22 and G-15-29 Closure

**Status: ✓ VERIFIED** (both gaps; both plans' must-haves independently checked against source and
re-run, not accepted from SUMMARY)

**G-15-22 — a solver-tuning regression must fail locally, not on a live desk.**

- `SolverQualityGuardTest` runs in the DEFAULT `./gradlew test` suite with no gating flag —
  confirmed: `git diff --quiet HEAD~ish -- build.gradle .github/workflows/` shows no change to
  either, and the class carries no `@EnabledIfSystemProperty` or similar annotation (direct read).
  This is the specific fix for how G-15-22 shipped in the first place: `ShiftModelBenchmarkTest` is
  gated behind `-Dwfm.benchmark=true` and therefore never ran on the deploy gate.
- The guard loads the real shipped config, never a hand-built one — confirmed:
  `grep -c 'createFromXmlResource("solverConfig.xml")'` returns hits in the file (direct read,
  lines around `solve()`); `withStepCountLimit` and `withRandomSeed` are both present; no
  wall-clock-based termination or timing assertion exists anywhere in the file (elapsed millis is
  printed only, confirmed by reading every `assert`/`.as(` site).
- Three independent structural walkers (`findSplitShifts`, `findEdgeBreaks`,
  `findUnstaffedEdgeHours`) compute membership from raw `LocalTime` arithmetic on
  `template.getStartTime()` / `band.getOffsetMinutes()` / `band.getDurationMinutes()` — confirmed by
  direct read of all three method bodies; none references `ShiftBandPair.covers`,
  `ScheduleConstraintProvider`, or a score director.
- Five red-proof tests (`redProof_INV1_*` x3, `redProof_INV2_*`, `redProof_INV3_*`) each corrupt an
  already-solved schedule and assert an EXACT size + identity, not merely non-emptiness — confirmed
  by reading each test body; a negative control (`redProof_INV1_theBreakWindowItselfIsNotAHole`)
  proves the break window itself is never mistaken for a hole.
- `TOTAL_VIOLATION_CEILING = 3` exists as a named constant with the pre-committed rule (baseline
  median + 2, floored at 2) stated in its javadoc — confirmed by direct read at
  `SolverQualityGuardTest.java:113`; the assertion is on the median across 5 seeds
  (`liveShapeDesk_fiveSeeds_holdEveryStructuralInvariant`), never a single seed's raw `hardScore` —
  confirmed by reading the assertion body (`.isLessThanOrEqualTo((double) TOTAL_VIOLATION_CEILING)`
  operates on a computed median, not `hardScore`).
- `defaultConstraintWeights_areTheDocumentedShippedValues` pins the four shipped
  `ConstraintWeights` defaults — confirmed by cross-reading the test's asserted literals against
  `ConstraintWeights.java`'s actual field defaults (`unassignedAssignmentWeight = ofSoft(1000)`,
  `shiftEnvelopeComplianceWeight = ofHard(1)`, `bandCapacityWeight = ofHard(1)`,
  `shiftWorkContiguityWeight = ofHard(10)`) — all four match.
- Independently re-run this session: **10/10 pass, 52.166s**, isolated JVM, matching both SUMMARYs'
  claims exactly (test-result XML transcribed above).

**G-15-29 — solver comparisons must use violation counts, never raw hard scores, across weight changes.**

- `thesisProof_atZeroWeightTheViolationCountTableGoesBlind_butTheWalkerStillSeesTheSplit` — read
  directly: one `unseat` corruption, one solve, two `hardMatchCountsByConstraint` calls (live weight,
  then `ofHard(0)`), one structural-walker call reused across both readings. This is a mechanical
  proof, not an assertion of authority: the constraint key is present in the violation-count map at
  the live weight and absent after the weight is zeroed, while `findSplitShifts` returns the
  identical hole in both readings — confirmed present and asserted, by direct read of the test body.
- `15-BENCHMARK.md`'s two new sections (`## Solver Comparison Rule (G-15-29)`,
  `## Solver Quality Guard (G-15-22)`) are present — confirmed via `grep -n` — and the diff against
  the file's pre-round state is append-only: `git diff` shows no line above the previous end-of-file
  touched (verified by reading the diff directly in this session, not merely trusting the SUMMARY's
  claim).
- `15-UAT.md`: `G-15-22` and `G-15-29` both read `status: resolved` with `resolved_by` and
  `resolved_evidence` keys present — confirmed by direct grep/read at their respective line
  locations. No other gap entry shows evidence of having been touched by this round (spot-checked
  G-15-27, which carries its own, separately-authored resolution — see below).
- **G-15-22's honesty disclosure, independently confirmed:** its `resolved_evidence` states the
  guard was NOT back-tested against the original failing acceptor commit (already reverted before
  the guard existed) — confirmed present by direct read, not merely claimed by the SUMMARY to exist.

### Regression / hygiene check on this round's document changes

- **`15-BENCHMARK.md`:** confirmed append-only via direct diff read — no edit to the original Pass
  Rule or Results sections that establish SC6's "threshold committed before the number was seen"
  claim.
- **`15-UAT.md`:** the `G-15-27` entry (contiguity, unrelated to this round's two gaps) is also
  `status: resolved` — but its own commit (`660408d`, "docs(15): mark G-15-27 resolved — contiguity
  shipped in a02d150") is a separate, explicitly-authored commit, made outside plans 15-14/15-15 and
  correctly NOT bundled into either gap-closure plan's commits (confirmed: `git show --stat
  660408d` shows only that one file, and its message states it was authored before the 15-14/15-15
  round and deliberately left unstaged by both executors). This resolves the exact "outstanding,
  uncommitted G-15-27 change" that 15-15-SUMMARY.md flagged as a follow-up — it is no longer
  outstanding.
- **`CR-04` (unbounded warning-list growth), flagged WARNING in the prior VERIFICATION.md:**
  independently re-checked against current source and found **already fixed**, though not by this
  round. `ScheduleOutputService.java` now contains a `publishDivergenceWarning` method using
  `removeIf`-then-add (idempotent, synchronized) — confirmed by direct read at
  `ScheduleOutputService.java:235,269-276`. `ScheduleOutputServiceShiftReportingTest` carries three
  corresponding tests (`buildAgentSchedule_repeatedPolls_doNotGrowTheWarningsList`,
  `buildAgentSchedule_divergenceThatClears_removesItsStaleWarning`,
  `buildAgentSchedule_preservesWarningsItDoesNotOwn`) — confirmed present by direct grep. `15-UAT.md`
  Test 20 records this as `confirms: 15-REVIEW.md CR-04 — predicted in code review, now observed in
  the field by the operator` with a `fix:` and `guard_added:` block. **This is a genuine
  improvement since the prior report and is noted here rather than silently dropped — CR-04 no
  longer needs carrying forward as an open warning.**
- **`WR-01`** (unscoped `deleteByShiftTemplate_Id` write) — re-confirmed present, unchanged, still a
  hygiene item rather than a blocker (direct read, `ShiftTemplateBreakBandRepository.java:28`).
- **`WR-02`** (unstaffed-by-design tooltip derives from that day's assigned shifts, not the full
  library) — re-confirmed present, unchanged; explicitly named as a known, unfixed caveat in
  `15-UAT.md` Test 19's `known_caveat` block.

## Required Artifacts (this round's additions)

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `src/test/java/com/wfm/solver/LiveShapeShiftDeskFixture.java` | Live-library-shaped synthetic desk fixture | ✓ VERIFIED | 481 lines; `TEMPLATE_SPECS` (5 entries incl. `Weekend Flex` slack template), band-margin static validator throwing `IllegalStateException`, routes filler seats through production `SolverSeatExpansionAccess.expandMinimumStaffingSeats` |
| `src/test/java/com/wfm/solver/SolverQualityGuardTest.java` | Guard: 3 structural walkers, violation-count reporter, 5-seed sweep, pinned defaults, red-proofs, thesis proof | ✓ VERIFIED | 1078 lines, 10 `@Test` methods, re-run independently 10/10 pass |
| `.planning/phases/15-shift-envelope-breaks-library-generation/15-BENCHMARK.md` | Append-only Comparison Rule + Guard baseline sections | ✓ VERIFIED | Both section headers present; append-only diff confirmed |
| `.planning/phases/15-shift-envelope-breaks-library-generation/15-UAT.md` | G-15-22, G-15-29 closed in place | ✓ VERIFIED | Both `status: resolved` with `resolved_by`/`resolved_evidence` |
| `.planning/phases/15-shift-envelope-breaks-library-generation/deferred-items.md` | Staleness-check section for this round | ✓ VERIFIED | `## 15-14 / 15-15` section present |

## Key Link Verification (this round's additions)

| From | To | Via | Status |
|---|---|---|---|
| `SolverQualityGuardTest.solve` | shipped `solverConfig.xml` | `SolverConfig.createFromXmlResource("solverConfig.xml")`, never hand-built | ✓ WIRED |
| Three invariant walkers | raw `LocalTime` arithmetic | No call to `ShiftBandPair.covers`, `ScheduleConstraintProvider`, or a score director — confirmed by direct read | ✓ WIRED (deliberately independent) |
| `LiveShapeShiftDeskFixture.build` | `SolverSeatExpansionAccess.expandMinimumStaffingSeats` | Production filler-seat routing, not a fixture-local reimplementation | ✓ WIRED |
| Guard test class | `SolverSeatSupplyGateAccess.requireShiftEnvelopeSeatSupply` | Deliberately NOT called — that gate is the subject of open gaps G-15-21/25/31, routing the guard through it would make its verdict depend on known-broken code | ✓ CORRECTLY NOT WIRED (by design, confirmed) |
| Every invariant assertion | `buildQualityReport` | `.as(...)` description built from the reporter on every INV-1/2/3 assertion — confirmed by direct read | ✓ WIRED |

## Requirements Coverage

| Requirement | This Round's Plans | Status | Evidence |
|---|---|---|---|
| ENVL-02 | 15-14 | ✓ SATISFIED | Pinned shipped defaults test; guard's live-weight-pinned envelope-compliance invariant coverage |
| ENVL-04 | 15-14, 15-15 | ✓ SATISFIED | INV-1/INV-2 walkers + red-proofs + negative control, all independently re-run |
| XCUT-04 | 15-14, 15-15 | ✓ SATISFIED | 5-seed sweep, median-not-mean, ungated default-suite guard, Comparison Rule documented |

All three requirement IDs declared across plans 15-14/15-15's `requirements:` frontmatter are
accounted for. The task's full requirement list (ENVL-01…10, SHLB-07) was already fully covered per
the prior report and is unaffected by this round's diff (Part A above). No orphaned requirements.
`REQUIREMENTS.md`'s traceability table still shows the stale `"ENVL-01…10 | Phase 15 | Pending"` row
inconsistent with the `[x]` checkboxes directly above it — unchanged since the prior report,
informational only, not a phase-goal gap.

## Anti-Patterns Found

`TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` scan across both new files this round
(`LiveShapeShiftDeskFixture.java`, `SolverQualityGuardTest.java`): **zero markers found.**

No new anti-patterns found in this round. CR-04 (flagged WARNING in the prior report) is now fixed
(see Regression check above, not this round's own work but confirmed current). WR-01 and WR-02
carried forward unchanged, still hygiene-level, not blockers.

## Behavioral Spot-Checks (re-run independently in this verification session)

| Behavior | Command | Result | Status |
|---|---|---|---|
| Solver quality guard, isolated | `./gradlew test --tests "com.wfm.solver.SolverQualityGuardTest"` | `tests="10" skipped="0" failures="0" errors="0" time="52.166"` | ✓ PASS |
| Full backend suite (accepted from orchestrator's independently-run `cleanTest test`, not a cache hit — this session did not re-run the full suite a second time, per the "run the full suite at most once" constraint) | `./gradlew cleanTest test` | `600 tests, 0 failures, 0 errors, 2 skipped, BUILD SUCCESSFUL in 10m51s` | ✓ PASS |
| Build/CI files untouched by this round | `git diff --stat -- build.gradle .github/workflows/` (across the gap-closure commit range) | empty | ✓ PASS |
| No `src/main` file touched by this round (regression scope check) | `git diff --stat 5bf636e..HEAD -- src/main` | empty | ✓ PASS |

## Probe Execution

No `scripts/*/tests/probe-*.sh` files exist in this repository and no PLAN/SUMMARY for this phase
references a probe-based verification convention. **SKIPPED — no runnable probe entry points.**

## Human Verification Required

See the `human_verification` block in this file's frontmatter for full detail. Both items are
**carried forward from the prior VERIFICATION.md, not newly discovered**, per this task's explicit
instruction not to silently drop them:

### 1. Visual confirmation of the new divergence/unstaffed-hour rendering (UNCHANGED — still open)

No frontend test framework exists in this codebase. `15-UAT.md` Test 19 ("Envelope divergence and
unstaffed hours render correctly") still carries `result: [pending]` as of this file's own
2026-09-01T19:20Z update timestamp — it was not reached in the session that closed G-15-22/G-15-29,
which instead ran end-to-end on Test 10's mechanism. Still the single highest-value open human item
alongside Test 11.

### 2. Re-run live UAT Test 10 against the dev deployment (ADVANCED, but verdict still undecided)

Substantially more evidence exists than at the prior report: three real live runs at commit a320ca7
(post-contiguity-fix) are now on record in `15-UAT.md`'s `session_2026_09_01` block, including the
first-ever 0-hard/FEASIBLE run with operator requirements intact. But a byte-identical
configuration also produced -20 hard twenty minutes later, and the file's own conclusion is that
"reaches 0 hard" is not a decidable property of this build at current search variance — the
question of what Test 10's actual pass criterion should be (raw hard score vs. the invariant set
this round's guard automates) is now squarely a human/product decision, explicitly left open by the
file itself ("VERDICT — NOT SET HERE, operator's call"). This verifier does not resolve product
decisions and does not apply the file's own suggested restatement on the file's behalf.

## Gaps Summary

**No gaps found in this round's own scope.** Both plans' must-haves (15-14: 6 truths, 15-15: 5
truths) were checked directly against source — not accepted from either SUMMARY — and hold. The
guard is genuinely in the default, ungated suite; its three structural invariants are proven able to
both pass on a clean solve and fail on an exactly-identified injected defect; the score-blindness
thesis at the heart of G-15-29 is mechanically demonstrated, not merely argued; the comparison rule
is written down where an engineer will find it and is printed in the guard's own failure output; and
both gaps are closed in `15-UAT.md` with an honest statement of what was and was not back-tested.
Independently re-run: 10/10 for the guard class alone, 600/0/0/2 for the full suite (accepted from
the orchestrator's independently-verified, non-cached run for this exact commit).

**No regressions.** No `src/main` file changed in this round; all seven of the phase's ROADMAP
success criteria, independently re-derived from source in the prior report, are unaffected. One
prior WARNING (CR-04) is confirmed fixed since the prior report, though not by this round's own
work — noted rather than silently carried forward as still-open. Two hygiene items (WR-01, WR-02)
and four deferred latent gaps (breakBlockedHours enforcement, envelope-vs-operating-window save-time
validation, plus the newly-recorded G-15-31 day-total seat-supply blindness and the broader
G-15-21/23/24/25/26/28 cluster) remain open by deliberate, previously-recorded operator scoping
decisions — expected, not defects of this execution round, and correctly outside this round's
stated scope (the plans themselves scoped to G-15-22/G-15-29 only).

**Status is `human_needed`, not `passed`,** for the same reason as the prior report and no other:
two human-verification items remain outstanding. One is entirely unchanged (visual rendering
confirmation — UAT Test 19 still pending). The other has substantially more automated and live
evidence behind it than before, but its resolution requires a human product decision this verifier
is not positioned to make (what Test 10's actual pass criterion should be, given measured solve
variance). Nothing in the codebase evidence gathered here contradicts the phase goal being achieved
at the mechanism level — the coupling between shift assignment and envelope containment is proven
sound against a real fixture (not assumed), and its effect on schedule quality is now measured
honestly via a permanent, ungated, invariant-based guard rather than discovered ad hoc at UAT, which
is precisely what the phase's own goal statement asks for. What remains open is the live desk's
search-variance plateau and the visual proof of the rendering work — both correctly routed to a
human rather than manufactured into a false `passed`.

---

_Verified: 2026-09-01T23:10:00Z_
_Verifier: Claude (gsd-verifier)_
