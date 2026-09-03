---
phase: 16
slug: usual-shift-storage
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-09-03
---

# Phase 16 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Seeded by plan-phase from `16-RESEARCH.md` § Validation Architecture.
> The per-task map is filled by `/gsd-validate-phase` once plans exist.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ + Spring `@DataJpaTest` / `@SpringBootTest` |
| **Config file** | `src/test/resources/application-test.yml` (H2, `flyway.enabled: false`, `ddl-auto: create-drop`) for the default suite; `src/test/java/com/wfm/support/PostgresBackedTest.java` overrides via `@DynamicPropertySource` to a real Postgres 16 Testcontainer with `flyway.enabled: true`, `ddl-auto: validate` |
| **Quick run command** | `./gradlew test --tests "com.wfm.service.*UsualShift*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~490 seconds full suite (590 tests, baseline from `15-*/HANDOFF.md`) |

---

## Sampling Rate

- **After every task commit:** Run the scoped `--tests` command for the file the task touches
- **After every plan wave:** Run `./gradlew test` — mandatory here because USHF-05 / XCUT-02 are
  cross-cutting: a change to `clearDesk` or `removeDeskAgent` risks regressing existing tests
  (`DeskAssignmentUploadMultiSheetTest`, `DeskAgentServiceDayHoursTest`) that assert on those exact
  methods' current behaviour
- **Before `/gsd-verify-work`:** Full suite green, **plus** a manual Excel open-and-inspect of a
  generated template carrying the new dropdown — a POI round-trip test cannot catch Excel-side
  corruption from the explicit-list 255-character limit
- **Max feedback latency:** ~30 seconds for the scoped run; ~490 seconds for the full suite

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| *(filled by `/gsd-validate-phase` once PLAN.md files exist)* | | | | | | | | | ⬜ pending |

### Requirement → test map (from RESEARCH.md, pre-plan)

| Req ID | Behavior | Test Type | Automated Command | File Exists |
|--------|----------|-----------|-------------------|-------------|
| USHF-01 | Stored usual shift references a valid, active desk-scoped template | unit (H2) | `./gradlew test --tests DeskAgentServiceUsualShiftTest` | ❌ W0 |
| USHF-01 | `agent_usual_shift` migration is entity-consistent (the G-14-1 `CHAR`/`varchar` bug class) | Postgres-backed | `./gradlew test --tests AgentUsualShiftPostgresTest` | ❌ W0 — MUST extend `PostgresBackedTest` |
| USHF-02 | Upload parses seven Usual Shift columns; blank = none (D-07); unknown name skips the cell and warns (D-08) | unit (H2) | `./gradlew test --tests DeskAssignmentUploadUsualShiftTest` | ❌ W0 |
| USHF-02 | Template pre-fill round-trips stored values (D-09); sheet-scoped dropdown attaches without corrupting the workbook | unit (POI re-open) | `./gradlew test --tests DeskAssignmentTemplateServiceUsualShiftTest` | ❌ W0 |
| USHF-03 | Inline write rejects a weekday-mask-excluded template with 400 (D-03); tenant/desk IDOR guard (T-13-05 shape) | unit (H2) | `./gradlew test --tests UsualShiftServiceTest` | ❌ W0 |
| USHF-04 | No stored row resolves to empty / no penalty, not to a default | unit | `./gradlew test --tests UsualShiftResolutionServiceTest` | ❌ W0 |
| USHF-05 | Each of the 7 write paths leaves usual-shift data in its documented state | integration, one test per path; the desk-move / clearDesk pair MUST be Postgres-backed (real FK enforcement) | `./gradlew test --tests "*UsualShiftWritePath*"` | ❌ W0 |
| USHF-05 | Structural completeness guard (D-14) — a new writer without a table row fails the build | structural | `./gradlew test --tests UsualShiftWritePathGuardTest` | ❌ W0 |
| USHF-06 | Roster response carries the resolved usual shift per weekday; all three D-16 states reachable | unit (H2) | `./gradlew test --tests DeskAgentServiceUsualShiftTest` | ❌ W0 |
| USHF-06 | Export gains seven columns at the correct index; First/Last Name shift right by 7 | unit | `./gradlew test --tests DeskAgentExportServiceTest` | ✓ extend existing |

---

## Wave 0 Requirements

- [ ] `src/test/java/com/wfm/service/DeskAgentServiceUsualShiftTest.java` — USHF-01, USHF-03,
      USHF-04, USHF-06 (mirrors `DeskAgentServiceDayHoursTest`'s `@DataJpaTest` + `@Import` style)
- [ ] `src/test/java/com/wfm/support/AgentUsualShiftPostgresTest.java` — extends
      `PostgresBackedTest`; covers the migration-vs-entity drift risk (G-14-1 class)
- [ ] `src/test/java/com/wfm/service/DeskAssignmentUploadUsualShiftTest.java` — USHF-02, D-07,
      D-08, D-09, D-11
- [ ] `src/test/java/com/wfm/service/UsualShiftResolutionServiceTest.java` — D-01, D-02, USHF-04
- [ ] The D-14 structural guard test — name and mechanism are a planner decision (RESEARCH.md
      Pitfall 3 documents two options; ArchUnit is confirmed **absent** from `build.gradle`)
- [ ] Framework install: **none** — every framework needed is already a project dependency

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Generated template opens cleanly in Excel with a working Usual Shift dropdown | USHF-02 (D-10) | A POI round-trip test re-reads the file with the same library that wrote it; it cannot detect Excel-side corruption from the explicit-list 255-character limit | Download a per-desk template for a desk with a live shift library, open in Excel, click a Usual Shift cell, confirm the dropdown lists the desk's live template names and no repair prompt appears |
| Roster tile renders all three D-16 states distinguishably | USHF-06 (D-15, D-16) | No frontend test framework exists in this project (Phase 13 P-11) | On a desk with a shift library: set one weekday's usual shift, leave another unset, retire the template behind a third; expand the agent row and confirm the three tiles read differently |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s (scoped) / 490s (full suite)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
