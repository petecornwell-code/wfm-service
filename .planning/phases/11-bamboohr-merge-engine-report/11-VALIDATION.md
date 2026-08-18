---
phase: 11
slug: bamboohr-merge-engine-report
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-18
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Seeded by `/gsd-plan-phase` from `11-RESEARCH.md` § Validation Architecture.
> Per-task rows are filled by `/gsd-validate-phase` once plans exist.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (`useJUnitPlatform()`, `build.gradle:47-49`) + Mockito + AssertJ via `spring-boot-starter-test` |
| **Config file** | none — plain Gradle `test` task |
| **Quick run command** | `./gradlew test --tests "com.wfm.service.DeskAssignmentUpload*Test" --tests "com.wfm.integration.*Merge*Test"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~TBD seconds (measure during Wave 0) |

**Established test style** (from `DeskAssignmentUploadMultiSheetTest.java:31-68`): plain unit tests, no Spring context, `mock(Repository.class)` / `mock(Service.class)` collaborators wired directly into the constructor under test, `MockMultipartFile` + `XSSFWorkbook` for in-memory test workbooks, `TenantContext.setTenantId(...)` set manually per test. New merge-engine tests follow this shape — fast, no `@SpringBootTest`.

---

## Sampling Rate

- **After every task commit:** the targeted `--tests` filter for the file(s) touched
- **After every plan wave:** `./gradlew test --tests "com.wfm.service.*" --tests "com.wfm.integration.*"`
- **Before `/gsd-verify-work`:** `./gradlew test` full suite must be green
- **Max feedback latency:** TBD seconds (set once Wave 0 measures the targeted-filter runtime)

---

## Per-Task Verification Map

*Populated by `/gsd-validate-phase` after PLAN.md files exist. Requirement-level mapping seeded from research below.*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | TBD | TBD | MRG-01 | — | Fresh sync precedes any merge decision | unit | `./gradlew test --tests "*UploadFreshSyncTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | MRG-02 | — | BambooHR wins when populated; sheet fills gaps only | unit | `./gradlew test --tests "*MergePrecedenceTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | MRG-03 | — | Dated BambooHR PTO wins in-window; recurring sheet PTO fills outside it | unit | `./gradlew test --tests "*PtoArbitrationTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | MRG-04 | — | Merge report shows per-field source | unit | `./gradlew test --tests "*MergeReportTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | MRG-05 | — | Report shows sheet values overridden by BambooHR | unit | `./gradlew test --tests "*MergeReportTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | MRG-06 | — | Sheet-only pattern makes `workingDaysKnown` true → solver-eligible | unit | `./gradlew test --tests "*WorkingDaysKnownTest"` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | MRG-07 | T-11-01 | Sync failure aborts with zero writes and a clear operator message | unit | `./gradlew test --tests "*UploadSyncFailureTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] New merge-engine test class(es) — none exist yet; exact class names confirmed once the merge engine's home package/class is decided (research Assumption A2)
- [ ] Confirm whether `src/test/java/com/wfm/service/SolverServiceTest.java` (or equivalent) already exists and covers `filterEligible` / `buildRecurringDaysOff` — determines whether MRG-06 / MRG-03 tests are net-new files or extensions
- [ ] No framework install needed — JUnit 5, Mockito, AssertJ, H2 already present

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Merge report renders correctly in the Upload Results modal | MRG-04, MRG-05 | Visual/interaction contract in a browser modal; unit tests cover the report payload shape, not its rendering | Upload a spreadsheet for an agent with mixed BambooHR/sheet field coverage; confirm the modal lists each field's source and flags overridden sheet values |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < TBD s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
