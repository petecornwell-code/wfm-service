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

Three scenarios in this suite assert what this codebase does TODAY at a property whose correct
behaviour has not shipped yet. Each is marked `@AssertsTodaysBehaviour` on its test method
(`com.wfm.support.AssertsTodaysBehaviour`); `MidnightBoundaryScenarioRegistryTest` asserts the
marked-method set and the fenced registry below are set-equal in BOTH directions. A later phase
implementing one of these requirements must remove the corresponding entry below in the SAME
change that flips the assertion it names -- leaving a stale entry here after the method it
described was deleted or rewritten fails the build exactly as an unregistered new mark does.

| Scenario | Flipped by | What it flips to |
|---|---|---|
| A day-off record's attribution to a shift's calendar date | OVNT-03 | Attribution follows the business day a midnight-spanning shift starts on, not each individually stamped calendar date |
| Contracted hours consumed by the starting weekday only | OVNT-04 | The whole stretch of a midnight-spanning shift consumes only the starting weekday's contracted-hours row |
| A shift starting before and ending after midnight | OVNT-01 | The interval means the shift crosses the day anchor into the next calendar date, not that the interval is malformed |

### Scenarios asserting today's behaviour

```
com.wfm.solver.MidnightBoundaryRegressionTest.PtoOnAdjacentCalendarDate#attributionIsPerCalendarDateOnly -> OVNT-03
com.wfm.service.MidnightBoundaryPropertyTest.ContractedHoursStartingWeekdayOnly#twoCalendarDatesDrawFromTwoIndependentWeekdayRows -> OVNT-04
com.wfm.service.MidnightBoundaryPropertyTest.ShiftCrossingMidnight#durationMinutesThrowsAndTheSavePathRefuses -> OVNT-01
```
