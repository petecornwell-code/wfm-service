---
schema_version: 1
open_count: 14
waived_count: 0
fixed_count: 0
total_count: 14
last_updated: 2026-10-03T03:01:51.216Z
---

# Broken Windows Ledger

> Cross-phase defect register. With `workflow.windows_enforce` enabled, `/gsd-ship` blocks while `open_count > 0`.
> Waive with `gsd-tools windows waive <id> "<reason>"` (reason required).
> Mark fixed with `gsd-tools windows fixed <id>`.

| id | phase | kind | file | line | description | status | reason | recorded_at | resolved_at |
|----|-------|------|------|------|-------------|--------|--------|-------------|-------------|
| 1 | 13 | unrun-verify | frontend/src/pages/DeskAgents.tsx |  | Task 2 <human-check> visual walkthrough (chevron, MAND/PTO badges, explicit-zero vs not-set styling, no horizontal overflow) not run — no live DB/BambooHR-configured environment available in this executor session | open |  | 2026-08-22T00:21:30.559Z |  |
| 2 | 13 | unrun-verify | src/main/java/com/wfm/service/DeskAgentService.java |  | Plan-level manual walkthrough (roster Hours/Day column reflects an enriched upload's Mon-Sun values against a live desk) not run — no live DB/BambooHR-configured environment available in this executor session | open |  | 2026-08-22T00:21:34.090Z |  |
| 3 | 13 | deviation | frontend/src/pages/DeskAgents.tsx |  | Task 1 relocated the existing editHoursAgentId/editHours/startEditHours/saveHours triad into a new expanded-row <tr> scaffold one task earlier than the plan's literal wording (plan said the expanded row body is built in Task 2) — required because removing the triad's only usage site from the collapsed Hours/Day cell made all four symbols unused under noUnusedLocals:true, which would have failed Task 1's own required 'npm run build exits 0' acceptance criterion | open |  | 2026-08-22T00:21:39.273Z |  |
| 4 | 13 | unrun-verify | src/main/java/com/wfm/controller/DeskAgentController.java |  | PUT .../day-hours/{day} behavioral acceptance criteria (200 with fresh body on valid hours, 400 not 500 on out-of-range hours) verified only via compileJava + grep, not an actual HTTP/integration test — no controller test file exists for DeskAgentController in this codebase | open |  | 2026-08-22T00:37:58.146Z |  |
| 5 | 13 | unrun-verify | frontend/src/pages/DeskAgents.tsx |  | 13-04 Task 1 <human-check> per-cell combo walkthrough (typed number, PTO/MANDATORY pick, Not-set clear, out-of-range rejection, offline error revert, datalist overflow) not run — no live desk with an enriched upload available in this executor session | open |  | 2026-08-22T01:01:23.383Z |  |
| 6 | 13 | unrun-verify | frontend/src/pages/DeskAgents.tsx |  | 13-04 Task 2 <human-check> bulk 'Set all days to…' walkthrough (no-dialog on unlabelled agent, accurate label-count confirm dialog, decline/accept paths, single-line layout) not run — no live desk with an enriched upload available in this executor session | open |  | 2026-08-22T01:01:23.451Z |  |
| 7 | 13 | unrun-verify | src/main/java/com/wfm/controller/GlobalExceptionHandler.java |  | 13-06 Task 2 <human-check> HTTP-level walkthrough (malformed day-segment returns 400 not 500, valid/out-of-range day-hours paths unchanged, bulk contracted-hours 400 message, genuine 500 still generic) not run — no live backend available in this executor session; only the direct handler unit test (GlobalExceptionHandlerTest) proves the response the handler builds, not Spring's dispatch to it (P-17) | open |  | 2026-08-24T12:54:26.239Z |  |
| 8 | 15 | deviation | build.gradle |  | Rule 3 blocking fix: restored the wfm.benchmark system-property test-JVM passthrough (removed at commit 299c42c alongside the Phase 12 harness) — without it -Dwfm.benchmark=true never reaches the test JVM and ShiftModelBenchmarkTest silently never runs | open |  | 2026-08-27T05:52:41.259Z |  |
| 9 | 17 | stub | src/main/java/com/wfm/service/ScheduleOutputService.java |  | buildDriftReport's popularity field always returns List.of() -- DRFT-04's over-subscription ranking is plan 17-03's deliverable (D-13), explicitly deferred per the plan's own Task 1 action text | open |  | 2026-09-17T16:33:12.009Z |  |
| 10 | 20 | deviation | src/test/resources/bday-join-guard.md |  | BusinessDateJoinGuardTest cannot see ScheduleConstraintProvider's shared DATE lambda (lines 91-92) or the seven groupBy(AGENT_ID, DATE, ...) consumers it feeds -- symbolic-constant indirection breaks the guard's single-line verb+receiver co-occurrence scan. Covers ~half of SOLV-01's edit surface; plan 20-05's migration at these 8 lines must be verified by direct code review, not by this guard's green. | open |  | 2026-10-01T18:52:23.613Z |  |
| 11 | 21 | unrun-verify | frontend/src/pages/DeskManagement.tsx |  | 21-08 backstop geometry checks (cell wrapping, table-width overflow, Toast long-text at narrow viewport) not executed — no browser-automation tool available in this execution session; installing one (Playwright/Puppeteer) would violate the plan's own no-new-dependency threat mitigation | resolved |  | 2026-10-03T01:38:09.327Z |  |
| 12 | 21 | unrun-verify | frontend/src/pages/ScheduleResults.tsx |  | 21-10 Task 2 backstop geometry check not executed: whether 24+ anchored slot columns disturb the sticky Agent column's offset -- no browser-automation tool available in this execution session; functional claims (contiguous run, envelope containment, anchored ordering, business-date keying, midnight no-op) were instead proven by executing the real dayWindow.ts module against representative fixtures (12/12 checks passed, see plan SUMMARY) RESOLVED 2026-10-03: measured with browser automation (21-UAT.md item 2). Grid rendered 26 columns (24 slots) in ANCHORED order 21:00->20:00; the overnight run occupied header indices 3-9 as one unbroken run (each index = predecessor+1), and the sticky Agent column was undisturbed by the re-ordering (declaration intact, width stable 232px across scrollLeft 0/300/full). A separate PRE-EXISTING sticky-pinning defect found during the measurement is filed as #15, verified by git diff not to be this phase's doing. | resolved |  | 2026-10-03T02:29:42.969Z |  |
| 13 | 21 | unrun-verify | frontend/src/pages/ScheduleResults.tsx |  | 21-10 Task 3 backstop geometry checks not executed: whether the lengthened section heading forces horizontal page scroll at a 21:00-anchored desk, and whether its ~37-character parenthetical wraps rather than clips at a narrow viewport -- no browser-automation tool available in this execution session; the heading's exact text contract (locked copy, midnight suppression, absent-anchor degradation) was proven by executing sectionHeading() against representative fixtures RESOLVED 2026-10-03: measured with browser automation (21-UAT.md item 3). Heading rendered "2026-01-05 (business day: Mon 21:00-Tue 21:00)" with scrollW==clientW (801) and scrollH==clientH (24), whiteSpace normal, overflow visible -- not clipped. It does not wrap because it sits INSIDE the grid's own horizontal scroll container so its box tracks the 801px table width rather than the 375px viewport; the page-scroll the item guards against is therefore not attributable to it. Verifier independently judged this reasoning sound rather than a rationalised miss. | resolved |  | 2026-10-03T02:29:43.049Z |  |
| 14 | 21 | deviation | src/main/java/com/wfm/service/ScheduleOutputService.java |  | The 'Unassigned assignment' constraint's ConstraintMatch never indicts an individual AgentAssignment (groupBy/join/join/filter aggregate; justification is Timeslot+int+TimeslotDemandConfig+ScheduleConfig), so its 'No agent assigned for X at Y' description branch is pre-existing dead code in buildConstraintViolations' live path -- confirmed empirically in plan 21-11, not introduced by it, not fixed (out of scope: would require restructuring ScheduleConstraintProvider's stream shape). SIGNIFICANCE REVISED 2026-10-03 by the phase-21 UAT fourth pass: this is NOT merely a dead description string. ScheduleOutputService.java:683-709 populates ViolationDetail's businessDate/calendarDate/startTime/endTime ONLY under `instanceof AgentAssignment` (no Timeslot branch), so every Unassigned-assignment violation carries NULL attribution; ScheduleResults.tsx:391-402 keys its unfilledSlots map on businessDate|startTime, so the operator-facing UNFILLED-SEAT MARKER can never render in either branch of the allocation grid. Still not a phase-21 regression (the match shape predates it, and 21-03/21-10's structured fields populate correctly for every constraint that does indict an AgentAssignment) but a live operator-visible gap, not cosmetic. Cross-referenced from #17 | open |  | 2026-10-03T03:01:51.216Z |  |
| 15 | 21 | defect | frontend/src/pages/ScheduleResults.tsx |  | The allocation grid's Agent column declares position:sticky,left:0 but never pins: the table wrapper carries overflowX:auto while the DOCUMENT is what scrolls horizontally, so sticky resolves against a non-scrolling ancestor and is inert -- measured live, the column reaches left -366px/right -134px (fully off-screen) at full document scroll. NOT introduced by phase 21 (git diff of this file filtered for position:/sticky/overflowX over the phase range returns nothing); found while measuring UAT item 2. Phase 21's 24-column anchored layout increases exposure to it by making horizontal scroll more likely | open |  | 2026-10-03T15:10:00Z |  |
| 16 | 21 | waived | src/main/java/com/wfm/service/ScheduleExportService.java |  | Excel Roster-sheet legend-collision backstop (21-07 Task 1) was never ledgered by the plan; identified by the third verification run. 21-07-SUMMARY records only a POI-level programmatic proxy and states a human opening the file in Excel is the authoritative check. Structurally unavailable to an automated session -- no tool here renders an .xlsx as Excel does -- so it could only be inferred, which is the bar this phase's UAT refused for every other item. WAIVED by explicit operator decision 2026-10-03 rather than lowering that bar or leaving the phase indefinitely pending. Residual risk accepted: a collision would be cosmetic in the exported workbook, affects none of the seven OVNT truths, and cannot corrupt data | waived |  | 2026-10-03T15:32:00Z |  |
| 17 | 21 | unrun-verify | frontend/src/pages/ScheduleResults.tsx |  | 21-10 Task 2 requires its six sub-checks verified in BOTH the slot-mode and shift-mode render branches of AgentAllocationTab; every pass before the fourth exercised slot-mode only. Identified by the fourth verification run. RESOLVED-IN-PART 2026-10-03: five of six measured in the shift-mode branch against a seeded SHIFT-mode desk with a full-day wrapping window (24 columns regenerated, anchored column order, anchored shift-group ordering with Overnight before Daytime, break band at +180min rendering 'B' past midnight, and envelope-reached styling correctly including 00:00-05:00 inside a 22:00-06:00 envelope). The sixth, unfilled-seat-marker placement, is UNREACHABLE BY CONSTRUCTION pending #14 -- not unmeasured; see 21-UAT.md item 5 for the end-to-end trace | partial |  | 2026-10-03T15:46:00Z |  |

````json
[
  {
    "id": 1,
    "kind": "unrun-verify",
    "phase": "13",
    "file": "frontend/src/pages/DeskAgents.tsx",
    "line": null,
    "description": "Task 2 <human-check> visual walkthrough (chevron, MAND/PTO badges, explicit-zero vs not-set styling, no horizontal overflow) not run — no live DB/BambooHR-configured environment available in this executor session",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-22T00:21:30.559Z",
    "resolved_at": null
  },
  {
    "id": 2,
    "kind": "unrun-verify",
    "phase": "13",
    "file": "src/main/java/com/wfm/service/DeskAgentService.java",
    "line": null,
    "description": "Plan-level manual walkthrough (roster Hours/Day column reflects an enriched upload's Mon-Sun values against a live desk) not run — no live DB/BambooHR-configured environment available in this executor session",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-22T00:21:34.090Z",
    "resolved_at": null
  },
  {
    "id": 3,
    "kind": "deviation",
    "phase": "13",
    "file": "frontend/src/pages/DeskAgents.tsx",
    "line": null,
    "description": "Task 1 relocated the existing editHoursAgentId/editHours/startEditHours/saveHours triad into a new expanded-row <tr> scaffold one task earlier than the plan's literal wording (plan said the expanded row body is built in Task 2) — required because removing the triad's only usage site from the collapsed Hours/Day cell made all four symbols unused under noUnusedLocals:true, which would have failed Task 1's own required 'npm run build exits 0' acceptance criterion",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-22T00:21:39.273Z",
    "resolved_at": null
  },
  {
    "id": 4,
    "kind": "unrun-verify",
    "phase": "13",
    "file": "src/main/java/com/wfm/controller/DeskAgentController.java",
    "line": null,
    "description": "PUT .../day-hours/{day} behavioral acceptance criteria (200 with fresh body on valid hours, 400 not 500 on out-of-range hours) verified only via compileJava + grep, not an actual HTTP/integration test — no controller test file exists for DeskAgentController in this codebase",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-22T00:37:58.146Z",
    "resolved_at": null
  },
  {
    "id": 5,
    "kind": "unrun-verify",
    "phase": "13",
    "file": "frontend/src/pages/DeskAgents.tsx",
    "line": null,
    "description": "13-04 Task 1 <human-check> per-cell combo walkthrough (typed number, PTO/MANDATORY pick, Not-set clear, out-of-range rejection, offline error revert, datalist overflow) not run — no live desk with an enriched upload available in this executor session",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-22T01:01:23.383Z",
    "resolved_at": null
  },
  {
    "id": 6,
    "kind": "unrun-verify",
    "phase": "13",
    "file": "frontend/src/pages/DeskAgents.tsx",
    "line": null,
    "description": "13-04 Task 2 <human-check> bulk 'Set all days to…' walkthrough (no-dialog on unlabelled agent, accurate label-count confirm dialog, decline/accept paths, single-line layout) not run — no live desk with an enriched upload available in this executor session",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-22T01:01:23.451Z",
    "resolved_at": null
  },
  {
    "id": 7,
    "kind": "unrun-verify",
    "phase": "13",
    "file": "src/main/java/com/wfm/controller/GlobalExceptionHandler.java",
    "line": null,
    "description": "13-06 Task 2 <human-check> HTTP-level walkthrough (malformed day-segment returns 400 not 500, valid/out-of-range day-hours paths unchanged, bulk contracted-hours 400 message, genuine 500 still generic) not run — no live backend available in this executor session; only the direct handler unit test (GlobalExceptionHandlerTest) proves the response the handler builds, not Spring's dispatch to it (P-17)",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-24T12:54:26.239Z",
    "resolved_at": null
  },
  {
    "id": 8,
    "kind": "deviation",
    "phase": "15",
    "file": "build.gradle",
    "line": null,
    "description": "Rule 3 blocking fix: restored the wfm.benchmark system-property test-JVM passthrough (removed at commit 299c42c alongside the Phase 12 harness) — without it -Dwfm.benchmark=true never reaches the test JVM and ShiftModelBenchmarkTest silently never runs",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-27T05:52:41.259Z",
    "resolved_at": null
  },
  {
    "id": 9,
    "kind": "stub",
    "phase": "17",
    "file": "src/main/java/com/wfm/service/ScheduleOutputService.java",
    "line": null,
    "description": "buildDriftReport's popularity field always returns List.of() -- DRFT-04's over-subscription ranking is plan 17-03's deliverable (D-13), explicitly deferred per the plan's own Task 1 action text",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-09-17T16:33:12.009Z",
    "resolved_at": null,
    "milestone": "v1.3"
  },
  {
    "id": 10,
    "kind": "deviation",
    "phase": "20",
    "file": "src/test/resources/bday-join-guard.md",
    "line": null,
    "description": "BusinessDateJoinGuardTest cannot see ScheduleConstraintProvider's shared DATE lambda (lines 91-92) or the seven groupBy(AGENT_ID, DATE, ...) consumers it feeds -- symbolic-constant indirection breaks the guard's single-line verb+receiver co-occurrence scan. Covers ~half of SOLV-01's edit surface; plan 20-05's migration at these 8 lines must be verified by direct code review, not by this guard's green.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-10-01T18:52:23.613Z",
    "resolved_at": null,
    "milestone": "v1.5"
  },
  {
    "id": 11,
    "kind": "unrun-verify",
    "phase": "21",
    "file": "frontend/src/pages/DeskManagement.tsx",
    "line": null,
    "description": "21-08 backstop geometry checks (cell wrapping, table-width overflow, Toast long-text at narrow viewport) not executed — no browser-automation tool available in this execution session; installing one (Playwright/Puppeteer) would violate the plan's own no-new-dependency threat mitigation",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-10-03T01:38:09.327Z",
    "resolved_at": null,
    "milestone": "v1.5"
  },
  {
    "id": 12,
    "kind": "unrun-verify",
    "phase": "21",
    "file": "frontend/src/pages/ScheduleResults.tsx",
    "line": null,
    "description": "21-10 Task 2 backstop geometry check not executed: whether 24+ anchored slot columns disturb the sticky Agent column's offset -- no browser-automation tool available in this execution session; functional claims (contiguous run, envelope containment, anchored ordering, business-date keying, midnight no-op) were instead proven by executing the real dayWindow.ts module against representative fixtures (12/12 checks passed, see plan SUMMARY)",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-10-03T02:29:42.969Z",
    "resolved_at": null,
    "milestone": "v1.5"
  },
  {
    "id": 13,
    "kind": "unrun-verify",
    "phase": "21",
    "file": "frontend/src/pages/ScheduleResults.tsx",
    "line": null,
    "description": "21-10 Task 3 backstop geometry checks not executed: whether the lengthened section heading forces horizontal page scroll at a 21:00-anchored desk, and whether its ~37-character parenthetical wraps rather than clips at a narrow viewport -- no browser-automation tool available in this execution session; the heading's exact text contract (locked copy, midnight suppression, absent-anchor degradation) was proven by executing sectionHeading() against representative fixtures",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-10-03T02:29:43.049Z",
    "resolved_at": null,
    "milestone": "v1.5"
  },
  {
    "id": 14,
    "kind": "deviation",
    "phase": "21",
    "file": "src/main/java/com/wfm/service/ScheduleOutputService.java",
    "line": null,
    "description": "The 'Unassigned assignment' constraint's ConstraintMatch never indicts an individual AgentAssignment (groupBy/join/join/filter aggregate; justification is Timeslot+int+TimeslotDemandConfig+ScheduleConfig), so its 'No agent assigned for X at Y' description branch is pre-existing dead code in buildConstraintViolations' live path -- confirmed empirically in plan 21-11, not introduced by it, not fixed (out of scope: would require restructuring ScheduleConstraintProvider's stream shape)",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-10-03T03:01:51.216Z",
    "resolved_at": null,
    "milestone": "v1.5"
  }
]
````
