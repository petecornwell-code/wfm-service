# Phase 21: Overnight Shift Templates - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-02
**Phase:** 21-overnight-shift-templates
**Areas discussed:** Reachability & the day-start control, What OVNT-05 actually refuses, The continuation indicator & calendar-span label, The UI grid's slot ordering

---

## Todo cross-reference

Three `todo.match-phase` hits were presented. The operator first selected two to fold
("Blank upload template, one sheet per desk" and "Cross-agent seat displacement"), then was shown the
scope and risk those carry — the displacement todo's own file forbids folding into an in-flight phase
and requires a benchmark re-run plus new undo-correctness coverage; the upload template changes the
workbook structure a destructive parser accepts.

| Option | Description | Selected |
|--------|-------------|----------|
| Note both for the roadmap, not Phase 21 | Record in CONTEXT.md deferred with reasoning; raise as candidate phases after Phase 22 | ✓ |
| Fold the upload template only | Shared file argument with OVNT-06's export work | |
| Fold both, as originally selected | Phase 21 takes overnight + solver displacement + upload template | |

**User's choice:** Note both for the roadmap, not Phase 21.
**Notes:** Reversal of the initial selection once the risk was laid out. Both remain pending todos; the
deferral reasoning is recorded in CONTEXT.md's Reviewed Todos section so a future phase does not
re-derive it.

---

## Reachability & the day-start control

### How far does the day-start control go in Phase 21?

| Option | Description | Selected |
|--------|-------------|----------|
| Editable, all four refusals surfaced | Time picker plus error surfacing for sub-minute precision, 15-minute boundary, ACCEPTED-schedule block, and the generation-time tiling refusal | ✓ |
| Editable, save-time refusals only | The three `DeskService` refusals; tiling left to the FTE upload screen | |
| Dropdown of safe values, no error surfacing | Select limited to 15-minute boundaries; two refusals unreachable by construction | |
| Stays read-only — defer the control again | Prove overnight by test and API only; control moves to a later phase | |

**User's choice:** Editable, all four refusals surfaced.
**Notes:** `DeskManagement.tsx`'s own in-code comment already assigns this to "the overnight-template
phase", and 20-CONTEXT D-03 deferred it here explicitly. The four refusals already exist server-side,
so this is surfacing work rather than validation work.

### What should happen when an operator saves `22:00–06:00` on a desk whose day starts at `00:00`?

| Option | Description | Selected |
|--------|-------------|----------|
| Refuse, but say why — name the desk's day start | Keeps the locked anchor model; replaces a message that blames the operator's times | ✓ |
| Refuse exactly as today — no message change | OVNT-01 becomes test-only; `validate` untouched | |
| Accept it on any desk | Infer "ends next day" from inverted times | |

**User's choice:** Refuse, but say why — name the desk's day start.
**Notes:** The third option was identified during the discussion as the per-template `ends_next_day`
flag arriving implicitly — the mechanism `REQUIREMENTS.md`'s scoping table rejected, since a desk would
then hold two day boundaries at once.

### What should changing a desk's day start do to its existing shift library?

| Option | Description | Selected |
|--------|-------------|----------|
| Refuse the change, naming the templates that would break | Fifth refusal on `setDayStart`, mirroring `switchSchedulingMode`'s named-blocker pattern | ✓ |
| Allow it, but report which templates are now invalid | Change succeeds; response lists newly-invalid envelopes | |
| Allow silently — today's behaviour | Templates stay checked on save only | |

**User's choice:** Refuse the change, naming the templates that would break.
**Notes:** Surfaced during the discussion, not inherited. Worked example: a `14:00–23:00` template is
valid at a `00:00` anchor and becomes offsets 1020 → 120 at a `21:00` anchor, which
`anchoredIsForwardWithinDay` rejects — while `anchoredDurationMinutes` still returns 540 by wrapping,
so nothing visibly breaks.

### How should the UI handle a desk that already has an accepted schedule?

| Option | Description | Selected |
|--------|-------------|----------|
| Disable the control and say why, keep the error path wired | Blocking schedule named; server error still surfaced for races | ✓ |
| Leave it enabled, surface the error when it fires | One error path, no second source of truth | |
| Disable it, no explanation | Greyed out, no reason given | |

**User's choice:** Disable the control and say why, keep the error path wired.
**Notes:** `setDayStart` refuses this unconditionally with deliberately no bypass — `MIGR-04` owns the
documented reversal and is deferred out of v1.5 — so the constraint is permanent and worth disclosing
before the operator tries.

### Operator interrupt, mid-area: "I want 15, 30 and 60 minute slots"

Checked and reported: already enforced at `TimeslotGeneratorService:96-97`
(`incrementMinutes must be 15, 30, or 60`), inferred per call from the FTE spreadsheet header rather
than stored as desk state. The interrupt surfaced a seam nobody had named: the day-start control
accepts any 15-minute boundary, but `requireDayStartTiles` demands `dayStart % incrementMinutes == 0`
at generation, so `21:15` saves and then refuses to generate on a 30- or 60-minute desk.

| Option | Description | Selected |
|--------|-------------|----------|
| Warn at save using the desk's last-known increment | Read `getLiveBounds`' increment where timeslots exist; warn, do not refuse | ✓ |
| Accept silently, surface the refusal at generation | Keep the two refusals cleanly separated as designed | |
| Restrict the picker to hour boundaries | `:00` values tile all three increments by construction | |

**User's choice:** Warn at save using the desk's last-known increment.
**Notes:** Confirmed the interrupt meant "all three stay supported" rather than a request about the
picker's own granularity. Recorded in CONTEXT.md as a constraint the phase must not narrow.

---

## What OVNT-05 actually refuses

### Which check should OVNT-05 actually be?

| Option | Description | Selected |
|--------|-------------|----------|
| Envelope must fit the desk's operating window | Refuse an envelope outside `[bounds.startTime(), bounds.endTime()]`; reads the dead `endTime()` | ✓ |
| Both — operating window plus an explicit business-day assertion | Discharges OVNT-05's literal wording with one extra test | |
| Keep the literal wording — business-day fit only | Cheap; the check can never fail | |

**User's choice:** Envelope must fit the desk's operating window.
**Notes:** Established during the discussion that the requirement as worded is near-vacuous — at any
anchor every `(start, end)` pair maps to offsets in `[0, 1440]`, so business-day fit holds by
construction once the envelope runs forward. Also corrected the inherited record: the v1.3 deferred
item quotes an "It will still save" advisory that actually belongs to the contracted-hours mismatch
finding at `ShiftLibraryValidationService:682`; no envelope-vs-window advisory exists at all.

### Should the operating-window check apply to every template, or only overnight ones?

| Option | Description | Selected |
|--------|-------------|----------|
| Blocking for overnight, advisory for same-day | Zero regression risk; surfaces the rest of the v1.3 gap without breaking anything | ✓ |
| Blocking for every template | Closes the gap entirely; could refuse edits to stored templates on live desks | |
| Blocking for overnight only, nothing for same-day | Strictly the ROADMAP's scope; silent about the other half | |

**User's choice:** Blocking for overnight, advisory for same-day.
**Notes:** The blocking-for-all option was flagged as needing a survey of live templates first, since
`dev` is the live system with real tenant data.

### Should anything bound how long a shift template can be?

| Option | Description | Selected |
|--------|-------------|----------|
| A fixed cap in `ShiftTemplateService.validate` | One visible condition, not inside `DayWindow`; 16h proposed | ✓ |
| No cap — operating-window containment is the only bound | Fewest moving parts; a 24/7 desk accepts a 22-hour template | |
| A per-desk maximum shift length | Genuinely desk policy; schema change plus new control and refusal | |

**User's choice:** A fixed cap in `ShiftTemplateService.validate`.
**Notes:** Motivated by a regression the re-anchoring introduces quietly: `22:00–20:00` on a `21:00`
desk is offsets 60 → 1380 and saves as a 22-hour shift. Placement outside `DayWindow` honours
19-CONTEXT D-11, which rejected a max-span bound there as desk policy rather than interval arithmetic.

### Where should the OVNT-05 refusal live?

| Option | Description | Selected |
|--------|-------------|----------|
| Save path and the mode-switch gate, sharing one predicate | Refuses where the operator is, and satisfies the "library validation" wording | ✓ |
| Save path only | A template that cannot exist cannot reach the library | |
| Mode-switch gate only | Matches the wording; catches pre-existing rows; late feedback | |

**User's choice:** Save path and the mode-switch gate, sharing one predicate.
**Notes:** Reuses the one-computation-two-callers structure `ShiftLibraryValidationService` already
documents as existing "so the report and the refusal can never disagree."

### Should generated suggestions be held to the same rule?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — a suggestion must always be saveable | Run `generateSuggestion` output through the same predicate | ✓ |
| No — suggestions stay advisory | Save-time refusal is the single gate | |

**User's choice:** Yes — a suggestion must always be saveable.
**Notes:** Verified that `generateSuggestion` is the only path bypassing the save-path rules — the only
two `ShiftTemplate` save sites are `ShiftTemplateService:107` and `:126`, both behind `validate()`.

---

## The continuation indicator & calendar-span label

Opened with a measured correction: `ScheduleOutputService` keys `agentSchedule` on `getBusinessDate()`
(`:173`, `:323`, `:745`), so the Roster sheet's columns are business days, an overnight shift already
occupies one cell, and OVNT-06's "morning-after cell" does not exist on that surface.

### What should an overnight shift's Roster cell read?

Three full cell-and-legend renderings were presented side by side.

| Option | Description | Selected |
|--------|-------------|----------|
| Spell out both weekdays in the cell | `Sun 22:00-Mon 06:00`; self-describing; column width 16 → ~22 | ✓ |
| Keep the times, add a marker plus a legend row | `22:00-06:00 (+1)`; column width unchanged; legend lookup | |
| Dates in the cell, span in the column header | Most complete disclosure; two surfaces to keep correct | |

**User's choice:** Spell out both weekdays in the cell.
**Notes:** Selected from the rendered preview, which is reproduced verbatim in CONTEXT.md D-12 and
should be treated as the contract for the cell and its legend row.

### OVNT-06's "continuation indicator on the morning-after cell" has no cell to sit on. How should the documents handle that?

| Option | Description | Selected |
|--------|-------------|----------|
| Amend both ROADMAP criterion 4 and OVNT-06's wording | Restate against what is true; precedent in 20-CONTEXT D-04/D-07/D-14 | ✓ |
| Amend the ROADMAP criterion only | Fixes what the verifier reads; leaves the requirement text wrong | |
| Leave both, record the discrepancy in CONTEXT.md | Cheapest; what D-14 chose against | |

**User's choice:** Amend both ROADMAP criterion 4 and OVNT-06's wording.

### What should the Excel Allocation sheet do for an overnight shift?

| Option | Description | Selected |
|--------|-------------|----------|
| Order slot columns from the desk's day start | One contiguous run; identical output on a `00:00` desk | ✓ |
| Keep clock order, mark the wrap | No reordering risk; shift still reads as two runs | |
| Leave the Allocation sheet alone this phase | Smaller export diff | |

**User's choice:** Order slot columns from the desk's day start.

### What should OVNT-07 do with the two timeslot labels?

Presented with the hazard that the label is parsed back as data in two places —
`ScheduleExportService:832-837` and `ScheduleResults.tsx:391`, both splitting on the first space.

| Option | Description | Selected |
|--------|-------------|----------|
| Add structured date fields, then change the label | Explicit `businessDate`/`calendarDate` on the DTO; re-point both parsers first | ✓ |
| Change the label, fix both parsers in the same commit | Smaller diff; contract enforced only by a test | |
| Leave the labels calendar-only, mark D-10 final | Zero risk; OVNT-07 says "wherever it is displayed" | |

**User's choice:** Add structured date fields, then change the label.

---

## The UI grid's slot ordering

Opened by reporting two measured facts: the frontend has no test runner of any kind
(`frontend/package.json` has no test script and zero test files; `tsc -b` is the only gate), and
`ScheduleResults.tsx` carries seven distinct instances of the business-date defect class — a negative
`timeDiffMinutes`, a zero-iteration full-day loop on a wrapping window, three zero-iteration break
loops, two lexical slot sorts, and an envelope-containment test that matches nothing.

### How should the desk's anchor reach the schedule page?

| Option | Description | Selected |
|--------|-------------|----------|
| Add `dayStart` to the schedule payload | One field on `ScheduleDetailResponse` and `ScheduleSummary`; the anchor is part of the solved schedule's identity | ✓ |
| Fetch the desk separately | No new pattern; loading-order race and the wrong authority | |
| Both — payload for rendering, desk for the control | Each surface reads what is authoritative for it | |

**User's choice:** Add `dayStart` to the schedule payload.

### The frontend has no test runner. How should the seven sites be protected from regressing?

| Option | Description | Selected |
|--------|-------------|----------|
| A branded offset type — compiler-forced, no test runner | `dayWindow.ts` returning a branded `DayOffset`; all seven sites become build errors | ✓ |
| Add vitest and port the BDAY-05 structural guard | Mirrors the Java guard; new dependency and CI step | |
| Fix the seven sites, no guard | Smallest diff; the only unguarded surface in the milestone | |

**User's choice:** A branded offset type — compiler-forced, no test runner.
**Notes:** This is the type-level approach 18-CONTEXT D-03 rejected for Java on width grounds; here the
surface is one file and seven sites, and it reproduces Phase 19's compiler-forced property without a
test harness that does not exist.

### Which render branches get the anchored ordering?

| Option | Description | Selected |
|--------|-------------|----------|
| Both branches, unconditionally | Provable no-op at a `00:00` anchor; one code path | ✓ |
| Both branches, gated on a non-midnight anchor | Byte-identical by construction; creates an unexercised path | |
| SHIFT branch only | Honours P-33; leaves the SLOT branch's identical bugs | |

**User's choice:** Both branches, unconditionally.
**Notes:** SOLV-04's finding is what rules out the SHIFT-only option — `AgentAssignment`'s only planning
variable is the agent and the timeslot is fixed, so a SLOT-mode desk really can place an agent across
midnight on today's data.

### Should the grid's business-day section headers disclose their calendar span?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, matching the Roster convention | Shown only where the anchor is not `00:00` | ✓ |
| Yes, on every desk | One unconditional format; noise on current desks | |
| No — the cell-level disclosure is enough | Least change to the page | |

**User's choice:** Yes, matching the Roster convention.

---

## Claude's Discretion

Captured in full in CONTEXT.md's `Claude's Discretion` subsection. Summary: `dayWindow.ts`'s exact
type and function surface; whether it extends to the StaffingTab/PtoTab/DriftTab date columns (a
consistency question, not a correctness one — ISO date strings sort correctly); whether
`DeskManagement.tsx`'s new picker reuses it; the 16-hour value in D-08 if evidence suggests otherwise;
how the `${date}|${slot}` unfilled-seat key changes once slots carry offsets; task ordering and plan
decomposition throughout, including where the document amendments land; and whether `ScheduleSummary`
gains `dayStart` alongside `ScheduleDetailResponse`.

## Deferred Ideas

Captured in full in CONTEXT.md's `<deferred>` section. Summary: a blocking operating-window refusal for
same-day templates (needs a live-template survey first); a frontend test harness; the six-fact
`date` → `businessDate` rename; a per-desk maximum shift length; a documented day-start reversal
(`MIGR-04`); restoring a live-desk drift check; deleting the frozen `DayWindow` oracle; tightening
`ScheduleConfig`'s null-anchor fallback; and blocked-break-hours enforcement in SHIFT mode, whose fix
location is a file this phase edits but which carries no OVNT id.

One item was surfaced and deliberately **not** settled: the two Erlang calendar-date range-delete
callers that `bday-join-guard.md:169-170` hands to "a Phase 21 owner … alongside OVNT-01". Phase 20 left
them unmigrated because no desk could carry a non-midnight anchor; D-01 is what ends that
unreachability. CONTEXT.md recommends migrating them here and flags it for an explicit scope decision at
planning time.
