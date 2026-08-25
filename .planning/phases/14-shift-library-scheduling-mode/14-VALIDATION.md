---
phase: 14
slug: shift-library-scheduling-mode
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-25
---

# Phase 14 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (backend); no frontend test framework — see 14-RESEARCH.md |
| **Config file** | `build.gradle` |
| **Quick run command** | `./gradlew test --tests '*ShiftTemplate*' --tests '*SchedulingMode*'` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | To be filled from RESEARCH.md §Validation Architecture at plan time |

---

## Sampling Rate

- **After every task commit:** Run the quick command scoped to the task's new test class
- **After every plan wave:** Run `./gradlew test` (full suite — MODE-05's proof is the existing suite still green)
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** to be set at plan time from measured suite runtime

---

## Per-Task Verification Map

*Populated by the planner from PLAN.md task IDs. One row per task.*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 14-01-01 | 01 | 1 | SHLB-01 | — | — | unit | `./gradlew test --tests '*ShiftTemplate*'` | ❌ W0 | ⬜ pending |

---

## Wave 0 Requirements

- [ ] New backend test classes for the shift-template CRUD stack, following `JobTitleConfigServiceTest`'s `@DataJpaTest` + `@Import` + `@ActiveProfiles("test")` + explicit `TenantContext.setTenantId` shape (`Specialization` has no test file to mirror — 14-RESEARCH.md)
- [ ] A test asserting the XCUT-05 classification map's key set equals what `ScheduleConstraintProvider.defineConstraints` registers (19 constraints confirmed)
- [ ] No framework install needed — JUnit 5 + Spring Boot Test already present

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Shift library page renders, mode toggle visible, coverage refusal message readable | SHLB-01…04, MODE-02, XCUT-01 | No frontend test framework exists in this project (14-RESEARCH.md) | Open the Shift Library page for a desk, create a template, attempt a mode switch with uncovered demand, confirm the refusal names the uncovered windows |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency under the measured full-suite runtime
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
