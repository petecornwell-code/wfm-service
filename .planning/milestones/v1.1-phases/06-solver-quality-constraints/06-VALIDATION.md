---
phase: 6
slug: solver-quality-constraints
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-02
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Scope: re-scoped to "PTO & Weekends" (QUAL-01 day-off data foundation).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (via `spring-boot-starter-test`) |
| **Config file** | `build.gradle` — `testImplementation 'org.springframework.boot:spring-boot-starter-test'` |
| **Quick run command** | `./gradlew test --tests "com.wfm.integration.WorkingDaysParserTest"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~5s quick / full suite per project norm |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.wfm.integration.WorkingDaysParserTest"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~30 seconds (quick parser run)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| W0 | — | 0 | — | T-6-SEC | Rotated BambooHR API key not in VCS/chat | manual | Confirm key rotated + stored in secret manager | ❌ W0 | ⬜ pending |
| Parser-all-formats | parser | 1 | QUAL-01 | — | Parser maps all live BambooHR formats → working days | unit | `./gradlew test --tests "com.wfm.integration.WorkingDaysParserTest"` | ❌ W0 | ⬜ pending |
| Parser-data-gap | parser | 1 | QUAL-01 | — | null/blank/"Variable" → empty Optional | unit | same | ❌ W0 | ⬜ pending |
| Parser-week-wrap | parser | 1 | QUAL-01 | — | `Fri-Tue` expands across week boundary | unit | same | ❌ W0 | ⬜ pending |
| Parser-annotation | parser | 1 | QUAL-01 | — | `Mon - Sun HOOP` trailing token stripped | unit | same | ❌ W0 | ⬜ pending |
| Mandatory-rows | refresh | 2 | QUAL-01 | — | Off-days produce MANDATORY rows within schedule window; idempotent on re-refresh | unit/integration | `./gradlew test --tests "com.wfm.integration.BambooRefreshServiceTest"` | ✅ extend | ⬜ pending |
| Solver-blocks | refresh | 2 | QUAL-01 | — | MANDATORY rows block solver via `buildAgentDaysOffMap` | unit | `./gradlew test --tests "com.wfm.service.SolverServicePtoFilterTest"` | ✅ exists | ⬜ pending |
| Data-gap-exclusion | exclusion | 2 | QUAL-01 | — | Variable/blank agents excluded from `filterEligible` | unit | `./gradlew test --tests "com.wfm.service.SolverServiceEligibilityFilterTest"` | ✅ extend | ⬜ pending |
| Mock-emits | refresh | 2 | QUAL-01 | — | `MockBambooHRClient` emits `customWorkingdays`-style value | unit | `./gradlew test --tests "com.wfm.integration.BambooRefreshServiceTest"` | ✅ extend | ⬜ pending |
| Pto-tab-render | ui-verify | 3 | QUAL-01 | — | PTO tab renders MANDATORY rows red within window | manual | Navigate ScheduleResults → PTO tab post-refresh | n/a | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/wfm/integration/WorkingDaysParserTest.java` — new file; `@ParameterizedTest` table covering all live BambooHR value formats (QUAL-01), incl. week-wrap, annotation, "to" form, comma list, and data-gap (null/blank/Variable → empty)
- [ ] BambooHR API key rotation confirmed (key was exposed in chat 2026-06-02) — blocking before any deploy

*All other test infrastructure covers phase requirements. `SolverServicePtoFilterTest` already passes and validates the downstream consumer.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| PTO tab renders MANDATORY weekend rows | QUAL-01 (D-10) | UI already built; phase guarantees data lands in window | Run a refresh, generate a schedule, open ScheduleResults → PTO tab, confirm weekend cells show red "MANDATORY" within the schedule window |
| Desk-scale coverage of `Working days` field | QUAL-01 (specifics risk) | Requires live BambooHR data for scheduled desks | Before shipping D-07 exclusion, confirm StubHub-GE / Vinted-UA agents are not mostly blank/Variable (would gut the schedule) |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
