# Phase 24: Close gap N-1/N-2 — calendar-date keys in shift-library validation and allocation rows - Research

**Researched:** 2026-10-07
**Domain:** In-repo business-date key correctness (Java 21 / Spring Boot / JPA service layer, a Timefold post-solve repair pair, one React/TS page), plus extending a textual structural guard test
**Confidence:** HIGH. Every claim below comes from reading the file at HEAD this session. No external library is introduced, so there is no Context7/registry work. I ran `BusinessDateJoinGuardTest` and `ShiftLibraryValidationServiceTest` and the frontend type check as a green baseline (see Environment Availability).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

All decisions below were taken as Claude's recommended defaults at the operator's instruction
("recommended decisions") — none was individually debated.

**N-1 — validator date keys**
- **D-01:** `ShiftLibraryValidationService` reads `sr.getTimeslot().getBusinessDate()` everywhere it
  currently reads `getDate()` — the `Window` construction (`:230`), `findUnsatisfiableWeekdays`
  (`:576`), the peak-shortfall loop (`:599`) and the advisory bucketing near `:455`. `Window.date()`
  is redefined as the business date and its javadoc says so. `covers()` stays the single predicate
  shared with `ShiftLibraryGenerationService` (P-03) — the generator already passes business dates,
  so after this fix the two finally agree; do not fork the predicate.
- **D-02:** Operator-facing validator text names the **business** date/weekday (a weekday
  restriction is a business-weekday concept, and the grid is ordered by business date). When a
  window's calendar date differs from its business date, the string also discloses the calendar
  date, e.g. `2026-10-05 (Mon) 01:00-02:00 [calendar 2026-10-06]`. This keeps faith with D-10's
  "operators can see when it actually happens" without re-labelling by the wrong key. Exact wording
  is Claude's discretion; `00:00` desks must produce exactly today's strings.

**N-2 — allocation-row demand**
- **D-03:** Add an **additive** `businessDate` field to `StaffingRequirementResponse.Item`, populated
  from `Timeslot.getBusinessDate()`. The existing `date` field stays the calendar date — Phase 20
  D-10 / SOLV-07 is not reopened. — **Reversibility:** costly — it becomes part of a published
  response contract; removing it later breaks any consumer that adopted it.
- **D-04:** The staffing-requirements list endpoint gains optional business-date range params
  (`businessFrom` / `businessTo`), filtered server-side the way `BusinessDayPeriodLoader:78-82`
  already filters. Existing `from`/`to` keep their calendar semantics for every current caller.
  `ScheduleResults.tsx` fetches with the business range = schedule period and keys
  `requiredPerSlot` on `${r.businessDate}|HH:MM` (key at `:414`, lookups at `:672, :686, :962, :976`).
  This fixes both the wrong-day lookup and the missing final-day post-midnight rows.
- **D-05:** The frontend never derives a business date itself (no day-start arithmetic in TS) —
  BDAY-08's single-derivation rule.

**Same-class sweep and guard**
- **D-06:** Classify every remaining `getTimeslot().getDate()` site in `src/main/java`:
  - `ScheduleEnvelopeRepairService` — **probable defect, in scope.** `envelopes` is keyed on
    `sa.getDate()` (business date, D-05) at `:122` but looked up with `a.getTimeslot().getDate()` at
    `:141, :144, :277` (and `:350, :376` callers). On a non-`00:00` desk a post-midnight seat misses
    its envelope. Prove with a red test first, then fix to the business date.
  - `ScheduleConsistencyRepairService:146` — groups seats by calendar date; research decides whether
    a consistency group must be per business day (likely). Fix only behind a red test; if it proves
    benign, allowlist with the reason.
  - `AgentAssignmentDifficultyComparator:21` — construction-ordering heuristic, no correctness
    effect. Planner's discretion: switch to business date for consistency or allowlist with reason.
  - `BusinessDayPeriodLoader:80` — legitimate: it IS the derivation via `DayWindow.businessDateOf`.
    Allowlist.
  If a red test shows a repair-service fix is larger than a key change, stop and surface it rather
  than expanding scope.
- **D-07:** Extend `BusinessDateJoinGuardTest`'s explicit file list (stay an explicit list, never a
  tree walk — Phase 20 D-08 / Phase 18 D-03) to cover `ShiftLibraryValidationService`,
  `ScheduleEnvelopeRepairService` and `ScheduleConsistencyRepairService`. Acceptance: the guard goes
  red against the pre-fix N-1 lines. If its verb predicate (`join(`/`equal(`/`groupBy(`/
  `computeIfAbsent(`) does not catch `new Window(sr.getTimeslot().getDate(), …)` or
  `.map(sr -> sr.getTimeslot().getDate())`, widen the predicate for the guarded files and prove the
  widening red, with the allowlist in `src/test/resources/bday-join-guard.md` updated in both
  directions.

**Verification**
- **D-08:** N-3 test gap closed in `ShiftLibraryValidationServiceTest` with a non-`00:00` anchor and
  a weekday-restricted overnight template, each case red before the fix: (a) `requireShiftModeReady`
  accepts the template's own post-midnight hours (no false refusal); (b) the template is not
  credited with another business day's hours (no false coverage); (c) unsatisfiable-weekday and
  peak-shortfall advisories bucket on the business weekday. Plus a `00:00` control proving output
  unchanged.
- **D-09:** N-2 is proven backend-first: a service/controller test that `businessDate` is populated
  and that the business range returns the final business day's post-midnight rows. The UI fix is
  checked live via the local verification recipe (throwaway DB 55432, app 8081, vite 3001) on a
  seeded non-`00:00` desk, measuring cell values with Playwright `browser_evaluate` — never
  screenshots, never against dev (dev is production).
- **D-10:** Do not refresh any stale verification `covered_digest` to make gates pass; re-verify
  instead (audit tech-debt note).

### Claude's Discretion
- Exact advisory/uncovered-window wording (D-02) and whether to rename `Window.date`.
- Param naming for D-04 if a better fit exists in the controller's conventions.
- Comparator handling (D-06).

### Deferred Ideas (OUT OF SCOPE)
- Frontend test runner (Phase 22 WR-04) — would let N-2 have a unit test; its own phase.
- Reviewed Todos (not folded): `2026-08-13-cross-agent-seat-displacement.md`, `2026-07-30-blank-upload-template-one-sheet-per-desk.md`, `2026-08-14-terraform-db-password-drift.md`.
- Out of scope per Phase Boundary: changing the `date` response field, any new UI, a frontend test runner.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| OVNT-05 | Shift library validation refuses an overnight template whose envelope does not fit inside its desk's business day | Finding N-1: false refusal of a weekday-restricted overnight template's post-midnight hours; fix = business-date `Window` (Q-A, tests T-1..T-3) |
| OVNT-06 | Allocation sheet/grid show an overnight shift as one block; business-day-keyed surfaces carry disclosure | The Required / Over-under rows on that grid are mis-keyed (N-2); fix = `businessDate` field + business-range fetch (Q-C, Q-F) |
| OVNT-07 | An overnight shift is labelled with the calendar dates it spans wherever displayed | D-02: validator strings disclose `[calendar …]` when calendar != business (Q-E) |
| SOLV-07 | Demand upload, coverage reporting and the solver resolve the same business date for the same timeslot | Validator "coverage reporting" is the last surface still on calendar date; the repair services are the same class (Q-A, Q-B) |
| BDAY-02 | A timeslot records its business day | Stored `business_date` NOT NULL column is the filter/read source (Q-C) |
| BDAY-05 | A guard fails if a scheduling interval calculation bypasses the shared day-window utility | The join guard's extension (Q-D) |
</phase_requirements>

## Summary

Both gaps are one defect class: a `Timeslot.getDate()` (calendar date) read where the solver reads `getBusinessDate()`. The research confirmed N-1 and N-2 exactly as the audit describes them, and it settled the four open D-06 sites. **Two repair services are real defects; one is a surprise.** `ScheduleEnvelopeRepairService` keys its `envelopes` map on the business date and then looks seats up by calendar date (8 call sites). `ScheduleConsistencyRepairService:146` builds `seatsByDateAgent` by calendar date and looks it up with a business date, so on a non-`00:00` desk an agent-day's post-midnight seats are not in the group that `applyPermutation` moves. `AgentAssignmentDifficultyComparator` must **not** be switched: calendar date plus clock time is the true chronological order, and business date plus clock time is not. Beyond CONTEXT's list, the sweep found one more site of the same class: `ShiftStartMixTargetService:148,163,170` (`computeIfAbsent(ts.getDate(), …)` into three maps that are then read with `sa.getDate()` business dates). It is a pure key change in a file the guard already scans for by verb, so this research recommends including it (Open Question 1 asks the planner/operator to confirm).

The structural guard does **not** catch the N-1 lines. Of the 11 `Timeslot`-receiver `getDate()` reads in the three files CONTEXT names, only 2 carry a scanned verb token (`computeIfAbsent(` at `ScheduleEnvelopeRepairService:141` and `ScheduleConsistencyRepairService:146`); the other 9, including all three `ShiftLibraryValidationService` lines, are invisible. The widening must be a second, verb-free scan applied only to the new files (a verb-free scan of `ScheduleOutputService` would fire on four legitimate calendar-date labels). Ordering matters for the red proof: land the guard extension first, run it against the unfixed tree to capture the red result (11 lines), then fix.

N-2 is a small additive backend change (one record component, two repository queries, two controller/service params) plus a four-line frontend swap. `timeslot.business_date` is a stored `NOT NULL` column with no index; the existing `deleteLiveByDeskAndBusinessDateRange` is the precedent for filtering on it in JPQL. Every hand-built `Timeslot` POJO in `ScheduleConsistencyRepairServiceTest`, `ScheduleEnvelopeRepairServiceTest` and `ShiftStartMixTargetServiceTest` omits `setBusinessDate(...)`, so switching those services to the business-date key will break their existing tests until the fixtures are fixed. That is the single biggest planning trap.

**Primary recommendation:** Sequence as (1) guard extension, red against HEAD; (2) N-1 validator fix with the D-08 red tests; (3) repair-service fixes, each behind a red test, fixtures given `setBusinessDate`; (4) N-2 backend (additive field + business-range query + service test); (5) N-2 frontend + `tsc` + live Playwright `browser_evaluate` check. Run plans sequentially (project memory: GSD execution runs sequential).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Which business day a timeslot belongs to | Database / Storage (`timeslot.business_date`, written once by `TimeslotGeneratorService`) | API / Backend (reads `getBusinessDate()`) | BDAY-08: single derivation, stored at write time. Consumers read the stored value, never recompute it |
| Shift-library coverage and weekday validity | API / Backend (`ShiftLibraryValidationService.covers`) | — | One predicate shared with the generator (P-03) |
| Business-range demand filter | Database / Storage (JPQL on `t.businessDate`) | API / Backend (param parsing, 400s) | Server-side filter keeps the final day's post-midnight rows; the browser must not derive dates (D-05) |
| Required / Over-under row keying | Browser / Client (`ScheduleResults.tsx`) | API / Backend (supplies `businessDate`) | The client only joins on a value the server provides |
| Post-solve seat/envelope repair | API / Backend (solver-adjacent services) | — | Operate on the in-memory `Schedule`; keys must match the maps built from `AgentShiftAssignment.getDate()` |
| Structural guard | Test tier (`BusinessDateJoinGuardTest`) | — | Textual scan of an explicit file list |

## Standard Stack

No new libraries. Everything below already exists in the repo.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot / Spring Data JPA (JPQL `@Query`) | repo-pinned | The two new business-range repository queries | Existing `StaffingRequirementRepository` pattern [VERIFIED: StaffingRequirementRepository.java:19-100, read this session] |
| JUnit 5 + AssertJ, `@DataJpaTest` (H2) | repo-pinned | New validator/service tests | Existing `ShiftLibraryValidationServiceTest` and `StaffingRequirementBusinessDateDeleteTest` style [VERIFIED: both files read this session] |
| `com.wfm.util.DayWindow` | in-repo | Calendar-date disclosure arithmetic (`calendarDateAtDayStartOffset`, `startMinuteFromDayStart`) | The one derivation; do not re-implement [VERIFIED: DayWindow.java:372-376, 441-451] |
| TypeScript 5.7 / `tsc` | `~5.7.0` | Frontend type check | `frontend/package.json` [VERIFIED: package.json] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Stored-column JPQL `t.businessDate BETWEEN` (recommended) | Widen-then-derive like `BusinessDayPeriodLoader` | Widen-then-derive is unsuitable for the cursor-paginated endpoint: filtering after a `LIMIT` page breaks `hasMore`/cursor. The stored column paginates correctly and has precedent in `deleteLiveByDeskAndBusinessDateRange` |
| Rename `Window.date` to `businessDate` (recommended) | Keep `date` + javadoc | Rename makes any stale `.date()` caller a compile error. Only 3 main call sites (`ShiftLibraryGenerationService:174`, `:854`, validator) and positional constructors in tests are untouched |

**Installation:** none. No packages are installed this phase.

## Package Legitimacy Audit

Not applicable: this phase installs no external packages. (No `package-legitimacy check` was run; there is nothing to check.)

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
                       timeslot row: date (calendar), business_date (stored, NOT NULL, written by TimeslotGeneratorService)
                                   |
        +--------------------------+---------------------------------------------+
        |                                                                        |
  N-1: validator                                                       N-2: allocation rows
  findAllLiveByDesk ──► Window(businessDate, start, end)               GET /staffing-requirements?businessFrom&businessTo
        |                    |                                                   |
        |            covers(template, bands, window, dayWindow)        repo: t.businessDate BETWEEN (same ORDER BY/cursor)
        |             ├ validWeekdays ∋ window.businessDate.dow                    |
        |             ├ isEffectiveOn(window.businessDate)             Item{date=calendar, businessDate=NEW}
        |             └ anchoredContains / anchoredOverlaps                        |
        |                    |                                         ScheduleResults.tsx: key `${r.businessDate}|HH:MM`
        |         shared with ShiftLibraryGenerationService                         |
        |                                                               lookup `${date}|${slot}` where date = grid business date
  uncoveredWindows / unsatisfiableWeekdays / peakShortfall(date, message)
  (D-02 label: "<biz> (<Dow>) HH:MM-HH:MM [calendar <cal>]" only when cal != biz)

  Same class, post-solve (SolverService.repairVerified callers):
  AgentShiftAssignment.getDate() = business date ──► envelopes map / byDate map   (keys are business dates)
  AgentAssignment.getTimeslot().getDate() = CALENDAR ──► free/occupied/seat maps   (keys are calendar dates)  <-- mismatch fixed in D-06
```

### Recommended Project Structure
No new directories except one test-resource fixture folder for the widened guard's pipeline red-proof:
```
src/main/java/com/wfm/service/            # edits only
src/main/java/com/wfm/dto/StaffingRequirementResponse.java
src/main/java/com/wfm/repository/StaffingRequirementRepository.java
src/main/java/com/wfm/controller/StaffingRequirementController.java
src/test/java/com/wfm/service/            # extend 5 existing tests, add 1 (list business range)
src/test/resources/bday-join-guard.md     # allowlist + scope notes
src/test/resources/bday-join-guard-offender-widened/OffendingSample.java   # new, never compiled
frontend/src/api/client.ts, frontend/src/pages/ScheduleResults.tsx
```

### Pattern 1: Additive-then-consume API change
**What:** Add `businessDate` to `StaffingRequirementResponse.Item`, leave `date` untouched, then switch the one consumer. (Phase 19 D-10 / Phase 20 precedent, CONTEXT `<code_context>`.)
**Example:**
```java
// Source: StaffingRequirementResponse.java (whole file read this session) — current shape, verbatim:
//   public record Item(UUID id, UUID timeslotId, UUID specializationId, LocalDate date,
//                      LocalTime startTime, LocalTime endTime, String specializationName,
//                      int requiredFTEs, String source) {}
// Recommended: insert `LocalDate businessDate` immediately after `date`. The only construction
// site in src/main and src/test is StaffingRequirementService.toResponseItem (:389-405).
// Jackson serialises by component name, so position is cosmetic.
```

### Pattern 2: Business-range query that keeps the calendar ORDER BY and cursor
The existing pageable queries order by `t.date, t.startTime, s.name, sr.id` and the cursor encodes calendar `date`. Keep that ordering in the new queries so cursors stay valid; only the `WHERE` changes.
```java
// Source: modelled on StaffingRequirementRepository.java:40-60 (verbatim predicate there:
//   "AND t.date BETWEEN :from AND :to " + "ORDER BY t.date, t.startTime, s.name, sr.id")
@Query("SELECT sr FROM StaffingRequirement sr JOIN FETCH sr.timeslot t JOIN FETCH sr.specialization s " +
       "WHERE sr.tenantId = :tenantId AND sr.deskId = :deskId AND sr.scheduleId IS NULL " +
       "AND t.businessDate BETWEEN :from AND :to " +
       "ORDER BY t.date, t.startTime, s.name, sr.id")
List<StaffingRequirement> findLiveByDeskAndBusinessDateRange(
        long tenantId, UUID deskId, LocalDate from, LocalDate to, Pageable pageable);
// + an ...AfterCursor twin: copy findLiveByDeskAndDateRangeAfterCursor, swap only the BETWEEN column.
```

### Pattern 3: Shared window label (D-02)
```java
// Window record today (ShiftLibraryValidationService.java:746), verbatim:
//   record Window(LocalDate date, LocalTime startTime, LocalTime endTime) {}
// Recommended: rename component to businessDate and add one method both services call.
record Window(LocalDate businessDate, LocalTime startTime, LocalTime endTime) {
    /** Business-date label; discloses the calendar date only when it differs (D-02). */
    String describe(DayWindow dayWindow) {
        LocalDate calendar = DayWindow.calendarDateAtDayStartOffset(dayWindow.dayStart(), businessDate,
                DayWindow.startMinuteFromDayStart(dayWindow.dayStart(), startTime));
        String base = businessDate + " " + startTime + "-" + endTime;           // == today's string
        return calendar.equals(businessDate) ? base
                : businessDate + " (" + businessDate.getDayOfWeek()
                        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + ") " + startTime + "-" + endTime
                        + " [calendar " + calendar + "]";
    }
}
```
`calendarDateAtDayStartOffset` adds `(startMinute(dayStart) + minutesFromDayStart) / 1440` days [VERIFIED: DayWindow.java:441-451], which is 0 for every window at a `00:00` anchor, so the `00:00` string is byte-identical to today's. `TextStyle` and `Locale` are already imported by this service (used in `peakShortfallMessage`).

### Anti-Patterns to Avoid
- **Switching `AgentAssignmentDifficultyComparator` to business date.** It compares `getDate()` then `getStartTime()`. Calendar date + clock time sorts chronologically; business date + clock time does not (at a 06:00 anchor, calendar Tue 01:00 would sort before calendar Mon 21:00 of the same business day). The comparator has no anchor to do `startMinuteFromDayStart`. Leave it; record why.
- **A tree-walk guard or a verb-free scan of `ScheduleOutputService`.** Lines 693, 736, 807, 875 are deliberate calendar-date labels (D-10) and would each need an allowlist entry (the 100-plus-entry decoration failure Phase 18 D-03 rejected).
- **Forking `covers()`** for the validator. D-01 forbids it; the generator calls the same one.
- **Computing a business date in TypeScript** (D-05).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Calendar date of a business-day window | Weekday/`plusDays` arithmetic in the validator | `DayWindow.calendarDateAtDayStartOffset` + `startMinuteFromDayStart` | The one derivation; both exist and handle every anchor |
| Business-date range filter on demand | Stream filter after pagination | JPQL `t.businessDate BETWEEN` | Stream-after-LIMIT breaks cursor paging; the column is stored and NOT NULL |
| Cursor encode/decode | New pagination | `CursorPagination` | The existing endpoint already uses it [VERIFIED: StaffingRequirementService.java:84-130] |
| Validator/generator window label | Two formatters | One `Window.describe(DayWindow)` | The generator's coverage `ErrorDetail` builds `window.date() + " " + start + "-" + end` independently (ShiftLibraryGenerationService.java:854); two copies would drift on a non-`00:00` desk |

## Runtime State Inventory

Not a rename/refactor/migration phase. The one stored value touched, `timeslot.business_date`, is read-only here: no data migration, no schema change, no new index required. The new response field is computed from the stored column. **Stored data:** none. **Live service config:** none. **OS-registered state:** none. **Secrets/env:** none. **Build artifacts:** none (frontend `dist/` is rebuilt by the normal build).

## Detailed Findings (the seven CONTEXT questions)

### Q-A. D-06: the repair services and the sweep

**`ScheduleEnvelopeRepairService` — confirmed defect, fix is a pure key change.**
The envelope map is keyed by business date [VERIFIED: ScheduleEnvelopeRepairService.java:122]:
`envelopes.put(new AgentDay(sa.getAgent().getId(), sa.getDate()), sa.getShiftBandPair());`
and every seat-side key uses the calendar date. Verbatim reads of the 8 calendar-date sites [VERIFIED: grep + Read this session]:

| Line | Verbatim | Role |
|------|----------|------|
| 141 | `freeByDate.computeIfAbsent(a.getTimeslot().getDate(), k -> new ArrayList<>()).add(a);` | free-seat map key |
| 144 | `AgentDay key = new AgentDay(a.getAgent().getId(), a.getTimeslot().getDate());` | `occupied` and the `envelopes.get(key)` lookup |
| 160 | `.comparing((AgentAssignment a) -> a.getTimeslot().getDate())` | deterministic violation sort |
| 277 | `LocalDate date = violation.getTimeslot().getDate();` | `candidatesFor`: `envelopes.get(key)` and `freeByDate.get(date)` |
| 323 | `violation.getAgent().getId(), violation.getTimeslot().getDate(),` | log arguments only |
| 349 | `LocalDate date = from.getTimeslot().getDate();` | `planMove` bookkeeping |
| 375 | `LocalDate date = move.from().getTimeslot().getDate();` | `restoreBookkeeping` |

(CONTEXT's `:350, :376` are the line after; the reads are at `:349` and `:375`.) Why it matters on a non-`00:00` desk: a post-midnight seat's calendar date is business date + 1, so `envelopes.get(new AgentDay(agent, calendar))` returns `null` (or, if the agent also works the next business day, **the wrong day's envelope**). Line 148-150 treats a `null` pair as "a violation this repair cannot fix" and skips it, so the violation is silently never found. The fix is replacing each `getTimeslot().getDate()` with `getTimeslot().getBusinessDate()` (at line 160, sort by `getBusinessDate()` then keep `thenComparing(a -> a.getTimeslot().getStartTime())`; that order is deterministic rather than chronological, which is all this sort needs, and it keeps the key system uniform for the widened guard). Size: 7 edits, no structural change — **within D-06's "key change" bound; no stop condition.**

*Red test shape* (06:00 anchor; extend `ScheduleEnvelopeRepairServiceTest`): schedule `dayStart` 06:00; one agent with a 21:00-05:00 no-band envelope on business Mon 2026-10-05; seated at calendar-Mon 21:00-24:00 and calendar-Tue 00:00-03:00 and 04:00-05:00 and one seat at calendar-Tue 05:00-06:00 (outside the envelope: violation); one free seat at calendar-Tue 03:00-04:00 (inside). Timeslots built with `setDate(calendar)` **and** `setBusinessDate(Mon)`. Scorer stub counts seats whose business-date envelope does not cover them using `DayWindow.anchoredAt(06:00)`. Assert `violationsFound == 1`, `violationsRepaired == 1`, the 05:00 seat vacated, the 03:00 seat held, final score 0. Before the fix: `violationsFound == 0` (key `(agent, Tue)` has no envelope). Note the existing stub scorer (`ScheduleEnvelopeRepairServiceTest.java:~50-70`) hardcodes `DayWindow.anchoredAt(LocalTime.MIDNIGHT)` and `seat.getTimeslot().getDate()`; parametrise the anchor and read `getBusinessDate()` there.

**`ScheduleConsistencyRepairService:146` — confirmed defect (not benign).** Verbatim [VERIFIED: ScheduleConsistencyRepairService.java:141-149]:
```java
seatsByDateAgent
        .computeIfAbsent(a.getTimeslot().getDate(), k -> new HashMap<>())
        .computeIfAbsent(a.getAgent().getId(), k -> new ArrayList<>())
        .add(a);
```
It is read at `:207` as `seatsByDateAgent.getOrDefault(date, Map.of())` where `date` is the key of `byDate`, which is `Collectors.groupingBy(AgentShiftAssignment::getDate)` — a **business** date. `applyPermutation` (`:318-352`) uses that map to move "a whole agent-day's seats" with the envelope. On a non-`00:00` desk the lookup for business day D returns only seats whose *calendar* date is D: it **omits** the agent-day's own post-midnight seats (calendar D+1) and **includes** the previous business day's post-midnight seats (calendar D, business D-1). After a swap, an agent holds a new envelope whose post-midnight seats stayed with the old holder, and may be handed seats from another day. Production impact: `repairVerified` re-scores and reverts on any hard regression (`:105-117`), so the likely live behaviour is a silent revert (WARN log "Usual-shift repair REVERTED") and zero consistency gain; `repair()` called bare would corrupt. [ASSUMED] that the revert path is what fires in production; the code read shows the corruption, the revert follows from the shift-envelope constraint scoring a seat outside its envelope as hard, which I did not execute.

The fix is the single key at `:146` -> `a.getTimeslot().getBusinessDate()`. *Red test* (extend `ScheduleConsistencyRepairServiceTest`): the existing `twoAgentsHoldingEachOthersUsualShift_areSwapped_andCoverageIsUnchanged` shape, but each agent also holds one post-midnight seat (calendar D+1 01:00, business D). Assert after `repair()` that `hoursOf` / seat identity for each agent is exactly the *other's* original seats (including the 01:00 one). Before the fix the 01:00 seats stay with the original holders and the assertion fails. The existing `seat(agent, hour)` helper takes only an hour; add an overload taking a calendar-date offset and a business date.

**`AgentAssignmentDifficultyComparator:21` — classify as benign, leave unchanged, document.** The class javadoc states the intent: "Date (earlier dates first — establishes day-by-day pattern)" then start time. Calendar date + clock time orders seats by real instant at every anchor; business date + clock time would misorder post-midnight seats ahead of the same business day's evening seats, and the comparator has no `dayStart` to fix that. It is used only as a construction-ordering heuristic (`AgentAssignment` entity annotation; fixtures `ShiftModeFixtures`, `LiveShapeShiftDeskFixture`). It is not in the guard's file list, so no allowlist entry is needed or possible; record the reasoning in `bday-join-guard.md` "Known scope boundaries" (that file already records out-of-scope sites this way).

**`BusinessDayPeriodLoader:80` — legitimate.** `DayWindow.businessDateOf(dayStart, sr.getTimeslot().getDate(), sr.getTimeslot().getStartTime())` is the derivation itself. Also legitimate: `TimeslotGeneratorService:135,137,202,206,308` (derivation/`isDesired`), `ScheduleOutputService:693,736,807,875` (labels, D-10), `TimeslotController:75` and `StaffingRequirementService:400` (response DTO calendar field), `ScheduleService:375` (`snapshot.setDate(live.getDate())` — snapshot copy; business date copied on the next lines).

**Sweep result — one site NOT in CONTEXT: `ShiftStartMixTargetService`.** [VERIFIED: ShiftStartMixTargetService.java:148, 163, 170, 177-193]
```java
reqByDate.computeIfAbsent(ts.getDate(), k -> new HashMap<>())          // :148
seatsByDate.computeIfAbsent(ts.getDate(), k -> new HashMap<>())        // :163
slotsByDate.computeIfAbsent(ts.getDate(), k -> new ArrayList<>()).add(ts);  // :170
...
rowsByDate.computeIfAbsent(sa.getDate(), k -> new ArrayList<>()).add(sa);   // :189  (business date)
```
They are read at `:198-203` with the business-date key (`reqByDate.getOrDefault(date, …)`, `slotsByDate.getOrDefault(date, …)`, `seatsByDate.getOrDefault(date, …)`). It runs only when `shiftStartMixMode` is not OFF (`SolverService.java:479-481`), so the default is unaffected, but a non-`00:00` desk in REPORT/ENFORCE mode would compute its start-mix targets against the wrong demand, slots and seat ceilings. Same defect class, three key edits, no structural change. Test fixtures: `ShiftStartMixTargetServiceTest.java:267` sets only `ts.setDate(DAY)`; it needs `ts.setBusinessDate(DAY)`. See Open Question 1.

Other `.getDate()` reads in `src/main/java` were enumerated and are not Timeslot reads (`sa.getDate()`, `AgentDayOff`, `AgentPreference`, `AgentException`, waivers). The residual false-negative risk is a `Timeslot` local with a name other than `ts` read by a non-chained `.getDate()` (e.g. `t.getDate()`); I found none outside `TimeslotGeneratorService`/`BusinessDayPeriodLoader`.

### Q-B. D-07: does the guard's predicate catch the N-1 lines?

[VERIFIED: BusinessDateJoinGuardTest.java:100-131, 298-349, read this session]. The match is `hasJoinVerb(code) && hasTimeslotReceiverGetDate(code)` on a single comment-stripped line, with `JOIN_VERB_TOKENS = List.of("join(", "equal(", "groupBy(", "computeIfAbsent(")`.

| Pre-fix line | File:line | Caught today? |
|---|---|---|
| `.map(sr -> new Window(sr.getTimeslot().getDate(),` | Validation:230 | **No** (`.map(` is not a verb) |
| `.map(sr -> sr.getTimeslot().getDate())` | Validation:455 | **No** |
| `LocalDate date = sr.getTimeslot().getDate();` | Validation:576 | **No** |
| `freeByDate.computeIfAbsent(a.getTimeslot().getDate(), …` | Envelope:141 | Yes |
| `AgentDay key = new AgentDay(a.getAgent().getId(), a.getTimeslot().getDate());` | Envelope:144 | **No** |
| `.comparing((AgentAssignment a) -> a.getTimeslot().getDate())` | Envelope:160 | **No** |
| `LocalDate date = violation.getTimeslot().getDate();` | Envelope:277 | **No** |
| `violation.getAgent().getId(), violation.getTimeslot().getDate(),` | Envelope:323 | **No** |
| `LocalDate date = from.getTimeslot().getDate();` | Envelope:349 | **No** |
| `LocalDate date = move.from().getTimeslot().getDate();` | Envelope:375 | **No** |
| `.computeIfAbsent(a.getTimeslot().getDate(), k -> new HashMap<>())` | Consistency:146 | Yes |

So adding the three files to `TARGET_FILES` alone yields 2 hits (and an unfixed tree with 11 real sites would "fail" only on 2); the N-1 file would stay silent — exactly the decoration outcome the guard's own doc warns about. The `bday-join-guard.md` "Known scope boundaries" already says the same of `SolverService` and records "a guard that reaches these shapes needs a different predicate — non-join key positions ... and a type-aware receiver test".

**Recommended widening (explicit, per-file, not global):**
1. Keep `TARGET_FILES` (4 files, verb predicate) exactly as is.
2. Add `WIDENED_TARGET_FILES` = `ShiftLibraryValidationService`, `ScheduleEnvelopeRepairService`, `ScheduleConsistencyRepairService` (and `ShiftStartMixTargetService` if Open Question 1 is accepted), scanned with `isWidenedTimeslotDateRead(rawLine)` = comment-stripped line non-empty AND the existing `hasTimeslotReceiverGetDate(code)` (no verb requirement). Reuse the three receiver shapes unchanged.
3. `scanFiles` takes the predicate as an argument; the headline test unions both scans into one `derived` set and compares to the single `### Allowlist` block (set equality, both directions, unchanged). Entries are `fqcn :: code` lines as today.
4. Rename `allFourTargetFilesExist` and update the class javadoc "FOUR-FILE" wording.
5. Liveness proofs for the widening, mirroring the existing two:
   - Matcher: `isWidenedTimeslotDateRead` returns `true` for each verbatim pre-fix line in the table above that lacks a verb (`.map(sr -> new Window(sr.getTimeslot().getDate(),`, `.map(sr -> sr.getTimeslot().getDate())`, `LocalDate date = sr.getTimeslot().getDate();`), and `false` for `sa.getDate()`, a commented-out occurrence, and a `getBusinessDate()` line.
   - Pipeline: a second tracked, never-compiled offender under `src/test/resources/bday-join-guard-offender-widened/OffendingSample.java` (exactly one real offending line plus one commented-out line), scanned through the widened path, `hasSize(1)`, and `assertThatThrownBy(... containsExactlyInAnyOrderElementsOf(Set.of()))`. Copy the existing fixture's header comment style [VERIFIED: bday-join-guard-offender/OffendingSample.java].
6. **Prove it red against the real pre-fix tree:** commit the guard extension *before* the N-1/D-06 fixes and capture the failing run (`./gradlew test --tests com.wfm.service.BusinessDateJoinGuardTest`), whose failure message lists the `NEW, not allowlisted` set: expect exactly the 11 lines above. The fixes then turn it green with an **empty** allowlist (no legitimate calendar-date read remains in these files once `:323` log and `:160` sort are migrated).
7. Update `bday-join-guard.md`: "Why this guard exists"/history paragraph (widened scope and why verb-free is safe only for these files), "Known scope boundaries" (comparator rationale, `ScheduleOutputService` labels remain verb-scoped, the `@Query` literal shape still unseen), and keep the `### Allowlist` fenced block (empty is the expected state; the two existing liveness proofs prevent vacuity).

### Q-C. D-04 / D-03: list endpoint, repository, column

**Controller/service conventions** [VERIFIED: StaffingRequirementController.java:25-33, StaffingRequirementService.java:84-130]:
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
The service branches `hasDateRange = from != null && to != null` x `hasCursor` into four repository calls, parsing with `LocalDate.parse` (a malformed date throws `DateTimeParseException`, which `GlobalExceptionHandler` does not map specially, so it surfaces as 500 — existing behaviour, not this phase's to change). `IllegalArgumentException` maps to HTTP 400 `VALIDATION_FAILED` [VERIFIED: GlobalExceptionHandler.java:32-34].

**Recommended change:**
- Controller: add `@RequestParam(required = false) String businessFrom` and `businessTo` (param names fit the conventions; `businessFrom`/`businessTo` keep `from`/`to` untouched). Pass through.
- Service: new signature `listRequirements(deskId, from, to, businessFrom, businessTo, cursor, limit)` (the controller is the only caller; no test calls it today). Add `hasBusinessRange = businessFrom != null && businessTo != null`. If exactly one of the two business params is present, or a calendar range and a business range are both present, throw `IllegalArgumentException` (400) — silently ignoring would return the desk's entire demand. (Discretion; the existing calendar pair silently ignores a half-supplied range, so this is stricter than precedent by design for a new param.) Branch: `hasBusinessRange && hasCursor` -> `findLiveByDeskAndBusinessDateRangeAfterCursor`; `hasBusinessRange` -> `findLiveByDeskAndBusinessDateRange`.
- The cursor map keeps `date` (calendar) + `startTime` + `specName` + `id` [VERIFIED: StaffingRequirementService.java:122-129] and the new queries keep `ORDER BY t.date, t.startTime, s.name, sr.id`, so a cursor from a business-range page resumes correctly.
- Record: add `LocalDate businessDate` after `date`; `toResponseItem` passes `t.getBusinessDate()`. The existing comment ("SOLV-07/D-10: deliberately the calendar date ...") stays attached to `date`; add one line saying `businessDate` is the grid's key [VERIFIED: StaffingRequirementService.java:389-405].
- The same `Item` is also returned by `saveRequirements`, `calculateErlangC` and `calculateErlangX` (`toResponseItem` is shared), so those responses gain the field too — additive, fine.

**`business_date` column:** `Timeslot.businessDate` is `@Column(name = "business_date", nullable = false)` with no Java default [VERIFIED: Timeslot.java:36-39]; V53 backfills and sets `NOT NULL` [VERIFIED: V53__add_desk_day_start_and_timeslot_business_date.sql:16-18]. The only timeslot index is `idx_timeslot_desk_date ON timeslot(tenant_id, desk_id, date)` [VERIFIED: V1__initial_schema.sql:186]; there is **no** index on `business_date`. The new query still gets the `(tenant_id, desk_id)` prefix of that index; a desk-year is tens of thousands of rows, so no migration is warranted. Optional hardening (not required): add `AND t.date BETWEEN :from AND :to+1` — NOT recommended, it adds a second date system to the predicate. Because the DB column is `NOT NULL`, a persisted `Timeslot` can never carry a null `businessDate`; the danger is only hand-built POJO fixtures (Pitfall 1).

**`BusinessDayPeriodLoader:75-85`** filters with widen-then-derive: query calendar `[periodStart, periodEnd+1]`, then keep rows whose `DayWindow.businessDateOf(dayStart, date, startTime)` lies in `[periodStart, periodEnd]` [VERIFIED: BusinessDayPeriodLoader.java:73-86]. That is why a stream filter is right for the unpaginated solver load and wrong for the paginated endpoint.

### Q-D. D-08 fixtures and exact red scenarios

Fixture style: `@DataJpaTest` with `@Import({ShiftLibraryValidationService.class, ShiftLibraryGenerationService.class, ShiftLibraryValidationController.class, ShiftTemplateService.class})`, `@MockitoBean TimeslotGeneratorService`, helpers `saveDeskWithDayStart(tenant, dayStart)`, `saveTemplate(deskId, name, start, end, breakOffset, breakDuration, weekdays, effectiveFrom, effectiveTo)`, `saveSpecialization`, `saveDemand(...)`, `saveAgent`, `saveAgentDayHours` [VERIFIED: ShiftLibraryValidationServiceTest.java:1195-1306]. **`saveTimeslot` hard-codes `timeslot.setBusinessDate(date)`** (`:1266`), i.e. it can only build 00:00-style rows. Add one helper (e.g. `saveDemandAnchored(tenant, deskId, spec, calendarDate, businessDate, start, end, fte)`) that sets `businessDate` explicitly. Pass the business date **explicitly** rather than via `DayWindow.businessDateOf`, so the fixture is an independent oracle (the repo precedent `StaffingRequirementBusinessDateDeleteTest.saveTimeslot` uses `businessDateOf`; both are acceptable, explicit is stronger here).

Shape (from CONTEXT `<specifics>` and the Phase-23 UAT fixture): desk `dayStart` 06:00 (tiles at 60-minute increments, unlike 06:30), template `Overnight` 21:00-06:00, no bands (net 9.00 h), `validWeekdays = {MONDAY}`, `effectiveFrom = LocalDate.of(2026, 10, 5)` (a Monday) or earlier as the case requires. A 21:00-06:00 template ends *at* the anchor; `covers()` already handles that via `dayWindow.anchoredContains` [VERIFIED: ShiftLibraryValidationService.java:263-270].

| Case | Demand fixture (calendar / business) | Pre-fix (red) | Post-fix |
|---|---|---|---|
| (a) no false refusal | Tue 2026-10-06 01:00-02:00 / business Mon 2026-10-05; template Mon-only, effective from before | `Window.date` = Tue, `getValidWeekdays()` lacks Tue -> uncovered; `requireShiftModeReady` throws | `uncoveredWindows` empty; `assertThatCode(() -> service.requireShiftModeReady(deskId)).doesNotThrowAnyException()` (add an agent with MONDAY 9.00 hours so the contracted-hours check passes) |
| (b) no false coverage | Mon 2026-10-05 01:00-02:00 / business **Sun** 2026-10-04; same template | Window.date = Mon -> covered (credited with Sunday's hours); `uncoveredWindows` empty | uncovered: `"2026-10-04 (Sun) 01:00-02:00 [calendar 2026-10-05]"` (adjust to the chosen D-02 wording). Add a second variant with `effectiveFrom = 2026-10-05` to prove `isEffectiveOn` also keys on business date (business Sun is before effective, calendar Mon is not) |
| (c1) unsatisfiable weekday | Demand only on business Monday: a 22:00-23:00 slot (cal Mon/biz Mon) and a 01:00-02:00 slot (cal Tue/biz Mon); agent with MONDAY 9.00 h | `demandDatesByWeekday` buckets calendar Tue -> reports `TUESDAY` unsatisfiable (a weekday with no demand), and `requireShiftModeReady` throws on it | `unsatisfiableWeekdays` empty |
| (c2) peak shortfall | Required 3 at the 01:00-02:00 slot above, one MONDAY-hours agent | `covers()` false on Tue -> `coveringNetHours` empty -> `continue` -> **no advisory at all** | one advisory, `date() == 2026-10-05`, `message()` contains `"Monday"` and `short by 2` |
| control (00:00) | Same weekday-restricted template at a 00:00 desk with ordinary daytime demand, `businessDate == date` | — | `uncoveredWindows` equals exactly today's string, e.g. `"2026-01-10 09:00-09:30"` (the exact assertion already at `ShiftLibraryValidationServiceTest.java:313`) and peak message unchanged |
| control (06:00 daytime) | Business Mon 10:00-11:00 (cal == biz) at the 06:00 desk, uncovered | — | string has **no** `(Mon)` / `[calendar …]` decoration: `"2026-10-05 10:00-11:00"` |

(c1)'s reported-weekday case relies on `hoursByWeekday` and `isEffectiveOn`; `unsatisfiable` is also thrown through `contractedHoursMessage`, so (c1) can also assert `requireShiftModeReady` does not throw once fixed.

### Q-E. Exact current strings (for the `00:00` assertions)

[VERIFIED: ShiftLibraryValidationService.java:241 and :620-629, :193-196, :214-221]
- Uncovered window, verbatim construction: `uncovered.add(window.date() + " " + window.startTime() + "-" + window.endTime());` -> e.g. `2026-01-05 09:00-09:30` (`LocalTime.toString()` drops seconds when zero).
- Refusal summary (when any uncovered window): `response.uncoveredWindows().size() + " demand window(s) have no covering shift template"`; each window is also added as `new ErrorDetail("coverage", window, null)`.
- Contracted-hours refusal: `" weekday(s) have no shift template any agent's contracted hours can satisfy: " + weekdayList + ". Add or adjust a template, or update contracted hours, before switching modes."` with `formatWeekday` giving `Monday` style.
- Peak shortfall, verbatim: `date + " " + window.startTime() + "-" + window.endTime() + " needs " + required + " agent(s), but only " + reachable + " rostered agent(s) that " + date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " could be working it at all — short by " + shortfall + ". This counts every agent whose contracted hours match a shift covering that hour, ignoring that they must also staff the rest of their shift, so the real figure can only be lower. No library change or longer solve can close this: it needs more rostered agents that day, or a lower forecast for that hour."` (exact tail as in the source; assert with `startsWith`/`contains`, not the whole sentence).
- `PeakShortfallAdvisory.date` is a response field consumed by `ShiftLibrary.tsx:292-310` (`PeakShortfallPanel` renders `{s.date} {s.startTime…}–{s.endTime…}` from fields, not `message`) — so after the fix that panel shows the business date with no calendar disclosure (Open Question 2).
- The generator's own coverage detail: `new ErrorDetail("coverage", window.date() + " " + window.startTime() + "-" + window.endTime(), null)` [VERIFIED: ShiftLibraryGenerationService.java:854-855], asserted at 00:00 by `ShiftLibraryGenerationServiceTest.java:~832`.

D-02 wording recommendation (inside the discretion grant): when `calendar != business`, `<business> (<Mon>) HH:MM-HH:MM [calendar <cal>]` (CONTEXT's own example); otherwise the unchanged `<business> HH:MM-HH:MM`. This leaves every string for a window whose calendar date equals its business date byte-identical, including on non-`00:00` desks, which is a stronger no-regression property than "00:00 only". Apply the same `Window.describe` in the generator's `computeUncoveredDetails` so the two coverage lists cannot diverge (identical output at `00:00`).

### Q-F. Frontend sites

[VERIFIED: ScheduleResults.tsx:384-435, 660-700, 950-990 read this session]
- State and fetch: `requiredPerSlot` `useState` at `:388`; effect `:389-427`. The fetch calls `staffingRequirements.list(schedule.deskId, { from: schedule.periodStartDate, to: schedule.periodEndDate, cursor })` (`:401-403`), pages to exhaustion with a 200-page bound.
- Key: `const key = \`${r.date}|${toHHMM(r.startTime)}\`` (`:414`).
- Lookups: `requiredPerSlot[\`${date}|${slot}\`]` at `:672`, `:686`, `:962`, `:976`. `date` there is the per-date section variable from `dates = [...new Set(schedule.agentSchedule?.map(e => e.date) || [])].sort()` (`:197`), i.e. the **business date** (the "unfilledSlots" map at `:462-471` already keys `"businessDate|HH:MM"` from structured `v.businessDate`; this is the in-file precedent).
- How the period is known: `schedule.periodStartDate` / `schedule.periodEndDate` (the solve's business period; the solver loads by business range through `BusinessDayPeriodLoader`). `schedule.dayStart` is also present but must not be used for arithmetic (D-05).
- API client: `staffingRequirements.list: (deskId, params?: { from?: string; to?: string; cursor?: string })` builds a `URLSearchParams` [VERIFIED: client.ts:233-239]; add `businessFrom?`, `businessTo?` and `query.set(...)` lines. `StaffingRequirement` type at `client.ts:407` is `{ id; timeslotId; specializationId; date; startTime; endTime; specializationName; requiredFTEs; source }`; add `businessDate: string`.
- `ScheduleResults.tsx` is the only consumer of `staffingRequirements.list` and of the `StaffingRequirement` type outside `client.ts` (grep this session), so making `businessDate` required has no other call-site fallout; `tsc` will confirm.
- Edits: swap the two params to `businessFrom/businessTo`, change `r.date` to `r.businessDate` at `:414`, nothing else (lookups already use the business `date`). Update the comment above the effect to say the fetch is keyed on the business date.

### Q-G. Verification commands

- Backend compile: `./gradlew compileJava compileTestJava`.
- Frontend type check: `cd frontend && npx tsc --noEmit -p tsconfig.json` (no `typecheck` script; `npm run build` runs `tsc -b && vite build`). Baseline passes with no output in ~2 s [VERIFIED: ran this session].
- Targeted runs always as explicit FQCN lists; **never read a suite aggregate after a `--tests` run** (project memory: filtered run wipes other classes' XML). Whole-suite is `./gradlew cleanTest test` (UP-TO-DATE otherwise fakes green; ~14 min warm, 1217 tests/197 classes on 2026-10-02 per memory). Run `./gradlew --stop` first if a daemon is old.
- Baseline this session: `./gradlew test --tests com.wfm.service.BusinessDateJoinGuardTest --tests com.wfm.service.ShiftLibraryValidationServiceTest -q` -> `BusinessDateJoinGuardTest` 6 tests 0 failures; `ShiftLibraryValidationServiceTest` 59 tests 0 failures (XML read for both).

## Common Pitfalls

### Pitfall 1: Hand-built `Timeslot` fixtures with no `businessDate` break the moment a service switches keys
**What goes wrong:** `ScheduleConsistencyRepairServiceTest.java:268`, `ScheduleEnvelopeRepairServiceTest.java:246` and `ShiftStartMixTargetServiceTest.java:267` all build `Timeslot` POJOs with `setDate(...)` and no `setBusinessDate(...)` [VERIFIED: grep this session]. After the fix their maps are keyed by `null` while the lookup uses the business date, so the *existing* tests fail (seats never move), looking like a regression.
**Why:** `businessDate` has no Java-side default; only persisted rows are guaranteed non-null by `NOT NULL`.
**How to avoid:** add `ts.setBusinessDate(DAY/DATE)` in each fixture in the same task as the production edit; for a `00:00`-style fixture `businessDate == date` is correct and inert. The Envelope test's stub scorer also reads `seat.getTimeslot().getDate()` at `:~58`; migrate it. Also see project memory "Null businessDate degrades silently" (solver fixtures, same cause).
**Warning signs:** a repair test that previously passed now reports "nothing changed".

### Pitfall 2: Proving the guard red after the fixes instead of before
**What goes wrong:** extending the guard in the same change as the fixes yields a green guard and no evidence it can fail.
**How to avoid:** guard task first, capture the red output, then fix (Q-B point 6). The pipeline fixture proves the mechanism, the real-tree run proves the 11 lines.

### Pitfall 3: Widening the verb predicate globally
Applying a verb-free scan to `TARGET_FILES` would flag `ScheduleOutputService:693,736,807,875` (calendar labels, D-10). Keep the widened scan to the new explicit list only.

### Pitfall 4: Filtering business range after pagination
A stream filter on a `LIMIT limit+1` page makes `hasMore` and the cursor lie. Use the JPQL `t.businessDate` predicate.

### Pitfall 5: Rest-day disclosure string changes for non-00:00 same-day windows
If `describe` decorated every window on a non-`00:00` desk, existing 21:00-anchored tests asserting plain strings would change. The rule "decorate only when calendar != business" avoids it.

### Pitfall 6: `Window.date` rename ripple
Renaming the record component changes accessor `date()` to `businessDate()`: `ShiftLibraryGenerationService.java:174` (`w.date().getDayOfWeek()`) and `:854` need updating; positional constructors in `ShiftLibraryGenerationServiceTest` and `MidnightWindowSeamTest` are unaffected. Compile errors will list every site.

### Pitfall 7: Local verification environment
Per project memory: throwaway pgvector DB on 55432, app on 8081 (`--cors.allowed-origins=http://localhost:3001`), vite on 3001 with a copied config inside `frontend/` pointing to 8081; never migrate the local 5432 DB, never kill the 8080 instance, never touch dev (dev is production). Unknown routes return 500, so check paths first. Playwright screenshots time out; use `browser_snapshot` / `browser_evaluate`.

## Code Examples

### Validator window construction (N-1)
```java
// ShiftLibraryValidationService.findUncoveredWindows — current, verbatim [VERIFIED: :229-234]:
//   .map(sr -> new Window(sr.getTimeslot().getDate(),
//           sr.getTimeslot().getStartTime(), sr.getTimeslot().getEndTime()))
// Fix:
.map(sr -> new Window(sr.getTimeslot().getBusinessDate(),
        sr.getTimeslot().getStartTime(), sr.getTimeslot().getEndTime()))
// ...and the sort/label:
.sorted(Comparator.comparing(Window::businessDate).thenComparing(Window::startTime))   // see note below
uncovered.add(window.describe(dayWindow));
```
Note on sorting: at a non-`00:00` anchor, `thenComparing(Window::startTime)` orders 00:00-06:00 before 21:00 within one business day, which is not chronological from the day start. CONTEXT does not ask to change ordering, and `00:00` output must be unchanged; ordering by `DayWindow.startMinuteFromDayStart(dayStart, startTime)` is identical at `00:00` and chronological elsewhere, so it is a free improvement the planner may take (discretion; add one ordering assertion if taken).

### `findUnsatisfiableWeekdays` and peak shortfall
```java
// :455  .map(sr -> sr.getTimeslot().getDate())           -> .map(sr -> sr.getTimeslot().getBusinessDate())
// :576  LocalDate date = sr.getTimeslot().getDate();      -> ...getBusinessDate();   (:599 date.getDayOfWeek() follows)
// peakShortfallMessage(date, window, ...) -> append the calendar disclosure via window.describe(dayWindow)
//   (needs dayWindow passed in; it is already a parameter of findPeakShortfalls).
```

### Guard widened scan (sketch)
```java
// Source: extends BusinessDateJoinGuardTest.java's own helpers (hasTimeslotReceiverGetDate, stripComment).
private static boolean isWidenedTimeslotDateRead(String rawLine) {
    String code = stripComment(rawLine);
    return !code.isEmpty() && hasTimeslotReceiverGetDate(code);   // no verb requirement
}
private static Set<String> scanFiles(List<Path> files, java.util.function.Predicate<String> matcher) throws IOException { ... }
```

### Frontend swap
```ts
// client.ts
list: (deskId: string, params?: { from?: string; to?: string; businessFrom?: string; businessTo?: string; cursor?: string }) => {
  const query = new URLSearchParams()
  if (params?.from) query.set('from', params.from)
  if (params?.to) query.set('to', params.to)
  if (params?.businessFrom) query.set('businessFrom', params.businessFrom)
  if (params?.businessTo) query.set('businessTo', params.businessTo)
  if (params?.cursor) query.set('cursor', params.cursor)
  ...
// ScheduleResults.tsx :401-403  { businessFrom: schedule.periodStartDate, businessTo: schedule.periodEndDate, cursor }
// ScheduleResults.tsx :414      const key = `${r.businessDate}|${toHHMM(r.startTime)}`
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Calendar `getDate()` keys across services | `getBusinessDate()` keys | Phase 20 (solver, output, generator, upload delete), Phase 21 (Erlang), Phase 24 (validator, repairs, allocation rows) | Last holdouts are the ones this phase closes |
| Verb-scoped four-file guard | Verb scoped + a verb-free widened scan on an explicit list | This phase | Reaches `.map(`, local assignments, constructors |

**Deprecated/outdated:** CONTEXT/ROADMAP line citations `:350, :376` (actual `:349, :375`) and `:599` (a `date.getDayOfWeek()` consumer of the `:576` variable, not a separate read).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | In production the consistency-repair defect manifests as a silent `repairVerified` revert (no consistency gain on non-`00:00` desks) rather than a shipped corrupted schedule | Q-A | Low: the fix and red test are the same either way; only the severity wording in the SUMMARY changes. Not executed against a solver run |
| A2 | `ShiftStartMixTargetService` mis-keying has an observable effect only when `shiftStartMixMode` is REPORT/ENFORCE on a non-`00:00` desk | Q-A | Low: determines whether to fold it in now vs defer; no live non-`00:00` desk uses ENFORCE per memory (live ENFORCE results are Saferide, 00:00) — not independently confirmed |
| A3 | A stricter 400 for half-supplied / mixed calendar+business ranges is acceptable to the operator | Q-C | Low: discretion item; alternative is to follow the silent-ignore precedent |
| A4 | Clients other than `ScheduleResults.tsx` do not consume the staffing-requirements list (e.g. external scripts) | Q-C/Q-F | Low: the change is additive so such a client is unaffected either way |

## Open Questions

1. **Fold `ShiftStartMixTargetService:148,163,170` into this phase?**
   - What we know: same defect class, three key edits, one fixture line (`ShiftStartMixTargetServiceTest.java:267`), within D-06's "key change" bound; already reachable by the verb predicate.
   - What's unclear: CONTEXT's D-06 list is explicit and does not name it; operator may prefer a follow-up.
   - Recommendation: include it (it is the same gap and the guard extension would otherwise leave an unguarded copy of the very pattern). If the operator declines, record it in `bday-join-guard.md` "Known scope boundaries" and the phase's deferred notes instead of leaving it undiscovered.
2. **`PeakShortfallPanel` shows `{s.date}` from the field (business date after the fix) with no calendar disclosure.**
   - What we know: the D-02 disclosure lives in `message` and `uncoveredWindows` strings; the panel renders fields. CONTEXT says no new UI and `PeakShortfallAdvisory.date` changes meaning only to "business date".
   - Recommendation: leave the panel as is (the date it now shows matches the schedule grid's headings); note it in the SUMMARY. Do not add a `calendarDate` field (a second published-contract change).
3. **Sort order of `uncoveredWindows` at non-`00:00` anchors** — optional improvement noted under Code Examples; take it or leave it.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | Gradle build/tests | yes | OpenJDK 21.0.12.1 | — |
| Gradle wrapper | tests | yes | `./gradlew` (ran this session) | — |
| Node / npx | `tsc` type check | yes | v24.19.0 | — |
| Docker | throwaway pgvector DB for live verification (D-09) | yes (daemon responds, no containers running) | 29.6.2 | — |
| `psql` | optional DB seeding for the live check | yes | Postgres.app | `docker exec` into the container |
| Ports 8080/8081/3001/55432 | live verification | 8080 occupancy not checked; 8081/3001/55432 free (no listeners seen) | — | Keep 8080 untouched (Pete's own instance) |

**Missing dependencies with no fallback:** none.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ + Spring `@DataJpaTest` (H2, profile `test`); Mockito `@MockitoBean` |
| Config file | `build.gradle` `tasks.named('test') { useJUnitPlatform() ... }` |
| Quick run command | `./gradlew test --tests <FQCN> [--tests <FQCN> ...]` (targeted; do not read aggregates afterwards) |
| Full suite command | `./gradlew --stop && ./gradlew cleanTest test` (~14 min warm) |
| Type check | `cd frontend && npx tsc --noEmit -p tsconfig.json` |

### Phase Requirements -> Test Map
| Req ID / Decision | Behavior | Test Type | Automated Command | File Exists? |
|---------|----------|-----------|-------------------|-------------|
| D-07 / BDAY-05 | Widened guard goes red on the 11 pre-fix lines, green after fixes; matcher + pipeline liveness for the widened scan | unit (textual scan) | `./gradlew test --tests com.wfm.service.BusinessDateJoinGuardTest` | edit existing; new fixture `bday-join-guard-offender-widened/OffendingSample.java` (Wave 0) |
| D-08a / OVNT-05 | Weekday-restricted overnight template accepts its own post-midnight hours; `requireShiftModeReady` does not throw | integration (`@DataJpaTest`) | `./gradlew test --tests com.wfm.service.ShiftLibraryValidationServiceTest` | edit existing + new anchored-demand helper |
| D-08b / OVNT-05, SOLV-07 | No credit for another business day's hours; `isEffectiveOn` keyed on business date | integration | same | same |
| D-08c / OVNT-05 | Unsatisfiable-weekday and peak-shortfall bucket on business weekday | integration | same | same |
| D-02 / OVNT-07 | Disclosure `[calendar …]` only when calendar != business; `00:00` and 06:00-daytime strings unchanged | integration | same | same |
| D-01 agreement | Generator and validator agree on a 06:00 desk (optional: one agreement test, precedent `requireShiftModeReady_and_createShiftTemplate_agreeOnTheSameOvernightEscape`) | integration | `./gradlew test --tests com.wfm.service.ShiftLibraryValidationServiceTest --tests com.wfm.service.ShiftLibraryGenerationServiceTest` | edit existing |
| D-06 envelope | Post-midnight seat outside envelope is found and moved on a 06:00 desk; 00:00 control unchanged | unit (POJO) | `./gradlew test --tests com.wfm.service.ScheduleEnvelopeRepairServiceTest` | edit existing; fixtures need `setBusinessDate` |
| D-06 consistency | Swap moves post-midnight seats with the envelope on a 06:00 desk; 00:00 control unchanged | unit (POJO) | `./gradlew test --tests com.wfm.service.ScheduleConsistencyRepairServiceTest` | edit existing; fixtures need `setBusinessDate` |
| D-06 start-mix (if folded in) | Targets keyed by business date | unit | `./gradlew test --tests com.wfm.service.ShiftStartMixTargetServiceTest --tests com.wfm.solver.ShiftStartMixSteerTest` | edit existing; fixture line 267 |
| D-03 / SOLV-07 | `Item.businessDate` populated from `Timeslot.getBusinessDate()`; `date` stays calendar | integration | `./gradlew test --tests com.wfm.service.StaffingRequirementListBusinessRangeTest` | NEW (Wave 0), modelled on `StaffingRequirementBusinessDateDeleteTest` (service built by hand) |
| D-04 / BDAY-02 | Business range returns the final business day's post-midnight rows that `from/to` omits; cursor paging across pages loses/duplicates nothing; half/mixed ranges -> 400 | integration | same | NEW |
| D-04 controller | param plumbing (`businessFrom`/`businessTo` reach the service) | optional controller slice | `./gradlew test --tests com.wfm.controller.StaffingRequirementControllerTest` | NEW, optional (no controller test exists today) |
| D-05 / N-2 | Required / Over-under rows use `businessDate`; no TS date arithmetic | type check + manual live | `cd frontend && npx tsc --noEmit -p tsconfig.json`; Playwright `browser_evaluate` (D-09) | n/a |
| Regression | Whole suite green | full | `./gradlew --stop && ./gradlew cleanTest test` | existing |

### Sampling Rate
- **Per task commit:** the targeted class(es) for that task (table above), plus `./gradlew compileJava compileTestJava`.
- **Per wave merge:** all touched classes in one `--tests` invocation, plus `tsc` after any frontend edit.
- **Phase gate:** `./gradlew cleanTest test` green (confirm bare `> Task :test`, not `UP-TO-DATE`; aggregate XML by filename incl. `@Nested`), `tsc` clean, live check recorded.

### Wave 0 Gaps
- [ ] `src/test/resources/bday-join-guard-offender-widened/OffendingSample.java` — pipeline red-proof for the widened scan.
- [ ] `src/test/java/com/wfm/service/StaffingRequirementListBusinessRangeTest.java` — D-03/D-04/D-09 (backend-first N-2 proof). Constructor to copy: `new StaffingRequirementService(staffingRequirementRepository, timeslotRepository, specializationRepository, new ErlangCalculatorService(), deskRepository, testEntityManager.getEntityManager())` [VERIFIED: StaffingRequirementBusinessDateDeleteTest.java:92-96].
- [ ] Anchored-demand helper in `ShiftLibraryValidationServiceTest` (explicit `businessDate`).
- [ ] `setBusinessDate(...)` in the three POJO fixtures (Pitfall 1).
- Framework install: none.

**Live check (D-09), measured not screenshotted:** seed (via API/SQL against the throwaway DB only) a 06:00 desk with a schedule whose final business day has post-midnight demand; open Agent Allocation; use `browser_evaluate` to read the "Required" and "Over / under" cell text for a post-midnight slot on (i) a middle day and (ii) the **last** day, and assert they equal the seeded demand (before the fix the last day's post-midnight cells are empty and a middle day's show the next business day's demand).

## Security Domain

`security_enforcement` is absent from `.planning/config.json`, so it is treated as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (no auth surface touched) | — |
| V3 Session Management | no | — |
| V4 Access Control | yes | Tenant scoping stays in every new query: `sr.tenantId = :tenantId AND sr.deskId = :deskId` copied from the existing queries; the service reads `TenantContext.getTenantId()` |
| V5 Input Validation | yes | New `businessFrom`/`businessTo` are `LocalDate.parse`d; reject half/mixed ranges with `IllegalArgumentException` -> 400 `VALIDATION_FAILED`. Note: a malformed date string throws `DateTimeParseException` -> 500 today for the existing params too; keep parity or add a catch that rethrows `IllegalArgumentException` (optional hardening) |
| V6 Cryptography | no | — |

### Known Threat Patterns
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cross-tenant read via new endpoint params | Information disclosure | Tenant id in every JPQL `WHERE`; `ShiftLibraryValidationServiceTest.validate_crossTenant_...` is the existing precedent; add a tenant filter assertion to the new list test |
| Unbounded page size | DoS | `CursorPagination.clampLimit` already caps at 1000 |
| JPQL injection | Tampering | Named parameters only; no string concatenation of request values |

## Sources

### Primary (HIGH confidence — files opened with Read/sed this session)
- `.planning/phases/24-…/24-CONTEXT.md`, `.planning/ROADMAP.md` (Phase 24 block), `.planning/REQUIREMENTS.md` (requirement lines)
- `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` (lines 85-300, 425-480, 560-660, 736-750)
- `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` (grep of `Window`/`covers`/`getBusinessDate`, lines 835-860)
- `src/main/java/com/wfm/service/ScheduleEnvelopeRepairService.java` (lines 60-400)
- `src/main/java/com/wfm/service/ScheduleConsistencyRepairService.java` (lines 1-400)
- `src/main/java/com/wfm/service/ShiftStartMixTargetService.java` (lines 100-260)
- `src/main/java/com/wfm/service/StaffingRequirementService.java` (lines 40-130, 375-410), `BusinessDayPeriodLoader.java`, `SolverService.java` (2420-2445, 1530-1550, 465-500)
- `src/main/java/com/wfm/controller/StaffingRequirementController.java`, `GlobalExceptionHandler.java` (handler map), `dto/StaffingRequirementResponse.java`, `dto/ShiftLibraryValidationResponse.java`, `repository/StaffingRequirementRepository.java`, `model/Timeslot.java`, `model/ShiftBandPair.java`, `util/DayWindow.java`, `util/CursorPagination.java`, `solver/AgentAssignmentDifficultyComparator.java`
- `src/main/resources/db/migration/V1__initial_schema.sql:182-187`, `V53__add_desk_day_start_and_timeslot_business_date.sql`
- `src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java`, `src/test/resources/bday-join-guard.md`, `bday-join-guard-offender/OffendingSample.java`, `ShiftLibraryValidationServiceTest.java`, `ScheduleConsistencyRepairServiceTest.java`, `ScheduleEnvelopeRepairServiceTest.java`, `StaffingRequirementBusinessDateDeleteTest.java`
- `frontend/src/pages/ScheduleResults.tsx` (370-475, 655-700, 950-985), `frontend/src/pages/ShiftLibrary.tsx` (292-312), `frontend/src/api/client.ts` (225-240, 383-410), `frontend/package.json`, `frontend/tsconfig.json`
- Project memory files cited inline (verification recipe, gradle result-reading traps, null businessDate, Phase-23 UAT fixture)

### Secondary (MEDIUM confidence)
- None used.

### Tertiary (LOW confidence)
- None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new libraries; all patterns already in the repo.
- Architecture: HIGH — every key mismatch confirmed by reading both the writer and reader of each map.
- Pitfalls: HIGH — the fixture/null-businessDate trap and the guard-predicate gap were both checked directly against the files.
- Production severity of the consistency-repair defect: MEDIUM (A1, reasoned not executed).

**Research date:** 2026-10-07
**Valid until:** 2026-11-06 (stable in-repo code; re-check line numbers if any of the named files change before planning)
