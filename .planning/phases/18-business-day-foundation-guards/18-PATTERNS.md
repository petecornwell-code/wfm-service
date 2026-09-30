# Phase 18: Business-Day Foundation & Guards - Pattern Map

**Mapped:** 2026-09-30
**Files analyzed:** 20 (create/modify)
**Analogs found:** 20 / 20 — but 5 files' "analog" is their own rescue-tag cherry-pick (verbatim source,
not a pattern to imitate), 1 file's analog is a superseded diff that must NOT be copied verbatim (F-1),
1 file's analog is a same-shape diff whose *intent* must be re-implemented, not its diff, and 1 area
(BDAY-06) has no single-file analog at all — two mechanisms from two different files compose instead.

**How to read this document.** This phase is unusual: most of its content already exists as five
verified, dry-run-clean commits on `rescue/phase-18-unwind-20260930`. For those files "the pattern to
copy" is close to "the literal diff," with one correction (F-1). Read the `## Salvage Instructions`
section before the per-file tables — it is load-bearing for how the planner should phrase each task.

## Salvage Instructions (read first)

| Commit | What it contains | Cherry-pick as-is? |
|---|---|---|
| `84fdc3f` | V53 migration, `Desk.dayStart`/`Timeslot.businessDate` entity fields, `DayStartRequest` DTO, `DeskController.setDayStart`, `DeskService.setDayStart`, frontend `Desk.dayStart` + `desks.setDayStart` + Day Start column, `DeskServiceDayStartTest` | **No — strip `confirmOverride` first (Finding F-1, below).** Everything else is verbatim-clean per RESEARCH.md's dry-run. |
| `a1c0077` | `MigrationEntityConsistencyTest.DECLARED_TABLES` += `timeslot`, `desk` | Yes, verbatim. |
| `2196e40` | `ts.setBusinessDate(date)` in the generator's create loop + `snapshot.setBusinessDate(live.getBusinessDate())` in `ScheduleService.acceptSchedule` + 6 test-fixture updates | Yes for `ScheduleService` and the 6 test fixtures. **No for the `TimeslotGeneratorService` hunk** — D-19/D-20's re-authored generator change (see BDAY-03 below) supersedes this exact line with a derived value; applying `2196e40` first and then rewriting that one line is the correct sequencing (do not skip it and hand-write the derivation from scratch — everything else in this commit is real and needed). |
| `7d42f23` | `BusinessDateWritePathGuardTest` (352 lines) + `bday-02-write-paths.md` (65 lines) | Yes, verbatim. Already satisfies BDAY-08. |
| `63d85a6` | `MidnightTimeArithmeticGuardTest` comparison-operator extension (+169 lines) + `midnight-time-arithmetic.md` comparison section (+80 lines) | Yes, verbatim, **then apply D-25**: extract `SOURCE_ROOT` from a hardcoded `private static final Path` to a parameter, and add a red-proof pointing the scan pipeline at a synthetic offending file in a new test-resources fixture directory (no existing precedent for this red-proof — new work). |
| `985e365` | `dayStart` parameter + `requireDayStartTiles` refusal on `generateTimeslots`, plus its own two call-site updates and two new test files | **Re-author, do not cherry-pick** (D-23). See BDAY-03 below — the tiling-refusal half transfers almost unchanged; the business-date-write half must be rewritten. |
| `fec8990` | Accepted-schedule refusal + `confirmOverride` consumption, `ScheduleRepository.findByTenantIdAndDeskIdAndStatusOrderByCreatedAtDesc` | **Not cherry-picked (D-27).** Read only to understand what F-1 is removing the hook for. Its refusal logic itself (the `ConflictException` naming the blocking schedule) is explicitly in scope per D-27 — see the F-1 resolution below. |

### Finding F-1 — the `confirmOverride` dead-parameter surface (resolve before landing `84fdc3f`)

`84fdc3f`'s `DayStartRequest` record, `DeskController.setDayStart`, and `DeskService.setDayStart` all
carry a `confirmOverride boolean` whose only consumer is `fec8990`, which D-23/D-27 exclude from this
phase. Concretely, the surface to strip or repoint is:

- `src/main/java/com/wfm/dto/DayStartRequest.java` — `record DayStartRequest(LocalTime dayStart, boolean confirmOverride)` → drop the second field entirely: `record DayStartRequest(LocalTime dayStart)`.
- `src/main/java/com/wfm/controller/DeskController.java` — `deskService.setDayStart(deskId, request.dayStart(), request.confirmOverride())` → drop the third argument.
- `src/main/java/com/wfm/service/DeskService.java` — `setDayStart(UUID deskId, LocalTime dayStart, boolean confirmOverride)` → drop the third parameter; the javadoc's "confirmOverride accepted, unused until Task 2's refusal" sentence must go too (D-24 — no forward references to a Task 2 that isn't in this phase's plan).
- `frontend/src/api/client.ts` — `setDayStart: (id: string, dayStart: string, confirmOverride: boolean) => ...` → drop the third parameter and the `confirmOverride` key from the request body.
- `src/test/java/com/wfm/service/DeskServiceDayStartTest.java` — every `deskService.setDayStart(desk.getId(), <value>, false)` call and `new DayStartRequest(<value>, false)` construction loses its trailing `false`/boolean argument.

**D-27's refusal itself is still in scope** — build it as an **unconditional** block (RESEARCH.md's
recommended resolution, consistent with D-27's own "an override with no caller is untested surface
area" reasoning): copy `fec8990`'s `ScheduleRepository` finder addition and the `ConflictException`
body from `DeskService.setDayStart` verbatim, but delete the `if (!confirmOverride)` guard around it —
the refusal fires unconditionally whenever an `ACCEPTED` schedule exists on the desk, with no bypass.
`fec8990`'s test file section (the four `acceptedScheduleExists...`/`twoAcceptedSchedules...` tests) is
the analog for that refusal's tests — drop only the `confirmOverride=true` bypass test
(`setDayStart_acceptedScheduleExists_confirmOverrideTrue_succeedsAndPersists`), since there is no
bypass to prove, and drop the trailing `false` argument from the rest.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/resources/db/migration/V53__*.sql` | migration | batch (schema DDL) | `84fdc3f`'s `V53__add_desk_day_start_and_timeslot_business_date.sql` (rescue-tag commit, not current tree) | exact — cherry-pick |
| `src/main/java/com/wfm/model/Desk.java` | model | CRUD | `84fdc3f`'s diff to this same file (current tree has 3 fields; cherry-pick adds the 4th) | exact — cherry-pick |
| `src/main/java/com/wfm/model/Timeslot.java` | model | CRUD | `84fdc3f`'s diff to this same file | exact — cherry-pick |
| `src/main/java/com/wfm/dto/DayStartRequest.java` | model (DTO) | request-response | `84fdc3f`'s new file, **minus `confirmOverride`** (F-1) | exact, with correction |
| `src/main/java/com/wfm/dto/DeskResponse.java` | model (DTO) | request-response | `84fdc3f`'s diff to this same file | exact — cherry-pick |
| `src/main/java/com/wfm/controller/DeskController.java` | controller | request-response | `84fdc3f`'s diff, **minus `confirmOverride` arg** (F-1) | exact, with correction |
| `src/main/java/com/wfm/service/DeskService.java` | service | CRUD | `84fdc3f`'s diff (gate) + `fec8990`'s diff (refusal, unconditional per F-1) | exact, composed, with correction |
| `frontend/src/api/client.ts` | provider (API client) | request-response | `84fdc3f`'s diff, **minus `confirmOverride` param** (F-1) | exact, with correction |
| `frontend/src/pages/DeskManagement.tsx` | component | CRUD (display) | `84fdc3f`'s diff **AND** current-tree `schedulingMode` cell (see below) | exact — cherry-pick, verified against current tree |
| `src/test/java/com/wfm/service/DeskServiceDayStartTest.java` | test | CRUD | `84fdc3f`'s new file + `fec8990`'s extension, both **minus `confirmOverride`** (F-1) | exact, with correction |
| `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java` | test | batch (schema reconciliation) | `a1c0077` (rescue-tag commit) | exact — cherry-pick |
| `src/main/java/com/wfm/service/ScheduleService.java` (`acceptSchedule`) | service | CRUD (snapshot copy) | `2196e40`'s diff to this same file | exact — cherry-pick |
| `src/test/java/com/wfm/service/DeskServiceSchedulingModeTest.java` (+5 more test fixtures) | test | CRUD | `2196e40`'s diff to these 6 files | exact — cherry-pick |
| `src/main/java/com/wfm/service/TimeslotGeneratorService.java` (`generateTimeslots`, generation loop) | service | transform (batch generation) | `985e365` for the `dayStart` param + `requireDayStartTiles` shape; **no precedent** for the anchored business-date derivation (D-19/D-20 — new work) | role-match (tiling refusal) / no analog (anchored derivation) |
| `src/main/java/com/wfm/util/DayWindow.java` (new date-aware functions) | utility | transform | itself — current tree, additive only | role-match (extend in place) |
| `src/main/java/com/wfm/controller/TimeslotController.java` | controller | request-response | `985e365`'s one-line diff (pass `LocalTime.MIDNIGHT` as the new `dayStart` positional arg) | exact — shape transfers, re-authored per new signature |
| `src/main/java/com/wfm/service/FteUploadService.java` (`:132`) | service | batch (upload parsing) | `985e365`'s one-line diff (same) | exact — shape transfers |
| `src/test/java/com/wfm/service/TimeslotGeneratorServiceTest.java` / new `TimeslotGeneratorBusinessDateTest.java` | test | transform | `985e365`'s two new/extended test files — reusable for the tiling-refusal half only | role-match (tiling) / no analog (business-date derivation across the anchor) |
| `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` (comparison extension) | test | batch (structural scan) | `63d85a6` (rescue-tag commit) | exact — cherry-pick, then D-25 |
| `src/test/resources/midnight-time-arithmetic.md` (comparison section) | config (parsed resource) | batch | `63d85a6` (rescue-tag commit) | exact — cherry-pick |
| `src/test/java/com/wfm/service/BusinessDateWritePathGuardTest.java` | test | batch (structural scan) | `7d42f23` (rescue-tag commit) | exact — cherry-pick, already satisfies BDAY-08 |
| `src/test/resources/bday-02-write-paths.md` | config (parsed resource) | batch | `7d42f23` (rescue-tag commit) | exact — cherry-pick |
| new BDAY-06 regression suite (test class TBD) | test | event-driven (pure score evaluation, no solve) | composed from `ConstraintPrecedenceObservabilityTest` (`.explain()` usage) + `ShiftEnvelopeGroundTruthTest` (`.update()` usage) + `LiveShapeShiftDeskFixture` (class-load validator idiom) | no single analog — two mechanisms, see below |
| new D-16 flip-registry resource + D-17 predicate list (files TBD) | config (parsed resource) | batch | `midnight-time-arithmetic.md` / `ushf-05-write-paths.md` idiom (parsed `.md`, set-equality enforcement) | role-match |

## Pattern Assignments

### `src/main/resources/db/migration/V53__add_desk_day_start_and_timeslot_business_date.sql` (migration, batch)

**Analog:** `git show 84fdc3f:src/main/resources/db/migration/V53__add_desk_day_start_and_timeslot_business_date.sql` (new file in the cherry-picked commit).

**Full content to copy** (17 lines, verbatim — this is D-09's three-statement, no-nullable-window shape):
```sql
-- Phase 18: Business Day Foundation & Guards (BDAY-01, BDAY-02).
--
-- A desk declares the time its day begins. Every existing desk defaults to 00:00 -- today's
-- behaviour, unchanged -- and Phase 18 gates the accepted value to 00:00 only (D-18); nothing
-- honours a non-default day-start until Phase 19 re-anchors DayWindow. The column and the API
-- land now so Phase 19 has a place to write into rather than a schema change of its own.
ALTER TABLE desk ADD COLUMN day_start TIME NOT NULL DEFAULT '00:00';

-- timeslot.business_date is distinct from its calendar date, populated at generation
-- (TimeslotGeneratorService is the sole writer, per D-24). D-22: the nullable window is
-- deliberately zero-length -- add nullable, backfill every existing row, then SET NOT NULL, all
-- three statements in this one migration, so no reader ever observes an unset row. Backfilling
-- equal to the calendar date is correct today because every desk's day-start is 00:00; Phase 19
-- introduces desks where the two values diverge.
ALTER TABLE timeslot ADD COLUMN business_date DATE;
UPDATE timeslot SET business_date = date;
ALTER TABLE timeslot ALTER COLUMN business_date SET NOT NULL;
```
Per D-24, rewrite the comment's decision-ID references (`D-09`, `D-18`, `D-22`) as reasons stated
inline — the content above already avoids `D-nn` citations in favor of requirement IDs and plain
reasoning, so it can be copied close to verbatim; only the two "D-18"/"D-22" mentions elsewhere in
this phase's other files need the rewrite, not this SQL comment (it has none — verify at copy time).

**Exercise through Postgres, not just H2:** the default suite runs H2 with `flyway.enabled: false`
(`src/test/resources/application-test.yml`), so V53 is never applied there. `MidnightTimeslotPostgresTest`
(`src/test/java/com/wfm/repository/MidnightTimeslotPostgresTest.java`) extends
`com.wfm.support.PostgresBackedTest` and is the existing test that must pass against this migration
(per the project's V39 lesson recorded in `STATE.md`).

---

### `src/main/java/com/wfm/model/Desk.java` (model, CRUD)

**Analog:** current tree (53 lines, 3 configurable fields) + `git show 84fdc3f -- src/main/java/com/wfm/model/Desk.java` for the diff to apply.

**Field + accessor pattern to copy** (verbatim, following `schedulingMode`'s existing shape):
```java
// The desk's business day begins at this time (BDAY-01). Every existing desk defaults to
// 00:00 -- today's behaviour, unchanged. D-18: Phase 18 gates the accepted value to 00:00
// only; DeskService.setDayStart refuses anything else until Phase 19 re-anchors DayWindow.
@Column(name = "day_start", nullable = false)
private LocalTime dayStart = LocalTime.MIDNIGHT;
...
public LocalTime getDayStart() { return dayStart; }
public void setDayStart(LocalTime dayStart) { this.dayStart = dayStart; }
```
Add `import java.time.LocalTime;` alongside the existing `import java.math.BigDecimal;`.

---

### `src/main/java/com/wfm/model/Timeslot.java` (model, CRUD)

**Analog:** current tree (56 lines) + `git show 84fdc3f -- src/main/java/com/wfm/model/Timeslot.java`.

**Field + accessor pattern to copy** verbatim:
```java
// The business day this timeslot belongs to (BDAY-02), distinct from its calendar `date`.
// Sole writer is TimeslotGeneratorService (D-24); equals `date` until Phase 19 introduces
// desks with a non-default day-start. No Java-side default -- V53 backfilled every
// pre-existing row and every new row is written through the generator.
@Column(name = "business_date", nullable = false)
private LocalDate businessDate;
...
public LocalDate getBusinessDate() { return businessDate; }
public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }
```
`Timeslot.java` already imports `java.time.LocalDate` (for `date`), so no new import is needed here.

---

### `src/main/java/com/wfm/dto/DayStartRequest.java` (model/DTO, request-response)

**Analog:** `git show 84fdc3f:src/main/java/com/wfm/dto/DayStartRequest.java` — **apply F-1's
correction**, do not copy verbatim.

**Corrected content (single-field record, no `confirmOverride`):**
```java
package com.wfm.dto;

import java.time.LocalTime;

public record DayStartRequest(
        LocalTime dayStart
) {}
```
The original's javadoc comment about `confirmOverride` "travel[ling] in the same request body as
the value it guards" is dropped entirely — there is nothing left to guard against interception since
D-27's refusal (below) is unconditional.

---

### `src/main/java/com/wfm/controller/DeskController.java` (controller, request-response)

**Analog:** `git show 84fdc3f -- src/main/java/com/wfm/controller/DeskController.java` — **apply F-1's correction**.

**Corrected endpoint** (drop the `confirmOverride` argument):
```java
@PutMapping("/{deskId}/day-start")
public DeskResponse setDayStart(@PathVariable UUID deskId, @RequestBody DayStartRequest request) {
    return toResponse(deskService.setDayStart(deskId, request.dayStart()));
}
```
The `toResponse` helper's diff — appending `desk.getDayStart()` as `DeskResponse`'s 6th constructor
argument — transfers unchanged from the cherry-pick.

---

### `src/main/java/com/wfm/service/DeskService.java` (service, CRUD)

**Analog:** `84fdc3f`'s `setDayStart` addition (gate) composed with `fec8990`'s refusal addition
(made unconditional per F-1). Read both diffs shown above in Salvage Instructions.

**Imports pattern** (existing file, one new import):
```java
import java.time.LocalTime;
```

**Core pattern — the composed method** (gate from `84fdc3f`, refusal from `fec8990` minus the
`confirmOverride` guard, F-1's recommended unconditional-block resolution):
```java
/**
 * Sets a desk's business-day start time (BDAY-01), mirroring {@link #switchSchedulingMode}'s
 * shape: null check, gate, tenant-scoped lookup, equal-value no-op, refusal, then write.
 *
 * <p>D-07: Phase 18 gates the accepted value to {@code 00:00} only -- the column, the API and
 * the UI all land this phase, but nothing honours a non-default day-start until Phase 19
 * re-anchors {@link com.wfm.util.DayWindow}. This is the one validation line Phase 19 deletes.
 *
 * <p>The equal-value early return is deliberate: re-asserting the value a desk already holds
 * is not a transition and must never raise the accepted-schedule refusal below it.
 *
 * <p>D-27: changing a desk's day-start is refused unconditionally while an ACCEPTED schedule
 * exists, naming the blocking schedule. {@code dev} is the live environment with real tenant
 * data and Phil-US holds a live accepted schedule; nothing in Phases 18-22 performs this
 * migration, so there is no caller for a bypass -- an override with no caller is untested
 * surface area (D-27). The refusal reads the ordered finder so its message is deterministic
 * when more than one ACCEPTED schedule exists on the desk.
 */
@Transactional
public Desk setDayStart(UUID deskId, LocalTime dayStart) {
    if (dayStart == null) {
        throw new IllegalArgumentException("Day start is required");
    }
    if (!dayStart.equals(LocalTime.MIDNIGHT)) {
        throw new IllegalArgumentException("Day start other than 00:00 is not yet supported");
    }

    long tenantId = TenantContext.getTenantId();
    Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));

    if (dayStart.equals(desk.getDayStart())) {
        return desk;
    }

    List<Schedule> accepted = scheduleRepository
            .findByTenantIdAndDeskIdAndStatusOrderByCreatedAtDesc(tenantId, deskId, ScheduleStatus.ACCEPTED);
    if (!accepted.isEmpty()) {
        Schedule blocking = accepted.get(0);
        throw new ConflictException("Desk has an accepted schedule (" + blocking.getId()
                + ", " + blocking.getPeriodStartDate() + " to " + blocking.getPeriodEndDate() + ")");
    }

    desk.setDayStart(dayStart);
    return deskRepository.save(desk);
}
```
**Error handling pattern:** plain `IllegalArgumentException` for input validation (matches every
other `DeskService` mutator, e.g. `switchSchedulingMode`), `ConflictException` for the business-rule
refusal (matches `fec8990`'s precedent and the existing `ConflictException` import already used
elsewhere in this file).

**New repository method needed** — `ScheduleRepository.findByTenantIdAndDeskIdAndStatusOrderByCreatedAtDesc`:
```java
// D-19/D-27: ordering is load-bearing -- it is what makes DeskService.setDayStart's
// accepted-schedule refusal deterministic when a desk holds more than one ACCEPTED schedule.
List<Schedule> findByTenantIdAndDeskIdAndStatusOrderByCreatedAtDesc(long tenantId, UUID deskId,
                                            com.wfm.model.ScheduleStatus status);
```
(from `fec8990`'s diff to `src/main/java/com/wfm/repository/ScheduleRepository.java` — cherry-pick
this hunk verbatim even though the rest of `fec8990` is not cherry-picked; it is pure addition with
no `confirmOverride` coupling.)

---

### `frontend/src/api/client.ts` (provider/API client, request-response)

**Analog:** `git show 84fdc3f -- frontend/src/api/client.ts` — **apply F-1's correction**.

**Corrected client method and type** (drop `confirmOverride`):
```ts
setDayStart: (id: string, dayStart: string) =>
  request<Desk>(`/desks/${id}/day-start`, { method: 'PUT', body: JSON.stringify({ dayStart }) }),
```
```ts
export interface Desk { id: string; name: string; description?: string; defaultContractedHoursPerDay: number; schedulingMode: 'SLOT' | 'SHIFT'; dayStart: string }
```
Confirmed against current tree: `export interface Desk { ... }` is a single line at
`frontend/src/api/client.ts:329`; `setSchedulingMode` (the sibling method to follow) sits at line 104.

---

### `frontend/src/pages/DeskManagement.tsx` (component, CRUD/display)

**Analog (two sources, cross-checked):** `git show 84fdc3f -- frontend/src/pages/DeskManagement.tsx`
for the diff shape, **and** the current tree's own `schedulingMode` cell for the actual read-only
markup pattern to follow (CONTEXT.md's discretion note pointed at `schedulingMode`; RESEARCH.md
corrected the assumption that it is a disabled `<input>` — it is a plain `<td>`).

**The exact current-tree cell being extended** (`frontend/src/pages/DeskManagement.tsx:101` and
`:118`, both edit- and display-branch instances):
```tsx
{/* Read-only in both branches — the mode cannot be changed from this page (D-14); a plain-text cell
    keeps the row's column count equal across edit/display so the table does not shift while a row
    is being edited. */}
<td>{desk.schedulingMode === 'SHIFT' ? 'Shift' : 'Slot'}</td>
```

**Pattern to add** (from `84fdc3f`'s diff, confirmed structurally identical to the above): a new
`<th>Day Start</th>` in the header row (`:92`), and a matching `<td>` in both branches. D-26 requires
the cell's rendered TEXT (not merely a code comment) to state the `00:00`-only restriction — `84fdc3f`
as landed does not yet do this (its cell is bare `{desk.dayStart}`); RESEARCH.md's discretion note
recommends wording along these lines:
```tsx
{/* Read-only in both branches, same reasoning as Scheduling Mode above. Day-start editing is not
    yet available -- only 00:00 is supported until overnight scheduling lands. */}
<td>{desk.dayStart} (day-start editing not yet available)</td>
```
This UI copy requirement is UAT-verified only, not test-enforced (D-28) — no `.tsx` test framework
exists in this project (Phase 13 P-11).

---

### `src/test/java/com/wfm/service/DeskServiceDayStartTest.java` (test, CRUD)

**Analog:** `84fdc3f`'s new file + `fec8990`'s extension (both shown in full above under Salvage
Instructions) — **apply F-1's correction throughout**: drop the trailing `false`/`true` argument from
every `deskService.setDayStart(...)` call and every `new DayStartRequest(...)` construction, and
delete the one bypass test (`setDayStart_acceptedScheduleExists_confirmOverrideTrue_succeedsAndPersists`).

**Structure to copy:** `@DataJpaTest` + `@Import({DeskService.class, InMemoryScheduleStore.class, DeskController.class})` + `@ActiveProfiles("test")`, mirroring `DeskServiceSchedulingModeTest`'s shape
(named explicitly in the analog's own javadoc). `saveDesk`/`saveDeskWithDayStart`/`saveAcceptedSchedule`
helper methods transfer with their `confirmOverride` call-site arguments stripped.

---

### `src/test/java/com/wfm/migration/MigrationEntityConsistencyTest.java` (test, batch/schema reconciliation)

**Analog:** `a1c0077` (rescue-tag commit, cherry-pick verbatim). Current tree confirmed at
`DECLARED_TABLES` lines 84-91, exactly 6 entries.

**Exact diff to apply:**
```java
import com.wfm.model.Desk;
import com.wfm.model.Timeslot;
...
private static final Map<String, Class<?>> DECLARED_TABLES = Map.of(
        "shift_template", ShiftTemplate.class,
        "shift_template_break_band", ShiftTemplateBreakBand.class,
        "agent_shift_assignment", AgentShiftAssignment.class,
        "constraint_weights", ConstraintWeights.class,
        "schedule", Schedule.class,
        "agent_usual_shift", AgentUsualShift.class,
        "timeslot", Timeslot.class,
        "desk", Desk.class
);
```
No `COMPATIBLE_SQL_TYPES` change needed — both new columns map through the pre-existing
`LocalTime -> TIME` and `LocalDate -> DATE` entries (verified by `a1c0077`'s own dry run and this
session's re-verification, per D-11's "risk to watch" — no mismatch materialized).

---

### `src/main/java/com/wfm/service/ScheduleService.java` (`acceptSchedule`, service, CRUD/propagating-write)

**Analog:** `2196e40`'s diff to this file (cherry-pick verbatim).

**Core pattern to copy** — inside the live-to-snapshot `Timeslot` copy loop:
```java
snapshot.setDate(live.getDate());
snapshot.setStartTime(live.getStartTime());
snapshot.setEndTime(live.getEndTime());
// BDAY-02: copy the live row's business_date rather than deriving a new one -- this
// is a snapshot of an already-generated row, not a new generation (D-24 reserves
// setting a fresh business_date to TimeslotGeneratorService).
snapshot.setBusinessDate(live.getBusinessDate());
entityManager.persist(snapshot);
```
This is BDAY-08's second (propagating) writer, already named by `7d42f23`'s allowlist — do not
duplicate the derivation logic here; this call must always read `.getBusinessDate()`, never compute.

---

### 6 test fixtures needing `timeslot.setBusinessDate(date)` beside their existing `timeslot.setDate(date)` calls

**Analog:** `2196e40`'s diff (cherry-pick verbatim) to:
`DeskServiceSchedulingModeTest.java`, `ScheduleServiceShiftSnapshotTest.java` (2 call sites),
`ShiftLibraryGenerationCapConfigTest.java`, `ShiftLibraryGenerationServiceTest.java`,
`ShiftLibraryValidationServiceTest.java`.

**Pattern, identical at every site:**
```java
timeslot.setDate(date);
timeslot.setStartTime(start);
timeslot.setEndTime(end);
timeslot.setBusinessDate(date);   // <- new line, always immediately after setDate
```

---

### `src/main/java/com/wfm/service/TimeslotGeneratorService.java` (`generateTimeslots`, service, transform)

**Analog — two halves, different provenance:**

**Half 1 (tiling refusal) — analog is `985e365`'s diff, cherry-pick-equivalent (re-authored, not
cherry-picked, per D-23, but the actual code is expected to transfer almost unchanged):**
```java
@Transactional
public List<Timeslot> generateTimeslots(UUID deskId, LocalDate periodStart, LocalDate periodEnd,
                                        LocalTime dayStart, LocalTime startTime, LocalTime endTime,
                                        int incrementMinutes) {
    if (incrementMinutes != 15 && incrementMinutes != 30 && incrementMinutes != 60) {
        throw new IllegalArgumentException("incrementMinutes must be 15, 30, or 60");
    }
    // D-08: the refusal fires before TenantContext.getTenantId() and before any repository
    // call -- late-but-loud at the generation boundary, same rule DayWindow itself follows.
    // The increment is not desk state; it arrives per call, inferred from the FTE
    // spreadsheet's own columns, so this cannot be validated any earlier than here.
    requireDayStartTiles(dayStart, incrementMinutes);
    ...
}

/**
 * D-08: whether {@code dayStart} is a whole multiple of {@code incrementMinutes} -- the
 * necessary condition for a desk's day-start to tile the generation grid without leaving a
 * fractional slot. Package-private static, the same shape as {@link #isDesired}, so a plain
 * unit test can call it directly with an arbitrary day-start.
 *
 * @throws IllegalArgumentException naming the day-start, the increment, and why the two
 *         cannot tile a day, or when {@code dayStart} is null.
 */
static void requireDayStartTiles(LocalTime dayStart, int incrementMinutes) {
    if (dayStart == null) {
        throw new IllegalArgumentException(
                "Day start is required to check tiling against the generation increment");
    }
    if (DayWindow.startMinute(dayStart) % incrementMinutes != 0) {
        throw new IllegalArgumentException(
                "Desk day-start " + dayStart + " is not a whole multiple of the "
                        + incrementMinutes + "-minute generation increment and cannot tile a day");
    }
}
```

**Half 2 (anchored business-date derivation + two-calendar-date walk) — NO ANALOG, this is genuinely
new work (D-19/D-20/D-22).** The current generation loop (verified this session,
`TimeslotGeneratorService.java:130-147`):
```java
int firstMinute = DayWindow.startMinute(startTime);
int lastMinute = DayWindow.endMinute(endTime);
for (LocalDate date = periodStart; !date.isAfter(periodEnd); date = date.plusDays(1)) {
    for (int minute = firstMinute; minute < lastMinute; minute += incrementMinutes) {
        LocalTime slotStart = DayWindow.toLocalTime(minute);
        LocalTime slotEnd = DayWindow.toLocalTime(minute + incrementMinutes);
        String key = slotKey(date, slotStart, slotEnd);
        if (!survivingByKey.containsKey(key)) {
            Timeslot ts = new Timeslot();
            ts.setTenantId(tenantId);
            ts.setDeskId(deskId);
            ts.setDate(date);
            ts.setStartTime(slotStart);
            ts.setEndTime(slotEnd);
            toCreate.add(ts);
        }
    }
}
```
must have its `ts.setDate(date)` line joined by a **derived**, not literal, business-date write —
`ts.setBusinessDate(<D-20's new DayWindow function>(dayStart, date, slotStart))` — where D-20's
function takes a day-start, a calendar date and a time and returns the business date. See Pitfall 3
in RESEARCH.md: do not write `ts.setBusinessDate(date)` here even though that is what `2196e40`
(cherry-picked) and `985e365` (not cherry-picked) both do — both were correct only because every
desk's day-start was `00:00` at the time they were authored. **Byte-identical output at `00:00`, not
byte-identical source line.** Design the new `DayWindow` function's signature and the walk-across-the-
anchor logic as new work; `DayWindow.toLocalTime` throws outside `[0, 1440]` by design
(`DayWindow.java:121-125`), so the walk must map minute-of-day across the anchor into
`(calendarDate, LocalTime)` pairs rather than calling the existing midnight-implicit `toLocalTime`
naively for a 21:00 anchor.

**Error handling pattern (both halves):** plain `IllegalArgumentException`, matching every other
guard in this file (`incrementMinutes` check two lines above, `Time range must be positive` two
lines below).

---

### `src/main/java/com/wfm/controller/TimeslotController.java` and `src/main/java/com/wfm/service/FteUploadService.java:132` (controllers/service, request-response / batch)

**Analog:** `985e365`'s one-line diffs to both call sites (re-authored per D-23, but this specific
shape is expected to transfer unchanged since D-07's gate makes any value but `LocalTime.MIDNIGHT`
unreachable this phase):
```java
// TimeslotController.java — inside the generateTimeslots endpoint handler
deskId,
request.periodStartDate(),
request.periodEndDate(),
LocalTime.MIDNIGHT,   // <- new 4th positional arg
request.startTime(),
request.endTime(),
request.incrementMinutes()
```
```java
// FteUploadService.java:132
List<Timeslot> timeslots = timeslotGeneratorService.generateTimeslots(
        deskId, minDate, maxDate, LocalTime.MIDNIGHT, startTime, endTime, incrementMinutes);
```
Both need `import java.time.LocalTime;` if not already present (`TimeslotController.java` does not
currently import it; `FteUploadService.java` already does, per its own `DayWindow.durationMinutes`
call at `:122-123`).

---

### `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` + `src/test/resources/midnight-time-arithmetic.md` (test + config, batch/structural scan)

**Analog:** `63d85a6` (rescue-tag commit, cherry-pick verbatim), then apply D-25.

**Core pattern — the second token family, gated by a receiver-name heuristic** (already shown in
full above; key excerpts):
```java
private static final List<String> COMPARISON_TOKENS = List.of(".isAfter(", ".isBefore(", ".compareTo(");

@Test
void rawTimeComparisonsInProductionCode_matchesTheComparisonAllowlistExactly() throws IOException {
    Set<String> derived = scanProductionSources(MidnightTimeArithmeticGuardTest::isRawComparison);
    Set<String> allowlist = parseComparisonAllowlist();
    ...
    assertThat(derived).containsExactlyInAnyOrderElementsOf(allowlist);
}
```
Receiver-name heuristic (`isRawComparison`/`looksLikeSchedulingTime`): ends-with `time`/`start`/`end`
or begins-with `envelope`/`band`/`break`/`slot`, case-insensitive, applied to the identifier
immediately left of the token. Ten pre-argued allowlist entries in
`midnight-time-arithmetic.md`'s "Permitted raw time comparisons" section, each with a "Why each
comparison is permitted" justification — copy this section verbatim, it is current-tree-verified.

**D-25's new red-proof (no precedent — new work):** currently `theScanDetectsAFreshOccurrence` and
`theComparisonScanDetectsAFreshOccurrence` (both present in `63d85a6`) test the matcher predicate
against synthetic strings only. D-25 requires extracting `SOURCE_ROOT` (currently
`private static final Path SOURCE_ROOT = Path.of("src", "main", "java")`) into a parameter, then
adding a test that points the full scan pipeline (walk → strip-comment → match → allowlist-compare)
at a new test-resources fixture directory holding one synthetic offending `.java` file, asserting the
set-equality assertion **fails**. Check `find src/test/resources -type d` first — RESEARCH.md notes
no existing fixture directory was confirmed for this purpose this session.

---

### `src/test/java/com/wfm/service/BusinessDateWritePathGuardTest.java` + `src/test/resources/bday-02-write-paths.md` (test + config, batch/structural scan)

**Analog:** `7d42f23` (rescue-tag commit, cherry-pick verbatim — already satisfies BDAY-08 exactly,
no modification needed). Full resource content and representative test excerpts shown above under
"the BDAY-02 analog"; see Salvage Instructions table. The DERIVING allowlist names
`com.wfm.service.TimeslotGeneratorService`; the PROPAGATING allowlist names
`com.wfm.service.ScheduleService` — both remain correct after this phase's generator re-authoring
(Half 2 above), since the call site is still exactly one line inside `TimeslotGeneratorService`, only
its right-hand-side expression changes.

---

### BDAY-06 constructed regression suite (new test class, event-driven/pure-evaluation — no single analog)

**No exact precedent exists in this codebase for D-14's "pin every planning variable, never call
`solve()`" shape** — confirmed this session: both `ConstraintPrecedenceObservabilityTest` and
`ShiftEnvelopeGroundTruthTest` call `solver.solve(unsolved)` to reach their starting fixture before
evaluating mutations of it. Two separate, transferable mechanisms compose instead:

**Mechanism 1 — pure score evaluation, no search** (from `ShiftEnvelopeGroundTruthTest.scoreAgreesOnBrokenSolution`, `:205-222`, and `ConstraintPrecedenceObservabilityTest`'s `.explain()` usage, `:84-100`):
```java
SolverFactory<Schedule> factory = SolverFactory.create(new SolverConfig()
        .withSolutionClass(Schedule.class)
        .withEntityClasses(AgentShiftAssignment.class, AgentAssignment.class)
        .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                .withConstraintProviderClass(ScheduleConstraintProvider.class)));
SolutionManager<Schedule, HardSoftScore> solutionManager = SolutionManager.create(factory);

HardSoftScore score = solutionManager.update(pinned);      // pure evaluation, no search
var explanation = solutionManager.explain(pinned);
Map<String, ConstraintMatchTotal<HardSoftScore>> totals = explanation.getConstraintMatchTotalMap();
// Match on total.getConstraintName() (e.g. "Shift work contiguity"), NOT the raw map key --
// the key carries a constraintPackage prefix. ConstraintPrecedenceObservabilityTest's own
// comment on this exact gotcha is at :91-93.
```
**What must NOT transfer:** neither precedent's `solveCleanFixture()`/`solve()` helper. BDAY-06's
`Schedule` is built entirely by hand — every `AgentShiftAssignment` and `AgentAssignment` planning
variable set directly by a stated, reproducible rule (Claude's Discretion), never by the solver.
Each scenario must argue in a comment why its pinned solution is one the solver could actually reach.

**Mechanism 2 — class-load non-vacuity validator** (from `LiveShapeShiftDeskFixture.validateTemplateSpecs()`, `:136-173`):
```java
static {
    validateTemplateSpecs();
}

private static void validateTemplateSpecs() {
    // walks the fixture's own constant/constructed data, throws IllegalStateException if a
    // named structural invariant does not hold -- fails at class-load, not first-test-run
    ...
}
```
For BDAY-06, adapt this shape to D-17's shared structural predicates — "a slot ending `00:00`", "a
23:00-00:00 slot", "a band flush to an envelope edge", "a span crossing the anchor" — each written as
a standalone method callable against any `Schedule`'s facts (not scenario-labeled), asserted to fire
at least once across the constructed fixtures in a `static { }` block that throws if any predicate
never fires. **These predicates must be reusable** — Phase 20 is expected to point the same
implementation at a live desk's facts (D-17), so do not couple the predicate methods to BDAY-06's own
fixture-construction helpers.

**D-16's separate registry (no precedent to extend, but a proven idiom to follow):** the three
scenarios whose property cannot exist today (shift crossing midnight, PTO on start-vs-end day,
starting-weekday-only contracted hours) assert TODAY's actual behavior and are named in a parsed
`.md` registry, validated for exact two-directional set-equality against the assertions — this is the
same `parseFencedBlock` + set-equality idiom already proven three times
(`midnight-time-arithmetic.md`/`MidnightTimeArithmeticGuardTest`,
`ushf-05-write-paths.md`/`UsualShiftWritePathGuardTest` and `bday-02-write-paths.md`/
`BusinessDateWritePathGuardTest`, `ScheduleConstraintClassificationTest`). Whether D-16's registry
and D-17's predicate list share one resource file is Claude's Discretion (D-01's one-guard-one-
resource rule as the deciding constraint).

**D-15's plain unit tests** (a 23:00-00:00 slot, an envelope flush to end-of-day, contracted-hours-
starting-weekday-only) need no `Schedule`, no Timefold import, no `SolutionManager` — write them as
ordinary `DayWindowTest`-style JUnit methods (see `src/test/java/com/wfm/util/DayWindowTest.java`,
which already has `crossingMidnightIsRejected` as its own closest in-class precedent) or plain
`TimeslotGeneratorServiceTest`-style methods, whichever the property under test belongs to.

---

## Shared Patterns

### Structural guard idiom: parsed `.md` allowlist + set-equality scan + red-proof

**Source:** `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java`,
`src/test/java/com/wfm/service/BusinessDateWritePathGuardTest.java` (cherry-picked, `7d42f23`),
`src/test/java/com/wfm/service/UsualShiftWritePathGuardTest.java`.
**Apply to:** every guard-test file this phase touches (comparison-operator extension, BDAY-08's
guard, D-16's flip registry, D-25's red-proof).
```java
private static Set<String> scanCallSites(...) throws IOException { /* comment-stripped source walk */ }
private static Set<String> parseAllowlist(String heading) throws IOException {
    return requireNonEmpty(parseFencedBlock(readResource(), heading), heading);
}
assertThat(derived).containsExactlyInAnyOrderElementsOf(allowlist); // NEVER isSubsetOf/containsAnyOf
```
**The rule every one of these tests' own javadoc states:** widening from `containsExactlyInAnyOrderElementsOf` to any subset/containment check turns the guard into decoration. This is the single most-repeated instruction across all five cherry-picked commits' comments.

### Tenant-scoped service mutation shape

**Source:** `src/main/java/com/wfm/service/DeskService.java` — existing `switchSchedulingMode`
method (the explicit precedent `84fdc3f`'s own javadoc names for `setDayStart`).
**Apply to:** `DeskService.setDayStart`.
```java
long tenantId = TenantContext.getTenantId();
Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Desk", deskId));
if (<new value>.equals(<current value>)) {
    return desk;   // equal-value no-op, precedes any business-rule refusal
}
desk.set<Field>(<new value>);
return deskRepository.save(desk);
```

### Fail loudly at the boundary, never wrap silently

**Source:** `DayWindow.durationMinutes` (throws on a backward interval, `DayWindow.java:68-81`),
`DayWindow.toLocalTime` (throws outside `[0,1440]`, `:121-125`).
**Apply to:** D-08's `requireDayStartTiles` refusal, D-20's new anchored `DayWindow` function (should
throw rather than silently wrap when given inputs outside its supported range, matching the class's
existing discipline).

### Requirement IDs in comments, never phase numbers or decision-doc IDs

**Source:** D-24, directly — `bday-02-write-paths.md`'s existing comment already follows this
("Phase 21 (SOLV-01)" is itself flagged as needing correction to whatever phase number is current).
**Apply to:** every new/modified comment in this phase. Cite `BDAY-01`, `SOLV-01`, `OVNT-01` etc.,
never `D-19`/`D-27` (those decision IDs exist only in `18-CONTEXT.md`, which downstream phases will
not read) and never a bare phase number (renumbered twice already across v1.4/v1.5).

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `src/main/java/com/wfm/util/DayWindow.java` — new date-aware anchor function(s) (D-19/D-20) | utility | transform | First `LocalDate`-aware function in a class that currently imports only `java.time.LocalTime`; the anchor-crossing arithmetic (walking minutes across an anchor to derive a business date, mapping to two calendar dates) has no existing implementation anywhere in this codebase to extend. Design against the class's own stated discipline (fail loudly outside supported range; `00:00`-by-position javadoc statement at `:30-36` must be preserved) rather than an analog. |
| `TimeslotGeneratorService`'s anchored generation walk (two-calendar-date minute mapping) | service | transform | Same reason — `985e365` only ever wrote `business_date = date` unconditionally; no commit anywhere, salvaged or otherwise, walks minutes across a non-midnight anchor into two calendar dates. Genuinely new work per RESEARCH.md's own "no rescue-tag precedent" finding. |
| BDAY-06's pinned-`Schedule`-construction-by-hand mechanism (D-14) | test | event-driven (pure evaluation) | Confirmed this session: the two nearest fixtures in this codebase (`ConstraintPrecedenceObservabilityTest`, `ShiftEnvelopeGroundTruthTest`) both call `solver.solve()` first. D-14 forbids that. Use RESEARCH.md's "Code Examples" composed shape (Mechanism 1 above) instead of a copyable file. |
| D-25's pipeline-level red-proof fixture directory | test (fixture) | batch | No existing test-resources directory holds a synthetic offending file for a guard's full walk→strip→match→compare pipeline; `63d85a6`'s own red-proofs test only the matcher predicate. Confirm no such directory exists (`find src/test/resources -type d`) before creating one. |

## Metadata

**Analog search scope:** `src/main/java/com/wfm/{model,dto,controller,service,util,repository}`,
`src/test/java/com/wfm/{service,migration,solver,util,repository}`, `src/test/resources/`,
`frontend/src/{pages,api}`, `src/main/resources/db/migration/`, and the full commit history of
`rescue/phase-18-unwind-20260930` (`84fdc3f`, `a1c0077`, `2196e40`, `7d42f23`, `63d85a6`, `985e365`,
`fec8990`, cross-checked against current HEAD).
**Files scanned:** 20 target files/areas; 8 rescue-tag commits read via `git show`; 9 current-tree
files read in full or in targeted regions to verify commits still apply cleanly.
**Pattern extraction date:** 2026-09-30
