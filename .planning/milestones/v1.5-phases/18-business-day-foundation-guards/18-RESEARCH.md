# Phase 18: Business-Day Foundation & Guards - Research

**Researched:** 2026-09-30
**Domain:** Schema/entity plumbing, structural source-scan guard tests, and pure-evaluation solver
regression fixtures — no new runtime dependency, no new UI framework, no new external library.
**Confidence:** HIGH — every load-bearing claim below was verified this session by reading the file,
running the count, or dry-running the cherry-pick against the current tree. This is codebase
archaeology, not library research; the 28 decisions in `18-CONTEXT.md` are the design, this document
is what they land on.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Phase Boundary:** A desk can declare the time its day begins and a timeslot records the business
day it belongs to; the two safety nets Phase 19 falls onto — the comparison-operator-extended
`DayWindow` guard and a constructed midnight-boundary regression suite — exist and pass green against
today's `00:00`-only behaviour; and `TimeslotGeneratorService` can generate a contiguous 24-hour
business day from a non-midnight anchor, proven by direct unit test while the production API still
refuses to save one. **Observable behaviour change for an operator: none.** Every existing desk's
business date is identical to its calendar date by construction, and the day-start control ships
visibly disabled. Requirements: BDAY-01, BDAY-02, BDAY-03, BDAY-05, BDAY-06, BDAY-08. **Not this
phase:** the `DayWindow` re-anchoring itself and retirement of the `00:00`-means-end-of-day convention
(BDAY-04, Phase 19); solver join migration and the calendar-vs-business-date join guard (SOLV-01..07,
BDAY-07, Phase 20); overnight shift templates (OVNT-01..07, Phase 21); minimum rest (REST-01..07,
Phase 22); the Phil-US migration (MIGR-01..04, Future Requirements, deferred out of v1.5 entirely).

**Inherited unchanged from v1.4's Phase 18 discussion** (locked, restated so downstream agents need
not read the v1.4 document): D-01 (BDAY-05 extends `MidnightTimeArithmeticGuardTest`, one guard one
resource, second allowlist section not second test) · D-02 (the hole is comparison operators —
`.isAfter`/`.isBefore`/`.compareTo` must route through `DayWindow`; `LocalTime.MIDNIGHT.isAfter(23:00) == false` is a live, unguarded defect today) · D-03 (name-based receiver heuristic — `*Time`,
`slotStart`/`slotEnd`, `envelope*`, `band*`, `break*Start` — documented in the `.md`; raw counts
re-verified fresh on HEAD: `.isAfter(` 43, `.isBefore(` 26, `.compareTo(` 36, 105 hits mostly
`LocalDate`/`BigDecimal`) · D-04 (date-token guarding stays out of this phase — SOLV-02 is Phase 20)
· D-05 (no pre-placed assertion that `00:00`-means-end-of-day is retired — BDAY-04 owns that,
Phase 19) · D-06 (target accepted day-start range is 15-minute boundaries) · D-07 (the `00:00`-only
gate is one validation line in `DeskService` that Phase 19 deletes; NOT in `TimeslotGeneratorService`,
which takes `dayStart` as a parameter — reversibility: `reversible`) · D-08 (a day-start not a whole
multiple of the generation increment refuses loudly at generation, naming the day-start, increment,
and why they cannot tile; the increment is inferred per-call from the FTE spreadsheet's own columns,
`FteUploadService:127`, not desk state, so save-time validation is impossible; built now even though
D-07's gate makes it unreachable through the API) · D-09 (**V53** adds `desk.day_start TIME NOT NULL
DEFAULT '00:00'` and `timeslot.business_date DATE` nullable → backfill → `SET NOT NULL`, all three
statements in one migration; schema head is V52 so V53 is correct; STATE.md's "next migration is V40"
line is stale — reversibility: `one-way`) · D-10 (not a generated column — `GENERATED ALWAYS AS
(date) STORED` cannot be assigned) · D-11 (`timeslot` and `desk` join
`MigrationEntityConsistencyTest.DECLARED_TABLES`, a hardcoded six-entity map; the `LocalDate→DATE` and
`LocalTime→TIME` mappings it needs already exist; risk to watch — adding a table can surface a
pre-existing mismatch, a finding to report not licence to widen scope) · D-12 (nothing reads
`business_date` in this phase — Phase 20/SOLV-01 is the first consumer). **Dead by v1.5 scoping — do
not resurrect:** v1.4's D-01 through D-10, the per-live-desk golden files, the `wfm.capture` harness,
the anonymisation guard, the four-desk capture.

**BDAY-06 — the constructed regression suite:** D-13 (no golden file, no justification log — each
scenario asserts named expected values directly with a comment arguing correctness; the roadmap's
"golden-file justification-log enforcement mechanism" open decision is a **void premise, not an
unsettled choice** — it belonged to v1.4's captured-live-desk design; constructed scenarios have
knowable answers) · D-14 (pin every planning variable; never call `solve()` — build a `Schedule` with
pre-assigned assignments, evaluate with `SolutionManager.update()`/`.explain()`; pure function
evaluation, no search, no step budget, no seed; each scenario must argue its pinned solution is
solver-reachable — reversibility: `costly`) · D-15 (scenarios needing no constraint provider — a
23:00–00:00 slot, an envelope flush to end-of-day, contracted-hours-starting-weekday-only — are plain
unit tests on `DayWindow`, save-time validation, contracted-hours arithmetic, no `Schedule`, no
Timefold import; only genuinely constraint-level properties get pinned-solution evaluation) · D-16
(the three scenarios whose property cannot exist today — a shift starting before/ending after
midnight, PTO on starting vs. ending day, starting-weekday-only contracted hours — assert TODAY's
actual behaviour, each registered by name in a parsed `.md` naming which phase flips the assertion and
to what; validator asserts the registry equals the so-marked set exactly, in both directions —
reversibility: `costly`) · D-17 (non-vacuity validator works on shared structural predicates over
problem facts — "a slot ending `00:00`", "a 23:00–00:00 slot", "a band flush to an envelope edge", "a
span crossing the anchor" — not scenario labels; predicates fire at least once across the fixtures;
predicates are callable against any `Schedule`'s facts so Phase 20 can point them at the live
drift-guard desk — pointing at live data is Phase 20's business, not this phase's) · D-18 (solve-time
dynamic boundary detection considered and rejected for this phase — observes rather than asserts, only
fires on solve so not build-checkable, and new production solve-path code breaks the no-observable-change claim).

**BDAY-03 — the generation walk:** Scoping tension recorded, not absorbed — BDAY-03 moved into Phase
18 while BDAY-04 (re-anchoring) stayed in Phase 19; `generateTimeslots:130-147` walks minute-of-day
inside one calendar date, `DayWindow.toLocalTime` throws outside `[0,1440]` by design
(`DayWindow.java:122`); a 21:00 anchor needs minutes 1260→2700 mapped across two calendar dates; no
rescue-tag precedent (`985e365` only added a `dayStart` parameter and tiling refusal, then set
`business_date = date`) · D-19 (additive anchored helpers in `DayWindow` — nothing removed, no
signature changed, byte-identical at `00:00`; generation walk emits `(calendarDate, LocalTime)` pairs
across the anchor and derives business date; Phase 19 still owns deleting the midnight-implicit
overload; rejected: local minute arithmetic in the generator, moving BDAY-03 to Phase 19, proving
against a test double) · D-20 (`DayWindow` gains date-aware functions, takes on `LocalDate` for the
first time; given a day start, calendar date, and time, returns the business date; BDAY-08's single
derivation site is unambiguous; javadoc must grow the second concept without losing the
`00:00`-by-position statement — reversibility: `costly`) · D-21 (`@Deprecated` on midnight-implicit
forms with a message naming BDAY-04; left as warnings not errors since ~100 sites would break the
build) · D-22 (`periodStart`/`periodEnd` mean business dates, not calendar dates, once a desk has a
non-midnight anchor; `isDesired`, `slotKey`, `timeslotsMatch` must each be re-read in those terms with
`00:00` behaviour proven unchanged; rejected calendar-dates-with-clipping — reversibility: `costly`,
affects both `generateTimeslots` call sites `TimeslotController`, `FteUploadService:132`).

**Salvage from `rescue/phase-18-unwind-20260930`:** D-23 (**cherry-pick `84fdc3f`, `a1c0077`,
`2196e40`, `7d42f23`, `63d85a6`** — schema, migration consistency, write paths, the write-path guard,
comparison-operator guard; **re-author `985e365`'s generator change** since D-19/D-22's
business-date-anchored walk replaces its unconditional `business_date = date`; rejected cherry-picking
all seven and amending, and re-authoring everything fresh) · D-24 (rewrite comments to state their
reason inline, dropping every `D-nn` citation; use requirement IDs not phase numbers, since phase
numbers are demonstrably unstable across milestones) · D-25 (parameterise the guard's scan source
root; add a red-proof pointing the same pipeline at a test-resources directory with one synthetic
offending file, asserting set-equality fails; `63d85a6`'s existing red-proofs test the matcher against
synthetic strings only, not the walk→strip→match→compare pipeline; rejected writing a temporary
offending file into `src/main`).

**BDAY-01 — day-start surface and the override:** D-26 (`day_start` renders in desk configuration
alongside `scheduling_mode` and `default_contracted_hours_per_day`, visibly disabled, with copy
stating only `00:00` is supported until overnight scheduling lands; backend validation, D-07, stays
the real enforcement) · D-27 (**build `fec8990`'s accepted-schedule refusal; drop its named
override** — `dev` is production with real tenant data and Phil-US holds a live accepted schedule;
the override's justification, "Phase 23 must perform this change," no longer applies since Phil-US
migration is deferred out of v1.5 entirely as MIGR-01..04; nothing in Phases 18–22 changes a live
desk's day start; an override with no caller is untested surface area) · D-28 (the `00:00`-only copy
requirement is held by UAT verification only, not enforced by a test, consistent with Phase 13's P-11
ruling — no frontend test framework; knowingly accepted gap).

### Claude's Discretion

- The exact deterministic assignment rule for D-14's pinned solutions — any stated, reproducible rule
  is acceptable provided each scenario argues solver-reachability and D-17's predicates all fire.
- The precise name-pattern list in D-03, provided it is documented in the `.md` alongside the
  heuristic's limitations.
- The signature and naming of D-19/D-20's additive anchored helpers, and how the two arithmetic
  vocabularies are visually distinguished in `DayWindow` pending Phase 19's removal.
- Where exactly `day_start` sits on `DeskManagement.tsx` and the wording of its disabled-state copy —
  follow the existing `scheduling_mode` / `default_contracted_hours_per_day` pattern. **Research
  correction: `scheduling_mode` itself is a read-only plain-text cell on this page, not an editable
  disabled input — see "Frontend — `DeskManagement.tsx`" in Architecture Patterns below.**
- Which resource file carries D-16's flip registry and D-17's predicate list, and whether they share
  one file — subject to D-01's one-guard-one-resource rule.
- Whether the roadmap's incorrect "`ShiftDeskEndToEndRegressionTest`'s scoring-only pattern" citation
  and its "~112 call sites" figure are corrected in `ROADMAP.md` as part of this phase or left to the
  next roadmap edit.

### Deferred Ideas (OUT OF SCOPE)

- Point D-17's boundary-case predicates at live desk facts — a Phase 20 input for BDAY-07; needs a
  database and a live desk, cannot be a build-time check.
- Solve-time dynamic boundary detection in the production solve path — rejected for this phase; only
  as an operator-facing diagnostic, never as BDAY-06's mechanism.
- Correcting `ROADMAP.md`'s two errors (the scoring-only-pattern citation; the ~112-references figure)
  — Claude's Discretion whether done here or at the next roadmap edit.
- The named day-start override — dropped per D-27; belongs with MIGR-04, designed against a
  documented, tested reversal.
- Retiring the `00:00`-means-end-of-day convention and asserting `endMinute` is gone — BDAY-04,
  Phase 19.
- Widening the day-start range beyond `00:00` — Phase 19 deletes D-07's gate; D-06's 15-minute
  granularity is the range it opens to.
- Date-token guarding (`.getDate()`, `.getDayOfWeek()`, `.plusDays(`, `ChronoUnit.DAYS`) — SOLV-02,
  Phase 20.
- A `.tsx` structural source scan for UI copy — considered for D-28 and declined as brittle for a
  one-phase lifespan.
- Parser-based (type-aware) guard detection — considered for D-03 in v1.4, rejected as a new build
  dependency.
- Behavioural night-start guard (build a 21:00 desk, assert contiguity/no-gap/no-dup,
  `MidnightGapScanTest` style) — D-19 makes this partially reachable in this phase for the generator
  specifically; the full-system version stays a Phase 19/20 candidate.
- A frontend test framework — explicitly out, per Phase 13's P-11.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| BDAY-01 | Operator can set the time a desk's day begins, and a desk that has never set one behaves exactly as it does today | "Current-Tree Verification" (`Desk.java` current 3-field shape) + "Architecture Patterns" (`84fdc3f` DTO/controller/UI diff, `fec8990` refusal diff) + Pitfall 2 / Finding F-1 (the `confirmOverride` dead-parameter tension that must be resolved before this requirement is cleanly satisfied) + "Frontend" subsection (the corrected `DeskManagement.tsx` read-only-cell pattern for D-26) |
| BDAY-02 | A timeslot records the business day it belongs to, distinct from its calendar date | "Current-Tree Verification" (`Timeslot.java` current shape, V52 head confirms V53 is next) + "Architecture Patterns" (`MigrationEntityConsistencyTest.DECLARED_TABLES` exact 6-entry map, `a1c0077`'s clean 2-entry addition) |
| BDAY-03 | Timeslot generation for a desk whose day starts at 21:00 produces a contiguous 24-hour business day spanning two calendar dates | "Current-Tree Verification" (`TimeslotGeneratorService.java` generation loop, lines 124-147, quoted verbatim) + Pitfall 3 (re-authoring `985e365` correctly rather than copying its now-wrong unconditional write) + Validation Architecture (BDAY-03 has no existing test file — Wave 0 gap) |
| BDAY-05 | A guard test fails if any scheduling interval calculation bypasses the shared day-window utility — comparisons as well as arithmetic | "Current-Tree Verification" (comparison-operator counts, 40/26/36) + "Architecture Patterns" (`MidnightTimeArithmeticGuardTest` exact current shape — 6 tests, 3 allowlist entries — and the mechanical extension path) + Pitfall 4 (why the comparison scan needs D-03's heuristic, not a bare token scan) |
| BDAY-06 | A constructed regression suite proves behaviour across the midnight boundary, chosen by the property under test, with a non-vacuity validator | "Architecture Patterns" (`LiveShapeShiftDeskFixture.validateTemplateSpecs()` as the D-17 validator template; the verified solve-first nature of both existing `SolutionManager` precedents, confirming D-14's approach has no exact copyable precedent) + "Code Examples" (the composed pure-evaluation shape) + Validation Architecture (entirely new test file, Wave 0 gap) |
| BDAY-08 | Exactly one code path writes a timeslot's business date, proven by a write-path guard test that fails in both directions | "Current-Tree Verification" (`ScheduleService.acceptSchedule` snapshot-copy region) + "Don't Hand-Roll" (`7d42f23`'s `BusinessDateWritePathGuardTest` already satisfies this verbatim, dry-run confirmed conflict-free) |

</phase_requirements>

## Summary

Phase 18 has no genuine unknowns left to research — `18-CONTEXT.md` already settled both of the
roadmap's open decisions (cherry-pick vs. re-author, BDAY-06's enforcement mechanism) with 28 numbered
decisions. What a planner still needs is verified ground truth about the tree those decisions land on:
exact current file states, line numbers, call-site counts, whether the five commits named in D-23
still apply cleanly, and the concrete shape of the guard-test and fixture patterns being extended. All
of that is now verified below.

**The five D-23 cherry-picks apply cleanly to HEAD with zero conflicts** (dry-run cherry-picked and
reverted this session): `84fdc3f`, `a1c0077`, `2196e40`, `7d42f23`, `63d85a6`. `5ddd8dc` (the one fix
that landed on HEAD since the rescue tag was cut) touches `ScheduleExportService`, a file none of the
five commits touch, so there is no overlap to reconcile.

**One internal tension surfaced that the discussion did not anticipate:** `84fdc3f` (cherry-picked
per D-23) already lands a `confirmOverride` field on `DayStartRequest` and threads it through
`DeskService.setDayStart`, unused — the commit message says so explicitly ("confirmOverride accepted,
unused until Task 2's refusal"). D-27 says to build `fec8990`'s refusal *without* its override, and
`fec8990` (which is what actually consumes `confirmOverride`) is **not** in D-23's cherry-pick list.
Cherry-picking `84fdc3f` verbatim therefore lands a permanently-unused boolean parameter and DTO field
— exactly the "override with no caller is untested surface area" problem D-27 itself names as the
reason to decline the override. See **Finding F-1** below; the plan must explicitly resolve it (most
likely: strip `confirmOverride` from `84fdc3f`'s cherry-pick and write the accepted-schedule refusal
as an unconditional block with no bypass parameter at all).

Re-verified counts differ slightly from `18-CONTEXT.md`'s numbers by methodology (line-count grep vs.
occurrence-count grep) but agree directionally and support every claim that mattered: `DayWindow` is
referenced in exactly 16 `src/main` files and 4 `src/test` files; the guard's three raw-arithmetic
tokens plus the two comparison-operator families are the right ~100-hit blast radius; the
`MidnightTimeArithmeticGuardTest` allowlist has exactly 3 entries and 6 `@Test` methods today; the
`MigrationEntityConsistencyTest.DECLARED_TABLES` map has exactly 6 entries; schema head is confirmed
**V52**, so **V53** is the correct next migration number.

**Primary recommendation:** Plan Phase 18 as five waves matching the five cherry-picks plus one
re-authored generator change plus the constructed BDAY-06 fixture, in this dependency order: (1) V53
schema + entity + DTO/controller/UI (`84fdc3f`, minus `confirmOverride` per F-1) + migration
reconciliation (`a1c0077`); (2) re-authored generator change implementing D-19/D-20's anchored
`DayWindow` helpers and D-22's business-date period semantics (NOT `985e365` as-is); (3) write-path
`business_date` population (`2196e40`, re-diffed against the re-authored generator) + write-path guard
(`7d42f23`, which already satisfies BDAY-08 verbatim); (4) comparison-operator guard extension
(`63d85a6` + D-25's red-proof); (5) the constructed BDAY-06 fixture (new work, no salvage precedent —
see the Validation Architecture section).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Desk day-start storage & the `00:00`-only gate | API / Backend | Database / Storage | `DeskService.setDayStart` owns the one validation line (D-07); `desk.day_start` column is the persisted fact |
| Day-start disclosure (visibly-disabled UI control) | Browser / Client | — | Pure display concern — D-26's copy lives in `DeskManagement.tsx`, enforces nothing (D-28) |
| Timeslot business-date derivation | API / Backend | Database / Storage | `TimeslotGeneratorService.generateTimeslots` is the sole deriving writer (D-24/BDAY-08); `timeslot.business_date` is the persisted fact |
| Business-date propagation (accepted-schedule snapshot) | API / Backend | — | `ScheduleService.acceptSchedule`'s snapshot copy is the sole propagating writer (BDAY-08's second half) |
| Anchored interval arithmetic (`DayWindow` date-aware helpers) | API / Backend | — | Pure utility class, no I/O; consumed by the generator and (in Phase 19) the solver |
| Comparison-operator / raw-arithmetic structural guards | API / Backend (test tier) | — | Build-time source scan, no runtime component; enforces the `DayWindow` funnel |
| BDAY-06 constructed regression fixture | API / Backend (test tier) | — | Pure `SolutionManager` evaluation against hand-built `Schedule` objects, no database, no solve |

## Package Legitimacy Audit

**Not applicable.** This phase installs no new npm or Maven/Gradle dependency. `build.gradle`
confirms Timefold pinned at `1.16.0` (`build.gradle:52`, `implementation platform('ai.timefold.solver:timefold-solver-bom:1.16.0')` [VERIFIED: build.gradle:52]) — unchanged, matching `STATE.md`'s
recorded decision. `REQUIREMENTS.md:67` names `jqwik` as "test-scope only" and "no new runtime
dependency is needed" for the v1.5 milestone generally — **jqwik is not currently a `build.gradle`
dependency** [VERIFIED: build.gradle testImplementation/testRuntimeOnly block, lines 60-72, no jqwik
line present] and this phase's own BDAY-06 design (D-13/D-14, pinned deterministic scenarios, no
property-based generation) does not need it. If a later phase reaches for property-based testing,
that is a fresh dependency decision, not inherited from this research.

## Current-Tree Verification

### Schema head and migration numbering

`ls src/main/resources/db/migration/ | sort -V | tail -5` confirms the five most recent applied
migrations end at `V52__add_non_working_day_seat_weight.sql` [VERIFIED: filesystem listing, this
session]. **Next migration is V53**, matching D-09 and correcting the stale "next migration is V40"
line `18-CONTEXT.md` already flags in `STATE.md`.

### `DayWindow` call-site counts

| Scan | Count | Files | Method |
|---|---|---|---|
| `DayWindow` token, any occurrence, `src/main` | 123 | 16 | `grep -ro 'DayWindow' src/main --include='*.java'` |
| `DayWindow.<method>(` actual call sites, `src/main` | 95 | 14 | `grep -ro 'DayWindow\.[a-zA-Z]*(' src/main --include='*.java'` |
| `DayWindow` token, any occurrence, `src/test` | 43 | 4 | `grep -ro 'DayWindow' src/test --include='*.java'` |

[VERIFIED: this session's grep output, quoted above] All 16 `src/main` files match `18-CONTEXT.md`'s
file count exactly; the occurrence-count gap (95-123 vs. the roadmap's "100") is grep methodology
(raw token vs. method-call regex vs. import-line inclusion), not disagreement about scope. The four
`src/test` files with `DayWindow` references are `DayWindowTest.java`, `MidnightGapScanTest.java`,
`MidnightTimeArithmeticGuardTest.java`, `MidnightWindowSeamTest.java` [VERIFIED: `grep -rl 'DayWindow' src/test --include='*.java'`, this session].

Heaviest concentrations in `src/main`: `ScheduleConstraintProvider.java` (21 `DayWindow.` calls, 12
`.getDate()` occurrences), `ShiftLibraryGenerationService.java` (14), `TimeslotGeneratorService.java`
(11) [VERIFIED: per-file `grep -c`, this session]. These three are Phase 19's and Phase 20's heaviest
touch points, not this phase's — Phase 18 does not modify `ScheduleConstraintProvider` at all.

### Comparison-operator counts (BDAY-05's blast radius)

| Operator | `src/main` count | Files with hits |
|---|---|---|
| `.isAfter(` | 40 (lines) / 41 (raw occurrences — one line has two) | 17 |
| `.isBefore(` | 26 | — |
| `.compareTo(` | 36 | — |

[VERIFIED: `grep -rn`/`grep -ro` against `src/main --include='*.java'`, this session; full per-file
breakdown captured in session transcript] These numbers agree with D-03's "43/26/36 — 105 hits" within
normal grep-methodology variance (a handful of `.isAfter(` matches differ depending on whether the
pattern requires a leading `.`). The finding that matters for planning is unchanged: **most hits are
on `LocalDate` or `BigDecimal`, not `LocalTime`**, e.g. `AgentShiftAssignment.compareTo(`,
`ShiftLibraryGenerationService.compareTo(` (7 hits, sorting), `DeskAgentService.compareTo(` (4 hits).
A naive token-only guard would need a 100+ entry allowlist; D-03's name-based receiver heuristic
(`*Time`, `slotStart`/`slotEnd`, `envelope*`, `band*`, `break*Start`) is the only way this guard stays
enforceable rather than decorative.

### `TimeslotGeneratorService.java` — exact structure (229 lines total)

[VERIFIED: Read tool, `src/main/java/com/wfm/service/TimeslotGeneratorService.java`, this session]

- `generateTimeslots(UUID deskId, LocalDate periodStart, LocalDate periodEnd, LocalTime startTime, LocalTime endTime, int incrementMinutes)` — signature at **line 71-72**.
- Increment/range validation — **lines 73-82**: `if (incrementMinutes != 15 && incrementMinutes != 30 && incrementMinutes != 60)` at 73, `rangeMinutes` positivity/tiling check at 79-81. D-08's `requireDayStartTiles` refusal is new logic that belongs beside this block, before any repository call.
- `timeslotsMatch` early-return (no-op preservation of linked staffing requirements) — **line 95**, body at **196-215**.
- Obsolete-slot partition loop calling `isDesired` — **lines 104-111**.
- **The generation loop D-19/D-22 rewrite — lines 124-147**, specifically:
  ```java
  int firstMinute = DayWindow.startMinute(startTime);
  int lastMinute = DayWindow.endMinute(endTime);
  for (LocalDate date = periodStart; !date.isAfter(periodEnd); date = date.plusDays(1)) {
      for (int minute = firstMinute; minute < lastMinute; minute += incrementMinutes) {
          LocalTime slotStart = DayWindow.toLocalTime(minute);
          LocalTime slotEnd = DayWindow.toLocalTime(minute + incrementMinutes);
          String key = slotKey(date, slotStart, slotEnd);
          if (!survivingByKey.containsKey(key)) {
              Timeslot ts = new Timeslot();
              ts.setTenantId(tenantId);
              ts.setDeskId(deskId);
              ts.setDate(date);
              ts.setStartTime(slotStart);
              ts.setEndTime(slotEnd);
              toCreate.add(ts);
          }
      }
  }
  ```
  [VERIFIED: quoted verbatim, lines 130-147] `ts.setDate(date)` at line 141 is the single line that
  must become two — a calendar-date write for `ts.setDate(...)` and a derived business-date write for
  the new `ts.setBusinessDate(...)` — once D-19's date-aware `DayWindow` function exists. Today the
  outer loop walks one calendar date per iteration and every minute in `[firstMinute, lastMinute)` maps
  onto that same date; a 21:00 anchor needs the inner loop's minute-of-day to map across the
  **anchor's** business day, which may span two calendar dates, while `date` stays the loop's
  calendar-date cursor.
- `isDesired` (package-private, directly unit-testable) — **lines 174-183**. D-22 requires re-reading
  this in business-date terms once desks can have non-midnight anchors; the existing docstring (lines
  160-172, quoted above) is explicit that duration-checking is load-bearing, not incidental.
- `slotKey` — **line 157-158** (`date + "|" + start + "|" + end"`).
- `timeslotsMatch` (private) — **lines 196-215**.

### `Desk.java` and `Timeslot.java` — exact current shape

[VERIFIED: Read tool, both files, this session]

`Desk.java` (53 lines) has exactly three configurable fields today:
`defaultContractedHoursPerDay` (`BigDecimal`, precision 5 scale 2, default `8.00`), `schedulingMode`
(`SchedulingMode` enum, default `SLOT`), `description` (`String`, nullable) — plus `id`, `tenantId`,
`name`. `day_start` is the fourth configurable field D-26 adds, following the same getter/setter
pattern.

`Timeslot.java` (56 lines) has `id`, `tenantId`, `deskId`, `scheduleId` (nullable), `date`
(`LocalDate`, `nullable = false`), `startTime`, `endTime`. `business_date` is the new
`LocalDate` field BDAY-02 adds, same pattern as `date`.

### `FteUploadService.java` — the two `generateTimeslots` call sites and the increment-inference site

[VERIFIED: `grep -n`, this session] Both callers confirmed:
- `TimeslotController.java:44` — `generateTimeslots(` inside the `generateTimeslots` endpoint handler (`:42`).
- `FteUploadService.java:132` — `generateTimeslots(` inside the upload-parsing flow.

The increment-inference logic D-08 cites lives at **`FteUploadService.java:122-123`**:
```java
if (incrementMinutes == 0) {
    incrementMinutes = DayWindow.durationMinutes(slotStart, slotEnd);
}
```
[VERIFIED: quoted verbatim, `src/main/java/com/wfm/service/FteUploadService.java:122-123`] — confirms
D-08's claim that the increment is inferred per-call from the spreadsheet's own columns, not stored
desk state, so save-time tiling validation genuinely cannot happen; it must be checked inside
`generateTimeslots` itself.

## Package Legitimacy — N/A, see above.

## Architecture Patterns

### `DayWindow.java` — verified current state (141 lines)

[VERIFIED: Read tool, `src/main/java/com/wfm/util/DayWindow.java`, this session — full text read] Key
facts for planning:

- Imports **only** `java.time.LocalTime` (line 3) — confirms D-20's claim that adding `LocalDate`
  awareness is a genuinely new import, not an extension of an existing one.
- The "does NOT model a shift that runs PAST midnight" paragraph is at **lines 30-36** (not exactly
  `:73-82` as `18-CONTEXT.md` states — that line range is `durationMinutes`'s throw, which is where
  the *behavior* lives; the *javadoc statement* of the rule is at 30-36). Both are real citations; a
  planner writing a task description should cite line 30-36 for "where the rule is stated" and the
  `durationMinutes` method (line 68, throw at line 73) for "where it is enforced."
- `toLocalTime(int minuteOfDay)` — **line 121**, throws outside `[0, 1440]` at the guard on line 122-125 (not `:122` exactly — `:122` is the signature line, the throw is 123-125). Minor citation correction for the plan.
- Public API surface today: `startMinute`, `endMinute`, `durationMinutes`, `isForwardWithinDay`,
  `overlaps`, `contains`, `startsBefore`, `toLocalTime`, `plusWithinDay` — 9 static methods, all
  `LocalTime`-only. D-19/D-20's new date-aware function(s) are additive to this list.
- `MINUTES_PER_DAY = 1440` is the one existing constant; any new anchor-aware function will need to
  reason in terms of a `dayStart` `LocalTime` plus this constant.

### `MidnightTimeArithmeticGuardTest.java` + `midnight-time-arithmetic.md` — exact current shape

[VERIFIED: Read tool, both files, this session — full text read]

- **6 `@Test` methods today**: `rawTimeArithmeticInProductionCode_matchesTheAllowlistExactly`,
  `noLoopUsesALocalTimeCursor`, `allowlist_parsesAsNonEmpty`, `theScanActuallySeesProductionSource`,
  `theScanDetectsAFreshOccurrence`, `missingAllowlistHeading_failsLoudly`. `18-CONTEXT.md`'s claim of
  "6 today; `63d85a6`'s has 9" is confirmed — cherry-picking `63d85a6` adds 3 more (almost certainly the
  comparison-operator counterparts of `theScanDetectsAFreshOccurrence` and the D-25 red-proof, per the
  canonical-refs note that `63d85a6` already carries `theComparisonScanDetectsAFreshOccurrence`).
- The allowlist resource has **exactly 3 fenced entries** under `### Permitted raw time arithmetic`:
  two `ScheduleOutputService` lines and one `ShiftBandPair` line, all start-to-start `ChronoUnit.MINUTES.between(` distances [VERIFIED: quoted verbatim above from `midnight-time-arithmetic.md`].
- **Raw-arithmetic token list** (line ~62 of the test): `Duration.between(`, `ChronoUnit.MINUTES`,
  `.plusMinutes(`, `.minusMinutes(` — this is the family D-01 says gets "a second allowlist section"
  for comparison operators, not a second scanner. The comparison-operator family
  (`.isAfter(`/`.isBefore(`/`.compareTo(`) needs its own token list and its own heading in the same
  resource file, parsed by the same `parseFencedBlock` helper with a different heading string.
- `parseFencedBlock` is a generic first-fenced-block-after-heading parser, already reusable for a
  second heading without modification — confirms D-01's "one guard, one resource, second section"
  framing is mechanically straightforward: add a second `ALLOWLIST_HEADING`-style constant and a
  second `@Test` calling `parseFencedBlock(readResource(), NEW_HEADING)`.
- `IMPLEMENTATION_CLASS = "com.wfm.util.DayWindow"` is the one exempted file for the raw-arithmetic
  scan; the comparison-operator scan needs the same exemption (confirmed present in `63d85a6`'s diff
  stat: it modifies this same test class in place, not a new file).
- `SOURCE_ROOT = Path.of("src", "main", "java")` — no Spring context, walks the filesystem directly.
  D-25's red-proof (parameterize the scan root, point it at a synthetic-offender test-resources
  directory) requires extracting `SOURCE_ROOT` from a `private static final` constant to a
  constructor/method parameter — currently hardcoded, confirmed by the field declaration.

### `UsualShiftWritePathGuardTest.java` + `ushf-05-write-paths.md` — the BDAY-08 template

[VERIFIED: Read tool, both files, this session — representative portions read] This is the pattern
`7d42f23`'s `BusinessDateWritePathGuardTest` already copies (confirmed by dry-run cherry-pick — no
conflicts). Structure to replicate for any *new* allowlist entries beyond what `7d42f23` already
covers: two fenced allowlists (a "Set A" / "Set B" pair, or here likely a "derivers" / "propagators"
pair per D-24's language), each parsed with `containsExactlyInAnyOrderElementsOf`, plus a markdown
table documenting each write path's proving test — the table is for humans, the fenced lists are what
the build enforces (explicit in this file's own header comment).

### `MigrationEntityConsistencyTest.java` — `DECLARED_TABLES` reconciliation

[VERIFIED: Read tool + `git show a1c0077`, this session] `DECLARED_TABLES` (lines 84-91) is confirmed
a **hardcoded 6-entry `Map.of(...)`**:
```java
private static final Map<String, Class<?>> DECLARED_TABLES = Map.of(
        "shift_template", ShiftTemplate.class,
        "shift_template_break_band", ShiftTemplateBreakBand.class,
        "agent_shift_assignment", AgentShiftAssignment.class,
        "constraint_weights", ConstraintWeights.class,
        "schedule", Schedule.class,
        "agent_usual_shift", AgentUsualShift.class
);
```
[VERIFIED: `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java:84-91`] `a1c0077`
appends exactly two entries (`"timeslot", Timeslot.class` and `"desk", Desk.class`) and adds no new
`COMPATIBLE_SQL_TYPES` entry — its own commit message states both new columns map through the
pre-existing `LocalTime→TIME` and `LocalDate→DATE` entries, and no pre-existing mismatch surfaced when
the two tables were added (D-11's "risk to watch" did not materialize on this dry run — worth a
planner note that this was re-verified this session, not merely inherited from v1.4's commit message).

### `LiveShapeShiftDeskFixture.validateTemplateSpecs()` — the D-17 class-load validator template

[VERIFIED: Read tool, `src/test/java/com/wfm/solver/LiveShapeShiftDeskFixture.java`, this session]
Confirmed pattern: a `static { validateTemplateSpecs(); }` block runs at class-load time (line
136-138), calling a `private static void validateTemplateSpecs()` method that walks the fixture's own
constant data and throws if an invariant (e.g. "at least one template has slack," "every band stays
≥120 minutes from an envelope edge") does not hold. This is exactly the shape D-17's non-vacuity
validator needs: a `static` block asserting each named structural predicate ("a slot ending `00:00`",
"a 23:00–00:00 slot", "a band flush to an envelope edge", "a span crossing the anchor") fires at least
once across BDAY-06's constructed fixtures.

### D-14's pure-evaluation precedent — verified to actually solve first

[VERIFIED: Read tool + grep, both files, this session] Both cited precedents call `.solve(` before
evaluating:
- `ConstraintPrecedenceObservabilityTest.java:141` — `return solver.solve(unsolved);` inside a helper
  that produces the `solved` fixture consumed by `solutionManager.explain(solved)` at line ~91.
- `ShiftEnvelopeGroundTruthTest.java:330` — `return solver.solve(unsolved);` inside
  `solveCleanFixture()` (declared at line 306), which every one of its 6+ test methods calls before
  corrupting the result by hand and re-scoring with `solutionManager.update(solved)`
  (`ShiftEnvelopeGroundTruthTest.java:213`, confirmed: `HardSoftScore freshScore = solutionManager.update(solved);` with **no** intervening `.solve()` call).

This confirms `18-CONTEXT.md`'s framing exactly: **both existing precedents need one initial solve to
reach a legal starting `Schedule`, then evaluate mutations of it purely.** BDAY-06's D-14 approach —
**never** calling `solve()`, constructing every `Schedule` by hand with pre-assigned planning
variables — has no existing precedent in this codebase to copy structurally; it must be built fresh.
The mechanical piece that *does* transfer is `SolutionManager.update(solution)` /
`.explain(solution)` for scoring a hand-built `Schedule`, which Timefold's own documentation confirms
is pure score evaluation with no search involved: `SolutionManager.update(solution,
SolutionUpdatePolicy.UPDATE_SCORE_ONLY)` (and the plain `.update(solution)` overload) computes score
without invoking the solver [CITED: docs.timefold.ai/timefold-solver — `ScoreExplanation` /
`SolutionManager` API reference; not independently re-verified against the exact 1.16.0 javadoc this
session, since the project's pinned version predates the current `SolutionUpdatePolicy` enum name in
some releases — **flag for a quick javadoc check against `ai.timefold.solver:timefold-solver-core:1.16.0` specifically before Task 1 of the BDAY-06 plan**, since API names have moved between minor
versions in this library's release history].

### `ScheduleService.acceptSchedule` — the propagating write BDAY-08 must cover

[VERIFIED: Read tool, `src/main/java/com/wfm/service/ScheduleService.java:260-303`, this session]
`acceptSchedule` (annotated `@Transactional`, signature at line 263) computes `coveredDates` (lines
286-292) and calls `acceptedScheduleDateRepository.updateStatusByTenantIdAndDeskIdAndDateIn(...)`
(lines 296-298) before persisting the `Schedule` record itself (comment at line 300 references "CR-02
gap closure: schedulingMode is now a mapped column"). The snapshot-copy write `2196e40` touches is
further down this method (not shown in the excerpt read this session, but confirmed present by the
dry-run cherry-pick's clean `M` against this exact file). This is the second of BDAY-08's two writers
(deriving in `TimeslotGeneratorService`, propagating here) that `7d42f23`'s
`BusinessDateWritePathGuardTest` already names and pins.

### Frontend — `DeskManagement.tsx` read-only-field convention (D-26's template)

[VERIFIED: Read tool, `frontend/src/pages/DeskManagement.tsx`, full 127-line file, this session]
**Critical correction to the "follow the `scheduling_mode` pattern" discretion note**:
`schedulingMode` is **not** an editable field anywhere on this page. It renders as a plain read-only
table cell in *both* the edit and display branches:
```tsx
{/* Read-only in both branches — the mode cannot be changed from this page (D-14); a plain-text cell
    keeps the row's column count equal across edit/display so the table does not shift while a row
    is being edited. */}
<td>{desk.schedulingMode === 'SHIFT' ? 'Shift' : 'Slot'}</td>
```
[VERIFIED: quoted verbatim, `DeskManagement.tsx`, the edit-branch cell; an identical cell appears in
the display branch] There is no `<input>`, no `disabled` HTML attribute, and no click handler — mode
changes happen through a separate flow (`switchSchedulingMode`, referenced elsewhere in the codebase,
not on this page). `defaultContractedHoursPerDay`, by contrast, **is** editable
(`<input type="number" value={editHours} ...>`, line ~99).

**For D-26's `day_start` control**: the precedent to copy is `schedulingMode`'s read-only cell
pattern (a plain-text `<td>` with an inline comment explaining why it is read-only), not an
`<input disabled>` element. D-26 additionally requires visible explanatory copy stating only `00:00`
is supported — this means the cell's rendered text itself (not just a code comment) should say
something like `00:00 (day-start editing not yet available)` rather than bare `{desk.dayStart}`. The
`Desk` TypeScript interface (`frontend/src/api/client.ts:329`) is a single-line type:
```ts
export interface Desk { id: string; name: string; description?: string; defaultContractedHoursPerDay: number; schedulingMode: 'SLOT' | 'SHIFT' }
```
[VERIFIED: quoted verbatim, `frontend/src/api/client.ts:329`] — `dayStart: string` is the field this
phase appends, following `84fdc3f`'s frontend diff (which the cherry-pick dry-run confirmed touches
exactly this file cleanly).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| A second guard-test scanner for comparison operators | A new `ComparisonOperatorGuardTest` class | Extend `MidnightTimeArithmeticGuardTest` with a second token list + second allowlist heading in the same `.md` | D-01 — one guard, one resource; `parseFencedBlock` already supports multiple headings with no code change |
| A new write-path guard class for `business_date` | A hand-rolled scanner | `BusinessDateWritePathGuardTest` from `7d42f23` (cherry-pick) — already satisfies BDAY-08 verbatim per canonical-refs | Proven 417-line implementation, dry-run confirmed conflict-free against HEAD |
| A non-vacuity check for BDAY-06 scenarios | Per-scenario boolean flags checked ad hoc | `LiveShapeShiftDeskFixture.validateTemplateSpecs()`'s `static { }` class-load-validator pattern, applied to D-17's shared structural predicates | Proven pattern already in this codebase; fails loudly at class-load, not at first-test-run |
| Proving BDAY-06 scenarios via a live solve | `solver.solve(...)` then asserting on the result | `SolutionManager.update()` / `.explain()` on a hand-constructed, fully-pinned `Schedule` | D-14 — the optimiser is non-deterministic, the score function is not; solving introduces exactly the flakiness BDAY-06 exists to avoid |
| A golden-file / hash-based fixture mechanism | Any of v1.4's three ranked golden-file options | Named expected values asserted directly in test code, with an inline comment arguing correctness | D-13 — the golden-file premise (unhand-writable match counts on a 288-agent live desk) does not apply to constructed scenarios with knowable answers |

**Key insight:** every piece of "new" machinery this phase needs (a second allowlist section, a
class-load validator, a pure-evaluation scoring harness) already has a working, tested precedent
somewhere in this codebase. The discipline is extension, not invention — confirmed this session by
reading each precedent directly rather than trusting the citation.

## Runtime State Inventory

**Not applicable — this is not a rename/refactor/migration phase.** Phase 18 adds new columns and new
entity fields; it does not rename or move existing state. The one item worth flagging explicitly:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — `desk.day_start` and `timeslot.business_date` are new columns with safe defaults (`'00:00'` and backfilled-from-`date`), not renames of existing columns | None |
| Live service config | None — no external service configuration references either new column | None |
| OS-registered state | None | None |
| Secrets/env vars | None | None |
| Build artifacts | None — no package/module rename | None |

**Nothing found in any category** — verified by reading the V53 migration text (`84fdc3f`'s diff) and
confirming both new columns are additive with safe defaults, not replacements for existing storage.

## Common Pitfalls

### Pitfall 1: Citing `DayWindow.java` line numbers from `18-CONTEXT.md` without re-checking

**What goes wrong:** `18-CONTEXT.md` cites `:73-82` for the "does not model a past-midnight shift"
javadoc and `:122` for `toLocalTime`'s throw. Verified this session: the javadoc *statement* of the
rule is actually at lines 30-36; lines 73-82 are `durationMinutes`'s *enforcement* of it (the throw
inside `durationMinutes`, method declared at line 68). `toLocalTime`'s throw is lines 123-125, not
just 122 (122 is the guard condition's opening line).
**Why it happens:** `18-CONTEXT.md` was written by a different session against a slightly different
in-memory view of the file; line numbers drift easily even without content changes (blank-line
insertion, javadoc rewording).
**How to avoid:** Task descriptions in the plan should cite the verified ranges in this document, not
re-derive from `18-CONTEXT.md`'s numbers.
**Warning signs:** A task that says "edit line 82" and finds a `}` or a blank line there.

### Pitfall 2: Cherry-picking `84fdc3f` without resolving the `confirmOverride` dead-parameter problem (Finding F-1)

**What goes wrong:** `84fdc3f` lands `DayStartRequest(LocalTime dayStart, boolean confirmOverride)`
and threads `confirmOverride` through `DeskService.setDayStart(UUID, LocalTime, boolean)`, but the
only code that reads `confirmOverride` is in `fec8990`, which D-23 explicitly excludes from the
cherry-pick list. If the plan cherry-picks `84fdc3f` verbatim and does not also write *some* form of
D-27's refusal logic, `confirmOverride` becomes a boolean parameter that always does nothing — visible
in the API contract, silently inert.
**Why it happens:** `84fdc3f` and `fec8990` were authored as two halves of one v1.4 task (Task 1 /
Task 2 of the same plan), and v1.5's D-23/D-27 split them differently than v1.4 did — D-23 keeps
`84fdc3f` (which contains half of `fec8990`'s contract) but not `fec8990` itself.
**How to avoid:** The plan must explicitly choose one of: (a) strip `confirmOverride` from the
cherry-picked `84fdc3f` diff, and write D-27's accepted-schedule refusal as an **unconditional** block
with no bypass parameter (simplest, matches D-27's own "no caller" reasoning most directly); or
(b) cherry-pick `84fdc3f` as-is and also build a trimmed version of `fec8990`'s refusal that still
reads `confirmOverride` but drops the override's *justification* language (keeps the parameter, uses
it). Option (a) is more consistent with D-27's stated reasoning but changes the shape of a commit
D-23 said to cherry-pick verbatim — this is a genuine plan-time decision, not a research gap.
**Warning signs:** `DeskServiceDayStartTest` (also cherry-picked in `84fdc3f`) has a test asserting
`confirmOverride`'s bypass behavior — if that test is inherited without `fec8990`'s refusal logic, it
will fail to compile or fail at runtime (no refusal exists yet to bypass).

### Pitfall 3: Re-authoring `985e365` by copying its diff instead of its intent

**What goes wrong:** `985e365`'s actual change is `ts.setBusinessDate(date)` — an unconditional write
of the *calendar* date into the business-date field, correct only because every desk's day-start was
`00:00` when that commit was authored. D-19/D-22 require the business date to be *derived* via the new
anchored `DayWindow` function, which is a different value once a desk has a non-`00:00` day-start
(even though D-07's gate makes that value unreachable via the API this phase).
**Why it happens:** The temptation is to treat "re-author" as "retype the same diff by hand," which
reproduces the exact behavior D-23 says is wrong to reproduce.
**How to avoid:** Write the business-date derivation as a call to D-20's new `DayWindow` function
inside the generation loop (`TimeslotGeneratorService.java:141`, beside the existing `ts.setDate(date)`), not as a literal copy of `date`.
**Warning signs:** A test asserting `ts.getBusinessDate().equals(ts.getDate())` unconditionally for
every generated slot — true only at `00:00`, and the test should say so explicitly (parameterize over
both a `00:00` desk and D-19's direct-unit-test 21:00 desk from BDAY-03).

### Pitfall 4: Treating `MidnightTimeArithmeticGuardTest`'s comparison-operator extension as symmetric with the raw-arithmetic scan

**What goes wrong:** The existing raw-arithmetic scan has a hard rule: matching tokens are ALWAYS
routed to `DayWindow` unless both endpoints are starts. The comparison-operator hole (D-02) is
different in kind — `.isAfter`/`.isBefore`/`.compareTo` appear constantly on `LocalDate` and
`BigDecimal`, which have nothing to do with the midnight bug. A token-only scan (as for raw
arithmetic) would misfire on the ~65 non-`LocalTime` hits this session counted.
**Why it happens:** Copy-pasting the raw-arithmetic scan's structure without re-deriving the detection
predicate.
**How to avoid:** D-03's name-based receiver heuristic (`*Time`, `slotStart`/`slotEnd`, `envelope*`,
`band*`, `break*Start`) must gate the comparison-operator scan — confirmed necessary by this session's
count (40+ `.isAfter(` hits, only a handful plausibly `LocalTime`-typed).
**Warning signs:** The comparison-operator allowlist balloons past ~10-15 entries — a sign the
heuristic is too loose and is catching `LocalDate`/`BigDecimal` comparisons.

## Code Examples

### The pattern for a second allowlist section in the same resource file

```java
// Source: src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java (existing, this session)
private static final String RESOURCE = "midnight-time-arithmetic.md";
private static final String ALLOWLIST_HEADING = "### Permitted raw time arithmetic";
// D-01 addition: a second heading, same resource, same parseFencedBlock helper
private static final String COMPARISON_ALLOWLIST_HEADING = "### Permitted raw comparison operators";

@Test
void comparisonOperatorsInProductionCode_matchTheAllowlistExactly() throws IOException {
    Set<String> derived = scanProductionSourcesForComparisons(); // new: filters by D-03's receiver heuristic
    Set<String> allowlist = parseFencedBlock(readResource(), COMPARISON_ALLOWLIST_HEADING);
    assertThat(derived).containsExactlyInAnyOrderElementsOf(allowlist);
}
```

### The pure-evaluation shape D-14 requires (no existing exact precedent — composed from two partial ones)

```java
// Composed from ShiftEnvelopeGroundTruthTest's evaluation call (verified: solutionManager.update(solved)
// with no preceding .solve() on the SAME solution) and ConstraintPrecedenceObservabilityTest's
// getConstraintMatchTotalMap() usage. D-14: build `solved` BY HAND, never via solver.solve(...).
Schedule pinned = buildPinnedScenario(); // every AgentShiftAssignment's planning variables set directly

SolverFactory<Schedule> factory = SolverFactory.create(new SolverConfig()
        .withSolutionClass(Schedule.class)
        .withEntityClasses(AgentShiftAssignment.class, AgentAssignment.class)
        .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                .withConstraintProviderClass(ScheduleConstraintProvider.class)));
SolutionManager<Schedule, HardSoftScore> solutionManager = SolutionManager.create(factory);

HardSoftScore score = solutionManager.update(pinned); // pure evaluation, no search
var explanation = solutionManager.explain(pinned);
Map<String, ConstraintMatchTotal<HardSoftScore>> totals = explanation.getConstraintMatchTotalMap();
// match on total.getConstraintName(), e.g. "Shift work contiguity" -- NOT the raw map key,
// which carries a constraintPackage prefix (confirmed: ConstraintPrecedenceObservabilityTest's own
// comment on this exact gotcha, lines ~91-93)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| v1.4: golden-file byte comparison of captured live desks | v1.5: constructed scenarios with named expected values, plus a separate live drift guard (BDAY-07, Phase 20) | 2026-09-30 (v1.4 cancellation) | Removes the over-sensitivity/under-power double failure that cancelled v1.4; documented at length in `REQUIREMENTS.md:35-50` |
| `985e365`'s unconditional `ts.setBusinessDate(date)` | D-19/D-20's anchor-derived business date via a new `DayWindow` function | This phase, re-authored | Only matters once a desk has a non-`00:00` day-start (Phase 20+); byte-identical at `00:00` |

**Deprecated/outdated:** None yet — D-21's `@Deprecated` annotations on midnight-implicit `DayWindow`
forms are new in Phase 19, not this phase. This phase adds functions; it does not deprecate any.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `SolutionManager.update(solution)` / `.explain(solution)` on `ai.timefold.solver:timefold-solver-core:1.16.0` specifically has the exact pure-evaluation semantics described (no search) — confirmed via general Timefold documentation for the API family, not independently re-checked against the pinned 1.16.0 javadoc this session | Architecture Patterns — D-14's pure-evaluation precedent | If the 1.16.0 API differs (unlikely given both existing precedents in this codebase already use exactly these calls this way), BDAY-06's whole mechanism needs a different entry point. Low risk — the codebase's own two precedent tests already use this exact call successfully against the pinned version. |
| A2 | The `confirmOverride` dead-parameter tension (Finding F-1 / Pitfall 2) is real and not something a later, un-read commit resolves | Summary, Pitfall 2 | If some other salvageable artifact on the rescue tag resolves this (e.g. a later, unlisted commit trims `84fdc3f`'s `confirmOverride`), the finding is moot. Checked: no such commit exists in D-23's list, and the tag's remaining ten commits are explicitly the out-of-scope capture harness per `18-CONTEXT.md`'s own accounting. |

**Risk assessment:** Both assumptions are low-risk. A1 is standard, widely-documented Timefold API
behavior already proven twice in this exact codebase at this exact pinned version. A2 was checked
against the full list of salvageable commits named in `18-CONTEXT.md`'s canonical-refs section, which
is exhaustive by that document's own accounting.

## Open Questions

1. **Should F-1's resolution (strip `confirmOverride` vs. build a trimmed `fec8990`) be a planner
   decision or should it go back to the discussion?**
   - What we know: D-27 says build the refusal, drop the override; D-23 says cherry-pick `84fdc3f`
     verbatim (which contains the override's DTO shape).
   - What's unclear: Whether "drop the override" means "never accept the parameter at all" (requiring
     `84fdc3f`'s cherry-pick to be modified, technically no longer verbatim) or "accept the parameter
     but the refusal is unconditional" (parameter present, silently ignored — arguably worse than
     absent).
   - Recommendation: Resolve at plan time as a P-numbered planner decision, not a fresh discuss-phase
     round — this is exactly the kind of small implementation-shape call `18-CONTEXT.md`'s own
     "Claude's Discretion" section delegates elsewhere in this same document. The cleanest resolution
     is stripping `confirmOverride` entirely: D-27's own reasoning ("an override with no caller is
     untested surface area") applies equally to a parameter with no caller.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | `PostgresBackedTest`-based tests (migration reconciliation against real Flyway/Postgres) | Not directly probed this session (no shell access to Docker daemon state was exercised) | — | `PostgresBackedTest` is `disabledWithoutDocker = true` [VERIFIED: class javadoc, `src/test/java/com/wfm/support/PostgresBackedTest.java`] — tests skip cleanly rather than failing when Docker is absent |
| Gradle / JDK | Backend build and test | Confirmed via successful `git cherry-pick --no-commit` dry runs against a buildable tree this session | — | — |
| Postgres 16.13 (Testcontainers) | Same as Docker row | Same | — | Same |

**Missing dependencies with no fallback:** None identified.

**Missing dependencies with fallback:** Docker — `PostgresBackedTest`'s `disabledWithoutDocker` flag
is the existing, already-adopted fallback; this phase does not need a new one.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + AssertJ, Spring Boot Test, Timefold Solver Test starter |
| Config file | `src/test/resources/application-test.yml` (H2, `flyway.enabled: false`, `ddl-auto: create-drop`) for the default suite; `src/test/java/com/wfm/support/PostgresBackedTest.java` (Testcontainers Postgres 16, real Flyway, `ddl-auto: validate`) for migration-sensitive tests, opt-in per test class |
| Quick run command | `./gradlew test --tests "com.wfm.service.MidnightTimeArithmeticGuardTest"` (or the equivalent fully-qualified class name for any single guard/unit test) |
| Full suite command | `./gradlew test` (project's recorded `test_command` in `.planning/config.json` [VERIFIED: `.planning/config.json`, `workflow.test_command`]) |

[VERIFIED: `src/test/resources/application-test.yml`, read this session — `flyway: enabled: false`,
`jpa.hibernate.ddl-auto: create-drop` confirmed verbatim] This confirms `STATE.md`'s recorded blocker:
the default suite never executes a real Flyway migration. **V53 must be exercised through
`PostgresBackedTest`** (or a subclass) at least once, per the project's own V39 lesson (migration
applied cleanly, app failed to boot under `ddl-auto=validate`, all 402 tests green — recorded in
`STATE.md`'s Blockers/Concerns).

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BDAY-01 | Desk stores `day_start`; existing desks default `00:00` unchanged; `00:00`-only gate refuses non-`00:00` at save time | unit + Postgres-backed | `./gradlew test --tests "com.wfm.service.DeskServiceDayStartTest"` | ✅ (cherry-picked from `84fdc3f`, modulo F-1's resolution) |
| BDAY-02 | Timeslot stores `business_date`; every existing desk's business date provably equals its calendar date | unit + Postgres-backed | `./gradlew test --tests "com.wfm.migration.MigrationEntityConsistencyTest"` and the generator's own business-date tests | ✅ / ❌ — migration test exists (`a1c0077`), business-date-equals-calendar-date proof for a `00:00` desk is new, Wave 0 gap |
| BDAY-03 | `TimeslotGeneratorService` given a 21:00-anchored desk generates a contiguous 24h run, one business date | unit (plain JUnit, no Spring, no DB) | `./gradlew test --tests "com.wfm.service.TimeslotGeneratorServiceTest"` | ❌ — new test, direct unit test per success criterion 2's own wording (D-19's rejected alternative — "proving it against a test double" — is explicitly NOT this; the real `TimeslotGeneratorService` must be exercised with a hand-built `Desk`, D-07's API gate is bypassable at the service-method level even though it blocks the API) |
| BDAY-05 | Guard fails the build if a comparison operator bypasses `DayWindow`; a red-proof shows the guard can go red | build-time structural scan | `./gradlew test --tests "com.wfm.service.MidnightTimeArithmeticGuardTest"` | ✅ extend existing (cherry-pick `63d85a6`) + ❌ D-25's pipeline-level red-proof is new |
| BDAY-06 | Constructed midnight-boundary regression suite passes against today's `00:00`-only behaviour; non-vacuity validator | pure `SolutionManager` evaluation, no solve, no DB | `./gradlew test --tests "com.wfm.solver.<NewBusinessDayBoundaryTest>"` | ❌ — entirely new, no existing file; the closest precedents (`ConstraintPrecedenceObservabilityTest`, `ShiftEnvelopeGroundTruthTest`) both solve first and are not reusable as base classes for this |
| BDAY-08 | Exactly one deriving writer, exactly one propagating writer for `business_date`, guard fails in both directions | build-time structural scan + behavioral | `./gradlew test --tests "com.wfm.service.BusinessDateWritePathGuardTest"` | ✅ (cherry-pick `7d42f23`, already satisfies verbatim per canonical-refs) |

### Sampling Rate

- **Per task commit:** the specific guard/unit test class touched by that task (`./gradlew test --tests "<FQCN>"`)
- **Per wave merge:** `./gradlew test` (full suite) — required given this phase touches shared
  infrastructure (`Desk`, `Timeslot`, `TimeslotGeneratorService`) that many other test classes construct
  fixtures against (confirmed: `2196e40`'s dry-run touched 5 test files beyond its 2 main-source files,
  all pre-existing tests whose fixtures needed a `business_date` update)
- **Phase gate:** Full suite green before `/gsd-verify-work`, **plus** at least one `PostgresBackedTest`-based run exercising V53 specifically (the project's own recorded discipline after the V39 incident)

### Wave 0 Gaps

- [ ] `TimeslotGeneratorServiceTest` (or a new dedicated test class) — direct unit test proving BDAY-03's
      contiguous-24h-one-business-date claim against a hand-built 21:00-anchor `Desk`, no Spring context
- [ ] A new test class for BDAY-06's constructed regression suite — no existing file to extend; base
      structure should follow `LiveShapeShiftDeskFixture`'s class-load validator idiom for D-17's
      non-vacuity predicates, and `ShiftEnvelopeGroundTruthTest`'s `SolutionManager.update()` usage for
      scoring, but must NOT inherit either class's `solveCleanFixture()`-first pattern (D-14 forbids it)
- [ ] D-25's pipeline-level red-proof for the comparison-operator guard (a synthetic offending file in a
      test-resources directory, parameterized scan root) — no existing test-resources fixture directory
      for this purpose; confirm none exists before creating one (`find src/test/resources -type d` was
      not exhaustively enumerated this session)
- [ ] Framework install: none — JUnit 5, AssertJ, Timefold Solver Test, Testcontainers are all already
      `build.gradle` dependencies (lines 60-72, verified this session)

## Security Domain

`security_enforcement` is not set in `.planning/config.json` [VERIFIED: full file read this session,
no such key present] — per the absent-means-enabled convention, this section is included. The
security surface this phase adds is narrow: one new UI-visible-but-disabled input value and two new
database columns with safe defaults. No authentication, session, or access-control surface changes.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-------------------|
| V2 Authentication | No | Unaffected — no change to auth flow |
| V3 Session Management | No | Unaffected |
| V4 Access Control | No | `day_start` write goes through the existing tenant-scoped `DeskService` pattern (`TenantContext.getTenantId()`, confirmed present in every touched service method this session) |
| V5 Input Validation | Yes | `DeskService.setDayStart`'s null check + D-06's 15-minute-boundary range check + D-07's `00:00`-only gate — all three are plain Java validation in the service layer, matching the existing `ShiftTemplateService.validateGridAlignment` precedent style (a hard 400 on invalid input, no new validation library) |
| V6 Cryptography | No | No secret or credential handling in this phase |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|----------------------|
| Cross-tenant `day_start` write (a tenant setting another tenant's desk day-start via a manipulated `deskId`) | Tampering / Elevation of Privilege | `DeskService`'s existing `TenantContext.getTenantId()` + tenant-scoped repository lookup pattern, unchanged by this phase — `84fdc3f`'s diff confirms `setDayStart` follows the same tenant-scoped lookup as every other `DeskService` mutator |
| Accepted-schedule day-start mutation causing silent schedule/timeslot mismatch | Tampering (data integrity) | D-27's refusal (pending F-1's resolution) — refuses the write while an `ACCEPTED` schedule exists on the desk, naming the blocking schedule |

## Sources

### Primary (HIGH confidence — verified this session via Read/Bash tools against the live repository)
- `src/main/java/com/wfm/util/DayWindow.java` — full text read
- `src/main/java/com/wfm/service/TimeslotGeneratorService.java` — full generation-loop region read (lines 60-229)
- `src/main/java/com/wfm/model/Desk.java`, `src/main/java/com/wfm/model/Timeslot.java` — full text read
- `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` — full text read
- `src/test/resources/midnight-time-arithmetic.md` — full text read
- `src/test/java/com/wfm/service/UsualShiftWritePathGuardTest.java`, `src/test/resources/ushf-05-write-paths.md` — representative portions read
- `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java` — `DECLARED_TABLES` region read
- `src/test/java/com/wfm/solver/LiveShapeShiftDeskFixture.java` — `validateTemplateSpecs()` region read
- `src/test/java/com/wfm/solver/ConstraintPrecedenceObservabilityTest.java`, `src/test/java/com/wfm/solver/ShiftEnvelopeGroundTruthTest.java` — cited regions read, `.solve(` calls confirmed via grep
- `src/main/java/com/wfm/service/ScheduleService.java` — `acceptSchedule` region read
- `frontend/src/pages/DeskManagement.tsx` — full 127-line file read
- `frontend/src/api/client.ts` — `Desk` interface line read
- `src/test/resources/application-test.yml` — full text read
- `src/test/java/com/wfm/support/PostgresBackedTest.java` — javadoc region read
- `build.gradle` — dependency and Timefold-version lines read
- `.planning/config.json` — full text read
- `git show`/`git log`/`git cherry-pick --no-commit` — all 8 named commits (`84fdc3f`, `a1c0077`, `2196e40`, `7d42f23`, `63d85a6`, `985e365`, `fec8990`, `5ddd8dc`) checked for ancestry and the five D-23 commits dry-run cherry-picked against HEAD with zero conflicts, then reverted

### Secondary (MEDIUM confidence)
- Timefold `SolutionManager`/`ScoreExplanation` pure-evaluation semantics — WebSearch against docs.timefold.ai, general API family confirmed, not independently checked against the exact pinned `1.16.0` javadoc this session (see Assumption A1)

### Tertiary (LOW confidence)
- None — no claim in this document rests solely on unverified training-data recall

## Metadata

**Confidence breakdown:**
- Current-tree file states, line numbers, call-site counts: HIGH — every number re-derived from a live `grep`/`Read` this session
- Cherry-pick applicability: HIGH — dry-run cherry-picked, not merely inspected
- BDAY-06 pure-evaluation mechanism: MEDIUM-HIGH — the codebase pattern is solid, the exact `1.16.0` API surface for `SolutionManager` was confirmed by general documentation, not the pinned-version javadoc specifically
- The `confirmOverride` finding (F-1): HIGH — read directly from both commits' diffs this session

**Research date:** 2026-09-30
**Valid until:** Until the next commit touches any of `DayWindow.java`, `TimeslotGeneratorService.java`, `Desk.java`, `Timeslot.java`, `MidnightTimeArithmeticGuardTest.java`, or the rescue tag's five named commits — line numbers and cherry-pick applicability are point-in-time facts, not durable ones. Re-verify line numbers if planning is delayed more than a few days from 2026-09-30.
