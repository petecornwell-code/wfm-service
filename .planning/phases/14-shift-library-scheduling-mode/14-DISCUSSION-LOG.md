# Phase 14: Shift Library & Scheduling Mode - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-25
**Phase:** 14-Shift Library & Scheduling Mode
**Areas discussed:** Break placement rule, Validator depth & severity, Template lifecycle, Mode switch mechanics

---

## Area selection

| Option | Description | Selected |
|--------|-------------|----------|
| Break placement rule | What shape SHLB-01's "break placement rule" takes on the row | ✓ |
| Validator depth & severity | How deep SHLB-05 coverage goes; whether SHLB-06 blocks | ✓ |
| Template lifecycle | Edit/retire/effective-date semantics vs accepted schedules | ✓ |
| Mode switch mechanics | Reversibility, in-flight solves, UI placement | ✓ |

**User's choice:** All four areas.

### Todo cross-reference

| Option | Description | Selected |
|--------|-------------|----------|
| Fold none (Recommended) | All three matches are keyword noise; cross-agent-seat-displacement is explicitly unlinked from any phase by PROJECT.md | ✓ |
| Fold cross-agent-seat-displacement | Contradicts its own frontmatter warning | |
| Fold blank-upload-template | Overlaps Phase 16 (USHF-02) | |

**User's choice:** Fold none.

---

## Break placement rule

### Q1 — What shape does the break placement rule take on the row?

| Option | Description | Selected |
|--------|-------------|----------|
| Fixed offset from start (Recommended) | `break_offset_minutes` + `break_duration_minutes`; fully determined, zero solver freedom; makes ENVL-05 mechanically true and lets the four break constraints be gated off | ✓ |
| Fixed clock time | Absolute `break_start_time`/`break_end_time`; redundant with start/end and needs separate envelope validation | |
| Offset window (solver picks) | `break_earliest_offset`/`break_latest_offset`; preserves optimiser freedom but keeps break a live solver decision and materially enlarges Phase 15 | |
| Offset + reuse Schedule break config | Template stores only the offset; duration/alignment stay on Schedule; least schema but no per-template break duration | |

**User's choice:** Fixed offset from start.

### Q2 — How do template times relate to the timeslot grid?

| Option | Description | Selected |
|--------|-------------|----------|
| Validate at save + re-check at switch (Recommended) | Reject misaligned templates at save; re-run inside the mode-switch gate so a later `incrementMinutes` change surfaces as a named failure | ✓ |
| Store freely, no grid validation | Interval containment tolerates misalignment; a lost partial slot only shows up as an under-hours agent after a Phase 15 solve | |
| Fix templates to a coarse grid | Constrain to 15 min regardless of the desk's grid; portable but excludes a 20-min-grid desk | |

**User's choice:** Validate at save + re-check at switch.
**Notes:** `TimeslotGeneratorService` takes `incrementMinutes` per generation and its own comments state the grid can be refined later — so alignment is not a constant that can be assumed.

### Q3 — Must the template's break satisfy Schedule's existing break config?

| Option | Description | Selected |
|--------|-------------|----------|
| Template is self-contained (Recommended) | No validation against Schedule break config; the four emergent break constraints are gated off for shift desks by ENVL-05, so validating against them validates a dead rule | ✓ |
| Validate against latest Schedule's config | Look up the desk's most recent Schedule (the Phase 13 schedule-derived-default pattern); uniform semantics but nothing to validate against on a new desk | |
| Promote break config to Desk now | Add the four break fields to `Desk`; stable target but touches the existing solve path's config resolution — a MODE-05 risk | |

**User's choice:** Template is self-contained.

**Continue check:** Next area.

---

## Validator depth & severity

### Q1 — How deep does the SHLB-05 / MODE-03 coverage check go?

| Option | Description | Selected |
|--------|-------------|----------|
| Structural envelope coverage (Recommended) | Every demanded `(date, timeslot)` must fall inside some active template's envelope on a valid weekday; uncovered slots named. Headcount stays with the existing `computeCapacityWarnings` | ✓ |
| Capacity-aware | Adds a bin-packing check that agents × shifts can meet FTE demand; a mini-solve whose approximation will sometimes wrongly block the operator | |
| Structural gate + capacity warning | Structural blocks, capacity advises; builds both and invites "it said I had enough and the solve still failed" | |

**User's choice:** Structural envelope coverage.

### Q2 — What date window, and what happens with no demand loaded?

| Option | Description | Selected |
|--------|-------------|----------|
| Live demand, empty = refuse (Recommended) | Live timeslots + live `StaffingRequirement` (`scheduleId IS NULL`) ∩ effective ranges; zero demand rows REFUSES rather than passing vacuously | ✓ |
| Live demand, empty = allow | Any setup order permitted; the gate is silently vacuous exactly when the desk is least configured | |
| Caller-supplied date range | Explicit period params; adds a knob but the mode-switch gate still needs a default, so the question remains | |

**User's choice:** Live demand, empty = refuse.
**Notes:** Follows the rule already recorded for Backlog 999.5 — missing demand data is reported as missing, never as a clean zero.

### Q3 — How hard does the SHLB-06 hours mismatch bite?

| Option | Description | Selected |
|--------|-------------|----------|
| Advisory always, except the fatal case (Recommended) | Non-blocking warning on save and in the library view; mode switch refuses only when a demanded weekday has no workable `(template, agent)` pair at all | ✓ |
| Advisory everywhere | Cleanest reading of "reported", but a desk can be switched into a provably unsolvable state | |
| Blocking at save | Strongest guarantee; makes the library unbuildable before a roster exists and creates an XCUT-02 write-path trap | |

**User's choice:** Advisory always, except the fatal case.

### Q4 — What counts as a "match"?

| Option | Description | Selected |
|--------|-------------|----------|
| Exact equality (Recommended) | `BigDecimal` compare via the existing `BigDecimals` util; mirrors the 1,001-hard / 100-hard contracted-hours constraints it predicts | ✓ |
| Tolerance band (± one timeslot) | Fewer spurious warnings; an unpinned guess about solver behaviour that will eventually mask a real mismatch | |
| Configurable tolerance per desk | A knob with no evidence behind it, sitting confusingly beside Phase 17's genuine per-desk tolerance band | |

**User's choice:** Exact equality.

**Continue check:** Next area.

---

## Template lifecycle

### Q1 — What protects an already-accepted schedule from a later edit?

| Option | Description | Selected |
|--------|-------------|----------|
| Snapshot on accept (Recommended) | Mutable rows; protection from the existing `acceptSchedule` snapshot pattern (`scheduleId` null = live, non-null = frozen), which Phase 15's `AgentShiftAssignment` inherits | ✓ |
| Immutable versioning | Edit writes a new row, references pin to a version; full history but a second identity concept for two downstream FKs to reason about | |
| Immutable once referenced | Free editing until referenced, then retire-and-recreate; templates freeze exactly when they become useful | |

**User's choice:** Snapshot on accept.

### Q2 — Does the row carry both an active flag and an effective date range?

| Option | Description | Selected |
|--------|-------------|----------|
| Date range only (Recommended) | `effective_from` + nullable `effective_to`; retire = set `effective_to`. One mechanism, one predicate — avoids the two-fields-that-disagree trap of audits NEW-1 and I-1 | ✓ |
| Both, with distinct roles | Four states where two would do; "active but out of range" needs defined and tested behaviour | |
| Active flag only | Drops SHLB-03's date range — a scope reduction against an explicit milestone requirement | |

**User's choice:** Date range only.

### Q3 — What is the template's identity?

| Option | Description | Selected |
|--------|-------------|----------|
| Unique (tenant, desk, name, effective_from) (Recommended) | "S1" can exist in two eras with non-overlapping ranges — what SHLB-03 is for. Known consequence: Phase 16's `agent_usual_shift` FK points at a specific row, so superseding strands it; recorded as an explicit hand-off | ✓ |
| Unique (tenant, desk, name) | Mirrors `Specialization` exactly; every FK unambiguous forever, but SHLB-03 becomes decorative | |
| UUID only, name is a label | Maximum flexibility; two "Early" templates become indistinguishable in roster, export, and DRFT-04's over-subscription report | |

**User's choice:** Unique (tenant, desk, name, effective_from).

**Continue check:** Next area.

---

## Mode switch mechanics

### Q1 — Can a desk switch back to SLOT, and is that gated?

| Option | Description | Selected |
|--------|-------------|----------|
| Freely reversible, ungated (Recommended) | Slot mode has no prerequisite and MODE-04 protects accepted schedules; REQUIREMENTS.md records per-desk optionality as the pilot strategy — a fallback you cannot take is not a fallback | ✓ |
| Reversible with a confirm | A dialog on a non-destructive action trains click-through, the way audit I-3's `confirm()` became mitigation in name only | |
| One-way once a shift schedule is accepted | Locks rollback exactly on the desk that most needs it | |

**User's choice:** Freely reversible, ungated.

### Q2 — What happens if the mode is flipped during a RUNNING solve?

| Option | Description | Selected |
|--------|-------------|----------|
| Refuse with 409 (Recommended) | Reuses `BambooRefreshService`'s per-`deskId` in-progress guard idiom; prevents accepting a slot-model schedule into a `SHIFT`-flagged desk | ✓ |
| Allow — the solve is already detached | Data-level correct (facts detach under `readOnly`), but leaves an accepted schedule whose shape doesn't match its desk's mode, with nothing recording why | |
| Allow and stop the solve | Silently destroys minutes of solver work on a click that doesn't look destructive; `STOPPED` is itself a legitimate accept state | |

**User's choice:** Refuse with 409.

### Q3 — Where do the library and the toggle live?

| Option | Description | Selected |
|--------|-------------|----------|
| New Shift Library page, toggle on it (Recommended) | `ShiftLibrary.tsx` mirroring `Specializations.tsx`, carrying the toggle and coverage panel; the refusal is actionable next to the library that fixes it. `DeskManagement.tsx` shows mode read-only | ✓ |
| Library page, toggle on DeskManagement | Literal MODE-02 reading; the refusal lands on a page with no library to fix | |
| Everything inside DeskManagement | One screen; `DeskManagement.tsx` (124 lines) absorbs a full CRUD surface plus a validation panel — Phase 13's comparable work ran to 6 plans | |

**User's choice:** New Shift Library page, toggle on it.

### Q4 — What form does the XCUT-05 classification table take?

| Option | Description | Selected |
|--------|-------------|----------|
| Classification map + completeness test (Recommended) | Key set asserted equal to what `defineConstraints` registers, so a 20th constraint fails the build until classified; markdown table mirrored for humans | ✓ |
| Markdown table only | Reads well, zero machinery; a snapshot nothing keeps honest — the failure mode XCUT-05 exists to prevent | |
| Javadoc annotation per constraint | Lives next to the code; no completeness enforcement, and Phase 15 reassembles the table by grep | |

**User's choice:** Classification map + completeness test.

**Continue check:** Ready for context.

---

## Claude's Discretion

- Zero-duration break permitted; never more than one break per template
- MODE-05 proven by the existing 315-test backend suite running unchanged, not a new slot-mode fixture
- `ErrorDetail` wording and field naming for uncovered windows
- Whether the coverage validator is a standalone service or a method on an existing one
- How a template's valid-weekday set is stored on the row
- Exact Flyway migration number — confirm latest-applied before writing, do not assume V39

## Deferred Ideas

- Multiple breaks per template
- Capacity-aware coverage validation (candidate for a future reporting phase, adjacent to Backlog 999.5)
- Promoting break config from `Schedule` to `Desk`
- Per-desk tolerance on the hours match (do not pre-empt Phase 17's consistency tolerance band)
- Re-pointing `agent_usual_shift` when a template is superseded — created by D-11, owned by Phase 16

### Reviewed Todos (not folded)

- `2026-08-13-cross-agent-seat-displacement.md` — explicitly unlinked from any phase by PROJECT.md; Phase 15 measures the same gap and does not close it
- `2026-07-30-blank-upload-template-one-sheet-per-desk.md` — overlaps Phase 16 (USHF-02)
- `2026-08-14-terraform-db-password-drift.md` — infrastructure, unrelated
