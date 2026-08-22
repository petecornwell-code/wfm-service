---
schema_version: 1
open_count: 4
waived_count: 0
fixed_count: 0
total_count: 4
last_updated: 2026-08-22T00:37:58.146Z
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
  }
]
````
