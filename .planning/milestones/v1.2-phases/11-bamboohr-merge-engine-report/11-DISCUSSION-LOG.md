# Phase 11: BambooHR Merge Engine & Report - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-18
**Phase:** 11-bamboohr-merge-engine-report
**Areas discussed:** Sync scope & failure behaviour, Precedence & un-blocking, Merge report shape, Solver eligibility flip

---

## Sync scope & failure behaviour

### Sync scope across a multi-desk workbook

| Option | Description | Selected |
|--------|-------------|----------|
| One sync up front, all desks | Pre-scan sheet names → desks, one `listEmployees` + `listTimeOff` before the transaction, merge every sheet against that snapshot | ✓ |
| Per-sheet sync as you go | Call `refreshDeskAgents(deskId)` per sheet; reuses existing method but multiplies API calls and collides with `refreshInProgress` | |
| Reuse `refreshDeskAgents`, batch the fetch | Keep the method for sync-event recording, hoist the HTTP out and pass a shared snapshot | |

**User's choice:** One sync up front, all desks
**Notes:** Grounded in the existing HTTP-before-transaction pattern; `listEmployees` already ignores the department filter, so one fetch serves all sheets.

### Atomic unit for MRG-07

| Option | Description | Selected |
|--------|-------------|----------|
| Whole upload — all or nothing | Sync before any write; 503 aborts with zero DB changes | ✓ |
| Per-sheet | Desks that synced fine still import; failures reported as skipped sheets | |
| Whole upload, distinguishing sync-fail from parse-fail | Sync failure aborts; parse failures keep skip-and-continue | |

**User's choice:** Whole upload — all or nothing
**Notes:** Literal MRG-07 reading; safe given clear-then-reimport is destructive. Parse-level skip-and-continue is unaffected.

### Does the sync write, or only feed the merge?

| Option | Description | Selected |
|--------|-------------|----------|
| Read-only snapshot, merge does all writes | Merge engine is the single writer | ✓ |
| Full refresh writes first, merge overlays | Reuses `persistRefreshData`, but two write passes | |
| You decide | Defer to research | |

**User's choice:** Read-only snapshot
**Notes:** Avoids the double-write that forced Phase 10's union rule.

### Upload latency

| Option | Description | Selected |
|--------|-------------|----------|
| Synchronous, longer timeout | One batched fetch keeps it bounded | ✓ |
| Async job with progress poll | Robust for large rosters, large change to the existing flow | |
| Synchronous, reuse a recent sync | Fast, but weakens MRG-01's freshness guarantee | |

**User's choice:** Synchronous, longer timeout

---

## Precedence & un-blocking

### Can the spreadsheet un-block a BambooHR field-4517 day off?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — sheet fully replaces the pattern | Day group is a complete statement of the week; supersedes field-4517 blocks | ✓ |
| No — keep the union | Strict MRG-02 reading; sheet only fills patterns BambooHR lacks | |
| Sheet replaces, every un-block reported as an override | Correction possible but never silent | |

**User's choice:** Yes — sheet fully replaces the pattern when it supplies one
**Notes:** Reverses Phase 10 D-16, which was explicitly a temporary data-safety stance.

### What counts as "BambooHR has data"?

| Option | Description | Selected |
|--------|-------------|----------|
| Null, empty, or whitespace-only = absent | Uniform rule across all string fields | ✓ |
| Also treat known placeholders as absent | `Variable`, `Unknown`, `TBD` — more correct, more per-field rules | |
| Per-field rule table decided in research | Lock the principle, defer the predicate | |

**User's choice:** Null, empty, or whitespace-only = absent

### Recurring PTO horizon

| Option | Description | Selected |
|--------|-------------|----------|
| Same lookback/lookahead window as the sync | Reuse the existing config | |
| Store the pattern, materialise at solve time | No window to expire | (already built) |
| You decide | Depends on Phase 10 D-12's storage outcome | |

**User's free-text response:** "PTO is likely to change on a weekly basis. MANDATORY is less likely to change."
**Notes:** Three readings were offered back (shorter PTO horizon / rewrite each upload / PTO-as-default overridden by dated records). Investigation then showed the question was largely moot: `SolverService.buildRecurringDaysOff` already resolves recurring PTO at solve time with nothing persisted, so there is no horizon to choose. The question narrowed to how BambooHR's dated PTO arbitrates against those facts, resolved below.

### Contested fields where BambooHR wins

| Option | Description | Selected |
|--------|-------------|----------|
| Discarded, recorded in the merge report | One value per field in the DB | ✓ |
| Discarded silently unless values differ | Less report clutter | |
| Persist both with source-of-record | Richer, needs a schema addition | |

**User's choice:** Discarded, but recorded in the merge report

### PTO scope (follow-up)

| Option | Description | Selected |
|--------|-------------|----------|
| Within the synced window | BambooHR governs every date in lookback/lookahead; sheet fills outside it | ✓ |
| Only dates with an actual PTO record | Sheet pattern applies inside the window too | |
| BambooHR only — sheet PTO ignored | Cleanest, discards the UPL-05 capability | |

**User's choice:** Within the synced window
**Notes:** Preceded by the free-text steer "Lets go with Bamboo records for PTO", plus "0 can stay - it may be an unforeseen day off" — the latter revising Phase 10 D-05 so a `0` cell no longer hard-blocks.

---

## Merge report shape

### Report scope

| Option | Description | Selected |
|--------|-------------|----------|
| Disagreements and gap-fills only | Rows only where sources differed or the sheet filled a gap | ✓ |
| Every field for every agent | Full grid, ~180 rows of mostly-agreement | |
| Per-agent rollup, expandable | Scales, more UI work | |

**User's choice:** Disagreements and gap-fills only

### Report location

| Option | Description | Selected |
|--------|-------------|----------|
| New section in the Upload Results modal | Extends Phase 10's existing modal | ✓ |
| Separate downloadable report | Good for audit, second artifact to open | |
| Both | Most complete, largest scope | |

**User's choice:** New section in the Upload Results modal

### Persistence

| Option | Description | Selected |
|--------|-------------|----------|
| Ephemeral — returned in the upload response | No schema change | ✓ |
| Persisted — queryable later | Survives a closed tab, adds retention questions | |
| Ephemeral + summary in `BambooSyncEvent` | Trend visibility | |

**User's choice:** Ephemeral

---

## Solver eligibility flip

### Refresh vs sheet-supplied pattern

| Option | Description | Selected |
|--------|-------------|----------|
| Refresh must not downgrade a sheet-supplied pattern | `workingDaysKnown` cannot be flipped back to false | ✓ |
| Refresh wins — re-upload to restore | Simple, but silently un-schedules agents | |
| Refresh downgrades, but warns loudly | Allow the flip, never silently | |

**User's choice:** Refresh must not downgrade a sheet-supplied pattern
**Notes:** Closes the hazard at `BambooRefreshService.persistRefreshData` (~line 272) — the UAT 2026-08-12 failure mode.

### `workingDaysKnown` rule

| Option | Description | Selected |
|--------|-------------|----------|
| All 7 day cells parsed — keep current behaviour | Already implemented; why all 18 agents solved | ✓ |
| Either source supplies a complete pattern | Frames the rule around the merged result | |
| You decide | Defer to research | |

**User's choice:** All 7 day cells parsed

### Eligibility callout in the report

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — as a distinct callout | The MRG-06 headline win, worth eyeballing | ✓ |
| No — just another gap-fill | No special treatment | |
| Yes, flagged as lower-confidence | Marked unverified against BambooHR | |

**User's choice:** Yes — as a distinct callout

---

## Claude's Discretion

- Merge-engine structure: dedicated service vs extending `DeskAssignmentUploadService`
- Where the batched snapshot is fetched and how it threads to the per-sheet loop
- Whether `BambooSyncEvent` records upload-triggered syncs
- Report DTO shape and the per-field predicate table implementing D-06

## Deferred Ideas

- Persisted per-field provenance / queryable merge history
- Field-specific sentinel handling (`Variable`, `Unknown`, `TBD`) for the has-data predicate
- Async/job-based upload with progress polling
- Reconciling upload-time vs solve-time hours validation (fractional hours accepted on upload, rejected pre-solve)
- Constraint weighting for surplus hours earned via `0` days

## Session Note

This discussion was interrupted mid-way by a live-environment exercise (generating a fake-data roster workbook for `Stubhub (EN)`, seeding a full week of staffing requirements, uploading and solving). That exercise produced several findings folded into CONTEXT.md: the live 18-of-22 import baseline, the feasible full-week solve (hard 0 / soft −6000), the upload-vs-solve fractional-hours validation gap, and empirical confirmation of the `0`-day scheduling behaviour that prompted the D-05 revision.
