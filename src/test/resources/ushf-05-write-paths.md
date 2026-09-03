# USHF-05 Write-Path Table

This file is the **canonical USHF-05 deliverable** (D-14). It is parsed at test time by
`UsualShiftWritePathGuardTest` (`src/test/java/com/wfm/service/UsualShiftWritePathGuardTest.java`)
— editing this file changes what the build enforces, not merely what a human reads. The table
below enumerates every write path that can change `agent_usual_shift` data, states what MUST HOLD
after that path runs, and names a test that actually exercises the path (never a test that only
asserts the path is unreachable, except as the second of two proofs for a path that already has a
behavioural one).

Rows 8 and 9 (Template delete, Desk delete) are planner additions beyond D-14's original seven
paths — tracing the code found both, and XCUT-02 requires every reachable write path, not only the
seven D-14 named. Both rows below carry that label in their `Path` cell.

The reason this document exists at all is v1.2 audit finding **I-2**: a guarantee (the BambooHR
merge-precedence rule) that held on one write path (the upload) and stayed **open across two
consecutive milestone audits** because a second entry point (the manual "Refresh from BambooHR"
button) bypassed it silently, with no structural guard to catch the gap. A table with no enforcing
guard is true only on the day it ships; the guard below is what keeps it true afterwards.

## The Table

| Path | Entry point | Source file | Effect on usual-shift data | Proving test |
|---|---|---|---|---|
| Upload — row import | `DeskAssignmentUploadService#uploadDeskAssignments` | `com.wfm.service.DeskAssignmentUploadService` | Writes one `agent_usual_shift` row per resolved Usual Shift cell (D-06); a blank cell writes none (D-07); an unresolvable or ambiguous template name skips the cell and warns, never the row (D-08); a weekday-mask violation skips the cell and warns (D-03, a cell-level skip here, not the inline path's 400) | DeskAssignmentUploadUsualShiftTest |
| Upload — clearDesk | `DeskAssignmentUploadService#clearDesk` | `com.wfm.service.DeskAssignmentUploadService` | Deletes every stored usual shift for every agent on the desk, before any row is re-imported, inside the same transaction (D-11) — safe only because the generated per-desk template pre-fills stored values so a re-upload round-trips as a no-op (D-09) | DeskAssignmentUploadUsualShiftTest |
| Inline edit | `UsualShiftService#setUsualShift` | `com.wfm.service.UsualShiftService` | Writes, replaces or deletes exactly one `(agent, weekday)` row and touches no other weekday and no other agent (USHF-03); rejects with 400 on a weekday-mask violation (D-03) or a template that is not currently effective (P-03) | UsualShiftTracerTest, DeskAgentServiceUsualShiftTest |
| Desk move / removal | `DeskAgentService#removeDeskAgent` | `com.wfm.service.DeskAgentService` | Deletes every stored usual shift for that agent, through the same `UsualShiftService#clearUsualShifts` implementation the clearDesk path uses (D-12) — `agent_day_hours` rows are deliberately NOT deleted here, unchanged from before this phase | DeskAgentServiceUsualShiftTest |
| BambooHR refresh | `BambooRefreshService#refreshDeskAgents` | — | Leaves every stored usual shift byte-identical; `BambooRefreshService` holds no field assignable from `AgentUsualShiftRepository`, so this path structurally cannot write usual-shift data | UsualShiftWritePathTest |
| Scheduling-mode switch | `DeskService#switchSchedulingMode` | — | Leaves every stored usual shift byte-identical in both directions; the switch remains the single-column write (`desk.scheduling_mode`) MODE-04 proved, and never becomes destructive at the moment an operator reaches for the SHIFT-to-SLOT escape hatch (D-13) | UsualShiftWritePathTest |
| The solver | `SolverService#startSolve` | — | Leaves every stored usual shift byte-identical; only `AgentShiftAssignment` is solver output — target (`agent_usual_shift`) and result (`agent_shift_assignment`) stay distinct fields, as `AgentDayHours` and `AgentAssignment` already are | UsualShiftWritePathTest |
| Template delete (PLANNER ADDITION, P-18) | `ShiftTemplateService#deleteShiftTemplate` | `com.wfm.service.ShiftTemplateService` | Refuses with a `ConflictException` naming the count when any `agent_usual_shift` row references the template being deleted, so the FK's `ON DELETE CASCADE` never fires through this path (T-16-09) | ShiftTemplateServiceTest |
| Desk delete (PLANNER ADDITION, P-18) | `DeskService#deleteDesk` | — | Usual shifts cascade away with the desk's shift templates (the FK's `ON DELETE CASCADE`, V47, relied upon by `shift_template.desk_id`'s own cascade at V39); the delete succeeds rather than failing on a foreign-key violation | AgentUsualShiftPostgresTest |

## Guard Allowlists

The two fenced lists below are what `UsualShiftWritePathGuardTest` actually parses and asserts set
equality against — the table above is for humans; these lists are load-bearing for the build. Each
entry is a fully-qualified production class name, one per line. Populated from the actual current
source (`grep -rl` against `src/main/java`, read directly, not predicted) — a class legitimately
appears here when it references the type at all (including the entity/repository's own
declaration), not only when it writes.

### AgentUsualShiftRepository references (Set A)

```
com.wfm.repository.AgentUsualShiftRepository
com.wfm.service.DeskAgentService
com.wfm.service.DeskAssignmentUploadService
com.wfm.service.ShiftTemplateService
com.wfm.service.UsualShiftService
```

### AgentUsualShift entity references (Set B)

```
com.wfm.model.AgentUsualShift
com.wfm.repository.AgentUsualShiftRepository
com.wfm.service.DeskAgentService
com.wfm.service.DeskAssignmentUploadService
com.wfm.service.UsualShiftResolutionService
com.wfm.service.UsualShiftService
```
