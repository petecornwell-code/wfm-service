---
status: complete
phase: 23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh
source: [23-VERIFICATION.md]
started: 2026-10-04T21:20:44Z
updated: 2026-10-08T14:04:52.863Z
---

## Current Test

[testing complete]

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
result: pass
note: |
  Run live 2026-10-08 on a throwaway SHIFT desk (day start 06:00 — the API refuses 22:00-06:00 at a
  00:00 anchor), Night 22:00-06:00 Mon / Early 07:00-15:00 Tue, min rest 660. POST /solve returned
  400 restFeasibility: "achieves only 60 minute(s) of rest against a required 660". No 0hard.
  Caveat accepted by user: at an overnight anchor the Night span ends exactly on the business-day
  boundary (no wrap), so this run does not by itself discriminate pre-fix vs post-fix arithmetic;
  the wrapped case is covered by the unit fixtures.

## Summary

total: 1
passed: 1
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps
