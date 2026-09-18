---
phase: 17
slug: consistency-constraint-drift-reporting
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-09-04
validated: 2026-09-18
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

| Task ID | Plan | Wave | Requirement | Covering Test | Automated Command | File Exists | Status |
|---------|------|------|-------------|---------------|-------------------|-------------|--------|
| not recorded | 17-01 | 1 | CONS-01 | `UsualShiftConsistencyConstraintTest` (5) | `./gradlew test --tests "com.wfm.solver.UsualShiftConsistencyConstraintTest"` | ✅ | ✅ green |
| not recorded | 17-01 | 1 | CONS-02 | `UsualShiftConsistencyConstraintTest` — `deviationEqualsBand_noPenalty` / `deviationOneMinuteBeyondBand_penalisedByOne` (exact boundary pair) | same class | ✅ | ✅ green |
| not recorded | 17-02 | 2 | CONS-03 | `ConstraintWeightsServiceTest` (10) | `./gradlew test --tests "com.wfm.service.ConstraintWeightsServiceTest"` | ✅ | ✅ green |
| not recorded | 17-02 | 2 | CONS-04 | `ConstraintWeightsServiceTest` (D-07 hard-must-be-0 rejection) + `SolverUsualShiftWritePathGuardTest` | same class + guard class | ✅ | ✅ green |
| not recorded | 17-02 | 2 | CONS-05 | `PreferredStartShiftModeConstraintTest` (9) | `./gradlew test --tests "com.wfm.solver.PreferredStartShiftModeConstraintTest"` | ✅ | ✅ green |
| not recorded | 17-02 | 2 | CONS-06 | `ConstraintWeightsServiceTest` (D-08 precedence rejection); UI-copy half is manual-only below | same class | ✅ | ✅ green |
| not recorded | 17-01 | 1 | DRFT-01 | `DriftReportTest` (19) | `./gradlew test --tests "com.wfm.service.DriftReportTest"` | ✅ | ✅ green |
| not recorded | 17-01 | 1 | DRFT-02 | `DriftReportTest` — one case per state (NO_USUAL_SHIFT / HONOURED / DRIFTED) | same class | ✅ | ✅ green |
| not recorded | 17-01 | 1 | DRFT-03 | `DriftReportTest` — the load-bearing agreement assertion that the constraint's penalty and the report's delta agree on one schedule (single shared call site) | same class | ✅ | ✅ green |
| not recorded | 17-03 | 2 | DRFT-04 | `DriftReportTest` — popularity ranking, count-desc then name-asc | same class | ✅ | ✅ green |
| not recorded | 17-03 | 2 | XCUT-01 | `ScheduleExportServiceTest` (10, incl. 2 added this session for the multi-day export NPE) | `./gradlew test --tests "com.wfm.service.ScheduleExportServiceTest"` | ✅ | ✅ green |
| not recorded | 17-03 | 2 | XCUT-02 | `SolverUsualShiftWritePathGuardTest` (3) — behavioural zero-mutating-interactions plus a structural source scan, itself guarded by a comment-stripping test | `./gradlew test --tests "com.wfm.service.SolverUsualShiftWritePathGuardTest"` | ✅ | ✅ green |
| not recorded | 17-04 | 3 | XCUT-04 | `UsualShiftConsistencyBenchmarkTest` (2, `@EnabledIfSystemProperty("wfm.benchmark")` — skipped in the default suite by design); results committed to `17-BENCHMARK.md` | `./gradlew test --tests "com.wfm.solver.*Benchmark*" -Dwfm.benchmark=true` | ✅ | ✅ green (gated) |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/java/com/wfm/solver/UsualShiftConsistencyConstraintTest.java` — CONS-01, CONS-02, CONS-04. Tolerance-band boundary cases, per-agent-day charging, `SchedulingMode.SHIFT` gate, unassigned-shift null-safety mirroring `shiftEnvelopeCompliance`'s `forEachIncludingUnassigned` handling.
- [x] `src/test/java/com/wfm/solver/PreferredStartShiftModeConstraintTest.java` — CONS-05 and CONS-06's constraint-firing half; D-09's "fires independently of stored usual shift" case.
- [x] `src/test/java/com/wfm/service/ConstraintWeightsServiceTest.java` — D-07's hard-must-be-0 rejection and D-08's precedence-ordering rejection, both asserted against the **merged** result, not the raw partial-update DTO (Pitfall 5). **Confirmed at plan time (2026-09-04): neither `ConstraintWeightsServiceTest` nor `ConstraintWeightsControllerTest` exists** — `find src/test -iname "*ConstraintWeights*"` returns nothing, so plan 17-02 builds both from scratch. Also confirmed: this repo has no Spring web-layer test context at all (`GlobalExceptionHandlerTest` records that standing decision), so the controller test is a plain instantiation test, not `@WebMvcTest`/MockMvc.
- [x] A drift-report unit test class in `src/test/java/com/wfm/service/` (name at planner's discretion, e.g. `DriftReportTest.java`) — DRFT-01, DRFT-02, DRFT-03, DRFT-04.
- [x] `src/test/java/com/wfm/solver/UsualShiftConsistencyBenchmarkTest.java` (or extend `ShiftModelBenchmarkTest`) — XCUT-04, gated `@EnabledIfSystemProperty("wfm.benchmark")`.
- [x] Extend the backing map in `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java` — **required for the build to pass at all** once the new constraints exist (Pitfall 2). This is an edit to an existing file, not a new test.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Precedence between usual shift and preference is *readable* in the UI copy | CONS-06 | No frontend test framework exists in this repo — **discharged 2026-09-18 by 17-UAT.md test 7** (Playwright: D-10 precedence note renders as a full-width colspan=5 row directly beneath the three new rows) | Open the constraint-weights page; confirm the copy states the precedence explicitly rather than leaving it implied by relative weights. Marked `backstop` in `17-UI-SPEC.md`. |
| Drift report panel renders as its own visible tab | XCUT-01, DRFT-01 | No frontend test framework exists in this repo — **discharged 2026-09-18 by 17-UAT.md test 5** (Playwright against a live solve: all three states rendered, summary bar tracked the date filter 60/17/33/10 → 12/2/8/2, status colours measured for WCAG AA) | Run a solve, open the schedule detail, confirm the drift panel is present and shows agent / date / magnitude with the three states distinguished. Marked `backstop` in `17-UI-SPEC.md`. |
| `SolutionManager.explain()` shows the new constraints in its breakdown at the intended magnitude relative to the existing soft hierarchy | CONS-02, CONS-03 | Diagnostic output, inspected before a default weight ships | Run `SolverService.runPreSolveScoreDiagnostic` on the seeded dataset; read the constraint-match breakdown; confirm the consistency total does not exceed `minStaffingWeight` (1000) on the live desk. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 5s (scoped) — full suite per wave
- [x] Benchmark threshold committed to `17-BENCHMARK.md` **before** the default weight ships (D-06/XCUT-04)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending


## Validation Audit 2026-09-18

Run by `/gsd-validate-phase 17`. The document was still `status: draft` — seeded at plan time and
never updated during execution — so every row read `TBD / ❌ W0 / ⬜ pending` although the tests
had existed and been green since the phase executed. This audit reconciles the map with reality.

| Metric | Count |
|--------|-------|
| Requirements mapped | 13 |
| Gaps found (MISSING) | 0 |
| Resolved by auditor | 0 (none needed) |
| Escalated to manual-only | 0 |
| Manual-only backstops discharged by UAT | 2 of 3 |

Evidence: every automated command in the map above was executed this session —
67 tests, 0 failures, 0 errors across the eight covering classes; the full suite is separately
green. No `gsd-nyquist-auditor` spawn was required because no requirement lacked automated
verification.

Remaining manual-only item: the `SolutionManager.explain()` breakdown inspection (CONS-02/CONS-03).
It was performed at plan time and is recorded in `17-BENCHMARK.md`'s explain() section, but it is a
diagnostic read rather than an assertion, so it stays manual by nature.
