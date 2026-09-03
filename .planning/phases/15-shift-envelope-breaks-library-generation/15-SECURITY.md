---
phase: 15
slug: shift-envelope-breaks-library-generation
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: 2026-09-02
---

# Phase 15 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

**Verdict:** SECURED — 35/35 blocking-tier (critical + high) threats CLOSED; 0 threats OPEN at any severity.
**Register origin:** `register_authored_at_plan_time: true` — all 20 PLAN files carry a `<threat_model>` block, so this audit verified existing mitigations rather than building a register retroactively.
**Block threshold:** `workflow.security_block_on: high` — critical and high severities are blocking; medium and low are not.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| client → `ShiftTemplateController` | Untrusted band offsets, durations and capacities cross here | Template/band geometry |
| client → `ShiftLibraryValidationController` | New desk-scoped read endpoints; must not widen reach beyond sibling endpoints | Coverage + suggestion reads |
| application → database | Multi-tenancy enforced in application code only — no database row-level security, so a repository method without `tenantId` is a cross-tenant read | Tenant-scoped rows |
| migration → running application | V40–V46 DDL never executes in the H2 suite, so a DDL-vs-entity divergence surfaces at first boot rather than in CI | Schema |
| desk library → solver value range | Pairs handed to an agent-day must come only from that agent's own desk | Shift envelopes |
| generated draft → `ShiftTemplateService` | A draft is untrusted input on save and must traverse the normal create/validate path | Draft template rows |
| operator forecast data → solver input construction | Arbitrary demand cells determine how many entities are constructed | Demand forecast |
| operator-set capacity → solve feasibility | Capacity totals are untrusted operator input that can render a solve infeasible | Band capacities |
| live template → accepted history | A routine template correction must not rewrite what history says an agent worked (D-07) | Denormalised accept-time columns |
| shift-mode change → slot-mode scoring/render | Every production desk is currently SLOT; a leak here regresses every live schedule and report | Mode-gated code paths |
| solver output → operator report / export | Scheduling facts cross into a surface operators make staffing decisions from | Schedules, divergence marks |
| reported score → correctness claim | A score is the model's assertion about itself, and this milestone has seen that assertion be confidently wrong | Solver scores, benchmarks |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Source | Status |
|-----------|----------|-----------|----------|-------------|--------|--------|
| T-15-01 | Information Disclosure | `ShiftTemplateBreakBandRepository` | high | mitigate | 15-01 | closed |
| T-15-02 | Tampering | `ShiftTemplateService.validate` band checks | medium | mitigate | 15-01 | closed |
| T-15-03 | Tampering | V40 data fan-out | high | mitigate | 15-01 | closed |
| T-15-04 | Information Disclosure | `CapacityAdvisory` message text | low | accept | 15-01 | closed |
| T-15-05 | Denial of Service | V40 INSERT ... SELECT over `shift_template` | low | accept | 15-01 | closed |
| T-15-06 | Information Disclosure | `GET .../shift-library/suggestion` | high | mitigate | 15-02 | closed |
| T-15-07 | Tampering | `ShiftLibraryGenerationService` | high | mitigate | 15-02 | closed |
| T-15-08 | Denial of Service | candidate enumeration | medium | mitigate | 15-02 | closed |
| T-15-09 | Information Disclosure | uncovered-window `ErrorDetail` text | low | accept | 15-02 | closed |
| T-15-10 | Information Disclosure | `agent_shift_assignment` reads | high | mitigate | 15-03 | closed |
| T-15-11 | Tampering | `ShiftBandPair` value range construction | high | mitigate | 15-03 | closed |
| T-15-12 | Tampering | V41 DDL vs `AgentShiftAssignment` mappings | high | mitigate | 15-03 | closed |
| T-15-13 | Denial of Service | the extra `AgentAssignment × AgentShiftAssignment` join at real scale | medium | mitigate | 15-03 | closed |
| T-15-14 | Repudiation | a `solverConfig.xml` change shipping unvalidated | high | mitigate | 15-03 | closed |
| T-15-15 | Repudiation | a vacuous ENVL-07 pass | high | mitigate | 15-04 | closed |
| T-15-16 | Repudiation | a walker that cannot go red | high | mitigate | 15-04 | closed |
| T-15-17 | Tampering | walker sharing a code path with the constraint | high | mitigate | 15-04 | closed |
| T-15-18 | Information Disclosure | advisory and error text rendered in the page | medium | mitigate | 15-05 | closed |
| T-15-19 | Tampering | a saved draft row bypassing validation | high | mitigate | 15-05 | closed |
| T-15-20 | Spoofing | rendering server-supplied text | low | accept | 15-05 | closed |
| T-15-21 | Tampering | the six mode filters | high | mitigate | 15-06 | closed |
| T-15-22 | Denial of Service | `breakClustering`'s cross-agent aggregation | medium | mitigate | 15-06 | closed |
| T-15-23 | Denial of Service | operator-set capacities producing an infeasible solve | high | mitigate | 15-06 | closed |
| T-15-24 | Tampering | V42 DDL vs `ConstraintWeights` mapping | medium | mitigate | 15-06 | closed |
| T-15-25 | Elevation of Privilege | capacity used to deny a required break | medium | mitigate | 15-06 | closed |
| T-15-26 | Information Disclosure | `AgentShiftAssignmentRepository` reads | high | mitigate | 15-07 | closed |
| T-15-27 | Tampering | a later template edit rewriting accepted history | high | mitigate | 15-07 | closed |
| T-15-28 | Tampering | slot-mode rendering regression | high | mitigate | 15-07 | closed |
| T-15-29 | Information Disclosure | shift descriptor in the detail response | medium | mitigate | 15-07 | closed |
| T-15-30 | Repudiation | a threshold chosen after the numbers | high | mitigate | 15-08 | closed |
| T-15-31 | Repudiation | a noise-level result reported as a win | high | mitigate | 15-08 | closed |
| T-15-32 | Information Disclosure | real-desk data in the indicative run | medium | mitigate | 15-08 | closed |
| T-15-33 | Denial of Service | the benchmark running in the default suite | low | mitigate | 15-08 | closed |
| T-15-09-01 | Denial of Service | `expandMinimumStaffingSeats` SHIFT top-up | medium | mitigate | 15-09 | closed |
| T-15-09-02 | Tampering | SLOT-mode branch of the same method | high | mitigate | 15-09 | closed |
| T-15-09-03 | Denial of Service | empty or fully retired shift library | medium | mitigate | 15-09 | closed |
| T-15-09-04 | Repudiation | XCUT-05 classification table | low | mitigate | 15-09 | closed |
| T-15-10-01 | Repudiation | `ScheduleOutputService.buildAgentSchedule` | high | mitigate | 15-10 | closed |
| T-15-10-02 | Tampering | slot-mode reporting path | high | mitigate | 15-10 | closed |
| T-15-10-03 | Information Disclosure | new divergence component on the response | low | accept | 15-10 | closed |
| T-15-10-04 | Tampering | duplicated coverage arithmetic | medium | mitigate | 15-10 | closed |
| T-15-10-05 | Repudiation | preference-report break KPIs | medium | mitigate | 15-10 | closed |
| T-15-11-01 | Denial of Service | the seat-supply refusal | high | mitigate | 15-11 | closed |
| T-15-11-02 | Denial of Service | slot-mode desks | high | mitigate | 15-11 | closed |
| T-15-11-03 | Tampering | duplicated expected-work-slot arithmetic | high | mitigate | 15-11 | closed |
| T-15-11-04 | Information Disclosure | refusal message content | low | accept | 15-11 | closed |
| T-15-11-05 | Repudiation | an unassigned shift with no explanation | medium | mitigate | 15-11 | closed |
| T-15-12-01 | Repudiation | Agent Schedule Shift column | high | mitigate | 15-12 | closed |
| T-15-12-02 | Tampering | slot-mode Agent Allocation branch | high | mitigate | 15-12 | closed |
| T-15-12-03 | Information Disclosure | divergence marks and tooltips | low | accept | 15-12 | closed |
| T-15-12-04 | Repudiation | deliberately unstaffed hour | medium | mitigate | 15-12 | closed |
| T-15-13-01 | Repudiation | a green suite that cannot fail | high | mitigate | 15-13 | closed |
| T-15-13-02 | Tampering | retained diagnostic tests mistaken for guards | medium | mitigate | 15-13 | closed |
| T-15-13-03 | Repudiation | silently dropped latent defects | medium | mitigate | 15-13 | closed |
| T-15-13-04 | Denial of Service | end-to-end solve runtime in CI | low | mitigate | 15-13 | closed |
| T-15-33 | Information disclosure | `LiveShapeShiftDeskFixture` | low | mitigate | 15-14 | closed |
| T-15-34 | Tampering | `SolverQualityGuardTest` as a CI gate | medium | mitigate | 15-14 | closed |
| T-15-35 | Denial of service | default `./gradlew test` runtime | low | mitigate | 15-14 | closed |
| T-15-36 | Repudiation | `15-BENCHMARK.md` | medium | mitigate | 15-15 | closed |
| T-15-37 | Repudiation | `15-UAT.md` gap entries | medium | mitigate | 15-15 | closed |
| T-15-38 | Information disclosure | red-proof fixtures | low | accept | 15-15 | closed |
| T-15-16-01 | Information disclosure | `GlobalExceptionHandler` 405 handler | medium | mitigate | 15-16 | closed |
| T-15-16-02 | Repudiation | `ScheduleService` `violatedHardConstraints` derivation | high | mitigate | 15-16 | closed |
| T-15-16-03 | Information disclosure | Accepted-path `ViolationDetail` rows | low | accept | 15-16 | closed |
| T-15-16-04 | Denial of service | Accepted-path violation walk | low | accept | 15-16 | closed |
| T-15-17-01 | Denial of service | Demand-ranked offset enumeration in `suggestedBands` | low | mitigate | 15-17 | closed |
| T-15-17-02 | Tampering | Dedupe dropping a load-bearing template | medium | mitigate | 15-17 | closed |
| T-15-17-03 | Repudiation | A draft that solves worse than the hand-built library | medium | mitigate | 15-17 | closed |
| T-15-17-04 | Information disclosure | Suggestion response | low | accept | 15-17 | closed |
| T-15-18-01 | Denial of service | Date-aware supply count (Task 1) | high | mitigate | 15-18 | closed |
| T-15-18-02 | Repudiation | Refusal advice (Task 2) | high | mitigate | 15-18 | closed |
| T-15-18-03 | Information disclosure | Refusal message naming the live constraint weight value | low | accept | 15-18 | closed |
| T-15-18-04 | Tampering | `ShiftBandPair.covers` | medium | mitigate | 15-18 | closed |
| T-15-19-01 | Information disclosure | `SeatSupplyDistributionAnalysisTest` fixture and the analysis document | medium | mitigate | 15-19 | closed |
| T-15-19-02 | Repudiation | The recommendation | high | mitigate | 15-19 | closed |
| T-15-19-03 | Tampering | Accidental production change while "just measuring" | high | mitigate | 15-19 | closed |
| T-15-19-04 | Denial of service | Repeated solves inside the analysis test | medium | mitigate | 15-19 | closed |
| T-15-20-01 | Denial of service | New per-hour / per-agent-day refusal | critical | mitigate | 15-20 | closed |
| T-15-20-02 | Denial of service | Per-agent-day computation cost inside the solve request | medium | mitigate | 15-20 | closed |
| T-15-20-03 | Information disclosure | Per-hour refusal message | low | accept | 15-20 | closed |
| T-15-20-04 | Tampering | Duplicate rule implementations drifting apart | high | mitigate | 15-20 | closed |
| T-15-SC | Tampering | npm/pip/cargo installs | n/a | accept | 15-01..15-08 | closed |

*Status: open · closed · open — below `high` threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above `workflow.security_block_on` count toward `threats_open`*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

**Register note — duplicate ID.** `T-15-33` was assigned twice by two different plans (15-08 "benchmark running in the default suite"; 15-14 "`LiveShapeShiftDeskFixture`"). Both are recorded above and both are closed; the collision is in the source plans, not introduced here. 82 register rows cover 81 unique IDs.

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-15-01 | T-15-04, T-15-09, T-15-11-04, T-15-18-03, T-15-20-03 | Advisory / refusal / error text names only the caller's own tenant data; `TenantContext.getTenantId()` is resolved server-side at the top of every public method and no caller-supplied tenant is ever accepted | Phase 15 plan authors | 2026-09-02 |
| R-15-02 | T-15-10-03, T-15-12-03, T-15-16-03, T-15-17-04 | Divergence components, marks, tooltips and suggestion/violation rows expose only the requesting tenant's own schedule facts | Phase 15 plan authors | 2026-09-02 |
| R-15-03 | T-15-05, T-15-16-04 | Bounded work: V40's fan-out is a single set-based statement over a tens-of-rows table; the accepted-path violation walk is bounded by the persisted snapshot | Phase 15 plan authors | 2026-09-02 |
| R-15-04 | T-15-20 | Server-supplied text is rendered through React's default escaping | Phase 15 plan authors | 2026-09-02 |
| R-15-05 | T-15-38 | Red-proof fixtures are synthetic and carry no real roster data | Phase 15 plan authors | 2026-09-02 |
| R-15-06 | T-15-SC | No packages were installed by plans 15-01..15-08 — see the supply-chain finding below, which supersedes this row for the dependencies actually added later in the phase | Phase 15 plan authors | 2026-09-02 |

---

## Findings Outside the Register

### F-15-01 — Test-scope dependencies added without threat-model coverage (WARNING, non-blocking)

Commit `d5b4169` ("test(15): Postgres-backed test support") added new external code to `build.gradle` after every plan's threat model was authored:

| Artifact | Scope | New surface? |
|---|---|---|
| `org.testcontainers:testcontainers-bom:1.21.4` | `testImplementation` (platform) | Yes |
| `org.testcontainers:junit-jupiter` | `testImplementation` | Yes |
| `org.testcontainers:postgresql` | `testImplementation` | Yes |
| `org.postgresql:postgresql` | `testRuntimeOnly` | No — already a production `runtimeOnly` dependency before this commit |

Why it is recorded rather than ignored:

- No plan's `<threat_model>` covers it; every `T-15-SC` row correctly stated "no packages are installed by this plan" at authoring time.
- No SUMMARY `## Threat Flags` section flags it — the change postdates all three (2026-09-02 18:48).
- Planning documents (`15-CONTEXT.md`, `15-RESEARCH.md`, `15-DISCUSSION-LOG.md`, `15-01-PLAN.md`) had explicitly *declined* Testcontainers. This is a reversal of a documented decision made outside the plan-and-threat-model process.

Why it does not block: the three genuinely-new artifacts are test-scope only and ship in no production artifact; both projects are long-established, widely-audited OSS. No package-legitimacy audit was performed — that remains outstanding.

**Disposition:** accept (test-scope only), with a package-legitimacy check recommended before any future promotion to `implementation` scope.

---

## Audit Depth and Limits

This audit ran at **ASVS L1** (presence/grep depth), with several blocking items verified at L2 depth where mitigation placement mattered.

**Verified with direct code evidence:** all 35 blocking-tier (critical + high) threats, plus these medium/low threats — T-15-02, T-15-05, T-15-24, T-15-32, T-15-36, T-15-10-04, T-15-11-05, T-15-13-02, T-15-13-03, T-15-16-01, T-15-18-04, T-15-19-04, T-15-20-02.

**Assessed by rationale/pattern consistency only, not independently grepped** — all below the `high` block threshold, so none affects the verdict: T-15-08, T-15-13, T-15-18, T-15-20, T-15-22, T-15-25, T-15-29, T-15-09-01, T-15-09-03, T-15-09-04, T-15-10-05, T-15-12-04, T-15-13-04, T-15-17-01, T-15-17-02, T-15-17-03, T-15-19-01, T-15-37.

A future audit at ASVS L2 should close that second list with direct evidence. Notably, T-15-20 (React escaping) was not independently checked for `dangerouslySetInnerHTML`.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-02 | 81 (82 rows) | 81 | 0 | gsd-security-auditor (sonnet), ASVS L1, block_on high |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-09-02
