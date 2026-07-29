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

## Cross-Milestone Trends

| Milestone | Requirements shipped | Phases delivered | Closed |
|---|---|---|---|
| v1.0 AWS Deployment | partial (IAM-blocked) | 1 of 4 complete, 2 partial | 2026-04-21 |
| v1.1 Schedule Quality & Reporting | 4 of 16 (25%) | 2 of 4 | 2026-07-29 |

**Recurring themes:**

- **Both milestones closed with roughly half their phases undelivered.** v1.0 on an external blocker (IAM permissions), v1.1 on internal scope discovery. Milestone scoping has consistently been more ambitious than the evidence available at planning time supported.
- **Blockers identified early are accepted and carried rather than resolved.** The IAM blocker has persisted across two milestones (999.1–999.3). The BambooHR key exposure is on the same trajectory. Both are gated on a single operator action outside the workflow.
- **Investigation consistently changes the requirement.** In both milestones, the work as specified differed materially from the work as built once real systems were probed. Front-loading a discovery spike before committing roadmap scope would likely have produced more accurate milestones.
