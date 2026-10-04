---
phase: "23"
slug: "close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-04"
---

# Phase 23 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Gradle — unchanged from Phase 22 |
| **Config file** | `build.gradle` (no separate test config). Postgres-backed tests opt in via `PostgresBackedTest` (Testcontainers) — **not needed for this phase**: every affected test class is a pure in-memory / `ConstraintVerifier` unit test |
| **Quick run command** | `./gradlew test --tests "<the specific touched class>"` — e.g. `./gradlew test --tests "com.wfm.solver.MinimumRestShiftConstraintTest"`. Note `RestSpan` has no own test class at HEAD, so there is no `com.wfm.model.RestSpan*` target to filter on |
| **Full suite command** | `./gradlew test` (`.planning/config.json` `test_command`; `test_gate_timeout: 3600`) |
| **Estimated runtime** | ~1800 seconds full suite; targeted `--tests` run ~60 seconds |

**Project gotchas that bind this phase's sampling:**
- A filtered `--tests` run **deletes every other class's JUnit XML**. Never read a suite-wide
  aggregate immediately after a targeted run — judge a targeted run by its own class output, and
  re-run the full suite before reading any aggregate.
- A stale Gradle daemon roughly doubles suite time. `./gradlew --stop` before a full-suite run.

---

## Sampling Rate

- **After every task commit:** `./gradlew test --tests "<touched class>"`
- **After every plan wave:** `./gradlew test` (full suite)
- **Before `/gsd-verify-work`:** full suite green, **plus** `MidnightTimeArithmeticGuardTest` and the
  new `RestGapArithmeticGuardTest` explicitly passing
- **Max feedback latency:** 60 seconds (targeted run)

---

## Per-Task Verification Map

*Seeded at requirement level — plan-phase does not yet know task IDs. `/gsd-validate-phase`
replaces these rows with one row per task once PLAN.md files exist.*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | TBD | TBD | REST-02 | — | `RestSpan.gapMinutes` measures the true gap when the PREDECESSOR is overnight, SHIFT mode | unit (`ConstraintVerifier`) + direct unit | `./gradlew test --tests "com.wfm.solver.MinimumRestShiftConstraintTest"` | ✅ class exists, new methods | ⬜ pending |
| TBD | TBD | TBD | REST-02 | — | Same, SLOT mode | unit (`ConstraintVerifier`) | `./gradlew test --tests "com.wfm.solver.MinimumRestSlotConstraintTest"` | ✅ class exists, new methods | ⬜ pending |
| TBD | TBD | TBD | REST-05 | — | Pre-horizon lookback builds and measures an overnight predecessor correctly, SHIFT mode | unit | `./gradlew test --tests "com.wfm.service.RestPredecessorServiceTest"` | ✅ class exists, new methods | ⬜ pending |
| TBD | TBD | TBD | REST-05 | — | Pre-solve refusal fires against an overnight pre-horizon predecessor, SHIFT mode | unit | `./gradlew test --tests "com.wfm.service.RestFeasibilityRefusalTest"` | ✅ class exists, new methods | ⬜ pending |
| TBD | TBD | TBD | REST-05 | — | **Finding 2 (in scope):** pre-solve refusal fires against an overnight pre-horizon predecessor, **SLOT mode** — the one case that proves the `SolverService.requireRestFeasibility` sibling fix | unit | `./gradlew test --tests "com.wfm.service.RestFeasibilityRefusalTest"` | ✅ class exists, new method | ⬜ pending |
| TBD | TBD | TBD | REST-01 | — | Per-desk minimum rest continues to drive both corrected call sites (regression safety; no new operator surface in this phase) | unit | `./gradlew test --tests "com.wfm.solver.MinimumRestShiftConstraintTest" --tests "com.wfm.solver.MinimumRestSlotConstraintTest"` | ✅ class exists | ⬜ pending |
| TBD | TBD | TBD | REST-07 (regression safety, not a new requirement) | — | Waiver disclosure still computes the gap correctly when the span is overnight | unit | `./gradlew test --tests "com.wfm.service.RestWaiverDisclosureTest"` | ✅ class exists, new method | ⬜ pending |
| TBD | TBD | TBD | REST-02 / REST-05 (structural) | — | Structural guard forbids composing the isolated end-accessor outside the sanctioned callers — makes a THIRD occurrence of this defect class unrepresentable | unit (source scan, no Spring context) | `./gradlew test --tests "com.wfm.service.RestGapArithmeticGuardTest"` | ❌ W0 — new class | ⬜ pending |
| TBD | TBD | TBD | OVNT-01 / OVNT-03 (transitive) | — | The overnight templates that make an overnight predecessor reachable still save and still respect day-off marking | unit | `./gradlew test` (existing Phase 21 suites, no new cases expected) | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] **No new fixture scaffolding is needed for the fixture-level proof.** All five affected test
      classes (`MinimumRestShiftConstraintTest`, `MinimumRestSlotConstraintTest`,
      `RestPredecessorServiceTest`, `RestFeasibilityRefusalTest`, `RestWaiverDisclosureTest`) already
      carry a `shiftRow` / `slotRow` / `compliantDaySeats` / `priorSpan`-shaped builder the new
      overnight-predecessor cases can call directly. Confirmed by exhaustive read/grep of all five at
      HEAD, not sampled.
- [ ] **Genuinely new infrastructure (the one Wave 0 item):** `src/test/resources/rest-gap-arithmetic-guard.md`
      registry file mirroring `rest-waiver-predicate-guard.md`'s exact structure, plus its scanner
      test class `src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java`. This exists because
      the operator decided (2026-10-04) to build the structural guard rather than rely on fixtures
      alone — the defect class has now occurred **twice independently** in this codebase.
- [ ] The new wrap-aware `DayWindow` primitive must land before either call site is switched to it,
      so the two fix sites never diverge again (this is the root cause of Finding 2).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| An illegal overnight-predecessor roster that previously scored `0hard` now scores a hard violation on a real desk solve | REST-02 | Needs a real solve run against a desk with an overnight template, not a unit fixture; a full solve competes with the app for its two cores | On a desk with an overnight template, construct a roster with prev 22:00–06:00 followed by next-day 07:00 under an 11h minimum, solve, and confirm the result is no longer `0hard`. Read `/summary`, never `GET /schedules/{id}` (4 MB payload). |

*Everything else in this phase has automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
