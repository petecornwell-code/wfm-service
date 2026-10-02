# Phase 21: Overnight Shift Templates - Context

**Gathered:** 2026-10-02
**Status:** Ready for planning

<domain>
## Phase Boundary

A shift template whose envelope crosses its desk's day-start anchor is creatable by an operator,
correctly measured, correctly validated against the desk's real operating window, and rendered as
one continuous thing that discloses the calendar dates it spans.

Requirements: **OVNT-01 … OVNT-07.**

**The phase is smaller on the backend and larger on the frontend than the ROADMAP entry implies.**
Measured on HEAD during this discussion, not assumed:

1. **OVNT-01's save path already works on an anchored desk.** `ShiftTemplateService.validate:250`
   calls `window.anchoredIsForwardWithinDay`. At a `21:00` anchor, `22:00–06:00` maps to offsets
   60 → 540 — forward — so the template saves today, and `anchoredDurationMinutes` returns 480, so
   `ShiftTemplate.getNetHours` is already correct. OVNT-01's production work here is the **refusal
   message** on a `00:00` desk (D-02), not the acceptance path.
2. **OVNT-02, OVNT-03 and OVNT-04 are substantially already true post-Phase-20.** The day-off join
   at `ScheduleConstraintProvider:183` already reads `a.getTimeslot().getBusinessDate()`;
   `computeAgentDayConfigs` (`SolverService:841-870`) iterates business dates and derives contracted
   hours from the business date's own weekday; `ScheduleOutputService` keys `agentSchedule` on
   `getBusinessDate()` at `:173`, `:323`, `:745`. Their work is **proving it and discharging the
   three `@AssertsTodaysBehaviour` registry entries** (D-14), not new behaviour.
3. **The genuinely unbuilt work is OVNT-05, OVNT-06, OVNT-07, and reachability.** Plus seven
   instances of the business-date defect class in `frontend/src/pages/ScheduleResults.tsx` (D-15),
   on a frontend with no test harness at all.

**Observable behaviour change for an operator:** a desk's day start becomes editable from the desk
page for the first time, so an overnight-capable desk can be created without curl. Overnight
templates save, validate against the operating window, and render as one block naming their calendar
span.

**Not this phase:** minimum rest (REST-01..07, Phase 22); the Phil-US live migration and a documented
day-start reversal (MIGR-01..04, deferred out of v1.5); renaming `date` → `businessDate` across the
six business-date-shaped problem facts (20-CONTEXT deferred); restoring a live-desk drift check
(20-CONTEXT D-14).

</domain>

<decisions>
## Implementation Decisions

### Reachability — the day-start control (OVNT-01's precondition)

- **D-01:** **The day-start control becomes editable, with all four refusals surfaced to the
  operator.** `DeskManagement.tsx:112` and `:124` are read-only today, and their own in-code comment
  says the editable control "belongs with the overnight-template phase" — this one. 20-CONTEXT D-03
  deferred it here explicitly. The four refusals are already written and tested server-side; this is
  surfacing work, not validation work:
  1. sub-minute precision (`DeskService.setDayStart`, ordered first and deliberately so)
  2. non-15-minute boundary (`DeskService.setDayStart`)
  3. unconditional refusal when an ACCEPTED schedule exists, naming the schedule id and period
  4. `TimeslotGeneratorService.requireDayStartTiles` — fires later, at generation
  Rejected: a dropdown of safe values (makes two refusals unreachable from the UI but strands the
  ACCEPTED-schedule one); deferring the control again (ships a capability no operator can switch on,
  for the second phase running).
  — **Reversibility:** `reversible` — one cell plus its error surfacing.

- **D-02:** **A `00:00` desk still refuses `22:00–06:00`, but the message names the desk's day
  start.** The current text — `Shift template end time must be after its start time` — blames the
  operator's times when the cause is the anchor, on the one case this milestone exists to enable.
  Keeps the locked desk-anchor model: a `00:00` desk's business day genuinely ends at midnight, so
  the template is two business days and refusing is correct. Rejected: accepting it on any desk,
  which is the per-template `ends_next_day` flag `REQUIREMENTS.md`'s scoping table rejected, arriving
  implicitly — a desk would hold two day boundaries at once. Rejected: no message change, which would
  make OVNT-01 test-only and leave the misdiagnosis in place.
  — **Reversibility:** `reversible` — one message string.

- **D-03:** **Changing a desk's day start is refused when it would invalidate stored templates,
  naming them.** Templates are validated only on save, so re-anchoring can strand a row the save path
  would now reject: on a `00:00` desk `14:00–23:00` is valid, and at a `21:00` anchor its offsets
  become 1020 → 120, which `anchoredIsForwardWithinDay` rejects — while `anchoredDurationMinutes`
  still returns 540 by wrapping, so nothing visibly breaks. A fifth refusal on `setDayStart`,
  mirroring `switchSchedulingMode`, which already refuses a SHIFT-mode switch and names the uncovered
  demand windows. Rejected: allowing it with a report (leaves a row in the library that could not be
  re-saved); allowing it silently (the solver could then assign an envelope the save path rejects —
  the silent-disagreement class this milestone keeps finding).
  — **Reversibility:** `reversible` — one refusal in one method.

- **D-04:** **The control renders disabled, naming the blocking schedule, when an ACCEPTED schedule
  exists — and the server error path stays wired.** `setDayStart` refuses this unconditionally with
  deliberately no bypass (`MIGR-04` owns a documented reversal, deferred out of v1.5), so the
  constraint is permanent and the operator should learn it before trying. The error is still surfaced
  for a stale page or a concurrent accept. Satisfies Phase 18's disclosure rule — the UI never states
  something false about the backend — without pretending the race cannot happen.

- **D-05:** **The 15/30/60-minute generation increments stay supported, and the control warns when a
  day start will not tile.** `TimeslotGeneratorService:96-97` already enforces
  `incrementMinutes must be 15, 30, or 60`; the increment arrives per call, inferred from the FTE
  spreadsheet header (`FteUploadService:110-140`), and is not desk state. **Phase 21 must not narrow
  that set** — confirmed by the operator mid-discussion. The seam: the control accepts any 15-minute
  boundary (correctly — the increment is not desk state), but `requireDayStartTiles` demands
  `dayStart % incrementMinutes == 0` at generation, so `21:15` saves and then refuses to generate on
  a 30- or 60-minute desk. Where the desk already has generated timeslots, read their increment
  through `getLiveBounds` (which already returns `incrementMinutes`) and warn at save. The refusal
  itself stays where it is — this adds a warning, never a second gate.
  — **Reversibility:** `reversible` — one advisory read.

### OVNT-05 — what the refusal actually checks

- **D-06:** **OVNT-05 is envelope-vs-desk-operating-window containment, not business-day fit.** As
  worded — "envelope does not fit inside its desk's business day" — the requirement is near-vacuous:
  at any anchor every `(start, end)` pair maps to offsets in `[0, 1440]`, so once
  `anchoredIsForwardWithinDay` passes, the envelope fits the business day **by construction**. The
  check with teeth is containment within `[bounds.startTime(), bounds.endTime()]` — the v1.3 deferred
  gap whose named starting point, `TimeslotBoundsResponse.endTime()`, is confirmed still unread
  (`ShiftLibraryValidationService` uses only `startTime()` and `incrementMinutes()`, at `:192`,
  `:295-296`, `:305-307`). An overnight envelope reaching past the desk's last generated slot is seats
  that do not exist. The ROADMAP's own note points here: "do not let the old advisory-only behaviour
  persist here."

  **Correction to the inherited record, for the planner:** the v1.3 deferred item says that gap "saves
  cleanly with an advisory reading *It will still save*". The only such string in the tree is
  `ShiftLibraryValidationService:682`, which is the **contracted-hours mismatch** advisory — a
  different finding. There is no envelope-vs-operating-window advisory at all; `isTemplateAligned`
  checks grid *alignment*, never containment. The gap is real; the sentence describing it is wrong.
  — **Reversibility:** `reversible` — one predicate and its two call sites.

- **D-07:** **Blocking for overnight templates, advisory for same-day ones.** No desk has an overnight
  template today (none can), so blocking them carries zero regression risk and matches the ROADMAP's
  "for the overnight case specifically". Same-day templates get a non-blocking advisory, so the rest
  of the v1.3 gap becomes visible without breaking anything. Rejected: blocking every template — if
  any live desk's stored templates already run past their operating window, an operator editing one
  would be refused and unable to save, and **the environment named `dev` is the live system with real
  tenant data**; that option would need a survey of live templates first. Rejected: overnight-only
  with nothing for same-day, which leaves no record that the other half was considered.
  — **Reversibility:** `reversible` — the advisory/blocking split is one branch.

- **D-08:** **A fixed maximum span lives in `ShiftTemplateService.validate`; 16 hours is the proposed
  value.** Before this milestone the midnight-anchored refusal caught `15:00–14:00` as a 23-hour
  fat-finger. On a `21:00`-anchored desk that pair is still refused (offsets 1080 → 1020), but
  `22:00–20:00` is offsets 60 → 1380 and saves cleanly as a 22-hour shift — and on a 24/7 desk D-06's
  operating-window check does not catch it either. One visible, reviewable condition alongside the
  other refusals, **not inside `DayWindow`** — honouring 19-CONTEXT D-11, which rejected a max-span
  bound there because it would make `DayWindow` hold an opinion about how long a shift may be, which
  is desk policy rather than interval arithmetic. 16h is well clear of any real contracted day here
  (~8–9h) and far below the 22–24h values that are always typos. Rejected: no cap (Phase 22's
  minimum-rest constraint would then find such a template structurally unsatisfiable and refuse the
  solve — a late, confusing place to learn about a typo); a per-desk maximum (schema change, new
  control, new refusal, for a bound no operator has asked to vary).
  — **Reversibility:** `reversible` — one condition; the 16h value is a constant.

- **D-09:** **The refusal lives at the save path AND at `requireShiftModeReady`, sharing one
  containment predicate.** `ShiftTemplateService.validate` refuses it where the operator actually is;
  `ShiftLibraryValidationService.requireShiftModeReady` refuses the SHIFT-mode switch for the same
  reason, satisfying OVNT-05's literal "shift **library** validation refuses" wording and catching any
  row stored before the check existed. One shared predicate so the two can never disagree — the D-08
  pattern `ShiftLibraryValidationService.covers` already uses for exactly this reason ("the report and
  the refusal can never disagree").

- **D-10:** **Generated suggestions run through the same predicate.** `ShiftLibraryGenerationService.
  generateSuggestion` is the only path that bypasses the save-path rules — confirmed: the only two
  `ShiftTemplate` save sites are `ShiftTemplateService:107` and `:126`, both behind `validate()`, and
  `generateSuggestion` returns a `ShiftLibrarySuggestionResponse` without persisting. Without this the
  generator can hand an operator a template the save path then refuses. Reuses D-09's predicate; no
  new validation logic.

### OVNT-06 / OVNT-07 — rendering and disclosure

- **D-11:** **OVNT-06's "continuation indicator on the morning-after cell" is a void premise on the
  Roster sheet, and both documents are amended to say what is actually true.** `ScheduleOutputService`
  keys `agentSchedule` on `getBusinessDate()`, so the Roster sheet's columns are **business days** and
  an overnight shift already occupies exactly one cell — "never two fragments" is already satisfied
  there, and there is no morning-after cell: a `22:00–06:00` shift on a `21:00` desk lies wholly inside
  business day D, while business day D+1 begins at calendar D+1 21:00, long after the shift ended.
  Real fragmentation lives in the per-date **slot** grids (D-13, D-15). The amendment restates ROADMAP
  criterion 4 and OVNT-06's requirement text: the business-day-keyed Roster is one block and carries
  calendar-span disclosure; "never two fragments" applies to the slot grids, which must order columns
  from the desk's day start. Direct precedent — 20-CONTEXT D-04, D-07 and D-14 all amended ROADMAP or
  REQUIREMENTS text during discuss, D-14 explicitly so "the requirement moves with the decision rather
  than failing at verification". Rejected: amending only the ROADMAP criterion (REQUIREMENTS.md would
  still ask for an indicator nothing produces, and the traceability table is what milestone audit
  reads); recording the discrepancy in CONTEXT.md alone (what D-14 chose against, and this project's
  audits keep finding planning documents asserting things a later finding falsified).
  — **Reversibility:** `costly` — the amended criterion becomes what `gsd-verifier` checks; reverting
  means re-deriving the void premise.

- **D-12:** **An overnight Roster cell spells out both weekdays: `Sun 22:00-Mon 06:00`.** Chosen by the
  operator from three rendered alternatives. Self-describing — no legend lookup — and it satisfies
  OVNT-07's "labelled with the calendar dates it spans" literally rather than by indirection. Costs
  column width: 16 → roughly 22 characters (`ScheduleExportService:350-352` sets `16 * 256`). Every
  same-day cell stays byte-identical; only overnight cells change shape. One new legend row: "shift
  crossing into the next calendar day". The existing legend stays as-is otherwise — `08:00-17:00`
  "assigned shift envelope", PTO "approved leave", MANDATORY "rostered day off", `(blank)` "not
  scheduled, no leave recorded". The operator's selected rendering:

  ```
  Roster

  Agent        Sun 2026-09-27        Mon 2026-09-28
  A. Reyes     Sun 22:00-Mon 06:00   Mon 22:00-Tue 06:00
  B. Okafor    08:00-17:00           PTO
  C. Lim       (blank)               MANDATORY

  Legend
    08:00-17:00           assigned shift envelope
    Sun 22:00-Mon 06:00   shift crossing into the next calendar day
    PTO                   approved leave
    MANDATORY             rostered day off
    (blank)               not scheduled, no leave recorded
  ```

  Rejected: appending a `(+1)` marker with a legend row (preserves column width but costs a legend
  lookup and carries the disclosure in a marker rather than the dates); adding the business day's
  calendar span to the column header as well (most complete, but two surfaces to keep correct and the
  header grows on every desk including `00:00` ones where the span is trivial).
  — **Reversibility:** `reversible` — `shiftCode`'s format plus one legend row and a column width.

- **D-13:** **The Excel Allocation sheet orders slot columns from the desk's day start.**
  `writeAllocationSheet` writes one sheet per date with columns in clock order, so a `22:00–06:00`
  span renders as two runs of cells at opposite ends of the row. Ordering by offset-from-anchor makes
  it one contiguous run. **Identical output on a `00:00` desk**, where day-start order and clock order
  are the same sequence, so no existing export changes. Rejected: keeping clock order with a visual
  wrap marker (the shift still reads as two runs, which is what OVNT-06 is about); leaving the
  Allocation sheet alone (it is the per-slot surface an operator uses to check coverage hour by hour —
  exactly where a fragmented overnight shift misleads).

- **D-14:** **OVNT-07 adds structured `businessDate` / `calendarDate` fields to the violation DTO,
  re-points both parsers, and only then changes the label text.** 20-CONTEXT D-10 deliberately left the
  two operator-facing timeslot labels on calendar date and assigned **all** labelling change to OVNT-07
  — this phase (`bday-join-guard.md:148`). The labels are built by concatenation at
  `ScheduleOutputService:670` and `:771`.

  **The hazard this decision exists to defuse:** that label is parsed back as data in two places.
  `ScheduleExportService:832-837` splits it on the first space to recover date and time
  (`label.substring(0, space)` / `label.substring(space + 1)`), and `ScheduleResults.tsx:391` does the
  same (`v.timeslotLabel.substring(0, spaceIdx)`). Changing the label's shape silently breaks both —
  unfilled-seat counts would land under the wrong date, or nowhere. Explicit DTO fields remove a
  string-parsing data channel that will break again the next time anyone touches a label, and this is
  the phase that has to touch it. Rejected: changing the label and fixing both parsers in the same
  commit with a format-pinning test — smaller, but leaves the contract enforced only by a test, in a
  codebase that keeps finding exactly this class of silent disagreement.
  — **Reversibility:** `costly` — a response-DTO shape change with two consumers, one of them the
  frontend.

### The frontend — seven instances of the same defect class

- **D-15:** **`dayStart` is added to the schedule payload, and a new `frontend/src/utils/dayWindow.ts`
  exposes a branded `DayOffset` type so every lexical time comparison becomes a build error.**

  `ScheduleDetail` carries no anchor today, so none of the sites below can be fixed without one.
  `ScheduleConfig.dayStart` already exists on the `Schedule` entity, so this is one field on
  `ScheduleDetailResponse` and on `ScheduleSummary` (which the page polls). **The anchor is part of a
  solved schedule's identity** — it is what the score was computed against — so reading it from the
  desk could disagree with the schedule being displayed if the desk is later re-anchored. Rejected:
  fetching the desk separately (adds no new pattern, since the page already fetches days off by
  `deskId`, but introduces a loading-order race for the grid and the wrong authority).

  The seven sites in `frontend/src/pages/ScheduleResults.tsx`, measured during this discussion:

  | Site | What breaks on an overnight shift |
  |---|---|
  | `:884` `timeDiffMinutes` | Returns **−960** for `22:00`→`06:00`; `if (inc > 0)` then fails and break slots collapse to one |
  | `:668` `t < dayEnd` loop | On a wrapping window (`21:00`→`21:00`) the condition is false immediately — the full-day grid regenerates **zero** columns |
  | `:441`, `:523`, `:741` `while (t < toHHMM(b.endTime))` | A break band crossing the anchor loops zero times |
  | `:455`, `:672` `[...slots].sort()` | Lexical, so `00:00`–`05:00` sort *before* `21:00` — the fragmentation OVNT-06 names |
  | `:681` `slot >= s && slot < e` | An envelope of `22:00`→`06:00` matches **nothing**, so every column renders grey "no shift covers this hour" |
  | `:653` `localeCompare` on start times | Shift groups order by clock, not by anchor offset |

  **The frontend has no test harness at all** — no vitest, no jest, zero test files; `tsc -b` is the
  only gate (`frontend/package.json`). So a structural guard in the BDAY-05 mould is not available
  without first building test infrastructure. The branded-type approach gets the same property from
  the compiler instead: `dayWindow.ts`'s functions return a branded `DayOffset` rather than a string,
  so `slot >= s` on raw times stops compiling and all seven sites become build errors until converted.
  This is the **compiler-forced** idiom Phase 19 chose for `DayWindow` ("the compiler finds them all"
  literally true rather than aspirational), and it is the type-level approach 18-CONTEXT D-03 rejected
  for Java only because it was too wide — here it is one file and seven sites. It needs no new
  dependency. Rejected: adding vitest and porting the structural guard (gives the frontend a harness it
  has never had, but that is a new dependency, a new CI step, and a test-infrastructure project inside
  a correctness phase); fixing the seven sites with plain string helpers and no guard (the only surface
  in this milestone with no structural protection, in a file where the same defect already recurred
  seven times).
  — **Reversibility:** `costly` — the branded type propagates through every call site that touches a
  scheduling time; removing it means re-widening all of them to `string`.

- **D-16:** **Both render branches get the anchored ordering, unconditionally.** At a `00:00` anchor an
  offset equals the clock minute, so anchored ordering emits the identical column sequence — a
  **provable no-op** on every desk that exists today, which is the argument Phase 19 used for the
  compiler-forced `DayWindow` migration. One code path, no mode branch to drift. The SLOT branch has
  its own copies of the bugs (`:455`, `:523`), and **SOLV-04 established that SLOT mode really can
  place an agent across midnight on today's data** — `AgentAssignment`'s only planning variable is the
  agent, the timeslot is fixed — so leaving it out would ship a known-broken path. Rejected: gating on
  a non-midnight anchor (byte-identical by construction rather than by argument, but creates a path the
  `00:00` desks never exercise, so the first real night desk is also the first test); SHIFT branch only
  (honours P-33's byte-for-byte rule but leaves the SLOT branch's identical bugs on a path SOLV-04
  proved can cross midnight).

- **D-17:** **The grid's per-business-day section headers disclose their calendar span, on
  non-midnight desks only.** `<h4>{date}</h4>` is a bare business date today. Matching D-12's Roster
  convention keeps OVNT-07's "wherever it is displayed" true on the UI as well as the export. Suppressed
  at a `00:00` anchor, where the business day and calendar day coincide and the extra text is noise.
  Rejected: showing it on every desk (one unconditional format with nothing to branch on, at the cost of
  noise on every desk in use today); cell-level disclosure only (relies on the operator reading a row to
  understand what the section means).

### Inherited obligations this phase must discharge

- **D-18:** **The three `@AssertsTodaysBehaviour` registry entries flip here, each deregistered in the
  same change that flips its assertion.** `src/test/resources/midnight-boundary-scenarios.md:85-87`
  registers them, and `MidnightBoundaryScenarioRegistryTest` asserts the marked-method set and the
  fenced registry are set-equal **in both directions** — so a flip without deregistration fails the
  build, and so does a stale entry. 18-CONTEXT D-16 made this the contract Phase 21 closes out against.
  The three:
  - `MidnightBoundaryRegressionTest.PtoOnAdjacentCalendarDate#attributionIsPerCalendarDateOnly` → OVNT-03
  - `MidnightBoundaryPropertyTest.ContractedHoursStartingWeekdayOnly#twoCalendarDatesDrawFromTwoIndependentWeekdayRows` → OVNT-04
  - `MidnightBoundaryPropertyTest.ShiftCrossingMidnight#durationMinutesThrowsAndTheSavePathRefuses` → OVNT-01
  Plan 20-05 is the worked precedent: SOLV-04's entry was removed in the same commit that flipped its
  assertion, with the before-picture preserved in the plan SUMMARY rather than in a stale registry row.
  — **Reversibility:** `costly` — these are the milestone's only enforcement that the flips happened.

- **D-19:** **The three BDAY-06 scenarios Phase 18 deferred become constructible here** (18-CONTEXT
  D-16, carried forward by 19-CONTEXT and 20-CONTEXT): a shift starting before and ending after
  midnight, PTO on the starting vs. ending day, and the starting-weekday-only half of contracted hours.
  They are the same three as D-18's registry rows — the deferral and the registry describe one
  obligation, not two.

### Claude's Discretion

- The exact spelling of `dayWindow.ts`'s branded type and its function set — whether it mirrors
  `DayWindow`'s anchored method names one-for-one or exposes a narrower surface covering only the seven
  sites' needs.
- Whether `dayWindow.ts` also covers `StaffingTab`, `PtoTab` and the `DriftTab` date columns, or stops
  at the allocation grid. (ISO date strings sort correctly lexically, so the date columns are not
  defective — this is a consistency question, not a correctness one.)
- Whether `DeskManagement.tsx`'s new time picker reuses `dayWindow.ts` or stays on plain string input,
  given the day start is an anchor rather than a time measured against one.
- The 16-hour value in D-08, if evidence suggests a different bound.
- How the unfilled-seat map key (`${date}|${slot}`) changes once slots carry offsets rather than clock
  strings, and whether that key moves to the structured DTO fields D-14 introduces.
- Task ordering and plan decomposition throughout, including where the D-11 document amendments land
  relative to the code they describe.
- Whether `ScheduleSummary` gains `dayStart` alongside `ScheduleDetailResponse` or only the detail
  payload does — D-15 assumes both because the page polls summary, but the grid may not need it there.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone scope and sequencing

- `.planning/ROADMAP.md` §"Phase 21: Overnight Shift Templates" — the five success criteria, the
  research flag, and the "do not let the old advisory-only behaviour persist here" note. **Criterion 4
  is amended by D-11.**
- `.planning/REQUIREMENTS.md` §"Overnight Shifts (OVNT)" — OVNT-01..07 as written. **OVNT-06 is
  amended by D-11.**
- `.planning/REQUIREMENTS.md` §"Decisions taken at scoping — do not relitigate without new evidence" —
  the desk-level-day-start decision D-02 rests on, and the "Accepted consequence" paragraph that
  assigns disclosure-through-calendar-span-labelling to OVNT-07.
- `.planning/REQUIREMENTS.md` §"What research established" item 5 — `agent_day_hours` needs no schema
  change, because it is keyed by `DayOfWeek` and a Sunday-night shift correctly consumes Sunday's row.
  This is OVNT-04's foundation.

### Prior phase decisions this phase consumes

- `.planning/phases/20-solver-business-date-correctness/20-CONTEXT.md` — D-03 (the editable control
  deferred here), D-05 (`agent_shift_assignment.date` IS the business date, derived), D-10 (labels keep
  the calendar date; all labelling change is OVNT-07's), D-13/D-15 (the migrated services), and its
  Deferred Ideas list, which names this phase as owner of three items.
- `.planning/phases/19-daywindow-re-anchoring/19-CONTEXT.md` — D-11 (why the save-path refusal survives
  the re-anchoring, and why a max-span bound does **not** belong in `DayWindow`), D-12 (relaxing the
  refusal is OVNT-01's, extending it to the library paths is OVNT-05's).
- `.planning/phases/18-business-day-foundation-guards/18-CONTEXT.md` — D-13..D-18 (the constructed
  regression suite's design), D-16 (the deferred-scenario registry contract), D-03 (why a naive token
  scan becomes decoration — the measurement behind D-15's choice of a type over a scan).

### The guard contracts and registries this phase closes out against

- `src/test/resources/midnight-boundary-scenarios.md` §"Scenarios asserting today's behaviour" and its
  fenced registry at `:85-87` — the three entries D-18 must remove.
- `src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java` — the both-directions
  validator that fails the build on a flip without deregistration, and on a stale entry.
- `src/test/resources/bday-join-guard.md` — `:148` assigns all labelling change to OVNT-07; `:169-170`
  hands the two Erlang calendar-date range-delete callers to "a Phase 21 owner … alongside OVNT-01"
  (see Specific Ideas — **not decided in this discussion**).
- `src/test/resources/midnight-time-arithmetic.md` — the Java structural guard whose technique D-15
  cannot reuse on TypeScript.
- `src/test/resources/bday-02-write-paths.md:12` — notes that OVNT-01 adds code with real
  business-date write significance.

### Code this phase changes

- `src/main/java/com/wfm/service/ShiftTemplateService.java` — `validate:238-300` (D-02, D-06, D-08,
  D-09); `dayWindowFor(UUID)` is the existing anchor seam.
- `src/main/java/com/wfm/model/ShiftTemplate.java:120-142` — `getNetHours(breakDurationMinutes, window)`,
  already correct for an overnight envelope; OVNT-01's proof target.
- `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` — `requireShiftModeReady` and
  `validate` (D-09); `:275-310` is where `bounds` is already read and `endTime()` is not (D-06);
  `:682` is the misattributed "It will still save" advisory.
- `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` — `generateSuggestion:131` (D-10).
- `src/main/java/com/wfm/service/DeskService.java` — `setDayStart:240-265` and its javadoc (D-03, D-05).
- `src/main/java/com/wfm/service/TimeslotGeneratorService.java` — `:96-97` the increment allowlist (D-05),
  `requireDayStartTiles:220-236`, `getLiveBounds` (D-05's advisory source).
- `src/main/java/com/wfm/service/ScheduleExportService.java` — `writeRoster:243-350` and
  `shiftCode:361-378` (D-12), `writeAllocationSheet:697` and `slotStarts:865` (D-13),
  `unfilledSeatsByDateAndSlot:821-840` (D-14's parser).
- `src/main/java/com/wfm/service/ScheduleOutputService.java` — `:670` and `:771` label concatenation
  (D-14); `:173`, `:323`, `:745` are the business-date groupings that make the Roster one cell.
- `src/main/java/com/wfm/dto/TimeslotBoundsResponse.java` — `endTime()`, dead today, read by D-06.
- `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` and `ScheduleSummary.java` — D-15's
  `dayStart` field.
- `frontend/src/pages/ScheduleResults.tsx` — the seven sites in D-15's table; local helpers at
  `:877-895`.
- `frontend/src/pages/DeskManagement.tsx:100-140` — the read-only day-start cells (D-01, D-04).
- `frontend/src/api/client.ts` — `setDayStart` at `:106-107`, `Desk` at `:331`, `ScheduleDetail` at
  `:523-533`.

### Project conventions

- `.planning/codebase/CONVENTIONS.md` — naming, and the test-method
  `{subject}_{condition}_{expectedResult}[_{proofType}]` convention.
- Comments state their reason inline and cite **requirement IDs, never phase numbers** (18-CONTEXT
  D-24) — phase numbers have moved between v1.4 and v1.5, and twice within v1.5.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **The anchor seam into template validation already exists.** `ShiftTemplateService.dayWindowFor(UUID)`
  binds one `DayWindow` per `validate()` call (BDAY-04, plan 19-06), and `validate` already holds
  `deskId`. D-02, D-06 and D-08 all consume it; none builds new plumbing.
- **`DayWindow.anchoredDurationMinutes` already wraps correctly across the anchor** and deliberately
  does **not** throw — "that condition now means *crosses the anchor*" per its own javadoc. Net hours
  for an overnight template are already right; OVNT-01 needs a test, not arithmetic.
- **`getLiveBounds` already returns `incrementMinutes`**, which is exactly what D-05's tiling advisory
  needs — no new repository read.
- **`ShiftLibraryValidationService` already has the one-computation-two-callers pattern.** `validate`
  is the non-throwing report and `requireShiftModeReady` converts blocking findings into a
  `PreSolveValidationException`, "so the report and the refusal can never disagree" (its class javadoc).
  D-09 extends an existing structure rather than inventing one.
- **`switchSchedulingMode` is the worked precedent for D-03's refusal** — it already refuses a
  transition and names what blocks it (the uncovered demand windows).
- **The four `DeskService.setDayStart` refusals are already written and tested.** D-01 surfaces them;
  it writes no new validation.
- **`shiftCode` already handles the `00:00`-means-end-of-day trap** via
  `window.anchoredEndMinute(ad.endTime())`, with the reason stated inline. D-12 extends a function that
  already understands the problem.

### Established Patterns

- **Guards land before the change they police, each with a proof it can go red.** Phases 18, 19 and 20
  all did this. D-18's registry flips invert it: the assertion exists first and the change flips it.
- **Additive first, consume second** — Phase 18's anchored helpers, Phase 19's `ScheduleConfig` field,
  Phase 20's one-commit consume. D-14 is explicitly shaped this way: structured fields first, label text
  second.
- **Compiler-forced migration where the surface is bounded.** 19-CONTEXT D-04/D-06 removed the
  midnight-implicit overload so every call site became a build error. D-15 applies the same reasoning in
  TypeScript, where a scan-based guard has no runner to live in.
- **A no-op at the `00:00` anchor is the standard safety argument** for touching shared rendering and
  ordering code (D-13, D-16). It is provable, not asserted: offset equals clock minute at that anchor.
- **`00:00` means different things by position** — a time equal to the anchor in an end position is the
  end of the business day. This is the trap behind three of D-15's seven sites.

### Integration Points

- `Desk.dayStart` → `ShiftTemplateService.dayWindowFor` → `validate` — D-02/D-06/D-08's channel
- `Desk.dayStart` → `TimeslotGeneratorService.requireDayStartTiles` — D-05's late refusal
- `ScheduleConfig.dayStart` → `ScheduleDetailResponse` / `ScheduleSummary` → `ScheduleResults.tsx` —
  D-15's new channel, the only new plumbing in the phase
- `TimeslotGeneratorService.getLiveBounds` → `TimeslotBoundsResponse.endTime()` →
  `ShiftLibraryValidationService` — D-06's containment check, activating a dead field
- `ScheduleOutputService` violation DTO → `ScheduleExportService:832` **and**
  `ScheduleResults.tsx:391` — D-14's two parsers; both must move with the DTO

### Constraints the architecture imposes

- **The frontend has no test runner.** `frontend/package.json` has no test script and no test
  dependency; there are zero test files. Any frontend correctness claim in this phase rests on the
  compiler or on manual verification — this is why D-15 chose a type over a guard.
- **The timeslot label is load-bearing as data, not just as text** (D-14). Two independent substring
  parsers depend on its exact shape, one of them across the HTTP boundary.
- **`dev` is the live system with real tenant data**, and at least one live desk already holds an
  accepted schedule — which is why `setDayStart` refuses unconditionally there, and why D-07 declined
  to make the operating-window check blocking for all templates.
- **Never poll `GET /schedules/{id}`** — the detail payload is ~4 MB and competes with the solver for
  its two cores. Any verification of D-12/D-13/D-15 against a live desk must use `/summary` or an
  in-process fixture.
- **A deploy kills a running solve** — solver state is heap-only. Relevant if any live check is
  attempted during execution.

</code_context>

<specifics>
## Specific Ideas

- **The operator chose the Roster rendering from three rendered alternatives**, selecting the
  spell-out-both-weekdays form (`Sun 22:00-Mon 06:00`) over a `(+1)` marker and over adding the span to
  the column header. The exact selected rendering is reproduced verbatim in D-12 — treat it as the
  contract for the cell and the legend row, not as one option among three.
- **The operator interrupted mid-discussion to confirm 15, 30 and 60 minute timeslots must all stay
  supported.** Already enforced at `TimeslotGeneratorService:96-97`. Recorded as a constraint the phase
  must not break (D-05), and it is what surfaced the save-accepts-but-generation-refuses seam.
- **OVNT-05's wording is near-vacuous and OVNT-06's is a void premise on the Roster sheet.** Both were
  found by measurement during this discussion, not inherited as known items. D-06 and D-11 reinterpret
  them with the documents amended to match. A planner working from ROADMAP + REQUIREMENTS alone would
  implement a check that cannot fail and hunt for a cell that does not exist.
- **The v1.3 deferred item describing the envelope-vs-operating-window gap quotes an advisory that
  belongs to a different finding.** "It will still save" at `ShiftLibraryValidationService:682` is the
  contracted-hours mismatch advisory. There is no envelope-vs-window advisory at all. The gap is real;
  do not go looking for the advisory.
- **The two Erlang range-delete callers were NOT decided in this discussion.** `bday-join-guard.md:169-170`
  hands them to "a Phase 21 owner … alongside OVNT-01": `StaffingRequirementRepository`'s calendar-date
  range delete has two callers beyond the demand-upload path — the Erlang C and Erlang X staffing
  calculators — whose from/to range arrives from the request payload as calendar dates. Phase 20 left
  them on calendar-date semantics because no desk carried a non-midnight anchor until its final commit,
  making them "unreachable instances of the same latent defect today, not live ones." **This phase makes
  non-midnight desks operator-reachable for the first time (D-01), which is exactly what turns them
  live.** Recommendation: migrate them here, since D-01 is what ends their unreachability. Raise it at
  planning as an explicit scope decision — it was surfaced, not settled.
- **OVNT-01's own proof target is narrow and should not be over-built.** The acceptance path works;
  write the test against a `21:00`-anchored desk saving `22:00–06:00` with net hours asserted, and
  against a `00:00` desk getting D-02's new message. The registry flip (D-18) is what makes it
  falsifiable.
- **SOLV-04's finding is load-bearing for D-16.** The SLOT-mode branch is not hypothetically affected:
  `AgentAssignment`'s only planning variable is the agent and the timeslot is fixed, so nothing stops a
  SLOT-mode desk placing one agent across a midnight boundary on today's data. Scoping the frontend fix
  to SHIFT mode would leave a live path broken.

</specifics>

<deferred>
## Deferred Ideas

- **An operating-window containment refusal for same-day templates.** D-07 makes it advisory here
  because a blocking check could refuse an edit to a stored template on a live desk. Its own phase
  needs a survey of live templates first, and then the advisory becomes a refusal.
- **A frontend test harness.** D-15 chose a branded type precisely to avoid building one inside a
  correctness phase. Adding vitest and porting `MidnightTimeArithmeticGuardTest`'s structural scan to
  TypeScript remains the stronger long-term answer, and the frontend has never had any test coverage.
- **Rename `date` → `businessDate` across the six business-date-shaped problem facts**
  (`AgentShiftAssignment`, `AgentDayConfig`, `AgentDayOff`, `AgentPreference`,
  `ResolvedUsualShiftTarget`, `ShiftStartMixTarget`) — ~200 accessor sites; renaming fewer than all six
  is worse than renaming none. Carried from 20-CONTEXT.
- **A per-desk maximum shift length**, if any desk ever needs to vary D-08's fixed cap.
- **A documented, tested day-start reversal** for a desk holding an accepted schedule — `MIGR-04`'s,
  deferred out of v1.5. D-04 discloses the permanence rather than working around it.
- **Restoring a genuine live-desk drift check** for Phil-US (20-CONTEXT D-14) — its natural home is the
  deferred MIGR-01..04 migration.
- **Deleting the frozen `DayWindow` oracle** (19-CONTEXT D-13) once v1.5 ships.
- **Tightening `ScheduleConfig`'s defensive null-anchor fallback** once no pre-Phase-19 `Schedule` can
  reach the solver — listed under Claude's Discretion in Phase 20 and not taken there.
- **Blocked-break-hours has no enforcement point in SHIFT mode** — a band at offset 0 or at
  `envelopeMinutes - duration` is legal at save time and scores 0hard. Deferred by operator ruling OR-2
  at the v1.3 close; the fix location is already settled as save-time in `ShiftTemplateService`, which is
  a file this phase edits. Not folded: it is a distinct requirement with no OVNT id.

### Reviewed Todos (not folded)

All three `todo.match-phase` hits were reviewed. The operator initially selected two to fold, then —
once the scope and risk were laid out — ruled that **both stay out of Phase 21 and are raised for the
roadmap instead**, with the reasoning recorded here:

- **Cross-agent seat displacement for the atomic shift move**
  (`.planning/todos/pending/2026-08-13-cross-agent-seat-displacement.md`, `solver`, score 0.6).
  Its own file says: "Confirm this is scoped as its own phase (do not fold into an in-flight phase) — it
  changes move semantics and score-corruption risk surface … A named displacement move is a
  `costly`/architectural decision, not a `reversible` one." It also requires the seeded 5×5 step-count
  benchmark harness re-run as its only trustworthy evidence format, plus new undo-correctness coverage.
  Separately, compound swap moves are a known hazard in this codebase — sound on a settled solution,
  corrupting during search, and `FULL_ASSERT` does not catch it. **Raise as a candidate phase after
  Phase 22.** Phases 19 and 20 declined it for the same reasons.
- **Provide a blank upload template spreadsheet, one sheet per desk**
  (`.planning/todos/pending/2026-07-30-blank-upload-template-one-sheet-per-desk.md`, `upload`, score 0.6).
  Changes the workbook structure the desk-assignment upload parser must accept, on a path that clears
  each desk before parsing and is destructive if the layout and the parser disagree. Has a real shared-file
  argument with OVNT-06 (both touch export/workbook code) but no shared correctness concern.
  **Raise as a candidate phase after Phase 22.**
- **Terraform state diverges from live RDS password and `publicly_accessible`**
  (`.planning/todos/pending/2026-08-14-terraform-db-password-drift.md`, `infra`, score 0.6). Matched on
  the word "phase" alone. Infrastructure drift, unrelated. Declined in Phases 19 and 20 too.

</deferred>

---

*Phase: 21-Overnight Shift Templates*
*Context gathered: 2026-10-02*
