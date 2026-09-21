---
phase: 15-shift-envelope-breaks-library-generation
plan: 10
subsystem: reporting
tags: [shift-envelope, break-geometry, timefold, report-layer, excel-export, coverage-predicate]

# Dependency graph
requires:
  - phase: 15 (plans 01-08, prior to this plan)
    provides: ShiftBandPair.covers(Timeslot), ShiftDescriptor resolution (resolveShiftDescriptor), D-07 denormalised accept-time snapshot columns on AgentShiftAssignment — all consumed unchanged by this plan
provides:
  - "ShiftBandPair.covers(LocalTime, LocalTime, Integer, Integer, LocalTime, LocalTime) — the one static coverage predicate, shared by the solver's instance method and the report layer"
  - "ScheduleDetailResponse.ShiftEnvelopeDivergence — a first-class divergence surface on every shift-mode AgentScheduleEntry"
  - "ScheduleOutputService.buildAgentSchedule/buildPreferenceReport reading the authoritative shift descriptor (template span, band-derived break) instead of re-deriving both from the seat pattern"
  - "ScheduleExportService Agent Schedule sheet exposing the assigned shift's template name and envelope on shift desks"
affects: [phase-16-usual-shift-storage, phase-17-consistency-constraint-drift-reporting, frontend-ScheduleResults.tsx-future-consumption-of-divergence-and-bandOffset/DurationMinutes]

# Actuals (#2632)
actuals:
  tokens: 11000
  tasks: 3
  commits: 4

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "One coverage predicate, two callers (D-08 discipline extended to envelope/break coverage): ShiftBandPair.covers(Timeslot) delegates to a public static overload the report layer calls directly, so the solver and the report layer cannot disagree about what an envelope covers."
    - "Descriptor-first branching in report builders: resolve the shift descriptor BEFORE computing span/breaks, branch on presence, and share the one resolution helper (buildShiftDescriptorsByAgentDate) across buildAgentSchedule and buildPreferenceReport so two independent builders cannot drift apart."
    - "Divergence as data, not silence: an envelope violation is now a first-class ShiftEnvelopeDivergence record on the response and a rolled-up warning, rather than being absorbed into a redrawn span."

key-files:
  created:
    - src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java
    - src/test/java/com/wfm/service/ScheduleExportServiceTest.java
  modified:
    - src/main/java/com/wfm/model/ShiftBandPair.java
    - src/main/java/com/wfm/dto/ScheduleDetailResponse.java
    - src/main/java/com/wfm/service/ScheduleOutputService.java
    - src/main/java/com/wfm/service/ScheduleExportService.java

key-decisions:
  - "Static ShiftBandPair.covers overload takes the four scalars a ShiftDescriptor carries directly (envelope start/end, nullable band offset/duration, slot start/end) rather than live ShiftTemplate/ShiftTemplateBreakBand references, so the report layer — which only ever holds the scalar descriptor — can call the SAME predicate instead of re-deriving the arithmetic."
  - "ShiftEnvelopeDivergence added as a trailing NULLABLE component of AgentScheduleEntry, deliberately breaking any positional-construction call site at compile time rather than risking a silent mis-bind."
  - "The Agent Schedule export sheet gains a 'Shift Template'/'Shift Envelope' column pair ONLY when at least one entry in the response carries a shift descriptor — a slot desk's export sheet stays byte-identical to today, including column count and header text, since no slot-desk entry ever carries a shift."
  - "Preference-report actualBreakTime/breakTimeHonouredCount now resolve through the same shared shift descriptor as the Agent Schedule builder on a shift desk — an intentional, test-asserted KPI correction (T-15-10-05), not a regression: a band-derived break honours or misses a preferred break time differently from a hole-derived one."
  - "Divergence computation walks the schedule's own timeslots for the agent-day's date (not the agent's assignment list, which cannot express a slot not held) to find unworked-legal slots; returns null when both lists are empty so a clean agent-day carries no noise."

patterns-established:
  - "Roll a per-entry divergence total up into the schedule's existing warnings collection (schedule.getWarnings().add(...)) rather than adding a parallel reporting channel — gives the operator a headline without scanning every row."

requirements-completed: [ENVL-05, ENVL-07, ENVL-10, XCUT-01]

coverage:
  - id: D1
    description: "One coverage predicate for envelope/break coverage — ShiftBandPair exposes a public static overload that the instance covers(Timeslot) delegates to with no behavioural change; the report layer is the second caller."
    verification:
      - kind: other
        ref: "./gradlew compileJava compileTestJava && ./gradlew test (full suite, includes every pre-existing solver test that calls the unchanged instance covers(Timeslot))"
        status: pass
    human_judgment: false
  - id: D2
    description: "ShiftEnvelopeDivergence type added as a nullable trailing component of AgentScheduleEntry, carrying out-of-envelope seats and unworked legal slots."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java#buildAgentSchedule_strayOutOfEnvelopeSeat_namesBothSidesOfTheDivergenceWithEqualSizeLists"
        status: pass
    human_judgment: false
  - id: D3
    description: "On a shift desk, the reported span is the assigned template's envelope, not min/max over held seats; the reported break is the band window, not gap-derived holes."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java#buildAgentSchedule_strayOutOfEnvelopeSeat_reportsTheTemplateSpanNotTheSeatSpan"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java#buildAgentSchedule_strayOutOfEnvelopeSeat_reportsExactlyOneBandShapedBreak"
        status: pass
    human_judgment: false
  - id: D4
    description: "A clean agent-day (held seats exactly equal legal slots) reports null divergence, template span, and exactly one band-shaped break."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java#buildAgentSchedule_cleanAgentDay_reportsNullDivergenceTemplateSpanAndOneBandBreak"
        status: pass
    human_judgment: false
  - id: D5
    description: "An unassigned shift-mode agent-day (no descriptor) falls back to seat-derived span and gap-derived breaks, carries no divergence, and does not throw."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java#buildAgentSchedule_unassignedShift_fallsBackToSeatDerivedOutputWithNoDivergenceAndDoesNotThrow"
        status: pass
    human_judgment: false
  - id: D6
    description: "A slot-scheduled desk's Agent Schedule entries are byte-identical to today: seat-derived span, gap-derived breaks, null shift, null divergence."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java#buildAgentSchedule_slotMode_producesByteIdenticalEntriesToToday"
        status: pass
    human_judgment: false
  - id: D7
    description: "An accepted schedule (D-07 denormalised scalar columns, transient shiftBandPair null) resolves through the same descriptor path and produces the same span/breaks/divergence as a live in-memory schedule."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java#buildAgentSchedule_acceptedScheduleDenormalisedColumns_resolvesThroughTheSameDescriptorPath"
        status: pass
    human_judgment: false
  - id: D8
    description: "Preference-report actualBreakTime and breakTimeHonoured switch to the band window on a shift desk instead of a gap-derived hole (T-15-10-05)."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java#buildPreferenceReport_shiftDesk_actualBreakTimeAndHonouredFlagUseTheBandWindowNotAGapDerivedHole"
        status: pass
    human_judgment: false
  - id: D9
    description: "The Excel export's Break rows inherit the band-derived correction (no code change needed); the Agent Schedule sheet's assignment rows carry the template name and envelope span on a shift desk."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleExportServiceTest.java#exportToExcel_shiftDesk_breakRowsCarryTheBandWindowAndAssignmentRowsCarryTheShift"
        status: pass
    human_judgment: false
  - id: D10
    description: "A slot desk's Excel export Agent Schedule sheet is byte-identical to today, including column count and header text."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ScheduleExportServiceTest.java#exportToExcel_slotDesk_sheetIsByteIdenticalToToday"
        status: pass
    human_judgment: false
  - id: D11
    description: "Full backend suite green after all changes (DTO shape change breaks positional construction at compile time; preference KPI change is a visible number shift)."
    verification:
      - kind: other
        ref: "./gradlew test"
        status: pass
    human_judgment: false

duration: 44min
completed: 2026-08-27
status: complete
---

# Phase 15 Plan 10: Shift Envelope Report-Layer Divergence Summary

**The Agent Schedule table, preference-report break KPIs, and Excel export now read the authoritative shift envelope and band-derived break the solver already resolved — instead of silently redrawing the envelope around whatever seats the solver happened to place — and a coupled out-of-envelope-seat / unworked-legal-slot divergence is surfaced as first-class data on every shift-mode agent-day.**

## Performance

- **Duration:** 44 min
- **Started:** 2026-08-27T16:08:17Z
- **Completed:** 2026-08-27T16:51:58Z
- **Tasks:** 3
- **Files modified:** 6 (4 production, 2 new test files)

## Accomplishments

- Extracted `ShiftBandPair.covers(...)` into one static predicate (four scalars in, boolean out) that the solver's `covers(Timeslot)` instance method delegates to unchanged — the report layer now calls the exact same predicate instead of re-deriving envelope/break arithmetic a second time.
- `ScheduleOutputService.buildAgentSchedule` and `buildPreferenceReport` resolve the shift descriptor BEFORE computing span/breaks and branch on it: present → template span + band-derived break + computed divergence; absent (slot desk, or an unassigned shift-mode agent-day) → today's seat-derived/gap-derived behaviour, unchanged.
- Added `ScheduleDetailResponse.ShiftEnvelopeDivergence` — out-of-envelope held seats vs. unworked legal slots — as a nullable trailing `AgentScheduleEntry` component, and rolled a non-empty total up into the schedule's warnings collection.
- `ScheduleExportService`'s Agent Schedule sheet now shows the assigned shift's template name and envelope span on shift desks (new columns, present only when at least one entry carries a shift); Break rows already read `AgentScheduleEntry.breaks()`, so they inherited the band-derived correction with zero writer-side changes.
- Inverted the characterising evidence at `.planning/debug/characterising-tests/ShiftModeBreakGeometryCharacterisationTest.java` (`reportLayer_gapDerivedBreaks_relabelEveryHoleAsABreak`, which passed ON the defect) with a real regression suite in `src/test`, observed RED against the pre-fix logic first.

## Task Commits

Each task was committed atomically:

1. **Task 1: One coverage predicate, and a divergence type on the response** - `a508cbb` (feat)
2. **Task 2: Read the authoritative envelope and band in shift mode; compute divergence** - `1bd953b` (fix)
   - Follow-up test for the threat model's T-15-10-05 mitigation - `dcba6d0` (test)
3. **Task 3: Confirm the export inherits the correction, and prove no slot-mode drift** - `459d445` (feat)

**Plan metadata:** commit to follow this SUMMARY.

## Files Created/Modified

- `src/main/java/com/wfm/model/ShiftBandPair.java` - extracted static `covers(...)` overload; instance method delegates
- `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` - added `ShiftEnvelopeDivergence` record; `AgentScheduleEntry` gains a trailing nullable `divergence` component
- `src/main/java/com/wfm/service/ScheduleOutputService.java` - descriptor-first branching in `buildAgentSchedule`/`buildPreferenceReport`; new `buildShiftDescriptorsByAgentDate`, `bandBreaks`, `computeDivergence` helpers; divergence rolled into `schedule.getWarnings()`
- `src/main/java/com/wfm/service/ScheduleExportService.java` - conditional Shift Template/Shift Envelope columns on the Agent Schedule sheet
- `src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java` - new: 8 tests covering span, break, divergence, clean agent-day, unassigned shift, slot invariance, accepted-schedule descriptor path, and the preference-report KPI switch
- `src/test/java/com/wfm/service/ScheduleExportServiceTest.java` - new: first test coverage for `ScheduleExportService`, shift-desk and slot-desk shapes

## Decisions Made

- Static `ShiftBandPair.covers` takes the four scalars a `ShiftDescriptor` carries (not live entity references) so the report layer can call it directly without holding a `ShiftTemplate`/`ShiftTemplateBreakBand` pair.
- `ShiftEnvelopeDivergence` is a trailing NULLABLE `AgentScheduleEntry` component, deliberately forcing any positional-construction call site to fail compile — there was exactly one such site (`ScheduleOutputService.java:196`), updated in the same task.
- Export's new Shift Template/Shift Envelope columns are conditional on `entries.stream().anyMatch(e -> e.shift() != null)`, not unconditional — this keeps a slot desk's export sheet byte-identical (column count and header text) per the plan's own success criterion, at the cost of the two sheet shapes (slot vs. shift) having different column counts. Break rows leave the new cells blank even on a shift desk — they belong to the assignment rows.
- Preference-report break KPIs now share the SAME shift-descriptor resolution helper as `buildAgentSchedule` (`buildShiftDescriptorsByAgentDate`), resolved once, rather than each builder independently deriving its own — closes the "two chances to disagree" gap the plan called out.
- Divergence's unworked-legal-slot side is derived by walking `schedule.getTimeslots()` for the agent-day's date, because `AgentAssignment` (the agent's own held-seat list) cannot express a slot the agent does NOT hold — the candidate slot set has to come from the schedule itself.

## Deviations from Plan

None - plan executed exactly as written, including the TDD RED-then-GREEN sequence for Task 2 (Tests 1/2/3/7 were reproduced RED against the pre-fix logic by temporarily restoring the original `buildAgentSchedule` body — patched only to compile against Task 1's new DTO shape — running the new test suite, and recording the exact failure text below, before restoring the fix).

### RED evidence (Task 2, pre-fix)

```
buildAgentSchedule_strayOutOfEnvelopeSeat_namesBothSidesOfTheDivergenceWithEqualSizeLists
  java.lang.AssertionError: Expecting actual not to be null

buildAgentSchedule_strayOutOfEnvelopeSeat_reportsTheTemplateSpanNotTheSeatSpan
  expected: 12:00
   but was: 08:00

buildAgentSchedule_acceptedScheduleDenormalisedColumns_resolvesThroughTheSameDescriptorPath
  expected: 12:00
   but was: 08:00

buildAgentSchedule_strayOutOfEnvelopeSeat_reportsExactlyOneBandShapedBreak
  Expected size: 1 but was: 3 in:
  [BreakDetail[startTime=09:00, endTime=12:00, durationMinutes=180],
      BreakDetail[startTime=13:00, endTime=14:00, durationMinutes=60],
      BreakDetail[startTime=16:00, endTime=17:00, durationMinutes=60]]
```

This exactly reproduces the live UAT defect (`.planning/debug/shift-mode-break-geometry-ungoverned.md`): the reported span is dragged to the stray seat's own start, and the three-way merged/scattered gap set is relabelled as three separate "breaks" instead of the one real band break.

## Threat Flags

None — all five register entries (T-15-10-01..05) were dispositioned `mitigate` in the plan and closed by this execution: T-15-10-01/02 by the descriptor-first branching and Test 6/D10 slot-mode invariance proofs, T-15-10-03 accepted as designed (no new trust boundary — the divergence component carries only timeslot start times already present in the same entry), T-15-10-04 by the single static predicate, T-15-10-05 by the dedicated preference-report test (D8).

## Issues Encountered

None. Two full `./gradlew test` runs were required to reach final confirmation — the second run (after adding the T-15-10-05 preference-report test) took 14m32s under heavier machine load from an unrelated concurrent Gradle daemon on this host; both runs were `BUILD SUCCESSFUL` with 0 failures/errors (525 tests before the last addition, 526 after, 2 pre-existing skips unrelated to this plan).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `AgentScheduleEntry.divergence` and the export's Shift Template/Shift Envelope columns are backend-only in this plan — the frontend (`ScheduleResults.tsx`, `client.ts`) does not yet render `divergence` or the export's new columns. A future phase/plan should wire the frontend to surface the divergence (the debug lane's H4 finding: `bandOffsetMinutes`/`bandDurationMinutes` were already present in the DTO but read nowhere in the frontend before this plan — the same risk now applies to `divergence` unless a follow-up closes it).
- The preference-report KPI change (band-derived instead of gap-derived break honouring on shift desks) is a visible number shift for any shift desk with existing UAT/demo data — flagged here so it is not mistaken for a regression when next observed.
- No blockers for the remaining 15-11..15-13 gap-closure plans in this G-15-10 sequence.

## Self-Check: PASSED

All 6 key files confirmed present on disk; all 4 task commit hashes (a508cbb, 1bd953b, 459d445, dcba6d0) confirmed in `git log`.

---
*Phase: 15-shift-envelope-breaks-library-generation*
*Completed: 2026-08-27*
