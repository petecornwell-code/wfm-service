---
phase: quick-261008-eby
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java
  - src/main/java/com/wfm/service/SolverService.java
  - .planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-REVIEW-DISPOSITION.md
autonomous: true
requirements: [REST-03, REST-05]

estimate:
  tokens: 45000
  raw_tokens: 45000
  tasks: 3
  confidence: low

must_haves:
  truths:
    - "SHIFT mode: an agent whose pre-horizon span ends 23:00 on periodStart-1, who works periodStart, has the next day off, and has only a 06:00 start the day after that, is NOT refused by requireRestFeasibility (CR-01)"
    - "SLOT mode: the same shape (late pre-horizon span, working periodStart, mid-horizon day off, a working day after it whose latest possible start is early) is NOT refused (CR-01)"
    - "REST-05 horizon edge intact: the same early start on periodStart itself, against the same pre-horizon span, is still refused with exactly ONE restFeasibility detail naming periodStart-1 and periodStart, in both SHIFT and SLOT mode"
    - "On the true horizon edge the pre-horizon span is still the predecessor: the SHIFT no-refusal fixture's periodStart advisory reports 780 minutes, measured against the 23:00 pre-horizon end"
    - "The in-solve hard constraints (ScheduleConstraintProvider), the waiver disclosure (ScheduleOutputService) and RestPredecessorService are byte-identical to before"
    - "23-REVIEW-DISPOSITION.md records CR-01 as fixed in both frontmatter and table, with open: 3 and total: 4"
  artifacts:
    - path: "src/main/java/com/wfm/service/SolverService.java"
      provides: "preHorizonPredecessor helper: the date-keyed horizon-edge guard both requireRestFeasibility branches call"
      contains: "businessDate().equals(dMinus1)"
    - path: "src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java"
      provides: "4 CR-01 tests: SHIFT and SLOT mid-horizon day-off no-refusal, plus SHIFT and SLOT horizon-edge controls"
      contains: "AFTER_DAY_OFF"
    - path: ".planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-REVIEW-DISPOSITION.md"
      provides: "CR-01 disposition fixed"
      contains: "| CR-01 | critical | fixed |"
  key_links:
    - from: "SolverService.requireRestFeasibility SHIFT branch (D-1 predecessor selection, ~:1894-1900)"
      to: "SolverService.preHorizonPredecessor"
      via: "priorSpanCandidates(preHorizonPredecessor(priorSpanByAgent, agentId, dMinus1))"
      pattern: "priorSpanCandidates\\(preHorizonPredecessor\\("
    - from: "SolverService.requireRestFeasibility SLOT branch (else of predecessorConfig != null, ~:2025-2031)"
      to: "SolverService.preHorizonPredecessor"
      via: "RestSpan prior = preHorizonPredecessor(priorSpanByAgent, agentId, dMinus1); null keeps the existing continue"
      pattern: "RestSpan prior = preHorizonPredecessor\\("
    - from: "SolverService.preHorizonPredecessor"
      to: "RestSpan.businessDate()"
      via: "span returned only when its own businessDate equals dMinus1"
      pattern: "businessDate\\(\\)\\.equals\\(dMinus1\\)"
---

<objective>
Fix CR-01. `SolverService.requireRestFeasibility` falls back to the agent's single pre-horizon `RestSpan` whenever there is no in-horizon D-1 entry, in both the SHIFT and SLOT branches. `RestPredecessorService` resolved that span for `periodStart - 1` only. Day-off and zero-hour dates never appear in `rowsByDate` or `configsByDate`, so any working day after a mid-horizon day off is measured against pre-horizon history. The result is a false `restFeasibility` 400 refusal. Scope both fallbacks to the true horizon edge (D-12, REST-05): the pre-horizon span is a predecessor only for the date it was resolved for. If no predecessor applies, `continue`, the same as the existing "nothing to be impossible against" path.

Purpose: closes the v1.5 milestone audit's one open blocker (CR-01; REST-03 and REST-05 partial; flow F-4 broken). This is a false-refusal fix only. The hard constraints and the waiver disclosure key on exact dates, are already correct, and are not touched.

Approach (verified during planning):
- `RestSpan` is `record RestSpan(UUID agentId, LocalDate businessDate, LocalTime startTime, LocalTime endTime, LocalTime dayStart)`, so it exposes `businessDate()`.
- `RestPredecessorService.resolvePriorSpans` always stamps that date with `lookbackDate = periodStartDate.minusDays(1)`. The SHIFT path uses `row.getDate()` from a query keyed on `lookbackDate`; the SLOT path passes `lookbackDate`.
- So `prior.businessDate().equals(dMinus1)` is exactly "d is the period's first business date". It needs no new `periodStart` parameter, which would ripple to the call site at ~:513 and all 20 existing test call sites.
- It also mirrors how `ScheduleConstraintProvider` keys the registered pre-horizon fact on its own `businessDate()`, as CR-01's own suggested fix describes.

TDD shape, per the user's instruction: Task 1 is the RED commit (4 failing tests), Task 2 is the GREEN commit (the fix), and Task 3 is a final docs commit.
- Tracer-first decomposition is deliberately not applied. The whole change is one date guard inside one existing static method, with no layers to slice through, so a thin slice would add no information.
- Task 3 (recording CR-01 `fixed` in 23-REVIEW-DISPOSITION.md) is done by the EXECUTOR as part of this plan, not left to the orchestrator. The orchestrator keeps only its usual quick-task STATE.md bookkeeping.

Output: the date-keyed guard in SolverService.java, 4 new tests in RestFeasibilityRefusalTest.java, and CR-01 recorded as fixed.
</objective>

<execution_context>
@/Users/pete/IdeaProjects/wfm-service/.claude/gsd-core/workflows/execute-plan.md
@/Users/pete/IdeaProjects/wfm-service/.claude/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-REVIEW.md
@.planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-REVIEW-DISPOSITION.md
@src/main/java/com/wfm/service/SolverService.java
@src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java
@src/main/java/com/wfm/model/RestSpan.java

Read only these SolverService.java regions:
- ~1792-1797: the javadoc paragraph "The pre-horizon predecessor (D-12, REST-05)"
- 1846-2077: the `requireRestFeasibility` body
- 2107-2125: `priorSpanCandidates` and `indexPriorSpansByAgent`

Read 23-REVIEW.md's CR-01 section only, lines ~66-145.

<interfaces>
Extracted from the codebase during planning. Use these directly; no exploration is needed.

```java
// src/main/java/com/wfm/model/RestSpan.java
public record RestSpan(UUID agentId, LocalDate businessDate, LocalTime startTime, LocalTime endTime,
        LocalTime dayStart) { ... public static int gapMinutes(RestSpan prev, RestSpan next) ... }

// src/main/java/com/wfm/service/SolverService.java (package-private static, called directly by tests)
static void requireRestFeasibility(SchedulingMode schedulingMode, Integer minimumRestMinutes,
        List<AgentShiftAssignment> shiftAssignments, List<AgentDayConfig> agentDayConfigs,
        List<RestSpan> priorRestSpans, List<AgentRestWaiver> restWaivers,
        List<AgentAssignment> assignments, ScheduleConfig config, List<String> warnings,
        DayWindow window)
private static List<RestSpan> priorSpanCandidates(RestSpan prior)           // null -> List.of()
private static Map<UUID, RestSpan> indexPriorSpansByAgent(List<RestSpan> priorRestSpans)

// Current SHIFT selection (~1894-1900)
LocalDate dMinus1 = d.minusDays(1);
AgentShiftAssignment predecessorRow = rowsByDate.get(dMinus1);
List<RestSpan> predecessorCandidates = predecessorRow != null
        ? shiftCandidateSpans(predecessorRow)
        // Pre-horizon edge (D-12/REST-05): ...
        : priorSpanCandidates(priorSpanByAgent.get(agentId));
if (predecessorCandidates.isEmpty()) { /* Neither an in-horizon D-1 row nor a pre-horizon span exists */ continue; }

// Current SLOT selection (~2025-2031)
} else {
    RestSpan prior = priorSpanByAgent.get(agentId);
    if (prior == null) { /* Neither an in-horizon D-1 agent-day nor a pre-horizon span exists */ continue; }
    predecessorEndMinute = window.anchoredWrappedEndMinute(prior.startTime(), prior.endTime());
}

// Error message shapes the tests assert against
// SHIFT: "... between <dMinus1> and <d> ... achieves only <bestGap> minute(s) of rest against a required <min> minute(s) ..."
// SLOT:  "... between <dMinus1> and <d> on this SLOT-mode desk ... the best achievable gap is only <bestGap> minute(s) against a required <min> minute(s) ..."
// SHIFT advisory (warnings, only when nothing refused): "Minimum rest on <d> is tightest for agent <id> at <gap> minute(s) of best achievable rest."

// RestFeasibilityRefusalTest fixture helpers and constants (all already present)
D_MINUS_1 = LocalDate.of(2026, 9, 7); D = LocalDate.of(2026, 9, 8); MINIMUM_REST_MINUTES = 660;
Agent agent(String name); ShiftBandPair pair(LocalTime start, LocalTime end);   // all weekdays, effective 2020-01-01
AgentShiftAssignment shiftRow(Agent agent, LocalDate date, ShiftBandPair singlePair); // dayConfig 8.00h, midnight anchor
AgentDayConfig slotDayConfig(UUID agentId, LocalDate date, int hours);  // 60-min increment: required minutes = hours*60
ScheduleConfig scheduleConfig(SchedulingMode mode, Integer min);
ScheduleConfig scheduleConfig(SchedulingMode mode, Integer min, LocalTime operatingStart, LocalTime operatingEnd);
DayWindow MIDNIGHT_WINDOW = DayWindow.anchoredAt(LocalTime.MIDNIGHT);
// Existing pre-horizon idiom: new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(14, 0), LocalTime.of(22, 0), LocalTime.MIDNIGHT)
// Existing call shape: SolverService.requireRestFeasibility(SchedulingMode.SHIFT, MIN, rows, List.of(), List.of(priorSpan), List.of(), List.of(), scheduleConfig(...), warnings, MIDNIGHT_WINDOW)
```

Hand-computed fixture arithmetic (midnight anchor):
- Pre-horizon span 15:00-23:00 has a wrapped end minute of 1380, leaving 60 minutes in its business day.
- SHIFT: a 06:00 start gives gap 60 + 360 = 420, under 660 (refused). A 12:00 start gives 60 + 720 = 780, at least 660 (fine).
- SLOT: the 08:00-20:00 window spans minutes 480-1200, length 720.
  - 8h day: latest start 1200 - 480 = 720, gap 60 + 720 = 780 (fine).
  - 11h day: required 660, within the 720 window, so it is not skipped. Latest start 1200 - 660 = 540, gap 60 + 540 = 600 (refused at 660).
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1 (RED): add 4 failing CR-01 tests to RestFeasibilityRefusalTest</name>
  <files>src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java</files>
  <read_first>src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java (whole file, 531 lines: fixture builders, the pre-horizon section at ~200-262, and the SLOT pre-horizon tests at ~441-497)</read_first>
  <behavior>
    - Test A (SHIFT, no refusal): the mid-horizon day-off successor is not checked against the pre-horizon span. Exactly one advisory warning, for D, at 780 minutes.
    - Test B (SHIFT control, REST-05): an early start on D is still refused against the pre-horizon span, with exactly one detail, and nothing about the day-off successor.
    - Test C (SLOT, no refusal): the mid-horizon day-off successor is not checked against the pre-horizon span.
    - Test D (SLOT control, REST-05): D's required hours are still refused against the pre-horizon span, with exactly one detail, and nothing about the day-off successor.
  </behavior>
  <action>
Add two class-level date constants next to `D_MINUS_1` and `D`, in the same `LocalDate.of` literal style:
- `DAY_OFF = LocalDate.of(2026, 9, 9)` (Wednesday)
- `AFTER_DAY_OFF = LocalDate.of(2026, 9, 10)` (Thursday)

Name them so the reader sees `DAY_OFF` never gets a row or config.

Append a new section at the end of the class, before the final closing brace. Give it a section-divider comment in the file's existing style: "CR-01 -- the pre-horizon span is a predecessor only at the true horizon edge (D-12, REST-05)". Put a short comment above the section explaining:
- `D` plays the period's first business date, and `D_MINUS_1` is the date `RestPredecessorService` resolved the span for.
- `DAY_OFF` is absent from every row list and config list, exactly as `computeAgentDayConfigs` and `buildShiftAssignments` omit a real day off.

Every test below uses the same pre-horizon span: agent id, `D_MINUS_1`, 15:00 to 23:00, `LocalTime.MIDNIGHT`. Every test also uses the existing `MINIMUM_REST_MINUTES` (660) and `MIDNIGHT_WINDOW`. Give each test an `@DisplayName` in the file's style and fresh agent names not used elsewhere in the class, for example Wren, Xan, Yael and Zion. Put the hand-computed arithmetic in a comment in each test, the way the existing SLOT pre-horizon tests do.

Test A, `shift_midHorizonDayOff_dayAfterIsNotCheckedAgainstPreHorizonSpan_noRefusal`:
- SHIFT rows: `shiftRow(a, D, pair(12:00, 20:00))` and `shiftRow(a, AFTER_DAY_OFF, pair(06:00, 14:00))`. No `DAY_OFF` row.
- Call: `priorRestSpans = List.of(priorSpan)`, with empty `agentDayConfigs`, waivers and assignments, and `scheduleConfig(SchedulingMode.SHIFT, MINIMUM_REST_MINUTES)`.
- Assert `doesNotThrowAnyException()`.
- Then assert `warnings` has size 1 and its element contains `D.toString()` and `"at 780 minute(s)"`. This proves the true horizon edge still measures against the 23:00 pre-horizon end, and that `AFTER_DAY_OFF` produced no advisory.

Test B, `shift_horizonEdgeControl_earlyStartOnFirstBusinessDateStillRefused_dayAfterDayOffIsNot`:
- Same as A, except the D row is `pair(06:00, 14:00)`.
- Assert a `PreSolveValidationException` whose `getDetails()` has size 1, using the `satisfies` idiom from `preHorizonEdge_everyDay1TemplateViolatesAgainstAcceptedDay0Shift_refused`.
- That detail's `value()` equals the agent id.
- Its `message()` contains `D_MINUS_1.toString()`, `D.toString()`, `"achieves only 420 minute(s)"` and `"required 660"`.
- It does not contain `DAY_OFF.toString()` or `AFTER_DAY_OFF.toString()`.

Test C, `slot_midHorizonDayOff_dayAfterIsNotCheckedAgainstPreHorizonSpan_noRefusal`:
- SLOT configs: `slotDayConfig(a.getId(), D, 8)` and `slotDayConfig(a.getId(), AFTER_DAY_OFF, 11)`. No `DAY_OFF` config. Empty `shiftAssignments`.
- Call: `List.of(priorSpan)`, `scheduleConfig(SchedulingMode.SLOT, MINIMUM_REST_MINUTES, 08:00, 20:00)`.
- Assert `doesNotThrowAnyException()`.

Test D, `slot_horizonEdgeControl_requiredHoursOnFirstBusinessDateStillRefused_dayAfterDayOffIsNot`:
- Same as C, except the D config is 11 hours.
- Assert a `PreSolveValidationException` with exactly 1 detail.
- That detail's message contains `D_MINUS_1.toString()`, `D.toString()`, `"only 600 minute(s)"` and `"required 660"`.
- It does not contain `DAY_OFF.toString()` or `AFTER_DAY_OFF.toString()`.

Add no new imports beyond what the file already has: `LocalDate`, `LocalTime`, `RestSpan`, `PreSolveValidationException` and the AssertJ statics are all present. Do not touch any existing test or fixture builder. Do not touch production code in this task.

Run the class. The RED state must be exactly these 4 tests failing, and all 20 existing tests passing. Each failure must be the predicted mode, read from the failure message in `build/test-results/test/TEST-com.wfm.service.RestFeasibilityRefusalTest.xml`:
- A and C throw `PreSolveValidationException`: `AFTER_DAY_OFF` is refused against the pre-horizon span (420 or 600 minutes).
- B and D fail the size-1 assertion with 2 details: the false `AFTER_DAY_OFF` refusal is piled onto the true D one.

If any test fails for a different reason, such as an eligibility or fixture mistake, fix the fixture, not production code. Then commit only the test file with the message `test(quick-261008-eby): add failing CR-01 horizon-edge tests for requireRestFeasibility`.
  </action>
  <verify>
    <automated>./gradlew compileJava compileTestJava</automated>
    <automated>./gradlew test --tests "com.wfm.service.RestFeasibilityRefusalTest"; grep -m1 -o ' tests="[0-9]*"' build/test-results/test/TEST-com.wfm.service.RestFeasibilityRefusalTest.xml | grep -qx ' tests="24"' && grep -m1 -o 'failures="[0-9]*"' build/test-results/test/TEST-com.wfm.service.RestFeasibilityRefusalTest.xml | grep -qx 'failures="4"'</automated>
    <automated>git diff --exit-code HEAD -- src/main/java</automated>
  </verify>
  <acceptance_criteria>
    - RestFeasibilityRefusalTest has 24 tests (20 existing plus 4 new). Exactly 4 fail, and they are the 4 new ones, each in its predicted mode.
    - No file under src/main/java is modified.
    - A RED commit exists, containing only RestFeasibilityRefusalTest.java.
  </acceptance_criteria>
  <done>The 4 CR-01 tests are committed and failing for the predicted reasons, and the existing 20 still pass. This is the RED commit.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2 (GREEN): date-key both pre-horizon fallbacks through one preHorizonPredecessor helper</name>
  <files>src/main/java/com/wfm/service/SolverService.java</files>
  <read_first>src/main/java/com/wfm/service/SolverService.java (~1792-1797 javadoc paragraph; 1846-2077 requireRestFeasibility; 2107-2125 priorSpanCandidates and indexPriorSpansByAgent)</read_first>
  <behavior>
    - All 24 RestFeasibilityRefusalTest tests pass, including Task 1's 4. The test file is unchanged since the RED commit.
    - Every other rest-related test class still passes, including both structural guards that scan SolverService (MidnightTimeArithmeticGuardTest, RestGapArithmeticGuardTest) and RestWaiverPredicateGuardTest's one-isWaived-call-site rule.
  </behavior>
  <action>
Implements CR-01's fix, scoping the D-12/REST-05 pre-horizon edge to the true horizon edge.

1. New helper. Add a private static helper `preHorizonPredecessor(Map<UUID, RestSpan> priorSpanByAgent, UUID agentId, LocalDate dMinus1)` returning `RestSpan`. Name the parameters exactly that, because the verify gates count on them. Place it directly after `priorSpanCandidates` and before `indexPriorSpansByAgent`.
   - It looks up `agentId` in the map. It returns that span only when it is non-null and `prior.businessDate().equals(dMinus1)`; otherwise it returns null.
   - Give it a short javadoc in the style of its neighbours. The pre-horizon span is the predecessor only of the date it was resolved for (`periodStart - 1`, D-12/REST-05). Day-off and zero-hour dates are absent from both branches' per-date maps, so without this guard a mid-horizon date whose D-1 is a day off would be measured against pre-horizon history (CR-01). Keying on the span's own business date is the same keying the in-solve constraints use for the registered pre-horizon fact.
   - Use `.equals` for the date comparison. Do not use an ordering comparison: `MidnightTimeArithmeticGuardTest` gates those tokens.
   - This helper must be the only code in the file that reads the agent-keyed prior-span map.

2. SHIFT branch, ~1896-1900. Replace the fallback operand of the `predecessorRow != null` ternary with `priorSpanCandidates(preHorizonPredecessor(priorSpanByAgent, agentId, dMinus1))`.
   - Keep the existing "Pre-horizon edge (D-12/REST-05)" comment and extend it by a few words: the span applies only when dated D-1 (CR-01).
   - Update the comment on the following `predecessorCandidates.isEmpty()` / `continue` block. It currently says "Neither an in-horizon D-1 row nor a pre-horizon span exists". It should say there is neither an in-horizon D-1 row nor a pre-horizon span dated D-1, which includes a mid-horizon day off (CR-01), so there is nothing to be impossible against.

3. SLOT branch, ~2026. In the else of `predecessorConfig != null`, initialise `RestSpan prior` from `preHorizonPredecessor(priorSpanByAgent, agentId, dMinus1)`.
   - The existing `prior == null` then `continue` path handles the day-off case unchanged. Update its comment the same way as the SHIFT one.
   - Leave the `anchoredWrappedEndMinute` line and its REST-05 comment untouched.

4. Javadoc. Add one sentence to the end of the "The pre-horizon predecessor (D-12, REST-05)" paragraph (~1792-1797). It should say that the span is consulted only when its own business date is D-1, through `preHorizonPredecessor`, so a mid-horizon date after a day off has no predecessor and is skipped rather than measured against history (CR-01).

Keep the change minimal and match the surrounding comment density.

Do not change `requireRestFeasibility`'s signature, the call site at ~513, `indexPriorSpansByAgent`, `priorSpanCandidates`, `shiftCandidateSpans`, any gap arithmetic, or the waiver hoisting. Do not touch `ScheduleConstraintProvider`, `ScheduleOutputService` or `RestPredecessorService`: their exact-date keying is already correct. Do not edit the test file; GREEN must pass the RED tests as committed.

Run the targeted class, then the rest-related classes listed in verify. Do NOT run the full `./gradlew test` suite (~1500 tests; `MultiDayConstraintDiagnosticTest` is a known wall-clock flake). Commit only SolverService.java with the message `fix(quick-261008-eby): scope requireRestFeasibility's pre-horizon fallback to the true horizon edge (CR-01)`.
  </action>
  <verify>
    <automated>./gradlew test --tests "com.wfm.service.RestFeasibilityRefusalTest"</automated>
    <automated>./gradlew test --tests "com.wfm.service.RestFeasibilityRefusalTest" --tests "com.wfm.service.RestPredecessorServiceTest" --tests "com.wfm.service.RestWaiverDisclosureTest" --tests "com.wfm.service.RestWaiverPredicateGuardTest" --tests "com.wfm.service.RestWaiverServiceTest" --tests "com.wfm.service.DeskServiceMinimumRestTest" --tests "com.wfm.service.RestGapArithmeticGuardTest" --tests "com.wfm.service.MidnightTimeArithmeticGuardTest" --tests "com.wfm.repository.RestWaiverFetchingFinderGuardTest" --tests "com.wfm.solver.MinimumRestShiftConstraintTest" --tests "com.wfm.solver.MinimumRestSlotConstraintTest" --tests "com.wfm.solver.RestHorizonEdgeTest"</automated>
    <automated>test "$(grep -Ev '^[[:space:]]*(//|\*|/\*)' src/main/java/com/wfm/service/SolverService.java | grep -o 'preHorizonPredecessor(' | wc -l)" -eq 3</automated>
    <automated>test "$(grep -Ev '^[[:space:]]*(//|\*|/\*)' src/main/java/com/wfm/service/SolverService.java | grep -o 'priorSpanByAgent.get(' | wc -l)" -eq 1</automated>
    <automated>test "$(grep -Ev '^[[:space:]]*(//|\*|/\*)' src/main/java/com/wfm/service/SolverService.java | grep -o 'businessDate().equals(dMinus1)' | wc -l)" -eq 1</automated>
    <automated>git diff --exit-code HEAD -- src/test/java src/main/java/com/wfm/solver/ScheduleConstraintProvider.java src/main/java/com/wfm/service/ScheduleOutputService.java src/main/java/com/wfm/service/RestPredecessorService.java</automated>
  </verify>
  <acceptance_criteria>
    - All 24 RestFeasibilityRefusalTest tests pass, and all 12 listed rest-related classes pass.
    - In non-comment code, `preHorizonPredecessor(` appears exactly 3 times (the declaration plus the SHIFT and SLOT call sites). The agent-keyed map lookup appears exactly once, inside the helper. The date-equality guard appears exactly once.
    - Before the GREEN commit, src/test/java, ScheduleConstraintProvider.java, ScheduleOutputService.java and RestPredecessorService.java have no uncommitted changes.
    - A GREEN commit exists, containing only SolverService.java.
  </acceptance_criteria>
  <done>Both requireRestFeasibility branches use the pre-horizon span only when it is dated D-1. The 4 RED tests pass with no test edits, the rest-related suite is green, and the GREEN commit is made.</done>
</task>

<task type="auto">
  <name>Task 3 (docs): record CR-01 as fixed in 23-REVIEW-DISPOSITION.md</name>
  <files>.planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-REVIEW-DISPOSITION.md</files>
  <read_first>.planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-REVIEW-DISPOSITION.md</read_first>
  <action>
The executor does this task as part of this plan; it is not left to the orchestrator. Use scoped `Edit` calls only, never a whole-file Write. The file is a gate-generated ledger, and re-runs preserve hand-recorded rows.

1. In the frontmatter `findings:` list, change only the CR-01 entry's `disposition: open` to `disposition: fixed`. Leave WR-01, IN-01 and IN-02 at `open`.
2. Change the frontmatter `open: 4` to `open: 3`. `open` counts rows still at `open`. Leave `total: 4`, `unparsed: 1`, `recorded`, `titles` and every title string unchanged.
3. In the table, change the CR-01 row to `| CR-01 | critical | fixed | quick 261008-eby, fix commit <short sha> |`. Get `<short sha>` from `git log -1 --format=%h -- src/main/java/com/wfm/service/SolverService.java`, which is Task 2's GREEN commit. The Source cell must contain no pipe character. Leave the WR-01, IN-01 and IN-02 rows and the footer prose unchanged.

Commit only this file with the message `docs(quick-261008-eby): record CR-01 fixed in 23-REVIEW-DISPOSITION`.
  </action>
  <verify>
    <automated>awk '/id: CR-01/{f=1} f && /disposition:/{print; exit}' .planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-REVIEW-DISPOSITION.md | grep -c 'disposition: fixed'</automated>
    <automated>test "$(grep -o 'disposition: open' .planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-REVIEW-DISPOSITION.md | wc -l)" -eq 3 && grep -qx 'open: 3' .planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-REVIEW-DISPOSITION.md && grep -qx 'total: 4' .planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-REVIEW-DISPOSITION.md</automated>
    <automated>grep -qE '^\| CR-01 \| critical \| fixed \| quick 261008-eby' .planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-REVIEW-DISPOSITION.md && test "$(grep -E '^\| (WR-01|IN-01|IN-02) \| [a-z]+ \| open \|' .planning/phases/23-close-gap-rest-01-02-05-restspan-gapminutes-with-an-overnigh/23-REVIEW-DISPOSITION.md | wc -l)" -eq 3</automated>
  </verify>
  <acceptance_criteria>
    - The frontmatter CR-01 entry is `fixed`, the other three entries are `open`, `open: 3` and `total: 4`.
    - The table's CR-01 row reads `fixed`, with the quick task id and the GREEN commit sha in Source. The other three rows are still `open`.
    - The docs commit contains only this file.
  </acceptance_criteria>
  <done>CR-01 is recorded as fixed in both the frontmatter and the table, with consistent counts, and committed.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| operator API → SolverService pre-solve validation | Operator-supplied schedule inputs (agent days off, shift library, desk minimum rest, waivers) decide whether `requireRestFeasibility` refuses the solve with a 400. No new input crosses this boundary; the change only narrows when an existing, server-resolved fact (the pre-horizon `RestSpan` from ACCEPTED history) is consulted. |

## STRIDE Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation Plan |
|-----------|----------|-----------|----------|-------------|-----------------|
| T-261008-eby-01 | D (Denial of Service) | SolverService.requireRestFeasibility, both branches | medium | mitigate | False `restFeasibility` refusals block a legal solve for any roster with a mid-horizon day off. Mitigated by the `preHorizonPredecessor` date guard (Task 2), proven by Tests A and C (Task 1). |
| T-261008-eby-02 | T (Tampering / integrity of the refusal) | requireRestFeasibility horizon edge (REST-05) | medium | mitigate | Over-scoping the fix, by dropping the pre-horizon fallback entirely, would let a genuinely impossible periodStart agent-day through to the solver as silent residual hard score. Mitigated by control Tests B and D, which require exactly one refusal on periodStart, and by Test A's 780-minute advisory assertion. |
| T-261008-eby-03 | I (Information Disclosure) | restFeasibility ErrorDetail messages | low | accept | The messages already name the agent (SHIFT) or the agent id (SLOT) to the desk's own operator. The text and audience are unchanged, and no new fields are exposed. |
| T-261008-eby-04 | T (Tampering) | ScheduleConstraintProvider / ScheduleOutputService / RestPredecessorService | low | mitigate | These must stay byte-identical. Task 2's verify runs `git diff --exit-code HEAD` on all three before the GREEN commit. |
| T-261008-eby-SC | Tampering | npm/pip/cargo installs | low | accept | Not applicable: the plan installs no packages and changes no dependencies. Recorded per the reserved-ID rule. |

ASVS L1, block on high: no threat is high or critical, so nothing blocks.
</threat_model>

<verification>
- `./gradlew compileJava compileTestJava` succeeds.
- RestFeasibilityRefusalTest: 24/24 pass after Task 2. Task 1's RED run showed exactly 4 failures, all predicted.
- These 12 rest-related classes pass:
  - RestFeasibilityRefusalTest, RestPredecessorServiceTest, RestWaiverDisclosureTest, RestWaiverPredicateGuardTest, RestWaiverServiceTest, DeskServiceMinimumRestTest
  - RestGapArithmeticGuardTest, MidnightTimeArithmeticGuardTest, RestWaiverFetchingFinderGuardTest
  - MinimumRestShiftConstraintTest, MinimumRestSlotConstraintTest, RestHorizonEdgeTest
- The full suite is deliberately NOT run: ~1500 tests, and MultiDayConstraintDiagnosticTest is a known wall-clock flake.
- ScheduleConstraintProvider.java, ScheduleOutputService.java and RestPredecessorService.java are unchanged by this plan's commits.
- 23-REVIEW-DISPOSITION.md: CR-01 `fixed` in the frontmatter and the table, `open: 3`, `total: 4`.
- Three commits, in order: test(...) RED, fix(...) GREEN, docs(...).
</verification>

<success_criteria>
- A working day after a mid-horizon day off is no longer measured against pre-horizon history in either SHIFT or SLOT mode. Flow F-4 (min rest, pre-horizon history and a mid-horizon day off, then solve) no longer gets a false pre-solve refusal.
- The genuine horizon edge (periodStart against periodStart-1, REST-05) is still refused in both modes when it is structurally impossible.
- No signature, call-site, constraint, disclosure or predecessor-resolution changes.
- CR-01 is recorded as fixed. The REST-03 and REST-05 partial status in v1.5-MILESTONE-AUDIT.md is left for the milestone re-audit to re-evaluate; this plan does not edit the audit.
</success_criteria>

<output>
Create `.planning/quick/261008-eby-fix-cr-01-pre-horizon-rest-fallback/261008-eby-SUMMARY.md` when done
</output>
