---
phase: 13
slug: per-day-hours-visibility
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-21
---

# Phase 13 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Backend: JUnit 5 + AssertJ via `spring-boot-starter-test` (`build.gradle:43`). Frontend: **none installed** — zero vitest/jest/testing-library anywhere in `frontend/` (verified in 13-RESEARCH.md Validation Architecture) |
| **Config file** | `src/test/resources/application-test.yml` — H2 in-memory, `ddl-auto: create-drop`, Flyway disabled. Frontend: `frontend/tsconfig.json` (`strict`, `noUnusedLocals`, `noUnusedParameters`) is the only static gate |
| **Quick run command** | `./gradlew test --tests "com.wfm.service.DeskAgent*"` |
| **Full suite command** | `./gradlew test` (backend) plus `npm --prefix frontend run build` (frontend type gate) |
| **Estimated runtime** | ~90 seconds backend full suite; ~15 seconds frontend `tsc -b` + vite build |

---

## Sampling Rate

- **After every task commit:** `./gradlew test --tests "com.wfm.service.DeskAgent*"` for backend tasks, `npm --prefix frontend run build` for frontend tasks
- **After every plan wave:** `./gradlew test` (full backend suite) and `npm --prefix frontend run build`
- **Before `/gsd-verify-work`:** both must be green
- **Max feedback latency:** 90 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 13-01-01 | 01 | 1 | MDL-02, UPL-03/04/05 | T-13-01 / T-13-02 | Day-hours and schedule reads are tenant-scoped by repository signature | unit | `./gradlew test --tests "com.wfm.service.DeskAgentServiceReadPathTest"` | ❌ W0 — new file | ⬜ pending |
| 13-01-02 | 01 | 1 | UPL-03/04/05 | — | N/A | type-check + manual | `npm --prefix frontend run build` + `<human-check>` | ✅ tsconfig | ⬜ pending |
| 13-02-01 | 02 | 2 | UPL-03/04/05 | T-13-05 / T-13-06 / T-13-08 | Tenant-scoped agent resolve precedes any day-hours repository call; 0–24 range rejected server-side | unit | `./gradlew test --tests "com.wfm.service.DeskAgentServiceDayHoursTest"` | ❌ W0 — new file | ⬜ pending |
| 13-02-02 | 02 | 2 | UPL-03/04/05 | T-13-07 | Invalid `DayOffType` binds to a clean 400, not a 500 | compile + behavior | `./gradlew compileJava` | ✅ | ⬜ pending |
| 13-02-03 | 02 | 2 | UPL-04, UPL-05 | — | Bulk fan-out stays a single transaction | unit | `./gradlew test --tests "com.wfm.service.DeskAgentServiceContractedHoursTest"` | ✅ existing — extend | ⬜ pending |
| 13-03-01 | 03 | 2 | UPL-09 | — | N/A | unit | `./gradlew test --tests "com.wfm.service.DeskAssignmentTemplate*"` | ✅ existing — extend | ⬜ pending |
| 13-03-02 | 03 | 2 | MDL-02, UPL-09 | T-13-10 / T-13-11 | Column-index shift does not detach an operator-controlled string column from `FormulaInjectionSanitizer` | unit | `./gradlew test --tests "com.wfm.service.DeskAgentExportServiceTest"` | ❌ W0 — new file | ⬜ pending |
| 13-04-01 | 04 | 3 | UPL-03/04/05 | T-13-14 / T-13-15 | Client validation is UX only; server re-validates | type-check + manual | `npm --prefix frontend run build` + `<human-check>` | ✅ tsconfig | ⬜ pending |
| 13-04-02 | 04 | 3 | UPL-04, UPL-05 | T-13-16 | Label-count confirmation precedes the destructive fan-out | type-check + manual | `npm --prefix frontend run build` + `<human-check>` | ✅ tsconfig | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Three backend test files do not exist today and are created by the tasks that need them, test-first
(each is written and confirmed red before its production code):

- [ ] `src/test/java/com/wfm/service/DeskAgentServiceReadPathTest.java` — created by task 13-01-01; covers MDL-02 / I-1's read-path fix including the scalar-disagreement invariant
- [ ] `src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java` — created by task 13-02-01; covers D-05's single-row-touched property, "not set" deletion, and range/tenant rejection
- [ ] `src/test/java/com/wfm/service/DeskAgentExportServiceTest.java` — created by task 13-03-02; first-ever coverage for `DeskAgentExportService`, pinning the full header row and the five cell-rendering states

Frontend test framework: **deliberately not installed** (planner decision P-11 in 13-04-PLAN.md,
adopting 13-RESEARCH.md Wave 0 Gaps option b). This project has zero frontend tests anywhere;
installing a framework would be a new-dependency decision outside this phase's scope. Frontend
behaviour is gated by `tsc -b` plus the `<verify><human-check>` blocks harvested into the phase UAT.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Collapsed `Hours/Day` cell shows a single value or a min-max range, never a MANDATORY/PTO marker | MDL-02 | No frontend test framework in this repo | See 13-01-PLAN.md task 2 `<human-check>` steps 1 |
| Expanded row renders all five weekday states distinctly (MAND badge, PTO badge, explicit zero, worked value, muted not-set) | UPL-03/04/05 | No frontend test framework | See 13-01-PLAN.md task 2 `<human-check>` steps 2-5 |
| Expanded row adds no new column and no horizontal-scroll regression | MDL-02 | Visual claim (UI-SPEC backstop items E1-overflow, E3-populated, E3-overflow) | See 13-01-PLAN.md task 2 `<human-check>` step 6 |
| Per-cell combo sets number / PTO / MANDATORY / not-set, one request each | UPL-03/04/05 | No frontend test framework | See 13-04-PLAN.md task 1 `<human-check>` steps 1-4 |
| Out-of-range typed value is rejected client-side with the exact Copywriting Contract sentence, zero requests | UPL-03 | No frontend test framework | See 13-04-PLAN.md task 1 `<human-check>` step 5 |
| Server-rejection toast reads "Couldn't save {Weekday} — …" and the cell reverts | UPL-03 | No frontend test framework | See 13-04-PLAN.md task 1 `<human-check>` step 6 |
| `Not set (default)` datalist entry is readable despite the narrow mini-column | UPL-03 | Browser-dependent visual (UI-SPEC backstop item E4-overflow) | See 13-04-PLAN.md task 1 `<human-check>` step 7 |
| Bulk action shows no dialog with zero labels, and an accurate `{N} day(s)` dialog with labels | UPL-04, UPL-05 | No frontend test framework | See 13-04-PLAN.md task 2 `<human-check>` steps 2-5 |
| Export opened in Excel shows seven Mon–Sun columns and re-uploads cleanly | MDL-02, UPL-09 | Round trip needs a real spreadsheet application and a live upload | Download the desk-agent export, confirm the seven day columns sit after "Effective Contracted Hours Per Day", edit one cell, and re-upload through the enriched upload path expecting zero skipped rows |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
