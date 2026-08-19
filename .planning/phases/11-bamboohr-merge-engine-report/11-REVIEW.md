---
phase: 11-bamboohr-merge-engine-report
reviewed: 2026-08-19T00:00:00Z
depth: standard
files_reviewed: 33
files_reviewed_list:
  - frontend/src/api/client.ts
  - frontend/src/pages/ClientManagement.tsx
  - src/main/java/com/wfm/controller/GlobalExceptionHandler.java
  - src/main/java/com/wfm/dto/MergeReportEntry.java
  - src/main/java/com/wfm/exception/BambooHRSyncFailedException.java
  - src/main/java/com/wfm/integration/AgentMergeService.java
  - src/main/java/com/wfm/integration/BambooRefreshService.java
  - src/main/java/com/wfm/integration/DelegatingBambooHRClient.java
  - src/main/java/com/wfm/integration/HttpBambooHRClient.java
  - src/main/java/com/wfm/model/Agent.java
  - src/main/java/com/wfm/model/WorkingDaysSource.java
  - src/main/java/com/wfm/service/DeskAssignmentUploadService.java
  - src/main/java/com/wfm/service/SolverService.java
  - src/main/resources/application.yml
  - src/main/resources/db/migration/V36__add_agent_working_days_source.sql
  - src/test/java/com/wfm/integration/MergePrecedenceTest.java
  - src/test/java/com/wfm/integration/MergeReportTest.java
  - src/test/java/com/wfm/integration/UploadFreshSyncTest.java
  - src/test/java/com/wfm/integration/WorkingDaysSourceGuardTest.java
  - src/test/java/com/wfm/integration/WorkingPatternMergeTest.java
  - src/test/java/com/wfm/service/DeskAssignmentUploadAllowlistTest.java
  - src/test/java/com/wfm/service/DeskAssignmentUploadDayCellTest.java
  - src/test/java/com/wfm/service/DeskAssignmentUploadEnrichedShapeTest.java
  - src/test/java/com/wfm/service/DeskAssignmentUploadLegacyShapeTest.java
  - src/test/java/com/wfm/service/DeskAssignmentUploadMultiSheetTest.java
  - src/test/java/com/wfm/service/DeskAssignmentUploadNonSchedulableRejectTest.java
  - src/test/java/com/wfm/service/DeskAssignmentUploadRetiredShapeTest.java
  - src/test/java/com/wfm/service/DeskAssignmentUploadSpecialtyTest.java
  - src/test/java/com/wfm/service/DeskAssignmentUploadValidationTest.java
  - src/test/java/com/wfm/service/PtoArbitrationTest.java
  - src/test/java/com/wfm/service/SheetPatternUnblockTest.java
  - src/test/java/com/wfm/service/UploadSyncFailureTest.java
  - src/test/java/com/wfm/service/WorkingDaysKnownTest.java
findings:
  critical: 0
  warning: 7
  info: 3
  total: 10
status: issues_found
---

# Phase 11: Code Review Report

**Reviewed:** 2026-08-19T00:00:00Z
**Depth:** standard
**Files Reviewed:** 33
**Status:** issues_found

## Summary

Reviewed the BambooHR merge-engine/report feature (plan 11-01: pre-transaction snapshot fetch,
`AgentMergeService` BambooHR-first identity/working-pattern merge, `MergeReportEntry` reporting,
`BambooHRSyncFailedException` → 503 zero-write guarantee; plan 11-02: `Agent.workingDaysSource`
permanence, solve-time PTO/pattern arbitration in `SolverService`).

The core invariants called out in the phase context hold up under inspection and are backed by
targeted unit tests: the fresh snapshot fetch (`AgentMergeService.fetchSnapshot`) runs strictly
before `transactionTemplate.executeWithoutResult` opens in both `DeskAssignmentUploadService` and
`BambooRefreshService`; a sync failure never reaches the transaction (`UploadSyncFailureTest`
verifies zero writes); `DeskAssignmentUploadService` still declares no `AgentDayOffRepository`
field (structural D-16 guard, verified by test); `arbitratePtoAgainstBambooWindow`'s window is
correctly closed/inclusive on both ends and does not touch MANDATORY facts; `unblockSheetWorkedDays`
only ever removes MANDATORY rows, never PTO, matching D-05.

No critical/security issues were found (no secrets, no injection vectors, no unsafe
deserialization — the new upload/report code paths are internal DTOs and server-computed
strings only). The findings below are correctness and robustness gaps: an unguarded duplicate-fact
risk in the new solve-time arbitration path, several places where diagnostic context is silently
discarded, an inconsistency between how the two merge-report code paths render a blank BambooHR
value, and a couple of pre-existing (not part of this diff, but present in the reviewed files)
frontend selection/state bugs that interact with the new Merge Report modal's surrounding code.

## Warnings

### WR-01: Recurring MANDATORY facts are not deduplicated against persisted AgentDayOff rows for the same (agent, date)

**File:** `src/main/java/com/wfm/service/SolverService.java:149-203` (persisted load), `:1079-1101` (`buildRecurringDaysOff`), `:203` (`allDaysOff.addAll(arbitratedRecurringDaysOff)`)

**Issue:** `allDaysOff` is first loaded from `agentDayOffRepository` (persisted rows, which include
BambooHR field-4517-derived MANDATORY rows written by `BambooRefreshService.persistRefreshData`
whenever a manual desk refresh has run). `unblockSheetWorkedDays` then removes a persisted
MANDATORY row only when the sheet marks that weekday as *worked* (day-off type `null`). But when
the sheet and BambooHR **agree** on an off-day (e.g. both say Sat/Sun are MANDATORY — the default,
common case for most rosters), the persisted BambooHR-origin row is left untouched, and
`buildRecurringDaysOff` unconditionally synthesizes a *second*, distinct `AgentDayOff` object
(random UUID, `DayOffType.MANDATORY`) for the exact same `(agent, date)` from `agent_day_hours`.
Both objects are then added to `allDaysOff` and flow straight into `schedule.setAgentDaysOff(...)`,
which per the code's own comment is consumed directly by the "Agent day off" HARD constraint
(`ScheduleConstraintProvider.agentDayOff`) precisely so nothing is missed. Two problem facts for
one real-world day-off means that constraint's match count / total penalty for that agent-date is
inflated (roughly doubled) whenever a desk has been both BambooHR-refreshed and enriched-uploaded
with agreeing patterns — which is the default/majority scenario, not an edge case. It doesn't flip
a feasible schedule to infeasible or vice versa (the day is blocked either way), but it corrupts
the violation counts / total penalties surfaced in `ConstraintViolationEntry` on the schedule
detail page, and inflates the hard score magnitude reported to operators.

**Fix:** Deduplicate `arbitratedRecurringDaysOff` against `allDaysOff` by `(agentId, date)` before
appending, mirroring the `dedupedDaysOff`/`putIfAbsent` idiom `BambooRefreshService` already uses
for exactly this kind of merge:
```java
Set<String> alreadyBlocked = allDaysOff.stream()
        .map(d -> d.getAgent().getId() + "|" + d.getDate())
        .collect(Collectors.toSet());
List<AgentDayOff> dedupedRecurring = arbitratedRecurringDaysOff.stream()
        .filter(d -> alreadyBlocked.add(d.getAgent().getId() + "|" + d.getDate()))
        .toList();
allDaysOff.addAll(dedupedRecurring);
```

---

### WR-02: `MergeReportEntry.bambooValue` can be a raw `null` for gap-filled identity fields, inconsistent with the working-pattern field's "not stated" convention — and untested

**File:** `src/main/java/com/wfm/integration/AgentMergeService.java:151-157` (identity gap-fill branch) vs. `:180-186` (`mergeWorkingPattern` gap-fill branch)

**Issue:** `mergeWorkingPattern`'s gap-fill branch explicitly substitutes the literal `"not stated"`
for a blank/absent BambooHR working-days pattern (line 185: `"not stated", renderDays(...)`).
`mergeIdentityFields`'s gap-fill branch (`!bambooHasData && sheetHasData`) instead passes the raw
`bambooValue` parameter straight into the `MergeReportEntry` record unchanged — and `bambooValue`
can be a literal Java `null` (e.g. `BambooEmployee.department()` is `null`, not `""`, whenever the
upstream field is genuinely absent, as `MergeReportTest.gapFilledField_emitsGapFilledOutcome_notOverride`
itself constructs with `new BambooEmployee(..., null, ...)`). The frontend's
`MergeReportEntry.bambooValue` TypeScript type (`frontend/src/api/client.ts:472`) is declared
non-nullable `string`, so this is also a type-contract mismatch between backend and frontend. The
rendered table cell (`ClientManagement.tsx:555`) silently renders nothing for `null` (React drops
null children) rather than showing an explicit "not stated"-style placeholder, which is
inconsistent copy for the operator reading the same report. Notably,
`gapFilledField_emitsGapFilledOutcome_notOverride` never asserts `entry.bambooValue()` at all, so
this gap is untested.

**Fix:** In `mergeIdentityFields`, normalize the gap-fill branch's stored `bambooValue` the same way
`mergeWorkingPattern` does:
```java
} else if (!bambooHasData && sheetHasData) {
    ...
    report.add(new MergeReportEntry(bamboohrId, agentName, field,
            bambooValue == null ? "" : bambooValue, sheetValue, OUTCOME_GAP_FILLED));
}
```
and add an assertion for `entry.bambooValue()` (e.g. `isEqualTo("")` or a "not stated" literal, per
whichever convention is chosen) to `MergeReportTest`.

---

### WR-03: `BambooHRRateLimitedException` re-thrown from `AgentMergeService.fetchSnapshot` loses the original exception/stack trace

**File:** `src/main/java/com/wfm/integration/AgentMergeService.java:104-109`

**Issue:**
```java
} catch (BambooHRRateLimitedException e) {
    throw new BambooHRRateLimitedException(
            uploadSyncFailureMessage(e.getMessage()), e.getRetryAfterSeconds());
}
```
`BambooHRRateLimitedException` has no cause-accepting constructor, so the new exception thrown
here is a completely disconnected object from the original — the original stack trace (which
would show exactly where in `HttpBambooHRClient` the 503/429 was received) is discarded. Combined
with WR-04 below (the exception handler never logs it either), a rate-limit failure during an
upload-triggered fetch leaves effectively zero server-side diagnostic trail beyond the
client-facing message string.

**Fix:** Either add a `Throwable cause` overload to `BambooHRRateLimitedException` and use it here,
or at minimum `log.warn("BambooHR rate-limited during upload snapshot fetch", e)` before
re-throwing, so the original stack trace reaches the application log even though the exception
object itself can't carry it.

---

### WR-04: `BambooHRSyncFailedException` and `BambooHRRateLimitedException` handlers never log the exception server-side

**File:** `src/main/java/com/wfm/controller/GlobalExceptionHandler.java:68-79`

**Issue:** `handleBambooHRRateLimited` and the newly added `handleBambooHRSyncFailed` both build
and return an `ErrorResponse` but never call `log.warn`/`log.error`. Contrast with
`handleUncaught` (line 90-94), which does `log.error("Unhandled exception", ex)`. Spring's
`@ExceptionHandler` machinery does not log handled exceptions on its own, so unless something else
logs it, these failures are completely invisible in server logs — no error-rate alerting, no way
to correlate a spike of "upload failed" tickets with a BambooHR outage after the fact.
`BambooHRSyncFailedException`'s own javadoc states "the original failure is preserved as the cause
for diagnostics," but nothing in the request path actually surfaces that cause anywhere.

**Fix:**
```java
@ExceptionHandler(BambooHRSyncFailedException.class)
public ResponseEntity<ErrorResponse> handleBambooHRSyncFailed(BambooHRSyncFailedException ex) {
    log.warn("BambooHR sync failed during upload", ex);
    return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "BAMBOOHR_SYNC_FAILED", ex.getMessage(), List.of());
}
```
(and similarly for `handleBambooHRRateLimited`).

---

### WR-05 (pre-existing, present in reviewed file): "Select all" checkbox selects filtered-out employees, inconsistent with its own checked-state and with what the operator can see

**File:** `frontend/src/pages/ClientManagement.tsx:95-101` (`toggleAll`) vs. `:444` (header checkbox `checked` expression) vs. `:266-277` (`filteredEmployees`)

**Issue:** `toggleAll` decides what to select using the raw, unfiltered `employees` array:
```javascript
const toggleAll = () => {
  if (selectedEmployeeIds.size === employees.length) {
    setSelectedEmployeeIds(new Set())
  } else {
    setSelectedEmployeeIds(new Set(employees.map(e => e.id)))
  }
}
```
but the header checkbox's own `checked` state, and the on-screen row list, are driven by
`sortedEmployees`/`filteredEmployees` (the `empSearch`-filtered subset). If an operator types a
filter (e.g. narrows 20 employees down to 3 visible rows) and clicks the header "select all"
checkbox, `toggleAll` selects **all 20** underlying employees (including the 17 not currently
visible), not just the 3 shown. The header checkbox itself then evaluates
`sortedEmployees.length > 0 && selectedEmployeeIds.size === sortedEmployees.length` (`3 === 20` is
false) and renders as *unchecked* even though 20 employees are now selected. The only surviving
signal is the `Assign Selected (${selectedEmployeeIds.size})` button label — an operator who
doesn't read that count carefully can click "Assign Selected" and assign employees to a desk that
were never visible in the filtered view. This file is being actively extended in this phase (the
Merge Report / newly-eligible-agents sections were added to the same modal-adjacent UI), so it's
worth fixing alongside the phase's other changes even though this specific function predates the
diff.

**Fix:** Base `toggleAll` (and the header checkbox's `checked` expression, which is already
correct) on `sortedEmployees`, and mutate `selectedEmployeeIds` by adding/removing only the
visible ids rather than the full `employees` array:
```javascript
const toggleAll = () => {
  setSelectedEmployeeIds(prev => {
    const allVisibleSelected = sortedEmployees.length > 0 &&
      sortedEmployees.every(e => prev.has(e.id))
    const next = new Set(prev)
    if (allVisibleSelected) {
      sortedEmployees.forEach(e => next.delete(e.id))
    } else {
      sortedEmployees.forEach(e => next.add(e.id))
    }
    return next
  })
}
```

---

### WR-06 (pre-existing, present in reviewed file): Changing "Rows per page" does not refetch — display silently goes stale

**File:** `frontend/src/pages/ClientManagement.tsx:418-423`

**Issue:**
```jsx
<select value={pageSize} onChange={e => { setPageSize(Number(e.target.value)); }}>
```
`setPageSize` updates local state but nothing re-fetches the employee list with the new page
size — there is no `useEffect` on `pageSize`, and `fetchEmployees` is only called from
`handleSearch`/pagination click handlers. The visible table keeps showing the old page's rows
(fetched under the previous `pageSize`) until the operator explicitly clicks Prev/Next or
re-searches, at which point the *next* request silently starts using the new value. This produces
a control that appears to do nothing when changed.

**Fix:** Trigger a re-fetch when `pageSize` changes, e.g. `onChange={e => { const n =
Number(e.target.value); setPageSize(n); fetchEmployeesWithSize(1, n) }}`, or add a `useEffect`
keyed on `pageSize` that re-runs `fetchEmployees(1, true)`.

---

### WR-07 (pre-existing, present in reviewed file): Silent empty catch on desk list load

**File:** `frontend/src/pages/ClientManagement.tsx:53`

**Issue:** `desksApi.list().then(setDeskList).catch(() => {})` swallows any error fetching the
desk list with no user-facing feedback and no logging. If this call fails (network blip, 5xx,
auth issue), both "Assign to Desk" and "View Desk" dropdowns silently stay empty with no
indication to the operator that anything went wrong — they'll just see empty `<select>`s and have
to guess why.

**Fix:** At minimum surface a toast, matching every other data-fetch in this file:
```javascript
desksApi.list().then(setDeskList).catch(err => showToast('error', getErrorMessage(err)))
```

## Info

### IN-01: `AgentMergeService.IDENTITY_FIELD_ORDER` is declared but never referenced

**File:** `src/main/java/com/wfm/integration/AgentMergeService.java:56-61`

**Issue:** The constant documents "the fixed order the merge report renders them" and callers do
in fact call `mergeIdentityFields` in that exact literal order in
`DeskAssignmentUploadService.java:386-409` — but nothing actually iterates or asserts against
`IDENTITY_FIELD_ORDER` itself; the ordering guarantee is maintained purely by convention at the
call site. A future edit that reorders the calls in `DeskAssignmentUploadService` would silently
break the ordering contract with no compiler or test signal pointing back at this constant
(`MergeReportTest.divergentFields_emitOneEntryEach_inFixedFieldOrder` hardcodes the same literal
list independently rather than referencing this constant).

**Fix:** Either delete the unused constant, or actually thread it through (e.g. have
`DeskAssignmentUploadService` iterate `IDENTITY_FIELD_ORDER` to drive the merge calls via a
field-name → value lookup, or have the test assert against the constant instead of a hardcoded
literal) so the two can't drift silently.

### IN-02: Active-status "merge" can never actually deactivate an agent — the contested-field framing is misleading

**File:** `src/main/java/com/wfm/service/DeskAssignmentUploadService.java:366-371` (earlier gate), `:403-409` (merge), `:419`

**Issue:** By the time `mergeIdentityFields` is called for "Active status," the row has already
been rejected earlier in the loop unless `"Active".equalsIgnoreCase(employee.status())` — i.e.
`bambooActiveLabel` is provably always the literal `"Active"` for every row that reaches this
code. Since BambooHR-has-data always wins under D-06, `mergedActiveLabel` can therefore never be
anything but `"Active"`, and `agent.setActive(...)` at line 419 is unconditionally `true`. The
merge-report entry for this field can only ever be silent or `"BambooHR override"` — it can never
represent a genuine "sheet wins" or "gap-filled" outcome the way the other five identity fields
can, despite being coded and reported identically to them. This isn't incorrect (it's covered by
`MergePrecedenceTest.activeStatus_bambooActive_sheetSaysInactive_bambooWins`), but the symmetrical
treatment alongside five genuinely-contested fields obscures that this one is structurally
one-sided.

**Fix:** No functional change needed; consider a code comment at the call site (or renaming the
outcome semantics) making clear that "Active status" can only ever report agreement or override,
never a sheet-wins/gap-fill, given the upstream skip gate.

### IN-03: `customWorkingdays` extraction key does not match the requested custom-report field id — verify against the live BambooHR account

**File:** `src/main/java/com/wfm/integration/HttpBambooHRClient.java:155` (requests field `"4517"`) vs. `:188` (`emp.path("customWorkingdays").asText(null)`)

**Issue:** The custom report request body asks BambooHR for the field by its raw numeric custom-field
id, `"4517"`, but the response is parsed by looking up the JSON key `"customWorkingdays"`. BambooHR's
custom-report API normally echoes back exactly the field identifier you requested unless that custom
field has an account-level API alias configured to `"customWorkingdays"`. If no such alias exists in
production, this lookup returns a missing node on every row, `customWorkingdays` is always `null`,
and — since this value is now the primary input to this phase's new
`AgentMergeService.mergeWorkingPattern` (always reports "gap-filled," never "replaced" or silent
agreement) and to `BambooRefreshService`'s D-15 downgrade guard (`shouldDowngradeWorkingDaysKnown`
would trigger on every BambooHR-owned agent, every refresh) — a large part of this phase's value
proposition would silently never activate in production. This code is unchanged by this diff (not
new), so it may already be verified against the tenant's actual BambooHR field alias configuration;
flagging for confirmation given how central this value now is to phase 11's core features.

**Fix:** Confirm against a real BambooHR custom report response (or the account's Field Alias
settings under Company Settings) that field 4517 is in fact returned under the key
`"customWorkingdays"`. If not, request the field using its aliased name directly in the `fields`
array, or read it back by the numeric key `"4517"` instead.

---

_Reviewed: 2026-08-19T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
