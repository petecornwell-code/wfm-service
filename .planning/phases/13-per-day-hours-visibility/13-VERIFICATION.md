---
phase: 13-per-day-hours-visibility
verified: 2026-08-24T13:15:24Z
status: human_needed
score: 54/58 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 50/58
  gaps_closed:
    - "E1 empty: zero agent_day_hours rows renders the single resolved schedule default in the Muted/not-set colour, never blank (13-UI-SPEC.md E1) — closed by 8f4f528"
    - "E4 empty: the 'empty' value is the explicit 'Not set (default)' picklist entry, never a blank or unstyled input (13-UI-SPEC.md E4) — closed by 208d5c0"
  gaps_remaining: []
  regressions: []
behavior_unverified_items: [] # both closed by dcc6e06 (see disposition below) — kept empty per contract; prior items detailed in Goal Achievement
coincidental_reliance_items: [] # the E1 long-text reliance is now hardened/declared, not coincidental — see disposition below
human_verification:
  - test: "Live-desk visual walkthrough of the collapsed summary muted colour and tooltip"
    expected: "Zero-row agent renders light-grey italic with 'Not set — using schedule default' tooltip; any agent with >=1 stored row renders in default body colour with no tooltip"
    why_human: "Visual colour/tooltip rendering in a real browser against real data — WINDOWS.md item 1"
  - test: "Live-desk walkthrough of the per-cell editor seeding the 'Not set (default)' literal and the expanded 7-day grid's five display states"
    expected: "Clicking a not-set cell opens the editor pre-filled with 'Not set (default)'; all 5 weekday states (MAND, PTO, explicit-zero, worked, not-set) are visually distinct"
    why_human: "Requires a live DB/BambooHR-configured environment — WINDOWS.md items 2 and 5"
  - test: "Live-desk walkthrough of the bulk 'Set all days to…' range guard and confirmation dialog"
    expected: "1000/-1 rejected via toast before confirm()/network; 24 and 0 accepted; label-count confirm() fires only when >=1 weekday carries MANDATORY/PTO"
    why_human: "End-to-end browser behaviour with no frontend test framework in this repo — WINDOWS.md item 6"
  - test: "PUT .../day-hours/{day} HTTP-level dispatch for a malformed {day} segment"
    expected: "400 (not 500), body names only the 'day' parameter, no rejected token or internal type leaked; valid/out-of-range day-hours paths and the bulk contracted-hours 400 message all unchanged; a genuine unexpected failure still returns 500"
    why_human: "No @WebMvcTest/MockMvc harness exists for DeskAgentController in this codebase, so Spring's actual dispatch to GlobalExceptionHandler.handleTypeMismatch for a real HTTP request is unit-tested but not integration-tested — WINDOWS.md item 7 (P-17 residual gap, declared not discovered)"
  - test: "E1 overflow (backstop): the collapsed summary's range output still fits the existing dense 13-column row after the muted-colour change"
    expected: "No wrap, no horizontal-scroll regression at the widest range value"
    why_human: "insufficient_spec — visual layout claim, abstain per honest-verifier contract"
  - test: "E3 populated (backstop): mixed-state weekday row rendering matches the CONTEXT.md mockup"
    expected: "All 5 states visually distinct in the expanded grid"
    why_human: "insufficient_spec — abstain per honest-verifier contract"
  - test: "E3 overflow (backstop): horizontal-scroll behaviour of the expanded 7-day grid"
    expected: "No layout break at the widest content"
    why_human: "insufficient_spec — abstain per honest-verifier contract"
  - test: "E4 overflow (backstop): the seeded 'Not set (default)' text in the browser's native datalist dropdown"
    expected: "Text is not clipped by the weekday mini-column width, since the datalist popup is not constrained by it"
    why_human: "insufficient_spec — abstain per honest-verifier contract"
---

# Phase 13: Per-Day Hours Visibility Verification Report

**Phase Goal:** An operator who uploads Mon–Sun contracted hours can see exactly what the system stored — the roster and the Excel export resolve effective hours from the authoritative `agent_day_hours` model rather than the retired `Agent.contractedHoursPerDay` scalar, and per-weekday hours, `MANDATORY` days and `PTO` markers are visible in the UI.
**Verified:** 2026-08-24T13:15:24Z
**Status:** human_needed
**Re-verification:** Yes — after gap closure (plans 13-05, 13-06)

## Summary

This is a re-verification following the prior run's `gaps_found` (50/58) report. Two plans
(13-05 UI fixes, 13-06 backend hardening + rollback proof) were written and executed specifically to
close every item that report recorded. **All five previously-recorded items are genuinely closed by
direct source inspection and independently re-run targeted tests — none of them merely claimed
closed by a SUMMARY.md.**

1. **Both `gaps:` entries (E1 empty, E4 empty) — CLOSED.** Read `frontend/src/pages/DeskAgents.tsx`
   directly: a new `isEveryDayNotSet(da)` predicate (line 50) drives a conditionally-styled `<span>`
   around the collapsed Hours/Day value (lines 518-525) that applies `color: '#9ca3af'` only when
   every weekday lacks a stored row, and the same predicate now also drives the expanded-row
   empty-state note (line 562), replacing the previously-duplicated inline check. `seedValueForEntry`
   (lines 23-30) now returns the literal `'Not set (default)'` for the not-set branch instead of
   `''`, and that literal is the exact string in the `<option>` backing the datalist (line 408) and
   the exact string `saveDayHours`'s clear-row branch already matched (line 288) — the round trip is
   genuinely closed, not just present.

2. **Both `behavior_unverified_items` (bulk transactional rollback) — CLOSED.** Read
   `src/test/java/com/wfm/service/DeskAgentServiceBulkRollbackTest.java` in full and ran it directly
   (`./gradlew test --tests DeskAgentServiceBulkRollbackTest` → 1 test, 0 failures). This is a genuine
   failure-injection test, not annotation reflection: it stubs `AgentDayHoursRepository.save` to throw
   only on the argument-matched THURSDAY row (the 4th of 7 in `DayOfWeek.values()`'s Monday-first
   order), runs under `@Transactional(propagation = NOT_SUPPORTED)` to defeat `@DataJpaTest`'s
   ambient rollback wrapper so `setContractedHours`'s own `@Transactional` genuinely commits/rolls
   back against H2, and asserts the post-failure row **ids** equal the pre-failure baseline ids — a
   property that could not pass by coincidence, since `setContractedHours` deletes and recreates rows
   with fresh generated ids on every call, so surviving-with-original-ids proves the preceding
   `deleteByAgent_Id` was itself rolled back, not just that the hours values happened to still read
   `6.00`. This is exactly the kind of behavioral evidence a state-transition/rollback truth requires
   (Step 3/Step 7b) — presence of `@Transactional` was never sufficient, and now isn't relied on.

3. **The `coincidental_reliance_items` entry (E1 long-text bound) — HARDENED, no longer coincidental.**
   `DeskAgentService.setContractedHours` (lines 244-249) now rejects the same inclusive `0–24` range
   `setDayHours` already enforced, closing the gap that let an unbounded bulk value reach the DB and
   (hypothetically) break the collapsed summary's 5-character assumption. The precondition the E1
   long-text truth depends on is now a declared, code-enforced invariant on both write paths into
   `agent_day_hours`, not an implicit assumption that happened to hold — so this is reclassified out
   of `coincidental_reliance_items` rather than merely re-flagged.

**Everything else from the prior pass was spot-checked for regression** (the untouched read path,
export path, and column-layout single-sourcing) — no file outside the six the gap-closure commits
touched shows any change, confirmed via `git show --stat` on all eight gap-closure commits.

**Why this report is `human_needed`, not `passed`:** the four `verification: backstop` /
`insufficient_spec` truths from the original pass (E1 overflow, E3 populated, E3 overflow, E4
overflow) remain unresolved — no evidence was added for them in this gap-closure round, and abstention
is the correct disposition per the honest-verifier contract, not a silent pass. Additionally, the
four still-open `unrun-verify` items in `.planning/WINDOWS.md` (1, 2, 5, 6) plus the new item 7 (the
`GlobalExceptionHandler.handleTypeMismatch` end-to-end HTTP dispatch, a declared residual per plan
13-06's own P-17 decision, not a newly discovered gap) all route to human verification below.

## Goal Achievement

### Observable Truths — Plan 13-01 (Read path + collapsed/expanded roster UI)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Roster shows stored per-weekday values, not the retired scalar/desk default | ✓ VERIFIED (regression-checked) | Unchanged since prior pass; `DeskAgentService.toResponse`, `git show --stat` on gap-closure commits confirms this file untouched |
| 2 | Absent weekday resolves to schedule default, not desk default | ✓ VERIFIED (regression-checked) | Unchanged; `resolveScheduleDefault` untouched by gap-closure commits |
| 3 | Explicit hours=0.00/dayOffType=null is distinguishable from absent | ✓ VERIFIED (regression-checked) | `DayCell` branches 3/5 unchanged, confirmed by direct re-read |
| 4 | MANDATORY weekday reported with label, not bare 0 | ✓ VERIFIED (regression-checked) | `DayCell` branch 1 unchanged |
| 5 | PTO weekday reported with label, not bare 0 | ✓ VERIFIED (regression-checked) | `DayCell` branch 2 unchanged |
| 6 | Exactly 7 dayHours entries, keyed MONDAY..SUNDAY | ✓ VERIFIED (regression-checked) | Unchanged |
| 7 | One agent_day_hours query per desk, no N+1 | ✓ VERIFIED (regression-checked) | Unchanged |
| 8 | E1 empty: default rendered in Muted colour, never blank | ✓ VERIFIED | **Was FAILED — now closed by `8f4f528`.** Direct re-read of `DeskAgents.tsx:518-525`: `isEveryDayNotSet(da)` gates a `color: '#9ca3af'` style on the collapsed summary `<span>`. `npm run build` clean |
| 9 | E1 loading: value in existing GET, no skeleton/spinner | ✓ VERIFIED (regression-checked) | Unchanged |
| 10 | E1 error: existing `loadAgents` catch → `showToast('error')`, no per-cell error state | ✓ VERIFIED (regression-checked) | Unchanged |
| 11 | E1 populated: single value or min-max range, never MAND/PTO | ✓ VERIFIED (regression-checked) | `formatHoursSummary` itself untouched — plan 13-05 explicitly did not touch it |
| 12 | E1 partial: unset days resolve to default before min/max | ✓ VERIFIED (regression-checked) | Unchanged |
| 13 | E1 long-text: numeric closed vocabulary, ≤5 chars, no truncation | ✓ VERIFIED — **now hardened, not coincidental** | See Summary item 3: `setContractedHours` now enforces the same 0-24 bound as `setDayHours` (`DeskAgentService.java:244-249`), confirmed by direct read and passing `setContractedHours_above24_isRejectedAndPersistsNothing`/`setContractedHours_exactly24_isAccepted` |
| 14 | Expandable row, no new top-level column | ✓ VERIFIED (regression-checked) | `colSpan={14}` unchanged |
| 15 | E2 empty: chevron renders even with zero rows | ✓ VERIFIED (regression-checked) | Unchanged |
| 16 | E2 loading: no secondary fetch, no spinner on toggle | ✓ VERIFIED (regression-checked) | Unchanged |
| 17 | E2 populated: ▸/▾ glyphs with agent-named aria-label | ✓ VERIFIED (regression-checked) | Unchanged |
| 18 | E3 empty: "No per-day hours uploaded" note + all-muted cells | ✓ VERIFIED | Note's gating condition now reads `isEveryDayNotSet(da)` (line 562) instead of the prior duplicated inline check — same behaviour, single source, confirmed by direct read |
| 19 | E3 loading: synchronous render from loaded data, no spinner | ✓ VERIFIED (regression-checked) | Unchanged |
| 20 | E3 error: no independent error state | ✓ VERIFIED (regression-checked) | Unchanged |
| 21 | E3 partial: 5-state priority order (MANDATORY→PTO→zero→worked→not-set) preserved | ✓ VERIFIED (regression-checked) | `DayCell` untouched by gap-closure commits |
| 22 | E3 long-text: fixed badge text; only the empty-state note wraps | ✓ VERIFIED (regression-checked) | Unchanged |
| 23 | E1 overflow (backstop) | ? insufficient_spec | Unresolved — no evidence added this round; routes to human review |
| 24 | E3 populated (backstop) | ? insufficient_spec | Unresolved — no evidence added this round; routes to human review |
| 25 | E3 overflow (backstop) | ? insufficient_spec | Unresolved — no evidence added this round; routes to human review |

### Observable Truths — Plan 13-02 (Per-cell backend write path)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Single-weekday edit touches exactly one row, leaves other six byte-identical | ✓ VERIFIED (regression-checked) | Unchanged; `setDayHours` untouched by gap-closure commits |
| 2 | MANDATORY/PTO stores hours=0.00 with label, matching parser encoding | ✓ VERIFIED (regression-checked) | Unchanged |
| 3 | "Not set" deletes the row rather than writing zero | ✓ VERIFIED (regression-checked) | Unchanged |
| 4 | Out-of-range / bad-precision edit rejected 400, persists nothing | ✓ VERIFIED (regression-checked) | Unchanged |
| 5 | Cross-tenant/cross-desk edit rejected before any AgentDayHoursRepository call | ✓ VERIFIED (regression-checked) | Unchanged |
| 6 | Per-cell response reflects the row just written | ✓ VERIFIED (regression-checked) | Unchanged |
| 7 | Bulk response reflects the seven rows just written | ✓ VERIFIED (regression-checked) | Unchanged |
| 8 | Bulk fan-out is a single transaction, no partial 3-of-7 write | ✓ VERIFIED | **Was PRESENT_BEHAVIOR_UNVERIFIED — now closed by `dcc6e06`.** `DeskAgentServiceBulkRollbackTest.setContractedHours_failureOnTheFourthOfSevenRowWrites_persistsNothing` genuinely re-run and passes (1 test, 0 failures, verified independently in this session); asserts row-id equality against baseline, not just hours equality — see Summary item 2 for why that's decisive |

### Observable Truths — Plan 13-03 (Excel export + template)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Export carries 7 Mon-Sun columns after "Effective Contracted Hours Per Day" | ✓ VERIFIED (regression-checked) | `DeskAgentExportService.java` not in any gap-closure commit's file list |
| 2 | MANDATORY weekday exports the keyword, not 0 | ✓ VERIFIED (regression-checked) | Unchanged |
| 3 | PTO weekday exports the keyword, not 0 | ✓ VERIFIED (regression-checked) | Unchanged |
| 4 | Explicit hours=0.00 exports numeric 0, distinct from label | ✓ VERIFIED (regression-checked) | Unchanged |
| 5 | No stored row exports resolved effective value, never blank | ✓ VERIFIED (regression-checked) | Unchanged |
| 6 | Effective Contracted Hours column derives from agent_day_hours, not scalar | ✓ VERIFIED (regression-checked) | Unchanged |
| 7 | Blank-template specialty headers sourced from EnrichedColumnLayout | ✓ VERIFIED (regression-checked) | Unchanged |
| 8 | No weekday/specialty string literal outside EnrichedColumnLayout | ✓ VERIFIED (regression-checked) | Unchanged |

### Observable Truths — Plan 13-04 (Per-cell + bulk editing UI)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Operator can set a weekday to number/PTO/MANDATORY/not-set in the expanded row | ✓ VERIFIED (regression-checked) | `saveDayHours` branch structure unchanged |
| 2 | "Not set (default)" or clearing deletes the row, not a zero write | ✓ VERIFIED (regression-checked) | Unchanged |
| 3 | Bulk editor is explicitly labelled "Set all days to…", not the collapsed cell's click | ✓ VERIFIED (regression-checked) | Unchanged |
| 4 | Bulk confirm() fires only when ≥1 weekday carries a label, never merely because rows exist | ✓ VERIFIED (regression-checked) | `labelledDayCount` logic unchanged, confirmed the new range guard sits strictly before it |
| 5 | E4 empty: "empty" value is the explicit "Not set (default)" picklist entry, never blank | ✓ VERIFIED | **Was FAILED — now closed by `208d5c0`.** Direct re-read: `seedValueForEntry`'s not-set branch returns `'Not set (default)'` (line 30); round-trip through `saveDayHours`'s clear-row branch (line 288) and the datalist `<option>` (line 408) confirmed consistent |
| 6 | E4 loading: input disables during PUT, no spinner | ✓ VERIFIED (regression-checked) | Unchanged |
| 7 | E4 error: client range check first, server error via "Couldn't save {Weekday} — {reason}" toast | ✓ VERIFIED (regression-checked) | Unchanged |
| 8 | E4 partial: explicit-zero colour distinct from muted not-set colour, editable | ✓ VERIFIED (regression-checked) | `#6b7280`/`#9ca3af` both still present, confirmed by direct grep |
| 9 | E4 long-text: numeric validated before submit, no free text reaches the cell | ✓ VERIFIED (regression-checked) | Unchanged |
| 10 | E5 empty: bulk input opens blank, Apply inert until value chosen | ✓ VERIFIED (regression-checked) | `disabled={editHours.trim() === '' \|\| applyingBulk}` unchanged |
| 11 | E5 loading: Apply disables during PUT | ✓ VERIFIED (regression-checked) | Unchanged |
| 12 | E5 error: failure surfaces as toast, zero rows changed (transactional) | ✓ VERIFIED | **Was PRESENT_BEHAVIOR_UNVERIFIED — now closed.** The backend half of this claim (transactional, zero rows changed on failure) is proven by `dcc6e06` (see Plan 13-02 item 8); the frontend half (catch → `showToast(getErrorMessage(err))`, editor stays open) is unchanged code, already confirmed wired in the prior pass. The full live-browser round trip is still an open `unrun-verify` item (WINDOWS.md item 6) and is listed under Human Verification below — that is a separate concern (visual/UX confirmation) from the correctness invariant, which is now behaviorally proven |
| 13 | E5 populated: amber notice (#fffbeb/#fde68a/#92400e) always visible above input | ✓ VERIFIED (regression-checked) | Colours confirmed still present by direct grep |
| 14 | E5 partial: confirm() triggered by label presence, not row existence | ✓ VERIFIED (regression-checked) | Same as truth 4 above |
| 15 | E5 overflow: single-line row at 12px spacing, no overflow path | ✓ VERIFIED (structural, regression-checked) | `marginTop: '12px'` unchanged; new range-guard toast delivered via `showToast`, not a rendered element, so no new line was added (confirmed no new JSX inserted between the amber notice and Apply button) |
| 16 | E5 zero-one-many: applies to exactly 7 days, "{N} day(s)" copy | ✓ VERIFIED (regression-checked) | Confirm-dialog string unchanged |
| 17 | E4 overflow (backstop) | ? insufficient_spec | Unresolved — no evidence added this round; routes to human review |

**Score:** 54/58 truths verified (0 present-but-behavior-unverified, 4 backstop/insufficient_spec, 0 FAILED)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `frontend/src/pages/DeskAgents.tsx` | `isEveryDayNotSet` predicate, muted collapsed styling, `Not set (default)` seed literal, bulk range guard | ✓ VERIFIED | All four confirmed present by direct read: lines 50, 518-525, 30, 246-249 |
| `.planning/phases/13-per-day-hours-visibility/13-04-PLAN.md` | Corrected seed-value action prose | ✓ VERIFIED | Contains `corrected by 13-05` at line 144; original frontmatter (`plan: 04`) and must_haves intact |
| `src/main/java/com/wfm/service/DeskAgentService.java` | Inclusive 0-24 rejection in `setContractedHours` | ✓ VERIFIED | `DeskAgentService.java:244-249` contains `Contracted hours per day must be between 0 and 24` |
| `src/main/java/com/wfm/controller/GlobalExceptionHandler.java` | `MethodArgumentTypeMismatchException` mapped to a clean, non-leaking 400 | ✓ VERIFIED | `handleTypeMismatch` present (lines 49-59), names only `ex.getName()`, never `ex.getValue()`/`ex.getRequiredType()`; `handleUncaught` unchanged |
| `src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java` | Upper-bound + boundary coverage | ✓ VERIFIED | 13 tests, all pass (re-run directly, 0 failures); contains all 3 new methods |
| `src/test/java/com/wfm/service/DeskAgentServiceBulkRollbackTest.java` | Failure-injection rollback proof | ✓ VERIFIED | 1 test, passes (re-run directly, 0 failures); genuine `@MockitoSpyBean` + `NOT_SUPPORTED` propagation, not annotation reflection |
| `src/test/java/com/wfm/controller/GlobalExceptionHandlerTest.java` | Direct handler-response coverage | ✓ VERIFIED | 2 tests, both pass (re-run directly, 0 failures) |
| `.planning/phases/13-per-day-hours-visibility/13-02-PLAN.md` | Corrected read_first note | ✓ VERIFIED | Contains `corrected by 13-06` at line 241; original `plan: 02` frontmatter intact |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `DeskAgents.tsx` (collapsed cell) | `DeskAgents.tsx` (expanded empty-state note) | shared `isEveryDayNotSet(da)` predicate | ✓ WIRED | Used at line 519/522 (collapsed) and line 562 (expanded), 2+ occurrences confirmed |
| `DeskAgents.tsx` (`seedValueForEntry`) | `DeskAgents.tsx` (`saveDayHours` clearRow branch) | literal `Not set (default)` round-trip | ✓ WIRED | Seed at line 30, save-branch match at line 288, datalist option at line 408 — all case-exact |
| `DeskAgents.tsx` (`saveHours`) | `DeskAgentService.java` (`setContractedHours`) | client guard mirrors server-authoritative bound | ✓ WIRED | Both independently enforce inclusive 0-24; client guard is UX-only, server guard confirmed authoritative (no bypass path in `DeskAgentController`) |
| `DeskAgentService.java` (`setContractedHours`) | `GlobalExceptionHandler.java` (`handleIllegalArgument`) | `IllegalArgumentException` → 400 | ✓ WIRED | Unchanged pre-existing mapping, confirmed still present |
| `DeskAgentController.java` (PUT day-hours/{day}) | `GlobalExceptionHandler.java` (`handleTypeMismatch`) | `MethodArgumentTypeMismatchException` → 400 | ✓ WIRED (unit-level) | Handler registered via `@ExceptionHandler`; Spring's dispatch to it for a real HTTP request is not integration-tested in this codebase (WINDOWS.md item 7, human verification) |
| `DeskAgentServiceBulkRollbackTest.java` | `DeskAgentService.java` (`setContractedHours`) | spy-injected failure on THURSDAY row | ✓ WIRED | Test passes; genuine mid-loop failure exercised via `argThat` stubbing |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Rollback proof re-run in isolation | `./gradlew test --tests "com.wfm.service.DeskAgentServiceBulkRollbackTest"` | 1 test, 0 failures | ✓ PASS |
| Upper-bound + boundary suite re-run | `./gradlew test --tests "com.wfm.service.DeskAgentServiceContractedHoursTest"` | 13 tests, 0 failures | ✓ PASS |
| Type-mismatch handler suite re-run | `./gradlew test --tests "com.wfm.controller.GlobalExceptionHandlerTest"` | 2 tests, 0 failures | ✓ PASS |
| Frontend type-check + build re-run | `npm --prefix frontend run build` | `tsc -b && vite build` exits 0, bundle produced | ✓ PASS |
| Gap-closure commits scoped to declared files only | `git show --stat` on all 8 gap-closure commits | Only `DeskAgents.tsx`, `13-04-PLAN.md`, `13-02-PLAN.md`, `DeskAgentService.java`, `GlobalExceptionHandler.java`, `DeskAgentServiceContractedHoursTest.java`, `GlobalExceptionHandlerTest.java` (new), `DeskAgentServiceBulkRollbackTest.java` (new) | ✓ PASS |
| No debt markers in touched files | `grep -E "TBD\|FIXME\|XXX\|TODO\|HACK\|PLACEHOLDER"` across all 8 gap-closure files | 0 matches | ✓ PASS |
| No `#ef4444` destructive red introduced | `grep -c '#ef4444' DeskAgents.tsx` | `0` | ✓ PASS |
| Full clean-rebuild suite (confirmed by orchestrator, not re-run here to avoid duplicate full-suite execution) | `./gradlew test --rerun-tasks` | 315 tests, 0 failures, 0 errors, 0 skipped (up from the 309 baseline this report's prior version cited) | ✓ PASS (relied upon per task instructions) |

### Requirements Coverage

| Requirement | Description (abridged) | Status | Evidence |
|-------------|------------------------|--------|----------|
| MDL-02 | Agent stores contracted hours per day of week; effective hours resolve per date from per-day values | ✓ SATISFIED (unchanged from prior pass) | Read path fully migrated off the scalar; this round's UI/backend hardening does not touch resolution logic |
| UPL-03 | Numeric day cell ≥0 is contracted hours; 0 marks a non-working day, never blank | ✓ SATISFIED (unchanged from prior pass) | Explicit-zero distinguishable throughout; now additionally bounded ≤24 on both write paths |
| UPL-04 | MANDATORY day cell marks a mandatory day off | ✓ SATISFIED — **UI polish gap now closed** | Both cosmetic gaps noted in the prior pass (collapsed muted colour, editor seed literal) are fixed; label semantics were never in question |
| UPL-05 | PTO day cell marks recurring weekly PTO | ✓ SATISFIED — **UI polish gap now closed** | Same as UPL-04 |
| UPL-09 | Template/parser/export share one column-layout definition | ✓ SATISFIED (unchanged from prior pass) | `EnrichedColumnLayout` untouched by this round's commits |

No orphaned requirements. Phase 13 introduces no new requirement IDs; REQUIREMENTS.md attributes
MDL-02/UPL-03/04/05/09 to Phases 9/10 as `Complete`, consistent with this phase's "closes audit
gaps" framing — cross-referenced, not re-opened.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `src/main/java/com/wfm/service/DeskAgentService.java` (`:248`) / `frontend/src/pages/DeskAgents.tsx` (`:246`) | — | The inclusive `0-24` range check is now independently written at 4 call sites (2 pre-existing, 2 added closing WR-01) with no shared constant | ℹ️ Info (IN-03, 13-REVIEW.md) | Not a functional risk today; a future change to the allowed range would require updating all 4 sites by hand. Not blocking |
| `frontend/src/pages/DeskAgents.tsx` (`:495-508`, `:586`) | — | Expand/collapse toggle and per-day cells remain plain `<span onClick>` with no `role="button"`/`tabIndex`/`onKeyDown` | ℹ️ Info (IN-01, 13-REVIEW.md, unaddressed by gap-closure, pre-existing convention) | Keyboard users cannot operate the per-day editing surface. Not a regression; not blocking |
| `frontend/src/pages/DeskAgents.tsx` (`:279-312`) | — | Every cell blur fires a PUT even with no change; now also fires a harmless `clearRow` no-op more often since not-set cells seed a non-empty literal | ℹ️ Info (IN-02, 13-REVIEW.md, unaddressed by gap-closure) | Idempotent, no correctness impact — unnecessary traffic only |

No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` debt markers found in any of the 8 files the
gap-closure commits touched. No Critical or Warning findings remain open in `13-REVIEW.md`'s
gap-closure pass — all three original Warnings (WR-01/02/03) are independently confirmed CLOSED by
this verification.

### Human Verification Required

Status is `human_needed` because of the four unresolved `insufficient_spec` backstop truths and the
five open `.planning/WINDOWS.md` items (1, 2, 5, 6, 7) — none of these are new discoveries; all are
declared, tracked residuals that this gap-closure round did not (and did not claim to) resolve.

### 1. Live-desk visual walkthrough of the collapsed summary muted colour and tooltip

**Test:** Open a desk roster with a mix of agents — some with zero uploaded per-day rows, some with
at least one. Observe the Hours/Day column.
**Expected:** Zero-row agents render the value in light-grey italic with the "Not set — using
schedule default" tooltip on hover; any agent with ≥1 stored row renders in the default body colour
with no italic and no tooltip. Chevron toggles ▸/▾ correctly. No horizontal-scroll or wrap regression
at the widest range value (WINDOWS.md item 1, this report's E1-overflow backstop truth).
**Why human:** Visual colour/tooltip rendering and overflow behavior in a real browser against real
data.

### 2. Live-desk walkthrough of the per-cell editor's seed literal and the expanded grid's five states

**Test:** Expand a row and click a not-set weekday cell. Then click cells showing MAND, PTO,
explicit-zero, and a worked value.
**Expected:** The not-set cell opens pre-filled with `Not set (default)`, not blank; blurring it
unchanged sends a no-op and the cell stays visually unchanged; all 5 display states remain visually
distinct, matching the CONTEXT.md mockup (WINDOWS.md items 2 and 5; this report's E3-populated
backstop truth).
**Why human:** Requires a live DB/BambooHR-configured environment not available in this session.

### 3. Live-desk walkthrough of the bulk "Set all days to…" range guard and confirmation dialog

**Test:** Click "Set all days to…", type `1000`, click Apply. Repeat with `-1`. Then correct to `24`
on an agent with a MANDATORY/PTO weekday, and separately on an agent with none.
**Expected:** Out-of-range values produce an error toast ("Enter a number from 0 to 24."), no
confirmation dialog, and no network request, with the typed value still in the field. In-range values
proceed normally; the confirm() dialog fires only when ≥1 weekday carries a label. The action row
stays single-line (WINDOWS.md item 6; this report's E4-overflow and E5-overflow backstop/regression
truths).
**Why human:** End-to-end browser behaviour with no frontend test framework in this repo.

### 4. `PUT .../day-hours/{day}` HTTP-level dispatch for a malformed `{day}` segment

**Test:** Call the endpoint directly with a malformed `day` segment (e.g. `notaday` or lowercase),
and separately with valid and out-of-range `hours` values on a valid day. Also call the bulk
contracted-hours endpoint with an out-of-range value.
**Expected:** The malformed segment returns 400 (not 500), naming only the `day` parameter with no
rejected token or internal type name in the body. Valid `hours` still returns 200; out-of-range
`hours` still returns 400. The bulk endpoint's out-of-range 400 reads "Contracted hours per day must
be between 0 and 24". A genuinely unexpected failure still returns 500 with the generic message.
**Why human:** No `@WebMvcTest`/`MockMvc` harness exists for `DeskAgentController` in this codebase
(WINDOWS.md item 7) — the unit test proves the response `GlobalExceptionHandler.handleTypeMismatch`
builds, not Spring's actual dispatch to it for a real HTTP request. This is a declared residual from
plan 13-06's own P-17 decision, not a new discovery.

## Prohibitions Disposition (descriptor-less, per-phase convention)

This phase's `must_haves.prohibitions` blocks (across plans 13-01 through 13-06) were authored
without `check_*` descriptor scalars, so per this phase's verification contract each disposes as
`{status: unverified, flagged: true}` regardless of its declared `verification: test` or
`verification: judgment` tier — this is the designed outcome, not a defect, and is recorded here so
it is never silently absorbed into a passing verdict.

| Plan | Requirement | Statement (abridged) | Disposition | Non-authoritative note |
|------|-------------|----------------------|--------------|------------------------|
| 13-01 | MDL-02 | MUST NOT present a resolved fallback default as an operator-supplied value | unverified, flagged | **Strengthened this round:** the collapsed-cell gap this note previously flagged is now closed (`8f4f528`) — the collapsed view now distinguishes a resolved default identically to the expanded grid. Still recorded as flagged per the descriptor-less convention, not auto-resolved |
| 13-01 | MDL-02 | MUST NOT display an hours figure diverging from what the solver resolves for the same agent-weekday | unverified, flagged | Unchanged from prior pass — P-01's documented residual (roster reads newest schedule, solver reads the specific schedule being solved) is untouched by this round's commits |
| 13-02 | UPL-04 | MUST NOT silently discard a MANDATORY/PTO label as a side effect of an unrelated edit | unverified, flagged | Unchanged; still strong test evidence (`setDayHours_numeric_touchesExactlyOneRow`), still flagged not auto-resolved |
| 13-03 | UPL-09 | MUST NOT change the export/template column set silently | unverified, flagged | Unchanged from prior pass |
| 13-04 | UPL-05 | MUST NOT let a bulk action destroy labels without warning | unverified, flagged | Unchanged; label-count trigger logic untouched by this round |
| 13-05 | MDL-02 | MUST NOT present a resolved fallback default as an operator-supplied value in the collapsed roster cell any more than in the expanded grid | unverified, flagged | Genuinely strong evidence now exists (`isEveryDayNotSet` gates identical styling in both views) — still flagged per convention |
| 13-05 | UPL-03 | MUST NOT let merely opening a not-set weekday cell create a row, and MUST NOT make the seeded literal indistinguishable from a typed value | unverified, flagged | `saveDayHours`'s `clearRow` no-op path is unchanged and tested (`setDayHours_notSet_onAbsentRow_isANoOp`) — still flagged per convention |
| 13-06 | UPL-04 | MUST NOT let a rejected or mid-way-failed bulk edit leave a partial write, destroy a label, or leave the scalar/rows disagreeing | unverified, flagged | **Now has direct behavioral test evidence** (`DeskAgentServiceBulkRollbackTest`, `setContractedHours_above24_leavesExistingRowsAndLabelsUntouched`) — genuinely the strongest evidence of any prohibition in this phase, still flagged per convention |
| 13-06 | MDL-02 | MUST NOT leak an internal type name, attacker-supplied path value, or stack trace in the 400 body | unverified, flagged | Direct code read and passing test confirm no leakage (`GlobalExceptionHandlerTest`); still flagged per convention, and its end-to-end HTTP dispatch remains an open human-check (item 4 above) |

### Gaps Summary

None. Both `status: failed` gaps from the prior verification are closed, confirmed by direct source
inspection (not SUMMARY.md self-report) and by independently re-running the targeted test suites in
this session. No regressions were found in any of the 40 previously-verified truths that this
gap-closure round did not touch — confirmed by `git show --stat` scoping all 8 gap-closure commits to
exactly the 8 files their SUMMARYs claim.

Status is `human_needed`, not `passed`, solely because of pre-existing, declared, non-regressive
items: 4 `insufficient_spec` backstop truths this round added no evidence for, and 5 open
`.planning/WINDOWS.md` `unrun-verify` items requiring a live BambooHR-configured desk or an HTTP
integration harness this codebase does not have. None of these block the phase goal — the
authoritative read-path migration (I-1/F-1), the structural per-cell write safety (I-3), the
specialty-header single-sourcing (I-4), and now the previously-missing UI-behavior conformance and
the bulk fan-out's transactional rollback are all proven by direct inspection and passing,
independently re-run tests.

---

_Verified: 2026-08-24T13:15:24Z_
_Verifier: Claude (gsd-verifier)_
