# Phase 14: Shift Library & Scheduling Mode - Pattern Map

**Mapped:** 2026-08-25
**Files analyzed:** 16 (backend: 10 new/modified, frontend: 2 new/modified, migration: 1, test: 2, docs: 1)
**Analogs found:** 15 / 16

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/java/com/wfm/model/ShiftTemplate.java` | model | CRUD | `src/main/java/com/wfm/model/Specialization.java` | exact (structural sibling, per CONTEXT.md D-11) |
| `src/main/java/com/wfm/model/Desk.java` (+`schedulingMode`) | model | CRUD | itself (existing file, additive column) | exact |
| `src/main/java/com/wfm/model/SchedulingMode.java` | model (enum) | CRUD | `src/main/java/com/wfm/model/DayOffType.java` | exact |
| `src/main/java/com/wfm/repository/ShiftTemplateRepository.java` | model (repository) | CRUD | `src/main/java/com/wfm/repository/SpecializationRepository.java` (referenced; not yet read this session — same package, same shape as `AgentDayHoursRepository`/`StaffingRequirementRepository` read below) | role-match |
| `src/main/java/com/wfm/service/ShiftTemplateService.java` | service | CRUD | `src/main/java/com/wfm/service/SpecializationService.java` | exact |
| `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` (name TBD) | service | request-response (validation) | `SolverService.runPreSolveValidation` (composed with `TimeslotGeneratorService.getLiveBounds`, `StaffingRequirementRepository.findLiveByDeskAndDateRange`, `AgentDayHoursRepository.findByTenantIdAndDeskId`) | role-match, multi-source composition — no single existing analog does all four reads together |
| `src/main/java/com/wfm/controller/ShiftTemplateController.java` | controller | request-response | `src/main/java/com/wfm/controller/SpecializationController.java` | exact |
| `src/main/java/com/wfm/controller/DeskController.java` (+mode-switch endpoint, or sibling controller) | controller | request-response | `SpecializationController.java` (CRUD verb shape) + `BambooRefreshController`-style single-action endpoint (409 idiom) | role-match |
| `src/main/java/com/wfm/dto/ShiftTemplateRequest.java` | model (DTO) | CRUD | `src/main/java/com/wfm/dto/DeskRequest.java` (referenced by RESEARCH.md as the plain-record, no-bean-validation shape; `SpecializationResponse` read below is the confirmed sibling) | role-match |
| `src/main/java/com/wfm/dto/ShiftTemplateResponse.java` | model (DTO) | CRUD | `src/main/java/com/wfm/dto/SpecializationResponse.java` (used directly by `SpecializationController.toResponse`, read below) | exact |
| `src/test/java/com/wfm/service/ShiftTemplateServiceTest.java` | test | CRUD | `src/test/java/com/wfm/service/JobTitleConfigServiceTest.java` | exact (test-shape analog; `Specialization` itself has NO test to copy) |
| `src/test/java/com/wfm/solver/ScheduleConstraintProviderClassificationTest.java` | test | transform (reflective/structural) | none — first-of-its-kind completeness test in this codebase | no analog |
| `src/main/resources/db/migration/V39__add_shift_template_and_scheduling_mode.sql` | migration | batch | `src/main/resources/db/migration/V38__add_consistent_start_weight.sql` (latest applied — confirmed via `ls`, V38 is head, V39 is next) | exact (forward-only convention) |
| `frontend/src/pages/ShiftLibrary.tsx` | component (page) | request-response / CRUD | `frontend/src/pages/Specializations.tsx` | exact (per D-14, structural mirror) |
| `frontend/src/pages/DeskManagement.tsx` (+mode column) | component (page) | request-response (read-only render) | itself (existing file, additive column) — pattern for the column cell copied from `frontend/src/pages/DeskAgents.tsx` inline-render conventions | exact |
| `.planning/phases/14-shift-library-scheduling-mode/XCUT-05-constraint-classification.md` | config (docs) | batch | none — new artifact, mirrors the completeness test's key set | no analog |

## Pattern Assignments

### `src/main/java/com/wfm/model/ShiftTemplate.java` (model, CRUD)

**Analog:** `src/main/java/com/wfm/model/Specialization.java` (full file, read this session)

**Entity shape to copy verbatim:**
```java
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

**Deviation required (per CONTEXT.md D-11):** widen `uniqueConstraints` to
`(tenant_id, desk_id, name, effective_from)`, and add a DB-level check constraint (or app-level
service check, per Pitfall 1's "decide explicitly" spirit) that effective ranges for the same name
never overlap. Add columns: `start_time`, `end_time`, `break_offset_minutes`, `break_duration_minutes`
(D-01), weekday storage (Claude's Discretion — no `@ElementCollection` precedent exists anywhere in
`src/main/java/com/wfm/model/`, verified zero matches this session — pick a `VARCHAR`/`SMALLINT`
encoding, not a first-ever element collection), `effective_from` (non-null), `effective_to` (nullable —
D-10, no `active` boolean).

---

### `src/main/java/com/wfm/model/SchedulingMode.java` (model/enum, CRUD)

**Analog:** `src/main/java/com/wfm/model/DayOffType.java` (full file, read this session)

```java
package com.wfm.model;

public enum DayOffType {
    MANDATORY,
    PTO
}
```

Copy this exact shape for `SchedulingMode { SLOT, SHIFT }`. `Desk.schedulingMode` field addition:
```java
@Enumerated(EnumType.STRING)
@Column(name = "scheduling_mode", nullable = false, length = 5)
private SchedulingMode schedulingMode = SchedulingMode.SLOT;
```
(mirrors `AgentDayHours.dayOfWeek`'s `@Enumerated(EnumType.STRING)` + explicit `length` idiom, read
in RESEARCH.md Pattern 7 — `length = 9` there for `"WEDNESDAY"`, `length = 5` here for `"SHIFT"`.)

**Current `Desk.java`** (full file, read this session — confirms the clean-addition claim: only
id/tenantId/name/description/defaultContractedHoursPerDay exist today, no other field to collide with):
```java
@Entity
@Table(name = "desk", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "name"})
})
public class Desk {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tenant_id", nullable = false) private long tenantId;
    @Column(nullable = false) private String name;
    private String description;
    @Column(name = "default_contracted_hours_per_day", precision = 5, scale = 2)
    private BigDecimal defaultContractedHoursPerDay = new BigDecimal("8.00");
    // plain getters/setters
}
```

---

### `src/main/java/com/wfm/service/ShiftTemplateService.java` (service, CRUD)

**Analog:** `src/main/java/com/wfm/service/SpecializationService.java` (read this session)

**Create pattern (copy verbatim shape):**
```java
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
```
Deviations: uniqueness check becomes `existsByTenantIdAndDeskIdAndNameAndEffectiveFrom` (D-11) plus a
non-overlap check across the same `name`'s other rows; every create/update must also call D-02's grid
check (`TimeslotGeneratorService.getLiveBounds`) and route through the shared coverage validator per
D-08.

**Delete/retire pattern — explicit deviation:** `SpecializationService.deleteSpecialization`'s
delete-time reference-check idiom (`existsBy...` guards across every FK-holding table before deleting)
does **not** apply the same way to `ShiftTemplate`. D-10 routes retirement through `effective_to`, not
a hard delete; per RESEARCH.md Pitfall/Pattern 2, no FK-holding consumer table exists yet this phase
(Phase 15/16 add them), so a delete endpoint — if offered at all — has nothing to check against today.
Use the reference shape only if a hard-delete endpoint is added:
```java
@Transactional
public void deleteSpecialization(UUID deskId, UUID id) {
    long tenantId = TenantContext.getTenantId();
    Specialization spec = specializationRepository.findByIdAndTenantIdAndDeskId(id, tenantId, deskId)
            .orElseThrow(() -> new EntityNotFoundException("Specialization", id));
    if (agentRepository.existsByPrimarySpecialization_Id(id)
            || agentRepository.existsBySecondarySpecializationsContaining(id)) {
        throw new ConflictException("Cannot delete specialization that is assigned to agents");
    }
    specializationRepository.delete(spec);
}
```

---

### `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` (service, request-response)

**No single analog** — this is a composition of four existing read paths plus the existing
`ErrorDetail`/`PreSolveValidationException` shape. Compose from:

**1. Error accumulation + throw (from `SolverService.runPreSolveValidation`, line ~895):**
```java
// Source: src/main/java/com/wfm/service/SolverService.java:895-898
if (!errors.isEmpty()) {
    throw new PreSolveValidationException(
            "Pre-solve validation failed with " + errors.size() + " issue(s)", errors);
}
```
```java
// Source: src/main/java/com/wfm/dto/ErrorResponse.java:7
public record ErrorDetail(String field, String message, String value) {}
```
```java
// Source: src/main/java/com/wfm/exception/PreSolveValidationException.java (full file)
public class PreSolveValidationException extends RuntimeException {
    private final List<ErrorDetail> details;
    public PreSolveValidationException(String message, List<ErrorDetail> details) {
        super(message);
        this.details = details;
    }
    public List<ErrorDetail> getDetails() { return details; }
}
```
Already wired: `GlobalExceptionHandler.handlePreSolveValidation` → 400 `VALIDATION_FAILED` with
`details`, at `src/main/java/com/wfm/controller/GlobalExceptionHandler.java:61-64` — no new
exception type or handler mapping needed (D-08).

**2. Grid check (D-02) — `TimeslotGeneratorService.getLiveBounds(deskId)`:**
```java
// Source: src/main/java/com/wfm/service/TimeslotGeneratorService.java:51-64
public Optional<TimeslotBoundsResponse> getLiveBounds(UUID deskId) {
    Object[] row = timeslotRepository.findLiveBoundsByDeskRaw(TenantContext.getTenantId(), deskId);
    if (row == null || row.length == 0) return Optional.empty();
    ...
    return Optional.of(new TimeslotBoundsResponse(
            periodStart, periodEnd, startTime, endTime, incrementMinutes));
}
```
`Optional.empty()` = no live timeslots for the desk, a real case D-05 forces the validator to
handle ("no staffing demand loaded" refusal). Do NOT store `incrementMinutes` anywhere new — it is
derived, never persisted (anti-pattern flagged in RESEARCH.md, echoes audit NEW-1/I-1).

**3. Live-demand read (D-05) — `StaffingRequirementRepository.findLiveByDeskAndDateRange`:**
```java
// Source: src/main/java/com/wfm/repository/StaffingRequirementRepository.java:57-61
@Query("SELECT sr FROM StaffingRequirement sr JOIN FETCH sr.timeslot t JOIN FETCH sr.specialization s " +
       "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId IS NULL " +
       "AND t.date BETWEEN :from AND :to")
List<StaffingRequirement> findLiveByDeskAndDateRange(
        long tenantId, UUID deskId, LocalDate from, LocalDate to);
```
No unfiltered "all live for desk" variant exists today — plan-time choice: call with the union of all
templates' effective ranges, or add a new unfiltered sibling following the identical
`JOIN FETCH ... scheduleId IS NULL` shape.

**4. Contracted-hours read (D-06/D-07) — `AgentDayHoursRepository.findByTenantIdAndDeskId`:**
```java
// Source: src/main/java/com/wfm/repository/AgentDayHoursRepository.java:26-28
// AgentDayHours has no desk_id column of its own; desk scoping goes through h.agent.deskId.
@Query("SELECT h FROM AgentDayHours h WHERE h.tenantId = :tenantId AND h.agent.deskId = :deskId")
List<AgentDayHours> findByTenantIdAndDeskId(long tenantId, UUID deskId);
```
```java
// Source: src/main/java/com/wfm/model/AgentDayHours.java (full file)
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
}
```
Compare per-weekday (not per-date, D-06's own framing) via `BigDecimals` util (D-07):
```bash
grep -n "class BigDecimals" -A 5 src/main/java/com/wfm/util/BigDecimals.java
```
Use `BigDecimals`'s comparison method, not `.equals()` (scale-sensitive) or inline `.compareTo() != 0`.

---

### `src/main/java/com/wfm/controller/ShiftTemplateController.java` (controller, request-response)

**Analog:** `src/main/java/com/wfm/controller/SpecializationController.java` (full file, read this session)

```java
@RestController
@RequestMapping("/api/v1/desks/{deskId}/specializations")
public class SpecializationController {
    private final SpecializationService specializationService;
    public SpecializationController(SpecializationService specializationService) {
        this.specializationService = specializationService;
    }

    @GetMapping
    public List<SpecializationResponse> listSpecializations(@PathVariable UUID deskId) {
        return specializationService.listSpecializations(deskId).stream()
                .map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<SpecializationResponse> createSpecialization(@PathVariable UUID deskId,
                                                                        @RequestBody Map<String, String> body) {
        Specialization created = specializationService.createSpecialization(deskId, body.get("name"), body.get("color"));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @PutMapping("/{id}")
    public SpecializationResponse updateSpecialization(@PathVariable UUID deskId,
                                                        @PathVariable UUID id,
                                                        @RequestBody Map<String, String> body) {
        return toResponse(specializationService.updateSpecialization(deskId, id, body.get("name"), body.get("color")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSpecialization(@PathVariable UUID deskId, @PathVariable UUID id) {
        specializationService.deleteSpecialization(deskId, id);
        return ResponseEntity.noContent().build();
    }

    private SpecializationResponse toResponse(Specialization spec) {
        return new SpecializationResponse(spec.getId(), spec.getName(), spec.getColor());
    }
}
```
Route path becomes `/api/v1/desks/{deskId}/shift-templates`; `@RequestBody` should be a
`ShiftTemplateRequest` record (not `Map<String,String>` — the request body has typed fields:
times, ints, a date range, a weekday set), matching the plain-record DTO idiom elsewhere
(`DeskRequest`), not `Specialization`'s ad hoc `Map` shortcut.

**Mode-switch endpoint (new, on `DeskController` or a sibling):** request-response with a 409 branch.
See the "409 Conflict" shared pattern below for the two legitimate implementations research flags —
this is a genuine plan-time choice, not settled by this pattern map.

---

### `src/main/java/com/wfm/dto/ShiftTemplateResponse.java` (DTO, CRUD)

**Analog:** `src/main/java/com/wfm/dto/SpecializationResponse.java` (used directly in
`SpecializationController.toResponse`, confirming its shape is a plain record with exactly the
fields the frontend needs — `new SpecializationResponse(spec.getId(), spec.getName(), spec.getColor())`).
Follow the same plain-record, no-bean-validation idiom for `ShiftTemplateResponse` /
`ShiftTemplateRequest` — this codebase's DTOs never carry `@NotBlank`/`@Positive` etc. (confirmed
absent codebase-wide by RESEARCH.md's Anti-Patterns section); manual checks belong in the service layer.

---

### `src/test/java/com/wfm/service/ShiftTemplateServiceTest.java` (test, CRUD)

**Analog:** `src/test/java/com/wfm/service/JobTitleConfigServiceTest.java` (head read this session)

```java
package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.JobTitleConfigResponse;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.JobTitleConfig;
import com.wfm.repository.JobTitleConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(JobTitleConfigService.class)
@ActiveProfiles("test")
class JobTitleConfigServiceTest {

    @Autowired
    private JobTitleConfigService service;

    @Autowired
    private JobTitleConfigRepository repository;

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }
}
```
**Critical: do NOT look for a `SpecializationServiceTest` to mirror — none exists** (confirmed by
RESEARCH.md Pitfall 2: `find src/test -iname "*Specialization*"` returns nothing). This
`JobTitleConfigServiceTest` shape (`@DataJpaTest` + `@Import(Service.class)` + `@ActiveProfiles("test")`
+ H2 + explicit `TenantContext.setTenantId`/`clear()` in `@BeforeEach`/`@AfterEach`) is the actual
test-shape template for `ShiftTemplateServiceTest` and `ShiftLibraryValidationServiceTest`.

---

### `src/main/resources/db/migration/V39__add_shift_template_and_scheduling_mode.sql` (migration, batch)

**Confirmed this session:** `ls src/main/resources/db/migration/ | sort -V | tail -5` shows
`V38__add_consistent_start_weight.sql` as the actual latest-applied file on disk. **V39 is correct**
— matches RESEARCH.md's independent confirmation. Follow Flyway forward-only convention: never edit
or delete an applied migration.

---

### `frontend/src/pages/ShiftLibrary.tsx` (component/page, CRUD + request-response)

**Analog:** `frontend/src/pages/Specializations.tsx` (full file, 131 lines, read this session)

```tsx
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { specializations as specApi, type Specialization, getErrorMessage } from '../api/client'
import { showToast } from '../components/Toast'

export default function Specializations() {
  const { deskId } = useParams<{ deskId: string }>()
  const [specs, setSpecs] = useState<Specialization[]>([])
  const [newName, setNewName] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editName, setEditName] = useState('')

  useEffect(() => {
    if (deskId) specApi.list(deskId).then(setSpecs).catch(err => showToast('error', getErrorMessage(err)))
  }, [deskId])

  const handleCreate = async () => {
    if (!deskId || !newName.trim()) return
    try {
      const created = await specApi.create(deskId, newName, newColor)
      setSpecs([...specs, created])
      setNewName('')
      showToast('success', 'Specialization created')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  const startEdit = (s: Specialization) => {
    setEditingId(s.id)
    setEditName(s.name)
  }

  const handleUpdate = async () => {
    if (!deskId || !editingId || !editName.trim()) return
    try {
      const updated = await specApi.update(deskId, editingId, editName)
      setSpecs(specs.map(s => s.id === editingId ? updated : s))
      setEditingId(null)
      showToast('success', 'Specialization updated')
    } catch (err) {
      showToast('error', getErrorMessage(err))
    }
  }

  return (
    <>
      <h1>Specializations</h1>
      <div style={{ marginBottom: '1rem', display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
        <input placeholder="Specialization name" value={newName} onChange={e => setNewName(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleCreate()} />
        <button className="primary" onClick={handleCreate}>Add</button>
      </div>
      <table>
        <thead><tr><th>Name</th><th>Actions</th></tr></thead>
        <tbody>
          {specs.map(s => (
            <tr key={s.id}>
              <td>
                {editingId === s.id ? (
                  <div style={{ display: 'flex', gap: '0.25rem' }}>
                    <input value={editName} onChange={e => setEditName(e.target.value)}
                      onKeyDown={e => e.key === 'Enter' && handleUpdate()} style={{ width: '200px' }} />
                    <button className="primary" onClick={handleUpdate} style={{ fontSize: '0.8rem', padding: '0.2rem 0.5rem' }}>Save</button>
                    <button onClick={() => setEditingId(null)} style={{ fontSize: '0.8rem', padding: '0.2rem 0.5rem' }}>Cancel</button>
                  </div>
                ) : s.name}
              </td>
              <td style={{ display: 'flex', gap: '0.25rem' }}>
                <button onClick={() => startEdit(s)}>Edit</button>
                <button className="danger" onClick={() => handleDelete(s.id)}>Delete</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
```
(Excerpt trimmed of the color-picker fields, which `ShiftTemplate` has no equivalent of; the
`useParams` → `useEffect` fetch → inline `editingId`-driven row edit → `showToast` error/success
pattern is the exact shape to copy.)

**Deviations per 14-UI-SPEC.md (binding — read this session, not re-derived here):**
- No `button.danger`/"Delete" anywhere — `ShiftTemplate`'s lifecycle end is **Retire**
  (`effective_to`), a plain-styled inline reveal, never a destructive action or `confirm()` dialog
  (UI-SPEC Component Spec §3, Color contract's explicit ruling).
- Additional state beyond `Specializations.tsx`'s: weekday checkboxes (`DAY_LABELS`/`DAY_ORDER` from
  `DeskAgents.tsx`, see below), break-start/duration fields with a live-computed preview line,
  effective-from/effective-to date fields, a Coverage validation panel (UI-SPEC §4), and a Scheduling
  Mode segmented-control toggle (UI-SPEC §5) — none of which `Specializations.tsx` has an equivalent
  for; these are net-new sections on the page, structurally sequenced below the template list per
  D-14/UI-SPEC's three-stacked-sections layout.

**Weekday labels — reuse verbatim, do not re-declare:**
```ts
// Source: frontend/src/pages/DeskAgents.tsx:9,11 (grep-confirmed this session)
const DAY_ORDER = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'] as const
const DAY_LABELS: Record<(typeof DAY_ORDER)[number], string> = { /* Mon…Sun abbreviations */ }
```

---

### `frontend/src/pages/DeskManagement.tsx` (+mode column)

**Analog:** itself, existing file (124 lines per RESEARCH.md) — additive read-only column following
the same plain-text-cell rendering convention already used for its other columns (e.g.
`Default Hours/Day`). Position: after `Default Hours/Day`, before `Actions` (UI-SPEC §6). Render
`Slot`/`Shift` as plain text from `Desk.schedulingMode`, arriving in the existing desk-list GET
response — no independent fetch, never editable from this page.

---

## Shared Patterns

### Multi-tenancy (applies to every new backend file)
**Source:** `TenantContext.getTenantId()` used throughout `SpecializationService`; `TenantFilter` →
`TenantContext` ThreadLocal. Every new entity carries `tenant_id`; isolation is enforced in
application code only (no DB row security). `ShiftTemplate` and `Desk.schedulingMode` reads must
follow this without exception.

### Error shape (applies to controller/service files)
**Source:** `GlobalExceptionHandler` — `{ "error": { "code", "message", "details" } }`. Typed
exception → HTTP status. Reuse `PreSolveValidationException` for the 400 `VALIDATION_FAILED` path
(D-08) rather than inventing a new exception type.

### 409 Conflict idiom — explicit plan-time choice (applies to the mode-switch endpoint, D-13)
Two legitimate options, flagged by RESEARCH.md as requiring a deliberate pick, not a default:

**Option A — `BambooRefreshService`'s literal idiom** (new map + new exception):
```java
// Source: src/main/java/com/wfm/integration/BambooRefreshService.java:45,102-105,152
private final ConcurrentHashMap<UUID, Boolean> refreshInProgress = new ConcurrentHashMap<>();
public void refreshDeskAgents(UUID deskId) {
    if (refreshInProgress.putIfAbsent(deskId, true) != null) {
        throw new RefreshInProgressException("A BambooHR refresh is already in progress for this desk.");
    }
    ...
    } finally { refreshInProgress.remove(deskId); }
}
```
```java
// Source: src/main/java/com/wfm/controller/GlobalExceptionHandler.java:76-79
@ExceptionHandler(RefreshInProgressException.class)
public ResponseEntity<ErrorResponse> handleRefreshInProgress(RefreshInProgressException ex) {
    return buildResponse(HttpStatus.CONFLICT, "REFRESH_IN_PROGRESS", ex.getMessage(), List.of());
}
```

**Option B — lower-cost, reuses existing state** (`InMemoryScheduleStore` + existing `ConflictException`):
```java
// Source: src/main/java/com/wfm/service/InMemoryScheduleStore.java (full file)
public boolean hasDeskSchedule(UUID deskId) { return deskToScheduleIndex.containsKey(deskId); }
public Optional<Schedule> getByDeskId(UUID deskId) {
    UUID scheduleId = deskToScheduleIndex.get(deskId);
    if (scheduleId == null) return Optional.empty();
    return Optional.ofNullable(scheduleMap.get(scheduleId));
}
```
```java
// Source: src/main/java/com/wfm/model/ScheduleStatus.java (full file)
public enum ScheduleStatus { RUNNING, COMPLETED, STOPPED, FAILED, ACCEPTED }
```
Check `inMemoryScheduleStore.getByDeskId(deskId).map(Schedule::getStatus).filter(s -> s == ScheduleStatus.RUNNING)`,
throw the already-existing `ConflictException` (already 409/`CONFLICT`) — zero new map, zero new
exception class, zero new handler mapping.

**Planner must pick one explicitly and record it** — D-13's text names Option A's idiom, but Option B
is lower total surface area; the frontend copy (UI-SPEC's D-13 toast text) works with either, since
both resolve to a 409.

### Accept-time snapshot (context only — not touched by this phase, D-09/MODE-04)
**Source:** `ScheduleService.acceptSchedule`, lines 196-256 — `scheduleId` null (live) vs. set
(snapshot). `ShiftTemplate` rows are never snapshotted in Phase 14 (no `AgentShiftAssignment` exists
yet); MODE-04 holds trivially because the mode switch touches only `Desk.schedulingMode`, never
`Schedule`/`Timeslot`/`StaffingRequirement` rows. Recorded here only because Phase 15 inherits this
exact mechanism for `AgentShiftAssignment`.

### Toast + loading-flag conventions (applies to `ShiftLibrary.tsx`)
**Source:** `Specializations.tsx`'s `showToast('error'|'success', ...)` calls, and the codebase-wide
`assigning`/`exporting`/`uploading` boolean-flag-during-request convention (referenced in UI-SPEC E2 —
apply the same disabling pattern to `Save` during create/update and to both mode-toggle options during
the switch request).

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `src/test/java/com/wfm/solver/ScheduleConstraintProviderClassificationTest.java` | test | transform (reflective) | First-of-its-kind completeness test in this codebase — no existing test asserts a classification-map key set against a live enumeration. Build directly against `ScheduleConstraintProvider.defineConstraints` (19 constraints, verified this session by RESEARCH.md's direct read — do not trust ARCHITECTURE.md's "18" or re-derive from a stale count). |
| `.planning/phases/14-shift-library-scheduling-mode/XCUT-05-constraint-classification.md` | config (docs) | batch | New artifact type for this phase — a human-readable mirror of the completeness test's key set; RESEARCH.md's own draft table (19 rows, 4 flagged `needs-a-shift-variant`, 2 flagged open-question) is the starting content, not an existing file to copy structure from. |

## Metadata

**Analog search scope:** `src/main/java/com/wfm/{model,repository,service,controller,dto,exception}/`,
`src/test/java/com/wfm/service/`, `src/main/resources/db/migration/`, `frontend/src/pages/`
**Files read this session:** `Specialization.java`, `Desk.java`, `DayOffType.java`,
`SpecializationController.java`, `JobTitleConfigServiceTest.java` (head), `Specializations.tsx` (full),
`DeskAgents.tsx` (grep for `DAY_LABELS`/`DAY_ORDER`), migration directory listing (`ls ... | sort -V`).
Additional excerpts (SolverService, PreSolveValidationException, ErrorDetail, GlobalExceptionHandler,
TimeslotGeneratorService, StaffingRequirementRepository, AgentDayHoursRepository, AgentDayHours,
BambooRefreshService, InMemoryScheduleStore, ScheduleStatus, ScheduleService.acceptSchedule,
ScheduleConstraintProvider.defineConstraints) sourced from 14-RESEARCH.md's own this-session verbatim
reads (each independently attributed to file:line in that document) rather than re-read here, per the
no-duplicate-reads rule.
**Pattern extraction date:** 2026-08-25
