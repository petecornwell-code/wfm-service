---
phase: 22-minimum-rest
verified: 2026-10-04T15:57:58Z
status: passed
score: 5/5 roadmap success criteria verified
behavior_unverified: 0
overrides_applied: 0
covered_files: [".planning/phases/22-minimum-rest/22-01-PLAN.md",".planning/phases/22-minimum-rest/22-01-SUMMARY.md",".planning/phases/22-minimum-rest/22-02-PLAN.md",".planning/phases/22-minimum-rest/22-02-SUMMARY.md",".planning/phases/22-minimum-rest/22-03-PLAN.md",".planning/phases/22-minimum-rest/22-03-SUMMARY.md",".planning/phases/22-minimum-rest/22-04-PLAN.md",".planning/phases/22-minimum-rest/22-04-SUMMARY.md",".planning/phases/22-minimum-rest/22-05-PLAN.md",".planning/phases/22-minimum-rest/22-05-SUMMARY.md",".planning/phases/22-minimum-rest/22-06-PLAN.md",".planning/phases/22-minimum-rest/22-06-SUMMARY.md",".planning/phases/22-minimum-rest/22-07-PLAN.md",".planning/phases/22-minimum-rest/22-07-SUMMARY.md",".planning/phases/22-minimum-rest/22-08-PLAN.md",".planning/phases/22-minimum-rest/22-08-SUMMARY.md",".planning/phases/22-minimum-rest/22-09-PLAN.md",".planning/phases/22-minimum-rest/22-09-SUMMARY.md",".planning/phases/22-minimum-rest/22-10-PLAN.md",".planning/phases/22-minimum-rest/22-10-SUMMARY.md",".planning/phases/22-minimum-rest/22-11-PLAN.md",".planning/phases/22-minimum-rest/22-11-SUMMARY.md",".planning/phases/22-minimum-rest/22-12-PLAN.md",".planning/phases/22-minimum-rest/22-12-SUMMARY.md",".planning/phases/22-minimum-rest/22-REVIEW-DISPOSITION.md",".planning/phases/22-minimum-rest/22-REVIEW.md","frontend/src/api/client.ts","frontend/src/pages/AgentExceptions.tsx","frontend/src/pages/DeskManagement.tsx","frontend/src/pages/ScheduleResults.tsx","src/main/java/com/wfm/controller/DeskAgentController.java","src/main/java/com/wfm/controller/DeskController.java","src/main/java/com/wfm/controller/ScheduleController.java","src/main/java/com/wfm/dto/DeskResponse.java","src/main/java/com/wfm/dto/MinimumRestRequest.java","src/main/java/com/wfm/dto/RestWaiverResponse.java","src/main/java/com/wfm/dto/ScheduleDetailResponse.java","src/main/java/com/wfm/dto/ScheduleSummary.java","src/main/java/com/wfm/model/AgentRestWaiver.java","src/main/java/com/wfm/model/ConstraintWeights.java","src/main/java/com/wfm/model/Desk.java","src/main/java/com/wfm/model/RestSpan.java","src/main/java/com/wfm/model/RestWaiverLookup.java","src/main/java/com/wfm/model/Schedule.java","src/main/java/com/wfm/model/ScheduleConfig.java","src/main/java/com/wfm/repository/AgentAssignmentRepository.java","src/main/java/com/wfm/repository/AgentRestWaiverRepository.java","src/main/java/com/wfm/repository/AgentShiftAssignmentRepository.java","src/main/java/com/wfm/service/DeskService.java","src/main/java/com/wfm/service/RestPredecessorService.java","src/main/java/com/wfm/service/RestWaiverService.java","src/main/java/com/wfm/service/ScheduleOutputService.java","src/main/java/com/wfm/service/ScheduleService.java","src/main/java/com/wfm/service/SolverService.java","src/main/java/com/wfm/solver/ScheduleConstraintProvider.java","src/main/resources/db/migration/V55__add_minimum_rest.sql","src/test/java/com/wfm/repository/RestWaiverFetchingFinderGuardTest.java","src/test/java/com/wfm/service/DeskServiceMinimumRestTest.java","src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java","src/test/java/com/wfm/service/RestPredecessorServiceTest.java","src/test/java/com/wfm/service/RestWaiverDisclosureTest.java","src/test/java/com/wfm/service/RestWaiverPredicateGuardTest.java","src/test/java/com/wfm/service/RestWaiverServiceTest.java","src/test/java/com/wfm/service/ScheduleSummaryConstructionSiteGuardTest.java","src/test/java/com/wfm/service/ScheduleSummaryReadTest.java","src/test/java/com/wfm/solver/ConstraintMatchCountNonVacuityTest.java","src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java","src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java","src/test/java/com/wfm/solver/RestHorizonEdgeTest.java","src/test/java/com/wfm/solver/ScheduleConstraintClassification.java","src/test/resources/rest-waiver-predicate-guard.md","src/test/resources/schedule-summary-construction-site.md"]
covered_digest: "v2:sha256:8efbdec5d8807e138b16d39efd9f2a629cebc180c7ef1ae168a00540ed940c07"
re_verification:
  previous_status: gaps_found
  previous_score: "4/5 roadmap success criteria verified (1 failed: REST-07)"
  gaps_closed:
    - "Gap (a): ScheduleService.listSchedules and getScheduleSummary reported a false 0 applied / 0 unused for a DB-fetched ACCEPTED schedule, because buildRestWaiverDisclosure walked empty @Transient collections that neither path ever hydrated. Closed by plan 22-12: a new relations-fetching finder (AgentRestWaiverRepository.findWithAgentByTenantIdAndDeskIdAndDateBetween) plus a new private ScheduleService.hydrateRestWaiverInputsFromDb, gated by three independently-tested cost gates (zero queries when minimumRestMinutes is null, exactly one batched waiver query per listSchedules page, zero span queries when no in-period waiver exists), and never calling the expensive loadSnapshotData."
    - "Gap (b): ScheduleDetailResponse carried no minimumRestMinutes/appliedRestWaiverCount/unusedRestWaiverCount fields, so the frontend header badge and Rest Waivers tab fell back to hidden/not-configured for every finished solve and every reopened ACCEPTED schedule outside the narrow RUNNING-poll window. Closed by plan 22-11: ScheduleDetailResponse gained all three fields (minimumRestMinutes as a mapped-column read, populated by JPA on every path including the DB fallback); ScheduleResults.tsx's badge gate and RestWaiversTab's configured gate were both re-pointed from the two-count presence check onto schedule.minimumRestMinutes != null, which loadDetail's full-state setSchedule(data) replace can no longer silently wipe."
  gaps_remaining: []
  regressions: []
advisory:
  - finding: "WR-03 — ScheduleService.hydrateRestWaiverInputsFromDb's pre-horizon branch (needsPreHorizon, calling RestPredecessorService.resolvePriorSpans) is untested through the two new call sites it was built for (listSchedules / getScheduleSummary); every new gap (a) test uses a D2-dated waiver, never a D1 (period-start-date) one. Confirmed open by direct inspection of RestWaiverDisclosureTest.java and the production branch at ScheduleService.java (~lines 673-682). The branch itself is byte-for-byte parallel to the already-tested loadSnapshotData call site (same six arguments, same method), so this is a coverage gap on a new combination of already-correct code, not a known defect."
    category: other
    reason: "Recorded open in 22-REVIEW-DISPOSITION.md (disposition: open, severity: warning); not required by either gap-closure plan's own must_haves, and the orchestrator's verified_state flags it as open rather than closed."
    evidence_status: "none provided beyond direct code inspection confirming the production logic mirrors an already-tested call site"
  - finding: "WR-04 — the IN-01 fix (DeskManagement.tsx hoursStringToMinutes/handleUpdate non-finite-parse refusal) has zero automated regression coverage; this repository has no frontend test runner at all (no *.test.ts*/*.spec.ts* files, no test script in package.json)."
    category: other
    reason: "Recorded open in 22-REVIEW-DISPOSITION.md (disposition: open, severity: warning). Correctness rests on tsc plus the grep-level source assertions in 22-12-SUMMARY.md's D3 coverage entry, which is explicitly marked human_judgment: true."
    evidence_status: "none provided; standing technical debt, out of scope for a frontend-test-runner-less repo per the review's own disposition"
  - finding: "IN-02 — the reused inline-validation message under the Min Rest input always reads \"Minimum rest must be less than 24 hours.\", including when hoursStringToMinutes returns undefined for a non-numeric keystroke (e.g. \"abc\"), which is a different problem than an out-of-range number. handleUpdate correctly refuses to save in this case; only the message text is imprecise."
    category: other
    reason: "Recorded open in 22-REVIEW-DISPOSITION.md (disposition: open, severity: info). Cosmetic — the save-refusal behavior itself is correct and covered by the grep assertions; only the wording is inaccurate."
    evidence_status: "none provided; cosmetic, deferred per the review's own suggested fix"
human_verification:
  - test: "DeskManagement.tsx Min Rest column (22-04's 7-step human-check, deferred to end-of-phase per workflow.human_verify_mode): open the desk config table and confirm the Min Rest (hrs) column sits between Day Start and Actions with the em-dash for an unconfigured desk; edit a desk to 10.5, save, reload twice and confirm it persists; clear to empty and confirm it reads the em-dash (not 0); set to 0 and confirm it reads 0 (not the em-dash); type 25 and confirm the amber (not red) out-of-range message appears without disabling Save; edit only the Name and confirm Min Rest is untouched; narrow the browser until the table scrolls and confirm no clipped content."
    expected: "All seven sub-checks pass as described; the null/zero distinction in particular must survive a real round trip through the PUT endpoint and a page reload, not just the in-memory state."
    why_human: "Visual rendering (column position, em-dash vs 0, amber vs red color), a live round trip against a running app and database, and narrow-viewport reflow are explicitly outside what grep/tsc can confirm; screenshots do not settle reliably on this app per this project's own documented hazard."
  - test: "AgentExceptions.tsx Rest Waivers section (22-09's 9-step human-check, deferred to end-of-phase): open one agent's exceptions page, confirm the Rest Waivers card layout and empty-state copy; add a waiver and confirm immediate (no Save-step) persistence across a reload; double-click Add rapidly and confirm exactly one row with the button disabled between clicks; submit a blank reason and confirm the server's own error surfaces with no row created; delete a row and confirm no confirmation dialog, a success toast, and the row gone after reload; add a waiver on a day-off date and confirm acceptance and consistent dimming; add a very-long-reason waiver and confirm it wraps rather than truncates; narrow the viewport and confirm the form wraps without overflow."
    expected: "All nine sub-checks pass as described, including the immediate-persistence (no explicit Save) interaction model and the double-click idempotency guard."
    why_human: "Live add/delete round trip against a running app and database, toast behavior, click-debounce timing, and visual wrap/overflow behavior are outside static analysis; this project's screenshot tool does not settle reliably here."
  - test: "ScheduleResults.tsx header badge and Rest Waivers tab (22-10's 11-step human-check, deferred to end-of-phase, and the specific surface both gap-closure plans targeted): solve a fixture desk with configured minimum rest and one deliberately waived short-rested pair; confirm the header badge renders last in the header row in the correct muted treatment and updates on the fast 2-second tick while RUNNING; click it and confirm the Rest Waivers tab activates, positioned between Constraint Violations and PTO; confirm Applied (green) sits above Unused (grey) with gaps as one-decimal-hour figures; confirm an unused waiver on a day-off date renders correctly; open a schedule on a desk with NO configured rest and confirm the badge is absent and the tab shows the never-configured (not nothing-recorded) copy; trigger a pre-solve rest refusal and confirm the full untruncated string renders in the existing red block; narrow the viewport and confirm reflow rather than clipping."
    expected: "All eleven sub-checks pass as described. This is the first human-eyes confirmation that the two now-closed defects (the false DB-fallback 0/0, and the detail-response field gap) actually produce a correct badge/tab for a REOPENED ACCEPTED schedule, not only for a schedule caught mid-poll — the exact distinction the two gap-closure plans were written to fix."
    why_human: "Visual color/placement checks, the live 2-second poll tick, click-to-switch-tab interaction, and viewport reflow are outside static analysis; running a real solve against a fixture desk is required to produce an actual waived pair to inspect. A pass taken before 22-11/22-12 landed would have exercised the broken fallback path and risked confirming a false negative as correct — this is the first point at which running it is meaningful."
---

# Phase 22: Minimum Rest Verification Report

**Phase Goal:** An operator can require a minimum gap between an agent's consecutive shifts,
enforced as a hard constraint the solver cannot silently violate, with a pre-solve refusal for the
structurally unavoidable cases and a per-agent, per-date waiver for the genuinely exceptional ones.

**Verified:** 2026-10-04T15:57:58Z
**Status:** human_needed
**Re-verification:** Yes — after gap closure (plans 22-11 and 22-12, closing both halves of the
prior `gaps_found` verdict's REST-07 failure)

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | An operator can set a minimum rest period per desk via the desk config UI, and a desk that sets none solves exactly as today (REST-01, REST-04) | ✓ VERIFIED | Unchanged since the prior pass; re-confirmed by direct re-read of `DeskManagement.tsx`, `DeskService.setMinimumRest`, and `ScheduleConstraintProvider`'s NULL-gated constraints. No regression. |
| 2 | The solver treats insufficient rest as a hard violation, measured between actual end/start instants, with tested behaviour at both horizon edges (REST-02, REST-05) | ✓ VERIFIED | Unchanged since the prior pass. `RestHorizonEdgeTest` (part of the 213-file, 1454-test green suite) still covers both edges. No regression. |
| 3 | A rest violation that is structurally unavoidable is refused before the solve runs, naming the agent and both shifts, by a mechanism separate from the in-solve constraint (REST-03) | ✓ VERIFIED | Unchanged since the prior pass. `SolverService.requireRestFeasibility` and `RestFeasibilityRefusalTest` re-confirmed present and passing. No regression. |
| 4 | An operator can waive minimum rest for one agent/date with a reason through the existing per-agent exception mechanism; the solver treats a waived pair as legal, and a waived occurrence does not trigger the pre-solve refusal (REST-06) | ✓ VERIFIED | Unchanged since the prior pass. `RestWaiverLookup`/`RestWaiverPredicateGuardTest` re-confirmed (8/8 passing per `TEST-com.wfm.service.RestWaiverPredicateGuardTest.xml`). No regression — and the CR-01 fix (below) strengthens this path further. |
| 5 | A waived rest violation is visible in the solved schedule's output, so a waiver cannot silently hide a roster problem (REST-07) | ✓ VERIFIED | **Both previously-confirmed defects are now closed.** See "REST-07 Gap Closure" below for the full evidence trail. |

**Score:** 5/5 truths verified (0 present, behavior-unverified)

### REST-07 Gap Closure — Direct Code Verification

This is a re-verification. The prior pass's `gaps_found` verdict rested on two independently
confirmed defects, both read directly from source again in this pass (not re-trusted from
SUMMARY.md claims):

**Gap (a) — DB-fallback false `0`/`0` — CLOSED.**
Direct read of `src/main/java/com/wfm/service/ScheduleService.java`:
- `getScheduleSummary` (line ~819): when the resolved schedule came from the DB (`fromDb`) and
  carries a non-null `getMinimumRestMinutes()`, it now calls the new
  `AgentRestWaiverRepository.findWithAgentByTenantIdAndDeskIdAndDateBetween` finder and passes the
  result into the new private `hydrateRestWaiverInputsFromDb` before building the summary — cost
  gate 1 (line ~841: `if (fromDb && schedule.getMinimumRestMinutes() != null)`).
- `listSchedules` (line ~80): narrows the DB-sourced schedules to those with a non-null snapshotted
  minimum rest, issues exactly ONE desk-wide waiver query spanning the narrowed set's combined
  period, and hydrates each entry from that one shared result — cost gate 2 (lines ~111-131). The
  in-memory (RUNNING) schedule is excluded from hydration by identity, matching the "never mutate
  the object `InMemoryScheduleStore` hands back by reference" invariant.
- `hydrateRestWaiverInputsFromDb` (private helper, line ~628): filters the shared candidate list to
  the schedule's own period; if empty, returns immediately with no span query (cost gate 3, line
  ~636); otherwise loads only the `{D, D-1}` business dates each in-period waiver actually
  references, through the pre-existing per-business-date finders — never `loadSnapshotData`'s
  seven-load, whole-schedule-assignment path.
- Behavioral proof, not just presence: `RestWaiverDisclosureTest.acceptedSchedule_dbFallbackSummary_reportsTheTrueCountsNotZero`
  is a direct inverse of the recorded defect — a short-rested waived agent and an adequately-rested
  waived agent, asserting `appliedRestWaiverCount == 1` and `unusedRestWaiverCount == 1` on a
  DB-resolved schedule. The plan's own RED evidence (quoted in `22-12-SUMMARY.md`) shows this exact
  test failing with `expected: 1 but was: 0` against the pre-fix tree — the literal defect
  `22-VERIFICATION.md`'s prior pass recorded. `listSchedules_manyAcceptedSchedules_issuesExactlyOneWaiverQuery`,
  `acceptedSchedule_noWaiversInPeriod_issuesNoSpanQueryAndReportsZero`,
  `nullMinimumRest_dbFallbackSummary_issuesNoWaiverQueryAtAll`, and
  `listSchedules_doesNotMutateTheInMemorySchedule` independently assert each of the three cost gates
  and the no-mutation invariant with `verify(..., never())`/`verify(..., times(1))` — not comments.
- All five tests confirmed passing in `build/test-results/test/TEST-com.wfm.service.RestWaiverDisclosureTest.xml`
  (30/30 tests, 0 failures, 0 errors — up from 25 at the end of plan 22-11, up from 22 before this
  gap closure began).

**Gap (b) — detail-response field gap — CLOSED.**
Direct read of `src/main/java/com/wfm/dto/ScheduleDetailResponse.java`: declares
`minimumRestMinutes`, `appliedRestWaiverCount` and `unusedRestWaiverCount` (all boxed `Integer`,
lines 35, 71-72) with matching getters/setters (lines 328-329, 374-377).
Direct read of `ScheduleService.buildDetailResponse` (line ~767): `r.setMinimumRestMinutes(s.getMinimumRestMinutes())`
is a mapped-`@Column` read, populated by JPA on every path including the DB fallback that never
runs `loadSnapshotData`. `getScheduleDetail` (line ~199) computes `buildRestWaiverDisclosure` exactly
once into a local, sets it on the response, and — gated on `schedule.getMinimumRestMinutes() != null`
— derives both counts from that single disclosure's list sizes, never a second walk.
Direct read of `frontend/src/pages/ScheduleResults.tsx`: the header badge's gate (line 258) and
`RestWaiversTab`'s `configured` gate (line 1448) both now read `schedule.minimumRestMinutes != null`
— the schedule's own snapshotted value carried directly on the detail response — rather than the two
counts' presence, which only ever landed on `schedule` state during the 2-second `/summary` poll.
Both UI-SPEC empty-state copy strings (`"Minimum rest is not configured for this desk."` /
`"No rest waivers recorded for this schedule."`) are preserved verbatim (lines 1450, 1457).
Behavioral proof: `detailResponse_declaresEveryRestWaiverCountFieldTheSummaryHas_plusTheSnapshottedRestSignal`
is a reflection-based RED-then-GREEN test whose quoted RED output (in `22-11-SUMMARY.md`) names the
three absent fields against HEAD before the fix. `acceptedSchedule_reopenedFromHistory_detailResponseCarriesBothCountsAndTheConfiguredSignal`
reaches `getScheduleDetail` through the DB-resolve branch (no `/summary` poll involved at all) and
asserts both counts equal the disclosure's own list sizes. `unconfiguredSchedule_detailResponse_allThreeRestFieldsNullNotZero`
confirms the REST-04 display-extension invariant: null, never a derived zero, for an unconfigured
desk.

**Regression check — CR-01 (critical finding from the incremental code review, already fixed per
the dispatch brief's verified_state) re-confirmed by direct read.** `SolverService.java` line 239
now calls `findWithAgentByTenantIdAndDeskIdAndDateBetween` (the same relations-fetching finder gap
(a) introduced), not the non-fetching `findByTenantIdAndDeskIdAndDateBetween`. A repo-wide grep
confirms the non-fetching finder has **zero remaining production callers** — its only appearances
in `src/main/java` are its own interface declaration and javadoc — matching
`RestWaiverFetchingFinderGuardTest`'s structural claim (confirmed passing, 4/4, in
`build/test-results/test/TEST-com.wfm.repository.RestWaiverFetchingFinderGuardTest.xml`). All three
production paths that read waivers for the disclosure (`listSchedules`, `getScheduleSummary`,
`loadSnapshotData` via `getScheduleDetail`, and `SolverService`'s live problem-fact load) now load
through the exact same fetching finder.

**WR-02 (structural guard for `ScheduleSummary` construction) — re-confirmed.**
`grep -rl 'new ScheduleSummary(' src/main/java/` returns exactly one file
(`ScheduleService.java`, line 901). `ScheduleController.toSummary` is deleted; its three
summary-returning endpoints (`startSolve`, `stopSolve`, `acceptSchedule`) all call
`scheduleService.toSummary(schedule)` (confirmed by direct read of `ScheduleController.java`, which
now has exactly 4 constructor parameters, not 6). `ScheduleSummaryConstructionSiteGuardTest`
(5/5 passing) asserts set equality against `schedule-summary-construction-site.md`'s one-entry
allowlist, and the plan's quoted RED evidence shows a temporarily-added second construction site in
`ScheduleExportService` was actually caught by the guard before being reverted — the guard's own
failing direction was observed, not assumed.

**IN-01 (non-finite minimum-rest parse) — re-confirmed.** `frontend/src/pages/DeskManagement.tsx`'s
`hoursStringToMinutes` now returns `number | null | undefined`; a non-finite parse returns
`undefined` (via `Number.isFinite`, confirmed present at line 30), never `NaN` serialising to
`null`. `handleUpdate` checks for `undefined` before the change-comparison (confirmed at lines
138-140), so the clearing PUT cannot fire on a garbage keystroke.

### Open Findings Carried Forward (not blocking REST-07)

Three findings from the incremental code review of plans 22-11/22-12 remain `open` per
`22-REVIEW-DISPOSITION.md` and are recorded in this report's `advisory:` frontmatter rather than as
gaps, because none of them is required by either gap-closure plan's own `must_haves` and none
represents a failure of an observable truth: **WR-03** (a test-coverage gap on an already-correct,
byte-for-byte-parallel code branch — the pre-horizon lookback through the two new DB-fallback call
sites has no `D1`-dated-waiver test, though the identical call shape is tested on the pre-existing
`loadSnapshotData` path), **WR-04** (the IN-01 fix has no automated regression test, because this
repository has no frontend test runner at all — standing, disclosed technical debt), and **IN-02**
(a cosmetic validation-message mismatch for a non-numeric keystroke; the underlying save-refusal
behavior is correct).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` | Disclosure + count + snapshotted-rest fields | ✓ VERIFIED | `minimumRestMinutes`, `appliedRestWaiverCount`, `unusedRestWaiverCount` all present with accessors (gap (b) closed) |
| `src/main/java/com/wfm/service/ScheduleService.java` | Truthful DB-fallback counts, single `toSummary` entry point, hydration helper | ✓ VERIFIED | `getScheduleSummary`/`listSchedules` hydrate via `hydrateRestWaiverInputsFromDb` under three cost gates; `toSummary(Schedule)` is the sole public entry point (gap (a) and WR-02 closed) |
| `src/main/java/com/wfm/repository/AgentRestWaiverRepository.java` | Relations-fetching waiver finder | ✓ VERIFIED | `findWithAgentByTenantIdAndDeskIdAndDateBetween` present, `JOIN FETCH`es `agent`, scoped by `tenantId` and `deskId` |
| `src/main/java/com/wfm/controller/ScheduleController.java` | Single delegation point, no duplicate construction | ✓ VERIFIED | `toSummary` deleted; 4-arg constructor; all 3 summary endpoints delegate to `scheduleService.toSummary` |
| `src/test/java/com/wfm/service/ScheduleSummaryConstructionSiteGuardTest.java` | WR-02 structural guard | ✓ VERIFIED | 5/5 passing; RED direction demonstrated live per the plan's own commit evidence |
| `src/test/resources/schedule-summary-construction-site.md` | Guard registry | ✓ VERIFIED | One-entry allowlist (`com.wfm.service.ScheduleService`), parsed by the guard test |
| `frontend/src/pages/ScheduleResults.tsx` | Header badge + Rest Waivers tab, correctly gated | ✓ VERIFIED | Badge and tab's `configured` gate both re-pointed at `schedule.minimumRestMinutes != null`; both UI-SPEC empty-state strings preserved verbatim |
| `frontend/src/pages/DeskManagement.tsx` | Min Rest column, non-finite-parse refusal | ✓ VERIFIED | `Number.isFinite` guard present; `handleUpdate` checks `undefined` before the change comparison (IN-01 closed) |
| `frontend/src/api/client.ts` | `ScheduleDetail.minimumRestMinutes` | ✓ VERIFIED | Present at line 563, with explanatory comment |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `Schedule.minimumRestMinutes` (mapped `@Column`) | `ScheduleDetailResponse.minimumRestMinutes` | `buildDetailResponse` | ✓ WIRED | Populated by JPA on every path, including DB fallback |
| `ScheduleDetailResponse`/`ScheduleResults.tsx` badge+tab gate | `schedule.minimumRestMinutes` | Re-pointed gate expression | ✓ WIRED | `loadDetail`'s full-state replace can no longer wipe the configured-or-not signal |
| `AgentRestWaiverRepository.findWithAgentByTenantIdAndDeskIdAndDateBetween` | `Schedule.agentRestWaivers` | `hydrateRestWaiverInputsFromDb` | ✓ WIRED | Confirmed at `listSchedules` and `getScheduleSummary` call sites |
| `ScheduleController.startSolve`/`stopSolve`/`acceptSchedule` | `ScheduleService.toSummary(Schedule)` | Direct delegation | ✓ WIRED | Confirmed — single construction site, no duplicate |
| `SolverService`'s live problem-fact load | `AgentRestWaiverRepository.findWithAgentByTenantIdAndDeskIdAndDateBetween` | CR-01 fix | ✓ WIRED | Confirmed; non-fetching finder has zero remaining production callers |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| `RestWaiverDisclosureTest` full class passes, including all 8 new gap-closure tests | JUnit XML aggregate read (not a fresh filtered run, per this project's own XML-corruption hazard) | `tests="30" skipped="0" failures="0" errors="0"` | ✓ PASS |
| `ScheduleSummaryConstructionSiteGuardTest` passes, including its own observed-RED liveness test | JUnit XML aggregate read | `tests="5" skipped="0" failures="0" errors="0"` | ✓ PASS |
| `RestWaiverFetchingFinderGuardTest` (CR-01's structural guard) passes | JUnit XML aggregate read | `tests="4" skipped="0" failures="0" errors="0"` | ✓ PASS |
| `RestWaiverPredicateGuardTest` (D-08, unaffected by this closure) still passes | JUnit XML aggregate read | `tests="8" skipped="0" failures="0" errors="0"` | ✓ PASS |
| `ScheduleSummaryReadTest` (updated for the 4-arg controller constructor) still passes | JUnit XML aggregate read | `tests="8" skipped="0" failures="0" errors="0"` | ✓ PASS |
| Full backend suite, no regression | JUnit XML aggregate read across all result files | `213 files, tests="1454" failures="0" errors="0" skipped="4"` | ✓ PASS |
| No surviving production caller of the non-fetching waiver finder | `grep -rn "findByTenantIdAndDeskIdAndDateBetween\b" src/main/java/` (excluding other entities' same-named methods) | Only the interface's own declaration and javadoc | ✓ PASS |
| Exactly one `ScheduleSummary` construction site | `grep -rl 'new ScheduleSummary(' src/main/java/ \| wc -l` | `1` | ✓ PASS |
| No debt markers in any phase-touched file | `grep -nE "TBD\|FIXME\|XXX\|TODO\|HACK\|PLACEHOLDER"` across all 73 `covered_files` entries with source extensions | No matches | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|---|---|---|---|---|
| REST-01 | 22-01, 22-04 | Operator can set minimum rest per desk | ✓ SATISFIED | Unchanged from prior pass; re-confirmed |
| REST-02 | 22-01, 22-02, 22-05 | Solver treats insufficient rest as hard violation, unless waived | ✓ SATISFIED | Unchanged from prior pass; re-confirmed |
| REST-03 | 22-07 | Pre-solve refusal, mechanism separate from in-solve constraint | ✓ SATISFIED | Unchanged from prior pass; re-confirmed |
| REST-04 | 22-01, 22-02, 22-04 | Desk with no minimum rest solves exactly as before | ✓ SATISFIED | Unchanged from prior pass; re-confirmed, and the display-extension invariant (`unconfiguredSchedule_detailResponse_allThreeRestFieldsNullNotZero`) carried it to the new detail-response fields too |
| REST-05 | 22-06 | Defined, tested behaviour at both horizon edges | ✓ SATISFIED | Unchanged from prior pass; re-confirmed |
| REST-06 | 22-03, 22-05, 22-09 | Operator can waive; solver treats waived pair as legal | ✓ SATISFIED | Unchanged from prior pass; re-confirmed, strengthened by the CR-01 fetching-finder fix on the live/poll path |
| REST-07 | 22-08, 22-10, 22-11, 22-12 | Waived rest violations visible in solved schedule's output | ✓ SATISFIED | **Both previously-confirmed defects closed** — see "REST-07 Gap Closure" above. `.planning/REQUIREMENTS.md`'s `[x]` / Complete marking for REST-07 is now earned by direct code and test evidence, not merely asserted. |

No orphaned requirements found — REQUIREMENTS.md's Phase 22 mapping (REST-01 through REST-07)
matches exactly the set declared across the 12 plans' `requirements:` frontmatter (10 original + 2
gap-closure).

### Anti-Patterns Found

No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers found in any of the 73 `covered_files`
entries with a source extension (`.java`, `.ts`, `.tsx`, `.sql`), including all files touched by
plans 22-11 and 22-12.

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| (none — the two 🛑 Blocker rows from the prior pass's report are both resolved; see "REST-07 Gap Closure" above) | | | | |
| `src/main/java/com/wfm/service/ScheduleService.java` (WR-03) | ~673-682 | `hydrateRestWaiverInputsFromDb`'s pre-horizon branch has no `D1`-waiver test through its two new call sites | ⚠️ Warning | Open per `22-REVIEW-DISPOSITION.md`; production logic verified correct by direct inspection (parallels the already-tested `loadSnapshotData` call), coverage gap only |
| `frontend/src/pages/DeskManagement.tsx` (WR-04) | 27-32, 138-144 | IN-01's fix has zero automated regression coverage | ⚠️ Warning | Open; no frontend test runner exists in this repository at all — standing, disclosed debt |
| `frontend/src/pages/DeskManagement.tsx` (IN-02) | 236-249 | Reused inline-validation message is inaccurate for a non-finite (non-numeric) parse | ℹ️ Info | Open; cosmetic — the save-refusal behavior itself is correct |

### Human Verification Required

Three items — all pre-existing, deferred end-of-phase UAT steps from plans 22-04, 22-09 and 22-10
(per `workflow.human_verify_mode=end-of-phase`), now meaningful for the first time because both
REST-07 defects are closed. A pass taken before 22-11/22-12 landed would have exercised the broken
DB-fallback/detail-response paths and risked confirming a false negative as correct; this is the
first point at which running them actually tests what they are meant to test. See frontmatter
`human_verification:` for the full test/expected/why_human detail on each of the three surfaces
(`DeskManagement.tsx`'s Min Rest column, `AgentExceptions.tsx`'s Rest Waivers section, and
`ScheduleResults.tsx`'s header badge + Rest Waivers tab).

### Gaps Summary

None remaining. Both defects behind the prior pass's `gaps_found` verdict on REST-07 — the
DB-fallback summary paths' false `0`/`0` (gap a) and the detail-response field gap that hid the
badge/tab for every finished or reopened schedule (gap b) — are closed, verified here by direct
reading of the production source (not SUMMARY.md claims alone) and by confirming the specific
behavioral tests that prove each fix pass in the actual JUnit output. The regression check on
CR-01 (the live/poll-path `LazyInitializationException` risk the incremental code review caught)
also holds: the non-fetching waiver finder has zero remaining production callers anywhere in
`src/main/java`.

Three findings (WR-03, WR-04, IN-02) remain open per the code review's own disposition and are
carried forward as advisories rather than gaps — none of them is a failure of an observable truth,
and WR-03 in particular is a coverage gap on code already verified correct by direct inspection.

The phase's accumulated 27-step human-check (7+9+11, across the three UI surfaces this phase
built) has not yet been run against a live app and database. It is the only thing standing between
this report and a clean `passed` verdict, and it is now worth running for the first time.

---

_Verified: 2026-10-04T15:57:58Z_
_Verifier: Claude (gsd-verifier)_
