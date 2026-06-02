# Phase 6: Solver Quality Constraints - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-02
**Phase:** 6-Solver Quality Constraints (re-scoped this session to "PTO & Weekends" first)
**Areas discussed:** No-fixed-weekend handling, Non-standard working-days patterns, PTO blocking behaviour

---

## No fixed weekend (Working days = Variable or blank, ~55% company-wide)

| Option | Description | Selected |
|--------|-------------|----------|
| Solver picks 2 contiguous | Solver chooses their weekend (original QUAL-01 intent) | |
| Fully flexible | No enforced days off; schedule by demand + preferences | |
| Flag as data gap | Don't auto-schedule until BambooHR populated; surface to operator | ✓ |

**User's choice:** Flag as data gap.
**Notes:** Reuse the Phase 5 eligibility-exclusion pattern. Risk flagged: ~55% lack the field company-wide — must validate coverage for actually-scheduled desks before shipping (D-07).

---

## Non-standard working-days values (3-day weeks, 0-day weeks, non-consecutive)

| Option | Description | Selected |
|--------|-------------|----------|
| Honour exactly as-is | Days off = complement, however many / non-consecutive | |
| Honour but flag outliers | Block as-is, surface ≠2-contiguous or 0-day patterns to operator | ✓ |
| Normalise to 2 contiguous | Coerce odd patterns to a standard weekend, overriding BambooHR | |

**User's choice:** Honour but flag outliers.
**Notes:** BambooHR is source of truth; block exactly the non-working days, but raise unusual patterns for operator review (D-04, D-05).

---

## PTO blocking behaviour

| Option | Description | Selected |
|--------|-------------|----------|
| Keep as-is | APPROVED hard-blocks; REQUESTED visible-only | ✓ |
| Also block REQUESTED | Treat requested PTO as a block too | |
| Revisit / discuss | Talk through a nuance first | |

**User's choice:** Keep as-is (already shipped in plan 05-03). No change needed (D-08).

---

## Claude's Discretion
- Persistence shape of the per-agent weekly pattern (transient vs stored).
- Exact surfacing mechanism for data-gap / outlier flags (likely extends BambooHR Sync Status / diagnostics).

## Deferred Ideas
- QUAL-02 weekend-position fairness distribution → follow-on phase.
- QUAL-03 day-to-day hours consistency → follow-on phase.
- Formally split Phase 6 into "6a PTO & Weekends" and "6b Fairness & Hours" via /gsd-phase.
