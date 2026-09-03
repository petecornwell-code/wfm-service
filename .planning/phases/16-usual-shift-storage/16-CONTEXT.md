# Phase 16: Usual Shift Storage - Context

**Gathered:** 2026-09-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Store each agent's **usual shift per weekday** — a catalog-valued *target*, not a solved result —
populate it from the per-desk upload template and from inline roster editing, and make it visible
everywhere agent scheduling data is displayed (roster and Excel export).

This phase stores, writes, resolves and displays. It does **not** make the solver read the value:
the consistency constraint, tolerance band, weight, and drift report are all Phase 17. Nothing here
touches `solverConfig.xml`, the constraint provider, or `AgentShiftAssignment`.

Depends on Phase 14 only (needs a valid `ShiftTemplate` FK target). Independently shippable relative
to Phase 15.

</domain>

<decisions>
## Implementation Decisions

### Template reference & era resolution

- **D-01:** `AgentUsualShift` stores a real FK `shift_template_id` (per the roadmap's
  `agent_id, day_of_week, shift_template_id` shape, unique on `(agent_id, day_of_week)`), **but the
  resolution service resolves by NAME**: it reads the stored row's template name and returns
  whichever era of that name is effective on the date being asked about. Rationale: Phase 14 made
  the era key `(tenant_id, desk_id, name, effective_from)` with no delete endpoint, so an operator
  editing "Early" creates a *new era* rather than mutating the row — and in operator language
  "Ana's usual shift is Early" must move with Early. In-codebase precedent: `AgentShiftAssignment`
  already carries `templateName` and `sourceTemplateId` side by side.
  — **Reversibility:** costly — switching to frozen-era semantics later changes what every stored
  row means and would need a data migration to re-point rows at the era live at their write time,
  plus a rewrite of Phase 17's comparison basis.

- **D-02:** When no era of the stored name is effective on a date (template retired outright, no
  successor), resolution **returns nothing — identical to unset**. The stored row survives untouched
  as history. USHF-04 already makes "no usual shift" a penalty-free state, so a retired target
  degrades into an existing state rather than a third one Phase 17 must handle. Retirement is never
  blocked or gated by the existence of referencing rows — Phase 14 (T-14-14) deliberately made
  retirement the one library edit that cannot be blocked by downstream references.

- **D-03:** Setting a usual shift for a weekday excluded by the template's `valid_weekdays` mask is
  **rejected with a 400**, on both the inline path and (as a cell-level skip) the upload path. This
  deliberately does *not* follow Phase 14's D-06 advisory-on-save precedent, and the distinction is
  stated: a contracted-hours mismatch is a moving judgement about a library the operator is still
  shaping, whereas a weekday-mask violation is a flat contradiction with a single-field fix that is
  knowable at pick time. Same reject-not-clamp posture `DeskAgentService.setDayHours` already takes
  on out-of-range hours (`DeskAgentService.java:311`). The inline picker therefore does not offer
  invalid templates at all.

- **D-04:** A usual shift **may** be stored on a weekday the agent does not work (`0` hours,
  `MANDATORY`, or `PTO` in `agent_day_hours`), stored and inert. The two models stay orthogonal:
  `agent_day_hours` decides *if* the agent works, the usual shift decides *which shift* when they
  do. Keeps the two editors uncoupled — setting a day to MANDATORY must not have to reach into a
  second table, which is exactly the cross-model write Phase 13 kept out of `setDayHours`.

- **D-05:** A template whose net working duration does not match the agent's effective contracted
  hours for that weekday is **advisory only**, following Phase 14 D-06. The mode switch
  (`requireShiftModeReady`) and the pre-solve seat-supply gate already block this where it actually
  matters.

### Upload column shape

- **D-06:** The upload workbook gains **seven columns, `Usual Shift Mon` … `Usual Shift Sun`**,
  generated from the same `EnrichedColumnLayout.DAY_ORDER` loop the day-hours group already uses, so
  template / parser / export remain one definition (D-13). USHF-02's literal wording is "a column",
  but USHF-01 is per-weekday and PROJECT.md's own worked example is "Ana can be `S1` Mon–Thu and
  `S2` Fri" — a single column cannot express it, and would silently flatten a split roster on
  re-import.
  — **Reversibility:** costly — the workbook shape is an operator-facing contract; collapsing seven
  columns back to one later means every operator re-downloads the template again, the same
  disruption v1.2 spent a decision on when it retired the flat enriched shape.

- **D-07:** A **blank Usual Shift cell means "no usual shift", and is valid.** This deliberately
  diverges from Phase 10's blank-day-cell-is-invalid rule, for a stated reason: USHF-04 makes "no
  stored usual shift" a first-class penalty-free state, which hours never had, and there is no
  keyword equivalent of `MANDATORY`/`PTO` meaning "deliberately none".

- **D-08:** A Usual Shift cell naming a template not in that desk's library (typo, wrong desk,
  retired and gone) **skips that cell, warns, and imports the rest of the row** — it does not
  trigger Phase 10's D-09 whole-row skip. A bad optional field must not cost the operator the
  agent's valid identity, specialty and hours data. Reported in the existing per-sheet warnings
  block alongside clamp and unmatched-sheet notices, naming the agent, the weekday, and the
  unresolved name.

- **D-09:** The generated per-desk template **pre-fills the seven Usual Shift cells with stored
  values.** This closes a hazard the other decisions create: the template leaves schedule cells
  blank (Phase 10), the upload calls `clearDesk` first
  (`DeskAssignmentUploadService.java:273`), and D-07 makes blank mean "none" — so without pre-fill,
  an operator downloading the template to fix one agent's hours would wipe every stored usual shift
  on the desk. With pre-fill, a re-upload round-trips as a no-op and blank genuinely means "clear
  it". **The day-hours cells are deliberately left as they are** — changing those is Phase 10 scope
  and re-opens a v1.2 decision.

- **D-10:** The generated template attaches a **sheet-scoped Excel data-validation dropdown** listing
  the desk's live template names to each Usual Shift column. Makes D-08's unknown-name path rare
  rather than routine. It does **not** replace parser validation — a pasted value bypasses Excel
  validation, so the skip-cell-and-warn rule must still hold and must still be tested.

### Write-path policy (USHF-05 / XCUT-02)

- **D-11:** `clearDesk` **wipes usual shifts**, mirroring what it already does to `agent_day_hours`,
  preferences and exceptions. Usual shift is desk-scoped data that has no meaning once the agent
  leaves the desk. This is only safe because D-09 makes the template round-trip stored values, so
  the upload re-supplies what `clearDesk` cleared — the two decisions are load-bearing for each
  other and must ship together.

- **D-12:** Moving an agent to a different desk through the roster UI **clears their usual shifts**,
  through the *same* clear-usual-shifts helper `clearDesk` calls — one implementation, two callers,
  per Phase 14's D-08 discipline. Keeps "references a template from their own desk's library"
  unconditional rather than a guarantee the resolver has to re-check.

- **D-13:** A `SHIFT → SLOT` mode switch leaves usual shifts **untouched**; the switch stays the
  single-column write MODE-04 proved field-by-field. Clearing them would make a deliberately
  non-destructive, undialogued action (Phase 14 D-12) destructive at exactly the moment an operator
  reaches for the pilot's escape hatch. "Unchanged" is the USHF-05 table's ruling for this path and
  is proven the same field-by-field way MODE-04 was.

- **D-14:** The USHF-05 deliverable is **a table, plus one test per path that actually exercises
  that path, plus a structural completeness guard** that fails when a new writer of the usual-shift
  table appears without a corresponding table row. Paths to enumerate: upload, inline edit, BambooHR
  refresh, `clearDesk`, desk move, mode switch, and the solver itself. The guard is the point — the
  Phase 14 reflection-derived constraint-classification test and Phase 10's D-16 reflection guard are
  the in-codebase precedents. Without it the table is true only on ship day, which is precisely how
  audit I-2 survived two consecutive audits.

### Roster & export surface (USHF-06 / XCUT-01)

- **D-15:** Usual shift renders as **a second line inside the existing seven day tiles** in the
  roster's expanded row (`frontend/src/pages/DeskAgents.tsx:604`), not as a new collapsed-row column
  and not as a separate tile strip. Each tile already owns one weekday; hours and usual shift are
  read and edited together. Watch the density — this surface is already tight (G-13-8 accepted
  `Not set (default)` clipping at 90px rather than widen it).

- **D-16:** The tile distinguishes **three states**: never set / set and live / stored-but-not-in-
  effect. The last merges "template era retired" (D-02) and "weekday not worked" (D-04) into one
  muted treatment showing the stored name with its reason, because the operator's next action is the
  same in both. Keeping "never set" visually distinct from "set to something not in effect" is the
  distinction audit I-1 was about, and the one DRFT-02 will need in Phase 17.

- **D-17:** Inline editing uses a **native `<select>` of the live template names**, with an explicit
  `— none —` option so clearing is a first-class choice rather than a blank. It deliberately does
  *not* reuse the neighbouring hours cell's `<input>` + `<datalist>` pattern: that pattern exists to
  work around G-13-DD (a seeded input collapses the datalist to its single self-matching option,
  hence the open-empty-with-placeholder behaviour and the `cellDirtyRef` guard), and a closed
  small value set has none of that problem. A `<select>` also structurally cannot produce the
  unknown-name case.

- **D-18:** The Excel export gains **seven columns from the same `DAY_ORDER` loop, immediately after
  the existing day-hours group** (`DeskAgentExportService.java:42`), so export / template / parser
  stay one shape and an exported sheet is directly re-uploadable. Known consequence: `First Name` /
  `Last Name` shift right by seven columns — the same kind of index move Phase 13's P-09 made when
  it took them from 13/14 to 20/21, so existing tests of that shape are the model to follow.
  Conditional columns (the Phase 15 "keep a slot desk byte-identical" trick) were rejected here
  because this export must round-trip with the upload template.

### Claude's Discretion

- **The resolution service is one implementation with multiple callers.** `resolvePreferences`
  currently exists twice — `SolverService.java:567` and `ScheduleService.java:486`, the latter
  commented "Mirrors the logic in SolverService.resolvePreferences". The roadmap calls the usual-
  shift resolver "a near-copy of `resolvePreferences`'s standing-vs-weekly precedence *shape*"; copy
  the shape, not the duplication. One service, called by everyone, per D-08 discipline.
- Migration number: schema head on disk is **V46**, so the next is V47 — confirm the actual
  latest-applied version before writing it, per the project's own recorded discipline.
- Table/entity naming, DTO shape, endpoint paths, and test-file organisation follow existing
  conventions (`AgentDayHours` / `AgentDayHoursRepository` / `DeskAgentController` are the models).
- Whether the inline write is one endpoint per weekday (mirroring `setDayHours`'s narrowly-scoped
  shape, which criterion 3 asks for) or something else — the criterion locks the *choke-point*
  requirement, not the HTTP shape.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope and requirements
- `.planning/ROADMAP.md` §"Phase 16: Usual Shift Storage" — goal, 5 success criteria, the three
  Notes (the `AgentDayHours`-shaped table, the I-2/I-1 repeat warning, and target-vs-result
  separation)
- `.planning/REQUIREMENTS.md` §"Usual Shift (USHF)" — USHF-01…06 verbatim; §"Cross-Cutting
  Requirements" — XCUT-01 (display verified in every surface) and XCUT-02 (every reachable write
  path), both of which this phase carries
- `.planning/PROJECT.md` §"Current Milestone" — the "Stored usual shift per agent, per weekday" and
  "Two population paths" target features; §"Key Decisions" for D-06, D-08, D-12, D-13, I-1/I-3
  history

### Prior-phase decisions this phase inherits
- `.planning/phases/14-shift-library-scheduling-mode/14-CONTEXT.md` — D-06 (advisory on save,
  blocking at mode switch), D-08 (one implementation, two callers), D-11 (era identity + app-level
  non-overlap), D-12 (SHIFT→SLOT unconditional, no dialog)
- `.planning/phases/15-shift-envelope-breaks-library-generation/15-CONTEXT.md` — how the shift
  model's target-vs-result separation actually landed
- `.planning/phases/14-shift-library-scheduling-mode/XCUT-05-constraint-classification.md` — the
  constraint table; this phase adds no constraints but must not invalidate it
- `.planning/milestones/v1.2-MILESTONE-AUDIT.md` — findings I-1 (model built, view never migrated)
  and I-2 (guarantee held on one write path only). Success criteria 4 and 5 exist because of these
  two specifically

### Handoff / open items
- `.planning/phases/15-shift-envelope-breaks-library-generation/HANDOFF.md` — suite baseline and
  runtime budget
- `.planning/phases/15-shift-envelope-breaks-library-generation/deferred-items.md` — what Phase 15
  explicitly left open, so this phase does not accidentally inherit it

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/main/java/com/wfm/model/AgentDayHours.java` — the exact shape to model `AgentUsualShift` on:
  `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id","day_of_week"}))`,
  `tenant_id` column, `@ManyToOne(fetch = LAZY)` agent, `@Enumerated(STRING) DayOfWeek`
- `src/main/java/com/wfm/service/DeskAgentService.java:289` `setDayHours` — the narrowly-scoped
  single-weekday choke point criterion 3 asks the usual-shift write to mirror, including its
  tenant+desk-scoped agent resolution *before* any repository call (T-13-05, the IDOR guard), its
  `upsertDayHoursRow` reuse-or-create helper, its reject-not-clamp validation, and its explicit
  `flush()` + re-read so the response reflects the write
- `src/main/java/com/wfm/util/EnrichedColumnLayout.java` — `DAY_ORDER`, `dayHeader(DayOfWeek)`, and
  the "no enriched-shape header string literal anywhere else" rule. The seven new headers belong
  here and nowhere else
- `src/main/java/com/wfm/model/ShiftTemplate.java` — FK target: `name`, `startTime`, `endTime`,
  `validWeekdaysMask` (`CHAR(7)`), `effectiveFrom`, `effectiveTo`
- `src/main/java/com/wfm/model/AgentShiftAssignment.java` — precedent for carrying `templateName`
  alongside `sourceTemplateId` (D-01), and the *result* side of the target/result separation
- `frontend/src/pages/DeskAgents.tsx:604` — the seven day tiles; `DAY_ORDER`/`DAY_LABELS` are
  exported from here (Phase 14 did this so `ShiftLibrary.tsx` could import rather than re-declare)

### Established Patterns
- **One layout definition, three consumers** (Phase 10 D-13): template generator, parser and export
  all resolve headers/order from `EnrichedColumnLayout`. Phase 13 had to close the last two
  hardcoded literals (audit I-4) — do not add new ones.
- **One computation, multiple callers** (Phase 14 D-08): the report an operator reads and the
  refusal that blocks them are the same code. Applies to the usual-shift resolver and to the
  clear-usual-shifts helper (D-11/D-12).
- **Reflection/structural completeness guards** rather than counts a developer can edit
  (Phase 14 plan 02, Phase 10 D-16) — the model for D-14's guard.
- **Reject, don't clamp**, on interactive single-cell writes; the bulk upload parser clamps with a
  non-silent warning instead (`setDayHours` P-04).
- **Migration-vs-entity drift is not caught by the default suite** — `application-test.yml` sets
  `flyway.enabled: false` with `ddl-auto: create-drop` on H2. `src/test/java/com/wfm/support/
  PostgresBackedTest.java` (Phase 15, `d5b4169`) runs real Flyway + `ddl-auto=validate` via
  Testcontainers, but *only for classes that extend it*. V39 shipped a `CHAR(7)`/`varchar(7)`
  mismatch through a fully green suite. A new table in this phase should extend it.

### Integration Points
- New Flyway migration (V47, confirm the live head first) — `agent_usual_shift` table with
  `agent_id` + `day_of_week` unique, `shift_template_id` FK, `tenant_id`
- `DeskAssignmentUploadService` — the parser's header map, the day-cell loop, `clearDesk:550`,
  and the per-sheet warnings/rollup structures
- `DeskAssignmentTemplateService` — pre-seeded template generation (D-09 pre-fill, D-10 dropdown)
- `DeskAgentExportService:42` — the `DAY_ORDER` header loop and the row writer at ~:105
- `DeskAgentService` / `DeskAgentController` — the inline write choke point and the desk-move path
- `DeskAgentResponse` — currently `Map<DayOfWeek, DayHoursEntry> dayHours`; the per-weekday usual
  shift travels alongside it (always-7-key shape, per Phase 13 P-02)
- `BambooRefreshService` — must be *proven* not to touch usual shifts (USHF-05 row), not assumed

</code_context>

<specifics>
## Specific Ideas

- The operator-language test for D-01: "Ana's usual shift is Early." If Early's hours change, Ana's
  usual shift changed with it — the stored reference follows the name, not the frozen row.
- D-09 and D-11 are load-bearing for each other. `clearDesk` wiping usual shifts is only safe
  because the template round-trips them. If either is dropped during planning, the other must be
  re-decided — do not ship one without the other.
- D-03 breaks Phase 14's D-06 advisory precedent on purpose, and the plan should say so in the code
  comment, not silently diverge: weekday-mask violation is a flat contradiction, hours mismatch is a
  moving judgement.
- D-14's structural guard is the deliverable that matters most in this phase. A table without it is
  the v1.2 audit shape all over again.

</specifics>

<deferred>
## Deferred Ideas

- **Pre-filling the day-hours cells in the generated template** (making the whole workbook a true
  round-trip document) — would fix the same latent wipe-on-re-upload hazard for contracted hours,
  but it changes Phase 10 behaviour outside this phase's scope. Worth its own decision later.
- **A desk-wide "set all days to…" bulk action for usual shifts** — the equivalent hours bulk action
  is the one that destroys MANDATORY/PTO labels (audit I-3, still open at 999.9). Not built here.
- **Warning the operator at template-retirement time how many usual shifts it strands** (the
  rejected second option under D-02) — a real usability improvement, but it puts a new obligation on
  Phase 14's template editor.
- **A searchable combobox for template selection** — unnecessary at current library sizes; revisit
  if a desk's live library grows past a handful.

### Reviewed Todos (not folded)
- `2026-07-30-blank-upload-template-one-sheet-per-desk.md` — matched at 0.9 on upload keywords, but
  it is Phase 10 scope and appears delivered by UPL-09 (pre-seeded per-desk template +
  `EnrichedColumnLayout`). Not folded; worth closing or re-scoping separately.
- `2026-08-13-cross-agent-seat-displacement.md` — solver work, and its own frontmatter says to keep
  it unlinked from any phase so a phase close cannot auto-sweep it away unresolved.
- `2026-08-14-terraform-db-password-drift.md` — infrastructure, unrelated to this phase.

</deferred>

---

*Phase: 16-Usual Shift Storage*
*Context gathered: 2026-09-03*
