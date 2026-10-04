# ScheduleSummary Construction Site Registry (REST-07, WR-02, D-08)

This file is parsed at test time by `ScheduleSummaryConstructionSiteGuardTest`
(`src/test/java/com/wfm/service/ScheduleSummaryConstructionSiteGuardTest.java`) — editing this
file changes what the build enforces, not merely what a human reads.

## Why this guard exists

The code review (WR-02) found the rest-waiver summary counts computed independently in two
places: `ScheduleService.toSummary` and `ScheduleController.toSummary`. Both walked
`buildRestWaiverDisclosure`'s output and derived `appliedRestWaiverCount`/`unusedRestWaiverCount`
by hand, protected only by a parity test (`summaryCounts_bothConstructionSitesAgree` /
`bothConstructionSitesAgreeOnTheAnchor`) that only catches a divergence someone remembered to
write a fixture for. This phase already paid for the structural alternative once, for the
analogous waived-pair-predicate risk (D-08, `RestWaiverPredicateGuardTest`) — this registry and
its guard apply that same discipline a second time, to `ScheduleSummary` construction rather than
to the waived-pair predicate.

`ScheduleController.toSummary` is now deleted outright. `ScheduleController.startSolve`,
`stopSolve` and `acceptSchedule` all call `ScheduleService.toSummary(Schedule)` — the single
public entry point, which itself delegates to the pre-existing private two-argument form so
`listSchedules` keeps resolving the desk name once per page rather than once per row.

A second, re-added construction site would not throw. It would not change any score. It would be
invisible to any behavioural test that only checks the final counts, because a second
implementation that happens to agree with the first produces an identical observable result on
every fixture anyone thinks to write — exactly the failure mode this guard exists to make loud
instead of silent.

## The Construction Site Table

| Entry point | Source file | What must hold | Public entry point callers must use |
|---|---|---|---|
| `toSummary(Schedule, String)` | `com.wfm.service.ScheduleService` | The ONLY expression in `src/main/java` that calls `new ScheduleSummary(...)` | `ScheduleService.toSummary(Schedule)` (resolves the desk name itself) for a single schedule; `listSchedules` calls the private two-argument form directly with its own already-resolved desk name |

## Guard Allowlist

The fenced list below is what `ScheduleSummaryConstructionSiteGuardTest` actually parses and
asserts set equality against — the table above is for humans; this list is load-bearing for the
build. Each entry is a fully-qualified production class name, one per line. Populated from the
actual current source (`grep -rl 'new ScheduleSummary(' src/main/java/`, read directly, never
predicted).

```
com.wfm.service.ScheduleService
```

## Known scope boundaries — deliberate, not gaps

- **This is a purely textual scan.** A construction expression assembled via reflection, string
  concatenation, or hidden behind a helper method that itself contains none of the scanned tokens
  would pass this guard undetected. Not a risk observed in this codebase today — the only
  construction found during planning was a direct `new ScheduleSummary(...)` call — but it is the
  honest boundary of what a line-level text scan can see, in the same register as
  `rest-waiver-predicate-guard.md`'s and `midnight-time-arithmetic.md`'s own disclosed boundaries.
- **This guard says nothing about whether the one construction site is *correct*.** It only proves
  there is exactly one. Correctness of the counts it derives is established by
  `RestWaiverDisclosureTest` and `ScheduleSummaryReadTest`, never by this guard.
- **A second implementation split across multiple lines or multiple methods that never puts the
  full `new ScheduleSummary(` token on one line would not be caught by this scan** — a measured
  blind spot, not an oversight, matching the single-line textual technique every structural guard
  in this project already uses.
