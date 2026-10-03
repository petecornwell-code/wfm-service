---
phase: 21-overnight-shift-templates
plan: 09
subsystem: api
tags: [java, typescript, day-window, branded-type, schedule-payload, violation-detail]

# Dependency graph
requires:
  - phase: 21-overnight-shift-templates (plan 21-02)
    provides: "The widened frontend Desk interface this plan's client.ts edits sit alongside; confirmed the file's current shape before adding to it"
  - phase: 21-overnight-shift-templates (plan 21-03)
    provides: "ViolationDetail's four typed fields (businessDate, calendarDate, startTime, endTime) on the backend DTO, which this plan's TypeScript contract mirrors so the frontend allocation-grid parser (21-10) can move off the display-string parse the same way the backend export already did"
provides:
  - "ScheduleSummary.dayStart (Java record component) -- the anchor the schedule was SOLVED against, populated at both construction sites (ScheduleService.toSummary, ScheduleController.toSummary) from Schedule.getDayStart(), never from the desk -- rides the fast 2s-poll summary payload, not only the slower detail response"
  - "frontend/src/api/client.ts: ScheduleSummary.dayStart?: string (optional, inherited by ScheduleDetail); ViolationDetail gains businessDate/calendarDate/startTime/endTime matching the backend's 21-03 fields"
  - "frontend/src/utils/dayWindow.ts -- a branded DayOffset numeric type plus the anchored arithmetic module (anchoredAt, anchoredStartMinute, anchoredEndMinute, anchoredDurationMinutes, anchoredContains, anchoredToHHMM, anchoredPlusWithinDay, MINUTES_PER_DAY, businessDateFromCalendarDateAndTime, calendarDateFromBusinessDateAndOffset), with zero consumers -- the structural guard 21-10 converts ScheduleResults.tsx's seven defect sites against"
affects: [21-10]

# Actuals (#2632)
actuals:
  tokens: 4444
  tasks: 2
  commits: 2
  plan_head_before: d5fe1b3c6eb1c35c7e697425ae125af1b5eb813a
  plan_head_after: 967f4d6aa395a571cbcc35dde57e3cc7ae4b4377

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A branded numeric type (`number & { readonly __brand: 'DayOffset' }`) as the structural guard on a frontend surface with no test runner -- the compiler enforces what a scan-based guard would enforce elsewhere, following the same compiler-forced idiom Phase 19 used for the backend's DayWindow re-anchoring"
    - "A deliberate 1:1 cross-language port: frontend/src/utils/dayWindow.ts mirrors backend com.wfm.util.DayWindow's bound-instance method names and positional rule exactly (a time equal to the anchor is minute 0 in a START position, MINUTES_PER_DAY in an END position), rather than a second, divergent implementation of midnight-boundary arithmetic"
    - "Testing that two independent construction sites of the same record agree: instantiate the second site's class directly (ScheduleController) with mocked collaborators, stub the method that calls its private toSummary, and assert field equality against the first site's output -- no new test file, extending the existing ScheduleSummaryReadTest.java per the plan's explicit instruction"

key-files:
  created:
    - frontend/src/utils/dayWindow.ts
  modified:
    - src/main/java/com/wfm/dto/ScheduleSummary.java
    - src/main/java/com/wfm/service/ScheduleService.java
    - src/main/java/com/wfm/controller/ScheduleController.java
    - frontend/src/api/client.ts
    - src/test/java/com/wfm/service/ScheduleSummaryReadTest.java

key-decisions:
  - "The anchor rides the schedule payload, read from Schedule.getDayStart() at both construction sites, never from the desk -- the anchor is part of the solved schedule's identity (what the score was computed against), so a later desk re-anchor cannot retroactively change what an already-solved schedule reports (D-15)"
  - "dayWindow.ts's anchoredPlusWithinDay keeps its `minutes` parameter a plain `number`, not `DayOffset` -- it is an additive delta to apply to a base time, not a position measured from the anchor, so branding it would misrepresent what the value means (left to Claude's Discretion per 21-CONTEXT)"
  - "The two ported date statics (businessDateFromCalendarDateAndTime, calendarDateFromBusinessDateAndOffset) take and return ISO 'YYYY-MM-DD' strings rather than a Date object, matching every other date field in this codebase's TypeScript contracts (periodStartDate, periodEndDate, etc.) and avoiding any local-timezone date-shift risk by doing day arithmetic in UTC internally"
  - "Test 3 (both construction sites agree) is proved by directly instantiating ScheduleController with mocked SolverService/ScheduleExportService/AgentDayOffService and the test's existing deskRepository mock, stubbing solverService.stopSolve to return the same Schedule fixture used against ScheduleService, then asserting dayStart() equality -- confirms the controller's private toSummary and the service's private toSummary cannot disagree, without duplicating either method's logic in the test"

patterns-established: []

requirements-completed: [OVNT-02]

# Coverage metadata (#1602)
coverage:
  - id: D1
    description: "Both schedule payload construction sites carry the schedule's own day-start anchor, read from the schedule rather than the desk; a 21:00-anchored schedule round-trips 21:00 and a 00:00-anchored one round-trips 00:00 (not null, not absent)"
    requirement: "OVNT-02"
    verification:
      - kind: unit
        ref: "ScheduleSummaryReadTest#summaryCarriesTheSchedulesOwnAnchor"
        status: pass
      - kind: unit
        ref: "ScheduleSummaryReadTest#midnightAnchoredScheduleCarriesMidnightNotNull"
        status: pass
      - kind: unit
        ref: "ScheduleSummaryReadTest#bothConstructionSitesAgreeOnTheAnchor"
        status: pass
      - kind: integration
        ref: "./gradlew compileJava compileTestJava (widened ScheduleSummary record compiler-forced across both construction sites)"
        status: pass
    human_judgment: false
  - id: D2
    description: "frontend/src/utils/dayWindow.ts exists, exports a branded DayOffset type, mirrors the backend DayWindow's instance method names and positional rule, leaks no unbranded offset, has zero consumers, and both frontend build gates stay clean"
    requirement: "OVNT-02"
    verification:
      - kind: other
        ref: "npm --prefix frontend exec -- tsc -b frontend --force (clean)"
        status: pass
      - kind: other
        ref: "npm --prefix frontend run build (clean)"
        status: pass
      - kind: other
        ref: "grep -c '__brand' dayWindow.ts (1); grep -cE 'export function [a-zA-Z]+\\([^)]*\\): number' dayWindow.ts (0); grep -c 'dayWindow' ScheduleResults.tsx (0); grep -cE '\"test\"|vitest|jest' package.json (0)"
        status: pass
    human_judgment: false

# Metrics
duration: ~20min
completed: 2026-10-03
status: complete
---

# Phase 21 Plan 09: Day-Start Anchor Payload and Branded Offset Module Summary

**`ScheduleSummary.dayStart` now rides both schedule payloads from the schedule's own stored anchor (never the desk), the TypeScript contracts carry it plus 21-03's structured violation fields, and a new `frontend/src/utils/dayWindow.ts` ports the backend's anchored arithmetic behind a branded `DayOffset` type with zero consumers yet.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-10-03T01:40:00Z (approx.)
- **Completed:** 2026-10-03T01:47:00Z (approx.)
- **Tasks:** 2 of 2
- **Files modified:** 6 (1 created, 5 modified)

## Accomplishments
- `ScheduleSummary` (the Java record the results page polls every 2s) gained a `LocalTime dayStart` component, placed immediately after `incrementMinutes`. Both of the record's exactly-two construction sites — `ScheduleService.toSummary` and `ScheduleController.toSummary` — now read it from `Schedule.getDayStart()`, never from the desk, so a later desk re-anchor cannot retroactively change what an already-solved schedule reports. `ScheduleDetailResponse` already carried the same field since Phase 19 and was confirmed unmodified.
- `frontend/src/api/client.ts`'s `ScheduleSummary` interface gained `dayStart?: string` (optional, so an older cached payload degrades to today's render rather than a half-built disclosure); `ScheduleDetail` inherits it automatically via `extends`. `ViolationDetail` gained the four structured fields 21-03 already emits on the backend (`businessDate`, `calendarDate`, `startTime`, `endTime`, all `string | null`), closing the TypeScript side of the gap 21-10 needs to stop parsing `timeslotLabel` as data.
- `frontend/src/utils/dayWindow.ts` is a new, zero-dependency module: a branded `DayOffset` numeric type (`number & { readonly __brand: 'DayOffset' }`) that a plain number cannot satisfy, an `anchoredAt(dayStart)` factory returning a bound `DayWindow` with `anchoredStartMinute`/`anchoredEndMinute`/`anchoredDurationMinutes`/`anchoredContains`/`anchoredToHHMM`/`anchoredPlusWithinDay`, the `MINUTES_PER_DAY` constant, and two ported date statics (`businessDateFromCalendarDateAndTime`, `calendarDateFromBusinessDateAndOffset`). It reproduces the backend `com.wfm.util.DayWindow`'s positional rule exactly (a time equal to the anchor is minute `0` in a START position, `MINUTES_PER_DAY` in an END position) and its three-branch duration-wrap shape verbatim.
- Three new tests in `ScheduleSummaryReadTest.java` prove the anchor round-trips at both a `21:00` and a `00:00` anchor (the latter asserting `LocalTime.MIDNIGHT`, not `null`, confirming the absent-field degradation stays reserved for genuinely old payloads), and that `ScheduleService`'s and `ScheduleController`'s independent construction sites cannot disagree about the same schedule's anchor.
- No existing frontend behaviour changed: `ScheduleResults.tsx` and `frontend/package.json` are confirmed byte-for-byte unmodified by this plan (the module has zero consumers), and no new dependency was added anywhere.

## Task Commits

Each task was committed atomically:

1. **Task 1: Carry the schedule's own anchor on both payloads, and land the TypeScript contracts** - `0a11ff7` (feat)
2. **Task 2: Create the branded anchored-time module the grid will be forced through** - `967f4d6` (feat)

**Plan metadata:** this SUMMARY commit (docs: complete plan)

## Files Created/Modified
- `src/main/java/com/wfm/dto/ScheduleSummary.java` — `dayStart` record component added after `incrementMinutes`, documented as the solved schedule's identity, not a live desk value
- `src/main/java/com/wfm/service/ScheduleService.java` — `toSummary` passes `s.getDayStart()` into the widened constructor
- `src/main/java/com/wfm/controller/ScheduleController.java` — `toSummary` passes `s.getDayStart()` into the widened constructor
- `frontend/src/api/client.ts` — `ScheduleSummary.dayStart?: string`; `ViolationDetail` gains `businessDate`/`calendarDate`/`startTime`/`endTime`
- `src/test/java/com/wfm/service/ScheduleSummaryReadTest.java` — three new tests (21:00 anchor, 00:00 anchor, both-construction-sites-agree)
- `frontend/src/utils/dayWindow.ts` — new file: `DayOffset`, `MINUTES_PER_DAY`, `DayWindow` interface, `anchoredAt`, `businessDateFromCalendarDateAndTime`, `calendarDateFromBusinessDateAndOffset`

## Exported Surface of `frontend/src/utils/dayWindow.ts`

Recorded verbatim for 21-10, which is written against this surface and not against the plan's description of it:

```typescript
export type DayOffset = number & { readonly __brand: 'DayOffset' }
export const MINUTES_PER_DAY = 1440

export interface DayWindow {
  anchoredStartMinute(t: string): DayOffset
  anchoredEndMinute(t: string): DayOffset
  anchoredDurationMinutes(start: string, end: string): DayOffset
  anchoredContains(outerStart: string, outerEnd: string, innerStart: string, innerEnd: string): boolean
  anchoredToHHMM(offset: DayOffset): string
  anchoredPlusWithinDay(base: string, minutes: number): string
}

export function anchoredAt(dayStart: string): DayWindow

export function businessDateFromCalendarDateAndTime(
  dayStart: string, calendarDate: string, timeOfDay: string
): string

export function calendarDateFromBusinessDateAndOffset(
  dayStart: string, businessDate: string, minutesFromDayStart: DayOffset | number
): string
```

All string-typed time/date parameters accept `"HH:MM"` or `"HH:MM:SS"` (seconds normalized off internally) for times, and ISO `"YYYY-MM-DD"` for dates. `anchoredToHHMM` and `anchoredPlusWithinDay` return absolute `"HH:MM"` clock strings (not offsets). `calendarDateFromBusinessDateAndOffset` throws `RangeError` when `minutesFromDayStart` is outside `[0, MINUTES_PER_DAY)` — the same exclusive-upper-bound range the backend static documents.

## Decisions Made

- The anchor rides the schedule payload, not the desk — see `key-decisions` in frontmatter (D-15).
- `anchoredPlusWithinDay`'s `minutes` parameter stays a plain `number`: it is a delta to add to a base time, not a position measured from the anchor, so branding it would be semantically wrong even though it is "a raw number of minutes" in the literal sense the plan's acceptance criteria describe.
- The two date-helper functions use ISO date strings, matching every other date field in this codebase's TypeScript contracts, and do their day arithmetic in UTC internally to avoid a local-timezone shift.
- Test 3's "both construction sites agree" proof instantiates `ScheduleController` directly in `ScheduleSummaryReadTest.java` (not a new test file, per the plan's explicit instruction) and stubs `SolverService.stopSolve` — the shortest real path to the controller's private `toSummary` without adding a Spring context or MockMvc.

## Deviations from Plan

None - plan executed exactly as written. Both tasks' acceptance criteria and verify commands pass as specified; no out-of-scope files were touched; no dependency was added.

## Issues Encountered

None. Task 1's three tests passed on first run after the production change; Task 2's module compiled clean against both `tsc -b --force` and `npm run build` on first attempt.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `ScheduleSummary.dayStart` and `ViolationDetail`'s four structured fields are live on both payloads and both TypeScript contracts; `frontend/src/utils/dayWindow.ts` exists with the exact exported surface recorded above. Nothing reads either yet — this plan's own "no existing frontend behaviour changes" claim is true by construction (`ScheduleResults.tsx` is confirmed unmodified).
- 21-10 can now convert `ScheduleResults.tsx`'s seven confirmed defect sites against `dayWindow.ts`'s branded accessors and move its allocation-grid parser off `timeslotLabel` onto the four structured violation fields.
- `requirements-completed` lists `[OVNT-02]` per this plan's own frontmatter, but it is not yet marked Complete in REQUIREMENTS.md: `requirements.ready-ids` confirms 0/1 ready, since OVNT-02 is shared with sibling plans 21-04, 21-06, 21-10 and 21-12, none of which have a SUMMARY yet. Expected shared-ID gating (#2388), not a gap.
- No blockers for 21-10 or any other sibling plan. This plan's changes are additive (one new record component, one optional TypeScript field, four new TypeScript fields, one new file with zero consumers) and touch no file any sibling plan needs reworked.

---
*Phase: 21-overnight-shift-templates*
*Completed: 2026-10-03*

## Self-Check: PASSED

- `src/main/java/com/wfm/dto/ScheduleSummary.java`, `src/main/java/com/wfm/service/ScheduleService.java`, `src/main/java/com/wfm/controller/ScheduleController.java`, `frontend/src/api/client.ts`, `src/test/java/com/wfm/service/ScheduleSummaryReadTest.java` and `frontend/src/utils/dayWindow.ts` all confirmed present on disk.
- Commits `0a11ff7` and `967f4d6` confirmed present in `git log --oneline --all`.
- Full unfiltered `./gradlew test` run (post-`--stop` reset): **1284 tests, 0 failures, 0 errors, 4 pre-existing skips, 201 classes** (baseline after 21-08 was 1281 tests / 201 classes; +3 tests from this plan's Task 1, +0 classes since no new test class was created).
- `npm --prefix frontend exec -- tsc -b frontend --force` — clean, no `error TS` lines.
- `npm --prefix frontend run build` — clean, `vite build` succeeded.
