---
phase: 24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida
plan: 03
subsystem: staffing-requirements-api-and-allocation-ui
tags: [java, spring, jpql, keyset-pagination, business-date, overnight-shifts, react, junit5, tdd]

requires:
  - phase: 24-01
    provides: "Shift-library validation and labels keyed on the business date"
  - phase: 24-02
    provides: "Repair and start-mix services keyed on the business date; BusinessDateJoinGuardTest green with an empty allowlist"
  - phase: 18-20
    provides: "Timeslot.businessDate (V53) as the single stored derivation, DayWindow.businessDateOf"
provides:
  - "StaffingRequirementResponse.Item.businessDate, additive, beside an unchanged calendar date"
  - "GET /desks/{deskId}/staffing-requirements?businessFrom=&businessTo= filtering the stored business_date column, paging on the existing calendar keyset"
  - "Refusal rules (HTTP 400 VALIDATION_FAILED) for half-supplied, mixed, inverted and malformed business ranges"
  - "Agent Allocation Required and Over / under rows loaded by business range and keyed on the server-supplied businessDate"
  - "StaffingRequirementListBusinessRangeTest (11 tests), the backend-first N-2 proof"
affects: [verify-work, phase-24-verification, ship, ScheduleResults, staffing-requirements-contract]

actuals:
  tokens: 7039
  tasks: 3
  commits: 4
plan_head_before: c964c63692072a4df620540b68aee24189693728
plan_head_after: 0ddaffa18b3078d5f17be08a01b0d2b7fab31df0

tech-stack:
  added: []
  patterns:
    - "Additive-then-consume: new response field and new query params first, the one consumer switched second, the published field untouched"
    - "Business-date twin of a paginated query copies the tenant, desk, live, cursor and sort clauses verbatim and changes only the range predicate, so either twin's cursor resumes in the other"
    - "Validate only the new params; leave the legacy pair's silent-ignore behaviour byte-identical"
    - "Live measurement with a no-install CDP driver (cached headless Chromium plus Node's built-in WebSocket) when the Playwright MCP tools are not in the session"

key-files:
  created:
    - src/test/java/com/wfm/service/StaffingRequirementListBusinessRangeTest.java
    - .planning/phases/24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida/deferred-items.md
  modified:
    - src/main/java/com/wfm/dto/StaffingRequirementResponse.java
    - src/main/java/com/wfm/repository/StaffingRequirementRepository.java
    - src/main/java/com/wfm/service/StaffingRequirementService.java
    - src/main/java/com/wfm/controller/StaffingRequirementController.java
    - src/test/resources/bday-join-guard.md
    - frontend/src/api/client.ts
    - frontend/src/pages/ScheduleResults.tsx

key-decisions:
  - "Business-range paging filters the STORED business_date column through two JPQL twins rather than widening the calendar range and re-deriving in memory (BusinessDayPeriodLoader's shape), which would break hasMore and the cursor on a paginated endpoint"
  - "Half-supplied, mixed, inverted and malformed business ranges are refused with IllegalArgumentException (400); the calendar pair keeps its silent-ignore behaviour, pinned by a test"
  - "The malformed-date message names the parameter and the ISO form and never echoes the raw value, because it is returned to the client"
  - "Item.date is not touched (Phase 20 D-10 / SOLV-07); the D-10 comment stays attached to it"

patterns-established:
  - "A cursor minted by the calendar query resumes in the business query and the reverse, because the keyset and sort are shared"

requirements-completed: [OVNT-06, SOLV-07, BDAY-02]

coverage:
  - id: D1
    description: "A business-date range returns exactly the in-range business days' live rows, including the final business day's calendar-next-day post-midnight row, and excludes the prior business day's; the calendar range for the same dates gets both wrong"
    requirement: "SOLV-07"
    verification:
      - kind: unit
        ref: "StaffingRequirementListBusinessRangeTest#businessRange_returnsTheFinalBusinessDaysPostMidnightRows_thatTheCalendarRangeGetsWrong"
        status: pass
    human_judgment: false
  - id: D2
    description: "Every item carries businessDate equal to DayWindow.businessDateOf(06:00, date, startTime) with date still calendar; callers of the unranged and calendar paths gain the field too"
    requirement: "SOLV-07"
    verification:
      - kind: unit
        ref: "StaffingRequirementListBusinessRangeTest#everyItemCarriesItsTimeslotsBusinessDate_andDateStaysCalendar"
        status: pass
    human_judgment: false
  - id: D3
    description: "00:00 control: business range and calendar range return identical items in identical order, and businessDate equals date"
    requirement: "BDAY-02"
    verification:
      - kind: unit
        ref: "StaffingRequirementListBusinessRangeTest#midnightDesk_businessRangeAndCalendarRangeReturnIdenticalItems"
        status: pass
    human_judgment: false
  - id: D4
    description: "Half-supplied, mixed, inverted and malformed business ranges are refused with a 400-mapped IllegalArgumentException; a malformed date does not echo its input"
    requirement: "SOLV-07"
    verification:
      - kind: unit
        ref: "StaffingRequirementListBusinessRangeTest#halfSuppliedBusinessRange_isRefused, #calendarAndBusinessRangesTogether_areRefused, #invertedBusinessRange_isRefused, #malformedBusinessDate_isRefusedAsIllegalArgument"
        status: pass
      - kind: integration
        ref: "curl against the throwaway app on 8081: four refusal requests each returned 400 VALIDATION_FAILED"
        status: pass
    human_judgment: false
  - id: D5
    description: "Paging a business range at limit 1 returns every in-range row exactly once in order; an empty range returns an empty page; the request is tenant-scoped; a half-supplied calendar range is still ignored"
    requirement: "SOLV-07"
    verification:
      - kind: unit
        ref: "StaffingRequirementListBusinessRangeTest#businessRange_pagesToExhaustionAtLimitOne_returnsEveryRowExactlyOnceInOrder, #businessRangeWithNoDemand_returnsAnEmptyPage, #businessRange_isTenantScoped, #halfSuppliedCalendarRange_isStillIgnoredAsBefore"
        status: pass
    human_judgment: false
  - id: D6
    description: "Agent Allocation Required and Over / under cells for post-midnight slots on a middle and on the final business day of a 06:00 desk equal the seeded demand and allocated-minus-required"
    requirement: "OVNT-06"
    verification:
      - kind: manual_procedural
        ref: "CDP evaluate against vite 3001 / app 8081 / DB 55432, desk 2fb25a84-774c-4372-82be-9a1b8f46aa09, schedule 2964ccc6-dd49-411f-8575-987621f0d86d; before and after measurements in this SUMMARY"
        status: pass
    human_judgment: true
    rationale: "No frontend test runner exists (Phase 22 WR-04, deferred). The measurement was taken in headless Chromium through raw CDP rather than the Playwright MCP tools, and read DOM text only; whether the rendered rows look right to an operator is untested. The plan's human-check stands for anyone who wants a look."
  - id: D7
    description: "Phase gate: full suite after a fresh daemon, bare Task :test, summed across every TEST-*.xml by file"
    verification:
      - kind: other
        ref: "./gradlew cleanTest test: 218 XML files, 1535 tests, 1 failure (MultiDayConstraintDiagnosticTest, pre-existing wall-clock flake, green in isolation), 0 errors, 4 skipped"
        status: fail
    human_judgment: true
    rationale: "The plan's gate requires zero failures. One failure occurred in a test acknowledged at the v1.3 close as flaky under suite contention and unrelated to this phase; it passes in isolation. Whether that satisfies the gate is the operator's call, so this item routes to a human."

duration: 32min
completed: 2026-10-07
status: complete
---

# Phase 24 Plan 03: Business-date range and keys for the allocation rows Summary

**GET staffing-requirements gains `businessDate` on every item plus a `businessFrom`/`businessTo` range over the stored business_date column, and the Agent Allocation Required / Over-under rows now load and key on it, so a 06:00 desk's final business day shows its own post-midnight demand (measured live: 3 of 3 days exact, where the old code blanked day one and mis-filed days two and three).**

## Performance

- **Duration:** about 32 min (start time was not captured at launch; estimated from the previous plan's completion at 17:54Z)
- **Started:** approximately 2026-10-07T17:58:00Z
- **Completed:** 2026-10-07T18:30:00Z
- **Tasks:** 3 (Task 1 tracer, Task 2 TDD, Task 3 consumer, live check and gate)
- **Files modified:** 8 in the code commits (500 insertions, 9 deletions), plus this SUMMARY and deferred-items.md

## Accomplishments

- **Additive record field.** `StaffingRequirementResponse.Item` carries `businessDate` immediately after `date`, populated from `Timeslot.getBusinessDate()`. `date` and its D-10 comment are untouched. `saveRequirements`, `calculateErlangC` and `calculateErlangX` gain the field through the single `toResponseItem` construction site.
- **Business range.** Two JPQL twins, `findLiveByDeskAndBusinessDateRange` and `...AfterCursor`, copy the calendar twins' tenant, desk, live, keyset and sort clauses verbatim and filter `t.businessDate`. Both are tenant- and desk-scoped (grep count of that predicate went 10 to 12).
- **Refusals (D-04).** Half-supplied, mixed with `from`/`to`, inverted, and malformed business ranges raise `IllegalArgumentException`, which `GlobalExceptionHandler` maps to 400 `VALIDATION_FAILED`. The calendar pair is not validated and still silently ignores a half-supplied range; a test pins that.
- **Consumer switch (D-05).** `ScheduleResults.tsx` fetches `businessFrom: schedule.periodStartDate, businessTo: schedule.periodEndDate` and keys `requiredPerSlot` on `r.businessDate`. The four Required / Over-under lookups are unchanged. No date arithmetic and no day-start read was added to the browser.
- **Guard document.** `bday-join-guard.md` records the two new twins and the additive field; the Allowlist block still parses to zero entries.

## TDD evidence

- **Task 1 (tracer).** RED: `./gradlew compileTestJava` failed with `method listRequirements ... cannot be applied to given types` and `cannot find symbol: method businessDate()`. This is a compile-time red, which tdd.md's `#3770` classifies as INVALID_RED, so no RED commit was made (the build cannot be compiled to a failing assertion without the signature). The plan's action explicitly specified the compile failure as the red and one commit for the task. GREEN: 3/3 new tests, plus `StaffingRequirementBusinessDateDeleteTest` 7/7 and `StaffingRequirementErlangTest` 10/10, with a real `> Task :test`. The tracer's verify (automated only, interactive end-of-phase mode) was re-run and passed before expansion.
- **Task 2.** Semantic RED, committed as `4999941`: 11 tests, 4 failed, 7 passed. `halfSuppliedBusinessRange_isRefused`, `invertedBusinessRange_isRefused` and `calendarAndBusinessRangesTogether_areRefused` failed with `Expecting code to raise a throwable`; `malformedBusinessDate_isRefusedAsIllegalArgument` failed with `Expecting actual throwable to be an instance of IllegalArgumentException but was DateTimeParseException`. These are exactly the four the plan predicted. The other seven (paging, empty page, tenant scope, calendar half-range, the Task 1 three) already passed, as they exercise Task 1 behaviour. GREEN: 11/11 after `6df5fd9`; `BusinessDateJoinGuardTest` 8/8 and `GlobalExceptionHandlerTest` 5/5 in the same run.
- **Gate compliance.** The plan is `type: execute`, not `type: tdd`; the `test(...)`/`feat(...)` ordering holds for Task 2 and is waived for Task 1 for the reason above.

## Live measurement (Task 3, D-09)

Throwaway stack only: pgvector DB on 55432, app on 8081, vite on 3001. Port 8080 was not listening and was not touched; `dev` and the 5432 database were not touched. The Playwright MCP tools are not available in this session, so the browser half used cached headless Chromium (`chrome-headless-shell`, already in `~/Library/Caches/ms-playwright`) driven over raw CDP from Node's built-in WebSocket, with `Runtime.evaluate` reading DOM text. Nothing was installed. The driver scripts lived in the scratchpad, not the repo.

- **Seed.** Desk `2fb25a84-774c-4372-82be-9a1b8f46aa09`, SLOT mode, day start 06:00, one specialization, 15 hourly slots (22:00 to 03:00 on business Mon 2026-10-05, Tue 10-06, Wed 10-07), 10 agents inserted by SQL with `working_days_known` true and 7 `agent_day_hours` rows each. Schedule `2964ccc6-dd49-411f-8575-987621f0d86d`, solved for 20 s (COMPLETED, hard -4913, infeasible; the allocation view still renders, and this measures demand lookup, not schedule quality).
- **API half (curl).** Business range 2026-10-05..10-07 returned all 15 rows, with the final business day's 01:00 row as `date` 2026-10-08, `businessDate` 2026-10-07, 7 FTE. The calendar range for the same dates returned 12 rows and no 2026-10-08 row. The four refusal cases each returned 400 `VALIDATION_FAILED` with the messages from the code.
- **Browser half, after the fix** (Required row, columns 22:00 / 23:00 / 00:00 / 01:00 / 02:00):
  - Mon 10-05: seeded 1,1,2,3,2; measured 1,1,2,3,2; Over / under +1,+1,+1,+1,+1 (allocated 2,2,3,4,3).
  - Tue 10-06 (middle day): seeded 2,2,4,5,4; measured 2,2,4,5,4; Over / under +1,+1,+2,+2,+2 (allocated 3,3,6,7,6).
  - Wed 10-07 (final day): seeded 3,3,6,7,6; measured 3,3,6,7,6; Over / under 0,+1,+2,+3,+1 (allocated 3,4,8,10,7).
  - Every Over / under figure equals allocated minus required.
- **Browser half, before the fix (control).** With `ScheduleResults.tsx` temporarily restored to the pre-plan commit's version (then put back with `git checkout -- <file>`; status clean), the same schedule read Mon post-midnight blank,blank,blank; Tue 2,3,2 (Monday's figures) and Wed 4,5,4 (Tuesday's figures). That reproduces the audit's N-2 exactly: the middle day shows the previous business day's demand and the post-midnight cells of the first day are empty.
- **Teardown verified.** Chromium, vite, the 8081 app and the container are stopped, `wfm-verify-pg` removed, `frontend/vite.verify.config.ts` deleted, and the Gradle daemons stopped; `lsof` shows nothing on 55432/8081/3001/9333.

## Phase gate

`./gradlew --stop`, then `./gradlew cleanTest test`: a bare `> Task :test` (no UP-TO-DATE), 23 min 8 s. Summed across the 218 `TEST-*.xml` files by filename: **1535 tests, 1 failure, 0 errors, 4 skipped**. `BusinessDateJoinGuardTest` 8/8 and `StaffingRequirementListBusinessRangeTest` 11/11 are inside that run.

**The plan's zero-failure condition is not met.** The one failure is `MultiDayConstraintDiagnosticTest.multiDay_5agents_5days_shouldScoreZeroHard` (hard score -610 against a -500 floor, a 90 s wall-clock solve). It is the test the v1.3 close already acknowledged as flaky under suite contention, it builds its own hand-made schedule, and none of this phase's code is on its path. Re-run alone with `--rerun-tasks` it passed (2 tests, 0 failures, 3 min 44 s). That isolated run was taken after the aggregate above was read, so it did not disturb it. I did not run the suite a second time hoping for green. Logged in `deferred-items.md`.

## D-10: verification documents and stale digests

No `*-VERIFICATION.md` differs from the commit that added 24-01-PLAN.md (count 0). No `covered_digest` was refreshed. By matching this phase's changed non-planning files against each document's `covered_files`, these earlier phases list a file this phase changed and are **re-verify; digest NOT refreshed**:

- Phase 18: `frontend/src/api/client.ts`
- Phase 19: `ScheduleEnvelopeRepairService`, `ShiftLibraryGenerationService`, `ShiftLibraryValidationService`, `ShiftStartMixTargetService`, `StaffingRequirementService` and three of their test classes
- Phase 20: `StaffingRequirementRepository`, `ShiftLibraryGenerationService`, `StaffingRequirementService`, `bday-join-guard.md`
- Phase 21: `client.ts`, `ScheduleResults.tsx`, `StaffingRequirementRepository`, `ShiftLibraryGenerationService`, `ShiftLibraryValidationService`, `StaffingRequirementService`, `bday-join-guard.md`
- Phase 22: `client.ts`, `ScheduleResults.tsx`
- Phase 23: none

(This list covers the whole phase 24 diff since the 24-01 plan-add commit, not only this plan's files.)

## Task Commits

1. **Task 1 (tracer): business-date range and businessDate** - `2a4e055` (feat)
2. **Task 2: refusal rules** - `4999941` (test, red), `6df5fd9` (feat, green; also carries the `bday-join-guard.md` edit)
3. **Task 3: consumer switch** - `0ddaffa` (feat)

**Plan metadata:** recorded in the follow-up `docs(24-03)` commit.

## Files Created/Modified

- `src/main/java/com/wfm/dto/StaffingRequirementResponse.java` - `businessDate` component after `date`, with a javadoc stating which is which
- `src/main/java/com/wfm/repository/StaffingRequirementRepository.java` - the two business-date twins
- `src/main/java/com/wfm/service/StaffingRequirementService.java` - new `listRequirements` signature, validation, `parseBusinessDate`, `toResponseItem` passes the stored business date
- `src/main/java/com/wfm/controller/StaffingRequirementController.java` - optional `businessFrom`/`businessTo` params
- `src/test/java/com/wfm/service/StaffingRequirementListBusinessRangeTest.java` - 11 tests, 06:00 six-row fixture and a 00:00 control
- `src/test/resources/bday-join-guard.md` - two paragraphs; Allowlist block untouched
- `frontend/src/api/client.ts` - `businessFrom`/`businessTo` params, `StaffingRequirement.businessDate`
- `frontend/src/pages/ScheduleResults.tsx` - business-range fetch, key on `r.businessDate`, rewritten comment
- `deferred-items.md` (phase dir) - the flaky gate failure

## Decisions Made

- Filter the stored `business_date` column with twin queries; do not use the widen-then-derive stream filter (24-RESEARCH Q-C, Pitfall 4).
- Be strict on the new params and leave the old pair alone (24-RESEARCH Assumption A3).
- Keep the message for a bad date free of the raw input.

## Deviations from Plan

**1. [Tooling substitution] Browser measurement through raw CDP instead of Playwright MCP**
- **Found during:** Task 3 live check
- **Issue:** The Playwright MCP tools are not in this session, and `playwright` is not installed in the repo or `frontend/node_modules`; the plan forbids installs (T-24-SC).
- **Fix:** Drove the already-cached headless Chromium over CDP with Node 24's built-in WebSocket, evaluating DOM text only (no screenshots, consistent with the memory note that they never settle). This is the plan's `browser_evaluate` approach by another route. No file in the repo was added; the scripts lived in the scratchpad.
- **Verification:** Before and after readings on the same schedule differ exactly as the audit predicts.

**2. [Gate not met] One full-suite failure (see Phase gate)**
- A known flaky wall-clock test failed under suite load and passed alone. Not fixed, not retried until green, recorded in `deferred-items.md`, and coverage item D7 routes it to a human.

**3. [Process] Task 1 has one commit, not a RED/GREEN pair**
- Per the plan's action and because a compile-only red is INVALID_RED under tdd.md. Task 2 has the full pair.

**Total deviations:** 3 (1 tooling, 1 gate shortfall, 1 process). **Impact on plan:** none on the delivered behaviour. The gate shortfall is the one that needs an operator's decision.

## Issues Encountered

- `$B:src/...` in zsh was parsed as a history modifier while counting the pre-plan tenant-predicate occurrences; rewritten as `${B}:src/...`. No effect on committed content.
- The first `bootRun` launch used a scratchpad log path that did not exist yet; created the directory and relaunched before anything was seeded.

## Known Stubs

None.

## Threat Flags

None. The new query params and queries are exactly the surface the plan's threat model (T-24-05 to T-24-09) already covers: tenant and desk predicates proven by `businessRange_isTenantScoped` and a grep count, named parameters only, bad input refused with 400 without echoing it, the limit still clamped at 1000, and the live check kept to the throwaway stack.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 24's three plans are complete; the phase is ready for verification. The verifier should treat coverage items D6 (rendered rows, measured via CDP DOM text, not Playwright) and D7 (one flaky gate failure) as `human_needed`.
- Earlier phases 18 to 22 are listed above for re-verification; their digests were deliberately not refreshed.
- **Do not push.** `.github/workflows/deploy.yml` deploys to the live `dev` system on every push to this branch. Pushing is the operator's decision after verification.

## Self-Check: PASSED

Files verified present: the new test class, the five changed main files, `bday-join-guard.md`, `client.ts`, `ScheduleResults.tsx`, `deferred-items.md`.
Commits verified as ancestors of HEAD: `2a4e055`, `4999941`, `6df5fd9`, `0ddaffa`. `commits: 4` measured from `git rev-list --count c964c63..HEAD`.
Teardown verified: no container, no copied vite config, nothing listening on 55432, 8081, 3001 or 9333.
The check is of existence and of the claims above; it does not certify the gate, which is recorded as not fully met.

---
*Phase: 24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida*
*Completed: 2026-10-07*
