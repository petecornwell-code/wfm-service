# Phase 16: Usual Shift Storage - Research

**Researched:** 2026-09-03
**Domain:** Spring Boot/JPA CRUD feature mirroring an existing shape (`AgentDayHours`), Apache POI
data-validation dropdowns, React inline editing. No solver/constraint work in this phase.
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Template reference & era resolution**
- **D-01:** `AgentUsualShift` stores a real FK `shift_template_id` (`agent_id, day_of_week,
  shift_template_id`, unique on `(agent_id, day_of_week)`), but the resolution service resolves by
  NAME: it reads the stored row's template name and returns whichever era of that name is
  effective on the date being asked about. Precedent: `AgentShiftAssignment` carries `templateName`
  alongside `sourceTemplateId`. Reversibility: costly.
- **D-02:** When no era of the stored name is effective on a date, resolution returns nothing —
  identical to unset. The stored row survives untouched as history. Retirement is never blocked by
  referencing rows.
- **D-03:** Setting a usual shift for a weekday excluded by the template's `valid_weekdays` mask is
  rejected with a 400, on both the inline path and (as a cell-level skip) the upload path.
  Deliberately does NOT follow Phase 14's D-06 advisory-on-save precedent. Same reject-not-clamp
  posture `DeskAgentService.setDayHours` takes on out-of-range hours.
- **D-04:** A usual shift may be stored on a weekday the agent does not work (0 hours, MANDATORY,
  or PTO). The two models stay orthogonal — no cross-model write.
- **D-05:** A template whose net working duration does not match the agent's effective contracted
  hours is advisory only, following Phase 14 D-06.

**Upload column shape**
- **D-06:** The upload workbook gains seven columns, `Usual Shift Mon` … `Usual Shift Sun`,
  generated from the same `EnrichedColumnLayout.DAY_ORDER` loop.
- **D-07:** A blank Usual Shift cell means "no usual shift", and is valid — diverges from Phase
  10's blank-day-cell-is-invalid rule.
- **D-08:** A Usual Shift cell naming a template not in that desk's library skips that cell, warns,
  and imports the rest of the row — does not trigger Phase 10's D-09 whole-row skip.
- **D-09:** The generated per-desk template pre-fills the seven Usual Shift cells with stored
  values. Load-bearing together with D-11: without pre-fill, `clearDesk` + blank-means-none would
  wipe every stored usual shift on re-upload. Day-hours cells are deliberately left blank (Phase 10
  scope).
- **D-10:** The generated template attaches a sheet-scoped Excel data-validation dropdown listing
  the desk's live template names. Does not replace parser validation.

**Write-path policy (USHF-05 / XCUT-02)**
- **D-11:** `clearDesk` wipes usual shifts, mirroring `agent_day_hours`/preferences/exceptions.
  Load-bearing together with D-09.
- **D-12:** Moving an agent to a different desk through the roster UI clears their usual shifts,
  through the SAME clear-usual-shifts helper `clearDesk` calls — one implementation, two callers.
- **D-13:** A SHIFT → SLOT mode switch leaves usual shifts untouched.
- **D-14:** The USHF-05 deliverable is a table, plus one test per path that actually exercises that
  path, plus a structural completeness guard that fails when a new writer of the usual-shift table
  appears without a corresponding table row. Paths: upload, inline edit, BambooHR refresh,
  `clearDesk`, desk move, mode switch, and the solver itself.

**Roster & export surface (USHF-06 / XCUT-01)**
- **D-15:** Usual shift renders as a second line inside the existing seven day tiles in the
  roster's expanded row (`frontend/src/pages/DeskAgents.tsx:604`), not a new column or tile strip.
- **D-16:** The tile distinguishes three states: never set / set and live / stored-but-not-in-
  effect (merges "era retired" and "weekday not worked" into one muted treatment).
- **D-17:** Inline editing uses a native `<select>` of live template names, with an explicit
  `— none —` option. Does not reuse the neighbouring hours cell's `<input>` + `<datalist>` pattern.
- **D-18:** The Excel export gains seven columns from the same `DAY_ORDER` loop, immediately after
  the existing day-hours group (`DeskAgentExportService.java:42`). `First Name`/`Last Name` shift
  right by seven columns. Conditional columns rejected — export must round-trip with upload.

### Claude's Discretion
- The resolution service is one implementation with multiple callers (currently duplicated as
  `SolverService.resolvePreferences`/`ScheduleService.resolvePreferences` — copy the shape, not the
  duplication).
- Migration number: confirm the actual latest-applied version before writing it.
- Table/entity naming, DTO shape, endpoint paths, and test-file organisation follow existing
  conventions (`AgentDayHours`/`AgentDayHoursRepository`/`DeskAgentController` are the models).
- Whether the inline write is one endpoint per weekday or something else — the criterion locks the
  choke-point requirement, not the HTTP shape.

### Deferred Ideas (OUT OF SCOPE)
- Pre-filling day-hours cells in the generated template (Phase 10 scope).
- A desk-wide "set all days to…" bulk action for usual shifts.
- Warning the operator at template-retirement time how many usual shifts it strands.
- A searchable combobox for template selection.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| USHF-01 | Each agent can have a stored usual shift per weekday, referencing a shift template from their desk's library | `AgentDayHours` shape verified (`src/main/java/com/wfm/model/AgentDayHours.java`); `ShiftTemplate` FK target verified (`src/main/java/com/wfm/model/ShiftTemplate.java`); migration DDL model in `V29__agent_first_last_name_and_day_hours.sql` |
| USHF-02 | Operator can set usual shifts in bulk via a column in the per-desk upload template | `EnrichedColumnLayout` consumers enumerated below; `DeskAssignmentUploadService.java` parser loop at line 521; POI 5.3.0 explicit-list + hidden-sheet fallback verified |
| USHF-03 | Operator can set and correct an agent's usual shift inline in the roster | `DeskAgentService.setDayHours` (line 289) fully read as the choke-point model; `DeskAgentController.setDayHours` (line 84) as the endpoint model |
| USHF-04 | An agent with no stored usual shift is scheduled without penalty rather than being forced toward an arbitrary default | Resolution service design (below) returns `Optional.empty()`/no-row rather than a default; D-02/D-04 covered |
| USHF-05 | Every write path that can change agent scheduling data leaves usual-shift data in a defined, documented state | Every write path traced to file/line below (upload, inline, BambooHR refresh, clearDesk, desk move, mode switch, solver) |
| USHF-06 | A stored usual shift is visible everywhere agent scheduling data is displayed, including roster and Excel export | `DeskAgents.tsx:604` day-tile structure read; `DeskAgentExportService.java:42/105` read; `DeskAgentResponse` DTO shape read |
</phase_requirements>

## Summary

This phase is a structural copy of two things this codebase has already built and hardened once:
`AgentDayHours` (the storage shape — child table, unique `(agent_id, day_of_week)`, no `desk_id`
column, resolved via `agent.deskId`) and `SolverService.resolvePreferences`/
`ScheduleService.resolvePreferences` (the standing-vs-weekly precedence shape, here becoming
"stored FK → live era of that name on the asked-for date"). Nearly every mechanical unknown listed
in the phase brief resolved to a concrete, previously-verified answer in the live codebase — POI is
pinned at 5.3.0 using `XSSFWorkbook` (not SXSSF, so no streaming constraints), the migration head on
disk is V46 and it is confirmed *applied* to dev (Flyway log timestamp in `15-*/HANDOFF.md`), and
every one of USHF-05's six write paths was traced to an exact file and method — including one
genuine gap: **`DeskAgentService.removeDeskAgent` (line 180) does not currently delete
`AgentDayHours` rows, unlike `clearDesk`, which does.** D-12's "same helper" requirement is not
automatically satisfied by today's code; it requires adding a call to the new clear-usual-shifts
helper into `removeDeskAgent`, which is also the ONLY place a desk-to-desk move actually happens in
this app (there is no atomic "move" endpoint — the roster UI removes an agent from a desk, which
sets `deskId = null`, then reassigns from BambooHR data on POST `/assign-to-desk`).

The one area with no existing precedent in this codebase is D-14's structural completeness guard.
ArchUnit is **not** a dependency (`build.gradle` has zero hits). The two existing reflection-guard
patterns in this codebase (`ScheduleConstraintClassificationTest`'s dual independent derivations,
and `DeskAssignmentUploadMultiSheetTest`'s single-class "does this ONE known class hold this
dependency" check) both operate over a single, already-known class. Neither, as-is, can detect "a
brand-new class anywhere in `src/main/java` now writes to `AgentUsualShiftRepository`." Two honest
extensions are documented below with their real tradeoffs — this is a genuine open design call for
the planner, not something research can resolve to a single "correct" answer.

**Primary recommendation:** Model `AgentUsualShift` directly on `AgentDayHours` (no `desk_id`
column; desk-scoping goes through `agent.deskId`, exactly as `AgentDayHoursRepository`'s own
comment states for its sibling). Give it a real `@ManyToOne` FK to `ShiftTemplate` (lazy fetch,
mirroring `AgentDayHours.agent`) rather than a denormalized name column — the FK can never dangle
(no delete endpoint on `ShiftTemplate`), so a read-through join is always safe and always reflects
current truth, which is what a *live target* should do (unlike `AgentShiftAssignment`'s frozen
historical record, which denormalizes on purpose). Extract one `clearUsualShifts(UUID agentId)`
helper used by both `DeskAssignmentUploadService.clearDesk` (add the call) and
`DeskAgentService.removeDeskAgent` (add both the call and — separately — recognize that this method
already IS the desk-move path).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Usual-shift storage (`AgentUsualShift` table + repository) | API / Backend | Database / Storage | New JPA entity + Flyway migration, modeled on `AgentDayHours` |
| Resolution (stored FK → live era by name+date) | API / Backend | — | Pure service-layer computation, no persistence side effects, mirrors `resolvePreferences` |
| Choke-point write (inline edit) | API / Backend | — | `DeskAgentService`-analog method; tenant/desk scoping (T-13-05 IDOR guard) must happen here, not in the controller |
| Bulk write (upload column) | API / Backend | — | `DeskAssignmentUploadService` parser extension; shares `EnrichedColumnLayout` |
| Excel dropdown validation | API / Backend | — | `DeskAssignmentTemplateService` (POI generation) — a backend concern even though it shapes a client-facing artifact |
| Roster tile display + inline `<select>` | Browser / Client | Frontend Server (SSR: N/A, this app has no SSR tier) | `frontend/src/pages/DeskAgents.tsx`, pure client-rendered React |
| Excel export column | API / Backend | — | `DeskAgentExportService`, same POI/`EnrichedColumnLayout` mechanism as the template |
| Structural completeness guard | API / Backend (test source set) | — | Lives in `src/test/java`, not production code, mirroring `ScheduleConstraintClassification`'s placement rationale |

This app has no server-side-rendering tier and no separate CDN tier — it is a Spring Boot REST API
(`/api/v1/...`) plus a client-rendered React SPA (`frontend/`). All backend capabilities above sit
in the single "API / Backend" tier; there is no tier-misassignment risk structurally available to
this phase (unlike a multi-tier SSR app).

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Apache POI (`poi-ooxml`) | 5.3.0 `[VERIFIED: build.gradle:57]` | Excel template generation + export + new data-validation dropdown | Already the project's only Excel library; no new dependency needed |
| Spring Data JPA / Hibernate | already pinned (project-wide) | New `AgentUsualShift` entity + repository | Matches every existing entity in `src/main/java/com/wfm/model` |
| Flyway | already pinned (project-wide) | New migration V47 | Matches every existing schema change |

No new external dependency is required for this phase — every mechanism (POI dropdown, JPA entity,
native `<select>`) is already available in the pinned stack.

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Testcontainers (`pgvector/pgvector:pg16`) | already pinned, used via `PostgresBackedTest` | Real-Flyway migration-vs-entity drift check for the new table | Required for at least one test class per this project's own G-14-1 lesson |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Native `<select>` (D-17, locked) | `<input>` + `<datalist>` (the neighbouring hours-cell pattern) | Rejected in CONTEXT: the datalist pattern exists specifically to work around G-13-DD (a seeded input collapses the list to its self-matching option), a problem a closed small value set with an explicit "— none —" option does not have |
| POI explicit-list dropdown | POI formula-list dropdown against a hidden sheet | Not an either/or — explicit-list is the primary mechanism (D-10), formula-list-against-a-hidden-sheet is the documented fallback once the 255-character Excel limit on the joined list string is hit |

**Installation:** none — no new packages.

**Version verification:**
```
$ grep -n poi build.gradle
56:    // Apache POI for spreadsheet export
57:    implementation 'org.apache.poi:poi-ooxml:5.3.0'
```
`[VERIFIED: build.gradle:57]` — read this session.

## Package Legitimacy Audit

No external packages are introduced by this phase. Apache POI 5.3.0 is an existing, already-vetted
dependency (used in production by `DeskAssignmentTemplateService`, `DeskAgentExportService`, and
`ClientManagementExportService`). No new npm or Maven/Gradle coordinate is added.

**Packages removed due to [SLOP] verdict:** none — no packages evaluated, none needed.
**Packages flagged as suspicious [SUS]:** none.

## Architecture Patterns

### System Architecture Diagram

```
 Upload path                         Inline-edit path                  Display path
 ───────────                         ────────────────                  ────────────
 Operator downloads                  Operator opens roster
 per-desk template          ┌──────► expanded row (tile)
 (pre-filled usual                    │
  shifts, D-09, D-10)                 ▼
        │                    picks name in native <select>
        ▼                    (only CURRENT eras whose weekday
 Operator fills/edits         mask includes this weekday, D-03)
 "Usual Shift Mon..Sun"              │
 columns, re-uploads                 ▼
        │                    PUT .../usual-shift/{day}
        ▼                    ──► UsualShiftService.setUsualShift  ◄── choke point,
 POST /upload-desk-assignments        (tenant+desk scope resolved         mirrors
        │                             BEFORE repo call, T-13-05;          setDayHours
        ▼                             weekday-mask reject-not-clamp,
 DeskAssignmentUploadService           D-03; upsert-or-create row)
   clearDesk(tenantId, deskId)                │
   ──► clearUsualShifts(agentId)              ▼
        (NEW shared helper,           agent_usual_shift row written
         D-11/D-12)                    (real FK -> shift_template.id)
        │                                     │
        ▼                                     │
   per-row parse loop                         │
   (blank = none, D-07;                       │
    unknown name = skip                       │
    cell + warn, D-08)                        │
        │                                     │
        ▼                                     ▼
   agent_usual_shift row written    ┌─────────────────────────┐
   (same upsert path)               │  UsualShiftResolutionSvc │
        │                           │  (ONE impl, N callers,   │
        └──────────────┬────────────┤   mirrors resolvePrefs)  │
                        │            │  reads stored FK's       │
                        ▼            │  .getName(), re-resolves │
              ┌──────────────────┐   │  live era by (name,date) │
              │ Desk-move path   │   └────────────┬─────────────┘
              │ removeDeskAgent  │                │
              │ ──► clearUsual-  │                ▼
              │     Shifts (NEW) │      GET /desks/{id}/agents
              └──────────────────┘      (DeskAgentResponse.usualShift)
                                                   │
                                                   ▼
                                    Roster tile (2nd line, D-15/D-16)
                                    + Excel export column (D-18)

 Paths proven to leave usual shifts UNTOUCHED (USHF-05 table, this phase):
   BambooHR refresh (refreshDeskAgents) — no AgentUsualShiftRepository field, structurally cannot write
   SHIFT<->SLOT mode switch — writes exactly one column (desk.scheduling_mode)
   The solver itself — this phase wires no solver read/write of AgentUsualShift (Phase 17 scope)
```

### Recommended Project Structure

No new top-level packages — every new file drops into the existing package that owns its concern,
exactly matching where `AgentDayHours`'s siblings live:

```
src/main/java/com/wfm/
├── model/AgentUsualShift.java              # new entity, mirrors AgentDayHours.java shape
├── repository/AgentUsualShiftRepository.java
├── service/UsualShiftService.java          # OR fold into DeskAgentService — see "Open Questions"
├── service/UsualShiftResolutionService.java  # one impl, called by roster read + export + (Phase 17) solver
├── dto/SetUsualShiftRequest.java
├── controller/DeskAgentController.java     # add PUT .../usual-shift/{day} here (existing file)
├── service/DeskAssignmentUploadService.java  # extend (existing file) — parser loop + clearDesk
├── service/DeskAssignmentTemplateService.java # extend (existing file) — pre-fill + dropdown
└── service/DeskAgentExportService.java     # extend (existing file) — seven new columns

src/main/resources/db/migration/
└── V47__add_agent_usual_shift.sql          # confirm V47 is still correct at plan time

src/test/java/com/wfm/
├── service/DeskAgentServiceUsualShiftTest.java   # mirrors DeskAgentServiceDayHoursTest.java
├── service/UsualShiftResolutionServiceTest.java
├── support/AgentUsualShiftPostgresTest.java      # extends PostgresBackedTest — G-14-1 requirement
└── <somewhere>/UsualShiftWritePathGuardTest.java  # D-14 structural guard — see below

frontend/src/pages/DeskAgents.tsx           # extend existing tile block at line 604
frontend/src/api/client.ts                  # extend existing deskAgents object
```

### Pattern 1: Table shape — copy `AgentDayHours` exactly

**What:** New JPA entity with no `desk_id` column; desk-scoping is always derived through the
`agent` association, exactly as `AgentDayHoursRepository`'s own comment states for its sibling
table.

**Verbatim source** `[VERIFIED: src/main/java/com/wfm/model/AgentDayHours.java:8-30]`:
```java
@Entity
@Table(name = "agent_day_hours", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"agent_id", "day_of_week"})
})
public class AgentDayHours {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal hours;
```

**When to use:** Directly as the model for `AgentUsualShift` — replace `hours`/`dayOffType` with a
`@ManyToOne(fetch = LAZY) @JoinColumn(name = "shift_template_id", nullable = false) ShiftTemplate
shiftTemplate` (a REAL FK, unlike `AgentShiftAssignment.sourceTemplateId`, which is deliberately
FK-less per its own service javadoc — `[VERIFIED:
src/main/java/com/wfm/service/ShiftTemplateService.java:122-130]`, quoted: *"agent_shift_assignment
.source_template_id carries no FK (D-07 denormalises template_name and the shift/band times onto
the row precisely so history survives)"*). `AgentUsualShift` is the opposite case — a live,
continuously-reinterpreted target, not a frozen history row — so the real FK is correct and safe: a
`ShiftTemplate` row is never hard-deleted (`deleteShiftTemplate` refuses when any
`agent_shift_assignment` row references it, and `AgentUsualShift`'s own FK would give it a SECOND
reason to refuse once this phase ships — the planner must decide whether to also guard delete on
`AgentUsualShiftRepository` usage, or accept that a real FK constraint already makes the delete
fail at the DB level with a less legible error).

**Confirmation this table is exactly what Phase 14 anticipated** `[VERIFIED:
src/main/resources/db/migration/V39__add_shift_template_and_scheduling_mode.sql, migration header
comment]`, quoted verbatim: *"by the time it matters, Phase 15's agent_shift_assignment FK and
Phase 16's agent_usual_shift FK both point at these rows."* — Phase 14's own migration commentary
names this table and this phase by number.

### Pattern 2: Resolution — copy `resolvePreferences`'s shape, not its duplication

**What:** `resolvePreferences` currently exists twice, verbatim-identical in structure, at
`[VERIFIED: src/main/java/com/wfm/service/SolverService.java:567-634]` and `[VERIFIED:
src/main/java/com/wfm/service/ScheduleService.java:486-...]` (the latter's javadoc reads *"Mirrors
the logic in SolverService.resolvePreferences"* — an explicit, self-documented duplication).

The shape to copy (standing-vs-weekly precedence, adapted): index all stored rows by agent, then
for each date in the period, prefer the more-specific-for-that-date value, falling through to the
next-most-specific, else `null`/absent.

**AgentUsualShift's adaptation is simpler than `resolvePreferences`** — there is no "weekly
override vs. standing" distinction; there is one stored row per `(agent, weekday)`. The precedence
that matters here is **era selection within the stored template's name**, not two competing rows.
Concrete algorithm, grounded in verified repository/entity methods:

```java
// UsualShiftResolutionService — one implementation, N callers (upload display, roster GET,
// export, and — not in this phase — Phase 17's solver/drift report).
public Optional<ShiftTemplate> resolve(AgentUsualShift stored, LocalDate date) {
    if (stored == null) return Optional.empty();                    // USHF-04: no row = no penalty
    String name = stored.getShiftTemplate().getName();               // read-through the real FK
    List<ShiftTemplate> eras = shiftTemplateRepository
            .findByTenantIdAndDeskIdAndName(tenantId, deskId, name); // [VERIFIED: ShiftTemplateRepository.java:21]
    return eras.stream()
            .filter(t -> t.isEffectiveOn(date))                      // [VERIFIED: ShiftTemplate.java:153]
            .findFirst();                                            // D-11 non-overlap => at most one match
    // Empty result here = D-02's "identical to unset" — the caller must not distinguish
    // "never set" from "set but no era live today" at this layer; that distinction is a DISPLAY
    // concern (D-16), not a resolution concern.
}
```

`ShiftTemplateRepository.findByTenantIdAndDeskIdAndName` `[VERIFIED:
src/main/java/com/wfm/repository/ShiftTemplateRepository.java:21]`, quoted: `List<ShiftTemplate>
findByTenantIdAndDeskIdAndName(long tenantId, UUID deskId, String name);` — already exists, no new
repository method needed on `ShiftTemplateRepository`.

`ShiftTemplate.isEffectiveOn(LocalDate)` `[VERIFIED: src/main/java/com/wfm/model/ShiftTemplate.java:153-158]`,
quoted:
```java
@Transient
public boolean isEffectiveOn(LocalDate date) {
    if (effectiveFrom != null && effectiveFrom.isAfter(date)) {
        return false;
    }
    return effectiveTo == null || !effectiveTo.isBefore(date);
}
```

**When to use:** This resolution service is the single implementation called from: (a) the roster
GET path (to populate the DTO field that drives D-16's three-state tile), (b) the export path (same
field), (c) — explicitly NOT this phase — Phase 17's consistency constraint and drift report, which
CONTEXT's own "Claude's Discretion" note flags as the reason it must be one shared implementation
from day one rather than a second copy invented in Phase 17.

### Pattern 3: Choke-point write — mirror `setDayHours` field-by-field

**Full verified source**, `[VERIFIED: src/main/java/com/wfm/service/DeskAgentService.java:281-328]`:

```java
@Transactional
public DeskAgentResponse setDayHours(UUID deskId, UUID agentId, DayOfWeek day,
                                      BigDecimal hours, DayOffType dayOffType, boolean clearRow) {
    long tenantId = TenantContext.getTenantId();

    BigDecimal scheduleDefault = resolveScheduleDefault(tenantId, deskId);

    // Mandatory access-control step (T-13-05): resolve the agent within tenant+desk scope
    // BEFORE any AgentDayHoursRepository call — findByAgent_IdAndDayOfWeek accepts a raw
    // agent id and would otherwise be an IDOR.
    Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
            .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));

    if (clearRow) {
        agentDayHoursRepository.findByAgent_IdAndDayOfWeek(agentId, day)
                .ifPresent(agentDayHoursRepository::delete);
    } else if (dayOffType == DayOffType.MANDATORY || dayOffType == DayOffType.PTO) {
        upsertDayHoursRow(agent, day, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), dayOffType);
    } else if (hours != null) {
        BigDecimal normalized = BigDecimals.normalize(hours);
        if (normalized.signum() < 0 || normalized.compareTo(new BigDecimal("24")) > 0) {
            throw new IllegalArgumentException("Hours must be between 0 and 24");
        }
        upsertDayHoursRow(agent, day, normalized, null);
    } else {
        throw new IllegalArgumentException("Must provide hours, a day-off type, or clearRow");
    }

    agentDayHoursRepository.flush();
    Map<DayOfWeek, AgentDayHours> dayRows = agentDayHoursRepository
            .findByTenantIdAndAgent_Id(tenantId, agentId).stream()
            .collect(Collectors.toMap(AgentDayHours::getDayOfWeek, h -> h));
    return toResponse(agent, scheduleDefault, dayRows, List.of());
}
```

**Adaptation for `setUsualShift`:** replace the hours/dayOffType branches with: `clearRow` →
delete-if-present; a `shiftTemplateId` supplied → validate it exists, belongs to this desk, is
CURRENT (`isEffectiveOn(today)`), and its `getValidWeekdays()` contains `day` — **reject with
`IllegalArgumentException` (→ 400, confirmed via
`[VERIFIED: src/main/java/com/wfm/controller/GlobalExceptionHandler.java:32-33]`,
`@ExceptionHandler(IllegalArgumentException.class)`) if the weekday mask excludes `day`** (D-03);
otherwise upsert-or-create the row (mirror `upsertDayHoursRow`'s reuse-or-create pattern,
`[VERIFIED: DeskAgentService.java:335-344]`). Controller model, `[VERIFIED:
src/main/java/com/wfm/controller/DeskAgentController.java:84-91]`:
```java
@PutMapping("/{agentId}/day-hours/{day}")
public DeskAgentResponse setDayHours(@PathVariable UUID deskId,
                                      @PathVariable UUID agentId,
                                      @PathVariable DayOfWeek day,
                                      @RequestBody SetDayHoursRequest request) {
    return deskAgentService.setDayHours(deskId, agentId, day,
            request.hours(), request.dayOffType(), Boolean.TRUE.equals(request.clearRow()));
}
```
Recommended analog: `PUT /desks/{deskId}/agents/{agentId}/usual-shift/{day}` with a body of
`{ shiftTemplateId: UUID | null, clearRow: boolean }` (a `SetUsualShiftRequest` record mirroring
`[VERIFIED: src/main/java/com/wfm/dto/SetDayHoursRequest.java:7]`,
`public record SetDayHoursRequest(BigDecimal hours, DayOffType dayOffType, Boolean clearRow) {}`).

### Pattern 4: `EnrichedColumnLayout` — every consumer, file and mechanism

`[VERIFIED: src/main/java/com/wfm/util/EnrichedColumnLayout.java, full file read]`. The class
exposes `DAY_ORDER` (a fixed `DayOfWeek[]`), `dayHeader(DayOfWeek)` (title-case string, e.g.
`"Monday"`), and `normalize(String)` (trim+lowercase, used as the map key everywhere). It has NO
existing concept of a second per-day column group — the seven new `Usual Shift {Day}` headers need
a new method, e.g. `usualShiftHeader(DayOfWeek d)` returning `"Usual Shift " + dayHeader(d)`,
following the same one-function convention.

Every consumer, with the exact loop each already runs over `DAY_ORDER`:

| Consumer | File:line | Mechanism |
|----------|-----------|-----------|
| Parser header validation | `DeskAssignmentUploadService.java:252-256` | Builds `missingHeaders` by checking `col.containsKey(EnrichedColumnLayout.normalize(dayHeader(d)))` for each `d` in `DAY_ORDER` |
| Parser header→index map | `DeskAssignmentUploadService.java:234-240` | `col.put(EnrichedColumnLayout.normalize(hdr), c)` — generic, needs no change, but the day-cell READ loop at `:521` needs a parallel Usual-Shift loop |
| Parser day-cell read loop | `DeskAssignmentUploadService.java:521-534` | `for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) { ... agentDayHoursRepository.save(...) }` — the exact loop shape to copy for usual-shift cells |
| `clearDesk` | `DeskAssignmentUploadService.java:550-565` | Deletes `agent_day_hours` by `agentDayHoursRepository.deleteByAgent_Id(agent.getId())` inside a per-agent loop — the exact call site to add `clearUsualShifts(agent.getId())` beside |
| Template header builder | `DeskAssignmentTemplateService.java:114-122` | `buildHeaders()`: `identityHeaders()` + `DAY_ORDER` loop + two specialty headers — append a second `DAY_ORDER` loop here for the seven Usual Shift headers |
| Template row writer | `DeskAssignmentTemplateService.java:74-84` | Currently writes only identity columns 0-6, leaving day/specialty columns blank (comment: *"Columns 7-13 ... intentionally left blank"*) — D-09 requires this row writer to ALSO write the seven Usual Shift cells (pre-filled), a genuinely new write, not a no-op extension |
| Export header builder | `DeskAgentExportService.java:35-46` | Builds `columns` list; the `DAY_ORDER` loop is at line 42-44, immediately followed by `First Name`/`Last Name` at 45-46 — D-18 inserts the new seven headers between these two, shifting First/Last Name right by seven (mirrors Phase 13 P-09's precedent index move) |
| Export row writer | `DeskAgentExportService.java:103-121` (`writeDayCells`) | `FIRST_DAY_COLUMN = 13` constant; writes at `FIRST_DAY_COLUMN + i` for `i` in `0..6` — a second constant (e.g. `FIRST_USUAL_SHIFT_COLUMN = 20`) and loop follow the identical shape; then `row.createCell(FIRST_DAY_COLUMN + 7)` / `+8` for First/Last Name must become `FIRST_USUAL_SHIFT_COLUMN + 7` / `+8` |

No enriched-shape header string literal exists anywhere else in `src/main/java` — grep confirms
every `EnrichedColumnLayout` reference is either inside the class itself or one of the four files
above (`[VERIFIED: grep -rn "EnrichedColumnLayout" src/main/java, 0 hits outside these files and
AgentMergeService.java, which only uses DAY_ORDER for an unrelated weekday-abbreviation report]`).

### Pattern 5: POI data validation — exact API, version-pinned

`DeskAssignmentTemplateService` uses `XSSFWorkbook` (in-memory, not `SXSSFWorkbook`) — `[VERIFIED:
src/main/java/com/wfm/service/DeskAssignmentTemplateService.java:11,49]`. This matters: SXSSF's
row-flushing model constrains when validation/styling can be applied to already-flushed rows; XSSF
has no such constraint, so this is the simple case.

No data-validation code exists anywhere in this codebase today (`[VERIFIED: grep -rn
"DataValidationHelper\|addValidationData\|CellRangeAddressList" src/main/java, 0 hits]`) — this is
genuinely new code for this phase, not an extension of an existing pattern.

**Primary mechanism (D-10), `[CITED: Apache POI XSSFDataValidationHelper Javadoc / community
examples, corroborated by web search this session]`:**
```java
Sheet sheet = ...; // the desk's sheet, XSSFSheet
DataValidationHelper dvHelper = sheet.getDataValidationHelper();
DataValidationConstraint constraint =
        dvHelper.createExplicitListConstraint(templateNames.toArray(new String[0]));
CellRangeAddressList addressList = new CellRangeAddressList(
        1, 1048575, usualShiftColumnIndex, usualShiftColumnIndex); // rows 1..max, one column per weekday
DataValidation validation = dvHelper.createValidation(constraint, addressList);
sheet.addValidationData(validation);
```
Repeat per weekday column (7 columns × 1 desk sheet), scoped to that sheet only (D-10's
"sheet-scoped" requirement is satisfied automatically — `addValidationData` is a `Sheet`-level
call).

**The 255-character limit is real and Excel-imposed, not a POI artifact** `[CITED: web search this
session, corroborates training knowledge — "Excel reports files as corrupt if they exceed 255
bytes for validation text"]`. `createExplicitListConstraint` joins the supplied array into a single
comma-delimited formula string; once that joined string exceeds ~255 characters, Excel treats the
file as corrupt on open. For a desk whose live template-name list is short (the common case — shift
libraries in this codebase's fixtures run 2-5 templates), this never triggers. A desk with many
long template names could hit it.

**Fallback mechanism (only needed if the 255-char limit is hit), `[CITED: web search this session]`:**
write the candidate names to a hidden helper sheet, define a named range over that column, and use
`dvHelper.createFormulaListConstraint("NamedRangeName")` (a cell-range/name reference, not an
explicit list) instead — no character-count limit applies to this form. Given every desk's shift
library size is currently small and operator-curated (Phase 14 imposes no upper bound but every
real desk observed in this project's own fixtures/UAT is single digits), the planner should treat
the explicit-list constraint as the primary implementation and record the hidden-sheet fallback as
a documented, not-necessarily-built, escape hatch — recommend a length-guard check (join the names,
measure the string, and only attempt the explicit list under 255 chars; otherwise fall back or skip
validation with a comment) rather than building the hidden-sheet path unconditionally, since D-10
"does not replace parser validation" — the dropdown is a UX convenience, not a correctness
guarantee, so degrading gracefully (skip the dropdown, keep the parser check) is an acceptable
minimum if the fallback is judged out of scope for this phase.

### Anti-Patterns to Avoid
- **Denormalizing the template name onto `AgentUsualShift`:** `AgentShiftAssignment` does this
  deliberately because it is a frozen historical record (D-07 in Phase 15: "so history survives").
  `AgentUsualShift` is the opposite — a live target that must always reflect current truth — so
  denormalizing would require redundant write-time synchronization for no benefit, and risks
  drifting stale if a template is renamed in place via `updateShiftTemplate` (which mutates the same
  row, `[VERIFIED: ShiftTemplateService.java:100-110]`).
- **A second `resolvePreferences`-shaped method:** CONTEXT explicitly calls out this exact trap —
  copy the *shape*, not the *duplication*. `resolvePreferences` already exists twice in this
  codebase (`SolverService.java:567`, `ScheduleService.java:486`) as a cautionary, not aspirational,
  example.
- **A whole-row upload skip on an unresolvable Usual Shift name:** Phase 10's D-09 whole-row-skip
  behavior is for identity/hours failures. D-08 is explicit that a bad usual-shift cell must not
  cost the agent's valid data.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Excel dropdown validation | A custom cell-comment/manual-entry convention | POI's `DataValidationHelper.createExplicitListConstraint`/`createFormulaListConstraint` | Standard Excel data-validation XML; already the correct tool, just not yet used in this codebase |
| Era/effective-date resolution | A new "which row wins" algorithm | `ShiftTemplate.isEffectiveOn(LocalDate)` (already exists, already the ONE implementation `ShiftLibraryValidationService` and `AgentShiftAssignment#getEligibleShiftBandPairs()` both call, per its own javadoc) | A second copy of this predicate is exactly the "one implementation, not two that can drift" trap this project has already named and closed once (CR-01) |
| Desk-scoped bulk delete | A new query joining through desk | `AgentDayHoursRepository.deleteByAgent_Id(UUID)` — desk-scoping happens by the CALLER iterating `agentRepository.findByTenantIdAndDeskId(...)` first, exactly as `clearDesk` already does | `AgentDayHours` (and by design, `AgentUsualShift`) has no `desk_id` column; a repository method that tried to filter by desk directly would need a join the existing pattern deliberately avoids |

**Key insight:** every piece of this phase's mechanics already exists in the codebase in a sibling
form. The risk in this phase is not "what library/algorithm to use" — it is "did I actually wire the
new write/read path into every place the sibling pattern touches," which is exactly what D-14's
guard and the USHF-05 table exist to catch.

## Runtime State Inventory

Not applicable — this is a greenfield table addition (new entity, new migration, no rename or
refactor of existing stored data, config, or registrations). No existing runtime state references
"usual shift" under any prior name.

## Common Pitfalls

### Pitfall 1: `removeDeskAgent` does not currently clear `AgentDayHours` — and D-12 needs it to clear `AgentUsualShift`
**What goes wrong:** D-12 requires the desk-move path to clear usual shifts "through the SAME
clear-usual-shifts helper `clearDesk` calls." The natural assumption is that `removeDeskAgent`
already mirrors `clearDesk`'s cleanup list. It does not.
**Why it happens:** `[VERIFIED: src/main/java/com/wfm/service/DeskAgentService.java:180-193]`,
quoted in full:
```java
@Transactional
public void removeDeskAgent(UUID deskId, UUID agentId) {
    long tenantId = TenantContext.getTenantId();

    Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
            .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));

    // Clean up associated desk-scoped data for this agent
    agentPreferenceRepository.deleteByTenantIdAndDeskIdAndAgent_Id(tenantId, deskId, agentId);
    agentExceptionRepository.deleteByTenantIdAndDeskIdAndAgent_Id(tenantId, deskId, agentId);

    // Unassign: clear desk-specific fields
    agent.setDeskId(null);
    agent.setPrimarySpecialization(null);
    agent.getSecondarySpecializations().clear();
    agent.setContractedHoursPerDay(null);
    agentRepository.save(agent);
}
```
Note `AgentDayHours` is absent from this cleanup list — by design, since day-hours are treated as
following the agent's PERSON, not their desk assignment (unlike preferences/exceptions, which ARE
explicitly desk-scoped columns and get deleted here). `clearDesk`, by contrast, DOES delete
`AgentDayHours`: `[VERIFIED: DeskAssignmentUploadService.java:556-564]`, quoted:
```java
List<Agent> deskAgents = agentRepository.findByTenantIdAndDeskId(tenantId, deskId);
for (Agent agent : deskAgents) {
    agent.setDeskId(null);
    agent.setPrimarySpecialization(null);
    agent.getSecondarySpecializations().clear();
    agent.setContractedHoursPerDay(null);
    agentDayHoursRepository.deleteByAgent_Id(agent.getId());
    agentRepository.save(agent);
}
```
`clearDesk` and `removeDeskAgent` ALREADY disagree about whether day-hours survive a desk removal —
`clearDesk` deletes them, `removeDeskAgent` does not. This phase must not silently inherit that
disagreement for usual shifts; D-12 explicitly requires the SAME behavior on both paths for usual
shifts specifically (desk-scoped-through-the-template-library data, unlike day-hours, which are
person-scoped).
**How to avoid:** extract `clearUsualShifts(UUID agentId)` as a private helper (or a small
`UsualShiftService` method), call it from BOTH `clearDesk`'s per-agent loop AND from
`removeDeskAgent` (a new call site to add). Do not extend `deleteByAgent_Id`-style logic to
`AgentDayHours` itself — that would be an unrelated, unrequested behavior change to existing hours
semantics.
**Warning signs:** a test that reassigns an agent to a new desk and finds their OLD desk's usual
shift FK still resolvable (pointing at a template in a library the agent no longer belongs to) is
the signature of this gap being missed.

### Pitfall 2: There is no single "move agent to a different desk" endpoint — D-12's trigger point is two separate calls
**What goes wrong:** Searching for a "move" or "reassign" endpoint to hook the clear-usual-shifts
call into finds nothing, because no such endpoint exists.
**Why it happens:** the roster UI's desk-move flow, confirmed via
`[VERIFIED: frontend/src/pages/ClientManagement.tsx:149-155]` and
`[VERIFIED: src/main/java/com/wfm/controller/ClientManagementController.java:66-80]`, is actually
two independent HTTP calls: `DELETE /api/v1/client-management/desks/{deskId}/agents/{agentId}` →
`DeskAgentService.removeDeskAgent` (sets `deskId = null`), then separately
`POST /api/v1/client-management/assign-to-desk` → `ClientManagementService.assignEmployeesToDesk`
(re-derives the agent from BambooHR data and sets the NEW `deskId`). The second call's own guard,
`[VERIFIED: src/main/java/com/wfm/service/ClientManagementService.java:326-329]`, quoted:
```java
if (agent.getDeskId() != null && !agent.getDeskId().equals(deskId)) {
    throw new ConflictException("Agent '" + agent.getName() + "' is already assigned to another desk");
}
```
confirms this call REFUSES to move an agent directly between two non-null desks — it can only
assign an agent whose `deskId` is currently `null` (or already equal to the target). So the ONLY
place in this app's code where an agent's desk changes from one non-null value toward another is the
`removeDeskAgent` call setting it to `null` followed by a later `assignEmployeesToDesk` call. There
is a separate `DeskAgentService.assignAgents` (`POST /desks/{deskId}/agents`) with an identical
"must currently be unassigned" guard — used by a different UI flow, same structural shape.
**How to avoid:** treat `removeDeskAgent` as the desk-move trigger point for D-12 — the clear must
happen there, not in a "move" method that does not exist. (The upload path's own intra-workbook
desk-move handling already gets this for free: the SOURCE desk's sheet processing calls `clearDesk`,
which clears the row and the usual shift alike, before the DESTINATION sheet's row can succeed —
`[VERIFIED: DeskAssignmentUploadService.java:431-441]` names this exact scenario in its own
comments as *"Agent ... is being moved between desks in this workbook."*)
**Warning signs:** implementing D-12 by searching for a method literally named "move" or "transfer"
and finding none, then concluding D-12 is inapplicable — it is applicable, just triggered from
`removeDeskAgent`.

### Pitfall 3: The structural completeness guard (D-14) has no ready-made mechanism in this codebase — two real, imperfect options
**What goes wrong:** assuming `ScheduleConstraintClassification`'s reflection pattern
"just works" for this phase's very different problem (detecting a NEW WRITER anywhere in the
codebase, not classifying an already-enumerable, bounded set of constraint-provider methods).
**Why it happens:** `ScheduleConstraintClassificationTest`'s two derivations both enumerate members
of a SINGLE already-known class (`ConstraintWeights`'s annotated fields;
`ScheduleConstraintProvider`'s builder methods) — the domain being checked is bounded and knowable
by reflecting over one class. `DeskAssignmentUploadMultiSheetTest`'s D-16 guard,
`[VERIFIED: src/test/java/com/wfm/service/DeskAssignmentUploadMultiSheetTest.java:276-280]`, quoted:
```java
boolean hasAgentDayOffRepositoryField = Arrays.stream(DeskAssignmentUploadService.class.getDeclaredFields())
        .anyMatch(f -> AgentDayOffRepository.class.isAssignableFrom(f.getType()));
assertThat(hasAgentDayOffRepositoryField)
        .as("DeskAssignmentUploadService must not depend on AgentDayOffRepository (D-16)")
        .isFalse();
```
proves the ABSENCE of a dependency in ONE named class — it says nothing about any OTHER class in
the codebase gaining that dependency. Neither pattern, unmodified, can detect "a brand-new class
anywhere under `src/main/java` now autowires `AgentUsualShiftRepository`."
**ArchUnit is confirmed absent** `[VERIFIED: grep -c archunit build.gradle → 0 hits]` — it is not
available as a ready-made "list every class depending on X" tool.
**Two honest options, with tradeoffs, for the planner to choose between:**
1. **Spring-bean reflective scan.** A `@SpringBootTest`(or a narrower context slice)-based test
   that calls `applicationContext.getBeansOfType(Object.class)` (or, more targeted,
   inspects the specific beans the write-path table names), reflects over each bean's
   `getClass().getDeclaredFields()` (this project's dependency-injection idiom is constructor
   injection into `private final` instance fields, confirmed across every service class read this
   session — e.g. `DeskAgentService.java:25-32` — so declared-field reflection DOES see every
   injected repository), and asserts the resulting SET of classes holding a field assignable from
   `AgentUsualShiftRepository` equals an explicit allowlist matching the write-path table's rows
   exactly (mirroring `ScheduleConstraintClassificationTest`'s "derived set must equal hand-
   maintained set" shape, generalized from one class to every Spring bean). **This is a genuinely
   new pattern for this codebase** — only 3 test classes currently use `@SpringBootTest`
   `[VERIFIED: grep -rl "@SpringBootTest" src/test/java, 3 files]`, and none does a bean-scan of
   this kind — so it carries a real cost (full context bring-up) and unproven-in-this-codebase risk,
   but it is structurally correct: it can only miss a writer that is NOT a Spring bean (unlikely for
   a repository consumer in this codebase, since every repository injection observed this session
   is into a `@Service`/`@Component`/`@RestController`).
2. **Static source scan.** A JUnit test that walks `src/main/java` with `Files.walk`, reads each
   `.java` file's text, and asserts the set of files containing the literal string
   `AgentUsualShiftRepository` equals an explicit allowlist. No Spring context, fast, but purely
   textual — a match inside a comment or an unrelated string literal would be a false positive
   (acceptable — it fails safe, forcing a human look), and it cannot see a dependency injected
   through an interface/abstraction that hides the concrete repository type (not a risk in this
   codebase today, since every repository is injected by its concrete Spring Data interface type
   directly, per every constructor signature read this session).
**Recommendation:** option 2 (static source scan) is lower-risk to introduce given this codebase's
zero precedent for full-context bean scanning, faster, and matches the "structural-absence" idiom
`DeskAssignmentUploadMultiSheetTest`'s own comment already names (*"mirrors plan 10-01 Task 3's
structural-absence approach"*) — just widened from one file to a directory walk. Option 1 is more
semantically correct (it verifies actual Spring-managed dependency graphs, not textual occurrence)
and should be the planner's choice if test-suite runtime budget allows a new `@SpringBootTest`
class. Either way, the guard's assertion must be "declared set equals expected set," never a
containment/subset check — per `ScheduleConstraintClassificationTest`'s own explicit warning:
*"Widening any assertion below to a subset/containment check ... defeats [the guard's purpose]."*
**Warning signs:** a guard test with `isSubsetOf` or `contains` instead of `containsExactly`-style
equality is the guard converted into decoration, exactly as this project's own comment warns.

### Pitfall 4: The V39 `VARCHAR` vs `CHAR` migration/entity mismatch (G-14-1) is a recurring risk class for this exact table shape
**What goes wrong:** a Flyway migration declares a column type that Hibernate's `ddl-auto: validate`
disagrees with, applying cleanly (Flyway does not check entity mappings) and then failing app boot
— invisible to the H2-backed default test suite because H2 test schema is GENERATED from entities,
never validated against the migration SQL.
**Why it happens:** `[VERIFIED: src/main/resources/db/migration/V39__add_shift_template_and_scheduling_mode.sql,
comment on valid_weekdays column]`, quoted: *"VARCHAR(7), not CHAR(7): ShiftTemplate.validWeekdaysMask
is a String with @Column(length = 7), which Hibernate maps to varchar(7). Postgres reports CHAR(7)
as bpchar, so under ddl-auto: validate the application aborted at startup ... (UAT G-14-1)."* The
identical class of mismatch is repeated as a recorded lesson in V41's migration comment
(`agent_shift_assignment.template_name`, also `VARCHAR`, "the exact V39 mismatch"). This table's
`day_of_week` column follows `AgentDayHours`'s own precedent exactly — `[VERIFIED:
src/main/resources/db/migration/V29__agent_first_last_name_and_day_hours.sql]`:
`day_of_week VARCHAR(9) NOT NULL` matching `AgentDayHours.java`'s `@Column(name = "day_of_week",
length = 9)` on a `String`-backed `@Enumerated(STRING)` field.
**How to avoid:** for `AgentUsualShift`'s migration, use `VARCHAR(9)` for `day_of_week` (never
`CHAR`), and — since `shift_template_id` is a real FK — declare it as
`UUID NOT NULL REFERENCES shift_template(id)` in the migration DDL, matching the entity's
`@JoinColumn`. Any new fixed-length string column must default to `VARCHAR`, never `CHAR`, unless a
specific reason to pad is documented.
**Warning signs:** a green H2 suite is NOT evidence this class of bug is absent — only a
`PostgresBackedTest`-derived class proves it (see Validation Architecture below).

### Pitfall 5: 255-character Excel data-validation limit is silent corruption, not a graceful error
**What goes wrong:** if a desk's live template-name list, joined with commas, exceeds Excel's
255-character validation-text limit, Excel reports the FILE as corrupt on open — not a friendly
"list too long" message.
**Why it happens:** `createExplicitListConstraint` embeds the joined list directly in the
validation's `formula1` XML attribute, which Excel treats as a size-limited string field.
**How to avoid:** guard the explicit-list construction with a length check before calling
`createExplicitListConstraint`; either skip the dropdown (log/comment, parser validation still
applies per D-10's own "does not replace parser validation" clause) or implement the hidden-sheet
fallback if the planner scopes it in.
**Warning signs:** a template that opens fine in POI-generated tests (POI does not validate the
255-char rule at write time) but reports "corrupt" or "unreadable content" in real Excel/LibreOffis
is the signature — this cannot be caught by a unit test that only re-opens the file with POI; it
needs either a manual Excel open or a written assertion on the joined-string length.

## Code Examples

### Bulk-fetch, no-N+1 pattern for the roster GET path
`[VERIFIED: src/main/java/com/wfm/service/DeskAgentService.java:68-75, 94]` — the exact pattern to
copy for bulk-loading usual shifts alongside day-hours in `listDeskAgentResponses`:
```java
/** Single bulk per-desk fetch, grouped by agent then weekday — no N+1 (mirrors pendingByAgent). */
private Map<UUID, Map<DayOfWeek, AgentDayHours>> loadDayHoursByAgent(long tenantId, UUID deskId) {
    List<AgentDayHours> rows = agentDayHoursRepository.findByTenantIdAndDeskId(tenantId, deskId);
    return rows.stream()
            .collect(Collectors.groupingBy(
                    h -> h.getAgent().getId(),
                    Collectors.toMap(AgentDayHours::getDayOfWeek, h -> h)));
}
// ... called once per listDeskAgentResponses invocation:
Map<UUID, Map<DayOfWeek, AgentDayHours>> dayHoursByAgent = loadDayHoursByAgent(tenantId, deskId);
```
The desk-scoped bulk query it calls, `[VERIFIED: src/main/java/com/wfm/repository/AgentDayHoursRepository.java:26-28]`:
```java
// AgentDayHours has no desk_id column of its own; desk scoping goes through h.agent.deskId.
@Query("SELECT h FROM AgentDayHours h WHERE h.tenantId = :tenantId AND h.agent.deskId = :deskId")
List<AgentDayHours> findByTenantIdAndDeskId(long tenantId, UUID deskId);
```
`AgentUsualShiftRepository` needs the identical `@Query` shape, joining through `agent.deskId`
since `AgentUsualShift` also has no `desk_id` column of its own.

### Frontend day-tile insertion point (D-15)
`[VERIFIED: frontend/src/pages/DeskAgents.tsx:604-645]` — the exact seven-tile block, one `<div>`
per weekday, each currently containing only a day label and the hours editor/display:
```tsx
{DAY_ORDER.map(d => (
  <div key={d} style={{ textAlign: 'center' }}>
    <div style={{ fontSize: '0.8rem', fontWeight: 600 }}>{DAY_LABELS[d]}</div>
    <div>
      {editCell && editCell.agentId === da.id && editCell.day === d ? (
        /* ...existing hours <input> editor... */
      ) : (
        <DayCell entry={da.dayHours[d]} onClick={() => startEditCell(da, d)} />
      )}
    </div>
    {/* D-15: a second line goes HERE, inside this same per-weekday <div>, sibling to the
        existing hours <div> above — e.g. a <UsualShiftLine> component reading
        da.usualShift[d] and rendering D-16's three states, with its own click handler
        opening the D-17 <select>. */}
  </div>
))}
```
`DAY_ORDER`/`DAY_LABELS` are already exported from this file (`[VERIFIED:
frontend/src/pages/DeskAgents.tsx:9-13]`) for `ShiftLibrary.tsx` to import — the same export can
serve a new component file if the planner splits the tile into its own component.

### Frontend `<select>` data source (D-17)
`[VERIFIED: frontend/src/api/client.ts:193-194,330]` — the existing shift-template list endpoint
already returns everything D-17's picker needs, no new GET endpoint required:
```ts
export const shiftTemplates = {
  list: (deskId: string) => request<ShiftTemplate[]>(`/desks/${deskId}/shift-templates`),
  ...
}
export interface ShiftTemplate { id: string; name: string; startTime: string; endTime: string;
  bands: ShiftTemplateBreakBand[]; validWeekdays: string[]; effectiveFrom: string;
  effectiveTo: string | null; eraStatus: 'CURRENT' | 'UPCOMING' | 'PAST' }
```
D-17's picker options are `shiftTemplates.list(deskId)` filtered client-side to
`t.eraStatus === 'CURRENT' && t.validWeekdays.includes(DAY_ORDER[i])` — reuses the exact `eraStatus`
field `[VERIFIED: src/main/java/com/wfm/controller/ShiftTemplateController.java:100-109]` already
computes server-side, and the exact `validWeekdays` list Phase 14 already serializes. No new backend
endpoint is needed purely to populate this dropdown.

## State of the Art

Not applicable in the usual sense — there is no external "library version bump" or "old vs. new
approach" axis to this phase. The one internally-relevant "old approach, being explicitly avoided"
is documented in Don't Hand-Roll / Anti-Patterns above: the `resolvePreferences` duplication is this
codebase's own prior mistake, named as the pattern NOT to repeat.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The POI 255-character explicit-list limit and the hidden-sheet/named-range formula-list fallback mechanism are corroborated by web search (community examples + Javadoc summaries) but not by opening POI's own source or an official Apache POI doc page directly. | Standard Stack / Pattern 5 | Low — the 255-char limit is widely documented as an Excel platform limit (not POI-version-specific), and this project's shift-library sizes are small enough that the fallback is unlikely to be needed in practice; if the exact character count differs slightly, only the guard threshold needs adjustment |
| A2 | Recommending a real FK (`@ManyToOne` to `ShiftTemplate`, no denormalized name column) rather than mirroring `AgentShiftAssignment`'s denormalization is a reasoned recommendation, not a locked decision — CONTEXT's D-01 precedent citation ("AgentShiftAssignment already carries templateName and sourceTemplateId side by side") is ambiguous about whether it prescribes denormalization or merely illustrates the FK+name-resolution CONCEPT. | Pattern 1 / Anti-Patterns | Medium — if the planner intends a denormalized name column instead, the resolution service's read-through-the-FK step becomes read-the-stored-column instead; either is internally consistent, but the planner must pick one explicitly rather than let it fall out of ambiguity |
| A3 | The recommended structural-completeness-guard mechanism (static source scan, Pitfall 3 option 2) is a reasoned recommendation given this codebase's zero precedent for Spring-context bean-scanning tests — not verified against any existing implementation of either option in this or a comparable codebase. | Pitfall 3 | Medium — either option is buildable from first principles; the risk is scope/time, not feasibility |
| A4 | V46 is confirmed applied to the live dev database via `15-*/HANDOFF.md`'s recorded Flyway deploy log (`Migrating schema "public" to version "46 - default shift work contiguity to 10"`, dated 2026-09-01), not via a direct query against dev's Postgres in this research session — this sandbox has no network route to the dev database (a local Postgres container exists but belongs to an unrelated project, confirmed by role-authentication failure). | Version verification / Environment Availability | Low — the HANDOFF.md record is itself the project's own contemporaneous, evidenced confirmation (ECS task def image tag + Flyway log timestamp + frontend bundle content, four independent checks), not a stale assumption; the planner should still have the executor run a final check before applying V47 if dev has moved since 2026-09-01 |

## Open Questions

1. **Should `AgentUsualShift`'s write/read live inside `DeskAgentService` (extending the existing
   class, as `setDayHours` already does) or in a new dedicated service class?**
   - What we know: CONTEXT explicitly leaves this to discretion ("table/entity naming, DTO shape,
     endpoint paths ... follow existing conventions"); `DeskAgentController` already owns the
     `.../day-hours/{day}` endpoint pattern and could own `.../usual-shift/{day}` identically.
   - What's unclear: whether adding a fourth+fifth concern (usual-shift write + resolution) to the
     already-large `DeskAgentService` (currently 8 injected repositories) crosses a size threshold
     the planner should split at.
   - Recommendation: extend `DeskAgentController` (same file, new endpoint) but put the WRITE logic
     in a new `UsualShiftService` (parallel to how `AgentPreferenceService`/`AgentExceptionService`
     are already separate from `DeskAgentService` despite being called from the same controller) —
     this matches the existing separation-of-concerns precedent in `DeskAgentController`'s own
     constructor, which already injects five different service classes.

2. **Does `ShiftTemplateService.deleteShiftTemplate`'s usage guard need to ALSO check
   `AgentUsualShiftRepository`, now that a second FK-real table can reference a template?**
   - What we know: `deleteShiftTemplate` currently only guards on `agent_shift_assignment` usage
     (`[VERIFIED: ShiftTemplateService.java:141-147]`). A REAL FK from `AgentUsualShift` to
     `ShiftTemplate` means the DB itself will refuse a delete that violates the FK, but with a raw
     `DataIntegrityViolationException` rather than the existing legible `ConflictException` message.
   - What's unclear: whether this phase should extend `deleteShiftTemplate`'s guard message (a
     one-line addition to an existing method in a file this phase does not otherwise touch) or leave
     it to surface as an unhandled 500 until a future phase notices.
   - Recommendation: extend the guard — it is a single additional `count > 0` check mirroring the
     existing one, low-risk, and prevents a legibility regression this phase would otherwise
     introduce as a side effect of choosing a real FK.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | `PostgresBackedTest`-derived tests (required per G-14-1 for this phase's new table) | ✓ `[VERIFIED: docker info succeeded this session]` | — | — |
| Apache POI (poi-ooxml) | Template dropdown, export columns | ✓ `[VERIFIED: build.gradle:57]` | 5.3.0 | — |
| Live dev Postgres connectivity | Confirming true Flyway head from THIS session directly | ✗ `[VERIFIED: psql connection attempt this session failed — role "wfm" does not exist on the locally-running container, which belongs to an unrelated project "museproject"]` | — | Confirmed instead via `15-*/HANDOFF.md`'s recorded Flyway deploy log (V46 applied 2026-09-01) — see Assumption A4 |
| Node/npm (frontend build) | `frontend/` changes | ✓ `[VERIFIED: node --version → v24.19.0]` | v24.19.0 | — |
| Gradle | Backend build/test | ✓ `[VERIFIED: Gradle 8.12]` | 8.12 | — |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** live dev DB connectivity (fallback: HANDOFF.md's recorded
evidence, described above).

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ + Spring `@DataJpaTest`/`@SpringBootTest`, `[VERIFIED: existing test files read this session]` |
| Config file | `src/test/resources/application-test.yml` (H2, `flyway.enabled: false`, `ddl-auto: create-drop`) for the default suite; `PostgresBackedTest` (`src/test/java/com/wfm/support/PostgresBackedTest.java`) overrides via `@DynamicPropertySource` to a real Postgres 16 Testcontainer with `flyway.enabled: true`, `ddl-auto: validate` |
| Quick run command | `./gradlew test --tests "com.wfm.service.*UsualShift*"` (scoped, for iteration) |
| Full suite command | `./gradlew test` — baseline `[VERIFIED: 15-*/HANDOFF.md: "590 tests, 0 failures, 0 errors, BUILD SUCCESSFUL in 8m 8s"]` |

### Phase Requirement → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| USHF-01 | Stored usual shift references a valid, active desk-scoped template | unit (H2) | `./gradlew test --tests DeskAgentServiceUsualShiftTest` | ❌ Wave 0 |
| USHF-01 | New `agent_usual_shift` migration is entity-consistent (VARCHAR-not-CHAR class of bug, G-14-1) | Postgres-backed | `./gradlew test --tests AgentUsualShiftPostgresTest` | ❌ Wave 0 — MUST extend `PostgresBackedTest` |
| USHF-02 | Upload parses seven Usual Shift columns; blank = none (D-07); unknown name skips cell + warns (D-08) | unit (H2, mirrors `DeskAssignmentUploadMultiSheetTest` style) | `./gradlew test --tests DeskAssignmentUploadUsualShiftTest` | ❌ Wave 0 |
| USHF-02 | Template pre-fill round-trips stored values (D-09); sheet-scoped dropdown attaches without corrupting the workbook | unit (POI re-open with POI itself — NOT a substitute for a manual Excel-open check, see Pitfall 5) | `./gradlew test --tests DeskAssignmentTemplateServiceUsualShiftTest` | ❌ Wave 0 |
| USHF-03 | Inline write rejects a weekday-mask-excluded template with 400 (D-03); tenant/desk IDOR guard (T-13-05 shape) | unit (H2) | `./gradlew test --tests UsualShiftServiceTest` | ❌ Wave 0 |
| USHF-04 | No stored row resolves to empty/no-penalty, not a default | unit | `./gradlew test --tests UsualShiftResolutionServiceTest` | ❌ Wave 0 |
| USHF-05 | Each of the 7 write paths (upload, inline, BambooHR refresh, clearDesk, desk move, mode switch, solver) proven to leave usual-shift data in its documented state | integration, ONE test per path, at least the desk-move/clearDesk pair MUST be Postgres-backed (real FK enforcement) | `./gradlew test --tests "*UsualShiftWritePath*"` | ❌ Wave 0 |
| USHF-05 | Structural completeness guard (D-14) — new writer without table row fails the build | structural (see Pitfall 3) | `./gradlew test --tests UsualShiftWritePathGuardTest` | ❌ Wave 0 |
| USHF-06 | Roster GET response includes resolved usual shift per weekday, all three D-16 states reachable | unit (H2) | `./gradlew test --tests DeskAgentServiceUsualShiftTest` (shared with USHF-01) | ❌ Wave 0 |
| USHF-06 | Export includes seven new columns at the correct index; First/Last Name shift right by 7 (mirrors Phase 13 P-09) | unit `[VERIFIED: existing DeskAgentExportServiceTest.java precedent to extend]` | `./gradlew test --tests DeskAgentExportServiceTest` | ✓ (extend existing file) |

### Sampling Rate
- **Per task commit:** the scoped `--tests` command for whichever file the task touches.
- **Per wave merge:** full suite (`./gradlew test`) — mandatory given USHF-05/XCUT-02's
  cross-cutting nature; a change to `clearDesk` or `removeDeskAgent` risks regressing unrelated
  existing tests (`DeskAssignmentUploadMultiSheetTest`, `DeskAgentServiceDayHoursTest`, etc.) that
  already assert on those exact methods' current behavior.
- **Phase gate:** full suite green, PLUS a manual Excel open-and-inspect of a generated template
  with the new dropdown (POI round-trip tests cannot catch Excel-side corruption from the 255-char
  limit, per Pitfall 5) before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `src/test/java/com/wfm/service/DeskAgentServiceUsualShiftTest.java` — covers USHF-01, USHF-03,
      USHF-04, USHF-06 (mirrors `DeskAgentServiceDayHoursTest.java`'s `@DataJpaTest` + `@Import`
      style)
- [ ] `src/test/java/com/wfm/support/AgentUsualShiftPostgresTest.java` (or similarly named) —
      extends `PostgresBackedTest`, covers USHF-01's migration-vs-entity drift risk (G-14-1 class)
- [ ] `src/test/java/com/wfm/service/DeskAssignmentUploadUsualShiftTest.java` (or extend the
      existing `DeskAssignmentUploadMultiSheetTest.java`) — covers USHF-02, D-07, D-08, D-09, D-11
- [ ] `src/test/java/com/wfm/service/UsualShiftResolutionServiceTest.java` — covers D-01, D-02,
      USHF-04
- [ ] The D-14 structural guard test (name/location per Pitfall 3's chosen option)
- [ ] Framework install: none — every framework needed is already a project dependency

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Unchanged — this phase adds no auth surface |
| V3 Session Management | No | Unchanged |
| V4 Access Control | Yes | Tenant + desk scoping on every new endpoint, mirroring `[VERIFIED: DeskAgentService.java:296-299]`'s T-13-05 pattern: resolve the agent via `agentRepository.findByIdAndTenantIdAndDeskId(...)` BEFORE any `AgentUsualShiftRepository` call, never accept a raw agent id for a repository lookup |
| V5 Input Validation | Yes | `IllegalArgumentException` → 400 for weekday-mask violations (D-03), reusing the existing `GlobalExceptionHandler` mapping (`[VERIFIED: GlobalExceptionHandler.java:32-33]`); formula-injection sanitization on the export/template paths already exists via `FormulaInjectionSanitizer` — the new Usual Shift columns carry only template NAMES (operator-controlled via the shift library, not free-text agent input), so injection risk is lower than the identity columns, but should still route through the same sanitizer for consistency if the planner writes raw strings to cells |
| V6 Cryptography | No | Not applicable |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| IDOR — an operator on Desk A setting/reading another desk's agent's usual shift by guessing a UUID | Elevation of Privilege | `findByIdAndTenantIdAndDeskId` resolution before any repository call, exactly as `setDayHours` already does — the SAME guard, applied to the new endpoint |
| A new writer of `AgentUsualShiftRepository` bypassing the choke point (the exact shape of audit finding I-2) | Tampering | D-14's structural guard (Pitfall 3) — this IS the mitigation this phase is required to build |
| Cross-tenant template reference — an operator's usual-shift FK pointing at a `ShiftTemplate` belonging to a different tenant/desk | Tampering / Information Disclosure | Validate `shiftTemplateId` against `shiftTemplateRepository.findByIdAndTenantIdAndDeskId(...)` (already exists, `[VERIFIED: ShiftTemplateRepository.java:17]`) before accepting a write, exactly mirroring how the agent itself is resolved |

## Sources

### Primary (HIGH confidence)
- Direct file reads this session (all `[VERIFIED: ...]` tags above) — `AgentDayHours.java`,
  `AgentDayHoursRepository.java`, `ShiftTemplate.java`, `ShiftTemplateRepository.java`,
  `ShiftTemplateService.java`, `ShiftTemplateController.java`, `AgentShiftAssignment.java`,
  `DeskAgentService.java` (full), `DeskAgentController.java`, `DeskAssignmentUploadService.java`
  (relevant sections), `DeskAssignmentTemplateService.java` (full), `DeskAgentExportService.java`
  (relevant sections), `EnrichedColumnLayout.java` (full), `SolverService.java` (resolvePreferences),
  `ScheduleService.java` (resolvePreferences), `BambooRefreshService.java` (constructor +
  persistRefreshData), `DeskService.java` (switchSchedulingMode, deleteDesk), `ClientManagementService.java`
  (assignEmployeesToDesk), `ClientManagementController.java` (full), `GlobalExceptionHandler.java`
  (relevant lines), `PostgresBackedTest.java` (full), `ScheduleConstraintClassification.java` (full),
  `ScheduleConstraintClassificationTest.java` (full), `WorkingDaysSourceGuardTest.java` (full),
  `DeskAssignmentUploadMultiSheetTest.java` (D-16 guard section), `DeskAgentServiceDayHoursTest.java`
  (setup section), `DeskAgentResponse.java`, `SetDayHoursRequest.java`, migration files V29, V39,
  V41 (headers + DDL), `frontend/src/pages/DeskAgents.tsx` (relevant sections),
  `frontend/src/api/client.ts` (relevant sections), `build.gradle` (dependency + plugin sections),
  `.planning/phases/15-shift-envelope-breaks-library-generation/HANDOFF.md` (V46 deploy evidence)
- `gsd_run` tool-based checks: `docker info`, `psql` connectivity attempt, `node --version`,
  `java -version`, `./gradlew --version`

### Secondary (MEDIUM confidence)
- WebSearch: "Apache POI 5.3.0 XSSFDataValidationHelper createExplicitListConstraint 255 character
  limit" — corroborates the Excel-imposed 255-character validation-text limit
- WebSearch: "POI DataValidationHelper createFormulaListConstraint hidden sheet named range
  dropdown" — corroborates the hidden-sheet/named-range fallback mechanism and its API shape

### Tertiary (LOW confidence)
- None used as load-bearing claims — every claim in this document is either file-verified this
  session or corroborated by a web search this session; nothing rests on unverified training
  knowledge alone (flagged explicitly in the Assumptions Log where corroboration was indirect).

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependency, POI version and usage confirmed by direct file read
- Architecture: HIGH — every pattern this phase copies was read in full from the live codebase this
  session, including exact file/line citations and verbatim quotes
- Pitfalls: HIGH for pitfalls 1, 2, 4, 5 (each grounded in a verbatim-quoted source read this
  session); MEDIUM for pitfall 3 (the guard mechanism itself is a reasoned recommendation, not a
  verified precedent, since no exact precedent exists in this codebase)

**Research date:** 2026-09-03
**Valid until:** 30 days (stable, internal-codebase-grounded research; the only external-facing
claim — the POI 255-char limit — is a long-stable Excel platform behavior, not a fast-moving API)
