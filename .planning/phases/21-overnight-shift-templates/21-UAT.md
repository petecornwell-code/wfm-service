---
status: passed
phase: 21-overnight-shift-templates
source: [21-VERIFICATION.md]
started: 2026-10-03T13:20:00Z
updated: 2026-10-03T15:10:00Z
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
result: passed (3 of 4 message types exercised; lock explanation not reachable without an accepted schedule)
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
not_covered: |
  The accepted-schedule lock explanation was not exercised — it requires an ACCEPTED schedule on
  the desk, which this seeded environment did not have. The disclosure helper's text contract was
  proven by 21-08 at the unit level; only its rendered geometry remains unmeasured. Narrower than
  the original ledger item, and recorded rather than claimed.

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

## Summary

total: 3
passed: 3
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

One sub-case of item 1 remains unmeasured: the accepted-schedule lock explanation's rendered
geometry, which needs an ACCEPTED schedule. Its text contract was proven at the unit level in
21-08; only geometry is open, and the item is recorded narrower rather than closed.

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
