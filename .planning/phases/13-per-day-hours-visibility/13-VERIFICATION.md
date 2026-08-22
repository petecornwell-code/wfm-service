---
phase: 13-per-day-hours-visibility
verified: 2026-08-22T01:30:00Z
status: gaps_found
score: 50/58 must-haves verified
behavior_unverified: 2
overrides_applied: 0
gaps:
  - truth: "E1 empty: zero agent_day_hours rows renders the single resolved schedule default in the Muted/not-set colour, never blank (13-UI-SPEC.md E1)"
    status: failed
    reason: "The collapsed Hours/Day summary text is rendered with no conditional styling at all — only the chevron glyph is coloured #9ca3af. When every weekday is 'not set', formatHoursSummary(da)'s output still renders in the default body colour, not muted #9ca3af as the UI-SPEC explicitly requires (E1 empty row, marked 'resolved / explicit', not backstop)."
    artifacts:
      - path: "frontend/src/pages/DeskAgents.tsx"
        issue: "Line 506 renders {formatHoursSummary(da)} directly inside the toggle <span> with no muted-colour wrapper or conditional style keyed off `DAY_ORDER.every(d => !da.dayHours[d].hasRow)`"
    missing:
      - "Wrap the collapsed summary value in a conditional style (color: '#9ca3af') when every dayHours entry has hasRow === false, matching the E3 not-set treatment already implemented for the expanded per-day cells"
  - truth: "E4 empty: the 'empty' value is the explicit 'Not set (default)' picklist entry, never a blank or unstyled input (13-UI-SPEC.md E4)"
    status: failed
    reason: "seedValueForEntry() returns the empty string '' for a not-set weekday (hasRow === false), so clicking to edit a not-set cell opens a blank <input> showing only the '(type a number)' placeholder — not the literal 'Not set (default)' datalist entry the must-have and 13-UI-SPEC.md E4 'empty' row both require. This is a genuine implementation gap, not a documentation-only slip: 13-04-PLAN.md's own <action> prose ('the empty string when hasRow is false') directly contradicts its own must_haves truth and the UI-SPEC it cites, and the executor followed the action text rather than the truth/spec."
    artifacts:
      - path: "frontend/src/pages/DeskAgents.tsx"
        issue: "seedValueForEntry (lines 25-30) returns '' instead of the literal 'Not set (default)' for entry.hasRow === false"
    missing:
      - "Change seedValueForEntry's not-set branch to return the literal string 'Not set (default)' so the input seeds with the picklist entry instead of opening blank; saveDayHours already treats that literal as clearRow so no downstream change is needed"
deferred: []
behavior_unverified_items:
  - truth: "The bulk fan-out remains a single transaction, so a mid-way failure never leaves a partial 3-of-7 write visible (13-UI-SPEC.md Section 4) [13-02 must_haves]"
    test: "Force a failure partway through setContractedHours' seven-row recreate loop (e.g. a constraint violation on row 4 of 7) and assert zero of the seven new rows persist after rollback"
    expected: "All seven original rows remain unchanged (or all seven new rows exist) — never a 3-of-7 mixed state"
    why_human: "Only `setContractedHours_isTransactional` exists, and it reflects on the @Transactional annotation via getMethod(...).getAnnotation(...) — that proves the annotation is present, not that a genuine mid-loop failure actually rolls back. No test injects a failure partway through the recreate loop."
  - truth: "E5 error: a bulk failure surfaces as an error toast with zero rows changed, because the backend fan-out is a single transaction (13-UI-SPEC.md E5) [13-04 must_haves]"
    test: "Stop the backend (or force a 500) mid-'Set all days to…' Apply and confirm the error toast reads via getErrorMessage(err), the editor stays open with the typed value intact, and none of the seven weekday cells change"
    expected: "Toast appears, editor remains open, zero cells change value"
    why_human: "This is the same backend rollback invariant as above, observed end-to-end through the frontend catch block; WINDOWS.md item 6 records this human-check as not run in this session (no live desk available)."
coincidental_reliance_items:
  - truth: "E1 long-text: the collapsed summary is a numeric closed vocabulary of at most 5 characters and cannot truncate (13-UI-SPEC.md E1)"
    reason: undeclared-precondition
    harden: "This holds only because every value formatHours() ever receives is currently bounded to [0, 24] by setDayHours' server-side range check. The bulk fan-out (setContractedHours) has no upper-bound check at all (WR-01 in 13-REVIEW.md — only a negative-value check exists), so a value like 999.99 written through the bulk endpoint would render as a 6+ character token, breaking this truth. Harden by adding the same 0-24 upper-bound rejection to setContractedHours that setDayHours already has (13-REVIEW.md's own suggested fix for WR-01)."
---

# Phase 13: Per-Day Hours Visibility Verification Report

**Phase Goal:** An operator who uploads Mon–Sun contracted hours can see exactly what the system stored — the roster and the Excel export resolve effective hours from the authoritative `agent_day_hours` model rather than the retired `Agent.contractedHoursPerDay` scalar, and per-weekday hours, `MANDATORY` days and `PTO` markers are visible in the UI.
**Verified:** 2026-08-22T01:30:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Summary

The read-path migration (I-1/F-1), the structurally-safe per-cell write path (I-3/D-05), and the
specialty-header single-sourcing (I-4/D-08) all hold up under direct source inspection and
independently re-run targeted test suites — this is the strong core of the phase and it is real,
not just claimed. Two UI-level must-haves from the phase's own `must_haves.truths` do **not** match
the shipped code, despite `npm run build` passing and both SUMMARY.md files reporting the tasks as
complete: the collapsed roster cell never applies the required Muted/not-set colour, and the per-cell
editor seeds a blank input instead of the required `Not set (default)` picklist literal for a
not-set weekday. Both are silent UI-behavior misses that green type-checking and code review did not
catch (13-REVIEW.md does not mention either). Independently, the code review's WR-01/WR-02 findings
are real and corroborated by direct inspection of `GlobalExceptionHandler.java` and
`DeskAgentService.setContractedHours` — reported here as a coincidental-reliance flag and a
non-blocking advisory, respectively, since neither maps onto a declared must-have truth on its own.

## Goal Achievement

### Observable Truths — Plan 13-01 (Read path + collapsed/expanded roster UI)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Roster shows stored per-weekday values, not the retired scalar/desk default | ✓ VERIFIED | `DeskAgentService.toResponse` builds `dayHours` from `dayRows.containsKey(day)`; `rosterIgnoresScalar_whenScalarDisagreesWithPerDayRows` test passes (re-run, green) |
| 2 | Absent weekday resolves to schedule default, not desk default | ✓ VERIFIED | `resolveScheduleDefault` reads `ScheduleRepository.findByTenantIdAndDeskIdOrderByCreatedAtDesc`; tests `absentWeekdayRow_resolvesToScheduleDefault_notDeskDefault`, `noPersistedSchedule_fallsBackToEightHours`, `mostRecentlyCreatedScheduleWins` pass |
| 3 | Explicit hours=0.00/dayOffType=null is distinguishable from absent | ✓ VERIFIED | `explicitZeroRow_isNotTreatedAsNotSet` test passes; `DayCell` branch 3 (`#6b7280`) distinct from branch 5 (`#9ca3af`) |
| 4 | MANDATORY weekday reported with label, not bare 0 | ✓ VERIFIED | `mandatoryAndPtoRows_keepTheirLabels` test; `DayCell` branch 1 renders `MAND` badge |
| 5 | PTO weekday reported with label, not bare 0 | ✓ VERIFIED | same test; `DayCell` branch 2 renders `PTO` badge |
| 6 | Exactly 7 dayHours entries, keyed MONDAY..SUNDAY | ✓ VERIFIED | `dayHoursMapAlwaysHasSevenEntries` test; `EnumMap<DayOfWeek,...>` built from `EnrichedColumnLayout.DAY_ORDER` |
| 7 | One agent_day_hours query per desk, no N+1 | ✓ VERIFIED | `loadDayHoursByAgent` called once outside `agents.stream()` in `listDeskAgentResponses`; confirmed by direct code read and 13-REVIEW.md |
| 8 | E1 empty: default rendered in Muted colour, never blank | ✗ **FAILED** | See `gaps` — collapsed cell text has no conditional muted styling |
| 9 | E1 loading: value in existing GET, no skeleton/spinner | ✓ VERIFIED | No new fetch added; `dayHours` rides the existing `deskAgents.list` response |
| 10 | E1 error: existing `loadAgents` catch → `showToast('error')`, no per-cell error state | ✓ VERIFIED | `loadAgents` unchanged (lines 138-148); no per-cell error state introduced for the collapsed cell |
| 11 | E1 populated: single value or min-max range, never MAND/PTO | ✓ VERIFIED | `formatHoursSummary` uses only `effectiveHours` (numeric), never `dayOffType` |
| 12 | E1 partial: unset days resolve to default before min/max | ✓ VERIFIED | `effectiveHours` already carries the resolved default before `formatHoursSummary` runs |
| 13 | E1 long-text: numeric closed vocabulary, ≤5 chars, no truncation | ✓ VERIFIED (coincidental-reliance) | Holds today because inputs are bounded 0-24 via `setDayHours`; see `coincidental_reliance_items` — the bulk endpoint's missing upper bound (WR-01) is an undeclared precondition this relies on |
| 14 | Expandable row, no new top-level column | ✓ VERIFIED | `<tr><td colSpan={14}>` — table header still has 14 `<th>` cells, no column added |
| 15 | E2 empty: chevron renders even with zero rows | ✓ VERIFIED | Chevron JSX is unconditional, not gated on `dayHours` data |
| 16 | E2 loading: no secondary fetch, no spinner on toggle | ✓ VERIFIED | `setExpandedAgentId` is pure client state; no fetch call in the handler |
| 17 | E2 populated: ▸/▾ glyphs with agent-named aria-label | ✓ VERIFIED | Lines 500-505 match exactly |
| 18 | E3 empty: "No per-day hours uploaded" note + all-muted cells | ✓ VERIFIED | Line 543 gate + `DayCell` branch 5 for every entry when all `hasRow` false |
| 19 | E3 loading: synchronous render from loaded data, no spinner | ✓ VERIFIED | Expanded `<tr>` derives purely from `da.dayHours`, already in state |
| 20 | E3 error: no independent error state | ✓ VERIFIED | No such state exists in the expanded row |
| 21 | E3 partial: 5-state priority order (MANDATORY→PTO→zero→worked→not-set) preserved | ✓ VERIFIED | `DayCell` branch order matches exactly |
| 22 | E3 long-text: fixed badge text; only the empty-state note wraps | ✓ VERIFIED | `MAND`/`PTO` are fixed literals; empty-state `<div>` has no truncation CSS |
| 23 | E1 overflow (backstop) | ? insufficient_spec | Range-length visual claim; abstain per honest-verifier contract — routes to human review |
| 24 | E3 populated (backstop) | ? insufficient_spec | Mixed-state row rendering vs. mockup; abstain — routes to human review |
| 25 | E3 overflow (backstop) | ? insufficient_spec | Horizontal-scroll behavior claim; abstain — routes to human review |

### Observable Truths — Plan 13-02 (Per-cell backend write path)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Single-weekday edit touches exactly one row, leaves other six byte-identical | ✓ VERIFIED | `setDayHours_numeric_touchesExactlyOneRow` captures and re-asserts 6 rows' id/hours/dayOffType |
| 2 | MANDATORY/PTO stores hours=0.00 with label, matching parser encoding | ✓ VERIFIED | `setDayHours_mandatory_storesZeroWithLabel`, `setDayHours_pto_storesZeroWithLabel` |
| 3 | "Not set" deletes the row rather than writing zero | ✓ VERIFIED | `setDayHours_notSet_deletesOnlyThatRow`, `setDayHours_notSet_onAbsentRow_isANoOp` |
| 4 | Out-of-range / bad-precision edit rejected 400, persists nothing | ✓ VERIFIED | `setDayHours_negative_isRejectedAndPersistsNothing`, `setDayHours_above24_isRejectedAndPersistsNothing`, `setDayHours_normalizesToScaleTwo` |
| 5 | Cross-tenant/cross-desk edit rejected before any AgentDayHoursRepository call | ✓ VERIFIED | Code orders `agentRepository.findByIdAndTenantIdAndDeskId` before any day-hours call; `setDayHours_foreignTenantAgent_throwsEntityNotFound` passes |
| 6 | Per-cell response reflects the row just written | ✓ VERIFIED | `setDayHours_responseReflectsTheJustWrittenRow` |
| 7 | Bulk response reflects the seven rows just written | ✓ VERIFIED | `setContractedHours_responseCarriesTheSevenJustWrittenRows` |
| 8 | Bulk fan-out is a single transaction, no partial 3-of-7 write | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | Only `@Transactional` annotation presence is tested (`setContractedHours_isTransactional` via reflection); no test forces a mid-loop failure and observes rollback |

### Observable Truths — Plan 13-03 (Excel export + template)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Export carries 7 Mon-Sun columns after "Effective Contracted Hours Per Day" | ✓ VERIFIED | `headerRow_matchesTheFullExpectedOrder`; code inserts `EnrichedColumnLayout.dayHeader` entries at index 13-19 |
| 2 | MANDATORY weekday exports the keyword, not 0 | ✓ VERIFIED | `mandatoryWeekday_writesTheKeywordNotZero`; `writeDayCells` branch 2 |
| 3 | PTO weekday exports the keyword, not 0 | ✓ VERIFIED | `ptoWeekday_writesTheKeywordNotZero`; branch 3 |
| 4 | Explicit hours=0.00 exports numeric 0, distinct from label | ✓ VERIFIED | `explicitZeroWeekday_writesNumericZero`; branch 4 |
| 5 | No stored row exports resolved effective value, never blank | ✓ VERIFIED | `notSetWeekday_writesTheResolvedEffectiveValue_notBlank`; branch 5 (P-08) |
| 6 | Effective Contracted Hours column derives from agent_day_hours, not scalar | ✓ VERIFIED | `effectiveContractedHoursColumn_reflectsThePerDayModel` |
| 7 | Blank-template specialty headers sourced from EnrichedColumnLayout | ✓ VERIFIED | `specialtyHeader_matchesTheDetectionRegex`; `SPECIALTY_1_HEADER`/`SPECIALTY_2_HEADER` constants removed (grep = 0) |
| 8 | No weekday/specialty string literal outside EnrichedColumnLayout | ✓ VERIFIED | `grep -Ec '"(Monday|...|Sunday)"'` on `DeskAgentExportService.java` = 0 |

### Observable Truths — Plan 13-04 (Per-cell + bulk editing UI)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Operator can set a weekday to number/PTO/MANDATORY/not-set in the expanded row | ✓ VERIFIED | `saveDayHours` handles all four branches, calling `deskAgents.setDayHours` |
| 2 | "Not set (default)" or clearing deletes the row, not a zero write | ✓ VERIFIED | `raw === '' \|\| raw === 'Not set (default)'` → `{clearRow: true}` |
| 3 | Bulk editor is explicitly labelled "Set all days to…", not the collapsed cell's click | ✓ VERIFIED | Collapsed cell click only toggles `expandedAgentId`; the bulk control is a distinct button inside the expanded row |
| 4 | Bulk confirm() fires only when ≥1 weekday carries a label, never merely because rows exist | ✓ VERIFIED | `labelledDayCount = DAY_ORDER.filter(d => da.dayHours[d].dayOffType !== null).length`; confirm only when `labelledDayCount > 0` |
| 5 | E4 empty: "empty" value is the explicit "Not set (default)" picklist entry, never blank | ✗ **FAILED** | See `gaps` — `seedValueForEntry` returns `''`, not the literal `'Not set (default)'` |
| 6 | E4 loading: input disables during PUT, no spinner | ✓ VERIFIED | `disabled={savingCell}` on the input |
| 7 | E4 error: client range check first, server error via "Couldn't save {Weekday} — {reason}" toast | ✓ VERIFIED | Exact copy present in `saveDayHours`'s catch block and the client-side numeric guard |
| 8 | E4 partial: explicit-zero colour distinct from muted not-set colour, editable | ✓ VERIFIED | `DayCell` branches 3/5 use `#6b7280`/`#9ca3af` respectively; both are inside the clickable cell |
| 9 | E4 long-text: numeric validated before submit, no free text reaches the cell | ✓ VERIFIED | Numeric parse + 0-24 range check before any request is sent |
| 10 | E5 empty: bulk input opens blank, Apply inert until value chosen | ✓ VERIFIED | `editHours` seeded `''`; `disabled={editHours.trim() === '' \|\| applyingBulk}` |
| 11 | E5 loading: Apply disables during PUT | ✓ VERIFIED | same `applyingBulk` flag |
| 12 | E5 error: failure surfaces as toast, zero rows changed (transactional) | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | Same underlying rollback claim as 13-02 item 8 — no failure-injection test proves "zero rows changed" end to end |
| 13 | E5 populated: amber notice (#fffbeb/#fde68a/#92400e) always visible above input | ✓ VERIFIED | Lines 595-597 render the notice unconditionally whenever the bulk editor is revealed |
| 14 | E5 partial: confirm() triggered by label presence, not row existence | ✓ VERIFIED | Same as truth 4 above |
| 15 | E5 overflow: single-line row at 12px spacing, no overflow path | ✓ VERIFIED (structural) | `<div style={{marginTop: '12px'}}>` wraps a single flex row with no wrapping elements |
| 16 | E5 zero-one-many: applies to exactly 7 days, "{N} day(s)" copy | ✓ VERIFIED | Confirm text literally uses the fixed `day(s)` string, count from `labelledDayCount` (0-7) |
| 17 | E4 overflow (backstop) | ? insufficient_spec | Native-datalist-dropdown-width claim; abstain — routes to human review |

**Score:** 50/58 truths verified (2 present-but-behavior-unverified, 4 backstop/insufficient_spec, 2 FAILED)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/dto/DeskAgentResponse.java` | `DayHoursEntry` record + `dayHours` map component | ✓ VERIFIED | Present exactly as specified, both components confirmed by direct read |
| `src/main/java/com/wfm/service/DeskAgentService.java` | Per-desk bulk fetch, schedule fallback, per-weekday mapping | ✓ VERIFIED | `resolveScheduleDefault`, `loadDayHoursByAgent`, `setDayHours`, `upsertDayHoursRow` all present and wired into all 4 `toResponse` call sites |
| `src/test/java/com/wfm/service/DeskAgentServiceReadPathTest.java` | 8-test read-path suite, ≥120 lines | ✓ VERIFIED | 225 lines, 8 `@Test` methods, all pass |
| `frontend/src/pages/DeskAgents.tsx` | Collapsed summary + expandable detail row | ✓ VERIFIED | Present, 709 lines, `dayHours` referenced 7 times |
| `src/main/java/com/wfm/repository/AgentDayHoursRepository.java` | `findByAgent_IdAndDayOfWeek` | ✓ VERIFIED | Present, not tenant-scoped by signature as documented |
| `src/main/java/com/wfm/dto/SetDayHoursRequest.java` | `record SetDayHoursRequest` | ✓ VERIFIED | One-line record, all 3 components nullable |
| `src/main/java/com/wfm/controller/DeskAgentController.java` | `PUT .../day-hours/{day}` | ✓ VERIFIED | Present, delegates to `setDayHours`; bulk endpoint survives unchanged |
| `src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java` | 13-test suite, ≥140 lines | ✓ VERIFIED | 247 lines, 13 `@Test` methods, all pass |
| `src/main/java/com/wfm/util/EnrichedColumnLayout.java` | `specialtyHeader(int)` | ✓ VERIFIED | Present alongside `dayHeader` |
| `src/main/java/com/wfm/service/DeskAgentExportService.java` | 7 Mon-Sun export columns from EnrichedColumnLayout | ✓ VERIFIED | `writeDayCells` present, columns array built programmatically |
| `src/test/java/com/wfm/service/DeskAgentExportServiceTest.java` | First test file for the export, ≥130 lines | ✓ VERIFIED | 241 lines, 9 `@Test` methods, all pass |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `DeskAgentService.java` | `AgentDayHoursRepository.java` | bulk per-desk fetch | ✓ WIRED | `agentDayHoursRepository.findByTenantIdAndDeskId(` present, called once in `loadDayHoursByAgent` |
| `DeskAgentService.java` | `ScheduleRepository.java` | D-06 fallback | ✓ WIRED | `findByTenantIdAndDeskIdOrderByCreatedAtDesc` present and used in `resolveScheduleDefault` |
| `DeskAgents.tsx` | `client.ts` | `dayHours` consumption | ✓ WIRED | `dayHours[` referenced 7 times across summary, chevron, and expanded-grid rendering |
| `DeskAgentController.java` | `DeskAgentService.java` | PUT delegates to `setDayHours` | ✓ WIRED | `deskAgentService.setDayHours(` present in controller |
| `DeskAgentService.java` | `AgentDayHoursRepository.java` | single-weekday upsert/delete | ✓ WIRED | `findByAgent_IdAndDayOfWeek` used 3× (upsert read, clearRow delete-check) |
| `DeskAgentExportService.java` | `EnrichedColumnLayout.java` | header text + ordering | ✓ WIRED | `EnrichedColumnLayout.dayHeader`/`DAY_ORDER` used, 3 occurrences |
| `DeskAssignmentTemplateService.java` | `EnrichedColumnLayout.java` | specialty header text | ✓ WIRED | `EnrichedColumnLayout.specialtyHeader(1)`/`(2)` present, 2 occurrences |
| `DeskAgentExportService.java` | `DeskAgentResponse.java` | per-weekday export values | ✓ WIRED | `agent.dayHours()` read in `writeDayCells` |
| `DeskAgents.tsx` (per-cell) | `client.ts` | `deskAgents.setDayHours` call | ✓ WIRED | 1 call site in `saveDayHours` |
| `client.ts` | `DeskAgentController.java` | `PUT .../day-hours/{day}` | ✓ WIRED | Path fragment `day-hours/` present in both files |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend targeted suites re-run clean | `./gradlew test --tests "com.wfm.service.DeskAgentServiceReadPathTest" --tests "com.wfm.service.DeskAgentServiceDayHoursTest" --tests "com.wfm.service.DeskAgentServiceContractedHoursTest" --tests "com.wfm.service.DeskAgentExportServiceTest" --tests "com.wfm.service.DeskAssignmentTemplate*"` | `BUILD SUCCESSFUL` | ✓ PASS |
| Frontend type-check + build re-run clean | `npm --prefix frontend run build` | `tsc -b && vite build` exits 0, bundle produced | ✓ PASS |
| No desk-level default reference remains in resolution path | `grep -Ec 'Desk::getDefaultContractedHoursPerDay\|desk\.getDefaultContractedHoursPerDay' DeskAgentService.java` | `0` | ✓ PASS |
| No weekday-name string literal in export | `grep -Ec '"(Monday\|...\|Sunday)"' DeskAgentExportService.java` | `0` | ✓ PASS |
| No specialty-header constants remain | `grep -c 'SPECIALTY_[12]_HEADER' DeskAssignmentTemplateService.java` | `0` | ✓ PASS |
| No destructive red introduced | `grep -c '#ef4444' DeskAgents.tsx` | `0` | ✓ PASS |
| Full clean-rebuild suite (confirmed by orchestrator, not re-run here to avoid duplicate full-suite execution) | `./gradlew test --rerun-tasks` | 309 tests, 0 failures | ✓ PASS (relied upon per task instructions) |

### Requirements Coverage

| Requirement | Description (abridged) | Status | Evidence |
|-------------|------------------------|--------|----------|
| MDL-02 | Agent stores contracted hours per day of week; effective hours resolve per date from per-day values | ✓ SATISFIED | Read path fully migrated off the scalar (13-01); export half also migrated (13-03) |
| UPL-03 | Numeric day cell ≥0 is contracted hours; 0 marks a non-working day, never blank | ✓ SATISFIED | Explicit-zero distinguishable from not-set throughout read, write, and export paths |
| UPL-04 | MANDATORY day cell marks a mandatory day off | ✓ SATISFIED (UI polish gap noted) | Label surfaces correctly in expanded grid, export, and edit path; see FAILED truths for two collapsed/edit-seed cosmetic gaps that do not affect the underlying label semantics |
| UPL-05 | PTO day cell marks recurring weekly PTO | ✓ SATISFIED (UI polish gap noted) | Same as UPL-04 |
| UPL-09 | Template/parser/export share one column-layout definition | ✓ SATISFIED | `EnrichedColumnLayout.specialtyHeader`/`dayHeader` now the sole source for both template and export |

No orphaned requirements — Phase 13 introduces no new requirement IDs and REQUIREMENTS.md attributes MDL-02/UPL-03/04/05/09 to their originating phases (9/10), consistent with this phase's "closes audit gaps" framing.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `src/main/java/com/wfm/service/DeskAgentService.java` | 244-248 | Bulk fan-out (`setContractedHours`) rejects negative hours but has no upper-bound check (unlike `setDayHours`) | ⚠️ Warning (WR-01, 13-REVIEW.md) | A value like 1000 typed into the new "Set all days to…" control (no client-side range check beyond emptiness) reaches the DB layer and fails with an opaque 500 rather than a clean 400. Not a data-loss risk (transactional), but a poor failure mode this phase's own new UI made directly reachable. Feeds the `coincidental-reliance` flag on the E1 long-text truth above. |
| `src/main/java/com/wfm/controller/DeskAgentController.java` / `GlobalExceptionHandler.java` | 84-91 / whole file | No `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` | ⚠️ Warning (WR-02, 13-REVIEW.md, independently confirmed by direct read) | `PUT .../day-hours/{day}` with a malformed `day` segment (e.g. lowercase or an unrecognised token) falls through to the generic `Exception.class` handler and returns 500, not the 400 that 13-02-PLAN.md's Task 2 `<action>` text explicitly (and incorrectly) claims. This does not map to any declared must_have truth — it is not reachable through the shipped UI, which only ever sends valid `DAY_ORDER` values — but it is a real, unfixed defect worth tracking (already is, in `13-REVIEW.md` and implicitly covered by WINDOWS.md item 4's "unrun-verify" on controller HTTP behavior). |
| `src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java` | — | Missing `setContractedHours_above24_isRejected` counterpart | ℹ️ Info (WR-03, 13-REVIEW.md) | The missing-test evidence behind WR-01. |
| `frontend/src/pages/DeskAgents.tsx` | 495-508, 586 | Expand/collapse toggle and per-day cells are plain `<span onClick>` with no `role="button"`/`tabIndex`/`onKeyDown` | ℹ️ Info (IN-01, 13-REVIEW.md) | Keyboard users cannot operate the new per-day editing surface. Matches a pre-existing file convention, not a regression, but this phase substantially grows what's gated behind it. |
| `frontend/src/pages/DeskAgents.tsx` | 267-300 | Every cell blur fires a PUT even with no change (`saveDayHours` has no dirty-check) | ℹ️ Info (IN-02, 13-REVIEW.md) | Idempotent, so no correctness impact — unnecessary traffic only. |

No TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER debt markers found in any of the 10 files this phase touched.

### Human Verification Required

Even though this phase's status is `gaps_found` (which takes precedence per the verifier's decision
tree), the following items should still be exercised once the two FAILED truths above are fixed —
carried forward from `.planning/WINDOWS.md`'s 5 open `unrun-verify` entries, this report's 4 backstop
truths, and the 2 behavior-unverified transactional-rollback claims:

### 1. Live-desk visual walkthrough of the collapsed summary and expand/collapse chevron

**Test:** Open a desk roster that has had an enriched upload; observe the Hours/Day column for
agents with uniform hours, mixed hours, MANDATORY/PTO days, and zero uploaded rows.
**Expected:** Single value or min-max range never shows MAND/PTO; chevron toggles ▸/▾; the "12-24
worst case" range never disturbs the 13-column row layout (WINDOWS.md item 1).
**Why human:** Visual layout and overflow behavior in a real browser against real data.

### 2. Live-desk walkthrough of the expanded 7-day grid and its five display states

**Test:** Expand a row with a mix of MANDATORY, PTO, explicit-zero, worked, and not-set weekdays.
**Expected:** All 5 states are visually distinct, matching the CONTEXT.md mockup (WINDOWS.md item 2).
**Why human:** Requires a live DB/BambooHR-configured environment not available in the executor session.

### 3. `PUT .../day-hours/{day}` HTTP-level behavior

**Test:** Call the endpoint directly with valid and invalid `hours` values and a malformed `day`
segment.
**Expected:** 200 with fresh body on valid input; 400 on out-of-range hours; ideally 400 (not 500)
on a malformed day segment — currently returns 500 per WR-02 above.
**Why human:** No `@WebMvcTest`/`MockMvc` harness exists for `DeskAgentController` in this codebase
(WINDOWS.md item 4).

### 4. Per-cell type-or-pick combo and bulk "Set all days to…" walkthroughs

**Test:** Full per-cell combo flow (number/PTO/MANDATORY/not-set/out-of-range/offline-failure) and
the bulk action's no-dialog vs. labelled-dialog paths.
**Expected:** Matches 13-04-PLAN.md's `<human-check>` blocks in full.
**Why human:** No live desk with an enriched upload was available in the executor session
(WINDOWS.md items 5 and 6). Note that item 1 in this walkthrough (typing `4.5` on a not-set cell)
will currently also surface the E4-empty gap above — the cell opens blank rather than showing
"Not set (default)".

### 5. Transactional rollback of the bulk fan-out under a genuine mid-write failure

**Test:** Force a failure partway through the seven-row recreate loop in `setContractedHours`
(e.g. a constraint violation on one row) and confirm zero rows change.
**Expected:** No 3-of-7 mixed state ever becomes visible, on either the backend response or the
roster UI.
**Why human:** Only annotation presence is tested; no failure-injection test exists (see
`behavior_unverified_items`).

## Prohibitions Disposition (descriptor-less, per-phase convention)

This phase's `must_haves.prohibitions` blocks were authored without `check_*` descriptor scalars
(the spec-less fallback), so per this phase's verification contract each disposes as
`{status: unverified, flagged: true}` regardless of its declared `verification: test` or
`verification: judgment` tier — this is the designed outcome, not a defect, and is recorded here so
it is never silently absorbed into a passing verdict.

| Plan | Requirement | Statement (abridged) | Disposition | Non-authoritative note |
|------|-------------|----------------------|--------------|------------------------|
| 13-01 | MDL-02 | MUST NOT present a resolved fallback default as an operator-supplied value | unverified, flagged | `hasRow` + distinct muted/italic styling with a "using schedule default" tooltip does distinguish it in the expanded grid — but the collapsed-cell FAILED truth above means the *collapsed* view currently does present the default with no visual distinction at all |
| 13-01 | MDL-02 | MUST NOT display an hours figure diverging from what the solver resolves for the same agent-weekday | unverified, flagged | `SolverService.resolveEffectiveHours` and `DeskAgentService.toResponse` use the same exception→per-day→schedule-default precedence, but the roster always reads the *newest* persisted schedule's default while the solver reads the *specific* schedule being solved — for a desk with multiple persisted schedules with different defaults, these can diverge. This is P-01's own documented residual gap, not a silent omission, but it is exactly the kind of divergence this prohibition exists to prevent |
| 13-02 | UPL-04 | MUST NOT silently discard a MANDATORY/PTO label as a side effect of an unrelated edit | unverified, flagged | `setDayHours_numeric_touchesExactlyOneRow` directly tests this exact property and passes — strong evidence exists, but per this phase's descriptor-less convention it is still recorded as flagged rather than auto-resolved |
| 13-03 | UPL-09 | MUST NOT change the export/template column set silently — must be pinned by a full-header-row assertion | unverified, flagged | `headerRow_matchesTheFullExpectedOrder` and `generateTemplate_headerRowMatchesEnrichedColumnLayoutPlusDaysAndSpecialties` both exist and pass — same note as above |
| 13-04 | UPL-05 | MUST NOT let a bulk action destroy labels without warning, and MUST NOT warn so indiscriminately operators learn to click through it | unverified, flagged | The label-count trigger (`labelledDayCount > 0`) matches the "carries a label, not merely exists" requirement in code — same note as above |

### Gaps Summary

Two of the phase's own declared UI must-haves are not met by the shipped code, despite a green
`npm run build`, a clean code review, and both plan SUMMARY.md files self-reporting completion:

1. **Collapsed Hours/Day cell never applies the Muted/not-set colour** for an agent with zero
   uploaded per-day hours (13-UI-SPEC.md E1 "empty", explicitly resolved, not backstop). The value
   is correct; only the required visual distinction is missing.
2. **The per-cell editor's "empty" seed value is a blank string, not the literal `Not set (default)`**
   picklist entry (13-UI-SPEC.md E4 "empty", explicitly resolved, not backstop). This traces to a
   genuine internal contradiction in 13-04-PLAN.md itself — its `<action>` prose says "the empty
   string when hasRow is false" while its own `must_haves.truths` and the UI-SPEC it cites both
   require the literal picklist text. The executor followed the action text; the acceptance
   contract (must_haves) says otherwise, so this is scored as a gap.

Neither gap breaks the phase's core read-path/export migration (I-1/F-1) or the structural per-cell
edit safety (I-3) — those are solidly proven by passing, re-run unit tests. Both gaps are small,
targeted UI fixes (see `missing` in the `gaps` frontmatter) that do not require replanning the
phase's architecture.

Separately, two behavior-dependent truths about the bulk fan-out's transactional rollback are
present and wired but not behaviorally proven (only annotation presence is tested), and five
`unrun-verify` human-check items remain open in `.planning/WINDOWS.md` from lack of a live
BambooHR-configured environment during execution. None of these block correctness on their own, but
they should be closed out before the v1.2 milestone completes.

---

_Verified: 2026-08-22T01:30:00Z_
_Verifier: Claude (gsd-verifier)_
