# Phase 5: Agent Data Enrichment & Desk Upload - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-11
**Phase:** 05-agent-data-enrichment-desk-upload
**Areas discussed:** Employment-type storage & UI, Non-schedulable job titles, Upload failure UX, BambooHR 503 + requested-PTO diagnostics, Spreadsheet upload contract

---

## Pre-validated facts (locked before discussion)

Carried in from spike work during the same session:

1. BambooHR field for employment type = `employmentHistoryStatus`. Tenant-observed values: Full-time, Part-Time, Probation Period, PIP, Notice of Resignation. `payType` and `standardHoursPerWeek` are blank.
2. Part-time mapping = `Y` iff `employmentHistoryStatus == "Part-Time"`, else `N`.
3. PTO API behavior: `/time_off/requests` returns approved + requested separately; `dates` object has `"YYYY-MM-DD": "amount"` where `"0"` = day inside span that doesn't count. DATA-03 requires only `approved` to block scheduling.
4. Sample data shape lives at `src/main/resources/sample-data/production_agents.xlsx` (16 columns including Mon–Sun PTO/Weekend annotations).

---

## Employment-type storage + UI surfacing

| Option | Description | Selected |
|--------|-------------|----------|
| enum + dropdown filter | `Agent.employmentType: enum {FULL_TIME, PART_TIME}`. Column + dropdown filter (All / Full-time / Part-time). Clean, future-proof if more types appear. | ✓ |
| boolean partTime + toggle | `Agent.partTime: boolean`. 3-state toggle. Simpler schema. | |
| string verbatim + dropdown filter | `Agent.employmentHistoryStatus: String` stored as BambooHR returns it. No mapping logic but couples schema to BambooHR vocabulary. | |

**User's choice:** enum + dropdown filter
**Notes:** Mapping rule applied at refresh time so schema stays decoupled from BambooHR vocabulary (D-04).

---

## Non-schedulable job titles config

| Option | Description | Selected |
|--------|-------------|----------|
| JobTitleConfig table + Configuration page | New table keyed on (tenantId, jobTitle); Configuration page lists distinct titles with checkbox. Solver filters at eligibility time. Edit once → applies to all agents with that title. | ✓ |
| Per-agent nonSchedulable boolean | Operator toggles per-agent. Most flexible; tedious for "mark all Team Leads non-schedulable". | |
| Global tenant blocklist | Set<String> on tenant config. Lightweight, no audit trail per title. | |

**User's choice:** JobTitleConfig table + Configuration page
**Notes:** Auto-populate rows on refresh (D-09) so the Configuration UI always reflects every title currently in the roster.

---

## Upload failure UX (DATA-01)

| Option | Description | Selected |
|--------|-------------|----------|
| Modal after upload | Modal opens with summary + expandable per-row failure list + CSV download. Doesn't pollute the page. | ✓ |
| Inline expandable results section | Persistent card under the upload button until next upload. | |
| Persistent results table at /imports | Audit trail of historical uploads. | |
| Block on any failure | Reject entire upload on any failure. Strictest, most friction. | |

**User's choice:** Modal after upload
**Notes:** Requires the backend `skippedDetails` to carry structured row info (D-18), not free-text strings.

---

## BambooHR 503 + requested-PTO diagnostics

| Option | Description | Selected |
|--------|-------------|----------|
| Configuration page sync panel | "BambooHR Sync Status" card on Configuration. Requested PTO as small badge on each agent row in DeskAgents. | ✓ |
| Persistent app-level banner + DeskAgents column | Yellow banner site-wide; separate column on DeskAgents for requested PTO dates. | |
| New /diagnostics page | Dedicated tab for all sync issues. Heavier to build. | |

**User's choice:** Configuration page sync panel
**Notes:** Pairs naturally with the BambooHR config that already lives on the Configuration page.

---

## Spreadsheet upload contract

| Option | Description | Selected |
|--------|-------------|----------|
| Keep existing 6-col format | Don't touch the contract; treat production_agents.xlsx as read-only view. | |
| Adopt 16-col production_agents.xlsx | Operators upload the same shape the system produces (export → edit → re-upload). | |
| Accept both shapes | Parser detects shape from headers. Most permissive. | ✓ |

**User's choice:** Accept both shapes
**Notes:** Detection is header-based, not column-count based (D-14). Mon–Sun schedule columns are informational only — never overwrite PTO from spreadsheet (D-15).

---

## Claude's Discretion

- DB migration tool/style (follow existing Liquibase/Flyway conventions)
- Modal component (reuse existing frontend modal pattern)
- Sync-status card layout (match existing Configuration card style)
- Exact 503 retry message wording — match "retry in 60 seconds" pattern but pluralize from Retry-After header value

## Deferred Ideas

- Persistent imports history at `/imports` — its own phase if audit trail becomes a need
- Per-agent `nonSchedulable` override — revisit if a future phase needs per-agent exception to a title-level rule
- App-level persistent banner for BambooHR errors — deferred in favor of sync-status card
- Dedicated `/diagnostics` page — deferred; Configuration card covers Phase 5 needs
- Storing `employmentHistoryStatus` verbatim alongside the mapped enum — add only if a future phase needs the unmapped value
