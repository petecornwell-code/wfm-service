# Roadmap: WFM Service

## Milestones

- ⚠ **v1.0 AWS Deployment** — Phases 1–4 (partially shipped 2026-04-21; IAM blocker — see Backlog 999.1–999.3)
- ⚠ **v1.1 Schedule Quality & Reporting** — Phases 5–8 (closed early 2026-07-29; 5–6 shipped, 7–8 deferred — see Backlog 999.4–999.6)
- ✅ **v1.2 Unified Agent Provisioning** — Phases 9–13 (shipped 2026-08-25; override closeout — see Backlog 999.9)
- ✅ **v1.3 Shift-Based Scheduling & Consistency** — Phases 14–17 (shipped 2026-09-21; override closeout, 43/43 requirements, 10 artifacts acknowledged)
- ✗ **v1.4 Overnight Shifts & Business Dates** — Phases 18–23 (cancelled 2026-09-30 before shipping; all work unwound, one salvaged defect fix retained — see MILESTONES.md)
- 🚧 **v1.5 Overnight Shifts & Business Dates** — Phases 18–22 (in progress; started 2026-09-30 as v1.4's successor — phase numbers 18–23 were not reused, since v1.4 shipped nothing)

## Phases

### 🚧 v1.5 Overnight Shifts & Business Dates (In Progress)

**Milestone Goal:** A shift can span midnight and belongs to the business day it starts on — for
any desk, any day start, any future data set.

**Phase numbering continues from v1.3's Phase 17.** v1.4 attempted this same scope under Phase
numbers 18–23 and was cancelled 2026-09-30 before shipping, with all 32 commits unwound (see
MILESTONES.md and the `## Milestones` entry above) — its numbers are not reserved. v1.5 restarts at
**Phase 18** with a redesigned regression-proof strategy: constructed midnight-spanning scenarios
prove correctness, and one small live desk (Phil-US, 48 agents) is demoted to a drift guard, rather
than four captured live desks standing in for both jobs at once.

- [x] **Phase 18: Business-Day Foundation & Guards** - Guard tests and constructed regression scenarios exist and pass green against today's `00:00`-only behaviour; desk day-start and timeslot business-date schema lands gated to a provable no-op
- [x] **Phase 19: DayWindow Re-anchoring** - `DayWindow`'s interval arithmetic is re-anchored on a caller-supplied day start in one atomic, compiler-forced, revertible change (completed 2026-10-01)
- [x] **Phase 20: Solver Business-Date Correctness** - Every solver join, the seat-supply check, SLOT-mode accounting and demand/coverage reporting resolve the same business date, proven by match counts, with one live desk showing nothing else moved (completed 2026-10-02)
- [ ] **Phase 21: Overnight Shift Templates** - A shift can span midnight, save-time validation and contracted-hours consumption treat it as belonging to its starting business day, and the grid/export render it as one continuous block
- [ ] **Phase 22: Minimum Rest** - A per-desk minimum rest period is enforced as a hard constraint with a pre-solve refusal and a per-agent, per-date waiver

### Phase 18: Business-Day Foundation & Guards

**Goal**: The guard tests and regression fixtures that will prove the re-anchoring correct already
exist and pass green against today's `00:00`-only behaviour, and the schema/plumbing for a per-desk
day start exists as a provable no-op — so the re-anchoring in Phase 19 is provably the first change
that could make any of this red.
**Depends on**: Nothing new — first phase of v1.5, continuing from v1.3's Phase 17
**Requirements**: BDAY-01, BDAY-02, BDAY-03, BDAY-05, BDAY-06, BDAY-08
**Success Criteria** (what must be TRUE):

  1. A desk can store a day-start time (surfaced in the desk configuration UI, but gated to accept
     only `00:00` in production) and a timeslot can store a business date, and every existing desk's
     business date is provably identical to its calendar date — by construction, not convention
     (BDAY-01, BDAY-02).
  2. `TimeslotGeneratorService`, given a desk object configured with a 21:00 day start, generates a
     contiguous 24-hour run of timeslots whose business date is the same single date across the
     whole span — proven by a direct unit test even though the production API still refuses to save
     that value on a live desk until Phase 20 lifts the gate (BDAY-03).
  3. A structural guard test fails the build if any scheduling-time comparison (`isAfter`/`isBefore`/
     `compareTo`) or arithmetic operation bypasses the shared `DayWindow` utility, and a dedicated
     test proves the guard can actually go red on a freshly introduced offending line (BDAY-05).
  4. A constructed regression suite of midnight-boundary scenarios — chosen by the property under
     test (a 23:00–00:00 slot, an envelope flush to end-of-day, a shift starting before and ending
     after midnight, a break band touching an envelope edge, PTO on the starting vs. ending day,
     contracted-hours-starting-weekday-only) — exists and passes against today's implementation, with
     a class-load validator that fails the build if a named boundary case goes missing (BDAY-06).
  5. A write-path guard test proves exactly one code path derives a timeslot's business date and
     exactly one propagates it forward, failing in both directions — on an unexpected new writer and
     on a stale allowlist entry (BDAY-08).
**Notes**: Standard, well-documented pattern — skip a dedicated research phase. Direct reference
implementations exist both in this codebase (`MidnightTimeArithmeticGuardTest`,
`ShiftDeskEndToEndRegressionTest`'s scoring-only pattern) and on v1.4's unwound branch
(`84fdc3f` schema, `2196e40` write paths, `7d42f23` write-path guard, `63d85a6` comparison-operator
guard extension, at tag `rescue/phase-18-unwind-20260930`). **Open decision for phase planning:**
cherry-pick those four commits or re-author fresh against the current tree, which has since had at
least one relevant fix land (`5ddd8dc`) — not settled by research. **Open decision:** the golden-file
justification-log enforcement mechanism for the BDAY-06 fixture — three ranked options existed in
v1.4's own research (hash-based recommended), never settled. A `day_start` control shown in the UI
before it does anything must make the `00:00`-only restriction explicit in its copy, not enforce it
silently via a backend 400. **Both open decisions were resolved at planning time (2026-09-30):** the
five named rescue-tag commits are cherry-picked and `985e365`'s generator change is re-authored
(18-CONTEXT.md D-23), and the golden-file enforcement question is recorded as a void premise rather
than an unsettled choice — it belonged to v1.4's captured-live-desk design, and constructed scenarios
have knowable answers (D-13). Planning also found one contradiction between two locked decisions,
resolved as planner decision P-01 in `18-01-PLAN.md`.
**Plans**: 6 plans
Plans:

**Wave 1**

- [x] 18-01-PLAN.md — Tracer: day-start and business-date land end to end, every write path green (BDAY-01, BDAY-02)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 18-02-PLAN.md — Accepted-schedule refusal, day-start disclosure, migration reconciliation (BDAY-01, BDAY-02)
- [x] 18-03-PLAN.md — Anchored DayWindow helpers and the business-day generation walk (BDAY-03, BDAY-02)
- [x] 18-04-PLAN.md — Constructed midnight-boundary regression suite with a non-vacuity validator (BDAY-06)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 18-05-PLAN.md — Business-date write-path guard and V53 through real Flyway (BDAY-08, BDAY-02)
- [x] 18-06-PLAN.md — Comparison-operator guard extension and its pipeline-level red-proof (BDAY-05)

**UI hint**: yes

### Phase 19: DayWindow Re-anchoring

**Goal**: `DayWindow`'s interval arithmetic is anchored on a caller-supplied day start instead of an
implicit midnight, in one atomic, compiler-forced, revertible change — proven behaviour-preserving
for every desk still at the `00:00` default.
**Depends on**: Phase 18 (guard tests must be green before this lands — there is nothing to compare
against otherwise)
**Requirements**: BDAY-04
**Success Criteria** (what must be TRUE):

  1. Every `DayWindow` call site supplies an explicit day-start parameter; the one-argument,
     midnight-implicit overload no longer exists, so a missed site is a compile failure, not a
     judgement call (BDAY-04).
  2. `durationMinutes` and the other position-aware functions no longer throw on an end time earlier
     than the start time — that condition now means "crosses the anchor," not "malformed" (BDAY-04).
  3. A parameterised unit test proves the re-anchored functions produce byte-identical output to the
     pre-migration implementation when day-start is fixed at midnight, run through `DayWindow`'s full
     existing test suite.
  4. Phase 18's guard tests (BDAY-05, BDAY-06) still pass green after the re-anchoring lands — proving
     the migration did not silently leave a drifted caller on old semantics.
  5. The change lands as its own isolated, `git diff --name-only`-provable commit — touching only
     `DayWindow` and its call sites, separate from the constraint-provider re-point that consumes it
     in Phase 20.
**Notes**: Standard, well-documented pattern — the compiler-forced-overload technique is fully
specified against three alternatives with no open design question remaining. **Action for phase
planning:** re-grep the exact `DayWindow` call-site count fresh (previously estimated at ~112
references across 16 files) before starting — that count was measured on a tree that has since
moved. This is the single riskiest edit in the milestone; isolate it exactly as scoped and do not
combine it with the join re-point that follows. **Re-grepped 2026-09-30:** 264 references across 27
files, of which 83 are production call sites of the nine midnight-implicit forms across 15 files.
**Planner decisions:** P-01 (the `ifExists`-gated constraints keep one named, allowlisted midnight
anchor that SOLV-01 removes — Timefold 1.16.0 has no Penta stream), P-04 (`schedule.day_start` is a
persisted column, V54), P-06 (criterion 5 is checked over the commit range from the additive
plumbing commit forward, not a single commit). All three are recorded in full in `19-01-PLAN.md`.
**Plans**: 8 plans, 6 waves

Plans:
**Wave 1**

- [x] 19-01-PLAN.md — Additive solver anchor channel as a provable no-op (V54, `Schedule`/`ScheduleConfig`/DTO); the range's exclusive lower bound (wave 1)
- [x] 19-02-PLAN.md — Frozen oracle: pin today's nine implementations and their throw domain while the originals are still callable (wave 1)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 19-03-PLAN.md — Tracer: `DayWindow.anchoredAt` plus nine instance methods, and one desk anchor reaches one constraint's interval check end-to-end (wave 2)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 19-04-PLAN.md — Break-band times and net hours take a `DayWindow` across every caller; controller served by the service; `breakClustering` wired (wave 3)

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 19-05-PLAN.md — Retire the transitional one-argument `ShiftBandPair.covers` and re-point its remaining callers (wave 4)

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 19-06-PLAN.md — Eight service files re-pointed to the instance API, save-path refusal preserved exactly (wave 5)
- [x] 19-07-PLAN.md — Solver helpers, the standalone generator, and D-07's third two-directional allowlist scan (wave 5)

**Wave 6** *(blocked on Wave 5 completion)*

- [x] 19-08-PLAN.md — Demote the nine to private, flip the oracle, invert the reflective guard, prove criteria 4 and 5 (wave 6)

### Phase 20: Solver Business-Date Correctness

**Goal**: Every solver join, the pre-solve seat-supply check, SLOT-mode accounting, and demand
upload/coverage reporting all resolve the same business date for the same timeslot — proven by
per-constraint match counts, not just score — and one small live desk shows the re-anchoring changed
nothing it shouldn't have.
**Depends on**: Phase 18 (`Timeslot.getBusinessDate()` must exist), Phase 19 (`DayWindow` must
correctly express overnight intervals for the constraints that do interval math, not just
date-equality joins)
**Requirements**: SOLV-01, SOLV-02, SOLV-03, SOLV-04, SOLV-05, SOLV-06, SOLV-07, BDAY-07
**Success Criteria** (what must be TRUE):

  1. All business-date-relevant joins in `ScheduleConstraintProvider` (re-verified count, ~12 at
     research time) key on business date, moved in one deliberate pass, backed by a structural guard
     test that fails if any constraint joins on calendar date where business date is meant — in both
     directions (SOLV-01, SOLV-02).
  2. Break-band, contiguity and envelope-compliance constraints hold correctly for an agent-day that
     spans midnight, and the pre-solve seat-supply check reports a shortfall against the business day
     it actually affects (SOLV-03, SOLV-05).
  3. An overnight SLOT-mode stretch counts against a single business day's contracted hours instead
     of under-allocating both calendar days it touches — closing a defect that is already live today,
     independent of overnight shifts (SOLV-04).
  4. Every migrated join carries a non-vacuity assertion and a per-constraint match-count assertion,
     so a silent non-join (zero matching tuples, scored identically to "satisfied") cannot pass as
     correct (SOLV-06).
  5. Demand upload, coverage reporting and the solver are proven — by a guard test, not convention —
     to resolve the same business date for the same timeslot, and a constructed fixture built to one
     small live desk's shape (Phil-US, 48 agents, not Vinted's 287) produces unchanged per-constraint
     match counts and score across the whole re-anchoring, decomposed enough that a failure names
     which constraint moved (SOLV-07, BDAY-07).
  6. `DeskService`'s `00:00`-only gate is gone and a desk's day start accepts any 15-minute boundary,
     refusing anything else by name at save time — landed as this phase's FINAL commit, after every
     join and anchor above is migrated and guarded. Phase 18's generation-time tiling refusal (a
     day-start that is not a whole multiple of the generation increment) becomes reachable through
     the API for the first time and is proven to fire (SOLV-01).
  7. `ScheduleConstraintProvider`'s `PENDING_DESK_ANCHOR` placeholder is gone, every one of its call
     sites (19 at planning time) reads the desk's real anchor from the joined `ScheduleConfig`, and
     its entry in `src/test/resources/midnight-time-arithmetic.md` is removed — with the guard going
     red if either the constant or the allowlist entry returns (SOLV-03).
**Notes**: Standard, well-documented pattern — the join-migration technique and guard-test shape are
directly copied from two proven precedents in this codebase. **Planning action discharged:** the "12
`timeslot.getDate()` joins" count was re-measured on HEAD 2026-10-01 and is still exactly 12;
re-verify if the tree moves before execution. **Resolved (20-CONTEXT D-08/D-09):** the
`BusinessDateJoinGuardTest` allowlist is empty for key positions by construction — it scans only
`join`/`equal`/`groupBy`/`computeIfAbsent` key positions for `.getDate()` on a `Timeslot` receiver,
so display sites are out of scope without needing an allowlist entry. **Resolved (20-CONTEXT D-05):**
`agent_shift_assignment` gets no `business_date` column — its `date` already IS the business date,
derived from `AgentDayConfig` through the schedule period, guarded by a derivation-chain test (D-06)
instead of duplicated into a stored column. SOLV-07 (the anchor-agreement guard between demand
upload/coverage and the solver) was accepted into v1.5 scope by operator decision 2026-09-30, flagged
by research as a real gap, not an assumed by-product of SOLV-01..04 — treat it as its own deliverable
here. Two items absent from this entry were surfaced in discussion and are now criteria 6 and 7;
`ShiftLibraryGenerationService`'s two calendar-weekday derivations migrate in this phase as well
(20-CONTEXT D-13).
**Planner decisions:** P-01 (the join guard is a NEW class, `BusinessDateJoinGuardTest`, not a fourth
scan in `MidnightTimeArithmeticGuardTest` — taking D-08's discretion, following
`BusinessDateWritePathGuardTest`'s own precedent). P-02 (the anchor carrier is `AgentDayConfig.dayStart`
plus a 10-argument delegating constructor, so 34 existing test construction sites compile unchanged and
the carrier commit is provably additive). P-03 (`honourPreferredBreakTime`'s anchor mechanism is NOT
pre-decided — it is a blocking `checkpoint:decision` in `20-05-PLAN.md`, because both candidate
mechanisms end a grouping-node sharing arrangement six sibling constraints rely on and both can move a
match count two committed baselines pin). P-04 (SOLV-05 is discharged by the seat-supply gate's key fix
alone; the schedule-wide capacity warning is NOT widened — it has no per-day key to mismatch, and the
gate is what blocks a solve). P-05 (D-15's delete gets a SECOND repository method filtering on business
date rather than re-pointing the shared one, because two Erlang callers still pass operator-supplied
calendar dates — both recorded by name in the guard's documentation as unreachable instances today).
P-06 (`ScheduleConfig`'s defensive null-anchor fallback is NOT tightened — 34 test construction sites
still reach the solver through midnight-supplying delegating constructors, so it stays load-bearing;
carried forward per CONTEXT.md's Deferred Ideas). **Re-grepped on HEAD 2026-10-01:** the placeholder
anchor appears on 19 lines in `ScheduleConstraintProvider.java` including its declaration, with one line
carrying two occurrences — 19 call-site occurrences across 18 lines; `getDate()` appears at 12 sites, of
which 8 need migration and 4 are already business-date-shaped. **Amendments D-04, D-07 and D-14 are
already discharged** — criteria 6 and 7 exist and criterion 5 is reworded, and `REQUIREMENTS.md`'s
BDAY-07 carries its 2026-10-01 amendment.
**Plans**: 12/12 plans complete — 8 original (6 waves), plus 4 gap-closure plans across two rounds (20-09..20-11 in round 1, 20-12 in round 2; each round's waves are its own, run with `/gsd-execute-phase 20 --gaps-only`)

Plans:
**Wave 1**

- [x] 20-01-PLAN.md — Tracer: carry the desk anchor into a Quad-arity constraint's reach (additive, zero behaviour change), then three 21:00-anchored scenarios red against it (wave 1)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 20-02-PLAN.md — The two structural guards: `BusinessDateJoinGuardTest` over four files (red) and `AgentDayDerivationGuardTest` over D-06's chain (green) (wave 2)
- [x] 20-03-PLAN.md — The two per-constraint count proofs: SOLV-06's reflective anchor-invariance table (red) and BDAY-07's 48-agent Phil-US-shaped drift baseline (green) (wave 2)
- [x] 20-04-PLAN.md — SOLV-04's constructed SLOT-mode overnight proof (red) and SOLV-05's seat-supply key-system fix (wave 2)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 20-05-PLAN.md — THE migration, one deliberate commit: 8 key-position edits, 19 anchor occurrences across 6 constraints, the placeholder and its allowlist row gone — with a blocking decision checkpoint for `honourPreferredBreakTime` (wave 3, not autonomous)

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 20-06-PLAN.md — SOLV-07's read paths: 6 coverage grouping keys and 4 library-generation key positions, two display labels deliberately unchanged (wave 4)

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 20-07-PLAN.md — D-15's destructive delete range and its repository filter moved together, proven by observing survivors; the join guard goes green (wave 5)

**Wave 6** *(blocked on Wave 5 completion)*

- [x] 20-08-PLAN.md — The gate deletion and the 15-minute refusal, with the disclosure copy corrected first and the generation-time tiling refusal proven to fire — this phase's FINAL commit (wave 6)

**Gap closure** *(from `20-VERIFICATION.md`, status `gaps_found`, 6/8 must-haves. Run with `/gsd-execute-phase 20 --gaps-only`; the wave numbers below are this run's own, not a continuation of waves 1-6.)*

- [x] 20-09-PLAN.md — GAP 1 (criterion 6, SOLV-01): the generate-timeslots endpoint reads the desk's own day start through a tenant-scoped lookup, proven through the real controller path, and the save-time gate refuses sub-minute precision by name (gap-closure wave 1)
- [x] 20-10-PLAN.md — GAP 2 (the phase goal's "every solver join", CR-02): `expandMinimumStaffingSeats` resolves the business date at both reads, proven on a 21:00-anchored desk; advisory 2's stale pre-migration javadoc corrected (gap-closure wave 2)
- [x] 20-11-PLAN.md — Advisory 1 discharged: the two further `Timeslot` calendar-date reads the audit found in `runPreSolveValidation` migrated and proven, and the full `SolverService` enumeration plus the join-guard scope decision recorded in `bday-join-guard.md` (gap-closure wave 3)

**Gap closure, round 2** *(from `20-REVIEW.md` CR-01/WR-01/IN-01, status `issues_found`. Run with `/gsd-execute-phase 20 --gaps-only`.)*

- [x] 20-12-PLAN.md — CR-01: the four problem-fact and snapshot fetches that pass business-date period bounds into calendar-date finders load through one shared widen-then-derive loader (`BusinessDayPeriodLoader`), mirroring `TimeslotGeneratorService`'s BDAY-03 read-back; proven end-to-end on the accept path at a 21:00 anchor with a midnight control. WR-01: the repository derived-query-name / JPQL `t.date` blind spot recorded in `bday-join-guard.md`. IN-01 folded in (gap-closure round 2, wave 1)

### Phase 21: Overnight Shift Templates

**Goal**: A desk can define a shift that spans midnight, and every surface that touches it — save-time
validation, contracted-hours consumption, day-off blocking, the schedule UI grid, the Excel export —
treats it correctly as one continuous thing belonging to the business day it starts on.
**Depends on**: Phase 19 (`DayWindow.durationMinutes` must not throw on an overnight interval), Phase
20 (business-date joins must be trustworthy)
**Requirements**: OVNT-01, OVNT-02, OVNT-03, OVNT-04, OVNT-05, OVNT-06, OVNT-07
**Success Criteria** (what must be TRUE):

  1. An operator can save a shift template whose end time is earlier in the clock than its start
     time, with correct net hours computed for the overnight span (OVNT-01).
  2. That shift is reported, everywhere it is displayed, against the business day it starts on
     (OVNT-02), and a day-off or PTO marking on that starting business day blocks the shift from
     being assigned (OVNT-03).
  3. The shift consumes the contracted hours of the weekday it starts on only, never split across two
     weekday rows (OVNT-04), and shift-library validation refuses an overnight template whose
     envelope does not fit inside its desk's business day (OVNT-05).
  4. The schedule UI grid and the Excel export render the overnight shift as one continuous block,
     never two fragments, with a distinct, non-blank, non-duplicate continuation indicator on the
     morning-after cell (OVNT-06).
  5. The shift is labelled with the calendar dates it spans wherever it is displayed, so a
     business-day-anchored surface still discloses that it runs into the next calendar day (OVNT-07).
**Notes**: Research flag — genuinely needs a design pass at plan time. The Excel/UI
continuation-indicator design is specified conceptually ("a distinct, non-blank, non-duplicate
treatment") but the actual visual/legend convention needs a concrete pass against the existing
cell-code legend before planning locks it in. This phase is also the natural place to close part of
the pre-existing v1.3 "it will still save" envelope-vs-operating-window gap for the overnight case
specifically (OVNT-05) — do not let the old advisory-only behaviour persist here. If Phase 20 decides
`agent_shift_assignment` needs its own `business_date` column, this is the phase that populates and
reads it for overnight shift assignment.
**Plans**: 12 plans
Plans:

**Wave 1**
- [ ] 21-01-PLAN.md — Tracer: an overnight template saves on a 21:00-anchored desk; the refusal on a 00:00 desk names the anchor; a 16-hour span cap (D-02, D-08, OVNT-01 registry flip)
- [ ] 21-02-PLAN.md — Desk day-start reachability, backend: the stranded-template refusal, the non-blocking tiling advisory, the permanent-lock disclosure fields (D-03, D-04, D-05)
- [ ] 21-03-PLAN.md — Additive-first: structured business/calendar date and times on the violation DTO, and the export's parser moved onto them (D-14, first half)
- [ ] 21-04-PLAN.md — Scope decision + outcome: which date system the two Erlang calculators' clear-before-insert range means (checkpoint, surfaced by 20-CONTEXT and this phase's research)

**Wave 2**
- [ ] 21-05-PLAN.md — OVNT-05: one operating-window containment predicate shared by the save path, the library report, the mode gate and candidate generation (D-06, D-07, D-09, D-10, P-03)
- [ ] 21-06-PLAN.md — The genuinely midnight-crossing fixture, the solver's anchored scan ranges, and the last two registry flips (D-18, D-19, P-02)
- [ ] 21-07-PLAN.md — Excel: the spelled-out overnight roster cell with its vertical legend, and anchored allocation columns and rows (D-12, D-13, P-01)
- [ ] 21-08-PLAN.md — The editable day-start control, its locked render, and every refusal surfaced as the server's own message (D-01, D-04, D-05)
- [ ] 21-09-PLAN.md — Contracts: the schedule's anchor on both payloads, and a new branded-offset frontend day-window module with no consumers yet (D-15, first half)

**Wave 3**
- [ ] 21-10-PLAN.md — The grid converted onto anchored time: all seven defect sites in both render branches, business-day span disclosure, structured unfilled-seat attribution (D-15, D-16, D-17)

**Wave 4**
- [ ] 21-11-PLAN.md — OVNT-07's label text, now that nothing parses it, plus the ROADMAP and REQUIREMENTS amendments for the void continuation-indicator premise (D-11, D-14, second half)

**Wave 5**
- [ ] 21-12-PLAN.md — Scope decision + outcome: nine build-allowlisted raw time comparisons whose "both operands are start times" justification does not hold at a non-midnight anchor (checkpoint, P-04)

**UI hint**: yes

### Phase 22: Minimum Rest

**Goal**: An operator can require a minimum gap between an agent's consecutive shifts, enforced as a
hard constraint the solver cannot silently violate, with a pre-solve refusal for the structurally
unavoidable cases and a per-agent, per-date waiver for the genuinely exceptional ones.
**Depends on**: Phase 19 (interval arithmetic across a business-date boundary), Phase 21 (overnight
shift templates must exist for consecutive shifts to actually be adjacent to each other)
**Requirements**: REST-01, REST-02, REST-03, REST-04, REST-05, REST-06, REST-07
**Success Criteria** (what must be TRUE):

  1. An operator can set a minimum rest period between consecutive shifts, per desk, via the desk
     configuration UI, and a desk that sets none solves exactly as it does today (REST-01, REST-04).
  2. The solver treats insufficient rest — measured between actual end and start instants, ordered by
     actual start instant rather than calendar-date bucket, covering same-day back-to-back shifts as
     well as overnight ones — as a hard violation, with explicit, tested behaviour at the first and
     last day of the solving horizon rather than an accidental one (REST-02, REST-05).
  3. A rest violation that is structurally unavoidable is refused before the solve runs, naming the
     agent and the two shifts, by a mechanism genuinely separate from the in-solve hard constraint —
     not a hoped-for side effect of it (REST-03).
  4. An operator can waive minimum rest for one agent on one business date, with a recorded reason,
     through the existing per-agent exception mechanism; the solver treats a waived pair as legal, and
     a waived occurrence does not trigger the pre-solve refusal (REST-06).
  5. A waived rest violation is visible in the solved schedule's output, so a waiver cannot silently
     hide a roster problem (REST-07).
**Notes**: Research flag — genuinely needs deeper research at plan time. The exact horizon-edge
lookback strategy (query cost bound; whether to fetch the agent's actual pre-horizon last shift or
accept a documented, tested blind spot) is not fully settled by the milestone's research, nor is how
the pre-solve refusal composes with a desk whose shift library structurally cannot avoid a rest
violation for some agent — distinct from the ENVL-07 seat-supply-gate precedent's simpler case. If a
horizon-edge lookback query is built, bound it to the maximum configured rest period across all desks
and batch it as one query, not N+1 per agent.
**Plans**: TBD
**UI hint**: yes

<details>
<summary>✅ v1.3 Shift-Based Scheduling & Consistency (Phases 14–17) — SHIPPED 2026-09-21</summary>

**Goal:** An agent works a recognisable, repeating shift — not a slot pattern the optimiser
reassembles from scratch every week.

- [x] Phase 14: Shift Library & Scheduling Mode (6/6 plans) — completed 2026-08-26
- [x] Phase 15: Shift Envelope, Breaks & Library Generation (20/20 plans) — completed 2026-08-27
- [x] Phase 16: Usual Shift Storage (5/5 plans) — completed 2026-09-04
- [x] Phase 17: Consistency Constraint & Drift Reporting (5/5 plans) — completed 2026-09-18

Full details: `.planning/milestones/v1.3-ROADMAP.md` · Requirements:
`.planning/milestones/v1.3-REQUIREMENTS.md` · Audit:
`.planning/milestones/v1.3-MILESTONE-AUDIT.md` · Phase artifacts:
`.planning/milestones/v1.3-phases/`

**Known gaps carried forward** (deferred by operator ruling OR-2, not missed):

- Blocked-break-hours has no enforcement point in SHIFT mode — a band at offset `0` or at
  `envelopeMinutes - duration` is legal at save time and scores `0hard`, so a "break" on the
  envelope boundary is really a late start or an early finish. Fix location settled as save-time in
  `ShiftTemplateService`; do not relitigate restoring the gated solver constraint.
- A template's envelope is never validated against the desk's operating window at save time.
  `TimeslotBoundsResponse.endTime()` is read by no caller and is the natural starting point.

</details>

<details>
<summary>✅ v1.2 Unified Agent Provisioning (Phases 9–13) — SHIPPED 2026-08-25</summary>

- [x] Phase 9: Agent Data Model (4/4 plans)
- [x] Phase 10: Enriched Upload (6/6 plans)
- [x] Phase 11: Merge Engine (5/5 plans)
- [x] Phase 12: Atomic Shift Move — **WITHDRAWN**, reverted in `299c42c`; effect inside the
      baseline's own noise spread and goal explicitly not claimed
- [x] Phase 13: Display Migration (8/8 plans)

Full details: `.planning/milestones/v1.2-ROADMAP.md` · Audit:
`.planning/milestones/v1.2-MILESTONE-AUDIT.md`

</details>

<details>
<summary>⚠ v1.1 Schedule Quality & Reporting (Phases 5–8) — CLOSED EARLY 2026-07-29</summary>

Phases 5–6 shipped; Phases 7–8 deferred to Backlog 999.4–999.6 (twelve requirements).

Full details: `.planning/milestones/v1.1-ROADMAP.md`

</details>

<details>
<summary>⚠ v1.0 AWS Deployment (Phases 1–4) — PARTIALLY SHIPPED 2026-04-21</summary>

Blocked by an IAM permissions gap (`iam:CreateRole` excluded from PowerUserAccess). Resume steps in
Backlog 999.1–999.3.

Full details: `.planning/milestones/v1.0-ROADMAP.md`

</details>

## Backlog

### Phase 999.1: Resume Phase 2 — OIDC & IAM Setup (BACKLOG)

**Goal:** Complete 02-02-PLAN.md — fix iam.tf bugs, create terraform.tfvars, apply IAM resources, capture role ARN
**Source phase:** 02 (Security Cleanup & OIDC Setup)
**Deferred at:** 2026-04-21 during v1.0 milestone archive — blocked on iam:CreateRole (PowerUserAccess excludes IAM)
**Blocker:** Requires root/admin AWS access to grant `WFMTerraformIAMPermissions` to `pete.cornwell@helpware.com`
**Plans:**

- [ ] 02-02: Fix iam.tf, terraform apply IAM resources, capture github-actions role ARN

### Phase 999.2: Resume Phase 3 — Infrastructure Verification (BACKLOG)

**Goal:** Complete 03-02-PLAN.md — verify RDS/ECS security groups, Secrets Manager injection, Flyway readiness; capture terraform outputs
**Source phase:** 03 (Infrastructure Provisioning)
**Deferred at:** 2026-04-21 during v1.0 milestone archive — IAM roles not yet provisioned (9 resources pending)
**Blocker:** Depends on 999.1 (IAM roles required before ECS task definition and service can be created)
**Plans:**

- [ ] 03-02: Verify infrastructure, capture outputs for CI/CD phase

### Phase 999.3: Phase 4 — CI/CD Pipeline & Go-Live (BACKLOG)

**Goal:** GitHub secret set, pipeline triggered, application live and verified at CloudFront URL
**Source phase:** 04 (CI/CD Pipeline & Go-Live)
**Deferred at:** 2026-04-21 during v1.0 milestone archive — Phase 3 incomplete
**Blocker:** Depends on 999.1 and 999.2
**Plans:**

- [ ] TBD — plan this phase once infrastructure is fully provisioned

### Phase 999.4: Solver Fairness & Hours Consistency (BACKLOG)

**Goal:** Solver distributes desirable weekend positions fairly and keeps each agent's daily hours consistent with their contracted pattern
**Source phase:** 06 (Solver Quality Constraints) — dropped during phase discussion, never re-homed
**Deferred at:** 2026-07-29 during v1.1 milestone close
**Requirements:** QUAL-02 (weekend-position fairness), QUAL-03 (day-to-day hours consistency)
**Design constraints carried forward:**

- Fairness constraints must be **soft score only** — hard fairness makes schedules infeasible
- Use **quadratic** penalties for hours consistency, not linear (avoids score traps)
- Interacts with QUAL-01: agents with a fixed BambooHR pattern have their weekend *determined*, so fairness may only apply to agents without a parseable field-4517 value

**Plans:**

- [ ] TBD

### Phase 999.5: Coverage, Utilization & Diagnostics (BACKLOG)

**Goal:** Operators can see where the schedule is thin, which agents are over- or under-utilised, whether preferences were honoured, and why PTO may not have synced
**Source phase:** 07 — planned in the v1.1 roadmap but never planned in detail or executed
**Deferred at:** 2026-07-29 during v1.1 milestone close
**Requirements:** RPT-01, RPT-02, QUAL-04, DIAG-01, DIAG-02
**Success criteria carried forward:**

1. Per-timeslot coverage table (demand FTEs, assigned count, gap, coverage %) colour-coded red/amber/green; missing demand data marked "No data", not "0% gap"
2. Agent utilization table (weekly hours, contracted hours, delta, overtime-risk flag); agents at/above contracted +5% highlighted
3. Preference satisfaction rate (% honoured) shown after a solve without requiring an export
4. PTO sync status panel showing which agents imported (date counts, approved/requested) and which failed, with reason
5. Week-over-week hours variance table per agent across accepted schedule history

**Also required here** (carried from Phase 6 debt):

- Operator-facing UI for data-gap and outlier agents — currently CloudWatch `log.warn` only
- Fix `loadSnapshotData()` missing problem facts for accepted schedules — **blocks 999.6 score breakdown**

**UI hint:** yes
**Plans:**

- [ ] TBD

### Phase 999.6: Export, Score Breakdown & Tuning (BACKLOG)

**Goal:** Operators can export publication-ready schedules, understand solver decisions, and adjust solver behaviour from the UI
**Source phase:** 08 — planned in the v1.1 roadmap but never planned in detail or executed
**Deferred at:** 2026-07-29 during v1.1 milestone close
**Requirements:** RPT-03, RPT-04, RPT-05, RPT-06, QUAL-05
**Blocker:** Depends on 999.5 (`loadSnapshotData()` fix required before score breakdown; coverage/utilization data methods required before export tabs)
**Success criteria carried forward:**

1. Excel (.xlsx) export with Coverage and Utilization tabs, colour-coded cells, correctly sorting date/time cells
2. PDF export in readable tabular layout (OpenPDF 3.0.4 — LGPL/MPL; iText rejected as AGPL)
3. Score breakdown panel: every constraint that fired, violation count, score impact; stub constraints (`breakClustering`, `bulkUnderallocationSoft`) labelled "Inactive"
4. Score breakdown guarded to in-memory solves; DB-loaded accepted schedules show a clear message, not empty data or a 500
5. Score breakdown exportable to Excel
6. Constraint weights and time limit adjustable from the UI without redeploy; time limit labelled "Local Search time limit" with a tooltip

**Constraint:** Timefold pinned at **1.16.0** (corrected 2026-08-13 against `build.gradle:35`; the previously recorded 1.33.0 was wrong) — `ScoreAnalysis` moves to paid tier in 2.0
**UI hint:** yes
**Plans:**

- [ ] TBD

### Phase 999.7: BambooHR Credential Rotation & Scrub — REMOVED FROM TRACKING

Removed from the GSD backlog on 2026-08-25 at operator request. Ownership sits with the operator
outside this planning system; the ID is retired rather than reused so 999.8/999.9 keep their
existing references.

### Phase 999.8: Decommission Orphaned v1.0 Infrastructure (BACKLOG — COST)

**Goal:** The v1.0 AWS resources in the abandoned account are audited and either destroyed or knowingly retained, so nothing bills silently
**Source:** Discovered 2026-08-10 while reconciling stale endpoints in planning docs during Phase 10 UAT
**Detail:** v1.0 was provisioned in AWS account **521757869980** (`03-01-SUMMARY.md`); the live environment is now account **982940000233** (`infra/main.tf`, `.github/workflows/deploy.yml`). Endpoints from the old account still resolve:

- `d3f4cgjy3bqy.cloudfront.net` — live CloudFront distribution, S3 origin returns `AccessDenied`
- `wfm-service-dev-1135113453.eu-west-2.elb.amazonaws.com` — live ALB (`18.171.68.68`), returns 503, zero healthy targets

An idle ALB plus NAT gateway plus an RDS instance in that account would be the material cost; RDS/NAT status was **not** verified — only the two public endpoints above were probed from outside.
**Work:**

- [ ] Confirm whether account 521757869980 is still open and billing, and who owns it
- [ ] Inventory surviving v1.0 resources there (RDS `wfm-service-dev`, NAT gateway, ALB, CloudFront, ECR, Secrets Manager)
- [ ] Confirm no data in the old RDS instance is still needed before destroying
- [ ] `terraform destroy` against the old state (`wfm-terraform-state-521757869980`) or delete manually if state is unrecoverable

### Phase 999.9: Close v1.2 Integration Gap I-2 (BACKLOG — carried from v1.2 close)

**Goal:** The merge-precedence guarantee holds on every write path, not just the upload path
**Severity:** high — recorded in **three** consecutive milestone audits (2026-08-21, 2026-08-25, 2026-09-21) and never scoped into a phase
**Source:** v1.2 milestone audit finding I-2; accepted as debt at milestone close 2026-08-25

**Why.** The manual "Refresh from BambooHR" button (`DeskAgents.tsx:448` → `POST /desks/{id}/agents/refresh`
→ `BambooRefreshService.persistRefreshData:224-234`) overwrites `name`, `firstName`, `lastName`,
`email`, `department`, `jobTitle` and `active` straight from BambooHR with no precedence rule and
emits no `MergeReportEntry`. `grep` confirms zero references to `AgentMergeService` in that file. It
is a normal, expected operator action that silently discards the guarantees MRG-02, MRG-04 and
MRG-05 describe — the same failure shape as I-1 (a requirement verified `passed` in its own phase
while a second reachable entry point violates it).

**Three options recorded at close:**

1. Route the manual refresh through `AgentMergeService` — the real fix; makes MRG-02 true on every path
2. Constrain the button to fields BambooHR owns outright, leaving spreadsheet-sourced data alone — cheaper, removes the silent-overwrite risk without building report plumbing
3. Scope MRG-02 to the upload path explicitly and label the button — no code change, but converts an undiscovered limitation into a stated product decision

**Also fold in** (same area, recorded at v1.2 close):

- [ ] **I-3 residual** — `DeskAgentService.setContractedHours:236-279` still calls `deleteByAgent_Id` then recreates seven rows with `dayOffType` unset, destroying MANDATORY/PTO labels. Preserve labels across the fan-out, or retire the bulk action now that the safe per-cell `setDayHours` path exists
- [ ] **NEW-1** — stop exporting the legacy `contractedHoursPerDay` scalar as its own column, or keep it in sync from `setDayHours`; today they can silently disagree after any single-cell edit
- [ ] **Nyquist coverage — now five phases across two milestones.** `/gsd-validate-phase 10`, `13`, **`14`, `15`** (all four `VALIDATION.md` still `status: draft` — seeded by plan-phase, never reconciled, so their `nyquist_compliant: false` is not authoritative), plus **`16`**, the one genuine PARTIAL (`status: validated` *and* `nyquist_compliant: false`). Only Phase 17 is COMPLIANT. Flagged at the v1.3 audit as having drifted from an oversight into a pattern
- [ ] **Phase 9 security** — `/gsd-secure-phase 9` has never run; no `09-SECURITY.md` exists

**Plans:**

- [ ] TBD

Full analysis: `.planning/milestones/v1.2-MILESTONE-AUDIT.md`
