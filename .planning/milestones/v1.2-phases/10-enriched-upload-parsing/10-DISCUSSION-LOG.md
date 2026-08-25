# Phase 10: Enriched Upload Parsing - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-07-31
**Phase:** 10-enriched-upload-parsing
**Areas discussed:** Workbook structure, Header column contract, Blank template generation, Validation & backward-compat, P10/P11 boundary, Re-upload/removal semantics, Recurring PTO storage, Upload Results view

---

## Workbook structure

| Option | Description | Selected |
|--------|-------------|----------|
| One sheet per desk | Sheet name = desk, drop per-row Desk column; parser iterates all sheets | ✓ |
| Single flat sheet + Desk column | One sheet, Desk column names the desk (as today) | |
| Per-desk sheets, keep Desk col | Multiple sheets but Desk column authoritative | |

**User's choice:** One sheet per desk (sheet name authoritative).
**Notes:** Aligns with existing FTE upload (already sheet-name-driven).

### Unknown sheet name handling

| Option | Description | Selected |
|--------|-------------|----------|
| Skip sheet, report it | Skip whole sheet, one Upload Results entry; other sheets import | ✓ |
| Silently ignore non-desk sheets | No report entry | |
| Fail whole upload | Reject entire workbook on any unmatched sheet | |

**User's choice:** Skip sheet, report it.

---

## Header column contract

### Mon–Sun day-group naming (later superseded by the polymorphic model)

| Option | Description | Selected |
|--------|-------------|----------|
| Bare days = hours + suffixed others | Bare Monday..Sunday = hours; add `… Off` and `… PTO` groups | ✓ (then revised) |
| Explicit prefix on all three | `Hours Monday` / `Off Monday` / `PTO Monday` | |
| Grouped section headers | Two-row merged header | |

**User's choice:** Bare days = hours + suffixed others — **subsequently revised** during clarification into a single polymorphic 7-column group (see "Day-cell model" below).

### Specialization columns

| Option | Description | Selected |
|--------|-------------|----------|
| Numbered Specialty N columns | `Specialty 1..N`, prefix-detected, first = primary | ✓ |
| Single delimited column | One `Specializations` column, comma-separated | |

**User's choice:** Numbered `Specialty N` columns.

---

## Day-cell model (emerged via user clarification)

The user proposed collapsing the three Mon–Sun groups into **one** group of 7 columns where each cell value encodes status. Converged through several clarification turns:

- Each `Monday`…`Sunday` cell is **required**, always one of: number `>= 0`, `MANDATORY`, or `PTO`. **Blank is never valid.**
- User clarification: "The minimum for each row should be the bamboohr id and the mandatory days off."
- User clarification: "why dont we change days to PTO and MANDATORY and other column is set to the hours worked" → the polymorphic single-column idea.
- User clarification: "So it will never be blank - it will be a >= 0 or MANDATORY or PTO."
- User clarification on `0`: "it could be a day they dont work as a part-time worker (they work other days), remember this is a future schedule" and "0 is not available on that day either - so they should be treated as a day off."

**Resolved:** `0`, `MANDATORY`, `PTO` all = day off / not schedulable (solver); `>0` = hours worked. Keywords `MANDATORY`/`PTO`, case-insensitive.

### Keyword tokens

| Option | Description | Selected |
|--------|-------------|----------|
| MANDATORY and PTO | Matches domain language (field 4517 / solver) | ✓ |
| OFF and PTO | Shorter but less specific | |

**User's choice:** MANDATORY and PTO.

---

## Blank template generation

| Option | Description | Selected |
|--------|-------------|----------|
| In scope + shared column builder | Build template; template+parser+export share one `EnrichedColumnLayout` | ✓ |
| In scope, standalone builder | Template ships but own hard-coded headers | |
| Defer template to later | Phase 10 = parsing only | |

**User's choice:** In scope + shared `EnrichedColumnLayout`.

### Template rows (pre-seed vs empty)

| Option | Description | Selected |
|--------|-------------|----------|
| Pre-seed identity, blank day cells | Current roster identity filled; operator fills schedule + specialties | ✓ |
| Truly empty (headers only) | Zero rows, operator types everything incl. IDs | |
| Offer both downloads | Empty + pre-seeded | |

**User's choice:** Pre-seed identity, blank day cells. (Initially blocked to clarify the minimum-row rule; re-posed after the day-cell model settled.)

---

## Validation & backward-compat

### Hours bounds

| Option | Description | Selected |
|--------|-------------|----------|
| 0–24, else skip row | >24/negative/word → skip | |
| 0–24 cap, clamp >24 | Clamp >24 to 24 (later refined to warn, not silent) | ✓ |
| Any >= 0, no upper cap | Only negatives/words skip | |

**User's choice:** 0–24 cap, clamp >24 — **refined** in the Upload Results discussion to clamp **with a non-blocking warning** (not silent).

### Backward-compat (UPL-08)

| Option | Description | Selected |
|--------|-------------|----------|
| Redefine shape, retire old too | Both 6-col legacy AND old flat enriched rejected; one-time re-download | ✓ |
| Accept both layouts | Parser handles new per-desk + old flat enriched | |

**User's choice:** Redefine shape, retire old too. Revises UPL-08's literal wording.

---

## P10/P11 boundary (BambooHR interaction)

| Option | Description | Selected |
|--------|-------------|----------|
| Spreadsheet wins, overwrite | Sheet replaces field-4517 MANDATORY blocks | |
| Coexist (union of days off) | Sheet adds to BambooHR blocks; can't un-block until Phase 11 | ✓ |
| Defer decision to Phase 11 | Phase 10 stores hours only, no day-off blocks | |

**User's choice:** Coexist (union) — matches Phase 9 D-05; data-safe.

---

## Re-upload / removal semantics

| Option | Description | Selected |
|--------|-------------|----------|
| Omission = remove from desk | Keep clear-then-reimport; omitted agent unassigned + data cleared | ✓ |
| Omission = leave untouched | Only upsert present rows | |
| Additive, no clear at all | Drop clear-desk entirely | |

**User's choice:** Omission = remove from desk (justified by pre-seeded full-roster template).

---

## Recurring PTO storage

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse AgentDayOff, PTO type | Block like MANDATORY, labelled PTO | |
| New recurring-PTO structure | Dedicated table/field | |
| Let research decide | Capture constraints, defer mechanism | ✓ |

**User's choice:** Let research decide (constraints: blocks like day off, labelled PTO, no conflict with Phase 11 dated PTO).

---

## Upload Results view

| Option | Description | Selected |
|--------|-------------|----------|
| Per-sheet rollup + row skips + clamps | Per-desk summary + skip reasons + clamp/sheet warnings | ✓ |
| Keep current view, new reasons only | Reuse existing lists, silent clamps | |
| Defer results-view scope | Only per-row skips guaranteed | |

**User's choice:** Per-sheet rollup + row skips + clamps (this is what makes the clamp non-silent).

---

## Claude's Discretion

- Recurring-PTO storage mechanism (deferred to research with locked constraints — D-12).
- `EnrichedColumnLayout` API shape, header normalization, detected-but-incomplete-sheet tolerance.

## Deferred Ideas

- BambooHR↔spreadsheet per-field precedence + merge report — Phase 11 (MRG).
- Dated PTO vs recurring PTO reconciliation — Phase 11.
- Consolidating per-day `0` vs MANDATORY `AgentDayOff` into a single authority — Phase 11 (carried from Phase 9).
