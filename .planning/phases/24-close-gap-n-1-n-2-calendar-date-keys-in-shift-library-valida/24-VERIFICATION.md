---
phase: 24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida
verified: 2026-10-08T15:00:00Z
status: passed
score: 34/34 must-haves verified
covered_files:
  - .planning/phases/24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida/24-01-PLAN.md
  - .planning/phases/24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida/24-01-SUMMARY.md
  - .planning/phases/24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida/24-02-PLAN.md
  - .planning/phases/24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida/24-02-SUMMARY.md
  - .planning/phases/24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida/24-03-PLAN.md
  - .planning/phases/24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida/24-03-SUMMARY.md
  - frontend/src/api/client.ts
  - frontend/src/pages/ScheduleResults.tsx
  - src/main/java/com/wfm/controller/StaffingRequirementController.java
  - src/main/java/com/wfm/dto/StaffingRequirementResponse.java
  - src/main/java/com/wfm/repository/StaffingRequirementRepository.java
  - src/main/java/com/wfm/service/ScheduleConsistencyRepairService.java
  - src/main/java/com/wfm/service/ScheduleEnvelopeRepairService.java
  - src/main/java/com/wfm/service/ShiftLibraryGenerationService.java
  - src/main/java/com/wfm/service/ShiftLibraryValidationService.java
  - src/main/java/com/wfm/service/ShiftStartMixTargetService.java
  - src/main/java/com/wfm/service/StaffingRequirementService.java
  - src/main/java/com/wfm/solver/AgentAssignmentDifficultyComparator.java
  - src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java
  - src/test/java/com/wfm/service/CallIsolation.java
  - src/test/java/com/wfm/service/ScheduleConsistencyRepairServiceTest.java
  - src/test/java/com/wfm/service/ScheduleEnvelopeRepairServiceTest.java
  - src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java
  - src/test/java/com/wfm/service/ShiftStartMixTargetServiceTest.java
  - src/test/java/com/wfm/service/StaffingRequirementListBusinessRangeTest.java
  - src/test/resources/bday-join-guard-offender-widened/OffendingSample.java
  - src/test/resources/bday-join-guard.md
covered_digest: "v3:sha256:a18b1760822a24c4d383c3bc8684b0aad043fdbf1d6003949010f27321d0e5b6"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 32/34
  gaps_closed: []
  gaps_remaining: []
  regressions: []
coincidental_reliance_items: []
---

# Phase 24: Close Gap N-1/N-2 Verification Report

**Phase Goal:** Every surface that decides which business day a timeslot belongs to agrees with the solver, so a weekday-restricted overnight template validates against the hours the solver can actually give it, and the Agent Allocation Required / Over-under rows compare like with like (v1.5 re-audit gaps N-1 and N-2).
**Verified:** 2026-10-08
**Status:** passed
**Re-verification:** Yes. The previous report (2026-10-07) was `human_needed` with no `gaps:` section. This report was regenerated in full, not patched.

## Goal Achievement

The goal is achieved. Both gaps are closed at the source: the shift-library validator, the generator, the envelope and usual-shift repair services, the start-mix service, and the allocation-row demand fetch all key on the stored `Timeslot.business_date`. I re-ran the affected test classes (all green) and re-ran two mutations against the code (validator key reverted, and two leaks planted in a service). Each mutation turned the intended tests red. No truth failed. Each of the four items the previous report routed to a human now has evidence, and nothing is left that needs a human to judge.

ROADMAP Phase 24 has a Goal and a Context but no Success Criteria list, so the truths below come from the three PLAN `must_haves` blocks (34 truths and 6 prohibitions) and are traced to the Goal.

### What changed since the previous report

| Previous human item | Now | Basis |
|---|---|---|
| D7 per-call state isolation (truth 24, backstop, PRESENT_BEHAVIOR_UNVERIFIED) | VERIFIED | Behavioural tests exist and I re-ran them and mutated against them (below) |
| Agent Allocation rows on a 06:00 desk (truth 33, UNCERTAIN) | VERIFIED | Operator observation, `24-UAT.md` test 2 |
| Six judgment-tier prohibitions | Confirmed | Operator, `24-UAT.md` test 3 |
| 24-03 phase-gate shortfall | Accepted | Operator, `24-UAT.md` test 4; today's full run: 218 XML, 1538 tests, 0 failures, 0 errors, 4 skipped |

### Observable Truths

**Plan 24-01 (N-1: shift-library validation)**

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Monday-only 21:00-06:00 template on a 06:00 desk covers its own post-midnight hours; `requireShiftModeReady` does not throw | VERIFIED | `findUncoveredWindows` builds `Window` from `sr.getTimeslot().getBusinessDate()` (ShiftLibraryValidationService.java:230); `requireShiftModeReady_mondayOnlyOvernightTemplate_acceptsItsOwnPostMidnightHours` passes. With :230 reverted to `getDate()` it fails. |
| 2 | Same template is NOT credited with business-Sunday's calendar-Monday 01:00 hour | VERIFIED | `validate_previousBusinessDaysPostMidnightWindow_isNotCreditedToTheCalendarWeekdaysTemplate` passes; red under my mutation |
| 3 | `isEffectiveOn` judged on the business date | VERIFIED | `validate_effectiveFromIsJudgedOnTheBusinessDate` passes; red under mutation |
| 4 | `covers` stays the single predicate; generator calls it unchanged | VERIFIED | One `static covers`; generator reads the business date at :185, :236, :509, :654 and calls the shared predicate; 31 generation tests pass |
| 5 | Unsatisfiable-weekday findings bucket by business weekday | VERIFIED | :457 maps `getBusinessDate()`; asserted inside truth 1's test |
| 6 | Post-midnight peak shortfall yields one advisory dated 2026-10-05 naming Monday, short by 2 | VERIFIED | :578 `LocalDate date = sr.getTimeslot().getBusinessDate()`; `validate_peakShortfallOnAPostMidnightHour_bucketsOnTheBusinessWeekday` passes |
| 7 | Operator text names the business date, discloses the calendar date only when it differs, via one shared `Window.describe` | VERIFIED | Exact-string test passes (`validate_uncoveredPostMidnightWindow_namesTheBusinessDayAndDisclosesTheCalendarDate`, red under mutation); generator builds its coverage detail from the same `describe` |
| 8 | At a 00:00 anchor every validator string is byte-identical | VERIFIED | `validate_midnightDesk_uncoveredStringsStayByteIdentical` passes (67/67 in the class) |
| 9 | Adjacency at the day start belongs to the correct business day, undecorated | VERIFIED | `validate_windowStartingExactlyAtTheDayStart_...` passes; red under mutation |
| 10 | `uncoveredWindows` ordered by business date, anchored start, anchored end | VERIFIED | `validate_uncoveredWindowsOnAnAnchoredDesk_areOrderedFromTheDayStart` passes; red under mutation |
| 11 | Widened guard went red against the unmigrated tree before any production edit | VERIFIED | Commit order from the previous report stands (`d763d82` test-only, before `623258f`); history unchanged since |
| 12 | No allowlist entry added; `TARGET_FILES`/`JOIN_VERB_TOKENS` unchanged; explicit four-file widened list | VERIFIED | `bday-join-guard.md` allowlist empty; guard 8/8 green; mutation turns its headline test red, so the empty allowlist is not vacuous |
| 13 | `PeakShortfallAdvisory.date` carries the business date; `ShiftLibrary.tsx` unchanged | VERIFIED | :578-600; no diff for `ShiftLibrary.tsx` |

**Plan 24-02 (same-class sweep: repair, swap, start-mix)**

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 14 | Post-midnight seat outside its business-Monday envelope is found and moved | VERIFIED | Envelope service keys at :147, 150, 169, 286, 332, 358, 384 all `getBusinessDate()`; `findsAndRepairsAPostMidnightSeatOutsideItsBusinessDayEnvelope` passes (8/8 in class) |
| 15 | Post-midnight seat never judged against the next business day's envelope | VERIFIED | `neverJudgesAPostMidnightSeatAgainstTheNextBusinessDaysEnvelope` passes |
| 16 | Usual-shift swap carries post-midnight seats with the envelope | VERIFIED | ScheduleConsistencyRepairService.java:149; `postMidnightSeatsFollowTheirEnvelopeInASwap` passes (9/9 in class) |
| 17 | A business day whose demand falls wholly after midnight still gets start-mix targets | VERIFIED | ShiftStartMixTargetService.java:153, 168, 175; `aBusinessDayWhoseDemandFallsAfterCalendarMidnight_stillGetsTargets` passes (15/15) |
| 18 | Each fix is a key change only (7 + 1 + 3 sites), `repairVerified` unchanged | VERIFIED | Eleven business-date sites counted; no score-compare, rescore or revert line in the diff of the envelope service |
| 19 | 00:00 control: pre-existing tests pass unchanged | VERIFIED | All three classes green |
| 20 | Comparator keeps calendar date plus clock time; loader read stays; both classified | VERIFIED | Comparator :27 still reads the calendar date with an added comment; `BusinessDayPeriodLoader` :54, 58, 80 feed `DayWindow.businessDateOf`; classified in `bday-join-guard.md` |
| 21 | Guard GREEN with empty allowlist; liveness proofs still red on synthetic input | VERIFIED | 8/8 pass; mutation turns the headline test red |
| 22 | Widened pipeline fixture line invisible to the verb predicate, visible to the widened one | VERIFIED | Fixture present; dedicated liveness test among the 8 passing |
| 23 | `bday-join-guard.md` records widened scope, history, classification, empty allowlist | VERIFIED | Unchanged since the previous report; guard parses it and passes |
| 24 | Repair/start-mix services hold no state across calls (D7, `verification: backstop`) | VERIFIED | Previously PRESENT_BEHAVIOR_UNVERIFIED. Now exercised by tests. See "D7" below. |

**Plan 24-03 (N-2: allocation rows)**

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 25 | List endpoint accepts `businessFrom`/`businessTo` and filters the STORED `business_date` | VERIFIED | Controller :30-31; `businessRange_returnsTheFinalBusinessDaysPostMidnightRows_...` passes (11/11 in class) |
| 26 | Every `Item` carries `businessDate` beside an UNCHANGED calendar `date` | VERIFIED | `everyItemCarriesItsTimeslotsBusinessDate_andDateStaysCalendar` passes |
| 27 | Existing from/to callers unchanged | VERIFIED | `halfSuppliedCalendarRange_isStillIgnoredAsBefore` passes |
| 28 | Business-range paging reuses the calendar keyset and cursor | VERIFIED | Paging-to-exhaustion test passes |
| 29 | Half, mixed, inverted or malformed business range refused (400); empty range returns an empty page | VERIFIED | Refusals at StaffingRequirementService.java:101-114; refusal and empty-page tests pass. Malformed cursor is a separate residual (WR-03). |
| 30 | Business-range queries are tenant- and desk-scoped | VERIFIED | `businessRange_isTenantScoped` passes |
| 31 | At a 00:00 anchor business and calendar ranges are identical | VERIFIED | `midnightDesk_businessRangeAndCalendarRangeReturnIdenticalItems` passes |
| 32 | `ScheduleResults.tsx` loads by `businessFrom/businessTo` and keys on `businessDate`; no TypeScript business-date arithmetic | VERIFIED | Diff of the frontend since `623258f^` is the param swap, the `businessDate` key swap, the type field and comments only. `tsc -b --noEmit` exits clean. |
| 33 | Live: Required / Over-under cells equal seeded demand on a middle and the FINAL business day of a 06:00 desk | VERIFIED | Operator observation on a fresh throwaway stack, 2026-10-08 (`24-UAT.md` test 2): all 15 Required cells matched, including the final day's post-midnight 6/7/6; Over/under = total minus required in every column; stack torn down |
| 34 | No `*-VERIFICATION.md` of another phase modified; no digest refreshed; gate is a real full-suite run | VERIFIED | `git diff a5d895c..HEAD --name-only` lists only this phase's own VERIFICATION file. Gate: orchestrator's full run today (218 XML, 1538 tests, 0 failures, 0 errors, 4 skipped), accepted by the operator (UAT test 4). I did not re-run the roughly 11 minute suite; my own runs were the targeted classes below. |

**Score:** 34/34 truths verified, 0 present-but-behavior-unverified.

### D7 (truth 24) in detail

The truth is a backstop truth, so presence plus wiring never qualifies. What qualifies is a passing wired test, and I checked both that it exists and that it can fail.

- **Static shape.** The three services are Spring `@Service` singletons. Their only fields are `static final` (Logger, `NOTHING`, `RESTARTS`, `MAX_*`). Every map is a per-call local.
- **Tests.** `holdsNoStateAcrossCalls_*` exists in `ScheduleEnvelopeRepairServiceTest`, `ScheduleConsistencyRepairServiceTest` and `ShiftStartMixTargetServiceTest`, all running through `CallIsolation.assertNoStateSurvivesACall`. It takes an isolated baseline for each of two desks, requires the two baselines to differ, runs five alternating successive rounds, then runs `max(4, cores)` threads x 10 runs on one instance. Fixtures are pre-built and the threads meet at a `CyclicBarrier` before every call, so the service calls overlap rather than the fixture builds (this was incremental review WR-01, fixed in `55a5afe`).
- **I re-ran them:** envelope 8, consistency 9, start-mix 15 tests, 0 failures.
- **I mutated against them, not just read the evidence.**
  - Mutation 1: `ShiftStartMixTargetService.reqByDate` hoisted to a never-cleared instance field (a leak that survives between calls). The new D7 test failed, as did two older tests (`isDeterministic`, `ignoresAUsualShiftOnADayTheAgentDoesNotWork`).
  - Mutation 2: the same field as a `ConcurrentHashMap` cleared at the start of each call. That is invisible to the successive half and detectable only by overlapping calls. Over 3 runs the D7 test failed 3 of 3, and it was the only test that failed.
  - The source was restored after each, and `git status` for `src` is clean.
- **Not claimed.** A lock-free race that depends on a particular interleaving is probabilistic by nature. For this class of defect (shared mutable state in a singleton) the deterministic successive half plus the barrier-stepped concurrent half is adequate, and the 3 of 3 detection is evidence of that.

### Judgment-Tier Prohibitions

All six were confirmed by the operator (`24-UAT.md` test 3), and my own checks agree. Operator confirmation is human review of the `unverified-prohibition` flag, so none is left flagged.

| Plan | Prohibition | Verdict | Basis |
|------|-------------|---------|-------|
| 24-01 | Never label a differing-date window with the business date alone; never decorate a matching-date window (OVNT-07) | Held | Exact-string and byte-identical tests pass and are red under mutation |
| 24-02 | Guard not made green by allowlist, narrowing, weakened equality, tree walk, deleted liveness proof, or digest refresh (BDAY-05) | Held | Allowlist empty; mutation turns the headline test red; no other phase's VERIFICATION file touched |
| 24-02 | Do not weaken either `repairVerified` (SOLV-07) | Held | No score-compare, rescore or revert line in the diff |
| 24-03 | Do not change meaning of `Item.date` (SOLV-07) | Held | `date` is still the calendar date, asserted by test, and observed live (2026-10-08 against businessDate 2026-10-07) |
| 24-03 | No business-date derivation in the frontend (OVNT-06) | Held | Frontend diff is a param swap and a key swap |
| 24-03 | Never seed, migrate, solve against or verify on `dev`, nor touch 5432/8080 (OVNT-06) | Held | Operator confirmed; the UAT stack used 55432/8081/3001 only. I cannot prove a negative about past actions, and the operator's confirmation is the evidence. |

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `ShiftLibraryValidationService.java` | VERIFIED | Business-date reads at :230, :457, :578; `Window.describe` wired |
| `ShiftLibraryGenerationService.java` | VERIFIED | Four business-date reads; shared `describe` |
| `BusinessDateJoinGuardTest.java`, `bday-join-guard.md`, `OffendingSample.java` | VERIFIED | 8/8 pass; allowlist empty |
| `ShiftLibraryValidationServiceTest.java` | VERIFIED | 67/67 pass |
| Envelope, consistency and start-mix services and tests | VERIFIED | 7 + 1 + 3 business-date sites; 8, 9, 15 tests pass |
| `CallIsolation.java` | VERIFIED | Substantive, used by all three D7 tests, mutation-proven |
| `StaffingRequirement{Response,Repository,Service,Controller}.java` | VERIFIED | Additive field, twins, refusals and params present and wired |
| `StaffingRequirementListBusinessRangeTest.java` | VERIFIED | 11/11 pass |
| `ScheduleResults.tsx`, `client.ts` | VERIFIED | Params, type and key wired; typecheck clean |

### Key Link Verification

| From | To | Status | Details |
|------|----|--------|---------|
| Validator `findUncoveredWindows` | `Timeslot.getBusinessDate` | WIRED | :230 |
| Generator coverage detail | shared `Window.describe` | WIRED | Generator tests pass |
| Guard test | validator and three other files | WIRED | Mutation of :230 turns the guard's headline test red |
| Envelope / consistency repair | `AgentShiftAssignment.getDate()` (business date) | WIRED | Both sides of every key agree |
| `ScheduleResults.tsx` | controller `businessFrom/businessTo` | WIRED | client.ts sends both params |
| Service | repository business twins | WIRED | `hasBusinessRange` branches call them |
| `toResponseItem` | `Timeslot.getBusinessDate` | WIRED | Populates the additive field |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Real Data | Status |
|----------|---------------|--------|-----------|--------|
| `ScheduleResults.tsx` Required / Over-under | `requiredPerSlot` | `staffingRequirements.list` to repository queries over stored `timeslot.business_date` (NOT NULL, V53) | Yes, confirmed live in UAT 2 | FLOWING |
| Validator windows | `demand` | `findAllLiveByDesk` joined to `Timeslot.businessDate` | Yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Affected classes pass | `./gradlew --offline cleanTest test --tests` on the guard, validation, generation (2 classes), envelope, consistency, start-mix and business-range classes (real `> Task :test`) | 8 XML files: 8 + 9 + 8 + 1 + 31 + 67 + 15 + 11 tests, 0 failures, 0 errors, 0 skipped | PASS |
| Validator tests bind to the fix | Reverted ValidationService:230 to `getDate()` and re-ran validation and guard tests | 7 of 75 failed (six validator tests plus the guard's headline test); restored, tests green again | PASS |
| D7 test binds to the property (successive leak) | Hoisted start-mix `reqByDate` to a never-cleared instance field | D7 test red (plus two older tests) | PASS |
| D7 test binds to the property (concurrent-only leak) | Same field, shared and cleared per call | D7 test the only failure, 3 of 3 runs | PASS |
| Frontend typechecks | `npx tsc -b --noEmit` in `frontend/` | No output | PASS |

My filtered runs replaced `build/test-results`. The full-suite evidence is the orchestrator's run, not that directory.

### Probe Execution

SKIPPED. The plans declare no probes, and the phase is not a migration or tooling phase.

### Requirements Coverage

Plan frontmatter: 24-01 `[OVNT-05, OVNT-07, SOLV-07, BDAY-02, BDAY-05]`, 24-02 `[SOLV-07, BDAY-02, BDAY-05]`, 24-03 `[OVNT-06, SOLV-07, BDAY-02]`. The union is OVNT-05, OVNT-06, OVNT-07, SOLV-07, BDAY-02, BDAY-05, which is every ID in the request. Each plan's SUMMARY lists the same IDs in `requirements-completed`.

| Requirement | Source Plans | Status | Evidence |
|-------------|-------------|--------|----------|
| OVNT-05 (validation of overnight templates) | 24-01 | SATISFIED | Truths 1-3, 5, 6, 9, 10 |
| OVNT-06 (allocation sheet and grid show overnight shift consistently) | 24-03 | SATISFIED | Truths 25-33, including live operator observation |
| OVNT-07 (calendar-date disclosure) | 24-01 | SATISFIED | Truths 7, 8; prohibition 1 |
| SOLV-07 (demand, coverage and solver resolve the same business date) | 24-01, 24-02, 24-03 | SATISFIED | Validator, repair, start-mix and list endpoint all key on the stored business date; the guard enforces it on four explicit files |
| BDAY-02 (timeslot records its business day) | 24-01, 24-02, 24-03 | SATISFIED | `Item.businessDate`; `businessFrom/businessTo` filter the stored column |
| BDAY-05 (guard against bypassing the shared day-window) | 24-01, 24-02 | SATISFIED | Truths 11, 12, 21-23 |

REQUIREMENTS.md marks all six `[x]`. Its traceability table maps them to Phases 18, 20 and 21 only, and none is mapped to Phase 24, so there are no orphans. The table does not mention Phase 24, and the orchestrator may want to note that.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| ShiftLibraryGenerationService.java | 239-240 | Windows sorted by clock `startTime`, not anchored minute (WR-01) | Warning | The generator lists a business day's 01:00 window before its 21:00 one on an anchored desk, while the validator lists them the other way round. Order only, not day membership. |
| ShiftLibraryValidationService.java | 616-618 | Peak advisory tie-break on clock `startTime` (WR-02) | Warning | Order only among equal-shortfall advisories. Same residual at ScheduleEnvelopeRepairService.java:315 and ShiftStartMixTargetService.java:177. |
| StaffingRequirementService.java | 122-130 | Business-range cursor decoded without validation (WR-03) | Warning | A malformed cursor yields HTTP 500 rather than 400. Copied from the pre-existing calendar branches. |
| ShiftLibraryValidationResponse.java | 137-145 | `PeakShortfallAdvisory.date` changed meaning with no javadoc (IN-01) | Info | Documentation only; the message discloses the calendar date. |
| BusinessDateJoinGuardTest.java | 122-125 | Receiver heuristic misses `Timeslot` locals not named `ts` (IN-02) | Info | Guard silence is weaker evidence than it reads. I grepped `src/main/java` for `Timeslot` calendar reads and found only intentional ones (DTO output, `TimeslotGeneratorService`, `BusinessDayPeriodLoader`, `ScheduleOutputService` calendar disclosure). |

The incremental review of the D7 tests (`24-REVIEW-fd38bf5.md`) raised two warnings and two infos, with its disposition recording WR-01, WR-02 and IN-02 fixed and IN-01 accepted. I read the final `CallIsolation.java` and the fixes are present.

No `TBD`, `FIXME` or `XXX` marker exists in any file in the covered code list (grep clean).

I weighed WR-01 to WR-03 against the must-haves. The plans pinned order only for `uncoveredWindows` (truth 10, which holds), and WR-03 sits outside every truth. None defeats the goal. These five findings are still `open` in `24-REVIEW-DISPOSITION.md`. That is backlog triage for the operator, not a verification gap.

### Human Verification Required

None. Every item the previous report raised now carries evidence: the D7 tests (re-run and mutated by me), the operator's UI observation, the operator's prohibition confirmation, and the operator's acceptance of the full-suite gate.

### Stale Verification Reports (do not edit; report only)

Phase 24 changed files listed in other phases' `covered_files`. Per 24-03-SUMMARY these are re-verify candidates, and their digests were deliberately not refreshed: Phase 18 (`client.ts`); Phase 19 (the repair, generation, validation, start-mix and requirement services plus three test classes); Phase 20 (`StaffingRequirementRepository`, `ShiftLibraryGenerationService`, `StaffingRequirementService`, `bday-join-guard.md`); Phase 21 (`client.ts`, `ScheduleResults.tsx`, the repository, generation, validation and requirement services, `bday-join-guard.md`); Phase 22 (`client.ts`, `ScheduleResults.tsx`). Milestone close will be blocked on these digests until each is re-verified. This does not affect Phase 24's own status.

### Gaps Summary

No gaps. No truth failed and no artifact is missing, stubbed or unwired. The remaining work is outside this phase's verification: triage the five open review findings, decide the wall-clock flake `MultiDayConstraintDiagnosticTest.multiDay_5agents_5days_shouldScoreZeroHard` (open in `deferred-items.md`, outside this phase's path), and re-verify phases 18 to 22 before milestone close.

---

_Verified: 2026-10-08_
_Verifier: Claude (gsd-verifier)_
