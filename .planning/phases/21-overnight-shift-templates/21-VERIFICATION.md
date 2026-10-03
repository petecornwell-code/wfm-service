---
phase: 21-overnight-shift-templates
verified: 2026-10-03T15:42:00Z
status: human_needed
score: 7/7 must-haves verified
behavior_unverified: 0
overrides_applied: 0
covered_files:
  - ".planning/REQUIREMENTS.md"
  - ".planning/ROADMAP.md"
  - ".planning/WINDOWS.md"
  - ".planning/phases/21-overnight-shift-templates/21-01-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-01-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-02-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-02-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-03-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-03-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-04-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-04-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-05-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-05-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-06-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-06-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-07-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-07-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-08-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-08-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-09-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-09-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-10-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-10-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-11-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-11-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-12-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-12-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-REVIEW-DISPOSITION.md"
  - ".planning/phases/21-overnight-shift-templates/21-REVIEW.md"
  - ".planning/phases/21-overnight-shift-templates/21-UAT.md"
  - "frontend/src/api/client.ts"
  - "frontend/src/pages/DeskManagement.tsx"
  - "frontend/src/pages/ScheduleResults.tsx"
  - "frontend/src/utils/dayWindow.ts"
  - "src/main/java/com/wfm/controller/DeskController.java"
  - "src/main/java/com/wfm/controller/ScheduleController.java"
  - "src/main/java/com/wfm/dto/DeskResponse.java"
  - "src/main/java/com/wfm/dto/ScheduleDetailResponse.java"
  - "src/main/java/com/wfm/dto/ScheduleSummary.java"
  - "src/main/java/com/wfm/dto/ShiftLibraryValidationResponse.java"
  - "src/main/java/com/wfm/repository/ScheduleRepository.java"
  - "src/main/java/com/wfm/repository/StaffingRequirementRepository.java"
  - "src/main/java/com/wfm/service/DeskService.java"
  - "src/main/java/com/wfm/service/FteUploadService.java"
  - "src/main/java/com/wfm/service/ScheduleExportService.java"
  - "src/main/java/com/wfm/service/ScheduleOutputService.java"
  - "src/main/java/com/wfm/service/ShiftLibraryGenerationService.java"
  - "src/main/java/com/wfm/service/ShiftLibraryValidationService.java"
  - "src/main/java/com/wfm/service/ShiftTemplateService.java"
  - "src/main/java/com/wfm/service/StaffingRequirementService.java"
  - "src/main/java/com/wfm/solver/ScheduleConstraintProvider.java"
  - "src/test/resources/bday-join-guard.md"
  - "src/test/resources/midnight-boundary-scenarios.md"
  - "src/test/resources/midnight-time-arithmetic.md"
covered_digest: "v2:sha256:1c2003713a7081ea7f49b5852ddb02cb42c405f52a8e431a0b7c145961b1a572"
re_verification:
  previous_status: human_needed
  previous_score: "7/7"
  gaps_closed:
    - "WINDOWS.md #11's 5th sub-case — the accepted-schedule refusal Toast — now measured against a genuine stale-page race (desk created UNLOCKED, page loaded and put in edit mode with input[type=time].disabled confirmed false, an ACCEPTED schedule inserted server-side behind the page, then a day-start save from that stale page): 'Desk has an accepted schedule (dddddddd-0000-4000-8000-000000000099, 2026-02-01 to 2026-02-28)' — 94 chars, #dc2626, 344x89, 4 lines, scrollW==clientW (344), scrollH==clientH (89), whiteSpace normal, overflow visible, fully in viewport, exactly one toast, row held in edit mode. WINDOWS.md #11's note corrected in place to record all five D5-named sub-cases as measured."
    - "The Excel Roster legend-collision backstop (21-07-PLAN.md Task 1) — waived by explicit operator decision (WINDOWS.md #16, status waived; 21-UAT.md item 4). The check is structurally unavailable to an automated session (no tool here renders an .xlsx the way Excel does, so it could only be inferred, which is the bar this phase's UAT has refused everywhere else). Residual risk accepted and recorded: cosmetic only, affects none of the seven OVNT truths, cannot corrupt data, and the POI-level proxy (21-07-SUMMARY.md D5) already confirmed cell placement programmatically. Treated as a CLOSED decision per this pass's explicit instruction, not an open human-verification item."
  gaps_remaining: []
  regressions: []
  new_items_from_full_harvest:
    - "A full re-harvest of every <human-check> block across all twelve plans (Step 8), going one level deeper than the three prior passes, surfaces that 21-10-PLAN.md Task 2's human-check — which explicitly requires its six sub-checks to be confirmed 'in BOTH the slot-mode and shift-mode grid branches' — was only ever exercised live against a slot-mode schedule (21-UAT.md item 2: no shift-group header row, no envelope-reached styling mentioned, consistent with ScheduleResults.tsx's schedulingMode !== 'SHIFT' branch at line 487). Two of the six sub-checks (an overnight envelope's hours rendering as 'covered' via isEnvelopeReached, and shift groups ordering by anchored start) have NO slot-mode equivalent at all — they exist only in the shift-mode branch (schedulingMode === 'SHIFT', lines ~630-825) — so a slot-mode-only live pass cannot have incidentally exercised them under any reading. Two more (a break band crossing midnight showing every slot it covers; an unfilled-seat marker landing under its own business-day section) were not visually confirmed live in either branch. All four are computationally proven by the plan's own executed dayWindow.ts fixture script (21-10-SUMMARY.md coverage D2/D3, 12/12 checks passed, human_judgment: false) — this is not a logic defect — but the rendered-DOM half of the claim, which this phase's own stated methodology treats as a separate, required measurement (the same split 21-10-SUMMARY.md itself draws for D1/D5's sticky-column and heading-wrap checks), has never been exercised for these four sub-checks. Classified MEASURABLE (not structurally human-only): the same throwaway-stack/browser-automation technique already used three times this session would work, seeding one SHIFT-mode-scheduled desk with an overnight envelope, a break crossing the anchor, and one deliberately unfilled post-midnight seat."
---

# Phase 21: Overnight Shift Templates Verification Report

**Phase Goal:** A desk can define a shift that spans midnight, and every surface that touches it —
save-time validation, contracted-hours consumption, day-off blocking, the schedule UI grid, the
Excel export — treats it correctly as one continuous thing belonging to the business day it starts
on.
**Verified:** 2026-10-03T15:42:00Z
**Status:** human_needed
**Re-verification:** Yes — fourth pass. This pass (1) confirms the two items left open by the third
pass are now genuinely resolved (one measured, one waived), and (2) performs the complete,
exhaustive Step 8 harvest the brief asked for — every `<human-check>` block in all twelve plans,
classified as measured/resolved/waived, structurally human-only, or newly-found-and-measurable —
rather than re-checking only the items already named.

## What changed since the third verification pass

Only `.planning/WINDOWS.md` and `.planning/phases/21-overnight-shift-templates/21-UAT.md` changed
again (confirmed: `git diff --stat bc6ecaf~4..HEAD -- src/ frontend/src/` is empty, and `git status
--short` shows no tracked changes to either tree — only the untracked `.planning/state.json` and
`.playwright-mcp/` scratch artifacts, neither of which this phase's `covered_files` includes). The
third pass's own `21-VERIFICATION.md` — the one this pass updates in place — was part of commit
`2283e77`/the prior working-tree state; `bc6ecaf` closed both items that pass left open.

### Both items the third pass left open are now genuinely closed

**1. The accepted-schedule refusal Toast (WINDOWS.md #11's 5th sub-case) — MEASURED.**
`21-UAT.md`'s `accepted_schedule_refusal_measured` block records the genuine stale-page race rather
than a shortcut: a desk created UNLOCKED, the page loaded and put into edit mode with
`input[type=time].disabled` independently confirmed `false` (so the page held no knowledge of a
lock), an ACCEPTED schedule then inserted server-side behind the page's back, then a day-start save
submitted from that stale page — the exact path `DeskService.java:298-304`'s `ConflictException`
guards. Result: `"Desk has an accepted schedule (dddddddd-0000-4000-8000-000000000099, 2026-02-01 to
2026-02-28)"` — 94 chars, `#dc2626`, 344×89 box, 4 lines, `scrollW==clientW` (344) and
`scrollH==clientH` (89), `whiteSpace: normal`, `overflow: visible`, fully in viewport, exactly one
toast, row held in edit mode. This is the same evidentiary bar (live seeded state, in-page geometry
read, not inferred from a structurally-similar-but-distinct message) already applied to the other
four D5-named sub-cases. **WINDOWS.md #11's full five-sub-case scope is now genuinely measured**, and
its ledger note has been corrected to say so rather than overstate at four.

**2. The Excel Roster legend-collision backstop — WAIVED, treated as closed per this pass's
instruction.** `21-UAT.md` item 4 and `WINDOWS.md #16` (status `waived`) record the operator's
explicit decision: this check is structurally unavailable to any automated session here (nothing in
this environment renders an `.xlsx` the way Excel does), so it could only ever be inferred — the
exact bar this phase's UAT has refused to accept for every other item. Rather than lower that bar or
leave the phase indefinitely pending on a check no agent in this setup can perform, the operator
waived it, with the rationale and residual risk recorded in both files: cosmetic only, affects none
of the seven OVNT truths, cannot corrupt data, and `21-07-SUMMARY.md`'s POI-level proxy already
confirmed cell placement programmatically. Per this pass's explicit brief, an operator-waived item
with rationale and residual risk recorded in both the UAT and the ledger is a **closed decision**,
not an open human-verification item, and I agree with that reading — it is not held at `human_needed`
in this pass's output.

### The complete classified harvest (the one-more-harvest this pass was asked to run)

Every `<human-check>` block across all twelve plans, traced to its disposition:

| # | Source | Item | Classification | Disposition |
|---|--------|------|----------------|-------------|
| 1 | 21-01-PLAN.md Task 1 | 00:00-anchored desk's error Toast naming its own day start, no clipping at normal/narrow viewport | MEASURABLE | **CLOSED** — human-verified live during this plan's own tracer-feedback-gate pause at execution time (21-01-SUMMARY.md D2, "Human confirmed... at both viewport widths") |
| 2 | 21-07-PLAN.md Task 1 | Excel Roster-sheet legend does not collide with agent rows, viewed in Excel | STRUCTURALLY HUMAN-ONLY (no `.xlsx` renderer available to any automated session here) | **WAIVED** by explicit operator decision, residual risk recorded (WINDOWS.md #16, 21-UAT.md item 4) |
| 3 | 21-08-PLAN.md Task 1 | Picker 15-min stepping; disabled render naming schedule+period; lock explanation wraps at real widths | MEASURABLE | **CLOSED** — measured live (21-UAT.md item 1 control + `lock_explanation_measured`) |
| 4 | 21-08-PLAN.md Task 2 (5 sub-items) | Tiling-value save; non-dividing-increment warning; keyboard non-boundary refusal; stale-page accepted-schedule refusal; geometry of the refusal/warning pair at narrow viewport | MEASURABLE | **CLOSED** — all 5 measured live (21-UAT.md item 1 + `accepted_schedule_refusal_measured`) |
| 5 | 21-10-PLAN.md Task 2, sub-items (1)(2) | Contiguous run + anchored column order | MEASURABLE | **CLOSED for the slot-mode branch** (21-UAT.md item 2); **not separately re-exercised for the shift-mode branch** — see new finding below |
| 6 | 21-10-PLAN.md Task 2, sub-item (3) | Overnight envelope's hours render as "covered" (`isEnvelopeReached`), not as hours no shift reaches | MEASURABLE, never exercised live | **OPEN — new finding this pass** (shift-mode-exclusive code path; logic fixture-proven, never rendered) |
| 7 | 21-10-PLAN.md Task 2, sub-item (4) | A break band crossing midnight shows every slot it covers | MEASURABLE, never exercised live | **OPEN — new finding this pass** (logic fixture-proven via A4, never rendered in either branch) |
| 8 | 21-10-PLAN.md Task 2, sub-item (5) | Shift groups list the day's earliest anchored shift first | MEASURABLE, never exercised live | **OPEN — new finding this pass** (shift-mode-exclusive code path; logic fixture-proven via A5, never rendered) |
| 9 | 21-10-PLAN.md Task 2, sub-item (6) | Unfilled-seat marker for a post-midnight slot appears under its own business-day section | MEASURABLE, never exercised live | **OPEN — new finding this pass** (keying logic fixture-proven via A6, placement never visually confirmed in either branch) |
| 10 | 21-10-PLAN.md Task 2, 00:00-anchor regression repeat of (1)(2)(6) | Grid unchanged from before this plan at a midnight anchor | MEASURABLE | **CLOSED** — proven byte-identical to the pre-phase logic by the executed fixture script (21-10-SUMMARY.md D4: B1, B4, both reproducing the OLD implementation verbatim for direct comparison), which is a stronger evidentiary bar than a visual spot-check would be |
| 11 | 21-10-PLAN.md Task 3, sub-items (1)(2)(3) | Heading text/weekday-apart; heading wraps without forcing page scroll; sticky column undisturbed at 24+ columns | MEASURABLE | **CLOSED** — measured live (21-UAT.md items 2 and 3) |
| 12 | 21-10-PLAN.md Task 3, 00:00-anchor regression repeat | Heading renders the bare date, unchanged | MEASURABLE | **CLOSED** — proven byte-identical by the executed fixture script (21-10-SUMMARY.md D4: B-heading) |

**Net new finding (items 6–9 above):** `ScheduleResults.tsx`'s `AgentAllocationTab` has two
structurally distinct render branches selected by `schedule.schedulingMode` (line 487: `if
(schedule.schedulingMode !== 'SHIFT')` renders the flat per-agent slot table; the `else` branch,
~line 630–825, renders shift-template-grouped rows with `colSpan` group headers). `21-10-PLAN.md`
Task 2's own human-check text explicitly scopes itself to "BOTH the slot-mode and shift-mode grid
branches." `21-UAT.md`'s environment description ("60-minute timeslots, one overnight 22:00–06:00
template, and a COMPLETED schedule") and its item 2 evidence (no shift-group header row, no
envelope-reached/"no shift reaches" styling mentioned) are consistent only with the slot-mode branch
having been rendered. Two of the six sub-checks — envelope-reached hour styling and shift-group
ordering — have **no slot-mode equivalent in the code at all**, so no reading of a slot-mode-only
live pass can have incidentally exercised them. I considered inferring these are "probably fine"
because they share the same `dayWindow.anchoredContains`/`anchoredStartMinute` primitives already
proven correct by the executed fixture script — but this phase's own prior passes have twice
explicitly rejected inference-by-analogy for a weaker case (a shorter Toast string in an identical,
already-proven container) on the grounds that "the whole reason this class of check exists is that
DOM geometry is not safely inferable from markup." Applying a laxer standard here — inferring a
different React render branch renders correctly because its *arithmetic* is shared — would be the
same inconsistency already rejected. **Decision:** record this as a new, narrow, measurable,
low-risk human-verification item rather than close it by analogy. It blocks none of the seven OVNT
truths (all remain code-and-test verified independent of this specific render branch's live
appearance), but it does mean the full scope of `21-10-PLAN.md` Task 2's own human-check text has not
yet been exercised end-to-end.

### Net effect on status

Per the decision tree, a non-empty human-verification list routes status to `human_needed`
(`gaps_found` does not fire — no truth failed, no artifact is missing or a stub, no key link is
unwired; the four newly-identified items' underlying logic is fixture-proven and unit/integration
tested, only their rendered-DOM appearance in the shift-mode branch is unmeasured). **Status stays
`human_needed`**, carried forward from the third pass, but for a narrower and more precisely scoped
reason: both items that pass left open are genuinely resolved; one new, smaller item surfaced by
finally completing the exhaustive harvest replaces them.

## Goal Achievement

### Observable Truths

Unchanged from all three prior passes — all 7 are code-and-test-verified facts that no UAT/backstop
measurement work touches, and no implementation file has changed since the initial verification.

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | An operator can save a shift template whose end time is earlier in the clock than its start time, with correct net hours for the overnight span, on an anchored desk (OVNT-01) | ✓ VERIFIED | `ShiftTemplateService.java:51` `MAX_SPAN_MINUTES=16*60`; `validate()` refuses a backward interval naming the desk's own day start; `window.anchoredDurationMinutes(...)` computes net hours. `ShiftTemplateServiceTest` (53 tests, 0 failures). Corroborated live (UAT): a real 22:00-06:00 template SAVED successfully, end-to-end, through the real HTTP API on a 21:00-anchored desk; the D-02 refusal Toast on a 00:00-anchored desk was human-verified live during this plan's own execution. |
| 2 | That shift is reported against the business day it starts on, everywhere it is displayed (OVNT-02) | ✓ VERIFIED | `ScheduleOutputService.buildAgentSchedule` groups by `a.getTimeslot().getBusinessDate()`; `ScheduleSummary.dayStart`/`ScheduleDetailResponse` carry the schedule's own anchor; structured `ViolationDetail` fields. Corroborated live (UAT): the summary payload carried `"dayStart":"21:00:00"`; the Schedule Results date filter offered exactly ONE date for a run spanning two calendar days. |
| 3 | A day-off or PTO marking on the starting business day blocks the shift from being assigned, including seats stamped with the following calendar date (OVNT-03) | ✓ VERIFIED | `ScheduleConstraintProvider.java:183`: `agentDayOff` join is `equal(a -> a.getTimeslot().getBusinessDate(), AgentDayOff::getDate)`. `MidnightBoundaryRegressionTest$DayOffAttributesToStartingBusinessDate` green. |
| 4 | The shift consumes the contracted hours of the weekday it starts on only, never split across two weekday rows (OVNT-04) | ✓ VERIFIED | `MidnightBoundaryPropertyTest.ContractedHoursStartingWeekdayOnly` proves single business-date derivation for both calendar days of a crossing stretch. |
| 5 | Shift-library validation refuses an overnight template whose envelope does not fit inside its desk's business day (OVNT-05) | ✓ VERIFIED | `isWithinOperatingWindow`/`crossesCalendarMidnight` wired into save path, SHIFT-mode gate, and candidate generation — one predicate, three callers. |
| 6 (amended) | The business-day-keyed schedule UI grid/roster render an overnight shift as one block carrying calendar-span disclosure; per-date slot surfaces render it as one continuous run of cells (OVNT-06) | ✓ VERIFIED | Amendment premise independently re-derived and confirmed true. `ScheduleExportService` sorts by `anchoredStartMinute`/`anchoredShiftSortKey`; `ScheduleResults.tsx` binds one `dayWindow` throughout both render branches. Corroborated live (UAT, slot-mode branch): 26-column allocation grid on a 21:00-anchored desk renders anchored order (21:00→20:00, not clock order); the night agent's 7 overnight cells occupy header indices 3-9 with no gap. The shift-mode branch's equivalent rendering (envelope-reached styling, shift-group ordering) is proven correct by the real `dayWindow.ts` module executed against representative fixtures (12/12 checks passed) but not yet confirmed in a live render — see Human Verification. |
| 7 | The shift is labelled with the calendar dates it spans wherever displayed (OVNT-07) | ✓ VERIFIED | `ScheduleExportService.shiftCode`/`crossingAwareCode` on Roster sheet; `ScheduleOutputService.timeslotLabel` appends `(business day: {date})`; `ScheduleResults.tsx`'s `sectionHeading` discloses `(business day: {span})`. Corroborated live (UAT): heading rendered live as `"2026-01-05 (business day: Mon 21:00–Tue 21:00)"`, en dash intact through JSON transit. |

**Score:** 7/7 truths verified (0 present, behavior-unverified)

### Amendment Re-Verification (ROADMAP criterion 4 / REQUIREMENTS OVNT-06)

Unchanged from all prior passes. Plan 21-11's amendment premise — that the agent schedule/Roster
sheet is keyed on business date and therefore an overnight shift already occupies exactly one cell
there — was independently re-derived from `ScheduleOutputService.buildAgentSchedule` (lines 164-170)
and `ScheduleExportService.writeRoster` (line 245), and holds.

### Required Artifacts

Unchanged — no implementation files were modified since the initial verification (confirmed again
this pass: `git diff --stat bc6ecaf~4..HEAD -- src/ frontend/src/` is empty).

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/service/ShiftTemplateService.java` | Overnight save, D-02/D-08 refusals, OVNT-05 containment | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/service/DeskService.java` | D-03/D-04/D-05 day-start refusals/advisory/disclosure | ✓ VERIFIED | Unchanged; all five day-start message types now measured live |
| `src/main/java/com/wfm/service/FteUploadService.java` | CR-01 fix: lookup keyed by business date | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/service/ScheduleExportService.java` | Anchored column/row order, overnight cell disclosure | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/service/ScheduleOutputService.java` | Structured violation fields, shared `timeslotLabel` helper | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` | Anchored contiguity/break scans, business-date day-off join | ✓ VERIFIED | Unchanged |
| `frontend/src/pages/DeskManagement.tsx` | Editable day-start control, lock disclosure, server-message surfacing | ✓ VERIFIED | Unchanged; UAT confirms all 5 D5-named message/geometry types render without clipping live |
| `frontend/src/pages/ScheduleResults.tsx` | Anchored grid (7 sites), business-day heading disclosure | ✓ VERIFIED | Unchanged; UAT confirms anchored ordering/contiguity/heading-wrap live for the slot-mode branch and the pre-existing (unrelated) sticky-column defect; the shift-mode branch's equivalent render is logic-proven but not yet live-rendered (see Human Verification) |
| `frontend/src/utils/dayWindow.ts` | Branded `DayOffset` arithmetic module | ✓ VERIFIED | Unchanged |
| `src/test/resources/midnight-time-arithmetic.md` | Raw-comparison allowlist resolved to exactly 2 entries | ✓ VERIFIED | Unchanged |

### Key Link Verification

Unchanged (no implementation files modified since the initial verification). All links remain
✓ WIRED.

### Behavioral Spot-Checks

Unchanged — not re-run this pass since no implementation file changed. The full-suite confirmation
from the supplied test_state stands: 202 classes, 1303 tests, 0 failures/errors, 4 pre-existing
benchmark skips. The regression gate against Phases 18/19/20 guard classes (DayWindowTest,
MidnightBoundaryRegressionTest, DayWindowAnchorBindingTest, TimeslotGeneratorBusinessDateTest,
MidnightBoundaryPropertyTest) remains green.

### Browser-Measured Backstops

| # | Backstop | Ledger | Result | Status |
|---|----------|--------|--------|--------|
| 1 | Desk Management: all 5 D5-named message/geometry types (non-15-min refusal, D-05 tiling advisory, stranded-template refusal, accepted-schedule lock explanation, accepted-schedule refusal Toast) | WINDOWS.md #11 | All 5 measured clean across the four UAT passes — no clipping, correct edit-mode retention, single-toast discipline, disabled-input confirmation | ✓ CLOSED |
| 2 | Sticky Agent column survives anchored column re-ordering (slot-mode branch) | WINDOWS.md #12 | Declaration/width stable across scroll positions; contiguous 7-cell overnight run confirmed by index adjacency; pre-existing unrelated sticky-pinning defect found and separately filed (#15) | ✓ CLOSED |
| 3 | Business-day heading does not force page scroll at 375px | WINDOWS.md #13 | No clipping/overflow attributable to the heading; tracks the grid's own 801px scroll container rather than the 375px viewport | ✓ CLOSED |
| 4 | Excel Roster-sheet legend does not visually collide with agent rows, viewed in a real spreadsheet application | WINDOWS.md #16 | Structurally unmeasurable here; executor's own proxy (POI cell/coordinate inspection) is not authoritative by its own admission | ✓ WAIVED by operator decision, residual risk accepted |
| 5 | AgentAllocationTab's shift-mode branch: envelope-reached hour styling, shift-group ordering, break-band-crosses-midnight rendering, unfilled-seat-marker placement | No WINDOWS.md entry yet | Never rendered or measured live in any UAT pass; underlying arithmetic is fixture-proven (21-10-SUMMARY.md D2/D3) but the React render of the shift-mode branch specifically was never exercised | ⚠️ OPEN — newly identified this pass |

### Requirements Coverage

Unchanged. All seven OVNT-01..07 requirements remain ✓ SATISFIED on code and test evidence,
independent of the UAT backstops. OVNT-06/OVNT-07's REQUIREMENTS.md checkboxes remain `[ ]`/"Pending"
— correctly so, since this phase has not reached `passed` status.

### Anti-Patterns Found

Unchanged four pre-recorded, operator-accepted findings (WR-01, WR-02, WR-03, IN-01) — re-confirmed
present and unaffected by this pass since no implementation files changed. Of note: WR-03 ("
`AgentAllocationTab`'s envelope-reached check can throw past the end of the business day, with no
error boundary to catch it") sits in the exact shift-mode code region (`isEnvelopeReached`,
`ScheduleResults.tsx:695-698`) that this pass's new human-verification item also covers — the code
review already flagged this region as risk-bearing, independently of this verification's own
DOM-rendering concern; both are open by operator decision and neither blocks the seven OVNT truths.

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `frontend/src/pages/ScheduleResults.tsx:695-698` | `isEnvelopeReached` | Unguarded `RangeError`; no `ErrorBoundary` anywhere | ⚠️ Warning (pre-recorded, WR-03, disposition: open) | Unaffected |
| `src/main/java/com/wfm/service/ShiftTemplateService.java:412-424` | `isAligned` | END-position reinterpretation edge case (WR-01, disposition: open) | ⚠️ Warning (pre-recorded) | Unaffected |
| `src/main/java/com/wfm/service/DeskService.java:298-321` | `setDayStart`'s stranding check | Does not exclude retired template eras (WR-02, disposition: open) | ⚠️ Warning (pre-recorded) | Unaffected |
| `frontend/src/pages/DeskManagement.tsx:109,172-175` | Cancel button | Stays enabled during in-flight save (IN-01, disposition: open) | ℹ️ Info (pre-recorded) | Unaffected; UAT reproduced this live empirically (Save disabled, Cancel not) |

**Previously recorded (second pass), independently confirmed pre-existing:**

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `frontend/src/pages/ScheduleResults.tsx` (Agent column `th`/`td`, multiple sites) | `position: 'sticky', left: 0` | Sticky column never actually pins: resolves against the document (which scrolls), not the table wrapper (`overflowX: auto`, which does not) | ℹ️ Info (WINDOWS.md #15, attribution independently re-verified via `git diff` filtered for `position:`/`sticky`/`overflowX` over the phase range — no matches) | Pre-existing; not introduced by Phase 21 |

### Code Review Finding CR-01 (Blocker, disposition: fixed)

Unchanged — `FteUploadService.java:183` reads `timeslotLookup.computeIfAbsent(ts.getBusinessDate(),
...)`, confirmed fixed, unaffected by this pass. Disposition unchanged: `open: 4 / total: 5` (WR-01,
WR-02, WR-03, IN-01 remain open by operator decision; CR-01 fixed).

### Human Verification Required

One item, newly surfaced by this pass's exhaustive Step 8 harvest. Both items the third pass left
open (the accepted-schedule refusal Toast; the Excel legend collision) are now genuinely resolved —
one measured, one waived — and are not repeated here.

1. **AgentAllocationTab's shift-mode branch has never been rendered or measured live.**
   **Test:** Seed a desk configured for shift-based scheduling (`schedulingMode === 'SHIFT'`) at a
   non-midnight anchor (e.g. 21:00), with an overnight shift template whose envelope crosses the
   anchor (e.g. 22:00–06:00), at least one agent break band crossing the anchor, and one deliberately
   unfilled post-midnight seat. Solve/accept a schedule and open the Allocation grid tab. Confirm,
   with an in-page evaluation where geometry matters: (a) the envelope's covered hours render with
   the "reached" styling rather than the grey/italic "no shift reaches" styling; (b) the break band
   renders across every slot it covers, not only its start slot; (c) the shift group's header row
   lists the earliest anchored shift first; (d) the unfilled-seat marker for the post-midnight slot
   appears under the correct business-day section, not the following calendar date's section.
   **Expected:** All four render correctly, matching the already fixture-proven arithmetic
   (`21-10-SUMMARY.md`'s A3/A3b, A4, A5, A6 checks) rather than the pre-phase defects those checks
   were written to catch (an overnight envelope rendering as entirely unreached; a break band
   crossing the anchor expanding to zero slots; a night shift sorting after a day shift; a
   post-midnight violation keying under the wrong calendar date).
   **Why human:** The underlying transformations are proven correct by executing the real
   `dayWindow.ts` module against representative fixtures (not inferred), but confirming the RENDERED
   React output of this specific, structurally distinct branch is a browser-DOM fact no test runner
   on this surface can assert, and no prior UAT pass exercised a SHIFT-mode schedule — every live
   measurement to date was against the simpler slot-mode branch, which has no envelope-reached or
   shift-group-ordering code path to exercise in the first place.

### Stale Verification Digest

**This phase's own digest:** refreshed this pass using `gsd_run query verification.fingerprint` over
the current contents of all 55 `covered_files` (unchanged list from the prior pass). New digest:
`v2:sha256:1c2003713a7081ea7f49b5852ddb02cb42c405f52a8e431a0b7c145961b1a572`. This re-covers
`.planning/WINDOWS.md` (entries #11 corrected in place, #16 added as `waived`) and `21-UAT.md` (the
`accepted_schedule_refusal_measured` addition and item 4's waiver record), both of which changed
again since the prior digest was computed.

**Not this phase's to fix, carried forward as before:** plan 21-11 edited `.planning/REQUIREMENTS.md`,
`.planning/ROADMAP.md`, `src/test/resources/bday-join-guard.md`, and
`src/main/java/com/wfm/service/ScheduleOutputService.java`, all of which appear in Phase 18, 19,
and/or 20's own `covered_files`. Those phases' `covered_digest` values remain stale relative to
current file contents. This is a milestone-level bookkeeping item, not a Phase 21 blocker.

### Gaps Summary

No must-have truth failed, no required artifact is missing or a stub, no key link is unwired, and all
seven OVNT requirements remain independently confirmed true in the current codebase.

Both items the third pass left open are now genuinely closed: the accepted-schedule refusal Toast is
measured against a real stale-page race, and the Excel legend-collision backstop is waived by
explicit, documented operator decision — a closed decision, not an open item, per this pass's own
instruction and my independent agreement with that reading.

However, running the full, exhaustive Step 8 harvest this pass was specifically asked to run — every
`<human-check>` block in all twelve plans, not just the ones named in the brief — surfaces one new,
narrow, measurable item: `21-10-PLAN.md` Task 2's human-check explicitly requires its six sub-checks
confirmed in BOTH the slot-mode and shift-mode render branches of `AgentAllocationTab`, and only the
slot-mode branch has ever been rendered in a browser. Two of the six sub-checks (envelope-reached
styling, shift-group ordering) exist only in the shift-mode branch and so cannot have been
incidentally exercised by any slot-mode-only pass under any reading; two more (break-band-crosses-
midnight rendering, unfilled-seat-marker placement) were never visually confirmed in either branch.
All four are proven correct at the computation level by the plan's own executed `dayWindow.ts`
fixture script — this is not a logic defect, and it blocks none of the seven OVNT truths — but the
rendered-DOM half of the claim, which this phase's own stated methodology treats as a distinct,
required measurement, is still open for this specific branch.

I considered closing this by analogy (the shift-mode branch shares the same already-proven
`dayWindow` primitives as the slot-mode branch that WAS measured) but declined, for the same reason
the second and third passes declined similar analogies: this phase's own repeated position is that
DOM-rendering is not safely inferable from markup or from a different code path's measured behavior,
and applying a laxer standard here than was just insisted on twice would be the same inconsistency
already rejected.

**Recommendation:** Seed one SHIFT-mode-scheduled desk with an overnight envelope, a break crossing
the anchor, and a deliberately unfilled post-midnight seat (the same throwaway-stack/browser
technique already used successfully three times this session), render the Allocation grid, and
measure the four sub-checks named above. This is a single, narrow, low-risk check — not a new class
of backstop — and the same session technique that closed the last four items would close this one.
Recommend opening a new WINDOWS.md ledger entry for it (distinct from #12, which only ever covered
the sticky-column/contiguous-run question, not the shift-mode-exclusive rendering facts) so it is
tracked rather than rediscovered on a fifth pass. Either measuring it, or an explicit
operator-accepted waiver documented the same way #16 was, clears the last blocker to `passed`.

---

_Verified: 2026-10-03T15:42:00Z_
_Verifier: Claude (gsd-verifier)_
