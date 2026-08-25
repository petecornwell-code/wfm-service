---
phase: 13-per-day-hours-visibility
plan: 01
subsystem: api
tags: [spring-boot, jpa, react, roster, agent-day-hours]

# Dependency graph
requires:
  - phase: 09-agent-data-model-foundation
    provides: agent_day_hours storage contract (D-04/D-09), SolverService.resolveEffectiveHours precedent
  - phase: 10-enriched-upload-parsing
    provides: EnrichedColumnLayout.DAY_ORDER / dayHeader(day) shared header source
provides:
  - "DeskAgentResponse.dayHours: Map<DayOfWeek, DayHoursEntry> — always 7 keys, hasRow/hours/dayOffType/effectiveHours"
  - "DeskAgentService reads all hours resolution from agent_day_hours + schedule default, never Agent.contractedHoursPerDay or Desk.defaultContractedHoursPerDay"
  - "DeskAgentService.resolveScheduleDefault (D-06/P-01 fallback) and loadDayHoursByAgent (bulk per-desk fetch)"
  - "Roster UI: collapsed Hours/Day min-max summary, expand/collapse chevron, expanded per-weekday detail row with 5 display states"
affects: [13-per-day-hours-visibility-plan-02, 13-per-day-hours-visibility-plan-03, 13-per-day-hours-visibility-plan-04]

# Actuals (#2632)
actuals:
  tokens: 8227
  tasks: 2
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Bulk per-desk fetch-then-group (Map<UUID, Map<DayOfWeek, AgentDayHours>>) mirroring the existing pendingByAgent PTO pattern — single query, no N+1"
    - "Nested DTO record (DayHoursEntry) following the existing SpecSummary nested-record convention"
    - "containsKey/EnumMap-based presence check instead of null-or-zero, to keep a stored 0.00 row distinguishable from an absent row"

key-files:
  created:
    - src/test/java/com/wfm/service/DeskAgentServiceReadPathTest.java
  modified:
    - src/main/java/com/wfm/dto/DeskAgentResponse.java
    - src/main/java/com/wfm/service/DeskAgentService.java
    - src/test/java/com/wfm/service/DeskAssignmentTemplateServiceTest.java
    - src/test/java/com/wfm/service/DeskAssignmentTemplateFilterTest.java
    - frontend/src/api/client.ts
    - frontend/src/pages/DeskAgents.tsx

key-decisions:
  - "P-01/P-02/P-03 adopted as written in PLAN.md's <planner_decisions> — schedule-derived D-06 default via ScheduleRepository.findByTenantIdAndDeskIdOrderByCreatedAtDesc, always-7-key dayHours DTO shape, effectiveContractedHoursPerDay recomputed as the max of the 7 resolved weekday values"
  - "Deviation: relocated the pre-existing editHoursAgentId/editHours/startEditHours/saveHours inline-edit triad into a new expanded-row <tr> scaffold in Task 1 (one task earlier than the plan's literal 'expanded row body is built in Task 2' wording) — required to keep npm run build green under noUnusedLocals:true once the collapsed cell's only reference to that triad was replaced by the expand toggle. Task 2 then added the empty-state note and 7-day grid above the same scaffold, exactly as the plan's Task 2 action describes"

requirements-completed: [MDL-02, UPL-03, UPL-04, UPL-05]

coverage:
  - id: D1
    description: "Roster read path resolves per-weekday hours from agent_day_hours, ignoring the retired Agent.contractedHoursPerDay scalar and the Desk-level default"
    requirement: "MDL-02"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceReadPathTest.java#rosterIgnoresScalar_whenScalarDisagreesWithPerDayRows"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceReadPathTest.java#absentWeekdayRow_resolvesToScheduleDefault_notDeskDefault"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceReadPathTest.java#noPersistedSchedule_fallsBackToEightHours"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceReadPathTest.java#mostRecentlyCreatedScheduleWins"
        status: pass
    human_judgment: false
  - id: D2
    description: "Explicit-zero and MANDATORY/PTO rows are distinguished from a not-set weekday (D-04)"
    requirement: "UPL-03"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceReadPathTest.java#explicitZeroRow_isNotTreatedAsNotSet"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceReadPathTest.java#dayHoursMapAlwaysHasSevenEntries"
        status: pass
    human_judgment: false
  - id: D3
    description: "A MANDATORY-labelled weekday is reported with its label, not as a bare 0"
    requirement: "UPL-04"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceReadPathTest.java#mandatoryAndPtoRows_keepTheirLabels"
        status: pass
    human_judgment: false
  - id: D4
    description: "A PTO-labelled weekday is reported with its label, not as a bare 0"
    requirement: "UPL-05"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskAgentServiceReadPathTest.java#mandatoryAndPtoRows_keepTheirLabels"
        status: pass
    human_judgment: false
  - id: D5
    description: "Roster UI: collapsed Hours/Day cell shows a single value or min-max range, expands to a 7-day grid with 5 distinct display states (MANDATORY, PTO, explicit-zero, worked, not-set) and no new table column"
    verification:
      - kind: automated_ui
        ref: "npm --prefix frontend run build (tsc -b + vite build)"
        status: pass
    human_judgment: true
    rationale: "Visual correctness (badge colours, chevron glyph, layout, no horizontal overflow) requires a live desk with an enriched upload and cannot be proven by a type-check alone — Task 2's <human-check> and the plan-level manual walkthrough were not run in this session (no live DB/BambooHR-configured environment available) and are recorded as open unrun-verify entries in .planning/WINDOWS.md"

duration: 22min
completed: 2026-08-22
status: complete
---

# Phase 13 Plan 01: Roster Per-Day Hours Read Path Summary

**Roster and its API now resolve every contracted-hours figure from `agent_day_hours` (schedule-default fallback, D-06), replacing the retired `Agent.contractedHoursPerDay`/`Desk.defaultContractedHoursPerDay` read path, and the roster UI gains a collapsed min-max summary plus an expandable per-weekday detail row with 5 distinct display states.**

## Performance

- **Duration:** 22 min
- **Started:** 2026-08-21T23:59:11Z
- **Completed:** 2026-08-22T00:21:15Z
- **Tasks:** 2 (Task 1 tracer/tdd, Task 2 auto)
- **Files modified:** 7 (1 created, 6 modified)

## Accomplishments
- Closed audit finding I-1/F-1: `DeskAgentService.toResponse` (all 4 call sites) now resolves hours exclusively from `agent_day_hours`, with a schedule-derived "not set" default (D-06/P-01) instead of the desk-level default
- `DeskAgentResponse` gained an always-7-key `dayHours: Map<DayOfWeek, DayHoursEntry>` field (`hasRow`/`hours`/`dayOffType`/`effectiveHours`), with `effectiveContractedHoursPerDay` recomputed as the max of the 7 resolved weekday values (P-03)
- New `DeskAgentServiceReadPathTest` (8 tests) proves the assumption-delta invariant that a disagreeing scalar is ignored, plus D-06 fallback ordering, explicit-zero/label distinction, and the 7-key map guarantee
- Roster UI: `Hours/Day` cell now shows a collapsed min-max summary with an expand/collapse chevron; expanding renders a 7-column Mon–Sun grid with MANDATORY/PTO badges, explicit-zero, worked-hours, and not-set (tooltip) states, each visually distinct per 13-UI-SPEC.md

## Task Commits

1. **Task 1: End-to-end "roster shows the uploaded per-day hours" — one path only** (tracer/tdd)
   - `59fcf25` test(13-01): add failing regression suite for roster per-day hours read path
   - `e95b6ba` feat(13-01): resolve roster hours from agent_day_hours read path (I-1/F-1)
2. **Task 2: Expandable per-weekday detail row with the five display states** (auto)
   - `17d24f8` feat(13-01): expandable per-weekday detail row with five display states

**Plan metadata:** commit pending (this SUMMARY + STATE/ROADMAP/REQUIREMENTS update)

_TDD gate check: RED (`59fcf25`) precedes GREEN (`e95b6ba`) — confirmed by re-running the new test against the pre-change DTO/service via a temporary local `git stash` of only the two production files, observing a compile failure, then restoring._

## Files Created/Modified
- `src/main/java/com/wfm/dto/DeskAgentResponse.java` - adds nested `DayHoursEntry` record and `dayHours` map field
- `src/main/java/com/wfm/service/DeskAgentService.java` - `ScheduleRepository` injected; `resolveScheduleDefault`/`loadDayHoursByAgent` helpers; `toResponse` and all 4 call sites rewired off the desk-default scalar
- `src/test/java/com/wfm/service/DeskAgentServiceReadPathTest.java` - new 8-test read-path regression suite
- `src/test/java/com/wfm/service/DeskAssignmentTemplateServiceTest.java` - fixture arity fix (trailing `Map.of()`)
- `src/test/java/com/wfm/service/DeskAssignmentTemplateFilterTest.java` - fixture arity fix (trailing `Map.of()`)
- `frontend/src/api/client.ts` - `DayHoursEntry` interface + `DeskAgent.dayHours` field
- `frontend/src/pages/DeskAgents.tsx` - collapsed summary + chevron, `expandedAgentId` state, expanded 7-day grid, `DayCell` component

## Decisions Made
- Adopted PLAN.md's P-01/P-02/P-03 planner decisions verbatim (schedule-derived D-06 default; always-7-key DTO shape; max-of-7 `effectiveContractedHoursPerDay`)
- See "Deviations from Plan" below for the one implementation-order deviation

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Relocated the inline hours-edit triad into the expanded row one task earlier than described**
- **Found during:** Task 1 (frontend leg, step 3)
- **Issue:** PLAN.md's Task 1 action says to replace the collapsed `Hours/Day` cell's contents with the new min-max summary and states "the expanded row body is built in Task 2." Doing exactly that removes the only remaining reference to `editHoursAgentId`/`editHours`/`startEditHours`/`saveHours` from the file, which fails the project's `noUnusedLocals: true` TypeScript setting — and Task 1's own acceptance criteria require `npm --prefix frontend run build` to exit 0.
- **Fix:** Added the expanded-row `<tr colSpan={14}>` scaffold in Task 1, containing only the relocated (unchanged) inline editor. Task 2 then added the empty-state note and 7-day grid above it, exactly as its own action text describes ("current state after Task 1... existing inline hours editor, relocated into this expanded row"), which independently corroborates this was the intended sequencing — Task 2's own text assumes Task 1 already made the fan-out reachable.
- **Files modified:** frontend/src/pages/DeskAgents.tsx
- **Verification:** `npm --prefix frontend run build` exits 0 after both Task 1 and Task 2; `saveHours`/`startEditHours` still present and referenced (grep confirmed)
- **Committed in:** `e95b6ba` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 bug/build-gate)
**Impact on plan:** No scope creep — the relocation Task 2 itself describes was simply started one commit earlier than the plan's prose implied, to keep every intermediate commit's build green.

## Issues Encountered
- Full `./gradlew test` run took ~7.5 minutes (includes solver test suite) — ran in background, confirmed `BUILD SUCCESSFUL`.
- Task 2's `<human-check>` visual walkthrough and the plan-level "manual walkthrough" verification step were not run in this session — no live database or BambooHR-configured environment was available to the executor. Recorded as `unrun-verify` entries in `.planning/WINDOWS.md` (3 entries total, including this deviation).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `DeskAgentResponse.dayHours` and the schedule-default resolution helper are now available for plan 13-02 (Excel export D-02) and plan 13-03 (per-cell editing D-03/D-05/D-07) to build on directly
- Recommend a human UAT pass against a live desk with an enriched upload before shipping, to close the 3 open `unrun-verify` items in `.planning/WINDOWS.md`

## Self-Check: PASSED

- All 5 key files confirmed present on disk (`[ -f ]`)
- All 3 task commit hashes confirmed present in `git log --oneline --all`

---
*Phase: 13-per-day-hours-visibility*
*Completed: 2026-08-22*
