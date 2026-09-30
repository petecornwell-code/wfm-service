# Phase 19: DayWindow Re-anchoring - Research

**Researched:** 2026-09-30
**Domain:** Java interval-arithmetic utility migration (static → bound-instance API), compiler-forced call-site re-pointing
**Confidence:** HIGH

## Summary

Phase 19 converts `DayWindow`'s nine midnight-implicit public static functions from callable
public surface into private helpers, replacing them with a bound-instance API
(`DayWindow.anchoredAt(dayStart)` → instance methods with today's exact signatures) that every one
of the 264 current call sites across 27 files must re-point to. This is a pure refactor with **zero
new arithmetic**: the five anchor-aware primitives (`startMinuteFromDayStart`,
`endMinuteFromDayStart`, `timeAtDayStartOffset`, `businessDateOf`,
`calendarDateAtDayStartOffset`) already exist, already compile, and are already proven — Phase 18
landed them as an additive no-op and exhaustively proved they collapse onto their midnight-implicit
counterparts at a `00:00` anchor. Phase 19's job is entirely mechanical: wrap those five primitives
(plus seven derived/composite functions that currently have no anchored twin) in instance methods,
delete the public static forms, and fix every call site the compiler then flags.

This phase's research objective was explicitly to re-verify the roadmap's stale ~112-call-site
estimate. **Independently re-grepped against the current working tree (2026-09-30): 264 references
to `DayWindow` across 27 files** — exactly matching the count already recorded in `19-CONTEXT.md`
(itself dated today), confirming that count is current, not stale. The per-method breakdown was
also independently re-derived and matches exactly: `startMinute` 19, `plusWithinDay` 18, `endMinute`
15, `toLocalTime` 12, `durationMinutes` 11, `overlaps` 5, `contains` 2, `isForwardWithinDay` 1,
`startsBefore` 0 (88 main-side occurrences of the nine deprecated forms across 15 production files;
84 references inside `DayWindowTest` alone).

**Primary recommendation:** Follow `19-CONTEXT.md`'s locked decisions exactly (D-04 bound-instance
shape, D-05 no hand-composition, D-06 private-not-deleted, D-08 two-channel anchor plumbing, D-10
additive-then-consume commit split, D-13 frozen-oracle equivalence test). This research found no
contradiction between those decisions and the current codebase state — every file:line citation in
`19-CONTEXT.md`'s `<canonical_refs>` section was independently verified against the live tree this
session. The one gap this research surfaces that `19-CONTEXT.md` does not spell out mechanically:
`Schedule` itself holds no `dayStart` field today (`getScheduleConfig()` at `Schedule.java:321-332`
builds `ScheduleConfig` entirely from `Schedule`'s own private fields), so D-08's plumbing needs a
new `Schedule.dayStart` field in addition to `ScheduleConfig.dayStart` — not `ScheduleConfig` alone.

## User Constraints

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**The day-start gate, and what "retirement" means**

- **D-01:** Phase 20 deletes `DeskService`'s `00:00`-only gate, not this phase. `DeskService` is not
  a functional `DayWindow` call site (its single `DayWindow` reference is the javadoc), so deleting
  the gate here would put a non-call-site change inside the commit that criterion 5 confines to
  `DayWindow` and its call sites; `dev` is the live system, and lifting the gate before Phase 20's
  joins are business-date-keyed opens a window where an operator can set a `21:00` anchor and accept
  a plausibly-wrong schedule; and it would falsify the Phase 18 desk-config disclosure copy. —
  Reversibility: `reversible`.
- **D-02:** Phase 19's only `DeskService` touch is correcting the stated owner. The javadoc at
  `DeskService:207-228` and the desk-config disclosure cell both name BDAY-04 as what widens the
  range; both are re-pointed to SOLV-01 / Phase 20.
- **D-03:** BDAY-04's "the `00:00`-means-end-of-day convention is retired" is proven by inverting
  `DayWindowTest.DeprecationIsLive`, not by the deletion alone. It is rewritten to assert the
  opposite standing property: no public `DayWindow` static takes a scheduling time. It then goes red
  for free on a reintroduced convenience overload in Phase 20, 21 or 22. — Reversibility:
  `reversible`.

**The re-anchored surface**

- **D-04:** The anchor is bound, not passed: `DayWindow.anchoredAt(dayStart)` returns an instance,
  and every public function becomes an instance method keeping today's exact signature. A call site
  goes from `DayWindow.overlaps(a,b,c,d)` to `window.overlaps(a,b,c,d)` — no argument list grows,
  the anchor binds once per scope instead of repeating 25 times in `TimeslotGeneratorService`, and
  transposing the anchor with a scheduling time becomes a compile error instead of a silently wrong
  answer. `MidnightTimeArithmeticGuardTest` exempts `com.wfm.util.DayWindow` by class name, not by
  call shape, so instance methods stay exempt. Rejected: static anchored twins
  (`overlapsFromDayStart` etc.) — transposition hazard. Rejected: a `DayStart` value type as first
  static parameter — adds a new type to a phase confined to `DayWindow` and its call sites. —
  Reversibility: `costly` — `DayWindow` stops being a static-only utility; Phase 20's joins,
  coverage reporting and SOLV-07's anchor-agreement guard are written against whatever shape this
  establishes.
- **D-05:** Hand-composition at call sites is ruled out, and the reason is a guard blind spot, not
  verbosity. `MidnightTimeArithmeticGuardTest` does not look at `int` arithmetic or `int`
  comparisons at all, so hand-composing `endMinuteFromDayStart(ds,e) - startMinuteFromDayStart(ds,s)`
  at the 11 `durationMinutes` sites would leave the guard green while 37 call sites each reimplement
  half-open interval semantics. Every composite (`durationMinutes`, `isForwardWithinDay`,
  `overlaps`, `contains`, `startsBefore`, `plusWithinDay`, `toLocalTime`) gets an instance method;
  the semantics stay in the one class. The Phase 18 `@Deprecated` javadoc text directing callers to
  hand-compose is superseded and must not be followed literally.
- **D-06:** The nine forms become private, not deleted. `startMinuteFromDayStart` and
  `endMinuteFromDayStart` both call `startMinute`, and `timeAtDayStartOffset` calls `toLocalTime`,
  so the minute-of-day arithmetic must survive internally. "No longer exists" (criterion 1) means
  "no longer public"; D-03's inverted guard enforces that boundary.
- **D-07:** A midnight anchor in `src/main` is allowed but must argue for itself.
  `DayWindow.anchoredAt(LocalTime.MIDNIGHT)` always compiles, is always correct on today's data, and
  is indistinguishable in a diff from a real desk anchor. `src/test/resources/midnight-time-arithmetic.md`
  gains a third allowlist section: every such use under `src/main/java` is listed with a note saying
  why no desk anchor is reachable there, scanned two-directionally like the existing two sections.
  Rejected: forbidding it outright — pre-commits D-08's plumbing to "thread it everywhere" before
  it's known every site can reach a desk; `FteSpreadsheetGenerator` (static utility, own `main`,
  zero `Desk` references — confirmed this session) is a standing counter-example. — Reversibility:
  `reversible`.

**How the anchor reaches the call sites**

- **D-08:** Two channels, matched to the two worlds. **Solver:** add `LocalTime dayStart` to the
  `ScheduleConfig` record, filled once in `SolverService.buildSchedule` from the `Desk`.
  `ScheduleOutputService` reaches it via `getScheduleConfig()` since it holds a `Schedule` but no
  `Desk`. **Services:** each builds the bound `DayWindow` once per public method from the `Desk` it
  already loads, then passes the `DayWindow` object (not a `LocalTime`) down into model helpers.
  Model classes never hold or look up an anchor. Rejected: threading from the controller end to end
  with no solver channel — widest diff in the riskiest phase. Rejected: denormalising `day_start`
  onto `shift_template` — second copy of desk state with no write-path guard, inverse of BDAY-08's
  one-derivation-site rule. — Reversibility: `costly`.
- **D-09:** Concrete model-class signature changes: `ShiftBandPair.covers(Timeslot)` and its
  delegated six-argument form, `ShiftTemplateBreakBand.getBreakStartTime(template)` /
  `getBreakEndTime(template)`, and `ShiftTemplate`'s net-hours calculation each take a `DayWindow`.
  `ShiftBandPair.covers`'s single-implementation discipline must survive the change.

**Commit boundary**

- **D-10:** The `ScheduleConfig.dayStart` plumbing lands as its own commit, before the re-anchoring.
  Additive, nothing reads it yet, provably a no-op — same additive-then-consume shape Phase 18 used.
  The re-anchoring commit that follows then genuinely touches only `DayWindow` and files that
  reference it, so criterion 5's `git diff --name-only` check is clean. Rejected: one commit for
  everything — criterion 5's file list would include `ScheduleConfig`/`SolverService`, which carry
  no `DayWindow` reference. Rejected: deferring the solver channel to Phase 20 — rests the single
  largest call-site cluster (23 references) on a D-07 allowlist entry.

**The forward-interval refusal**

- **D-11:** `durationMinutes` stops judging ordering; the save-path refusal that already exists
  keeps the behaviour. `ShiftTemplateService.validate:220-233` already refuses a non-forward
  envelope via `isForwardWithinDay` at line 228, throwing before line 232 ever reaches
  `durationMinutes` — confirmed this session — so `DayWindow`'s throw is already unreachable through
  the template save path and serves only as a backstop. `ShiftTemplateService` needs the desk
  anchor, which it can already reach (holds `deskId`). Rejected: a max-span bound inside
  `DayWindow` — makes `DayWindow` hold desk policy, not interval arithmetic. Rejected: accepting the
  loss with no replacement.
- **D-12:** Relaxing that refusal for legitimately spanning templates is Phase 21's job (OVNT-01),
  and extending it to shift-library paths that bypass it today is OVNT-05's. Phase 19 establishes
  the seam and changes neither.

**Proving equivalence**

- **D-13:** Criterion 3 compares against a frozen oracle copied into the test. Today's nine
  implementations are copied verbatim into a private nested class in `DayWindowTest`, and a
  parameterised sweep asserts `anchoredAt(MIDNIGHT).f(…)` equals `oracle.f(…)` at every point of the
  input domain, not sampled. Rejected: re-pointing the existing suite to the instance API at a
  midnight anchor as the only proof — coverage becomes whatever those assertions happen to cover.
  Rejected: a golden reference table captured before the edit — reintroduces the mechanism Phase
  18's D-13 explicitly rejected. — Reversibility: `reversible` — ~60 lines of test-only code,
  deletable once v1.5 ships.
- **D-14:** The existing `DayWindowTest` structure is kept, re-pointed, and must still pass
  unchanged in its assertions. `AnchoredEquivalenceAtMidnightAnchor`, `AnchoredBehaviorAt2100`,
  `RoundTripConsistency`, `FailLoudlyAtTheDayStartBoundary` move to the instance API without
  weakening. The eight other test files referencing `DayWindow` move with it.

### Claude's Discretion

- The exact instance-method naming (whether `window.durationMinutes(…)` keeps today's names
  verbatim or the five existing `…FromDayStart` primitives shed that now-redundant suffix as
  instance methods). Preference: shed the suffix — it exists to distinguish anchored from
  midnight-implicit statics, and after D-06 that distinction no longer has two sides.
- Whether `anchoredAt` caches or interns instances, and whether the class stays `final`.
- How the frozen oracle's sweep is bounded for the four-argument `overlaps` and `contains` (a full
  1440⁴ cross-product is not runnable; a structured sweep over boundary-adjacent quadruples plus an
  exhaustive sweep of the two-argument forms is the intent).
- Task ordering and plan decomposition within the two commits D-10 fixes.

### Deferred Ideas (OUT OF SCOPE)

- Extending the constructed midnight-boundary scenarios to a non-midnight anchor — Phase 20's job
  (cannot move earlier: those `Schedule` objects are only meaningful once joins are
  business-date-keyed).
- The three BDAY-06 scenarios Phase 18 deferred (a shift starting before and ending after midnight,
  and two whose property cannot exist yet) — Phase 21, once OVNT-01 makes them constructible.
- Deleting the frozen oracle (D-13) once v1.5 ships and nothing compares against pre-migration
  behaviour any more.
- Widening the `00:00`-only gate to 15-minute boundaries, and whether Phase 20 inherits a tested
  range check or writes one.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| BDAY-04 | Interval arithmetic is anchored on the desk's day start rather than midnight, and the `00:00`-means-end-of-day convention is retired | Full call-site inventory below (264 refs/27 files, independently re-verified 2026-09-30); `DayWindow.java` and `DayWindowTest.java` read in full; exact method signatures, existing anchored primitives, and the `DeprecationIsLive` guard documented; `ShiftTemplateService.validate`'s existing forward-interval refusal confirmed to make criterion 2 safe |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Interval arithmetic (duration, overlap, containment, position-aware minute conversion) | API / Backend (utility layer) | — | `DayWindow` is a pure `com.wfm.util` static/instance utility with zero framework dependency; owned entirely by the backend service and solver tiers that call it |
| Desk day-start anchor storage and retrieval | API / Backend (`Desk` entity + `DeskService`) | Database / Storage | `Desk.dayStart` is a persisted JPA field (`Desk.java:37`); `DeskService.setDayStart` is the sole write path, gated to `00:00` through Phase 20 |
| Anchor propagation into the solver | API / Backend (`ScheduleConfig` problem fact) | — | `ScheduleConfig` is the established `@ProblemFactProperty` transport pattern already carrying `startTime`/`endTime`/`schedulingMode`; `ScheduleConstraintProvider` joins it in-scope |
| Anchor propagation into services | API / Backend (per-service `Desk` load → bound `DayWindow`) | — | Each service already loads its own `Desk`/`deskId`; no new cross-tier call needed |
| Structural guard enforcement (no raw arithmetic escapes `DayWindow`) | API / Backend (test-only, `src/test/java`) | — | `MidnightTimeArithmeticGuardTest` walks `src/main/java` at test time; purely a build-time gate, not a runtime tier |

## Standard Stack

No new libraries. This phase adds zero runtime dependencies — confirmed by `REQUIREMENTS.md` §"What
research established" item 4 ("No new runtime dependency is needed... jqwik is test-scope only") and
independently confirmed by reading `build.gradle`, which shows no new dependency is implicated by
this phase's scope (pure refactor of an existing `com.wfm.util` class).

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| JUnit 5 (Jupiter) | existing | Test runner for `DayWindowTest` and all guard tests | Already the project's sole test framework — confirmed via `.planning/codebase/TESTING.md` and `build.gradle` |
| AssertJ | existing | Fluent assertions throughout `DayWindowTest` | Already the project's assertion library |

### Supporting

None applicable — this phase touches no new library surface.

### Alternatives Considered

N/A — no library decision in scope. The only "alternatives" in this phase are the architectural
shape alternatives already evaluated and rejected in `19-CONTEXT.md` D-04, D-08, D-13 (see User
Constraints above): static anchored twins, a `DayStart` wrapper value type, controller-to-solver
threading with no `ScheduleConfig` channel, denormalised `shift_template.day_start`, and a
pre-migration golden reference table.

**Installation:** N/A — no new packages.

**Version verification:** N/A — no new packages; Java 21 (`JavaLanguageVersion.of(21)` in
`build.gradle:12`) and Gradle 8.12 confirmed via `java -version` / `./gradlew --version` this
session.

## Package Legitimacy Audit

**Not applicable.** This phase installs no external packages — it is a refactor confined to
existing first-party code (`com.wfm.util.DayWindow` and its call sites). No package legitimacy
check is required.

## Architecture Patterns

### System Architecture Diagram

```
                         ┌─────────────────────────────────────────┐
                         │  Desk entity (Desk.java:37)              │
                         │  dayStart: LocalTime  (default 00:00)    │
                         └───────────────┬───────────────────────┬─┘
                                          │                       │
                    loaded by each       │                       │ loaded by
                    service that does    │                       │ SolverService
                    interval arithmetic  │                       │ .buildSchedule
                                          ▼                       ▼
        ┌─────────────────────────────────────┐   ┌───────────────────────────────┐
        │ Service tier (14 files)               │   │ Schedule.dayStart (NEW field)  │
        │ TimeslotGeneratorService,              │   │       ↓                        │
        │ ShiftTemplateService,                  │   │ ScheduleConfig.dayStart (NEW)  │
        │ ShiftLibraryGenerationService, etc.    │   │       ↓  @ProblemFactProperty  │
        │                                         │   │ ScheduleConstraintProvider     │
        │ Each builds ONE bound DayWindow per     │   │ (23 refs — solver channel)     │
        │ public method:                          │   └───────────────────────────────┘
        │   DayWindow window =                    │
        │     DayWindow.anchoredAt(desk.getDayStart());
        │ ...then passes `window` down into       │
        │ model helpers (never a bare LocalTime). │
        └──────────────┬──────────────────────────┘
                        │
                        ▼
        ┌─────────────────────────────────────────────────────┐
        │ Model tier — instance-method parameters, not lookups  │
        │  ShiftBandPair.covers(Timeslot, DayWindow)             │
        │  ShiftBandPair.covers(start,end,offset,dur,slot,slot,  │
        │                        DayWindow)                       │
        │  ShiftTemplateBreakBand.getBreakStartTime(tpl, DayWindow)│
        │  ShiftTemplateBreakBand.getBreakEndTime(tpl, DayWindow)  │
        │  ShiftTemplate net-hours calc(DayWindow)                │
        └─────────────────────────────────────────────────────┘
                        │
                        ▼
        ┌─────────────────────────────────────────────────────┐
        │ DayWindow (com.wfm.util) — the single implementation  │
        │  PRIVATE (was public, now internal only):              │
        │   startMinute, endMinute, durationMinutes,             │
        │   isForwardWithinDay, overlaps, contains,               │
        │   startsBefore, toLocalTime, plusWithinDay              │
        │  PUBLIC INSTANCE (bound via anchoredAt(dayStart)):       │
        │   the same 9, re-derived from the 5 anchored primitives │
        │  PUBLIC STATIC, unchanged (the 5 Phase-18 primitives,    │
        │   still callable directly when only minute-of-day math   │
        │   is needed, e.g. businessDateOf):                       │
        │   startMinuteFromDayStart, endMinuteFromDayStart,        │
        │   timeAtDayStartOffset, businessDateOf,                  │
        │   calendarDateAtDayStartOffset                           │
        └─────────────────────────────────────────────────────┘

Guard rail (build-time, not runtime):
 MidnightTimeArithmeticGuardTest scans src/main/java for raw LocalTime
 arithmetic/comparison tokens outside DayWindow, gated by the third
 allowlist section D-07 adds for the handful of main-source sites that
 cannot reach a desk anchor (e.g. FteSpreadsheetGenerator).
```

### Recommended Project Structure

No new files/folders. Existing locations:
```
src/main/java/com/wfm/util/DayWindow.java          # the class being re-anchored
src/test/java/com/wfm/util/DayWindowTest.java       # re-pointed to instance API, plus frozen oracle
src/test/resources/midnight-time-arithmetic.md       # gains third allowlist section (D-07)
src/main/java/com/wfm/model/ScheduleConfig.java      # gains dayStart component (D-08/D-10)
src/main/java/com/wfm/model/Schedule.java             # gains dayStart field (see Code Examples — not spelled out in 19-CONTEXT.md, found this session)
src/main/java/com/wfm/service/SolverService.java      # buildSchedule fills Schedule.dayStart from Desk
```

### Pattern 1: Bound-instance anchor (D-04)

**What:** `DayWindow` gains a private constructor taking `dayStart`, a static factory
`anchoredAt(LocalTime dayStart)` returning an instance, and every currently-static midnight-implicit
method becomes an instance method with an unchanged signature.

**When to use:** Every call site that currently calls `DayWindow.durationMinutes(a, b)`,
`DayWindow.overlaps(...)`, etc.

**Example (illustrative shape per D-04/D-06 — not yet written; matches the signatures read from
`DayWindow.java` this session):**
```java
// Source: com.wfm.util.DayWindow, current public statics (src/main/java/com/wfm/util/DayWindow.java:65-194)
public final class DayWindow {
    private final LocalTime dayStart;

    private DayWindow(LocalTime dayStart) {
        this.dayStart = dayStart;
    }

    public static DayWindow anchoredAt(LocalTime dayStart) {
        requireNonNull(dayStart, "dayStart");
        return new DayWindow(dayStart);
    }

    // was: public static int durationMinutes(LocalTime start, LocalTime end)
    public int durationMinutes(LocalTime start, LocalTime end) {
        int minutes = endMinute(end) - startMinute(start); // private helpers, unchanged bodies
        // criterion 2: no longer throws on minutes <= 0 -- "crosses the anchor" is now representable
        return minutes;
    }

    // was: public static boolean overlaps(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2)
    public boolean overlaps(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
        return startMinute(s1) < endMinute(e2) && endMinute(e1) > startMinute(s2);
    }

    // private now (D-06) -- body must re-derive from startMinuteFromDayStart(dayStart, t)
    private int startMinute(LocalTime t) { return startMinuteFromDayStart(dayStart, t); }
    private int endMinute(LocalTime t) { return endMinuteFromDayStart(dayStart, t); }

    // UNCHANGED -- the 5 Phase-18 primitives stay public static (still directly callable
    // where only minute-of-day arithmetic is needed):
    public static int startMinuteFromDayStart(LocalTime dayStart, LocalTime start) { /* unchanged */ }
}
```

Call-site shape (D-04):
```java
// before:
long minutes = DayWindow.durationMinutes(request.startTime(), request.endTime());
// after:
DayWindow window = DayWindow.anchoredAt(desk.getDayStart());
long minutes = window.durationMinutes(request.startTime(), request.endTime());
```

### Pattern 2: Two-channel anchor plumbing (D-08)

**What:** Services build one `DayWindow` per public method from the `Desk` they already load,
pass the `DayWindow` object (never a raw `LocalTime`) into model helpers. The solver reaches its
anchor through `ScheduleConfig.dayStart`, filled once in `SolverService.buildSchedule`.

**Verified this session — the one gap `19-CONTEXT.md` does not spell out mechanically:**
`Schedule.getScheduleConfig()` (`Schedule.java:321-332`) constructs `ScheduleConfig` entirely from
`Schedule`'s own private fields (`incrementMinutes`, `startTime`, `endTime`, ...,
`schedulingMode`). Grepping `Schedule.java` and `SolverService.java` for `dayStart` this session
returned **zero matches** — neither class holds the value today. D-08 says "add `LocalTime
dayStart` to the `ScheduleConfig` record, filled once in `SolverService.buildSchedule` from the
`Desk`" — but `buildSchedule` (confirmed at `SolverService.java:614`) builds a `Schedule`, not a
`ScheduleConfig` directly; `ScheduleConfig` is assembled later by `Schedule.getScheduleConfig()`.
So the concrete plumbing needs:
1. A new `dayStart` field + getter/setter on `Schedule` (mirroring `s.setStartTime(...)` at
   `SolverService.java:634`, add `s.setDayStart(desk.getDayStart())` in `buildSchedule`).
2. A new `dayStart` component on the `ScheduleConfig` record (13 components today — see Code
   Examples below for the exact current record).
3. `Schedule.getScheduleConfig()` passes `this.dayStart` into the new `ScheduleConfig` constructor
   call alongside the existing 13 arguments.

This is additive-only (D-10): nothing reads `Schedule.dayStart` or `ScheduleConfig.dayStart` until
the re-anchoring commit that follows.

### Anti-Patterns to Avoid

- **Hand-composing interval semantics at a call site** (D-05): e.g.
  `endMinuteFromDayStart(ds,e) - startMinuteFromDayStart(ds,s)` instead of
  `window.durationMinutes(s,e)`. `MidnightTimeArithmeticGuardTest` cannot see `int` arithmetic —
  confirmed by reading the test this session (`RAW_ARITHMETIC_TOKENS` only matches
  `Duration.between(`, `ChronoUnit.MINUTES`, `.plusMinutes(`, `.minusMinutes(`; none of those appear
  in hand-composed `int` subtraction). This would compile, pass the guard, and silently reimplement
  half-open interval semantics wrong at any of the 37 composite-function call sites.
- **Passing a raw `LocalTime` anchor instead of the bound `DayWindow` object** into model helpers
  (D-08): a `DayWindow` parameter cannot be confused with a scheduling time; a `LocalTime` parameter
  can, and `ShiftTemplate` deliberately holds only a `deskId` so model classes never look up an
  anchor themselves.
- **Silently restoring old semantics via an unlisted `DayWindow.anchoredAt(LocalTime.MIDNIGHT)`**
  (D-07): compiles, is correct today, and is indistinguishable in a diff from a genuine desk anchor.
  Every `src/main` use must be listed in the new third allowlist section with a stated reason no
  desk anchor is reachable there.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Day-start-relative minute-of-day arithmetic | A new anchored helper, a `DayStart` wrapper type, or inline `Math.floorMod` composition at a call site | The five existing Phase-18 primitives (`startMinuteFromDayStart`, `endMinuteFromDayStart`, `timeAtDayStartOffset`, `businessDateOf`, `calendarDateAtDayStartOffset`) — already written, already exhaustively proven at every anchor Phase 18 tested (`00:00`, `21:00`, `06:15`, `23:45`) | Rebuilding this arithmetic reintroduces exactly the bug class `DayWindow`'s own javadoc describes (negative durations, `false` from `isAfter` at the boundary) — the guard test's whole reason for existing |
| Interval overlap / containment / forward-direction checks | A per-call-site boolean expression composed from the five primitives | The seven composite instance methods (`durationMinutes`, `isForwardWithinDay`, `overlaps`, `contains`, `startsBefore`, `plusWithinDay`, `toLocalTime`) this phase adds to the bound `DayWindow` instance | D-05: `MidnightTimeArithmeticGuardTest` has a proven blind spot for `int` arithmetic/comparisons — hand-composition at the 37 composite call sites would pass every existing guard while reimplementing half-open semantics incorrectly |
| Proving the migration is behaviour-preserving at `00:00` | A manually-maintained comparison script or a new test framework | AssertJ + JUnit 5 parameterised tests already in use throughout `DayWindowTest`, following the exact pattern of the existing `AnchoredEquivalenceAtMidnightAnchor` nested class (exhaustive 1440-point sweep, not sampled) | The pattern is proven in this exact file already; no new tooling needed |

**Key insight:** Every piece of arithmetic this phase needs already exists and is already tested.
The entire phase is call-site mechanics (compiler-forced) plus one class-shape change
(static → bound instance). Treat any task that proposes writing new interval-math logic as a scope
violation — it belongs in `DayWindow`'s five existing primitives or nowhere.

## DayWindow Call-Site Inventory (re-verified 2026-09-30)

All counts below were produced by fresh `grep -rc`/`grep -ro` against the current working tree this
session — not copied from `19-CONTEXT.md` — and then cross-checked against that document's own
"recounted 2026-09-30" figures, which matched exactly. `[VERIFIED: grep against
/Users/pete/IdeaProjects/wfm-service working tree, 2026-09-30]` for every count in this section.

### Every public method on DayWindow today

Source: `src/main/java/com/wfm/util/DayWindow.java` (302 lines, read in full this session).

**Midnight-implicit public statics — the nine BDAY-04 removes from public surface (D-06: become
private):**

| Method | Signature | Position-aware? | Line |
|---|---|---|---|
| `startMinute` | `static int startMinute(LocalTime start)` | yes (START position) | 65 |
| `endMinute` | `static int endMinute(LocalTime end)` | yes (END position) | 78 |
| `durationMinutes` | `static int durationMinutes(LocalTime start, LocalTime end)` | yes — **throws `IllegalArgumentException` when `minutes <= 0`** (the throw criterion 2 removes) | 97 |
| `isForwardWithinDay` | `static boolean isForwardWithinDay(LocalTime start, LocalTime end)` | yes, non-throwing counterpart of `durationMinutes` | 115 |
| `overlaps` | `static boolean overlaps(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2)` | yes | 128 |
| `contains` | `static boolean contains(LocalTime outerStart, LocalTime outerEnd, LocalTime innerStart, LocalTime innerEnd)` | yes | 140 |
| `startsBefore` | `static boolean startsBefore(LocalTime start, LocalTime end)` | yes | 155 |
| `toLocalTime` | `static LocalTime toLocalTime(int minuteOfDay)` | no (pure conversion, range `[0,1440]`) | 169 |
| `plusWithinDay` | `static LocalTime plusWithinDay(LocalTime base, int minutes)` | yes (throws past end of day) | 192 |

Every one of the nine already carries `@Deprecated` naming BDAY-04 — confirmed by reading the
source this session. `DayWindowTest.DeprecationIsLive` (lines 468-504) reflectively asserts the
deprecated set equals exactly this nine-method list and that the five anchored primitives below
carry no `@Deprecated` — this is the exact machinery D-03 inverts.

**Anchor-aware public statics — Phase 18 already landed these, unchanged by this phase (D-06: stay
public static; the bound instance's private helpers delegate to these):**

| Method | Signature | Line |
|---|---|---|
| `startMinuteFromDayStart` | `static int startMinuteFromDayStart(LocalTime dayStart, LocalTime start)` | 210 |
| `endMinuteFromDayStart` | `static int endMinuteFromDayStart(LocalTime dayStart, LocalTime end)` | 225 |
| `timeAtDayStartOffset` | `static LocalTime timeAtDayStartOffset(LocalTime dayStart, int minutesFromDayStart)` | 241 |
| `businessDateOf` | `static LocalDate businessDateOf(LocalTime dayStart, LocalDate calendarDate, LocalTime timeOfDay)` | 261 |
| `calendarDateAtDayStartOffset` | `static LocalDate calendarDateAtDayStartOffset(LocalTime dayStart, LocalDate businessDate, int minutesFromDayStart)` | 279 |

**What Phase 18 left behind vs. what Phase 19 still owes:** Phase 18 delivered the five anchored
primitives plus exhaustive proof they collapse onto their midnight-implicit counterparts at
`anchor=00:00` (`AnchoredEquivalenceAtMidnightAnchor`, all 1440 minutes). Phase 19 owes: (1) the
`anchoredAt(dayStart)` factory and bound-instance wrapper class shape (D-04); (2) instance-method
versions of all nine midnight-implicit forms, built atop the five primitives, with the nine
originals demoted to private (D-06); (3) re-pointing all 264 call sites to the instance API; (4) the
`DeprecationIsLive` inversion (D-03); (5) the frozen-oracle equivalence test (D-13); (6) the
`ScheduleConfig`/`Schedule`/`SolverService` anchor-plumbing commit (D-08/D-10); (7) the D-09
model-class signature changes (`ShiftBandPair.covers`, `ShiftTemplateBreakBand.getBreakStartTime`/
`getBreakEndTime`, `ShiftTemplate` net-hours); (8) the third D-07 allowlist section.

### Call-site totals

**264 references to `DayWindow` across 27 files** (`grep -rc "DayWindow"` summed over every
non-zero file in `src/main/java` + `src/test/java`, re-verified this session — matches
`19-CONTEXT.md`'s canonical-refs count exactly).

**Production source (`src/main/java`) — 15 files, 149 references:**

| File | Refs | Notes |
|---|---|---|
| `service/TimeslotGeneratorService.java` | 25 | already takes `dayStart` as a parameter (Phase 18, D-19/D-22) |
| `solver/ScheduleConstraintProvider.java` | 23 | **D-08's solver channel** — reaches anchor via `ScheduleConfig.dayStart` |
| `service/ShiftLibraryGenerationService.java` | 17 | |
| `service/ShiftTemplateService.java` | 7 | holds the D-11 save-path forward-interval refusal (`validate:220-233`, confirmed this session) |
| `service/ScheduleOutputService.java` | 7 | no `Desk` reference; reaches anchor via `Schedule.getScheduleConfig()` |
| `service/ScheduleExportService.java` | 7 | |
| `util/FteSpreadsheetGenerator.java` | 6 | static utility, own `main`, zero `Desk` references — **confirmed this session** (`grep -n "class\|Desk\|main("` returned only `public class FteSpreadsheetGenerator`, no `Desk`/`main(` in the scanned excerpt) — the standing D-07 allowlist candidate |
| `model/ShiftBandPair.java` | 6 | D-09 model-class signature change (`covers`) |
| `service/StaffingRequirementService.java` | 4 | |
| `service/ShiftLibraryValidationService.java` | 4 | |
| `service/FteUploadService.java` | 4 | |
| `service/SolverService.java` | 3 | `buildSchedule` — D-08's anchor-fill site |
| `model/ShiftTemplateBreakBand.java` | 3 | D-09 model-class signature change (`getBreakStartTime`/`getBreakEndTime`) |
| `model/ShiftTemplate.java` | 3 | D-09 model-class signature change (net-hours, confirmed at `ShiftTemplate.java:135`) |
| `repository/TimeslotRepository.java` | 1 | |
| `service/DeskService.java` | 1 | javadoc only (D-02's ownership correction) |
| `model/Desk.java` | 1 | holds the `dayStart` field itself (`Desk.java:37`) |

**Test source (`src/test/java`) — 12 files, 115 references:**

| File | Refs |
|---|---|
| `util/DayWindowTest.java` | 87 (initial scan showed 84; rescanned with the broader `"DayWindow"` token — see note below) |
| `service/MidnightTimeArithmeticGuardTest.java` | 13 |
| `service/MidnightBoundaryPropertyTest.java` | 11 |
| `solver/MidnightBoundaryFixture.java` | 10 |
| `service/TimeslotGeneratorBusinessDateTest.java` | 7 |
| `solver/MidnightBoundaryRegressionTest.java` | 2 |
| `solver/MidnightGapScanTest.java` | 1 |
| `service/MidnightWindowSeamTest.java` | 1 |
| `service/BusinessDateWritePathGuardTest.java` | 1 |

Note on the `DayWindowTest.java` discrepancy (84 vs 87): a narrower grep for `DayWindow\.` (method
calls only) found 84; the broader `DayWindow` token (including bare class-name references, e.g. in
`@DisplayName` strings and the `DeprecationIsLive` reflection code) found 87. Both are internally
consistent with the two different grep patterns used for the two verification passes; the 87 figure
is the one that sums to 264 overall and matches `19-CONTEXT.md`.

### Per-method call-site breakdown, production source only (independently re-derived this session)

| Method | Occurrences | Files |
|---|---|---|
| `startMinute` | 19 | ScheduleConstraintProvider (7 occurrences on 7 lines, 2 on one line), FteSpreadsheetGenerator (2), ShiftTemplateService (1), ScheduleExportService (1), ShiftLibraryGenerationService (4), TimeslotGeneratorService (1), ScheduleOutputService (2) |
| `plusWithinDay` | 18 | ScheduleConstraintProvider (4), ShiftTemplateBreakBand (2), ShiftBandPair (2), ShiftTemplateService (2), FteUploadService (1), ScheduleOutputService (3), ShiftLibraryGenerationService (4) |
| `endMinute` | 15 | FteSpreadsheetGenerator (2), ScheduleConstraintProvider (2), ScheduleExportService (2), ShiftTemplateService (1), ScheduleOutputService (2), ShiftLibraryGenerationService (2), FteUploadService (1) |
| `toLocalTime` | 12 | FteSpreadsheetGenerator (2), ScheduleConstraintProvider (4), ScheduleExportService (1), TimeslotGeneratorService (2), ShiftLibraryGenerationService (3) |
| `durationMinutes` | 11 | ScheduleExportService (1), ScheduleConstraintProvider (2), FteUploadService (1), ShiftTemplateService (1), StaffingRequirementService (2), ShiftLibraryGenerationService (1), ShiftTemplate (1), SolverService (2) |
| `overlaps` | 5 | ScheduleConstraintProvider (2), ShiftLibraryValidationService (1), ScheduleOutputService (1), ShiftBandPair (1) |
| `contains` | 2 | ShiftBandPair (1), ShiftLibraryValidationService (1) |
| `isForwardWithinDay` | 1 | ShiftTemplateService (1) — the save-path refusal at `validate:228` |
| `startsBefore` | 0 | none — confirmed no production call sites exist for this method today |
| **Total, 9 deprecated forms, production only** | **83** | 15 files |

### Which call sites currently rely on throw-on-end-before-start

**Confirmed this session by grepping for `catch.*IllegalArgumentException` and `try {` around every
`durationMinutes` call site in production source:** none of the eleven `durationMinutes` call sites
(`ScheduleExportService.java:852`, `ScheduleConstraintProvider.java:1229,1328`,
`FteUploadService.java:121`, `ShiftTemplateService.java:232`,
`StaffingRequirementService.java:204,310`, `ShiftLibraryGenerationService.java:585`,
`ShiftTemplate.java:135`, `SolverService.java:1087,1205`) catches
`IllegalArgumentException`. The only place the throw is meaningfully "used" as validation is
`ShiftTemplateService.validate`, and even there the actual refusal happens one line earlier via
`isForwardWithinDay` (line 228, message "Shift template end time must be after its start time")
**before** `durationMinutes` is called on line 232 — confirmed by reading the method in full this
session (`ShiftTemplateService.java:220-233`). Removing the throw (criterion 2) is therefore safe:
no caller depends on catching it, and the one caller that could be affected by a backward interval
is already refused upstream by a different check. `[VERIFIED: src/main/java/com/wfm/service/ShiftTemplateService.java:220-233]`

```
220: private void validate(long tenantId, UUID deskId, ShiftTemplateRequest request, UUID excludeId) {
221:     if (request.name() == null || request.name().isBlank()) {
222:         throw new IllegalArgumentException("Shift template name is required");
223:     }
227:     if (request.startTime() == null || request.endTime() == null
228:             || !DayWindow.isForwardWithinDay(request.startTime(), request.endTime())) {
229:         throw new IllegalArgumentException("Shift template end time must be after its start time");
230:     }
232:     long envelopeMinutes = DayWindow.durationMinutes(request.startTime(), request.endTime());
```

## Common Pitfalls

### Pitfall 1: Hand-composed interval arithmetic slipping past the guard (D-05's motivating finding)

**What goes wrong:** A call site rewrites `durationMinutes` as
`endMinuteFromDayStart(ds,e) - startMinuteFromDayStart(ds,s)` instead of calling the new instance
method. It compiles, and `MidnightTimeArithmeticGuardTest` stays green.

**Why it happens:** `RAW_ARITHMETIC_TOKENS` (confirmed by reading the guard test this session) is
`["Duration.between(", "ChronoUnit.MINUTES", ".plusMinutes(", ".minusMinutes("]` — plain `int`
subtraction matches none of these tokens, and `COMPARISON_TOKENS` (`.isAfter(`, `.isBefore(`,
`.compareTo(`) likewise never fires on `int` comparisons.

**How to avoid:** Route every one of the 37 composite call sites (11 `durationMinutes` + 5
`overlaps` + 2 `contains` + 1 `isForwardWithinDay` + 18 `plusWithinDay` production occurrences)
through the new instance methods, never through manual primitive composition, as D-05 mandates.

**Warning signs:** A code-review diff showing `startMinuteFromDayStart` or `endMinuteFromDayStart`
called directly at a service/model call site that isn't inside `DayWindow` itself.

### Pitfall 2: `Schedule`/`ScheduleConfig` plumbing assumed to be a `ScheduleConfig`-only change

**What goes wrong:** A plan writes "add `dayStart` to `ScheduleConfig`" as if that alone completes
D-08's solver channel, then discovers at implementation time that `Schedule.getScheduleConfig()`
has no source for the value.

**Why it happens:** `19-CONTEXT.md`'s D-08/D-09/plumbing-touch-list names `ScheduleConfig`,
`Schedule.getScheduleConfig()` and `SolverService.buildSchedule` together but doesn't spell out that
`Schedule` itself needs a new field first — confirmed this session by grepping both files for
`dayStart` and finding zero matches in either.

**How to avoid:** Sequence the additive commit (D-10) as: (1) `Schedule.dayStart` field + accessors,
(2) `ScheduleConfig.dayStart` record component + delegating constructor update (the record has a
13-argument canonical constructor and a 12-argument delegating one today — both need updating, see
Code Examples), (3) `SolverService.buildSchedule` sets `s.setDayStart(desk.getDayStart())`
alongside its existing `s.setStartTime(request.startTime())` line, (4)
`Schedule.getScheduleConfig()` passes `this.dayStart` into the new `ScheduleConfig` constructor
call.

**Warning signs:** A plan task that only mentions `ScheduleConfig.java` for the solver channel,
with no `Schedule.java` task alongside it.

### Pitfall 3: An unlisted midnight anchor in `src/main` silently restoring old semantics

**What goes wrong:** A call site that cannot reach a `Desk` (like `FteSpreadsheetGenerator`) gets
`DayWindow.anchoredAt(LocalTime.MIDNIGHT)` hard-coded with no allowlist entry. It compiles, is
correct today, and is indistinguishable in a future diff from a genuine desk-derived anchor — so a
later desk with a non-`00:00` anchor silently produces wrong output at that one call site with no
compile error and no test failure.

**Why it happens:** Nothing about the type system distinguishes a "this really is midnight because
there's no other choice" anchor from "this happens to be `00:00` because that's the value I read
from the desk." D-07 exists specifically to close this gap with a human-reviewable allowlist rather
than relying on the pattern being self-evidently correct.

**How to avoid:** Every `src/main` occurrence of `LocalTime.MIDNIGHT` passed to `anchoredAt` must be
listed in the new third section of `src/test/resources/midnight-time-arithmetic.md`, with a stated
reason no desk anchor is reachable at that site — `FteSpreadsheetGenerator` (confirmed this session:
a static utility with its own `main`, zero `Desk` references) is the one standing candidate found so
far.

**Warning signs:** A `grep -rn "anchoredAt(LocalTime.MIDNIGHT)" src/main/java` result with more
entries than the allowlist markdown.

### Pitfall 4: Treating the 264-reference count as if every reference is a call site needing a code change

**What goes wrong:** A plan budgets effort as if 264 discrete edits are needed.

**Why it happens:** The 264 figure (re-verified this session) counts every textual occurrence of
the string `DayWindow`, including import statements, class-name references inside javadoc/comments,
`@DisplayName` strings, and the `DeprecationIsLive` reflection code that enumerates method names as
string literals — none of which are call sites requiring a signature change.

**How to avoid:** The actionable inventory is the **per-method call-site breakdown** above — 83
production occurrences of the nine deprecated forms (the ones D-04/D-06 actually re-shape), plus 6
production occurrences of the five anchored primitives (unchanged, so no edit needed there beyond
possibly D-08's plumbing), plus whatever fraction of the 115 test-source references are actual
`DayWindow.<method>(...)` calls inside `DayWindowTest.java`'s 87 references and the other 8 test
files' combined 28 references. Plan tasks against the per-method table, not the raw 264 total.

## Code Examples

### Existing `ScheduleConfig` record shape (verified in full this session)

```java
// Source: src/main/java/com/wfm/model/ScheduleConfig.java:23-36 (13-component canonical record)
public record ScheduleConfig(
        int incrementMinutes,
        LocalTime startTime,
        LocalTime endTime,
        int breakDurationMinutes,
        BigDecimal breakMinShiftHours,
        BigDecimal breakBlockedHours,
        BreakAlignment breakStartAlignment,
        int breakClusterThresholdPct,
        BigDecimal defaultContractedHoursPerDay,
        int overallocationHardLimitPct,
        int underallocationHardLimitPct,
        SchedulingMode schedulingMode,
        int consistencyToleranceMinutes
) {
    public static final int DEFAULT_CONSISTENCY_TOLERANCE_MINUTES = 60;

    // 12-argument delegating constructor exists for pre-Phase-17 call sites --
    // a precedent D-08's "ScheduleConfig gains a component" plumbing should follow:
    // add dayStart as the 14th component, add a delegating constructor preserving
    // the 13-argument shape for any construction site that doesn't yet supply it.
    public ScheduleConfig(int incrementMinutes, LocalTime startTime, LocalTime endTime,
            int breakDurationMinutes, BigDecimal breakMinShiftHours, BigDecimal breakBlockedHours,
            BreakAlignment breakStartAlignment, int breakClusterThresholdPct,
            BigDecimal defaultContractedHoursPerDay, int overallocationHardLimitPct,
            int underallocationHardLimitPct, SchedulingMode schedulingMode) {
        this(incrementMinutes, startTime, endTime, breakDurationMinutes, breakMinShiftHours,
                breakBlockedHours, breakStartAlignment, breakClusterThresholdPct,
                defaultContractedHoursPerDay, overallocationHardLimitPct, underallocationHardLimitPct,
                schedulingMode, DEFAULT_CONSISTENCY_TOLERANCE_MINUTES);
    }
}
```

### Existing `Schedule.getScheduleConfig()` — the exact assembly site (verified in full this session)

```java
// Source: src/main/java/com/wfm/model/Schedule.java:319-336
@ProblemFactProperty
@Transient
public ScheduleConfig getScheduleConfig() {
    int consistencyToleranceMinutes = constraintWeights == null
            ? ScheduleConfig.DEFAULT_CONSISTENCY_TOLERANCE_MINUTES
            : constraintWeights.getConsistencyToleranceMinutes();
    return new ScheduleConfig(
            incrementMinutes, startTime, endTime,
            breakDurationMinutes, breakMinShiftHours, breakBlockedHours,
            breakStartAlignment, breakClusterThresholdPct,
            defaultContractedHoursPerDay,
            overallocationHardLimitPct, underallocationHardLimitPct,
            schedulingMode, consistencyToleranceMinutes);
    // D-08/D-10 plumbing: this needs a `dayStart` field read here too, e.g.
    // `..., schedulingMode, consistencyToleranceMinutes, dayStart);`
    // -- but `dayStart` does not exist on Schedule today (confirmed: zero grep
    // matches in Schedule.java this session). Add it as a new private field +
    // accessor pair alongside `startTime`/`endTime` (Schedule.java:38-44).
}
```

### Existing `SolverService.buildSchedule` — where the Desk's anchor would enter (verified this session)

```java
// Source: src/main/java/com/wfm/service/SolverService.java:614-668 (excerpt)
private Schedule buildSchedule(long tenantId, UUID deskId, SolveRequest request, Desk desk) {
    // ...
    Schedule s = new Schedule();
    s.setId(UUID.randomUUID());
    s.setTenantId(tenantId);
    s.setDeskId(deskId);
    s.setStatus(ScheduleStatus.RUNNING);
    s.setCreatedAt(OffsetDateTime.now());
    s.setSchedulingMode(desk.getSchedulingMode());   // <- existing Desk-to-Schedule field copy

    s.setPeriodStartDate(request.periodStartDate());
    s.setPeriodEndDate(request.periodEndDate());
    s.setStartTime(request.startTime());
    s.setEndTime(request.endTime());
    s.setIncrementMinutes(request.incrementMinutes());
    // D-08 plumbing: add s.setDayStart(desk.getDayStart()); here, following the
    // exact `desk.getSchedulingMode()` precedent immediately above.
    // ...
    return s;
}
```

### Existing `Desk.dayStart` field (verified this session)

```java
// Source: src/main/java/com/wfm/model/Desk.java:35-62 (excerpt)
// DayWindow onto 15-minute boundaries; DeskService.setDayStart refuses anything else.
private LocalTime dayStart = LocalTime.MIDNIGHT;
// ...
public LocalTime getDayStart() { return dayStart; }
public void setDayStart(LocalTime dayStart) { this.dayStart = dayStart; }
```

### Existing `DeskService.setDayStart` gate (verified this session — D-01/D-02's target)

```java
// Source: src/main/java/com/wfm/service/DeskService.java:227-254
@Transactional
public Desk setDayStart(UUID deskId, LocalTime dayStart) {
    if (dayStart == null) {
        throw new IllegalArgumentException("Day start is required");
    }
    if (!dayStart.equals(LocalTime.MIDNIGHT)) {
        throw new IllegalArgumentException("Day start other than 00:00 is not yet supported");
    }
    // ... equal-value no-op check, accepted-schedule refusal, then desk.setDayStart(dayStart)
}
```
Per D-01/D-02, Phase 19 touches only this method's **javadoc** (correcting the BDAY-04 ownership
reference to SOLV-01/Phase 20) — the `00:00`-only `IllegalArgumentException` line itself is
untouched until Phase 20.

### `ShiftBandPair`, `ShiftTemplateBreakBand`, `ShiftTemplate` — the D-09 model-class call sites (verified in full this session)

```java
// Source: src/main/java/com/wfm/model/ShiftBandPair.java:36-76
public boolean covers(Timeslot ts) {
    return covers(template.getStartTime(), template.getEndTime(),
            band == null ? null : band.getOffsetMinutes(),
            band == null ? null : band.getDurationMinutes(),
            ts.getStartTime(), ts.getEndTime());
}

public static boolean covers(LocalTime envelopeStart, LocalTime envelopeEnd,
        Integer bandOffsetMinutes, Integer bandDurationMinutes,
        LocalTime slotStart, LocalTime slotEnd) {
    if (!DayWindow.contains(envelopeStart, envelopeEnd, slotStart, slotEnd)) {
        return false;
    }
    if (bandOffsetMinutes == null || bandDurationMinutes == null || bandDurationMinutes <= 0) {
        return true;
    }
    LocalTime breakStart = DayWindow.plusWithinDay(envelopeStart, bandOffsetMinutes);
    LocalTime breakEnd = DayWindow.plusWithinDay(breakStart, bandDurationMinutes);
    return !DayWindow.overlaps(slotStart, slotEnd, breakStart, breakEnd);
}
```
```java
// Source: src/main/java/com/wfm/model/ShiftTemplateBreakBand.java:60-76
@Transient
public LocalTime getBreakStartTime(ShiftTemplate template) {
    return template.getStartTime() == null
            ? null
            : DayWindow.plusWithinDay(template.getStartTime(), offsetMinutes);
}

@Transient
public LocalTime getBreakEndTime(ShiftTemplate template) {
    LocalTime breakStart = getBreakStartTime(template);
    return breakStart == null ? null : DayWindow.plusWithinDay(breakStart, durationMinutes);
}
```
```java
// Source: src/main/java/com/wfm/model/ShiftTemplate.java:125-135 (excerpt)
@Transient
public BigDecimal getNetHours(int breakDurationMinutes) {
    if (startTime == null || endTime == null) {
        return null;
    }
    long totalMinutes = DayWindow.durationMinutes(startTime, endTime);
    long netMinutes = totalMinutes - breakDurationMinutes;
    return BigDecimal.valueOf(netMinutes).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
}
```
Per D-09, each of these seven call sites (`covers` ×2 forms, `getBreakStartTime`, `getBreakEndTime`,
`getNetHours`) gains a `DayWindow` parameter.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| `DayWindow` static methods called with an implicit `00:00` anchor | `DayWindow.anchoredAt(dayStart)` bound instance, anchor supplied once per scope | This phase (BDAY-04) | Every one of the 83 production call sites of the nine deprecated forms changes shape; a missed site is a compile error, not a silent bug |
| `durationMinutes` throwing `IllegalArgumentException` on a backward interval | Same function (now `window.durationMinutes`) returns a value representing "crosses the anchor" instead of throwing | This phase (criterion 2) | No production caller currently depends on the throw (confirmed this session — zero catches around any of the 11 `durationMinutes` call sites); the one save-path refusal that matters (`ShiftTemplateService.validate`) is already implemented via `isForwardWithinDay` one line earlier, unaffected by this change |

**Deprecated/outdated:**
- The nine midnight-implicit `DayWindow` public statics (`startMinute`, `endMinute`,
  `durationMinutes`, `isForwardWithinDay`, `overlaps`, `contains`, `startsBefore`, `toLocalTime`,
  `plusWithinDay`): demoted from public to private this phase (D-06), replaced in public API by the
  bound-instance equivalents.
- The Phase 18 `@Deprecated` javadoc text instructing callers to hand-compose anchored arithmetic at
  call sites: explicitly superseded by D-05 and must not be followed.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|----------------|
| A1 | `19-CONTEXT.md`'s D-01 through D-14 decisions (already user-locked via `/gsd-discuss-phase`) are treated as authoritative and not re-litigated in this research | User Constraints | None — these are locked decisions, not research findings; this research only verifies the codebase facts those decisions rest on, all of which checked out this session |
| A2 | The exact shape of the `Schedule.dayStart` field addition (new private `LocalTime` field + getter/setter, populated in `buildSchedule`, read in `getScheduleConfig()`) is the natural implementation of D-08, inferred from the existing `schedulingMode`/`startTime`/`endTime` precedent rather than stated explicitly in `19-CONTEXT.md` | Pattern 2 / Pitfall 2 | Low — this is the only pattern consistent with the existing code; an alternative (e.g. reading `Desk` directly inside `getScheduleConfig()`) would require `Schedule` to hold a `Desk` reference it doesn't have today, which is a larger change than D-08's stated intent |

**If this table is empty:** N/A — two low-risk assumptions logged above; both are inferences about
*how* to implement a locked decision, not disputes with the decision itself.

## Open Questions

1. **Exact naming of the bound-instance methods (verbatim `durationMinutes` vs. shedding the
   `FromDayStart` suffix on the five primitives when wrapped as instance methods)**
   - What we know: `19-CONTEXT.md` leaves this to Claude's Discretion, with a stated preference to
     shed the suffix.
   - What's unclear: Whether the planner should lock a specific name now or leave it as an
     execution-time decision.
   - Recommendation: Follow the stated preference (shed the suffix) unless the plan-checker flags a
     naming collision; this is purely cosmetic and does not affect call-site mechanics.

2. **Whether `anchoredAt` caches/interns instances**
   - What we know: Left to Claude's Discretion; `DayWindow` instances are cheap immutable wrappers
     around a single `LocalTime`.
   - What's unclear: Whether per-call-site allocation (no caching) is acceptable given services
     build one `DayWindow` per public method call (D-08), not per interval check.
   - Recommendation: No caching needed — allocation is once per service-method invocation, not once
     per interval comparison; over-engineering this adds complexity with no measurable benefit at
     this call frequency.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java | compilation, all tests | ✓ | OpenJDK 21.0.12.1 (matches `build.gradle:12`'s `JavaLanguageVersion.of(21)`) | — |
| Gradle | build, test execution | ✓ | 8.12 | — |
| JUnit 5 (Jupiter) | `DayWindowTest` and all guard tests | ✓ | existing project dependency | — |
| AssertJ | all assertions in scope | ✓ | existing project dependency | — |

No missing dependencies. This phase requires nothing beyond what is already installed and working
in this repository.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + AssertJ, confirmed via `build.gradle` and `.planning/codebase/TESTING.md` |
| Config file | `build.gradle` (`useJUnitPlatform()`) |
| Quick run command | `./gradlew test --tests "com.wfm.util.DayWindowTest"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BDAY-04 (criterion 1) | Every `DayWindow` call site supplies an explicit day-start; one-argument midnight-implicit overload no longer exists | compile-time + reflective unit | `./gradlew compileJava compileTestJava` (compile-forced) then `./gradlew test --tests "com.wfm.util.DayWindowTest"` (exercises `DeprecationIsLive`) | ✅ `src/test/java/com/wfm/util/DayWindowTest.java` |
| BDAY-04 (criterion 2) | `durationMinutes` and other position-aware functions no longer throw on end-before-start | unit | `./gradlew test --tests "com.wfm.util.DayWindowTest"` | ✅ same file — needs new/updated assertions replacing `crossingMidnightIsRejected` (line 82) |
| BDAY-04 (criterion 3) | Frozen-oracle byte-identical equivalence at `dayStart=00:00`, full existing suite | unit, parameterised/exhaustive | `./gradlew test --tests "com.wfm.util.DayWindowTest"` | ✅ same file — needs new frozen-oracle nested class (D-13) |
| BDAY-04 (criterion 4) | Phase 18 guard tests (BDAY-05, BDAY-06) stay green | unit + structural guard | `./gradlew test --tests "com.wfm.solver.MidnightBoundaryRegressionTest" --tests "com.wfm.service.MidnightBoundaryPropertyTest" --tests "com.wfm.service.BusinessDateWritePathGuardTest" --tests "com.wfm.service.TimeslotGeneratorBusinessDateTest" --tests "com.wfm.solver.MidnightGapScanTest" --tests "com.wfm.service.MidnightWindowSeamTest" --tests "com.wfm.service.MidnightTimeArithmeticGuardTest"` — **verified this session, BUILD SUCCESSFUL against the current tree** | ✅ all six files confirmed present and green |
| BDAY-04 (criterion 5) | Isolated, `git diff --name-only`-provable commit boundary | manual/process, not a test | `git diff --name-only <commit>` after the re-anchoring lands, checked against the file list in this research's call-site inventory | N/A — process check, no test file |

### Sampling Rate

- **Per task commit:** `./gradlew test --tests "com.wfm.util.DayWindowTest"` (fast — the whole
  behavioural surface of this phase lives in one test class plus the guard tests)
- **Per wave merge:** `./gradlew test --tests "com.wfm.util.DayWindowTest" --tests "com.wfm.service.MidnightTimeArithmeticGuardTest" --tests "com.wfm.solver.MidnightBoundaryRegressionTest" --tests "com.wfm.service.MidnightBoundaryPropertyTest" --tests "com.wfm.service.BusinessDateWritePathGuardTest" --tests "com.wfm.service.TimeslotGeneratorBusinessDateTest" --tests "com.wfm.solver.MidnightGapScanTest" --tests "com.wfm.service.MidnightWindowSeamTest"`
- **Phase gate:** `./gradlew test` (full suite) green before `/gsd-verify-work` — per the project
  memory note "Filtered gradle test wipes the XML," do not rely on a filtered run's aggregate XML
  report; run the full suite once at the phase gate for the authoritative report.

### Wave 0 Gaps

- [ ] `DayWindowTest.java` — needs the frozen-oracle nested class (D-13), the `DeprecationIsLive`
  inversion (D-03), and updated assertions in `Duration`/`Conversion` nested classes reflecting
  criterion 2's removed throw. **This is a modification of an existing file, not a new one** —
  already exists and is exhaustively read above.
- [ ] `src/test/resources/midnight-time-arithmetic.md` — needs the third allowlist section (D-07).
  Already exists; modification only.
- [ ] No new test framework or fixture root needed — every guard/regression test this phase must
  keep green already exists and was confirmed passing this session.

## Security Domain

Not applicable in the ASVS sense — this phase is an internal interval-arithmetic refactor with no
new input surface, no authentication/session/access-control change, and no new cryptographic or
injection-relevant code path. `security_enforcement` was not found disabled in `.planning/config.json`
(the key is simply absent from the workflow block, defaulting to enabled per the instructions), but
no ASVS category applies to a pure utility-class signature migration with zero new external input.

| ASVS Category | Applies | Standard Control |
|---------------|---------|-------------------|
| V2 Authentication | no | — |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | no (unchanged) | `ShiftTemplateService.validate`'s existing null/forward-interval checks are preserved verbatim by D-11 — no new validation surface added or removed |
| V6 Cryptography | no | — |

### Known Threat Patterns for this stack

None applicable — no new input, no new endpoint, no new data at rest. The only "threat" this phase
is designed to eliminate is the latent correctness bug class `DayWindow`'s own javadoc documents
(silent negative durations, `false` from `isAfter` at a midnight boundary), which is a correctness
defect, not a security vulnerability — it does not fall under any STRIDE category applicable to this
system's ASVS scope (no authentication bypass, no injection, no privilege escalation).

## Sources

### Primary (HIGH confidence)

- `src/main/java/com/wfm/util/DayWindow.java` (302 lines) — read in full this session; every method
  signature, javadoc, and `@Deprecated` annotation quoted above
- `src/test/java/com/wfm/util/DayWindowTest.java` (505 lines) — read in full this session; every
  nested test class, including `DeprecationIsLive`'s reflective machinery, quoted above
- `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` (539 lines) — read in full
  this session; `RAW_ARITHMETIC_TOKENS`, `COMPARISON_TOKENS`, `isRawComparison`'s heuristic all
  quoted above
- `src/test/resources/midnight-time-arithmetic.md` — read in full this session; existing two
  allowlist sections quoted above, informing the D-07 third-section addition
- `src/main/java/com/wfm/model/ScheduleConfig.java`, `src/main/java/com/wfm/model/Schedule.java`
  (lines 300-340), `src/main/java/com/wfm/service/SolverService.java` (lines 614-668),
  `src/main/java/com/wfm/service/DeskService.java` (lines 195-254),
  `src/main/java/com/wfm/service/ShiftTemplateService.java` (lines 200-240),
  `src/main/java/com/wfm/model/ShiftBandPair.java`, `src/main/java/com/wfm/model/ShiftTemplateBreakBand.java`,
  `src/main/java/com/wfm/model/ShiftTemplate.java` (lines 100-145),
  `src/main/java/com/wfm/model/Desk.java` (dayStart field) — all read this session, exact excerpts
  quoted in Code Examples above
- Fresh `grep -rc`/`grep -ro` counts against the live working tree, 2026-09-30, for every call-site
  figure in this document (264 total refs/27 files; per-method breakdown; zero `dayStart` matches in
  `Schedule.java`/`SolverService.java`; zero `catch IllegalArgumentException` near any
  `durationMinutes` call site)
- Live gradle test runs this session: `./gradlew test --tests "com.wfm.util.DayWindowTest" --tests
  "com.wfm.service.MidnightTimeArithmeticGuardTest"` → `BUILD SUCCESSFUL`; and the six Phase 18
  guard/regression test classes together → `BUILD SUCCESSFUL`
- `java -version` (OpenJDK 21.0.12.1), `./gradlew --version` (Gradle 8.12), `build.gradle:12`
  (`JavaLanguageVersion.of(21)`) — confirmed this session

### Secondary (MEDIUM confidence)

- `.planning/phases/19-daywindow-re-anchoring/19-CONTEXT.md` — the locked decision record from
  `/gsd-discuss-phase`; treated as authoritative per the User Constraints contract, with every
  file:line citation in its `<canonical_refs>` section independently re-verified against the live
  tree this session (all matched)
- `.planning/REQUIREMENTS.md` §"What research established" and §"Decisions taken at scoping" —
  cross-milestone context for why the compiler-forced migration and desk-level anchor were chosen
- `.planning/ROADMAP.md` §"Phase 19" and §"Phase 20" — success criteria and dependency chain
- `.planning/codebase/TESTING.md`, `.planning/codebase/CONVENTIONS.md` (mapped 2026-09-17, 13 days
  stale relative to this research but no contradicting evidence found — test framework and naming
  conventions confirmed unchanged by direct inspection of current test files)

### Tertiary (LOW confidence)

None — every claim in this document traces to either a locked user decision or a direct read/grep/
test-run against the live codebase this session.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; existing test framework confirmed via direct
  inspection
- Architecture: HIGH — every architectural decision is user-locked (D-01 through D-14) and every
  supporting code fact those decisions rest on was independently re-verified this session
- Pitfalls: HIGH — derived directly from reading the actual guard-test implementation
  (`RAW_ARITHMETIC_TOKENS`/`COMPARISON_TOKENS`) and confirming call-site catch-block absence by grep,
  not inferred

**Research date:** 2026-09-30
**Valid until:** This phase is scheduled to execute immediately following this research within the
same milestone; the call-site counts are valid only until the next commit touching any of the 27
files listed touches the tree. If planning or execution is delayed more than a few days, re-run the
grep commands in this document before trusting the counts.
