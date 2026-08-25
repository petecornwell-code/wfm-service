# Phase 10: Enriched Upload Parsing - Context

**Gathered:** 2026-07-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Operators upload **one workbook with one worksheet per desk** that fully parses into the agent roster: identity (BambooHR ID, first/last name, job title, email, department, active), an **unbounded** set of `Specialty N` columns, and a **single Mon–Sun day-cell group** whose per-cell value encodes hours-or-status. Per-row and per-sheet validation feeds the existing Upload Results view. The 6-col legacy shape **and** the old flat enriched shape are both retired; a downloadable pre-seeded template is provided.

**In scope:** the workbook parser (`DeskAssignmentUploadService`), a shared `EnrichedColumnLayout` (single source of column order/names) used by parser + template + export, blank-template generation (pre-seeded identity, blank schedule), per-day-cell parsing into the Phase 9 `agent_day_hours` model + day-off/PTO blocks, per-row/per-sheet validation and Upload Results surfacing.

**Out of scope (later phases):** BambooHR↔spreadsheet per-field precedence and the merge report (Phase 11, MRG) — Phase 10 writes spreadsheet data using a **union / coexist** rule, not a true merge. Any change to how the solver consumes days off (MANDATORY/APPROVED-PTO blocking already exists).

**⚠ This phase REVISES several milestone/Phase-9 assumptions — see `<decisions>` D-13..D-16 and `<requirement_revisions>`.**

</domain>

<decisions>
## Implementation Decisions

### Workbook structure (UPL-01)
- **D-01:** **One worksheet per desk.** The **sheet name is the desk** (authoritative); there is **no per-row `Desk` column**. The parser iterates every sheet (not just sheet 0 as today). Consistent with the existing FTE upload, which already parses by sheet name.
- **D-02:** A worksheet whose name matches **no configured desk** → **skip the whole sheet** and report it as a non-blocking entry in Upload Results (`Sheet "X": no matching desk — skipped`). Other valid sheets still import. Desks must be configured beforehand (upload assigns to existing desks only).

### Day-cell contract — the core model (UPL-03/04/05)
- **D-03:** **Seven day columns** (`Monday`…`Sunday`), NOT the originally-planned three Mon–Sun groups (21 columns). Each cell holds **exactly one** of: a **number `>= 0`** (contracted hours), the keyword **`MANDATORY`** (mandatory day off), or **`PTO`** (recurring weekly PTO). Keywords are matched **case-insensitively**; exact tokens are `MANDATORY` and `PTO`.
- **D-04:** **Every day cell is required — blank is never valid.** A row with any blank day cell, or a cell value that is not a number/`MANDATORY`/`PTO`, is **skipped** with a specific reason (UPL-06).
- **D-05:** **Solver semantics — `0`, `MANDATORY`, and `PTO` all mean "not working / not schedulable" that day** (the agent is unavailable; no shift). A number `> 0` = worked that many hours. The three non-working states are **descriptively distinct** (part-time off day vs contractual day off vs recurring PTO) but the solver treats all three as a day off. Confirmed: a `0` day is NOT available as overflow.
  - `number > 0` → `agent_day_hours` row = hours (Phase 9 D-09).
  - `0` → `agent_day_hours` row = `0.00` (Phase 9 D-04 "exists and 0 = not worked").
  - `MANDATORY` → a MANDATORY day-off block (reuse the field-4517 mechanism; Phase 9 D-05).
  - `PTO` → recurring weekly PTO block — **storage deferred to research** (D-12).

### Specialization columns (UPL-02)
- **D-06:** **Numbered `Specialty N` columns** (`Specialty 1`, `Specialty 2`, …, unbounded). Parser detects every header matching the `specialty {n}` prefix and reads all non-blank values. **First non-blank = primary**, rest = secondary (matches current `DeskAssignmentUploadService` lines 344–353). Backward-compatible with today's `Specialty 1`/`Specialty 2` sheets. (Rejected: a single delimited `Specializations` column — breaks compat, needs escaping.)

### Minimum valid row & identity (UPL-01/UPL-07)
- **D-07:** **Minimum valid row = BambooHR ID + all 7 day cells populated.** Identity columns (first/last name, job title, email, department, active) and specialties are **optional** — sourced from BambooHR where blank.
- **D-08:** **Match by BambooHR ID only** (milestone decision — ID is always populated). A row whose BambooHR ID is **not found in the BambooHR cache** → rejected with reason `"BambooHR ID not found"`; **no agent is created** (UPL-07). This simplifies the current code's fuzzy name/email fallback matching (`DeskAssignmentUploadService` lines 249–293).

### Validation & Upload Results (UPL-06)
- **D-09:** **Whole-row skip-and-continue** on the first validation failure in a row (matches current behaviour); other valid rows in the same sheet still import.
- **D-10:** **Numeric hours accepted 0–24** (fractional allowed, e.g. `7.5`). A value **> 24 is clamped to 24 and surfaced as a non-blocking warning** (`Row 8 Tue: 32 → 24`) — clamp is **not silent**. **Negative** numbers and unrecognized words → **skip row** with reason.
- **D-11:** **Upload Results view gains a per-sheet rollup** (`Billing: 12 imported, 2 skipped`), keeps per-row skip reasons, and surfaces **clamp warnings** and **skipped-sheet notices** as non-blocking warnings. (Exact UI layout → planner / optional UI-phase.)

### Blank template generation (folds the pending todo)
- **D-13:** **Blank-template download is IN SCOPE for Phase 10.** Template + parser + export **share one `EnrichedColumnLayout`** (single source of truth for header order/names) so the shapes can never drift. Resolves the design tension the todo raised.
- **D-14:** The template is **pre-seeded**: each desk sheet arrives with the **current roster's identity columns filled** (BambooHR ID, first/last, job title, email, dept, active), leaving the **7 day cells + specialties blank** for the operator. Rationale: minimum row needs the exact BambooHR ID, and hand-typing IDs risks UPL-07 rejection — pre-seeding makes it a round-trip (export current → fill schedule → re-upload).

### Backward-compat & shape retirement (UPL-08)
- **D-15:** **Phase 10 redefines the enriched shape.** Both the **6-col legacy** shape AND the **old flat enriched** shape (single sheet + `Desk` column) are **rejected** with a `"download the new template"` message. Operators do a one-time re-download of the pre-seeded template. Rationale: the old enriched day columns were never parsed into real schedule data, so nothing meaningful is lost; the milestone's "one unified upload" intent justifies the churn. **This revises UPL-08's literal "existing enriched sheets continue to import."**

### Phase 10 / Phase 11 boundary (data safety)
- **D-16:** **Coexist / union with BambooHR-derived days off.** In Phase 10, spreadsheet day cells do **not** clobber the MANDATORY blocks BambooHR derives from field 4517 — a day is off if **either** source says so (Phase 9 D-05 union stance). Consequence: the sheet **cannot un-block** a BambooHR day off (turn it back into a worked day) until the **Phase 11 merge engine**. Chosen for data safety over operator intuitiveness; the real per-field precedence is explicitly Phase 11 (MRG).

### Re-upload / removal semantics
- **D-17:** **Keep clear-then-reimport.** Uploading a desk sheet **clears the whole desk first** (`clearDesk` — unassign agents, delete desk-scoped preferences/exceptions and per-day hours), then re-adds only the rows present. An agent **on the desk but omitted from the sheet is removed** from that desk. Justified because the template is pre-seeded with the full roster, so omission is deliberate.

### Claude's Discretion / deferred to research
- **D-12 (research):** **Recurring-PTO storage mechanism** — reuse `AgentDayOff` with a `PTO` type vs a dedicated recurring-PTO structure vs `AgentException`. Constraints locked: (a) must block the solver = day off (same effect as MANDATORY per D-05); (b) must be **labelled PTO**, not "mandatory", for reporting; (c) must not conflict with Phase 11's BambooHR **dated**-PTO precedence. Researcher/planner choose against the current model.
- Exact `EnrichedColumnLayout` API shape, header casing/whitespace normalization, and how the parser tolerates a detected-but-incomplete sheet — planner/researcher decide, mirroring existing `cellAt` tolerance.

### Folded Todos
- **`2026-07-30-blank-upload-template-one-sheet-per-desk.md`** (score 0.9, `resolves_phase: 10`) — folded via **D-13/D-14** (blank template, one sheet per desk, shared column builder) and the workbook-structure decisions D-01/D-02. The todo's open questions (sheet-name-as-desk vs Desk column; single source of column order; export round-trip) are all resolved here.

</decisions>

<requirement_revisions>
## Requirement Revisions (⚠ downstream + REQUIREMENTS.md must reconcile)

These discussion decisions **change** things locked at milestone start / Phase 9. The planner should treat this section as authoritative for Phase 10; REQUIREMENTS.md / ROADMAP.md / PROJECT.md should be updated to match.

1. **Three Mon–Sun groups (21 cols) → one polymorphic 7-col day group.** PROJECT.md target features and REQUIREMENTS UPL-03/04/05 describe separate contracted-hours / mandatory-day-off / recurring-PTO columns. Phase 10 collapses these into **one Mon–Sun group** where the cell value (`>=0` / `MANDATORY` / `PTO`) encodes which. UPL-03/04/05 are all satisfied by the single group (D-03).
2. **Contracted-hours as "the authority on days worked" → the day cell is the authority.** The milestone-start decision ("Mon–Sun contracted hours are the single authority; mandatory-days-off is a cross-check") is superseded: `0`/`MANDATORY`/`PTO` all = day off, `>0` = worked (D-05). There is no separate cross-check group.
3. **UPL-08 "existing enriched sheets continue to import" → old flat enriched is also retired** (D-15). Both legacy and old-enriched sheets require a one-time re-download.
4. **Clamp behaviour** is clamp-**with-warning**, not silent (D-10 / D-11).

</requirement_revisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements & roadmap
- `.planning/REQUIREMENTS.md` — UPL-01…UPL-08 (note the revisions above), plus "MDL-02 highest-risk" constraint note
- `.planning/ROADMAP.md` §"Phase 10: Enriched Upload Parsing" — goal + 5 success criteria (success criteria wording predates the 7-col consolidation; reconcile with `<requirement_revisions>`)
- `.planning/PROJECT.md` §"Current Milestone: v1.2" — milestone-start design decisions (some now revised, see above)

### Phase 9 foundation this phase writes into (MUST read — the locked storage contract)
- `.planning/phases/09-agent-data-model-foundation/09-CONTEXT.md` — D-01..D-12: `agent_day_hours` table (D-09: absent = no row, not-worked = `0.00` row), per-day resolution precedence (D-03/D-04), name-split via `AgentNameSplitter` (D-06/D-07/D-11), the union-of-not-worked-mechanisms stance (D-05)

### Code the phase modifies (verify current state before editing)
- `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` — the parser being rewritten: shape detection (lines 61–137), per-shape column constants (139–154), clear-desk (156–168, 363–378), row loop + matching + specialties (170–357), `cellAt`/`getCellString` helpers (385–403)
- `src/main/java/com/wfm/service/DeskAgentExportService.java` — export; should share `EnrichedColumnLayout` for round-trip symmetry (touched in Phase 9 plan 09-05)
- `src/main/java/com/wfm/model/Agent.java`, `AgentDayHours` (Phase 9 `agent_day_hours` entity/repo), `AgentDayOff` (MANDATORY blocks), `AgentException`, `AgentPreference` — the storage targets
- `src/main/java/com/wfm/integration/BambooRefreshService.java` §255–303 — where field-4517 MANDATORY `AgentDayOff` rows are generated (the blocks D-16 must coexist with)
- `src/main/java/com/wfm/util/AgentNameSplitter.java` — reused for any name splitting (though sheet now supplies first/last explicitly)
- `src/main/resources/db/migration/` — latest is `V29` (Phase 9); any new recurring-PTO storage (D-12) follows this sequence

No external ADRs/specs — requirements fully captured in the decisions above.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`DeskAssignmentUploadService`** header→index map (lowercase-trimmed) and `cellAt` null-safe read — the parsing foundation to extend for per-desk sheets and the numbered `Specialty N` scan.
- **`clearDesk`** (already deletes per-day `agent_day_hours` via `agentDayHoursRepository.deleteByAgent_Id`, plus preferences/exceptions) — the removal mechanism D-17 keeps.
- **Field-4517 MANDATORY `AgentDayOff` generation** in `BambooRefreshService` — the same block type `MANDATORY` day cells map onto (D-05); PTO likely mirrors this (D-12).
- **FTE upload** (sheet-name-driven parsing) — precedent for D-01's one-sheet-per-desk.

### Established Patterns
- POI single-row header parsing; skip-and-continue per row with `SkippedRow(rowNum, id, name, reason)`.
- Flyway forward-only migrations (`V##__*.sql`), latest `V29`.
- Child-table `@ManyToOne` to Agent (`agent_day_hours`, `AgentDayOff`, `AgentException`, `AgentPreference`).

### Integration Points
- New shared **`EnrichedColumnLayout`** consumed by parser (header→index), template generator, and export (D-13).
- Parser writes: `agent_day_hours` (hours + `0.00`), MANDATORY `AgentDayOff` (union with BambooHR, D-16), recurring-PTO storage (D-12), specializations (`setPrimarySpecialization` + `secondarySpecializations`).
- Upload Results DTO (`DeskAssignmentUploadResult`) extended for per-sheet rollup + warnings (D-11).

</code_context>

<specifics>
## Specific Ideas

- Example valid row (Billing sheet): `EmployeeID=4517 | First=Mary | Last=Watson | … | Monday=8 | Tuesday=8 | Wednesday=0 | Thursday=MANDATORY | Friday=8 | Saturday=PTO | Sunday=0 | Specialty 1=Billing | Specialty 2=Refunds`.
- The pre-seeded template is effectively "export current roster (identity only) → operator fills the schedule → re-upload" — a round-trip, hence the shared column layout.
- Clamp warning wording pattern: `Row {n} (id {id}) {Day}: {value} clamped to 24`.

</specifics>

<deferred>
## Deferred Ideas

- **BambooHR↔spreadsheet per-field precedence + merge report** (MRG-01/02) — Phase 11. Phase 10 uses union/coexist (D-16); Phase 11 lets the sheet un-block a BambooHR day off and produces the merge report.
- **Dated PTO vs recurring PTO reconciliation** — Phase 11 (BambooHR dated PTO wins for covered dates; recurring pattern fills gaps). D-12's storage choice must not preclude this.
- **Consolidating the two "recurring not-worked" mechanisms** (per-day `0` vs MANDATORY `AgentDayOff`) into a single authority — Phase 11 merge work (carried from Phase 9 deferred).

None of these belong in Phase 10 — this phase parses the new shape and writes it (union rule), leaving true precedence to Phase 11.

</deferred>

---

*Phase: 10-enriched-upload-parsing*
*Context gathered: 2026-07-31*
