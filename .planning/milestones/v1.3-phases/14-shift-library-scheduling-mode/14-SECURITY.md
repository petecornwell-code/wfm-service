---
phase: 14
slug: shift-library-scheduling-mode
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: 2026-08-26
---

# Phase 14 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

Register origin: `register_authored_at_plan_time: true` — all six PLAN files carried a parseable
`<threat_model>` block, so this audit **verifies the authored mitigations exist** rather than
building a register retroactively. Per the secure-phase short-circuit rule, ASVS L1 grep-depth
verification is sufficient at `threats_open: 0` with a plan-time register; no deeper L2/L3
boundary-trace pass was required.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| browser → `/api/v1/desks/{deskId}/shift-templates` | Untrusted request body and untrusted `deskId` path variable | Operator-authored template names, times, weekday masks, effective dates |
| browser → `PUT /api/v1/desks/{deskId}/scheduling-mode` | Untrusted `deskId` and mode value | `SchedulingMode` enum value |
| browser → `GET /api/v1/desks/{deskId}/shift-library/validation` | Untrusted `deskId` path variable | Coverage report: windows, weekdays, template names, hour values |
| `X-Tenant-ID` header → `TenantFilter` → `TenantContext` | Tenant identity established here and enforced **in application code only** — no DB row-level security exists in this project | Tenant id (long) |
| application → Postgres (Flyway) | A migration executes with the app's DB role at boot | DDL |
| in-memory solver state → desk table write | Decision read from `InMemoryScheduleStore`, acted on against Postgres; the two are **not** in one transaction | Schedule status |
| server response → React render | Operator-authored names and server messages rendered into the DOM | Text |
| test source set → production solver package | Test code reads production solver types reflectively; must stay one-directional | Class metadata |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-14-01 | Elevation of Privilege | `ShiftTemplateRepository` / `ShiftTemplateService` | high | mitigate | Verified: all four declared methods take `tenantId` (`findByTenantIdAndDeskId`, `findByIdAndTenantIdAndDeskId`, `existsByTenantIdAndDeskIdAndNameAndEffectiveFrom`, `findByTenantIdAndDeskIdAndName`); no bare `findById` declared; `ShiftTemplateController` accepts no tenant parameter (zero `tenantId`/`TenantContext` references) | closed |
| T-14-02 | Tampering | `V39__add_shift_template_and_scheduling_mode.sql` | medium | mitigate | Verified: no migration file deleted from `db/migration`. See **Deviation note 1** — V39 was edited in place after authoring (commit `9a98029`), which is the sanctioned remediation for an unreleased migration | closed |
| T-14-03 | Tampering | `desk.scheduling_mode` default | medium | mitigate | Verified: V39:55 `ADD COLUMN scheduling_mode VARCHAR(10) NOT NULL DEFAULT 'SLOT'`; `Desk.java:30` field initialised to `SchedulingMode.SLOT` | closed |
| T-14-04 | Information Disclosure | `ShiftTemplateResponse` | low | accept | Desk-scoped operator-authored data the requester already owns | closed |
| T-14-05 | Repudiation | shift-template writes | low | accept | No audit-log facility exists for any desk-scoped entity; out of phase scope | closed |
| T-14-06 | Denial of Service | `GET /shift-templates` | low | accept | Unpaginated by design; library sizes single digits to low tens, same order as `Specialization` | closed |
| T-14-07 | Tampering | `ScheduleConstraintProvider` / `solverConfig.xml` | high | mitigate | Verified: `ScheduleConstraintClassification.java` and its test live in `src/test/java/com/wfm/solver/`; production solver package and `solverConfig.xml` untouched | closed |
| T-14-08 | Repudiation | the completeness assertion | medium | mitigate | Both expected sets derived by reflection; no hardcoded count a contributor could silently edit | closed |
| T-14-09 | Information Disclosure | `XCUT-05-constraint-classification.md` | low | accept | Content already present in repository source and planning artifacts | closed |
| T-14-10 | Elevation of Privilege | `ShiftTemplateService.updateShiftTemplate` | high | mitigate | Verified: row loaded via `findByIdAndTenantIdAndDeskId` with tenant from `TenantContext`; no bare `findById` | closed |
| T-14-11 | Tampering | the era invariant | high | mitigate | Verified: `validateIdentityAndNonOverlap` (`ShiftTemplateService.java:207`) is the single shared path, called at `:142` for both create and update via an `excludeId` parameter, so the two entry points cannot drift; overlap test at `:229` | closed |
| T-14-12 | Information Disclosure | grid-misalignment `ErrorDetail` | low | mitigate | Verified: `field` is a DTO component name, `message` names only the desk's increment, `value` is the operator's own submitted time | closed |
| T-14-13 | Denial of Service | same-name non-overlap check | low | accept | Loads only rows sharing the candidate's exact name on one desk | closed |
| T-14-14 | Tampering | absence of a delete endpoint | medium | mitigate | Verified: `ShiftTemplateController` exposes only `@GetMapping`, `@PostMapping`, `@PutMapping("/{id}")` — no `@DeleteMapping` | closed |
| T-14-15 | Elevation of Privilege | `ShiftLibraryValidationService` | high | mitigate | Verified: `tenantId` resolved once from `TenantContext.getTenantId()` (`:67`) and threaded into every read (`:69`, `:72`, `:222`); no method accepts a caller-supplied tenant | closed |
| T-14-16 | Information Disclosure | refusal and advisory messages | low | mitigate | Verified: `advisoryMessage(netHours, weekday)` (`:235-236`) embeds no UUID; `HoursAdvisory.templateId` is a structured field for UI keying, not operator-facing prose. No agent names in any message | closed |
| T-14-17 | Tampering | `findAllLiveByDesk` | high | mitigate | Verified: `StaffingRequirementRepository:72` `@Query` carries `AND sr.scheduleId IS NULL`, keeping the validator off accepted-schedule snapshot rows (MODE-04) | closed |
| T-14-18 | Denial of Service | coverage scan | medium | accept | O(distinct windows × templates); windows bounded by the desk's live period, the same set `SolverService` already loads per solve | closed |
| T-14-19 | Repudiation | advisory vs. blocking distinction | medium | mitigate | Verified: separate DTO lists and separate code paths in `requireShiftModeReady`; advisories never enter the exception details | closed |
| T-14-20 | Elevation of Privilege | `DeskService.switchSchedulingMode` | high | mitigate | Verified: desk loaded via `findByIdAndTenantId(deskId, tenantId)` with tenant from `TenantContext`; cross-tenant id throws `EntityNotFoundException` before any write | closed |
| T-14-21 | Tampering | mode switch vs. in-flight solve (TOCTOU) | medium | mitigate | Residual risk explicitly accepted at plan time. See **Deviation note 2** — a `requireShiftModeReady` DB read now sits between the store read and the save, widening the window beyond the plan's "no I/O between them" claim. Non-blocking at `block_on: high`; the in-flight solve is independently immune because `SolverService.startSolve` loads all facts up front under `readOnly` and the entities detach | closed — below high threshold (non-blocking) |
| T-14-22 | Denial of Service | in-flight solve destruction | high | mitigate | Verified: `switchSchedulingMode` (`DeskService.java:176`+) throws `ConflictException` and contains no `terminateEarly`, `remove` or cancel call. The one `inMemoryScheduleStore.remove` in the file is at `:160`, inside `deleteDesk` (`:127`) — a different method | closed |
| T-14-23 | Repudiation | accepted-schedule integrity across a switch | high | mitigate | Verified: the method writes exactly one column (`desk.setSchedulingMode(target)` then `deskRepository.save(desk)`) and reads nothing for writing | closed |
| T-14-24 | Information Disclosure | refusal details | low | accept | 400 details carry the requester's own desk-scoped window/weekday strings; the 409 message is a fixed sentence naming nothing | closed |
| T-14-25 | Spoofing | mode value in the request body | low | mitigate | Verified: `SchedulingModeRequest` is a record wrapping a typed `SchedulingMode` enum, so out-of-range values fail deserialization into `handleUnreadable` (400) before service code; explicit null check throws `IllegalArgumentException("Scheduling mode is required")` | closed |
| T-14-26 | Tampering | client-side verdicts | high | mitigate | Verified: coverage, hours and era verdicts are read from server responses and rendered only (`ShiftLibrary.tsx:109-130`, `:240`); `:522` comment confirms `eraStatus` comes from the response and is never recomputed | closed |
| T-14-27 | Elevation of Privilege | optimistic toggle | medium | mitigate | Verified: optimistic selection reverts on any error (`ShiftLibrary.tsx:356`); the server remains sole authority on persisted mode | closed |
| T-14-28 | Information Disclosure | rendered error details | low | accept | `details` carries validator strings scoped to the desk the operator is already viewing; no UUIDs or agent names (enforced server-side per T-14-16) | closed |
| T-14-29 | Tampering | cross-site scripting via template name | low | accept | Verified: no `dangerouslySetInnerHTML` anywhere in `ShiftLibrary.tsx`; React escapes interpolated text; the advisory tooltip uses the native `title` attribute, rendered by the OS and never parsed as markup | closed |
| T-14-30 | Denial of Service | validation refetch on every mutation | low | accept | One additional desk-scoped GET per mutation attempt; operator-paced | closed |
| T-14-SC | Tampering | npm/gradle installs | low | accept | Zero packages added to `build.gradle` or `frontend/package.json` across all six plans; no install task exists for the package-legitimacy gate to gate | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Deviation Notes

**1. T-14-02 — V39 was edited in place after authoring.** The plan-time mitigation read
"forward-only: V39 is a new file and no applied migration is edited or deleted." V39 was
subsequently edited by commit `9a98029` (`fix(14): G-14-1 declare valid_weekdays VARCHAR(7) to
match entity mapping`) to close UAT gap G-14-1, where Hibernate `ddl-auto=validate` rejected the
original `CHAR(7)` declaration against the entity's `varchar(7)` mapping. Editing an *unreleased*
migration in place was the remediation the gap's own `missing` list sanctioned. The forward-only
invariant holds in substance: no migration that had been applied to a persistent environment was
altered, and no migration was deleted. UAT test 1 re-verified the edited V39 end-to-end against
live Postgres 18.4 — Flyway applied it to v39 and the application booted under `ddl-auto=validate`.

**2. T-14-21 — TOCTOU window is wider than the plan asserted.** The plan claimed the
`InMemoryScheduleStore` read "sits immediately before `deskRepository.save` … with no I/O between
them." In the shipped `DeskService.switchSchedulingMode`, `shiftLibraryValidationService
.requireShiftModeReady(deskId)` — which performs several DB reads — executes between the store
read and the save on the `SHIFT` path. The window is therefore wider than described. This does not
change the disposition: the risk was already accepted as residual at plan time, it is `medium`
severity and so below the `high` block threshold, and the in-flight solve is independently immune
because `SolverService.startSolve` loads every fact up front under `readOnly` and the entities
detach. Recorded so the divergence between the authored claim and the implementation is on record
rather than silently passed.

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-14-01 | T-14-04 | Response carries only desk-scoped operator-authored data the requester already owns | plan-time disposition | 2026-08-26 |
| R-14-02 | T-14-05 | No audit-log facility exists for any desk-scoped entity; adding one for `ShiftTemplate` alone is out of phase scope | plan-time disposition | 2026-08-26 |
| R-14-03 | T-14-06 | Unpaginated list is by design at expected library sizes, consistent with `Specialization` | plan-time disposition | 2026-08-26 |
| R-14-04 | T-14-09 | Document content already present in repository source | plan-time disposition | 2026-08-26 |
| R-14-05 | T-14-13 | Non-overlap check loads only same-name rows on one desk | plan-time disposition | 2026-08-26 |
| R-14-06 | T-14-18 | Coverage scan is no worse than the existing pre-solve load | plan-time disposition | 2026-08-26 |
| R-14-07 | T-14-21 | TOCTOU narrowing is the mitigation, not a lock; in-flight solve independently immune. Window wider than authored — see Deviation note 2 | plan-time disposition, re-affirmed at audit | 2026-08-26 |
| R-14-08 | T-14-24 | Refusal details are scoped to the requester's own desk; 409 message names nothing | plan-time disposition | 2026-08-26 |
| R-14-09 | T-14-28 | Rendered details scoped to the desk already being viewed | plan-time disposition | 2026-08-26 |
| R-14-10 | T-14-29 | React default escaping plus native `title` attribute; consistent with every existing page | plan-time disposition | 2026-08-26 |
| R-14-11 | T-14-30 | One operator-paced desk-scoped GET per mutation | plan-time disposition | 2026-08-26 |
| R-14-12 | T-14-SC | Zero packages added across all six plans | plan-time disposition | 2026-08-26 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-26 | 31 | 31 | 0 | /gsd-secure-phase (orchestrator, ASVS L1 short-circuit) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-08-26
