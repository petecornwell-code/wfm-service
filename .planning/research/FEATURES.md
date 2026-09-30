# Feature Research

**Domain:** Contact-centre workforce management — overnight/night shifts and business-day
(vs. calendar-day) attribution
**Milestone:** v1.5 Overnight Shifts & Business Dates (adds midnight-spanning shifts and business-date
attribution to an existing, live shift/slot WFM app)
**Researched:** 2026-09-30
**Confidence:** MEDIUM overall — industry-wide web sources on vendor UX/behaviour and cross-domain
analogues (hospitality, gaming, healthcare, rail), no direct vendor trial access. Regulatory claims for
the EU and US tiers are cross-checked against primary legal text. The Philippines claim is **LOW
confidence as a single number** — the Labor Code sets no statutory shift-to-shift rest figure; 10–12h
is repeatedly cited industry/commentary practice, not codified law. See Sources.

This research covers **only the new v1.5 surface**: business-day/day-start anchoring, overnight shift
templates, business-date-correct solving, and minimum rest. It does not re-cover the shift template
library, contracted-hours model, PTO, usual-shift/consistency, or the SHIFT/SLOT mode switch — all
already built (see PROJECT.md "Current State" and v1.2/v1.3 `FEATURES.md`). Every finding below is
framed against what those existing capabilities already provide, since the downstream roadmap needs to
know what v1.5 must **extend** versus what it must **build new**.

## Feature Landscape

### Table Stakes (Any 24/7 Roster Must Get These Right)

These are not competitive differentiation — every mature rostering product that supports shifts
crossing midnight (contact-centre WFM, hospital nurse rostering, hospitality PMS, gaming, rail crew)
gets these right, and a product that doesn't feels structurally broken, not merely unpolished.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| A single, fixed "day boundary" concept distinct from midnight, uniformly applied per operating unit | Hospitality calls it "business date" (rolled at night audit, often ~2am, not midnight); gaming calls it "gaming day" (fixed cutover, commonly 6am); healthcare/rail call it "shift date"/"duty day". Every domain researched puts the boundary **somewhere other than midnight** precisely because operations don't stop at midnight | LOW conceptually, **HIGH here specifically** | This project's version is the desk-level day-start anchor. The concept itself is table stakes and well-precedented; the *retrofit* onto 112 existing midnight-anchored `DayWindow` references is what makes it the riskiest edit in the milestone, per PROJECT.md's own framing — complexity is migration risk, not novelty of the idea |
| Business date stored/derivable on every schedulable unit (shift, slot, timeslot) | Cannot report, block, or account against "the day this belongs to" if that day isn't a queryable fact | LOW–MEDIUM | Already scoped in PROJECT.md as "Business date on every timeslot — distinct from its calendar date, populated at generation" |
| Shift templates whose end clock is earlier than the start clock, with correct net-duration math | Any 24/7 desk needs at least one shift that crosses midnight (e.g. `21:00–06:00` = 9h, not a negative or wrapped-wrong duration) | LOW–MEDIUM | Directly extends the existing shift-template library (v1.3 SHLB-01…04) — same entity, new validation rule (`end < start` is legal and means "next calendar day") |
| Day-off/PTO marking blocks by the shift's **starting** business day | Universal across every domain checked: a nurse's day off is the day her shift was scheduled to *start*; a BPO agent's PTO day blocks the night shift that begins that evening, even though most of its hours land on the next calendar date | LOW | No schema change needed — `agent_day_hours` is already keyed by `DayOfWeek` (PROJECT.md, "carried forward" section). This is purely a business-date-computation correctness requirement on the existing lookup, not a new model |
| Contracted hours consumed against the starting business day's row, not split across two calendar days | Same precedent as above — none of the domains researched split a single duty period's hours proportionally across the calendar days it touches for *rostering* purposes (payroll/timesheet software sometimes does this for wage-period apportionment, a different, lower layer this project doesn't operate at) | LOW | Same no-schema-change note; a Sunday-night shift correctly consumes Sunday's `agent_day_hours` row per PROJECT.md's own settled decision |
| One continuous schedule-grid block for a midnight-crossing shift, never rendered as two fragments | Confirmed by current call-center scheduling UX research: grids show a shift's exact start and end regardless of date-line crossing; splitting visually at midnight reads as a defect in weaker tools, not a feature | LOW–MEDIUM (frontend) | Already explicitly required in PROJECT.md's OVNT bullet ("export and UI render one continuous block, not two fragments") |
| Excel export places the full shift span under the **starting** day's column, not split or duplicated | Matches how printed nurse/duty rosters lay out day-shift and night-shift columns for the same day; also matches this project's own existing per-weekday roster/export convention | LOW–MEDIUM | One recent commit (`5ddd8dc fix(export): route roster-cell end tracking through DayWindow`) already began this work — a head start, not a fresh problem |
| Coverage/headcount reporting rolls up by business date, and does so **consistently** between demand input and coverage output | If Erlang staffing demand is interpreted against calendar date while coverage reporting rolls up by business date (or vice versa), the two numbers permanently disagree on where a night trough sits — this is a correctness bug class, not a UX nicety | MEDIUM | Depends on the existing Erlang C/X staffing calculator and demand upload (already built) being re-pointed at the same business-date anchor the rest of v1.5 introduces — flagged explicitly since it's easy to fix "the schedule side" and silently leave "the demand side" on calendar date |
| Overnight coverage counted correctly in **SLOT mode**, not just SHIFT mode | Every desk that has ever had an agent working past midnight under the old slot model has, per PROJECT.md, been silently under-allocating both calendar days it touched — this is a pre-existing defect the new capability exposes rather than one it introduces | MEDIUM | Explicitly scoped in PROJECT.md as its own line item ("SLOT-mode correctness"); table stakes because it's a **correctness fix**, not new capability, and ships regardless of whether a given desk ever adopts overnight SHIFT templates |

### Differentiators (Where v1.5 Exceeds Common Practice)

None of these are required to claim "we support overnight shifts." They are what makes this
implementation more trustworthy than the median vendor's, and they follow directly from this
project's existing pattern of refusing bad solves rather than silently degrading (v1.3 ENVL-07).

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Minimum rest as a **hard**, pre-solve-refused constraint naming the agent and both shifts | Every commercial tool researched treats shift-to-shift rest as either unenforced (operator judgement) or a soft warn-and-override-with-premium-pay pattern (the US fair-workweek "clopening" ordinances: LA 10h, Berkeley/Emeryville 11h, consent + time-and-a-half if violated). None of the domains researched pre-solve-refuse a schedule for a rest violation the way this project already does for shift-envelope infeasibility | MEDIUM–HIGH | Reuses the exact refusal pattern already proven for SHIFT-mode infeasibility (ENVL-07) — same UX shape (name the date/agents, not just "infeasible"), new predicate. PROJECT.md has already settled this as non-negotiable: "enabling overnight shifts without it would create a way to produce illegal back-to-back rosters scoring `0hard`" |
| Desk-level (not per-template) day-start anchor | Simpler operator mental model than a per-shift `ends_next_day` flag (the alternative several vendors effectively implement by letting each shift template independently declare it spans midnight) — one number per desk, not N flags across a library that must all agree | LOW conceptually, but the coordinating cost lands elsewhere (DayWindow retrofit) | PROJECT.md has already settled and explicitly protects this decision from relitigation — restated here because it is also the source of the one open usability question (see "Mixed day/night desks" below) |
| A guard test that fails if any solver constraint joins calendar date where business date is meant | Vendors do not publish, and this research found no evidence any commercial WFM product tests, the distinction between "silently joins the wrong date" and "correctly computed." This project treats that failure mode as first-class enough to require a structural test, not a review item | MEDIUM | Directly named in PROJECT.md: "the failure mode here is a *silent non-join*, not an error, which is why the guard is a requirement rather than a review item" — 12 `timeslot.getDate()` call sites in `ScheduleConstraintProvider` |
| Retiring the `00:00`-means-end-of-day `DayWindow` convention behind one shared utility | Not user-facing, but it is what makes the correctness properties above maintainable rather than accidental — 112 references across 16 files routed through one seam by design (P-19/D-02) | HIGH (breadth), LOW (per-call-site mechanical risk) | The riskiest single edit in the milestone by PROJECT.md's own account; differentiator from a defect-avoidance standpoint, not a market one |
| Constructed, property-chosen regression fixtures for midnight coverage, with live data demoted to a drift guard | v1.4's cancelled attempt sourced its midnight regression fixture from four live desks that barely contain midnight (three never cross it, none has a 23:00–00:00 slot). v1.5 inverts that deliberately | MEDIUM | This is a testing-strategy differentiator, not a runtime feature, but it belongs in this document because it is the thing that failed last time and the roadmap needs to plan phases around it, not just features |

### Anti-Features (Tempting, Wrong for This Milestone)

Each of these looks like a reasonable next step from the shape of v1.5 but is either already
explicitly rejected in PROJECT.md, or contradicted by what every domain researched actually does.

| Feature | Why It Looks Right | Why It's Wrong Here | Alternative |
|---------|--------------------|---------------------|-------------|
| Per-shift-template `ends_next_day` flag instead of a desk-level day-start anchor | This is what several vendors effectively do (a per-template midnight-crossing flag), and it looks like a smaller, more local change than a desk-wide anchor | **Already explicitly rejected in PROJECT.md**: "the flag was considered and rejected for leaving `DayWindow`'s midnight convention in place alongside a second mechanism" — it would create two competing day-boundary concepts instead of one | Desk-level day-start anchor, settled and not to be relitigated |
| Proportional/calendar-split attribution of an overnight shift's hours across the two calendar days it touches, for contracted-hours or coverage purposes | Looks "more accurate" against a naive calendar view, and it is genuinely how some payroll/timesheet software apportions wages across a pay-period boundary | No domain researched does this for **rostering** (as opposed to payroll wage-period apportionment, a different layer this project does not operate). It also has no home in the schema — `agent_day_hours` is one row per weekday, not per calendar date, and splitting would require exactly the schema change PROJECT.md states is unnecessary | Whole-shift attribution to the starting business day, per the table-stakes row above |
| Soft-only / warn-then-allow minimum rest, with an operator override | This is literally what the researched US municipal fair-workweek ordinances do (consent + premium pay if under 10–11h) — a real, live regulatory pattern, not a strawman | **Already explicitly settled and rejected in PROJECT.md**: minimum rest is HARD, because "enabling overnight shifts without it would create a way to produce illegal back-to-back rosters scoring `0hard` — a gap this milestone would introduce, not inherit" | Hard, pre-solve refusal per desk, with the constraint simply unset (and therefore inert) on any desk that doesn't configure it |
| Premium-pay / consent-to-override machinery for a minimum-rest violation, mirroring the US "clopening" ordinance pattern | The regulatory research surfaced real vendor mechanics for exactly this (allow the short-rest shift, pay time-and-a-half) | This project has no payroll or premium-pay calculation system anywhere in its architecture. Building consent-and-pay-through UI for a single constraint type would introduce a whole subsystem this milestone doesn't need and no other constraint in the app has | Hard block only. If Helpware ever needs a pay-through-override workflow, that is new scope requiring new research, not something to pre-build here |
| Timezone-aware or auto-adjusting day-start anchor (deriving the anchor from agent or forecast timezone) | Phil-US is a real live desk with a documented 15h PHT/Pacific gap (PROJECT.md, "Phil-US grid is PHT, forecast is Pacific") — it's tempting to solve the general problem once | **Explicitly deferred in PROJECT.md**: "Timezones... stay the operator's job, done before load. Keeps the re-anchoring self-contained." Building this now directly contradicts a stated scope boundary and couples an already-risky migration to a second hard problem | Operator sets one desk-level anchor in local operating time, same as today; timezone handling stays a pre-load operator responsibility |
| Rolling/rounding an overnight shift's late hours into "tomorrow's" coverage number in reports for readability, rather than making reporting itself business-date-aware | Looks like a lightweight display fix instead of a data-model change | This recreates the calendar-split anti-pattern one layer up (in the reporting query instead of the data model) — it produces a report that disagrees with the underlying business-date-anchored schedule and demand data | Reporting queries and rollups must be business-date-aware end to end; there is no display-layer shortcut that avoids this |

## Feature Dependencies

```
Desk-level day-start anchor (defaults 00:00, backward-compatible)
    └──requires──> DayWindow re-anchored on day-start, not midnight (112 refs, 16 files)
                       └──enables──> Business date populated on every timeslot
                                        ├──requires-for──> Overnight shift templates (end < start, correct net hours)
                                        │                     └──extends──> Existing shift-template library (v1.3 SHLB-01…04)
                                        │                     └──requires──> Existing per-day contracted hours / PTO model
                                        │                                      (agent_day_hours keyed by DayOfWeek, v1.2 MDL-02)
                                        │                                      — NO schema change, per PROJECT.md
                                        ├──requires-for──> Solver business-date correctness
                                        │                     (12 timeslot.getDate() joins → business date, + guard test)
                                        ├──requires-for──> SLOT-mode overnight correctness (independent of shift templates)
                                        ├──requires-for──> Minimum rest hard constraint
                                        │                     (needs correct start/end instants across a midnight boundary
                                        │                      to compute the shift-to-shift gap at all)
                                        ├──requires-for──> Coverage/headcount reporting by business date
                                        │                     └──requires──> Existing Erlang C/X demand upload re-pointed
                                        │                                      at the same anchor (already built, needs
                                        │                                      consistent interpretation, not new code)
                                        └──requires-for──> Schedule grid (one continuous block) + Excel export
                                                              └──touches──> Existing usual-shift/drift-report display
                                                                             surfaces (v1.3 USHF-06, DRFT-01…04)
                                                              └──touches──> Existing ScheduleExportService (partially
                                                                             started: 5ddd8dc)

Regression safety (constructed midnight-spanning scenarios + Phil-US small drift guard)
    └──must-precede──> DayWindow re-anchoring, per PROJECT.md ("guard tests land before the re-anchoring")
```

### Dependency Notes

- **Everything sits on the DayWindow re-anchor; nothing else can be parallelized ahead of it.** Business
  date on timeslots, solver correctness, minimum rest, reporting, grid rendering, and export all consume
  a correctly re-anchored `DayWindow`. This is the single sequencing fact that should shape phase
  ordering: the re-anchor (with its guard tests written first, per PROJECT.md) is necessarily an early
  phase, not a parallel track.
- **Overnight shift templates and PTO/contracted-hours blocking need no schema change** — this is a
  genuine gift from prior milestones' design choices (v1.2's per-weekday `agent_day_hours`), not
  something v1.5 has to build. The roadmap should not budget schema-migration time for this specific
  piece; the work is entirely in business-date *computation* feeding an existing lookup correctly.
- **Minimum rest depends on overnight shifts existing, but is not exclusive to them.** A same-day
  back-to-back pairing (e.g., a `05:00–14:00` shift followed by a `20:00–05:00` shift the same calendar
  day) can violate minimum rest without either shift crossing midnight. Phase planners should not scope
  the rest constraint as "overnight-shift-only" — it is a general shift-to-shift gap computed from any
  two shift instants, and overnight shifts are simply what makes violations *common* rather than what
  makes them *possible*.
- **Coverage reporting has a silent-disagreement risk that is easy to miss**: the demand/staffing upload
  path (Erlang calculator, already built) and the new business-date-anchored coverage output are two
  separate code paths that must agree on the same anchor. Unlike the solver-join guard (explicitly
  scoped with its own test), PROJECT.md's v1.5 feature list does not call out a matching guard for
  demand-vs-coverage anchor agreement — flag this to the roadmapper as a candidate verification gap, not
  an assumed-covered item.
- **Excel export already has a head start.** `5ddd8dc fix(export): route roster-cell end tracking
  through DayWindow` (in the git log at research time) suggests export's roster-cell end-tracking has
  already begun routing through `DayWindow` — the roadmap should verify whether this closes part of the
  OVNT export requirement or was a narrower, unrelated fix, rather than assuming it's either fully done
  or entirely separate.

## Minimum Rest — Specification Norms and Regulatory Baselines

### How it's normally specified

Universally specified as **consecutive hours between the end of one shift and the start of the next**
(a rolling gap measured from real instants), not as a calendar-day-count or a weekly aggregate. This is
distinct from — and in addition to — weekly rest-day guarantees (e.g., "24 hours after 6 consecutive
work days"), which this project's existing MANDATORY day-off / weekly rest-day machinery already
substantially covers. v1.5's REST group is specifically the **shift-to-shift gap**, not weekly rest.

### Typical values found, by regulatory tier

| Tier | Value | Character | Source |
|------|-------|-----------|--------|
| EU / UK statutory | **11 consecutive hours** in each 24-hour period | Hard, non-negotiable minimum (Member States "must ensure") | EU Working Time Directive 2003/88/EC Art. 3; mirrored in UK Working Time Regulations 1998 |
| EU / UK weekly (adjacent, not the same constraint) | 24 hours + the 11-hour daily rest, per 7-day period | Hard statutory | Same Directive, Art. 5 |
| US municipal fair-workweek ("clopening" laws) | 10h (Los Angeles City/County FWWO) to 11h (Berkeley, Emeryville) | **Soft**: shift may still be scheduled with written consent, but triggers a premium-pay penalty (time-and-a-half in LA) if under the threshold | LA FWWO; Berkeley/Emeryville municipal ordinances — no California statewide law |
| Philippines | **No single statutory figure.** The Labor Code (Book III, Arts. 83–96) regulates via 8-hour-day limits, Art. 91's 24-hour weekly rest after 6 consecutive days, and Art. 86's 10% night-shift differential (22:00–06:00) — it does not set a shift-to-shift gap number | Not codified; **10–12 hours** repeatedly cited as industry/commentary good practice (a "safety and longevity" recommendation, not law) | Respicio & Co. commentary (twice, independently phrased); no DOLE advisory found specific to BPO/IT-BPM rest gaps |

### How this maps onto v1.5's already-settled design

PROJECT.md has already settled minimum rest as a **per-desk hard constraint, refused pre-solve, unset
by default (inert on any desk that configures nothing)**. This research confirms that shape is the
right generalization across all three tiers found, rather than a one-size-fits-all number:

- An **EU/UK desk** can set 11h and get statutory-strength enforcement — stricter than the US municipal
  pattern (which is soft-with-consent-and-pay), matching the character of EU law (hard, no override).
- A **US-jurisdiction desk** could set 10–11h to match the relevant municipal ordinance, though this
  project has no premium-pay/consent-override subsystem and isn't building one (see Anti-Features) — the
  hard-block behaviour is *stricter* than the ordinance requires (the ordinance permits the short-rest
  shift with consent + pay; this project simply refuses it), which is a defensible, conservative choice
  but worth naming explicitly to the operator, since it means a US desk configured this way could refuse
  a schedule pattern that would be legal (with premium pay) under local law.
- **Phil-US**, the one live desk in a jurisdiction with no statutory figure, is exactly the case the
  per-desk-configurable, no-baked-in-default model is for: the operator sets whatever the client/company
  policy specifies (10–12h per the industry-practice range found), and an unconfigured desk is
  unaffected — correctly reflecting that there is no legal minimum being silently assumed.

### How it's surfaced

Every domain and jurisdiction researched treats a rest violation as either invisible (no system
enforcement, human judgement only) or **soft** (warn/consent + financial penalty). This project's
decision to make it **hard, pre-solve-refused, naming the agent and both shifts** — mirroring the
existing ENVL-07 refusal pattern for SHIFT-mode infeasibility — is stricter than everything found in
this research. That is a deliberate, already-settled choice (PROJECT.md: "not for unmet requirements...
minimum rest is HARD"), not a gap this research is flagging — restated here only so the roadmap has the
comparative grounding for why it's the right call rather than an arbitrary one.

## Mixed Day/Night Desks — A Known Usability Problem, Addressed

**This is real, not hypothetical.** A desk running both a `09:00` day shift and a `21:00` night shift,
anchored on `21:00`, produces a genuinely disorienting mapping: business-day "Tuesday" spans calendar
Tuesday 21:00 through calendar Wednesday 21:00 — so the `09:00` shift that a human would call
"Wednesday's day shift" is stored, blocked-by-PTO, and reported against **business-day Tuesday**, not
Wednesday. An operator reading a bare business-date label without context will misread it.

**Precedent from research: real products resolve this with one global anchor per operating unit plus
explicit calendar-span labelling — not with per-shift-type rules.**

- **Hospitality PMS is the closest working analogue.** A hotel's "business date" is a single anchor for
  the entire property (rolled at night audit, commonly ~2am), applied uniformly to every department and
  every guest transaction regardless of what's actually happening at 1am — check-ins, restaurant POS,
  and room charges all resolve to the same business date. Hotels do **not** give the restaurant one
  cutover and housekeeping another. This directly validates this project's already-settled decision
  (one desk-level anchor, not a per-shift-type or per-template flag) as the industry-consistent choice,
  not an arbitrary simplification.
- **The mitigation hotels use is UI/labelling, not architecture.** Guest-facing surfaces never show the
  raw rolled business date; staff-facing PMS screens do, and staff are trained on the convention (with
  a folio's charges from "1am" explicitly still reading as "yesterday"). This project's operator-facing
  surfaces are the PMS-staff equivalent — the fix is **not** to invent a second day-boundary mechanism
  for the day-shift-like agents, but to make the business day's calendar span visible everywhere it's
  displayed.

**Concrete recommendation for the roadmap:** every UI surface that shows a business-date label (roster
grid header, drift report, usual-shift display, coverage report) should render the calendar span
alongside it wherever the desk's anchor is non-midnight — e.g. "Business Day: Tue 21:00 – Wed 21:00,"
not a bare "Tuesday." This is cheap (a label-formatting concern, not a data-model one) and directly
follows the hotel precedent: the confusion is real, well-precedented, and solved by disclosure, not by
re-litigating the single-anchor decision. Flag this explicitly as a UI requirement candidate for the
OVNT/BDAY phase — PROJECT.md's current v1.5 feature list does not call it out, and it is exactly the
kind of gap that becomes a support-ticket generator if the day-shift agents' data appears to be
"misfiled" under the wrong date to an operator who hasn't internalised the anchor.

**Why Phil-US is deferred rather than being the proof case, and why that's correct here too:** Phil-US
is precisely this shape — multiple night-shift starts (`2100`, `2200`, `2300`, `0000`) plus, in its real
target state, day-adjacent coverage — which is exactly why PROJECT.md defers its migration to the
milestone *after* this one. Solving the mixed-desk labelling problem in the abstract (constructed test
cases) before attempting it on the one live desk that actually has the shape is the right order, and
matches this milestone's own stated inversion of v1.4's mistake (prove correctness on constructed cases
chosen by the property under test, not on under-powered live data).

## MVP Definition

### Launch With (v1.5 — matches PROJECT.md's own stated target features)

- [ ] Desk-level day-start anchor, defaulting to `00:00` (zero behaviour change for every desk that
      doesn't opt in) — the foundational, sequencing-critical piece
- [ ] `DayWindow` re-anchored on day-start, with guard tests landing **before** the re-anchor, not after
- [ ] Business date populated on every timeslot at generation
- [ ] Overnight shift templates (end < start, correct net hours), extending the existing template
      library — day-off/PTO blocking and contracted-hours consumption against the starting business day
      require no schema change
- [ ] Solver business-date correctness across all 12 `timeslot.getDate()` joins, plus the silent-non-join
      guard test
- [ ] SLOT-mode overnight correctness (pre-existing under-allocation defect, independent of new
      overnight-template capability)
- [ ] Minimum rest, hard, per-desk, pre-solve-refused, naming the agent and both shifts — inert on any
      desk that configures nothing
- [ ] Schedule grid renders one continuous block; Excel export places the full span under the starting
      day's column
- [ ] Constructed midnight-spanning regression scenarios chosen by the property under test; Phil-US as a
      small live drift guard only

### Add After Validation (v1.x — once the re-anchor is proven live)

- [ ] Business-day calendar-span labelling on operator-facing surfaces (roster grid header, drift
      report, usual-shift display) for any desk with a non-midnight anchor — cheap, UI-only, addresses
      the mixed day/night usability finding above; not currently in PROJECT.md's v1.5 list and worth
      explicit roadmap consideration rather than silent omission
- [ ] Explicit verification that demand/staffing upload (Erlang calculator) and coverage/headcount
      reporting agree on the same business-date anchor — flagged above as a candidate gap in the current
      scope, not yet covered by a named guard the way the solver-join defect is

### Future Consideration (v2+ — explicitly out of this milestone's scope, per PROJECT.md)

- [ ] Phil-US migration to its real client shift pattern (`2100-0600` / `2200-0700` / `2300-0800` /
      `0000-0900`), with a tested reversal — deliberately deferred so this capability stays desk-agnostic
- [ ] Timezone-aware day-start anchoring — stays the operator's pre-load job indefinitely by design
- [ ] Premium-pay / consent-to-override machinery for minimum-rest violations — no payroll subsystem
      exists anywhere in this project to hang it off

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|----------------------|----------|
| Desk-level day-start anchor + `DayWindow` re-anchor | HIGH (foundational) | HIGH (breadth: 112 refs / 16 files) | P1 |
| Business date on every timeslot | HIGH (foundational) | LOW–MEDIUM | P1 |
| Overnight shift templates (end < start, correct hours) | HIGH | LOW–MEDIUM (extends existing library) | P1 |
| Day-off/PTO + contracted-hours attribution to starting business day | HIGH | LOW (no schema change) | P1 |
| Solver business-date correctness + non-join guard test | HIGH (silent-failure risk) | MEDIUM | P1 |
| SLOT-mode overnight correctness | MEDIUM–HIGH (pre-existing defect) | MEDIUM | P1 |
| Minimum rest, hard, pre-solve refused | HIGH (regulatory exposure) | MEDIUM–HIGH | P1 |
| Schedule grid: one continuous block | HIGH (table stakes UX) | LOW–MEDIUM | P1 |
| Excel export: span under starting day | HIGH (table stakes UX) | LOW–MEDIUM (partially started) | P1 |
| Coverage/headcount reporting by business date | MEDIUM–HIGH | MEDIUM | P1 |
| Constructed midnight regression fixtures + Phil-US drift guard | HIGH (proof strategy) | MEDIUM | P1 |
| Business-day calendar-span labelling on operator surfaces | MEDIUM (usability, real but not blocking) | LOW | P2 |
| Demand-vs-coverage anchor-agreement guard test | MEDIUM (silent-disagreement risk) | LOW–MEDIUM | P2 |
| Phil-US real-shift migration | HIGH (eventually) | HIGH (live desk, needs tested reversal) | P3 (next milestone) |
| Timezone-aware anchoring | LOW (deliberately out) | HIGH | P3 (not planned) |
| Premium-pay override for rest violations | LOW (no payroll system to attach to) | HIGH | P3 (not planned) |

**Priority key:**
- P1: Must have for v1.5 launch — matches PROJECT.md's stated target features
- P2: Should have, surfaced by this research as gaps not yet named in PROJECT.md's list — flag to
  roadmapper for explicit inclusion or explicit deferral, not silent omission
- P3: Explicitly deferred — do not schedule against this milestone

## Cross-Domain Terminology and Precedent Analysis

| Domain | Term Used | Boundary Placement | Attribution Rule |
|--------|-----------|---------------------|-------------------|
| Contact-centre WFM (this project) | "Business date" | Desk-configurable day-start (settled) | Start-day |
| Hospitality PMS | "Business date" | Fixed per-property cutover, commonly ~2am (night audit) | Start-day (all transactions before cutover belong to the date being closed) |
| Gaming / casinos | "Gaming day" | Fixed cutover, commonly 6am | Start-day |
| Healthcare / nurse rostering | "Shift date" | Shift's own start instant | Start-day (a 22:00–06:00 shift is dated by its 22:00 start even though most transactions/entries land after midnight) |
| Rail / transport crew rostering | "Duty day" | Sign-on instant | Start-day (regulatory rest is measured from sign-off to next sign-on, not by calendar date) |
| Retail / general commerce | "Trading day" | Close-of-business event | Start-day, functionally identical to the PMS pattern |
| Payroll / timesheet systems (a different layer) | Varies; sometimes "pay period date" | Punch-out instant, or a majority-hours split | The **only** domain where non-start-day attribution (majority-hours, or calendar-split) appears at all — and it answers a different question (wage/tax period apportionment), not "which roster day does this shift belong to" |

**Conclusion: start-day attribution is the overwhelming, near-universal norm for the question this
milestone is actually asking** ("which day does a night shift belong to, for scheduling/PTO/coverage
purposes"). The only domain where alternative attribution (majority-hours or end-day) appears is payroll
wage-period apportionment — a lower, separate layer this project does not operate (no payroll
integration exists in this system). This strongly confirms PROJECT.md's already-settled start-day
attribution as not just a reasonable implementation choice but the domain-standard one, with no credible
alternative surfaced by this research.

## Sources

- [EU-OSHA — Directive 2003/88/EC (Working Time)](https://osha.europa.eu/en/legislation/directives/directive-2003-88-ec)
- [Your Europe (European Commission) — Working hours in the EU: minimum standards](https://europa.eu/youreurope/business/human-resources/general-employment-terms-conditions/working-hours/index_en.htm)
- [Wikipedia — Working Time Regulations 1998 (UK)](https://en.wikipedia.org/wiki/Working_Time_Regulations_1998)
- [Traliant — California Scheduling Requirements](https://resource.traliant.com/ca-state-scheduling-requirements/)
- [Join Homebase — Predictive Scheduling Laws by State and City](https://www.joinhomebase.com/blog/predictive-scheduling-laws)
- [Ogletree — Los Angeles Predictable Scheduling Law](https://ogletree.com/insights-resources/blog-posts/los-angeles-predictable-scheduling-law-set-to-take-effect/)
- [Ogletree — LA County Predictable Scheduling Ordinance](https://ogletree.com/insights-resources/blog-posts/los-angeles-countys-predictable-scheduling-ordinance-will-take-effect-on-july-1-2025/)
- [TimewaveHR — "Clopening" (CA Labor Law Glossary)](https://timewavehr.com/glossary/clopening)
- [Chanrobles — Labor Code of the Philippines, Book Three](https://chanrobles.com/legal4labor3.htm)
- [DOLE — Book 3, Conditions of Employment](https://dole.gov.ph/book-3-conditions-of-employment/)
- [Respicio & Co. — Legal Rest Period Between Shifts Philippines](https://www.respicio.ph/commentaries/legal-rest-period-between-shifts-philippines)
- [Respicio & Co. — Mandatory Rest Periods and Gap Between Work Shifts under DOLE](https://www.respicio.ph/commentaries/mandatory-rest-periods-and-gap-between-work-shifts-under-dole)
- [Respicio & Co. — Labor Standards on Work Hours and Breaks in the Philippines](https://www.lawyer-philippines.com/articles/labor-standards-on-work-hours-and-breaks-in-the-philippines)
- [PayrollCenter.ph — Managing Shifting Schedules and Rest Days under Philippine Labor Code](https://payrollcenter.ph/blog/employee-schedules.html)
- [Sprout Solutions — Night Differential in the Philippines](https://sprout.ph/articles/night-differential-philippines/)
- [hospemag — Night Audit: The Ultimate Hospitality Deep Dive](https://hospemag.squarespace.com/hospitality-terminology/night-audit/deep-dive)
- [SetupMyHotel — Hotel Night Audit / End Of Day Process](https://setupmyhotel.com/hotel-staff-training/front-office-training/hotel-night-audit-end-of-day-process-hotels-resorts/)
- [e360hospitality — Hotel Night Audit and the Night Auditor](https://e360hospitality.com/hotel-glossary/hotel-night-audit-and-the-night-auditor/)
- [Factorial — How to manage overnight shifts](https://help.factorialhr.com/en_US/shift-management/how-to-manage-overnight-shifts)
- [Springer — Staff rostering for the station personnel of a railway company](https://link.springer.com/article/10.1057/jors.2009.48)
- [Springer Nature Link — Rail Crew Scheduling and Rostering Optimization Algorithms](https://link.springer.com/chapter/10.1007/978-3-642-56423-9_4)
- [BMC Nursing — Nurse rostering: current shift work scheduling processes](https://bmcnurs.biomedcentral.com/articles/10.1186/s12912-024-01949-2)
- Project context: `/Users/pete/IdeaProjects/wfm-service/.planning/PROJECT.md`
- Prior-milestone precedent for research depth/format: `/Users/pete/IdeaProjects/wfm-service/.planning/milestones/v1.3-research/FEATURES.md`

---
*Feature research for: overnight shifts and business-date attribution (v1.5)*
*Researched: 2026-09-30*
