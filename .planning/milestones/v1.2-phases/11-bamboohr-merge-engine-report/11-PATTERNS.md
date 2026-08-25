# Phase 11: BambooHR Merge Engine & Report - Pattern Map

**Mapped:** 2026-08-18
**Files analyzed:** 8 (new/modified)
**Analogs found:** 8 / 8

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `src/main/java/com/wfm/integration/AgentMergeService.java` (NEW) | service | request-response (HTTP fetch + CRUD write) | `src/main/java/com/wfm/integration/BambooRefreshService.java` (`refreshDeskAgents`/`persistRefreshData`) | exact — same HTTP-before-TX shape, same package for `WorkingDaysParser` access |
| `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` (MODIFIED — `uploadDeskAssignments`, identity-merge block) | controller-adjacent service | CRUD / request-response | itself (restructure target) + `BambooRefreshService.refreshDeskAgents` for the TX-boundary shape | exact (self) / role-match (TX shape) |
| `src/main/java/com/wfm/dto/MergeReportEntry.java` (NEW) | model (DTO) | transform | `src/main/java/com/wfm/dto/SkippedRow.java` / `SkippedSheet.java` | exact — sibling ephemeral-report record already returned in `DeskAssignmentUploadResult` |
| `src/main/resources/db/migration/V36__*.sql` (NEW, D-15 provenance column) | migration | batch/DDL | `db/migration/V28__add_agent_working_days_known.sql` | exact — same table, same single-boolean-column style |
| `src/main/java/com/wfm/model/Agent.java` (MODIFIED — +1 provenance field) | model | CRUD | existing `workingDaysKnown` field on same class | exact |
| `frontend/src/pages/ClientManagement.tsx` (MODIFIED — Merge Report table + Eligibility callout in Upload Results modal) | component | request-response (render server DTO) | same file's "Per-desk rollup" block (lines ~505-516) and Warnings box (lines ~517-529) and Skipped Rows table (lines ~530-553) | exact — three structural siblings in the same modal |
| `frontend/src/api/client.ts` (MODIFIED — `DeskAssignmentUploadResult` interface +`mergeReport` field) | model (TS interface) | transform | existing interface itself (lines 468-476) | exact |
| `src/test/java/com/wfm/integration/AgentMergeServiceTest.java` (NEW) | test | CRUD/unit | `src/test/java/com/wfm/service/DeskAssignmentUploadMultiSheetTest.java` | exact — same no-Spring-context, mocked-collaborator, `MockMultipartFile`+`XSSFWorkbook` shape |

**Package placement note (Pitfall 3):** `WorkingDaysParser` is package-private (`final class WorkingDaysParser`, no `public` modifier) in `com.wfm.integration`, with Javadoc explicitly restricting it to same-package callers ("Called only from `BambooRefreshService` in the same package"). Any new merge-engine class that needs `parseWorkingDays`/`offDaysFrom` for D-05's day-pattern comparison **must live in `com.wfm.integration`**, not `com.wfm.service`, or it will not compile. `BambooEmployee` (the raw record with `customWorkingdays`/`employmentHistoryStatus`) also lives in `com.wfm.integration` — co-locating the merge engine there avoids an import-only workaround.

## Pattern Assignments

### `src/main/java/com/wfm/integration/AgentMergeService.java` (NEW — service, request-response)

**Analog:** `src/main/java/com/wfm/integration/BambooRefreshService.java`

**Imports pattern** (`BambooRefreshService.java:1-24`):
```java
package com.wfm.integration;

import com.wfm.config.TenantContext;
import com.wfm.exception.BambooHRRateLimitedException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.exception.RefreshInProgressException;
import com.wfm.model.*;
import com.wfm.repository.*;
import com.wfm.service.BambooSyncEventService;
import com.wfm.service.JobTitleConfigService;
import com.wfm.util.AgentNameSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
```
Note: `AgentMergeService` will need `com.wfm.service.DeskAssignmentUploadResult`-adjacent DTOs, `com.wfm.dto.MergeReportEntry`, and `AgentEligibilityService` — cross-package imports from `com.wfm.integration` into `com.wfm.service`/`com.wfm.dto` are fine (the restriction is one-directional: `com.wfm.service` cannot reach package-private `com.wfm.integration` members, but `com.wfm.integration` can freely import public `com.wfm.service`/`com.wfm.dto` types).

**HTTP-before-transaction core pattern** (`BambooRefreshService.java:87-145`, verbatim):
```java
public void refreshDeskAgents(UUID deskId) {
    if (refreshInProgress.putIfAbsent(deskId, true) != null) {
        throw new RefreshInProgressException("A BambooHR refresh is already in progress for this desk.");
    }
    long tenantId = TenantContext.getTenantId();
    BambooSyncEvent syncEvent = new BambooSyncEvent();
    syncEvent.setTenantId(tenantId);
    syncEvent.setDeskId(deskId);
    syncEvent.setStartedAt(OffsetDateTime.now());
    syncEvent.setSuccess(false);

    try {
        Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));

        String deskName = desk.getName();
        List<BambooEmployee> employees = bambooHRClient.listEmployees(
                String.valueOf(tenantId), deskName);

        LocalDate from = LocalDate.now().minusWeeks(lookbackWeeks);
        LocalDate to = LocalDate.now().plusWeeks(lookaheadWeeks);
        List<BambooTimeOff> timeOffs = bambooHRClient.listTimeOff(String.valueOf(tenantId), from, to);

        // Use TransactionTemplate instead of @Transactional on a self-invoked method,
        // which Spring proxies cannot intercept.
        transactionTemplate.executeWithoutResult(status ->
                persistRefreshData(deskId, tenantId, desk, employees, timeOffs, from, to));

        syncEvent.setSuccess(true);
        syncEvent.setAgentsSynced(employees.size());
        syncEvent.setTimeOffPulled(timeOffs.size());

    } catch (BambooHRRateLimitedException e) {
        syncEvent.setErrorMessage(e.getMessage());
        syncEvent.setRetryAfterSeconds(e.getRetryAfterSeconds());
        throw e;
    } catch (Exception e) {
        syncEvent.setErrorMessage(e.getMessage());
        throw e;
    } finally {
        refreshInProgress.remove(deskId);
        syncEvent.setFinishedAt(OffsetDateTime.now());
        bambooSyncEventService.record(syncEvent);
    }
}
```
**This is the exact structural template for `uploadDeskAssignments`.** Note: the upload variant needs its own signature (whole-tenant fetch, not desk-scoped `listEmployees(tenantId, deskName)`); D-01 requires `listEmployees(tenantId, null)` (no department filter) since one fetch must serve all sheets. The `refreshInProgress` guard and `BambooSyncEvent` recording are per-desk concerns (Claude's Discretion item — A3 in RESEARCH.md: `deskId=null` acceptable for an upload-triggered whole-workbook sync if recorded at all).

**Config values to reuse, not reinvent** (`BambooRefreshService.java:47-51`):
```java
@Value("${bamboohr.time-off.lookahead-weeks:8}")
private int lookaheadWeeks;

@Value("${bamboohr.time-off.lookback-weeks:12}")
private int lookbackWeeks;
```

**Error handling / MRG-07 propagation:** do NOT catch `BambooHRRateLimitedException` inside the merge engine except to attach `syncEvent` metadata (if sync-event recording is adopted) — let it propagate uncaught so `GlobalExceptionHandler` handles it (see Shared Patterns below). This is what makes "zero DB writes on sync failure" automatic: the exception is thrown before `transactionTemplate.executeWithoutResult(...)` ever opens.

---

### `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` (MODIFIED — restructure `uploadDeskAssignments`)

**Analog:** `BambooRefreshService.refreshDeskAgents` (TX-boundary shape) + itself (current identity-merge block, inversion target)

**Current state — entry point is `@Transactional` directly, which violates D-01/D-02** (`DeskAssignmentUploadService.java:76-91`, verbatim):
```java
@Transactional
public DeskAssignmentUploadResult uploadDeskAssignments(MultipartFile file) throws IOException {
    long tenantId = TenantContext.getTenantId();

    // Pre-populate the BambooHR cache so findCachedEmployee can match agents
    // even if the user hasn't manually fetched a department on this page yet.
    clientManagementService.ensureCachePopulatedForUpload(tenantId);

    List<String> assigned = new ArrayList<>();
    List<SkippedRow> skipped = new ArrayList<>();
    List<SkippedSheet> skippedSheets = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<SheetSummary> sheetSummaries = new ArrayList<>();
    ...
```
Spring's proxy opens the transaction *before* this body executes — so any `bambooHRClient` call added at the top still happens inside an open TX. Must be rewritten to: drop `@Transactional`, do the fetch(es) unguarded, then wrap the existing sheet-loop (currently ~lines 199-483) in `transactionTemplate.executeWithoutResult(...)`, mirroring `refreshDeskAgents` above. `ensureCachePopulatedForUpload` must NOT be reused as-is — it no-ops when the cache is already warm (`ClientManagementService.java:162`), which breaks D-04's "always fresh" guarantee.

**Current identity-field merge logic — the concrete inversion target (D-06/D-07)** (`DeskAssignmentUploadService.java:365-386`, verbatim):
```java
// Backfill missing identity fields from the BambooHR cache
if (isBlank(agent.getEmail())) agent.setEmail(cached.workEmail());
if (isBlank(agent.getDepartment())) agent.setDepartment(cached.department());
if (isBlank(agent.getJobTitle())) agent.setJobTitle(cached.jobTitle());
if (isBlank(agent.getFirstName()) || isBlank(agent.getLastName())) {
    AgentNameSplitter.Split cachedSplit = AgentNameSplitter.split(cached.displayName());
    if (isBlank(agent.getFirstName())) agent.setFirstName(cachedSplit.firstName());
    if (isBlank(agent.getLastName())) agent.setLastName(cachedSplit.lastName());
}
if (isBlank(agent.getName())) agent.setName(cached.displayName());

// Spreadsheet-supplied identity fields are optional and override the
// cache when present (D-07)
if (!isBlank(firstName)) agent.setFirstName(firstName.trim());
if (!isBlank(lastName)) agent.setLastName(lastName.trim());
if (!isBlank(jobTitle)) agent.setJobTitle(jobTitle.trim());
if (!isBlank(email)) agent.setEmail(email.trim());
if (!isBlank(department)) agent.setDepartment(department.trim());
if (!isBlank(activeStr)) agent.setActive(parseActive(activeStr));
```
This is Phase 10's D-07: sheet wins when present. **Phase 11 must invert this** — BambooHR wins whenever it has data (not null/blank/whitespace-only per D-06), sheet fills only true gaps, and any case where the sheet supplied a *different* non-blank value than a non-blank BambooHR value must produce a `MergeReportEntry` with outcome `"BambooHR override"` (D-07/D-11). Where BambooHR is blank, the sheet's value is used and — if it was actually used to fill a gap — recorded as `"Gap-filled by spreadsheet"`.

**`workingDaysKnown` — preserve unchanged (D-16)** (`DeskAssignmentUploadService.java:451-460`, verbatim):
```java
// Reaching here means all 7 Mon-Sun cells parsed (any failure skips the row
// above), so this upload has explicitly stated the agent's working days. Mark
// them known.
agent.setWorkingDaysKnown(true);
```
D-15 adds a provenance marker alongside this — do not change the resolution rule itself.

**Single-write-per-weekday pattern — do not double-save (`agent_day_hours` unique constraint)** (`DeskAssignmentUploadService.java:455-473`, verbatim):
```java
for (DayOfWeek day : EnrichedColumnLayout.DAY_ORDER) {
    DayCellResult dayResult = dayResults.get(day);
    AgentDayHours agentDayHours = new AgentDayHours();
    agentDayHours.setTenantId(tenantId);
    agentDayHours.setAgent(agent);
    agentDayHours.setDayOfWeek(day);
    agentDayHours.setHours(dayResult.hours());
    agentDayHours.setDayOffType(dayResult.type());
    agentDayHoursRepository.save(agentDayHours);
    if (dayResult.clampWarning() != null) {
        warnings.add("Row " + (i + 1) + " (id " + bamboohrId.trim() + ") " + dayResult.clampWarning());
    }
}
```
`AgentDayHours` has a `(agent_id, day_of_week)` unique constraint (`model/AgentDayHours.java:9-11`) — the merge decision must be fully computed before this single `save` call, never saved-then-corrected (Pitfall 4).

**Return-shape pattern** (`DeskAssignmentUploadService.java:480-486`, verbatim):
```java
return new DeskAssignmentUploadResult(
        assigned.size(), skipped.size(), assigned, skipped,
        sheetSummaries, warnings, skippedSheets);
```
Extend this constructor call to add `mergeReport` (and, if adopted, a separate `newlyEligibleAgents` list for D-14's callout) as trailing arguments — extend the record definition below in step.

---

### `src/main/java/com/wfm/dto/MergeReportEntry.java` (NEW — DTO)

**Analog:** `src/main/java/com/wfm/dto/SkippedRow.java` / the existing `SheetSummary` record (`DeskAssignmentUploadService.java:562`)

**Pattern — simple record, no behaviour, field-for-field with the UI-SPEC's table columns:**
```java
// UI-SPEC.md table columns, in order: BambooHR ID | Agent | Field | BambooHR value | Sheet value | Outcome
// Outcome values (fixed vocabulary per Copywriting Contract): "BambooHR override" | "Gap-filled by spreadsheet"
public record MergeReportEntry(
        String bamboohrId,
        String agentName,
        String field,          // one of D-08's contested field labels, e.g. "Email", "Working pattern (Mon–Sun)"
        String bambooValue,
        String sheetValue,
        String outcome
) {}
```
D-11: only build an entry for divergences and gap-fills — never for silent agreement. D-13: this list is returned in `DeskAssignmentUploadResult` and never persisted (no migration, no repository).

---

### `src/main/resources/db/migration/V36__*.sql` (NEW — D-15 provenance column)

**Analog:** `db/migration/V28__add_agent_working_days_known.sql` (verbatim):
```sql
ALTER TABLE agent ADD COLUMN working_days_known BOOLEAN NOT NULL DEFAULT TRUE;
```
Next migration number is `V36` (latest on disk confirmed as `V35__contracted_hours_under_back_to_hard.sql`). Follow the same single-column-add style; per RESEARCH.md, include an inline comment explaining the hazard the column closes (mirroring `V30`'s comment style — explain *why*, not just *what*). Suggested shape:
```sql
-- D-15: distinguishes a sheet-sourced working-days pattern from a BambooHR-sourced one,
-- so a later BambooRefreshService.persistRefreshData pass cannot silently flip
-- working_days_known back to false for an agent whose pattern came from the spreadsheet
-- (the UAT 2026-08-12 downgrade hazard).
ALTER TABLE agent ADD COLUMN working_days_source VARCHAR(20) NOT NULL DEFAULT 'BAMBOOHR';
```
(Exact column name/type is a planner decision — flagged here as the analog and hazard context only.)

---

### `frontend/src/pages/ClientManagement.tsx` (MODIFIED — Merge Report table + Eligibility callout)

**Analog:** same file, three structural siblings already in the Upload Results modal.

**Modal container to widen (600px → 760px per UI-SPEC)** (`ClientManagement.tsx:490`, verbatim):
```tsx
<div style={{ background: '#fff', borderRadius: '8px', padding: '1.5rem', width: '600px', maxHeight: '80vh', display: 'flex', flexDirection: 'column' }}>
```

**Per-desk rollup — the exact conditional-render idiom + heading style to copy for both new sections** (`ClientManagement.tsx:505-516`, verbatim):
```tsx
{uploadResult.sheetSummaries.length > 0 && (
  <div style={{ marginTop: '0.5rem' }}>
    <div style={{ fontWeight: 600, fontSize: '0.85rem', marginBottom: '0.25rem' }}>Per-desk rollup</div>
    <ul style={{ fontSize: '0.85rem', margin: 0, paddingLeft: '1.25rem' }}>
      {uploadResult.sheetSummaries.map((sheet: SheetSummary, idx: number) => (
        <li key={idx}>
          {sheet.deskName}: {sheet.importedCount} imported, {sheet.skippedCount} skipped
        </li>
      ))}
    </ul>
  </div>
)}
```

**Warnings box — the block-styling analog for both new boxed sections (8px padding, amber for override / green for eligibility per UI-SPEC)** (`ClientManagement.tsx:517-529`, verbatim):
```tsx
{(uploadResult.warnings.length > 0 || uploadResult.skippedSheets.length > 0) && (
  <div style={{ marginTop: '0.75rem', padding: '0.5rem', background: '#fffbeb', border: '1px solid #fde68a', borderRadius: '4px' }}>
    <div style={{ fontWeight: 600, fontSize: '0.85rem', color: '#92400e', marginBottom: '0.25rem' }}>Warnings</div>
    <ul style={{ fontSize: '0.85rem', color: '#92400e', margin: 0, paddingLeft: '1.25rem' }}>
      {uploadResult.warnings.map((warning: string, idx: number) => (
        <li key={`warning-${idx}`}>{warning}</li>
      ))}
      {uploadResult.skippedSheets.map((sheet: SkippedSheet, idx: number) => (
        <li key={`skipped-sheet-${idx}`}>Sheet "{sheet.sheetName}": {sheet.reason}</li>
      ))}
    </ul>
  </div>
)}
```

**Skipped Rows table — the exact structural analog for the new Merge Report table (scrollable container, thead/tbody shape)** (`ClientManagement.tsx:530-553`, verbatim):
```tsx
{uploadResult.skippedCount > 0 && (
  <div style={{ overflowY: 'auto', maxHeight: '300px', marginTop: '1rem', border: '1px solid #e5e7eb', borderRadius: '4px' }}>
    <table style={{ width: '100%', fontSize: '0.85rem' }}>
      <thead>
        <tr>
          <th>Row</th>
          <th>BambooHR ID</th>
          <th>Name</th>
          <th>Reason</th>
        </tr>
      </thead>
      <tbody>
        {uploadResult.skippedDetails.map((row: SkippedRow, idx: number) => (
          <tr key={idx}>
            <td>{row.rowNumber}</td>
            <td>{row.bamboohrId ?? '—'}</td>
            <td>{row.name ?? '—'}</td>
            <td>{row.reason}</td>
          </tr>
        ))}
      </tbody>
    </table>
  </div>
)}
```
Per UI-SPEC: the Merge Report table uses this exact `overflowY:'auto', maxHeight:'300px'` scroll container and `border:'1px solid #e5e7eb'` shell, with 6 columns (`BambooHR ID | Agent | Field | BambooHR value | Sheet value | Outcome`), Outcome rendered as a small colored pill (amber `#92400e` bg for "BambooHR override", accent blue `#3b82f6` bg for "Gap-filled by spreadsheet" — the ONLY accent-blue usage in this phase). The Eligibility callout copies the Warnings box's green counterpart — the existing "Upload Desk Assignments" panel's green treatment (`ClientManagement.tsx:345`, bg `#f0fdf4` / border `#bbf7d0`) rather than the amber Warnings box. Insertion order per UI-SPEC: Eligibility callout first (directly after per-desk rollup), then Merge Report table, then existing Warnings box.

---

### `frontend/src/api/client.ts` (MODIFIED — DTO mirror)

**Analog:** the interface itself (`frontend/src/api/client.ts:468-476`, verbatim):
```typescript
export interface DeskAssignmentUploadResult {
  assignedCount: number
  skippedCount: number
  assignedDetails: string[]
  skippedDetails: SkippedRow[]
  sheetSummaries: SheetSummary[]
  warnings: string[]
  skippedSheets: SkippedSheet[]
}
```
Add `mergeReport: MergeReportEntry[]` (and, if D-14's callout needs its own list rather than being derived client-side, `newlyEligibleAgents: string[]`) field-for-field matched to the new backend record — same convention Phase 10 established ("Frontend TS interfaces matched field-for-field to the already-implemented backend DTOs").

---

### `src/test/java/com/wfm/integration/AgentMergeServiceTest.java` (NEW — unit test)

**Analog:** `src/test/java/com/wfm/service/DeskAssignmentUploadMultiSheetTest.java:1-90`

**Imports + mock-collaborator setup pattern** (verbatim, lines 1-68):
```java
package com.wfm.service;

import com.wfm.dto.BambooEmployeeResponse;
import com.wfm.model.*;
import com.wfm.repository.*;
import com.wfm.util.EnrichedColumnLayout;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeskAssignmentUploadMultiSheetTest {

    private AgentRepository agentRepository;
    private DeskRepository deskRepository;
    private ClientManagementService clientManagementService;
    ...
    private DeskAssignmentUploadService service;

    private static final long TENANT_ID = 1L;
    private static final String[] FULL_WEEK = {"8", "8", "8", "8", "8", "0", "0"};

    @BeforeEach
    void setUp() {
        agentRepository = mock(AgentRepository.class);
        deskRepository = mock(DeskRepository.class);
        clientManagementService = mock(ClientManagementService.class);
        ...
        when(agentEligibilityService.isIncludedByTitleAllowlist(anyLong(), any())).thenReturn(true);

        service = new DeskAssignmentUploadService(
                agentRepository, deskRepository, clientManagementService, ...);

        com.wfm.config.TenantContext.setTenantId(TENANT_ID);
        when(agentEligibilityService.isNonSchedulable(anyLong(), anyString())).thenReturn(false);
        when(agentEligibilityService.isNonSchedulable(anyLong(), isNull())).thenReturn(false);
    }
```
No `@SpringBootTest`, no real DB — every repository/service collaborator is `mock(...)`, `TenantContext.setTenantId(...)` is set manually in `@BeforeEach`. For `AgentMergeService` tests, mock `BambooHRClient` directly (`mock(BambooHRClient.class)`) and stub `listEmployees`/`listTimeOff` to return fixture `BambooEmployee`/`BambooTimeOff` lists, then assert on the merged `Agent` fields and the emitted `MergeReportEntry` list — same assertion style (`assertThat(...)` from AssertJ) as the rest of this suite. `MockMultipartFile` + `XSSFWorkbook` (used elsewhere in this same test file for building fixture workbooks) is the pattern to reuse if the merge test needs to exercise the full upload path rather than the merge engine in isolation.

---

## Shared Patterns

### HTTP-before-transaction / `TransactionTemplate` boundary
**Source:** `src/main/java/com/wfm/integration/BambooRefreshService.java:87-145`
**Apply to:** `AgentMergeService` (new fetch) and `DeskAssignmentUploadService.uploadDeskAssignments` (restructure)
```java
transactionTemplate.executeWithoutResult(status -> persistRefreshData(...));
```
`TransactionTemplate` is the established mechanism for exactly this shape because `@Transactional` on a self-invoked method is not intercepted by Spring's proxy (documented in-repo, not this phase's discovery).

### Sync-failure → 503 propagation (MRG-07)
**Source:** `src/main/java/com/wfm/controller/GlobalExceptionHandler.java:67-70`
```java
@ExceptionHandler(BambooHRRateLimitedException.class)
public ResponseEntity<ErrorResponse> handleBambooHRRateLimited(BambooHRRateLimitedException ex) {
    return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "BAMBOOHR_RATE_LIMITED", ex.getMessage(), List.of());
}
```
**Apply to:** the new upload-sync fetch — let `BambooHRRateLimitedException` propagate uncaught from `listEmployees`/`listTimeOff`, thrown before `transactionTemplate.executeWithoutResult(...)` opens. Zero new exception-handling code needed. UI-SPEC requires the exception message text to explicitly state "no changes were made" — verify/adjust `BambooHRRateLimitedException`'s message construction (or `BambooRefreshService`'s catch block, if message assembly happens there) to include that clause; it currently propagates via `ex.getMessage()` into `Toast.tsx`'s existing red (`#dc2626`) toast rendering (`Toast.tsx:39`) with no code change needed there.

### `WorkingDaysParser` package-private access (D-05 pattern comparison)
**Source:** `src/main/java/com/wfm/integration/WorkingDaysParser.java:1-18`
**Apply to:** `AgentMergeService` — must be declared in `com.wfm.integration` to call `parseWorkingDays`/`offDaysFrom` directly; do not widen the parser to `public` (deliberate Phase 9/10 encapsulation choice) and do not re-implement its parsing logic elsewhere.

### Raw `BambooEmployee` vs cache DTO (D-08 field access)
**Source:** `src/main/java/com/wfm/integration/BambooEmployee.java:1-13`
```java
public record BambooEmployee(
    String id, String displayName, String workEmail, String department,
    String jobTitle, String status, String employmentHistoryStatus,
    String customWorkingdays, String wfmTenantId, String project
) {}
```
**Apply to:** the merge engine's field-precedence logic — `customWorkingdays` and `employmentHistoryStatus` are NOT present on the cache-layer `BambooEmployeeResponse` DTO used by `findCachedEmployee`/`ClientManagementService`; the merge engine must work off this raw record from the fresh `listEmployees` call, not the cache.

### Ephemeral report DTO extension of an existing result record
**Source:** `src/main/java/com/wfm/service/DeskAssignmentUploadService.java:553-561` (`DeskAssignmentUploadResult`) and `frontend/src/api/client.ts:468-476` (its TS mirror)
**Apply to:** `MergeReportEntry` list addition — both sides kept in the same field-for-field-matched style; no persistence, no repository, gone on modal close (D-13).

## No Analog Found

None — every file in scope has a direct structural sibling already in the codebase (verified above). The one genuinely novel piece of logic — where D-05's "sheet un-blocks a stale BambooHR MANDATORY `AgentDayOff` row" is enforced (merge-write time vs. solve time) — has no existing analog because it's an open architectural question (RESEARCH.md Open Question 1 / Pitfall 2), not a missing pattern; whichever side the planner picks, `SolverService.buildAgentDaysOffMap`/`buildRecurringDaysOff` (`SolverService.java:1000-1083`) is the read-side analog to extend if solve-time arbitration is chosen (consistent with D-09's PTO-window arbitration, which is confirmed solve-time).

## Metadata

**Analog search scope:** `src/main/java/com/wfm/integration/`, `src/main/java/com/wfm/service/`, `src/main/java/com/wfm/dto/`, `src/main/java/com/wfm/controller/`, `src/main/java/com/wfm/exception/`, `src/test/java/com/wfm/service/`, `frontend/src/pages/`, `frontend/src/components/`, `frontend/src/api/`, `db/migration/`
**Files scanned:** ~15 read directly this session (`BambooRefreshService.java`, `DeskAssignmentUploadService.java`, `DeskAssignmentUploadMultiSheetTest.java`, `ClientManagement.tsx`, `Toast.tsx`, `GlobalExceptionHandler.java`, `BambooEmployee.java`, `WorkingDaysParser.java`, `client.ts` excerpt, plus migration listing)
**Pattern extraction date:** 2026-08-18
