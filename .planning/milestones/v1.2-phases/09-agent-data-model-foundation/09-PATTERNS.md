# Phase 9: Agent Data Model Foundation - Pattern Map

**Mapped:** 2026-07-30
**Files analyzed:** 13 (3 new source, 1 new migration, 2 new test classes bundled as 1 row, 7 modified)
**Analogs found:** 13 / 13 (all files have at least a role-match analog; no "No Analog Found" section needed)

**Live-code verification notes (differs slightly from RESEARCH.md, confirm before planning):**
- Latest Flyway migration is confirmed **`V28__add_agent_working_days_known.sql`** — new migration is **`V29`**.
- No `pgcrypto` / `uuid-ossp` extension is enabled anywhere in `db/migration/` (only `V24` enables `vector`, guarded by a privilege-check `DO` block). However `infra/rds.tf:33` pins `engine_version = "16.6"` — PostgreSQL 13+ ships `gen_random_uuid()` as a **built-in core function**, no extension required. RESEARCH.md's Assumption A1 is resolved: the migration sketch's `gen_random_uuid()` call is safe to use as-is on this RDS instance.
- No prior migration does an `INSERT` with app-relevant generated UUIDs at bulk scale except `V15` (which only copies existing FKs, no new UUIDs). `V29`'s fan-out INSERT is the first of its kind — use `gen_random_uuid()` per the above.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/java/com/wfm/model/AgentDayHours.java` | model (child entity) | CRUD | `src/main/java/com/wfm/model/AgentDayOff.java` (structure) + `AgentPreference.java` (DayOfWeek enum column) | exact (composite) |
| `src/main/java/com/wfm/repository/AgentDayHoursRepository.java` | repository | CRUD | `src/main/java/com/wfm/repository/AgentDayOffRepository.java` / `AgentExceptionRepository.java` | exact |
| `src/main/java/com/wfm/util/AgentNameSplitter.java` | utility | transform | `src/main/java/com/wfm/util/BigDecimals.java` (only existing standalone static utility) | role-match (structural template only; no split-logic analog exists) |
| `src/main/java/com/wfm/model/Agent.java` (MODIFY) | model | CRUD | itself — mirror existing scalar-field getter/setter pairs | exact |
| `src/main/java/com/wfm/service/SolverService.java` (MODIFY) | service | transform / batch (pre-solve problem-fact assembly) | itself — `buildAgentDaysOffMap` (static-extraction pattern) + `getEffectiveHours` (resolution logic being replaced) | exact |
| `src/main/java/com/wfm/service/DeskAgentService.java` (MODIFY) | service | CRUD | itself — `setContractedHours` (lines 179-193) | exact |
| `src/main/java/com/wfm/integration/BambooRefreshService.java` (MODIFY) | service (event-driven sync) | CRUD | itself — line 211 `setName` call site | exact |
| `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` (MODIFY) | service (file I/O + CRUD) | batch / CRUD | itself — lines 276, 301 `setName` sites, line 360 `setContractedHoursPerDay(null)` clear site | exact |
| `src/main/java/com/wfm/dto/AgentResponse.java`, `DeskAgentResponse.java` (MODIFY) | model/DTO | request-response | themselves — existing record field lists | exact |
| `src/main/java/com/wfm/service/DeskAgentExportService.java` (MODIFY) | service (file I/O export) | batch/transform | itself — `agent.name()` cell-write site (line 41) | exact |
| `src/main/resources/db/migration/V29__*.sql` | migration | batch | `V15__merge_desk_agent_into_agent.sql` (multi-step ALTER+UPDATE+CREATE TABLE+INSERT script) and `V28__add_agent_working_days_known.sql` (single ALTER + comment style) | exact (composite) |
| `AgentDayHoursPersistenceTest` / `AgentNamePersistenceTest` (NEW test) | test | CRUD | `src/test/java/com/wfm/model/AgentEmploymentTypePersistenceTest.java` | exact |
| `SolverServiceEffectiveHoursResolutionTest` (NEW test) | test | transform | `src/test/java/com/wfm/service/SolverServicePtoFilterTest.java` | exact |

## Pattern Assignments

### `src/main/java/com/wfm/model/AgentDayHours.java` (model, CRUD)

**Analogs:** `src/main/java/com/wfm/model/AgentDayOff.java` (full file, structure/imports/uniqueConstraint) + `src/main/java/com/wfm/model/AgentPreference.java` lines 27-29 (DayOfWeek enum column convention)

**Imports pattern** (`AgentDayOff.java` lines 1-5):
```java
package com.wfm.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
```
(swap `LocalDate` for `DayOfWeek` + add `BigDecimal`)

**Entity/table pattern** (`AgentDayOff.java` lines 7-22, verified live):
```java
@Entity
@Table(name = "agent_day_off", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"agent_id", "date"})
})
public class AgentDayOff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;
```

**DayOfWeek enum column pattern** (`AgentPreference.java` lines 27-29, verified live):
```java
@Enumerated(EnumType.STRING)
@Column(name = "day_of_week", nullable = false)
private DayOfWeek dayOfWeek;
```

**Hours BigDecimal column pattern** (`Agent.java` line 68, verified live — the scale/precision convention to mirror on `AgentDayHours.hours`):
```java
@Column(name = "contracted_hours_per_day", precision = 5, scale = 2)
private BigDecimal contractedHoursPerDay;
```
Note: on `AgentDayHours`, `hours` must be `nullable = false` (unlike the scalar above) — "absent" is represented by row absence (D-09), not by a nullable column, so every row that exists must carry a real, non-null value (mirrors `AgentException.contractedHoursOverride`'s `nullable = false` pattern, `AgentException.java` line 31).

**Getters/setters pattern:** copy `AgentDayOff.java` lines 37-53 verbatim, renaming fields/types (`date`→`dayOfWeek`, `type`/`status`→`hours`).

**Full target shape** (already sketched and verified structurally consistent by RESEARCH.md against `AgentDayOff.java`):
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
    // ... constructor + getters/setters mirroring AgentDayOff.java
}
```

**Anti-pattern to avoid:** Do NOT add a `@OneToMany List<AgentDayHours> dayHours` field to `Agent.java`. Verified: `Agent.java` (full file read, 125 lines) has zero `@OneToMany` collections back-referencing sibling child tables — `AgentDayOff`, `AgentException`, `AgentPreference` are all loaded independently via repository + folded into `Map<UUID, ...>` in `SolverService`. `AgentDayHours` must follow the identical pattern.

---

### `src/main/java/com/wfm/repository/AgentDayHoursRepository.java` (repository, CRUD)

**Analog:** `src/main/java/com/wfm/repository/AgentDayOffRepository.java` (full file, 61 lines) and `AgentExceptionRepository.java` (full file, 30 lines — simpler, closer size match)

**Imports pattern** (`AgentExceptionRepository.java` lines 1-10):
```java
package com.wfm.repository;

import com.wfm.model.AgentException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
```

**Core CRUD/query pattern** (`AgentExceptionRepository.java` lines 12-29, verified live — the closest-shaped analog: tenant-scoped, agent-scoped, desk-scoped finder + bulk-delete):
```java
@Repository
public interface AgentExceptionRepository extends JpaRepository<AgentException, UUID> {

    List<AgentException> findByTenantIdAndDeskIdAndAgent_Id(long tenantId, UUID deskId, UUID agentId);

    List<AgentException> findByTenantIdAndDeskIdAndDateBetween(
            long tenantId, UUID deskId, LocalDate from, LocalDate to);

    void deleteByTenantIdAndDeskIdAndAgent_Id(long tenantId, UUID deskId, UUID agentId);

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);
}
```

**Recommended shape for `AgentDayHoursRepository`** (adapt query names — `AgentDayHours` has no `desk_id` column of its own; desk scoping goes through `agent.deskId`, matching `AgentDayOffRepository.java` lines 44-48's join style):
```java
@Repository
public interface AgentDayHoursRepository extends JpaRepository<AgentDayHours, UUID> {

    List<AgentDayHours> findByTenantIdAndAgent_Id(long tenantId, UUID agentId);

    // Bulk fetch for SolverService — mirrors AgentDayOffRepository.findByTenantIdAndDeskIdAndDateBetween style
    @Query("SELECT h FROM AgentDayHours h WHERE h.tenantId = :tenantId AND h.agent.deskId = :deskId")
    List<AgentDayHours> findByTenantIdAndDeskId(long tenantId, UUID deskId);

    void deleteByAgent_Id(UUID agentId); // for DeskAgentService fan-out replace + DeskAssignmentUploadService clear
}
```
**Source of the `@Query` join-through-agent pattern:** `AgentDayOffRepository.java` lines 44-48 (verified live):
```java
@EntityGraph(attributePaths = {"agent"})
@Query("SELECT d FROM AgentDayOff d WHERE d.tenantId = :tenantId " +
       "AND d.agent.deskId = :deskId AND d.date BETWEEN :from AND :to " +
       "ORDER BY d.date, d.id")
List<AgentDayOff> findByTenantIdAndDeskIdAndDateBetween(long tenantId, UUID deskId, LocalDate from, LocalDate to);
```

---

### `src/main/java/com/wfm/util/AgentNameSplitter.java` (utility, transform)

**Analog:** `src/main/java/com/wfm/util/BigDecimals.java` (full file, 21 lines) — the only existing standalone stateless utility class in the project; use it purely as the **structural** template (private constructor, `final` class, static method, null-handling). No existing split-logic analog exists in the codebase — this is genuinely new logic, per D-06.

**Structural pattern to copy** (`BigDecimals.java` lines 1-20, verified live in full):
```java
package com.wfm.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BigDecimals {

    private BigDecimals() {}

    /**
     * Normalizes a BigDecimal to scale 2 with HALF_UP rounding.
     * Returns null if the input is null.
     */
    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
```

**Recommended shape for `AgentNameSplitter`** (implements D-06's rule — first whitespace token → firstName, remainder → lastName):
```java
package com.wfm.util;

public final class AgentNameSplitter {

    private AgentNameSplitter() {}

    public record Split(String firstName, String lastName) {}

    public static Split split(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return new Split("", "");
        }
        String trimmed = displayName.trim();
        int firstSpace = trimmed.indexOf(' ');
        if (firstSpace < 0) {
            return new Split(trimmed, "");
        }
        String first = trimmed.substring(0, firstSpace);
        String rest = trimmed.substring(firstSpace + 1).trim();
        return new Split(first, rest);
    }
}
```
Call sites to update using this utility: `BambooRefreshService.java:211`, `DeskAssignmentUploadService.java:276`, `DeskAssignmentUploadService.java:301` (see below).

---

### `src/main/java/com/wfm/model/Agent.java` (MODIFY — model, CRUD)

**Analog:** itself. Add `firstName`/`lastName` as plain `String` scalar columns, following the exact style of the existing `name`/`email`/`department` fields.

**Existing scalar-field pattern to mirror** (`Agent.java` lines 28-33, 82-89, verified live):
```java
@Column(nullable = false)
private String name;

private String email;

private String department;
...
public String getName() { return name; }
public void setName(String name) { this.name = name; }

public String getEmail() { return email; }
public void setEmail(String email) { this.email = email; }
```

**Recommended insertion point:** immediately after `name` (line 29) — add:
```java
@Column(name = "first_name")
private String firstName;

@Column(name = "last_name")
private String lastName;
```
with getters/setters inserted after `getName()`/`setName()` (after line 83), matching the exact 2-line-per-accessor style used throughout the file.

**Do NOT touch:** `contractedHoursPerDay` (line 68-69) stays as-is — the scalar remains in the schema (still read/written by `DeskAgentService`/`DeskAssignmentUploadService`) but is no longer consulted by `SolverService`'s resolution chain post-migration. Do not add a `@OneToMany` collection (see anti-pattern note above).

---

### `src/main/java/com/wfm/service/SolverService.java` (MODIFY — service, transform/batch)

**Analog:** itself — two co-located patterns to mirror.

**Pattern A — Map-building from a bulk-loaded child-table list** (`SolverService.java` lines 171-175, verified live, this is the *exact* shape to replicate for `agentDayHoursMap`):
```java
Map<UUID, Map<LocalDate, BigDecimal>> agentExceptionMap = new HashMap<>();
for (AgentException ex : exceptions) {
    agentExceptionMap.computeIfAbsent(ex.getAgent().getId(), k -> new HashMap<>())
            .put(ex.getDate(), ex.getContractedHoursOverride());
}
```
New code (per RESEARCH.md Pattern 1, confirmed against live style):
```java
Map<UUID, Map<DayOfWeek, BigDecimal>> agentDayHoursMap = new HashMap<>();
for (AgentDayHours h : agentDayHours) {
    agentDayHoursMap.computeIfAbsent(h.getAgent().getId(), k -> new HashMap<>())
            .put(h.getDayOfWeek(), h.getHours());
}
```
This map must be built **twice** — once inside `runPreSolveValidation` (which builds its own local copies of `agentDaysOffMap`/`agentExceptionMap` at lines 620-628) and once in the main `solve()` flow (lines 171-175) before `computeAgentDayConfigs` — mirroring exactly how `agentExceptionMap` is independently duplicated today. **Confirmed 3 call sites of `getEffectiveHours` in live code:** line 487 (`computeAgentDayConfigs`), line 651 and line 733 (both inside `runPreSolveValidation`, verified at those exact line numbers).

**Pattern B — Package-private static extraction for unit-testability** (`SolverServicePtoFilterTest.java` lines 1-24 doc-comment, verified live — the established precedent this phase's `resolveEffectiveHours` refactor must follow):
```java
// SolverServicePtoFilterTest.java calls the package-private static helper
// SolverService.buildAgentDaysOffMap(List<AgentDayOff>) directly, extracted
// from the original for-loop so it can be unit-tested without Spring
// context or reflection tricks.
```
Current inline logic being replaced (`SolverService.java` lines 801-810, verified live):
```java
private BigDecimal getEffectiveHours(Agent agent, LocalDate date,
                                     Map<LocalDate, BigDecimal> exceptionMap,
                                     Schedule schedule) {
    if (exceptionMap.containsKey(date)) {
        return exceptionMap.get(date);
    }
    return agent.getContractedHoursPerDay() != null
            ? agent.getContractedHoursPerDay()
            : schedule.getDefaultContractedHoursPerDay();
}
```
Recommended extraction (implements D-03/D-04 precedence, scalar no longer consulted):
```java
static BigDecimal resolveEffectiveHours(Map<LocalDate, BigDecimal> exceptionMap,
                                         Map<DayOfWeek, BigDecimal> dayHoursMap,
                                         LocalDate date,
                                         BigDecimal scheduleDefaultHours) {
    if (exceptionMap.containsKey(date)) {
        return exceptionMap.get(date);
    }
    DayOfWeek dow = date.getDayOfWeek();
    if (dayHoursMap.containsKey(dow)) {
        return dayHoursMap.get(dow);
    }
    return scheduleDefaultHours;
}
```
Note (Pitfall 6, verified against `AgentException.java` line 31 `nullable = false`): keep `containsKey`-based precedence checks, never null-checks — a present map entry is always a real value, absence is signalled by the map key itself being missing.

**`compareTo` convention for the "0 means not worked" check** — already used 3x at `SolverService.java:488, 652, 734` (verified live, e.g. line 488):
```java
if (effectiveHours == null || effectiveHours.compareTo(BigDecimal.ZERO) <= 0) continue;
```
Reuse this exact `compareTo` idiom for the new per-day `0.00` check — never `BigDecimal.equals()` (scale-sensitive).

**Call-site signatures to update (3 total, all verified live):**
- `computeAgentDayConfigs(...)` — signature at line 469-473, call to `getEffectiveHours` at line 487.
- `runPreSolveValidation(...)` — signature at line 565-572, calls to `getEffectiveHours` at line 651 (increment-multiple check) and line 733 (coverage-window check). Local maps built at lines 620-628 inside this same method — the new `agentDayHoursMap` must be built there too, from a `List<AgentDayHours>` parameter threaded in alongside the existing `daysOff`/`exceptions`/`preferences` parameters (same pattern as those three).

---

### `src/main/java/com/wfm/service/DeskAgentService.java` (MODIFY — service, CRUD)

**Analog:** itself, `setContractedHours` method (verified live, `DeskAgentService.java` lines 179-193):
```java
@Transactional
public DeskAgentResponse setContractedHours(UUID deskId, UUID agentId, BigDecimal hours) {
    long tenantId = TenantContext.getTenantId();

    BigDecimal deskDefault = deskRepository.findByIdAndTenantId(deskId, tenantId)
            .map(Desk::getDefaultContractedHoursPerDay)
            .orElse(new BigDecimal("8.00"));

    Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
            .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));

    agent.setContractedHoursPerDay(BigDecimals.normalize(hours));
    Agent saved = agentRepository.save(agent);
    return toResponse(saved, deskDefault, List.of());
}
```
**Fan-out change required (D-10):** after `agent.setContractedHoursPerDay(BigDecimals.normalize(hours))`, additionally replace all 7 `AgentDayHours` rows for `agentId` with the same normalized value — delete existing rows via the new repository's `deleteByAgent_Id(agentId)` then insert 7 fresh rows (one per `DayOfWeek` value), mirroring D-01's migration fan-out rule. Use `BigDecimals.normalize(hours)` (already imported/used in this file) for the per-row value, keeping scale/precision consistent with the `AgentDayHours.hours` column.

---

### `src/main/java/com/wfm/integration/BambooRefreshService.java` (MODIFY — service/event-driven sync)

**Analog:** itself, the `setName` call site (verified live, `BambooRefreshService.java` line 211, within the existing-agent refresh loop lines 206-229):
```java
agent.setName(emp.displayName());
agent.setEmail(emp.workEmail());
agent.setDepartment(emp.department());
agent.setJobTitle(emp.jobTitle());
```

**Required change (D-07):** replace/augment line 211 with a split via the new `AgentNameSplitter` utility:
```java
agent.setName(emp.displayName());
AgentNameSplitter.Split split = AgentNameSplitter.split(emp.displayName());
agent.setFirstName(split.firstName());
agent.setLastName(split.lastName());
```
Add `import com.wfm.util.AgentNameSplitter;` to the existing import block (`BambooRefreshService.java` lines 3-22, which already imports `com.wfm.model.*` and `com.wfm.repository.*` via wildcard — a single new explicit import line is consistent with this file's style, e.g. matching the explicit `com.wfm.service.BambooSyncEventService` / `com.wfm.service.JobTitleConfigService` imports at lines 9-10).

**Note:** `name` itself is NOT changed — it keeps being set from `emp.displayName()` directly (Pitfall 3: `name` is a real, queried JPA column via `AgentRepository.findFiltered`'s `LOWER(a.name) LIKE ...` JPQL — do not derive it from parts).

---

### `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` (MODIFY — service, batch/CRUD)

**Analog:** itself, two `setName` sites + one clear site (verified live).

**Site 1 — new-agent creation from BambooHR cache** (`DeskAssignmentUploadService.java` lines 271-282):
```java
if (agent == null) {
    agent = new Agent();
    agent.setTenantId(tenantId);
    agent.setBamboohrId(cached.id());
    agent.setName(cached.displayName());
    agent.setEmail(cached.workEmail());
    ...
}
```
Per D-11, add split at this site: `agent.setName(cached.displayName()); AgentNameSplitter.Split s = AgentNameSplitter.split(cached.displayName()); agent.setFirstName(s.firstName()); agent.setLastName(s.lastName());`

**Site 2 — spreadsheet-supplied name override** (`DeskAssignmentUploadService.java` lines 299-302):
```java
// Update fields from spreadsheet if provided
if (hasName) {
    agent.setName(name.trim());
}
```
Per D-11, add split here too: `if (hasName) { agent.setName(name.trim()); AgentNameSplitter.Split s = AgentNameSplitter.split(name.trim()); agent.setFirstName(s.firstName()); agent.setLastName(s.lastName()); }`

**Site 3 — hours-clearing on desk clear/re-import** (`DeskAssignmentUploadService.java` `clearDesk` method, lines 349-363, verified live):
```java
private void clearDesk(long tenantId, UUID deskId) {
    log.info("Clearing desk {} for tenant {} before spreadsheet re-import", deskId, tenantId);
    agentPreferenceRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
    agentExceptionRepository.deleteByTenantIdAndDeskId(tenantId, deskId);
    List<Agent> deskAgents = agentRepository.findByTenantIdAndDeskId(tenantId, deskId);
    for (Agent agent : deskAgents) {
        agent.setDeskId(null);
        agent.setPrimarySpecialization(null);
        agent.getSecondarySpecializations().clear();
        agent.setContractedHoursPerDay(null);
        agentRepository.save(agent);
    }
}
```
Per D-10, add `agentDayHoursRepository.deleteByAgent_Id(agent.getId());` immediately after `agent.setContractedHoursPerDay(null);` inside this loop — mirrors the existing `agentPreferenceRepository.deleteByTenantIdAndDeskId` / `agentExceptionRepository.deleteByTenantIdAndDeskId` pattern already at the top of this same method (add a new field `agentDayHoursRepository` alongside the existing `agentExceptionRepository`/`agentPreferenceRepository` fields — check constructor/field-injection style at top of class for exact insertion point).

**Imports:** add `import com.wfm.util.AgentNameSplitter;` and `import com.wfm.repository.AgentDayHoursRepository;` to the explicit import block (`DeskAssignmentUploadService.java` lines 3-20, which already lists `com.wfm.repository.AgentExceptionRepository` etc. individually, not via wildcard — follow that explicit style, unlike `BambooRefreshService`).

---

### `src/main/java/com/wfm/dto/AgentResponse.java`, `DeskAgentResponse.java` (MODIFY — DTO, request-response)

**Analog:** themselves — simple Java `record` field-list pattern, no logic.

**`AgentResponse.java` current shape (full file, verified live):**
```java
public record AgentResponse(
        UUID id,
        String name,
        String email,
        String department,
        String jobTitle,
        boolean active,
        OffsetDateTime lastRefreshedAt
) {}
```
Add `firstName`/`lastName` after `name`:
```java
public record AgentResponse(
        UUID id,
        String name,
        String firstName,
        String lastName,
        String email,
        ...
```

**`DeskAgentResponse.java` current shape (full file, verified live, line 11):**
```java
public record DeskAgentResponse(UUID id, UUID deskId, String bamboohrId, String name, String email,
                                String department, String jobTitle, boolean active,
                                OffsetDateTime lastRefreshedAt,
                                SpecSummary primarySpecialization, List<SpecSummary> secondarySpecializations,
                                BigDecimal contractedHoursPerDay, BigDecimal effectiveContractedHoursPerDay,
                                EmploymentType employmentType,
                                int pendingPtoCount,
                                List<LocalDate> pendingPtoDates) {
```
Add `firstName, lastName` after `name` in the field list. **Per D-12: do NOT add per-day `agent_day_hours` fields here** — scope is name fields only.

**Every construction call site of these records** (wherever `new AgentResponse(...)` / `new DeskAgentResponse(...)` appears, e.g. in `AgentService`/`DeskAgentService.toResponse(...)`) must be updated to pass `agent.getFirstName()`, `agent.getLastName()` at the corresponding new positional args — find via grep for `new AgentResponse(` and `new DeskAgentResponse(`.

---

### `src/main/java/com/wfm/service/DeskAgentExportService.java` (MODIFY — service, file I/O export)

**Analog:** itself, the existing `name` cell-write (verified live, line 41):
```java
row.createCell(3).setCellValue(agent.name() != null ? agent.name() : "");
```
Per D-08, this stays as the derived combined name (no logic change needed here since `agent.name()` on the DTO record is unaffected) — **only add new cells for `firstName`/`lastName`** using the identical null-guard style, e.g.:
```java
row.createCell(N).setCellValue(agent.firstName() != null ? agent.firstName() : "");
row.createCell(N+1).setCellValue(agent.lastName() != null ? agent.lastName() : "");
```
Confirm exact column/header indices and the header-row definition (likely a sibling constant list near the top of the file) before inserting — read the file's header-setup section (not yet in this pattern excerpt) to place new columns without shifting existing indices unexpectedly.

---

### `src/main/resources/db/migration/V29__agent_first_last_name_and_day_hours.sql` (NEW — migration, batch)

**Analogs:** `V15__merge_desk_agent_into_agent.sql` (multi-step ALTER+UPDATE+CREATE TABLE+INSERT, closest structural match) and `V28__add_agent_working_days_known.sql` (single-ALTER + explanatory-comment style).

**Multi-step script style** (`V15__merge_desk_agent_into_agent.sql` lines 1-9, 19-24, verified live — the numbered-comment-step convention to follow):
```sql
-- Merge desk_agent fields into agent table, then drop desk_agent.
-- After this migration, agent is the single entity for both tenant-level
-- and desk-level concerns. desk_id IS NULL means the agent is unassigned.

-- 1. Add desk-specific columns to agent
ALTER TABLE agent
    ADD COLUMN desk_id UUID REFERENCES desk(id) ON DELETE SET NULL,
    ADD COLUMN primary_specialization_id UUID REFERENCES specialization(id) ON DELETE SET NULL,
    ADD COLUMN contracted_hours_per_day NUMERIC(5,2);

-- 3. Create agent_secondary_specialization from desk_agent_secondary_specialization
CREATE TABLE agent_secondary_specialization (
    agent_id            UUID NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    specialization_id   UUID NOT NULL REFERENCES specialization(id) ON DELETE CASCADE,
    PRIMARY KEY (agent_id, specialization_id)
);
```

**Explanatory-comment style for a single-purpose column addition** (`V28__add_agent_working_days_known.sql`, full file, verified live):
```sql
ALTER TABLE agent ADD COLUMN working_days_known BOOLEAN NOT NULL DEFAULT TRUE;

-- DEFAULT TRUE is kept permanently (unlike V25 which dropped the default).
-- Agents created before their first BambooHR refresh must not be incorrectly excluded
-- from the solver: the flag stays TRUE until BambooRefreshService explicitly sets it
-- to false for agents with blank/Variable customWorkingdays (D-07).
```

**Recommended `V29` shape** (from RESEARCH.md's sketch, cross-checked against live conventions above — `gen_random_uuid()` confirmed safe per the top-of-file note):
```sql
-- 1. New columns on agent (mirrors V15 step-numbering style)
ALTER TABLE agent
    ADD COLUMN first_name VARCHAR(255),
    ADD COLUMN last_name VARCHAR(255);

-- 2. Backfill name split (D-06: first whitespace token -> first_name, remainder -> last_name)
UPDATE agent
SET first_name = split_part(name, ' ', 1),
    last_name = CASE
        WHEN position(' ' IN name) > 0
        THEN trim(substring(name FROM position(' ' IN name) + 1))
        ELSE ''
    END;

-- 3. New child table (D-09) -- mirrors agent_day_off / agent_exception FK+unique style
CREATE TABLE agent_day_hours (
    id          UUID PRIMARY KEY,
    tenant_id   BIGINT NOT NULL,
    agent_id    UUID NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    day_of_week VARCHAR(9) NOT NULL,
    hours       NUMERIC(5,2) NOT NULL,
    UNIQUE (agent_id, day_of_week)
);

-- 4. Fan out existing non-null scalar to all 7 weekday rows (D-01).
-- gen_random_uuid() is a PostgreSQL 13+ core builtin (confirmed: engine_version 16.6
-- in infra/rds.tf) -- no CREATE EXTENSION needed, unlike V24's pgvector.
INSERT INTO agent_day_hours (id, tenant_id, agent_id, day_of_week, hours)
SELECT gen_random_uuid(), a.tenant_id, a.id, dow.name, a.contracted_hours_per_day
FROM agent a
CROSS JOIN (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'),
                    ('FRIDAY'), ('SATURDAY'), ('SUNDAY')) AS dow(name)
WHERE a.contracted_hours_per_day IS NOT NULL;
-- NULL-scalar agents (D-02) intentionally get zero rows -- no INSERT for them.
```

---

### Tests: `AgentNamePersistenceTest`, `AgentDayHoursPersistenceTest` (NEW — test, CRUD)

**Analog:** `src/test/java/com/wfm/model/AgentEmploymentTypePersistenceTest.java` (full file, 42 lines, verified live):
```java
package com.wfm.model;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Agent.employmentType persists correctly via JPA (V25 migration + entity field).
 * Uses H2 in-memory database with ddl-auto=create-drop (Flyway disabled in test profile).
 */
@DataJpaTest
@ActiveProfiles("test")
class AgentEmploymentTypePersistenceTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void persistPartTime_reloadsAsPartTime() {
        Agent agent = new Agent();
        agent.setTenantId(1L);
        agent.setBamboohrId("B001");
        agent.setName("Alice");
        agent.setEmploymentType(EmploymentType.PART_TIME);

        Agent saved = em.persistFlushFind(agent);

        assertThat(saved.getEmploymentType()).isEqualTo(EmploymentType.PART_TIME);
    }
}
```
Copy this exact `@DataJpaTest` + `TestEntityManager` + `persistFlushFind` shape for both new persistence tests (`firstName`/`lastName` round-trip on `Agent`; `AgentDayHours` STRING-enum + precision/scale round-trip with a `@ManyToOne` agent reference set via `em.persist(agent)` then `em.persistFlushFind(dayHours)`).

**Note:** Confirms Pitfall 4 from RESEARCH.md — this test style runs against H2 with `ddl-auto: create-drop` and `flyway.enabled: false` (`src/test/resources/application-test.yml`), so it validates the JPA mapping only, never the actual `V29__*.sql` migration SQL. Migration data-integrity verification is a separate manual/checkpoint step.

---

### Test: `SolverServiceEffectiveHoursResolutionTest` (NEW — test, transform)

**Analog:** `src/test/java/com/wfm/service/SolverServicePtoFilterTest.java` (lines 1-80 read, full class doc-comment + first 4 tests, verified live):
```java
package com.wfm.service;

import com.wfm.model.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the PTO filter rules for agentDaysOffMap construction in SolverService: ...
 * Tests call the package-private static helper
 * {@code SolverService.buildAgentDaysOffMap(List<AgentDayOff>)} directly,
 * extracted from the original for-loop so it can be unit-tested without
 * Spring context or reflection tricks. ...
 */
class SolverServicePtoFilterTest {

    private static final long TENANT = 1L;
    private static final LocalDate D1 = LocalDate.of(2026, 4, 7);
    ...

    @Test
    void pto_approved_blocksDay() {
        Agent agent = agent("A1");
        AgentDayOff pto = dayOff(agent, D1, DayOffType.PTO, DayOffStatus.APPROVED);

        Map<UUID, Set<LocalDate>> map = SolverService.buildAgentDaysOffMap(List.of(pto));

        assertThat(map).containsKey(agent.getId());
        assertThat(map.get(agent.getId())).containsExactly(D1);
    }
}
```
New test class must call `SolverService.resolveEffectiveHours(...)` directly (same package, no Spring context), covering: exception-precedence-over-day-hours, day-hours-precedence-over-schedule-default, absent-day-falls-back-to-default, present-zero-day-returns-zero (not the schedule default), and the behaviour-equivalence regression case (uniform 7-day map == old scalar-only result for every `DayOfWeek`, pinning Success Criterion 4).

## Shared Patterns

### Tenant scoping (apply to `AgentDayHours` model + repository + all read/write sites)
**Source:** `AgentDayOff.java` line 17-18, `AgentException.java` line 18-19, `AgentPreference.java` line 17-18 — every sibling child entity carries `tenant_id` as a hard, no-exceptions convention.
```java
@Column(name = "tenant_id", nullable = false)
private long tenantId;
```
Apply to: `AgentDayHours.java`, every `AgentDayHoursRepository` query method (all must be tenant-scoped, following `findByTenantIdAndAgent_Id`/`findByTenantIdAndDeskId` naming).

### Child-table-as-Map resolution (not JPA navigation)
**Source:** `SolverService.java` lines 171-175 (exception map build) — the established idiom for every sibling child table (`AgentDayOff` via `buildAgentDaysOffMap`, `AgentException` via inline map build).
```java
Map<UUID, Map<LocalDate, BigDecimal>> agentExceptionMap = new HashMap<>();
for (AgentException ex : exceptions) {
    agentExceptionMap.computeIfAbsent(ex.getAgent().getId(), k -> new HashMap<>())
            .put(ex.getDate(), ex.getContractedHoursOverride());
}
```
Apply to: `SolverService.java` (both the main-flow build and the duplicate build inside `runPreSolveValidation`).

### BigDecimal comparison (never `.equals()` for zero-check)
**Source:** `SolverService.java` lines 488, 652, 734 (verified, all three identical):
```java
if (effectiveHours == null || effectiveHours.compareTo(BigDecimal.ZERO) <= 0) continue;
```
Apply to: the new `resolveEffectiveHours` static method's callers, and any new "is this per-day value zero" check.

### BigDecimal normalization on write
**Source:** `src/main/java/com/wfm/util/BigDecimals.java` (already used at `DeskAgentService.java:190`):
```java
agent.setContractedHoursPerDay(BigDecimals.normalize(hours));
```
Apply to: `DeskAgentService.setContractedHours`'s new per-day fan-out writes (each of the 7 `AgentDayHours.hours` values), and the Flyway migration's backfill (DB-side `NUMERIC(5,2)` column already enforces scale, no Java-side normalize needed there).

### Package-private static extraction for solver-logic unit testing
**Source:** `SolverServicePtoFilterTest.java` class Javadoc (lines 11-23) documenting the `buildAgentDaysOffMap` precedent.
Apply to: the new `resolveEffectiveHours` static method in `SolverService.java` — same rationale (testable without Spring context), same package-private static signature style.

## No Analog Found

None — every file in scope has at least a role-match analog in the live codebase (see table above). The only genuinely novel logic (no existing precedent) is the first-whitespace-token name-split rule (`AgentNameSplitter`), which uses `BigDecimals.java` purely as a *structural* template (private constructor, static method, null-guard), not a logic analog.

## Metadata

**Analog search scope:** `src/main/java/com/wfm/model/`, `src/main/java/com/wfm/repository/`, `src/main/java/com/wfm/service/`, `src/main/java/com/wfm/integration/`, `src/main/java/com/wfm/dto/`, `src/main/java/com/wfm/util/`, `src/main/resources/db/migration/`, `src/test/java/com/wfm/model/`, `src/test/java/com/wfm/service/`, plus `infra/rds.tf` (Postgres version confirmation).
**Files scanned/read in full or by targeted range:** `Agent.java` (full), `AgentDayOff.java` (full), `AgentPreference.java` (full), `AgentException.java` (full), `AgentDayOffRepository.java` (full), `AgentExceptionRepository.java` (full), `BigDecimals.java` (full), `AgentResponse.java` (full), `DeskAgentResponse.java` (full), `AgentEmploymentTypePersistenceTest.java` (full), `SolverServicePtoFilterTest.java` (lines 1-80), `SolverService.java` (lines 130-190, 469-810, targeted), `DeskAgentService.java` (lines 160-194), `BambooRefreshService.java` (lines 190-230, plus grep of full file for import/setName lines), `DeskAssignmentUploadService.java` (lines 255-370, plus grep of full file), `DeskAgentExportService.java` (grep only, name-cell line 41), `V15__merge_desk_agent_into_agent.sql` (full), `V28__add_agent_working_days_known.sql` (full), migration directory listing, `infra/rds.tf` (engine_version grep).
**Pattern extraction date:** 2026-07-30
