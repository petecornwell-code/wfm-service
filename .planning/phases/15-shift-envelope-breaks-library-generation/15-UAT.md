---
status: diagnosed
phase: 15-shift-envelope-breaks-library-generation
source: [15-01-SUMMARY.md, 15-02-SUMMARY.md, 15-03-SUMMARY.md, 15-04-SUMMARY.md, 15-05-SUMMARY.md, 15-06-SUMMARY.md, 15-07-SUMMARY.md, 15-08-SUMMARY.md]
started: 2026-08-27T13:10:00Z
updated: "2026-08-27T16:20:00Z"
---

<!--
WHERE TO TEST

Phase 15 is ALREADY DEPLOYED to the dev environment and healthy:

  https://d2bbtcc80peap7.cloudfront.net        (/actuator/health -> UP, Postgres connected)

Deployed commit: `adaad6d` — the merge of both defect-fix worktrees. Verified to contain NO
source difference from the phase's final HEAD (`git diff adaad6d..HEAD -- . ':!.planning/'` is
empty); every commit after it touches only `.planning/**`, which `deploy.yml` excludes via
`paths-ignore`. So the live dev service runs exactly the code this phase verified, including
the CR-01/CR-02/CR-03 fixes.

WHAT THE AUTOMATED SUITE DOES AND DOES NOT COVER

The suite (79 classes / 505 tests / 0 failures) runs against H2 with `ddl-auto: create-drop`
and `spring.flyway.enabled: false` — the test schema is generated from the JPA entities, so
**no test executes V40-V43**. `MigrationEntityConsistencyTest` is a static regex reconciliation
of DDL text against entity mappings: a real guard, but it cannot catch Postgres-specific
syntax, constraint violations on real rows, or an incorrect data fan-out.

However — and this CORRECTS the original framing of this file — the migrations have now been
executed for real. Four successful dev deploys applied V40, V41, V42 and V43 against real
Postgres. Production runs `ddl-auto: validate` with Flyway enabled, so a migration disagreeing
with its entity mapping would fail startup and take the health check with it. It didn't. Dev is
therefore a successful rehearsal, not an untested leap.

What that does NOT establish is that the V40 data fan-out produced the RIGHT rows — "the
migration ran" and "the data is correct" are different claims. Test 1 is the one that closes
that gap, and it is the first thing to run.

Production remains genuinely untested, and V40's `DROP COLUMN` is irreversible there. Test 18
covers that and stays blocked until a production deploy is actually planned.
-->

## Current Test

number: 1
name: V40 data fan-out preserved every existing break (dev)
expected: |
  Against the dev database, every pre-existing template that had a non-zero
  `break_duration_minutes` now has exactly ONE band carrying its former offset and duration;
  templates that had no break have zero bands. Nothing lost, nothing duplicated.
awaiting: user response

## Tests

### 1. V40 data fan-out preserved every existing break (dev)

expected: The migration ran (see Test 2) — this checks it produced the RIGHT rows, which is a separate claim. Run against dev:

```sql
SELECT t.id, t.name, count(b.id) AS bands,
       min(b.offset_minutes) AS offset_min, min(b.duration_minutes) AS dur_min
FROM shift_template t
LEFT JOIN shift_template_break_band b ON b.shift_template_id = t.id
GROUP BY t.id, t.name
ORDER BY bands DESC, t.name;
```

Every template that previously had a break shows exactly 1 band with its former offset/duration; templates without a break show 0. **This is the highest-value item in the file** — it is the one thing about V40 that deploy success cannot tell you, and the source columns are already dropped.
result: [pending]

### 2. All four migrations apply cleanly on Postgres (dev)

expected: V40-V43 apply with no error and the app starts under `ddl-auto: validate`.
result: pass
reported: "Evidenced by deploy history, not by manual test: four successful dev deploys (runs at 00:27, 02:18, 05:07, 11:51, 12:42 UTC on 2026-08-27), with /actuator/health reporting UP and db UP on PostgreSQL. Under ddl-auto=validate + flyway.enabled=true, a migration/entity disagreement fails startup, so a passing health check is positive evidence. Confirm if desired with: SELECT version, success FROM flyway_schema_history WHERE version IN ('40','41','42','43');"

### 3. No Phase 14 desk's validation verdict moved

expected: For a desk that existed before this phase and uses single-break templates, the Shift Library validation result — coverage verdict, net hours, grid-alignment verdict — is identical to before. This is the phase's own stated invariant: a one-band template must reproduce its single-offset predecessor exactly.
result: [pending]

### 4. Break-band editor saves and reads back multiple bands

expected: On the Shift Library page, a template can be given two or more break bands. After save and reload, all bands persist and display **ordered by offset ascending** — same order in the editor, the value range, and the template list.
result: [pending]

### 5. Band capacity: 0 rejected, blank means unlimited

expected: Capacity `0` is refused with a clear message (a band nobody can use is a data error). BLANK capacity is accepted and means unlimited. Blank and 0 are not the same value.
result: [pending]

### 6. Duplicate bands rejected, touching bands allowed

expected: Two bands with the SAME offset AND SAME duration are refused as duplicates. Two bands whose break windows merely touch (A ends exactly as B begins) are accepted as distinct and legal.
result: [pending]

### 7. Off-grid band offsets refused, never silently rounded

expected: An offset or duration not landing on the desk's timeslot grid produces a hard 400 with a named, readable message. The value is never silently rounded to fit.
result: [pending]

### 8. Capacity shortfall shows an advisory at save time

expected: When band capacities total below the shift's admissible headcount, the operator sees a named advisory in the Capacity column at save time — not a bare hard score at solve time.
result: [pending]

### 9. Suggested Library returns a draft and writes nothing

expected: Requesting a suggested library returns an editable draft derived from the desk's demand and its agents' contracted hours. **Nothing is persisted until a row is explicitly saved.** Navigating away leaves the library unchanged. Requesting twice for an unchanged desk returns the same suggestion (it is deterministic).
result: [pending]

### 10. Shift-mode solve succeeds at production scale

expected: A real desk in shift-scheduled mode solves to a feasible schedule in acceptable time. The automated benchmark ran only 4 agents x 2 days — this is the first exercise at your real agent count, day count and demand curve.
result: issue
reported: "It seems to be stuck on shift envelope compliance. A big issue is covering 0 hour slots - It pulls to fill the 0 slot but then adds breaks to fill in the gaps!"
severity: blocker
tested_against: dev (https://d2bbtcc80peap7.cloudfront.net), live cloud data

### 11. No agent is seated outside their assigned shift envelope

expected: Every agent works only within the envelope of the single shift assigned to them that day; each working agent-day has exactly one shift. This is the phase's core hard-constraint guarantee.
result: [pending]

### 12. Breaks are distributed across bands, not simultaneous

expected: On a shift with multiple bands, agents sharing that shift are spread across bands rather than all breaking at once, and no band exceeds its capacity. This is the headline user-visible behaviour change.
result: [pending]

### 13. Agent Allocation groups by shift on a shift desk

expected: On a shift-scheduled desk, Agent Allocation in Schedule Results groups agents under their assigned shift, each group naming the shift and its headcount.
result: [pending]

### 14. A slot-scheduled desk is completely unchanged

expected: A desk still in slot mode behaves exactly as before — same Agent Allocation rendering, same solve behaviour, same validation. No shift-mode UI appears on it.
result: [pending]

### 15. UPCOMING and RETIRED templates are not assignable (CR-01 fix)

expected: A template whose `effectiveFrom` is in the FUTURE is not assigned to any agent-day before that date. If it becomes effective partway through the schedule period, it is assignable only from `effectiveFrom` onward — not on earlier days of the same schedule. A RETIRED template (past `effectiveTo`) is likewise never assignable. *Newly written code, fixed after the main phase, with the least field exposure of anything here.*
result: [pending]

### 16. Accepted schedule keeps its true mode (CR-02 fix)

expected: Accept a shift-mode schedule, reopen it — Schedule Results reports SHIFT and renders the shift view. This must hold even for a schedule accepted with few or NO placed shifts, and must not change if the desk's mode is switched afterwards. *Legacy caveat: schedules accepted BEFORE this deploy are backfilled by inference, so a pre-existing shift-mode accept that placed zero shifts will read SLOT. That is unrecoverable — the true fact was never recorded — not a new bug. Verify only that post-deploy accepts are exact.*
result: [pending]

### 17. Deleting an accepted shift schedule leaves no orphans (CR-03 fix)

expected: Delete an accepted shift-mode schedule, then confirm no rows remain:

```sql
SELECT count(*) FROM agent_shift_assignment WHERE schedule_id = '<deleted-schedule-id>';
```

Expected: `0`.
result: [pending]

### 18. Production migration executed safely

expected: Before deploying to PRODUCTION, a restorable snapshot of `shift_template` exists (including `break_offset_minutes` and `break_duration_minutes`), because V40 DROPs both after fanning their data out — if the fan-out is wrong there, the source data is already gone. After the production deploy, Test 1's fan-out query is re-run against production and passes. Dev's four successful runs are a rehearsal, not a substitute: production has different data volume, different pre-existing rows, and different edge cases.
result: blocked
blocked_by: server
reason: No production deploy has occurred. `deploy.yml` targets the dev environment (ECS cluster `wfm-service-dev`) only. Unblock when a production deploy is planned.

## Summary

total: 18
passed: 1
issues: 1
pending: 15
skipped: 0
blocked: 1

## Gaps

- gap_id: G-15-10
  truth: "A real desk in shift-scheduled mode solves to a feasible schedule in acceptable time"
  status: failed
  reason: "User reported: It seems to be stuck on shift envelope compliance. A big issue is covering 0 hour slots - It pulls to fill the 0 slot but then adds breaks to fill in the gaps!"
  severity: blocker
  test: 10
  root_cause: |
    THREE independent defects, AND-gated. No single one produces the symptom; together they make an
    irreducible hard score inevitable AND invisible. Diagnosed by three parallel debug agents, each
    with a passing characterising test; two of the orchestrator's own leading hypotheses were
    REFUTED in the process.

    D1 — ZERO SLACK BY CONSTRUCTION (the precondition).
    AgentShiftAssignment.getEligibleShiftBandPairs() (:165-178) admits ONLY band pairs whose
    netHours EXACTLY equals the agent-day's effectiveHours, and ShiftBandPair.covers() (:31-47)
    excludes the break window. Therefore legal in-envelope slots == expectedWorkSlots EXACTLY: an
    agent must occupy 100% of their legal slots. Proven structural, not coincidental, by sweeping
    every plausible grid/envelope/break combination — NO desk configuration an operator could choose
    absorbs even one missing seat. There is no margin anywhere in the model.

    D2 — SEAT SUPPLY IS ENVELOPE-BLIND (the trigger).
    Seats exist only where demand created them. expandMinimumStaffingSeats (SolverService:1175) then
    tops every timeslot to MIN_AGENTS_PER_TIMESLOT=1 — its parameter list carries no SchedulingMode,
    no ShiftTemplate, no ShiftBandPair, so it CANNOT be shift-aware. It manufactures seats on
    zero-demand hours that no envelope reaches. Compounding it, computeTimeslotDemandConfigs (:753)
    emits no row for a zero-demand timeslot (FteUploadService:202 skips fteValue <= 0), and both bulk
    allocation constraints reach demand through an INNER join — so a zero-demand hour has no
    over-allocation ceiling at all. Not a ceiling of zero: the ABSENCE of the constraint. Proven with
    a matched control (3 agents on a bare hour penalise 0; supply TimeslotDemandConfig(ts,0) and the
    same 3 agents penalise 3).

    D1 + D2 => with zero slack, ONE missing in-envelope seat forces the agent out of the envelope to
    reach contracted hours. The only seats available out there are the zero-demand filler seats.

    D3 — COST ARBITRAGE MAKES THE BREACH RATIONAL (why it lands where it does).
    shiftEnvelopeComplianceWeight is ofHard(1) — the LOWEST hard weight in ConstraintWeights —
    against contractedHoursUnder ofHard(100). The solver pays 1 hard per out-of-envelope seat rather
    than 100 per missing contracted slot. So contracted hours come out EXACT and the entire residual
    parks on the phase's own headline guarantee. The zero-demand hour is additionally the cheapest
    destination on the grid (1 hard, vs 2 hard for a thin-demand hour at its 130% ceiling) BEFORE the
    1000-soft min-staffing bonus is ever consulted — the driver is hard-vs-hard, not soft.

    D4 — THE REPORT LAYER HIDES ALL OF IT (why UAT nearly missed it).
    ScheduleOutputService derives BOTH the displayed envelope (:174-175, min/max over held seats) and
    the displayed breaks (:465-478, findBreaks over seat gaps, signature accepts only
    List<AgentAssignment> so it structurally cannot read the band). The authoritative values ARE
    resolved into ShiftDescriptor at :433-450 and then discarded; bandOffsetMinutes/
    bandDurationMinutes appear in the frontend only as type declarations (client.ts:389-390), read
    nowhere. So the schedule table SILENTLY REDRAWS THE ENVELOPE around a violating seat and cannot
    render an envelope violation as a violation. Agent Allocation's group header
    (ScheduleResults.tsx:632) DOES use the authoritative template times — which is why the same
    agent-day reads "Late 12:00-21:00" in the header and "09:00-21:00" in the table. Two views of one
    fact disagree, and THE DISAGREEMENT IS THE VIOLATION, surfaced as one nowhere. The mislabelling
    propagates into ScheduleExportService:129 and the preference report's actualBreakTime /
    breakTimeHonouredCount KPIs.

    D5 — LATENT, INDEPENDENT: breakBlockedHours has NO enforcement point in SHIFT mode.
    breakBlockedWindow is gated off (ScheduleConstraintProvider:307-309) and ShiftTemplateService's
    band validation (:177-235) never checks band offset against it. A band at offset 0 or at the
    envelope's trailing edge saves cleanly and scores 0 hard, producing a full unbroken working day.
    In slot mode that shape cost ofHard(10). Explains Mariami Katcheishvili 2026-01-10: 8 consecutive
    hours, zero breaks.

    DISCRIMINATOR IDENTITY (verifiable against live data). Because contracted hours are satisfied,
    the pair (|held\legal|, |legal\held|) separates all three candidate mechanisms — asserted in test:
      seat supply       -> EQUAL                  (measured 4 / 4)
      envelope capacity -> illegal EXCEEDS surrendered (measured 2 / 0)
      null band pair    -> maximally asymmetric   (measured 15 / 0)
    So a desk at -19 with EXACTLY 19 unworked in-envelope slots is pure seat-supply, has no null-pair
    agent-days, and has no capacity shortfall.

    REFUTED HYPOTHESES (recorded so gap-closure does not re-litigate them):

    - "Contracted hours exceed band net hours" — IMPOSSIBLE. The value-range filter forbids it.
      The defect is the EQUALITY (zero slack), not an excess.

    - "Null shiftBandPair explains the screenshot" — ruled out arithmetically. On a 60-minute grid one
      null agent-day costs 8 hard, a week for one agent 56, against an observed 19.

    - "Break geometry is ungoverned in shift mode" — largely REFUTED as to significance. The
      eligibility filter forces held == legal in any feasible solution, so ENVL-04 genuinely holds.
      Only breakBlockedWindow's removal left a real hole (D5). DO NOT restore the gated constraints:
      that fights the envelope model and could make under-supplied desks unsolvable.

    - "The PT5M default / small benchmark caused this" — REFUTED. More time cannot clear an
      irreducible floor (measured identical at 2,000 and 40,000 step budgets). The benchmark missed
      it by SHAPE, not scale: ShiftModeFixtures:58-59 makes every template span the whole operating
      window, so an out-of-envelope timeslot cannot exist, and :245 never calls
      expandMinimumStaffingSeats. 400 agents x 60 days would have missed it identically.

    PRE-REGISTERED AND UNCLOSED: .planning/research/SPIKE-COUPLING.md:432 flagged this exact risk
    before Phase 15 was planned — "Whether the envelope constraint interacts badly with the break
    window or contracted-hours logic is unknown." expandMinimumStaffingSeats and
    MIN_AGENTS_PER_TIMESLOT appear in ZERO Phase 15 planning artifacts.
  artifacts:

    - path: "src/main/java/com/wfm/service/SolverService.java"
      issue: "expandMinimumStaffingSeats (:1175) structurally envelope-blind — no SchedulingMode/ShiftTemplate/ShiftBandPair in its parameter list; computeTimeslotDemandConfigs (:753) emits no row for zero-demand timeslots, removing the over-allocation ceiling entirely"

    - path: "src/main/java/com/wfm/model/AgentShiftAssignment.java"
      issue: "getEligibleShiftBandPairs (:165-178) exact-equality net-hours filter leaves ZERO slack; empty range degrades silently to null"

    - path: "src/main/java/com/wfm/model/ConstraintWeights.java"
      issue: "shiftEnvelopeComplianceWeight ofHard(1) (:138) is the cheapest hard weight in the model, making the phase's headline guarantee the default dumping ground for hard tension"

    - path: "src/main/java/com/wfm/service/ScheduleOutputService.java"
      issue: "Envelope (:174-175) and breaks (:465-478) both derived from seats, not from template/band; authoritative ShiftDescriptor (:433-450) resolved then discarded — UI cannot render an envelope violation"

    - path: "frontend/src/pages/ScheduleResults.tsx"
      issue: ":868 and :882-885 render derived values while :632 renders authoritative ones — same agent-day shows two different shift spans"

    - path: "src/main/java/com/wfm/service/ShiftTemplateService.java"
      issue: "Band validation (:177-235) never checks offset against breakBlockedHours, and never validates envelope containment within the operating window (TimeslotBoundsResponse.endTime() is read by no caller in src/main)"

    - path: "src/test/java/com/wfm/solver/ShiftModeFixtures.java"
      issue: "Templates span the entire operating window (:58-59) and expandMinimumStaffingSeats is never called (:245) — feasible by construction, so the suite CANNOT catch this class of regression at any scale"

    - path: "src/test/java/com/wfm/solver/ScheduleConstraintClassification.java"
      issue: ":248 still classifies Minimum staffing as MODE_AGNOSTIC; Phase 15 reclassified six other constraints and left this row stale"
  missing:

    - "Make seat supply inside an envelope GUARANTEED rather than hoped for — the zero-slack identity means there is no margin to absorb a single missing seat"
    - "Suppress the min-staffing filler seat on a SHIFT desk where no live ShiftBandPair covers the timeslot (agent 1's primary lever)"
    - "Pre-solve refusal when per-slot seat supply inside any envelope is below admissible headcount"
    - "Migrate ScheduleOutputService to read ShiftDescriptor's band/template in shift mode; keep findBreaks for slot mode; render true-envelope vs actual-seat DIVERGENCE as a visible violation instead of absorbing it"
    - "Validate envelope containment within the operating window, and band offset against breakBlockedHours, at save time in ShiftTemplateService"
    - "Refuse rather than advise an hours mismatch (currently a page advisory reading 'It will still save')"
    - "Fix ShiftModeFixtures (templates narrower than the operating window; route seats through the real expandMinimumStaffingSeats) and the stale MODE_AGNOSTIC row — otherwise the suite stays blind"
    - "CAUTION: raising shiftEnvelopeComplianceWeight above contractedHoursUnder inverts the arbitrage but only changes WHERE infeasibility surfaces, not WHETHER. Both agents independently warned against it as a primary fix"
    - "ShiftModeBreakGeometryCharacterisationTest passes ON the defect — it is diagnostic only and must be replaced, not kept green"
  debug_session: ".planning/debug/min-staffing-seats-zero-demand.md, .planning/debug/shift-envelope-unsatisfiable-hard.md, .planning/debug/shift-mode-break-geometry-ungoverned.md"
  characterising_tests: ".planning/debug/characterising-tests/ (4 files, all passing against the defect)"
  preliminary_findings:  # Orchestrator code-read, NOT yet a confirmed diagnosis

    - "ShiftBandPair.covers() excludes the band's break window, so shiftEnvelopeCompliance (hard)
       caps an agent's legal seats at the template's NET slots. contractedHoursOver/Under are also
       hard and demand EXACTLY expectedWorkSlots(dayConfig). When an agent's contracted hours exceed
       the net hours of the band pair they hold, the two hard constraints are jointly unsatisfiable —
       the residual hard score lands on Shift envelope compliance and never clears. Suspected direct
       cause of 'stuck on shift envelope compliance'."

    - "Zero-demand timeslots get no TimeslotDemandConfig (computeTimeslotDemandConfigs runs over
       demand-derived assignments only, BEFORE filler seats are appended). Both bulk over- and
       under-allocation constraints join on that fact, so they are SILENT on a zero-demand hour.
       minimumStaffing (soft 1000, the dominant soft weight) is therefore the only term acting there,
       and expandMinimumStaffingSeats has already created a seat to satisfy it. The solver spends a
       scarce contracted slot on a zero-demand hour because it is worth more soft score than a real
       demand hour. Suspected cause of 'it pulls to fill the 0 slot'."

    - "expandMinimumStaffingSeats is envelope-blind: it adds a seat on every uncovered timeslot with
       no awareness of whether any shift envelope reaches it. A seat outside every envelope can only
       be filled by incurring a hard shiftEnvelopeCompliance penalty, so minimumStaffing's soft 1000
       and shiftEnvelopeCompliance's hard weight pull against each other with no feasible resolution."

    - "In SHIFT mode all four break-geometry constraints AND honourPreferredBreakTime are gated off
       (ifExists cfg.schedulingMode() != SHIFT). Band choice is a free planning variable constrained
       only by bandCapacity, so the solver can select whichever band puts its break window over an
       hour it would rather not staff. Nothing replaces breakBlockedWindow's guard in shift mode.
       Suspected cause of 'adds breaks to fill in the gaps'."

    - "ShiftLibraryValidationService reconciles template net hours against contracted hours, but only
       as a Shift Library page advisory — there is no solver-time guard, so a desk can be solved with
       templates no agent's contracted hours can satisfy."
  screenshot_evidence: "src/main/resources/sample-data/Example.png — Agent Allocation, group
    'Late · 12:00–21:00 · 10 agent(s)'. Three things are visible and each narrows the diagnosis:
    (1) Evelina Yasinchuk and Melina Noemi Aparicio are SEATED in a column left of 12:00 — outside the
        Late envelope. shiftEnvelopeComplianceWeight is ofHard(1), so a feasible solve could never do
        this: the grid is a best-so-far solution carrying unresolved HARD penalty. This is the
        screenshot form of 'stuck on shift envelope compliance'.
    (2) Those same two agents carry FOUR separate B blocks (three consecutive early, one mid-shift)
        while every other agent carries exactly one. With exactlyOneBreak/breakDuration/
        breakBlockedWindow/breakStartAlignment all mode-gated off in SHIFT mode, nothing forbids a
        fragmented multi-block break — 'adds breaks to fill in the gaps'.
    (3) All 10 agents in the group break in the SAME column (the grey column, total 8). User
        confirmed this template is SINGLE-BAND, so a shared break column is correct behaviour here,
        NOT a Test 12 failure. Test 12 remains untested — it needs a multi-band template.
    Column totals across the row (4, 2, 4, 8, then 17, 16, 15, 14, 8, 16, 16, 14, 10) show the early
    columns are the thin/zero-demand hours — exactly where the two out-of-envelope agents were pulled,
    consistent with minimumStaffing filler seats being envelope-blind."
  open_question: "Which of the above is the actual trigger cannot be settled by code-reading alone —
    it needs the solver score breakdown from the stuck dev solve (which constraint holds the residual
    hard score, and what band pair those two agents were assigned)."
  live_run_evidence: "User re-ran with an extended solve time. At 15 MINUTES the hard score had
    dropped to -19 and was still falling. This materially reframes the gap: the solve is NOT frozen
    on an irreducible contradiction — it is CONVERGING, just far too slowly for the shipped defaults.
    Two live possibilities remain, distinguished by whether the score reaches 0 or flattens:
      (a) Converges to 0hard given time -> the defect is scale/config, not constraint modelling.
          solver.time-limit defaults to PT5M (application.yml:53) and SolverService:449 derives
          unimprovedSpentLimit = max(30s, 30% of total) = 90s at that default. A production-scale
          desk is therefore killed at 5 minutes, or after only 90s of plateau, long before it can
          reach feasibility. 15-BENCHMARK.md only ever exercised 4 agents x 2 days, so this was
          never going to surface pre-UAT — it is precisely the risk Test 10 was written to catch.
      (b) Flattens at ~-19 and then terminates on the unimproved limit -> -19 is the fingerprint of
          a small structurally-infeasible subset of agent-days, which is the leading hypothesis under
          investigation (contracted hours exceeding the net hours of every available band pair).
    Also implicated regardless of which holds: solverConfig.xml:69 sets the simulated-annealing
    starting temperature to 0hard/3000soft — ZERO hard tolerance, so local search can never accept a
    transiently worse hard score. Escaping a hard local optimum usually requires exactly that
    (vacate a seat before another agent can take it), so this acceptor setting plausibly explains
    slow hard-score convergence on its own."
  actionable_now: "solveTimeSeconds is exposed on SolveRequest (SolveRequest.java:21), so extended
    solve times are testable against dev without a redeploy."
  decisive_evidence: |
    Desk Stubhub (EN), period 2026-01-05 to 2026-01-11 (7 days). At 20m41s: Hard -19, Soft -89,
    NOT FEASIBLE, still RUNNING. Hard had been -19 at 15m — flat for 5.5 minutes while soft kept
    moving. UI reported: "NON-OPTIMAL SOLUTION — Violated hard constraints: Shift envelope
    compliance" — that constraint ALONE.

    The score COMPOSITION settles the mechanism, because the hard weights are distinct enough to
    fingerprint. shiftEnvelopeComplianceWeight is ofHard(1), so -19 == exactly 19 out-of-envelope
    seats. Decisively ABSENT: contractedHoursOver (ofHard(1001)), contractedHoursUnder (ofHard(100)),
    contractedHoursUnderZero (ofHard(100)), agentDayOff (ofHard(10_000)), noOverlap (ofHard(1000)).
    None of those could hide inside a total of 19. Therefore every agent IS working EXACTLY their
    contracted hours, while 19 of those seats sit outside any shift envelope.

    That combination is only reachable one way: agents are FORCED to take out-of-envelope seats in
    order to reach contracted hours. The solver is not making a mistake — it is choosing the
    CHEAPEST available hard violation. Missing a contracted slot costs 100; taking a seat outside
    the envelope costs 1. Given a shortage of legal in-envelope seats, parking the infeasibility on
    Shift envelope compliance is the rational move, and it will do so every time.

    Two candidate sources of that shortage, both still live and both already assigned to debug
    agents:
      (i)  SEAT SUPPLY. Seats are created by demand fan-out; expandMinimumStaffingSeats then adds
           filler seats on timeslots with no demand-derived seat — precisely the zero-demand hours,
           which lie OUTSIDE every envelope (envelopes pack against the first non-zero hour). If
           in-envelope seats are fewer than an agent's contracted slots, the only seats left to
           take are the out-of-envelope filler seats. This exactly reproduces the user's original
           words: "It pulls to fill the 0 slot."
      (ii) ENVELOPE CAPACITY. ShiftBandPair.covers() excludes the break window, so legal seats per
           agent-day cap at the template's NET slots. Any agent whose contracted hours exceed their
           band pair's net hours must overflow by construction.

    IMPORTANT WEIGHTING FINDING, independent of which source holds: shiftEnvelopeComplianceWeight
    ofHard(1) is the LOWEST hard weight in ConstraintWeights. The phase's headline guarantee — no
    agent seated outside their shift envelope — is thereby the cheapest hard constraint in the model
    to violate, making it the default dumping ground for ANY hard tension anywhere in the system.
    That is a modelling smell worth addressing regardless of the seat-supply fix.
  termination_finding: |
    withUnimprovedSpentLimit (SolverService.java:454) triggers on lack of improvement to the OVERALL
    best score, so continuing SOFT improvement resets the timer while HARD sits frozen. A run can
    therefore burn its entire window chasing soft score with a permanently stuck hard score and
    never self-terminate early. Observed directly here: hard flat 15m -> 20m41s, still RUNNING.
