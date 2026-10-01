# Agent-Day Derivation Chain Allowlists (D-05 / D-06 / SOLV-07)

This file is parsed at test time by `AgentDayDerivationGuardTest`
(`src/test/java/com/wfm/service/AgentDayDerivationGuardTest.java`) — editing this file changes what
the build enforces, not merely what a human reads.

## Why a derived value is safe here

D-05 trades a stored `business_date` column on `agent_shift_assignment` for a two-link derivation
chain: `AgentDayConfig.date` comes only from the schedule period being walked in
`SolverService.computeAgentDayConfigs`, and `AgentShiftAssignment.date` comes only from that
`AgentDayConfig`'s own date, or — at accept time — from another, already-derived
`AgentShiftAssignment`'s date (the accept-time snapshot copy in `ScheduleService`).

`agent_shift_assignment` is rebuilt on every solve, so a stored column would be a denormalised copy
of a derived value — and `V41__agent_shift_assignment.sql`'s own design note is that entity creation
and the value-range filter read "the SAME fact … so they can never disagree **by construction**", a
guarantee a stored column breaks the moment it can hold a different value than the fact it was
copied from. A stored column would also be a SECOND business-date write path, the inverse of
BDAY-08's one-derivation-site rule this codebase already enforces for `Timeslot.business_date`
(`BusinessDateWritePathGuardTest`). And it would force a backfill decision for ACCEPTED historical
rows that `V41` deliberately freezes at accept time precisely so a later edit can never rewrite what
history says an agent actually worked — there is no correct backfill value for a row recording a
real, already-happened shift.

`SOLV-07`'s own text demands this chain be "guarded by a test rather than by convention" — this
file's three allowlists, each asserted for set equality in both directions by
`AgentDayDerivationGuardTest`, are that test. Cite `SOLV-07` and `D-05`/`D-06`, never phase numbers.

## Guard Allowlists

### AgentDayConfig construction sites

```
com.wfm.service.SolverService
```

### AgentShiftAssignment#setDate call sites -- deriving from AgentDayConfig

```
com.wfm.service.SolverService
```

### AgentShiftAssignment#setDate call sites -- snapshot-copy (propagating, not deriving)

```
com.wfm.service.ScheduleService
```
