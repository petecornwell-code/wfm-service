---
phase: 16
slug: usual-shift-storage
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: validated
# false: 2 manual-only items (Excel render, roster visual) — no automation in this project can close them
nyquist_compliant: false
wave_0_complete: true
created: 2026-09-03
validated: 2026-09-04
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

| Plan | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | Tests | Status |
|------|-------------|------------|-----------------|-----------|-------------------|-------|--------|
| 16-01 | USHF-01, USHF-03, USHF-06 | T-13-05, T-16-01, T-16-02 | Choke-point write resolves agent and template within tenant+desk scope before any repository call | integration (H2, real repos) | `./gradlew test --tests UsualShiftTracerTest` | 13 | ✅ COVERED |
| 16-01 | USHF-01 (migration/entity drift, G-14-1 class) | — | Real Flyway + `ddl-auto=validate` on real Postgres | Postgres-backed | `./gradlew test --tests AgentUsualShiftPostgresTest` | 5 | ✅ COVERED |
| 16-01 | USHF-01 (migration/entity drift, second mechanism) | — | Declared columns reconcile with entity mappings | structural *(class from 15-01, extended)* | `./gradlew test --tests MigrationEntityConsistencyTest` | 2 | ✅ COVERED |
| 16-01 | USHF-06 (export column indices) | — | Seven columns at 20-26, First/Last displaced to 27/28 | unit *(class from 13-03, extended)* | `./gradlew test --tests DeskAgentExportServiceTest` | 9 | ✅ COVERED |
| 16-02 | USHF-01, USHF-03, USHF-04, USHF-06 | — | D-01 era-following, D-02 retirement-degrades, D-05 advisory, D-12 clear-on-remove | unit (H2) | `./gradlew test --tests DeskAgentServiceUsualShiftTest` | 20 | ✅ COVERED |
| 16-02 | USHF-04 | — | No stored row resolves to empty, never a default | unit | `./gradlew test --tests UsualShiftResolutionServiceTest` | 6 | ✅ COVERED |
| 16-02 | USHF-01 (referential integrity) | — | Deleting a referenced template is refused; retiring it succeeds | unit *(class from 14-03, extended)* | `./gradlew test --tests ShiftTemplateServiceTest` | 37 | ✅ COVERED |
| 16-03 | USHF-02 (template pre-fill + dropdown) | — | Dropdown degrades safely at the 255-char limit and on comma/quote names | unit (POI re-open) | `./gradlew test --tests DeskAssignmentTemplateServiceUsualShiftTest` | 10 | ✅ COVERED |
| 16-03 | USHF-02 (upload parse) | — | D-07 blank valid, D-08 unresolvable skips cell only, P-11 missing headers does NOT clear the desk | unit (H2) | `./gradlew test --tests DeskAssignmentUploadUsualShiftTest` | 11 | ✅ COVERED |
| 16-04 | USHF-05 (all 7 write paths) | — | Refresh / mode-switch / solve leave stored rows byte-identical | integration (H2) | `./gradlew test --tests UsualShiftWritePathTest` | 5 | ✅ COVERED |
| 16-04 | USHF-05 (structural completeness) | — | A new writer without a table row fails the build, by set equality | structural | `./gradlew test --tests UsualShiftWritePathGuardTest` | 6 | ✅ COVERED |
| 16-05 | USHF-06 (frontend) | — | Three D-16 states render distinguishably | **manual** — no frontend test framework | *(see Manual-Only)* | — | ⚠️ MANUAL |

**Test counts.** 76 tests across the **8 classes Phase 16 created**: `UsualShiftTracerTest` (13),
`DeskAgentServiceUsualShiftTest` (20), `DeskAssignmentUploadUsualShiftTest` (11),
`DeskAssignmentTemplateServiceUsualShiftTest` (10), `UsualShiftWritePathGuardTest` (6),
`UsualShiftResolutionServiceTest` (6), `AgentUsualShiftPostgresTest` (5),
`UsualShiftWritePathTest` (5). Three further classes in the table above **pre-date Phase 16 and
were extended by it**, so their totals are not Phase 16's: `ShiftTemplateServiceTest` (37, created
14-03), `DeskAgentExportServiceTest` (9, created 13-03), `MigrationEntityConsistencyTest` (2,
created 15-01). Full suite: 720 tests.

### Validation audit 2026-09-04 — gap found and filled

The pre-plan map named a `UsualShiftServiceTest` covering *"D-03 … **tenant/desk IDOR guard
(T-13-05 shape)**"*. No such class was ever written. Its behaviour landed in
`UsualShiftTracerTest` instead — a naming difference, not a coverage hole, for every clause
**except the tenant one**: all 10 original usual-shift tracer tests, and every other Phase 16
test, ran under a single `TENANT_ID = 1L`. The desk dimension was negatively tested
(`wrongDesk_…`, `crossDeskTemplate_…`); the tenant dimension was not tested at all.

Three tests were added to `UsualShiftTracerTest` (10 → 13), following
`ShiftTemplateTracerTest#list_crossTenant_returnsEmpty`'s established two-tenant shape.

**Each was proven able to fail** by deliberately breaking the guard it pins and confirming it
trips — a green test that cannot fail proves nothing:

| Test | Guard it pins | Deliberate break that trips it |
|------|---------------|-------------------------------|
| `crossTenantClear_cannotDeleteAnotherTenantsStoredUsualShift` | `UsualShiftService` agent lookup, **alone** | drop tenant from `agentRepository.findByIdAndTenantIdAndDeskId` → FAILS |
| `crossTenantWrite_throwsEntityNotFound_andWritesNoRow` | agent **and** template lookups together | drop tenant from the agent lookup alone → still passes (the template guard backstops it); drop both → FAILS |
| `crossTenantRead_doesNotSeeAnotherTenantsStoredUsualShift` | `DeskAgentService.getDeskAgentResponse` | drop tenant from the roster read → FAILS |

The middle row is the finding worth keeping: on the **`clearRow=true` path the template lookup is
skipped entirely**, so the agent-lookup tenant scope is the *only* thing standing between another
tenant and a destructive delete. `crossTenantClear` is the sole test pinning it. Both source files
were restored to a clean diff after each break.

### Requirement → test map (from RESEARCH.md, pre-plan — superseded by the table above; kept for provenance)

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

- [x] `src/test/java/com/wfm/service/DeskAgentServiceUsualShiftTest.java` — USHF-01, USHF-03,
      USHF-04, USHF-06 (mirrors `DeskAgentServiceDayHoursTest`'s `@DataJpaTest` + `@Import` style)
- [x] `src/test/java/com/wfm/repository/AgentUsualShiftPostgresTest.java` — extends
      `PostgresBackedTest`; covers the migration-vs-entity drift risk (G-14-1 class)
- [x] `src/test/java/com/wfm/service/DeskAssignmentUploadUsualShiftTest.java` — USHF-02, D-07,
      D-08, D-09, D-11
- [x] `src/test/java/com/wfm/service/UsualShiftResolutionServiceTest.java` — D-01, D-02, USHF-04
- [x] The D-14 structural guard test — name and mechanism are a planner decision (RESEARCH.md
      Pitfall 3 documents two options; ArchUnit is confirmed **absent** from `build.gradle`)
- [x] Framework install: **none** — every framework needed is already a project dependency

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Status | Evidence / Instructions |
|----------|-------------|------------|--------|-------------------------|
| Generated template opens cleanly in Excel with a working Usual Shift dropdown | USHF-02 (D-10) | A POI round-trip test re-reads the file with the same library that wrote it; it cannot detect Excel-side corruption from the explicit-list 255-character limit | ⛔ **OPEN** — `16-UAT.md` test 3, `blocked_by: third-party` | Everything short of Excel itself is measured on the live deployed template: 7 `dataValidation` blocks at `O2:U1048576`, each `formula1` 104 chars against the 255 limit (151 spare), retired templates excluded, pre-fill round-trips. Remaining: open `~/Downloads/wfm-desk-assignment-template-2026-09-03.xlsx` in real Excel, click a Usual Shift cell, confirm the dropdown lists live names and no repair prompt appears |
| Roster tile renders all three D-16 states distinguishably | USHF-06 (D-15, D-16) | No frontend test framework exists in this project (Phase 13 P-11) | ✅ **DISCHARGED** 2026-09-03 — `16-UAT.md` test 4 | Verified in a live browser by driving all three states onto one agent row and reading `getComputedStyle` from the DOM rather than eyeballing: A `#d1d5db`/400/upright, B `#3b82f6`/600/upright, C `#9ca3af`/400/italic — distinct by colour AND weight AND slant, matching `16-UI-SPEC.md` §1. Clipping real (scrollWidth 96 > 90) with the full value in `title` |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references — every file listed above now exists
- [x] No watch-mode flags
- [x] Feedback latency: ~9s scoped (`UsualShiftTracerTest`) / ~610s full suite (720 tests)
- [ ] `nyquist_compliant: true` — **NOT set.** Two Manual-Only items remain and no automation in
      this project can close them: Excel-side rendering (a POI round-trip re-reads with the same
      library that wrote it) and the roster visual (no frontend test framework exists — Phase 13
      P-11). Both were reduced as far as automation allows: the dropdown's 255-char headroom is
      measured (104 chars used, 151 spare) and the three roster states were verified in a live
      browser against computed styles during UAT.

**Approval:** validated 2026-09-04 — PARTIAL (automated coverage complete; 2 manual-only items)
