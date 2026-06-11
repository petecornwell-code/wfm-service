# WFM Service — Progress Report

**Period:** May 4 – June 11, 2026
**Product:** Workforce Management scheduling service for Helpware (live at `d2bbtcc80peap7.cloudfront.net`)
**Audience:** Project / delivery management

---

## Executive Summary

This period covered the **launch of milestone v1.1 ("Schedule Quality & Reporting")** and the delivery of its first major workstream. The product lets scheduling managers produce optimised, constraint-aware agent rosters across client desks in minutes instead of hours.

Over these ~5 weeks we:

1. **Stabilised the live deployment** — fixed several production/infrastructure issues that were blocking reliable releases.
2. **Shipped Phase 5 in full** — richer agent data from BambooHR, the ability to exclude non-schedulable staff, bulk desk assignment via spreadsheet, and PTO sync fixes.
3. **Largely completed Phase 6** — automatically importing each agent's fixed weekly days off from BambooHR so the scheduler never books people on their contracted days off.

**Headline outcome for the business:** schedules now reflect real-world staff data far more accurately — who is full- vs part-time, who shouldn't be scheduled at all, and which days each person is contracted off — reducing manual correction after each solve.

---

## Milestone Status at a Glance

| Phase | Description | Status |
|-------|-------------|--------|
| **5. Agent Data Enrichment & Desk Upload** | Richer BambooHR data, non-schedulable exclusion, bulk desk upload, PTO fixes | ✅ **Complete** (Jun 2) |
| **6. Solver Quality — PTO & Weekends** | Auto-import fixed weekly days off as hard scheduling rules | 🟡 **In progress** (2 of 3 plans done; final step awaiting verification) |
| 7. Coverage, Utilization & Diagnostics | Coverage gaps, utilization reports, PTO sync diagnostics | ⬜ Not started |
| 8. Export, Score Breakdown & Tuning | Better Excel/PDF export, "why this schedule" view, tunable solver | ⬜ Not started |

*Milestone v1.1 is roughly **40% delivered** (1.7 of 4 phases).*

---

## What Was Delivered

### 1. Production & Release Stability (early May)

Several fixes to keep the live service deployable and reliable:

- Resolved a browser-security (CORS) error that blocked the web app from talking to the backend.
- Fixed the automated build so deployments run cleanly through the pipeline.
- Loosened the deployment permissions so releases can be pushed from any working branch.

**Business value:** removed friction and failures from the release process; the team can ship updates without manual intervention.

### 2. Solver Reliability Fixes (early May)

- Made "secondary skill" optional — real agents often have only one skill, and the solver was wrongly excluding them.
- Made the staffing-demand spreadsheet upload more forgiving of real-world file formats (varied tab names and column headers).

**Business value:** fewer agents wrongly left off schedules; less rework reformatting spreadsheets before upload.

### 3. Phase 5 — Agent Data Enrichment & Desk Upload (✅ complete)

The largest deliverable of the period. Five sub-projects, all shipped and approved against the live deployment.

**a) Richer agent data from BambooHR**
- Each agent now shows their **employment type** (full-time / part-time), and operators can filter the agent list by it.
- Each agent now shows their **job title**.
- *Value:* operators can see at a glance who they are scheduling and slice the workforce appropriately.

**b) Non-schedulable staff exclusion**
- Operators can mark specific job titles as "non-schedulable" (e.g. managers, trainees). Those people are now automatically excluded from both the solver and desk allocation.
- *Value:* prevents staff who shouldn't be on the roster from being scheduled — a frequent source of manual correction.

**c) Bulk desk assignment via spreadsheet**
- Operators can upload a spreadsheet to assign many agents to desks at once. The system auto-detects the file format, and a results screen clearly shows **which rows succeeded and which failed, with reasons**. Manual one-by-one assignment still works.
- *Value:* turns a tedious manual task into a single upload, with transparency on what didn't import and why.

**d) PTO (time-off) sync reliability**
- Only **approved** PTO now blocks scheduling; **requested** (not-yet-approved) PTO is visible but no longer wrongly blocks people.
- *Value:* fixes a class of bug where pending requests removed agents from schedules prematurely.

**e) Graceful handling of BambooHR rate limits**
- When BambooHR temporarily throttles us, operators see a clear "retry shortly" message instead of a generic server error.
- *Value:* less confusion and fewer support questions when an external system is busy.

### 4. Phase 6 — Auto-Import of Fixed Weekly Days Off (🟡 in progress)

Most of this phase is built; the final step is awaiting human verification.

- Each agent's **fixed weekly days off** are now read directly from BambooHR (the "Working days" field) and turned into **hard scheduling rules** — the solver will never book someone on a contracted day off.
- The system tolerantly reads the many free-text formats real HR data contains (ranges, lists, spelling variations) without failing.
- Agents with missing or "variable" day-off data are flagged as a **data gap** and held out of scheduling so they aren't scheduled on bad assumptions.

**Business value:** schedules respect each person's real contracted working pattern automatically, removing one of the biggest remaining sources of manual fix-up — and surfacing where the underlying HR data needs cleaning up.

**Remaining for completion:** final implementation step (generating the day-off blocks and outlier flags) is committed but awaiting verification/sign-off before the phase closes.

---

## Status, Risks & What's Next

**Current state:** Phase 5 is live and signed off. Phase 6 is at its final checkpoint — the work is committed and awaiting a verification pass before being marked complete.

**Coming next in v1.1:**
- **Phase 7 — Reporting & Diagnostics:** coverage gap visibility (where is the schedule thin?), agent utilization (over-/under-worked staff), preference-satisfaction metric, and a PTO sync diagnostic that shows exactly what imported and what failed.
- **Phase 8 — Export & Transparency:** improved Excel/PDF schedule exports, a "why this schedule?" solver breakdown, and UI controls to tune solver behaviour without a code release.

**Notable carried-over risk (from v1.0, unchanged this period):** the preferred AWS deployment-permissions setup (OIDC) remains deferred pending admin-level cloud access. A working token-based workaround is in place, so this is not blocking delivery.

---

*Generated from version-control history and project planning records for the May 4 – June 11, 2026 window. (Note: no commits were dated May 4 exactly; activity in range begins May 5.)*
