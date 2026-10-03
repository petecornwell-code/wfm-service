---
phase: "22"
slug: "minimum-rest"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-03"
---

# Phase 22 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Gradle |
| **Config file** | `build.gradle` (no separate test config); Postgres-backed tests opt in via `src/test/java/com/wfm/support/PostgresBackedTest.java` (Testcontainers) |
| **Quick run command** | `./gradlew test --tests "<the specific touched class>"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~1800 seconds full suite (`test_gate_timeout: 3600`); targeted `--tests` run ~60s |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "<touched class>"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green, plus the structural guards (`MidnightTimeArithmeticGuardTest`, `BusinessDateJoinGuardTest`, and the new rest-waiver predicate guard) explicitly passing
- **Max feedback latency:** 60 seconds (targeted run)

**Project gotcha that applies to this sampling rate:** a filtered `--tests` run deletes every other
class's JUnit XML, so never read a suite-wide aggregate immediately after a targeted run — judge a
targeted run by its own class output, and re-run the full suite before reading an aggregate.

---

## Per-Task Verification Map

*Seeded at requirement level — plan-phase does not yet know task IDs. `/gsd-validate-phase`
replaces these rows with one row per task once PLAN.md files exist.*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | TBD | TBD | REST-01 | — | Operator sets/clears minimum rest per desk via PUT endpoint | integration | `./gradlew test --tests "com.wfm.controller.DeskControllerTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | REST-02 | — | Hard violation fires for insufficient rest, both modes, same-day and overnight | unit | `./gradlew test --tests "com.wfm.solver.ScheduleConstraintProviderTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | REST-03 | — | Pre-solve refusal names agent + both shifts for the structurally unavoidable case | unit | `./gradlew test --tests "com.wfm.service.SolverServiceTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | REST-04 | — | NULL minimum rest produces zero tuples (byte-identical solve) | unit | `./gradlew test --tests "com.wfm.solver.ConstraintMatchCountNonVacuityTest"` | ✅ | ⬜ pending |
| TBD | TBD | TBD | REST-05 | — | Horizon-edge behaviour (first/last day) is deliberate and tested | unit | new boundary test class | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | REST-06 | — | Waiver CRUD; solver treats a waived pair as legal | integration + unit | new test class(es) mirroring `AgentExceptionServiceTest` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | REST-07 | — | Waived violations visible in output; counts on `/summary` | unit + integration | `ScheduleOutputServiceTest` / `ScheduleDetailResponseTest` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] A structural guard test for the shared "is this pair waived" predicate (D-08), mirroring
      `bday-join-guard.md`'s parsed-allowlist pattern or `MidnightTimeArithmeticGuardTest`'s
      source-scan pattern — new infrastructure this phase must build, not reuse
- [ ] `AgentShiftAssignmentRepository` / `AgentAssignmentRepository` date-filtered read methods —
      confirmed absent; needed before any REST-05 / D-10 test can exercise the real lookback path
- [ ] `AgentRestWaiverRepository` + `RestWaiverService` and their test doubles
- [ ] A constructed fixture desk with a tight-but-not-impossible shift library, so
      "hard violation, solvable with a different choice" and "structurally impossible, pre-solve
      refused" can be distinguished in the same test file — different code paths, different fixtures
- [ ] New test methods on `DeskControllerTest` (REST-01) and `ConstraintMatchCountNonVacuityTest`
      (REST-04 non-vacuity)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Minimum-rest field renders and persists in the desk configuration UI | REST-01 | Playwright screenshots never settle on this app; geometry/DOM assertions only | Open the desk configuration form, set minimum rest, save, reload, confirm the value round-trips; assert via DOM query rather than screenshot |
| Waived-violation visibility in the solved schedule view | REST-07 | Requires a solved schedule with a deliberately waived pair — produced by a solve run, not a unit fixture | Solve a fixture desk with one waived pair, open the schedule output, confirm the waived occurrence is labelled rather than absent |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
