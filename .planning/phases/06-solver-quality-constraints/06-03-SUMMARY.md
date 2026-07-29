---
phase: "06-solver-quality-constraints"
plan: "03"
subsystem: "integration/solver"
tags: ["bamboohr", "field-4517", "mandatory-days-off", "qual-01", "checkpoint"]
dependency_graph:
  requires: ["06-01", "06-02"]
  provides: ["Agent.workingDaysKnown", "MANDATORY AgentDayOff rows from field 4517", "SolverService.filterEligible 4th criterion"]
  affects: ["frontend PTO tab (consumes existing MANDATORY rendering, unchanged)", "Phase 7 DIAG (deferred BambooSyncEvent extension)"]
tech_stack:
  added: []
  patterns: ["V25-style boolean migration with permanent DEFAULT TRUE", "putIfAbsent priority ordering for dedup map", "log.warn count-only surfacing (no BambooSyncEvent extension this phase)"]
key_files:
  created:
    - src/main/resources/db/migration/V28__add_agent_working_days_known.sql
  modified:
    - src/main/java/com/wfm/model/Agent.java
    - src/main/java/com/wfm/integration/BambooRefreshService.java
    - src/main/java/com/wfm/service/SolverService.java
    - src/test/java/com/wfm/integration/BambooRefreshServiceTest.java
    - src/test/java/com/wfm/service/SolverServiceEligibilityFilterTest.java
decisions:
  - "V28 keeps DEFAULT TRUE permanently (unlike V25's employmentType pattern) so agents created before their first BambooHR refresh are not incorrectly excluded from the solver by D-07"
  - "D-05/D-07 surfacing: log.warn used for outliers and a single data-gap summary line (MVP). BambooSyncEvent was deliberately NOT extended this phase — persisted diagnostics deferred to Phase 7 DIAG to avoid a breaking schema change there"
  - "MANDATORY rows generated BEFORE the PTO loop into the shared dedupedDaysOff map, using putIfAbsent, so MANDATORY always wins conflicts on the same (agent, date) key without changing the existing PTO priority logic"
  - "status=APPROVED on generated MANDATORY rows (A3) — they represent facts derived from the working pattern, not pending requests"
  - "Task 3 (PTO tab render + desk-scale data-gap proportion) is verify-not-build (D-10); UI left untouched per operator approval"
metrics:
  duration_minutes: 45
  completed_date: "2026-07-29"
  tasks_completed: 3
  tasks_total: 3
  files_created: 1
  files_modified: 5
---

# Phase 06 Plan 03: MANDATORY Day-Off Generation + Solver Exclusion Summary

Completes QUAL-01: BambooHR field 4517 (`customWorkingdays`) now drives recurring `MANDATORY` `AgentDayOff` rows that the existing solver and PTO tab already honour, data-gap agents are excluded from scheduling, and outliers/gaps are surfaced via log.warn.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | V28 migration + workingDaysKnown on Agent + filterEligible filter | `d07b7e5` | `V28__add_agent_working_days_known.sql`, `Agent.java`, `SolverService.java`, `SolverServiceEligibilityFilterTest.java` |
| 2 | Generate MANDATORY rows + flag data-gap/outliers + remove dead match | `b2b195d` | `BambooRefreshService.java`, `BambooRefreshServiceTest.java` |
| 3 | Verify MANDATORY weekends render in PTO tab + desk-scale coverage (checkpoint:human-verify) | operator-approved, no code commit | — |

## What Was Built

**Task 1 — V28 migration + Agent flag + solver exclusion:**
- `V28__add_agent_working_days_known.sql`: `ALTER TABLE agent ADD COLUMN working_days_known BOOLEAN NOT NULL DEFAULT TRUE;` — unlike the V25 `employmentType` analog, the `DEFAULT TRUE` is kept **permanently** (not dropped after backfill) so agents created before their first BambooHR refresh are not incorrectly excluded by D-07.
- `Agent.workingDaysKnown` (default `true`) with `isWorkingDaysKnown()` / `setWorkingDaysKnown(boolean)`.
- `SolverService.filterEligible` gained a fourth criterion, `.filter(Agent::isWorkingDaysKnown)`, appended after the `primarySpecialization` filter (method signature unchanged, order of surviving agents preserved). Confirmed at `src/main/java/com/wfm/service/SolverService.java:975-983` — exactly four `.filter(...)` calls.

**Task 2 — MANDATORY generation in `BambooRefreshService.persistRefreshData`:**
- Immediately after the existing `deleteByAgent_IdAndDateBetween` + `flush()` idempotency window (`:250-253`), a new block (`:255-303`) iterates each refreshed desk agent, looks up its `BambooEmployee` via the already-built `employeesByBambooId` map, and calls `WorkingDaysParser.parseWorkingDays(emp.customWorkingdays())`.
  - **Data gap** (empty `Optional`): `dataGapCount++`, `agent.setWorkingDaysKnown(false)`, saved, agent skipped (D-07).
  - **Parseable pattern**: `agent.setWorkingDaysKnown(true)`, saved; `offDays` computed via `WorkingDaysParser.offDaysFrom`; if `!isStandardTwoContiguousDaysOff(offDays)` a `log.warn` names the agent and off-day **count** only (D-05, T-6-ID — no raw `customWorkingdays` value logged); a `MANDATORY`/`APPROVED` (A3) `AgentDayOff` is built for every date in `[from, to]` matching an off-day and inserted into `dedupedDaysOff` via `putIfAbsent`, keyed `agentId + "|" + date`.
- Because this block runs and populates `dedupedDaysOff` **before** the pre-existing PTO loop (`:309-335`), and the PTO loop's own priority check only overwrites when `dayOffType == MANDATORY && existing.getType() != MANDATORY`, generated MANDATORY rows win all PTO conflicts on the same key without any change to the PTO-vs-PTO priority logic (D-08 unchanged).
- After the PTO loop, a single `log.warn` (`:337-342`) summarizes `dataGapCount` for the desk if `> 0` (D-05/D-07, T-6-RP — visible signal instead of silent exclusion).
- The dead `"MANDATORY".equalsIgnoreCase(type)` branch was removed; `"holiday"` still maps to `MANDATORY`, everything else collapses to `PTO` (`:314-317`, D-03).
- Idempotency is inherited for free: the pre-existing `deleteByAgent_IdAndDateBetween` + `flush()` window already clears the agent's rows in `[from, to]` before this block runs, so a re-refresh regenerates the same MANDATORY set without duplication.

**Task 3 — checkpoint:human-verify (PTO tab render + desk-scale coverage):**
- No code was changed — this is a verify-not-build checkpoint per D-10 (`frontend/src/pages/ScheduleResults.tsx` PtoTab already renders `type === 'MANDATORY'` cells red with a label/legend, built in an earlier phase).
- **Operator response:** "Yes approved."

## Design Decision Required by Plan (D-05/D-07 Surfacing)

Per the plan's `<interfaces>` section, this phase deliberately uses **`log.warn` only** for both outlier patterns and the data-gap count summary (MVP surfacing). `BambooSyncEvent` was **not** extended in this phase to carry these diagnostics as structured/persisted data — that is intentionally deferred to Phase 7 (DIAG) so as not to introduce a breaking schema change to `BambooSyncEvent` twice. Operators currently have visibility only via CloudWatch logs, not via a UI diagnostic surface.

## Verification

Already confirmed by the orchestrator prior to this dispatch (not re-run by this executor):

```
./gradlew test                                                             — BUILD SUCCESSFUL (full suite)
./gradlew test --tests "com.wfm.integration.BambooRefreshServiceTest"      — BUILD SUCCESSFUL
./gradlew test --tests "com.wfm.service.SolverServiceEligibilityFilterTest" — BUILD SUCCESSFUL
./gradlew test --tests "com.wfm.service.SolverServicePtoFilterTest"        — BUILD SUCCESSFUL
./gradlew test --tests "com.wfm.integration.WorkingDaysParserTest"         — BUILD SUCCESSFUL
```

All 4 `must_haves.artifacts` confirmed present on disk; all 3 `must_haves.key_links` patterns match in source; dead `"MANDATORY".equalsIgnoreCase(type)` match confirmed removed (`grep` count 0). Code is deployed to the dev sandbox (deploy run `27364497136`, commit `f173c51`).

Task 3's checkpoint was reviewed and approved by the operator ("Yes approved"). The executor did not independently drive the frontend or observe the PTO tab.

## Deviations from Plan

None — Tasks 1 and 2 were executed exactly as written (see commits `d07b7e5`, `b2b195d`).

## Issues / Outstanding Verification Debt

**1. Desk-scale data-gap proportion NOT CAPTURED (Task 3 acceptance criterion 3 unmet).**

The plan's Task 3 required recording the observed data-gap proportion for live scheduled desks (e.g. StubHub-GE, Vinted-UA) — i.e., what fraction of desk agents have blank/`Variable` `customWorkingdays` and are therefore excluded from the solver via D-07. The operator approved the checkpoint overall but did **not** report this proportion, and did not describe what was observed in the PTO tab (criteria 1 and 2 are approved-but-not-independently-verified by the executor; see above).

This matters because D-07 **silently excludes** any agent with a data gap from `filterEligible` — if a live desk has a high proportion of blank/`Variable` field-4517 values, the solver would be missing a large fraction of its real agent pool and could produce a badly understaffed or infeasible schedule without an obvious error surfacing to the operator.

**This proportion still needs to be captured before Phase 6 work is considered fully verified for production desks.** Recommended approach to capture it:

```bash
aws logs tail /ecs/wfm-service-dev --follow --region eu-west-2 --filter-pattern "customWorkingdays"
```

(Trigger a BambooHR refresh for a live desk, then tail for the `{N} agent(s) on desk {deskId} have blank/Variable customWorkingdays...` `log.warn` line and compare `N` against the desk's total scheduled agent count.)

**2. Task 3 UI observation was operator-approved, not executor-verified.** The executor did not run the frontend, trigger a refresh, or view the ScheduleResults PTO tab. The "MANDATORY" red cell rendering and label/legend clarity (criteria 1 and 2) rely entirely on the operator's "Yes approved" response and the pre-existing (out-of-scope, D-10) frontend code, not on independent executor observation.

## Known Stubs

None. All generation logic is fully wired: `BambooRefreshService` writes real `MANDATORY` rows to `agentDayOffRepository`; `SolverService.filterEligible` reads the real `workingDaysKnown` flag; the frontend PTO tab renders real persisted rows (pre-existing, unchanged).

## Threat Surface Scan

No new network endpoints, auth paths, or trust-boundary changes beyond what the plan's own `<threat_model>` already registers (T-6-ID, T-6-RP, T-6-DT, T-6-SC — all addressed per the plan's mitigation column; see source citations above for T-6-ID compliance — only agent id/name and off-day *count* are logged, never the raw `customWorkingdays` string).

## Self-Check: PASSED

- `src/main/resources/db/migration/V28__add_agent_working_days_known.sql` — FOUND
- `src/main/java/com/wfm/model/Agent.java` (workingDaysKnown) — FOUND
- `src/main/java/com/wfm/integration/BambooRefreshService.java` (WorkingDaysParser usage, MANDATORY generation, dead-match removed) — FOUND
- `src/main/java/com/wfm/service/SolverService.java` (isWorkingDaysKnown, four filters) — FOUND
- Commit `d07b7e5` — FOUND (`git log --oneline --all | grep d07b7e5`)
- Commit `b2b195d` — FOUND (`git log --oneline --all | grep b2b195d`)
