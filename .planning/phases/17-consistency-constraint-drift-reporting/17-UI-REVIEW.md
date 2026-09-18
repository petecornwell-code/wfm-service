# Phase 17 — UI Review

**Audited:** 2026-09-18
**Baseline:** `17-UI-SPEC.md` (design contract)
**Screenshots:** captured — live app at `localhost:3000`, real seeded data (UAT Drift Desk, schedule `4c456b76-112c-4a92-8776-01bb52671e78`, 60 agent-days, 33/17/10... actually observed 37 drifted / 13 honoured / 10 no-usual-shift on this solve). Desktop (1440×900), tablet (768×1024), mobile (375×812) captured for the Drift Report tab; desktop-only for Constraint Weights (declared desktop-only tool). Stored at `.planning/ui-reviews/17-20260918-165037/` (gitignored).

**Method note:** this audit drove the actual running app with Playwright (not a code-only read): clicked into the Drift Report tab, read computed styles (`getComputedStyle`) for status-cell colors/weights, the tolerance badge, the precedence note, and the summary bar; exercised the date filter live; and triggered both `ConstraintWeightsPage` save-time rejections (D-07 hard-score, D-08 ordering) live to confirm the exact toast copy. The task-provided schedule ID in the brief (`f85c2a6e-...`) 404'd ("Schedule not found") — the desk's actual current schedule ID was discovered via the app's own network calls and used instead.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | All spec strings verified live byte-for-byte, including both save-time rejection toasts and the D-10 precedence note |
| 2. Visuals | 3/4 | Status-column focal point reads clearly, but the 60-row main table has no visual date-boundary cue, so an operator must re-read the Date column every row to track where one day ends and the next begins |
| 3. Color | 4/4 | Remediated WCAG AA values (#dc2626, #15803d, #6b7280) confirmed via computed style; deviation from the spec's literal `#16a34a`/`#d1d5db` is justified, documented in code, and applied app-wide |
| 4. Typography | 4/4 | Heading (18px/600), body (13.6px), and badge (12px/600) sizes all measured and match the spec exactly; no stray weight introduced |
| 5. Spacing | 4/4 | Summary bar, D-11 note, and precedence-note padding/margins all measured and match spec values exactly |
| 6. Experience Design | 3/4 | Loading/error/empty states correctly inherit the page-level mechanism and both config-save rejections work exactly as specified; but the inherited unconstrained table shell wraps cells awkwardly below ~900px, and there's no scroll or grouping aid at the stated 60-row density |

**Overall: 22/24**

---

## Top 3 Priority Fixes

1. **No date-boundary visual cue in the 60-row drift table** — at the desk's real scale (12 agents × 5 days), the Agent column repeats the same 12 names five times with only a `<tr>` boundary between the last agent of one date and the first of the next; nothing (zebra stripe, subtle top border, or a bolded date-change row) tells the eye "new day starts here." Fix: add a `borderTop: '2px solid #e5e7eb'` (or similar existing-token divider) to the first row of each date group — cheap, no new color, matches the `StaffingTab` total-row precedent of using border-top for a semantic break.
2. **Table wraps rather than scrolls below ~900px viewport width** — `frontend/src/pages/ScheduleResults.tsx:265`'s `overflowX: 'auto'` wrapper only creates a scrollbar if the `<table>` inside it refuses to shrink; because the table has `width: '100%'` and no `minWidth`, the browser compresses columns instead, wrapping "2026-09-21" onto two lines and "+180 min" onto two lines in the Date and Delta columns at tablet width. This is inherited from `PreferenceTab`'s existing shell, not new to this phase, but it is fully reproducible at a plausible operator-laptop width and materially hurts scanability of exactly the two columns (Date, Delta) that a drift report review most depends on. Fix (repo-wide, not phase-17-scoped): add `minWidth: '720px'` to the drift table (and ideally the sibling tables) so the existing `overflowX: 'auto'` container actually engages a horizontal scrollbar instead of letting cells wrap.
3. **Popularity section total is silently incomplete relative to the working desk** — the seeded desk has 12 agents but the Most-Subscribed Usual Shifts table only sums to 10 (8 Early + 2 Late); the two agents with no stored usual shift (Aurora Garcia, Ava Gonzalez, confirmed in the main table's `No usual shift` rows) correctly don't appear, per spec ("one row per template with ≥1 subscribing agent"). This is spec-compliant, not a bug — but there's no line anywhere in the section stating "N of M agents have no usual shift and are omitted here," so an operator doing arithmetic against the main table's `No usual shift: 10` figure could momentarily wonder why 8+2=10 doesn't include the desk's 12. Not a blocker (the section's own subtext already scopes the claim correctly — "reads each agent's current usual shift"), but worth a one-line follow-up: since D-13 exists specifically to make the consistency-vs-fairness tension "observable," an explicit "(N agents have no stored usual shift and are not counted here)" caption would remove the one remaining inference step.

---

## Detailed Findings

### Pillar 1: Copywriting (4/4)

- Tab label "Drift Report" placed between "Preference Report" and "Constraint Violations" — confirmed live in the tab bar screenshot.
- Summary bar renders `Working agent-days: 60`, `Honoured: 13`, `Drifted: 37`, `No usual shift: 10` on the full period, and correctly recomputes to `Working agent-days: 12` (etc.) when the date filter is set to a single day — confirmed via live `select` interaction, matching the spec's "counts reflect the currently date-filtered entry set" requirement exactly.
- D-11 note text confirmed live, character-for-character: *"This report is computed live from each agent's current usual shift. If a usual shift changes after this schedule was accepted, this report changes too — even for a schedule from the past."*
- Table headers `Agent | Date | Status | Usual Start | Actual Start | Delta (min)` confirmed live in the rendered `<thead>`, and confirmed byte-identical in `ScheduleExportService.java:254` (`{"Agent", "Date", "Status", "Usual Start", "Actual Start", "Delta (min)"}`) — D-14's cross-surface requirement holds.
- Status values `Drifted` / `Honoured` / `No usual shift` confirmed live, exact case and wording.
- Section 2 heading "Most-Subscribed Usual Shifts" and subtext "Reads each agent's current usual shift, not this solve's results — it does not change when you re-solve." both confirmed live.
- Both `ConstraintWeightsPage` error toasts triggered live and confirmed byte-for-byte:
  - D-07: *"Usual Shift Consistency's hard score must be 0 — a hard score would risk making an otherwise-feasible schedule infeasible."*
  - D-08: *"Preferred Start (Shift Mode) weight must be lower than Usual Shift Consistency's weight."*
- D-10 precedence copy confirmed live in the rendered table row: *"Usual Shift Consistency decides first. Preferred Start (Shift Mode) only breaks ties where Usual Shift Consistency scores two shifts equally — its weight must be lower than Usual Shift Consistency's, checked when you save."*
- No generic "Submit"/"Click Here"/"OK" labels found in either touched file; the phase adds no button at all on surface 1 and reuses the existing Save/Reset labels on surface 2, per contract.

### Pillar 2: Visuals (3/4)

- Focal point works: `Drifted` renders at weight 600 in `#dc2626`, `Honoured` at weight 400 in `#15803d`, `No usual shift` at weight 400 in `#6b7280` — three genuinely distinct visual weights/colors, confirmed via computed style on live rows, and the Drifted rows do draw the eye first when scanning the real 60-row table (verified against the full-page screenshot).
- Popularity section correctly uses no color coding and no rank number — position alone is the signal, matching D-13's stated intent, and reads cleanly with only 2 populated rows (Early Shift: 8, Late Shift: 2).
- Tolerance-band row on `ConstraintWeightsPage` is visually distinct from the surrounding Hard/Soft rows at a glance — the merged single input plus "Minutes" gray badge does read as "a different kind of field," confirmed against the full-page screenshot; this was called out as the load-bearing, unverifiable-without-a-harness claim in the spec's own backstop list, and it holds up under a real look.
- **Deduction:** the main drift table, at the stated 60-row real-world scale, has zero visual aid marking where one date's block of 12 rows ends and the next begins — no border, no background shift, no bolding on the first row of a new date. The operator's only cue is re-reading the Date cell on every single row. This isn't called out anywhere in the spec's UI Considerations (E1's "overflow" and "long-text" rows are dismissed, but there's no row addressing multi-day density specifically), and it is a real scanability gap once the table is populated at the size the phase's own test fixture produces.
- Table header shading (`#f3f4f6`) is present but subtle — confirmed via computed style (`th` background, not `thead` background — the shading is applied per-cell) and visually correct, matching `StaffingTab`'s existing convention.

### Pillar 3: Color (4/4)

- Computed styles confirm the WCAG-remediated values are what's actually shipping, not just what the commit log claims:
  - `Drifted`: `rgb(220, 38, 38)` = `#dc2626`, weight 600 — matches spec.
  - `Honoured`: `rgb(21, 128, 61)` = `#15803d`, weight 400 — **deviates from the spec's literal `#16a34a`**, but this is the known, already-shipped, app-wide accessibility fix (commit `8782d9c`), and the deviation is correctly documented in-code with a detailed comment at `ScheduleResults.tsx` explaining the contrast-ratio math (3.30:1 → 5.02:1). Correct call, not a defect.
  - `No usual shift`: `rgb(107, 114, 128)` = `#6b7280`, weight 400 — **deviates from the spec's literal `#d1d5db`**, same remediation, same in-code justification (1.47:1 was illegible; #6b7280 measures 4.83:1). Correct call, not a defect.
- Tolerance-band "Minutes" badge confirmed live: `background: rgb(229, 231, 235)` (#e5e7eb), `color: rgb(55, 65, 81)` (#374151) — exact match to spec's Neutral badge token.
- Precedence note box confirmed live: `background: rgb(249, 250, 251)` (#f9fafb) — exact match to spec's Informational box token, correctly distinct from the amber warning box (not used, per spec's explicit ruling).
- No new `#3b82f6` (accent) usage found on either surface — the Drift Report tab has no button, and the existing tab-bar mechanism (unchanged) is the only accent user, as specified.
- `#ef4444` (destructive) confirmed absent from both new surfaces via grep and visual check — the Hard-level badge on `ConstraintWeightsPage`'s new rows correctly uses the existing `#dc2626`/`#fef2f2` pair, not the destructive token.

### Pillar 4: Typography (4/4)

- "Most-Subscribed Usual Shifts" heading measured live: `fontSize: 18px`, `fontWeight: 600` — exact match to the Heading role (18px/600).
- Body table text measured live at `13.6px` (browser's rendering of `0.85rem` at a 16px root — this is the same value the whole page already uses elsewhere, confirmed not phase-specific).
- D-11 note measured live: `fontSize: 12px` (`0.75rem`), `fontStyle: italic`, color `#6b7280` — exact match.
- Minutes badge measured live: `fontSize: 12px`, `fontWeight: 600` — matches the existing Level-badge shape's size/weight exactly.
- No third font weight introduced — only 400 and 600 appear anywhere in either touched file's new code, confirmed by reading the row-rendering branches directly.

### Pillar 5: Spacing (4/4)

- Summary bar measured live: `padding: 12px`, `gap: 24px` (`1.5rem`), `background: #f9fafb`, `borderRadius` present — exact match to the `PreferenceTab`-mirroring spec block.
- D-11 note measured live: `marginBottom: 12px` (`0.75rem`) — exact match.
- Precedence note measured live: `padding: 12px`, `borderRadius: 6px`, `marginTop: 4px` (`0.25rem`) — exact match to the spec's literal style object.
- Section-2 gap (`marginTop: '1.5rem'`) confirmed in source at `ScheduleResults.tsx` — exceeds the `md` (16px) token as the spec explicitly permits ("matching the existing section-gap rhythm").
- No arbitrary/unexplained spacing values found in the new code beyond the two documented house exceptions (12px, 24px) already established by prior phases.

### Pillar 6: Experience Design (3/4)

- Loading and error states correctly have no independent implementation — confirmed by reading `DriftTab`'s top of function: no `useEffect`, no fetch, purely a function of the already-loaded `schedule` prop, so the page's existing top-level loading/error gates cover it exactly as claimed.
- Both `ConstraintWeightsPage` save-time rejections (D-07, D-08) triggered live end-to-end (filled an invalid value, clicked Save, read the resulting toast) and both fired correctly with exact copy — this is a real functional guarantee, not just a documented intent.
- Date filter on the Drift Report tab exercised live: selecting a single date correctly narrows both the summary bar and the table body to that date's 12 rows, with the same `dateFilter` mechanism every sibling tab uses — no separate filter control was added, as specified.
- Zero-entries and slot-scheduled empty-state copy verified by direct code read (both single-`<p>` returns, matching sibling tabs' pattern) rather than by live toggle, per the task's explicit instruction not to leave the desk switched to SLOT mode — this is a reasonable and deliberate audit-scope trade-off, not a gap in the audit.
- **Deduction:** the inherited table shell (`width: '100%'`, no `minWidth`) causes the Date and Delta columns specifically — the two columns a drift review depends on most — to wrap onto two lines at tablet width (768px) and worse at mobile width (375px), rather than the `overflowX: 'auto'` container's intended horizontal-scroll behavior taking over. The spec correctly scopes this app as "desktop-only, no touch-target minimum," so this isn't a contract violation, but it is a real, reproducible degradation at a width many operators' laptops actually use, and it isn't called out anywhere in the spec's UI Considerations as a known/accepted trade-off the way other edge cases are.

---

## Files Audited

- `frontend/src/pages/ScheduleResults.tsx` (`DriftTab` function, tab-bar wiring, `activeTab` union) — read in full and exercised live
- `frontend/src/pages/ConstraintWeightsPage.tsx` (`CONSTRAINTS` array, tolerance-band render branch, precedence-note render branch, `DEFAULTS` map) — read in full and exercised live
- `src/main/java/com/wfm/service/ScheduleExportService.java` (`writeDriftReport`) — read, headers cross-checked against the live table
- Live app: Drift Report tab (desktop/tablet/mobile), Constraint Weights page (desktop), both save-time error paths, date filter interaction — all exercised via Playwright against `localhost:3000`/`localhost:8080`
