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
4. **It inspects names rather than anchors.** The scan can tell a scheduling-time receiver from a
   non-scheduling one, but it cannot tell a comparison that is correct at any anchor from one that
   is only correct at midnight, because it inspects names rather than anchors — it has no notion
   of "anchor" at all. The end-boundary ambiguity
   this guard's "Raw comparisons" section exists to catch and the ordering question a start-to-
   start comparison answers are two SEPARATE properties: an entry justified on "both operands are
   start times" has answered only the first. Two start times compared raw order by CLOCK, not by
   position in the business day, so on a desk anchored away from midnight a later-clock-time start
   can be an EARLIER business-day start, and a start-to-start entry's justification can be true
   about the end boundary while being silent — and wrong — about ordering. This is what let the
   nine-entry family below (plan 21-12, OVNT-02) survive unexamined for three prior plans in this
   phase: every entry was true about the end boundary and never asked about ordering. Narrowing
   this gap further means tracking a desk's anchor through the scan, which this heuristic does not
   do and is not scoped to do.

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

**A second, narrower kind of legitimate entry (OVNT-05/D-07): deliberately testing against CALENDAR
midnight, not reachability.** `ShiftTemplateService.crossesCalendarMidnight` is bound to this
desk's own `window` elsewhere in the same method, so the desk's real anchor is trivially reachable
here — this entry is not an unreachability exception. It is permitted because the question it asks
is independent of any desk's anchor by design: "does this pair cross calendar midnight at all,"
which is the fixed split key between a blocking overnight escape and a non-blocking same-day one
(OVNT-05/D-07), not a per-desk business-day question. Binding to the desk's own anchor instead
would make the predicate answer "does this cross THIS DESK's business-day boundary" — true of every
overnight template on its own anchored desk, which is the opposite of what the split needs. At a
`00:00` desk anchor the two questions happen to coincide, which is exactly why this reduces to the
existing forward-interval refusal there and classifies no same-day template as overnight.

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
com.wfm.service.ShiftLibraryGenerationService :: int startCompare = candidate.spanStart().compareTo(currentBest.spanStart());
com.wfm.solver.AgentAssignmentDifficultyComparator :: int timeCompare = a.getTimeslot().getStartTime().compareTo(b.getTimeslot().getStartTime());
```

### Why each comparison is permitted

**(OVNT-02/plan 21-12) Seven sibling entries were converted, not amended, and no longer appear
here.** The family below used to carry nine entries, all justified on "both operands are START
times, so the end-of-day ambiguity does not apply." That reason is true about the end boundary and
silent about ordering (see heuristic limitation 4 above): two start times compared raw order by
CLOCK, not by position in the business day, so on a desk anchored away from midnight the
justification's own premise does not imply the comparison is correct. Seven of the nine produced
an operator-visible wrong answer on such a desk and were routed through `DayWindow`'s anchored
minute in plan 21-12, with their allowlist entries removed in the same change: the FTE upload's
min-start tracking (`FteUploadService`), the export's roster-cell earliest-start fallback
(`ScheduleExportService`), the preference report's start check and the drift-sign ternary's two
branches (`ScheduleOutputService`), the shift-library generator's expansion admission check
together with the clock-ordered reduction (`earliestStart`) it reads, converted in the same change
because the consumer and its source must move together (`ShiftLibraryGenerationService`), and the
preferred-start soft constraint (`ScheduleConstraintProvider`) — the one genuine scoring change in
the group, since it changes which assignments get penalised on a desk anchored away from midnight
(provably a no-op on every desk that exists today, since at a `00:00` anchor an anchored minute
equals the clock minute). The two entries remaining below are deliberately NOT converted:

- **`ShiftLibraryGenerationService`** — the greedy-cover tie-break between two already-selected
  candidates' `spanStart()` values (`candidate.spanStart()`/`currentBest.spanStart()`). Both
  operands are START positions; no end is involved. This orders by CLOCK, not by the candidate's
  position in the business day — on a desk anchored away from midnight the two orderings differ,
  so a different candidate could win a tie than the anchored order would pick. The consequence is
  ordering only: `isBetterCandidate` only ever breaks a tie between two candidates that already
  cover the identical uncovered-window set and the identical break-duration cost, so a different
  winner is a different but equally valid suggestion, never a wrong one. Left unconverted
  deliberately (plan 21-12, operator ruling): changing which candidate wins a tie alters which
  template the generator proposes, in a way no test in this repo asserts, which is out of scope for
  a phase scoped to overnight templates.
- **`AgentAssignmentDifficultyComparator`** — the start-time tie-break between two timeslots'
  `getStartTime()` in the solver's construction-heuristic placement order. Both are START
  positions; the comparator never orders by end. This orders by CLOCK, not by the timeslot's
  position in the business day — on a desk anchored away from midnight the two orderings differ,
  so the solver would place a different assignment first during construction. The consequence is
  ordering only: it changes the construction heuristic's placement SEQUENCE, never the legality or
  scoring of any resulting assignment. Left unconverted deliberately (plan 21-12, operator ruling):
  a move-ordering change alters the solver's search trajectory and therefore which schedule it
  lands on, in ways no test in this repo asserts — out of scope for a phase scoped to overnight
  templates.

### Permitted midnight anchors

```
com.wfm.util.FteSpreadsheetGenerator :: DayWindow window = DayWindow.anchoredAt(LocalTime.MIDNIGHT);
com.wfm.service.ShiftLibraryGenerationService :: DayWindow.anchoredAt(LocalTime.MIDNIGHT));
com.wfm.model.ShiftBandPair :: DayWindow.anchoredAt(LocalTime.MIDNIGHT));
com.wfm.service.ShiftTemplateService :: return !DayWindow.anchoredAt(LocalTime.MIDNIGHT).anchoredIsForwardWithinDay(startTime, endTime);
```

### Why each midnight anchor is permitted

- **`FteSpreadsheetGenerator.generate`** — a standalone report-generation utility with its own
  `main`, no `Desk` or tenant context reachable anywhere in the file (BDAY-04, plan 19-07). There is
  no desk to bind; `SOLV-01` does not own this one, since no join re-point will ever make a desk
  reachable here — it stays allowlisted for the life of this file's current shape.
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
- **`ShiftTemplateService.crossesCalendarMidnight`** (OVNT-05/D-07) — not an unreachability
  exception; this method deliberately asks "does this pair cross CALENDAR midnight," the fixed
  split key between a blocking overnight escape and a non-blocking same-day one, independent of
  this desk's own anchor (which the same class already binds elsewhere, as `window`). Binding to
  the desk's own anchor here would answer a different question ("does this cross THIS DESK's
  business-day boundary" — true of every overnight template on its own anchored desk) and break the
  split. Not removable by any future reachability fix, because reachability is not why it is here.
