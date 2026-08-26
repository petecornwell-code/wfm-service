---
phase: 15
slug: shift-envelope-breaks-library-generation
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-26
---

# Phase 15 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Seeded from `15-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (`useJUnitPlatform()`, `build.gradle:49`) + `ai.timefold.solver:timefold-solver-test` `ConstraintVerifier` (`build.gradle:43-44`) |
| **Config file** | `build.gradle` — no separate JUnit platform config |
| **Quick run command** | `./gradlew test --tests "com.wfm.solver.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | quick ~30s · full suite ~3–5 min (402 tests green at Phase 14 close) |

**Frontend:** no test framework exists in `frontend/package.json` — a standing project decision
(Phase 13: "no frontend test framework introduced"). ENVL-10's Agent Allocation grouping and
SHLB-07's suggestion panel are therefore **manual/UAT-verified**, consistent with every prior
frontend phase here.

**Standing rule that overrides the quick command:** any change to `solverConfig.xml` or
`ScheduleConstraintProvider.java` requires the **full** suite, not the solver-package scope — no
test under `src/test/java/com/wfm/solver/` loads the Spring context, so a scoped run cannot catch a
solver-config regression (Phase 12 lesson, carried as XCUT-03).

---

## Sampling Rate

- **After every task commit:** `./gradlew test --tests "com.wfm.solver.*"`
- **After every plan wave:** `./gradlew test` (mandatory for solver-config / constraint-provider changes)
- **Before `/gsd-verify-work`:** full suite green, **and** the seeded benchmark executed
  (`-Dwfm.benchmark=true`) with its verdict recorded in `15-BENCHMARK.md` before any piloting
  recommendation (CONTEXT.md D-13)
- **Max feedback latency:** ~30 seconds on the quick command

---

## Per-Task Verification Map

*Populated by plan-phase 2026-08-26 as the eight plans were authored. Task IDs are
`15-{plan}-T{task}`. Requirement → test-type mapping below is the contract each task must satisfy.*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 15-01-T1 | 15-01 | 1 | ENVL-08 | T-15-01, T-15-02 | Band repository methods all take `tenantId`; band inputs validated before persistence | integration (service) | `./gradlew test` | ❌ W0 | ⬜ pending |
| 15-01-T2 | 15-01 | 1 | ENVL-08, SHLB-07 | — | — | unit (service) | `./gradlew test --tests "com.wfm.service.ShiftLibraryValidationServiceTest"` | ❌ W0 | ⬜ pending |
| 15-01-T3 | 15-01 | 1 | ENVL-08 | T-15-03, T-15-04 | Migration DDL reconciled against entity mappings; advisory text tenant-scoped | unit + migration guard | `./gradlew test --tests "com.wfm.migration.MigrationEntityConsistencyTest"` | ❌ W0 | ⬜ pending |
| 15-02-T1 | 15-02 | 2 | SHLB-07 | T-15-06, T-15-07 | Endpoint desk-scoped and tenant-resolved; service performs no write | integration (service) | `./gradlew test --tests "com.wfm.service.ShiftLibraryGenerationServiceTest"` | ❌ W0 | ⬜ pending |
| 15-02-T2 | 15-02 | 2 | SHLB-07 | T-15-08, T-15-09 | Candidate cap refuses by name; refusal text carries only own-tenant windows | integration (service) | `./gradlew test --tests "com.wfm.service.ShiftLibraryGenerationServiceTest"` | ❌ W0 | ⬜ pending |
| 15-02-T3 | 15-02 | 2 | SHLB-07 | — | Break-less full-length template rejected at enumeration (plan prohibition) | integration (service) | `./gradlew test --tests "com.wfm.service.ShiftLibraryGenerationServiceTest"` | ❌ W0 | ⬜ pending |
| 15-03-T1 | 15-03 | 2 | ENVL-01, ENVL-06, XCUT-03 | T-15-14 | `solverConfig.xml` change validated by a real solver build in the same commit | integration (real `SolverFactory`) | `./gradlew test --tests "com.wfm.solver.SolverConfigBuildTest"` | ❌ W0 | ⬜ pending |
| 15-03-T2 | 15-03 | 2 | ENVL-01 | T-15-10, T-15-11, T-15-12 | Shift rows tenant/desk-scoped; value range built from own desk only; V41 covered by the migration guard | unit (service) | `./gradlew test --tests "com.wfm.service.SolverServiceShiftAssignmentTest"` | ❌ W0 | ⬜ pending |
| 15-03-T3 | 15-03 | 2 | ENVL-02, ENVL-03 | — | — | constraint-unit (`ConstraintVerifier`) | `./gradlew test --tests "com.wfm.solver.ShiftEnvelopeComplianceConstraintTest"` | ❌ W0 | ⬜ pending |
| 15-04-T1 | 15-04 | 3 | ENVL-06, ENVL-07 | T-15-15, T-15-17 | Non-vacuous pass asserted; walker shares no code path with the constraint | integration (solve + ground-truth walk) | `./gradlew test --tests "com.wfm.solver.ShiftEnvelopeGroundTruthTest"` | ❌ W0 | ⬜ pending |
| 15-04-T2 | 15-04 | 3 | ENVL-02, ENVL-03, ENVL-07 | T-15-16 | Walker proven able to go red on six known violations before its clean pass is trusted | integration | `./gradlew test --tests "com.wfm.solver.ShiftEnvelopeGroundTruthTest"` | ❌ W0 | ⬜ pending |
| 15-05-T1 | 15-05 | 3 | ENVL-08, XCUT-01 | T-15-18 | Advisory/error text server-scoped; React escapes interpolated text | build + manual/UAT | `cd frontend && npm run build` | n/a (no FE framework) | ⬜ pending |
| 15-05-T2 | 15-05 | 3 | SHLB-07, XCUT-01 | T-15-19 | Draft rows saved through the unchanged server create/validate path | build + manual/UAT | `cd frontend && npm run build` | n/a | ⬜ pending |
| 15-05-T3 | 15-05 | 3 | ENVL-08, XCUT-01 | T-15-20 | No `dangerouslySetInnerHTML` introduced | build + manual/UAT | `cd frontend && npm run build` | n/a | ⬜ pending |
| 15-06-T1 | 15-06 | 4 | ENVL-04, ENVL-05, XCUT-05 | T-15-21 | Slot-mode bodies untouched; `BreakAwareConstructionTest` green AND unmodified | unit + git-diff gate | `./gradlew test && git diff --quiet HEAD -- src/test/java/com/wfm/solver/BreakAwareConstructionTest.java` | ✅ partial | ⬜ pending |
| 15-06-T2 | 15-06 | 4 | ENVL-09 | T-15-22 | Hoisted grouping lambdas reused; real-scale cost measured in 15-08 | constraint-unit | `./gradlew test --tests "com.wfm.solver.BreakClusteringConstraintTest"` | ❌ W0 | ⬜ pending |
| 15-06-T3 | 15-06 | 4 | ENVL-08 | T-15-23, T-15-24, T-15-25 | Over-tight capacity refused by name pre-solve; V42 covered by the migration guard; capacity never denies a required break | constraint-unit + service | `./gradlew test --tests "com.wfm.solver.BandCapacityConstraintTest"` | ❌ W0 | ⬜ pending |
| 15-07-T1 | 15-07 | 4 | ENVL-10, XCUT-01 | T-15-26, T-15-27 | Accepted envelope denormalised, proven immune to a later template edit | integration (service) | `./gradlew test --tests "com.wfm.service.ScheduleServiceShiftSnapshotTest"` | ❌ W0 | ⬜ pending |
| 15-07-T2 | 15-07 | 4 | XCUT-01 | T-15-29 | Descriptor built from already tenant/desk-scoped rows | unit (service) | `./gradlew test --tests "com.wfm.service.*Schedule*"` | ✅ partial | ⬜ pending |
| 15-07-T3 | 15-07 | 4 | ENVL-10, XCUT-01 | T-15-28 | Mode branch is the first statement in the per-date block — no new path runs on a slot desk | build + manual/UAT | `cd frontend && npm run build` | n/a | ⬜ pending |
| 15-08-T1 | 15-08 | 5 | XCUT-04 | T-15-30, T-15-33 | Threshold committed before any result; harness gated out of the default suite | benchmark (gated) | `./gradlew test --tests "com.wfm.solver.ShiftModelBenchmarkTest" -Dwfm.benchmark=true` | ❌ W0 | ⬜ pending |
| 15-08-T2 | 15-08 | 5 | XCUT-04 | T-15-32 | Only aggregate metrics transcribed from the indicative real-desk run | benchmark (gated) | `./gradlew test --tests "com.wfm.solver.ShiftModelBenchmarkTest" -Dwfm.benchmark=true` | ❌ W0 | ⬜ pending |
| 15-08-T3 | 15-08 | 5 | XCUT-04 | T-15-31 | Noise rule applied literally; verdict recorded verbatim | judgement + full suite | `./gradlew test` | n/a | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Sampling continuity check:** no three consecutive tasks lack an automated verify. The three
frontend tasks (15-05-T1…T3) run `npm run build` as their automated gate and route every visual claim
to a human-check, which is the standing arrangement in a repository with no frontend test framework.

---

## Wave 0 Requirements

*Each item below is now owned by a named task in the map above; no Wave 0 gap is unassigned.*

- [ ] `src/test/java/com/wfm/solver/SolverConfigBuildTest.java` — builds a solver from the real
      `solverConfig.xml`; covers XCUT-03 and ENVL-06. **No Spring context required** —
      `SolverFactory.createFromXmlResource(...).buildSolver()` is sufficient. → **15-03-T1**
- [ ] `src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java` — walks a solved schedule
      **outside the score director** asserting no `AgentAssignment` falls outside its agent's
      envelope; covers ENVL-07. This is the check that would have caught the spike's Option C.
      → **15-04-T1/T2**
- [ ] A constraint-unit test for `shiftEnvelopeCompliance` — covers ENVL-01/02/03. → **15-03-T3**
- [ ] A constraint-unit test for the real `Break clustering` body, carrying the ROADMAP's **required
      fixture**: a single-band library measurably starves a mid-shift timeslot and a multi-band
      library does not — covers ENVL-08/09. → **15-06-T2**
- [ ] A shift-mode fixture proving the four break constraints are inert in shift mode, alongside
      `BreakAwareConstructionTest` kept **green and unmodified** for slot mode — covers
      ENVL-04/05 and XCUT-05. → **15-06-T1**
- [ ] A service test for shift-library generation, including D-12's partial-coverage and
      zero-demand/zero-agent refusal cases — covers SHLB-07. → **15-02-T1/T2/T3**
- [ ] `ShiftModelBenchmarkTest` gated behind `-Dwfm.benchmark=true`, mirroring Phase 12's
      `AtomicShiftMoveBenchmarkTest` harness shape — covers XCUT-04. → **15-08-T1/T2**
- [ ] Framework install: **none needed** — JUnit 5 and `timefold-solver-test` are already present.

**Added by plan-phase, not in the original research list:**

- [ ] `src/test/java/com/wfm/solver/ShiftModeFixtures.java` — the shared shift-mode `Schedule`
      builder plans 15-04, 15-06 and 15-08 all consume, so the ground-truth walker, the gating
      fixtures and the benchmark agree on what a shift-mode schedule is. → **15-04-T1**
- [ ] `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java` — a dependency-free guard
      reconciling migration DDL column names and SQL types against the JPA `@Column` mappings,
      standing in for the migration execution CI cannot perform (`flyway.enabled: false` with
      `ddl-auto: create-drop`). Closes the G-14-1 failure class without adding Testcontainers.
      → **15-01-T3**, extended by **15-03-T2** and **15-06-T3**

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Agent Allocation view groups agents under their assigned shift with name + headcount | ENVL-10, XCUT-01 | No frontend test framework (standing project decision) | Solve a shift-scheduled desk; confirm grouped rendering, then confirm a slot-scheduled desk renders exactly as before |
| Shift-library band editor writes and reads band rows | ENVL-08 | Frontend | Add two bands to a template, save, reload, confirm persisted |
| Suggested-library draft is editable and never auto-applied | SHLB-07, D-11 | Frontend | Request a suggestion, edit a row, navigate away without saving, confirm nothing was written |
| Benchmark verdict recorded and piloting recommendation stated | XCUT-04, D-13 | Judgement call on a written report | Read `15-BENCHMARK.md`; confirm the pre-committed threshold, the PASS/FAIL verdict, and the plateau finding are all present |
| Migration applies cleanly against real Postgres | D-01 | CI runs H2 with `flyway.enabled: false` — migration SQL is never executed in the suite (UAT gap G-14-1) | Deploy to dev and confirm boot under `ddl-auto=validate` |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s on the quick command
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
