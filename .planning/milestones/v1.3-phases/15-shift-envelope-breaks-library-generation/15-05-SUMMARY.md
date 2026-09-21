---
phase: 15-shift-envelope-breaks-library-generation
plan: 05
subsystem: ui
tags: [react, typescript, shift-library, break-bands, shift-library-generation]

# Dependency graph
requires:
  - phase: 15-shift-envelope-breaks-library-generation (plan 01)
    provides: "ShiftTemplateRequest/Response bands array, ShiftLibraryValidationResponse.capacityAdvisories"
  - phase: 15-shift-envelope-breaks-library-generation (plan 02)
    provides: "GET /desks/{deskId}/shift-library/suggestion, ShiftLibrarySuggestionResponse"
provides:
  - "BandEditor component — repeatable break-band list editor (add/remove/update rows), reused verbatim by the Add/Edit form and every Suggested Library draft row"
  - "ShiftLibrary.tsx renders and writes N break bands per template via client.ts's bands array, replacing Phase 14's single break offset/duration field pair"
  - "Saved-template list Break column made band-aware (zero/one/many); new Capacity column with the D-03 capacity-shortfall advisory glyph"
  - "Suggested Library draft panel — stateless GET, always-editable draft rows, per-row Save through the existing create call, refusal/partial-coverage rendering reusing CoveragePanel verbatim"
  - "client.ts: ShiftTemplate/ShiftTemplateBody bands array types, CapacityAdvisory type, ShiftLibrarySuggestion/SuggestedTemplate/SuggestedBand types, shiftLibrary.suggestion() call"
affects: [15-07-frontend-agent-allocation-grouping, end-of-phase-UAT]

actuals:
  tokens: 9380
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Plain useState<BandRow[]> array with spread-append/index-filter removal (P-20) — no library, the codebase's first 'append a blank row to a growing form array' pattern"
    - "Reusable field-editor component (BandEditor) parameterized by a setF-shaped setter, shared verbatim between the top-level Add/Edit form and N independent Suggested Library draft rows via a per-row setter adapter (setDraftRow)"
    - "Per-band/per-row field errors keyed by server-emitted field name (bands[i].breakStartTime) rather than a flat field map, routing a misaligned band's error to its own row"

key-files:
  created: []
  modified:
    - frontend/src/api/client.ts
    - frontend/src/pages/ShiftLibrary.tsx

key-decisions:
  - "Band-aware Break column (zero/one/many) implemented in Task 1's own commit rather than deferred to Task 3, because the client.ts type change (bands array replacing the four scalar fields) makes the old Break column code not compile — Task 3 then only adds the Capacity column, per the plan's actual scope split"
  - "Draft row state (DraftRowState) extends TemplateFormState with its own submitting/fieldErrors — draft rows save and error independently of each other and of the Add/Edit form, so each row carries its own request lifecycle rather than sharing the page-level submitting/fieldErrors state"
  - "Suggestion refusal message rendered from err.message (the exception's own top-level text) rather than getErrorMessage(err), which would prepend the 'demand:' field prefix to the copywriting-contract's exact wording"

patterns-established:
  - "setF-shaped setter adapter pattern: any component expecting `(updater: (f: T) => T) => void` can be driven by either page-level useState setters or a per-row setter closure (setDraftRow(idx)), letting one field-editor component serve both a singleton form and an N-row draft list without duplication"

requirements-completed: [ENVL-08, SHLB-07, XCUT-01]

coverage:
  - id: D1
    description: "An operator can add two break bands to a template, save, reload the page, and see both bands persisted in offset-ascending order"
    requirement: "ENVL-08"
    verification:
      - kind: other
        ref: "cd frontend && npm run build"
        status: pass
    human_judgment: true
    rationale: "Requires a running app against a real desk/DB to exercise save-reload-render; no frontend test framework exists in this repo (standing project decision since Phase 13). Automated build proves the code compiles and the type shapes line up; the end-to-end persistence claim is this plan's own declared human-check, routed to end-of-phase UAT."
  - id: D2
    description: "Zero break bands renders the muted 'No break bands added' line in the editor and 'No break' in the list's Break column, never a blank cell"
    requirement: "ENVL-08"
    verification:
      - kind: other
        ref: "grep -n \"No break bands added|No break\" frontend/src/pages/ShiftLibrary.tsx"
        status: pass
    human_judgment: true
    rationale: "Static presence of the copy strings and the conditional branch is confirmed by grep/code-read; whether it reads correctly on screen (visual claim) is a backstop row per 15-UI-SPEC.md E1/E2, routed to human UAT."
  - id: D3
    description: "A band's capacity input opens blank with an 'Unlimited' placeholder, never pre-filled with 0 or a number; the per-band preview always states capacity explicitly"
    requirement: "ENVL-08"
    verification: []
    human_judgment: true
    rationale: "Visual/interactive claim (placeholder rendering, live preview text) not provable by assertion without a frontend test framework — routes to end-of-phase UAT per this plan's own <verification> section."
  - id: D4
    description: "A grid-misaligned band renders its inline amber error beneath that band's own row, keyed by the server's bands[i].breakStartTime/breakEndTime field names"
    requirement: "ENVL-08"
    verification: []
    human_judgment: true
    rationale: "Requires a live desk with a timeslot grid and a real 400 response from the backend to exercise; this plan's own human-check explicitly defers this to end-of-phase UAT."
  - id: D5
    description: "Requesting a suggested library writes nothing: the draft renders in a dashed container with a Draft badge and the discard-notice line; navigating away leaves the template list unchanged"
    requirement: "SHLB-07"
    verification:
      - kind: other
        ref: "grep -n \"This draft isn't saved\" frontend/src/pages/ShiftLibrary.tsx"
        status: pass
    human_judgment: true
    rationale: "Statelessness (no write on generate) is structurally true by code inspection (shiftLibraryApi.suggestion is a GET with no mutation call), but the operator-visible rendering claim requires a live desk with demand — routed to human UAT."
  - id: D6
    description: "A suggestion against a desk with no demand renders the amber refusal panel and never an empty draft table; a partial-coverage suggestion renders the draft rows plus CoveragePanel fed the response's own uncoveredWindows"
    requirement: "SHLB-07"
    verification:
      - kind: other
        ref: "grep -c CoveragePanel frontend/src/pages/ShiftLibrary.tsx"
        status: pass
    human_judgment: true
    rationale: "CoveragePanel reuse (no second uncovered-windows renderer) is confirmed structurally; the actual refusal/partial-coverage branches require live desk data with the corresponding demand shapes — this plan's own human-check, routed to end-of-phase UAT."
  - id: D7
    description: "Saving one draft row commits it through the same create endpoint a manual Add uses; on success the row leaves the draft and appears in the saved template list; validation errors surface through the identical inline mechanism"
    requirement: "SHLB-07"
    verification:
      - kind: other
        ref: "grep -n \"shiftTemplatesApi.create(deskId, formToBody(row))\" frontend/src/pages/ShiftLibrary.tsx"
        status: pass
    human_judgment: true
    rationale: "Code inspection confirms handleSaveDraftRow calls the identical shiftTemplatesApi.create() a manual Add uses and routes 400 details into the same fieldErrors shape; the end-to-end save/move-to-list behavior needs a live app to exercise, per this plan's own human-check."
  - id: D8
    description: "No new colour, type size, spacing token, component library, CSS framework, or frontend test framework is introduced — every new element reuses a token already in the Phase 14 contract"
    verification:
      - kind: other
        ref: "git diff --stat frontend/package.json frontend/package-lock.json (empty); grep -c '#ef4444' frontend/src/pages/ShiftLibrary.tsx (0)"
        status: pass
    human_judgment: false

duration: ~40min
completed: 2026-08-27
status: complete
---

# Phase 15 Plan 5: Break Band Editor & Suggested Library Draft Panel Summary

**`ShiftLibrary.tsx` gained a repeatable break-band editor (`BandEditor`, shared by the Add/Edit form and N Suggested Library draft rows), a band-aware Break column, a capacity-advisory glyph column, and a stateless Suggested Library draft panel that saves rows through the existing create endpoint — closing the display-verification half of ENVL-08 and SHLB-07 that XCUT-01 exists to guard against.**

## Performance

- **Duration:** ~40 min
- **Tasks:** 3
- **Files modified:** 2

## Accomplishments

- `client.ts`: `ShiftTemplate`/`ShiftTemplateBody` carry a `bands`/`ShiftTemplateBreakBandBody[]` array replacing Phase 14's four scalar break fields; `ShiftLibraryValidation.capacityAdvisories`; `ShiftLibrarySuggestion`/`SuggestedTemplate`/`SuggestedBand` types and `shiftLibrary.suggestion(deskId)` against `GET /desks/{deskId}/shift-library/suggestion`.
- `BandEditor` component: a plain `useState<BandRow[]>`-backed repeatable band list (P-20) — start time, duration, capacity (blank-default, `Unlimited` placeholder), Remove, per-band preview line, `+ Add Break Band`, and the zero-bands "No break bands added" empty state — reused verbatim by the Add/Edit form and every draft row.
- Per-band grid-misalignment errors route to the offending row via the server's `bands[i].breakStartTime`/`bands[i].breakEndTime` field-keyed `ErrorDetail`s.
- `startEdit` seeds one band row per existing band, offset-ascending; `formToBody` converts each band's wall-clock start to an offset per band (P-21).
- Suggested Library draft panel: plain `Suggest a Library` button beside the accent `Add Shift Template`; renders nothing before the first click; three outcomes off the one stateless call — D-12 refusal (amber panel, no Draft badge), full coverage (draft rows only), partial coverage (draft rows plus `CoveragePanel` reused verbatim, fed the response's own `uncoveredWindows`, P-22).
- Draft rows are always-editable (P-24), reusing the exact Add/Edit field set including `BandEditor`; per-row `Save` commits through the identical `shiftTemplatesApi.create()` call a manual Add uses (T-15-19); `Drop` removes the row in-memory with no request. Dashed container + discard-notice line make the endpoint's statelessness operator-visible.
- Saved-template list's `Break` column made band-aware (zero → "No break", one → unchanged from Phase 14, two-or-more → one line per band, offset-ascending); new `Capacity` column with the identical glyph-plus-tooltip mechanism SHLB-06's Hours match column already uses, combining multiple weekday advisories into one tooltip.
- No new colour, type size, spacing token, icon library, component library, or dependency — `frontend/package.json` is byte-identical to its pre-plan state; no `#ef4444` element introduced.

## Task Commits

1. **Task 1: Two bands, saved and read back — the band editor end to end** - `6ecbcdd` (feat)
2. **Task 2: The Suggested Library draft panel — three results, one call, nothing written** - `1ed31c1` (feat)
3. **Task 3: Band-aware Break column and the capacity advisory glyph** - `658d5ca` (feat)

## Files Created/Modified

- `frontend/src/api/client.ts` - `ShiftTemplateBreakBand`/`ShiftTemplateBreakBandBody`, `bands` array on `ShiftTemplate`/`ShiftTemplateBody`, `CapacityAdvisory`, `capacityAdvisories` on `ShiftLibraryValidation`, `ShiftLibrarySuggestion`/`SuggestedTemplate`/`SuggestedBand`, `shiftLibrary.suggestion()`
- `frontend/src/pages/ShiftLibrary.tsx` - `BandEditor` component, `BandRow`/`DraftRowState` types, band mutation helpers (`addBand`/`removeBand`/`updateBand`), `startEdit`/`formToBody`/`confirmRetire` ported to bands, band-aware Break column, new Capacity column, Suggested Library section (`handleSuggest`/`handleSaveDraftRow`/`handleDropDraftRow`/`renderDraftRow`/`toggleDraftWeekday`/`clearDraftFieldError`/`setDraftRow`)

## Decisions Made

See `key-decisions` in frontmatter — the Break-column-in-Task-1 scope adjustment (forced by the type change, not a plan deviation in substance), the `DraftRowState` per-row lifecycle choice, and the `err.message`-over-`getErrorMessage()` choice for the refusal panel's exact copy.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Band-aware Break column implemented in Task 1's commit, not deferred to Task 3**
- **Found during:** Task 1
- **Issue:** Task 1's own client.ts type change replaces `ShiftTemplate`'s four scalar break fields with a `bands` array. The saved-template list's Break column (`t.breakStartTime.slice(0, 5)}–{t.breakEndTime...`) referenced those removed fields and would not compile once the type changed, even though the plan's prose assigns the full Break-column rewrite to Task 3.
- **Fix:** Implemented the zero/one/many band-aware Break column rendering (this plan's own Task 3 §2 spec) inside Task 1's commit, as a build-compile requirement of the type change. Task 3's commit then only adds the new Capacity column, which is the remaining, genuinely Task-3-scoped work.
- **Files modified:** `frontend/src/pages/ShiftLibrary.tsx`
- **Verification:** `cd frontend && npm run build` passes after Task 1's commit.
- **Committed in:** `6ecbcdd` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking/compile)
**Impact on plan:** Necessary for Task 1's own stated `<verify>` (`npm run build` succeeding); no scope creep beyond what Task 3 already specified for the same column — the work simply landed one commit earlier than the plan's task boundary implied, with Task 3's commit correspondingly smaller.

## Issues Encountered

None beyond the deviation above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `BandEditor` and the `setF`-shaped setter adapter pattern (`setDraftRow`) are available for plan 15-07 (Agent Allocation shift grouping) if it needs a similar repeatable-list pattern, though that plan's own scope (per `15-UI-SPEC.md` § Component Specifications 4) does not currently require one.
- **Note for 15-07:** this plan modified `frontend/src/api/client.ts` additively (new interfaces and one new `shiftLibrary.suggestion()` export appended alongside existing exports) — no existing export signature was renamed or removed, so 15-07's own additive changes to the same file (per its own scope) should merge without conflict.
- **Concern for a human reviewer / end-of-phase UAT:** every behavioral truth in this plan's `must_haves.truths` (bands persisting offset-ascending across a save/reload, per-band misalignment errors landing on the correct row, the three Suggested-Library outcomes, the capacity-advisory tooltip content) requires a running app against a real desk with demand/agent data to exercise — this repo has no frontend test framework (standing project decision since Phase 13, reconfirmed by `15-UI-SPEC.md`), so all of it routes to the human-check blocks this plan's own `<verification>` section already names, not to a silent pass.

## Self-Check: PASSED

- Both modified files verified present on disk (`frontend/src/api/client.ts`, `frontend/src/pages/ShiftLibrary.tsx`).
- All three task commits verified present in `git log`: `6ecbcdd`, `1ed31c1`, `658d5ca`.
- Plan `<verification>` bullets re-run: `cd frontend && npm run build` succeeds with `noUnusedLocals` enabled (confirmed in `frontend/tsconfig.json`); `git diff --stat frontend/package.json frontend/package-lock.json` is empty (byte-identical); `grep -c "#ef4444"` returns 0 (no destructive-red element introduced). Every human-check block in the plan's tasks remains unexecuted by design, routed to end-of-phase UAT per this plan's own `<verification>` section ("Every human-check block above is answered at end-of-phase UAT").
- `must_haves.key_links` patterns confirmed present: `suggestion|bands` (30 matches) and `CoveragePanel` (4 matches) in `ShiftLibrary.tsx`.

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-08-27*
