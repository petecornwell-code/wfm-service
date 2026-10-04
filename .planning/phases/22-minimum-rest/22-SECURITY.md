---
phase: "22"
slug: "minimum-rest"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-10-04"
---

# Phase 22 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

Register origin: **authored at plan time.** All twelve of `22-01-PLAN.md` through `22-12-PLAN.md`
carry a parseable `<threat_model>` block, so this is a verification of declared mitigations, not a
retroactive STRIDE reconstruction. ASVS level 1, block threshold `high` — L1 grep-depth
verification is the contracted depth at this level.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| operator browser → `PUT /desks/{deskId}/minimum-rest` | An untrusted integer becomes a solver input for every subsequent solve on the desk; a `null` body means "clear the setting", so an unguarded non-finite parse can silently remove a compliance policy | Desk rest policy (integer minutes, nullable) |
| operator browser → `/desks/{deskId}/agents/{agentId}/rest-waivers` | Untrusted desk id, agent id, date and free-text reason become a row that can make a hard safety constraint stop firing | Waiver date, operator-authored reason (free text) |
| API → database (`desk.minimum_rest_minutes`, `agent_rest_waiver`) | No database row-level security exists; explicit tenant and desk filtering in every query is the only isolation | Tenant-scoped desk and waiver rows |
| database → solver problem facts | Waiver rows and accepted-history spans become solver inputs that can suppress a hard safety constraint; the loading query's scoping is what restricts which rows can do that | Waivers, prior-period rest spans |
| problem facts → constraint stream (in-process) | Predicates run inside `filtering` joiners during score calculation, where a thrown exception aborts the solve rather than returning a usable error, and solver state is heap-only | Spans, waiver lookups |
| accepted prior period → current solve | The only cross-period read in this phase; accepting one period can now change the next period's score or refuse its solve | Prior-period accepted assignments |
| API → operator browser (`/summary`, detail, list) | Operator-authored waiver reasons, agent identifiers and shift instants cross here; the detail payload is ~4 MB and competes with the solver for its two cores | Waiver disclosure rows, refusal strings |
| JPA session boundary (`spring.jpa.open-in-view: false`) | A lazy association crossing out of a repository call on a non-transactional path is an exception, not a value | Fetched `AgentRestWaiver.agent` |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-22-01 | Elevation of Privilege | `AgentRestWaiverRepository` | high | mitigate | Verified: all seven declared finders take `tenantId` AND `deskId` and filter on both; no bare `findById`-style method is declared, and `grep` over `src/main/java` finds no `findById`/`getById`/`getReferenceById`/`findAll()` call on this repository anywhere in production | closed |
| T-22-02 | Tampering | `RestWaiverService.saveWaivers` | high | mitigate | Verified at `RestWaiverService.java:71` — the agent is resolved through `agentRepository.findByIdAndTenantIdAndDeskId`, so an agent id belonging to desk A cannot write a waiver against desk B | closed |
| T-22-03 | Information Disclosure | `DeskController.setMinimumRest` tenant scoping | medium | mitigate | Verified at `DeskService.java:354` — the desk is loaded via `deskRepository.findByIdAndTenantId`, the same tenant-scoped finder `setDayStart` uses | closed |
| T-22-04 | Tampering | `DeskService.setMinimumRest` | medium | mitigate | Verified at `DeskService.java:349-350` — rejects `< 0` and `>= 1440` before any write with "Minimum rest must be less than 24 hours"; `Integer` typing keeps `null` distinct from `0`. Exercised live during UAT: the server refusal is authoritative and the client bound check is advisory only | closed |
| T-22-05 | Repudiation | `agent_rest_waiver.reason` | low | mitigate | Verified at `RestWaiverService.java:79-80` (null/blank refused with `IllegalArgumentException` "reason is required") and `V55__add_minimum_rest.sql:28` (`reason TEXT NOT NULL`). Exercised live during UAT — a blank reason returned 400 and created no row | closed |
| T-22-06 | Denial of Service | `ScheduleConstraintProvider.minimumRestSlot` | medium | mitigate | Verified — the self-join uses indexed `Joiners.equal` positions (`ScheduleConstraintProvider.java:1160-1161`); `forEachUniquePair` appears only in comments explaining its avoidance, never as a call | closed |
| T-22-07 | Tampering | `RestSpan.ofSlots` empty-input handling | low | accept | Accepted — loud internal invariant; see Accepted Risks Log AR-01 | closed |
| T-22-08 | Tampering | client-side out-of-range check in `DeskManagement.tsx` | low | accept | Accepted — convenience, not a control; see AR-02 | closed |
| T-22-09 | Information Disclosure | the Min Rest cell's rendered content | low | accept | Accepted — a number from a desk the caller may already read; see AR-03 | closed |
| T-22-10 | Spoofing | `RestWaiverLookup` via `Schedule.agentRestWaivers` | high | mitigate | Verified — the problem-fact list is populated only from the tenant- and desk-scoped, date-bounded finder (`SolverService.java:249`); the predicate then matches within that already-scoped list and cannot widen it | closed |
| T-22-11 | Denial of Service | the waiver load in `SolverService.startSolve` | medium | mitigate | Verified — one batched desk-wide period-bounded query, with call-count gates asserted in `RestPredecessorServiceTest` (`times(1)`/`never()` at lines 147, 180, 212, 242, 269-273, 323) | closed |
| T-22-12 | Tampering | `RestWaiverLookup.waives` null handling | medium | mitigate | Verified at `RestWaiverLookup.java:43-45` — every argument null-checked, returns `false` rather than throwing, which is also the safe default (an unmatched waiver leaves the hard constraint enforcing) | closed |
| T-22-13 | Denial of Service | `RestPredecessorService.resolvePriorSpans` | high | mitigate | Verified at `RestPredecessorService.java:102-103` — structural `return List.of()` with zero queries when `minimumRestMinutes == null`; at most three queries per solve otherwise, asserted by the interaction-count tests above | closed |
| T-22-14 | Information Disclosure | the two new predecessor repository reads | high | mitigate | Verified — both resolve through `findWithRelationsByTenantIdAndDeskIdAndScheduleIdAndDate`-shaped finders taking tenant, desk AND schedule id; neither accepts a bare id | closed |
| T-22-15 | Tampering | the predecessor's anchor and mode source | medium | mitigate | Verified at `RestPredecessorService.java:132-133` — both read from the predecessor schedule's own snapshotted columns, never the desk's current state; the class javadoc records this as load-bearing | closed |
| T-22-16 | Denial of Service | `RestSpan.gapMinutes` throwing inside scoring | medium | mitigate | Verified — unequal-anchor spans are dropped with a warning on the schedule's shared warnings channel (`RestPredecessorService.java:125`, step 7) before becoming problem facts, so the throw is unreachable from the solver | closed |
| T-22-17 | Information Disclosure | `PreSolveValidationException` message and `ErrorDetail` list | low | accept | Accepted — same exposure the existing seat-supply refusal carries; see AR-04 | closed |
| T-22-18 | Denial of Service | the cross-date product in the SHIFT branch | medium | mitigate | Verified — the method returns structurally before any grouping when the desk has no minimum rest (`SolverService.java:513` guard), the waived-pair check is hoisted ahead of the product, and an empty candidate set short-circuits | closed |
| T-22-19 | Tampering | a false refusal blocking a legitimate solve | medium | mitigate | Verified — `RestFeasibilityRefusalTest` pairs every refusal case with a non-refusal case on an otherwise identical fixture (SHIFT: "two eligible templates … not refused"; SLOT: "best achievable gap is 20h, not refused"; waiver-cleared variants), so a refusal cannot pass on a single positive case | closed |
| T-22-20 | Denial of Service | the disclosure computation on the summary path | high | mitigate | Verified — only two integers ride the summary, and `RestWaiverDisclosureTest:769-774` asserts `buildRestWaiverDisclosure` is called `times(1)` while `buildAgentSchedule`, `buildStaffingSummary`, `buildPreferenceReport`, `buildDriftReport` and `buildConstraintViolations` are all `never()` called. `ScheduleOutputService.java:953-955` returns empty immediately when the snapshotted rest is null | closed |
| T-22-21 | Denial of Service | accepted-path waiver and pre-horizon loads in `loadSnapshotData` | medium | mitigate | Verified — guarded on `schedule.getMinimumRestMinutes() != null` at `ScheduleService.java:206`, `577` and `842`, so an unconfigured desk's accepted schedule issues no extra query | closed |
| T-22-22 | Information Disclosure | operator-authored `reason` text on the response | low | accept | Accepted — same boundary existing operator-authored fields sit behind; see AR-05 | closed |
| T-22-23 | Tampering | reporting the required gap from a live desk value instead of the snapshot | medium | mitigate | Verified — `grep` confirms `ScheduleOutputService` holds no `DeskRepository` reference at all (the only matches are the javadoc lines at 937 and 969 stating the gate); the required gap is read only from `schedule.getMinimumRestMinutes()` at line 953. Confirmed live during UAT: the reported figure was the schedule's snapshot, 10.5h | closed |
| T-22-24 | Tampering | the immediate Add handler | medium | mitigate | Verified **live** during UAT: a genuine browser double-click produced exactly ONE `POST .../rest-waivers` and one row, with the button observed `disabled`/"Adding..." through the request and re-enabled in `finally`. The authoritative backstop `UNIQUE (tenant_id, desk_id, agent_id, date)` is present at `V55__add_minimum_rest.sql:29` and held on 3/3 concurrent-race attempts (row count stayed 1). See Observation OB-01 for the error-handling quality issue found alongside it | closed |
| T-22-25 | Information Disclosure | rendering the waiver `reason` (AgentExceptions) | low | mitigate | Verified — `grep` finds no `dangerouslySetInnerHTML` or `innerHTML` in any touched page, and none anywhere in `frontend/src`; the reason renders as React text content. Confirmed live during UAT with a 229-character reason | closed |
| T-22-26 | Elevation of Privilege | the client's desk and agent path parameters | low | accept | Accepted — the client is not treated as a control; see AR-06 | closed |
| T-22-27 | Information Disclosure | rendering the waiver `reason` and the refusal string (ScheduleResults) | low | mitigate | Verified — same no-escape-hatch grep as T-22-25. Confirmed live during UAT: the 462-character backend refusal string rendered as text, verbatim and untruncated | closed |
| T-22-28 | Denial of Service | the badge on the two-second tick | medium | mitigate | Verified **live** during UAT: on a RUNNING schedule the only polled request was `/summary` at 2035ms and 2024ms intervals; the badge reads two integers already on that response and issued no fetch of its own and no detail fetch | closed |
| T-22-29 | Information Disclosure | the tab's rendered agent names and shift instants | low | accept | Accepted — same boundary every other tab on the page sits behind; see AR-07 | closed |
| T-22-30 | Information Disclosure | `ScheduleDetailResponse` rest fields | low | accept | Accepted — integers derived from rows the payload already carried; see AR-08 | closed |
| T-22-31 | Denial of Service | `getScheduleDetail`'s count derivation | low | mitigate | Verified — both counts are `List.size()` reads on lists the response already holds, and `RestWaiverDisclosureTest:769` asserts `buildRestWaiverDisclosure` runs exactly `times(1)` per request | closed |
| T-22-32 | Tampering | `buildRestWaiverDisclosure`'s anchor binding (WR-01) | low | mitigate | Verified at `ScheduleOutputService.java:971` — a null anchor degrades to `LocalTime.MIDNIGHT` instead of throwing out of `DayWindow.anchoredAt`, so a partially-populated `Schedule` cannot turn a disclosure read into a 500. `MidnightTimeArithmeticGuardTest`'s allowlist gate is present and pins the expression | closed |
| T-22-33 | Information Disclosure | `AgentRestWaiverRepository.findWithAgentByTenantIdAndDeskIdAndDateBetween` | medium | mitigate | Verified — the `@Query` filters `w.tenantId = :tenantId AND w.deskId = :deskId` in its own `WHERE` clause; `RestWaiverPredicateGuardTest` pins which production classes may touch `AgentRestWaiver` | closed |
| T-22-34 | Denial of Service | the new read-path hydration in `listSchedules` | high | mitigate | Verified — all three cost gates carry their own assertion in `RestWaiverDisclosureTest`: no waiver query unless a DB-sourced schedule has non-null snapshotted rest (`never()` at 404-406, 539-541), exactly one desk-wide waiver query per page (`times(1)` at 433, 577), and zero span queries unless a waiver falls inside the schedule's period (`never()` at 512-514) | closed |
| T-22-35 | Tampering | hydration writing into a `Schedule` the in-memory store owns | medium | mitigate | Verified — hydration is applied only to DB-sourced entries, gated on `fromDb` at `ScheduleService.java:842`, and `RestWaiverDisclosureTest.listSchedules_doesNotMutateTheInMemorySchedule` (line 582) asserts the stored instance's collections are still empty afterwards | closed |
| T-22-36 | Tampering | `DeskManagement.tsx` `hoursStringToMinutes` (IN-01) | medium | mitigate | Verified at `DeskManagement.tsx:27-31` (`Number.isFinite(minutes) ? minutes : undefined`) and `:140` (the `!== undefined` check is ordered BEFORE the change comparison, so a non-finite parse skips the request entirely and `null` stays reachable only from a deliberately emptied field). Confirmed live during UAT: a name-only edit fired `PUT /desks/{id}` and no `PUT /minimum-rest` | closed |
| T-22-SC | Tampering | npm/pip/cargo installs | high | mitigate | Not exercised — `git log --name-only` over the phase's commits shows no change to `build.gradle`, `frontend/package.json`, `frontend/package-lock.json` or any other dependency manifest, so no install task existed and no legitimacy checkpoint was required. `22-RESEARCH.md`'s Package Legitimacy Audit stands unamended | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-01 | T-22-07 | `RestSpan.ofSlots` throws on an empty slot list rather than returning a degenerate span. Accepted as a loud internal invariant: the only caller is the `groupBy` node, which never emits an agent-day with zero assignments, and a throw during scoring surfaces immediately rather than producing a silently wrong span | Pete Cornwell (plan 22-02 disposition) | 2026-10-04 |
| AR-02 | T-22-08 | The inline amber out-of-range message in `DeskManagement.tsx` is a convenience, not a control. `DeskService.setMinimumRest` revalidates server-side and refuses regardless of what the client sent, and the UI surfaces the server's own message rather than a second client-authored one, so a bypass produces the authoritative refusal text | Pete Cornwell (plan 22-04 disposition) | 2026-10-04 |
| AR-03 | T-22-09 | The Min Rest cell renders only a number derived from a desk the caller is already authorised to read; no operator-supplied free text and no cross-desk data enters the column | Pete Cornwell (plan 22-04 disposition) | 2026-10-04 |
| AR-04 | T-22-17 | The pre-solve refusal names agent identifiers and shift instants for the desk whose solve the already-authenticated, tenant-scoped caller just requested. No data outside the requested desk and period enters the message — the same exposure `requireShiftEnvelopeSeatSupply`'s own refusal already carries | Pete Cornwell (plan 22-07 disposition) | 2026-10-04 |
| AR-05 | T-22-22 | The operator-authored `reason` is rendered to operators with access to the desk whose schedule they requested — the same boundary exception reasons and template names already sit behind. No new boundary is crossed, and the waiver read is desk-scoped so no cross-desk data enters the disclosure | Pete Cornwell (plan 22-08 disposition) | 2026-10-04 |
| AR-06 | T-22-26 | The page's desk and agent ids come from the route and are sent as path segments, but every endpoint revalidates them server-side through the tenant-scoped three-key agent resolution, so a hand-edited URL reaches a 404 rather than another desk's data. The client is not a control here and is not treated as one | Pete Cornwell (plan 22-09 disposition) | 2026-10-04 |
| AR-07 | T-22-29 | The Rest Waivers tab renders agent names and shift times for the desk whose schedule the operator is already viewing — the same boundary every other tab on the page sits behind. The backend's waiver read is desk-scoped, so no cross-desk data enters the disclosure | Pete Cornwell (plan 22-10 disposition) | 2026-10-04 |
| AR-08 | T-22-30 | `minimumRestMinutes`, `appliedRestWaiverCount` and `unusedRestWaiverCount` describe a desk's own rest policy and that schedule's own waiver counts, returned to a caller already authorised for that desk's full schedule detail via `findByIdAndTenantIdAndDeskId`. The per-waiver rows, including each `reason`, were already carried by `restWaiverDisclosure`; the new fields are integers derived from them and widen nothing | Pete Cornwell (plan 22-11 disposition) | 2026-10-04 |

*Accepted risks do not resurface in future audit runs.*

---

## Observations

Not threats in this register — found while verifying, recorded so they are not lost.

| Obs ID | Near | Finding | Disposition |
|--------|------|---------|-------------|
| OB-01 | T-22-24 | Two **genuinely concurrent** `POST .../rest-waivers` calls for the same `(agent, date)` return one 200 and one unhandled 500 off the `agent_rest_waiver` unique constraint (reproduced 3/3 during UAT). T-22-24's own property holds — no duplicate row was ever created, the row count stayed at 1 every time, and a genuine double-click is correctly guarded by the disable-on-click window. A SEQUENTIAL duplicate is already a clean idempotent upsert. The defect is error-handling quality, not an integrity or authorisation failure, so it does not reopen T-22-24 | Promoted to ROADMAP Phase 999.10; recorded in `22-UAT.md` Deferred Follow-Ups |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-04 | 37 (36 numbered + 1 reserved supply-chain) | 37 | 0 | Claude Opus 5, L1 verification per the `threats_open: 0` + plan-time-register + ASVS-1 short-circuit (no auditor subagent spawned) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-04
