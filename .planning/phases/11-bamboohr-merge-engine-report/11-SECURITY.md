---
phase: 11
slug: bamboohr-merge-engine-report
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: 2026-08-21
---

# Phase 11 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

Register origin: authored at plan time (`<threat_model>` blocks in 11-01-PLAN.md and
11-02-PLAN.md). Blocking threshold: `high`. ASVS level 1 — grep-depth mitigation
verification.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| operator browser → `POST` desk-assignment upload | Untrusted workbook bytes and untrusted cell values cross here | Spreadsheet bytes, operator-typed cell values |
| BambooHR API → `AgentMergeService.fetchSnapshot` | Untrusted third-party employee and time-off data crosses here, now on every upload rather than only on manual refresh | Employee records, time-off records (PII) |
| tenant → repository layer | Every read and write must be scoped by `tenantId` from `TenantContext` | All persisted agent/desk/PTO data |
| backend → operator browser (`DeskAssignmentUploadResult`) | Agent PII leaves the server in the merge report | Agent names, emails, job titles |
| operator spreadsheet → `Agent.workingDaysSource` | Operator-typed data becomes a persisted marker deciding whether a person is schedulable | Working-days provenance marker |
| `agent_day_hours` + `agent_day_off` → `SolverService.solve` day-off assembly | Two independently-written stores are reconciled; the result decides who may be scheduled on a date | Day-off facts, worked-weekday patterns |
| BambooHR refresh → `Agent.workingDaysKnown` | Third-party data quality can remove a person from the solve | Working-days pattern (field 4517) |
| backend → operator browser (`newlyEligibleAgents`) | Agent PII leaves the server in the eligibility callout | Agent names |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-11-01 | Tampering | `AgentMergeService.fetchSnapshot` / `DeskAssignmentUploadService.uploadDeskAssignments` | high | mitigate | Verified: `fetchSnapshot` at `DeskAssignmentUploadService.java:92` completes before `transactionTemplate.executeWithoutResult` at `:216`; no `@Transactional` on the method; both catches in `AgentMergeService.java:104-112` rethrow. `UploadSyncFailureTest` present. | closed |
| T-11-02 | Information Disclosure | `AgentMergeService.fetchSnapshot` snapshot map and repository calls in the merge path | high | mitigate | Verified: `listEmployees`/`listTimeOff` called with `String.valueOf(tenantId)` (`AgentMergeService.java:84,99`); upload-path repository calls tenant-scoped (`findByTenantId`, `findByTenantIdAndDeskId`, `findByTenantIdAndBamboohrId`, `deleteByTenantIdAndDeskId`). | closed |
| T-11-03 | Information Disclosure | merge-decision logging in `AgentMergeService` | medium | mitigate | Verified: INFO lines carry `bamboohrId` + field label only; raw BambooHR/sheet values at DEBUG (`AgentMergeService.java:146-191`). | closed |
| T-11-04 | Information Disclosure | `MergeReportEntry` payload returned to the browser | medium | mitigate | Verified: entries built only for rows on desks from `deskRepository.findByTenantId(tenantId)` (`DeskAssignmentUploadService.java:112`); agents resolved via `findByTenantIdAndBamboohrId` (`:373`). | closed |
| T-11-05 | Denial of Service | whole-tenant `listEmployees` on every upload | medium | accept | Accepted per D-04; bounded by the explicit BambooHR read timeout. Recorded in Accepted Risks Log (R-11-01). | closed |
| T-11-06 | Information Disclosure | `BambooHRSyncFailedException` message content | medium | mitigate | Verified: `uploadSyncFailureMessage` (`AgentMergeService.java:121-125`) wraps only the upstream reason in fixed operator copy — no API key, subdomain or configuration value. | closed |
| T-11-07 | Elevation of Privilege | `Agent.workingDaysSource` → `SolverService.filterEligible` | high | mitigate | Verified: `filterEligible` (`SolverService.java:1357-1364`) keeps `isActive` → title allowlist → `primarySpecialization != null` in order; only the fourth filter (`isWorkingDaysKnown`) is affected. `WorkingDaysKnownTest` present. | closed |
| T-11-08 | Tampering | `SolverService.arbitratePtoAgainstBambooWindow` | high | mitigate | Verified: non-`PTO` facts pass through untouched; only in-window `PTO` facts are dropped; builds a new list rather than mutating the input; persisted PTO rows untouched (`SolverService.java:1253-1266`). `PtoArbitrationTest` present. | closed |
| T-11-09 | Tampering | `SolverService.unblockSheetWorkedDays` | high | mitigate | Verified: `removeIf` restricted to `DayOffType.MANDATORY` on weekdays the sheet marks worked (`dayOffType == null`); operates on the in-memory list, no repository delete (`SolverService.java:1289-1300`). `SheetPatternUnblockTest` present. | closed |
| T-11-10 | Information Disclosure | `newlyEligibleAgents` payload returned to the browser | medium | mitigate | Verified: same tenant-scoped desk/agent resolution pass as the merge report (see T-11-04). | closed |
| T-11-11 | Information Disclosure | new arbitration and merge logging | medium | mitigate | Verified: arbitration logs counts and desk id at INFO; merge-pattern logging carries agent UUID + field label at INFO; rendered day sets at DEBUG (`AgentMergeService.java:181-191`, `SolverService.java:209`). | closed |
| T-11-12 | Repudiation | in-memory-only PTO arbitration (D-10) | medium | accept | Accepted per D-10/D-13; arbitration is deterministic and re-derived every solve. Recorded in Accepted Risks Log (R-11-02). | closed |
| T-11-13 | Spoofing | BambooHR's absence of a PTO record treated as authoritative | medium | accept | Accepted per D-09; a stale prior refresh is not distinguishable from a genuine absence. Recorded in Accepted Risks Log (R-11-03). | closed |
| T-11-14 | Tampering | `V36` provenance column default | low | mitigate | Verified: `V36__add_agent_working_days_source.sql` declares `NOT NULL DEFAULT 'BAMBOOHR'`, so no existing agent's eligibility changes at deploy time. | closed |
| T-11-SC | Tampering | npm / pip / cargo installs | low | accept | No package-manager install tasks in this phase; Java/Gradle project with a fixed, already-audited dependency set and no new dependency. Recorded in Accepted Risks Log (R-11-04). | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-11-01 | T-11-05 | D-04 locks synchronous fresh sync and explicitly rejects sync reuse; async upload deferred by CONTEXT. The explicit BambooHR read timeout bounds the request instead. Already recorded in REQUIREMENTS.md Open Risks. | operator (D-04) | 2026-08-21 |
| R-11-02 | T-11-12 | D-10 forbids new storage, so no persisted audit exists of which recurring PTO facts a solve suppressed. The arbitration is deterministic and re-derived every solve, so the outcome is reproducible from the inputs. | operator (D-10/D-13) | 2026-08-21 |
| R-11-03 | T-11-13 | D-09 locks "no record inside the window means the agent works". A never-run or incomplete refresh is indistinguishable from a genuine absence. MRG-07's whole-upload abort prevents a failed upload sync from writing, but a stale prior refresh is not detected. Surfaced as minted prohibition and flagged assumption A-02-1 before the one-way door closed. | operator (D-09) | 2026-08-21 |
| R-11-04 | T-11-SC | No package-manager install tasks exist in this phase. RESEARCH.md's Package Legitimacy Audit records N/A: Java/Gradle project, fixed already-audited dependency set, no dependency added. | operator | 2026-08-21 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-21 | 15 | 15 | 0 | /gsd-secure-phase (orchestrator, ASVS L1 grep-depth) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-08-21
