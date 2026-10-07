---
phase: 24
review: 24-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: open
    title: "Generator sorts demand windows in clock order while the validator sorts them in anchored order, so the two \"shared\" coverage lists drift"
  - id: WR-02
    severity: warning
    disposition: open
    title: "Peak-shortfall advisories tie-break on clock start time, contradicting the phase's own anchored-order rule"
  - id: WR-03
    severity: warning
    disposition: open
    title: "Business-range query path trusts the cursor payload and turns a malformed one into a 500"
  - id: IN-01
    severity: info
    disposition: open
    title: "`PeakShortfallAdvisory.date` silently changed meaning with no contract note"
  - id: IN-02
    severity: info
    disposition: open
    title: "Join-guard receiver heuristic misses `Timeslot` locals not named `ts`, and the widened scan relies on that gap being empty"
open: 5
total: 5
unparsed: 0
recorded: 2026-10-07T18:35:15.000Z
---

# Phase 24: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | open | - |
| WR-02 | warning | open | - |
| WR-03 | warning | open | - |
| IN-01 | info | open | - |
| IN-02 | info | open | - |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
