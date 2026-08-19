---
phase: 11
slug: bamboohr-merge-engine-report
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-08-18
validated: 2026-08-19
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Seeded by `/gsd-plan-phase` from `11-RESEARCH.md` § Validation Architecture.
> Per-task rows populated at plan-time sign-off (2026-08-19) once `11-01-PLAN.md` and
> `11-02-PLAN.md` both existed. Runtimes below are **measured**, not estimated.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (`useJUnitPlatform()`, `build.gradle:47-49`) + Mockito + AssertJ via `spring-boot-starter-test` |
| **Config file** | none — plain Gradle `test` task |
| **Quick run command** | `./gradlew test --tests "com.wfm.service.DeskAssignmentUpload*Test" --tests "com.wfm.integration.*Merge*Test"` |
| **Full suite command** | `./gradlew test` |
| **Measured targeted-filter runtime** | **7 s** (`DeskAssignmentUpload*Test`, warm daemon, 2026-08-19) |
| **Measured full-suite runtime** | **456 s (~7.6 min)**, exit 0 — full suite green at plan time |

**Established test style** (from `DeskAssignmentUploadMultiSheetTest.java:31-68`): plain unit tests, no Spring context, `mock(Repository.class)` / `mock(Service.class)` collaborators wired directly into the constructor under test, `MockMultipartFile` + `XSSFWorkbook` for in-memory test workbooks, `TenantContext.setTenantId(...)` set manually per test. New merge-engine tests follow this shape — fast, no `@SpringBootTest`.

---

## Sampling Rate

- **After every task commit:** the targeted `--tests` filter for the file(s) touched — **measured 7 s**
- **After every plan wave:** `./gradlew test --tests "com.wfm.service.*" --tests "com.wfm.integration.*"`
- **Before `/gsd-verify-work`:** `./gradlew test` full suite must be green — **measured 456 s**
- **Max feedback latency:** **7 s** at task granularity (budget: 60 s). The 456 s full suite is a wave/phase gate only — never the per-task sampling loop.

---

## Per-Task Verification Map

*Populated 2026-08-19 from `11-01-PLAN.md` and `11-02-PLAN.md`. Every row's command is copied from that task's `<automated>` block.*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| Task 1 (tracer) | 11-01 | 1 | MRG-01 (+ MRG-02/04 for the Email path only) | T-11-01, T-11-02 | Both fetches complete before the transaction opens; `listEmployees` is tenant-scoped | unit + build | `./gradlew test --tests "com.wfm.integration.UploadFreshSyncTest" --tests "com.wfm.service.DeskAssignmentUpload*Test" && npm --prefix frontend run build` | ⬜ task-created (TDD) | ⬜ pending |
| Task 2 | 11-01 | 1 | MRG-02, MRG-04, MRG-05 | T-11-03, T-11-04 | Raw BambooHR/sheet values at DEBUG only; report entries built only for tenant-resolved desks | unit + build | `./gradlew test --tests "com.wfm.integration.MergePrecedenceTest" --tests "com.wfm.integration.MergeReportTest" --tests "com.wfm.service.DeskAssignmentUpload*Test" && npm --prefix frontend run build` | ⬜ task-created (TDD) | ⬜ pending |
| Task 3 | 11-01 | 1 | MRG-07 | T-11-01, T-11-06 | Sync failure aborts with zero writes; message is upstream reason in fixed operator copy | unit | `./gradlew test --tests "com.wfm.service.UploadSyncFailureTest" --tests "com.wfm.integration.HttpBambooHRClient503Test" --tests "com.wfm.integration.UploadFreshSyncTest"` | ⬜ task-created (TDD) | ⬜ pending |
| Task 1 | 11-02 | 2 | MRG-06 | T-11-07, T-11-14 | Provenance marker relaxes only the 4th eligibility filter; `V36` default preserves existing eligibility | unit + build | `./gradlew test --tests "com.wfm.service.WorkingDaysKnownTest" --tests "com.wfm.integration.WorkingDaysSourceGuardTest" --tests "com.wfm.integration.BambooRefreshServiceTest" --tests "com.wfm.service.DeskAssignmentUpload*Test" && npm --prefix frontend run build` | ⬜ task-created (TDD) | ⬜ pending |
| Task 2 (checkpoint) | 11-02 | 2 | — (gates MRG-03) | T-11-13 | Human decision on the D-05/D-09 one-way door before it closes | **human gate** | none — `checkpoint:decision gate="blocking"` | n/a | ⬜ pending |
| Task 3 | 11-02 | 2 | MRG-03 | T-11-08, T-11-09, T-11-12, T-11-13 | Arbitration removes only recurring PTO inside the window, never MANDATORY facts; un-blocking restricted to sheet-stated worked weekdays | unit | `./gradlew test --tests "com.wfm.service.PtoArbitrationTest" --tests "com.wfm.service.SheetPatternUnblockTest" --tests "com.wfm.service.SolverService*Test" --tests "com.wfm.service.AgentDayOffRecurringExpansionTest" --tests "com.wfm.service.ResolvePreferencesPtoFilterTest"` | ⬜ task-created (TDD) | ⬜ pending |
| Task 4 | 11-02 | 2 | MRG-04, MRG-05 (D-05 week row) | T-11-10, T-11-11 | Names only from tenant-scoped desks; merge-pattern logging carries agent UUID, not raw values | unit + build | `./gradlew test --tests "com.wfm.integration.WorkingPatternMergeTest" --tests "com.wfm.integration.Merge*Test" --tests "com.wfm.integration.WorkingDaysParserTest" --tests "com.wfm.service.DeskAssignmentUpload*Test" && npm --prefix frontend run build` | ⬜ task-created (TDD) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*
*File Exists: "task-created (TDD)" — the task carries `tdd="true"`, so it writes the failing test before the implementation. This is not a Wave 0 gap.*

**Regression anchors.** Every row above re-runs at least one **pre-existing** suite alongside its net-new one, so a green result proves both the new behavior and no regression. Confirmed present on disk 2026-08-19: `SolverServiceEligibilityFilterTest`, `SolverServiceRecurringDaysOffTest`, `SolverServicePtoFilterTest`, `AgentDayOffRecurringExpansionTest`, `ResolvePreferencesPtoFilterTest`, `BambooRefreshServiceTest`, `HttpBambooHRClient503Test`, `WorkingDaysParserTest`, and the nine `DeskAssignmentUpload*Test` suites.

---

## Wave 0 Requirements

All three Wave 0 items are **resolved at plan time** — no Wave 0 execution work remains.

- [x] **New merge-engine test class names fixed.** Research Assumption A2 is resolved: the merge engine lives in `com.wfm.integration` (`AgentMergeService`), because `WorkingDaysParser`'s `parseWorkingDays` / `offDaysFrom` are package-private. Net-new classes: `UploadFreshSyncTest`, `MergePrecedenceTest`, `MergeReportTest`, `UploadSyncFailureTest`, `WorkingDaysKnownTest`, `WorkingDaysSourceGuardTest`, `PtoArbitrationTest`, `SheetPatternUnblockTest`, `WorkingPatternMergeTest`.
- [x] **Existing solver coverage confirmed.** The research pass could not locate `SolverServiceTest`; it does not exist under that name. The equivalent coverage is split across `SolverServiceEligibilityFilterTest`, `SolverServiceRecurringDaysOffTest`, `SolverServicePtoFilterTest` and `AgentDayOffRecurringExpansionTest` — all verified present. **MRG-03 and MRG-06 tests are therefore net-new classes alongside these, not edits to them**, and each plan task re-runs the pre-existing suites as regression anchors.
- [x] **No framework install needed** — JUnit 5, Mockito, AssertJ and H2 already present.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Merge report renders correctly in the Upload Results modal | MRG-04, MRG-05 | Visual/interaction contract in a browser modal; unit tests cover the report payload shape, not its rendering | Upload a spreadsheet for an agent with mixed BambooHR/sheet field coverage; confirm the modal lists each field's source and flags overridden sheet values |
| Working-pattern replacement row and newly-eligible callout render | MRG-06, D-05/D-14/D-16 | Same modal, added by 11-02 Task 4 and Task 1; the E2 UI-SPEC states (empty/loading/error/populated/partial/overflow/long-text/zero-one-many) are a rendering contract | Upload a sheet supplying a week for an agent BambooHR has no pattern for; confirm the `Working pattern (Mon–Sun)` row appears and the green newly-eligible callout lists that agent |
| D-05/D-09 arbitration-location decision | MRG-03 | `checkpoint:decision gate="blocking"` — a one-way door with no automated proxy | Answer the checkpoint in `11-02-PLAN.md` Task 2 before Task 3 runs; record the choice in the plan |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies — 6 of 7 carry `<automated>`; the 7th is the blocking human checkpoint, which by contract has none
- [x] Sampling continuity: no 3 consecutive tasks without automated verify — longest gap is **1** (11-02 Task 2, the checkpoint), immediately followed by Task 3's suite
- [x] Wave 0 covers all MISSING references — all three Wave 0 items resolved at plan time (above); the 9 net-new test classes are written by their own `tdd="true"` tasks, not deferred
- [x] No watch-mode flags — every command is a one-shot `./gradlew test` or `npm --prefix frontend run build`; no `--watch`, no `-t`, no `--continuous`
- [x] Feedback latency 7 s < 60 s budget — measured, not estimated
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** signed off 2026-08-19 at plan time, after `11-02-PLAN.md` closed the MRG-03/MRG-06 gap and the plan-checker returned `VERIFICATION PASSED`.

**Scope note:** this is a *plan-time* sign-off — it certifies that the plans' verification design satisfies Nyquist sampling, and that the full suite was green (456 s, exit 0) before execution began. Per-task `Status` cells stay ⬜ pending until `/gsd-execute-phase 11` runs them; flip each to ✅/❌ as its task commits.
