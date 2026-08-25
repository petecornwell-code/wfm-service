---
phase: 09-agent-data-model-foundation
plan: 01
subsystem: database
tags: [jpa, hibernate, agent-model, java, junit5, assertj]

# Dependency graph
requires: []
provides:
  - "AgentNameSplitter.split(displayName) -> Split(firstName, lastName): shared D-06 first-whitespace name-split rule"
  - "Agent.firstName / Agent.lastName scalar columns (first_name / last_name), nullable, alongside the existing name column"
affects: [09-04-bamboohr-name-split-integration, 09-05-upload-name-split-integration, 09-06-flyway-migration-and-dto-export]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Static stateless utility class (final, private ctor, static method) mirroring BigDecimals.java structural template"
    - "JUnit 5 @ParameterizedTest + @MethodSource for data-driven split-rule coverage"

key-files:
  created:
    - src/main/java/com/wfm/util/AgentNameSplitter.java
    - src/test/java/com/wfm/util/AgentNameSplitterTest.java
    - src/test/java/com/wfm/model/AgentNamePersistenceTest.java
  modified:
    - src/main/java/com/wfm/model/Agent.java

key-decisions:
  - "firstName/lastName are nullable at the entity level (no nullable=false) so pre-migration rows can reload with null until the future V29 Flyway backfill runs"
  - "No @OneToMany collection added to Agent; sibling child-table pattern (AgentDayOff/AgentException/AgentPreference) stays repository-loaded, not JPA-navigated"

patterns-established:
  - "AgentNameSplitter is now the single shared implementation of the D-06 split rule — downstream plans (BambooHR refresh, upload write-sites, Flyway migration SQL) must call this utility rather than re-implementing the rule"

requirements-completed: [MDL-01]

# Metrics
duration: 15min
completed: 2026-07-30
---

# Phase 9 Plan 01: Agent Name Split Foundation Summary

**AgentNameSplitter utility implementing the D-06 first-whitespace split rule, plus Agent.firstName/lastName JPA columns, both proven by passing tests.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-07-30T14:20:00Z (approx)
- **Completed:** 2026-07-30T14:35:59Z
- **Tasks:** 2 completed
- **Files modified:** 4 (2 new source, 2 new test)

## Accomplishments
- `AgentNameSplitter.split(String)` implements the exact D-06 rule (first whitespace token → firstName, trimmed remainder → lastName; null/blank → empty Split; single-token → empty lastName, never null), proven by a 7-case parameterized test plus 3 explicit assertions
- `Agent` now has `firstName`/`lastName` scalar columns mapped to `first_name`/`last_name`, proven by a passing `@DataJpaTest` round-trip
- Both tasks followed strict RED→GREEN TDD: each test was confirmed failing (compile error) before the corresponding implementation was written

## Task Commits

Each task was committed atomically, split into RED/GREEN TDD commits:

1. **Task 1: AgentNameSplitter utility**
   - `67917fe` test(09-01): add failing test for AgentNameSplitter D-06 split rule (RED)
   - `0f27f45` feat(09-01): implement AgentNameSplitter D-06 split rule (GREEN)
2. **Task 2: Add firstName/lastName columns to Agent**
   - `e93b416` test(09-01): add failing persistence test for Agent firstName/lastName (RED)
   - `82cf1e9` feat(09-01): add firstName/lastName scalar columns to Agent (GREEN)

_No REFACTOR commits were needed — both GREEN implementations matched the target shape from PATTERNS.md on first pass._

## Files Created/Modified
- `src/main/java/com/wfm/util/AgentNameSplitter.java` - Static utility, D-06 split rule, structurally mirrors `BigDecimals.java`
- `src/test/java/com/wfm/util/AgentNameSplitterTest.java` - Data-driven test (7 parameterized cases + 3 explicit assertions) covering all behavior-block cases
- `src/main/java/com/wfm/model/Agent.java` - Added `firstName`/`lastName` fields (`@Column(name = "first_name"/"last_name")`) + accessor pairs after `name`/`getName()`/`setName()`; `name` and `contractedHoursPerDay` untouched; no `@OneToMany` added
- `src/test/java/com/wfm/model/AgentNamePersistenceTest.java` - `@DataJpaTest` round-trip mirroring `AgentEmploymentTypePersistenceTest`

## Decisions Made
- Followed PATTERNS.md's recommended shape verbatim for both the utility and the entity changes — no deviation from the researched/verified insertion points was needed
- Kept `firstName`/`lastName` nullable per plan instruction (explicitly do NOT add `nullable = false`), since the future V29 migration is the intended backfill point, not entity defaults

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. The worktree's initial base commit was stale (predated the `fa2d719` phase-plan commit); corrected via `git reset --hard` to the expected base per the branch-check step before any file reads, per protocol.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `AgentNameSplitter` and `Agent.firstName`/`Agent.lastName` are ready for Plan 09-04 (BambooHR refresh integration) and Plan 09-05 (upload write-sites) to call/populate
- Plan 09-06 (Flyway V29 migration + DTO/export exposure) can now rely on the entity shape being final for the name-split portion of MDL-01
- No blockers or concerns for downstream plans

## Self-Check: PASSED

All created files verified present on disk:
- FOUND: src/main/java/com/wfm/util/AgentNameSplitter.java
- FOUND: src/test/java/com/wfm/util/AgentNameSplitterTest.java
- FOUND: src/main/java/com/wfm/model/Agent.java
- FOUND: src/test/java/com/wfm/model/AgentNamePersistenceTest.java
- FOUND: .planning/phases/09-agent-data-model-foundation/09-01-SUMMARY.md

All commit hashes verified present in `git log`:
- FOUND: 67917fe (test RED, Task 1)
- FOUND: 0f27f45 (feat GREEN, Task 1)
- FOUND: e93b416 (test RED, Task 2)
- FOUND: 82cf1e9 (feat GREEN, Task 2)
- FOUND: b718ea7 (docs, SUMMARY)

---
*Phase: 09-agent-data-model-foundation*
*Completed: 2026-07-30*
