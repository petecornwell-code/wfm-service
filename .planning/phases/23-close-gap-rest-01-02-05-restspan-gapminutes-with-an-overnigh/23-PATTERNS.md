# Phase 23: Close gap REST-01/02/05 — Pattern Map

**Mapped:** 2026-10-04
**Files analyzed:** 10 (2 CREATE, 8 MODIFY), per RESEARCH.md + the two operator-locked scope decisions
**Analogs found:** 10 / 10 (every file has an exact or role-match in-repo analog; no "no analog" files)

**Correction to the dispatch prompt's file list:** `DayWindow.java` lives at
`src/main/java/com/wfm/util/DayWindow.java`, not `src/main/java/com/wfm/model/DayWindow.java`.
Verified by `git ls-files` — the `util` path is the only one tracked. Use `util` in every task.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/java/com/wfm/util/DayWindow.java` | utility | transform (pure arithmetic) | same file, `anchoredDurationMinutes` (lines ~136-147) | exact |
| `src/main/java/com/wfm/model/RestSpan.java` | model (pure record + static logic) | transform | same file, current `gapMinutes` body (being replaced) | exact |
| `src/main/java/com/wfm/service/SolverService.java` (`requireRestFeasibility` SLOT branch, ~:2008-2045) | service | event-driven (pre-solve validation) | same method's SHIFT branch (already calls `RestSpan.gapMinutes` correctly, ~:1870-1953) | exact |
| `src/test/resources/rest-gap-arithmetic-guard.md` | test (registry/allowlist) | event-driven (build-time guard) | `src/test/resources/rest-waiver-predicate-guard.md` (whole file) | exact |
| `src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java` | test (structural scanner) | event-driven (build-time guard) | `src/test/java/com/wfm/service/RestWaiverPredicateGuardTest.java` (whole file, mechanism); secondarily `MidnightTimeArithmeticGuardTest.java` (comment-stripping scan technique) | exact |
| `src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java` | test | request-response (`ConstraintVerifier`) | same file's existing `shiftRow`/`pair`/`template` fixtures | exact |
| `src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java` | test | request-response (`ConstraintVerifier`) | same file's existing `compliantDaySeats`/`dayConfig`/`timeslot` fixtures | exact |
| `src/test/java/com/wfm/service/RestPredecessorServiceTest.java` | test | request-response | same file's existing `shiftRow`/`slotRow` fixtures | exact |
| `src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java` | test | request-response | same file's existing SLOT pre-horizon case (`slot_preHorizonEdge_usesAcceptedActualEnd_refusedWithExactFigure`, ~:419-446) — direct precedent for Finding 2's proof case | exact |
| `src/test/java/com/wfm/service/RestWaiverDisclosureTest.java` | test | request-response | same file's existing `priorRestSpans`/`RestSpan` construction (~:203-204) | exact |

## Pattern Assignments

### `src/main/java/com/wfm/util/DayWindow.java` (utility, transform) — add `anchoredWrappedEndMinute`

**Analog:** same file, `anchoredDurationMinutes` (verified at HEAD):
```java
public int anchoredDurationMinutes(LocalTime start, LocalTime end) {
    int startOffset = startMinuteFromDayStart(dayStart, start);
    int endOffset = endMinuteFromDayStart(dayStart, end);
    int raw = endOffset - startOffset;
    if (raw > 0) { return raw; }
    if (raw == 0) { return 0; }
    return raw + MINUTES_PER_DAY;
}
```
New method, same instance-method family, placed alongside `anchoredEndMinute`/`anchoredStartMinute`:
```java
public int anchoredWrappedEndMinute(LocalTime start, LocalTime end) {
    return anchoredStartMinute(start) + anchoredDurationMinutes(start, end);
}
```
Javadoc convention to copy: every sibling method in this class documents (a) what it collapses onto at a `00:00` anchor, (b) which existing exhaustive test proves it (`DayWindowTest`), (c) why it does not throw. Follow the same three-part shape — RESEARCH.md's Code Examples section already drafts the exact javadoc text to use.

---

### `src/main/java/com/wfm/model/RestSpan.java` (model, transform) — `gapMinutes` switched onto the new primitive

**Analog:** the method's own current body (being replaced in place, not restructured):
```java
public static int gapMinutes(RestSpan prev, RestSpan next) {
    if (!prev.dayStart().equals(next.dayStart())) {
        throw new IllegalArgumentException(
                "Cannot compute a rest gap between spans anchored at different day starts: "
                        + prev.dayStart() + " vs " + next.dayStart());
    }
    DayWindow window = DayWindow.anchoredAt(prev.dayStart());
    int remainingInPrevDay = DayWindow.MINUTES_PER_DAY - window.anchoredEndMinute(prev.endTime());
    int elapsedIntoNextDay = window.anchoredStartMinute(next.startTime());
    return remainingInPrevDay + elapsedIntoNextDay;
}
```
Only the `remainingInPrevDay` line changes:
```java
int remainingInPrevDay = DayWindow.MINUTES_PER_DAY
        - window.anchoredWrappedEndMinute(prev.startTime(), prev.endTime());
```
Everything else (the guard clause, the javadoc's "one and only gap-minutes implementation" claim, the successor-side line) is unchanged — RESEARCH Finding 3/Pitfall 3 confirms the successor side needs no change. Preserve the existing javadoc's claim verbatim; it is still true after this edit (D-08).

---

### `src/main/java/com/wfm/service/SolverService.java` (service, event-driven) — SLOT pre-horizon branch switched onto the same primitive

**Analog:** the same method's own SHIFT branch, which already delegates correctly and needs no change — proof that "call the shared primitive, don't inline" is the established house style here. Current buggy line (verified at HEAD, `requireRestFeasibility`'s SLOT branch):
```java
} else {
    RestSpan prior = priorSpanByAgent.get(agentId);
    if (prior == null) {
        continue;
    }
    // Pre-horizon edge (D-12/REST-05): the predecessor is HISTORY, not a
    // choice -- use the accepted span's actual end, never an earliest-possible
    // estimate.
    predecessorEndMinute = window.anchoredEndMinute(prior.endTime());
}
```
Changed line only:
```java
predecessorEndMinute = window.anchoredWrappedEndMinute(prior.startTime(), prior.endTime());
```
Leave the `// Same structural shape as RestSpan.gapMinutes (D-08: the two must never drift)` comment at the `bestGap` computation a few lines below in place — it becomes true again rather than aspirational once this fix lands; do not delete it as "no longer needed," it is exactly the kind of in-code assertion this phase exists to make honest.

**Do not touch** the `if (predecessorConfig != null)` sub-branch immediately above (synthetic earliest-possible-end estimate) — RESEARCH's Finding 2/Assumption A3 proves it is provably bounded and never needs wrap-awareness.

---

### `src/test/resources/rest-gap-arithmetic-guard.md` (CREATE) — registry/allowlist

**Analog:** `src/test/resources/rest-waiver-predicate-guard.md` (whole file, read in full above). Copy its exact structure:
1. A one-paragraph title naming the requirement IDs (here: REST-02/REST-05) and the proving test class.
2. A "Why this guard exists" section — for this guard, adapt Pattern 2's own text from RESEARCH.md almost verbatim: this is a *correct-primitive, wrong-composition* defect, not a forbidden-token defect, which is why it is a narrower pattern-scan than `MidnightTimeArithmeticGuardTest`, not a blanket ban on `anchoredEndMinute(`.
3. A **Call-Site Table** (same four-column shape: Entry point | Source file | What must hold | Proving test) with exactly two rows:
   - `RestSpan.gapMinutes`'s predecessor-end computation | `com.wfm.model.RestSpan` | Calls `DayWindow.anchoredWrappedEndMinute(prev.startTime(), prev.endTime())`, never `anchoredEndMinute(prev.endTime())` alone | `MinimumRestShiftConstraintTest`/`MinimumRestSlotConstraintTest` (via `RestSpan.gapMinutes`)
   - `requireRestFeasibility`'s SLOT pre-horizon branch | `com.wfm.service.SolverService` | Calls the same `anchoredWrappedEndMinute`, never `anchoredEndMinute(prior.endTime())` alone | `RestFeasibilityRefusalTest`
4. A **Guard Allowlists** section with ONE fenced allowlist (not two, unlike the waiver guard) — the set of production classes permitted to call `anchoredWrappedEndMinute` at all (expect exactly `com.wfm.model.RestSpan`, `com.wfm.service.SolverService`, and `com.wfm.util.DayWindow` itself as the implementation). Mirror the waiver guard's exact fenced-code-block format (one fully-qualified class name per line, parsed by `indexOf("```")`/`indexOf('\n')`).
5. A **Known scope boundaries — deliberate, not gaps** section — copy the waiver guard's three bullets' *shape* (textual-scan limits, correctness-vs-presence distinction, single-line-only detection) but reword the content for the specific forbidden shape: `MINUTES_PER_DAY - <anchored end accessor call>` appearing outside `DayWindow.anchoredWrappedEndMinute`'s own body and its two sanctioned callers. State explicitly (per RESEARCH Pattern 2) that this is NOT a blanket ban on `anchoredEndMinute(` — only on the specific `MINUTES_PER_DAY - anchoredEndMinute(...)` idiom, so `RestSpan.ofSlots`'s per-slot ordering use of `anchoredEndMinute` (confirmed clean in RESEARCH Finding 3) remains legal and untouched.

---

### `src/test/java/com/wfm/service/RestGapArithmeticGuardTest.java` (CREATE) — scanner test

**Analog:** `src/test/java/com/wfm/service/RestWaiverPredicateGuardTest.java` (whole file, read in full above) for the overall mechanism — resource-loading, allowlist parsing, set-equality assertion, table-row parsing, and the mandatory "test-of-the-test" (`deliberatelyBrokenAllowlist_isDetectedAsAMismatch`-shaped method proving the guard can go red). Copy near-verbatim:

```java
private static final String REGISTRY_RESOURCE = "rest-gap-arithmetic-guard.md";
private static final Path SOURCE_ROOT = Path.of("src", "main", "java");
private static final Pattern TABLE_ROW = Pattern.compile("^\\|(.+)\\|\\s*$");

private static Set<String> parseAllowlist(String heading) throws IOException { /* identical body */ }
private static List<TableRow> parseTableRows() throws IOException { /* identical body */ }
private static String readRegistryResource() throws IOException { /* identical body, different RESOURCE */ }
```

**Where the mechanism must DIFFER from `RestWaiverPredicateGuardTest`, per the dispatch prompt's explicit instruction** — this guard catches a wrong-*composition* of sanctioned primitives, not use of a forbidden token or type reference, so borrow `MidnightTimeArithmeticGuardTest`'s comment-stripping single-line textual matcher technique (`stripComment`, `scanProductionSources`) rather than `RestWaiverPredicateGuardTest`'s "does this class reference type X at all" derivation:

```java
// From MidnightTimeArithmeticGuardTest — the line-level matcher shape to copy, not the
// whole-file reference-scan shape:
private static boolean isSecondImplementationComposition(String rawLine) {
    String code = stripComment(rawLine);
    if (code.isEmpty()) { return false; }
    return code.contains("MINUTES_PER_DAY") && code.contains("anchoredEndMinute(")
            && code.contains("-");
}
```
Apply `scanProductionSources(SOURCE_ROOT, RestGapArithmeticGuardTest::isSecondImplementationComposition)` (copied verbatim from `MidnightTimeArithmeticGuardTest`, with `DayWindow`/`RestSpan`/`SolverService`-after-the-fix excluded as the sanctioned implementation set) and assert the result set is empty, exactly as `MidnightTimeArithmeticGuardTest.rawTimeArithmeticInProductionCode_matchesTheAllowlistExactly` and `RestWaiverPredicateGuardTest.secondImplementationScan_returnsEmptySet` both do.

**Required test-of-the-test (do not skip — both analogs independently require this):** a method proving the matcher fires on a synthetic offending line and does NOT fire on a comment or the correct call site, mirroring `RestWaiverPredicateGuardTest.secondImplementationMatcher_isLiveAgainstSyntheticStrings` and `MidnightTimeArithmeticGuardTest`'s equivalent.

**One allowlist, not two** (simpler than the waiver guard's two-allowlist shape) — only `anchoredWrappedEndMinute` call sites need enumerating; there is no separate "entity reference" set because this defect is not about a type reference.

---

### `src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java` — new overnight-predecessor cases

**Existing fixture builders to call by exact name (verified at HEAD):**
```java
private static Agent agent()
private static ShiftTemplate template(LocalTime start, LocalTime end)
private static ShiftBandPair pair(LocalTime start, LocalTime end)
private static AgentRestWaiver waiver(Agent agent, LocalDate date)
private static AgentShiftAssignment shiftRow(Agent agent, LocalDate date, ShiftBandPair pair, LocalTime dayStart)
private static AgentShiftAssignment shiftRow(Agent agent, LocalDate date, ShiftBandPair pair)  // dayStart defaults to MIDNIGHT
private static ScheduleConfig scheduleConfig(SchedulingMode mode, Integer minimumRestMinutes)
```
RESEARCH.md's Code Examples section already drafts the exact new `@Test` method (`overnightPredecessor_trueGapMeasuredCorrectly`) using `shiftRow(a, D_MINUS_1, pair(LocalTime.of(22,0), LocalTime.of(6,0)))` — call these builders by these names verbatim, do not invent a new helper.

---

### `src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java` — new overnight-predecessor cases

**Existing fixture builders to call by exact name (verified at HEAD):**
```java
private static Agent agent()
private static Timeslot timeslot(LocalDate businessDate, LocalTime start, LocalTime end)
private static AgentAssignment seat(Agent agent, Timeslot ts)
private static AgentDayConfig dayConfig(Agent agent, LocalDate date, LocalTime dayStart)
private static AgentRestWaiver waiver(Agent agent, LocalDate date)
private static ScheduleConfig scheduleConfig(SchedulingMode mode, Integer minimumRestMinutes, LocalTime dayStart)
private static List<AgentAssignment> compliantDaySeats(Agent agent, LocalDate date, LocalTime shiftStart)
```
A wrapping predecessor case is built by calling `timeslot`/`seat` directly with a start/end pair that crosses midnight (there is no shortcut `compliantDaySeats`-equivalent for an overnight run — `compliantDaySeats` assumes a within-day clock range per Finding 5, so the new case constructs its own slot list rather than extending that helper).

---

### `src/test/java/com/wfm/service/RestPredecessorServiceTest.java` — overnight-predecessor cases, both SHIFT and SLOT

**Existing fixture builders to call by exact name (verified at HEAD):**
```java
private static Agent agent()
private static AcceptedScheduleDate acceptedDate(UUID scheduleId)
private static Schedule predecessorSchedule(SchedulingMode mode, LocalTime dayStart)
private static AgentShiftAssignment shiftRow(Agent agent, LocalTime start, LocalTime end)
private static AgentAssignment slotRow(Agent agent, LocalTime start, LocalTime end)
```
Note this file's `shiftRow`/`slotRow` take only `(agent, start, end)` — no explicit date parameter (the class-level `PERIOD_START`/`LOOKBACK_DATE` constants supply it) — different signature shape from `MinimumRestShiftConstraintTest`'s `shiftRow`. Do not assume a uniform signature across files; call each file's own builder as declared there.

---

### `src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java` — Finding 2's proof case (SLOT, wrapping `priorSpan`)

**Analog: the file's own existing pre-horizon SLOT case, verified at HEAD (`:419-446`)** — this is the exact shape the new case sits beside, not a new idiom:
```java
@Test
@DisplayName("SLOT: pre-horizon edge uses the accepted span's actual end, not an earliest-possible estimate, and the refusal reports the exact figure")
void slot_preHorizonEdge_usesAcceptedActualEnd_refusedWithExactFigure() {
    Agent a = agent("Reese");
    RestSpan priorSpan = new RestSpan(a.getId(), D_MINUS_1, LocalTime.of(10, 0), LocalTime.of(19, 0),
            LocalTime.MIDNIGHT);
    AgentDayConfig dConfig = slotDayConfig(a.getId(), D, 8);
    List<String> warnings = new ArrayList<>();

    assertThatThrownBy(() -> SolverService.requireRestFeasibility(SchedulingMode.SLOT,
            1080, List.of(), List.of(dConfig), List.of(priorSpan), List.of(),
            List.of(), scheduleConfig(SchedulingMode.SLOT, 1080,
                    LocalTime.of(8, 0), LocalTime.of(20, 0)),
            warnings, MIDNIGHT_WINDOW))
            .isInstanceOf(PreSolveValidationException.class)
            .satisfies(ex -> { /* ... assert message contains the expected figures ... */ });
}
```
**New case construction:** build `priorSpan` with `start=22:00, end=06:00` (wrapping) instead of `10:00–19:00`, and choose the successor `AgentDayConfig`/window shape so the pre-fix `bestGap` (falsely large, because `anchoredEndMinute(06:00)=360` understates the wrap) would NOT have refused, but the post-fix `bestGap` (using `anchoredWrappedEndMinute`) correctly does. Use the exact same `assertThatThrownBy(...).isInstanceOf(PreSolveValidationException.class).satisfies(...)` shape, asserting the message contains the correct (smaller) `bestGap` figure — mirror the existing case's `.contains("1020").contains("1080")`-style numeric assertion with the new case's hand-derived numbers.

**Existing fixture builders available in this file (verified at HEAD):**
```java
private static Agent agent(String name)
private static ShiftTemplate template(LocalTime start, LocalTime end)
private static ShiftBandPair pair(LocalTime start, LocalTime end)
private static AgentDayConfig dayConfig(UUID agentId, LocalDate date)
private static AgentShiftAssignment shiftRow(Agent agent, LocalDate date, List<ShiftBandPair> eligiblePairs)
private static AgentShiftAssignment shiftRow(Agent agent, LocalDate date, ShiftBandPair singlePair)
private static AgentRestWaiver waiver(Agent agent, LocalDate date)
private static ScheduleConfig scheduleConfig(SchedulingMode mode, Integer minimumRestMinutes)
private static ScheduleConfig scheduleConfig(SchedulingMode mode, Integer minimumRestMinutes, ...)
private static AgentDayConfig slotDayConfig(UUID agentId, LocalDate date, int hours)
private static final DayWindow MIDNIGHT_WINDOW = DayWindow.anchoredAt(LocalTime.MIDNIGHT);
```
Also add a SHIFT-mode overnight-predecessor case for symmetry with `MinimumRestShiftConstraintTest`'s new case (this file's SHIFT branch already delegates to `RestSpan.gapMinutes` and is "fixed for free," but Finding 5 still calls for a fixture here per the five-class enumeration).

---

### `src/test/java/com/wfm/service/RestWaiverDisclosureTest.java` — overnight-span regression case

**Analog:** the file's own existing `priorRestSpans` construction, verified at HEAD (`:203-204`):
```java
schedule.setPriorRestSpans(new ArrayList<>(List.of(
        new RestSpan(dana.getId(), D1.minusDays(1), LocalTime.of(12, 0), LocalTime.of(20, 0),
                /* dayStart */ LocalTime.MIDNIGHT))));
```
New case follows this exact construction shape with a wrapping span (`22:00`→`06:00`) in place of `12:00`–`20:00`, asserting the waiver-disclosure DTO's reported gap is the correct (post-fix) figure, not the pre-fix overstated one. This exercises the "fixed for free" call path (`ScheduleOutputService`'s waiver disclosure calling `RestSpan.gapMinutes` directly) — the regression-safety case named in RESEARCH's Phase Requirements table (REST-07).

## Shared Patterns

### Never compose `MINUTES_PER_DAY - <anchored end accessor>` inline — call `anchoredWrappedEndMinute`
**Source:** the new `DayWindow.anchoredWrappedEndMinute`, Pattern 1 of RESEARCH.md
**Apply to:** `RestSpan.gapMinutes` and `SolverService.requireRestFeasibility`'s SLOT branch — the two, and only two, legitimate call sites. Both must call the same method; neither may re-inline the two-call expression (Finding 2 is direct evidence this has already happened once).

### Structural guard registry + scanner shape (D-08 "exactly one implementation")
**Source:** `src/test/resources/rest-waiver-predicate-guard.md` + `RestWaiverPredicateGuardTest.java` (overall registry/table/allowlist shape); `MidnightTimeArithmeticGuardTest.java` (single-line comment-stripped textual matcher shape, since this is a composition defect not a type-reference defect)
**Apply to:** `rest-gap-arithmetic-guard.md` + `RestGapArithmeticGuardTest.java`

### Never raw `Duration.between`/`.isAfter`/comparison outside `DayWindow` — unaffected, but every new fixture must still route through `DayWindow` anchored accessors
**Source:** `src/test/resources/midnight-time-arithmetic.md`, `MidnightTimeArithmeticGuardTest`
**Apply to:** every new test case in this phase; none of the five affected test classes may construct a gap by any means other than calling `RestSpan.gapMinutes`/`requireRestFeasibility` and reading the result — this existing guard already enforces that no new raw arithmetic creeps in, and this phase's new fixtures must not trip it.

## No Analog Found

None — every file in scope (the corrected list of 10, including the `util` path correction) has an exact in-repo analog. The only genuinely new *shape* (not file) is the single-allowlist, line-level-matcher guard variant — `RestWaiverPredicateGuardTest` uses a two-allowlist, whole-file-reference-scan shape, and `MidnightTimeArithmeticGuardTest` uses an unconditional multi-token scan; `RestGapArithmeticGuardTest` is a deliberate hybrid (one allowlist, line-level matcher) documented inline above rather than listed as missing.

## Metadata

**Analog search scope:** `src/main/java/com/wfm/{util,model,service}`, `src/test/java/com/wfm/{service,solver}`, `src/test/resources`
**Files scanned:** `DayWindow.java` (full relevant sections), `RestSpan.java` (full file), `SolverService.java` (`requireRestFeasibility`, ~:1990-2070), `rest-waiver-predicate-guard.md` (full file), `RestWaiverPredicateGuardTest.java` (full file), `MidnightTimeArithmeticGuardTest.java` (targeted ~150 lines), `MinimumRestShiftConstraintTest.java`, `MinimumRestSlotConstraintTest.java`, `RestPredecessorServiceTest.java`, `RestFeasibilityRefusalTest.java`, `RestWaiverDisclosureTest.java` (fixture-signature greps + targeted reads of the pre-horizon SLOT case and span construction)
**Pattern extraction date:** 2026-10-04
