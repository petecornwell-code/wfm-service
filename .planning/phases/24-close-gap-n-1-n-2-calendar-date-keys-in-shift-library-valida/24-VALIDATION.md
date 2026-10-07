---
phase: "24"
slug: "close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida"
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: "2026-10-07"
---

# Phase 24 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ + Spring `@DataJpaTest` (H2, profile `test`); frontend has no test runner — `tsc` only |
| **Config file** | `build.gradle` (`tasks.named('test') { useJUnitPlatform() ... }`); `frontend/tsconfig.json` |
| **Quick run command** | `./gradlew test --tests <FQCN> [--tests <FQCN> ...]` (targeted; never read the suite aggregate after a filtered run) |
| **Full suite command** | `./gradlew --stop && ./gradlew cleanTest test` |
| **Type check** | `frontend/node_modules/.bin/tsc --noEmit -p frontend/tsconfig.json` (repo-root-relative; equivalent to `cd frontend && npx tsc --noEmit -p tsconfig.json`) |
| **Estimated runtime** | ~60-120 s targeted; ~14 min full suite (warm) |

---

## Sampling Rate

- **After every task commit:** `./gradlew compileJava compileTestJava` plus the targeted class(es) for that task
- **After every plan wave:** all classes touched in the wave in one `--tests` invocation; `tsc` after any frontend edit
- **Before `/gsd-verify-work`:** full suite green (bare `> Task :test`, not `UP-TO-DATE`; aggregate XML by filename incl. `@Nested`), `tsc` clean, live check recorded
- **Max feedback latency:** ~120 seconds (targeted)

---

## Per-Task Verification Map

Populated from 24-RESEARCH.md §Validation Architecture; task IDs bound by the planner (2026-10-07).
Plans run sequentially: 24-01 (wave 1) → 24-02 (wave 2) → 24-03 (wave 3).
`BusinessDateJoinGuardTest` is EXPECTED RED from 24-01-T1 until 24-02-T3 (precedent: Phases 18-20).

| Task ID | Req / Decision | Behavior | Test Type | Automated Command | File Exists | Status |
|---------|----------------|----------|-----------|-------------------|-------------|--------|
| 24-01-T1 | D-07 / BDAY-05 | Widened guard red on the 14 pre-fix lines, captured before any production edit | unit (textual scan) | `! ./gradlew test --tests "com.wfm.service.BusinessDateJoinGuardTest"` + XML grep | edit | ⬜ pending |
| 24-01-T1 | D-08a, D-08c / OVNT-05 | Weekday-restricted overnight template accepts its own post-midnight hours; unsatisfiable weekday buckets on business weekday | integration | `./gradlew test --tests "com.wfm.service.ShiftLibraryValidationServiceTest"` | edit (+ `saveDemandAnchored`) | ⬜ pending |
| 24-01-T1 | D-08b / OVNT-05, SOLV-07 | No credit for another business day's hours; `isEffectiveOn` on business date | integration | same | edit | ⬜ pending |
| 24-01-T2 | D-02 / OVNT-07, BDAY-05 | `[calendar …]` disclosure only when calendar ≠ business; day-start ordering; `00:00` strings unchanged | integration | same + `ShiftLibraryGenerationServiceTest`, `MidnightTimeArithmeticGuardTest` | edit | ⬜ pending |
| 24-01-T3 | D-08c / OVNT-05 | Peak shortfall on a post-midnight hour reported on its business weekday; `00:00` peak message unchanged | integration | `./gradlew test --tests "com.wfm.service.ShiftLibraryValidationServiceTest"` | edit | ⬜ pending |
| 24-02-T1 | D-06 envelope | Post-midnight seat finds its own envelope on a 06:00 desk, never the next day's; `00:00` control unchanged | unit | `./gradlew test --tests "com.wfm.service.ScheduleEnvelopeRepairServiceTest"` | edit (fixtures need `setBusinessDate`) | ⬜ pending |
| 24-02-T2 | D-06 consistency + start-mix | Post-midnight seats move with the swap; a post-midnight-only business day still gets targets | unit | `./gradlew test --tests "com.wfm.service.ScheduleConsistencyRepairServiceTest" --tests "com.wfm.service.ShiftStartMixTargetServiceTest" --tests "com.wfm.solver.ShiftStartMixSteerTest"` | edit (fixtures need `setBusinessDate`) | ⬜ pending |
| 24-02-T3 | D-07 / BDAY-05 | Guard green with empty allowlist; widened pipeline liveness proof (W0 offender fixture) | unit (textual scan) | `./gradlew test --tests "com.wfm.service.BusinessDateJoinGuardTest" --tests "com.wfm.service.MidnightTimeArithmeticGuardTest" --tests "com.wfm.service.BusinessDateWritePathGuardTest"` | edit + ❌ W0 offender fixture | ⬜ pending |
| 24-03-T1 | D-03 / SOLV-07 | `Item.businessDate` populated; `date` stays calendar | integration | `./gradlew test --tests "com.wfm.service.StaffingRequirementListBusinessRangeTest"` | ❌ W0 | ⬜ pending |
| 24-03-T1 | D-04 / BDAY-02 | Business range returns the final business day's post-midnight rows | integration | same | ❌ W0 | ⬜ pending |
| 24-03-T2 | D-04 | Paging intact; half/mixed/inverted/malformed ranges → 400; empty range → empty page; tenant-scoped | integration | same + `GlobalExceptionHandlerTest` | ❌ W0 | ⬜ pending |
| 24-03-T3 | D-05, D-09 / OVNT-06 | Rows keyed on `businessDate`; no TS date arithmetic; live cells equal seeded demand | type check + live | `frontend/node_modules/.bin/tsc --noEmit -p frontend/tsconfig.json` | n/a | ⬜ pending |
| 24-03-T3 | Regression / D-10 | Whole suite green on a real run; no VERIFICATION.md touched | full | `./gradlew --stop && ./gradlew cleanTest test` | existing | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/resources/bday-join-guard-offender-widened/OffendingSample.java` — pipeline red-proof for the widened scan
- [ ] `src/test/java/com/wfm/service/StaffingRequirementListBusinessRangeTest.java` — D-03/D-04 backend-first N-2 proof
- [ ] Anchored-demand helper in `ShiftLibraryValidationServiceTest` (explicit `businessDate`)
- [ ] `setBusinessDate(...)` in the POJO `Timeslot` fixtures of the repair-service tests

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Agent Allocation Required / Over-under cells show the right demand for post-midnight slots, including the final business day | OVNT-06, SOLV-07 (N-2) | No frontend test runner (Phase 22 WR-04) | Local recipe only (DB 55432, app 8081, vite 3001) — never dev. Seed a 06:00 desk with post-midnight demand on a middle and the last business day; read cell text via Playwright `browser_evaluate` (no screenshots) and compare with seeded demand |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
