---
phase: 13
slug: per-day-hours-visibility
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
block_on: high
created: 2026-08-25
register_authored_at_plan_time: true
---

# Phase 13 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

**Register origin:** authored at plan time. All six of `13-01-PLAN.md` … `13-06-PLAN.md` carry a
parseable `<threat_model>` block, so this audit **verified existing mitigations** rather than
reconstructing a register retroactively. No `## Threat Flags` section appears in any SUMMARY.md
(`grep -l "Threat Flags" 13-0*-SUMMARY.md` → empty), so nothing was raised during execution that the
plan-time register did not already anticipate.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| browser → `GET /api/v1/desks/{deskId}/agents` | Untrusted `deskId` path variable and `X-Tenant-ID` header | Roster payload incl. per-day contracted hours |
| browser → `PUT .../agents/{agentId}/day-hours/{day}` | Untrusted `deskId`, `agentId`, `day` segments + untrusted JSON body | Single weekday hours value or day-off label |
| browser → `PUT .../agents/{agentId}/contracted-hours` | Client-composed numeric body that fans out to seven rows | Bulk contracted-hours overwrite |
| service → `agent_day_hours` / `schedule` tables | `AgentDayHoursRepository` finders are documented as NOT self-tenant-scoping (`AgentDayHoursRepository.java:33-35`) | Cross-tenant row exposure risk |
| service → generated `.xlsx` reopened in Excel | Operator-supplied strings re-emitted into a file a spreadsheet will interpret | Formula-injection surface |
| application internals → HTTP error body | Exception state crossing back out via `GlobalExceptionHandler` | Parameter names, rejected tokens, type names |
| server-resolved schedule default → rendered roster cell | System-derived value shown in a surface an operator reads as their own uploaded data | Provenance / spoofing surface |

---

## Threat Register

All 29 threats verified 2026-08-25. **Status legend:** `closed (verified)` = control located in code and
exercised; `closed (accepted)` = documented risk, no control expected.

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-13-01 | Information Disclosure | `DeskAgentService.loadDayHoursByAgent` | high | mitigate | `:70` calls only `findByTenantIdAndDeskId(tenantId, deskId)`; `tenantId` from `TenantContext.getTenantId()` (`:79`), invoked at `:94` | closed (verified) |
| T-13-02 | Information Disclosure | `DeskAgentService.resolveScheduleDefault` | medium | mitigate | `:58-60` uses tenant-scoped `findByTenantIdAndDeskIdOrderByCreatedAtDesc` (`ScheduleRepository.java:17`); no `findAll` anywhere in the service | closed (verified) |
| T-13-03 | Information Disclosure | `DeskAgentResponse.dayHours` payload | low | accept | Populated exclusively from the tenant-scoped read path (T-13-01/02); no new subject-data class | closed (accepted) |
| T-13-04 | Tampering | `DeskAgentResponse.contractedHoursPerDay` echo | low | accept | Scalar echoed at `:132` for back-compat but excluded from resolution; pinned by passing `rosterIgnoresScalar_whenScalarDisagreesWithPerDayRows` (`DeskAgentServiceReadPathTest.java:118-133`) | closed (accepted) |
| T-13-05 | Elevation of Privilege | `DeskAgentService.setDayHours` | high | mitigate | `:298-299` resolves agent via `findByIdAndTenantIdAndDeskId(...).orElseThrow` **before** the first `AgentDayHoursRepository` call at `:303`; `setDayHours_foreignTenantAgent_throwsEntityNotFound` (`DeskAgentServiceDayHoursTest.java:214-224`) passing | closed (verified) |
| T-13-06 | DoS / Info Disclosure | `setDayHours` hours validation | medium | mitigate | `:309-314` throws `IllegalArgumentException`; `GlobalExceptionHandler.java:30-33` maps to 400 (not a stack-leaking 500). `setDayHours_negative_…` (`:167`) and `setDayHours_above24_…` (`:177`) passing | closed (verified) |
| T-13-07 | Tampering | `SetDayHoursRequest.dayOffType` binding | low | mitigate | Plain enum field, no custom deserializer; Jackson throws on unrecognized literal → `handleHttpMessageNotReadable` (`GlobalExceptionHandler.java:44-47`) → clean 400 | closed (verified) |
| T-13-08 | Tampering | duplicate row for one weekday | low | mitigate | `AgentDayHours.java:8-10` `@UniqueConstraint(agent_id, day_of_week)`; `upsertDayHoursRow` (`:335-343`) reuses the existing row via `findByAgent_IdAndDayOfWeek` | closed (verified) |
| T-13-09 | Repudiation | per-cell edits not audited | low | accept | No per-entity audit trail exists anywhere in this codebase; consistent, out of scope | closed (accepted) |
| T-13-10 | Tampering | new Mon-Sun export cells | low | accept | `DeskAgentExportService.java:111-114` writes enum-derived `"MANDATORY"`/`"PTO"` via `setCellValue(String)`, bypassing `sanitize()` — never operator input. Javadoc `:89-101` states this | closed (accepted) |
| T-13-11 | Tampering | column-index shift in `exportDeskAgentsToExcel` | medium | mitigate | Every operator-controlled string column still routed through `sanitize()` post-shift (`:61-64,67-69,73-74`; names now at 20/21). `identityColumnsStillSanitized` (`DeskAgentExportServiceTest.java:228-240`) asserts a formula-prefixed name at shifted index 20 is neutralized — passing | closed (verified) |
| T-13-12 | Information Disclosure | export payload gains per-day hours | low | accept | Flows from the same tenant-scoped `listDeskAgentResponses` read path; no new subject-data class | closed (accepted) |
| T-13-13 | DoS | `specialtyHeader` index growth | low | accept | Called only with literals 1 and 2 (`DeskAssignmentTemplateService.java:119-120`). Read-side bound: `EnrichedColumnLayout.java:40-41` regex `\d{1,9}` + `NumberFormatException` guard `:74-81` | closed (accepted) |
| T-13-14 | Tampering | client-side range validation on the combo | medium | mitigate | `DeskAgents.tsx:323-331` is UX-only; authoritative bound server-side (`DeskAgentService.java:309-314`) with the T-13-06 tests | closed (verified) |
| T-13-15 | Tampering | operator-typed text reaching a rendered cell | low | mitigate | No `dangerouslySetInnerHTML` in `DeskAgents.tsx` (grep: zero matches); `saveDayHours` (`:304-347`) resolves all typed text to `{hours}`/`{dayOffType}`/`{clearRow}` before any request | closed (verified) |
| T-13-16 | Repudiation | bulk overwrite of MANDATORY/PTO labels | medium | mitigate | `DeskAgents.tsx:269-273` computes `labelledDayCount` from **server-supplied** `dayOffType`, so it cannot understate the loss; `confirm()` fires only when count > 0 | closed (verified) |
| T-13-17 | DoS | rapid repeated cell edits | low | accept | `savingCell` in-flight flag disables the input during a PUT (`:614`); no app-wide rate limiting exists — consistent with accept | closed (accepted) |
| T-13-18 | Tampering | client-side bulk range guard in `saveHours` | medium | mitigate | `DeskAgents.tsx:265-268` UX-only; authoritative rejection at `DeskAgentService.java:247-250`, `setContractedHours_above24_isRejectedAndPersistsNothing` passing | closed (verified) |
| T-13-19 | Repudiation | ordering of range check vs destructive `confirm()` | medium | mitigate | Range test `:265-268` runs strictly before `labelledDayCount`/`confirm()` `:269-272` — confirmed by line order, so a rejected value can never raise a dialog the operator approves and believes was applied | closed (verified) |
| T-13-20 | Spoofing | resolved schedule default rendered as operator-supplied | medium | mitigate | `DeskAgents.tsx:554-561` applies `isEveryDayNotSet(da)` styling (`#9ca3af`, italic, `title="Not set — using schedule default"`) identical to `DayCell`'s not-set branch (`:86-94`). **Also confirmed visually** — UAT test 1, both branches side by side | closed (verified) |
| T-13-21 | Information Disclosure | the not-set tooltip | low | accept | Fixed literal at `:89,558` — no agent, tenant or schedule data embedded | closed (accepted) |
| T-13-22 | Tampering | operator-typed text reaching a rendered cell | low | mitigate | Same file and evidence as T-13-15 | closed (verified) |
| T-13-23 | DoS | `setContractedHours` unbounded hours value | medium | mitigate | `:247-250` rejects before any write, so 999.99 never reaches the `NUMERIC(5,2)` column; `setContractedHours_above24_isRejectedAndPersistsNothing` passing | closed (verified) |
| T-13-24 | Tampering | rejected bulk edit leaving partial state | high | mitigate | Guard `:247-250` sits strictly before `setContractedHoursPerDay` (`:251`) and `deleteByAgent_Id` (`:259`). `setContractedHours_above24_leavesExistingRowsAndLabelsUntouched` (`DeskAgentServiceContractedHoursTest.java:228-254`) **and** the mid-loop failure-injection test `setContractedHours_failureOnTheFourthOfSevenRowWrites_persistsNothing` both passing — zero partial writes | closed (verified) |
| T-13-25 | Information Disclosure | `MethodArgumentTypeMismatchException` 400 body | medium | mitigate | `GlobalExceptionHandler.java:49-59` builds the message from `ex.getName()` only; no `getValue()`/`getRequiredType()` call anywhere in the file. `handleTypeMismatch_returns400WithParameterNameOnly` asserts `"notaday"` and `"DayOfWeek"` are absent — passing. **Also proven over real HTTP** by UAT test 4 | closed (verified) |
| T-13-26 | Tampering | reflected attacker-supplied path segment | medium | mitigate | Same control as T-13-25 — the segment is never echoed, so no reflection primitive and no log-injection vector | closed (verified) |
| T-13-27 | Elevation of Privilege | broadening the advice class | medium | mitigate | `git show 534e9ed` confirms `handleTypeMismatch` was a **pure insertion**; `handleUncaught` (`:102-107`) byte-identical to its pre-phase form. `preExistingMappings_stillReturnTheirOriginalStatuses` passing | closed (verified) |
| T-13-28 | DoS | test-only spy / fixtures leaking into other suites | low | mitigate | `DeskAgentServiceBulkRollbackTest` has its own `@DataJpaTest` context; `@MockitoSpyBean` auto-resets; explicit `@AfterEach` (`:83-97`) deletes fixtures in dependency order (day-hours → agent → desk) | closed (verified) |
| T-13-29 | Repudiation | concurrent same-key insert surfacing as opaque 500 | low | accept | No `DataIntegrityViolationException` handler in `GlobalExceptionHandler.java` (full-file read) — matches the documented accept; race recorded, not fixed | closed (accepted) |

*Status: open · closed · open — below `high` threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above `workflow.security_block_on` count toward `threats_open`*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

**Blocking threshold check:** three threats are rated `high` — T-13-01, T-13-05, T-13-24. All three are
`mitigate` and all three are verified closed in code with passing regression tests. No unmitigated item
sits at or above `block_on: high`.

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-13-01 | T-13-03, T-13-12 | New `dayHours` field and export columns expose only the requesting tenant's own contracted-hours data for agents already returned by the existing endpoint. No new subject data class. | plan-time (13-01, 13-03) | 2026-08-25 |
| R-13-02 | T-13-04 | Retired `contractedHoursPerDay` scalar still echoed for backward compatibility; proven excluded from every resolution path by an invariant test. | plan-time (13-01) | 2026-08-25 |
| R-13-03 | T-13-09 | Per-cell edits are not audited. This project has no per-entity audit trail today; adding one is out of phase scope and no requirement asks for it. | plan-time (13-02) | 2026-08-25 |
| R-13-04 | T-13-10 | The seven new export cells carry only `BigDecimal`-derived numerics and two class-produced enum keywords, never operator-supplied strings, so `FormulaInjectionSanitizer` does not apply to them. | plan-time (13-03) | 2026-08-25 |
| R-13-05 | T-13-13 | `specialtyHeader` is called only with the literals 1 and 2 by production code; the bounded 1-to-9-digit regex and its `NumberFormatException` guard remain the read-side protection. | plan-time (13-03) | 2026-08-25 |
| R-13-06 | T-13-17 | Rapid repeated cell edits are unthrottled. Each edit is a single small row write already guarded by the in-flight disable; no rate limiting exists anywhere in this application today. | plan-time (13-04) | 2026-08-25 |
| R-13-07 | T-13-21 | The not-set tooltip is a fixed literal containing no agent, tenant or schedule data; the numeric default it accompanies is already rendered in the same cell. | plan-time (13-05) | 2026-08-25 |
| R-13-08 | T-13-29 | A concurrent same-key insert surfaces as an opaque 500. Recorded, not fixed — `13-02-PLAN.md` accepts this race as out of scope and no `DataIntegrityViolationException` handler was added (P-19). | plan-time (13-06) | 2026-08-25 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-25 | 29 | 29 | 0 | gsd-security-auditor (sonnet), via /gsd-verify-work 13 → verify:post |

**Audit depth.** ASVS L1 was the configured level, but verification exceeded L1 grep depth:

- The named regression tests were **executed**, not merely located — `./gradlew test` across
  `DeskAgentServiceReadPathTest`, `DeskAgentServiceDayHoursTest`, `DeskAgentServiceContractedHoursTest`,
  `DeskAgentServiceBulkRollbackTest`, `GlobalExceptionHandlerTest`, `DeskAgentExportServiceTest`,
  `DeskAssignmentTemplate*` → BUILD SUCCESSFUL.
- `npm --prefix frontend run build` → exit 0.
- T-13-27's "byte-identical `handleUncaught`" claim was checked against `git show 534e9ed` rather than
  accepted from the plan's prose.
- The orchestrator supplied 8 preliminary grep-level findings as **hypotheses**; the auditor
  independently re-derived all 8 from source rather than accepting them on trust.

This depth was deliberate. This phase's own UAT twice caught phase documentation asserting things that
were false but unobservable (the E1 "at most 5 characters" claim, actually 10; the E4 "the datalist
popup is not constrained by the cell width" claim, exactly backwards). A mitigation plan's prose is a
claim, not evidence, and was treated as such throughout.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-08-25
