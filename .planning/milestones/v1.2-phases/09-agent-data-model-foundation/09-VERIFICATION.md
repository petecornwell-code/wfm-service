---
phase: 09-agent-data-model-foundation
verified: 2026-08-21T14:00:00Z
status: passed
score: 4/4 must-haves verified
behavior_unverified: 0
overrides_applied: 0
---

# Phase 9: Agent Data Model Foundation Verification Report

**Phase Goal:** Agent stores first/last name separately and per-day contracted hours, so the solver's `AgentDayConfig.effectiveHours` resolution composes with existing `AgentException` per-date overrides without changing solve behaviour for agents whose hours are uniform across worked days.
**Verified:** 2026-08-21T14:00:00Z
**Status:** passed
**Re-verification:** No — initial verification (retroactive; phase was executed 2026-07-30 but never formally verified before Phases 10/11 were built on top of it)

## Goal Achievement

### Observable Truths

| # | Truth (ROADMAP Success Criterion) | Status | Evidence |
|---|-------|--------|----------|
| 1 | Every agent record shows first name and last name as separate fields, not a single combined `name` | ✓ VERIFIED | `Agent.java:31-35` has `@Column(name="first_name") private String firstName` / `@Column(name="last_name") private String lastName` with getters/setters (lines 95-99). `AgentResponse`, `DeskAgentResponse`, and the Excel export (`DeskAgentExportService.java:32,59-60`, cols 13/14) all expose the split fields, populated from `agent.getFirstName()/getLastName()` at every construction site (`AgentService.java:57`, `ClientManagementService.java:339-340`, `DeskAgentService.java:80`). Combined `name` is still populated (D-08) so no API break. `AgentNameSplitterTest` (10 tests) and `AgentNamePersistenceTest` (2 tests) pass. |
| 2 | Agent stores contracted hours per day of the week (Mon–Sun); `AgentDayConfig` resolves effective hours per date from these per-day values rather than a single scalar | ✓ VERIFIED | `AgentDayHours.java` is a `@ManyToOne` child entity (`agent_id`, STRING-enum `day_of_week`, `NUMERIC(5,2) NOT NULL hours`), mirroring `AgentDayOff`/`AgentException`. `SolverService.resolveEffectiveHours` (line 913) is the sole resolution choke point and the agent's scalar `contractedHoursPerDay` is *never read* in the resolution path (old `getEffectiveHours` instance method was deleted per 09-03-SUMMARY). All 3 former call sites (`computeAgentDayConfigs` line 579, and both `runPreSolveValidation` checks lines 750 and 833) now call `resolveEffectiveHours` against a per-agent `Map<UUID, Map<DayOfWeek, BigDecimal>>` built from `AgentDayHoursRepository.findByTenantIdAndDeskId`. `SolverServiceEffectiveHoursResolutionTest` (5 tests, all pass) proves precedence. |
| 3 | Migrating an existing agent produces no data loss: the prior scalar `contractedHoursPerDay` becomes that agent's per-day value on every day they previously worked, and the single `name` splits cleanly into first/last | ✓ VERIFIED | `V29__agent_first_last_name_and_day_hours.sql` fans each non-null-scalar agent's `contracted_hours_per_day` out to all 7 `agent_day_hours` rows (`CROSS JOIN` over weekday literals, `WHERE ... IS NOT NULL`); NULL-scalar agents get zero rows (D-02, falls back to schedule default). Name backfill normalizes via `trim(both E' \t\n\r' from coalesce(name,''))` before `split_part`/`position`/`substring`. This is not merely present SQL — it was **executed against a real seeded Postgres 16 container** (documented in 09-06-SUMMARY.md "Checkpoint Resolution"): 49 `agent_day_hours` rows = 7 × 7 non-null-scalar agents (exact), 0 mismatches, NULL-scalar agents got 0 rows. The name-split was cross-checked byte-for-byte against `AgentNameSplitter.split()` on the same 9 inputs; a divergence (leading-whitespace names, NULL names) was found and **fixed in commit `6af92bd`**, then re-verified matching on all 9 inputs. Commit confirmed present in git log. |
| 4 | The solver produces the same schedule for agents whose contracted hours are uniform across worked days as it did before the migration — no behaviour regression for the common case | ✓ VERIFIED | `SolverServiceEffectiveHoursResolutionTest.uniformDayHoursMap_allSevenWeekdays_returnsExactValueForEveryDate` calls the actual production `resolveEffectiveHours` method (not a mock) with a uniform 7-weekday map (all days = 7.50) and an empty exception map, and asserts the exact value V is returned for every date across a full week — byte-identical to what the old scalar-only `getEffectiveHours` produced for a uniform agent. Since (a) the migration (Truth 3) guarantees every non-null-scalar agent gets exactly this uniform 7-row shape, and (b) `resolveEffectiveHours` is the single method feeding all 3 call sites into the solver (Truth 2), this is direct behavioral proof of the equivalence claim at the actual choke point, not a presence/wiring inference. `DeskAgentServiceContractedHoursTest` additionally proves the D-10 "fan-out stays durable" invariant (operator edits keep affecting the solver post-migration) with 5 passing tests, including the CR-01 null-revert regression fix. |

**Score:** 4/4 truths verified (0 present-but-behavior-unverified)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/util/AgentNameSplitter.java` | Shared D-06 split rule utility | ✓ VERIFIED | Static, stateless, first-whitespace-token rule, handles null/blank/single-token per spec |
| `src/main/java/com/wfm/model/Agent.java` | firstName/lastName columns | ✓ VERIFIED | `first_name`/`last_name` columns present, nullable, alongside existing `name`; `contractedHoursPerDay` scalar retained (D-05, not dropped) |
| `src/main/java/com/wfm/model/AgentDayHours.java` | Per-weekday hours child entity | ✓ VERIFIED | `@ManyToOne` to Agent, STRING-enum `dayOfWeek`, `NUMERIC(5,2) NOT NULL hours`, unique `(agent_id, day_of_week)` |
| `src/main/java/com/wfm/repository/AgentDayHoursRepository.java` | Tenant-scoped fetch + agent-scoped delete | ✓ VERIFIED | `findByTenantIdAndAgent_Id`, `findByTenantIdAndDeskId` (join through `h.agent.deskId`), `deleteByAgent_Id` all present and used |
| `src/main/java/com/wfm/service/SolverService.java` | `resolveEffectiveHours` + 3-call-site migration | ✓ VERIFIED | Package-private static method at line 913; all 3 former `getEffectiveHours` call sites migrated; old scalar-reading instance method deleted |
| `src/main/resources/db/migration/V29__agent_first_last_name_and_day_hours.sql` | Additive DDL + backfill + fan-out | ✓ VERIFIED | 4-step migration, no `DROP COLUMN`, no `CREATE EXTENSION`; verified against real Postgres 16 (see Truth 3) |
| `src/main/java/com/wfm/integration/BambooRefreshService.java` | displayName split on refresh (D-07) | ✓ VERIFIED | `AgentNameSplitter.split(emp.displayName())` called, `setFirstName`/`setLastName` set alongside existing `setName` |
| `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` | Name split at upload write-sites + per-day-hours delete on clear (D-10/D-11) | ✓ VERIFIED | `agentDayHoursRepository.deleteByAgent_Id` present in clear path; `AgentNameSplitter` imported and used |
| `src/main/java/com/wfm/dto/AgentResponse.java`, `DeskAgentResponse.java` | firstName/lastName exposed, combined name retained (D-08/D-12) | ✓ VERIFIED | Both records carry `firstName`/`lastName` fields; no per-day hours surfaced (D-12 honored) |
| `src/main/java/com/wfm/service/DeskAgentService.java` | `setContractedHours` fans out to 7 rows (D-10) | ✓ VERIFIED | Lines 184-220: delete-then-flush-then-insert-7-rows pattern; null input clears rows without crashing (CR-01 fix confirmed) |

**Artifacts:** 10/10 verified

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `SolverService.computeAgentDayConfigs` | `resolveEffectiveHours` | direct call, line 579 | ✓ WIRED | Passes `dayHoursMap` built from `AgentDayHoursRepository` |
| `SolverService.runPreSolveValidation` (rule 5) | `resolveEffectiveHours` | direct call, line 750 | ✓ WIRED | Same map, prevents validation/solve divergence (RESEARCH Pitfall 1) |
| `SolverService.runPreSolveValidation` (rule 10) | `resolveEffectiveHours` | direct call, line 833 | ✓ WIRED | Same map, third call site confirmed |
| `AgentDayHoursRepository` | `agent_day_hours` table | JPA `@Query` join through `h.agent.deskId` | ✓ WIRED | `findByTenantIdAndDeskId` used by `SolverService` constructor injection (line 82) |
| `DeskAgentService.setContractedHours` | `AgentDayHoursRepository` | `deleteByAgent_Id` + `save` × 7 | ✓ WIRED | D-10 fan-out; flush ordering fix confirmed present |
| `BambooRefreshService` | `AgentNameSplitter` | static call on `displayName` | ✓ WIRED | Line ~225-227 |
| `DeskAssignmentUploadService` | `AgentDayHoursRepository.deleteByAgent_Id` | clear-desk cleanup loop | ✓ WIRED | Line 562 |
| `ClientManagementService.assignEmployeesToDesk` | `AgentNameSplitter` | static call (WR-01 fix, commit 68ace87) | ✓ WIRED | Fourth write-site, initially missed by review, fixed and confirmed present |
| `V29 fan-out INSERT` | `agent.contracted_hours_per_day` | `CROSS JOIN` over 7 weekday literals | ✓ WIRED | Verified via real-Postgres dry-run, not just SQL inspection |

**Wiring:** 9/9 connections verified

## Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|----------------|-------------|--------|----------|
| MDL-01 | 09-01, 09-04, 09-05 | Agent stores first name and last name as separate fields | ✓ SATISFIED | Truth 1 above |
| MDL-02 | 09-02, 09-03, 09-04, 09-05 | Agent stores contracted hours per day of week; `AgentDayConfig` resolves from per-day values | ✓ SATISFIED | Truths 2, 4 above |
| MDL-03 | 09-03, 09-06 | Existing agents migrate without data loss | ✓ SATISFIED | Truth 3 above |

**Coverage:** 3/3 requirements satisfied. No orphaned requirements found (REQUIREMENTS.md maps only MDL-01/02/03 to Phase 9, and all three appear in plan frontmatter `requirements:` fields). Note: REQUIREMENTS.md and ROADMAP.md still show these as "Pending"/unchecked — that is a documentation-sync gap left for the orchestrator to close now that verification has run, not a code gap.

## Anti-Patterns Found

None. Scanned all 14 phase-modified files (`AgentNameSplitter.java`, `Agent.java`, `AgentDayHours.java`, `AgentDayHoursRepository.java`, `SolverService.java`, `BambooRefreshService.java`, `DeskAssignmentUploadService.java`, `AgentResponse.java`, `DeskAgentResponse.java`, `DeskAgentService.java`, `DeskAgentExportService.java`, `V29 migration`, `ClientManagementService.java`, `AgentService.java`) for `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/placeholder/empty-implementation patterns — zero matches.

**Anti-patterns:** 0 found

### Unresolved code-review judgment calls (09-REVIEW.md, status: resolved)

Not blockers for this phase's success criteria — recorded for visibility per the verification brief:
- **WR-03**: `AgentNameSplitter` and the V29 backfill split only on ASCII space (U+0020), not all whitespace. Java and SQL agree with each other (no drift risk), documented as an intentional interpretation of "first whitespace token." Low real-world impact for BambooHR display names.
- **WR-04**: Upload-created agents default to `workingDaysKnown=true`/`FULL_TIME` with no `AgentDayOff`/`agent_day_hours` rows, unlike the BambooHR refresh path. Deliberately left alone by the reviewer because fixing it risked the phase's own no-solve-regression invariant — appropriately deferred rather than blocking.
- **IN-02**: V29's SQL `trim()` character set is narrower than Java `String.trim()` (misses form-feed/vertical-tab). Negligible for real BambooHR data.
- **IN-03**: `BambooRefreshService` sync-event count overstates actual synced agents. Cosmetic audit-trail issue, unrelated to MDL-01/02/03.

## Human Verification Required

None. All four Success Criteria have direct, executable evidence: unit tests exercising the actual production resolution/persistence code (not mocks), plus a real-Postgres migration dry-run with recorded row-count and name-split proof for the migration-safety criterion. No visual, real-time, or external-service-dependent behavior in this phase's scope (Phase 9 explicitly has no UI).

## Gaps Summary

**No gaps found.** Phase goal achieved. All 4 ROADMAP success criteria verified with direct code + test evidence, all 3 requirement IDs (MDL-01, MDL-02, MDL-03) satisfied, all 10 artifacts present/substantive/wired, all 9 key links wired, 0 anti-patterns, 0 unresolved blocking review findings. The full regression suite (56 suites / 273 tests / 0 failures) plus 5 narrowly-scoped re-runs of the phase's own tests (`AgentNameSplitterTest`, `AgentNamePersistenceTest`, `AgentDayHoursPersistenceTest`, `SolverServiceEffectiveHoursResolutionTest`, `DeskAgentServiceContractedHoursTest` — 28 tests total, 0 failures) confirm the current codebase state, not just the SUMMARYs' claims.

One administrative note (not a code gap): ROADMAP.md still shows Phase 9 as `[ ]` unchecked and REQUIREMENTS.md shows MDL-01/02/03 as "Pending," even though Phases 10 and 11 (which depend on Phase 9) are already marked complete. This verification should be used to close that documentation gap.

## Verification Metadata

**Verification approach:** Goal-backward (ROADMAP success criteria + PLAN frontmatter must_haves merged)
**Must-haves source:** ROADMAP.md §Phase 9 (4 success criteria) + 6 PLAN.md frontmatter blocks (09-01 through 09-06)
**Automated checks:** 23 passed, 0 failed (10 artifacts + 9 key links + 4 truths, cross-checked against 28 re-run tests and a 273-test full suite)
**Human checks required:** 0
**Total verification time:** ~25 min

---
*Verified: 2026-08-21T14:00:00Z*
*Verifier: Claude (gsd-verifier)*
