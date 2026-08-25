# Phase 13: Per-Day Hours Visibility - Pattern Map

**Mapped:** 2026-08-21
**Files analyzed:** 10 (7 modified backend, 1 modified frontend page, 1 modified frontend API client, 0 net-new files — this phase edits existing files in place per RESEARCH.md's "Recommended Project Structure")
**Analogs found:** 10 / 10 (all in-file — every "analog" is the very file being modified, since RESEARCH.md confirms this phase touches zero new files)

**Note on approach:** Every file in this phase is a *modification* to an existing file, not a new file. There is therefore no external analog to search for — the analog for each file's new code is the file's own existing sibling methods/sections. This PATTERNS.md maps each new addition to the specific existing pattern within (or directly adjacent to) the same file that it must copy.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|---------------|
| `src/main/java/com/wfm/service/DeskAgentService.java` (`toResponse` fix) | service | CRUD (read) | same file, `listDeskAgentResponses` bulk-fetch (`:55-63`) + `SolverService`'s map-building (`SolverService.java:161,249-253`) | exact |
| `src/main/java/com/wfm/service/DeskAgentService.java` (new `setDayHours`) | service | CRUD (write, single-row upsert) | same file, `setContractedHours` (`:183-219`) — copy tenant/agent resolution and validation shape, NOT the delete-all-seven body | role-match (deliberately divergent core) |
| `src/main/java/com/wfm/service/DeskAgentService.java` (`setContractedHours` D-07 warning) | service | CRUD (write) | same method, extend in place | exact |
| `src/main/java/com/wfm/repository/AgentDayHoursRepository.java` (new finder) | repository | CRUD | same file, `deleteByAgent_Id` (`:36`) and `findByTenantIdAndDeskId` (`:19-20`) derived/`@Query` conventions | exact |
| `src/main/java/com/wfm/dto/DeskAgentResponse.java` (new per-weekday field) | model/DTO | transform | same file, existing record field list (`:11-19`) | exact |
| `src/main/java/com/wfm/controller/DeskAgentController.java` (new `PUT .../day-hours/{day}`) | controller | request-response | same file, `setContractedHours` endpoint (`:76-81`) | exact |
| `src/main/java/com/wfm/service/DeskAgentExportService.java` (+7 Mon–Sun columns) | service | file-I/O (Excel write) | same file, existing column-array + per-row `createCell` loop (`:26-60`); header sourcing pattern from `DeskAssignmentTemplateService.buildHeaders` (`:117-125`) | exact |
| `src/main/java/com/wfm/util/EnrichedColumnLayout.java` (+`specialtyHeader`) | utility | transform | same file, `dayHeader(DayOfWeek)` (`:46-49`) | exact |
| `src/main/java/com/wfm/service/DeskAssignmentTemplateService.java` (specialty header sourcing) | service | file-I/O | same file, `buildHeaders()`'s existing `EnrichedColumnLayout.dayHeader(day)` call (`:119-121`) | exact |
| `frontend/src/pages/DeskAgents.tsx` (expandable row, combo cells, D-07 relocation) | component | request-response + local UI state | same file, `editHoursAgentId`/`editHours`/`saveHours` inline-edit triad (`:41-42,148-163,352-365`) for edit-state shape; `showDaysOff` modal (`:444-466`) for styling tokens only (RESEARCH.md's explicit correction: NOT a structural analog) | role-match (structural analog absent; RESEARCH.md verified this) |
| `frontend/src/pages/DeskAgents.tsx` (D-07 amber warning notice) | component | — | `frontend/src/pages/ClientManagement.tsx:590-601` (Upload Results warnings block) | exact |
| `frontend/src/api/client.ts` (new `DeskAgent` fields + new endpoint calls) | api-client | request-response | same file, `deskAgents.setContractedHours` (`:152-153`) and `DeskAgent` interface (`:296`) | exact |
| `src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java` (new) | test | CRUD | `src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java` (full file) | exact |
| `src/test/java/com/wfm/service/DeskAgentExportServiceTest.java` (new) | test | file-I/O | no existing export test file — see "No Analog Found" | none |

## Pattern Assignments

### `src/main/java/com/wfm/service/DeskAgentService.java` — `toResponse` read-path fix (I-1/F-1)

**Analog:** same file's `listDeskAgentResponses` bulk-fetch-then-map convention (`:55-63`), and `SolverService`'s day-hours map-building (`SolverService.java:161,249-253`).

**Existing bulk-fetch + group-by-agent pattern to copy** (`DeskAgentService.java:55-63`):
```java
List<AgentDayOff> pendingPtoRows = agentDayOffRepository
        .findByAgentDeskIdAndTypeAndStatusAndDateGreaterThanEqual(
                deskId, DayOffType.PTO, DayOffStatus.REQUESTED, LocalDate.now());

Map<UUID, List<LocalDate>> pendingByAgent = pendingPtoRows.stream()
        .collect(Collectors.groupingBy(
                d -> d.getAgent().getId(),
                Collectors.mapping(AgentDayOff::getDate, Collectors.toList())));
```
Mirror this shape exactly for `AgentDayHours`, using `agentDayHoursRepository.findByTenantIdAndDeskId(tenantId, deskId)` (already exists, `AgentDayHoursRepository.java:19-20`) and grouping into `Map<UUID, Map<DayOfWeek, AgentDayHours>>` (verified reference shape in RESEARCH.md's Pattern 1, sourced from `SolverService.java:161,249-253`).

**Defect being replaced** (`DeskAgentService.java:73-74`, `:152-154`, `:187-189` — three call sites):
```java
BigDecimal effective = a.getContractedHoursPerDay() != null
        ? a.getContractedHoursPerDay() : deskDefault;
```
This line, and the `deskDefault` resolution feeding it (`deskRepository...map(Desk::getDefaultContractedHoursPerDay)`), must be deleted from all three call sites (`:49-51`, `:152-154`, `:187-189`) and replaced by the schedule-derived default (see D-06 resolution in RESEARCH.md) plus a per-weekday `dayMap.get(day)` read — never a null-or-zero check on `hours` (Pitfall 1/2).

**Resolution logic to copy** (RESEARCH.md Pattern 2 — do NOT call `SolverService.resolveEffectiveHours`, it is date-based; use the simpler weekday read):
```java
BigDecimal resolveWeekdayForDisplay(Map<DayOfWeek, AgentDayHours> dayMap, DayOfWeek day, BigDecimal scheduleDefault) {
    AgentDayHours row = dayMap.get(day);
    return row != null ? row.getHours() : scheduleDefault;   // absent row = "not set" (D-04)
}
```

**Schedule-default lookup** (new, no existing analog — first use of `ScheduleRepository` in this class):
```java
scheduleRepository.findByTenantIdAndDeskIdOrderByCreatedAtDesc(tenantId, deskId, PageRequest.of(0, 1))
```
Falls back to hardcoded `new BigDecimal("8.00")` (matching `Schedule`'s own field default, `Schedule.java:69`) when the list is empty — never `Desk.getDefaultContractedHoursPerDay()` (D-06 anti-pattern).

---

### `src/main/java/com/wfm/service/DeskAgentService.java` — new `setDayHours` (D-05)

**Analog:** `setContractedHours` (`:183-219`) for the tenant/agent resolution and validation shape ONLY. Its body must NOT be copied — that fan-out is exactly the multi-row-write pattern D-05 forbids for this new method.

**Copy this shape** (`DeskAgentService.java:184-198`):
```java
@Transactional
public DeskAgentResponse setContractedHours(UUID deskId, UUID agentId, BigDecimal hours) {
    long tenantId = TenantContext.getTenantId();
    BigDecimal deskDefault = deskRepository.findByIdAndTenantId(deskId, tenantId)
            .map(Desk::getDefaultContractedHoursPerDay)
            .orElse(new BigDecimal("8.00"));
    Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
            .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));
    BigDecimal normalized = BigDecimals.normalize(hours);
    if (normalized != null && normalized.signum() < 0) {
        throw new IllegalArgumentException("Contracted hours per day must not be negative");
    }
    ...
```
The tenant-scoped `findByIdAndTenantIdAndDeskId` resolution is the mandatory security pattern (V4 Access Control) — `findByAgent_IdAndDayOfWeek` (new repository method) must only ever be called with an `agentId` that has already passed through this resolve, never a raw path/body value.

**Do NOT copy** (`DeskAgentService.java:206-217`, the anti-pattern for this method):
```java
agentDayHoursRepository.deleteByAgent_Id(agentId);
agentDayHoursRepository.flush();
if (normalized != null) {
    for (DayOfWeek dayOfWeek : DayOfWeek.values()) { ... }
}
```
Instead use the RESEARCH.md-recommended single-row upsert (verified recommended shape, RESEARCH.md "Code Examples" section) — `findByAgent_IdAndDayOfWeek(...).orElseGet(AgentDayHours::new)`, set fields, `save()`; or `delete()` for the "not set" (`clearRow`) branch. Reuse `BigDecimals.normalize()` (`src/main/java/com/wfm/util/BigDecimals.java:14-19`) exactly as `setContractedHours` does — do not hand-roll a second rounding call.

---

### `src/main/java/com/wfm/service/DeskAgentService.java` — `setContractedHours` D-07 warning extension

**Analog:** the method's own existing negative-check pattern (`:194-197`) — add a pre-check in the same style before the fan-out:
```java
if (normalized != null && normalized.signum() < 0) {
    throw new IllegalArgumentException("Contracted hours per day must not be negative");
}
```
New pre-check (same shape, new condition per Pitfall 4): query the agent's existing 7 rows and check `.anyMatch(row -> row.getDayOffType() != null)` — return this boolean to the caller (e.g. via a response field or a distinct pre-check endpoint) so the frontend can decide whether to show the `confirm()` dialog. Do NOT trigger on "rows exist."

---

### `src/main/java/com/wfm/repository/AgentDayHoursRepository.java` — new finder

**Analog:** same file, existing `deleteByAgent_Id` (`:36`) and `findByTenantIdAndDeskId` (`:19-20`).

**Pattern to copy** (derived-query convention already established in this repository, `:15`, `:36`):
```java
List<AgentDayHours> findByTenantIdAndAgent_Id(long tenantId, UUID agentId);
void deleteByAgent_Id(UUID agentId);
```
New addition (RESEARCH.md "Code Examples", exact recommended signature — matches the existing `Agent_Id` underscore-traversal naming convention verbatim):
```java
Optional<AgentDayHours> findByAgent_IdAndDayOfWeek(UUID agentId, DayOfWeek dayOfWeek);
```
Note this repository's existing methods are **not tenant-scoped by themselves** (comment at `:33-35` explicitly documents this) — callers must resolve tenant scope via `AgentRepository` first, exactly as `setContractedHours` does today (Security Domain / V4 in RESEARCH.md).

---

### `src/main/java/com/wfm/dto/DeskAgentResponse.java` — new per-weekday field

**Analog:** same file's existing record shape (`:11-19`).

**Existing shape to extend, not replace:**
```java
public record DeskAgentResponse(UUID id, UUID deskId, String bamboohrId, String name,
                                String firstName, String lastName, String email,
                                String department, String jobTitle, boolean active,
                                OffsetDateTime lastRefreshedAt,
                                SpecSummary primarySpecialization, List<SpecSummary> secondarySpecializations,
                                BigDecimal contractedHoursPerDay, BigDecimal effectiveContractedHoursPerDay,
                                EmploymentType employmentType,
                                int pendingPtoCount,
                                List<LocalDate> pendingPtoDates) {
    public record SpecSummary(UUID id, String name) {}
}
```
Add a new field following this same nested-record convention (`SpecSummary` is the precedent for a per-item sub-record). RESEARCH.md/UI-SPEC.md's recommended shape (Assumption A3 / "Minimum data shape" table) is a `Map<DayOfWeek, DayHoursEntry>`-like field where `DayHoursEntry` carries `hasRow`, `hours`, `dayOffType`, `effectiveHours` — model it as a new nested record (`DayHoursEntry(boolean hasRow, BigDecimal hours, DayOffType dayOffType, BigDecimal effectiveHours)`) plus a `Map<DayOfWeek, DayHoursEntry> dayHours` field on the outer record, mirroring `SpecSummary`'s nested-record style. **Leave `contractedHoursPerDay()` and `effectiveContractedHoursPerDay()` untouched** — they continue to echo the scalar for backward compat (Pitfall 3) and must not be recomputed from the new per-day map.

---

### `src/main/java/com/wfm/controller/DeskAgentController.java` — new `PUT .../day-hours/{day}`

**Analog:** same file, `setContractedHours` endpoint (`:76-81`).

**Pattern to copy verbatim:**
```java
@PutMapping("/{agentId}/contracted-hours")
public DeskAgentResponse setContractedHours(@PathVariable UUID deskId,
                                             @PathVariable UUID agentId,
                                             @RequestBody SetContractedHoursRequest request) {
    return deskAgentService.setContractedHours(deskId, agentId, request.contractedHoursPerDay());
}
```
New endpoint, same structural shape, new path segment and request DTO (new `SetDayHoursRequest` record, following the existing `dto` package convention — see `SetContractedHoursRequest`/`SetSpecializationsRequest` siblings referenced via the `import com.wfm.dto.*;` wildcard at `:3`):
```java
@PutMapping("/{agentId}/day-hours/{day}")
public DeskAgentResponse setDayHours(@PathVariable UUID deskId,
                                      @PathVariable UUID agentId,
                                      @PathVariable DayOfWeek day,
                                      @RequestBody SetDayHoursRequest request) {
    return deskAgentService.setDayHours(deskId, agentId, day,
            request.hours(), request.dayOffType(), request.clearRow());
}
```
RESEARCH.md's Open Question #1 recommends this as a **second, distinct endpoint** — not a discriminator on the existing one — to keep the "one row" (D-05) and "seven rows" (D-07) operations structurally separate in the API surface, matching their separation in the UI.

---

### `src/main/java/com/wfm/service/DeskAgentExportService.java` — +7 Mon–Sun columns (D-02)

**Analog:** same file's existing column-array + per-row `createCell` loop (`:26-60`).

**Pattern to copy** (header array construction, `:26-33`):
```java
String[] columns = {
    "ID", "Desk ID", EnrichedColumnLayout.COL_BAMBOOHR_ID, "Name", EnrichedColumnLayout.COL_EMAIL,
    EnrichedColumnLayout.COL_DEPARTMENT, EnrichedColumnLayout.COL_JOB_TITLE,
    EnrichedColumnLayout.COL_ACTIVE, "Last Refreshed At",
    "Primary Specialization", "Secondary Specializations",
    "Contracted Hours Per Day", "Effective Contracted Hours Per Day",
    EnrichedColumnLayout.COL_FIRST_NAME, EnrichedColumnLayout.COL_LAST_NAME
};
```
Extend this array with 7 new entries sourced from `EnrichedColumnLayout.dayHeader(day)` for `day` in `EnrichedColumnLayout.DAY_ORDER` — placed immediately after "Effective Contracted Hours Per Day" per UI-SPEC.md's column-order contract. **Do not hardcode `"Monday".."Sunday"`** — this is precisely the I-4 drift class (Don't Hand-Roll table, RESEARCH.md).

**Pattern to copy** (per-row `createCell` convention, `:57-58`):
```java
row.createCell(11).setCellValue(agent.contractedHoursPerDay() != null ? agent.contractedHoursPerDay().doubleValue() : 0);
row.createCell(12).setCellValue(agent.effectiveContractedHoursPerDay() != null ? agent.effectiveContractedHoursPerDay().doubleValue() : 0);
```
New per-day cells must branch on `dayOffType` first (writing `"MANDATORY"`/`"PTO"` string keywords, not `0`) before falling through to the numeric value — mirroring the display-mode branching order in UI-SPEC.md Section 3 (rules 1-5) so the export/re-upload round-trip stays lossless (D-02).

**Formula-injection note:** the new columns are `BigDecimal`/enum-derived, not operator strings — `sanitize()` (`:90-92`) does not apply to them (Don't Hand-Roll table confirms this explicitly).

---

### `src/main/java/com/wfm/util/EnrichedColumnLayout.java` — new `specialtyHeader` factory (D-08/I-4)

**Analog:** same file, `dayHeader(DayOfWeek)` (`:46-49`).

**Pattern to copy verbatim:**
```java
public static String dayHeader(DayOfWeek d) {
    String name = d.name();
    return name.charAt(0) + name.substring(1).toLowerCase();
}
```
New method, same static-factory style (RESEARCH.md's exact recommended addition):
```java
public static String specialtyHeader(int index) {
    return "Specialty " + index;
}
```

---

### `src/main/java/com/wfm/service/DeskAssignmentTemplateService.java` — source specialty headers from `EnrichedColumnLayout` (D-08/I-4)

**Analog:** same file's existing `buildHeaders()` (`:117-125`), which already calls `EnrichedColumnLayout.dayHeader(day)` for the day columns — extend that exact convention to specialty columns.

**Current defect** (`:31-32`, `:122-123`):
```java
private static final String SPECIALTY_1_HEADER = "Specialty 1";
private static final String SPECIALTY_2_HEADER = "Specialty 2";
...
headers.add(SPECIALTY_1_HEADER);
headers.add(SPECIALTY_2_HEADER);
```
Replace both local constants and their two `headers.add(...)` calls with:
```java
headers.add(EnrichedColumnLayout.specialtyHeader(1));
headers.add(EnrichedColumnLayout.specialtyHeader(2));
```
matching the day-header call immediately above it in the same method (`:119-121`):
```java
for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
    headers.add(EnrichedColumnLayout.dayHeader(day));
}
```

---

### `frontend/src/pages/DeskAgents.tsx` — expandable row, per-day combo cells, relocated bulk action

**Analog:** the file's own `editHoursAgentId`/`editHours`/`saveHours` inline-edit triad (`:41-42`, `:148-163`, `:352-365`) for state-management shape. **RESEARCH.md verified there is no existing in-row expandable pattern in this file** — the "Days Off" modal (`:444-466`) is a `position: fixed` overlay, not a structural analog; treat it as a *styling* reference only (rounded card, `#fff` background), per RESEARCH.md's explicit correction to CONTEXT.md's claim.

**Existing per-row edit-state pattern to copy the shape of** (`DeskAgents.tsx:41-42, 148-163`):
```tsx
const [editHoursAgentId, setEditHoursAgentId] = useState<string | null>(null)
const [editHours, setEditHours] = useState(8)

const startEditHours = (da: DeskAgent) => {
  setEditHoursAgentId(da.id)
  setEditHours(da.effectiveContractedHoursPerDay)
}

const saveHours = async () => {
  if (!deskId || !editHoursAgentId) return
  try {
    const updated = await deskAgents.setContractedHours(deskId, editHoursAgentId, editHours)
    setAgentList(agentList.map(da => da.id === editHoursAgentId ? updated : da))
    setEditHoursAgentId(null)
    showToast('success', 'Contracted hours updated')
  } catch (err) {
    showToast('error', getErrorMessage(err))
  }
}
```
UI-SPEC.md's Section 3 recommends the per-cell edit state be shaped as `{ agentId, day } | null` (a single state variable, not one per cell) — directly extending this existing single-nullable-id convention to a compound key.

**Existing inline `<input>` + step convention to copy** (`:355-356`):
```tsx
<input type="number" value={editHours} onChange={e => setEditHours(Number(e.target.value))}
  step="0.25" style={{ width: '60px' }} />
```
The per-cell combo (D-03) replaces this numeric-only input with `<input type="text" list="day-hours-options">` bound to a shared `<datalist>` (UI-SPEC.md Component Spec §3) — same controlled-input wiring pattern, different element.

**Existing click-to-edit span convention to copy** (`:323, :347, :361`):
```tsx
<span onClick={() => startEditHours(da)} style={{ cursor: 'pointer', textDecoration: 'underline dotted' }}>
  {da.effectiveContractedHoursPerDay}
</span>
```
Reuse this exact `cursor: pointer` + `underline dotted` visual affordance for the collapsed `Hours/Day` cell's click-to-expand behavior (UI-SPEC.md Section 1, item 5).

**Existing pending-PTO badge styling to copy for the `PTO`/`MAND` badges** (`:369-386`):
```tsx
<span
  title={da.pendingPtoDates.join(', ')}
  style={{
    display: 'inline-block',
    background: '#fef9c3',
    color: '#92400e',
    padding: '0.25rem 0.5rem',
    borderRadius: '4px',
    fontSize: '0.85rem',
    fontWeight: 400,
    cursor: 'default',
    whiteSpace: 'nowrap',
  }}
>
  {da.pendingPtoCount} pending PTO
</span>
```
This is the exact `#fef9c3`/`#92400e` palette UI-SPEC.md's "PTO badge" role reuses verbatim.

**Existing destructive-confirmation convention to copy** (`DeskAgents.tsx:117`):
```tsx
if (!deskId || !confirm('Remove this agent from the desk?')) return
```
UI-SPEC.md's D-07 warning dialog copy follows this exact `confirm()` pattern, not a custom modal.

---

### `frontend/src/pages/DeskAgents.tsx` — D-07 amber warning notice

**Analog:** `frontend/src/pages/ClientManagement.tsx:590-601` (verified this session in RESEARCH.md).

**Pattern to copy verbatim (palette values load-bearing):**
```tsx
{(uploadResult.warnings.length > 0 || uploadResult.skippedSheets.length > 0) && (
  <div style={{ marginTop: '0.75rem', padding: '0.5rem', background: '#fffbeb', border: '1px solid #fde68a', borderRadius: '4px' }}>
    <div style={{ fontWeight: 600, fontSize: '0.85rem', color: '#92400e', marginBottom: '0.25rem' }}>Warnings</div>
    <ul style={{ fontSize: '0.85rem', color: '#92400e', margin: 0, paddingLeft: '1.25rem' }}>
      {uploadResult.warnings.map((warning, idx) => <li key={`warning-${idx}`}>{warning}</li>)}
    </ul>
  </div>
)}
```
Reuse the `#fffbeb`/`#fde68a`/`#92400e` triad verbatim for the "Set all days to…" pre-submit warning banner (UI-SPEC.md Color table, Warning role).

---

### `frontend/src/api/client.ts` — new `DeskAgent` fields + endpoint calls

**Analog:** same file, `deskAgents.setContractedHours` (`:152-153`) and the `DeskAgent` interface (`:296`).

**Pattern to copy verbatim:**
```ts
setContractedHours: (deskId: string, agentId: string, hours: number) =>
  request<DeskAgent>(`/desks/${deskId}/agents/${agentId}/contracted-hours`, { method: 'PUT', body: JSON.stringify({ contractedHoursPerDay: hours }) }),
```
New addition, same shape:
```ts
setDayHours: (deskId: string, agentId: string, day: string, body: { hours?: number; dayOffType?: 'MANDATORY' | 'PTO'; clearRow?: boolean }) =>
  request<DeskAgent>(`/desks/${deskId}/agents/${agentId}/day-hours/${day}`, { method: 'PUT', body: JSON.stringify(body) }),
```

**Existing interface to extend** (`:296`):
```ts
export interface DeskAgent { id: string; deskId: string; bamboohrId: string; name: string; email: string; department: string; jobTitle: string; active: boolean; lastRefreshedAt: string; primarySpecialization?: Specialization; secondarySpecializations: Specialization[]; contractedHoursPerDay?: number; effectiveContractedHoursPerDay: number; employmentType: 'FULL_TIME' | 'PART_TIME' | null; pendingPtoCount: number; pendingPtoDates: string[] }
```
Add a `dayHours: Record<string, DayHoursEntry>` field (or similarly-shaped) matching whatever field name is chosen on the backend DTO (`DeskAgentResponse`, Claude's discretion) — keep this single-line inline-interface style consistent with every other interface in this file (see `Desk`, `Agent`, `Specialization` immediately above/below, all one-liners).

---

### `src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java` (new)

**Analog:** `src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java` (full file, 135 lines).

**Pattern to copy verbatim** (test-class scaffolding, `:32-74`):
```java
@DataJpaTest
@Import(DeskAgentService.class)
@ActiveProfiles("test")
class DeskAgentServiceContractedHoursTest {

    @Autowired private DeskAgentService deskAgentService;
    @Autowired private AgentRepository agentRepository;
    @Autowired private DeskRepository deskRepository;
    @Autowired private AgentDayHoursRepository agentDayHoursRepository;

    private static final long TENANT_ID = 1L;
    private Desk desk;
    private Agent agent;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        desk = new Desk();
        desk.setTenantId(TENANT_ID);
        desk.setName("Support Desk");
        desk = deskRepository.save(desk);
        agent = new Agent();
        agent.setTenantId(TENANT_ID);
        agent.setBamboohrId("B100");
        agent.setName("Jane Doe");
        agent.setDeskId(desk.getId());
        agent = agentRepository.save(agent);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }
```
New test file needs `@Import` to additionally pull in `ScheduleRepository`-backed setup (RESEARCH.md Pattern 3) to test D-06's schedule-default resolution — create a `Schedule` row with a known `defaultContractedHoursPerDay` and assert the response reflects it, not the desk default.

**Assertion style to copy** (`:76-89`):
```java
@Test
void setContractedHours_writesSevenPerDayRows_normalizedToScaleTwo() {
    deskAgentService.setContractedHours(desk.getId(), agent.getId(), new BigDecimal("6"));
    List<AgentDayHours> rows = agentDayHoursRepository.findByTenantIdAndAgent_Id(TENANT_ID, agent.getId());
    assertThat(rows).hasSize(7);
    ...
}
```
New tests for `setDayHours` must assert exactly ONE row changes per call (D-05's structural property) — e.g. seed 7 rows, call `setDayHours` for one day, assert the other 6 rows are byte-for-byte unchanged (a negative assertion this existing test file has no precedent for, since `setContractedHours` intentionally rewrites all 7).

---

## Shared Patterns

### Tenant-scoped resolution before any per-day repository call (V4 Access Control)
**Source:** `DeskAgentService.java:191` (`agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId).orElseThrow(...)`)
**Apply to:** `setDayHours` (new), any new controller endpoint reading `agentId` from the path.
```java
Agent agent = agentRepository.findByIdAndTenantIdAndDeskId(agentId, tenantId, deskId)
        .orElseThrow(() -> new EntityNotFoundException("Agent not found for desk: " + agentId));
```
`AgentDayHoursRepository`'s new `findByAgent_IdAndDayOfWeek` is NOT itself tenant-scoped (mirrors `deleteByAgent_Id`) — never call it with a client-supplied `agentId` that hasn't passed through this resolve first.

### `BigDecimals.normalize()` for all new hours input
**Source:** `src/main/java/com/wfm/util/BigDecimals.java:14-19`
**Apply to:** `setDayHours` (new), any place a client-typed hours value is persisted.
```java
public static BigDecimal normalize(BigDecimal value) {
    if (value == null) { return null; }
    return value.setScale(2, RoundingMode.HALF_UP);
}
```

### `EnrichedColumnLayout` as single header source (D-02/D-08/D-13, UPL-09)
**Source:** `src/main/java/com/wfm/util/EnrichedColumnLayout.java`
**Apply to:** `DeskAgentExportService` (new Mon–Sun columns), `DeskAssignmentTemplateService` (specialty headers).
Never hardcode `"Monday"`.."Sunday"` or `"Specialty 1"`/`"Specialty 2"` string literals anywhere outside this class.

### Amber warning-notice block (Phase 10 D-11 precedent)
**Source:** `frontend/src/pages/ClientManagement.tsx:590-601`
**Apply to:** `DeskAgents.tsx`'s new "Set all days to…" overwrite warning.
Exact palette: bg `#fffbeb`, border `#fde68a`, text `#92400e`.

### Click-to-edit inline pattern (per-row nullable-id state)
**Source:** `frontend/src/pages/DeskAgents.tsx:41-42, 148-163, 352-365` (`editHoursAgentId`/`editHours`/`saveHours`)
**Apply to:** the new per-cell combo edit state (compound `{ agentId, day } | null` key) and the D-07 bulk action (relocated `editHours` state, now scoped inside the expanded row).

### Toast error handling
**Source:** `frontend/src/pages/DeskAgents.tsx:62-63` and every other handler in this file
```tsx
} catch (err) {
  showToast('error', getErrorMessage(err))
}
```
**Apply to:** all new save handlers (`saveDayHours`, relocated `saveHours`/bulk apply).

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `src/test/java/com/wfm/service/DeskAgentExportServiceTest.java` | test | file-I/O | No existing test file for `DeskAgentExportService` exists in this codebase (verified: `find src/test -iname "*DeskAgentExport*"` returns zero results per RESEARCH.md Wave 0 Gaps). Use `DeskAssignmentTemplateServiceTest.java` (168 lines, existing, verified present) as the nearest **file-I/O / POI workbook-assertion** style reference instead — it is the only existing test in this codebase that opens a generated `XSSFWorkbook` and asserts on cell values, even though it targets a different service. |
| Frontend component tests for `DeskAgents.tsx`'s new elements (E1–E5 in UI-SPEC.md) | test | — | No frontend test framework exists anywhere in this project (zero vitest/jest/testing-library, verified in RESEARCH.md). RESEARCH.md's Wave 0 Gaps section recommends manual UAT only (option b), matching the project-wide convention — no analog to map to because this class of file does not exist in the codebase at all. |

## Metadata

**Analog search scope:** `src/main/java/com/wfm/service/`, `src/main/java/com/wfm/repository/`, `src/main/java/com/wfm/dto/`, `src/main/java/com/wfm/controller/`, `src/main/java/com/wfm/util/`, `frontend/src/pages/`, `frontend/src/api/`, `src/test/java/com/wfm/service/` — all files this phase modifies plus their in-package siblings named in CONTEXT.md/RESEARCH.md's canonical references.
**Files scanned:** 13 (all read in full or via targeted ranges this session; no additional Glob/Grep search was needed since RESEARCH.md already identifies every touch point by file:line with verified line numbers).
**Pattern extraction date:** 2026-08-21
