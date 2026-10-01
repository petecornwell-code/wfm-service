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

Filled by plan 19-08 Task 2, 2026-10-01, from the automated commands that actually ran in each
plan's own `<verify>` block and the task commit each produced. All 19 rows green at the phase's
final commit (`4a129d3`, this plan's Task 1); re-run for Task 3's own two commits separately below
the table.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | Commit | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|--------|--------|
| 19-01 T1 | 19-01 | 1 | BDAY-04 | — | Additive no-op: `ScheduleConfig.dayStart` reaches the solver, read by nothing | unit + integration | `./gradlew test --tests "com.wfm.migration.MigrationEntityConsistencyTest" --tests "com.wfm.solver.ScheduleConfigAnchorPlumbingTest"`; `./gradlew test` | `47649f1` | ✅ green |
| 19-02 T1 | 19-02 | 1 | BDAY-04 | — | Frozen oracle transcribed, proven faithful to the live statics | unit | `./gradlew test --tests "com.wfm.util.DayWindowTest"` | `4d61cde` | ✅ green |
| 19-02 T2 | 19-02 | 1 | BDAY-04 | — | Throw domain pinned as data (predicates + expected post-migration value) | unit | `./gradlew test --tests "com.wfm.util.DayWindowTest"` | `17641c5` | ✅ green |
| 19-03 T1 | 19-03 | 2 | BDAY-04 | — | Tracer: `anchoredAt` + nine instance methods exist; one solver constraint consumes the real desk anchor | unit + integration | `./gradlew test --tests "com.wfm.util.DayWindowAnchorBindingTest" --tests "com.wfm.solver.DeskAnchorReachesConstraintTest"`; `./gradlew test` | `3d45274` | ✅ green |
| 19-04 T1 | 19-04 | 3 | BDAY-04 | — | Break-band times take the window; controller reaches an anchor | compile + integration | `./gradlew compileJava compileTestJava`; `./gradlew test` | `7296d3d` | ✅ green |
| 19-04 T2 | 19-04 | 3 | BDAY-04 | — | Net hours takes the window, across every caller | compile + integration | `./gradlew compileJava compileTestJava`; `./gradlew test` | `a2c76fa` | ✅ green |
| 19-05 T1 | 19-05 | 3 | BDAY-04 | — | Transitional `covers` forms retired; every caller supplies a window | compile + unit + integration | `./gradlew compileJava compileTestJava`; `./gradlew test --tests "com.wfm.service.MidnightWindowSeamTest" --tests "com.wfm.service.ScheduleEnvelopeRepairServiceTest"`; `./gradlew test` | `faed18a` | ✅ green |
| 19-06 T1 | 19-06 | 4 | BDAY-04 | — | Generation/upload services bind a real window | compile + unit + integration | `./gradlew compileJava compileTestJava`; `./gradlew test --tests "com.wfm.service.TimeslotGeneratorBusinessDateTest" --tests "com.wfm.service.TimeslotGeneratorServiceTest"`; `./gradlew test` | `bc444b5` | ✅ green |
| 19-06 T2 | 19-06 | 4 | BDAY-04 | — | Validation services bind a window; save-path refusal survives intact | compile + unit + integration | `./gradlew compileJava compileTestJava`; `./gradlew test --tests "com.wfm.service.ShiftTemplateServiceTest" --tests "com.wfm.service.ShiftLibraryValidationServiceTest"`; `./gradlew test` | `610a805` | ✅ green |
| 19-06 T3 | 19-06 | 4 | BDAY-04 | — | Report/export services bind the anchor the schedule carries | compile + unit + integration | `./gradlew compileJava compileTestJava`; `./gradlew test --tests "com.wfm.service.ScheduleExportServiceTest" --tests "com.wfm.service.ScheduleAllocationExportTest" --tests "com.wfm.service.ScheduleRosterExportTest"`; `./gradlew test` | `5c9a728` | ✅ green |
| 19-07 T1 | 19-07 | 5 | BDAY-04 | — | Every constraint helper's anchor is visible at its call site; `PENDING_DESK_ANCHOR` introduced | compile + unit + integration | `./gradlew compileJava compileTestJava`; `./gradlew test --tests "com.wfm.solver.MidnightGapScanTest" --tests "com.wfm.solver.MidnightBoundaryRegressionTest"`; `./gradlew test` | `1495751` | ✅ green |
| 19-07 T2 | 19-07 | 5 | BDAY-04 | — | Standalone generator and solver's remaining direct calls migrated | compile + integration | `./gradlew compileJava compileTestJava`; `./gradlew test` | `42c650d` | ✅ green |
| 19-07 T3 | 19-07 | 5 | BDAY-04 | T-19-15 (guard reused by plan 19-08) | Third allowlist section + guard scan, proven two-directional | unit + integration | `./gradlew test --tests "com.wfm.service.MidnightTimeArithmeticGuardTest"`; `./gradlew test` | `ca9a450` | ✅ green |
| 19-08 T1 | 19-08 | 6 | BDAY-04 | T-19-15 | Nine statics demoted to private; oracle flipped onto the bound instance; reflective guard inverted | compile + unit + integration | `./gradlew compileJava compileTestJava`; `./gradlew test --tests "com.wfm.util.DayWindowTest"`; `./gradlew test` | `4a129d3` | ✅ green |
| 19-08 T2 | 19-08 | 6 | BDAY-04 | — | Criterion 4 (eight guards) and criterion 5 (commit-range isolation) proven over the actual range | integration + process check | eight-selector guard set; `./gradlew test`; `git diff --name-only 47649f1..4a129d3` | *(this plan's metadata commit)* | ✅ green |
| 19-08 T3 | 19-08 | 6 | BDAY-04 | T-19-13 | `DeskService`/`DeskManagement.tsx` name `SOLV-01`, not `BDAY-04`, as the range-widener; gate itself untouched | unit + build + process check | `./gradlew test --tests "com.wfm.service.DeskServiceDayStartTest"`; `npm --prefix frontend run build` | *(filled after Task 3 commits)* | ⬜ pending |

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

- [x] `src/test/java/com/wfm/util/DayWindowTest.java` — **modification, not creation.** Needs the
      frozen-oracle nested class (D-13, landed plan 19-02), the `DeprecationIsLive` inversion (D-03,
      landed plan 19-08 Task 1 as `NoPublicStaticTakesABareSchedulingTime`), and updated assertions
      in the `Duration` / `Conversion` nested classes reflecting criterion 2's removed throw (landed
      plan 19-08 Task 1).
- [x] `src/test/resources/midnight-time-arithmetic.md` — **modification.** Third allowlist section
      (D-07) landed plan 19-07 Task 3.
- [x] No new test framework, fixture root, or dependency needed — every guard/regression test this
      phase must keep green already exists and was confirmed passing 2026-09-30.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions | Result |
|----------|-------------|------------|-------------------|--------|
| Commit isolation (criterion 5) | BDAY-04 | A commit boundary is a property of git history, not of a running program — no test can assert it from inside the build | After the re-anchoring commit lands, run `git diff --name-only <commit>^ <commit>` and check the result against the call-site file list in `19-RESEARCH.md` § Call-site totals. No file outside `DayWindow` and its call sites may appear; the Phase 20 constraint-provider re-point must not be present. | ✅ Verified 2026-10-01 by plan 19-08 Task 2: `git diff --name-only 47649f1..4a129d3` (verbatim output and per-path verdict in `19-08-SUMMARY.md`). All paths are either `.planning/` docs, the sanctioned `DayWindow` call-site list, or one of four files that gained a legitimate new `DayWindow` reference (propagating the D-09 model-class signature change to their own callers — `ShiftTemplateController`, `DeskAgentService`, `ScheduleEnvelopeRepairService`, `ShiftStartMixTargetService`, each confirmed 0 refs before / >0 refs after). No migration file, no `ScheduleConfigAnchorPlumbingTest` (confirming the lower bound), `ifExists(ScheduleConfig` unchanged at 9, `.join(` count on `ScheduleConstraintProvider` unchanged at 31, no `.equal(` join-key predicate exists. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies — confirmed for all 16 rows above
- [x] Sampling continuity: no 3 consecutive tasks without automated verify — every task row has at
      least one automated command
- [x] Wave 0 covers all MISSING references — both Wave 0 items now landed and checked above
- [x] No watch-mode flags — every command in the table is a one-shot `./gradlew`/`npm run build` invocation
- [x] Feedback latency < 30s — the quick-command (`DayWindowTest` alone) completes in ~6-10s per run
- [ ] `nyquist_compliant: true` set in frontmatter — left for `/gsd-validate-phase` to set; this plan
      fills the map with real data but does not own the frontmatter status transition

**Approval:** data complete as of plan 19-08 Task 2 (2026-10-01); frontmatter `status`/`nyquist_compliant`
transition is `/gsd-validate-phase`'s own action, not performed here.
