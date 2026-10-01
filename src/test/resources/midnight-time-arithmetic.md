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

This guard enforces two independent families, each checked by its own set-equality assertion in
`MidnightTimeArithmeticGuardTest`.

**Raw arithmetic.** The set of lines in `src/main/java` (excluding `DayWindow` itself, which is
the implementation) matching any of `Duration.between(`, `ChronoUnit.MINUTES`, `.plusMinutes(` or
`.minusMinutes(` must equal the "Permitted raw time arithmetic" allowlist below **exactly** — set
equality, never subset. A new occurrence fails the build; an allowlisted line that no longer
exists also fails the build, so the list cannot rot. This family is unconditional: every match on
these four tokens counts, because they are already low-noise on their own.

**Raw comparisons.** `LocalTime.MIDNIGHT.isAfter(LocalTime.of(23, 0))` being `false` is the other
half of the same bug class (see above) and was, until this section existed, completely unguarded.
A line matching `.isAfter(`, `.isBefore(` or `.compareTo(` is only in scope when its RECEIVER
looks like a scheduling time — the identifier immediately to the left of the token, ignoring one
trailing empty argument list (so both `slotStart.isBefore(x)` and `getStartTime().isBefore(x)`
resolve to a receiver name). A receiver counts when it, case-insensitively, **ends with** `time`,
`start` or `end`, or **begins with** `envelope`, `band`, `break` or `slot`. The argument is
deliberately never inspected — only the receiver — because inspecting the argument pulls in
`date.isAfter(periodEnd)` and `hours.compareTo(breakConfig.getMaxHours())`: `LocalDate` and
`BigDecimal` comparisons with nothing to do with the midnight boundary. These three tokens appear
roughly a hundred times combined across `src/main/java`, almost all of them date or decimal
comparisons; an ungated scan would demand a hundred-entry allowlist, which is the
guard-becomes-decoration failure this file's own javadoc warns against, so gating by receiver name
is what keeps the list reviewable.

**The heuristic's limitations — read this before editing the comparison allowlist.** This
receiver-name heuristic is the part of the guard most likely to rot, and it has three known,
named limitations:

1. **It is name-based rather than type-aware.** It never inspects the receiver's declared type, so
   a scheduling time held in a local variable whose name does not match the pattern list (e.g. a
   variable called `t` or `whenDone`) escapes the scan entirely, and a `LocalDate` or other
   non-time value held in a matching-named local (e.g. a `LocalDate` called `periodStart`) is
   caught spuriously. A type-aware parser was evaluated and rejected as a new build dependency;
   revisit only if this heuristic proves noisy in practice.
2. **It inspects only the receiver, never the argument.** A scheduling time appearing solely as
   the *argument* of a comparison — never as the receiver — escapes the scan, by the same design
   choice that keeps date and decimal noise out (see above).
3. **It tolerates exactly one trailing empty argument list.** A two-level accessor chain (e.g.
   `shift.getBandPair().getStartTime().isBefore(x)`) resolves to the nearest name before the
   token — here `getStartTime()` — rather than the true owner further left (`shift`). For this
   heuristic's purpose that is fine, since `getStartTime()` is itself the receiver that matters,
   but a chain shaped differently could resolve to an unexpected name.

A false positive fails safe: a human looks at a diff and adds a justified allowlist entry or a
`DayWindow` fix. A false negative is only possible under one of the three limitations above;
narrowing that gap further is deferred rather than solved by a heavier, parser-based detector.

**Deliberately out of scope.** Date tokens — `.getDate()`, `.getDayOfWeek()`, `.plusDays(`,
`ChronoUnit.DAYS` — are not guarded here. `SOLV-02` owns the calendar-vs-business-date join guard,
and adding date tokens to either family here would pre-empt that requirement rather than support
it.

Comment lines are stripped before matching, so prose mentioning these names is free.

**The guard's blind spot (D-05).** This guard scans for `Duration.between(`, `ChronoUnit.MINUTES`,
`.plusMinutes(`, `.minusMinutes(` and the three `LocalTime` comparison tokens above — and therefore
cannot see `int` arithmetic or `int` comparisons AT ALL. `DayWindow.anchoredStartMinute(t)` and
`DayWindow.anchoredEndMinute(t)` both return a plain `int`, so hand-composing an interval from them
at a call site — `endMinuteFromDayStart(ds, e) - startMinuteFromDayStart(ds, s)` for a duration, or
a four-call `&&` for an overlap — would pass every scan in this file while silently reimplementing
half-open interval semantics outside `DayWindow`. The only correct place for interval semantics is
inside `DayWindow` itself; this note exists so the next person who considers hand-composing learns
the guard will not catch them, from the guard's own documentation.

**Third family (D-07): permitted midnight anchors.** A `DayWindow.anchoredAt(LocalTime.MIDNIGHT)`
binding is a third, independent thing this guard enforces, scanned and allowlisted exactly like the
two families above — see "Permitted midnight anchors" below.

## When a new entry is legitimate

**Raw arithmetic or comparison entries** are legitimate only when **both** endpoints are START
positions, where `00:00` genuinely means the start of the day and the ambiguity does not arise.
Every entry in those two sections is a start-to-start distance. If either endpoint is an interval
END — a timeslot's `endTime`, a shift template's `endTime`, a break's end, a schedule's or desk's
closing time — the line belongs in `DayWindow` instead, via `durationMinutes`, `overlaps`,
`contains`, `startsBefore`, `endMinute` or `plusWithinDay`.

Adding a line to either of those sections **without** that being true silently reopens the defect
this guard closes. Say why the endpoints are starts in the annotation, as the entries below do.

**A midnight-anchor entry** (the third section) is legitimate only when the call site genuinely
cannot reach a desk's real day-start anchor — a standalone utility with no `Desk`/tenant context in
scope, or a constraint stream Timefold's arity cap keeps `ScheduleConfig` out of. It is never
legitimate merely because a desk's anchor currently happens to be `00:00` (D-07) — that is true of
every desk today and proves nothing about reachability. Say why no desk anchor is reachable at that
site in the annotation, as the entries below do, and name the requirement ID that owns removing it
once the site becomes reachable.

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

### Permitted raw time comparisons

```
com.wfm.service.FteUploadService :: if (startTime == null || slotStart.isBefore(startTime)) startTime = slotStart;
com.wfm.service.ScheduleExportService :: if (earliest == null || ad.startTime().isBefore(earliest)) earliest = ad.startTime();
com.wfm.service.ScheduleOutputService :: startOk = actStart != null && !actStart.isBefore(prefStart);
com.wfm.service.ScheduleOutputService :: int sign = actualStartTime.isAfter(usualStartTime) ? 1
com.wfm.service.ScheduleOutputService :: : actualStartTime.isBefore(usualStartTime) ? -1 : 0;
com.wfm.service.ShiftLibraryGenerationService :: if (start.isBefore(earliestStart) || window.anchoredEndMinute(end) > window.anchoredEndMinute(latestEnd)) {
com.wfm.service.ShiftLibraryGenerationService :: int startCompare = candidate.spanStart().compareTo(currentBest.spanStart());
com.wfm.solver.AgentAssignmentDifficultyComparator :: int timeCompare = a.getTimeslot().getStartTime().compareTo(b.getTimeslot().getStartTime());
com.wfm.solver.ScheduleConstraintProvider :: return a.getTimeslot().getStartTime().isBefore(p.getPreferredStartTime());
```

### Why each comparison is permitted

- **`FteUploadService`** — the min-start tracking beside the sheet's already-correct max-end
  tracking two lines below it (which routes through `DayWindow.endMinute`). Both operands of this
  line are slot START times; the ambiguity only exists at the end boundary.
- **`ScheduleExportService`** — the earliest-start half of the roster cell's earliest/latest loop.
  The latest-end half of the same loop routes through `DayWindow.endMinute` (see "Permitted raw
  time arithmetic" above); this line's both operands are assignment START times.
- **`ScheduleOutputService`, all three lines** — the preference-report start check
  (`actStart`/`prefStart`) and the two ternary branches of the drift-sign calculation
  (`actualStartTime`/`usualStartTime`). Every operand named here is a START time; no end is
  involved in any of the three.
- **`ShiftLibraryGenerationService`, both lines** — the earliest-start clause that shares its line
  with an already-correct end comparison, now routed through the method's own bound `window`
  (`start`/`earliestStart` are both starts; only the end half needed `DayWindow`), and the
  span-start tie-break (`candidate.spanStart()`/`currentBest.spanStart()`), whose `spanStart()` is
  built via `DayWindow.toLocalTime` and is a START position by construction.
- **`AgentAssignmentDifficultyComparator`** — the start-time tie-break between two timeslots'
  `getStartTime()`. Both are START positions; the comparator never orders by end.
- **`ScheduleConstraintProvider`** — the preferred-start comparison between a timeslot's START and
  an agent's preferred START time. No end is involved.

### Permitted midnight anchors

```
com.wfm.util.FteSpreadsheetGenerator :: DayWindow window = DayWindow.anchoredAt(LocalTime.MIDNIGHT);
com.wfm.solver.ScheduleConstraintProvider :: private static final DayWindow PENDING_DESK_ANCHOR = DayWindow.anchoredAt(LocalTime.MIDNIGHT);
com.wfm.service.ShiftLibraryGenerationService :: DayWindow.anchoredAt(LocalTime.MIDNIGHT));
com.wfm.model.ShiftBandPair :: DayWindow.anchoredAt(LocalTime.MIDNIGHT));
```

### Why each midnight anchor is permitted

- **`FteSpreadsheetGenerator.generate`** — a standalone report-generation utility with its own
  `main`, no `Desk` or tenant context reachable anywhere in the file (BDAY-04, plan 19-07). There is
  no desk to bind; `SOLV-01` does not own this one, since no join re-point will ever make a desk
  reachable here — it stays allowlisted for the life of this file's current shape.
- **`ScheduleConstraintProvider.PENDING_DESK_ANCHOR`** — the single named constant seven
  `ifExists(ScheduleConfig.class, filtering(...))`-gated constraints (`exactlyOneBreak`,
  `breakDuration`, `breakBlockedWindow`, `breakStartAlignment`, `shiftWorkContiguity`,
  `honourPreferredBreakTime`, reached through their shared interval-arithmetic helpers) pass instead
  of the real desk anchor. Timefold 1.16.0 has no Penta (five-argument) constraint stream, so
  `ScheduleConfig` cannot be joined into these already-Quad streams; `SOLV-01` owns replacing this
  constant with the real anchor read from the joined `ScheduleConfig` when it re-points the joins
  (BDAY-04/P-01).
- **`ShiftLibraryGenerationService.resolveBreakConfig`** — the zero-schedule-yet fallback branch
  (plan 19-06). This class holds neither a `DeskRepository` nor a `Schedule` parameter of its own;
  when no `Schedule` has ever been created for the desk there is no anchor source to read at all, so
  the binding falls back to midnight until the first `Schedule` exists, at which point the method's
  normal path binds the real anchor from that `Schedule` instead.
- **`ShiftBandPair.netHours`** (`@Deprecated`, transitional) — this record's remaining callers
  (`AgentShiftAssignment`, `ShiftLibraryGenerationService`'s `Candidate`/`EmittedRow` report fields,
  and their tests) do not yet reach a desk anchor (plan 19-04/19-05). Mirrors the transitional
  `covers(Timeslot)` shape plan 19-05 retired once every production
  `covers(Timeslot, DayWindow)` caller supplied a real anchor; callers of `netHours()` move to a
  window-aware form as each is migrated, and this entry is removed once none remain.
