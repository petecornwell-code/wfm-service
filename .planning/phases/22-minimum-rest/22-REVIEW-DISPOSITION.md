---
phase: 22-minimum-rest
source: 22-REVIEW.md
recorded: 2026-10-04
recorded_by: execute-phase code_review_gate
findings:
  total: 3
  critical: 0
  warning: 2
  info: 1
dispositions:
  open: 3
  fixed: 0
  skipped: 0
---

# Phase 22 — Code Review Disposition

One row per finding ID from `22-REVIEW.md`. A finding defaults to `open`; it becomes `fixed` or
`skipped` only when something actually acted on it. No `--fix` run has been dispatched for this
phase, so all three findings are `open` — recorded as seen, not as resolved.

| Finding | Severity | Summary | Disposition | Reason |
|---|---|---|---|---|
| WR-01 | Warning | `buildRestWaiverDisclosure` reads `schedule.getDayStart()` without the codebase's null-coalescing anchor fallback (`resolveAnchor`) | open | Not reachable at HEAD — V54 (`dayStart`) precedes V55 (`minimum_rest_minutes`), so a schedule carrying minimum rest always carries an anchor. Untested and inconsistent with the phase's own defensive convention, so left standing rather than dismissed. |
| WR-02 | Warning | Rest-waiver summary counts computed independently in `ScheduleService.toSummary` and `ScheduleController.toSummary`, protected only by a parity test | open | Real duplication risk, and notably the same phase builds a structural guard (`RestWaiverPredicateGuardTest`) for the analogous waived-pair risk. A structural guard here would be consistent; deferred as a design call, not a defect fix. |
| IN-01 | Info | `DeskManagement.tsx`'s `hoursStringToMinutes`/`handleUpdate` can yield `NaN`, which `JSON.stringify` serialises as `null`, silently clearing a configured minimum rest | open | Likely unreachable through a browser `<input type="number">`, but unguarded in code. |

## Not re-reported here

The reviewer was briefed on, and deliberately did not re-litigate, one already-known issue tracked
separately for the phase verifier: the waiver counts on `ScheduleSummary` are accurate only for a
live in-memory schedule. An ACCEPTED schedule fetched through the DB-fallback path in
`listSchedules` / `getScheduleSummary` has unhydrated transient collections and reports a false
`0`/`0`. It is recorded in `22-08-SUMMARY.md` and `22-10-SUMMARY.md` and bears directly on REST-07.

## What the reviewer checked and cleared

- Constraint stream shapes, `RestSpan.gapMinutes`, the pre-horizon `concat`, and the
  `RestWaiverLookup` exclusion clause — no correctness defect found.
- Tenant/desk scoping across `AgentRestWaiverRepository`, the two new lookback finders,
  `RestWaiverService`, `DeskService.setMinimumRest`, and the new `/rest-waivers` and
  `/minimum-rest` endpoints — all scoped by `tenantId` + `deskId`, no bare `findById`, no
  cross-tenant leak.
- `SolverService.requireRestFeasibility` and `RestPredecessorService` — SHIFT/SLOT branches,
  waiver hoist, and anchor-divergence guard consistent with their documentation and tests.

To act on the open findings: `/gsd-code-review 22 --fix`
