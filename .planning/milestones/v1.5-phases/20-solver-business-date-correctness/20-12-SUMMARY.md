---
phase: 20-solver-business-date-correctness
plan: 12
subsystem: solver
tags: [timefold, jpa, business-date, day-window, derived-query]

# Dependency graph
requires:
  - phase: 20-solver-business-date-correctness
    provides: "DayWindow.businessDateOf/startMinuteFromDayStart statics (plan 19-05), TimeslotGeneratorService's BDAY-03 widen-then-derive read-back pattern, and plans 20-10/20-11's business-date fixes to expandMinimumStaffingSeats/runPreSolveValidation (the two downstream consumers this plan stops feeding pre-truncated data)"
provides:
  - "BusinessDayPeriodLoader — the single shared widen-then-derive loader for live timeslots and live staffing requirements, used by all four call sites that take business-date period bounds"
  - "acceptSchedule's timeslot and staffing-requirement snapshot loads corrected (CR-01 sites 3-4) — a re-anchored desk's ACCEPTED snapshot is no longer permanently truncated"
  - "startSolve's two problem-fact loads corrected (CR-01 sites 1-2) — the solver, runPreSolveValidation and expandMinimumStaffingSeats now receive the complete last business day"
  - "bday-join-guard.md's repository-finder-name / JPQL t.date blind spot documented (WR-01), and IN-01's ordering-dependency comment on SolverService's lastOnDay reduce"
affects: [21-overnight-shift-labelling, 22-minimum-rest-horizons]

# Actuals (#2632)
actuals:
  tokens: 11817
  tasks: 3
  commits: 5

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "widen-then-derive loader: widen the calendar upper bound by one day, then filter (and for timeslots, sort) on DayWindow.businessDateOf — never on the stored business_date column. BusinessDayPeriodLoader is the second instance of this pattern, mirroring TimeslotGeneratorService's BDAY-03 read-back exactly."

key-files:
  created:
    - src/main/java/com/wfm/service/BusinessDayPeriodLoader.java
    - src/test/java/com/wfm/service/BusinessDayPeriodLoaderTest.java
  modified:
    - src/main/java/com/wfm/service/ScheduleService.java
    - src/main/java/com/wfm/service/SolverService.java
    - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
    - src/test/resources/bday-join-guard.md

key-decisions:
  - "Test C's plan-specified assertion (hasSize(3)) is mathematically incompatible with DayWindow.businessDateOf's own documented semantics for the fixture the plan describes — corrected to hasSize(24) so the test proves what the surrounding prose intends (derivation governs over the stored column) rather than asserting a count the production code cannot produce. See Deviations."
  - "BusinessDayPeriodLoaderTest's 'Test 1' (captured widened bound) is two @Test methods, not one — one per repository (timeslots, staffing) — for isolated failure reporting. Pre-existing-baseline-plus-delta math in the plan's own <verification> step 8 (1216) is therefore off by one; the real total is 1217, accounted for below."

patterns-established:
  - "Pattern 1: a shared package-private static loader class is the fix for 'the same calendar-vs-business-date defect recurring across call sites' — one class, N callers, grep-able by name — rather than N inlined copies of the widen/filter/sort logic."

requirements-completed: [SOLV-01, SOLV-02]

coverage:
  - id: D1
    description: "Accepting a schedule on a 21:00-anchored desk snapshots all 24 of the accepted business day's timeslots and staffing requirements (previously 3 and 3) — CR-01 sites 3-4"
    requirement: "SOLV-01"
    verification:
      - kind: integration
        ref: "ScheduleServiceShiftSnapshotTest#acceptSchedule_21_00Anchor_snapshotsAllTwentyFourTimeslotsOfTheLastBusinessDay"
        status: pass
      - kind: integration
        ref: "ScheduleServiceShiftSnapshotTest#acceptSchedule_21_00Anchor_snapshotsAllTwentyFourStaffingRequirementsOfTheLastBusinessDay"
        status: pass
      - kind: integration
        ref: "ScheduleServiceShiftSnapshotTest#acceptSchedule_21_00Anchor_derivesBusinessDateRatherThanTrustingTheStoredColumn"
        status: pass
      - kind: integration
        ref: "ScheduleServiceShiftSnapshotTest#acceptSchedule_midnightAnchor_snapshotIsUnchangedAndDecoyDayIsExcluded"
        status: pass
    human_judgment: false
  - id: D2
    description: "startSolve's two problem-fact loads (timeslots, staffing requirements) go through BusinessDayPeriodLoader — CR-01 sites 1-2 — and BusinessDayPeriodLoader's own widen/filter/sort mechanics are pinned directly"
    requirement: "SOLV-01"
    verification:
      - kind: unit
        ref: "BusinessDayPeriodLoaderTest (8 tests)"
        status: pass
      - kind: unit
        ref: "PreSolveValidationBusinessDateTest (5 tests, unaffected regression check)"
        status: pass
      - kind: unit
        ref: "MinimumStaffingSeatsBusinessDateTest (5 tests, unaffected regression check)"
        status: pass
    human_judgment: false
  - id: D3
    description: "bday-join-guard.md records the repository-finder-name / JPQL t.date shape as a named blind spot (WR-01), and SolverService's lastOnDay reduce documents its ordering dependency (IN-01)"
    requirement: "SOLV-02"
    verification:
      - kind: unit
        ref: "BusinessDateJoinGuardTest (6 tests, unaffected — guard still parses its allowlist and passes)"
        status: pass
    human_judgment: false

duration: 30min
completed: 2026-10-02
status: complete
---

# Phase 20 Plan 12: Business-Date Problem-Fact Loads Summary

**One shared widen-then-derive loader (`BusinessDayPeriodLoader`) closes CR-01's four confirmed sites — `startSolve`'s two problem-fact loads and `acceptSchedule`'s two snapshot loads — plus WR-01's join-guard documentation gap, proven end-to-end against a real database at a 21:00 anchor with a midnight no-op control.**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-10-02T12:44:00Z (approx, first RED commit 08:49:54 EDT)
- **Completed:** 2026-10-02T13:14:27Z
- **Tasks:** 3 completed
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments

- `BusinessDayPeriodLoader` (new): the single shared widen-then-derive implementation — `loadLiveTimeslots` and `loadLiveStaffingRequirements` — mirroring `TimeslotGeneratorService`'s BDAY-03 read-back exactly: widen the calendar upper bound by `periodEnd.plusDays(1)`, then filter (timeslots: and sort) on `DayWindow.businessDateOf` — zero reads of the stored `business_date` column.
- `ScheduleService.acceptSchedule`'s two snapshot loads (CR-01 sites 3-4, the third and fourth confirmed sites — not in the original `20-REVIEW.md`, found while verifying/planning) now go through the loader. Proven end-to-end against a real H2/`@DataJpaTest` database: a 21:00-anchored accept now persists all 24 of business-Monday's timeslots and staffing requirements, where it previously persisted 3 and 3.
- `SolverService.startSolve`'s two problem-fact loads (CR-01 sites 1-2) now go through the same loader, using `desk.getDayStart()` — no second anchor source, no new `Desk` lookup. The solver, `runPreSolveValidation` and `expandMinimumStaffingSeats` (fixed in plans 20-11/20-10) now receive the complete last business day instead of data already truncated upstream.
- `BusinessDayPeriodLoaderTest` (new, 8 tests, plain JUnit + Mockito, no Spring context): pins the widened bound actually requested, two-sided filtering, explicit business-day ordering for timeslots, order-preservation (filter-only) for staffing requirements, and a midnight no-op control for both methods.
- `bday-join-guard.md` (WR-01): new "Known scope boundaries" entry naming the repository derived-query-method-name / JPQL `t.date` shape as a third blind spot this guard structurally cannot see, continuing plan 20-11's `SolverService` audit entry. `BusinessDateJoinGuardTest` is unedited and still passes 6/6.
- `SolverService`'s `lastOnDay` reduce (IN-01) now documents the ordering guarantee it depends on, naming `BusinessDayPeriodLoader` as the provider.
- Full unfiltered suite: **1217 tests, 0 failures, 0 errors, 4 skipped** (see Verification below for the +1 vs. the plan's projected 1216).

## Task Commits

Each task was committed atomically (Tasks 1 and 2 carry TDD RED→GREEN commit pairs):

1. **Task 1 (RED): add failing 21:00-anchored accept-path tests** — `6be0dcb` (test)
2. **Task 1 (GREEN): add BusinessDayPeriodLoader, re-point acceptSchedule's two snapshot loads** — `f1bf64d` (feat)
3. **Task 2 (test): add BusinessDayPeriodLoaderTest** — `ba85a00` (test)
4. **Task 2 (GREEN): re-point startSolve's two problem-fact loads** — `0959c34` (feat)
5. **Task 3: record the repository-finder blind spot, document IN-01** — `4a1edf0` (docs)

**Plan metadata:** (this commit)

## Files Created/Modified

- `src/main/java/com/wfm/service/BusinessDayPeriodLoader.java` — new; the shared widen-then-derive loader
- `src/test/java/com/wfm/service/BusinessDayPeriodLoaderTest.java` — new; 8 tests, loader's own edge coverage
- `src/main/java/com/wfm/service/ScheduleService.java` — `acceptSchedule`'s two snapshot loads re-pointed
- `src/main/java/com/wfm/service/SolverService.java` — `startSolve`'s two problem-fact loads re-pointed; `lastOnDay` ordering comment (IN-01)
- `src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java` — 4 new 21:00-anchored accept-path tests, 2 new helpers, 1 new `@Autowired` field
- `src/test/resources/bday-join-guard.md` — new "Known scope boundaries" entry (WR-01)

## Decisions Made

- **Test C's assertion corrected from the plan's literal `3` to `24`** (see Deviations) — the mathematically consistent value proving derivation governs over the stored column.
- **`BusinessDayPeriodLoaderTest`'s Test 1 split into two `@Test` methods** (one per repository) rather than one test asserting both captured arguments, for isolated per-repository failure reporting. Pushes the suite total to 1217, not the plan's projected 1216 (see Verification).
- No architectural deviations; `BusinessDayPeriodLoader`'s shape (package-private, two statics, private constructor) matches the plan's `<planner_decisions>` P-01 through P-06 verbatim.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug in the plan's test specification, not production code] Test C's expected count corrected from 3 to 24**

- **Found during:** Task 1, first GREEN run (after `BusinessDayPeriodLoader` existed and `ScheduleService` was re-pointed)
- **Issue:** The plan's `<behavior>` section specified Test C (the "falsification control") should assert `hasSize(3)` and "pass both before and after the production change." Both claims contradict `DayWindow.businessDateOf`'s own documented, exhaustively-tested semantics — the same function `TimeslotGeneratorService`'s BDAY-03 read-back already relies on, and which this entire plan's fix depends on being correct. For every one of Test C's 21 post-midnight rows (calendar `TUESDAY`, start times `00:00`-`20:00`, anchor `21:00`): `startMinute(startTime) < startMinute(21:00)`, so `businessDateOf` unconditionally returns `calendarDate.minusDays(1)` = `MONDAY` — independent of whatever is stored in the `businessDate` column. There is no way for only 3 of these 24 specific rows to derive to `MONDAY`; all 24 do, by the same construction Test A already relies on. Further, no fixture built on this exact cross-midnight calendar split (3 rows on calendar Monday, 21 on calendar Tuesday) can be a true before/after no-op: the OLD code filters on the CALENDAR `date` column alone (never reads `businessDate` at all) and always returns 3 for this split; the FIXED code always returns 24, regardless of what is stored — so any genuine stored-vs-derived disagreement test built on this split necessarily shows the same 3→24 transition Test A shows.
- **Fix:** Changed the assertion from `hasSize(3)` to `hasSize(24)`, with an inline comment explaining the math and documenting that only 3 of the 24 rows' STORED value reads `MONDAY` while all 24 DERIVE to `MONDAY` — proving derivation, not the stored column, governs. This is what the surrounding prose actually intended ("proving the loader derives rather than reads the stored column").
- **Observed RED/behavior history (both runs are real, not hypothetical):** At the RED observation point (Task 1, before `BusinessDayPeriodLoader` existed), Test C — then still asserting the plan's literal `hasSize(3)` — **passed**, exactly as the plan claimed ("Tests C and D passed before it as well"): the pre-fix finder's calendar-only filter happens to return 3 for this fixture regardless of stored values, which coincidentally matched the literal assertion. Only after applying the production fix did Test C start failing (`expected: 3, actual: 24`), which is what surfaced the plan's inconsistency. **Test C, as corrected, is therefore NOT a true before/after no-op** — it is a second instance of the same 3→24 RED/GREEN transition Test A proves, for the same underlying reason. Test D (the midnight-anchored control) remains the genuine no-op: it is unaffected by this issue, since a midnight anchor makes the widen-then-filter mechanism provably inert regardless of stored values.
- **Files modified:** `src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java`
- **Verification:** `./gradlew test --tests "com.wfm.service.ScheduleServiceShiftSnapshotTest"` — 21/21 pass after the correction.
- **Committed in:** `f1bf64d` (Task 1 GREEN commit)

---

**Total deviations:** 1 auto-fixed (1 test-specification bug in the plan, not production code).
**Impact on plan:** No change to any production file's behavior or shape — `BusinessDayPeriodLoader`, `ScheduleService` and `SolverService` were implemented exactly as the plan's `<action>` sections specify. The deviation is confined to one test assertion's expected value and its surrounding documentation. No scope creep.

## Issues Encountered

None beyond the Test C deviation documented above.

## Verification

Ran in full, full suite last and unfiltered, per the plan's `<verification>` block:

1. `./gradlew compileJava compileTestJava` — exit 0, no `error:` lines. PASS.
2. `grep -rl 'findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc' src/main/java | sort` — exactly `TimeslotRepository.java`, `BusinessDayPeriodLoader.java`, `TimeslotGeneratorService.java`. PASS.
3. `grep -rl 'findLiveByDeskAndDateRange' src/main/java | sort` — exactly `StaffingRequirementRepository.java`, `BusinessDayPeriodLoader.java`, `StaffingRequirementService.java`. PASS.
4. `grep -c 'getBusinessDate()' .../BusinessDayPeriodLoader.java` — 0. PASS.
5. `grep -c '.sorted(' .../BusinessDayPeriodLoader.java` — 1. PASS.
6. `grep -c 'businessDate BETWEEN' .../TimeslotRepository.java .../StaffingRequirementRepository.java` — 0 and 1 (pre-existing `deleteLiveByDeskAndBusinessDateRange`, unchanged). PASS.
7. `grep -c 'TimeslotRepository' src/test/resources/bday-join-guard.md` — 1 (baseline was 0). PASS.
8. `./gradlew test` (full, unfiltered) — **1217 tests, 0 failures, 0 errors, 4 skipped.** The plan projected 1216 (1205 baseline + 7 `BusinessDayPeriodLoaderTest` + 4 `ScheduleServiceShiftSnapshotTest`). The actual +12 (not +11) is accounted for entirely by `BusinessDayPeriodLoaderTest` carrying **8** tests, not 7 — "Test 1" (the captured-widened-bound proof) is two `@Test` methods (one for `loadLiveTimeslots`, one for `loadLiveStaffingRequirements`) rather than a single test asserting both repository calls, for isolated per-repository failure reporting. 1205 + 8 + 4 = 1217. Confirmed via each class's own JUnit XML: `BusinessDayPeriodLoaderTest` tests="8", `ScheduleServiceShiftSnapshotTest` tests="21" (17 pre-existing + 4 new), `BusinessDateJoinGuardTest` tests="6" (unchanged).
9. `BusinessDateJoinGuardTest` — 6 tests, 0 failures, within the same unfiltered run. PASS.

**Before/after values recorded for every "same value as before this task" acceptance criterion:**

| Criterion | Before | After |
|---|---|---|
| `deskRepository` count in `ScheduleService.java` (Task 1, P-03 — no new Desk lookup) | 6 | 6 |
| `DayWindow.anchoredAt` count in `SolverService.java` (Task 2 — no second anchor source) | 2 | 2 |
| `findByTenantIdAndAgent_IdAndDateBetweenOrderByDateAsc\|findByTenantIdAndDeskIdAndDateBetween` count in `SolverService.java` (Task 2 — AgentDayOff/AgentException untouched) | 2 | 2 |
| `lastOnDay` count in `SolverService.java` (Task 3 — no logic change, comment only) | 3 | 3 |
| `desk.getDayStart()` count in `SolverService.java` (Task 2 — fewer than 3 required) | 2 | 4 |

**RED evidence observed for CR-01 sites 3-4 (Task 1, commit `6be0dcb`, before `BusinessDayPeriodLoader` existed):**
`./gradlew test --tests "com.wfm.service.ScheduleServiceShiftSnapshotTest"` — 21 tests, **2 failed**:
- `acceptSchedule_21_00Anchor_snapshotsAllTwentyFourTimeslotsOfTheLastBusinessDay`: `Expected size: 24 but was: 3`
- `acceptSchedule_21_00Anchor_snapshotsAllTwentyFourStaffingRequirementsOfTheLastBusinessDay`: `Expected size: 24 but was: 3`
- Falsification control and midnight control both passed (see Deviations for the nuance on the falsification control's own history).

**RED evidence for CR-01 sites 1-2 (Task 2) is structural, not behavioral** (as the plan itself anticipates): `BusinessDayPeriodLoaderTest` exercises the loader directly, built GREEN in Task 1, so it passed on first run. Before commit `0959c34`, `SolverService.java`'s comment-stripped source named both calendar-date finders directly (1 and 1); after, it names neither (0 and 0) — confirmed by the comment-stripped grep in the task commit message.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 20's four CR-01 sites and WR-01 are closed. `20-REVIEW.md`'s only two open findings (CR-01, WR-01) are both resolved; IN-01 (info-level) is also addressed.
- `BusinessDayPeriodLoader` is now the canonical reference implementation any future business-date-period load should mirror, alongside `TimeslotGeneratorService`'s BDAY-03 read-back.
- No blockers for Phase 21 (overnight shift labelling) or Phase 22 (minimum-rest horizons) — both were already scoped independently of this plan's sites.
- `bday-join-guard.md` now carries a complete, honest account of every blind spot found across plans 20-05 through 20-12; a future call site using a repository derived-query method name or JPQL date literal fed business-date arguments is a documented, named risk class rather than a rediscovery.

---
*Phase: 20-solver-business-date-correctness*
*Completed: 2026-10-02*

## Self-Check: PASSED

- All 6 key files confirmed present on disk via `[ -f ]`.
- All 5 task commit hashes (`6be0dcb`, `f1bf64d`, `ba85a00`, `0959c34`, `4a1edf0`) confirmed via `git log --oneline --all`.
- Re-ran plan-level `<verification>` steps 1-9: all PASS (full unfiltered suite 1217/1217, 0 failures, 0 errors, 4 skipped).
- Re-ran every task's `<acceptance_criteria>` grep/structural checks: all PASS, before/after values recorded above.
