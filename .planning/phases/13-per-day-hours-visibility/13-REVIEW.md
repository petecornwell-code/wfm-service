---
phase: 13-per-day-hours-visibility
reviewed: 2026-08-21T00:00:00Z
depth: standard
files_reviewed: 16
files_reviewed_list:
  - src/main/java/com/wfm/dto/DeskAgentResponse.java
  - src/main/java/com/wfm/dto/SetDayHoursRequest.java
  - src/main/java/com/wfm/service/DeskAgentService.java
  - src/main/java/com/wfm/service/DeskAgentExportService.java
  - src/main/java/com/wfm/service/DeskAssignmentTemplateService.java
  - src/main/java/com/wfm/repository/AgentDayHoursRepository.java
  - src/main/java/com/wfm/controller/DeskAgentController.java
  - src/main/java/com/wfm/util/EnrichedColumnLayout.java
  - frontend/src/api/client.ts
  - frontend/src/pages/DeskAgents.tsx
  - src/test/java/com/wfm/service/DeskAgentServiceReadPathTest.java
  - src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java
  - src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java
  - src/test/java/com/wfm/service/DeskAgentExportServiceTest.java
  - src/test/java/com/wfm/service/DeskAssignmentTemplateServiceTest.java
  - src/test/java/com/wfm/service/DeskAssignmentTemplateFilterTest.java
findings:
  critical: 0
  warning: 3
  info: 2
  total: 5
status: issues_found
---

# Phase 13: Code Review Report

**Reviewed:** 2026-08-21
**Depth:** standard
**Files Reviewed:** 16
**Status:** issues_found

## Summary

This phase migrates the roster and export read paths off the retired `Agent.contractedHoursPerDay`
scalar / `Desk.defaultContractedHoursPerDay` fallback and onto `agent_day_hours` (D-06), and adds a
structurally-safe single-weekday write path (`setDayHours`, D-05) alongside the surviving seven-row
bulk fan-out (`setContractedHours`, D-07).

The core claims hold up under direct inspection:

- **Read-path correctness.** `DeskAgentService.toResponse` and `DeskAgentExportService` both resolve
  every weekday from `agent_day_hours` + the schedule-derived default, never the retired scalar or the
  desk default. The `rosterIgnoresScalar_whenScalarDisagreesWithPerDayRows` test genuinely proves the
  scalar is excluded. `EnrichedColumnLayout.dayHeader`/`DAY_ORDER` are the single source for both the
  export and the template, with no stray weekday/specialty string literals left in either file.
- **N+1.** `listDeskAgentResponses` and `assignAgents` both bulk-fetch `agent_day_hours` once per
  desk/call (`loadDayHoursByAgent`), grouped in memory — no per-agent query.
- **Tenant/desk scoping on the new endpoint.** `setDayHours` resolves the agent via
  `agentRepository.findByIdAndTenantIdAndDeskId(...)` *before* any call into the non-tenant-scoped
  `AgentDayHoursRepository.findByAgent_IdAndDayOfWeek`, closing the IDOR the repository's own comment
  warns about. `setDayHours_foreignTenantAgent_throwsEntityNotFound` proves it.
- **Label preservation (I-3).** The per-cell path (`upsertDayHoursRow`) only ever touches the one row
  for `(agentId, day)`; `setDayHours_numeric_touchesExactlyOneRow` captures and re-asserts the other
  six rows' `id`/`hours`/`dayOffType` are byte-identical. The bulk fan-out remains explicitly
  destructive and is now correctly labelled and warning-guarded client-side, keyed off "a weekday
  carries a label" rather than "rows exist" (matches 13-RESEARCH.md Pitfall 4).
- **Export column shift.** First/Last Name correctly moved from indices 13/14 to 20/21; no other
  reader of that sheet (grep across `src/main` and `src/test`) still assumes the old indices, and
  `identityColumnsStillSanitized` proves the sanitizer still applies after the shift.
- **Input validation on the new per-cell endpoint.** `setDayHours` rejects (not clamps) hours outside
  `[0, 24]` with `IllegalArgumentException` → 400, matching P-04, with `24` itself accepted as the
  inclusive boundary.

Three gaps remain, all around the edges of the write paths rather than the phase's central read-path
claim, plus two lower-severity quality notes.

## Warnings

### WR-01: Bulk fan-out (`setContractedHours`) has no upper-bound validation, unlike the new per-cell endpoint

**File:** `src/main/java/com/wfm/service/DeskAgentService.java:244-248`
**Issue:** The new per-cell endpoint (`setDayHours`) correctly implements "reject, not clamp" for
`0–24` (P-04). The pre-existing bulk fan-out this phase keeps alive as the explicit "Set all days
to…" action only rejects negative values:

```java
BigDecimal normalized = BigDecimals.normalize(hours);
if (normalized != null && normalized.signum() < 0) {
    throw new IllegalArgumentException("Contracted hours per day must not be negative");
}
```

There is no upper-bound check at all. `agent_day_hours.hours` is `NUMERIC(5,2)` (max `999.99`), so a
value like `1000` fanned out to all seven rows will fail at the JDBC layer with a
`DataIntegrityViolationException`, which has no dedicated handler in `GlobalExceptionHandler` and
falls through to the generic `@ExceptionHandler(Exception.class)` → an opaque 500 ("An unexpected
error occurred") instead of a clean validation error. (Transactional integrity is preserved — the
whole `@Transactional` method rolls back — so this is not a data-loss risk, just a poor failure mode.)

This existed before the phase, but the phase's new `frontend/src/pages/DeskAgents.tsx` "Set all days
to…" control (`:599-609`) makes it directly reachable: it's a bare `<input type="number" step="0.25"
min="0" max="24">` with no wrapping `<form>` and no range check in the `Apply` handler
(`saveHours`, `:234-254`) beyond "is it empty" — so a user can type `1000` and click Apply.

**Fix:** Add the same `compareTo(new BigDecimal("24")) > 0` rejection used in `setDayHours` to
`setContractedHours`, and/or add a client-side range check to `saveHours` mirroring `saveDayHours`'s
`0..24` guard.

### WR-02: `PUT .../day-hours/{day}` returns 500, not the claimed 400, for a malformed `day` segment

**File:** `src/main/java/com/wfm/controller/DeskAgentController.java:84-91`, `src/main/java/com/wfm/controller/GlobalExceptionHandler.java`
**Issue:** 13-02-PLAN.md's Task 2 `<action>` explicitly asserts: *"an unrecognised segment produces a
400 through the existing handler chain rather than a 500."* This does not hold for this codebase.
`GlobalExceptionHandler` is a plain `@RestControllerAdvice` with a catch-all
`@ExceptionHandler(Exception.class)` and no handler for `MethodArgumentTypeMismatchException`. When
Spring's enum path-variable converter fails (e.g. `PUT .../day-hours/monday` lowercase, or
`.../day-hours/FOO`), the resulting `MethodArgumentTypeMismatchException` is caught by the generic
`Exception.class` handler and returned as a 500 with the message "An unexpected error occurred," not
a 400. This is untested — the codebase has no `@WebMvcTest`/`MockMvc` harness for `DeskAgentController`
(acknowledged in 13-02-SUMMARY.md as an unrun-verify gap), so this incorrect claim was never caught.
This is exactly the "dayOfWeek path variable's parsing" this review was asked to check.

The same gap also means a genuine race — two concurrent `PUT` requests for the same `(agentId, day)`
that both observe an absent row and both attempt an insert — will have the loser's
`DataIntegrityViolationException` (from the `(agent_id, day_of_week)` unique constraint) surface as
the same opaque 500 rather than a clean conflict response. This particular race is explicitly flagged
as an accepted, out-of-scope risk in 13-02-PLAN.md's `<flagged_assumptions>`, but the *shape* of the
failure (silent-looking generic 500 vs. the "fail loudly" the plan describes) is worth recording here
since it shares the same missing-handler root cause as the day-segment issue.

**Fix:** Add a `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` (and optionally
`DataIntegrityViolationException.class`) to `GlobalExceptionHandler` that returns 400/409 with an
operator-actionable message, or add a `@WebMvcTest` for `DeskAgentController` to catch this class of
regression going forward.

### WR-03: `DeskAgentService.setContractedHours` is missing test coverage for the upper-bound gap

**File:** `src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java`
**Issue:** `DeskAgentServiceContractedHoursTest` has `setContractedHours_negative_isRejected` but no
`setContractedHours_above24_isRejected` counterpart, unlike `DeskAgentServiceDayHoursTest`, which
tests both bounds for the new per-cell endpoint. This is the missing-test evidence behind WR-01 — had
this test existed, WR-01 would have been caught before merge.
**Fix:** Add a test asserting `setContractedHours(deskId, agentId, new BigDecimal("1000"))` either
throws `IllegalArgumentException` (once WR-01 is fixed) or is explicitly documented as an accepted gap
if the team decides not to fix it.

## Info

### IN-01: New expand/edit affordances are not keyboard-operable

**File:** `frontend/src/pages/DeskAgents.tsx:495-508` (expand/collapse toggle), `:586` (per-day cell click)
**Issue:** The expand/collapse control and each per-day cell are plain `<span onClick=...>` elements
with no `role="button"`, `tabIndex`, or `onKeyDown` handler, so a keyboard-only user cannot expand a
row or open the per-cell editor at all — only `aria-label` is present, which announces the control to
a screen reader without making it operable. This mirrors the file's pre-existing convention elsewhere
(e.g. `startEditSpec`), so it isn't a regression unique to this phase, but this phase substantially
grows the amount of primary functionality gated behind that pattern (all per-day editing).
**Fix:** Not blocking for this phase given the existing convention, but worth a follow-up pass adding
`role="button"` + `tabIndex={0}` + Enter/Space key handling to the new interactive spans.

### IN-02: Every cell blur fires a `PUT`, even with no change

**File:** `frontend/src/pages/DeskAgents.tsx:267-300` (`saveDayHours`)
**Issue:** `saveDayHours` runs unconditionally on blur/Enter with no dirty-check against the seeded
value, so clicking a cell to look at it and then clicking away (with no edit) still issues a
`PUT .../day-hours/{day}` request. The write is idempotent (same row, same values) so there is no
correctness impact, but it is unnecessary traffic on every stray click in the expanded row.
**Fix:** Compare `editCellValue` against the value `seedValueForEntry` produced before calling
`deskAgents.setDayHours`, and skip the request when unchanged. Not blocking — noted for polish only.

---

_Reviewed: 2026-08-21_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
