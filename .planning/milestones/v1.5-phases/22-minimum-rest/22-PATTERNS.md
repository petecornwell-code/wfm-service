# Phase 22: Minimum Rest - Pattern Map

**Mapped:** 2026-10-03
**Files analyzed:** 19 (new or modified, per CONTEXT.md "Code this phase changes")
**Analogs found:** 19 / 19 (all have a close in-repo precedent; no "no analog" files on this phase)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/resources/db/migration/V55__*.sql` | migration | batch (schema DDL) | `V54__*.sql` (desk `day_start` column + `schedule` snapshot precedent) | exact |
| `src/main/java/com/wfm/model/Desk.java` (+`minimumRestMinutes` column) | model | CRUD | `Desk.dayStart` field (same file, lines 36-37, 61-62) | exact |
| `src/main/java/com/wfm/model/AgentRestWaiver.java` (new) | model | CRUD | `src/main/java/com/wfm/model/AgentException.java` (whole file) | exact |
| `src/main/java/com/wfm/repository/AgentRestWaiverRepository.java` (new) | model/repository | CRUD | `src/main/java/com/wfm/repository/AgentExceptionRepository.java` (whole file) | exact |
| `src/main/java/com/wfm/service/RestWaiverService.java` (new) | service | CRUD | `src/main/java/com/wfm/service/AgentExceptionService.java` (whole file) | exact |
| `src/main/java/com/wfm/controller/*` (waiver endpoints, likely on `AgentExceptionController` or sibling) | controller | request-response | `DeskController.setDayStart` (lines 67-74) for the desk PUT; agent-exception controller for waiver CRUD | exact (desk PUT) / role-match (waiver CRUD — controller not yet located, mirror the exception controller that calls `AgentExceptionService`) |
| `src/main/java/com/wfm/service/DeskService.java` (+`setMinimumRest`) | service | CRUD | `DeskService.setDayStart` (lines 266-325) | exact |
| `src/main/java/com/wfm/model/ConstraintWeights.java` (+`minimumRestWeight`) | model | CRUD (config) | Any existing `@ConstraintWeight`/`@Column` pair, e.g. `unassignedAssignmentWeight` (lines 27-29) | exact |
| `src/main/java/com/wfm/model/Schedule.java` (+`minimumRestMinutes` snapshot, +`agentRestWaivers` `@ProblemFactCollectionProperty`) | model | event-driven (solver problem facts) | `Schedule.agentExceptions` field (`:143`, cited in RESEARCH) for the collection; `Schedule.dayStart` snapshot for the scalar | exact |
| `src/main/java/com/wfm/model/ScheduleConfig.java` (+15th component, +4th delegating constructor) | model | transform | `ScheduleConfig`'s existing delegating constructors (lines 46, 76) and `dayStart` component (line 34) | exact |
| `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — `minimumRestShift` (new) | solver/constraint | event-driven (constraint stream) | `exactlyOneBreak` (lines 304-351) for mode-gate shape; self-join pattern per RESEARCH Pattern 2 | role-match (closest same-arity mode-gated constraint; no existing self-join-across-dates constraint exists yet — see "No close self-join analog" below) |
| `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — `minimumRestSlot` (new) | solver/constraint | event-driven (constraint stream) | `exactlyOneBreak` (lines 304-351) — identical `ifExists(ScheduleConfig.class, filtering(... != SHIFT))` gate | exact (gate) / role-match (span derivation + self-join is new shape) |
| `src/main/java/com/wfm/model/RestWaiverLookup.java` (or similar — D-08 shared predicate, new) | utility | transform | No direct analog; closest structural idiom is `resolveAnchor`/`anchorFor` (lines 62-73) as "one small shared helper two classes call" | role-match |
| `src/test/java/com/wfm/...RestWaiverGuardTest.java` (new, D-08 structural guard) | test | event-driven (build-time guard) | `src/test/resources/bday-join-guard.md` + its reader test; `MidnightTimeArithmeticGuardTest` | exact |
| `src/main/java/com/wfm/service/SolverService.java` — `requireRestFeasibility` (new, REST-03) | service | event-driven (pre-solve validation) | `requireShiftEnvelopeSeatSupply` (lines 1448-1540+) | exact |
| `src/main/java/com/wfm/repository/AgentShiftAssignmentRepository.java` (+date-filtered read) | model/repository | CRUD (batch read) | Existing `findByTenantIdAndDeskIdAndScheduleId` (line 20) — same file, new sibling method | role-match (shape to copy; the date-range predicate itself has no sibling in this file yet) |
| `src/main/java/com/wfm/repository/AgentAssignmentRepository.java` (+date-filtered read) | model/repository | CRUD (batch read) | Existing `findByTenantIdAndDeskIdAndScheduleId` (line 14) — same file | role-match |
| `src/main/java/com/wfm/service/ScheduleOutputService.java` (+`appliedRestWaivers`/`unusedRestWaivers` computation) | service | transform | `buildConstraintViolations`/`buildAcceptedConstraintViolations` split (lines 625, 828) | role-match |
| `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` (+waiver fields) | model (DTO) | transform | `ViolationDetail` record (lines 229-236) — typed-field extension precedent | exact |
| `src/main/java/com/wfm/dto/ScheduleSummary.java` (+waiver counts) | model (DTO) | transform | Existing `dayStart` field + its javadoc (lines 19-22) | exact |
| `frontend/src/pages/DeskManagement.tsx` (+6th column) | component | CRUD (form/table) | Existing day-start column/cell in the same table | exact |
| `frontend/src/pages/AgentExceptions.tsx` (+"Rest Waivers" section) | component | CRUD (form) | The existing Agent Exceptions section in the same file | exact |
| `frontend/src/pages/ScheduleResults.tsx` (+badge + applied/unused tab) | component | request-response (polled render) | Existing constraint-violation tab/section in the same file | role-match |

**No close self-join analog:** no constraint in `ScheduleConstraintProvider.java` currently self-joins an entity stream across adjacent business dates (`date` vs `date.plusDays(1)`). This is genuinely new shape in this codebase — RESEARCH.md's Pattern 2 (verified against `oneAssignmentPerTimeslot`'s own "avoid O(N²)" comment) is the best available guidance, not an existing constraint to copy wholesale. Treat `exactlyOneBreak`'s mode-gate scaffolding as the copyable frame and RESEARCH.md's sketch (quoted below) as the self-join body.

## Pattern Assignments

### `src/main/java/com/wfm/model/Desk.java` (model, CRUD) — add `minimumRestMinutes`

**Analog:** same file, `dayStart` field.

**Field pattern** (lines 36-37, 61-62):
```java
@Column(name = "day_start", nullable = false)
private LocalTime dayStart = LocalTime.MIDNIGHT;
...
public LocalTime getDayStart() { return dayStart; }
public void setDayStart(LocalTime dayStart) { this.dayStart = dayStart; }
```
Copy this exact getter/setter shape for `minimumRestMinutes`, but **do not default it** — per D-04, `NULL` is the unambiguous unset signal (unlike `dayStart`, which defaults to `LocalTime.MIDNIGHT`). Declare as `Integer minimumRestMinutes;` with `@Column(name = "minimum_rest_minutes")` (no `nullable = false`).

---

### `src/main/java/com/wfm/controller/DeskController.java` (controller, request-response) — add `PUT /{deskId}/minimum-rest`

**Analog:** same file, `setDayStart` (lines 67-74).

**Endpoint pattern:**
```java
@PutMapping("/{deskId}/day-start")
public DeskResponse setDayStart(@PathVariable UUID deskId, @RequestBody DayStartRequest request) {
    Desk updated = deskService.setDayStart(deskId, request.dayStart());
    String tilingWarning = deskService.dayStartTilingWarning(deskId, request.dayStart()).orElse(null);
    return toResponse(updated, lockFor(deskId), tilingWarning);
}
```
Mirror exactly for `PUT /{deskId}/minimum-rest` with a new `MinimumRestRequest(Integer minimumRestMinutes)` DTO and `deskService.setMinimumRest(deskId, minutes)`. No tiling-warning equivalent is needed (D-04 names no such concern) — the response can be the bare `toResponse(updated, lockFor(deskId), null)` shape used by other simple setters in this controller.

---

### `src/main/java/com/wfm/service/DeskService.java` (service, CRUD) — add `setMinimumRest`

**Analog:** same file, `setDayStart` (lines 266-325).

**Validation-ordering pattern** (lines 266-286):
```java
public Desk setDayStart(UUID deskId, LocalTime dayStart) {
    if (dayStart == null) {
        throw new IllegalArgumentException("Day start is required");
    }
    // ordering of checks is deliberate — report the right failure, not a downstream symptom
    if (dayStart.getSecond() != 0 || dayStart.getNano() != 0) {
        throw new IllegalArgumentException(
                "Desk day start " + dayStart + " must not carry seconds or sub-second precision");
    }
    int dayStartMinuteOfDay = dayStart.getHour() * 60 + dayStart.getMinute();
    if (dayStartMinuteOfDay % 15 != 0) {
        throw new IllegalArgumentException(/* ... */);
    }
    ...
    desk.setDayStart(dayStart);
    return deskRepository.save(desk);
}
```
For `setMinimumRest`: `null` is a **legal** value (clears the setting — D-04), so the null-check at the top of `setDayStart` must NOT be copied verbatim. Copy only the **range-validation-then-save** shape. Per RESEARCH Discretion Resolution 1, validate `minutes < 0 || minutes >= 1440` and throw `IllegalArgumentException("Minimum rest must be less than 24 hours")` (exact wording already locked by the approved `22-UI-SPEC.md`). Do not add an ACCEPTED-schedule refusal (`setDayStart`'s unconditional lock) — RESEARCH Discretion 3 recommends no refusal/warning on this value at all, including on mode switch.

---

### `src/main/java/com/wfm/model/AgentRestWaiver.java` (new model, CRUD)

**Analog:** `src/main/java/com/wfm/model/AgentException.java` (whole file, reproduced above in full — 58 lines).

**Entity shape to copy nearly verbatim:**
```java
@Entity
@Table(name = "agent_exception", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "desk_id", "agent_id", "date"})
})
public class AgentException {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private long tenantId;
    @Column(name = "desk_id", nullable = false)
    private UUID deskId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;
    @Column(nullable = false)
    private LocalDate date;
    @Column(nullable = false)
    private String reason;
    // ... getters/setters
}
```
For `AgentRestWaiver`: table `agent_rest_waiver`, same four-column unique constraint `(tenant_id, desk_id, agent_id, date)`, same required non-null `reason`. **Do not add a `contracted_hours_override`-shaped column at all** — D-07's entire reasoning is that this table carries no hours value; "waived" is the presence of the row.

---

### `src/main/java/com/wfm/repository/AgentRestWaiverRepository.java` (new, CRUD)

**Analog:** `src/main/java/com/wfm/repository/AgentExceptionRepository.java` (whole file):
```java
public interface AgentExceptionRepository extends JpaRepository<AgentException, UUID> {
    List<AgentException> findByTenantIdAndDeskIdAndAgent_Id(long tenantId, UUID deskId, UUID agentId);
    List<AgentException> findByTenantIdAndDeskIdAndAgent_IdAndDateBetween(
            long tenantId, UUID deskId, UUID agentId, LocalDate from, LocalDate to);
    Optional<AgentException> findByTenantIdAndDeskIdAndAgent_IdAndDate(
            long tenantId, UUID deskId, UUID agentId, LocalDate date);
    List<AgentException> findByTenantIdAndDeskIdAndDateBetween(
            long tenantId, UUID deskId, LocalDate from, LocalDate to);
    void deleteByTenantIdAndDeskIdAndAgent_Id(long tenantId, UUID deskId, UUID agentId);
    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);
}
```
Copy this method set verbatim with `AgentRestWaiver` in place of `AgentException`. The `findByTenantIdAndDeskIdAndDateBetween` (desk-wide, no agent) method is the one the solver's problem-fact population will call to load every waiver for a schedule's period — same shape `SolverService` already uses for `agent_exception` at its own `:484`/`:302`/`:1159` sites (see Shared Patterns below).

---

### `src/main/java/com/wfm/service/RestWaiverService.java` (new, CRUD)

**Analog:** `src/main/java/com/wfm/service/AgentExceptionService.java` (whole file, reproduced above).

**Tenant/desk/agent resolution pattern** (lines 56-58):
```java
Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
        .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));
```
This is the exact three-key lookup RESEARCH's Security Domain section requires `RestWaiverService` reuse rather than a bare `findById` — copy verbatim.

**Required-field validation pattern** (lines 62-68):
```java
if (ex.date() == null) {
    throw new IllegalArgumentException("date is required for each exception");
}
if (ex.contractedHoursOverride() == null) {
    throw new IllegalArgumentException("contractedHoursOverride is required");
}
if (ex.reason() == null || ex.reason().isBlank()) {
    throw new IllegalArgumentException("reason is required");
}
```
For the waiver: keep the `date` and `reason` checks verbatim; drop the `contractedHoursOverride` check entirely (no such field exists on `AgentRestWaiver`).

**Upsert-by-natural-key pattern** (lines 76-91) — copy verbatim with `AgentRestWaiverRepository.findByTenantIdAndDeskIdAndAgent_IdAndDate` in place of the exception repository's equivalent:
```java
Optional<AgentException> existing = agentExceptionRepository
        .findByTenantIdAndDeskIdAndAgent_IdAndDate(tenantId, deskId, agentId, ex.date());
AgentException entity;
if (existing.isPresent()) {
    entity = existing.get();
} else {
    entity = new AgentException();
    entity.setTenantId(tenantId);
    entity.setDeskId(deskId);
    entity.setAgent(agent);
    entity.setDate(ex.date());
}
```

**What NOT to copy:** the day-off coincidence refusal (lines 70-73 of `AgentExceptionService`):
```java
if (!agentDayOffRepository.findByTenantIdAndAgent_IdAndDateBetweenOrderByDateAsc(
        tenantId, agentId, ex.date(), ex.date()).isEmpty()) {
    throw new ConflictException("Agent has a day off on " + ex.date() + "; cannot create exception");
}
```
D-07 explicitly names this refusal as one of the two measured reasons the waiver is a *new* table rather than a widened `agent_exception` row — a rest waiver on a day-off date is harmless (there is no shift to rest from), so this check must be **omitted**, not copied.

---

### `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — `minimumRestSlot` (new constraint, event-driven)

**Analog:** `exactlyOneBreak` (lines 304-351, full method reproduced above) for the mode-gate and anchor-resolution idiom.

**Mode-gate pattern to copy verbatim:**
```java
.join(AgentDayConfig.class,
        equal((daId, date, assignments) -> daId, AgentDayConfig::agentId),
        equal((daId, date, assignments) -> date, AgentDayConfig::date))
.ifExists(ScheduleConfig.class,
        filtering((daId, date, assignments, dayConfig, cfg) ->
                cfg.schedulingMode() != SchedulingMode.SHIFT))
```
**Anchor resolution pattern to copy verbatim:**
```java
DayWindow window = resolveAnchor(dayConfig.dayStart());
```
**NULL-means-no-tuple pattern (REST-04), to add as a filter not present in `exactlyOneBreak` (new for this phase, per RESEARCH's verified `ConstraintWeights` precedent):**
```java
.filter((..., cfg) -> cfg.minimumRestMinutes() != null && gapMinutes < cfg.minimumRestMinutes())
```

**Self-join body (no existing in-repo analog; RESEARCH.md's verified sketch, to be adapted for the SLOT per-agent-day span via `groupBy` per D-02):**
```java
factory.forEach(AgentShiftAssignment.class)
    .filter(sa -> sa.getShiftBandPair() != null)
    .join(AgentShiftAssignment.class,
          Joiners.equal(AgentShiftAssignment::getAgentId),
          Joiners.equal(sa -> sa.getDate().plusDays(1), AgentShiftAssignment::getDate))
    .filter((prev, next) -> next.getShiftBandPair() != null)
    .filter((prev, next) -> !isRestWaived(prev, next, restWaivers))
    .filter((prev, next) -> gapMinutes(prev, next, window) < minimumRestMinutes)
    .penalize(...)
```

**Gap-minutes formula (never raw `Duration.between` — enforced by `MidnightTimeArithmeticGuardTest`):**
```java
int gapMinutes(AgentShiftAssignment prev, AgentShiftAssignment next, DayWindow window) {
    int remainingInPrevDay = DayWindow.MINUTES_PER_DAY - window.anchoredEndMinute(prevEndTime);
    int elapsedIntoNextDay = window.anchoredStartMinute(nextStartTime);
    return remainingInPrevDay + elapsedIntoNextDay;
}
```

---

### `src/main/java/com/wfm/service/SolverService.java` — `requireRestFeasibility` (new, pre-solve validation)

**Analog:** `requireShiftEnvelopeSeatSupply` (lines 1448-1540+, excerpted above in full detail).

**Accumulate-then-throw pattern to copy:**
```java
if (schedulingMode != SchedulingMode.SHIFT || shiftAssignments == null || shiftAssignments.isEmpty()) {
    return;  // structural no-op, not a cheap check
}
...
Map<LocalDate, List<AgentShiftAssignment>> rowsByDate = shiftAssignments.stream()
        .collect(Collectors.groupingBy(AgentShiftAssignment::getDate, LinkedHashMap::new, Collectors.toList()));
List<ErrorDetail> errors = new ArrayList<>();
for (Map.Entry<LocalDate, List<AgentShiftAssignment>> entry : rowsByDate.entrySet()) {
    // per-date structural check; errors.add(new ErrorDetail(...)) on violation
}
// caller: if (!errors.isEmpty()) throw new PreSolveValidationException(msg, errors);
```
`PreSolveValidationException` (whole file, 21 lines) is the exact exception shape to reuse unchanged:
```java
public class PreSolveValidationException extends RuntimeException {
    private final List<ErrorDetail> details;
    public PreSolveValidationException(String message, List<ErrorDetail> details) {
        super(message);
        this.details = details;
    }
    public List<ErrorDetail> getDetails() { return details; }
}
```
**Key divergence from the analog (load-bearing, per D-12):** `requireShiftEnvelopeSeatSupply`'s per-date check is a same-date existence check; `requireRestFeasibility` must additionally reason about the **pre-horizon predecessor** (D-10/D-12) — a cross-date "every (D-1 pair, D pair) combination violates the minimum" claim — and must **exclude waived pairs** via D-08's shared predicate before accumulating an error. Neither exclusion nor the cross-date predicate exists in the analog; both are new logic layered onto the copied accumulate-then-throw scaffold.

---

### `src/main/java/com/wfm/repository/AgentShiftAssignmentRepository.java` / `AgentAssignmentRepository.java` (+date-filtered reads)

**Analog:** same files, existing `findByTenantIdAndDeskIdAndScheduleId` methods:
```java
List<AgentShiftAssignment> findByTenantIdAndDeskIdAndScheduleId(long tenantId, UUID deskId, UUID scheduleId);
List<AgentAssignment> findByTenantIdAndDeskIdAndScheduleId(long tenantId, UUID deskId, UUID scheduleId);
```
Add siblings filtering by `tenantId`, `deskId`, and a date range (not `scheduleId`) — this is D-10's required new query shape, e.g. `findByTenantIdAndDeskIdAndDateBetween(...)`. Copy the parameter-naming and tenant-filtering convention exactly; this project's own `AgentShiftAssignmentRepository` class javadoc states tenant/desk filtering is "the mitigation" for the absence of row-level security — carry that forward on the new method.

---

### `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` (+waiver fields)

**Analog:** `ViolationDetail` record (lines 229-236):
```java
public record ViolationDetail(
        ...,
        LocalDate businessDate,
        LocalDate calendarDate,
        LocalTime startTime,
        LocalTime endTime,
        ...) {}
```
D-13's `appliedRestWaivers`/`unusedRestWaivers` entries should be new typed records following this exact shape — agent, both business dates, both shift instants, measured gap, required gap, recorded reason — extending the same typed-field channel `ViolationDetail` established, never a string-parsed field.

---

### `src/main/java/com/wfm/dto/ScheduleSummary.java` (+waiver counts)

**Analog:** the existing `dayStart` field and its javadoc (lines 19-22):
```java
// OVNT-02/D-15: the anchor the schedule was SOLVED against -- part of the solved
// schedule's identity, not a live desk value, which is why it is read from the schedule
// itself (Schedule.getDayStart()) rather than from the desk. Rides the summary, not only
// the detail response, so the allocation grid can order columns during the fast 2s poll
// without a second fetch for the slower detail payload.
LocalTime dayStart,
```
Add `int appliedRestWaiverCount` / `int unusedRestWaiverCount` (or similar) with a javadoc citing the identical reasoning: D-13 requires counts here specifically because the ~4 MB detail payload must never be polled.

## Shared Patterns

### Tenant/desk-scoped repository filtering (Access Control, ASVS V4)
**Source:** every repository in `src/main/java/com/wfm/repository/` (e.g. `AgentExceptionRepository`, `AgentShiftAssignmentRepository`)
**Apply to:** `AgentRestWaiverRepository`, both new date-filtered reads
```java
List<AgentException> findByTenantIdAndDeskIdAndAgent_Id(long tenantId, UUID deskId, UUID agentId);
```
Every query method takes `tenantId` and `deskId` explicitly and filters on them — there is no row-level security, so this is the mitigation (verified at `AgentShiftAssignmentRepository`'s class javadoc). Never add a bare `findById`.

### Mode-gate via `ifExists(ScheduleConfig.class, filtering(...))`
**Source:** `ScheduleConstraintProvider.exactlyOneBreak` (lines 310-313)
**Apply to:** `minimumRestSlot` (gate `!= SHIFT`); `minimumRestShift` should add the same gate explicitly for symmetry even though `AgentShiftAssignment` is empty-by-construction in SLOT mode (RESEARCH Assumption A1 recommends the explicit gate regardless).
```java
.ifExists(ScheduleConfig.class,
        filtering((..., cfg) -> cfg.schedulingMode() != SchedulingMode.SHIFT))
```

### Single shared anchor helper
**Source:** `ScheduleConstraintProvider.resolveAnchor`/`anchorFor` (lines 62-73)
**Apply to:** both new constraints — never re-derive a `DayWindow` from a second join; call `resolveAnchor(dayConfig.dayStart())` or `anchorFor(sa)`.

### Accumulate-`ErrorDetail`-then-throw-one-exception
**Source:** `SolverService.requireShiftEnvelopeSeatSupply` + `PreSolveValidationException`
**Apply to:** `requireRestFeasibility` (REST-03)

### Never raw `Duration.between`/`.plusMinutes`/`.minusMinutes` outside the allowlist
**Source:** `src/test/resources/midnight-time-arithmetic.md`, `DayWindow.anchoredEndMinute`/`anchoredStartMinute`
**Apply to:** the gap-minutes formula in both new constraints and in `requireRestFeasibility`'s cross-date check — `MidnightTimeArithmeticGuardTest` fails the build on an unlisted raw-arithmetic token.

### Structural guard for "exactly one implementation" (D-08)
**Source:** `src/test/resources/bday-join-guard.md` and its reader test
**Apply to:** the new D-08 rest-waiver-predicate guard test — same parsed-allowlist-or-source-scan shape, asserting the waived-pair check has exactly one implementation across `ScheduleConstraintProvider` (two call sites) and `SolverService` (one call site).

## No Analog Found

None — every file in CONTEXT.md's "Code this phase changes" list has at least a role-match analog in-repo. The one genuinely novel code shape is the cross-business-date self-join inside the two new constraints; no existing constraint performs a `date` vs `date.plusDays(1)` self-join today, so that specific body must be built from RESEARCH.md's verified sketch (Pattern 2/3) rather than copied from a working constraint. Flagged inline above rather than listed as a missing file.

## Metadata

**Analog search scope:** `src/main/java/com/wfm/{model,service,controller,repository,solver,dto,exception}`, `frontend/src/pages`, `src/test/resources`
**Files scanned:** `ScheduleConstraintProvider.java`, `SolverService.java`, `AgentException.java`, `AgentExceptionService.java`, `AgentExceptionRepository.java`, `Desk.java`, `DeskController.java`, `DeskService.java`, `ConstraintWeights.java`, `ScheduleConfig.java`, `PreSolveValidationException.java`, `ScheduleDetailResponse.java`, `ScheduleSummary.java`, `AgentShiftAssignmentRepository.java`, `AgentAssignmentRepository.java`, `AcceptedScheduleDateRepository.java`
**Pattern extraction date:** 2026-10-03
