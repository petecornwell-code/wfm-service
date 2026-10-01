# Phase 20: Solver Business-Date Correctness - Context

**Gathered:** 2026-10-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Every consumer of a timeslot's date resolves the **business** date rather than the calendar date,
and every one of those resolutions is policed by a structural guard rather than by convention. Three
distinct migrations sit inside that sentence, and the ROADMAP entry as written only names the first:

1. **12 date joins** in `ScheduleConstraintProvider` — one shared `DATE` lambda at `:91-92` feeding 7
   `groupBy` sites, plus 11 direct `getDate()` sites (SOLV-01, SOLV-02, SOLV-04, SOLV-06).
2. **19 interval-anchor sites** in `ScheduleConstraintProvider` that currently run on
   `PENDING_DESK_ANCHOR`, a hardcoded `DayWindow.anchoredAt(LocalTime.MIDNIGHT)` placeholder whose own
   javadoc names `SOLV-01` as its removal owner (SOLV-03). **Break bands and contiguity cannot hold
   across midnight while they run on a hardcoded midnight anchor — SOLV-03 is not a test-only
   deliverable.**
3. **Coverage-reporting and library-generation date keys** — ~6 grouping sites in
   `ScheduleOutputService` and 2 calendar-weekday derivations in `ShiftLibraryGenerationService`
   (SOLV-07).

Plus the pre-solve seat-supply shortfall reporting per business day (SOLV-05), a Phil-US-shaped
constructed drift guard (BDAY-07, as amended by D-14), and — as the phase's final commit — deletion
of `DeskService`'s `00:00`-only gate (D-01).

Requirements: **SOLV-01 … SOLV-07, BDAY-07.**

**Observable behaviour change for an operator:** after the final commit a desk's day start can be set
to any 15-minute boundary through the API. The desk-configuration cell stays read-only (D-03), so no
operator-reachable *control* changes in this phase.

**Not this phase:** overnight shift templates and relaxing the save-path forward-interval refusal
(OVNT-01..07, Phase 21); OVNT-07's calendar-span label disclosure (D-10); an editable day-start
control (D-03); minimum rest (REST-01..07, Phase 22); the Phil-US live migration (MIGR-01..04,
deferred out of v1.5).

</domain>

<decisions>
## Implementation Decisions

### The `00:00`-only gate

- **D-01:** **The gate deletion is Phase 20's LAST commit**, after every join and anchor is migrated
  and guarded. Three locked documents assign it here — `ROADMAP.md` Phase 18 success criterion 2
  ("until Phase 20 lifts the gate"), `DeskService.java:211`'s javadoc ("SOLV-01 widens the accepted
  range to 15-minute boundaries"), and `19-CONTEXT.md` D-01 — but Phase 20's own five success criteria
  never mention it, which is how it slipped between Phases 18 and 19 in the first place. Ordering it
  last closes the window `19-CONTEXT.md` D-01 refused for Phase 19: that argument ("an operator can
  set a `21:00` anchor and accept a plausibly-wrong schedule") does not stop at the phase boundary and
  applies equally to a gate deleted in Phase 20's first commit.
  — **Reversibility:** `reversible` — one validation line, unchanged in shape from Phase 18.

- **D-02:** **The line is replaced by a 15-minute-boundary refusal at save time**, naming the rejected
  value. D-06 of `18-CONTEXT.md` fixes 15-minute boundaries as the target range. This check is
  *increment-independent*, which is what makes it possible at save time — Phase 18's D-08 established
  that the generation increment is not desk state (it arrives per call, inferred from the FTE
  spreadsheet at `FteUploadService:127`), so tiling cannot be validated on save. **Phase 18's D-08
  refusal — a day-start that is not a whole multiple of the generation increment fails loudly at
  generation, naming the day-start, the increment and why they cannot tile — becomes reachable through
  the API for the first time in this phase.** It was deliberately built unreachable; Phase 20 is where
  it earns its keep, and the plan should prove it fires rather than assume it does.
  Kept as one visible, reviewable condition, honouring `18-CONTEXT.md` D-07's intent rather than
  folding the refusal into a range block.
  — **Reversibility:** `reversible` — one condition in one method.

- **D-03:** **`DeskManagement.tsx`'s day-start copy is corrected; the cell stays read-only.** Both
  `:112` and `:124` currently read `only 00:00 is supported until overnight scheduling lands`, which
  becomes false the moment D-01 lands — and Phase 18's disclosure requirement exists precisely to stop
  the UI stating something false about the backend. The corrected copy states the desk's anchor and
  that the accepted range is 15-minute boundaries. An *editable* control is not in this phase: it needs
  a time picker plus error surfacing for both refusals (15-minute boundary, and the unconditional
  accepted-schedule refusal at `DeskService:247-253`), which is operator-facing work belonging with
  Phase 21.
  — **Reversibility:** `reversible` — two cells of copy.

- **D-04:** **A sixth ROADMAP success criterion is added to Phase 20** covering the gate deletion and
  the opened range. Without it the deletion is invisible to `gsd-verifier` and can silently not happen
  — the exact failure mode that produced the three-document contradiction D-01 resolves. Capturing it
  as a CONTEXT decision alone would leave the decision-coverage gate as the only thing holding it.

### `agent_shift_assignment`'s date

- **D-05:** **No `business_date` column — `agent_shift_assignment.date` already IS the business date,
  and stays derived.** The three joins at `ScheduleConstraintProvider:510`, `:608` and `:1193` pair
  `sa.getDate()` against `a.getTimeslot().getDate()`; they re-point the **Timeslot side only**, to
  `getBusinessDate()`. The derivation chain is evidence, not assumption: `computeAgentDayConfigs`
  (`SolverService:841-849`) iterates `schedule.getPeriodStartDate()` to `getPeriodEndDate()`, and
  `18-CONTEXT.md` D-22 locks `periodStart`/`periodEnd` as business dates; `buildShiftAssignments` then
  emits one row per working `AgentDayConfig`.

  **The operator delegated this decision on scalability and maintainability grounds. The reasoning,
  recorded so it is not re-litigated from scratch:** (a) `agent_shift_assignment` is rebuilt every
  solve, so a stored column is a denormalised copy of a derived value — and `V41__agent_shift_assignment.sql`'s
  own design note is that entity creation and the value-range filter read "the SAME fact … so they can
  never disagree **by construction**", which a column breaks; (b) it would create a *second*
  business-date write path, the inverse of BDAY-08's one-derivation-site rule; (c) it forces a backfill
  decision for ACCEPTED historical rows, which V41 deliberately freezes at accept time so a later edit
  cannot rewrite what history says an agent worked — there is no correct backfill value. Renaming
  `date` → `businessDate` was costed and rejected *for this phase only*: it reads honestly only if all
  six business-date-shaped facts rename together (`AgentShiftAssignment`, `AgentDayConfig`,
  `AgentDayOff`, `AgentPreference`, `ResolvedUsualShiftTarget`, `ShiftStartMixTarget`), because renaming
  one would actively imply the other five are *not* business dates. That is ~200 accessor sites, too
  wide for the phase the ROADMAP wants decomposed and reviewable. Deferred, not dismissed.

  The comprehension gap gets the cheap fix this codebase already uses: a javadoc line on the field
  stating the semantics and citing `SOLV-01` (per `18-CONTEXT.md` D-24 — requirement IDs, never phase
  numbers).
  — **Reversibility:** `costly` — reversing to a stored column means a migration, an entity change,
  the three join sites, the D-06 guard, and an answer to the ACCEPTED-row backfill question.

- **D-06:** **A guard test over the derivation chain is what makes "derived" safe**, standing in the
  same relationship to D-05 that BDAY-08's write-path guard stands in to `Timeslot`'s *stored* column.
  It asserts structurally that `AgentDayConfig.date` is sourced only from the schedule period and
  `AgentShiftAssignment.date` only from `AgentDayConfig`, failing the build on a future writer that
  sets either from a calendar date. Two-directional, matching the four guards already proven in this
  codebase (`BusinessDateWritePathGuardTest`, `UsualShiftWritePathGuardTest`,
  `SolverUsualShiftWritePathGuardTest`, `MidnightTimeArithmeticGuardTest`). Convention alone was
  rejected — `REQUIREMENTS.md` SOLV-07 itself demands "guarded by a test rather than by convention".
  — **Reversibility:** `reversible` — one test class.

- **D-07:** **`PENDING_DESK_ANCHOR`'s 19-site migration gets its own (seventh) ROADMAP success
  criterion.** `ScheduleConstraintProvider:58-66` declares it as a placeholder "named `PENDING`, not
  `MIDNIGHT` or `DEFAULT`, so a reader cannot mistake it for a deliberate choice", with a javadoc
  stating it "stays so until `SOLV-01` replaces every call site below with the real anchor read from
  the joined `ScheduleConfig`", and it is listed in `src/test/resources/midnight-time-arithmetic.md`
  §"Permitted midnight anchors" with Phase 20 as removal owner. It is therefore a deliberate Phase 19
  hand-forward, not a surprise — but the Phase 20 ROADMAP entry never mentions it, and a planner
  reading only ROADMAP + REQUIREMENTS would size SOLV-03 as test-only. The criterion requires: the
  constant is gone, its allowlist entry is gone, and the guard goes red if either returns.

### The join guard

- **D-08:** **`BusinessDateJoinGuardTest` scans key positions only — `join` / `equal` / `groupBy` /
  `computeIfAbsent` — across three files**: `ScheduleConstraintProvider`, `ScheduleOutputService`, and
  (per D-13) `ShiftLibraryGenerationService`. Display and label sites are out of scope *by construction
  rather than by allowlist entry*, which is what keeps the allowlist near-empty. That matters: Phase 18
  measured the alternative — D-03 of `18-CONTEXT.md` found a naive token scan would demand a 100+ entry
  allowlist and "become decoration". One guard covers both SOLV-02 and SOLV-07's structural half.
  The ROADMAP's "Open decision: the final allowlist contents … research expects plausibly none survive"
  resolves as: **plausibly empty for key positions, non-empty for display** — which is why the scope is
  drawn at key positions rather than at `.getDate()` occurrences.
  — **Reversibility:** `reversible` — one test class plus an allowlist section.

- **D-09:** **The scanned token is `.getDate()` on a `Timeslot` receiver, and nothing else.** `Timeslot`
  is the only type carrying two competing dates — `REQUIREMENTS.md` §"What research established" item 1
  establishes that every other date-bearing problem fact the solver reads is already business-date-shaped
  with no competing calendar-date meaning. Of the four candidate tokens `18-CONTEXT.md` D-04 lists
  (`.getDate()`, `.getDayOfWeek()`, `.plusDays(`, `ChronoUnit.DAYS`), the other three would fire
  overwhelmingly on types with no ambiguity — the measured path to a decoration guard. Note this token
  choice *does* catch `ShiftLibraryGenerationService`'s weekday derivations, because they are spelled
  `sr.getTimeslot().getDate().getDayOfWeek()` — a `.getDate()` on a `Timeslot` receiver.

- **D-10:** **The two operator-facing timeslot labels keep the calendar date, documented as
  deliberate.** `ScheduleOutputService:664` and `:759` build `ts.getDate() + " " + startTime + "-" +
  endTime`. A label answers "when does this happen", which is a calendar question: on a `21:00` desk a
  `02:00` slot belongs to business day D but occurs on calendar D+1, and the operator needs the latter
  to find it. The guard's own documentation must record why display is out of scope, so the next reader
  files it as a decision rather than a missed migration. All labelling change — including showing both
  dates — is OVNT-07's, in Phase 21, where `REQUIREMENTS.md`'s accepted consequence already assigns
  "disclosure through calendar-span labelling".
  — **Reversibility:** `reversible` — no code change at all; a documented non-change.

### How correctness is proven, and how the change is decomposed

- **D-11:** **The `21:00`-anchored scenarios land BEFORE the migration, red.** `19-CONTEXT.md`'s
  deferred list hands this forward explicitly: extend `MidnightBoundaryFixture`'s three constructed
  scenarios to a non-midnight anchor, and "**Phase 20 should own it explicitly** rather than inherit it
  by accident — otherwise the join migration and the first non-midnight scenario proof go red together
  with two candidate causes." Writing them first, against an anchor nothing honours yet, makes the
  migration falsifiable instead of merely compiling, and follows the guards-land-before-the-change
  pattern Phases 18 and 19 both used. They are this phase's tracer.
  — **Reversibility:** `reversible` — test ordering and fixture extension.

- **D-12:** **One migration commit; the per-constraint match-count assertions do the diagnosis.** The
  ROADMAP asks for both "moved in one deliberate pass" and "decomposed enough that a failure names
  which constraint moved" — these only conflict if decomposition is read as commit granularity. It is
  not: "one deliberate pass" is the commit, and "names which constraint moved" is SOLV-06's
  per-constraint match-count assertion set. One revert target for the risky edit, and a failure still
  names its constraint. This mirrors `19-CONTEXT.md` D-10's additive-then-consume shape, where the
  plumbing commit precedes the consuming one.
  — **Reversibility:** `reversible` — a history-shape choice; a single commit is itself the cheap
  revert that motivates it.

- **D-13:** **`ShiftLibraryGenerationService` is the third guarded file, and both of its weekday
  derivations migrate in this phase.** `:180` and `:486` derive a weekday from a timeslot's calendar
  date (`sr.getTimeslot().getDate().getDayOfWeek()`). On a `21:00` desk a `02:00` Monday-business-day
  slot has calendar weekday Tuesday, so library generation buckets demand under the wrong weekday —
  a real instance of this defect class. It was surfaced during this discussion, not inherited as a
  known item, and no Phase 20 success criterion names library generation. Leaving a known instance
  outside the guard's scope would make the guard's green misleading, which is the specific failure
  `MidnightTimeArithmeticGuardTest`'s own javadoc warns about. The operator chose to migrate now rather
  than allowlist with a Phase 21 owner.
  — **Reversibility:** `costly` — widens the migration into a third service, and library-generation
  output changes shape for any non-midnight desk.

- **D-14:** **BDAY-07 and ROADMAP criterion 5 are rewritten as a Phil-US-*shaped* constructed drift
  guard.** The measurement is a test-scope fixture carrying Phil-US's real composition (48 agents, its
  desk/specialization shape), asserting unchanged per-constraint match counts across the re-anchoring.
  Both documents currently say *live*: BDAY-07 reads "One small **live** desk produces an unchanged
  schedule", and criterion 5 says "one small live desk (Phil-US, 48 agents, not Vinted's 287)". A
  constructed fixture satisfies neither as written, so the requirement moves with the decision rather
  than failing at verification.

  **The tension this knowingly accepts, recorded because `REQUIREMENTS.md` makes it load-bearing:**
  v1.4 was cancelled because BDAY-06's fixture sourced its inputs from four captured *live* desks,
  making it wrong in both directions at once — and the BDAY-06/BDAY-07 split exists "so the two can
  never again be conflated into one fixture that does neither job". D-14 brings BDAY-07 back toward
  constructed data. What keeps it from repeating v1.4's mistake is that it is **constructed to Phil-US's
  shape, not captured from Phil-US's bytes** — no golden-byte comparison, and the assertion is
  per-constraint match counts, which carry a diagnostic signal where golden bytes carry none. The live
  half is what is being given up: v1.5 ships with no real-tenant drift evidence. That was weighed —
  `explain()` is the only per-constraint match-count source and `ScheduleOutputService:582-583` refuses
  to call it on the ACCEPTED/DB path by design, so a live before/after would mean either polling the
  4 MB detail payload against a solver that has two cores, or building new operator-reachable API
  surface in a correctness phase.
  — **Reversibility:** `costly` — restoring a live proof means building the data or endpoint path this
  phase declined to build, and the pre-migration match counts are then only recoverable by checking out
  the pre-migration commit.

### Claude's Discretion

- The exact spelling and structure of the per-constraint match-count assertion set across the 26
  registered constraints — whether one parameterised test derives the expected counts from a single
  table (the `ScheduleConstraintClassification` precedent, which already enforces that the constraint
  set and its own map agree by reflection) or each constraint carries its own assertion.
- Whether `BusinessDateJoinGuardTest` is a new class or a fourth scan inside
  `MidnightTimeArithmeticGuardTest` — the allowlist machinery and both-directions proof already live
  there, but the token and receiver heuristics are different enough to argue for separation.
- Whether `ScheduleConfig`'s defensive null-anchor fallback (the `dayStart != null ? dayStart :
  LocalTime.MIDNIGHT` shape that `MidnightTimeArithmeticGuardTest` explicitly does *not* treat as a
  midnight anchor) survives this phase or is tightened once no pre-Phase-19 `Schedule` can reach the
  solver.
- Task ordering and plan decomposition within the commit boundary D-12 fixes, and where in that
  ordering SOLV-05's per-business-day seat-supply shortfall reporting lands.
- The wording of the three ROADMAP/REQUIREMENTS amendments D-04, D-07 and D-14 call for.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone scope and sequencing

- `.planning/ROADMAP.md` §"Phase 20: Solver Business-Date Correctness" — the five success criteria as
  written, plus the two "Open decision" markers this discussion resolves (D-05 resolves the
  `agent_shift_assignment` question; D-08 resolves the allowlist question). **Three amendments are
  required before planning closes: a sixth criterion (D-04), a seventh (D-07), and a rewrite of
  criterion 5 (D-14).**
- `.planning/ROADMAP.md` §"Phase 18" success criterion 2 — "until Phase 20 lifts the gate", one of the
  three documents assigning the gate deletion here
- `.planning/ROADMAP.md` §"Phase 21: Overnight Shift Templates" — where OVNT-01 relaxes the save-path
  forward-interval refusal and OVNT-07 owns calendar-span label disclosure (D-10)
- `.planning/REQUIREMENTS.md` §"What research established" — item 1 (only `Timeslot` carries two dates;
  every other date-bearing fact is already business-date-shaped) grounds D-09's token choice; item 2
  (the failure mode is a silent non-join, not an error) is why SOLV-06 asserts match counts
- `.planning/REQUIREMENTS.md` §"Relationship to the cancelled v1.4" — the constructed-vs-live
  conflation that cancelled v1.4; **read before implementing D-14**
- `.planning/REQUIREMENTS.md` §"Decisions taken at scoping" — the rejected `BusinessDate` wrapper type
  and the stored-column-with-write-path-guard pattern, neither relitigated here
- `.planning/phases/19-daywindow-re-anchoring/19-CONTEXT.md` — D-01 (gate ownership moved to SOLV-01),
  D-04/D-08 (the bound-instance `DayWindow` and the `ScheduleConfig.dayStart` channel this phase
  consumes), D-07 (the midnight-anchor allowlist this phase shortens), D-10 (additive-then-consume
  commit shape D-12 mirrors), and the **Deferred Ideas** entry handing the non-midnight scenario proof
  to Phase 20 (D-11)
- `.planning/phases/18-business-day-foundation-guards/18-CONTEXT.md` — D-06 (15-minute target range),
  D-07 (one visible validation line), D-08 (the generation-time tiling refusal D-02 makes reachable),
  D-22 (`periodStart`/`periodEnd` mean business dates — the evidence under D-05), D-24 (cite
  requirement IDs, never phase numbers)

### The joins and anchors being migrated

- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — the `DATE` lambda at `:91-92` and its
  7 `groupBy(AGENT_ID, DATE, …)` consumers (`:299, :356, :389, :438, :692, :718, :1109`); the 11 direct
  `getDate()` sites (`:175, :222, :510, :608, :678, :745, :916, :972, :1028, :1082, :1193`);
  `PENDING_DESK_ANCHOR` at `:58-66` and its 19 call sites; the SLOT-mode contracted-hours constraints
  at `:704, :730, :745` (SOLV-04); `minimumStaffing` at `:871`
- `src/main/java/com/wfm/model/Timeslot.java:38,:64` — the stored `business_date` column and
  `getBusinessDate()`, both landed by Phase 18
- `src/main/java/com/wfm/model/AgentShiftAssignment.java:58,:126` — the `date` field D-05 keeps derived
  and annotates
- `src/main/java/com/wfm/model/AgentDayConfig.java:16` — the record's `LocalDate date` component, the
  middle link in D-06's derivation chain
- `src/main/java/com/wfm/service/SolverService.java:841-849` — `computeAgentDayConfigs`, where the
  schedule period becomes `AgentDayConfig.date`; `:352-354` `buildShiftAssignments`; `:1304-1316` and
  `:1460-1560` the band-capacity / seat-supply shortfall reporting (SOLV-05)
- `src/main/resources/db/migration/V41__agent_shift_assignment.sql` — "one row per working agent-day",
  and the "can never disagree by construction" design note D-05 rests on

### Coverage reporting and library generation (SOLV-07)

- `src/main/java/com/wfm/service/ScheduleOutputService.java` — grouping keys at `:62, :71, :164, :173,
  :323, :739` (migrate); display labels at `:664, :759` (D-10, do not migrate);
  `explain()`/`getConstraintMatchTotalMap()` at `:628-650`; **`:582-597`, which refuses to call
  `explain()` on the ACCEPTED/DB path — the constraint that shaped D-14**
- `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java:180,:486` — the two calendar-weekday
  derivations D-13 migrates
- `src/main/java/com/wfm/service/StaffingRequirementService.java:172-175,:378` — demand's date handling;
  note `StaffingRequirement` stores **no** date of its own and derives through its `Timeslot`, so
  SOLV-07 is about which accessor each consumer calls, not divergent stored values

### The gate

- `src/main/java/com/wfm/service/DeskService.java:207-258` — `setDayStart`: the javadoc at `:211` naming
  SOLV-01, the gate at `:236-237` (D-01 deletes, D-02 replaces), and the unconditional
  accepted-schedule refusal at `:247-253` which this phase leaves untouched
- `frontend/src/pages/DeskManagement.tsx:109-124` — the disclosure copy D-03 corrects
- `frontend/src/api/client.ts:106-107` — the `PUT /desks/{id}/day-start` client call

### Guards — the precedents and the allowlists

- `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` — the three-scan,
  both-directions machinery; `:307-325` `theMidnightAnchorScanDetectsAFreshOccurrence`, which already
  asserts the `PENDING_DESK_ANCHOR` shape is caught and a real desk anchor is not
- `src/test/resources/midnight-time-arithmetic.md` §"Permitted midnight anchors" (`:177-192`) — the
  entry D-07 requires be deleted
- `src/test/java/com/wfm/service/BusinessDateWritePathGuardTest.java` — BDAY-08's one-derivation-site
  guard; the pattern D-06 copies for a *derived* value
- `src/test/java/com/wfm/service/UsualShiftWritePathGuardTest.java`,
  `SolverUsualShiftWritePathGuardTest.java`, `src/test/resources/ushf-05-write-paths.md`,
  `src/test/resources/bday-02-write-paths.md` — the other proven allowlist guards
- `src/test/java/com/wfm/solver/ScheduleConstraintClassification.java` and
  `ScheduleConstraintClassificationTest.java` — the reflective constraint-set enforcement that keeps a
  per-constraint table honest; the precedent for D-12's assertion set

### Fixtures and existing proof

- `src/test/java/com/wfm/solver/MidnightBoundaryFixture.java` (653 lines) and
  `MidnightBoundaryRegressionTest.java` — BDAY-06's three constructed scenarios, all at a `00:00`
  anchor; D-11 extends them to `21:00`
- `src/test/resources/midnight-boundary-scenarios.md` — the non-vacuity validator's scenario manifest,
  which must grow with D-11's scenarios
- `src/test/java/com/wfm/solver/ScheduleConfigAnchorPlumbingTest.java` — Phase 19's proof that the
  anchor reaches the solver; the channel D-07's migration consumes
- `src/test/java/com/wfm/service/TimeslotGeneratorBusinessDateTest.java` — BDAY-03's `21:00` generator
  proof, the existing positive non-midnight evidence
- `src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java`,
  `src/test/java/com/wfm/service/ShiftEnvelopeSupplyGateTest.java`,
  `ShiftModeMinimumStaffingSeatSupplyTest.java` — the existing seat-supply proofs SOLV-05 extends

### Project conventions

- `.planning/codebase/CONVENTIONS.md`, `.planning/codebase/TESTING.md`
- `./CLAUDE.md` or `./.claude/CLAUDE.md` if present

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **The anchor already reaches the solver.** Phase 19 landed `ScheduleConfig.dayStart`, filled once in
  `SolverService.buildSchedule` from the `Desk`, with `ScheduleConfigAnchorPlumbingTest` proving it.
  D-07's migration consumes an existing channel; it builds no new plumbing.
- **`shiftEnvelopeCompliance` is already migrated** and is the worked example for the other 18 anchor
  sites: it binds a `DayWindow` per match from the joined `ScheduleConfig` rather than using
  `PENDING_DESK_ANCHOR`. The in-code comment at `:511-513` states the pattern.
- **The two-directional allowlist guard pattern is proven four times** in this codebase and fails on
  both an unlisted new occurrence and a stale entry whose line has gone — D-06 and D-08 copy it rather
  than inventing a mechanism.
- **`explain().getConstraintMatchTotalMap()`** already exists as the per-constraint match-count source
  (`ScheduleOutputService:628-650`); D-14's fixture reads the same API in-process rather than over HTTP.
- **`ScheduleConstraintClassification`** already enforces by reflection that a hand-maintained
  per-constraint map agrees with both `ConstraintWeights`' `@ConstraintWeight` annotations and
  `ScheduleConstraintProvider`'s builder methods — "adding a twentieth constraint fails the build until
  someone adds a row here". D-12's assertion set should inherit this property, not re-derive it.
- **Phase 18's D-08 tiling refusal is already written and tested**, merely unreachable. D-02 makes it
  reachable; nothing new is built for it.

### Established Patterns

- **Guards land before the change they police**, and each carries a red-proof that it can actually
  fail. D-11 applies this to scenarios as well as guards.
- **Additive first, consume second** (Phase 18's anchored helpers, Phase 19's `ScheduleConfig` field).
  D-12's one-commit migration is the *consume* half of a pattern whose additive half already shipped.
- **Comments state their reason inline and cite requirement IDs, never phase numbers** (D-24) — phase
  numbers have demonstrably moved between v1.4 and v1.5, and twice within v1.5 for this very gate.
- **A placeholder carries a removal owner in its name and in an allowlist** — `PENDING_DESK_ANCHOR` is
  the pattern Phase 19 used on Phase 20, and the pattern D-13 declined to use on Phase 21.
- **`00:00` means different things by position**: a time equal to the anchor in an end position is the
  end of the business day. `DayWindow`'s class javadoc is the clearest statement of this anywhere in the
  codebase.

### Integration Points

- `ScheduleConfig.dayStart` → `Schedule.getScheduleConfig()` → every constraint in
  `ScheduleConstraintProvider` — the single channel for all 19 anchor sites
- `Timeslot.getBusinessDate()` → the 12 join/group key positions
- `schedule.period{Start,End}Date` → `AgentDayConfig.date` → `AgentShiftAssignment.date` — D-06's
  guarded chain
- `DeskService.setDayStart` → `Desk.dayStart` → `TimeslotGeneratorService` (which already takes
  `dayStart` as a parameter) → Phase 18's D-08 tiling refusal
- `src/test/resources/midnight-time-arithmetic.md` → `MidnightTimeArithmeticGuardTest`'s three scans,
  one of which loses its only entry in this phase

### Constraints the architecture imposes

- **Per-constraint match counts are only available on the live solving path.**
  `ScheduleOutputService:582-597` deliberately never calls `explain()` on the ACCEPTED/DB path, because
  explaining a score from a mis-restored director is worse than reporting nothing. This is not an
  oversight to work around — it is what makes D-14's in-process fixture the cheap option.
- **The 4 MB detail payload competes with the solver for its two cores**, so a live before/after via
  `GET /schedules/{id}` is not a viable measurement path.
- **Two constraints are touched by both migrations** — `shiftEnvelopeCompliance` and
  `shiftWorkContiguity` carry both an `AgentShiftAssignment`↔`Timeslot` date join and
  `PENDING_DESK_ANCHOR` interval maths. D-12's single commit absorbs that overlap; a split-by-migration
  history would not have.

</code_context>

<specifics>
## Specific Ideas

- **`PENDING_DESK_ANCHOR` and the `ShiftLibraryGenerationService` weekday sites were found during this
  discussion, not inherited as known open items.** The ROADMAP entry names neither. D-07 and D-13 make
  them visible; a planner working from ROADMAP + REQUIREMENTS alone would size SOLV-03 as test-only and
  would not touch library generation at all.
- **The ROADMAP's "re-grep the 12 `timeslot.getDate()` joins count fresh" action is discharged:**
  re-measured on HEAD at 2026-10-01, still exactly **12** in `ScheduleConstraintProvider.java`. The
  planner does not need to re-derive it, but should re-verify if the tree moves before execution.
- **The roadmap calls SOLV-04 "a defect that is already live today, independent of overnight shifts."**
  Worth a sceptical look during research: with every desk at a `00:00` anchor, business date equals
  calendar date, so it is not obvious what is live. The sites are real (`:704, :730, :745, :692, :718`);
  the "already live" framing may be the part that is wrong. Do not let an unfalsifiable "already live"
  claim become an untestable acceptance criterion.
- **The guard's own documentation is a deliverable, not a side-effect.** D-10's reasoning (display is
  out of scope *by construction*) and D-09's reasoning (why three of the four candidate tokens are not
  scanned) both belong in the guard's docs, so the next reader does not file either as a gap. Phase 19
  established the precedent by documenting a guard's *blind spot* in the allowlist file itself.
- **`ScheduleOutputService` holds both kinds of site in one file.** Any instruction phrased as "migrate
  `ScheduleOutputService`'s date usage" is wrong in one direction or the other; tasks must name the
  specific line positions.

</specifics>

<deferred>
## Deferred Ideas

- **Rename `date` → `businessDate` across all six business-date-shaped problem facts**
  (`AgentShiftAssignment`, `AgentDayConfig`, `AgentDayOff`, `AgentPreference`,
  `ResolvedUsualShiftTarget`, `ShiftStartMixTarget`). A real comprehension win — it would make
  `sa.getBusinessDate() == ts.getBusinessDate()` read correctly at every join — and it is
  compiler-forced, which this project values. Rejected for this phase only, on width: ~200 accessor
  sites across main and test, inside the milestone's riskiest phase. Renaming fewer than all six is
  worse than renaming none, because it implies the unrenamed ones are calendar dates.
- **An editable day-start control in the desk-configuration UI** — needs a time picker plus error
  surfacing for the 15-minute-boundary refusal and the accepted-schedule refusal. Phase 21, with
  OVNT-07's disclosure work.
- **OVNT-07's calendar-span label disclosure** — showing both the business date and the calendar date
  on a timeslot label, so a night desk's roster is never ambiguous. D-10 keeps the labels unchanged
  until then. Phase 21.
- **Tightening `ScheduleConfig`'s defensive null-anchor fallback** once no pre-Phase-19 `Schedule` can
  reach the solver. Listed under Claude's Discretion for this phase; if not taken, it carries forward.
- **Restoring a genuine live-desk drift check** for Phil-US, with whatever data or endpoint path makes
  per-constraint match counts cheap to capture against a running system. D-14 gives this up for v1.5;
  MIGR-01..04 (the Phil-US migration, deferred out of v1.5) is its natural home.
- **The three BDAY-06 scenarios Phase 18 deferred** (18-CONTEXT D-16) — Phase 21, once OVNT-01 makes
  them constructible.
- **Deleting the frozen `DayWindow` oracle** (19-CONTEXT D-13) once v1.5 ships.

### Reviewed Todos (not folded)

All three `todo.match-phase` hits matched on generic keywords, and none touches business-date
resolution, the solver's joins, or interval anchoring. Phase 19 reviewed and declined the same three
for the same reasons:

- *Provide a blank upload template spreadsheet, one sheet per desk* (`upload`, score 0.9) — upload
  tooling. Matched on "upload", "one", "per", "desk". Unrelated to date resolution; note the known
  destructive-upload hazard lives there, not here.
- *Cross-agent seat displacement for the atomic shift move* (`solver`, score 0.9) — a solver move
  selector. Matched on "agent", "seat", "shift". **Phase 20 changes no move selectors**, and move
  selectors are a known hazard area in this codebase; keeping them out of a phase that re-keys every
  join is deliberate.
- *Terraform state diverges from live RDS password and `publicly_accessible`* (`infra`, score 0.6) —
  infrastructure drift. Matched on "live" and "phase" alone.

</deferred>

---

*Phase: 20-solver-business-date-correctness*
*Context gathered: 2026-10-01*
