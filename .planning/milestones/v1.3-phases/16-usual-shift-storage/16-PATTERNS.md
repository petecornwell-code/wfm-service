# Phase 16: Usual Shift Storage - Pattern Map

**Mapped:** 2026-09-03
**Files analyzed:** 17 (new + modified)
**Analogs found:** 17 / 17 (all have a strong, previously-verified analog; the D-14 structural
guard has no exact precedent — two imperfect analogs documented below, per RESEARCH.md Pitfall 3)

This phase is unusual: CONTEXT.md and RESEARCH.md already named nearly every analog with file/line
citations. This document verifies those citations still hold (all confirmed live this session) and
adds the concrete excerpts the planner copies from directly, plus a few details RESEARCH.md didn't
spell out (exact migration DDL shape, exact export column-index math, exact controller/client stubs).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/java/com/wfm/model/AgentUsualShift.java` (new) | model | CRUD | `src/main/java/com/wfm/model/AgentDayHours.java` | exact |
| `src/main/java/com/wfm/repository/AgentUsualShiftRepository.java` (new) | repository | CRUD | `src/main/java/com/wfm/repository/AgentDayHoursRepository.java` | exact |
| `src/main/resources/db/migration/V47__add_agent_usual_shift.sql` (new) | migration | batch | `src/main/resources/db/migration/V29__agent_first_last_name_and_day_hours.sql` (day_of_week VARCHAR(9) shape) + `V39__add_shift_template_and_scheduling_mode.sql` (VARCHAR-not-CHAR lesson, FK-to-shift_template shape) | exact |
| `src/main/java/com/wfm/service/UsualShiftService.java` (new — recommended split, see Open Question 1) | service | request-response | `src/main/java/com/wfm/service/DeskAgentService.java` `setDayHours` (~line 289) + `upsertDayHoursRow` (~335) | exact (shape), new class |
| `src/main/java/com/wfm/service/UsualShiftResolutionService.java` (new) | service | transform | `src/main/java/com/wfm/service/SolverService.java` `resolvePreferences` (~567) / `ScheduleService.java` `resolvePreferences` (~486) | role-match (precedence shape, not duplication) |
| `src/main/java/com/wfm/controller/DeskAgentController.java` (modify — add endpoint) | controller | request-response | same file, `setDayHours` (~85) | exact |
| `src/main/java/com/wfm/dto/SetUsualShiftRequest.java` (new) | model (DTO) | request-response | `src/main/java/com/wfm/dto/SetDayHoursRequest.java` | exact |
| `src/main/java/com/wfm/util/EnrichedColumnLayout.java` (modify — add `usualShiftHeader`) | utility | transform | same file, `dayHeader(DayOfWeek)` | exact |
| `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` (modify — parser loop + clearDesk) | service | batch / file-I/O | same file, day-cell read loop (~521) and `clearDesk` (~550) | exact |
| `src/main/java/com/wfm/service/DeskAssignmentTemplateService.java` (modify — pre-fill + dropdown) | service | file-I/O | same file, `buildHeaders()` (~114) and row writer (~74) | exact |
| `src/main/java/com/wfm/service/DeskAgentExportService.java` (modify — 7 columns) | service | file-I/O | same file, `writeDayCells` (~103) and header loop (~42) | exact |
| `src/main/java/com/wfm/service/DeskAgentService.java` (modify — `removeDeskAgent` gains `clearUsualShifts` call) | service | CRUD | same file, `removeDeskAgent` (~180) | exact |
| `src/main/java/com/wfm/dto/DeskAgentResponse.java` (modify — add `usualShift` field) | model (DTO) | request-response | same file, existing `dayHours` field shape | exact |
| `frontend/src/pages/DeskAgents.tsx` (modify — tile 2nd line + `<select>`) | component | event-driven | same file, day-tile block (~604) and hours-cell editor (`cellDirtyRef` guard) | exact |
| `frontend/src/api/client.ts` (modify — add `setUsualShift`) | service (API client) | request-response | same file, `deskAgents.setDayHours` (~156) | exact |
| `src/test/java/com/wfm/service/DeskAgentServiceUsualShiftTest.java` (new) | test | CRUD | `src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java` | exact |
| `src/test/java/com/wfm/support/AgentUsualShiftPostgresTest.java` (new) | test | batch | `src/test/java/com/wfm/repository/AgentRepositoryPostgresTest.java` (extends `PostgresBackedTest`) | exact |
| `src/test/java/com/wfm/service/UsualShiftWritePathGuardTest.java` (new, D-14) | test | event-driven | `src/test/java/com/wfm/service/DeskAssignmentUploadMultiSheetTest.java` (D-16 field-absence guard) + `ScheduleConstraintClassificationTest.java` (dual-derivation-equality guard) | role-match, no exact precedent (see "No Analog Found") |

## Pattern Assignments

### `src/main/java/com/wfm/model/AgentUsualShift.java` (model, CRUD)

**Analog:** `src/main/java/com/wfm/model/AgentDayHours.java` (full file, 65 lines — copy verbatim structure)

**Full analog** `[VERIFIED live this session]`:
```java
package com.wfm.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.UUID;

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
    // ... dayOffType field + all getters/setters
}
```

**Adaptation for `AgentUsualShift`:** identical `@Table`/`@Id`/`tenantId`/`agent` block; replace
`hours`/`dayOffType` with a REAL FK (per RESEARCH.md Pattern 1's recommendation and Anti-Patterns
section — do NOT denormalize the template name):
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "shift_template_id", nullable = false)
private ShiftTemplate shiftTemplate;
```
Table name `agent_usual_shift`, unique constraint on `{"agent_id", "day_of_week"}` — same shape.

---

### `src/main/java/com/wfm/repository/AgentUsualShiftRepository.java` (repository, CRUD)

**Analog:** `src/main/java/com/wfm/repository/AgentDayHoursRepository.java` (full file, 45 lines)

**Full analog** `[VERIFIED live this session]`:
```java
package com.wfm.repository;

import com.wfm.model.AgentDayHours;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentDayHoursRepository extends JpaRepository<AgentDayHours, UUID> {

    List<AgentDayHours> findByTenantIdAndAgent_Id(long tenantId, UUID agentId);

    // Single-weekday finder for the per-cell upsert (setDayHours, D-05). Not tenant-scoped,
    // mirroring deleteByAgent_Id below -- callers must resolve tenant scope via AgentRepository
    // before calling this (T-13-05).
    Optional<AgentDayHours> findByAgent_IdAndDayOfWeek(UUID agentId, DayOfWeek dayOfWeek);

    // Bulk fetch for SolverService -- mirrors AgentDayOffRepository's join-through-agent style.
    // AgentDayHours has no desk_id column of its own; desk scoping goes through h.agent.deskId.
    @Query("SELECT h FROM AgentDayHours h WHERE h.tenantId = :tenantId AND h.agent.deskId = :deskId")
    List<AgentDayHours> findByTenantIdAndDeskId(long tenantId, UUID deskId);

    // ... findDaysOffByTenantIdAndDeskId (irrelevant to usual shift), deleteByAgent_Id(UUID agentId)
    void deleteByAgent_Id(UUID agentId);
}
```

**Adaptation:** `AgentUsualShiftRepository` needs the same four method shapes:
`findByTenantIdAndAgent_Id`, `findByAgent_IdAndDayOfWeek` (single-weekday upsert finder),
`findByTenantIdAndDeskId` (identical `@Query` joining `agent.deskId` — copy the comment verbatim,
it explains why there's no `desk_id` column), and `deleteByAgent_Id` (used by both `clearDesk` and
the new `removeDeskAgent` call site, D-11/D-12).

---

### `src/main/resources/db/migration/V47__add_agent_usual_shift.sql` (migration, batch)

**Migration head confirmed:** `[VERIFIED: ls src/main/resources/db/migration | sort -V | tail -1
this session → V46__default_shift_work_contiguity_to_10.sql]`. **V47 is correct.**

**Column-type analog** — `day_of_week VARCHAR(9) NOT NULL` (never `CHAR`), per `V29__agent_first_last_name_and_day_hours.sql`, matching `AgentDayHours.java`'s `@Column(length = 9)` on a `String`-backed `@Enumerated(STRING)` field. RESEARCH.md Pitfall 4 documents the exact V39 `CHAR` vs `VARCHAR` production incident (G-14-1) this must not repeat.

**FK shape:** `shift_template_id UUID NOT NULL REFERENCES shift_template(id)`, matching the entity's `@JoinColumn`.

Recommended DDL shape (synthesized from the two verified analogs, entity fields above):
```sql
CREATE TABLE agent_usual_shift (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id BIGINT NOT NULL,
    agent_id UUID NOT NULL REFERENCES agent(id),
    day_of_week VARCHAR(9) NOT NULL,
    shift_template_id UUID NOT NULL REFERENCES shift_template(id),
    CONSTRAINT uq_agent_usual_shift_agent_day UNIQUE (agent_id, day_of_week)
);
```
(Confirm exact `agent(id)`/`shift_template(id)` PK column names and `gen_random_uuid()` vs.
`GenerationType.UUID` default-generation convention against `V29`/`V39` directly before finalizing —
this is a synthesis, not a verbatim quote, since no migration in this codebase adds a
FK-plus-unique-pair table in exactly this shape simultaneously.)

---

### `src/main/java/com/wfm/service/UsualShiftService.java` (service, request-response) — choke-point write

**Analog:** `src/main/java/com/wfm/service/DeskAgentService.java` `setDayHours` (lines 281-328, confirmed live)

**Full analog** `[VERIFIED live this session]`:
```java
@Transactional
public DeskAgentResponse setDayHours(UUID deskId, UUID agentId, DayOfWeek day,
                                      BigDecimal hours, DayOffType dayOffType, boolean clearRow) {
    long tenantId = TenantContext.getTenantId();

    BigDecimal scheduleDefault = resolveScheduleDefault(tenantId, deskId);

    // Mandatory access-control step (T-13-05): resolve the agent within tenant+desk scope
    // BEFORE any AgentDayHoursRepository call -- findByAgent_IdAndDayOfWeek accepts a raw
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

**Adaptation for `setUsualShift(deskId, agentId, day, shiftTemplateId, clearRow)`:**
1. Same tenant+desk-scoped `agentRepository.findByIdAndTenantIdAndDeskId` guard, first line of body.
2. `clearRow` → `agentUsualShiftRepository.findByAgent_IdAndDayOfWeek(agentId, day).ifPresent(::delete)`.
3. `shiftTemplateId` supplied → resolve via `shiftTemplateRepository.findByIdAndTenantIdAndDeskId(...)`
   (`[VERIFIED: ShiftTemplateRepository.java:17]` per RESEARCH.md's Security Domain section — the
   cross-tenant-template-reference guard) → validate `isEffectiveOn(today)` and
   `getValidWeekdays().contains(day)` → **reject with `IllegalArgumentException` (not clamp) if the
   weekday mask excludes `day` (D-03)** → upsert-or-create row (mirror `upsertDayHoursRow` shape below).
4. Same `flush()` + re-read pattern so the response reflects the write.

**upsert-or-create helper analog** `[VERIFIED: DeskAgentService.java:335-344]`:
```java
private void upsertDayHoursRow(Agent agent, DayOfWeek day, BigDecimal hours, DayOffType dayOffType) {
    AgentDayHours row = agentDayHoursRepository.findByAgent_IdAndDayOfWeek(agent.getId(), day)
            .orElseGet(AgentDayHours::new);
    row.setTenantId(TenantContext.getTenantId());
    row.setAgent(agent);
    row.setDayOfWeek(day);
    row.setHours(hours);
    row.setDayOffType(dayOffType);
    agentDayHoursRepository.save(row);
}
```
Copy this exact reuse-or-create shape for `upsertUsualShiftRow(agent, day, shiftTemplate)`.

**Error handling:** `IllegalArgumentException` → 400 via existing `GlobalExceptionHandler`
`[VERIFIED: src/main/java/com/wfm/controller/GlobalExceptionHandler.java:32-33]`,
`@ExceptionHandler(IllegalArgumentException.class)` — no new exception-handling code needed.

---

### `src/main/java/com/wfm/service/UsualShiftResolutionService.java` (service, transform)

**Analog:** `SolverService.resolvePreferences` (line 567) / `ScheduleService.resolvePreferences`
(line 486) — **copy the precedence SHAPE, not the duplication.** One implementation only.

**Concrete algorithm** (from RESEARCH.md Pattern 2, grounded in two verified repository/entity methods):
```java
public Optional<ShiftTemplate> resolve(AgentUsualShift stored, LocalDate date) {
    if (stored == null) return Optional.empty();                    // USHF-04: no row = no penalty
    String name = stored.getShiftTemplate().getName();               // read-through the real FK
    List<ShiftTemplate> eras = shiftTemplateRepository
            .findByTenantIdAndDeskIdAndName(tenantId, deskId, name); // [VERIFIED: ShiftTemplateRepository.java:21]
    return eras.stream()
            .filter(t -> t.isEffectiveOn(date))                      // [VERIFIED: ShiftTemplate.java:153]
            .findFirst();                                            // D-11 non-overlap => at most one match
}
```
`ShiftTemplate.isEffectiveOn(LocalDate)` `[VERIFIED: ShiftTemplate.java:153-158]`:
```java
@Transient
public boolean isEffectiveOn(LocalDate date) {
    if (effectiveFrom != null && effectiveFrom.isAfter(date)) {
        return false;
    }
    return effectiveTo == null || !effectiveTo.isBefore(date);
}
```
**Callers (all must go through this one service):** roster GET (D-16 discriminator), export (D-18
raw value — note export uses the RAW stored name, not this resolver, per UI-SPEC Component Spec §3),
and — out of scope this phase — Phase 17's solver/drift report.

**Anti-pattern to avoid:** do not write a second `resolvePreferences`-shaped method. This codebase
already has that duplication twice (`SolverService.java:567`, `ScheduleService.java:486` — the
latter's javadoc literally says "Mirrors the logic in SolverService.resolvePreferences"), cited in
RESEARCH.md as the cautionary, not aspirational, example.

---

### `src/main/java/com/wfm/controller/DeskAgentController.java` (controller, request-response)

**Analog:** same file, `setDayHours` endpoint (confirmed live this session, exact text):
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

**New endpoint** (recommended, per RESEARCH.md Open Question 1 — endpoint stays on
`DeskAgentController`, write logic moves to a new `UsualShiftService`, injected as a 6th service in
the controller's constructor — mirrors how `AgentPreferenceService`/`AgentExceptionService` are
already separate from `DeskAgentService` despite sharing this controller):
```java
@PutMapping("/{agentId}/usual-shift/{day}")
public DeskAgentResponse setUsualShift(@PathVariable UUID deskId,
                                        @PathVariable UUID agentId,
                                        @PathVariable DayOfWeek day,
                                        @RequestBody SetUsualShiftRequest request) {
    return usualShiftService.setUsualShift(deskId, agentId, day,
            request.shiftTemplateId(), Boolean.TRUE.equals(request.clearRow()));
}
```

**DTO analog** `[VERIFIED: src/main/java/com/wfm/dto/SetDayHoursRequest.java:7]`:
```java
public record SetDayHoursRequest(BigDecimal hours, DayOffType dayOffType, Boolean clearRow) {}
```
→ `public record SetUsualShiftRequest(UUID shiftTemplateId, Boolean clearRow) {}`

---

### `src/main/java/com/wfm/util/EnrichedColumnLayout.java` (utility, transform)

Full file already read this session per RESEARCH.md — exposes `DAY_ORDER` (`DayOfWeek[]`),
`dayHeader(DayOfWeek)` (returns e.g. `"Monday"`), `normalize(String)` (trim+lowercase key). No
existing second-column-group concept. Add one function following the identical one-function
convention:
```java
public static String usualShiftHeader(DayOfWeek d) {
    return "Usual Shift " + dayHeader(d);
}
```
This is the ONLY place the `"Usual Shift "` string literal may appear — every consumer (parser,
template generator, export) must call this function, never hardcode the string (I-4's drift class,
already closed once in Phase 13 for the existing day-hours group).

**Every consumer to modify** (table from RESEARCH.md Pattern 4, verified file/line):

| Consumer | File:line | What to add |
|---|---|---|
| Header validation | `DeskAssignmentUploadService.java:252-256` | Extend `missingHeaders` check with a second `DAY_ORDER` loop over `usualShiftHeader(d)` — but per D-07, a missing/blank Usual Shift cell is VALID, so this may need to be a soft check, not a hard "missing header" failure — confirm against D-07 at plan time |
| Day-cell read loop | `DeskAssignmentUploadService.java:521-534` (exact loop shape to copy) | Parallel loop reading `usualShiftHeader(d)` columns; blank = none (D-07), unresolvable name = skip cell + warn (D-08) |
| `clearDesk` | `DeskAssignmentUploadService.java:550-565` (verified full body below) | Add `usualShiftService.clearUsualShifts(agent.getId())` (or repository call) beside `agentDayHoursRepository.deleteByAgent_Id(agent.getId())` |
| Template header builder | `DeskAssignmentTemplateService.java:114-122` (`buildHeaders()`) | Append second `DAY_ORDER` loop calling `usualShiftHeader(d)` |
| Template row writer | `DeskAssignmentTemplateService.java:74-84` | D-09: pre-fill the seven cells with stored values — a genuinely new write, not a no-op (comment there currently says "Columns 7-13 ... intentionally left blank") |
| Export header builder | `DeskAgentExportService.java:35-46` (verified below) | Insert `usualShiftHeader(d)` loop between the `DAY_ORDER` loop and `COL_FIRST_NAME`/`COL_LAST_NAME` |
| Export row writer | `DeskAgentExportService.java:103-121` (`writeDayCells`, verified below) | New `FIRST_USUAL_SHIFT_COLUMN = 20` constant + loop; shift `FIRST_DAY_COLUMN + 7`/`+8` (First/Last Name) to `FIRST_USUAL_SHIFT_COLUMN + 7`/`+8` |

**`clearDesk` full verified body** (confirmed live this session, matches RESEARCH.md quote exactly):
```java
private void clearDesk(long tenantId, UUID deskId) {
    log.info("Clearing desk {} for tenant {} before spreadsheet re-import", deskId, tenantId);
    // Remove desk-scoped data
    agentPreferenceRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
    agentExceptionRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
    // Unassign all agents from the desk
    List<Agent> deskAgents = agentRepository.findByTenantIdAndDeskId(tenantId, deskId);
    for (Agent agent : deskAgents) {
        agent.setDeskId(null);
        agent.setPrimarySpecialization(null);
        agent.getSecondarySpecializations().clear();
        agent.setContractedHoursPerDay(null);
        agentDayHoursRepository.deleteByAgent_Id(agent.getId());
        agentRepository.save(agent);
    }
}
```
Add `agentUsualShiftRepository.deleteByAgent_Id(agent.getId())` inside this same loop (D-11).

---

### `src/main/java/com/wfm/service/DeskAgentService.java` (`removeDeskAgent`, modify) — desk-move clear (D-12)

**Full verified body** (confirmed live this session, matches RESEARCH.md Pitfall 1 quote exactly):
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
**Confirmed gap (RESEARCH.md Pitfall 1):** `AgentDayHours` is deliberately absent here (day-hours
follow the person, not the desk). Usual shift must NOT inherit this — D-12 requires the SAME clear
behavior as `clearDesk`. **Add `agentUsualShiftRepository.deleteByAgent_Id(agentId)` (or the shared
`clearUsualShifts` helper) to this method's body** — this is a new call site, the only place in the
app where a desk-to-desk move actually happens (per RESEARCH.md Pitfall 2 — there is no atomic
"move" endpoint; `removeDeskAgent` sets `deskId = null`, a later `assignEmployeesToDesk` call sets
the new desk).

**Extract as a shared helper** (D-11/D-12 "one implementation, two callers"):
```java
// Called from both DeskAssignmentUploadService.clearDesk and DeskAgentService.removeDeskAgent.
void clearUsualShifts(UUID agentId) {
    agentUsualShiftRepository.deleteByAgent_Id(agentId);
}
```

---

### `src/main/java/com/wfm/service/DeskAgentExportService.java` (modify) — 7 export columns (D-18)

**Header builder, full verified excerpt** (`FIRST_DAY_COLUMN = 13` confirmed at line 23):
```java
List<String> columns = new ArrayList<>(List.of(
    "ID", "Desk ID", EnrichedColumnLayout.COL_BAMBOOHR_ID, "Name", EnrichedColumnLayout.COL_EMAIL,
    EnrichedColumnLayout.COL_DEPARTMENT, EnrichedColumnLayout.COL_JOB_TITLE,
    EnrichedColumnLayout.COL_ACTIVE, "Last Refreshed At",
    "Primary Specialization", "Secondary Specializations",
    "Contracted Hours Per Day", "Effective Contracted Hours Per Day"
));
for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
    columns.add(EnrichedColumnLayout.dayHeader(day));
}
columns.add(EnrichedColumnLayout.COL_FIRST_NAME);
columns.add(EnrichedColumnLayout.COL_LAST_NAME);
```
**Adaptation:** insert a second `DAY_ORDER` loop calling `usualShiftHeader(day)` immediately after
the existing `dayHeader` loop and before `COL_FIRST_NAME`/`COL_LAST_NAME` — this is the exact D-18
insertion point.

**Row writer, full verified excerpt** (`writeDayCells`, called from the main row-writing block which
also does `row.createCell(FIRST_DAY_COLUMN + 7).setCellValue(sanitize(agent.firstName()))` and
`+ 8` for lastName immediately after `writeDayCells(row, agent)`):
```java
private void writeDayCells(Row row, DeskAgentResponse agent) {
    Map<DayOfWeek, DayHoursEntry> dayHours = agent.dayHours();
    DayOfWeek[] order = EnrichedColumnLayout.DAY_ORDER;
    for (int i = 0; i < order.length; i++) {
        DayHoursEntry entry = dayHours != null ? dayHours.get(order[i]) : null;
        Cell cell = row.createCell(FIRST_DAY_COLUMN + i);
        if (entry == null) {
            cell.setCellValue(0);
        } else if (entry.dayOffType() == DayOffType.MANDATORY) {
            cell.setCellValue("MANDATORY");
        } else if (entry.dayOffType() == DayOffType.PTO) {
            cell.setCellValue("PTO");
        } else if (entry.hasRow()) {
            cell.setCellValue(entry.hours().doubleValue());
        } else {
            cell.setCellValue(entry.effectiveHours().doubleValue());
        }
    }
}
```
**Adaptation:** add `private static final int FIRST_USUAL_SHIFT_COLUMN = 20;` (13 + 7), a new
`writeUsualShiftCells(row, agent)` writing the raw stored template name (blank if none, per D-18's
"raw stored value, not the roster's three-state distinction") at `FIRST_USUAL_SHIFT_COLUMN + i`, and
change `row.createCell(FIRST_DAY_COLUMN + 7)`/`+ 8` to `row.createCell(FIRST_USUAL_SHIFT_COLUMN + 7)`/`+ 8`
for First/Last Name — the exact index-shift precedent Phase 13's P-09 already established once for
this same column group.

---

### `frontend/src/pages/DeskAgents.tsx` (component, event-driven)

**Analog:** same file, day-tile block (verified `[VERIFIED: DeskAgents.tsx:604-645]` per RESEARCH.md,
matches the researched excerpt):
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
    {/* D-15: usual-shift second line goes HERE, sibling to the hours <div> above */}
  </div>
))}
```
`DAY_ORDER`/`DAY_LABELS` already exported from this file for `ShiftLibrary.tsx` to reuse.

**D-16 three-state contract** (from UI-SPEC.md Component Spec §1 — backend-computed discriminator,
do NOT reimplement the resolution logic client-side, per the same anti-duplication principle as
`resolvePreferences`):
```ts
interface UsualShiftEntry {
  status: 'NOT_SET' | 'LIVE' | 'STORED_INACTIVE'
  name: string | null
  reason: 'RETIRED' | 'NOT_WORKED' | null
}
```
Mirrors the existing `DayHoursEntry` discriminator shape (`hasRow`/`dayOffType`/`effectiveHours`).

**Why NOT to reuse the neighbouring hours `<input>` + `<datalist>` pattern (D-17):** that pattern
exists specifically to work around G-13-DD (a seeded input collapses the datalist to its
self-matching option, forcing the open-empty-with-placeholder behaviour and a `cellDirtyRef` guard).
D-17 locks a plain `<select>` instead — no dirty-tracking guard needed, commit fires on native
`onChange`, since a closed option list has no equivalent quirk.

**Styling tokens (from UI-SPEC.md, exact values to copy):**
- Tile line: `fontSize: 11px`, `marginTop: 4px`, `cursor: pointer`, truncation
  `overflow: hidden; textOverflow: ellipsis; whiteSpace: nowrap; maxWidth: 90px` + native `title` attr.
- State A (never set): `–`, color `#d1d5db`, weight 400, not italic.
- State B (live): `{name}`, color `#3b82f6` (accent), weight 600.
- State C (stored inactive): `{name} · retired` / `{name} · not worked`, color `#9ca3af`, weight 400, italic.
- `<select>`: `width: 90px`, `fontSize: 11px`, `autoFocus` (mirrors hours `<input>`'s `autoFocus` at `DeskAgents.tsx:616`), `disabled` while request in flight.
- Error (D-03 400): amber `#92400e`, identical placement to the existing hours-cell error at `DeskAgents.tsx:637`.

---

### `frontend/src/api/client.ts` (service/API client, request-response)

**Analog:** same file, `deskAgents.setDayHours` (verified live this session, exact text):
```ts
setDayHours: (deskId: string, agentId: string, day: string, body: { hours?: number; dayOffType?: 'MANDATORY' | 'PTO'; clearRow?: boolean }) =>
  request<DeskAgent>(`/desks/${deskId}/agents/${agentId}/day-hours/${day}`, { method: 'PUT', body: JSON.stringify(body) }),
```
**New method:**
```ts
setUsualShift: (deskId: string, agentId: string, day: string, body: { shiftTemplateId?: string | null; clearRow?: boolean }) =>
  request<DeskAgent>(`/desks/${deskId}/agents/${agentId}/usual-shift/${day}`, { method: 'PUT', body: JSON.stringify(body) }),
```

**Picker data source — no new endpoint needed** `[VERIFIED: frontend/src/api/client.ts:193-194,330]`:
```ts
export const shiftTemplates = {
  list: (deskId: string) => request<ShiftTemplate[]>(`/desks/${deskId}/shift-templates`),
}
export interface ShiftTemplate { id: string; name: string; startTime: string; endTime: string;
  bands: ShiftTemplateBreakBand[]; validWeekdays: string[]; effectiveFrom: string;
  effectiveTo: string | null; eraStatus: 'CURRENT' | 'UPCOMING' | 'PAST' }
```
Filter client-side: `shiftTemplates.list(deskId).filter(t => t.eraStatus === 'CURRENT' && t.validWeekdays.includes(DAY_ORDER[i]))`, alphabetical ascending, per UI-SPEC Component Spec §2.

---

### Test analogs

**`DeskAgentServiceUsualShiftTest.java`** — mirror `DeskAgentServiceDayHoursTest.java`'s
`@DataJpaTest` + `@Import` setup style (setup section verified this session per RESEARCH.md sources
list). Covers USHF-01, USHF-03, USHF-04, USHF-06.

**`AgentUsualShiftPostgresTest.java`** — extend `PostgresBackedTest`
(`src/test/java/com/wfm/support/PostgresBackedTest.java`, full file verified this session):
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// + @Testcontainers, @Container static PostgreSQLContainer, @DynamicPropertySource overriding
//   spring.flyway.enabled=true and ddl-auto=validate — see full class for the container wiring.
```
Only current subclass confirmed: `src/test/java/com/wfm/repository/AgentRepositoryPostgresTest.java`
— use it as the second reference for the exact subclassing shape (constructor/field wiring), since
`PostgresBackedTest` itself is abstract infrastructure, not a runnable example.
**Docker confirmed available** `[VERIFIED: docker info succeeded this session per RESEARCH.md]`.

---

## Shared Patterns

### Tenant+desk IDOR guard (T-13-05)
**Source:** `DeskAgentService.setDayHours` line ~296-299, `findByIdAndTenantIdAndDeskId`
**Apply to:** `UsualShiftService.setUsualShift` — resolve the agent within tenant+desk scope BEFORE
any `AgentUsualShiftRepository` call. Also apply to `shiftTemplateId` validation:
`shiftTemplateRepository.findByIdAndTenantIdAndDeskId(...)` `[VERIFIED: ShiftTemplateRepository.java:17]`
before accepting a write, guarding the cross-tenant-template-reference threat.

### Reject-not-clamp validation
**Source:** `DeskAgentService.setDayHours` line 311 (`throw new IllegalArgumentException("Hours must be between 0 and 24")`)
**Apply to:** `UsualShiftService.setUsualShift`'s weekday-mask check (D-03) — throw
`IllegalArgumentException`, do not silently pick a different day or a nearest-valid template.

### Reuse-or-create upsert helper
**Source:** `DeskAgentService.upsertDayHoursRow`, lines 335-344
**Apply to:** `UsualShiftService`'s row upsert — `findByAgent_IdAndDayOfWeek(...).orElseGet(::new)`, set fields, `save`.

### Explicit `flush()` + re-read
**Source:** `DeskAgentService.setDayHours` lines 427-431
**Apply to:** `UsualShiftService.setUsualShift`'s response construction — flush before re-reading so the DTO reflects the write.

### One layout definition, three (now four) consumers
**Source:** `EnrichedColumnLayout.DAY_ORDER`/`dayHeader`
**Apply to:** every file touching the seven Usual Shift columns (parser, template generator, export) — add `usualShiftHeader(DayOfWeek)` to `EnrichedColumnLayout` and never hardcode the string elsewhere (I-4 drift class, closed once already).

### One implementation, multiple callers
**Source:** CONTEXT D-08 discipline; RESEARCH.md's own named cautionary example (`resolvePreferences` duplicated twice)
**Apply to:** `UsualShiftResolutionService` (roster GET, export, future solver) and the new `clearUsualShifts` helper (`clearDesk`, `removeDeskAgent`).

### GlobalExceptionHandler mapping (no new code needed)
**Source:** `GlobalExceptionHandler.java:32-33`, `@ExceptionHandler(IllegalArgumentException.class)` → 400
**Apply to:** D-03's weekday-mask rejection — reuse as-is.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `src/test/java/com/wfm/service/UsualShiftWritePathGuardTest.java` (D-14 structural completeness guard) | test | event-driven | No exact precedent. Two imperfect analogs exist, both verified this session: (1) `ScheduleConstraintClassificationTest` — reflects over a SINGLE already-known class's members (`ConstraintWeights` fields / `ScheduleConstraintProvider` methods) and asserts the derived set equals a hand-maintained set; mechanically this only works because the domain is bounded to one class, so it cannot detect a brand-new class anywhere in `src/main/java` gaining a dependency. (2) `DeskAssignmentUploadMultiSheetTest`'s D-16 guard (`[VERIFIED: lines 276-280]`: `Arrays.stream(DeskAssignmentUploadService.class.getDeclaredFields()).anyMatch(f -> AgentDayOffRepository.class.isAssignableFrom(f.getType()))` asserted `.isFalse()`) — proves the ABSENCE of a dependency in ONE named class; says nothing about any OTHER class gaining it. ArchUnit is confirmed absent (`grep -c archunit build.gradle` → 0 hits). RESEARCH.md Pitfall 3 recommends a static source-scan test (`Files.walk` over `src/main/java`, assert the set of files containing the literal string `AgentUsualShiftRepository` equals an explicit allowlist, `containsExactly`-style — never a subset/containment check, per `ScheduleConstraintClassificationTest`'s own explicit warning against that) as lower-risk than a `@SpringBootTest` bean-scan (only 3 existing `@SpringBootTest` classes in the codebase, no bean-scan precedent). Planner should pick one of RESEARCH.md's two documented options explicitly. |

## Metadata

**Analog search scope:** `src/main/java/com/wfm/{model,repository,service,controller,dto,util}`,
`src/main/resources/db/migration`, `src/test/java/com/wfm/{service,support,repository}`,
`frontend/src/{pages,api}`
**Files scanned this session:** ~20 (all previously identified by CONTEXT.md/RESEARCH.md; this
session re-verified each citation against the live file rather than trusting the citation alone)
**Pattern extraction date:** 2026-09-03
