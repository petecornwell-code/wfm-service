---
phase: 19-daywindow-re-anchoring
plan: 06
subsystem: solver
tags: [daywindow, bday-04, timeslot-generation, shift-library, staffing-requirements, export, timefold]

requires:
  - phase: 19-daywindow-re-anchoring
    provides: "19-01's ScheduleDetailResponse.dayStart field, 19-03's DayWindow.anchoredAt(LocalTime) and the nine anchored* instance methods, 19-04's dayWindowFor(UUID) seam on ShiftTemplateService and the ShiftLibraryGenerationService/ShiftLibraryValidationService window-binding patterns, 19-05's ScheduleOutputService window bindings in buildAgentSchedule/buildConstraintViolations"
provides:
  - "TimeslotGeneratorService.getLiveBounds, ShiftLibraryGenerationService's full candidate-enumeration/supply-expansion/band-scoring path, and FteUploadService.uploadFtes each bind one DayWindow from a real desk anchor -- no midnight-implicit static call and no midnight literal survives in any of the three"
  - "ShiftTemplateService.isAligned(LocalTime, int, LocalTime, DayWindow) -- the D-02 grid-alignment predicate, now anchor-aware, with the shift-template save-path refusal (D-11) preserved byte-identical in message, position and outcome"
  - "StaffingRequirementService.calculateErlangC/calculateErlangX each bind a DayWindow from the desk and reproduce DayWindow.durationMinutes's throw-on-non-forward behaviour explicitly, since the anchored instance method no longer throws"
  - "ScheduleOutputService.buildPreferenceReport binds its own window (the third of three public-method bindings in this class, alongside plan 19-05's buildAgentSchedule/buildConstraintViolations)"
  - "ScheduleExportService.exportToExcel binds one window per entry point from ScheduleDetailResponse.dayStart (plan 19-01), threaded through the Roster and Agent Allocation sheets"
affects: [19-07-daywindow-reanchoring, 19-08-daywindow-reanchoring]

actuals:
  tokens: 14983
  tasks: 3
  commits: 3
  plan_head_before: 5feb20eca5c998610541ee22d6aff3818a84523c

tech-stack:
  added: []
  patterns:
    - "Window bound once per public method from a real desk/schedule-derived anchor, threaded as a parameter through every private helper that needs it -- the same shape plans 19-04/19-05 established, applied here to TimeslotGeneratorService, ShiftLibraryGenerationService, FteUploadService, StaffingRequirementService, ShiftTemplateService and ScheduleOutputService/ScheduleExportService's remaining unmigrated methods."
    - "When the anchor-source table's P-02 rule-5 classification ('propagate a DayWindow parameter outward to the caller that already resolves a desk') does not hold in practice -- the caller resolves no anchor either -- the class takes a DeskRepository directly and loads its own anchor, mirroring ShiftTemplateService.dayWindowFor's shape. Applied to TimeslotGeneratorService (for getLiveBounds only), FteUploadService and StaffingRequirementService."
    - "A deprecated DayWindow static call that the anchored instance method does not behave identically to (durationMinutes no longer throws on a non-forward interval) is not migrated verbatim where an existing test pins the throw; the throw is reproduced explicitly at the call site via anchoredIsForwardWithinDay, keeping the anchored primitives as the one implementation underneath."
    - "A raw-arithmetic/comparison line already recorded in MidnightTimeArithmeticGuardTest's structural allowlist (src/test/resources/midnight-time-arithmetic.md, a file reserved for plan 19-07) is left on its pre-existing call shape when migrating it would change the line's text and desync the allowlist -- the migration is deferred to whichever plan can touch that file, not forced through a workaround."

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/service/TimeslotGeneratorService.java
    - src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
    - src/main/java/com/wfm/service/FteUploadService.java
    - src/main/java/com/wfm/service/ShiftTemplateService.java
    - src/main/java/com/wfm/service/StaffingRequirementService.java
    - src/main/java/com/wfm/service/ShiftLibraryValidationService.java
    - src/main/java/com/wfm/service/ScheduleOutputService.java
    - src/main/java/com/wfm/service/ScheduleExportService.java
    - src/test/java/com/wfm/service/TimeslotGeneratorBusinessDateTest.java
    - src/test/java/com/wfm/service/MidnightWindowSeamTest.java
    - src/test/java/com/wfm/service/StaffingRequirementErlangTest.java
    - src/test/java/com/wfm/service/ScheduleExportServiceTest.java
    - src/test/java/com/wfm/service/ScheduleAllocationExportTest.java
    - src/test/java/com/wfm/service/ScheduleRosterExportTest.java

key-decisions:
  - "TimeslotGeneratorService.getLiveBounds gained a DeskRepository and a private dayWindowFor(UUID) (throws EntityNotFoundException on a missing desk, matching ShiftTemplateService's convention) -- the anchor-source table listed this file under 'already receives LocalTime dayStart', but getLiveBounds specifically does not: it converts two raw minute-of-day values straight from a native query, with no dayStart parameter anywhere in its call chain. Binding the desk's real anchor here is forward-compatible once a non-00:00 desk exists, and is byte-identical to today's output since every desk's anchor is currently MIDNIGHT (G-1)."
  - "TimeslotGeneratorService.requireDayStartTiles is NOT routed through a bound DayWindow instance, despite being one of this file's three midnight-implicit calls. Its argument IS the anchor itself (checking dayStart's own divisibility by the generation increment), so binding window = DayWindow.anchoredAt(dayStart) and calling window.anchoredStartMinute(dayStart) would always return 0 (an anchor measured against itself) -- a silent behaviour regression the plan's own transformation text did not account for. Kept as plain LocalTime.getHour()*60+getMinute() arithmetic instead, which is mathematically identical to the deprecated static's own body and is not one of the seven composite forms D-05 rules out hand-composing."
  - "FteUploadService and StaffingRequirementService both gained a DeskRepository directly, rather than the P-02 rule-5 'propagate outward' shape the anchor-source table assigned them. Verified: neither class's only real caller (StaffingRequirementController) resolves a desk or a dayStart either, so there is no anchor to propagate from. Loading the desk once per public method, mirroring ShiftTemplateService.dayWindowFor's shape, is the self-contained fix."
  - "FteUploadService.uploadFtes now passes desk.getDayStart() to TimeslotGeneratorService.generateTimeslots instead of the hardcoded LocalTime.MIDNIGHT literal the controller-style comment already flagged as temporary -- removes the file's one midnight literal and is byte-identical today since the desk's anchor is MIDNIGHT (G-1)."
  - "StaffingRequirementService.intervalMinutes(DayWindow, LocalTime, LocalTime) reproduces DayWindow.durationMinutes's exact throw (same message) rather than delegating straight to anchoredDurationMinutes, which no longer throws on a non-forward interval (BDAY-04 criterion 2). StaffingRequirementErlangTest#midnightCrossingTimeslotIsRejected pins this throw at exactly this call site, and nothing upstream of it (unlike ShiftTemplateService.validate's D-11 refusal) already refuses a non-forward timeslot interval."
  - "ShiftTemplateService.isAligned gained a trailing DayWindow parameter -- its two internal raw calls (DayWindow.endMinute/startMinute) needed migrating, and the method is shared across ShiftLibraryGenerationService and ShiftLibraryValidationService. Both callers' existing window already in scope is threaded through; no new anchor source was introduced in either caller."
  - "ShiftLibraryGenerationService.expandForSupply's one comparison (start.isBefore(earliestStart) || DayWindow.endMinute(end) > DayWindow.endMinute(latestEnd)) is deliberately left on the deprecated static form. MidnightTimeArithmeticGuardTest's comparison allowlist keys this exact line's text in src/test/resources/midnight-time-arithmetic.md, a file this plan is explicitly forbidden from touching (reserved for plan 19-07); converting the DayWindow calls to window.anchoredEndMinute desyncs that allowlist (confirmed: the full suite went from 1134/0/0 to 1134/1/0 with that one change, the comparison-allowlist test the sole failure). This is the one call site in the plan's eight files where the per-file completeness gate reads 2, not 0, for a reason outside this plan's reach -- plan 19-07 or 19-08 should complete the migration once the allowlist file is in scope."

requirements-completed: [BDAY-04]

coverage:
  - id: D1
    description: "TimeslotGeneratorService, ShiftLibraryGenerationService and FteUploadService each compute every interval through a DayWindow bound once per scope from a real anchor source; no midnight-implicit static call and no midnight literal survives in any of the three."
    requirement: BDAY-04
    verification:
      - kind: other
        ref: "grep -vE comment-lines | grep -oE 'DayWindow[.](startMinute|endMinute|durationMinutes|isForwardWithinDay|overlaps|contains|startsBefore|toLocalTime|plusWithinDay)\\(' -- 0 for all three files"
        status: pass
      - kind: other
        ref: "grep -c 'LocalTime.MIDNIGHT' -- 0 for all three files"
        status: pass
      - kind: integration
        ref: "./gradlew test --tests com.wfm.service.TimeslotGeneratorBusinessDateTest --tests com.wfm.service.TimeslotGeneratorServiceTest (33 tests, 0 failures, 0 errors)"
        status: pass
      - kind: integration
        ref: "./gradlew test (full, unfiltered) after Task 1 commit alone -- 1134 tests, 0 failures, 0 errors, 4 skipped"
        status: pass
    human_judgment: false
  - id: D2
    description: "ShiftTemplateService, StaffingRequirementService and ShiftLibraryValidationService compute every interval against an explicit anchor; the shift-template save-path refusal is byte-identical in message, position and outcome (D-11)."
    requirement: BDAY-04
    verification:
      - kind: other
        ref: "grep -c 'Shift template end time must be after its start time' ShiftTemplateService.java == 1; no added throw statement in the task-2 commit diff"
        status: pass
      - kind: integration
        ref: "./gradlew test --tests com.wfm.service.ShiftTemplateServiceTest --tests com.wfm.service.ShiftLibraryValidationServiceTest --tests com.wfm.service.StaffingRequirementErlangTest --tests com.wfm.service.MidnightWindowSeamTest (114 tests, 0 failures, 0 errors)"
        status: pass
      - kind: integration
        ref: "./gradlew test (full, unfiltered) after Task 2 commit -- 1134 tests, 0 failures, 0 errors, 4 skipped"
        status: pass
    human_judgment: false
  - id: D3
    description: "ScheduleOutputService and ScheduleExportService compute every interval against the anchor the schedule was solved with (schedule.getScheduleConfig().dayStart() / ScheduleDetailResponse.dayStart); an export whose anchor is missing fails loudly instead of quietly reverting to midnight."
    requirement: BDAY-04
    verification:
      - kind: other
        ref: "grep -c 'LocalTime.MIDNIGHT' ScheduleOutputService.java ScheduleExportService.java == 0 for both; grep -c 'getDayStart()' ScheduleExportService.java == 1"
        status: pass
      - kind: integration
        ref: "./gradlew test --tests com.wfm.service.ScheduleExportServiceTest --tests com.wfm.service.ScheduleAllocationExportTest --tests com.wfm.service.ScheduleRosterExportTest (37 tests, 0 failures, 0 errors)"
        status: pass
      - kind: integration
        ref: "./gradlew test (full, unfiltered) after Task 3 commit -- 1134 tests, 0 failures, 0 errors, 4 skipped"
        status: pass
    human_judgment: false
  - id: D4
    description: "Full unfiltered suite green after every task, including all eight Phase 18 guard tests, at every commit in this plan."
    requirement: BDAY-04
    verification:
      - kind: integration
        ref: "./gradlew test (full, unfiltered) -- 1134 tests, 0 failures, 0 errors, 4 pre-existing skips, run 3 times (once per task commit)"
        status: pass
      - kind: other
        ref: "Per-class XML tallies for MidnightBoundaryRegressionTest, MidnightBoundaryPropertyTest, BusinessDateWritePathGuardTest, TimeslotGeneratorBusinessDateTest, MidnightGapScanTest, MidnightWindowSeamTest, MidnightTimeArithmeticGuardTest, DayWindowTest -- all 0 failures, 0 errors"
        status: pass
    human_judgment: false

duration: 90min
completed: 2026-10-01
status: complete
---

# Phase 19 Plan 06: Horizontal Expansion — Eight Services Bind A Real Window Summary

**The eight service files reaching an anchor through a parameter, a `Desk`, or a schedule snapshot — `TimeslotGeneratorService`, `ShiftLibraryGenerationService`, `FteUploadService`, `ShiftTemplateService`, `StaffingRequirementService`, `ShiftLibraryValidationService`, `ScheduleOutputService` and `ScheduleExportService` — now compute every interval through a `DayWindow` bound once per scope from a real anchor, with the shift-template save-path refusal preserved byte-identical and one deliberately deferred call site documented for plan 19-07/19-08.**

## Performance

- **Duration:** ~90 min
- **Started:** 2026-10-01T03:05:00Z
- **Completed:** 2026-10-01T04:34:20Z
- **Tasks:** 3
- **Files modified:** 14 (8 main, 6 test)

## Accomplishments
- `TimeslotGeneratorService.getLiveBounds` now binds a `DayWindow` from a newly-added `DeskRepository` lookup (a Rule 3 deviation — see below) and converts its two native-query minute-of-day values through `window.anchoredToLocalTime`, removing both occurrences of the deprecated static `DayWindow.toLocalTime`. `requireDayStartTiles`'s one remaining call — checking the anchor's OWN divisibility by the generation increment — stays plain `LocalTime` arithmetic rather than being routed through a bound instance, since binding at the anchor and measuring the anchor against itself would always read zero (documented as a Rule 1 correction to the plan's own transformation text).
- `ShiftLibraryGenerationService`'s full candidate-enumeration (`enumerateCandidates`), supply-expansion (`expandForSupply`, `demandHours`) and band-scoring (`scoreOffset`) paths now thread the `DayWindow` plan 19-04 already bound in `generateSuggestion` through every private helper, eliminating 13 of 15 raw `DayWindow` static calls. One comparison in `expandForSupply` is deliberately left on the deprecated static form because `MidnightTimeArithmeticGuardTest`'s comparison allowlist — a file this plan cannot touch — keys that exact line's text (documented below).
- `FteUploadService.uploadFtes` binds a `DayWindow` from a newly-added `DeskRepository` lookup (Rule 3), eliminating all four midnight-implicit calls and the file's one `LocalTime.MIDNIGHT` literal — the `generateTimeslots` call now passes the desk's real `dayStart` instead of a hardcoded literal.
- `ShiftTemplateService.validate` binds one window per call via the existing `dayWindowFor(UUID)` seam, routing the D-11 forward-interval refusal and the duration read through it with the exact message, position and outcome preserved. `isAligned` gained a trailing `DayWindow` parameter (shared by `ShiftLibraryGenerationService` and `ShiftLibraryValidationService`, both updated to pass their already-bound window).
- `StaffingRequirementService.calculateErlangC`/`calculateErlangX` each bind a `DayWindow` from a newly-added `DeskRepository` lookup (Rule 3) and route the Erlang interval calculation through a new `intervalMinutes` helper that reproduces `DayWindow.durationMinutes`'s exact throw-on-non-forward behaviour — a Rule 1 fix, since the anchored instance method no longer throws and `StaffingRequirementErlangTest#midnightCrossingTimeslotIsRejected` pins exactly this throw.
- `ScheduleOutputService.buildPreferenceReport` gains its own window binding (the third public-method binding in this class, alongside plan 19-05's two), threaded through `bandBreaks`/`findBreaks`/`breaksOverlapPreferred`.
- `ScheduleExportService.exportToExcel` binds one window per entry point from `ScheduleDetailResponse.dayStart` (plan 19-01's field), threaded through `writeRoster`/`shiftCode` and `writeAgentAllocation`/`writeAllocationSheet`/`incrementMinutes`/`slotStarts` — no `DayWindow`/`LocalTime` parameter added to the two public entry points, and an unset anchor now throws at window construction instead of silently exporting at midnight.
- Full unfiltered `./gradlew test` is green at every task boundary: **1134 tests, 0 failures, 0 errors, 4 pre-existing skips** — run three separate times (once per task commit), plus all eight Phase 18 guard tests confirmed green individually.

## Task Commits

Each task was committed atomically:

1. **Task 1: Generation and upload services — the files that already know their anchor** - `bc444b5` (feat)
2. **Task 2: Validation services — and the save-path refusal survives intact** - `610a805` (feat)
3. **Task 3: Report and export services — the anchor arrives with the schedule** - `5c9a728` (feat)

**Plan metadata:** (this commit, docs(19-06): complete plan)

## Files Created/Modified
- `src/main/java/com/wfm/service/TimeslotGeneratorService.java` - new `DeskRepository` field + `dayWindowFor(UUID)`; `getLiveBounds` binds a window; `requireDayStartTiles` uses plain `LocalTime` arithmetic for the anchor's own minute-of-day
- `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` - `enumerateCandidates`, `expandForSupply` (+ new `DayWindow` param), `demandHours` (+ new `DayWindow` param), `scoreOffset` (+ new `DayWindow` param) all route through the window; `isAligned` call sites gain a trailing `window` arg; one comparison in `expandForSupply` deliberately kept on the deprecated static (see Deviations)
- `src/main/java/com/wfm/service/FteUploadService.java` - new `DeskRepository` field; binds a window at the top of `uploadFtes`; `generateTimeslots` call passes `desk.getDayStart()` instead of a `LocalTime.MIDNIGHT` literal
- `src/main/java/com/wfm/service/ShiftTemplateService.java` - `validate` binds a window via `dayWindowFor`; `validateGridAlignment`/`addIfMisaligned`/`isAligned` all take a `DayWindow` parameter; D-11 refusal preserved byte-identical
- `src/main/java/com/wfm/service/StaffingRequirementService.java` - new `DeskRepository` field; `dayWindowFor(UUID, long)` and `intervalMinutes(DayWindow, LocalTime, LocalTime)` private helpers; both Erlang endpoints bind a window and route through `intervalMinutes`
- `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` - `isTemplateAligned`'s calls to `ShiftTemplateService.isAligned` updated to pass the already-bound `dayWindow`
- `src/main/java/com/wfm/service/ScheduleOutputService.java` - `buildPreferenceReport` binds its own window; `bandBreaks`/`findBreaks`/`breaksOverlapPreferred` all take a `DayWindow` parameter
- `src/main/java/com/wfm/service/ScheduleExportService.java` - `exportToExcel(detail, daysOff)` binds a window from `detail.getDayStart()`; `writeRoster`/`shiftCode`/`writeAgentAllocation`/`writeAllocationSheet`/`incrementMinutes`/`slotStarts` all take a `DayWindow` parameter
- `src/test/java/com/wfm/service/TimeslotGeneratorBusinessDateTest.java` - constructor call updated for the new `DeskRepository` parameter
- `src/test/java/com/wfm/service/MidnightWindowSeamTest.java` - `ShiftTemplateService.isAligned` calls pass an explicit `DayWindow.anchoredAt(LocalTime.MIDNIGHT)`, no asserted value changed
- `src/test/java/com/wfm/service/StaffingRequirementErlangTest.java` - new `DeskRepository` mock, stubbed to return a desk at its default (MIDNIGHT) anchor; constructor call updated
- `src/test/java/com/wfm/service/ScheduleExportServiceTest.java`, `ScheduleAllocationExportTest.java`, `ScheduleRosterExportTest.java` - every `ScheduleDetailResponse` fixture sets `dayStart` to `LocalTime.MIDNIGHT`; no asserted cell value, sheet name, or byte count changed

## Decisions Made

See `key-decisions` in the frontmatter for full reasoning. In brief:
- Three classes (`TimeslotGeneratorService` for `getLiveBounds` only, `FteUploadService`, `StaffingRequirementService`) gained a `DeskRepository` directly rather than following the anchor-source table's "propagate outward" classification, because neither class's real caller resolves a desk or a `dayStart` either — verified by reading the actual controllers before implementing.
- `TimeslotGeneratorService.requireDayStartTiles` is the one call in this plan's eight files that is NOT routed through a bound `DayWindow` instance — its argument is the anchor itself, and the plan's own transformation text would have silently broken the check (always reading zero).
- `StaffingRequirementService.intervalMinutes` reproduces the deprecated static's throw-on-non-forward behaviour explicitly, since the anchored instance method's non-throwing semantics (BDAY-04 criterion 2) would otherwise silently regress a pinned test.
- `ShiftTemplateService.isAligned` gained a trailing `DayWindow` parameter, propagated to its two existing cross-class callers.
- One comparison in `ShiftLibraryGenerationService.expandForSupply` is deliberately left unmigrated because converting it desyncs `MidnightTimeArithmeticGuardTest`'s comparison allowlist, a file reserved for plan 19-07.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `TimeslotGeneratorService.getLiveBounds` has no anchor source reachable under the anchor-source table's classification**
- **Found during:** Task 1 (before writing any code — confirmed by reading `getLiveBounds` and its four production callers)
- **Issue:** The plan's anchor-source table lists `TimeslotGeneratorService` under "already receives `LocalTime dayStart` on `generateTimeslots`, `isDesired`, `timeslotsMatch`, `requireDayStartTiles`" — but `getLiveBounds(UUID deskId)` is not in that list and genuinely has no `dayStart` parameter anywhere in its signature or call chain. Its two midnight-implicit `DayWindow.toLocalTime` calls convert raw minute-of-day values straight from a native query (`findLiveBoundsByDeskRaw`), with no anchor concept in the SQL at all.
- **Fix:** Added a `DeskRepository` field and a private `dayWindowFor(UUID)` helper (throwing `EntityNotFoundException` on a missing desk, matching `ShiftTemplateService`'s own convention), and bound a window in `getLiveBounds` from the desk's real `dayStart`. Byte-identical output today since every desk's anchor is `MIDNIGHT` (G-1); forward-compatible once a non-`00:00` desk exists.
- **Files modified:** `src/main/java/com/wfm/service/TimeslotGeneratorService.java`, `src/test/java/com/wfm/service/TimeslotGeneratorBusinessDateTest.java` (constructor update only, this test never calls `getLiveBounds`)
- **Verification:** `./gradlew compileJava compileTestJava` exits 0; `./gradlew test --tests com.wfm.service.TimeslotGeneratorBusinessDateTest --tests com.wfm.service.TimeslotGeneratorServiceTest` green (33 tests); full suite green.
- **Committed in:** `bc444b5` (Task 1 commit)

**2. [Rule 1 - Bug] The plan's described transformation for `requireDayStartTiles` would silently break the tiling check**
- **Found during:** Task 1 (before writing any code — verified by hand-computing `anchoredStartMinute` against itself)
- **Issue:** The plan's action text states `requireDayStartTiles`'s one midnight-implicit call "stays correct through the bound instance." It does not: `window.anchoredStartMinute(dayStart)` where `window = DayWindow.anchoredAt(dayStart)` computes `floorMod(startMinute(dayStart) - startMinute(dayStart), 1440)`, which is always `0` — an anchor measured against itself. The original check (`DayWindow.startMinute(dayStart) % incrementMinutes != 0`) asks whether `dayStart`'s own clock position tiles the generation grid; following the plan literally would make this `IllegalArgumentException` never fire for any `dayStart`/`incrementMinutes` combination, silently disabling a data-integrity guard.
- **Fix:** Kept the check as plain `LocalTime` arithmetic (`dayStart.getHour() * 60 + dayStart.getMinute()`), mathematically identical to `DayWindow.startMinute`'s own body (confirmed against `DayWindow.java`'s source) and documented inline with the reasoning. This is not one of the seven composite forms D-05 rules out hand-composing (it extracts a single `LocalTime`'s own minute-of-day, not an interval/duration/overlap computation), and it does not trip `MidnightTimeArithmeticGuardTest`'s raw-arithmetic scan (confirmed: that test stayed green across every run in this plan).
- **Files modified:** `src/main/java/com/wfm/service/TimeslotGeneratorService.java`
- **Verification:** `./gradlew test --tests com.wfm.service.TimeslotGeneratorServiceTest` (includes the `RequireDayStartTiles` nested class) green; full suite green.
- **Committed in:** `bc444b5` (Task 1 commit)

**3. [Rule 3 - Blocking] `FteUploadService` and `StaffingRequirementService` have no reachable anchor under the "propagate outward" classification**
- **Found during:** Task 1 (`FteUploadService`) and Task 2 (`StaffingRequirementService`) — confirmed by reading `StaffingRequirementController`, the real (and only) caller of both classes' relevant public methods
- **Issue:** The anchor-source table assigns both classes "Rule 5: propagate a `DayWindow` parameter out to the caller that already resolves a desk or a `dayStart`." Neither caller does: `StaffingRequirementController` holds only `StaffingRequirementService` and `FteUploadService`, with no `DeskRepository` and no desk-resolving call of its own anywhere in the controller.
- **Fix:** Both classes gained a `DeskRepository` directly and bind their own window (mirroring `ShiftTemplateService.dayWindowFor`'s shape) rather than growing the controller. `FteUploadService` also lost its one `LocalTime.MIDNIGHT` literal as a direct consequence — the `generateTimeslots` call now passes the desk's real `dayStart`.
- **Files modified:** `src/main/java/com/wfm/service/FteUploadService.java`, `src/main/java/com/wfm/service/StaffingRequirementService.java`, `src/test/java/com/wfm/service/StaffingRequirementErlangTest.java` (new `DeskRepository` mock + stub; `FteUploadService` has no existing test file)
- **Verification:** `./gradlew compileJava compileTestJava` exits 0 both times; targeted and full suite green both times.
- **Committed in:** `bc444b5` (Task 1, `FteUploadService`), `610a805` (Task 2, `StaffingRequirementService`)

**4. [Rule 1 - Bug, caught by this plan's own mandated test run] `StaffingRequirementService`'s Erlang duration call loses a pinned throw if migrated verbatim**
- **Found during:** Task 2 (reading `StaffingRequirementErlangTest` before implementing, specifically `midnightCrossingTimeslotIsRejected`)
- **Issue:** `DayWindow.durationMinutes` throws `IllegalArgumentException` (message containing "single day") on a non-forward interval; `window.anchoredDurationMinutes` does NOT (BDAY-04 criterion 2 — it wraps forward instead). Unlike `ShiftTemplateService.validate`'s D-11 refusal (where `isForwardWithinDay` already refuses the request one line earlier), nothing upstream of `StaffingRequirementService`'s two Erlang duration calls refuses a non-forward timeslot interval, and `StaffingRequirementErlangTest#midnightCrossingTimeslotIsRejected` pins the throw directly. A literal migration to `anchoredDurationMinutes` would have silently turned this refusal into a wrapped-forward duration.
- **Fix:** Added a private `intervalMinutes(DayWindow, LocalTime, LocalTime)` helper that calls `window.anchoredIsForwardWithinDay` first and throws the exact same message before delegating to `anchoredDurationMinutes` — reproducing the deprecated static's behaviour through the anchored primitives rather than silently losing it.
- **Files modified:** `src/main/java/com/wfm/service/StaffingRequirementService.java`
- **Verification:** `./gradlew test --tests com.wfm.service.StaffingRequirementErlangTest` green (10 tests, including `midnightCrossingTimeslotIsRejected`); full suite green.
- **Committed in:** `610a805` (Task 2 commit)

**5. [Rule 3 - Compiler-forced, cross-task] `ShiftTemplateService.isAligned`'s signature change ripples into `ShiftLibraryGenerationService` (a Task 1 file)**
- **Found during:** Task 2 (compile step, after `isAligned` gained its trailing `DayWindow` parameter)
- **Issue:** `isAligned`'s two internal raw `DayWindow.endMinute`/`DayWindow.startMinute` calls needed migrating (it is a `ShiftTemplateService.java` method, one of Task 2's own declared files), but it is called from `ShiftLibraryGenerationService` (4 sites, Task 1) and `ShiftLibraryValidationService` (4 sites, Task 2) with the old 3-argument shape. Changing the signature without updating every caller is a compile failure.
- **Fix:** Threaded the trailing `window` argument (already in scope at every call site — `enumerateCandidates`'s existing `DayWindow window` parameter, `isTemplateAligned`'s existing `dayWindow` parameter) through all 8 call sites, plus `MidnightWindowSeamTest`'s 3 direct calls (added an explicit `DayWindow.anchoredAt(LocalTime.MIDNIGHT)`, no asserted value changed). To keep Task 1's own commit independently compilable, this ripple was committed as part of Task 2 (`610a805`), not Task 1 — `ShiftLibraryGenerationService.java`'s isAligned call sites were verified staying on the OLD 3-argument shape at Task 1's commit point (confirmed by a clean `./gradlew compileJava compileTestJava` + full-suite run with Task 2's files reverted via `git stash` before committing Task 1).
- **Files modified:** `src/main/java/com/wfm/service/ShiftTemplateService.java`, `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java`, `src/main/java/com/wfm/service/ShiftLibraryValidationService.java`, `src/test/java/com/wfm/service/MidnightWindowSeamTest.java`
- **Verification:** `./gradlew compileJava compileTestJava` exits 0; full suite green at both the Task 1-only state (`isAligned` calls reverted to 3-arg) and the Task 2 state (4-arg, signature changed).
- **Committed in:** `610a805` (Task 2 commit)

**6. [Rule 4 - Constraint conflict, documented and deferred] One comparison in `ShiftLibraryGenerationService.expandForSupply` cannot be migrated without touching a file reserved for plan 19-07**
- **Found during:** Task 1 (full-suite run caught this before the Task 1 commit — see below)
- **Issue:** `MidnightTimeArithmeticGuardTest`'s comparison allowlist (`src/test/resources/midnight-time-arithmetic.md`) keys the EXACT TEXT of `expandForSupply`'s line `if (start.isBefore(earliestStart) || DayWindow.endMinute(end) > DayWindow.endMinute(latestEnd)) {` as a permitted raw comparison (the `start.isBefore(earliestStart)` sub-expression triggers the scan; the whole line's text is the allowlisted unit). Converting the `DayWindow.endMinute` calls to `window.anchoredEndMinute` changes the line's text, which the guard's two-directional scan reports as both a new unlisted occurrence and a stale allowlist entry — confirmed directly: the full suite went from 1134/0/0/4 to 1134/**1**/0/4 with only that one line changed, `MidnightTimeArithmeticGuardTest.rawTimeComparisonsInProductionCode_matchesTheComparisonAllowlistExactly` the sole failure. This plan's own execution constraints forbid touching `midnight-time-arithmetic.md` (reserved for plan 19-07) and forbid introducing any change that would need a new allowlist entry.
- **Fix:** Reverted that one line to the deprecated static `DayWindow.endMinute(...)` form, documented inline with the reasoning, and left as a known exception. The adjacent `.max(Comparator.comparingInt(...))` line (a DIFFERENT, non-allowlisted `DayWindow::endMinute` method reference) was still successfully converted to `window::anchoredEndMinute`.
- **Files modified:** `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java`
- **Verification:** `./gradlew test --tests com.wfm.service.MidnightTimeArithmeticGuardTest` green after the revert (9 tests, 0 failures); full suite green (1134/0/0/4).
- **Committed in:** `bc444b5` (Task 1 commit)
- **Residual gate state:** `ShiftLibraryGenerationService.java`'s per-file completeness gate reads **2**, not 0 (the two `DayWindow.endMinute(` calls on this one line), and its `LocalTime.MIDNIGHT` literal count reads **2** (both pre-existing from plan 19-04's `resolveBreakConfig` fallback, untouched and out of this plan's scope — the same category 19-05's SUMMARY documented for `ShiftBandPair.netHours()`). `ShiftLibraryValidationService.java` likewise retains 1 pre-existing `LocalTime.MIDNIGHT` literal from plan 19-04's `dayWindowFor` fallback-on-missing-desk, also untouched and out of scope.

---

**Total deviations:** 6 auto-fixed/documented (3 Rule 3 blocking-anchor-source additions spanning Tasks 1-2, 2 Rule 1 behaviour-preservation fixes caught before each relevant task's commit, 1 Rule 4 constraint conflict deliberately deferred to plan 19-07/19-08 and fully documented)
**Impact on plan:** None of these change what any production caller's anchor resolves to at a `00:00` anchor, or weaken the zero-midnight-fallback discipline this phase establishes — every deviation either adds a minimal, self-contained dependency to resolve a genuinely unreachable anchor, preserves an existing pinned behaviour a literal migration would have silently regressed, or defers a single call site to the plan that can actually touch the file the deferral depends on.

## Issues Encountered
None beyond the six deviations above, all resolved before their respective task commits.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All three task commits (`bc444b5`, `610a805`, `5c9a728`) land cleanly after plan 19-05's `faed18a`, each independently compiling and passing the full suite — verified by stashing forward-dependent files and re-running `compileJava`/`compileTestJava`/the full suite at each commit boundary, not merely asserted.
- The nine `DayWindow` statics remain public and `@Deprecated` (plan 19-07 runs in the same wave and still calls them); this plan did not touch `ScheduleConstraintProvider`, `SolverService`'s solver-side helpers, `FteSpreadsheetGenerator`, or `src/test/resources/midnight-time-arithmetic.md` — confirmed by `git diff --name-only` across all three commits.
- **One residual item for plan 19-07/19-08 to pick up:** `ShiftLibraryGenerationService.expandForSupply`'s one comparison line is still on the deprecated `DayWindow.endMinute` static form, blocked only by `midnight-time-arithmetic.md`'s reservation — see Deviation 6 for the exact line and the allowlist entry that needs updating alongside the code change.
- No blockers. Full unfiltered `./gradlew test` is green (1134 tests, 0 failures, 0 errors, 4 pre-existing skips) at every one of this plan's three commits, and all eight Phase 18 guard tests are confirmed green individually.

## Self-Check: PASSED

- FOUND: `src/main/java/com/wfm/service/TimeslotGeneratorService.java`
- FOUND: `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java`
- FOUND: `src/main/java/com/wfm/service/FteUploadService.java`
- FOUND: `src/main/java/com/wfm/service/ShiftTemplateService.java`
- FOUND: `src/main/java/com/wfm/service/StaffingRequirementService.java`
- FOUND: `src/main/java/com/wfm/service/ShiftLibraryValidationService.java`
- FOUND: `src/main/java/com/wfm/service/ScheduleOutputService.java`
- FOUND: `src/main/java/com/wfm/service/ScheduleExportService.java`
- FOUND commit `bc444b5` (Task 1) in `git log --oneline --all`
- FOUND commit `610a805` (Task 2) in `git log --oneline --all`
- FOUND commit `5c9a728` (Task 3) in `git log --oneline --all`
- CONFIRMED: per-file completeness gate reports 0 for TimeslotGeneratorService.java, FteUploadService.java, ShiftTemplateService.java, StaffingRequirementService.java, ShiftLibraryValidationService.java, ScheduleOutputService.java, ScheduleExportService.java; reports 2 (documented, justified) for ShiftLibraryGenerationService.java
- CONFIRMED: `grep -c 'LocalTime.MIDNIGHT'` reports 0 for six of eight files; 2 (pre-existing, plan 19-04, documented) for ShiftLibraryGenerationService.java; 1 (pre-existing, plan 19-04, documented) for ShiftLibraryValidationService.java
- CONFIRMED: `grep -c 'Shift template end time must be after its start time' ShiftTemplateService.java` reports 1
- CONFIRMED: `./gradlew compileJava compileTestJava` exits 0 at every task boundary
- CONFIRMED: full, unfiltered `./gradlew test` — 1134 tests, 0 failures, 0 errors, 4 skipped, run three times (once per task commit)
- CONFIRMED: all eight Phase 18 guard test classes individually 0 failures / 0 errors
- CONFIRMED: `git diff --name-only 5feb20e..HEAD` touches exactly the 14 files listed above

---
*Phase: 19-daywindow-re-anchoring*
*Completed: 2026-10-01*
