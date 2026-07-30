---
phase: 9
slug: agent-data-model-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-30
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ (existing project convention) |
| **Config file** | `src/test/resources/application-test.yml` (H2, `ddl-auto: create-drop`, **`flyway.enabled: false`**) |
| **Quick run command** | `./gradlew test --tests "com.wfm.service.SolverService*Test" --tests "com.wfm.model.Agent*Test" --tests "com.wfm.util.AgentNameSplitterTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~quick: seconds · full: minutes |

**Critical caveat (RESEARCH Pitfall 4):** `application-test.yml` sets `flyway.enabled: false`, so `./gradlew test` **never executes the V29 migration SQL**. MDL-03 migration data-integrity cannot be validated by the automated suite alone — see Manual-Only Verifications.

---

## Sampling Rate

- **After every task commit:** Run the quick run command above
- **After every plan wave:** Run `./gradlew test` (full suite)
- **Before `/gsd-verify-work`:** Full suite green **plus** the manual migration dry-run must pass
- **Max feedback latency:** < 120 seconds for the quick command

---

## Per-Task Verification Map

| Task ID | Requirement | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|-------------|-----------------|-----------|-------------------|-------------|--------|
| Name split rule (D-06) | MDL-01 | N/A | unit | `./gradlew test --tests "com.wfm.util.AgentNameSplitterTest"` | ❌ W0 | ⬜ pending |
| firstName/lastName persist+reload | MDL-01 | tenant scoping preserved | unit (`@DataJpaTest`) | `./gradlew test --tests "com.wfm.model.AgentNamePersistenceTest"` | ❌ W0 | ⬜ pending |
| Resolution precedence exception>per-day>default (D-03) | MDL-02 | N/A | unit | `./gradlew test --tests "com.wfm.service.SolverServiceEffectiveHoursResolutionTest"` | ❌ W0 | ⬜ pending |
| Per-day `0.00` → not scheduled that weekday (D-04/D-05) | MDL-02 | N/A | unit | same class as above | ❌ W0 | ⬜ pending |
| `AgentDayHours` persist+reload (STRING enum, 5,2) | MDL-02 | `tenant_id` scoped | unit (`@DataJpaTest`) | `./gradlew test --tests "com.wfm.model.AgentDayHoursPersistenceTest"` | ❌ W0 | ⬜ pending |
| Behaviour-equivalence: uniform-hours agent identical pre/post | MDL-03 | N/A | unit (pure resolver) | same class as resolver test | ❌ W0 | ⬜ pending |
| setContractedHours fan-out to 7 rows (D-10) | MDL-02 | tenant scoped | unit | `./gradlew test --tests "com.wfm.service.DeskAgentService*Test"` | ❌ W0 | ⬜ pending |
| Flyway V29 row counts/values vs real data | MDL-03 | N/A | manual / `checkpoint:human-verify` | see Manual-Only | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `AgentNameSplitterTest` — data-driven test of D-06's rule (single token, "First Last", "Mary Jane Watson", leading/trailing/double whitespace) — MDL-01
- [ ] `AgentNamePersistenceTest` — `@DataJpaTest` mirroring `AgentEmploymentTypePersistenceTest` — MDL-01
- [ ] `SolverServiceEffectiveHoursResolutionTest` — extracted static resolver, mirroring `SolverServicePtoFilterTest` — MDL-02 + MDL-03 equivalence bar
- [ ] `AgentDayHoursPersistenceTest` — `@DataJpaTest` for the new entity (STRING enum round-trip, precision/scale, tenant scoping) — MDL-02
- [ ] Migration dry-run checklist (manual against seeded Postgres) — MDL-03 data-integrity bar; no existing Testcontainers/Flyway harness to reuse

*JUnit5/AssertJ/`@DataJpaTest` are already fully wired — no framework install needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| V29 migration produces correct rows/values | MDL-03 | `flyway.enabled: false` in test profile; no Testcontainers in repo — the actual SQL is never run by `./gradlew test` | Run `flyway migrate` against a Postgres seeded with representative agents (non-null scalar, null scalar, single-token name, multi-word name). Assert: `agent_day_hours` row count == `7 × count(agents with non-null scalar)`; NULL-scalar agents have zero rows; sample multi-word names split correctly. Cross-check the SQL split expression output against `AgentNameSplitter` on the same inputs (RESEARCH Pitfall 5). |

---

## Validation Sign-Off

- [ ] All tasks have automated verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Migration data-integrity gated behind manual `checkpoint:human-verify` before verify-work
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
