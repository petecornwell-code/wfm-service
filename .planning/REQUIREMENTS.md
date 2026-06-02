# Requirements: WFM Service v1.1 — Schedule Quality & Reporting

**Milestone:** v1.1
**Status:** Active
**Created:** 2026-05-07

---

## v1 Requirements

### Schedule Quality

- [x] **QUAL-01**: Solver ensures every agent receives exactly 2 contiguous days off per week (their "weekend")
- [ ] **QUAL-02**: Solver distributes desirable weekend positions (e.g. Sat/Sun, Fri/Sat) fairly across agents — no agent consistently gets the same popular or unpopular slots
- [ ] **QUAL-03**: Solver applies day-to-day consistency so each agent's scheduled daily hours match their standard contracted daily pattern (no erratic variation within a week)
- [ ] **QUAL-04**: After each solve, operator sees preference satisfaction rate (% of agent shift preferences honoured) on the schedule results page
- [ ] **QUAL-05**: Operator can adjust solver constraint weights and time limit via the UI without code changes

### Reporting

- [ ] **RPT-01**: Operator can view a per-timeslot coverage report showing demand, assigned agent count, and gap (over/under-staffed)
- [ ] **RPT-02**: Operator can view an agent utilization report showing weekly hours per agent with overtime and underutilization flags
- [ ] **RPT-03**: Operator can export the published schedule to Excel (.xlsx) including Coverage and Utilization tabs
- [ ] **RPT-04**: Operator can export the published schedule to PDF
- [ ] **RPT-05**: Operator can view a solver score breakdown showing which constraints fired, violation counts, and affected agents
- [ ] **RPT-06**: Operator can export the solver score breakdown to Excel (.xlsx)

### Diagnostics

- [ ] **DIAG-01**: Operator can view a PTO sync status page showing which agents have BambooHR PTO imported and which failed to sync, with reason (e.g. bamboohrId mismatch)
- [ ] **DIAG-02**: System tracks and displays week-over-week hours variance per agent so operators can identify inconsistent scheduling patterns across weeks

### Data Entry & Integration

- [ ] **DATA-01**: Operator can upload a spreadsheet to bulk-assign BambooHR agents to desks; existing manual per-agent UI assignment remains available
- [ ] **DATA-02**: BambooHR sync pulls full-time vs part-time employment status and stores it on the Agent; operator can see and filter agents by employment type
- [ ] **DATA-03**: BambooHR sync pulls job description/title and stores it on the Agent; operator can mark specific job descriptions as non-schedulable so those agents are excluded from the solver and desk allocation

---

## Future Requirements

- Week-to-week hours consistency (single-week solver cannot enforce cross-week; surface as DIAG-02 metric instead)
- Multi-week scheduling horizon
- Agent self-service preference portal
- API authentication / authorization (deferred from v1.0)

---

## Out of Scope

- Custom domain / DNS — using AWS default CloudFront URL
- Multi-environment staging — single environment only
- Monitoring dashboards / alerting — beyond basic CloudWatch logs
- AWS OIDC GitHub Actions role — blocked by `iam:CreateRole` (PowerUserAccess policy)
- Agent-facing views / self-service preferences

---

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| DATA-01 | Phase 5 | Pending |
| DATA-02 | Phase 5 | Pending |
| DATA-03 | Phase 5 | Pending |
| QUAL-01 | Phase 6 | Complete |
| QUAL-02 | Phase 6 | Pending |
| QUAL-03 | Phase 6 | Pending |
| RPT-01 | Phase 7 | Pending |
| RPT-02 | Phase 7 | Pending |
| QUAL-04 | Phase 7 | Pending |
| DIAG-01 | Phase 7 | Pending |
| DIAG-02 | Phase 7 | Pending |
| RPT-03 | Phase 8 | Pending |
| RPT-04 | Phase 8 | Pending |
| RPT-05 | Phase 8 | Pending |
| RPT-06 | Phase 8 | Pending |
| QUAL-05 | Phase 8 | Pending |
