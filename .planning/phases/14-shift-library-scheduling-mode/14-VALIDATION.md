---
phase: 14
slug: shift-library-scheduling-mode
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-25
---

# Phase 14 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ + `@DataJpaTest` with H2 (backend). **No frontend test framework** — `frontend/package.json` has no `test` script and no vitest/jest/testing-library dependency (14-RESEARCH.md Pitfall 4) |
| **Config file** | `build.gradle`; test profile at `src/test/resources/application-test.yml` |
| **Quick run command** | `./gradlew test --tests '*ShiftTemplate*' --tests '*ShiftLibrary*' --tests '*SchedulingMode*' --tests '*ConstraintClassification*'` |
| **Full suite command** | `./gradlew test` |
| **Frontend command** | `cd frontend && npm run build` (`tsc -b && vite build`) — exit code is the only automated frontend signal available |
| **Estimated runtime** | **Not measured at plan time.** No prior artifact records a suite runtime for this project. The first full-suite run in Wave 1 must record it here; until then, "max feedback latency" below is unset rather than guessed. |

> **Load-bearing test-infrastructure fact (P-03).** `application-test.yml` sets
> `spring.jpa.hibernate.ddl-auto: create-drop` and `spring.flyway.enabled: false`. The H2 schema is
> derived from JPA annotations and **`V39__add_shift_template_and_scheduling_mode.sql` is never
> executed by `./gradlew test`.** A green suite therefore says nothing about whether the migration
> applies. Migration correctness is covered by SQL source assertions plus the `<human-check>` on
> 14-01's tracer task, not by the suite.

---

## Sampling Rate

- **After every task commit:** the quick command scoped to that task's new or touched test class
- **After every plan wave:** `./gradlew test` (full suite — this is also literally MODE-05's proof)
- **Before `/gsd-verify-work`:** full suite green **and** `cd frontend && npm run build` exit 0
- **Max feedback latency:** unset — to be filled from the first measured full-suite runtime (see above)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 14-01-01 | 01 | 1 | SHLB-01 | — | Human confirms the one-way D-11 identity migration before it is written | checkpoint:decision | n/a — `gate="blocking-human"`, never auto-approved | n/a | ⬜ pending |
| 14-01-02 | 01 | 1 | SHLB-01, MODE-01 | T-14-01, T-14-02, T-14-03 | Every repository method takes `tenantId`; no bare `findById`; forward-only migration | unit (`@DataJpaTest`, controller→service→repo→H2) + build | `./gradlew test --tests 'com.wfm.service.ShiftTemplateTracerTest'` ; `./gradlew test` ; `cd frontend && npm run build` | ❌ W0 | ⬜ pending |
| 14-02-01 | 02 | 1 | MODE-05 | T-14-07, T-14-08 | No production solver file modified; completeness assertion cannot be silenced by editing a count | unit (reflection, no Spring context) | `./gradlew test --tests 'com.wfm.solver.ScheduleConstraintClassificationTest'` ; `./gradlew test` | ❌ W0 | ⬜ pending |
| 14-02-02 | 02 | 1 | MODE-05 | T-14-09 | Documentation only; no code path | doc assertion | `test -f .planning/phases/14-shift-library-scheduling-mode/XCUT-05-constraint-classification.md` + row-count grep | ❌ W0 | ⬜ pending |
| 14-03-01 | 03 | 2 | SHLB-01, SHLB-02, SHLB-03, SHLB-04 | T-14-10, T-14-11, T-14-12 | Cross-tenant update yields `EntityNotFoundException`; identity + non-overlap applied on both entry points through one path | unit (`@DataJpaTest`, `@MockitoBean TimeslotGeneratorService`) | `./gradlew test --tests 'com.wfm.service.ShiftTemplateServiceTest'` ; `./gradlew test` | ❌ W0 | ⬜ pending |
| 14-03-02 | 03 | 2 | SHLB-01, SHLB-03 | T-14-14 | No `@DeleteMapping` exposed; retirement removes no row | unit (`@DataJpaTest`) | `./gradlew test --tests 'com.wfm.service.ShiftTemplateServiceTest'` ; `./gradlew test` | ❌ W0 | ⬜ pending |
| 14-04-01 | 04 | 3 | SHLB-05, SHLB-06 | T-14-15, T-14-16, T-14-17, T-14-19 | `scheduleId IS NULL` keeps the validator off accepted-schedule snapshots; no UUIDs in operator-facing prose | unit (`@DataJpaTest`, `@MockitoBean TimeslotGeneratorService`) | `./gradlew test --tests 'com.wfm.service.ShiftLibraryValidationServiceTest'` ; `./gradlew test` | ❌ W0 | ⬜ pending |
| 14-04-02 | 04 | 3 | SHLB-05 | T-14-15 | Report endpoint is read-only and never refuses | unit (`@DataJpaTest`, controller imported) | `./gradlew test --tests 'com.wfm.service.ShiftLibraryValidationServiceTest'` ; `./gradlew test` | ❌ W0 | ⬜ pending |
| 14-05-01 | 05 | 4 | MODE-03, MODE-04 | T-14-20, T-14-21, T-14-22, T-14-23 | 409 guard evaluated before the gate and immediately before the write; in-flight solve never stopped; accepted schedule asserted field-by-field | unit (`@DataJpaTest`, `@MockitoBean ShiftLibraryValidationService`) | `./gradlew test --tests 'com.wfm.service.DeskServiceSchedulingModeTest'` ; `./gradlew test` | ❌ W0 | ⬜ pending |
| 14-05-02 | 05 | 4 | MODE-02 | T-14-25 | Typed enum body — an out-of-vocabulary mode fails deserialization before service code runs | unit (`@DataJpaTest`, controller imported) | `./gradlew test --tests 'com.wfm.service.DeskServiceSchedulingModeTest'` ; `./gradlew test` | ❌ W0 | ⬜ pending |
| 14-06-01 | 06 | 5 | SHLB-01, SHLB-02, SHLB-03, SHLB-04 | T-14-26, T-14-29 | Era read from `eraStatus`, never re-derived client-side; no destructive control, no confirmation dialog | build + source assertion | `cd frontend && npm run build` + the plan's source greps | n/a (no FE framework) | ⬜ pending |
| 14-06-02 | 06 | 5 | SHLB-05, SHLB-06 | T-14-26, T-14-28, T-14-30 | Coverage and hours verdicts read from the report, never recomputed in the browser | build + source assertion | `cd frontend && npm run build` + the plan's source greps | n/a (no FE framework) | ⬜ pending |
| 14-06-03 | 06 | 5 | MODE-02 | T-14-27 | Optimistic toggle is display-only and reverts on error; mode is not editable from Desk Management | build + source assertion + `<human-check>` | `cd frontend && npm run build` + the plan's source greps | n/a (no FE framework) | ⬜ pending |

---

## Wave 0 Requirements

Every backend test class in this phase is net-new. None exists today; each is created by the task that
depends on it, written failing first (`tdd="true"` on every code-producing backend task).

- [ ] `src/test/java/com/wfm/service/ShiftTemplateTracerTest.java` — the end-to-end tracer proof
      (14-01-02). Modelled on `JobTitleConfigServiceTest`'s `@DataJpaTest` + `@Import` +
      `@ActiveProfiles("test")` + explicit `TenantContext.setTenantId` shape. **`Specialization` has no
      test file to mirror** — 14-RESEARCH.md Pitfall 2.
- [ ] `src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java` — XCUT-05 completeness
      (14-02-01). The one genuinely new *kind* of test in this phase: it reflects over
      `ScheduleConstraintProvider` and `ConstraintWeights`, which nothing else in the suite does.
- [ ] `src/test/java/com/wfm/service/ShiftTemplateServiceTest.java` — SHLB-01…04 (14-03).
- [ ] `src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java` — SHLB-05, SHLB-06,
      MODE-03 (14-04).
- [ ] `src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java` — MODE-01…04 (14-05).
- [ ] Framework install: **none needed** — JUnit 5, AssertJ, Spring Boot Test, `timefold-solver-test`
      and H2 are already declared in `build.gradle`.
- [ ] Frontend framework install: **none, deliberately** — P-26 carries Phase 13's precedent forward.

### Test-harness constraints the executor must honour

- `TimeslotGeneratorService.getLiveBounds` runs a Postgres-specific native query
  (`EXTRACT(EPOCH FROM ...)`) that cannot execute under H2. Supply it as a `@MockitoBean` in
  `ShiftTemplateServiceTest` and `ShiftLibraryValidationServiceTest`; never import the real bean.
- There is no MockMvc or web-layer harness in this codebase (`GlobalExceptionHandlerTest` is a plain
  instantiation test; Phase 13 recorded P-17 "no new MockMvc harness"). Controller-level proof is a
  `@DataJpaTest` that `@Import`s the controller alongside its service and calls the controller methods
  directly.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `V39` applies cleanly against the dev Postgres | SHLB-01, MODE-01 | The suite runs H2 with `ddl-auto: create-drop` and Flyway disabled, so the migration is never executed by any test | 14-01's tracer `<human-check>`: start the app against dev, confirm Flyway logs "Migrating schema ... to version 39", `\d shift_template` shows eleven columns and the `(tenant_id, desk_id, name, effective_from)` unique constraint, and `SELECT DISTINCT scheduling_mode FROM desk;` returns only `SLOT` |
| End-to-end mode-switch refusal, retry, reversal, 409 and advisory flows | SHLB-05, SHLB-06, MODE-02, MODE-03, XCUT-01 | No frontend test framework exists (14-RESEARCH.md Pitfall 4); these are interaction flows across page state and three endpoints | 14-06 Task 3's `<human-check>`, seven numbered steps |
| Era grouping reads as legible eras, not accidental duplicates | SHLB-03 | Purely visual claim; unverifiable without a frontend harness | UI-SPEC E1 populated — `backstop` truth in 14-06 |
| Long template names and many eras per name wrap/scroll without breaking layout | SHLB-01, SHLB-03 | Purely visual | UI-SPEC E1 overflow + E1 long-text + E2 long-text — `backstop` truths in 14-06 |
| The uncovered-windows list reads clearly at a realistic count | SHLB-05 | Visual density claim | UI-SPEC E3 populated — `backstop` truth in 14-06 |
| The SHLB-06 advisory sentence renders legibly in a native OS tooltip | SHLB-06 | The tooltip is rendered by the OS, outside this codebase's test surface | UI-SPEC E5 long-text — `backstop` truth in 14-06 |

> The six `backstop` items above are marked so deliberately. At verify time, with no explicit evidence
> wired, they abstain to `human_needed` (reason `insufficient_spec`) rather than passing silently — that
> is honest-verifier behaviour, not over-flagging.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or a Wave 0 dependency — **satisfied**: every task carries an
      automated command; the one checkpoint task is a `blocking-human` decision gate, not a verification
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify — **satisfied**: every one of
      the 12 non-checkpoint tasks carries one
- [ ] Wave 0 covers all MISSING references — **satisfied**: five net-new test classes enumerated above,
      each created by the task that needs it
- [ ] No watch-mode flags — **satisfied**: no `--watch`, no `vite dev`, no continuous runner anywhere
- [ ] Feedback latency under the measured full-suite runtime — **unset pending the first measurement**
- [ ] `nyquist_compliant: true` set in frontmatter — pending

**Approval:** pending
