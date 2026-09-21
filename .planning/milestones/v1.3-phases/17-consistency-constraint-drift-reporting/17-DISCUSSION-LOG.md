# Phase 17: Consistency Constraint & Drift Reporting - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-04
**Phase:** 17-Consistency Constraint & Drift Reporting
**Areas discussed:** Distance definition, Tolerance band + weight config, Preference precedence (CONS-05/06), Drift report surface (DRFT-01–04)

---

## Distance definition

### Q1 — What should "deviation from the usual shift" actually measure?

| Option | Description | Selected |
|--------|-------------|----------|
| Start-time delta, minutes | `abs(assigned.startTime − usual.startTime)`. Ordinal, so CONS-02's band has a real unit and DRFT-01's "by how much" reads naturally. Matches the adopted column's name. Blind spot: same start, different end scores zero drift. | ✓ |
| Template identity — same name or not | 0 if names match, else 1. Simplest, matches operator language. But collapses CONS-02 (no magnitude to be inside a band) and degenerates DRFT-01 to yes/no. | |
| Composite — `max(start delta, end delta)` | Catches same-start/different-duration; band keeps a unit. Harder to read, and a wholesale shift move scores identically anyway. | |

**User's choice:** Start-time delta, minutes
**Notes:** Recorded as CONTEXT D-01, rated `costly` — DRFT-03 makes it one function with two callers, and changing it invalidates the D-06 benchmark.

### Q2 — Past the tolerance band, how should the penalty scale?

| Option | Description | Selected |
|--------|-------------|----------|
| Linear per excess increment | `excess minutes / incrementMinutes`, per agent-day. Gives the gradient the roadmap demands; house pattern for every existing soft constraint; increment-based keeps the weight comparable across 15/30/60-minute desks. | ✓ |
| Quadratic in excess increments | Follows PROJECT.md's unbuilt Key Decision. Matches operator intuition, but multiplies with an unvalidated weight and needs tail validation, not just median. | |
| Flat 1 per drifting agent-day | Simplest to weight. Reintroduces a plateau — the exact flaw the reverted attempt's javadoc named. | |

**User's choice:** Linear per excess increment
**Notes:** The quadratic option's supporting Key Decision was reasoned for QUAL-03 hours consistency (deferred to 999.4), not shift starts — surfaced during the question rather than left to be discovered.

### Q3 — Which "start" is the agent's actual start?

| Option | Description | Selected |
|--------|-------------|----------|
| Envelope start | `shiftBandPair.template().getStartTime()`. Pure problem-fact comparison, independent of how seats fell. Consequence: a late first seat inside the Early envelope still reads as zero drift. | ✓ |
| Earliest seated slot | What the reverted `6fb78c7` did. Reflects lived experience, but that was a slot-mode definition; under V44 envelope slack it mixes shift choice with seat placement. | |
| Envelope start, plus flag late first seats | Both signals without distorting the constraint. More to build; arguably Phase 15 contiguity scope. | |

**User's choice:** Envelope start
**Notes:** Operator-language test recorded in CONTEXT `<specifics>`: "Ana was put on Late, not Early."

---

## Tolerance band + weight config

### Q1 — Where should the per-desk tolerance band live?

| Option | Description | Selected |
|--------|-------------|----------|
| `constraint_weights` table | Band and weight in one row, one screen, one API. The only genuinely per-desk config table, matching CONS-02. Cost: it is a `@ConstraintConfiguration` where every non-key column is a `HardSoftScore`, so DTO and page grow a second field shape. | ✓ |
| `Desk` table | Cleanest typing, no DTO change. Cost: band and weight configured on two different screens. | |
| `Schedule` / `SolveRequest` | The actual house pattern for tunable scalars, zero new plumbing. Cost: per-solve, not per-desk — does not satisfy CONS-02 as written. | |

**User's choice:** `constraint_weights` table
**Notes:** CONTEXT D-04, rated `costly`. Flagged as the phase's one deliberate convention break; must be commented as intentional per Phase 16 D-03's house style.

### Q2 — Symmetric band, or early treated differently from late?

| Option | Description | Selected |
|--------|-------------|----------|
| Symmetric — one value | CONS-02 says "a tolerance band", singular. One knob is one criterion-2 validation. Asymmetry is addable later without changing what any stored value means. | ✓ |
| Asymmetric — separate early and late | Captures a real operational asymmetry (early = childcare/commute, late = coverage). Doubles config and validation surface. | |
| Symmetric band, asymmetric weight | Captures the asymmetry where it bites, keeps the band single. Two constraints or a signed multiplier. | |

**User's choice:** Symmetric — one value
**Notes:** Asymmetry moved to CONTEXT `<deferred>` as deferred, not rejected.

### Q3 — How should the shipped default weight be decided?

| Option | Description | Selected |
|--------|-------------|----------|
| Benchmark decides, in this phase | Commit the threshold first (XCUT-04), run the seeded A/B at ~130% over-allocation, read the `explain()` breakdown, then write the result as the migration default. Satisfies criteria 2 and 5 literally. | ✓ |
| Ship inert (0 soft), opt-in per desk | Nothing can regress; matches the project's demonstrated honesty. But the phase goal says the solver IS nudged, and "before a default ships" becomes vacuous. | |
| Keep V38's `0hard/2soft`, adjust only on regression | Most literal "adopt the existing column". But inherits a number reasoned for a different penalty granularity. | |

**User's choice:** Benchmark decides, in this phase
**Notes:** The deciding evidence was surfaced in the question: V38's own comment sizes `2 soft` for a **per-agent** penalty (28 × 4 × 2 = 224 soft); D-02 charges **per agent-day**, so the same desk over five working days reaches ≈1,120 soft — above `minStaffingWeight`'s 1,000, i.e. consistency buying uncovered hours, which is what that comment exists to prevent. Load-bearing plan consequence recorded: the benchmark task **gates** the migration that sets the default.

### Q4 — CONS-04: enforce soft-only, or document it?

| Option | Description | Selected |
|--------|-------------|----------|
| Enforce — reject a hard consistency weight | Service-layer 400, in `setDayHours`'s shape. Makes CONS-04 structural rather than a discipline. Diverges from Phase 15's "hard-vs-soft is a config row" precedent, deliberately. | ✓ |
| Document and warn, like V38 did | Consistent with Phase 15 and with the project's reluctance to block operators. Cost: CONS-04 becomes a claim about the default, with nothing structural for a verifier to point at. | |
| Enforce in the constraint, not the config | CONS-04 holds regardless of what is stored. Cost: the stored value then lies about behaviour — the divergence class audit I-1 was about. | |

**User's choice:** Enforce — reject a hard consistency weight
**Notes:** Divergence from Phase 15 justified on the grounds that for `shiftEnvelopeCompliance`/`bandCapacity` hard is the *correct* setting, whereas here V38's comment documents hard as the known failure mode.

---

## Preference precedence (CONS-05/06)

### Q1 — How should the preference tie-break work, given `honourPreferredStartTime` is gated off in SHIFT mode?

| Option | Description | Selected |
|--------|-------------|----------|
| New shift-granularity constraint, ordering enforced | Separate soft constraint on `abs(envelopeStart − preferredStartTime)`, own weight, ordering enforced at config-save plus a named test. Turns CONS-06's "not implicit in relative weights" into a structural property. | ✓ |
| Search-order tie-break, no score contribution | Value-range ordering or difficulty comparator. Zero weight interaction, trivially CONS-04-safe. But a nudge, not a guarantee, and invisible in `explain()`. | |
| One constraint, lexicographic arithmetic | `(consistency × K) + preference`. Precedence provable by arithmetic. But `explain()` shows one line, so criterion 2's breakdown can't separate the effects. | |

**User's choice:** New shift-granularity constraint, ordering enforced
**Notes:** This question exists because of a codebase finding that contradicts the roadmap: `ScheduleConstraintProvider.java:813` is mode-gated off for SHIFT desks and its javadoc states *"Phase 17's CONS-05 use of `preferredStartTime` at shift granularity is a new use, not a reason to leave this per-slot constraint on."* The roadmap lists `7861b83` (anchor-not-floor) as "transfers as-is"; it patches a constraint that never fires on the desks this phase targets. CONTEXT D-08 revises that entry — the *idea* transfers, the commit does not.

### Q2 — Should the new preference constraint fire for agents with no stored usual shift?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — it applies independently | Consistency is silent for those agents (USHF-04), so preference is their only start-time signal. Also closes a regression Phase 15 opened: gating the per-slot constraint off left preference-holding agents on shift desks with nothing honouring it. | ✓ |
| No — only as a tie-break where a usual shift exists | Matches CONS-05's literal wording; smallest benchmark surface. Cost: leaves the Phase 15 regression unclosed with nothing in v1.3 to close it. | |

**User's choice:** Yes — it applies independently
**Notes:** Reads wider than CONS-05's literal wording; CONTEXT D-09 requires the plan to say so explicitly rather than let it look accidental.

### Q3 — What should CONS-06's "documented and observable" concretely require?

| Option | Description | Selected |
|--------|-------------|----------|
| Enforced invariant + `explain()` lines + page copy | Three artefacts: the save-time rejection, two separate constraint lines in the score breakdown, and copy on the Constraint Weights page. Observable to operator and developer, nothing new built. | ✓ |
| The above, plus per-row attribution in the drift report | Most direct answer to "why did Ana get Late?". Cost: couples the drift report to a second constraint's reasoning, which DRFT-03 exists to prevent. | |
| Documentation + test only | Cheapest and honest about who reads precedence rules. But "observable" then means observable to a developer reading the repo. | |

**User's choice:** Enforced invariant + `explain()` lines + page copy
**Notes:** The rejected per-row attribution option was preserved in CONTEXT `<deferred>`.

---

## Drift report surface (DRFT-01–04)

### Q1 — Derived on read, or snapshotted at solve time?

| Option | Description | Selected |
|--------|-------------|----------|
| Derived on read | `buildDriftReport(schedule)` alongside `buildPreferenceReport`. No table, no migration; `preferenceReport` already behaves this way. Coherent with Phase 16 D-01's name-follows-era semantics. Consequence: a past schedule's report can change when nobody touched that schedule. | ✓ |
| Snapshot at solve time | Immutable per solve, better for audit. Cost: table + migration, and it freezes exactly what D-01 decided not to freeze. | |
| Derived on read, from a target stored on `AgentShiftAssignment` | Snapshot stability without a new table. Cost: makes the solver a writer of usual-shift-derived data, needing a new USHF-05 write-path row and test. | |

**User's choice:** Derived on read
**Notes:** CONTEXT `<specifics>` asks for the surprising consequence to be surfaced in the panel, not only in a code comment.

### Q2 — Row granularity, and how DRFT-02's three states appear

| Option | Description | Selected |
|--------|-------------|----------|
| One row per agent-date, three-state field | Mirrors `PreferenceReportEntry`. State is `NO_USUAL_SHIFT` / `HONOURED` / `DRIFTED` — a field, not an inference from a null. Same shape Phase 16 D-16 gave the roster tile. Date filtering free from the existing pattern. | ✓ |
| One row per agent, aggregated across the week | Reads faster; closer to the reverted `6fb78c7`. But DRFT-01 asks for "on which dates", so dates end up nested anyway. | |
| Per-agent-date rows plus a per-agent summary block | Most informative. Introduces a third summary level the codebase does not have. | |

**User's choice:** One row per agent-date, three-state field

### Q3 — Where should DRFT-04's over-subscription view live?

| Option | Description | Selected |
|--------|-------------|----------|
| Second section of the same drift tab | Keeps the fairness tension next to the drift it explains. Needs a heading noting it reads stored usual shifts, not solve results. | ✓ |
| Its own tab | Cleaner conceptually. A whole tab for one small table, and it separates tension from symptom. | |
| On the Shift Library page instead | Most actionable placement. But DRFT-04 is worded as post-solve, and it puts Phase 17 work into a Phase 14 surface. | |

**User's choice:** Second section of the same drift tab
**Notes:** The Shift Library placement was preserved in CONTEXT `<deferred>`.

### Q4 — Should the drift report be written into the Excel export?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — a sheet, like the preference report | `ScheduleExportService` already writes the preference report to its own sheet; XCUT-01 names "export" as a surface. Small, well-precedented. | ✓ |
| No — UI panel only | DRFT-01 asks only for a panel. Cost: XCUT-01's "every surface" becomes a judgement call — the ambiguity class that produced audit I-1. | |

**User's choice:** Yes — a sheet, like the preference report

---

## Claude's Discretion

- Rounding mode for the minutes→increments conversion (must reuse HALF_UP or CEILING, not introduce a third).
- Whether the penalty is skipped on days the agent does not work (Phase 16 D-04 stores a usual shift there, inert).
- Mode gating for both new constraints, following `shiftEnvelopeCompliance`'s documented stream-ordering performance contract.
- Constraint names, weight-column names, DTO shape, endpoint paths, test organisation.
- Migration number — head on disk is V47, so next is V48; confirm the live head first.
- Whether the band's own default minutes value is benchmark-derived or set by judgement.
- How the band renders on the Constraint Weights page as the only non-`ScoreDto` field among 22.

## Deferred Ideas

- Asymmetric tolerance (early vs late) — deferred under D-05, not rejected.
- A matching shift-granularity preferred-**break**-time constraint — `honourPreferredBreakTime` is mode-gated off exactly as the start-time one is, and the reverted `9f4a96f` was this. A real hole Phase 17's requirements do not cover.
- Any mitigation for the over-subscription DRFT-04 makes visible — explicitly out of scope (FAIR-01, Backlog 999.4).
- Per-row attribution in the drift report marking preference-resolved ties — rejected under D-10.
- Subscription counts on the Shift Library page — rejected under D-13 as Phase 14 surface.
- Composite start+end distance — rejected under D-01; revisit if operators report same-start/different-duration drift going unseen.
