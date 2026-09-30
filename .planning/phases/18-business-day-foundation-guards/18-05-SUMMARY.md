---
phase: 18-business-day-foundation-guards
plan: 05
subsystem: database
tags: [business-date, write-path-guard, real-flyway, testcontainers, structural-guard]

# Dependency graph
requires:
  - phase: 18-01
    provides: "desk.day_start column, Desk.dayStart, timeslot.business_date column, Timeslot.businessDate, V53 migration"
  - phase: 18-03
    provides: "TimeslotGeneratorService's re-authored business-date-cursor generation walk -- the settled DERIVING call site this guard pins"
provides:
  - "BusinessDateWritePathGuardTest -- BDAY-08's structural completeness guard, pinning the DERIVING writer set to {TimeslotGeneratorService} and the PROPAGATING (snapshot-copy) writer set to {ScheduleService}, asserted set-equal in both directions"
  - "bday-02-write-paths.md -- the parsed resource the guard enforces against, with every comment citing requirement IDs (SOLV-01, BDAY-04, OVNT-01) rather than phase/plan/decision-document identifiers"
  - "Real-Flyway proof that V53's two new columns (timeslot.business_date, desk.day_start) round-trip through a real Postgres 16 under ddl-auto: validate, via MidnightTimeslotPostgresTest"
affects: [20-solver-business-date, 21-overnight-shift-templates]

# Actuals (#2632) -- pairs with the plan's estimate to calibrate future estimates.
actuals:
  tokens: 6087
  tasks: 2
  commits: 2
  plan_head_before: 4fa7a22c9b97bcefd9b3b2cf3f1f0b21f80b9f4e

tech-stack:
  added: []
  patterns:
    - "Salvaged-commit landing: 7d42f23's guard and resource copied verbatim apart from D-24's rewrite pass -- zero logic/assertion changes, only comment citations rewritten from phase numbers, plan numbers, task identifiers and decision-document identifiers to requirement IDs"
    - "Two-directional set-equality allowlist idiom extended a fourth time in this codebase (after midnight-time-arithmetic.md, ushf-05-write-paths.md, and this guard's own prior incarnation) -- containsExactlyInAnyOrderElementsOf, never isSubsetOf/containsAnyOf/bare .contains(...)"
    - "DERIVING vs. PROPAGATING classified structurally by whether the setBusinessDate(...) argument is itself a .getBusinessDate() read, not by which class calls it"
    - "Postgres round-trip proof pattern: entityManager.flush() then entityManager.clear() then entityManager.find(...) -- a fresh read through a cleared persistence context, not a re-assertion of the in-memory object the service returned"

key-files:
  created:
    - src/test/java/com/wfm/service/BusinessDateWritePathGuardTest.java
    - src/test/resources/bday-02-write-paths.md
  modified:
    - src/test/java/com/wfm/repository/MidnightTimeslotPostgresTest.java

key-decisions:
  - "Landed 7d42f23's salvaged BusinessDateWritePathGuardTest/bday-02-write-paths.md verbatim apart from D-24's rewrites, exactly as the plan directed -- no assertion logic changed, only comment citations (SOLV-01 for the solver joins, BDAY-04 for DayWindow re-anchoring, OVNT-01 for overnight templates) and the resource's Generation-row prose, updated to describe the shipped 18-03 generator (writes the business-day cursor, not a literal calendar-date copy) rather than the interim expression the salvaged commit predates."
  - "Confirmed at copy time, via a direct grep of src/main/java, that exactly two setBusinessDate call sites exist in production code: TimeslotGeneratorService.java:160 (ts.setBusinessDate(businessDate), DERIVING -- the argument is the loop's own business-date cursor, not a read of another row) and ScheduleService.java:328 (snapshot.setBusinessDate(live.getBusinessDate()), PROPAGATING). No third writer found; the stop-and-report branch in the plan's action text was not triggered."
  - "Task 2's business-date round-trip assertions were added to the existing generatesAFullMidnightEndingDay test (not a new test method), and exactly one new test method was added for the desk day-start default -- per the plan's literal action text ('In the full-midnight-ending-day test...' / 'Add one test...'). This yields 6 total test methods (5 pre-existing + 1 new), matching the plan's own acceptance_criteria ('at least 6 -- the five pre-existing behaviours plus the desk-default one') even though the plan's <verify> step and its plan-level <verification> item both separately state a higher, self-inconsistent figure of 'seven' / 'at least seven tests' (see Deviations)."

requirements-completed: [BDAY-08, BDAY-02]

coverage:
  - id: D1
    description: "BusinessDateWritePathGuardTest pins business_date to exactly two named writers -- DERIVING: TimeslotGeneratorService, PROPAGATING: ScheduleService -- asserted set-equal in BOTH directions via containsExactlyInAnyOrderElementsOf, never a subset/containment check"
    requirement: "BDAY-08"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/BusinessDateWritePathGuardTest.java (8 tests: both set-equality assertions, non-vacuity check, scan-sees-production-source check, fresh-occurrence-detection check, missing-heading-throws check, empty-allowlist-rejected check, deliberately-broken-allowlist-detected check)"
        status: pass
      - kind: other
        ref: "./gradlew test --tests \"com.wfm.service.BusinessDateWritePathGuardTest\" -> BUILD SUCCESSFUL; build/test-results/test/TEST-com.wfm.service.BusinessDateWritePathGuardTest.xml -> tests=8 skipped=0 failures=0 errors=0"
        status: pass
    human_judgment: false
  - id: D2
    description: "V53 is executed by real Flyway against a real Postgres 16 under ddl-auto: validate, and both new columns (timeslot.business_date, desk.day_start) round-trip through the database via a fresh, cleared-entity-manager read, not an in-memory re-assertion"
    requirement: "BDAY-02"
    verification:
      - kind: integration
        ref: "src/test/java/com/wfm/repository/MidnightTimeslotPostgresTest.java (6 tests against real Postgres via Testcontainers, including the two round-trip additions)"
        status: pass
      - kind: other
        ref: "./gradlew test --tests \"com.wfm.repository.MidnightTimeslotPostgresTest\" -> BUILD SUCCESSFUL; build/test-results/test/TEST-com.wfm.repository.MidnightTimeslotPostgresTest.xml -> tests=6 skipped=0 failures=0 errors=0"
        status: pass
    human_judgment: false
  - id: D3
    description: "A Postgres-backed test run is proven to have actually executed rather than silently skipped -- the JUnit XML report's skipped=0 attribute is asserted directly, closing the exact failure mode the V39 incident exposed (migration applies cleanly, app fails to boot under validate, 402 tests stay green)"
    requirement: "BDAY-02"
    verification:
      - kind: other
        ref: "grep -oE 'tests=\"[0-9]+\" skipped=\"[0-9]+\"' build/test-results/test/TEST-com.wfm.repository.MidnightTimeslotPostgresTest.xml -> tests=\"6\" skipped=\"0\""
        status: pass
    human_judgment: false
  - id: D4
    description: "Every comment in the guard and its resource cites requirement IDs (SOLV-01, BDAY-04, OVNT-01, BDAY-02, BDAY-01) rather than phase numbers, plan numbers, task identifiers or decision-document identifiers (D-24)"
    requirement: "BDAY-08"
    verification:
      - kind: other
        ref: "grep -cE '\\bD-[0-9]+\\b|\\bT-18-[0-9]+\\b' on both new files -> 0; grep -cE 'Phase [0-9]+|Plan 18-[0-9]+' on both new files -> 0; grep -cE 'SOLV-01' on the resource -> 2 (at least 1 required)"
        status: pass
    human_judgment: false

duration: ~30min
completed: 2026-09-30
status: complete
---

# Phase 18 Plan 5: Business-Date Write-Path Guard & Real-Flyway V53 Proof Summary

**BusinessDateWritePathGuardTest pins `business_date` to exactly two named writers (DERIVING: `TimeslotGeneratorService`, PROPAGATING: `ScheduleService`) with two-directional set equality, and `MidnightTimeslotPostgresTest` proves V53's two new columns round-trip through real Flyway on real Postgres 16 under `ddl-auto: validate`.**

## Performance

- **Duration:** ~30 min
- **Completed:** 2026-09-30
- **Tasks:** 2 (both `type="auto"`)
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments

- `BusinessDateWritePathGuardTest` (new, 352 lines, landed verbatim from salvaged commit `7d42f23` apart from D-24's comment rewrites) scans `src/main/java` for calls to `Timeslot#setBusinessDate`, classifies each by whether its argument reads another row's business date (PROPAGATING) or not (DERIVING), and asserts each classified set equals its own fenced allowlist in `bday-02-write-paths.md` via `containsExactlyInAnyOrderElementsOf` -- never weakened to a subset or containment check. All 8 tests pass, including the five guard-the-guard proofs (non-vacuity, scan-sees-production-source, fresh-occurrence-detection, missing-heading-throws, deliberately-broken-allowlist-detected).
- `bday-02-write-paths.md` (new, the parsed resource) carries the two fenced allowlists the build actually enforces -- `com.wfm.service.TimeslotGeneratorService` under the DERIVING heading, `com.wfm.service.ScheduleService` under the PROPAGATING heading -- plus a human-readable table and explanation. Every comment cites requirement IDs (`SOLV-01` for the solver joins this guard protects, `BDAY-04` for the re-anchoring that will first make business dates diverge from calendar dates, `OVNT-01` for overnight templates) instead of the salvaged commit's stale phase numbers, plan number, and decision-document identifiers.
- **Scan confirmed no third writer exists.** A direct grep of `src/main/java` for `setBusinessDate` found exactly two call sites: `TimeslotGeneratorService.java:160` (`ts.setBusinessDate(businessDate)`, DERIVING) and `ScheduleService.java:328` (`snapshot.setBusinessDate(live.getBusinessDate())`, PROPAGATING) -- both already correctly classified by the guard and matching its allowlists exactly.
- `MidnightTimeslotPostgresTest`'s `generatesAFullMidnightEndingDay` test now clears the entity manager after the flush and re-finds a persisted timeslot through a fresh read: its `business_date` is non-null and equals its calendar date, and every row created for the requested day carries that day as its business date -- a real round trip through Postgres, not a re-assertion of the in-memory object `generateTimeslots` returned.
- One new test, `deskSavedWithoutDayStart_readsBackMidnight`, proves the desk-side column: a desk saved without setting a day start, re-read through a cleared entity manager, reads back `00:00` -- the V53 SQL column default and `Desk.dayStart`'s Java-side default agree on real Postgres.
- The JUnit XML report for `MidnightTimeslotPostgresTest` confirms the run actually executed: `tests="6" skipped="0" failures="0" errors="0"` -- the exact assertion this task exists to make, since `disabledWithoutDocker` would otherwise let an absent Docker daemon silently skip the class and report a vacuous green (the V39 failure mode).
- No new Postgres-backed test class added (3 before this task, 3 after) and `build.gradle` untouched -- both confirmed by direct diff.
- Full suite green: 1105 tests, 0 failures, 0 errors, 4 pre-existing skips (the same two disabled benchmark classes noted in every prior plan's summary this phase).

## Task Commits

Each task was committed atomically:

1. **Task 1: Pin business_date to two named writers with a set-equality guard** -- `d699a41` (test)
2. **Task 2: Run V53 through real Flyway on real Postgres under ddl-auto: validate** -- `df0bb17` (test)

**Plan metadata:** commit pending (this SUMMARY + STATE.md/ROADMAP.md/REQUIREMENTS.md update)

## Files Created/Modified

- `src/test/java/com/wfm/service/BusinessDateWritePathGuardTest.java` (new) -- the structural write-path guard, landed from `7d42f23` with D-24's comment rewrites applied
- `src/test/resources/bday-02-write-paths.md` (new) -- the parsed allowlist resource, same treatment
- `src/test/java/com/wfm/repository/MidnightTimeslotPostgresTest.java` (modified) -- business-date round-trip assertions added to the existing midnight-ending-day test, plus one new desk day-start default test

## Shipped Allowlist Contents (as required by this plan's `<output>`)

**DERIVING** (`### Timeslot#setBusinessDate call sites`):
```
com.wfm.service.TimeslotGeneratorService
```

**PROPAGATING** (`### Timeslot#setBusinessDate call sites -- snapshot-copy (propagating, not deriving)`):
```
com.wfm.service.ScheduleService
```

No third business-date writer was found by the scan.

## JUnit XML Report Counts (as required by this plan's `<output>`)

- `BusinessDateWritePathGuardTest`: `tests="8" skipped="0" failures="0" errors="0"`
- `MidnightTimeslotPostgresTest`: `tests="6" skipped="0" failures="0" errors="0"`

## Decisions Made

- Landed the salvaged guard and resource verbatim apart from D-24's rewrite pass, per the plan's explicit instruction -- no assertion logic changed anywhere in either file.
- Confirmed via direct source grep (not just trusting the salvaged commit) that exactly two writers exist in the current tree and both classify correctly; this satisfied the plan's "verify at copy time" instruction without triggering its stop-and-report branch.
- Business-date round-trip assertions were added to the *existing* `generatesAFullMidnightEndingDay` test rather than split into a new test method, and exactly one new test method (`deskSavedWithoutDayStart_readsBackMidnight`) was added -- both choices taken directly from the plan's literal `<action>` wording ("In the full-midnight-ending-day test..." / "Add one test..."), yielding 6 total test methods rather than 7 (see Deviations for the resulting plan-text inconsistency and its resolution).

## Deviations from Plan

### Auto-fixed Issues

None -- no bugs, missing functionality, or blocking issues were encountered during implementation.

### Plan-text inconsistency (not a code deviation, documented for the verifier)

**1. Task 2's `<verify>` step and the plan-level `<verification>` item both state "seven" / "at least seven tests" for `MidnightTimeslotPostgresTest`, which contradicts the task's own `<action>` text and its own `<acceptance_criteria>`**
- **Found during:** Task 2 acceptance-criteria verification
- **Issue:** Task 2's `<action>` explicitly instructs adding the business-date round-trip assertions "In the full-midnight-ending-day test" (i.e. to the EXISTING test method, not a new one) and to "Add one test" (singular) for the desk-side day-start default. That produces 5 pre-existing + 1 new = 6 total test methods -- exactly what Task 2's own `<acceptance_criteria>` states verbatim: "`grep -c '@Test' ...` reports at least `6` -- the five pre-existing behaviours plus the desk-default one." However, Task 2's `<verify>` block's `<fails_when>` clause separately states a "tests count lower than `7`" fails ("the five pre-existing behaviours plus the two added here"), and the plan-level `<verification>` item 4 repeats "at least seven tests." These two stated counts (6 vs. 7) are internally inconsistent with the `<action>` and `<acceptance_criteria>`, which unambiguously call for exactly 1 new test method, not 2.
- **Resolution:** Followed the more specific, self-consistent instructions -- the literal `<action>` text and the `<acceptance_criteria>` HARD GATE -- rather than padding the suite with an unneeded 7th test method solely to satisfy an internally-contradictory `<verify>`/`<verification>` comment. `grep -c '@Test'` on the modified file reports `6`, satisfying the stated acceptance criterion exactly, and the actual JUnit XML report shows `tests="6" skipped="0"`.
- **Files modified:** none (no code change; this is a plan-text observation, recorded here rather than silently resolved)
- **Verification:** all of Task 2's literal `<acceptance_criteria>` pass; `skipped="0"` holds regardless of which total-test-count figure is used
- **Committed in:** n/a (documentation-only finding)

---

**Total deviations:** 0 code deviations (0 auto-fixed). 1 plan-text inconsistency (test-count arithmetic in `<verify>`/plan-level `<verification>` vs. `<action>`/`<acceptance_criteria>`) noted and resolved by deferring to the more specific, internally-consistent acceptance criteria.
**Impact on plan:** None on correctness or scope. All stated acceptance criteria pass, the guard is proven able to go red (both directions), V53 round-trips through real Flyway on real Postgres with zero skips, and no weakened assertion or missing requirement-ID citation exists anywhere in the two touched/created test files.

## Issues Encountered

None beyond the plan-text inconsistency documented above.

## User Setup Required

None -- no external service configuration required.

## Next Phase Readiness

- BDAY-08's write-path guard is live and proven able to go red in both directions; BDAY-02's `business_date` column is now proven, by a real-Flyway-on-real-Postgres round trip, to persist and reload correctly, closing this phase's one remaining migration-verification gap.
- The DERIVING allowlist names only `TimeslotGeneratorService` and the PROPAGATING allowlist names only `ScheduleService` -- both correct after Plan 18-03's generator re-authoring, since the call site is still exactly one line inside the generator with only its right-hand-side expression changed. Phase 19 (`DayWindow` re-anchoring, BDAY-04) and Phase 20/21 (solver business-date joins, overnight templates) now inherit a build that fails loudly if either adds a second business-date writer without updating the allowlist.
- Plan 18-06 (BDAY-05) is this phase's remaining plan per `18-PATTERNS.md`'s roadmap.
- Full suite green at 1105 tests (up from 1096 before this plan), 0 failures, 0 errors, 4 pre-existing benchmark skips (unrelated, unchanged).

---
*Phase: 18-business-day-foundation-guards*
*Completed: 2026-09-30*

## Self-Check: PASSED

- `src/test/java/com/wfm/service/BusinessDateWritePathGuardTest.java` - FOUND
- `src/test/resources/bday-02-write-paths.md` - FOUND
- `src/test/java/com/wfm/repository/MidnightTimeslotPostgresTest.java` - FOUND (modified)
- Commit `d699a41` - FOUND in `git log --oneline --all`
- Commit `df0bb17` - FOUND in `git log --oneline --all`
- All task-level `<acceptance_criteria>` re-verified: Task 1's 9 checks all pass (containsExactlyInAnyOrderElementsOf count 3, weakened-assertion grep 0, @Test count 8, SOURCE_ROOT grep 1, allowlist fence counts 1/1/2, D-nn/T-18-nn grep 0/0, Phase/Plan grep 0/0, SOLV-01 grep 2); Task 2's checks all pass (extends PostgresBackedTest 1, entityManager.clear() 4, getBusinessDate() 2, getDayStart() 1, @Test count 6, Postgres-class count unchanged at 3, decision-ID diff grep 0) -- see Deviations for the one plan-text count inconsistency (6 vs. the plan's stated 7), resolved in favor of the literal acceptance_criteria
- Plan-level `<verification>` re-run: `./gradlew test` green (1105/1105 effectively 1101 passing + 4 pre-existing skips, 0 failures/errors); `BusinessDateWritePathGuardTest` both allowlists set-equal with no weakened assertion; neither new/modified file cites a phase number, plan number, task identifier, or decision-document identifier; `MidnightTimeslotPostgresTest`'s JUnit XML shows `skipped="0"` (test count 6, not 7 -- see Deviations); `build.gradle` diff empty; Postgres-backed test class count unchanged at 3
