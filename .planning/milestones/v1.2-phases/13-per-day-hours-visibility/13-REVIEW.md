---
phase: 13-per-day-hours-visibility
reviewed: 2026-08-24T00:00:00Z
depth: standard
files_reviewed: 6
files_reviewed_list:
  - frontend/src/pages/DeskAgents.tsx
  - src/main/java/com/wfm/service/DeskAgentService.java
  - src/main/java/com/wfm/controller/GlobalExceptionHandler.java
  - src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java
  - src/test/java/com/wfm/service/DeskAgentServiceBulkRollbackTest.java
  - src/test/java/com/wfm/controller/GlobalExceptionHandlerTest.java
findings:
  critical: 0
  warning: 0
  info: 3
  total: 3
status: issues_found
---

# Phase 13: Code Review Report

**Reviewed:** 2026-08-24 (gap-closure pass) / 2026-08-21 (original pass)
**Depth:** standard
**Files Reviewed:** 6 (gap-closure pass) + 16 (original pass, 12 overlapping)
**Status:** issues_found (Info only — no open Critical or Warning findings)

This file has two dated sections. **This is a rewrite, not a fresh review** — the original
2026-08-21 findings are preserved below with their current disposition explicitly marked, per the
instruction not to silently drop review history. Read the "Gap-Closure Pass" section first; it
supersedes the disposition of WR-01/WR-02/WR-03 from the original pass.

---

## Gap-Closure Pass (13-05 / 13-06) — 2026-08-24

**Scope:** commits `8f4f528`, `208d5c0`, `974dc25`, `6cc76f7`, `ab0af9d`, `6325203`, `534e9ed`,
`dcc6e06` — closing the two `13-VERIFICATION.md` UI gaps (E1 muted collapsed summary, E4 seed
literal) and the two Warning findings from the original review (WR-01 bulk upper-bound, WR-02
malformed-day 500).

### Summary

All four things this pass claims to close are genuinely closed by direct inspection, not just by
green tests. In particular, the rollback test (`dcc6e06`) — the single highest-value thing this
review was asked to scrutinize — is not a test that passes for the wrong reason. It correctly uses
`@Transactional(propagation = NOT_SUPPORTED)` at the test-method level, which per Spring's
documented `TransactionalTestExecutionListener` behavior means the `@DataJpaTest`-supplied
rollback-wrapper transaction is never created for that method; the service's own
`@Transactional` then opens a real, independently committed/rolled-back transaction against the
H2 test database. The `@AfterEach`'s need for explicit manual cleanup (rather than relying on
`@DataJpaTest`'s automatic rollback) is itself corroborating evidence the baseline-seeding call
really did commit for real — if the ambient rollback wrapper were still active, that cleanup step
would be unnecessary and inert. The post-failure assertion compares row **ids**, not just hours,
against the pre-failure baseline (`assertThat(idsAfterFailure).isEqualTo(baselineIds)`); because
`setContractedHours` deletes-and-recreates (new entities, new generated ids) on every call, a
failed rollback would have produced a fresh, different id set even if the hours values happened to
still read `6.00` by coincidence, so this assertion cannot pass by accident. The `argThat` stub
that throws only on the THURSDAY row (the 4th of `DayOfWeek.values()`'s Monday-first order) plus
the `atLeast(4)` invocation-count check after `clearInvocations` genuinely proves the failure
happened mid-loop, not before the loop started. No flaw found in this test's methodology.

### Findings

No Critical or Warning findings in this pass. One Info-level quality note below.

### Closed findings (from the original 2026-08-21 review)

**WR-01 — CLOSED by `ab0af9d`, `974dc25`.** `DeskAgentService.setContractedHours` now rejects
`> 24` (not just negative) with the same inclusive `0–24` bound as `setDayHours`
(`DeskAgentService.java:247-249`), and `DeskAgents.tsx`'s bulk "Set all days to…" `Apply` handler
(`saveHours`, `DeskAgents.tsx:246-249`) now rejects out-of-range input with a toast **before**
`confirm()` and before any network call — confirmed by reading the function body top-to-bottom;
the range check is textually and behaviorally prior to both the `confirm()` call and the
`deskAgents.setContractedHours(...)` await. The client check is UX-only, as intended; the server
check is genuinely authoritative — verified there is no code path that lets a client bypass it
(the controller passes the raw request value straight through to the service, no clamping
anywhere in between).

**WR-02 — CLOSED by `534e9ed`.** `GlobalExceptionHandler.handleTypeMismatch` maps
`MethodArgumentTypeMismatchException` to a clean 400. Verified the ASVS V5 constraint holds: the
handler's response message interpolates only `ex.getName()` (the declared path-variable name,
e.g. `"day"`) — it never touches `ex.getValue()` (the rejected token) or `ex.getRequiredType()`
(the target enum class), and `GlobalExceptionHandlerTest.handleTypeMismatch_returns400WithParameterNameOnly`
explicitly asserts the message `doesNotContain("notaday")` and `doesNotContain("DayOfWeek")`.
Checked for indirect leakage too — `buildResponse` doesn't call `ex.getMessage()` or `ex.toString()`
anywhere in this handler, and the exception's `cause` is never touched — so there's no nested-cause
path back into the response body either. As a side effect (not requested but not a defect), this
handler is global, so it now also cleanly 400s malformed-UUID path segments on every other endpoint
in the codebase, not just `day-hours/{day}`; no existing test asserted a 500 for any of those, so
nothing broke.

**WR-03 — CLOSED by `6cc76f7`.** `DeskAgentServiceContractedHoursTest` now has
`setContractedHours_above24_isRejectedAndPersistsNothing`,
`setContractedHours_above24_leavesExistingRowsAndLabelsUntouched` (also proves labels/other rows
survive a rejected bulk call — stronger than what WR-03 asked for), and
`setContractedHours_exactly24_isAccepted` (inclusive boundary). This is exactly the missing
coverage WR-03 flagged.

**E1 (collapsed summary muted color) — CLOSED by `8f4f528`.** The collapsed `Hours/Day` cell now
wraps the summary text in a `<span>` styled `{ color: '#9ca3af', fontStyle: 'italic' }` gated on
the new shared `isEveryDayNotSet(da)` predicate, which correctly replaces the previously-inline,
duplicated `DAY_ORDER.every(d => !da.dayHours[d].hasRow)` expression at the expanded-row empty-note
site too (`DeskAgents.tsx:562`) — no leftover duplicate copy of that predicate remains. Scope is
correctly limited to the fully-empty case (E1 "empty"), not the partial-fill case (E1 "partial"),
matching the UI-SPEC distinction.

**E4 (per-cell seed literal) — CLOSED by `208d5c0`.** `seedValueForEntry` now returns the literal
`'Not set (default)'` for a not-set weekday. Traced the round-trip end to end as the task
instructed: `saveDayHours`'s branch `raw === '' || raw === 'Not set (default)'` (unchanged by this
commit — it already handled the literal) maps directly to `{ clearRow: true }`, and the datalist
backing the input (`<option value="Not set (default)" />`, `DeskAgents.tsx:408`) supplies the exact
same string, so the literal is a case-exact match on both the seed and save paths. The literal
cannot be persisted as a stored value — there is no code path where `'Not set (default)'` reaches
the `hours` or `dayOffType` fields of the PUT body; every branch order in `saveDayHours` checks
`clearRow` before falling through to numeric parsing, so a user who leaves the seeded literal
untouched and blurs the cell issues a (harmless, idempotent) delete-if-present, not a write of the
literal text. Confirmed intact.

### IN-03: The `0–24` range check is now duplicated across four call sites with no shared constant

**File:** `src/main/java/com/wfm/service/DeskAgentService.java:248` and `:311`;
`frontend/src/pages/DeskAgents.tsx:246` and `:292`
**Issue:** Closing WR-01 added a third and fourth copy of the literal `0`/`24` boundary check
(`DeskAgentService.java` already had one at line 311 in `setDayHours`; this pass added a matching
one at line 248 in `setContractedHours`; `DeskAgents.tsx` already had one in `saveDayHours` at
line 292; this pass added a matching one in `saveHours` at line 246). All four are independently
written boolean expressions with the same two magic numbers, two per language. This is intentional
duplication in service of the "mirror `setDayHours`" design language documented in this pass's own
code comments, and it is not a functional risk today — but a future change to the allowed range
(e.g. a per-tenant configurable max) would require finding and updating all four sites by hand, and
nothing enforces that they stay in sync.
**Fix:** Not blocking. If the range is ever expected to change, extract a
`MAX_CONTRACTED_HOURS_PER_DAY = 24` constant in a shared location (e.g. `BigDecimals` or a new
`DayHoursValidation` utility) on the Java side, and a `MAX_DAY_HOURS = 24` constant near
`DAY_ORDER` on the TypeScript side, and have all four call sites reference it.

---

## Original Review (13-01…13-04) — 2026-08-21

**Files Reviewed:** 16
**Status at time of writing:** issues_found (3 Warning, 2 Info)

### Summary

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

Three gaps remained at the time of this pass, all around the edges of the write paths rather than the
phase's central read-path claim, plus two lower-severity quality notes. **All three Warnings are now
CLOSED — see the Gap-Closure Pass section above.**

### Warnings (historical — see disposition)

#### WR-01: Bulk fan-out (`setContractedHours`) has no upper-bound validation, unlike the new per-cell endpoint

**Disposition: CLOSED by `ab0af9d` (server) and `974dc25` (client). See Gap-Closure Pass above.**

**File:** `src/main/java/com/wfm/service/DeskAgentService.java:244-248` (as it stood at review time)
**Issue:** The new per-cell endpoint (`setDayHours`) correctly implements "reject, not clamp" for
`0–24` (P-04). The pre-existing bulk fan-out this phase keeps alive as the explicit "Set all days
to…" action only rejects negative values. There was no upper-bound check at all.

#### WR-02: `PUT .../day-hours/{day}` returns 500, not the claimed 400, for a malformed `day` segment

**Disposition: CLOSED by `534e9ed`. See Gap-Closure Pass above.**

**File:** `src/main/java/com/wfm/controller/DeskAgentController.java:84-91`, `src/main/java/com/wfm/controller/GlobalExceptionHandler.java`
**Issue:** `GlobalExceptionHandler` had no handler for `MethodArgumentTypeMismatchException`, so an
unrecognised `day` path segment fell through to the generic `Exception.class` handler and returned
500 with "An unexpected error occurred," not the 400 that 13-02-PLAN.md's Task 2 `<action>` text
claimed. **Residual note (accepted, tracked separately):** the closing test is a direct handler
unit test, not a `@WebMvcTest` — this codebase has zero MockMvc tests and a `TenantFilter` in the
web layer, so Spring's actual dispatch to this handler for a real HTTP request is still not proven
end-to-end. Tracked as `.planning/WINDOWS.md` item 7, not re-raised here as a new finding.

#### WR-03: `DeskAgentService.setContractedHours` is missing test coverage for the upper-bound gap

**Disposition: CLOSED by `6cc76f7`. See Gap-Closure Pass above.**

**File:** `src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java`
**Issue:** `DeskAgentServiceContractedHoursTest` had `setContractedHours_negative_isRejected` but no
`setContractedHours_above24_isRejected` counterpart.

### Info (still open — not touched by the gap-closure pass)

#### IN-01: New expand/edit affordances are not keyboard-operable

**File:** `frontend/src/pages/DeskAgents.tsx:495-508` (expand/collapse toggle), `:586` (per-day cell click)
**Issue:** The expand/collapse control and each per-day cell are plain `<span onClick=...>` elements
with no `role="button"`, `tabIndex`, or `onKeyDown` handler, so a keyboard-only user cannot expand a
row or open the per-cell editor at all — only `aria-label` is present, which announces the control to
a screen reader without making it operable. This mirrors the file's pre-existing convention elsewhere
(e.g. `startEditSpec`), so it isn't a regression unique to this phase, but this phase substantially
grows the amount of primary functionality gated behind that pattern (all per-day editing). Still
present, unaddressed by the gap-closure commits.
**Fix:** Not blocking for this phase given the existing convention, but worth a follow-up pass adding
`role="button"` + `tabIndex={0}` + Enter/Space key handling to the new interactive spans.

#### IN-02: Every cell blur fires a `PUT`, even with no change

**File:** `frontend/src/pages/DeskAgents.tsx:279-312` (`saveDayHours`, line numbers shifted by this
phase's edits but the behavior is unchanged)
**Issue:** `saveDayHours` runs unconditionally on blur/Enter with no dirty-check against the seeded
value, so clicking a cell to look at it and then clicking away (with no edit) still issues a
`PUT .../day-hours/{day}` request. The write is idempotent (same row, same values) so there is no
correctness impact, but it is unnecessary traffic on every stray click in the expanded row. Now that
the "not set" cell seeds with the literal `'Not set (default)'` instead of blank text (this pass's
E4 fix), an unedited blur on a not-set cell issues a `clearRow: true` delete request against a row
that was never there — harmless (no-op per `setDayHours_notSet_onAbsentRow_isANoOp`), but worth
noting the fix slightly increases how often this no-op fires. Still present, unaddressed by the
gap-closure commits.
**Fix:** Compare `editCellValue` against the value `seedValueForEntry` produced before calling
`deskAgents.setDayHours`, and skip the request when unchanged. Not blocking — noted for polish only.

---

_Reviewed: 2026-08-24 (gap-closure pass), 2026-08-21 (original pass)_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
