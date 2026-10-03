# Midnight-Boundary Scenario Registry (BDAY-06)

This file is parsed at test time by `MidnightBoundaryFixture`'s class-load validator and by
`MidnightBoundaryScenarioRegistryTest`. Editing it changes what the build enforces, not merely
what a human reads.

## Why this guard exists

BDAY-06 replaces earlier captured-live-desk golden files -- over-sensitive on a large real roster
and under-powered on the midnight boundary itself -- with a constructed suite whose scenarios are
chosen by the property under test, never copied from a desk. Two failure modes that suite must
resist are guarded here:

1. **Going vacuous.** A future edit could remove the boundary case a scenario exists to prove
   while leaving the scenario itself green -- for example, narrowing a timeslot's times so it no
   longer actually reaches the end of the day. The predicates below name the structural
   properties every constructed scenario is checked against at class-load time; if a named
   predicate stops firing across every scenario, the build fails immediately rather than the
   suite quietly stopping to mean anything.
2. **Flipping without updating.** Some scenarios assert what this codebase does TODAY at a
   property whose correct behaviour has not shipped yet. A later phase implementing the real
   behaviour must update this file's registry section in the SAME change that flips the
   assertion -- see the registry section this file gains once that section exists.

## What is enforced

The set of structural predicate names in `MidnightBoundaryFixture.STRUCTURAL_PREDICATES` must
equal the fenced allowlist below **exactly** -- set equality, never subset, in both directions. A
predicate added in code without a matching entry here, or an entry surviving that predicate's
deletion from code, both fail the build at class-load.

### Boundary predicates

```
timeslot ending at day end
final hour timeslot before day end
band flush to envelope edge
envelope crossing the day anchor
```

### Why each predicate is a real boundary this codebase can get wrong

- **timeslot ending at day end** -- a timeslot whose end time is the start of a new day stores the
  SMALLEST value its time type can hold, for what is, by position, the LARGEST minute-of-day. A
  raw chronological comparison against it silently drops the final slot of the day from every
  ordering check -- the defect class this codebase's day-window arithmetic exists to close.
- **final hour timeslot before day end** -- the specific one-hour slot immediately before that
  boundary. Its coverage and minimum-staffing scoring must treat it like any other slot; an
  off-by-one at the boundary would silently under- or over-count this slot alone.
- **band flush to envelope edge** -- a break band whose end lands EXACTLY on its own shift
  envelope's end. One minute either side of this line is legal or illegal; sitting exactly on it
  is the line itself, and is the hardest case to get right with inclusive/exclusive boundary
  comparisons.
- **envelope crossing the day anchor** -- a shift envelope whose stored end time, read as a plain
  chronological value, is no later than its own stored start time, because the end position
  stores the smallest value in its type for "the end of the day". Reusable verbatim against a
  genuine multi-day-spanning envelope should one ever exist -- this predicate reads only a
  schedule's own problem facts, never a fixture's construction helpers.

## Scenarios asserting today's behaviour

This registry is now EMPTY. Every scenario that once asserted what this codebase did TODAY at a
property whose correct behaviour had not shipped yet has had its assertion flipped, with its
registry row removed in the SAME change -- never left stale, never flipped without removal. Each
is marked `@AssertsTodaysBehaviour` on its test method
(`com.wfm.support.AssertsTodaysBehaviour`) only while its row still stands here;
`MidnightBoundaryScenarioRegistryTest` asserts the marked-method set and the fenced registry below
are set-equal in BOTH directions, so a future scenario that genuinely needs this mechanism again
adds both at once.

(SOLV-04's own entry -- "A SLOT-mode cross-midnight stretch charged to the wrong calendar dates" --
was removed here in the SAME commit that flipped its assertion, plan 20-05: the join now resolves
business date, so `SlotModeOvernightContractedHoursTest.TodaysBehaviour` and its registry entry are
both gone, not merely updated. The before-picture those literals recorded now lives in plan
20-04's SUMMARY. OVNT-01's own entry -- "A shift starting before and ending after midnight" -- was
removed here in the SAME commit that flipped its assertion, plan 21-01: the save path now accepts
an overnight envelope on an anchored desk, so
`MidnightBoundaryPropertyTest.ShiftCrossingMidnight#durationMinutesThrowsAndTheSavePathRefuses` was
rewritten to `#anchoredDeskAcceptsTheCrossingEnvelope` and both its marker and this registry row are
gone, not merely updated. The before-picture is preserved in plan 21-01's SUMMARY. The remaining
two entries -- "A day-off record's attribution to a shift's calendar date" (OVNT-03) and
"Contracted hours consumed by the starting weekday only" (OVNT-04) -- were both discharged here, in
plan 21-06, in the SAME commit that rewrote the assertions each one named:
`MidnightBoundaryRegressionTest.PtoOnAdjacentCalendarDate#attributionIsPerCalendarDateOnly` became
`DayOffAttributesToStartingBusinessDate#dayOffOnStartingBusinessDate_blocksEverySeatIncludingTheFollowingCalendarDate`,
proving the day-off join's existing business-date attribution against a genuinely
midnight-crossing agent-day rather than against a shift whose seats happened to share one
calendar date; `MidnightBoundaryPropertyTest.ContractedHoursStartingWeekdayOnly#twoCalendarDatesDrawFromTwoIndependentWeekdayRows`
became `#bothCalendarDatesResolveToOneBusinessDate_oneWeekdaysHoursCoverTheWholeStretch`, proving
that both calendar dates of a midnight-spanning stretch resolve to the same business date and that
`resolveEffectiveHours` -- itself unchanged -- is handed that one date for the whole stretch. Both
before-pictures are preserved in plan 21-06's SUMMARY.)

### Scenarios asserting today's behaviour

```
```
