---
phase: 21-overnight-shift-templates
verified: 2026-10-03T15:09:51Z
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
covered_digest: "v2:sha256:cb19e26a3f3f277a7561287845623b4d4ae96ac9ad87d80c71854e8c000b04cd"
re_verification:
  previous_status: human_needed
  previous_score: "7/7"
  gaps_closed:
    - "WINDOWS.md #12 — measured live: 26-column grid (Agent + Hours + 24 anchored slot columns) on a 21:00-anchored desk; anchored ordering confirmed (21:00→20:00, not clock order); overnight run occupies header indices 3-9 with no gap (contiguous); sticky Agent column's declaration/width (232px) was undisturbed by the anchored column re-ordering itself, which is what this item asked"
    - "WINDOWS.md #13 — measured live: heading '(business day: Mon 21:00–Tue 21:00)' renders with scrollW==clientW, scrollH==clientH, overflow:visible at 375px; does not force page-level scroll attributable to itself because it sits inside the grid's own horizontal-scroll container and tracks the 801px table width rather than the 375px viewport — the backstop's underlying concern (clipping / unwanted page overflow) is answered even though the originally-hypothesized wrap never had to occur"
    - "WINDOWS.md #11 (3 of 4 sub-cases) — non-15-minute refusal (two phrasings), D-05 tiling advisory, and stranded-template refusal all measured at 375px with a 52-char desk name: whiteSpace normal, overflow visible, scrollW==clientW and scrollH==clientH on all four — no clipping, row stays in edit mode, tiling advisory arrives alone as required"
  gaps_remaining:
    - "WINDOWS.md #11 (narrowed) — the accepted-schedule lock explanation (`dayStartLockExplanation`, rendered via `desk.dayStartLockedByScheduleId`) was not reachable in the seeded throwaway environment (no ACCEPTED schedule existed on any desk) and its rendered wrap/clip geometry remains unmeasured. See reasoning below — not accepted as discharged by inference from the other three measurements."
  regressions: []
human_verification:
  - test: "Create or seed an ACCEPTED schedule on an anchored desk, then open Desk Management and view that desk's Day Start cell (both in display mode and in edit mode) at a ~375px viewport with a long desk name (reuse 'Night Desk Verification With A Deliberately Long Name' or similar)."
    expected: "The lock explanation text (naming the blocking schedule and its period) wraps onto additional lines inside the table cell rather than clipping or forcing the row/table to overflow — matching the wrap behavior already measured for the other three message types in this same desk table."
    why_human: "DOM wrapping/overflow is a rendered-DOM fact, not inferable from the component's plain unstyled `<div>` markup alone. WINDOWS.md #11's three other sub-cases were measured live and closed on 2026-10-03, but this fourth sub-case could not be exercised because the seeded environment had no ACCEPTED schedule, so `dayStartLockedByScheduleId` was never truthy. It renders through the identical unstyled `<div>` inside the identical `<td>` already proven not to clip for the other three messages, which makes a pass likely but not measured — the gap is narrower than before, not closed."
---

# Phase 21: Overnight Shift Templates Verification Report

**Phase Goal:** A desk can define a shift that spans midnight, and every surface that touches it —
save-time validation, contracted-hours consumption, day-off blocking, the schedule UI grid, the
Excel export — treats it correctly as one continuous thing belonging to the business day it starts
on.
**Verified:** 2026-10-03T15:09:51Z
**Status:** human_needed
**Re-verification:** Yes — after human (browser-measured) UAT of the three DOM-geometry backstops left open by the initial verification

## What changed since the initial verification

The initial verification (2026-10-03T13:10:00Z) found all 7 OVNT truths ✓ VERIFIED in code and
tests, and was blocked from `passed` status only by three DOM-geometry backstop checks
(WINDOWS.md #11, #12, #13) that no executor session had a browser tool to run. Those three have
now been measured against a real browser by the orchestrator session, on a throwaway stack
(pgvector :55432, backend :8081, vite :3001), with geometry read via `browser_evaluate`. Results
are recorded in `21-UAT.md` (self-reported `status: passed`, 3/3 items, 0 issues).

This re-verification does not take that UAT record's verdict on faith — it re-derives the
underlying facts and reaches its own conclusion, which differs from the UAT file's own headline in
one respect (see Item 1 below).

### Item-by-item re-assessment

**Item 2 (WINDOWS.md #12 — sticky Agent column under anchored re-ordering): CLOSED.** The UAT's own
evidence is internally consistent and answers exactly what the backstop asked — whether the
*anchored column re-ordering* disturbs the sticky column's offset. It measured the column's
declaration and width as stable across three scroll positions. Separately, the UAT found that the
sticky column was already non-functional for an unrelated, pre-existing reason (the scrolling
ancestor is the document, not the table wrapper, so `position: sticky` is inert regardless of
column order). I independently re-ran the attribution check rather than trusting it:

```
git diff 7fbacf1^..HEAD -- frontend/src/pages/ScheduleResults.tsx | grep -n -E "^[+-].*(position:|sticky|overflowX)"
```
(`7fbacf1^` = the commit immediately before Phase 21's first commit.) This returns **no matching
lines** — the diff touches the heading text and unrelated table rows in that region, never the
`position`/`sticky`/`overflowX` declarations. Confirmed independently: this defect predates Phase
21 and was correctly filed as a new, separate ledger item (WINDOWS.md #15) rather than folded into
this phase's verdict. It does not negate OVNT-06 — the contiguous-run claim was verified by a
direct index-adjacency assertion (header indices 3-9, each equal to predecessor+1), which is
independent of whether the column visually pins during scroll.

**Item 3 (WINDOWS.md #13 — section heading wrap at narrow viewport): CLOSED, reasoning accepted as
sound, not a rationalization.** The original backstop's literal expectation ("wraps onto a second
line") was premised on the heading living in page-level flow at 375px. The measurement instead
shows the heading sits inside the grid's own `overflowX` scroll container and its box tracks the
801px table width — so page-level scroll and clipping, the actual concern a wrapping check exists
to catch, provably do not occur (`scrollW==clientW`, `scrollH==clientH`, `overflow: visible`). This
is a valid re-characterization, not special pleading: the backstop existed to catch unwanted
overflow/clipping, and the measurement directly answers that question in the negative, even though
the specific mechanism anticipated (wrapping) is not the one at work. Accepted as closed.

**Item 1 (WINDOWS.md #11 — Desk Management message rendering at narrow viewport): NOT fully
closed — narrowed.** Three of the four sub-cases named in the original backstop (non-15-minute
refusal, D-05 tiling advisory, stranded-template refusal) were measured live and are unambiguously
clean (no clipping on either axis, correct edit-mode retention, tiling advisory alone). The fourth
— the **accepted-schedule lock explanation** — is explicitly named in the original backstop's test
text and was *not* exercised, because the seeded throwaway environment had no ACCEPTED schedule on
any desk, so the `desk.dayStartLockedByScheduleId` branch never rendered. The UAT file itself is
honest about this (`not_covered`), and I take that at face value rather than rounding "3 of 4
measured" up to "the item is discharged."

I considered whether this is safe to infer from the other three measurements (same table, same
desk name, same unstyled `<div>` markup, no `maxWidth`/`overflow`/`whiteSpace` override distinct
from the other cells) — read the component directly at
`frontend/src/pages/DeskManagement.tsx:154-194` to check this rather than assume it. The structural
similarity is real and makes a pass likely. But likely is not measured, and the whole reason this
class of check exists is that DOM geometry is not safely inferable from markup — that is the
verifier's own stated justification for routing the other three to human verification in the first
place. Applying a weaker evidentiary bar to the fourth sub-case than the other three were held to
would be inconsistent. **Decision: WINDOWS.md #11 is narrowed from "4 Desk Management message
types unmeasured" to "1 Desk Management message type unmeasured (accepted-schedule lock
explanation)," and the narrower item remains open as a human-verification item.** It is not treated
as a new gap (nothing regressed or failed) and it is not treated as closed (something real remains
unmeasured).

### Net effect on status

Per the decision tree, any non-empty human-verification list routes status to `human_needed`
(unless a higher-precedence `gaps_found` condition also fires, which it does not here — no truth
failed, no artifact is missing/stub, no key link is unwired, and the one new UI finding from UAT
measurement, WINDOWS.md #15, was independently confirmed pre-existing and not attributable to this
phase). **Status stays `human_needed`**, not `passed` — blocked by exactly one narrow,
well-scoped item, not by any of the three original items in full.

## Goal Achievement

### Observable Truths

Unchanged from the initial verification — all 7 are code-and-test-verified facts that UAT
measurement does not touch. Reproduced here with incidental live corroboration from the UAT run
added where it strengthens (not replaces) the original evidence.

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | An operator can save a shift template whose end time is earlier in the clock than its start time, with correct net hours for the overnight span, on an anchored desk (OVNT-01) | ✓ VERIFIED | `ShiftTemplateService.java:51` `MAX_SPAN_MINUTES=16*60`; `validate()` refuses a backward interval naming the desk's own day start; `window.anchoredDurationMinutes(...)` computes net hours. `ShiftTemplateServiceTest` (53 tests, 0 failures). **Corroborated live (UAT):** a real 22:00-06:00 template SAVED successfully, end-to-end, through the real HTTP API on a 21:00-anchored desk. |
| 2 | That shift is reported against the business day it starts on, everywhere it is displayed (OVNT-02) | ✓ VERIFIED | `ScheduleOutputService.buildAgentSchedule` groups by `a.getTimeslot().getBusinessDate()`; `ScheduleSummary.dayStart`/`ScheduleDetailResponse` carry the schedule's own anchor; structured `ViolationDetail` fields. **Corroborated live (UAT):** the summary payload carried `"dayStart":"21:00:00"`; the Schedule Results date filter offered exactly ONE date for a run spanning two calendar days. |
| 3 | A day-off or PTO marking on the starting business day blocks the shift from being assigned, including seats stamped with the following calendar date (OVNT-03) | ✓ VERIFIED | `ScheduleConstraintProvider.java:183`: `agentDayOff` join is `equal(a -> a.getTimeslot().getBusinessDate(), AgentDayOff::getDate)`. `MidnightBoundaryRegressionTest$DayOffAttributesToStartingBusinessDate` green. |
| 4 | The shift consumes the contracted hours of the weekday it starts on only, never split across two weekday rows (OVNT-04) | ✓ VERIFIED | `MidnightBoundaryPropertyTest.ContractedHoursStartingWeekdayOnly` proves single business-date derivation for both calendar days of a crossing stretch. |
| 5 | Shift-library validation refuses an overnight template whose envelope does not fit inside its desk's business day (OVNT-05) | ✓ VERIFIED | `isWithinOperatingWindow`/`crossesCalendarMidnight` wired into save path, SHIFT-mode gate, and candidate generation — one predicate, three callers. |
| 6 (amended) | The business-day-keyed schedule UI grid/roster render an overnight shift as one block carrying calendar-span disclosure; per-date slot surfaces render it as one continuous run of cells (OVNT-06) | ✓ VERIFIED | Amendment premise independently re-derived and confirmed true (see original "Amendment Re-Verification" below). `ScheduleExportService` sorts by `anchoredStartMinute`/`anchoredShiftSortKey`; `ScheduleResults.tsx` binds one `dayWindow` throughout. **Corroborated live (UAT):** 24+ column allocation grid on a 21:00-anchored desk renders anchored order (21:00→20:00, not clock order); the night agent's 7 overnight cells occupy header indices 3-9 with no gap — a direct index-adjacency measurement, not an inference. |
| 7 | The shift is labelled with the calendar dates it spans wherever displayed (OVNT-07) | ✓ VERIFIED | `ScheduleExportService.shiftCode`/`crossingAwareCode` on Roster sheet; `ScheduleOutputService.timeslotLabel` appends `(business day: {date})`; `ScheduleResults.tsx`'s `sectionHeading` discloses `(business day: {span})`. **Corroborated live (UAT):** heading rendered live as `"2026-01-05 (business day: Mon 21:00–Tue 21:00)"`, en dash intact through JSON transit. |

**Score:** 7/7 truths verified (0 present, behavior-unverified)

### Amendment Re-Verification (ROADMAP criterion 4 / REQUIREMENTS OVNT-06)

Unchanged from initial verification. Plan 21-11's amendment premise — that the agent
schedule/Roster sheet is keyed on business date and therefore an overnight shift already occupies
exactly one cell there, making a "continuation indicator" requirement structurally impossible to
need — was independently re-derived from `ScheduleOutputService.buildAgentSchedule` (lines 164-170)
and `ScheduleExportService.writeRoster` (line 245) in the initial verification pass, and holds. Not
re-litigated here; this re-verification's new evidence (live index-adjacency measurement of the
*per-date slot* surface, where the real fragmentation risk lived) reinforces rather than touches
this reasoning.

### Required Artifacts

Unchanged from initial verification — no implementation files were modified between the initial
verification and this re-verification (confirmed: `git diff --stat HEAD -- src/ frontend/src/`
returns empty; only `.planning/WINDOWS.md` and `.planning/phases/21-overnight-shift-templates/21-UAT.md`
changed, both already committed in 77de67e and 585d6bf).

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/service/ShiftTemplateService.java` | Overnight save, D-02/D-08 refusals, OVNT-05 containment | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/service/DeskService.java` | D-03/D-04/D-05 day-start refusals/advisory/disclosure | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/service/FteUploadService.java` | CR-01 fix: lookup keyed by business date | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/service/ScheduleExportService.java` | Anchored column/row order, overnight cell disclosure | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/service/ScheduleOutputService.java` | Structured violation fields, shared `timeslotLabel` helper | ✓ VERIFIED | Unchanged |
| `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` | Anchored contiguity/break scans, business-date day-off join | ✓ VERIFIED | Unchanged |
| `frontend/src/pages/DeskManagement.tsx` | Editable day-start control, lock disclosure, server-message surfacing | ✓ VERIFIED | Unchanged; UAT confirms 3 of 4 message types render without clipping live |
| `frontend/src/pages/ScheduleResults.tsx` | Anchored grid (7 sites), business-day heading disclosure | ✓ VERIFIED | Unchanged; UAT confirms anchored ordering and contiguity live, plus the pre-existing (unrelated) sticky-column defect |
| `frontend/src/utils/dayWindow.ts` | Branded `DayOffset` arithmetic module | ✓ VERIFIED | Unchanged |
| `src/test/resources/midnight-time-arithmetic.md` | Raw-comparison allowlist resolved to exactly 2 entries | ✓ VERIFIED | Unchanged |

### Key Link Verification

Unchanged from initial verification (no implementation files modified since). See original table;
all links remain ✓ WIRED.

### Behavioral Spot-Checks

Unchanged from initial verification — the targeted JUnit sample run previously (53+32+15+4+12/12+5/5
tests, plus full-suite confirmation via the supplied test_state: 202 classes, 1303 tests, 0
failures/errors, 4 pre-existing benchmark skips) was not re-run here because no implementation file
changed between the initial verification and this one. The regression gate against Phases 18/19/20
guard classes (DayWindowTest, MidnightBoundaryRegressionTest, DayWindowAnchorBindingTest,
TimeslotGeneratorBusinessDateTest, MidnightBoundaryPropertyTest) remains green per the supplied
test_state.

### Browser-Measured Backstops (new this run)

| # | Backstop | Ledger | Result | Status |
|---|----------|--------|--------|--------|
| 1 | Desk Management refusal/advisory rendering at 375px, long desk name | WINDOWS.md #11 | 3 of 4 message types measured clean (no clipping, correct edit-mode retention); accepted-schedule lock explanation not reachable (no ACCEPTED schedule in seeded env) | ⚠️ PARTIAL — narrowed, not closed |
| 2 | Sticky Agent column survives anchored column re-ordering | WINDOWS.md #12 | Declaration/width stable across scroll positions; contiguous 7-cell overnight run confirmed by index adjacency; pre-existing unrelated sticky-pinning defect found and separately filed (#15), independently confirmed not Phase-21-caused | ✓ CLOSED |
| 3 | Business-day heading does not force page scroll at 375px | WINDOWS.md #13 | No clipping/overflow attributable to the heading; it tracks the grid's own 801px scroll container rather than the 375px viewport, which answers the backstop's underlying concern even though literal "wrapping" was not the mechanism | ✓ CLOSED |

### Requirements Coverage

Unchanged from initial verification. All seven OVNT-01..07 requirements remain ✓ SATISFIED on code
and test evidence, independent of the UAT backstops. OVNT-06's REQUIREMENTS.md checkbox remains
`[ ]`/"Pending" — correctly so, since this phase has not reached `passed` status and marking it
complete is this verification's own downstream consequence.

### Anti-Patterns Found

Unchanged four pre-recorded, operator-accepted findings (WR-01, WR-02, WR-03, IN-01) — see initial
verification table below, re-confirmed present and unaffected by this re-verification since no
implementation files changed.

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `frontend/src/pages/ScheduleResults.tsx:695-698` | `isEnvelopeReached` | Unguarded `RangeError`; no `ErrorBoundary` anywhere | ⚠️ Warning (pre-recorded, WR-03, disposition: open) | Unaffected |
| `src/main/java/com/wfm/service/ShiftTemplateService.java:412-424` | `isAligned` | END-position reinterpretation edge case (WR-01, disposition: open) | ⚠️ Warning (pre-recorded) | Unaffected |
| `src/main/java/com/wfm/service/DeskService.java:298-321` | `setDayStart`'s stranding check | Does not exclude retired template eras (WR-02, disposition: open) | ⚠️ Warning (pre-recorded) | Unaffected |
| `frontend/src/pages/DeskManagement.tsx:109,172-175` | Cancel button | Stays enabled during in-flight save (IN-01, disposition: open) | ℹ️ Info (pre-recorded) | Unaffected; UAT reproduced this live empirically (Save disabled, Cancel not) |

**New, UAT-discovered, independently attributed as pre-existing (not a Phase 21 regression):**

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `frontend/src/pages/ScheduleResults.tsx` (Agent column `th`/`td`, multiple sites) | `position: 'sticky', left: 0` | Sticky column never actually pins: resolves against the document (which scrolls), not the table wrapper (`overflowX: auto`, which does not); measured live at left -366px/right -134px at full document scroll | ℹ️ Info (new ledger item WINDOWS.md #15, filed by orchestrator, attribution independently re-verified by this verifier via `git diff 7fbacf1^..HEAD -- frontend/src/pages/ScheduleResults.tsx` filtered for `position:`/`sticky`/`overflowX` — returns no matching added/removed lines) | Pre-existing; not introduced by Phase 21. Phase 21's 24-column anchored layout increases exposure (horizontal scroll is more likely to be needed now) but did not create the defect. Does not negate OVNT-06 — contiguity was proven by index adjacency, independent of visual pinning. |

### Code Review Finding CR-01 (Blocker, disposition: fixed)

Unchanged — independently re-verified in the initial pass; `FteUploadService.java:183` reads
`timeslotLookup.computeIfAbsent(ts.getBusinessDate(), ...)`, confirmed fixed, unaffected by this
re-verification.

### Human Verification Required

One item remains, narrowed from the original three:

1. **Accepted-schedule lock explanation geometry** (WINDOWS.md #11, narrowed) — see frontmatter
   `human_verification` block above for the full test/expected/why_human. Needs an ACCEPTED
   schedule in the test environment to exercise `desk.dayStartLockedByScheduleId`, which the
   throwaway stack used for this UAT round did not have.

### Stale Verification Digest

**This phase's own digest:** refreshed deliberately as part of this re-verification run, using
`gsd-tools query verification.fingerprint` over the current contents of all 55 `covered_files`
(the same list as the initial verification, plus `21-UAT.md`, which is now phase evidence this
re-verification relies on). New digest:
`v2:sha256:cb19e26a3f3f277a7561287845623b4d4ae96ac9ad87d80c71854e8c000b04cd`. This specifically
re-covers `.planning/WINDOWS.md`, which changed (ledger entry #15 added) since the prior digest was
computed — the prior digest was stale with respect to the verifier's own tracked file; it no
longer is.

**Not this phase's to fix, carried forward as before:** plan 21-11 edited `.planning/REQUIREMENTS.md`,
`.planning/ROADMAP.md`, `src/test/resources/bday-join-guard.md`, and
`src/main/java/com/wfm/service/ScheduleOutputService.java`, all of which appear in Phase 18, 19,
and/or 20's own `covered_files`. Those phases' `covered_digest` values remain stale relative to
current file contents. This is a milestone-level bookkeeping item, not a Phase 21 blocker, and
needs a deliberate fingerprint refresh on those phases before milestone close.

### Gaps Summary

No must-have truth failed, no required artifact is missing or a stub, no key link is unwired, and
all seven OVNT requirements remain independently confirmed true in the current codebase.

Of the three DOM-geometry backstops that previously held this phase at `human_needed`, two
(WINDOWS.md #12 and #13) are now genuinely closed by live browser measurement, re-derived and
accepted on their own evidence rather than taken from the UAT file's self-reported verdict. The
third (WINDOWS.md #11) is three-quarters closed by the same measurement standard but is **not**
fully discharged: its fourth named sub-case (the accepted-schedule lock explanation) was not
reachable in the seeded environment and remains an open, narrowly-scoped human-verification item.
Rounding "3 of 4 measured" up to "the item is closed" would apply a materially weaker evidentiary
bar to this sub-case than the other three were held to, for no reason other than convenience — so
this verification declines to do that.

A new, pre-existing UI defect (sticky Agent column does not actually pin) was found while measuring
item 2. Its non-attribution to Phase 21 was independently re-checked via `git diff` against the
commit immediately preceding Phase 21's first commit, not merely accepted from the UAT file's own
claim, and the check confirms it: Phase 21 never touched the relevant `position`/`sticky`/`overflowX`
lines. It is correctly filed as a new ledger item (#15) rather than folded into this phase's verdict,
and does not block Phase 21.

**Recommendation:** seed an ACCEPTED schedule on an anchored desk in a throwaway environment (the
same stack already stood up for this UAT round would work) and measure the lock explanation's wrap
behavior directly — a single additional check, not a new backstop. Alternatively, accept a
documented waiver on the inferential grounds laid out above (identical unstyled `<div>`/`<td>`
pattern to the three already-measured message types) if the team judges that inference sufficient
to ship without the fourth measurement. Either resolution closes WINDOWS.md #11 and clears the last
blocker to `passed`.

---

_Verified: 2026-10-03T15:09:51Z_
_Verifier: Claude (gsd-verifier)_
