---
phase: 17
slug: consistency-constraint-drift-reporting
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-09-04
---

# Phase 17 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Seeded by `/gsd-plan-phase` from `17-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ + Timefold `ConstraintVerifier` (backend). No frontend test framework exists in this repo — `frontend/package.json` has zero UI-library/test dependencies (reconfirmed in `17-UI-SPEC.md`). |
| **Config file** | `build.gradle` (Gradle `test` task); `src/test/resources/application-test.yml` for Spring context tests |
| **Quick run command** | `./gradlew test --tests "com.wfm.solver.UsualShiftConsistencyConstraintTest"` (constraint-only, no Spring context, sub-second per `ConstraintVerifier` precedent) |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | Quick: ~1–5s · Full suite: existing project baseline |

---

## Sampling Rate

- **After every task commit:** Run the scoped class for the task's own file — e.g. `./gradlew test --tests "com.wfm.solver.UsualShiftConsistencyConstraintTest"`
- **After every plan wave:** Run `./gradlew test` (full suite). Required by this project's own recorded discipline: no solver-package test loads the Spring context, so a scoped run cannot catch a `solverConfig.xml`-adjacent regression. XCUT-03's existing full-suite-only rule applies if `solverConfig.xml` is touched (not expected this phase — no CH/moves change).
- **Before `/gsd-verify-work`:** Full suite green, **plus** the benchmark run (`-Dwfm.benchmark=true`) executed and its result committed to `17-BENCHMARK.md`, mirroring `15-BENCHMARK.md`'s structure, per D-06/XCUT-04's threshold-first discipline.
- **Max feedback latency:** ~5 seconds (scoped constraint tests)

---

## Per-Task Verification Map

> Task IDs are filled in by `/gsd-execute-phase` as plans are executed. The requirement→test
> mapping below is fixed by research and must be honoured by whichever task claims each requirement.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | 17-01 | 1 | CONS-01 | — | N/A | unit (`ConstraintVerifier`) | `./gradlew test --tests "com.wfm.solver.UsualShiftConsistencyConstraintTest"` | ❌ W0 | ⬜ pending |
| TBD | 17-01 | 1 | CONS-02 | — | N/A | unit (`ConstraintVerifier`) | same class — dedicated `@Test` at exactly the band boundary | ❌ W0 | ⬜ pending |
| TBD | 17-02 | 2 | CONS-03 | — | N/A | unit + integration | `./gradlew test --tests "com.wfm.service.ConstraintWeightsServiceTest"` | ❌ W0 | ⬜ pending |
| TBD | 17-02 | 2 | CONS-04 | T-17-02 | Hard score must be 0; reject, don't clamp (400) | unit (service validation) + structural | `./gradlew test --tests "com.wfm.service.ConstraintWeightsServiceTest"` | ❌ W0 | ⬜ pending |
| TBD | 17-02 | 2 | CONS-05 | — | N/A | unit (`ConstraintVerifier`) | `./gradlew test --tests "com.wfm.solver.PreferredStartShiftModeConstraintTest"` | ❌ W0 | ⬜ pending |
| TBD | 17-02 | 2 | CONS-06 | T-17-02 | Precedence-ordering rejection is server-side | unit (save-time rejection) + manual (UI copy, `explain()` breakdown) | `./gradlew test --tests "com.wfm.service.ConstraintWeightsServiceTest"` | ❌ W0 | ⬜ pending |
| TBD | 17-01 | 1 | DRFT-01 | T-17-03 | Report computed from an already-authorized `Schedule` | unit | `./gradlew test --tests "com.wfm.service.*DriftReport*"` | ❌ W0 | ⬜ pending |
| TBD | 17-01 | 1 | DRFT-02 | — | N/A | unit — one case per state (NO_USUAL_SHIFT / HONOURED / DRIFTED) | same class | ❌ W0 | ⬜ pending |
| TBD | 17-01 | 1 | DRFT-03 | — | N/A | unit/structural — assert the deviation calculation has a single call site shared by constraint and report | shared-fixture test asserting constraint penalty and report delta agree on one schedule | ❌ W0 | ⬜ pending |
| TBD | 17-03 | 2 | DRFT-04 | — | N/A | unit | `./gradlew test --tests "com.wfm.service.*DriftReport*"` | ❌ W0 | ⬜ pending |
| TBD | 17-03 | 2 | XCUT-01 | — | N/A | unit (export sheet) + manual (tab render — `backstop` per UI-SPEC) | `./gradlew test --tests "com.wfm.service.ScheduleExportServiceTest"` | ❌ W0 | ⬜ pending |
| TBD | 17-03 | 2 | XCUT-02 | T-17-01 | Solver path never calls `AgentUsualShiftRepository.save(...)` | structural guard test | guard test mirroring `src/test/resources/ushf-05-write-paths.md`'s enumerated-write-paths discipline | ❌ W0 | ⬜ pending |
| TBD | 17-04 | 3 | XCUT-04 | — | N/A | benchmark (gated, not in default suite) | `./gradlew test --tests "com.wfm.solver.*Benchmark*" -Dwfm.benchmark=true` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/wfm/solver/UsualShiftConsistencyConstraintTest.java` — CONS-01, CONS-02, CONS-04. Tolerance-band boundary cases, per-agent-day charging, `SchedulingMode.SHIFT` gate, unassigned-shift null-safety mirroring `shiftEnvelopeCompliance`'s `forEachIncludingUnassigned` handling.
- [ ] `src/test/java/com/wfm/solver/PreferredStartShiftModeConstraintTest.java` — CONS-05 and CONS-06's constraint-firing half; D-09's "fires independently of stored usual shift" case.
- [ ] `src/test/java/com/wfm/service/ConstraintWeightsServiceTest.java` — D-07's hard-must-be-0 rejection and D-08's precedence-ordering rejection, both asserted against the **merged** result, not the raw partial-update DTO (Pitfall 5). **Confirmed at plan time (2026-09-04): neither `ConstraintWeightsServiceTest` nor `ConstraintWeightsControllerTest` exists** — `find src/test -iname "*ConstraintWeights*"` returns nothing, so plan 17-02 builds both from scratch. Also confirmed: this repo has no Spring web-layer test context at all (`GlobalExceptionHandlerTest` records that standing decision), so the controller test is a plain instantiation test, not `@WebMvcTest`/MockMvc.
- [ ] A drift-report unit test class in `src/test/java/com/wfm/service/` (name at planner's discretion, e.g. `DriftReportTest.java`) — DRFT-01, DRFT-02, DRFT-03, DRFT-04.
- [ ] `src/test/java/com/wfm/solver/UsualShiftConsistencyBenchmarkTest.java` (or extend `ShiftModelBenchmarkTest`) — XCUT-04, gated `@EnabledIfSystemProperty("wfm.benchmark")`.
- [ ] Extend the backing map in `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java` — **required for the build to pass at all** once the new constraints exist (Pitfall 2). This is an edit to an existing file, not a new test.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Precedence between usual shift and preference is *readable* in the UI copy | CONS-06 | No frontend test framework exists in this repo | Open the constraint-weights page; confirm the copy states the precedence explicitly rather than leaving it implied by relative weights. Marked `backstop` in `17-UI-SPEC.md`. |
| Drift report panel renders as its own visible tab | XCUT-01, DRFT-01 | No frontend test framework exists in this repo | Run a solve, open the schedule detail, confirm the drift panel is present and shows agent / date / magnitude with the three states distinguished. Marked `backstop` in `17-UI-SPEC.md`. |
| `SolutionManager.explain()` shows the new constraints in its breakdown at the intended magnitude relative to the existing soft hierarchy | CONS-02, CONS-03 | Diagnostic output, inspected before a default weight ships | Run `SolverService.runPreSolveScoreDiagnostic` on the seeded dataset; read the constraint-match breakdown; confirm the consistency total does not exceed `minStaffingWeight` (1000) on the live desk. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 5s (scoped) — full suite per wave
- [ ] Benchmark threshold committed to `17-BENCHMARK.md` **before** the default weight ships (D-06/XCUT-04)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
