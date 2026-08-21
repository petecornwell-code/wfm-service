---
phase: 10
slug: enriched-upload-parsing
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
block_on: high
register_authored_at_plan_time: true
created: 2026-08-21
---

# Phase 10 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

Register authored at plan time — all six `10-0*-PLAN.md` files carried a `<threat_model>` block.
This run verified that each `mitigate` disposition has a corresponding control in the implementation.
Per the ASVS L1 short-circuit (`threats_open: 0` + `register_authored_at_plan_time: true` +
`asvs_level == 1`), grep-depth verification is sufficient and no deep auditor pass was required.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| browser → upload API | Operator uploads an untrusted multipart `.xlsx` parsed by Apache POI | Arbitrary binary workbook |
| spreadsheet cell → DB | Cell contents (hours / keywords / identity) become persisted agent state | Operator-controlled strings and numbers |
| BambooHR sync → local DB | Refresh deletes/regenerates dated `AgentDayOff` rows; must not touch spreadsheet-sourced durable state | Third-party HR records |
| DB roster string → generated `.xlsx` | Operator/BambooHR-sourced identity strings written into a downloadable spreadsheet | PII (names, emails, BambooHR IDs) |
| browser → template download API | Tenant-scoped GET; must only expose the caller's tenant desks | Tenant roster |
| API JSON → DOM | Server-supplied skip reasons / warnings (may echo operator BambooHR IDs and names) rendered in the modal | Reflected operator strings |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-10-01 | Tampering (data loss) | `BambooRefreshService` rolling-window delete vs spreadsheet-sourced day-off state | high | mitigate | Day-off flavour stored on `agent_day_hours.day_off_type` (`AgentDayHours.java:42`), which refresh never touches; `BambooRefreshService` has **0** references to `AgentDayHoursRepository` | closed |
| T-10-02 | Tampering | Flyway `V30` schema change | low | accept | `V30__agent_day_hours_recurring_status.sql` is additive + nullable, no backfill, forward-only — no destructive DDL | closed (accepted) |
| T-10-03 | Tampering | Header string drift across parser / template / export | medium | mitigate | Single source of truth: `src/main/java/com/wfm/util/EnrichedColumnLayout.java` | closed |
| T-10-04 | Denial of Service | OOXML zip-bomb / oversized multi-desk workbook via `XSSFWorkbook` parse | high | mitigate | `application.yml:28-29` caps `max-file-size` and `max-request-size` at 10MB before POI opens the stream; POI `ZipSecureFile` defaults left enabled (no `setMinInflateRatio` override anywhere in `src/main/java`) | closed |
| T-10-05 | Tampering / Elevation | Invalid / negative / unknown day-cell content persisted | high | mitigate | Allowlist validation in `DeskAssignmentUploadService.parseDayCell` (`:319`) returning a `DayCellOutcome`; negatives / unknown / blank skip the whole row — allowlist, never a denylist | closed |
| T-10-06 | Spoofing / Integrity | Creating agents that do not exist in BambooHR | high | mitigate | BambooHR-ID-only match against the cache; unmatched rows rejected with `"BambooHR ID not found"` (`DeskAssignmentUploadService.java:362`) and no agent created | closed |
| T-10-07 | Information Disclosure | BambooHR ID / name reflected into skip-reason strings | low | accept | Pre-existing accepted pattern — `SkippedRow` already carried id/name before this phase; no new exposure introduced | closed (accepted) |
| T-10-08 | Tampering | CSV / formula injection via identity strings written into the template `.xlsx` | high | mitigate | `FormulaInjectionSanitizer.sanitize` prefixes leading `=`, `+`, `-`, `@`, tab and CR with a single quote; applied at every template write site (`DeskAssignmentTemplateService.java:137`) | closed |
| T-10-09 | Information Disclosure | Template download leaking another tenant's roster | high | mitigate | `DeskAssignmentTemplateService` resolves `TenantContext.getTenantId()` (`:47`) and reads via `deskRepository.findByTenantId` (`:48`); no cross-tenant query path | closed |
| T-10-10 | Denial of Service | Large workbook generation for many desks | low | accept | Bounded by tenant desk/roster size; existing export precedent returns `byte[]` synchronously without issue | closed (accepted) |
| T-10-11 | Tampering (regression) | Parser validation allowlist / clamp / ID-reject could silently regress | medium | mitigate | Regression suite pins the behaviour: `DeskAssignmentUploadAllowlistTest`, `DeskAssignmentUploadDayCellTest`, `DeskAssignmentUploadMultiSheetTest`, `DeskAssignmentUploadNonSchedulableRejectTest`, `DeskAssignmentUploadRetiredShapeTest`, `DeskAssignmentUploadSpecialtyTest`, `DeskAssignmentTemplateFilterTest`, `DeskAssignmentTemplateServiceTest` | closed |
| T-10-12 | Information Disclosure / XSS | Reflected skip-reason / warning strings rendered in the modal | high | mitigate | Rendered as React text nodes (auto-escaped); `grep -rn "dangerouslySetInnerHTML" frontend/src/` returns **no matches** | closed |
| T-10-13 | Tampering | Operator strings re-exported via CSV download | low | accept | Existing `handleDownloadSkippedCsv` `sanitize()` already guards formula injection on the CSV path; unchanged by this phase | closed (accepted) |
| T-10-SC | Tampering | Package installs | n/a | n/a | No new packages — POI / Flyway / JPA already present (`10-RESEARCH.md` Package Legitimacy Audit: N/A) | closed (n/a) |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above `workflow.security_block_on` (high) count toward `threats_open`*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-10-01 | T-10-02 | `V30` is additive and nullable with no backfill — forward-only and reversible by design; a destructive-migration threat does not apply | Plan author (10-01-PLAN.md) | 2026-07-31 |
| R-10-02 | T-10-07 | Skip reasons echoing BambooHR ID / name is a pre-existing accepted pattern; `SkippedRow` already carried these fields, so the phase introduces no new exposure. Audience is the authenticated operator who uploaded the file | Plan author (10-03-PLAN.md) | 2026-07-31 |
| R-10-03 | T-10-10 | Workbook generation cost is bounded by tenant desk/roster size; the pre-existing export path already returns `byte[]` synchronously at comparable size without issue | Plan author (10-04-PLAN.md) | 2026-07-31 |
| R-10-04 | T-10-13 | CSV re-export already passes through the existing `sanitize()` guard in `handleDownloadSkippedCsv`; this phase does not alter that path | Plan author (10-06-PLAN.md) | 2026-07-31 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-21 | 14 | 14 | 0 | /gsd-secure-phase (orchestrator, ASVS L1 short-circuit) |

**Verification method:** grep-depth control verification against the implementation, per the ASVS L1
short-circuit rule (`threats_open: 0` + `register_authored_at_plan_time: true` + `asvs_level == 1`).
Each `mitigate` disposition was confirmed to have a named control at a specific source location; each
`accept` disposition was carried into the Accepted Risks Log above. No `gsd-security-auditor` deep
pass was required at this level. Full test suite green at audit time: 56 suites, 273 tests, 0 failures.

**If ASVS level is raised to 2 or 3**, this phase must be re-audited with the auditor spawned —
L2 boundary-placement and L3 end-to-end trace checks were deliberately not performed here.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-08-21
