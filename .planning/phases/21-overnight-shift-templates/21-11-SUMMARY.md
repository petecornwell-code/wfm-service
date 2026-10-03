---
phase: 21-overnight-shift-templates
plan: 11
subsystem: api
tags: [java, timeslot-label, business-date, excel-export, schedule-grid, planning-docs]

# Dependency graph
requires:
  - phase: 21-overnight-shift-templates (plan 21-03)
    provides: "ViolationDetail's structured businessDate/calendarDate/startTime/endTime fields, and the backend export's parser moved onto them -- this plan's precondition"
  - phase: 21-overnight-shift-templates (plan 21-10)
    provides: "ScheduleResults.tsx's unfilled-seat parser moved onto the structured fields -- the other half of this plan's precondition"
  - phase: 21-overnight-shift-templates (plan 21-04)
    provides: "bday-join-guard.md's Erlang-calculator bullet already rewritten to RECORD its migration -- this plan's edit to the neighbouring labelling bullet had to preserve that, not undo it"
  - phase: 21-overnight-shift-templates (plan 21-07)
    provides: "The Roster cell's calendar-span disclosure convention and the real per-date-slot-grid fragmentation fix -- the evidence base for this plan's ROADMAP/REQUIREMENTS amendment"
provides:
  - "ScheduleOutputService.timeslotLabel(Timeslot) -- one shared private static helper building the operator-facing timeslot label for both violation-reporting paths, discloses the business date as a labelled suffix when it differs from the calendar date, byte-identical to today otherwise"
  - "bday-join-guard.md's labelling bullet rewritten to record the change landed, not defer it"
  - "ROADMAP.md's Phase 21 fourth success criterion and REQUIREMENTS.md's OVNT-06 text amended to describe the business-day-keyed roster's calendar-span disclosure and locate the never-two-fragments property on the per-date slot grids, with a Notes sentence recording why"
affects: []

# Actuals (#2632)
actuals:
  tokens: 5816
  tasks: 2
  commits: 2
  plan_head_before: bede6e7b515d8e1975e049bf1b823a95c47ef1cd
  plan_head_after: 05febd602550bc5921ff26d2d9e93b24db4110f4

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A label built in exactly one shared private static helper, called from both a live-path explain() loop and an accepted-path persisted-snapshot walk, so the two reporting paths cannot silently drift into producing different text for the same timeslot -- verified directly by a test comparing both paths' output for identical coordinates"
    - "A displayed string with two former parsers is only safe to change once both are confirmed, independently, to no longer recover data from it by splitting the string -- confirmed here by grep against both the backend export and the frontend grid before any text changed, matching this plan's own precondition and two independent verify gates"

key-files:
  modified:
    - src/main/java/com/wfm/service/ScheduleOutputService.java
    - src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java
    - src/test/resources/bday-join-guard.md
    - .planning/ROADMAP.md
    - .planning/REQUIREMENTS.md

key-decisions:
  - "The label's disclosure format, when business date and calendar date differ, is `{calendarDate} {start}-{end} (business day: {businessDate})` -- the calendar date stays leading (unchanged, what finds the row on a calendar), and the business date is appended as an explicit parenthetical labelled with the term \"business day\" already established project-wide (matching 21-10's sectionHeading convention, e.g. \"2026-09-27 (business day: Sun 21:00-Mon 21:00)\"). 21-07's Roster-cell convention (`Sun 22:00-Mon 06:00`, weekday abbreviations on two span ENDPOINTS of the same scale) does not map cleanly onto this label's divergence, which is between two INTERPRETATIONS of one instant (calendar date vs. business-day attribution), not two different instants -- so the closest-fit reuse was the labelled-parenthetical phrasing, not the literal weekday-abbreviation string shape. Documented here per the plan's explicit instruction to follow an established convention rather than invent one, since the two conventions in this phase address genuinely different disclosure shapes."
  - "Task 1's Test 3 (\"the unassigned-seat description embeds the same label string\") could not be proven via the literal live-path route the plan's <behavior> section assumed. Empirically confirmed (two separate probes against the real Timefold solver via solutionManager.explain(), one with a null-agent seat, one with a mixed assigned/unassigned pair at the same timeslot): the \"Unassigned assignment\" constraint (a groupBy/join/join/filter/penalizeConfigurable aggregate in ScheduleConstraintProvider) never indicts an individual AgentAssignment in its ConstraintMatch -- the justification tuple is (Timeslot key, summed int, TimeslotDemandConfig, ScheduleConfig), none of which is an AgentAssignment. specName and timeslotLabel are therefore always null wherever this constraint fires, so the `\"No agent assigned for \" + specName + \" at \" + timeslotLabel` description branch is pre-existing dead code in the live path, predating this plan. Fixing it would mean restructuring that constraint's stream shape in ScheduleConstraintProvider -- a solver file outside this plan's files_modified and threat model, and Rule 4 (architectural) territory, not Rule 1. Documented as a deviation (WINDOWS.md #14) rather than fixed. Test 3 is instead proven at the source level (a structural test confirming the description expression is literally built from the shared timeslotLabel variable) plus a genuine live-path proof using a constraint that DOES indict AgentAssignment (\"Specialization match\") for Test 4's both-paths-agree comparison."
  - "Test 4 (\"both violation paths produce an identical label for the same timeslot\") uses the LIVE path's \"Specialization match\" constraint (forEach(AgentAssignment.class) directly, confirmed to indict the AgentAssignment and populate the label correctly) rather than \"Unassigned assignment\", for the reason above -- this is a real solver-driven proof, not a substitute unit test."

requirements-completed: []

coverage:
  - id: D1
    description: "ScheduleOutputService builds the operator-facing timeslot label in exactly one shared helper, called from both buildConstraintViolations's live-path loop and buildAcceptedConstraintViolations"
    requirement: "OVNT-07"
    verification:
      - kind: unit
        ref: "grep -cF 'ts.getDate() + \" \" + ts.getStartTime() + \"-\" + ts.getEndTime()' ScheduleOutputService.java (prints 0 -- the plan's own survivor gate)"
        status: pass
      - kind: unit
        ref: "ScheduleOutputServiceShiftReportingTest#buildConstraintViolations_liveAndAcceptedPaths_produceIdenticalLabelsForTheSameTimeslot"
        status: pass
    human_judgment: false
  - id: D2
    description: "A label whose business date and calendar date differ discloses both; one whose dates match is byte-identical to today"
    requirement: "OVNT-07"
    verification:
      - kind: unit
        ref: "ScheduleOutputServiceShiftReportingTest#timeslotLabel_differingBusinessAndCalendarDates_disclosesBothAsALabelledSuffix"
        status: pass
      - kind: unit
        ref: "ScheduleOutputServiceShiftReportingTest#timeslotLabel_equalBusinessAndCalendarDates_isByteIdenticalToToday"
        status: pass
      - kind: unit
        ref: "ScheduleOutputServiceShiftReportingTest#buildConstraintViolations_anchoredDesk_labelNowDisclosesTheBusinessDateWhenDatesDiffer_staysPlainWhenTheyAgree (the inverted 21-03 control)"
        status: pass
      - kind: unit
        ref: "ScheduleOutputServiceShiftReportingTest#buildConstraintViolations_liveViolation_carriesTheDisclosingLabel"
        status: pass
    human_judgment: false
  - id: D3
    description: "Neither former parser (ScheduleExportService's unfilledSeatsByDateAndSlot, ScheduleResults.tsx's unfilled-seat map) reads the label as data -- confirmed before the text changed, not merely assumed"
    requirement: "D-14"
    verification:
      - kind: other
        ref: "sed-scoped grep for indexOf(' ') inside unfilledSeatsByDateAndSlot's method body (prints 0)"
        status: pass
      - kind: other
        ref: "grep -c 'v.timeslotLabel.substring(' frontend/src/pages/ScheduleResults.tsx (prints 0)"
        status: pass
    human_judgment: false
  - id: D4
    description: "The unassigned-seat description, where it is reachable, is built from the same timeslotLabel variable this plan's helper populates"
    requirement: "OVNT-07"
    verification:
      - kind: unit
        ref: "ScheduleOutputServiceShiftReportingTest#unassignedSeatDescription_isBuiltFromTheSameTimeslotLabelVariable"
        status: pass
    human_judgment: true
    rationale: "Proven at the source level because the live-path branch this test targets is pre-existing dead code (see key-decisions) -- the property it CAN prove (same variable, so can never disagree) is proven; the property the plan originally imagined (observing it fire through a real solve) is structurally unreachable and documented as a deviation rather than silently dropped."
  - id: D5
    description: "The ROADMAP's fourth Phase 21 success criterion and REQUIREMENTS.md's OVNT-06 text describe the business-day-keyed roster's calendar-span disclosure and locate the never-two-fragments property on the per-date slot grids, with every other entry in both documents left untouched"
    requirement: "OVNT-06"
    verification:
      - kind: other
        ref: "All 8 of the plan's own grep-based structural gates re-run after the edit: morning-after count 0, 5 numbered criteria, 14 phase headings, Goal/Depends-on/Requirements/Notes lines present, 'not two fragments' count 0, OVNT-07 text count 1, OVNT-06 text count 1, OVNT-06 traceability row unchanged (all pass; see Task Commits below)"
        status: pass
    human_judgment: false

# Metrics
duration: ~55min
completed: 2026-10-03
status: complete
---

# Phase 21 Plan 11: Overnight Label Disclosure and the Void Continuation-Indicator Amendment Summary

**The operator-facing timeslot label now discloses the business date as a `(business day: ...)` suffix whenever it diverges from the calendar date, built by one shared helper now that both former parsers read structured fields instead — and the ROADMAP/REQUIREMENTS documents are amended to stop asking for a continuation indicator a business-day-keyed Roster sheet structurally cannot produce.**

## Performance

- **Duration:** ~55 min (including a ~10.5 min full unfiltered `./gradlew test` run)
- **Tasks:** 2 of 2
- **Files modified:** 5 (3 production/test, 2 planning documents)

## Accomplishments

- Extracted the two inline `ts.getDate() + " " + ts.getStartTime() + "-" + ts.getEndTime()` concatenations in `ScheduleOutputService` into one shared private static `timeslotLabel(Timeslot)` helper, called from both `buildConstraintViolations`'s live-path loop and `buildAcceptedConstraintViolations`.
- Before changing any text, independently confirmed (not trusted from the plan's premise) that neither former parser recovers data from the label by splitting the string: `ScheduleExportService.unfilledSeatsByDateAndSlot` reads `v.businessDate()`/`v.startTime()` directly (zero `indexOf(' ')` occurrences in its scoped body) and `ScheduleResults.tsx`'s unfilled-seat map carries zero `v.timeslotLabel.substring(` occurrences — both confirmed via the plan's own grep gates before any production edit.
- The label is now byte-identical to today when business date equals calendar date (every midnight-anchored desk), and discloses the business date as an explicit `(business day: {date})` suffix when they differ — the calendar date stays in its leading position, unchanged, since that's what an operator needs to find the row on a calendar.
- Rewrote both explanatory comments at the two construction sites: the "all labelling change belongs to OVNT-07" deferral is replaced with a statement of what is now true, naming the structured fields (21-03) and the frontend parser (21-10) that replaced the label as a data channel.
- Updated `bday-join-guard.md`'s inherited bullet about these two call sites to record that the labelling change landed, leaving the neighbouring Erlang-calculator bullet (already rewritten by plan 21-04) untouched.
- Added 5 new tests to `ScheduleOutputServiceShiftReportingTest` and inverted the 21-03 label-unchanged control test (not deleted — rewritten in place) to assert the new disclosing shape for differing dates while keeping its byte-identical assertion for the same-dates case.
- Amended `.planning/ROADMAP.md`'s Phase 21 fourth success criterion and `.planning/REQUIREMENTS.md`'s OVNT-06 text to describe the business-day-keyed roster's calendar-span disclosure and locate the never-two-fragments property on the per-date slot grids (the Excel Allocation sheet, fixed by plan 21-07, and the schedule grid, fixed by plan 21-10) — recording in the ROADMAP's Notes why the original criterion's premise was void and where the real fragmentation was. All other criteria, the Goal/Depends-on/Requirements lines, OVNT-01 through 05, OVNT-07, the traceability table, the "Decisions taken at scoping" table and the "Accepted consequence" paragraph are all untouched — confirmed by 8 independent grep-based structural gates.

## Label text — before and after

**Before (produced identically at both construction sites):**
```
2026-09-21 21:00-22:00
```
(Java's default `LocalDate`/`LocalTime` `toString()`, unchanged since 21-03.)

**After — business date equals calendar date (every midnight-anchored desk, unchanged):**
```
2026-09-21 21:00-22:00
```

**After — business date differs from calendar date (a 21:00-anchored desk's 02:00-03:00 slot, business date one day earlier than the calendar date):**
```
2026-09-08 02:00-03:00 (business day: 2026-09-07)
```

## Amended ROADMAP.md text (Phase 21, criterion 4, in full)

> 4. The business-day-keyed schedule UI grid and roster render an overnight shift as one block
>    carrying its calendar-span disclosure, and the per-date slot surfaces — the Excel allocation
>    sheet and the schedule grid, both ordered from the desk's day start — render it as one
>    continuous run of cells, never two fragments (OVNT-06).

Added to the Phase 21 Notes:

> **Amended in plan 21-11 (D-11):** criterion 4's original text asked for a distinct, non-blank,
> non-duplicate continuation indicator on the cell immediately following an overnight shift's
> starting cell — a premise measured void during this phase. The agent schedule is keyed on
> business date, so an overnight shift already occupies exactly one Roster cell and there is no
> following cell on that sheet to annotate; this requirement predates any desk being able to hold
> an overnight template, so the falsity was undiscoverable until this phase built one. The real
> fragmentation lived on the per-date slot grids — the Excel Allocation sheet (fixed by plan
> 21-07) and the schedule grid (fixed by plan 21-10) — both now ordered from the desk's day start.
> Criterion 4 above restates what is actually true and verifiable rather than what was originally
> assumed.

## Amended REQUIREMENTS.md text (OVNT-06, in full)

> - [ ] **OVNT-06**: The Excel Allocation sheet and the schedule UI grid — the per-date slot
>   surfaces, ordered from the desk's day start — show an overnight shift as one continuous
>   block, never two fragments; the business-day-keyed Roster sheet and schedule grid carry the
>   shift's calendar-span disclosure instead of a continuation indicator, since an overnight
>   shift there already occupies exactly one business-day cell

The traceability table row (`| OVNT-06 | Phase 21 | Pending |`) is untouched, per the dispatch instructions — it carries an id, phase and status with no text to amend, and editing it would change what the milestone audit reads about completion rather than about scope. OVNT-06's checkbox state is left `[ ]` (Pending) — this plan amends the requirement's TEXT, not its completion status; completion is `/gsd-verify-work`'s and the phase verifier's call.

## Task Commits

Each task was committed atomically:

1. **Task 1: Disclose the calendar span in the operator-facing timeslot label** — `edd9bb5` (feat)
2. **Task 2: Amend the roadmap criterion and the requirement text to what is actually true** — `05febd6` (docs)

**Plan metadata:** this SUMMARY commit (docs: complete plan)

## Files Created/Modified

- `src/main/java/com/wfm/service/ScheduleOutputService.java` — new private static `timeslotLabel(Timeslot)` helper; both construction sites call it; both explanatory comments rewritten
- `src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java` — 5 new tests, 1 inverted control test, 2 new private fixture helpers (`singleLiveSpecializationMismatchViolation`; the pre-existing `singleRelocatedViolation` reused unchanged)
- `src/test/resources/bday-join-guard.md` — the `ScheduleOutputService` labelling bullet rewritten to record the change landed; the neighbouring Erlang-calculator bullet (21-04's territory) left untouched
- `.planning/ROADMAP.md` — Phase 21's fourth success criterion rewritten; one sentence added to the Phase 21 Notes
- `.planning/REQUIREMENTS.md` — OVNT-06's requirement text (the one place it appears) rewritten; the traceability table row untouched

## Decisions Made

See `key-decisions` in frontmatter: the label's disclosure format (a labelled parenthetical reusing the project's established "business day" vocabulary, since 21-07's literal weekday-abbreviation convention addresses a different disclosure shape and doesn't map cleanly onto this label's calendar-date-vs-business-date divergence); the pre-existing dead-code finding in the "Unassigned assignment" constraint's live-path justification and how Test 3/Test 4 were adapted to prove what is actually provable.

## Deviations from Plan

### Auto-fixed Issues

None — no bugs were found in code this plan's own changes touch.

### Found and documented, not fixed (Rule 4 — architectural, out of scope)

**1. [Rule 4 - Architectural, out of scope] The "Unassigned assignment" constraint's live-path description branch is pre-existing dead code**
- **Found during:** Task 1, while building Test 3 ("the unassigned-seat description embeds the same label string")
- **Issue:** `ScheduleConstraintProvider.unassignedAssignment` is a `groupBy(Timeslot, sum(...)).join(TimeslotDemandConfig).join(ScheduleConfig).filter(...).penalizeConfigurable()` constraint. Confirmed empirically against the real Timefold solver (two separate probes: a single null-agent seat, and a mixed assigned/unassigned pair at the same timeslot) that this constraint's `ConstraintMatch.getIndictedObjectList()` never contains an `AgentAssignment` instance — the justification tuple is `(Timeslot, Integer, TimeslotDemandConfig, ScheduleConfig)`. `ScheduleOutputService`'s loop only populates `specName`/`timeslotLabel` from an `AgentAssignment` justification, so for THIS constraint they are always `null`, and the `"No agent assigned for " + specName + " at " + timeslotLabel` description branch (gated on `specName != null && timeslotLabel != null`) can never execute in the live `explain()` path. This predates this plan — the label's text change did not introduce it, and 21-03's own test suite never exercised this specific branch either.
- **Why not fixed:** fixing it would mean restructuring `unassignedAssignment`'s constraint-stream shape in `ScheduleConstraintProvider.java` — a solver file outside this plan's `files_modified` and threat model, with real risk to the per-constraint match-count guards (`ConstraintMatchCountNonVacuityTest`, `SolverQualityGuardTest`) this codebase relies on for solver correctness. This is Rule 4 (architectural change), not a Rule 1/2/3 auto-fix.
- **Disposition:** documented here and in `WINDOWS.md` entry #14 (`kind: deviation`, phase 21). Test 3's intent is instead proven two ways: a structural test (`unassignedSeatDescription_isBuiltFromTheSameTimeslotLabelVariable`) confirming the description expression is literally built from the shared `timeslotLabel` variable, so it can never disagree with it wherever it IS reachable; and Test 4's both-paths-agree comparison uses a different, genuinely-firing live constraint ("Specialization match") that DOES indict `AgentAssignment`.
- **Verification:** `ScheduleOutputServiceShiftReportingTest` green (29 tests, up from 24); full suite green.
- **Commit:** `edd9bb5` (Task 1 commit)

---

**Total deviations:** 1 found-and-documented (architectural, out of scope — not auto-fixed under Rules 1-3)
**Impact on plan:** No change to this plan's own deliverable. The dead-code finding is orthogonal to the label-text change and does not block OVNT-07's disclosure requirement, which is about what the label DOES say, not about every code path that theoretically could embed it.

## Issues Encountered

None beyond the deviation above. Both tasks' acceptance criteria and verify commands pass as specified; the precondition (neither former parser splits the label) was confirmed independently before any text changed, per the plan's explicit instruction.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes at trust boundaries were introduced. This plan's threat register (T-21-17, T-21-18, T-21-SC) is unchanged from the plan's own analysis — no package installs occurred (`build.gradle` and `frontend/package.json` untouched, confirmed via `git diff --stat`), and the label's new disclosed field (business date) was already present in the same response as a structured field since plan 21-03. No `## Threat Flags` entries.

## Stale Verification Digest Risk (flagged per dispatch instructions)

Editing `.planning/REQUIREMENTS.md` makes the `covered_digest` fingerprint in **two earlier phases' VERIFICATION.md** stale, since both list it in `covered_files`:

- **`.planning/phases/18-business-day-foundation-guards/18-VERIFICATION.md`** — `covered_files` includes `.planning/REQUIREMENTS.md`. `covered_digest: "v1:sha256:f3aaeab3..."` no longer matches.
- **`.planning/phases/19-daywindow-re-anchoring/19-VERIFICATION.md`** — `covered_files` includes `.planning/REQUIREMENTS.md` **and** `src/main/java/com/wfm/service/ScheduleOutputService.java` **and** `src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java`, all three touched by Task 1. `covered_digest: "v2:sha256:cd0cd40e..."` is stale on three independent grounds, not just the planning-document edit.

Additionally, editing `src/test/resources/bday-join-guard.md` and `src/main/java/com/wfm/service/ScheduleOutputService.java` makes a **third** phase's digest stale:

- **`.planning/phases/20-solver-business-date-correctness/20-VERIFICATION.md`** — `covered_files` includes both `src/test/resources/bday-join-guard.md` and `src/main/java/com/wfm/service/ScheduleOutputService.java`. `covered_digest: "v2:sha256:34097e0b..."` no longer matches.

`.planning/ROADMAP.md` is **not** listed in any of these three `covered_files` arrays, so its edit does not add a fourth staleness.

Per the dispatch notes, this is flagged explicitly rather than silently refreshed — the fingerprint should be refreshed deliberately, with a control, at milestone-close time (or whenever `/gsd-verify-work` or an audit next touches Phases 18/19/20), not auto-corrected here. This plan does not touch any VERIFICATION.md, STATE.md's phase-completion fields, or write a VERIFICATION.md of its own.

## Broken-Windows Ledger

Appended to `.planning/WINDOWS.md` (entry #14, `kind: deviation`, phase 21, `src/main/java/com/wfm/service/ScheduleOutputService.java`) — the "Unassigned assignment" dead-code finding above. 13 pre-existing entries (all phases 13-21) are unaffected by this plan.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- OVNT-07's disclosure requirement is fully implemented and tested: the label now discloses the business date whenever it diverges from the calendar date, built by one shared helper, with both former parsers already confirmed off the string before the text changed.
- The ROADMAP and REQUIREMENTS documents now describe the surfaces that actually exist for OVNT-06, consistent with 21-07 and 21-10's shipped behaviour.
- `requirements-completed` is `[]` in this plan's own frontmatter — `OVNT-06` and `OVNT-07` are both shared with sibling plans (OVNT-06 also declared by 21-07/21-10; OVNT-07 also declared by 21-03/21-07/21-10), none of which are complete in REQUIREMENTS.md's checkbox state yet (both still `[ ]`/Pending in the traceability table). This plan amends the REQUIREMENT TEXT, not completion status — marking requirements complete is a phase-verifier/milestone-audit decision, consistent with the dispatch instructions' explicit "do NOT mark the phase complete" boundary.
- The stale `covered_digest` risk on Phases 18/19/20's VERIFICATION.md (above) should be refreshed deliberately before milestone close, with a control — flagged here so it is not discovered cold at that point.
- No blockers for plan 21-12 (the last plan in this phase, a scope-decision checkpoint about nine build-allowlisted raw time comparisons) — this plan's changes are confined to the timeslot label's text, its own test coverage, and two planning documents; nothing it touches is a dependency 21-12 needs reworked.

---
*Phase: 21-overnight-shift-templates*
*Completed: 2026-10-03*

## Self-Check: PASSED

- All 5 modified files confirmed present on disk via `git status`/`git diff` against `bede6e7` (the plan-head-before ledger).
- Both plan commits (`edd9bb5`, `05febd6`) confirmed present in `git log --oneline -5`.
- `./gradlew compileJava compileTestJava` — clean.
- `./gradlew test --tests "com.wfm.service.ScheduleOutputServiceShiftReportingTest" --tests "com.wfm.service.ScheduleAllocationExportTest" --tests "com.wfm.service.ScheduleExportServiceTest"` — green.
- Full unfiltered `./gradlew test` run (post-`--stop` reset, aggregated from JUnit XML by filename, never trusted from a console line immediately after a filtered run): **1289 tests, 0 failures, 0 errors, 4 pre-existing skips, 201 classes** — up from the 1284-test baseline by exactly the 5 new test methods this plan added (the control test was inverted in place, not added).
- All grep-based acceptance criteria from both tasks re-run and confirmed passing (see Accomplishments and the frontmatter `coverage` block for exact commands and counts).
- WINDOWS.md ledger entry #14 confirmed present via `gsd-tools windows append`'s own returned ledger (14 total entries, 0 fixed, 0 waived).
