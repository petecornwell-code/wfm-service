# Phase 23: Close gap REST-01/02/05 — RestSpan.gapMinutes with an overnight predecessor - Research

**Researched:** 2026-10-04
**Domain:** Interval arithmetic correctness (Java, in-repo, `DayWindow`/`RestSpan` family), Timefold Constraint Streams (no new stream shape needed), JUnit 5 test-gap closure
**Confidence:** HIGH — every claim below was checked by reading the actual file at HEAD this session (not by trusting the ROADMAP or audit text), and the numeric example in the ROADMAP/audit was independently re-derived by hand from the real code paths, not merely re-quoted.

There is no CONTEXT.md for this phase (operator skipped discuss-phase). This document supplies the
missing discussion and is the sole upstream input to the planner besides REQUIREMENTS.md/ROADMAP.md.

<user_constraints>
## User Constraints

No CONTEXT.md exists for this phase — there are no locked decisions, discretion grants, or deferred
ideas from `/gsd-discuss-phase` to carry forward. The planner's constraints are instead the ROADMAP's
phase description (verbatim in the dispatch prompt), REQUIREMENTS.md's REST-01/02/05 text, and the
decisions already locked in Phase 22 (CONTEXT.md D-01 through D-15, reproduced in `22-RESEARCH.md`)
that this phase must not relitigate — this phase corrects an implementation defect and a coverage gap
inside an already-shipped mechanism, it does not redesign that mechanism.

**Locked by inheritance from Phase 22 (do not relitigate):**
- D-08: one shared implementation discipline — `RestSpan.gapMinutes` is "the one and only gap-minutes
  implementation in this codebase" per its own javadoc. This phase's fix must preserve that property,
  not create a second correct-but-separate formula (see Finding 2 below for where that discipline has
  already quietly slipped).
- D-10/D-11: pre-horizon lookback is one business date back, ACCEPTED-only, one-directional. Unchanged
  by this phase.
- D-05: `minimumRestMinutes` is bounded below 24h, which is what makes a single `plusDays(1)` self-join
  step and a one-day lookback provably sufficient. Unchanged by this phase, and still holds after the
  fix (verified below — the fix does not need a second day of lookback).

**This phase's own scope, per the dispatch prompt:** fix `RestSpan.gapMinutes` so it correctly measures
the gap when the predecessor shift itself spans midnight, and close the test-coverage gap that let the
defect ship. Keep "real defect" and "coverage gap" as two separately-verifiable deliverables, per the
ROADMAP's explicit instruction (this project has a documented history of a review conflating the two).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REST-01 | Operator can set a minimum rest period between an agent's consecutive shifts, per desk | Unaffected by this phase's defect (the desk-level `minimumRestMinutes` CRUD path is pure config, verified untouched by the bug). Listed because the audit marks it "compromised by G-1" in the transitive sense that a feature whose enforcement silently fails is not really delivered — REST-01's own test (`DeskServiceMinimumRestTest`) needs no change. |
| REST-02 | Solver treats insufficient rest as a hard violation, measured between actual end/start instants — same-day and overnight | **The defect's primary target.** `[VERIFIED: src/main/java/com/wfm/model/RestSpan.java:120-130]` `gapMinutes` computes `remainingInPrevDay` from `window.anchoredEndMinute(prev.endTime())` alone, which is bounded to `(0, 1440]` and cannot distinguish a predecessor ending at `06:00` the same business day from one ending at `06:00` after a `22:00` start. See Finding 1 (root cause, hand-verified), Finding 2 (a second, independent occurrence of the same defect class), and Finding 3 (confirmed-clean family members). |
| REST-05 | Rest at the first and last day of the solving horizon has defined, tested behaviour | **Directly implicated.** `RestPredecessorService.resolveShiftSpans` (`:185`) builds the pre-horizon predecessor `RestSpan` directly from `AgentShiftAssignment`'s denormalised `shiftStartTime`/`shiftEndTime` — these can be overnight exactly as an in-horizon shift can, so the horizon-edge lookback path carries the identical defect once it feeds into `gapMinutes`. See Finding 4 for the exact reachability map. |
| OVNT-01 (transitive) | Operator can save an overnight shift template | Not itself broken by this defect — `OVNT-01` is about saving the template; this phase's defect is about what happens when the solver later measures rest against one. Listed transitively because the defect is only reachable once OVNT-01 makes an overnight template assignable. |
| OVNT-03 (transitive) | Day-off/PTO on starting business day blocks an overnight shift | Not itself broken. Listed transitively for the same reason as OVNT-01 — it is what makes the overnight shift that triggers the defect schedulable in the first place, not something this phase changes. |
</phase_requirements>

## Summary

This phase closes one confirmed, independently-reproduced production defect plus its coverage gap,
and this research surfaces a **second, independent occurrence of the same defect class** that the
ROADMAP's audit text does not mention — found by reading every consumer of the gap-measurement
arithmetic, not just `RestSpan.gapMinutes` itself. The fix direction the ROADMAP proposes is correct:
independently re-derived by hand (not merely re-quoted) against both a non-wrapping and a wrapping
predecessor, and against both the predecessor and successor position, it produces the exactly-right
gap in every case, including the existing passing tests' numbers. But patching `RestSpan.gapMinutes`
alone does **not** close every reachable instance of the bug — `SolverService.requireRestFeasibility`'s
SLOT-mode branch contains its own hand-rolled copy of the identical formula (`DayWindow.MINUTES_PER_DAY
- predecessorEndMinute + successorLatestStartMinute`, its own comment calling out the duplication:
*"Same structural shape as `RestSpan.gapMinutes` (D-08: the two must never drift)"*), and that copy
independently suffers the exact same wrap-blindness in its own pre-horizon branch
(`SolverService.java:2035`, `predecessorEndMinute = window.anchoredEndMinute(prior.endTime())`). This
is Finding 2 below, and it means the phase's "real defect" deliverable has two fix sites, not one.

**Primary recommendation:** Fix `RestSpan.gapMinutes` exactly as the ROADMAP's fix direction specifies
— replace the isolated `window.anchoredEndMinute(prev.endTime())` with the wrap-aware sum
`window.anchoredStartMinute(prev.startTime()) + window.anchoredDurationMinutes(prev.startTime(),
prev.endTime())` — and extract that sum into a new, well-named `DayWindow` instance method (this
research recommends `anchoredWrappedEndMinute(LocalTime start, LocalTime end)`) rather than inlining
the two-call expression at the call site. Then **replace** `SolverService.requireRestFeasibility`'s
duplicate inline formula at `:2035` with a call to the *same* new method, closing Finding 2 with the
same fix rather than a second, independently-reasoned one. This restores the "one shared
implementation" property D-08 already claims but which has in fact already drifted once. Add the
overnight-predecessor test cases the ROADMAP names to `MinimumRestShiftConstraintTest` and
`RestPredecessorServiceTest`, plus (not named in the ROADMAP, but required by the same standard) to
`MinimumRestSlotConstraintTest`, `RestFeasibilityRefusalTest`, and `RestWaiverDisclosureTest` — every
one of these five test classes independently exercises `RestSpan.gapMinutes` (directly or through the
constraint/service under test) and every one of them has the identical coverage gap: zero fixtures
with an overnight predecessor. See Finding 5 for the full enumeration, with exact line numbers, of
every existing fixture in every affected test class.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Gap-minutes arithmetic correctness | API / Backend (Timefold solver, in-process; and pre-solve validation, same process) | — | `RestSpan` is a pure Java record with no framework dependency; the fix is in-process arithmetic reached from three different execution contexts (constraint stream, pre-solve validator, output/reporting service) but is itself tier-agnostic business logic |
| In-solve hard-constraint enforcement | API / Backend (Timefold solver) | — | `ScheduleConstraintProvider.minimumRestShift`/`minimumRestSlot` — unchanged by this phase except for the arithmetic they call transitively |
| Pre-solve structural-impossibility refusal | API / Backend (`SolverService.requireRestFeasibility`) | — | Contains the sibling defect (Finding 2); same tier as the primary fix |
| Waived-violation disclosure | API / Backend (`ScheduleOutputService`) | Browser / Client (renders the DTO, does no arithmetic of its own) | Calls `RestSpan.gapMinutes` directly (`:1034`) — benefits from the primary fix with zero changes of its own |
| Test coverage | — | — | Entirely a backend JUnit concern; no UI surface is touched by this phase |

## Package Legitimacy Audit

**Not applicable.** This phase installs zero new external packages in any ecosystem. It is a pure
correctness fix plus test additions inside the existing Java/Gradle/JUnit 5/Timefold stack, all
already pinned (`ai.timefold.solver:timefold-solver-bom:1.16.0`, confirmed unchanged since Phase 22).
No `build.gradle` or `frontend/package.json` edit is implied by anything in this research.

## Finding 1 — The defect, independently re-derived (not merely re-quoted from the audit)

`[VERIFIED: src/main/java/com/wfm/model/RestSpan.java:120-130]`, read in full this session:

```java
public static int gapMinutes(RestSpan prev, RestSpan next) {
    if (!prev.dayStart().equals(next.dayStart())) {
        throw new IllegalArgumentException(/* ... */);
    }
    DayWindow window = DayWindow.anchoredAt(prev.dayStart());
    int remainingInPrevDay = DayWindow.MINUTES_PER_DAY - window.anchoredEndMinute(prev.endTime());
    int elapsedIntoNextDay = window.anchoredStartMinute(next.startTime());
    return remainingInPrevDay + elapsedIntoNextDay;
}
```

`[VERIFIED: src/main/java/com/wfm/util/DayWindow.java:347-367]`, read in full this session — the two
methods `gapMinutes` calls:

> `startMinuteFromDayStart`: *"Day-start-relative minute of a time in a START position, `[0, 1440)`"*
> (returns `Math.floorMod(startMinute(start) - startMinute(dayStart), MINUTES_PER_DAY)`).
>
> `endMinuteFromDayStart`: *"Day-start-relative minute of a time in an END position, `(0, 1440]`...
> mapping a result of zero to `MINUTES_PER_DAY` so a time equal to the anchor reads as the end of the
> business day"* (returns `raw == 0 ? MINUTES_PER_DAY : raw`, where `raw =
> Math.floorMod(startMinute(end) - startMinute(dayStart), MINUTES_PER_DAY)`).

Both functions are **pure functions of one `LocalTime` plus the anchor** — neither receives the
*other* end of the interval, so neither can know whether the interval it is one half of wrapped past
the anchor. `anchoredDurationMinutes` (`DayWindow.java:136-147`) gets this right precisely because it
receives both `start` and `end` together and can detect `raw < 0` (the wrap signal) itself:

```java
public int anchoredDurationMinutes(LocalTime start, LocalTime end) {
    int startOffset = startMinuteFromDayStart(dayStart, start);
    int endOffset = endMinuteFromDayStart(dayStart, end);
    int raw = endOffset - startOffset;
    if (raw > 0) return raw;
    if (raw == 0) return 0;
    return raw + MINUTES_PER_DAY;
}
```

**Hand-derivation of the bug, at a `00:00` anchor, `prev = 22:00–06:00`, `next` starts `07:00`** (the
audit's own cited numbers, independently re-walked here rather than trusted):
- `anchoredEndMinute(06:00)` = `endMinuteFromDayStart(00:00, 06:00)` = `floorMod(360-0,1440)` = `360`
  (not zero, so no `MINUTES_PER_DAY` substitution) → `360`.
- `remainingInPrevDay` = `1440 - 360` = `1080`.
- `elapsedIntoNextDay` = `anchoredStartMinute(07:00)` = `420`.
- `gapMinutes` returns `1080 + 420` = **`1500`**.
- **True rest:** the shift ends at real-world `06:00` the morning after a `22:00` start; the next
  shift starts `07:00` the same calendar morning. Real gap = **60 minutes**. The function overstates
  by `1500 - 60 = 1440` minutes — exactly one full day, which is the tell: the function is silently
  treating the wrapped end as if it had not wrapped.

This matches the audit's own measured numbers exactly (`1500` computed, `60` true) — independently
reproduced here by reading the source and the `DayWindow` contracts rather than re-stating the audit's
claim.

**Direction of error, confirmed:** unsafe. `remainingInPrevDay` is overstated whenever `prev` wraps,
which overstates the total gap, which means a real violation measures as compliant. The hard
constraint (`minimumRestShift`/`minimumRestSlot`, both `penalizeConfigurable`) produces **zero
penalty** on an illegal roster rather than a smaller-than-deserved one — this is not a magnitude bug,
it is a presence/absence bug at the boundary the audit's `07:00` example sits on. A desk whose minimum
is below the overstatement (anything under 24h, which by D-05 is *every* configurable value) can have
its hard constraint silently fail to fire at all.

## Finding 2 — A second, independent occurrence of the same defect class (not named in the ROADMAP/audit)

`[VERIFIED: src/main/java/com/wfm/service/SolverService.java:2008-2045]`, read in full this session.
`requireRestFeasibility`'s SLOT-mode branch (REST-03's pre-solve structural-impossibility refusal)
does **not** call `RestSpan.gapMinutes` — it computes the "best achievable gap" with its own inline
formula, justified by its own comment as deliberately a different, looser computation (SLOT mode has
no chosen shift to measure, only a window-and-contracted-hours bound):

```java
LocalDate dMinus1 = d.minusDays(1);
AgentDayConfig predecessorConfig = configsByDate.get(dMinus1);

int predecessorEndMinute;
if (predecessorConfig != null) {
    // in-horizon: a SYNTHETIC "earliest possible end" bound, safely within [windowStart, windowEnd]
    int requiredMinutesDMinus1 = predecessorConfig.expectedWorkSlots() * predecessorConfig.incrementMinutes();
    predecessorEndMinute = windowStartMinute + requiredMinutesDMinus1;   // <= windowEndMinute, no wrap risk
} else {
    RestSpan prior = priorSpanByAgent.get(agentId);
    if (prior == null) { continue; }
    // pre-horizon: the ACTUAL historical end -- and here is the defect:
    predecessorEndMinute = window.anchoredEndMinute(prior.endTime());   // <-- SAME BUG, independently
}

int successorLatestStartMinute = windowEndMinute - requiredMinutesD;

// Same structural shape as RestSpan.gapMinutes (D-08: the two must never drift)
int bestGap = DayWindow.MINUTES_PER_DAY - predecessorEndMinute + successorLatestStartMinute;
```

**Why this is reachable and realistic, not hypothetical:** `prior` is a `RestSpan` built by
`RestPredecessorService.resolveSlotSpans` → `RestSpan.ofSlots(...)` from the real accepted-schedule
slot assignments of the business date immediately before the solving period. `[VERIFIED: 21-RESEARCH.md
Pitfall 3 / SOLV-04 citation]` SLOT mode is already proven able to place an agent's assigned span
across midnight on today's data — so a historical SLOT-mode predecessor day's span genuinely can wrap,
and `prior.endTime()` genuinely can be the wrapped (e.g. `06:00`) value. The in-horizon sub-branch just
above it is **not** affected — `windowStartMinute + requiredMinutesDMinus1` is a synthetic estimate
provably bounded by `windowEndMinute <= 1440` (confirmed by the `requiredMinutesDMinus1 >
windowLengthMinutes` guard a few lines earlier), so it never needs wrap information. Only the
pre-horizon (`else`) sub-branch reads an actual historical end time through the same isolated-accessor
antipattern as Finding 1.

**Direction of error:** identical to Finding 1 and for the identical reason — `anchoredEndMinute`
in isolation understates a wrapped end's true offset, which overstates `bestGap`, which means
`requireRestFeasibility`'s SLOT branch can fail to refuse a genuinely structurally-impossible
agent-day at the horizon's first business date. This is REST-03/REST-05 territory specifically (the
pre-solve refusal at the horizon edge), not REST-02's in-solve constraint — **fixing `RestSpan.gapMinutes`
alone does not fix this call site**, because this call site never calls `RestSpan.gapMinutes` at all.

**The comment at `:2042-2044` is itself evidence of a discipline that has already quietly failed**:
it asserts "the two must never drift" while the two are, in fact, two separately-maintained
expressions of the same formula — one correct-by-construction in its own first branch, buggy in its
second. The honest fix is not to patch the buggy sub-expression in place a second time; it is to give
both `RestSpan.gapMinutes` and this branch exactly one place to get a wrapped end offset from. See
Recommendation below.

## Finding 3 — Confirmed-clean family members (checked, not assumed)

To avoid both under- and over-claiming, every other `RestSpan`-adjacent computation was read this
session and confirmed **not** to share the defect:

- `RestSpan.ofSlots` (`:76-96`) — orders slots by `window.anchoredStartMinute`/`anchoredEndMinute`
  called on each slot's *own* start/end independently, never combining one slot's start with
  another's end across a day boundary. No wrap-sensitive composition occurs here; confirmed safe.
- `RestSpan.gapMinutes`'s `elapsedIntoNextDay = window.anchoredStartMinute(next.startTime())`
  (the successor half) — confirmed safe per the ROADMAP's own reasoning, independently re-derived:
  `anchoredStartMinute` only needs to know where `next` starts relative to anchor, never how `next`
  ends or whether `next` itself wraps. This is correct regardless of whether `next` is itself an
  overnight shift.
- `SolverService.requireRestFeasibility`'s SHIFT-mode branch (`:1870-1953`) — calls
  `RestSpan.gapMinutes(predecessor, successor)` directly (`:1924`) for every candidate pair, including
  the pre-horizon case via `priorSpanCandidates(priorSpanByAgent.get(agentId))` (`:2107-2109`), which
  just wraps a `RestSpan` into a singleton list with no arithmetic of its own. This branch is **fixed
  for free** once `RestSpan.gapMinutes` itself is corrected — no separate code change needed here.
- `ScheduleOutputService.buildRestWaiverDisclosure`-equivalent (`:1034`) — calls `RestSpan.gapMinutes`
  directly, same free-fix property as above. The spans it builds (`:986-987` SHIFT descriptor,
  `:1000` SLOT `ofSlots`, `:1009-1010` pre-horizon) can all be overnight and all feed straight into
  the one function.
- `ScheduleConstraintProvider.minimumRestShift`/`minimumRestSlot` (`:1049-1176`) — both call
  `RestSpan.gapMinutes` exactly once per candidate pair via the shared `RestGapMatch` record
  (`:1073`, `:1166`); no arithmetic of their own. Both fixed for free.

**Net effect:** the fix has exactly two sites — `RestSpan.gapMinutes` itself (Finding 1) and
`SolverService.requireRestFeasibility`'s SLOT pre-horizon sub-branch (Finding 2) — and four call
sites that need no code change at all because they already delegate correctly (`minimumRestShift`,
`minimumRestSlot`, `requireRestFeasibility`'s SHIFT branch, `ScheduleOutputService`'s waiver
disclosure).

## Finding 4 — Full reachability map of `RestSpan.gapMinutes` and its factories

| Call site | File:line | Predecessor can be overnight? | How |
|---|---|---|---|
| `minimumRestShift` (in-horizon self-join) | `ScheduleConstraintProvider.java:1052,1073` | **Yes** | `RestSpan.ofShift` reads the assigned `ShiftBandPair`'s template, which OVNT-01 permits to span midnight; this span plays predecessor via the `spans.concat(...)` into `predecessorSpans` at `:1061` |
| `minimumRestShift` (pre-horizon lookback) | via `Schedule.priorRestSpans` (`ScheduleConstraintProvider.java:1061`), populated by `RestPredecessorService.resolveShiftSpans` (`:185`) | **Yes** | Built directly from `AgentShiftAssignment`'s denormalised `shiftStartTime`/`shiftEndTime` — no containment or wrap check anywhere in that construction |
| `minimumRestSlot` (in-horizon self-join) | `ScheduleConstraintProvider.java:1151,1166` | **Yes** | `RestSpan.ofSlots`'s first-slot-start-to-last-slot-end span can wrap (SOLV-04 precedent) |
| `minimumRestSlot` (pre-horizon lookback) | via `Schedule.priorRestSpans`, populated by `RestPredecessorService.resolveSlotSpans` (`:207`) | **Yes** | Same `RestSpan.ofSlots` factory, applied to the historical date's rows |
| `requireRestFeasibility` SHIFT branch | `SolverService.java:1924` | **Yes** (fixed for free by Finding 1's fix) | Candidate spans from `shiftCandidateSpans` (in-horizon) or `priorSpanCandidates` (pre-horizon), both eventually calling `RestSpan.gapMinutes` |
| `requireRestFeasibility` SLOT branch | `SolverService.java:2035,2045` | **Yes** (Finding 2 — needs its own fix) | Inline duplicate formula, not `RestSpan.gapMinutes` |
| `ScheduleOutputService` waiver disclosure | `ScheduleOutputService.java:1034` | **Yes** (fixed for free) | `inHorizonSpans`/`predecessorSpans` built from SHIFT descriptor, SLOT `ofSlots`, or `schedule.getPriorRestSpans()` |

**Conclusion:** the defect is reachable through every one of the four paths a rest gap can be
measured through in this codebase (in-horizon SHIFT, in-horizon SLOT, pre-horizon SHIFT, pre-horizon
SLOT), confirming the ROADMAP's "not only the historical-lookback path" framing and extending it: it
is *also* not only the SHIFT-mode path, and it is *also* present a second time outside
`RestSpan.gapMinutes` entirely (Finding 2).

## Finding 5 — Exact test-coverage gap, enumerated across every affected test class

The ROADMAP names two test classes. Reading all five classes that exercise this arithmetic (directly
or through the constraint/service under test) this session shows **all five** share the identical
gap — zero fixtures anywhere construct an overnight predecessor:

| Test class | Lines checked | Overnight fixtures found | Where the one overnight template (if any) sits |
|---|---|---|---|
| `MinimumRestShiftConstraintTest` | full file, 346 lines | None as predecessor | `:156-157` (`fifteenHundredAnchor_overlappingCalendarDates_gapComputedCorrectly`) has `next = pair(20:00, 05:00)` — **successor** position only |
| `MinimumRestSlotConstraintTest` | full file, 421 lines | None as predecessor | No overnight fixture at all — every `compliantDaySeats(...)` call uses a within-day clock range |
| `RestPredecessorServiceTest` | full file, 376 lines | None | Every `shiftRow`/`slotRow` fixture is non-wrapping (`14:00–22:00`, `6:00–14:00`, `9:00–17:00`, etc.) |
| `RestFeasibilityRefusalTest` | grepped, ~480 lines | None as predecessor | All fixtures non-wrapping; closest is `1:00–9:00`/`0:00–8:00` (early but not wrapping) |
| `RestWaiverDisclosureTest` | grepped, ~680 lines | None as predecessor | Same pattern — `12:00–20:00`, `4:00–9:00`, `1:00–9:00`, `0:00–8:00`, all non-wrapping |

This phase's test-coverage deliverable is therefore wider than the ROADMAP's two named classes.
`MinimumRestSlotConstraintTest`, `RestFeasibilityRefusalTest`, and `RestWaiverDisclosureTest` need an
overnight-predecessor case exactly as much as the two the ROADMAP names — and
`RestFeasibilityRefusalTest` specifically needs a case targeting Finding 2's SLOT pre-horizon
sub-branch (a `RestSpan priorSpan` built with a wrapping `start`/`end`, feeding the `else` branch at
`:2025-2036`), which is the only place that defect is reachable in a unit test at all.

## Architecture Patterns

### Pattern 1: Extract the corrected formula as a named `DayWindow` method, not an inlined expression

**What:** Add `anchoredWrappedEndMinute(LocalTime start, LocalTime end)` to `DayWindow`, returning
`anchoredStartMinute(start) + anchoredDurationMinutes(start, end)` — the exact sum this research
independently verified collapses onto `anchoredEndMinute(end)` in the non-wrapping case (so every
existing passing test's number is unchanged) and onto `anchoredEndMinute(end) + MINUTES_PER_DAY` in
the wrapping case (so the previously-lost day is restored).

**Why a new named method, not an inlined two-call expression at each site:** Finding 2 is direct
proof that inlining this exact composition a second time, "by analogy," already happened once and
drifted into a bug. A named method is also what makes a structural guard possible at all (Pattern 2) —
a guard can assert "every file in the rest-gap family calls `anchoredWrappedEndMinute`, never
`anchoredEndMinute` directly on a predecessor/historical end," which it cannot assert against two
independently-spelled four-line expressions.

**Verified collapse check, worked by hand:**
- Non-wrapping, anchor `00:00`, `prev = 14:00–22:00`: `anchoredStartMinute(14:00)=840`;
  `anchoredDurationMinutes(14:00,22:00)`: `startOffset=840`, `endOffset=anchoredEndMinute(22:00)=1320`,
  `raw=480>0` → `480`. Sum `=840+480=1320` = `anchoredEndMinute(22:00)` exactly. **Matches today's
  value — zero regression risk for every existing non-wrapping test.**
- Wrapping, anchor `00:00`, `prev = 22:00–06:00`: `anchoredStartMinute(22:00)=1320`;
  `anchoredDurationMinutes(22:00,06:00)`: `startOffset=1320`, `endOffset=anchoredEndMinute(06:00)=360`,
  `raw=360-1320=-960<0` → `-960+1440=480`. Sum `=1320+480=1800`. Then
  `remainingInPrevDay = 1440 - 1800 = -360`; `gapMinutes = -360 + anchoredStartMinute(07:00)=420`
  → **`60`** — the correct answer, matching the audit's expected value exactly.

### Pattern 2: A new, narrowly-scoped structural guard — honest about what it can and cannot catch

**What this research recommends, and why it is NOT a drop-in extension of
`MidnightTimeArithmeticGuardTest`:** that guard's `RAW_ARITHMETIC_TOKENS`/`COMPARISON_TOKENS` scan
forbids specific *tokens* (`Duration.between(`, `.isAfter(`, etc.) anywhere outside an allowlist. The
defect in this phase is **not** a forbidden token — `RestSpan.gapMinutes` already uses only sanctioned
`DayWindow` anchored accessors. The bug is a *correct-primitive, wrong-composition* error: calling
`anchoredEndMinute(x.endTime())` alone is legitimate in dozens of places in this codebase where only
one time, within one business day, is being read (e.g. `RestSpan.ofSlots`'s per-slot ordering,
Finding 3). A blanket token-forbid on `anchoredEndMinute(` would be over-broad and would immediately
demand a large, noisy allowlist — the exact "guard becomes decoration" failure mode
`MidnightTimeArithmeticGuardTest`'s own javadoc warns against.

**What a guard CAN usefully do here, scoped narrowly:** forbid the specific sub-expression shape
`MINUTES_PER_DAY - <anchored end accessor call>` (the literal "remaining in prev day" idiom) appearing
anywhere **outside** `DayWindow.anchoredWrappedEndMinute`'s own body and its two sanctioned callers
(`RestSpan.gapMinutes`, `SolverService.requireRestFeasibility`'s SLOT branch after the fix). This is a
textual scan in the same family as the existing guard (walk `src/main/java`, strip comments, match a
token sequence, assert set-equality against an allowlist file) but keyed to a *pattern specific to this
defect class* rather than a single banned token. Recommend naming it for what it catches — e.g. a new
`src/test/resources/rest-gap-arithmetic-guard.md` plus a sibling `RestGapArithmeticGuardTest`, mirroring
`rest-waiver-predicate-guard.md`/`RestWaiverPredicateGuardTest`'s existing shape (same project, same
D-08 "single implementation" idiom, already proven twice).

**Be honest about the limit:** like the existing guard's own javadoc admits, this cannot see
arithmetic hidden behind a third helper method that itself contains none of the scanned tokens. The
real backstop is Finding 5's test coverage — a genuine overnight-predecessor fixture in all five
affected test classes is what actually proves the fix, the guard is a tripwire against *regression*
after the fix lands, not a substitute for the fixture-level proof.

### Pattern 3: D-05's sub-24h bound is unaffected by the fix — single `plusDays(1)` step stays sufficient

**What:** Confirm the fix does not require widening the self-join from one day to two, or the
lookback from one business date to two.

**Why it holds:** D-05 bounds `minimumRestMinutes` strictly below `1440`. The corrected
`gapMinutes` can now return a value as low as `-ongoing overlap` (if two shifts the solver considers
genuinely overlap, which other constraints already forbid) up to just under `2 * MINUTES_PER_DAY` in
the pathological case of a maximally-wrapping 16h-capped predecessor paired with a successor starting
at its own anchor — still comfortably inside the range the existing `.filter(match -> match.gapMinutes()
< match.cfg().minimumRestMinutes())` predicate handles correctly with no second join step, because the
join's correctness depends on *which pairs are compared* (one business day apart, unchanged by this
fix), not on the *magnitude* `gapMinutes` returns once compared.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---|---|---|---|
| A wrap-aware "true end offset" for a span that may cross the anchor | A second inlined `anchoredStartMinute(x) + anchoredDurationMinutes(x,y)` expression at the `SolverService` call site, "fixed in place" | The same new `DayWindow.anchoredWrappedEndMinute` both `RestSpan.gapMinutes` and `SolverService.requireRestFeasibility`'s SLOT branch call | Finding 2 is direct, in-this-codebase proof that inlining this composition twice already produced a second bug from one correct idea — the fix must not repeat the mistake it is closing |
| A second day-start-relative "is this later shift actually one calendar day later" check | A new `LocalDate`/`Duration` based sanity check bolted onto `gapMinutes` | Nothing new — the existing `businessDate().plusDays(1)` join key (verified at `ScheduleConstraintProvider.java:1068,1163`) already guarantees `prev`/`next` are exactly one business day apart before `gapMinutes` is ever called; the fix only needs to get the within-pair arithmetic right | D-05's bound is what makes the one-day join sufficient; re-deriving adjacency inside `gapMinutes` itself would duplicate a guarantee the caller already provides |

**Key insight:** every piece of this fix is recomposing primitives that already exist and are already
individually correct (`anchoredStartMinute`, `anchoredDurationMinutes`) — the defect and its sibling
are both failures of *composition*, not missing primitives. The risk in this phase is not inventing
new arithmetic; it is making sure the one correct composition is written exactly once and reused,
which is the precise discipline that already slipped once (Finding 2) under a project that explicitly
values it (D-08).

## Common Pitfalls

### Pitfall 1: Fixing `RestSpan.gapMinutes` and declaring the defect closed

**What goes wrong:** A plan that patches only `RestSpan.java:127` and adds the two ROADMAP-named test
cases will pass every test it writes — and still ship Finding 2's sibling defect in
`SolverService.requireRestFeasibility`'s SLOT branch, completely unexercised, because no existing test
reaches that specific sub-branch with a wrapping `prior` span.

**Why it happens:** The ROADMAP's own text, and the audit it's drawn from, examined `RestSpan.gapMinutes`
specifically and did not trace every caller of the *pattern*, only every caller of the *function*.

**How to avoid:** Treat Finding 2 as in-scope for this phase's "real defect" deliverable — it is the
same requirement (REST-02/REST-05), the same root cause, and the same unsafe error direction, just a
second call site. Add a `RestFeasibilityRefusalTest` case with a wrapping `priorSpan` (e.g.
`LocalTime.of(22,0)`→`LocalTime.of(6,0)`) feeding the `else` branch at `SolverService.java:2025-2036`.

**Warning signs:** A plan that lists only `RestSpan.java`, `MinimumRestShiftConstraintTest.java`, and
`RestPredecessorServiceTest.java` as touched files.

### Pitfall 2: Treating the fix as "add a wrap branch," when the right fix is "stop reading one end of an
interval in isolation"

**What goes wrong:** A tempting minimal patch is to special-case `anchoredEndMinute(prev.endTime())`
when it is "small" (e.g. `< anchoredStartMinute(prev.startTime())`, implying a wrap) and add
`MINUTES_PER_DAY` in that case. This reproduces the correct number for the cases tested but is a third,
independently-reasoned formula living beside the two that already exist — worsening, not fixing, the
"single shared implementation" drift Finding 2 already demonstrates.

**Why it happens:** It looks like a smaller diff than introducing a new `DayWindow` method and
re-pointing two call sites.

**How to avoid:** Use `anchoredDurationMinutes` (already correct, already tested exhaustively per
`DayWindowTest`) as the wrap-detection mechanism, exactly as this research's Pattern 1 and the
ROADMAP's own fix direction specify — do not hand-roll a new wrap test.

**Warning signs:** Any new `if` comparing two minute-offsets to decide "did this wrap" that does not
go through `anchoredDurationMinutes`'s existing `raw < 0` branch.

### Pitfall 3: Assuming the successor side needs the identical fix "for symmetry"

**What goes wrong:** Applying the same wrap-aware composition to `next.startTime()` as well — e.g.
computing `elapsedIntoNextDay` from some analogous "wrapped start" formula — on the theory that if the
predecessor needed fixing, the successor side must too.

**Why it happens:** Surface-level pattern-matching on "there are two sides to this function."

**How to avoid:** The ROADMAP's own reasoning, independently re-verified in Finding 3, is specific:
`anchoredStartMinute(next.startTime())` needs no wrap information because the gap is measured
*forward* from `next`'s start — it never needs to know where `next` ends. Changing this side would be
unnecessary work and risks introducing a real regression into code that is not broken. Confirm with a
test where `next` itself is overnight (e.g. `next = 20:00–05:00`, mirroring the existing
`:156-157` fixture) paired with both a wrapping and non-wrapping `prev`, asserting the successor side's
contribution to the gap is unchanged by the fix.

### Pitfall 4: Forgetting that `RestPredecessorService.resolveShiftSpans` bypasses `RestSpan.ofShift`
on purpose — don't "simplify" it into calling the factory

**What goes wrong:** Noticing that `resolveShiftSpans` builds a `RestSpan` via the raw constructor
(`:185`) rather than `RestSpan.ofShift`, and "cleaning this up" to call the factory instead.

**Why it happens:** `RestSpan.ofShift` looks like the obviously-intended factory, and the raw
constructor call looks like an oversight.

**How to avoid:** `[VERIFIED: src/main/java/com/wfm/service/RestPredecessorService.java:179-184]` the
comment is explicit and load-bearing: *"Do NOT call `RestSpan.ofShift` here: that factory reads the
live planning variable's template, which is null on a persisted row. The denormalised
`shiftStartTime`/`shiftEndTime` columns are the authoritative source here precisely because a later
`updateShiftTemplate` edit must not be able to rewrite what history says the agent actually worked."*
This is unrelated to the defect this phase fixes and must not be touched.

## Code Examples

### The recommended fix, as a diff-shaped sketch (not yet in the codebase — composed from verified primitives)

```java
// NEW — src/main/java/com/wfm/util/DayWindow.java, alongside anchoredEndMinute
/**
 * The day-start-relative minute an interval's END instant truly falls at, allowing the result to
 * exceed {@link #MINUTES_PER_DAY} when the interval wraps past the anchor -- unlike
 * {@link #anchoredEndMinute}, which only ever returns a value in {@code (0, 1440]} and therefore
 * cannot distinguish "ends within this business day" from "ends the calendar day after a wrap."
 * Equal to {@link #anchoredEndMinute(LocalTime)} exactly whenever the interval does not wrap
 * (verified exhaustively by the non-wrapping collapse case in 23-RESEARCH.md Pattern 1).
 */
public int anchoredWrappedEndMinute(LocalTime start, LocalTime end) {
    return anchoredStartMinute(start) + anchoredDurationMinutes(start, end);
}
```

```java
// CHANGED — src/main/java/com/wfm/model/RestSpan.java:120-130
public static int gapMinutes(RestSpan prev, RestSpan next) {
    if (!prev.dayStart().equals(next.dayStart())) {
        throw new IllegalArgumentException(/* unchanged */);
    }
    DayWindow window = DayWindow.anchoredAt(prev.dayStart());
    int remainingInPrevDay = DayWindow.MINUTES_PER_DAY
            - window.anchoredWrappedEndMinute(prev.startTime(), prev.endTime());   // <-- changed line
    int elapsedIntoNextDay = window.anchoredStartMinute(next.startTime());          // unchanged
    return remainingInPrevDay + elapsedIntoNextDay;
}
```

```java
// CHANGED — src/main/java/com/wfm/service/SolverService.java, requireRestFeasibility SLOT branch, ~:2035
} else {
    RestSpan prior = priorSpanByAgent.get(agentId);
    if (prior == null) { continue; }
    predecessorEndMinute = window.anchoredWrappedEndMinute(prior.startTime(), prior.endTime()); // <-- changed line, was anchoredEndMinute(prior.endTime())
}
```

Note `bestGap = DayWindow.MINUTES_PER_DAY - predecessorEndMinute + successorLatestStartMinute` can now
go negative when `predecessorEndMinute > MINUTES_PER_DAY` (a wrapped predecessor) — this is correct and
requires no change to the surrounding `if (bestGap < minimumRestMinutes)` check, since a very negative
`bestGap` correctly and immediately fails that comparison (reports the agent-day as impossible, which
is the intended outcome when the predecessor's real end already consumes rest time into the next
business day).

### Existing test fixture shape to extend (SHIFT mode) — `MinimumRestShiftConstraintTest.java:52-93`

```java
// Source: src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java:52-93, read in full
private static ShiftTemplate template(LocalTime start, LocalTime end) { /* ... */ }
private static ShiftBandPair pair(LocalTime start, LocalTime end) {
    return new ShiftBandPair(template(start, end), null);
}
private static AgentShiftAssignment shiftRow(Agent agent, LocalDate date, ShiftBandPair pair, LocalTime dayStart) {
    /* builds a row with its own AgentDayConfig carrying dayStart */
}
```

A new overnight-predecessor case follows this exact existing idiom, e.g.:

```java
@Test
@DisplayName("an overnight predecessor (22:00-06:00) measures the true 60-minute gap against a 07:00 successor, not 1500")
void overnightPredecessor_trueGapMeasuredCorrectly() {
    Agent a = agent();
    AgentShiftAssignment prev = shiftRow(a, D_MINUS_1, pair(LocalTime.of(22, 0), LocalTime.of(6, 0)));
    AgentShiftAssignment next = shiftRow(a, D, pair(LocalTime.of(7, 0), LocalTime.of(15, 0)));

    verifier.verifyThat(ScheduleConstraintProvider::minimumRestShift)
            .given(prev, next, scheduleConfig(SchedulingMode.SHIFT, 660))
            .penalizesBy(600); // 660 - 60
}
```

Plus a direct `RestSpan.gapMinutes` unit case mirroring `:254-262`'s existing shape:

```java
@Test
@DisplayName("RestSpan.gapMinutes with an overnight predecessor measures the true gap, not the pre-fix 1500")
void gapMinutes_overnightPredecessor_matchesTrueGap() {
    UUID agentId = UUID.randomUUID();
    RestSpan prev = new RestSpan(agentId, D_MINUS_1, LocalTime.of(22, 0), LocalTime.of(6, 0), LocalTime.MIDNIGHT);
    RestSpan next = new RestSpan(agentId, D, LocalTime.of(7, 0), LocalTime.of(15, 0), LocalTime.MIDNIGHT);

    assertThat(RestSpan.gapMinutes(prev, next)).isEqualTo(60);
}
```

### Existing test fixture shape to extend (SLOT mode, pre-horizon — Finding 2's own proof) — `RestFeasibilityRefusalTest.java:419-446`

```java
// Source: src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java:419-446, read via grep this
// session — the exact shape of the existing "pre-horizon edge" SLOT test this phase's new case
// should sit beside:
RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(10, 0), LocalTime.of(19, 0),
        LocalTime.MIDNIGHT);
// ... feeds the SLOT branch's else sub-branch (SolverService.java:2025-2036) ...
```

A new case constructs `priorSpan` with `start=22:00, end=06:00` (wrapping) instead, and a successor
`AgentDayConfig`/window shape chosen so the TRUE best-case gap is below the configured minimum (proving
Finding 2's refusal now fires) where the pre-fix code would have computed a falsely-large `bestGap`
and NOT refused.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|---|---|---|---|
| `DayWindow.durationMinutes` threw on any backward interval (no overnight shift representable at all) | `DayWindow.anchoredDurationMinutes` wraps forward instead of throwing | Phase 19 (pre-existing, unchanged by this phase) | This is the primitive this phase's fix reuses — already correct, already exhaustively tested, not touched |
| `RestSpan.gapMinutes` reads `prev`'s end in isolation via `anchoredEndMinute` | (this phase) reads `prev`'s wrapped end via the new `anchoredWrappedEndMinute(start, end)` | This phase | Closes REST-02's unsafe under-fire on an overnight predecessor |
| `SolverService.requireRestFeasibility`'s SLOT branch independently re-derives the same formula inline | (this phase) calls the same `anchoredWrappedEndMinute` its sibling now uses | This phase | Closes Finding 2, restores the "one shared implementation" property D-08 already claims |

**Deprecated/outdated:** None — this phase adds one new `DayWindow` method; it does not retire any
existing public surface.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|---|---|---|
| A1 | `anchoredWrappedEndMinute` is the right name/home for the extracted primitive, as opposed to e.g. a `RestSpan`-local private helper or a method on `RestSpan` itself rather than `DayWindow` | Pattern 1 | LOW — purely a naming/placement choice; `DayWindow` is the established single home for every other anchored accessor (`anchoredStartMinute`, `anchoredEndMinute`, `anchoredDurationMinutes` all live there), so placing the new one elsewhere would be the surprising choice, not this recommendation. Easily corrected in review with no behavioural consequence. |
| A2 | A new, narrowly-scoped structural guard (Pattern 2) is worth building in this phase rather than relying solely on the five test classes' fixtures | Pattern 2 | MEDIUM — this is a genuine scope judgment: the guard adds a new test-infrastructure file and its own registry/allowlist file (mirroring `rest-waiver-predicate-guard.md`), which is real but bounded additional work. If the planner judges the fixture-level proof in Finding 5 sufficient on its own (matching the ROADMAP's literal ask, which names only test additions, not a new guard), dropping the guard is defensible — but then Finding 2's "the comment already claims a discipline that drifted once" risk has no mechanical tripwire against a third occurrence, only code review. |
| A3 | `requiredMinutesDMinus1 > windowLengthMinutes` at `SolverService.java:2015-2021` is a sufficient guarantee that the in-horizon sub-branch's `predecessorEndMinute` never itself needs wrap-awareness | Finding 2 | LOW — directly re-derived from the guard's own bound (`predecessorEndMinute = windowStartMinute + requiredMinutesDMinus1 <= windowStartMinute + windowLengthMinutes = windowEndMinute <= 1440` by construction), not merely assumed. |

**If this table is empty:** N/A — two assumptions recorded above, both low-to-medium risk with an
explicit mitigation path.

## Open Questions

1. **Is Finding 2 in scope for this phase, or a new backlog item?**
   - What we know: it is the identical root cause, the identical requirement (REST-02/REST-05), the
     identical unsafe error direction, and reachable with the exact same kind of fixture this phase is
     already building. The ROADMAP's own framing ("real defect AND, separately, a coverage gap...
     recorded distinctly because this project has a documented history of a review conflating the
     two") is explicitly about keeping categories distinct, not about narrowing scope to one file.
   - What's unclear: the ROADMAP's dispatch prompt names only `RestSpan.java` and two test classes
     verbatim; it does not mention `SolverService.java` at all, which may mean the auditor who wrote
     the gap simply did not trace this far, or may mean a deliberate decision to scope this phase to
     the named file and file Finding 2 as a follow-up.
   - Recommendation: treat it as in-scope. It is cheap to fix (one call-site swap, reusing the same
     new primitive) and expensive to leave (a second silent-under-fire pre-solve refusal on exactly
     the requirement this phase exists to close, discovered by a future audit rather than this one).
     If the planner disagrees, it must be an explicit, named descope decision, not a silent omission.

2. **Does the new structural guard (Pattern 2) belong in this phase or is the fixture-level proof enough?**
   - What we know: the five-test-class fixture gap (Finding 5) is the ROADMAP's literal ask and is
     sufficient to prove the fix correct. The guard is this research's own addition, aimed at
     preventing a *third* occurrence of the same composition error somewhere not yet written.
   - What's unclear: whether a new test-infrastructure file (allowlist + scanner) is proportionate for
     a two-call-site fix, versus being appropriate scope creep for a gap-closure phase.
   - Recommendation: Claude's discretion at plan time, flagged for the planner to decide explicitly
     rather than default silently either way — this research's assumption log (A2) carries the
     reasoning either direction.

## Validation Architecture

### Test Framework
| Property | Value |
|---|---|
| Framework | JUnit 5 + Gradle (`./gradlew test`) — unchanged from Phase 22 |
| Config file | `build.gradle` (no separate test config); Postgres-backed tests opt in via `PostgresBackedTest` (Testcontainers) — not needed for this phase, every affected test class is a pure in-memory/`ConstraintVerifier` unit test |
| Quick run command | `./gradlew test --tests "com.wfm.model.RestSpan*"` is not a real class (RestSpan has no own test class today — confirmed by the `find` in Finding 5's table); use the specific touched class, e.g. `./gradlew test --tests "com.wfm.solver.MinimumRestShiftConstraintTest"` |
| Full suite command | `./gradlew test` (per `.planning/config.json`'s `test_command`; `test_gate_timeout: 3600`) |
| Known project gotcha | A filtered `--tests` run **deletes every other class's JUnit XML** — never read a suite-wide aggregate immediately after a targeted run; re-run the full suite first. A stale Gradle daemon roughly doubles suite time — `./gradlew --stop` before a full-suite run. |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|---|---|---|---|---|
| REST-02 | `RestSpan.gapMinutes` correctly measures the gap when the predecessor is overnight, SHIFT mode | unit (`ConstraintVerifier`) + direct unit | `./gradlew test --tests "com.wfm.solver.MinimumRestShiftConstraintTest"` | ✅ existing class, new methods — Wave 0 |
| REST-02 | Same, SLOT mode | unit (`ConstraintVerifier`) | `./gradlew test --tests "com.wfm.solver.MinimumRestSlotConstraintTest"` | ✅ existing class, new methods — Wave 0 |
| REST-05 | Pre-horizon lookback correctly builds and measures an overnight predecessor, SHIFT mode | unit | `./gradlew test --tests "com.wfm.service.RestPredecessorServiceTest"` | ✅ existing class, new methods — Wave 0 |
| REST-05 | Pre-solve refusal fires correctly against an overnight pre-horizon predecessor, SHIFT mode | unit | `./gradlew test --tests "com.wfm.service.RestFeasibilityRefusalTest"` | ✅ existing class, new methods — Wave 0 |
| REST-05 (Finding 2) | Pre-solve refusal fires correctly against an overnight pre-horizon predecessor, SLOT mode | unit | `./gradlew test --tests "com.wfm.service.RestFeasibilityRefusalTest"` | ✅ existing class, new method — Wave 0, **the one case that proves Finding 2's fix** |
| REST-07 (regression safety, not a new requirement) | Waiver disclosure still correctly computes the gap when the span is overnight | unit | `./gradlew test --tests "com.wfm.service.RestWaiverDisclosureTest"` | ✅ existing class, new method — Wave 0 |
| (new, if Pattern 2 is adopted) | Structural guard forbids the isolated-end-accessor composition outside the sanctioned callers | unit (source scan, no Spring context) | `./gradlew test --tests "com.wfm.service.RestGapArithmeticGuardTest"` | ❌ new class — Wave 0, only if Open Question 2 resolves "yes" |

### Sampling Rate
- **Per task commit:** targeted `--tests` run against the touched class(es)
- **Per wave merge:** `./gradlew test` (full suite) — never read the aggregate immediately after a
  targeted run (project gotcha above)
- **Phase gate:** full suite green before `/gsd-verify-work`, plus `MidnightTimeArithmeticGuardTest`
  and (if built) the new guard from Pattern 2 explicitly passing

### Wave 0 Gaps
- [ ] None of the five test classes need new fixtures infrastructure or helper methods — every one
  already has a `shiftRow`/`slotRow`/`compliantDaySeats`/`priorSpan`-shaped builder this phase's new
  cases can call directly (see Finding 5's table and Code Examples). This phase needs **zero** new
  test scaffolding for the fixture-level proof.
- [ ] If Pattern 2's guard is adopted: a new `src/test/resources/rest-gap-arithmetic-guard.md`
  registry file (mirroring `rest-waiver-predicate-guard.md`'s exact structure) and its scanner test —
  genuinely new infrastructure, not reuse.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---|---|---|
| V2 Authentication | no | Unchanged |
| V3 Session Management | no | Unchanged |
| V4 Access Control | no | This phase touches no repository query, no tenant/desk scoping, no new entity |
| V5 Input Validation | no | No new input surface — the fix is internal arithmetic on already-validated, already-stored times |
| V6 Cryptography | no | Not applicable |

This phase is a pure correctness fix inside the solver/pre-solve arithmetic with no new attacker-reachable
surface, no new persisted data, and no new endpoint. The "threat" this phase closes is a correctness
one (an illegal roster scoring as legal), not a security one in the ASVS sense — already correctly
classified by the ROADMAP/audit as a functional defect, not a vulnerability.

## Sources

### Primary (HIGH confidence — read in full or via targeted grep this session, this repo, HEAD)
- `src/main/java/com/wfm/model/RestSpan.java` — full file read
- `src/main/java/com/wfm/util/DayWindow.java` — full file read (439 lines)
- `src/main/java/com/wfm/service/RestPredecessorService.java` — full file read
- `src/main/java/com/wfm/service/SolverService.java` — targeted reads: `requireRestFeasibility`
  (`:1846-2070`) and its helpers `shiftCandidateSpans`/`priorSpanCandidates`/`indexPriorSpansByAgent`/
  `isAgentDayWaived` (`:2075-2140`)
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — targeted reads: `minimumRestShift`/
  `minimumRestSlot`/`RestGapMatch` (`:995-1176`)
- `src/main/java/com/wfm/service/ScheduleOutputService.java` — targeted read: the waiver-disclosure
  span-building and `gapMinutes` call (`:955-1045`)
- `src/main/java/com/wfm/model/Schedule.java` — targeted grep confirming `priorRestSpans`/
  `agentRestWaivers` `@ProblemFactCollectionProperty` registration
- `src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java` — full file read (346 lines)
- `src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java` — full file read (421 lines)
- `src/test/java/com/wfm/service/RestPredecessorServiceTest.java` — full file read (376 lines)
- `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` — targeted read of its
  mechanism, tokens, and allowlist-parsing discipline
- `src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java`,
  `src/test/java/com/wfm/service/RestWaiverDisclosureTest.java` — targeted grep of all fixture
  construction lines
- `src/test/resources/rest-waiver-predicate-guard.md`, `bday-join-guard.md`,
  `schedule-summary-construction-site.md`, `midnight-boundary-scenarios.md` — read/grepped to confirm
  no existing registry already covers this defect class
- `.planning/v1.5-MILESTONE-AUDIT.md` — full G-1/F-1 section read, cross-checked against the live
  source rather than trusted as-is (every number independently re-derived in Finding 1)
- `.planning/REQUIREMENTS.md`, `.planning/STATE.md` — read for phase context and locked v1.5 decisions
- `.planning/phases/22-minimum-rest/22-RESEARCH.md`, `22-VALIDATION.md` — read in full for Phase 22's
  locked decisions and test-infrastructure shape
- `.planning/phases/21-overnight-shift-templates/21-RESEARCH.md` — read substantially (first 727 of
  938 lines) for overnight-template semantics and the SOLV-04 SLOT-overnight precedent
- `.planning/config.json` — confirmed `nyquist_validation: true`, `test_command: "./gradlew test"`,
  all external search providers disabled (consistent with this being a pure in-repo research pass)

### Secondary (MEDIUM confidence)
- None — every claim in this document was checked against the actual repository at HEAD this session,
  not inferred from a secondary description.

### Tertiary (LOW confidence)
- None — no WebSearch or external-source claims were needed or made; `.planning/config.json` confirms
  `brave_search`/`exa_search`/`firecrawl` are all disabled for this project, and this phase's domain is
  entirely in-repo arithmetic with a already-pinned, unchanged dependency set.

## Metadata

**Confidence breakdown:**
- Root cause (Finding 1): HIGH — independently re-derived by hand against the real source, matches
  the audit's measured numbers exactly, not merely re-quoted
- Fix correctness (Pattern 1): HIGH — verified by hand for both the wrapping and non-wrapping case,
  and for both predecessor and successor position; the non-wrapping collapse check is what guarantees
  zero regression on every currently-passing test
- Sibling defect (Finding 2): HIGH — found by reading `SolverService.requireRestFeasibility` in full,
  not inferred; the comment at `:2042-2044` is independent textual corroboration that the duplication
  was known and accepted as a risk at write time
- Test-coverage gap (Finding 5): HIGH — every one of the five affected test classes was read in full
  or exhaustively grepped this session; the "zero overnight predecessor fixtures" claim is a complete
  enumeration, not a sample
- Structural-guard feasibility (Pattern 2): MEDIUM — the recommendation is sound but is this research's
  own addition, not drawn from an existing precedent that exactly fits (the closest precedent,
  `MidnightTimeArithmeticGuardTest`, catches a different class of error, as explained)

**Research date:** 2026-10-04
**Valid until:** Next change to `DayWindow`, `RestSpan`, or `SolverService.requireRestFeasibility` —
this is an internal-codebase research doc about a specific, dated defect, not time-decaying external
documentation.
