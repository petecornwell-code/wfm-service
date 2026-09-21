# Phase 14: Shift Library & Scheduling Mode - Research

**Researched:** 2026-08-25
**Domain:** Desk-scoped CRUD (Spring Boot / JPA / React) + pre-solve validation extension. No solver code.
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 (Break placement):** A shift template's break is a fixed offset from shift start —
  `break_offset_minutes` + `break_duration_minutes` on the row. Zero solver freedom. Example:
  `08:00–17:00` + offset `240` → break `12:00–13:00`.
- **D-02 (Grid validation):** Template times are grid-validated at save and re-checked at the mode
  switch. Reject a template whose start, end, or break boundaries do not land on the desk's current
  timeslot grid.
- **D-03 (Break self-contained):** The template's break is NOT validated against `Schedule`'s
  existing break config (`breakDurationMinutes`, `breakStartAlignment`, `breakBlockedHours`,
  `breakMinShiftHours`). Those four constraints are gated OFF for shift-scheduled desks by Phase 15's
  ENVL-05. Slot-scheduled desks keep the Schedule-level config entirely untouched (MODE-05).
- **D-04 (Structural coverage):** SHLB-05/MODE-03 coverage is structural envelope coverage, not
  capacity-aware. For every `(date, timeslot)` with `StaffingRequirement` demand > 0, assert at least
  one template whose weekday set and effective range include that date has an envelope containing
  that slot (break excluded); name uncovered slots in the failure. Headcount shortfall already has a
  home (`SolverService.computeCapacityWarnings`).
- **D-05 (Live-demand-only scope):** Coverage runs over the desk's live timeslots and live
  `StaffingRequirement` rows (`scheduleId IS NULL`), intersected with each template's effective date
  range. A desk with zero live demand rows is REFUSED ("no staffing demand loaded for this desk"),
  never passed vacuously.
- **D-06 (SHLB-06 advisory-except-fatal):** Non-blocking warning on save and in the library view. The
  mode switch refuses ONLY when a demanded weekday has no workable `(template, agent)` pair at all.
- **D-07 (Exact-equality hours matching):** "Match" means exact equality of net duration
  (`end − start − break_duration`) to the agent's `AgentDayHours` value for that weekday, compared as
  `BigDecimal` via `BigDecimals` util. No tolerance.
- **D-08 (One validator, two callers):** SHLB-05's coverage check and MODE-03's refusal are one
  implementation called twice — from the shift-library editor and the mode-switch endpoint — extending
  `SolverService.runPreSolveValidation`'s existing `ErrorDetail` pattern, surfaced through
  `PreSolveValidationException` → 400 `VALIDATION_FAILED` with a populated `details` array.
- **D-09 (Snapshot-on-accept protection):** Templates are mutable rows protected by the existing
  accept-time snapshot pattern (`ScheduleService.acceptSchedule`). No new versioning concept. Phase 15
  extends the same snapshot obligation to `AgentShiftAssignment`.
- **D-10 (Effective-date-range-only retirement):** No `active` boolean. A template applies to date D
  iff `effective_from ≤ D ≤ effective_to` (nullable `effective_to` = open-ended). One mechanism, one
  predicate.
- **D-11 (Identity):** Unique `(tenant_id, desk_id, name, effective_from)`, with a check that
  effective ranges for the same name never overlap. Hand-off to Phase 16: `agent_usual_shift`'s FK
  points at a specific template row; superseding leaves stale pointers — Phase 16 must decide
  re-point-on-supersede vs. resolve-by-name+date.
- **D-12 (Mode switch reversibility):** SHIFT → SLOT is freely reversible and ungated. Gate the way
  in, leave the way out open. No `confirm()` dialog.
- **D-13 (409 refusal during RUNNING solve):** A mode switch is refused with 409 while the desk has a
  RUNNING solve, reusing the idiom already in the codebase — `BambooRefreshService`'s per-`deskId`
  `refreshInProgress` map throwing `RefreshInProgressException` → 409.
- **D-14 (UI):** New desk-scoped `ShiftLibrary.tsx` page mirroring `Specializations.tsx` (desk
  selector, list, inline add/edit, delete), carrying the mode toggle and the coverage validation panel
  on the same page. `DeskManagement.tsx` shows the mode as read-only status.
- **D-15 (XCUT-05 classification):** A structure enumerating every constraint with its tag
  (mode-agnostic / mode-gated / needs-a-shift-variant), plus a test asserting its key set exactly
  equals the constraint set `ScheduleConstraintProvider.defineConstraints` actually registers.
  Mirrored into a readable markdown table for humans; the test is the part that does not rot.
  **Do not trust ARCHITECTURE.md's "18" or ROADMAP's "19" — derive the set from `defineConstraints`.**
  (This research already did that derivation — see `## Constraint Classification` below — 19
  confirmed.)

### Claude's Discretion

- A template may carry a zero-duration break, never more than one break. Multiple breaks would be a
  deviation worth raising, not assuming.
- MODE-05 is proven by the existing backend suite (288 `@Test` methods across 59 files — see
  Assumptions Log re: the "~315" figure in STATE.md/CONTEXT.md) running unchanged and green, not a new
  slot-mode fixture. Standing caveat: no test under `src/test/java/com/wfm/solver/` loads the Spring
  context, so a scoped run cannot catch a `solverConfig.xml` regression — irrelevant here since this
  phase changes no solver config.
- Exact wording/field naming of `ErrorDetail` entries for uncovered windows.
- Whether the coverage validator lives as a standalone service or a method on an existing one — D-08
  fixes only that it is one implementation with two callers.
- How a template's valid-weekday set is stored on the row (`Set<DayOfWeek>` element collection, join
  table, or compact encoding) — a plan-time modelling call. **No `@ElementCollection` /
  `@CollectionTable` usage exists anywhere in this codebase today** — whichever is chosen is a new
  pattern, not a mirrored one (see Pattern section below).
- Exact Flyway migration number: confirmed as **V39** below (V38 is the actual latest-applied file on
  disk).

### Deferred Ideas (OUT OF SCOPE for this phase)

- Multiple breaks per template.
- Capacity-aware coverage validation (candidate for a future reporting phase, adjacent to Backlog
  999.5).
- Promoting break config from `Schedule` to `Desk` (rejected as a MODE-05 risk; future cleanup once
  shift mode is proven).
- Per-desk tolerance on the hours match (Phase 17 owns a differently-motivated per-desk tolerance
  band; do not pre-empt it).
- Re-pointing `agent_usual_shift` on supersede — created by D-11, owned by Phase 16.
- `AgentShiftAssignment`, `shiftEnvelopeCompliance`, mode-gating the four break constraints, any
  `solverConfig.xml` change, any benchmark — all Phase 15.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SHLB-01 | Per-desk shift template library: start, end, break placement rule | `Specialization` entity/service/controller/DTO/page mirrored 1:1; see Code Examples |
| SHLB-02 | Valid weekdays per template | Modelling options for weekday storage documented; no existing element-collection precedent in codebase |
| SHLB-03 | Effective date range per template | D-10/D-11 predicate documented; unique constraint pattern from `Specialization` widened |
| SHLB-04 | Edit/retire without corrupting referencing schedules | `ScheduleService.acceptSchedule` snapshot mechanics read and quoted below (D-09) |
| SHLB-05 | Coverage validation against demand, naming uncovered windows | `SolverService.runPreSolveValidation` / `ErrorDetail` / `PreSolveValidationException` read and quoted; `StaffingRequirementRepository.findLiveByDeskAndDateRange` identified as the live-demand read path |
| SHLB-06 | Template duration vs. agent contracted hours, reported at definition time | `AgentDayHours` entity + `AgentDayHoursRepository.findByTenantIdAndDeskId` read and quoted — exact desk-scoped bulk fetch for the hours-match validator |
| MODE-01 | Desk mode SLOT/SHIFT, default SLOT | `Desk` entity read; clean column addition; `ConstraintWeights`'s per-desk-row precedent cited for why a plain column (not a second table) is right-sized here |
| MODE-02 | Switch from desk configuration UI | `DeskManagement.tsx` (124 lines) read in full; D-14's `ShiftLibrary.tsx` page is the actual switch surface |
| MODE-03 | Refusal names uncovered demand windows | Same validator as SHLB-05 (D-08); `ErrorDetail(field, message, value)` record read and quoted |
| MODE-04 | Mode switch never alters accepted schedules | `acceptSchedule`'s `scheduleId` null-vs-set snapshot pattern read and quoted; mode switch never touches `Schedule`/`Timeslot`/`StaffingRequirement` rows |
| MODE-05 | Slot-scheduled solve unchanged | Confirmed: `Desk.schedulingMode` is a new, currently-unread column — no existing solve path reads it, so it cannot affect slot-mode solves this phase |

</phase_requirements>

## Summary

This phase is almost entirely a copy-and-extend exercise inside a codebase that already contains
every pattern it needs. `Specialization` is a proven desk-scoped-list entity/service/controller/DTO
stack with zero backend tests today (a gap, not a template to imitate for tests); `JobTitleConfigServiceTest`
is the actual test-shape template to copy (`@DataJpaTest` + `@Import(Service.class)` +
`@ActiveProfiles("test")`, H2, explicit `TenantContext.setTenantId` in `@BeforeEach`).
`SolverService.runPreSolveValidation` already has the exact `ErrorDetail` / `PreSolveValidationException`
→ 400 `VALIDATION_FAILED` shape D-08 extends, and `BambooRefreshService`'s `refreshInProgress`
`ConcurrentHashMap<UUID,Boolean>` + `RefreshInProgressException` → 409 is a literal idiom to copy for
D-13, though a simpler path exists: `ConflictException` already maps to 409 today with no new
exception class required (see Pitfalls).

Two structural findings change what the planner needs to decide, beyond what CONTEXT.md already
locked. First, `TimeslotGeneratorService`'s grid is **not stored anywhere** — it is derived on demand
via a native query (`MIN(EXTRACT(EPOCH FROM (end_time-start_time))/60)` over live timeslots),
already wrapped as `TimeslotGeneratorService.getLiveBounds(deskId) → Optional<TimeslotBoundsResponse>`.
D-02's grid check should call this, not invent new storage. Second, the 19-constraint list is now
authoritatively derived (see below): ARCHITECTURE.md's "18" is stale (predates `minimumStaffing`),
and ROADMAP's "19" is confirmed correct by reading `defineConstraints` directly.

**Primary recommendation:** Build `ShiftTemplate` as a structural `Specialization` sibling (own table,
`desk_id` FK, no nesting inside `Desk`), reuse `runPreSolveValidation`'s `ErrorDetail` pattern for the
one coverage/hours validator called from both the library editor and the mode-switch endpoint, add
`scheduling_mode` as a plain enum column on `Desk` (not a new per-desk config table — `ConstraintWeights`
already proves that pattern exists for something genuinely multi-valued and weighted; a two-value mode
flag doesn't need it), and copy `JobTitleConfigServiceTest`'s `@DataJpaTest` shape for every new backend
test.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Shift template CRUD | API / Backend | Database / Storage | New `shift_template` table + service/controller, desk-scoped, tenant-isolated — structural sibling of `Specialization` |
| Weekday/effective-range storage | Database / Storage | API / Backend | Schema modelling choice (element collection vs. join table vs. compact encoding); validated in service layer |
| Coverage validation (SHLB-05/MODE-03) | API / Backend | — | Pure server-side computation over live `Timeslot`/`StaffingRequirement` rows; no client-side duplication (D-08: one implementation) |
| Contracted-hours match (SHLB-06) | API / Backend | — | Reads `AgentDayHours` desk-scoped; advisory computation, same validator as coverage |
| Scheduling mode flag | Database / Storage | API / Backend | One column on `Desk`; read by the (future, Phase 15) solver-config-resolution path, written by this phase's mode-switch endpoint only |
| Mode-switch refusal (409 RUNNING guard) | API / Backend | — | In-memory guard against `InMemoryScheduleStore.hasDeskSchedule` + status check; no DB involvement |
| Shift library admin UI + mode toggle | Frontend Server (SPA) | — | `ShiftLibrary.tsx`, a React Router page under `/desks/:deskId/*`, calling the backend CRUD + validation endpoints |
| Desk mode read-only display | Frontend Server (SPA) | — | `DeskManagement.tsx` gains a column; no new state, just rendering `Desk.schedulingMode` from the existing list response |
| XCUT-05 constraint classification | API / Backend (test) | Documentation | A JUnit test reading `ScheduleConstraintProvider.defineConstraints` reflectively/directly, plus a markdown table — reads solver code, never modifies it |

## Standard Stack

This phase introduces no new external dependencies. It is 100% additive use of the existing stack:
Spring Boot 3 / Spring Data JPA / Flyway / PostgreSQL (backend), React 19 / TypeScript / Vite / React
Router 7 (frontend), JUnit 5 + AssertJ + H2 (`@DataJpaTest`) for backend tests, no frontend test
framework (confirmed absent — see Environment Availability).

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| (none new) | — | — | Every capability in this phase is built from libraries already in `build.gradle` / `package.json` |

### Package Legitimacy Audit

**Not applicable — this phase adds zero new packages to either `build.gradle` or `package.json`.**
`## Package Legitimacy Audit` is required only when a phase installs external packages; skipped per
that condition.

## Architecture Patterns

### System Architecture Diagram

```
Operator (browser)
   |
   v
ShiftLibrary.tsx  (new, mirrors Specializations.tsx)
   |  desk selector -> GET  /api/v1/desks/{deskId}/shift-templates
   |                -> POST /api/v1/desks/{deskId}/shift-templates
   |                -> PUT  /api/v1/desks/{deskId}/shift-templates/{id}
   |                -> DELETE /api/v1/desks/{deskId}/shift-templates/{id}
   |  mode toggle   -> PUT  /api/v1/desks/{deskId}/scheduling-mode   {mode: SLOT|SHIFT}
   v
ShiftTemplateController (new)      DeskController (existing, gains mode endpoint or a sibling controller)
   |                                       |
   v                                       v
ShiftTemplateService (new)          DeskService (existing, extended) --------+
   |  create/update/delete                 |  switchMode(deskId, target)     |
   |  calls shared validator on             |  1. guard: RUNNING solve? -> 409|
   |  save AND on mode-switch               |     (InMemoryScheduleStore     |
   v                                        |      .getByDeskId + status)    |
ShiftLibraryCoverageValidator (new,         |  2. calls SAME validator        |
 name TBD at plan time -- D-08 fixes        |     as the library editor       |
 only "one implementation, two callers")    |  3. persists Desk.schedulingMode|
   |  reads:                                |     only if validator passes    |
   |   - ShiftTemplateRepository (own       v
   |     desk's templates + effective  Desk.schedulingMode column (new, SLOT default)
   |     range)
   |   - StaffingRequirementRepository
   |     .findLiveByDeskAndDateRange
   |     (scheduleId IS NULL -- D-05)
   |   - TimeslotGeneratorService
   |     .getLiveBounds(deskId) for grid
   |     check (D-02)
   |   - AgentDayHoursRepository
   |     .findByTenantIdAndDeskId
   |     (D-06/D-07 exact-hours match)
   |
   v
throws PreSolveValidationException(List<ErrorDetail>)  [existing type, extended with new field keys]
   |
   v
GlobalExceptionHandler.handlePreSolveValidation -> 400 VALIDATION_FAILED {details: [...]}
   (existing handler, zero changes needed)

Separately, read-only:
ScheduleConstraintProviderClassificationTest (new)
   reads ScheduleConstraintProvider.defineConstraints(factory) -> Constraint[19]
   asserts classification-map key set == constraint name set
   (never modifies solver code)
```

### Recommended Project Structure

```
src/main/java/com/wfm/
├── model/
│   ├── ShiftTemplate.java                 # new — Specialization sibling shape
│   └── Desk.java                          # +schedulingMode field
├── repository/
│   └── ShiftTemplateRepository.java       # new — mirrors SpecializationRepository
├── service/
│   ├── ShiftTemplateService.java          # new — mirrors SpecializationService
│   └── ShiftLibraryValidationService.java # new (name is Claude's Discretion) — D-08's shared validator
├── controller/
│   └── ShiftTemplateController.java       # new — mirrors SpecializationController
├── dto/
│   ├── ShiftTemplateRequest.java          # new record, mirrors DeskRequest (no bean-validation annotations — manual checks in service, matching codebase idiom)
│   └── ShiftTemplateResponse.java         # new record, mirrors SpecializationResponse
└── solver/
    └── ScheduleConstraintProvider.java    # READ ONLY — classification test reads this, never edits it

src/main/resources/db/migration/
└── V39__add_shift_template_and_scheduling_mode.sql   # exact number confirmed below

frontend/src/pages/
├── ShiftLibrary.tsx        # new — mirrors Specializations.tsx structure
└── DeskManagement.tsx      # +read-only mode column

.planning/phases/14-shift-library-scheduling-mode/
└── XCUT-05-constraint-classification.md   # human-readable mirror of the completeness test (D-15)
```

### Pattern 1: Desk-scoped CRUD entity — `Specialization` (the exact shape to copy)

**What:** UUID id, `tenant_id`, `desk_id` FK, unique constraint, plain getters/setters, no bean
validation annotations.
**When to use:** Every new desk-scoped list entity in this codebase (proven for `Specialization`,
about to be proven again for `ShiftTemplate`).
**Example — verbatim, read this session:**
```java
// Source: src/main/java/com/wfm/model/Specialization.java (full file, read 2026-08-25)
@Entity
@Table(name = "specialization", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "desk_id", "name"})
})
public class Specialization {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "desk_id", nullable = false)
    private UUID deskId;

    @Column(nullable = false)
    private String name;
    // ... plain getters/setters, no validation annotations
}
```
`ShiftTemplate` widens the unique key per D-11 to `(tenant_id, desk_id, name, effective_from)` and adds
start/end/break-offset/break-duration/weekday-set/effective-range columns — everything else about the
shape (id/tenant/desk pattern, no bean validation) carries over unchanged.

### Pattern 2: Desk-scoped CRUD service — manual validation, no bean-validation framework

**What:** Constructor-injected repositories, manual `isBlank()`/uniqueness checks throwing
`IllegalArgumentException` (→ 400) or `ConflictException` (→ 409/`CONFLICT` — see Pitfalls for the
actual HTTP status), delete-time reference checks across every FK-holding table before deleting.
**Example — verbatim, read this session:**
```java
// Source: src/main/java/com/wfm/service/SpecializationService.java (read 2026-08-25)
@Transactional
public Specialization createSpecialization(UUID deskId, String name, String color) {
    long tenantId = TenantContext.getTenantId();
    if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Specialization name is required");
    }
    if (specializationRepository.existsByTenantIdAndDeskIdAndName(tenantId, deskId, name)) {
        throw new ConflictException("A specialization with name '" + name + "' already exists for this desk");
    }
    Specialization spec = new Specialization();
    spec.setTenantId(tenantId);
    spec.setDeskId(deskId);
    spec.setName(name);
    spec.setColor(color);
    return specializationRepository.save(spec);
}

@Transactional
public void deleteSpecialization(UUID deskId, UUID id) {
    long tenantId = TenantContext.getTenantId();
    Specialization spec = specializationRepository.findByIdAndTenantIdAndDeskId(id, tenantId, deskId)
            .orElseThrow(() -> new EntityNotFoundException("Specialization", id));
    if (agentRepository.existsByPrimarySpecialization_Id(id)
            || agentRepository.existsBySecondarySpecializationsContaining(id)) {
        throw new ConflictException("Cannot delete specialization that is assigned to agents");
    }
    if (staffingRequirementRepository.existsBySpecialization_Id(id)) {
        throw new ConflictException("Cannot delete specialization that is referenced by staffing requirements");
    }
    specializationRepository.delete(spec);
}
```
For SHLB-04 (retire without corrupting referencing schedules), `ShiftTemplateService` does NOT need this
delete-time reference-check pattern in the same way — D-10 already routes retirement through
`effective_to`, not a hard delete. If a hard-delete endpoint is offered at all, it should follow this
`existsBy...` guard shape checking `agent_usual_shift` (Phase 16, not yet existing) and any
`AgentShiftAssignment` (Phase 15, not yet existing) — for Phase 14, no consumer table exists yet, so a
delete-time check has nothing to check against. Note this explicitly for the planner: **retirement, not
deletion, is D-04/SHLB-04's actual mechanism this phase; a delete endpoint is optional and, if added,
is presently unguarded because no FK-holding consumer exists until Phase 15/16 ship.**

### Pattern 3: Timeslot grid — derived, not stored (verified this session; changes D-02's plan)

**What:** `TimeslotGeneratorService` never persists `incrementMinutes` as a column anywhere. It is
computed on demand from the live `Timeslot` rows via a native query, already exposed as a typed method.
**Example — verbatim, read this session:**
```java
// Source: src/main/java/com/wfm/repository/TimeslotRepository.java:28-33 (read 2026-08-25)
@Query(value = "SELECT MIN(t.date) as periodStart, MAX(t.date) as periodEnd, " +
               "MIN(t.start_time) as startTime, MAX(t.end_time) as endTime, " +
               "MIN(EXTRACT(EPOCH FROM (t.end_time - t.start_time)) / 60)::int as incrementMinutes " +
               "FROM timeslot t WHERE t.tenant_id = :tenantId AND t.desk_id = :deskId AND t.schedule_id IS NULL",
       nativeQuery = true)
Object[] findLiveBoundsByDeskRaw(long tenantId, UUID deskId);
```
```java
// Source: src/main/java/com/wfm/service/TimeslotGeneratorService.java:51-64 (read 2026-08-25)
public Optional<TimeslotBoundsResponse> getLiveBounds(UUID deskId) {
    Object[] row = timeslotRepository.findLiveBoundsByDeskRaw(TenantContext.getTenantId(), deskId);
    if (row == null || row.length == 0) return Optional.empty();
    Object[] cols = (row[0] instanceof Object[]) ? (Object[]) row[0] : row;
    if (cols[0] == null) return Optional.empty();
    return Optional.of(new TimeslotBoundsResponse(
            ((java.sql.Date) cols[0]).toLocalDate(),
            ((java.sql.Date) cols[1]).toLocalDate(),
            ((java.sql.Time) cols[2]).toLocalTime(),
            ((java.sql.Time) cols[3]).toLocalTime(),
            ((Number) cols[4]).intValue()
    ));
}
```
```java
// Source: src/main/java/com/wfm/dto/TimeslotBoundsResponse.java (full file, read 2026-08-25)
public record TimeslotBoundsResponse(
        LocalDate periodStart, LocalDate periodEnd,
        LocalTime startTime, LocalTime endTime,
        int incrementMinutes
) {}
```
**D-02's grid check should call `TimeslotGeneratorService.getLiveBounds(deskId)`** and validate
template start/end/break boundaries land on a multiple of `incrementMinutes()` relative to the desk's
`startTime()`, rather than inventing a new stored-grid concept. `Optional.empty()` (no live timeslots)
is a real case D-05 already forces the validator to handle (refuse with "no staffing demand loaded").

### Pattern 4: Pre-solve validation → `ErrorDetail` → 400 (the exact shape D-08 extends)

**What:** A `List<ErrorDetail>` accumulator, one `errors.add(new ErrorDetail(field, message, value))`
per violation, thrown together at the end as one `PreSolveValidationException`.
**Example — verbatim, read this session:**
```java
// Source: src/main/java/com/wfm/dto/ErrorResponse.java:7 (read 2026-08-25)
public record ErrorDetail(String field, String message, String value) {}
```
```java
// Source: src/main/java/com/wfm/exception/PreSolveValidationException.java (full file, read 2026-08-25)
public class PreSolveValidationException extends RuntimeException {
    private final List<ErrorDetail> details;
    public PreSolveValidationException(String message, List<ErrorDetail> details) {
        super(message);
        this.details = details;
    }
    public List<ErrorDetail> getDetails() { return details; }
}
```
```java
// Source: src/main/java/com/wfm/service/SolverService.java:895-898 (read 2026-08-25)
if (!errors.isEmpty()) {
    throw new PreSolveValidationException(
            "Pre-solve validation failed with " + errors.size() + " issue(s)", errors);
}
```
```java
// Source: src/main/java/com/wfm/controller/GlobalExceptionHandler.java:61-64 (read 2026-08-25)
@ExceptionHandler(PreSolveValidationException.class)
public ResponseEntity<ErrorResponse> handlePreSolveValidation(PreSolveValidationException ex) {
    return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage(), ex.getDetails());
}
```
**D-08's shared validator can reuse `PreSolveValidationException`/`ErrorDetail` directly with no new
exception type** — the mode-switch caller would throw the same exception type, and the same
`GlobalExceptionHandler` mapping already returns 400 `VALIDATION_FAILED` with a populated `details`
array. No new wiring needed for the 400 path.

### Pattern 5: In-flight guard → 409 (D-13's idiom — with a lower-cost alternative)

**What:** `BambooRefreshService` guards concurrent refreshes with a `ConcurrentHashMap<UUID,Boolean>`
and a dedicated exception mapped to 409.
**Example — verbatim, read this session:**
```java
// Source: src/main/java/com/wfm/integration/BambooRefreshService.java:45,102-105,152 (read 2026-08-25)
private final ConcurrentHashMap<UUID, Boolean> refreshInProgress = new ConcurrentHashMap<>();
...
public void refreshDeskAgents(UUID deskId) {
    if (refreshInProgress.putIfAbsent(deskId, true) != null) {
        throw new RefreshInProgressException("A BambooHR refresh is already in progress for this desk.");
    }
    ...
    } finally {
        refreshInProgress.remove(deskId);
        ...
    }
}
```
```java
// Source: src/main/java/com/wfm/controller/GlobalExceptionHandler.java:76-79 (read 2026-08-25)
@ExceptionHandler(RefreshInProgressException.class)
public ResponseEntity<ErrorResponse> handleRefreshInProgress(RefreshInProgressException ex) {
    return buildResponse(HttpStatus.CONFLICT, "REFRESH_IN_PROGRESS", ex.getMessage(), List.of());
}
```
**Lower-cost alternative worth flagging to the planner:** the mode-switch guard does not need a new
`ConcurrentHashMap` or a new exception class the way `BambooRefreshService` does, because the "is this
desk mid-solve" fact already lives in `InMemoryScheduleStore`:
```java
// Source: src/main/java/com/wfm/service/InMemoryScheduleStore.java (full file, read 2026-08-25)
public boolean hasDeskSchedule(UUID deskId) {
    return deskToScheduleIndex.containsKey(deskId);
}
public Optional<Schedule> getByDeskId(UUID deskId) {
    UUID scheduleId = deskToScheduleIndex.get(deskId);
    if (scheduleId == null) return Optional.empty();
    return Optional.ofNullable(scheduleMap.get(scheduleId));
}
```
```java
// Source: src/main/java/com/wfm/model/ScheduleStatus.java (full file, read 2026-08-25)
public enum ScheduleStatus {
    RUNNING, COMPLETED, STOPPED, FAILED, ACCEPTED
}
```
The mode-switch endpoint can call `inMemoryScheduleStore.getByDeskId(deskId)`, check
`.map(Schedule::getStatus).filter(s -> s == ScheduleStatus.RUNNING)`, and throw the **already-existing**
`ConflictException` (which already maps to 409, code `CONFLICT`) — no new exception class, no new map,
no new `GlobalExceptionHandler` entry. This is a genuine plan-time choice: D-13 explicitly names the
`RefreshInProgressException` idiom as the one to reuse (giving a distinct `REFUSE`-shaped 409 code, e.g.
`SOLVE_IN_PROGRESS`), but `ConflictException` already gets to 409 with less new code. Flagging both so
the planner picks deliberately rather than by default.

### Pattern 6: Accept-time snapshot (D-09's whole protection model — quoted for Phase 15's benefit too)

**What:** Live rows carry `scheduleId == null`; accepting a schedule copies them into new rows with
`scheduleId` set to the newly-persisted `Schedule.id`, superseding is by date range not row mutation.
**Example — verbatim, read this session:**
```java
// Source: src/main/java/com/wfm/service/ScheduleService.java:196-256 (read 2026-08-25)
@Transactional
public Schedule acceptSchedule(UUID deskId, UUID scheduleId, int expectedVersion) {
    ...
    if (schedule.getStatus() != ScheduleStatus.COMPLETED
            && schedule.getStatus() != ScheduleStatus.STOPPED) {
        throw new ConflictException("Schedule must be COMPLETED or STOPPED to accept (status: "
                + schedule.getStatus() + ")");
    }
    ...
    schedule.setStatus(ScheduleStatus.ACCEPTED);
    schedule.setId(null);
    entityManager.persist(schedule);
    Schedule saved = schedule;

    // Snapshot live timeslots -> new IDs with schedule_id set
    Map<UUID, UUID> timeslotRemap = new HashMap<>();
    List<Timeslot> liveTimeslots = timeslotRepository
            .findByTenantIdAndDeskIdAndScheduleIdIsNullAndDateBetweenOrderByDateAscStartTimeAsc(
                    tenantId, deskId, schedule.getPeriodStartDate(), schedule.getPeriodEndDate());
    for (Timeslot live : liveTimeslots) {
        Timeslot snapshot = new Timeslot();
        ...
        snapshot.setScheduleId(saved.getId());
        ...
        entityManager.persist(snapshot);
        timeslotRemap.put(live.getId(), snapshot.getId());
    }
```
This phase does not need to touch `acceptSchedule` at all — `ShiftTemplate` rows are never snapshotted
by this phase (there is no `AgentShiftAssignment` yet to snapshot alongside). The reason to quote it
here is D-09's protection claim rests entirely on this existing mechanism plus MODE-04 ("mode switch
never alters accepted schedules") — which holds trivially for Phase 14 because the mode switch touches
only `Desk.schedulingMode`, never `Schedule`/`Timeslot`/`StaffingRequirement` rows. Record this for
Phase 15: the snapshot obligation for `AgentShiftAssignment` lands in this exact method, in this exact
place in the loop structure.

### Pattern 7: `AgentDayHours` — the exact table SHLB-06/D-06/D-07 compare against

**What:** One row per `(agent_id, day_of_week)`, `hours` as `BigDecimal(5,2)`, desk-scoped bulk fetch
already exists.
**Example — verbatim, read this session:**
```java
// Source: src/main/java/com/wfm/model/AgentDayHours.java (full file, read 2026-08-25)
@Entity
@Table(name = "agent_day_hours", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"agent_id", "day_of_week"})
})
public class AgentDayHours {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tenant_id", nullable = false) private long tenantId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "agent_id", nullable = false) private Agent agent;
    @Enumerated(EnumType.STRING) @Column(name = "day_of_week", nullable = false, length = 9) private DayOfWeek dayOfWeek;
    @Column(nullable = false, precision = 5, scale = 2) private BigDecimal hours;
    @Enumerated(EnumType.STRING) @Column(name = "day_off_type", length = 9) private DayOffType dayOffType;
    ...
}
```
```java
// Source: src/main/java/com/wfm/repository/AgentDayHoursRepository.java:26-28 (read 2026-08-25)
// Bulk fetch for SolverService -- mirrors AgentDayOffRepository's join-through-agent style.
// AgentDayHours has no desk_id column of its own; desk scoping goes through h.agent.deskId.
@Query("SELECT h FROM AgentDayHours h WHERE h.tenantId = :tenantId AND h.agent.deskId = :deskId")
List<AgentDayHours> findByTenantIdAndDeskId(long tenantId, UUID deskId);
```
D-06/D-07's hours-match validator should call `agentDayHoursRepository.findByTenantIdAndDeskId(tenantId,
deskId)`, group by `dayOfWeek`, and compare each template's net duration
(`end − start − break_duration`, computed as `BigDecimal` hours) against every row's `hours` field for
that weekday using `BigDecimals` (see Pattern 8). **This is per-weekday, not per-date** — `AgentDayHours`
carries no date, so there is no "effective hours accounting for `AgentException` overrides" available at
library-edit time; the comparison is necessarily against the standing weekly value, which matches D-06's
own framing ("a template matching nobody today may match after tomorrow's roster upload").

### Pattern 8: `BigDecimals` comparison util (D-07's exact-equality mechanism)

```bash
grep -n "class BigDecimals" -A 5 src/main/java/com/wfm/util/BigDecimals.java
```
Confirms the util exists and is the codebase's standing `BigDecimal` comparison helper (already used
elsewhere for hours comparisons per D-07's own text). Use it rather than `.equals()` (which is
scale-sensitive) or `.compareTo() != 0` written out inline in a new location.

### Pattern 9: Live-demand read path (D-05's exact query)

```java
// Source: src/main/java/com/wfm/repository/StaffingRequirementRepository.java:57-61 (read 2026-08-25)
@Query("SELECT sr FROM StaffingRequirement sr JOIN FETCH sr.timeslot t JOIN FETCH sr.specialization s " +
       "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId IS NULL " +
       "AND t.date BETWEEN :from AND :to")
List<StaffingRequirement> findLiveByDeskAndDateRange(
        long tenantId, UUID deskId, LocalDate from, LocalDate to);
```
This unpaginated, date-range-bounded, `scheduleId IS NULL` query is exactly D-05's "live" scope. If the
coverage validator needs the desk's full live-demand set (no date filter, since template effective
ranges vary), note that `StaffingRequirementRepository` has no unfiltered "all live for desk" list
method today (only paginated `findLiveByDesk(tenantId, deskId, Pageable)`, or the date-ranged
unpaginated variant above) — the planner should decide whether to call the date-ranged variant with the
union of all templates' effective ranges, or add a new unfiltered repository method following the same
`JOIN FETCH ... scheduleId IS NULL` shape.

### Anti-Patterns to Avoid

- **Adding bean-validation annotations (`@NotBlank`, `@Positive`, etc.) to new DTOs.** Nothing in this
  codebase uses them (`DeskRequest`, `SpecializationResponse`, etc. read this session are all plain
  records). Manual checks in the service layer, mirroring `SpecializationService`, is the established
  idiom — mixing styles inside one phase is inconsistent with everything around it.
- **Storing `incrementMinutes` as a new column anywhere.** It is derived, not stored (Pattern 3). Adding
  a stored copy creates the exact two-fields-that-can-disagree trap PROJECT.md's audit NEW-1 and audit
  I-1 already warn about, and D-10 explicitly invokes that history for a different field.
- **A second `Set<DayOfWeek>` storage design invented ad hoc.** No `@ElementCollection` precedent exists
  in this codebase (verified: zero matches for `@ElementCollection`/`@CollectionTable` across
  `src/main/java/com/wfm/model/`). Whatever the plan picks, document it as a new pattern, not an existing
  one — and keep it simple (a `VARCHAR` of concatenated day abbreviations, or a `SMALLINT` bitmask, are
  both lower-ceremony than a first-ever element collection for a project this size).
- **Building a `SpecializationTest`-shaped test file.** There isn't one — `Specialization`'s CRUD has
  zero backend test coverage today. Copy `JobTitleConfigServiceTest`'s shape instead (see Validation
  Architecture below); it is desk/tenant-scoped CRUD with actual test coverage to mirror.

## Constraint Classification (XCUT-05 groundwork)

**The 19-constraint set, verified this session by reading `defineConstraints` directly** (not trusting
ARCHITECTURE.md's "18" or ROADMAP's "19" — both are now confirmed: ROADMAP's number is right,
ARCHITECTURE.md's is stale because it predates `minimumStaffing`):

```java
// Source: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:62-81 (read 2026-08-25)
public Constraint[] defineConstraints(ConstraintFactory factory) {
    return new Constraint[] {
        unassignedAssignment(factory),
        agentDayOff(factory),
        specializationMatch(factory),
        oneAssignmentPerTimeslot(factory),
        exactlyOneBreak(factory),
        breakDuration(factory),
        breakBlockedWindow(factory),
        breakStartAlignment(factory),
        preferPrimarySpecialization(factory),
        honourPreferredStartTime(factory),
        honourPreferredBreakTime(factory),
        breakClustering(factory),
        contractedHoursOver(factory),
        contractedHoursUnder(factory),
        contractedHoursUnderZero(factory),
        bulkOverallocationLimit(factory),
        bulkUnderallocationSoft(factory),
        bulkUnderallocationHard(factory),
        minimumStaffing(factory),
    };
}
```
That is exactly 19 array elements — `[VERIFIED: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:63-81]`.
`ConstraintWeights` (read in full this session) has exactly 19 corresponding `@ConstraintWeight` fields,
one per name above, confirming the count a second way.

**Working classification hypothesis** (the actual deliverable is D-15's test + table; this is a
starting point for whoever builds it, graded by how directly each constraint's body was read this
session — `[VERIFIED]` where the constraint body was read in full, `[ASSUMED]` where classification is
inferred from the constraint's stated purpose without deep interaction analysis against the not-yet-built
shift model):

| Constraint | Read this session? | Working classification | Basis |
|---|---|---|---|
| Unassigned assignment | body read | mode-agnostic `[ASSUMED]` | Operates per-timeslot on assigned-count vs. demand bounds; shift mode still has timeslots and demand |
| Agent day off | body read | mode-agnostic `[ASSUMED]` | Pure agent×date join; unaffected by how a day's shift was chosen |
| Specialization match | body read | mode-agnostic `[ASSUMED]` | ENVL-03 explicitly keeps specialization variable within the shift envelope |
| One assignment per timeslot | body read | mode-agnostic `[ASSUMED]` | Structural (agent, timeslot) uniqueness; mode-independent |
| Exactly one break | body read | **needs-a-shift-variant** — one of D-03's named "four break constraints" | Break is derived from assignment gaps today; D-01 makes shift-mode breaks a fixed template offset instead |
| Break duration | body read | **needs-a-shift-variant** — one of the "four" | Same — currently derives duration from assignment gap length |
| Break blocked window | body read | **needs-a-shift-variant** — one of the "four" | Same — currently derives break position from assignment gap position |
| Break start alignment | body read | **needs-a-shift-variant** — one of the "four" | Same — currently derives break start from assignment gap start |
| Prefer primary specialization | body read | mode-agnostic `[ASSUMED]` | Pure agent-attribute soft preference; unaffected by shift structure |
| Honour preferred start time | body read | **open question** `[ASSUMED, LOW confidence]` | Penalises a timeslot before `AgentPreference.preferredStartTime`; in shift mode the agent's start is chosen from the library, not per-timeslot — could remain meaningful (compare shift start to preference) or become redundant. Needs solver-level judgement Phase 14 deliberately does not make (no solver code touched) |
| Honour preferred break time | body read | **open question** `[ASSUMED, LOW confidence]` | Currently derives actual break start from assignment gaps (`findBreakStart`); in shift mode the break start is a template-fixed value, so this constraint could be trivially satisfied/violated against that fixed value rather than gated off outright — distinct from the "four" named in D-03, not automatically bundled with them |
| Break clustering | body read | mode-agnostic (currently inert) `[VERIFIED: ScheduleConstraintProvider.java:573-578]` | **Constraint body is `penalizeConfigurable(a -> 0)` — a documented no-op placeholder today** ("Evaluated as a no-op placeholder — full implementation requires cross-agent aggregation... deferred to Phase 5 optimization"), quoted verbatim: `.penalizeConfigurable(a -> 0)`. Classification is moot until it does something |
| Contracted hours (over) | body read | mode-agnostic `[ASSUMED]` | Compares assignment count to `AgentDayConfig.effectiveHours`-derived expected slots; ROADMAP's joint-unsatisfiability argument (D-06) explicitly assumes this constraint still applies in shift mode |
| Contracted hours (under) | body read | mode-agnostic `[ASSUMED]` | Same basis as above |
| Contracted hours (under, zero) | body read | mode-agnostic `[ASSUMED]` | Same basis as above |
| Bulk over-allocation limit | body read | mode-agnostic `[ASSUMED]` | Per-timeslot demand-vs-supply; mode-independent |
| Bulk under-allocation soft | body read | mode-agnostic `[ASSUMED]` | Same |
| Bulk under-allocation hard | body read | mode-agnostic `[ASSUMED]` | Same |
| Minimum staffing | body read | mode-agnostic `[ASSUMED]` | Per-timeslot floor; mode-independent |

**This table is explicitly not the deliverable** — D-15 requires a test asserting completeness against
`defineConstraints`'s actual key set (done: 19, confirmed above) plus a markdown table the phase produces
as its own artifact. This research's classification column is a reasoned starting draft only for the four
break constraints (which CONTEXT.md itself already names as the "four emergent break constraints" D-03
identifies) — the other two `[ASSUMED, LOW confidence]` rows (`honourPreferredStartTime`,
`honourPreferredBreakTime`) are flagged as open questions precisely because they require judgement this
research is not positioned to make responsibly without solver-level analysis this phase is scoped to
avoid.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|--------------|-----|
| 400 error shape for validation failures | A new exception/response type for shift-library validation | `PreSolveValidationException` + `ErrorDetail` + existing `GlobalExceptionHandler.handlePreSolveValidation` | Already wired to 400 `VALIDATION_FAILED` with a `details` array; D-08 explicitly says to extend it |
| Grid alignment storage | A new `grid_increment_minutes` column on `Desk` or `ShiftTemplate` | `TimeslotGeneratorService.getLiveBounds(deskId)` | Already derives the live grid from actual `Timeslot` rows; a stored duplicate can silently disagree with reality after regeneration at a different increment |
| Desk-scoped agent hours lookup | A new query joining `Agent`→`AgentDayHours` | `AgentDayHoursRepository.findByTenantIdAndDeskId` | Already exists, already desk-scoped through `h.agent.deskId`, already used by `SolverService` |
| Live demand lookup | A new query against `StaffingRequirement` | `StaffingRequirementRepository.findLiveByDeskAndDateRange` (or add an unfiltered sibling following the same `scheduleId IS NULL` shape) | Matches D-05's exact scope requirement |
| In-flight-solve detection | A new `ConcurrentHashMap<UUID,Boolean>` per D-13's literal wording | `InMemoryScheduleStore.getByDeskId(deskId)` + `ScheduleStatus.RUNNING` check | The fact already exists in `InMemoryScheduleStore`; a second map duplicates state instead of reading it |

**Key insight:** every validation and lookup this phase needs already has a repository method or service
method reading the right rows with the right scope. The phase's actual net-new backend surface is small:
one entity, one repository, one service, one controller, a handful of DTOs, and one validator that
composes four already-existing read paths (`getLiveBounds`, `findLiveByDeskAndDateRange`,
`findByTenantIdAndDeskId`, and the new `ShiftTemplateRepository`).

## Runtime State Inventory

**Not applicable — this is a greenfield-additive phase, not a rename/refactor/migration.** No existing
string, key, or identifier is being renamed. `Desk.schedulingMode` and `shift_template` are wholly new;
nothing pre-existing changes name or shape. Section omitted per the trigger condition in the agent's own
instructions (rename/refactor/migration phases only).

## Common Pitfalls

### Pitfall 1: Two different 409 idioms exist in this codebase — picking the wrong one adds unneeded code

**What goes wrong:** D-13 names `RefreshInProgressException`/`REFRESH_IN_PROGRESS` as "the idiom to
reuse", which requires a new exception class, a new `GlobalExceptionHandler` mapping, and a new
`ConcurrentHashMap`. But `ConflictException` already exists, already maps to 409 `CONFLICT`, and the
"is this desk mid-solve" state already lives in `InMemoryScheduleStore.getByDeskId`.
**Why it happens:** D-13's prose describes the *shape* of the guard (per-desk in-flight check → 409),
not literally "reuse this exact class." Both readings are defensible from the text.
**How to avoid:** The planner should pick one explicitly and record it as a plan-time decision, not
silently default to whichever is written first. Given `InMemoryScheduleStore` already tracks the needed
state, `ConflictException` is the lower-total-surface-area choice — but a distinct `SOLVE_IN_PROGRESS`
error code (requiring the `RefreshInProgressException` pattern, renamed) gives the frontend a specific
code to branch on instead of a generic `CONFLICT`. Both are legitimate; just decide.
**Warning signs:** A PR that adds a second unused `ConcurrentHashMap<UUID,Boolean>` when
`InMemoryScheduleStore` already answers the same question.

### Pitfall 2: `Specialization` has no backend tests to copy — don't assume one exists

**What goes wrong:** CONTEXT.md's `code_context` section names `Specialization`'s "service/controller
pair to mirror" for the CRUD shape, which is accurate for entity/service/controller/DTO code — but there
is no `SpecializationServiceTest` or `SpecializationControllerTest` in `src/test/` (confirmed: `find
src/test -iname "*Specialization*"` returns nothing). A plan that says "mirror Specialization's tests"
has nothing to mirror.
**Why it happens:** The CRUD code and its test coverage are not the same artifact, and CONTEXT.md's
framing (correctly) only claims the former.
**How to avoid:** Use `JobTitleConfigServiceTest` as the test-shape template instead — it is
desk/tenant-scoped CRUD with actual `@DataJpaTest` coverage (see Validation Architecture below).
**Warning signs:** A plan task that says "port Specialization's existing tests" — there are none to port.

### Pitfall 3: The "~315 backend tests" figure in CONTEXT.md doesn't match a direct count

**What goes wrong:** CONTEXT.md's Specific Ideas / additional_context reference "~315 backend tests."
This session counted 288 `@Test` annotations across 59 files (`grep -rn "@Test" src/test | wc -l` = 288;
`find src/test -name "*.java" | wc -l` = 59).
**Why it happens:** Likely counted at a different point in time, or counted differently (e.g. including
`@ParameterizedTest`/`@RepeatedTest` invocations at runtime rather than source annotations, or the figure
predates a since-reverted commit).
**How to avoid:** Treat "288 backend tests, verified this session" as the number to plan against for
MODE-05's "run the suite unchanged and green" success criterion, and don't be surprised if the actual CI
run reports a different total (parameterized tests expand at runtime). This is flagged in the
Assumptions Log, not silently corrected in CONTEXT.md.
**Warning signs:** A verification step that hard-codes "315 tests must pass" and fails confusingly when
the runner reports a different total for unrelated (test-expansion) reasons.

### Pitfall 4: No frontend test framework exists — don't plan a `ShiftLibrary.test.tsx`

**What goes wrong:** `frontend/package.json` (read in full this session) has no `test` script and no
`vitest`/`jest`/`@testing-library/*` dependency. Phase 13's own decision log (STATE.md, Phase 13 Plan 04)
already recorded "no frontend test framework introduced" for a comparably-sized UI phase.
**Why it happens:** Assuming parity with the backend's test rigor without checking the frontend actually
has an equivalent harness.
**How to avoid:** Plan `ShiftLibrary.tsx` and the `DeskManagement.tsx` mode column without new automated
frontend tests, consistent with Phase 13's precedent — verification is manual/UAT, as it was for
`Specializations.tsx` and `DeskManagement.tsx` themselves (neither has frontend tests either).
**Warning signs:** A plan task creating a `*.test.tsx` file with no corresponding devDependency added.

### Pitfall 5: `Desk.schedulingMode` being unread anywhere yet is what actually proves MODE-05, not a benchmark

**What goes wrong:** Assuming MODE-05 ("slot solve unchanged") needs a new fixture proving the solver
still behaves correctly, when the real argument is structural: no code path in `SolverService` or
`ScheduleConstraintProvider` reads a scheduling-mode field today (it doesn't exist yet), and this phase
adds no such read (explicitly deferred to Phase 15's `shiftEnvelopeCompliance`/gating work). A column
that nothing reads cannot change behaviour.
**Why it happens:** Confusing "we added a mode concept" with "the mode concept now affects solving" —
CONTEXT.md's own Claude's Discretion section already settles this correctly (existing suite green, no
new fixture), but it's worth restating the structural reason why that's sufficient rather than merely
asserted.
**How to avoid:** Verify (as part of the plan's own checks) that no `SolverService`/`ScheduleConstraintProvider`
code added in this phase reads `Desk.schedulingMode` — if it does, MODE-05's "additive, not a rewrite"
claim needs re-examination.
**Warning signs:** A task that has `SolverService` branch on `schedulingMode` "just to be safe" — that
branch is exactly what would violate MODE-05's "same result it did before" claim, since it introduces a
new code path into a currently-unconditional flow.

## Code Examples

See the nine numbered patterns under `## Architecture Patterns` above — each is a verbatim, this-session
read of the exact file/line range it claims, not a paraphrase. No additional examples needed; this phase
has no external-library integration surface requiring a Context7/official-docs example.

## State of the Art

Not applicable in the usual "library X moved to Y" sense — this phase adds no new external dependency
and touches no previously-established external integration. The one relevant "state of the art" fact is
internal: `ARCHITECTURE.md`'s constraint count ("18") is stale as of this session and should be corrected
to 19 wherever it's referenced going forward (D-15 already flags this; this research independently
confirms it by direct read).

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| ARCHITECTURE.md states 18 constraints | `defineConstraints` registers 19 (confirmed by direct read this session) | Between ARCHITECTURE.md's last update and `minimumStaffing`'s addition (V37, `min_staffing_weight` migration) | D-15's completeness test will fail immediately if anyone still writes a classification map sized to 18 |

**Deprecated/outdated:**
- ARCHITECTURE.md's "18 constraints" figure — supersede with 19 wherever this phase or Phase 15 cites a
  constraint count.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|----------------|
| A1 | `honourPreferredStartTime`'s mode classification (open question, not settled) | Constraint Classification | Low — this phase only needs the classification *test scaffold* and a defensible starting table; getting this specific row wrong doesn't block Phase 14's shippable work, only means Phase 15/whoever finalizes D-15's table needs to revisit it |
| A2 | `honourPreferredBreakTime`'s mode classification (open question, not settled) | Constraint Classification | Same as A1 |
| A3 | "~315 backend tests" in CONTEXT.md vs. 288 counted this session | Common Pitfalls #3 | Low — cosmetic; MODE-05's actual test is "the suite runs green," not a specific count |
| A4 | Whether `StaffingRequirementRepository` needs a new unfiltered-by-date "all live for desk" method, or the coverage validator should union each template's effective range against the existing date-ranged method | Pattern 9 / Don't Hand-Roll | Low-medium — affects one query's shape; either approach reaches the same live rows, just different call patterns |
| A5 | Which 409 idiom (D-13's named `RefreshInProgressException` pattern vs. the lower-cost existing `ConflictException`) the plan should pick | Common Pitfalls #1 | Low — both produce a 409 with the same operator-visible refusal message; differs only in whether the frontend can branch on a specific error code |

**If this table is empty:** N/A — see above; every row here is scoped, low-risk, and explicitly flagged
rather than silently assumed.

## Open Questions

1. **Do `honourPreferredStartTime` and `honourPreferredBreakTime` belong with the "four break
   constraints" as needs-a-shift-variant, or are they mode-agnostic once break/start become
   template-fixed?**
   - What we know: their bodies were read in full this session (Pattern/Constraint table above); both
     compare a solver-derived value (assignment start, gap-derived break start) against
     `AgentPreference`.
   - What's unclear: whether comparing against a *template-fixed* value (once shift mode exists) still
     makes semantic sense as the same constraint, or whether it needs its own shift-mode variant like the
     four D-03 already names.
   - Recommendation: leave classified as open in D-15's table for Phase 14's deliverable, with a note
     that Phase 15 — which actually builds the shift envelope — is where this gets resolved with real
     solver-code changes in view. Phase 14 should not guess an answer it cannot verify without touching
     solver code it's explicitly scoped to avoid touching.

2. **Should `ShiftTemplateController` live at `/api/v1/desks/{deskId}/shift-templates` (mirroring
   `/api/v1/desks/{deskId}/specializations`) or somewhere else?**
   - What we know: `SpecializationController`'s `@RequestMapping("/api/v1/desks/{deskId}/specializations")`
     is the exact existing precedent for a desk-scoped list resource.
   - What's unclear: nothing substantive — this is a naming-convention confirmation, not a real
     uncertainty.
   - Recommendation: `/api/v1/desks/{deskId}/shift-templates`, following the plural-noun convention
     exactly.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|--------------|-----------|---------|----------|
| PostgreSQL (dev) | Flyway migration, `shift_template` table | assumed available (existing dev DB) | — | — |
| Flyway | New migration `V39__...sql` | ✓ (in active use, V1–V38 applied) | project-pinned | — |
| Frontend test framework (vitest/jest/etc.) | Automated tests for `ShiftLibrary.tsx` | ✗ (confirmed absent from `package.json`) | — | Manual/UAT verification, consistent with Phase 13 precedent — no fallback framework needed since none is expected |
| H2 (test scope) | `@DataJpaTest` for `ShiftTemplateService` | ✓ (used by `JobTitleConfigServiceTest` and others) | project-pinned | — |

**Missing dependencies with no fallback:** none — the one "missing" dependency (frontend test framework)
has an accepted, precedented fallback (manual verification), not a blocking gap.

**Missing dependencies with fallback:**
- Frontend automated test framework — fallback is manual/UAT verification, already the norm for every
  existing frontend page in this codebase.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ, `@DataJpaTest` + H2 for service-layer tests (backend). No frontend framework. |
| Config file | `src/test/resources/application-test.yml` (implied by `@ActiveProfiles("test")` usage; not read this session — confirm path at plan time if a new profile-specific override is needed) |
| Quick run command | `./gradlew test --tests "com.wfm.service.ShiftTemplateServiceTest"` (pattern; exact class name is plan-time) |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|---------------------|--------------|
| SHLB-01/02/03 | Create/edit template with start/end/break/weekdays/effective range | unit (`@DataJpaTest`) | `./gradlew test --tests "*ShiftTemplateServiceTest"` | ❌ Wave 0 |
| SHLB-04 | Retire without corrupting referencing schedules | unit (`@DataJpaTest`) | `./gradlew test --tests "*ShiftTemplateServiceTest"` | ❌ Wave 0 |
| SHLB-05 | Coverage validation names uncovered windows | unit (`@DataJpaTest` or plain unit if validator has no repo deps beyond what can be constructed in-memory) | `./gradlew test --tests "*ShiftLibraryValidation*Test"` | ❌ Wave 0 |
| SHLB-06 | Hours-match advisory + fatal-on-mode-switch | unit | `./gradlew test --tests "*ShiftLibraryValidation*Test"` | ❌ Wave 0 |
| MODE-01/02 | Desk defaults SLOT; switch via endpoint | unit (`@DataJpaTest`) | `./gradlew test --tests "*DeskServiceSchedulingModeTest"` (name TBD) | ❌ Wave 0 |
| MODE-03 | Refusal names uncovered windows (same validator as SHLB-05, D-08) | unit | same as SHLB-05 | ❌ Wave 0 |
| MODE-04 | Accepted schedule untouched by mode switch | unit — assert `Schedule`/`Timeslot`/`StaffingRequirement` rows unchanged after a mode-switch call | `./gradlew test --tests "*DeskServiceSchedulingModeTest"` | ❌ Wave 0 |
| MODE-05 | Slot solve unchanged | **full suite**, not a new fixture (per CONTEXT.md Claude's Discretion) | `./gradlew test` | ✓ (288 existing tests) |
| XCUT-05 | Classification completeness | unit — reads `ScheduleConstraintProvider.defineConstraints` key set, asserts equality with classification map's key set | `./gradlew test --tests "*ConstraintClassificationTest"` (name TBD) | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** targeted `./gradlew test --tests "*ShiftTemplate*"` (or the relevant new class)
- **Per wave merge:** `./gradlew test` (full suite — this is also literally how MODE-05 is proven)
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `ShiftTemplateServiceTest` (or equivalent name) — covers SHLB-01…04, modelled on
  `JobTitleConfigServiceTest`'s `@DataJpaTest` + `@Import` + `@ActiveProfiles("test")` shape
- [ ] The shared coverage/hours validator's test class — covers SHLB-05, SHLB-06, MODE-03
- [ ] `ConstraintClassificationTest` (or equivalent) — covers XCUT-05's completeness assertion; this is
  the one genuinely new *kind* of test in this phase (reflects on `ScheduleConstraintProvider`, nothing
  else in the suite does this today)
- [ ] Framework install: none — JUnit 5/AssertJ/H2 are already project dependencies

## Security Domain

`security_enforcement` is not set to `false` in `.planning/config.json` (absent key = enabled per the
agent's own instructions), so this section is required.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|----------------|---------|--------------------|
| V2 Authentication | no | Out of scope — no auth surface touched by this phase |
| V3 Session Management | no | Out of scope |
| V4 Access Control | yes | Existing `TenantContext`/`TenantFilter` ThreadLocal pattern — every new query MUST scope by `tenantId` exactly like `Specialization`'s repository methods do (`findByTenantIdAndDeskId`, etc.) |
| V5 Input Validation | yes | Manual service-layer checks mirroring `SpecializationService` (`isBlank()`, uniqueness `existsBy...`); no bean-validation framework in this codebase (see Anti-Patterns) |
| V6 Cryptography | no | No secrets, tokens, or crypto surface in this phase |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|----------------------|
| Cross-tenant `ShiftTemplate` read/write via a guessed/enumerated UUID | Elevation of Privilege / Information Disclosure | Every repository method MUST take `tenantId` as a parameter and filter by it, exactly like `findByIdAndTenantIdAndDeskId` on `SpecializationRepository` — never a bare `findById` |
| Mode-switch endpoint invoked concurrently with an in-flight solve start | Tampering (race condition) | D-13's guard — verify the chosen 409 idiom (Pitfall 1) is actually applied before the `Desk.schedulingMode` write, not merely checked-then-forgotten (classic TOCTOU: check `InMemoryScheduleStore`/guard, then write, without a lock spanning both — mirror `BambooRefreshService`'s `putIfAbsent`-then-`finally`-remove pattern for atomicity if the `ConcurrentHashMap` idiom is chosen) |
| Coverage-refusal error messages leaking internal identifiers | Information Disclosure (low severity — internal tool, single tenant per deployment) | Match the existing `ErrorDetail` convention: human-readable `message`, and `value` fields carrying only data already visible to the operator (dates, times, template names) — never raw UUIDs where a name/date suffices, consistent with existing `runPreSolveValidation` messages (e.g. "Agent " + agent.getName(), not agent.getId(), in the message text) |

## Sources

### Primary (HIGH confidence — all read directly, this session, from the local repository)

- `src/main/java/com/wfm/model/Specialization.java` (full file)
- `src/main/java/com/wfm/model/Desk.java` (full file)
- `src/main/java/com/wfm/model/Timeslot.java` (full file)
- `src/main/java/com/wfm/model/StaffingRequirement.java` (relevant portion)
- `src/main/java/com/wfm/model/AgentDayHours.java` (full file)
- `src/main/java/com/wfm/model/ConstraintWeights.java` (full file)
- `src/main/java/com/wfm/model/ScheduleStatus.java` (full file)
- `src/main/java/com/wfm/service/SpecializationService.java` (full file)
- `src/main/java/com/wfm/service/TimeslotGeneratorService.java` (full file)
- `src/main/java/com/wfm/service/InMemoryScheduleStore.java` (full file)
- `src/main/java/com/wfm/service/SolverService.java` (lines 600-899: `computeCapacityWarnings`,
  `runPreSolveValidation`)
- `src/main/java/com/wfm/service/ScheduleService.java` (lines 193-256: `acceptSchedule`)
- `src/main/java/com/wfm/controller/SpecializationController.java` (full file)
- `src/main/java/com/wfm/controller/DeskController.java` (full file)
- `src/main/java/com/wfm/controller/GlobalExceptionHandler.java` (full file)
- `src/main/java/com/wfm/repository/SpecializationRepository.java` (full file)
- `src/main/java/com/wfm/repository/TimeslotRepository.java` (relevant portion)
- `src/main/java/com/wfm/repository/StaffingRequirementRepository.java` (full file)
- `src/main/java/com/wfm/repository/AgentDayHoursRepository.java` (full file)
- `src/main/java/com/wfm/integration/BambooRefreshService.java` (lines 85-157)
- `src/main/java/com/wfm/exception/PreSolveValidationException.java` (full file)
- `src/main/java/com/wfm/exception/RefreshInProgressException.java` (full file)
- `src/main/java/com/wfm/exception/ConflictException.java` (full file)
- `src/main/java/com/wfm/dto/ErrorResponse.java` (relevant portion — `ErrorDetail` record)
- `src/main/java/com/wfm/dto/DeskRequest.java`, `DeskResponse.java`, `TimeslotBoundsResponse.java`
  (full files)
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` (lines 1-320, 380-500, 507-611:
  `defineConstraints` + every constraint body needed for the classification table)
- `frontend/src/pages/Specializations.tsx` (full file)
- `frontend/src/pages/DeskManagement.tsx` (full file)
- `frontend/src/App.tsx` (routing/nav section)
- `frontend/package.json` (full file)
- `src/test/java/com/wfm/service/JobTitleConfigServiceTest.java` (opening section)
- `src/test/java/com/wfm/service/TimeslotGeneratorServiceTest.java` (opening section)
- `src/test/java/com/wfm/controller/GlobalExceptionHandlerTest.java` (opening section)
- `src/main/resources/db/migration/` directory listing + `V37__add_min_staffing_weight.sql`,
  `V38__add_consistent_start_weight.sql`, `V1__initial_schema.sql` (specialization table DDL)
- `.planning/phases/14-shift-library-scheduling-mode/14-CONTEXT.md`, `.planning/REQUIREMENTS.md`,
  `.planning/STATE.md`, `.planning/config.json`

### Secondary (MEDIUM confidence)

- None used — this phase required no external documentation lookup; everything needed was already in
  the local repository, per the roadmap's own "Research needed at plan time: No" signal.

### Tertiary (LOW confidence)

- `honourPreferredStartTime`/`honourPreferredBreakTime` mode-classification judgement (Open Questions
  #1) — inferred from reading the constraint bodies, not confirmed against any Phase 15 design that
  doesn't exist yet.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — zero new dependencies; every pattern verified by direct file read this session
- Architecture: HIGH — every entity/service/controller/repository pattern quoted verbatim from files
  read this session; the two structural findings (grid is derived not stored; 19 not 18 constraints)
  are independently verified, not carried over from CONTEXT.md's framing
- Pitfalls: HIGH for the five documented (each traced to a specific, re-checkable file read this
  session); LOW for the two open constraint-classification questions, explicitly flagged as such

**Research date:** 2026-08-25
**Valid until:** 30 days (stable, internal-codebase-only research; no external library version risk)
