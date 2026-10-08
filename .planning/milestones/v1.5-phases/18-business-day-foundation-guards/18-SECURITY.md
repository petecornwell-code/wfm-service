---
phase: "18"
slug: "business-day-foundation-guards"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-09-30"
---

# Phase 18 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.
> Built by `/gsd-secure-phase 18` (State B) from the `<threat_model>` blocks in all six PLAN files.
> `register_authored_at_plan_time: true` — every plan carried a parseable threat model, so this is
> mitigation verification, not retroactive STRIDE.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| browser → `PUT /api/v1/desks/{deskId}/day-start` | Untrusted `deskId` path variable and untrusted `dayStart` body value | Desk identifier, time-of-day value |
| application → live Postgres | `dev` is the live system; V53's DDL and backfill execute against real tenant rows | Desk and timeslot rows for all tenants |
| tenant A → tenant B | One tenant's request must not reach another tenant's desk row or schedule | Desk config, schedule metadata |
| uploaded spreadsheet → `FteUploadService` → `generateTimeslots` | Generation increment inferred from untrusted uploaded columns | Period bounds, window times, increment |
| operator's screen → operator's understanding | The disclosure cell is the only place the `00:00`-only restriction is stated pre-request | Scheduling configuration copy |
| build → the guard's verdict | A green suite is taken as evidence; a vacuous suite is indistinguishable from a covering one | Test outcomes |
| test resources → compiled source sets | A deliberately offending `.java` fixture must never be compiled | Synthetic source |
| CI host → Docker daemon | Whether the Postgres-backed class runs or silently skips depends on a daemon outside the build | Test execution state |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-18-01 | Tampering / EoP | `DeskService.setDayStart` | high | mitigate | `deskRepository.findByIdAndTenantId(deskId, TenantContext.getTenantId())` → `EntityNotFoundException`; never `findById` | closed |
| T-18-02 | Tampering (input validation, ASVS V5) | `DeskService.setDayStart` | high | mitigate | Null and non-`00:00` both rejected as `IllegalArgumentException` **before** the tenant lookup | closed |
| T-18-03 | Tampering (data integrity) | V53 against live `dev` data | high | mitigate | `ADD COLUMN business_date DATE` → `UPDATE … SET business_date = date` → `SET NOT NULL`, all three in one migration; zero-length nullable window | closed |
| T-18-04 | Denial of Service | V53 backfill on live `timeslot` | medium | mitigate | Single set-based `UPDATE`, not a row loop; deploy-terminates-in-flight-solve recorded in `18-01-SUMMARY.md` | closed |
| T-18-05 | Information Disclosure | `DeskResponse.dayStart` | low | accept | Scheduling config already visible to any operator authorised for the desk; no credential or personal data | closed (accepted) |
| T-18-06 | Repudiation | day-start changes | low | accept | No audit-log facility in this project; exposure bounded because 18-02's refusal makes the hazardous transition impossible, not merely attributable | closed (accepted) |
| T-18-SC | Tampering (supply chain) | dependency set | high | mitigate | `build.gradle` diff over the phase touches no `dependencies`/`repositories`/`plugins` block; no `package.json` change | closed |
| T-18-02-01 | Tampering (data integrity) | accepted-schedule refusal | high | mitigate | Unconditional `ConflictException` when an ACCEPTED schedule exists, after the no-op check and before the save; no bypass flag exists | closed |
| T-18-02-02 | Tampering / EoP | `ScheduleRepository.findByTenantIdAndDeskIdAndStatus…` | high | mitigate | Finder takes `tenantId` first, called only with `TenantContext.getTenantId()` | closed |
| T-18-02-03 | Information Disclosure | `ConflictException` message | low | accept | Names a schedule on the same tenant-scoped desk the caller already resolved | closed (accepted) |
| T-18-02-04 | Spoofing (misleading UI) | Day Start disclosure cell | medium | mitigate | Plain read-only `<td>`, no input, no handler; backend remains the real gate. **Verified live 2026-09-30**: 0 focusable descendants in display and edit mode across 4 desks | closed |
| T-18-02-05 | Repudiation | unenforced copy requirement (D-28) | low | accept | Knowingly accepted; held by the UAT item, now executed and passed in `18-UAT.md` | closed (accepted) |
| T-18-02-SC | Tampering (supply chain) | dependency set | high | mitigate | Frontend task changed two existing source files only; no `package.json`/lock change | closed |
| T-18-03-01 | Tampering (data integrity) | `generateTimeslots` obsolete-slot deletion | high | mitigate | `slotKey` stays keyed on the CALENDAR date (source comment at `TimeslotGeneratorService.java:211`) so survivor map and generated keys are built identically; `isDesired` takes the anchor | closed |
| T-18-03-02 | Tampering (input validation) | `requireDayStartTiles` | medium | mitigate | Refuses null / non-tiling anchor at line 82, before `TenantContext.getTenantId()` and before any repository call | closed |
| T-18-03-03 | Denial of Service | the generation walk | medium | mitigate | Iteration count still bounded by caller period and increment; chunked deletion untouched | closed |
| T-18-03-04 | Information Disclosure | widened calendar read-back | medium | mitigate | Widened fetch still goes through the tenant-and-desk-scoped finder | closed |
| T-18-03-05 | Tampering | new `DayWindow` functions | low | accept | Pure statics, no I/O, no state, no reflection; out-of-range throws | closed (accepted) |
| T-18-03-SC | Tampering (supply chain) | `build.gradle` | high | mitigate | Only change is `options.compilerArgs << '-Xlint:deprecation'` in a `tasks.withType(JavaCompile)` block — no dependency, repository or plugin | closed |
| T-18-04-01 | Tampering (integrity of safety net) | the constructed suite | high | mitigate | `MidnightBoundaryFixtureLoadsTest` class-load validator asserts `ALL_SCENARIOS` and `STRUCTURAL_PREDICATES` | closed |
| T-18-04-02 | Tampering (silent scope loss) | the flip registry | high | mitigate | Two-directional set equality over `@AssertsTodaysBehaviour` methods resolved by reflection | closed |
| T-18-04-03 | Repudiation (unfalsifiable expected value) | per-constraint match counts | high | mitigate | Exact integers argued from constraint definitions, no tolerance band; constraint name asserted present before its count is read | closed |
| T-18-04-04 | Denial of Service (suite runtime) | the evaluation harness | medium | mitigate | No `.solve(` and no `SolverManager` anywhere in the suite; `SolverFactory` appears only to construct a `SolutionManager` for `update`/`explain`. No search, no time budget, no seed | closed |
| T-18-04-05 | Information Disclosure | fixture data | low | accept | Constructed constants with deterministic ids; no live tenant data | closed (accepted) |
| T-18-04-SC | Tampering (supply chain) | dependency set | high | mitigate | No `build.gradle` dependency change in this plan's tasks | closed |
| T-18-05-01 | Tampering (silent non-join downstream) | business-date writer set | high | mitigate | Two-directional set equality over a comment-stripped scan of `src/main/java`; 3 `containsExactlyInAnyOrder` assertions | closed |
| T-18-05-02 | Tampering (integrity of the guard) | `BusinessDateWritePathGuardTest` | high | mitigate | Five guard-the-guard proofs retained | closed |
| T-18-05-03 | Tampering (migration vs entity drift) | V53 under `ddl-auto: validate` | high | mitigate | `PostgresBackedTest` runs every migration in order on real Postgres 16 with `flyway.enabled=true`, `ddl-auto=validate` | closed |
| T-18-05-04 | Repudiation (a vacuous green) | `disabledWithoutDocker` | high | mitigate | JUnit XML asserted at execution time: `tests="6" skipped="0" failures="0" errors="0"`. **Point-in-time, not a standing control** — see Residual Risks | closed |
| T-18-05-05 | Information Disclosure | Testcontainers container | low | accept | Ephemeral, migration-created schema and test rows only, reaped at JVM exit | closed (accepted) |
| T-18-05-06 | Denial of Service | shared singleton container | medium | accept | Started once per JVM, shared across subclasses — documented fix for the cross-class port collision | closed (accepted) |
| T-18-05-SC | Tampering (supply chain) | dependency set | high | mitigate | Testcontainers and the Postgres driver already declared and pinned; plan does not touch `build.gradle` | closed |
| T-18-06-01 | Tampering (guard becomes decoration) | comparison set-equality assertion | high | mitigate | `containsExactlyInAnyOrderElementsOf` in both directions plus a pipeline-level red-proof | closed |
| T-18-06-02 | Tampering (false negatives) | receiver-name heuristic | medium | mitigate | Pattern list, receiver-only rule and three named limitations documented in the parsed resource; matcher-level proof asserts the heuristic cuts both ways | closed |
| T-18-06-03 | Tampering (poisoned source tree) | synthetic offending file | high | mitigate | `COMPARISON_OFFENDER_ROOT` resolves under `src/test/resources`; `OffendingSample.java` absent from both compiled source sets (verified by `find`) | closed |
| T-18-06-04 | Repudiation (silently disarmed assertion) | parameterised scan root | high | mitigate | `SOURCE_ROOT = Path.of("src","main","java")` is a named constant every real assertion passes explicitly | closed |
| T-18-06-05 | Information Disclosure | fixture and allowlist | low | accept | Fully qualified class names and one synthetic line from this repository | closed (accepted) |
| T-18-06-SC | Tampering (supply chain) | dependency set | high | mitigate | Type-aware parser rejected precisely to avoid a new build dependency | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above `workflow.security_block_on` (high) count toward `threats_open`*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-18-01 | T-18-05 | Day-start is scheduling config already visible to any operator authorised for the desk | plan 18-01 author | 2026-09-30 |
| R-18-02 | T-18-06 | No audit-log facility exists; 18-02's refusal makes the hazardous transition impossible rather than merely attributable | plan 18-01 author | 2026-09-30 |
| R-18-03 | T-18-02-03 | `ConflictException` message cannot carry a cross-tenant identifier | plan 18-02 author | 2026-09-30 |
| R-18-04 | T-18-02-05 | D-28: no frontend test framework (Phase 13 P-11); source scan rejected as brittle; Phase 19 deletes the copy. Held by a UAT item, now executed and passed | plan 18-02 author | 2026-09-30 |
| R-18-05 | T-18-03-05 | `DayWindow` additions are pure statics with validated inputs | plan 18-03 author | 2026-09-30 |
| R-18-06 | T-18-04-05 | Fixture holds constructed constants only — no live tenant data | plan 18-04 author | 2026-09-30 |
| R-18-07 | T-18-05-05 | Testcontainers container is ephemeral and holds no live tenant data | plan 18-05 author | 2026-09-30 |
| R-18-08 | T-18-05-06 | Shared singleton container is the documented fix for cross-class port collision | plan 18-05 author | 2026-09-30 |
| R-18-09 | T-18-06-05 | Fixture and allowlist hold only class names and one synthetic line | plan 18-06 author | 2026-09-30 |

---

## Residual Risks

Not open threats — mitigations that verified as present but whose reach is narrower than the
register's wording might suggest. Recorded so a later phase does not assume more coverage than exists.

- **T-18-05-04 is a point-in-time check, not a standing guard.** The mitigation reads "assert
  against the JUnit XML report that the class ran with `skipped="0"`". That assertion was performed
  and evidenced by the executor (`18-05-SUMMARY.md` records `tests="6" skipped="0" failures="0"
  errors="0"` for `MidnightTimeslotPostgresTest`), but **no code in the repository asserts it**. A
  grep for `skipped="0"` across `src/test/java` and `build.gradle` returns nothing. On any future
  run where the Docker daemon is unreachable, `@Testcontainers(disabledWithoutDocker = true)` will
  skip the whole class and the build will still report green — the exact V39 failure mode the threat
  names. GitHub Actions `ubuntu-latest` provides Docker, so CI is covered today; a developer running
  `./gradlew build` without Docker is not. Closing this permanently means a standing assertion, not
  another manual observation.
- **The plan asked for "at least seven tests"; the class has six.** `18-05-SUMMARY.md` documents this
  as a plan self-inconsistency (the same plan's `acceptance_criteria` said "at least 6"). Noted so the
  discrepancy is not rediscovered as a defect.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-30 | 38 | 38 | 0 | `/gsd-secure-phase 18` (orchestrator, L1 grep-depth short-circuit) |

**Method.** `register_authored_at_plan_time: true` and `asvs_level: 1` with `threats_open: 0`, so the
Step 3 short-circuit applied and no `gsd-security-auditor` was spawned — L1 grep depth is sufficient
by the workflow's own rule. Each `mitigate` disposition was checked against the implementation;
each `accept` disposition is recorded in the Accepted Risks Log above.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
