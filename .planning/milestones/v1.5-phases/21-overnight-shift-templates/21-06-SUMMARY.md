---
phase: 21-overnight-shift-templates
plan: 06
subsystem: solver
tags: [java, timefold, constraint-provider, day-window, midnight-boundary, shift-templates]

# Dependency graph
requires:
  - phase: 21-overnight-shift-templates (plan 21-01)
    provides: "ShiftTemplateService.createShiftTemplate accepts a genuine overnight envelope on an anchored desk, which is what makes a genuinely midnight-crossing scenario constructible here for the first time"
provides:
  - "MidnightBoundaryFixture.ninePmShiftCrossingMidnight(): the genuinely midnight-crossing constructed scenario Phase 18 deferred, registered in buildAllScenarios"
  - "Two 21:00-anchored day-off scenario builders over that shape, one with the day-off record on the shift's starting business date and one on the next"
  - "ScheduleConstraintProvider.anchoredMinAndMaxMinute(Collection<LocalTime>, DayWindow): the shared anchored range-bound helper now used by getGapLengths, findBreakStart, and the break-aware contiguity path -- replacing three clock-ordered TreeSet.first()/last() reads that inverted on a span crossing the anchor"
  - "An empty asserts-todays-behaviour registry (src/test/resources/midnight-boundary-scenarios.md): both remaining rows (OVNT-03, OVNT-04) discharged, each in the same commit that flipped its assertion"
affects: [21-07, 21-08, 21-09, 21-10, 21-11, 21-12]

# Actuals (#2632)
actuals:
  tokens: 9442
  tasks: 3
  commits: 3
  plan_head_before: 594f9bdbb5f36e1a7b22da1cc1f87487be2aeefa
  plan_head_after: d8fbb6262a5c30d6b422df1b9d5f117a0683c898

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A range-bound derivation repeated at three call sites is extracted to one private static helper the moment a second call site is added on top of the first two (anchoredMinAndMaxMinute) -- three independent copies is how two of them later disagree"
    - "A clock-ordered TreeSet<LocalTime> stays correct for membership lookups (.contains()) across an anchor crossing; only its first()/last() reads invert and must route through the window's anchored minute instead"

key-files:
  created: []
  modified:
    - src/test/java/com/wfm/solver/MidnightBoundaryFixture.java
    - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
    - src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java
    - src/test/java/com/wfm/service/MidnightBoundaryPropertyTest.java
    - src/test/resources/midnight-boundary-scenarios.md
    - src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java

key-decisions:
  - "The new midnight-crossing scenario (22:00-06:00 at a 21:00 anchor) carries exactly one break band (00:00-01:00) AND exactly one further interior gap (03:00-04:00), so the contiguity and break-location assertions each have a single unambiguous expected value -- not two overlapping concerns bundled into one slot"
  - "The day-off scenario pair reuses Task 1's crossing shape but with no break band and no gap -- every one of the eight slots is a plain demanded, assigned seat, so the scenario isolates the day-off join alone, never contiguity"
  - "anchoredMinAndMaxMinute takes Collection<LocalTime>, not List<AgentAssignment> -- all three call sites already hold a TreeSet<LocalTime> (assignedStarts or worked) by the time the range is needed, so the helper operates on that shared shape directly rather than re-deriving it from assignments"
  - "MidnightBoundaryScenarioRegistryTest's parseRegistry() throw-on-empty guard was removed, not widened with a special case -- it existed to catch an ACCIDENTALLY empty registry (wrong heading, vanished fence), which is a different failure mode from a DELIBERATELY empty one the registry's own exact-count assertion (EXPECTED_REGISTRY_SIZE) and the marked/registered set-equality assertion already guard independently. The generic no-fence-found failure in parseFencedBlock is untouched and still fails loudly on a genuine accident"

patterns-established:
  - "A scenario built specifically to invert a clock-ordered TreeSet's first()/last() (worked set first() = 01:00/anchored 240, last() = 23:00/anchored 120, collapsing the range to [240,120)) is the regression-safety shape for any future anchor-crossing scan fix in this file"

requirements-completed: [OVNT-03, OVNT-04]

# OVNT-02 is also satisfied by this plan's code (the three scan fixes) but stays OPEN in
# REQUIREMENTS.md/ROADMAP.md: it is shared by seven sibling plans in this phase (21-01, 21-03,
# 21-04, 21-09, 21-10, 21-11, 21-12) per the #2388 shared-ID gate, and is marked complete only once
# the LAST plan declaring it finishes. gsd_run query requirements.ready-ids confirmed this directly:
# {"ready": ["OVNT-03","OVNT-04"], "blocked": ["OVNT-02"], "total": 3}.

coverage:
  - id: D1
    description: "A constructed scenario genuinely crosses calendar midnight inside one business day (22:00-06:00 at a 21:00 anchor), registered in the fixture's scenario list"
    requirement: "OVNT-02"
    verification:
      - kind: unit
        ref: "com.wfm.support.MidnightBoundaryScenarioRegistryTest (class-load validator, via MidnightBoundaryFixture.validateBoundaryCoverage)"
        status: pass
    human_judgment: false
  - id: D2
    description: "The contiguity constraint (Shift work contiguity) counts the midnight-crossing agent-day's one interior gap, not zero"
    requirement: "OVNT-02"
    verification:
      - kind: unit
        ref: "MidnightBoundaryRegressionTest.ShiftCrossingMidnightContiguity#reportsItsOneInteriorGap"
        status: pass
    human_judgment: false
  - id: D3
    description: "The break-start scan (findBreakStart) locates the midnight-crossing agent-day's break slot (00:00), not null"
    requirement: "OVNT-02"
    verification:
      - kind: unit
        ref: "MidnightBoundaryRegressionTest.ShiftCrossingMidnightContiguity#locatesItsBreakSlot"
        status: pass
    human_judgment: false
  - id: D4
    description: "Every pre-existing midnight-anchored scenario's per-constraint match counts are byte-identical to before this plan's fix"
    requirement: "OVNT-02"
    verification:
      - kind: unit
        ref: "MidnightBoundaryRegressionTest.ShiftCrossingMidnightContiguity#preExistingScenarios_matchCountsUnchanged"
        status: pass
      - kind: unit
        ref: "full unfiltered ./gradlew test: 199 classes, 1266 tests, 0 failures, 0 errors, 4 pre-existing skips"
        status: pass
    human_judgment: false
  - id: D5
    description: "A day-off on the business day a midnight-spanning shift starts on blocks every one of its seats, including the ones stamped with the following calendar date; a day-off on the next business date blocks none"
    requirement: "OVNT-03"
    verification:
      - kind: unit
        ref: "MidnightBoundaryRegressionTest.DayOffAttributesToStartingBusinessDate#dayOffOnStartingBusinessDate_blocksEverySeatIncludingTheFollowingCalendarDate"
        status: pass
    human_judgment: false
  - id: D6
    description: "A midnight-spanning stretch's two calendar dates resolve to one business date, and the hours resolver returns the starting weekday's row for the whole stretch, never a second independent row for the following calendar date"
    requirement: "OVNT-04"
    verification:
      - kind: unit
        ref: "MidnightBoundaryPropertyTest.ContractedHoursStartingWeekdayOnly#bothCalendarDatesResolveToOneBusinessDate_oneWeekdaysHoursCoverTheWholeStretch"
        status: pass
    human_judgment: false
  - id: D7
    description: "The asserts-todays-behaviour registry is empty, and its both-directions validator (MidnightBoundaryScenarioRegistryTest) is green"
    requirement: "OVNT-03"
    verification:
      - kind: unit
        ref: "com.wfm.support.MidnightBoundaryScenarioRegistryTest (5/5)"
        status: pass
    human_judgment: false

duration: ~45min
completed: 2026-10-03
status: complete
---

# Phase 21 Plan 06: Midnight-Crossing Contiguity, Break-Scan, Day-Off and Hours Flips Summary

**A genuinely midnight-crossing SHIFT-mode scenario (22:00-06:00 at a 21:00 anchor) proved two of `ScheduleConstraintProvider`'s solver scans silently wrong -- the contiguity gap scan and the break-start scan both collapsed to an empty range on a span crossing the anchor -- fixed by replacing three clock-ordered `TreeSet.first()/last()` reads with one shared anchored-minute helper, then used to close out the milestone's two remaining inherited "asserts today's behaviour" registry entries.**

## Performance

- **Duration:** ~45 min
- **Tasks:** 3 of 3
- **Files modified:** 6

## Accomplishments
- Built `MidnightBoundaryFixture.ninePmShiftCrossingMidnight()`: a 21:00-anchored, 22:00-06:00 SHIFT-mode agent-day whose clock times genuinely touch `00:00` (unlike its `ninePmOvernightContiguityScenario` sibling, which deliberately avoids `00:00` by construction), with one real break band and one further interior gap -- registered in the fixture's scenario list and confirmed by the class-load validator to fire the "envelope crossing the day anchor" predicate without disturbing the predicate/fence set-equality contract.
- Proved, before touching production code, that this scenario's agent-day was mis-scored: the contiguity constraint ("Shift work contiguity") read **zero** holes for a day with one genuine interior gap, and the break-start scan returned **null** for a day with a genuine break -- both because the worked set's clock-ordered `first()` (01:00, anchored minute 240) sorted *after* its clock-ordered `last()` (23:00, anchored minute 120), collapsing the scan range to `[240, 120)`.
- Fixed the defect at its root: extracted `ScheduleConstraintProvider.anchoredMinAndMaxMinute(Collection<LocalTime>, DayWindow)` and routed all three affected range-bound derivations (`getGapLengths`, `findBreakStart`, and the break-aware contiguity path's inline `worked` set) through it. Membership lookups (`.contains()`) on the same `TreeSet`s were left untouched -- only the first/last reads were wrong. No constraint weight was retuned anywhere.
- Both of Task 1's red assertions turned green, and every pre-existing midnight-anchored scenario's per-constraint match counts stayed byte-identical -- none of them cross the anchor the way this plan's new scenario deliberately does, so Task 2's fix is a true no-op on every prior fixture.
- Closed out the milestone's last two inherited registry rows in the same commit that flipped each one's assertion: a day-off record on a midnight-spanning shift's starting business date now provably blocks every one of its eight seats (including the six stamped with the following calendar date), via the day-off join's existing `getTimeslot().getBusinessDate()` attribution -- proof work, not a solver change, since that join was already business-date-correct from Phase 20. Likewise, `resolveEffectiveHours` is proved to resolve the starting weekday's contracted-hours row for a stretch whose two calendar dates both derive to the same business date -- the resolver itself is unchanged; what changed is which date it is handed.
- The asserts-todays-behaviour registry in `midnight-boundary-scenarios.md` is now empty.

## Task Commits

Each task was committed atomically:

1. **Task 1: Construct the genuinely midnight-crossing scenario and prove the solver mis-scores it** - `6cf7941` (test) -- new fixture scenario + 4 new tests; 2 deliberately RED, 2 GREEN; zero production changes
2. **Task 2: Resolve the solver's first and last assigned slot in anchored order** - `e9415d9` (feat) -- `anchoredMinAndMaxMinute` extracted; all three clock-ordered reads replaced; Task 1's two RED assertions turn GREEN
3. **Task 3: Flip and deregister the day-off and contracted-hours registry entries** - `d8fbb62` (test) -- new day-off scenario pair; both nested test classes rewritten and un-marked; registry emptied; `MidnightBoundaryScenarioRegistryTest`'s own throw-on-empty guard fixed (Rule 3, see Deviations)

**Plan metadata:** this SUMMARY commit (docs: complete plan)

## Files Created/Modified
- `src/test/java/com/wfm/solver/MidnightBoundaryFixture.java` -- `ninePmShiftCrossingMidnight()`, `dayOffOnCrossingShiftStartingBusinessDateScenario()`/`dayOffOnCrossingShiftFollowingBusinessDateScenario()` and their shared private builder; all three registered in `buildAllScenarios()`
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` -- `anchoredMinAndMaxMinute(Collection<LocalTime>, DayWindow)` extracted; `getGapLengths`, `findBreakStart`, and `countNonBreakHoles`'s break-aware path all route their range bounds through it instead of `TreeSet.first()/last()`
- `src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java` -- new `ShiftCrossingMidnightContiguity` nested class (4 tests); `PtoOnAdjacentCalendarDate` rewritten to `DayOffAttributesToStartingBusinessDate` (marker and now-false reasoning comment removed)
- `src/test/java/com/wfm/service/MidnightBoundaryPropertyTest.java` -- `ContractedHoursStartingWeekdayOnly`'s assertion rewritten to prove the business-date derivation and the resolver's starting-weekday result; marker, its import, and the now-false reasoning comment removed
- `src/test/resources/midnight-boundary-scenarios.md` -- both remaining registry rows and their prose-table rows deleted; surrounding prose rewritten to record the registry is now empty, with both before-pictures preserved in prose and pointed at this SUMMARY
- `src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java` -- `EXPECTED_REGISTRY_SIZE` `2 -> 0`; `parseRegistry()`'s throw-on-empty guard removed (not in this plan's `files_modified` frontmatter -- see Deviations)

## Decisions Made

- The new midnight-crossing scenario deliberately carries both a break band AND a separate interior gap, at different anchored-minute positions, so Tasks 1/2's contiguity and break-location assertions each prove a single unambiguous property rather than conflating "the break" with "the gap."
- `anchoredMinAndMaxMinute` operates on `Collection<LocalTime>` rather than `List<AgentAssignment>` -- every call site already holds a `TreeSet<LocalTime>` by the time the range is needed, so the helper takes that shape directly rather than re-deriving it.
- The day-off scenario pair carries no break band and no gap -- every one of its eight slots is a plain demanded, assigned seat -- so it isolates the day-off join alone and cannot be confused with a contiguity assertion.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `MidnightBoundaryScenarioRegistryTest`'s `parseRegistry()` throw-on-empty guard removed; `EXPECTED_REGISTRY_SIZE` updated `2 -> 0`**
- **Found during:** Task 3, verification step
- **Issue:** the plan's `files_modified` frontmatter does not list `MidnightBoundaryScenarioRegistryTest.java`, but `parseRegistry()` throws `IllegalStateException` whenever the fenced "Scenarios asserting today's behaviour" block parses to an empty set ("An empty expected set would make the set-equality assertion vacuously satisfiable"). Once Task 3 emptied that block (the plan's own required end state), every test in this class that calls `parseRegistry()` -- including `registryHoldsExactlyTheExpectedEntryCount` and `markedMethodSet_equalsTheParsedRegistry_inBothDirections` -- would fail on the class's own now-stale `EXPECTED_REGISTRY_SIZE = 2` and on the throw-on-empty guard, blocking the plan's own stated acceptance criterion (`MidnightBoundaryScenarioRegistryTest` exits 0).
- **Fix:** updated `EXPECTED_REGISTRY_SIZE` to `0` with an explanatory javadoc recording that zero is the deliberate terminal state, not an oversight. Removed the `if (entries.isEmpty()) throw ...` guard from `parseRegistry()` specifically -- the structural "no fence found at all" failure mode it overlapped with is untouched (`parseFencedBlock` still throws loudly on a missing heading or an unclosed/absent fence), and the two live guards against a silently-wrong count (`EXPECTED_REGISTRY_SIZE`'s exact-match assertion and the marked/registered set-equality assertion) remain in force.
- **Files modified:** `src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.support.MidnightBoundaryScenarioRegistryTest"` green (5/5); full unfiltered `./gradlew test` green (199 classes, 1266 tests, 0 failures, 0 errors, 4 pre-existing skips).
- **Committed in:** `d8fbb62` (Task 3 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary for the plan's own stated acceptance criterion to be satisfiable at all -- emptying the registry and keeping its validator green are both explicit success criteria, and the validator's own emptiness guard was the one thing standing between them. No scope creep: the fix touches only this validator's guard logic and expected-count constant, not the registry mechanism itself.

## Issues Encountered

None beyond the one deviation above, resolved within Task 3 before proceeding.

## Registry Before-Pictures (preserved per this plan's `<output>` instruction)

The asserts-todays-behaviour registry is now empty. Before this plan, it held two rows, both
discharged here. Their before-pictures, since the registry itself no longer records them:

**OVNT-03 (day-off attribution)** -- Before this plan, `MidnightBoundaryRegressionTest.PtoOnAdjacentCalendarDate#attributionIsPerCalendarDateOnly`
asserted, against a shift whose seats all shared one calendar date (21:00-00:00, non-crossing):
a day-off record on the shift's own calendar date matched all 3 seats
(`MidnightBoundaryFixture.ptoOnShiftStartingDateScenario()`), while one on the following calendar
date matched 0 (`ptoOnFollowingDateScenario()`). Framed as "today: attribution is per calendar
date only, not per business day." Flipped here against a scenario that actually proves the
distinction (seats on two DIFFERENT calendar dates, one business date): a day-off on the starting
business date now provably matches all 8 seats of `ninePmShiftCrossingMidnight()`'s shape,
including the 6 stamped with the FOLLOWING calendar date -- proof work, since
`ScheduleConstraintProvider.agentDayOff`'s join already read `getTimeslot().getBusinessDate()`
(migrated in Phase 20), not a solver change.

**OVNT-04 (contracted-hours attribution)** -- Before this plan,
`MidnightBoundaryPropertyTest.ContractedHoursStartingWeekdayOnly#twoCalendarDatesDrawFromTwoIndependentWeekdayRows`
asserted that `SolverService.resolveEffectiveHours`, called directly with Monday and Tuesday as two
independent dates, returned Monday's 8.00h and Tuesday's 4.00h respectively -- "two calendar dates
each draw from their own independent weekday row." Flipped here by proving the PRIOR step instead:
at a 21:00 anchor, `DayWindow.businessDateOf` derives the SAME business date (Monday) for both
calendar-Monday-22:00 and calendar-Tuesday-02:00, so `resolveEffectiveHours` -- itself unchanged --
is handed Monday for the whole stretch and returns 8.00h throughout, never Tuesday's 4.00h.

**Task 1's observed RED values (P-02, the scan defect this plan's Task 2 fixes)** -- before Task
2's fix, on `ninePmShiftCrossingMidnight()`: the contiguity constraint ("Shift work contiguity")
reported **0** matches (expected 1, the agent-day's one genuine interior gap at 03:00-04:00); the
break-start scan (`ScheduleConstraintProvider.findBreakStart`) returned **null** (expected
`LocalTime.MIDNIGHT`, the agent-day's one genuine break at 00:00-01:00). Both failures were caused
by the same clock-order inversion: the worked set's clock-ordered `first()` is 01:00 (anchored
minute 240) and its clock-ordered `last()` is 23:00 (anchored minute 120), collapsing the derived
scan range to `[240, 120)` -- empty.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `ScheduleConstraintProvider.anchoredMinAndMaxMinute` is the shared anchored-range vocabulary any
  future scan over assigned start times in this file should reuse rather than re-deriving a
  clock-ordered first/last.
- The asserts-todays-behaviour registry mechanism (`@AssertsTodaysBehaviour`,
  `MidnightBoundaryScenarioRegistryTest`) is now at its deliberate zero-entry terminal state, fully
  live and ready to receive a new row the moment a future phase genuinely needs it again.
- OVNT-02 is satisfied by this plan's code but stays open in `REQUIREMENTS.md`/`ROADMAP.md`: it is
  declared by seven sibling plans in this phase (21-01 already closed; 21-03, 21-04, 21-09, 21-10,
  21-11, 21-12 still outstanding) and will mark complete automatically once the last of them
  finishes, per the #2388 shared-ID gate (`gsd_run query requirements.ready-ids` confirmed this:
  `{"ready":["OVNT-03","OVNT-04"],"blocked":["OVNT-02"]}`).
- No blockers for any downstream Phase 21 plan. `midnight-time-arithmetic.md`'s allowlist is
  unmodified (still 9 entries), and `src/test/resources/midnight-time-arithmetic.md`'s raw
  preferred-start comparison (owned by plan 21-11) was not touched, as instructed.

---
*Phase: 21-overnight-shift-templates*
*Completed: 2026-10-03*

## Self-Check: PASSED

All 6 modified source/test files and the SUMMARY.md confirmed present on disk; all 3 plan commits
(`6cf7941`, `e9415d9`, `d8fbb62`) confirmed present in `git log --oneline --all`. Full unfiltered
`./gradlew test` run green (199 classes, 1266 tests, 0 failures, 0 errors, 4 pre-existing skips).
All plan-level `<acceptance_criteria>` grep checks re-verified passing.
