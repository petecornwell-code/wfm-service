# Rest Gap Arithmetic Call-Site Registry (REST-02, REST-05)

This file is parsed at test time by `RestGapArithmeticGuardTest`
(`src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java`) — editing this file changes what
the build enforces, not merely what a human reads.

## Why this guard exists

A predecessor shift running `22:00`–`06:00` before a `07:00` successor measured `1500` minutes of
rest, where the true figure is `60`. An `11`-hour (`660`-minute) minimum therefore reported no
violation at all, and an illegal back-to-back roster scored `0hard`. The error direction is unsafe
precisely because it OVERSTATES rest: a predecessor that wraps past the business-day anchor was
read as if it ended earlier in the clock than it actually did, inflating "minutes remaining in the
predecessor's business day" instead of deflating it.

This defect occurred a SECOND time, independently, in `SolverService.requireRestFeasibility`'s SLOT
pre-horizon branch — under a comment asserting "the two must never drift" while the two were, in
fact, two separately-maintained copies of the same composition. Both occurrences used only
sanctioned primitives (`DayWindow.MINUTES_PER_DAY`, the single-argument anchored end accessor,
ordinary subtraction); neither used a forbidden token. This is a *correct-primitive, wrong-
composition* defect, not a forbidden-token defect — the predecessor's end offset must be read
through the wrap-aware `DayWindow.anchoredWrappedEndMinute`, never through the single-argument
anchored end accessor applied to the predecessor's end alone, because the single-argument accessor
is bounded to `(0, 1440]` and cannot distinguish "ends within this business day" from "ends the
calendar day after wrapping past the anchor."

That distinction is why this guard is a narrow composition scan keyed to the specific idiom
("minutes remaining in the predecessor's business day" = `MINUTES_PER_DAY` minus an end-accessor
call, on one line) rather than a blanket ban on the single-argument anchored end accessor itself. A
blanket ban would be over-broad — that accessor is legitimate in many places where only one time
within one business day is being read — and would demand a large, noisy allowlist of every
legitimate single-time read in the codebase. That is exactly the "guard becomes decoration" failure
`MidnightTimeArithmeticGuardTest`'s own javadoc warns against, so this guard stays narrow by design.

## The Call-Site Table

| Entry point | Source file | What must hold | Proving test |
|---|---|---|---|
| the predecessor-end computation inside `RestSpan.gapMinutes` | `com.wfm.model.RestSpan` | derives the predecessor's end offset from `DayWindow.anchoredWrappedEndMinute(prev.startTime(), prev.endTime())`, never from the single-argument anchored end accessor applied to the predecessor's end alone, so a predecessor that wraps past the business-day anchor is measured rather than silently treated as not having wrapped | `MinimumRestShiftConstraintTest` |
| the pre-horizon sub-branch of `requireRestFeasibility`'s SLOT branch | `com.wfm.service.SolverService` | derives the historical predecessor's end offset from the SAME `anchoredWrappedEndMinute`, never from a second inline composition, and does not clamp the resulting negative `bestGap` | `RestFeasibilityRefusalTest` |

## Guard Allowlists

### anchoredWrappedEndMinute call sites

Every production class that actually invokes `DayWindow.anchoredWrappedEndMinute(...)` — this is
call SITES, not the implementation. `com.wfm.util.DayWindow` declares the method and is
deliberately excluded, exactly as `RestWaiverLookup` is excluded from its own call-site set in
`rest-waiver-predicate-guard.md`. Derived from the live source with a grep before writing it, not
predicted.

```
com.wfm.model.RestSpan
com.wfm.service.SolverService
```

## Known scope boundaries — deliberate, not gaps

- **This is a purely textual, single-line scan.** A composition split across a helper method that
  itself contains none of the scanned tokens, assembled from string fragments, or reached by
  reflection passes undetected. This is the honest limit of what a line-level text scan can see,
  disclosed in the same register as `bday-join-guard.md`'s and `midnight-time-arithmetic.md`'s own
  boundaries.
- **This guard says nothing about whether the one implementation is CORRECT.** It proves only that
  there is exactly one. Correctness is established by the overnight-predecessor fixtures in
  `MinimumRestShiftConstraintTest`, `MinimumRestSlotConstraintTest`, `RestHorizonEdgeTest`,
  `RestPredecessorServiceTest`, `RestFeasibilityRefusalTest` and `RestWaiverDisclosureTest` — six
  classes, every one of which had zero overnight-predecessor fixtures before this phase. The guard
  is a tripwire against regression, never a substitute for those fixtures.
- **This is NOT a blanket ban on the single-argument anchored end accessor.** That accessor is
  legitimate in many places where only one time within one business day is being read —
  `RestSpan.ofSlots`' per-slot ordering and `requireRestFeasibility`'s own `windowEndMinute` are
  both confirmed-clean uses and stay untouched. Only the specific "minutes remaining in the
  predecessor's business day" idiom — the `MINUTES_PER_DAY` minus an end-accessor call composition,
  on one line — is forbidden outside the sanctioned callers.
- **PF-02, recorded during planning:** `RestSpan.ofSlots` orders an agent-day's slots by anchored
  start minute and takes the first slot's start and the last slot's end, so it cannot emit a span
  wrapping past the business-day anchor unless an individual timeslot itself crosses that anchor,
  which grid generation from the day start does not produce. A SLOT-mode wrapping predecessor is
  therefore reached through a pre-horizon `RestSpan` supplied as a problem fact or as a
  `requireRestFeasibility` parameter, which is where it is pinned. Both call sites in the table
  above are corrected and proven regardless; this bullet records where the SLOT-mode wrap is and is
  not reachable, so a future reader does not mistake the absence of a wrapping `ofSlots` fixture for
  a missing test.
