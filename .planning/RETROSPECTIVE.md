# Retrospective: WFM Service

A living record of what worked, what didn't, and what to carry forward across milestones.

---

## Milestone: v1.1 — Schedule Quality & Reporting

**Closed:** 2026-07-29 (early — re-scoped)
**Phases:** 2 of 4 delivered | **Plans:** 8 | **Requirements:** 4 of 16 (25%)
**Timeline:** 2026-05-07 → 2026-07-29 (83 days, 74 commits, 99 files, +14,238/−144 LOC)

### What Was Built

The BambooHR agent-data foundation. Employment type and job title now flow onto `Agent`, with non-schedulable job titles excluded from both solving and desk allocation. Desks can be bulk-populated from a spreadsheet with per-row failure reporting.

The substantial piece was making the solver's `MANDATORY` day-off path real. It had been fully built but dead in production for the entire life of the service — `BambooRefreshService` tried to detect mandatory days by exact string match on time-off *type* names, which never matched real BambooHR data. v1.1 found the actual source (custom field 4517, `customWorkingdays`, under BambooHR Personal → Schedule), added it to the existing bulk `/reports/custom` fetch, wrote a tolerant parser for its free-text values, and generates recurring MANDATORY blocks idempotently across the schedule horizon. Agents whose pattern can't be parsed are now excluded from solving rather than silently mis-scheduled.

### What Worked

- **Investigating the data source before designing the constraint.** The original QUAL-01 said "solver ensures every agent receives exactly 2 contiguous days off." Investigation revealed employees have *fixed* weekly patterns already recorded in BambooHR — so the solver should respect them, not choose them. Building the original requirement as written would have produced schedules that overrode real contracts.
- **Following the evidence through three wrong answers.** The todo record shows the source hypothesis moved from time-off `amount==0` → desk-upload spreadsheet columns → weekday custom fields 5553–5563 (which exist but are empty — a genuine red herring) → field 4517. Each was tested against live data rather than assumed.
- **Writing the parser against the real value catalogue.** Pulling live values first surfaced wrapping ranges, "Mon. to Thurs.", comma lists, "Mon - Sun HOOP" annotations, and spelling variants. A parser written from the spec's "2 consecutive days off" assumption would have broken on 4-day and 7-day weeks.
- **The verifier reading source rather than trusting summaries.** `06-VERIFICATION.md` re-ran test suites, inspected JUnit XML directly, and re-grepped for the dead code path instead of citing SUMMARY claims. It caught that the idempotency test exercises a hand-copied replica of the real method, and it independently confirmed the frontend rendering code the operator had approved by eye.

### What Was Inefficient

- **Scope was set before the problem was understood.** The v1.1 roadmap committed to 16 requirements across 4 phases up front. Phase 6 alone consumed the milestone once its real complexity surfaced. 12 requirements were carried for 83 days without being started.
- **Deferred requirements lost their home.** QUAL-02 and QUAL-03 were dropped from Phase 6 during discussion and pointed at "a follow-on phase (6b/7)" that was never created. The traceability table still listed them as `Phase 6 | Pending`. The verifier flagged this as "a documentation nit, not a BLOCKER" — but it meant two requirements were one milestone-close away from vanishing. Deferral needs a destination, not a direction.
- **Requirement checkboxes drifted from reality.** DATA-01/02/03 shipped in Phase 5 but stayed unchecked with "Pending" traceability rows through milestone close, making v1.1 look like it delivered 1 of 16 rather than 4.
- **A blocking security gate was bypassed without a mechanism to force resolution.** The key rotation was correctly modelled as `blocking-human`, correctly disclosed in the summary, correctly recorded as a FAILED must-have with an override — and is still unrotated in a public repo. Every process step worked; the credential is still exposed.

### Patterns Established

- **`AgentEligibilityService` as the single eligibility gate.** `SolverService.filterEligible` now has exactly four filters (active, desk-assigned, schedulable job title, working-days-known). New exclusion criteria belong here rather than scattered through the solver.
- **Data-gap as an explicit modelled state.** `Agent.working_days_known` treats "we don't know this agent's pattern" as distinct from "this agent has no days off." Migration default stays `TRUE` permanently so pre-existing agents aren't retro-flagged.
- **Delete-then-reinsert for idempotent refresh.** `deleteByAgent_IdAndDateBetween` + `flush()` before regeneration, with `putIfAbsent` and MANDATORY processed before PTO to establish priority.
- **Tolerant parsers return `Optional.empty()`, never throw.** Free-text integration fields get a try/catch wrapper and explicit null/blank/sentinel guards ahead of parsing.

### Key Lessons

1. **Verify the data source exists and is populated before scoping work that depends on it.** Field 4517 turned out to be ~45% populated and ~24% parseable. That was discovered *after* building the pipeline that consumes it, and it now silently excludes an unmeasured fraction of agents from scheduling. Source-data coverage is a scoping input, not an implementation detail.
2. **A deferral needs a destination.** "Deferred to a follow-on phase" is not a location. Create the backlog entry at the moment of deferral.
3. **An accepted risk with no owner and no date is an unfixed bug with paperwork.** The override mechanism recorded the key-rotation decision accurately and changed nothing about the exposure.
4. **Update requirement status at phase close, not milestone close.** Three months of drift made the milestone's real progress unreadable from its own tracking.
5. **Dead code paths can pass UAT indefinitely if only the mock exercises them.** `MockBambooHRClient` was the sole producer of MANDATORY day-offs, so the feature demoed correctly while doing nothing in production. Mock-only coverage of an integration path is a warning sign.

### Cost Observations

- Model profile: `balanced` | Mode: `yolo` | Granularity: `coarse`
- 8 plans across 2 phases; Phase 6 required research, discussion, patterns, and verification artifacts — the heaviest per-requirement cost of the milestone (3 plans for 1 requirement)
- Notable: Phase 6's investigation cost (four candidate data sources, live API probing) was front-loaded into the todo record before planning began, which kept the plans themselves tight

---

## Milestone: v1.2 — Unified Agent Provisioning

**Closed:** 2026-08-25 (`override_closeout`)
**Phases:** 4 delivered, 1 withdrawn | **Plans:** 23 | **Requirements:** 19 of 19 (100%)
**Timeline:** 2026-07-30 → 2026-08-25 (26 days, 252 commits, 105 files, +10,233/−857 LOC)

### What Was Built

Mon–Sun contracted hours became a first-class data model, and — after a second attempt — actually
visible. An operator downloads a per-desk template, fills seven day cells per agent (a number,
`MANDATORY`, or `PTO`), uploads it; the system syncs BambooHR, merges field-by-field with explicit
precedence, reports what came from where, and shows the stored result back in the roster and the
Excel export.

Phase 9 built the storage (`agent_day_hours`, name split, V29 migration). Phase 10 rewrote the
parser around one polymorphic 7-column day group and one shared `EnrichedColumnLayout`. Phase 11
built the merge engine and its report. Phase 12 was withdrawn. **Phase 13 existed only because the
first milestone audit caught that nobody had migrated the read path** — the roster still showed a
desk default unrelated to what had just been uploaded.

### What Worked

- **The milestone audit caught what four phase verifications missed.** Phases 9, 10 and 11 each
  verified `passed` in isolation, and each was correct. The defect lived in the seam: Phase 9 made
  `agent_day_hours` authoritative, Phase 10's upload nulled the old scalar, and nothing ever
  repointed `DeskAgentService.toResponse`. Per-phase verification structurally cannot see this.
- **Staging data to make a test observable, rather than declaring it unobservable.** Phase 13's UAT
  repeatedly hit states the dev dataset could not produce — all 28 agents had full row sets, so the
  "not set" branch rendered for nobody; 2 of 5 display states had no representative cell anywhere in
  196 cells. Each was staged through the supported endpoint, verified by API read-back, blast radius
  checked, and restored afterward.
- **Refusing bundle-string presence as proof of render.** The UAT notes record this explicitly:
  finding `#9ca3af` and `'Not set (default)'` in the shipped JS was *not* accepted as evidence the
  muted branch rendered. That distinction is what made test 1's first run a recorded PARTIAL instead
  of a false pass.
- **Verifying documentation claims against arithmetic and observation.** Two claims that had
  survived every prior review were false: E1's "at most 5 characters" (the `min-max` branch reaches
  10 — `0.25-23.75`), and E4's "the datalist popup is not constrained by the cell width" (a native
  `<datalist>` renders at its input's width, which is fixed at 90px). Both were load-bearing
  resolutions of design contracts.
- **Withdrawing Phase 12 instead of shipping it.** Three plans executed, benchmark run, effect
  measured at +0.25h median against a 5.00h baseline spread — inside its own noise. Code reverted,
  goal explicitly not claimed, artifacts kept as the record.

### What Was Inefficient

- **Building a data model without building the view of it.** The single largest cost of the
  milestone was Phase 13: six plans, a full gap-closure round, and two UAT sessions, entirely to
  make already-stored data visible. "Store it" and "show it" were treated as one requirement (MDL-02)
  and delivered as one — but only the first half.
- **A claim that cannot be observed cannot be falsified.** E4's clipping assertion was wrong from
  the day it was written and survived because the editor opened pre-seeded, which filtered the
  offending entry out of the dropdown entirely. Fixing an unrelated defect (G-13-DD) made the list
  render, and the false claim became visible immediately. Design contracts resolved on reasoning
  about browser behaviour, with no observation, are unfalsifiable until something else changes.
- **Fixing one gap opened another.** Phase 13's gap-closure seeded the editor with the current value
  (closing gap 2), which collapsed the native `<datalist>` to a single self-matching option and made
  the 97-entry picklist unreachable — in exactly the case it existed for. The fix reversed part of an
  earlier fix. Neither was wrong in isolation; the interaction was never modelled.
- **I-2 was deferred as "a scoping decision, not a defect" and then not decided.** The 2026-08-21
  audit framed it that way, legitimately. It was still open, still undecided, and still untouched at
  the 2026-08-25 re-audit, and shipped as accepted debt. A deferral that is never revisited is
  indistinguishable from a decision not to fix.
- **Security review ran only because milestone close forced it.** Phase 13's `/gsd-secure-phase`
  never ran during the phase; it was discovered as a blocking `verify:post` gate afterward. Phase 9
  still has no SECURITY.md at all.

### Patterns Established

- **`EnrichedColumnLayout` as the single column-layout definition** shared by template, parser and
  export — including `specialtyHeader(int)` / `specialtyIndex()` as a round-tripping pair.
- **Single-row upsert over delete-and-recreate for per-entity edits.** `setDayHours` writes exactly
  one weekday, provably leaving the other six untouched; the destructive seven-row fan-out survives
  only as an explicitly labelled bulk action behind a `confirm()`.
- **Tenant/desk resolution before any un-scoped repository call.** `findByIdAndTenantIdAndDeskId(...)
  .orElseThrow()` runs first, with an inline comment stating why the ordering is load-bearing.
- **Range guards positioned before destructive work, pinned by an ordering assertion.** The 0–24
  check sits before `setContractedHoursPerDay` and before `deleteByAgent_Id`, and a test proves rows
  and labels survive a rejection.
- **Preserving superseded verdicts rather than overwriting them.** When an amended contract turns a
  failing test into a passing one, the original report stays in the file with an explanation. A
  `pass` earned by changing the spec and a `pass` earned by fixing the code are different results,
  and the file should not let a reader confuse them.

### Key Lessons

1. **"Store X" and "show X" are two requirements.** MDL-02 was satisfiable, and was satisfied, with
   the operator unable to see any of it. Where a requirement exists so a human can verify something,
   the verification surface belongs in the requirement.
2. **A requirement verified `passed` in one phase can be violated by a second entry point in
   another.** This happened twice — I-1 (the read path) and I-2 (the Refresh button). The pattern to
   watch for is a guarantee implemented at one call site while the underlying field or table has
   multiple writers.
3. **Prose in a plan is a claim, not evidence.** Three separate false statements shipped inside
   design contracts and verification reports this milestone, each surviving review because it sounded
   like reasoning. Two were arithmetic errors; one was an assumption about browser behaviour that a
   ten-second test would have refuted.
4. **A deferral needs a destination *and* a decision date.** v1.1's lesson was the first half. I-2
   had a destination — it was written down, twice — and still went nowhere, because nothing forced a
   verdict.
5. **Run the retroactive gates during the phase, not at the boundary.** Security review, Nyquist
   validation and UAT all surfaced as blockers at close. Two phases still carry `VALIDATION.md` at
   `status: draft`, and Phase 9 has never had a security review at all.

### Cost Observations

- Model profile: planner `opus`, checker/auditor/integration-checker `sonnet`
- 23 plans across 5 phases; Phase 13 alone was 6 plans (26% of the milestone) for zero new
  requirements — pure closure of an integration gap the audit found
- Phase 12 cost 3 plans and produced no shipped code, by design — the benchmark was the deliverable
- Notable: the two most valuable findings of the milestone (I-1 and the E1/E4 false claims) came from
  adversarial re-derivation — the milestone audit and UAT staging — not from any phase's own
  verification of itself

---

## Cross-Milestone Trends

| Milestone | Requirements shipped | Phases delivered | Closed |
|---|---|---|---|
| v1.0 AWS Deployment | partial (IAM-blocked) | 1 of 4 complete, 2 partial | 2026-04-21 |
| v1.1 Schedule Quality & Reporting | 4 of 16 (25%) | 2 of 4 | 2026-07-29 |
| v1.2 Unified Agent Provisioning | 19 of 19 (100%) | 4 of 5 (1 withdrawn) | 2026-08-25 (override) |

**Recurring themes:**

- **v1.2 broke the scoping pattern; the other patterns held.** The first two milestones closed with
  roughly half their phases undelivered. v1.2 shipped every requirement — but needed a sixth-of-a-
  milestone closure phase to do it, and still closed under override. Tighter scope (19 requirements,
  26 days) produced a materially better outcome than v1.1's 16-requirement, 83-day sprawl.
- **Blockers identified early are still accepted and carried rather than resolved.** The IAM blocker
  has now persisted across three milestones (999.1–999.3). The BambooHR key exposure has been
  accepted at two consecutive closes and is still live in a public repo. I-2 joins them this
  milestone. **Every one of these was correctly identified, correctly documented, and not fixed** —
  the process reliably produces accurate records of things that do not get done.
- **Investigation consistently changes the requirement** — v1.0 and v1.1 through probing live
  systems, v1.2 through auditing its own claims. In every milestone so far, the work as specified
  differed materially from the work as built.
- **Adversarial verification finds what self-verification cannot.** v1.1's verifier caught a test
  exercising a hand-copied replica. v1.2's audit caught a cross-phase seam four phase verifications
  passed over, and its UAT caught two false claims inside design contracts. The consistent signal is
  that a reviewer trying to *refute* finds things a reviewer confirming does not.
