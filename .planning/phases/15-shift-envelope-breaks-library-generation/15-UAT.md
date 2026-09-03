---
status: complete
phase: 15-shift-envelope-breaks-library-generation
source: [15-01-SUMMARY.md, 15-02-SUMMARY.md, 15-03-SUMMARY.md, 15-04-SUMMARY.md, 15-05-SUMMARY.md, 15-06-SUMMARY.md, 15-07-SUMMARY.md, 15-08-SUMMARY.md, 15-09-SUMMARY.md, 15-10-SUMMARY.md, 15-11-SUMMARY.md, 15-12-SUMMARY.md, 15-13-SUMMARY.md, 15-VERIFICATION.md]
started: 2026-08-27T13:10:00Z
updated: "2026-09-03T03:05:00Z"
---

<!--
WHERE TO TEST

Phase 15 is deployed to the dev environment:

  https://d2bbtcc80peap7.cloudfront.net        (/actuator/health -> UP, Postgres connected)

>>> CURRENT AS OF 2026-09-01T19:00Z — commit `a320ca7` is live (V45 contiguity constraint +
>>> V46 default of 10 + the UI weights page). Re-derived from infrastructure, not from any
>>> recorded claim, per the standing rule below:
>>>     ECS image tag   wfm-service:a320ca77b7d45a0111bcfa1f21c7a01229d41876  (task def :65)
>>>     rollout         PRIMARY, 1/1 running, COMPLETED 18:46:28Z
>>>     Flyway          "now at version v46", applied 18:44:35Z
>>>     frontend        bundle index-B6kWCkcj.js carries the three new weight labels
>>> HANDOFF.md §1 previously recorded this deploy as FAILED. That was wrong — it was written
>>> mid-run; the run passed on attempt 1. See HANDOFF.md §1 for the correction.
>>>
>>> TWO API NOTES for anyone testing by curl rather than the UI:
>>>   - /api/v1/** returns a bare 400 with no message unless you send `X-Tenant-ID: 1`.
>>>   - /actuator/info returns INTERNAL_ERROR, so there is no build-info endpoint; the ECS
>>>     task definition's image tag is the only reliable way to read the deployed SHA.
>>>
>>> CORRECTED 2026-08-27T21:22Z. The paragraph below is preserved because its REASONING is
>>> still sound, but its CONCLUSION expired the moment the gap-closure round landed. What it
>>> asserted was true of the main phase and is NOT true of the phase as it now stands. Read
>>> the correction under it before trusting any deployment claim in this file.
>>>
>>>   [SUPERSEDED] "Deployed commit: `adaad6d` — the merge of both defect-fix worktrees.
>>>   Verified to contain NO source difference from the phase's final HEAD
>>>   (`git diff adaad6d..HEAD -- . ':!.planning/'` is empty); every commit after it touches
>>>   only `.planning/**`, which `deploy.yml` excludes via `paths-ignore`. So the live dev
>>>   service runs exactly the code this phase verified, including the CR-01/CR-02/CR-03
>>>   fixes."
>>>
>>> WHAT WAS ACTUALLY TRUE at the moment this UAT session resumed: `adaad6d` was still the
>>> last deployed commit, but it was NO LONGER equal to HEAD in source. `adaad6d..HEAD`
>>> excluding `.planning/` contained 17 commits and ~4,086 changed lines across 24 files —
>>> the ENTIRE G-15-10 gap-closure round: `f893e97` (envelope-aware seat expansion),
>>> `4492355` (the seat-supply gate), `1bd953b`/`a54abc3`/`ce3c502` (authoritative-envelope
>>> rendering), `52a1656` (the end-to-end regression). SolverService.java +313,
>>> ScheduleOutputService.java +164.
>>>
>>> The reason the deploy history looked reassuring is the trap: those 17 commits had never
>>> been PUSHED. The branch was 30 commits ahead of origin. `deploy.yml` fires on push, so
>>> no deploy could have carried them, and dev was serving the PRE-FIX build — the exact
>>> build that filed G-15-10. Running test 10 against it would have re-measured the defect
>>> and read as confirmation that the fix failed.
>>>
>>> RESOLVED: branch pushed 21:22Z (`4ae9492..5b7bd15`), deploy run 33117833899 triggered on
>>> `5b7bd15`. Tests 10, 19 and 20 are meaningful only against that deployment or later.
>>>
>>> STANDING RULE for anyone resuming this file: do not trust a recorded deployment claim.
>>> Re-derive it. `git log --oneline origin/<branch>..HEAD -- . ':!.planning/'` must be EMPTY
>>> and the newest successful `deploy.yml` run's headSha must equal HEAD. A local commit is
>>> not a deployed commit.
>>>
>>> STILL UNCOMMITTED at push time, therefore NOT in the deployed image:
>>>   src/main/resources/sample-data/preferences.xlsx (modified, sample data only)

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

[testing complete]

ALL 20 NUMBERED TESTS RESOLVED — 20 pass, 0 issues, 0 blocked, 0 pending. Two were closed by
operator ruling rather than by fresh measurement, and each says so in its own entry: test 10
(2026-09-02, the desk's residual judged acceptable) and test 18 (2026-09-02, "no cloud is dev" —
no separate production tier exists, and Test 1 already verified the fan-out against the one live
environment). Status is `complete` because no pending, blocked, or reasonless-skipped item remains.

STILL OPEN AND NOT CLOSED BY ANY OF THIS: G-15-28, the weekend demand forecast, operator-owned.

RESUMED 2026-09-02. The pause note below is kept because its reasoning still explains how the file
got here; its "RESUME AT TEST 13" instruction has now been carried out.

PAUSED 2026-09-02 by operator decision ("fix everything") after test 11 passed. UAT did not stop
because it was blocked — it stopped because the open GAPS were routed to a planned gap-closure
round in preference to continuing to accumulate findings against un-fixed code.

RESUME AT TEST 13. Its expected Agent Allocation grouping was already computed from the API for
schedule 6a10afa1 and is recorded in test 13's `precomputed_expectation` block below, so whoever
resumes compares against a table rather than re-deriving it. NOTE: if the gap-closure round
changes the solver or the library, RE-COMPUTE that table before using it — it describes schedule
6a10afa1 specifically, not whatever is newest at resume time.

>>> THE NOTE ABOVE WAS ACTED ON, 2026-09-02T17:00Z. The gap-closure round DID change both the
>>> solver and the library, so the table was re-derived against the shipped build rather than
>>> reused. Counts came back identical on all seven dates; the block is rewritten in the page's own
>>> sort order and the G-15-32 caveat under it is retired. See test 13.

<!--
  SESSION 2026-09-02 (resumed). Deployment re-derived per the standing rule BEFORE any testing, and
  this time the rule EARNED ITS KEEP — it caught a trap that would have wasted the whole sitting.

    AT RESUME:  HEAD 64bafd8, but `git log @{u}..HEAD -- . ':!.planning/'` showed 17 UNPUSHED source
                commits, and unlike the 2026-09-01 sitting these were NOT test-only. The runtime
                diff (excluding src/test/) was 6 files, +650/-98:
                  GlobalExceptionHandler +36   ScheduleOutputService +164   ScheduleService +13
                  ShiftLibraryGenerationService +272   SolverService +252
                  ScheduleConstraintProvider +11
                Newest successful deploy was 33543912228 on a320ca7 (2026-09-01T18:29Z). So dev was
                serving PRE-gap-closure code: the ENTIRE 15-16..15-20 round was local only.

                Every remaining test touches at least one of those six files (13/19 ->
                ScheduleOutputService; 14/16/17 -> ScheduleService; 15 -> SolverService; 10/20 are
                retests of the changed paths). Running any of them would have re-measured the
                un-fixed build and read as the fixes having FAILED — the identical trap the
                2026-08-27 sitting fell into and wrote the standing rule to prevent.

    RESOLVED:   operator authorised the push. gh active account was `pcornwell` and was switched to
                `petecornwell-code` first. Pushed 5bf636e..64bafd8; unpushed source commits -> 0.
                Deploy run 33656348187 on 64bafd8 SUCCESS.

    RE-DERIVED FROM INFRASTRUCTURE, not from the green tick:
                ECS task def   wfm-service-dev:66, PRIMARY, 1/1, rolloutState COMPLETED
                image tag      wfm-service:64bafd87948723440dddd9338dd19368b53e3b2e  == HEAD
                /actuator/health  UP, db UP on PostgreSQL
                Flyway         no migration files changed a320ca7..HEAD — this round is code-only,
                               so there is no new schema version to confirm. V46 remains current.

  NOTE ON THE STANDING RULE'S REFINEMENT: the 2026-09-01 sitting relaxed "unpushed source log must
  be EMPTY" to "empty after excluding ':!src/test/'". That relaxation held up here and is what made
  the danger legible rather than ambiguous — the exclusion did NOT clear the log this time, which is
  precisely the signal it was designed to give. Keep both forms: the conservative check first, the
  refined one only after LOOKING at the file list.

  Gap reconciliation on resume: 0 newly reconciled. No gap carries `status: failed`, so the
  reconcile pass had nothing to act on. Standing: 10 resolved, 1 closed_pending_retest (G-15-10),
  1 open (G-15-28, weekend demand forecast, operator-owned).
-->

<!--
  MEASURED IN PASSING 2026-09-02 while re-deriving test 13's table, and it BEARS ON TEST 19 — read
  before running it. On accepted schedule 6a10afa1 under the shipped build, 17 agent-days carry a
  non-null `divergence`, and they break down as:

      total outOfEnvelopeSeats:  0
      total unworkedLegalSlots: 17

  So this schedule has NOTHING to exercise the `E!` rendering with — zero out-of-envelope seats.
  Test 19's second and third bullets (the inline divergence marker, and `E!` being visually distinct
  from `x`) CANNOT be observed on 6a10afa1. Only the `x` treatment can.

  That is consistent with the desk having reached hard 0 and is not itself a defect — but test 19's
  own wording anticipated it ("or force one"). No forcing is needed: all six accepted schedules were
  read on the shipped build and three of them carry real out-of-envelope seats already, so test 19
  can be run against stored data without perturbing the live library.

    id        hard  soft   oosSeats  unworkedLegalSlots  violatedHardConstraints
    6a10afa1     0   -68          0                  17                        0
    b88cc98f     0   -60          0                   0                        0
    523c8785     0   -76          0                   0                        0
    709fd8b4    -1   -70          1                  31                        1   <-- minimal E! case
    e6728aab    -9   -67          9                  37                        1
    9bd158dd   -12   -62         12                  41                        1   <-- richest E! case

  RECOMMENDED FOR TEST 19: run the `E!`-vs-`x` distinction on 709fd8b4 first — exactly ONE
  out-of-envelope seat against 31 unworked legal slots is the sharpest possible test of whether the
  two markers are visually distinguishable, because the rare one must not get lost among the common
  one. Then 9bd158dd for density. Use 6a10afa1 for the "unstaffed by design" and tooltip bullets.

  UNASKED-FOR CORROBORATION OF G-15-32, recorded because it was observed. `oosSeats` equals
  |hardScore| EXACTLY on all six schedules — 0, 0, 0, 1, 9, 12 against hard 0, 0, 0, -1, -9, -12.
  The G-15-32 defect was that every accepted schedule reported a CONSTANT 1104 violations
  regardless of its true count, and the resolution entry lists precisely this series (12, 9, 1, 0,
  0, 0) as the counts that all wrongly read 1104. Re-read live on the shipped build, each one now
  reports its own true figure. That is independent confirmation of the fix from the read path an
  operator actually uses, not from the test that was written to cover it.
-->


<!--
  SESSION 2026-09-01 (resumed, second sitting). Deployment re-derived per the standing rule BEFORE
  any testing — and the derivation has a WRINKLE worth stating precisely, because a naive reading
  of the standing rule would have HALTED here for no reason:

    `git log @{u}..HEAD -- . ':!.planning/'` is NOT empty — 5 unpushed source commits.
    BUT all five are TEST-ONLY: SolverQualityGuardTest.java + LiveShapeShiftDeskFixture.java,
    +1,559 lines, ZERO files under src/main or frontend/src. The decisive check is therefore
    `git diff a320ca7..HEAD -- . ':!.planning/' ':!src/test/'`, which IS empty.
    So dev's RUNTIME code equals HEAD's runtime code; only the guard tests are local-only.

    Deploy run 33543912228 on a320ca7 — success. ECS task def wfm-service-dev:65, PRIMARY, 1/1,
    rolloutState COMPLETED. /actuator/health UP, db UP on PostgreSQL.

  REFINEMENT TO THE STANDING RULE, earned here: the rule says the unpushed-source log must be
  EMPTY. That is the safe default, but it is too strong — it cannot distinguish a test-only commit
  (which cannot change live behaviour) from a src/main commit (which does). Exclude ':!src/test/'
  from the diff and the rule becomes exact instead of merely conservative. Keep the conservative
  form as the first check; only relax it after LOOKING at the file list, never on the commit
  subjects alone.

  Gap reconciliation on resume: 0 newly reconciled. No gap carries `status: failed`, so the
  reconcile pass had nothing to act on — G-15-22, G-15-27, G-15-29 and G-15-30 were already
  written up as `resolved` with their evidence by the 15-14/15-15 round. Standing: 4 resolved,
  1 closed_pending_retest (G-15-10), 7 open (G-15-21, -23, -24, -25, -26, -28, -31).

  Resuming at test 3, still carrying the observability problem raised at the last resume.
-->

<!--
  SESSION 2026-08-31 (resumed). Deployment re-derived per the standing rule BEFORE any testing:
  branch fully pushed (@{u}..HEAD empty); newest successful deploy.yml run 33431842459 on 9cd4703;
  `git diff 9cd4703..HEAD -- . ':!.planning/'` EMPTY — the only commit since is .planning-only.
  Dev IS serving current HEAD. /actuator/health UP, db UP on PostgreSQL.
  Not in the image: src/main/resources/sample-data/preferences.xlsx (uncommitted, sample data).

  Gap reconciliation on resume: 0 resolved, 4 open. No gap carries `status: failed`, so none was
  re-diagnosable. G-15-10 remains closed_pending_retest; G-15-21/22/23 have no fix plans yet.

  TESTABILITY PROBLEM FOUND AT RESUME — test 3 may no longer be observable. See test 3's
  observability_problem block. The live desk's Phase-14-era single-break state no longer exists.
-->


<!--
  SESSION 2026-08-27 .. 2026-08-31. Test 10 was retested end to end and its mechanism drove the
  whole session; tests 3, 11, 13-17 and 19 were never reached.

  WHAT SHIPPED THIS ROUND (all deployed to dev and verified by ECS image tag, not by a green tick):
    b2dd702  validWeekdays enforced in the solver; warnings list made idempotent
    00d675d  break-concentration advisory
    7298f96  generator emits multi-band capped templates
    461483d  generator expands spans by supply, not just coverage
    a4a3115  generator clusters templates by demand shape, not by the calendar
    1f66211  peak-hour shortfall check
    81117e3  bounded envelope slack (V44) + shift-library delete control
    6c82241  seat expansion asks whether the library reaches an hour ON THAT DATE
    5077a75  acceptor 0hard -> 1hard   <-- REGRESSED THE LIVE DESK 7x
    9cd4703  revert of 5077a75

  IF YOU READ ONE THING: the -6 -> -12 step in test 10's progression is NOT a regression, and the
  acceptor experiment IS. Both are documented under test 10; conflating them would send the next
  round after the wrong mechanism.

  WHERE TO PICK UP:
    1. G-15-22 first — there is no automated guard on solver tuning, and both remaining routes to
       closing G-15-10's residual are solver-tuning changes.
    2. G-15-21 — third site of the calendar-blindness class, over-counts library supply.
    3. Then tests 11 and 19, the highest-value of the unreached ones. Note test 19 was written
       before bounded slack existed and its unworkedLegalSlots expectation is now WRONG: with
       slack, unworked legal slots are normal and no longer a defect signal.
-->

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
result: pass
tested_against: dev (https://d2bbtcc80peap7.cloudfront.net), live cloud data, 2026-08-27T21:26Z
method: |
  Read via the live API rather than SQL — GET /api/v1/desks/{id}/shift-templates with header
  `X-Tenant-ID: 1` returns each template's `bands[]` (offsetMinutes, durationMinutes,
  breakStartTime, breakEndTime, capacity, netHours), which answers the same question as the
  planned SQL without needing DB credentials. Build-independent: V40 had already run, so this
  was answerable during the redeploy window.
evidence: |
  Desk Stubhub (EN) (6170be17-3bee-41da-9d81-62ddd50c786f) — the tenant's ONLY desk, SHIFT mode.
  All 4 templates, each with EXACTLY 1 band, all capacities NULL (= unlimited, per D-03):

    Early          08:00-17:00   1 band   offset 240m / dur 60m -> 12:00-13:00   net 8.0h
    Weekend Early  10:00-19:00   1 band   offset 240m / dur 60m -> 14:00-15:00   net 8.0h
    Weekend Late   11:00-20:00   1 band   offset 240m / dur 60m -> 15:00-16:00   net 8.0h
    Late           12:00-21:00   1 band   offset 240m / dur 60m -> 16:00-17:00   net 8.0h

  Templates with >1 band: none. Templates with 0 bands: none.

  STRONGEST CORROBORATION: V40's own header documents the pre-migration live state as "~15
  Early-shift agents all break 12:00-13:00". Early's band still reads 12:00-13:00 — the value
  survived both the fan-out and the DROP COLUMN intact. That is an independent pre-migration
  record of the expected value, which is otherwise unrecoverable.

  STRUCTURAL ARGUMENT (why the residual risk is one-sided): V40 is a single
  INSERT...SELECT ... WHERE break_duration_minutes > 0. It CANNOT emit more than one band per
  template, so duplication was never possible; the only failure mode it carries is a DROPPED
  break, and none were dropped. Note also that a row with break_duration_minutes > 0 AND a NULL
  break_offset_minutes would have violated the band table's `offset_minutes INTEGER NOT NULL`
  and failed the migration — so deploy success independently proves no such row existed.
user_confirmed: |
  The one thing the live data cannot settle is whether some template was break-FREE in Phase 14
  and wrongly acquired a band. Operator confirmed all four templates did carry a one-hour break
  before this phase, closing that residual.
reported: "yes"

### 2. All four migrations apply cleanly on Postgres (dev)

expected: V40-V43 apply with no error and the app starts under `ddl-auto: validate`.
result: pass
reported: "Evidenced by deploy history, not by manual test: four successful dev deploys (runs at 00:27, 02:18, 05:07, 11:51, 12:42 UTC on 2026-08-27), with /actuator/health reporting UP and db UP on PostgreSQL. Under ddl-auto=validate + flyway.enabled=true, a migration/entity disagreement fails startup, so a passing health check is positive evidence. Confirm if desired with: SELECT version, success FROM flyway_schema_history WHERE version IN ('40','41','42','43');"

### 3. No Phase 14 desk's validation verdict moved

expected: For a desk that existed before this phase and uses single-break templates, the Shift Library validation result — coverage verdict, net hours, grid-alignment verdict — is identical to before. This is the phase's own stated invariant: a one-band template must reproduce its single-offset predecessor exactly.
result: pass
resolved_via: |
  ROUTE (b), chosen by the operator 2026-09-01 ("I like (b) - do it now"). The literal test — "no
  Phase 14 desk's verdict MOVED" — remains permanently unobservable (see observability_problem
  below; UAT consumed its own baseline). What route (b) tests instead is the INVARIANT the test
  exists to protect, stated in the test's own second sentence: *a one-band template must reproduce
  its single-offset predecessor exactly.* That is now demonstrated, and demonstrated as a FUNCTION
  of the offset rather than at a single point.
tested_against: |
  A throwaway DESK — `ZZ-UAT3-SCRATCH-phase14-invariant` (3d0a0df8-b785-4206-ba2b-956618905205),
  created and DELETED within this session. Deliberately a desk and not a template: test 5b's
  process failure was writing disposable rows into a live desk whose disposal route was documented
  as absent. DeskController CAN delete, and `DeskService.deleteDesk` leaves shift_template to V39's
  DB-level ON DELETE CASCADE (with V40's band FK cascading onto that in turn), so the disposal is
  complete rather than merely tidy. VERIFIED, not assumed: after DELETE, GET desk -> 404 and GET
  its shift-templates -> `[]`. Live desk Stubhub (EN) re-listed after: still 11 templates, untouched.
  Setup: 9 hourly timeslots Mon 2026-01-05 08:00-17:00, demand 1 FTE on every hour, one
  specialization. No agents — deliberately, see scope_note.
the_mechanism: |
  ShiftLibraryValidationService's own class javadoc states the claim being tested:
    "a template with exactly one band produces the byte-identical verdict Phase 14's single offset
     produced, because the any-band quantifier over a one-element set is the element itself."
  `covers()` (:227) ends `return bands.stream().anyMatch(band -> !overlapsBreak)`. Over a singleton
  that reduces to the predicate on that one element — which IS Phase 14's rule ("covered iff the
  window does not overlap the break"). The argument is sound by inspection; the runs below are what
  turn it from an argument into a measurement.
evidence: |
  COVERAGE VERDICT — the uncovered set tracks the single band exactly, and nothing else moves:

    bands                         uncoveredWindows
    ---------------------------   --------------------------------
    one, offset 240 (12:00-13:00) ['2026-01-05 12:00-13:00']   <-- exactly the break hour
    three, 180/240/300            []                          <-- D-02 self-cover
    one, offset 240 (again)       ['2026-01-05 12:00-13:00']   <-- reversible, deterministic
    ZERO bands                    []                          <-- Phase 14's "no break"
    one, offset 60  (09:00-10:00) ['2026-01-05 09:00-10:00']   <-- tracks the offset

  The three-band and zero-band rows are the CONTROLS, and they are what make the one-band rows
  meaningful: they prove the quantifier genuinely varies with band composition, so "exactly the
  break hour" is a measured property and not vacuously true of any library on this desk.
  misalignedTemplates was [] throughout; hasLiveDemand true throughout.

  NET HOURS — envelope 08:00-17:00 = 9h:
    one band, duration 60   -> netHours 8.0     (9h - 1h, the Phase 14 formula)
    bands 60 and 120        -> netHours 8.0 and 7.0
  netHours is now PER BAND (it moved into BreakBandResponse; Phase 14 carried one per template),
  so for a ONE-band template it reduces to Phase 14's single value by the same singleton argument.
  CORROBORATION: test 1 recorded the four Phase-14-era live templates at "net 8.0h" on exactly this
  9h-envelope/60m-break shape. The scratch reproduction returns the same 8.0.

  GRID-ALIGNMENT VERDICT — one-band path, off-grid offset 250:
    HTTP 400 "Shift template times must align to the desk's timeslot grid"
      bands[0].breakStartTime  value 12:10
      bands[0].breakEndTime    value 13:10
  Both derived boundaries named, never silently rounded, and the refused PUT left the template on
  its previous state (re-read after: still one band 240/60) — no partial write.
bonus_finding: |
  D-08's "the report and the refusal can never disagree" was exercised for free and HOLDS. With the
  one-band library leaving 12:00-13:00 uncovered, PUT /scheduling-mode {SHIFT} was REFUSED:
    "1 demand window(s) have no covering shift template"
      coverage -> "2026-01-05 12:00-13:00"
  The refusal names the IDENTICAL window the report listed — same computation, two presentations,
  as D-08 claims. This was not asked for by test 3 and is recorded because it was observed.
scope_note: |
  STATED PLAINLY so nobody over-reads this pass. This is NOT a byte-for-byte diff against a running
  Phase 14 — that code path no longer exists (V40 dropped break_offset_minutes and
  break_duration_minutes, and test 1 already established the source columns are gone). It is a
  demonstration that the Phase 15 one-band path produces Phase 14's single-offset SEMANTICS,
  argued from the singleton reduction and confirmed behaviourally at three offsets plus the
  zero-band and multi-band controls. That is the strongest evidence still available, and it is
  strictly stronger than route (a)'s "the original templates draw a clean verdict" — but it is not
  the literal claim in the test's first sentence, and it does not resurrect the lost baseline.
  No agents were created on the scratch desk, so the agent-hours-dependent advisories
  (hoursAdvisories, unsatisfiableWeekdays, capacityAdvisories, breakConcentrationAdvisories) were
  NOT exercised here. Deliberate: those are Phase 15 ADDITIONS, not Phase 14 verdicts, so they fall
  outside the invariant this test names. The SHIFT-switch refusal in bonus_finding did surface a
  contractedHours detail for exactly that reason (no agents => no weekday satisfiable) and it is an
  artefact of the minimal fixture, not a finding.
incidental_observation: |
  NOT a test 3 finding and deliberately NOT filed as a gap — recorded for the operator to route.
  After the scratch desk was deleted, `GET /desks/{deadId}/shift-templates` returns 200 `[]` and
  `GET /desks/{deadId}/shift-library/validation` returns 200 with an all-empty verdict, while
  `GET /desks/{deadId}` correctly returns 404. A mistyped or stale desk id therefore reads as
  "a desk with a clean, empty library" rather than as an error. Cosmetic today; it is the same
  degenerate-empty-verdict shape that made test 3's own baseline loss hard to notice.
observability_problem: |
  PRESERVED AS THE RECORD OF WHY ROUTE (b) WAS NEEDED. Re-measured live 2026-09-01 before choosing
  a route and STILL TRUE: Stubhub (EN) is still the tenant's only desk and now carries 11
  templates, EVERY ONE with 3 bands — zero single-band templates remain anywhere.

  RAISED 2026-08-31 at resume, BEFORE presenting the test. The test as written may no longer be
  answerable against live data, and it is better to say so than to record a meaningless verdict.

  Live state read this session (X-Tenant-ID: 1, dev on HEAD):
    - Stubhub (EN) is still the tenant's ONLY desk, so it is the only candidate for "a desk that
      existed before this phase".
    - It now carries 12 templates, and EVERY ONE has 3 bands. NO single-band template remains
      anywhere on the desk.

  The Phase 14 baseline recorded in 14-UAT.md was a FOUR-template, one-band-each library (Early
  08:00-17:00, Late 12:00-21:00 Mon-Fri; Weekend Early 10:00-19:00, Weekend Late 11:00-20:00
  Sat-Sun) validating hasLiveDemand=true, uncoveredWindows []. That configuration was deliberately
  dismantled during test 12 and the test 10 retest — 3 bands added to each original template, then
  8 further templates (Morning, Mid, Daytime, Weekend Morning/Opening/Closing/Flex, and the
  ZZ-UAT-SCRATCH probe). The "before" side of this comparison no longer exists to be re-measured.

  IMPORTANT: this is NOT evidence the invariant broke. It is loss of the observation, caused by
  UAT's own deliberate edits, and the loss was inevitable once test 12 required multi-band data on
  the only desk available.

  WHAT CAN STILL BE SAID (current validation, read live this session):
    uncoveredWindows []      misalignedTemplates []      unsatisfiableWeekdays []
    hoursAdvisories — fire ONLY on Weekend Flex (9.00h net, added this session). None of the four
      original Phase 14 templates appears in any advisory list.
  So the four Phase-14-era templates, which still exist under their original names, spans and
  weekdays, still draw a clean coverage verdict and a clean grid-alignment verdict. What cannot be
  isolated is whether that verdict is IDENTICAL to Phase 14's, because eight additional templates
  now contribute to the same desk-wide computation.

  ROUTES — RESOLVED: the operator chose (b) on 2026-09-01 and it was executed the same session.
  See resolved_via / evidence above. Routes (a) and (c) preserved for the reasoning only:
    (a) Accept the partial evidence above — original templates present, all verdicts clean, no
        advisory naming them — as sufficient, and pass with that caveat recorded.
    (b) Create a scratch desk with a single one-band template reproducing a Phase 14 shape and
        confirm its validation matches the single-offset predecessor. Tests the INVARIANT properly;
        does not test "no Phase 14 desk moved", because the Phase 14 desk has moved by our own hand.
    (c) Mark it unobservable and close it, recording that UAT consumed its own baseline.

### 4. Break-band editor saves and reads back multiple bands

expected: On the Shift Library page, a template can be given two or more break bands. After save and reload, all bands persist and display **ordered by offset ascending** — same order in the editor, the value range, and the template list.
result: pass
tested_against: dev API, live desk Stubhub (EN)
evidence: |
  Three bands submitted deliberately OUT of order (300, 120, 60) and read back from the LIST
  endpoint — not the create echo — as 60, 120, 300. Ordering is ascending by offset, produced by
  the repository's findByTenantIdAndShiftTemplateIdOrderByOffsetMinutesAsc rather than by the
  caller. A mixed capacity set (null, null, 3) round-tripped intact.

  Subsequently exercised at scale on the live library: all ten templates now carry three bands
  each, and the whole library reads back correctly ordered.
caveat: |
  The API layer is proven. The EDITOR's own rendering order was not separately eyeballed — this
  project has no frontend test framework (Phase 13 P-11), and the same repository ordering backs
  both, so they cannot disagree without a deliberate re-sort in the page.

### 5. Band capacity: 0 rejected, blank means unlimited

expected: Capacity `0` is refused with a clear message (a band nobody can use is a data error). BLANK capacity is accepted and means unlimited. Blank and 0 are not the same value.
result: pass
tested_against: dev API, live desk Stubhub (EN), 2026-08-27T21:31Z
method: |
  Exercised directly against POST /api/v1/desks/{id}/shift-templates. Safe to do so because
  `ShiftTemplateService` is NOT among the files changed after the deployed commit `adaad6d`
  (`git diff adaad6d..HEAD` touches SolverService, ScheduleOutputService, ScheduleExportService
  and ScheduleConstraintProvider only) — so these validation rules are byte-identical in the
  deployed build and the build now shipping. A REJECTED request persists nothing, so the
  refusal half of this test had zero data footprint (re-listed after: still 4 templates).
evidence: |
  REFUSED — capacity 0:
    HTTP 400 — "Break band capacity must be at least 1 (band at offset 240 minutes)"
    The message names the offending band, so an operator with several bands knows which one.

  ACCEPTED — blank (null) capacity, on two separate bands of the scratch template, read back
  from the LIST endpoint as capacity=null and rendered as unlimited. The four pre-existing
  live templates likewise all carry capacity=null (V40 sets it NULL by design, D-03).

  Blank and 0 are therefore demonstrably NOT the same value: one is refused at save, the other
  persists and means unlimited.
source: automated-probe + operator-authorised live write

### 5b. (probe artefact) Scratch template left on the desk

expected: n/a — bookkeeping entry, not a test.
result: skipped
reason: |
  Tests 4/6/7 required one successful multi-band write, and it could not be undone afterwards.
    name: ZZ-UAT-SCRATCH-do-not-use
    id:   4f1b2078-9a62-4dcd-8959-790ae3bb2ac5

  PROCESS FAILURE WORTH RECORDING — this should not have been created at all. The controller's
  OWN javadoc, read before the write, states "there is no delete endpoint ... no destructive
  action exists in this phase (D-10, T-14-14)". Writing a disposable row into a live desk whose
  disposal route is documented as absent was the error; the correct move was to confine the
  acceptance probes to a throwaway DESK (which DeskController CAN delete) or to skip them and let
  the operator exercise tests 4/6b through the UI.

  ALSO OVERSTATED: the artefact was described to the operator as unable to "perturb test 10",
  asserted as settled fact. The supporting evidence is real — SolverService.filterLiveShiftTemplates
  drops any template whose effectiveTo precedes the period start, and the desk had zero schedules —
  but it was stated with more confidence than a live system warrants, and the operator then spent
  effort trying to remove a row they had been told was harmless.

  RESOLUTION: the operator retired it themselves via the UI (effectiveTo 2025-12-02, API confirms
  eraStatus=PAST). Deletion was attempted and failed at the time: psql to the RDS endpoint times
  out — the instance is flagged PubliclyAccessible=true but its security group does not admit an
  arbitrary client IP. Schema-wise the delete was always unobstructed (the ONLY FK to
  shift_template is V40's break-band one, ON DELETE CASCADE; agent_shift_assignment
  .source_template_id carries no FK by D-07 denormalisation).

  CLOSED PROPERLY (81117e3): the operator asked for a delete control, and the absence of one was
  the real defect this artefact exposed. D-10 made retirement the entire lifecycle — right for a
  template that WAS used, since the row must survive so an existing roster stays explicable, and
  wrong for one that never should have existed, which retiring strands in the library forever.
  DELETE /desks/{deskId}/shift-templates/{id} now exists, refusing with 409 if any
  agent_shift_assignment references the template and directing the caller to retire instead. That
  is deliberately stricter than the database requires: a foreign key was never the point, but an
  operator who deletes a template that shaped a real roster loses the ability to explain it.
  A Delete button sits beside Edit and Retire on the Shift Library page; operator confirmed it
  visible on 2026-08-31. This scratch row is the natural first use — it has never been in a
  schedule, so it takes the happy path.

### 6. Duplicate bands rejected, touching bands allowed

expected: Two bands with the SAME offset AND SAME duration are refused as duplicates. Two bands whose break windows merely touch (A ends exactly as B begins) are accepted as distinct and legal.
result: pass
tested_against: dev API, live desk Stubhub (EN), 2026-08-27T21:31Z
evidence: |
  REFUSED — duplicate pair, bands [offset 240 / dur 60] submitted twice:
    HTTP 400 — "Duplicate break band at offset 240 minutes with duration 60 minutes"

  ACCEPTED — touching pair, on the scratch template (envelope 09:00-18:00):
    band offset  60m / dur 60m -> 10:00-11:00
    band offset 120m / dur 60m -> 11:00-12:00
  A ends exactly as B begins. Both persisted and both read back from the LIST endpoint as
  distinct bands. This is the discriminating case: a touching pair shares its BOUNDARY but
  never both (offset, duration) values, which is precisely why the duplicate key
  `offset + ":" + duration` admits it — the implementation comment states this intent and the
  live behaviour matches it.
source: automated-probe + operator-authorised live write

### 7. Off-grid band offsets refused, never silently rounded

expected: An offset or duration not landing on the desk's timeslot grid produces a hard 400 with a named, readable message. The value is never silently rounded to fit.
result: pass
tested_against: dev API, live desk Stubhub (EN), 2026-08-27T21:31Z
grid: desk live bounds 08:00-21:00, incrementMinutes=60, period 2026-01-05..2026-01-11
evidence: |
  THREE independent off-grid shapes, all REFUSED with HTTP 400 and per-field detail. Every
  response carries the offending VALUE, so the operator can see what was wrong rather than
  guessing — and in no case was a value accepted-then-rounded:

  (a) off-grid band OFFSET (250m from 09:00 -> break 13:10):
      "Shift template times must align to the desk's timeslot grid"
        bands[0].breakStartTime  value 13:10
        bands[0].breakEndTime    value 14:10
      Note it reports BOTH boundaries — an off-grid offset drags the end off-grid too.

  (b) off-grid band DURATION (30m on a 60m grid -> break ends 13:30):
      bands[0].breakEndTime      value 13:30
      Only the END is flagged; the start at 13:00 is legal. The check is per-boundary, not
      per-band, which is the more useful diagnosis.

  (c) off-grid TEMPLATE start (09:20):
      startTime                  value 09:20
      bands[0].breakStartTime    value 13:20
      bands[0].breakEndTime      value 14:20

  NEVER SILENTLY ROUNDED — proven positively, not just by the 400: the desk was re-listed after
  all probes and still held exactly its 4 original templates. A rejected request writes nothing,
  so there is no rounded row to find.
source: automated-probe (zero data footprint — all three were refusals)

### 8. Capacity shortfall shows an advisory at save time

expected: When band capacities total below the shift's admissible headcount, the operator sees a named advisory in the Capacity column at save time — not a bare hard score at solve time.
result: pass
result_note: The stated behaviour works. Testing it exposed a blind spot big enough that two further advisories were built this round — see below.
tested_against: dev (https://d2bbtcc80peap7.cloudfront.net), live library
blind_spot_found: |
  findCapacityAdvisories fires only when capacity is too LOW, and skips any template carrying a
  blank-capacity band outright ("unlimited by construction" — it genuinely cannot be short).
  But UNLIMITED IS THE DEFAULT: V40 migrates every Phase 14 break forward with a NULL capacity.

  So the single most damaging configuration — one band, blank capacity, every agent breaking in
  the same hour — passed every check in silence. The live desk had exactly that on all four
  templates. The page reported nothing; the solve then put 18 of 18 Late agents on a 16:00 break,
  emptied the hour, and forced agents through their own break to hold it. 13 hard violations that
  no advisory had predicted.
closed_by: |
  TWO new advisories, both deployed and both verified against the live library:

  1. BreakConcentrationAdvisory (00d675d) — the inverse of the shortfall check. Fires when a
     template's bands permit MORE THAN HALF its admissible headcount to break in the same hour,
     reporting what the library PERMITS rather than what one solve produced. On the original
     library it would have said, before any solve: "Up to 18 of 18 could break in the SAME hour".
     Thresholds: strictly-more-than-half (so an even split does not nag), minimum 4 agents (three
     people breaking together is a normal shift), zero bands skipped (that is "no break", nothing
     to concentrate). Its message also names the trap — bandCapacity is ofHard(1), so an operator
     who over-corrects by setting capacities too tight makes the desk UNSOLVABLE rather than
     merely worse.

  2. PeakShortfallAdvisory (1f66211) — the blind spot every PER-DATE aggregate shares. Every other
     supply check aggregates over a day; a daily total says nothing about its distribution. Live
     proof: the desk read 139.9% coverage with the seat-supply gate clean while Saturday 11:00
     needed 44 FTE against 25 agents on the whole desk. reachableAgents is a deliberate UPPER
     bound, so a reported shortfall is PROVABLE — no library edit or longer solve closes it, which
     turns a tuning problem into a staffing conversation.
live_output: |
  Against the current library, on 2026-08-31:
    2026-01-10 11:00  needs 44, reachable 25, short 19
    2026-01-11 11:00  needs 32, reachable 18, short 14
  and zero findings from every other check, including concentration.

### 9. Suggested Library returns a draft and writes nothing

expected: Requesting a suggested library returns an editable draft derived from the desk's demand and its agents' contracted hours. **Nothing is persisted until a row is explicitly saved.** Navigating away leaves the library unchanged. Requesting twice for an unchanged desk returns the same suggestion (it is deterministic).
result: pass
result_note: |
  The stated behaviour was already correct — read-only, deterministic, envelopes derived from real
  demand. But the operator question "how will a user know what shifts to set?" turned this from a
  pass into three substantive gaps, because the feature that answers that question was handing
  operators a draft that could not solve well.
tested_against: dev (https://d2bbtcc80peap7.cloudfront.net), live desk, before and after each fix
as_found: |
  3 templates, each ONE band with capacity None, all 7 days:
    11:00-20:00 / 08:00-17:00 / 12:00-21:00,  uncoveredWindows 0
  Envelopes genuinely right — three of the five stagger positions later arrived at by hand,
  derived from data rather than solver forensics. Everything else was wrong in a way that only
  showed up at solve time.
gaps_found_and_closed: |

  1. ONE BAND, BLANK CAPACITY (7298f96). The generator emitted precisely the configuration proven
     harmful this same session. Blank capacity also made its own output invisible to the validator
     meant to guard it, since the capacity check skips blank-capacity templates. Now three
     grid-aligned bands with capacity floor(headcount/2), sized between two constraints pulling
     opposite ways: total must EXCEED headcount (bandCapacity is ofHard(1), so under-sizing makes
     the desk unsolvable, not merely worse) while no single band may admit more than half (the
     concentration threshold). 3*floor(h/2) >= h and 2*floor(h/2) <= h both hold for h >= 2, so a
     draft accepted unchanged now comes back clean from the validator. A round-trip test ties the
     two services together; previously nothing did, which is how the generator came to emit the
     shape its own validator would have warned about.

  2. COVERAGE-MINIMAL, NOT SUPPLY-AWARE (461483d). greedyCover answers "smallest library that
     covers demand" — the wrong question for an over-supplied desk, and the gap was measured, not
     theorised: going from 3 distinct spans to 5 took the residual from -18 to -6, and NEITHER
     added span was needed for coverage. Both were needed for absorption. Now expands to
     ceil(coverSpans * supply / demand) spans; on this desk (789 vs 1104, 3 cover spans) that is
     ceil(3 * 1.399) = 5 — exactly the five positions found by hand, from data. Strictly additive:
     a desk at or below 100% supply keeps its minimal cover untouched.

  3. ONE TEMPLATE SET FOR EVERY WEEKDAY (a4a3115). A single set must straddle shapes that want
     different envelopes: it proposed weekend envelopes starting 08:00 and 09:00 where weekend
     demand is ZERO, and a 12:00-21:00 envelope that misses the 11:00 weekend peak entirely while
     covering two dead hours. Now clusters weekdays by the SHAPE of their demand curve — cosine
     similarity on the hourly FTE vector, scale-invariant so a quiet Sunday clusters with a busy
     Saturday if the contour matches — and emits one set per cluster. Nothing encodes Mon-Fri vs
     weekend; if a desk's Wednesday looks like its Saturday they get one set. Threshold 0.90 is not
     fitted: on this desk weekdays resemble each other at 0.949-0.991 and weekends at 0.986 while
     every cross-pair is 0.680-0.788, leaving 0.16 of clear air.
after: |
  6 templates in 2 demand-shape clusters, 3 capped bands each:
    [Mo Tu We Th Fr]  08:00-17:00, 08:00-17:00, 09:00-18:00, 12:00-21:00
    [Sa Su]           10:00-19:00, 11:00-20:00
  uncoveredWindows 0. The weekend set now matches the actual weekend curve (10:00-19:00 bearing
  hours, 11:00 peak) instead of starting on dead hours.
still_open: |

  - DUPLICATE TEMPLATE: the weekday cluster above contains 08:00-17:00 TWICE with identical bands.
    greedyCover legitimately selects two same-span candidates with different band offsets so each
    covers the other's break hour (D-02 self-cover); expanding both to the same three bands
    collapses them into a pointless duplicate. Needs a dedupe after expansion. Not fixed.

  - BAND PLACEMENT IGNORES THE DEMAND PEAK: it proposed breaking at 11:00 on the 10:00-19:00
    weekend template — the busiest hour of the weekend. Placement is driven by coverage, not by
    avoiding demand. The hand-set bands (13:00-15:00) were deliberately kept instead. Not fixed.

### 10. Shift-mode solve succeeds at production scale

expected: A real desk in shift-scheduled mode solves to a feasible schedule in acceptable time. The automated benchmark ran only 4 agents x 2 days — this is the first exercise at your real agent count, day count and demand curve. AFTER G-15-10 CLOSURE, the acceptable outcomes are exactly two: (a) the solve reaches 0 hard in acceptable time, or (b) the solve is REFUSED BEFORE it starts, naming the date, the seat shortfall and the levers you control. A completed solve still carrying residual `Shift envelope compliance` penalty is a failure. Per operator ruling OR-1, an hour the shift library does not reach is now deliberately unstaffed and should render as such — that is correct behaviour, not a bug.
result: pass
closed_by_operator_ruling_2026_09_02: |
  OPERATOR RULING ("mark test 10 as passed"), taken after the evidence below was put to them. This
  is a JUDGEMENT about the desk, not a new measurement — recorded as a ruling so nobody later reads
  it as though the -9 simply stopped being true.

  WHAT THE TEST ASKED FOR. Its own expected text names exactly two acceptable outcomes: "(a) the
  solve reaches 0 hard in acceptable time, or (b) the solve is REFUSED BEFORE it starts, naming the
  date, the seat shortfall and the levers you control."

  OUTCOME (a) IS DEMONSTRABLY REACHED. Three accepted schedules on the live desk, all solved after
  the three root causes shipped, all over the full 2026-01-05..11 period at production scale:
      523c8785   hard 0 / soft -76   feasible=true   2026-09-01T13:57
      b88cc98f   hard 0 / soft -60   feasible=true   2026-09-01T18:46
      6a10afa1   hard 0 / soft -68   feasible=true   2026-09-01T22:06
  Re-read live from the API on 2026-09-02 against the shipped build; all three report
  violatedHardConstraints [] and 138/138 agent-days carrying a shift. The frozen-solve symptom that
  opened this test is gone, and a feasible production-scale schedule exists.

  WHAT THE PASS DOES NOT CLAIM. It does not claim every run reaches 0 hard. The newest accepted
  schedule 7cc71bf5 reads hard -30, which is THREE violations at the rescaled ofHard(10) envelope
  weight (G-15-30), not thirty — and it was solved at overallocationHardLimitPct 500, the G-15-28
  workaround. The residual is attributed, with a controlled experiment behind it, to the weekend
  demand forecast rather than to the solver: raising the ceiling 250 -> 500 with everything else
  held constant took the desk to hard 0. That work is G-15-28, it is OPEN, and the operator owns it.
  Passing test 10 does not close G-15-28.

  ALL THREE ROOT CAUSES FIXED AND DEPLOYED, which is what makes (a) reachable at all:
    A  validWeekdays never enforced in the solver path        b2dd702
    B  phantom seats from calendar-blind coverage             6c82241  (third site closed by G-15-21)
    C  zero-slack eligibility                                 81117e3 (V44)
previous_result: issue
previously_reported_at_pass_time: "Hard: -9, Soft: -67 — NOT FEASIBLE. Nine agent-day seats outside their assigned shift envelope, all on 2026-01-11, all agents seated during their own break window."
severity_at_time_of_issue: major
severity_note: |
  Downgraded from the original BLOCKER. The blocking symptom — a solve frozen on an irreducible
  score with agents dragged onto zero-demand hours — is gone. What remains is a small search
  plateau on one date, with a schedule that is materially usable: 9 mis-seated agent-hours out of
  1104 staffed, under 1%.
previous_result: issue
previously_reported: "It seems to be stuck on shift envelope compliance. A big issue is covering 0 hour slots - It pulls to fill the 0 slot but then adds breaks to fill in the gaps!"
previous_severity: blocker
tested_against: dev (https://d2bbtcc80peap7.cloudfront.net), live cloud data, commits 5b7bd15 -> 9cd4703
progression: |
  Measured across the session, every step attributable to a NAMED mechanism rather than tuning.
  Each number is a live solve on desk Stubhub (EN), period 2026-01-05..2026-01-11:

    -19  frozen 5.5 min      the original defect, pre-fix build
    -31  after validWeekdays enforcement + the gap-closure round deployed
    -18  after 3 break bands per template (13 break-window seats removed)
    -11  after +3 stagger templates (09:00, 10:00 weekday; 09:00 weekend)
     -6  after +3 more stagger templates (full 5-position stagger, both day types)
    -12  after bounded slack + Weekend Flex  <-- LOOKS like a regression, is not: see below
     -9  after date-aware seat coverage (9 phantom-seat strays removed)
    -66  after acceptor 0hard -> 1hard       <-- a REAL regression, reverted
     -9  after reverting the acceptor

  THE -6 -> -12 STEP IS NOT A REGRESSION and reading it as one would send the next person the
  wrong way. Enforcing validWeekdays stopped agents holding the weekday template that had been
  making a weekend hour look reachable. The phantom seat on that hour still existed, so filling it
  now cost hard score where before it had been free. The defect did not appear; it became
  chargeable. Fixing the seat coverage then removed it properly.
root_causes_found: |
  THREE defects the original G-15-10 diagnosis did not anticipate, all found by operator questions
  during UAT rather than by review or by the test suite:

  A. validWeekdays was never enforced in the solver path.
     AgentShiftAssignment.getEligibleShiftBandPairs filtered on net hours and the effective-date
     era, never on the template's valid weekdays. The field was read only by
     ShiftLibraryValidationService (a page advisory) and ShiftLibraryGenerationService
     (suggestions) — so it constrained what the library ADVISED and never what the solver could DO.
     Live effect: a MON-FRI 'Late' seated on a Sunday (x5), a MON-FRI 'Early' on a Sunday, and a
     SAT/SUN 'Weekend Late' on a Monday and a Wednesday. ALL EIGHT residual violations of a frozen
     -8 solve were weekday-invalid assignments.
     Why it hid: the symptom is indirect. Give a Sunday agent the Late envelope (12:00-21:00) when
     Sunday demand opens at 11:00 and they must take one seat outside and surrender one inside —
     presenting as the 1:1 seat-supply fingerprint of an ALREADY-DIAGNOSED cause. Fixed in b2dd702.

  B. Phantom seats from calendar-blind coverage — the same class, in the seat-supply path.
     ShiftBandPair.covers(Timeslot) compares only envelope/break TIMES. Applied to the DESK-WIDE
     pair list in expandMinimumStaffingSeats, a weekday-only 'Early' (08:00-17:00, Mon-Fri) reported
     Saturday 08:00 as covered purely on the clock, so a filler seat appeared on an hour no WEEKEND
     template reaches. Any agent seated there breached by construction.
     9 of 12 residual violations at that point were weekend 08:00/09:00/20:00 — every one a
     zero-demand hour reachable only by a weekday template. Fixed in 6c82241.
     STILL OPEN, same class, third site: requireShiftEnvelopeSeatSupply computes coveredTimeslots
     twice (SolverService ~1184 and ~1281) with the same calendar-blind predicate. The advisory
     one is cosmetic; the ~1184 one feeds librarySupplySlots, so on a weekend it counts hours only
     a weekday template reaches and OVER-COUNTS library supply — the exact failure its own javadoc
     warns of ("wave through an unsolvable one if it over-counts"). Visible symptom: the live
     advisory now reads "tightest at 08:00-09:00 with 0 seat(s)", which is incoherent — a covered
     hour cannot have zero seats. Not fixed.

  C. Zero-slack eligibility (D1, deliberately kept, now relaxed).
     Eligibility demanded net hours EXACTLY equal contracted hours, so legal in-envelope slots
     equalled contracted slots and an agent had to occupy 100% of them. Sunday 10:00 carries demand
     of 1, so the 250% over-allocation ceiling admits 2 agents, yet every agent on a 10:00-starting
     envelope was obliged to work it. No library shape avoids this — a 9-hour contiguous envelope
     starting 08:00, 09:00 or 10:00 necessarily contains 10:00. Fixed in 81117e3 (V44, default 1
     slot), proven live by Weekend Flex (9.0h net) which only became holdable under the new rule.
remaining_9: |
  All nine on 2026-01-11, all agents seated during their OWN break window (13:00 x6, 15:00 x3).
  Every structural cause is ruled out by the data:

    - seats are NOT scarce      Sunday 13:00 demand 8 (~20 seats at 250%), 15:00 demand 9
    - routing freedom EXISTS    those agents hold 9h-net envelopes: 9 legal slots for 8 hours
    - not a hard/soft trade     hard dominates soft in HardSoftScore comparison
    - not unreachable hours     all nine are INSIDE the envelope, in the break window
    - not library shape         zero uncovered windows, every advisory clean
  Nothing forces those seats. The solver cannot REACH the arrangement that clears them: freeing a
  break-window seat needs a transiently worse intermediate state (vacate, let another agent take
  it, reseat legally), and the acceptor forbids that. See failed_experiment.
failed_experiment: |
  solverConfig.xml acceptor 0hard/3000soft -> 1hard/3000soft, to permit that transient worsening.
  PREDICTED to clear the plateau. MEASURED: -9 -> -66 hard while soft went -67 -> -43, still not
  reconverged at 17m25s. Seven times worse. Reverted in 9cd4703.

  The reasoning error was SCALE, not mechanism. shiftEnvelopeComplianceWeight is ofHard(1), so a
  1hard temperature is not a small tolerance — it is exactly one whole violation, and there are a
  great many single-violation moves available, so the annealer random-walks on envelope compliance.
  HardSoftScore is integral: nothing exists between 0 and 1. At this weight scale 0hard is the only
  workable setting, and the mechanism argument cannot be expressed through this lever at all.

  A genuine fix needs a different acceptor (late acceptance, tabu — these escape plateaus by
  remembering recent scores rather than tolerating worse ones, so no temperature scale problem) or
  a weight scale on which one hard unit is small relative to the total. The latter means raising
  shiftEnvelopeComplianceWeight, which THIS PHASE'S OWN DIAGNOSIS warned against twice: it changes
  WHERE infeasibility surfaces, not whether it exists.
suite_gap_this_exposed: |
  THE MOST IMPORTANT FINDING OF THE RETEST, and it is about the tests, not the solver.
  All 580 tests passed with the acceptor setting that regressed the live desk sevenfold. No fixture
  is sensitive to acceptor behaviour, so solver-tuning changes have NO automated guard whatsoever —
  which is precisely how a regression shipped with a green build and full confidence.
  Before any future acceptor or weight change: add a benchmark-shaped test that solves a realistic
  fixture and asserts a hard-score ceiling, so a tuning change that wrecks convergence fails
  locally instead of on a live desk.
operator_context: |
  The desk is 40% OVER-SUPPLIED and this is fixed data, not a correctable input: 789 demand-hours
  against 1104 staffed, every day between 135% and 143%. Simultaneously it is UNDER-supplied at the
  peak — Saturday 11:00 needs 44 FTE against 25 agents on the desk, Sunday 11:00 needs 32 against

  18. Both facts were invisible to every aggregate check (coverage read 139.9%, seat-supply gate
  clean) until the peak-hour advisory was added this round.
  Unmet peak demand is SOFT (confirmed live: "Bulk under-allocation soft"), so it never blocked
  0 hard. The over-supply is what made hard feasibility hard: 315 surplus agent-hours must be
  seated somewhere legal, and the cheapest hard constraint to break is the phase's own headline
  guarantee at ofHard(1).
verdict: |
  NOT the original blocker. The frozen-solve symptom is gone, all three named root causes are
  fixed and deployed, and the desk produces a usable schedule. What remains is a search plateau
  worth 9 agent-hours in 1104, plus one unfixed instance of the calendar-blindness class (B, third
  site) which is not implicated in the residual but is a live over-counting risk in the gate.
ab_experiment_2026_09_01: |
  A CONTROLLED A/B RESOLVED THE RESIDUAL ALMOST ENTIRELY, and it was an OPERATOR lever — a shift
  library edit — not a solver change. Both arms: period 2026-01-05..11, 08:00-21:00, 60min,
  overallocation 250 / underallocation 50, solveTimeSeconds 300. Only the bands differed.

    Run A  f1f82a8f  ORIGINAL bands (offsets 180/240/300 on every template)
                     hard  -10   soft -62   envelope violations 10   converged ~4 min
    Run B  3a72f0c4  + FIVE EDGE BANDS
                     hard   -1   soft -58   envelope violations  1   converged ~1 min

  Run A reproduces the -9 baseline within search variance, so it is a sound control.
  Run B ACCEPTED as aa17cc3c (PUT .../accept?version=0).

  THE EDGE BANDS ADDED (chosen against the live demand curve, deliberately avoiding the 11:00
  peak of 44 FTE Saturday / 32 Sunday):
    Weekend Early 10:00-19:00  + offset 0 (break 10:00) and 480 (18:00)
    Weekend Flex  10:00-20:00  + offset 0 (break 10:00) and 540 (19:00)
    Weekend Late  11:00-20:00  + offset 480 (break 19:00)   [no offset 0 — that is the peak]

  WHY IT WORKS. Every template previously carried bands ONLY at offsets 180/240/300 — a three-hour
  window in the middle of the shift. Sunday 10:00 carries demand 1, so its over-allocation ceiling
  is tiny, yet Weekend Early (net 8.0 against 8 contracted hours) has no spare slot and every agent
  holding it was OBLIGED to work 10:00. An offset-0 band puts the break exactly on that bottleneck,
  converting a violation the solver had to pay into a single strictly-improving ChangeMove it finds
  at once. The 4x faster convergence is the signature of that: the fix is reachable in one move
  instead of an unreachable multi-move sequence.

  BEARING ON THE 'SEARCH PLATEAU' VERDICT ABOVE: the residual was NOT purely a search-quality
  problem. It was substantially a LIBRARY SHAPE problem — break positions clustered where they
  could not relieve the binding hours — and the plateau reading pointed at acceptors and weight
  scales, which is where the failed experiment went. remaining_9's claim that library shape was
  ruled out ("zero uncovered windows, every advisory clean") is now falsified: coverage and
  advisory cleanliness do not detect a library whose break OFFSETS are all in the wrong place.

  CAVEAT — the offset-0 and trailing-edge bands mean an agent's break sits at the very start or
  end of their envelope, i.e. effectively a late start or early finish, producing an unbroken
  working day. That is exactly the shape D5 warns is ungoverned in SHIFT mode
  (breakBlockedHours has no enforcement point). The score is better; whether these shifts are
  OPERATIONALLY acceptable is an operator judgement that has NOT been made. Do not treat the
  -1 as ratified until someone rules on that.
  residual_1: "One envelope violation remains, plus 39 unworked legal slots (normal under bounded
    slack — see test 19's expectation_correction)."
weekend_edge_coverage_2026_09_01: |
  OPERATOR RULING, and it QUALIFIES OR-1. Asked about weekend 08:00-09:00 sitting empty, the
  operator ruled: "they need to be staffed by at least ONE agent." OR-1 said an hour the library
  does not reach is deliberately unstaffed and renders as such. That remains true as a RENDERING
  rule, but it is NOT a licence for the library to stop reaching an hour the desk actually opens.
  Unstaffed-by-design must be a decision, not a side effect of a retirement nobody re-examined.

  WHAT WAS ACTUALLY EMPTY (accepted schedule aa17cc3c, both weekend days):
    08:00 -> 0 agents, 09:00 -> 0 agents, AND 20:00 -> 0 agents (the operator asked about 08-10;
    20:00 was found alongside it, same cause, and was fixed in the same change).

  CAUSE: three weekend templates carried effectiveFrom 2026-01-01 AND effectiveTo 2026-01-01 —
  live for exactly one day, retired for the whole schedule period:
      Weekend Opening 08:00-17:00   was the ONLY weekend template reaching 08:00 and 09:00
      Weekend Morning 09:00-18:00   09:00
      Weekend Closing 12:00-21:00   was the ONLY weekend template reaching 20:00
  With none live, no band reached those hours; date-aware seat expansion (6c82241) therefore
  created no seat; minimumStaffing groups by timeslot over AgentAssignment rows, so with ZERO rows
  it emits no tuple and never fires. An hour with no seat is silently exempt from the very
  constraint meant to guarantee it — on this desk minStaffing is ofHard(10), so the guarantee looks
  strong and is simply absent where it is most needed.

  FIX APPLIED: un-retired Weekend Opening and Weekend Closing (effectiveTo -> null). Break offsets
  set deliberately, NOT copied:
      Weekend Opening 08:00-17:00  offsets 300/360/420 = breaks 13:00/14:00/15:00
        — offset 0/60 REJECTED: a break at 08:00 or 09:00 would vacate the exact hours being fixed.
        — the default 180 REJECTED: that is 11:00, the demand peak (44 FTE Sat, 32 Sun).
      Weekend Closing 12:00-21:00  offsets 180/240/300 = breaks 15:00/16:00/17:00, clear of 20:00.
  Weekend Morning left retired — Opening covers 08:00 and 09:00 together, and one Opening holder
  (net 8.0, zero slack) must work every legal slot, so it necessarily staffs both.

  RESULT — Run D 1c93a7a5, ACCEPTED as 709fd8b4. Same params as the A/B (250/50, 300s):
      hard -1   soft -70   feasible false
      Sat 2026-01-10   08:00 = 1   09:00 = 1   20:00 = 2
      Sun 2026-01-11   08:00 = 3   09:00 = 3   20:00 = 2
  Requirement met on every hour. Hard score unchanged from Run B; the cost is SOFT, -58 -> -70,
  which is the honest price of seating agents on hours carrying no forecast demand.

  RESIDUAL -1: Tekla Davitashvili, 2026-01-11 20:00-21:00. Structurally the SAME pattern as the
  Tuesday 08:00 case — the boundary hour is reachable by exactly one template (Weekend Closing),
  too few agents hold it, so one agent covers it from outside their envelope. The general lesson:
  wherever a single template is the sole route to a boundary hour, its headcount becomes a hard
  constraint nothing in the UI surfaces.

  0 HARD IS REACHABLE ON THIS DESK — schedule bff23a47 (hard 0, soft -66, feasible TRUE) was
  observed this session, on the pre-fix library and therefore WITHOUT the weekend edge coverage.
  It is not a valid answer to the operator's requirement, but it settles a question the phase has
  carried since G-15-10 was filed: feasibility is attainable, so the -1 residuals are search
  misses, not a structural floor.

  PROVENANCE CONFIRMED by the operator: bff23a47 was started by them from the UI. Its library
  state was edge-bands-applied but pre-un-retire (it holds no Opening/Closing agent-day and leaves
  08:00/09:00/20:00 empty), so it is the SAME configuration as Run B.

  THAT MAKES IT A VARIANCE MEASUREMENT, and a sharp one:
      Run B     3a72f0c4   edge bands, no weekend edge coverage   hard -1
      operator  bff23a47   SAME configuration                     hard  0   FEASIBLE
  Identical library, identical 250/50 params, different outcome. So this desk sits right at the
  boundary of feasibility and the search reaches it only sometimes. Two consequences:
    1. A single solve is NOT evidence of an irreducible floor. Test 10's whole history reads
       single-run hard scores as structural facts; at this variance that inference is unsafe, and
       several of its recorded steps (-19, -12, -11, -6) deserve the same scepticism.
    2. Run D's -1 (WITH the weekend coverage) looked like it might also be a search miss. IT IS
       NOT — that prediction was TESTED AND FALSIFIED. See sunday_2000_is_structural below.

  This also explains, benignly, several "A schedule already exists for this desk" refusals earlier
  in the session: the operator and this session were driving the same desk concurrently. Those
  were correct guard behaviour, NOT the defect the retracted G-15-26 claimed.

session_2026_09_01: |
  THREE runs on dev at commit a320ca7 (V45 contiguity constraint + V46 default of 10, both live —
  deploy verified against the ECS image tag, not a recorded claim). Library unchanged from §2 of
  HANDOFF.md. This is the first exercise of Test 10 with the contiguity constraint in place.

  RUN 1 — 8d67825c, accepted as b88cc98f. Started by the previous session's parked chained task,
  not by hand. 0 HARD / -60 soft, FEASIBLE, terminated 7m33s into a 900s budget on unimproved
  steps.
      0 split shifts / 138 agent-days     0 edge breaks     violatedHardConstraints []
      edge-hour coverage, all 7 days non-zero on 08:00, 09:00, 10:00 and 20:00

  THIS IS THE FIRST RUN EVER TO SATISFY TEST 10'S CRITERION (a) WITH THE OPERATOR'S REQUIREMENTS
  INTACT. The earlier 0-hard run (bff23a47) reached 0 only on the pre-un-retire library, which
  left 08:00/09:00/20:00 empty and was explicitly "not a valid answer to the operator's
  requirement". This one holds both at once.

  RUN 2 — 60523b98, operator-run from the UI. CONFIG BYTE-IDENTICAL TO RUN 1 (verified field by
  field via the API: same period, 08:00-21:00, 60min, breaks 60/ON_HOUR/4.0h/20%, contracted 8.0,
  500/50, SHIFT). Result:
      -20 HARD / -61 soft, NOT FEASIBLE — 2 Shift envelope compliance violations
      0 split shifts     0 edge breaks     all four edge hours still non-zero on all 7 days

  So the SAME configuration gave 0 hard and -20 hard within twenty minutes of each other. This is
  the variance already recorded at the head of this file (Run B -1 vs bff23a47 0), reproduced at
  the current commit. Criterion (a) — "reaches 0 hard" — is satisfied by one run and failed by the
  other, with no difference between them that anyone can name.

  RUN 3 — 2eeb2ca9, operator-run at overallocationHardLimitPct 250 (the form default, not a
  deliberate choice). -120 hard, NOT FEASIBLE, three constraints violated (contracted hours under,
  envelope compliance, contiguity). Rejected, not accepted. Its value is diagnostic only, and it is
  the evidence behind G-15-31 below — the run was NOT refused by the seat-supply gate despite
  failing on exactly what that gate exists to pre-empt.

  WHERE THE VIOLATIONS LANDED — recorded because this file's own methodological note says a
  recurring LOCATION is far stronger evidence than a recurring score:
      Run 2 violation 1   Juan Diego Dieguez     2026-01-05 (Mon) 08:00-09:00
      Run 2 violation 2   Melina Noemi Aparicio  2026-01-06 (Tue) 08:00-09:00
  Both weekday 08:00. Dieguez holds `Morning` (09:00-18:00) and is seated at 08:00 from outside it
  (`divergence.outOfEnvelopeSeats: ["08:00"]`, surrendering 17:00 inside it). `Early` (08:00-17:00,
  MON-FRI) is the ONLY weekday template reaching 08:00 — confirmed against the live template list.

  That is the SAME SHAPE as sunday_2000_is_structural, now on weekdays: a boundary hour reachable
  by exactly one template, too few agents holding it, so the hour is covered from outside an
  envelope at a cost of 1 hard each. The general lesson recorded there — "wherever a single
  template is the sole route to a boundary hour, its headcount becomes a hard constraint nothing in
  the UI surfaces" — now has a second, independent instance.

  BEARING ON sunday_2000_is_structural: Sunday 20:00 did NOT violate in either of today's runs.
  Run 1 staffed it with 2 agents and Run 2 with 1, both legally, both at 0 violations there. This
  does NOT falsify that finding — it was measured before `Weekend Closing` (12:00-21:00, the sole
  route to Sunday 20:00) was un-retired, and un-retiring it is essentially option (b) from the
  options list there. The structural analysis was correct for the library it described, and the
  library change resolved it. Recorded so nobody re-opens it as a contradiction.

  VERDICT — NOT SET HERE, operator's call. The evidence supports two readings and they lead
  different places:
    (a) PASS. Criterion (a) has now been met with requirements intact, and the desk is live on that
        schedule. The -20 run is search noise of the size this file already documents (2,3,3,4,6,8).
    (b) STILL ISSUE. The stated criterion is "reaches 0 hard", and 1 of 2 identical runs fails it.
  The honest observation is that CRITERION (a) IS ITSELF THE PROBLEM. On a search with this
  variance, "reaches 0 hard" is a coin-flip property of a run, not a property of the build — so it
  cannot decide a UAT test either way. What WAS stable across all three of today's runs, including
  the -120 one, is the operator-requirement set: 0 splits, 0 edge breaks, every edge hour non-zero.
  Re-stating Test 10 against those invariants plus a violation-count ceiling would make it decidable
  and is the same measurement G-15-22's automated guard needs. Recommended, not applied.

### 11. No agent is seated outside their assigned shift envelope

expected: Every agent works only within the envelope of the single shift assigned to them that day; each working agent-day has exactly one shift. This is the phase's core hard-constraint guarantee.
result: pass
verdict: |
  PASSED on substance by operator ruling, 2026-09-01 ("pass it"). Both limbs hold on the schedule
  the desk is live on, verified two independent ways. The reporting defect found while measuring
  it is filed SEPARATELY as G-15-32 and does NOT attach to this test — deliberately, because it is
  a read-path defect that would equally misreport a clean schedule or a broken one, and pinning it
  to test 11 would re-open a constraint this file has already exonerated.
  Recorded caveat: an operator running this test through the UI today would still SEE
  "Violated hard constraints: Shift envelope compliance". Test 11 is verified BELOW that display,
  not through it, and does not become false if the display is later fixed.
measured_from: API, not the UI — the display is the thing that is wrong here.
method: |
  An INDEPENDENT structural walker, deliberately sharing no code with the score director — the
  G-15-29 methodology applied to a read path instead of to a solve. For every agent-day it takes
  the authoritative ShiftDescriptor (templateName, startTime, endTime, bandOffsetMinutes,
  bandDurationMinutes), derives legal slots = envelope MINUS the assigned band's break window, and
  counts held seats outside that set. Limb 2 is checked separately: an agent-day with assignments
  but a null `shift` would be a working day with no shift.
walker_validation: |
  THE WALKER IS ITSELF VALIDATED, which matters more than any single number it produced. Run
  against all six ACCEPTED schedules on the desk, it reproduces EVERY hard score this file has
  recorded, exactly:

      9bd158dd  walker 12   <-> "-12 after bounded slack + Weekend Flex"   (test 10 progression)
      e6728aab  walker  9   <-> "-9" the baseline the whole session cites
      709fd8b4  walker  1   <-> "-1" Run D (weekend_edge_coverage)
      523c8785  walker  0   <-> "hard 0, FEASIBLE"
      b88cc98f  walker  0   <-> "0 HARD / -60 soft, FEASIBLE" (session_2026_09_01 Run 1)
      6a10afa1  walker  0   <-> newest accepted, undocumented until now

  Six for six, across values 12/9/1/0/0/0. A walker that agreed only where the answer is 0 would
  prove nothing; one that reproduces the non-zero history too is measuring the same quantity the
  solver scored. It also AGREES exactly with the server's own per-agent-day `divergence` field
  (which is built from the D-07 denormalised columns) on every schedule.
evidence: |
  NEWEST ACCEPTED SCHEDULE 6a10afa1 (2026-09-01T22:06:23Z — created AFTER HANDOFF.md §8 was
  written, which still names b88cc98f as newest; §8 needs that correction):

      working agent-days                 138
      working agent-days with NO shift     0     <-- limb 2 holds
      out-of-envelope seats (walker)       0     <-- limb 1 holds
      out-of-envelope seats (server)       0     <-- independent agreement
      feasible                          true

  Both limbs of test 11 hold on the schedule the desk is actually live on.
blocked_by_reporting: |
  AND YET THE PRODUCT SAYS THE OPPOSITE. The same GET reports:
      violatedHardConstraints  ['Shift envelope compliance']
      constraintViolations     'Shift envelope compliance' HARD violationCount 1104
  1104 == 138 agent-days x 8 contracted hours == EVERY STAFFED SEAT ON THE DESK. Confirmed by
  walking a named row: Armaz Dugashvili 2026-01-05, shift `Mid 11:00-20:00` band offset 300
  (break 16:00-17:00), seats 11,12,13,14,15,17,18,19 — every seat inside the envelope, none in the
  break window, `divergence: null` — and all eight reported as violations.
  Filed as G-15-32. An operator running test 11 through the UI would fail it.
recommendation: |
  PASS on substance and take the misreport as its own gap (G-15-32), because they are different
  defects with different fixes: the guarantee is met and provably met, while the READ PATH is
  broken in a way that would equally misreport a schedule that was genuinely clean OR genuinely
  broken. Marking test 11 as an issue would attach the phase's headline guarantee to a defect that
  is not in the solver at all, and would re-open a constraint this file has already spent a whole
  session exonerating.

### 12. Breaks are distributed across bands, not simultaneous

expected: On a shift with multiple bands, agents sharing that shift are spread across bands rather than all breaking at once, and no band exceeds its capacity. This is the headline user-visible behaviour change.
result: pass
tested_against: dev (https://d2bbtcc80peap7.cloudfront.net), live solve on Stubhub (EN)
evidence: |
  BEFORE — one band per template, capacity NULL, so no alternative existed:
    2026-01-05 Late 12:00-21:00   14 agents   16:00-17:00 x14
    2026-01-09 Late 12:00-21:00   18 agents   16:00-17:00 x18
    2026-01-11 Weekend Late       15 agents   15:00-16:00 x15

  AFTER — three capped bands per template:
    2026-01-05 Late   15:00x4, 16:00x5, 17:00x5
    2026-01-09 Late   15:00x7, 16:00x5, 17:00x5
    2026-01-11 Wknd Late  14:00x6, 15:00x4, 16:00x6

  No band exceeded its capacity of 9 in any observed solve, and no capacityAdvisory fired.
causal_evidence: |
  Distribution is not merely cosmetic here — it removed hard violations. Agents forced to work
  THROUGH their own break went 13 -> 2 -> 0 as bands were added, and the per-day correlation was
  exact: Friday's 18 agents breaking together produced 3 such seats, Sunday's 15 produced 5, while
  SATURDAY produced ZERO because its 25 agents were already split across two templates with
  DIFFERENT break hours. Saturday was the natural control that confirmed the mechanism before any
  fix was applied.
note: |
  The desk's original library had one band per template, so this behaviour had never been
  exercised on live data before this round — the capability shipped in Phase 15 and the data
  never adopted it. V40 faithfully carried each Phase 14 single break forward, and nothing
  prompted an operator to add a second band. That prompt now exists (see test 8).

### 13. Agent Allocation groups by shift on a shift desk

expected: On a shift-scheduled desk, Agent Allocation in Schedule Results groups agents under their assigned shift, each group naming the shift and its headcount.
result: pass
tested_against: |
  dev (https://d2bbtcc80peap7.cloudfront.net) on the SHIPPED gap-closure build — deploy 33656348187,
  image tag 64bafd8, ECS task def wfm-service-dev:66. Accepted schedule 7cc71bf5, all seven dates
  read off the Agent Allocation tab by the operator and compared against the table computed
  independently from the API before it was shown.
evidence: |
  EXACT MATCH ON ALL SEVEN DATES — every group, every headcount, every daily total:

    2026-01-05  Early 1 · Morning 3 · Mid 4 · Late 10                    total 18   FOUR groups
    2026-01-06  Early 3 · Morning 2 · Daytime 2 · Mid 2 · Late 10        total 19
    2026-01-07  Early 1 · Morning 2 · Daytime 4 · Mid 4 · Late 7         total 18
    2026-01-08  Early 3 · Daytime 5 · Mid 3 · Late 7                     total 18   FOUR groups
    2026-01-09  Early 2 · Morning 4 · Daytime 3 · Mid 5 · Late 8         total 22
    2026-01-10  Wknd Opening 1 · Wknd Flex 9 · Wknd Late 14 · Wknd Closing 1   total 25   FOUR groups
    2026-01-11  Wknd Opening 1 · Wknd Early 2 · Wknd Flex 7 · Wknd Late 7 · Wknd Closing 1   total 18

  The prediction was made from the API and recorded in this file BEFORE the operator opened the
  tab, so this is a genuine comparison rather than a post-hoc reading.

  GROUP HEADERS carry all three required facts — "Morning · 09:00–18:00 · 3 agent(s)" names the
  shift, its envelope and its headcount.

  ALL FOUR what_to_watch ITEMS CLEAN:
    1. Header times are the TEMPLATE's. The decisive case is 2026-01-05: Augusto Correia, Cindy
       Rodriguez and Juan Diego Dieguez each hold an 08:00 seat, yet their group header still reads
       "Morning · 09:00–18:00" and the stray seat is marked E! rather than the header being widened
       to 08:00–18:00 to swallow it. That is exactly the G-15-10 D4 disagreement NOT happening.
    2. No empty group on any of the three four-group dates (05, 08, 10).
    3. No weekday template on Sat/Sun, no weekend template on Mon-Fri.
    4. No "No shift assigned" group anywhere.

  SORT ORDER matches ScheduleResults.tsx:592 — start time ascending (Early 08:00, Morning 09:00,
  Daytime 10:00, Mid 11:00, Late 12:00; Weekend Opening 08:00 ... Weekend Closing 12:00).
bonus_evidence_for_test_19: |
  Not asked for by test 13 and recorded because it was observed — the allocation grid carries most
  of what test 19 asks about, and it renders correctly:
    - E! and × are BOTH present and visually distinct (E! on the three 08:00 seats, × on the
      Weekend Flex slack slots).
    - The extended legend is complete and legible: "E! = seat outside assigned envelope",
      "× = legal slot left unworked", "No shift covers this hour (unstaffed by design)",
      "Unfilled seat(s)", "Break B", "Red name = allocation violation".
  This does NOT close test 19 — its first bullet (Shift column agreeing with the group header) and
  its tooltip legibility bullet are Agent-Schedule-tab items — but it retires most of the doubt.
reported: "[operator pasted the full Agent Allocation tab for all seven dates]"
precomputed_expectation: |
  RE-COMPUTED 2026-09-02T17:00Z against the SHIPPED gap-closure round (deploy 33656348187, image
  tag 64bafd8, ECS task def wfm-service-dev:66). The previous table was computed 2026-09-01 against
  a320ca7 — i.e. BEFORE the 15-16..15-20 round — and carried its own instruction to re-compute if
  the solver or library changed. Both changed (SolverService +252, ShiftLibraryGenerationService
  +272), so it was re-derived rather than trusted.

  Same schedule 6a10afa1-1823-4773-8563-598a08fb2952 (still the newest ACCEPTED, hard 0 / soft -68,
  feasible), so this is a clean before/after: identical stored rows, new rendering code.

  ORDERED THE WAY THE PAGE ORDERS IT — ScheduleResults.tsx:592 sorts groups by shift start time
  ascending, tie-broken alphabetically by template name, with the null bucket last. The old table
  was ordered by headcount descending, which meant comparing it to the screen required re-sorting
  in your head. Read top-to-bottom against the page.

    2026-01-05 (18)  Early 08:00-17:00 x4 · Morning 09:00-18:00 x1 · Daytime 10:00-19:00 x1
                     · Mid 11:00-20:00 x3 · Late 12:00-21:00 x9
    2026-01-06 (19)  Early x3 · Morning x2 · Daytime x4 · Mid x2 · Late x8
    2026-01-07 (18)  Early x1 · Morning x3 · Daytime x2 · Mid x4 · Late x8
    2026-01-08 (18)  Early x2 · Daytime x4 · Mid x4 · Late x8      <-- FOUR groups, no Morning
    2026-01-09 (22)  Early x3 · Morning x3 · Daytime x1 · Mid x9 · Late x6
    2026-01-10 (25)  Weekend Opening 08:00-17:00 x1 · Weekend Early 10:00-19:00 x2
                     · Weekend Flex 10:00-20:00 x10 · Weekend Late 11:00-20:00 x10
                     · Weekend Closing 12:00-21:00 x2
    2026-01-11 (18)  Weekend Opening x1 · Weekend Early x1 · Weekend Flex x7 · Weekend Late x8
                     · Weekend Closing x1

  10 distinct groups, 138 working agent-days, every one carrying a shift.

  THE COUNTS ARE UNCHANGED — every date, every group, every headcount matches the 2026-09-01 table
  exactly. That is a RESULT, not a formality: the gap-closure round rewrote the seat-supply gate
  (G-15-21/-24/-25/-31) and the library generator (G-15-23), and none of it perturbed the accepted
  schedule's shift assignment. Weekend template times are now written out in full; the old table
  abbreviated them and only gave times for Weekend Opening.

  SUPERSEDED AS "NEWEST ACCEPTED" 2026-09-02T17:09Z — the operator ran a fresh solve and accepted
  it as 7cc71bf5-829c-4e41-85a0-c23bef70d198 (hard -30, soft -67, 500% over-allocation, 7m39s).
  6a10afa1 still EXISTS and the table above still describes it exactly, so either schedule can be
  used for test 13; open the one on screen. The fresh solve's grouping, same page order:

    2026-01-05 (18)  Early 08:00-17:00 x1 · Morning 09:00-18:00 x3 · Mid 11:00-20:00 x4
                     · Late 12:00-21:00 x10          <-- FOUR groups, no Daytime
    2026-01-06 (19)  Early x3 · Morning x2 · Daytime x2 · Mid x2 · Late x10
    2026-01-07 (18)  Early x1 · Morning x2 · Daytime x4 · Mid x4 · Late x7
    2026-01-08 (18)  Early x3 · Daytime x5 · Mid x3 · Late x7      <-- FOUR groups, no Morning
    2026-01-09 (22)  Early x2 · Morning x4 · Daytime x3 · Mid x5 · Late x8
    2026-01-10 (25)  Wknd Opening 08:00-17:00 x1 · Wknd Flex 10:00-20:00 x9
                     · Wknd Late 11:00-20:00 x14 · Wknd Closing 12:00-21:00 x1
                     <-- FOUR groups, no Weekend Early
    2026-01-11 (18)  Wknd Opening x1 · Wknd Early 10:00-19:00 x2 · Wknd Flex x7 · Wknd Late x7
                     · Wknd Closing x1

  9 distinct groups, 138 working agent-days, every one carrying a shift. Note this schedule has
  THREE dates with only four groups (05, 08, 10), so the "empty group" check in what_to_watch has
  three places to fail rather than one. All four invariants re-measured on it and clean: 0
  header/row disagreements, 0 null-bucket agent-days, 0 cross-calendar assignments.
what_to_watch: |
  1. GROUP HEADER TIMES MUST BE THE TEMPLATE'S, not derived from held seats. This is the exact
     disagreement that surfaced G-15-10's D4 (same agent-day reading "Late 12:00-21:00" in the
     header and "09:00-21:00" in the table). ScheduleResults.tsx:632 uses the authoritative values,
     so the header is the trustworthy side; if the two disagree at resume, THAT is the finding.
     MEASURED 2026-09-02 on the shipped build: of 138 agent-days, ZERO disagree — `shift.startTime`
     /`shift.endTime` equal `shiftStart`/`shiftEnd` on every row. So the API data is clean and any
     disagreement visible ON SCREEN is a pure rendering defect, not the D4 data bug resurfacing.
  2. 2026-01-08 has FOUR groups. An empty "Morning" group rendered there would be a defect the API
     data does not show. (Re-confirmed on the shipped build.)
  3. No weekday template on Sat/Sun and no weekend template on Mon-Fri. The data is clean on this
     — it is validWeekdays enforcement (b2dd702) visible in the grouping — so confirm it survives
     to the screen. Re-measured 2026-09-02: zero cross-calendar assignments.
  4. NO "No shift assigned" GROUP SHOULD RENDER. All 138 agent-days carry a non-null `shift`, so
     the null bucket is empty. One appearing on screen is a finding.
caveat_at_pause: |
  RETIRED 2026-09-02 — the caveat below described the pre-fix build and no longer applies.

  It read: "Until G-15-32 is fixed, the results page for ANY accepted schedule also shows
  'Violated hard constraints: Shift envelope compliance' and 1104 violations."

  G-15-32 shipped in the round now deployed (plan 15-16 task 1, 579b090). Re-read live from the
  same accepted schedule on the new build: `violatedHardConstraints: []` and `warnings: []`,
  against a true score of hard 0 — the constant 1104-violation misreport is gone. If the results
  page still shows a "Shift envelope compliance" hard violation for 6a10afa1, that IS now a test 13
  finding rather than a known misreport to look past.

### 14. A slot-scheduled desk is completely unchanged

expected: A desk still in slot mode behaves exactly as before — same Agent Allocation rendering, same solve behaviour, same validation. No shift-mode UI appears on it.
result: pass
reported: "It was scheduled in SLOT mode - there are no shifts"
tested_against: |
  dev on the shipped build (image 8f61493, ECS task def wfm-service-dev:67, rollout COMPLETED,
  health UP) — so the visual check ran against the newest code including the divergence-marker fix,
  even though that fix cannot reach a slot desk.
verdict_scope: |
  The operator's sentence answers the test's LAST and most important clause — "No shift-mode UI
  appears on it" — from the screen, which is the half no automated test in this project can reach.
  Read together with the API evidence below (0 entries carrying .shift, 0 carrying .divergence) the
  two agree: there are no shifts in the data and no shifts on the page.

  What the verdict does NOT separately confirm is the finer visual detail — the absence of the E!/×
  glyphs and the envelope legend specifically, as opposed to shifts generally. Those are gated on
  the same null `.shift`/`.divergence` the API proves absent, and on the ScheduleResults.tsx:422
  early return, so they cannot render without the data that is not there. Noted as inference rather
  than observation.
teardown_2026_09_02_second: |
  DISPOSED cleanly, and the disposal itself confirmed the lesson the first teardown taught:
    accepted schedules on desk   0  (the schedule was deliberately left COMPLETED)
    9 agents removed             roster -> 0
    DELETE desk                  204 on the FIRST attempt — no 409, no second step
    GET desk                     404
  Live state after: 1 desk, Stubhub (EN) still 28 agents and 11 templates, tenant unassigned back
  to 14. So "do not accept the scratch schedule" is the whole difference between a one-call
  teardown and a two-call one.
setup_problem: |
  The tenant has ONE desk (Stubhub (EN)) and it is in SHIFT mode, so there is no slot-mode desk to
  observe. Same shape of problem as test 3, and the same answer: a throwaway DESK, which
  DeskController can delete and whose cascade test 3 already verified end to end.

  ROSTER IS AVAILABLE AND SAFE TO USE. Checked before proposing it: the tenant has 42 agents, 28 on
  the live desk and 14 UNASSIGNED (deskId null). `DeskAgentService.assignAgents` REFUSES any agent
  that already holds a desk ("Agent '...' is already assigned to a desk"), so the live desk's 28
  cannot be stolen even by mistake — the guard is in the code, not in my care. Assigning some of
  the 14 and removing them afterwards restores their prior state exactly (deskId back to null).
  This is why test 14 CAN exercise real solve behaviour where test 3 deliberately could not.
automated_coverage_already_held: |
  Run locally on HEAD before touching anything live — 6 classes, 67 tests, 0 failures. Slot-mode
  invariance is already pinned BELOW the UI:
    ScheduleOutputServiceShiftReportingTest.buildAgentSchedule_slotMode_producesByteIdenticalEntriesToToday
    ScheduleServiceShiftSnapshotTest.buildAgentSchedule_slotMode_returnsNullShiftOnEveryEntryAndOtherFieldsUnchanged
    ScheduleServiceShiftSnapshotTest.acceptSchedule_slotMode_writesZeroShiftRowsAndLeavesAssignmentSnapshotUnchanged
    ShiftModeMinimumStaffingSeatSupplyTest.slotModeOutputIsInvariantToShiftContext
    ZeroDemandTimeslotCeilingTest.slotMode_zeroDemandTimeslot_seatAndMissingCeilingUnchanged
    + slotMode_* cases across 4 constraint tests
structural_argument: |
  What is left for a human is the SCREEN, and the code says the screen cannot diverge:
    ScheduleResults.tsx:419-422  the mode branch is the FIRST statement of the rendering decision,
      with its own comment — "a slot-scheduled desk renders the exact table below, byte-for-byte,
      no restructuring whatsoever; no Phase 15 grouping code executes on this branch (T-15-28)".
    ScheduleOutputService.java:197-215  on a slot desk shiftDescriptor is null, so `divergence`
      stays null and the else-branch takes the seat-derived span and gap-derived breaks — commented
      "Keep today's behaviour exactly".
  That also means the 2026-09-02 divergence-marker fix cannot reach a slot desk: BOTH of its
  branches are gated on `e.divergence &&`, which is null here.
  Backend gating is symmetric in ScheduleConstraintProvider — shift constraints filter
  `== SchedulingMode.SHIFT`, slot constraints filter `!= SchedulingMode.SHIFT`.
scratch_desk_built_2026_09_02: |
  BUILT ON OPERATOR INSTRUCTION ("build it"). Live on dev, awaiting the operator's visual check.

    desk    ZZ-UAT14-SCRATCH-slot-mode  33ab13db-0379-4aba-b930-f807928fc7bc   mode SLOT
    spec    English  e33ed7eb-6ed2-4137-bca2-13614f8a183f
    period  Mon 2026-01-05, 09 hourly timeslots 08:00-17:00, demand 2 FTE every hour (18 FTE-slots)
    roster  9 agents borrowed from the 14 UNASSIGNED; the live desk's 28 were never touched
    solve   5e27329f-837c-4b15-aa73-5c404f1c67b6

  MODE WAS NOT SET BY HAND — the desk was created with no mode field and read back `SLOT`, which is
  the documented default (`desk_savedWithoutModeSet_readsBackAsSlot`). So this is the real default
  path, not a desk forced into slot mode.

  API-SIDE RESULT — every invariant clean, on a FEASIBLE solve:
    schedulingMode                  SLOT
    score                           0 hard / 0 soft, feasible=true
    agentSchedule entries           3
    entries with .shift != null     0
    entries with .divergence != null 0
    warnings                        0
    violatedHardConstraints         []
    constraintViolations            NONE AT ALL
    shift/envelope/band/contiguity constraints scored   none
    coverage                        18 predicted vs 24 actual = 133.33%

  A 0-hard/0-soft feasible solve is worth more here than a merely-clean one: it shows the slot path
  still SOLVES, not just that the shift features stayed quiet. "Same solve behaviour" is the half
  of this test the structural argument could not reach.

  ROSTER NOTE, so the 9-vs-3 gap is not misread as a defect. Only 3 of the 9 assigned agents are
  solver-eligible, and `SolverService.filterEligible` (:2066) says why — it requires active + job
  title on the tenant allowlist + a primary specialization + `workingDaysKnown`. Chantelle Abel-Obi
  is a "Subject Matter Expert" (not allowlisted); Lizi Arkania shares Elene Tsakadze's allowlisted
  "Customer Support Representative" title, so she is excluded on `workingDaysKnown`. Pre-existing
  roster data, nothing to do with this phase.

  WARNINGS DID NOT GROW across 5 polls of a RUNNING schedule (5 polls, warnings stayed 0; the
  earlier 3-agent solve held at 1 across its 5 polls). Weak evidence for test 20's CR-04 fix rather
  than strong — on a SLOT desk `publishDivergenceWarning` finds zero out-of-envelope seats and
  publishes nothing — but it is consistent, and it is free.

  DISPOSAL PLAN, to run after the operator's visual check: remove the 9 agents (restores deskId to
  null, their prior state exactly), then DELETE the desk, then verify as test 3 did — GET desk ->
  404, and confirm the live desk still holds its 28 agents and 11 templates.
torn_down_2026_09_02: |
  DISPOSED on operator instruction ("tear it down"), and verified rather than assumed:

    9 agents removed          all 204; desk roster -> 0
    agents released cleanly   tenant unassigned 14 -> 5 while borrowed -> 14 again, the exact
                              pre-borrow count, so every borrowed agent went back to deskId null
    desk DELETE (1st try)     409 "Cannot delete desk with accepted schedules"
    accepted schedule deleted 204   (58f21bae — see note below)
    desk DELETE (2nd try)     204
    GET desk                  404
    cascade                   timeslots [], specializations 0, staffing-requirements 0, schedules 0

  LIVE DESK UNTOUCHED, checked against the baseline taken before teardown began:
    Stubhub (EN)  28 agents (was 28) · 11 templates (was 11) · mode SHIFT · 7 schedules · health UP
    tenant desks 2 -> 1

  THE 409 IS A FINDING ABOUT THE TEST, NOT A DEFECT. `DeskService.deleteDesk` (:132) refuses while
  any ACCEPTED schedule exists — correct behaviour, and the same protective instinct as the shift
  template delete control (test 5b). But it means "create a throwaway desk, use it, delete it" is
  NOT a complete disposal route on its own: accepting anything on the scratch desk strands it until
  the schedule is deleted first. Test 3's disposal was clean only because it never accepted
  anything. Worth knowing before the next scratch desk.

  SCHEDULE 58f21bae WAS NOT MINE. My three solves were ce41f5b1, 48966bfd and 5e27329f; 58f21bae
  was created 20:22:17Z, during the operator's visual check, and accepting a schedule mints a new
  persisted id (the same rename seen on the live desk when cf6f516e became 7cc71bf5). So it is the
  operator's acceptance of solve 5e27329f. Recorded because it is the only evidence in this file
  that the scratch desk was actually opened in the UI — and it is NOT a substitute for a verdict.
rebuilt_2026_09_02: |
  REBUILT on operator instruction ("rebuild it") so the visual half can actually be observed.
  The ui_half_unobserved note below describes the FIRST fixture's disposal and is kept as the
  record of why this rebuild happened; its "cannot be run without rebuilding" no longer applies.

    desk    ZZ-UAT14-SCRATCH-slot-mode  5d8a6c1d-5af9-4100-809f-3b875aa5a2a1   mode SLOT (default)
    spec    English  980b2ddd-d8df-4c52-a8d3-c4be6a8da453
    period  Mon 2026-01-05, 9 hourly timeslots 08:00-17:00, 2 FTE/hour = 18 FTE-slots
    roster  the same 9 active unassigned agents
    solve   56dcd502-65e1-4713-85a6-d4a8dffc1812

  REPRODUCED THE FIRST FIXTURE EXACTLY, which is itself worth recording — the slot path is
  deterministic across a full teardown and rebuild:
    score 0 hard / 0 soft, feasible=true, mode SLOT
    3 agent-day entries, the SAME three eligible agents (Elene Tsakadze, Mariam Zeikidze,
      Nino Ninoshvili), the other 6 excluded by filterEligible exactly as before
    entries with .shift 0 · entries with .divergence 0 · warnings 0 · constraintViolations 0

  DO NOT ACCEPT THIS SCHEDULE. Accepting it strands the desk behind
  DeskService.deleteDesk's 409 guard and makes disposal a two-step job — that is what happened to
  the first fixture. Leaving it COMPLETED keeps teardown to a single DELETE.
ui_half_unobserved: |
  RECORD OF THE FIRST FIXTURE'S DISPOSAL — superseded by rebuilt_2026_09_02 above.

  STATED PLAINLY so this test is not over-read. The API half is verified above and is strong. The
  SCREEN half was never reported: the operator was asked for a visual verdict, and the next
  instruction was "tear it down" with no verdict given. The desk was then deleted, so the four
  visual checks below could not be run without rebuilding the fixture:
    - Schedule Results shows the SLOT rendering — plain per-date agent table, no shift group
      headers, no "· HH:MM–HH:MM · N agent(s)" bars.
    - No E! marks, no × marks, no "unstaffed by design" treatment, no envelope legend.
    - Agent Schedule tab shows no ⚠ badge and no grey "legal slot unworked" badge.
    - Shift Library page offers the SLOT-mode state.
  What DOES stand without them: ScheduleResults.tsx:419-422 makes the mode branch the first
  statement of the rendering decision and no Phase 15 grouping code executes on it, and the API
  proves `.shift` and `.divergence` are null on every entry — so the shift-mode UI has no data to
  render even if it were reached. That is a strong structural argument, not an observation.
  Rebuilding the fixture takes about ten minutes if the visual check is wanted.

### 14b. (incidental, FIXED) GET /api/v1/agents returns 500 unless `search` is supplied

expected: n/a — NOT a Phase 15 test. Recorded because it was found while setting up test 14.
result: skipped
reason: |
  OUT OF PHASE 15 SCOPE — `git log` shows AgentService.java and AgentRepository.java were last
  touched in phase 09 (32a8bfc), and nothing in this phase goes near them. Filed here for the
  operator to route rather than as a Phase 15 gap, following test 3's incidental_observation
  precedent.

  SYMPTOM, measured on dev:
    GET /api/v1/agents                     -> 500 INTERNAL_ERROR
    GET /api/v1/agents?limit=10            -> 500 INTERNAL_ERROR
    GET /api/v1/agents?unassigned=true     -> 500 INTERNAL_ERROR
    GET /api/v1/agents?search=             -> 200, 42 agents      <-- empty string, not absent
    GET /api/v1/agents?unassigned=true&search=  -> 200, 14 agents
  So the DEFAULT call — no parameters — is the one that fails.

  ROOT CAUSE, read from the ECS logs rather than inferred:
    ERROR: function lower(bytea) does not exist
  AgentRepository.findFiltered carries `(:search IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%',
  :search, '%')))`. When `search` is null the driver sends an UNTYPED null, Postgres types it as
  `bytea`, and `lower(bytea)` has no overload. An empty string types fine, which is exactly why
  `?search=` works and omitting it does not.

  WHY NO TEST CAUGHT IT: the suite runs on H2, which tolerates the untyped null. This is precisely
  the H2-vs-Postgres blind spot this file's own header warns about for the migrations — the same
  gap, in a query rather than a migration.

  LIKELY FIX (not applied, not in scope): type the parameter — `CAST(:search AS string)` in the
  JPQL, or split into two query methods, or pass "" instead of null from AgentService.listAgents.
  findFilteredAfterCursor carries the identical predicate and will fail the same way on the
  cursor path.

### 15. UPCOMING and RETIRED templates are not assignable (CR-01 fix)

expected: A template whose `effectiveFrom` is in the FUTURE is not assigned to any agent-day before that date. If it becomes effective partway through the schedule period, it is assignable only from `effectiveFrom` onward — not on earlier days of the same schedule. A RETIRED template (past `effectiveTo`) is likewise never assignable. *Newly written code, fixed after the main phase, with the least field exposure of anything here.*
result: pass
tested_against: |
  dev, live desk Stubhub (EN), on the shipped build (image 8f61493, task def :67). Operator
  authorised the live-library route. Probe schedule c45054f5 over 2026-01-05..11, then fully
  reverted — see disposal below.
method: |
  A CONTROLLED EXPERIMENT rather than an observation, because "the template was not used" is
  ambiguous on its own — the solver might simply not have wanted that shape. Each era template was
  created as an EXACT CLONE of a heavily-used live template, identical in envelope, valid weekdays
  and all three bands (180/60/9, 240/60/9, 300/60/9, net 8.00h). The ONLY difference between a
  clone and its twin is the effective range, so any difference in usage is attributable to that
  and nothing else.

    ZZ-UAT15-UPCOMING   clone of Late          12:00-21:00 Mon-Fri   effectiveFrom 2027-01-01
    ZZ-UAT15-RETIRED    clone of Weekend Late  11:00-20:00 Sat/Sun   effectiveFrom 2025-01-01,
                                                                     effectiveTo   2025-12-31
    ZZ-UAT15-MIDPERIOD  clone of Late          12:00-21:00 Mon-Fri   effectiveFrom 2026-01-08
                                                                     (Thursday, mid-period)
  eraStatus read back as UPCOMING / PAST / CURRENT respectively, so the API agreed about their
  lifecycle before the solve ran.
evidence: |
  138 agent-days. Usage of each era template against its twin:

    ZZ-UAT15-UPCOMING     0 agent-days   <-- its twin "Late" got 34
    ZZ-UAT15-RETIRED      0 agent-days   <-- its twin "Weekend Late" got 20
    ZZ-UAT15-MIDPERIOD    7 agent-days   <-- ACTIVELY CHOSEN, and only where it is effective

  THE MID-PERIOD RESULT IS THE DECISIVE ONE, and it settles the claim no unit test can reach in the
  field — that enforcement is PER AGENT-DAY rather than per desk:

    2026-01-05  0      2026-01-08  3     <-- effectiveFrom
    2026-01-06  0      2026-01-09  4
    2026-01-07  0      2026-01-10/11  0  (Sat/Sun — Mon-Fri template, validWeekdays)

  A desk-level filter could only have produced 0 everywhere (excluded outright) or assignments on
  01-05..07 too (included for the whole period). Exactly 01-08 and 01-09 is reachable ONLY by
  evaluating the effective range against each row's own date. It also rules out the alternative
  reading of the two zeroes — the solver plainly WILL use a ZZ- template, 7 times, when it is
  effective, so the zeroes are the date filter working and not the solver ignoring new rows.
corroborating_natural_experiment: |
  Free, from data that already existed, and checked BEFORE anything was written. The live library
  already carried a retired template — "Weekend Morning" 09:00-18:00 Sat/Sun, effectiveTo
  2026-01-01, era PAST — retired before the 2026-01-05..11 period every schedule covers. Across
  ALL SEVEN accepted schedules (966 agent-days) it appears ZERO times, while 8-10 distinct
  templates are used in each. Weaker than the clone experiment on its own (no control proving the
  solver wanted that shape), which is exactly why the clone experiment was run.
not_a_regression_signal: |
  The probe solve scored hard -40 against the -30 of 7cc71bf5. That is NOT a regression reading:
  the library differed (three extra templates), it is a single run, and G-15-29's own rule says
  weights and scores are comparable only by violation COUNT across repeated runs. -40 is 4
  violations at the ofHard(10) envelope weight. Recorded so the number is not mistaken for one.
disposal: |
  FULLY REVERTED, verified against the baseline captured before any write:
    probe schedule c45054f5   REJECTED (204) — never accepted, so nothing persisted
    3 templates DELETED       204, 204, 204
    library                   14 -> 11, ZZ-UAT15 remaining 0
    schedules                 7, all ACCEPTED — exactly the pre-probe set
  THE THREE 204s ARE THEMSELVES EVIDENCE. `DELETE /shift-templates/{id}` refuses with 409 when any
  agent_shift_assignment references the template (81117e3, deliberately stricter than the FK
  requires). A clean 204 on all three is an independent confirmation, from a different code path
  than the report, that no persisted assignment ever referenced them.
reported: "[operator authorised the live-desk route; result measured from the API]"

### 16. Accepted schedule keeps its true mode (CR-02 fix)

expected: Accept a shift-mode schedule, reopen it — Schedule Results reports SHIFT and renders the shift view. This must hold even for a schedule accepted with few or NO placed shifts, and must not change if the desk's mode is switched afterwards. *Legacy caveat: schedules accepted BEFORE this deploy are backfilled by inference, so a pre-existing shift-mode accept that placed zero shifts will read SLOT. That is unrecoverable — the true fact was never recorded — not a new bug. Verify only that post-deploy accepts are exact.*
result: pass
tested_against: |
  dev, live desk Stubhub (EN), shipped build (image 8f61493, task def :67), plus the automated
  suite run locally on HEAD. Operator authorised the live mode round-trip.
the_mechanism: |
  CR-02 replaced an INFERENCE with a RECORDED FACT. `schedule.scheduling_mode` is a mapped column
  (V43, `75349d8`), written by SolverService.buildSchedule from Desk.schedulingMode at SOLVE time
  and persisted unchanged at accept; ScheduleService:573 reads it directly. Nothing derives it from
  shift-row presence any more. That is why claim 2 holds structurally: the value is fixed before a
  single shift is placed, so how many get placed cannot affect it.
claim_1_reported_mode: |
  All 7 accepted schedules on the live desk report SHIFT.
  HONEST SCOPE: this is NON-DISCRIMINATING on its own. Every one carries shifts on 138 of 138
  agent-days, so the OLD inference ("has shift rows -> SHIFT") would return the same answer. It
  confirms nothing regressed; it cannot confirm the fix. The discriminating evidence is below.
claim_2_zero_placed_shifts: |
  Covered by a purpose-built regression test, verified passing on HEAD:
    ScheduleServiceShiftSnapshotTest.getScheduleDetail_acceptedShiftModeSchedule_zeroPlacedShifts_stillReportsShift

  It is well constructed in the way that matters — it asserts
  `agentShiftAssignmentRepository.findByTenantIdAndDeskIdAndScheduleId(...)` is EMPTY *before*
  asserting the mode reads SHIFT, so it cannot silently decay into the ordinary at-least-one-shift
  case. Its sibling test carries the fix's own history in its name: it was renamed FROM
  `...derivesSchedulingModeFromShiftRowPresence`, i.e. the pre-fix test asserted the very inference
  that was the defect.

  Not reproduced live: engineering a zero-placed-shift solve on the live desk would mean giving it
  a library that matches no agent's contracted hours, which the seat-supply gate exists to refuse.
  The unit test reaches the state directly and proves it reached it.
claim_3_survives_a_desk_mode_switch: |
  THE ONE WITH NO AUTOMATED GUARD — see coverage_gap_found below — so it was measured live.

  ROUND TRIP on the live desk, 2026-09-02:
    pre        desk SHIFT · 0 RUNNING schedules · validation clean (0 uncovered windows)
    SHIFT->SLOT  HTTP 200, desk reads SLOT
    WHILE THE DESK WAS IN SLOT, all 7 accepted schedules still read:
       7cc71bf5 SHIFT · 6a10afa1 SHIFT · b88cc98f SHIFT · 523c8785 SHIFT
       709fd8b4 SHIFT · e6728aab SHIFT · 9bd158dd SHIFT
    and their shift descriptors survived intact — withShift 138/138 on every one, so the mode is
    not merely a label that stayed put while the payload emptied.
    SLOT->SHIFT  HTTP 200 (the validator DOES run on this direction and passed), desk reads SHIFT
    post       byte-identical to the pre-switch reading; 11 templates, 28 agents, unchanged

  Desk mode and schedule mode are now independent facts, which is exactly what CR-02 set out to
  make true.
coverage_gap_found: |
  FOUND WHILE VERIFYING CLAIM 3, and it is why claim 3 was worth measuring rather than assuming.

  `DeskServiceSchedulingModeTest.switchSchedulingMode_roundTrip_leavesAcceptedScheduleAndSnapshotRowsExactlyUnchanged`
  reads like the guard for this claim. It is not. Its private `ScheduleSnapshot` record (:393)
  captures 18 fields — id, tenantId, deskId, incrementMinutes, start/endTime, period dates, the
  break settings, contracted hours, the two allocation limits, status, errorMessage, version — and
  `schedulingMode` is NOT among them. The test would therefore pass unchanged even if a desk mode
  switch corrupted the accepted schedule's `scheduling_mode`, despite asserting "ExactlyUnchanged".

  WHY IT HAPPENED, from the history rather than guessed: the test was last touched in PHASE 14
  (`b7f5b3b feat(14-05)`), and the column it omits was introduced in PHASE 15 (`75349d8`). The
  snapshot simply predates the field and was never extended when CR-02 added it. No other test
  pairs a desk mode switch with an accepted-schedule mode read.

  SEVERITY: low as things stand — the behaviour is correct, as the live round-trip above proves.
  The risk is REGRESSION, not present breakage: nothing would fail if a future change made the
  desk switch write through to accepted schedules. Suggested fix is one line — add
  `s.getSchedulingMode()` to the ScheduleSnapshot record and its `of(...)` factory, which converts
  an existing passing test into a real guard without writing a new one.
  Recorded as an observation for the operator to route; NOT filed as a Phase 15 gap, since the
  shipped behaviour meets the phase's stated requirement.
closed_2026_09_02: |
  CLOSED on operator instruction ("fix the ScheduleSnapshot gap too") — commit 883a103.

  THE OBVIOUS FIX WOULD HAVE BEEN A DUD, and this is the part worth remembering. Adding
  `schedulingMode` to the record is necessary but NOT sufficient: `saveSchedule` leaves the
  schedule at the SLOT entity default and the desk round-trips SLOT -> SHIFT -> SLOT, so the
  assertion would have compared SLOT against SLOT. A regression that copied the desk's mode onto
  accepted schedules ends on SLOT too — the guard would have passed while the behaviour was
  broken, which is the same shape of false comfort the original omission created.

  So the accepted schedule is now deliberately SHIFT while the desk ends on SLOT. The MISMATCH is
  the mechanism: only a write-through can turn SHIFT into SLOT, and that is exactly what must fail.

  PROVEN TO BITE, not assumed. The regression was simulated (writing the desk's final mode onto the
  accepted schedule after the round trip) and the test failed with precisely:
      expected: schedulingMode = SHIFT
      but was:  schedulingMode = SLOT
  then the simulation was removed and the suite re-run green — 92 classes, 645 tests, 0 failures.

  Test 16's claim 3 is now guarded by the suite rather than resting on the one-off live round-trip
  recorded above.
reported: "[operator authorised the live mode round-trip; result measured from the API]"

### 17. Deleting an accepted shift schedule leaves no orphans (CR-03 fix)

expected: Delete an accepted shift-mode schedule, then confirm no rows remain:

```sql
SELECT count(*) FROM agent_shift_assignment WHERE schedule_id = '<deleted-schedule-id>';
```

Expected: `0`.
result: pass
tested_against: |
  dev, throwaway desk ZZ-UAT17-SCRATCH-orphans (968fda8c), shipped build 8f61493 / task def :67,
  plus the automated regression run locally on HEAD. NOT run on the live desk — see why below.
why_not_the_live_desk: |
  Deliberate. `ScheduleService.acceptSchedule` (:265) supersedes the currently-ACCEPTED
  accepted_schedule_date rows for every date the new schedule covers, and `deleteSchedule`
  (:386-405) does NOT restore them. Accepting a throwaway schedule on Stubhub (EN) for
  2026-01-05..11 would therefore demote 7cc71bf5's date rows to SUPERSEDED, and deleting it again
  would leave those dates with NO accepted schedule at all. A scratch desk avoids damaging the
  operator's live acceptance state.
how_it_was_observed_without_sql: |
  The test as written is a SQL count, and psql to the RDS instance times out (test 5b — the
  instance is PubliclyAccessible but its security group admits no arbitrary client IP). The same
  count is reachable through the API: `ShiftTemplateService.deleteShiftTemplate` (:141) calls
  `agentShiftAssignmentRepository.countByTenantIdAndSourceTemplateId(tenantId, id)` and REFUSES
  with 409 when it is > 0 — and its message reports the count. So a template delete is a read of
  exactly the table test 17 asks about.
evidence: |
  A THREE-STEP PROOF WITH A POSITIVE CONTROL, on an accepted SHIFT-mode schedule (mode read back
  SHIFT, 3 of 3 agent-days carrying shifts):

    STEP 1  DELETE template BEFORE deleting the schedule
            409 — "Shift template 'AllDay' cannot be deleted: it is used by 3 agent-day
                   assignment(s) in one or more schedules."
            => exactly 3 agent_shift_assignment rows existed, counted by the server itself.

    STEP 2  DELETE the accepted schedule            204
            GET the deleted schedule                404

    STEP 3  DELETE template AFTER                   204
            => countByTenantIdAndSourceTemplateId returned 0. NO ORPHANS.

  THE POSITIVE CONTROL IS WHAT MAKES THIS CONCLUSIVE. Step 1 proves the query can see these rows
  and would have refused had any survived; step 3's success is therefore a measurement of zero,
  not an absence of evidence. 3 -> 0, caused by the schedule delete and nothing else.
automated_guard: |
  ScheduleServiceShiftSnapshotTest.deleteSchedule_shiftMode_deletesAgentShiftAssignmentRows,
  verified passing on HEAD. Built with the same discipline as the CR-02 zero-shift test — it
  asserts the repository has `hasSize(1)` BEFORE the delete and `isEmpty()` after, so it cannot
  pass vacuously on a schedule that never had shift rows.
the_mechanism: |
  ScheduleService:402 — `agentShiftAssignmentRepository.deleteByTenantIdAndDeskIdAndScheduleId(...)`.
  Needed explicitly because `agent_shift_assignment.schedule_id` carries NO foreign key (V41, D-07
  denormalisation), so deleting the schedule row cannot cascade to it. Before CR-03 the repository
  exposed no deleteBy* method at all and the rows were silently stranded.
disposal: |
  9 agents removed (roster -> 0), desk DELETE 204 on the first attempt — the accepted schedule had
  already been deleted as step 2, so the DeskService 409 guard did not fire. GET desk -> 404.
  Live state after: 1 desk, unassigned back to 14, Stubhub (EN) untouched at SHIFT / 28 agents /
  11 templates / 7 schedules.
reported: "[measured from the API on a throwaway desk]"

### 17b. (incidental, FIXED) A desk whose agents rely on the default contracted hours cannot enter SHIFT mode

expected: n/a — NOT a Phase 15 test. Found while building test 17's fixture.
result: skipped
reason: |
  OUT OF PHASE 15 SCOPE — `ShiftLibraryValidationService.loadHoursByWeekday` dates from
  `bcf8c84 feat(14-04)`, i.e. Phase 14. Recorded for the operator to route, following the test 3
  and test 14b precedent.

  SYMPTOM. A new desk whose agents carry no per-day hours rows is refused entry to SHIFT mode:
    PUT /scheduling-mode {SHIFT} -> 400
    "1 weekday(s) have no shift template any agent's contracted hours can satisfy: Monday.
     Add or adjust a template, or update contracted hours, before switching modes."
  This was with a 9h envelope / 1h band template whose net is 8.00h, a desk default of 8.00h, and
  every agent's own screen showing `dayHours.MONDAY.effectiveHours = 8.00`. The refusal contradicts
  what the agent page displays.

  ROOT CAUSE. `loadHoursByWeekday` (:284) reads ONLY `AgentDayHours` rows
  (`findByTenantIdAndDeskId`) and applies NO fallback. With no rows the map is empty, so every
  weekday is unsatisfiable. The solver disagrees: `SolverService.computeAgentDayConfigs` resolves
  through `resolveEffectiveHours(exMap, dayHoursMap, d, schedule.getDefaultContractedHoursPerDay())`
  — i.e. it DOES fall back to the desk/schedule default. The validator is stricter than the solver
  about the same fact, and it is the validator that gates mode entry.

  PROVEN BY CONTROLLED FIX, not by reading alone. Writing MONDAY day-hours rows carrying the SAME
  8.00 value that was already the effective default — changing no template, no demand, no agent —
  flipped `unsatisfiableWeekdays` from ["MONDAY"] to [] and the switch then returned 200.

  WHY THE LIVE DESK NEVER HIT IT: all 28 of Stubhub (EN)'s agents have explicit MONDAY rows
  (hasRow=true), so it has always taken the populated path. Only 1 of the 28 has an agent-level
  `contractedHoursPerDay`, so the rows — not that field — are what saves it.

  CONSEQUENCE WORTH WEIGHING: this blocks SHIFT-mode adoption on any newly created desk until
  per-day hours are written for its agents, with a message that points at "contracted hours" the
  UI already shows as correct. Workaround is straightforward (set day hours), so it is friction
  rather than breakage — but it is friction at exactly the moment a new desk adopts the phase's
  headline feature.
fixed_2026_09_02: |
  FIXED on operator instruction ("Also fix bug - where new desk cant have shift mode"), RED first
  per this repo's convention:
    cd1a71e  test(15) — the failing test. RED: `Expecting empty but was: ["MONDAY"]`
    14f0868  fix(15)  — loadHoursByWeekday now mirrors SolverService.resolveEffectiveHours:
                        an agent_day_hours row wins where one exists, otherwise the DESK DEFAULT.

  TWO PROPERTIES DELIBERATELY PRESERVED, each with a test:
    - A desk with NO agents still reports every demanded weekday unsatisfiable. No agents means no
      hours, which is a different statement from "hours not recorded yet". The pre-existing
      validate_liveDemandAndTemplatesButZeroAgents test still passes and now reads as the
      deliberate contrast to the new one.
    - An explicit row still beats the default: validate_agentWithADayHoursRow_stillWinsOverTheDeskDefault
      (7.75h row against an 8.00h default and an 8.00h template keeps MONDAY unsatisfiable). It
      passes both before and after the fix, so it is a genuine non-regression guard.

  NO EXISTING DESK CAN CHANGE VERDICT. Checked before writing the fix, not asserted after: all 28
  of Stubhub (EN)'s agents carry rows for all 7 weekdays, so the fallback cannot fire there. It
  only fires for an (agent, weekday) pair that has no row.

  Full suite after the change: 90 classes, 637 tests, 0 failures.
verified_live_2026_09_02: |
  END-TO-END on the deployed fix — deploy run 33685883299, ECS task def wfm-service-dev:68, image
  tag 14f0868 (re-derived from the task definition, not from the green tick), health UP.

  A fresh desk taken down exactly the path that used to fail:
    desk created with no mode field            -> read back SLOT, default 8.00
    9 hourly timeslots Mon 2026-01-05, 2 FTE/hour
    one template, net 8.00h — matching ONLY the desk default, no explicit agent row anywhere
    5 agents assigned and specialised, every day-hours row CLEARED
    PRECONDITION ASSERTED: 0 day-hours rows across all agents and all 7 days
    API still displayed dayHours.MONDAY.effectiveHours = 8.00 — the fallback, visible

    unsatisfiableWeekdays   []      (was ["MONDAY"])
    PUT /scheduling-mode {SHIFT}    HTTP 200 -> SHIFT      (was HTTP 400)

  Desk deleted afterwards; 1 desk remaining, unassigned agents back to 14.

  THE FIRST ATTEMPT WAS INVALID AND IS RECORDED BECAUSE THE TRAP IS REUSABLE. It ran against
  borrowed agents that still carried MONDAY rows created by test 17's own workaround, so it
  reported 200 without the fallback ever firing — a green result that proved nothing. The cause is
  worth knowing before building any future scratch-desk fixture: **AgentDayHours hangs off the
  AGENT, not the desk** (the repository's desk scoping is a join through agent.deskId), so per-day
  hours FOLLOW an agent onto whatever desk they are next assigned to and survive both removal from
  a desk and that desk's deletion. Only the precondition assert caught it. Clearing the rows also
  restored those agents to the state they were in before this UAT borrowed them — test 14 recorded
  them as hasRow=false originally.

### 18. Production migration executed safely

expected: Before deploying to PRODUCTION, a restorable snapshot of `shift_template` exists (including `break_offset_minutes` and `break_duration_minutes`), because V40 DROPs both after fanning their data out — if the fan-out is wrong there, the source data is already gone. After the production deploy, Test 1's fan-out query is re-run against production and passes. Dev's four successful runs are a rehearsal, not a substitute: production has different data volume, different pre-existing rows, and different edge cases.
result: pass
closed_by_operator_ruling_2026_09_02: |
  OPERATOR RULING ("no cloud is dev", 2026-09-02): there is no separate production tier and none
  is planned. The cloud deployment NAMED "dev" is the live system — one environment, carrying real
  tenant data. This test was written assuming a two-tier dev→production promotion that does not
  exist in this project.

  WHY THAT CLOSES IT RATHER THAN EXCUSING IT. The test's substantive requirement is "after the
  deploy, Test 1's fan-out query is re-run against the live system and passes". That has already
  happened: Test 1 ran against this exact environment on live tenant data
  (https://d2bbtcc80peap7.cloudfront.net, 2026-08-27T21:26Z) — desk Stubhub (EN), all four real
  templates, each showing exactly 1 band with its former offset/duration (Early 12:00-13:00
  surviving the DROP intact, corroborated by V40's own header recording the pre-migration state).
  Zero templates with >1 band, zero with 0 bands. The pre-deploy snapshot concern is likewise
  spent: V40 has already run on the only system there is, and its fan-out was verified correct
  before the source columns became unrecoverable.

  WHAT IS NOT CLAIMED. No second migration run against a different data volume was performed,
  because no second environment exists to run one against. If a production tier is ever stood up,
  this test becomes live again and must be re-run there.

  SUPERSEDED BY: Test 1 (live cloud data, same environment).

  PRIOR STATE (kept for history, no longer the verdict):
    result: blocked
    blocked_by: server
    reason: No production deploy has occurred. `deploy.yml` targets the dev environment (ECS
      cluster `wfm-service-dev`) only. Unblock when a production deploy is planned.

### 19. Envelope divergence and unstaffed hours render correctly

expected: |
  Load a shift-mode schedule with a real envelope divergence (or force one) and confirm the new
  rendering from plans 15-10 and 15-12 lands on the correct cells:

    - the Agent Schedule table's Shift column agrees with the Agent Allocation group header for the
      same agent-day (the "Late 12:00-21:00" vs "09:00-21:00" disagreement is gone);

    - an inline divergence marker appears on the Agent Schedule row when the actual seats fall
      outside the assigned envelope;

    - per-cell `E!` (out-of-envelope seat, amber ring) is visually distinct from `x` (surrendered
      legal slot, amber fill);

    - an hour no template reaches renders muted/italic under an "unstaffed by design" header, and
      is clearly distinct from the existing red unfilled-demand treatment;

    - tooltips and the extended legend are legible.

result: pass
reported: |
  "E! and x are clearly distinct, header reads 08:00-17:00" (709fd8b4)
  "unstaffed hours render muted, E! cells clear, legend readable" (9bd158dd)
  "12 amber, 22 grey, matches the E! agents" (9bd158dd, Agent Schedule tab)
tested_against: |
  dev on the shipped build (image 8f61493 / task def :67), two schedules chosen so that between
  them every bullet has a case that actually exercises it:
    709fd8b4  1 out-of-envelope seat against 31 unworked — the E!-vs-x discrimination test
    9bd158dd  12 E! seats, and the ONLY schedules (with e6728aab) that have hours no assigned
              shift reaches, which is the only place the unstaffed-by-design bullet is testable
  Five of the seven accepted schedules have FULL envelope coverage on every date, so the
  unstaffed-by-design treatment cannot be seen on them at all. That is why this test needed two.
evidence: |
  BULLET 1 — Shift column agrees with the Agent Allocation group header.
    Confirmed on 709fd8b4's decisive row: Tekla Davitashvili, 2026-01-11, holding a 20:00 seat
    while assigned Weekend Opening 08:00-17:00. Header read "08:00-17:00" — NOT widened to 20:00
    to swallow the stray seat. That is the G-15-10 D4 disagreement demonstrably not happening, on
    the one row in the schedule where it could have shown.

  BULLET 2 — inline divergence marker on the Agent Schedule row.
    Verified on 9bd158dd by COUNT, against a figure computed from the API before it was viewed:
      predicted  12 amber "outside envelope"  /  22 grey "legal slot(s) unworked"  (34 rows)
      observed   "12 amber, 22 grey, matches the E! agents"
    Operator also confirmed the 12 amber rows are the SAME agents carrying E! in the allocation
    grid, which is the cross-check that matters — the badge and the per-cell mark agree.

  BULLET 3 — per-cell E! distinct from x. "clearly distinct" (709fd8b4), re-confirmed at density
    on 9bd158dd ("E! cells clear"). 709fd8b4 is the sharp case: ONE E! among 31 x cells, and both
    markers on the SAME ROW (Tekla — E! at 20:00, x at 10:00), so the two were compared side by
    side rather than across the grid.

  BULLET 4 — hours no template reaches render muted under "unstaffed by design".
    "unstaffed hours render muted" on 9bd158dd, where 2026-01-10 and 2026-01-11 both leave 08:00,
    09:00 and 20:00 unreached (that day's only groups are Weekend Early 10:00-19:00, Weekend Flex
    10:00-20:00 and Weekend Late 11:00-20:00 — nothing before 10:00, nothing past 20:00).
    NOTE THE HARD CASE THIS COVERED: those columns are not empty. Every agent seated in them is
    out of envelope by construction, so an "unstaffed by design" column carries E! cells beneath
    it — 3 at 08:00, 2 at 09:00, 1 at 20:00 on 01-11. The rendering held up with that combination
    present, which occurs in no other schedule on the desk.

  BULLET 5 — legend legible. "legend readable".
divergence_marker_fix_verified_here: |
  This test doubles as the field verification of the 2026-09-02 divergence-marker fix (commit
  8f61493, recorded under test 19's finding_surfaced_early / fixed_2026_09_02 above). The fix had
  passed `npm run build` but had NO automated coverage — this project has no frontend test
  framework — so bullet 2's count was the only verification available to it.
  The 12/22 split is exactly the fix's intended behaviour: before it, all 34 rows carried an amber
  ⚠, including 22 that describe correct bounded-slack behaviour. Operator's confirmation that the
  12 amber rows match the E! agents closes it: the warning now marks defects and nothing else.
scope_note: |
  The tooltip half of bullet 5 was not separately reported, and deliberately was not pressed —
  known_caveat WR-02 records that the "unstaffed by design" tooltip reads only that day's assigned
  shifts rather than the desk's full live library, so its literal wording is known-inaccurate in
  exactly the edge case 9bd158dd presents. The visual treatment was the thing to judge, and it was.
amended: 2026-08-31 — see expectation_correction below. The test's ORIGINAL wording is preserved above; read the correction before judging the `x` marker.
expectation_correction: |
  This test was written BEFORE bounded envelope slack shipped (81117e3, V44), and one of its
  premises expired at that commit.

  WHAT CHANGED: under the old zero-slack rule an agent's legal in-envelope slots equalled their
  contracted slots exactly, so a surrendered legal slot (`x`) meant the agent could not have
  reached contracted hours — it was a DEFECT SIGNAL. With bounded slack (default 1 slot) an agent
  holding a 9h-net envelope against 8 contracted hours has one legal slot they are SUPPOSED to
  leave unworked. Some `x` cells are now the system working correctly.

  HOW TO JUDGE IT NOW: `x` is a rendering question, not a correctness question. Check that `x` is
  drawn, is legible, and is visually distinct from `E!`. Do NOT treat the presence of `x` cells —
  or their count — as a failure. `E!` (a seat OUTSIDE the envelope) remains a true defect signal
  and is the one to count.

  The other four bullets are unaffected by this correction.
source: 15-VERIFICATION.md human_verification
why_human: |
  This project has no frontend test framework (Phase 13 P-11, a standing constraint). 15-12-SUMMARY.md
  marks all three of its rendering must-haves `human_judgment: true` for that reason. `npm run build`
  proves the code compiles and bundles — not that the marks are legible or correctly positioned.
known_caveat: |
  Code review WR-02: the "unstaffed by design" tooltip reflects only that day's assigned shifts,
  not the desk's full live shift library, so its literal wording can be inaccurate in an edge case.
  Known and unfixed — judge the visual treatment, not the tooltip's precise claim.
finding_surfaced_early_2026_09_02: |
  NOT YET AN OPERATOR VERDICT — found by analysis while the operator was answering test 13, and
  recorded here rather than filed as a gap so they can judge it. It bears directly on this test's
  SECOND bullet, which asks that the inline divergence marker appear "when the actual seats fall
  outside the assigned envelope".

  IT ALSO FIRES WHEN ZERO SEATS FALL OUTSIDE. On accepted schedule 7cc71bf5, 19 of 138 agent-days
  carry the ⚠ marker, and they split:

      3  genuine   ⚠ 1 outside envelope, 1 unworked   (Mon 01-05, Morning 09:00-18:00, 08:00 seat)
     16  spurious  ⚠ 0 outside envelope, 1 unworked   (all Weekend Flex 10:00-20:00)

  So 84% of the warnings on this schedule describe correct behaviour, and the 3 that matter are
  buried among them.

  IT IS STRUCTURAL, NOT OCCASIONAL. Weekend Flex is a 10-hour envelope carrying a 60m band: 10
  slots - 1 break = 9 legal slots, against 8 contracted hours. Every agent holding it leaves
  exactly one legal slot unworked BY CONSTRUCTION, so every Weekend Flex agent-day is flagged, on
  every date, forever. Verified: all 16 sit on that one template, and the example rows all read
  held=8, unworked=[10:00].

  THE MECHANISM — the two features disagree because they were built at different times:
    ScheduleOutputService:727  divergence is null only if BOTH lists are empty, so a pure-slack
                               agent-day yields a non-null divergence.
    ScheduleResults.tsx:922    renders ⚠ when `outOfEnvelopeSeats.length > 0 ||
                               unworkedLegalSlots.length > 0` — the OR is what admits the 16.

  This is the SAME EXPIRY this test already documents for the `x` marker in expectation_correction
  above: before bounded envelope slack (81117e3, V44, default 1 slot) an unworked legal slot meant
  the agent could not reach contracted hours and WAS a defect signal. V44 made it normal. The `x`
  cell's meaning was corrected in this file; the ⚠ row marker was not, and it is the louder of the
  two. Consequence worth weighing: a marker that cries wolf on 16 correct rows trains an operator
  to ignore the 3 real ones — it degrades exactly the signal the phase's headline guarantee needs.

  SUGGESTED SHAPE OF A FIX (not planned, not agreed): gate the ⚠ on `outOfEnvelopeSeats.length > 0`
  alone and surface unworked legal slots without warning styling, since the per-cell `x` treatment
  already communicates them. That keeps both facts visible while restoring the marker as a defect
  signal. Whether unworked slots deserve any row-level indicator at all is an operator call.
fixed_2026_09_02: |
  FIXED on operator instruction ("fix the divergence marker"), same session.

  SCOPE — frontend only, ScheduleResults.tsx. The backend was checked first and is NOT at fault:
  `ScheduleOutputService.publishDivergenceWarning` already gates the schedule-level headline on
  `if (outOfEnvelopeSeatCount > 0)` (:278), so the "N agent-day seat(s) fall outside..." line only
  publishes on real violations. Leaving the API contract alone also leaves
  `ScheduleOutputServiceShiftReportingTest`'s three guards (test 20's fix) untouched.

  THE CHANGE — the single `||` at :922 became two separate, differently-styled indicators:
    - out-of-envelope seats > 0  -> amber ⚠ badge, unchanged styling, now appending the unworked
      count only when there is one ("⚠ 1 outside envelope, 1 unworked").
    - out-of-envelope seats == 0 and unworked > 0 -> NEUTRAL grey badge, no ⚠, reading
      "1 legal slot unworked", with a tooltip stating the slack rule explicitly.

  Kept rather than deleted because the Agent Schedule tab has no per-cell × grid — unlike the
  Agent Allocation view, it is the only place that row's unworked slot is visible at all, so
  removing the indicator outright would have lost information rather than de-escalated it.

  EFFECT on the measured case: the 16 Weekend Flex false positives lose the ⚠ and read as neutral
  fact; the 3 genuine Mon-01-05 violations keep it. Warning markers on 7cc71bf5 go 19 -> 3, and
  every remaining one is real.

  VERIFIED: `npm run build` (tsc -b && vite build) passes. NOT verified by automated test — this
  project has no frontend test framework (Phase 13 P-11, standing constraint), which is the same
  reason test 19 exists as a human checkpoint. Confirm visually on the Agent Schedule tab: the
  Weekend Flex rows on 2026-01-10/11 should show a grey "1 legal slot unworked", and only Augusto
  Correia / Cindy Rodriguez / Juan Diego Dieguez on 2026-01-05 should still show amber ⚠.

### 20. Warning list does not grow while a schedule is running

expected: |
  Start a SHIFT-mode solve that produces an envelope divergence and leave the results page open
  while it is RUNNING. The schedule's warnings should NOT accumulate duplicate entries as the page
  polls (every 2s). The divergence information itself should be correct on every refresh.
result: pass
closed_by_operator_ruling_2026_09_02: |
  OPERATOR RULING ("20 is a pass"), given after the fix and its evidence were put to them. Recorded
  as a ruling, not a measurement, so nobody later reads it as a fresh field observation.

  WHAT IS ESTABLISHED: the append-per-poll cause was located precisely (ScheduleOutputService:236
  on a READ path, against a Schedule handed back by reference from InMemoryScheduleStore), fixed
  idempotently, and covered by three guard tests — two of which were verified to FAIL against the
  pre-fix code before being accepted.

  WHAT IS NOT: no post-fix observation on a RUNNING shift desk. The only field evidence after the
  fix is weak by construction — warnings held flat across 5 polls during test 14, but that was a
  SLOT desk, where publishDivergenceWarning has nothing to publish and so never exercises the
  fixed path. The operator's own original sighting was pre-fix.
previously_reported: "the capacity warning keeps growing please stop that"
severity_at_time_of_issue: minor
tested_against: dev (https://d2bbtcc80peap7.cloudfront.net) on 5b7bd15, live operator observation
confirms: 15-REVIEW.md CR-04 — predicted in code review, now observed in the field by the operator.
finding: |
  CONFIRMED. The operator watched the warnings panel grow while a schedule was open. Two
  different warning producers were disentangled to locate it:

    - SolverService:819 `schedule.setWarnings(warnings)` — the CAPACITY warning
      ("Demand (N FTE-slots) exceeds supply..."). SET, not appended, once per solve. Does NOT grow.
      This is the warning the operator NAMED, but it is not the one multiplying.

    - ScheduleOutputService:236 `schedule.getWarnings().add(...)` — the envelope-divergence
      headline. This is the growth. It sits on a READ path: ScheduleService:155 calls
      buildAgentSchedule on every GET of the schedule results, the results page polls that
      endpoint ~2s while RUNNING, and InMemoryScheduleStore.get returns the SAME Schedule
      instance by reference rather than a copy. One more identical line per refresh, unbounded.

  WHY THE SUITE MISSED IT: `grep -rn "getWarnings" src/test/java` returned ZERO hits before this
  fix. Not one test in 79 classes asserted on the warnings list, so nothing could have caught an
  append-per-call on a shared object.
fix: |
  ScheduleOutputService — extracted `publishDivergenceWarning`, which is IDEMPOTENT:
  remove-then-add under a stable marker substring, rather than add-if-absent.

  Remove-then-add specifically, for two reasons dedup-on-equality would miss:

    1. the two counts legitimately CHANGE between polls while a solve is still running, so
       equality dedup would append a new distinct string per changed count — still unbounded,
       just less obvious;

    2. a divergence that CLEARS must retract its stale line; add-if-absent would leave a false
       warning on screen permanently.
  Synchronised on the list: concurrent polls would otherwise interleave removeIf's iteration with
  another thread's add on a plain ArrayList. The pre-existing bare add was already unsafe;
  iterating makes that latent race worth closing.
guard_added: |
  ScheduleOutputServiceShiftReportingTest — three tests, verified to FAIL against the pre-fix
  code before being accepted (2 of 3 fail; the third is a non-regression guard on the new
  removeIf, so it correctly passes both ways):

    - buildAgentSchedule_repeatedPolls_doNotGrowTheWarningsList (10 polls -> exactly 1 headline)
    - buildAgentSchedule_divergenceThatClears_removesItsStaleWarning
    - buildAgentSchedule_preservesWarningsItDoesNotOwn (the solver's capacity advisory survives)

source: 15-REVIEW.md CR-04 (verifier re-judged from blocker to warning)
why_human: |
  `ScheduleOutputService.buildAgentSchedule` appends to the shared live `Schedule` object's warnings
  list on every call with no dedup guard, and `InMemoryScheduleStore.get()` returns the same object
  reference rather than a copy. Confirmed present in code by both the reviewer and the verifier, but
  unfixed — no phase success criterion depends on bounded warning-list behaviour, so it did not block
  verification. This test establishes how visible it actually is to an operator.

## Summary

total: 20
passed: 20
issues: 0
pending: 0
skipped: 3
blocked: 0

<!--
  COUNT CORRECTION 2026-09-02: this block briefly read `issues: 0` while test 20 still carried
  `result: issue`, and the figures did not sum to 20. Numbered tests 1-20: 18 passed, 1 issue
  (test 20), 1 blocked (test 18). `skipped: 3` counts the lettered entries 5b/14b/17b, which sit
  outside the numbered 20 — that is why passed+issues+blocked already totals 20 without them.

  COUNT UPDATE 2026-09-02 (later): test 18 closed by operator ruling ("no cloud is dev") — no
  separate production tier exists, and Test 1 already verified V40's fan-out against the one live
  environment. blocked 1 -> 0, passed 19 -> 20. Numbered tests now 20 passed, 0 issues, 0 blocked;
  `skipped: 3` still counts only the lettered entries 5b/14b/17b, outside the numbered 20.
-->

<!--
  passed : 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12
  issue  : 10 (major, was blocker — search plateau, all structural causes fixed)
  skipped: 5b (probe artefact bookkeeping, now closed by the delete control)
  blocked: 18 (production deploy) — SUPERSEDED, see COUNT UPDATE above; now passed
  pending: 13, 14, 15, 16, 17, 19

  TEST 3 CLOSED 2026-09-01 via operator-chosen route (b) — a throwaway DESK (created and deleted
  in-session, cascade disposal verified) carrying a one-band template in the Phase 14 shape. The
  invariant holds: the uncovered set tracks the single band's offset exactly, with zero-band and
  three-band controls proving the result is not vacuous. The literal "no Phase 14 desk MOVED"
  claim stays unobservable — UAT consumed its own baseline — and the pass is scoped accordingly.

  PRIOR NOTE (2026-08-31), still accurate for tests 11/13-17/19: none reached that round; the session ran end-to-end
           on test 10's mechanism instead, which produced 6 code fixes and 4 new advisories.
           Tests 11 and 19 are the highest-value of the remainder: 11 is the phase's core
           guarantee (currently violated 9 times on one date, see test 10), and 19 covers the
           envelope-divergence rendering, whose unworkedLegalSlots figure CHANGED MEANING this
           round — with bounded slack it is no longer a defect signal, and test 19 was written
           before that was true.
-->

## Gaps

- gap_id: G-15-10
  truth: "A real desk in shift-scheduled mode solves to a feasible schedule in acceptable time"
  status: resolved
  resolved_by: "UAT test 10 retest closed by operator ruling 2026-09-02 — outcome (a) reached on three
    accepted schedules (523c8785, b88cc98f, 6a10afa1: hard 0, feasible, 138/138 shifts). The
    residual on other runs is G-15-28 (weekend demand forecast), which stays OPEN and is not
    closed by this."
  resolved_at: 2026-09-02
  closed_by: [15-09, 15-10, 15-11, 15-12, 15-13]
  closure_evidence: |
    All four AND-gated root causes fixed and independently verified against HEAD (d490171) by
    15-VERIFICATION.md — not accepted from SUMMARY prose:
      D1 — SolverService.requireShiftEnvelopeSeatSupply refuses a solve whose in-envelope seat
           supply cannot meet contracted demand, naming date, shortfall and levers (plan 15-11).
           The zero-slack equality was KEPT deliberately and pinned by an invariant test.
      D2 — SolverService.expandMinimumStaffingSeats is now SchedulingMode-aware: no filler seat
           where no live band reaches, guaranteed seats where a band reaches but demand does not
           (plan 15-09). SLOT mode proven unchanged.
      D3 — follows from D1+D2: the cheapest-hard-violation arbitrage has no destination left.
      D4 — ScheduleOutputService reads the authoritative ShiftDescriptor/AgentShiftAssignment
           instead of redrawing the envelope around held seats; ShiftEnvelopeDivergence surfaces
           the breach; frontend renders it (plans 15-10, 15-12).
    ShiftDeskEndToEndRegressionTest solves a fixture built to the live defect's SHAPE through the
    real solverConfig.xml and asserts no completed solve carries residual envelope penalty (3/3).
    Full suite: 546 tests, 0 failures, 0 errors, 2 pre-existing skips.
  retest: "Test 10, against a dev deployment REDEPLOYED from current HEAD. The gap is closed in
    code; it is not closed in this record until the live desk that filed it passes."
  retest_outcome: |
    RETESTED 2026-08-27..2026-08-31 against dev, redeployed repeatedly through 8 commits.
    NOT CLOSED, but no longer the gap that was filed. Final state: hard -9, all nine on one date,
    all agents seated in their own break window, with every structural cause ruled out by data.

    The filed symptom is gone: no frozen solve, no agents dragged onto zero-demand hours, no
    breaks manufactured to fill gaps. The four AND-gated causes D1-D4 are fixed and verified live.

    What the retest ALSO found — and this is the part the closure evidence could not have
    anticipated — is that the gap-closure round had been verified against code that was never
    deployed. 17 source commits (~4,086 lines), the entire round, sat UNPUSHED while the file
    recorded dev as running the phase's final HEAD. Testing would have re-measured the defect and
    read as proof the fix failed. See the corrected WHERE TO TEST block.

    THREE further defects were then found, none of them in this diagnosis: validWeekdays never
    enforced in the solver, phantom seats from calendar-blind coverage (two sites, one still open),
    and an unbounded warnings list. All three were found by operator questions during UAT, not by
    review and not by the 580-test suite.
  residual_owner: "Search plateau, not modelling. See test 10 failed_experiment: the acceptor
    temperature is the wrong lever at ofHard(1) weights, and a real fix needs a different acceptor
    or a rescaled hard weight — both larger than a config line, and neither shippable without a
    live measurement, since the whole suite passed with the setting that regressed this desk 7x."
  original_status: failed
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

- gap_id: G-15-24
  truth: "Raising the over-allocation limit is NOT a safe remedy on a desk where unassigned seats are hard"
  status: resolved
  resolved_by: "Plan 15-18 Task 2 (c5baa55) -- requireShiftEnvelopeSeatSupply takes a nullable live
    ConstraintWeights parameter and withdraws the over-allocation-ceiling remedy, naming the
    consequence instead, whenever unassignedAssignmentWeight carries a nonzero hard component."
  resolved_evidence: |
    PART 1 OF THE FIX (advice-safety) IS DELIVERED AND TEST-PROVEN. Default/null weights emit
    exactly today's message, pinned by LITERAL EQUALITY (not substring):
    `ShiftEnvelopeSupplyGateTest#defaultWeightsMessageIsByteIdenticalToBeforeThisPlan` and
    `#nullWeightsFallBackToDefaultWording`. A hard `unassignedAssignmentWeight` withdraws the
    ceiling suggestion, names the consequence (every manufactured, unfillable seat is a hard
    violation at the desk's own configured weight, value named) and still reports the current
    percentage: `#hardUnassignedWeightWithdrawsCeilingRemedyAndNamesConsequence`.

    PART 2 (the destructiveness measurement) IS EXPLICITLY NOT RE-ESTABLISHED, per this gap's own
    fix field's basis requirement -- stated here rather than left implicit. The original -20,338
    measurement is retracted_claim-adjacent and confirmed CONFOUNDED (it changed
    overallocationHardLimitPct AND underallocationHardLimitPct in the same run; G-15-28 recorded a
    clean raise, measured separately, as beneficial on this desk). The fix instead rests on the
    structural, checkable mechanism alone: expandOverflowAssignments derives maxAgents as
    ceil(requiredFTEs * pct / 100), so raising the ceiling manufactures additional seats regardless
    of measurement, and a nonzero hard unassignedAssignmentWeight makes every one of those seats
    that no agent can fill a hard violation by construction -- reading that weight before advising
    is correct independent of how a controlled re-experiment would come out. This is the SAME basis
    this gap's own fix field asked for ("read the desk's LIVE weights, not
    ConstraintWeights.java's defaults"), delivered in code: 15-BENCHMARK.md's "Live Weights
    Discipline (G-15-24)" section (appended 2026-09-02) writes the same rule down, with the
    source-vs-live divergence table this gap's detail names.

    RE-RUN THIS SESSION (plan 15-19), not transcribed from the plan text:
    `./gradlew test --tests "com.wfm.service.ShiftEnvelopeSupplyGateTest"
    --tests "com.wfm.solver.ShiftEnvelopeSupplyInvariantTest"
    --tests "com.wfm.solver.ShiftDeskEndToEndRegressionTest"` -- 23/23 pass, 0 failures, 0 errors.
  severity: major
  found_by: edge-band experiment, 2026-08-31 (resumed session)
  retracted_claim: |
    THIS ENTRY ORIGINALLY CLAIMED the desk runs at 130% and that test 10's "seats are NOT scarce"
    pillar was therefore computed at the wrong ceiling. THAT CLAIM WAS WRONG and is withdrawn.
    130% is only the desk's STORED default; the baseline -9 run explicitly overrode it per-request
    with overallocationHardLimitPct=250, underallocationHardLimitPct=50 (read back off schedule
    e6728aab). Test 10's 250% arithmetic was correct for the run it described. No correction to
    the remaining_9 pillars follows from this, and none should be made on the strength of it.
  detail: |
    WHAT IS ACTUALLY TRUE, and it is a different and more dangerous fact.

    The live desk overrides ConstraintWeights, and two overrides invert the source-default
    reasoning that this file (and my own analysis) had been using:

        unassignedAssignmentWeight   source ofSoft(1000)  ->  LIVE ofHard(10000)
        contractedHoursUnderWeight   source ofHard(100)   ->  LIVE ofHard(10)
        minStaffingWeight            source ofSoft(1000)  ->  LIVE ofHard(10)

    Seat COUNT scales with overallocationHardLimitPct. On this desk every seat no agent can fill
    is worth 10,000 HARD. So raising the ceiling manufactures hard liability directly.

    MEASURED. Same period/library, ceiling 250->150 (underalloc 50->70):

        Unassigned assignment        2 violations   -20,000 hard   <-- dominates everything
        Contracted hours (under)    18 violations      -280
        Shift envelope compliance   49 violations       -49        <-- was 9 at the baseline
        Bulk under-allocation hard   2 violations         -9

    Envelope violations went 9 -> 49 and the total went -9 -> -20,338.

    THE GATE RECOMMENDS THIS. requireShiftEnvelopeSeatSupply's refusal text says "To fix it: raise
    the desk's over-allocation limit (currently N%)" as its FIRST suggestion. On a desk weighted
    like this one, following that advice is the single most destructive available action. The gate
    has no knowledge of unassignedAssignmentWeight and cannot know its own advice is unsafe.
  fix: "Two parts. (1) The refusal text must not recommend raising the over-allocation limit
    without checking unassignedAssignmentWeight — where unassigned seats are hard, that advice
    should be suppressed or inverted. (2) Any analysis of this desk must read the desk's LIVE
    weights, not ConstraintWeights.java defaults; they differ on at least five constraints."
  also_observed: |
    GET on an ACCEPTED schedule returns constraintViolations with "Shift envelope compliance"
    violationCount 1104 / penalty -1104 while the stored score reads hardScore -9. The two cannot
    both be right. 1104 is the desk's total staffed agent-hours, so the read path looks to be
    re-deriving violations against the CURRENT library rather than the accepted one. Same read-path
    family as CR-04 (test 20). Not chased this session.

- gap_id: G-15-28
  truth: "Weekend demand at the day's edges is forecast far below the roster actually working"
  status: open
  severity: major
  owner: operator ("ok I'll fix the weekend demand forecast", 2026-09-01)
  found_by: the 0-hard experiment, 2026-09-01
  detail: |
    THE ROOT CAUSE BEHIND MOST OF THIS SESSION'S SYMPTOMS, and the operator identified it
    independently before this entry was written.

    Live weekend forecast against a roster of 18-25 agents on those days:
      Sat 2026-01-10  NO demand row at all before 11:00, none at 20:00. Peak 11:00 = 44.
      Sun 2026-01-11  10:00 = 1 FTE, nothing at 08:00/09:00/20:00. Peak 11:00 = 32.

    `bulkOverallocationLimit` derives its ceiling as demand x overallocationHardLimitPct / 100, so
    a forecast of 1 FTE at Sunday 10:00 admits 2 agents at the desk's 250% setting. Weekend Opening
    (08:00-17:00) has zero slack, so EVERY holder must work 10:00 — five held it, two could sit
    there, three were forced out of envelope. Sunday 18:00 (demand 4, ceiling 10) forced the other
    three. Those six were the entire residual hard score.

    PROVEN BY EXPERIMENT. Raising overallocationHardLimitPct 250 -> 500 (which is purely a way of
    saying "trust the forecast less at the edges"), with everything else held constant, took the
    live desk to hard 0, FEASIBLE — the first feasible solve this desk has produced. Same library,
    same roster, same period. Nothing about the schedule changed except the ceiling the forecast
    implies.

    So the residual was never a solver defect. It was the forecast asserting that ~1 agent is
    needed at hours when the operator rosters ~20 and requires the desk staffed.
  fix: "Operator is correcting the weekend forecast. Once corrected, RE-RUN AT 250% — the 500%
    ceiling is a workaround for the bad data and should not be left in place, because it also
    disables the over-allocation guard everywhere else in the week."
  consequences_for_this_file: |
    Several earlier conclusions were reasoning around this data defect without naming it:
      - test 10's remaining_9 "seats are NOT scarce" pillar: seats were scarce, because the
        ceiling is derived from a forecast that understates the edges.
      - the Tuesday 08:00 residual and the Sunday 20:00 residual, both diagnosed as "a boundary
        hour reachable by exactly one template", are the same mechanism seen at other edges.
      - G-15-24's warning about raising the ceiling: that measurement is now known to be
        CONFOUNDED (underallocationHardLimitPct was changed from 50 to 70 in the same run). A
        clean raise, measured here, was beneficial rather than destructive.

- gap_id: G-15-29
  truth: "Solver weights must be compared by violation COUNT, and a single run is not evidence"
  status: resolved
  resolved_by: "15-BENCHMARK.md's new 'Solver Comparison Rule (G-15-29)' section (the documented
    rule this gap asked for), plan 15-14's SolverQualityGuardTest (the invariant-based guard the
    rule's own argument justifies), and plan 15-15's thesisProof test (the rule's argument reduced
    to a single deterministic assertion)"
  resolved_evidence: |
    THE RULE, WRITTEN DOWN. `15-BENCHMARK.md`'s new "Solver Comparison Rule (G-15-29)" section
    states, as five numbered items each carrying its own measured evidence, that violation COUNTS
    per constraint -- never raw hard scores -- are the only valid comparison whenever two runs
    differ in any constraint weight; that a single run is never evidence on this desk (byte-
    identical configuration produced `b88cc98f 0 hard FEASIBLE`, `60523b98 -20 hard NOT FEASIBLE`
    and `aaf17313 -20 hard NOT FEASIBLE`, three readings under the same config); that a recurring
    violation LOCATION (weekday 08:00, sole-routed to `Weekend Opening`) is stronger evidence than
    a recurring score; and that structural invariants beat scores as a pass/fail gate. This rule is
    printed inside `SolverQualityGuardTest`'s own failure report (`buildQualityReport`), so a red
    run surfaces it directly rather than assuming the reader already knows it.

    THE MECHANICAL DEMONSTRATION. `thesisProof_atZeroWeightTheViolationCountTableGoesBlind_
    butTheWalkerStillSeesTheSplit` (plan 15-15) reduces the rule's argument to one deterministic
    assertion on one fixed corrupted schedule (single solve, no re-solve): at the fixture's live
    weight, `hardMatchCountsByConstraint` reports the corrupted constraint (`Shift work
    contiguity`) with count 1; at `ConstraintWeights.shiftWorkContiguityWeight =
    HardSoftScore.ofHard(0)`, the SAME key is absent from that map entirely; `findSplitShifts`
    (the independent structural walker, sharing no code with the score director) returns the
    identical single hole in both readings. Score-derived evidence is provably a function of the
    weights AND the schedule; the structural walker is provably a function of the schedule alone
    -- which is why `SolverQualityGuardTest`'s pass/fail gate (plan 15-14) is invariant-based, with
    violation counts used only as a median-of-five trip-wire, never a single run's raw score.

    THE UNDERLYING GUARD AND ITS BASELINE (full detail in `15-BENCHMARK.md`'s "Solver Quality
    Guard (G-15-22)" section and G-15-22's own `resolved_evidence` in this file): five seeded,
    step-count-terminated solves through the shipped `solverConfig.xml`; `totalHardViolations` per
    seed 3, 1, 2, 0, 1, median 1.0, ceiling 3, committed before the number was read; INV-1/2/3 hold
    on every seed; five red-proofs (plan 15-15) demonstrate each walker can go red on exactly its
    own injected defect. `./gradlew test --tests "com.wfm.solver.SolverQualityGuardTest"` -- 10/10
    pass, 0 failures, 0 errors.

    POINTER, Test 10 `session_2026_09_01`. That verdict block (recorded above in this file)
    recommends restating Test 10's criterion (a) ("reaches 0 hard") against the same operator
    invariants (0 splits, 0 edge breaks, every edge hour non-zero) plus a violation-count ceiling,
    because on this desk's search "reaches 0 hard" is a coin-flip property of one run, not a
    property of the build. This guard now supplies exactly that measurement, in automated form.
    The restatement of Test 10 itself remains the operator's call and is NOT applied by this plan.
  severity: major
  found_by: the contiguity weight-tuning session, 2026-09-01
  detail: |
    ~12 live solves were run on Stubhub (EN) to tune the new contiguity constraint against
    shiftEnvelopeCompliance. Most of that effort produced no usable signal, for two reasons that
    should be written down before anyone repeats them.

    1. HARD SCORES ARE NOT COMPARABLE ACROSS WEIGHT CHANGES. Run U scored -4 with envelope at
       weight 1; run V scored -30 with envelope at 10. V looks five times worse and is actually
       BETTER: 3 envelope violations against U's 4. Only violation counts compare.

    2. THIS DESK'S SEARCH IS INTERMITTENT AT THE FEASIBILITY BOUNDARY. Measured spread across
       runs that all met every operator requirement (0 splits, 0 edge breaks, full coverage):
           envelope violations: 2, 3, 3, 4, 6, 8
       with no consistent ordering by configuration. Two runs of ONE configuration earlier in the
       session gave 0 and -1. Differences of 2-8 on a 1104-seat schedule are noise.

    THE COST OF NOT KNOWING THIS: a forecast edit by the operator was blamed for a collapse that
    was actually caused by a weight change, and the operator was asked to delete demand rows
    twice on the strength of uncontrolled comparisons. Run R -- the first properly controlled
    comparison, changing only the weights -- exonerated the data immediately. That control should
    have come first.

    This is G-15-22 restated with evidence. It is not a nice-to-have.

    CONFIRMED AGAIN 2026-09-01, at the current commit, on two runs rather than twelve. Runs 1 and 2
    of Test 10's session_2026_09_01 used BYTE-IDENTICAL configuration — verified field by field
    through the API, not assumed — twenty minutes apart on the same build and the same library:
        run 1  b88cc98f    0 hard   FEASIBLE
        run 2  60523b98  -20 hard   NOT FEASIBLE  (2 envelope violations)
    Neither of the two people looking at it could tell noise from regression without hand-computing
    the operator invariants from the API response. That is the whole gap, reproduced cheaply.

    AND A DESIGN CONSTRAINT THE FIX MUST RESPECT, learned from those same runs: a benchmark
    asserting a HARD-SCORE CEILING WILL ITSELF BE FLAKY, for exactly the reason run 2 was -20. What
    was stable across all three of 2026-09-01's runs — including the -120 one at the wrong
    over-allocation limit — was the operator-requirement set: 0 split shifts, 0 edge breaks, every
    edge hour non-zero on every day. Those are the invariants worth asserting. A score ceiling, if
    used at all, belongs on violation COUNTS and over several runs, never on one run's raw score.
  fix: "A benchmark-shaped test that solves a realistic fixture through the real solverConfig.xml
    and asserts the STABLE operator invariants (0 split shifts, 0 edge breaks, every edge hour
    non-zero) rather than a single run's hard score; any score-based assertion must be on violation
    counts across repeated runs. Plus a documented rule that solver comparisons report violation
    counts per constraint, never raw hard scores, whenever weights differ between runs."

- gap_id: G-15-30
  truth: "Contiguity and envelope compliance must be weighted on a comparable scale"
  status: resolved
  severity: major
  found_by: the contiguity weight-tuning session, 2026-09-01
  detail: |
    shiftWorkContiguity shipped at ofHard(100) (V45's default) against shiftEnvelopeCompliance's
    ofHard(1) -- a 100:1 ratio under which the solver will rationally breach the envelope up to 99
    times to avoid one split shift. It did exactly that: 52 envelope violations, 43 on one date,
    with spans like 08:00-20:00 that no template provides.

    Levelling both to 100 removed the arbitrage but made the model too rigid; 10/10 is the setting
    that holds every operator requirement with the smallest residual.

    SETTINGS NOW LIVE ON THE DESK, and the recommended default:
        shiftEnvelopeComplianceWeight  10
        shiftWorkContiguityWeight      10
        unassignedAssignmentWeight  10000   (feasibility REQUIRES empty seats be intolerable;
                                             lowering it to 1000 told the solver 3-8 empty seats
                                             were acceptable and coverage collapsed)
        bandCapacityWeight              1
    Solve: overallocation 500, underallocation 50, shiftEnvelopeSlackSlots 0, 900s.
  note: "V45's ofHard(100) default should be changed to ofHard(10) so a fresh desk does not
    inherit the arbitrage. Not done -- it needs a migration and the live desk is already set."

- gap_id: G-15-27
  truth: "An agent's working hours must be contiguous apart from their break"
  status: resolved
  resolved_by: "a02d150 — V45 + the shiftWorkContiguity hard constraint, deployed and live at a320ca7"
  resolved_evidence: |
    The `fix:` block below specified a hard constraint in SHIFT mode forbidding an unworked legal
    slot strictly between two worked slots unless it is the break. That is what a02d150 shipped
    (10 guard tests), and V46 later corrected its weight from 100 to 10 (G-15-30).

    MEASURED 2026-09-01 on three independent live solves at the current commit, computed from the
    API response rather than read off the UI:
        b88cc98f    0 hard    0 split shifts / 138 agent-days
        60523b98  -20 hard    0 split shifts / 138 agent-days
        2eeb2ca9 -120 hard    0 split shifts / 138 agent-days
    Zero splits held even on the -120 run at the wrong over-allocation limit — the property is
    enforced by the constraint, not by a lucky search. Compare the 24-of-138 (17%) splits measured
    on 709fd8b4 when this gap was filed.
  severity: blocker
  found_by: operator, 2026-09-01 — "it breaks the need for an agent to work contiguous hours. That's a no-no."
  regression_introduced_by: "81117e3 / V44 bounded envelope slack — this phase's own gap-closure fix"
  detail: |
    MEASURED on the accepted schedule 709fd8b4: 24 of 138 agent-days (17%) contain a NON-BREAK
    hole in the working day — a split shift. Some contain two, fragmenting the day into three
    pieces, e.g. Darien Speranza 2026-01-10 on 10:00-20:00: works 10,11 / GAP 12 / 13 / BREAK 14 /
    15,16,17,18,19.

    THE CAUSE IS SLACK, and the correlation is one-for-one:

        shift          net hours          agent-days   split
        10:00-20:00    9.0  SLACK = 1         30         23      <-- 77%
        10:00-19:00    8.0  zero slack        12          0
        11:00-20:00    8.0  zero slack        30          0
        12:00-21:00    8.0  zero slack        40          0
        09:00-18:00    8.0  zero slack        10          0
        08:00-17:00    8.0  zero slack        16          1

    Every split is on the ONE template whose net hours exceed contracted hours. Zero-slack
    templates cannot split, because the agent must occupy 100% of their legal slots.

    WHY NOTHING CAUGHT IT. In SHIFT mode all four break-geometry constraints are gated off
    (ScheduleConstraintProvider, `ifExists cfg.schedulingMode() != SHIFT`), and NO constraint
    requires an agent's worked slots to be contiguous. Under the ORIGINAL zero-slack rule that did
    not matter: legal slots equalled contracted slots exactly, so contiguity was guaranteed BY
    ACCIDENT. V44 relaxed the equality to fix D1 and thereby removed a guarantee nobody had
    written down, in a mode where nothing else enforces it.

    This is the second time this phase has been bitten by the same shape: a property held
    incidentally by a constraint, then lost when that constraint was relaxed for an unrelated
    reason. The first was G-15-10's zero-slack identity itself.

    UI FRAMING MAKES IT WORSE. The hole surfaces as "Legal slot left unworked", which after V44
    reads as benign — test 19's own expectation was amended this session to say exactly that. That
    amendment is now known to be INCOMPLETE: an unworked legal slot at the envelope BOUNDARY is
    benign (a shorter contiguous day); an unworked legal slot in the INTERIOR is a split shift and
    is not acceptable. The marker cannot be judged without knowing which it is.
  fix: |
    PROPER FIX (code): a hard constraint in SHIFT mode that an agent-day's worked slots form at
    most two contiguous runs separated only by the assigned break — equivalently, no unworked
    legal slot may lie strictly between two worked slots unless it is the break. Slack should be
    spendable only at the envelope boundary (late start / early finish), never in the interior.

    IMMEDIATE OPERATOR LEVER, no code, no data change: `shiftEnvelopeSlackSlots: 0` is exposed on
    SolveRequest.java:25. Setting it to 0 restores contiguity by construction. COST: it also makes
    Weekend Flex (net 9.0) ineligible for 8h agents entirely, and 30 agent-days currently sit on
    it — they would redistribute across Opening/Early/Late/Closing. Whether that is acceptable is
    an operator call, and it should be MEASURED before being recommended.
  test_gap: "No test asserts contiguity in SHIFT mode. Add one before fixing, so the fix is
    guarded — this is the same omission that let the acceptor regression ship green (G-15-22)."

- gap_id: G-15-26
  truth: "An unsupported HTTP method must return 405, not 500 INTERNAL_ERROR"
  status: resolved
  resolved_by: "Plan 15-16 Task 3 -- test(15-16) 7eb77cf (RED) then feat(15-16) 614f8f0 (GREEN):
    an @ExceptionHandler for HttpRequestMethodNotSupportedException in GlobalExceptionHandler,
    placed above the catch-all"
  resolved_evidence: |
    THE FIX MATCHES `fix_actual` EXACTLY. `GlobalExceptionHandler.handleMethodNotSupported` returns
    405 METHOD_NOT_ALLOWED through the existing `buildResponse`-shaped `ErrorResponse`, with an
    `Allow` header built as ONE comma-joined value from `ex.getSupportedMethods()` (the server's own
    knowledge of the endpoint) -- not one header entry per method, since `HttpHeaders.getAllow()`
    only reads the first `Allow` value and tokenizes it, which a naive varargs `.header(ALLOW,
    array)` call would have silently truncated to the first method only. The message names the
    supported methods and never echoes `ex.getMethod()` (the client-supplied verb), following this
    file's own `handleTypeMismatch` T-13-25/26 precedent named in this gap's `detail_actual`.

    THE EMPTY-SUPPORTED-METHODS CASE IS COVERED, not assumed benign:
    `handleMethodNotSupported_emptySupportedMethods_stillReturns405WithoutMalformedAllowHeader`
    constructs the exception with zero supported methods and asserts 405 with no malformed
    (blank-valued) `Allow` header entry.

    PRE-EXISTING MAPPINGS PROVEN UNDISTURBED. `preExistingMappings_stillReturnTheirOriginalStatuses`
    was broadened (not duplicated) to also cover `handleNotFound`/`handleConflict` alongside the
    pre-existing `handleIllegalArgument`/`handleUncaught` checks -- the catch-all still returns 500
    with its fixed, non-leaking string for a genuine unhandled exception, exactly as this gap's
    `detail_actual` and the plan's `<behavior>` block required.

    `./gradlew test --tests "com.wfm.controller.GlobalExceptionHandlerTest"` -- 5/5 pass, 0
    failures, 0 errors (2 pre-existing + 3 new).
  severity: minor
  found_by: edge-band experiment, 2026-08-31/09-01 (resumed session)
  RETRACTED_ORIGINAL_CLAIM: |
    THIS ENTRY ORIGINALLY READ "A completed solve must always be clearable" at severity BLOCKER,
    and asserted the dev desk was WEDGED with no API route out — that a COMPLETED schedule living
    in InMemoryScheduleStore but not the DB permanently blocked new solves, "reproduced
    deterministically" across two schedules and a task replacement.

    ALL OF THAT WAS WRONG, and it was my error, not a product defect. accept/reject/stop are
    @PutMapping (ScheduleController:63, :69, :77); I was calling them with POST. Spring answered
    HttpRequestMethodNotSupportedException, which the catch-all mapped to 500 INTERNAL_ERROR, and
    I read the 500 as a broken lifecycle. The CloudWatch log settled it in one line:
      "HttpRequestMethodNotSupportedException: Request method 'POST' is not supported"
    Issued correctly as `PUT /schedules/{id}/accept?version=0`, accept returned 200 immediately.

    CONSEQUENCES OF THE BAD DIAGNOSIS, recorded so the next reader can discount them:
      - An ECS force-new-deployment was run to "recover" a desk that was never stuck.
      - A deploy re-run was attempted for the same reason (it failed on ECR tag immutability —
        that finding is real and independent, see below).
      - The "one solve per task lifetime" claim was an artefact of my using POST every time.
    The DELETE 404 is ALSO not a defect: a COMPLETED schedule is not yet persisted, so the
    repository lookup correctly misses. Nothing about the store/DB split is broken.
  detail_actual: |
    WHAT REMAINS, and it is much smaller. GlobalExceptionHandler has no handler for
    HttpRequestMethodNotSupportedException, so its catch-all (:105) returns
    500 / "An unexpected error occurred" for what is a plain 405. That is what made a trivial
    client mistake look like a server fault and cost this session a great deal of time.
  fix_actual: "Add an @ExceptionHandler for HttpRequestMethodNotSupportedException returning 405
    METHOD_NOT_ALLOWED with the supported methods, so a wrong verb is self-diagnosing."
  unrelated_real_finding: |
    ECR TAG IMMUTABILITY. `gh run rerun` on a deploy can NEVER succeed: the image is tagged with
    the commit SHA and ECR refuses to overwrite an existing tag —
    "tag invalid: The image tag '<sha>' already exists ... and cannot be overwritten because the
    tag is immutable." Re-running a deploy to restart the service is not available; a redeploy
    needs a commit touching something outside `.planning/**`. The test gate and frontend deploy
    both passed in that run, so nothing regressed.
  superseded_detail: |
    Schedule bf3e533f-a9b4-411f-b0a1-6a5987c86d4a reached status COMPLETED (hard -20338). It is
    returned by GET /schedules, and it gates new solves — POST /solve refuses with "A schedule
    already exists for this desk. Stop it (if running) and accept or reject it before starting a
    new solve." But every route the message names is broken for it:

        POST /{id}/stop     -> 500 INTERNAL_ERROR
        POST /{id}/reject   -> 500 INTERNAL_ERROR
        POST /{id}/accept   -> 500 INTERNAL_ERROR
        DELETE /{id}        -> 404 NOT_FOUND ("Schedule not found")

    The 404-vs-500 split localises it. accept/reject read `inMemoryStore.get(scheduleId)`
    (ScheduleService:230, :406) and DO find the row — they fail later in the method. deleteSchedule
    reads `scheduleRepository.findByIdAndTenantIdAndDeskId` (:382) and does NOT find it. So the
    schedule exists in the in-memory store and NOT in the database, and the only endpoint that
    could clear it is the one looking in the wrong place.

    Net effect: a solve that completes without being persisted permanently blocks the desk. There
    is no API route out. Recovery requires restarting the service to clear InMemoryScheduleStore.
  reproduced_deterministically: |
    CONFIRMED TWICE, on two different schedules, either side of a task replacement. After the task
    was replaced (which cleared the first wedge), a clean solve was run to completion — Run A,
    f1f82a8f, COMPLETED at hard -10 — and the desk wedged again IMMEDIATELY on the same three
    failures: accept 500, reject 500, DELETE 404.

    So this is not a corrupted-schedule edge case. The desk can run EXACTLY ONE solve per task
    lifetime, then locks until ECS replaces the task. Any UAT session doing more than one solve
    will hit it, which is why the A/B in this session could not be completed.
  severity_note: |
    Escalated from the initial reading. Two ACCEPTED schedules for the SAME desk and SAME period
    (2026-01-05..11) already exist, from before the current task. A leading hypothesis for the 500
    is that accept tries to persist a third schedule over that pair and trips a DB constraint,
    with GlobalExceptionHandler's catch-all (:105) hiding the real exception. Worth checking
    whether accept ever succeeded on a desk that already had an accepted schedule for the period.
  recovery: "aws ecs update-service --cluster wfm-service-dev --service wfm-service
    --region eu-west-2 --force-new-deployment  (rolls the task, clears the in-memory store, no
    code change). NOTE: `gh run rerun` on the last deploy CANNOT work — ECR tags are immutable and
    the image is tagged with the commit SHA, so the push step always fails with `tag invalid`."
  fix: "deleteSchedule must fall back to the in-memory store when the DB lookup misses, and the
    500s on stop/accept/reject need their real exception surfaced (currently swallowed into
    INTERNAL_ERROR by GlobalExceptionHandler's catch-all at :105). Independently: the
    active-schedule guard should not treat an unpersisted COMPLETED schedule as blocking."
  note: "Related to CR-04's finding that InMemoryScheduleStore.get returns the live object by
    reference. The store's relationship to the DB is under-specified in more than one place."

- gap_id: G-15-25
  truth: "The seat-supply gate must respond to band composition, not just to the union of envelopes"
  status: resolved
  resolved_by: "Plan 15-20 Task 1 (2ef0252) -- SolverService.forcedAgentDaysByTimeslotId, a new
    per-agent-day forced-occupancy count computed against each agent-day's OWN
    getEligibleShiftBandPairs(), never a desk-wide anyMatch union. An agent-day is forced at a
    covered timeslot when EVERY one of its eligible pairs both covers it and has zero slack for
    that agent-day; requireShiftEnvelopeSeatSupply refuses when the forced count at any covered
    timeslot exceeds the seats there, accumulated alongside the pre-existing day-wide sum (never
    replacing it). This is R2 from plan 15-19's analysis, promoted from test source into
    production exactly as recommended."
  resolved_evidence: |
    THE BAND-COMPOSITION FIGURES, LEADING (the exact experiment that filed this gap). On a
    saturated-union library -- one 9h envelope, 3 single-band pairs with distinct break offsets
    (11-12, 12-13, 13-14) -- the shipped forced-occupancy count at the boundary hour 08:00 is 1
    (the agent's every eligible pair covers it, zero slack). Adding two edge bands to the SAME
    envelope (breaks 08-09, 16-17 -- 5 bands total) changes that figure to 0, while the desk-wide
    union figure (day-wide demand=8 supply=9) stays BYTE-IDENTICAL across both runs -- two
    DIFFERENT numbers from two runs differing only in band composition, the direct inverse of the
    byte-identical live measurement that filed this gap (five break bands added at the envelope
    edges of three live weekend templates, both runs reaching "136" identically). Re-run this
    session: `SeatSupplyDistributionAnalysisTest#bandCompositionExperiment_shippedFigureChangesButUnionStaysSaturated`
    and `ShiftEnvelopeSupplyGateTest#bandCompositionChangesForcedCountButNotTheSaturatedUnion`,
    both pass.

    THE MODEL FIX, not merely a number fix, matching `fix` exactly: supply is computed against
    each agent-day's OWN eligible pairs, never a desk-wide `anyMatch` union. An agent-day whose
    every eligible pair covers a covered timeslot and carries zero slack is counted as forced
    there; the same agent-day, once its template gains a band whose break falls on that hour, is
    not -- exactly the band-composition sensitivity a desk-wide union structurally cannot have.

    NECESSARY CONDITION PRESERVED, MEASURED AGAINST THE CORPUS, NOT HOPED AWAY: zero false
    refusals across every KNOWN-SOLVES fixture in plan 15-19's labelled corpus (3 date-slices),
    asserted in `SeatSupplyDistributionAnalysisTest#ruleByFixtureTable_falseAndTrueRefusalCounts`.
    The distribution-blind fixture, which PASSED the gate in plan 15-19, is now REFUSED
    (`#distributionBlindFixture_shippedGateNowRefusesIt`), naming the 08:00 bottleneck, the 10
    forced agent-days and the 2 seats there.

    ALL THREE PRE-EXISTING GATE-CALLING TEST CLASSES PASS UNCHANGED: 25/25 across
    `ShiftEnvelopeSupplyGateTest` (14 pre-existing + 2 new gap-closure tests +
    3 weight-branching tests updated for the new check's correct interaction, per
    15-20-SUMMARY.md), `ShiftEnvelopeSupplyInvariantTest` (6/6) and `ShiftDeskEndToEndRegressionTest`
    (3/3) -- re-run this session.

    Full suite: `./gradlew test` -- **635 tests, 0 failures, 0 errors, 2 skipped**, against the
    632/0/0/2 baseline this executor was handed (the 3-test increase is exactly this plan's new
    tests: 2 in Task 1, 1 in Task 2; see 15-20-SUMMARY.md for the full accounting).
  severity: major
  found_by: edge-band experiment, 2026-08-31 (resumed session)
  related_to: G-15-21 (same method, same calendar-blind predicate)
  detail: |
    MEASURED, not inferred. Five break bands were added at the envelope edges of the three live
    weekend templates (Weekend Early +offset 0/480, Weekend Flex +offset 0/540, Weekend Late
    +offset 480), the solve was attempted, then the ORIGINAL bands were restored and the solve
    attempted again. Both runs produced BYTE-IDENTICAL gate output — same five dates, same
    figures, 2026-01-11 "reaches 136" in both.

    The cause is structural (SolverService.requireShiftEnvelopeSeatSupply, ~1184):

        coveredTimeslots = timeslots(date).filter(ts -> pairs.stream().anyMatch(p -> p.covers(ts)))
        librarySupplySlots = sum of existing seats over coveredTimeslots

    `anyMatch` is a UNION over the desk-wide pair list. Each band pair excludes only its own break
    hour, so a template with 3+ bands already unions to its FULL envelope. Adding further bands can
    never change the union once it is saturated — the gate cannot see band composition at all.

    Why that matters: the union models "some agent could work this hour", but the solver binds each
    agent to ONE pair. Two libraries with identical envelopes and completely different break
    structure — one solvable, one not — are indistinguishable to this gate. It is measuring the
    wrong quantity, not merely measuring it on the wrong calendar.
  fix: "Compute supply per-agent-day against each agent's OWN eligible pairs and take the
    achievable assignment, rather than unioning the desk-wide pair list. Fixing G-15-21's date
    filter alone will NOT fix this — the two defects are independent and share only the method."

- gap_id: G-15-31
  truth: "The seat-supply gate must compare supply where the shortfall bites, not as a day-wide total"
  status: resolved
  resolved_by: "Plan 15-20 Task 1 (2ef0252) implemented plan 15-19's own recommendation exactly:
    R2 (the forced-occupancy necessary condition) adopted as an ADDITIONAL per-timeslot blocking
    check alongside the existing day-wide sum (R0), never replacing it -- the identical shape
    §5's Recommendation names ('adopt R2 ... as an additional, per-timeslot blocking check
    alongside the existing day-wide sum'). Plan 15-20 Task 2 (4cc01b6) re-measured
    SeatSupplyDistributionAnalysisTest against the SHIPPED implementation rather than leaving it
    as a test-local copy (T-15-20-04)."
  resolved_evidence: |
    THE COMPARISON NOW SEES WITHIN-DAY DISTRIBUTION. requireShiftEnvelopeSeatSupply no longer
    relies solely on one day-wide sum-vs-sum comparison; it also refuses when the count of
    agent-days structurally forced onto any ONE covered timeslot exceeds the seats there --
    exactly the per-timeslot granularity `detail` names as missing ("Distribution within the day
    is invisible to it").

    THE ANALYSIS'S RECOMMENDATION WAS IMPLEMENTED IN FULL, not partially: R2 adopted additively
    (R0 kept, unchanged, per the recommendation's own "alongside", not "instead of"); the
    necessary-condition argument (§4) carried over unmodified into production (zero eligible
    pairs is not "forced" -- that is the distinct unassignable-row branch; a pair with slack does
    not force the agent, since contractedHoursOver/Under judge only the aggregate hours total).
    `advisoryOnThinTimeslotDoesNotBlock` was settled explicitly, not silently changed -- see
    `15-SEAT-SUPPLY-GATE-ANALYSIS.md` §8.5: it stays non-blocking, untouched, exactly as §6
    recommended.

    FALSE-REFUSAL COUNT: zero, against the SAME three-KNOWN-SOLVES corpus plan 15-19 measured
    (`SeatSupplyDistributionAnalysisTest#ruleByFixtureTable_falseAndTrueRefusalCounts`, asserted
    on the "Shipped gate" row -- the actual throwing production method, not R2's isolated
    diagnostic). TRUE REFUSAL: the distribution-blind fixture, which passed the gate in plan
    15-19 at 250%-scale headroom (day-wide supply 114 vs demand 80), is now refused
    (`#distributionBlindFixture_shippedGateNowRefusesIt`).

    NOT RESOLVED ON THE STRENGTH OF G-15-25's FIX ALONE, per this gap's own related_to caveat: the
    evidence above is G-15-31-specific (the per-timeslot comparison structurally seeing
    distribution), distinct from G-15-25's evidence (the per-agent-day model seeing band
    composition) even though one production check now serves both -- see
    `15-SEAT-SUPPLY-GATE-ANALYSIS.md` §8.1's key_links accounting for why one check can close two
    independent gaps without the gaps being the same gap.

    WHAT REMAINS UNSETTLED, NAMED RATHER THAN IMPLIED CLOSED (`15-SEAT-SUPPLY-GATE-ANALYSIS.md`
    §8.4): whether R0 becomes fully redundant once R2 is adopted was not checked (R0 is kept,
    unchanged, per the recommendation's own "additive" framing); R2's runtime cost at real desk
    scale (138 agent-days) was bounded structurally (threat T-15-20-02, O(agent-days x pairs x
    timeslots)) but not wall-clock-benchmarked; the 23 referenced-but-not-re-instantiated
    pre-existing fixtures were still not run against R0-R3 individually (they DO all still pass
    the shipped combined gate, confirmed this session: 25/25 across ShiftEnvelopeSupplyGateTest,
    ShiftEnvelopeSupplyInvariantTest and ShiftDeskEndToEndRegressionTest). None of these residual
    items contradicts or narrows the recommendation that WAS implemented; they are the corpus's
    own honestly-stated scope boundary (§7), carried forward rather than silently dropped.

    Full suite: `./gradlew test` -- **635 tests, 0 failures, 0 errors, 2 skipped**, against the
    632/0/0/2 baseline this executor was handed (the 3-test increase is exactly this plan's new
    tests: 2 in Task 1, 1 in Task 2; see 15-20-SUMMARY.md for the full accounting).
  severity: major
  found_by: operator run at 250% over-allocation, 2026-09-01 (see Test 10 session_2026_09_01 Run 3)
  related_to: >
    G-15-25 (same method, same line) and G-15-21 (same method). DISTINCT FROM BOTH and neither fix
    resolves it. G-15-21 says the supply NUMBER is wrong (calendar-blind, over-counts). G-15-25 says
    the supply MODEL is wrong (unions desk-wide pairs, blind to band composition). This gap says the
    COMPARISON is wrong: even given a perfectly correct per-date supply total, a sum-vs-sum test
    cannot see distribution within the day. Fix G-15-21 and G-15-25 in full and this still stands.
  detail: |
    MEASURED on a live run, not inferred. The operator solved at overallocationHardLimitPct 250
    (the form default) instead of the 500 that HANDOFF.md §3 records as load-bearing. The solve was
    NOT refused. It ran to -120 hard, NOT FEASIBLE, violating contracted-hours-under, envelope
    compliance AND contiguity — precisely the class of outcome the gate exists to pre-empt.

    The blocking check is one comparison (SolverService.requireShiftEnvelopeSeatSupply, ~1194):

        if (contractedSlots > librarySupplySlots) { refuse }

    Both sides are DAY TOTALS — contractedSlots sums every rostered agent's expected work slots for
    the date; librarySupplySlots sums seats across every covered timeslot. Distribution within the
    day is invisible to it.

    The run was not a near miss. Seats scale linearly with the limit (expandOverflowAssignments:
    maxAgents = ceil(requiredFTEs * pct / 100)), so halving 500 -> 250 halves supply, and even
    halved it dwarfs contracted demand on every date:

        date        contracted   supply@500   supply@250   headroom@250
        2026-01-05     144          530          268          +124
        2026-01-06     152          535          270          +118
        2026-01-08     144          510          259          +115
        2026-01-10     200          735          371          +171
        2026-01-11     144          520          264          +120

    ~1.8x contracted at 250%, ~3.6x at 500%. The gate is nowhere near firing in EITHER case, yet one
    solves to 0 hard and the other collapses. The entire difference lives in the dimension the
    comparison aggregates away.

    CAVEAT ON THESE FIGURES: they assume every timeslot is covered by some live pair, being derived
    from the staffing-requirements API rather than from the desk's actual envelope coverage. Under
    partial coverage both the gate's real supply and the figures above fall together, so the
    conclusion is unaffected at this margin — but do not quote them as exact. The seat arithmetic
    itself IS exact: this model reproduces both runs' live "tightest at HH:MM with N seat(s)"
    advisories byte for byte (25/30/15/15/25/5/5 at 500%, 13/15/8/8/13/3/3 at 250%).

    THE SHARPEST PART: the system COMPUTES the number that predicts the failure and declines to act
    on it. The tightest-hour advisory at ~1281 sees per-hour distribution exactly — it is what
    printed "tightest at 08:00-09:00 with 3 seat(s)" on the failing run — and it is non-blocking BY
    EXPLICIT DESIGN, pinned by a passing test named `advisoryOnThinTimeslotDoesNotBlock`
    (ShiftEnvelopeSupplyGateTest.java:405).
  fix: |
    NOT a mechanical change, and DO NOT simply make the tightest-hour advisory blocking. That test
    name is not an accident — advisory-only may well have been a deliberate call, and promoting a
    thin-hour warning to a refusal risks false refusals on desks that currently solve fine. Nobody
    has done that analysis; it is the actual work here.

    Read the 15-11 discussion before changing behaviour. The likely shape is a per-hour (or
    per-agent-day, per G-15-25's fix) achievable-assignment check rather than a day-wide sum, with
    the false-refusal rate measured against desks known to solve — but that is a design proposal,
    not a settled answer.
  operator_note: |
    Until this is fixed, over-allocation at 250% on this desk fails SILENTLY — no refusal, ~8
    minutes burned, three hard constraints violated, and nothing on screen names the limit as the
    cause. The 500% in HANDOFF.md §3 is load-bearing and the UI default is not it.
  plan_15_19_analysis: |
    THE ANALYSIS THIS GAP ASKED FOR IS DONE. Status stays OPEN, deliberately — a document existing
    is not a code change, and this entry is not marked resolved merely because one now exists.

    Full write-up: `15-SEAT-SUPPLY-GATE-ANALYSIS.md`. Executable evidence:
    `src/test/java/com/wfm/solver/SeatSupplyDistributionAnalysisTest.java`
    (`./gradlew test --tests "com.wfm.solver.SeatSupplyDistributionAnalysisTest"` -- 11/11 pass,
    re-run this session).

    FOUR CANDIDATE RULES EVALUATED against a small, honestly-sized corpus (4 date-slices across 3
    fixtures -- 1 KNOWN-COLLAPSES, 3 KNOWN-SOLVES; 23 further pre-existing fixtures referenced but
    not re-instantiated, named in the analysis's own §2.4/§7): R0 (the shipped day-wide sum, this
    gap's own control), R1 (the tightest-hour advisory promoted to blocking naively), R2 (a proven
    forced-occupancy necessary condition), R3 (R2 demoted to warn-only).

    R2 REFUSES THE COLLAPSING FIXTURE (a minimal, ten-agent-scale reproduction of this gap's own
    live shape -- total supply generous, one boundary hour reachable by a single zero-slack
    template, forcing every agent-day onto it) WITH ZERO MEASURED FALSE REFUSALS across every
    KNOWN-SOLVES fixture tried, and is proven a genuine necessary condition (not merely measured
    lucky) on a hand-built case where the forced set is known by construction.

    R1 IS MEASURED, NOT DISMISSED, PER THE fix FIELD'S OWN INSTRUCTION NOT TO SKIP IT: it
    false-refuses 3 of 4 KNOWN-SOLVES date-slices in this corpus (75%) -- the measured version of
    the exact risk this gap's fix field named, and the measured justification for
    `advisoryOnThinTimeslotDoesNotBlock` staying non-blocking (§6 of the analysis states this
    verdict explicitly).

    RECOMMENDATION (not applied by this plan): adopt R2 as an additional per-timeslot blocking check
    alongside the existing day-wide sum R0 -- for plan 15-20 to implement and close this gap
    against, with the false-refusal measurement already on record. `advisoryOnThinTimeslotDoesNotBlock`
    is untouched (`git diff` on `ShiftEnvelopeSupplyGateTest.java` shows the method unchanged) and
    still passes.

    NOT SETTLED BY THIS ANALYSIS, named in its own §7 rather than gestured at: the 23
    referenced-but-not-re-instantiated fixtures were not run against R1/R2/R3; multi-template desks
    beyond two templates and R2-with-mixed-slack configurations are untested; R2's interaction with
    G-15-25 (still open) is not evaluated; R2's runtime cost at real desk scale (138 agent-days) was
    not measured; the live-desk calibration is a cross-consistency check between two already-
    published advisory sequences, not an independent replication against a raw per-hour demand
    table (none was recorded anywhere in this file or HANDOFF.md for this desk).

- gap_id: G-15-32
  truth: "An accepted schedule's reported constraint violations must describe that schedule"
  status: resolved
  resolved_by: "Plan 15-16 Task 1 (579b090) -- ScheduleOutputService.buildConstraintViolations
    takes an explicit isAcceptedSnapshot parameter threaded from ScheduleService.getScheduleDetail's
    own fromDb local, replacing the null-weights proxy this gap's mechanism identified. The accepted
    path never calls solutionManager.explain and instead derives the report from the persisted
    snapshot via a new buildAcceptedConstraintViolations, reusing resolveShiftDescriptor and
    computeDivergence's coverage walk (factored into outOfEnvelopeAssignments, D-08 one-predicate
    discipline). Plan 15-16 Task 2 (e07d036) added the regression, constant-1104 pin, and red-proof
    this gap's own test_gap named."
  resolved_evidence: |
    NAMED-ROW PROOF REPRODUCED AND CLOSED. The exact shape this gap's `detail` recorded (Armaz
    Dugashvili, 2026-01-05, shift `Mid 11:00-20:00`, bandOffset 300 => break 16:00-17:00, held
    seats 11,12,13,14,15,17,18,19) is now a permanent regression test
    (`ScheduleOutputServiceShiftReportingTest.buildConstraintViolations_acceptedNamedRowShape_reportsNoEnvelopeViolation`
    and `ScheduleServiceShiftSnapshotTest.getScheduleDetail_acceptedNamedRowShape_feasibleTrueImpliesNoViolatedHardConstraints`).
    Before the fix: all eight seats reported as violations (the constant-1104 arithmetic, N*H for
    N agent-days x H legal seats). After the fix: zero violations, `violatedHardConstraints` empty,
    matching the schedule's own `feasible: true` and its already-correct `divergence: null`.

    THE CONSTANT-1104 ARITHMETIC IS NOW PINNED AS IMPOSSIBLE, not merely absent:
    `buildConstraintViolations_acceptedCleanMultiAgentDay_countIsZeroNeverTheStaffedSeatConstant`
    asserts the reported count for two clean agent-days (N=2, H=8) is explicitly NOT N*H (16), and
    is zero.

    THE READ-PATH INVARIANT THIS GAP'S OWN `test_gap` NAMED NOW EXISTS AND IS APPLIED EVERYWHERE:
    `assertFeasibleImpliesNoViolatedHardConstraints` (`feasible == true` implies
    `violatedHardConstraints` is empty) is asserted on every accepted-schedule and live-schedule
    case in `ScheduleServiceShiftSnapshotTest`, not only the two new dedicated cases.

    THE RED-PROOF (the accepted path can still go non-empty) is load-bearing and was verified
    manually before commit: relocating one seat outside its persisted envelope reports exactly one
    violation naming that agent and timeslot
    (`buildConstraintViolations_acceptedRedProof_oneRelocatedSeatReportsExactlyOneViolationNamingIt`,
    `getScheduleDetail_acceptedRedProof_oneOutOfEnvelopeSeatReportsExactlyOneNamedViolation`).
    Forcing the accepted path to return an unconditionally empty list failed both red-proofs;
    restoring the pre-fix unconditional `explain()` call failed the named-row/constant-1104
    regressions. Both reverted before committing -- confirmed via `git diff` showing no residual
    change to `ScheduleOutputService.java` from the check.

    DEVIATION FROM PLAN SCOPE, RECORDED HERE FOR VISIBILITY: Task 1's commit (579b090) also touched
    `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`, which 15-16-PLAN.md's
    `files_modified` frontmatter did not list. The change exposes
    `SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME` as a public constant and swaps the pre-existing
    literal `"Shift envelope compliance"` string in `.asConstraint(...)` for that constant --
    same string value, same registered constraint name, no solver behaviour change. It was
    necessary to satisfy the plan's own action text ("take the constant from
    ScheduleConstraintProvider rather than retyping the string"). `git diff --stat` against the
    solver package is therefore NOT empty for this plan, contrary to the plan's `<verification>`
    expectation -- see 15-16-SUMMARY.md deviations for the full accounting.
  severity: major
  found_by: test 11 measurement, 2026-09-01
  promotes: >
    G-15-24's `also_observed` paragraph, which recorded "violationCount 1104 / penalty -1104 while
    the stored score reads hardScore -9 — the two cannot both be right" and closed with "Not chased
    this session." It has now been chased. Which one is right is settled: the divergence field and
    the independent walker (0) are right; constraintViolations (1104) is wrong.
  detail: |
    ON EVERY ACCEPTED SCHEDULE ON THE DESK — all six, spanning true violation counts of 12, 9, 1,
    0, 0 and 0 — `constraintViolations` reports `Shift envelope compliance` with
    violationCount 1104. That number is CONSTANT and equals 138 agent-days x 8 contracted hours:
    every staffed seat on the desk. It carries no information about the schedule it describes.

    NAMED-ROW PROOF, so this is not an aggregate artefact. Armaz Dugashvili, 2026-01-05, on
    schedule 6a10afa1: shift `Mid 11:00-20:00`, bandOffset 300 => break 16:00-17:00; held seats
    11,12,13,14,15,17,18,19. Every seat inside the envelope, none in the break window, and the
    server's OWN `divergence` for that agent-day is `null` (= clean). All eight seats appear in
    constraintViolations as violations.

    IT PROPAGATES TO THE HEADLINE. ScheduleService:159 derives `violatedHardConstraints` FROM
    `constraintViolations`, so schedule 6a10afa1 reports `feasible: true` AND
    `violatedHardConstraints: ['Shift envelope compliance']` in the same response — self-
    contradictory on its face. This is what an operator sees on the results page, and it says the
    phase's headline guarantee is broken on a schedule where it demonstrably holds.
  mechanism: |
    ScheduleOutputService.buildConstraintViolations (:421) calls `solutionManager.explain(schedule)`
    and counts `total.getConstraintMatchSet()`. Its own javadoc says "accepted (DB) schedules should
    not call this method", but the GUARD IS A PROXY for that intent:

        if (schedule.getConstraintWeights() == null) return List.of();

    The proxy does not hold — accepted schedules reach the explain() call anyway. Explaining a
    schedule whose shift problem facts are not reconstituted makes every seat fail the coverage
    predicate, which is exactly the file's own DISCRIMINATOR IDENTITY for a null band pair
    ("maximally asymmetric"): with no band pair, every held seat reads as out-of-envelope. Hence
    "every seat", hence the constant 1104.
  falsified_prediction: |
    RECORDED BECAUSE THE FALSIFICATION IS THE USEFUL PART, per this file's standing methodology.
    Predicted: the 1104 comes from schedules still resident in InMemoryScheduleStore (which would
    retain weights), so schedules predating the 18:44Z ECS task roll should trip the null-weights
    guard and return `[]`. TESTED across all six schedules, two after the roll and four before.
    ALL SIX returned 1104. The prediction was WRONG and store residency is not the discriminator —
    the behaviour is unconditional on the accepted path. Do not re-run this experiment.
  fix: |
    Replace the null-weights proxy with the real question — whether the schedule is an accepted/DB
    schedule — and on that path either (a) return no constraintViolations at all, as the javadoc
    already intends, or (b) derive them from the persisted ShiftDescriptor/AgentShiftAssignment
    columns, which is the same source the `divergence` field already reads correctly. (b) is
    preferable: the data to report violations accurately demonstrably exists, because divergence
    computes it right on every one of the six schedules.
    Whichever is chosen, `violatedHardConstraints` (ScheduleService:159) must stop being derivable
    into a state that contradicts `feasible`.
  test_gap: |
    Nothing asserts that a FEASIBLE schedule reports no violated hard constraints. That single
    invariant — `feasible == true` implies `violatedHardConstraints` is empty — would have caught
    this, and it is cheap. It belongs with SolverQualityGuardTest's INV-1/2/3 family (G-15-22), but
    on the READ path rather than the solve path, which is a gap that family does not currently cover.
  not_implicated_in: >
    The schedule data itself. Every accepted schedule's assignments, shift descriptors and
    divergence fields are correct; only the explain-derived violation report is wrong. No
    re-solve is needed to fix this and no live schedule needs replacing.

- gap_id: G-15-21
  truth: "The seat-supply gate must not count hours only a weekday template reaches when solving a weekend"
  status: resolved
  resolved_by: "Plan 15-18 Task 1 (32c3240) -- SolverService.coveredTimeslotsOnDate, one date-aware
    coverage helper shared by both the blocking supply computation and the trailing tightest-hour
    advisory, replacing the two textually-duplicated calendar-blind anyMatch expressions this gap
    named at ~1184 and ~1281."
  resolved_evidence: |
    THE FIX MATCHES fix EXACTLY. coveredTimeslotsOnDate filters pairs by
    `template.isEffectiveOn(date) && template.appliesOn(date)` before calling
    `ShiftBandPair.covers(ts)` -- the identical two-step `expandMinimumStaffingSeats` already
    applied (6c82241). `ShiftBandPair.covers` itself is untouched, exactly as the fix specified.

    THE WEEKEND OVER-COUNT RED-PROOF, with exact pre/post figures (not merely "it throws"):
    `ShiftEnvelopeSupplyGateTest#refusesWeekendOvercountFromWeekdayOnlyTemplate` -- a two-template
    fixture (weekday-only "Weekday" 08:00-17:00 and weekend-valid "Weekend" 10:00-19:00, clock-time
    footprints overlapping) on a Saturday. PRE-FIX (calendar-blind union): 8 slots counted as
    supplied == 8 contracted -- the gate PASSED. POST-FIX (date-aware): only 5 slots counted (the
    weekend-valid pair's own coverage) against 8 contracted -- a shortfall of 3, and the gate now
    REFUSES, naming both figures exactly ("only reaches 5 slot(s)", "a shortfall of 3 slot(s)").

    ADVISORY COHERENCE, closing the incoherent "tightest at 08:00-09:00 with 0 seat(s)" symptom this
    gap's own detail named: `ShiftEnvelopeSupplyGateTest
    #advisoryNeverNamesAnHourOnlyAWeekdayInvalidTemplateReaches` proves the tightest-hour advisory
    never names an hour reachable only by a weekday-invalid template on that date.

    RE-RUN THIS SESSION (plan 15-19), not transcribed from the plan text:
    `./gradlew test --tests "com.wfm.service.ShiftEnvelopeSupplyGateTest"
    --tests "com.wfm.solver.ShiftEnvelopeSupplyInvariantTest"
    --tests "com.wfm.solver.ShiftDeskEndToEndRegressionTest"` -- 23/23 pass, 0 failures, 0 errors
    (14 + 6 + 3, confirmed against the test-results XML this session, matching 15-18-SUMMARY.md's
    own recorded 23/23).
  severity: major
  found_by: test 10 retest, 2026-08-31
  detail: |
    Third instance of the calendar-blindness class. SolverService.requireShiftEnvelopeSeatSupply
    computes coveredTimeslots TWICE (~1184 and ~1281) with the calendar-blind predicate
    `pairs.stream().anyMatch(p -> p.covers(ts))` over the DESK-WIDE pair list.
    ShiftBandPair.covers(Timeslot) compares only envelope/break TIMES.

    The ~1281 one is cosmetic (the tightest-hour advisory). The ~1184 one feeds
    librarySupplySlots, so on a weekend it counts hours only a weekday template reaches and
    OVER-COUNTS library supply — precisely the failure its own javadoc names: "wave through an
    unsolvable one if it over-counts".

    Visible symptom on the live desk: the advisory reads "tightest at 08:00-09:00 with 0 seat(s)",
    which is incoherent — a covered hour cannot have zero seats. The two definitions of "covered"
    diverged once expandMinimumStaffingSeats became date-aware (6c82241) and this did not.
  fix: "Filter both by template.isEffectiveOn(date) && template.appliesOn(date), the same
    predicate 6c82241 applied to seat expansion. Leave ShiftBandPair.covers unchanged."
  not_implicated_in: "The -9 residual. This over-counts SUPPLY, so it makes the gate too
    permissive, not the schedule worse."

- gap_id: G-15-22
  truth: "A solver-tuning change that wrecks convergence must fail locally, not on a live desk"
  status: resolved
  resolved_by: "Plan 15-14 -- SolverQualityGuardTest (LiveShapeShiftDeskFixture + three
    independent structural walkers + INV-4 violation-count ceiling), and plan 15-15 -- seven
    red-proofs demonstrating the guard is able to fail, plus the thesis proof showing why the
    gate is invariant-based rather than score-based"
  resolved_evidence: |
    THE GUARD. `SolverQualityGuardTest` (`src/test/java/com/wfm/solver/`) solves a live-shape
    synthetic desk five times (seeded, step-count-terminated, through the shipped
    `solverConfig.xml`) in the DEFAULT `./gradlew test` suite -- ungated, no
    `-Dwfm.benchmark=true` -- so the deploy gate picks it up automatically, unlike the existing
    `ShiftModelBenchmarkTest` which sits behind that flag and guarded nothing. Full parameters,
    the per-seed table, and the per-constraint table are recorded verbatim in `15-BENCHMARK.md`'s
    new "Solver Quality Guard (G-15-22)" section.

    THE CEILING AND ITS BASELINE. Five seeds, `totalHardViolations` = 3, 1, 2, 0, 1 -- sorted
    [0, 1, 1, 2, 3], median 1.0. `TOTAL_VIOLATION_CEILING = median + headroom(2) = 3`, committed
    BEFORE the number was read (plan 15-14's P-42). All five seeds: 0 split shifts, 0 edge
    breaks, 0 unstaffed edge hours -- the three structural invariants (INV-1/2/3), asserted
    per-seed and absolute, are the real gate; INV-4 (the ceiling) is a coarse trip-wire on top.

    THE GUARD CAN FAIL -- proven, not assumed. Plan 15-15 added five red-proofs and a thesis proof
    that corrupt an already-solved clean schedule (single solve, no re-solve, so no proof carries
    search variance of its own) and assert each structural walker flags EXACTLY its own injected
    defect: a non-break interior hole named by exact agent/date/hole (INV-1), the break window
    itself proven NOT flagged as a negative control, a null shift-band pair closing the
    `ShiftDeskEndToEndRegressionTest` laundering loophole (INV-1), a one-sided break with the
    exact operational reason string (INV-2), and one unstaffed edge hour scoped to exactly its own
    date and hour with every other cell untouched (INV-3). A sixth test
    (`thesisProof_atZeroWeightTheViolationCountTableGoesBlind_butTheWalkerStillSeesTheSplit`) is
    the mechanical form of G-15-29's argument: at the live weight the corrupted constraint shows
    count 1 in `hardMatchCountsByConstraint`; at weight `ofHard(0)` the same key vanishes from
    that map while `findSplitShifts` still returns the identical hole. A seventh proves the
    failure report's every load-bearing element (invariant name, run parameters, offending rows,
    per-constraint table, comparison guidance, the `G-15-29` pointer) individually, on the
    returned string. All ten tests in `SolverQualityGuardTest` pass:
    `./gradlew test --tests "com.wfm.solver.SolverQualityGuardTest"` -- BUILD SUCCESSFUL, 10/10,
    0 failures, 0 errors.

    WHAT THIS PROVES AND WHAT IT DOES NOT. The guard proves a solver-tuning change that
    reintroduces the SHAPE of regression G-15-22 describes -- broken convergence on a realistic,
    live-weight-shaped fixture -- now fails `./gradlew test`, the same command the deploy gate
    runs, via structural invariants immune to the run-to-run score noise that let the original
    regression hide. It does NOT prove every possible tuning regression is caught -- only ones
    that manifest as a split shift, an edge break, an unstaffed edge hour, or a median
    violation-count breach on THIS fixture's shape and scale.

    BACK-TEST STATUS -- stated plainly, not implied. This guard has NOT been run against the
    original failing acceptor commit that caused the -9 -> -66 regression. That acceptor change
    was already reverted before this guard existed -- test 10's `failed_experiment` above records
    it explicitly ("-66 after acceptor 0hard -> 1hard -- a REAL regression, reverted" / "-9 after
    reverting the acceptor"), and the revert predates plan 15-14 by weeks. No checkout of the
    pre-revert acceptor configuration was performed as part of plan 15-14 or plan 15-15 to confirm
    the guard would have failed on it, and doing so now would require restoring a specific
    historical commit's solver config rather than a documented parameter change. This is a genuine
    gap in the evidence, not a technicality: the guard is verified against SYNTHETIC corruption of
    the SAME shape as the original regression, never against the original regression itself.
  severity: major
  found_by: the failed acceptor experiment, 2026-08-31
  detail: |
    All 580 tests passed with the acceptor setting that regressed the live desk sevenfold
    (-9 -> -66). No fixture is sensitive to acceptor behaviour, so solver-tuning changes have NO
    automated guard at all. That is exactly how the regression shipped with a green build.

    The existing solver tests assert correctness on small fixtures that solve trivially; none
    asserts a hard-score CEILING on a fixture large enough for search quality to matter.
  fix: "A benchmark-shaped test that solves a realistic fixture through the real solverConfig.xml
    and asserts a hard-score ceiling. 15-BENCHMARK.md exists but only ever exercised 4 agents x 2
    days — the same shape blindness that let G-15-10 through in the first place."
  priority: "Before any future acceptor or hard-weight change. Both remaining routes to closing
    G-15-10's residual are exactly that kind of change."

- gap_id: G-15-23
  truth: "The suggested library must not emit duplicate templates or break on the demand peak"
  status: resolved
  severity: minor
  found_by: test 9 retest, 2026-08-31
  detail: |
    Two defects in the generator, both introduced or exposed by this round's changes:

    1. DUPLICATE TEMPLATES. greedyCover legitimately selects two same-span candidates with
       different band offsets so each covers the other's break hour (D-02 self-cover). Expanding
       both to the same three bands (7298f96) collapses them into an identical duplicate. Observed
       live: the weekday cluster contained 08:00-17:00 twice with identical bands.
       Fix: dedupe by (span, bands) after expansion.

    2. BAND PLACEMENT IGNORES DEMAND. Offsets are chosen for COVERAGE, not to avoid the demand
       peak. Observed live: it proposed breaking at 11:00 on the 10:00-19:00 weekend template —
       the busiest weekend hour (44 FTE Saturday, 32 Sunday). The hand-set bands were kept instead.
       Fix: bias offsets away from the highest-demand hours the envelope spans; the demand curve
       is already loaded in that method.
  resolved_by: "Plan 15-17 Task 1 (8bac0bc) dedupes `buildResponse`'s emitted rows on exact
    identity (start, end, sorted weekdays, ordered (offset,duration,capacity) band list) AFTER
    `suggestedBands` runs for every selected candidate, reassigning contiguous `Suggested N`
    names afterward; `uncoveredDetails` is recomputed from the deduped, final-band templates via
    the same `covers()` predicate. Plan 15-17 Task 2 (6c8ddeb) replaces the outward-offset walk in
    `suggestedBands` with demand-ranked selection: every admissible offset (bounds unchanged, still
    excluding envelope edges) is scored by the demand its break window would sit on (max across the
    template's valid weekdays), and the 3 lowest-scoring offsets are chosen, ties broken ascending
    for determinism; a coverage re-check puts the original coverage-bearing offset back (evicting
    the worst-scoring chosen one) if the ranked set would regress coverage. Plan 15-17 Task 3
    (2e35fbf) extends the generator-to-validator round trip onto a peaked-demand fixture, proving
    both fixes together as eight separately-named assertions."
  resolved_evidence: |
    DEDUPE PROVEN: `generateSuggestion_selfCoveringCandidatesWithIdenticalFinalBands_collapseToOneTemplate`
    reproduces the exact self-cover scenario (two candidates sharing span/weekdays/duration, D-02)
    against a peaked single-day fixture and asserts the response contains exactly ONE template
    (`Suggested 1`), not two, with zero uncovered windows.
    `generateSuggestion_templatesSharingASpanButDifferingElsewhere_areNotCollapsed` proves dedupe is
    on exact identity, not span alone: weekday and weekend clusters both propose an 08:00-17:00
    envelope, and the two rows are kept separate (their weekdays and bands differ) with contiguous
    `Suggested 1..N` numbering.

    DEMAND-AWARE PLACEMENT PROVEN: on a full-week fixture with a sharp peak at 14:00 (20 FTE vs 1
    FTE baseline), the generated templates whose envelope spans 14:00 exclude the peak-hour offset
    every time -- e.g. the 12:00-21:00 template chose bands {60,180,240} (13:00-14, 15:00-16,
    16:00-17), explicitly skipping offset 120 (14:00-15:00, the peak); the 10:00-19:00 template
    chose {60,120,180}, skipping offset 240 (14:00-15:00); the 11:00-20:00 template chose
    {60,120,240}, skipping offset 180 (14:00-15:00). This directly closes the live observation (a
    break proposed on the desk's busiest hour) with the same mechanism, generalized via the shared
    demand curve rather than hand-tuned per template.
    `generateSuggestion_exactlyThreeAdmissibleOffsetsOneOnThePeak_stillEmitsAllThree` proves the
    degrade path: when the shortest non-breakless envelope this grid can produce has exactly 3
    admissible offsets and the peak lands on one of them, all 3 are still emitted (band count is
    never sacrificed to dodge demand).

    ROUND-TRIP PROVEN JOINTLY:
    `generateSuggestion_peakedDemandAcceptedUnchanged_cleanOnEveryValidatorAxisAndDeterministic`
    asserts, on a peaked full-week/12-agent fixture, as eight separately-named assertions: zero
    uncovered windows, zero misaligned templates, zero capacity advisories, zero
    break-concentration advisories, no two emitted templates identical on (start,end,weekdays,bands),
    no emitted band overlapping the fixture's own 14:00 peak, no emitted band at an envelope edge,
    and byte-identical repeated calls.

    Full suite: see 15-17-SUMMARY.md for the verbatim before/after totals against the 600/0/0/2
    baseline recorded at commit 660408d.

sunday_2000_is_structural: |
  PREDICTION MADE AND FALSIFIED, recorded because the falsification is the useful part. Having
  seen 0 hard on the same config as Run B, this file predicted Run D's -1 was likewise a search
  miss and that a plain re-run would reach 0 with coverage intact.

  Run E (e620d8f8) — identical library to accepted 709fd8b4, identical params, nothing changed:
      hard -1, soft -73 (worse soft than Run D's -70), coverage still met.
      Run D violation: Tekla Davitashvili   2026-01-11 20:00-21:00
      Run E violation: Nutsa Kipshidze      2026-01-11 20:00-21:00
  Different agent, SAME SLOT, across two independent runs. That is not variance. Sunday
  20:00-21:00 is structurally tight and the earlier prediction was wrong.

  MECHANISM, from the two runs side by side (both 18 Sunday agent-days):
      Run D  Weekend Closing holders = 1  ->  20:00 staffed by 2  ->  1 legal, 1 violating
      Run E  Weekend Closing holders = 0  ->  20:00 staffed by 1  ->  0 legal, 1 violating
  Sunday 20:00 carries NO forecast demand; its seat exists only because of the min-staffing floor
  of one. Weekend Closing (12:00-21:00) is the only template reaching it, and holding it costs an
  agent a full 9-hour envelope that misses the 11:00 peak — where Sunday is already short (needs
  32 agents, has 18). The solver therefore declines to staff Closing and pays 1 hard instead.
  That is a rational trade under the current weights, not a defect.

  OPTIONS, none taken this session:
    (a) Accept -1. One agent-hour in ~1104, with a named and understood cause.
    (b) Extend Weekend Late 11:00-20:00 to 11:00-21:00 so more than one template reaches 20:00.
        Changes its net hours to 9.0, so it becomes eligible only under bounded slack — verify
        against contracted hours before applying.
    (c) Add forecast demand at Sunday 20:00 if the desk genuinely trades then. If it does not,
        (a) is the honest answer and the hour is unstaffed-by-design in OR-1's sense.

  METHODOLOGICAL NOTE, the durable lesson of this session: single-run hard scores on this desk are
  not reliable evidence. Two runs of one configuration gave 0 and -1; two runs of another both gave
  -1 on the same slot. Repeat before concluding, and treat a recurring VIOLATION LOCATION as far
  stronger evidence than a recurring score.
