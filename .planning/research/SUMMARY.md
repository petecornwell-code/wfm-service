# Project Research Summary

**Project:** WFM Service (Helpware) — v1.5 Overnight Shifts & Business Dates
**Domain:** Constraint-solver re-anchoring in an existing, live Spring Boot + Timefold 1.16.0 + PostgreSQL 16 workforce-scheduling service
**Researched:** 2026-09-30
**Confidence:** HIGH

## Executive Summary

This is not a stack-acquisition milestone; it is a modelling and migration-safety milestone. All four
research tracks converge on the same shape: give each desk a `LocalTime day_start` anchor (default
`00:00`, zero behaviour change), generalize the existing `DayWindow` utility from an implicit
midnight anchor to an explicit one, stamp a stored `business_date` column onto `Timeslot` at
generation time, and re-point roughly a dozen solver joins from `Timeslot::getDate` to
`Timeslot::getBusinessDate`. No new runtime dependency is needed — `java.time`, plain Postgres
`DATE`/`TIME` columns, and the project's existing `Joiners.equal` pattern cover every requirement.
The only new dependency is test-scoped (`jqwik`, for property-based construction of midnight-boundary
fixtures). Crucially, this milestone has a dry run: v1.4 attempted the identical scope, built
roughly the first third of it cleanly, and was cancelled on 2026-09-30 not for a wrong architecture
but for an unsound regression fixture — so the architecture below is not speculative, it is
partially pre-validated against this exact codebase (commits recoverable at
`rescue/phase-18-unwind-20260930`).

The single dominant risk, named identically by ARCHITECTURE.md and PITFALLS.md, is the
**re-anchoring of `DayWindow`** — 112 call sites across 16 files, all routing through one
utility by design. The failure mode is not a crash but a *silent non-join*: a constraint or
comparison that reads the wrong date field compiles, runs, and reports a clean score against a
schedule that is not actually legal. The mitigation the research converges on is structural, not
procedural: force the migration through a compiler-checked overload removal (so a missed call site
is a build failure, not a judgement call), and pair it with source-scan guard tests
(`MidnightTimeArithmeticGuardTest` extended, plus a new `BusinessDateJoinGuardTest`) that fail on
any un-allowlisted offending line, in both directions. This is the same proven-in-this-codebase
technique as `BusinessDateWritePathGuardTest` and `UsualShiftWritePathGuardTest`.

The second-order risk — the one that actually killed v1.4 — is regression-fixture design, not code
correctness. v1.4's BDAY-06 fixture was built from four live desks and was simultaneously
over-sensitive (any unrelated scoring change anywhere shifts golden bytes with no diagnostic signal)
and under-powered on the exact property it existed to prove (three of the four desks never cross
midnight; none had a 23:00–00:00 slot). v1.5 inverts this: constructed, property-chosen boundary
scenarios (with a class-load validator proving the fixture actually contains each named boundary
case) carry correctness proof, and one small live desk (Phil-US, 48 agents, not Vinted's 287) is
demoted to a secondary drift guard, decomposed into per-constraint match counts rather than a whole-
object byte diff. All four research documents treat this fixture redesign as the single most
important deliverable of the milestone — more important than any individual feature.

## Key Findings

### Recommended Stack

No new production dependency. `java.time.LocalTime`/`LocalDate` (unchanged types, generalized usage),
plain Postgres `DATE`/`TIME` columns, and the existing `Joiners.equal` constraint-join pattern cover
every v1.5 requirement. The one addition is test-scoped: `net.jqwik:jqwik:1.10.1` as a
`testImplementation`, for property-based generation of anchor/start/end combinations that a
hand-enumerated table tends to under-sample — precisely the gap that made v1.4's fixture
under-powered. See STACK.md's "What NOT to Add" table for four tempting-but-rejected options
(`threeten-extra`, `ZonedDateTime`, Postgres range/exclusion-constraint types, a `BusinessDate`
wrapper type, `ApprovalTests.java`) — each rejected for a specific, load-bearing reason, not
generic caution.

**Core technologies:**
- `java.time.LocalTime`/`LocalDate` — already the type for every time/date column and `DayWindow`
  signature; no second time type needed for the day-start anchor.
- `int` minute-of-day, anchor-relative — `DayWindow`'s existing arithmetic representation,
  generalized with an anchor parameter rather than replaced; kept because it is branch-free and
  allocation-free on hot solver code paths, unlike `Duration` or a wrapper type.
- PostgreSQL 16, plain `DATE`/`TIME` columns — a `business_date` column cannot be a generated column
  (Postgres cannot reference another table's row for the desk's anchor), so it must be computed in
  application code at generation time and stored.
- `net.jqwik:jqwik:1.10.1` (test-scope only) — property-based construction of midnight-boundary
  fixtures for BDAY-06.

### Expected Features

**Must have (table stakes, P1 — matches PROJECT.md's stated v1.5 scope):**
- Desk-level day-start anchor, defaulting to `00:00`, zero behaviour change for non-opted-in desks.
- `DayWindow` re-anchored on day-start; guard tests land before the re-anchoring, not after.
- Business date populated on every timeslot at generation.
- Overnight shift templates (end < start, correct net hours) — day-off/PTO blocking and
  contracted-hours consumption against the starting business day need **no schema change**, a
  genuine gift from v1.2's per-weekday `agent_day_hours` design.
- Solver business-date correctness across all 12 `timeslot.getDate()` joins, plus the silent-non-join
  guard test.
- SLOT-mode overnight correctness — a pre-existing under-allocation defect (already live today,
  independent of new capability), not new scope.
- Minimum rest, hard, per-desk, pre-solve-refused, naming the agent and both shifts; inert on any
  desk that configures nothing.
- Schedule grid renders one continuous block; Excel export places the full span under the starting
  day's column (partially started: `5ddd8dc`).
- Constructed midnight-spanning regression scenarios chosen by the property under test; Phil-US as a
  small live drift guard only.

**Candidate additions surfaced by research, NOT currently in PROJECT.md's v1.5 list (P2 — flag to
operator for explicit accept/decline, do not silently adopt):**
- **Business-day calendar-span labelling on operator-facing surfaces.** FEATURES.md's "Mixed
  Day/Night Desks" analysis found this is a real, precedented usability problem: a desk anchored at
  `21:00` stores a `09:00` day shift's agent-day facts against business-day "Tuesday" even though a
  human reading a bare date label would call it "Wednesday's day shift." Hospitality PMS ("business
  date," rolled at night audit) is the closest working analogue, and it solves this with disclosure
  (calendar-span labelling), not a second boundary mechanism — validating the desk-level single-anchor
  decision while flagging that the labelling itself is currently unscoped. Cheap (UI-only, no data
  model change).
- **An anchor-agreement guard between demand upload/coverage reporting and the solver.** The existing
  Erlang C/X staffing calculator and demand upload path is a separate code path from the new
  business-date-anchored coverage output; unlike the solver-join guard (explicitly scoped, Pitfall 2),
  nothing currently guards that these two agree on the same anchor. FEATURES.md flags this as a
  candidate verification gap, not an assumed-covered item.

**Defer (v2+, P3 — explicitly out of this milestone, per PROJECT.md and confirmed by all four docs):**
- Phil-US real-shift migration (`2100-0600`/`2200-0700`/`2300-0800`/`0000-0900`, tested reversal) —
  deferred so the capability stays desk-agnostic; also the correct sequencing per FEATURES.md (prove
  the mixed-desk labelling problem on constructed cases before the one live desk with that exact
  shape).
- Timezone-aware or auto-adjusting day-start anchoring — stays the operator's pre-load job
  indefinitely by design; Phil-US's 15h PHT/Pacific gap is deliberately not this milestone's problem.
- Premium-pay/consent-to-override machinery for minimum-rest violations — no payroll subsystem exists
  anywhere in this project to attach it to; the US municipal "clopening" ordinance pattern (soft +
  premium pay) was researched and explicitly rejected as a model here since minimum rest is
  hard-only in this project.

### Architecture Approach

`Timeslot` is the **only** entity in the codebase carrying two competing notions of date — `date`
(calendar) and the new `businessDate` (which day the row counts against). Every other date-bearing
problem fact the solver reads (`AgentDayOff.date`, `AgentDayConfig.date`, `AgentPreference.date`,
`AgentShiftAssignment.date`, `ResolvedUsualShiftTarget.date`, `ShiftStartMixTarget.date`) is already
modelled as a per-agent-day fact with no competing calendar-date meaning — this narrows the
migration's actual blast radius to: (1) make `Timeslot` carry both dates correctly, (2) re-point
constraint joins that compare `Timeslot`'s calendar date against one of those already-business-dated
facts, and (3) make `SolverService`'s pre-solve derivation walk business dates instead of calendar
dates. The domain model for agent-day facts does not change shape at all.

**Major components:**
1. `Desk.dayStart` (new field, `LocalTime`, default `00:00`) — per-desk anchor, read only at
   generation time by `TimeslotGeneratorService`.
2. `Timeslot.businessDate` (new stored column, not a derived getter/shadow variable/lookup map) —
   sole deriving writer `TimeslotGeneratorService`, sole propagating writer `ScheduleService`'s
   accept-time snapshot copy (v1.4's D-24 rule).
3. `DayWindow` (modified, highest-risk edit) — `startMinute`/`endMinute` gain an explicit `dayStart`
   parameter; `durationMinutes` stops throwing on `end < start` (that condition now means "crosses
   the anchor," not "malformed").
4. `ScheduleConstraintProvider` (modified) — ~12 call sites re-point from `Timeslot::getDate` to
   `Timeslot::getBusinessDate`; join *order* must stay untouched (a documented ~3x throughput cost
   exists for reordering these streams).
5. A new hard minimum-rest constraint, plus a separate pre-solve refusal mechanism (not a hoped-for
   side effect of the hard constraint) — reuses the existing ENVL-07 seat-supply-gate refusal pattern.
6. A new structural guard test (`BusinessDateJoinGuardTest`) mirroring the existing
   `MidnightTimeArithmeticGuardTest`/`BusinessDateWritePathGuardTest` family — a source-scan,
   set-equality-both-directions test, not a type-level wrapper.

### Critical Pitfalls

1. **A constraint join on the wrong date column is a silent non-join, not an error** — it produces
   zero matching tuples, scored identically to "constraint satisfied," while the operator sees a
   clean score. Avoid by moving all 12 joins in one deliberate pass (not incrementally — a mixed
   state is worse than either uniform state), paired with a structural guard, a match-count assertion
   (not just a score assertion), and a non-vacuity check proving the constraint can actually fire.
   This is not hypothetical: SLOT mode already exhibits it today, independent of overnight shifts.
2. **A regression baseline sourced from live data that barely contains the property under test** —
   the specific mistake that killed v1.4. Live data is simultaneously too broad (any unrelated
   scoring change anywhere shifts golden bytes with zero diagnostic signal) and too narrow (none of
   the captured desks had a real midnight-crossing slot to exercise). Avoid by using constructed,
   property-chosen boundary scenarios for correctness proof, with a class-load validator confirming
   the fixture actually contains each named case, and demoting live data (Phil-US, 48 agents) to a
   small, per-constraint-decomposed drift guard only.
3. **"Byte-identical" misread as "reproduce a solve"** — Timefold solves are time-boxed and
   non-deterministic; this codebase's own solver rarely reaches hard 0. Reinterpret as byte-identical
   against a deterministic, hand-built object graph scored via `SolutionManager` with zero
   search/termination configured (precedent already exists:
   `ShiftDeskEndToEndRegressionTest.hardPenaltiesByConstraint`).
4. **`DayWindow` re-anchoring treated as 112 independent edits instead of one coordinated change** —
   piecemeal review is exactly the discipline the utility exists to replace, and a single drifted
   caller (a raw `LocalTime` comparison that slipped past the guard) silently keeps old semantics
   after everything else moves. Avoid by removing the one-argument overload entirely so the compiler
   enumerates every site as a build failure, landed as one atomic commit, gated behind the
   arithmetic guard passing clean first.
5. **Minimum rest computed across the wrong boundary, or blind at the solved horizon's edges** — a
   naive implementation groups by calendar-date bucket instead of ordering by actual start instant,
   and/or treats "no visible previous shift" as "infinite rest" even when the agent's actual last
   shift is just outside the solved window. Also: a hard constraint with no satisfiable assignment
   anywhere makes the *entire* solve infeasible, not just one agent-day, unless a genuinely separate
   pre-solve refusal mechanism exists.

## Implications for Roadmap

All four research documents converge on the same build order (ARCHITECTURE.md's Q4, cross-validated
by PITFALLS.md's "Phase to address" column on every pitfall). This is a hard-dependency ordering, not
a preference — most of it is not parallelizable.

### Phase 1: Foundation & Guards
**Rationale:** Every subsequent phase depends on the guard tests existing and passing green against
today's `00:00`-only behaviour *first*, so the re-anchoring commit is provably the first commit that
could make them red. This is the milestone's own settled, non-negotiable sequencing decision, and it
is also literally where v1.4's fatal defect (the fixture) must be redesigned before any other code
changes.
**Delivers:** Extended `MidnightTimeArithmeticGuardTest` (comparison operators, not just arithmetic);
constructed midnight-boundary regression scenarios (23:00–00:00 slot, envelope flush to end-of-day,
shift starting before/ending after midnight, break band touching an envelope edge, PTO on starting
vs. ending day, contracted-hours-starting-weekday-only) with a class-load validator proving their
presence; `Desk.dayStart`/`Timeslot.businessDate` schema, gated to `00:00`-only (v1.4's `84fdc3f`/D-18
pattern — safe to ship because it is provably a no-op); `BusinessDateWritePathGuardTest` (v1.4's
`7d42f23` is a directly reusable reference implementation).
**Addresses:** BDAY-01…06 group; the regression-safety feature from FEATURES.md's MVP list.
**Avoids:** Pitfall 3 (the v1.4 killer), Pitfall 4 (byte-identical misread), Pitfall 6 (assertion vs.
proof), Pitfall 7 (deploy-timing/migration-shape discipline), Pitfall 1 (comparison-operator gap).

### Phase 2: Re-anchoring
**Rationale:** `DayWindow` has zero outbound dependencies (imports only `java.time.LocalTime`) —
its correctness is fully determined by its own file and unit tests, making it the most isolated,
independently revertible change in the milestone, but only safe once Phase 1's guard is proven clean.
**Delivers:** `DayWindow`'s anchor parameterised via compiler-forced overload removal, landed as one
atomic commit across all 112/16 call sites; a parameterised unit test proving byte-identical output
at `dayStart == MIDNIGHT` for every existing behaviour.
**Uses:** The `int` minute-of-day anchor-relative representation from STACK.md; the compiler-enumerated
migration pattern from ARCHITECTURE.md.
**Implements:** `DayWindow` component (ARCHITECTURE.md Q2/Q6).
**Avoids:** Pitfall 5 (88/112-site piecemeal drift).

### Phase 3: Solver Business-Date Correctness
**Rationale:** Needs both `Timeslot.getBusinessDate()` to exist (Phase 1) and `DayWindow` to
correctly express overnight intervals (Phase 2) — the join point where both halves become necessary
simultaneously. This is also where the `dayStart`-gate from Phase 1 is finally lifted (a single,
deliberately reviewable line deletion).
**Delivers:** ~12 `ScheduleConstraintProvider` joins re-pointed from `getDate()` to
`getBusinessDate()` in one deliberate pass; the new `BusinessDateJoinGuardTest`; per-constraint
match-count assertions and non-vacuity checks on each migrated join; `TimeslotGeneratorService`
computing real (not identity) business dates from the desk's `dayStart`.
**Addresses:** SOLV-01…04.
**Avoids:** Pitfall 2 (silent non-join) — the core defense of this entire phase.

### Phase 4: Overnight Shift Templates & SLOT-Mode Correctness
**Rationale:** Nothing about an overnight envelope can be represented until `DayWindow.durationMinutes`
stops throwing on it (Phase 2), and nothing about which business day it counts against is correct
until the constraint provider and generator agree (Phase 3). SLOT-mode's fix is a narrower instance of
the same join fix and can be isolated as its own commit/test.
**Delivers:** `ShiftTemplate.endTime < startTime` as a legal, validated state; envelope validated
against the desk's *business day*, not its calendar-day operating window (closing part of the
pre-existing v1.3 "it will still save" gap for the overnight case specifically, per OVNT-05); one
continuous grid block and Excel span under the starting day's column, with a distinct (non-blank,
non-duplicate) continuation indicator on the morning-after cell; SLOT-mode overnight correctness as
its own named, independently tested success criterion.
**Addresses:** OVNT-01…06, the table-stakes UX findings from FEATURES.md.
**Avoids:** Pitfall 10 (grid/Excel fragment rendering), Pitfall 11 (envelope validated against the
wrong window).

### Phase 5: Minimum Rest
**Rationale:** Needs Phase 2's interval arithmetic across a business-date boundary and Phase 4's
overnight templates existing to actually be adjacent to each other. It is the first genuinely new
constraint, not a re-pointed join, so it is the natural final stage. Separable from BDAY/OVNT/SOLV in
delivery sequencing but not in correctness — it has no legacy calendar-date behaviour to be
backward-compatible with, so it must get business-date ordering right from day one.
**Delivers:** Hard, per-desk minimum-rest constraint ordered by actual start instant (not
calendar-date bucket); explicit, tested horizon-edge behaviour (documented blind spot or a bounded
lookback query, batched not N+1); a genuinely separate pre-solve refusal mechanism naming the agent
and both shifts, reusing the ENVL-07 pattern; regression proof that a desk configuring no minimum
rest solves identically to today.
**Addresses:** REST-01…04.
**Avoids:** Pitfall 9 (wrong boundary, horizon blindness, infeasibility-not-refusal).

### Phase Ordering Rationale

- Phases 1→2→3 are strictly sequential hard dependencies; nothing about `businessDate` exists to
  join against before Phase 1, and Phase 3's joins need Phase 2's corrected interval math.
- Phases 2 and the schema/write-path-guard work in Phase 1 have **no dependency on each other** and
  could in principle run as parallel workstreams (ARCHITECTURE.md Q4) — but both must land before
  Phase 3, and v1.4's own cancellation had nothing to do with this pairing, so there is no evidence
  against parallelizing it if the roadmapper wants throughput here.
- Phases 4 and 5 are strictly sequential after Phase 3; Phase 5 additionally depends on Phase 4's
  overnight templates existing so consecutive shifts can actually be adjacent.
- Regression-fixture *authoring* threads through all five phases (constructed cases are built in
  Phase 1, but can only be run against a real business-date-aware schedule once Phase 3 exists) but is
  not itself a build-order dependency — it is verification, not construction, and should be the last
  thing *finalized*, not the last thing started.
- This order directly avoids the class of mistake that cancelled v1.4: guards precede the re-anchor
  they protect, and the regression proof strategy is decided and built before any risky code change,
  not retrofitted after.

### Research Flags

Needs deeper research during planning:
- **Minimum Rest (Phase 5):** the exact horizon-edge lookback strategy (query cost bound, whether to
  fetch the agent's actual pre-horizon last shift or accept a documented blind spot) is not fully
  settled by this research — PITFALLS.md names the tradeoff but not a final decision. Also needs a
  concrete answer on how the pre-solve refusal composes with a desk whose shift library structurally
  cannot avoid a rest violation for some agent (distinct from the ENVL-07 precedent's simpler
  seat-supply case).
- **Overnight Shift Templates rendering (Phase 4):** the Excel/UI continuation-indicator design is
  specified at the conceptual level ("a distinct, non-blank, non-duplicate treatment") but the actual
  visual/legend convention needs a concrete design pass against the existing cell-code legend.

Phases with standard, well-documented patterns (skip research-phase):
- **Foundation & Guards (Phase 1):** direct reference implementations exist in this exact codebase
  (`MidnightTimeArithmeticGuardTest`, v1.4's `BusinessDateWritePathGuardTest` at commit `7d42f23`,
  `ShiftDeskEndToEndRegressionTest`'s scoring-only pattern) — this is templated work, not exploratory.
- **Re-anchoring (Phase 2):** the compiler-forced-overload technique is fully specified with worked
  reasoning against the three alternatives; no open design question remains.
- **Solver Business-Date Correctness (Phase 3):** the join-migration technique and guard-test shape
  are directly copied from two proven precedents in this codebase.

## Unresolved Decisions (for the operator/roadmapper, not settled by this research)

- **Cherry-pick v1.4's rescue-tag commits vs. re-author fresh.** v1.4's four salvageable commits
  (`84fdc3f`, `2196e40`, `7d42f23`, `63d85a6` on `rescue/phase-18-unwind-20260930`) are directly
  reusable reference implementations, but whether v1.5 should literally cherry-pick them or re-author
  against the current tree (which has since had at least one relevant fix land, `5ddd8dc`) is not
  decided by any of the four research tracks.
- **The golden-file justification-log enforcement mechanism.** PITFALLS.md and ARCHITECTURE.md both
  discuss "prove nothing changed" mechanisms (default-and-gate, write-path guards, per-constraint
  match-count snapshots) but three ranked options existed in v1.4's own research for how to enforce a
  justification log against a golden file, hash-based recommended — this was never settled and is not
  re-settled here.
- **Whether `agent_shift_assignment` needs its own `business_date` column or derives one.** STACK.md
  explicitly flags this as something "the roadmapper should confirm" — the milestone's "12
  `timeslot.getDate()` joins" requirement doesn't specify which rows need a sibling column versus
  which can derive via a join to `Timeslot`.
- **The final allowlist contents for the join guard (`BusinessDateJoinGuardTest`).** ARCHITECTURE.md
  proposes the token and structure but explicitly states "plausibly none" survive as legitimate
  calendar-date uses inside a join — this needs verification against the actual, current
  `ScheduleConstraintProvider` at planning time, not assumed empty.
- **Call-site counts should be re-grepped at planning time.** Both the "112 references / 16 files"
  `DayWindow` count and the "12 `timeslot.getDate()` joins" count are stated with an explicit caveat
  in the source research ("re-verify fresh, since files have moved since 2026-09-28/29") — treat these
  as approximate, not exact, until re-confirmed.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Grounded directly in this codebase's `DayWindow`, migrations, and `build.gradle`, cross-checked against current library releases (jqwik version verified via WebSearch). |
| Features | MEDIUM | Industry-wide web sources on vendor UX/behaviour and cross-domain analogues (hospitality, gaming, healthcare, rail) — no direct vendor trial access. EU/UK and US municipal regulatory claims cross-checked against primary legal text (higher confidence). The Philippines minimum-rest figure is **LOW confidence as a single number** — the Labor Code sets no statutory shift-to-shift rest figure; 10–12h is repeatedly cited industry/commentary practice, not codified law. |
| Architecture | HIGH | Every claim grounded in direct inspection of `DayWindow.java`, `ScheduleConstraintProvider.java`, `TimeslotGeneratorService.java`, the planning-entity classes, and the actual (unwound) v1.4 implementation at `rescue/phase-18-unwind-20260930` — primary-source evidence of an already-attempted, partially-validated implementation on this exact codebase. |
| Pitfalls | HIGH | Every pitfall grounded in a file read from this codebase or in the cancelled v1.4 attempt's own research/context artifacts, not generic scheduling-domain lore. |

**Overall confidence:** HIGH, with two named exceptions: the Philippines minimum-rest figure (LOW,
industry commentary not statute) and the DST/timezone-corner-painting reasoning in PITFALLS.md
Pitfall 8 (MEDIUM — forward-looking risk analysis for a deliberately out-of-scope future concern,
not evidence that a DST problem exists today).

### Gaps to Address

- **Demand-vs-coverage anchor agreement** (FEATURES.md P2 candidate) has no named guard the way the
  solver-join defect does — flag explicitly to the operator for accept/decline; do not silently treat
  it as covered by SOLV-01…04.
- **Business-day calendar-span labelling** (FEATURES.md P2 candidate) is a real, precedented
  usability gap not currently in PROJECT.md's feature list — flag for explicit inclusion or explicit
  deferral at roadmap creation, not silent omission.
- **Exact 112/16 and 12-join call-site counts** need a fresh grep at planning time, not reuse of the
  research-session counts (both research docs flag this themselves).
- **The four unresolved decisions above** (cherry-pick vs. re-author, golden-file enforcement
  mechanism, `agent_shift_assignment`'s business-date storage, join-guard allowlist contents) should
  be resolved during phase planning, not assumed by the roadmap.

## Sources

### Primary (HIGH confidence)
- Direct codebase inspection: `src/main/java/com/wfm/util/DayWindow.java`,
  `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` (full file),
  `src/main/java/com/wfm/service/TimeslotGeneratorService.java`,
  `src/main/java/com/wfm/model/{Timeslot,Desk,AgentAssignment,AgentShiftAssignment,AgentDayConfig,
  AgentDayOff,ShiftTemplate,ShiftBandPair,ResolvedUsualShiftTarget,ShiftStartMixTarget}.java`,
  `src/test/java/com/wfm/service/MidnightWindowSeamTest.java`, `build.gradle`,
  `src/main/resources/db/migration/{V1,V52}__*.sql`.
- The unwound v1.4 implementation at tag `rescue/phase-18-unwind-20260930`: commits `84fdc3f`,
  `2196e40`, `7d42f23`, `985e365`, `63d85a6`, and v1.4's own `18-RESEARCH.md`/`18-CONTEXT.md`/
  `ROADMAP.md`/`REQUIREMENTS.md`.
- `.planning/PROJECT.md` (v1.5 scope, v1.4 cancellation rationale, settled/deferred decisions),
  `.planning/codebase/{STACK,ARCHITECTURE,CONVENTIONS,CONCERNS,TESTING}.md`.

### Secondary (MEDIUM confidence)
- jqwik 1.10.1 maintenance-mode status — WebSearch against jqwik.net release notes/user guide.
- EU-OSHA Directive 2003/88/EC, UK Working Time Regulations 1998, LA/Berkeley/Emeryville
  predictable-scheduling ordinances — primary legal text, cross-checked.
- Cross-domain precedent (hospitality PMS night audit, gaming day, nurse rostering, rail crew duty
  day) — industry sources, converging independently on start-day attribution.

### Tertiary (LOW confidence)
- Philippines minimum-rest figure (10–12h) — Respicio & Co. commentary (repeated but not statutory);
  no DOLE advisory found specific to BPO/IT-BPM shift-to-shift rest gaps. Needs validation if a
  Philippines-jurisdiction desk configures this constraint.

---
*Research completed: 2026-09-30*
*Ready for roadmap: yes*
