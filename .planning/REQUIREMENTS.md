# Requirements: WFM Service v1.2 — Unified Agent Provisioning

**Milestone:** v1.2
**Status:** Active
**Created:** 2026-07-29

---

## v1 Requirements

### Upload Format & Parsing

- [ ] **UPL-01**: Operator can upload a single spreadsheet that provisions agents with BambooHR ID, first name, last name, job title, email, department, desk, and active status
- [ ] **UPL-02**: The upload parses an unbounded (but finite) number of specialization columns per agent, rather than a fixed `Specialty 1` / `Specialty 2` pair
- [ ] **UPL-03**: The upload parses Monday–Sunday contracted-hours columns per agent, where `0` or blank marks a day the agent does not work
- [ ] **UPL-04**: The upload parses Monday–Sunday mandatory day-off columns per agent
- [ ] **UPL-05**: The upload parses Monday–Sunday recurring PTO columns per agent, applied across the schedule horizon as a repeating weekly pattern
- [ ] **UPL-06**: Rows failing validation on any new column are skipped with a specific per-row reason shown in the existing Upload Results view, and valid rows in the same file still import
- [ ] **UPL-07**: Rows whose BambooHR ID is not found in BambooHR are rejected with reason "BambooHR ID not found" rather than creating an agent
- [ ] **UPL-08**: The 6-column legacy upload shape is retired; the enriched shape is extended in place and existing enriched sheets continue to import

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
- **Retiring the 6-col legacy shape (UPL-08) is operator-visible.** Anyone still using a legacy sheet must re-export before their next upload.
- **MDL-02 is the highest-risk change** — `contractedHoursPerDay` feeds the solver through `AgentDayConfig.effectiveHours`, and `AgentException` rows already override it per date. Per-day hours must compose with exceptions without changing existing solve behaviour for agents whose days are uniform.

---

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| UPL-01 | Phase 10 | Pending |
| UPL-02 | Phase 10 | Pending |
| UPL-03 | Phase 10 | Pending |
| UPL-04 | Phase 10 | Pending |
| UPL-05 | Phase 10 | Pending |
| UPL-06 | Phase 10 | Pending |
| UPL-07 | Phase 10 | Pending |
| UPL-08 | Phase 10 | Pending |
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
