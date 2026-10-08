# Phase 19: DayWindow Re-anchoring - Pattern Map

**Mapped:** 2026-09-30
**Files analyzed:** 27 files referencing `DayWindow` (per RESEARCH.md's call-site inventory) + 3
plumbing files (`Schedule.java`, `ScheduleConfig.java`, `SolverService.java`) + 2 structural-guard
files (`DayWindowTest.java`, `midnight-time-arithmetic.md`)
**Analogs found:** RESEARCH.md already enumerates every call site by file and method (§"DayWindow
Call-Site Inventory") — this document does not repeat that inventory. Instead it groups every
DISTINCT SHAPE of call site found across those files and gives each shape one concrete analog, so
an executor can classify any of the 83 production occurrences by shape rather than re-deriving a
transformation per file.

**How to use this document:** for a given call site, find its shape below (A through H), read the
analog excerpt, and apply the shown transformation. `DayWindow.java` and `DayWindowTest.java`
themselves are the primary "analog" for D-04's class-shape change — no other bound-instance
conversion exists anywhere else in this codebase (see "No Analog Found").

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/java/com/wfm/util/DayWindow.java` | utility (static→bound-instance) | transform | *(itself — no prior conversion of this shape exists in this codebase; see "No Analog Found")* | no-analog |
| `src/test/java/com/wfm/util/DayWindowTest.java` | test | transform | itself, re-pointed; `DeprecationIsLive` nested class (existing, Phase 18) is the direct analog for D-03's inversion | exact |
| `src/main/java/com/wfm/service/TimeslotGeneratorService.java` | service | transform (CRUD-adjacent: generation) | itself — already anchor-aware, shape B below | exact (self-analog) |
| `src/main/java/com/wfm/service/ShiftTemplateService.java` | service | request-response (validation) | itself — holds D-11 refusal, shape C below | exact (self-analog) |
| `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` | service | transform (generation) | `TimeslotGeneratorService.java` (shape B) | role-match |
| `src/main/java/com/wfm/service/ScheduleExportService.java` | service | transform (export) | `ScheduleOutputService.java` (shape D) | role-match |
| `src/main/java/com/wfm/service/ScheduleOutputService.java` | service | transform (report) | itself — `Schedule.getScheduleConfig()` precedent, shape D below | exact (self-analog) |
| `src/main/java/com/wfm/service/StaffingRequirementService.java` | service | CRUD/transform | `ShiftTemplateService.java` (shape C) | role-match |
| `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` | service | request-response (validation) | `ShiftTemplateService.java` (shape C) | role-match |
| `src/main/java/com/wfm/service/FteUploadService.java` | service | file-I/O (upload parse) | `TimeslotGeneratorService.java` (shape B) | role-match |
| `src/main/java/com/wfm/service/SolverService.java` | service | batch (solve orchestration) | itself — D-08's `buildSchedule` anchor-fill site, shape G below | exact (self-analog) |
| `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` | solver constraint provider | event-driven (constraint stream) | itself — `shiftEnvelopeCompliance` (`.join(ScheduleConfig.class)`), shape E below | exact (self-analog) |
| `src/main/java/com/wfm/util/FteSpreadsheetGenerator.java` | utility (report generation, standalone `main`) | file-I/O | itself — D-07 allowlist candidate, shape H below | exact (self-analog) |
| `src/main/java/com/wfm/model/ShiftBandPair.java` | model (record, problem fact) | transform | `ShiftTemplateBreakBand.java` (shape F — external-context-as-parameter) | exact (sibling model class) |
| `src/main/java/com/wfm/model/ShiftTemplateBreakBand.java` | model (JPA entity) | transform | `ShiftBandPair.java` (shape F) | exact (sibling model class) |
| `src/main/java/com/wfm/model/ShiftTemplate.java` | model (JPA entity) | transform | `ShiftTemplateBreakBand.java` (shape F) | exact (sibling model class) |
| `src/main/java/com/wfm/model/Schedule.java` | model (JPA entity / problem fact host) | CRUD (field add) | `Schedule.setSchedulingMode`/`schedulingMode` field pair (already on the class) | exact (self-analog) |
| `src/main/java/com/wfm/model/ScheduleConfig.java` | model (record, `@ProblemFactProperty`) | transform | itself — existing 13→delegating-constructor pattern, shape G below | exact (self-analog) |
| `src/main/java/com/wfm/service/DeskService.java` | service | CRUD (javadoc only, D-02) | itself — `setDayStart` javadoc | exact (self-analog) |
| `src/main/java/com/wfm/controller/ShiftTemplateController.java` | controller | request-response | *(no analog — controller has no `Desk`/anchor in scope; see "No Analog Found")* | no-analog |
| `src/test/resources/midnight-time-arithmetic.md` | test fixture (structural allowlist) | transform | itself — two existing sections, shape K below | exact (self-analog) |
| `src/test/java/com/wfm/service/MidnightTimeArithmeticGuardTest.java` | test (structural guard) | request-response (build-time scan) | unchanged this phase except scanning the new third section | exact (self-analog) |

## Pattern Assignments (grouped by call-site shape, not by file)

### Shape A — The bound-instance class shape itself (D-04, `DayWindow.java`)

**No analog exists elsewhere in this codebase.** Searched `src/main/java/com/wfm/util/` and
`src/main/java/com/wfm/model/` for any class with a private constructor + static factory method
returning a bound instance that wraps formerly-static behavior (the "static utility →
bound-instance" conversion shape). None found: every other utility class in `com.wfm.util`
(`CursorPagination`, `BigDecimals`, `AgentNameSplitter`, `EnrichedColumnLayout`,
`FormulaInjectionSanitizer`, `FteSpreadsheetGenerator`) stays static-only with a `private
NoArgs() {}` constructor that is never called, and every `com.wfm.model` class is either a JPA
entity or an immutable record, never a "bind once, call many" wrapper. `grep -rn -i "oracle"
src/main/java src/test/java` also returned zero hits, confirming D-13's frozen-oracle test pattern
is likewise new to this codebase, not a re-use.

**Use `DayWindow.java`'s own current structure as the template**, per RESEARCH.md's Pattern 1
(`src/main/java/com/wfm/util/DayWindow.java:50-295`, read in full):

```java
// Current shape (src/main/java/com/wfm/util/DayWindow.java:50-56, 64-68):
public final class DayWindow {
    public static final int MINUTES_PER_DAY = 1440;
    private DayWindow() {}

    @Deprecated
    public static int startMinute(LocalTime start) {
        requireNonNull(start, "start");
        return start.getHour() * 60 + start.getMinute();
    }
    // ...
}
```

Target shape (D-04/D-06, not yet written — the class's own five anchored primitives at lines
210-289 are the arithmetic that survives unchanged; only the wrapping shape is new):

```java
public final class DayWindow {
    private final LocalTime dayStart;
    private DayWindow(LocalTime dayStart) { this.dayStart = dayStart; }

    public static DayWindow anchoredAt(LocalTime dayStart) {
        requireNonNull(dayStart, "dayStart");
        return new DayWindow(dayStart);
    }

    // instance method, was public static startMinute(LocalTime) — now PRIVATE (D-06):
    private int startMinute(LocalTime t) { return startMinuteFromDayStart(dayStart, t); }

    // PUBLIC instance method with today's exact signature (D-04):
    public int durationMinutes(LocalTime start, LocalTime end) { /* re-derived, no throw (crit. 2) */ }

    // UNCHANGED — stays public static (Phase 18, lines 210-289):
    public static int startMinuteFromDayStart(LocalTime dayStart, LocalTime start) { /* unchanged */ }
}
```

**Class-name guard exemption precedent** — `MidnightTimeArithmeticGuardTest` exempts `DayWindow` by
class name, not call shape, so instance methods stay exempt automatically; no guard change is
needed for D-04 itself (only D-07's third allowlist section is a guard change — see Shape K).

---

### Shape B — Service already takes `dayStart` as a bare `LocalTime` parameter, calls the FIVE anchored primitives repeatedly

**Analog:** `src/main/java/com/wfm/service/TimeslotGeneratorService.java` (25 references — the
largest single file, already anchor-aware since Phase 18/D-19/D-22).

**Current shape** (lines 139-146, 199-206, 233-253 — representative):
```java
// src/main/java/com/wfm/service/TimeslotGeneratorService.java:139-146
int firstOffset = DayWindow.startMinuteFromDayStart(dayStart, startTime);
int lastOffset = DayWindow.endMinuteFromDayStart(dayStart, endTime);
for (int offset = firstOffset; offset < lastOffset; offset += incrementMinutes) {
    LocalDate slotDate = DayWindow.calendarDateAtDayStartOffset(dayStart, businessDate, offset);
    LocalTime slotStart = DayWindow.timeAtDayStartOffset(dayStart, offset);
    LocalTime slotEnd = DayWindow.timeAtDayStartOffset(dayStart, offset + incrementMinutes);
```
```java
// src/main/java/com/wfm/service/TimeslotGeneratorService.java:199-206 (a private static helper)
static void requireDayStartTiles(LocalTime dayStart, int incrementMinutes) {
    if (dayStart == null) { ... }
    if (DayWindow.startMinute(dayStart) % incrementMinutes != 0) { ... }   // <- midnight-implicit form
```

**This is D-04's motivating example verbatim** ("the anchor binds once per scope instead of
repeating 25 times in `TimeslotGeneratorService`"). Because this file ALREADY threads `dayStart` as
a parameter through every method (`generateTimeslots`, `isDesired`, `timeslotsMatch`,
`requireDayStartTiles`, ...), the mechanical transformation at each call site is:

1. At the top of each method that currently receives `LocalTime dayStart`, bind once:
   `DayWindow window = DayWindow.anchoredAt(dayStart);`
2. Replace every `DayWindow.startMinuteFromDayStart(dayStart, x)` → `window.startMinute(x)` (per
   Claude's Discretion naming — shed the `FromDayStart` suffix), every
   `DayWindow.calendarDateAtDayStartOffset(dayStart, d, m)` → `window.calendarDateAtOffset(d, m)`
   (or whatever final name D-04's discretion settles), etc.
3. Midnight-implicit calls inside the SAME file that still use the nine deprecated statics (e.g.
   `requireDayStartTiles`'s `DayWindow.startMinute(dayStart)` at line 204) become
   `window.startMinute(dayStart)` — note this one call's argument IS the anchor itself, not a
   scheduling time; it is checking the anchor's own divisibility, which stays correct through the
   bound instance.

**Apply this same shape to:** `ShiftLibraryGenerationService.java` (17 refs), `FteUploadService.java`
(4 refs, mixes anchored and midnight-implicit calls) — both already receive or can trivially receive
`dayStart`/`desk` as a method parameter from their own callers.

---

### Shape C — Service loads `Desk` (or holds `deskId`), calls ONLY the nine midnight-implicit statics directly, no existing `dayStart` parameter threading

**Analog:** `src/main/java/com/wfm/service/ShiftTemplateService.java` — holds the D-11 save-path
refusal.

**Current shape** (`ShiftTemplateService.java:220-233`, already quoted in RESEARCH.md and verified
this session):
```java
private void validate(long tenantId, UUID deskId, ShiftTemplateRequest request, UUID excludeId) {
    if (request.name() == null || request.name().isBlank()) {
        throw new IllegalArgumentException("Shift template name is required");
    }
    // ...
    if (request.startTime() == null || request.endTime() == null
            || !DayWindow.isForwardWithinDay(request.startTime(), request.endTime())) {
        throw new IllegalArgumentException("Shift template end time must be after its start time");
    }
    long envelopeMinutes = DayWindow.durationMinutes(request.startTime(), request.endTime());
```

**Target shape:** this method already holds `deskId` and "calls `validateGridAlignment(deskId, …)`"
(per D-11) — i.e. it already has a proven path to load the `Desk` mid-method. The transformation
binds the window once, at the point the desk is loaded, then routes both calls through it:
```java
Desk desk = deskRepository.findByIdAndTenantId(deskId, tenantId).orElseThrow(...); // pattern already used elsewhere in this method for validateGridAlignment
DayWindow window = DayWindow.anchoredAt(desk.getDayStart());
if (request.startTime() == null || request.endTime() == null
        || !window.isForwardWithinDay(request.startTime(), request.endTime())) {
    throw new IllegalArgumentException("Shift template end time must be after its start time");
}
long envelopeMinutes = window.durationMinutes(request.startTime(), request.endTime());
```

**Apply this same shape to:** `StaffingRequirementService.java` (4 refs), `ShiftLibraryValidationService.java`
(4 refs) — both are request-response validators that already resolve a `deskId`/`Desk` per call.

---

### Shape D — Service holds a `Schedule`, no `Desk` reference; reaches the anchor through `Schedule.getScheduleConfig()`

**Analog:** `src/main/java/com/wfm/service/ScheduleOutputService.java` — the exact file D-08 names
("`ScheduleOutputService` reaches it the same way: it holds no `Desk` reference but does receive
the `Schedule`, so `getScheduleConfig()` is its path").

**Existing precedent for reading a `ScheduleConfig` scalar this way — already in this file today**
(`ScheduleOutputService.java:468-472`):
```java
// Read the SAME value the solver read (Schedule.getScheduleConfig(), which falls back to ...
int toleranceMinutes = schedule.getScheduleConfig().consistencyToleranceMinutes();
```

**Current midnight-implicit call sites in the same file** (`ScheduleOutputService.java:813-814,
932-934, 968, 971`):
```java
LocalTime breakStart = DayWindow.plusWithinDay(descriptor.startTime(), offset);
LocalTime breakEnd = DayWindow.plusWithinDay(breakStart, duration);
// ...
if (DayWindow.endMinute(currentEnd) < DayWindow.startMinute(nextStart)) {
    int gapMinutes = DayWindow.startMinute(nextStart) - DayWindow.endMinute(currentEnd);
```

**Target shape** — bind once per public method using the exact same accessor style as the
`toleranceMinutes` precedent:
```java
DayWindow window = DayWindow.anchoredAt(schedule.getScheduleConfig().dayStart());   // NEW, D-08/D-10
LocalTime breakStart = window.plusWithinDay(descriptor.startTime(), offset);
LocalTime breakEnd = window.plusWithinDay(breakStart, duration);
```

**Apply this same shape to:** `ScheduleExportService.java` (7 refs — "the file whose raw `isAfter()`
produced the demonstrated defect `5ddd8dc`," per RESEARCH.md; it also receives a `Schedule`).

---

### Shape E — Solver constraint-stream lambda, `ScheduleConfig` already joined into the tuple via `.join(ScheduleConfig.class)`

**Analog:** `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java`, `shiftEnvelopeCompliance`
(lines 482-491) — the constraint that calls `.covers(a.getTimeslot())`, one of D-09's own
model-class call sites, and confirmed this session to already carry `cfg` in its tuple:

```java
// src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:482-491
Constraint shiftEnvelopeCompliance(ConstraintFactory factory) {
    return factory.forEachIncludingUnassigned(AgentShiftAssignment.class)
            .join(ScheduleConfig.class)
            .filter((sa, cfg) -> cfg.schedulingMode() == SchedulingMode.SHIFT)
            .join(AgentAssignment.class,
                    equal((sa, cfg) -> sa.getAgent().getId(), a -> a.getAgent().getId()),
                    equal((sa, cfg) -> sa.getDate(), a -> a.getTimeslot().getDate()))
            .filter((sa, cfg, a) -> sa.getShiftBandPair() == null
                    || !sa.getShiftBandPair().covers(a.getTimeslot()))
            .penalizeConfigurable()
            .asConstraint(SHIFT_ENVELOPE_COMPLIANCE_CONSTRAINT_NAME);
}
```

**Target shape** — `cfg` is already a tuple member at the final `.filter`, so once D-08/D-10 adds
`ScheduleConfig.dayStart()`, building the window inline is a one-line addition, and the
`ShiftBandPair.covers` call gains the D-09 parameter directly from it:
```java
.filter((sa, cfg, a) -> {
    DayWindow window = DayWindow.anchoredAt(cfg.dayStart());
    return sa.getShiftBandPair() == null || !sa.getShiftBandPair().covers(a.getTimeslot(), window);
})
```

**Known DIFFERENT shape in the same file — flag for plan-time attention, not covered by this
analog:** some constraints bring `ScheduleConfig` into scope only via `.ifExists(ScheduleConfig.class,
filtering(...))` for a mode-gate check, WITHOUT joining it into the tuple — e.g.
`breakBlockedWindow` (lines 365-399):
```java
// src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:372-374, 396-399
.ifExists(ScheduleConfig.class,
        filtering((daId, date, assignments, dayConfig, cfg) ->
                cfg.schedulingMode() != SchedulingMode.SHIFT))
.filter((daId, date, assignments, dayConfig) -> {   // <- cfg is NOT in this tuple
    // ...
    LocalTime blockedStartEnd = DayWindow.plusWithinDay(shiftStart, (int) blockedMinutes);
    int blockedEndStartMinute = DayWindow.endMinute(shiftEnd) - (int) blockedMinutes;
    return DayWindow.startMinute(breakStart) < DayWindow.startMinute(blockedStartEnd)
            || DayWindow.endMinute(breakEnd) > blockedEndStartMinute;
})
```
`ifExists` (unlike `.join`) never adds its class to the output tuple, so `cfg.dayStart()` is not
reachable at this `.filter` without a structural change (e.g. switching this join to `.join` plus a
correlating key, or threading `dayStart` through `AgentDayConfig` another way). This is a genuine
plan-time decision `19-CONTEXT.md`/RESEARCH.md does not resolve — flag every constraint using
`.ifExists(ScheduleConfig.class, ...)` (not `.join`) for explicit handling rather than assuming
Shape E's one-liner applies uniformly across all 23 `ScheduleConstraintProvider` references.

---

### Shape F — Model class method already takes external context as a PARAMETER (never a constructor field) — the D-09 precedent

**This is not an external analog — it is the exact shape the three D-09 classes already use for
every OTHER piece of context they need**, which is why D-09 adding a `DayWindow` parameter is a
same-shape extension, not a new pattern:

```java
// src/main/java/com/wfm/model/ShiftBandPair.java:36-41 — already takes Timeslot as a parameter:
public boolean covers(Timeslot ts) {
    return covers(template.getStartTime(), template.getEndTime(),
            band == null ? null : band.getOffsetMinutes(),
            band == null ? null : band.getDurationMinutes(),
            ts.getStartTime(), ts.getEndTime());
}
```
```java
// src/main/java/com/wfm/model/ShiftTemplateBreakBand.java:65-77 — already takes ShiftTemplate as a parameter:
@Transient
public LocalTime getBreakStartTime(ShiftTemplate template) {
    return template.getStartTime() == null
            ? null
            : DayWindow.plusWithinDay(template.getStartTime(), offsetMinutes);
}
@Transient
public LocalTime getBreakEndTime(ShiftTemplate template) {
    LocalTime breakStart = getBreakStartTime(template);
    return breakStart == null ? null : DayWindow.plusWithinDay(breakStart, durationMinutes);
}
```

**Target shape** (D-09) — add `DayWindow window` as one more such parameter, identical calling
convention:
```java
public boolean covers(Timeslot ts, DayWindow window) { ... }
public LocalTime getBreakStartTime(ShiftTemplate template, DayWindow window) { ... }
public LocalTime getBreakEndTime(ShiftTemplate template, DayWindow window) { ... }
```

**Callers that already have (or can trivially obtain) a `DayWindow` to pass in** — confirmed this
session via `grep -rn "\.covers(\|getBreakStartTime(\|getBreakEndTime("`:
- `ScheduleConstraintProvider.shiftEnvelopeCompliance` (Shape E above — `cfg` in tuple)
- `ScheduleConstraintProvider.java:1342-1343` (inside a private helper already reached from a
  constraint with `ScheduleConfig` available upstream — verify per-call)
- `SolverService.java:1645,1704,1896` (`pair.covers(ts)`) — `SolverService.buildSchedule` already
  loads `Desk` (Shape G), so these later solve-time call sites need the SAME `Desk`/window carried
  forward through whatever state `SolverService` already threads into its solve loop
- `ScheduleOutputService.java:837,873` (static `ShiftBandPair.covers(...)` 6-arg form) — Shape D
  applies; `schedule.getScheduleConfig().dayStart()` is reachable
- `ScheduleEnvelopeRepairService.java:144,288`, `ShiftStartMixTargetService.java:303` — role-match
  to Shape C/D; each needs verification of whether it holds `Desk` or `Schedule`
- `ShiftTemplateController.java:77-78` (`band.getBreakStartTime(template)`,
  `band.getBreakEndTime(template)`) — **no analog; see "No Analog Found," controller has no anchor
  in scope**
- `ShiftLibraryValidationService.java:246-247,287,289` — Shape C applies (service with `deskId`)

**`ShiftTemplate`'s net-hours calculation** (`ShiftTemplate.java:125-135`, quoted in full in
RESEARCH.md) is the third D-09 site and follows the identical shape — `getNetHours(int
breakDurationMinutes)` already takes a scalar parameter; D-09 adds `DayWindow` alongside it.

---

### Shape G — Two-channel solver plumbing (D-08): `Schedule.dayStart` → `ScheduleConfig.dayStart`

**The exact existing precedent RESEARCH.md names** — `desk.getSchedulingMode()` flowing through
`SolverService.buildSchedule` into `Schedule`, verified in full this session:

```java
// src/main/java/com/wfm/service/SolverService.java:614-634 (excerpt)
private Schedule buildSchedule(long tenantId, UUID deskId, SolveRequest request, Desk desk) {
    Schedule s = new Schedule();
    s.setId(UUID.randomUUID());
    s.setTenantId(tenantId);
    s.setDeskId(deskId);
    s.setStatus(ScheduleStatus.RUNNING);
    s.setCreatedAt(OffsetDateTime.now());
    s.setSchedulingMode(desk.getSchedulingMode());   // <- THE precedent: Desk field -> Schedule field
    s.setPeriodStartDate(request.periodStartDate());
    s.setPeriodEndDate(request.periodEndDate());
    s.setStartTime(request.startTime());
    s.setEndTime(request.endTime());
    s.setIncrementMinutes(request.incrementMinutes());
    // D-08 plumbing: add immediately after the schedulingMode line, same style:
    // s.setDayStart(desk.getDayStart());
    return s;
}
```

**`Schedule.getScheduleConfig()` assembling `ScheduleConfig` from `Schedule`'s own fields** — the
second half of the precedent (`src/main/java/com/wfm/model/Schedule.java:319-336`, verified in full
this session):

```java
@ProblemFactProperty
@Transient
public ScheduleConfig getScheduleConfig() {
    int consistencyToleranceMinutes = constraintWeights == null
            ? ScheduleConfig.DEFAULT_CONSISTENCY_TOLERANCE_MINUTES
            : constraintWeights.getConsistencyToleranceMinutes();
    return new ScheduleConfig(
            incrementMinutes, startTime, endTime,
            breakDurationMinutes, breakMinShiftHours, breakBlockedHours,
            breakStartAlignment, breakClusterThresholdPct,
            defaultContractedHoursPerDay,
            overallocationHardLimitPct, underallocationHardLimitPct,
            schedulingMode, consistencyToleranceMinutes);
    // D-08/D-10: append `, dayStart` as the 14th positional argument once Schedule.dayStart exists
    // and ScheduleConfig's canonical constructor gains a 14th component.
}
```

**`ScheduleConfig`'s existing delegating-constructor precedent** — the shape a 14th component
must follow (`src/main/java/com/wfm/model/ScheduleConfig.java:23-36`, verified in full this
session):
```java
public record ScheduleConfig(
        int incrementMinutes, LocalTime startTime, LocalTime endTime,
        int breakDurationMinutes, BigDecimal breakMinShiftHours, BigDecimal breakBlockedHours,
        BreakAlignment breakStartAlignment, int breakClusterThresholdPct,
        BigDecimal defaultContractedHoursPerDay,
        int overallocationHardLimitPct, int underallocationHardLimitPct,
        SchedulingMode schedulingMode, int consistencyToleranceMinutes
        // D-08: add `, LocalTime dayStart` as the 14th component here
) {
    public static final int DEFAULT_CONSISTENCY_TOLERANCE_MINUTES = 60;

    // existing 12-argument delegating constructor -- the PRECEDENT for a 13-argument delegating
    // constructor once dayStart is the 14th canonical component, preserving any pre-Phase-19
    // construction site that doesn't yet supply it:
    public ScheduleConfig(int incrementMinutes, LocalTime startTime, LocalTime endTime,
            int breakDurationMinutes, BigDecimal breakMinShiftHours, BigDecimal breakBlockedHours,
            BreakAlignment breakStartAlignment, int breakClusterThresholdPct,
            BigDecimal defaultContractedHoursPerDay, int overallocationHardLimitPct,
            int underallocationHardLimitPct, SchedulingMode schedulingMode) {
        this(incrementMinutes, startTime, endTime, breakDurationMinutes, breakMinShiftHours,
                breakBlockedHours, breakStartAlignment, breakClusterThresholdPct,
                defaultContractedHoursPerDay, overallocationHardLimitPct, underallocationHardLimitPct,
                schedulingMode, DEFAULT_CONSISTENCY_TOLERANCE_MINUTES);
                // D-08: this delegating form would need a dayStart default too (LocalTime.MIDNIGHT,
                // matching Desk's own field default at Desk.java:37) if kept at 12 args, OR simply
                // let every construction site move to the 14-arg canonical form -- a plan-time choice.
    }
}
```

**`Desk.dayStart`, the field this whole chain originates from** (`Desk.java:35-62`, already read in
full by RESEARCH.md):
```java
private LocalTime dayStart = LocalTime.MIDNIGHT;
public LocalTime getDayStart() { return dayStart; }
public void setDayStart(LocalTime dayStart) { this.dayStart = dayStart; }
```

---

### Shape H — Static utility with zero `Desk` references, own `main` — the D-07 allowlist candidate

**Analog:** `src/main/java/com/wfm/util/FteSpreadsheetGenerator.java` — confirmed this session: no
`Desk` import/reference anywhere in the file, has its own `public static void main(String[] args)`
at line 83.

```java
// src/main/java/com/wfm/util/FteSpreadsheetGenerator.java:48-64 (excerpt)
int firstMinute = DayWindow.startMinute(startTime);
int lastMinute = DayWindow.endMinute(endTime);
for (int minute = DayWindow.startMinute(startTime); minute < DayWindow.endMinute(endTime); ...) {
    LocalTime t = DayWindow.toLocalTime(minute);
    LocalTime slotEnd = DayWindow.toLocalTime(minute + incrementMinutes);
    // ...
}
```

This file cannot reach a `Desk` (it is a standalone spreadsheet-generation utility, not
tenant/desk-scoped), so per D-07 its call sites become
`DayWindow.anchoredAt(LocalTime.MIDNIGHT)` and every resulting `window.startMinute(...)` /
`window.endMinute(...)` / `window.toLocalTime(...)` call MUST be listed in the new third allowlist
section of `midnight-time-arithmetic.md` (Shape K) with the stated reason: "static report-generation
utility with its own `main`, no `Desk`/tenant context reachable."

---

### Shape K — Structural guard allowlist (D-07): the exact format the third section must follow

**Analog:** `src/test/resources/midnight-time-arithmetic.md`'s two EXISTING sections (read in full
this session, 152 lines total). This is the precedent for D-07's third section — same file, same
two-directional scan machinery, same annotation convention.

**Existing section 1 format** (`midnight-time-arithmetic.md:100-116`):
````markdown
### Permitted raw time arithmetic

```
com.wfm.model.ShiftBandPair :: java.time.temporal.ChronoUnit.MINUTES.between(usualStartTime, assignedEnvelopeStart));
com.wfm.service.ScheduleOutputService :: long minDistance = Math.abs(ChronoUnit.MINUTES.between(closest.startTime(), preferredBreak));
```

### Why each is permitted

- **`ShiftBandPair.startDeviationMinutes`** — the distance between an agent's *usual* shift START
  and their *assigned* envelope START (DRFT-03's one distance calculation). Both are start times;
  no end is involved, and the method is explicitly documented as start-only.
````

**Existing section 2 format** (`midnight-time-arithmetic.md:118-152`) follows the identical
`### Permitted raw time comparisons` / fenced code block / `### Why each comparison is permitted`
pairing, entry format `fully.qualified.ClassName :: trimmed code line`.

**D-07's new third section must follow this EXACT two-part format** — a fenced list of
`fully.qualified.ClassName :: DayWindow.anchoredAt(LocalTime.MIDNIGHT)`-based lines (or the
resulting `window.method(...)` calls, once D-04 lands), immediately followed by a "Why each is
permitted" subsection stating, per entry, why no desk anchor is reachable there. Per the guard's own
"What is enforced" section (lines 27-30): "each checked by its own set-equality assertion in
`MidnightTimeArithmeticGuardTest`" — the third section needs a THIRD set-equality assertion added to
that test class, mirroring the existing two (`RAW_ARITHMETIC_TOKENS`/`COMPARISON_TOKENS` scans).

**The D-05 finding this file should also record** (per `19-CONTEXT.md`'s Specific Ideas) — add a
note to this file's "What is enforced" section stating plainly that the guard cannot see `int`
arithmetic/comparisons, so hand-composing `endMinuteFromDayStart(ds,e) -
startMinuteFromDayStart(ds,s)` at a call site would NOT be caught here.

---

### Test-side shape — the `DeprecationIsLive` inversion target (D-03)

**Analog:** the nested class itself, already present and already reflective
(`DayWindowTest.java:468-504`, quoted in full above). D-03 inverts its two `Set<String>` constants
and its final assertion, not its machinery:

```java
// CURRENT (src/test/java/com/wfm/util/DayWindowTest.java:468-504)
@Nested
@DisplayName("deprecation is live: every midnight-implicit function is @Deprecated, no anchored function is")
class DeprecationIsLive {
    private static final Set<String> MIDNIGHT_IMPLICIT = Set.of(
            "startMinute", "endMinute", "durationMinutes", "isForwardWithinDay", "overlaps",
            "contains", "startsBefore", "toLocalTime", "plusWithinDay");
    private static final Set<String> ANCHORED = Set.of(
            "startMinuteFromDayStart", "endMinuteFromDayStart", "timeAtDayStartOffset",
            "businessDateOf", "calendarDateAtDayStartOffset");

    @Test
    void deprecationMatchesTheMidnightImplicitSetExactly() {
        List<Method> publicStatics = Arrays.stream(DayWindow.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers()))
                .collect(Collectors.toList());
        // ... asserts deprecated set == MIDNIGHT_IMPLICIT exactly
    }
}
```

**Target shape (D-03)** — same reflective scan, opposite standing property ("no public `DayWindow`
static takes a scheduling time"): after D-06 demotes the nine to private, `publicStatics` naturally
shrinks to just the five anchored primitives (which all take a `dayStart` `LocalTime` as their
FIRST parameter, never a lone scheduling time), so the inverted assertion should check parameter
SHAPE (first-parameter-named-`dayStart` or equivalent), not merely absence of `@Deprecated` — the
nine no longer being `getDeclaredMethods()`-visible as public is criterion 1's compiler-forced part;
D-03's inversion is the reflective proof that a FUTURE convenience overload re-introducing a bare
one-`LocalTime`-argument static form would also be caught.

---

### Test-side shape — the frozen-oracle nested class (D-13)

**No existing analog for the frozen-oracle mechanism itself** (confirmed: zero `"oracle"` matches
anywhere in `src/main/java` or `src/test/java` this session) — this is new test machinery. However,
**the exhaustive-sweep STYLE it must follow is directly proven in the same file**, in
`AnchoredEquivalenceAtMidnightAnchor` (`DayWindowTest.java:275-325`) and `RoundTripConsistency`
(`DayWindowTest.java:376-410`) — both already sweep all 1440 minutes (or all anchors × all 1440
minutes) rather than sampling, exactly the rigor D-13 asks the frozen oracle to match:

```java
// The exhaustive-sweep style to replicate (src/test/java/com/wfm/util/DayWindowTest.java:279-291)
@Test
@DisplayName("startMinuteFromDayStart/endMinuteFromDayStart equal startMinute/endMinute for every minute of the day")
void startAndEndMinuteEquivalence() {
    for (int m = 0; m < 1440; m++) {
        LocalTime t = DayWindow.toLocalTime(m);
        assertThat(DayWindow.startMinuteFromDayStart(MIDNIGHT, t))
                .as("startMinuteFromDayStart(MIDNIGHT, %s)", t)
                .isEqualTo(DayWindow.startMinute(t));
        // ...
    }
}
```

**Target shape (D-13)** — a new `@Nested` class (sibling to the eleven already in the file, same
`@DisplayName` convention) containing:
1. A `private static final class Oracle { ... }` (or similar) with the NINE current
   implementations copied verbatim from `DayWindow.java`'s lines 65-194 (before they are demoted to
   private and rewritten to delegate through the five primitives) — this nested class is what makes
   the comparison meaningful: it freezes today's bytecode-equivalent logic independent of whatever
   `DayWindow` becomes.
2. A parameterised or hand-rolled sweep, following the `for (int m = 0; m < 1440; m++)` style
   above, asserting `DayWindow.anchoredAt(MIDNIGHT).f(...)` equals `oracle.f(...)` at every point —
   see `19-CONTEXT.md` D-13's "Claude's Discretion" note on bounding the 4-argument `overlaps`/
   `contains` sweep (structured boundary-adjacent quadruples, not a full 1440⁴ cross-product).

---

## Shared Patterns

### Pattern: Build one `DayWindow` per public method, never per interval check
**Source:** D-04/D-08 (`19-CONTEXT.md`), demonstrated at scale in `TimeslotGeneratorService.java`
(Shape B)
**Apply to:** every service file in the classification table above
```java
DayWindow window = DayWindow.anchoredAt(desk.getDayStart());   // once, near where Desk is loaded
```

### Pattern: Pass the `DayWindow` object into model helpers, never a raw `LocalTime`
**Source:** D-08 (rejected alternative: denormalising `dayStart` onto `shift_template`)
**Apply to:** every Shape F call site (`ShiftBandPair.covers`, `ShiftTemplateBreakBand.getBreak*Time`,
`ShiftTemplate` net-hours)
```java
// correct:
pair.covers(ts, window);
// wrong -- a LocalTime parameter can be confused with a scheduling time, a DayWindow cannot:
pair.covers(ts, desk.getDayStart());
```

### Pattern: `schedule.getScheduleConfig().<scalar>()` is the established read path for a
desk-derived solver scalar
**Source:** `ScheduleOutputService.java:472`'s existing `consistencyToleranceMinutes()` read
**Apply to:** every Shape D file (`ScheduleOutputService`, `ScheduleExportService`) once
`ScheduleConfig.dayStart()` exists

### Pattern: Two-directional allowlist, set-equality, never subset
**Source:** `MidnightTimeArithmeticGuardTest`'s existing two families (RAW_ARITHMETIC_TOKENS /
COMPARISON_TOKENS), extended by D-07's third section (Shape K)
**Apply to:** `midnight-time-arithmetic.md` and its guard test — an unlisted new
`anchoredAt(LocalTime.MIDNIGHT)` occurrence in `src/main` must fail the build exactly as an
unlisted raw-arithmetic line does today

### Pattern: `@Deprecated` javadoc naming a requirement ID, never a phase number (D-24, carried from
Phase 18)
**Source:** every one of the nine current deprecation javadocs in `DayWindow.java` (e.g. line 61-62:
`"@deprecated BDAY-04 removes this midnight-implicit form..."`)
**Apply to:** any new javadoc this phase adds (D-02's `DeskService` correction, D-07's allowlist
annotations) — cite `SOLV-01`/`BDAY-04` etc., never "Phase 19" or "Phase 20"

## No Analog Found

| File / Call Site | Role | Data Flow | Reason |
|---|---|---|---|
| `DayWindow.java` class shape itself (static→bound-instance conversion) | utility | transform | No other class in this codebase performs this exact conversion; every other `com.wfm.util` class stays static-only. Use RESEARCH.md's Pattern 1 illustrative shape (Shape A above) instead of a codebase analog. |
| Frozen-oracle nested test class (D-13) | test | transform | Zero `"oracle"` occurrences anywhere in `src/main` or `src/test`; this mechanism is new. Follow the exhaustive-sweep STYLE of `AnchoredEquivalenceAtMidnightAnchor`/`RoundTripConsistency` (already in `DayWindowTest.java`) as the closest available rigor precedent. |
| `ShiftTemplateController.java:77-78` (`band.getBreakStartTime(template)`, `band.getBreakEndTime(template)`) | controller | request-response | Controller holds only `ShiftTemplateService` and `ShiftTemplateBreakBandRepository` — no `Desk`/`DeskRepository`/anchor source in scope (confirmed via full constructor read), and D-08 explicitly rejects "threading from the controller end to end." The service-mediated path (service exposes the bound `DayWindow`, or the response-building code moves into the service) is a genuine plan-time decision, not resolved by any existing precedent. |
| `ScheduleConstraintProvider` constraints using `.ifExists(ScheduleConfig.class, filtering(...))` rather than `.join(ScheduleConfig.class)` (e.g. `breakBlockedWindow`, lines 365-399) | solver constraint provider | event-driven | `ifExists` does not add its class to the output tuple, so `cfg.dayStart()` is unreachable at the point the midnight-implicit `DayWindow` calls occur inside these constraints, unlike Shape E's `.join`-based constraints. No existing constraint in this file demonstrates how to thread a joined scalar through an `ifExists`-gated stream; flag for explicit plan-time handling per constraint. |

## Metadata

**Analog search scope:** `src/main/java/com/wfm/util`, `src/main/java/com/wfm/model`,
`src/main/java/com/wfm/service`, `src/main/java/com/wfm/solver`, `src/main/java/com/wfm/controller`,
`src/test/java/com/wfm/util`, `src/test/resources`
**Files scanned this session (full or targeted read):** `DayWindow.java` (full, 302 lines),
`DayWindowTest.java` (full, 505 lines), `midnight-time-arithmetic.md` (full, 152 lines),
`ScheduleConstraintProvider.java` (targeted, two constraint bodies + import/join grep),
`ShiftBandPair.java` (full), `ShiftTemplateBreakBand.java` (full), `ShiftTemplateController.java`
(targeted), `ScheduleOutputService.java` (targeted), `TimeslotGeneratorService.java` (targeted),
`DeskService.java` (targeted, `setDayStart` + javadoc), `FteSpreadsheetGenerator.java` (targeted),
`Schedule.java`/`ScheduleConfig.java`/`SolverService.java` excerpts (reused verbatim from
RESEARCH.md, independently verified by RESEARCH.md this same session)
**Pattern extraction date:** 2026-09-30
