---
phase: 20-solver-business-date-correctness
plan: 07
subsystem: solver
tags: [business-date, destructive-delete, staffing-requirement, structural-guard, timeslot, timefold]

# Dependency graph
requires:
  - phase: 20-02
    provides: "BusinessDateJoinGuardTest and its documented, measured finding that StaffingRequirementService's D-15 delete-range derivation (.map(Timeslot::getDate).min()/.max()) is structurally invisible to the guard's four-verb scan regardless of migration state"
  - phase: 20-06
    provides: "The join guard's headline set-equality test already GREEN across ScheduleConstraintProvider/ScheduleOutputService/ShiftLibraryGenerationService -- a true-but-incomplete signal this plan's SUMMARY must not be read as superseding, since it never covered StaffingRequirementService"
provides:
  - "StaffingRequirementRepository.deleteLiveByDeskAndBusinessDateRange -- a business-date twin of the existing calendar-date delete, differing only in which Timeslot column its subselect filters"
  - "The demand-upload replace path (StaffingRequirementService.saveRequirements) derives its delete range from getBusinessDate() and calls only the business-date delete method -- range and filter moved in one edit, making a half-migration structurally impossible"
  - "StaffingRequirementBusinessDateDeleteTest -- observed-survivor proof at a 21:00 and a 00:00 anchor, plus the transactional-boundary property, confirmed by a red/green self-check against the pre-migration code"
  - "bday-join-guard.md updated to record this migration as the final piece the guard's green could never see -- StaffingRequirementService still contributes zero matched lines to the scan, migrated or not"
affects: [20-08-day-start-generation-reachability]

# Actuals (#2632)
actuals:
  tokens: 6827
  tasks: 2
  commits: 2
  plan_head_before: 20f7b38

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A destructive delete's range derivation and its repository-side filter are changed in ONE edit, with the range passed to a method whose query can only interpret it one way -- the only way to make a half-migration structurally impossible rather than merely documented as forbidden"
    - "A business-date-filtering delete is added as a NEW sibling method rather than by re-pointing the shared calendar-date query, because two other callers (Erlang C/X) legitimately keep calendar-date semantics and re-pointing the shared query would silently re-scope both"
    - "A destructive query's correctness is proven by observing SURVIVORS (which rows remain after the call), not by asserting which rows were targeted for deletion -- the latter can pass while extra rows are also destroyed"
    - "A red/green self-check for a survivor test: temporarily revert the production migration, confirm the specific case fails for the specific documented reason, restore, confirm the file matches the committed state exactly via `git diff`"

key-files:
  created:
    - src/test/java/com/wfm/service/StaffingRequirementBusinessDateDeleteTest.java
  modified:
    - src/main/java/com/wfm/repository/StaffingRequirementRepository.java
    - src/main/java/com/wfm/service/StaffingRequirementService.java
    - src/test/resources/bday-join-guard.md

key-decisions:
  - "A second repository method (deleteLiveByDeskAndBusinessDateRange) was added rather than re-pointing deleteLiveByDeskAndDateRange, because the Erlang C and Erlang X calculators still legitimately pass operator-supplied calendar-date bounds straight from their request payloads -- re-pointing the shared query would have silently re-scoped both operator-facing endpoints. The javadoc on each method records the other's callers by name, matching the plan's caller-count acceptance criteria (exactly 1 business-date call, exactly 2 calendar-date calls in the service)."
  - "Task 2's third case (transactional integrity) asserts the real-but-weaker boundary property (@Transactional on saveRequirements, confirmed by reflection) rather than a constructed insert-time failure. Direct analysis showed no payload shape reaches the insert loop with a row that collides with a surviving live requirement: the delete range is ALWAYS derived from, and therefore always covers, the business dates of every timeslot the SAME payload targets, so the rows about to be reinserted are by construction always wiped first. A forced failure (e.g. a stale post-clear() entity-manager reference) would have exercised an unrelated, pre-existing code path rather than this plan's own change, and risked surfacing a tangential finding out of scope for this plan. The plan explicitly sanctions this fallback when a true failure is not reachable without contorting the fixture."
  - "The 21:00-anchor survivor test was verified by an explicit red/green self-check: the migrated service code was temporarily reverted in place (business-date derivation and the new delete call swapped back to the calendar-date method), the test suite was re-run, and exactly the 21:00 case failed (the 00:00 regression case and the transactional case still passed, as expected -- neither depends on the migration). The file was then restored and `git diff` confirmed byte-identical to the committed state before re-running green. This is the same discipline plan 20-06 used for its own guard-invisible sites."
  - "bday-join-guard.md's 'Current state' paragraph and two 'Known scope boundaries' bullets were rewritten rather than left as plan-20-06 artifacts, because leaving them as-is would have understated this plan's own finding: the guard's green (established at plan 20-06) was NEVER evidence about StaffingRequirementService, and still is not now that the file is actually fixed -- the fix is proven by this plan's own test and by direct code review, never by the guard turning red and green again."

patterns-established:
  - "A migrated destructive-delete site that a structural guard cannot see gets its own dedicated survivor-observing test class, not a reliance on the guard's silence meaning safety -- the guard's scope boundaries document, in the same file, both what it catches and what it explicitly does not, so neither claim is overstated."

requirements-completed: [SOLV-07]

# Coverage metadata (#1602)
coverage:
  - id: D1
    description: "StaffingRequirementRepository gains deleteLiveByDeskAndBusinessDateRange; the demand-upload replace path derives its range from getBusinessDate() and calls only that method, in one edit, with the calendar-date method and its two Erlang callers left intact and documented by name"
    requirement: "SOLV-07"
    verification:
      - kind: other
        ref: "grep -c 'deleteLiveByDeskAndBusinessDateRange' StaffingRequirementRepository.java -> 1; grep -c 'businessDate BETWEEN' -> 1; grep -c 'deleteLiveByDeskAndDateRange' -> 2 (unchanged method + new javadoc line, not 0)"
        status: pass
      - kind: other
        ref: "grep -c 'deleteLiveByDeskAndBusinessDateRange' StaffingRequirementService.java -> exactly 1; grep -c 'deleteLiveByDeskAndDateRange' -> exactly 2 (the two Erlang callers)"
        status: pass
      - kind: other
        ref: "awk over saveRequirements method body | grep -c entityManager.flush -> exactly 1 (flush-before-insert step byte-identical)"
        status: pass
      - kind: unit
        ref: "./gradlew compileJava compileTestJava"
        status: pass
    human_judgment: false
  - id: D2
    description: "StaffingRequirementBusinessDateDeleteTest proves the deleted span by observing survivors at a 21:00 anchor (business day D's upload leaves business day D+1's requirements byte-identical despite sharing calendar date D+1) and at a 00:00 anchor (surviving set identical to what a calendar-date range always produced), plus the transactional-boundary property for the third case"
    requirement: "SOLV-07"
    verification:
      - kind: unit
        ref: "com.wfm.service.StaffingRequirementBusinessDateDeleteTest#anchor21_uploadForBusinessDayD_leavesBusinessDayDPlus1Untouched"
        status: pass
      - kind: unit
        ref: "com.wfm.service.StaffingRequirementBusinessDateDeleteTest#anchor00_uploadForDayOne_survivingSetMatchesCalendarDateRange"
        status: pass
      - kind: unit
        ref: "com.wfm.service.StaffingRequirementBusinessDateDeleteTest#saveRequirementsTransactionBoundaryEnclosesDeleteAndReinsert"
        status: pass
      - kind: other
        ref: "Red/green self-check: production delete temporarily reverted to the calendar-date method, re-ran the class -- exactly the 21:00 case FAILED (AssertionError at line 159), the other two still passed; restored, `git diff` showed zero difference from the committed state, re-ran green"
        status: pass
      - kind: other
        ref: "grep -c 'DayWindow.businessDateOf' StaffingRequirementBusinessDateDeleteTest.java -> 1 (fixture business dates derived through the shared production function, never hand-computed)"
        status: pass
    human_judgment: false
  - id: D3
    description: "BusinessDateJoinGuardTest's headline set-equality test remains GREEN with an empty allowlist after this plan's production edits, and a manual re-count across all four guarded files confirms every remaining Timeslot calendar-date read is a documented display decision (D-10), not an un-migrated key position"
    requirement: "SOLV-02, SOLV-07"
    verification:
      - kind: unit
        ref: "./gradlew test --tests com.wfm.service.BusinessDateJoinGuardTest"
        status: pass
      - kind: unit
        ref: "./gradlew test --tests com.wfm.service.StaffingRequirementErlangTest --tests com.wfm.solver.PhilUsShapedDriftGuardTest --tests com.wfm.service.MidnightTimeArithmeticGuardTest"
        status: pass
      - kind: other
        ref: "Manual re-count: grep -n 'getDate()' across ScheduleConstraintProvider/ScheduleOutputService/ShiftLibraryGenerationService/StaffingRequirementService -- every remaining hit is either AgentShiftAssignment/AgentPreference's own (business-date-shaped) date field, or one of the two documented D-10 display sites (ScheduleOutputService:670,771 and StaffingRequirementService:388)"
        status: pass
      - kind: unit
        ref: "full unfiltered ./gradlew test"
        status: pass
    human_judgment: true
    rationale: "The manual re-count is a property of the tree at this moment, as the plan's own <verification> section states -- a human should confirm the allowlist documentation in bday-join-guard.md is clear enough that a future reader does not mistake the guard's green (established one plan early, at 20-06) for evidence about StaffingRequirementService, which the guard structurally cannot see."

duration: 38min
completed: 2026-10-02
status: complete
---

# Phase 20 Plan 7: StaffingRequirementService Business-Date Delete Migration Summary

**The one destructive write path in this phase -- demand-upload's delete range and its repository filter moved to business date in one edit, proven safe by observing survivors at two anchors (not by reading the query), with a red/green self-check against the pre-migration code and the join guard confirmed still green and still honestly documented as blind to this exact file.**

## Performance

- **Duration:** 38 min (approx)
- **Started:** 2026-10-01T23:50:00Z (approx)
- **Completed:** 2026-10-02T00:24:08Z
- **Tasks:** 2 completed
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments

- **Task 1 (the migration itself).** `StaffingRequirementRepository` gains
  `deleteLiveByDeskAndBusinessDateRange`, a business-date twin of the existing calendar-date
  delete whose subselect differs in exactly one place: it filters the timeslot's `businessDate`
  column instead of its `date` column. The demand-upload replace path
  (`StaffingRequirementService.saveRequirements`) derives its `minDate`/`maxDate` bound from
  `getBusinessDate()` instead of `getDate()` and calls only the new business-date method -- all
  three changes landed in one edit, so a service-side range on one date system can never reach a
  repository-side filter on the other. The pre-existing calendar-date method is left
  byte-identical for its two remaining legitimate callers (`calculateErlangC`/`calculateErlangX`,
  which still pass operator-supplied calendar-date bounds straight from the request), with a new
  javadoc line on each method naming the other's callers. The response item's `date` field stays
  on the calendar date (D-10), now with an explanatory comment stating the display reasoning in
  production rather than only in the plan.
- **Task 2 (the proof).** `StaffingRequirementBusinessDateDeleteTest` builds eight real timeslots
  (via `@DataJpaTest` against H2) across three calendar dates and two business days on a
  21:00-anchored desk, asserts every fixture timeslot has a non-null business date before
  exercising the service, then uploads a payload covering only business day D. **Observed
  survivor sets:**
  - **21:00 anchor:** business day D+1's four requirements survive byte-identical (same ids,
    same `requiredFTEs` values) even though business day D's last two timeslots and business day
    D+1's first two timeslots share calendar date D+1; business day D's four requirements are
    replaced with the uploaded value (9, from an original 5).
  - **00:00 anchor:** the surviving set after an identical-shaped upload is exactly what a
    calendar-date range always produced -- proving no live desk's deleted span changes, since
    every live desk is anchored at 00:00 until this phase's final commit.
  - **Transactional case:** asserts the real-but-weaker property that `saveRequirements` carries
    `@Transactional` (confirmed by reflection), with the class javadoc explaining why a
    constructed insert-time failure was not attempted -- the delete range is always derived from,
    and therefore always covers, the same payload's own timeslots, so no payload shape reaches
    the insert loop with a row that collides with a surviving live requirement.
- **Red/green self-check, not just a passing run.** The production migration was temporarily
  reverted in place (business-date derivation and the new delete call swapped back to the
  pre-Task-1 calendar-date shape), the test class re-run, and **exactly the 21:00 case failed**
  (`AssertionError` at the business-day-D+1-survives assertion) while the 00:00 and transactional
  cases still passed, as expected since neither depends on the migration. The file was then
  restored and `git diff` confirmed zero difference from the committed state before re-running
  green. This is the same discipline plan 20-06 used to verify its own guard-invisible sites, now
  applied to a destructive-delete test rather than a read-path one.
- **`bday-join-guard.md` updated, not left as a plan-20-06 artifact.** The "Current state"
  paragraph and the relevant "Known scope boundaries" bullets (the response-DTO line number, and
  the `StaffingRequirementService` lines-172-175 blind spot) are rewritten to state plainly: the
  guard's green was established one plan early (at 20-06) and was **never** evidence about this
  file, and is still not evidence about it now that the file is actually fixed -- the fix is
  proven by this plan's own test and direct code review, not by the guard changing state.
- **Manual re-count (required by the plan before the phase closes).** Re-grepped `getDate()`
  across all four guarded files. Every remaining hit is either `AgentShiftAssignment`'s or
  `AgentPreference`'s own date field (already business-date-shaped, established in plan 20-02) or
  one of the two documented D-10 display sites (`ScheduleOutputService:670,771`,
  `StaffingRequirementService:388`). No undocumented Timeslot calendar-date read remains in any
  join/group/delete-range key position across any of the four files.
- **Full unfiltered `./gradlew test`: 1180 tests, 0 failures, 0 errors, 4 skipped, 10m18s** across
  all 192 JUnit XML result files -- the 1177-test baseline plus this plan's 3 new tests, exactly
  as expected. `BusinessDateJoinGuardTest` remains green with an empty allowlist.

## Task Commits

Each task was committed atomically:

1. **Task 1: The delete range and its filter, moved in one edit** - `94c2b41` (feat)
2. **Task 2: Prove the deleted span by observing survivors, and close the join guard** - `97509a1` (test)

**Plan metadata:** pending (this commit)

## Files Created/Modified

- `src/main/java/com/wfm/repository/StaffingRequirementRepository.java` -- new
  `deleteLiveByDeskAndBusinessDateRange` method, javadoc on both delete methods naming each
  other's callers
- `src/main/java/com/wfm/service/StaffingRequirementService.java` -- `saveRequirements`'s range
  derivation and delete call moved to business date in one edit; response item's calendar-date
  field gains an explanatory comment
- `src/test/java/com/wfm/service/StaffingRequirementBusinessDateDeleteTest.java` -- observed-survivor
  tests at two anchors plus the transactional-boundary case
- `src/test/resources/bday-join-guard.md` -- "Current state" and two "Known scope boundaries"
  entries rewritten to reflect this plan's actual fix, not merely the guard's unrelated earlier green

## Decisions Made

See `key-decisions` in the frontmatter for full wording. Summary: (1) a second repository method
rather than re-pointing the shared one, so the two Erlang calculators keep calendar-date
semantics without a silent re-scope; (2) the transactional case asserts the real-but-weaker
boundary property rather than contorting the fixture into an artificial failure, because no
genuine insert-time conflict is reachable through the public method by construction; (3) a
red/green self-check on the 21:00 survivor test, matching plan 20-06's own discipline; (4)
`bday-join-guard.md` rewritten rather than left stale, so its documentation does not let a future
reader conflate the guard's earlier green with evidence about this file.

## Deviations from Plan

None - plan executed exactly as written. The one judgment call explicitly anticipated by the plan
itself -- whether a genuine post-delete insert failure was reachable for the transactional case --
resolved to the plan's own documented fallback (assert the weaker boundary property), not a
deviation from it.

## Issues Encountered

None. No blockers, no auth gates, no unresolved ambiguity requiring a checkpoint.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

**Ready for plan 20-08** (`DeskService.setDayStart`'s midnight-only gate deletion and day-start
generation reachability -- the phase's final commit). This plan closes the last item on plan
20-02's original work order: `StaffingRequirementService`'s D-15 destructive-delete defect, which
`BusinessDateJoinGuardTest` could never see regardless of migration state, is now fixed and proven
by a dedicated survivor-observing test with a red/green self-check, not by the guard's unrelated
earlier green. SOLV-07's "provably the same business date, guarded by a test rather than by
convention" now holds for every file this phase names, including the one structurally outside the
guard's own reach.

No blockers.

---
*Phase: 20-solver-business-date-correctness*
*Completed: 2026-10-02*

## Self-Check: PASSED

- FOUND: src/main/java/com/wfm/repository/StaffingRequirementRepository.java (modified)
- FOUND: src/main/java/com/wfm/service/StaffingRequirementService.java (modified)
- FOUND: src/test/java/com/wfm/service/StaffingRequirementBusinessDateDeleteTest.java (created)
- FOUND: src/test/resources/bday-join-guard.md (modified)
- FOUND: commit 94c2b41 (Task 1)
- FOUND: commit 97509a1 (Task 2)
- Acceptance criteria re-verified: `grep -c 'deleteLiveByDeskAndBusinessDateRange'` repository.java
  -> 1; `grep -c 'businessDate BETWEEN'` -> 1; `grep -c 'deleteLiveByDeskAndDateRange'` repository.java
  -> 2 (not 0); service.java business-date count -> exactly 1; service.java calendar-date count ->
  exactly 2; `awk` over `saveRequirements` body | `grep -c entityManager.flush` -> exactly 1;
  `grep -c 'DayWindow.businessDateOf'` in the new test -> 1.
- Plan-level `<verification>` re-run: `./gradlew compileJava compileTestJava` -> BUILD SUCCESSFUL;
  `./gradlew test --tests "com.wfm.service.StaffingRequirementBusinessDateDeleteTest"` -> BUILD
  SUCCESSFUL, 3/3 pass; `./gradlew test --tests "com.wfm.service.BusinessDateJoinGuardTest"` ->
  BUILD SUCCESSFUL, green with empty allowlist; `./gradlew test --tests
  "com.wfm.service.StaffingRequirementErlangTest"` -> BUILD SUCCESSFUL, undisturbed; full
  unfiltered `./gradlew test` -> 1180 tests, 0 failed, 0 errors, 4 skipped, 10m18s, across all 192
  JUnit XML result files.
- Red/green self-check (beyond the plan's own verify list): production migration temporarily
  reverted, re-ran the new test class -- exactly the 21:00 case failed with the expected
  AssertionError, the other two cases still passed; restored, `git diff` showed zero difference
  from the committed state, re-ran green.
