---
status: complete
phase: 17-consistency-constraint-drift-reporting
source: 17-01-SUMMARY.md, 17-02-SUMMARY.md, 17-03-SUMMARY.md, 17-04-SUMMARY.md, 17-05-SUMMARY.md
started: 2026-09-18T12:16:28Z
updated: 2026-09-18T19:40:10Z
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: Kill any running service. Start the app from scratch against a database that has not yet applied V48/V49. Flyway applies both migrations cleanly, the backend boots with no checksum or validation error, and a schedule detail request returns live data.
result: pass
source: injected-smoke-test
evidence: "Local stack stood up (wfm role/DB created on local Postgres 18.4). Booted from an EMPTY database: Flyway applied 48 migrations to v49, V48 and V49 both success=t, zero ERROR/Exception lines, actuator/health 200, GET /api/v1/desks 200."
note: Injected because this phase added src/main/resources/db/migration/V48__*.sql and V49__*.sql (db/migration path match).

### 2. Solve Never Writes Usual Shifts
expected: Running a solve on a shift-mode desk completes normally and performs zero writes to agent_usual_shift — an agent's stored usual shift is byte-identical before and after the solve. SolverService.resolveUsualShiftTargets reads the repository but never mutates it.
result: pass
evidence: "Live solve on a 12-agent SHIFT desk with 50 stored usual-shift rows. md5 digest of agent_usual_shift identical before (3cfcced654fcdb03ed80f08363974c15) and after the solve; row count 50 unchanged."
coverage_id: 17-01/D8
requirement: CONS-01

### 3. Benchmark Write-Up Is Honest
expected: 17-BENCHMARK.md transcribes the A/B results verbatim (median plus full min/max spread), names the construction-heuristic plateau plainly as a null result rather than dressing it as a win, and redoes V38's per-agent sizing arithmetic for D-02's per-agent-day model.
result: pass
coverage_id: 17-04/D2
requirement: CONS-02

### 4. Checkpoint Decision Recorded and Honoured
expected: The 17-04 checkpoint decision is recorded and honoured — the "proposed" pair (consistency 2 / preferred-start 1) was selected, and its rationale (D-08 minimality, incumbent-value safety, documented worst-case residual risk) is carried into both 17-BENCHMARK.md and the V49 migration header.
result: pass
coverage_id: 17-04/D3
requirement: CONS-02

### 5. Drift Report Tab
expected: On a shift-scheduled desk the Drift Report tab shows the per-agent-date table, a summary bar whose four counts track the current date filter, the D-11 live-recomputation note, and headers byte-identical to the Excel export sheet. A Drifted row draws the eye at a glance — red/bold (#dc2626, weight 600) against Honoured green (#16a34a) and No-usual-shift muted gray (#d1d5db). On a slot-scheduled desk it shows a single not-applicable message instead of an empty table.
result: issue
reported: "Automated verification: tab renders correctly. One finding remains — the 'No usual shift' status colour #d1d5db on white measures 1.47:1 contrast, failing WCAG AA for both normal (4.5:1) and large (3:1) text. The second finding (export HTTP 500 blocking header-parity verification) is now FIXED and verified — see gap G-17-9."
severity: minor
evidence: "Export header parity CONFIRMED after the G-17-9 fix: the Drift Report sheet's headers resolve to [Agent, Date, Status, Usual Start, Actual Start, Delta (min)] — byte-identical to the on-screen tab. On a SLOT-solved desk holding 50 usual-shift rows the exported Drift Report sheet contains the header row and ZERO data rows, with no Most-Subscribed/popularity content anywhere in the workbook — CR-01 and IN-02 both confirmed end to end for the first time. Also verified working: per-agent-date table; summary bar tracks the date filter (60/17/33/10 unfiltered -> 12/2/8/2 filtered to 2026-09-21); D-11 live-recomputation note present; Drifted rgb(220,38,38) weight 600, Honoured rgb(22,163,74), No usual shift rgb(209,213,219) — all exactly per 17-UI-SPEC; SLOT desk shows the single not-applicable message with zero tables."
coverage_id: 17-05/D2
requirement: DRFT-01

### 6. Most-Subscribed Usual Shifts Section
expected: Beneath the main table, a popularity section ranks shift templates by distinct-agent count, with its own empty message and subtext disclosing it reads stored usual shifts rather than this solve's results. On a desk with a large shift library the ranked list stays readable without a scroll or density problem, and a long operator-authored template name wraps rather than clips.
result: pass
evidence: "Most-Subscribed Usual Shifts renders beneath the main table with the disclosure subtext ('Reads each agent's current usual shift, not this solve's results'). Counts correct against seeded data: Early Shift 8, Late Shift 2. Long-template-name wrapping and large-library density were NOT exercised — the seeded library has 2 short names."
coverage_id: 17-05/D3
requirement: DRFT-04

### 7. Constraint Weights Rows
expected: The Constraint Weights page shows three new rows (Usual Shift Consistency, Usual Shift Consistency Tolerance, Preferred Start (Shift Mode)) consecutively, immediately before Minimum Staffing, followed by the D-10 precedence note. The tolerance-band row (Minutes badge, single input, colSpan=2) reads as a clearly different kind of field from the Hard/Soft score rows — not as a rendering bug.
result: pass
evidence: "Constraint Weights page: the three new rows render consecutively (Usual Shift Consistency, Usual Shift Consistency Tolerance, Preferred Start (Shift Mode)), followed by the D-10 precedence note as a full-width colspan=5 row, then Minimum Staffing. Tolerance row carries a Minutes badge and a single colSpan=2 input, visibly distinct from the Hard/Soft score rows. WR-01's isScore guard dropped no rows (27 rows rendered, no console error)."
coverage_id: 17-05/D4
requirement: CONS-02

### 8. XCUT-04 Benchmark Sign-Off
expected: A decision on whether XCUT-04's success criterion ("a seeded A/B benchmark confirms the consistency constraint's weight doesn't regress coverage/break quality before a default ships") is satisfied. The A/B measured no difference at any weight — every arm converged identically, so the pass rule was met trivially by identity, not by evidence. The shipped 2/1 default rests instead on sizing arithmetic plus explain(), with a disclosed worst-case 12% overshoot of the 1000-soft minStaffingWeight ceiling, accepted at the 17-04 checkpoint. Either accept the substitute as satisfying the criterion, or track it as an open item pending real drift telemetry.
result: pass
decision: >
  Accepted: the arithmetic-plus-explain() substitute satisfies XCUT-04, with the
  construction-heuristic plateau and the documented worst-case 12% overshoot of
  minStaffingWeight's 1000-soft ceiling carried as disclosed residual risk. The shipped
  17-BENCHMARK.md itself proposes the Phase 17 drift report as the retroactive source of
  the real drift telemetry this substitute stands in for.
source: 17-VERIFICATION.md human_verification
requirement: XCUT-04

## Summary

total: 8
passed: 7
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- gap_id: G-17-5
  truth: "Drift Report status column reads as clearly distinct at a glance"
  status: resolved
  reason: "Automated contrast check: 'No usual shift' renders #d1d5db on #ffffff = 1.47:1, failing WCAG AA normal (4.5:1) and large (3:1). The status text is effectively illegible."
  severity: minor
  test: 5
  artifacts:
    - path: "frontend/src/pages/ScheduleResults.tsx"
      issue: "No-usual-shift status colour #d1d5db has 1.47:1 contrast on white"
  missing: []
  resolved_by: "37309f4 (no-usual-shift 1.47 -> 4.83:1), 7af2ab5 (Honoured 3.30 -> 5.02:1), 8782d9c (app-wide green sweep)"
  resolved_at: 2026-09-18
  resolution: >
    Measuring the fix surfaced a second failure the original finding missed: Honoured
    (#16a34a) was 3.30:1, also under AA. Both fixed and verified in the browser against a
    live shift-mode schedule — Drifted 4.83:1, Honoured 5.02:1, No usual shift 4.83:1.
    The same green was a shared token failing identically at 13 other call sites, swept
    app-wide in 8782d9c.

- gap_id: G-17-9
  truth: "Excel export of a schedule succeeds and its Drift Report sheet headers match the on-screen tab"
  status: resolved
  reason: "GET /api/v1/desks/{deskId}/schedules/{id}/export returns HTTP 500 for every multi-day schedule. NullPointerException in ScheduleExportService.writeStaffingSummary:86 — e.date().toString() on the GRAND TOTAL entry, which ScheduleOutputService:132 constructs with a null date whenever allDates.size() > 1. PRE-EXISTING, not a Phase 17 regression: line 86 dates from Phase 5 (3ebf89f, 2026-03-03) and Phase 17's only change to this file since the review baseline is the IN-02 comment (102e28f). Consequence for Phase 17: the export half of DRFT-01/DRFT-04 — and CR-01/IN-02's whole concern about what the export leaks on a SLOT desk — cannot be observed end to end, because the export throws before reaching the Drift Report sheet."
  severity: blocker
  test: 5
  artifacts:
    - path: "src/main/java/com/wfm/service/ScheduleExportService.java"
      issue: "writeStaffingSummary:86 dereferences e.date() unguarded"
    - path: "src/main/java/com/wfm/service/ScheduleOutputService.java"
      issue: "Line 132 adds a GRAND TOTAL StaffingSummaryEntry with a null date when allDates.size() > 1"
  missing: []
  resolved_by: "fix(17): guard null GRAND TOTAL date in staffing summary export"
  resolved_at: 2026-09-18
  resolution: >
    Fixed and verified live. writeStaffingSummary now writes a blank Date cell when
    e.date() is null (matching how the on-screen Staffing Summary renders that row).
    Two regression tests added to ScheduleExportServiceTest, both confirmed to FAIL with the
    guard reverted (NPE) and pass with it restored: one asserts the blank GRAND TOTAL date
    while the per-day TOTAL row keeps its real date, the other asserts a later sheet (Drift
    Report) is still written — the NPE previously aborted the whole workbook.
    Live proof: the same export that returned HTTP 500 now returns 200 / 20,958 bytes with
    all five sheets present.

## Notes

- Coverage classification (#1602): 33 deliverables across the 5 SUMMARYs, 27 auto-passed by
  passing tests and not presented here; 6 required human judgment and became tests 2-7.
- Commit-claim reconciliation (#3968): all 5 SUMMARYs are pre-#3968 legacy (no `commits:` /
  `plan_head_before:` frontmatter) — reported as a WARNING with measured git state, not a
  mismatch. 17-05 records `plan_head_before: 33eb0412`.
- Automated UI verification: initially unavailable (nothing running, local Postgres had no
  `wfm` role), which is why tests 2 and 5-7 were first recorded as blocked. A local stack was
  then stood up — `wfm` role/DB created, backend booted from an EMPTY database (Flyway applied
  48 migrations to v49 cleanly, which is test 1 for real), frontend on :3000, and a desk seeded
  via the API with 12 agents, 2 shift templates, 50 usual-shift rows and a real solve. All four
  blocked tests were then executed against the running app. Final: 3 UI checkpoints verified via
  Playwright (computed styles and contrast measured, not eyeballed), 0 left blocked.
- 17-VERIFICATION.md was stale at the start of this session (committed 0a8608e, 18:08:59,
  predating the CR-01 fix at ea9b6ac, 18:15:07). Re-run at b659491: status moved
  `gaps_found` -> `human_needed`, `gaps: []`, criterion 4 now VERIFIED, and the re-verification
  independently judged the shipped "never invoked" regression test at least equivalent to the
  originally-requested stored-rows test.
- Defects found and fixed during this UAT: G-17-9 (export HTTP 500 on every multi-day schedule,
  pre-existing since Phase 5) and G-17-5 (drift status contrast, which on measurement turned out
  to be two failures, not one). Both verified fixed against the running app.
