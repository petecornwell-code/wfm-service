---
phase: 24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida
verified: 2026-10-07T19:20:00Z
status: human_needed
score: 32/34 must-haves verified
covered_files:
  - .planning/phases/24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida/24-01-PLAN.md
  - .planning/phases/24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida/24-01-SUMMARY.md
  - .planning/phases/24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida/24-02-PLAN.md
  - .planning/phases/24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida/24-02-SUMMARY.md
  - .planning/phases/24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida/24-03-PLAN.md
  - .planning/phases/24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida/24-03-SUMMARY.md
  - frontend/src/api/client.ts
  - frontend/src/pages/ScheduleResults.tsx
  - src/main/java/com/wfm/controller/StaffingRequirementController.java
  - src/main/java/com/wfm/dto/StaffingRequirementResponse.java
  - src/main/java/com/wfm/repository/StaffingRequirementRepository.java
  - src/main/java/com/wfm/service/ScheduleConsistencyRepairService.java
  - src/main/java/com/wfm/service/ScheduleEnvelopeRepairService.java
  - src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
  - src/main/java/com/wfm/service/ShiftLibraryValidationService.java
  - src/main/java/com/wfm/service/ShiftStartMixTargetService.java
  - src/main/java/com/wfm/service/StaffingRequirementService.java
  - src/main/java/com/wfm/solver/AgentAssignmentDifficultyComparator.java
  - src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java
  - src/test/java/com/wfm/service/ScheduleConsistencyRepairServiceTest.java
  - src/test/java/com/wfm/service/ScheduleEnvelopeRepairServiceTest.java
  - src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java
  - src/test/java/com/wfm/service/ShiftStartMixTargetServiceTest.java
  - src/test/java/com/wfm/service/StaffingRequirementListBusinessRangeTest.java
  - src/test/resources/bday-join-guard-offender-widened/OffendingSample.java
  - src/test/resources/bday-join-guard.md
covered_digest: "v3:sha256:a38c21556ffcb3705e6ee793816853ecb5dfb936bc350bab0068fdee29d09050"
behavior_unverified: 1
overrides_applied: 0
behavior_unverified_items:
  - truth: "The re-keyed repair and start-mix services hold no state across calls (D7), so an interrupted solve or two desks repaired concurrently cannot observe each other's business-date maps"
    test: "Run ScheduleEnvelopeRepairService, ScheduleConsistencyRepairService and ShiftStartMixTargetService concurrently for two desks (or twice in succession on different schedules) and compare each result to its single-desk run"
    expected: "Each result is identical to the isolated run; no map or field survives a call"
    why_human: "No test exercises call isolation. Code reading shows only static final fields (Logger, NOTHING, RESTARTS) and per-call local maps, which is presence evidence, not a behavioural proof. Declared backstop truth."
coincidental_reliance_items: []
human_verification:
  - test: "D7 per-call state isolation (see behavior_unverified_items)"
    expected: "Concurrent or successive calls on different desks do not interfere"
    why_human: "Backstop truth with no test; abstain absent explicit evidence"
  - test: "Open a 06:00-anchored desk's schedule in the Agent Allocation tab and read the Required and Over / under rows for post-midnight slots on a middle business day and on the FINAL business day"
    expected: "Required equals the demand seeded for that business day's calendar-next-day hours; the final business day's post-midnight cells are populated, not blank"
    why_human: "The UI half has no test runner (Phase 22 WR-04). 24-03-SUMMARY records a live CDP measurement (3 of 3 days exact, desk 2fb25a84, schedule 2964ccc6) taken with raw CDP DOM text rather than Playwright; I could not re-observe it without standing up the throwaway stack. Whether the rendered rows look right to an operator is untested."
  - test: "Review the six judgment-tier prohibitions below (non-authoritative LLM verdicts recorded in this report)"
    expected: "Operator confirms each MUST NOT held"
    why_human: "unverified-prohibition: human review recommended. Judgment-tier prohibitions get a non-authoritative LLM-judge verdict only; none has a wired enforcement check."
  - test: "Decide whether the 24-03 phase-gate shortfall is closed"
    expected: "Accept the orchestrator's post-24-03 clean full-suite run (218 XML files, 1535 tests, 0 failures, 0 errors, 4 skipped) as the gate"
    why_human: "24-03-SUMMARY recorded one failure (MultiDayConstraintDiagnosticTest.multiDay_5agents_5days_shouldScoreZeroHard, wall-clock-flaky, deferred-items.md, status open). The orchestrator reports it passed in a later fresh-daemon run. I did not re-run the ~20 min suite."
---

# Phase 24: Close Gap N-1/N-2 Verification Report

**Phase Goal:** Every surface that decides which business day a timeslot belongs to agrees with the solver, so a weekday-restricted overnight template validates against the hours the solver can actually give it, and the Agent Allocation Required / Over-under rows compare like with like (v1.5 re-audit gaps N-1 and N-2).
**Verified:** 2026-10-07
**Status:** human_needed
**Re-verification:** No, initial verification

## Goal Achievement

The goal is achieved in the code. Both gaps are closed at the source, the unit and guard tests bind to the fix (shown by mutation), and no calendar-date key remains in any file the guard covers. Status is `human_needed` rather than `passed` because one declared backstop truth (D7) has no test, the UI half rests on a recorded live measurement I could not re-observe, and six judgment-tier prohibitions are only judged by an LLM (me). No truth FAILED.

ROADMAP Phase 24 defines no Success Criteria list, only a Goal and a Context, so the truths below come from the three PLAN `must_haves` blocks (34 truths, plus 6 prohibitions) and are traced to the Goal.

### Observable Truths

**Plan 24-01 (N-1: shift-library validation)**

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Monday-only 21:00-06:00 template on a 06:00 desk covers its own post-midnight hours; no uncovered window, no unsatisfiable weekday, `requireShiftModeReady` does not throw | VERIFIED | `findUncoveredWindows` builds `Window` from `getBusinessDate()` (ShiftLibraryValidationService.java:230); test `requireShiftModeReady_mondayOnlyOvernightTemplate_acceptsItsOwnPostMidnightHours` passes. Mutating line 230 back to `getDate()` turns this test red. |
| 2 | Same template is NOT credited with business-Sunday's calendar-Monday 01:00 hour; reported uncovered under 2026-10-04 | VERIFIED | `validate_previousBusinessDaysPostMidnightWindow_isNotCreditedToTheCalendarWeekdaysTemplate` passes; red under mutation |
| 3 | `isEffectiveOn` judged on the business date | VERIFIED | `covers()` calls `template.isEffectiveOn(window.businessDate())` (:261); `validate_effectiveFromIsJudgedOnTheBusinessDate` passes; red under mutation |
| 4 | `covers` stays the single predicate; generator calls it unchanged | VERIFIED | One `static covers` (:258); generator calls `shiftLibraryValidationService.covers` at :694, :713, :852, :941, :946; no fork found |
| 5 | Unsatisfiable-weekday findings bucket by business weekday (no TUESDAY finding) | VERIFIED | `findUnsatisfiableWeekdays` maps `getBusinessDate()` (:457); asserted via `unsatisfiableWeekdays().isEmpty()` in truth 1's test |
| 6 | Post-midnight peak shortfall yields one advisory dated 2026-10-05 naming Monday, short by 2 | VERIFIED | `findPeakShortfalls` uses `getBusinessDate()` (:578); `validate_peakShortfallOnAPostMidnightHour_bucketsOnTheBusinessWeekday` passes |
| 7 | Operator text names the business date and discloses the calendar date only when it differs, via one shared `Window.describe`, used by validator and generator | VERIFIED | `Window.describe` (:768); generator builds its coverage `ErrorDetail` from `window.describe(dayWindow)` (Generation:854); peak message uses it (:624); exact-string test passes |
| 8 | At a 00:00 anchor every validator string is byte-identical | VERIFIED | `describe` returns the undecorated form when the two dates agree; `validate_midnightDesk_uncoveredStringsStayByteIdentical` passes |
| 9 | Adjacency at the day start: a window ending at 06:00 belongs to the ending day, one starting at 06:00 to the new day, undecorated | VERIFIED | `validate_windowStartingExactlyAtTheDayStart_belongsToTheNewBusinessDay_andIsNotDecorated` passes; red under mutation |
| 10 | `uncoveredWindows` ordered by business date, then minutes from day start, then anchored end | VERIFIED | Sort at :233-235 uses `anchoredStartMinute`/`anchoredEndMinute`; `validate_uncoveredWindowsOnAnAnchoredDesk_areOrderedFromTheDayStart` passes; red under mutation |
| 11 | Widened guard went red against the unmigrated tree on 14 sites before any production edit, then validator sites cleared | VERIFIED | Commit order: `d763d82` (test-only, 1 file, message "red on 14 unmigrated sites") precedes the first production commit `623258f`. `ecc95e8` is likewise test-only. |
| 12 | No allowlist entry added; `TARGET_FILES`/`JOIN_VERB_TOKENS` unchanged; widened scan is an explicit four-file list | VERIFIED | `bday-join-guard.md` Allowlist fenced block is empty; `git diff d763d82^..HEAD` on the test shows no `-`/`+` lines for `TARGET_FILES` or `JOIN_VERB_TOKENS`; `WIDENED_TARGET_FILES` is a 4-entry `List.of` (:145) |
| 13 | `PeakShortfallAdvisory.date` carries the business date; `ShiftLibrary.tsx` panel unchanged | VERIFIED | `findPeakShortfalls` passes the business `date` into the record (:600); no frontend diff for `ShiftLibrary.tsx` |

**Plan 24-02 (same-class sweep: repair, swap, start-mix)**

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 14 | Post-midnight seat outside its business-Monday envelope is found and moved (violationsFound 1, repaired 1) | VERIFIED | Envelope keys at ScheduleEnvelopeRepairService.java:147, 150, 169, 286, 332, 358, 384 all use `getBusinessDate()`; `findsAndRepairsAPostMidnightSeatOutsideItsBusinessDayEnvelope` passes; red-first commit `b06f683` is test-only |
| 15 | Post-midnight seat is never judged against the next business day's envelope | VERIFIED | `neverJudgesAPostMidnightSeatAgainstTheNextBusinessDaysEnvelope` passes |
| 16 | Usual-shift swap carries post-midnight seats with the envelope | VERIFIED | `seatsByDateAgent` keyed on `getBusinessDate()` (Consistency:149); `postMidnightSeatsFollowTheirEnvelopeInASwap` passes; red-first commit `309832f` test-only |
| 17 | A business day whose demand falls wholly after midnight still gets start-mix targets | VERIFIED | `reqByDate`, `seatsByDate`, `slotsByDate` keyed on `ts.getBusinessDate()` (StartMix:153, 168, 175); `aBusinessDayWhoseDemandFallsAfterCalendarMidnight_stillGetsTargets` passes |
| 18 | Each fix is a key change only (7 + 1 + 3 sites), no new map or method, `repairVerified` logic unchanged | VERIFIED | Eleven `getBusinessDate()` sites found. Diff of the envelope service shows no touched `repairVerified`, rescore, revert or score-compare lines. |
| 19 | 00:00 control: pre-existing repair/start-mix tests pass unchanged with business date set equal to calendar date | VERIFIED | Envelope 7, Consistency 8, StartMix 14 tests, 0 failures in my targeted run |
| 20 | `AgentAssignmentDifficultyComparator` keeps calendar date plus clock time; `BusinessDayPeriodLoader` read stays; both classified in `bday-join-guard.md` | VERIFIED | Comparator still reads `getTimeslot().getDate()` (:27) with a comment added; loader reads feed `DayWindow.businessDateOf` (:54, 58, 80); both classified under "Phase 24 classification" in the md |
| 21 | Guard GREEN with empty allowlist; all four liveness proofs still red on synthetic input | VERIFIED | `BusinessDateJoinGuardTest` 8/8 pass in my run. Under my line-230 mutation its headline test went red, so the empty allowlist is not vacuous. |
| 22 | Widened pipeline fixture line is invisible to the verb predicate and visible to the widened one | VERIFIED | Fixture `OffendingSample.java` exists; the dedicated liveness test is among the 8 passing guard tests |
| 23 | `bday-join-guard.md` records widened scope, 14-site history, classification of remaining reads, empty allowlist | VERIFIED | Read in full: "Widened scope", "Phase 24 history" and "Phase 24 classification" sections present; block empty |
| 24 | Repair/start-mix services hold no state across calls (D7, `verification: backstop`) | PRESENT_BEHAVIOR_UNVERIFIED | Fields are only `static final` (Logger, NOTHING, RESTARTS) with per-call local maps, but no test exercises isolation. Abstained per the backstop rule; see Human Verification. Not counted. |

**Plan 24-03 (N-2: allocation rows)**

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 25 | List endpoint accepts `businessFrom`/`businessTo` and filters the STORED `business_date` | VERIFIED | Controller :30-31 passes both; repository twins filter `t.businessDate BETWEEN :from AND :to` (lines 60-84 region); `businessRange_returnsTheFinalBusinessDaysPostMidnightRows_thatTheCalendarRangeGetsWrong` passes |
| 26 | Every `Item` carries `businessDate` beside an UNCHANGED calendar `date` | VERIFIED | `Item` record has `LocalDate businessDate` after `date`; `toResponseItem` passes `t.getDate(), t.getBusinessDate()`; `everyItemCarriesItsTimeslotsBusinessDate_andDateStaysCalendar` passes |
| 27 | Existing from/to callers unchanged; half-supplied calendar range still silently ignored | VERIFIED | Calendar branches untouched in `listRequirements`; `halfSuppliedCalendarRange_isStillIgnoredAsBefore` passes |
| 28 | Business-range paging reuses the calendar keyset and cursor; exhaustion at limit 1 returns every row once, in order | VERIFIED | Twin cursor predicate and ORDER BY copied verbatim; `businessRange_pagesToExhaustionAtLimitOne_returnsEveryRowExactlyOnceInOrder` passes |
| 29 | Half, mixed, inverted or malformed business range refused with IllegalArgumentException (HTTP 400); empty range returns empty page | VERIFIED | Refusals at Service:102-115 and `parseBusinessDate` (:175); four refusal tests and `businessRangeWithNoDemand_returnsAnEmptyPage` pass. Malformed cursor on this path is a separate residual, see WR-03. |
| 30 | Business-range queries are tenant- and desk-scoped | VERIFIED | Twins carry `sr.tenantId = :tenantId AND sr.deskId = :deskId`; `businessRange_isTenantScoped` passes |
| 31 | At a 00:00 anchor business and calendar ranges are identical and `businessDate == date` | VERIFIED | `midnightDesk_businessRangeAndCalendarRangeReturnIdenticalItems` passes |
| 32 | `ScheduleResults.tsx` loads by `businessFrom/businessTo` = schedule period and keys `requiredPerSlot` on `businessDate`; four lookups unchanged; no business date computed in TypeScript | VERIFIED | ScheduleResults.tsx:407-420 uses `businessFrom/businessTo` and `` `${r.businessDate}|...` ``; lookups at :678, :692, :968, :982 index `${date}|${slot}` on the grid's business date; the commit diff adds no date arithmetic; `tsc -b --noEmit` is clean |
| 33 | Live local measurement shows Required / Over-under cells equal seeded demand on a middle and the FINAL business day of a 06:00 desk | UNCERTAIN | Recorded in 24-03-SUMMARY (3/3 days exact; old code blanked day one and mis-filed days two and three), on a throwaway stack that is torn down (nothing listening on 55432/8081/3001/9333). I could not re-observe it. Routed to human. Not counted. |
| 34 | No `*-VERIFICATION.md` modified and no `covered_digest` refreshed by this phase; earlier phases listed for re-verify; gate is a real full-suite run | VERIFIED | `git diff a5d895c..HEAD --name-only` contains no VERIFICATION file. The gate evidence is the orchestrator's post-24-03 fresh-daemon `cleanTest test`: 218 XML, 1535 tests, 0 failures, 0 errors, 4 skipped. I did not re-run it; I ran the 8 affected classes myself (below). |

**Score:** 32/34 truths verified (1 present, behavior-unverified; 1 uncertain, routed to human)

### Judgment-Tier Prohibitions (non-authoritative LLM verdicts)

Flag: **unverified-prohibition, human review recommended.** None has wired enforcement. These are my judgments, not gates.

| Plan | Prohibition | My verdict | Basis |
|------|-------------|------------|-------|
| 24-01 | Never label a differing-date window with the business date alone; never decorate a matching-date window (OVNT-07) | Held | `describe` branches on date equality; exact-string and byte-identical tests pass; generator and peak message route through it. Residual: `PeakShortfallAdvisory.date` has no contract note (IN-01). |
| 24-02 | Guard not made green by allowlist, narrowing, weakened equality, tree walk, deleted liveness proof, or digest refresh (BDAY-05) | Held | Allowlist empty; scope lists and verb tokens unchanged; mutation turns the headline test red; no VERIFICATION file touched |
| 24-02 | Do not weaken either `repairVerified` (SOLV-07) | Held | No score-compare, rescore or revert lines in the diff of the envelope service; the consistency service's re-score-and-revert code is untouched in the diff |
| 24-03 | Do not change meaning of `Item.date` (SOLV-07) | Held | `toResponseItem` still passes `t.getDate()`; asserted by test |
| 24-03 | No business-date derivation in the frontend (OVNT-06) | Held | Commit diff is a param swap and a key swap; the comment states the server derives |
| 24-03 | Never seed, migrate, solve against or verify on `dev`, nor touch 5432/8080 (OVNT-06) | Held on available evidence | SUMMARY states throwaway stack only and a verified teardown; I confirmed nothing listens on 55432/8081/3001/9333 now. I cannot prove a negative about past actions. |

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `ShiftLibraryValidationService.java` | VERIFIED | `Window(businessDate, ...)` and `describe(DayWindow)` present, wired, no calendar read left |
| `ShiftLibraryGenerationService.java` | VERIFIED | Four business-date reads (:185, 236, 509, 654); `window.describe(dayWindow)` at :854 |
| `BusinessDateJoinGuardTest.java` | VERIFIED | `WIDENED_TARGET_FILES`, widened predicate, union headline scan; 8/8 pass |
| `ShiftLibraryValidationServiceTest.java` | VERIFIED | Anchored cases present; 67/67 pass |
| `ScheduleEnvelopeRepairService.java`, `ScheduleConsistencyRepairService.java`, `ShiftStartMixTargetService.java` | VERIFIED | 7 + 1 + 3 business-date sites |
| `OffendingSample.java` (widened) and `bday-join-guard.md` | VERIFIED | Present and substantive |
| `StaffingRequirementResponse.java`, `StaffingRequirementRepository.java`, `StaffingRequirementService.java`, `StaffingRequirementController.java` | VERIFIED | Additive field, twins, refusals and params all present and wired |
| `StaffingRequirementListBusinessRangeTest.java` | VERIFIED | 11/11 pass |
| `ScheduleResults.tsx`, `client.ts` | VERIFIED | Params, type (`businessDate: string`) and key wired; typecheck clean |

### Key Link Verification

| From | To | Status | Details |
|------|----|--------|---------|
| Validator `findUncoveredWindows` | `Timeslot.getBusinessDate` | WIRED | :230 |
| Generator `computeUncoveredDetails` | shared `Window.describe` | WIRED | :854 |
| Guard test | validator (widened list) | WIRED | `WIDENED_TARGET_FILES` names it; mutation proves the guard sees it |
| Envelope / consistency repair | `AgentShiftAssignment.getDate()` (business date) | WIRED | Keys now agree on both sides |
| `ScheduleResults.tsx` | controller `businessFrom/businessTo` | WIRED | client.ts:240-241 sends them |
| Service | repository business twins | WIRED | `hasBusinessRange` branches call them |
| `toResponseItem` | `Timeslot.getBusinessDate` | WIRED | `t.getBusinessDate()` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Real Data | Status |
|----------|---------------|--------|-----------|--------|
| `ScheduleResults.tsx` Required / Over-under | `requiredPerSlot` | `staffingRequirements.list` to `findLiveByDeskAndBusinessDateRange*` over stored `timeslot.business_date` (NOT NULL, V53) | Yes | FLOWING |
| Validator windows | `demand` | `findAllLiveByDesk` joined to `Timeslot.businessDate` | Yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Affected classes pass | `./gradlew test --tests` on Guard, ValidationService, EnvelopeRepair, ConsistencyRepair, StartMix, ListBusinessRange, ShiftLibraryGeneration* (real `> Task :test`, fresh daemon) | 8 XML files: 8 + 8 + 7 + 1 + 31 + 67 + 14 + 11 tests, 0 failures, 0 errors, 0 skipped | PASS |
| Tests bind to the fix | Temporarily reverted ValidationService:230 to `getDate()`, re-ran Validation + Guard tests | 7 of 75 tests failed (six validator tests plus the guard's headline test); file restored, `git diff` empty | PASS |
| Frontend typechecks | `npx tsc -b --noEmit` in `frontend/` | No output | PASS |

Note: my filtered run replaced `build/test-results` with only these 8 classes; the full-suite evidence is the orchestrator's earlier run, not this directory.

### Probe Execution

SKIPPED. No probes are declared by the plans, and the phase is not a migration or tooling phase.

### Requirements Coverage

Plan frontmatter: 24-01 `[OVNT-05, OVNT-07, SOLV-07, BDAY-02, BDAY-05]`, 24-02 `[SOLV-07, BDAY-02, BDAY-05]`, 24-03 `[OVNT-06, SOLV-07, BDAY-02]`. The union is exactly the six IDs the orchestrator listed.

| Requirement | Source Plans | Status | Evidence |
|-------------|-------------|--------|----------|
| OVNT-05 (validation of overnight templates) | 24-01 | SATISFIED | Truths 1-3, 5, 6, 9, 10 |
| OVNT-06 (allocation sheet and grid show overnight shift consistently) | 24-03 | SATISFIED in code; rendered result needs human look | Truths 25-33 |
| OVNT-07 (calendar-date disclosure) | 24-01 | SATISFIED | Truths 7, 8; prohibition 1 |
| SOLV-07 (demand, coverage and solver resolve the same business date) | 24-01, 24-02, 24-03 | SATISFIED | Validator, repair, start-mix and list endpoint all key on the stored business date; the guard enforces it on four explicit files |
| BDAY-02 (timeslot records its business day) | 24-01, 24-02, 24-03 | SATISFIED | `Item.businessDate`, `businessFrom`/`businessTo` filter the stored column |
| BDAY-05 (guard against bypassing the shared day-window) | 24-01, 24-02 | SATISFIED | Truths 11, 12, 21-23 |

REQUIREMENTS.md already marks all six `[x]` and its traceability table maps them to Phases 18, 20 and 21 only. No requirement is mapped to Phase 24, so there are no orphans. The table does not mention Phase 24; the orchestrator may want to note it there.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| ShiftLibraryGenerationService.java | 239-240 | Windows sorted by clock `startTime`, not anchored minute (WR-01) | Warning | On an anchored desk the generator's per-window `coverage` errors list a business day's 01:00 window before its 21:00 one, while the validator lists them the other way. Labels are shared; order is not. Does not affect which business day a window belongs to. |
| ShiftLibraryValidationService.java | 616-618 | Peak advisory tie-break on clock `startTime` (WR-02) | Warning | Order only, among equal-shortfall advisories on the same business date. Same residual in `ScheduleEnvelopeRepairService.java:315` (candidate tie-break) and `ShiftStartMixTargetService.java:177` (harmless). |
| StaffingRequirementService.java | 122-130 | Business-range cursor decoded without validation (WR-03) | Warning | A tampered or malformed cursor yields HTTP 500 instead of 400. Copied from the pre-existing calendar branches; not a must-have. |
| ShiftLibraryValidationResponse.java | 137-145 | `PeakShortfallAdvisory.date` changed meaning with no javadoc note (IN-01) | Info | Contract documentation only; the message discloses the calendar date. |
| BusinessDateJoinGuardTest.java | 122-125 | Receiver heuristic misses `Timeslot` locals not named `ts` (IN-02) | Info | Guard silence is weaker evidence than it reads. I grepped `src/main/java` for `t.`, `slot.` and `timeslot.` receivers and found only the intentional DTO read at StaffingRequirementService:453. |

No `TBD`, `FIXME` or `XXX` debt markers were found in the phase's changed files by the review or my greps of the key files.

I weighed WR-01 to WR-03 against the must-haves. WR-01 and WR-02 concern list order, which the plans pinned only for `uncoveredWindows` (truth 10, which holds). WR-03 sits outside every truth. None defeats the goal. All three are `open` in `24-REVIEW-DISPOSITION.md` and need triage.

### Human Verification Required

#### 1. D7 per-call state isolation
**Test:** Run the three re-keyed services for two desks concurrently, or in succession on different schedules, and compare with isolated runs.
**Expected:** Identical results; no state survives a call.
**Why human:** Backstop truth with no test. Code reading shows only static finals and per-call locals, which is not behavioural proof.

#### 2. Agent Allocation rows on a 06:00 desk
**Test:** Open a 06:00-anchored desk's schedule, Agent Allocation tab; read Required and Over / under for post-midnight slots on a middle business day and the FINAL business day.
**Expected:** Cells equal the seeded demand; the final day's post-midnight cells are populated.
**Why human:** No frontend test runner. The recorded measurement used raw CDP DOM text, was taken once, and the stack is gone.

#### 3. Six judgment-tier prohibitions
**Test:** Confirm each verdict in the prohibition table above.
**Expected:** All six held.
**Why human:** Non-authoritative LLM judgment only; flagged `unverified-prohibition`.

#### 4. Phase gate acceptance
**Test:** Accept or reject the orchestrator's clean full-suite run in place of 24-03's one-failure run.
**Expected:** Accepted. `MultiDayConstraintDiagnosticTest.multiDay_5agents_5days_shouldScoreZeroHard` stays open in `deferred-items.md` as a flaky wall-clock test.
**Why human:** I did not re-run the roughly 20 min suite, as instructed.

### Stale Verification Reports (do not edit; report only)

Phase 24 changed files listed in other phases' `covered_files`. Per 24-03-SUMMARY, these are re-verify candidates and their digests were deliberately not refreshed: Phase 18 (`client.ts`); Phase 19 (the repair, generation, validation, start-mix and requirement services plus three test classes); Phase 20 (`StaffingRequirementRepository`, `ShiftLibraryGenerationService`, `StaffingRequirementService`, `bday-join-guard.md`); Phase 21 (`client.ts`, `ScheduleResults.tsx`, the repository, generation, validation and requirement services, `bday-join-guard.md`); Phase 22 (`client.ts`, `ScheduleResults.tsx`); Phase 23 none. Milestone close will be blocked on these digests until each is re-verified. I edited none of them.

### Gaps Summary

No gaps. No truth failed and no artifact is missing, stubbed or unwired. The two uncounted truths are a missing test for a backstop invariant (D7) and a live UI measurement I could not repeat. The remaining work is human: those two, the six prohibition verdicts, triage of the five open review findings, and re-verification of phases 18 to 22.

---

_Verified: 2026-10-07_
_Verifier: Claude (gsd-verifier)_
