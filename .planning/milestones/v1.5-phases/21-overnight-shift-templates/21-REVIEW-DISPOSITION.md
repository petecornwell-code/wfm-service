---
phase: 21
review: 21-REVIEW.md
titles: json
findings:
  - id: CR-01
    severity: Blocker
    disposition: fixed
    title: "FTE upload silently drops post-midnight demand on any desk anchored away from midnight"
  - id: WR-01
    severity: Warning
    disposition: open
    title: "`ShiftTemplateService.isAligned`'s END-position reinterpretation is unsound when the live grid starts later than the desk's anchor"
  - id: WR-02
    severity: Warning
    disposition: open
    title: "`DeskService.setDayStart`'s stranding check does not exclude retired shift-template eras"
  - id: WR-03
    severity: Warning
    disposition: open
    title: "`AgentAllocationTab`'s envelope-reached check can throw past the end of the business day, with no error boundary to catch it"
  - id: IN-01
    severity: Info
    disposition: open
    title: "`DeskManagement.tsx`'s Cancel button stays enabled while a day-start save is in flight"
open: 4
total: 5
recorded: 2026-10-03T12:40:00Z
---

# Phase 21: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| CR-01 | Blocker | fixed | src/main/java/com/wfm/service/FteUploadService.java:165-169, 190, 213-218 — orchestrator independently confirmed the calendar/business key mismatch and found the impact UNDERSTATED: unmatched columns are reported via the `skipped` list (not silent), but columns that DO resolve under the calendar key attach FTE values to the WRONG business day's timeslot, which is mis-attribution rather than omission. Fixed by keying the lookup by `getBusinessDate()`; proven by a new mis-attribution test and a 00:00-anchor no-op control; full suite green (202 classes, 1303 tests, 0 failures/errors, 4 pre-existing skips) |
| WR-01 | Warning | open | src/main/java/com/wfm/service/ShiftTemplateService.java:412-424 |
| WR-02 | Warning | open | src/main/java/com/wfm/service/DeskService.java:298-321 |
| WR-03 | Warning | open | frontend/src/pages/ScheduleResults.tsx:695-698 |
| IN-01 | Info | open | frontend/src/pages/DeskManagement.tsx:109, 172-175 |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
