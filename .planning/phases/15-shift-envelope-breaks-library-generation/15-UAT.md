---
status: partial
phase: 15-shift-envelope-breaks-library-generation
source: [15-01-SUMMARY.md, 15-02-SUMMARY.md, 15-03-SUMMARY.md, 15-04-SUMMARY.md, 15-05-SUMMARY.md, 15-06-SUMMARY.md, 15-07-SUMMARY.md, 15-08-SUMMARY.md, 15-09-SUMMARY.md, 15-10-SUMMARY.md, 15-11-SUMMARY.md, 15-12-SUMMARY.md, 15-13-SUMMARY.md, 15-VERIFICATION.md]
started: 2026-08-27T13:10:00Z
updated: "2026-08-31T00:00:00Z"
---

<!--
WHERE TO TEST

Phase 15 is deployed to the dev environment:

  https://d2bbtcc80peap7.cloudfront.net        (/actuator/health -> UP, Postgres connected)

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

number: 3
name: No Phase 14 desk's validation verdict moved
expected: |
  For a desk that existed before this phase and uses single-break templates, the Shift Library
  validation result — coverage verdict, net hours, grid-alignment verdict — is identical to before.
awaiting: user response

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
result: [pending]
observability_problem: |
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

  ROUTES, for the operator to choose between:
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
result: issue
reported: "Hard: -9, Soft: -67 — NOT FEASIBLE. Nine agent-day seats outside their assigned shift envelope, all on 2026-01-11, all agents seated during their own break window."
severity: major
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

### 11. No agent is seated outside their assigned shift envelope

expected: Every agent works only within the envelope of the single shift assigned to them that day; each working agent-day has exactly one shift. This is the phase's core hard-constraint guarantee.
result: [pending]

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

result: [pending]
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

### 20. Warning list does not grow while a schedule is running

expected: |
  Start a SHIFT-mode solve that produces an envelope divergence and leave the results page open
  while it is RUNNING. The schedule's warnings should NOT accumulate duplicate entries as the page
  polls (every 2s). The divergence information itself should be correct on every refresh.
result: issue
reported: "the capacity warning keeps growing please stop that"
severity: minor
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
passed: 9
issues: 1
pending: 8
skipped: 1
blocked: 1

<!--
  passed : 1, 2, 4, 5, 6, 7, 8, 9, 12
  issue  : 10 (major, was blocker — search plateau, all structural causes fixed)
  skipped: 5b (probe artefact bookkeeping, now closed by the delete control)
  blocked: 18 (production deploy, unchanged)
  pending: 3, 11, 13, 14, 15, 16, 17, 19  — none reached this round; the session ran end-to-end
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
  status: closed_pending_retest
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
  status: open
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

- gap_id: G-15-27
  truth: "An agent's working hours must be contiguous apart from their break"
  status: open
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
  status: open
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
  status: open
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

- gap_id: G-15-21
  truth: "The seat-supply gate must not count hours only a weekday template reaches when solving a weekend"
  status: open
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
  status: open
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
  status: open
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
