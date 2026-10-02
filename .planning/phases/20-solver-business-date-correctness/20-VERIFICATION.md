---
phase: 20-solver-business-date-correctness
verified: 2026-10-02T01:17:26Z
status: gaps_found
score: 6/8 must-haves verified
covered_files: [".planning/REQUIREMENTS.md", ".planning/ROADMAP.md", ".planning/phases/20-solver-business-date-correctness/20-01-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-01-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-02-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-02-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-03-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-03-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-04-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-04-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-05-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-05-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-06-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-06-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-07-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-07-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-08-PLAN.md", ".planning/phases/20-solver-business-date-correctness/20-08-SUMMARY.md", ".planning/phases/20-solver-business-date-correctness/20-REVIEW.md", "frontend/src/pages/DeskManagement.tsx", "src/main/java/com/wfm/controller/TimeslotController.java", "src/main/java/com/wfm/model/AgentDayConfig.java", "src/main/java/com/wfm/model/AgentShiftAssignment.java", "src/main/java/com/wfm/repository/StaffingRequirementRepository.java", "src/main/java/com/wfm/service/DeskService.java", "src/main/java/com/wfm/service/ScheduleOutputService.java", "src/main/java/com/wfm/service/ShiftLibraryGenerationService.java", "src/main/java/com/wfm/service/SolverService.java", "src/main/java/com/wfm/service/StaffingRequirementService.java", "src/main/java/com/wfm/solver/ScheduleConstraintProvider.java"]
covered_digest: "v1:sha256:876f294d7a7299e1a2c84741f2c2fae194cb383734b9475c28345193d10d876e"
behavior_unverified: 0
overrides_applied: 0
gaps:
  - truth: "SC6: DeskService's 00:00-only gate is gone and a desk's day start accepts any 15-minute boundary, refusing anything else by name at save time; Phase 18's generation-time tiling refusal becomes reachable through the API for the first time and is proven to fire (SOLV-01)"
    status: failed
    reason: "The gate removal itself is correct (any 15-minute boundary is accepted; DeskServiceDayStartTest green), but two sub-claims fail. (1) 'Reachable through the API for the first time and proven to fire': TimeslotController.generateTimeslots (POST /api/v1/desks/{deskId}/timeslots/generate, live, wired into frontend/src/api/client.ts) still hardcodes LocalTime.MIDNIGHT instead of reading desk.getDayStart() -- confirmed by direct read of TimeslotController.java:45-55. On a re-anchored desk this endpoint will NEVER consult the real anchor, so the tiling refusal can never fire through it; worse, it silently generates timeslots whose calendar/business-date relationship is computed against the wrong anchor. The only test claiming to prove reachability, DeskDayStartGenerationReachabilityTest, bypasses this controller entirely and calls TimeslotGeneratorService.generateTimeslots directly -- confirmed by reading the test and its own javadoc, which documents the bypass. The refusal IS correctly reachable via the OTHER real caller (FteUploadService, confirmed fixed at FteUploadService.java:146-151, passing desk.getDayStart()), so the claim is true for the upload path and false for the dedicated generate-timeslots API path. (2) 'Refusing anything else by name': DeskService.setDayStart's new gate (DeskService.java:241-245) computes dayStart.getHour()*60+dayStart.getMinute() and ignores getSecond()/getNano(), so a value like 06:00:01 is silently ACCEPTED and persisted even though it is not a clean 15-minute boundary -- weaker than the strict .equals(MIDNIGHT) check it replaced. Untested (DeskServiceDayStartTest and DeskDayStartGenerationReachabilityTest only construct whole-minute LocalTime values)."
    artifacts:
      - path: "src/main/java/com/wfm/controller/TimeslotController.java"
        issue: "generateTimeslots passes LocalTime.MIDNIGHT instead of desk.getDayStart() at lines 45-55; stale comment still claims the 00:00-only gate makes this unreachable, which plan 20-08 invalidated"
      - path: "src/main/java/com/wfm/service/DeskService.java"
        issue: "setDayStart's 15-minute-boundary gate (lines 241-245) ignores seconds/nanoseconds, silently accepting non-boundary values with nonzero sub-minute precision"
    missing:
      - "TimeslotController.generateTimeslots must read desk.getDayStart() (via DeskService or DeskRepository) and pass it as the dayStart argument, mirroring FteUploadService's fix; the stale BDAY-01 comment must be deleted"
      - "A test driving the actual REST/controller path (not TimeslotGeneratorService directly) proving the tiling refusal fires through POST /api/v1/desks/{deskId}/timeslots/generate on a re-anchored desk"
      - "DeskService.setDayStart should reject nonzero getSecond()/getNano() explicitly, alongside the 15-minute modulus check"
  - truth: "Phase goal: every solver join resolves business date, not calendar date, for the same timeslot"
    status: failed
    reason: "SolverService.expandMinimumStaffingSeats (lines ~1955 and ~1966, confirmed by direct read) reads a timeslot's CALENDAR date (ts.getDate()) to (a) evaluate shift-template isEffectiveOn/appliesOn weekday eligibility and (b) look up workingAgentDaysByDate, a map keyed by BUSINESS date (AgentShiftAssignment::getDate, confirmed at SolverService.java:400-402, and confirmed elsewhere in this same migration as already being the business date per D-05). This is the identical key-system mismatch this phase's own diff explicitly found and fixed four hundred lines below, in the sibling function requireShiftEnvelopeSeatSupply (SolverService.java:1450-1467), with an extensive in-code comment describing exactly this failure mode -- but expandMinimumStaffingSeats itself was never touched by this phase's diff. On a re-anchored desk this silently mis-provisions (or under-provisions) minimum-staffing filler seats and can re-open a previously-fixed weekday-eligibility defect, with no error, no warning, and no failing test. SolverService is not one of BusinessDateJoinGuardTest's four scanned files, so the structural guard's all-green status does not and cannot see this -- it is exactly the blind-spot class (a silent non-join / silent miskey scored identically to correct) this phase exists to eliminate, reproduced one layer outside the guard's drawn scope. No test in the suite exercises expandMinimumStaffingSeats against a non-midnight-anchored desk (checked ShiftEnvelopeSupplyInvariantTest, ZeroDemandTimeslotCeilingTest, ShiftModeMinimumStaffingSeatSupplyTest, ShiftDeskEndToEndRegressionTest, MinimumStaffingSeatsTest -- none construct a desk with a non-midnight dayStart)."
    artifacts:
      - path: "src/main/java/com/wfm/service/SolverService.java"
        issue: "expandMinimumStaffingSeats reads ts.getDate() (calendar) at lines ~1955 and ~1966 where ts.getBusinessDate() is required to match workingAgentDaysByDate's business-date key system and the shift-template eligibility semantics used identically elsewhere in this same migration"
    missing:
      - "Change ts.getDate() to ts.getBusinessDate() at both sites inside expandMinimumStaffingSeats's SHIFT-mode branch"
      - "A test exercising expandMinimumStaffingSeats against a non-midnight-anchored (e.g. 21:00) desk, proving minimum-staffing seat counts and weekday eligibility are computed against the correct business day"
advisory:
  - finding: "BusinessDateJoinGuardTest's four-file scope (ScheduleConstraintProvider, ScheduleOutputService, ShiftLibraryGenerationService, StaffingRequirementService) does not cover SolverService at all, and this verification's own direct read of SolverService found one real defect (CR-02) the first time it was scanned with the same discipline applied to the four guarded files. The four 'worth your attention' items the code review specifically re-checked in the guarded files (AgentDayConfig.dayStart, AgentShiftAssignment.date, the resolveAnchor fallback, the tagged-collector shape) all turned out correct -- so the guard's scope, where drawn, is reliable. The residual risk is specifically the undrawn area: SolverService has ~1900+ lines of date-sensitive logic with no structural guard at all, and this phase's own review process just demonstrated that one pass can still miss instances. This is a recommendation for a follow-up (widen BusinessDateJoinGuardTest's scope to SolverService, or a dedicated audit), not a blocking gap in this phase's own named deliverables."
    category: architectural
    reason: "Not in scope for this phase's named success criteria (none of SC1-7 names SolverService as a guarded file), but directly relevant to the phase's own stated purpose and to confidence that CR-02 is the only remaining instance rather than one of several"
    evidence_status: "none provided — flagged by code review, confirmed by this verifier's direct read, not exhaustively re-audited across all of SolverService"
  - finding: "MidnightBoundaryRegressionTest.java:273's class-level javadoc for the NonMidnightAnchor nested test still states in the present tense that 'Today's solver still runs every interval on ScheduleConstraintProvider.PENDING_DESK_ANCHOR ... and still joins ... on CALENDAR date' -- no longer true after plan 20-05's migration landed. The comment is deliberately written as a RED-state argument (per its own preceding sentence, 'argued against what the MIGRATED solver must produce'), so it is understandable as historical framing, but it is not clearly marked as describing the pre-migration state once read in isolation."
    category: other
    reason: "Cosmetic; misleads a future reader skimming only this javadoc into thinking the migration never happened. No behavior or test correctness is affected."
    evidence_status: "confirmed by direct read, no code or test change needed beyond a wording fix"
---

# Phase 20: Solver Business-Date Correctness Verification Report

**Phase Goal:** Every solver join, the pre-solve seat-supply check, SLOT-mode accounting, and demand
upload/coverage reporting all resolve the same business date for the same timeslot — proven by
per-constraint match counts, not just score — and one small live desk shows the re-anchoring changed
nothing it shouldn't have.

**Verified:** 2026-10-02T01:17:26Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SC1: All business-date-relevant joins in `ScheduleConstraintProvider` key on business date, backed by a structural guard, both directions (SOLV-01, SOLV-02) | ✓ VERIFIED | Shared `DATE` lambda at `:98-99` resolves `a.getTimeslot().getBusinessDate()`; all 7 `groupBy(AGENT_ID, DATE, …)` consumers and all standalone `equal()`/`join()` sites read `getBusinessDate()` (confirmed by grep across the file). `BusinessDateJoinGuardTest` passes 6/6, allowlist (`bday-join-guard.md`) is empty with two independent liveness proofs (matcher-level + tracked-offender pipeline) |
| 2 | SC2: Break-band, contiguity and envelope-compliance constraints hold across midnight; the pre-solve seat-supply check reports a shortfall against the business day it actually affects (SOLV-03, SOLV-05) | ✓ VERIFIED | `MidnightBoundaryRegressionTest.NonMidnightAnchor` (21:00-anchored, falsifiable before/after scenarios) passes; `requireShiftEnvelopeSeatSupply` (`SolverService.java:1450-1467`) re-keyed from `Timeslot::getDate` to `Timeslot::getBusinessDate`, matching `rowsByDate`'s business-date key — confirmed by direct read and by `ShiftEnvelopeSupplyGateTest`/`ShiftEnvelopeSupplyInvariantTest` passing. See truth 8 for a related, narrower-scoped defect in a sibling function that is NOT part of this literal criterion |
| 3 | SC3: An overnight SLOT-mode stretch counts against a single business day's contracted hours (SOLV-04) | ✓ VERIFIED | `SlotModeOvernightContractedHoursTest` (constructed 21:00-anchor, 8 assignments spanning 3 calendar dates / 2 business days) passes 1/1; `contractedHoursOver`/`Under`/zero-assignment constraints join on the migrated `DATE` lambda |
| 4 | SC4: Every migrated join carries a non-vacuity assertion and a per-constraint match-count assertion (SOLV-06) | ✓ VERIFIED | `ConstraintMatchCountNonVacuityTest` passes 5/5: a reflectively-complete 26-constraint table (inherited from `ScheduleConstraintClassificationTest`'s own reflective derivation), a literal pre-migration baseline, and an anchor-invariance assertion naming exactly which constraints a non-join would defeat |
| 5 | SC5: Demand upload, coverage reporting and the solver resolve the same business date for the same timeslot, guarded by a test; a Phil-US-shaped constructed fixture shows unchanged per-constraint match counts and score across the re-anchoring (SOLV-07, BDAY-07) | ✓ VERIFIED | `ScheduleOutputService` (6 key positions), `ShiftLibraryGenerationService` (4 key positions, including 2 outside the guard's 4-verb scan) and `StaffingRequirementService`'s destructive-delete range all migrated to `getBusinessDate()` (confirmed by grep); `BusinessDateJoinGuardTest` green across all 4 files; `StaffingRequirementBusinessDateDeleteTest` passes 3/3 (the guard's own documented blind spot, closed by a direct survivor-observing test); `PhilUsShapedDriftGuardTest` passes 2/2 (48-agent, 44.6-FTE constructed fixture, unchanged match counts/score before and after re-anchoring) |
| 6 | SC6: `DeskService`'s `00:00`-only gate is gone, any 15-minute boundary is accepted, refusing anything else by name at save time; Phase 18's generation-time tiling refusal becomes reachable through the API for the first time and is proven to fire (SOLV-01) | ✗ FAILED | Gate removal itself confirmed correct (`DeskService.java:236-242`, `DeskServiceDayStartTest` green). But `TimeslotController.generateTimeslots` (`TimeslotController.java:42-58`) still hardcodes `LocalTime.MIDNIGHT` — confirmed by direct read — so the tiling refusal can never fire through this live, frontend-wired REST endpoint; `DeskDayStartGenerationReachabilityTest` bypasses the controller entirely (confirmed from its own javadoc and body). Additionally the new gate ignores seconds/nanoseconds (`DeskService.java:241-245`), so it does not refuse "anything else" as literally promised. See Gaps |
| 7 | SC7: `PENDING_DESK_ANCHOR` is gone, all 19 call sites read the real anchor via the joined `ScheduleConfig`, its allowlist entry is removed, the guard goes red if either returns (SOLV-03) | ✓ VERIFIED | `grep -rn PENDING_DESK_ANCHOR src/` finds it only inside two test files' string literals (used to prove the matcher detects the shape if it ever returns), never in production source; `midnight-time-arithmetic.md`'s "Permitted midnight anchors" section lists only 3 unrelated, legitimate sites, no `PENDING_DESK_ANCHOR` entry; `MidnightTimeArithmeticGuardTest` passes 12/12 |
| 8 | Phase goal: every solver join resolves business date, not calendar date, for the same timeslot | ✗ FAILED | `SolverService.expandMinimumStaffingSeats` (lines ~1955, ~1966) reads `ts.getDate()` (calendar) against `workingAgentDaysByDate` (keyed by business date) and for shift-template weekday eligibility — the identical key-system bug this phase's own diff fixed in the sibling function `requireShiftEnvelopeSeatSupply` but left unfixed here. Confirmed by direct read; no test in the suite exercises this method against a non-midnight desk. Outside `BusinessDateJoinGuardTest`'s four-file scope. See Gaps |

**Score:** 6/8 truths verified (0 present, behavior-unverified)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java` | Structural, bidirectional join-key guard over 4 files | ✓ VERIFIED | Exists, substantive, wired; 6/6 tests pass |
| `src/test/resources/bday-join-guard.md` | Allowlist, empty by construction | ✓ VERIFIED | Empty fenced block, documents StaffingRequirementService blind spot honestly |
| `src/test/java/com/wfm/service/AgentDayDerivationGuardTest.java` | D-06 derivation-chain guard | ✓ VERIFIED | 11/11 tests pass |
| `src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java` (NonMidnightAnchor) | 21:00-anchored falsifiable scenarios | ✓ VERIFIED | Full suite green (independently confirmed: 1187 tests, 0 failures) |
| `src/test/java/com/wfm/solver/SlotModeOvernightContractedHoursTest.java` | SOLV-04 constructed proof | ✓ VERIFIED | 1/1 pass, substantive (376 lines) |
| `src/test/java/com/wfm/solver/ConstraintMatchCountNonVacuityTest.java` | SOLV-06 per-constraint match-count instrument | ✓ VERIFIED | 5/5 pass, 342 lines, reflective completeness |
| `src/test/java/com/wfm/solver/PhilUsShapedDriftGuardTest.java` | BDAY-07 constructed drift guard | ✓ VERIFIED | 2/2 pass, 694 lines |
| `src/test/java/com/wfm/service/StaffingRequirementBusinessDateDeleteTest.java` | SOLV-07 destructive-delete migration proof | ✓ VERIFIED | 3/3 pass |
| `src/test/java/com/wfm/service/DeskDayStartGenerationReachabilityTest.java` | SC6 reachability proof | ⚠️ ORPHANED (scope) | Exists, passes 2/2, but proves reachability only through a direct `TimeslotGeneratorService` call, not through the real `TimeslotController` REST path — does not cover the gap it was meant to close |
| `src/main/java/com/wfm/controller/TimeslotController.java` | `/generate` reads `desk.getDayStart()` | ✗ STUB (behavior) | Still hardcodes `LocalTime.MIDNIGHT`; file exists and compiles but the specific behavior this phase requires was never implemented here |
| `src/main/java/com/wfm/service/SolverService.java` (`expandMinimumStaffingSeats`) | Reads business date, not calendar date | ✗ STUB (behavior) | `requireShiftEnvelopeSeatSupply` in the same file was correctly migrated; `expandMinimumStaffingSeats` was not |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `ScheduleConstraintProvider` joins | `Timeslot.getBusinessDate()` | shared `DATE` lambda + direct `equal()`/`join()` calls | ✓ WIRED | Confirmed by grep; 0 remaining `Timeslot.getDate()` calls at key positions in this file |
| `ScheduleOutputService`/`ShiftLibraryGenerationService`/`StaffingRequirementService` key positions | `Timeslot.getBusinessDate()` | `computeIfAbsent`/`groupBy`/`.map().min()/.max()` | ✓ WIRED | Confirmed by grep; display labels (`:670`, `:771`) deliberately kept on calendar date per D-10, correctly documented |
| `DeskService.setDayStart` | `TimeslotGeneratorService.requireDayStartTiles` | `FteUploadService.generateTimeslots(..., desk.getDayStart(), ...)` | ✓ WIRED | `FteUploadService.java:146-151` confirmed passing `desk.getDayStart()` |
| `DeskService.setDayStart` | `TimeslotGeneratorService.requireDayStartTiles` | `TimeslotController.generateTimeslots(..., LocalTime.MIDNIGHT, ...)` | ✗ NOT_WIRED | Controller hardcodes `LocalTime.MIDNIGHT`; never reads the desk's real anchor — this is CR-01 |
| `SolverService.requireShiftEnvelopeSeatSupply` | `Timeslot.getBusinessDate()` | `timeslotsByDate` keyed by `Timeslot::getBusinessDate` | ✓ WIRED | Confirmed at `SolverService.java:1467` |
| `SolverService.expandMinimumStaffingSeats` | `Timeslot.getBusinessDate()` | — | ✗ NOT_WIRED | Still reads `ts.getDate()` at both date-sensitive branches — this is CR-02 |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| `BusinessDateJoinGuardTest` structural guard fires correctly | `./gradlew test --tests com.wfm.service.BusinessDateJoinGuardTest` | 6 tests, 0 failures | ✓ PASS |
| `MidnightTimeArithmeticGuardTest` (PENDING_DESK_ANCHOR removal) | `./gradlew test --tests com.wfm.service.MidnightTimeArithmeticGuardTest` | 12 tests, 0 failures | ✓ PASS |
| `PhilUsShapedDriftGuardTest` (BDAY-07 unchanged-counts) | `./gradlew test --tests com.wfm.solver.PhilUsShapedDriftGuardTest` | 2 tests, 0 failures | ✓ PASS |
| `ConstraintMatchCountNonVacuityTest` (SOLV-06 non-vacuity) | `./gradlew test --tests com.wfm.solver.ConstraintMatchCountNonVacuityTest` | 5 tests, 0 failures | ✓ PASS |
| `SlotModeOvernightContractedHoursTest` (SOLV-04) | `./gradlew test --tests com.wfm.solver.SlotModeOvernightContractedHoursTest` | 1 test, 0 failures | ✓ PASS |
| `DeskDayStartGenerationReachabilityTest` (SC6, scope-limited) | `./gradlew test --tests com.wfm.service.DeskDayStartGenerationReachabilityTest` | 2 tests, 0 failures | ✓ PASS (but does not cover the controller path — see gap) |
| `AgentDayDerivationGuardTest` (D-06) | `./gradlew test --tests com.wfm.service.AgentDayDerivationGuardTest` | 11 tests, 0 failures | ✓ PASS |
| `StaffingRequirementBusinessDateDeleteTest` (SOLV-07 blind spot) | `./gradlew test --tests com.wfm.service.StaffingRequirementBusinessDateDeleteTest` | 3 tests, 0 failures | ✓ PASS |
| Full workspace suite (independently re-confirmed, not re-run by this verifier — already run and reported by the executor with matching exit code) | `./gradlew test` | 1187 tests, 4 skipped, 0 failures, 0 errors | ✓ PASS (relied on prior run; this verifier ran the 8 targeted classes above directly, which wiped and regenerated only those classes' XML, consistent with this project's known `--tests` XML-wipe behavior) |

### Probe Execution

Not applicable — this phase has no `scripts/*/tests/probe-*.sh` conventions and none are declared in its PLAN/SUMMARY files. Step 7c: SKIPPED (no probes declared).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| SOLV-01 | 20-01, 20-05, 20-08 | Every constraint grouping an agent's day joins on business date | ✓ SATISFIED | As literally defined in REQUIREMENTS.md; the gate-removal/reachability deliverable the ROADMAP additionally files under this tag (SC6) has a gap — see Gaps |
| SOLV-02 | 20-02 | A test fails if any constraint joins on calendar date where business date is meant | ✓ SATISFIED | `BusinessDateJoinGuardTest`, bidirectional, green |
| SOLV-03 | 20-01, 20-05 | Break bands, contiguity, envelope compliance hold across midnight | ✓ SATISFIED | `MidnightBoundaryRegressionTest.NonMidnightAnchor`, `PENDING_DESK_ANCHOR` removal confirmed |
| SOLV-04 | 20-04, 20-05 | SLOT mode counts overnight stretch against one business day | ✓ SATISFIED | `SlotModeOvernightContractedHoursTest` |
| SOLV-05 | 20-04 | Pre-solve seat-supply check reports shortfalls per business day | ✓ SATISFIED | `requireShiftEnvelopeSeatSupply` re-keyed; note the identically-shaped defect in the sibling `expandMinimumStaffingSeats` is NOT within this requirement's literal text (that function is not "the pre-solve check") but is flagged against the phase goal — see Gaps |
| SOLV-06 | 20-02, 20-03, 20-05 | Each migrated join proven non-vacuous via match counts | ✓ SATISFIED | `ConstraintMatchCountNonVacuityTest` |
| SOLV-07 | 20-02, 20-06, 20-07 | Demand upload, coverage reporting and solver resolve same business date, guarded by test | ✓ SATISFIED | `BusinessDateJoinGuardTest` (3 of 4 files) + `StaffingRequirementBusinessDateDeleteTest` (4th file, guard's documented blind spot) |
| BDAY-07 | 20-03 | Constructed Phil-US-shaped fixture shows unchanged schedule across re-anchoring | ✓ SATISFIED | `PhilUsShapedDriftGuardTest` |

No orphaned requirements: the union of `requirements:` fields across all 8 plans (`SOLV-01..07`, `BDAY-07`) exactly matches the phase's declared requirement set and REQUIREMENTS.md's Phase 20 mapping.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `src/main/java/com/wfm/controller/TimeslotController.java` | 45-55 | Hardcoded literal (`LocalTime.MIDNIGHT`) feeding a date-sensitive generation path, with a comment whose premise this phase invalidated | 🛑 Blocker | CR-01 — see Gaps |
| `src/main/java/com/wfm/service/SolverService.java` | 1955, 1966 | Calendar-date read (`ts.getDate()`) against a business-date-keyed map, matching this phase's own documented defect signature elsewhere in the same file | 🛑 Blocker | CR-02 — see Gaps |
| `src/main/java/com/wfm/service/DeskService.java` | 241-245 | Validation gate weaker than the one it replaced on an unstated dimension (seconds/nanoseconds) | ⚠️ Warning | WR-01 — folded into the SC6 gap above |
| `src/test/java/com/wfm/solver/MidnightBoundaryRegressionTest.java` | 273 | Stale present-tense javadoc describing pre-migration behavior | ℹ️ Info | Cosmetic; recorded as advisory, not blocking |

No `TBD`/`FIXME`/`XXX` markers found in any file this phase modified.

### Human Verification Required

None. Every finding in this report was confirmed by direct source inspection and/or a passing/failing automated test; nothing here depends on visual appearance, real-time behavior, or external-service integration.

### Gaps Summary

The core migration — re-pointing every join/groupBy/computeIfAbsent key position in
`ScheduleConstraintProvider`, `ScheduleOutputService`, `ShiftLibraryGenerationService` and
`StaffingRequirementService` from calendar date to business date, removing `PENDING_DESK_ANCHOR`,
and proving non-vacuity and anchor-invariance with per-constraint match counts — is sound,
thorough, and independently confirmed against the source for this verification. Five of the seven
ROADMAP success criteria (SC1-5, SC7) are fully verified with passing, substantive tests.

Two gaps remain, both already identified by this phase's own code review (`20-REVIEW.md`) and
independently re-confirmed here by direct source inspection, both sitting just outside the
four-file structural guard's drawn scope, and both exactly the bug class this phase exists to
eliminate:

1. **SC6 is not fully met.** `TimeslotController.generateTimeslots` — a live, frontend-wired REST
   endpoint whose entire purpose is the generation this criterion names — still hardcodes
   `LocalTime.MIDNIGHT` rather than the desk's real `dayStart`. The sibling caller
   (`FteUploadService`) was correctly fixed; this one was not, and the one test that claims to
   prove reachability deliberately bypasses the controller. A secondary, lower-severity gap in the
   same criterion: the new 15-minute-boundary gate ignores seconds/nanoseconds, so it does not
   refuse "anything else" as literally promised.

2. **The phase goal's "every solver join" promise is not fully met.**
   `SolverService.expandMinimumStaffingSeats` still reads a timeslot's calendar date where its own
   sibling function (`requireShiftEnvelopeSeatSupply`, fixed earlier in the same file, by the same
   migration) now correctly reads business date. No test exercises this path on a non-midnight
   desk.

**Both gaps are currently latent, not currently firing** — every live desk today is anchored at
`00:00`, where business date equals calendar date, so neither defect has visible production
impact right now. But both are fragile-latent rather than harmless-latent: this phase's own final
deliverable (SC6, the gate removal) is precisely what makes a non-midnight anchor reachable for
the first time, and the moment any desk is re-anchored, both CR-01 (silent wrong-anchor timeslot
generation) and CR-02 (silent minimum-staffing mis-provisioning and weekday-eligibility
regression) will fire with no error, no warning, and no failing test to catch them. A green test
suite and a healthy `dev` environment are not evidence these are harmless — they are evidence only
that no live desk has yet exercised the path. This is exactly the "proven, not assumed" standard
this phase itself sets for everything in its own structural guard's scope; these two sites fall
just outside that scope and were not held to it.

Recommended resolution before this phase is considered complete:
- Fix `TimeslotController.generateTimeslots` to read `desk.getDayStart()` (mirroring
  `FteUploadService`'s fix) and add a test driving the real controller path.
- Fix `SolverService.expandMinimumStaffingSeats`'s two `ts.getDate()` reads to `ts.getBusinessDate()`
  and add a non-midnight-desk test.
- Optionally (not blocking): tighten `DeskService.setDayStart`'s gate to reject nonzero
  seconds/nanoseconds, and correct the stale javadoc in `MidnightBoundaryRegressionTest.java:273`.

---

_Verified: 2026-10-02T01:17:26Z_
_Verifier: Claude (gsd-verifier)_
