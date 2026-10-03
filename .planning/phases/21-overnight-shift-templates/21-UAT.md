---
status: passed
phase: 21-overnight-shift-templates
source: [21-VERIFICATION.md]
started: 2026-10-03T13:20:00Z
updated: 2026-10-03T15:46:00Z
measured_by: orchestrator session via browser automation (geometry read with browser_evaluate; screenshots deliberately not used — they do not settle on this app)
environment: throwaway stack — pgvector Postgres :55432, backend :8081, vite :3001; seeded desk at a 21:00 anchor with 60-minute timeslots, one overnight 22:00-06:00 template, and a COMPLETED schedule carrying dayStart 21:00
---

## Current Test

number: 3
name: Business-day section heading wraps rather than forcing page scroll
expected: |
  The heading's '(business day: Sun 21:00–Mon 21:00)' parenthetical wraps onto a second line
  rather than clipping or forcing the page to scroll horizontally outside the grid's own
  scrolling container.
awaiting: none — measured

## Tests

### 1. Desk Management refusal and advisory messages render without clipping

test: Open Desk Management, edit a desk's day start to a value entered by keyboard (not the picker) that the backend refuses for one of the two reasons the time picker normally makes unreachable (e.g. a value carrying seconds, or a non-15-minute boundary). Also trigger the stranded-template refusal and the accepted-schedule-lock message, and the D-05 tiling-warning toast, at a narrow (~375px) viewport and with a long desk name.
expected: Every refusal/advisory renders the backend's own full message without clipping inside the Toast or table cell; the lock explanation wraps rather than overflowing the desk table at real desk-name widths.
ledger: WINDOWS.md #11
result: passed (all 5 message types exercised across three passes — the 5th, the accepted-schedule refusal, was identified by the third verification run and measured in a third pass)
evidence: |
  Desk name used throughout: "Night Desk Verification With A Deliberately Long Name" (52 chars).
  Control confirmed live first: the day-start cell renders input[type=time][step=900], enabled,
  seeded from the desk's own anchor. Phase 18's "(only 00:00 is supported...)" parenthetical is
  gone, so this phase made that cell's text shorter, not longer.

  Measured, all with scrollW == clientW AND scrollH == clientH (no clipping on either axis),
  whiteSpace: normal, overflow: visible, maxWidth 400px, and exactly ONE toast each:

  | Message | Chars | Viewport | Box | Lines | Clipped | In viewport |
  |---|---|---|---|---|---|---|
  | non-15-min refusal ("Desk day start 21:07 is not a 15-minute boundary") | 48 | 1200 | 356x46 | 2 | no | yes |
  | non-15-min refusal (03:33) | 48 | 375 | 344x67 | 3 | no | yes |
  | D-05 tiling advisory (amber #b45309) | 163 | 375 | 344x110 | 5 | no | yes |
  | stranded-template refusal (red #dc2626), naming "Overnight 22-06 (2026-01-05)" | 154 | 375 | 344x110 | 5 | no | yes |

  Day-start table cell: not clipped on either axis at either width.
  Row correctly stayed in edit mode after every refusal (21-08's requirement), verified each time.
  The tiling advisory arrived ALONE — no success toast alongside it, which 21-08 required explicitly.
  Page-level horizontal scroll does occur at 375px, but it is NOT attributable to this phase: the
  desk table is 760px wide and removing the Day Start column entirely would still leave 609px
  against a 360px client width. Inherent to a 6-column table at phone width, pre-existing.
lock_explanation_measured: |
  Second pass, 2026-10-03T15:12Z. Seeded a desk named "Overnight Operations Desk — Manila Night
  Coverage (Tier 2)" (58 chars) at a 21:00 anchor with an ACCEPTED schedule over 2026-01-05–
  2026-01-18, so all three disclosure fields populated — the LONGEST form, with both optional
  halves present:
    dayStartLockedByScheduleId: cccccccc-0000-4000-8000-000000000011
    dayStartLockedPeriodStart:  2026-01-05
    dayStartLockedPeriodEnd:    2026-01-18

  Rendered text (en dash intact through JSON transit):
    "Locked — accepted schedule blocks day start. cccccccc-0000-4000-8000-000000000011
     (2026-01-05–2026-01-18)."

  | Viewport | Mode | Box | Lines | Clipped H | Clipped V | Within table | Table overflows |
  |---|---|---|---|---|---|---|---|
  | 1200 | read | 340x110 | 5 | no | no | yes | no |
  | 375 | edit (locked) | 152x219 | 9 | no | no | yes | no |

  whiteSpace: normal, overflow: visible at both widths; scrollW == clientW and scrollH == clientH
  in every case. It WRAPS (5 lines then 9) rather than overflowing the desk table — which is
  precisely what this item's expectation asked for.

  The disabled render was confirmed in the same pass: on the locked desk in edit mode,
  input[type=time].disabled === true, and the cell names the blocking schedule id. 21-08's
  "disabled render that names the schedule permanently blocking the change" verified live.

accepted_schedule_refusal_measured: |
  Third pass, 2026-10-03T15:28Z. The third verification run correctly identified that the
  accepted-schedule refusal is a FIFTH, distinct sub-case — 21-08-SUMMARY's coverage entry D5
  names it alongside the lock-explanation and tiling targets, and it is causally different from
  the stranded-template refusal measured in pass one. Flipping ledger #11 to resolved after only
  four sub-cases overstated what had been checked; this pass corrects that.

  Reproduced the genuine stale-page race rather than a shortcut: created the desk UNLOCKED,
  loaded /desk-management, entered edit mode (confirmed input[type=time].disabled === false, so
  the page had no knowledge of any lock), THEN inserted an ACCEPTED schedule server-side behind
  the page's back, THEN saved a day-start change from that stale page. This is the exact path
  DeskService.java:298-304's ConflictException guards.

  Rendered: "Desk has an accepted schedule (dddddddd-0000-4000-8000-000000000099, 2026-02-01 to
  2026-02-28)" — 94 chars, error red #dc2626, naming BOTH the blocking schedule id and its period.

  | Viewport | Box | Lines | Clipped H | Clipped V | In viewport | Single toast | Row held in edit |
  |---|---|---|---|---|---|---|---|
  | 375 | 344x89 | 4 | no | no | yes | yes | yes |

  scrollW == clientW (344) and scrollH == clientH (89); whiteSpace normal, overflow visible,
  maxWidth 400px. Desk name under test: "Stale Page Desk — Locked Behind Your Back (Tier 3
  Overnight)" (60 chars).

### 2. Sticky Agent column survives anchored column re-ordering

test: Open a solved schedule on a 21:00-anchored desk with 24+ anchored slot columns in the Allocation grid tab and scroll the table horizontally.
expected: The sticky Agent column's left offset is undisturbed by the anchored column re-ordering; the overnight shift's cells still read as one contiguous highlighted run.
ledger: WINDOWS.md #12
result: passed, with a pre-existing defect found and separately recorded (WINDOWS.md #15)
evidence: |
  Grid rendered 26 columns = Agent + Hours + 24 slot columns, satisfying the 24+ requirement.

  Column order was ANCHORED, not clock: 21:00, 22:00, 23:00, 00:00, 01:00, ... through 20:00 —
  the full business day beginning at the desk's own anchor. Under the pre-phase clock ordering
  this would have read 00:00 ... 23:00.

  The overnight run is ONE UNBROKEN RUN, which is the substantive OVNT-06 claim: the night agent
  occupies header indices 3-9 (22:00, 23:00, 00:00, 01:00, 02:00, 03:00, 04:00) with no gap —
  verified by asserting every index equals its predecessor + 1. Under clock ordering those seven
  cells would have split into indices 3-7 plus 24-25: two fragments at opposite ends of the row,
  exactly what OVNT-06 forbids.

  Sticky column, as the item asks: UNDISTURBED by the re-ordering. Declaration intact
  (position: sticky, left: 0px, zIndex 1, opaque #fff background on both th and td) and width
  stable at 232px across scrollLeft 0, 300 and full scroll.
defect_found: |
  Separately, and NOT caused by this phase: the Agent column never actually pins. Scrolling the
  document horizontally to its limit (654px) moved the column to left -366px / right -134px —
  entirely off-screen. position: sticky resolves against the nearest scrolling ancestor, and the
  table wrapper carries overflowX: auto while the DOCUMENT is what scrolls, so sticky is inert in
  this layout.

  Attribution checked against git rather than assumed: `git diff <phase-base>..HEAD -- ScheduleResults.tsx`
  filtered for position:/sticky/overflowX returns NOTHING. Phase 21 reordered columns; it did not
  touch the sticky or overflow CSS. The defect predates the phase.

  Worth acting on anyway: the 24-column anchored layout makes horizontal scrolling more likely
  than before, so this phase increases exposure to a pre-existing defect it did not introduce.
  Recorded as WINDOWS.md #15.

### 3. Business-day section heading wraps rather than forcing page scroll

test: View the per-business-day section heading on the Schedule Results grid for a 21:00-anchored desk at a narrow viewport (~375px), and check it does not force horizontal scroll on the page itself.
expected: The heading's '(business day: Sun 21:00–Mon 21:00)' parenthetical wraps onto a second line rather than clipping or forcing the page to scroll horizontally outside the grid's own scrolling container.
ledger: WINDOWS.md #13
result: passed — on the substance, though the expectation's premise does not hold
evidence: |
  Heading rendered live as "2026-01-05 (business day: Mon 21:00–Tue 21:00)" (46 chars, H4),
  en dash intact through JSON transit.

  NOT clipped: scrollW 801 == clientW 801, scrollH 24 == clientH 24, whiteSpace: normal,
  overflow: visible.

  It does not wrap — but it does not need to. Its box is 801px wide because it sits INSIDE the
  grid's own horizontal scroll container (confirmed by walking the ancestor chain for
  overflowX: auto|scroll) and therefore tracks the TABLE's content width, not the viewport's.
  So the expectation's premise — that a 46-character heading at 375px would have to wrap or clip
  — does not hold in this layout, and the thing it guards against does not occur: the heading
  forces no page scroll attributable to itself, because it is inside the scrolling container the
  item explicitly excludes.

  Page-level X scroll at 375px exists but comes from the app shell plus the 24-column table, the
  same pre-existing cause as item 1.

### 5. Shift-mode branch of AgentAllocationTab (21-10 Task 2, both-branches requirement)

test: Verify 21-10 Task 2's six sub-checks in the SHIFT-mode render branch of AgentAllocationTab, not only the slot-mode branch every prior pass exercised.
expected: Anchored column order, anchored shift-group ordering, full-day column regeneration on a wrapping window, break-band-crosses-midnight rendering, envelope-reached hour styling, and unfilled-seat-marker placement all correct in the shift-mode branch.
ledger: WINDOWS.md #17
result: 5 of 6 measured and correct; the 6th is UNREACHABLE BY CONSTRUCTION, not unmeasured (see below)
evidence: |
  Fourth pass, 2026-10-03T15:40Z. Seeded a SHIFT-mode desk at a 21:00 anchor with
  schedulingMode 'SHIFT' confirmed on the detail payload (the frontend branches on
  `schedule.schedulingMode !== 'SHIFT'` at ScheduleResults.tsx:487, so this is what selects the
  previously-unexercised branch). Deliberately used a FULL-DAY WRAPPING window —
  start_time == end_time == 21:00 — which is the exact input that made the pre-21-10 code
  regenerate zero columns.

  1. Full-day regeneration on a wrapping window — PASS. 24 slot columns (26 total) regenerated.
     A start-position read of the window's end would have made spanMinutes 0 and produced zero
     regenerated columns; 24 is the post-fix behaviour.
  2. Anchored column order — PASS. 21:00, 22:00, 23:00, 00:00 ... through 20:00, beginning at the
     desk's own anchor rather than 00:00.
  3. Anchored shift-group ordering — PASS. "Overnight 22-06 · 22:00–06:00" renders BEFORE
     "Daytime 09-17 · 09:00–17:00". Anchored minutes at a 21:00 anchor are 60 and 720; by clock
     order 09:00 < 22:00 would have inverted them.
  4. Break band crossing midnight — PASS. The night agent's 01:00 cell renders "B" on a distinct
     grey (rgb(229,231,235)) between worked blue cells (rgb(59,130,246)) at 00:00 and 02:00. The
     band offset is +180 minutes from a 22:00 start, i.e. PAST midnight, and lands in the correct
     cell.
  5. Envelope-reached hour styling, across midnight — PASS. Header colour splits exactly on
     envelope membership: rgb(107,114,128) for 22:00-05:00 and 09:00-16:00 (reached),
     rgb(156,163,175) for 21:00, 06:00-08:00 and 17:00-20:00 (not reached). A 22:00-06:00
     envelope correctly marks 00:00-05:00 as inside with 06:00 excluded — a raw clock comparison
     would never place 00:00 between 22:00 and 06:00. This is `anchoredContains` +
     `anchoredPlusWithinDay` proven across the boundary in the live DOM.
unreachable_sub_check: |
  6. Unfilled-seat-marker placement — UNREACHABLE IN CURRENT PRODUCTION CODE, and the cause is
  pre-existing ledger #14, whose significance this pass revises upward.

  Traced end to end rather than inferred:
  - ScheduleResults.tsx:391-402 builds `unfilledSlots` ONLY from "Unassigned assignment"
    constraint violations, keyed `${businessDate}|${startTime}` — the structured channel 21-03
    created and 21-10 re-pointed the parser onto.
  - Those violations come from ScheduleOutputService.buildConstraintViolations' LIVE-solver path
    (`solutionManager.explain`), not the accepted-snapshot path — confirmed by flipping the seeded
    schedule to ACCEPTED, which yielded constraintViolations: 0.
  - That path's justification loop (ScheduleOutputService.java:683-709) populates businessDate,
    calendarDate, slotStartTime, slotEndTime and timeslotId ONLY under
    `if (justification instanceof AgentAssignment aa)`. There is no `instanceof Timeslot` branch.
  - Ledger #14 records, confirmed empirically against the real solver in plan 21-11, that the
    "Unassigned assignment" ConstraintMatch indicts Timeslot + int + TimeslotDemandConfig +
    ScheduleConfig and never an individual AgentAssignment.

  Therefore every ViolationDetail this constraint emits carries a NULL businessDate and NULL
  startTime, the frontend's map key can never match a real slot, and the unfilled-seat marker
  cannot render in EITHER branch of the grid — independently of this phase's work.

  This revises #14: it was filed as a cosmetic dead description string, out of scope. It is
  actually the reason an operator-facing marker never appears. Still not a Phase 21 regression —
  the constraint's match shape predates the phase, and 21-03/21-10's structured-field work is
  correct and does populate for every constraint that DOES indict an AgentAssignment — but it is
  a live gap worth its own fix, not a cosmetic note. Recorded on #14 and cross-referenced from #17.

### 4. Excel Roster-sheet legend collision (WAIVED by operator)

test: Export a schedule for a 21:00-anchored desk to Excel and open the .xlsx in Excel, checking that the Roster sheet's vertical five-row legend does not collide with or obscure the roster content beside it.
expected: The legend occupies its own rows without overlapping roster cells, and the widened date columns remain readable.
ledger: WINDOWS.md #16
result: waived — operator decision, 2026-10-03
why_waived: |
  Identified by the third verification run; it had never been given a ledger entry. 21-07-SUMMARY's
  own coverage block states that only a POI-level programmatic proxy was performed and that "a
  human opening the file in Excel is still the authoritative check this backstop exists for."

  This check is structurally unavailable to the orchestrator session: there is no tool here that
  renders an .xlsx the way Excel does, so it cannot be measured, only inferred — and inference is
  the bar this UAT has refused for every other item. Rather than lower that bar or leave the phase
  indefinitely pending on a check no automated agent in this setup can perform, the operator
  waived it explicitly.

  Residual risk accepted: a legend/roster collision on the Roster sheet would be cosmetic in the
  exported workbook. It would not affect any of the seven OVNT truths, all of which are verified
  in code and tests, and it cannot corrupt data — the POI-level proxy already confirmed cell
  placement programmatically. Tracked as WINDOWS.md #16 so /gsd-audit-uat surfaces it.

## Summary

total: 5
passed: 4
issues: 0
pending: 0
skipped: 0
waived: 1
unreachable: 1
blocked: 0

## Gaps

None outstanding. All three ledger items (#11, #12, #13) are measured and closed; item 1's
fourth sub-case, open after the first pass, was measured in a second pass against a seeded
ACCEPTED schedule (see lock_explanation_measured above).

One pre-existing defect was found while measuring item 2 — the Agent column's `position: sticky`
is inert under document-level horizontal scroll. Verified by git diff NOT to be Phase 21's doing,
and filed as WINDOWS.md #15 rather than folded into this phase's verdict.

Incidental confirmations gathered while measuring, each through the real HTTP API or live DOM
rather than by reading source:
- An overnight 22:00-06:00 template SAVED successfully on a 21:00-anchored desk (OVNT-01, the
  phase's headline capability, end-to-end).
- The schedule summary payload carries `"dayStart":"21:00:00"` (21-09).
- The Schedule Results date filter offered exactly ONE date for a run spanning two calendar days
  (OVNT-02 — the shift reports against the business day it starts on).
- IN-01 reproduced empirically: during an in-flight day-start save, Save is disabled and Cancel is
  not.
