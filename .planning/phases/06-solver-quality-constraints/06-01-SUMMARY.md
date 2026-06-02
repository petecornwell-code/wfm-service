---
phase: "06-solver-quality-constraints"
plan: "01"
subsystem: "integration"
tags: ["tdd", "parser", "bamboohr", "working-days", "qual-01"]
dependency_graph:
  requires: []
  provides: ["WorkingDaysParser.parseWorkingDays", "WorkingDaysParser.offDaysFrom", "WorkingDaysParser.isStandardTwoContiguousDaysOff"]
  affects: ["com.wfm.integration", "BambooRefreshService (plan 06-02)", "SolverService.filterEligible (plan 06-03)"]
tech_stack:
  added: []
  patterns: ["token-normalise-expand", "package-private static utility (BigDecimals shell)", "JUnit 5 @ParameterizedTest @MethodSource"]
key_files:
  created:
    - src/main/java/com/wfm/integration/WorkingDaysParser.java
    - src/test/java/com/wfm/integration/WorkingDaysParserTest.java
  modified: []
decisions:
  - "Token-normalise-expand strategy chosen over regex dispatch (more defensible when new formats appear)"
  - "Package-private final class with private constructor (BigDecimals shell) — no Spring dependency"
  - "parseWorkingDays returns Optional.empty() for all unrecognised/garbage input; never throws (T-6-IV)"
  - "isStandardTwoContiguousDaysOff uses Sun-Mon wrap: idx0==0 && idx1==6 is the adjacent-wrap case"
  - "14 test cases: 10 positive (all live BambooHR formats) + 1 garbage + 3 data-gap (Variable/blank/null)"
metrics:
  duration: "~5 minutes"
  completed: "2026-06-02"
  tasks_completed: 2
  tasks_total: 2
  files_created: 2
  files_modified: 0
---

# Phase 06 Plan 01: WorkingDaysParser (TDD) Summary

Tolerant BambooHR `customWorkingdays` (field 4517) parser via RED/GREEN TDD cycle. Provides the engineering core of QUAL-01; all downstream plans (02: field plumbing, 03: MANDATORY row generation) depend on this contract.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 0 | Rotate BambooHR API key | BYPASSED (operator directive) | — |
| 1 (RED) | WorkingDaysParserTest — failing test | `8508032` | `src/test/java/com/wfm/integration/WorkingDaysParserTest.java` |
| 2 (GREEN) | WorkingDaysParser implementation | `2f0971c` | `src/main/java/com/wfm/integration/WorkingDaysParser.java` |

## TDD Gate Compliance

- RED gate commit: `8508032` — `test(06-01): add failing WorkingDaysParserTest (RED)` — compilation failed only because `WorkingDaysParser` was undefined.
- GREEN gate commit: `2f0971c` — `feat(06-01): implement WorkingDaysParser (GREEN)` — all 14 test cases pass.

## What Was Built

`WorkingDaysParser` — package-private `final` class in `com.wfm.integration`:

- **`parseWorkingDays(String raw)`** — tolerant parser; returns `Optional<Set<DayOfWeek>>`. Pipeline: (1) null/blank guard -> empty; (2) "Variable" (case-insensitive) guard -> empty; (3) normalise `\s+to\s+` -> `-`; (4) strip trailing non-day annotations (HOOP etc.); (5) dispatch to comma-list or range parser; (6) garbage/unrecognised -> empty (never throws).
- **`offDaysFrom(Set<DayOfWeek> workingDays)`** — `EnumSet.allOf` minus working days.
- **`isStandardTwoContiguousDaysOff(Set<DayOfWeek> offDays)`** — true iff exactly 2 off-days adjacent in WEEK_ORDER (Mon=0..Sun=6) with Sun-Mon wrap.

## Verification

```
./gradlew test --tests "com.wfm.integration.WorkingDaysParserTest"
BUILD SUCCESSFUL — 14 tests, 0 failures, 0 errors
```

All 14 rows green:

| Input | Expected |
|-------|----------|
| `Mon-Fri` | {MON,TUE,WED,THU,FRI} |
| `Wed-Sun` | {WED,THU,FRI,SAT,SUN} |
| `Sun-Thu` | {SUN,MON,TUE,WED,THU} |
| `Tue-Sat` | {TUE,WED,THU,FRI,SAT} |
| `Fri-Tue` | {FRI,SAT,SUN,MON,TUE} (week-wrap) |
| `Mon - Sun` | all 7 (0 off-days outlier) |
| `Mon - Sun HOOP` | all 7 (annotation stripped) |
| `Mon. to Thurs.` | {MON,TUE,WED,THU} |
| `Mon, Tue, Wed, Thu, Sat` | {MON,TUE,WED,THU,SAT} |
| `xyz garbage 123` | empty Optional |
| `Variable` | empty Optional |
| `variable` | empty Optional (case-insensitive) |
| `""` | empty Optional |
| `null` | empty Optional |

## Deviations from Plan

### Task 0 Bypassed (Operator Directive)

**Task 0 (BambooHR API key rotation) was BYPASSED by operator directive on 2026-06-02.** The exposed key rotation gate remains OPEN and MUST be completed before any Phase 6 deploy.

- Full key value remains committed in `.planning/codebase/CONCERNS.md`, `02-01-PLAN.md`, `02-RESEARCH.md` (operator chose "leave as-is").
- `git grep -i ad2bb` acceptance criterion is NOT met by design.
- **This is a blocking pre-deploy requirement (T-6-SC / Elevation of Privilege).** No Phase 6 plan should be deployed to production until the BambooHR API key for the helpware tenant (prefix `ad2bb…2be`) is rotated and stored in the secret manager.

### Auto-added Test Cases (Rule 2)

Added two test rows not in the plan spec:
- `"variable"` (lowercase) — ensures case-insensitive "Variable" guard works both ways
- `"xyz garbage 123"` — exercises the garbage-input-to-empty-Optional path (acceptance criterion in plan: "Parser throws on no input in the test catalog (manually confirm a garbage row like 'xyz' would return empty, not throw — add it to the catalog if helpful)")

These are correctness requirements per T-6-IV and the plan's own suggestion.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. `WorkingDaysParser` is a pure in-memory static utility with no I/O. The threat model entry T-6-IV (parser must never throw) is fully mitigated.

## Known Stubs

None. This plan creates a standalone static utility with no downstream wiring (that is plan 02's scope). The contract is fully implemented and tested.

## Self-Check: PASSED

- `src/main/java/com/wfm/integration/WorkingDaysParser.java` — FOUND
- `src/test/java/com/wfm/integration/WorkingDaysParserTest.java` — FOUND
- RED commit `8508032` — FOUND
- GREEN commit `2f0971c` — FOUND
- `./gradlew test --tests "com.wfm.integration.WorkingDaysParserTest"` — PASSED (14/14)
