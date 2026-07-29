---
phase: 5
slug: agent-data-enrichment-desk-upload
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-11
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Spring Boot test) for backend; manual-only for frontend (no JS test framework installed) |
| **Config file** | `build.gradle` (test task), `src/test/resources/application-test.yml` if present |
| **Quick run command** | `./gradlew test --tests "com.wfm.<package>.<ClassName>"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~60–120 seconds full suite |

---

## Sampling Rate

- **After every task commit:** Run targeted `./gradlew test --tests <class>` for the file under change.
- **After every plan wave:** Run `./gradlew test` (full backend suite).
- **Before `/gsd-verify-work`:** Full suite must be green; manual UI walkthrough recorded.
- **Max feedback latency:** ~120 seconds backend; manual UI verifications recorded separately.

---

## Per-Task Verification Map

To be filled by the planner. Every task with backend code changes must reference an automated command; UI-only behaviors must appear in the Manual-Only Verifications table below.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 5-01-01 | 01 | 1 | DATA-02 | — | Employment type column synced from BambooHR | unit | `./gradlew test --tests "com.wfm.integration.BambooRefreshServiceTest"` | ❌ W0 | ⬜ pending |

---

## Wave 0 Requirements

- [ ] `src/test/java/com/wfm/integration/BambooRefreshServiceTest.java` — verify `employmentHistoryStatus → EmploymentType` mapping rule (D-03) + auto-populate `JobTitleConfig` rows (D-09)
- [ ] `src/test/java/com/wfm/integration/HttpBambooHRClientTest.java` — 503 → `BambooHRRateLimitedException` with `retryAfterSeconds` populated from `Retry-After` header (D-20)
- [ ] `src/test/java/com/wfm/service/SolverServiceTest.java` — `agentDaysOffMap` filter: only `APPROVED` PTO blocks, mandatory holidays still hard-block, non-schedulable agents excluded from candidates (D-11, D-22)
- [ ] `src/test/java/com/wfm/service/DeskAssignmentUploadServiceTest.java` — both spreadsheet shapes (6-col legacy + 16-col enriched) detected by header (D-13, D-14); structured `skippedDetails` with `{rowNumber, bamboohrId, name, reason}` (D-18); non-schedulable rejection reason (D-12)
- [ ] `src/test/java/com/wfm/service/JobTitleConfigServiceTest.java` — list/toggle endpoints; uniqueness on `(tenantId, jobTitle)` (D-08)
- [ ] `src/test/java/com/wfm/integration/BambooSyncEventServiceTest.java` — sync-event recording on success + on failure (D-19, D-20)
- [ ] `src/test/resources/sample-data/upload-legacy.xlsx`, `upload-enriched.xlsx`, `upload-mixed-failures.xlsx` — fixture spreadsheets covering accept, reject, and partial-success cases (or programmatic fixture builders in tests)
- [ ] `MockBambooHRClient` parity: extend test fixtures to include `employmentHistoryStatus` so refresh tests don't break when `BambooEmployee` gains the field

---

## Manual-Only Verifications

Frontend has no JS test framework installed (per RESEARCH.md §1). RESEARCH explicitly recommends NOT introducing one in this phase. All UI behaviors are manual-only.

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Employment Type column shows on DeskAgents and dropdown filter (All/Full-time/Part-time) works | DATA-02 / SC-1 | No FE test framework | After refresh, open DeskAgents → confirm column present → select "Part-time" filter → only part-time rows show |
| Non-Schedulable Job Titles section on Configuration page lists distinct titles with toggle | DATA-03 / SC-2 | No FE test framework | After refresh, open Configuration → section lists titles → toggle a title → reload → state persisted → run solve → those agents excluded from results and from desk allocation candidates |
| Upload result modal opens with summary + expandable skipped rows + CSV export | DATA-01 / SC-3 | No FE test framework | Upload mixed-failures spreadsheet → modal opens → counts correct → expand skipped → each row shows row#/id/name/reason → click CSV → file downloads with structured rows |
| Existing manual per-agent desk assignment endpoint still works | DATA-01 / SC-3 | No FE test framework | Assign a single agent to a desk via existing UI button → row updates → backend test for endpoint passes too |
| BambooHR Sync Status card shows last sync timestamp, result, counts, and 503 error message | DATA-01 / SC-4 | No FE test framework | Trigger a refresh while BambooHR returns 503 (Mock client toggle or stub) → card shows "Rate-limited — retry in N seconds" |
| Requested-PTO badge appears on DeskAgents rows with count > 0; tooltip lists dates | DATA-01 / SC-5 | No FE test framework | Find an agent with `requested` PTO → row shows "N pending PTO request(s)" badge → hover → tooltip lists dates |

---

## Validation Sign-Off

- [ ] All backend tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify (backend tasks only — UI-only tasks chain to a manual gate)
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s for backend; UI walkthrough captured before `/gsd-verify-work`
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
