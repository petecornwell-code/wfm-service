---
phase: quick-261008-eby
plan: 01
subsystem: solver-pre-solve-validation
tags: [rest, requireRestFeasibility, CR-01, REST-03, REST-05, tdd]
requires: []
provides:
  - "SolverService.preHorizonPredecessor: date-keyed horizon-edge guard for both requireRestFeasibility branches"
affects: [SolverService.requireRestFeasibility]
tech-stack:
  added: []
  patterns: ["pre-horizon span keyed on its own businessDate (== periodStart - 1)"]
key-files:
  created: []
  modified:
    - src/main/java/com/wfm/service/SolverService.java
    - src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java
    - .planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-REVIEW-DISPOSITION.md
decisions:
  - "Guard on prior.businessDate().equals(dMinus1) rather than adding a periodStart parameter: no signature or call-site ripple"
metrics:
  duration: ~10m
  completed: 2026-10-08
status: complete
requirements: [REST-03, REST-05]
actuals:
  tokens: 9000
  tasks: 3
  commits: 3
plan_head_before: 37af3c7bfc36358becce4b95d4057208344105c7
plan_head_after: 99da6adcdab2348f905089db8216dc82bd516c3d
---

# Phase quick-261008-eby Plan 01: Fix CR-01 pre-horizon rest fallback Summary

`requireRestFeasibility` now consults the pre-horizon `RestSpan` only when it is dated D-1, via a new `preHorizonPredecessor` helper used by both the SHIFT and SLOT branches, so a working day after a mid-horizon day off is no longer falsely refused against pre-horizon history.

## Commits

| Task | Commit | Message |
| ---- | ------ | ------- |
| 1 RED | 8e17e83 | test: add failing CR-01 horizon-edge tests |
| 2 GREEN | 5e454e8 | fix: scope pre-horizon fallback to the true horizon edge (CR-01) |
| 3 docs | 99da6ad | docs: record CR-01 fixed in 23-REVIEW-DISPOSITION |

## What changed

- Four new tests in `RestFeasibilityRefusalTest` (SHIFT and SLOT no-refusal, SHIFT and SLOT horizon-edge controls). The RED run failed exactly those 4 of 24, in the predicted modes: the no-refusal tests threw a false refusal against the pre-horizon span, and the controls reported 2 details instead of 1.
- `SolverService.preHorizonPredecessor(priorSpanByAgent, agentId, dMinus1)` returns the span only when `businessDate().equals(dMinus1)`. It is the only reader of the agent-keyed prior-span map. Both branches call it; null keeps the existing `continue` path. Javadoc and comments updated.
- `23-REVIEW-DISPOSITION.md`: CR-01 `fixed` in frontmatter and table, `open: 3`, `total: 4`.

## Verification

Targeted classes only (full suite deliberately not run), all green:
RestFeasibilityRefusalTest 24, RestPredecessorServiceTest 12, RestWaiverDisclosureTest 31, RestWaiverPredicateGuardTest 8, RestWaiverServiceTest 15, DeskServiceMinimumRestTest 14, RestGapArithmeticGuardTest 6, MidnightTimeArithmeticGuardTest 12, RestWaiverFetchingFinderGuardTest 4, MinimumRestShiftConstraintTest 24, MinimumRestSlotConstraintTest 19, RestHorizonEdgeTest 12. Zero failures, errors or skips.

Non-comment grep gates: `preHorizonPredecessor(` x3, `priorSpanByAgent.get(` x1, `businessDate().equals(dMinus1)` x1.

ScheduleConstraintProvider, ScheduleOutputService and RestPredecessorService were not touched.

## Deviations from Plan

None - plan executed exactly as written. (`commits: 3` counts the three plan commits measured before this summary was committed; the summary commit follows.)

## Known Stubs

None.

## Threat Flags

None.

## Self-Check: PASSED

Commits 8e17e83, 5e454e8, 99da6ad exist; modified files present.
