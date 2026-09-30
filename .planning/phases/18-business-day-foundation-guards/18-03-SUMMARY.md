---
phase: 18-business-day-foundation-guards
plan: 03
subsystem: database
tags: [timeslot-generation, day-window, business-date, timefold-unrelated-arithmetic]

# Dependency graph
requires:
  - phase: 18-01
    provides: "desk.day_start column, Desk.dayStart, timeslot.business_date column, Timeslot.businessDate"
  - phase: 18-02
    provides: "unconditional accepted-schedule refusal, day_start disclosure, DECLARED_TABLES reconciliation"
provides:
  - "DayWindow.startMinuteFromDayStart / endMinuteFromDayStart / timeAtDayStartOffset / businessDateOf / calendarDateAtDayStartOffset -- five additive day-start-aware functions, proven exhaustively equivalent to their midnight-implicit counterparts at a 00:00 anchor"
  - "@Deprecated on DayWindow's nine midnight-implicit statics, naming BDAY-04 and the anchored replacement; -Xlint:deprecation makes the remaining call-site count a compiler-enumerated live count (89 DayWindow-specific warnings)"
  - "TimeslotGeneratorService.generateTimeslots takes a dayStart 4th positional parameter, refuses a non-tiling anchor before any repository call (requireDayStartTiles), walks BUSINESS dates deriving each slot's calendar date/times through DayWindow, and its closing read-back widens by one day and filters on the derived business date"
  - "Direct unit proof (TimeslotGeneratorBusinessDateTest) that a 21:00-anchored desk generates a contiguous 24-hour business day spanning two calendar dates with one business date"
affects: [19-daywindow-reanchoring, 20-solver-business-date]

# Actuals (#2632) — pairs with the plan's estimate to calibrate future estimates.
actuals:
  tokens: 17119
  tasks: 3
  commits: 4
  plan_head_before: 5aad68cc658e5cac740d1ad0ff4d54f94a3ad9df

tech-stack:
  added: []
  patterns:
    - "Day-start-aware vocabulary lives alongside the midnight-implicit vocabulary in DayWindow, separated by a section banner; the anchored block collapses onto the midnight-implicit block exactly at a 00:00 anchor, proven exhaustively over all 1440 minutes rather than sampled"
    - "-Xlint:deprecation as the mechanism that makes a deprecation-driven migration checklist a compiler-enumerated, always-current count rather than a planning-time grep"
    - "Generation walk iterates business dates and derives calendar dates via DayWindow.calendarDateAtDayStartOffset, never the reverse -- avoids manufacturing partial business days at period edges"
    - "isDesired classifies an existing row by DERIVING its business date from the row's own calendar date and start time (DayWindow.businessDateOf) -- it never reads the stored business_date column, so the classifier and the future solver-join reader can never disagree by construction"
    - "Real-service-under-test with mocked repositories (no test double of the generator itself) is the required shape for a direct behavioral proof of generation logic -- D-19's explicit rejection of proving properties against throwaway doubles"

key-files:
  created:
    - src/test/java/com/wfm/service/TimeslotGeneratorBusinessDateTest.java
  modified:
    - src/main/java/com/wfm/util/DayWindow.java
    - src/test/java/com/wfm/util/DayWindowTest.java
    - build.gradle
    - src/main/java/com/wfm/service/TimeslotGeneratorService.java
    - src/main/java/com/wfm/controller/TimeslotController.java
    - src/main/java/com/wfm/service/FteUploadService.java
    - src/test/java/com/wfm/repository/MidnightTimeslotPostgresTest.java
    - src/test/java/com/wfm/service/TimeslotGeneratorServiceTest.java
    - src/test/java/com/wfm/service/MidnightWindowSeamTest.java

key-decisions:
  - "Task 3 (tdd=true) carries zero src/main files by design -- it is a direct-proof task over behaviour task 2 already implemented and verified, not new-feature TDD. Documented explicitly rather than forced into an artificial RED phase."
  - "requireDayStartTiles reasons via DayWindow.startMinute(dayStart) (the deprecated plain minute-of-day reading), matching the salvaged 985e365 analog verbatim -- the anchor's OWN minute-of-day is what's being range-checked, not a business-day-relative offset, so the midnight-implicit function is the semantically correct one to call here despite being deprecated. Contributes to the expected one-phase-noisy build."
  - "The closing read-back's business-date filter/sort is applied in-memory via a Java Stream, not a second repository query -- the repository finder stays unchanged (widened upper bound only), keeping BDAY-08's DERIVING-writer allowlist accurate with no new repository method."

requirements-completed: [BDAY-03, BDAY-02]

coverage:
  - id: D1
    description: "DayWindow gains five additive day-start-aware functions, proven byte-equivalent to their midnight-implicit counterparts at a 00:00 anchor across all 1440 minutes of the day"
    requirement: "BDAY-03"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/util/DayWindowTest.java#AnchoredEquivalenceAtMidnightAnchor (4 tests, each looping all 1440 minutes)"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/util/DayWindowTest.java#AnchoredBehaviorAt2100 (5 tests)"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/util/DayWindowTest.java#RoundTripConsistency (2 tests, each across 4 anchors)"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/util/DayWindowTest.java#FailLoudlyAtTheDayStartBoundary (5 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "All nine midnight-implicit DayWindow statics carry @Deprecated naming BDAY-04; -Xlint:deprecation makes the remaining call-site count compiler-enumerated (89 DayWindow-specific warnings on a clean recompile)"
    requirement: "BDAY-03"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/util/DayWindowTest.java#DeprecationIsLive#deprecationMatchesTheMidnightImplicitSetExactly"
        status: pass
      - kind: other
        ref: "./gradlew compileJava compileTestJava --rerun-tasks | grep -c 'in DayWindow has been deprecated' -> 89"
        status: pass
    human_judgment: false
  - id: D3
    description: "generateTimeslots takes the desk anchor, refuses a non-tiling anchor before touching the database, and walks business days emitting derived calendar dates"
    requirement: "BDAY-03"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/TimeslotGeneratorServiceTest.java#RequireDayStartTiles (4 tests)"
        status: pass
      - kind: other
        ref: "./gradlew test (full suite, 1074 tests, 0 failures/errors)"
        status: pass
    human_judgment: false
  - id: D4
    description: "A 21:00-anchored desk provably yields one contiguous 24-hour business day across two calendar dates with a single business date, proven against the REAL generator (D-19), including the returned list (not only the captured save)"
    requirement: "BDAY-03"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/TimeslotGeneratorBusinessDateTest.java#SingleBusinessDayAt2100 (7 tests)"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/TimeslotGeneratorBusinessDateTest.java#ThreeBusinessDaysAt2100#threeBusinessDatesFourCalendarDates"
        status: pass
    human_judgment: false
  - id: D5
    description: "Every existing behaviour at a 00:00 anchor is unchanged: isDesired's full existing case suite still passes with the anchor threaded through, and MidnightTimeslotPostgresTest's five behaviours (including regeneration idempotency and the tiling refusal) pass against real Postgres"
    requirement: "BDAY-02"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/TimeslotGeneratorServiceTest.java (15 tests, up from 9 before this plan -- no case dropped)"
        status: pass
      - kind: integration
        ref: "src/test/java/com/wfm/repository/MidnightTimeslotPostgresTest.java (5 tests against real Postgres via Testcontainers)"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/TimeslotGeneratorBusinessDateTest.java#MidnightAnchorInvariance#businessDateEqualsCalendarDateAtMidnightAnchor"
        status: pass
    human_judgment: false

duration: 38min
completed: 2026-09-30
status: complete
---

# Phase 18 Plan 3: Day-Start-Aware DayWindow Arithmetic & Anchored Timeslot Generation Summary

**Five additive DayWindow functions plus a rewritten TimeslotGeneratorService generation walk prove, on the real service, that a 21:00-anchored desk yields a contiguous 24-hour business day across two calendar dates with one business date -- while every existing behaviour at 00:00 stays byte-identical across 1074 passing tests.**

## Performance

- **Duration:** 38 min
- **Started:** 2026-09-30T15:19:43Z
- **Completed:** 2026-09-30T15:57:56Z
- **Tasks:** 3 (1 TDD, 1 auto, 1 TDD-tagged direct-proof)
- **Files modified:** 10 (1 created, 9 modified)

## Accomplishments

- `DayWindow` gains five additive day-start-aware functions (`startMinuteFromDayStart`, `endMinuteFromDayStart`, `timeAtDayStartOffset`, `businessDateOf`, `calendarDateAtDayStartOffset`), proven exhaustively equivalent to their midnight-implicit counterparts at a `00:00` anchor across all 1440 minutes -- not sampled points
- All nine existing `DayWindow` statics carry `@Deprecated` naming BDAY-04; `build.gradle` adds `-Xlint:deprecation` so the remaining call-site count is compiler-enumerated (89 `DayWindow`-specific deprecation warnings on a clean recompile) rather than a planning-time grep that goes stale
- `TimeslotGeneratorService.generateTimeslots` takes the desk's `dayStart` as its 4th positional parameter, refuses a non-tiling anchor via `requireDayStartTiles` before any tenant lookup or repository call, walks business dates (not calendar dates) as its outer loop cursor, and derives each slot's calendar date/times through `DayWindow` with zero local minute arithmetic
- `isDesired` classifies an existing row by DERIVING its business date via `DayWindow.businessDateOf` -- it never reads the stored `business_date` column, keeping SOLV-01 (Phase 20) the first consumer of that stored value
- The closing read-back widens its calendar upper bound by one day and filters/orders on the derived business date, so a 21:00-anchored desk's post-midnight rows are never silently under-returned
- `TimeslotGeneratorBusinessDateTest` (new) proves the claim directly against the real service: 24 contiguous rows across two calendar dates for one 21:00-anchored business day, 72 rows across four calendar dates for three business days, and business-date == calendar-date invariance at a `00:00` anchor
- Full suite green: 1074 tests, 0 failures, 0 errors (4 pre-existing benchmark skips, unrelated), including `MidnightTimeslotPostgresTest`'s 5 tests against real Postgres

## TDD Gate Compliance

Two tasks carried `tdd="true"`.

**Task 1 (DayWindow arithmetic) -- full RED-GREEN cycle:**
- **RED** (`test(18-03)`, `11b8c58`): `DayWindowTest` extended with the exhaustive equivalence, 21:00-anchor, round-trip, boundary, and deprecation-reflection tests, against RED-phase stub methods (`throw new UnsupportedOperationException`) added to `DayWindow`. Genuine RED evidence: `./gradlew test --tests "com.wfm.util.DayWindowTest"` reported 52 tests completed, 17 failed -- every new test failed on either the stub exception or the not-yet-`@Deprecated` reflection assertion; all 35 pre-existing tests stayed green.
- **GREEN** (`feat(18-03)`, `b35eb1d`): the five functions implemented for real, `@Deprecated` added to the nine existing statics, `-Xlint:deprecation` added to `build.gradle`. `./gradlew test --tests "com.wfm.util.DayWindowTest"` -> all 52 tests pass.
- **REFACTOR:** none needed; no `refactor(18-03)` commit.

**Task 3 (BDAY-03 direct proof) -- test-only by design, documented explicitly:**
- This task's own `<files>` list in the plan names **only test files** (`TimeslotGeneratorBusinessDateTest.java`, `TimeslotGeneratorServiceTest.java`) -- no `src/main` file. It is a direct-proof task over behaviour Task 2 (`type="auto"`, not TDD) already implemented and verified with the full suite green, not new-feature TDD.
- Because the production logic already existed and was already correct when these tests were written, every new test passed on its first run (`./gradlew test --tests "com.wfm.service.TimeslotGeneratorBusinessDateTest" --tests "com.wfm.service.TimeslotGeneratorServiceTest"` -> BUILD SUCCESSFUL, no failures at any point). This is the expected and correct outcome for a proof task following its own implementation task in the same plan, not a "feature already exists" red flag (tdd.md's Fail-Fast Rule 1) -- the feature legitimately already exists, by this plan's own design (Task 2 built it, Task 3 proves it).
- One `test(18-03)` commit (`8e48eb9`) carries these tests; no accompanying `feat(18-03)` commit exists for Task 3, since no production code changed.
- The automated `gsd_run check tdd-red-evidence` gate is Node/TAP-output-specific (`# tests N` / `# pass N` / TAP `ok`/`not ok` lines) and has no adapter for Gradle/JUnit5 XML output in this project; combined with `workflow.tdd_mode: false` in `.planning/config.json` (the plan-level strict-gate enforcement this verb backs is not active project-wide), RED-phase evidence for Task 1 was verified directly from the Gradle test report (named failing tests, exit code, pass/fail counts) rather than through that tool.

**Gate commit check** (`tdd.md`'s Executor Gate Validation, run against `18-03`): `test(18-03):` commits found (`11b8c58`, `8e48eb9`); `feat(18-03):` commits found (`b35eb1d`, `425ffd1`). Both gates present.

## Task Commits

Each task was committed atomically:

1. **Task 1a: RED -- failing DayWindow tests** - `11b8c58` (test)
2. **Task 1b: GREEN -- DayWindow anchored arithmetic + deprecation** - `b35eb1d` (feat)
3. **Task 2: Anchor generateTimeslots on the desk day-start** - `425ffd1` (feat)
4. **Task 3: Direct proof of BDAY-03 on the real generator** - `8e48eb9` (test)

**Plan metadata:** commit pending (this SUMMARY + STATE.md/ROADMAP.md/REQUIREMENTS.md update)

## Files Created/Modified

- `src/main/java/com/wfm/util/DayWindow.java` - five additive anchored functions, `@Deprecated` on the nine midnight-implicit statics, extended class javadoc
- `src/test/java/com/wfm/util/DayWindowTest.java` - exhaustive equivalence, 21:00-anchor, round-trip, boundary and deprecation-reflection test suites
- `build.gradle` - `tasks.withType(JavaCompile)` adds `-Xlint:deprecation`
- `src/main/java/com/wfm/service/TimeslotGeneratorService.java` - `dayStart` 4th positional parameter, `requireDayStartTiles`, business-date-cursor generation walk, anchored `isDesired`/`timeslotsMatch`, widened+filtered closing read-back
- `src/main/java/com/wfm/controller/TimeslotController.java` - passes `LocalTime.MIDNIGHT` as the new `dayStart` argument
- `src/main/java/com/wfm/service/FteUploadService.java` - passes `LocalTime.MIDNIGHT` as the new `dayStart` argument
- `src/test/java/com/wfm/repository/MidnightTimeslotPostgresTest.java` - six call sites updated for the new `dayStart` parameter
- `src/test/java/com/wfm/service/TimeslotGeneratorServiceTest.java` - `desired(...)` wrapper gains an anchor parameter (defaulting to `00:00`), new `RequireDayStartTiles` and `AnchoredAt2100` nested classes (9 -> 15 `@Test` methods)
- `src/test/java/com/wfm/service/MidnightWindowSeamTest.java` - minimal Rule-3 compile fix threading `LocalTime.MIDNIGHT` through its `isDesired` call (not in this plan's file list; needed to compile after `isDesired`'s signature changed)
- `src/test/java/com/wfm/service/TimeslotGeneratorBusinessDateTest.java` (new) - direct proof of BDAY-03 against the real `TimeslotGeneratorService` with mocked repositories

## Decisions Made

- Task 3's zero-`src/main`-files design is treated as an intentional direct-proof task, not a TDD violation -- documented explicitly in "TDD Gate Compliance" above rather than forcing an artificial RED phase against already-correct code.
- `requireDayStartTiles` calls the deprecated `DayWindow.startMinute(dayStart)` deliberately (matching the salvaged `985e365` analog): it needs the anchor's own plain minute-of-day, not a business-day-relative offset, so the midnight-implicit function is semantically correct here despite the deprecation -- contributes to D-21's expected one-phase-noisy build.
- The closing read-back's business-date filter and sort are applied in-memory via a `Stream`, not a second repository query, keeping BDAY-08's DERIVING-writer allowlist unchanged (no new repository method).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `TimeslotGeneratorServiceTest.java` and `MidnightWindowSeamTest.java` needed minimal compile fixes not in Task 2's file list**
- **Found during:** Task 2 (full-suite verification, `./gradlew compileTestJava`)
- **Issue:** Task 2's `<files>` list for `TimeslotGeneratorService.java` did not include `TimeslotGeneratorServiceTest.java` or `MidnightWindowSeamTest.java`, but both call `TimeslotGeneratorService.isDesired(...)` directly, and Task 2's own `<verify>` requires the full `./gradlew test` suite to compile and pass. Once `isDesired` gained its `dayStart` first parameter, both files failed to compile.
- **Fix:** Added `LocalTime.MIDNIGHT` as the first argument to the `isDesired` calls in both files' existing wrapper methods -- a minimal, behavior-preserving compile fix. Task 3 (which does list `TimeslotGeneratorServiceTest.java`) later did the real rework: threading a genuine anchor parameter through with a `00:00` default.
- **Files modified:** `src/test/java/com/wfm/service/TimeslotGeneratorServiceTest.java`, `src/test/java/com/wfm/service/MidnightWindowSeamTest.java`
- **Verification:** `./gradlew compileJava compileTestJava` clean; full suite green (1059 tests after Task 2, 1074 after Task 3)
- **Committed in:** `425ffd1` (Task 2 commit)

**2. [Rule 1 - Bug] Two planned inline decision-ID (`D-nn`) citations leaked into new comments during drafting**
- **Found during:** Task 2 and Task 3 acceptance-criteria verification (the explicit `grep -cE '^\+.*\bD-[0-9]+\b'` check)
- **Issue:** Several new comments in `TimeslotGeneratorService.java` cited `D-08`/`D-22`/`D-12` directly (a first-draft habit carried over from reading `18-CONTEXT.md`'s decision numbering while writing the reasoning), and a `TimeslotGeneratorServiceTest.java` `@DisplayName` literally contained the substring `D-1` (from "business day D-1"), which the acceptance criterion's grep pattern also flags. D-24 requires every new/modified comment to cite requirement IDs, never decision-document identifiers, and never a bare phase number.
- **Fix:** Reworded all flagged comments to cite `BDAY-02`/`BDAY-03`/`BDAY-08` instead of `D-nn`, and reworded the `@DisplayName` to say "the PREVIOUS business day" instead of "D-1".
- **Files modified:** `src/main/java/com/wfm/service/TimeslotGeneratorService.java`, `src/test/java/com/wfm/service/TimeslotGeneratorServiceTest.java`
- **Verification:** `git diff <base> -- src/main src/test | grep -cE '^\+.*\bD-[0-9]+\b'` prints `0` for both tasks' diffs
- **Committed in:** `425ffd1` (Task 2), `8e48eb9` (Task 3)

**3. [Rule 1 - Bug] `build.gradle` comment's own prose contained the literal substring "Werror", tripping its own acceptance criterion**
- **Found during:** Task 1 acceptance-criteria verification
- **Issue:** The inline comment explaining why `-Werror` was NOT added spelled out `-Werror` literally, which the acceptance criterion's own naive `grep -c 'Werror'` (expecting `0`) then matched against the explanatory comment itself, not an actual flag.
- **Fix:** Reworded the comment to describe the absent flag ("no flag turning warnings into build failures") without spelling out its literal name.
- **Files modified:** `build.gradle`
- **Verification:** `grep -c 'Werror' build.gradle` prints `0`
- **Committed in:** `b35eb1d` (Task 1 GREEN commit)

---

**Total deviations:** 4 auto-fixed (1 Rule 3 blocking compile fix affecting two files outside the plan's stated scope, 3 Rule 1 bugs in comment/display-name wording tripping the plan's own literal acceptance criteria)
**Impact on plan:** All auto-fixes are either minimal compile-compatibility shims later superseded by the plan's own follow-on task (deviation 1) or wording corrections with zero behavior change (deviations 2-3). No scope creep; no weakening of any guard, assertion, or the phase's provable-no-op claim.

## Issues Encountered

None beyond the four deviations documented above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `DayWindow`'s day-start-aware vocabulary and `TimeslotGeneratorService`'s anchored generation walk are both in place; full suite green at 1074 tests.
- Nothing in this plan changed what value reaches `generateTimeslots`' `dayStart` parameter in production -- `DeskService.setDayStart`'s `00:00`-only gate (18-01/18-02) still makes any other anchor unreachable through the API. This phase is the arithmetic and generation-logic foundation; Phase 19 (`DayWindow Re-anchoring`, BDAY-04) is what re-points the ~89 remaining midnight-implicit call sites this plan's `-Xlint:deprecation` flag now enumerates, and removes the `00:00`-only gate.
- Plans 18-04/18-05/18-06 (this phase's remaining plans) are next per `18-PATTERNS.md`'s roadmap: `MidnightTimeArithmeticGuardTest`'s comparison-operator extension (D-25), `BusinessDateWritePathGuardTest`, BDAY-06's constructed regression suite, and the real-Flyway V53 proof are not yet done.
- `TimeslotGeneratorService.requireDayStartTiles` and the five `DayWindow` anchored functions are package-private/public statics respectively, directly unit-testable without Spring context -- confirmed reusable by design for Phase 19/20's own tests.

---
*Phase: 18-business-day-foundation-guards*
*Completed: 2026-09-30*

## Self-Check: PASSED

- `src/main/java/com/wfm/util/DayWindow.java` - FOUND
- `src/test/java/com/wfm/util/DayWindowTest.java` - FOUND
- `build.gradle` - FOUND
- `src/main/java/com/wfm/service/TimeslotGeneratorService.java` - FOUND
- `src/main/java/com/wfm/controller/TimeslotController.java` - FOUND
- `src/main/java/com/wfm/service/FteUploadService.java` - FOUND
- `src/test/java/com/wfm/repository/MidnightTimeslotPostgresTest.java` - FOUND
- `src/test/java/com/wfm/service/TimeslotGeneratorServiceTest.java` - FOUND
- `src/test/java/com/wfm/service/MidnightWindowSeamTest.java` - FOUND
- `src/test/java/com/wfm/service/TimeslotGeneratorBusinessDateTest.java` - FOUND
- Commit `11b8c58` - FOUND in `git log --oneline --all`
- Commit `b35eb1d` - FOUND in `git log --oneline --all`
- Commit `425ffd1` - FOUND in `git log --oneline --all`
- Commit `8e48eb9` - FOUND in `git log --oneline --all`
- All task-level `<acceptance_criteria>` re-verified (see Deviations for the 3 wording-level discrepancies, all corrected)
- Plan-level `<verification>` re-run: `./gradlew test` green (1074/1074, 0 failures/errors, 4 pre-existing benchmark skips); `./gradlew compileJava --rerun-tasks` emits 89 `DayWindow`-specific deprecation diagnostics; `DayWindow` has exactly 9 `@Deprecated` annotations and 5 new anchored functions; `git diff -- build.gradle` touches no `dependencies`/`repositories`/`plugins` block; `setBusinessDate(` in `TimeslotGeneratorService.java` prints `1`
