---
phase: "18"
slug: "business-day-foundation-guards"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: validated
nyquist_compliant: false
wave_0_complete: true
created: "2026-09-30"
---

# Phase 18 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Seeded by `/gsd-plan-phase 18` from `18-RESEARCH.md` § Validation Architecture.
> The Per-Task Verification Map is filled by `/gsd-validate-phase` once task IDs exist.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + AssertJ, Spring Boot Test, Timefold Solver Test starter — all already in `build.gradle`, no install needed |
| **Config file** | `src/test/resources/application-test.yml` (H2, `flyway.enabled: false`, `ddl-auto: create-drop`) for the default suite; `src/test/java/com/wfm/support/PostgresBackedTest.java` (Testcontainers Postgres 16, real Flyway, `ddl-auto: validate`) for migration-sensitive tests, opt-in per test class |
| **Quick run command** | `./gradlew test --tests "<FQCN>"` — the single guard/unit test class the task touched |
| **Full suite command** | `./gradlew test` (`.planning/config.json` → `workflow.test_command`) |
| **Estimated runtime** | quick run seconds-scale; full suite not timed this session — `workflow.test_gate_timeout` is set to 1800s, treat that as the ceiling, not the expectation |

**Migration caveat (carried from `STATE.md`'s recorded V39 incident):** the default suite never
executes a real Flyway migration (`flyway.enabled: false`, `ddl-auto: create-drop`). **V53 must be
exercised through `PostgresBackedTest`** (or a subclass) at least once before the phase gate. V39
applied cleanly, the app failed to boot under `ddl-auto=validate`, and all 402 tests were green — a
green default suite is not evidence a migration is sound.

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "<FQCN>"` for the guard/unit class that task touched
- **After every plan wave:** Run `./gradlew test` (full suite) — required, not optional, for this phase: it
  touches shared infrastructure (`Desk`, `Timeslot`, `TimeslotGeneratorService`) that many unrelated test
  classes build fixtures against. `2196e40`'s dry-run touched 5 test files beyond its 2 main-source files.
- **Before `/gsd-verify-work`:** Full suite green, **plus** at least one `PostgresBackedTest`-based run
  exercising V53 specifically
- **Max feedback latency:** quick run must stay under 60s; a task whose only verification is the full
  suite is a sampling gap and should be flagged

---

## Per-Task Verification Map

Filled by `/gsd-validate-phase 18` after PLAN.md files exist — task IDs are not known at plan-seed time.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 18-01 | 01 | 1 | BDAY-01, BDAY-02 | — | N/A | unit + Postgres-backed | `./gradlew test --tests "com.wfm.service.DeskServiceDayStartTest"` | ✅ `src/test/java/com/wfm/service/DeskServiceDayStartTest.java` | ✅ green |
| 18-02 | 02 | 2 | BDAY-01, BDAY-02 | — | N/A | migration consistency | `./gradlew test --tests "com.wfm.migration.MigrationEntityConsistencyTest"` | ✅ `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java` | ✅ green |
| 18-03 | 03 | 2 | BDAY-03, BDAY-02 | — | N/A | unit (plain JUnit, no Spring, no DB) | `./gradlew test --tests "com.wfm.service.TimeslotGeneratorServiceTest"` | ✅ `src/test/java/com/wfm/service/TimeslotGeneratorServiceTest.java` | ✅ green |
| 18-04 | 04 | 2 | BDAY-06 | — | N/A | pure `SolutionManager` evaluation (no solve, no DB) | `./gradlew test --tests "com.wfm.solver.MidnightBoundaryRegressionTest"` | ✅ `src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java` (+ `MidnightBoundaryFixture`, `MidnightBoundaryFixtureLoadsTest`, `MidnightGapScanTest`) | ✅ green |
| 18-05 | 05 | 3 | BDAY-08, BDAY-02 | — | N/A | build-time structural scan + behavioral + Postgres-backed | `./gradlew test --tests "com.wfm.service.BusinessDateWritePathGuardTest"` | ✅ `src/test/java/com/wfm/service/BusinessDateWritePathGuardTest.java` | ✅ green |
| 18-06 | 06 | 3 | BDAY-05 | — | N/A | build-time structural scan (with pipeline red-proof) | `./gradlew test --tests "com.wfm.service.MidnightTimeArithmeticGuardTest"` | ✅ `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` (+ `src/test/resources/midnight-guard-offender/OffendingSample.java`) | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

### Requirement → test mapping (from research, pre-task-breakdown)

| Req ID | Test Type | Automated Command | File Exists? |
|--------|-----------|-------------------|-------------|
| BDAY-01 | unit + Postgres-backed | `./gradlew test --tests "com.wfm.service.DeskServiceDayStartTest"` | ✅ cherry-picked from `84fdc3f`, modulo F-1's resolution |
| BDAY-02 | unit + Postgres-backed | `./gradlew test --tests "com.wfm.migration.MigrationEntityConsistencyTest"` | ⚠️ migration test exists (`a1c0077`); the business-date-equals-calendar-date proof for a `00:00` desk is new |
| BDAY-03 | unit (plain JUnit, no Spring, no DB) | `./gradlew test --tests "com.wfm.service.TimeslotGeneratorServiceTest"` | ❌ new |
| BDAY-05 | build-time structural scan | `./gradlew test --tests "com.wfm.service.MidnightTimeArithmeticGuardTest"` | ✅ extend existing (cherry-pick `63d85a6`); ❌ D-25's pipeline-level red-proof is new |
| BDAY-06 | pure `SolutionManager` evaluation — no solve, no DB | `./gradlew test --tests "com.wfm.solver.<BusinessDayBoundaryTest>"` | ❌ entirely new; neither cited precedent is reusable as a base class (both solve first) |
| BDAY-08 | build-time structural scan + behavioral | `./gradlew test --tests "com.wfm.service.BusinessDateWritePathGuardTest"` | ✅ cherry-pick `7d42f23` |

---

## Wave 0 Requirements

- [x] `TimeslotGeneratorServiceTest` — direct unit test for BDAY-03's contiguous-24h-one-business-date
      claim against a hand-built 21:00-anchor `Desk`, no Spring context
- [x] A new test class for BDAY-06's constructed regression suite — no existing file to extend. Follow
      `LiveShapeShiftDeskFixture`'s class-load-validator idiom for D-17's non-vacuity predicates and
      `ShiftEnvelopeGroundTruthTest`'s `SolutionManager.update()` usage for scoring, but do NOT inherit
      either class's `solveCleanFixture()`-first pattern (D-14 forbids calling `solve()`)
- [x] D-25's pipeline-level red-proof for the comparison-operator guard — needs a test-resources
      directory holding one synthetic offending file and a parameterised scan root. Confirm no such
      fixture directory already exists before creating one.
- [x] Framework install: **none** — JUnit 5, AssertJ, Timefold Solver Test and Testcontainers are already
      `build.gradle` dependencies

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| The `day_start` control in desk configuration states in its copy that only `00:00` is supported until overnight scheduling lands | BDAY-01 (D-26, D-28) | Knowingly accepted gap per D-28 — no frontend test framework exists in this project (Phase 13's P-11 ruling), and Phase 19 deletes this copy within one phase. A `.tsx` source scan was considered and rejected as brittle to ordinary rewording for a one-phase lifespan. | Open desk configuration, locate the `day_start` field beside `scheduling_mode` and `default_contracted_hours_per_day`, confirm it is visibly non-editable and its copy names the `00:00`-only restriction explicitly. Record in the phase UAT document. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 60s for per-task quick runs
- [x] V53 exercised at least once through `PostgresBackedTest`
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** validated 2026-09-30 by `/gsd-validate-phase 18` (PARTIAL — 6 requirements automated, 1 manual-only behavior)

---

## Validation Audit 2026-09-30

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |

**Method.** State A audit. All six requirements (BDAY-01, -02, -03, -05, -06, -08) were
cross-referenced against test classes on disk; every one exists at the path the
requirement→test mapping predicted. No auditor spawn was needed.

**Green evidence.** The deploy gate for commit `4065041` ran `./gradlew build --no-daemon` —
the full unfiltered suite — and passed (GitHub Actions run `36762808705`). No filtered
`--tests` run was used to establish this, because a filtered run deletes the other classes'
JUnit XML and would invalidate any suite-level aggregate read afterwards.

**Wave 0.** All four items complete, and their two binding constraints hold on inspection:
`TimeslotGeneratorServiceTest` loads no Spring context, and `MidnightBoundaryRegressionTest`
never calls `solve()` (D-14).

**Why `nyquist_compliant: false` with zero gaps.** One behavior remains manual-only by
design — the `day_start` disclosure copy in desk configuration (D-28: no frontend test
framework exists, and Phase 19 deletes the copy). That is a knowingly accepted exception,
not an unfilled gap. It was verified by hand on 2026-09-30 against the deployed environment
and recorded as passed in `18-UAT.md`.
