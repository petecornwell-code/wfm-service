---
phase: 24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida
plan: 02
subsystem: solver-post-processing
tags: [java, spring, business-date, overnight-shifts, structural-guard, junit5, tdd]

requires:
  - phase: 24-01
    provides: "Widened verb-free BusinessDateJoinGuardTest committed red on 14 sites; the validator's 3 sites already migrated"
  - phase: 18-20
    provides: "Timeslot.businessDate, DayWindow day-start vocabulary, AgentShiftAssignment.getDate() as the business date"
provides:
  - "ScheduleEnvelopeRepairService keys every seat-side map, sort and log on Timeslot.getBusinessDate() (7 sites)"
  - "ScheduleConsistencyRepairService.seatsByDateAgent keyed on the business date (1 site)"
  - "ShiftStartMixTargetService reqByDate, seatsByDate, slotsByDate keyed on the business date (3 sites)"
  - "BusinessDateJoinGuardTest GREEN with an empty Allowlist and four live liveness proofs"
  - "bday-join-guard.md: widened scope, fourteen-site red-then-green history, classification of every remaining Timeslot calendar-date read"
affects: [24-03, schedule-envelope-repair, usual-shift-consistency, shift-start-mix, bday-join-guard]

actuals:
  tokens: 11954
  tasks: 3
  commits: 5
plan_head_before: 46ed942ff5b75a42d42c14cc375187c704ae30d8
plan_head_after: 7eb665694f1c7a79f7182ac0390b249c844fcca1

tech-stack:
  added: []
  patterns:
    - "Key-only fix: re-key a map on the business date, leave the verified re-score-and-revert guard rails untouched"
    - "Hand-built Timeslot fixtures set a business date equal to the calendar date, which is inert at a 00:00 anchor and keeps every midnight test unchanged"
    - "A second pipeline liveness proof whose offender is invisible to the other scan's predicate, so an empty allowlist cannot be vacuous"

key-files:
  created:
    - src/test/resources/bday-join-guard-offender-widened/OffendingSample.java
  modified:
    - src/main/java/com/wfm/service/ScheduleEnvelopeRepairService.java
    - src/main/java/com/wfm/service/ScheduleConsistencyRepairService.java
    - src/main/java/com/wfm/service/ShiftStartMixTargetService.java
    - src/main/java/com/wfm/solver/AgentAssignmentDifficultyComparator.java
    - src/test/java/com/wfm/service/ScheduleEnvelopeRepairServiceTest.java
    - src/test/java/com/wfm/service/ScheduleConsistencyRepairServiceTest.java
    - src/test/java/com/wfm/service/ShiftStartMixTargetServiceTest.java
    - src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java
    - src/test/resources/bday-join-guard.md

key-decisions:
  - "Violation sort in ScheduleEnvelopeRepairService is business date, then window.anchoredStartMinute, then id: chronological from the day start, identical to the old order at 00:00"
  - "AgentAssignmentDifficultyComparator keeps the CALENDAR date (comment only): calendar date plus clock time is true chronological construction order at every anchor"
  - "BusinessDayPeriodLoader and the other remaining calendar reads are classified in bday-join-guard.md, not allowlisted, because none is in a scanned list"
  - "The Allowlist block stays empty; no entry added for a migrated site, no narrowing of WIDENED_TARGET_FILES, no covered_digest touched"

patterns-established:
  - "Red-then-green per service with the RED output recorded in the SUMMARY"

requirements-completed: [SOLV-07, BDAY-02, BDAY-05]

coverage:
  - id: D1
    description: "On a 06:00 desk a post-midnight seat outside its business-day envelope is found and moved to the free in-envelope calendar-next-day seat: violationsFound 1, repaired 1, eight seats held, score 0"
    requirement: "SOLV-07"
    verification:
      - kind: unit
        ref: "ScheduleEnvelopeRepairServiceTest#findsAndRepairsAPostMidnightSeatOutsideItsBusinessDayEnvelope"
        status: pass
    human_judgment: false
  - id: D2
    description: "A legal post-midnight seat is never judged against the next business day's envelope: violationsFound 0 and no seat changes holder"
    requirement: "SOLV-07"
    verification:
      - kind: unit
        ref: "ScheduleEnvelopeRepairServiceTest#neverJudgesAPostMidnightSeatAgainstTheNextBusinessDaysEnvelope"
        status: pass
    human_judgment: false
  - id: D3
    description: "A usual-shift swap on a non-midnight desk carries the post-midnight seats with their envelope (A holds hours 20 and 2, B holds 21 and 1)"
    requirement: "BDAY-02"
    verification:
      - kind: unit
        ref: "ScheduleConsistencyRepairServiceTest#postMidnightSeatsFollowTheirEnvelopeInASwap"
        status: pass
    human_judgment: false
  - id: D4
    description: "A business day whose whole demand falls after calendar midnight still receives start-mix targets (21:00 and 22:00, dated 2026-10-05, summing to 3)"
    requirement: "BDAY-02"
    verification:
      - kind: unit
        ref: "ShiftStartMixTargetServiceTest#aBusinessDayWhoseDemandFallsAfterCalendarMidnight_stillGetsTargets"
        status: pass
    human_judgment: false
  - id: D5
    description: "00:00 control: every pre-existing test in the three service test classes and ShiftStartMixSteerTest passes unchanged"
    requirement: "BDAY-02"
    verification:
      - kind: unit
        ref: "ScheduleEnvelopeRepairServiceTest, ScheduleConsistencyRepairServiceTest, ShiftStartMixTargetServiceTest, ShiftStartMixSteerTest (7, 8, 14, 3 tests, 0 failures)"
        status: pass
    human_judgment: false
  - id: D6
    description: "BusinessDateJoinGuardTest GREEN with an empty Allowlist; widened pipeline proof goes red on a verb-free offender the verb scan cannot see"
    requirement: "BDAY-05"
    verification:
      - kind: unit
        ref: "BusinessDateJoinGuardTest#businessDateJoinKeyPositionsInProductionCode_matchTheAllowlistExactly"
        status: pass
      - kind: unit
        ref: "BusinessDateJoinGuardTest#widenedPipelineRedProof_walkStripMatchAndSetCompareAreAllLive"
        status: pass
    human_judgment: false
  - id: D7
    description: "Re-keyed repair and start-mix services hold no state across calls, so a solve interrupted mid-repair or two desks repaired concurrently cannot observe each other's maps"
    requirement: "SOLV-07"
    verification: []
    human_judgment: true
    rationale: "Plan marker verification: backstop. No test in this repo proves cross-call isolation of a post-solve repair; the property follows from every map being built per call. The verifier must abstain to human_needed."

duration: 5min
completed: 2026-10-07
status: complete
---

# Phase 24 Plan 02: Business-date keys in repair and start-mix services Summary

**Eleven key edits across the envelope-repair, usual-shift-swap and start-mix services re-key post-midnight seats, demand and slots on the business date, each proven red-then-green on a 06:00 desk, which turns the widened BusinessDateJoinGuardTest green with an empty allowlist.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-10-07T17:49:18Z
- **Completed:** 2026-10-07T17:54:41Z
- **Tasks:** 3
- **Files modified:** 10 (9 modified, 1 created; `git diff --stat`: 533 insertions, 30 deletions)

## Accomplishments

- **Envelope repair (7 sites).** A post-midnight seat now finds its own business day's envelope. On a 06:00 desk the calendar-Tuesday 05:00-06:00 seat outside a business-Monday 21:00-05:00 envelope is found and moved onto the free calendar-Tuesday 03:00 seat (violationsFound 1, violationsRepaired 1, eight seats held, score 0). A legal post-midnight seat is no longer judged against the next business day's 07:00-15:00 envelope.
- **Usual-shift swap (1 site).** `seatsByDateAgent` is keyed on the business date, matching the `byDate` grouping it is read with. Two agents swapping overnight envelopes now exchange their calendar-next-day seats too.
- **Start-mix targets (3 sites).** A business day whose whole demand falls after calendar midnight no longer looks demand-free to GUARD 1.
- **Guard green, empty allowlist.** `BusinessDateJoinGuardTest` is GREEN with zero allowlist entries. A second pipeline proof against `bday-join-guard-offender-widened/OffendingSample.java` asserts the verb-scoped predicate finds nothing in the fixture while the widened one finds exactly one entry, so the empty allowlist is not vacuous.
- **Remaining sites classified.** `bday-join-guard.md` records the widened scope, the fourteen-site red-then-green history, and the reason each remaining `Timeslot` calendar-date read in `src/main/java` is deliberate.

## RED evidence (before each fix)

`./gradlew test --tests ... --rerun-tasks`, failure messages read from the JUnit XML.

- **Task 1** (`b06f683`, 7 tests, 2 failed; the 5 pre-existing passed):
  - `findsAndRepairsAPostMidnightSeatOutsideItsBusinessDayEnvelope`: `ScheduleEnvelopeRepairServiceTest.java:204` `expected: 1 but was: 0` (violationsFound).
  - `neverJudgesAPostMidnightSeatAgainstTheNextBusinessDaysEnvelope`: `ScheduleEnvelopeRepairServiceTest.java:227` `expected: 0 but was: 5`.
  - Both match the plan's predicted pre-fix values exactly (0, and 5 calendar-Tuesday seats judged against Tuesday's day envelope).
- **Task 2** (`309832f`, 22 tests across the two classes, 2 failed):
  - `postMidnightSeatsFollowTheirEnvelopeInASwap`: `Expecting actual: [1, 20] to contain exactly in any order: [20, 2]` (A kept its original 01:00 seat; the plan predicted `{20, 1}`).
  - `aBusinessDayWhoseDemandFallsAfterCalendarMidnight_stillGetsTargets`: `Expected size: 2 but was: 0` (empty list from GUARD 1).
- **Task 3**: the guard was already GREEN once Task 2's fix landed (the 11 owned sites were gone), so the new widened pipeline proof was added alongside a green headline. It is the one test in this plan that was not seen red first; its red-ness is structural (it asserts that `assertThat(derived).containsExactlyInAnyOrderElementsOf(Set.of())` throws `AssertionError` and that the verb scan is empty on the fixture), and it passed on the first run.

## Final verification (all with `--rerun-tasks`, XML read per class)

| Class | Tests | Failures |
|-------|-------|----------|
| ScheduleEnvelopeRepairServiceTest | 7 | 0 |
| ScheduleConsistencyRepairServiceTest | 8 | 0 |
| ShiftStartMixTargetServiceTest | 14 | 0 |
| ShiftStartMixSteerTest | 3 | 0 |
| BusinessDateJoinGuardTest | 8 | 0 |
| MidnightTimeArithmeticGuardTest | 12 | 0 |
| BusinessDateWritePathGuardTest | 8 | 0 |

Source assertions: zero live `getTimeslot().getDate()` in `ScheduleEnvelopeRepairService` (7 `getBusinessDate()`) and `ScheduleConsistencyRepairService`; zero `ts.getDate()` and exactly 3 `ts.getBusinessDate()` in `ShiftStartMixTargetService`; `after.hardScore() > before.hardScore()` appears once, unchanged. Allowlist `awk` count prints `0`. The comparator diff adds only `//` lines. `midnight-time-arithmetic.md` is unchanged since the 24-01 plan-add commit. No `*-VERIFICATION.md` file was touched. No full-suite run (24-03 owns the phase gate).

## Task Commits

1. **Task 1: envelope repair keyed on the business date** (tracer)
   - `b06f683` test: red, envelope repair misses post-midnight seats on a 06:00 desk
   - `87fa6f6` fix: key envelope repair seats on the business date
2. **Task 2: usual-shift swap and start-mix keyed on the business date**
   - `309832f` test: red, swap and start-mix lose post-midnight rows
   - `499d10e` fix: key usual-shift swap and start-mix maps on the business date
3. **Task 3: widened guard green, second pipeline proof, classification**
   - `7eb6656` test: widened business-date guard green with an empty allowlist

**Plan metadata:** recorded in the follow-up `docs(24-02)` commit.

## Files Created/Modified

- `src/main/java/com/wfm/service/ScheduleEnvelopeRepairService.java` - seven business-date key edits, sort now business date then anchored start minute then id, `AgentDay` javadoc and free-seat comment updated
- `src/main/java/com/wfm/service/ScheduleConsistencyRepairService.java` - `seatsByDateAgent` key
- `src/main/java/com/wfm/service/ShiftStartMixTargetService.java` - `reqByDate`, `seatsByDate`, `slotsByDate` keys
- `src/main/java/com/wfm/solver/AgentAssignmentDifficultyComparator.java` - classification comment only
- `src/test/java/com/wfm/service/ScheduleEnvelopeRepairServiceTest.java` - `envelopeScorer(LocalTime)`, `overnightDesk` fixture, two tests, business date on the existing fixture
- `src/test/java/com/wfm/service/ScheduleConsistencyRepairServiceTest.java` - four-argument `seat` overload, `postMidnightSeatsFollowTheirEnvelopeInASwap`
- `src/test/java/com/wfm/service/ShiftStartMixTargetServiceTest.java` - `overnightDesk` fixture, `aBusinessDayWhoseDemandFallsAfterCalendarMidnight_stillGetsTargets`
- `src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java` - `OFFENDER_ROOT_WIDENED`, `widenedPipelineRedProof_walkStripMatchAndSetCompareAreAllLive`, javadoc names all four proofs
- `src/test/resources/bday-join-guard-offender-widened/OffendingSample.java` - never-compiled verb-free offender (one real line, one commented-out copy)
- `src/test/resources/bday-join-guard.md` - widened scope, Phase 24 history, remaining-site classification; Allowlist block empty

## Decisions Made

- The violation sort uses `window.anchoredStartMinute` so a business day's 01:00 seat sorts after its 21:00 seat on a 06:00 desk; at a 00:00 anchor it is identical to the old start-time order.
- `AgentAssignmentDifficultyComparator` stays on the calendar date. It is not in a scanned list, so it is classified in `bday-join-guard.md` rather than allowlisted.
- `ShiftStartMixTargetService`'s per-date slot sort by start time was left alone, as the plan specified (slot order does not affect the objective).

## Deviations from Plan

None - plan executed exactly as written.

The D-06 scope bound held: every red test went green on key changes alone. `repairVerified`'s strict-hard-improvement rule, the batch/fallback structure, `MAX_RESCORES` and `MAX_CANDIDATES_PER_VIOLATION` are untouched.

## Issues Encountered

- A first attempt to correct a method name in `bday-join-guard.md` with `sed` failed on BSD sed (the replacement text contained a `/`); redone with a short Python edit. No effect on the committed content.
- Task 3's new widened pipeline proof was never seen red in isolation (see RED evidence). The deliberately red state of the headline guard ended when Task 2 landed.

## Known Stubs

None.

## Threat Flags

None. No new endpoint, parameter, query or persisted field. T-24-03 is mitigated by tests that assert the exact seats moved and that a legal seat is never moved; both `repairVerified` guard rails are untouched. T-24-04 is mitigated by set equality, the `awk` empty-allowlist count and four liveness proofs.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 24-03 owns the single phase gate (full suite); the guard is GREEN from this plan on.
- **Do not push.** `.github/workflows/deploy.yml` deploys to the live `dev` system on every push to this branch; pushing is the operator's decision after the phase gate.
- Open item for the verifier: coverage item D7 is a `backstop` truth (per-call state isolation), which no test proves; it must abstain to `human_needed`.
- FA-04 stands: a persisted Timeslot cannot carry a null business date (`NOT NULL` since V53), so the only null risk introduced here is hand-built fixtures, each fixed and checked by count.

## Self-Check: PASSED

Files verified present: the widened offender fixture, `bday-join-guard.md`, the three re-keyed services, the comparator and the four test files.
Commits verified as ancestors of HEAD: `b06f683`, `87fa6f6`, `309832f`, `499d10e`, `7eb6656`. `commits: 5` measured from `git rev-list --count 46ed942..HEAD`.

---
*Phase: 24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida*
*Completed: 2026-10-07*
