---
phase: 17-consistency-constraint-drift-reporting
plan: 05
subsystem: ui
tags: [react, typescript, vite, inline-styles]

# Dependency graph
requires:
  - phase: 17-consistency-constraint-drift-reporting
    provides: "17-01/17-02's ScheduleDetailResponse.DriftReport DTO family and ConstraintWeights.consistentStartWeight/consistencyToleranceMinutes/preferredStartShiftModeWeight columns; 17-03's popularity ranking and drift date-filter summary recompute; 17-04's V49-shipped Score defaults (2 soft / 1 soft)"
provides:
  - "The Drift Report tab (frontend/src/pages/ScheduleResults.tsx) surfacing DRFT-01/02/04 to the operator, wired to the existing ScheduleDetail payload with no independent fetch"
  - "DriftReportEntry/DriftSummary/ShiftPopularityEntry/DriftReport TypeScript interfaces and ScheduleDetail.driftReport (frontend/src/api/client.ts)"
  - "Three new Constraint Weights rows and the D-10 precedence note (frontend/src/pages/ConstraintWeightsPage.tsx), including the page's first non-score field"
  - "ConstraintWeightsData's widened index signature (Score | number) admitting the tolerance-band minutes field"
affects: []

# Actuals (#2632)
actuals:
  tokens: 4313
  tasks: 2
  commits: 2
plan_head_before: 33eb0412da85ea929cda202705dc33bd6bb87c8a

tech-stack:
  added: []
  patterns:
    - "Third-branch extension of an existing two-branch colour conditional (PreferenceTab's honoured/not-honoured green/red) to a three-state honoured/drifted/no-target signal, reusing DeskAgents.tsx's NOT_SET gray for the third state"
    - "A form row whose value is read/written outside the page's generic Record<string, Score> cast, special-cased by key inside the same .map(...) loop rather than forking into a second render path"

key-files:
  created: []
  modified:
    - frontend/src/api/client.ts
    - frontend/src/pages/ScheduleResults.tsx
    - frontend/src/pages/ConstraintWeightsPage.tsx

key-decisions:
  - "Drift Report summary bar is computed client-side from the date-filtered entry set (not report.summary as PreferenceTab does from the whole-schedule report) because the page's schedules.get() call never sends a date query param -- report.summary would otherwise silently disagree with the date-filtered rows on screen."
  - "The tolerance-band row stays inside CONSTRAINTS.map(...) rather than forking a second render path -- it is special-cased by key before reaching the Record<string, Score> cast, per the plan's explicit 'branch keyed on the row's kind' allowance."
  - "The D-10 precedence note is emitted as a second <tr> returned via a React Fragment keyed off preferredStartShiftModeWeight inside the same .map(...) call, avoiding a second array pass over CONSTRAINTS."

patterns-established: []

requirements-completed: [CONS-02, CONS-03, CONS-06, DRFT-01, DRFT-02, DRFT-04, XCUT-01]

coverage:
  - id: D1
    description: "DriftReportEntry/DriftSummary/ShiftPopularityEntry/DriftReport TypeScript contract added to client.ts, and ScheduleDetail gains a nullable driftReport field"
    requirement: DRFT-01
    verification:
      - kind: other
        ref: "cd frontend && npm run build (tsc -b clean exit); grep -q 'driftReport' frontend/src/api/client.ts"
        status: pass
    human_judgment: false
  - id: D2
    description: "Drift Report tab (main per-agent-date table, date-filtered summary bar, D-11 live-recomputation note, byte-identical headers to the Excel export sheet, single not-applicable message on slot-scheduled desks)"
    requirement: DRFT-01
    verification:
      - kind: other
        ref: "cd frontend && npm run build; header literals in ScheduleResults.tsx match ScheduleExportService.java:248's {\"Agent\",\"Date\",\"Status\",\"Usual Start\",\"Actual Start\",\"Delta (min)\"} verbatim"
        status: pass
    human_judgment: true
    rationale: "Whether the three-state Status column reads as visually distinct at a glance, and whether a Drifted row draws the eye without a fourth colour, is a backstop truth per 17-UI-SPEC.md E1 populated -- this repo has no frontend test framework, so only a human opening a shift-desk schedule can confirm it."
  - id: D3
    description: "Most-Subscribed Usual Shifts popularity section beneath the main table, with its own empty message and subtext disclosing it reads stored usual shifts rather than this solve's results"
    requirement: DRFT-04
    verification:
      - kind: other
        ref: "cd frontend && npm run build; grep -c 'Most-Subscribed Usual Shifts' frontend/src/pages/ScheduleResults.tsx == 1; header literals match ScheduleExportService.java:280's {\"Shift Template\",\"Agents (Usual Shift)\"} verbatim"
        status: pass
    human_judgment: true
    rationale: "A desk with an unusually large shift library producing a long, readable ranked list, and an operator-authored long template name wrapping cleanly, are both backstop truths per 17-UI-SPEC.md E2 overflow/long-text -- unverifiable without a frontend test harness."
  - id: D4
    description: "ConstraintWeightsData's index signature widened to Score | number, and three new rows (Usual Shift Consistency, Usual Shift Consistency Tolerance, Preferred Start (Shift Mode)) added consecutively immediately before Minimum Staffing, with the tolerance band rendered as a single minutes input and a neutral Minutes badge instead of Hard/Soft"
    requirement: CONS-02
    verification:
      - kind: other
        ref: "cd frontend && npm run build; the three CONSTRAINTS entries appear consecutively before minStaffingWeight; DEFAULTS gains matching entries (2 soft / 60 / 1 soft, matching 17-04's V49 defaults); grep confirms the tolerance row's value is never read through the Record<string, Score> cast"
        status: pass
    human_judgment: true
    rationale: "Whether the Minutes badge and the merged single-input cell read as clearly a different kind of field from the surrounding Hard/Soft score rows -- rather than a broken score row -- is the E3 populated backstop truth Component Specifications §3 exists to satisfy, unverifiable without a frontend test harness."
  - id: D5
    description: "D-10 precedence copy rendered verbatim as a full-width informational row directly beneath the third new row, stating that Usual Shift Consistency decides first and Preferred Start (Shift Mode) only breaks ties"
    requirement: CONS-06
    verification:
      - kind: other
        ref: "grep -q \"decides first\" frontend/src/pages/ConstraintWeightsPage.tsx; the precedence <tr> is returned in the same Fragment as the preferredStartShiftModeWeight row, i.e. immediately after it in source/render order"
        status: pass
    human_judgment: false

duration: "~21 min"
completed: 2026-09-17
status: complete
---

# Phase 17 Plan 5: Drift Report Tab & Constraint Weights Configuration Summary

**Drift Report tab (per-agent-date table + Most-Subscribed Usual Shifts ranking) and three new Constraint Weights rows including the page's first non-score field, both wired to the backend contract shipped by plans 17-01 through 17-04.**

## Performance

- **Duration:** ~21 min
- **Started:** 2026-09-17T21:30:00Z (approx)
- **Completed:** 2026-09-17T21:51:04Z
- **Tasks:** 2 completed
- **Files modified:** 3

## Accomplishments
- Added the `DriftReportEntry`/`DriftSummary`/`ShiftPopularityEntry`/`DriftReport` interfaces to `client.ts` beside the `PreferenceReport` family, and `driftReport: DriftReport | null` to `ScheduleDetail` — copied verbatim from the plan's `<interfaces>` block, which mirrors 17-03's shipped backend shape.
- Added the **Drift Report** tab to `ScheduleResults.tsx` between Preference Report and Constraint Violations: a `DriftTab` component that branches once at the top on `schedule.schedulingMode !== 'SHIFT'` (mirroring `AgentAllocationTab`'s existing discipline), rendering a single not-applicable message covering both sections on a slot-scheduled desk, and for a shift desk a date-filtered four-value summary bar, the D-11 live-recomputation note (always rendered), the per-agent-date table with a three-state Status column extending `PreferenceTab`'s honoured/not-honoured colour conditional to a third `NO_USUAL_SHIFT` branch, and the Most-Subscribed Usual Shifts popularity ranking rendered in server order with no client-side sort or re-derivation.
- Widened `ConstraintWeightsData`'s index signature from `{ [key: string]: Score }` to `{ [key: string]: Score | number }` — the minimum change needed to admit a plain-integer field alongside 22 existing `Score` fields.
- Added three rows to `ConstraintWeightsPage.tsx`'s `CONSTRAINTS` array, consecutively and immediately before `minStaffingWeight`: `consistentStartWeight` (Usual Shift Consistency), `consistencyToleranceMinutes` (Usual Shift Consistency Tolerance — the page's first non-score field, rendered with a neutral `Minutes` badge and a single merged two-column input+suffix cell, read/written outside the `Record<string, Score>` cast), and `preferredStartShiftModeWeight` (Preferred Start (Shift Mode)). `DEFAULTS` gained matching entries mirroring 17-04's shipped V49 values (2 soft / 60 minutes / 1 soft).
- Rendered the D-10 precedence copy verbatim as a full-width informational row directly beneath the third new row, making CONS-06's ordering something the operator reads rather than reverse-engineers from relative weights.

## Task Commits

Each task was committed atomically:

1. **Task 1: The Drift Report tab** - `b7dc32c` (feat)
2. **Task 2: Three new Constraint Weights rows and the precedence note** - `c5e6912` (feat)

**Plan metadata:** (this commit)

## Files Created/Modified
- `frontend/src/api/client.ts` — `DriftReportEntry`/`DriftSummary`/`ShiftPopularityEntry`/`DriftReport` interfaces, `ScheduleDetail.driftReport`, and `ConstraintWeightsData`'s widened index signature
- `frontend/src/pages/ScheduleResults.tsx` — `'drift'` tab (union member, tab-bar entry, label, content branch) and the new `DriftTab` function
- `frontend/src/pages/ConstraintWeightsPage.tsx` — three new `CONSTRAINTS`/`DEFAULTS` entries, the tolerance-band row's own render branch, and the D-10 precedence-note row

## Decisions Made
- Drift Report's summary bar is computed from the date-filtered entry set client-side, not `report.summary`, because the initial `schedules.get()` fetch never sends a `date` query param — `report.summary` reflects the whole schedule and would silently disagree with the filtered rows on screen otherwise. This is a deliberate divergence from `PreferenceTab`, which uses `report.summary` unfiltered.
- The tolerance-band row stays inside the same `CONSTRAINTS.map(...)` loop as every other row, special-cased by `key` before it would reach the `Record<string, Score>` cast, rather than forking a second render path — the plan explicitly allows "a branch keyed on the row's kind."
- The D-10 precedence note is emitted as a second `<tr>` inside a `React.Fragment` keyed off `preferredStartShiftModeWeight`, returned from the same `.map(...)` call rather than a second pass over `CONSTRAINTS`.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 17 is complete — this was its last plan. All seven of this plan's requirement IDs (`CONS-02`, `CONS-03`, `CONS-06`, `DRFT-01`, `DRFT-02`, `DRFT-04`, `XCUT-01`) are shared with earlier plans in this phase and are now ready to mark `Complete` per the shared-ID readiness rule, since every plan declaring them has finished. `./gradlew test` re-confirmed green (no backend file touched by this plan). `cd frontend && npm run build` exits 0 with no TypeScript errors after both tasks.

The four `verification: backstop` visual truths from `17-UI-SPEC.md` (Status column distinctness, long shift-library list readability, long template-name wrapping, and the Minutes-badge/merged-cell read as a different field kind) remain unconfirmed by assertion — this repo has no frontend test framework, as recorded in every phase since 13. They route to `human_needed` at verify time via the `human_judgment: true` coverage entries above; the plan's own `<human-check>` blocks list the exact manual steps (open a shift-desk schedule and the Constraint Weights page).

---
*Phase: 17-consistency-constraint-drift-reporting*
*Completed: 2026-09-17*

## Self-Check: PASSED

- `frontend/src/api/client.ts`, `frontend/src/pages/ScheduleResults.tsx`, `frontend/src/pages/ConstraintWeightsPage.tsx` verified present and modified on disk.
- Both commit hashes (`b7dc32c`, `c5e6912`) verified present via `git log --oneline`.
- All task-level `<acceptance_criteria>` re-run and passing: `cd frontend && npm run build` exits 0 for both tasks combined; `'drift'` literal count is 4 (>=3 required); `Drift Report` present; `Most-Subscribed Usual Shifts` appears exactly once; `dangerouslySetInnerHTML` count is 0 in both modified page files; `#ef4444` count unchanged (0 before, 0 after) in `ScheduleResults.tsx`; no `.sort()` call inside `DriftTab`; `driftReport` present in `client.ts` with `ScheduleDetail` declaring it nullable; the three new `CONSTRAINTS` entries sit consecutively immediately before `minStaffingWeight` in the specified order; the tolerance-band row's value is never read through the `Record<string, Score>` cast; the `Minutes` badge text is literal with no `Hard`/`Soft` on that row; the D-10 precedence copy appears verbatim after the third new row in source order; `DEFAULTS` gained exactly three new entries matching 17-04's V49 values.
- Plan-level `<verification>` re-run: `cd frontend && npm run build` exits 0; `./gradlew test` green (109 test-result files, no backend file touched); the six main-table headers and two popularity headers are byte-identical to `ScheduleExportService.java`'s `Drift Report` sheet headers (`writeDriftReport`, lines 248 and 280).
