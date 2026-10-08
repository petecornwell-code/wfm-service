---
phase: 20-solver-business-date-correctness
plan: 05
subsystem: solver
tags: [timefold, constraint-streams, business-date, midnight-anchor, tagged-collector, performance-investigation]

# Dependency graph
requires:
  - phase: 20-02
    provides: "BusinessDateJoinGuardTest's new-and-unlisted work order (12 un-migrated key positions) and its three documented blind spots -- the shared DATE lambda plus seven groupBy consumers, ShiftLibraryGenerationService:180, StaffingRequirementService:172-175"
  - phase: 20-03
    provides: "ConstraintMatchCountNonVacuityTest's anchor-invariance assertion and its independently-derived differing-constraint list; PhilUsShapedDriftGuardTest's 48-agent 00:00-anchored no-drift baseline"
  - phase: 20-04
    provides: "SlotModeOvernightContractedHoursTest's RED forward assertion and TodaysBehaviour pin; the seat-supply gate fix; the five-fixture businessDate precedent this plan's deviation list extends to 18 more files"
provides:
  - "Every join, equal and groupBy key position in ScheduleConstraintProvider that reads a timeslot's date now reads the business date: one shared lambda feeding six groupBy consumers (honourPreferredBreakTime no longer among them, see key-decisions), plus seven standalone equal/join call sites"
  - "Every interval anchor in ScheduleConstraintProvider reads the desk's real day-start anchor through exactly one private helper (resolveAnchor), replacing the PENDING_DESK_ANCHOR placeholder and its allowlist row"
  - "honourPreferredBreakTime's tagged-collector anchor mechanism (checkpoint decision), proven not to move its own match count by the unmoved PhilUsShapedDriftGuardTest baseline"
  - "A conclusive, evidence-based finding that a catastrophic (600x+) performance regression during this plan's own verification was caused by pre-existing test-fixture gaps (null Timeslot.businessDate), not by the join/anchor migration or the tagged-collector mechanism -- backed by controlled A/B isolation, not inference"
affects: [20-06-shift-library-and-coverage-migration, 20-07-staffing-requirement-migration, 20-08-day-start-generation-reachability]

# Actuals (#2632)
actuals:
  tokens: 15009
  tasks: 2
  commits: 1
  plan_head_before: 25103234da9879e83d33397b58dbab9fa5d29fd1

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A single private anchor-resolution helper (resolveAnchor(LocalTime)) replacing a hardcoded midnight placeholder -- the ONE place in the file that binds a DayWindow, so the file cannot hold two anchor rules that drift apart"
    - "Tagged-collector mechanism: join a schedule-wide singleton BEFORE a groupBy and fold a derived scalar into the groupBy's own collector (ConstraintCollectors.compose + a private record), so a downstream join can still reach the desired arity without the fact needing to survive the grouping as a tuple member"
    - "A ConstraintCollectors#min(BiFunction, Comparator) overload used specifically for its Comparator.nullsFirst() null-tolerance -- the natural-ordering min(BiFunction) overload's internal TreeMap throws on a null mapped value"
    - "Moving a mode-gate filter from an ifExists-after-grouping shape to a plain filter-before-grouping shape is behaviour-preserving when the gated fact is a schedule-wide singleton (ScheduleConfig, @ProblemFactProperty) -- the filter outcome cannot vary by row"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
    - src/test/resources/midnight-time-arithmetic.md
    - src/test/resources/midnight-boundary-scenarios.md
    - src/test/java/com/wfm/solver/SlotModeOvernightContractedHoursTest.java
    - src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java
    - src/test/java/com/wfm/solver/BreakAwareConstructionTest.java
    - src/test/java/com/wfm/solver/FullScale150AgentTest.java
    - src/test/java/com/wfm/solver/IncrementalScoringDiagnosticTest.java
    - src/test/java/com/wfm/solver/MultiDayConstraintDiagnosticTest.java
    - src/test/java/com/wfm/solver/NinetyAgent12HourTest.java
    - src/test/java/com/wfm/solver/NinetyFiveAgentReproTest.java
    - src/test/java/com/wfm/solver/NonWorkingDaySeatConstraintTest.java
    - src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java
    - src/test/java/com/wfm/solver/SingleDaySolvableTest.java
    - src/test/java/com/wfm/solver/SolverQualityGuardTest.java
    - src/test/java/com/wfm/solver/TwelveHourUniformDemandTest.java
    - src/test/java/com/wfm/solver/BreakClusteringConstraintTest.java
    - src/test/java/com/wfm/solver/DeskAnchorReachesConstraintTest.java
    - src/test/java/com/wfm/solver/ShiftEnvelopeComplianceConstraintTest.java
    - src/test/java/com/wfm/solver/ShiftModeBreakGeometryGuardTest.java
    - src/test/java/com/wfm/solver/ShiftWorkContiguityConstraintTest.java
    - src/test/java/com/wfm/solver/ShiftModeBreakGatingTest.java
    - src/test/java/com/wfm/solver/UsualShiftConsistencyBenchmarkTest.java
    - src/test/java/com/wfm/service/UsualShiftWritePathTest.java

key-decisions:
  - "Task 1 checkpoint: tagged-collector chosen over lead-from-agent-day-config for honourPreferredBreakTime's anchor mechanism. Both options lose the node sharing with six sibling constraints equally (a scoring-performance cost, not a correctness one), so that was not the deciding factor. The deciding factor was match-count safety BY CONSTRUCTION: tagged-collector keeps the grouping keys exactly as they are -- agent id and business date -- so group identity (and this constraint's match count) cannot move, which matters because two already-committed baselines (ConstraintMatchCountNonVacuityTest, PhilUsShapedDriftGuardTest) pin that count and the plan forbids editing either to accommodate a move. lead-from-agent-day-config would have changed the group identity to the agent-day-config object, risking exactly that forced-halt scenario on the riskiest commit of the phase. Implementation detail decided after the checkpoint (not itself re-litigating it): the 'schedule configuration' joined before the grouping is ScheduleConfig (a cheap cross join against a @ProblemFactProperty singleton), not AgentDayConfig -- joining AgentDayConfig there would have DROPPED every agent-day lacking a matching AgentDayConfig row from the grouping entirely, which is exactly the match-count-moving risk the checkpoint existed to avoid. ScheduleConfig is schedule-wide and never absent, so this is a correctness-neutral join. Verified unmoved: PhilUsShapedDriftGuardTest (48-agent, 00:00-anchored) passed unedited after the migration, and its literal table is one of the two baselines that pin this constraint's count."
  - "Consequence of the tagged-collector decision, not separately chosen: honourPreferredBreakTime no longer shares the file's AGENT_ID/DATE/TO_LIST grouping node with exactlyOneBreak/breakDuration/breakBlockedWindow/breakStartAlignment. Its own groupBy now reads the business date directly ((a, cfg) -> a.getTimeslot().getBusinessDate()), so the shared DATE lambda's edit now feeds SIX groupBy consumers, not seven as the plan's own prose stated -- re-measured and reported here per this phase's established re-grep practice, not silently absorbed."
  - "ConstraintCollectors#min(BiFunction, Comparator) used with Comparator.nullsFirst(Comparator.naturalOrder()), not the simpler min(BiFunction) overload. Schedule.dayStart carries no MIDNIGHT default (by design, per its own javadoc) -- every pre-BDAY-04 Schedule and every test fixture that never calls setDayStart has a genuinely null ScheduleConfig.dayStart(). The natural-ordering min(BiFunction) overload's internal TreeMap throws NullPointerException on a null mapped value; the null is intentionally allowed to flow through to resolveAnchor's own null-coalescing fallback instead, matching the rest of the file's pattern exactly."
  - "The severe (600x+) performance regression discovered during this plan's own verification was root-caused to pre-existing test-fixture gaps, NOT to the tagged-collector mechanism or to any per-call stream cost -- see the dedicated investigation section below for the full evidence chain."
  - "MidnightBoundaryScenarioRegistryTest's EXPECTED_REGISTRY_SIZE corrected 4 -> 3 (Rule 3, blocking): removing SlotModeOvernightContractedHoursTest's TodaysBehaviour nested class and its registry entry in this same commit (required by the registry's own rule) left the constant pointing at a count the registry could never reach again, failing registryHoldsExactlyFourEntries. Renamed to registryHoldsExactlyTheExpectedEntryCount for clarity."

patterns-established:
  - "A controlled A/B isolation (stash the production diff, keep the test-only diagnostic edit, measure; restore) is how this phase distinguishes 'my production change is slow' from 'a test fixture silently broke' -- cheaper and more conclusive than reading thread dumps alone."

requirements-completed: [SOLV-01, SOLV-02, SOLV-03, SOLV-04, SOLV-06, BDAY-07]

# Coverage metadata (#1602)
coverage:
  - id: D1
    description: "Every business-date-relevant key position in ScheduleConstraintProvider (shared DATE lambda + seven standalone equal/join sites) migrated to Timeslot.getBusinessDate(); the four AgentShiftAssignment-side do-not-edit sites confirmed byte-identical"
    requirement: "SOLV-01"
    verification:
      - kind: unit
        ref: "com.wfm.solver.MidnightBoundaryRegressionTest$NonMidnightAnchor (3 methods)"
        status: pass
      - kind: other
        ref: "grep -c 'getTimeslot().getBusinessDate()' ScheduleConstraintProvider.java -> 8 (>= 6 required)"
        status: pass
      - kind: other
        ref: "direct code review: lines 307,366,400,450,707,733 reference the shared DATE constant by identity, inheriting its line-100 definition; lines 678,916,972,1028 (bandCapacity/usualShiftConsistency/shiftStartMix/preferredStartShiftMode) confirmed byte-identical, still reading sa.getDate() on an AgentShiftAssignment-led stream"
        status: pass
    human_judgment: false
  - id: D2
    description: "Every interval anchor reads the desk's real day-start anchor through one private resolveAnchor helper; the PENDING_DESK_ANCHOR placeholder and its allowlist row are gone"
    requirement: "SOLV-03"
    verification:
      - kind: unit
        ref: "com.wfm.service.MidnightTimeArithmeticGuardTest, com.wfm.solver.MidnightBoundaryRegressionTest$NonMidnightAnchor"
        status: pass
      - kind: other
        ref: "grep -c 'PENDING_DESK_ANCHOR' -> 0; grep -c 'anchoredAt' -> 1"
        status: pass
    human_judgment: false
  - id: D3
    description: "honourPreferredBreakTime's tagged-collector anchor mechanism (checkpoint decision), implemented without moving its own match count"
    requirement: "SOLV-06"
    verification:
      - kind: unit
        ref: "com.wfm.solver.PhilUsShapedDriftGuardTest (48-agent, 00:00-anchored, literals unedited)"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.ConstraintMatchCountNonVacuityTest (anchor-invariance assertion)"
        status: pass
    human_judgment: false
  - id: D4
    description: "SOLV-04's SLOT-mode cross-midnight stretch now attributes to a single business day; the TodaysBehaviour pin and its registry entry are removed in this same commit"
    requirement: "SOLV-04"
    verification:
      - kind: unit
        ref: "com.wfm.solver.SlotModeOvernightContractedHoursTest#businessDateJoinAttributesTheCrossMidnightStretchToASingleBusinessDayEach"
        status: pass
      - kind: unit
        ref: "com.wfm.support.MidnightBoundaryScenarioRegistryTest (6 tests, EXPECTED_REGISTRY_SIZE=3)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Root-cause investigation of a 600x+ performance regression discovered during this plan's own verification, concluding the tagged-collector mechanism and the join migration are innocent, and the cause is a pre-existing, now-fixed, 18-file test-fixture gap"
    verification:
      - kind: other
        ref: "A/B isolation: BreakAwareConstructionTest at STEPS_SMALL=50, 2510323 baseline 0.283s vs migrated-unfixed killed at >180s (never finished); migrated-with-fixture-fix 0.273s"
        status: pass
      - kind: other
        ref: "./gradlew test (unfiltered, full suite, clean environment): 1173 tests, 1 failed (BusinessDateJoinGuardTest's expected-red), 0 errors, 10m55s -- matching the historical ~11-minute baseline"
        status: pass
    human_judgment: true
    rationale: "The conclusion that the tagged-collector mechanism is not responsible rests on a negative result (reverting the DATE lambda alone did not fix the hang) combined with a positive one (fixing the fixture did) -- a human should confirm this reasoning is sound before treating the investigation as closed, since no single automated assertion proves a mechanism's innocence."

duration: 3h 10min
completed: 2026-10-01
status: complete
---

# Phase 20 Plan 5: The Business-Date Join and Anchor Migration Summary

**Every join, equal, groupBy and interval anchor in ScheduleConstraintProvider now reads the business date and the desk's real day-start anchor in one commit; a 600x+ performance regression discovered during verification was root-caused by controlled A/B isolation to pre-existing test-fixture gaps (null `Timeslot.businessDate`), not to the migration or the tagged-collector mechanism — 18 fixtures fixed, full suite back to its historical ~11-minute baseline.**

## Performance

- **Duration:** 3h 10min (includes the performance-regression investigation)
- **Started:** 2026-10-01T22:10:00Z (approx, continuation from Task 1's resolved checkpoint)
- **Completed:** 2026-10-01T23:20:00Z (approx)
- **Tasks:** 2 (Task 1: checkpoint decision, resolved before this continuation; Task 2: the migration)
- **Files modified:** 24 (1 production, 23 test/test-resource)

## Accomplishments

- **The eight key-position edits (SOLV-01).** The shared `DATE` lambda (line 99-100) now reads `a.getTimeslot().getBusinessDate()`, feeding six `groupBy(AGENT_ID, DATE, ...)` consumers (`exactlyOneBreak`, `breakDuration`, `breakBlockedWindow`, `breakStartAlignment`, `contractedHoursOver`, `contractedHoursUnder` — not seven, see Decisions). Seven standalone `equal()`/`join()` sites re-point the `Timeslot` side: `agentDayOff`, `agentNotWorkingThatDay`, `shiftEnvelopeCompliance`, `shiftWorkContiguity`, `contractedHoursUnderZero`, `honourPreferredStartTime`, `breakClustering`'s on-break sub-stream. The four `AgentShiftAssignment`-side `getDate()` sites (`bandCapacity`, `usualShiftConsistency`, `shiftStartMix`, `preferredStartShiftMode`) are confirmed byte-identical by direct review.
- **The nineteen anchor occurrences (SOLV-03, D-07).** A single private `resolveAnchor(LocalTime)` helper is now the ONLY place in the file that binds a `DayWindow`, replacing `PENDING_DESK_ANCHOR` (constant and its allowlist row, both gone). The five Quad-arity break constraints and `shiftWorkContiguity` read the anchor from the already-joined `AgentDayConfig` (via a new `anchorFor(AgentShiftAssignment)` helper for the latter) at zero extra join cost.
- **honourPreferredBreakTime's tagged-collector mechanism (Task 1's checkpoint decision, implemented).** `ScheduleConfig` is joined and its mode-gate filter applied BEFORE the grouping (behaviour-preserving: the gate is schedule-wide); the grouping's own collector is `compose(toList(...), min(dayStartOf, nullsFirst), AnchoredAssignments::new)`, folding the anchor into the group result so the subsequent `AgentPreference` join still lands at Quad. `PhilUsShapedDriftGuardTest`'s unedited literal table confirms this constraint's match count did not move.
- **SlotModeOvernightContractedHoursTest's forward assertions are green**; its `TodaysBehaviour` nested class and `midnight-boundary-scenarios.md`'s matching registry entry are removed in this same commit.
- **A severe performance regression was found, root-caused, and resolved — not by weakening the plan's forbidden levers (no test deleted or weakened, no baseline edited, no step-budget raised), but by fixing the actual, unrelated defect.** See the dedicated section below.

## Task Commits

1. **Task 1: checkpoint:decision — anchor mechanism for honourPreferredBreakTime** — resolved by the user before this continuation (no commit; see Context)
2. **Task 2: The one deliberate pass — eight key-position edits, nineteen anchor occurrences, one commit, plus 18 test-fixture fixes discovered during verification** - `c0ceb71` (feat)

**Plan metadata:** pending (this commit)

## The performance-regression investigation (read before touching this file's moves/weights)

**What was observed.** The plan's mandatory unfiltered wave-close gate hung: a first attempt was killed by the orchestrator after 54 minutes (never finished, stuck in `BreakAwareConstructionTest`'s construction-heuristic/local-search phase). Historical baseline for the full 1174-test suite is ~11 minutes, three runs in a row, per this file's own `runSolver` javadoc.

**What it was NOT.** Direct, controlled A/B isolation (git-stash the production diff, keep a temporary reduced-step-count diagnostic in the test file only, measure, then restore) proved the tagged-collector mechanism innocent:
- Reverting ONLY the shared `DATE` lambda (feeding six OTHER constraints, never `honourPreferredBreakTime`) back to `getDate()` did NOT fix the hang — still unfinished past 180s at `STEPS_SMALL=50`.
- A single from-scratch `SolutionManager.explain()` call on the same fixture took 20ms — the SCORE CALCULATION itself was never slow. The cost was specific to the incremental local-search path.

**What it WAS.** `BreakAwareConstructionTest`'s `Timeslot` factory never called `setBusinessDate(...)` — the identical gap class plan 20-04 already found and fixed five times, in a file this migration had no reason to re-discover until its OWN much larger join surface (14 logical joins vs. 20-04's one) made the gap live here too. With `businessDate` null, `contractedHoursUnderZero`'s and `agentNotWorkingThatDay`'s `ifNotExists` joins can never match a real `AgentDayConfig`/date, so once any seat is assigned both constraints fire UNCONDITIONALLY — an unfixable hard-score floor no move can ever reduce. The local search burns its entire step budget against a floor it cannot lower, which is what manifested as "slow": not a more expensive stream, a permanently wrong answer.

**Proof, not inference:** adding `ts.setBusinessDate(date)` to the one affected factory method, with NOTHING else changed, took the identical `STEPS_SMALL=50` test from >180s (killed, unfinished) to 0.273s — matching the 2510323 baseline's 0.283s for the same test. **Ratio: at 50 steps, 2510323 = 0.283s, migrated-unfixed = >180s (did not finish) = at least 636x slower; migrated-fixed = 0.273s, matching baseline.**

**Scope of the fix.** Auditing every `new Timeslot()` site in `src/test/java` for a matching `setBusinessDate` call (same audit shape as plan 20-04's), 18 more pre-existing files were found with the identical gap AND confirmed to exercise a migrated constraint (via `ConstraintVerifier.verifyThat` on a migrated method, a full `.solve()`, or `SolutionManager` on a full `Schedule`): `BreakAwareConstructionTest`, `FullScale150AgentTest`, `IncrementalScoringDiagnosticTest`, `MultiDayConstraintDiagnosticTest`, `NinetyAgent12HourTest`, `NinetyFiveAgentReproTest`, `NonWorkingDaySeatConstraintTest`, `ShiftEnvelopeGroundTruthTest`, `SingleDaySolvableTest`, `SolverQualityGuardTest` (dead-code path, fixed for consistency), `TwelveHourUniformDemandTest`, `BreakClusteringConstraintTest`, `DeskAnchorReachesConstraintTest`, `ShiftEnvelopeComplianceConstraintTest`, `ShiftModeBreakGeometryGuardTest`, `ShiftWorkContiguityConstraintTest`, `ShiftModeBreakGatingTest`, `UsualShiftConsistencyBenchmarkTest`, `com.wfm.service.UsualShiftWritePathTest` (this one independently confirmed failing in the very first full-suite attempt). Each fixture is implicitly 00:00-anchored (no `dayStart` concept anywhere in the file), so `businessDate == date` is correct and behaviourally inert — the identical reasoning plan 20-04 used for its five fixes. `DeskAnchorReachesConstraintTest` is the one case checked for a non-midnight anchor specifically (it tests a 21:00-anchored desk): its single `Timeslot` is always `23:00-00:00`, on-or-after both the 21:00 and 00:00 anchors it exercises, so `businessDate = calendarDate` is correct under both.

**Files NOT touched, and why.** `MidnightGapScanTest` tests `ScheduleConstraintProvider`'s private static helpers directly (`getGapLengths` etc.), which read only `getStartTime()`/`getEndTime()`, never a date — unaffected by construction. `BulkUnderallocationSoftConstraintTest`, `MinimumStaffingConstraintTest`, `ZeroDemandTimeslotCeilingTest` each `ConstraintVerifier.verifyThat` only a constraint this migration does not touch (`bulkUnderallocationSoft`, `minimumStaffing`, `bulkOverallocationLimit`/`bulkUnderallocationHard`) — `ConstraintVerifier` scores only the named method, so an unrelated fixture gap there cannot matter. Several `com.wfm.service` tests (`MinimumStaffingSeatsTest`, `StaffingRequirementErlangTest`, `ScheduleConsistencyRepairServiceTest`, `ScheduleEnvelopeRepairServiceTest`, `ShiftModeMinimumStaffingSeatSupplyTest`, `ShiftStartMixTargetServiceTest`, `ScheduleOutputServiceShiftReportingTest`) never invoke `ScheduleConstraintProvider`'s scoring at all.

**Verification that the fix is complete and the suite is healthy, in a clean environment (no competing processes from this investigation's own earlier kill/retry cycles):**
```
1173 tests completed, 1 failed, 4 skipped
BUILD FAILED in 10m 55s
```
The single failure is `BusinessDateJoinGuardTest`'s set-equality assertion (`allFourTargetFilesExist`/the headline test), naming exactly the three service-layer sites plan 20-06 owns (`ScheduleOutputService` x2, `ShiftLibraryGenerationService` x1) — **this is the designed RED state**, explicit in both this plan's own acceptance criteria ("`BusinessDateJoinGuardTest`'s set-equality test is still red — the three service-layer files are migrated in plans 20-06 and 20-07") and `ROADMAP.md`'s stated closure point. Not touched, not allowlisted. 10m55s matches the historical ~11-minute baseline — the regression is fully resolved, not merely masked.

**Direct code review verdict on the two sites 20-02's `BusinessDateJoinGuardTest` cannot see (its own documented blind spot — its green does NOT prove these, by its own design):** the shared `DATE` lambda at line 99-100 reads `a.getTimeslot().getBusinessDate()`, confirmed by direct reading; its six `groupBy(AGENT_ID, DATE, ...)` consumers at lines 307, 366, 400, 450, 707, 733 reference the `DATE` constant by Java identity (not a re-derivation), so none of them can disagree with the lambda's own definition — verified structurally, not merely by count. (`honourPreferredBreakTime`, line ~1109 in the pre-migration tree, is no longer among these six consumers — see Decisions Made.)

**MultiDayConstraintDiagnosticTest's TreeSet activity (raised as a Phase 21 concern) — checked, not closed.** This file never calls `setSchedulingMode(SchedulingMode.SHIFT)` (confirmed: `grep -c "setSchedulingMode(SchedulingMode.SHIFT)"` → 0), so `shiftWorkContiguity` — the one constraint with the midnight-wraparound-hazardous `TreeSet<LocalTime>` in `countNonBreakHoles`, per plan 20-01's own documented deferral — is structurally inert here (SLOT mode). The TreeSet activity an earlier thread dump caught is almost certainly `getGapLengths`/`findBreakStart`'s `TreeSet<LocalTime>` (used by the SLOT-mode break constraints), operating on ordinary same-day times since every fixture this plan touches is implicitly 00:00-anchored — no non-midnight anchor was introduced anywhere by this plan's fixes. In isolation this test completes in 3m1s and passes; **I did not independently confirm whether 3m1s is this specific test's historical pre-migration duration** (no prior timing baseline was available to compare against) — it is not hung, not failing, and not using a SHIFT-mode/midnight-crossing path, but I am recording the open edge rather than asserting it is definitely unchanged.

**Production-safety note (phase-level finding, not a defect in this commit).** After this migration, a null `Timeslot.businessDate` degrades SILENTLY into a permanent, unfixable hard-constraint penalty (not an exception) for any schedule holding one. Production itself is safe: `V53`'s migration backfills every existing row and sets the column `NOT NULL` in the same migration, and `BusinessDateWritePathGuardTest` (BDAY-02) structurally proves exactly two classes (`TimeslotGeneratorService`, `ScheduleService`) ever call `setBusinessDate`, both always deriving or propagating a real value — this is the same structural guarantee plan 20-04's SUMMARY already documented for its own fix. Recorded here because this plan's own investigation is the first time the SILENT-DEGRADATION failure mode (vs. an exception) was directly observed and measured, not merely argued.

## `resolveAnchor`'s null-coalescing fallback — which case it serves, and why it cannot be the other one

`resolveAnchor(LocalTime dayStart)` returns `DayWindow.anchoredAt(dayStart != null ? dayStart : LocalTime.MIDNIGHT)`. Plan 20-01's recorded prohibition requires this fallback serve ONLY "a schedule that has no anchor source at all," never "a convenience default" that could silently score an anchored desk at midnight. Verified by direct code review, not asserted:

**When `dayStart` is actually null at a `resolveAnchor`/`anchorFor` call site:** only when a `Schedule` object was constructed by hand (in a test) and `setDayStart(...)` was never called on it, or an `AgentShiftAssignment`'s `@Transient dayConfig` field was never populated. `Schedule.dayStart` has no Java-side default (`Schedule.java`'s own field — confirmed no initializer) and the class's own `getScheduleConfig()` javadoc states it deliberately "exposes null rather than a silently substituted midnight." That is the literal "no anchor source at all" case: the object was never given one.

**Why this cannot fire for a schedule whose desk HAS an anchor (production or DB-loaded):**
- `desk.day_start` is `TIME NOT NULL DEFAULT '00:00'` at the schema level (`V53__add_desk_day_start_and_timeslot_business_date.sql`), and `Desk.dayStart`'s Java field independently defaults to `LocalTime.MIDNIGHT` (`Desk.java:37`) — a `Desk` can never be null or absent an anchor, whether loaded from the DB or freshly constructed.
- `schedule.day_start` is likewise `TIME NOT NULL DEFAULT '00:00'` (`V54__add_schedule_day_start.sql`).
- `new ScheduleConfig(...)` is constructed in exactly ONE place in `src/main/java`: `Schedule.getScheduleConfig()` (`Schedule.java:343`), which passes `this.dayStart` straight through — never a fallback at that call site.
- `new AgentDayConfig(...)` is constructed in exactly ONE place in `src/main/java`: `SolverService.computeAgentDayConfigs` (`SolverService.java:856`), which always passes `schedule.getDayStart()` explicitly (confirmed by reading the call, not inferred).
- The SOLE production path that builds the `Schedule` object the solver scores, `SolverService.buildSchedule` (`SolverService.java:655`), unconditionally copies `desk.getDayStart()` onto it — so a solver-scored, real schedule's `dayStart` traces back to the `NOT NULL DEFAULT` desk column every time, never to an unset field.
- Corroborating evidence from the REST of the codebase: every other production caller of `schedule.getScheduleConfig().dayStart()` (`ScheduleOutputService.java:147,305,614`, `ScheduleEnvelopeRepairService.java:117`, `SolverService.java:1240`) calls `DayWindow.anchoredAt(...)` on it DIRECTLY, with NO null-coalescing at all — the rest of this codebase already trusts this value unconditionally on every production path.

So the fallback fires only for a hand-built-in-Java test fixture that supplied no anchor at all (the pre-BDAY-04-shaped majority of this test suite, which predates `setDayStart` existing), never for a desk that has one — production and any DB-loaded schedule are structurally excluded from the null branch by the schema's own `NOT NULL DEFAULT` plus the two single production construction sites both tracing back to it. This is the identical, already-reviewed reasoning Phase 19/BDAY-04 recorded for the two pre-existing inline copies of this exact pattern (`shiftEnvelopeCompliance`, `breakClustering`'s on-break sub-stream) — this commit centralizes it into one helper and extends it to the newly-migrated sites, it does not introduce a new risk. For the five Quad-arity break constraints and `shiftWorkContiguity`, the prior behaviour was `PENDING_DESK_ANCHOR` — an UNCONDITIONAL midnight anchor on every call, 100% of the time — so this commit strictly narrows the silent-midnight surface at those sites (now only when no anchor source exists at all) rather than introducing it.

## Files Created/Modified

- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — the migration itself: 8 key-position edits, 19 anchor-occurrence replacements, `resolveAnchor`/`anchorFor` helpers, `honourPreferredBreakTime`'s tagged-collector restructuring, `AnchoredAssignments` record
- `src/test/resources/midnight-time-arithmetic.md` — `PENDING_DESK_ANCHOR`'s allowlist row and prose bullet removed
- `src/test/resources/midnight-boundary-scenarios.md` — SOLV-04's table row and registry entry removed
- `src/test/java/com/wfm/solver/SlotModeOvernightContractedHoursTest.java` — `TodaysBehaviour` nested class and its import removed
- `src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java` — `EXPECTED_REGISTRY_SIZE` 4→3 (Rule 3)
- 18 test fixture files — `Timeslot.setBusinessDate(...)` added alongside the existing `setDate(...)` call (Rule 1; see investigation section above for the full list and per-file reasoning)

## Decisions Made

See `key-decisions` in the frontmatter for the full, precise wording. Summary: (1) tagged-collector chosen at the checkpoint for match-count safety by construction, not performance; (2) the tagged-collector joins `ScheduleConfig` (not `AgentDayConfig`) specifically to avoid the match-count-moving risk the checkpoint existed to prevent; (3) this ends `honourPreferredBreakTime`'s shared grouping node, dropping the shared-lambda's downstream consumer count from seven to six — re-measured and reported as plan-narrative drift, not silently absorbed; (4) `min(BiFunction, Comparator.nullsFirst(...))` used over the simpler overload because `Schedule.dayStart` has no default and a null must flow through to `resolveAnchor`, not throw; (5) `MidnightBoundaryScenarioRegistryTest`'s expected-size constant corrected to match the registry's own post-migration shape.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] 18 pre-existing test fixtures never called `Timeslot#setBusinessDate`, causing a 600x+ local-search performance collapse once this migration made their constraints business-date-sensitive**
- **Found during:** Task 2's mandatory unfiltered wave-close gate (first attempt killed at 54 minutes by the orchestrator; investigated via controlled A/B isolation per the dedicated section above)
- **Issue:** Each fixture's `Timeslot` factory called `setDate(...)` but never `setBusinessDate(...)`, leaving the field null. Harmless before this migration (nothing read it); after, `contractedHoursUnderZero` and `agentNotWorkingThatDay`'s business-date `ifNotExists` joins could never match, firing unconditionally once seats exist and handing local search a permanently-unfixable hard-score floor.
- **Fix:** Added `X.setBusinessDate(date)` immediately after the existing `X.setDate(date)` call in each fixture's `Timeslot` factory, matching plan 20-04's identical, already-reviewed pattern. Each fixture is implicitly 00:00-anchored, so this is behaviourally inert — confirmed for the one anchor-varying case (`DeskAnchorReachesConstraintTest`) by checking the specific clock position used.
- **Files modified:** listed in Files Created/Modified above (18 files)
- **Verification:** Each affected file re-run individually in a clean environment (daemons stopped, no stray processes) — all pass, all complete in well under a minute (`BreakAwareConstructionTest`'s full 3-method class: 1m34s; the rest: under 60s each, most under 2s). Full unfiltered suite: 1173 tests, 1 expected failure, 0 errors, 10m55s.
- **Committed in:** `c0ceb71` (same commit as the migration itself — these fixture fixes are what makes the migration's own mandatory wave-close gate pass, so they belong to the same deliberate pass, not a follow-up)

**2. [Rule 3 - Blocking] `MidnightBoundaryScenarioRegistryTest`'s `EXPECTED_REGISTRY_SIZE` constant pointed at a count the registry could never hold again**
- **Found during:** Task 2, running the plan's own required registry-removal step
- **Issue:** Removing `SlotModeOvernightContractedHoursTest.TodaysBehaviour` and its registry entry (required by the manifest's own rule, in this same commit) left the registry at 3 entries against a hardcoded expectation of 4.
- **Fix:** Corrected the constant to 3, matching the post-migration registry shape; renamed the asserting test method for clarity (`registryHoldsExactlyTheExpectedEntryCount`).
- **Files modified:** `src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.support.MidnightBoundaryScenarioRegistryTest"` passes (6/6).
- **Committed in:** `c0ceb71`

---

**Total deviations:** 2 auto-fixed (1 Rule 1 bug spanning 18 files, 1 Rule 3 blocking fix).
**Impact on plan:** Both were necessary for the plan's own mandatory wave-close gate to mean what it claims. Neither changed any committed baseline, any match count, any score, or any production behaviour on a 00:00-anchored desk — `PhilUsShapedDriftGuardTest`'s and `ConstraintMatchCountNonVacuityTest`'s literals are unedited and pass. No test was deleted or weakened; no step budget was raised in the committed state (temporary diagnostic edits used during the investigation were reverted via `git checkout --` before committing, confirmed by a zero-diff check against HEAD before the fix was reapplied properly).

## Issues Encountered

The performance-regression investigation (detailed above) was the dominant cost of this plan. No blockers remain; no unresolved ambiguity requiring a further checkpoint.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

**Ready for plan 20-06** (`ScheduleOutputService`'s six coverage-grouping keys and `ShiftLibraryGenerationService`'s four library-generation key positions). `BusinessDateJoinGuardTest`'s remaining new-and-unlisted set, re-measured against the committed tree, contains ZERO `ScheduleConstraintProvider` entries — every item plan 20-02 listed against that file is closed. The guard's one remaining failure names exactly plan 20-06's three sites (two in `ScheduleOutputService`, one in `ShiftLibraryGenerationService`) and nothing else — the designed signal for this wave boundary.

**Both independently-derived work orders this commit closes:**
- The join guard's (plan 20-02) new-and-unlisted set for `ScheduleConstraintProvider`: all 12 entries closed (confirmed by re-running `BusinessDateJoinGuardTest` and reading its failure message, which now names only `ScheduleOutputService`/`ShiftLibraryGenerationService` sites).
- The anchor-invariance assertion's (plan 20-03) differing-constraint list: both pairs (coverage, band-flush) now pass — `ConstraintMatchCountNonVacuityTest`'s two anchor-invariance tests are green.

No blockers.

---
*Phase: 20-solver-business-date-correctness*
*Completed: 2026-10-01*

## Self-Check: PASSED

- FOUND: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java (modified)
- FOUND: src/test/resources/midnight-time-arithmetic.md (modified)
- FOUND: src/test/resources/midnight-boundary-scenarios.md (modified)
- FOUND: src/test/java/com/wfm/solver/SlotModeOvernightContractedHoursTest.java (modified)
- FOUND: src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java (modified)
- FOUND: all 18 test-fixture files listed above (modified)
- FOUND: commit c0ceb71
- Acceptance criteria re-verified against the committed tree: `grep -c 'PENDING_DESK_ANCHOR'` → 0; `grep -c 'anchoredAt'` → 1; `grep -c 'getTimeslot().getBusinessDate()'` → 8 (≥ 6 required); `grep -c 'ScheduleConstraintProvider'` in `midnight-time-arithmetic.md` → 2 (see note below); fenced "Permitted midnight anchors" block → 3 rows.
- **Known plan-narrative discrepancy, reported not silently fixed:** the plan's verify step for `midnight-time-arithmetic.md` states "exactly one mention may remain" (prints ≤ 1), but the raw-comparison section has TWO pre-existing, legitimate mentions of `ScheduleConstraintProvider` (the fenced-block entry and its own prose-bullet explanation, both unrelated to anchors, both present before this plan touched the file — confirmed via `git show HEAD:...` showing 4 total before this commit, 2 of which were the anchor-section entries this commit removed). The actual intent (placeholder's row and bullet gone) is satisfied; the raw count floor in the plan's own verify text undercounts by one, the same class of plan-vs-tree drift this phase repeatedly documents.
- Plan-level `<verification>` re-run: `./gradlew compileJava compileTestJava` → BUILD SUCCESSFUL; `./gradlew test --tests "com.wfm.solver.MidnightBoundaryRegressionTest" --tests "com.wfm.solver.ConstraintMatchCountNonVacuityTest" --tests "com.wfm.solver.SlotModeOvernightContractedHoursTest"` → BUILD SUCCESSFUL; `./gradlew test --tests "com.wfm.solver.PhilUsShapedDriftGuardTest"` → BUILD SUCCESSFUL; `./gradlew test --tests "com.wfm.service.MidnightTimeArithmeticGuardTest" --tests "com.wfm.support.MidnightBoundaryScenarioRegistryTest" --tests "com.wfm.solver.ScheduleConstraintClassificationTest"` → BUILD SUCCESSFUL; unfiltered `./gradlew test` (clean environment, single run) → 1173 tests, 1 failed (`BusinessDateJoinGuardTest`, expected), 0 errors, 10m55s.
