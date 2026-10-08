---
phase: 23
review: 23-REVIEW.md
titles: json
findings:
  - id: CR-01
    severity: critical
    disposition: fixed
    title: "`requireRestFeasibility`'s pre-horizon fallback is not scoped to the true pre-horizon date, in both the SHIFT and SLOT branches"
  - id: WR-01
    severity: warning
    disposition: open
    title: "The rest-gap arithmetic structural guard is evadable by two legitimate, already-public spellings of the same composition"
  - id: IN-01
    severity: info
    disposition: open
    title: "`TableRow.entryPoint()` is dead code"
  - id: IN-02
    severity: info
    disposition: open
    title: "The SLOT branch's best-gap formula re-derives `RestSpan.gapMinutes`'s composition inline instead of delegating to it"
open: 3
total: 4
unparsed: 1
recorded: 2026-10-04T21:12:20.122Z
---

# Phase 23: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| CR-01 | critical | fixed | quick 261008-eby, fix commit 5e454e8 |
| WR-01 | warning | open | - |
| IN-01 | info | open | - |
| IN-02 | info | open | - |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
