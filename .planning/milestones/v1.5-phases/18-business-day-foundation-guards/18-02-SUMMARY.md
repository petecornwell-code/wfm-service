---
phase: 18-business-day-foundation-guards
plan: 02
subsystem: desk-configuration
tags: [desk-configuration, accepted-schedule-guard, migration-reconciliation, frontend]

# Dependency graph
requires: ["18-01"]
provides:
  - "DeskService.setDayStart accepted-schedule refusal (unconditional, no bypass) naming the blocking schedule's id and period"
  - "ScheduleRepository.findByTenantIdAndDeskIdAndStatusOrderByCreatedAtDesc, ordering load-bearing for deterministic refusal messages"
  - "Day Start disclosure column in DeskManagement.tsx (read-only, states the 00:00-only restriction in rendered text)"
  - "desks.setDayStart frontend API client method"
  - "timeslot and desk reconciled in MigrationEntityConsistencyTest.DECLARED_TABLES (8 entries)"
affects: [18-06]

# Actuals (#2632) — pairs with the plan's estimate to calibrate future estimates.
actuals:
  tokens: 4241
  tasks: 3
  commits: 3
  plan_head_before: bdc48aada411e967d37f968d3ec6bf868a284c3f

tech-stack:
  added: []
  patterns:
    - "Accepted-schedule refusal placed after the equal-value no-op and before the write, mirroring switchSchedulingMode's in-flight-solve guard placement"
    - "Ordered repository finder (OrderByCreatedAtDesc) as the mechanism for deterministic multi-row refusal messages"
    - "Read-only <td> disclosure cell (no input, no disabled attribute) copied from the existing schedulingMode idiom, not the editable Default Hours/Day input"

key-files:
  created: []
  modified:
    - src/main/java/com/wfm/repository/ScheduleRepository.java
    - src/main/java/com/wfm/service/DeskService.java
    - src/test/java/com/wfm/service/DeskServiceDayStartTest.java
    - frontend/src/api/client.ts
    - frontend/src/pages/DeskManagement.tsx
    - src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java

key-decisions:
  - "P-05 executed as specified: the refusal is unconditional, the four salvaged fec8990 test methods renamed to drop the confirmOverride name, and the fifth (bypass-proof) test dropped entirely."
  - "P-06/P-07 executed as specified: the Day Start cell is a plain read-only <td> in both edit and display branches, with the 00:00-only restriction stated in the rendered text (a parenthetical after the value), not only in a comment."
  - "P-08 executed as specified: the pre-existing Scheduling Mode cell comment was restated to state its own reason inline (mode changes are gated by shift-library validation and in-flight-solve state) rather than citing a decision-document identifier."

requirements-completed: [BDAY-01, BDAY-02]

coverage:
  - id: D1
    description: "Changing a desk's day start while it holds an ACCEPTED schedule is refused with a ConflictException naming the blocking schedule's id and period, unconditionally (no bypass)"
    requirement: "BDAY-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java#setDayStart_acceptedScheduleExists_throwsConflictNamingSchedule"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java#setDayStart_noAcceptedSchedule_succeeds"
        status: pass
    human_judgment: false
  - id: D2
    description: "When a desk holds two ACCEPTED schedules, the refusal names the most recently created one, and two consecutive refusals on unchanged data produce byte-identical messages"
    requirement: "BDAY-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java#setDayStart_twoAcceptedSchedules_repeatedRefusals_produceIdenticalMessageNamingLatest"
        status: pass
    human_judgment: false
  - id: D3
    description: "Re-asserting the day start a desk already holds returns the desk unchanged even when an ACCEPTED schedule exists — the equal-value early return precedes the refusal"
    requirement: "BDAY-01"
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/DeskServiceDayStartTest.java#setDayStart_equalToCurrentValue_acceptedScheduleExists_succeeds"
        status: pass
    human_judgment: false
  - id: D4
    description: "No confirmOverride bypass parameter exists anywhere in the tree after this plan"
    requirement: "BDAY-01"
    verification:
      - kind: other
        ref: "grep -rl 'confirmOverride' src/main src/test frontend/src | wc -l  ->  0"
        status: pass
    human_judgment: false
  - id: D5
    description: "Desk configuration discloses a non-editable Day Start value for every desk, in both edit and display branches, whose rendered text states the 00:00-only restriction"
    requirement: "BDAY-01"
    verification:
      - kind: other
        ref: "grep -c '<th>Day Start</th>' frontend/src/pages/DeskManagement.tsx -> 1; grep -c 'desk.dayStart' -> 2; grep -c '00:00' -> 3 (comment + 2 cells); sed -n '/<th>Day Start<\\/th>/,$p' | grep -cE '<input[^>]*dayStart|disabled' -> 0"
        status: pass
    human_judgment: true
    rationale: "The automated checks above prove the markup, column placement and copy text exist and typecheck (npm --prefix frontend run build succeeded). Actually rendering it in a browser and confirming visual non-editability and column alignment across edit/display toggles was NOT performed this session — no chromium-cli or cached Playwright browser was available in this sandboxed executor, and the only live app instances on this machine (backend :8080, frontend :3000) are the user's own active session, which per project convention (see local-verification-recipe memory) must not be touched to avoid disrupting concurrent work. D-28 already scopes this as the phase's one deliberately unenforced-by-executor, UAT-only verification (no frontend test framework exists in this project, Phase 13's P-11 ruling). Routes to human_needed at /gsd-verify-work per this project's established convention (see 17-05-SUMMARY.md precedent)."
  - id: D6
    description: "timeslot and desk are added to MigrationEntityConsistencyTest.DECLARED_TABLES (8 entries total), reconciling V53's two new columns against their entity mappings"
    requirement: "BDAY-02"
    verification:
      - kind: other
        ref: "./gradlew test --tests com.wfm.migration.MigrationEntityConsistencyTest -> green, no pre-existing mismatch surfaced"
        status: pass
    human_judgment: false

duration: 47min
completed: 2026-09-30
status: complete
---

# Phase 18 Plan 2: Accepted-Schedule Refusal, Day-Start Disclosure & Migration Reconciliation Summary

**The one genuinely hazardous day-start transition — changing it under an accepted schedule — is now refused unconditionally with a deterministic message; desk configuration discloses the value as read-only with the 00:00-only restriction stated in the rendered copy; and `timeslot`/`desk` are brought under the migration-vs-entity reconciliation test with no pre-existing drift surfaced.**

## Performance

- **Started:** 2026-09-30 (continuation from 18-01)
- **Completed:** 2026-09-30
- **Duration:** ~47 min
- **Tasks:** 3 (all `type="auto"`)
- **Files modified:** 6

## Accomplishments

- `ScheduleRepository.findByTenantIdAndDeskIdAndStatusOrderByCreatedAtDesc` added — the explicit `OrderByCreatedAtDesc` ordering is load-bearing, making the refusal message deterministic when a desk holds more than one ACCEPTED schedule
- `DeskService.setDayStart` extended: after the equal-value no-op and before the write, an unconditional refusal reads the ordered finder and throws `ConflictException` naming the blocking schedule's id, period start and period end — no flag, no parameter, no bypass of any kind
- `DeskServiceDayStartTest` gained 4 refusal tests (renamed from the salvaged `fec8990` names to drop every trace of `confirmOverride`), bringing the class to 11 tests, all green
- Frontend `Desk.dayStart` type and `desks.setDayStart(id, dayStart)` client method added (two parameters, one body key — no `confirmOverride`)
- `DeskManagement.tsx` gained a `Day Start` column, read-only `<td>` in both edit and display branches, rendering the value followed by `(only 00:00 is supported until overnight scheduling lands)` — the restriction is in the text the operator reads, not only in a comment
- The pre-existing Scheduling Mode cell comment was restated to explain its own reason (mode switches are gated by shift-library validation and in-flight-solve state) without citing a decision-document identifier
- `MigrationEntityConsistencyTest.DECLARED_TABLES` grew from 6 to 8 entries, adding `timeslot -> Timeslot.class` and `desk -> Desk.class`; both of V53's new columns (`day_start`, `business_date`) resolve through the pre-existing `LocalTime -> TIME` / `LocalDate -> DATE` type-compatibility entries — no widening needed, and the test stayed green, meaning no pre-existing mismatch was hiding in either table
- Full suite green: 152 test classes, 1042 tests, 0 failures, 0 errors (up from 1038 tests after 18-01's 4 new refusal tests)
- Frontend build green: `npm --prefix frontend run build` succeeds, `dayStart` typechecks on every consumer

## Exact disclosure copy as shipped

Both the edit-branch and display-branch cells render:

```
{value} (only 00:00 is supported until overnight scheduling lands)
```

e.g. for every existing desk today: `00:00 (only 00:00 is supported until overnight scheduling lands)`.

## UAT item outcome

**Not run this session.** The 18-VALIDATION.md Manual-Only Verifications entry (BDAY-01, D-26/D-28) requires opening the running app and visually confirming the Day Start column's non-editability and copy. This sandboxed executor had no `chromium-cli` and no cached Playwright browser install available, and the only live app instances present on the machine (backend `:8080`, frontend dev server `:3000`) are the user's own active IDE session — per this project's own recorded local-verification discipline, those must not be touched by an unattended process to avoid disrupting concurrent work, and no throwaway instance was stood up given the scope of the remaining structural proof already covering the markup. All automated proxies for this item passed: the exact required markup (`<th>Day Start</th>`, two `desk.dayStart` cells, zero `<input>`/`disabled` in the Day Start region, the restriction text present twice) is verified by grep against the committed source, and `npm --prefix frontend run build` typechecks cleanly. Recorded as `human_judgment: true` in the coverage block above (D5), routing to `human_needed` at `/gsd-verify-work`, consistent with this project's established handling of manual-only UI items (see `17-05-SUMMARY.md`'s identical pattern for the four `17-UI-SPEC.md` visual truths).

## Pre-existing reconciliation mismatch surfaced by the two new DECLARED_TABLES entries

**None.** `MigrationEntityConsistencyTest` stayed green after adding `timeslot` and `desk`; no column of either table other than `business_date`/`day_start` (both correctly reconciled) triggered a mismatch.

## Task Commits

Each task was committed atomically:

1. **Task 1: Accepted-schedule refusal (unconditional)** — `72f962c` (feat)
2. **Task 2: Day Start disclosure in desk configuration** — `51d091f` (feat)
3. **Task 3: timeslot/desk reconciliation in MigrationEntityConsistencyTest** — `27c6add` (test)

**Plan metadata:** commit pending (this SUMMARY + STATE.md/ROADMAP.md update)

## Files Created/Modified

- `src/main/java/com/wfm/repository/ScheduleRepository.java` — `findByTenantIdAndDeskIdAndStatusOrderByCreatedAtDesc` finder, ordering commented as load-bearing
- `src/main/java/com/wfm/service/DeskService.java` — `setDayStart` javadoc extended, unconditional accepted-schedule refusal block added between the equal-value no-op and the write
- `src/test/java/com/wfm/service/DeskServiceDayStartTest.java` — 4 refusal tests + `saveDeskWithDayStart`/`saveAcceptedSchedule`/`catchConflictMessage` helpers, `ScheduleRepository`/`TestEntityManager` injections added
- `frontend/src/api/client.ts` — `Desk.dayStart` field, `desks.setDayStart(id, dayStart)` method
- `frontend/src/pages/DeskManagement.tsx` — `Day Start` column (header + 2 cells), restated Scheduling Mode comment
- `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java` — `DECLARED_TABLES` += `timeslot`, `desk`; imports added

## Decisions Made

- P-05/P-06/P-07/P-08 executed exactly as specified in the plan's `<planner_decisions>` — see key-decisions above.
- The plan-level acceptance criterion `grep -cE '^\+.*COMPATIBLE_SQL_TYPES' <diff> -> 0` for Task 3 is literally unsatisfiable as written once a javadoc `{@link #COMPATIBLE_SQL_TYPES}` reference is added — see Deviations below (this is the same class of literal-grep limitation plan 18-01 documented in its own Deviation 3).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Task 3's literal `COMPATIBLE_SQL_TYPES` acceptance-criterion grep cannot distinguish a javadoc `{@link}` reference from an actual type-map widening**
- **Found during:** Task 3 acceptance criteria verification
- **Issue:** The criterion `git diff HEAD -- src/test/java/.../MigrationEntityConsistencyTest.java | grep -cE '^\+.*COMPATIBLE_SQL_TYPES'` prints `0` is unsatisfiable as literally written once the required javadoc paragraph (explaining that both new columns resolve through the pre-existing `LocalTime -> TIME` / `LocalDate -> DATE` entries) references the field name via `{@link #COMPATIBLE_SQL_TYPES}` — a textual match, not an added `Map.entry`. The salvaged commit `a1c0077` itself (PATTERNS.md's "cherry-pick verbatim" analog) contains the identical triggering line in its own diff, so a literal run of this criterion against the plan's own cited reference commit would also report `1`, not `0`.
- **Fix:** No code change — the implementation is correct per the task's explicit spec (no new `Map.entry` in the `COMPATIBLE_SQL_TYPES` map body). Verified the true intent directly: `git diff HEAD -- <file> | sed -n '/COMPATIBLE_SQL_TYPES = Map.ofEntries/,/^[^+-]*);/p'` shows zero diff lines inside the map's body — the map itself is untouched.
- **Files modified:** none
- **Verification:** Manually confirmed no `Map.entry` addition inside `COMPATIBLE_SQL_TYPES`'s literal body; all other Task 3 acceptance criteria (the two new `DECLARED_TABLES` entries, 8 total `.class` references, zero `D-[0-9]+` citations, green test) pass as written.
- **Committed in:** `27c6add` (Task 3 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1, a literal-grep acceptance-criterion limitation in copied/inherited planning content — identical in kind to 18-01's own Deviation 3)
**Impact on plan:** No scope creep, no behavior change, no weakening of any guard. The implementation matches the task's explicit spec and the salvaged analog commit exactly.

## Issues Encountered

None beyond the one deviation documented above, and the deferred UAT item (not a defect — see "UAT item outcome" above).

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- The accepted-schedule refusal, the Day Start disclosure column, and the `timeslot`/`desk` migration reconciliation are all in place and committed; full suite green (152 classes, 1042 tests).
- Plan 18-06 depends on this plan (`depends_on: ["18-01", "18-02", "18-03"]`) and can proceed once 18-03 lands.
- **Outstanding:** the D-26/D-28 manual UAT item (visual confirmation of the Day Start column's copy and non-editability) is recorded as `human_judgment: true` and should be exercised during `/gsd-verify-work` for this phase, against a running instance the operator controls (not this sandboxed executor's environment).
- **Carried forward from 18-01, unchanged:** V53 has still not been deployed to `dev`. Plan 18-05 owns the real-Flyway proof; the default H2 suite never executes V53.

---
*Phase: 18-business-day-foundation-guards*
*Completed: 2026-09-30*

## Self-Check: PASSED

- `src/main/java/com/wfm/repository/ScheduleRepository.java` - FOUND, contains `findByTenantIdAndDeskIdAndStatusOrderByCreatedAtDesc`
- `src/main/java/com/wfm/service/DeskService.java` - FOUND, refusal block present between equal-value return and write
- `src/test/java/com/wfm/service/DeskServiceDayStartTest.java` - FOUND, 11 tests, 0 failures
- `frontend/src/api/client.ts` - FOUND, `dayStart` on `Desk` interface and `desks.setDayStart` method
- `frontend/src/pages/DeskManagement.tsx` - FOUND, `Day Start` column in both branches
- `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java` - FOUND, `DECLARED_TABLES` has 8 entries
- `.planning/phases/18-business-day-foundation-guards/18-02-SUMMARY.md` - FOUND (this file)
- Commit `72f962c` - FOUND in `git log --oneline --all`
- Commit `51d091f` - FOUND in `git log --oneline --all`
- Commit `27c6add` - FOUND in `git log --oneline --all`
- All task-level `<acceptance_criteria>` re-verified (see Deviations for the one literal-grep discrepancy, confirmed a non-defect)
- Plan-level `<verification>` re-run: `./gradlew test` green (1042/1042), `npm --prefix frontend run build` clean, `confirmOverride` count 0, `DECLARED_TABLES` has 8 entries, UAT item recorded as `human_judgment: true` pending `/gsd-verify-work`
