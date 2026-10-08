---
phase: 22-minimum-rest
plan: 12
subsystem: scheduling
tags: [rest-waiver, disclosure, dto, structural-guard, timefold, java, react]

# Dependency graph
requires:
  - phase: 22-minimum-rest
    provides: "22-11's ScheduleDetailResponse.minimumRestMinutes/appliedRestWaiverCount/unusedRestWaiverCount fields and the frontend badge/tab gate re-point (gap (b)); plan 22-06's per-business-date finders (AgentShiftAssignmentRepository.findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate, AgentAssignmentRepository.findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndBusinessDate), which this plan's hydration helper reuses rather than re-deriving"
provides:
  - "ScheduleService.listSchedules and getScheduleSummary now report a DB-fetched schedule's TRUE applied/unused rest-waiver counts, never a false 0/0 computed over empty @Transient collections -- closing gap (a) of 22-VERIFICATION.md's REST-07 failure"
  - "ScheduleService.toSummary(Schedule) -- the single public ScheduleSummary construction entry point, with ScheduleSummaryConstructionSiteGuardTest pinning it structurally (WR-02)"
  - "AgentRestWaiverRepository.findWithAgentByTenantIdAndDeskIdAndDateBetween -- the one relations-fetching waiver finder every production path now loads through"
  - "DeskManagement.tsx hoursStringToMinutes refuses a non-finite parse instead of silently clearing a configured minimum rest (IN-01)"
affects: []

# Actuals (#2632)
actuals:
  tokens: 16297
  tasks: 3
  commits: 4
  plan_head_before: bd1b7108927cb5b71c059a541e0fb2bce5772914
  plan_head_after: 1214718031e3b8fe41b9a6feb3e8d8a6a16f6a21

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Structural construction-site guard over a parity test (D-08 applied a second time): ScheduleSummaryConstructionSiteGuardTest asserts set equality between every src/main/java class containing 'new ScheduleSummary(' and a one-entry registry, modelled byte-for-byte on RestWaiverPredicateGuardTest's comment-stripping scan technique."
    - "Three independently-asserted cost gates for an on-read hydration that must never become an N+1: gate 1 (snapshotted value non-null) before any query, gate 2 (one batched desk-wide query per page) before any per-row query, gate 3 (empty-in-period-waiver-set returns immediately) before any span query. Each gate has its own never()/times(1) test rather than a claim in a comment."
    - "A frontend parse guard returns a THIRD sentinel (undefined) rather than reusing null for the error case, because null already carries a legitimate distinct meaning (deliberately cleared) on this exact field -- collapsing the two would make a bug and a deliberate action indistinguishable on the wire."

key-files:
  created:
    - src/test/java/com/wfm/service/ScheduleSummaryConstructionSiteGuardTest.java
    - src/test/resources/schedule-summary-construction-site.md
  modified:
    - src/main/java/com/wfm/controller/ScheduleController.java
    - src/main/java/com/wfm/service/ScheduleService.java
    - src/main/java/com/wfm/repository/AgentRestWaiverRepository.java
    - src/test/java/com/wfm/service/RestWaiverDisclosureTest.java
    - src/test/java/com/wfm/service/ScheduleSummaryReadTest.java
    - frontend/src/pages/DeskManagement.tsx

key-decisions:
  - "Hydrate on read, never persist the counts at accept time (P-02, locked by the plan before execution) -- persisting would introduce a NEW silent disagreement (a frozen count above a detail response that keeps re-deriving live) worse than the one being fixed, and the rejected route is rated one-way (a Flyway migration against dev, the live system) versus the chosen route's reversible (no column, no migration)."
  - "WR-02's fix is collapse-plus-structural-guard, not a wider parity test: ScheduleController.toSummary deleted outright, ScheduleService.toSummary(Schedule) is the sole public entry point, and ScheduleSummaryConstructionSiteGuardTest -- not a second fixture someone has to remember to write -- is what now prevents a second construction site from reappearing."
  - "hydrateRestWaiverInputsFromDb deliberately does NOT reuse loadSnapshotData: that method's seven loads include a whole-schedule assignment read measured at six figures of rows on a large SLOT desk (AgentAssignmentRepository's own javadoc), and calling it per schedule in listSchedules would be exactly the N+1 the verification warned against. The new helper loads only the {D, D-1} business dates each in-period waiver actually references, through plan 22-06's existing per-business-date finders."
  - "IN-01's non-finite parse returns undefined, never null -- null already means 'operator deliberately cleared the field' on this exact wire contract (PUT .../minimum-rest), so collapsing a parse failure into that same value would make a bug indistinguishable from a deliberate action. handleUpdate checks for undefined BEFORE the change-comparison, since NaN's self-inequality would otherwise make the clearing PUT fire with certainty rather than by chance."

requirements-completed: [REST-07]

coverage:
  - id: D1
    description: "listSchedules and getScheduleSummary report a DB-fetched ACCEPTED schedule's true applied/unused rest-waiver counts (never 0/0 for a schedule with real waivers), under three independently-gated cost bounds: zero extra queries for an unconfigured desk, exactly one waiver query per page, zero span queries for a schedule with no in-period waiver"
    requirement: "REST-07"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestWaiverDisclosureTest.java#acceptedSchedule_dbFallbackSummary_reportsTheTrueCountsNotZero"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestWaiverDisclosureTest.java#acceptedSchedule_noWaiversInPeriod_issuesNoSpanQueryAndReportsZero"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestWaiverDisclosureTest.java#nullMinimumRest_dbFallbackSummary_issuesNoWaiverQueryAtAll"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestWaiverDisclosureTest.java#listSchedules_manyAcceptedSchedules_issuesExactlyOneWaiverQuery"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/RestWaiverDisclosureTest.java#listSchedules_doesNotMutateTheInMemorySchedule"
        status: pass
    human_judgment: false
  - id: D2
    description: "Exactly one expression in src/main/java constructs a ScheduleSummary, pinned by a registry-backed structural guard whose own RED direction was observed and quoted, rather than by a parity test alone"
    requirement: "REST-07"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleSummaryConstructionSiteGuardTest.java (full class, 5/5)"
        status: pass
      - kind: other
        ref: "grep -rl 'new ScheduleSummary(' src/main/java/ | wc -l == 1"
        status: pass
    human_judgment: false
  - id: D3
    description: "A non-numeric minimum-rest keystroke in the desk table never serialises as a cleared value -- hoursStringToMinutes refuses a non-finite parse, handleUpdate skips the PUT, and the existing inline amber validation text (never a new message) surfaces the problem"
    requirement: "REST-07"
    verification:
      - kind: other
        ref: "npm --prefix frontend exec -- tsc -b frontend --force"
        status: pass
      - kind: unit
        ref: "grep -c 'Number.isFinite' frontend/src/pages/DeskManagement.tsx == 1"
        status: pass
    human_judgment: true
    rationale: "No frontend test runner exists in this project (per ScheduleSummaryReadTest's own javadoc precedent and this plan's verification note) -- correctness rests on the compiler plus the source-level grep assertions above. Visual confirmation that the amber text actually renders beneath the input for a non-numeric keystroke is deferred to the phase's accumulated human-UAT pass, same as 22-04/22-09/22-10/22-11's own deferred visual checks."

# Metrics
duration: 44min
completed: 2026-10-04
status: complete
---

# Phase 22 Plan 12: DB-Fallback Waiver Counts, Construction-Site Guard & Frontend Parse Refusal Summary

**`ScheduleService.listSchedules`/`getScheduleSummary` now hydrate a minimal, three-cost-gated subset of a DB-fetched schedule's waiver inputs instead of reporting a false `0`/`0`, backed by one relations-fetching finder; a registry-backed structural guard now pins `ScheduleSummary` to its single construction site; and a non-finite `DeskManagement.tsx` minimum-rest keystroke can no longer silently clear a configured value.**

## Performance

- **Duration:** 44 min
- **Started:** 2026-10-04T13:56:00Z (approx. — immediately following 22-11's completion)
- **Completed:** 2026-10-04T14:40:15Z
- **Tasks:** 3
- **Files modified:** 8 (2 created, 6 modified)

## Accomplishments

- **WR-02 closed structurally.** `ScheduleController.toSummary` deleted outright; `startSolve`/`stopSolve`/`acceptSchedule` now all call `ScheduleService.toSummary(Schedule)` — the single public entry point, which resolves the desk name itself and delegates to the pre-existing private two-argument form (so `listSchedules` still resolves its desk name once per page, not once per row). `ScheduleController`'s constructor narrows from 6 parameters to 4 (`DeskRepository` and `ScheduleOutputService` removed, along with their now-unused imports). A new `ScheduleSummaryConstructionSiteGuardTest` + `schedule-summary-construction-site.md` registry — modelled on `RestWaiverPredicateGuardTest`/`MidnightTimeArithmeticGuardTest` — asserts set equality between every `src/main/java` class containing `new ScheduleSummary(` and the registry's one-entry allowlist. The guard's RED direction was demonstrated live (not assumed): a second construction site was temporarily added to `ScheduleExportService`, the guard failed naming that exact class, and the edit was reverted (`git diff` confirmed clean) before committing.
- **Gap (a) of REST-07 closed.** New private `ScheduleService.hydrateRestWaiverInputsFromDb` hydrates only the minimal waiver/pre-horizon inputs `buildRestWaiverDisclosure` needs for a DB-fetched schedule, without ever calling `loadSnapshotData` (whose seven loads include a whole-schedule assignment read measured at six figures of rows on a large SLOT desk). Three independently-asserted cost gates: zero waiver queries when a schedule's snapshotted `minimumRestMinutes` is null; exactly one desk-wide waiver query for a whole `listSchedules` page, never one per row; zero span queries when a schedule's period holds no in-period waiver. `AgentRestWaiverRepository` gained `findWithAgentByTenantIdAndDeskIdAndDateBetween` (a `JOIN FETCH agent` sibling of the existing finder — correctness, not performance, since `spring.jpa.open-in-view` is `false`), and `loadSnapshotData` is re-pointed at it too, so the disclosure's waiver input is loaded exactly one way on every path.
- **IN-01 closed.** `DeskManagement.tsx`'s `hoursStringToMinutes` widened to `number | null | undefined`: the empty-string-means-unset path is unchanged (still `null`), but a non-finite parse (a non-numeric string, or an overflow like `"1e500"` → `Infinity`) now returns `undefined` instead of `NaN` — which previously serialised through `JSON.stringify` as `null`, silently clearing a configured minimum rest. `handleUpdate` checks for `undefined` *before* the change comparison (ordered deliberately: `NaN` is unequal to everything including itself, so the comparison would otherwise fire the clearing PUT with certainty). The existing inline amber validation text now derives from `hoursStringToMinutes` itself rather than a second, independent `Number(...)` computation, so a non-finite parse surfaces through the same pre-existing `#92400e` text — no new client message, no new toast variant.
- All five new `RestWaiverDisclosureTest` methods plus all five new `ScheduleSummaryConstructionSiteGuardTest` methods pass; the full backend suite is green as its own clean invocation (`BUILD SUCCESSFUL in 11m 20s`, 212 result files, 1450 tests, 0 failures/errors — up from the 211/1440 baseline recorded at the end of plan 22-11); `npm --prefix frontend exec -- tsc -b frontend --force` exits 0.

## Task Commits

Each task was committed atomically, with Task 2 (a `tdd="true"` task) following the plan's explicit RED-then-GREEN sequence:

1. **Task 1: WR-02 — one `ScheduleSummary` construction site, structurally guarded** — `b345f1b` (refactor)
2. **Task 2 RED — the DB-fallback summary's false 0/0, proven failing** — `4ff9021` (test)
3. **Task 2 GREEN — `hydrateRestWaiverInputsFromDb` and the three cost gates** — `0b4770a` (feat)
4. **Task 3: IN-01 — a non-finite minimum-rest parse refused, never silently cleared** — `1214718` (fix)

**Plan metadata:** recorded below.

## RED Evidence

### Task 1 — `ScheduleSummaryConstructionSiteGuardTest`'s own failing direction, demonstrated

A second construction expression was temporarily added to `ScheduleExportService` (reverted immediately after observing the failure; `git diff` confirmed clean before Task 1's commit):

```
java.lang.AssertionError: [Classes constructing ScheduleSummary in src/main/java must equal the
registry's allowlist exactly. Missing a row for ...: [com.wfm.service.ScheduleExportService].]
Expecting actual:
  ["com.wfm.service.ScheduleService", "com.wfm.service.ScheduleExportService"]
to contain exactly in any order:
  ["com.wfm.service.ScheduleService"]
but the following elements were unexpected:
  ["com.wfm.service.ScheduleExportService"]
```

### Task 2 — `acceptedSchedule_dbFallbackSummary_reportsTheTrueCountsNotZero`, before any production change

Command: `./gradlew test --tests "com.wfm.service.RestWaiverDisclosureTest.acceptedSchedule_dbFallbackSummary_reportsTheTrueCountsNotZero"`, run against the tree as it stood after plan 22-11, with only this test (plus the repository interface declaration it needs to compile) added:

```
org.opentest4j.AssertionFailedError:
expected: 1
 but was: 0
```

This is the exact false zero `22-VERIFICATION.md` records for the DB-fallback summary path. Green after Task 2's GREEN commit (`RestWaiverDisclosureTest`: 30/30, up from 25/25 at the end of plan 22-11).

## Files Created/Modified

- `src/main/java/com/wfm/controller/ScheduleController.java` — `toSummary` deleted; constructor narrowed 6→4 params; three endpoints delegate to `scheduleService.toSummary(...)`
- `src/main/java/com/wfm/service/ScheduleService.java` — new public `toSummary(Schedule)`; new private `hydrateRestWaiverInputsFromDb`; `loadSnapshotData` re-pointed at the new finder; `getScheduleSummary`/`listSchedules` restructured for provenance-gated hydration
- `src/main/java/com/wfm/repository/AgentRestWaiverRepository.java` — new `findWithAgentByTenantIdAndDeskIdAndDateBetween` (`JOIN FETCH agent`)
- `src/test/java/com/wfm/service/ScheduleSummaryConstructionSiteGuardTest.java` — new, 5 tests
- `src/test/resources/schedule-summary-construction-site.md` — new registry
- `src/test/java/com/wfm/service/RestWaiverDisclosureTest.java` — 5 new behavioural tests, 2 new fixture helpers, 3 existing tests re-pointed/renamed
- `src/test/java/com/wfm/service/ScheduleSummaryReadTest.java` — one test updated to the 4-arg `ScheduleController` constructor, renamed
- `frontend/src/pages/DeskManagement.tsx` — `hoursStringToMinutes` widened; `handleUpdate`'s guard reordered; inline validation reused

## Decisions Made

- Hydrate on read, never persist the counts at accept time (P-02) — the rejected persist route is rated `one-way` (a Flyway migration against `dev`, the live system with real tenant data); the chosen route is `reversible` (no column, no migration). No `checkpoint:decision` was needed because the chosen route is not the one-way one.
- WR-02 fixed by collapse-plus-structural-guard (D-08 applied a second time in this phase), not by widening the existing parity test.
- `hydrateRestWaiverInputsFromDb` deliberately does not reuse `loadSnapshotData` — calling it per row in `listSchedules` would be exactly the N+1 the verification warned against.
- IN-01's non-finite parse returns `undefined`, never `null` — collapsing the two would make a parse bug indistinguishable from an operator's deliberate "clear this field" action on the same wire contract.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. The only process note: the first full `./gradlew test` run (and the final confirmation run) each took ~11.5 minutes; `./gradlew --stop` was run before each to avoid the stale-daemon doubling hazard this project has previously hit, and every filtered `--tests` invocation was run to completion before starting the next, so no test-results XML corruption occurred.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- **WR-01, WR-02 and IN-01 are now all CLOSED** — WR-01 by `22-11-PLAN.md` Task 2, WR-02 and IN-01 by this plan. All three code-review findings from `22-REVIEW-DISPOSITION.md` are accounted for.
- **Both independent defects behind `22-VERIFICATION.md`'s REST-07 failure are now closed** — gap (b) (the frontend detail-response contract) by 22-11, gap (a) (the DB-fallback summary's false `0`/`0`) by this plan. A re-run of `/gsd-verify-work` has something new to adjudicate.
- **The 20 human-UAT steps carried in `22-04-SUMMARY.md`/`22-09-SUMMARY.md`/`22-10-SUMMARY.md` remain outstanding, and are now worth running** — a pass before this plan landed would have exercised the still-broken fallback path and risked recording a false "Minimum rest is not configured for this desk." state as correct.
- This is the last of 12 plans in Phase 22 (10 original + 2 gap-closure). No blockers for proceeding to phase-level re-verification.

## Self-Check: PASSED

- All 8 created/modified files confirmed present on disk (see file list above).
- Commits `b345f1b`, `4ff9021`, `0b4770a`, `1214718` — all FOUND in `git log --oneline`.
- `grep -rl 'new ScheduleSummary(' src/main/java/ | wc -l` → `1` (`ScheduleService.java` only).
- `grep -v '^\s*\*' src/main/java/com/wfm/controller/ScheduleController.java | grep -v '^\s*//' | grep -c 'scheduleService.toSummary'` → `3`.
- `grep -c 'com.wfm.service.ScheduleService' src/test/resources/schedule-summary-construction-site.md` → `2`.
- `grep -v '^\s*\*' src/main/java/com/wfm/repository/AgentRestWaiverRepository.java | grep -v '^\s*//' | grep -c 'JOIN FETCH'` → `1`.
- `grep -v '^\s*\*' src/main/java/com/wfm/service/ScheduleService.java | grep -v '^\s*//' | grep -c 'hydrateRestWaiverInputsFromDb'` → `3`.
- `grep -c 'Number.isFinite' frontend/src/pages/DeskManagement.tsx` → `1`.
- `npm --prefix frontend exec -- tsc -b frontend --force` → exit `0`.
- All plan-level `<verification>` commands re-run and passing:
  - `./gradlew --stop` then `./gradlew compileJava compileTestJava` — green
  - `./gradlew test --tests "com.wfm.service.RestWaiverDisclosureTest" --tests "com.wfm.service.ScheduleSummaryReadTest" --tests "com.wfm.service.ScheduleSummaryConstructionSiteGuardTest" --tests "com.wfm.service.RestWaiverPredicateGuardTest"` — green (30/30, 8/8, 5/5, 8/8)
  - `./gradlew test` (full suite, clean invocation after the targeted runs) — **BUILD SUCCESSFUL in 11m 20s, 212 result files, 1450 tests, 0 failures, 0 errors**
  - `npm --prefix frontend exec -- tsc -b frontend --force` — clean, exit 0

---
*Phase: 22-minimum-rest*
*Completed: 2026-10-04*
