# Phase 15 — Session Handoff (2026-09-01)

State saved for `/clear`. Read this first, then `15-UAT.md` gaps G-15-27 through G-15-30, then
`OPERATOR-TARGET-MODEL.md`.

---

## 1. IMMEDIATE — nothing is broken; this section was wrong

>>> CORRECTED 2026-09-01T18:50Z. The original text of this section claimed deploy `33543912228`
>>> FAILED at the test gate and that dev was still serving `a4b2828`. **Both claims were false.**
>>> They were written while the run was still in progress, and `gh run view --log-failed`
>>> refusing on an in-progress run was misread as a failure signal rather than as "not finished
>>> yet." Verified evidence below. Do not go looking for a failing test — there isn't one.

**Deploy `33543912228` (commit `a320ca7`) SUCCEEDED on attempt 1.** All three jobs green:
Test (deploy gate) 12m53s, Deploy Backend 4m13s, Deploy Frontend 30s. Run completed 18:46:38Z.

**a320ca7 is what is live on dev**, verified four independent ways:

| check | evidence |
|---|---|
| ECS image tag | `wfm-service:a320ca77b7d45a0111bcfa1f21c7a01229d41876` on task def `wfm-service-dev:65` |
| ECS rollout | PRIMARY, 1/1 running, `rolloutState: COMPLETED`, settled 18:46:28Z |
| V46 applied | Flyway log 18:44:35Z — `Migrating schema "public" to version "46 - default shift work contiguity to 10"` → `now at version v46` |
| frontend | deployed bundle `index-B6kWCkcj.js` contains `Shift Work Contiguity`, `Break Band Capacity`, `Shift Envelope Compliance` |

**The full local suite is green: 590 tests, 0 failures, 0 errors (`BUILD SUCCESSFUL in 8m 8s`).**
This is the run the original section said "was started but had not reported" — it reported after
the handoff was written, and it passed.

**The one real lesson survives:** after the V46 change only `--tests "com.wfm.solver.*"` was run
(101 tests) before pushing, instead of the full suite. That was still the wrong process even
though it happened to be safe this time. Run the full suite before pushing.

**Second lesson, new:** do not record a CI verdict from an in-progress run. `gh run view <id>
--json status,conclusion` distinguishes `in_progress` from `completed/failure`; `--log-failed`
does not.

**First actions on resume:**
1. ~~Find and fix the failing test~~ — no failing test exists. Skipped.
2. ~~Fix, push, confirm deploy green~~ — deploy is already green and live. Skipped.
3. ~~The parked background task is dead; re-run the re-solve manually~~ — **it was not dead.** It
   fired at 18:46:40.649Z, two seconds after the ECS rollout settled at 18:46:38Z, ran the §3
   config, reached 0 hard, and auto-accepted. **See §8 for the result and the state change.**

---

## 2. LIVE STATE — dev desk Stubhub (EN) `6170be17-3bee-41da-9d81-62ddd50c786f`

>>> SUPERSEDED 2026-09-01T18:55Z by the §8 re-solve. `523c8785` is no longer the newest accepted
>>> schedule — `b88cc98f-61bc-407f-80b3-c61b81f9d418` (hard 0, soft -60) is. Both remain ACCEPTED
>>> in the database. Read §8 before treating anything below as current.

**Accepted schedule: `523c8785-2ce5-45a5-8cb9-75d2b8c2ec06` — hard 0, FEASIBLE.**
0 split shifts, 0 edge breaks, weekend 08:00/09:00/10:00/20:00 staffed all 7 days. This meets
every operator requirement. It predates the contiguity constraint but is valid and live.

Other ACCEPTED rows (superseded, left alone): `709fd8b4` (-1), `e6728aab` (-9), `9bd158dd` (-12).

**Live constraint weights (set via API this session, NOT defaults):**
```
shiftEnvelopeComplianceWeight   10
shiftWorkContiguityWeight       10
unassignedAssignmentWeight   10000   <-- LOAD-BEARING, see §4
bandCapacityWeight               1
```

**Shift library** — 5 live weekend templates, all breaks MID-SHIFT (no offset 0, no trailing edge):
```
Weekend Opening  08:00-17:00  breaks 12:00/13:00/14:00
Weekend Early    10:00-19:00  breaks 13:00/14:00/15:00
Weekend Flex     10:00-20:00  breaks 13:00/14:00/15:00
Weekend Late     11:00-20:00  breaks 14:00/15:00/16:00
Weekend Closing  12:00-21:00  breaks 15:00/16:00/17:00
```
Opening and Closing were un-retired this session (they had `effectiveTo 2026-01-01`); they are the
ONLY weekend templates reaching 08:00/09:00 and 20:00 respectively.

**Forecast** — operator added 1 FTE at weekend 08:00/09:00/10:00/20:00. It was removed and then
RESTORED verbatim from `scratchpad/weekend-demand-backup.json`. It is currently as the operator
set it. It was never the cause of anything (see §5).

---

## 3. THE SOLVE CONFIGURATION THAT WORKS

```
overallocationHardLimitPct  500
underallocationHardLimitPct  50
shiftEnvelopeSlackSlots       0
solveTimeSeconds            900
period 2026-01-05..2026-01-11, 08:00-21:00, 60min
```
Every requirement holds under it. Residual is 2-4 envelope violations that VARY RUN TO RUN.

---

## 4. WHAT SHIPPED THIS SESSION (all pushed)

| commit | what |
|---|---|
| `a02d150` | **V45 + `shiftWorkContiguity` constraint** — split shifts unrepresentable. 10 guard tests. |
| `a4b2828` | Phase 15's 3 weights exposed in `ConstraintWeightsDto` — were unreadable/unsettable |
| `c09003f` | G-15-29 / G-15-30 written up |
| `a320ca7` | **V46** (contiguity default 100→10) + Java default + **UI weights page** — **DEPLOYED, live on dev** (§1) |

**`unassignedAssignmentWeight` must stay at 10000.** Lowering it to 1000 for diagnosis told the
solver 3-8 empty seats were acceptable and weekend coverage collapsed. It is back at 10000.

---

## 5. THE MEASURED FINDINGS (do not re-litigate)

- **Contiguity at 100 vs envelope at 1 is a 100:1 arbitrage** — the solver breaches the envelope
  99 times to avoid one split. Observed: 52 envelope violations, 43 on one date. 10/10 is best.
- **Hard SCORES are not comparable across weight changes.** Run U scored -4 at envelope weight 1;
  run V scored -30 at weight 10 and is BETTER (3 violations vs 4). Compare violation COUNTS.
- **This desk's search is intermittent at the feasibility boundary.** Envelope violations across
  runs all meeting every requirement: 2, 3, 3, 4, 6, 8 — no consistent ordering by config. Two
  runs of ONE config gave 0 and -1. Differences of 2-8 on 1104 seats are NOISE.
- **The operator's forecast edit was never the cause.** Run R (first controlled comparison,
  changing only weights) exonerated it immediately. The operator was asked to delete demand rows
  twice on uncontrolled comparisons. That was wrong; the data is restored.
- **Edge break bands took the desk 10 hard -> 1** early in the session, but produce an unbroken
  working day (D5) and the operator rejected them. Breaks are mid-shift only now.

---

## 6. OUTSTANDING GAPS (in 15-UAT.md)

| id | severity | what |
|---|---|---|
| **G-15-22 / G-15-29** | major | **No automated guard on solver quality.** The whole reason this session cost ~12 runs. A benchmark test asserting a hard-score ceiling. DO THIS FIRST. |
| G-15-21 | major | Seat-supply gate is calendar-blind — "tightest at 08:00-09:00 with 0 seats" advisory is incoherent noise |
| G-15-25 | major | Gate unions over desk-wide pairs, so it is blind to band composition entirely |
| G-15-27 | resolved | contiguity — shipped in `a02d150` |
| G-15-30 | resolved | weight ratio — shipped in `a320ca7`, **deployed and live** (§1), validated by the §8 re-solve |
| G-15-23 | minor | generator emits duplicate templates and breaks on the demand peak |
| G-15-26 | minor | `HttpRequestMethodNotSupportedException` returns 500 not 405 (cost this session hours) |

**Also open, from `OPERATOR-TARGET-MODEL.md`** — the operator stated a six-part target model
(library from demand curve; slot-mode break constraints in shift mode; 9h contiguous no
exceptions; breaks mid-shift; use preferences; cross-day continuity). R3 is now partly delivered
by the contiguity constraint. R5 (preferences) is doubly disabled — gated off in SHIFT mode AND
weighted 0/0. R6 does not exist at all.

---

## 7. UAT STATUS

`15-UAT.md` is `partial`. Test 10 still `issue`. Tests 3, 11, 13-17, 19 still `[pending]` — none
were reached this session; it ran end-to-end on solver behaviour instead. Test 18 blocked
(no production deploy). Test 19's expectation was amended (bounded slack changed what an unworked
legal slot means) and then partly re-corrected by G-15-27 — read both notes on it.

**Standing rule from the file, still applies:** do not trust a recorded deployment claim,
re-derive it. `git log --oneline origin/<branch>..HEAD -- . ':!.planning/'` must be EMPTY and the
newest successful `deploy.yml` run's headSha must equal HEAD.

**`gh` auth slips back to `pcornwell` and pushes 403.** Run `gh auth switch --user
petecornwell-code` when that happens.

**`gh run rerun` can NEVER redeploy** — ECR tags are immutable and the image is tagged with the
commit SHA. A redeploy needs a commit touching something outside `.planning/**`.

---

## 8. THE RE-SOLVE — ran 2026-09-01, 0 HARD, AUTO-ACCEPTED

**This is the first solve ever run with V46's contiguity default of 10 actually applied in the
database.** It was not started by hand — the parked chained task from the previous session fired
on its own when the deploy settled (§1 item 3).

```
solve id   8d67825c-4f29-4fd6-9e66-b3506c117d65   started 18:46:40.649Z
config     §3 verbatim — 2026-01-05..2026-01-11, 08:00-21:00, 60min,
           over/under 500/50, slack 0, 900s, mode SHIFT,
           breaks 60min ON_HOUR, contracted 8.00/day
weights    envelope 10, contiguity 10, unassigned 10000, band capacity 1

18:46:42   -941684 hard / -796 soft
18:47:43      -140 hard /  -63 soft
18:48:44       -40 hard /  -61 soft
18:49:45         0 hard /  -60 soft   <-- reached zero, then flat
18:54:13   COMPLETED    0 hard /  -60 soft   FEASIBLE
```

It terminated at 18:54:13Z, ~7m33s into a 900s budget — an unimproved-step termination, not the
clock running out. Not a truncated run.

**Every operator requirement holds.** Verified twice independently — once by the chained task,
once by re-deriving it from the API response afterwards. Both agree exactly:

```
split shifts      0 / 138 agent-days
edge breaks       0            (no break at a shift's first or last slot)
violatedHardConstraints  []    (empty)

edge-hour coverage, agents working:
  date        08  09  10  20
  2026-01-05   4   5   6  10
  2026-01-06   4   6   8   8
  2026-01-07   2   5   7   6
  2026-01-08   2   5   8   6
  2026-01-09   3   5   6  10
  2026-01-10   1   1   4   3
  2026-01-11   1   1   3   2
```

All four edge hours staffed on all 7 days. The only residual is soft: **Bulk under-allocation
soft, n=12, -60 soft** — the entire soft score.

**Against the previously-accepted `523c8785`:** soft -60 vs -76, and `violatedHardConstraints` is
now empty where `523c8785` reported `["Shift envelope compliance"]` despite also scoring 0 hard.
Both have 0 split shifts and 0 edge breaks. Coverage is comparable, better at the weekday 08:00-
09:00 edge, slightly thinner on 2026-01-10 (08=1 vs 3). Per §5, differences of this size on this
desk are within run-to-run noise — **do not read the soft-score gain as proof V46 improved
anything.** It is one run. G-15-22/G-15-29 (an automated quality guard) is still the open gap
that would make this claim testable.

### STATE CHANGE — the desk's accepted schedule moved

The chained task was written to auto-accept on 0 hard, and it did:

```
ACCEPTED http:200
b88cc98f-61bc-407f-80b3-c61b81f9d418   ACCEPTED   hard 0 / soft -60   (this run)
523c8785-2ce5-45a5-8cb9-75d2b8c2ec06   ACCEPTED   hard 0 / soft -76   (previous, still ACCEPTED)
709fd8b4 (-1), e6728aab (-9), 9bd158dd (-12)      ACCEPTED, older, untouched
```

Two things to know about that:

1. **The accept re-keyed the schedule.** It went in as `8d67825c` and is now `b88cc98f`, with a
   `createdAt` identical to the microsecond (`18:46:40.649148Z`). Same solve, new id. Whether
   accept is meant to mint a new id is **not established** — nobody has looked at that code path.
   Worth a glance before it confuses someone mid-UAT.
2. **Accepting does not un-accept the previous one.** The desk now carries five ACCEPTED rows.
   Which one is authoritative is presumably newest-wins, but that is an assumption, not a
   verified fact. If any UAT test depends on "the accepted schedule", pin the id explicitly.

Nothing here was rejected, stopped, or deleted.

### Two API details that cost time, for whoever is next

- **`/api/v1/**` returns bare `400 Bad Request` without an `X-Tenant-ID` header.** No message, no
  error code — it reads like a broken route. Use `-H "X-Tenant-ID: 1"`. The frontend sets it in
  `frontend/src/api/client.ts`.
- **`/actuator/info` returns `{"code":"INTERNAL_ERROR"}`**, so there is no build-info endpoint to
  read the deployed SHA from. To verify what is live, go to the source:
  `aws ecs describe-task-definition --region eu-west-2 --task-definition wfm-service-dev:<rev>
  --query 'taskDefinition.containerDefinitions[0].image'` — the tag is the commit SHA.
