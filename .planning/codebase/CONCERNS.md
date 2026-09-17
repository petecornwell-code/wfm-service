---
last_mapped_commit: 7e18ca2766e5fc34d93136d520c5a1529f8dc3b5
last_mapped_at: 2026-09-17
---
# Codebase Concerns

**Analysis Date:** 2026-09-17

## Tech Debt

### Time Handling: No Midnight-Wrap Logic in Shift Envelope Arithmetic

**Issue:** `ShiftBandPair.covers()` (lines 60-73) and `ShiftBandPair.startDeviationMinutes()` (lines 97-103) use `LocalTime` arithmetic directly with no handling for shifts that cross midnight.

**Files:**

- `src/main/java/com/wfm/model/ShiftBandPair.java:60-103`
- `src/main/java/com/wfm/model/ShiftTemplate.java:129-136` (net hours calculation)

**Impact:** 

- Overnight shifts (e.g., 22:00–06:00) cannot be modelled correctly — break offset calculation will wrap within the same calendar day.
- `startDeviationMinutes()` produces incorrect minute deltas when comparing times that cross midnight boundaries.
- Latent until the first desk schedules an overnight shift template; will surface as wrong shift geometry and incorrect deviation metrics.

**Fix approach:**

- Introduce an `OvernightShiftDuration` utility wrapping `LocalTime` pairs and providing `plusMinutes()` that handles day boundaries.
- Audit all sites using `LocalTime.plusMinutes()` for shift/break calculations; apply the new utility.
- Add unit tests exercising midnight wraps for break-offset and deviation calculations.

---

### Overnight Shift Templates Unmodelled as a Problem Class

**Issue:** The schema and solver model ShiftTemplate as start/end times only, with no day-of-week change signalling. The `isEffectiveOn()` and `appliesOn()` logic check date validity and weekday membership, but never acknowledge that a shift might span two calendar days.

**Files:**

- `src/main/java/com/wfm/model/ShiftTemplate.java:89-189`
- `src/main/java/com/wfm/service/SolverService.java:1-100` (no overnight shift pre-solve validation)
- `src/main/resources/db/migration/V39__add_shift_template_and_scheduling_mode.sql` (schema carries no overnight flag)

**Impact:**

- No explicit model state tracks which shifts are overnight — the system infers it from `endTime < startTime` comparison, which is fragile and undocumented.
- Pre-solve validation does not warn users that overnight shifts have not been validated against real payroll constraints (e.g., "no agent works across two payroll days").
- Reports and break-placement logic (e.g., `ScheduleOutputService.findBreaks()`) may render overnight shifts incorrectly in the grid.

**Fix approach:**

- Add `is_overnight` boolean column to `shift_template` table; require it at save time when `endTime < startTime`.
- Update `appliesOn()` to check weekday membership against the START day (current) and also the END day (next day) when overnight.
- Document break-placement semantics for overnight shifts in `ScheduleOutputService.findBreaks()`.
- Write integration tests for 22:00–06:00 shifts with breaks at offsets spanning midnight.

---

### Structural Guard Tests Use Hardcoded Violation Counts

**Issue:** `SolverQualityGuardTest.java` encodes an architectural invariant (`TOTAL_VIOLATION_CEILING = 3` at line 113) as a manually-set constant derived from observed per-seed baseline. The comment (lines 107-111) records the exact per-seed counts that were measured at the time this constant was set.

**Files:**

- `src/test/java/com/wfm/solver/SolverQualityGuardTest.java:113`
- `src/test/java/com/wfm/solver/SolverQualityGuardTest.java:220-244` (four hardcoded weight assertions on lines 228-243)
- `.planning/phases/15-shift-envelope-breaks-library-generation/15-14-SUMMARY.md` (the baseline data this constant was transcribed from)

**Impact:**

- Changes to the solver configuration, constraint weights, or fixture structure break the guard only if someone manually updates the hardcoded numbers.
- A silent weight or fixture drift can pass this guard while the actual quality regressed, as happened in Phase 15 (the guard was added specifically because a sevenfold regression shipped green without detection).
- Two previous phase completions already required manual adjustment of these counts (Phase 15 and Phase 17); expect this will repeat.

**Fix approach:**

- Compute `TOTAL_VIOLATION_CEILING` dynamically: run the fixture once at guard initialization, capture the baseline, and apply the `+2 headroom` rule programmatically.
- Alternatively, move from a fixed ceiling to a trend-based assertion: track the last 3 runs' medians and fail only if the current median is strictly higher by more than a threshold (e.g., +2 violations).
- Extract the per-seed weight values (shiftEnvelopeCompliance, shiftWorkContiguity, bandCapacity, unassignedAssignment) into a shared `LiveShapeShiftDeskFixture.SHIPPED_WEIGHTS` constant that `SolverQualityGuardTest` and the fixture both reference, so drift in one location updates both.

---

### Solver Quality Plateau and Known Non-Optimal Solutions

**Issue:** A soft-quality plateau was formally accepted in the shift-coupling model during Phase 15. The solver does not reach the known optimum on specific toy fixtures under the current weight configuration and time budget.

**Files:**

- `.planning/debug/shift-mode-break-geometry-ungoverned.md` (root-cause diagnosis)
- `.planning/debug/min-staffing-seats-zero-demand.md` (second known defect)
- `src/main/java/com/wfm/model/ConstraintWeights.java` (weight defaults)
- `src/main/resources/solverConfig.xml` (time/step limits)

**Impact:**

- Schedules that could mathematically achieve 0 hard violations may not in practice under the 5-minute time limit or 5,000-step limit (or both).
- Users may see "non-optimal solutions" accepted on desks that should be feasible, eroding confidence in the tool.
- If limits are increased, solution time could exceed SLA and stall the UI.

**Fix approach:**

- Document the known plateau in a visible place (e.g., inline comment at weight defaults, a deployment checklist note).
- Write an off-roadmap "Solver Time Budget Trade-Off Study" that benchmarks the cost (wall-clock time, resource usage) of incrementally higher step limits on fixture shapes.
- For immediate relief: expose time limit and step count as tuneable parameters in the UI so power users can trade runtime for quality on specific runs.

---

## Known Bugs

### Shift Mode: Break Geometry Not Enforced, Agents Scatter into Out-of-Envelope Hours

**Symptoms:** Agents hold seats outside their shift envelope and in zero/thin-demand hours, breaking the envelope into scattered fragments. One out-of-envelope seat pairs with one missing in-envelope seat (contracted hours stay constant by constraint, but position is unpriced).

**Files:**

- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` (constraints gating, lines 64-86)
- `src/main/java/com/wfm/service/SolverService.java:1175-1180` (minimum staffing seat expansion)
- `src/main/java/com/wfm/model/ConstraintWeights.java` (weight defaults and trade-offs)
- `.planning/debug/shift-mode-break-geometry-ungoverned.md` (full diagnosis)
- `.planning/debug/min-staffing-seats-zero-demand.md` (related root cause)

**Trigger:** Operating on a SHIFT-mode desk where demand window is wider than the union of shift envelopes, with at least one zero-FTE demand cell.

**Root Cause:** A conjunction of four independent facts that together create the defect:

1. `expandMinimumStaffingSeats()` creates fillable seats on zero-demand timeslots with no knowledge of shift envelopes (SolverService.java:1175-1180).
2. Bulk allocation constraints (`bulkOver`, `bulkUnder`) only penalize timeslots with a `TimeslotDemandConfig` row — zero-demand timeslots have no row, so both constraints produce zero penalty.
3. Shift envelope compliance carries weight 1hard, while contracted hours under-allocation carries 100hard — working outside the envelope is 100x cheaper than being one slot under.
4. Break geometry is not independently constrained in SHIFT mode — six geometry constraints are gated off when `SchedulingMode != SLOT`, leaving only cardinality constraints to describe occupied slots.

**Workaround:** Ensure demand curve covers at least the union of all shift envelopes; add dummy zero-FTE rows to `TimeslotDemandConfig` for all zero-demand timeslots to give bulk constraints a chance to penalize overflow.

**Fix approach (not yet implemented):**

- Add a SHIFT-mode-only hard constraint: "one agent per zero-demand timeslot, or zero agents" — rejects the parking-lot scenario before local search.
- OR: Gate off `expandMinimumStaffingSeats()` for SHIFT mode and route minimum-staffing need through the normal solver mechanics.
- OR: Increase `shiftEnvelopeComplianceWeight` to exceed `contractedHoursUnderWeight`, so envelope violation is never the cheaper move.

---

### Minimum Staffing Seats Generate No Over-Allocation Penalty on Zero-Demand Hours

**Symptoms:** Zero-demand timeslots accept seat allocations without triggering bulk-over-allocation constraints, making them the cheapest place to park agents who need to reach contracted hours but lack legal seats elsewhere.

**Files:**

- `src/main/java/com/wfm/service/SolverService.java:1175-1180` (expandMinimumStaffingSeats parameter list, envelope-blind)
- `src/main/java/com/wfm/service/SolverService.java:322-344` (call ordering: demand configs before filler seats)
- `src/main/resources/db/migration/V14__staffing_requirement_ftes.sql` (zero FTE rows not created)

**Trigger:** Same as above; surfaces on SHIFT-mode desks with thin operating windows.

**Root Cause:** `computeTimeslotDemandConfigs()` only creates rows for timeslots with `requiredFTEs > 0`, so the constraint's inner join produces no tuple for zero-demand hours and both `bulkOverallocationLimit` and `bulkUnderallocationHard` penalties are zero.

**Workaround:** Pre-populate `TimeslotDemandConfig` with zero-FTE rows for all zero-demand timeslots.

**Fix approach:**

- Include zero-FTE demand rows in `computeTimeslotDemandConfigs()` so over-allocation constraints can penalize them equally with non-zero demand.

---

## Security Considerations

### Terraform State Divergence: RDS Password Not Managed by Terraform

**Risk:** Terraform state holds an April 2026 generated password while the live RDS instance holds a different value. A future `terraform apply` that regenerates the password will rewrite RDS correctly, but an out-of-band password change not reflected in the secret manager, or a state restore, will cause ECS to crash with auth failure.

**Files:**

- `infra/rds.tf:24,45` (both password and secret tie to `random_password.db.result`)
- `infra/main.tf` (main module)

**Current Mitigation:** Live instance holds a dictionary phrase (weak password) while security group restricts access. Acceptable only in a dev sandbox.

**Recommendations:**

1. **Immediate:** Switch to RDS-managed password rotation via Secrets Manager (`manage_master_user_password = true`). Remove `random_password.db` entirely.
2. **Short-term:** If keeping the current approach, add a validation step in the deploy pipeline: check that RDS password matches Secrets Manager before allowing the ECS task to start.

---

### Environment Variables and Secrets Not Explicitly Listed

**Risk:** No centralized registry of required environment variables (API keys, database credentials, BambooHR token). Easy to miss required secrets during deployment or lose them during infrastructure changes.

**Files:**

- No `.env.example` or documented list found in repo root.
- Individual services (`application.properties`, `application-dev.properties`) scatter secret config across files.

**Current Mitigation:** The application fails at startup if a required secret is missing, which is good fail-fast behavior.

**Recommendations:**

- Create a `SECRETS.md` documenting every required env var, its source (Terraform, manual, BambooHR, AWS Secrets Manager), and rotation policy.
- Add validation at application startup that lists missing vs. present secrets for debugging.

---

## Performance Bottlenecks

### SolverService Complexity: 2,164 Lines, Multiple Phases, Complex State Management

**Problem:** The solver lifecycle spans initialization, constraint setup, pre-solve validation (12 distinct checks per spec §7.11), problem-fact assembly, entity expansion, Hibernate proxy unwrapping, tenant context propagation, asynchronous solver execution, and completion callback. All in one service class with heavy dependencies on 18 injected repositories.

**Files:**

- `src/main/java/com/wfm/service/SolverService.java` (2,164 lines)

**Impact:**

- Difficult to test individual phases in isolation; most tests construct the full service.
- High context switching cost when debugging — a bug in step 3 requires understanding steps 1–10 first.
- Easy to miss a pre-solve validation check or forget to propagate tenant context to the solver thread.

**Fix approach:**

- Extract pre-solve validation into a separate `PreSolveValidator` service with 12 discrete methods, each testable independently.
- Extract problem-fact assembly into `ProblemFactAssembler` (agents, specializations, preferences, exceptions, constraints).
- Extract entity expansion into `AgentAssignmentExpander`.
- Leave `SolverService` responsible for orchestration only, not implementation details.

---

### ScheduleOutputService: 975 Lines, Four Output View Methods All Stubs

**Problem:** The service aggregates staffing summary, agent schedule, preference report, and constraint violations from a solved schedule. Each method walks the full assignment graph to compute different projections; no index or cache means repeated scans.

**Files:**

- `src/main/java/com/wfm/service/ScheduleOutputService.java` (975 lines)

**Impact:**

- Generating all four views is O(assignments) × 4 — can be slow on large schedules (1000+ assignments).
- Building the Excel export calls all four views; generating multiple exports in sequence scans the assignment graph 4n times.

**Fix approach:**

- Materialize a single-pass walk of assignments into intermediate data structures (e.g., a `SolvedScheduleProjection` object holding pre-computed maps for each view).
- Cache the projection per schedule ID and clear only on schedule update/accept.

---

### Constraint Matching at Scale: ScheduleConstraintProvider Defines 21 Constraints, Some With O(n²) Complexity

**Problem:** `ScheduleConstraintProvider.java` (1,223 lines) defines 21 constraints, several with quadratic match cardinality (e.g., checking every agent against every timeslot). The solver runs these on every step, which can be slow on large problem sizes.

**Files:**

- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` (1,223 lines, 21 constraints)

**Impact:**

- Solver steps may slow down as the problem grows from 10 agents × 5 days (current benchmark) to 150+ agents × 30 days (live shape).
- Local search may spend most of its time re-scoring the same constraints rather than exploring new solutions.

**Fix approach:**

- Profile constraint matching times on realistic problem sizes; identify the 2–3 slowest constraints.
- Add incremental constraint evaluation where possible (e.g., only re-score the timeslots affected by a move, not all).
- Consider splitting the 1,223-line file into multiple constraint providers (e.g., `HardConstraintProvider`, `SoftConstraintProvider`) for readability.

---

## Fragile Areas

### Flyway Migrations: 48 Migrations Against Live Data, Forward-Only

**Files:**

- `src/main/resources/db/migration/V1__initial_schema.sql` through `V48__add_consistency_tolerance_and_preferred_start_weight.sql`

**Why Fragile:** Migrations run against the live "dev" environment with real tenant data. Once applied, rolling back a migration is manual and risky. Mistakes in migration logic corrupt live data.

**Safe Modification:**

- Test every migration against a fresh replica of the live database before deploying.
- Write a corresponding `DOWN` migration (or document the exact SQL to reverse it) alongside every forward migration.
- Add a pre-deployment check: run the migration on a staging clone, verify the result, then apply to live.
- Keep migration scripts small and focused (one logical change per migration).
- Add rollback runbooks to the deployment checklist.

---

### InMemoryScheduleStore: Single Global Store for Running Schedules

**Files:**

- `src/main/java/com/wfm/service/InMemoryScheduleStore.java`
- Used by `SolverService`, `ScheduleService`, all schedule-related endpoints

**Why Fragile:**

- If the application restarts or crashes, all in-flight schedules are lost.
- Concurrent access to the same desk could create race conditions (is a schedule already running?).
- No persistence means the data isn't visible across instances in a load-balanced deployment.

**Safe Modification:**

- Treat in-memory store as a cache only; back it with the database as the source of truth.
- Add a status column to Schedule (e.g., `status ENUM: RUNNING, COMPLETED, FAILED, ACCEPTED`); query the DB to check if a solve is in progress.
- Clear the in-memory store on startup by checking the DB for stale RUNNING schedules and moving them to FAILED.

---

### ShiftLibraryValidationService and ShiftLibraryGenerationService: 663+923 Lines of Validation and Template Generation Logic

**Files:**

- `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` (663 lines)
- `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` (923 lines)
- Tested by: `ShiftLibraryValidationServiceTest.java` (1,056 lines), `ShiftLibraryGenerationServiceTest.java` (973 lines)

**Why Fragile:**

- Complex business logic for shift template generation and validation is tightly coupled to the solver's requirements.
- Many edge cases (overnight shifts, break bands at edges, templates spanning month boundaries) are tested but not all surfaces are documented.
- If a new constraint is added to the solver, pre-solve validation might not catch violations (e.g., Phase 15 shift work contiguity was added but validation doesn't check it).

**Safe Modification:**

- Document the validation invariants as explicit assertions in the code (e.g., `// INV-1: every shift template must have valid weekday mask`).
- Add a "validation checkpoint" at each major branch: if conditions change, re-run validation on the changed data.
- Write round-trip tests: generate a library, solve with it, assert no constraint violations occur.

---

## Scaling Limits

### Solver Performance: Step-Count Limit of 5,000 Steps in 5 Minutes

**Current Capacity:** Successfully solves:

- 4 agents × 2 days × 15-min slots = 64 assignments (benchmark: `ShiftModelBenchmarkTest`)
- 10 agents × 2 days (fixture: `LiveShapeShiftDeskFixture`, `SolverQualityGuardTest`)
- 150 agents × 1 day (benchmark: `BreakAwareConstructionTest`)

**Limit:** Solves may not reach 0hard on the following:

- 150 agents × 3 days in the quality guard (Step count 5,000; increased from 2,000 after initial escape-hatch rung did not clear interior holes — see `SolverQualityGuardTest` lines 82-91).
- Real-world 200+ agent desks with 30+ day periods.

**Scaling Path:**

- Upgrade the construction heuristic: `BreakAwareConstructionPhase` replaced `FIRST_FIT_DECREASING` and eliminated the 150-agent tangles, but even larger problems may need a more specialized pre-assignment strategy.
- Add domain-driven local search moves (e.g., the deferred `AtomicShiftMoveFactory` cross-agent seat displacement, `.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md`).
- Profile and optimize constraint matching (see Performance Bottlenecks section).
- Expose time limit and step count as configuration parameters so users can trade runtime for quality on specific runs.

---

### Database Connections: InMemoryScheduleStore and 18+ Injected Repositories in SolverService

**Current Capacity:** SolverService maintains 18 repository injections (lines 54-69 of SolverService.java) and a single `InMemoryScheduleStore`. On concurrent solve requests, each opens its own persistence context and transaction.

**Limit:** If 10+ desks attempt to solve simultaneously on the same instance, database connection pool can saturate and new solve requests queue or fail.

**Scaling Path:**

- Monitor database connection usage on live; if approaching pool limit, increase pool size or implement queuing at the application layer.
- Move schedule storage to the database (see Fragile Areas section); eliminate InMemoryScheduleStore.
- Consider query optimization: pre-fetch all problem facts in a single batch query instead of separate queries per repository.

---

### Data Model: No Indexes on High-Cardinality Lookups

**Problem:** Queries like "list all days off for an agent within a date range" or "list all preferences for a desk-agent pair" scan the table without explicit indices.

**Files:**

- `src/main/java/com/wfm/repository/AgentDayOffRepository.java`
- `src/main/java/com/wfm/repository/AgentPreferenceRepository.java`
- `src/main/resources/db/migration/V1__initial_schema.sql` (partial unique indexes exist but not covering all access patterns)

**Scaling Path:**

- Run `EXPLAIN ANALYZE` on each repository query against a real-world schema (1000+ agents, 30 days of data).
- Add missing indexes on (tenant_id, agent_id, date), (tenant_id, desk_id, agent_id), etc.
- Use `@EntityGraph` (already done for some repositories) to avoid N+1 on eager relationships.

---

## Dependencies at Risk

### Timefold Solver 1.16.0: Breaking Changes in Future Versions

**Risk:** The project uses Timefold 1.16.0 (Java constraint-satisfaction library). Version 2.0 is in development; breaking API changes are expected (e.g., score types, solution class registration, constraint provider syntax).

**Files:**

- `build.gradle` (dependency declaration)
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` (all constraint definitions)
- `src/main/java/com/wfm/service/SolverService.java` (solver setup)

**Impact:**

- Upgrading to Timefold 2.0 requires rewriting all 21 constraints.
- No prior warning if Timefold 1.16.0 hits end-of-life; security fixes may stop.

**Migration Plan:**

- Monitor Timefold release notes monthly.
- Allocate a dedicated phase (2–3 weeks) for the upgrade if 2.0 is released.
- Create a branch early and test constraint compatibility against the 2.0 beta.

---

### Spring Boot and Spring Data JPA: Long-Term Support Tracking

**Risk:** The project uses Spring Boot (version from `build.gradle`). Spring 6.0 is the latest major version; older 5.x lines are approaching end-of-support.

**Impact:**

- Unpatched security vulnerabilities in older Spring versions.
- New Spring Boot versions have breaking changes (e.g., Spring Boot 3.0 dropped Java 8 support).

**Mitigation:**

- Check `build.gradle` for the declared Spring Boot version monthly.
- Schedule a Spring Boot upgrade phase every 12–18 months, coordinated with the Timefold upgrade above if possible.

---

### External API: BambooHR Integration Has No Circuit Breaker

**Files:**

- `src/main/java/com/wfm/integration/HttpBambooHRClient.java`
- `src/main/java/com/wfm/service/BambooRefreshService.java`

**Risk:** If BambooHR API is slow or unavailable, the refresh endpoint will hang waiting for a response. No timeout, no retry logic, no fallback.

**Impact:**

- Refresh requests timeout silently or hang the request thread, degrading application responsiveness.
- If BambooHR is down for hours, the service cannot update agent records.

**Mitigation:**

- Add a timeout (e.g., 10 seconds) to the BambooHR HTTP client.
- Wrap calls in a circuit breaker (e.g., Resilience4j) that fails fast after 3 consecutive timeouts.
- Return a 503 Service Unavailable response if BambooHR is unreachable, rather than hanging.

---

## Test Coverage Gaps

### Frontend: No Automated Tests for UI Interactions

**Untested Area:** All React components in `frontend/` lack unit or integration tests. User workflows like "assign agents to desk," "save staffing requirements," "download schedule" have no automated coverage.

**Files:**

- `frontend/src/` (all `.tsx` files)

**Risk:** Regressions in critical workflows (save preferences, accept schedule) won't surface until manual testing or production deployment.

**Priority:** High — these are user-facing critical paths.

**Recommended Coverage:**

- Preference/exception edit and save workflows
- Staffing requirement grid edit and save
- Schedule result tabs (staffing summary, agent schedule, preference report, constraint violations)
- Export to Excel
- Error handling and validation feedback

---

### Integration Tests: No End-to-End Solve Path Covering All Desk Types

**Untested Area:** The full solve pipeline (create desk → add agents → set preferences → add demand → solve → accept → generate report) is tested in isolation but not end-to-end for all desk types (SLOT mode, SHIFT mode, with/without breaks).

**Files:**

- Unit tests exist for each service (`SolverServiceTest`, `ScheduleServiceTest`, `ScheduleOutputServiceTest`) but they mock dependencies.
- Integration test `ShiftDeskEndToEndRegressionTest` covers SHIFT mode only.

**Risk:** A breaking change in one service (e.g., ScheduleService) might not be caught by other services' unit tests.

**Priority:** Medium — the unit tests are thorough, but an end-to-end smoke test would catch wiring errors.

**Recommended Coverage:**

- SLOT mode: create → solve → accept → export in under 10 seconds.
- SHIFT mode: same workflow with a 3-agent × 5-day fixture.
- Boundary cases: desks with zero agents, zero demand, zero preferences.

---

### Database Schema: No Constraints Tests for Data Integrity

**Untested Area:** Unique constraints, foreign keys, and NOT NULL rules are defined in Flyway migrations but never explicitly tested. A migration that relaxes a constraint (e.g., making a column nullable) could silently corrupt assumptions downstream.

**Files:**

- `src/main/resources/db/migration/` (all migration files)
- No explicit constraint-validation tests.

**Risk:** Silent schema drift (e.g., a column that should be NOT NULL becoming nullable) propagates to the application logic, which may crash with NullPointerException rather than catching the invariant violation early.

**Priority:** Low — the application validates most invariants at save time, so the database constraint is defensive rather than primary.

**Recommended Coverage:**

- Write a `SchemaIntegrityTest` that connects to the test database and asserts every unique constraint, foreign key, and NOT NULL rule is present.

---

### Constraint Provider: Shift Work Contiguity Constraint Has Known Edge Cases

**Untested Area:** The shift work contiguity constraint (lines 261-270 of ScheduleConstraintProvider.java) checks for non-break holes in an agent-day's assignment span. The test suite (`ShiftWorkContiguityConstraintTest`) covers most cases, but the red-proofs in `SolverQualityGuardTest` expose edge cases that the constraint test doesn't cover (e.g., a break window that is exactly at an envelope edge, which should be flagged as structural).

**Files:**

- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:261-270`
- `src/test/java/com/wfm/solver/ShiftWorkContiguityConstraintTest.java`

**Risk:** A change to break-window handling could introduce regressions without triggering test failures.

**Priority:** Medium — the red-proofs exist but are not automated (they're manual corruption + walker checks in SolverQualityGuardTest).

**Recommended Coverage:**

- Extract the SolverQualityGuardTest red-proofs into automated JUnit tests with realistic fixtures.
- Add parameterized tests for all break-edge-case geometries.

---

## Missing Critical Features / Limitations

### No Backwards Compatibility for Schedule Evolution

**Problem:** Once a schedule is accepted and persisted, there is no way to "version" it or see what changed from the previous version. If a user accepts a schedule, then changes demand and re-solves, the old schedule is lost.

**Impact:** Users cannot compare two schedules or understand drift over time.

**Fix Approach:** Add a `schedule_version` table or archive accepted schedules with a `superseded_by_schedule_id` FK, so each version is retained.

---

### No Soft Reset of Constraint Weights to Defaults

**Problem:** Once a desk's constraint weights are modified, there's no one-click "reset to defaults" button. Users must remember the defaults and re-enter them manually.

**Files:**

- `frontend/` constraint weights form (no reset button)
- `src/main/java/com/wfm/service/ConstraintWeightsService.java` (no reset method)

**Impact:** Users customize weights experimentally, regret the change, and must re-do all customization from memory.

**Fix Approach:** Add a `PUT /desks/{deskId}/constraint-weights/reset` endpoint that loads the shipped defaults from `ConstraintWeights.java` and saves them.

---

## Summary

| Category | Count | Severity |
|----------|-------|----------|
| Tech Debt | 3 | Medium–High |
| Known Bugs | 2 | High |
| Security | 2 | Medium |
| Performance | 3 | Medium |
| Fragile Areas | 3 | Medium |
| Scaling Limits | 3 | Medium |
| Dependencies at Risk | 3 | Low–Medium |
| Test Coverage Gaps | 4 | Medium |
| Missing Features | 2 | Low |

---

*Concerns audit: 2026-09-17*
