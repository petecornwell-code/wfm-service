# Phase 16: Usual Shift Storage - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-03
**Phase:** 16-Usual Shift Storage
**Areas discussed:** Template reference & era drift, Upload column shape, Write-path policy (USHF-05), Roster & export surface

---

## Template reference & era drift

### Q1 — What does the stored reference mean when its era is superseded?

| Option | Description | Selected |
|--------|-------------|----------|
| FK + resolve live era by name | Store `shift_template_id` as a real FK; the resolver reads the stored row's NAME and returns whichever era is effective on the date. Follows `AgentShiftAssignment`'s `templateName` + `sourceTemplateId` precedent | ✓ |
| FK, frozen to the stored era | The stored row IS the target, times and all; era edits silently invalidate targets across the roster | |
| Name only, no FK | Cheapest to keep valid across eras, but drops the referential integrity the roadmap explicitly required | |

**User's choice:** FK + resolve live era by name
**Notes:** Operator-language test — "Ana's usual shift is Early"; if Early's hours change, Ana's target moves with it. → D-01

### Q2 — Template retired outright, no successor era

| Option | Description | Selected |
|--------|-------------|----------|
| Resolves to nothing — same as unset | Stored row survives as history; resolution returns empty; USHF-04's no-penalty path handles it; retirement never blocked | ✓ |
| Resolves to nothing and warns the operator | Same, plus reporting how many usual shifts a retirement strands, at retirement time | |
| Refuse the retirement while agents reference it | Blocks a routine library edit on data invisible from the shift-library screen | |

**User's choice:** Resolves to nothing — same as unset
**Notes:** Avoids a third state for Phase 17; keeps Phase 14's T-14-14 guarantee that retirement is never blocked by references. → D-02

### Q3 — Weekday-mask violation on save

| Option | Description | Selected |
|--------|-------------|----------|
| Reject with a 400 | A flat contradiction with a single-field fix, knowable at pick time; picker simply won't offer it; mirrors `setDayHours`' reject-not-clamp | ✓ |
| Accept with an advisory warning | Literal consistency with Phase 14 D-06 | |
| Accept silently, filter at resolution | Makes a typo indistinguishable from a deliberate blank — the audit I-1 failure shape | |

**User's choice:** Reject with a 400
**Notes:** Deliberate divergence from D-06, with the distinction stated rather than silent. → D-03

### Q4a — Usual shift on a non-working weekday

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, stored and inert | The models stay orthogonal — hours decide IF, usual shift decides WHICH; target survives pattern changes | ✓ |
| Reject — refuse the write | Couples the two editors; setting MANDATORY would need a cross-model write Phase 13 kept out | |
| Accept, but flag it in the roster | Preserves the target and makes the oddity legible; more UI for a state arguably not wrong | |

**User's choice:** Yes, stored and inert → D-04

### Q4b — Contracted-hours mismatch

| Option | Description | Selected |
|--------|-------------|----------|
| Advisory only — follow D-06 | A moving judgement; mode switch and pre-solve gate already block where it matters | ✓ |
| Reject with a 400 | Consistent with Q3, but fails upload rows on arithmetic the operator may be mid-fix on | |
| Store silently, no advisory | Operator learns far from the edit that caused it | |

**User's choice:** Advisory only — follow D-06 → D-05

---

## Upload column shape

### Q1 — How usual shift arrives in the workbook

| Option | Description | Selected |
|--------|-------------|----------|
| Seven columns, `Usual Shift Mon…Sun` | Same `DAY_ORDER` loop as the day-hours group; full per-weekday expressiveness; ~28 columns before specialties | ✓ |
| One column, applied to every worked weekday | Matches USHF-02's literal wording; cannot express Mon–Thu/Fri splits and would flatten them on re-import | |
| One column plus optional per-day overrides | Handles both cases at the cost of an in-sheet precedence rule and 8 new columns | |

**User's choice:** Seven columns
**Notes:** PROJECT.md's own worked example (S1 Mon–Thu, S2 Fri) is unrepresentable in one column. → D-06

### Q2 — Meaning of a blank cell

| Option | Description | Selected |
|--------|-------------|----------|
| Blank = no usual shift, and valid | USHF-04 makes "none" a first-class state, unlike hours; deliberate, stated divergence from Phase 10 | ✓ |
| Blank is invalid, like the day cells | Uniform, but forces every operator to supply a usual shift before any upload succeeds | |
| Blank = leave the stored value untouched | `clearDesk` makes "untouched" mean something different on the upload path; clearing becomes impossible | |

**User's choice:** Blank = no usual shift → D-07

### Q3 — Unknown template name in a cell

| Option | Description | Selected |
|--------|-------------|----------|
| Skip the cell, warn, import the rest of the row | A bad optional field must not cost the agent's valid identity/specialty/hours data | ✓ |
| Whole-row skip, consistent with D-09 | Maximum parser consistency; one typo costs a whole row | |
| Fail the whole sheet | Far too blunt for one optional cell | |

**User's choice:** Skip that cell, warn, import the rest → D-08

### Q4a — The wipe-on-re-upload hazard

| Option | Description | Selected |
|--------|-------------|----------|
| Pre-fill the seven Usual Shift cells with stored values | Re-upload round-trips as a no-op; blank then genuinely means "clear it" | ✓ |
| Pre-fill the whole schedule group, hours included | Fixes the same hazard for hours, but re-opens a Phase 10 decision outside this scope | |
| Leave blank; warn on upload when it would clear values | A `confirm()`-shaped mitigation — the pattern audit I-3 showed operators click through | |

**User's choice:** Pre-fill the seven Usual Shift cells
**Notes:** Hazard surfaced from combining D-07 (blank = none) with `clearDesk` and the blank-schedule-cells template. → D-09

### Q4b — Constraining cell input

| Option | Description | Selected |
|--------|-------------|----------|
| Excel dropdown of the desk's live template names | Sheet-scoped POI data validation; makes the unknown-name path rare | ✓ |
| Free text, parser validates | Simplest; makes typos the normal case on an exact-catalog-name column | |

**User's choice:** Excel dropdown
**Notes:** Explicitly does not replace parser validation — a pasted value bypasses it. → D-10

---

## Write-path policy (USHF-05)

### Q1 — Does `clearDesk` wipe usual shifts?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — wipe, mirroring `agent_day_hours` | Desk-scoped data, like the hours/preferences/exceptions it already deletes | ✓ |
| No — preserve across re-import | Safer against loss, but creates a state the model calls impossible | |
| Wipe, but report the count in Upload Results | Same behaviour, made legible in the existing rollup | |

**User's choice:** Yes — wipe
**Notes:** Only safe because D-09 makes the template round-trip the values. The two decisions are load-bearing for each other. → D-11

### Q2 — Agent moved to another desk via the roster UI

| Option | Description | Selected |
|--------|-------------|----------|
| Cleared on desk change | Same shared helper `clearDesk` calls — one implementation, two callers (D-08) | ✓ |
| Kept, but resolves to nothing | Nothing destroyed by a mis-click; stores rows the model calls invalid | |
| Refuse the desk change while usual shifts exist | The same block-a-routine-action shape rejected for template retirement | |

**User's choice:** Cleared on desk change → D-12

### Q3 — `SHIFT → SLOT` mode switch

| Option | Description | Selected |
|--------|-------------|----------|
| Untouched — mode switch stays a one-column write | Preserves MODE-04's proof and D-12's non-destructive toggle | ✓ |
| Cleared on `SHIFT → SLOT` | Tighter data model; makes the pilot's escape hatch destructive | |

**User's choice:** Untouched → D-13

### Q4 — Form of the USHF-05 verification

| Option | Description | Selected |
|--------|-------------|----------|
| Table + a test per path + a structural completeness guard | The guard fails when a new writer appears without a table row — the Phase 14 reflection / Phase 10 D-16 trick | ✓ |
| Table + a test per path | Satisfies the criterion as written; nothing catches the eighth write path added later | |
| Table + one end-to-end test crossing all paths | Reads like the operator's story; a failure anywhere fails everything | |

**User's choice:** Table + per-path tests + structural guard
**Notes:** Without the guard the table is true only on ship day — how audit I-2 survived two audits. → D-14

---

## Roster & export surface

### Q1 — Where usual shift lives in the roster

| Option | Description | Selected |
|--------|-------------|----------|
| A second line inside the existing seven day tiles | Tiles already own one weekday each; no new column, no new affordance | ✓ |
| A separate seven-tile row below the hours tiles | Visually distinct, doubles the expanded row's height | |
| A new collapsed-row column plus tile detail | Best discoverability; a 15th column on an already-scrolling table | |

**User's choice:** Second line inside the existing day tiles
**Notes:** Density is a known constraint here (G-13-8). → D-15

### Q2 — How many tile states

| Option | Description | Selected |
|--------|-------------|----------|
| Three: never set / live / stored-but-not-in-effect | Merges retired and non-working-day; keeps "never set" distinct, as DRFT-02 will need | ✓ |
| All four, each visually distinct | Most informative, most states to specify and keep consistent | |
| Two: has a value / doesn't | Collapses the ambiguity the retirement decision relies on being visible | |

**User's choice:** Three states → D-16

### Q3 — Inline editing mechanic

| Option | Description | Selected |
|--------|-------------|----------|
| A native `<select>` of live template names | No datalist trap, no `cellDirtyRef` guard, cannot produce an unknown name; explicit "— none —" option | ✓ |
| Reuse the hours cell's input + datalist pattern | Consistent with the neighbour; re-imports a documented trap (G-13-DD) to solve a problem this field doesn't have | |
| A searchable combobox | New component; unnecessary at current library sizes | |

**User's choice:** Native `<select>` → D-17

### Q4 — Export columns

| Option | Description | Selected |
|--------|-------------|----------|
| Seven columns from the same `DAY_ORDER` loop, after the hours group | Round-trips with the upload template; First/Last Name shift right by seven, as in Phase 13 P-09 | ✓ |
| Seven columns, only when the desk has a shift library | Phase 15's byte-identical trick; a conditional shape the parser can't rely on | |
| One summary column | Compact but cannot round-trip | |

**User's choice:** Seven columns from the same loop → D-18

---

## Claude's Discretion

- One shared resolution service rather than repeating `resolvePreferences`' existing duplication
  across `SolverService` and `ScheduleService`
- Migration number (V47 pending confirmation of the live head)
- Table/entity naming, DTO shape, endpoint paths, test-file organisation
- Whether the inline write is one endpoint per weekday or another HTTP shape — criterion 3 locks the
  choke-point requirement, not the transport

## Deferred Ideas

- Pre-filling the day-hours cells too, making the whole workbook a round-trip document (Phase 10
  scope)
- A desk-wide "set all days to…" bulk action for usual shifts (the hours equivalent is audit I-3,
  still open at 999.9)
- Warning at template-retirement time how many usual shifts a retirement strands (a new obligation
  on Phase 14's editor)
- A searchable combobox for template selection, if libraries ever grow past a handful

## Todos reviewed, not folded

- `2026-07-30-blank-upload-template-one-sheet-per-desk.md` — Phase 10 scope, appears delivered by
  UPL-09
- `2026-08-13-cross-agent-seat-displacement.md` — solver work; its frontmatter says keep it unlinked
- `2026-08-14-terraform-db-password-drift.md` — infrastructure, unrelated
