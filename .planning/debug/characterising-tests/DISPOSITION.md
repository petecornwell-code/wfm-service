# Characterising Tests — Disposition

This directory is a **historical record only**. None of the four files below is a live guard —
every one of them was written to prove a *defect existed*, run once against unmodified production
source, and left in place beneath this file as the evidence the diagnosis was executable. If any
file in this directory is green today, that proves nothing about current correctness: read the
row below for the regression test that actually carries the guarantee now, and check that one
instead. A future reader who finds one of these files still passing and mistakes that for
coverage has misread this directory's purpose.

All four files were produced during the G-15-10 debug session
(`.planning/debug/shift-envelope-unsatisfiable-hard.md`,
`.planning/debug/min-staffing-seats-zero-demand.md`,
`.planning/debug/shift-mode-break-geometry-ungoverned.md`) and are referenced from
`.planning/phases/15-shift-envelope-breaks-library-generation/15-UAT.md`'s `characterising_tests`
field. Gap closure ran as five plans, 15-09 through 15-13; the table below is this round's final
account of what happened to each file.

## Disposition table

| Characterising file (this directory) | Diagnosed defect | Disposition | Regression test | Plan |
|---|---|---|---|---|
| `ShiftModeMinimumStaffingSeatGapTest.java` | D2 — `expandMinimumStaffingSeats` is structurally envelope-blind (no `SchedulingMode`/`ShiftBandPair` in its signature); manufactures filler seats on zero-demand hours no shift envelope reaches | **Fully promoted.** Inverted from "passes on the defect" to a permanent regression suite proving the envelope-aware branch. | `src/test/java/com/wfm/service/ShiftModeMinimumStaffingSeatSupplyTest.java` | 15-09 |
| `ZeroDemandTimeslotHasNoCeilingGapTest.java` | Companion to D2 — a zero-demand timeslot gets no `TimeslotDemandConfig` row, so both bulk over/under-allocation constraints are silent there (absence of a ceiling, not a ceiling of zero) | **Fully promoted**, with the falsification control retained and a note added explaining why the missing-ceiling fact is now safe (filler seats are envelope-suppressed at those hours, plan 15-09) rather than a hazard. | `src/test/java/com/wfm/solver/ZeroDemandTimeslotCeilingTest.java` | 15-09 |
| `ShiftEnvelopeUnsatisfiableHardTest.java` | D1 — zero-slack-by-construction (the value range's exact netHours-equals-effectiveHours filter leaves no margin) combined with D2's seat-supply gap makes an irreducible hard score inevitable once triggered | **Fully promoted**, renamed to state the system is now HELD to the invariant rather than merely observed to exhibit it. Two LEMMA cases kept and reframed as the invariant's proof; three "hard score cannot reach zero" cases inverted (one now solves feasibly, two now refuse by name before any solve); the weight-ladder case corrected to record the tie at `ofHard(1)` as a deliberate, measured, unchanged fact. | `src/test/java/com/wfm/solver/ShiftEnvelopeSupplyInvariantTest.java` | 15-11 |
| `ShiftModeBreakGeometryCharacterisationTest.java` | D3/D4 (break-geometry lane) — once the slot-mode break constraints are mode-gated off, nothing in SHIFT mode prices per-agent break geometry directly; a scattered/fragmented break pattern costs the same as an operationally sane one, and (separately) the report layer relabelled every seat-gap as a "break" | **Partially promoted, partially superseded, one case intentionally dropped** — see below. | `src/test/java/com/wfm/solver/ShiftModeBreakGeometryGuardTest.java` (4 of 5 cases) **plus** `src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java` (the 5th case's replacement) **plus** `src/test/java/com/wfm/solver/ShiftDeskEndToEndRegressionTest.java` (the property the guard file explicitly does NOT prove) | 15-10, 15-13 |

## The one file with a mixed disposition, in detail

`ShiftModeBreakGeometryCharacterisationTest.java` had five cases. They did not all go the same way:

1. **`shiftMode_gatedBreakConstraints_areSilentOnEveryGeometry`** — kept, reframed as a guard: the
   mode gate genuinely works.
2. **`slotMode_exactlyOneBreak_ranksTheThreeGeometries`** — kept, reframed as a guard: SLOT mode
   still ranks the geometries strictly, proving the gated signal was real, not dead code.
3. **`shiftMode_scatteredAndEdge_scoreIdenticallyEverywhere`** — kept, RE-LABELLED. It still proves
   the flatness is real (no gradient distinguishes a sane hole placement from a scattered one while
   the solve is already infeasible), but the class javadoc now states the settled disposition: this
   is a cosmetic property of an already-broken schedule, not the cause of the breakage. The remedy
   is to prevent the infeasibility (plan 15-11's seat-supply gate), not to price the geometry.
   Restoring the gated slot-mode break constraints in SHIFT mode was considered during gap closure
   and rejected — it would fight the envelope model and could make an under-supplied desk
   permanently unsolvable rather than cleanly refused. If ever revisited, it is a soft tie-break
   candidate at most, never a hard constraint.
4. **`shiftMode_envelopeCompliance_pricesTheSeatNotTheHole`** — kept, reframed as a guard: envelope
   compliance prices the illegal seat, never the compensating hole it forces.
5. **`reportLayer_gapDerivedBreaks_relabelEveryHoleAsABreak`** — **dropped, not ported.** Plan 15-10
   replaced the behaviour this case characterised (the report layer now reads the authoritative
   template span and band-derived break instead of re-deriving both from seat gaps). Porting the
   old case forward would leave a live test asserting that a seat gap is a break — the exact defect
   15-10 fixed. Its replacement lives in
   `ScheduleOutputServiceShiftReportingTest#buildAgentSchedule_strayOutOfEnvelopeSeat_reportsExactlyOneBandShapedBreak`
   and its sibling cases in the same class.

The surviving four cases landed as `ShiftModeBreakGeometryGuardTest.java`
(`src/test/java/com/wfm/solver/`), whose class javadoc states plainly what it proves (the mode
gate works, and SHIFT mode has no per-agent geometry term) and what it does not (that anything
replaces that term) — the property that actually governs an agent's non-worked time at feasibility
is a conjunction of three OTHER hard constraints, asserted directly and observably by
`ShiftDeskEndToEndRegressionTest` (plan 15-13, Task 1), named from the guard file's javadoc so the
two are discoverable from each other.

## Not touched by this disposition

`.planning/debug/shift-mode-break-geometry-ungoverned.md`'s RC-2 finding (blocked-break-hours has
no enforcement point in SHIFT mode) and `.planning/debug/shift-envelope-unsatisfiable-hard.md`'s T3
finding (a template's envelope is never validated against the desk's operating window at save
time) are both real, latent, and deliberately out of scope for this round — see
`.planning/phases/15-shift-envelope-breaks-library-generation/deferred-items.md` (plan 15-13,
Task 3) for the filed record.
