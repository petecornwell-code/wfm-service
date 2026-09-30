# Stack Research: Overnight Shifts & Business Dates

**Domain:** Business-day/midnight-crossing interval modelling in an existing Spring Boot + Timefold
1.16.0 + PostgreSQL 16 scheduling service
**Researched:** 2026-09-30
**Confidence:** HIGH (grounded directly in this codebase's `DayWindow`, migrations, and
`build.gradle`, cross-checked against current library releases)

## Headline Finding

**This milestone needs no new core or runtime dependency.** Every requirement in
`PROJECT.md`'s v1.5 scope — desk day-start anchor, business date on timeslots, overnight
envelopes, business-date solver joins, hard minimum rest — is expressible with plain
`java.time.LocalTime`/`LocalDate`, plain Postgres `TIME`/`DATE` columns, and a generalized
`DayWindow`, exactly the primitives already in use for the same-day case. The only dependency
change worth making is **test-scoped**: adding jqwik for property-based construction of
midnight-boundary fixtures, which is what BDAY-06 ("constructed midnight-spanning scenarios,
chosen by the property under test") is actually asking for. Everything else below is a type,
column, and API-usage decision, not a library choice — which is itself the finding the
downstream roadmap needs: this is a **modelling milestone**, not a stack-acquisition one.

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| `java.time.LocalTime` | JDK 21 (unchanged) | Time-of-day storage for start/end/day-start-anchor | Already the type for every `start_time`/`end_time` column in this schema (`V1__initial_schema.sql:151-152,177-178`) and every `DayWindow` signature. No reason to introduce a second time-of-day type for the anchor (`desk.day_start_time`) — it is a `LocalTime` mapped exactly like `ShiftTemplate.startTime` already is. |
| `java.time.LocalDate` | JDK 21 (unchanged) | Calendar date and business date, both | `Timeslot.date` is already `LocalDate` (`Timeslot.java:26`). `business_date` is the same type with different derivation — not a new type. |
| `int` minute-of-day, anchor-relative | n/a | The interval arithmetic `DayWindow` performs | This is the established pattern for shift-scheduling systems and the one already in this codebase (`DayWindow.startMinute`/`endMinute` return `int` in `[0,1440]`). Generalizing it to be anchor-relative rather than midnight-relative is the smallest change that supports both today's midnight-ending desks and new overnight ones. See "Java Time Modelling" below. |
| PostgreSQL | 16 (RDS, unchanged) | `date`/`time` columns for business date, calendar date, and day-start anchor | Already the production database. No new column type is needed — see "Persistence" below for why the tempting range/generated-column options are wrong-shaped here. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `net.jqwik:jqwik` | **1.10.1** | Property-based construction of midnight-boundary test fixtures | Add as `testImplementation` only. Runs as an additional JUnit Platform test engine alongside Jupiter — existing tests are untouched, no production dependency. Fit for exactly what BDAY-06 asks for: properties like "for any anchor and any `(start, end)` with `end` earlier-or-equal to `start`, `durationMinutes` is in `(0, 1440]`" or "`businessDate` is always `date` or `date.minusDays(1)`, never further" — generated across the anchor/start/end space rather than hand-enumerated. Project is in maintenance mode (stable API, security/JUnit-platform updates only, no roadmap churn) — a low-risk, test-scope-only addition. |

### Development Tools (already present, reused not added)

| Tool | Purpose | Notes |
|------|---------|-------|
| `PostgresBackedTest` (Testcontainers 1.21.4, already pinned) | Real-Postgres verification of the new `business_date`/`day_start_time` columns, any CHECK constraint, and `ddl-auto=validate` | This is exactly the tool v1.3's `V39` incident (`CHAR(7)` vs `varchar(7)`) proved necessary. A new column on `timeslot`/`desk` plus a CHECK constraint (see below) is precisely the class of defect `MigrationEntityConsistencyTest` + `PostgresBackedTest` exist to catch — reuse both, do not build a parallel check. |
| AssertJ (via `spring-boot-starter-test`, already present) | Assertions on `LocalTime`/`LocalDate`/`int` minute values in the generalized `DayWindow` tests | `.isEqualByComparingTo`, `.isBetween` cover everything the new anchor-relative arithmetic needs. No new assertion library required. |

## Java Time Modelling

**The question:** minutes-since-day-start `int`, `Duration` offsets, half-open `[start,
start+duration)` pairs, or `LocalDateTime` pairs?

**Answer: keep the existing anchor-relative `int` minute-of-day representation for
same-business-day interval arithmetic; reach for `LocalDateTime` only at the two seams where a
second calendar date genuinely enters the picture.**

### Why `int` minutes-since-anchor, not `Duration` or a wrapper type

`DayWindow` already represents every interval as `[startMinute, endMinute)` in `int` minutes,
with the one deliberate special case that `LocalTime.MIDNIGHT` in an **end** position means 1440,
not 0 (`DayWindow.java:56-62`). The generalization this milestone needs is small and mechanical:

- Today, `startMinute`/`endMinute` are implicitly anchored at `00:00`.
- Add an anchor parameter: `startMinute(LocalTime time, LocalTime anchor)` and
  `endMinute(LocalTime time, LocalTime anchor)`, returning minutes-since-anchor in `[0, 1440]`
  by subtracting the anchor's own minute-of-day and wrapping mod 1440.
- `durationMinutes` currently throws when `endMinute - startMinute <= 0` (`DayWindow.java:73-82`)
  — today that condition means "not a valid same-day interval." Once desks can have shift
  *templates* whose end clock is earlier than their start clock (an overnight envelope), that
  same condition instead means "this interval crosses the anchor's day boundary," and the fix is
  `minutes = ((endRelative - startRelative - 1 + 1440) % 1440) + 1` (equivalently: add 1440 to
  `endRelative` when it is `<= startRelative` before subtracting) — still throwing only on a
  genuinely malformed interval (e.g. `start == end`), not on every overnight one.

This keeps the half-open `[start, end)` convention, keeps every existing `DayWindow` caller
working unchanged for desks at the default `00:00` anchor (anchor-relative arithmetic at anchor
`00:00` is byte-identical to today's midnight-relative arithmetic), and keeps the arithmetic as
plain `int` comparisons — which is what makes `overlaps`, `contains`, and `startsBefore` cheap
and branch-free inside a solver's incremental score calculation, called on every move.

`Duration` is the wrong storage/comparison type here: `Duration.between(LocalTime, LocalTime)`
still requires you to have already decided which of the two times comes "first" across the
midnight boundary — it doesn't solve the ordering problem, it just relocates it, and it adds
boxing/allocation to code paths that run millions of times per solve. Reserve `Duration` for
*output* values (e.g. `ShiftTemplate.getNetHours` already returns a `BigDecimal` of hours derived
from `durationMinutes` — that pattern is right and unaffected).

A bespoke `@Embeddable` "BusinessTimeWindow" wrapper type was considered and is not recommended:
it would require touching all 112 `DayWindow` call sites to unwrap/wrap instead of the smaller
"add an anchor parameter" change, and it adds an indirection layer for behavior the static
`DayWindow` methods already provide. See "What NOT to Add" for the fuller argument.

### Where `LocalDateTime` pairs are the right tool — and only there

Two seams in this milestone genuinely span two different `LocalDate` values, and pretending
otherwise with time-of-day-only arithmetic would be the same mistake `DayWindow`'s own javadoc
already calls out ("that is not a boundary problem but a data-model one," `DayWindow.java:32-37`,
written about exactly this):

1. **Timeslot generation for an overnight envelope.** A `23:00–07:00` shift, generated as
   discrete timeslots, produces rows whose `date` (calendar date, for display/export) genuinely
   differs before and after midnight, even though they share one `business_date`. The generation
   loop needs `LocalDateTime` (or an explicit `(LocalDate, LocalTime)` pair walked forward with a
   date rollover) to know *which* calendar date each generated timeslot row gets — this is a
   sequencing/generation concern, not an interval-membership one, and `TimeslotGeneratorService`
   is the one place this milestone should introduce `LocalDateTime` combination logic.
2. **Minimum-rest checking between two shifts that may span different business dates.** "Does
   shift A's actual end instant fall within N hours of shift B's actual start instant" is a
   question about two real points in time, potentially on different calendar dates — this needs
   `LocalDateTime` (`LocalDate.atTime(LocalTime)`, composed from the timeslot's calendar `date`
   and `start`/`end` `LocalTime`, with anchor-aware day-rollover for an end time earlier than its
   start) before a plain `Duration.between(...)` comparison is meaningful. Do not attempt this as
   anchor-relative-minute arithmetic; that representation deliberately throws away which calendar
   day a time falls on, which is exactly the information minimum-rest needs.

Everywhere else — envelope containment, break-band legality, grid alignment, net-hours, a single
timeslot's own start/end — stays same-business-day interval arithmetic in anchor-relative `int`
minutes, unchanged from today's shape.

## Persistence

**The question:** column types for business date vs. calendar date, and day-start time-of-day;
is there an idiomatic Postgres construct worth reaching for.

**Answer: plain `date` + `date` + `time`, no range type, no generated column, one defensive CHECK
constraint.**

### Columns

| Column | Type | Notes |
|--------|------|-------|
| `desk.day_start_time` | `TIME NOT NULL DEFAULT '00:00:00'` | Same shape as every other time-of-day column in this schema (`start_time TIME NOT NULL`, `V1__initial_schema.sql:151`). `DEFAULT '00:00:00'` makes every existing desk's anchor explicit at `00:00` with zero migration-time behaviour change — this is the "a desk that has never set one defaults to `00:00` and behaves exactly as it does today" requirement, satisfied by the column default itself, not application code. |
| `timeslot.date` | `DATE NOT NULL` (unchanged) | Stays the real calendar date the slot falls on — needed for display, export, and the actual wall-clock day something happened. Do not repurpose this column; add a second one. |
| `timeslot.business_date` | `DATE NOT NULL` (new) | The day the enclosing shift/slot *started* on. Populated at generation time (`TimeslotGeneratorService`), not computed lazily — see the Timefold section for why a stored column, not a derived getter, is the right call here regardless of the persistence question. |

Apply the same `date` + `business_date` pair to `agent_shift_assignment` if/where it stores its
own date rather than deriving one from its `Timeslot`/date FK — the roadmapper should confirm
which rows in the v1.5 requirement's "12 `timeslot.getDate()` joins" actually need a sibling
`business_date` column versus which can derive it via a join to `Timeslot`.

### Why not a generated column

Postgres `GENERATED ALWAYS AS (...) STORED` cannot reference another table — and
`business_date`'s derivation (`date` compared against `desk.day_start_time`) needs the *desk's*
anchor, which lives on a different row. This isn't a stylistic preference against generated
columns; it's a hard Postgres limitation that rules the option out mechanically. Worth stating
explicitly in this document so a phase planner doesn't rediscover the dead end mid-implementation
— `business_date` has to be computed in application code at write time (exactly as scoped:
"populated at generation") and stored as an ordinary column.

### Why not `daterange`/`tstzrange` or an exclusion constraint

Both were considered and rejected:

- **`daterange`** models a *continuous span* (a leave request spanning several days). Business
  date is a scalar tag on one row ("which business day does this timeslot belong to"), not a
  range — using a range type for a single value is the wrong shape and would need unwrapping
  (`lower(daterange)`) everywhere a plain `date` comparison would otherwise do.
- **`tstzrange`** additionally drags in a timezone dimension. This milestone explicitly defers
  timezones ("stays the operator's job, done before load" — PROJECT.md, v1.5 "Deliberately
  deferred") — adopting a `tz`-aware column type here would silently reintroduce a concept the
  milestone scoped out, for a value (business date) that has no timezone component at all.
- **Exclusion constraints** (`EXCLUDE USING gist (...)` with `btree_gist`) are the idiomatic
  Postgres way to prevent overlapping ranges at the database layer, and are genuinely tempting for
  "no two shifts for the same agent may overlap." Reject them anyway: they would be a **second**
  overlap-checking implementation living in SQL, alongside `DayWindow.overlaps()` in Java — the
  exact "two implementations that can drift" problem project rule P-19/D-02 exists to prevent,
  just relocated from Java into the schema instead of eliminated. Minimum rest and overlap
  legality stay Timefold `ConstraintStream` concerns, checked in the one place (`DayWindow` +
  `ScheduleConstraintProvider`) the project has already committed to.

### A defensive CHECK constraint worth adding

`business_date` is always either the same as `date` or exactly one day earlier — never more
distant, in either direction. That invariant is cheap to encode and catches a wrong-anchor bug
structurally rather than only in application tests:

```sql
ALTER TABLE timeslot
    ADD CONSTRAINT timeslot_business_date_bounds
    CHECK (business_date <= date AND date - business_date <= 1);
```

This is a guard, not the primary correctness mechanism — the primary mechanism is `DayWindow`'s
generalized arithmetic and the milestone's own guard tests, landed *before* re-anchoring, exactly
as PROJECT.md already commits to. The CHECK constraint is cheap insurance underneath that, in the
same spirit as `MigrationEntityConsistencyTest`: fail structurally, not by review attention.

### `ddl-auto=validate` implication

Every new column above must have an exactly-matching `@Column` mapping (type, nullability,
default) on the JPA entity or the application fails to boot under `ddl-auto=validate` — the same
failure mode V39 hit in Phase 14. No new library needed here; `PostgresBackedTest` and
`MigrationEntityConsistencyTest` already exist to catch this and should be exercised against every
migration this milestone adds (`business_date`, `day_start_time`, the CHECK constraint, and
whatever V53+ minimum-rest configuration columns the REST requirement needs).

## Timefold 1.16.0 Specifics

**The question:** join-key considerations when moving from a `LocalDate` field to a
derived/stored business-date field; stored column vs. computed getter for a
`@PlanningEntity`/problem-fact join key; anything version-specific about
`@ProblemFactProperty`/`@ProblemFactCollectionProperty`.

**Answer: stored column, not a computed getter — for performance and correctness reasons that
apply to Timefold's incremental (Bavet) score calculation generally, not something new in 1.16.0
specifically, but worth stating precisely because getting it wrong here is a silent-wrong-schedule
risk, exactly the failure mode this milestone's own SOLV requirement calls out.**

- **Use a stored `business_date` field, joined with the same `Joiners.equal(...)` pattern the
  existing 12 `getDate()` joins already use** — e.g. `Joiners.equal(Timeslot::getBusinessDate)`
  replacing `Joiners.equal(Timeslot::getDate)`. This is plain `Joiners.equal` on a `LocalDate`
  getter, available since long before 1.16.0; nothing here needs a newer Timefold release.
- **Do not compute `businessDate` lazily inside a `@PlanningEntity` getter from `desk.dayStart`
  and `date`.** Two independent reasons, both about how Bavet's incremental calculation works,
  not about API surface:
  1. **Performance.** A join-key mapping function is re-evaluated on every incrementally-scored
     move during local search — it is not memoized by the constraint stream engine. A getter that
     re-derives the business date (which itself may require reaching the desk's `dayStartTime`)
     on every call adds real per-move cost across a solve that already runs thousands of moves;
     a stored field is one array/field read.
  2. **Correctness.** Timefold's incremental score calculation assumes problem-fact-derived state
     is stable for the duration of a solve. If the derivation reads a mutable path (e.g. a `Desk`
     reference that could theoretically be reloaded or whose `dayStartTime` changes underneath an
     in-flight solve), the join key can disagree with itself between the moment a tuple was
     inserted into the constraint stream's working memory and the moment it's re-evaluated —
     producing a `0hard` schedule that is not actually legal, the exact class of defect this
     codebase has already hit once (`non_working_day_seat_weight`, V52, "The schedule was
     feasible on the score and illegal in fact"). Compute `businessDate` once, at generation time,
     outside the solve, and treat it as an ordinary immutable field from the solver's point of
     view — same discipline as every other problem-fact field already on `Timeslot`.
- **`@ProblemFactProperty`/`@ProblemFactCollectionProperty`:** no derived-key-specific behaviour
  differs here. `Desk.dayStartTime` is itself a plain immutable-during-solve problem fact, exposed
  however desks already are today (directly or via the existing problem-fact collection) — no new
  annotation usage pattern is needed; the only rule is the same one above, restated: whatever is
  exposed as a problem fact must not have fields that change mid-solve, and `businessDate`
  qualifies as such a field only if it's computed once, before solving starts, and stored.
- **Minimum rest (hard constraint) needs no new Timefold API.** It is a plain hard
  `ConstraintStream` join between an agent's consecutive shift assignments (ordered by
  business/calendar date), penalizing when the gap between one shift's end instant and the next
  shift's start instant is under the desk's configured minimum — the same shape as every existing
  hard constraint in `ScheduleConstraintProvider`, using `LocalDateTime` arithmetic (see the Java
  Time Modelling section) inside the constraint body, not a new join primitive.
- **Reuse the existing static-scan guard pattern for the "12 joins moved" requirement.** This
  codebase already has two precedents for "a set of code locations must all agree, verified
  structurally rather than by review" — `USHF-05`'s write-path table and
  `MigrationEntityConsistencyTest`. The SOLV requirement's "a test that fails if any constraint
  joins calendar date where business date is meant" should follow that same pattern (a
  reflection/source-scan test enumerating the join sites), not a new testing library.

## Testing Libraries

**The question:** what's already available or worth adding for constructed midnight-boundary
scenarios.

| Already available, reuse | Add | Do not add |
|---|---|---|
| JUnit 5 (Jupiter) | `net.jqwik:jqwik:1.10.1` (test-scope) | junit-quickcheck |
| AssertJ | — | ApprovalTests.java |
| Testcontainers 1.21.4 + `PostgresBackedTest` | — | — |
| `timefold-solver-test` (already a `testImplementation`, `build.gradle:61`) | — | — |

- **jqwik 1.10.1** (verified current) is the right fit for BDAY-06's constructed-scenario
  requirement specifically because it generates the *edge* cases (anchor at `23:00`, a shift
  ending exactly at the anchor, an envelope one minute short of 24h) that a hand-written table of
  examples tends to under-sample — which is precisely the gap v1.4's post-mortem identified ("no
  desk has a 23:00–00:00 slot, and no break band touches an envelope edge"). It runs as an
  additional JUnit Platform engine alongside Jupiter with no change to existing tests, and its
  current maintenance-mode status is a feature here, not a risk: stable API, no churn to track
  across a solver-pinned codebase that already deliberately avoids version churn (Timefold pinned
  at 1.16.0 for the same reason).
- **`MidnightWindowSeamTest`** (already in the codebase, `src/test/java/com/wfm/service/`) is the
  right home to extend, not replace — it already isolates `DayWindow`-dependent predicates from
  Spring context/database, which is exactly where jqwik `@Property` methods belong (fast,
  no-I/O). Generalizing its fixtures from a fixed `MONDAY`/`OPEN 08:00`/`CLOSE MIDNIGHT` triple to
  jqwik-generated anchors and envelopes is the natural next step for this test class.
- **`PostgresBackedTest`** should be exercised for the new `business_date` column, the CHECK
  constraint, and any `day_start_time` migration — this is exactly its purpose and it already
  exists; no new integration-test infrastructure is needed.
- **Do not add ApprovalTests.java** (current: 31.0.0) despite it being current and well-fitted to
  "verify a whole computed schedule matches a snapshot." See "What NOT to Add" below — this
  milestone's own retrospective diagnosed golden-file fragility as the reason v1.4 was cancelled,
  and a formal approval-testing library would institutionalize the pattern the retrospective
  explicitly moved away from.
- **Do not add junit-quickcheck.** It bridges through JUnit 4 (`com.pholser:junit-quickcheck` is a
  JUnit 4 `Theories`-style runner with a JUnit 5 adapter bolted on), has a smaller and less active
  maintenance footprint than jqwik, and offers no capability jqwik lacks for this codebase's pure
  JUnit 5 / Jupiter test suite. There is no scenario in this milestone where picking it over jqwik
  is the better call.

## What NOT to Add

| Avoid | Why | Use Instead |
|-------|-----|--------------|
| `org.threeten.extra:threeten-extra` (`Interval`, `LocalDateRange`) | Looks purpose-built for "business-day interval" but is the wrong shape twice over: `Interval` is `Instant`-based and needs a timezone to become concrete (this milestone explicitly defers timezones), and `LocalDateRange` is date-only with no time-of-day component. Adopting it means converting to/from it at every `DayWindow` call site for no arithmetic that anchor-relative `int` minutes doesn't already provide, and adds a dependency to justify against P-19/D-02's "one implementation, not two that can drift." | Generalize `DayWindow` with an anchor parameter (see Java Time Modelling) |
| `ZonedDateTime`/`ZoneId`/any timezone-aware type, anywhere in the new business-date path | Timezone handling is an explicit v1.5 non-goal ("stays the operator's job, done before load" — the Phil-US PHT/Pacific gap is deliberately not this milestone's problem). Introducing a zone-aware type into `DayWindow`, `Timeslot`, or `Desk` would silently couple business-date math to a concept the milestone scoped out, and would touch all 112 `DayWindow` references for no in-scope requirement. | Plain `LocalDate`/`LocalTime`, as today |
| Postgres `tstzrange`/`daterange` columns | Wrong shape for a scalar per-row tag (business date is not a span); `tstzrange` additionally drags in timezone semantics that are out of scope. | Plain `DATE` columns, one defensive CHECK constraint |
| Postgres `EXCLUDE USING gist` overlap constraints (`btree_gist`) | A second overlap-checking implementation living in SQL, alongside `DayWindow.overlaps()` in Java — exactly the "two implementations that can drift" problem P-19/D-02 exists to prevent, just relocated into the schema. | `ScheduleConstraintProvider` hard constraints, checked once, in Java, via `DayWindow` |
| A Postgres generated column for `business_date` | Mechanically impossible here, not just undesirable: `GENERATED ALWAYS AS` cannot reference another table's row (`desk.day_start_time`), which the derivation needs. | Compute in application code at generation time, store as an ordinary column (matches the milestone's own scoping) |
| A custom `@Embeddable`/value-object wrapper for a time interval (e.g. `BusinessTimeWindow`) | Adds an indirection layer for behaviour the static `DayWindow` methods already provide, and would require touching all 112 existing call sites to unwrap/wrap instead of the smaller "add an anchor parameter" change. | Generalized `DayWindow` static methods, unchanged calling convention |
| `java.time.Duration` as the *stored/compared* interval representation | `Duration.between(LocalTime, LocalTime)` still requires deciding which time comes first across the midnight boundary before it can be computed correctly — it relocates the ordering problem rather than solving it, and adds boxing/allocation on hot solver code paths. | `int` minute-of-day arithmetic in `DayWindow`; `Duration`/`BigDecimal` only as an *output* (e.g. `getNetHours`), as today |
| ApprovalTests.java (or any snapshot/golden-file testing library) for schedule-level regression coverage | This milestone's own retrospective is a golden-file-fragility post-mortem: v1.4's BDAY-06 fixture sourced from four live desks and was "over-sensitive... any scoring change anywhere shifts the golden bytes with no diagnostic signal." Adding a formal approval-testing library would institutionalize exactly the pattern PROJECT.md says v1.5 is inverting ("correctness is proven on constructed cases... live data is demoted to a drift guard"). | Constructed jqwik/AssertJ property and example tests for correctness; a small, explicitly-named live-desk comparison (Phil-US, 48 agents) for drift only, using plain AssertJ comparison, not a snapshot library |
| junit-quickcheck | JUnit-4-bridged, smaller ecosystem footprint than jqwik, no capability advantage for a pure JUnit 5 suite. | jqwik 1.10.1 |
| Upgrading Timefold past 1.16.0 | Explicit standing project decision, restated here defensively: `ScoreAnalysis` moves to a paid tier in Timefold 2.0, and nothing in this milestone (business-date joins, hard minimum rest) needs the `Neighborhoods` API (1.31.0+) or any other post-1.16 feature. | Stay on 1.16.0 — `Joiners.equal`, `AbstractMove.doMoveOnGenuineVariables`, everything this milestone needs is already available |

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| `net.jqwik:jqwik:1.10.1` | JUnit Platform (already pulled in transitively via `spring-boot-starter-test` → `junit-platform-launcher`), Gradle 8.x | Runs as an independent JUnit Platform engine; no version coupling to Jupiter 5.x version already in use. Test-scope only — zero production classpath impact. |
| Generalized `DayWindow` (anchor param) | Every existing caller at the default `00:00` anchor | Anchor-relative arithmetic at anchor `00:00` is arithmetically identical to today's midnight-relative arithmetic — existing 112 call sites at the default anchor need no behaviour change, only (where relevant) an explicit anchor argument threaded through from `Desk.dayStartTime`. |
| `business_date DATE` column + `ddl-auto=validate` | Flyway migration V53+ | Must be added via both a new migration and a matching `@Column` on the entity in the same change, per existing `MigrationEntityConsistencyTest` guard — this is process, not a version number, but the failure mode (V39) was real and specifically about this exact mismatch class. |

## Sources

- **Direct codebase inspection (HIGH confidence, primary source for this milestone):**
  `src/main/java/com/wfm/util/DayWindow.java`, `src/main/java/com/wfm/model/Timeslot.java`,
  `src/test/java/com/wfm/service/MidnightWindowSeamTest.java`, `build.gradle`,
  `src/main/resources/db/migration/V1__initial_schema.sql`,
  `src/main/resources/db/migration/V52__add_non_working_day_seat_weight.sql`,
  `.planning/PROJECT.md` (v1.5 scope and constraints), `.planning/codebase/STACK.md`.
- **jqwik 1.10.1, maintenance-mode status** — WebSearch against jqwik.net release notes and user
  guide (MEDIUM-HIGH confidence, official project sources).
- **ApprovalTests.java 31.0.0 current release** — WebSearch against Maven Central / GitHub
  (MEDIUM confidence, package-registry source, version only — the rejection rationale is
  project-specific, not a claim about the library's quality).
- **Timefold `Joiners`/constraint-stream join mechanics and problem-fact immutability
  requirements** — general Timefold/OptaPlanner architecture knowledge (constraint-stream
  incremental calculation semantics have been stable since long before 1.16.0); no
  version-specific 1.16.0 release-note change was found bearing on derived join keys, which is
  itself informative — this is ordinary constraint-stream usage, not a 1.16.0-specific concern.
- Postgres generated-column cross-table limitation, `daterange`/`tstzrange`/`EXCLUDE USING gist`
  semantics — standard PostgreSQL 16 documentation knowledge, not independently re-verified via
  WebSearch this session (LOW-risk claim: these are long-stable, version-independent Postgres
  features, not recent additions).

---
*Stack research for: overnight shifts and business-date modelling on an existing Spring Boot +
Timefold 1.16.0 + PostgreSQL 16 scheduling service*
*Researched: 2026-09-30*
