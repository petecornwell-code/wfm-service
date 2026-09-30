# Phase 18: Business-Day Foundation & Guards - Context

**Gathered:** 2026-09-30
**Status:** Ready for planning

<domain>
## Phase Boundary

A desk can declare the time its day begins and a timeslot records the business day it belongs to;
the two safety nets Phase 19 falls onto — the comparison-operator-extended `DayWindow` guard and a
constructed midnight-boundary regression suite — exist and pass green against today's `00:00`-only
behaviour; and `TimeslotGeneratorService` can generate a contiguous 24-hour business day from a
non-midnight anchor, proven by direct unit test while the production API still refuses to save one.

**Observable behaviour change for an operator: none.** Every existing desk's business date is
identical to its calendar date by construction, and the day-start control ships visibly disabled.

Requirements: BDAY-01, BDAY-02, BDAY-03, BDAY-05, BDAY-06, BDAY-08.

**Not this phase:** the `DayWindow` re-anchoring itself and retirement of the `00:00`-means-end-of-day
convention (BDAY-04, Phase 19); solver join migration and the calendar-vs-business-date join guard
(SOLV-01..07, BDAY-07, Phase 20); overnight shift templates (OVNT-01..07, Phase 21); minimum rest
(REST-01..07, Phase 22); the Phil-US migration (MIGR-01..04, Future Requirements, deferred out of
v1.5 entirely).

</domain>

<decisions>
## Implementation Decisions

### Inherited unchanged from v1.4's Phase 18 discussion

v1.4 discussed this same phase with the same operator on 2026-09-29. Its `18-CONTEXT.md` survives at
`rescue/phase-18-unwind-20260930`. The following were not re-asked and are **locked**, restated here
so downstream agents need not read the v1.4 document to act:

- **D-01:** BDAY-05 is an **extension, not a build** — `MidnightTimeArithmeticGuardTest` plus
  `src/test/resources/midnight-time-arithmetic.md` already implement the required shape. One guard,
  one resource; a second allowlist section rather than a second test (v1.4 D-11/D-15).
- **D-02:** The hole BDAY-05 closes is **comparison operators on scheduling times** —
  `.isAfter` / `.isBefore` / `.compareTo` must route through `DayWindow`.
  `LocalTime.MIDNIGHT.isAfter(LocalTime.of(23,0)) == false` is documented in the resource as half the
  bug class and is currently unguarded. A defect live today, independent of overnight shifts (v1.4 D-12).
- **D-03:** Detection uses a **name-based receiver heuristic** (`*Time`, `slotStart`/`slotEnd`,
  `envelope*`, `band*`, `break*Start`), documented in the `.md` and not only in the test, because the
  heuristic is the part that rots. Re-verified fresh on HEAD: raw counts are `.isAfter(` **43**,
  `.isBefore(` **26**, `.compareTo(` **36** — 105 hits, mostly `LocalDate` and `BigDecimal`, so a naive
  token scan would demand a 100+ entry allowlist and become decoration (v1.4 D-13).
- **D-04:** **Date-token guarding stays out of this phase.** SOLV-02 — now **Phase 20**, not Phase 21 —
  owns the calendar-vs-business-date join guard (v1.4 D-14).
- **D-05:** No pre-placed assertion that the `00:00`-means-end-of-day convention is retired. It cannot
  pass until Phase 19 removes `endMinute`, so pre-placing it means a disabled test or a red build across
  a phase boundary. BDAY-04 owns the retirement (v1.4 D-16).
- **D-06:** Target accepted day-start range is **15-minute boundaries** (v1.4 D-17).
- **D-07:** **The `00:00`-only gate is one validation line in `DeskService` that Phase 19 deletes**, so
  its removal is a visible, reviewable act. The gate is not in `TimeslotGeneratorService`, which takes
  `dayStart` as a parameter — which is what makes BDAY-03's unit test reachable (v1.4 D-18).
  — **Reversibility:** `reversible` — one validation; the column and its range are unaffected.
- **D-08:** A day-start that is **not a whole multiple of the generation increment refuses loudly at
  generation**, naming the day-start, the increment, and why they cannot tile. The increment is not desk
  state — it arrives per call, inferred from the FTE spreadsheet's own columns
  (`FteUploadService:127`) — so save-time validation is impossible. Built in this phase even though D-07's
  gate makes it unreachable through the API, so Phase 19 inherits a validated refusal instead of writing
  one during the milestone's riskiest edit (v1.4 D-20/D-21).
- **D-09:** **V53** adds `desk.day_start TIME NOT NULL DEFAULT '00:00'` and
  `timeslot.business_date DATE` nullable → `UPDATE business_date = date` → `SET NOT NULL`, all three
  statements in one migration. No nullable window and no "unset" state any reader must handle. Verified:
  schema head on HEAD is **V52**, so V53 is correct. *(STATE.md's "next migration is V40" line is a stale
  v1.3-era note and should not be trusted.)* (v1.4 D-22).
  — **Reversibility:** `one-way` — undoing needs a further migration, and any row written after V53
  carries a value no earlier schema has a column for.
- **D-10:** **Not a generated column.** `GENERATED ALWAYS AS (date) STORED` cannot be assigned, so
  Phase 19 would need another migration to drop and re-add it as writable (v1.4 D-23).
- **D-11:** **`timeslot` and `desk` join `MigrationEntityConsistencyTest.DECLARED_TABLES`.** That map is
  a hardcoded six entities today, so both of this phase's schema changes would otherwise land on tables
  the reconciliation test does not watch. The `LocalDate→DATE` and `LocalTime→TIME` mappings it needs
  already exist. **Risk to watch:** adding a table can surface a pre-existing mismatch in `timeslot` or
  `desk` — that is a finding to report, not licence to widen this phase into fixing unrelated drift
  (v1.4 D-25).
- **D-12:** **Nothing reads `business_date` in this phase.** Phase 20 (SOLV-01) is the first consumer
  (v1.4 D-26).

**Dead by v1.5 scoping — do not resurrect:** v1.4's D-01 through D-10, the per-live-desk golden files,
the `wfm.capture` harness, the anonymisation guard and the four-desk capture. That fixture is what got
v1.4 cancelled (`REQUIREMENTS.md:36-50`).

### BDAY-06 — the constructed regression suite

- **D-13:** **No golden file, and no justification log.** Each scenario asserts named expected values
  directly in the test, with a comment arguing why that number is correct. A failure names the property
  that moved, and a wrong expectation is reviewable because it is an argued claim rather than an opaque
  recording. The roadmap's open decision — "the golden-file justification-log enforcement mechanism,
  three ranked options, hash-based recommended" — is a **void premise, not an unsettled choice**: it
  belonged to v1.4's captured-live-desk design, where 288-agent match counts could not be hand-written
  and a blind recorded baseline was the only option. Constructed scenarios have knowable answers.
  Collateral movement is BDAY-07's job in Phase 20, which is precisely why `REQUIREMENTS.md:48-50`
  split BDAY-06 (constructed) from BDAY-07 (live drift).
- **D-14:** **Pin every planning variable; never call `solve()`.** The suite builds a `Schedule` whose
  assignments are all pre-assigned by a stated rule, then evaluates it with
  `SolutionManager.update()` / `.explain()`. That is pure function evaluation — no search, no step
  budget, no seed — so exact per-constraint match counts are assertable. This is the answer to "how can
  an optimisation algorithm have expected values": the optimiser is non-deterministic, the score
  function is not. **Each scenario must argue its pinned solution is one the solver could actually
  reach**, or it proves a score for an unreachable state.
  — **Reversibility:** `costly` — if pinned evaluation turns out to be unworkable, every scenario's
  assertions become tolerance bands and BDAY-06 loses its exactness, which is the sensitivity problem
  that cancelled v1.4 in smaller form.
- **D-15:** Scenarios that need no constraint provider — a 23:00–00:00 slot, an envelope flush to
  end-of-day, contracted-hours-starting-weekday-only — are **plain unit tests** on `DayWindow`,
  save-time validation and contracted-hours arithmetic, with no `Schedule` and no Timefold import.
  Only genuinely constraint-level properties get a pinned-solution evaluation.
- **D-16:** The three scenarios whose property **cannot exist today** — a shift starting before and
  ending after midnight (`DayWindow.durationMinutes` throws; `ShiftTemplateService` refuses), PTO on the
  starting vs. ending day, and the starting-weekday-only half of contracted hours — **assert today's
  actual behaviour**, and each is registered by name in a parsed `.md` naming which phase flips the
  assertion and to what. The validator asserts the registry equals the set of so-marked scenarios
  **exactly, in both directions**: Phase 19/21 cannot flip an assertion without removing its entry, and
  cannot leave a stale entry behind. This is the idiom already proven three times here
  (`midnight-time-arithmetic.md`, `ushf-05-write-paths.md`, `ScheduleConstraintClassificationTest`).
  Chosen over comment-only documentation because "a human will check at Phase 19 planning time" is the
  pattern the v1.3 audit named as this project's weakness, and STATE.md's deferred-items table shows
  such notes going three milestones unactioned.
  — **Reversibility:** `costly` — the registry becomes the contract Phase 19 and Phase 21 close out
  against; removing it later means the assertion flips have no enforcement.
- **D-17:** BDAY-06's non-vacuity validator works on **shared structural predicates over problem
  facts**, not on scenario labels: "a slot ending `00:00`", "a 23:00–00:00 slot", "a band flush to an
  envelope edge", "a span crossing the anchor". The validator asserts each predicate fires at least once
  across the constructed fixtures, so the suite cannot go vacuous, and there is no name registry to rot.
  **The predicates are written to be callable against any `Schedule`'s facts**, so Phase 20 can point
  them at the live drift-guard desk and find out whether BDAY-07 is meaningful at all — the exact
  ignorance that cancelled v1.4 (`REQUIREMENTS.md:44`: three of four captured desks never cross
  midnight, none has a 23:00–00:00 slot, no break band touches an envelope edge). One implementation,
  two callers — the D-08 discipline from Phase 14. **Pointing them at live data is Phase 20's business,
  not this phase's.**
- **D-18:** **Solve-time dynamic boundary detection was considered and rejected for this phase.** It
  observes rather than asserts (detecting a 23:00–00:00 slot is not asserting what it scores); it only
  fires when someone solves, so it is not checkable on a build with no database; and new production code
  in the solve path breaks this phase's no-observable-change claim — on a path where a deploy kills a
  running solve and the detail payload is 4 MB competing with the solver for its two cores. The
  underlying coverage question it was reaching for is captured in D-17 and deferred to BDAY-07.

### BDAY-03 — the generation walk

**Scoping tension to record, not absorb silently.** v1.5 moved BDAY-03 into Phase 18 while BDAY-04 (the
re-anchoring) stayed in Phase 19; v1.4 had both in Phase 19 together. `generateTimeslots:130-147` walks
minute-of-day inside one calendar date (`ts.setDate(date)`), and `DayWindow.toLocalTime` throws outside
`[0, 1440]` by design (`DayWindow.java:122`). A 21:00 anchor needs minutes 1260→2700 mapped across two
calendar dates. There is **no rescue-tag precedent**: v1.4's `985e365` added only a `dayStart` parameter
and the tiling refusal, then set `business_date = date`. This is genuinely new work inside the phase
whose premise is a provable no-op.

- **D-19:** **Additive anchored helpers in `DayWindow`** — nothing removed, no signature changed,
  existing behaviour byte-identical at `00:00`. The generation walk uses them to emit
  `(calendarDate, LocalTime)` pairs across the anchor and derive each slot's business date. Phase 19
  still owns deleting the midnight-implicit overload and re-pointing the other call sites, so BDAY-04
  stays intact and isolated. Rejected: local minute arithmetic in the generator (it plants a new
  raw-arithmetic site that BDAY-05's own guard must then allowlist — a self-inflicted exemption in the
  phase tightening the guard); moving BDAY-03 to Phase 19 (grows the change the milestone insists stays
  most isolated); and proving it against a test double (proves a property of code that gets thrown away).
- **D-20:** **`DayWindow` gains date-aware functions** and takes on `LocalDate` for the first time —
  today it imports only `java.time.LocalTime`. Given a day start, a calendar date and a time, it returns
  the business date. Everything anchor-related stays in the one class the milestone is organised around,
  and it is the file Phase 19 already rewrites, so no new file joins that commit. BDAY-08's single
  derivation site is then unambiguous, and Phase 20's joins, coverage reporting and SOLV-07's
  anchor-agreement guard all have one implementation to share. **Its javadoc must grow the second
  concept without losing the `00:00`-by-position statement**, which is currently the clearest
  articulation of the rule anywhere in the codebase.
  — **Reversibility:** `costly` — `DayWindow` stops being a pure time utility, and Phase 20's joins plus
  SOLV-07 are written against whatever shape this establishes.
- **D-21:** **`@Deprecated` on the midnight-implicit forms**, with a message naming BDAY-04. Every
  remaining call site then emits a build warning, so the compiler maintains a live count of exactly what
  Phase 19 must re-point — replacing the roadmap's "re-grep the count fresh" action item with something
  that cannot go stale. Left as warnings, not errors, since ~100 sites would otherwise break the build
  immediately. Accepts a noisy build for one phase.
- **D-22:** **`periodStart`/`periodEnd` mean business dates**, not calendar dates, once a desk has a
  non-midnight anchor. Generating D..D+6 on a 21:00 desk writes calendar rows from D 21:00 to D+7 21:00.
  `isDesired` partitions on derived business date. **`isDesired`, `slotKey` and `timeslotsMatch` must
  each be re-read in those terms, with each one's behaviour at `00:00` proven unchanged** — they govern
  the obsolete-slot deletion and the early return that preserves linked staffing requirements
  (`:95-112`). Rejected calendar-dates-with-clipping because it manufactures partial business days at
  every period edge, which is the SOLV-04 under-allocation defect in a new place.
  — **Reversibility:** `costly` — the parameter's meaning changes for non-midnight desks and both
  `generateTimeslots` call sites (`TimeslotController`, `FteUploadService:132`) inherit it.

### Salvage from `rescue/phase-18-unwind-20260930`

- **D-23:** **Cherry-pick `84fdc3f`, `a1c0077`, `2196e40`, `7d42f23`, `63d85a6`** — schema, migration
  consistency, write paths, the write-path guard, and the comparison-operator guard. **Re-author
  `985e365`'s generator change**, since D-19/D-22's business-date-anchored walk and business-date period
  semantics replace its unconditional `business_date = date`. Rejected cherry-picking all seven and
  amending (the generator gets written twice and `business_date = date` lands only to be immediately
  replaced) and re-authoring everything fresh (`7d42f23` is 417 lines of verified guard test and
  `63d85a6` is 239 lines of scanner — re-typing proven machinery for its own sake).
- **D-24:** **Rewrite comments to state their reason inline, dropping every `D-nn` citation**, and use
  requirement IDs rather than phase numbers — `SOLV-01`, `OVNT-01` are stable across milestones in a way
  phase numbers demonstrably are not. Concretely affected: `bday-02-write-paths.md` says "Phase 21
  (SOLV-01)" and "Phase 20 (overnight shift templates)", which in v1.5 are Phase 20 and Phase 21
  respectively; and V53's migration comment cites D-18, D-22, D-24 — v1.4 `18-CONTEXT.md` decision IDs
  that do not exist in this document. Directly answers the v1.3 audit's stale-reference finding: a
  comment depending on no external document cannot go stale.
- **D-25:** **Parameterise the guard's scan source root** and add a red-proof that points the same
  pipeline at a test-resources directory holding one synthetic offending file, asserting the
  set-equality assertion fails. `63d85a6` already carries `theScanDetectsAFreshOccurrence` and
  `theComparisonScanDetectsAFreshOccurrence`, but both test the **matcher predicate against synthetic
  strings** — they prove the matcher is live and that the receiver heuristic cuts both ways, but they do
  not exercise walk → comment-strip → match → set-compare. That leaves one failure mode uncovered: if
  the final assertion were weakened from set equality to a subset check, both red-proofs would still
  pass — the "guard becomes decoration" failure the test's own javadoc warns against. Rejected writing a
  temporary offending file into `src/main` (a killed run poisons the source tree, and it would corrupt a
  concurrent build on a suite STATE.md already records as contention-flaky in two places). This makes
  the commit no longer byte-identical to `63d85a6`.

### BDAY-01 — day-start surface and the override

- **D-26:** The `day_start` field renders in desk configuration alongside `scheduling_mode` and
  `default_contracted_hours_per_day`, **visibly disabled, with copy stating that only `00:00` is
  supported until overnight scheduling lands**. The operator can see the capability is coming and cannot
  reach a state that does nothing. Backend validation (D-07) stays the real enforcement, so the UI is
  disclosure rather than the gate — satisfying `ROADMAP.md:67-69`'s requirement that the restriction be
  explicit in the copy rather than enforced silently via a backend 400. Phase 19 deletes one attribute
  and one sentence.
- **D-27:** **Build `fec8990`'s accepted-schedule refusal; drop its named override.** The refusal stands
  on its own merits — `dev` is production with real tenant data and Phil-US holds a live accepted
  schedule, so silently mutating a desk's day start under an accepted schedule is a real hazard. But the
  override's recorded justification was "Phase 23 must actually perform this change", and the Phil-US
  migration is now MIGR-01..04 under Future Requirements, deferred out of v1.5 entirely. **Nothing in
  Phases 18–22 changes a live desk's day start.** An override with no caller is untested surface area
  whose name and semantics would be fixed by this phase and inherited by a migration phase not yet
  designed; MIGR-04 (a documented, tested reversal) is where it should be designed against a real
  requirement. Whoever performs the migration adds it back.
- **D-28:** The `00:00`-only copy requirement is held by **UAT verification only**, recorded in the
  phase's UAT document, not enforced by a test. Consistent with Phase 13's P-11 ruling (no frontend test
  framework introduced) and with every other UI guarantee in this project. Phase 19 deletes this copy
  within one phase, so the window for it to rot is short. **Knowingly accepted gap:** it is a real
  unenforced requirement in a phase where almost everything else got a structural guard. A `.tsx` source
  scan was considered and rejected as brittle to ordinary rewording for a one-phase lifespan.

### Claude's Discretion

- The exact deterministic assignment rule for the pinned solutions in D-14 — any stated, reproducible
  rule is acceptable provided each scenario argues its solution is solver-reachable and the D-17
  predicates all fire.
- The precise name-pattern list in D-03, provided it is documented in the `.md` alongside the
  heuristic's limitations.
- The signature and naming of D-19/D-20's additive anchored helpers, and how the two arithmetic
  vocabularies are visually distinguished in `DayWindow` pending Phase 19's removal.
- Where exactly `day_start` sits on `DeskManagement.tsx` and the wording of its disabled-state copy —
  follow the existing `scheduling_mode` / `default_contracted_hours_per_day` pattern.
- Which resource file carries D-16's flip registry and D-17's predicate list, and whether they share
  one file — subject to D-01's one-guard-one-resource rule.
- Whether the roadmap's incorrect "`ShiftDeskEndToEndRegressionTest`'s scoring-only pattern" citation
  and its "~112 call sites" figure are corrected in `ROADMAP.md` as part of this phase or left to the
  next roadmap edit.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone scope and sequencing
- `.planning/ROADMAP.md` — Phase 18's goal and five success criteria (lines 32–71); the non-negotiable
  sequencing rationale (lines 142–159 of STATE.md mirror it); the `day_start`-copy constraint at lines
  67–69. **Contains two errors this discussion found:** line 61 cites
  "`ShiftDeskEndToEndRegressionTest`'s scoring-only pattern" but that test runs a real
  `solver.solve(unsolved)` at `:526` with tolerance-based assertions; and Phase 19's "~112 references
  across 16 files" measures 100 across 16 on HEAD.
- `.planning/REQUIREMENTS.md` — BDAY-01/02/03/05/06/08 wording (lines 97–104); "Decisions taken at
  scoping — do not relitigate" table (lines 71–84); the v1.4 cancellation diagnosis (lines 36–50), which
  is the reasoning behind D-13 and D-17; Out of Scope table (lines 157–169); Open Decisions for Planning
  (lines 171–188).
- `.planning/STATE.md` — Accumulated Context → Roadmap Evolution for the v1.5 roadmap rationale; the
  Blockers/Concerns list. **Its "next migration is V40" line is stale** — verified head is V52, next is
  V53.
- `.planning/PROJECT.md` — project-level context and Key Decisions log.

### v1.4's own Phase 18 artifacts (read via git, not on disk)
- `git show rescue/phase-18-unwind-20260930:.planning/phases/18-business-day-foundation-guards/18-CONTEXT.md`
  — the 2026-09-29 discussion with the same operator. D-11..D-26 there are inherited above as this
  document's D-01..D-12; **D-01..D-10 there are dead** (the captured-live-desk fixture).
- Same path for `18-RESEARCH.md`, `18-PATTERNS.md`, `18-01-PLAN.md` .. `18-05-PLAN.md`,
  `18-01-SUMMARY.md` .. `18-04-SUMMARY.md`, `18-VALIDATION.md` — reference material for the salvaged
  commits.

### The utility being protected and extended
- `src/main/java/com/wfm/util/DayWindow.java` — the single implementation. Read the class javadoc: it
  states the `00:00`-by-position rule and explicitly documents that it does **not** model a
  past-midnight shift, throwing instead (`:73-82`). `toLocalTime` throws outside `[0, 1440]` at `:122`.
  Imports only `java.time.LocalTime` today — D-20 changes that.
- `src/test/java/com/wfm/util/DayWindowTest.java` — existing unit coverage including
  `crossingMidnightIsRejected`. The parameterised byte-identical-at-midnight proof Phase 19 needs runs
  through this suite.

### Guard-test precedents
- `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` — the test BDAY-05 extends. Read
  its javadoc for the set-equality-only rule and the explicit warning against widening to a subset
  check. Current version has 6 `@Test` methods; `63d85a6`'s has 9.
- `src/test/resources/midnight-time-arithmetic.md` — the parsed allowlist, 3 entries today. Its "When a
  new entry is legitimate" section defines the both-endpoints-are-starts test any new comparison entry
  must meet.
- `src/test/java/com/wfm/service/UsualShiftWritePathGuardTest.java` and
  `src/test/resources/ushf-05-write-paths.md` — the write-path allowlist pattern `7d42f23` copies.
- `src/test/java/com/wfm/service/SolverUsualShiftWritePathGuardTest.java` — the behavioural-mock plus
  comment-stripped-source-scan pairing.
- `src/test/java/com/wfm/solver/ScheduleConstraintClassificationTest.java` — third allowlist precedent;
  same decoration warning in its javadoc.
- `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java` — `DECLARED_TABLES`, the map
  D-11 extends; `a1c0077` is the salvaged version.

### Fixture precedents for BDAY-06
- `src/test/java/com/wfm/solver/ConstraintPrecedenceObservabilityTest.java` `:89-111` — asserts **exact**
  per-constraint match counts off `SolutionManager.explain().getConstraintMatchTotalMap()`. The closest
  precedent for D-14, but it solves first.
- `src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java` `:205-222` — exact violation counts on
  a solution deliberately corrupted by hand, re-scored via `solutionManager.update()`. Also solves
  first. **The pure-evaluation shape D-14 chooses — never calling `solve()` — is new to this codebase**;
  these two are partial precedents, not a copyable template.
- `src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java` — **not** the scoring-only pattern
  the roadmap claims: `:526` runs a real solve and `:160-171` asserts score thresholds.
- `src/test/java/com/wfm/solver/LiveShapeShiftDeskFixture.java` — `validateTemplateSpecs()` is the
  class-load validator pattern D-17 follows.
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — 21 `DayWindow` call sites, the
  heaviest concentration Phase 19 touches, and 12 `getDate()` occurrences (the joins Phase 20 migrates).
  The `.asConstraint(...)` names are BDAY-06's match-count row labels.

### Code this phase changes
- `src/main/java/com/wfm/model/Desk.java` — 53 lines, three configurable fields today
  (`default_contracted_hours_per_day`, `scheduling_mode`, `description`); the pattern `day_start` follows.
- `src/main/java/com/wfm/model/Timeslot.java` — 56 lines; gains `business_date`.
- `src/main/java/com/wfm/service/TimeslotGeneratorService.java` — `generateTimeslots` at `:70`; the
  range check at `:79`; the generation loop at `:130-147` that D-19/D-22 rewrite; `isDesired` and
  `slotKey` (from `:160`) that D-22 requires re-reading; 12 `DayWindow` call sites.
- `src/main/java/com/wfm/controller/TimeslotController.java` and
  `src/main/java/com/wfm/service/FteUploadService.java` `:132` — the two `generateTimeslots` call sites,
  and the reason the increment is not desk state.
- `src/main/java/com/wfm/controller/DeskController.java`, `src/main/java/com/wfm/service/DeskService.java`,
  `frontend/src/pages/DeskManagement.tsx`, `frontend/src/api/client.ts` — where `day_start` is exposed;
  `84fdc3f` is the salvaged version and adds `src/main/java/com/wfm/dto/DayStartRequest.java`.
- `src/main/java/com/wfm/service/ScheduleService.java` — `acceptSchedule`'s snapshot copy, the
  propagating write `7d42f23` allowlists.
- `src/main/resources/db/migration/` — head is **V52**; this phase writes **V53**.

### Salvageable commits (all reachable, none an ancestor of HEAD)
- `84fdc3f` — V53 schema + entity/DTO/controller/service/UI, 248 lines across 10 files. **Cherry-pick.**
- `a1c0077` — `MigrationEntityConsistencyTest` reconciliation. **Cherry-pick.** *(Not named in ROADMAP.)*
- `2196e40` — `business_date` on every write path, 15 lines across 7 files. **Cherry-pick.**
- `7d42f23` — `BusinessDateWritePathGuardTest` + `bday-02-write-paths.md`, 417 lines. **Cherry-pick.**
  **Already satisfies BDAY-08 verbatim** — distinguishes deriving from propagating writers, pins both
  sets by name, set equality in both directions, classifying by whether the argument is a
  `.getBusinessDate()` read.
- `63d85a6` — comparison-operator guard extension, 239 lines. **Cherry-pick, then apply D-25.** Should
  apply close to cleanly: `5ddd8dc` was salvaged forward from v1.4, so this allowlist already reflects
  the fixed `isAfter` and carries only the `isBefore` half at `:105`, which still exists on HEAD.
- `985e365` — `dayStart` parameter + `requireDayStartTiles`. **Re-author per D-23.** *(Not named in
  ROADMAP.)*
- `fec8990` — accepted-schedule refusal + named override. **Refusal only, per D-27.** *(Not named in
  ROADMAP.)*
- The remaining ten Phase 18 commits on that tag (`68ce08e`, `fc11133`, `848b4ef`, `ffa956b`, `686777e`,
  `f83434b`, `628d3f2` and their docs) are the `wfm.capture` harness and four-desk capture —
  **deliberately out of v1.5 scope.**
- `f17a1ef` on the tag is the same export fix already on HEAD as `5ddd8dc` — do not re-apply.

### Project conventions
- `.planning/codebase/` — `ARCHITECTURE.md`, `TESTING.md`, `CONVENTIONS.md`, `STACK.md`, `STRUCTURE.md`,
  `CONCERNS.md`, `INTEGRATIONS.md`.
- `build.gradle:35` — Timefold pinned at 1.16.0. `build.gradle:90` — the single-named-property test-JVM
  passthrough (`wfm.benchmark`); forwarding the whole `-D` set was rejected because it leaks credentials
  into CI logs. **This phase does not add a second passthrough** — `wfm.capture` was v1.4's.
- `src/test/java/com/wfm/support/PostgresBackedTest.java` — the only route to real Flyway execution;
  the default suite runs H2 with `flyway.enabled: false` and `ddl-auto: create-drop`, so a migration
  is not checked against entity mappings unless a test opts in. V53 must not repeat V39's failure
  (migration applied cleanly, app failed to boot under `ddl-auto=validate`, all 402 tests green).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DayWindow` — **100 call sites across 16 files** in `src/main`, 46 more in `src/test`. That single
  funnel is what makes Phase 19 one coordinated change rather than 146 judgement calls, and it is why
  D-19/D-20 put the new anchored arithmetic there.
- `MidnightTimeArithmeticGuardTest` + `midnight-time-arithmetic.md` — BDAY-05's machinery already
  exists; the phase extends token families and the allowlist, not the scanner.
- `UsualShiftWritePathGuardTest` + `ushf-05-write-paths.md` — the shape `7d42f23` already copied for
  BDAY-08.
- `LiveShapeShiftDeskFixture.validateTemplateSpecs()` — the class-load validator pattern for D-17.
- `ConstraintPrecedenceObservabilityTest`, `ShiftEnvelopeGroundTruthTest` — exact match-count
  assertions off `SolutionManager`, the nearest thing to D-14's pinned evaluation.
- `MigrationEntityConsistencyTest` — already maps `LocalDate→DATE` and `LocalTime→TIME`.
- Seven cherry-pickable commits at `rescue/phase-18-unwind-20260930`, five taken as-is.

### Established Patterns
- **One implementation, not two that can drift** — drives D-01 (extend the existing guard), D-17
  (shared predicates with two callers) and D-20 (derivation in `DayWindow`, not the generator). The
  Phase 14 D-08 precedent is the canonical statement: the coverage validator has one implementation and
  two callers so the report and the refusal can never disagree.
- **Set equality, never subset** — every existing guard's javadoc warns that a subset check turns the
  guard into decoration. The stale-entry direction is what stops an allowlist rotting into a permanent
  exemption. D-16 and D-25 both rest on this.
- **Structural guards over review attention** — the v1.3 audit named this the project's real strength
  and stale planning records its weakness. Both show up here: D-16 chooses a registry over a comment,
  D-24 removes external references from comments, and D-28 is the one place a guard was deliberately
  declined.
- **Fail loudly at the boundary** — `DayWindow.durationMinutes` throws rather than returning a wrapped
  positive; D-08's tiling refusal follows it.
- **A guard that has never failed proves nothing** — `ShiftEnvelopeGroundTruthTest`'s Task 2, plan
  15-15's five red-proofs. D-25 applies it to the scan pipeline rather than only the matcher.
- **Seeded, step-count-terminated solver runs, never wall-clock** — recorded project discipline. D-14
  sidesteps it entirely by not solving.

### Integration Points
- `desk` table + `Desk` + `DeskService` + `DeskController` + `DeskManagement.tsx` — `day_start`,
  following `scheduling_mode`'s route end to end.
- `timeslot` table + `Timeslot` + `TimeslotGeneratorService.generateTimeslots` — `business_date`, plus
  `ScheduleService.acceptSchedule`'s propagating snapshot copy.
- `DayWindow` — gains anchored, date-aware functions; the midnight-implicit forms gain `@Deprecated`.
- `src/test/resources/midnight-time-arithmetic.md` — a second allowlist section.
- `MigrationEntityConsistencyTest.DECLARED_TABLES` — two new entries.
- New parsed resources for D-16's flip registry and D-17's predicate list.

### Creative Options the architecture enables
- Because `DayWindow` is already the single funnel, BDAY-06 can prove Phase 19's 100-site change safe
  by pure score evaluation, without running the solver at all.
- Because D-17's predicates operate on problem facts rather than on constructed fixtures specifically,
  the same code answers "does the live drift-guard desk contain midnight at all" in Phase 20 — the
  question nobody could answer when v1.4 built a midnight baseline on data that barely contained
  midnight.
- Because `@Deprecated` makes the compiler enumerate the doomed call sites, Phase 19 inherits its own
  migration list instead of re-deriving it from a grep.

</code_context>

<specifics>
## Specific Ideas

- **"The optimiser is non-deterministic, the score function is not."** Raised directly by the operator
  as an objection to expected values, and it is the load-bearing distinction in D-14. BDAY-06 never
  calls `solve()`; it pins every planning variable and evaluates.
- **The golden file was nonsensical, and saying so is the finding.** Not a mechanism to choose between
  three ranked options — a premise that left with v1.4's captured-desk design. Recorded as void in
  D-13 so no later phase re-opens it as unfinished business.
- **Detection is not assertion.** The operator's solve-time-detection idea was right about a real gap
  and wrong as BDAY-06's mechanism; D-17 keeps the useful half (shared predicates, reusable on live
  data) and D-18 records why the solve path is the wrong home for it.
- **Requirement IDs outlive phase numbers.** D-24's rule comes from watching `bday-02-write-paths.md`
  and V53's comment go stale in a single milestone cancellation. This is the second renumbering those
  same lines would have had.
- **An override with no caller is untested surface area.** D-27 declines to fix a contract for a phase
  that no longer exists, and points MIGR-04 at designing it against a real reversal requirement.
- **The scoping tension is recorded, not absorbed.** BDAY-03 arriving in Phase 18 without BDAY-04 is
  written down in the decisions above as a tension with a chosen resolution, so a later audit reads a
  deliberate call rather than inferring one from a diff.

</specifics>

<deferred>
## Deferred Ideas

- **Point D-17's boundary-case predicates at live desk facts** to report which midnight boundary cases
  real data actually contains — a **Phase 20 input for BDAY-07**, since a drift guard on a desk
  containing no boundary case is vacuous, and that exact ignorance is what cancelled v1.4. Needs a
  database and a live desk, so it cannot be a build-time check.
- **Solve-time dynamic boundary detection in the production solve path** — rejected for this phase
  (observes rather than asserts, not build-checkable, and new production code breaks the no-op claim).
  Revisit only as an operator-facing diagnostic, never as BDAY-06's mechanism. See D-18.
- **Correcting `ROADMAP.md`'s two errors** — the "`ShiftDeskEndToEndRegressionTest`'s scoring-only
  pattern" citation (that test solves) and Phase 19's "~112 references across 16 files" (100 on HEAD).
  Listed under Claude's Discretion; if not done here, it belongs in the next roadmap edit.
- **The named day-start override** — dropped per D-27; belongs with **MIGR-04**, designed against a
  documented, tested reversal rather than inherited as a guess.
- **Retiring the `00:00`-means-end-of-day convention** and asserting `endMinute` is gone — BDAY-04,
  Phase 19.
- **Widening the day-start range beyond `00:00`** — Phase 19 deletes D-07's gate; D-06's 15-minute
  granularity is the range it opens to.
- **Date-token guarding** (`.getDate()`, `.getDayOfWeek()`, `.plusDays(`, `ChronoUnit.DAYS`) — SOLV-02,
  **Phase 20**.
- **A `.tsx` structural source scan for UI copy** — considered for D-28 and declined as brittle for a
  one-phase lifespan. Revisit if UI copy requirements recur across phases.
- **Parser-based (type-aware) guard detection** — considered for D-03 in v1.4 and rejected as a new
  build dependency. Revisit only if the name-based heuristic proves noisy in practice.
- **Behavioural night-start guard** (build a 21:00 desk and assert contiguity/no-gap/no-dup properties,
  `MidnightGapScanTest` style) — D-19 makes this partially reachable in this phase for the generator
  specifically; the full-system version stays a Phase 19/20 candidate.
- **A frontend test framework** — explicitly out, per Phase 13's P-11.

### Reviewed Todos (not folded)

All three matched on generic keywords only (`one`, `per`, `desk`, `phase`, `live`, `does`, `what`,
`still`) with no business-date relevance — the same conclusion v1.4's discussion reached about the same
three:

- `2026-07-30-blank-upload-template-one-sheet-per-desk.md` — blank upload template, one sheet per desk.
  Upload-surface work, unrelated to business dates.
- `2026-08-13-cross-agent-seat-displacement.md` — cross-agent seat displacement for the atomic shift
  move. Phase 12 was withdrawn and reverted (`299c42c`); solver-move work, not this phase.
- `2026-08-14-terraform-db-password-drift.md` — Terraform state diverges from live RDS password and
  `publicly_accessible`. Infra; belongs with the v1.0 IAM backlog (999.1–999.3).

</deferred>

---

*Phase: 18-business-day-foundation-guards*
*Context gathered: 2026-09-30*
