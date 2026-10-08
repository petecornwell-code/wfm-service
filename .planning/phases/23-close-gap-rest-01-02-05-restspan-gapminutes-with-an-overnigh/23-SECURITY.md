---
phase: "23"
slug: "close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-10-08"
---

# Phase 23 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| (none new, 23-01) | In-process integer rest arithmetic (`RestSpan.gapMinutes`, `DayWindow.anchoredWrappedEndMinute`, `SolverService.requireRestFeasibility`) inside the already-authenticated, tenant-scoped solve request. No endpoint, body, query, column or DTO field added. | None new |
| persisted ACCEPTED schedule rows → rest arithmetic (pre-existing) | `RestPredecessorService` reads prior-date assignment rows, already scoped by `tenant_id`/`desk_id`; not widened by this phase. | Tenant-scoped shift times |
| (none, 23-02) | Test-only changes under `src/test/java`. | None |
| build-time source scan → build outcome (23-03) | `RestGapArithmeticGuardTest` reads this repository's own `src/main/java` and a test resource; output is a test verdict only. | Repository source (build output only) |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-23-01 | Tampering | `RestSpan.gapMinutes` / `DayWindow.anchoredWrappedEndMinute` | low | accept | Correctness/compliance defect with no attacker in the loop; closure is the functional fix (60-not-1500), see Accepted Risks | closed |
| T-23-02 | Denial of Service | `SolverService.requireRestFeasibility` SLOT pre-horizon branch | low | accept | Refusal is an operator-visible 400 on the operator's own desk, bounded by horizon agent-days; see Accepted Risks | closed |
| T-23-03 | Tampering | six rest test classes' fixtures | low | accept | Fixtures name pre-fix-wrong numbers; `RestGapArithmeticGuardTest` backstops regression; see Accepted Risks | closed |
| T-23-04 | Tampering | `src/test/resources/rest-gap-arithmetic-guard.md` allowlist | low | mitigate | `RestGapArithmeticGuardTest.java:119` asserts `containsExactlyInAnyOrderElementsOf` (bidirectional); `deliberatelyBrokenAllowlist_isDetectedAsAMismatch` at `:204` proves it can go red; `isSubsetOf`/`containsAnyOf` absent (0 matches) | closed |
| T-23-05 | Information Disclosure | `RestGapArithmeticGuardTest` failure messages | low | accept | Prints repository source lines to build output only; see Accepted Risks | closed |
| T-23-SC | Tampering | npm/pip/cargo installs | high | mitigate | No install exists: none of the 54 files touched by phase-23 commits is `build.gradle`, `settings.gradle` or a `package*.json`; no `frontend/` files touched | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-23-01 | T-23-01 | Inputs are already-stored, already-validated shift times; no new input surface. A mis-scored roster is a functional/compliance failure, closed by the corrected arithmetic and its fixtures, not a security control. | 23-01 plan threat model | 2026-10-08 |
| AR-23-02 | T-23-02 | A negative `bestGap` refusing an agent-day is the intended outcome, surfaced as a 400 to the authenticated operator on their own desk. Deliberately not mitigated by clamping (REST-05 prohibition). | 23-01 plan threat model | 2026-10-08 |
| AR-23-03 | T-23-03 | No access-control dimension: whoever can write a fixture can change any assertion. Counterweighted by pre-fix-wrong assertion values and the 23-03 build guard. | 23-02 plan threat model | 2026-10-08 |
| AR-23-05 | T-23-05 | Disclosure reaches build output only; content is this repository's own source. Naming the offending line is the guard's purpose. | 23-03 plan threat model | 2026-10-08 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-08 | 6 | 6 | 0 | /gsd-secure-phase 23 (L1 orchestrator check; auditor skipped per short-circuit: threats_open 0, register authored at plan time, ASVS 1) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-08
