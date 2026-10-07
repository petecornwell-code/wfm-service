# Phase 24: Close gap N-1/N-2 - Pattern Map

**Mapped:** 2026-10-07
**Files analyzed:** 22 (all modifications except two new test resources and one optional test)
**Analogs found:** 22 / 22 (every analog verified git-tracked; no mirror paths)

This phase is a key-change phase: nearly every file is its own best analog (the fix is a swap of
`getTimeslot().getDate()` for `getBusinessDate()`), so the patterns below are mostly "the shape to
keep" plus the precedent to copy for new additions. Verbatim pre-fix lines and line numbers are in
24-RESEARCH.md (Q-A, Q-B, Q-E); they are not repeated in full here.

Operator decision (2026-10-07): `ShiftStartMixTargetService` IS in scope (RESEARCH Open Question 1 resolved).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` | service | transform | itself; `ShiftLibraryGenerationService.java` (already business-date, shares `covers()`) | exact |
| `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` (`:174`, `:854` accessor rename, `computeUncoveredDetails` uses `Window.describe`) | service | transform | `ShiftLibraryValidationService.java` | exact |
| `src/main/java/com/wfm/service/ScheduleEnvelopeRepairService.java` | service | batch (post-solve repair) | itself (`:122` is the correct business-date key) | exact |
| `src/main/java/com/wfm/service/ScheduleConsistencyRepairService.java` (`:146`) | service | batch | itself (`:207` lookup is business-date) | exact |
| `src/main/java/com/wfm/service/ShiftStartMixTargetService.java` (`:148, :163, :170`) | service | transform | `ScheduleConsistencyRepairService.java` (same key fix); own `:189` `sa.getDate()` is the target key | exact |
| `src/main/java/com/wfm/dto/StaffingRequirementResponse.java` | model (DTO record) | request-response | itself (additive component) | exact |
| `src/main/java/com/wfm/service/StaffingRequirementService.java` (`listRequirements`, `toResponseItem`) | service | CRUD (cursor-paged read) | itself (existing 4-way branch) | exact |
| `src/main/java/com/wfm/repository/StaffingRequirementRepository.java` | repository | CRUD | its own `findLiveByDeskAndDateRange[AfterCursor]` (`:40-60`); `deleteLiveByDeskAndBusinessDateRange` (business-date JPQL precedent) | exact |
| `src/main/java/com/wfm/controller/StaffingRequirementController.java` | controller | request-response | itself (`:25-33`) | exact |
| `frontend/src/api/client.ts` (`:233-239`, `:407`) | utility (API client) | request-response | itself | exact |
| `frontend/src/pages/ScheduleResults.tsx` (`:389-427`, `:414`) | component | request-response | itself; in-file precedent `unfilledSlots` keyed `businessDate|HH:MM` (`:462-471`) | exact |
| `src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java` | test (structural guard) | batch (text scan) | itself; `MidnightTimeArithmeticGuardTest`, `BusinessDateWritePathGuardTest` | exact |
| `src/test/resources/bday-join-guard.md` | config (allowlist) | n/a | itself | exact |
| `src/test/resources/bday-join-guard-offender-widened/OffendingSample.java` (NEW) | test fixture | n/a | `src/test/resources/bday-join-guard-offender/OffendingSample.java` | exact |
| `src/test/java/com/wfm/service/ShiftLibraryValidationServiceTest.java` | test | CRUD (DataJpaTest) | itself (`:1195-1306` helpers, `:313` string assertion) | exact |
| `src/test/java/com/wfm/service/ShiftLibraryGenerationServiceTest.java` / `MidnightWindowSeamTest` | test | transform | themselves (positional `Window` ctor unaffected; only accessor ripple) | exact |
| `src/test/java/com/wfm/service/ScheduleEnvelopeRepairServiceTest.java` | test | unit | itself (`:246` fixture, stub scorer `:~50-70`) | exact |
| `src/test/java/com/wfm/service/ScheduleConsistencyRepairServiceTest.java` | test | unit | itself (`:268` fixture; `twoAgentsHoldingEachOthersUsualShift_areSwapped_andCoverageIsUnchanged`) | exact |
| `src/test/java/com/wfm/service/ShiftStartMixTargetServiceTest.java` | test | unit | itself (`:267` fixture needs `setBusinessDate(DAY)`) | exact |
| `src/test/java/com/wfm/service/StaffingRequirementServiceListBusinessRangeTest.java` (NEW, name discretionary) | test | CRUD (DataJpaTest) | `src/test/java/com/wfm/service/StaffingRequirementBusinessDateDeleteTest.java` | role-match |
| `AgentAssignmentDifficultyComparator.java` | utility | transform | n/a: UNCHANGED, rationale recorded in `bday-join-guard.md` | n/a |

## Pattern Assignments

### `ShiftLibraryValidationService.java` (service, transform)

**Analog:** itself plus `ShiftLibraryGenerationService.java` (passes business dates into the shared `covers()`).

**Core swap** (sites `:230`, `:455`, `:576`, `:599`), per RESEARCH Code Examples:
```java
.map(sr -> new Window(sr.getTimeslot().getBusinessDate(),
        sr.getTimeslot().getStartTime(), sr.getTimeslot().getEndTime()))
```
**Window record** (currently `record Window(LocalDate date, LocalTime startTime, LocalTime endTime) {}` at `:746`): rename component to `businessDate`, add `describe(DayWindow)` per RESEARCH Pattern 3. Uses `DayWindow.calendarDateAtDayStartOffset` (`util/DayWindow.java:441-451`) and `DayWindow.startMinuteFromDayStart`. `TextStyle`/`Locale` already imported. Decorate only when calendar != business (keeps every same-day string byte-identical, Pitfall 5).

**String to keep byte-identical at 00:00:** `window.date() + " " + window.startTime() + "-" + window.endTime()` at `:241`; replace with `window.describe(dayWindow)`. Peak-shortfall message tail (RESEARCH Q-E) asserted with `contains`/`startsWith`.

**Do not fork `covers()`**; the generator calls the same predicate. Optional free improvement: sort by `DayWindow.startMinuteFromDayStart` instead of `Window::startTime`.

---

### `ShiftLibraryGenerationService.java` (service, transform)

**Analog:** `ShiftLibraryValidationService.java`. Edits: `:174` `w.date().getDayOfWeek()` becomes `w.businessDate()...`; `:854-855` `new ErrorDetail("coverage", window.date() + " " + ..., null)` becomes `window.describe(dayWindow)` so both coverage lists cannot diverge. Compile errors list every ripple site (Pitfall 6).

---

### `ScheduleEnvelopeRepairService.java` (service, batch)

**Analog:** itself. Correct-key line to copy (`:122`):
```java
envelopes.put(new AgentDay(sa.getAgent().getId(), sa.getDate()), sa.getShiftBandPair());
```
Replace `getTimeslot().getDate()` with `getTimeslot().getBusinessDate()` at `:141, :144, :160, :277, :323, :349, :375` (7 edits). At `:160` keep `thenComparing(a -> a.getTimeslot().getStartTime())`. Red test first (RESEARCH Q-A: 06:00 anchor, 21:00-05:00 envelope, post-midnight seats; before fix `violationsFound == 0`). Scope bound: if red test shows more than a key change, stop and surface (D-06).

---

### `ScheduleConsistencyRepairService.java` (service, batch)

**Analog:** itself. Single edit at `:146`:
```java
seatsByDateAgent
        .computeIfAbsent(a.getTimeslot().getBusinessDate(), k -> new HashMap<>())   // was getDate()
        .computeIfAbsent(a.getAgent().getId(), k -> new ArrayList<>())
        .add(a);
```
Lookup at `:207` (`seatsByDateAgent.getOrDefault(date, Map.of())`, `date` from `byDate` = `groupingBy(AgentShiftAssignment::getDate)`) already uses the business date.

---

### `ShiftStartMixTargetService.java` (service, transform)

**Analog:** `ScheduleConsistencyRepairService.java` (same key fix); target key already used at `:189` (`rowsByDate.computeIfAbsent(sa.getDate(), ...)`).

Three edits, each `ts.getDate()` to `ts.getBusinessDate()` (bare-`ts` receiver, so the guard's `ts` shape catches them, and `computeIfAbsent(` is a scanned verb):
```java
reqByDate.computeIfAbsent(ts.getDate(), k -> new HashMap<>())              // :148
seatsByDate.computeIfAbsent(ts.getDate(), k -> new HashMap<>())            // :163  (ts = seat.getTimeslot())
slotsByDate.computeIfAbsent(ts.getDate(), k -> new ArrayList<>()).add(ts); // :170
```
Reads at `:198-203` already use the business date. Behind a red test (non-00:00 fixture in REPORT/ENFORCE path). Add this file to the guard's explicit list (D-07 extended per operator decision). Fixture: `ShiftStartMixTargetServiceTest.java:267` builds `ts.setDate(DAY)` only; add `ts.setBusinessDate(DAY)` in the same task (Pitfall 1).

---

### `StaffingRequirementResponse.java` (DTO record, request-response)

**Analog:** itself. Current shape: `record Item(UUID id, UUID timeslotId, UUID specializationId, LocalDate date, LocalTime startTime, LocalTime endTime, String specializationName, int requiredFTEs, String source)`. Insert `LocalDate businessDate` right after `date`. Only construction site: `StaffingRequirementService.toResponseItem` (`:389-405`). Leave `date` and its D-10 comment untouched.

---

### `StaffingRequirementController.java` + `StaffingRequirementService.listRequirements` (controller/service, request-response)

**Analog:** itself (`:25-33`):
```java
@GetMapping
public PaginatedResponse<StaffingRequirementResponse.Item> listRequirements(
        @PathVariable UUID deskId,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false, defaultValue = "50") int limit) {
    return staffingRequirementService.listRequirements(deskId, from, to, cursor, limit);
}
```
Add `@RequestParam(required = false) String businessFrom, businessTo`. In service add `hasBusinessRange` branch alongside `hasDateRange x hasCursor` (`:84-130`); throw `IllegalArgumentException` (maps to 400 `VALIDATION_FAILED`, `GlobalExceptionHandler.java:32-34`) when only one business param is given or calendar and business ranges are both given. Cursor keeps calendar `date/startTime/specName/id` (`:122-129`); do not change `CursorPagination`.

---

### `StaffingRequirementRepository.java` (repository, CRUD)

**Analog:** its own `findLiveByDeskAndDateRange` and `...AfterCursor` (`:40-60`); business-date JPQL precedent `deleteLiveByDeskAndBusinessDateRange`. Copy both queries verbatim, swapping only `t.date BETWEEN` for `t.businessDate BETWEEN`, keeping `ORDER BY t.date, t.startTime, s.name, sr.id` (RESEARCH Pattern 2 gives the full text). No stream filter after paging (Pitfall 4); no migration/index.

---

### `frontend/src/api/client.ts` and `ScheduleResults.tsx` (N-2)

**client.ts:** `staffingRequirements.list` (`:233-239`) builds `URLSearchParams`; add `businessFrom?`, `businessTo?` params and two `query.set(...)` lines. `StaffingRequirement` type (`:407`): add `businessDate: string`.

**ScheduleResults.tsx:** fetch effect (`:389-427`) currently passes `{ from: schedule.periodStartDate, to: schedule.periodEndDate, cursor }` (`:401-403`); change to `businessFrom/businessTo`. Key at `:414`:
```ts
const key = `${r.date}|${toHHMM(r.startTime)}`      // becomes r.businessDate
```
Lookups at `:672, :686, :962, :976` already use the per-section business `date`; leave them. In-file precedent: `unfilledSlots` (`:462-471`) keyed `businessDate|HH:MM`. No TS day-start arithmetic (D-05). Verify with `cd frontend && npx tsc --noEmit -p tsconfig.json`.

---

### `BusinessDateJoinGuardTest.java` (test, structural guard)

**Analog:** itself. Keep `TARGET_FILES` (`:119`, 4 files, verb predicate) unchanged. Add `WIDENED_TARGET_FILES` as an explicit `List<Path>` in the same style (`SOURCE_ROOT.resolve(Path.of("com","wfm","service","..."))`): `ShiftLibraryValidationService`, `ScheduleEnvelopeRepairService`, `ScheduleConsistencyRepairService`, `ShiftStartMixTargetService`. Never a tree walk.

**Scan shape to generalize** (`:275-292`): change `scanFiles(List<Path>)` to take a `Predicate<String>`; existing call is `isBusinessDateJoinKeyPosition(rawLine)` (`:298-304`, `hasJoinVerb(code) && hasTimeslotReceiverGetDate(code)`). New widened matcher:
```java
private static boolean isWidenedTimeslotDateRead(String rawLine) {
    String code = stripComment(rawLine);
    return !code.isEmpty() && hasTimeslotReceiverGetDate(code);   // no verb requirement
}
```
Headline test (`:133-136`, `scanFiles(TARGET_FILES)`) unions both scans into one `derived` set compared to the single `### Allowlist` block (set equality, both directions).

**Liveness proofs to copy:** pipeline red-proof (`:207-226`: `Files.walk(OFFENDER_ROOT)` hasSize(1), scan hasSize(1), `assertThatThrownBy(... containsExactlyInAnyOrderElementsOf(Set.of()))`). Add a twin for `OFFENDER_ROOT_WIDENED = src/test/resources/bday-join-guard-offender-widened`, plus a matcher test that `isWidenedTimeslotDateRead` is true for the three verb-less N-1 lines and false for `sa.getDate()`, a commented-out line, and a `getBusinessDate()` line. Rename `allFourTargetFilesExist` (`:259`) and update the "FOUR-FILE" javadoc.

**Ordering (Pitfall 2):** land the guard extension first, run `./gradlew test --tests com.wfm.service.BusinessDateJoinGuardTest` against the unfixed tree, capture the red (expect 11 lines from RESEARCH Q-B plus the 3 `ShiftStartMixTargetService` lines), then fix; allowlist ends empty. Update `bday-join-guard.md` in both directions: history paragraph, "Known scope boundaries" (comparator rationale: calendar date + clock time is true chronological order; `ScheduleOutputService:693,736,807,875` stay verb-scoped D-10 labels; `BusinessDayPeriodLoader:80` is the derivation itself). Do not refresh any `covered_digest` (D-10).

---

### `OffendingSample.java` widened fixture (NEW, test resource)

**Analog:** `src/test/resources/bday-join-guard-offender/OffendingSample.java`. Copy header-comment style; exactly one real offending verb-free line (e.g. `LocalDate date = sr.getTimeslot().getDate();`) plus one commented-out occurrence. Under `src/test/resources`, never compiled.

---

### `ShiftLibraryValidationServiceTest.java` (test, DataJpaTest)

**Analog:** itself. Class setup `@DataJpaTest` with `@Import({ShiftLibraryValidationService.class, ShiftLibraryGenerationService.class, ShiftLibraryValidationController.class, ShiftTemplateService.class})`, `@MockitoBean TimeslotGeneratorService`. Reuse helpers `saveDeskWithDayStart`, `saveTemplate`, `saveSpecialization`, `saveDemand`, `saveAgent`, `saveAgentDayHours` (`:1195-1306`). `saveTimeslot` hard-codes `timeslot.setBusinessDate(date)` (`:1266`), so add `saveDemandAnchored(tenant, deskId, spec, calendarDate, businessDate, start, end, fte)` that sets business date explicitly (independent oracle). Cases (a), (b), (b2 effectiveFrom), (c1), (c2), 00:00 control (`:313` exact string `"2026-01-10 09:00-09:30"`), and 06:00-daytime no-decoration control: table in RESEARCH Q-D. Each red before the fix.

---

### `ScheduleEnvelopeRepairServiceTest.java`, `ScheduleConsistencyRepairServiceTest.java`, `ShiftStartMixTargetServiceTest.java` (tests, unit)

**Analog:** themselves. Same-task fixture fix: every hand-built `Timeslot` POJO gets `setBusinessDate(...)` (`Envelope:246`, `Consistency:268`, `StartMix:267`); for 00:00-style fixtures `businessDate == date`. Envelope stub scorer (`:~50-70`) hardcodes `DayWindow.anchoredAt(LocalTime.MIDNIGHT)` and reads `seat.getTimeslot().getDate()`; parametrize the anchor and read `getBusinessDate()`. Consistency: extend `twoAgentsHoldingEachOthersUsualShift_areSwapped_andCoverageIsUnchanged` with a post-midnight seat per agent and a `seat(agent, hour)` overload taking calendar-date offset and business date. 06:00-anchor red scenario for Envelope: RESEARCH Q-A.

---

### N-2 backend test (NEW, test, DataJpaTest)

**Analog:** `StaffingRequirementBusinessDateDeleteTest.java` (header: `@DataJpaTest`, `@ActiveProfiles`, `TestEntityManager`, `TenantContext`, helper `saveTimeslot` using `DayWindow.businessDateOf`; survivor-style assertions at a 21:00 anchor and a 00:00 anchor). Assert: `Item.businessDate` populated and `date` unchanged; business range returns the final business day's post-midnight rows (calendar D+1) that the calendar range drops; cursor from a business-range page resumes; half-supplied / mixed range throws `IllegalArgumentException`; 00:00 desk identical.

## Shared Patterns

### Business-date key rule
**Source:** `SolverService.java:2425-2440` (reads `ts.getBusinessDate()`); `AgentShiftAssignment.getDate()` is already the business date (D-05).
**Apply to:** every map in the repair/start-mix services and the validator: key and lookup must both be business dates. Calendar date stays only for operator labels (D-10) and the `date` response field.

### Red-first, with a 00:00 control
**Source:** CONTEXT `<code_context>`; `BusinessDateJoinGuardTest` liveness proofs.
**Apply to:** every fix: red test captured before the production edit, a 00:00 control proving unchanged output, and for guards a proof they can go red.

### Hand-built Timeslot fixtures need `setBusinessDate`
**Apply to:** all three repair/start-mix tests (null key degrades silently; project memory "Null businessDate degrades silently").

### Additive-then-consume API change
**Source:** Phase 19 D-10 / Phase 20. **Apply to:** `StaffingRequirementResponse.Item.businessDate` then `ScheduleResults.tsx`. Never repurpose `date`.

### Verification commands
Compile: `./gradlew compileJava compileTestJava`. Targeted: explicit FQCN `--tests` lists; never read a suite aggregate after a filtered run; `./gradlew --stop` first if the daemon is old. Frontend: `npx tsc --noEmit -p tsconfig.json`. Live N-2 check: local recipe (DB 55432, app 8081, vite 3001), Playwright `browser_evaluate` not screenshots, never against dev. Execute plans sequentially.

## No Analog Found

None. The widened-scan guard predicate has no precedent beyond the existing guard itself (extension of its own helpers).

## Metadata

**Analog search scope:** `src/main/java/com/wfm/{service,dto,repository,controller,util}`, `src/test/java/com/wfm/service`, `src/test/resources`, `frontend/src`
**Tracked-source check:** `git ls-files` confirmed tracked for the 8 files spot-checked (validation, envelope, consistency, start-mix services; join-guard test, md, offender fixture; delete test)
**Pattern extraction date:** 2026-10-07
