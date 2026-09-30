# BDAY-02 `business_date` Write-Path Table

This file is parsed at test time by `BusinessDateWritePathGuardTest`
(`src/test/java/com/wfm/service/BusinessDateWritePathGuardTest.java`) — editing this file changes
what the build enforces, not merely what a human reads.

## Why this guard exists

SOLV-01 joins roughly twelve solver computations on `timeslot.business_date`. A wrong value there
is a **silent non-join**, not an error — the join simply returns nothing, and the solver behaves
as if the affected timeslots do not exist. The writer set is therefore pinned down *before*
BDAY-04 (`DayWindow` re-anchoring) and OVNT-01 (overnight shift templates) both add code with real
reason to touch this field.

## Deriving vs. propagating writers

Two different things can happen at a call to `Timeslot#setBusinessDate`:

- **Deriving** — computing a business date from scratch (today: always equal to the calendar
  date, since every desk's day-start is gated to `00:00` until BDAY-04 lifts that gate). This is
  the operation the "one writer" prohibition is actually about — a second deriving writer is how
  the field goes quietly wrong.
- **Propagating** — copying an *already-derived* value from one `Timeslot` row onto another,
  verbatim, via `x.setBusinessDate(y.getBusinessDate())`. This is a snapshot copy, not a new
  computation, and cannot introduce a wrong value that wasn't already there.

**Two writers by design, not by accident, recorded here rather than silently absorbed:**
`ScheduleService`'s accept-time snapshot copy also sets the field —
`snapshot.setBusinessDate(live.getBusinessDate())` in `ScheduleService#acceptSchedule` — because
V53 added `business_date NOT NULL` with no Java-side default, so every row this path writes must
carry a value. That call is a **propagating** write (it copies the live row's own already-derived
value forward onto its ACCEPTED snapshot); it is not a second computation of a business date and
does not weaken the "one deriving writer" guarantee SOLV-01 depends on. Rather than let that fact
go unenforced, this guard tracks the two kinds of call separately and pins BOTH sets by name — so
a new class that either derives OR merely propagates a value without an accompanying allowlist
entry still fails the build.

## The Table

| Path | Entry point | Source file | Effect on `business_date` | Proving test |
|---|---|---|---|---|
| Generation — sole deriving writer | `TimeslotGeneratorService#generateTimeslots` | `com.wfm.service.TimeslotGeneratorService` | Sets `business_date` to the business-day cursor for every newly created timeslot row inside the generation loop -- never the calendar date. The two values agree today only because every desk's day-start is gated to `00:00` until BDAY-04 lifts that gate. | `TimeslotGeneratorBusinessDateTest` |
| Accept-schedule snapshot — propagating copy | `ScheduleService#acceptSchedule` | `com.wfm.service.ScheduleService` | Copies the live row's already-derived `business_date` onto its ACCEPTED snapshot row (`snapshot.setBusinessDate(live.getBusinessDate())`); never computes a fresh value. Exercised by `ScheduleServiceShiftSnapshotTest`'s accept-path tests; the `NOT NULL` correctness of every row this path writes is proven across the full suite by `MidnightTimeslotPostgresTest`. | `ScheduleServiceShiftSnapshotTest` |

## Guard Allowlists

The two fenced lists below are what `BusinessDateWritePathGuardTest` actually parses and asserts
set equality against, in BOTH directions — the table above is for humans; these lists are
load-bearing for the build. A class calling `setBusinessDate(...)` with an argument that is NOT a
`.getBusinessDate()` read is classified DERIVING; a class calling it WITH one is classified
PROPAGATING. `Timeslot` itself (the setter's own declaration) is excluded from the scan, the same
way `MidnightTimeArithmeticGuardTest` excludes `DayWindow` — the class that IS the rule, not a
caller of it.

### Timeslot#setBusinessDate call sites

```
com.wfm.service.TimeslotGeneratorService
```

### Timeslot#setBusinessDate call sites -- snapshot-copy (propagating, not deriving)

```
com.wfm.service.ScheduleService
```
