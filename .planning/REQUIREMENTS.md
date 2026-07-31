# Requirements: WFM Service v1.2 — Unified Agent Provisioning

**Milestone:** v1.2
**Status:** Active
**Created:** 2026-07-29

---

## v1 Requirements

### Upload Format & Parsing

> **Revised 2026-07-31 (Phase 10 discussion — see `10-CONTEXT.md`).** The three separate Mon–Sun
> column groups (contracted-hours / mandatory-day-off / recurring-PTO, ~21 columns) are consolidated
> into **one Mon–Sun day-cell group (7 columns)** where each cell's *value* encodes status:
> a number `>= 0` = contracted hours (`0` = day not worked), the keyword `MANDATORY` = mandatory day
> off, `PTO` = recurring weekly PTO. All of `0` / `MANDATORY` / `PTO` mean "not schedulable that day".
> Every day cell is required — **blank is invalid**. The workbook has **one worksheet per desk** (sheet
> name = desk; no per-row Desk column). Keywords are case-insensitive (`MANDATORY`, `PTO`).

- [ ] **UPL-01**: Operator can upload a single workbook — **one worksheet per desk, sheet name = desk** — that provisions agents with BambooHR ID, first name, last name, job title, email, department, and active status. Minimum valid row = BambooHR ID + all 7 day cells populated; identity fields are optional and sourced from BambooHR where blank
- [ ] **UPL-02**: The upload parses an unbounded (but finite) number of specialization columns per agent (`Specialty 1`, `Specialty 2`, … `Specialty N`), rather than a fixed `Specialty 1` / `Specialty 2` pair; first non-blank = primary, rest = secondary
- [ ] **UPL-03**: The upload parses the Monday–Sunday day cells per agent; a numeric cell `>= 0` is contracted hours for that day, where `0` marks a day the agent does not work (never blank)
- [x] **UPL-04**: A Monday–Sunday day cell equal to `MANDATORY` marks a mandatory day off for that weekday
- [x] **UPL-05**: A Monday–Sunday day cell equal to `PTO` marks recurring weekly PTO, applied across the schedule horizon as a repeating weekly pattern
- [ ] **UPL-06**: Rows failing validation (blank day cell, or a value that is not a number 0–24 / `MANDATORY` / `PTO`; negatives rejected) are skipped with a specific per-row reason; the Upload Results view shows a per-sheet rollup, per-row skip reasons, unmatched-sheet notices, and clamp warnings (values > 24 clamped to 24, surfaced non-silently); valid rows in the same file still import
- [ ] **UPL-07**: Rows whose BambooHR ID is not found in BambooHR are rejected with reason "BambooHR ID not found" rather than creating an agent (matching is by BambooHR ID only)
- [ ] **UPL-08**: The 6-column legacy shape **and** the previous flat enriched shape (single sheet + Desk column) are both retired; one per-desk enriched shape replaces them, and operators re-download the pre-seeded template once (~~existing enriched sheets continue to import~~ — superseded 2026-07-31)
- [ ] **UPL-09**: Operator can download a blank pre-seeded template — one worksheet per desk, current roster identity filled, schedule (day cells + specialties) left blank; template, upload parser, and export share one column-layout definition (folds the `2026-07-30-blank-upload-template-one-sheet-per-desk` todo)

### Merge & Precedence

- [ ] **MRG-01**: Uploading triggers a fresh BambooHR sync before merging, so the merge always runs against current BambooHR data
- [ ] **MRG-02**: For every field carried by both sources, BambooHR's value is used where BambooHR has data; the spreadsheet value is used only where BambooHR's is absent
- [ ] **MRG-03**: BambooHR's dated PTO takes precedence for the dates it covers; the spreadsheet's recurring weekly PTO pattern applies only to dates with no BambooHR PTO record
- [ ] **MRG-04**: Operator can see a merge report after upload showing, per field, which values came from BambooHR and which the spreadsheet supplied
- [ ] **MRG-05**: The merge report shows which spreadsheet values were overridden by BambooHR, so operators can spot disagreement between the two sources
- [ ] **MRG-06**: An agent whose working pattern is unknown to BambooHR but supplied by the spreadsheet becomes eligible for solving — `workingDaysKnown` resolves true and the agent is no longer filtered out
- [ ] **MRG-07**: If the BambooHR sync fails during upload (e.g. 503 rate limit), the operator gets a clear message and no partial merge is written

### Agent Data Model

- [ ] **MDL-01**: Agent stores first name and last name as separate fields
- [ ] **MDL-02**: Agent stores contracted hours per day of week, replacing the single `contractedHoursPerDay` scalar; `AgentDayConfig` resolves effective hours per date from the per-day values
- [ ] **MDL-03**: Existing agents migrate without data loss — the current scalar contracted hours becomes the per-day value for working days, and the existing single `name` is split into first and last

---

## Future Requirements

- Coverage, utilization, preference-satisfaction and PTO-diagnostic reporting (v1.1 Backlog 999.5)
- Excel/PDF export, solver score breakdown, constraint weight tuning UI (v1.1 Backlog 999.6)
- Weekend-position fairness and day-to-day hours consistency constraints (v1.1 Backlog 999.4)
- Multi-week scheduling horizon
- Agent self-service preference portal
- API authentication / authorization (deferred from v1.0)

---

## Out of Scope

- Creating agents that do not exist in BambooHR — rejected rows instead (UPL-07); contractor/new-starter support is deferred
- Fuzzy matching on name or email — BambooHR ID is always populated, so ID matching is sufficient
- Changing how the solver consumes days off — MANDATORY and APPROVED PTO already block; this milestone changes only what data reaches it
- Editing the merged result in the UI — the spreadsheet and BambooHR remain the only input paths
- BambooHR API key rotation (v1.1 Backlog 999.7) — explicitly kept out to hold milestone focus
- Custom domain / DNS, multi-environment staging, monitoring dashboards — unchanged from v1.0
- Agent-facing views / self-service preferences

---

## Open Risks

- **Fresh-sync-on-upload (MRG-01) couples upload latency to BambooHR availability.** v1.1 shipped 503/429 handling with a human-readable retry message, which MRG-07 builds on, but a large roster sync inside a request may need async handling or a longer timeout.
- **Retiring both the 6-col legacy shape and the old flat enriched shape (UPL-08) is operator-visible.** Every operator must re-download the new pre-seeded per-desk template (UPL-09) before their next upload — no old sheet imports.
- **Phase 10 uses a coexist/union rule, not a merge (Phase 11 boundary).** In Phase 10 a spreadsheet MANDATORY/PTO day cell is *added* to the days-off BambooHR derives from field 4517 — a day is off if either source says so — so the spreadsheet cannot yet turn a BambooHR day off back into a worked day. True per-field precedence (MRG-02) and un-blocking arrive with the Phase 11 merge engine.
- **MDL-02 is the highest-risk change** — `contractedHoursPerDay` feeds the solver through `AgentDayConfig.effectiveHours`, and `AgentException` rows already override it per date. Per-day hours must compose with exceptions without changing existing solve behaviour for agents whose days are uniform.

---

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| UPL-01 | Phase 10 | Pending |
| UPL-02 | Phase 10 | Pending |
| UPL-03 | Phase 10 | Pending |
| UPL-04 | Phase 10 | Complete |
| UPL-05 | Phase 10 | Complete |
| UPL-06 | Phase 10 | Pending |
| UPL-07 | Phase 10 | Pending |
| UPL-08 | Phase 10 | Pending |
| UPL-09 | Phase 10 | Pending |
| MRG-01 | Phase 11 | Pending |
| MRG-02 | Phase 11 | Pending |
| MRG-03 | Phase 11 | Pending |
| MRG-04 | Phase 11 | Pending |
| MRG-05 | Phase 11 | Pending |
| MRG-06 | Phase 11 | Pending |
| MRG-07 | Phase 11 | Pending |
| MDL-01 | Phase 9 | Pending |
| MDL-02 | Phase 9 | Pending |
| MDL-03 | Phase 9 | Pending |
