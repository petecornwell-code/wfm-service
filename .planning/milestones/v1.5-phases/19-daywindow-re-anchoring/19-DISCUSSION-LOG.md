# Phase 19: DayWindow Re-anchoring - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-30
**Phase:** 19-daywindow-re-anchoring
**Areas discussed:** Gate ownership, Anchored surface shape, Anchor plumbing, The forward-interval throw

Four gray areas were offered; the operator selected all four. A fifth (the criterion-3 reference
oracle) was flagged as Claude's to take and was raised explicitly at the end instead.

---

## Gate ownership

Opened by reporting a direct contradiction found during context loading: `18-CONTEXT.md` D-07,
`DeskService:213`'s javadoc and `18-01-SUMMARY.md` all assign the `00:00`-only gate deletion to
BDAY-04 (Phase 19), while `ROADMAP.md`'s Phase 18 success criterion 2 assigns it to Phase 20.

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 20 | ROADMAP wins. Phase 19 stays a pure no-op; no desk can hold a non-midnight anchor until the joins are trustworthy. Phase 19's only `DeskService` touch is correcting the stated owner. Costs: a dead validation line and a known-stale comment survive one more phase. | ✓ |
| Phase 19, full widening | D-07 wins. Delete the line, open the range to 15-minute boundaries, make the desk-config cell editable and rewrite its copy. Phase 19 gains a UI surface and a real operator-visible change; dev gets a window where a 21:00 anchor is settable against calendar-date-keyed joins. | |
| Build the range, keep the narrowing | Mirrors D-08's own build-it-unreachable precedent: write and test the 15-minute-boundary validation but leave the `00:00` narrowing in front of it. Phase 20 then deletes one line again. Costs: more surface in this phase. | |

**User's choice:** Phase 20
**Notes:** Supporting evidence presented before the question — `DeskService`'s only `DayWindow`
reference is its javadoc, so it is not a functional call site and deleting the gate there would put a
non-call-site change inside the commit criterion 5 confines; dev is the live system; and the Phase 18
disclosure copy ("only 00:00 is supported until overnight scheduling lands") would become false in a
phase with no UI hint. Recorded as D-01 / D-02.

---

## Gate ownership — retirement proof

Follow-up within the same area. Mechanical point raised first: the nine forms cannot all simply
vanish, because `startMinuteFromDayStart` and `endMinuteFromDayStart` call `startMinute` and
`timeAtDayStartOffset` calls `toLocalTime` — "no longer exists" has to mean "no longer public".

| Option | Description | Selected |
|--------|-------------|----------|
| Invert `DeprecationIsLive` | Rewrite the existing reflective guard from "must be deprecated" to "no public static takes a scheduling time". Same machinery, flipped. Goes red for free on a reintroduced convenience overload, and enforces the public/private boundary. Retirement becomes a standing property. | ✓ |
| Deletion is the proof | Making the nine forms private is itself the retirement; drop `DeprecationIsLive`. Smallest diff. Costs: nothing stops a later phase adding a public two-arg overload back. | |
| Guard the special-case too | Invert the guard AND add a source-level assertion that `MIDNIGHT` special-casing appears nowhere in `DayWindow` outside two named private helpers. Strongest. Costs: a second scanner, overlapping BDAY-05's existing scan. | |

**User's choice:** Invert `DeprecationIsLive`
**Notes:** Recorded as D-03, with D-06 recording the private-not-deleted mechanic that makes the
inverted assertion the right one.

---

## Anchored surface shape

Opened by ruling out hand-composition on evidence rather than taste. `MidnightTimeArithmeticGuardTest`
was read: it scans unconditionally for `Duration.between(`, `ChronoUnit.MINUTES`, `.plusMinutes(`,
`.minusMinutes(`, and for `.isAfter(`/`.isBefore(`/`.compareTo(` gated by a `LocalTime`-receiver-name
heuristic — and never looks at `int` arithmetic or `int` comparisons. Hand-composing the seven
composite functions at 37 call sites would therefore leave the guard green while each site
reimplements half-open interval semantics. Not offered as an option.

The hazard that drove the remaining choice: every parameter is a `LocalTime`, so a static
`overlapsFromDayStart(dayStart, s1, e1, s2, e2)` compiles fine with its arguments transposed.

| Option | Description | Selected |
|--------|-------------|----------|
| Bound instance | `DayWindow.anchoredAt(dayStart)` returns an instance; every public function becomes an instance method with today's exact signature. No argument list grows, transposition is impossible, the anchor binds once per scope. The guard exempts `DayWindow` by class name, so instance methods stay exempt. Costs: `DayWindow` stops being a static-only utility. | ✓ |
| Static twins, `LocalTime` anchor | `overlapsFromDayStart` etc. with `dayStart` as the first parameter, consistent with the five primitives Phase 18 landed. Most mechanical migration. Costs: a 6-argument all-`LocalTime` static and the silent transposition hazard. | |
| Static twins + `DayStart` type | Same, but the anchor is a `record DayStart(LocalTime at)` so transposition is a compile error and `DayWindow` stays static-only. Costs: a new type in a phase confined to `DayWindow` and its call sites; edges toward the wrapper-type approach REQUIREMENTS.md rejected for business dates. | |

**User's choice:** Bound instance
**Notes:** Recorded as D-04, with D-05 recording the guard blind spot as the reason hand-composition
is excluded — including that the Phase 18 `@Deprecated` javadoc text directing callers to
hand-compose is superseded and must not be followed literally.

---

## Anchored surface shape — the midnight shortcut

Follow-up within the same area. Once the anchor is a constructor argument, nothing stops a main-code
caller writing `DayWindow.anchoredAt(LocalTime.MIDNIGHT)` — compiler-forced, reviewer-invisible, and
semantically the old bug restored.

The operator asked for a plainer explanation of what "forbidding it" meant. Restated concretely
against `ShiftTemplateBreakBand.java:69`: the real path threads the desk's anchor in as a parameter
(and `ShiftTemplate` holds only a `deskId`, so that means changing signatures up the call chain); the
shortcut changes one line and nothing else. The question was then re-put.

| Option | Description | Selected |
|--------|-------------|----------|
| Allowlist with a reason | The shortcut is allowed but every use under `src/main` must be listed in `midnight-time-arithmetic.md` with a note saying why no desk anchor is reachable there. Fails on an unlisted use and on a stale entry — the same twice-proven pattern the file already uses. | ✓ |
| Forbid it outright | Build fails on any use in `src/main`. Strongest guarantee, and pre-commits the plumbing question to "thread it everywhere". If a site cannot reach a desk, this phase invents plumbing under pressure or the rule gets a carve-out. | |
| No guard | Code review is the check. Smallest diff. The one substitution that silently restores today's midnight assumption is then the one thing untested. | |

**User's choice:** Allowlist with a reason
**Notes:** `FteSpreadsheetGenerator` — a static utility with its own `main` and zero `Desk`
references — was identified as the standing counter-example that makes "forbid outright" turn into an
allowlist by another name. Recorded as D-07.

---

## Anchor plumbing

Scouting established the channels before the question: `ScheduleConfig` is already the
`@ProblemFactProperty` scalar `SolverService.buildSchedule` fills from the `Desk`, already carries
`startTime`/`endTime`/`schedulingMode`, and is already in scope in `ScheduleConstraintProvider`'s
constraints. The awkward sites are the model classes — `ShiftBandPair.covers(Timeslot)`,
`ShiftTemplateBreakBand.getBreakStartTime(template)` and `ShiftTemplate`'s net-hours calculation — all
doing interval arithmetic while holding only a `deskId`.

| Option | Description | Selected |
|--------|-------------|----------|
| Two channels | Solver reads `ScheduleConfig.dayStart()`; services build the bound `DayWindow` once per public method from the `Desk` they already load and pass the `DayWindow` object (not a `LocalTime`) into model helpers. Model classes never hold or look up an anchor, and a `DayWindow` parameter cannot be confused with a scheduling time. | ✓ |
| Thread from the controller | No service does its own lookup; the anchor enters at the entry point and is passed down every layer uniformly. Most explicit and fully compiler-forced. Costs: the widest diff in the riskiest phase, and duplicates what `ScheduleConfig` exists to do. | |
| Denormalise onto the entities | Copy `day_start` onto `shift_template` so model methods stay self-sufficient. Smallest call-site diff. Costs: a second copy of desk state with no write-path guard — the inverse of BDAY-08 — plus a schema change in a phase confined to `DayWindow` and its call sites. | |

**User's choice:** Two channels
**Notes:** `ScheduleOutputService` was found to hold no `Desk` reference but to receive the
`Schedule`, so it reaches the anchor via `getScheduleConfig()` — the solver channel extended, not a
third mechanism. Recorded as D-08 / D-09.

---

## Anchor plumbing — commit boundary

Follow-up within the same area, driven by criterion 5 being machine-checkable
(`git diff --name-only`, "only `DayWindow` and its call sites"). `ScheduleConstraintProvider` is
plainly a call site; `ScheduleConfig` and `SolverService.buildSchedule` are plumbing that enables one
and carry no `DayWindow` reference today.

| Option | Description | Selected |
|--------|-------------|----------|
| Separate commit, before | Land `dayStart` on `ScheduleConfig` and fill it in `SolverService` first — additive, nothing reads it, provably a no-op. The re-anchoring commit then touches only `DayWindow` and files referencing it, so criterion 5's check is clean and the revert target is exactly the risky edit. Same shape Phase 18 used for the anchored helpers. | ✓ |
| One commit | Everything together; one revert, no ordering to reason about. Costs: criterion 5's file list includes files with no `DayWindow` reference, so it is restated at planning time or fails on a technicality. | |
| Defer the solver channel to Phase 20 | Leave `ScheduleConstraintProvider` on an allowlisted midnight anchor. Keeps Phase 19 smallest. Costs: the largest call-site cluster (23 references) rests on an allowlist entry. | |

**User's choice:** Separate commit, before
**Notes:** Recorded as D-10.

---

## The forward-interval throw

| Option | Description | Selected |
|--------|-------------|----------|
| Save-path refusal | `DayWindow` computes the span without judgement; refusal moves to shift-template and shift-library validation where an operator can act on it. Establishes the seam OVNT-05 lands on in Phase 21. | ✓ |
| Max-span bound in `DayWindow` | Keep a throw, but on span rather than ordering — refuse at or over 1440 minutes. Keeps the loud-failure-at-the-boundary property, needs no new validation surface. Costs: `DayWindow` starts holding an opinion about how long a shift may be, which is desk policy. | |
| Accept the loss | No replacement; any ordering is a legal interval. Smallest change, exactly what criterion 2 asks and nothing more. Costs: removes the only thing between a fat-fingered envelope and a plausible wrong schedule. | |

**User's choice:** Save-path refusal
**Notes:** Verified after the choice that this is not new work.
`ShiftTemplateService.validate:220-233` already refuses a non-forward envelope via
`isForwardWithinDay` at line 228, before line 232 ever reaches `durationMinutes` — so `DayWindow`'s
throw is already unreachable through the template save path. The anchored `isForwardWithinDay`
preserves the behaviour exactly by construction: at a `00:00` anchor `15:00–14:00` gives offsets 900
and 840 (refused) and `22:00–06:00` gives 1320 and 360 (refused, correctly, since overnight templates
need both Phase 20 and OVNT-01). Recorded as D-11 / D-12. This reframes the area: the decision is to
relocate an existing refusal, not to build one.

---

## Criterion 3's reference oracle

Raised by Claude rather than selected by the operator. Criterion 3 asks for output "byte-identical to
the pre-migration implementation", but D-04/D-06 mean that implementation stops being callable code.

| Option | Description | Selected |
|--------|-------------|----------|
| Frozen oracle in the test | Copy today's nine implementations verbatim into a private nested class in `DayWindowTest` and sweep the full input domain, not sampled. Literally satisfies the criterion's wording, cannot go stale, and covers the seven composites that the existing exhaustive proof does not reach. Costs: ~60 lines of duplicated implementation in test code. | ✓ |
| The existing suite is the oracle | Re-point `DayWindowTest`'s existing assertions to the instance API at a midnight anchor; their constants were derived from today's behaviour, so all passing unchanged is the proof. No duplication, smallest diff. Costs: coverage is spot checks for the composites. | |
| Golden reference table | Capture every function's output across its input domain before the edit and assert reproduction. Strongest and deletable later. Costs: reintroduces the golden-file mechanism Phase 18's D-13 explicitly rejected. | |

**User's choice:** Frozen oracle in the test
**Notes:** Recorded as D-13, with D-14 recording that Phase 18's existing anchored test classes move
to the instance API without weakening.

---

## Claude's Discretion

- Exact instance-method naming — whether the five existing `…FromDayStart` primitives shed that
  now-redundant suffix as instance methods. Stated preference: shed it, since after D-06 the
  distinction it draws no longer has two sides.
- Whether `anchoredAt` caches or interns instances, and whether the class stays `final`.
- How the frozen oracle's sweep is bounded for the four-argument `overlaps` and `contains` — a full
  1440⁴ cross-product is not runnable.
- Task ordering and plan decomposition within the two commits D-10 fixes.

## Deferred Ideas

- Extending the constructed midnight-boundary scenarios to a non-midnight anchor — cannot move earlier
  than Phase 20, but Phase 20 should own it explicitly rather than inherit it by accident.
- The three BDAY-06 scenarios Phase 18 deferred (D-16) — Phase 21, once OVNT-01 makes them
  constructible.
- Deleting the frozen oracle once v1.5 ships.
- Building the 15-minute-boundary range validation behind the still-present narrowing — the third
  option raised and not taken under gate ownership; Phase 20 may still want that shape.

### Reviewed Todos (not folded)

All three `todo.match-phase` hits matched on generic keywords only and none touches interval
arithmetic or the day-start anchor: the blank upload template (matched `one`, `desk`, `phase`),
cross-agent seat displacement (matched `atomic`, `phase`), and Terraform RDS password drift (matched
`phase` alone).
