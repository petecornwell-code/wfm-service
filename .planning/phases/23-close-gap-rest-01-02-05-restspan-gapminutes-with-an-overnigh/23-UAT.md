---
status: testing
phase: 23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh
source: [23-VERIFICATION.md]
started: 2026-10-04T21:20:44Z
updated: 2026-10-04T21:20:44Z
---

## Current Test

number: 1
name: D5 — a live solve against an overnight-template desk no longer scores 0hard
expected: |
  The hard score is no longer `0hard`, and `Minimum rest (shift)` fires — or, in SLOT mode, the
  pre-solve refusal fires naming a smaller best-achievable gap.
  Read the result through `/summary`, never `GET /schedules/{id}` (~4 MB payload, and it competes
  with the solver for its two cores).
awaiting: user response

## Tests

### 1. D5 — a live solve against an overnight-template desk no longer scores 0hard

Setup: on a real desk with an overnight shift template and a configured minimum rest of 660 minutes,
construct a roster where an agent works the overnight template on one business date and starts around
07:00 the next business date. Start a solve.

expected: The hard score is no longer `0hard` and `Minimum rest (shift)` fires (or the SLOT-mode
pre-solve refusal fires, naming a smaller best-achievable gap). Read via `/summary`, never
`GET /schedules/{id}`.
why_human: Needs a real solve against a desk with an overnight shift template; no unit fixture
substitutes for a live Timefold solve, and running one competes with the application for its two
cores. Deliberately deferred by plan 23-03 (`human_judgment: true`) to end-of-phase UAT harvest per
the project's `end-of-phase` default.
result: [pending]

## Summary

total: 1
passed: 0
issues: 0
pending: 1
skipped: 0
blocked: 0

## Gaps
