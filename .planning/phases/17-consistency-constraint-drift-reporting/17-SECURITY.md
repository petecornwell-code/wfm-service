---
phase: "17"
slug: "consistency-constraint-drift-reporting"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-09-18"
register_authored_at_plan_time: true
---

# Phase 17 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.
> Register built from the `<threat_model>` blocks in all five 17-0N-PLAN.md files (State B —
> no prior SECURITY.md existed). All five plans carried a parseable threat model, so this is a
> mitigation-verification run, not retroactive STRIDE.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| HTTP → `TenantFilter` | Every request must carry `X-Tenant-ID`; missing or non-numeric is rejected 400 before any controller runs | Tenant identity |
| Controller → Service | Desk-scoped operations resolve tenant from `TenantContext`, never from the request body | Desk/schedule identifiers |
| Service → Repository | Usual-shift and schedule reads are `findByTenantIdAndDeskId`-scoped from an already-authorized entity | Agent usual shifts, schedule detail |
| Solver → Persistence | The solve path reads usual shifts but must never write them (XCUT-02) | Stored usual-shift targets |
| Backend → Excel export | Schedule detail is serialised into a downloadable workbook | Agent names, dates, drift magnitudes |
| Operator → Constraint weights | Per-desk weight and tolerance values are operator-editable and feed solver behaviour | Constraint weight/tolerance configuration |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-17-01 | Tampering | `SolverService.resolveUsualShiftTargets` | high | mitigate | Solve path is read-only against `AgentUsualShiftRepository`. `SolverUsualShiftWritePathGuardTest` asserts zero mutating interactions behaviourally **and** scans `SolverService` source for any mutating call, with its own comment-stripping test guarding against false negatives. Independently confirmed live this session: a real solve left `agent_usual_shift` byte-identical (md5 unchanged, 50 rows). | closed |
| T-17-02 | Tampering | `ConstraintWeightsService.updateWeights` (hard component) | high | mitigate | D-07 hard-score and D-08 ordering checks run against the fully-merged entity, not the raw partial-update DTO. Rejected, not clamped → 400 `VALIDATION_FAILED` via `GlobalExceptionHandler`. Covered by `ConstraintWeightsServiceTest` (10 tests, green). Transferred from 17-01, owned by 17-02. | closed |
| T-17-03 | Information Disclosure | `ScheduleService.getScheduleDetail` → `ScheduleOutputService.buildDriftReport` | high | mitigate | `buildDriftReport` derives entirely from the already-authorized `Schedule` object and never re-fetches by id (verified: no `scheduleRepository`/`findById` call in `ScheduleOutputService`). **Strengthened during this phase's own code review:** CR-01 (`ea9b6ac`) added the `SchedulingMode.SHIFT` gate, so a slot-scheduled desk no longer emits a drift report at all — confirmed live against a SLOT-solved schedule on a desk holding 50 usual-shift rows (`driftReport: null`, export sheet header-only). | closed |
| T-17-04 | Tampering / Input Validation | `consistencyToleranceMinutes` | medium | mitigate | Server-side rejection of a negative band before persist; the page's `min="0"` attribute is a hint and is not relied on. Covered by `ConstraintWeightsServiceTest`. Below the `high` block threshold. | closed |
| T-17-05 | Information Disclosure | popularity ranking read | high | mitigate | `ScheduleOutputService:460` calls `findByTenantIdAndDeskId(schedule.getTenantId(), schedule.getDeskId())` — tenant and desk both taken from the authorized schedule, never from request input. | closed |
| T-17-06 | Tampering | `V49__set_consistency_weight_defaults.sql` | high | mitigate | Both `UPDATE`s are predicated on the row still holding its prior shipped default, so an operator-tuned per-desk value is never overwritten; column defaults are set separately for new rows. `ConstraintWeightsMigrationTest` round-trips the exact predicate against both a still-default and an operator-tuned fixture row. Migration applied cleanly from an empty database this session (V1→V49, zero errors). | closed |
| T-17-07 | Denial of Service | mis-sized `consistent_start_weight` | high | accept | **Mitigation as stated is not fully achieved** — see Accepted Risks R-17-01. The stated ceiling ("consistency total must stay under the cost of one minimum-staffing violation") is met in the typical case (~240 soft vs 1,000) but breached in the documented worst case (1,120 soft, 12% over). Accepted as documented residual risk. | closed (accepted risk) |
| T-17-08 | Repudiation | benchmark result provenance | medium | mitigate | Threshold and pass rule committed to git ahead of any recorded number (threshold commit precedes harness/result commits), so a result cannot be reinterpreted after the fact; the V49 header cites the run that produced its value. Below the `high` block threshold. | closed |
| T-17-09 | Elevation of Privilege | `ConstraintWeightsController` desk scoping | medium | accept | Unchanged surface — the controller is a passthrough and `ConstraintWeightsService` already resolves tenant from `TenantContext`. Below the `high` block threshold. | closed |
| T-17-10 | Information Disclosure | Excel `Drift Report` sheet | low | accept | The sheet carries agent names and dates already present in the `Preference Report` sheet of the same workbook; no new data class is exposed by the export. | closed |
| T-17-11 | Tampering / XSS | popularity `templateName`, drift `agentName` | medium | mitigate | Both render as plain text children, so React's default escaping applies. Verified: zero `dangerouslySetInnerHTML` in `ScheduleResults.tsx`. Below the `high` block threshold. | closed |
| T-17-12 | Tampering / Input Validation | tolerance-band number input | medium | transfer | The input's minimum-of-zero attribute is a usability hint only; authoritative rejection is T-17-04's server-side check. Transfer documented. | closed |
| T-17-SC | Tampering | npm/pip/cargo installs | low | accept | No package-manager install task exists in this phase — `17-RESEARCH.md` § Package Legitimacy Audit records "not applicable, no external packages are installed by this phase". Declared identically in all five plans. | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above `workflow.security_block_on` (high) count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-17-01 | T-17-07 | The shipped `consistentStartWeight = 2` projects to a worst-case consistency total of 1,120 soft against `minStaffingWeight`'s 1,000-soft ceiling — **12% over**, meaning consistency can outbid minimum staffing in that case, which is the service-availability condition T-17-07 names. Accepted because: (a) no compliant positive-integer weight pair avoids it — weight 1 clears the ceiling (560 soft) but leaves no positive integer below it for a nonzero preference weight, so 2 is the smallest value satisfying D-08's strict ordering with both weights nonzero; (b) the worst case requires the **entire** roster to drift on **every** working day simultaneously, which reflects a data-quality breakdown rather than normal operation; (c) the illustrative typical case (~20% of roster drifting) projects to ~240 soft, comfortably under the ceiling; (d) the overshoot was surfaced and accepted at 17-04's blocking human checkpoint and is disclosed in both `17-BENCHMARK.md` and the V49 migration header. **Monitorable, not eliminated** — `17-BENCHMARK.md` proposes this phase's own drift report as the retroactive source of the real drift-rate telemetry that would replace the projection. | Pete Cornwell (17-04 checkpoint; re-affirmed at `/gsd-secure-phase 17`, 2026-09-18) | 2026-09-18 |
| R-17-02 | T-17-09, T-17-10, T-17-SC | Pre-existing/no-op surfaces accepted at plan time: unchanged controller passthrough, an export sheet exposing no new data class, and a phase that installs no external packages. | Plan-time disposition (17-02, 17-03, all plans) | 2026-09-18 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-18 | 13 | 13 | 0 | `/gsd-secure-phase 17` (orchestrator, ASVS L1) |

**Method.** State B — no prior SECURITY.md. Register built from the `<threat_model>` blocks in all
five plan files (`register_authored_at_plan_time: true`), so this is mitigation verification rather
than retroactive STRIDE. ASVS L1 with `block_on: high`. Evidence for each closure was gathered
against the implementation and the test suite, not from SUMMARY claims: the covering test classes
were executed this session (67 tests, 0 failures) and the full suite is separately green.

**One threat did not classify closed on evidence alone.** T-17-07's stated mitigation names a
numeric ceiling that the shipped weight breaches in the documented worst case. It was presented to
the operator rather than silently closed, and closed via the explicit accepted-risk entry R-17-01.
Had that acceptance not been given, this file would have shipped `threats_open: 1` and blocked
phase advancement.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-09-18
