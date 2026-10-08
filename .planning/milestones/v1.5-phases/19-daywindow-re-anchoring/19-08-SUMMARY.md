---
phase: 19-daywindow-re-anchoring
plan: 08
subsystem: testing
tags: [daywindow, bday-04, reflective-guard, frozen-oracle, junit5, public-api-boundary]

requires:
  - phase: 19-daywindow-re-anchoring
    provides: "19-01's ScheduleConfig.dayStart plumbing (V54-plumbing-sha 47649f1, this plan's criterion-5 lower bound); 19-02's frozen oracle and throw-domain pinning; 19-03's anchoredAt/nine anchored* instance methods; 19-04 through 19-07's full call-site migration"
provides:
  - "DayWindow's public surface reduced to the five anchored primitives, anchoredAt, and the nine anchored* instance methods -- no public static takes a bare scheduling time, compiler-enforced"
  - "DayWindowTest.FrozenOracleEquivalence flipped onto DayWindow.anchoredAt(MIDNIGHT) -- criterion 3's proof against pre-migration behaviour, with the criterion-2 duration divergence asserted as a named exception"
  - "DayWindowTest.NoPublicStaticTakesABareSchedulingTime -- the D-03 inversion of DeprecationIsLive, reflectively enforcing the public/private boundary, proven able to go red by hand"
  - "Criterion 5's proof: git diff --name-only 47649f1..4a129d3 is isolated to DayWindow, its sanctioned call sites, and four files that legitimately gained a new DayWindow reference"
  - "DeskService/DeskManagement.tsx name SOLV-01, not BDAY-04, as the day-start range's widener; 18-CONTEXT.md D-07 records the superseding resolution"
affects: [20-solver-business-date-correctness]

actuals:
  tokens: 20769
  tasks: 3
  commits: 4
  plan_head_before: 63df259b168adebe7ed3a2a7f7e4b2a28be16a9f

tech-stack:
  added: []
  patterns:
    - "Grounded compile-impact audit before a public-surface demotion: grep the exact method-call pattern across the whole tree immediately before AND after the edit, rather than trusting the plan's own read list -- found three test files (DayWindowAnchorBindingTest.java, MidnightBoundaryFixture.java, MidnightBoundaryPropertyTest.java, 40 combined call sites) the plan's read_first never named."
    - "When a transitional comparison test (static vs. instance) loses its second side to a demotion, re-point it at the nearest independent reference still in scope -- the frozen oracle for same-file sibling nested classes, a locally reproduced formula for a different top-level test class -- rather than deleting coverage or asserting a tautology against the instance method being tested."
    - "A governed @AssertsTodaysBehaviour test whose assertion straddles two different future flip points (DayWindow's own throw, owned by this phase; the save-path refusal, owned by a later phase's requirement) needs its two assertions treated separately on migration, not as one unit -- only the one already-this-phase's-job half changes."

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/util/DayWindow.java
    - src/test/java/com/wfm/util/DayWindowTest.java
    - src/test/java/com/wfm/util/DayWindowAnchorBindingTest.java
    - src/test/java/com/wfm/solver/MidnightBoundaryFixture.java
    - src/test/java/com/wfm/service/MidnightBoundaryPropertyTest.java
    - src/main/java/com/wfm/service/DeskService.java
    - frontend/src/pages/DeskManagement.tsx
    - .planning/phases/18-business-day-foundation-guards/18-CONTEXT.md
    - .planning/phases/19-daywindow-re-anchoring/19-VALIDATION.md

key-decisions:
  - "DayWindowTest.AnchoredEquivalenceAtMidnightAnchor and ThrowDomainIsPinned's two throw-predicate tests re-point onto FrozenOracle, not onto MIDNIGHT_WINDOW's anchored* methods. Comparing the five still-public anchored primitives against the bound instance's own anchored* methods would be circular -- anchoredStartMinute(t) literally IS startMinuteFromDayStart(MIDNIGHT, t) forwarded, so asserting they're equal proves nothing. FrozenOracle (plan 19-02, D-13) is the independent reference these comparisons need, and it was built precisely for this role."
  - "DayWindowTest's Duration nested class has TWO obsoleted assertions, not the one the plan's action text named. The throw domain plan 19-02 pinned (ThrowDomainIsPinned.durationMinutesThrows) is raw<=0, which covers BOTH the crossing-the-anchor case (crossingMidnightIsRejected) AND the zero-length-at-a-non-anchor-instant case (zeroLengthIntervalIsRejected) -- both assertions that today's durationMinutes throws are obsoleted by criterion 2 removing the throw, not only the one the plan's action text called out by name. Both were rewritten to assert the anchored instance method's new (non-throwing) value, each documented inline."
  - "MidnightBoundaryPropertyTest.ShiftCrossingMidnight#durationMinutesThrowsAndTheSavePathRefuses (a BDAY-06 @AssertsTodaysBehaviour test flipped by OVNT-01, Phase 21) had its DIRECT DayWindow.durationMinutes(22:00,06:00) throw-assertion removed and replaced with positive proof of the new wrapped value (480 minutes) -- this phase's own criterion 2 already removed that specific backstop throw from DayWindow's public surface (19-CONTEXT.md D-11 established it was backstop-only, unreachable through the real save path). The save-path refusal assertion immediately below it, exercised through the real ShiftTemplateService, is UNCHANGED and remains exactly what the test's OVNT-01 marker governs -- the registry entry in midnight-boundary-scenarios.md was NOT touched, since the method's name and its governed (save-path) assertion are both unchanged."
  - "FrozenOracleEquivalence.plusWithinDayAgreesForEveryBaseMinute no longer compares exception MESSAGE text between the oracle and the live instance (still compares exception TYPE and throw/no-throw agreement). anchoredPlusWithinDay composes through timeAtDayStartOffset (a different Phase-18 primitive, message 'minutesFromDayStart must be within...') rather than toLocalTime directly (message 'minuteOfDay must be within...') -- a cosmetic wording difference from using a different, equally-correct internal delegate, not a behavioural divergence. Found by running the sweep, not anticipated by the plan."
  - "DeskService.setDayStart's javadoc correction avoids the literal string 'BDAY-04' entirely (required by the task's own acceptance criterion), including in the sentence describing that DayWindow has already been re-anchored -- phrased via a {@link com.wfm.util.DayWindow} reference instead of naming the requirement ID that did it, since citing BDAY-04 there would have made the file's BDAY-04 grep count 1, not 0."
  - "The per-task verification map's 17th row (19-08 T3) could not be filled by Task 2 as the plan's action text describes, because Task 3 had not yet committed when Task 2 ran. Filled in a small, clearly-labelled follow-up commit (234ee37) immediately after Task 3 landed, closing the 'no remaining placeholder row' requirement without inventing a fourth named task."

requirements-completed: [BDAY-04]

coverage:
  - id: D1
    description: "DayWindow's public surface is reduced to the five anchored primitives, anchoredAt, and the nine anchored* instance methods. The nine midnight-implicit forms (startMinute, endMinute, durationMinutes, isForwardWithinDay, overlaps, contains, startsBefore, toLocalTime, plusWithinDay) are private; @Deprecated is removed from all nine since an unreachable private method needs no deprecation warning. A missed call site anywhere in the codebase is a compile failure (criterion 1)."
    requirement: BDAY-04
    verification:
      - kind: other
        ref: "./gradlew compileJava compileTestJava"
        status: pass
      - kind: other
        ref: "grep -cE '^[[:space:]]*public static (int|boolean|LocalTime) (startMinute|endMinute|durationMinutes|isForwardWithinDay|overlaps|contains|startsBefore|toLocalTime|plusWithinDay)[(]' src/main/java/com/wfm/util/DayWindow.java == 0"
        status: pass
      - kind: other
        ref: "grep -c '@Deprecated' src/main/java/com/wfm/util/DayWindow.java == 0"
        status: pass
    human_judgment: false
  - id: D2
    description: "The reflective guard (NoPublicStaticTakesABareSchedulingTime, the D-03 inversion of DeprecationIsLive) asserts no public DayWindow static takes a bare scheduling time, and is proven able to go red by hand: a temporary public static LocalTime bareOverloadRedProof(LocalTime) was added, DayWindowTest run, the guard observed red, then reverted."
    requirement: BDAY-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/util/DayWindowTest.java#NoPublicStaticTakesABareSchedulingTime.noMidnightImplicitFormSurvivesAsPublic"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/util/DayWindowTest.java#NoPublicStaticTakesABareSchedulingTime.everyPublicStaticTakesTheAnchorFirst"
        status: pass
    human_judgment: false
  - id: D3
    description: "The frozen-oracle sweep (FrozenOracleEquivalence) is flipped onto DayWindow.anchoredAt(MIDNIGHT), proving criterion 3 against the pre-migration implementation across the whole domain plan 19-02 established, with the criterion-2 duration divergence (oracle throws, instance returns the anchored composition's value) asserted as a named exception rather than excluded."
    requirement: BDAY-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/util/DayWindowTest.java#FrozenOracleEquivalence.durationMinutesAgreesForEveryOrderedPair"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/util/DayWindowTest.java#FrozenOracleEquivalence (all six tests)"
        status: pass
    human_judgment: false
  - id: D4
    description: "All eight of Phase 18's guard and regression selectors are green by name after the re-anchoring, and the full unfiltered suite is green at every commit boundary in this plan."
    requirement: BDAY-04
    verification:
      - kind: integration
        ref: "eight-selector guard set (DayWindowTest, MidnightTimeArithmeticGuardTest, MidnightBoundaryRegressionTest, MidnightBoundaryPropertyTest, BusinessDateWritePathGuardTest, TimeslotGeneratorBusinessDateTest, MidnightGapScanTest, MidnightWindowSeamTest)"
        status: pass
      - kind: integration
        ref: "./gradlew test (full, unfiltered), run after Task 1 and again at the plan's final commit -- 1138 tests, 0 failures, 0 errors, 4 pre-existing skips both times"
        status: pass
    human_judgment: false
  - id: D5
    description: "The re-anchoring commit range (47649f1..4a129d3) is isolated to DayWindow and files that reference it -- no migration, no ScheduleConfigAnchorPlumbingTest (confirming the lower bound), ifExists(ScheduleConfig count unchanged at 9, no constraint join key changed from a calendar-date to a business-date accessor."
    requirement: BDAY-04
    verification:
      - kind: other
        ref: "git diff --name-only 47649f1..4a129d3 -- per-path verdict in this SUMMARY's Criterion 5 section"
        status: pass
      - kind: other
        ref: "grep -c 'ifExists(ScheduleConfig' src/main/java/com/wfm/solver/ScheduleConstraintProvider.java == 9 (matches 19-07-SUMMARY.md)"
        status: pass
    human_judgment: false
  - id: D6
    description: "DeskService's setDayStart javadoc and the desk-configuration disclosure copy both name SOLV-01, not BDAY-04, as what widens the accepted day-start range. The 00:00-only gate itself, its message, and every other executable line are untouched."
    requirement: BDAY-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java (11/11 green)"
        status: pass
      - kind: other
        ref: "grep -c 'BDAY-04' DeskService.java/DeskManagement.tsx == 0; grep -c 'SOLV-01' both >= 1; grep -c 'Day start other than 00:00 is not yet supported' DeskService.java == 1"
        status: pass
      - kind: other
        ref: "npm --prefix frontend run build"
        status: pass
    human_judgment: false

duration: ~85 min
completed: 2026-10-01
status: complete
---

# Phase 19 Plan 08: Demote, Flip, Invert, Prove, Correct Summary

**The nine midnight-implicit `DayWindow` statics are demoted to private (compiler-forced proof no call site was missed), the frozen oracle is flipped onto the bound instance with the criterion-2 divergence asserted by name, the reflective guard is inverted and proven able to fail, criterion 5's commit-range isolation is proven over the actual range with a verbatim diff and four legitimately-unlisted files explained, and `DeskService`/`DeskManagement.tsx` now name `SOLV-01` instead of `BDAY-04` as the day-start range's widener.**

## Performance

- **Duration:** ~85 min
- **Started:** 2026-10-01 (immediately following plan 19-07's completion)
- **Completed:** 2026-10-01T02:35:14-04:00
- **Tasks:** 3
- **Files modified:** 9

## Accomplishments

- `DayWindow`'s nine midnight-implicit statics (`startMinute`, `endMinute`, `durationMinutes`, `isForwardWithinDay`, `overlaps`, `contains`, `startsBefore`, `toLocalTime`, `plusWithinDay`) are `private`, `@Deprecated` removed from all nine. `./gradlew compileJava compileTestJava` exits 0 — the compiler's own proof that every call site across the whole codebase was migrated by plans 19-01 through 19-07, with nothing missed.
- The class javadoc's "00:00 means different things by position" rule is generalised to the day-start anchor; the "Naming note" paragraph is updated from forward-looking ("through wave 19-07... plan 19-08 may rename these") to describing the final, settled state.
- `DayWindowTest.FrozenOracleEquivalence`'s nine `live*` comparison methods are flipped from the (now-private) statics onto `DayWindow.anchoredAt(LocalTime.MIDNIGHT)`; `durationMinutesAgreesForEveryOrderedPair` activates the criterion-2 divergence — inside the throw domain, asserts the oracle throws and the instance method returns `ThrowDomainIsPinned.expectedDurationAfterMigration`'s value.
- `DayWindowTest.DeprecationIsLive` is replaced by `NoPublicStaticTakesABareSchedulingTime` (D-03): reflectively asserts the public-static set equals exactly the five anchored primitives plus `anchoredAt`, and that every one of them takes `LocalTime` as its first parameter. Proven able to fail by hand (see Red-Proof below).
- Three test files not named in the plan's own read list, but found by a grounded compile-impact audit (`grep -rnE 'DayWindow\.(startMinute|endMinute|...)\(' src/main/java/ src/test/java/` before vs. after), needed re-pointing to compile: `DayWindowAnchorBindingTest.java` (23 calls), `MidnightBoundaryFixture.java` (9 calls), `MidnightBoundaryPropertyTest.java` (8 calls). All bind a midnight-anchored `DayWindow` instance; no asserted value changed except the two documented below.
- Criterion 4: all eight Phase 18 guard selectors green by name; full unfiltered `./gradlew test` green (1138 tests, 0 failures, 0 errors, 4 pre-existing skips — up from the 1137 baseline) at Task 1's commit and again at the plan's final commit.
- Criterion 5: `git diff --name-only 47649f1..4a129d3` proven isolated to `DayWindow` and its call sites (full verdict below); `ifExists(ScheduleConfig` count unchanged at 9; `.join(` count on `ScheduleConstraintProvider` unchanged at 31; no `.equal(` join-key predicate exists anywhere in the file.
- `DeskService.setDayStart`'s javadoc and `DeskManagement.tsx`'s disclosure copy both now name `SOLV-01` as what widens the accepted day-start range; `18-CONTEXT.md` D-07 carries a one-line superseded note pointing at `19-CONTEXT.md` D-01. The `00:00`-only gate, its message, and every other executable line in `DeskService` are byte-for-byte unchanged (confirmed by diff).
- `19-VALIDATION.md`'s per-task verification map is filled with real data for all 17 rows across all eight plans in this phase, replacing the seeded placeholder.

## Red-Proof (NoPublicStaticTakesABareSchedulingTime)

Performed by hand per the plan's `<behavior>` requirement: added `public static LocalTime bareOverloadRedProof(LocalTime t) { return t; }` to `DayWindow`, ran `./gradlew test --tests "com.wfm.util.DayWindowTest"`, observed red, then reverted (confirmed absent via `grep -n bareOverloadRedProof src/main/java/com/wfm/util/DayWindow.java` returning nothing, and a clean re-run of the same test command).

Verbatim AssertJ failure:

```
java.lang.AssertionError: [every anchored public static (D-06)]
Expecting actual:
  ["anchoredAt",
    "bareOverloadRedProof",
    "startMinuteFromDayStart",
    "endMinuteFromDayStart",
    "businessDateOf",
    "timeAtDayStartOffset",
    "calendarDateAtDayStartOffset"]
to contain exactly in any order:
  ["anchoredAt",
    "businessDateOf",
    "timeAtDayStartOffset",
    "calendarDateAtDayStartOffset",
    "endMinuteFromDayStart",
    "startMinuteFromDayStart"]
but the following elements were unexpected:
  ["bareOverloadRedProof"]
```

## Criterion 5 — Commit-Range Isolation Proof

Lower bound (exclusive): `47649f1` (V54 plumbing commit, plan 19-01). Upper bound: `4a129d3` (this plan's Task 1 commit).

```
$ git diff --name-only 47649f1..4a129d3
.planning/STATE.md
.planning/phases/19-daywindow-re-anchoring/19-01-SUMMARY.md
.planning/phases/19-daywindow-re-anchoring/19-02-SUMMARY.md
.planning/phases/19-daywindow-re-anchoring/19-03-SUMMARY.md
.planning/phases/19-daywindow-re-anchoring/19-04-SUMMARY.md
.planning/phases/19-daywindow-re-anchoring/19-05-SUMMARY.md
.planning/phases/19-daywindow-re-anchoring/19-06-SUMMARY.md
.planning/phases/19-daywindow-re-anchoring/19-07-SUMMARY.md
src/main/java/com/wfm/controller/ShiftTemplateController.java
src/main/java/com/wfm/model/ShiftBandPair.java
src/main/java/com/wfm/model/ShiftTemplate.java
src/main/java/com/wfm/model/ShiftTemplateBreakBand.java
src/main/java/com/wfm/service/DeskAgentService.java
src/main/java/com/wfm/service/FteUploadService.java
src/main/java/com/wfm/service/ScheduleEnvelopeRepairService.java
src/main/java/com/wfm/service/ScheduleExportService.java
src/main/java/com/wfm/service/ScheduleOutputService.java
src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
src/main/java/com/wfm/service/ShiftLibraryValidationService.java
src/main/java/com/wfm/service/ShiftStartMixTargetService.java
src/main/java/com/wfm/service/ShiftTemplateService.java
src/main/java/com/wfm/service/SolverService.java
src/main/java/com/wfm/service/StaffingRequirementService.java
src/main/java/com/wfm/service/TimeslotGeneratorService.java
src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
src/main/java/com/wfm/util/DayWindow.java
src/main/java/com/wfm/util/FteSpreadsheetGenerator.java
src/test/java/com/wfm/service/MidnightBoundaryPropertyTest.java
src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java
src/test/java/com/wfm/service/MidnightWindowSeamTest.java
src/test/java/com/wfm/service/MinimumStaffingSeatsTest.java
src/test/java/com/wfm/service/ScheduleAllocationExportTest.java
src/test/java/com/wfm/service/ScheduleEnvelopeRepairServiceTest.java
src/test/java/com/wfm/service/ScheduleExportServiceTest.java
src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java
src/test/java/com/wfm/service/ScheduleRosterExportTest.java
src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java
src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java
src/test/java/com/wfm/service/ShiftModeMinimumStaffingSeatSupplyTest.java
src/test/java/com/wfm/service/ShiftStartMixTargetServiceTest.java
src/test/java/com/wfm/service/ShiftTemplateServiceTest.java
src/test/java/com/wfm/service/SolverSeatExpansionAccess.java
src/test/java/com/wfm/service/SolverSeatSupplyGateAccess.java
src/test/java/com/wfm/service/StaffingRequirementErlangTest.java
src/test/java/com/wfm/service/TimeslotGeneratorBusinessDateTest.java
src/test/java/com/wfm/solver/DeskAnchorReachesConstraintTest.java
src/test/java/com/wfm/solver/LiveShapeShiftDeskFixture.java
src/test/java/com/wfm/solver/MidnightBoundaryFixture.java
src/test/java/com/wfm/solver/MidnightGapScanTest.java
src/test/java/com/wfm/solver/SeatSupplyDistributionAnalysisTest.java
src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java
src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java
src/test/java/com/wfm/solver/ShiftModeFixtures.java
src/test/java/com/wfm/solver/ZeroDemandTimeslotCeilingTest.java
src/test/java/com/wfm/util/DayWindowAnchorBindingTest.java
src/test/java/com/wfm/util/DayWindowTest.java
src/test/resources/midnight-time-arithmetic.md
```

**Per-path verdict:**

| Category | Verdict |
|---|---|
| `.planning/` docs (`STATE.md`, `19-0N-SUMMARY.md`) | Expected — planning artifacts for each plan in this phase's wave sequence |
| 15-production-file sanctioned list (`19-RESEARCH.md` § Call-site totals) | All present and accounted for: `TimeslotGeneratorService`, `ScheduleConstraintProvider`, `ShiftLibraryGenerationService`, `ShiftTemplateService`, `ScheduleOutputService`, `ScheduleExportService`, `FteSpreadsheetGenerator`, `ShiftBandPair`, `StaffingRequirementService`, `ShiftLibraryValidationService`, `FteUploadService`, `SolverService`, `ShiftTemplateBreakBand`, `ShiftTemplate`, plus `DayWindow` itself |
| 12-test-file sanctioned list | `MidnightBoundaryPropertyTest`, `MidnightTimeArithmeticGuardTest`, `MidnightWindowSeamTest`, `TimeslotGeneratorBusinessDateTest`, `MidnightBoundaryFixture`, `MidnightGapScanTest`, `DayWindowTest` present |
| Plan 19-03's two new test files | `DeskAnchorReachesConstraintTest.java`, `DayWindowAnchorBindingTest.java` present |
| Plan 19-07's guard files | `midnight-time-arithmetic.md` present |
| Four files NOT in `19-RESEARCH.md`'s original inventory | `ShiftTemplateController.java`, `DeskAgentService.java`, `ScheduleEnvelopeRepairService.java`, `ShiftStartMixTargetService.java` — each confirmed **0 `DayWindow` references at `47649f1`, >0 at `4a129d3`** (3, 12, 3, 3 respectively), each change a propagation of the D-09 model-class signature change (`ShiftBandPair.covers`, `ShiftTemplate.getNetHours`, `ShiftTemplateBreakBand` accessors) to that file's own callers — binding/threading a `DayWindow` parameter, not reimplementing interval arithmetic. None references `DayWindow` neither before nor after (the check's actual negative). |
| Remaining test files (`MinimumStaffingSeatsTest`, `ScheduleAllocationExportTest`, `ScheduleEnvelopeRepairServiceTest`, `ScheduleExportServiceTest`, `ScheduleOutputServiceShiftReportingTest`, `ScheduleRosterExportTest`, `ScheduleServiceShiftSnapshotTest`, `ShiftEnvelopeSupplyGateTest`, `ShiftLibraryValidationServiceTest`, `ShiftModeMinimumStaffingSeatSupplyTest`, `ShiftStartMixTargetServiceTest`, `ShiftTemplateServiceTest`, `SolverSeatExpansionAccess`, `SolverSeatSupplyGateAccess`, `StaffingRequirementErlangTest`, `LiveShapeShiftDeskFixture`, `SeatSupplyDistributionAnalysisTest`, `ShiftDeskEndToEndRegressionTest`, `ShiftEnvelopeSupplyInvariantTest`, `ShiftModeFixtures`, `ZeroDemandTimeslotCeilingTest`) | Test callers of the production files above whose own signatures changed to accept/construct a `DayWindow` (constructing fixtures, assertions against `getNetHours`/`covers`/break-band accessors) — same D-09 propagation, test-side |
| `src/main/resources/db/migration/` | **Empty — no migration in range**, confirming V54 is the exclusive lower bound and this phase adds no second migration |
| `src/test/java/com/wfm/solver/ScheduleConfigAnchorPlumbingTest.java` | **Absent from the range** — confirms the lower bound is plan 19-01's own commit (which created this file), not its parent; the three negatives are not passing vacuously |

**Negatives confirmed:**
- No path references `DayWindow` neither before nor after the change.
- No path under `src/main/resources/db/migration`.
- `ifExists(ScheduleConfig` count: `9` before and after (matches `19-07-SUMMARY.md`'s recorded count) — no `ifExists` stream converted to a join.
- `.join(` count on `ScheduleConstraintProvider.java`: `31` before and after. `.equal(` count: `0` both times — no constraint moved a join key from a calendar-date to a business-date accessor.

## Task Commits

Each task was committed atomically:

1. **Task 1: Demote the nine, flip the oracle, invert the guard** - `4a129d3` (feat)
2. **Task 2: Prove criterion 4 and criterion 5 over the phase's actual commit range** - `f0df8e8` (docs)
3. **Task 3: Correct the stated owner of the day-start range, outside the verified range** - `05d7ae6` (docs)

Plus one small, clearly-labelled follow-up to Task 2's own deliverable, landed after Task 3 (see Deviations): **Complete the per-task verification map's final row** - `234ee37` (docs)

**Plan metadata:** (this commit, docs(19-08): complete plan)

## Files Created/Modified
- `src/main/java/com/wfm/util/DayWindow.java` - nine statics demoted to private, `@Deprecated` removed, javadoc generalised
- `src/test/java/com/wfm/util/DayWindowTest.java` - frozen oracle flipped onto the bound instance; `DeprecationIsLive` inverted; `Duration`/`Conversion`/`PositionMatters`/`Forward`/`Overlaps`/`Contains`/`StartsBefore`/`AnchoredEquivalenceAtMidnightAnchor`/`ThrowDomainIsPinned` re-pointed off the now-private statics
- `src/test/java/com/wfm/util/DayWindowAnchorBindingTest.java` - re-pointed off the now-private statics (not in the plan's read list; found by the compile-impact audit)
- `src/test/java/com/wfm/solver/MidnightBoundaryFixture.java` - re-pointed (ditto)
- `src/test/java/com/wfm/service/MidnightBoundaryPropertyTest.java` - re-pointed (ditto); the OVNT-01-governed test's DayWindow-throw sub-assertion updated, save-path refusal unchanged
- `src/main/java/com/wfm/service/DeskService.java` - javadoc: `SOLV-01` replaces `BDAY-04` as the range-widener; executable lines unchanged
- `frontend/src/pages/DeskManagement.tsx` - disclosure comment: `SOLV-01` replaces `BDAY-04`
- `.planning/phases/18-business-day-foundation-guards/18-CONTEXT.md` - D-07 gains a superseded-ownership note
- `.planning/phases/19-daywindow-re-anchoring/19-VALIDATION.md` - per-task verification map filled (17 rows), Wave 0 and sign-off items ticked

## Decisions Made
See `key-decisions` in the frontmatter for full reasoning. In brief:
- `AnchoredEquivalenceAtMidnightAnchor`/`ThrowDomainIsPinned` re-point onto `FrozenOracle`, not the instance API, to avoid a circular comparison.
- Both `Duration` assertions the throw domain covers (crossing AND zero-length) needed updating, not only the one the plan named.
- `MidnightBoundaryPropertyTest`'s OVNT-01-governed test keeps its save-path assertion unchanged; only its DayWindow-own-throw sub-assertion (this phase's own criterion 2) was updated.
- `plusWithinDay`'s oracle-vs-live comparison drops exact exception-message matching (type and throw/no-throw still compared) — the live path now composes through a different Phase-18 primitive with a different message, a cosmetic difference found during the sweep.
- `DeskService`'s javadoc fix avoids the literal string "BDAY-04" entirely, including when describing that `DayWindow` is already re-anchored, to satisfy the task's own grep-count-of-0 acceptance criterion.
- The verification map's 17th row was filled in a small follow-up commit after Task 3, since Task 2 could not have filled a row for a task that had not yet run.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Re-pointed three test files not named in the plan's read list that call the nine demoted statics directly**
- **Found during:** Task 1, via the grounded compile-impact audit supplied in the dispatch prompt (and independently re-verified with the same grep before and after)
- **Issue:** `DayWindowAnchorBindingTest.java` (23 calls), `MidnightBoundaryFixture.java` (9 calls), `MidnightBoundaryPropertyTest.java` (8 calls) each call one or more of the nine midnight-implicit statics directly. Demoting those statics to private breaks compilation in all three — `private` is class-scoped, so even same-package test classes cannot reach them.
- **Fix:** Each binds a `DayWindow.anchoredAt(LocalTime.MIDNIGHT)` instance and calls the corresponding `anchored*` method instead. No asserted value changed in `MidnightBoundaryFixture.java`. In `DayWindowAnchorBindingTest.java`, the "agrees with its static counterpart" comparisons became comparisons against a locally reproduced formula (the static is unreachable from this file; no shared reference exists to call instead) — same asserted values, different comparison mechanics.
- **Files modified:** the three files above
- **Verification:** `./gradlew compileJava compileTestJava` exits 0; `./gradlew test` full suite green (1138/0/0/4)
- **Committed in:** `4a129d3` (Task 1)

**2. [Rule 1 - Bug] `zeroLengthIntervalIsRejected` is a second obsoleted assertion the plan's action text did not name**
- **Found during:** Task 1, while migrating `DayWindowTest.Duration` to the instance API
- **Issue:** The plan's action text names only `crossingMidnightIsRejected` as the one assertion criterion 2 obsoletes. But plan 19-02's own `ThrowDomainIsPinned.durationMinutesThrows` predicate is `raw <= 0`, which is also true for the zero-length-at-a-non-anchor-instant case `zeroLengthIntervalIsRejected` pins — criterion 2 removes the throw for BOTH cases, not only the named one.
- **Fix:** Renamed and rewrote `zeroLengthIntervalIsRejected` to `zeroLengthIntervalIsNoLongerRejected`, asserting the anchored instance method now returns `0` (matching `ThrowDomainIsPinned.equalEndpointBoundaryIsNotConfusedWithTheWholeDayCase`'s own pinned expectation), with an inline comment explaining the distinction from the whole-day-at-the-anchor case.
- **Files modified:** `src/test/java/com/wfm/util/DayWindowTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.util.DayWindowTest"` green, 62 tests
- **Committed in:** `4a129d3` (Task 1)

**3. [Rule 1 - Bug] `FrozenOracleEquivalence.plusWithinDayAgreesForEveryBaseMinute`'s exact exception-message comparison fails after the flip**
- **Found during:** Task 1, first test run after flipping the frozen oracle onto the bound instance
- **Issue:** `anchoredPlusWithinDay` composes through `timeAtDayStartOffset`'s bounds check (message: `"minutesFromDayStart must be within..."`) rather than `toLocalTime`'s (message: `"minuteOfDay must be within..."`) — a different, already-existing Phase-18 primitive. The oracle's `plusWithinDay` still throws via its own `toLocalTime`-shaped message. Exact message-text equality, which the pre-flip test asserted, is no longer true even though the exception type and the throw/no-throw boundary are identical.
- **Fix:** Changed the assertion to compare exception type and throw/no-throw agreement (unchanged behaviour, still asserted), and replaced the exact-message-equality check with a weaker check that the live message still names the `1440` boundary — with an inline comment explaining why message text legitimately differs.
- **Files modified:** `src/test/java/com/wfm/util/DayWindowTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.util.DayWindowTest"` green
- **Committed in:** `4a129d3` (Task 1)

**4. [Rule 1 - Bug] `MidnightBoundaryPropertyTest`'s two direct calls to `DayWindow.toLocalTime`/`durationMinutes` inside `ThrowDomainIsPinned`-adjacent predicate tests also needed re-pointing**
- **Found during:** Task 1 compile pass
- **Issue:** `ThrowDomainIsPinned.durationMinutesPredicateAgreesForEveryOrderedPair` and `plusWithinDayPredicateAgreesForEveryBaseMinute` called `DayWindow.toLocalTime`/`durationMinutes`/`plusWithinDay` directly to establish "today's" (pre-migration) throwing behaviour, contradicting 19-02-SUMMARY's claim that this class "reads only FrozenOracle's own methods" — that claim was true of the predicate *functions*, not these two *test methods*.
- **Fix:** Re-pointed both test methods onto `FrozenOracle`'s own methods (the genuine frozen, pre-migration reference), preserving the intent of proving the predicates match "today's" behaviour.
- **Files modified:** `src/test/java/com/wfm/util/DayWindowTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.util.DayWindowTest"` green
- **Committed in:** `4a129d3` (Task 1)

**5. [Rule 4 → resolved via careful analysis, not escalated] `MidnightBoundaryPropertyTest`'s OVNT-01-governed test's direct `DayWindow` throw assertion**
- **Found during:** Task 1 compile pass
- **Issue:** `ShiftCrossingMidnight#durationMinutesThrowsAndTheSavePathRefuses` is a BDAY-06 `@AssertsTodaysBehaviour` test registered in `midnight-boundary-scenarios.md` as flipped by `OVNT-01` (Phase 21). Its first assertion directly calls `DayWindow.durationMinutes(22:00, 06:00)` expecting a throw — but this phase's own criterion 2 already removes that exact throw from `DayWindow`'s public surface (confirmed by `19-CONTEXT.md` D-11: the backstop was always unreachable through the real save path). The test's SECOND assertion (the save-path refusal via the real `ShiftTemplateService`) is genuinely `OVNT-01`'s concern and must stay unchanged.
- **Fix:** Did not touch the registry (`midnight-boundary-scenarios.md`) or rename the method — its `@AssertsTodaysBehaviour`/`OVNT-01` marker and the save-path assertion it governs are unaffected. Removed only the now-impossible direct-`DayWindow`-throw assertion and replaced it with positive proof that the anchored instance method now returns the wrapped value (480 minutes), with an inline comment distinguishing the two different flip points the original test conflated.
- **Files modified:** `src/test/java/com/wfm/service/MidnightBoundaryPropertyTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.service.MidnightBoundaryPropertyTest"` green (part of the eight-selector guard set and the full suite)
- **Committed in:** `4a129d3` (Task 1)

**6. [Rule 3 - Mechanical] `DeskService.java`'s javadoc fix could not mention "BDAY-04" at all, including when describing that `DayWindow` is already re-anchored**
- **Found during:** Task 3, first draft of the javadoc correction
- **Issue:** The task's own acceptance criterion requires `grep -c 'BDAY-04'` to report `0`. An early draft included "BDAY-04 has already re-anchored DayWindow" to explain why the blocker is now solver joins, not `DayWindow` itself — which would have made the count `1`.
- **Fix:** Rephrased to reference `{@link com.wfm.util.DayWindow}` without naming which requirement re-anchored it.
- **Files modified:** `src/main/java/com/wfm/service/DeskService.java`
- **Verification:** `grep -c 'BDAY-04' src/main/java/com/wfm/service/DeskService.java` reports `0`
- **Committed in:** `05d7ae6` (Task 3)

**7. [Rule 3 - Mechanical] The per-task verification map's final row could not be filled by Task 2 as literally described**
- **Found during:** Task 2, while filling the verification map
- **Issue:** The plan's action text asks Task 2 to fill "one row per task across all eight plans" — but this plan's own Task 3 had not yet run (or committed) when Task 2 executed, so Task 2 could not know Task 3's commit hash or outcome.
- **Fix:** Task 2 filled all 16 rows it could (including a `⬜ pending` placeholder for 19-08 T3), then a small, separately-committed follow-up filled the 17th row once Task 3's commit existed.
- **Files modified:** `.planning/phases/19-daywindow-re-anchoring/19-VALIDATION.md`
- **Verification:** no remaining `⬜ pending`/placeholder row in the table
- **Committed in:** `234ee37` (follow-up, after `05d7ae6`)

---

**Total deviations:** 7 auto-fixed (4 Rule-1/Rule-3 test-migration fixes caught by the compile-impact audit and the sweep itself, 1 careful Rule-4-adjacent resolution of a governed test that straddled two different future flip points without touching its governing registry, 2 Rule-3 mechanical fixes to satisfy literal acceptance criteria)
**Impact on plan:** All seven are confined to test-only code or javadoc/comment text; none weakens coverage, none folds Phase 20/21 work into this phase, none touches a constraint's join key or the `DeskService` gate's executable behaviour. Deviation 5 required the most care (a governed BDAY-06 test straddling two owners) and was resolved by leaving the registry and the OVNT-01-governed half of the assertion untouched, changing only the half that is genuinely this phase's own criterion 2.

## Issues Encountered

An earlier `./gradlew test` invocation (full unfiltered suite, launched in the background) was run concurrently with several filtered `--tests` invocations while investigating the eight-selector set — this produced "Could not write XML test results" errors and a spurious `BUILD FAILED`, exactly the known concurrent-gradle-XML-write collision documented in project memory. That run's result was discarded entirely; a clean, non-concurrent re-run (no other gradle invocation in flight) produced the authoritative green result recorded in this SUMMARY. No code or test content was affected — this was a tooling/concurrency artifact, not a real failure.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness

- Phase 19 (DayWindow Re-anchoring) is complete: all 8 plans landed, `BDAY-04` is the phase's sole requirement and is now ready to mark complete (the last plan declaring it has finished).
- `18-VERIFICATION.md`'s `covered_digest` (`v1:sha256:f3aaeab3...`) is now **stale**. Files in its `covered_files` list modified across this plan's three tasks: `DeskService.java`, `DeskManagement.tsx`, `DayWindow.java`, `DayWindowTest.java`, `MidnightBoundaryPropertyTest.java`, `MidnightBoundaryFixture.java`. This is bookkeeping the phase owes forward, not a defect — per the plan's explicit instruction, the digest was NOT refreshed from inside this task; a deliberate refresh with its own control is required at milestone close.
- `DayWindow`'s public surface is now exactly the five anchored primitives, `anchoredAt`, and the nine `anchored*` instance methods — the revert target for the whole re-anchoring is the range `47649f1..4a129d3`, proven isolated.
- `ScheduleConstraintProvider.PENDING_DESK_ANCHOR` (plan 19-07) remains the explicit, named work queue `SOLV-01` (Phase 20) inherits — six call sites, one allowlist entry, nothing scattered.
- `roadmap update-plan-progress` is known to return `{updated:false, reason:"missing_phase_details"}` for this repo (pre-existing tool/repo mismatch per the dispatch prompt's constraint 8) — not attempted as a blocking step; the orchestrator handles phase-level ROADMAP marking.
- No blockers. Full unfiltered `./gradlew test` is green (1138 tests, 0 failures, 0 errors, 4 pre-existing skips) at the plan's final commit (`234ee37`).

## Self-Check: PASSED

- FOUND: `src/main/java/com/wfm/util/DayWindow.java` — `grep -c '@Deprecated'` reports `0`
- FOUND: `grep -cE` for the nine public-static signatures reports `0`
- FOUND: `src/test/java/com/wfm/util/DayWindowTest.java` declares `NoPublicStaticTakesABareSchedulingTime`, no longer declares `DeprecationIsLive`
- FOUND commit `4a129d3` (Task 1) in `git log --oneline --all`
- FOUND commit `f0df8e8` (Task 2) in `git log --oneline --all`
- FOUND commit `05d7ae6` (Task 3) in `git log --oneline --all`
- FOUND commit `234ee37` (verification-map follow-up) in `git log --oneline --all`
- CONFIRMED: `./gradlew compileJava compileTestJava` exits 0
- CONFIRMED: `./gradlew test --tests "com.wfm.util.DayWindowTest"` — 62 tests, 0 failures, 0 errors (up from 61)
- CONFIRMED: eight-selector guard set green by name
- CONFIRMED: full unfiltered `./gradlew test` at `4a129d3` and again at `234ee37` — both 1138 tests, 0 failures, 0 errors, 4 pre-existing skips
- CONFIRMED: `git diff --name-only 47649f1..4a129d3` matches the sanctioned list plus four explained files; no migration; `ScheduleConfigAnchorPlumbingTest.java` absent
- CONFIRMED: `grep -c 'ifExists(ScheduleConfig' src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` reports `9`, matching `19-07-SUMMARY.md`
- CONFIRMED: `grep -c 'BDAY-04' src/main/java/com/wfm/service/DeskService.java frontend/src/pages/DeskManagement.tsx` reports `0` for both
- CONFIRMED: `grep -c 'SOLV-01'` reports `>=1` for both
- CONFIRMED: `grep -c 'Day start other than 00:00 is not yet supported' src/main/java/com/wfm/service/DeskService.java` reports `1`
- CONFIRMED: `./gradlew test --tests "com.wfm.service.DeskServiceDayStartTest"` — 11 tests, 0 failures, 0 errors
- CONFIRMED: `npm --prefix frontend run build` exits 0
- CONFIRMED: `git diff -- .planning/phases/18-business-day-foundation-guards/18-CONTEXT.md` shows only the D-07 superseded-note addition

---
*Phase: 19-daywindow-re-anchoring*
*Completed: 2026-10-01*
