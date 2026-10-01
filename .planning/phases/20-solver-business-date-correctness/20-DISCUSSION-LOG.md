# Phase 20: Solver Business-Date Correctness - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-01
**Phase:** 20-solver-business-date-correctness
**Areas discussed:** The `00:00`-only gate, `agent_shift_assignment`'s date, Guard scope and allowlist, How correctness is proven

---

## The `00:00`-only gate

### Does Phase 20 delete the gate, and if so where in the phase?

| Option | Description | Selected |
|--------|-------------|----------|
| Last commit of Phase 20 | Honours all three documents while closing the mid-phase window — every join business-date-keyed and guarded before any operator can set a non-midnight anchor | ✓ |
| Re-point it to OVNT-01 (Phase 21) | Keep the gate; a `21:00` desk is unusable until overnight templates exist, so opening the anchor in Phase 20 ships a half-capability on live data | |
| Lift backend, keep UI read-only | Delete the validation line so API and tests reach a `21:00` anchor; leave the cell non-editable | |

**User's choice:** Last commit of Phase 20 → **D-01**
**Notes:** The deciding argument was that 19-CONTEXT D-01's refusal ("an operator can set a `21:00` anchor and accept a plausibly-wrong schedule") does not stop at the phase boundary — it applies equally to a gate deleted in Phase 20's first commit.

### What replaces the validation line when it goes?

| Option | Description | Selected |
|--------|-------------|----------|
| 15-minute boundary check at save | `minute % 15 == 0` refusal in `setDayStart`; Phase 18's D-08 generation-time tiling refusal becomes the reachable backstop | ✓ |
| Delete outright, rely on D-08 | No save-time check; generation time is the only place that knows the real increment | |
| Both, as one condition | Same as option 1, argued explicitly as honouring 18-CONTEXT D-07's one-visible-line intent | |

**User's choice:** 15-minute boundary check at save → **D-02**
**Notes:** The boundary check is increment-independent, which is what makes save-time validation possible at all — the increment is not desk state (`FteUploadService:127`).

### What does Phase 20 do with the `DeskManagement.tsx` day-start cell?

| Option | Description | Selected |
|--------|-------------|----------|
| Correct the copy, cell stays read-only | Smallest honest change; editable control needs a picker plus two error surfaces, which is Phase 21 work | ✓ |
| Make it editable in this phase | Offer the control the moment the backend accepts a non-midnight anchor | |
| Leave the copy alone | Treat the cell as Phase 21's problem entirely | |

**User's choice:** Correct the copy, cell stays read-only → **D-03**
**Notes:** `:112` and `:124` both currently state something that becomes false when D-01 lands; Phase 18's disclosure requirement exists to prevent exactly that drift.

### Phase 20's five ROADMAP criteria never mention the gate. How does it become checkable?

| Option | Description | Selected |
|--------|-------------|----------|
| Add it as a sixth criterion | Makes the deletion visible to `gsd-verifier` rather than resting on the decision-coverage gate | ✓ |
| Fold it into criterion 1 | Extend the SOLV-01/SOLV-02 criterion's wording instead of adding one | |
| Leave the criteria as they are | CONTEXT.md decision only | |

**User's choice:** Add it as a sixth criterion → **D-04**

---

## `agent_shift_assignment`'s date

### Does it get its own `business_date` column?

| Option | Description | Selected |
|--------|-------------|----------|
| No column — its date already IS the business date | Derived through `AgentDayConfig` from the schedule period, which 18-CONTEXT D-22 locks as business dates; the three joins re-point the Timeslot side only | ✓ (Claude's call) |
| Add a stored `business_date` column | Explicit in schema; costs a migration, a backfill, and a second business-date write path | |
| No column, but rename the field | Keeps derivation and fixes comprehension; costs a wide mechanical diff | |

**User's choice:** *"What is most scaleable and maintainable"* — delegated to Claude → **D-05**
**Notes:** Decided against a column on three grounds recorded in CONTEXT.md D-05: the entity is rebuilt every solve so a column is a denormalised copy of a derived value and breaks V41's "can never disagree by construction"; it creates a second business-date write path against BDAY-08's one-derivation-site rule; and it forces a backfill for ACCEPTED rows that V41 deliberately freezes, with no correct value. The rename option was costed and rejected *for this phase only* — it reads honestly only across all six business-date-shaped facts (~200 accessor sites). Deferred, not dismissed.

### How is the claim "`agent_shift_assignment.date` means business date" kept true?

| Option | Description | Selected |
|--------|-------------|----------|
| Guard test on the derivation chain | Structural assertion that each link sources only from the one above; fails the build on a future calendar-date writer | ✓ |
| Javadoc citing SOLV-01, no new guard | Cheapest; relies on convention REQUIREMENTS itself calls insufficient | |
| Fold into the SOLV-07 anchor-agreement guard | Extend one guard rather than write a second class | |

**User's choice:** Guard test on the derivation chain → **D-06**
**Notes:** Stands in the same relationship to D-05's *derived* value that BDAY-08's write-path guard stands in to `Timeslot`'s *stored* column.

### How is `PENDING_DESK_ANCHOR`'s 19-site migration handled, given the ROADMAP omits it?

| Option | Description | Selected |
|--------|-------------|----------|
| Seventh criterion, its own deliverable | Constant gone, allowlist entry gone, guard red if either returns | ✓ |
| Explicit inside criterion 2 | Restate the break-band/contiguity criterion to name the 19 sites | |
| CONTEXT.md decision only | Let the plan's `must_haves` carry it | |

**User's choice:** Seventh criterion, its own deliverable → **D-07**
**Notes:** Surfaced during this discussion. `ScheduleConstraintProvider:58-66` names `SOLV-01` as the removal owner in its own javadoc and is listed in `midnight-time-arithmetic.md`, so Phase 19 handed it forward deliberately — but a planner reading only ROADMAP + REQUIREMENTS would size SOLV-03 as test-only.

---

## Guard scope and allowlist

### What does `BusinessDateJoinGuardTest` actually police?

| Option | Description | Selected |
|--------|-------------|----------|
| Join/group expressions in both files | Scans `join`/`equal`/`groupBy`/`computeIfAbsent` key positions only; display out of scope by construction, not by allowlist | ✓ |
| `ScheduleConstraintProvider` only | Narrower structural guard plus a separate behavioural test for SOLV-07 | |
| Every `.getDate()` in both files, with an allowlist | Widest net; risks the 100+-entry decoration failure 18-CONTEXT D-03 measured | |

**User's choice:** Join/group expressions in both files → **D-08**
**Notes:** Resolves the ROADMAP's "final allowlist contents" open decision: plausibly empty for key positions, plausibly non-empty for display — which is why the scope is drawn at key positions rather than at `.getDate()` occurrences.

### Which tokens does the guard scan?

| Option | Description | Selected |
|--------|-------------|----------|
| Only `.getDate()` on a `Timeslot` receiver | `Timeslot` is the only type with two competing dates; the other tokens would fire on unambiguous types | ✓ |
| All four tokens from 18-CONTEXT D-04 | Catches weekday derivation and date arithmetic; larger allowlist and a per-token receiver heuristic | |
| `.getDate()` plus `.getDayOfWeek()` | Middle path aimed at the `agent_day_hours` weekday-keying risk | |

**User's choice:** Only `.getDate()` on a `Timeslot` receiver → **D-09**
**Notes:** This token *does* catch `ShiftLibraryGenerationService`'s weekday sites, because they are spelled `sr.getTimeslot().getDate().getDayOfWeek()`.

### What do the two operator-facing timeslot labels show after this phase?

| Option | Description | Selected |
|--------|-------------|----------|
| Unchanged — calendar date, documented as deliberate | A label answers "when does this happen", a calendar question; all labelling change deferred to OVNT-07 | ✓ |
| Business date, matching the grouping it sits under | Removes intra-row inconsistency; worsens the ambiguity OVNT-07 exists to disclose | |
| Both dates, now | Pulls OVNT-07 forward; operator-visible format change in a "changed nothing it shouldn't have" phase | |

**User's choice:** Unchanged — calendar date, documented as deliberate → **D-10**

---

## How correctness is proven

### Does Phase 20 own extending `MidnightBoundaryFixture` to a `21:00` anchor?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — and they land before the migration | Red first, then migrate until green; makes the migration falsifiable, follows the Phase 18/19 guards-first pattern | ✓ |
| Yes — after the migration, as proof | Easier to author; reintroduces the two-candidate-causes failure 19-CONTEXT warned about | |
| No — unit-level anchored proof is enough | Leaves scenario-level `21:00` proof to Phase 21 | |

**User's choice:** Yes — and they land before the migration → **D-11**
**Notes:** 19-CONTEXT's deferred list hands this forward explicitly, with the two-candidate-causes argument as its reason.

### How is "one deliberate pass" reconciled with "decomposed enough that a failure names which constraint moved"?

| Option | Description | Selected |
|--------|-------------|----------|
| One commit, per-constraint assertions | "One pass" is the commit; "which constraint" is SOLV-06's assertion set, not commit granularity | ✓ |
| One commit per constraint cluster | Independently revertible and bisectable; "one pass" becomes a claim about the plan, not the history | |
| Two commits: joins, then anchors | Separates the two migrations with different failure modes; `shiftEnvelopeCompliance` and contiguity are touched by both | |

**User's choice:** One commit, per-constraint assertions → **D-12**

### What happens to `ShiftLibraryGenerationService`'s two calendar-weekday derivations?

| Option | Description | Selected |
|--------|-------------|----------|
| Third file in the guard, migrate now | Leaving a known instance outside scope makes the guard's green misleading | ✓ |
| Guard covers it, migration goes to Phase 21 | Allowlist with a named removal owner — the `PENDING_DESK_ANCHOR` pattern Phase 19 used | |
| Record as a deferred idea only | Cheapest; nothing fails the build if the deferred list is not read | |

**User's choice:** Third file in the guard, migrate now → **D-13**
**Notes:** Surfaced during this discussion. `:180` and `:486` bucket demand by calendar weekday, so a `02:00` Monday-business-day slot on a `21:00` desk lands under Tuesday. No Phase 20 success criterion names library generation, so this widens scope by the operator's explicit choice.

### How is BDAY-07's drift measured?

| Option | Description | Selected |
|--------|-------------|----------|
| Local harness against a copy of Phil-US data | Both solves run locally against a dump; no load on dev, before-run pinned to a commit | |
| A test-scope fixture, no live data | CI-reproducible; stops being a live-drift guard | ✓ |
| Add a lightweight match-count endpoint | Reusable; new operator-reachable API surface in a correctness phase | |

**User's choice:** A test-scope fixture, no live data

### Follow-up: how does BDAY-07 reconcile with that?

| Option | Description | Selected |
|--------|-------------|----------|
| Rewrite BDAY-07 as a constructed drift guard | Amend BDAY-07 and criterion 5 to say a Phil-US-*shaped* constructed fixture; amendment visible in the roadmap | ✓ |
| Keep BDAY-07 live, defer it to Phase 21 | Requirement wording intact; Phase 20 loses a requirement | |
| Both — fixture now, live run as a manual gate | Keeps the wording; the live half is unrepeatable and racing a deploy | |

**User's choice:** Rewrite BDAY-07 as a constructed drift guard → **D-14**
**Notes:** Claude flagged that a test-scope fixture satisfies neither BDAY-07's nor criterion 5's wording, and that REQUIREMENTS records the constructed-vs-live conflation as v1.4's cancellation cause. The reconciliation keeps the choice but moves the requirement with it. What distinguishes D-14 from v1.4's mistake: constructed *to Phil-US's shape*, not captured from its bytes, and asserted on per-constraint match counts rather than golden bytes. The live half is knowingly given up for v1.5.

---

## Claude's Discretion

- The spelling and structure of the per-constraint match-count assertion set across 26 constraints (one parameterised table vs per-constraint assertions), with `ScheduleConstraintClassification` as the precedent.
- Whether `BusinessDateJoinGuardTest` is a new class or a fourth scan inside `MidnightTimeArithmeticGuardTest`.
- Whether `ScheduleConfig`'s defensive null-anchor fallback survives this phase or is tightened.
- Task ordering within D-12's commit boundary, and where SOLV-05's per-business-day shortfall reporting lands in it.
- The wording of the three ROADMAP/REQUIREMENTS amendments (D-04, D-07, D-14).

## Deferred Ideas

- Rename `date` → `businessDate` across all six business-date-shaped problem facts (~200 accessor sites).
- An editable day-start control in the desk-configuration UI — Phase 21.
- OVNT-07's calendar-span label disclosure — Phase 21.
- Tightening `ScheduleConfig`'s defensive null-anchor fallback.
- Restoring a genuine live-desk drift check for Phil-US — natural home is MIGR-01..04.
- The three BDAY-06 scenarios Phase 18 deferred — Phase 21, once OVNT-01 makes them constructible.
- Deleting the frozen `DayWindow` oracle once v1.5 ships.

### Offered and declined

At the final check Claude offered three further gray areas; the operator chose to proceed to CONTEXT.md:
the per-constraint assertion-set maintenance burden, the `ScheduleConfig` null-anchor fallback's fate,
and the Phase 21 handoff shape. The first two are recorded under Claude's Discretion above.
