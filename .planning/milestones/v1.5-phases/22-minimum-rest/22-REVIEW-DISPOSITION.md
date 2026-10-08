---
phase: 22
review: 22-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: fixed
    title: "`buildRestWaiverDisclosure` read `schedule.getDayStart()` without the codebase's null-coalescing anchor fallback"
  - id: WR-02
    severity: warning
    disposition: fixed
    title: "Rest-waiver summary counts computed independently in `ScheduleService.toSummary` and `ScheduleController.toSummary`, protected only by a parity test"
  - id: IN-01
    severity: info
    disposition: fixed
    title: "`DeskManagement.tsx`'s `hoursStringToMinutes`/`handleUpdate` could yield `NaN`, which `JSON.stringify` serialises as `null`, silently clearing a configured minimum rest"
  - id: CR-01
    severity: critical
    disposition: fixed
    title: "The live (RUNNING, in-memory) schedule path still loads rest waivers without fetching `agent`, so polling a schedule with configured rest can throw `LazyInitializationException`"
  - id: WR-03
    severity: warning
    disposition: open
    title: "`hydrateRestWaiverInputsFromDb`'s pre-horizon branch is untested through the two new call sites it was built for"
  - id: WR-04
    severity: warning
    disposition: open
    title: "The IN-01 fix (`hoursStringToMinutes` / `handleUpdate`) has zero automated regression coverage"
  - id: IN-02
    severity: info
    disposition: open
    title: "The reused inline-validation message is inaccurate for a non-finite parse"
open: 3
total: 7
recorded: 2026-10-04T14:57:56.265Z
---

# Phase 22: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | fixed | 22-11 Task 2 — `706bbfd` |
| WR-02 | warning | fixed | 22-12 Task 1 — `b345f1b` |
| IN-01 | info | fixed | 22-12 Task 3 — `1214718` |
| CR-01 | critical | fixed | `f8f131d` — fetching finder + `RestWaiverFetchingFinderGuardTest` |
| WR-03 | warning | open | 22-REVIEW.md (incremental review of 22-11/22-12) |
| WR-04 | warning | open | 22-REVIEW.md (incremental review of 22-11/22-12) |
| IN-02 | info | open | 22-REVIEW.md (incremental review of 22-11/22-12) |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.

## Provenance of the first three rows

WR-01, WR-02 and IN-01 came from the FIRST review of this phase (plans 22-01..22-10) and are not
re-reported by the incremental review of 22-11/22-12, because that review's file scope is the diff
since the first review. They are recorded `fixed` here rather than dropped: the gap-closure plans
22-11 and 22-12 were created specifically to close them, and closure was verified twice — by the
incremental reviewer and independently by the orchestrator (`grep -rn "new ScheduleSummary("`
returns exactly one site; `ScheduleOutputService` null-coalesces the anchor; `hoursStringToMinutes`
returns `undefined` on a non-finite parse).

The first review's fuller per-finding triage rationale, its "Not re-reported here" note on the
REST-07 DB-fallback gap (since closed by 22-12), and its "What the reviewer checked and cleared"
section are preserved in git history at `0337f15^` for this path — the gate's regeneration of this
file does not carry prose sections forward.
