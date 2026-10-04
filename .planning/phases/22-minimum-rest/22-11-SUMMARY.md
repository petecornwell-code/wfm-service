---
phase: 22-minimum-rest
plan: 11
subsystem: scheduling
tags: [rest-waiver, disclosure, dto, timefold, java, react]

# Dependency graph
requires:
  - phase: 22-minimum-rest
    provides: "22-08's buildRestWaiverDisclosure/RestWaiverDisclosure/RestWaiverEntry and ScheduleSummary.appliedRestWaiverCount/unusedRestWaiverCount; 22-10's ScheduleResults.tsx header badge and Rest Waivers tab (the surfaces this plan re-points at a new gate)"
provides:
  - "ScheduleDetailResponse.minimumRestMinutes/appliedRestWaiverCount/unusedRestWaiverCount -- the schedule's own snapshotted configured-rest signal and both waiver counts, populated on every detail-response path including the DB-fallback that never runs loadSnapshotData"
  - "ScheduleResults.tsx's header badge and RestWaiversTab's `configured` gate both re-pointed at schedule.minimumRestMinutes, immune to loadDetail's setSchedule(data) full-state replace"
  - "ScheduleOutputService.buildRestWaiverDisclosure's anchor resolved through the codebase's null-coalescing shape (WR-01 closed)"
affects: [22-12]

# Actuals (#2632)
actuals:
  tokens: 6182
  tasks: 2
  commits: 5
  plan_head_before: f8cf81d8a51be9fab87185609aee286cd03a75a9
  plan_head_after: aee5a1f9c1324f18e861dde978c8bcb478982511

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Schedule-identity signal as a mapped @Column on the response DTO, not a derived-field presence check -- minimumRestMinutes joins dayStart as the second snapshotted scalar this phase's detail response carries directly, so a caller never has to infer configured-or-not from whether some OTHER field happens to be populated (the exact proxy that broke for REST-07 gap (b))."
    - "Single-computation count derivation: getScheduleDetail calls buildRestWaiverDisclosure exactly once, stores it in a local, and derives both the list field and the two counts from that one instance -- never a second walk, mirroring buildConstraintViolations' own provenance-threading discipline."

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/dto/ScheduleDetailResponse.java
    - src/main/java/com/wfm/service/ScheduleService.java
    - src/main/java/com/wfm/service/ScheduleOutputService.java
    - frontend/src/api/client.ts
    - frontend/src/pages/ScheduleResults.tsx
    - src/test/java/com/wfm/service/RestWaiverDisclosureTest.java

key-decisions:
  - "Re-pointed the frontend's configured-or-not gate at schedule.minimumRestMinutes itself rather than adding only the two counts to the detail response (planner decision P-01) -- the UI-SPEC's literal wording for the gate, restored rather than left as a proxy."
  - "WR-01 folded into this plan's Task 2 rather than deferred: plan 22-12 widens the set of Schedule instances reaching buildRestWaiverDisclosure to DB-fetched schedules on paths that never run loadSnapshotData's guard, so the code review's 'not reachable at HEAD' judgement was about to stop being load-bearing."
  - "The existing scheduleService(...) test helper's bare ScheduleOutputService mock is now stubbed to return a non-null empty RestWaiverDisclosure by default, since the real implementation never returns null and the new count derivation reads its list sizes -- documented under Deviations."

requirements-completed: [REST-07]

coverage:
  - id: D1
    description: "Opening an ALREADY-ACCEPTED schedule's detail response directly (not via the RUNNING-poll path) returns a non-null minimumRestMinutes and non-null appliedRestWaiverCount/unusedRestWaiverCount whenever that schedule's snapshotted minimum rest is non-null"
    requirement: "REST-07"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestWaiverDisclosureTest.java#acceptedSchedule_reopenedFromHistory_detailResponseCarriesBothCountsAndTheConfiguredSignal"
        status: pass
    human_judgment: false
  - id: D2
    description: "A schedule whose snapshotted minimumRestMinutes is null returns all three new fields as null on the detail response -- never zero"
    requirement: "REST-07"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestWaiverDisclosureTest.java#unconfiguredSchedule_detailResponse_allThreeRestFieldsNullNotZero"
        status: pass
    human_judgment: false
  - id: D3
    description: "RestWaiverDisclosureTest carries a declared-field-set assertion over ScheduleDetailResponse's actual fields that fails against HEAD before this plan and passes after it"
    requirement: "REST-07"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestWaiverDisclosureTest.java#detailResponse_declaresEveryRestWaiverCountFieldTheSummaryHas_plusTheSnapshottedRestSignal"
        status: pass
    human_judgment: false
  - id: D4
    description: "ScheduleOutputService.buildRestWaiverDisclosure resolves its anchor through the codebase's null-coalescing shape (WR-01 closed), with MidnightTimeArithmeticGuardTest green and no new allowlist entry"
    requirement: "REST-07"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java (full class, 12/12)"
        status: pass
      - kind: unit
        ref: "grep -c LocalTime.MIDNIGHT src/main/java/com/wfm/service/ScheduleOutputService.java == 1"
        status: pass
    human_judgment: false
  - id: D5
    description: "ScheduleResults.tsx's header badge and RestWaiversTab's configured gate both read the snapshotted minimumRestMinutes value, and both UI-SPEC empty-state copy strings and the tickSummary count merge are preserved"
    requirement: "REST-07"
    verification:
      - kind: other
        ref: "node -e region scans over frontend/src/pages/ScheduleResults.tsx (badge gate within 900 chars of badge copy, RestWaiversTab configured gate within 500 chars of function start) -- both found"
        status: pass
      - kind: other
        ref: "npm --prefix frontend exec -- tsc -b frontend --force"
        status: pass
    human_judgment: true
    rationale: "Visual rendering of the badge/tab (color, placement, narrow-viewport reflow) is not asserted by any automated check in this plan and was explicitly deferred -- human UAT must run only after 22-12 lands gap (a), per this plan's own <verification> note."

# Metrics
duration: 33min
completed: 2026-10-04
status: complete
---

# Phase 22 Plan 11: Rest Waiver Detail-Response Signal Summary

**`ScheduleDetailResponse` now carries its own snapshotted `minimumRestMinutes` plus both waiver counts, and the frontend's badge/tab gate on that field instead of the poll-merge-only counts — closing REST-07 gap (b) for every reopened ACCEPTED schedule outside the RUNNING-poll window.**

## Performance

- **Duration:** 33 min
- **Started:** 2026-10-04T13:20:00Z (approx.)
- **Completed:** 2026-10-04T13:53:00Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- `ScheduleDetailResponse` gains three additive, nullable fields — `minimumRestMinutes` (a mapped `@Column` read, populated by JPA on every path including the DB-fallback that never runs `loadSnapshotData`) plus `appliedRestWaiverCount`/`unusedRestWaiverCount` — mirroring `ScheduleSummary`'s existing null-is-unconfigured/zero-is-nothing-waived contract.
- `ScheduleService.buildDetailResponse` maps the snapshot; `getScheduleDetail` now calls `buildRestWaiverDisclosure` exactly once, stores it in a local, and derives both counts from that single instance's list sizes, gated on `schedule.getMinimumRestMinutes() != null` — the badge's numbers and the tab's row counts can never disagree.
- `frontend/src/api/client.ts`: `ScheduleDetail.minimumRestMinutes?: number`.
- `ScheduleResults.tsx`: the header badge and `RestWaiversTab`'s `configured` gate both re-pointed at `schedule.minimumRestMinutes != null` instead of the two counts' presence, so `loadDetail`'s `setSchedule(data)` full-state replace is harmless by construction. Both empty-state copy strings (`"Minimum rest is not configured for this desk."` / `"No rest waivers recorded for this schedule."`) and `tickSummary`'s count merge are unchanged, verbatim.
- `ScheduleOutputService.buildRestWaiverDisclosure` (WR-01, Task 2): the anchor binding now resolves through the codebase's established null-coalescing shape (`dayStart != null ? dayStart : LocalTime.MIDNIGHT`) instead of a raw `schedule.getDayStart()` read — closed because plan 22-12 is about to widen the set of `Schedule` instances reaching this method to DB-fetched schedules on paths that never run `loadSnapshotData`'s guard.
- `RestWaiverDisclosureTest`: three new tests (25 total, up from 22) — the RED-proof reflection test over `ScheduleDetailResponse`'s declared field set, the ACCEPTED/DB-path behavioural test reached entirely through `getScheduleDetail` with no `/summary` poll involved, and the null-minimum-rest test proving all three new fields stay null, not zero.

## Task Commits

Each task was committed atomically, following the plan's explicit RED-then-GREEN sequence for Task 1 (a `tracer`/`tdd="true"` task):

1. **Task 1 RED — declared-field-set proof** — `115b36a` (test)
2. **Task 1 GREEN — DTO fields, service wiring, frontend gates, behavioural tests** — `0fc28c6` (feat)
3. **Task 2 — WR-01 anchor fix** — `706bbfd` (fix)
4. **Follow-up — drop phase-number citations, Task 1's comments** — `b29204b` (refactor)
5. **Follow-up — drop phase-number citations, Task 2's comment** — `aee5a1f` (refactor)

**Plan metadata:** recorded below.

## RED Evidence (Task 1, before any production change)

Command: `./gradlew test --tests "com.wfm.service.RestWaiverDisclosureTest"`, with only the reflection test added and zero production changes.

```
RestWaiverDisclosureTest > detailResponse_declaresEveryRestWaiverCountFieldTheSummaryHas_plusTheSnapshottedRestSignal() FAILED
    java.lang.AssertionError at RestWaiverDisclosureTest.java:605

java.lang.AssertionError:
Expecting HashSet:
  ["breakDurationMinutes", "restWaiverDisclosure", "underallocationHardLimitPct",
   "preferenceReport", "schedulingMode", "periodEndDate", "violatedHardConstraints",
   "breakBlockedHours", "score", "createdAt", "dayStart", "feasibleAt", "deskName",
   "agentSchedule", "startTime", "feasible", "id", "breakMinShiftHours",
   "periodStartDate", "warnings", "errorMessage", "driftReport",
   "breakClusterThresholdPct", "version", "overallocationHardLimitPct",
   "constraintViolations", "breakStartAlignment", "defaultContractedHoursPerDay",
   "staffingSummary", "endTime", "incrementMinutes", "deskId", "status"]
to contain:
  ["appliedRestWaiverCount", "unusedRestWaiverCount"]
but could not find the following element(s):
  ["appliedRestWaiverCount", "unusedRestWaiverCount"]

23 tests completed, 1 failed
```

This is the exact assertion `22-VERIFICATION.md` named as missing — proof that `ScheduleDetailResponse`'s declared field set never carried the rest-waiver signal, which is how the gap survived 1437 passing backend tests and a clean `tsc` build. Green after Task 1's GREEN commit (25 tests completed, 0 failed).

## Files Created/Modified

- `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` — three new fields + accessors + javadoc
- `src/main/java/com/wfm/service/ScheduleService.java` — `buildDetailResponse`'s scalar mapping, `getScheduleDetail`'s single-computation count derivation
- `src/main/java/com/wfm/service/ScheduleOutputService.java` — `buildRestWaiverDisclosure`'s anchor binding (WR-01)
- `frontend/src/api/client.ts` — `ScheduleDetail.minimumRestMinutes?: number`
- `frontend/src/pages/ScheduleResults.tsx` — badge gate, `RestWaiversTab`'s `configured` gate, both stale comments rewritten
- `src/test/java/com/wfm/service/RestWaiverDisclosureTest.java` — three new tests, one new fixture helper, one existing helper's mock hardened

## Decisions Made

- Re-pointed the configured-or-not gate at the schedule's own snapshotted `minimumRestMinutes` rather than adding only the two counts to the detail response (planner decision P-01) — restores the UI-SPEC's literal wording for the gate rather than leaving a count-presence proxy in place.
- WR-01 folded into Task 2 (not deferred) because plan 22-12 is about to widen the caller set in a way that invalidates the code review's "not reachable at HEAD" judgement.
- `ScheduleSummary` deliberately does NOT gain a `minimumRestMinutes` component (planner decision P-02) — the only frontend consumer of the counts is `ScheduleResults.tsx`, which always loads the detail response before any poll begins, so the gate is always fed from the detail response alone.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `scheduleService(...)` test helper's bare `ScheduleOutputService` mock returned null, NPEing the new count derivation**
- **Found during:** Task 1 GREEN, first full run of `RestWaiverDisclosureTest`
- **Issue:** A Mockito mock's unstubbed `buildRestWaiverDisclosure(...)` call returns `null` (the method is not collection-returning, so Mockito's `ReturnsEmptyValues` default does not apply) — but the real `ScheduleOutputService` implementation never returns `null`, always a `RestWaiverDisclosure` instance. `getScheduleDetail`'s new count derivation reads `disclosure.applied().size()` whenever `schedule.getMinimumRestMinutes() != null`, so the two pre-existing `loadSnapshotData_*` tests that exercise this helper with a non-null minimum rest NPE'd.
- **Fix:** Stubbed the helper's internal mock (`when(mockedOutputService.buildRestWaiverDisclosure(any(), anyBoolean())).thenReturn(new RestWaiverDisclosure(List.of(), List.of()))`) so the fixture honours the same never-null invariant the production class upholds. The helper's own signature is unchanged — only its internal wiring, per the plan's explicit "do not widen either existing helper's signature" instruction.
- **Files modified:** `src/test/java/com/wfm/service/RestWaiverDisclosureTest.java`
- **Verification:** Both `loadSnapshotData_*` tests and the full `RestWaiverDisclosureTest` class (25/25) pass.
- **Committed in:** `0fc28c6` (Task 1 GREEN commit)

**2. [Rule 1 - Bug, self-correction] New comments cited phase/plan numbers against the plan's own explicit instruction**
- **Found during:** Review pass after Task 2's commit, before running the full suite
- **Issue:** Both tasks' `<action>` text explicitly states "Cite requirement IDs, never phase numbers (18-CONTEXT D-24)." The first drafts of several new comments (on `ScheduleDetailResponse`, `ScheduleService`, `client.ts`, `ScheduleResults.tsx`, and `ScheduleOutputService`) parenthesised `(plan 22-11, gap closure)` / `(plan 22-11 Task 2)` / `plan 22-12` alongside the required REST-07/D-nn citations.
- **Fix:** Rewrote each comment to lead with the REST-07 requirement ID and drop the phase/plan-number parentheticals, preserving every reason in plain prose.
- **Files modified:** `src/main/java/com/wfm/dto/ScheduleDetailResponse.java`, `src/main/java/com/wfm/service/ScheduleService.java`, `src/main/java/com/wfm/service/ScheduleOutputService.java`, `frontend/src/api/client.ts`, `frontend/src/pages/ScheduleResults.tsx`
- **Verification:** All plan `<verify>` grep/node checks re-run and still passing; `tsc -b frontend --force` clean.
- **Committed in:** `b29204b`, `aee5a1f` (follow-up commits)

---

**Total deviations:** 2 auto-fixed (1 Rule 3 — blocking test-fixture fix, 1 Rule 1 — self-corrected comment-citation compliance).
**Impact on plan:** No scope creep. Both fixes were required for the plan's own changes to compile/pass and to comply with the plan's own explicit citation instruction.

## Issues Encountered

- **Gradle test-results XML corruption from concurrent invocations (process error, not a code defect):** the first full-suite run (`./gradlew test`) failed with "Could not write XML test results" for ~79 unrelated test classes. Root cause: several filtered `--tests` invocations (run to verify this plan's targeted tests) executed concurrently against the same `build/test-results/test` output directory while the full suite was still running in the background on a separate Gradle daemon — exactly the project's own documented hazard ("a filtered `--tests` run deletes every other class's JUnit XML"), compounded here by true process concurrency. No test assertion failures were reported — only XML-write errors. Resolved by `./gradlew --stop` followed by a single, uninterrupted `./gradlew test` run with no concurrent Gradle invocations: **BUILD SUCCESSFUL in 11m 38s, 211 result files, 1440 tests, 0 failures, 0 errors** (1437 baseline + 3 new from this plan).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- **WR-01 is CLOSED** by Task 2 — `buildRestWaiverDisclosure`'s anchor binding now matches the codebase's null-coalescing convention, verified against `MidnightTimeArithmeticGuardTest` with no new allowlist entry needed.
- **WR-02 and IN-01 remain OPEN** — both are carried by `22-12-PLAN.md`, not dropped. WR-02 (the duplicated summary-count computation at two construction sites) lives in the summary path this plan does not touch; IN-01 (`DeskManagement.tsx`'s `NaN`-to-`null` hazard) is an unrelated surface.
- **Gap (a) of `22-VERIFICATION.md`'s REST-07 failure is still OPEN after this plan.** This plan closed gap (b) only — the frontend detail-response contract gap. Gap (a) — `listSchedules`/`getScheduleSummary`'s DB-fallback path silently reporting a false `0 applied / 0 unused` for an ACCEPTED schedule's unhydrated transient waiver collections — is unchanged by this plan and is closed by `22-12-PLAN.md`.
- Human UAT on the badge/tab must wait until 22-12 lands gap (a), per this plan's own `<verification>` note — a pass today would exercise gap (a)'s still-broken list/summary path and risk "confirming" a false `0`/`0` as correct on that surface.
- No blockers for proceeding to 22-12.

## Self-Check: PASSED

- `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` — FOUND, contains `minimumRestMinutes`/`appliedRestWaiverCount`/`unusedRestWaiverCount`
- `src/main/java/com/wfm/service/ScheduleService.java` — FOUND, contains `setMinimumRestMinutes`
- `src/main/java/com/wfm/service/ScheduleOutputService.java` — FOUND, contains `LocalTime.MIDNIGHT`, zero `deskRepository`
- `frontend/src/api/client.ts` — FOUND, `minimumRestMinutes?: number` present (count 2 across the file)
- `frontend/src/pages/ScheduleResults.tsx` — FOUND, both empty-state copy strings intact, both gates re-pointed
- `src/test/java/com/wfm/service/RestWaiverDisclosureTest.java` — FOUND, 25 tests (22 + 3 new)
- Commits `115b36a`, `0fc28c6`, `706bbfd`, `b29204b`, `aee5a1f` — all FOUND in `git log --oneline --all`
- All plan-level `<verification>` commands re-run and passing:
  - `./gradlew --stop` then `./gradlew test --tests "com.wfm.service.RestWaiverDisclosureTest" --tests "com.wfm.service.MidnightTimeArithmeticGuardTest" --tests "com.wfm.service.ScheduleSummaryReadTest"` — green
  - `./gradlew test` (full suite, clean single run after resolving the concurrent-invocation XML issue) — **BUILD SUCCESSFUL, 211 result files, 1440 tests, 0 failures, 0 errors**
  - `npm --prefix frontend exec -- tsc -b frontend --force` — clean, exit 0
  - All task-level grep/node `<verify>` checks — passing (see task sections above)

---
*Phase: 22-minimum-rest*
*Completed: 2026-10-04*
