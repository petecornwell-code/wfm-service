---
phase: 10
slug: enriched-upload-parsing
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-31
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ + Mockito, via `spring-boot-starter-test` (build.gradle) |
| **Config file** | `build.gradle` (no separate JUnit platform config file) |
| **Quick run command** | `./gradlew test --tests "com.wfm.service.DeskAssignmentUpload*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~quick: seconds; full: minutes (existing suite) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.wfm.service.DeskAssignmentUpload*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~60 seconds (quick command)

---

## Per-Task Verification Map

> Task IDs are assigned during planning; rows are keyed by requirement + behavior until plans finalize.

| Requirement | Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|----------|-----------|-------------------|-------------|--------|
| UPL-01 | Multi-sheet parse, sheet name = desk; unmatched sheet skipped (D-02) | unit | `./gradlew test --tests "com.wfm.service.DeskAssignmentUploadMultiSheetTest"` | ❌ W0 | ⬜ pending |
| UPL-02 | Unbounded `Specialty N` detection, first-non-blank = primary | unit | `./gradlew test --tests "com.wfm.service.DeskAssignmentUploadSpecialtyTest"` | ❌ W0 | ⬜ pending |
| UPL-03/04/05 | Day-cell parse: number / MANDATORY / PTO → `agent_day_hours` (+ fractional hours, D-12 PTO label) | unit | `./gradlew test --tests "com.wfm.service.DeskAssignmentUploadDayCellTest"` | ❌ W0 | ⬜ pending |
| UPL-06 | Validation skip reasons, clamp warning surfaced non-silently, per-sheet rollup counts | unit | `./gradlew test --tests "com.wfm.service.DeskAssignmentUploadValidationTest"` | ❌ W0 | ⬜ pending |
| UPL-07 | BambooHR-ID-only match; unmatched ID rejected, no agent created | unit | `./gradlew test --tests "com.wfm.service.DeskAssignmentUploadNonSchedulableRejectTest"` (extend) | ✅ extend | ⬜ pending |
| UPL-08 | Both retired shapes (6-col legacy + old flat enriched) → "download the new template" message | unit | `./gradlew test --tests "com.wfm.service.DeskAssignmentUploadRetiredShapeTest"` | ❌ W0 | ⬜ pending |
| UPL-09 | Pre-seeded template round-trips through `EnrichedColumnLayout` (identity filled, day+specialty blank) | unit + manual | `./gradlew test --tests "com.wfm.service.DeskAssignmentTemplateServiceTest"` | ❌ W0 | ⬜ pending |
| D-12 (solver/refresh regression) | Uploaded `PTO`/`MANDATORY`/hours survive a `refreshDeskAgents` call unchanged | integration | `./gradlew test --tests "com.wfm.integration.BambooRefreshServiceTest"` (extend) | ✅ extend | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/wfm/service/DeskAssignmentUploadMultiSheetTest.java` — UPL-01 (multi-sheet, sheet-name→desk, D-02 skip-notice)
- [ ] `src/test/java/com/wfm/service/DeskAssignmentUploadSpecialtyTest.java` — UPL-02 (N-column detection, first-non-blank primary)
- [ ] `src/test/java/com/wfm/service/DeskAssignmentUploadDayCellTest.java` — UPL-03/04/05 incl. fractional-hours regression (getCellString bug) and D-12 PTO label storage
- [ ] `src/test/java/com/wfm/service/DeskAssignmentUploadValidationTest.java` — UPL-06 (blank/invalid cell skip, clamp-with-warning, per-sheet rollup counts)
- [ ] `src/test/java/com/wfm/service/DeskAssignmentUploadRetiredShapeTest.java` — UPL-08 (both retired shapes → new-template message)
- [ ] `src/test/java/com/wfm/service/DeskAssignmentTemplateServiceTest.java` — UPL-09 (pre-seeded identity filled, day+specialty blank, shared `EnrichedColumnLayout`)
- [ ] Extend `src/test/java/com/wfm/integration/BambooRefreshServiceTest.java` — regression guard for the D-12 window-wipe hazard (refresh must not delete/alter `agent_day_hours` rows)
- [ ] Framework install: none — `spring-boot-starter-test` already present

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Upload Results modal shows per-sheet rollup, skip reasons, clamp + skipped-sheet warnings | UPL-06 | Frontend rendering of `DeskAssignmentUploadResult` in `ClientManagement.tsx` | Upload a mixed-validity workbook; confirm rollup counts, skip reasons, clamp warnings, and unmatched-sheet notice render |
| Pre-seeded template download opens with roster identity filled, schedule blank | UPL-09 | Browser download + Excel open | Download template for a desk; confirm identity columns populated, 7 day cells + specialty columns blank |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
