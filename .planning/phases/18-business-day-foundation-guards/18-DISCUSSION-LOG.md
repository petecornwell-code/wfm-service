# Phase 18: Business-Day Foundation & Guards - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-30
**Phase:** 18-business-day-foundation-guards
**Areas discussed:** BDAY-06 fixture enforcement, BDAY-03 generation walk, Salvage strategy,
day_start surface & override

**Prior context applied:** v1.4's `18-CONTEXT.md` (same phase, same operator, 2026-09-29) survives at
`rescue/phase-18-unwind-20260930`. Its D-11..D-26 were carried forward without re-asking and appear as
CONTEXT.md's D-01..D-12. Its D-01..D-10 (the captured-live-desk golden-file fixture) are dead by v1.5
scoping and were not offered.

---

## Area selection

| Option | Description | Selected |
|--------|-------------|----------|
| BDAY-03's generation walk | Flagged as a possible scoping tension: v1.5 moved BDAY-03 into Phase 18 while BDAY-04 stayed in 19, but the generation loop is calendar-bound and there is no rescue-tag precedent | ✓ |
| Salvage: cherry-pick vs re-author | Seven Phase-18-scope commits reusable, not the four the ROADMAP names; `5ddd8dc` already salvaged forward | ✓ |
| BDAY-06 fixture enforcement | The named open decision, but the question changed shape once the fixture became constructed rather than captured | ✓ |
| day_start surface & override | Roadmap adds an explicit-copy constraint; `fec8990`'s override justification is void | ✓ |

**User's choice:** all four.
**Notes:** added unprompted — "The golden file fixture seems nonsensical it needs explanation and
justification." This set the order: BDAY-06 was taken first, starting from an explanation of what a
golden file is and why v1.4 needed one.

---

## BDAY-06 fixture enforcement

### Q1 — How should the constructed suite record its expected values?

| Option | Description | Selected |
|--------|-------------|----------|
| Inline argued assertions, no golden file | Each scenario asserts named expected values directly with a comment arguing why the number is correct; the class-load validator still catches a missing boundary case. Drops the golden file and justification log, and with them the roadmap's "enforcement mechanism" open decision as a void premise | ✓ |
| Inline assertions + one small golden file | Argued assertions carry correctness; a single file additionally records the full decomposition as a change-detector. Catches collateral movement earlier than BDAY-07 would, at the cost of a second blind baseline needing re-blessing | |
| Golden file with hash-based justification log | v1.4's recommended option carried forward verbatim — the conservative reading of "don't deviate from what was scoped" | |

**User's choice:** inline argued assertions, no golden file.
**Notes:** the explanation that preceded this established that v1.4's golden file was *blind* — it
asserted that numbers had not changed, never that they were right — because 288-agent match counts
cannot be hand-written. Constructed scenarios have knowable answers, so the recording mechanism loses
its only justification. Collateral-movement detection is BDAY-07's job in Phase 20, which is why
`REQUIREMENTS.md:48-50` split the two requirements in the first place.

### Q2 — For scenarios that need the constraint provider, how should the fixture be built?

| Option | Description | Selected |
|--------|-------------|----------|
| Pin every assignment, never call `solve()` | Build the Schedule fully pre-assigned, evaluate with `SolutionManager.update()`/`.explain()`. Pure function evaluation, so exact match counts are assertable. New shape here — `ConstraintPrecedenceObservabilityTest` and `ShiftEnvelopeGroundTruthTest` are partial precedents but both solve first | ✓ |
| Split the suite by tier, Timefold-free where possible | Push as many scenarios as possible to plain unit tests on DayWindow / validation / contracted-hours arithmetic | |
| Solve with a fixed seed and step-count termination, assert tolerances | Follows `ShiftDeskEndToEndRegressionTest` and the project's seeded-run discipline, but expected values become bands, not equalities | |

**User's choice:** pin every assignment, never call `solve()`.
**Notes:** the question was raised as a challenge — "How can an optimization algortihm have expected
values - the solve isnt deterministic" — and it is correct about solves. The distinction that resolved
it: the optimiser is non-deterministic, the score function is not. This exchange also surfaced that
`ROADMAP.md:61` miscites "`ShiftDeskEndToEndRegressionTest`'s scoring-only pattern" — that test runs a
real `solver.solve(unsolved)` at `:526` with tolerance-based assertions, so a planner following the
pointer would build the wrong thing. Option 2's tier split was not selected as the primary mechanism
but its substance survives as CONTEXT.md D-15, since three of the six scenarios need no score director
at all.

### Q3 — How should the three not-yet-possible scenarios be expressed?

| Option | Description | Selected |
|--------|-------------|----------|
| Pin today's behaviour + a set-equality flip registry | Each impossible scenario asserts what happens today and is registered by name in a parsed `.md` naming which phase flips it and to what; validator enforces set equality in both directions | ✓ |
| Pin today's behaviour, document the flip in comments only | Same assertions, intent in javadoc. Relies on a human noticing at Phase 19 planning time | |
| Build them below the validation layer and assert the wrong answer | Construct problem facts for a 22:00–06:00 span directly, bypassing `ShiftTemplateService`, and record the incorrect current behaviour | |

**User's choice:** pin today's behaviour + a set-equality flip registry.
**Notes:** three of BDAY-06's six named scenarios describe properties that cannot exist on today's tree
— a shift crossing midnight (`DayWindow.durationMinutes` throws, `ShiftTemplateService` refuses), PTO on
the starting vs. ending day, and the starting-weekday-only half of contracted hours. Comment-only
documentation was declined on the evidence that STATE.md's deferred-items table shows such notes going
three milestones unactioned.

### Q4 — How should the class-load validator know a boundary scenario is present?

| Option | Description | Selected |
|--------|-------------|----------|
| Named scenario registry in code, set-equal to one `.md` | Static map of scenario name → fixture builder; validator set-compares keys against a parsed resource that also carries the flip registry | |
| Reflect over annotated test methods | Custom annotation naming each boundary property; validator reflects and set-compares | |
| Validate the fixture data, not the scenario names | Validator inspects the constructed facts and asserts the structural properties are present | |
| Shared structural predicates, reusable later on live data *(re-offered after the exchange below)* | One set of named predicates over problem facts; validator asserts each fires at least once; predicates written to be callable against any Schedule's facts so Phase 20 can point them at the live drift-guard desk | ✓ |
| Shared predicates now, and also point them at a live desk in Phase 18 | Same predicates, plus a Phase 18 run against real loaded facts to answer the coverage question a milestone earlier | |

**User's choice:** shared structural predicates, reusable later on live data.
**Notes:** the first three options were set aside by a counter-question — "Can these boundary scnearios
not be checked dynamically when the solve starts?" Ruled out for this phase on three grounds: it
observes rather than asserts (detecting a 23:00–00:00 slot is not asserting what it scores); it only
fires when someone solves, so it is not checkable on a build with no database; and new production code
in the solve path breaks the phase's no-observable-change claim, on a path where a deploy kills a
running solve and the detail payload is 4 MB. But the idea identified a real gap — nobody knows whether
live data contains the midnight boundary at all, which is exactly what cancelled v1.4
(`REQUIREMENTS.md:44`). The predicates were therefore made reusable against live facts, with that use
deferred to BDAY-07 in Phase 20. Recorded as CONTEXT.md D-17 and D-18.

---

## BDAY-03 generation walk

### Q1 — How should Phase 18 satisfy BDAY-03 without pre-empting Phase 19?

| Option | Description | Selected |
|--------|-------------|----------|
| Additive anchored helpers in DayWindow | New day-start-aware functions alongside the existing ones; nothing removed, behaviour byte-identical at 00:00. Phase 19 still owns the overload removal and the call-site re-point | ✓ |
| Local minute arithmetic in the generator only | Keeps DayWindow untouched but plants a new raw-arithmetic site that BDAY-05's own guard must allowlist — a self-inflicted exemption in the phase tightening the guard | |
| Move BDAY-03 to Phase 19, restoring v1.4's split | Honest about the dependency, but grows the change the milestone insists stays most isolated, and costs a roadmap/traceability edit | |
| Prove BDAY-03 against a test double, ship nothing | Satisfies "proven by a direct unit test" literally, but proves a property of code that gets thrown away | |

**User's choice:** additive anchored helpers in DayWindow.
**Notes:** the tension was presented with evidence — `generateTimeslots:130-147` walks minute-of-day
inside one calendar date via `ts.setDate(date)`, and `DayWindow.toLocalTime` throws outside `[0, 1440]`
at `:122`, while a 21:00 anchor needs minutes 1260→2700 across two dates. v1.4's `985e365` added only a
`dayStart` parameter and the tiling refusal, so there is no precedent for the spanning walk.

### Q2 — Where should business-date derivation live?

| Option | Description | Selected |
|--------|-------------|----------|
| DayWindow gains date-aware functions | Takes on LocalDate for the first time; everything anchor-related in one class, and it is the file Phase 19 already rewrites so no new file joins that commit | ✓ |
| DayWindow stays time-only; a thin resolver composes it | Preserves single responsibility and the javadoc's focus, at the cost of a second file in the anchor story | |
| Derivation lives in TimeslotGeneratorService | Fewest moving parts, but not reusable by Phase 20's joins, coverage reporting or SOLV-07's anchor-agreement guard — the drift shape SOLV-07 exists to prevent | |

**User's choice:** DayWindow gains date-aware functions.
**Notes:** `DayWindow` imports only `java.time.LocalTime` today, so business-date derivation is the
first thing needing both types. The accepted cost is that its javadoc — currently the clearest statement
of the `00:00`-by-position rule anywhere in the codebase — has to grow a second concept without losing
the first.

### Q3 — How to stop the additive helpers becoming permanent duplicates?

| Option | Description | Selected |
|--------|-------------|----------|
| `@Deprecated` on the midnight-implicit forms | Every remaining call site emits a build warning, so the compiler maintains Phase 19's migration list. Warnings not errors, since ~100 sites would break the build | ✓ |
| Add it to the flip registry | Reuses the D-16 mechanism with no build noise, but gives no per-call-site visibility | |
| Rely on Phase 19's own criterion | Simplest — the overload removal already turns a missed site into a compile failure — but nothing marks the doomed vocabulary in the meantime | |

**User's choice:** `@Deprecated` on the midnight-implicit forms.
**Notes:** presented alongside fresh measurements, since the roadmap flags its counts as taken on a
moved tree. Measured on HEAD: **100** `DayWindow.` call sites across **16** files in `src/main` (roadmap
says ~112 across 16), **46** in `src/test`; comparison tokens **43 / 26 / 36** (roadmap says 41/26/36).
The file count held; the call-site count did not. This decision replaces Phase 19's "re-grep the count
fresh" action item with a compiler-maintained list.

### Q4 — Do `periodStart`/`periodEnd` mean business or calendar dates on a non-midnight anchor?

| Option | Description | Selected |
|--------|-------------|----------|
| Business dates | D..D+6 on a 21:00 desk writes calendar rows D 21:00 → D+7 21:00; `isDesired` partitions on derived business date | ✓ |
| Calendar dates, with slots clipped at the period edges | Smallest change, but manufactures partial business days at every boundary — the SOLV-04 under-allocation defect in a new place | |
| Defer the decision to Phase 19 and keep Phase 18's test single-day | Literally all criterion 2 asks for, but leaves "what does the period mean" to be answered by accident in the next phase's diff | |

**User's choice:** business dates.
**Notes:** the parameters feed three things — the outer generation loop, `timeslotsMatch`'s early return
that preserves linked staffing requirements, and `isDesired`'s surviving/obsolete partition (`:95-112`).
All three, plus `slotKey`, must be re-read in business-date terms with `00:00` behaviour proven
unchanged.

---

## Salvage strategy

### Q1 — How should Phase 18 use the rescue-tag material?

| Option | Description | Selected |
|--------|-------------|----------|
| Cherry-pick the mechanical ones, re-author what BDAY-03 touches | Take `84fdc3f`, `a1c0077`, `2196e40`, `7d42f23`, `63d85a6`; re-author `985e365`'s generator change since the business-date walk replaces its `business_date = date` | ✓ |
| Cherry-pick all seven, then amend for BDAY-03 | Preserves v1.4's verified end state as a diffable checkpoint, but the generator gets written twice and `business_date = date` lands only to be replaced | |
| Re-author everything fresh, using the commits as reference only | Cleanest answer to the "re-imports the habits that produced the cancelled fixture" concern, but re-types 417 lines of verified guard test and 239 lines of scanner | |

**User's choice:** cherry-pick the mechanical ones, re-author what BDAY-03 touches.
**Notes:** two findings shaped the options. **First**, seven Phase-18-scope commits exist, not the four
the ROADMAP names — `fec8990`, `a1c0077` and `985e365` are unnamed. The other ten are the `wfm.capture`
harness, deliberately out of scope. **Second**, `5ddd8dc` (the export fix on HEAD) was salvaged forward
from v1.4, so the rescue tag's `midnight-time-arithmetic.md` already reflects the fixed `isAfter` and
carries only the `isBefore` half at `:105`, which still exists on HEAD — meaning `63d85a6` should apply
close to cleanly rather than fight the one commit that landed since.

### Q2 — What about the v1.4 phase numbers and D-nn references embedded in the picks?

| Option | Description | Selected |
|--------|-------------|----------|
| Describe the reason inline, drop the D-nn citations | Comments state why the code is as it is, with no reference to any decision log; phase numbers become requirement IDs, which are stable across milestones | ✓ |
| Renumber to v1.5 on cherry-pick | Preserves traceability the way Phases 14–17 comments do, but the references are only as durable as v1.5's CONTEXT.md — and this is their second renumbering | |
| Leave them, add a provenance header | Cheapest, commits stay byte-identical to the verified originals, but every reader hits stale numbers | |

**User's choice:** describe the reason inline, drop the D-nn citations.
**Notes:** concretely affected — `bday-02-write-paths.md` says "Phase 21 (SOLV-01)" and "Phase 20
(overnight shift templates)", which in v1.5 are Phase 20 and 21 respectively; V53's migration comment
cites D-18, D-22, D-24. The v1.3 audit named stale planning references as this project's specific
weakness and STATE.md records three documents asserting things a later event had falsified.

### Q3 — Does BDAY-05's red-proof need to exercise the full scan pipeline?

| Option | Description | Selected |
|--------|-------------|----------|
| Parameterise the source root, point a red-proof at a synthetic tree | Real assertions still walk `src/main/java`; a new red-proof runs the same pipeline over a test-resources file with one offending line and asserts the set-equality assertion fails | ✓ |
| Write a temporary offending file into `src/main`, then delete it | Most literal reading, no guard refactor — but a killed run poisons the source tree and would corrupt a concurrent build | |
| Keep the matcher-level proofs as they stand | Cherry-picks stay byte-identical; they do prove the matcher is live and the heuristic cuts both ways | |

**User's choice:** parameterise the source root, point a red-proof at a synthetic tree.
**Notes:** two findings here. `7d42f23` **already satisfies BDAY-08 verbatim** — it distinguishes
deriving writers from propagating ones (`snapshot.setBusinessDate(live.getBusinessDate())` at accept
time), pins both sets by name, and asserts set equality in both directions, classifying by whether the
argument is a `.getBusinessDate()` read. And `63d85a6` already carries two red-proofs
(`theScanDetectsAFreshOccurrence`, `theComparisonScanDetectsAFreshOccurrence`) — but both test the
matcher predicate against synthetic strings, not the walk → comment-strip → match → set-compare
pipeline. That leaves one failure mode uncovered: weakening the final assertion from set equality to a
subset check would pass every existing test in the class, which is the "guard becomes decoration"
failure its own javadoc warns against.

---

## day_start surface & override

### Q1 — How should the control present the `00:00`-only restriction?

| Option | Description | Selected |
|--------|-------------|----------|
| Disabled control with explanatory copy | Renders alongside `scheduling_mode` and `default_contracted_hours_per_day`, visibly disabled, copy naming the restriction. Backend validation stays the real gate, so the UI is disclosure. Phase 19 deletes one attribute and one sentence | ✓ |
| Enabled control, rejects non-`00:00` inline before submitting | Operator interacts with the real control, but the restriction then lives in two places | |
| Read-only display of the current value, no control at all | Most literal no-change reading, but Phase 19 then builds the control from scratch during the milestone's most isolated change | |

**User's choice:** disabled control with explanatory copy.
**Notes:** satisfies `ROADMAP.md:67-69`'s new requirement that the restriction be explicit in the copy
rather than enforced silently via a backend 400 — a constraint v1.4 left to Claude's discretion.

### Q2 — Should Phase 18 still build `fec8990`'s refusal and named override?

| Option | Description | Selected |
|--------|-------------|----------|
| Build the refusal, drop the override | Refusal stands on its own — dev is production and Phil-US holds a live accepted schedule. Override's only intended caller is now deferred to Future Requirements | ✓ |
| Build both, as `fec8990` wrote them | Both are written and verified, and a hard refusal with no escape hatch is the shape that makes a real operation impossible — the argument REQUIREMENTS.md uses for the rest waiver | |
| Build neither in Phase 18 | The gate makes the refusal unreachable anyway | |

**User's choice:** build the refusal, drop the override.
**Notes:** D-19's recorded justification for the override was "Phase 23 must actually perform this
change — making it explicit and named gives Phase 23 a reversal to test against." The Phil-US migration
is now MIGR-01..04 under Future Requirements, so nothing in Phases 18–22 changes a live desk's day
start and that justification is void. An override with no caller would have its name and semantics fixed
by this phase and inherited by a migration phase not yet designed; MIGR-04 (a documented, tested
reversal) is where it should be designed. "Build neither" was declined as inconsistent with the tiling
refusal, which is deliberately built here despite being unreachable.

### Q3 — How should the copy requirement be held in place?

| Option | Description | Selected |
|--------|-------------|----------|
| UAT verification only | Treated as every other UI guarantee here has been; consistent with Phase 13's P-11, and Phase 19 deletes the copy within one phase | ✓ |
| Structural source scan over the `.tsx` | Would make the roadmap's requirement enforced rather than hoped for, using the same static-source-scan idiom chosen over reflection in plan 16-04 | |
| Backend-driven copy | Server declares the supported range and reason; copy and validation cannot disagree, the D-08 discipline | |

**User's choice:** UAT verification only.
**Notes:** accepted knowingly as the one place in this phase where a requirement has no structural
guard. The `.tsx` scan was judged brittle to ordinary rewording for a one-phase lifespan.

---

## Claude's Discretion

- The deterministic assignment rule for the pinned solutions, provided each scenario argues its solution
  is solver-reachable and every D-17 predicate fires.
- The precise name-pattern list for the comparison-operator heuristic, provided it is documented in the
  `.md` alongside its limitations.
- Signature and naming of the additive anchored helpers, and how the two arithmetic vocabularies are
  visually distinguished in `DayWindow` pending Phase 19's removal.
- Placement of `day_start` on `DeskManagement.tsx` and the wording of its disabled-state copy.
- Which resource file carries the flip registry and the predicate list, and whether they share one.
- Whether `ROADMAP.md`'s two errors are corrected as part of this phase or left to the next edit.

## Deferred Ideas

- Point the boundary-case predicates at live desk facts — Phase 20 input for BDAY-07.
- Solve-time dynamic boundary detection in the production solve path — revisit only as an operator
  diagnostic, never as BDAY-06's mechanism.
- Correcting `ROADMAP.md`'s "scoring-only pattern" citation and its "~112 call sites" figure.
- The named day-start override — belongs with MIGR-04.
- Retiring the `00:00`-means-end-of-day convention — BDAY-04, Phase 19.
- Widening the day-start range beyond `00:00` — Phase 19.
- Date-token guarding — SOLV-02, Phase 20.
- A `.tsx` structural source scan for UI copy — revisit if copy requirements recur.
- Parser-based type-aware guard detection — revisit only if the name heuristic proves noisy.
- Behavioural night-start guard at full-system scope — Phase 19/20 candidate.
- A frontend test framework — out per Phase 13's P-11.

### Scope creep redirected

None. All four areas stayed inside the phase boundary. The solve-time-detection suggestion was the one
proposal that would have added production behaviour to a no-op phase; it was redirected into a
test-scope predicate set with the live-data half deferred to Phase 20 rather than declined outright.
