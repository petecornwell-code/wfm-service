# Phase 13: Per-Day Hours Visibility - Context

**Gathered:** 2026-08-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Make the authoritative per-day contracted-hours model (`agent_day_hours`) **visible and
directly editable** to the operator, so the roster and the Excel export stop reporting a value
unrelated to what was uploaded.

This phase closes the v1.2 milestone audit's critical finding (I-1 / F-1) plus two adjacent
findings (I-3, I-4). It changes **readers and one editor**; it does not change the storage
schema, the parser, or the solver.

**In scope:**
- `DeskAgentService.toResponse` and `DeskAgentExportService` resolve from `agent_day_hours`
- Roster UI surfaces Mon–Sun hours with `MANDATORY` / `PTO` / not-set distinguished
- Per-weekday editing replaces the destructive single-value fan-out as the primary edit path (I-3)
- Specialty headers sourced from the shared column definition (I-4)

**Out of scope:**
- **I-2** — the manual "Refresh from BambooHR" button bypassing `AgentMergeService`. A scoping
  decision (route it through the merge engine, or document that the merge-report guarantee
  covers uploads only), not a defect. Deferred.
- Removing the `Agent.contractedHoursPerDay` scalar. It stays live by decision (Phase 9 D-05 /
  V29 migration comment). This phase changes who **reads** it, not whether it exists.
- Any change to `agent_day_hours` schema, the upload parser, or solver resolution.

</domain>

<decisions>
## Implementation Decisions

### Placement — where per-day hours appear

- **D-01:** The roster (`frontend/src/pages/DeskAgents.tsx`) gets an **expandable row per agent**
  showing the seven weekdays inline beneath them. Chosen over a side panel, tooltip, or separate
  view because the operator's task is a post-upload verification glance, and an expandable row
  lets several agents be compared without navigation. The table is already 13 columns wide, so
  seven additional top-level columns were rejected outright.
  — **Reversibility:** reversible — a self-contained UI affordance on one page.

- **D-02:** The **Excel export gains seven Mon–Sun columns** mirroring the upload template's shape
  and header order, sourced from `EnrichedColumnLayout` (which already exposes `dayHeader(day)`).
  This makes export → fill → re-upload a true round-trip, completing the intent behind Phase 10
  D-14's pre-seeded template. Rejected: locally-defined export headers (reintroduces exactly the
  drift UPL-09 exists to prevent) and a single summary column (breaks round-tripping).
  — **Reversibility:** costly — the export shape is an operator-facing file format; once
  operators round-trip against it, changing the column set breaks their saved workbooks.

### Cell rendering and editing

- **D-03:** Each weekday cell is a **type-or-pick combo**: the operator can type any value the
  upload accepts (**0–24**, quarter-hour steps) **or** pick `PTO`, `MANDATORY`, or `Not set
  (default)` from the list. A plain integer dropdown was rejected on evidence — the upload clamps
  at **24** (`DeskAssignmentUploadService.java:693-695`, so `24.00` is reachable) and preserves
  fractions (`hours` is `precision=5, scale=2`; parser does `setScale(2, HALF_UP)` at `:697`),
  and the existing inline editor already uses `step="0.25"`. An integer list could not display an
  agent whose upload set `7.5`.
  — **Reversibility:** reversible — control shape only; the stored values are unchanged.
  — **AMENDED 2026-08-25 (UAT, phase 13):** the picklist now ALSO carries every quarter-hour value
  from `0` to `24` inclusive (97 options), so the numeric range is pickable and not only typeable.
  Requested by the operator during UAT of the deployed build. This does **not** reverse D-03's
  original reasoning: what D-03 rejected was a *plain integer* dropdown, on the evidence that it
  could not represent `7.5`. A quarter-hour list resolves that exact objection rather than
  reintroducing it — `7.5` is present, as is every value the parser's `setScale(2, HALF_UP)` can
  produce at quarter-hour granularity. Typing is unchanged; this only adds a pick affordance for
  values that were already legal. Ordering is load-bearing: `PTO`, `MANDATORY` and `Not set
  (default)` remain FIRST so D-04's five-state affordance is not buried under 97 numeric rows.
  Still reversible — control shape only, stored values unchanged.

- **D-04:** The combo must expose **five distinct states**, because the model distinguishes them:
  a number (worked), `0` (explicitly not worked), `MANDATORY` (contractual day off), `PTO`
  (recurring weekly PTO), and **not set** (no row — falls back to the default per Phase 9 D-04).
  `Not set` is not a synonym for `0`; selecting a value must be what creates a row, so merely
  opening the combo never writes one.
  — **Reversibility:** reversible.

- **D-05:** Edits **save per cell**. One change writes exactly one `AgentDayHours` row and cannot
  disturb the other six. This is the structural property finding I-3 was missing — I-3 is closed
  by construction here, not by adding a guard to a multi-row write.
  — **Reversibility:** reversible.

### Default resolution

- **D-06:** "Not set" resolves against **`Schedule.defaultContractedHoursPerDay`** — the same
  fallback `SolverService.resolveEffectiveHours` uses (`SolverService.java:925`) — **not**
  `Desk.defaultContractedHoursPerDay`, which is what the roster reads today
  (`DeskAgentService.java:49-50`). These are different entities holding different values, so
  keeping the desk fallback would leave a smaller version of the very bug this phase exists to
  fix. **Exactly one fallback rule survives this phase.**
  — **Reversibility:** costly — the point of the phase is that screen and solver agree; undoing
  this reintroduces a second, divergent resolution rule that a future audit would have to
  rediscover.

- **OPEN — for research:** the roster is scoped to a **desk**, not a schedule, so something must
  decide *which* schedule's default to read, and what to do when a desk has zero or several
  schedules. Deliberately left to the researcher/planner to resolve against the code rather than
  guessed at in discussion. Whatever is chosen must not introduce a second fallback rule (D-06).

### Fan-out edit (Phase 9 D-10)

- **D-07:** The existing single-value fan-out **survives as an explicit, labelled "set all days"
  bulk action** — no longer the only way to edit, and it **warns before overwriting** any
  `MANDATORY` / `PTO` labels. Phase 9's D-10 required this fan-out so operator edits keep reaching
  the solver; that intent is preserved rather than superseded, and setting a uniform 8h week stays
  a one-click operation. Rejected: removing it (makes a uniform week seven edits, supersedes D-10)
  and leaving it unchanged (leaves I-3 open).
  — **Reversibility:** reversible.

### Hygiene

- **D-08:** `DeskAssignmentTemplateService.java:31-32` hardcodes `"Specialty 1"` / `"Specialty 2"`.
  Source them from `EnrichedColumnLayout`, which today exposes only the `specialtyIndex` detection
  regex (`:61`) and has no header constant or factory — so a small addition there is required.
  Closes audit finding I-4 (UPL-09 drift point).
  — **Reversibility:** reversible.

### Claude's Discretion

- Exact expand/collapse affordance, and whether the expanded row reuses the existing PTO-dates
  sub-table pattern already present in `DeskAgents.tsx`.
- Whether the summary in the collapsed `Hours/Day` cell is a range (`6–8`), a word, or a total —
  the accepted mockup showed a range with a single number when all days match, but the precise
  rendering is a planner/UI call.
- Response DTO shape for per-day hours. Phase 9 **D-12 deliberately deferred this** ("adding it
  now risks locking an API shape prematurely") — the shape is genuinely open and this phase is the
  first real consumer.
- Whether the "set all days" warning is a confirm dialog or an inline notice.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### The gap being closed
- `.planning/v1.2-MILESTONE-AUDIT.md` — the audit that produced this phase. Findings I-1, I-3, I-4
  and flow F-1, each with file:line evidence and a recommended closure. **Read first.**

### Locked upstream decisions this phase must honour
- `.planning/phases/09-agent-data-model-foundation/09-CONTEXT.md` — D-03 (resolution precedence:
  `AgentException` → per-day → schedule default), D-04 (**absent ≠ 0**), D-05 (scalar and per-day
  coexist; "not worked" is the union of a `MANDATORY AgentDayOff` and a per-day `0`), D-09
  (`agent_day_hours` storage contract: absent = no row, not-worked = a `0.00` row), D-10 (fan-out
  requirement), **D-12 (per-day hours deliberately withheld from `DeskAgentResponse` until a real
  consumer existed — this phase is that consumer)**.
- `.planning/phases/10-enriched-upload-parsing/10-CONTEXT.md` — D-05 (`0`, `MANDATORY` and `PTO`
  are all "not working" to the solver but **descriptively distinct**), D-10 (hours 0–24, fractional
  allowed, >24 clamped non-silently), D-13 (template/parser/export share one `EnrichedColumnLayout`
  — the constraint behind D-02 and D-08), D-16 (spreadsheet cannot un-block a BambooHR day off).

### Phase-level record
- `.planning/ROADMAP.md` § "Phase 13: Per-Day Hours Visibility" — goal, scope, audit evidence and
  the three known design constraints for planning.

### No external specs or ADRs
The project has no `docs/adr/` or external spec directory; all constraints are captured in the
planning artifacts above and in the code references below.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`SolverService.resolveEffectiveHours`** (`SolverService.java:913-925`) — `static`, takes
  `(exceptionMap, dayHoursMap, date, scheduleDefaultHours)`. The correct, already-tested
  resolution. **Reuse it rather than writing a second implementation** (ROADMAP constraint). Note
  it resolves per *date*, not per *weekday*, so a Mon–Sun summary means calling it per weekday or
  reading the day-hours map directly.
- **`EnrichedColumnLayout`** (`src/main/java/com/wfm/util/EnrichedColumnLayout.java`) — already
  exposes `dayHeader(day)` (used by the parser's clamp message at
  `DeskAssignmentUploadService.java:695`) and `specialtyIndex(...)` (`:61`). The shared source for
  D-02 and D-08.
- **`AgentDayHoursRepository`** — already injected into `DeskAgentService` (`:28`) but used only by
  the write path (`:210`). The read path needs no new wiring, just use.
- **PTO-dates sub-table** in `DeskAgents.tsx` (`~:454`) — an existing in-row detail pattern the
  expandable row can follow.

### Established Patterns
- Roster hours cell is click-to-edit inline with `step="0.25"` (`DeskAgents.tsx:352-365`) — the
  quarter-hour granularity in D-03 is already the house convention, not a new idea.
- Clamp/skip warnings surface as non-blocking amber notices (Phase 10 D-11), the precedent for any
  warning the "set all days" action shows.

### Integration Points
- `DeskAgentService.toResponse` (`:71-93`) — **the defect site.** Computes
  `getContractedHoursPerDay() ?: deskDefault` and never reads the per-day model. Called from three
  places (`:66`, `:125`, `:180`), so all three inherit the fix.
- `DeskAgentExportService` (`:26-60`) — "Contracted Hours Per Day" / "Effective Contracted Hours
  Per Day" columns.
- `DeskAgentService.setContractedHours` (`:183-219`) — the fan-out; `deleteByAgent_Id` +
  seven uniform rows with `dayOffType` unset (`:206-217`) is what erases labels today.
- `DeskAssignmentUploadService.clearDesk` (`:561`) — nulls the scalar on every re-import. **Do not
  assume the scalar is non-null**; five sites write it (`BambooRefreshService:244`,
  `DeskAgentService:143`/`:198`, `DeskAssignmentUploadService:561`, `DeskService:147`).

</code_context>

<specifics>
## Specific Ideas

The operator proposed the per-cell dropdown directly ("can we have a dropdown for each cell 0-23,
PTO, MANDATORY") and, on being shown that the upload clamps at 24 rather than 23, confirmed the
range as **0–24**. This is the shape to build toward: seven independent per-day controls, each
carrying both the numeric value and the day-off label in one affordance.

Accepted mockup for the expanded row:

```
Hours/Day  [ 6-8   v ]   <- collapsed summary, click to expand

  expanded:
  Mon Tue Wed  Thu Fri Sat Sun
  8   8   MAND 8   4   PTO PTO

  [ Set all days to: ___ ]  (bulk action, warns before overwriting labels)
```

Per-cell control:

```
Wed  [ 7.5        v ]
     +------------------+
     | (type a number)  |
     | PTO              |
     | MANDATORY        |
     | Not set (default)|
     +------------------+
```

</specifics>

<deferred>
## Deferred Ideas

- **I-2 — manual "Refresh from BambooHR" bypasses the merge engine.** `BambooRefreshService`
  (`:224-234`) overwrites seven identity fields with no precedence rule and no `MergeReportEntry`;
  it holds zero references to `AgentMergeService`. Not a defect but a scoping decision: either
  route it through the merge engine, or document that MRG's provenance guarantee covers the upload
  path only. Needs its own discussion.
- **Retiring the `Agent.contractedHoursPerDay` scalar.** Once nothing reads it, removing it becomes
  tractable — but that is a migration touching five write sites, and Phase 9 D-05 deliberately kept
  it. A future cleanup phase.

### Reviewed Todos (not folded)
- `2026-07-30-blank-upload-template-one-sheet-per-desk.md` — matched at 0.9 on keywords but is
  **stale**: it was folded into UPL-09 and delivered by Phase 10 (`DeskAssignmentTemplateService`
  + the download endpoint). Recommend closing it rather than carrying it forward.
- `2026-08-13-cross-agent-seat-displacement.md` — solver work, the Phase 12 successor. Unrelated
  to this phase despite the keyword match.
- `2026-08-14-terraform-db-password-drift.md` — infrastructure. Unrelated.

</deferred>

---

*Phase: 13-per-day-hours-visibility*
*Context gathered: 2026-08-21*
