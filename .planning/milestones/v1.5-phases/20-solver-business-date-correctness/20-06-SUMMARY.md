---
phase: 20-solver-business-date-correctness
plan: 06
subsystem: solver
tags: [business-date, coverage-reporting, shift-library-generation, structural-guard, timeslot]

# Dependency graph
requires:
  - phase: 20-02
    provides: "BusinessDateJoinGuardTest's new-and-unlisted work order for ScheduleOutputService (6 entries) and ShiftLibraryGenerationService (1 reachable entry), plus its documented blind spots at ShiftLibraryGenerationService:180,226,616"
  - phase: 20-05
    provides: "ScheduleConstraintProvider's business-date join/anchor migration fully closed; the 18-fixture setBusinessDate precedent this plan's own fixture fix follows"
provides:
  - "Six coverage-reporting key positions in ScheduleOutputService (:62,:71,:164,:173,:323,:739) read Timeslot.getBusinessDate(); the two operator-facing display labels deliberately keep the calendar date, each with an explanatory comment citing SOLV-07/D-10"
  - "All four timeslot-date key positions in ShiftLibraryGenerationService (:180,:226,:497,:632) read Timeslot.getBusinessDate(), including the two the join guard's four-verb scan structurally cannot see"
  - "BusinessDateJoinGuardTest's headline set-equality test is now GREEN -- all matched lines across ScheduleConstraintProvider, ScheduleOutputService and ShiftLibraryGenerationService are migrated. This is NOT proof SOLV-07 is fully delivered: StaffingRequirementService's D-15 destructive-delete defect remains live and structurally invisible to this guard"
affects: [20-07-staffing-requirement-migration, 20-08-day-start-generation-reachability]

# Actuals (#2632)
actuals:
  tokens: 8318
  tasks: 2
  commits: 2
  plan_head_before: 7d2c28f7338e81256ac904e0c8c70d45a2977b55

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A migrated-by-instruction comment at a join-guard blind spot (outside the four-verb scan) records WHY the guard's own green does not cover that line, so a future reader finds a documented decision rather than a false sense of coverage"
    - "A quick red/green self-check (revert the production accessor, confirm the new test fails, restore) used to prove a new assertion exercises the real defect rather than passing vacuously -- applied to the ShiftLibraryGenerationService weekday-attribution test"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/service/ScheduleOutputService.java
    - src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
    - src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java
    - src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java
    - src/test/java/com/wfm/service/ShiftLibraryGenerationServiceTest.java
    - src/test/resources/bday-join-guard.md

key-decisions:
  - "Rule 1 bug fix extended beyond this plan's declared file list: migrating ScheduleOutputService's six key positions to getBusinessDate() silently broke agent-day grouping in every hand-built Timeslot test fixture that never called setBusinessDate (grouping keyed itself on null instead of the intended date). Three sites in this plan's own ScheduleOutputServiceShiftReportingTest.java and three in the un-declared ScheduleServiceShiftSnapshotTest.java were fixed, following plan 20-05's identical 18-fixture precedent. All six fixtures are implicitly 00:00-anchored, so businessDate == date and the fix is behaviourally inert."
  - "BusinessDateJoinGuardTest went fully GREEN as a direct, unplanned consequence of this plan's migration, not plan 20-07's. StaffingRequirementService contributes ZERO matched lines to this guard regardless of migration state (its D-15 delete-range derivation uses .map(Timeslot::getDate).min()/.max(), neither of which is a scanned verb) -- a fact already established in plan 20-02's SUMMARY. Once ScheduleConstraintProvider (20-05) and ScheduleOutputService/ShiftLibraryGenerationService's one reachable site (this plan) were migrated, nothing scannable remained un-migrated, so the guard reports green even though StaffingRequirementService's actual demand-upload defect (D-15) is still live. bday-join-guard.md is updated with an explicit warning against misreading this green as SOLV-07 completion."
  - "The two ShiftLibraryGenerationService sites the join guard cannot see (:226, :632 -- originally :616) were migrated by this plan's explicit instruction, confirmed correct by direct code review and by a red/green self-check (temporarily reverting the weekday-related accessors and confirming the new test fails), not by the guard's own enforcement."

patterns-established:
  - "A migrated-but-guard-invisible site gets BOTH a production comment (why this line is outside the scan) and a bday-join-guard.md update (so the allowlist doc's own prose does not go stale the moment the migration lands) -- the same discipline 20-02 established, now demonstrated end-to-end on a completed migration rather than a pending one."

requirements-completed: [SOLV-07]

# Coverage metadata (#1602)
coverage:
  - id: D1
    description: "ScheduleOutputService's six coverage-reporting key positions (predicted/actual staffing-summary maps, timeslots-by-date, agent-schedule and preference-report groupings, accepted-constraint-violations grouping) read Timeslot.getBusinessDate(); the two operator-facing display labels and the four agent-shift-assignment/agent-preference date reads are confirmed byte-identical"
    requirement: "SOLV-07"
    verification:
      - kind: unit
        ref: "com.wfm.service.ScheduleOutputServiceShiftReportingTest#buildStaffingSummary_21_00AnchoredDesk_bucketsTheCrossMidnightBusinessDayOnce"
        status: pass
      - kind: unit
        ref: "com.wfm.service.ScheduleOutputServiceShiftReportingTest#buildStaffingSummary_00_00AnchoredDesk_figuresUnchanged"
        status: pass
      - kind: other
        ref: "grep -c 'getBusinessDate()' ScheduleOutputService.java -> 6 (>= 6 required); grep -n 'getDate() + \" \"' -> both label lines present"
        status: pass
      - kind: other
        ref: "direct code review: lines 364,366 (AgentPreference.getDate), 498,820 (AgentShiftAssignment.getDate) confirmed byte-identical; the ACCEPTED/DB-path explain() refusal confirmed byte-identical"
        status: pass
      - kind: unit
        ref: "./gradlew test --tests ScheduleOutputServiceShiftReportingTest --tests ScheduleExportServiceTest --tests ScheduleAllocationExportTest --tests ScheduleRosterExportTest --tests ScheduleServiceShiftSnapshotTest"
        status: pass
    human_judgment: false
  - id: D2
    description: "ShiftLibraryGenerationService's all four timeslot-date key positions (weekday-cluster filter, distinct/sort window identity, per-weekday demand aggregation, demanded-dates set) read Timeslot.getBusinessDate(), including the two sites BusinessDateJoinGuardTest's four-verb scan structurally cannot see"
    requirement: "SOLV-07"
    verification:
      - kind: unit
        ref: "com.wfm.service.ShiftLibraryGenerationServiceTest#generateSuggestion_21_00AnchoredDesk_postMidnightDemandBucketsUnderTheBusinessDayItBelongsTo"
        status: pass
      - kind: unit
        ref: "com.wfm.service.ShiftLibraryGenerationServiceTest#generateSuggestion_00_00AnchoredDesk_weekdayAttributionUnchanged"
        status: pass
      - kind: other
        ref: "grep -c 'getTimeslot().getDate()' ShiftLibraryGenerationService.java -> 0; grep -c 'getBusinessDate()' -> 4"
        status: pass
      - kind: other
        ref: "red/green self-check: temporarily reverted the two weekday-related getBusinessDate() calls back to getDate(), confirmed generateSuggestion_21_00Anchored... test FAILS, restored and confirmed green again"
        status: pass
      - kind: unit
        ref: "./gradlew test --tests ShiftLibraryGenerationServiceTest --tests ShiftLibraryValidationServiceTest --tests ShiftLibraryGenerationCapConfigTest --tests MidnightTimeArithmeticGuardTest"
        status: pass
    human_judgment: false
  - id: D3
    description: "BusinessDateJoinGuardTest's documentation (bday-join-guard.md) updated to accurately reflect post-migration state, including an explicit warning that the guard's new green does not prove StaffingRequirementService's D-15 defect is fixed"
    verification: []
    human_judgment: true
    rationale: "Prose/documentation accuracy is not mechanically verifiable beyond the guard's own test passing -- a human should confirm the warning is clear enough that a future reader does not mistake this guard's green for SOLV-07 completion before plan 20-07 lands."

duration: 23min
completed: 2026-10-01
status: complete
---

# Phase 20 Plan 6: ScheduleOutputService and ShiftLibraryGenerationService Business-Date Migration Summary

**Coverage reporting and shift-library generation now resolve the same business date a timeslot's solver constraints already resolve -- ten key-position edits across two services, two display labels deliberately left on calendar date, and an unplanned side effect: BusinessDateJoinGuardTest's headline test is now GREEN, a full plan ahead of the ROADMAP's own prediction, because StaffingRequirementService's D-15 defect is structurally invisible to the guard regardless of migration state.**

## Performance

- **Duration:** 23 min
- **Started:** 2026-10-01T23:25:00Z (approx)
- **Completed:** 2026-10-01T23:47:55Z
- **Tasks:** 2 completed
- **Files modified:** 6 (2 production, 3 test, 1 test resource)

## Accomplishments

- **Task 1 (ScheduleOutputService).** Six coverage-reporting key positions — the predicted/actual
  staffing-summary maps (`:62`, `:71`), the timeslots-by-date map (`:164`), the agent-schedule and
  preference-report agent-day groupings (`:173`, `:323`), and the accepted-constraint-violations
  grouping (`:739`) — now read `Timeslot.getBusinessDate()`. The two operator-facing timeslot
  labels (`:670`, `:771`, string concatenation with the calendar date) are deliberately unchanged,
  each carrying a new comment explaining why (SOLV-07/D-10: a label answers "when does this
  happen", a calendar question) and naming `OVNT-07` as the owner of any future labelling change.
  The four agent-shift-assignment/agent-preference date reads (`:364`, `:366`, `:498`, `:820`) and
  the accepted/DB-path `explain()` refusal are confirmed byte-identical by direct review.
- **Task 2 (ShiftLibraryGenerationService).** All four timeslot-date key positions — the
  weekday-cluster filter (`:180`), the distinct/sort window identity (`:226`), the per-weekday
  demand aggregation (`:497`, was `:486`), and the demanded-dates set (`:632`, was `:616`) — now
  read `Timeslot.getBusinessDate()`. Two of these four (`:226`, `:632`) are outside
  `BusinessDateJoinGuardTest`'s four-verb scan (`.distinct()` and `TreeSet`-collection are not
  scanned verbs); both carry comments stating so, confirmed correct by direct code review AND by a
  deliberate red/green self-check (temporarily reverting the weekday-related accessors, confirming
  the new test fails, restoring). The zero-schedule-yet break-configuration fallback
  (`DayWindow.anchoredAt(LocalTime.MIDNIGHT)`) is byte-identical; its row in
  `midnight-time-arithmetic.md` is untouched.
- **Unplanned finding, documented rather than hidden: `BusinessDateJoinGuardTest`'s headline
  set-equality test is now GREEN**, a full plan ahead of this plan's own `<verification>` text
  ("still red … plan 20-07 closes those") and the ROADMAP's stated closure point at plan 20-07.
  This is NOT because `StaffingRequirementService`'s D-15 destructive-delete defect (`:172-175`)
  was fixed — it was not touched, and plan 20-02's SUMMARY already established that this exact site
  contributes ZERO matched lines to the guard regardless of migration state (`.min(`/`.max(` are not
  scanned verbs). Once `ScheduleConstraintProvider` (plan 20-05) and `ScheduleOutputService`/
  `ShiftLibraryGenerationService`'s one reachable site (this plan) were migrated, nothing scannable
  remained, so the guard reports green on a true-but-incomplete picture. `bday-join-guard.md` is
  updated with an explicit warning against misreading this green as SOLV-07 completion; plan 20-07
  still owns closing the real demand-upload defect by its own direct proof.
- **Rule 1 bug, caught before any commit shipped broken: migrating `ScheduleOutputService` silently
  broke six pre-existing hand-built `Timeslot` test fixtures that never called `setBusinessDate`.**
  Grouping that used to key on the intended calendar date silently keyed itself on `null` instead,
  which in `ScheduleServiceShiftSnapshotTest` would have flipped three previously-passing assertions
  (`shift` expected non-null, `entry.date()` expected `MONDAY`) to failures, and in this plan's own
  `ScheduleOutputServiceShiftReportingTest` would have collapsed every shift-descriptor lookup to
  `null` across most of the suite. Fixed by adding `setBusinessDate` alongside the existing
  `setDate` call in all six fixtures (three declared in this plan's file list, three in the
  un-declared `ScheduleServiceShiftSnapshotTest.java`), following plan 20-05's identical 18-fixture
  precedent exactly. Every fixture is implicitly 00:00-anchored, so the fix is behaviourally inert.
- Full unfiltered `./gradlew test`: **1177 tests, 0 failures, 0 errors, 4 skipped, 10m24s** —
  matching the historical ~11-minute baseline (1173 + this plan's 4 new tests).

## Task Commits

1. **Task 1: ScheduleOutputService — six coverage keys migrate, two labels deliberately do not** - `a17de3c` (feat)
2. **Task 2: ShiftLibraryGenerationService — four key positions, including the two the guard cannot see** - `ab88fd4` (feat)

**Plan metadata:** pending (this commit)

## Files Created/Modified

- `src/main/java/com/wfm/service/ScheduleOutputService.java` — six key-position migrations, two documented display-label non-migrations
- `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` — four key-position migrations, two documented guard-blind-spot comments, one in-scope-justification comment
- `src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java` — two new 21:00/00:00-anchor coverage tests; three pre-existing fixtures fixed with `setBusinessDate`
- `src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java` — three pre-existing fixtures fixed with `setBusinessDate` (Rule 1, unplanned file)
- `src/test/java/com/wfm/service/ShiftLibraryGenerationServiceTest.java` — two new 21:00/00:00-anchor weekday-attribution tests, one `saveAnchoredDemand` fixture helper
- `src/test/resources/bday-join-guard.md` — "Known scope boundaries" updated to past tense; new explicit warning against misreading the guard's green

## Decisions Made

See `key-decisions` in the frontmatter for full wording. Summary: (1) the Rule 1 fixture fix extended
to an un-declared file (`ScheduleServiceShiftSnapshotTest.java`) because the bug it caught was a direct
consequence of this plan's production change, not a pre-existing unrelated issue; (2) the join guard's
unplanned GREEN is documented as a true-but-incomplete signal, not quietly accepted as "ahead of
schedule" or allowlisted away; (3) the two guard-blind-spot sites were verified by BOTH direct code
review AND a deliberate red/green self-check, going beyond the plan's stated verification method.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Six pre-existing `Timeslot` test fixtures lacked `setBusinessDate`, silently breaking agent-day grouping once `ScheduleOutputService` migrated to business-date keys**
- **Found during:** Task 1, running the task's own verify suite (`ScheduleServiceShiftSnapshotTest` et al.) before committing
- **Issue:** `ScheduleOutputServiceShiftReportingTest`'s shared `timeslot()` helper, its `acceptedScheduleWithEnvelope` helper, and one inline `relocated` fixture, plus three `new Timeslot()` sites in `ScheduleServiceShiftSnapshotTest.java` (an un-declared file), all called `setDate(...)` but never `setBusinessDate(...)`. After migration, agent-day grouping keyed on `null` instead of the intended date — in `ScheduleServiceShiftSnapshotTest`, this would have made `shiftDescriptorsByAgentDate` lookups miss (changing `shift` from non-null to null) and `entry.date()` return `null` instead of the asserted `MONDAY`.
- **Fix:** Added `X.setBusinessDate(date)` immediately after each existing `X.setDate(date)` call, matching plan 20-05's identical, already-reviewed 18-fixture pattern. All six fixtures are implicitly 00:00-anchored, so this is behaviourally inert.
- **Files modified:** `src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java` (3 sites), `src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java` (3 sites, un-declared file)
- **Verification:** `./gradlew test --tests ScheduleOutputServiceShiftReportingTest --tests ScheduleServiceShiftSnapshotTest --tests ScheduleExportServiceTest --tests ScheduleAllocationExportTest --tests ScheduleRosterExportTest` all pass; full unfiltered suite 1177/0/0/4.
- **Committed in:** `a17de3c` (Task 1 commit — caught before the commit, not a follow-up fix)

---

**Total deviations:** 1 auto-fixed (Rule 1 bug spanning 6 fixtures across 2 files, one of them outside
this plan's declared file list). **Impact on plan:** Necessary for the plan's own stated regression
guard (the four export/snapshot suites) to mean what it claims. No committed baseline, test, or
production behaviour on a 00:00-anchored desk changed — every fixture is implicitly 00:00-anchored so
`businessDate == date`, and no scope creep beyond fixing a bug this plan's own production change caused.

## Issues Encountered

None beyond the deviation documented above, and the unplanned-but-benign join-guard GREEN documented
in Accomplishments. No blockers, no auth gates, no unresolved ambiguity requiring a checkpoint.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

**Ready for plan 20-07** (`StaffingRequirementService`'s demand-upload delete-range migration, D-15).
The join guard's remaining new-and-unlisted set is now empty in practice — not because
`StaffingRequirementService`'s defect is fixed, but because that file structurally contributes ZERO
matched lines to this guard's four-verb scan regardless of migration state (established in plan 20-02,
reconfirmed here). Plan 20-07 must verify its own fix by direct proof (the destructive delete range
actually narrowing to the correct business-date span), not by expecting this guard to turn red first.

No blockers.

---
*Phase: 20-solver-business-date-correctness*
*Completed: 2026-10-01*

## Self-Check: PASSED

- FOUND: src/main/java/com/wfm/service/ScheduleOutputService.java (modified)
- FOUND: src/main/java/com/wfm/service/ShiftLibraryGenerationService.java (modified)
- FOUND: src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java (modified)
- FOUND: src/test/java/com/wfm/service/ScheduleServiceShiftSnapshotTest.java (modified)
- FOUND: src/test/java/com/wfm/service/ShiftLibraryGenerationServiceTest.java (modified)
- FOUND: src/test/resources/bday-join-guard.md (modified)
- FOUND: commit a17de3c (Task 1)
- FOUND: commit ab88fd4 (Task 2)
- Acceptance criteria re-verified: `grep -c 'getBusinessDate()' ScheduleOutputService.java` → 6;
  `grep -n 'getDate() + " "' ScheduleOutputService.java` → both label lines present (670, 771);
  `grep -c 'getTimeslot().getDate()' ShiftLibraryGenerationService.java` → 0;
  `grep -c 'getBusinessDate()' ShiftLibraryGenerationService.java` → 4.
- Plan-level `<verification>` re-run: `./gradlew compileJava compileTestJava` → BUILD SUCCESSFUL;
  `./gradlew test --tests "com.wfm.service.BusinessDateJoinGuardTest"` → BUILD SUCCESSFUL, all 6
  tests pass (including the previously-red headline set-equality test — see Accomplishments for why
  this is a documented, not silently-accepted, early green); `./gradlew test --tests
  "com.wfm.solver.PhilUsShapedDriftGuardTest" --tests "com.wfm.service.MidnightTimeArithmeticGuardTest"`
  → BUILD SUCCESSFUL; unfiltered `./gradlew test` → 1177 tests, 0 failed, 0 errors, 4 skipped, 10m24s.
