---
phase: 19-daywindow-re-anchoring
plan: 07
subsystem: solver
tags: [daywindow, bday-04, timefold, constraint-stream, allowlist, structural-guard]

requires:
  - phase: 19-daywindow-re-anchoring
    provides: "19-03's DayWindow.anchoredAt(LocalTime) and the nine anchored* instance methods; 19-06's eight-service horizontal expansion and its deliberately deferred ShiftLibraryGenerationService.expandForSupply call site"
provides:
  - "ScheduleConstraintProvider's seven interval-arithmetic helpers (countNonBreakHoles, slotMinutes, getGapLengths, countContiguousGaps, totalGapSlots, findBreakStart, deriveIncrement, plus getShiftEnd's comparator) each take a trailing DayWindow parameter -- the anchor is visible at every call site"
  - "ScheduleConstraintProvider.PENDING_DESK_ANCHOR -- the single named, javadoc'd, allowlisted midnight anchor the six ifExists(ScheduleConfig)-gated constraints pass, with SOLV-01 recorded as the owner of its removal"
  - "FteSpreadsheetGenerator and SolverService's remaining direct DayWindow calls migrated to the instance API -- zero deprecated-static calls and zero LocalTime.MIDNIGHT literals left in SolverService"
  - "midnight-time-arithmetic.md's third allowlist section (Permitted midnight anchors) and MidnightTimeArithmeticGuardTest's third set-equality scan, proven able to go red in both directions"
  - "ShiftLibraryGenerationService.expandForSupply migrated off the deprecated static (plan 19-06's deferred call site), landed together with its matching comparison-allowlist entry"
affects: [19-08-daywindow-reanchoring]

actuals:
  tokens: 13325
  tasks: 3
  commits: 3
  plan_head_before: b8562eaa844db5cd18405742c9bbbf6a20d1787d

tech-stack:
  added: []
  patterns:
    - "One named, documented, allowlisted midnight-anchor constant (PENDING_DESK_ANCHOR) standing in for a desk's real anchor at every ifExists(ScheduleConfig.class, filtering(...))-gated constraint -- not twenty scattered midnight literals. The constant's javadoc, not the allowlist entry alone, carries the full reason and the removal owner."
    - "Helpers that compute an interval take a trailing DayWindow parameter, same shape plans 19-04/19-05/19-06 already established -- extended here to the solver's own package-private helper methods, not just service-layer methods."
    - "The guard's own markdown documents its blind spot (D-05): it cannot see int arithmetic or int comparisons at all, so hand-composing an interval from the anchored minute-offset primitives at a call site would pass every scan while reimplementing half-open interval semantics outside DayWindow."

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/solver/ScheduleConstraintProvider.java
    - src/test/java/com/wfm/solver/MidnightGapScanTest.java
    - src/main/java/com/wfm/service/SolverService.java
    - src/main/java/com/wfm/util/FteSpreadsheetGenerator.java
    - src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
    - src/test/resources/midnight-time-arithmetic.md
    - src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java

key-decisions:
  - "Six of the seven ifExists(ScheduleConfig)-gated constraints actually reach a helper needing PENDING_DESK_ANCHOR -- exactlyOneBreak, breakDuration, breakBlockedWindow, breakStartAlignment, shiftWorkContiguity, honourPreferredBreakTime. honourPreferredBreakTime's deriveIncrement/findBreakStart calls needed it; honourPreferredStartTime's filter (a.getTimeslot().getStartTime().isBefore(p.getPreferredStartTime())) calls no DayWindow helper at all and was left untouched, per the plan's own 'whichever of them actually reach a helper' qualifier."
  - "SolverService.runPreSolveValidation's two remaining duration calls resolve their anchor through two DIFFERENT, deliberate channels, not one: the raw-timeslot increment check (reading first.getStartTime()/getEndTime()) takes a propagated DayWindow parameter from the desk-anchored window solve() already binds (P-02 rule 5); the coverage-window check (reading schedule.getStartTime()/getEndTime(), the Schedule's own fields) binds its own window from schedule.getScheduleConfig().dayStart(), the established Schedule.getScheduleConfig() read path. Total anchoredAt() call count in the file: 2, matching the plan's explicit ceiling."
  - "ShiftLibraryGenerationService.expandForSupply's deferred call site (plan 19-06's hand-off) is migrated in THIS plan's Task 3 commit, not Task 2 -- migrating it before the matching allowlist entry exists would turn the full-suite gate red between Task 2 and Task 3 (confirmed by running the suite with the migration applied early: 1134/1/0/4, the comparison-allowlist test the sole failure). Reverted, redone atomically with the allowlist update in Task 3."
  - "The third allowlist section's four entries cover every anchoredAt(LocalTime.MIDNIGHT) binding present in the tree at this point in the phase, not only the two this plan's own Tasks 1-2 added -- ShiftBandPair.netHours() (plan 19-04/19-05 transitional delegate) and ShiftLibraryGenerationService.resolveBreakConfig's zero-schedule fallback (plan 19-06) had no allowlist to land in before this section existed. The plan's action text named only two families; 'enumerate the actual lines present in the tree' (the plan's own instruction) is what the fourth and fifth-looking entries follow."
  - "getShiftEnd (not named in the plan's helper list) was also converted to take a DayWindow parameter, replacing its DayWindow::endMinute method reference with window::anchoredEndMinute -- its deprecated-static method reference doesn't match the literal grep[.](...)[(] acceptance-criteria pattern, but leaving it would have broken compilation the moment plan 19-08 demotes the statics to private. Low-risk, in-scope extension of the same mechanical migration."

requirements-completed: []

coverage:
  - id: D1
    description: "Every DayWindow-using helper in ScheduleConstraintProvider takes a DayWindow parameter, making visible at each call site which anchor a constraint computes against."
    requirement: BDAY-04
    verification:
      - kind: other
        ref: "grep -vE comment-lines | grep -oE 'DayWindow[.](startMinute|endMinute|durationMinutes|isForwardWithinDay|overlaps|contains|startsBefore|toLocalTime|plusWithinDay)\\(' src/main/java/com/wfm/solver/ScheduleConstraintProvider.java -- 0"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/solver/MidnightGapScanTest.java (6 tests, all nine call sites updated, no asserted value changed)"
        status: pass
      - kind: integration
        ref: "./gradlew test (full, unfiltered) after Task 1 -- 1134 tests, 0 failures, 0 errors, 4 skipped"
        status: pass
    human_judgment: false
  - id: D2
    description: "The two constraints whose stream carries ScheduleConfig (shiftEnvelopeCompliance, breakClustering) compute against the desk's real anchor; the six whose stream cannot carry it pass one named, documented, allowlisted PENDING_DESK_ANCHOR constant that SOLV-01 removes."
    requirement: BDAY-04
    verification:
      - kind: other
        ref: "grep -c 'private static final DayWindow PENDING_DESK_ANCHOR' ScheduleConstraintProvider.java == 1; grep -c 'cfg.dayStart()' == 2 (unchanged); grep -c 'ifExists(ScheduleConfig' == 9 (unchanged -- no gated stream converted to a join)"
        status: pass
    human_judgment: false
  - id: D3
    description: "FteSpreadsheetGenerator and SolverService's remaining direct DayWindow calls are migrated to the instance API; SolverService reaches a real desk anchor at every call site and holds zero LocalTime.MIDNIGHT literals."
    requirement: BDAY-04
    verification:
      - kind: other
        ref: "completeness grep == 0 for both files; grep -c 'LocalTime.MIDNIGHT' SolverService.java == 0; grep -c 'anchoredAt' SolverService.java == 2 (one desk binding, one schedule-config binding); grep -c 'Desk' FteSpreadsheetGenerator.java == 0"
        status: pass
      - kind: integration
        ref: "./gradlew test (full, unfiltered) after Task 2 -- 1134 tests, 0 failures, 0 errors, 4 skipped"
        status: pass
    human_judgment: false
  - id: D4
    description: "midnight-time-arithmetic.md's third allowlist section and MidnightTimeArithmeticGuardTest's third set-equality scan are live, two-directional, and proven able to go red on both an unlisted new occurrence and a stale entry."
    requirement: BDAY-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java -- 12 tests (up from 9), including midnightAnchorsInProductionCode_matchesTheMidnightAnchorAllowlistExactly, theMidnightAnchorScanDetectsAFreshOccurrence, and midnightAnchorScan_failsOnBothAnUnlistedOccurrenceAndAStaleEntry"
        status: pass
      - kind: other
        ref: "grep -c '^### ' midnight-time-arithmetic.md == 6; third section's 4 entries match the guard's own comment-stripped scan (the literal `grep -rc 'anchoredAt(LocalTime.MIDNIGHT)' src/main/java` sum is 5, not 4 -- see Deviations)"
        status: pass
      - kind: integration
        ref: "./gradlew test (full, unfiltered) after Task 3 -- 1137 tests, 0 failures, 0 errors, 4 skipped"
        status: pass
    human_judgment: false
  - id: D5
    description: "The allowlist document records, in its own text, that the guard cannot see int arithmetic or int comparisons, naming DayWindow as the only correct home for interval semantics."
    requirement: BDAY-04
    verification:
      - kind: other
        ref: "src/test/resources/midnight-time-arithmetic.md 'What is enforced' section -- new 'The guard's blind spot (D-05)' paragraph states this explicitly"
        status: pass
    human_judgment: false
  - id: D6
    description: "All eight Phase 18 guard tests stay green, and the full unfiltered suite is green at every task boundary."
    requirement: BDAY-04
    verification:
      - kind: integration
        ref: "MidnightBoundaryRegressionTest (6), MidnightBoundaryPropertyTest (10), BusinessDateWritePathGuardTest (8), TimeslotGeneratorBusinessDateTest (9), MidnightGapScanTest (6), MidnightWindowSeamTest (17), MidnightTimeArithmeticGuardTest (12), DayWindowTest (61) -- all 0 failures, 0 errors, individually confirmed after Task 3"
        status: pass
      - kind: integration
        ref: "./gradlew test (full, unfiltered), run after each of the three task commits -- 1134/1134/1137 tests, 0 failures, 0 errors, 4 pre-existing skips throughout"
        status: pass
    human_judgment: false

duration: 65min
completed: 2026-10-01
status: complete
---

# Phase 19 Plan 07: Solver Constraints, The Standalone Generator, And The Third Allowlist Section Summary

**`ScheduleConstraintProvider`'s seven interval-arithmetic helpers now take an explicit `DayWindow`, with one named `PENDING_DESK_ANCHOR` constant standing in wherever Timefold's arity cap keeps `ScheduleConfig` out of reach; `FteSpreadsheetGenerator` and `SolverService` reach the instance API cleanly; and `midnight-time-arithmetic.md`'s third allowlist section plus its matching two-directional guard scan turn every midnight anchor left in production source into a named, reviewable, removable line.**

## Performance

- **Duration:** ~65 min
- **Started:** 2026-10-01T04:40:00Z
- **Completed:** 2026-10-01T05:43:44Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments
- `ScheduleConstraintProvider`'s `countNonBreakHoles`, `slotMinutes`, `getGapLengths`, `countContiguousGaps`, `totalGapSlots`, `findBreakStart`, `deriveIncrement` (plus `getShiftEnd`, not named in the plan but converted for the same reason) each gained a trailing `DayWindow` parameter, eliminating all 20+ bare deprecated-static calls in the file — the per-file completeness gate now reads `0`.
- `PENDING_DESK_ANCHOR` — one `private static final DayWindow`, bound to `LocalTime.MIDNIGHT`, javadoc'd with the full Penta-stream reasoning and `SOLV-01` named as the removal owner — is passed from the six `ifExists(ScheduleConfig)`-gated constraints that actually reach a helper needing it (`exactlyOneBreak`, `breakDuration`, `breakBlockedWindow`, `breakStartAlignment`, `shiftWorkContiguity`, `honourPreferredBreakTime`). `honourPreferredStartTime` was left untouched — it calls no `DayWindow` helper at all. The two constraints already carrying `cfg` (`shiftEnvelopeCompliance`, `breakClustering`, wired by plans 19-03/19-04) were not touched.
- `MidnightGapScanTest`'s nine direct static-helper calls now pass a named `MIDNIGHT` constant (`DayWindow.anchoredAt(LocalTime.MIDNIGHT)`); every asserted value is unchanged.
- `FteSpreadsheetGenerator` — the one production file with zero `Desk` references and its own `main` — binds one `DayWindow` per `generate()` call from `LocalTime.MIDNIGHT` and routes all six midnight-implicit calls through it.
- `SolverService`'s two remaining direct duration calls are migrated without adding a third `anchoredAt()` binding: `runPreSolveValidation` gains a trailing `DayWindow` parameter fed the already-bound desk window (P-02 rule 5 — propagate outward), used for the raw-timeslot increment check; the coverage-window check binds its own window from `schedule.getScheduleConfig().dayStart()`, the established read path. `SolverService` now holds zero `LocalTime.MIDNIGHT` literals.
- `ShiftLibraryGenerationService.expandForSupply`'s one call site deliberately deferred by plan 19-06 is migrated off the deprecated static, landed in the same commit as the allowlist's matching comparison-entry update (not split across commits, to avoid a red gate between tasks).
- `midnight-time-arithmetic.md` gains a third `### Permitted midnight anchors` section (4 entries: `FteSpreadsheetGenerator`, `ScheduleConstraintProvider.PENDING_DESK_ANCHOR`, `ShiftLibraryGenerationService.resolveBreakConfig`'s zero-schedule fallback, `ShiftBandPair.netHours()`'s transitional delegate) and a "What is enforced" paragraph stating the guard's `int`-arithmetic blind spot (D-05) in its own words.
- `MidnightTimeArithmeticGuardTest` gains a third heading constant, token list, set-equality scan, fresh-occurrence red-proof, and a dedicated both-directions red-proof — 9 → 12 tests, all green.
- Full unfiltered `./gradlew test` is green after every task: **1134 → 1134 → 1137 tests, 0 failures, 0 errors, 4 pre-existing skips**, with all eight Phase 18 guard test classes individually confirmed green.

## Task Commits

Each task was committed atomically:

1. **Task 1: Make every constraint's anchor visible at its call site** - `1495751` (feat)
2. **Task 2: The standalone generator and the solver's remaining direct calls** - `42c650d` (feat)
3. **Task 3: Third allowlist section, third two-directional scan, and the blind spot written down** - `ca9a450` (feat)

**Plan metadata:** (this commit, docs(19-07): complete plan)

## Files Created/Modified
- `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` — `PENDING_DESK_ANCHOR` constant; seven helpers (+ `getShiftEnd`) gained a trailing `DayWindow` parameter; six `ifExists`-gated constraints pass the pending anchor
- `src/test/java/com/wfm/solver/MidnightGapScanTest.java` — nine static-helper calls pass a named `MIDNIGHT` constant; no asserted value changed
- `src/main/java/com/wfm/service/SolverService.java` — `runPreSolveValidation` gains a `DayWindow` parameter (propagated) plus its own `schedule.getScheduleConfig()`-derived window for the coverage check; zero `LocalTime.MIDNIGHT` literals remain
- `src/main/java/com/wfm/util/FteSpreadsheetGenerator.java` — one `DayWindow` bound per `generate()` call from `LocalTime.MIDNIGHT`; six calls routed through it
- `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` — `expandForSupply`'s one deferred comparison migrated to the method's own bound `window`
- `src/test/resources/midnight-time-arithmetic.md` — third `### Permitted midnight anchors` section (4 entries), updated comparison-allowlist entry for `ShiftLibraryGenerationService`, new blind-spot paragraph, extended "When a new entry is legitimate"
- `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` — third heading constant, token list, matcher, parser, set-equality test, two red-proofs, and the shared `missingAllowlistHeading_failsLoudly`/`allowlist_parsesAsNonEmpty` tests extended to the third heading

## Decisions Made
See `key-decisions` in the frontmatter for full reasoning. In brief:
- Six of the seven `ifExists`-gated constraints actually need `PENDING_DESK_ANCHOR`; `honourPreferredStartTime` calls no `DayWindow` helper and was left alone.
- `SolverService`'s two remaining call sites resolve their anchor through two deliberately different channels (propagated desk window vs. `schedule.getScheduleConfig()`), capped at 2 total `anchoredAt()` bindings in the file.
- `ShiftLibraryGenerationService`'s deferred call site is migrated in Task 3, not Task 2, so the full-suite gate stays green between every task.
- The third allowlist section's four entries cover every midnight anchor present in the tree at this point in the phase, including two pre-existing transitional bindings from earlier plans that had no allowlist to land in before this section existed.
- `getShiftEnd` was converted alongside the plan's named helper list, since leaving its `DayWindow::endMinute` method reference in place would break compilation the moment plan 19-08 demotes the statics to private.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Mechanical] The plan's literal `DayWindow` method names are stale; the tracer's `anchored`-prefixed names are what actually exist**
- **Found during:** Task 1 (before writing any code — flagged explicitly in the dispatch prompt's API correction)
- **Issue:** The plan's `<action>` text describes calling bare instance method names (`window.startMinute(...)`, `window.plusWithinDay(...)`, etc.), but plan 19-03 established that these cannot coexist with the nine still-public deprecated statics under the same name, and named them `anchoredStartMinute`, `anchoredEndMinute`, `anchoredDurationMinutes`, `anchoredIsForwardWithinDay`, `anchoredOverlaps`, `anchoredContains`, `anchoredStartsBefore`, `anchoredToLocalTime`, `anchoredPlusWithinDay` instead.
- **Fix:** Every new call site in this plan uses the `anchored*`-prefixed names verbatim, per 19-03's SUMMARY and `DayWindow.java`'s own class javadoc.
- **Files modified:** All production files touched this plan.
- **Verification:** `./gradlew compileJava compileTestJava` exits 0 at every task boundary.
- **Committed in:** `1495751`, `42c650d`, `ca9a450`

**2. [Rule 3 - Mechanical] Task 3's literal `grep -rc 'anchoredAt(LocalTime.MIDNIGHT)' src/main/java` count (5) does not equal the guard's own comment-aware scan count (4)**
- **Found during:** Task 3, verifying the acceptance criterion "the number of entries in the new fenced block equals the count printed by `grep -rc ...` summed over all files"
- **Issue:** `ShiftBandPair.java` carries the literal string `DayWindow.anchoredAt(LocalTime.MIDNIGHT)` TWICE — once in real code (`netHours()`, line 129) and once inside a javadoc comment describing that same method (line 120, `* DayWindow.anchoredAt(LocalTime.MIDNIGHT) rather than breaking them --`). A raw, comment-unaware `grep -rc` therefore counts that file as 2, summing to 5 across all files, while the guard's actual scan (and the allowlist it enforces) correctly strips comment lines before matching — exactly as the two existing scans already do — and finds only 4 real occurrences.
- **Fix:** The third allowlist section holds 4 entries, matching the guard's own `scanProductionSources`/`stripComment` pipeline exactly (verified: `midnightAnchorsInProductionCode_matchesTheMidnightAnchorAllowlistExactly` passes). The acceptance criterion's literal un-gated grep count is a stale proxy for a check the guard itself performs correctly; the INTENT (allowlist completeness against the real scan) is satisfied.
- **Files modified:** None beyond the allowlist/guard test already committed.
- **Verification:** `./gradlew test --tests "com.wfm.service.MidnightTimeArithmeticGuardTest"` — 12/12 green, including the new third scan.
- **Committed in:** `ca9a450`

**3. [Rule 4 → resolved per dispatch instructions] `ShiftLibraryGenerationService.java:586`'s hand-off from plan 19-06**
- **Found during:** Task 3 planning (flagged explicitly in the dispatch prompt's hand-off section)
- **Issue:** Plan 19-06 deliberately left `expandForSupply`'s one comparison on the deprecated `DayWindow.endMinute` static form because migrating it would desync the comparison allowlist in a file reserved for this plan.
- **Fix:** Chosen the preferred option named in the dispatch prompt — migrated the line to the method's own bound `window` and updated the matching comparison-allowlist entry in the SAME commit (Task 3), since this plan owns `midnight-time-arithmetic.md`. Initially attempted in Task 2's scope; reverted when the full-suite run showed it would turn the Task 2→Task 3 gate red (1134/1/0/4, the comparison-allowlist test the sole failure) before the matching allowlist entry existed — redone atomically in Task 3 instead.
- **Files modified:** `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java`, `src/test/resources/midnight-time-arithmetic.md`
- **Verification:** Full unfiltered `./gradlew test` green (1137/0/0/4) after the atomic Task 3 commit.
- **Committed in:** `ca9a450`

---

**Total deviations:** 3 (2 Rule 3 mechanical adaptations to stale plan text, 1 hand-off item resolved per the dispatch prompt's preferred option)
**Impact on plan:** None change what any production call site's anchor resolves to, weaken the two-directional guard discipline, or widen this plan's scope beyond `DayWindow` and its call sites. All three are documented adaptations to a plan text that could not fully anticipate the tree's exact shape after five prior plans in this phase, resolved in favour of the measured, actual state.

## Issues Encountered
None beyond the three deviations above, all resolved before their respective task commits.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All three task commits (`1495751`, `42c650d`, `ca9a450`) land cleanly on top of plan 19-06's `b8562ea`, each verified compiling and passing the full suite independently.
- The nine `DayWindow` statics remain public and `@Deprecated` — unchanged by this plan, as required (plan 19-08 demotes them).
- `ScheduleConstraintProvider.PENDING_DESK_ANCHOR` is the explicit work queue `SOLV-01` inherits: one named constant, six call sites, one allowlist entry — no scattered literals to rediscover.
- The third allowlist section's two pre-existing entries (`ShiftBandPair.netHours()`, `ShiftLibraryGenerationService.resolveBreakConfig`'s fallback) are also now visible, reviewable, and named — not merely tolerated.
- Per the project memory on filtered gradle runs: every full-suite aggregate read in this plan followed an UNFILTERED `./gradlew test` run; every targeted `--tests` run was followed only by reading that same class's own XML, never a different class's or a suite aggregate.
- No blockers. Full unfiltered `./gradlew test` is green (1137 tests, 0 failures, 0 errors, 4 pre-existing skips) at the final commit, and all eight Phase 18 guard test classes are confirmed green individually.

## Self-Check: PASSED

- FOUND: `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` contains `private static final DayWindow PENDING_DESK_ANCHOR`
- FOUND: `src/main/java/com/wfm/util/FteSpreadsheetGenerator.java` binds `DayWindow.anchoredAt(LocalTime.MIDNIGHT)`
- FOUND: `src/test/resources/midnight-time-arithmetic.md` contains `### Permitted midnight anchors`
- FOUND: `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` contains `MIDNIGHT_ANCHOR_ALLOWLIST_HEADING`
- FOUND commit `1495751` (Task 1) in `git log --oneline --all`
- FOUND commit `42c650d` (Task 2) in `git log --oneline --all`
- FOUND commit `ca9a450` (Task 3) in `git log --oneline --all`
- CONFIRMED: completeness gate reports `0` for `ScheduleConstraintProvider.java`, `FteSpreadsheetGenerator.java`, `SolverService.java`
- CONFIRMED: `grep -c 'private static final DayWindow PENDING_DESK_ANCHOR'` reports `1`
- CONFIRMED: `grep -c 'anchoredAt' src/main/java/com/wfm/service/SolverService.java` reports `2`
- CONFIRMED: `grep -c 'LocalTime.MIDNIGHT' src/main/java/com/wfm/service/SolverService.java` reports `0`
- CONFIRMED: `./gradlew test --tests "com.wfm.service.MidnightTimeArithmeticGuardTest"` — 12 tests, 0 failures, 0 errors
- CONFIRMED: full, unfiltered `./gradlew test` at the final commit — 1137 tests, 0 failures, 0 errors, 4 skipped
- CONFIRMED: all eight Phase 18 guard test classes individually 0 failures / 0 errors
- CONFIRMED: `git diff --stat b8562ea..HEAD` touches exactly the seven files listed above

---
*Phase: 19-daywindow-re-anchoring*
*Completed: 2026-10-01*
