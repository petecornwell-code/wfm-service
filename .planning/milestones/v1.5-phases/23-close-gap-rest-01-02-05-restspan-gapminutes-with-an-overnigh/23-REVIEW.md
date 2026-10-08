---
phase: 23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh
reviewed: 2026-10-04T00:00:00Z
depth: standard
files_reviewed: 12
files_reviewed_list:
  - src/main/java/com/wfm/model/RestSpan.java
  - src/main/java/com/wfm/service/SolverService.java
  - src/main/java/com/wfm/util/DayWindow.java
  - src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java
  - src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java
  - src/test/java/com/wfm/service/RestPredecessorServiceTest.java
  - src/test/java/com/wfm/service/RestWaiverDisclosureTest.java
  - src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java
  - src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java
  - src/test/java/com/wfm/solver/RestHorizonEdgeTest.java
  - src/test/java/com/wfm/util/DayWindowTest.java
  - src/test/resources/rest-gap-arithmetic-guard.md
findings:
  critical: 1
  warning: 2
  info: 2
  total: 5
status: issues_found
---

# Phase 23: Code Review Report

**Reviewed:** 2026-10-04T00:00:00Z
**Depth:** standard
**Files Reviewed:** 12
**Status:** issues_found

## Summary

The phase's stated fix — `DayWindow.anchoredWrappedEndMinute` and its two call sites
(`RestSpan.gapMinutes`, `SolverService.requireRestFeasibility`'s SLOT pre-horizon branch) — is
arithmetically correct. I traced the wrap case (`22:00`–`06:00` predecessor against a `07:00`
successor), the non-wrap case, and the `end == anchor` boundary case by hand against
`anchoredStartMinute`/`anchoredDurationMinutes`/`anchoredWrappedEndMinute`'s actual implementations
in `DayWindow.java`, and all three match the javadoc's worked examples and the test fixtures in
`DayWindowTest`, `MinimumRestShiftConstraintTest`, `MinimumRestSlotConstraintTest`, and
`RestHorizonEdgeTest`. The one-line diff against the pre-phase tree (`a6c9210`) is exactly the
claimed single-site correction; no residual wrap-blind arithmetic or sign error was found at either
call site.

The structural guard (`RestGapArithmeticGuardTest` + `rest-gap-arithmetic-guard.md`) does enforce
true set equality (`containsExactlyInAnyOrderElementsOf`, both directions checked, with its own
"deliberately broken allowlist" negative test) rather than a weaker containment check — that part of
the design holds up. However, tracing through what its textual matcher actually scans for surfaced a
real detection gap (WR-01 below): the matcher is keyed to one specific token spelling and one
specific named constant, and a semantically identical reintroduction of the wrap-blind formula using
either of two other legitimate spellings already present in `DayWindow`'s own public API would pass
through undetected.

Separately, reading `requireRestFeasibility` end-to-end (not just the one line this phase touched)
turned up a pre-existing, unrelated-to-this-phase correctness defect in the same method: its
pre-horizon fallback is agent-keyed but not date-keyed, so it can silently substitute an unrelated,
stale historical predecessor span for a day that is merely a day-off inside the schedule horizon,
not the genuine pre-horizon edge. This is flagged as CR-01 because it lives in a file this review was
asked to cover in full, and it is reachable by an extremely common real-world shape (a weekly day
off followed by a working day).

## Critical Issues

### CR-01: `requireRestFeasibility`'s pre-horizon fallback is not scoped to the true pre-horizon date, in both the SHIFT and SLOT branches

**File:** `src/main/java/com/wfm/service/SolverService.java:1894-1900` (SHIFT branch) and
`src/main/java/com/wfm/service/SolverService.java:2008-2039` (SLOT branch)

**Issue:** `priorSpanByAgent` (built by `indexPriorSpansByAgent`, `SolverService.java:2116-2125`) is
indexed **by agent ID only** — it holds, per agent, the single `RestSpan` `RestPredecessorService`
resolved for exactly one date: `schedule.getPeriodStartDate().minusDays(1)` (confirmed directly in
`RestPredecessorServiceTest`'s `LOOKBACK_DATE = PERIOD_START.minusDays(1)` and the
"at most one per agent" javadoc on `indexPriorSpansByAgent`). It carries no date of its own once
indexed.

Both branches use this map as a date-blind fallback:

```java
// SHIFT branch, SolverService.java:1894-1900
LocalDate dMinus1 = d.minusDays(1);
AgentShiftAssignment predecessorRow = rowsByDate.get(dMinus1);
List<RestSpan> predecessorCandidates = predecessorRow != null
        ? shiftCandidateSpans(predecessorRow)
        : priorSpanCandidates(priorSpanByAgent.get(agentId));
```

```java
// SLOT branch, SolverService.java:2008-2039 (abridged)
AgentDayConfig predecessorConfig = configsByDate.get(dMinus1);
if (predecessorConfig != null) {
    ...
} else {
    RestSpan prior = priorSpanByAgent.get(agentId);
    ...
    predecessorEndMinute = window.anchoredWrappedEndMinute(prior.startTime(), prior.endTime());
}
```

Neither branch checks that `d` is actually the schedule's first business date, nor that
`prior.businessDate()` actually equals `dMinus1`. `rowsByDate`/`configsByDate` only contain entries
for *working* agent-days (`shiftAssignments`/`agentDayConfigs` both exclude days off —
`computeAgentDayConfigs` explicitly `continue`s when `effectiveHours() <= 0`, and
`AgentShiftAssignment` rows are only built per working agent-day per the D-05 comment at line ~377).
So for **any** date `d` inside the period whose immediate predecessor `d-1` happens to be a day off,
PTO, or otherwise non-working — not just the genuine first day of the period — `predecessorRow`/
`predecessorConfig` is `null`, and the code falls back to the single span resolved for
`periodStartDate - 1`, which may be an entirely unrelated date, possibly weeks or months earlier
(whatever the agent's last ACCEPTED shift was before this period began).

This is a real day-off-followed-by-a-working-day scenario, not a contrived edge case — it is one of
the most common shapes a real roster takes (a weekly rest day). The practical effect: an agent who
had, say, Tuesday off and returns to work Wednesday gets their Wednesday rest-feasibility check
computed against a stale shift from the end of the *previous solved period*, rather than correctly
recognizing "no predecessor shift on Tuesday, nothing to be infeasible against" (the same `continue`
behaviour already used a few lines above for the genuine "neither an in-horizon D-1 row nor a
pre-horizon span exists" case). Depending on the stale span's actual time, this can either produce
a false `PreSolveValidationException` ("this agent-day is structurally impossible") that blocks a
perfectly legal solve, or mask what should have been a real violation.

Contrast this with `ScheduleConstraintProvider`'s equivalent in-solve constraint
(`minimumRestShift`/`minimumRestSlot`, exercised by `RestHorizonEdgeTest`): there, the registered
pre-horizon `RestSpan` fact carries its own `businessDate()` and the join is naturally keyed by that
date, so it only ever matches the real first business date's successor — the same conflation cannot
happen there. `requireRestFeasibility`'s hand-rolled map lookup throws away exactly the date
information that makes the constraint-stream version safe.

**Fix:** Key the fallback on the span's own `businessDate()`, not merely its presence in a map keyed
by agent only — e.g.:

```java
RestSpan prior = priorSpanByAgent.get(agentId);
List<RestSpan> predecessorCandidates = predecessorRow != null
        ? shiftCandidateSpans(predecessorRow)
        : (prior != null && prior.businessDate().equals(dMinus1)
                ? priorSpanCandidates(prior)
                : List.of());
```

and the analogous guard in the SLOT branch before using `prior.startTime()`/`prior.endTime()`. This
also lets `RestFeasibilityRefusalTest`'s and `RestPredecessorServiceTest`'s existing pre-horizon
fixtures continue to pass unchanged, since they all construct `priorSpan` dated exactly
`D_MINUS_1 == D.minusDays(1)`.

## Warnings

### WR-01: The rest-gap arithmetic structural guard is evadable by two legitimate, already-public spellings of the same composition

**File:** `src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java:85,228-237`

**Issue:** `isWrapBlindPredecessorEndComposition` fires only when a line contains all three of: the
literal substring `MINUTES_PER_DAY`, a `-` character, and the literal substring
`anchoredEndMinute(` (built via `"anchored" + "End" + "Minute("`, `RestGapArithmeticGuardTest.java:85`).
This is a narrower target than the actual defect class the guard's own `.md` registry describes
(`rest-gap-arithmetic-guard.md:20-25`): "the predecessor's end offset must be read through the
wrap-aware `DayWindow.anchoredWrappedEndMinute`, never through the single-argument anchored end
accessor applied to the predecessor's end alone."

Two legitimate call shapes already exist in `DayWindow`'s own public API that reproduce the identical
wrap-blind defect without tripping either token:

1. **The two-argument static equivalent.** `DayWindow.endMinuteFromDayStart(dayStart, end)`
   (`DayWindow.java:387-392`) is `anchoredEndMinute`'s own static, non-bound counterpart and is
   `public`. A future reintroduction written as
   `int remaining = DayWindow.MINUTES_PER_DAY - DayWindow.endMinuteFromDayStart(dayStart, prior.endTime());`
   is textually and semantically identical to the forbidden idiom, composes only sanctioned
   primitives, and contains neither `anchoredEndMinute(` nor any token the matcher checks for.
2. **The literal `1440` instead of the named constant.** The matcher requires the literal substring
   `MINUTES_PER_DAY`; a reintroduction written as
   `int remaining = 1440 - window.anchoredEndMinute(prior.endTime());` supplies the identical
   numeric constant by value and is just as wrong, but is invisible to the scan.

Both evasions reproduce the exact production defect this phase fixed (an overstated "minutes
remaining in the predecessor's business day" for a wrapping predecessor), and neither is exercised by
`wrapBlindMatcher_isLiveAgainstSyntheticStrings` (`RestGapArithmeticGuardTest.java:156-194`), which
only tests the `window.anchoredEndMinute(...)` spelling. The guard's own `.md` documentation discloses
the single-line-scan limitation but does not disclose this narrower, token-level gap, which is a
distinct and closeable weakness, not an inherent limit of line-level scanning.

**Fix:** Broaden the matcher to catch at least the two shapes above, e.g. also match
`endMinuteFromDayStart(` (dropping the "single-argument" framing to cover the two-argument static
form), and/or match the literal `1440` alongside `MINUTES_PER_DAY` in the "subtraction" predicate.
Add synthetic-string cases for both shapes to
`wrapBlindMatcher_isLiveAgainstSyntheticStrings` so the guard-of-the-guard actually proves the
broadened matcher fires.

## Info

### IN-01: `TableRow.entryPoint()` is dead code

**File:** `src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java:412-414`

**Issue:** `entryPoint()` is declared as a convenience accessor for `path()` but is never called
anywhere in the file (`grep` confirms the only occurrence is its own declaration).

**Fix:** Delete the method, or use it in place of the `row.path()` calls elsewhere in the file if it
was meant to be the canonical accessor.

### IN-02: The SLOT branch's best-gap formula re-derives `RestSpan.gapMinutes`'s composition inline instead of delegating to it

**File:** `src/main/java/com/wfm/service/SolverService.java:2045-2048`

**Issue:** The comment at this line acknowledges the risk directly: "Same structural shape as
`RestSpan.gapMinutes` (D-08: the two must never drift)." This phase's own root cause (REST-05) was
precisely two independently-maintained copies of this exact structural shape drifting apart. The
SLOT branch's `bestGap = DayWindow.MINUTES_PER_DAY - predecessorEndMinute + successorLatestStartMinute`
is a third manual re-derivation of the same "minutes remaining in predecessor's day, plus minutes
elapsed into successor's day" idiom (using already-computed offsets rather than calling
`anchoredEndMinute`/`anchoredStartMinute` directly, so it does not trip the structural guard either —
consistent with WR-01's point that the guard's coverage is narrower than the pattern it exists to
prevent).

**Fix:** Where practical, express this computation in terms of the two already-computed boundary
offsets by constructing throwaway `RestSpan` instances (or extracting a small shared helper that both
`RestSpan.gapMinutes` and this branch delegate to) so there is structurally one implementation again,
rather than a comment asking future maintainers to keep two hand-written formulas in sync.

---

_Reviewed: 2026-10-04T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
