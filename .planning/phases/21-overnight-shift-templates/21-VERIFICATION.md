---
phase: 21-overnight-shift-templates
verified: 2026-10-03T15:48:58Z
status: passed
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
covered_digest: "v2:sha256:66ec6e9d07263a997692cc0efca0ad55d36dab5f59df97602ee682e890569ec3"
re_verification:
  previous_status: human_needed
  previous_score: "7/7"
  gaps_closed:
    - "21-10-PLAN.md Task 2's shift-mode-branch human-check (WINDOWS.md #17): 5 of its 6 sub-checks measured live against a seeded SHIFT-mode desk at a 21:00 anchor with a full-day wrapping window (21-UAT.md item 5) — full-day column regeneration on a wrapping window, anchored column order, anchored shift-group ordering (Overnight before Daytime), break band crossing midnight rendering 'B' on the correct post-midnight cell, and envelope-reached header styling splitting exactly on envelope membership across the midnight boundary."
    - "The 6th sub-check (unfilled-seat-marker placement) is reclassified from an open human-verification item to a defect finding, not a gap this phase can close by measurement. Traced and independently re-confirmed this pass: ScheduleOutputService.java's justification loop (lines 683-709) populates businessDate/calendarDate/slotStartTime/slotEndTime/timeslotId ONLY inside `if (justification instanceof AgentAssignment aa)` — grep confirms no `instanceof Timeslot` branch exists anywhere in the file. The 'Unassigned assignment' constraint (ScheduleConstraintProvider.java:157-172) is a groupBy/join/join/filter aggregate over AgentAssignment.class via forEachIncludingUnassigned, whose ConstraintMatch indicts the join tuple (Timeslot, int, TimeslotDemandConfig, ScheduleConfig) — never an individual AgentAssignment. So every ViolationDetail this constraint emits carries null businessDate/startTime, and ScheduleResults.tsx:391-402's `${businessDate}|${startTime}` map key can never match a real slot in EITHER render branch. There is no human act or measurement that would discharge this sub-check as currently implemented — the production path feeding it is inert by construction, independent of this phase's own work, which is confirmed correct for every constraint that DOES indict an AgentAssignment (21-03/21-10's structured-field channel)."
  gaps_remaining: []
  regressions: []
  full_harvest_confirmation: |
    Re-ran the complete Step 8 harvest (grep for <human-check> across all twelve plans): the same
    six blocks found on the fourth pass are the only ones in the phase (21-01 Task 1; 21-07 Task 1;
    21-08 Task 1 and Task 2; 21-10 Task 2 and Task 3). No new block exists. Every sub-item across
    all six is now dispositioned as CLOSED (measured live), WAIVED (operator decision, WINDOWS.md
    #16), or a defect finding routed off the human-verification path (WINDOWS.md #14/#17, this
    pass). The human-verification list is empty for the first time this phase.
  implementation_stability_confirmation: |
    `git log --oneline -- src/ frontend/src/` shows the last implementation-touching commit as
    `d6b940e` (fix(21-CR-01): key FTE upload's timeslot lookup by business date). Every commit since
    (`c8ab386` through `21c9ad2`, five verification/UAT passes) is docs/test-artifact only. `git diff
    --stat bc6ecaf~4..HEAD -- src/ frontend/src/` is empty, confirmed again this pass. The seven
    OVNT truths rest on an implementation that has not moved across all five verification passes.
---

# Phase 21: Overnight Shift Templates Verification Report

**Phase Goal:** A desk can define a shift that spans midnight, and every surface that touches it —
save-time validation, contracted-hours consumption, day-off blocking, the schedule UI grid, the
Excel export — treats it correctly as one continuous thing belonging to the business day it starts
on.
**Verified:** 2026-10-03T15:48:58Z
**Status:** passed
**Re-verification:** Yes — fifth and final pass. This pass (1) independently re-traces, rather than
takes on faith, the chain that makes the fourth pass's one open item unreachable-by-construction,
(2) measures that item's five discharge-able sub-checks live against a seeded SHIFT-mode desk, and
(3) re-runs the complete Step 8 harvest once more to confirm nothing further remains.

## What changed since the fourth verification pass

Only `.planning/WINDOWS.md` and `21-UAT.md` changed again (`git diff --stat bc6ecaf~4..HEAD --
src/ frontend/src/` is empty; `git status --short` shows only the untracked `.planning/state.json`
and `.playwright-mcp/` scratch artifacts, neither in `covered_files`). WINDOWS.md ledger entry #17
moved from `open` to `partial`; #14's description was revised in place to record the upgraded
significance this pass independently re-confirmed. `21-UAT.md` gained item 5 (the shift-mode
measurement) and its `unreachable_sub_check` evidence block, and its summary counts now read
`total: 5, passed: 4, waived: 1, unreachable: 1`.

### Independent re-trace of the "unreachable by construction" claim

The brief asked this pass to verify the chain itself rather than accept the narrative. Read directly:

- `ScheduleResults.tsx:391-402` builds `unfilledSlots` only from violations where
  `cv.constraintName === 'Unassigned assignment'`, keyed `${v.businessDate}|${toHHMM(v.startTime)}`,
  and explicitly skips any violation missing either field (`if (!v.businessDate || !v.startTime)
  continue`).
- `ScheduleOutputService.java`'s justification loop (lines 680-709, confirmed by direct read) sets
  `agentId`, `agentName`, `timeslotId`, `businessDate`, `calendarDate`, `slotStartTime`,
  `slotEndTime`, `timeslotLabel`, and `specName` ONLY inside `if (justification instanceof
  AgentAssignment aa)`. `grep -n "instanceof Timeslot"` over the full file returns nothing — there
  is no second branch that would populate these fields from any other indicted-object type.
- `ScheduleConstraintProvider.java:157-172`'s `unassignedAssignment` constraint is built as
  `factory.forEachIncludingUnassigned(AgentAssignment.class).groupBy(a -> a.getTimeslot(), sum(...))
  .join(TimeslotDemandConfig.class, ...).join(ScheduleConfig.class).filter(...)
  .asConstraint("Unassigned assignment")`. A `groupBy`+`join`+`join` chain's `ConstraintMatch`
  indicts the tuple the stream carries at the point of `.filter()` — here `(Timeslot, int,
  TimeslotDemandConfig, ScheduleConfig)` — never an individual `AgentAssignment`, by construction of
  how the stream was built, not as a matter of runtime data.
- Net: `match.getIndictedObjectList()` for every "Unassigned assignment" violation contains no
  `AgentAssignment` instance, so the `instanceof AgentAssignment aa` branch never executes for this
  constraint, so `businessDate`/`startTime` are always null on its `ViolationDetail`s, so the
  frontend's map key can never be built, so the unfilled-seat marker cannot render for this
  constraint in either grid branch.

The chain holds up under independent re-trace. This is a correct, pre-existing (not phase-21-
introduced) defect in a different code region than this phase's own 21-03/21-10 structured-
attribution work, which is independently confirmed correct here: the same `instanceof
AgentAssignment` branch populates all five fields correctly for every OTHER constraint whose
`ConstraintMatch` does indict an `AgentAssignment` (e.g. "Agent day off", "Contracted hours"),
which is what every other OVNT truth and backstop in this phase actually depends on.

**Disposition:** per this pass's brief, a sub-check whose production path is inert by construction
is not an open human-verification item — no human action or measurement would discharge it as
currently implemented. It is a defect finding. It is recorded as WINDOWS.md #14 (revised
significance, status remains `open` — it is a real, live gap, just not this phase's to fix) and
cross-referenced from #17 (status `partial`: 5 of 6 sub-checks measured and closed, the 6th
dispositioned as above rather than left pending). It does not block Phase 21's own seven truths:
OVNT-06's actual claim is that a FILLED overnight shift renders as one contiguous run (measured,
both branches), not that every unfilled-seat marker renders correctly — that marker's rendering
depends on a different constraint's match shape, pre-existing and out of this phase's scope to fix.

### Complete Step 8 re-harvest (confirms nothing further remains)

Re-ran the full harvest across all twelve plans. The same six `<human-check>` blocks found on the
fourth pass are the complete set (21-01 Task 1; 21-07 Task 1; 21-08 Task 1 and Task 2; 21-10 Task 2
and Task 3) — no new block exists anywhere in the phase's plans. Every sub-item is now dispositioned:

| Source | Item | Disposition |
|--------|------|-------------|
| 21-01 Task 1 | 00:00-anchored desk's error Toast | **CLOSED** — human-verified live at plan execution time |
| 21-07 Task 1 | Excel Roster legend collision | **WAIVED** by explicit operator decision (WINDOWS.md #16) |
| 21-08 Task 1 | Picker stepping; disabled render; lock explanation wrap | **CLOSED** — measured live |
| 21-08 Task 2 (5 sub-items) | Tiling save; non-dividing warning; keyboard refusal; stale-page refusal; narrow-viewport geometry | **CLOSED** — all 5 measured live |
| 21-10 Task 2 (1)(2) | Contiguous run; anchored column order | **CLOSED** — measured in both slot-mode (pass 2) and shift-mode (pass 5) branches |
| 21-10 Task 2 (3) | Envelope-reached hour styling | **CLOSED** — measured live, shift-mode branch (pass 5) |
| 21-10 Task 2 (4) | Break band crossing midnight | **CLOSED** — measured live, shift-mode branch (pass 5) |
| 21-10 Task 2 (5) | Shift group ordering by anchored start | **CLOSED** — measured live, shift-mode branch (pass 5) |
| 21-10 Task 2 (6) | Unfilled-seat marker under its own business-day section | **DEFECT FINDING, not a human-verification item** — production path inert by construction (WINDOWS.md #14/#17); no measurement would discharge it |
| 21-10 Task 2, 00:00-anchor regression repeat | Grid unchanged at midnight anchor | **CLOSED** — byte-identical fixture proof |
| 21-10 Task 3 (1)(2)(3) | Heading text; wrap-not-scroll; sticky column | **CLOSED** — measured live |
| 21-10 Task 3, 00:00-anchor regression repeat | Heading renders bare date, unchanged | **CLOSED** — byte-identical fixture proof |

The human-verification list is empty. No block, sub-item, or facet of this phase's `<human-check>`
inventory remains open, pending, or unmeasured in a way a future pass could discharge.

### Net effect on status

Per the decision tree: no truth FAILED, no artifact MISSING/STUB, no key link NOT_WIRED, no blocker
anti-pattern (the four pre-existing warning/info findings — WR-01, WR-02, WR-03, IN-01 — remain open
by prior explicit operator decision, unaffected by this pass). Step 8 now produces **zero** human
verification items — the one item open after the fourth pass is either measured (5 of 6 sub-checks)
or reclassified as a non-discharge-able defect finding (the 6th), per this pass's explicit
instruction. **Status moves to `passed`.**

## Goal Achievement

### Observable Truths

Re-confirmed this pass; no implementation file has changed across any of the five verification
passes (see `implementation_stability_confirmation` above).

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | An operator can save a shift template whose end time is earlier in the clock than its start time, with correct net hours for the overnight span, on an anchored desk (OVNT-01) | ✓ VERIFIED | `ShiftTemplateService.java:51` `MAX_SPAN_MINUTES=16*60`; `validate()` refuses a backward interval naming the desk's own day start; `window.anchoredDurationMinutes(...)` computes net hours. `ShiftTemplateServiceTest` (53 tests, 0 failures). Live: a real 22:00-06:00 template SAVED successfully through the real HTTP API on a 21:00-anchored desk; the D-02 refusal Toast on a 00:00-anchored desk was human-verified live during plan 21-01's own execution. |
| 2 | That shift is reported against the business day it starts on, everywhere it is displayed (OVNT-02) | ✓ VERIFIED | `ScheduleOutputService.buildAgentSchedule` groups by `a.getTimeslot().getBusinessDate()`; `ScheduleSummary.dayStart`/`ScheduleDetailResponse` carry the schedule's own anchor; structured `ViolationDetail` fields. Live: the summary payload carried `"dayStart":"21:00:00"`; the Schedule Results date filter offered exactly ONE date for a run spanning two calendar days. |
| 3 | A day-off or PTO marking on the starting business day blocks the shift from being assigned, including seats stamped with the following calendar date (OVNT-03) | ✓ VERIFIED | `ScheduleConstraintProvider.java:183`: `agentDayOff` join is `equal(a -> a.getTimeslot().getBusinessDate(), AgentDayOff::getDate)`. `MidnightBoundaryRegressionTest$DayOffAttributesToStartingBusinessDate` green. |
| 4 | The shift consumes the contracted hours of the weekday it starts on only, never split across two weekday rows (OVNT-04) | ✓ VERIFIED | `MidnightBoundaryPropertyTest.ContractedHoursStartingWeekdayOnly` proves single business-date derivation for both calendar days of a crossing stretch. |
| 5 | Shift-library validation refuses an overnight template whose envelope does not fit inside its desk's business day (OVNT-05) | ✓ VERIFIED | `isWithinOperatingWindow`/`crossesCalendarMidnight` wired into save path, SHIFT-mode gate, and candidate generation — one predicate, three callers. |
| 6 (amended) | The business-day-keyed schedule UI grid/roster render an overnight shift as one block carrying calendar-span disclosure; per-date slot surfaces render it as one continuous run of cells (OVNT-06) | ✓ VERIFIED | Amendment premise independently re-derived and confirmed true. `ScheduleExportService` sorts by `anchoredStartMinute`/`anchoredShiftSortKey`; `ScheduleResults.tsx` binds one `dayWindow` throughout both render branches. Live, slot-mode branch: 26-column grid renders anchored order (21:00→20:00), the night agent's 7 overnight cells occupy header indices 3-9 with no gap. Live, shift-mode branch (this pass): the same contiguous-run and anchored-order facts hold at a 21:00 anchor with a full-day wrapping window; the shift-group header correctly lists "Overnight 22-06" before "Daytime 09-17" (anchored minutes 60 vs 720); the envelope-reached header styling splits exactly on envelope membership across midnight. Both structurally distinct render branches are now live-measured. |
| 7 | The shift is labelled with the calendar dates it spans wherever displayed (OVNT-07) | ✓ VERIFIED | `ScheduleExportService.shiftCode`/`crossingAwareCode` on Roster sheet; `ScheduleOutputService.timeslotLabel` appends `(business day: {date})`; `ScheduleResults.tsx`'s `sectionHeading` discloses `(business day: {span})`. Live: heading rendered as `"2026-01-05 (business day: Mon 21:00–Tue 21:00)"`, en dash intact through JSON transit. |

**Score:** 7/7 truths verified (0 present, behavior-unverified)

### Amendment Re-Verification (ROADMAP criterion 4 / REQUIREMENTS OVNT-06)

Unchanged from all prior passes. Plan 21-11's amendment premise — that the agent schedule/Roster
sheet is keyed on business date and therefore an overnight shift already occupies exactly one cell
there — was independently re-derived from `ScheduleOutputService.buildAgentSchedule` (lines 164-170)
and `ScheduleExportService.writeRoster` (line 245), and holds.

### Required Artifacts

No implementation files were modified since the initial verification (confirmed again this pass via
`git log --oneline -- src/ frontend/src/`: last touch `d6b940e`).

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/service/ShiftTemplateService.java` | Overnight save, D-02/D-08 refusals, OVNT-05 containment | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/service/DeskService.java` | D-03/D-04/D-05 day-start refusals/advisory/disclosure | ✓ VERIFIED | Unchanged; all five day-start message types measured live |
| `src/main/java/com/wfm/service/FteUploadService.java` | CR-01 fix: lookup keyed by business date | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/service/ScheduleExportService.java` | Anchored column/row order, overnight cell disclosure | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/service/ScheduleOutputService.java` | Structured violation fields, shared `timeslotLabel` helper | ✓ VERIFIED | Unchanged. Independently re-traced this pass: the `instanceof AgentAssignment` branch populates all five structured fields correctly for every constraint whose match indicts an `AgentAssignment`; the "Unassigned assignment" constraint's match never does, which is a pre-existing, separately-tracked defect (WINDOWS.md #14), not a correctness gap in this file's own OVNT-02/OVNT-07 work |
| `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` | Anchored contiguity/break scans, business-date day-off join | ✓ VERIFIED | Unchanged |
| `frontend/src/pages/DeskManagement.tsx` | Editable day-start control, lock disclosure, server-message surfacing | ✓ VERIFIED | Unchanged; UAT confirms all 5 D5-named message/geometry types render without clipping live |
| `frontend/src/pages/ScheduleResults.tsx` | Anchored grid (7 sites), business-day heading disclosure | ✓ VERIFIED | Unchanged; UAT confirms anchored ordering/contiguity/heading-wrap/envelope-reached styling/shift-group ordering/break-band rendering live in BOTH render branches; the pre-existing (unrelated) sticky-column defect remains separately filed (#15); the unfilled-seat marker's non-render for this specific constraint is a defect in its upstream data source (#14), not in this file's keying logic, which is correct |
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
| 1 | Desk Management: all 5 D5-named message/geometry types | WINDOWS.md #11 | All 5 measured clean — no clipping, correct edit-mode retention, single-toast discipline, disabled-input confirmation | ✓ CLOSED |
| 2 | Sticky Agent column survives anchored column re-ordering (slot-mode branch) | WINDOWS.md #12 | Declaration/width stable across scroll positions; contiguous 7-cell overnight run confirmed by index adjacency; pre-existing unrelated sticky-pinning defect found and separately filed (#15) | ✓ CLOSED |
| 3 | Business-day heading does not force page scroll at 375px | WINDOWS.md #13 | No clipping/overflow attributable to the heading; tracks the grid's own 801px scroll container rather than the 375px viewport | ✓ CLOSED |
| 4 | Excel Roster-sheet legend does not visually collide with agent rows, viewed in a real spreadsheet application | WINDOWS.md #16 | Structurally unmeasurable here; executor's own proxy (POI cell/coordinate inspection) is not authoritative by its own admission | ✓ WAIVED by operator decision, residual risk accepted |
| 5 | AgentAllocationTab's shift-mode branch: envelope-reached styling, shift-group ordering, break-band-crosses-midnight rendering, full-day column regeneration, anchored column order | WINDOWS.md #17 | 5 of 6 sub-checks measured live against a seeded SHIFT-mode desk at a 21:00 anchor with a full-day wrapping window — all correct | ✓ CLOSED (5/6) |
| 6 | Unfilled-seat-marker placement in the shift-mode branch | WINDOWS.md #14/#17 | Traced end-to-end and independently re-confirmed this pass: the "Unassigned assignment" constraint's `ConstraintMatch` never indicts an `AgentAssignment`, so its `ViolationDetail`s carry null businessDate/startTime and the frontend's map key can never match a real slot — unreachable by construction, in either render branch | ⚠️ DEFECT FINDING, not a human-verification item — recorded on the ledger, not blocking this phase |

### Requirements Coverage

All seven OVNT-01..07 requirements are ✓ SATISFIED on code and test evidence, independent of the
UAT backstops. `REQUIREMENTS.md`'s OVNT-06/OVNT-07 checkboxes and phase-tracker rows still read
`[ ]`/"Pending" as of this pass's read of the file — that bookkeeping update is outside this
verifier's scope (it is not a `covered_files` edit this agent should make mid-verification, since it
would change the digest just computed); flagged here so the orchestrator or `/gsd-ship` flow updates
it now that the phase has reached `passed`.

### Anti-Patterns Found

Unchanged four pre-recorded, operator-accepted findings (WR-01, WR-02, WR-03, IN-01) — re-confirmed
present and unaffected by this pass since no implementation files changed. WR-03
("`AgentAllocationTab`'s envelope-reached check can throw past the end of the business day, with no
error boundary to catch it") sits in the same shift-mode code region (`isEnvelopeReached`,
`ScheduleResults.tsx:695-698`) exercised live by this pass's backstop #5 measurement — exercised
without throwing in the seeded scenario, consistent with the code review's framing of this as a risk
for an edge case not hit here, not a confirmed live defect.

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `frontend/src/pages/ScheduleResults.tsx:695-698` | `isEnvelopeReached` | Unguarded `RangeError`; no `ErrorBoundary` anywhere | ⚠️ Warning (pre-recorded, WR-03, disposition: open) | Unaffected |
| `src/main/java/com/wfm/service/ShiftTemplateService.java:412-424` | `isAligned` | END-position reinterpretation edge case (WR-01, disposition: open) | ⚠️ Warning (pre-recorded) | Unaffected |
| `src/main/java/com/wfm/service/DeskService.java:298-321` | `setDayStart`'s stranding check | Does not exclude retired template eras (WR-02, disposition: open) | ⚠️ Warning (pre-recorded) | Unaffected |
| `frontend/src/pages/DeskManagement.tsx:109,172-175` | Cancel button | Stays enabled during in-flight save (IN-01, disposition: open) | ℹ️ Info (pre-recorded) | Unaffected; UAT reproduced this live empirically (Save disabled, Cancel not) |
| `src/main/java/com/wfm/service/ScheduleOutputService.java:683-709` | justification loop | Only `instanceof AgentAssignment` branch populates structured ViolationDetail fields; "Unassigned assignment" constraint's match never supplies one, so its violations carry null attribution and the unfilled-seat marker cannot render for this constraint | ⚠️ Warning (WINDOWS.md #14, revised significance this pass; disposition: open, pre-existing, not a Phase 21 regression) | Does not affect any of the seven OVNT truths; affects only the unfilled-seat marker for this one constraint |

**Previously recorded (second pass), independently confirmed pre-existing:**

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `frontend/src/pages/ScheduleResults.tsx` (Agent column `th`/`td`, multiple sites) | `position: 'sticky', left: 0` | Sticky column never actually pins: resolves against the document (which scrolls), not the table wrapper (`overflowX: auto`, which does not) | ℹ️ Info (WINDOWS.md #15, attribution independently re-verified via `git diff` filtered for `position:`/`sticky`/`overflowX` over the phase range — no matches) | Pre-existing; not introduced by Phase 21 |

### Code Review Finding CR-01 (Blocker, disposition: fixed)

Unchanged — `FteUploadService.java:183` reads `timeslotLookup.computeIfAbsent(ts.getBusinessDate(),
...)`, confirmed fixed, unaffected by this pass. Disposition unchanged: `open: 4 / total: 5` (WR-01,
WR-02, WR-03, IN-01 remain open by operator decision; CR-01 fixed).

### Human Verification Required

None. The one item open after the fourth pass (WINDOWS.md #17) has been measured for 5 of its 6
sub-checks; the 6th is reclassified as a defect finding (WINDOWS.md #14) rather than an open
human-verification item, because its production path is inert by construction and no human action
or measurement would discharge it as currently implemented. The complete Step 8 re-harvest across
all twelve plans surfaces no further block.

### Stale Verification Digest

**This phase's own digest:** refreshed this pass using `gsd_run query verification.fingerprint` over
the current contents of all 54 `covered_files` (unchanged list from the fourth pass). New digest:
`v2:sha256:66ec6e9d07263a997692cc0efca0ad55d36dab5f59df97602ee682e890569ec3`. This re-covers
`.planning/WINDOWS.md` (entry #17 moved to `partial`, #14's significance revised in place) and
`21-UAT.md` (item 5 and its `unreachable_sub_check` evidence added), both of which changed again
since the prior digest was computed.

**Not this phase's to fix, carried forward as before:** plan 21-11 edited `.planning/REQUIREMENTS.md`,
`.planning/ROADMAP.md`, `src/test/resources/bday-join-guard.md`, and
`src/main/java/com/wfm/service/ScheduleOutputService.java`, all of which appear in Phase 18, 19,
and/or 20's own `covered_files`. Those phases' `covered_digest` values remain stale relative to
current file contents. This is a milestone-level bookkeeping item, not a Phase 21 blocker.

### Gaps Summary

No must-have truth failed, no required artifact is missing or a stub, no key link is unwired, and
all seven OVNT requirements remain independently confirmed true in the current codebase. The
complete, re-run Step 8 harvest across all twelve plans produces zero open human-verification items.

Phase 21 ships with two items open by explicit, documented decision, neither of which is this
phase's own defect to fix and neither of which affects any of the seven OVNT truths:

1. **WINDOWS.md #15** (pre-existing, not Phase 21's doing, git-verified) — the Allocation grid's
   Agent column declares `position: sticky` but never actually pins, because the table wrapper's
   `overflowX: auto` is not the scrolling ancestor the document is. Phase 21's wider anchored grid
   increases exposure to it but did not introduce it.
2. **WINDOWS.md #14** (pre-existing, not Phase 21's doing, independently re-traced this pass) — the
   "Unassigned assignment" constraint's `ConstraintMatch` never indicts an individual
   `AgentAssignment`, so its violations carry null business-date/time attribution and the
   operator-facing unfilled-seat marker cannot render for this specific constraint in either grid
   render branch. This phase's own structured-attribution channel (21-03/21-10) is correct and
   populates fully for every OTHER constraint whose match does indict an `AgentAssignment`; fixing
   this one would require restructuring `ScheduleConstraintProvider`'s stream shape for this
   constraint, out of this phase's scope.

One item is waived by explicit operator decision: the Excel Roster-sheet legend-collision backstop
(WINDOWS.md #16) — structurally unmeasurable in this environment, cosmetic only, residual risk
accepted.

Four further findings remain open by prior explicit operator decision and are unaffected by this
pass: WR-01, WR-02, WR-03, IN-01 (code review, disposition `open: 4 / total: 5`).

**Recommendation:** Phase 21 is complete against its own stated goal and all seven OVNT requirements.
`REQUIREMENTS.md`'s OVNT-06/OVNT-07 checkboxes and phase-tracker rows should be flipped to `[x]`/
"Complete" now that this phase has reached `passed` (not done by this verifier, to avoid mutating a
`covered_files` entry after the digest above was computed). WINDOWS.md #14 and #15 should carry
forward as open, cross-phase-visible defects rather than be treated as blockers of this phase's own
ship — they are pre-existing, independently verified via git history not to be Phase 21's
introduction, and neither touches any of the seven OVNT truths.

---

_Verified: 2026-10-03T15:48:58Z_
_Verifier: Claude (gsd-verifier)_
