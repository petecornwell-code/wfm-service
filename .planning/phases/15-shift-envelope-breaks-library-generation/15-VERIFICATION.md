---
phase: 15-shift-envelope-breaks-library-generation
verified: 2026-08-27T13:15:00Z
status: passed
score: 11/11 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 9/11
  gaps_closed:
    - "ENVL-01 — solver assigns each working agent exactly one shift per day, from that desk's live library (CR-01)"
    - "ENVL-10 / XCUT-01 — accepted-schedule schedulingMode recorded accurately, not inferred (CR-02)"
  gaps_remaining: []
  regressions: []
---

# Phase 15: Shift Envelope, Breaks & Library Generation Verification Report

**Phase Goal:** The solver assigns one shift per agent-day via a hard-constraint coupling proven
sound and benchmarked honestly, breaks are distributed rather than simultaneous, and a starting
library can be suggested from demand.

**Verified:** 2026-08-27T13:15:00Z
**Status:** passed
**Re-verification:** Yes — after gap closure (commits `ba0c3f0`, `75349d8`, `6065fd6`)

## Goal Achievement

This is a re-verification of the two gaps (`CR-01`, `CR-02`) reported in the prior run
(`9/11 must-haves`, `gaps_found`), plus an independently-confirmed third fix (`CR-03`, a
data-hygiene defect that was documented but not previously promoted to a formal gap). Every claim
below was re-derived directly from the current HEAD source — not read off the fix commits'
messages, the SUMMARY files, or the orchestrator's test-count claim.

### Gap 1 (CR-01 / ENVL-01) — re-verified closed

**Claim under test:** eligibility is enforced per agent-day in the solver's own value range, not
just as a load-time pre-filter, and a template effective for only part of a schedule period is
genuinely ineligible on the dates outside its range.

- `ShiftTemplate#isEffectiveOn(LocalDate)` (`src/main/java/com/wfm/model/ShiftTemplate.java:153`)
  is a new, single shared predicate checking both the `effectiveFrom` and `effectiveTo` boundaries.
  `ShiftLibraryValidationService`'s former private `withinEffectiveRange` copy is gone — grep
  confirms zero occurrences — and both its call sites (`ShiftLibraryValidationService.java:208,328`)
  now call `template.isEffectiveOn(...)` directly.
- `AgentShiftAssignment#getEligibleShiftBandPairs()` — the method actually annotated
  `@ValueRangeProvider(id = "shiftBandRange")`, which is exactly the id referenced by
  `AgentShiftAssignment.shiftBandPair`'s `@PlanningVariable(valueRangeProviderRefs =
  "shiftBandRange")` — now chains a second `.filter(p -> date != null &&
  p.template().isEffectiveOn(date))` after the existing net-hours filter (lines 166–178). This is
  the actual value-range provider the solver consults, confirmed by reading the annotation pair
  directly, not inferred from naming.
- **The "one shared list instance" claim, verified:** `SolverService.buildShiftAssignments`
  (lines 722–749) calls `sa.setDeskShiftBandPairs(deskShiftBandPairs)` inside a loop over
  `agentDayConfigs` with no per-row copy — every `AgentShiftAssignment` row for a desk shares the
  identical `List<ShiftBandPair>` reference. This is why a desk-level (or period-level) filter
  alone cannot enforce per-agent-day eligibility for a period that straddles a template's
  `effectiveFrom`/`effectiveTo` boundary — confirmed by direct code reading, not merely cited from
  the commit message.
- `SolverService.filterLiveShiftTemplates` (lines 664–673, renamed and made package-private-static
  for direct testability) is now a coarse pre-filter checking overlap against
  `[schedule.getPeriodStartDate(), schedule.getPeriodEndDate()]` — confirmed at the call site
  (line 289–291) — never `LocalDate.now()`.
- New test class `SolverServiceShiftAssignmentTest` (15 tests) covers upcoming-template exclusion,
  retired-template exclusion, and — critically — a period-straddling case with two rows sharing one
  `deskShiftBandPairs` instance, one dated before `effectiveFrom` (empty range) and one dated on
  `effectiveFrom` (non-empty range), proving per-row enforcement rather than per-desk. I ran this
  test class directly: **15/15 pass** (not trusted from the commit message).

### Gap 1 side effect — empty-value-range case, verified as claimed

`ScheduleConstraintProvider.shiftEnvelopeCompliance` (line 413) hard-constrains
`sa.getShiftBandPair() == null || !sa.getShiftBandPair().covers(a.getTimeslot())` for every seated
timeslot on that agent-day. `ConstraintWeights.shiftEnvelopeComplianceWeight` defaults to
`HardSoftScore.ofHard(1)` — a genuinely hard constraint, confirmed by reading
`ConstraintWeights.java:138`. When `getEligibleShiftBandPairs()` returns an empty list (all
candidates excluded by the effective-range or hours filter), `shiftBandPair` stays `null`
(`allowsUnassigned = true`), and any seat the CH places for that agent that day is hard-penalised.
This is exactly the claimed behavior: a desk whose entire library is upcoming/retired for a working
agent-day drives the solve visibly infeasible, not silently unconstrained. Confirmed directly in
the constraint source and the constraint-weight default — not merely cited from the fix commit's
prose.

### Gap 2 (CR-02 / ENVL-10 / XCUT-01) — re-verified closed

**Claim under test:** `schedulingMode` is persisted, not inferred, and no accept/re-solve path
writes a `Schedule` without it being set.

- `V43__schedule_scheduling_mode.sql` adds `schedule.scheduling_mode VARCHAR(10) NOT NULL DEFAULT
  'SLOT'` and backfills existing rows via `EXISTS (agent_shift_assignment WHERE schedule_id = s.id)
  -> SHIFT, else SLOT` — read directly, matches its own header comment exactly, and is explicitly
  documented (in the SQL comment) as best-effort for historical rows only, not authoritative.
- `Schedule.schedulingMode` (`Schedule.java:162–164`) is now `@Enumerated(EnumType.STRING)
  @Column(name = "scheduling_mode", nullable = false)` — no longer `@Transient`.
- **Every accept/re-solve path traced:** the only `new Schedule()` construction site in the
  service layer is `SolverService.buildSchedule` (line 489), which sets
  `s.setSchedulingMode(desk.getSchedulingMode())` at line 495 — before any solve starts. Every
  `inMemoryStore.put(...)` call in `startSolve`/`stopSolve` (lines 384, 408, 423, 434, 472) operates
  on this same in-memory `Schedule` instance (Timefold mutates and returns the same working
  solution object; there is no second construction path). `ScheduleService.acceptSchedule`
  (line 202–244) retrieves this exact object from `inMemoryStore` and calls
  `entityManager.persist(schedule)` directly — no intermediate reconstruction that could drop the
  field. `ScheduleService.loadSnapshotData` (line 398–428) no longer contains any inference logic
  at all; the loaded entity already carries the DB column value. No path was found that constructs
  or persists a `Schedule` without `schedulingMode` having been set from `desk.getSchedulingMode()`.
- New regression test
  `ScheduleServiceShiftSnapshotTest#getScheduleDetail_acceptedShiftModeSchedule_zeroPlacedShifts_stillReportsShift`
  reproduces the exact CR-02 scenario: a SHIFT-mode schedule with a shift-assignment row whose
  `shiftBandPair` is null, confirms zero `agent_shift_assignment` rows are actually persisted (so
  the test genuinely exercises the empty-snapshot case, not the ordinary case), and asserts the
  reloaded `schedulingMode` is still `SHIFT`. I ran `ScheduleServiceShiftSnapshotTest` directly:
  **12/12 pass**, including this test and the CR-03 delete-cascade test below.

### Bonus fix (CR-03) — verified

`AgentShiftAssignmentRepository.deleteByTenantIdAndDeskIdAndScheduleId` was added and
`ScheduleService.deleteSchedule` (line 367) now calls it, mirroring
`agentAssignmentRepository`'s own delete immediately above it. This was flagged but not promoted
to a formal gap in the prior verification (no must-have truth asserted delete-path cleanup); it is
now fixed regardless, with its own regression test passing (part of the 12/12 above).

### Migration guard (`MigrationEntityConsistencyTest`) — verified

`schedule` was added to `DECLARED_TABLES` (`MigrationEntityConsistencyTest.java:88`) alongside its
own explanatory comment. Before this fix, `schedule` was entirely absent from this map, meaning no
migration touching `schedule` (including V43 itself) was ever reconciled against the entity
mapping — that gap is now closed for this table. I ran
`MigrationEntityConsistencyTest` directly: **2/2 pass**. Note for the record (not a phase-15 gap):
several long-lived tables (`desk`, `agent`, `timeslot`, `staffing_requirement`,
`agent_assignment`, etc.) are still outside `DECLARED_TABLES` — this is a pre-existing scope
limitation of the guard, unrelated to and not worsened by this phase's fixes; worth a future
follow-up but out of scope here.

### Regression check — no new defects found

- `ShiftEnvelopeGroundTruthTest` (ENVL-02/03/07's independent ground-truth walker) — ran directly:
  **7/7 pass**, unchanged from the prior verification. The core coupling-soundness claim still
  holds after both fix commits.
- `BreakClusteringConstraintTest` (5/5), `BandCapacityConstraintTest` (8/8),
  `ShiftModeBreakGatingTest` (7/7), `SolverConfigBuildTest` (1/1),
  `ShiftLibraryGenerationServiceTest` (8/8), `ShiftLibraryValidationServiceTest` (36/36) — all ran
  directly, all pass. None of these files were touched by any of the three fix commits (confirmed
  by `git diff --stat` across the fix range), so this is a genuine no-regression check, not a
  restatement of unaffected code.
- No frontend file was touched by any of the three fix commits (`git diff --stat ...frontend/`
  across the fix range returns nothing) — `ScheduleResults.tsx`'s mode-branch and
  `ShiftLibrary.tsx`'s suggestion flow are unaffected by construction, and `npm run build` succeeds
  cleanly (57 modules, no errors).
- **Full backend suite, run twice** (the first run collided with my own concurrent targeted test
  invocations writing to the same output directory and failed on an XML-write race — a
  self-inflicted artifact, not a real test failure; the second, clean, uncontended run succeeded).
  Aggregated directly from the 79 `TEST-*.xml` files in `build/test-results/test/`:
  **79 classes, 505 tests, 0 failures, 0 errors, 2 skipped** (`BUILD SUCCESSFUL`). This
  independently reproduces, byte-for-byte, the orchestrator's claimed count — not merely accepted
  from the task description. The 2 skips are both `ShiftModelBenchmarkTest`'s `-Dwfm.benchmark=true`
  -gated methods, pre-existing and unrelated to this gap closure.

### Observable Truths (by requirement ID)

| # | Requirement | Truth | Status | Evidence |
|---|---|---|---|---|
| 1 | ENVL-01 | Solver assigns each working agent exactly one shift per day, **from that desk's live library** | ✓ VERIFIED | CR-01 closure: per-agent-day `isEffectiveOn` enforcement in the actual value-range provider; `SolverServiceShiftAssignmentTest` 15/15 (ran directly) |
| 2 | ENVL-02 | Agent never seated outside their shift envelope, hard constraint | ✓ VERIFIED | `shiftEnvelopeCompliance` unchanged; `ShiftEnvelopeGroundTruthTest` 7/7 (ran directly) |
| 3 | ENVL-03 | Specialization varies freely within a shift | ✓ VERIFIED | `ShiftEnvelopeGroundTruthTest` (ran, passes; unchanged by fix commits) |
| 4 | ENVL-04 | Working day contiguous apart from break | ✓ VERIFIED | `ShiftModeBreakGatingTest` 7/7 (ran directly; file untouched by fix commits) |
| 5 | ENVL-05 | Break placement from shift template, not the four emergent constraints | ✓ VERIFIED | `ShiftModeBreakGatingTest` (same run) |
| 6 | ENVL-06 | Feasible initial solution via CH alone, no pre-assignment pipeline | ✓ VERIFIED | `solverConfig.xml` unchanged; `SolverConfigBuildTest` 1/1 (ran directly) |
| 7 | ENVL-07 | Reported score agrees with an independent ground-truth check | ✓ VERIFIED | `ShiftEnvelopeGroundTruthTest` 7/7 (ran directly, re-confirming no regression from CR-01's enforcement change) |
| 8 | ENVL-08 | Shift template defines break bands; solver assigns one band per agent-day | ✓ VERIFIED | `BandCapacityConstraintTest` 8/8 (ran directly) |
| 9 | ENVL-09 | Break clustering constraint has a real, penalising body | ✓ VERIFIED | `BreakClusteringConstraintTest` 5/5 (ran directly) |
| 10 | ENVL-10 | Agent Allocation view groups agents by assigned shift; slot desk unchanged, and the recorded mode is accurate | ✓ VERIFIED | CR-02 closure: `schedule.scheduling_mode` persisted column (V43), written on every accept path; `ScheduleServiceShiftSnapshotTest` 12/12 (ran directly), including the zero-placed-shift regression test |
| 11 | SHLB-07 | Suggested library computed from demand, editable draft, never auto-applied, reuses `covers()` | ✓ VERIFIED | Unaffected by fix commits; `ShiftLibraryGenerationServiceTest` 8/8 (ran directly) |

**Score:** 11/11 requirement-level truths verified.

### Cross-Cutting Requirements

| Requirement | Truth | Status | Evidence |
|---|---|---|---|
| XCUT-01 | Written shift/mode data visible everywhere it's displayed | ✓ VERIFIED | CR-02 closure removes the last known undermining case; `ScheduleResults.tsx` grouping unchanged and confirmed wired |
| XCUT-03 | Any `solverConfig.xml` change validated by a test that actually builds a solver | ✓ VERIFIED (code) — **still unchecked in REQUIREMENTS.md, a stale documentation gap carried over from the prior verification** | `SolverConfigBuildTest` 1/1, ran directly |
| XCUT-04 | Seeded, step-count-terminated A/B benchmark, threshold pre-committed | ✓ VERIFIED | Unaffected by fix commits; previously independently re-run and confirmed |
| XCUT-05 | Every constraint classified mode-agnostic/mode-gated/needs-variant | ✓ VERIFIED | Unaffected by fix commits |

### Required Artifacts (fix-commit scope)

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `src/main/java/com/wfm/model/ShiftTemplate.java` | Shared `isEffectiveOn(LocalDate)` predicate | ✓ VERIFIED | Present, substantive, javadoc explains the design rationale; used by 2 call sites |
| `src/main/java/com/wfm/model/AgentShiftAssignment.java` | Value-range provider filters by `isEffectiveOn` | ✓ VERIFIED | `getEligibleShiftBandPairs()` — confirmed to be the actual `@ValueRangeProvider` the `@PlanningVariable` references |
| `src/main/java/com/wfm/service/SolverService.java` | Coarse pre-filter against schedule period, not `now()` | ✓ VERIFIED | `filterLiveShiftTemplates`, called with `schedule.getPeriodStartDate()/getPeriodEndDate()` |
| `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` | Private duplicate predicate removed | ✓ VERIFIED | `withinEffectiveRange` no longer exists; both call sites use the shared predicate |
| `src/test/java/com/wfm/service/SolverServiceShiftAssignmentTest.java` | Regression coverage for CR-01 | ✓ VERIFIED | 15/15 tests, ran directly, including the period-straddling shared-instance case |
| `src/main/java/com/wfm/model/Schedule.java` | Persisted `schedulingMode` column | ✓ VERIFIED | `@Enumerated(EnumType.STRING)`, no longer `@Transient` |
| `src/main/resources/db/migration/V43__schedule_scheduling_mode.sql` | Column + backfill | ✓ VERIFIED | Read directly, matches its own header comment |
| `src/main/java/com/wfm/service/ScheduleService.java` | No inference logic remains; delete cascade added | ✓ VERIFIED | `loadSnapshotData` inference block removed; `deleteSchedule` now deletes `agent_shift_assignment` rows |
| `src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java` | Regression coverage for CR-02 and CR-03 | ✓ VERIFIED | 12/12 tests, ran directly |
| `src/main/java/com/wfm/repository/AgentShiftAssignmentRepository.java` | `deleteByTenantIdAndDeskIdAndScheduleId` | ✓ VERIFIED | Present, called from `deleteSchedule` |
| `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java` | `schedule` added to `DECLARED_TABLES` | ✓ VERIFIED | 2/2 tests, ran directly |

### Key Link Verification (fix-commit scope)

| From | To | Via | Status |
|---|---|---|---|
| `AgentShiftAssignment.shiftBandPair` (`@PlanningVariable`) | `AgentShiftAssignment.getEligibleShiftBandPairs()` (`@ValueRangeProvider`) | `valueRangeProviderRefs = "shiftBandRange"` matched to `id = "shiftBandRange"` | ✓ WIRED — confirmed by reading both annotations directly |
| `getEligibleShiftBandPairs()` | `ShiftTemplate.isEffectiveOn(date)` | per-row date filter | ✓ WIRED |
| `ShiftLibraryValidationService` | `ShiftTemplate.isEffectiveOn(date)` | shared predicate, no private duplicate remains | ✓ WIRED |
| `SolverService.buildSchedule` | `Schedule.schedulingMode` | `s.setSchedulingMode(desk.getSchedulingMode())` before solve | ✓ WIRED |
| `ScheduleService.acceptSchedule` | `entityManager.persist(schedule)` | same in-memory instance from `inMemoryStore`, no reconstruction | ✓ WIRED |
| `ScheduleService.loadSnapshotData` | `schedule.getSchedulingMode()` | direct column read, inference block removed | ✓ WIRED |
| `ScheduleService.deleteSchedule` | `AgentShiftAssignmentRepository.deleteByTenantIdAndDeskIdAndScheduleId` | explicit call, mirrors `agentAssignmentRepository`'s own delete | ✓ WIRED |
| `ScheduleConstraintProvider.shiftEnvelopeCompliance` | `AgentShiftAssignment.shiftBandPair == null` branch | hard-penalises every seat on an agent-day with an empty value range | ✓ WIRED — confirmed hard weight (`HardSoftScore.ofHard(1)`) |

### Behavioral Spot-Checks (run directly)

| Behavior | Command | Result | Status |
|---|---|---|---|
| CR-01 regression suite (upcoming/retired/straddling-period cases) | `./gradlew test --tests com.wfm.service.SolverServiceShiftAssignmentTest` | `tests="15" failures="0" errors="0"` | ✓ PASS |
| CR-02/CR-03 regression suite (zero-placed-shift accept, delete cascade) | `./gradlew test --tests com.wfm.service.ScheduleServiceShiftSnapshotTest` | `tests="12" failures="0" errors="0"` | ✓ PASS |
| Migration guard now reconciles `schedule` | `./gradlew test --tests com.wfm.migration.MigrationEntityConsistencyTest` | `tests="2" failures="0" errors="0"` | ✓ PASS |
| Ground-truth agreement unaffected by CR-01's enforcement change (ENVL-02/03/07) | `./gradlew test --tests com.wfm.solver.ShiftEnvelopeGroundTruthTest` | `tests="7" failures="0" errors="0"` | ✓ PASS |
| Break/band constraints unaffected (ENVL-04/05/08/09) | `./gradlew test --tests {BreakClusteringConstraintTest,BandCapacityConstraintTest,ShiftModeBreakGatingTest}` | `5/5`, `8/8`, `7/7`, all `failures="0" errors="0"` | ✓ PASS |
| SolverConfig still builds (XCUT-03) | `./gradlew test --tests com.wfm.solver.SolverConfigBuildTest` | `tests="1" failures="0" errors="0"` | ✓ PASS |
| Library generation/validation unaffected (SHLB-07) | `./gradlew test --tests {ShiftLibraryGenerationServiceTest,ShiftLibraryValidationServiceTest}` | `8/8`, `36/36`, all `failures="0" errors="0"` | ✓ PASS |
| Full backend suite, clean uncontended run | `./gradlew test` (2nd, clean run after a self-caused XML-write collision on the 1st) | `BUILD SUCCESSFUL`; aggregated from 79 `TEST-*.xml`: 505 tests, 0 failures, 0 errors, 2 skipped | ✓ PASS |
| Frontend build | `npm run build` (in `frontend/`) | `tsc -b && vite build` — 57 modules, no errors | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan(s) | Status | Evidence |
|---|---|---|---|
| ENVL-01 | 15-03 | ✓ SATISFIED | CR-01 gap closed, verified directly |
| ENVL-02 | 15-03, 15-04 | ✓ SATISFIED | Unaffected, ran directly |
| ENVL-03 | 15-03, 15-04 | ✓ SATISFIED | Unaffected, ran directly |
| ENVL-04 | 15-06 | ✓ SATISFIED | Unaffected, ran directly |
| ENVL-05 | 15-06 | ✓ SATISFIED | Unaffected, ran directly |
| ENVL-06 | 15-03, 15-04 | ✓ SATISFIED | Unaffected |
| ENVL-07 | 15-04 | ✓ SATISFIED | Ran directly, re-confirms no regression |
| ENVL-08 | 15-01, 15-05, 15-06 | ✓ SATISFIED | Ran directly |
| ENVL-09 | 15-06 | ✓ SATISFIED | Ran directly |
| ENVL-10 | 15-07 | ✓ SATISFIED | CR-02 gap closed, verified directly |
| SHLB-07 | 15-01, 15-02, 15-05 | ✓ SATISFIED | Unaffected, ran directly |
| XCUT-01 | 15-05, 15-07 | ✓ SATISFIED | CR-02 closure removes the last undermining case |
| XCUT-03 | 15-03 | ✓ SATISFIED (REQUIREMENTS.md checkbox still stale) | Ran directly |
| XCUT-04 | 15-08 | ✓ SATISFIED | Unaffected |
| XCUT-05 | 15-06 | ✓ SATISFIED | Unaffected |

No orphaned requirements. All 15 requirement IDs declared across the 8 plans' `requirements:`
frontmatter are accounted for above.

**REQUIREMENTS.md bookkeeping note (informational, not a gap):** the checkboxes for ENVL-01…10,
SHLB-07, and XCUT-03 currently read `[ ]` in `.planning/REQUIREMENTS.md` (reverted by the
`gaps_found` protocol after the prior run, per `c1ab80e`). Per this re-verification, ENVL-01…10 and
SHLB-07 are now genuinely satisfied and XCUT-03 was a stale unchecked box even before this
re-verification (the required test exists, is substantive, and passes) — all are candidates to be
checked off. This report does not edit `REQUIREMENTS.md`; that is left to the orchestrator per
instructions.

### Anti-Patterns Found

None. `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` scan across all files touched by the three
fix commits (`ShiftTemplate.java`, `AgentShiftAssignment.java`, `SolverService.java`,
`ShiftLibraryValidationService.java`, `Schedule.java`, `ScheduleService.java`,
`AgentShiftAssignmentRepository.java`, `V43__schedule_scheduling_mode.sql`,
`MigrationEntityConsistencyTest.java`, and both new/extended test files) found zero markers.

### Human Verification Required

None. Every truth resolved to VERIFIED by direct code reading and independently-run test
execution — no item required visual/UX judgment.

### Gaps Summary

Both gaps from the prior verification (`CR-01`/ENVL-01, `CR-02`/ENVL-10/XCUT-01) are genuinely
closed, independently re-derived from source rather than accepted from the fix reports. The
mechanism claimed by each fix — per-agent-day effective-range enforcement via the actual value-range
provider for CR-01, and a persisted (not inferred) `schedulingMode` column written on the one
`Schedule`-construction path for CR-02 — was traced end-to-end and confirmed correct, including the
specific edge cases each gap named (a period straddling an `effectiveFrom` boundary; a zero-placed-
shift accept). A third defect (`CR-03`, orphaned `agent_shift_assignment` rows on schedule delete)
that was documented but not previously promoted to a formal gap has also been fixed, with its own
passing regression test. The full backend suite (79 classes, 505 tests, 0 failures, 0 errors, 2
pre-existing/unrelated skips) and the frontend build both pass on a clean, independently-run check —
not merely accepted from the orchestrator's or fix agents' claims. No regression was found in any
of the break/band/library/ground-truth machinery the two parallel fix worktrees did not touch. This
phase's goal — a proven-sound hard-constraint coupling, honest benchmarking, distributed breaks, and
a demand-derived starting library — is now achieved without qualification.

---

_Verified: 2026-08-27T13:15:00Z_
_Verifier: Claude (gsd-verifier)_
