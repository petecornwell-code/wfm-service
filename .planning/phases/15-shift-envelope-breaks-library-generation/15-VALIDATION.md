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

*Populated by the planner as tasks are authored. Requirement → test-type mapping below is the
contract each task must satisfy.*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 15-XX-XX | — | — | SHLB-07 | — | — | integration | `./gradlew test --tests "*ShiftLibraryGeneration*"` | ❌ W0 | ⬜ pending |
| 15-XX-XX | — | — | ENVL-08, ENVL-09 | — | — | constraint-unit | `./gradlew test --tests "*BreakClustering*"` | ❌ W0 | ⬜ pending |
| 15-XX-XX | — | — | ENVL-01, ENVL-02, ENVL-03 | — | — | constraint-unit | `./gradlew test --tests "*ShiftEnvelope*"` | ❌ W0 | ⬜ pending |
| 15-XX-XX | — | — | ENVL-06, XCUT-03 | — | — | integration (real `SolverFactory`) | `./gradlew test --tests "*SolverConfigBuild*"` | ❌ W0 | ⬜ pending |
| 15-XX-XX | — | — | ENVL-07 | — | — | integration (solve + ground-truth walk) | `./gradlew test --tests "*GroundTruth*"` | ❌ W0 | ⬜ pending |
| 15-XX-XX | — | — | ENVL-04, ENVL-05, XCUT-05 | — | — | unit (existing suite unchanged + new shift-mode fixture) | `./gradlew test` | ✅ partial | ⬜ pending |
| 15-XX-XX | — | — | XCUT-04 | — | — | benchmark (gated out of default suite) | `./gradlew test --tests "*ShiftModelBenchmark*" -Dwfm.benchmark=true` | ❌ W0 | ⬜ pending |
| 15-XX-XX | — | — | ENVL-10, XCUT-01 | — | — | manual/UAT | — | n/a | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/wfm/solver/SolverConfigBuildTest.java` — builds a solver from the real
      `solverConfig.xml`; covers XCUT-03 and ENVL-06. **No Spring context required** —
      `SolverFactory.createFromXmlResource(...).buildSolver()` is sufficient.
- [ ] `src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java` — walks a solved schedule
      **outside the score director** asserting no `AgentAssignment` falls outside its agent's
      envelope; covers ENVL-07. This is the check that would have caught the spike's Option C.
- [ ] A constraint-unit test for `shiftEnvelopeCompliance` — covers ENVL-01/02/03.
- [ ] A constraint-unit test for the real `Break clustering` body, carrying the ROADMAP's **required
      fixture**: a single-band library measurably starves a mid-shift timeslot and a multi-band
      library does not — covers ENVL-08/09.
- [ ] A shift-mode fixture proving the four break constraints are inert in shift mode, alongside
      `BreakAwareConstructionTest` kept **green and unmodified** for slot mode — covers
      ENVL-04/05 and XCUT-05.
- [ ] A service test for shift-library generation, including D-12's partial-coverage and
      zero-demand/zero-agent refusal cases — covers SHLB-07.
- [ ] `ShiftModelBenchmarkTest` gated behind `-Dwfm.benchmark=true`, mirroring Phase 12's
      `AtomicShiftMoveBenchmarkTest` harness shape — covers XCUT-04.
- [ ] Framework install: **none needed** — JUnit 5 and `timefold-solver-test` are already present.

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
