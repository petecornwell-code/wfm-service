# Raw Time-Arithmetic Allowlist (midnight boundary)

This file is parsed at test time by `MidnightTimeArithmeticGuardTest`
(`src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java`). Editing it changes what
the build enforces, not merely what a human reads.

## Why this guard exists

Every scheduling time in this codebase is a `java.time.LocalTime`, which has no `24:00` — its
maximum is `23:59:59.999999999`. A desk whose day runs to midnight therefore stores its end as
`00:00`, the **smallest** value the type can hold. Consequently:

- `Duration.between(LocalTime.of(15, 0), LocalTime.MIDNIGHT)` is **−900 minutes**, not 540.
- `LocalTime.MIDNIGHT.isAfter(LocalTime.of(23, 0))` is **false**.
- `LocalTime.of(23, 0).plusMinutes(120)` silently **wraps** to `01:00` — an earlier time that
  then fails every ordering check downstream.

None of these throw. They produce plausible wrong numbers that travel into net-hours, coverage
checks, seat legality and the solver's envelope arithmetic. `com.wfm.util.DayWindow` is the single
implementation that reads `00:00` by **position** — minute `0` in a start position, minute `1440`
in an end position — and every interval calculation is supposed to go through it.

This guard exists because the bug class is re-introducible by writing one perfectly ordinary line
of Java. A developer adding `Duration.between(slot.getStartTime(), slot.getEndTime())` to a new
constraint would reintroduce it with no test failing, exactly as the original code did.

## What is enforced

The set of lines in `src/main/java` (excluding `DayWindow` itself, which is the implementation)
matching any of `Duration.between(`, `ChronoUnit.MINUTES`, `.plusMinutes(` or `.minusMinutes(`
must equal the fenced allowlist below **exactly** — set equality, never subset. A new occurrence
fails the build; an allowlisted line that no longer exists also fails the build, so the list
cannot rot.

Comment lines are stripped before matching, so prose mentioning these names is free.

## When a new entry is legitimate

Only when **both** endpoints are START positions, where `00:00` genuinely means the start of the
day and the ambiguity does not arise. Every entry below is a start-to-start distance. If either
endpoint is an interval END — a timeslot's `endTime`, a shift template's `endTime`, a break's end,
a schedule's or desk's closing time — the line belongs in `DayWindow` instead, via
`durationMinutes`, `overlaps`, `contains`, `startsBefore`, `endMinute` or `plusWithinDay`.

Adding a line here **without** that being true silently reopens the defect this guard closes. Say
why the endpoints are starts in the annotation, as the entries below do.

## Allowlist

Format: fully-qualified class name, ` :: `, then the code line with leading and trailing
whitespace trimmed and any trailing `//` comment removed.

### Permitted raw time arithmetic

```
com.wfm.model.ShiftBandPair :: java.time.temporal.ChronoUnit.MINUTES.between(usualStartTime, assignedEnvelopeStart));
com.wfm.service.ScheduleOutputService :: long minDistance = Math.abs(ChronoUnit.MINUTES.between(closest.startTime(), preferredBreak));
com.wfm.service.ScheduleOutputService :: long dist = Math.abs(ChronoUnit.MINUTES.between(breaks.get(i).startTime(), preferredBreak));
```

### Why each is permitted

- **`ShiftBandPair.startDeviationMinutes`** — the distance between an agent's *usual* shift START
  and their *assigned* envelope START (DRFT-03's one distance calculation). Both are start times;
  no end is involved, and the method is explicitly documented as start-only.
- **`ScheduleOutputService`, both lines** — the distance between an actual break's START and the
  agent's preferred break START, used to pick the closest break for the preference report. Both
  are start times. The overlap test in the same class, which *does* involve break ends, goes
  through `DayWindow.overlaps`.
