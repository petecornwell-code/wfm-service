# Phase 19: DayWindow Re-anchoring - Context

**Gathered:** 2026-09-30
**Status:** Ready for planning

<domain>
## Phase Boundary

`DayWindow`'s nine midnight-implicit public functions stop existing as callable public surface, and
every one of their call sites is re-pointed at a form that is told, explicitly, what time the desk's
business day begins. One compiler-forced, revertible change, proven to produce identical output for
every desk at the `00:00` default — which, after this phase, is still every desk.

Requirement: **BDAY-04** only.

**Observable behaviour change for an operator: none.** The `00:00`-only gate in `DeskService` stays
in place through this phase (G-1), so no desk can hold a non-midnight anchor and nothing an operator
can reach behaves differently.

**Not this phase:** lifting the `00:00`-only gate and widening the accepted day-start range to
15-minute boundaries (Phase 20); re-pointing the solver's ~12 date joins onto business date and the
`BusinessDateJoinGuardTest` that polices them (SOLV-01..07, BDAY-07, Phase 20); the live-desk drift
comparison on Phil-US (BDAY-07, Phase 20); overnight shift templates and relaxing the save-path
forward-interval refusal for templates that legitimately span the anchor (OVNT-01..07, Phase 21);
minimum rest (REST-01..07, Phase 22); the Phil-US migration (MIGR-01..04, deferred out of v1.5).

</domain>

<decisions>
## Implementation Decisions

### The day-start gate, and what "retirement" means

- **D-01:** **Phase 20 deletes `DeskService`'s `00:00`-only gate, not this phase.** This resolves a
  direct contradiction between two locked documents: `18-CONTEXT.md` D-07, `DeskService:213`'s own
  javadoc and `18-01-SUMMARY.md` all say BDAY-04 (this phase) deletes it, while `ROADMAP.md`'s Phase
  18 success criterion 2 says "until **Phase 20** lifts the gate." The roadmap wins, for three
  reasons: `DeskService` is not a functional `DayWindow` call site (its single `DayWindow` reference
  is the javadoc), so deleting the gate here puts a non-call-site change inside the commit that
  criterion 5 confines to `DayWindow` and its call sites; dev is the live system, and lifting the
  gate before Phase 20's joins are business-date-keyed opens a window where an operator can set a
  `21:00` anchor and accept a plausibly-wrong schedule; and it would falsify the Phase 18 desk-config
  disclosure copy, forcing that cell editable in a phase with no UI hint. D-07's actual intent — one
  visible, reviewable validation line, never folded into a range check — survives whichever phase
  deletes it.
  — **Reversibility:** `reversible` — one validation line, unchanged from Phase 18.

- **D-02:** **Phase 19's only `DeskService` touch is correcting the stated owner.** The javadoc at
  `DeskService:207-228` and the desk-config disclosure cell both name BDAY-04 as what widens the
  range; both are re-pointed to SOLV-01 / Phase 20. Per D-24's convention this is written as a
  requirement ID, not a phase number.

- **D-03:** **BDAY-04's "the `00:00`-means-end-of-day convention is retired" is proven by inverting
  `DayWindowTest.DeprecationIsLive`, not by the deletion alone.** That reflective guard already
  enumerates the method set and asserts every midnight-implicit form carries `@Deprecated`; it is the
  one existing test this phase necessarily breaks. It is rewritten to assert the opposite standing
  property: **no public `DayWindow` static takes a scheduling time**. It then goes red for free on a
  reintroduced convenience overload in Phase 20, 21 or 22, and it enforces the public/private
  boundary D-04 establishes rather than trusting a reviewer to notice.
  — **Reversibility:** `reversible` — one test class, same machinery, inverted assertion.

### The re-anchored surface

- **D-04:** **The anchor is bound, not passed: `DayWindow.anchoredAt(dayStart)` returns an instance,
  and every public function becomes an instance method keeping today's exact signature.** A call site
  goes from `DayWindow.overlaps(a,b,c,d)` to `window.overlaps(a,b,c,d)` — no argument list grows, the
  anchor binds once per scope instead of repeating 25 times in `TimeslotGeneratorService`, and
  transposing the anchor with a scheduling time becomes a compile error instead of a silently wrong
  answer (the decisive point: under a static `overlapsFromDayStart(LocalTime dayStart, LocalTime s1,
  …)` every parameter is a `LocalTime`, so a transposition across a 37-site mechanical migration
  compiles clean). `MidnightTimeArithmeticGuardTest` exempts `com.wfm.util.DayWindow` by class name,
  not by call shape, so instance methods stay exempt. Satisfies criterion 1's "supplies an explicit
  day-start parameter" — supplied at construction.
  Rejected: **static anchored twins** (`overlapsFromDayStart` etc.) — most mechanical and most
  consistent with the five primitives Phase 18 landed, but produces a 6-argument all-`LocalTime`
  static and the transposition hazard above. Rejected: a **`DayStart` value type** as the first
  static parameter — closes transposition while keeping `DayWindow` static-only, but adds a new type
  to a phase confined to `DayWindow` and its call sites, and edges toward the wrapper-type approach
  `REQUIREMENTS.md` rejected for business dates (a different concern, but a reviewer will ask).
  — **Reversibility:** `costly` — `DayWindow` stops being a static-only utility, and Phase 20's joins,
  coverage reporting and SOLV-07's anchor-agreement guard are all written against whatever shape this
  establishes. D-20 already accepted the related consequence that `DayWindow` stops being a pure time
  utility.

- **D-05:** **Hand-composition at call sites is ruled out, and the reason is a guard blind spot, not
  verbosity.** Only five anchored primitives exist; the seven composite and derived forms
  (`durationMinutes`, `isForwardWithinDay`, `overlaps`, `contains`, `startsBefore`, `plusWithinDay`,
  `toLocalTime`) have no anchored twin, and the Phase 18 deprecation javadoc tells callers to compose
  `startMinuteFromDayStart` / `endMinuteFromDayStart` by hand. Verified against the guard's actual
  detection: `MidnightTimeArithmeticGuardTest` scans unconditionally for `Duration.between(`,
  `ChronoUnit.MINUTES`, `.plusMinutes(`, `.minusMinutes(`, and for `.isAfter(` / `.isBefore(` /
  `.compareTo(` gated by a `LocalTime`-receiver-name heuristic. **It does not look at `int` arithmetic
  or `int` comparisons at all.** So `endMinuteFromDayStart(ds,e) - startMinuteFromDayStart(ds,s)` at
  the 11 `durationMinutes` sites, and a four-call `&&` at the 5 `overlaps` sites, would leave the
  guard green while 37 call sites each reimplement half-open interval semantics — the
  "guard-becomes-decoration" failure that class's own javadoc warns against, reached through a blind
  spot rather than an allowlist, and a direct contradiction of `DayWindow`'s opening sentence ("the
  single implementation of … do these intervals overlap"). Every composite gets an instance method;
  the semantics stay in the one class. **The Phase 18 `@Deprecated` javadoc text that directs callers
  to hand-compose is superseded and must not be followed literally.**

- **D-06:** **The nine forms become private, not deleted.** `startMinuteFromDayStart` and
  `endMinuteFromDayStart` both call `startMinute`, and `timeAtDayStartOffset` calls `toLocalTime`, so
  the minute-of-day arithmetic must survive internally. "The one-argument, midnight-implicit overload
  no longer exists" (criterion 1) means *no longer public*; D-03's inverted guard is what makes that
  boundary enforced rather than conventional.

- **D-07:** **A midnight anchor in `src/main` is allowed but must argue for itself.**
  `DayWindow.anchoredAt(LocalTime.MIDNIGHT)` always compiles, is always correct on today's data, and
  is indistinguishable in a diff from a real desk anchor — the one substitution that silently restores
  the old semantics. `src/test/resources/midnight-time-arithmetic.md` gains a **third allowlist
  section**: every such use under `src/main/java` is listed with a note saying why no desk anchor is
  reachable there. The scan fails in both directions — an unlisted new use, and a listed entry whose
  line no longer exists — exactly like the two sections already proven able to go red. Rejected:
  **forbidding it outright**, because it pre-commits D-08's plumbing to "thread it everywhere, no
  exceptions" before it is known that every site can reach a desk, and `FteSpreadsheetGenerator` (a
  static utility with its own `main` and zero `Desk` references) is a standing counter-example — a
  carve-out for it would be this allowlist by another name. Rejected: **no guard**, in a codebase that
  has twice found this exact allowlist pattern worth building.
  — **Reversibility:** `reversible` — a third section in an existing markdown allowlist and a third
  scan in an existing guard test.

### How the anchor reaches the call sites

- **D-08:** **Two channels, matched to the two worlds.**
  **Solver:** add `LocalTime dayStart` to the `ScheduleConfig` record, filled once in
  `SolverService.buildSchedule` from the `Desk`. This is precisely what that record documents itself
  as being for — surfacing a desk entity field to the solver as a `@ProblemFactProperty` scalar — it
  already carries `startTime`, `endTime` and `schedulingMode`, and `ScheduleConstraintProvider`, the
  23-reference file, already has it in scope in its constraints. `ScheduleOutputService` reaches it
  the same way: it holds no `Desk` reference but does receive the `Schedule`, so `getScheduleConfig()`
  is its path.
  **Services:** each builds the bound `DayWindow` once per public method from the `Desk` it already
  loads, then passes **the `DayWindow` object, not a `LocalTime`**, down into model helpers. Model
  classes never hold or look up an anchor — `ShiftTemplate` holds only a `deskId` (a `UUID`), and a
  `DayWindow` parameter cannot be confused with a scheduling time.
  Rejected: **threading from the controller** end to end with no solver channel — most explicit and
  fully compiler-forced, but the widest diff in the milestone's riskiest phase, touching signatures
  well outside `DayWindow`'s call sites, and duplicating what `ScheduleConfig` exists to do.
  Rejected: **denormalising `day_start` onto `shift_template`** — smallest call-site diff, but a
  second copy of desk state with no write-path guard over it, which is the inverse of BDAY-08's
  one-derivation-site rule, plus a schema change in a phase whose criterion 5 confines the commit to
  `DayWindow` and its call sites.
  — **Reversibility:** `costly` — `ScheduleConfig` gains a component (every construction site and its
  12-argument delegating constructor move with it), and the model-class signature changes propagate to
  every caller of `covers`, `getBreakStartTime` and the net-hours calculation.

- **D-09:** The concrete model-class signature changes: `ShiftBandPair.covers(Timeslot)` and its
  delegated six-argument form, `ShiftTemplateBreakBand.getBreakStartTime(template)` /
  `getBreakEndTime(template)`, and `ShiftTemplate`'s net-hours calculation at `ShiftTemplate.java:135`
  each take a `DayWindow`. `ShiftBandPair.covers`'s "load-bearing single-implementation discipline"
  (its own javadoc, D-08 / T-15-10-04) must survive the change — the two forms stay one
  implementation.

### Commit boundary

- **D-10:** **The `ScheduleConfig.dayStart` plumbing lands as its own commit, before the
  re-anchoring.** Additive, nothing reads it yet, provably a no-op — the same additive-then-consume
  shape Phase 18 used for the anchored helpers (D-19). The re-anchoring commit that follows then
  genuinely touches only `DayWindow` and files that reference it, so criterion 5's
  `git diff --name-only` check is clean on its own terms and the revert target is exactly the risky
  edit. Rejected: **one commit for everything** — one revert with no ordering to reason about, but
  criterion 5's file list then includes `ScheduleConfig` and `SolverService`, which carry no
  `DayWindow` reference, so the criterion either gets restated at planning time or fails on a
  technicality. Rejected: **deferring the solver channel to Phase 20** and leaving
  `ScheduleConstraintProvider` on an allowlisted midnight anchor — keeps this phase smallest, but
  rests the single largest call-site cluster (23 references) on a D-07 allowlist entry.

### The forward-interval refusal

- **D-11:** **`durationMinutes` stops judging ordering; the save-path refusal that already exists
  keeps the behaviour.** Criterion 2 requires end-before-start to stop throwing — it now means
  "crosses the anchor". Verified that this loses nothing: `ShiftTemplateService.validate:220-233`
  already refuses a non-forward envelope via `isForwardWithinDay` at line 228 and throws "Shift
  template end time must be after its start time" **before** line 232 ever reaches
  `durationMinutes`, so `DayWindow`'s throw is already unreachable through the template save path and
  serves only as a backstop. The anchored `isForwardWithinDay` preserves that refusal exactly: at a
  `00:00` anchor a `15:00–14:00` envelope gives offsets 900 and 840, not forward, refused; a
  `22:00–06:00` envelope gives 1320 and 360, not forward, refused — correct, because overnight
  templates need both a non-midnight desk (Phase 20) and OVNT-01 (Phase 21). `ShiftTemplateService`
  needs the desk anchor, which it can already reach — it holds `deskId` and calls
  `validateGridAlignment(deskId, …)`.
  Rejected: a **max-span bound inside `DayWindow`** — keeps a loud failure at the boundary and needs
  no new validation surface, but makes `DayWindow` hold an opinion about how long a shift may be,
  which is desk policy, not interval arithmetic. Rejected: **accepting the loss** with no replacement.

- **D-12:** **Relaxing that refusal for legitimately spanning templates is Phase 21's job (OVNT-01),
  and extending it to the shift-library paths that bypass it today is OVNT-05's.** Phase 19
  establishes the seam and changes neither.

### Proving equivalence

- **D-13:** **Criterion 3 compares against a frozen oracle copied into the test.** Today's nine
  implementations are copied verbatim into a private nested class in `DayWindowTest`, and a
  parameterised sweep asserts `anchoredAt(MIDNIGHT).f(…)` equals `oracle.f(…)` at **every point of
  the input domain, not sampled**. This literally satisfies the criterion's "byte-identical to the
  pre-migration implementation" where D-04/D-06 mean that implementation is no longer callable, it
  cannot go stale (it is frozen by definition, which is D-24's philosophy applied to a test), and it
  covers the seven composite functions that `AnchoredEquivalenceAtMidnightAnchor` — already
  exhaustive across all 1440 minutes, but only for the five primitives — does not reach.
  Rejected: **treating the existing suite as the oracle** by re-pointing `PositionMatters` / `Duration`
  / `Forward` / `Overlaps` / `Contains` / `StartsBefore` / `Conversion` to the instance API at a
  midnight anchor — no duplicated implementation and the smallest diff, but coverage becomes whatever
  those assertions happen to cover: exhaustive for the primitives, spot checks for the composites.
  Rejected: a **golden reference table** captured before the edit — strongest and deletable later, but
  reintroduces the mechanism D-13 of Phase 18 explicitly rejected (defensible for deterministic pure
  functions, but it invites the argument).
  — **Reversibility:** `reversible` — roughly 60 lines of test-only code, deletable once v1.5 ships.

- **D-14:** **The existing `DayWindowTest` structure is kept, re-pointed, and must still pass
  unchanged in its assertions.** `AnchoredEquivalenceAtMidnightAnchor`, `AnchoredBehaviorAt2100`,
  `RoundTripConsistency` (already sweeping `MIDNIGHT`, `21:00`, `06:15`, `23:45`) and
  `FailLoudlyAtTheDayStartBoundary` are Phase 18's positive proof that the anchored path works at a
  non-midnight anchor; they move to the instance API without weakening. The eight other test files
  referencing `DayWindow` move with it.

### Claude's Discretion

- The exact instance-method naming (whether `window.durationMinutes(…)` keeps today's names verbatim
  or the five existing `…FromDayStart` primitives shed that now-redundant suffix as instance methods).
  Preference: shed the suffix — it exists to distinguish anchored from midnight-implicit statics, and
  after D-06 that distinction no longer has two sides.
- Whether `anchoredAt` caches or interns instances, and whether the class stays `final`.
- How the frozen oracle's sweep is bounded for the four-argument `overlaps` and `contains` (a full
  1440⁴ cross-product is not runnable; a structured sweep over boundary-adjacent quadruples plus an
  exhaustive sweep of the two-argument forms is the intent).
- Task ordering and plan decomposition within the two commits D-10 fixes.

### Folded Todos

None — see Reviewed Todos below.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Milestone scope and sequencing

- `.planning/ROADMAP.md` §"Phase 19: DayWindow Re-anchoring" — the five success criteria this phase
  is judged on, and the "do not combine with the join re-point" instruction
- `.planning/ROADMAP.md` §"Phase 20: Solver Business-Date Correctness" — what D-01 hands forward, and
  the phase whose criterion 2 the gate deletion now belongs to
- `.planning/REQUIREMENTS.md` §"Decisions taken at scoping" — the compiler-forced-migration decision
  and the rejected `BusinessDate` wrapper type, neither of which is relitigated here
- `.planning/REQUIREMENTS.md` §"What research established" — the narrower-than-112-sites finding, and
  the silent-non-join failure mode that motivates Phase 20's match-count assertions
- `.planning/phases/18-business-day-foundation-guards/18-CONTEXT.md` — D-05 (BDAY-04 owns the
  retirement), D-06 (15-minute target range), **D-07 (superseded on ownership by D-01 above; its
  one-visible-line intent stands)**, D-19/D-20/D-21 (the additive anchored helpers, `DayWindow` taking
  on `LocalDate`, and the live deprecation count), D-22 (`periodStart`/`periodEnd` mean business
  dates), D-24 (comments state their reason inline and cite requirement IDs, never phase numbers)

### The utility being re-anchored

- `src/main/java/com/wfm/util/DayWindow.java` — the nine midnight-implicit public forms above the
  banner comment at the `BDAY-03` divider, the five anchored primitives below it, and the class
  javadoc whose `00:00`-by-position statement must survive the rewrite (D-20)
- `src/test/java/com/wfm/util/DayWindowTest.java` — 87 references; `AnchoredEquivalenceAtMidnightAnchor`,
  `AnchoredBehaviorAt2100`, `RoundTripConsistency`, `FailLoudlyAtTheDayStartBoundary` and
  `DeprecationIsLive` (the class D-03 inverts)

### The guard whose blind spot shapes D-05

- `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` — `RAW_ARITHMETIC_TOKENS`,
  `COMPARISON_TOKENS`, `isRawComparison`'s receiver-name heuristic, the class-name exemption for
  `com.wfm.util.DayWindow`, and the two-directional allowlist machinery D-07 extends
- `src/test/resources/midnight-time-arithmetic.md` — §"Permitted raw time arithmetic" and
  §"Permitted raw time comparisons"; D-07 adds a third section alongside them

### Call sites (264 references, 27 files — recounted 2026-09-30, superseding the roadmap's ~112 estimate)

- `src/main/java/com/wfm/service/TimeslotGeneratorService.java` (25) — already takes `dayStart` as a
  parameter per D-19/D-22
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` (23) — D-08's solver channel
- `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` (17)
- `src/main/java/com/wfm/service/ShiftTemplateService.java` (7) — holds the D-11 save-path refusal at
  `validate:220-233`
- `src/main/java/com/wfm/service/ScheduleOutputService.java` (7) — no `Desk` reference; reaches the
  anchor through `Schedule.getScheduleConfig()`
- `src/main/java/com/wfm/service/ScheduleExportService.java` (7) — the file whose raw `isAfter()`
  produced the demonstrated defect `5ddd8dc`
- `src/main/java/com/wfm/util/FteSpreadsheetGenerator.java` (6) — static utility with its own `main`
  and zero `Desk` references; the standing D-07 allowlist candidate
- `src/main/java/com/wfm/model/ShiftBandPair.java` (6), `ShiftTemplateBreakBand.java` (3),
  `ShiftTemplate.java` (3) — the D-09 model-class signature changes
- `src/main/java/com/wfm/service/StaffingRequirementService.java` (4),
  `ShiftLibraryValidationService.java` (4), `FteUploadService.java` (4), `SolverService.java` (3),
  `DeskService.java` (1, javadoc only), `TimeslotRepository.java` (1), `model/Desk.java` (1)
- Main-side calls to the nine deprecated forms: `startMinute` 19, `plusWithinDay` 18, `endMinute` 15,
  `toLocalTime` 12, `durationMinutes` 11, `overlaps` 5, `contains` 2, `isForwardWithinDay` 1,
  `startsBefore` 0

### Plumbing D-08 and D-10 touch

- `src/main/java/com/wfm/model/ScheduleConfig.java` — the `@ProblemFactProperty` scalar gaining
  `dayStart`; its class javadoc states the pattern, and its 12-argument delegating constructor moves
  with the change
- `src/main/java/com/wfm/model/Schedule.java:321-332` — `getScheduleConfig()`, where the value is read
- `src/main/java/com/wfm/service/SolverService.java` — `buildSchedule`, where it is filled from the `Desk`
- `src/main/java/com/wfm/service/DeskService.java:207-254` — `setDayStart`; D-02's javadoc correction
  only, the gate itself untouched

### Phase 18's guard tests that criterion 4 requires stay green

- `src/test/java/com/wfm/solver/MidnightBoundaryFixture.java` (653 lines) and
  `MidnightBoundaryRegressionTest.java` — BDAY-06's three constructed scenarios, all at a `00:00`
  anchor, pinned rather than solved
- `src/test/java/com/wfm/service/MidnightBoundaryPropertyTest.java` — BDAY-06's plain-unit scenarios
- `src/test/java/com/wfm/service/BusinessDateWritePathGuardTest.java` — BDAY-08's single-derivation
  guard
- `src/test/java/com/wfm/service/TimeslotGeneratorBusinessDateTest.java` — BDAY-03's 21:00 generator
  proof, the other existing positive non-midnight evidence
- `src/test/resources/midnight-boundary-scenarios.md` — the non-vacuity validator's scenario manifest

### Project conventions

- `.planning/codebase/CONVENTIONS.md`, `.planning/codebase/TESTING.md`
- `.planning/STATE.md` — session state; note its "next migration is V40" line is a stale v1.3-era note
  (18-CONTEXT D-09); schema head is V53 after Phase 18

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **The five anchored primitives already exist and are already proven.** Phase 18 landed
  `startMinuteFromDayStart`, `endMinuteFromDayStart`, `timeAtDayStartOffset`, `businessDateOf` and
  `calendarDateAtDayStartOffset`, with `AnchoredEquivalenceAtMidnightAnchor` proving the collapse onto
  their midnight counterparts exhaustively across all 1440 minutes rather than at sampled points. This
  phase builds instance methods over them, not new arithmetic.
- **`@Deprecated` is already on all nine midnight-implicit forms**, each naming BDAY-04, so the build
  already maintains a live warning count of exactly what must be re-pointed (D-21) — the roadmap's
  "re-grep the count fresh" action is satisfied by both the compiler and the recount in
  `<canonical_refs>` above.
- **The two-directional allowlist guard pattern is proven twice** in this codebase and is what D-07
  extends: fails on an unlisted new occurrence and on a stale entry whose line has gone.
- **`DayWindowTest.DeprecationIsLive` already enumerates the method set reflectively** — D-03 inverts
  that machinery rather than writing a scanner.
- **`ScheduleConfig` is the established channel** for surfacing a desk entity field to the solver, and
  already carries `startTime`, `endTime` and `schedulingMode` by exactly this route.

### Established Patterns

- **Additive first, consume second.** Phase 18 landed the anchored helpers as a provable no-op before
  anything read them; D-10 applies the same shape to the `ScheduleConfig` field.
- **Guards are structural and two-directional**, never one-way assertions, and each carries a
  red-proof that the guard can actually fail (D-25's parameterised scan root).
- **Comments state their own reason inline and cite requirement IDs, never phase numbers** (D-24) —
  phase numbers have demonstrably moved between v1.4 and v1.5.
- **`00:00` means different things by position**, and that rule generalises rather than disappears:
  after this phase a time equal to the anchor in an end position is the end of the business day. The
  class javadoc's articulation of it is the clearest statement of the rule anywhere in the codebase and
  must survive.

### Integration Points

- `DayWindow` ← 18 files under `src/main/java`, 9 under `src/test/java`
- `ScheduleConfig` → `Schedule.getScheduleConfig()` → every constraint in `ScheduleConstraintProvider`
- `SolverService.buildSchedule` → the one place the `Desk`'s anchor enters the solver
- `ShiftTemplateService.validate` → the save-path refusal D-11 preserves and OVNT-01/OVNT-05 later relax
- `src/test/resources/midnight-time-arithmetic.md` → `MidnightTimeArithmeticGuardTest`'s three scans

### Creative Options the architecture enables

- Binding the anchor once per scope (D-04) makes the anchor a visible local in every method that does
  interval arithmetic — which is itself documentation, and makes a missing anchor a missing variable
  rather than a missing argument.
- The D-07 allowlist becomes the honest inventory of every place in the codebase that still cannot
  reach a desk anchor. Phase 20 and Phase 21 inherit that list as a work queue rather than having to
  rediscover it.

</code_context>

<specifics>
## Specific Ideas

- The contradiction in D-01 was found in this discussion, not inherited as a known open question. It
  should be corrected at its source: `18-CONTEXT.md` D-07's ownership claim, `DeskService:213`'s
  javadoc, and the desk-config disclosure copy all currently name the wrong phase.
- D-05's finding — that the guard cannot see `int` arithmetic — is worth stating in
  `midnight-time-arithmetic.md` itself, so the next person who considers hand-composing at a call site
  learns the guard will not catch them from the guard's own documentation.
- Criterion 3's "byte-identical to the pre-migration implementation" was written before the
  bound-instance shape was chosen. D-13 satisfies its intent; the planner should not read it as
  requiring the old public API to survive.

</specifics>

<deferred>
## Deferred Ideas

- **Extending the constructed midnight-boundary scenarios to a non-midnight anchor.** Phase 18's three
  `MidnightBoundaryFixture` scenarios are all built at a `00:00` anchor, so this phase's criteria prove
  "nothing moved" plus unit-level anchored behaviour, and the first scenario-level proof that
  re-anchoring works at `21:00` lands in Phase 20. It cannot move earlier: those scenarios are assembled
  `Schedule` objects whose constraints are only meaningful once the joins are business-date-keyed
  (SOLV-01). **Phase 20 should own it explicitly** rather than inherit it by accident — otherwise the
  join migration and the first non-midnight scenario proof go red together with two candidate causes.
- **The three BDAY-06 scenarios Phase 18 deferred** (D-16: a shift starting before and ending after
  midnight, and the two whose property cannot exist yet) — Phase 21, once OVNT-01 makes them
  constructible.
- **Deleting the frozen oracle** (D-13) once v1.5 ships and nothing compares against pre-migration
  behaviour any more.
- **Widening the `00:00`-only gate to 15-minute boundaries**, and whether Phase 20 inherits a tested
  range check or writes one — the third option raised and not taken in this discussion was building the
  range validation here behind the still-present narrowing, mirroring D-08's build-it-unreachable
  precedent. Phase 20 may still want that shape.

### Reviewed Todos (not folded)

All three `todo.match-phase` hits matched on generic keywords only (`phase`, `one`, `desk`, `atomic`)
and none touches interval arithmetic or the day-start anchor:

- *Provide a blank upload template spreadsheet, one sheet per desk* (`upload`, score 0.6) — upload
  tooling; unrelated. See the desk-assignment-upload hazard, not this phase.
- *Cross-agent seat displacement for the atomic shift move* (`solver`, score 0.6) — a solver move
  selector; matched on the word "atomic". Phase 19 changes no move selectors.
- *Terraform state diverges from live RDS password and publicly_accessible* (`infra`, score 0.6) —
  infrastructure drift; matched on the word "phase" alone.

</deferred>

---

*Phase: 19-daywindow-re-anchoring*
*Context gathered: 2026-09-30*
