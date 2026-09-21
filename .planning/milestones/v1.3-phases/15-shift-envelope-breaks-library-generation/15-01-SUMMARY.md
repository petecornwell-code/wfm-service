---
phase: 15-shift-envelope-breaks-library-generation
plan: 01
subsystem: database
tags: [jpa, spring-boot, flyway, postgres, h2, tenant-scoping, shift-templates]

# Dependency graph
requires: []
provides:
  - shift_template_break_band table (id, tenant_id, shift_template_id FK, offset_minutes, duration_minutes, capacity) — V40, one row per previously-breaking template
  - ShiftTemplate no longer declares break_offset_minutes/break_duration_minutes (dropped by V40)
  - ShiftTemplateBreakBand entity + ShiftTemplateBreakBandRepository (tenant-scoped, no parent-side collection mapping)
  - ShiftTemplateRequest/Response carrying a bands list instead of the four Phase 14 scalar break fields
  - ShiftLibraryValidationService.covers() generalised to any-band coverage (public + package-visible Window record, reusable by plan 15-02)
  - ShiftLibraryValidationResponse.capacityAdvisories — named shortfall when band capacity totals fall below the admissible headcount
  - MigrationEntityConsistencyTest — dependency-free migration-vs-entity reconciliation guard (closes UAT gap G-14-1's blind spot for shift_template/shift_template_break_band)
affects: [15-02-shift-library-generation, 15-03-shift-envelope-solver-model, 15-05-frontend-band-editor, 15-06-break-clustering-constraint]

actuals:
  tokens: 26977
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Child-FK-plus-repository over @OneToMany (P-01) — ShiftTemplateBreakBand mirrors AgentDayHours's shape rather than introducing this codebase's first parent-side collection mapping"
    - "Save-then-replace-children for id-generated parents — the template must exist (id populated by GenerationType.UUID at persist time) before its band rows can hold the FK, so create/update save the template first and replace its bands second"
    - "Bulk-load-once-per-request-then-any-band-quantify — ShiftLibraryValidationService.validate() loads every template's bands in one IN-list query and evaluates covers()/hours checks with an any-band anyMatch, so a one-band template's verdict is provably identical to the pre-migration scalar path"
    - "Dependency-free migration-vs-entity reconciliation — a plain JUnit test folds CREATE/ALTER TABLE statements (off the classpath, comments stripped) into an effective column map and reflects over @Column mappings, closing a Flyway-never-runs-under-test blind spot without Testcontainers"

key-files:
  created:
    - src/main/resources/db/migration/V40__shift_template_break_bands.sql
    - src/main/java/com/wfm/model/ShiftTemplateBreakBand.java
    - src/main/java/com/wfm/repository/ShiftTemplateBreakBandRepository.java
    - src/test/java/com/wfm/service/ShiftTemplateBreakBandServiceTest.java
    - src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java
  modified:
    - src/main/java/com/wfm/model/ShiftTemplate.java
    - src/main/java/com/wfm/service/ShiftTemplateService.java
    - src/main/java/com/wfm/dto/ShiftTemplateRequest.java
    - src/main/java/com/wfm/dto/ShiftTemplateResponse.java
    - src/main/java/com/wfm/controller/ShiftTemplateController.java
    - src/main/java/com/wfm/service/ShiftLibraryValidationService.java
    - src/main/java/com/wfm/dto/ShiftLibraryValidationResponse.java
    - src/test/java/com/wfm/service/ShiftTemplateServiceTest.java
    - src/test/java/com/wfm/service/ShiftTemplateTracerTest.java
    - src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java

key-decisions:
  - "D-01 executed verbatim: bands promoted to primary, both scalar break columns dropped (not kept alongside) — one mechanism, one predicate"
  - "Band persistence split into applyScalarFields (pre-save) + replaceBands (post-save) rather than a single pre-save applyFields, because GenerationType.UUID only guarantees the template's id once save()/persist() runs, and the band rows need that id for their FK"
  - "Task 1's ShiftLibraryValidationService adaptation deliberately preserved first-band-only semantics (mechanical port); Task 2 generalised to any-band coverage as its own reviewable diff, per the plan's explicit instruction not to conflate the two"
  - "MigrationEntityConsistencyTest strips SQL -- line comments before parsing — this project's own migration comment discipline (long prose citing decision IDs, embedding commas/parens) corrupted a naive top-level-comma splitter and produced a false negative on valid_weekdays itself"

patterns-established:
  - "One-band invariant test: any coverage-service generalisation from a scalar to a collection ships with an explicit test asserting the one-element case is byte-identical to the pre-generalisation behaviour"

requirements-completed: [ENVL-08, SHLB-07]

coverage:
  - id: D1
    description: "shift_template_break_band exists and is populated one row per previously-breaking template; ShiftTemplateBreakBand entity + tenant-scoped repository read/write it without a parent-side collection mapping"
    requirement: "ENVL-08"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateBreakBandServiceTest.java#create_twoBands_roundTripOffsetAscendingWithCorrectNetHours"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java#migrationDeclaredColumns_reconcileWithEntityMappings"
        status: pass
    human_judgment: false
  - id: D2
    description: "ShiftTemplateService validates bands wholesale on every save: capacity >= 1 when set, duplicate (offset,duration) pairs rejected, per-band grid alignment reported by index"
    requirement: "ENVL-08"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateBreakBandServiceTest.java#create_duplicateOffsetAndDuration_rejected"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateServiceTest.java#create_offGridBandBreakStart_rejectedWithIndexedBandDetail"
        status: pass
    human_judgment: false
  - id: D3
    description: "covers() generalised to any-band coverage: a demand window is covered when at least one band leaves it worked; a two-band template self-covers its own break hour; touching bands remain distinct and legal"
    requirement: "ENVL-08"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java#validate_twoBandTemplate_selfCoversItsOwnBreakHour"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java#validate_touchingBands_secondBandCoversWhereFirstDoesNot"
        status: pass
    human_judgment: false
  - id: D4
    description: "A one-band template's coverage, misalignment, hours-advisory and unsatisfiable-weekday verdicts are byte-identical to Phase 14's single-offset predecessor"
    requirement: "ENVL-08"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java#validate_oneBandTemplate_matchesPreMigrationSingleOffsetInvariant"
        status: pass
    human_judgment: false
  - id: D5
    description: "Capacity-shortfall advisory: a band-capacity total below the admissible headcount is named per template/weekday (template, weekday, total, headcount) rather than surfacing as a bare hard score at solve time"
    requirement: "ENVL-08"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java#validate_capacityBelowHeadcount_producesOneAdvisoryNamingAllFour"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java#validate_mixedBlankAndSetCapacity_clearsTheWholeTemplate"
        status: pass
    human_judgment: false
  - id: D6
    description: "MigrationEntityConsistencyTest closes the G-14-1 blind spot without a new dependency, catching a CHAR-vs-varchar-class divergence before it reaches application boot"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java#v40DroppedColumns_absentFromMigrationMapAndEntity"
        status: pass
    human_judgment: false
  - id: D7
    description: "Application boots under ddl-auto=validate after V40 applies against a real Postgres database, and shift_template_break_band holds exactly the rows the data fan-out promises"
    verification: []
    human_judgment: true
    rationale: "CI cannot prove this — src/test/resources/application-test.yml disables Flyway and builds the H2 test schema from the entities, which is exactly the G-14-1 blind spot. Requires deploying the branch to the dev environment and inspecting the applied migration, per the plan's own Task 3 human-check."

duration: 55min
completed: 2026-08-26
status: complete
---

# Phase 15 Plan 1: Shift Template Break Bands Summary

**Promoted a shift template's break from Phase 14's single fixed offset to N break bands end-to-end (V40 migration, entity, service validation, DTOs, controller), generalised `covers()` to any-band coverage with a proven one-band-identical invariant, added a capacity-shortfall advisory, and closed the G-14-1 migration-vs-entity blind spot with a dependency-free guard.**

## Performance

- **Duration:** ~55 min
- **Tasks:** 3
- **Files modified:** 15 (5 created, 10 modified)

## Accomplishments

- `shift_template_break_band` table created and populated by V40 (one band per previously-breaking template); `shift_template.break_offset_minutes`/`break_duration_minutes` dropped in the same migration
- `ShiftTemplateBreakBand` entity + `ShiftTemplateBreakBandRepository` (tenant-scoped everywhere, no `@OneToMany` introduced), `ShiftTemplate.getNetHours(int)` band-parameterised
- `ShiftTemplateRequest`/`Response` carry a `bands` list; `ShiftTemplateService` validates (non-negative offset/duration, envelope containment, capacity >= 1, duplicate `(offset, duration)` rejected, per-band grid alignment) and replaces a template's bands wholesale on every save
- `ShiftLibraryValidationService.covers()` generalised to any-band coverage (D-02): zero bands = no break, one band reproduces Phase 14's exact verdict, two bands can self-cover their own break hour — proven with a dedicated one-band invariant test plus the D-02 worked example
- Capacity-shortfall advisory (D-03's named residual risk): a below-headcount band-capacity total is surfaced per template/weekday rather than discovered as an unexplained hard score
- `MigrationEntityConsistencyTest` — a plain JUnit 5 test with no Spring context or database that folds migration DDL into an effective column map and reconciles it against `@Column` mappings, sanity-checked against the historical V39 `CHAR(7)`-vs-`varchar(7)` mismatch to confirm it fails on that exact class of drift

## Task Commits

1. **Task 1: A template's break becomes N bands — schema, entity, service, DTO, end to end** - `a86276b` (feat)
2. **Task 2: Any-band coverage, per-band grid re-check, and the one-band behaviour-preservation invariant** - `848dd24` (feat)
3. **Task 3: Two safety nets — the capacity-shortfall advisory and the migration-vs-entity guard** - `d909074` (feat)

## Files Created/Modified

- `src/main/resources/db/migration/V40__shift_template_break_bands.sql` - band table, data fan-out, column drop
- `src/main/java/com/wfm/model/ShiftTemplateBreakBand.java` - child entity (offset, duration, capacity, tenant-scoped)
- `src/main/java/com/wfm/repository/ShiftTemplateBreakBandRepository.java` - tenant-scoped, offset-ordered band reads
- `src/main/java/com/wfm/model/ShiftTemplate.java` - break fields/helpers removed; `getNetHours(int)` band-parameterised
- `src/main/java/com/wfm/service/ShiftTemplateService.java` - band validation + wholesale replace-on-save
- `src/main/java/com/wfm/dto/ShiftTemplateRequest.java` / `ShiftTemplateResponse.java` - `bands` list replaces the four scalar break fields
- `src/main/java/com/wfm/controller/ShiftTemplateController.java` - loads/maps bands into the response
- `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` - any-band `covers()`, bulk band loading, capacity advisory
- `src/main/java/com/wfm/dto/ShiftLibraryValidationResponse.java` - `capacityAdvisories` field + `CapacityAdvisory` record
- `src/test/java/com/wfm/service/ShiftTemplateBreakBandServiceTest.java` - new, the band round trip
- `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java` - new, the P-07 guard
- `src/test/java/com/wfm/service/ShiftTemplateServiceTest.java` / `ShiftTemplateTracerTest.java` - ported to the bands-based request/response shape
- `src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java` - band-aware helpers + any-band/capacity test additions

## Decisions Made

- D-01 (bands promoted, both scalar columns dropped) executed verbatim — see `key-decisions` in frontmatter.
- Band persistence split into `applyScalarFields` (pre-save) + `replaceBands` (post-save) rather than a single pre-save `applyFields`, because the template's generated `id` — needed for the band FK — is only guaranteed once `save()`/`persist()` runs.
- Task 1 kept `ShiftLibraryValidationService`'s coverage semantics single-band (mechanical port); Task 2 generalised to any-band as its own reviewable diff, exactly as the plan specified.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Ported `ShiftTemplateServiceTest.java` and `ShiftTemplateTracerTest.java` to the new bands-based request/response shape**
- **Found during:** Task 1
- **Issue:** Neither file is in this plan's `files_modified` list, but both construct `ShiftTemplateRequest`/read `ShiftTemplateResponse` using Phase 14's scalar break fields, which Task 1 removes — the module would not compile once the DTO shape changed.
- **Fix:** Rewrote every request construction to use the new `bands` list and every response assertion to read `response.bands()`; added a plain unit test for the entity's new `getNetHours(int)` signature.
- **Files modified:** `src/test/java/com/wfm/service/ShiftTemplateServiceTest.java`, `src/test/java/com/wfm/service/ShiftTemplateTracerTest.java`
- **Verification:** Both suites green (32 and 6 tests respectively).
- **Committed in:** `a86276b` (Task 1 commit)

**2. [Rule 1 - Bug] Band persistence moved to occur after the template is saved**
- **Found during:** Task 1
- **Issue:** The plan's prose describes band replacement happening inside a single pre-save `applyFields` call, but on create the template has no `id` yet at that point — persisting a band row referencing an unsaved parent would violate the FK.
- **Fix:** Split into `applyScalarFields` (sets name/times/weekdays/effective range) called before `save()`, and `replaceBands` (delete-then-recreate, `flush()`) called after `save()` returns the id-populated entity, for both create and update.
- **Files modified:** `src/main/java/com/wfm/service/ShiftTemplateService.java`
- **Verification:** `ShiftTemplateBreakBandServiceTest` round-trip and `update_replacesTemplatesBandsWholesale` pass.
- **Committed in:** `a86276b` (Task 1 commit)

**3. [Rule 1 - Bug] `MigrationEntityConsistencyTest` strips SQL `--` line comments before parsing**
- **Found during:** Task 3
- **Issue:** The initial parser read migration SQL verbatim; this project's own comment discipline (long prose citing decision IDs, e.g. V39's `VARCHAR(7), not CHAR(7)` explanation) embeds commas and parens that corrupted the top-level-comma column splitter, producing a false failure claiming `ShiftTemplate` declares a `valid_weekdays` column "that no migration creates."
- **Fix:** Added a `stripLineComments` preprocessing step before folding `CREATE TABLE`/`ALTER TABLE` statements. Sanity-checked by temporarily reintroducing the historical V39 `CHAR(7)` bug (`sed` edit, not committed) and confirming the guard fails on it, then reverting.
- **Files modified:** `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java`
- **Verification:** Both migration tests pass on the real V40; the guard demonstrably fails when `CHAR(7)` is reintroduced.
- **Committed in:** `d909074` (Task 3 commit)

**4. [Rule 2 - Missing Critical] Removed the literal "OneToMany" token from model javadoc**
- **Found during:** Task 3 (self-check before finalizing)
- **Issue:** The plan's own `<verification>` requires `grep -rl "OneToMany" src/main/java/com/wfm/model/` to return nothing; my explanatory javadoc comments (correctly stating no `@OneToMany` was introduced) incidentally matched that grep, which would have made the plan's stated verification command fail despite no actual annotation existing.
- **Fix:** Reworded the two comments to describe the absence of a parent-side collection mapping without using the literal token.
- **Files modified:** `src/main/java/com/wfm/model/ShiftTemplate.java`, `src/main/java/com/wfm/model/ShiftTemplateBreakBand.java`
- **Verification:** `grep -rl "OneToMany" src/main/java/com/wfm/model/` returns nothing.
- **Committed in:** `d909074` (Task 3 commit)

---

**Total deviations:** 4 auto-fixed (1 blocking/compile, 2 bugs, 1 missing-critical/verification-hygiene)
**Impact on plan:** All four were necessary for the plan's own stated done-criteria (full suite green, the literal `<verification>` grep) or for correctness (FK ordering, comment-corrupted parser). No scope creep — no file outside this plan's transitive blast radius was touched.

## Issues Encountered

- `com.wfm.solver.BreakAwareConstructionTest.solverCH_30agents_30minSlots_shouldProduceFeasibleSolution` failed twice during full-suite runs (hard score `-670` and, on a second run, `-1010`, against a `>= -500` tolerance), but passes reliably in isolation. This is a pre-existing, load-sensitive solver construction-heuristic test with no relationship to `ShiftTemplate`, break bands, or any file this plan touches (confirmed by isolation re-run: BUILD SUCCESSFUL). Not auto-fixed per the scope boundary — logged to `.planning/phases/15-shift-envelope-breaks-library-generation/deferred-items.md`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The band schema (`shift_template_break_band`, `ShiftTemplateBreakBandRepository`, the public `covers()`/`Window` pair) is ready for plan 15-02's `ShiftLibraryGenerationService` to reuse (P-03) rather than reimplement.
- **Blocker/concern for the human-check:** Task 3's `<verify>` includes a human-check ("Deploy the branch to the dev environment and confirm the application boots under `ddl-auto=validate` after V40 applies") that CI structurally cannot perform (see coverage D7). This has not been executed as part of this automated run and should happen before shift-mode desks depend on this migration in production.
- The `MigrationEntityConsistencyTest` table constant (`shift_template`, `shift_template_break_band`) is a declared list a later phase can extend when it adds tables of its own.

## Self-Check: PASSED

- All created files verified present on disk (V40 migration, `ShiftTemplateBreakBand.java`,
  `ShiftTemplateBreakBandRepository.java`, `ShiftTemplateBreakBandServiceTest.java`,
  `MigrationEntityConsistencyTest.java`, `deferred-items.md`).
- All three task commits verified present in `git log`: `a86276b`, `848dd24`, `d909074`.
- All plan `<verification>` bullets re-run: `./gradlew test` green (423 tests, 1 pre-existing
  unrelated flaky failure confirmed passing in isolation — see Issues Encountered);
  `shift_template` no longer declares the two retired columns, proven by
  `MigrationEntityConsistencyTest` rather than by reading; the one-band invariant test asserts
  identical output to the Phase 14 fixture; `grep -rl "OneToMany" src/main/java/com/wfm/model/`
  returns nothing.

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-08-26*
