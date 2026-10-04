# Requirements: WFM Service — v1.5 Overnight Shifts & Business Dates

**Defined:** 2026-09-30
**Core Value:** Scheduling managers can produce optimised, constraint-aware agent schedules in minutes instead of hours — without spreadsheets.
**Milestone goal:** A shift can span midnight and belongs to the business day it starts on — for any desk, any day start, any future data set.

## Context

Requirement IDs continue the project's existing scheme. Categories are new to this milestone.
Phase numbering continues from **18**. Next Flyway migration is **V53** (schema head is V52; v1.4's
V53 was unwound and the number is free again).

### Why this milestone exists

`DayWindow.durationMinutes` throws on a midnight-crossing interval, deliberately. Its javadoc names
the reason, and the reason is not a boundary bug:

> This class deliberately does NOT model a shift that runs PAST midnight into the next calendar
> day (22:00–06:00). That is not a boundary problem but a data-model one: such a shift belongs to
> two dates, while day-off records, contracted-hours-per-day and the solver's per-day seat model
> all assume one.

That assumption is now wrong in two directions. Desks that need night shifts cannot express them —
the live Phil-US desk stores **every roster time with a +3h offset** purely so its base shift reads
`00:00-09:00`, so nothing in the desk says what the client's shifts actually are. And SLOT mode is
**already wrong today**: it can hold an overnight stretch but splits it at midnight, so a Sunday
21:00–Monday 06:00 stretch counts 3h against Sunday and 6h against Monday and `contractedHoursOver/
Under` registers *both* days as under-allocated against an 8h contract.

The defect family is demonstrated, not theoretical. `ScheduleExportService`'s roster-cell end
tracking compared times with a raw `isAfter()`, so an assignment ending at end-of-day stored `00:00`
— `LocalTime`'s minimum — and never won the comparison; the exported roster silently under-reported
every such shift's true end time. Found and fixed as `5ddd8dc`, live in the current tree.

### Relationship to the cancelled v1.4

v1.4 ran this scope and was **cancelled on 2026-09-30** before shipping, all 32 commits unwound,
state preserved at tag `rescue/phase-18-unwind-20260930`. It was not cancelled for a wrong
architecture — the architecture research for v1.5 re-derived and confirmed the same design. It was
cancelled because BDAY-06's regression fixture sourced its inputs from four captured *live* desks,
which made it wrong in both directions at once: **over-sensitive**, because Vinted's 287 agents mean
any scoring change anywhere shifts the golden bytes with no diagnostic signal; and **under-powered
on midnight**, because three of the four live desks never cross it, none has a 23:00–00:00 slot, and
no break band touches an envelope edge. A baseline built to catch a midnight regression rested on
data that barely contains midnight — because *this* milestone is what introduces overnight shifts.

v1.5 inverts that. Constructed scenarios chosen by the property under test carry correctness; one
small live desk is demoted to a drift guard. The requirement split below (BDAY-06 constructed,
BDAY-07 drift) exists so the two can never again be conflated into one fixture that does
neither job.

**Amended 2026-10-01 (Phase 20 discussion, 20-CONTEXT D-14).** BDAY-07's drift guard is built to a
live desk's *shape*, not from its captured bytes. The only source of per-constraint match counts is
`solutionManager.explain()`, which `ScheduleOutputService` deliberately refuses to call on the
ACCEPTED/DB path, so a real before/after against the live desk would mean either polling the 4 MB
detail payload against a two-core solver or adding operator-reachable API surface inside a
correctness phase. What still keeps BDAY-07 distinct from BDAY-06 — and keeps v1.4's failure from
recurring — is that it asserts **per-constraint match counts**, which carry a diagnostic signal,
rather than golden bytes, which carry none, and that its composition is the live desk's rather than
a property-chosen scenario's. The live half is knowingly given up for v1.5; restoring it belongs
with MIGR-01..04.

### What research established — treat as settled

1. **The blast radius is narrower than "112 call sites" suggests.** Every date-bearing problem fact
   the solver reads *except* `Timeslot` — `AgentDayOff`, `AgentDayConfig`, `AgentPreference`,
   `AgentShiftAssignment`, `ResolvedUsualShiftTarget`, `ShiftStartMixTarget` — is already
   business-date-shaped with no competing calendar-date meaning. Only `Timeslot` carries two dates.
   7 of the 12 constraint joins already flow through one shared `DATE` lambda hoisted in v1.3.
2. **The failure mode is a silent non-join, not an error.** A constraint that matches nothing scores
   identically to a satisfied one. This is why SOLV-06 asserts per-constraint *match counts* and not
   only scores — two different wrongnesses can sum to the same score.
3. **Start-day attribution is the cross-domain norm.** Hospitality ("business date", rolled at night
   audit), gaming ("gaming day"), healthcare ("shift date") and rail ("duty day") all attribute a
   midnight-spanning period to its start instant. The only domain that splits at midnight is payroll
   wage apportionment, a layer this project does not operate.
4. **No new runtime dependency is needed.** This is a modelling and migration-safety milestone.
   jqwik is test-scope only.
5. **`agent_day_hours` needs no schema change** — it is keyed by `DayOfWeek`, and a Sunday-night
   shift correctly consumes Sunday's row.

### Decisions taken at scoping — do not relitigate without new evidence

| Decision | Rationale |
|---|---|
| **Desk-level day start**, not a per-template `ends_next_day` flag | One uniform anchor. Existing desks default to `00:00` and behave exactly as today, so only night desks opt in, and the `00:00`-means-end-of-day convention can retire rather than persist alongside a second mechanism. Carried from v1.4 scoping; re-confirmed by the hospitality-PMS precedent, which solves the mixed day/night problem with disclosure (OVNT-07) rather than a second boundary. |
| **`business_date` is a stored column populated at write time** | Not a Postgres generated column — that is mechanically impossible here, since the value needs a cross-table desk anchor. Not a lazily-computed getter — incremental-score performance and correctness risk. BDAY-08's write-path guard is what makes "stored" safe. |
| **The `DayWindow` migration is compiler-forced** | Removing the implicit-`00:00` overload turns every call site into a build error. This is the only option where "the compiler finds them all" is literally true rather than aspirational. |
| **The join guard is test-level, not type-level** | A `BusinessDate` wrapper type was evaluated and rejected for this milestone: it buys real compile-time enforcement only if *both* sides of every join are wrapped, which is a wide refactor. The test-level structural guard pattern is already proven twice in this codebase (`MidnightTimeArithmeticGuardTest`, and v1.3's write-path allowlist which was proven able to fail twice). |
| **Minimum rest is a HARD constraint** | Enabling overnight shifts without it would *create* a way to produce illegal back-to-back rosters that score `0hard`. The gap would be introduced by this milestone, not inherited. Its pre-solve refusal is a **separate mechanism** from the in-solve constraint, reusing the ENVL-07 seat-supply-gate precedent. |
| **A rest violation is waivable per agent, per business date** | Rest is a hard constraint, and a hard constraint with no escape hatch makes a real roster unsolvable rather than merely flagged. The waiver reuses `AgentException`, which already has the `(tenant, desk, agent, date)` shape and a mandatory `reason`, so an override is always attributable. A waiver names the **business date of the later shift** — the shift the operator is actually authorising — consistent with the milestone's start-day attribution. Scope is one agent, one date: no standing exemptions, because a standing exemption is the kind that gets forgotten. |
| **Minimum rest is a general shift-to-shift gap, not overnight-only** | Same-day back-to-back shifts can violate it too. Scoping it narrowly to overnight would ship a constraint that is wrong in the ordinary case. |
| **Guard tests land before the re-anchoring** | `DayWindow` is the only thing standing between this change and a silently wrong schedule, and there is nothing to compare against otherwise. |
| **Timezones are out of scope** | The other half of the Phil-US problem (roster PHT, forecast US Pacific, 15h apart) stays the operator's job, done before load. Keeps the `DayWindow` re-anchoring self-contained. |
| **Phil-US is NOT migrated in this milestone** | v1.4 made the live desk its proof; v1.5 does not. Deferring it is what keeps the capability desk-agnostic and forces correctness to be demonstrated on constructed cases rather than on the one desk that happens to need it. |

### Accepted consequence

On a desk with a 21:00 day start, a hypothetical 09:00 day shift sits 12 hours into business day D
and therefore runs on calendar D+1. Correct arithmetically, but a desk mixing day and night shifts
requires the operator to think in business days. Accepted at scoping — and OVNT-07 is the mitigation:
disclosure through calendar-span labelling, not a second day-boundary mechanism.

## v1.5 Requirements

### Business Day Model (BDAY)

- [x] **BDAY-01**: Operator can set the time a desk's day begins, and a desk that has never set one behaves exactly as it does today
- [x] **BDAY-02**: A timeslot records the business day it belongs to, distinct from its calendar date
- [x] **BDAY-03**: Timeslot generation for a desk whose day starts at 21:00 produces a contiguous 24-hour business day spanning two calendar dates
- [x] **BDAY-04**: Interval arithmetic is anchored on the desk's day start rather than midnight, and the `00:00`-means-end-of-day convention is retired
- [x] **BDAY-05**: A guard test fails if any scheduling interval calculation bypasses the shared day-window utility — comparisons (`isAfter`/`isBefore`/`compareTo`) as well as arithmetic
- [x] **BDAY-06**: A constructed regression suite proves behaviour across the midnight boundary, its scenarios chosen by the property under test, with a validator that fails if those scenarios do not actually contain the boundary cases they claim to
- [x] **BDAY-07**: A constructed fixture built to one small live desk's shape (Phil-US, 48 agents) produces an unchanged schedule across the re-anchoring, compared on per-constraint match counts as well as score
- [x] **BDAY-08**: Exactly one code path writes a timeslot's business date, proven by a write-path guard test that fails in both directions

### Overnight Shifts (OVNT)

- [x] **OVNT-01**: Operator can save a shift template whose end time is earlier in the clock than its start time, and its net hours are correct
- [x] **OVNT-02**: A shift that spans midnight is reported against the business day it starts on, everywhere it is displayed
- [x] **OVNT-03**: A day-off or PTO marking on the business day an overnight shift starts prevents that shift being assigned
- [x] **OVNT-04**: An overnight shift consumes the contracted hours of the weekday it starts on, not split across two
- [x] **OVNT-05**: Shift library validation refuses an overnight template whose envelope does not fit inside its desk's business day
- [x] **OVNT-06**: The Excel Allocation sheet and the schedule UI grid — the per-date slot surfaces, ordered from the desk's day start — show an overnight shift as one continuous block, never two fragments; the business-day-keyed Roster sheet and schedule grid carry the shift's calendar-span disclosure instead of a continuation indicator, since an overnight shift there already occupies exactly one business-day cell
- [x] **OVNT-07**: An overnight shift is labelled with the calendar dates it spans wherever it is displayed, so a business-day-anchored surface still tells the operator the shift runs into the next calendar day

### Solver Correctness (SOLV)

- [x] **SOLV-01**: Every constraint that groups an agent's day joins on business date, so an overnight shift's post-midnight timeslots are included
- [x] **SOLV-02**: A test fails if any constraint joins on a timeslot's calendar date where business date is meant
- [x] **SOLV-03**: Break bands, contiguity and envelope compliance hold across the midnight boundary
- [x] **SOLV-04**: SLOT mode counts an overnight stretch against a single business day rather than under-allocating both calendar days
- [x] **SOLV-05**: The pre-solve seat-supply check reports shortfalls per business day
- [x] **SOLV-06**: Each migrated join is proven non-vacuous — per-constraint match counts are asserted, so a constraint that matches nothing cannot pass as satisfied
- [x] **SOLV-07**: Demand upload, coverage reporting and the solver provably resolve the same business date for the same timeslot, guarded by a test rather than by convention

### Minimum Rest (REST)

- [x] **REST-01**: Operator can set a minimum rest period between an agent's consecutive shifts, per desk
- [x] **REST-02**: The solver treats insufficient rest as a hard violation, measured between the actual end and start instants — including same-day back-to-back shifts, not only overnight ones — unless that occurrence is waived per REST-06
- [x] **REST-03**: A rest violation is refused pre-solve where it is structurally unavoidable, naming the agent and the two shifts, by a mechanism separate from the in-solve constraint — and a waived occurrence does not trigger that refusal
- [x] **REST-04**: A desk that sets no minimum rest solves exactly as it does today
- [x] **REST-05**: Rest at the first and last day of the solving horizon has defined, tested behaviour rather than an accidental one
- [ ] **REST-06**: Operator can waive minimum rest for one agent on one business date, with a recorded reason, through the existing per-agent exception mechanism — and the solver treats a waived pair as legal
- [ ] **REST-07**: Waived rest violations are visible in the solved schedule's output, so a waiver cannot silently hide a roster problem

## Future Requirements

Deferred. Tracked but not in this roadmap.

### Phil-US Migration (MIGR)

Deferred from v1.4 by scoping decision — the capability must be desk-agnostic before one desk is
moved onto it.

- **MIGR-01**: The Phil-US desk stores the client's real shift times with no offset applied
- **MIGR-02**: Phil-US demand is loaded against its real hours, and the mapping from the client forecast is recorded in the desk's own configuration rather than in a session transcript
- **MIGR-03**: Phil-US produces a schedule at least as good as its pre-migration baseline on hard score and coverage, where that baseline is re-measured on the pre-migration model at the start of the migration phase rather than carried forward from an earlier solve
- **MIGR-04**: The migration has a documented, tested reversal that restores the desk to its pre-migration state

### Timezones (TZ)

- **TZ-01**: A desk declares the timezone its schedule is expressed in
- **TZ-02**: A demand upload declares the timezone its hours are expressed in, and is converted on load
- **TZ-03**: Operator is warned when a desk's roster and its demand were loaded in different timezones
- **TZ-04**: DST transitions produce a 23- or 25-hour business day without corrupting contracted hours

## Out of Scope

| Feature | Reason |
|---------|--------|
| Phil-US live desk migration | Promoted to Future above. v1.4 made it the milestone's proof; v1.5 deliberately does not, so the capability is demonstrated on constructed cases rather than on the one desk that happens to need it. |
| Timezone modelling | The other half of the Phil-US problem, deliberately separated so the `DayWindow` re-anchoring stays self-contained. Promoted to Future above. |
| Per-template `ends_next_day` flag | Considered and rejected at scoping in favour of the desk-level day start — it would leave `DayWindow`'s midnight convention in place alongside a second mechanism. |
| A `BusinessDate` wrapper type | Evaluated by architecture research and rejected for this milestone: it only buys compile-time enforcement if both sides of every join are wrapped, a wide refactor. The test-level structural guard is already proven twice here. |
| Golden-file byte comparison of whole live desks | The specific mechanism that caused v1.4's cancellation. Replaced by constructed scenarios plus per-constraint match counts. |
| Shifts longer than 24 hours | No operational case; a business day is exactly 24 hours by construction. |
| Rest periods across desks | An agent belongs to one desk at a time in this data model. |
| DST-aware business days | A business day is a fixed 24 hours in this milestone. Named in TZ-04 so the model is not built in a way that forecloses it. |
| Reconciling the Phil-US roster with its own forecast | The client's roster is rest-day driven and 42 FTE-days short of its own Required FTE for the week. Real, but a client conversation, not a code change. |

## Open Decisions for Planning

Not blockers for the roadmap, but each needs a deliberate answer at phase-planning time rather than
a default:

- **Cherry-pick v1.4's rescue-tag commits or re-author fresh.** `84fdc3f` (`business_date` column),
  `2196e40`, `7d42f23` (`BusinessDateWritePathGuardTest`) and `63d85a6` are directly reusable
  reference implementations. Cherry-picking is faster but re-imports the habits that produced the
  cancelled fixture; the tree has also moved since (`5ddd8dc`).
- **The golden-file justification-log enforcement mechanism.** Three ranked options existed in v1.4's
  research, hash-based recommended; never settled.
- **Whether `agent_shift_assignment` needs its own `business_date` column** or derives one through
  its `Timeslot` relation.
- **The final allowlist contents for the join guard.** Architecture research expects "plausibly none"
  survive as legitimate calendar-date uses inside a join, but this needs verifying against the
  current `ScheduleConstraintProvider`, not assuming.
- **Re-grep the counts.** "112 references across 16 files" and "12 joins" are directionally reliable
  but were measured on a tree that has since moved. Re-verify at Foundation planning time.

## Traceability

Populated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| BDAY-01 | Phase 18 | Complete |
| BDAY-02 | Phase 18 | Complete |
| BDAY-03 | Phase 18 | Complete |
| BDAY-04 | Phase 19 | Complete |
| BDAY-05 | Phase 18 | Complete |
| BDAY-06 | Phase 18 | Complete |
| BDAY-07 | Phase 20 | Complete |
| BDAY-08 | Phase 18 | Complete |
| OVNT-01 | Phase 21 | Complete |
| OVNT-02 | Phase 21 | Complete |
| OVNT-03 | Phase 21 | Complete |
| OVNT-04 | Phase 21 | Complete |
| OVNT-05 | Phase 21 | Complete |
| OVNT-06 | Phase 21 | Complete |
| OVNT-07 | Phase 21 | Complete |
| SOLV-01 | Phase 20 | Complete |
| SOLV-02 | Phase 20 | Complete |
| SOLV-03 | Phase 20 | Complete |
| SOLV-04 | Phase 20 | Complete |
| SOLV-05 | Phase 20 | Complete |
| SOLV-06 | Phase 20 | Complete |
| SOLV-07 | Phase 20 | Complete |
| REST-01 | Phase 22 | Complete |
| REST-02 | Phase 22 | Complete |
| REST-03 | Phase 22 | Complete |
| REST-04 | Phase 22 | Complete |
| REST-05 | Phase 22 | Complete |
| REST-06 | Phase 22 | Pending |
| REST-07 | Phase 22 | Pending |

**Coverage:**

- v1.5 requirements: 29 total
- Mapped to phases: 29 (roadmap created 2026-09-30 — Phases 18–22)
- Unmapped: 0 ✓

---
*Requirements defined: 2026-09-30*
