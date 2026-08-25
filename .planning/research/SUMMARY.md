# Project Research Summary

**Project:** wfm-service
**Domain:** Contact-centre workforce management — adding a second, coupled planning dimension (shift-level scheduling) to an existing live Timefold Solver constraint-satisfaction model
**Milestone:** v1.3 Shift-Based Scheduling & Consistency
**Researched:** 2026-08-25
**Confidence:** MEDIUM-HIGH overall (see Confidence Assessment — one load-bearing design question has a 2-1 split across the three research files that must be resolved by a spike, not by this summary)

## Executive Summary

v1.3 adds a shift as a new, small-cardinality planning unit above the existing per-15-minute seat model, targeting the exact failure both prior attempts (`BreakAwareConstructionPhase`, Phase 12's withdrawn Atomic Shift Move) hit: break-geometry discovery across ~36 correlated slot decisions per agent-day. All three research files converge on why this is structurally different from those two attempts — it shrinks the search space instead of adding smarter moves inside the same unchanged one — and STACK.md's headline finding is a clean green light: the Timefold 1.16.0 pin survives with zero new dependencies and zero version-bump justification. 11 of 19 existing constraints need no change (ARCHITECTURE.md), and the feature landscape (FEATURES.md) is well precedented by this project's own `agent_day_hours` upload/inline-edit pattern from v1.2.

The one genuinely unresolved question is the coupling mechanism between the new shift-choice variable and the existing `AgentAssignment.agent` variable — STACK.md and ARCHITECTURE.md both land on a hard-constraint join (their independently-reasoned "Option A," and ARCHITECTURE.md grounds this directly in Timefold's own `school-timetabling` quickstart precedent for "two decisions must agree"), while PITFALLS.md states a filtered value range is preferred and a hard constraint is "a last resort, not a default." This is presented below as a live 2-1 split, not a false consensus — see "The Coupling Mechanism Disagreement" for the full reasoning and the recommended resolution path (a short technical spike, which PITFALLS.md itself calls for).

The second major finding is procedural, not architectural: this is not the milestone's second attempt at consistency scheduling — it is the third. ARCHITECTURE.md discovered four previously undocumented, fully-reverted feature commits (`7861b83`, `9f4a96f`, `9207ceb`, `6fb78c7`) built and unwound between 2026-08-19 and 2026-08-20, one of which (`6fb78c7`) is effectively the drift report this milestone plans to build again. Why that work was unwound is not established in any commit body and must be treated as an open question, not settled history — a claim in ARCHITECTURE.md attributing the revert to "a CI break, not a modelling flaw" is explicitly corrected below because it is not supported by the commit bodies the orchestrator checked. Recovering and repairing six reverted commits is a materially different job than building from scratch, and the roadmap needs to establish which situation applies before phase planning commits to a build order.

## Key Findings

### Recommended Stack

Every primitive this milestone needs — a second `@PlanningEntity`, a second genuine `@PlanningVariable`, solution- and entity-level `@ValueRangeProvider`, class-based `@ShadowVariable`/`VariableListener`, difficulty/strength comparators — predates Timefold 1.16.0 and is unchanged through the 1.16.0→1.19.0 upgrade window. Nothing in scope needs `@PlanningListVariable`, chained variables, the `Neighborhoods` API (1.31.0+), or declarative shadow variables (still PREVIEW well past 1.25.0). **No `build.gradle` change is required or justified for this milestone.**

**Core technologies (all already present):**
- `timefold-solver-bom` 1.16.0 (unchanged pin) — every needed primitive predates it; bumping crosses into `Neighborhoods`/2.0-paid-tier risk for zero capability gain
- `timefold-solver-spring-boot-starter` / `-jpa` 1.16.0 — already wired for the dual JPA-`@Entity`/`@PlanningEntity` role `AgentAssignment` uses today; the new shift entity replicates the same pattern
- Apache POI 5.3.0, Flyway, React — no additions; the Usual Shift column extends the existing `EnrichedColumnLayout` shared definition rather than forking a second one

**New domain classes (modelling only, zero new dependencies):** `ShiftTemplate` (per-desk shift library, plain problem fact, sibling of `Specialization`), `AgentShiftAssignment` (new `@PlanningEntity` + `@Entity`, identity `(agent, date)`, one `@PlanningVariable ShiftTemplate shift`), `AgentUsualShift` (new table modelled on `AgentDayHours`'s shape, not an extension of `AgentPreference` — see below).

**Gap flagged by STACK.md:** Context7 was unavailable this session; all API claims rest on `docs.timefold.ai`/GitHub sources rather than curated-docs-tier. Recommend a direct Javadoc check against the vendored `timefold-solver-core-1.16.0.jar` for exact annotation attribute names before writing constraint-provider code.

### Expected Features

**Must have (table stakes, matches milestone's own stated scope):**
- Per-desk shift library — start/end, break placement rule (reusing existing break hard-constraint parameters, not duplicating them), weekday validity
- Stored usual shift per agent per weekday, populated via upload column + inline roster edit — deliberately mirrors `agent_day_hours` UX exactly, validated precedent, not a new UX decision
- Consistency soft constraint with per-desk tolerance band + weight (genuine dead zone within tolerance, not a taper)
- Per-desk mode switch (shift-scheduled vs. slot-scheduled), defaulting existing desks to slot-scheduled
- Drift report — usual shift vs. solved schedule, per agent/date, reusing the consistency constraint's own distance calculation rather than a second implementation
- Explicit, documented (not silently merged) resolution of `AgentPreference.preferredStartTime` vs. usual-shift precedence

**Should have (add after pilot validation):**
- Effective date range on shift-library entries (defer unless the pilot desk needs mid-flight shift-set changes)
- "Over-subscribed usual shift" visibility across agents — makes the fairness tension observable without building any mitigation

**Defer (explicitly out of scope this milestone):**
- Any shift bidding, rotation, or automated reshuffle — belongs with Backlog 999.4 fairness work; building it now repeats the exact scope-overreach pattern Phase 12 was withdrawn for
- Real-time adherence monitoring — no time-and-attendance data source exists
- Shift-level skill/queue restriction — would contradict the milestone's own architecture decision to keep specialization assignment inside the shift envelope
- Minimum/maximum staffing caps inside the shift template — staffing demand is already a separate, solved subsystem; embedding a second signal creates two sources of truth
- Predictive-scheduling compliance machinery (US ordinance-specific) — this is a single EU/UK-based tenant; none of the researched US laws apply

**The `AgentPreference` vs. usual-shift overlap is a genuine open design question, not a research-resolved fact:** `preferredStartTime` is a continuous, agent-desired nudge; usual shift is a discrete, operator-set, catalog-valued planning target. They are different axes and must not be silently merged — an explicit precedence/tie-break rule between the two soft signals is required, and this interacts directly with reverted commit `7861b83`, which changed exactly this constraint's semantics (making preferred start time an anchor rather than a floor).

### Architecture Approach

**Recommendation (with the caveat below): two independent `@PlanningEntity` classes** — `AgentAssignment` unchanged, new `AgentShiftAssignment` (identity `(agent, date)`, one `@PlanningVariable ShiftTemplate shift`) — coupled by a new hard constraint (`shiftEnvelopeCompliance`) joining on `(agent, date)`, using the same `Joiners`/`ConstraintCollectors` machinery already pervasive in `ScheduleConstraintProvider`. One `Schedule` solution class, one `SolverConfig`, one mode-gated `ConstraintProvider` — not two of anything; a per-desk `scheduling_mode` scalar (mirroring existing per-desk config fields) determines which constraints fire, following the same configuration-driven pattern `minimumStaffing`'s hard/soft toggle already establishes.

**Major components:**
1. `ShiftTemplate` — per-desk shift library (problem fact, sibling of `Specialization`)
2. `AgentShiftAssignment` — new `@PlanningEntity`, one shift choice per agent-day, value range filtered to templates whose net duration matches `AgentDayConfig.effectiveHours` (a sound, problem-fact-dependent use of entity-level `@ValueRangeProvider`)
3. `shiftEnvelopeCompliance` — new hard constraint joining `AgentAssignment` to `AgentShiftAssignment`
4. `AgentUsualShift` — new table (not an `AgentPreference` extension), resolution service copying the proven `resolvePreferences` shape
5. Mode-gated break constraints — the four existing break hard constraints become dead code (not deleted) on shift-scheduled desks, replaced by break-as-a-structural-template-attribute

**Coverage verdict:** 11 of 19 existing constraints are unchanged because they all reduce to counting `AgentAssignment.agent != null`, agnostic to how the seat was filled. 4 break constraints are mode-gated off; 1 new hard constraint is added. **The one integration risk requiring explicit design, not assumption:** shift-template net duration must be filtered against `AgentDayConfig.effectiveHours`/`contractedHoursOver`/`Under`, or the envelope constraint and the contracted-hours constraint can become structurally unsatisfiable together for a given agent-day, with no amount of solver time able to fix it.

**Schema head correction:** current schema is **V38, not V36** as PROJECT.md previously stated (now corrected in PROJECT.md). `V38__add_consistent_start_weight.sql` exists on disk, is applied on dev, and is inert — read by nothing in `src/`. The next migration is **V39**; v1.3 should adopt the existing `consistent_start_weight` column rather than add a duplicate under a colliding name.

### Critical Pitfalls

1. **Two coupled planning variables that can each be locally legal and jointly infeasible** (Pitfall 1) — settle the coupling mechanism as an explicit, tested decision before any consistency-constraint or dual-mode work begins; detect via an extended `runPreSolveScoreDiagnostic` probe and a trivial-fixture CH-feasibility test, not by eyeballing.
2. **A shift library stricter than actual demand makes a previously-solvable desk infeasible** (Pitfall 2) — nothing today connects the shift library to the FTE demand curve; build a coverage validator (extending `runPreSolveValidation`'s existing pattern) that runs on every library edit and mode switch, before the mode switch is ever operator-facing.
3. **Benchmarking the shift model the way Phase 12 did, reaching the same untrustworthy conclusion** (Pitfall 3) — seeded, step-count-terminated, ≥5-seed A/B runs, median AND full min/max spread, pre-committed thresholds, realistic (130%) over-allocation as first-class not an appendix. Non-negotiable, directly traceable to Phase 12's near-false-positive.
4. **Dual-mode coexistence gaps** (Pitfall 4) — enumerate every one of the 18 (now 19) existing constraints as mode-agnostic / must-be-bypassed / needs-a-new-variant; a test suite where every fixture is single-mode is structurally blind to interaction bugs, the same blindness that let the BambooHR field-4517 alias bug ship.
5. **Model built, view never migrated** (Pitfall 6) — this project's own most directly-precedented risk (audit finding I-1/F-1 repeated at a larger surface area): roster view, export, accepted-schedule view, and the drift report itself are all independent opportunities to show stale data while the model underneath is correct. Every phase that writes shift data needs its own display verification as a must-have.

## The Coupling Mechanism Disagreement — Presented Explicitly, Not Resolved Here

This is the milestone's single highest-leverage decision and the three research files do not agree on it. This must not be smoothed over.

- **STACK.md (self-rated MEDIUM-HIGH):** recommends a `ConstraintStream` hard-constraint join ("Option A"), explicitly rejecting a filtered value range (calls it "the wrong tool... inverts the dependency") and a shadow variable.
- **ARCHITECTURE.md (self-rated HIGH):** independently ranks the same hard-constraint join first, rejecting a filtered entity-level value range on `AgentAssignment.agent` (its "Option C") on three specific soundness grounds — no invalidation path when the depended-on variable changes, a construction-order chicken-and-egg problem, and it doesn't remove the need for the hard constraint anyway, since Timefold never re-validates a value already held against a later-narrowed range. Grounds this directly in Timefold's own `school-timetabling` quickstart (verified at git tag v1.16.0), which resolves its analogous "two decisions must agree" problem with hard constraints between entities, never a value range reading another entity's genuine planning variable. Notes Option A can be layered later with a `SelectionFilter` as a pure performance tune, sound because the hard constraint remains the correctness backstop regardless.
- **PITFALLS.md (self-rated HIGH):** states a filtered value range is the *preferred first choice* and that a hard constraint is "a last resort, not a default." Warns that constraint-only coupling reproduces the noise-floor problem that already nearly produced a false positive in Phase 12's benchmark, now with a larger, two-variable search space to generate noise in.

**Do these reconcile or genuinely conflict?** They are less opposed than they first appear, but not fully reconciled. ARCHITECTURE.md's own reasoning is that a *filtered value range specifically on `AgentAssignment.agent` reading `AgentShiftAssignment`'s genuine variable* (its Option C — the shape PITFALLS.md's "filtered value range" most naturally reads as) is unsound for correctness reasons, not just an efficiency concern — Timefold does not re-validate a held value against a later-narrowed range, so it cannot be relied upon as the sole coupling mechanism regardless of performance. PITFALLS.md's objection, read carefully, is about search-budget waste (the solver visiting jointly-infeasible states before rejecting them), which is a real but different complaint from soundness. ARCHITECTURE.md's own `SelectionFilter` layer (its "E-variant," sound and documented, filtering the `agent` value selector's candidates based on current shift state) targets exactly PITFALLS.md's search-budget objection, without reintroducing Option C's soundness problem, because the hard constraint remains the correctness backstop underneath it. **If PITFALLS.md's "filtered value range" recommendation is read as this `SelectionFilter` layering, the two positions substantially reconcile: hard constraint for correctness, filter for efficiency, exactly ARCHITECTURE.md's staged design.** If PITFALLS.md instead means a bare filtered value range in place of the hard constraint, the disagreement is real and unresolved — ARCHITECTURE.md's three soundness objections would apply directly.

**This ambiguity is not resolvable from research alone.** PITFALLS.md itself recommends a short technical spike against the real Timefold 1.16.0 API before phase planning commits to a coupling mechanism — that recommendation is carried forward here as a required first step, not a nice-to-have. The spike should specifically test: (a) can a `SelectionFilter`-layered hard constraint achieve acceptable search efficiency without the soundness risk of Option C, using a trivial fixture; (b) does the `AgentShiftAssignment`-before-`AgentAssignment` construction-heuristic ordering (two sequential CH phases, confirmed as a documented 1.16.0 pattern but with only MEDIUM confidence on exact XML nesting) work as expected. Do not let the roadmap presuppose a settled answer.

## A Third, Previously Undocumented Prior Attempt — Confirmed, Not Speculative

ARCHITECTURE.md discovered, and the orchestrator has independently verified in git, a third prior attempt at this exact feature — v1.3 is not building on a blank slate and is not the second attempt (`BreakAwareConstructionPhase`, Phase 12) but the fourth overall.

**Confirmed facts:** Four feature commits, all ancestors of HEAD, all reverted: `7861b83` (2026-08-19, preferred start time as an anchor rather than a floor), `9f4a96f` (2026-08-20, consistent break offset across an agent's week), `9207ceb` (2026-08-20, consistent daily start with a solver-chosen anchor), `6fb78c7` (2026-08-20, per-agent start and break-offset spread reporting — in effect the drift report this milestone plans). Two supporting perf commits shared one agent-day grouping across nine constraints. Reverted by `2da56fd`, `3aba7c6`, `65ccb34`, `ac395f2`, `b6188c8`, `12315ed`.

**Correction to ARCHITECTURE.md, made explicit here:** ARCHITECTURE.md attributes the revert to "a CI break, not a modelling flaw," citing the revert commit's explanation for why the `V38` migration was retained. The orchestrator checked the revert commit bodies directly and they state only why the migration stayed — they do not state that a CI break, rather than a modelling problem, is why the whole four-commit sequence was unwound. **Why the work was actually unwound is unknown and must be carried forward as an open question, not repeated as settled fact.** The roadmapper should not inherit "it was just a CI issue" as a premise.

**Consequence for the roadmap:** determining whether these six reverted commits are a recoverable asset (fix-forward from proven, previously-compiling code) or a warning (abandoned for a substantive reason not yet understood) is likely a prerequisite investigation task, not something to resolve implicitly by simply reimplementing from scratch. One substantive technical difference is already known and favors the new design regardless of the revert's cause: the abandoned design's own javadoc names a structural weakness (a spread-based penalty set entirely by the two extreme days, giving the search "a plateau, not a gradient") that the milestone's target-shift formulation (comparing every day against a stored target) does not share — this is a real, citable reason to expect better local-search behavior independent of the shift-vs-slot architecture question, but it does not by itself explain or excuse the unexplained revert.

## Implications for Roadmap

Based on combined research, a dependency-ordered build sequence (adapted from ARCHITECTURE.md §6, cross-checked against PITFALLS.md's phase-mapping):

### Phase 1: Shift template library — data model + admin CRUD
**Rationale:** Touches no solver code, no existing desk behavior. Independently shippable and unblocks everything downstream that references a `ShiftTemplate` FK.
**Delivers:** `ShiftTemplate` entity, `V39` migration, desk-scoped admin UI.
**Addresses:** Per-desk shift library table-stakes feature.
**Avoids:** Nothing yet at risk — this phase is deliberately inert.

### Phase 2: Desk mode switch — field + validation only
**Rationale:** Depends only on Phase 1; safe/inert until a desk is explicitly switched, matching the milestone's own "keeps a fallback if it underperforms live" requirement.
**Delivers:** `Desk.scheduling_mode` (`V41`, default `SLOT`), validation blocking switch to `SHIFT` mode without an active `ShiftTemplate`.
**Addresses:** Per-desk mode switch.
**Avoids:** Pitfall 8 (migration risk) via additive-only, safe-default migration discipline.

### Phase 3: Investigate the reverted third attempt (prerequisite, not optional)
**Rationale:** Cannot responsibly plan the consistency-constraint phase without knowing whether `7861b83`/`9f4a96f`/`9207ceb`/`6fb78c7` are a recoverable asset or a warning. This is analysis work, cheap and blocking.
**Delivers:** A documented determination of why the third attempt was unwound and what, if anything, is safely reusable from it (the `groupBy(agent, date, ...)` pattern is confirmed to compile and pass tests at the time it was live).
**Addresses:** Directly de-risks Phase 6/7 below.
**Avoids:** Repeating an unknown mistake blind.

### Phase 4: Coupling-mechanism spike (prerequisite, not optional)
**Rationale:** The load-bearing architecture decision (hard constraint vs. `SelectionFilter`-layered hard constraint vs. anything else) is disputed across research files and self-rated HIGH confidence on both sides of the dispute. Must be resolved against the real 1.16.0 API before Phase 5 commits to an implementation.
**Delivers:** A settled coupling design, validated against a trivial fixture (CH reaches feasibility; `SelectionFilter` layering assessed for soundness/efficiency tradeoff).
**Addresses:** The central architectural question named in PROJECT.md.
**Avoids:** Pitfall 1 (jointly-infeasible locally-legal states) and Anti-Pattern 2/3 (unsound value-range/shadow-variable coupling).

### Phase 5: Core coupling — `AgentShiftAssignment` + two-phase CH + `shiftEnvelopeCompliance` hard constraint
**Rationale:** The architectural core; carries the real risk from Phase 4's spike and Phase 12's precedent. Must be proven in isolation before anything downstream depends on it.
**Delivers:** New `@PlanningEntity`, sequential CH phase ordering, the new hard constraint — gated behind a system property, not in the default suite, until the seeded 5x5 A/B benchmark passes a pre-committed threshold.
**Addresses:** Shift as an availability envelope; contiguity by construction.
**Avoids:** Pitfall 3 (untrustworthy benchmark) and Pitfall 9 (Timefold 1.16.0-specific traps — explicit `unionMoveSelector` re-declaration, full-suite `solverConfig.xml` validation, dedicated `FULL_ASSERT` test).

### Phase 6: Break-as-structural-attribute + mode-gating the four break constraints; value-range filtering by `effectiveHours`
**Rationale:** Depends on Phase 5 being proven. Changes what "break" means in shift mode and needs its own correctness proof.
**Delivers:** Break offset/duration as `ShiftTemplate` fields, four break constraints mode-gated off for shift-scheduled desks, entity-level value-range filtering against `AgentDayConfig.effectiveHours`.
**Addresses:** Break placement rule per shift.
**Avoids:** The structural-unsatisfiability integration risk named in ARCHITECTURE.md §1 (envelope + contracted-hours constraints becoming jointly impossible).

### Phase 7: `AgentUsualShift` storage + resolution service + Usual Shift upload/roster columns
**Rationale:** Independently shippable relative to the consistency constraint — pure data model plus a resolution service copying the proven `resolvePreferences` pattern; only needs Phase 1 (valid `ShiftTemplate` FK target).
**Delivers:** New table, upload column extending `EnrichedColumnLayout`, inline roster edit, explicit `AgentPreference`-vs-usual-shift precedence surfacing.
**Addresses:** Stored usual shift; two population paths; the `AgentPreference` overlap.
**Avoids:** Pitfall 7 (guarantee holding on one write path only — I-2 repeat) by enumerating every write path (upload, inline edit, solver, BambooHR refresh, mode switch) up front.

### Phase 8: Usual-shift consistency soft constraint
**Rationale:** Depends on Phase 5 (needs `AgentShiftAssignment` to compare against) and Phase 7 (needs the stored target) and Phase 3's determination.
**Delivers:** Target-deviation soft constraint (not spread-based), tolerance band as a genuine dead zone, weight validated via `SolutionManager.explain()` against the existing constraint hierarchy before shipping a default.
**Addresses:** Consistency soft constraint.
**Avoids:** Pitfall 5 (weight dominating or being dominated) and repeats the CI/deploy-gate discipline the reverted attempt lacked.

### Phase 9: Drift report
**Rationale:** Independently shippable once Phase 7 exists; reuses Phase 8's distance calculation, doesn't need Phase 8's constraint to exist.
**Delivers:** Read-side panel comparing solved schedule to stored usual-shift target, framed as both a solver-diagnostic and a usual-shift-data-quality signal.
**Addresses:** Drift report.
**Avoids:** Pitfall 6 (model built, view not migrated) via an explicit end-to-end trace as a verification step, and the passive-report UX pitfall (report must be actionable, not log-equivalent).

### Phase Ordering Rationale

- Phases 1, 2, 3, 4, and 7 have no dependency on the risky core (Phase 5) landing successfully and can proceed in parallel with its investigation/spike/benchmark work — only Phases 6, 8 (and therefore 9) are gated on Phase 5.
- Phases 3 and 4 are placed before Phase 5 deliberately, as prerequisite investigation rather than folded into Phase 5's own scope, because both research files treat them as blocking unknowns rather than implementation details.
- Every phase that writes shift/usual-shift data must include display verification as a must-have criterion in its own UAT, per Pitfall 6 — do not defer this to a later closure phase the way v1.2 needed Phase 13.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 4 (coupling-mechanism spike):** the exact `queuedEntityPlacer`/`entitySelector` nesting for two sequential CH phases is only MEDIUM confidence per ARCHITECTURE.md; must be spiked against a fixture, not trusted as documented.
- **Phase 5 (core coupling):** exact Timefold annotation attribute names (`sourceVariableName` vs. `sourceVariableNames`, etc.) were not independently confirmed against version-pinned Javadoc — verify against the vendored 1.16.0 JAR before writing constraint-provider code.
- **Phase 3 (revert investigation):** may surface findings that change the shape of Phases 6-8 entirely; treat its output as a gate, not a formality.

Phases with standard, well-documented patterns (skip research-phase):
- **Phase 1 (shift library CRUD):** directly parallels `Specialization`'s existing desk-scoped list pattern.
- **Phase 2 (mode switch):** directly parallels `minimumStaffing`'s existing configuration-driven hard/soft toggle pattern.
- **Phase 7 (usual shift storage):** directly parallels the proven `AgentDayHours`/`resolvePreferences` shape from v1.2.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM | Multiple independent official Timefold sources agree, but fetched via WebFetch/WebSearch, not Context7 (unavailable this session) — treat as MEDIUM pending a direct Javadoc check |
| Features | MEDIUM | Industry-wide web sources on vendor UX/behavior, no vendor trial access; regulatory claims cross-checked across multiple sources but not primary vendor docs |
| Architecture | HIGH for codebase claims (read directly from source, line-cited) and Timefold API claims (verified against git tag v1.16.0 source); MEDIUM for Option C's soundness reasoning (rests on documented API contract and intent, not an explicit "this is forbidden" statement) |
| Pitfalls | HIGH | Every pitfall grounded in this codebase's own source and recorded milestone history (audit findings, Phase 12's own evidence), not generic domain advice |

**Overall confidence:** MEDIUM-HIGH — high on domain/codebase grounding and the "what needs to change" questions; the one unresolved item (coupling mechanism) is a genuine disagreement between HIGH-confidence sources requiring a spike, not a confidence gap that more research would resolve.

### Gaps to Address

- **The coupling mechanism dispute (STACK/ARCHITECTURE vs. PITFALLS):** not resolvable from research alone — carry PITFALLS.md's recommended technical spike forward as Phase 4, before Phase 5 commits to an implementation.
- **Why the third prior attempt was reverted:** unknown, not stated in any commit body — carry forward as Phase 3, a blocking investigation task, not an assumption either direction.
- **Exact Timefold 1.16.0 annotation attribute names and CH placer XML nesting:** MEDIUM confidence per STACK.md and ARCHITECTURE.md respectively — verify against the vendored JAR/a fixture spike before trusting in a real plan.
- **PROJECT.md's schema-head claim was stale (V36 vs. actual V38):** now corrected in PROJECT.md itself during this research pass; the roadmap and all migration numbering must start from V39.

## Sources

### Primary (HIGH confidence)
- Codebase, read directly: `Schedule.java`, `AgentAssignment.java`, `AgentPreference.java`, `AgentDayHours.java`, `AgentDayConfig.java`, `Desk.java`, `ConstraintWeights.java`, `ScheduleConstraintProvider.java`, `BreakAwareConstructionPhase.java`, `solverConfig.xml`, `SolverService.java`, `build.gradle:35`, Flyway migrations V26-V38
- `github.com/TimefoldAI/timefold-solver` at git tag `v1.16.0` — `AbstractMove.java`, `ConstructionHeuristicPhaseConfig.java`, `ValueSelectorConfig.java`, `MoveSelectorConfig.java`, `EnvironmentMode.java`
- `github.com/TimefoldAI/timefold-quickstarts` (`stable` branch) — `school-timetabling`/`employee-scheduling` domain classes, verifying the hard-constraint coupling precedent
- `.planning/milestones/v1.2-phases/12-atomic-shift-move/` (all files) and `.planning/milestones/v1.2-MILESTONE-AUDIT.md` — Phase 12 benchmark methodology and withdrawal record; audit findings I-1 through I-4, NEW-1
- Git history, read directly: commits `9207ceb`, `9f4a96f`, `7861b83`, `6fb78c7` and their reverts

### Secondary (MEDIUM confidence)
- `docs.timefold.ai` version-specific pages and GitHub release notes (WebFetch/WebSearch, cross-checked across independent official sources, not Context7)
- Vendor UX research (Genesys, NICE CXone, Deputy, Bright Pattern) — web search snippets, no primary vendor documentation fetched

### Tertiary (LOW confidence, flagged for validation)
- Exact Timefold annotation attribute names and CH placer XML nesting — needs direct Javadoc/fixture verification before implementation

---
*Research completed: 2026-08-25*
*Ready for roadmap: yes — with Phase 3 (revert investigation) and Phase 4 (coupling-mechanism spike) as required prerequisite tasks, not optional research*
