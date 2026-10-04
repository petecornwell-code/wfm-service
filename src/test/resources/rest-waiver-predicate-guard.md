# Rest Waiver Predicate Call-Site Registry (REST-06, D-08)

This file is parsed at test time by `RestWaiverPredicateGuardTest`
(`src/test/java/com/wfm/service/RestWaiverPredicateGuardTest.java`) — editing this file changes
what the build enforces, not merely what a human reads.

## Why this guard exists

D-03 bought two separate mode-gated constraints (`minimumRestShift`, `minimumRestSlot`) over one
fused stream, with the divergence cost named at the time. D-08 is what pays for that choice: with
three things that must now agree about what "waived" means — the SHIFT constraint, the SLOT
constraint, and REST-03's pre-solve refusal (plan 22-07) — a single shared predicate plus a
structural guard is the only mechanism stopping them drifting apart.

A second, inlined comparison living in any of these three sites would not throw. It would not
change any score on a desk with no waivers at all (REST-04's empty case is identical either way).
It would be invisible to any behavioural test that only checks the final score, because a second
implementation that happens to agree with the first produces the identical observable result on
every fixture anyone thinks to write. It would surface only as two constraints — or a constraint
and the pre-solve refusal — quietly disagreeing about whether a given pair is waived on some
fixture nobody wrote, which is exactly the failure mode this guard exists to make loud instead of
silent.

## The Call-Site Table

| Entry point | Source file | What must hold | Proving test |
|---|---|---|---|
| `minimumRestShift`'s waiver exclusion | `com.wfm.solver.ScheduleConstraintProvider` | Excludes a pair via `ifNotExists(AgentRestWaiver.class, Joiners.filtering(...))` calling `RestWaiverLookup.waives(waiver, next.agentId(), next.businessDate())` — the successor side, per D-06 — placed after the gap filter and before the penalty | `MinimumRestShiftConstraintTest` |
| `minimumRestSlot`'s waiver exclusion | `com.wfm.solver.ScheduleConstraintProvider` | Identical shape to the row above, for the SLOT-mode sibling constraint | `MinimumRestSlotConstraintTest` |
| `requireRestFeasibility`'s waiver exclusion | `com.wfm.service.SolverService` | Calls `RestWaiverLookup.isWaived(waivers, agentId, businessDateEntered)` through this file's own `isAgentDayWaived` wrapper — never a local iteration — the same shared predicate the two constraints above use, hoisted so one call site serves both the SHIFT and SLOT branches | `RestFeasibilityRefusalTest` |

## Guard Allowlists

The two fenced lists below are what `RestWaiverPredicateGuardTest` actually parses and asserts set
equality against — the table above is for humans; these lists are load-bearing for the build. Each
entry is a fully-qualified production class name, one per line. Populated from the actual current
source (`grep -rl` against `src/main/java`, read directly, not predicted).

### AgentRestWaiver references

Every production class that references the `AgentRestWaiver` entity type at all, including the
entity's own declaration and the repository's declaration — mirrors `ushf-05-write-paths.md`'s
Set A/B precedent of including a type's own declaring file in its own reference set.

```
com.wfm.model.AgentRestWaiver
com.wfm.model.RestWaiverLookup
com.wfm.model.Schedule
com.wfm.repository.AgentRestWaiverRepository
com.wfm.service.RestWaiverService
com.wfm.service.ScheduleOutputService
com.wfm.service.ScheduleService
com.wfm.service.SolverService
com.wfm.solver.ScheduleConstraintProvider
```

### RestWaiverLookup call sites

Every production class that actually invokes `RestWaiverLookup.waives(...)` or
`RestWaiverLookup.isWaived(...)` — narrower than the set above, and the set this guard's
second-implementation scan exists to keep small. `RestWaiverLookup` itself is excluded: this set is
call SITES, not the implementation.

```
com.wfm.solver.ScheduleConstraintProvider
com.wfm.service.SolverService
```

## Known scope boundaries — deliberate, not gaps

- **This is a purely textual scan.** A comparison written through reflection, assembled from
  string fragments, or hidden behind a helper method that itself contains none of the scanned
  tokens would pass this guard undetected. Not a risk observed in this codebase today — every
  comparison found during planning was a direct field read and `.equals(...)` call — but it is the
  honest boundary of what a line-level text scan can see, in the same register as
  `bday-join-guard.md`'s and `midnight-time-arithmetic.md`'s own disclosed boundaries.
- **This guard says nothing about whether the one implementation is *correct*.** It only proves
  there is exactly one. Correctness of `RestWaiverLookup.waives`'s direction (D-06) and null
  handling (T-22-12) is established by the behavioural tests in `MinimumRestShiftConstraintTest`
  and `MinimumRestSlotConstraintTest`, never by this guard.
- **The second-implementation scan looks for a single line reading both an `AgentRestWaiver`'s
  date and its agent and comparing them** (the literal shape `RestWaiverLookup.waives`'s own body
  has). A second implementation split across multiple lines or multiple methods that never puts
  both reads on one line would not be caught by this scan — a measured blind spot, not an oversight,
  matching the single-line textual technique every structural guard in this project already uses.
- **A call site that passes the whole `AgentRestWaiver` object into `RestWaiverLookup` without
  reading its fields directly is, correctly, invisible to the second-implementation scan.** That is
  the intended shape of a correct call site (see the call-site table above) — the scan's matcher is
  deliberately narrow enough to not fire on `RestWaiverLookup.waives(waiver, agentId, date)` itself.
