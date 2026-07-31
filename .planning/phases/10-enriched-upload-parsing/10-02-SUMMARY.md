---
phase: 10-enriched-upload-parsing
plan: 02
subsystem: upload-parsing
tags: [java, util, poi, header-mapping, single-source-of-truth]

# Dependency graph
requires:
  - phase: 10-enriched-upload-parsing (plan 01)
    provides: D-12 recurring day-off storage foundation (agent_day_hours.day_off_type)
provides:
  - EnrichedColumnLayout — single source of truth for enriched per-desk column order/names (D-13)
  - identityHeaders()/dayHeader()/specialtyIndex()/normalize() shared header-resolution API
  - Retired-shape marker constants (RETIRED_COL_DESK, LEGACY_HEADER_DESK_ASSIGNMENT) for shape detection
affects: [10-03-parser, 10-04-template-and-export]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single-source-of-truth utility class for header order/names, consumed by parser/template/export"
    - "normalize() formalizes the existing trim+lowercase header-key convention from DeskAssignmentUploadService"

key-files:
  created:
    - src/main/java/com/wfm/util/EnrichedColumnLayout.java
    - src/test/java/com/wfm/util/EnrichedColumnLayoutTest.java
  modified: []

key-decisions:
  - "specialtyIndex() takes an already-normalized (trim+lowercase) header string per the RESEARCH.md/PATTERNS.md verbatim proposal, mirroring how callers already build lowercase-keyed header maps"

patterns-established:
  - "No enriched-shape header string literal may be hardcoded outside EnrichedColumnLayout — parser (plan 03) and template/export (plan 04) must resolve headers through this class"

requirements-completed: [UPL-02, UPL-09]

# Metrics
duration: 6min
completed: 2026-07-31
---

# Phase 10 Plan 02: EnrichedColumnLayout Summary

**Shared `EnrichedColumnLayout` utility (identity headers, day headers, unbounded Specialty-N detection, normalize, retired-shape markers) that closes the D-13 header-drift design tension across parser/template/export.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-07-31T21:20:xx (approx, see git log)
- **Completed:** 2026-07-31
- **Tasks:** 2 completed
- **Files modified:** 2 (both new)

## Accomplishments
- Created `EnrichedColumnLayout` with the seven identity header constants, `DAY_ORDER`, `dayHeader()`, `identityHeaders()`, `specialtyIndex()` (case-insensitive, whitespace-tolerant `^specialty\s*(\d+)$`), and `normalize()`.
- Added retired-shape marker constants (`RETIRED_COL_DESK`, `LEGACY_HEADER_DESK_ASSIGNMENT`) so downstream shape-classification (plan 03) never hardcodes retired header literals.
- Added full unit coverage pinning specialty-index parsing across case/whitespace/non-match, day-header title-casing at both week boundaries, identity-header count/order/content, and normalize's trim+lowercase+null behavior.

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement EnrichedColumnLayout utility** - `359d2a3` (feat)
2. **Task 2: EnrichedColumnLayout unit test** - `a1c7f27` (test)

**Plan metadata:** pending (docs: complete plan)

## Files Created/Modified
- `src/main/java/com/wfm/util/EnrichedColumnLayout.java` - Single source of truth for enriched column order/names, specialty detection, and retired-shape markers
- `src/test/java/com/wfm/util/EnrichedColumnLayoutTest.java` - Unit coverage (JUnit 5 + AssertJ, no Spring context)

## Decisions Made
- Followed the verbatim class proposal from RESEARCH.md/PATTERNS.md exactly (constants, regex, method signatures) — no deviation needed since the plan fully specified the API shape.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `EnrichedColumnLayout` is ready for plan 03 (parser rewrite: header→index via `specialtyIndex`/`normalize`, shape detection via `RETIRED_COL_DESK`/`LEGACY_HEADER_DESK_ASSIGNMENT`) and plan 04 (template generator: index→header via `identityHeaders`/`dayHeader`; export: shared identity header text).
- No blockers. `./gradlew test --tests "com.wfm.util.EnrichedColumnLayoutTest"` passes (8 tests, all green).

---
*Phase: 10-enriched-upload-parsing*
*Completed: 2026-07-31*

## Self-Check: PASSED

- FOUND: src/main/java/com/wfm/util/EnrichedColumnLayout.java
- FOUND: src/test/java/com/wfm/util/EnrichedColumnLayoutTest.java
- FOUND commit: 359d2a3
- FOUND commit: a1c7f27
