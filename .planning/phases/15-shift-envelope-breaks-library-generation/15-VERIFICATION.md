---
phase: 15-shift-envelope-breaks-library-generation
verified: 2026-08-27T12:05:31Z
status: gaps_found
score: 9/11 must-haves verified
behavior_unverified: 0
overrides_applied: 0
gaps:
  - truth: "ENVL-01 — on a shift-scheduled desk, the solver assigns each working agent exactly one shift per day from that desk's library"
    status: failed
    reason: "The coupling mechanism itself (one AgentShiftAssignment planning variable per working agent-day, hard-constrained into its envelope) is genuinely sound and independently proven by ShiftEnvelopeGroundTruthTest — but the 'from that desk's library' clause is violated: SolverService.startSolve's liveShiftTemplates filter excludes only RETIRED templates (effectiveTo before today) and never excludes UPCOMING templates (effectiveFrom in the future). A shift template created today with an effectiveFrom three months out is immediately assignable in the current solve. This directly undermines Phase 14's SHLB-03 effective-date-range guarantee and is confirmed by direct code reading, independently of the code review (15-REVIEW.md CR-01)."
    artifacts:
      - path: "src/main/java/com/wfm/service/SolverService.java"
        issue: "Lines 278-282: `.filter(t -> t.getEffectiveTo() == null || !t.getEffectiveTo().isBefore(LocalDate.now()))` checks only the retirement boundary. `ShiftLibraryValidationService.withinEffectiveRange` (line 228) already implements the correct two-sided predicate but SolverService never calls it. No downstream code (ShiftBandPair.covers, AgentShiftAssignment.getEligibleShiftBandPairs, or any constraint) checks the effective range against a specific agent-day either."
    missing:
      - "Filter liveShiftTemplates (or, better, filter per agent-day when building ShiftBandPairs/AgentShiftAssignments) by both ends of the effective range against the schedule's actual period dates, not LocalDate.now() — e.g. reusing ShiftLibraryValidationService.withinEffectiveRange, per the review's own suggested fix."
      - "A test asserting an UPCOMING template (effectiveFrom after the schedule period) is excluded from the solver's value range for that period — SolverServiceShiftAssignmentTest currently has no coverage for this case."
  - truth: "ENVL-10 / XCUT-01 — the shift an agent was assigned is visible correctly in the accepted-schedule view, and a shift-scheduled desk's schedulingMode is recorded accurately"
    status: failed
    reason: "ScheduleService.loadSnapshotData infers an accepted schedule's schedulingMode solely from whether any agent_shift_assignment rows exist for it. acceptSchedule only ever writes a row for an AgentShiftAssignment whose shiftBandPair is non-null. acceptSchedule is reachable for any COMPLETED or STOPPED schedule — feasibility is not required. A SHIFT-mode solve that is stopped early, or one whose live library matches no agent's contracted hours, can legitimately reach COMPLETED/STOPPED with zero placed shifts. Accepting it persists zero agent_shift_assignment rows, and every subsequent load reports schedulingMode = SLOT for what was actually a SHIFT-mode solve — a permanent mislabeling of an immutable historical record, feeding directly into ScheduleResults.tsx's mode branch and selecting the wrong (ungrouped) rendering. Confirmed by direct code reading, independently of the code review (15-REVIEW.md CR-02)."
    artifacts:
      - path: "src/main/java/com/wfm/service/ScheduleService.java"
        issue: "Line ~416: `schedule.setSchedulingMode(shiftAssignments.isEmpty() ? SchedulingMode.SLOT : SchedulingMode.SHIFT)` infers state from a collection that can legitimately be empty for the state it is supposed to signal."
    missing:
      - "Persist schedulingMode explicitly at accept time (e.g. a column on schedule, or on the accepted agent_assignment/agent_shift_assignment snapshot) written from schedule.getScheduleConfig()'s in-memory value, rather than inferred from row presence."
      - "A test covering a SHIFT-mode accept with zero placed shift rows, asserting the reloaded schedulingMode is still SHIFT."
human_verification: []
---

# Phase 15: Shift Envelope, Breaks & Library Generation Verification Report

**Phase Goal:** The solver assigns one shift per agent-day via a hard-constraint coupling proven
sound and benchmarked honestly, breaks are distributed rather than simultaneous, and a starting
library can be suggested from demand.

**Verified:** 2026-08-27T12:05:31Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

The phase goal's three central claims were independently re-derived from the codebase and
re-executed, not read off SUMMARY.md or 15-BENCHMARK.md prose:

1. **"The solver assigns one shift per agent-day via a hard-constraint coupling proven sound"** —
   VERIFIED at the mechanism level. `AgentShiftAssignment` carries exactly one `@PlanningVariable`
   per row, one row per working agent-day (`AgentDayConfig.effectiveHours > 0`), so a double
   assignment is structurally impossible. `shiftEnvelopeCompliance` is a genuine hard
   `ConstraintStream` constraint (not the rejected Option C filtered-value-range) and
   `ShiftEnvelopeGroundTruthTest` — an external walker sharing no code with the constraint it
   checks — independently confirms agreement between the reported score and actual envelope
   membership, including six adversarial corruption cases. I ran both `SolverConfigBuildTest` and
   `ShiftEnvelopeGroundTruthTest` directly (not trusting the SUMMARY): both pass (8/8 tests, 0
   failures). **However**, one clause of ENVL-01 — "from that desk's library" — has a genuine,
   uncovered defect (CR-01, below): a not-yet-effective (UPCOMING) shift template is treated as
   live immediately, which the solve-time code path does not guard against even though the
   validation-time code path already does.

2. **"…benchmarked honestly"** — VERIFIED, and re-confirmed independently rather than taken on
   trust. `15-BENCHMARK.md`'s git history shows the Pass Rule, Harness Configuration, and a
   placeholder Results/Verdict section committed at `cd26db93` (01:17:46), with the actual numbers
   filling in 20 minutes later at `836033a`/`57ad2a7` (01:37:18–01:50:02) — the threshold was
   genuinely fixed before any result existed, not adjusted after seeing one. I independently
   re-ran `ShiftModelBenchmarkTest -Dwfm.benchmark=true` myself: the reproduced per-seed numbers
   (e.g. slot seed 1 → `0hard/-128soft`, slot seed 2 → `-5140hard/-149soft`, every shift-arm seed
   → `0hard/-192soft`) match the transcribed table in `15-BENCHMARK.md` exactly. The verdict's
   own "no measurable difference" framing under the noise rule, and its refusal to call the 5/5
   vs 1/5 convergence gap anything other than what it is, hold up against direct inspection of
   the harness output — this is a genuinely disciplined write-up, not a rebrand of Phase 12's
   mistake.

3. **"…breaks are distributed rather than simultaneous, and a starting library can be suggested
   from demand"** — VERIFIED. `breakClustering` has a real body (no longer
   `penalizeConfigurable(a -> 0)`), `BreakClusteringConstraintTest` carries the exact
   single-band-starves / multi-band-does-not fixture the ROADMAP asked for, and
   `ShiftLibraryGenerationService` genuinely calls `ShiftLibraryValidationService.covers()` (the
   `verify.key-links` tool reported a false negative here on an escaping-regex mismatch — I
   confirmed the call at `ShiftLibraryGenerationService.java:331` by direct grep).

**Two genuine, uncovered defects were found by direct code reading** (independently reproducing
`15-REVIEW.md`'s CR-01 and CR-02, not merely citing them) that undermine specific clauses of
ENVL-01 and ENVL-10/XCUT-01. These are detailed in the Gaps section below and are the reason this
phase does not pass outright.

### Observable Truths (by requirement ID)

| # | Requirement | Truth | Status | Evidence |
|---|---|---|---|---|
| 1 | ENVL-01 | Solver assigns each working agent exactly one shift per day, **from that desk's live library** | ✗ FAILED (partial) | Coupling mechanism sound (`AgentShiftAssignment` single variable, `SolverServiceShiftAssignmentTest`) — but `SolverService.startSolve` never excludes UPCOMING templates (CR-01); see Gaps |
| 2 | ENVL-02 | Agent never seated outside their shift envelope, hard constraint | ✓ VERIFIED | `shiftEnvelopeCompliance` (`ScheduleConstraintProvider.java:413`), `ShiftEnvelopeComplianceConstraintTest`, `ShiftEnvelopeGroundTruthTest` (ran directly, 7/7 pass) |
| 3 | ENVL-03 | Specialization varies freely within a shift | ✓ VERIFIED | `ShiftEnvelopeGroundTruthTest#specializationVariesWithinShift_notFlaggedByThisWalker` (ran, passes) |
| 4 | ENVL-04 | Working day contiguous apart from break | ✓ VERIFIED | `ShiftModeBreakGatingTest#everySeatedAgentDay...` (369 lines, substantive) |
| 5 | ENVL-05 | Break placement from shift template, not the four emergent constraints | ✓ VERIFIED | `ShiftModeBreakGatingTest` — 6 gated-constraint cases, each proving inertness in SHIFT and unchanged behavior in SLOT |
| 6 | ENVL-06 | Feasible initial solution via CH alone, no pre-assignment pipeline | ✓ VERIFIED | `solverConfig.xml` two explicit `<constructionHeuristic>` phases (shifts-first, D-08); benchmark's own log shows the shift arm reaching `0hard` via CH alone (0 local-search steps) on every seed |
| 7 | ENVL-07 | Reported score agrees with an independent ground-truth check | ✓ VERIFIED | `ShiftEnvelopeGroundTruthTest` — ran directly, 7/7 pass, including the deliberately-corrupted-solution agreement case |
| 8 | ENVL-08 | Shift template defines break bands; solver assigns one band per agent-day | ✓ VERIFIED | `AgentShiftAssignment`'s single `(template,band)` variable (D-04); `BandCapacityConstraintTest` (244 lines) |
| 9 | ENVL-09 | Break clustering constraint has a real, penalising body | ✓ VERIFIED | `ScheduleConstraintProvider.breakClustering` (line 732) replaces the old placeholder; `BreakClusteringConstraintTest` carries the required single-band-starves/multi-band-does-not fixture |
| 10 | ENVL-10 | Agent Allocation view groups agents by assigned shift; slot desk unchanged | ✗ FAILED (partial) | Grouping itself confirmed wired (`ScheduleResults.tsx:561-629`) — but the `schedulingMode` value it branches on can be silently wrong for an accepted schedule (CR-02); see Gaps |
| 11 | SHLB-07 | Suggested library computed from demand, editable draft, never auto-applied, reuses `covers()` | ✓ VERIFIED | `ShiftLibraryGenerationService` calls `shiftLibraryValidationService.covers()` directly (confirmed by grep, tool's regex match was a false negative); `ShiftLibraryGenerationServiceTest` (399 lines); frontend draft panel with "isn't saved" copy matches must-have verbatim |

**Score:** 9/11 requirement-level truths verified; 2 failed (partial — the underlying mechanism for
each is otherwise sound; the specific undermining clause is the failure).

### Cross-Cutting Requirements

| Requirement | Truth | Status | Evidence |
|---|---|---|---|
| XCUT-01 | Written shift/mode data visible everywhere it's displayed | ⚠️ Partially undermined by the same CR-02 defect as ENVL-10 above | `AgentShiftAssignment` denormalised snapshot correctly feeds the roster/export/accepted-schedule view in the common case; the `schedulingMode`-mislabeling edge case is the gap |
| XCUT-03 | Any `solverConfig.xml` change validated by a test that actually builds a solver | ✓ VERIFIED (code) — **but still unchecked in REQUIREMENTS.md** | `SolverConfigBuildTest` exists, is substantive, and I ran it directly: `tests="1" failures="0" errors="0"`. `solverConfig.xml` changed repeatedly this phase (two explicit CH phases, D-08 arm reordering) and this test is part of the full suite. This is a documentation gap in REQUIREMENTS.md, not a code gap — see note below. |
| XCUT-04 | Seeded, step-count-terminated A/B benchmark, threshold pre-committed | ✓ VERIFIED, independently re-run | See Goal Achievement §2 above — git history confirms pre-commitment, and I reproduced the harness output byte-for-byte against the transcribed table |
| XCUT-05 | Every constraint classified mode-agnostic/mode-gated/needs-variant | ✓ VERIFIED | `ScheduleConstraintClassification.java` — zero `OPEN_RESOLVE_IN_PHASE_15` rows remain (grep confirms); the two preference constraints reclassified `MODE_GATED` |

**Note on the REQUIREMENTS.md ENVL-01 / XCUT-03 checkbox discrepancy** (per the flagged scrutiny
items): both are still unchecked in `.planning/REQUIREMENTS.md` despite 15-03-SUMMARY.md claiming
both `requirements-completed`. Git-blaming the file (`63558c9`, `docs(15-04): mark ENVL-02,
ENVL-03, ENVL-06, ENVL-07 complete`) shows ENVL-02/03/06/07 were flipped to `[x]` in the same
commit that left ENVL-01 untouched at `[ ]` — this looks like a deliberate choice by whoever wrote
that commit, not an oversight, and it turns out to be the *correct* current state: CR-01 is a real,
live defect, so ENVL-01 should stay unchecked until it's fixed. XCUT-03, by contrast, has no
matching defect — the required test exists, is substantive, and passes; leaving it unchecked
appears to be a genuine documentation gap that should be corrected (recommend checking it off once
the ENVL-01 gap closure commit lands, so the two aren't conflated).

### Required Artifacts (all 8 plans)

| Plan | Artifacts | Status |
|---|---|---|
| 15-01 | V40 migration, ShiftTemplateBreakBand, ShiftTemplateBreakBandRepository, MigrationEntityConsistencyTest | ✓ VERIFIED (4/4, gsd-tools) |
| 15-02 | ShiftLibraryGenerationService, ShiftLibrarySuggestionResponse, ShiftLibraryGenerationServiceTest | ✓ VERIFIED (3/3, gsd-tools) |
| 15-03 | AgentShiftAssignment, ShiftBandPair, SolverConfigBuildTest, V41 migration | ✓ VERIFIED (4/4, gsd-tools) |
| 15-04 | ShiftEnvelopeGroundTruthTest, ShiftModeFixtures | ✓ VERIFIED (2/2, gsd-tools; both ran directly by me) |
| 15-05 | frontend/src/api/client.ts, frontend/src/pages/ShiftLibrary.tsx | ✓ VERIFIED (2/2, gsd-tools) |
| 15-06 | ShiftModeBreakGatingTest, BreakClusteringConstraintTest, V42 migration, XCUT-05 table | ✓ VERIFIED (4/4, gsd-tools) |
| 15-07 | AgentShiftAssignmentRepository, ScheduleServiceShiftSnapshotTest | ✓ VERIFIED (2/2, gsd-tools) |
| 15-08 | ShiftModelBenchmarkTest, 15-BENCHMARK.md | ✓ VERIFIED (2/2, gsd-tools; independently re-run by me) |

All 25 declared artifacts across all 8 plans exist, are substantive (no stubs), and are not
placeholder implementations. No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers found in
any of the 16 files spot-checked for anti-patterns (model, service, repository, constraint
provider, and frontend files).

### Key Link Verification

| From | To | Via | Status |
|---|---|---|---|
| ShiftLibraryValidationService | ShiftTemplateBreakBandRepository | band load → `covers()` | ✓ WIRED |
| ShiftTemplateService | ShiftTemplateBreakBand | band persistence | ✓ WIRED |
| ShiftLibraryGenerationService | ShiftLibraryValidationService | `covers()` reuse (D-02, one implementation) | ✓ WIRED — tool reported a false negative on a regex-escaping mismatch (`shiftLibraryValidationService\\.` vs literal `.`); I confirmed the call directly at `ShiftLibraryGenerationService.java:331,349,385` |
| ShiftLibraryValidationController | ShiftLibraryGenerationService | `GET .../shift-library/suggestion` | ✓ WIRED |
| solverConfig.xml | AgentShiftAssignment | second `<entityClass>` + scoped CH phase | ✓ WIRED |
| SolverService | AgentShiftAssignment | `buildShiftAssignments` | ✓ WIRED |
| ScheduleConstraintProvider | ShiftBandPair | `shiftEnvelopeCompliance` join + `covers()` | ✓ WIRED |
| ShiftEnvelopeGroundTruthTest | solverConfig.xml | `createFromXmlResource` (real config, not hand-built) | ✓ WIRED |
| ShiftLibrary.tsx | api/client.ts | suggestion request, band-carrying bodies | ✓ WIRED |
| ScheduleConstraintProvider | ScheduleConfig | mode filter | ✓ WIRED |
| SolverService | ShiftLibraryValidationService | `runPreSolveValidation` capacity-shortfall reuse | ✓ WIRED |
| ScheduleService | AgentShiftAssignment | accept-time denormalisation | ✓ WIRED (but see CR-02 gap — the *inferred mode* derived from this link can be wrong in an edge case) |
| ScheduleOutputService | ScheduleDetailResponse | `buildAgentSchedule` populates `entry.shift` | ✓ WIRED |
| ScheduleResults.tsx | api/client.ts | `schedulingMode` + `entry.shift` partition | ✓ WIRED (consuming a value that can itself be wrong per CR-02) |
| ShiftModelBenchmarkTest | 15-BENCHMARK.md | numbers transcribed verbatim | ✓ WIRED — independently reproduced, byte-for-byte match |

18/18 evaluated key links wired (counting the one tool false-negative as confirmed-wired by manual
inspection).

### Behavioral Spot-Checks (run directly, not trusted from SUMMARY/BENCHMARK docs)

| Behavior | Command | Result | Status |
|---|---|---|---|
| Solver actually builds with two `@PlanningEntity` classes (XCUT-03) | `./gradlew test --tests com.wfm.solver.SolverConfigBuildTest` | `tests="1" failures="0" errors="0"` | ✓ PASS |
| Ground-truth walker agrees with reported score, including 6 adversarial corruption cases (ENVL-02/03/07) | `./gradlew test --tests com.wfm.solver.ShiftEnvelopeGroundTruthTest` | `tests="7" failures="0" errors="0"` | ✓ PASS |
| XCUT-04 benchmark reproduces the numbers transcribed into 15-BENCHMARK.md | `./gradlew test --tests com.wfm.solver.ShiftModelBenchmarkTest -Dwfm.benchmark=true` | `tests="2" failures="0" errors="0"`; per-seed hard/soft scores match the committed table exactly (e.g. slot seed 1: `0/-128`; slot seed 2: `-5140/-149`; every shift-arm seed: `0/-192`) | ✓ PASS |
| solverConfig.xml carries the committed shifts-first ordering, both CH phases with `id`+`entityClass` | Direct file read | Confirmed: `shiftEntitySelector` then `seatEntitySelector`, matching D-08's shipped decision | ✓ PASS |
| REQUIREMENTS.md threshold-commit-before-run claim (XCUT-04 process integrity) | `git log --follow -p -- 15-BENCHMARK.md` | `cd26db93` (threshold + placeholder Results) precedes `836033a`/`57ad2a7` (filled-in numbers) by ~20 minutes, same session | ✓ PASS |

### Code Review Findings — independently re-verified, not merely cited

`15-REVIEW.md` reported 3 Critical + 1 Warning. I independently re-derived CR-01 and CR-02 by
reading the referenced source files myself (not trusting the review's prose) and confirm both are
real, reachable defects — promoted to formal gaps above (tied to ENVL-01 and
ENVL-10/XCUT-01 respectively).

CR-03 (`ScheduleService.deleteSchedule` never deletes `AgentShiftAssignment` rows, orphaning them —
confirmed: `AgentShiftAssignmentRepository` exposes no `deleteBy*` method at all, and
`agent_shift_assignment.schedule_id` carries no FK, confirmed by reading `V41__agent_shift_
assignment.sql` directly) and WR-01 (one unscoped repository delete method, not currently
exploitable) are both real but are **not** promoted to formal gaps here: no plan's must-have
truths assert delete-path cleanup, and the defect is a data-hygiene orphan (unreachable rows) — not
user-visible incorrect data, unlike CR-01/CR-02. They should still be fixed; recommend a follow-up
task alongside the ENVL-01/ENVL-10 gap closure since all three touch the same
`AgentShiftAssignment` lifecycle code paths.

### Requirements Coverage

| Requirement | Source Plan(s) | Status | Evidence |
|---|---|---|---|
| ENVL-01 | 15-03 | ⚠️ Partial (mechanism verified, library-scoping gap) | See gaps |
| ENVL-02 | 15-03, 15-04 | ✓ SATISFIED | Ran directly |
| ENVL-03 | 15-03, 15-04 | ✓ SATISFIED | Ran directly |
| ENVL-04 | 15-06 | ✓ SATISFIED | `ShiftModeBreakGatingTest` |
| ENVL-05 | 15-06 | ✓ SATISFIED | `ShiftModeBreakGatingTest` |
| ENVL-06 | 15-03, 15-04 | ✓ SATISFIED | `solverConfig.xml`, benchmark log |
| ENVL-07 | 15-04 | ✓ SATISFIED | Ran directly |
| ENVL-08 | 15-01, 15-05, 15-06 | ✓ SATISFIED | `AgentShiftAssignment`, `BandCapacityConstraintTest` |
| ENVL-09 | 15-06 | ✓ SATISFIED | `breakClustering` real body |
| ENVL-10 | 15-07 | ⚠️ Partial (grouping verified, mode-label gap) | See gaps |
| SHLB-07 | 15-01, 15-02, 15-05 | ✓ SATISFIED | Confirmed call site directly |
| XCUT-01 | 15-05, 15-07 | ⚠️ Partial | Same CR-02 gap as ENVL-10 |
| XCUT-03 | 15-03 | ✓ SATISFIED (REQUIREMENTS.md checkbox stale) | Ran directly |
| XCUT-04 | 15-08 | ✓ SATISFIED | Independently reproduced |
| XCUT-05 | 15-06 | ✓ SATISFIED | Zero `OPEN_RESOLVE_IN_PHASE_15` rows |

No orphaned requirements: all 15 requirement IDs declared across the 8 plans' `requirements:`
frontmatter (ENVL-01…10, SHLB-07, XCUT-01, XCUT-03, XCUT-04, XCUT-05) are accounted for above, and
match the phase requirement IDs given for this verification exactly.

### Human Verification Required

None. Every truth resolved to VERIFIED or FAILED by direct code reading and test execution — no
item required visual/UX judgment beyond what the frontend grep-and-read checks already covered
(the frontend has no test framework, per standing project decision, but the specific rendering
logic — mode branch, group partition, draft badge copy — was read directly and matches the
must-haves verbatim).

### Gaps Summary

Two genuine, previously-undetected defects survive from `15-REVIEW.md`'s CR-01 and CR-02, both
confirmed by my own independent reading of the referenced source (not merely cited from the
review), and both concentrated in the same area: date/state handling around the
`AgentShiftAssignment` entity that is new in this phase.

1. **CR-01 (ENVL-01):** `SolverService.startSolve` filters out retired shift templates but not
   upcoming ones. An operator who creates a shift template with a future `effectiveFrom` — an
   explicitly supported, UI-surfaced workflow (`ShiftLibrary.tsx`'s "Upcoming" badge,
   `ShiftTemplateController.eraStatus`) — will find it immediately assignable in any current solve,
   contradicting the milestone's own SHLB-03 guarantee and the literal wording of ENVL-01
   ("from that desk's library"). The correct predicate (`ShiftLibraryValidationService.
   withinEffectiveRange`) already exists in the codebase and is unused by the solve path.

2. **CR-02 (ENVL-10/XCUT-01):** Accepted-schedule `schedulingMode` is inferred from whether any
   `agent_shift_assignment` rows exist, but the accept path only writes rows with a non-null
   `shiftBandPair`. A SHIFT-mode solve accepted with zero placed shifts (stopped early, or a
   library that matches no agent's hours) is permanently mislabeled SLOT on every future load,
   silently switching the Agent Allocation view to the wrong rendering branch.

Neither defect calls into question the **core, hardest-to-get-right claim** of this phase — that
the shift-envelope hard-constraint coupling is structurally sound (Option A, not the rejected
Option C) and that this soundness is independently proven, not merely reported by the solver's own
score. That claim was re-verified directly by me, by running the ground-truth walker myself, and
holds. The gaps are narrower: both are edge-case-adjacent (an UPCOMING-template solve; a
zero-shift SHIFT-mode accept) but both are genuinely reachable through ordinary, UI-encouraged
operator workflows, uncovered by any existing test, and worth a small, targeted closure plan before
this phase is considered fully done. `scheduling_mode` still defaults to `SLOT` on every desk, so
neither gap blocks piloting readiness in the way a coupling-soundness failure would have — but both
should be fixed, since XCUT-01/SHLB-03 are exactly the kind of "written value doesn't reach every
surface correctly" defect this project has already been burned by twice (v1.2 audits I-1, I-2, and
now this).

---

_Verified: 2026-08-27T12:05:31Z_
_Verifier: Claude (gsd-verifier)_
