# Codebase Concerns

**Analysis Date:** 2026-04-02

## Tech Debt

**Stub/no-op constraints registered as active:**
- Issue: Two constraints in `ScheduleConstraintProvider` are permanently no-ops that always penalize 0. `breakClustering` (constraint 11) and `bulkUnderallocationSoft` (constraint 14) are declared in `defineConstraints()`, wired into the score, exposed in the constraint weights DTO and database, but have bodies of `penalizeConfigurable(a -> 0)`. They consume solver evaluation time on every step while doing nothing.
- Files: `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` lines 490–506; `src/main/java/com/wfm/model/ConstraintWeights.java`; `src/main/java/com/wfm/dto/ConstraintWeightsDto.java`
- Impact: Solver wastes CPU per assignment on two constraints that never fire. Any external configuration of these weights has no effect and may mislead operators. `breakClustering` comment says it is "deferred to Phase 5 optimization."
- Fix approach: Remove them from `defineConstraints()` until implemented, or add a feature flag that gates their inclusion. Clean up corresponding weight fields from `ConstraintWeights` entity, DTO, and migrations at the same time.

**Duplicated `resolvePreferences` logic:**
- Issue: Identical preference-resolution logic (standing vs. weekly override, PTO exclusion) is implemented as a private method in both `SolverService` and `ScheduleService`. The comment in `ScheduleService` acknowledges it "mirrors the logic in SolverService" but no shared utility exists.
- Files: `src/main/java/com/wfm/service/SolverService.java` lines 402–468; `src/main/java/com/wfm/service/ScheduleService.java` lines 391–455
- Impact: Bug fixes or spec changes to preference resolution must be applied in two places. The two copies have already diverged slightly (`ScheduleService` copy does not call `setStanding(false)` at the same point).
- Fix approach: Extract into a shared `PreferenceResolver` utility or service called by both.

**Duplicated `isAligned` helper:**
- Issue: The `isAligned(LocalTime, BreakAlignment)` method appears identically in both `ScheduleConstraintProvider` and `SolverService` (pre-solve validation).
- Files: `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` line 595; `src/main/java/com/wfm/service/SolverService.java` line 822
- Impact: Alignment logic must be kept in sync; a change to supported alignment types must be applied twice.
- Fix approach: Move to a static helper on the `BreakAlignment` enum or a shared `BreakAlignmentUtil` class.

**`SolverService` god class (955 lines):**
- Issue: `SolverService` handles pre-solve validation (12 checks), preference resolution, agent day config computation, capacity warning computation, overflow assignment expansion, a pre-solve score diagnostic, and async solver lifecycle management — all in a single 955-line class.
- Files: `src/main/java/com/wfm/service/SolverService.java`
- Impact: Difficult to test individual concerns in isolation. All existing solver tests bypass `SolverService` entirely and talk directly to the constraint provider and `SolutionManager`.
- Fix approach: Extract `PreSolveValidator`, `AgentDayConfigBuilder`, and `CapacityAnalyser` into dedicated collaborators.

**Hardcoded specialization names in `BambooRefreshService`:**
- Issue: Four specialization name strings ("Shipping and Delivery", "Payments and Safety", "Order Quality and Usability", "Privacy and Legal & DSA") are hardcoded as `private static final String` constants. The refresh unconditionally upserts all four specializations for every desk, regardless of what the desk actually needs.
- Files: `src/main/java/com/wfm/integration/BambooRefreshService.java` lines 43–46, 104–141
- Impact: All desks inherit these four specializations even if irrelevant. Renaming a specialization in production requires a code change.
- Fix approach: Make specialization seeding data-driven (e.g., configured per desk or driven by a configurable list).

**`schedule.setId(null)` trick for JPA persistence on accept:**
- Issue: In `ScheduleService.acceptSchedule`, the in-memory schedule's ID is nulled out (`schedule.setId(null)`) so JPA treats the `@Entity` as new and calls `persist`. This mutates the canonical in-memory object mid-transaction before it is removed from the store.
- Files: `src/main/java/com/wfm/service/ScheduleService.java` line 236
- Impact: Any concurrent read of the schedule between `setId(null)` and `inMemoryStore.remove` gets a broken object. The pattern is fragile and non-obvious.
- Fix approach: Create a new `Schedule` entity for persistence rather than mutating the in-memory planning solution.

**V17/V18 migration add-then-drop pattern:**
- Issue: Migration V17 adds `shipping_and_delivery` and `payments_and_safety` columns to the `agent` table; V18 immediately drops them because "V17 incorrectly modelled specializations as boolean columns." Both migrations run on every fresh database setup.
- Files: `src/main/resources/db/migration/V17__add_shipping_delivery_payments_safety_columns.sql`; `src/main/resources/db/migration/V18__drop_shipping_delivery_payments_safety_columns.sql`
- Impact: Minor extra DDL noise on every new environment. Flyway checksums prevent removing these migrations from history.
- Fix approach: Acceptable to leave as-is; document as a known artefact in migration history.

---

## Security Considerations

**No authentication or authorisation layer:**
- Risk: The API has no Spring Security configuration. The only access control is the `TenantFilter`, which accepts any numeric `X-Tenant-ID` header supplied by the caller. Any client that can reach the network interface can impersonate any tenant.
- Files: `src/main/java/com/wfm/config/TenantFilter.java`; no `SecurityConfig` class exists anywhere in the project
- Current mitigation: The service appears designed to be deployed behind a reverse proxy or API gateway that enforces authentication before forwarding requests.
- Recommendations: Document the required gateway authentication assumption explicitly. Add a `@Value`-controlled API key or JWT validation filter at minimum. Add method-level `@PreAuthorize` guards if role differentiation is ever needed.

**BambooHR API key stored plaintext in database and returned by API:**
- Risk: The BambooHR API key and subdomain are stored unencrypted in the `app_configuration` table and the full key value is returned by `GET /api/v1/configuration` without any masking.
- Files: `src/main/java/com/wfm/service/AppConfigurationService.java`; `src/main/java/com/wfm/controller/AppConfigurationController.java`; `src/main/java/com/wfm/model/AppConfiguration.java`
- Current mitigation: None — the full key is persisted and returned over the API.
- Recommendations: Mask the key value in the GET response (e.g., return only last 4 characters). Consider storing sensitive config values with envelope encryption or delegating to a secrets manager.

**Hardcoded credentials in developer utility scripts compiled into production JAR:**
- Risk: Two utility classes in the non-standard `utils` package contain a hardcoded BambooHR API key and subdomain (`helpware` / `ad2bb9c54554545bccb7ee7f732ebefcd27492be`) directly in their `main` methods.
- Files: `src/main/java/utils/BambooCustomFields.java` line 30; `src/main/java/utils/BambooEmployeesByDepartment.java` line 30
- Current mitigation: These classes are not reachable via the web layer, but they are in `src/main/java` and are compiled into the production artifact.
- Recommendations: Move to `src/test/java` or a separate Gradle module, or delete if no longer needed. Rotate any credential that was committed in real git history.

**No file upload size limits configured:**
- Risk: The FTE spreadsheet upload, desk assignment upload, and preference upload endpoints accept `MultipartFile` with no configured maximum size in `application.yml`. A large file could exhaust heap memory.
- Files: `src/main/java/com/wfm/service/FteUploadService.java`; `src/main/java/com/wfm/service/DeskAssignmentUploadService.java`; `src/main/java/com/wfm/service/PreferenceUploadService.java`; `src/main/resources/application.yml`
- Current mitigation: Spring Boot default multipart limit is 1 MB per part / 10 MB per request.
- Recommendations: Explicitly set `spring.servlet.multipart.max-file-size` and `max-request-size` in `application.yml`.

**Solver time limit has no server-side cap:**
- Risk: `SolveRequest.solveTimeSeconds` is accepted from the caller with no upper bound validation. A caller can set an arbitrarily large solve time, tying up the Timefold `SolverManager` thread pool indefinitely.
- Files: `src/main/java/com/wfm/service/SolverService.java` lines 308–315; `src/main/java/com/wfm/dto/SolveRequest.java`
- Current mitigation: None.
- Recommendations: Enforce a server-side maximum (e.g., 3600 seconds) in `SolverService.startSolve`.

---

## Performance Bottlenecks

**`getScheduleDetail` recomputes all output views on every call:**
- Problem: Every `GET /desks/{deskId}/schedules/{scheduleId}` call triggers `buildStaffingSummary`, `buildAgentSchedule`, `buildPreferenceReport`, and `buildConstraintViolations` from raw assignments. For in-memory RUNNING schedules, constraint violation building also invokes Timefold's `SolutionManager.explain`, which re-runs the entire constraint stream.
- Files: `src/main/java/com/wfm/service/ScheduleService.java` lines 150–155; `src/main/java/com/wfm/service/ScheduleOutputService.java`
- Cause: No caching layer for derived output views; solver best-solution updates occur frequently during RUNNING state so polling clients trigger repeated full recomputation.
- Improvement path: Cache computed output views on the `Schedule` in-memory object and invalidate on each `bestSolutionConsumer` callback, or only recompute when the score changes.

**`listSchedules` loads up to 1001 DB rows before applying cursor pagination in Java:**
- Problem: `ScheduleService.listSchedules` fetches up to `MAX_LIMIT + 1` (1001) rows from the database via `PageRequest.of(0, 1001)`, merges the in-memory schedule at the front, then applies cursor pagination in Java. The database query cannot skip past the cursor position.
- Files: `src/main/java/com/wfm/service/ScheduleService.java` lines 79–119
- Cause: The in-memory schedule must appear at the top of the list, which prevents true keyset pagination against the database.
- Improvement path: Limit DB fetch to a practical ceiling (e.g., 200) and document that the list shows only recent accepted schedules beyond that threshold.

**Per-call BambooHR config lookup hits the database on every API request:**
- Problem: Every BambooHR HTTP call invokes `AppConfigurationService.getConfigValue` twice (for `bamboohr.server` and `bamboohr.apiKey`), each issuing a `SELECT` against `app_configuration`. This adds two DB round-trips per external API call.
- Files: `src/main/java/com/wfm/integration/HttpBambooHRClient.java` lines 47–65 (`getSubdomain`, `getApiKey`)
- Cause: No in-process cache for configuration values.
- Improvement path: Cache config values in `AppConfigurationService` with TTL-based or dirty-flag invalidation on `saveConfig`.

**Three separate O(N×D) nested loops in pre-solve validation:**
- Problem: For each eligible agent, the code iterates over every date in the schedule period in three separate passes: contracted-hours divisibility check, coverage window check, and agent day config computation. For 150 agents over 31 days this is ~4,650 iterations per pass (13,950 total).
- Files: `src/main/java/com/wfm/service/SolverService.java` lines 651–673, 736–759, 472–508
- Cause: Straightforward nested-loop design; acceptable at current scale but degrades with longer periods or more agents.
- Improvement path: Combine the three agent×date traversal passes into a single loop during pre-solve setup.

---

## Fragile Areas

**In-memory schedule state lost on pod restart:**
- Files: `src/main/java/com/wfm/service/InMemoryScheduleStore.java`
- Why fragile: All RUNNING/COMPLETED/STOPPED/FAILED schedules exist only in JVM heap. A pod crash or restart loses all in-flight solver state. No crash-recovery or restart-detection mechanism exists.
- Safe modification: Accepted schedules are persisted safely. The risk window is between solve start and accept.
- Test coverage: No test exercises the pod-restart/state-loss path.

**`deriveIncrement` computes slot size from timeslot arithmetic at scoring time:**
- Files: `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` lines 604–608
- Why fragile: The `honourPreferredBreakTime` constraint calls `deriveIncrement(assignments)` inside a filter lambda during constraint evaluation. If the assignments list is empty or the first timeslot's duration does not match the schedule increment (e.g., due to a data anomaly), it silently defaults to 15 minutes. This can cause the break-time constraint to evaluate with the wrong granularity with no error signal.
- Safe modification: `incrementMinutes` is already present in `AgentDayConfig`. Pass it explicitly to `findBreakStart` rather than deriving it from timeslot arithmetic.

**`ScheduleService.acceptSchedule` is not safe under concurrent calls:**
- Files: `src/main/java/com/wfm/service/ScheduleService.java` lines 195–308
- Why fragile: Two concurrent accept requests for the same schedule will both pass the in-memory status check (`COMPLETED` or `STOPPED`), both call `schedule.setId(null)`, and both attempt to persist. The `@Version` check at line 214 guards against client-supplied stale versions but not concurrent server-side accepts racing through the same in-memory object.
- Safe modification: Synchronize on the schedule object or use a per-schedule lock before mutating its ID and status, or—better—build a new entity for persistence rather than mutating the planning solution.

**Exception silently swallowed in `buildConstraintViolations`:**
- Files: `src/main/java/com/wfm/service/ScheduleOutputService.java` line 399
- Why fragile: The entire `solutionManager.explain()` call is wrapped in `catch (Exception e)` which logs a warning and returns `List.of()`. A broken constraint model, a null pointer in score explanation, or an incremental scoring bug silently produces an empty violation list rather than surfacing the error.
- Safe modification: At minimum log the full stack trace at ERROR level; consider returning a sentinel entry that indicates the explanation failed so callers can distinguish "no violations" from "explanation unavailable."

---

## Test Coverage Gaps

**No tests for service layer (`SolverService`, `ScheduleService`, `ScheduleOutputService`):**
- What's not tested: The 12-check pre-solve validation, preference resolution edge cases, capacity warning computation, overflow assignment expansion, accept/reject/stop schedule lifecycle, and the schedule detail output builders.
- Files: `src/main/java/com/wfm/service/SolverService.java`; `src/main/java/com/wfm/service/ScheduleService.java`; `src/main/java/com/wfm/service/ScheduleOutputService.java`
- Risk: Business logic regressions in validation, preference resolution, or output building are not caught until manual testing.
- Priority: High — `SolverService.runPreSolveValidation` and `ScheduleService.acceptSchedule` are the most complex paths with no coverage.

**No controller-layer tests (HTTP contract tests):**
- What's not tested: Request deserialization, path variable parsing, response HTTP codes, pagination parameters, and error body shape for all 11 controllers.
- Files: `src/main/java/com/wfm/controller/` (all files)
- Risk: Breaking changes to the API contract (wrong HTTP status, missing field in response) go undetected.
- Priority: High — no `@WebMvcTest` or `@SpringBootTest` slice tests exist. Only `WfmApplicationTests` (context load) is present.

**No tests for BambooHR integration layer:**
- What's not tested: `HttpBambooHRClient` JSON parsing, error handling for non-200 responses, time-off filtering; `BambooRefreshService` transaction behaviour, specialization upsert logic, agent matching by `bamboohrId`.
- Files: `src/main/java/com/wfm/integration/HttpBambooHRClient.java`; `src/main/java/com/wfm/integration/BambooRefreshService.java`
- Risk: Changes to BambooHR API response shapes or the refresh upsert logic break silently.
- Priority: Medium.

**Solver tests do not cover end-to-end solve runs with multi-day, multi-specialization scenarios:**
- What's not tested: A full solve (construction heuristic + local search) for a multi-day schedule with multiple specializations, agent exceptions, and weekly preferences. All solver tests either score a pre-assigned solution or use short (≤30 s) local search runs on single-specialization single-day scenarios.
- Files: `src/test/java/com/wfm/solver/`
- Risk: Solver regressions from constraint weight changes or new constraint additions may not surface until production.
- Priority: Medium.

---

## Incomplete Features

**`pgvector` extension enabled with no application usage:**
- Problem: Migration V24 enables the `pgvector` PostgreSQL extension, but no entity, repository, or service in the application references vector columns or similarity search.
- Files: `src/main/resources/db/migration/V24__enable_pgvector_extension.sql`
- Blocks: Indicates a planned feature (likely AI-driven staffing suggestions or preference matching) that has not been implemented. The extension requires the `vector` extension to be available on the PostgreSQL server, which may not be true in all deployment environments.

---

*Concerns audit: 2026-04-02*
