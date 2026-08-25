# Feature Research

**Domain:** Contact-centre workforce management — shift-based scheduling and schedule consistency
**Milestone:** v1.3 Shift-Based Scheduling & Consistency (adds shift-level scheduling to an existing live slot-based WFM app)
**Researched:** 2026-08-25
**Confidence:** MEDIUM (industry-wide web sources on vendor UX/behaviour, no vendor trial access; regulatory claims cross-checked across multiple sources; no primary-source vendor documentation was fetched beyond search snippets)

This research covers **only the new v1.3 surface**: shift libraries, usual-shift storage, the consistency
constraint, drift reporting, and the fairness/regulatory questions that surround them. It does not
re-cover desk management, staffing demand upload, BambooHR sync, breaks-as-hard-constraints, or the
solver core — all already built (see PROJECT.md "Current State").

## Feature Landscape

### Table Stakes (Operators Expect These)

Features every established WFM/scheduling tool (NICE CXone, Genesys WFM, Verint, Calabrio, Deputy,
When I Work, Shiftboard) has in some form for shift-based scheduling. Missing these makes the shift
model feel like a toy next to the slot model it replaces.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Named shift definitions with start/end time and total paid duration | Every scheduling tool's atomic unit is a shift with a start, end, and duration operators can read at a glance (`08:00–17:00`) — not raw slot ranges | LOW | Already implied by the milestone scope; this is the shift-library row itself |
| Break placement rule per shift (fixed offset, or window + duration) | Bright Pattern, Genesys, and NICE all attach break rules to the shift template, not to the agent — this project already has break hard constraints (duration, blocked window, alignment) at the slot level; the shift template needs to *carry* those parameters so a shift's break isn't independently configured per agent | LOW–MEDIUM | Directly reuses existing break-constraint machinery — the shift template becomes the parameter source instead of a global/desk default |
| Per-desk shift library (not global) | Desks already model contracted hours and specializations per desk; a global shift list would contradict the existing per-desk model and would not let a pilot desk diverge from the rest | LOW | Matches the milestone's own "per-desk mode switch" design |
| Weekday validity on a shift (which days a shift can be assigned) | `agent_day_hours` is already Mon–Sun; a shift with no weekday restriction can't express "S1 only runs weekdays, S2 only weekends" which is normal contact-centre practice | LOW | Natural extension of existing per-weekday model — do not treat as a stretch feature |
| Effective date range on a shift (start/end validity) | Shift libraries change over time (seasonal shift patterns, contract renegotiation); every mature tool versions or date-bounds shift definitions rather than mutating history | MEDIUM | Table stakes in the *industry*, but genuinely optional for v1.3's first pilot desk — flag as deferrable if the desk's shift set is stable; becomes load-bearing the moment an operator edits a shift definition after schedules exist against it |
| Recurring/reusable templates (define once, apply across weeks) | This is the entire premise of "shift library" vs. "shift instance" — every tool distinguishes the reusable *template* from a scheduled *occurrence* | LOW | Already the milestone's stated model: solver "picks one per agent-day from that library" |
| Skill/specialization restriction at the shift level (which desk specializations a shift is valid for) | Ambiguous by design in this project: the milestone explicitly keeps specialization assignment *inside* the shift envelope, per-slot, so an agent can change specialization mid-day. This is a deliberate departure from the vendor norm (where shift templates commonly gate which skill groups can use them) | LOW (as scoped) | Do not import the vendor pattern of "shift restricted to skill X" — it would conflict with the milestone's own architecture decision. If ever needed, it is a *desk*-level shift-set restriction, not a slot-level one |
| Minimum/maximum simultaneous staffing on a shift | Vendor pattern (Bright Pattern) ties shift templates to activity capacity; this project's staffing demand is already expressed independently via the FTE-per-timeslot upload, which the solver already honours | N/A — not needed | This is a genuine case of "already solved elsewhere" — do not duplicate demand modelling inside the shift template. Flag explicitly to phase planners so nobody re-invents it |

### Differentiators (This Milestone's Actual Value)

These are not standard shift-library plumbing — they are the two things the milestone exists to
deliver, and they are genuinely differentiated even against mature vendors.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Stored "usual shift" per agent per weekday, targeted (not just observed) by the solver | Most vendor tools derive "usual" from history/reporting after the fact (adherence trending) or from a rigid fixed-shift assignment; storing it as a **first-class planning target the optimizer actively steers toward**, while still allowing weekly variation, is closer to a hybrid of "fixed shift" and "preference-optimized" than either pure model | MEDIUM–HIGH | This is the load-bearing new entity. Directly parallels the existing `agent_day_hours` per-weekday model — same shape, same UX pattern (upload column + inline roster edit), which the milestone plan already commits to. Reuse that precedent deliberately |
| Soft consistency constraint with per-desk tolerance band + weight | No major vendor exposes "distance from usual shift" as a tunable soft constraint with an explicit tolerance band the way this milestone proposes; most tools treat "keep the same shift" as either a hard fixed-shift assignment or an emergent property of not re-solving from scratch. A **graduated, weighted, tolerance-banded** soft constraint is more expressive than the binary "fixed vs. rotating" distinction the industry generally uses | MEDIUM–HIGH (solver-side: new soft constraint stream; needs care given Timefold 1.16.0 pin and the two prior abandoned attempts — `BreakAwareConstructionPhase`, Phase 12 Atomic Shift Move) | This is where the milestone's own "Central architectural question" (shift as envelope vs. hard-coupled second planning variable) actually bites. Treat the tolerance band as minutes-of-drift-before-penalty, weight as desk-level solver tuning — mirrors "configurable adherence threshold" pattern vendors use for adherence *reporting*, applied instead to scheduling *itself* |
| Contiguity by construction (shift-scheduled desks cannot produce fragmented days) | Directly answers the milestone's own stated motivation — two prior slot-based attempts at shift-like behaviour were abandoned because penalizing fragmentation after the fact doesn't work as well as making it structurally impossible | MEDIUM | This is an emergent property of the shift-as-planning-unit model, not a separate feature to build — but phase planners should treat "can the solver even construct a fragmented day on a shift-scheduled desk" as an explicit verification criterion, not an assumption |
| Drift report (who broke usual shift, when, by how much) | Vendor tools have adherence reporting (scheduled vs. *actual worked*) but that is a different axis — it compares the schedule to clock-in/out reality. This milestone's drift report compares the **solved schedule** to the **stored usual-shift target**, i.e., it is a *planning-quality* report, not a *time-and-attendance* report | LOW–MEDIUM | Cheap once the usual-shift model and consistency constraint exist — it is largely a read-side view over the same distance calculation the soft constraint already computes. Do not conflate with real-time adherence (out of scope; this project has no clock-in data at all) |
| Per-desk mode switch (shift-scheduled vs. slot-scheduled) | Enables a live pilot on one desk without touching the others and gives a structural fallback if the shift model underperforms — this is explicitly the milestone's own risk-management device, not a vendor pattern being copied | LOW–MEDIUM | Already-built desk model already scopes settings per desk (specializations, contracted hours); this is one more per-desk toggle, not a new subsystem. The complexity is downstream — the solver and reporting surfaces need to branch on it cleanly |

### Anti-Features (Do Not Build This Milestone)

Specific, not generic. Each of these is something a real vendor either builds and regrets, or that
looks like an obvious next step from this milestone's shape but is a different problem the milestone
correctly excludes.

| Feature | Why It Looks Right | Why It's Wrong Here | Alternative |
|---------|--------------------|---------------------|-------------|
| Full agent-facing shift bidding (Genesys EE11 / NICE CXone style: agents rank Desired/Undesired shifts, priority allocation by seniority/rank/submission order) | The milestone stores a "usual shift" per agent — bidding is the industry-standard way vendors *populate* that kind of field, and it looks like the natural mechanism | This project has no agent-facing UI at all — schedules are built by operators for agents, not self-service. Building a bidding UI, ranking model, and seniority/tie-break allocation algorithm is a multi-week feature in its own right that the milestone never asked for, and it would sit awkwardly next to the *operator*-owned upload/roster-edit population path the milestone explicitly specifies | Usual shift is operator-set (upload column + inline roster edit), matching the `agent_day_hours` precedent exactly. If genuine agent self-service is ever wanted, it is a separate future milestone, not a checkbox on this one |
| Real-time adherence monitoring (scheduled-vs-actual-clock-time, live dashboards, alert thresholds) | "Drift report" sounds adjacent to "adherence report" and vendors treat them as one product area | This project has no time-and-attendance / clock-in data source at all. The drift report this milestone needs compares two *planned* artefacts (solved schedule vs. usual-shift target), not planned-vs-actual-worked. Building real-time adherence would require an entirely new data source this project doesn't have | Keep drift strictly as a planning-time report: usual shift vs. solved shift, both known before the schedule is published. If real adherence tracking is ever wanted, it needs a time clock integration first — out of scope indefinitely |
| Automatic periodic reshuffle / rotation engine to distribute desirable shifts fairly over time | The fairness tension is real (see below) and vendors do build automated rotation to solve it | This milestone's own scope explicitly excludes fairness (Backlog 999.4, QUAL-02/03, is deferred to a future milestone) and doing rotation properly requires exactly the fairness constraints that were deliberately punted. Building a rotation engine now means solving two hard problems (consistency *and* fairness) at once, which is precisely the kind of scope creep the project's own history warns against (Phase 12 was withdrawn for overreaching relative to what could be verified) | Flag the tension explicitly to the operator (see below) and leave rotation for the milestone that also does QUAL-02/03. Do not let "fairness" quietly attach itself to "consistency" — they are related but separably-scoped problems |
| Shift-level skill/queue restriction (shift template says "only skill X may use this shift") | This is the vendor-standard pattern (Bright Pattern, Genesys) and looks like natural shift-template richness | The milestone's own architecture decision keeps specialization assignment *inside* the shift envelope, per-slot — an agent can change specialization mid-shift. A shift-level skill restriction would contradict that design and reintroduce the coupling problem the milestone's "central architectural question" is trying to avoid | If a desk genuinely needs to restrict which shifts apply to which agents, do it via desk-level shift-set membership (which shifts exist on this desk), not skill-gating individual shifts |
| Minimum/maximum staffing caps embedded in the shift template | Vendor pattern; looks like it belongs next to break rules and weekday validity | Staffing demand is already a fully separate, already-built subsystem (FTE-per-timeslot Excel upload, already honoured by the solver). Embedding a second, competing staffing signal inside the shift template creates two sources of truth for the same concept | Leave staffing demand exactly where it is. The shift library defines *what an agent-day can look like*, not *how many agents are needed* |
| Fine-grained predictive-scheduling compliance machinery (predictability pay calculation, automated 14-day posting lock, right-to-rest enforcement) | The regulatory research surfaced real, well-documented laws (Oregon, Seattle, NYC, Chicago, Philadelphia, SF, and others) with specific advance-notice and penalty-pay mechanics | This is a single internal EU/UK-based tenant (Helpware) scheduling contact-centre desks — none of the cited predictive-scheduling ordinances are US-jurisdiction-specific and none apply extraterritorially to an EU/UK operation. Building compliance machinery for laws that do not apply to this deployment is pure waste | See Regulatory Note below — do not build anything from this list. If Helpware ever schedules US-jurisdiction staff subject to these ordinances, that is new information requiring new research, not something to pre-build speculatively |

## Feature Dependencies

```
Desk shift library (shift templates: start/end, break rule, weekday validity)
    └──requires──> Per-desk mode switch (shift-scheduled | slot-scheduled)
                       └──enhances──> Existing per-desk specialization/contracted-hours model (already built)

Stored usual shift (per agent, per weekday)
    └──requires──> Desk shift library (usual shift must reference a valid shift-library entry)
    └──mirrors───> agent_day_hours per-weekday model (already built) — same population UX (upload column + roster inline edit)
    └──overlaps──> AgentPreference.preferredStartTime (already built) — see explicit resolution below

Consistency constraint (soft, tolerance band + weight, per desk)
    └──requires──> Stored usual shift
    └──requires──> Desk shift library
    └──conflicts (partially)──> Existing break hard constraints if shift-carried break rule disagrees with per-slot break constraint parameters — must reconcile, not duplicate

Drift report (who broke usual shift, when, by how much)
    └──requires──> Consistency constraint (reuses its distance calculation)
    └──requires──> Solved schedule (already produced by existing solver run)

Contiguity by construction
    └──emergent from──> Shift-as-planning-unit model (not a separately built feature)

Fairness / rotation (explicitly NOT this milestone)
    └──conflicts──> Consistency constraint, if built without acknowledging the tension
    └──deferred to──> Backlog 999.4 (QUAL-02/03)
```

### Dependency Notes

- **Consistency constraint requires stored usual shift and shift library:** there is nothing to measure
  distance from until both exist. This fixes phase ordering — shift library and usual-shift storage
  must land before the consistency constraint can be built or tested.
- **Usual shift mirrors `agent_day_hours`:** the milestone's own plan already commits to reusing the
  proven "upload column + inline roster edit" dual-population pattern from v1.2's contracted-hours
  work. Treat this as validated precedent, not a new UX decision requiring research.
- **Consistency constraint's break-rule dependency on existing hard constraints:** the shift template
  needs to supply parameters (break duration, blocked window, start alignment) *to* the existing break
  hard constraints rather than defining a parallel break system. This is a reconciliation point phase
  planners must make explicit — get it wrong and the shift template's break rule and the slot-level
  break constraint can silently disagree, producing schedules that are internally inconsistent about
  where the break actually falls.
- **Drift report depends on, not duplicates, the consistency constraint's distance calculation:** build
  the distance-from-usual-shift computation once, use it both to penalize (soft constraint) and to
  report (drift panel). Two independent implementations of "how far did this agent drift" is a bug
  waiting to happen.
- **Fairness conflicts with consistency if unaddressed:** enforcing consistency without any fairness
  mechanism structurally *causes* the "some agents keep desirable shifts permanently" problem (see
  below). This is a genuine conflict, not just an unbuilt nice-to-have — the roadmap should carry it
  forward as a flagged, acknowledged design debt rather than silently deferring it the way Phase 6's
  QUAL-02/03 was silently dropped and "nearly lost" (per PROJECT.md's own Key Decisions log). Say it
  out loud in the roadmap this time.

## The `AgentPreference` Overlap — Addressed Directly

This project already has `AgentPreference` (`preferredStartTime`, `preferredBreakTime`; standing or
dated) honoured as a **soft** solver constraint. The new "usual shift per agent per weekday" is a
second, structurally different thing storing a similar-sounding signal, and the overlap needs to be
resolved explicitly rather than left ambiguous:

- **`AgentPreference.preferredStartTime` is a *continuous* value** (any start time within the day) and
  is **agent-desired** — in the industry pattern this maps to what vendors call a preference: something
  the optimizer tries to honour but that carries no structural weight beyond the soft-constraint score.
  It answers "what would this agent *like*."
- **Usual shift is a *discrete, catalog* value** (one of the desk's defined shift-library entries) and
  is **operator-set, planning-target** data — closer to what the industry treats as a semi-fixed shift
  assignment. It answers "what does this agent *normally work*," which is a claim about the schedule's
  shape, not the agent's wishes.
- **They are not the same axis and should not be merged into one field.** An agent's preferred start
  time might drift slightly week to week (they'd like to start a bit earlier on Mondays) while their
  usual shift stays `S1` throughout — the preference is a fine-grained nudge, the usual shift is the
  coarse-grained target the consistency constraint measures distance from.
- **The genuine risk is double-counting or contradiction**, not conceptual confusion: if an agent's
  `preferredStartTime` sits outside their usual shift's start time, the solver now has two soft signals
  pulling in different directions with no defined precedence. This needs an explicit tie-break rule
  (e.g., usual-shift consistency and preference satisfaction are separate constraint streams with
  separate, operator-visible weights — do not silently fold preference into the usual-shift target or
  vice versa). Flag this as a genuine open design question for phase planning, not something research
  can resolve in the abstract.
- **Practical implication for the shift library / usual-shift UI:** when an operator sets an agent's
  usual shift to `S1 (08:00–17:00)`, the existing `preferredStartTime` field should probably be treated
  as informational context shown alongside it (e.g., "agent's stated preference: 08:30 — usual shift
  starts 08:00, 30min off"), not silently overwritten or silently ignored. That surfacing is cheap and
  avoids the two systems appearing to contradict each other with no explanation.

## Fairness Interaction — Flagged as a Design Question, Not Solved Here

Every established vendor that supports "keep agents on the same shift" also has to deal with the
predictable consequence: whoever locks in a desirable shift first (or whose usual shift happens to be
attractive) keeps it indefinitely once a strong consistency weight is in play. The industry's typical
mitigations, roughly in order of maturity:

1. **Seniority-based bidding** — desirable shifts allocated by tenure; simplest to explain, well
   understood in unionized/long-tenure workplaces, but ossifies over time and locks out newer hires
   from ever getting good shifts.
2. **Rotating priority** — the order in which agents get first choice rotates on a schedule, so no one
   is permanently last.
3. **Points-based bidding** — everyone gets equal "currency" to bid with periodically, spendable on
   desirable shifts, refreshed on a cadence.
4. **Hybrid allocation** — a percentage of premium shifts reserved for seniority, the rest rotated or
   bid.
5. **Periodic reshuffle** — usual-shift assignments are deliberately re-set on a cadence (quarterly,
   semi-annually) rather than left to calcify indefinitely.

**This milestone should not build any of these.** It has no shift-bidding UI, no agent-facing surface,
and fairness (QUAL-02/03) is explicitly deferred to Backlog 999.4. The correct scope here is narrower:

- **Do** make the tension visible to the operator setting usual shifts — e.g., a simple view or report
  showing which shifts are over-subscribed as "usual" across agents, so an operator manually notices
  "6 agents all have `S1` as usual, `S3` has none" before it becomes an entrenched pattern.
- **Do not** build any automated rotation, bidding, or reshuffle mechanism this milestone — that is
  Backlog 999.4's job, and building it now would repeat the exact overreach pattern the project's own
  history explicitly warns against (Phase 12 was withdrawn specifically because it tried to solve a
  problem — cross-agent seat displacement — the benchmark couldn't show it addressed).
- **Do** record the design question in the roadmap explicitly, by name, so it does not suffer the fate
  PROJECT.md itself documents for QUAL-02/03: "narrowed... and never re-homed... nearly lost."

## Drift / Exception Reporting — What Real Tools Show and What Operators Do With It

Industry pattern from adherence reporting (the closest analogue, even though it measures a different
axis — actual clock time vs. schedule, not usual-shift target vs. solved schedule):

- **Configurable tolerance, not binary pass/fail.** Mature tools learned the hard way that treating
  every deviation as non-adherence, regardless of size, "inflates non-adherence metrics and generates
  alert fatigue." The pattern that survived is a tolerance window in minutes, below which a deviation
  doesn't even surface. This directly validates the milestone's own design (operator-configurable
  tolerance band per desk) — it is not a nice-to-have, it is the thing that keeps the drift report
  usable rather than noisy.
- **Real-time vs. historical split doesn't apply here** — this project's drift report is inherently a
  planning-time artefact (usual shift vs. solved schedule, both known before publication), not a
  live monitoring feed. Don't import the real-time dashboard pattern; it answers a question this
  project doesn't have the data to ask (no clock-in feed).
- **What operators do with the report, per industry pattern:** primarily two things — (1) decide
  whether to accept the solved schedule as-is (drift is a cost the operator weighs against coverage,
  same trade-off the consistency weight already encodes, just made visible per-agent), and (2) use
  repeated/large drift on a specific agent as a signal that the *stored usual shift* itself may be
  wrong or stale, prompting an update to the usual-shift record rather than fighting the solver every
  week. The drift report is as much a "is our usual-shift data still accurate" surface as it is a
  "how well did the solver honour it" surface — worth stating explicitly in the report's own framing,
  since it's easy to build it as pure solver-diagnostics and lose the second use case.

## MVP Definition

### Launch With (v1.3 — matches the milestone's own stated target features)

- [ ] Per-desk shift library — start/end time, break placement rule, weekday validity — reusing
      existing break hard-constraint parameters rather than duplicating them
- [ ] Stored usual shift per agent per weekday, populated via upload column + inline roster edit
      (mirroring `agent_day_hours` UX exactly)
- [ ] Consistency soft constraint with per-desk tolerance band and weight
- [ ] Per-desk mode switch (shift-scheduled vs. slot-scheduled), defaulting existing desks to
      slot-scheduled (no behaviour change without opt-in)
- [ ] Drift report — usual shift vs. solved schedule, per agent, per date, with the same tolerance
      framing as the constraint itself
- [ ] Explicit, documented (not built) resolution of the `AgentPreference` vs. usual-shift precedence
      question, surfaced to the operator when the two disagree

### Add After Validation (v1.x — once the pilot desk proves the shift model out)

- [ ] Effective date range on shift-library entries (versioning shift definitions over time) — defer
      unless the pilot desk needs to change its shift set mid-flight
- [ ] Visibility into "which shifts are over-subscribed as usual" across agents, to make the fairness
      tension observable to the operator without building any automated mitigation

### Future Consideration (v2+ — explicitly out of this milestone's scope)

- [ ] Any form of shift bidding, rotation, or automated reshuffle — belongs with Backlog 999.4
      fairness work, not bolted onto consistency
- [ ] Real-time adherence (scheduled-vs-actual-clock-time) — requires a time-and-attendance data
      source this project does not have
- [ ] Shift-level skill/queue restriction — would contradict the milestone's own architecture decision
      to keep specialization assignment inside the shift envelope

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Per-desk shift library (start/end, break rule, weekday validity) | HIGH | LOW–MEDIUM | P1 |
| Stored usual shift per agent per weekday | HIGH | MEDIUM | P1 |
| Consistency soft constraint (tolerance + weight) | HIGH | MEDIUM–HIGH | P1 |
| Per-desk mode switch | HIGH | LOW–MEDIUM | P1 |
| Drift report | MEDIUM–HIGH | LOW–MEDIUM | P1 |
| AgentPreference/usual-shift precedence surfacing | MEDIUM | LOW | P1 (cheap, avoids silent contradiction) |
| Effective date range on shift templates | MEDIUM | MEDIUM | P2 |
| "Over-subscribed usual shift" visibility (fairness signal, no mitigation) | MEDIUM | LOW | P2 |
| Shift bidding / rotation / reshuffle | LOW (for this deployment — single internal tenant, operator-driven) | HIGH | P3 (Backlog 999.4 territory) |
| Real-time adherence monitoring | LOW (no data source exists) | HIGH | P3 (not planned) |

**Priority key:**
- P1: Must have for v1.3 launch
- P2: Should have, add once the pilot desk validates the model
- P3: Explicitly deferred — do not schedule against this milestone

## Competitor / Vendor Feature Analysis

| Feature | Genesys WFM | NICE CXone | Deputy / Shiftboard | This Project's Approach |
|---------|-------------|------------|----------------------|--------------------------|
| Shift definition | Daily + weekly templates composed together | Weekly shift patterns bid on by agents | Save-as-template from a built schedule; recurring weekly reuse | Per-desk shift library, single-level (start/end + break rule + weekday validity) — deliberately simpler than the daily/weekly composition vendors use, matching the project's flatter data model |
| "Usual"/preferred shift population | Agent-driven via bidding (ranked Desired/Undesired), allocated by rank/seniority | Agent-driven via bidding, allocated by rank then seniority then submission order | Not a first-class concept; templates are operator-applied | **Operator-driven**, via upload + inline roster edit — no agent-facing surface exists in this project, so bidding is architecturally inapplicable, not merely deferred |
| Consistency enforcement | Emergent from bidding outcome (agents got the shift they bid for) plus fixed-shift assignment options | Emergent from bidding outcome | Not a distinct concept — templates just get reapplied | **Explicit soft constraint** with tolerance band and weight — more directly tunable than any vendor's binary fixed/rotating distinction found in this research |
| Drift/deviation reporting | Adherence reporting (scheduled vs. actual clocked time) | Adherence reporting with configurable tolerance thresholds | Not typically present at this granularity | **Planning-time drift** (usual shift vs. solved schedule) — a genuinely different axis from vendor adherence reporting; explicitly not a clock-time comparison |
| Fairness for desirable shifts | Bidding is itself the fairness mechanism (rank + seniority) | Bidding + seniority + submission-order tie-break | Not typically addressed at this level | **Not solved this milestone** — flagged as a design question, deferred to Backlog 999.4 |

## Regulatory Note (Predictive Scheduling / Fair Workweek)

**Applicability: essentially none for this deployment, and the research should not be read as
alarmist about it.**

- The predictive-scheduling / fair-workweek laws that exist (Oregon statewide; city ordinances in
  Seattle, NYC, Chicago, Philadelphia, San Francisco, Los Angeles, Berkeley, Emeryville, Evanston) are
  **US state and municipal law**, triggered by employing covered workers *within those specific
  jurisdictions* (typically in retail, hospitality, and food service — contact-centre/BPO coverage
  varies by ordinance and is not universal even within the US).
- This deployment is **Helpware's single internal tenant, EU/UK-based** (per PROJECT.md: region
  `eu-west-2`/London, BambooHR-driven agent roster). Nothing in the research suggests any of the
  cited US ordinances apply extraterritorially, and there is no indication in PROJECT.md that this
  tenant schedules US-jurisdiction employees.
- **The EU angle is real but narrower than the US predictive-scheduling regime, and already
  structurally addressed:** the EU Working Time Directive (2003/88/EC) guarantees minimum daily rest
  (11 consecutive hours) and weekly rest (24 hours), but — per this research — **does not itself
  mandate advance schedule notice**; that is left to national implementation and the (separate)
  Transparent and Predictable Working Conditions Directive, which only requires "reasonable" notice
  for *inherently unpredictable* work patterns, not a fixed number of days. Predictability-pay-style
  penalty mechanisms found in the US ordinances have no EU equivalent in this research.
  - This project's shift model doesn't need new machinery for rest-period compliance specifically
    *because* of this milestone — that is a pre-existing consideration for any schedule the solver
    produces (11h daily / 24h weekly rest), already a matter for the desk's contracted-hours and
    break constraints rather than something the shift library changes.
- **Recommendation to phase planners:** do not build predictability pay, mandatory posting-lock
  windows, or right-to-rest enforcement machinery. If Helpware ever schedules staff in a covered US
  jurisdiction under this system, that is a new fact requiring its own scoped research — do not
  pre-build speculative compliance features against laws that don't currently apply.

## Sources

- [Bright Pattern — Creating Shift Templates](https://help.brightpattern.com/latest:Workforce-management/Creating%20Shift%20Templates)
- [Deputy Help Center — Shift templates](https://help.deputy.com/hc/en-au/articles/4688788027151-Shift-templates-U-S-only)
- [Deputy Help Center — Schedule templates](https://help.deputy.com/hc/en-au/articles/4688863723791-Schedule-templates)
- [Genesys Documentation — Shift Bidding (EE11)](https://all.docs.genesys.com/UseCases/Current/GenesysEngage-onpremises/EE11)
- [Genesys Documentation — Schedule Bidding](https://all.docs.genesys.com/PEC-WFM/Current/Supervisor/SchedBdg)
- [NICE CXone Help Center — Shift Bidding](https://help.nicecxone.com/Content/workforcemanagement/shiftbidding.htm)
- [myshyft.com — Seniority-Based Shift Bidding](https://www.myshyft.com/blog/seniority-based-bidding/)
- [myshyft.com — Fair Shift Distribution](https://www.myshyft.com/blog/fair-distribution-of-desirable-shifts/)
- [myshyft.com — Schedule Consistency Benefits](https://www.myshyft.com/blog/schedule-consistency-benefits/)
- [NICE — What is Schedule Adherence, and how to Measure it](https://www.nice.com/blog/wfm-the-value-of-measuring-schedule-adherence-2434)
- [AWS Contact Center Blog — Configure Schedule Adherence Thresholds in Amazon Connect](https://aws.amazon.com/blogs/contact-center/configure-schedule-adherence-thresholds-in-amazon-connect-to-account-for-operational-variances/)
- [Verint — What is Schedule Adherence and Why is it Important](https://www.verint.com/blog/what-is-schedule-adherence-and-why-is-it-important-in-the-call-center/)
- [Community WFM — Shift Bidding Process in Call Centers](https://blog.communitywfm.com/shift-bidding)
- [WFM Labs Wiki — Schedule Bidding and Preference Based Scheduling](https://wiki.wfmlabs.org/wiki/Schedule_Bidding_and_Preference_Based_Scheduling)
- [Calabrio — Contact Center Workforce Management](https://www.calabrio.com/products/workforce-management/)
- [Clockspot — Predictive Scheduling Laws by State](https://www.clockspot.com/research/predictive-scheduling-laws-by-state)
- [Workforce.com — Fair Workweek Laws Explained](https://www.workforce.com/news/predictive-scheduling-laws)
- [WorkAxle — Fair Workweek Laws 2026](https://www.workaxle.com/blog/fair-workweek-laws-2026)
- [EU-OSHA — Directive 2003/88/EC (Working Time)](https://osha.europa.eu/en/legislation/directives/directive-2003-88-ec)
- [Springer/ERA Forum — Rest periods in EU law](https://link.springer.com/article/10.1007/s12027-025-00850-y)
- [EUR-Lex — 32003L0088 full text](https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=CELEX%3A32003L0088)
- Project context: `/Users/pete/IdeaProjects/wfm-service/.planning/PROJECT.md`

---
*Feature research for: contact-centre WFM shift-based scheduling and consistency (v1.3)*
*Researched: 2026-08-25*
