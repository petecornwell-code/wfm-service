---
phase: "19"
slug: "daywindow-re-anchoring"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-09-30"
---

# Phase 19 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Seeded by `/gsd-plan-phase 19` from `19-RESEARCH.md` § Validation Architecture.
> Per-task rows are filled by the planner/executor; `validate-phase` flips `status`.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + AssertJ — confirmed via `build.gradle` and `.planning/codebase/TESTING.md` |
| **Config file** | `build.gradle` (`useJUnitPlatform()`) |
| **Quick run command** | `./gradlew test --tests "com.wfm.util.DayWindowTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~15 seconds quick / full suite minutes |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.wfm.util.DayWindowTest"`
- **After every plan wave:** Run the eight-selector guard set —
  `./gradlew test --tests "com.wfm.util.DayWindowTest" --tests "com.wfm.service.MidnightTimeArithmeticGuardTest" --tests "com.wfm.solver.MidnightBoundaryRegressionTest" --tests "com.wfm.service.MidnightBoundaryPropertyTest" --tests "com.wfm.service.BusinessDateWritePathGuardTest" --tests "com.wfm.service.TimeslotGeneratorBusinessDateTest" --tests "com.wfm.solver.MidnightGapScanTest" --tests "com.wfm.service.MidnightWindowSeamTest"`
- **Before `/gsd-verify-work`:** `./gradlew test` (full, unfiltered) must be green
- **Max feedback latency:** ~30 seconds for the quick command

> **Filtered-run caveat (project memory):** a `--tests` run deletes every other class's JUnit XML.
> Never read a suite aggregate after a filtered run — the phase gate's authoritative report comes
> from the one unfiltered `./gradlew test`.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| *(filled by planner — one row per task)* | | | BDAY-04 | — | N/A | | | | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

### Requirement → criterion coverage (from RESEARCH.md)

| Criterion | Test Type | Automated Command | File Exists? |
|-----------|-----------|-------------------|--------------|
| 1 — no midnight-implicit overload survives; a missed site is a compile failure | compile-time + reflective unit | `./gradlew compileJava compileTestJava` then `./gradlew test --tests "com.wfm.util.DayWindowTest"` | ✅ `src/test/java/com/wfm/util/DayWindowTest.java` |
| 2 — position-aware functions no longer throw on end-before-start | unit | `./gradlew test --tests "com.wfm.util.DayWindowTest"` | ✅ same file — replaces `crossingMidnightIsRejected` |
| 3 — frozen-oracle byte-identical equivalence at `dayStart=00:00` | parameterised/exhaustive unit | `./gradlew test --tests "com.wfm.util.DayWindowTest"` | ✅ same file — new frozen-oracle nested class (D-13) |
| 4 — Phase 18 guard tests stay green | unit + structural guard | the eight-selector guard set above | ✅ all six Phase 18 files confirmed green 2026-09-30 |
| 5 — isolated, provable commit boundary | process check, not a test | `git diff --name-only <commit>` against the RESEARCH.md call-site file list | N/A |

---

## Wave 0 Requirements

- [ ] `src/test/java/com/wfm/util/DayWindowTest.java` — **modification, not creation.** Needs the
      frozen-oracle nested class (D-13), the `DeprecationIsLive` inversion (D-03), and updated
      assertions in the `Duration` / `Conversion` nested classes reflecting criterion 2's removed throw.
- [ ] `src/test/resources/midnight-time-arithmetic.md` — **modification.** Needs the third allowlist
      section (D-07).
- [x] No new test framework, fixture root, or dependency needed — every guard/regression test this
      phase must keep green already exists and was confirmed passing 2026-09-30.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Commit isolation (criterion 5) | BDAY-04 | A commit boundary is a property of git history, not of a running program — no test can assert it from inside the build | After the re-anchoring commit lands, run `git diff --name-only <commit>^ <commit>` and check the result against the call-site file list in `19-RESEARCH.md` § Call-site totals. No file outside `DayWindow` and its call sites may appear; the Phase 20 constraint-provider re-point must not be present. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
