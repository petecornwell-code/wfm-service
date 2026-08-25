---
phase: 12
slug: atomic-shift-move
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-13
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Seeded from `12-RESEARCH.md` § Validation Architecture. Task IDs are filled in by
> `/gsd-validate-phase` once PLAN.md task numbering is final.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + AssertJ, via `ai.timefold.solver:timefold-solver-test` (`build.gradle:44`) |
| **Config file** | none — Gradle's built-in `test` task on the JUnit Platform |
| **Quick run command** | `./gradlew test --tests "com.wfm.solver.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~120 seconds (quick) / full suite longer — existing solver tests are `Duration`-bounded |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.wfm.solver.*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green **and** the repeated-run benchmark
  comparison below must meet its must-pass thresholds. A single green solve is explicitly
  insufficient evidence for this phase — run-to-run variance currently exceeds the effect size
  of most changes (ROADMAP Phase 12 evidence).
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | TBD | TBD | — (no REQ-IDs mapped) | — | N/A | unit | `./gradlew test --tests com.wfm.solver.ShiftWindowFinderTest` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | — | — | N/A | unit | `./gradlew test --tests com.wfm.solver.AtomicShiftMoveFactoryTest` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | — | — | N/A | integration (`FULL_ASSERT`) | `./gradlew test --tests com.wfm.solver.AtomicShiftMoveFullAssertTest` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | — | — | N/A | regression | `./gradlew test --tests com.wfm.solver.BreakAwareConstructionTest` | ✅ | ⬜ pending |
| TBD | TBD | TBD | — | — | N/A | benchmark (5 seeded runs) | scripted harness — see Manual-Only Verifications | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Behaviors covered (from ROADMAP Phase 12 goal — no REQ-IDs are mapped to this phase):**

1. A full contracted shift (contiguous work slots + one correctly positioned break) is reachable as a single atomic move.
2. The custom move never corrupts the incremental score (undo correctness) — caught by `EnvironmentMode.FULL_ASSERT`.
3. The move **composes with**, not replaces, existing change/swap moves — fine-grained repair still works.
4. Illegal break placements (misaligned vs `breakStartAlignment`, inside `breakBlockedHours`) are never generated.
5. Agents previously pinned one slot below the break threshold reach full contracted hours **across repeated runs**.

---

## Wave 0 Requirements

- [ ] `src/test/java/com/wfm/solver/ShiftWindowFinderTest.java` — pure-function tests for
      (agent-day, free spec-matching seats, `AgentDayConfig`) → legal candidate windows. No solver, fast.
- [ ] `src/test/java/com/wfm/solver/AtomicShiftMoveFactoryTest.java` — move-factory contract tests
      (expected moves produced from a fixture `Schedule`; all-or-nothing doability enforced at
      generation time, since `CompositeMove.isMoveDoable` is an OR across submoves).
- [ ] `src/test/java/com/wfm/solver/AtomicShiftMoveFullAssertTest.java` — short local search under
      `EnvironmentMode.FULL_ASSERT`, following `IncrementalScoringDiagnosticTest.java:131-149`.
- [ ] Repeatable benchmark harness — checked-in fixture matching the reproduction scenario
      (400% over-allocation, 15-min increments, 8h contracted, 60-min break, 1h blocked window),
      `SolverConfig.withRandomSeed(<constant>)`, and **step-count termination** (not wall-clock)
      for controlled comparison runs. Production `SolverService` wall-clock termination stays untouched.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Benchmark comparison across repeated runs | — (ROADMAP Phase 12 goal) | Requires ≥5 seeded runs per configuration (baseline vs with-move) and a median/spread comparison; too slow and too statistical for the per-commit suite | Run the Wave 0 harness 5× baseline and 5× with the new move enabled. Record per run: hard score, soft score, hours assigned vs needed, count of agent-days with `effectiveHours > breakMinShiftHours` and zero breaks, count of agent-days at exactly `breakThresholdSlots - 1`. Report **median and full min/max spread** — the baseline data is bimodal, so a mean is misleading. |
| Live desk sanity check | — | Requires production-shaped data | Re-run the Stubhub (EN) desk scenario and confirm agents are no longer pinned at 15 slots with no breaks |

---

## Pass / Fail Thresholds (phase gate)

- **Must-pass:** across 5 repeated benchmark runs with the new move enabled, **median hours assigned**
  exceeds the median of 5 baseline runs by a margin **larger than the observed baseline spread**
  (baseline ≈ 30.25–80.50h). Landing inside the existing noisy band is not a pass.
- **Must-pass:** **zero** agent-days across all 5 post-fix runs show `effectiveHours > breakMinShiftHours`
  with zero breaks.
- **Must-pass:** `AtomicShiftMoveFullAssertTest` green — no score corruption under `FULL_ASSERT`.
- **Must-pass:** `BreakAwareConstructionTest` still green **unmodified** — proves the new move
  composed with, rather than displaced, the existing change/swap selectors.
- **Should-pass (non-blocking, track as follow-up):** post-fix hard score regresses below the best
  observed baseline (`-4,930`) in no more than 1 of 5 runs.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
