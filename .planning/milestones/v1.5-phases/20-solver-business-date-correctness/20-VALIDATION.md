---
phase: "20"
slug: "solver-business-date-correctness"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-01"
---

# Phase 20 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Seeded from `20-RESEARCH.md` §"Validation Architecture". The planner fills the
> Per-Task Verification Map once task IDs exist.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ (already in use project-wide) |
| **Config file** | `build.gradle` — existing, no change needed |
| **Quick run command** | `./gradlew test --tests "<FullyQualifiedTestClass>"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | quick: ~15–60s per class · full suite: several minutes (the 48-agent BDAY-07 fixture is the new long pole) |

> **HAZARD — do not read a suite aggregate after a filtered run.** A `--tests` filtered
> `./gradlew test` run **deletes every other class's JUnit XML**. After a filtered run, the absence
> of another class's results means its XML was deleted, NOT that it failed or was skipped. Only a
> full `./gradlew test` produces a trustworthy aggregate.

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "<the class(es) that task touched>"`
- **After every plan wave:** Run `./gradlew test` (full suite)
- **Before `/gsd-verify-work`:** Full suite must be green, **plus** a manual re-grep of
  `PENDING_DESK_ANCHOR` and of `.getDate()` on a `Timeslot` receiver across the guarded files — both
  counts are known to drift as the tree moves
- **Max feedback latency:** ~60 seconds for the targeted run

**Why a full suite per wave is non-negotiable here:** this phase's characteristic failure is a
*silent non-join* — a constraint matching zero tuples scores identically to a satisfied one. A
targeted run on the class just edited cannot see that; only the full suite, including the three
existing guard tests and the extended `MidnightBoundary*` suite, can.

---

## Per-Task Verification Map

Task IDs do not exist until the planner runs. The requirement-level map below is the contract the
planner must satisfy; it replaces this table until plans are written.

| Req / Decision | Behavior | Test Type | Automated Command | File Exists |
|---|---|---|---|---|
| SOLV-01, SOLV-02 | Every key position resolves business date; guard fails on a calendar-date regression, in both directions | unit (structural guard) | `./gradlew test --tests "com.wfm.service.BusinessDateJoinGuardTest"` | ❌ W0 — new class, or a 4th scan in `MidnightTimeArithmeticGuardTest` (D-08 discretion) |
| SOLV-03 | Break bands, contiguity and envelope compliance hold at a `21:00` anchor | unit (constructed fixture, RED before the migration per D-11) | `./gradlew test --tests "com.wfm.solver.MidnightBoundaryRegressionTest"` | ✅ exists — extend |
| SOLV-04 | A SLOT-mode overnight stretch counts against ONE business day | unit (new constructed scenario) | `./gradlew test --tests "com.wfm.solver.SlotModeOvernightContractedHoursTest"` | ❌ W0 — new class |
| SOLV-05 | Seat-supply shortfall keyed by business date; the `timeslotsByDate` key-type mismatch at `SolverService:1432-1455` cannot silently return empty | unit — extend existing | `./gradlew test --tests "com.wfm.service.ShiftEnvelopeSupplyGateTest"` · also `ShiftEnvelopeSupplyInvariantTest`, `ShiftModeMinimumStaffingSeatSupplyTest` | ✅ exist — extend with a non-midnight-anchor case |
| SOLV-06 | Per-constraint match-count non-vacuity across all registered constraints | unit (assertion table + reflection agreement, mirroring `ScheduleConstraintClassificationTest`) | `./gradlew test --tests "com.wfm.solver.ConstraintMatchCountNonVacuityTest"` | ❌ W0 — new class |
| SOLV-07 | Demand upload, coverage reporting and the solver agree on the business date for a timeslot | unit (same structural guard as SOLV-02) | `./gradlew test --tests "com.wfm.service.BusinessDateJoinGuardTest"` | ❌ W0 |
| BDAY-07 | Phil-US-shaped constructed fixture: unchanged per-constraint match counts and score across the re-anchoring | unit (in-process, via `solutionManager.explain()`) | `./gradlew test --tests "com.wfm.solver.PhilUsShapedDriftGuardTest"` | ❌ W0 — new class, 48-agent fixture |
| D-06 | `AgentDayConfig.date` sourced only from the schedule period; `AgentShiftAssignment.date` only from `AgentDayConfig` | unit (structural derivation guard) | `./gradlew test --tests "com.wfm.service.AgentDayDerivationGuardTest"` | ❌ W0 — new class |
| D-02 / criterion 6 | 15-minute save-time refusal fires; Phase 18's generation-time tiling refusal is reachable and PROVEN to fire | unit + integration | `./gradlew test --tests "com.wfm.service.DeskServiceTest"` (save-time) plus a reachability test through the real `setDayStart` → generation path | save-time ✅ extend · reachability ❌ W0 |
| D-07 / criterion 7 | `PENDING_DESK_ANCHOR` gone, 19 call sites on the real anchor, allowlist entry removed, guard red if either returns | unit (existing guard, two-directional) | `./gradlew test --tests "com.wfm.service.MidnightTimeArithmeticGuardTest"` | ✅ exists — its allowlist section shrinks |

*Class names above are the researcher's suggestions, not mandates — the planner may rename, but every
row needs a runnable `<automated>` command and a `<fails_when>` naming its failure signal.*

---

## Wave 0 Requirements

- [ ] `BusinessDateJoinGuardTest` (or a 4th scan inside `MidnightTimeArithmeticGuardTest`) — SOLV-02, SOLV-07
- [ ] `AgentDayDerivationGuardTest` — D-06
- [ ] `SlotModeOvernightContractedHoursTest` — SOLV-04
- [ ] `ConstraintMatchCountNonVacuityTest`, reusing `ScheduleConstraintClassification`'s reflection technique — SOLV-06
- [ ] `PhilUsShapedDriftGuardTest`, a 48-agent in-process fixture reading `solutionManager.explain()` — BDAY-07
- [ ] `MidnightBoundaryFixture` extended to a `21:00` anchor, plus its entry in `src/test/resources/midnight-boundary-scenarios.md` — D-11, the phase tracer
- [ ] A generation-time tiling-refusal reachability test through the real `DeskService.setDayStart` path — D-02 / criterion 6

**Ordering constraint (D-11):** the `MidnightBoundaryFixture` extension and the guard tests land
**before** the migration they police and must be RED first. A guard that has never failed is not
evidence.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|---|---|---|---|
| Re-count `PENDING_DESK_ANCHOR` call sites and `.getDate()`-on-`Timeslot` key positions | SOLV-01, SOLV-03 | A count is a property of the tree at a moment, not a behavior a test can own; both counts have already drifted once during this milestone | `grep -c PENDING_DESK_ANCHOR src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` → expect 0 at phase close; re-grep the key-position list across the guarded files and confirm the guard's allowlist still accounts for every survivor |

**Explicitly NOT a manual verification:** there is no live-desk before/after run in this phase.
BDAY-07 was amended on 2026-10-01 (20-CONTEXT D-14) to a constructed Phil-US-*shaped* fixture, so the
drift check is fully automated. Restoring a genuine live comparison belongs with MIGR-01..04.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or a Wave 0 dependency
- [ ] Every runnable `<automated>` command carries a `<fails_when>` naming an observable failure signal
- [ ] Sampling continuity: no 3 consecutive tasks without an automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s for targeted runs
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
