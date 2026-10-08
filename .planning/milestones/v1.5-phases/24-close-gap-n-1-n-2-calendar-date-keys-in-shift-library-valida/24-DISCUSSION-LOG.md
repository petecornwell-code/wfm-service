# Phase 24: Close gap N-1/N-2 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-07
**Phase:** 24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida
**Areas discussed:** N-2 fix shape, Validator message dates, Sweep the same pattern, Proving N-2 is fixed

---

The operator answered the area-selection prompt with "recommended decisions"; no per-area
questions were asked. Claude took the recommended option in each area, after verifying code facts.

## N-2 fix shape

| Option | Description | Selected |
|--------|-------------|----------|
| Additive `businessDate` field + server-side business-range params | `date` stays calendar per D-10 | ✓ |
| Frontend derives business date from desk day start | Violates BDAY-08 single derivation | |
| Widen fetch by a day, filter client-side | Still needs a business-date field | |

## Validator message dates

| Option | Description | Selected |
|--------|-------------|----------|
| Business date, calendar disclosed when different | Matches grid ordering and D-10's intent | ✓ |
| Calendar date only | Mislabels the weekday a restriction applies to | |
| Business date only | Hides when the hour actually happens | |

## Sweep the same pattern

| Option | Description | Selected |
|--------|-------------|----------|
| Classify all sites; fix proven defects red-first; extend the join guard | Scouting found a key mismatch in ScheduleEnvelopeRepairService | ✓ |
| N-1/N-2 only | Leaves a confirmed same-class mismatch | |

## Proving N-2 is fixed

| Option | Description | Selected |
|--------|-------------|----------|
| Backend contract test + live local check via Playwright evaluate | No new tooling | ✓ |
| Add vitest | Scope creep (WR-04) | |

## Claude's Discretion

All four areas; plus wording, param naming, comparator handling.

## Deferred Ideas

- Frontend test runner (WR-04).
