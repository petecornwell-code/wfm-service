# Phase 18 — UI Review

**Audited:** 2026-09-30
**Baseline:** No UI-SPEC.md exists for this phase — audited against abstract 6-pillar standards, scoped strictly to the phase's actual diff (`git diff 440fa93~1..HEAD -- frontend`, 15 added lines across `frontend/src/api/client.ts` and `frontend/src/pages/DeskManagement.tsx`).
**Screenshots:** Not captured by this audit — the orchestrator supplied live-verified facts (2026-09-30, https://d2bbtcc80peap7.cloudfront.net/desk-management) that are treated as established per instructions, and this audit does not contradict them.

**Scope note:** This phase touches one table column on an existing page. Four of six pillars have effectively nothing phase-attributable to score — this is stated explicitly below rather than papered over with an invented finding. Only Copywriting and Experience Design (specifically, disclosure/affordance) carry real, phase-caused content.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 2/4 | Correct in intent, but the shipped value renders `00:00:00` next to a parenthetical that says `00:00` — a visible mismatch the user accepted at UAT, not a defect this audit invents but one worth scoring down |
| 2. Visuals | 4/4 | N/A to this diff — no icons, no focal point, no new visual hierarchy introduced; existing table styling untouched |
| 3. Color | 4/4 | N/A to this diff — zero new color classes, zero hardcoded colors added |
| 4. Typography | 4/4 | N/A to this diff — no new font-size or font-weight classes; cell inherits the table's existing type scale |
| 5. Spacing | 4/4 | N/A to this diff — new `<td>`/`<th>` use the table's existing cell padding; column geometry confirmed unchanged between header/edit/display rows |
| 6. Experience Design | 2/4 | Disclosure-only cell is correctly non-interactive by design (D-26), but ships as a bare `<td>` with zero focusable descendants and no semantic marker (no `aria-disabled`, no `title`, no distinguishing style) separating it from an ordinary read-only value cell |

**Overall: 20/24** (of which 4 pillars are pass-by-inapplicability, not verified excellence)

---

## Top 3 Priority Fixes

1. **`00:00:00` vs `00:00` mismatch in the same cell** — user impact: an operator reads a time value with seconds precision immediately followed by parenthetical text asserting a different-looking value (`00:00`), which reads as a bug even though it isn't one — concrete fix: format `desk.dayStart` client-side (e.g. `dayStart.slice(0,5)`) before interpolating it into the cell, rather than passing the raw `HH:mm:ss` string from the backend through unformatted. One line in `DeskManagement.tsx`.
2. **No semantic distinction for a disclosure-only cell** — user impact: sighted operators can infer non-editability visually (no input rendered), but there is nothing in the DOM (no `aria-readonly`, no `title`, no muted/disabled styling) telling assistive tech or a quick visual scan that this cell is categorically different from `Scheduling Mode`'s read-only cell, which at least reads as a short label rather than a value-plus-caveat sentence — concrete fix: wrap the restriction text in a `<span className="hint">`/muted-color treatment so the caveat visually recedes from the value, and consider `aria-label` on the cell for screen readers given the sentence-length content packed into a table cell.
3. **Restriction sentence repeated per row with no visual de-emphasis** — user impact: on a desk list of any size the identical 47-character parenthetical repeats in every row of a dense column, adding reading load for no new information after the first row — concrete fix: this is explicitly deferred (BDAY-04 deletes the cell within one phase per D-26/D-28), so no fix is being requested now — flagging only so it isn't rediscovered as a "new" finding in Phase 19 planning.

---

## Detailed Findings

### Pillar 1: Copywriting (2/4)
- `frontend/src/pages/DeskManagement.tsx` (both new `<td>` cells, edit and display branches): renders `{desk.dayStart} (only 00:00 is supported until overnight scheduling lands)`.
- The copy itself is good practice — it states the restriction in on-screen text rather than only a code comment or tooltip, matching D-26's requirement, and it names the mechanism (overnight scheduling) rather than a bare "not supported."
- However, the verified live rendering is `00:00:00 (only 00:00 is supported until overnight scheduling lands)` — the backend serializes `LocalTime` as `HH:mm:ss`, and the frontend does zero formatting before display. The value and its own parenthetical explanation don't visually agree on precision. This is UAT-accepted (per D-28, no frontend test framework, and the user reviewed this exact mismatch and passed it) — but "accepted at UAT" and "correct copywriting" are different bars. Scored 2/4 on copy quality, not defect severity.
- No other copy (labels, empty/error states, CTAs) was touched by this diff.

### Pillar 2: Visuals (4/4 — not phase-attributable)
- No icons, no new focal points, no layout change beyond adding one column. The diff is purely textual content in existing table cells. There is nothing to fail here because there is nothing new to fail; scored full marks by default rather than by demonstrated excellence.

### Pillar 3: Color (4/4 — not phase-attributable)
- `grep` of the diff shows zero new Tailwind/CSS classes and zero hardcoded color values. The project doesn't appear to use Tailwind on this page at all (inline `style={{}}` objects); the new cells add no styling of any kind.

### Pillar 4: Typography (4/4 — not phase-attributable)
- No new font-size or font-weight declarations. The cell text inherits the ambient `<td>` styling. Nothing to score.

### Pillar 5: Spacing (4/4)
- Verified by the orchestrator's live measurement: column geometry is identical between header, edit-branch row, and display-branch row — the table does not shift when a row is edited. This is a genuine, phase-caused correctness property (the plan explicitly required equal column counts across both branches, and it held). This is the one "4" that reflects verified work rather than inapplicability.

### Pillar 6: Experience Design (2/4)
- Correctly implements the disclosure-not-enforcement design decision (D-26): the cell is a plain `<td>`, confirmed by live measurement to have 0 focusable descendants in both branches across all 4 production desks — no fake-disabled input, no dead click handler, no false affordance. This is the right choice per T-18-02-04's threat disposition and avoids the worse alternative (a `disabled` input implying a control that does nothing).
- However, execution is minimal: the cell carries no accessibility signal beyond visible text. A `title` attribute is confirmed null (copy is on-screen, which is what D-26 required, but the two aren't mutually exclusive — a `title` restating the on-screen text costs nothing and helps zoomed/truncated views). No `aria-` attributes distinguish this from a generic value cell. This is a defensible minimal implementation for a cell Phase 19 deletes, not a fully-realized disclosure pattern.
- No loading/error/empty states are relevant to this diff — the cell simply renders a field already present on the `Desk` object returned by the existing `desks.list()` call; no new async state was introduced.

---

## Files Audited
- `frontend/src/pages/DeskManagement.tsx` (lines ~89–133, the two new `<td>` cells and the `<th>Day Start</th>` header)
- `frontend/src/api/client.ts` (lines ~106 `Desk.dayStart` field, ~331 `desks.setDayStart` method — `setDayStart` itself is unused by any current UI control, since the cell is display-only; this is expected per D-26/D-27 and not a finding)

Registry audit: not applicable — no `components.json` in this repository, shadcn is not in use.
