---
phase: 20-solver-business-date-correctness
plan: 04
subsystem: solver
tags: [timefold, constraint-streams, business-date, seat-supply-gate, midnight-boundary, test-fixture]

# Dependency graph
requires:
  - phase: 20-01
    provides: "AgentDayConfig.dayStart Quad-arity carrier and the 21:00-anchored MidnightBoundaryFixture scenario-building pattern (local factory helpers, DayWindow.businessDateOf derivation) this plan's new test class copies rather than widens"
provides:
  - "SlotModeOvernightContractedHoursTest -- SOLV-04's constructed proof that a cross-midnight SLOT-mode stretch is mis-attributed by today's calendar-date join and correctly attributed to a single business day after plan 20-05's migration"
  - "requireShiftEnvelopeSeatSupply's two maps re-keyed to one key system (SOLV-05) -- a solvable 21:00-anchored desk is no longer falsely refused, a genuinely short one still is, a 00:00-anchored desk is byte-identical"
  - "A documented, schema-verified structural guarantee that Timeslot.businessDate is never null in production (V53's NOT NULL constraint + BusinessDateWritePathGuardTest's single-writer guard), closing the question the gate's new NullPointerException-on-null-key behaviour raised"
affects: [20-05-join-and-anchor-migration]

# Actuals (#2632)
actuals:
  tokens: 12950
  tasks: 2
  commits: 3
  plan_head_before: fcefe9b6af53424cbbabe19b7103dd1e6402e829

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Local factory helpers copying a shared test fixture's shape rather than widening its private methods, when the new scenario belongs to a different requirement than the fixture's own scenario set (SlotModeOvernightContractedHoursTest vs MidnightBoundaryFixture.ALL_SCENARIOS)"
    - "A shared test fixture's Timeslot factory sets businessDate = date as its own documented invariant (\"this fixture is implicitly 00:00-anchored, where business date equals calendar date by construction\"), now applied identically across five fixtures"

key-files:
  created:
    - src/test/java/com/wfm/solver/SlotModeOvernightContractedHoursTest.java
  modified:
    - src/main/java/com/wfm/service/SolverService.java
    - src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java
    - src/test/java/com/wfm/solver/LiveShapeShiftDeskFixture.java
    - src/test/java/com/wfm/solver/SeatSupplyDistributionAnalysisTest.java
    - src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java
    - src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java
    - src/test/java/com/wfm/solver/ShiftModeFixtures.java
    - src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java
    - src/test/resources/midnight-boundary-scenarios.md

key-decisions:
  - "A null Timeslot.businessDate is NOT reachable in production -- verified against schema and write-path evidence, not assumed. V53's migration adds the column nullable, backfills every existing row from date, then runs ALTER COLUMN business_date SET NOT NULL in the same migration (zero-length nullable window, by the migration's own comment); the entity maps it @Column(nullable = false); and BusinessDateWritePathGuardTest (BDAY-02) structurally proves TimeslotGeneratorService and ScheduleService are the only two classes in src/main/java that ever call setBusinessDate, both always deriving or propagating a real value. The NullPointerException Collectors.groupingBy(Timeslot::getBusinessDate, ...) would throw on a null key is therefore unreachable from any real Timeslot row -- it was reachable only from five test-only fixtures that never called setBusinessDate at all, now fixed."
  - "Five test-only Timeslot factories (LiveShapeShiftDeskFixture, ShiftEnvelopeSupplyInvariantTest, SeatSupplyDistributionAnalysisTest, ShiftDeskEndToEndRegressionTest, ShiftModeFixtures) are all implicitly 00:00-anchored with no dayStart concept, so setBusinessDate(date) in each is correct and behaviourally inert for every existing assertion -- the same invariant SOLV-05's own 00:00-anchor regression case asserts explicitly rather than merely relying on."
  - "MidnightBoundaryScenarioRegistryTest's two-class, exactly-three-entries scan is widened to three classes and exactly four entries (Rule 3, blocking) -- its hardcoded scan would never have seen SlotModeOvernightContractedHoursTest's @AssertsTodaysBehaviour marker, making the plan's own required registry entry structurally undetectable by the two-directional guard it is supposed to satisfy."
  - "computeCapacityWarnings is deliberately left unwidened -- it has no per-date map or date key at all, so it cannot hold a calendar/business mismatch, and SOLV-05's 'reports shortfalls per business day' is discharged by requireShiftEnvelopeSeatSupply's fix, the mechanism that actually blocks a solve. Recorded as a comment at the call site, not merely in this summary."

# SOLV-04 is also declared by plan 20-05 (the migration plan that flips
# SlotModeOvernightContractedHoursTest's forward assertions green) -- the shared-ID gate (#2388)
# correctly defers marking it complete until 20-05 lands too, matching 20-03's own precedent for
# SOLV-06/BDAY-07.
requirements-completed: [SOLV-05]

# Coverage metadata (#1602)
coverage:
  - id: D1
    description: "SlotModeOvernightContractedHoursTest: SOLV-04's constructed SLOT-mode proof -- today's mis-attribution pinned as literals (1 under-allocation match, 3 not-working matches), the correct single-business-day attribution asserted red until plan 20-05, Shift work contiguity asserted zero in both states as documented structural inertness"
    requirement: "SOLV-04"
    verification:
      - kind: unit
        ref: "com.wfm.solver.SlotModeOvernightContractedHoursTest#businessDateJoinAttributesTheCrossMidnightStretchToASingleBusinessDayEach"
        status: fail
      - kind: unit
        ref: "com.wfm.solver.SlotModeOvernightContractedHoursTest#shiftWorkContiguityIsStructurallyInertInSlotMode"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.SlotModeOvernightContractedHoursTest$TodaysBehaviour#todaysCalendarDateJoinMisattributesTheCrossMidnightStretch"
        status: pass
      - kind: unit
        ref: "com.wfm.solver.SlotModeOvernightContractedHoursTest$NoSearchDuringEvaluation#scoringMutatesNoPlanningVariable"
        status: pass
      - kind: unit
        ref: "com.wfm.support.MidnightBoundaryScenarioRegistryTest (6 tests)"
        status: pass
      - kind: other
        ref: "./gradlew test (unfiltered, full suite, after both tasks): 1174 tests, 7 failed, 0 errors"
        status: pass
    human_judgment: true
    rationale: "The forward-assertion method is EXPECTED to fail until plan 20-05 migrates the join -- a human must confirm this is the designed RED state (matching plan 20-01/20-02/20-03's identical pattern), since an automated pass/fail check cannot itself distinguish an intentional red proof from a broken one. The other four verification entries in this same run ARE the automated proof the instrument itself is sound."
  - id: D2
    description: "requireShiftEnvelopeSeatSupply's timeslot map re-keyed to Timeslot::getBusinessDate -- a solvable 21:00-anchored desk is no longer falsely refused, a genuinely short one still is, a 00:00-anchored desk is provably unchanged, and the decision not to widen computeCapacityWarnings is recorded in the source"
    requirement: "SOLV-05"
    verification:
      - kind: unit
        ref: "com.wfm.service.ShiftEnvelopeSupplyGateTest#ninePmAnchor_sufficientSupplyIsNotRefused"
        status: pass
      - kind: unit
        ref: "com.wfm.service.ShiftEnvelopeSupplyGateTest#ninePmAnchor_genuineShortfallIsStillRefused"
        status: pass
      - kind: unit
        ref: "com.wfm.service.ShiftEnvelopeSupplyGateTest#midnightAnchor_businessDateEqualsCalendarDate_shortfallStillRefused"
        status: pass
      - kind: unit
        ref: "com.wfm.service.ShiftEnvelopeSupplyGateTest (19 total, every pre-existing case including literal message-equality cases)"
        status: pass
      - kind: integration
        ref: "./gradlew test --tests com.wfm.solver.ShiftEnvelopeSupplyInvariantTest --tests com.wfm.service.ShiftModeMinimumStaffingSeatSupplyTest --tests com.wfm.service.MinimumStaffingSeatsTest --tests com.wfm.solver.SeatSupplyDistributionAnalysisTest"
        status: pass
      - kind: integration
        ref: "./gradlew test --tests com.wfm.solver.ShiftDeskEndToEndRegressionTest (discovered regression, now green)"
        status: pass
      - kind: other
        ref: "./gradlew test (unfiltered, full suite): 1174 tests, 7 failed, 0 errors, PhilUsShapedDriftGuardTest green"
        status: pass
    human_judgment: false

duration: 66min
completed: 2026-10-01
status: complete
---

# Phase 20 Plan 4: SOLV-04 Constructed Proof and SOLV-05 Seat-Supply Gate Fix Summary

**SOLV-04's cross-midnight SLOT-mode mis-attribution pinned with today's wrong literals (1 under-allocation match, 3 not-working matches) and asserted correct red for plan 20-05; SOLV-05's seat-supply gate re-keyed to business date, closing a false refusal of every re-anchored shift-mode desk while a genuine shortfall still refuses -- verified safe against a schema-backed, write-path-guarded proof that a null business date cannot reach the gate in production.**

## Performance

- **Duration:** 66 min
- **Started:** 2026-10-01T19:38:47Z
- **Completed:** 2026-10-01T20:44:35Z
- **Tasks:** 2 completed
- **Files modified:** 10 (1 created, 9 modified)

## Accomplishments

- `SlotModeOvernightContractedHoursTest` constructs a SLOT-mode, 21:00-anchored, one-agent fixture whose eight timeslots span three calendar dates and two business days. Forward assertions (RED today, GREEN after plan 20-05) assert `Contracted hours (under)`, `Contracted hours (over)` and `Agent not working that day` are each exactly zero once the join resolves business date. A `TodaysBehaviour` nested class pins today's wrong literals (1 under-allocation match, 3 not-working matches) and is marked `@AssertsTodaysBehaviour(flippedBy = "SOLV-04")`, with a matching registry entry in `midnight-boundary-scenarios.md`. `Shift work contiguity` is asserted zero in both states, documented as structural inertness (SHIFT-mode gated, this fixture holds zero shift-assignment rows).
- `requireShiftEnvelopeSeatSupply`'s `timeslotsByDate` map is re-keyed from `Timeslot::getDate` (calendar date) to `Timeslot::getBusinessDate` (business date), matching `rowsByDate`'s existing business-date key. Inline comments state why the two maps must share one key system, that the covered-timeslot filter's date was already correct and deliberately untouched, and that `computeCapacityWarnings` is deliberately left unwidened (no per-date key to mismatch; the gate is what actually blocks a solve).
- `ShiftEnvelopeSupplyGateTest` gains three cases: a 21:00-anchored sufficient-supply case that must NOT throw (RED against the pre-fix tree), a 21:00-anchored genuine-shortfall case that MUST throw and asserts the shortfall figures (closing the false-refusal fix without opening a false acceptance), and an explicit 00:00-anchor regression case asserting business date equals calendar date and the same refusal outcome as its calendar-date-keyed equivalent. All 19 cases in the class pass, including every pre-existing literal message-equality case.

## Task Commits

Each task was committed atomically:

1. **Task 1: SlotModeOvernightContractedHoursTest -- the already-live defect, demonstrated** - `8254554` (test)
2. **Task 2: The seat-supply gate's key-system mismatch -- a false refusal, closed in both directions** - `26bf98a` (fix), `ca0b0db` (fix, Rule 1 follow-up)

**Plan metadata:** pending (this commit)

## Files Created/Modified

- `src/test/java/com/wfm/solver/SlotModeOvernightContractedHoursTest.java` -- SOLV-04's constructed proof
- `src/main/java/com/wfm/service/SolverService.java` -- the gate's one-expression key-system fix, plus three recorded decisions as inline comments
- `src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java` -- three new cases (21:00 sufficient, 21:00 shortfall, 00:00 regression); shared `timeslot()`/`timeslotOnDate()` helpers now set `businessDate`
- `src/test/java/com/wfm/solver/LiveShapeShiftDeskFixture.java` -- Rule 1: `timeslot()` helper now sets `businessDate`
- `src/test/java/com/wfm/solver/SeatSupplyDistributionAnalysisTest.java` -- Rule 1: same fix
- `src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java` -- Rule 1: same fix
- `src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java` -- Rule 1: same fix
- `src/test/java/com/wfm/solver/ShiftModeFixtures.java` -- Rule 1: same fix (the widely-shared fixture `ShiftDeskEndToEndRegressionTest`'s healthy-control case reuses)
- `src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java` -- Rule 3: scan widened to a third class, expected-size bumped 3 -> 4
- `src/test/resources/midnight-boundary-scenarios.md` -- matching table row and registry entry for SOLV-04

## Production safety: is a null `businessDate` reachable at the gate in production?

**No.** Verified against schema and write-path evidence, not assumed:

- **Schema (`V53__add_desk_day_start_and_timeslot_business_date.sql`):** the migration adds `timeslot.business_date` nullable, immediately backfills every existing row (`UPDATE timeslot SET business_date = date`), then runs `ALTER COLUMN business_date SET NOT NULL` -- all three statements in one migration, a deliberately zero-length nullable window per the migration's own comment ("so no reader ever observes an unset row"). The entity (`Timeslot.java:38`) maps it `@Column(name = "business_date", nullable = false)`.
- **Write path (`BusinessDateWritePathGuardTest`, BDAY-02):** a structural, two-directional guard proving the complete set of `src/main/java` classes calling `Timeslot#setBusinessDate` is exactly `{TimeslotGeneratorService, ScheduleService}` -- both always derive or propagate a real value, never a null. A third, undiscovered writer would fail this guard's build, not silently pass a null through.

Together these mean no row in the real `timeslot` table, and no `Timeslot` object built by production code, can carry a null `businessDate`. The `NullPointerException` `Collectors.groupingBy(Timeslot::getBusinessDate, ...)` would throw on a null key is therefore **unreachable from production data** -- it was reachable only from five test-only fixtures (`LiveShapeShiftDeskFixture`, `ShiftEnvelopeSupplyInvariantTest`, `SeatSupplyDistributionAnalysisTest`, `ShiftDeskEndToEndRegressionTest`, `ShiftModeFixtures`) that constructed `Timeslot` rows without ever calling `setBusinessDate` -- all five now fixed as Rule 1 bugs, setting `businessDate = date`, correct and behaviourally inert at their implicit 00:00 anchor. No production code was defensively hardened against a null key, because the schema and the write-path guard already make that null structurally impossible -- adding a silent fallback would have hidden a real future regression (a third writer bypassing the guard) behind a defensive catch, which this codebase's established pattern (refuse loudly, never degrade silently on this gate) argues against.

## Decisions Made

1. **A null `businessDate` is a test-fixture gap, never a production risk** -- see the dedicated section above. This is the deliberate choice between the two options a defensive NPE could imply: fix the fixtures (done, five of them) and point to the structural guarantee that makes the fixtures the only possible source (done, cited above), rather than add unreachable-in-production defensive code to the gate.
2. **`computeCapacityWarnings` stays unwidened.** It sums demand and supply schedule-wide with no per-date map or date key at all, so it cannot hold a calendar/business mismatch; `requireShiftEnvelopeSeatSupply` is the mechanism that actually blocks a solve, and that is where SOLV-05's "reports shortfalls per business day" is discharged. Recorded as a comment at the call site (`SolverService.java`, the `// 9b.` comment), not only in this summary.
3. **The covered-timeslot filter's date was checked and deliberately left alone.** `coveredTimeslotsOnDate`'s `date` parameter is already `rowsByDate`'s business-date key; an inline comment at the per-date loop now states this was verified, not merely inherited, so a later reader does not "fix" it into a calendar date.
4. **`MidnightBoundaryScenarioRegistryTest` widened to a third class (Rule 3, blocking).** Its original two-class, exactly-three-entries scan would never see `SlotModeOvernightContractedHoursTest`'s `@AssertsTodaysBehaviour` marker at all -- the plan's own required registry entry would have been structurally invisible to the two-directional guard meant to enforce it. Fixed by adding the new class to the reflective scan and the expected-size constant to 4, matching `BusinessDateWritePathGuardTest`'s own precedent of a guard that must see every legitimate caller to be meaningful.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `MidnightBoundaryScenarioRegistryTest` only scanned two hardcoded classes and expected exactly 3 registry entries**
- **Found during:** Task 1, running the plan's own `MidnightBoundaryScenarioRegistryTest` verify step
- **Issue:** The plan requires marking `TodaysBehaviour`'s method `@AssertsTodaysBehaviour` and adding a matching registry entry, asserting the registry's two-directional guard would catch a mismatch. The guard's `collectMarkedMethodObjects()` resolved only `com.wfm.solver.MidnightBoundaryRegressionTest` and `com.wfm.service.MidnightBoundaryPropertyTest` by fully-qualified name -- a marker on any third class would never be reflected over, making the plan's own required registry entry permanently invisible to the set-equality check (a STALE entry that could never be matched).
- **Fix:** Added `SlotModeOvernightContractedHoursTest` as a third scanned class and bumped `EXPECTED_REGISTRY_SIZE` from 3 to 4, mirroring the file's own existing two-class pattern.
- **Files modified:** `src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.support.MidnightBoundaryScenarioRegistryTest"` passes (6/6).
- **Committed in:** `8254554` (Task 1 commit)

**2. [Rule 1 - Bug] Five test-only `Timeslot` fixtures never set `businessDate`, NPE'ing inside the fixed gate**
- **Found during:** Task 2, the plan's required sibling-suite verify step and the plan-level unfiltered wave-close gate
- **Issue:** `requireShiftEnvelopeSeatSupply`'s re-keyed `Collectors.groupingBy(Timeslot::getBusinessDate, ...)` throws `NullPointerException: element cannot be mapped to a null key` for any `Timeslot` whose `businessDate` is unset. Five shared or local test fixtures (`LiveShapeShiftDeskFixture`, `ShiftEnvelopeSupplyInvariantTest`, `SeatSupplyDistributionAnalysisTest`, `ShiftDeskEndToEndRegressionTest`, `ShiftModeFixtures`) construct `Timeslot` rows via `setDate(...)` alone, never `setBusinessDate(...)` -- all implicitly 00:00-anchored, predating this plan. The first two were caught by the plan's own required sibling-suite verify step; the latter three (`ShiftDeskEndToEndRegressionTest` directly, `ShiftModeFixtures` as the dependency three of its own failing cases shared) were caught only by the unfiltered full-suite wave-close gate, since they are outside the plan's named sibling-suite list.
- **Fix:** Each fixture's `Timeslot`-construction factory now also calls `ts.setBusinessDate(date)` -- correct and behaviourally inert, since every one of these fixtures is implicitly 00:00-anchored (business date equals calendar date by construction there).
- **Files modified:** `src/test/java/com/wfm/solver/LiveShapeShiftDeskFixture.java`, `src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java`, `src/test/java/com/wfm/solver/SeatSupplyDistributionAnalysisTest.java`, `src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java`, `src/test/java/com/wfm/solver/ShiftModeFixtures.java`
- **Verification:** All four plan-named sibling suites pass; `ShiftDeskEndToEndRegressionTest` passes (3/3); unfiltered `./gradlew test` confirms zero failures outside the four expected RED classes.
- **Committed in:** `26bf98a` (first two fixtures, same commit as the production fix) and `ca0b0db` (the two discovered via the full-suite gate)

---

**Total deviations:** 2 auto-fixed (1 Rule 3 blocking fix, 1 Rule 1 bug spanning 5 fixture files).
**Impact on plan:** Both were necessary for the plan's own required verify steps and its wave-close gate to mean what they claim. Neither changed the production fix's actual behaviour (the key-system re-keying in `SolverService.java` is exactly the one expression the plan specifies) or any constraint/test assertion's expected values -- both are corrections to test-only infrastructure the production change's correctness depends on being able to exercise at all.

## Issues Encountered

**The plan's named sibling-suite list (four classes) did not include every test that exercises `requireShiftEnvelopeSeatSupply`.** `ShiftDeskEndToEndRegressionTest` and the `ShiftModeFixtures` fixture it depends on both call the gate but are outside that list; the gap was closed only by this plan's own mandatory unfiltered wave-close gate, exactly the layered-verification design that gate exists for (a filtered run deletes every other class's JUnit XML, so a stale filtered result could have been mistaken for the authoritative one -- the full unfiltered run was re-confirmed as a single, non-concurrent `./gradlew test` process specifically to rule that out before concluding a class was genuinely green). No blockers; both issues were resolved within this plan.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

**Ready for plan 20-05** (the single migration commit consuming `PENDING_DESK_ANCHOR` and the 12 business-date joins, including `TodaysBehaviour`'s registry entries and `SlotModeOvernightContractedHoursTest`'s forward assertions flipping green). Explicit RED/GREEN state at this plan's close, for Wave 3 to distinguish its own baseline from a regression:

**Expected RED (unaffected by this plan, flip at plan 20-05):**
- `MidnightBoundaryRegressionTest$NonMidnightAnchor` -- 3 failures (plan 20-01)
- `BusinessDateJoinGuardTest` -- 1 failure (plan 20-02)
- `ConstraintMatchCountNonVacuityTest` -- 2 failures (plan 20-03)
- `SlotModeOvernightContractedHoursTest#businessDateJoinAttributesTheCrossMidnightStretchToASingleBusinessDayEach` -- 1 failure (**this plan's own new RED proof, SOLV-04**)

**Green, and must STAY green (this plan's own delivered behaviour, SOLV-05):**
- `ShiftEnvelopeSupplyGateTest` -- all 19 cases, including the three new ones
- `SlotModeOvernightContractedHoursTest#shiftWorkContiguityIsStructurallyInertInSlotMode` and `TodaysBehaviour#todaysCalendarDateJoinMisattributesTheCrossMidnightStretch`
- `PhilUsShapedDriftGuardTest` -- the no-drift baseline, 2/2
- `ShiftEnvelopeSupplyInvariantTest`, `ShiftModeMinimumStaffingSeatSupplyTest`, `MinimumStaffingSeatsTest`, `SeatSupplyDistributionAnalysisTest`, `ShiftDeskEndToEndRegressionTest` -- every sibling seat-supply suite

**Verbatim unfiltered wave-close result (confirmed on a single, non-concurrent `./gradlew test` run):**

```
1174 tests completed, 7 failed, 4 skipped
Failures confined to exactly:
  MidnightBoundaryRegressionTest$NonMidnightAnchor -- 3
  BusinessDateJoinGuardTest -- 1
  ConstraintMatchCountNonVacuityTest -- 2
  SlotModeOvernightContractedHoursTest -- 1
0 errors. No other class failed.
```

No blockers.

---
*Phase: 20-solver-business-date-correctness*
*Completed: 2026-10-01*

## Self-Check: PASSED

- FOUND: src/test/java/com/wfm/solver/SlotModeOvernightContractedHoursTest.java
- FOUND: src/main/java/com/wfm/service/SolverService.java (modified)
- FOUND: src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java (modified)
- FOUND: src/test/java/com/wfm/solver/LiveShapeShiftDeskFixture.java (modified)
- FOUND: src/test/java/com/wfm/solver/SeatSupplyDistributionAnalysisTest.java (modified)
- FOUND: src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java (modified)
- FOUND: src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java (modified)
- FOUND: src/test/java/com/wfm/solver/ShiftModeFixtures.java (modified)
- FOUND: src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java (modified)
- FOUND: src/test/resources/midnight-boundary-scenarios.md (modified)
- FOUND: commit 8254554 (Task 1)
- FOUND: commit 26bf98a (Task 2)
- FOUND: commit ca0b0db (Task 2, Rule 1 follow-up)
- Acceptance criteria re-verified: `grep -c 'DayWindow.businessDateOf'` in the new test class -> 2; `grep -c '\.solve('` -> 0; `grep -c 'groupingBy(Timeslot::getBusinessDate'` in SolverService.java -> 1; `awk` over `computeCapacityWarnings`'s body piped through `grep -c 'LocalDate'` -> 0.
- Plan-level `<verification>` re-run: `./gradlew compileJava compileTestJava` -> BUILD SUCCESSFUL; `./gradlew test --tests "com.wfm.service.ShiftEnvelopeSupplyGateTest"` -> BUILD SUCCESSFUL (19/19); four sibling seat-supply suites -> BUILD SUCCESSFUL; `./gradlew test --tests "com.wfm.solver.SlotModeOvernightContractedHoursTest"` -> BUILD FAILED, failing only the forward-assertion method (3/4 pass); `./gradlew test --tests "com.wfm.support.MidnightBoundaryScenarioRegistryTest"` -> BUILD SUCCESSFUL; unfiltered `./gradlew test` (single, non-concurrent run) -> 1174 tests, 7 failed, 0 errors, failures confined to exactly the four expected classes.
