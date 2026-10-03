---
phase: 21-overnight-shift-templates
verified: 2026-10-03T15:22:12Z
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
covered_digest: "v2:sha256:3544fafce96aa85413727df01caa7a49cd7511a24427bf97f3c436dca2f00399"
re_verification:
  previous_status: human_needed
  previous_score: "7/7"
  gaps_closed:
    - "WINDOWS.md #12 — measured live: 26-column grid (Agent + Hours + 24 anchored slot columns) on a 21:00-anchored desk; anchored ordering confirmed (21:00→20:00, not clock order); overnight run occupies header indices 3-9 with no gap (contiguous); sticky Agent column's declaration/width (232px) was undisturbed by the anchored column re-ordering itself, which is what this item asked"
    - "WINDOWS.md #13 — measured live: heading '(business day: Mon 21:00–Tue 21:00)' renders with scrollW==clientW, scrollH==clientH, overflow:visible at 375px; does not force page-level scroll attributable to itself because it sits inside the grid's own horizontal-scroll container and tracks the 801px table width rather than the 375px viewport — the backstop's underlying concern (clipping / unwanted page overflow) is answered even though the originally-hypothesized wrap never had to occur"
    - "WINDOWS.md #11 (3 of 4 original sub-cases, prior pass) — non-15-minute refusal (two phrasings), D-05 tiling advisory, and stranded-template refusal all measured at 375px with a 52-char desk name: whiteSpace normal, overflow visible, scrollW==clientW and scrollH==clientH on all four — no clipping, row stays in edit mode, tiling advisory arrives alone as required"
    - "WINDOWS.md #11's 4th named sub-case — the accepted-schedule lock explanation (`dayStartLockExplanation`, rendered via `desk.dayStartLockedByScheduleId`) — now measured against a real seeded ACCEPTED schedule (21-UAT.md `lock_explanation_measured`): 340x110/5 lines at 1200px read mode, 152x219/9 lines at 375px locked-edit mode, scrollW==clientW and scrollH==clientH in every case, whiteSpace normal, overflow visible, text wraps rather than overflowing the desk table. The disabled render (`input[type=time].disabled === true`, cell naming the blocking schedule id) was confirmed in the same pass. This is the item this re-verification pass was specifically asked to re-check, and it holds: the component source at `DeskManagement.tsx:154-194` matches exactly what was measured, and the measurement meets the same bar (scrollW/scrollH equality, explicit line/box counts, both viewports) already applied to the other three sub-cases. Genuinely discharged, not inferred."
  gaps_remaining:
    - "WINDOWS.md #11 — a 5th sub-case, distinct from the four already closed above, was identified during this pass's harvest of PLAN.md `<human-check>` blocks (Step 8) and was never previously tracked in WINDOWS.md or listed in either prior VERIFICATION.md: the 'accepted-schedule refusal' Toast (`ConflictException` 'Desk has an accepted schedule (id, start to end)'), reachable only when a stale client page attempts to save a day-start change after the desk has been locked server-side (21-08-PLAN.md Task 2's human-check items 4-5; 21-08-SUMMARY.md coverage id D5 names it explicitly as one of three required geometry targets, alongside the lock-explanation and tiling-advisory targets that ARE now measured). Neither its triggering scenario nor its Toast-container geometry at narrow viewport has ever been exercised live. See reasoning below for why this is not accepted as discharged by analogy to the already-measured, same-container Toast messages."
    - "A second, previously untracked human-check item, harvested the same way: 21-07-PLAN.md Task 1's backstop ('Open an exported workbook for a 21:00-anchored desk and look at the Roster sheet: confirm the restructured vertical legend does not collide with the agent rows above it...'). No WINDOWS.md ledger entry was ever created for this item. 21-07-SUMMARY.md's own coverage entry (id D5) records only an automatable proxy (byte-for-byte POI inspection of a generated workbook's cell values and frozen-pane coordinates) and explicitly states 'a human opening the file in Excel is still the authoritative check this backstop exists for' — i.e. the executor itself did not treat the proxy as sufficient. This item is orthogonal to the browser-based UAT pass (it requires opening an .xlsx file, not a web page) and was never measured by either prior VERIFICATION.md pass."
  regressions: []
human_verification:
  - test: "Simulate a stale client page: open a desk in Desk Management edit mode while it has NO accepted schedule (so the day-start input renders editable), then — without reloading that page — accept a schedule on the same desk via a second tab or a direct API call, then click Save on the first (still-stale) page with a changed day-start value. Do this at a narrow (~375px) viewport with a long desk name, and measure the resulting error Toast's geometry with an in-page evaluation (scrollWidth/clientWidth/scrollHeight/clientHeight), not a screenshot."
    expected: "The server's own refusal text ('Desk has an accepted schedule (<id>, <start> to <end>)') renders in full inside the Toast container without clipping on either axis, exactly once, and the row stays in edit mode with the entered value intact — matching the wrap/no-clip behavior already measured for the other three Toast message types on this same page."
    why_human: "DOM Toast geometry is a rendered-DOM fact, not inferable from markup alone — the same justification this phase has already applied to its other backstop checks. This specific message was never triggered or measured in either UAT pass; the structurally similar, shorter-text 'stranded-template refusal' and longer-text 'D-05 tiling advisory' Toasts were measured instead, which makes a pass likely (same Toast component, this message is ~100 chars against an already-proven-clean 163-char case) but not measured. Applying a weaker bar to this sub-case than was just insisted on for the lock-explanation sub-case would be inconsistent."
  - test: "Export a solved schedule for a 21:00-anchored (or other non-midnight-anchored) desk to Excel and open the resulting .xlsx file in Excel (or an equivalent spreadsheet application, not just programmatic POI inspection). On the Roster sheet, visually confirm the restructured 5-row vertical legend (carrying the new 'shift crossing into the next calendar day' row) does not visually collide with or overlap the agent data rows immediately above it, and that the frozen top row and first column still behave correctly with the taller legend block."
    expected: "The legend renders as 5 distinct, non-overlapping rows below a blank separator row, with no visual collision with agent rows; the freeze pane still holds the header row and first column in place as a human scrolls."
    why_human: "A generated workbook's visual layout in Excel (row heights, font rendering, pane-freeze visual behavior) is not fully assertable from cell values and frozen-pane coordinates alone — this is the executor's own stated rationale for marking this a human-check backstop (21-07-PLAN.md), and the only verification performed to date is a programmatic proxy (byte-for-byte POI row/column read), which the executor's own summary states is not the authoritative check. No WINDOWS.md ledger entry was ever opened to track this item, and neither prior verification pass surfaced it."
---

# Phase 21: Overnight Shift Templates Verification Report

**Phase Goal:** A desk can define a shift that spans midnight, and every surface that touches it —
save-time validation, contracted-hours consumption, day-off blocking, the schedule UI grid, the
Excel export — treats it correctly as one continuous thing belonging to the business day it starts
on.
**Verified:** 2026-10-03T15:22:12Z
**Status:** human_needed
**Re-verification:** Yes — third pass. This pass (1) re-derives whether the newly-measured
accepted-schedule lock explanation genuinely discharges the one item the second pass left open, and
(2) performs a full Step 8 harvest of every `<human-check>` block across all twelve plans in this
phase — a pass the first two verification runs did not complete exhaustively — which surfaces two
further backstop items that were never tracked in WINDOWS.md and never measured.

## What changed since the second verification pass

Only `.planning/WINDOWS.md` and `.planning/phases/21-overnight-shift-templates/21-UAT.md` changed
(confirmed: `git diff --stat 2283e77..HEAD -- src/ frontend/src/` is empty, and `git status --short`
shows no tracked changes to either tree). The second pass's own `21-VERIFICATION.md` content — the
one this pass updates in place — was itself part of that same commit (`2283e77`), and left exactly
one item open: the accepted-schedule lock explanation's DOM geometry, unmeasured because the
seeded throwaway environment had no ACCEPTED schedule on any desk.

### The requested re-check: is the lock explanation now genuinely discharged?

**Yes.** `21-UAT.md`'s new `lock_explanation_measured` block records a second UAT pass that seeded a
desk ("Overnight Operations Desk — Manila Night Coverage (Tier 2)", 58 chars) at a 21:00 anchor with
an ACCEPTED schedule spanning 2026-01-05 to 2026-01-18 — populating all three disclosure fields, the
longest form. Measured:

| Viewport | Mode | Box | Lines | Clipped H | Clipped V | Within table | Table overflows |
|---|---|---|---|---|---|---|---|
| 1200 | read | 340x110 | 5 | no | no | yes | no |
| 375 | edit (locked) | 152x219 | 9 | no | no | yes | no |

`scrollW==clientW` and `scrollH==clientH` in both cases, `whiteSpace: normal`, `overflow: visible`.
The text wraps (5 lines, then 9) rather than overflowing the desk table — the exact outcome this
item's expectation asked for. In the same pass, `input[type=time].disabled === true` on the locked
desk in edit mode, and the cell names the blocking schedule id, confirming 21-08's disabled-render
requirement live.

I independently re-read the rendering component (`frontend/src/pages/DeskManagement.tsx:60-73,
154-194`) rather than take the measurement's field names on faith: `dayStartLockExplanation`,
`desk.dayStartLockedByScheduleId`, `dayStartLockedPeriodStart`/`PeriodEnd` all exist exactly as
named, in both the read-mode cell (line 187-194) and the disabled edit-mode cell (line 154-161), and
the backend fields they read (`DeskResponse.java:25-27`, populated per `DeskService.java:373`,
`dayStartLocksByDeskId`) are real, not invented for the measurement. The measurement's reported box
dimensions are consistent with the component's actual markup (no `maxWidth` on the explanation
`<div>`, full-width input beside it in edit mode narrowing available space, which is why the edit-mode
box is narrower and wraps to more lines than the read-mode box). This sub-case is genuinely
discharged — the evidentiary bar applied to it (live seeded ACCEPTED schedule, in-page geometry
read, both viewports/modes) matches the bar the other three sub-cases were already held to in the
prior pass. **WINDOWS.md #11's originally-identified 4-sub-case scope is now fully measured.**

### What a fuller Step 8 harvest surfaces that the first two passes missed

Verifying the specific item asked about is not the same as verifying the phase goal is achieved —
this pass also re-ran Step 8's duty to harvest every `<human-check>` block across all PLAN.md files
in the phase, not just the three already tracked in WINDOWS.md. That harvest surfaces two items
neither prior pass caught:

**1. A 5th, distinct Desk Management sub-case (still open).** `21-08-SUMMARY.md`'s own coverage
entry D5 states the backstop's scope was three geometry targets, not the four this phase's WINDOWS
ledger ended up tracking: "locked-explanation wrapping/overflow, tiling-warning two-line rendering,
**accepted-schedule refusal long-text/overflow**." The third target — the Toast rendered when
`DeskService.setDayStart` throws `ConflictException("Desk has an accepted schedule (" + id + ", "
+ start + " to " + end + ")")` (`DeskService.java:298-304`) — is a *different* refusal from the
"stranded-template refusal" the UAT measured (`DeskService.java:306-321`, a different `ConflictException`
naming a template, not a schedule). The two are textually and causally distinct: one fires when a
re-anchor would strand an existing shift template; the other fires unconditionally whenever an
ACCEPTED schedule exists, and in the shipped UI is reachable only through a stale-page race (the
input is disabled client-side whenever the desk is *currently known* to be locked, so the only path
to this server refusal is submitting a change made before the lock was known about — exactly the
scenario `21-08-PLAN.md` Task 2's human-check items (4)-(5) name explicitly, and exactly the scenario
`21-08-SUMMARY.md`'s own key-decisions text affirms is real and intentionally server-enforced: "the
server's unconditional refusal is the sole decision-maker, exercised the same way for a stale page
or a direct API call"). Neither UAT pass triggered this scenario or measured this Toast's geometry.

I considered whether this is safe to infer from the two Toast messages that already passed (the
163-char D-05 tiling advisory and the 154-char stranded-template refusal, both in the identical Toast
component, both unclipped) — this message's text is *shorter* (~100 chars) than both, in the exact
same container. That is a stronger structural analogy than the one already rejected for the
lock-explanation sub-case (which was a different container, a static `<div>`, not a Toast). But the
prior pass's own stated reasoning for declining to infer was unqualified: "the whole reason this
class of check exists is that DOM geometry is not safely inferable from markup." Carving out an
exception here — a shorter string, same container — because the inference happens to be stronger
would still be applying a different bar case-by-case rather than a consistent one. **Decision:**
record this as a genuinely open, narrow, low-risk human-verification item rather than close it by
analogy. It does not block any of the seven OVNT truths (all remain code-and-test verified
independent of this Toast's rendering), but it does mean WINDOWS.md #11's "resolved" status, set in
commit `2283e77`, overstates what has actually been measured — one of its three D5-named geometry
targets is still outstanding.

**2. The Excel Roster-sheet legend-collision backstop (never tracked, never measured).**
`21-07-PLAN.md` Task 1's `<human-check>` ("Open an exported workbook for a 21:00-anchored desk and
look at the Roster sheet: confirm the restructured vertical legend does not collide with the agent
rows above it...") has no corresponding WINDOWS.md ledger entry at all — I searched the full ledger
for "legend", "Roster", and "21-07" and found nothing. `21-07-SUMMARY.md`'s own coverage entry (id
D5) is explicit that only an automated proxy was performed (byte-for-byte POI inspection of a real
generated workbook's cell text and frozen-pane coordinates) and that this proxy is not the
authoritative check: "a human opening the file in Excel is still the authoritative check this
backstop exists for." This item is orthogonal to the browser-based UAT measurements (it requires
opening a generated `.xlsx` file in a spreadsheet application, not evaluating a web page's DOM), so
the UAT pass that closed WINDOWS.md #12 and #13 could not have incidentally covered it, and did not
attempt to. Neither prior VERIFICATION.md pass surfaced this item — this is the first pass to run
Step 8's harvest against all twelve plans rather than only the three plans already represented in
WINDOWS.md's unrun-verify entries.

### Net effect on status

Per the decision tree, a non-empty human-verification list routes status to `human_needed` (gaps_found
does not fire — no truth failed, no artifact is missing or a stub, no key link is unwired, and the
code paths for both newly-surfaced items exist and are exercised by passing unit/integration tests;
only their rendered-DOM or rendered-workbook geometry is unmeasured). **Status stays `human_needed`.**
This is not a step backward from the second pass's position — the item that pass asked about is
genuinely closed — but a fuller application of this verifier's own stated methodology (Step 8) finds
the phase was never actually down to zero open backstop items; it was down to one that happened to
be named explicitly in this session's brief, plus two others that had fallen through the ledger's
tracking entirely.

## Goal Achievement

### Observable Truths

Unchanged from both prior passes — all 7 are code-and-test-verified facts that none of the UAT/backstop
measurement work touches, and no implementation file has changed since the initial verification.

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | An operator can save a shift template whose end time is earlier in the clock than its start time, with correct net hours for the overnight span, on an anchored desk (OVNT-01) | ✓ VERIFIED | `ShiftTemplateService.java:51` `MAX_SPAN_MINUTES=16*60`; `validate()` refuses a backward interval naming the desk's own day start; `window.anchoredDurationMinutes(...)` computes net hours. `ShiftTemplateServiceTest` (53 tests, 0 failures). Corroborated live (UAT): a real 22:00-06:00 template SAVED successfully, end-to-end, through the real HTTP API on a 21:00-anchored desk. |
| 2 | That shift is reported against the business day it starts on, everywhere it is displayed (OVNT-02) | ✓ VERIFIED | `ScheduleOutputService.buildAgentSchedule` groups by `a.getTimeslot().getBusinessDate()`; `ScheduleSummary.dayStart`/`ScheduleDetailResponse` carry the schedule's own anchor; structured `ViolationDetail` fields. Corroborated live (UAT): the summary payload carried `"dayStart":"21:00:00"`; the Schedule Results date filter offered exactly ONE date for a run spanning two calendar days. |
| 3 | A day-off or PTO marking on the starting business day blocks the shift from being assigned, including seats stamped with the following calendar date (OVNT-03) | ✓ VERIFIED | `ScheduleConstraintProvider.java:183`: `agentDayOff` join is `equal(a -> a.getTimeslot().getBusinessDate(), AgentDayOff::getDate)`. `MidnightBoundaryRegressionTest$DayOffAttributesToStartingBusinessDate` green. |
| 4 | The shift consumes the contracted hours of the weekday it starts on only, never split across two weekday rows (OVNT-04) | ✓ VERIFIED | `MidnightBoundaryPropertyTest.ContractedHoursStartingWeekdayOnly` proves single business-date derivation for both calendar days of a crossing stretch. |
| 5 | Shift-library validation refuses an overnight template whose envelope does not fit inside its desk's business day (OVNT-05) | ✓ VERIFIED | `isWithinOperatingWindow`/`crossesCalendarMidnight` wired into save path, SHIFT-mode gate, and candidate generation — one predicate, three callers. |
| 6 (amended) | The business-day-keyed schedule UI grid/roster render an overnight shift as one block carrying calendar-span disclosure; per-date slot surfaces render it as one continuous run of cells (OVNT-06) | ✓ VERIFIED | Amendment premise independently re-derived and confirmed true. `ScheduleExportService` sorts by `anchoredStartMinute`/`anchoredShiftSortKey`; `ScheduleResults.tsx` binds one `dayWindow` throughout. Corroborated live (UAT): 26-column allocation grid on a 21:00-anchored desk renders anchored order (21:00→20:00, not clock order); the night agent's 7 overnight cells occupy header indices 3-9 with no gap — a direct index-adjacency measurement, not an inference. |
| 7 | The shift is labelled with the calendar dates it spans wherever displayed (OVNT-07) | ✓ VERIFIED | `ScheduleExportService.shiftCode`/`crossingAwareCode` on Roster sheet; `ScheduleOutputService.timeslotLabel` appends `(business day: {date})`; `ScheduleResults.tsx`'s `sectionHeading` discloses `(business day: {span})`. Corroborated live (UAT): heading rendered live as `"2026-01-05 (business day: Mon 21:00–Tue 21:00)"`, en dash intact through JSON transit. |

**Score:** 7/7 truths verified (0 present, behavior-unverified)

### Amendment Re-Verification (ROADMAP criterion 4 / REQUIREMENTS OVNT-06)

Unchanged from both prior passes. Plan 21-11's amendment premise — that the agent schedule/Roster
sheet is keyed on business date and therefore an overnight shift already occupies exactly one cell
there — was independently re-derived from `ScheduleOutputService.buildAgentSchedule` (lines 164-170)
and `ScheduleExportService.writeRoster` (line 245), and holds.

### Required Artifacts

Unchanged — no implementation files were modified since the initial verification (confirmed again
this pass: `git diff --stat 2283e77..HEAD -- src/ frontend/src/` and `git diff --stat HEAD -- src/
frontend/src/` are both empty).

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/service/ShiftTemplateService.java` | Overnight save, D-02/D-08 refusals, OVNT-05 containment | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/service/DeskService.java` | D-03/D-04/D-05 day-start refusals/advisory/disclosure | ✓ VERIFIED | Unchanged; the ACCEPTED-schedule refusal (line 298-304) and stranded-template refusal (line 306-321) confirmed textually distinct this pass |
| `src/main/java/com/wfm/service/FteUploadService.java` | CR-01 fix: lookup keyed by business date | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/service/ScheduleExportService.java` | Anchored column/row order, overnight cell disclosure | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/service/ScheduleOutputService.java` | Structured violation fields, shared `timeslotLabel` helper | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` | Anchored contiguity/break scans, business-date day-off join | ✓ VERIFIED | Unchanged |
| `frontend/src/pages/DeskManagement.tsx` | Editable day-start control, lock disclosure, server-message surfacing | ✓ VERIFIED | Unchanged; UAT confirms 4 of 5 D5-named message/geometry types render without clipping live; the 5th (accepted-schedule refusal Toast) remains unmeasured (see Human Verification) |
| `frontend/src/pages/ScheduleResults.tsx` | Anchored grid (7 sites), business-day heading disclosure | ✓ VERIFIED | Unchanged; UAT confirms anchored ordering and contiguity live, plus the pre-existing (unrelated) sticky-column defect |
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
| 1a | Desk Management: non-15-min refusal, D-05 tiling advisory, stranded-template refusal (3 of the surface's message types) | WINDOWS.md #11 | All measured clean (no clipping, correct edit-mode retention, tiling advisory alone) | ✓ CLOSED |
| 1b | Desk Management: accepted-schedule lock explanation (always-rendered disclosure `<div>`, the 4th message type) | WINDOWS.md #11 | Measured this pass against a seeded ACCEPTED schedule: wraps (5/9 lines), no clipping, disabled input confirmed, at both 1200px read and 375px locked-edit modes | ✓ CLOSED (newly, this pass) |
| 1c | Desk Management: accepted-schedule refusal Toast (triggered by a stale-page save attempt on a now-locked desk, the 5th D5-named target) | WINDOWS.md #11 (incompletely resolved — see Gaps) | Never triggered or measured in any UAT pass | ⚠️ OPEN — newly identified this pass |
| 2 | Sticky Agent column survives anchored column re-ordering | WINDOWS.md #12 | Declaration/width stable across scroll positions; contiguous 7-cell overnight run confirmed by index adjacency; pre-existing unrelated sticky-pinning defect found and separately filed (#15), independently confirmed not Phase-21-caused | ✓ CLOSED |
| 3 | Business-day heading does not force page scroll at 375px | WINDOWS.md #13 | No clipping/overflow attributable to the heading; tracks the grid's own 801px scroll container rather than the 375px viewport | ✓ CLOSED |
| 4 | Excel Roster-sheet legend does not visually collide with agent rows, viewed in a real spreadsheet application | No WINDOWS.md entry exists | Only a POI-level cell/coordinate proxy performed (21-07-SUMMARY.md D5); executor's own text states this is not the authoritative check; never measured by a human opening the file | ⚠️ OPEN — newly identified this pass |

### Requirements Coverage

Unchanged. All seven OVNT-01..07 requirements remain ✓ SATISFIED on code and test evidence,
independent of the UAT backstops. OVNT-06/OVNT-07's REQUIREMENTS.md checkboxes remain `[ ]`/"Pending"
— correctly so, since this phase has not reached `passed` status.

### Anti-Patterns Found

Unchanged four pre-recorded, operator-accepted findings (WR-01, WR-02, WR-03, IN-01) — re-confirmed
present and unaffected by this pass since no implementation files changed.

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

Two items, both newly surfaced by this pass's full Step 8 harvest (neither was in either prior
pass's human-verification list):

1. **Accepted-schedule refusal Toast geometry** (WINDOWS.md #11, 5th sub-case) — see frontmatter
   `human_verification` block for the full test/expected/why_human. This is distinct from, and not
   discharged by, the lock-explanation sub-case this pass was specifically asked to re-check — that
   one is now genuinely closed.
2. **Excel Roster-sheet legend-collision check** (21-07-PLAN.md, no WINDOWS.md entry) — see
   frontmatter `human_verification` block. Requires opening a generated workbook in Excel; the only
   verification performed to date is a programmatic proxy the executor's own summary states is not
   authoritative.

### Stale Verification Digest

**This phase's own digest:** refreshed this pass using `gsd-tools query verification.fingerprint`
over the current contents of all 55 `covered_files` (unchanged list from the prior pass — `21-UAT.md`
was already added then). New digest: `v2:sha256:3544fafce96aa85413727df01caa7a49cd7511a24427bf97f3c436dca2f00399`.
This re-covers `.planning/WINDOWS.md` (ledger entries flipped to `resolved`, #15 unchanged) and
`21-UAT.md` (the `lock_explanation_measured` addition), both of which changed since the prior digest
was computed.

**Not this phase's to fix, carried forward as before:** plan 21-11 edited `.planning/REQUIREMENTS.md`,
`.planning/ROADMAP.md`, `src/test/resources/bday-join-guard.md`, and
`src/main/java/com/wfm/service/ScheduleOutputService.java`, all of which appear in Phase 18, 19,
and/or 20's own `covered_files`. Those phases' `covered_digest` values remain stale relative to
current file contents. This is a milestone-level bookkeeping item, not a Phase 21 blocker.

### Gaps Summary

No must-have truth failed, no required artifact is missing or a stub, no key link is unwired, and all
seven OVNT requirements remain independently confirmed true in the current codebase.

The specific item this pass was asked to re-check — the accepted-schedule lock explanation's DOM
geometry — is now genuinely discharged by a live measurement against a seeded ACCEPTED schedule,
matching the evidentiary bar already applied to the other three originally-identified sub-cases.
That part of the brief is correct and complete.

However, a fuller application of this verifier's own Step 8 duty (harvesting every `<human-check>`
block across all twelve plans, not only the three already represented in WINDOWS.md) surfaces two
further backstop items that neither of the first two verification passes caught:

- A 5th Desk Management sub-case — the accepted-schedule refusal Toast, distinct from the
  stranded-template refusal that was measured — named explicitly in `21-08-SUMMARY.md`'s own
  coverage block (D5) as one of three required geometry targets, and never triggered or measured.
  WINDOWS.md #11's "resolved" status (set in commit `2283e77`) overstates what has actually been
  measured by this one sub-case.
- The Excel Roster-sheet legend-collision check from `21-07-PLAN.md`, which was never given a
  WINDOWS.md ledger entry at all and has only ever been proxied, not measured, by the executor's own
  admission.

Neither item is treated as a regression (nothing changed; both were always open, just untracked) and
neither blocks any of the seven OVNT truths, which remain fully code-and-test verified. But per the
decision tree, a non-empty human-verification list means status cannot be `passed`.

**Recommendation:** (1) seed an unlocked desk, accept a schedule on it out-of-band, then attempt the
stale save and measure the resulting Toast — the same throwaway-stack technique already used twice
in this session would work; (2) open a real exported `.xlsx` for a 21:00-anchored desk in Excel (or
equivalent) and visually confirm the legend does not collide with the agent rows. Both are narrow,
single checks, not new backstops. Recommend also correcting WINDOWS.md #11's ledger text (currently
marked `resolved` with no inline note, unlike #12/#13) to reflect that one of its three D5-named
geometry targets remains open, and opening a new ledger entry for the 21-07 legend-collision item
so it is tracked going forward. Either closing both remaining items, or an explicit operator-accepted
override for one or both (documented in this file's `overrides:` frontmatter), clears the last
blockers to `passed`.

---

_Verified: 2026-10-03T15:22:12Z_
_Verifier: Claude (gsd-verifier)_
