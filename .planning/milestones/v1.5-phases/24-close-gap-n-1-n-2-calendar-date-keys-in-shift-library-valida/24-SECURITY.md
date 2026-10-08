---
phase: "24"
slug: "close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-10-08"
---

# Phase 24 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| (none new, 24-01 / 24-02) | Validator and repair/start-mix services re-keyed on business date; they read rows already loaded tenant-scoped. No endpoint, parameter, query or persisted field added. | None new |
| browser → `GET /staffing-requirements` | Two new untrusted query parameters `businessFrom` / `businessTo` | ISO dates; tenant demand rows returned |
| service → database | Two new JPQL queries filtering live demand by stored `business_date` | Tenant-scoped demand rows |
| executor → local verification stack | Live checks seed and read a database; must be the throwaway one | Synthetic seed data only |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-24-01 | Information disclosure | `Window.describe` / validator strings | low | accept | `[calendar …]` suffix shows only a date from the caller's own tenant's demand row, already readable via the staffing-requirements endpoint | closed |
| T-24-02 | Tampering (gate integrity) | `requireShiftModeReady` | low | mitigate | `ShiftLibraryValidationServiceTest#validate_previousBusinessDaysPostMidnightWindow_isNotCreditedToTheCalendarWeekdaysTemplate` asserts false coverage is reported uncovered | closed |
| T-24-03 | Tampering (schedule integrity) | `ScheduleEnvelopeRepairService` / `ScheduleConsistencyRepairService` | medium | mitigate | Diff since `623258f^` touches no rescore / revert / hard-score-compare line in either service; seat-exact repair tests pass; D7 isolation tests added 2026-10-08 | closed |
| T-24-04 | Repudiation (guard honesty) | `BusinessDateJoinGuardTest` / `bday-join-guard.md` | low | mitigate | Allowlist fenced block has 0 entries; liveness proofs present in `BusinessDateJoinGuardTest` | closed |
| T-24-05 | Information disclosure | `findLiveByDeskAndBusinessDateRange[AfterCursor]` | medium | mitigate | `sr.tenantId = :tenantId AND sr.deskId = :deskId` present on the new queries in `StaffingRequirementRepository`; tenant from `TenantContext`; `StaffingRequirementListBusinessRangeTest#businessRange_isTenantScoped` | closed |
| T-24-06 | Tampering (input validation) | `businessFrom` / `businessTo` parsing | medium | mitigate | Named JPQL parameters only; `DateTimeParseException` rethrown as `IllegalArgumentException` (400) naming the param, not echoing input (`StaffingRequirementService:178-179`); half/mixed/inverted ranges refused (:103-113) | closed |
| T-24-07 | Information disclosure | half-supplied business range | low | mitigate | Refused with 400; `halfSuppliedBusinessRange_isRefused` | closed |
| T-24-08 | Denial of service | page size on new branch | low | mitigate | `CursorPagination.clampLimit` and `PageRequest.of(0, clampedLimit + 1)` (`StaffingRequirementService:88-89`) | closed |
| T-24-09 | Information disclosure / safety | live verification | high | mitigate | Throwaway stack only (55432 / 8081 / 3001) in 24-03 and in the 2026-10-08 UAT re-run; teardown verified both times (no listener on those ports, container removed); operator confirmed dev / 5432 / 8080 untouched (UAT test 3) | closed |
| T-24-SC | Tampering (supply chain) | npm / pip / cargo installs | high | mitigate | No change to `build.gradle`, `frontend/package.json` or lockfile since `623258f^` | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-24-01 | T-24-01 | Disclosed date belongs to the caller's own tenant and is already readable by the same caller; no new data class crosses a boundary | Plan 24-01 threat model | 2026-10-07 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-08 | 10 | 10 | 0 | /gsd-secure-phase 24 (orchestrator, ASVS L1 grep-depth; auditor not spawned per short-circuit rule) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-08
