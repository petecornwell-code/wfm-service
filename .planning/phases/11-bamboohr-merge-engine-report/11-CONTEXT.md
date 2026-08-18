# Phase 11: BambooHR Merge Engine & Report - Context

**Gathered:** 2026-08-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Every desk-assignment upload runs a **fresh BambooHR sync before any merge decision**, then merges the parsed spreadsheet against that snapshot using documented **per-field precedence** — BambooHR authoritative where it has data, the spreadsheet filling gaps — and returns an operator-facing **merge report** showing where the two sources disagreed and where the sheet filled a gap.

**In scope:** the batched pre-transaction BambooHR fetch; the merge engine and its per-field precedence rules; replacement of Phase 10's union/coexist rule (D-16) with true precedence, including the spreadsheet's ability to un-block a BambooHR field-4517 day off; dated-vs-recurring PTO arbitration; the merge report in Upload Results; protecting `workingDaysKnown` from being downgraded by a later refresh.

**Out of scope:** changing the day-cell contract or the workbook shape (Phase 10, settled); the solver's consumption of days off beyond what precedence changes; async/job-based uploads; persisting per-field provenance to the database.

**⚠ This phase REVISES Phase 10 D-05 and D-16 — see `<requirement_revisions>`.**

</domain>

<decisions>
## Implementation Decisions

### Sync scope & failure behaviour (MRG-01, MRG-07)

- **D-01:** **One sync up front for the whole workbook.** Pre-scan sheet names → desks, then issue **one** `listEmployees` + `listTimeOff` fetch **before** the transaction opens, and merge every sheet against that single in-memory snapshot. Rationale: `BambooRefreshService.refreshDeskAgents` deliberately performs its HTTP *outside* the transaction to avoid holding a DB connection during slow external calls; `uploadDeskAssignments` is `@Transactional` across the whole workbook, so a per-sheet refresh would violate that pattern, multiply API calls (rate-limit risk), give sheets inconsistent snapshots, and collide with the `refreshInProgress` guard. `listEmployees` already ignores the department filter, so one fetch serves all sheets.

- **D-02:** **The whole upload is the atomic unit.** The sync happens before any write; a failure (503/429) aborts with a clear operator message and **zero** DB changes. Literal reading of MRG-07 ("no partial merge is written"), and the safe choice given clear-then-reimport (D-17) is destructive. Per-row/per-sheet **parse** failures keep Phase 10's skip-and-continue behaviour unchanged — only **sync** failure aborts everything. — **Reversibility:** costly — loosening to per-sheet atomicity later means unpicking the single transaction boundary the merge engine is built inside.

- **D-03:** **The sync is a read-only snapshot; the merge engine is the sole writer.** Do NOT run `persistRefreshData` as part of upload. Rationale: a refresh that writes field-4517 MANDATORY `AgentDayOff` rows which the merge must then reconcile against is exactly the double-write that forced Phase 10's union rule (D-16). One writer, one pass. — **Reversibility:** costly — the precedence rules assume nothing else has already written days off for these agents.

- **D-04:** **Synchronous upload with a longer timeout.** No async job, no job-id polling. One batched fetch (not N) keeps latency bounded, and operators already wait on uploads. Explicitly rejected: reusing a recent successful sync within N minutes — that weakens MRG-01's "always against current data" guarantee.

### Per-field precedence (MRG-02)

- **D-05:** **The spreadsheet's Mon–Sun day group fully REPLACES the BambooHR field-4517-derived pattern where the sheet supplies one.** The day group is a complete statement of the week, so it is not unioned with the field-4517 blocks — it supersedes them. **This reverses Phase 10 D-16**, which was explicitly a temporary data-safety stance pending this phase. Without this the sheet can never correct a wrong BambooHR pattern. — **Reversibility:** one-way — operators will rely on the sheet to fix bad BambooHR patterns; reverting to a union would silently re-block days they had corrected, with no signal that it happened.

- **D-06:** **"BambooHR has data" = not null, not empty, not whitespace-only.** One uniform rule across all string fields, mirroring existing blank-handling in the codebase. Where BambooHR is absent by that test, the spreadsheet's value is used. Rejected for now: field-specific sentinel handling (e.g. `WorkingDaysParser` already treats `Variable` as unparseable, job titles like `Unknown`/`TBD`) — more correct in practice but more per-field rules to maintain; research may revisit if real BambooHR data demands it.

- **D-07:** **Contested fields where BambooHR wins: the spreadsheet value is discarded, but recorded in the merge report.** Only the winning value is persisted — no shadow columns, one value per field in the DB. MRG-05 is satisfied by the report, not by storage.

- **D-08:** **Fields carried by only one source are uncontested.** BambooHR does not carry per-day contracted hours or specializations — those are spreadsheet-only. BambooHR carries `displayName`, `workEmail`, `department`, `jobTitle`, `status`, `employmentHistoryStatus`, `customWorkingdays`, plus dated time-off.

### PTO precedence (MRG-03)

- **D-09:** **BambooHR is authoritative for PTO within its synced window.** For every date inside the `bamboohr.time-off.lookback-weeks` / `lookahead-weeks` window, BambooHR governs: a date with **no** PTO request there means the agent **works**, overriding a spreadsheet `PTO` day cell. **Outside** that window the spreadsheet's recurring weekly pattern fills. This keeps recurring PTO meaningful for future dates while BambooHR is authoritative wherever it actually has visibility. — **Reversibility:** costly — the window boundary becomes a documented operator-visible rule; changing it later shifts which dates are blocked without any data change.

- **D-10:** **No new storage or materialisation window for recurring PTO.** `SolverService.buildRecurringDaysOff` (line 1038) already resolves the sheet's recurring MANDATORY/PTO labels from `agent_day_hours` into in-memory `AgentDayOff` facts **at solve time**. Nothing is persisted, so there is no window to expire and no staleness — re-solving always re-derives from the current pattern. Phase 11 arbitrates these facts against BambooHR's dated rows per D-09; it does not introduce a persisted PTO horizon. (This supersedes the "what date range should recurring PTO be materialised over?" question — the answer was already built.)

### Merge report (MRG-04, MRG-05)

- **D-11:** **Report scope = disagreements and gap-fills only.** A row appears only where the two sources differed (BambooHR overrode the sheet) or where the sheet filled a BambooHR gap. Silent agreement is not shown. Rationale: 18 agents × ~10 fields is ~180 cells of mostly-agreement; both MRG-04 and MRG-05 are about spotting divergence, so the report stays proportional to what needs attention.

- **D-12:** **The report is a new section in the existing Upload Results modal**, alongside Phase 10's per-sheet rollup, skip reasons and clamp warnings — one place for everything about the upload. Reuses `DeskAssignmentUploadResult`, which already carries `warnings` and `sheetSummaries`.

- **D-13:** **The report is ephemeral** — built during the merge, returned in `DeskAssignmentUploadResult`, gone when the modal closes. No schema change, matching how skip reasons and warnings already work. Rejected: a persisted provenance table (adds retention questions for a dev-stage need that has not been demonstrated).

- **D-14:** **Agents who became solver-eligible via the spreadsheet get a distinct callout** in the report. These are the milestone's headline win (MRG-06) and their working pattern rests entirely on operator-typed data with no HR-system backing, making them the most valuable rows to eyeball.

### Solver eligibility (MRG-06)

- **D-15:** **A later BambooHR refresh must not downgrade a spreadsheet-supplied pattern.** Once the sheet has supplied a full Mon–Sun pattern, `workingDaysKnown` cannot be flipped back to `false` by a subsequent refresh. **Hazard this closes:** `BambooRefreshService.persistRefreshData` currently sets `workingDaysKnown = false` for any agent whose field 4517 is blank or `Variable` — so a routine manual refresh silently drops sheet-provisioned agents out of the solve, which is exactly the UAT 2026-08-12 failure. Implies tracking that the pattern is sheet-sourced. — **Reversibility:** costly — needs a persisted marker distinguishing sheet-sourced from BambooHR-sourced patterns.

- **D-16:** **`workingDaysKnown` resolves true when all 7 day cells parsed** — preserve current behaviour. `DeskAssignmentUploadService` (line ~451) already does this, and it is why all 18 agents in the 2026-08-18 live test were solver-eligible. Phase 11 preserves it through the merge rather than reworking the rule.

### Claude's Discretion

- Exact merge-engine structure (a dedicated `AgentMergeService` vs extending `DeskAssignmentUploadService`) — planner decides. `DeskAssignmentUploadService` is already large; the parse/merge split is a natural seam.
- Where the batched snapshot is fetched and how it is threaded to the per-sheet loop.
- Whether `BambooSyncEvent` records upload-triggered syncs (D-03 makes the upload sync read-only, so the existing sync-event recording in `refreshDeskAgents` does not apply automatically).
- The report DTO shape and the per-field predicate table implementing D-06.

</decisions>

<requirement_revisions>
## Requirement Revisions (⚠ downstream + Phase 10 CONTEXT must reconcile)

1. **Phase 10 D-16 (union/coexist) is REPLACED by true precedence.** D-05 above: the spreadsheet's day group supersedes BambooHR's field-4517 pattern where supplied, and the sheet CAN un-block a BambooHR day off. Phase 10 chose the union deliberately as a temporary data-safety stance and named Phase 11 as where it would be resolved.

2. **Phase 10 D-05 is REVISED: a `0` day cell does NOT hard-block scheduling.** Phase 10 recorded "`0`, `MANDATORY`, and `PTO` all mean not working / not schedulable… Confirmed: a `0` day is NOT available as overflow." Operator decision 2026-08-18: **`0` means no contracted hours but the agent remains available** — "it may be an unforeseen day off" — a softer signal than MANDATORY/PTO, which continue to hard-block.
   - This matches what the code already does: `buildRecurringDaysOff` (`SolverService.java:1042`) skips rows whose `dayOffType` is null, so a `0`-hours row produces no `AgentDayOff` fact and never reaches the hard "Agent day off" constraint.
   - **Confirmed live 2026-08-18:** in the full-week solve, Bakar Gelashvili was scheduled 4h on Wednesday 2026-01-07 against a `0` cell, finishing at 36h against a 32h contract. This is now intended behaviour, not a defect.
   - **Consequence for planning:** because `0` days do not block, agents can exceed weekly contracted hours through them. If that surplus should be discouraged, it is a constraint-weight matter, not a parser or merge one.

</requirement_revisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap
- `.planning/REQUIREMENTS.md` — MRG-01…MRG-07 (lines 33–39), plus the fresh-sync latency risk note (line 74) and the Phase 10/11 boundary note (line 76)
- `.planning/ROADMAP.md` §"Phase 11: BambooHR Merge Engine & Report" — goal + 6 success criteria
- `.planning/PROJECT.md` §"Current Milestone: v1.2"

### Upstream phase contracts (MUST read — these are what Phase 11 revises)
- `.planning/phases/10-enriched-upload-parsing/10-CONTEXT.md` — D-01..D-17. **D-16 (union rule) and D-05 (`0` = not schedulable) are both revised by this phase** — see `<requirement_revisions>`. D-08 (ID-only match), D-17 (clear-then-reimport) and the day-cell contract stand unchanged.
- `.planning/phases/09-agent-data-model-foundation/09-CONTEXT.md` — `agent_day_hours` storage contract, per-day resolution precedence, `AgentNameSplitter`

### Code the phase modifies (verify current state before editing)
- `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` — the upload path; `@Transactional` at line 77–78, sheet loop from ~199, ID-match gate ~345, allowlist gate ~418, `workingDaysKnown = true` at ~451, `agent_day_hours` write from ~455
- `src/main/java/com/wfm/integration/BambooRefreshService.java` — `refreshDeskAgents` (90–144, HTTP-before-transaction pattern D-01 follows), `persistRefreshData` (147+), field-4517 MANDATORY generation and the `workingDaysKnown = false` downgrade at ~272 (the D-15 hazard)
- `src/main/java/com/wfm/service/SolverService.java` — `buildRecurringDaysOff` (1038–1060, the recurring PTO/MANDATORY materialisation D-10 relies on), `filterEligible` (~1073, the `workingDaysKnown` gate), day-off loading and `allDaysOff` assembly (137–162)
- `src/main/java/com/wfm/service/ClientManagementService.java` — `findCachedEmployee` / `findEmployeeStatus`, the current BambooHR cache read path
- `src/main/java/com/wfm/integration/BambooEmployee.java` — the field set available from BambooHR (D-08)
- `src/main/java/com/wfm/util/EnrichedColumnLayout.java` — the shared column contract (unchanged by this phase)

No external ADRs/specs — requirements fully captured in the decisions above.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **HTTP-before-transaction pattern** in `refreshDeskAgents` (lines 85–144) — the precedent D-01 follows for the batched pre-transaction fetch.
- **`buildRecurringDaysOff`** — already materialises the sheet's recurring MANDATORY/PTO at solve time; D-10 means Phase 11 arbitrates against these facts rather than building new storage.
- **`DeskAssignmentUploadResult`** — already carries `warnings`, `skippedDetails`, `sheetSummaries`, `skippedSheets`; the merge report (D-12/D-13) extends this DTO.
- **`BambooSyncEvent` + `bambooSyncEventService.record`** — `REQUIRES_NEW` write that survives caller rollback; available if upload-triggered syncs should be recorded.
- **`AgentEligibilityService.isIncludedByTitleAllowlist`** — the single schedulability control shared by solver, upload and template.

### Established Patterns
- Skip-and-continue per row with `SkippedRow(rowNum, id, name, reason)`; specific reasons, never a generic message.
- Non-blocking warnings surfaced to Upload Results rather than logged only (Phase 10 D-10/D-11).
- Flyway forward-only migrations; latest on disk is `V35`.
- `TransactionTemplate` for self-invoked transactional work (Spring proxies cannot intercept self-invocation).

### Integration Points
- Batched BambooHR snapshot → merge engine → single write pass (D-01/D-03).
- Merge engine writes: `Agent` identity fields, `agent_day_hours`, MANDATORY/PTO labels, specializations — replacing rather than unioning where the sheet supplies a pattern (D-05).
- Merge report accumulates through the sheet loop and returns in `DeskAssignmentUploadResult` (D-11..D-14).
- `workingDaysKnown` provenance marker read by `BambooRefreshService` to honour D-15.

</code_context>

<specifics>
## Specific Ideas

- **Live baseline captured 2026-08-18** on desk `Stubhub (EN)` (`6170be17-3bee-41da-9d81-62ddd50c786f`, tenant 1): 18 of 22 `StubHub - GE` employees import (the other 4 fail the job-title allowlist — 2 Team Lead, 1 SME, 1 Key Account Manager). Full-week solve 2026-01-05…01-11 reached **feasible, hard 0 / soft −6000**, 86 shifts, 653 scheduled hours against 593 required (110% coverage). Useful as a before/after fixture for the merge work.
- **Validation-layer gap found 2026-08-18:** the parser accepts fractional contracted hours (Phase 10 D-10 allows e.g. `7.5`), but `SolverService` pre-solve validation rejects any value that is not a whole multiple of the solve increment — a 7.5h agent parsed cleanly then blocked the solve with 5 validation errors. Upload-time and solve-time validation disagree. Not Phase 11 scope, but the merge report is where an operator would expect to be warned.
- The two "recurring not-worked" mechanisms (per-day `0`/label rows vs `AgentDayOff` rows) now have clearly different semantics after the D-05 revision — `0` is soft/available, MANDATORY and PTO hard-block. Worth stating explicitly in any operator-facing docs.

</specifics>

<deferred>
## Deferred Ideas

- **Persisted per-field provenance** (queryable merge history, "why is this agent's email wrong?" investigations) — rejected for Phase 11 by D-13; revisit if operators ask for after-the-fact auditing.
- **Field-specific sentinel handling** for D-06 (`Variable`, `Unknown`, `TBD` treated as absent) — deferred unless research finds real BambooHR data demands it.
- **Async/job-based upload** with progress polling — deferred by D-04; revisit if roster growth makes the synchronous fetch too slow.
- **Reconciling upload-time and solve-time hours validation** (the fractional-hours gap above) — belongs with solver/validation work, not the merge engine.
- **Constraint weighting for surplus hours earned via `0` days** — follows from the D-05 revision; a solver-tuning concern.

### Reviewed Todos (not folded)
- **`2026-07-30-blank-upload-template-one-sheet-per-desk.md`** (matched 0.9 on keywords) — already folded and delivered by Phase 10 (D-13/D-14; `DeskAssignmentTemplateService` exists and is live). Keyword match only, no Phase 11 work.
- **`2026-08-13-cross-agent-seat-displacement.md`** (matched 0.9) — solver move work from Phase 12, unrelated to the merge engine.
- **`2026-08-14-terraform-db-password-drift.md`** (matched 0.6) — infrastructure, unrelated.

</deferred>

---

*Phase: 11-bamboohr-merge-engine-report*
*Context gathered: 2026-08-18*
