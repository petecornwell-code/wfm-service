# Phase 15 — Session Handoff (2026-09-01)

State saved for `/clear`. Read this first, then `15-UAT.md` gaps G-15-27 through G-15-30, then
`OPERATOR-TARGET-MODEL.md`.

---

## 1. IMMEDIATE — there is a broken thing in flight

**Deploy `33543912228` (commit `a320ca7`) FAILED at the test gate.** The step "Run full test suite"
failed in CI. The commit contains V46 + the Java default change + the UI weights page.

- **Nothing broken reached dev.** The gate blocked it. Dev is still serving `a4b2828`, healthy.
- `MigrationEntityConsistencyTest` passes locally, so it is NOT the V46 DDL.
- **The failing test was NOT identified before the session ended.** A full local `./gradlew test`
  was started but had not reported. CI logs were not retrievable while the run was in progress
  (`gh run view --log-failed` refuses until the run completes).
- **Cause of the mistake:** after the V46 change only `--tests "com.wfm.solver.*"` was run (101
  tests, green) instead of the full suite, then pushed. Do not repeat — run the full suite.

**First actions on resume:**
1. `gh run view 33543912228 --log-failed` (now that it has finished) OR `./gradlew test` locally.
2. Fix, push, confirm deploy green.
3. A chained background task was left parked waiting for that deploy and is now dead — the
   re-solve it was going to run never happened. Re-run it manually (params in §3).

---

## 2. LIVE STATE — dev desk Stubhub (EN) `6170be17-3bee-41da-9d81-62ddd50c786f`

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
| `a320ca7` | **V46** (contiguity default 100→10) + Java default + **UI weights page** — **DEPLOY FAILED** |

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
| G-15-30 | resolved | weight ratio — shipped in `a320ca7` (deploy pending fix) |
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
