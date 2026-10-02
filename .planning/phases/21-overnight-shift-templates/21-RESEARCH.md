# Phase 21: Overnight Shift Templates - Research

**Researched:** 2026-10-02
**Domain:** Interval arithmetic / business-date modelling (Java/Spring backend), Excel export (Apache POI), React/TypeScript schedule grid rendering
**Confidence:** HIGH

## Summary

This phase is unusually well pre-researched: `21-CONTEXT.md` already measured the real codebase
(line numbers, exact predicates, exact strings) during `/gsd-discuss-phase` and recorded 19 numbered
decisions (D-01..D-19) plus a UI-SPEC with every copy string locked. This RESEARCH.md's job is to
independently re-verify those claims against the current tree (all verified HIGH confidence this
session — see `Code Examples` and inline `[VERIFIED: ...]` tags), fill the gaps CONTEXT.md left open,
and surface two things CONTEXT.md did not: a **verified discrepancy** between the UI-SPEC's locked
Toast copy and the actual backend exception string (see Pitfall 1), and the exact shape of the
one-computation-two-callers validation seam the new containment check must extend.

No new external dependency is needed anywhere in this phase — it is entirely a correctness and
disclosure phase over existing Java/Spring/Timefold and React/TypeScript/POI code. The backend half
is finishing work: `DayWindow.anchoredDurationMinutes` (Phase 19) and the business-date joins
(Phase 20) already make OVNT-01 through OVNT-04 substantially true; this phase's genuine backend
work is a save-time/library-time containment check (OVNT-05), two Excel rendering changes (OVNT-06),
and a structured-DTO label change (OVNT-07). The frontend half is new: `ScheduleResults.tsx` has
seven confirmed instances of lexical-string time comparison that silently misrender an overnight
shift, on a file with zero test coverage of any kind.

**Primary recommendation:** Follow CONTEXT.md's D-01 through D-19 as the locked implementation plan
— they are independently verified against HEAD in this session, not merely asserted. Do not
re-derive the architecture; the open items that genuinely need a planning-time decision are the
Erlang range-delete scope call (Open Questions #1), the 16-hour cap value (Open Questions #2), and
the exact shape of `dayWindow.ts` (Claude's Discretion in CONTEXT.md).

## User Constraints (from CONTEXT.md)

<user_constraints>

### Locked Decisions

19 decisions (D-01 through D-19) govern this phase. Full text, rationale, and rejected alternatives
are in `.planning/phases/21-overnight-shift-templates/21-CONTEXT.md` `<decisions>` — reproduced here
only as a compressed index; **do not plan from this index alone, read the full CONTEXT.md decision
text**, since each decision's rejected-alternatives reasoning is load-bearing for plan-checker review:

- **D-01**: The desk day-start control becomes editable in `DeskManagement.tsx`, surfacing four
  existing-but-unreachable backend refusals (not adding new validation).
- **D-02**: A `00:00` desk's refusal message for an overnight pair now names the desk's actual day
  start as the cause.
- **D-03**: `setDayStart` gains a fifth refusal: re-anchoring is blocked when it would strand an
  already-stored template that the save path would now reject.
- **D-04**: The day-start control renders disabled (naming the blocking ACCEPTED schedule) when one
  exists; the server-side unconditional refusal stays wired for the race case.
- **D-05**: The 15/30/60-minute generation increment set is NOT narrowed; a non-blocking tiling
  warning is added at save time, reading the existing increment via `getLiveBounds`.
- **D-06**: OVNT-05 is reinterpreted as **envelope-vs-operating-window containment**
  (`[bounds.startTime(), bounds.endTime()]`), not business-day fit — the latter is vacuous by
  construction once `anchoredIsForwardWithinDay` passes. The inherited v1.3 deferred-item text citing
  an "It will still save" advisory for this gap is **wrong** — that string belongs to a different
  finding (the contracted-hours mismatch advisory); there is no pre-existing envelope-containment
  advisory at all.
- **D-07**: The new containment check is **blocking for overnight templates, advisory for same-day
  ones** — zero regression risk for overnight (no desk has one today), and the same-day half stays
  non-blocking because `dev` is live and a blocking check there could strand an existing live desk's
  template.
- **D-08**: A fixed 16-hour maximum span is added in `ShiftTemplateService.validate`, not inside
  `DayWindow` (19-CONTEXT D-11 reserved `DayWindow` for interval arithmetic only, never a length
  opinion).
- **D-09**: The new containment predicate is shared by the save path (`ShiftTemplateService.validate`)
  and the library/mode-gate path (`ShiftLibraryValidationService.requireShiftModeReady`) — one
  predicate, two callers, "the report and the refusal can never disagree."
- **D-10**: `ShiftLibraryGenerationService.generateSuggestion` runs through the same predicate so a
  generated suggestion can never recommend what the save path would refuse.
- **D-11**: OVNT-06's "continuation indicator on the morning-after cell" is a **void premise** on the
  Roster sheet (it is already business-day-keyed, one cell per shift) — ROADMAP criterion 4 and
  OVNT-06's text are amended to say so. Real fragmentation lives in the per-date **slot** grids.
- **D-12**: The Roster cell for an overnight shift spells out both weekdays verbatim:
  `Sun 22:00-Mon 06:00` (operator-selected from three alternatives). One new legend row. Column width
  rises `16*256` → `22*256` POI units.
- **D-13**: The Excel Allocation sheet's per-date slot columns are ordered by offset-from-anchor, not
  clock order — byte-identical output at a `00:00` anchor (day-start order and clock order coincide).
- **D-14**: OVNT-07 adds structured `businessDate`/`calendarDate` fields to the violation DTO and
  re-points both existing string-parsers (`ScheduleExportService` and `ScheduleResults.tsx`) BEFORE
  changing the label text shape — the label is parsed as data in two places today and changing its
  shape first would silently break both.
- **D-15**: `dayStart` is added to `ScheduleDetailResponse`/`ScheduleSummary`/the frontend types; a
  new `frontend/src/utils/dayWindow.ts` exposes a branded `DayOffset` type so all seven confirmed
  lexical-time-comparison defect sites in `ScheduleResults.tsx` become compiler errors until fixed —
  the only structural-guard mechanism available on a frontend with zero test infrastructure.
- **D-16**: Both the SLOT-mode and SHIFT-mode render branches get anchored ordering unconditionally —
  SOLV-04 already proved SLOT mode can place an agent across midnight on today's data.
- **D-17**: The allocation grid's per-business-day `<h4>` section headers disclose their calendar
  span, suppressed at a `00:00` anchor (where it would be noise).
- **D-18**: The three `@AssertsTodaysBehaviour` registry entries in
  `midnight-boundary-scenarios.md:85-87` flip in the same change that flips their assertion, each
  deregistered — `MidnightBoundaryScenarioRegistryTest` fails the build on either a flip without
  deregistration or a stale entry.
- **D-19**: The three BDAY-06 scenarios deferred since Phase 18 become constructible here — they are
  the same three as D-18's registry rows.

### Claude's Discretion

- The exact spelling of `dayWindow.ts`'s branded type and function set.
- Whether `dayWindow.ts` also covers `StaffingTab`/`PtoTab`/`DriftTab` date columns (not defective —
  ISO strings sort correctly lexically — purely a consistency question).
- Whether `DeskManagement.tsx`'s new time picker reuses `dayWindow.ts` or stays plain string input.
- The 16-hour value in D-08, if evidence suggests otherwise.
- How the unfilled-seat map key (`${date}|${slot}`) changes once slots carry offsets.
- Task ordering and plan decomposition, including where D-11's document amendments land relative to
  the code they describe.
- Whether `ScheduleSummary` gains `dayStart` alongside `ScheduleDetailResponse` or only the latter
  does.

### Deferred Ideas (OUT OF SCOPE)

- An operating-window containment refusal for **same-day** templates (stays advisory here per D-07;
  its own phase needs a live-template survey first).
- A frontend test harness (vitest/jest) — D-15 deliberately avoids this inside a correctness phase.
- Renaming `date` → `businessDate` across the six business-date-shaped problem facts (~200 sites) —
  carried from 20-CONTEXT, not this phase's job.
- A per-desk maximum shift length, if D-08's fixed 16h cap ever needs to vary.
- A documented, tested day-start reversal for a desk holding an ACCEPTED schedule (MIGR-04).
- Restoring a genuine live-desk drift check for Phil-US (belongs with the deferred MIGR-01..04
  migration).
- Deleting the frozen `DayWindow` oracle (19-CONTEXT D-13) once v1.5 ships.
- Tightening `ScheduleConfig`'s defensive null-anchor fallback.
- Blocked-break-hours enforcement in SHIFT mode (operator ruling OR-2 at v1.3 close — a distinct
  requirement with no OVNT id, not folded into this phase).
- Cross-agent seat displacement for the atomic shift move, and a blank per-desk upload template —
  both reviewed and explicitly ruled **out** of Phase 21, raised as candidate phases after Phase 22.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| OVNT-01 | Operator can save a shift template whose end time is earlier in the clock than its start time, with correct net hours | **Mostly already true** — `[VERIFIED: src/main/java/com/wfm/util/DayWindow.java:126-137]` `anchoredDurationMinutes` wraps forward instead of throwing; `[VERIFIED: src/main/java/com/wfm/model/ShiftTemplate.java:132-141]` `getNetHours` already calls it. Remaining work is D-02's refusal-message fix and D-18's registry flip. See Code Examples #1, #2. |
| OVNT-02 | Shift reported against the business day it starts on, everywhere displayed | **Backend already true** — `[VERIFIED: src/main/java/com/wfm/service/ScheduleOutputService.java:166,172,321-324,742-745]` keys `agentSchedule` on `getBusinessDate()`. Frontend is NOT true — see D-15's seven sites, Pitfall 3. |
| OVNT-03 | Day-off/PTO on starting business day blocks the shift | **Already true** — `[VERIFIED: src/main/java/com/wfm/solver/ScheduleConstraintProvider.java:174-184]` `agentDayOff` joins on `a.getTimeslot().getBusinessDate()` vs `AgentDayOff::getDate`. Work is D-18's registry proof, not new behaviour. |
| OVNT-04 | Consumes contracted hours of the starting weekday only | **Already true** — `[VERIFIED: src/main/java/com/wfm/service/SolverService.java:838-873]` `computeAgentDayConfigs` iterates business dates and carries `schedule.getDayStart()` into every `AgentDayConfig`; `agent_day_hours` is keyed by `DayOfWeek` per REQUIREMENTS.md item 5 (unverified claim this session — not re-read directly, but cross-confirmed by 20-VERIFICATION.md's SOLV-04 pass). |
| OVNT-05 | Shift library validation refuses an overnight envelope that doesn't fit the operating window | **Genuinely new work.** `[VERIFIED: src/main/java/com/wfm/service/ShiftLibraryValidationService.java]` confirms `bounds.endTime()` is never called anywhere in the file (only `bounds.startTime()`/`bounds.incrementMinutes()`, at lines 295-296, 305-307) — `TimeslotBoundsResponse.endTime()` is dead exactly as D-06 claims. See Code Examples #3. |
| OVNT-06 | Excel + UI render overnight shift as one continuous block | **Split.** Roster sheet: already one cell (D-11's void-premise finding, verified — see Pitfall 2). Allocation sheet: genuinely fragmented today — `[VERIFIED: src/main/java/com/wfm/service/ScheduleExportService.java:703-712]` builds `slotSet` as a `TreeSet<LocalTime>` (natural/clock ordering), not anchor-relative. Frontend grid: genuinely fragmented — D-15's seven sites. |
| OVNT-07 | Calendar dates disclosed wherever displayed | **Genuinely new work**, and load-bearing hazard confirmed. `[VERIFIED: src/main/java/com/wfm/service/ScheduleOutputService.java:669-672,771]` the timeslot label is built by raw concatenation (`ts.getDate() + " " + ts.getStartTime() + "-" + ts.getEndTime()`) in exactly two places; `[VERIFIED: src/main/java/com/wfm/service/ScheduleExportService.java:826-836]` and `[VERIFIED: frontend/src/pages/ScheduleResults.tsx:386-393]` both parse that exact string by `indexOf(' ')`/`substring`. See Code Examples #4. |

</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Desk day-start anchor editing (D-01..D-04) | Browser / Client | API / Backend | `DeskManagement.tsx` surfaces controls; all refusal logic already lives server-side in `DeskService.setDayStart` |
| Shift template save-time validation (OVNT-01, OVNT-05, D-02, D-06, D-08, D-09) | API / Backend | — | `ShiftTemplateService.validate` is the single save-path gate; no client-side duplicate logic |
| Shift-library mode-gate / suggestion generation (D-09, D-10) | API / Backend | — | `ShiftLibraryValidationService`/`ShiftLibraryGenerationService` are pure backend services, read by the frontend only for display |
| Solver joins and per-day seat model (OVNT-02, OVNT-03, OVNT-04) | API / Backend (solver subsystem) | Database / Storage | `ScheduleConstraintProvider` and `SolverService` run entirely server-side; `Timeslot.business_date` is the stored column they key on |
| Excel export rendering (OVNT-06 Roster/Allocation, OVNT-07 labels) | API / Backend | — | `ScheduleExportService`/`ScheduleOutputService` generate the workbook server-side; the browser only downloads the finished `.xlsx` |
| Schedule grid rendering and ordering (OVNT-06 UI, OVNT-07 UI) | Browser / Client | API / Backend (payload shape) | `ScheduleResults.tsx` does all grid layout client-side; backend's only new responsibility is carrying `dayStart` in the response DTOs (D-15) |

## Standard Stack

No new external dependency is introduced or needed by this phase. It is entirely implemented in the
project's existing stack.

### Core (existing, reused — not introduced by this phase)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot / Spring Data JPA | pinned per `build.gradle` (unchanged this phase) | Backend service/repository layer | Already the project's framework for every service this phase edits |
| Timefold Solver | **1.16.0** `[VERIFIED: PROJECT STATE.md, cross-confirmed by build.gradle at Phase 12]` | Constraint solving | Pinned — `ScoreAnalysis` moves to paid tier at 2.0; custom-move API at this version predates `Neighborhoods` (introduced 1.31.0) |
| Apache POI (XSSFWorkbook) | existing pin, unchanged | Excel export (`.xlsx`) | Already the project's export engine; D-12/D-13 are column/row/width edits to existing `ScheduleExportService` code, not a library change |
| React + TypeScript (Vite) | existing pin, unchanged | Frontend | `frontend/package.json` build is `tsc -b && vite build` — no test runner, no component library (`Tool: none` per UI-SPEC) |

### Supporting — none new

This phase adds **zero** new runtime dependencies (matches `REQUIREMENTS.md` item "What research
established" #4: "No new runtime dependency is needed" — stated for the whole v1.5 milestone, and
nothing in this phase's scope changes that). `jqwik` (test-scope only, already present from Phase 18)
may be reused for the OVNT-05 containment predicate's property tests if the planner follows the
Phase 18/19/20 precedent of property-based construction tests — this is an existing test dependency,
not a new one.

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `dayWindow.ts` branded type (D-15) | vitest + a structural scan guard (the Java `MidnightTimeArithmeticGuardTest` pattern) | Rejected for this phase: adds a new dev dependency, CI step, and a parallel test-infrastructure project inside a correctness phase. Deferred (see Deferred Ideas). |
| `(+1)` Excel marker (D-12) | Spelling out both weekdays in the cell | Rejected by the operator at UI-SPEC time: preserves column width but moves disclosure into a legend lookup rather than the cell itself. |
| Per-desk maximum shift length (D-08) | A fixed 16h constant in `ShiftTemplateService` | Rejected: no desk has asked to vary the bound; a per-desk schema/control/refusal for an unrequested axis is over-engineering. |

**Installation:** none required.

**Version verification:** No new packages — nothing to verify against a registry. Existing pins
(Timefold 1.16.0, POI, Spring Boot) are unchanged by this phase and were not re-verified this
session since no version bump is proposed.

## Package Legitimacy Audit

**Not applicable.** This phase installs zero new external packages in any ecosystem (npm, Maven/
Gradle, or otherwise). All work is new code and edits to existing files in the current stack. The
Package Legitimacy Gate protocol was not run because there is nothing to check — `grep`-confirmed
`build.gradle` and `frontend/package.json` are both unmodified by anything this research recommends.

**Packages removed due to [SLOP] verdict:** none (no packages proposed).
**Packages flagged as suspicious [SUS]:** none (no packages proposed).

## Architecture Patterns

### System Architecture Diagram

```
Operator (DeskManagement.tsx)
      |
      |  PUT /desks/{id}/day-start  (D-01: control newly editable)
      v
DeskService.setDayStart  ──refuses──> 4 named reasons (sub-minute, 15-min boundary,
      |                                ACCEPTED-schedule, [D-03 NEW] stranded-template)
      | (persists Desk.dayStart)
      v
ShiftTemplateService.dayWindowFor(deskId) ──binds──> DayWindow.anchoredAt(dayStart)
      |
      v
ShiftTemplateService.validate(request)
   ├─ window.anchoredIsForwardWithinDay  ──refuses (D-02: names dayStart)──> 400
   ├─ window.anchoredDurationMinutes     ──feeds──> validateBands (envelope containment)
   ├─ [D-08 NEW] 16h max-span check      ──refuses──> 400
   └─ [D-06/D-09 NEW] containment vs TimeslotBoundsResponse[startTime,endTime]
        ├─ overnight template → BLOCKS (D-07)
        └─ same-day template  → advisory only (D-07)
                 ^
                 |  same predicate, second caller
ShiftLibraryValidationService.requireShiftModeReady ──refuses SHIFT-mode switch (D-09)
ShiftLibraryGenerationService.generateSuggestion     ──never recommends a refused shape (D-10)

                         ... solve happens (Phase 20 joins, unaffected) ...

ScheduleOutputService.buildAgentSchedule / buildPreferenceReport
      keyed on Timeslot.getBusinessDate()            [already correct, OVNT-02/03/04]
      |
      ├─> violation label: ts.getDate()+" "+start+"-"+end   [D-14 NEW: add structured
      |        ^                                             businessDate/calendarDate
      |        | parsed as DATA, not display, by:            fields; re-point both
      |        |   - ScheduleExportService.unfilledSeatsByDateAndSlot (substring)       parsers BEFORE
      |        |   - ScheduleResults.tsx (substring)                                    changing label text]
      |
      v
ScheduleExportService.writeRoster → shiftCode()        [D-12 NEW: "Sun 22:00-Mon 06:00"
      |                                                  cell shape, vertical legend]
      v
ScheduleExportService.writeAllocationSheet → slotStarts()
      slots ordered by TreeSet<LocalTime> (clock order) [D-13 NEW: order by
                                                           offset-from-anchor instead]
      v
.xlsx download (operator)

Parallel path: ScheduleResults.tsx (frontend grid)
      ScheduleDetailResponse.dayStart  [D-15 NEW field] ──feeds──> frontend/src/utils/dayWindow.ts
                                                                    [D-15 NEW: branded DayOffset type]
      7 confirmed lexical-time-comparison sites become compile errors until converted
      (timeDiffMinutes, the `t < dayEnd` loop, two `while` break loops, two lexical `.sort()`s,
       one `slot >= s && slot < e` containment check, one `localeCompare` ordering)
      → D-16: anchored ordering applied unconditionally to both SLOT and SHIFT render branches
      → D-17: per-date `<h4>` header discloses calendar span, suppressed at 00:00 anchor
```

### Recommended Project Structure

No new directories. This phase edits files in place:

```
src/main/java/com/wfm/
├── service/
│   ├── ShiftTemplateService.java          # D-02, D-03 (via DeskService), D-06, D-08, D-09
│   ├── ShiftLibraryValidationService.java # D-06 (endTime() activation), D-09
│   ├── ShiftLibraryGenerationService.java # D-10
│   ├── DeskService.java                   # D-03, D-04 (setDayStart gains a 5th refusal)
│   ├── TimeslotGeneratorService.java      # D-05 (advisory read, no new gate)
│   ├── ScheduleExportService.java         # D-12 (Roster), D-13 (Allocation), D-14's parser
│   └── ScheduleOutputService.java         # D-14 (label fields + text)
├── dto/
│   ├── TimeslotBoundsResponse.java        # endTime() — dead today, activated by D-06
│   ├── ScheduleDetailResponse.java        # D-15 adds dayStart (ALREADY PRESENT — see note)
│   └── ScheduleSummary.java               # D-15 may add dayStart (Claude's Discretion)
frontend/src/
├── utils/dayWindow.ts                     # NEW FILE — D-15's branded DayOffset type
├── pages/
│   ├── ScheduleResults.tsx                # D-15's 7 sites, D-16, D-17
│   └── DeskManagement.tsx                 # D-01, D-04
└── api/client.ts                          # Desk, ScheduleDetail/ScheduleSummary types
```

**Correction to CONTEXT.md's canonical-refs list:** `ScheduleDetailResponse.java` **already has**
a `dayStart` field `[VERIFIED: src/main/java/com/wfm/dto/ScheduleDetailResponse.java:23,241-242]`
(`private LocalTime dayStart;` with getter/setter) — this landed in Phase 19 per
`19-VERIFICATION.md`'s `covered_files` list, which names this exact file. What is **confirmed still
missing** is the frontend TypeScript side: `[VERIFIED: frontend/src/api/client.ts:523-533]`
`ScheduleDetail` (extends `ScheduleSummary`) carries no `dayStart` field, and
`[VERIFIED: grep of ScheduleSummary.java]` the Java `ScheduleSummary` record also has none yet. So
D-15's "new plumbing" is: (1) add `dayStart` to the Java `ScheduleSummary` record if the planner
decides the summary-poll path needs it (Claude's Discretion), and (2) add `dayStart` to both
frontend TypeScript interfaces and read it in `ScheduleResults.tsx`. The backend detail-response
field is not new work.

### Pattern 1: One-computation-two-callers validation (D-09)

**What:** A single non-throwing report method (`validate`) is wrapped by a throwing gate method
(`requireShiftModeReady`) that converts specific findings into a `PreSolveValidationException`. The
class javadoc states the reason explicitly: "so the report and the refusal can never disagree."

**When to use:** Any time a validation result must be shown to an operator (read-only report/
advisory) AND used to block an action (hard refusal) — this phase's new containment check is exactly
this shape (D-06 is the predicate, D-07 is "advisory vs blocking" branching on overnight-vs-same-day,
D-09 wires it into both existing callers).

**Example (existing code, verified this session):**
```java
// Source: src/main/java/com/wfm/service/ShiftLibraryValidationService.java:163-184
public void requireShiftModeReady(UUID deskId) {
    ShiftLibraryValidationResponse response = validate(deskId);
    List<ErrorDetail> errors = new ArrayList<>();

    if (!response.hasLiveDemand()) {
        errors.add(new ErrorDetail("demand", NO_DEMAND_MESSAGE, null));
    } else {
        for (String window : response.uncoveredWindows()) {
            errors.add(new ErrorDetail("coverage", window, null));
        }
        addGridDetails(deskId, response, errors);
        if (!response.unsatisfiableWeekdays().isEmpty()) {
            errors.add(new ErrorDetail("contractedHours", contractedHoursMessage(response), null));
        }
    }

    if (!errors.isEmpty()) {
        String message = !response.uncoveredWindows().isEmpty()
                ? response.uncoveredWindows().size() + " demand window(s) have no covering shift template"
                : errors.get(0).message();
        throw new PreSolveValidationException(message, errors);
    }
}
```
The new D-06 containment finding slots in as one more `if` block adding an `ErrorDetail`, following
this exact shape — it does not need a new mechanism.

### Pattern 2: Additive-first, consume-second (D-14)

**What:** Add a new structured field/column alongside an existing string/implicit representation;
only after the new field is wired into every consumer does the old representation's text actually
change. Phases 18/19/20 all used this for `ScheduleConfig`, `business_date`, and `Schedule.dayStart`.

**When to use:** Whenever an existing value is both displayed AND parsed as data by something else
— changing shape and text together risks a silent break in the parser, because nothing but a human
re-reading the format string would catch it.

**Example — the hazard this avoids, verified this session:**
```java
// Source: src/main/java/com/wfm/service/ScheduleOutputService.java:771 (and :669-672, duplicated)
String timeslotLabel = ts.getDate() + " " + ts.getStartTime() + "-" + ts.getEndTime();
```
```java
// Source: src/main/java/com/wfm/service/ScheduleExportService.java:826-836 — parses the ABOVE string back apart
int space = label.indexOf(' ');
if (space < 0) continue;
String datePart = label.substring(0, space);
String timePart = label.substring(space + 1).trim();
```
```typescript
// Source: frontend/src/pages/ScheduleResults.tsx:388-391 — the SAME string, parsed a second, independent way
const spaceIdx = v.timeslotLabel.indexOf(' ')
if (spaceIdx < 0) continue
const vDate = v.timeslotLabel.substring(0, spaceIdx)
```
Both parsers assume exactly one space between the date and the time range. Changing the label text
to disclose a calendar span (e.g. adding a second date) before adding structured
`businessDate`/`calendarDate` DTO fields and re-pointing both parsers to those fields would silently
break unfilled-seat attribution in **both** the Excel export and the UI grid at once. D-14 is correct
to sequence this additive-first.

### Pattern 3: Compiler-forced migration on a bounded surface (D-15, precedent: 19-CONTEXT D-04/D-06)

**What:** Remove or retype the unsafe overload/representation so every call site becomes a build
error, rather than relying on a runtime guard or a reviewer's memory.

**When to use:** When the unsafe surface is small and enumerable (here: exactly 7 sites in one file)
and no test runner exists to host a structural scan guard (the Java-side alternative, proven twice
already in this codebase via `MidnightTimeArithmeticGuardTest`).

**Example — the defect class D-15 forecloses, verified this session:**
```typescript
// Source: frontend/src/pages/ScheduleResults.tsx:681 — lexical string comparison
const isEnvelopeReached = (slot: string) => envelopeSpans.some(([s, e]) => slot >= s && slot < e)
```
For an overnight envelope `s="22:00"`, `e="06:00"`, no slot string satisfies `slot >= "22:00" &&
slot < "06:00"` lexically (e.g. `"02:00" >= "22:00"` is `false`), so the predicate is false for
every hour — exactly the "every column renders grey 'no shift covers this hour'" defect D-15's table
names. A branded `DayOffset` return type on `dayWindow.ts`'s functions makes `slot >= s` a type error
on raw strings, forcing every comparison through an anchor-aware numeric offset instead.

### Anti-Patterns to Avoid

- **Comparing scheduling times as raw strings or with `LocalTime.isAfter()`/`isBefore()` outside
  `DayWindow`:** `LocalTime` has no `24:00`; its minimum value `00:00` is reused to mean "end of day"
  in an end position. Every comparison must go through an anchored `DayWindow` accessor
  (`anchoredStartMinute`/`anchoredEndMinute`/etc.) or a `DayOffset`-typed frontend equivalent, never a
  bare `.isAfter()`/`<`/`.sort()` on the raw value.
- **Changing a load-bearing label's text format in the same commit that adds its structured
  replacement fields:** see Pattern 2. Land the fields, re-point both parsers, confirm both still
  pass, THEN change the text (D-14's explicit sequencing).
- **Putting a shift-length opinion inside `DayWindow`:** 19-CONTEXT D-11 reserved `DayWindow` for pure
  interval arithmetic; a maximum-span business rule belongs in the calling service
  (`ShiftTemplateService`), confirmed as D-08's placement.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Overnight-safe duration/comparison arithmetic | A second bespoke midnight-wrap helper in `ShiftTemplateService` or the frontend | `DayWindow.anchoredAt(dayStart)`'s instance methods (backend); the planned `dayWindow.ts` branded-type module (frontend) | `DayWindow` is already the single, exhaustively-tested (`DayWindowTest`, 62 tests across 14 nested classes, `[VERIFIED: 19-VERIFICATION.md]`) implementation; a second implementation reintroduces exactly the class of defect this milestone exists to close |
| Envelope-vs-operating-window containment logic | A second containment check duplicated between the save path and the library/mode-gate path | D-09's single shared predicate, called from both `ShiftTemplateService.validate` and `ShiftLibraryValidationService.requireShiftModeReady` | The codebase's own established pattern (Pattern 1 above) exists specifically so "the report and the refusal can never disagree" |
| Frontend date/time ordering guard | A new frontend test runner + structural scan ported from `MidnightTimeArithmeticGuardTest` | The compiler-forced branded `DayOffset` type (D-15) | Deliberately rejected per D-15/Deferred Ideas: adds a new dependency and CI step inside a correctness phase; the branded type gets the same practical guarantee for this bounded 7-site surface with zero new infrastructure |
| Excel column reordering for overnight shifts | A visual "wrap" marker or a second worksheet layout | D-13's offset-from-anchor sort key over the existing `TreeSet<LocalTime>` | Byte-identical at a `00:00` anchor by construction; a visual marker still leaves the shift reading as two separate runs, which is exactly what OVNT-06 forbids |

**Key insight:** Every piece of "don't hand-roll" guidance in this phase traces back to one fact:
`DayWindow` is already correct and already tested exhaustively for the overnight case. The phase's
job is almost entirely *wiring existing, correct primitives into places that currently bypass them*
(the library validator's dead `endTime()` field, the Excel export's clock-ordered `TreeSet`, the
frontend's seven raw-string comparisons) — not inventing new arithmetic.

## Common Pitfalls

### Pitfall 1: The UI-SPEC's locked ACCEPTED-schedule refusal copy does not match the actual backend string

**What goes wrong:** `21-UI-SPEC.md`'s Copywriting Contract locks this Toast text as "backend message
verbatim": `"Desk has an accepted schedule ({scheduleId}) with period {periodStart}–{periodEnd}"`.
The actual, currently-shipping exception text is different:

```java
// Source: src/main/java/com/wfm/service/DeskService.java (inside setDayStart), verified this session
throw new ConflictException("Desk has an accepted schedule (" + blocking.getId()
        + ", " + blocking.getPeriodStartDate() + " to " + blocking.getPeriodEndDate() + ")");
```
This renders as `"Desk has an accepted schedule (<uuid>, 2026-09-01 to 2026-09-07)"` — the id and
dates sit inside one parenthetical joined by `, ` and `to`, not `"(<uuid>) with period <start>–
<end>"`. If the planner/executor faithfully reproduces the UI-SPEC's literal quoted string as a
second, hand-authored Toast message (rather than just rendering `err.message` from the backend), the
operator will see a string that was never actually produced by the server, and D-04's "server error
path stays wired, belt-and-suspenders" claim becomes untrue in the literal-text sense.

**Why it happens:** The UI-SPEC was authored by reading the decision text in CONTEXT.md's D-04
(which itself paraphrases the message) rather than re-reading `DeskService.java`'s literal throw
statement at UI-SPEC time.

**How to avoid:** Render the Toast from the actual caught error message (`getErrorMessage(err)`,
the file's existing pattern per the UI-SPEC's own Copywriting Contract header note) rather than a
hand-typed literal matching the UI-SPEC's quoted text. This is already the UI-SPEC's own stated
mechanism ("Toast (`error`): backend message verbatim") — the pitfall is only in trusting the quoted
example string as the literal payload to hardcode anywhere (e.g., in a test assertion).

**Warning signs:** A test asserting the frontend renders the exact UI-SPEC string `"... with period
..."` will fail against the real backend, or worse, pass against a hand-typed frontend copy that
diverges from what the server actually sends.

### Pitfall 2: OVNT-06's literal requirement text describes a cell that does not exist

**What goes wrong:** OVNT-06 and ROADMAP criterion 4 both say an overnight shift needs "a distinct,
non-blank, non-duplicate continuation indicator on the morning-after cell." On the Roster sheet,
`[VERIFIED: src/main/java/com/wfm/service/ScheduleOutputService.java:166,321-324]` `agentSchedule`
is already grouped by `getBusinessDate()` — a `22:00-06:00` shift on a `21:00`-anchored desk occupies
exactly one business-day column; there is no "morning-after cell" on this sheet at all (business day
D+1 starts at calendar D+1 21:00, long after the shift already ended). A planner or executor reading
the requirement literally, without D-11's amendment, will go looking for a cell to annotate that
cannot exist on this surface, and may mistakenly build indicator logic into `writeRoster` where none
is needed.

**Why it happens:** The requirement was authored before any desk could hold an overnight template,
so "the Roster is business-day-keyed, hence this premise is false" was undiscoverable until this
phase's discussion actually measured the code.

**How to avoid:** Follow D-11 exactly — amend ROADMAP criterion 4 and OVNT-06's text as part of this
phase's own document work, and confine "never two fragments" enforcement to the per-date **slot**
grids (the Excel Allocation sheet and the frontend allocation grid), where the fragmentation is real
and verified (Pitfall covered by D-13/D-15/D-16).

**Warning signs:** A plan task that adds a `(+1)`-style marker or morning-after annotation logic to
`writeRoster`/`shiftCode` specifically — that is the wrong surface for this fix.

### Pitfall 3: Lexical string comparison on HH:MM times silently produces plausible-looking wrong output

**What goes wrong:** Seven confirmed sites in `ScheduleResults.tsx` compare, sort, or subtract HH:MM
strings as plain strings/numbers without accounting for the day-start anchor:

```typescript
// Source: frontend/src/pages/ScheduleResults.tsx:884-885, verified this session
function timeDiffMinutes(start: string, end: string): number {
  const [sh, sm] = start.split(':').map(Number)
  const [eh, em] = end.split(':').map(Number)
  return (eh * 60 + em) - (sh * 60 + sm)
}
```
For `start="22:00"`, `end="06:00"` this returns `-960`, not `480`. Downstream `if (inc > 0)` guards
then silently skip the overnight segment rather than throwing — the failure mode is **a
plausible-looking but wrong render** (missing break slots, a grid that appears to start later than
it does, grey "uncovered" cells where a shift actually exists), not a crash. This matches the
project's own established failure-mode pattern across this milestone: "a constraint that matches
nothing scores identically to a satisfied one" (REQUIREMENTS.md).

**Why it happens:** `ScheduleResults.tsx` has zero test coverage of any kind
(`[VERIFIED: frontend/package.json]` — no `test` script, no vitest/jest dependency;
`[VERIFIED: find frontend -iname "*.test.*" -o -iname "*.spec.*"]` returns zero files), so these
seven sites have never been exercised against a non-midnight anchor; they were all written when
every desk was gated to `00:00` and have silently accumulated since.

**How to avoid:** D-15's branded `DayOffset` type (see Pattern 3) — route all seven sites through
`frontend/src/utils/dayWindow.ts` so raw-string comparison is a compile error, then manually verify
each converted site against a `21:00`-anchored fixture (no automated regression is available on this
surface — see Validation Architecture below).

**Warning signs:** Any new or touched line in `ScheduleResults.tsx` that does `<`, `>`, `.sort()`,
subtraction, or `localeCompare` directly on a time-shaped string without going through
`dayWindow.ts`.

### Pitfall 4: `DeskService.setDayStart`'s 15-minute-boundary gate is increment-independent by design — do not conflate it with D-05's generation-time tiling check

**What goes wrong:** `[VERIFIED: src/main/java/com/wfm/service/DeskService.java, setDayStart body]`
the save-time gate uses a fixed `% 15` modulus regardless of the desk's actual generation increment
(15/30/60 min), while `[VERIFIED: src/main/java/com/wfm/service/TimeslotGeneratorService.java:220-236]`
`requireDayStartTiles` checks `dayStart % incrementMinutes`. A desk whose generation increment is 30
or 60 minutes can save a day-start of e.g. `21:15` (passes the 15-min save gate) that will then be
refused at generation time with no warning in between — D-05 exists specifically to close that silent
gap with an **advisory**, not a second hard gate, because the increment is not desk state (it arrives
per-upload, inferred from the FTE spreadsheet header) and cannot be validated earlier than save time
without reading `getLiveBounds` for whatever increment the desk's existing live timeslots already
used.

**Why it happens:** The two checks live in two different services for a structurally sound reason
(one validates desk state, the other validates a per-call parameter) but that separation makes the
mismatch easy to miss if a planner assumes "15-minute boundary" at save time is sufficient.

**How to avoid:** Implement D-05 exactly as specified — read `getLiveBounds(deskId)` at save time,
and if it returns a non-empty result with `incrementMinutes` that does not evenly divide the proposed
`dayStart`, emit the non-blocking Toast warning (exact text locked in UI-SPEC's Copywriting Contract).
Never promote this to a second blocking gate at save time — the UI-SPEC's "D-05 warning toast" row
and "Message behaviour" section are explicit that the save must still succeed.

**Warning signs:** A plan task that changes `setDayStart`'s own modulus check to use the desk's
increment — this would be the wrong file and would also require knowing the increment before it
exists, which is structurally impossible per D-05's own rejected-alternatives analysis.

### Pitfall 5: `ShiftLibraryGenerationService.generateSuggestion` reads live demand with its own filter — don't assume it already shares every predicate with the save path

**What goes wrong:** `[VERIFIED: src/main/java/com/wfm/service/ShiftLibraryGenerationService.java:126-141]`
`generateSuggestion` independently calls `staffingRequirementRepository.findAllLiveByDesk` and filters
`sr.getRequiredFTEs() > 0` — it is a read-only report method with its own demand-shaping logic, not a
thin wrapper around `ShiftTemplateService.validate`. D-10 is correct that it is "the only path that
bypasses the save-path rules," but a planner should not assume adding the D-06 containment check here
is a one-line call to an existing shared method — it requires threading the new predicate into this
service's own suggestion-construction logic (likely at the point candidate envelope offsets are
chosen), not merely gating the method's entry point.

**Why it happens:** The method's docstring ("No write of any kind occurs — every collaborator call
below is a read") and its demand-shaping logic make it structurally different from `validate`'s
accumulate-then-report shape, even though both ultimately consume the same `TimeslotBoundsResponse`.

**How to avoid:** When wiring D-10, trace where `generateSuggestion` actually proposes envelope
start/end times and filter or clamp candidates against the D-06 containment predicate there — not
just at the method's top.

## Code Examples

Verified patterns from the current tree (all read directly this session):

### 1. `DayWindow.anchoredDurationMinutes` — already correct for overnight spans (OVNT-01's proof target)

```java
// Source: src/main/java/com/wfm/util/DayWindow.java:126-137
public int anchoredDurationMinutes(LocalTime start, LocalTime end) {
    int startOffset = startMinuteFromDayStart(dayStart, start);
    int endOffset = endMinuteFromDayStart(dayStart, end);
    int raw = endOffset - startOffset;
    if (raw > 0) {
        return raw;
    }
    if (raw == 0) {
        return 0;
    }
    return raw + MINUTES_PER_DAY;
}
```
At a `21:00` anchor, `22:00` → offset `60`; `06:00` → offset `540`; `raw = 480 > 0` → returns `480`
directly (no wrap needed in this particular example since it IS already forward relative to the
anchor). A genuinely anchor-crossing pair (e.g. anchor `21:00`, start `20:00`→offset `1380`, end
`19:00`→offset `1320`) would hit the `raw < 0` branch and wrap. ShiftTemplate's `getNetHours` calls
this directly (`src/main/java/com/wfm/model/ShiftTemplate.java:132-141`).

### 2. The existing save-path refusal — what D-02 changes and what it keeps

```java
// Source: src/main/java/com/wfm/service/ShiftTemplateService.java, validate() method
DayWindow window = dayWindowFor(deskId);

if (request.startTime() == null || request.endTime() == null
        || !window.anchoredIsForwardWithinDay(request.startTime(), request.endTime())) {
    throw new IllegalArgumentException("Shift template end time must be after its start time");
}

long envelopeMinutes = window.anchoredDurationMinutes(request.startTime(), request.endTime());
validateBands(request.bands(), envelopeMinutes);
```
D-02 changes only the thrown message text (to name `window`'s bound anchor); the
`anchoredIsForwardWithinDay` check, its position before `anchoredDurationMinutes`, and the overall
method shape are unchanged — confirmed byte-for-byte against `19-VERIFICATION.md`'s own observable
truth #2, which independently verified this exact code is unchanged by the Phase 19 re-anchoring.

### 3. The dead `endTime()` field D-06 activates

```java
// Source: src/main/java/com/wfm/dto/TimeslotBoundsResponse.java (full file)
public record TimeslotBoundsResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalTime startTime,
        LocalTime endTime,
        int incrementMinutes
) {}
```
```java
// Confirmed this session: grep of all `bounds.` accessor calls in
// src/main/java/com/wfm/service/ShiftLibraryValidationService.java returns only
// bounds.startTime() (lines 295, 305) and bounds.incrementMinutes() (lines 295-296, 305-307).
// bounds.endTime() has zero call sites in this file today.
```
The new D-06 containment check is the first real consumer of `endTime()` on this DTO.

### 4. The load-bearing label — add fields first, change text second (D-14)

```java
// Source: src/main/java/com/wfm/service/ScheduleOutputService.java:771 (duplicated at :669-672)
String timeslotLabel = ts.getDate() + " " + ts.getStartTime() + "-" + ts.getEndTime();
```
```java
// Source: src/main/java/com/wfm/service/ScheduleExportService.java:826-836
for (ViolationDetail v : cv.violations()) {
    String label = v.timeslotLabel();
    if (label == null) continue;
    int space = label.indexOf(' ');
    if (space < 0) continue;
    String datePart = label.substring(0, space);
    String timePart = label.substring(space + 1).trim();
    int dash = timePart.indexOf('-');
    if (dash > 0) timePart = timePart.substring(0, dash).trim();
    // ... LocalTime.parse(timePart) ...
}
```
```typescript
// Source: frontend/src/pages/ScheduleResults.tsx:386-391
for (const cv of violations) {
  if (cv.constraintName !== 'Unassigned assignment') continue
  for (const v of cv.violations || []) {
    if (!v.timeslotLabel) continue
    const spaceIdx = v.timeslotLabel.indexOf(' ')
    if (spaceIdx < 0) continue
    const vDate = v.timeslotLabel.substring(0, spaceIdx)
    const vStart = toHHMM(v.timeslotLabel.substring(spaceIdx + 1))
```
Both parsers assume exactly one leading `"YYYY-MM-DD "` segment. D-14's structured
`businessDate`/`calendarDate` DTO fields must be added and both parsers re-pointed to them BEFORE
the label text itself gains a second (calendar-span) date — otherwise both break simultaneously.

### 5. The Excel allocation sheet's current clock-order column building (what D-13 changes)

```java
// Source: src/main/java/com/wfm/service/ScheduleExportService.java:703-712
Set<LocalTime> slotSet = new TreeSet<>(unfilledPerSlot.keySet());
int increment = incrementMinutes(dayEntries, window);
for (AgentScheduleEntry e : dayEntries) {
    for (AssignmentDetail a : e.assignments()) slotSet.add(a.startTime());
    for (BreakDetail b : e.breaks()) slotSet.addAll(slotStarts(b, increment, window));
}
...
List<LocalTime> slots = new ArrayList<>(slotSet);
```
`TreeSet<LocalTime>` uses `LocalTime`'s natural (clock) ordering — `00:00` sorts before `23:00`
regardless of the desk's anchor. D-13's fix is to order `slots` by `window.anchoredStartMinute(...)`
instead of relying on the set's natural order. Note `slotStarts` itself
(`src/main/java/com/wfm/service/ScheduleExportService.java:865-876`) already correctly uses
`window.anchoredStartMinute`/`anchoredEndMinute`/`anchoredToLocalTime` internally — only the final
column **ordering** is unanchored, not the per-break slot expansion.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|---------------|--------|
| `DayWindow.durationMinutes` threw on any interval that didn't run forward within one day | `DayWindow.anchoredDurationMinutes` wraps forward across the anchor instead of throwing | Phase 19 (`[VERIFIED: 19-VERIFICATION.md]`, merged before this phase) | OVNT-01's acceptance path already works; this phase's OVNT-01 work is message-only (D-02) and proof-only (D-18) |
| Every solver join keyed on `Timeslot.getDate()` (calendar date) | All 12 solver joins key on `Timeslot.getBusinessDate()`, structurally guarded by `BusinessDateJoinGuardTest` | Phase 20 (`[VERIFIED: 20-VERIFICATION.md]`, SC1, merged before this phase) | OVNT-02/03/04 already true server-side; no new join work needed in this phase |
| `DeskService.setDayStart` gated to `00:00`-only | Accepts any 15-minute boundary, refusing by name otherwise | Phase 20 (SC6, `[VERIFIED: 20-VERIFICATION.md]`) | The backend capability this phase makes operator-reachable for the first time via D-01's UI control |
| `TimeslotController.generateTimeslots` hardcoded `LocalTime.MIDNIGHT` | Reads `desk.getDayStart()` (tenant-scoped) | Phase 20 gap-closure (plan 20-09, `[VERIFIED: 20-VERIFICATION.md]`) | A prerequisite this phase's D-01 control depends on — already landed |

**Deprecated/outdated:** The nine midnight-implicit static `DayWindow` methods
(`startMinute`/`endMinute`/`durationMinutes`/etc.) are now `private` and unreachable from any caller
(`[VERIFIED: DayWindow.java, 19-VERIFICATION.md observable truth #1]`) — do not reference them even
in new test code; use the `anchored*` instance methods via `DayWindow.anchoredAt(dayStart)`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `agent_day_hours` is keyed by `DayOfWeek` so a Sunday-night shift correctly consumes Sunday's contracted-hours row, with no schema change needed (OVNT-04's foundation) | Phase Requirements (OVNT-04) | This is stated in `REQUIREMENTS.md` ("What research established" #5) but was not independently re-read against `AgentDayHours.java` this session. If wrong, OVNT-04 would need a schema or join change, not just a registry-flip proof. Low risk: 20-VERIFICATION.md's SOLV-04 pass (SLOT-mode overnight contracted hours, 2/2 tests) corroborates the same underlying mechanism. |
| A2 | The 16-hour maximum-span value (D-08) is the right magnitude | Locked Decisions, Claude's Discretion | D-08 itself flags this as adjustable "if evidence suggests a different bound" — no live desk data was reviewed this session to confirm 16h vs. some other value. Risk is low (any value between ~10h and ~20h would serve the stated purpose of catching 22-24h fat-finger typos while clearing real contracted days of ~8-9h) but the exact number is unverified against live desk data. |
| A3 | Migrating the two Erlang calendar-date range-delete callers (`calculateErlangC`/`calculateErlangX` in `StaffingRequirementService`) to business-date semantics belongs in this phase, since D-01 is what first makes non-midnight desks operator-reachable | Open Questions #1 | `bday-join-guard.md:169-170` explicitly hands this to "a Phase 21 owner … alongside OVNT-01" but 20-CONTEXT and 21-CONTEXT both record it as "raised, not decided." If deferred instead, the two Erlang paths remain a documented-but-dormant defect that becomes live the moment this phase ships D-01's editable control — same session, same desk. |

**If this table is empty:** N/A — three assumptions recorded above, all low-to-moderate risk.

## Open Questions

1. **Should this phase also migrate the two Erlang calendar-date range-delete callers
   (`StaffingRequirementService.calculateErlangC`/`calculateErlangX`)?**
   - What we know: `[VERIFIED: src/main/java/com/wfm/repository/StaffingRequirementRepository.java:75-76]`
     a comment explicitly names both methods as the two remaining calendar-date callers of the
     range-delete, confirmed by direct read: *"Calendar-date twin. SOLV-07/D-15: its two remaining
     callers (calculateErlangC and calculateErlangX in StaffingRequirementService) supply
     operator-facing calendar dates."* `[VERIFIED: src/test/resources/bday-join-guard.md:164-170]`
     explicitly hands them to "a Phase 21 owner … alongside OVNT-01," reasoning that both were
     "unreachable instances of the same latent defect" until a non-midnight desk became
     operator-creatable — which is exactly what D-01 does.
   - What's unclear: Neither CONTEXT.md nor this research settles whether fixing them is in-scope
     for Phase 21's plans or should be raised as a follow-up phase item.
   - Recommendation: Raise explicitly at plan-time as a scope decision (per CONTEXT.md's own Specific
     Ideas note: "Raise it at planning as an explicit scope decision — it was surfaced, not settled").
     Given D-01 is the event that turns this from latent to live, and the fix is localized (two call
     sites switching a date-range computation from calendar-date to business-date), including it in
     this phase's plan set is low-risk and closes a gap this phase itself creates. Deferring it means
     explicitly documenting it as a new, intentionally-accepted gap in this phase's own deferred-items
     list.

2. **Is 16 hours the right maximum-span value for D-08?**
   - What we know: 19-CONTEXT D-11 rejected putting a max-span bound inside `DayWindow`; D-08 proposes
     16h as "well clear of any real contracted day here (~8-9h) and far below the 22-24h values that
     are always typos."
   - What's unclear: No live desk's actual longest legitimate shift was measured this session to
     confirm 16h doesn't clip a real (if unusual) long shift.
   - Recommendation: Ship 16h as the default per D-08 (reversibility is `reversible` — "one condition;
     the 16h value is a constant") and flag it for confirmation if any live desk is found during
     implementation with a legitimate shift approaching that length.

3. **Does `ScheduleSummary` need `dayStart`, or only `ScheduleDetailResponse`?**
   - What we know: `ScheduleDetailResponse` already carries it (`[VERIFIED]`, see Recommended Project
     Structure note above); `ScheduleSummary` does not yet. The grid page polls `ScheduleSummary` on
     a faster cadence and `ScheduleDetailResponse` on a slower one.
   - What's unclear: Whether the grid's header-disclosure (D-17) needs the anchor available during the
     fast-poll phase, or can wait for the detail payload.
   - Recommendation: Left as Claude's Discretion per CONTEXT.md — the planner should decide based on
     whether D-17's header text needs to render before the first detail-payload fetch completes.

## Environment Availability

Skipped — this phase is entirely code/config changes to an existing, already-running Java/Spring/
Timefold backend and an existing React/Vite frontend. No new external tool, service, runtime, or CLI
dependency is introduced. `./gradlew compileJava compileTestJava` was run this session and exits
clean (0) against the current tree, confirming the baseline builds before this phase's work begins.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Backend framework | JUnit 5 + Spring Boot Test (existing, unchanged) |
| Backend config file | `build.gradle` (unchanged) |
| Backend quick run command | `./gradlew test --tests "com.wfm.service.ShiftTemplateServiceTest"` (or the specific new test class) |
| Backend full suite command | `./gradlew test` — **known to take ~10-13 minutes** (`19-VERIFICATION.md`: 10m14s/1138 tests; `20-VERIFICATION.md`: full `cleanTest test` run, 1217 tests) |
| Frontend framework | **None** — `frontend/package.json` has no `test` script, no vitest/jest dependency, zero `*.test.*`/`*.spec.*` files `[VERIFIED this session]` |
| Frontend quick run command | `cd frontend && npx tsc -b` (type-check only — this IS the test gate per D-15's compiler-forced design) |
| Frontend full suite command | Same as quick run; there is no broader automated suite |

**Known gradle hazards this phase's plans must account for (carried from project memory, applicable
here):**
- A filtered `--tests` run **deletes every other class's JUnit XML** — never read a suite-wide
  aggregate immediately after a filtered run; re-run the full suite or read only the filtered run's
  own fresh XML.
- An `UP-TO-DATE` gradle result fakes a green gate; `@Nested` test classes hide whole nested classes
  from a naive per-file XML read unless aggregated correctly.
- A stale gradle daemon can roughly double suite time; `./gradlew --stop` before a timing-sensitive
  run, and judge a run by worker CPU, not wall clock alone.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| OVNT-01 | `21:00`-anchored desk saves `22:00-06:00` with correct net hours; `00:00` desk gets D-02's new message | unit | `./gradlew test --tests "com.wfm.service.ShiftTemplateServiceTest"` | ✅ (693 lines, extend) |
| OVNT-01 (registry) | `MidnightBoundaryPropertyTest.ShiftCrossingMidnight#durationMinutesThrowsAndTheSavePathRefuses` flips and deregisters | unit (D-18 contract) | `./gradlew test --tests "com.wfm.service.MidnightBoundaryPropertyTest" --tests "com.wfm.support.MidnightBoundaryScenarioRegistryTest"` | ✅ (both exist) |
| OVNT-02/03 (registry) | `MidnightBoundaryRegressionTest.PtoOnAdjacentCalendarDate#attributionIsPerCalendarDateOnly` flips and deregisters | unit (D-18 contract) | `./gradlew test --tests "com.wfm.solver.MidnightBoundaryRegressionTest"` | ✅ |
| OVNT-04 (registry) | `MidnightBoundaryPropertyTest.ContractedHoursStartingWeekdayOnly#twoCalendarDatesDrawFromTwoIndependentWeekdayRows` flips and deregisters | unit (D-18 contract) | `./gradlew test --tests "com.wfm.service.MidnightBoundaryPropertyTest"` | ✅ |
| OVNT-05 | Overnight envelope outside `[bounds.startTime(), bounds.endTime()]` refused at save and at `requireShiftModeReady`; same-day case advisory only; empty-bounds case does not refuse | unit | `./gradlew test --tests "com.wfm.service.ShiftTemplateServiceTest" --tests "com.wfm.service.ShiftLibraryValidationServiceTest"` | ✅ (both exist, extend); new containment-predicate test class likely needed |
| OVNT-06 (Roster) | Overnight cell renders `"Sun 22:00-Mon 06:00"`; same-day cell byte-identical; legend restructured to vertical | unit | `./gradlew test --tests "com.wfm.service.ScheduleRosterExportTest"` | ✅ |
| OVNT-06 (Allocation) | Slot columns ordered by anchor offset; `00:00`-anchor output byte-identical to today | unit | `./gradlew test --tests "com.wfm.service.ScheduleAllocationExportTest"` | ✅ |
| OVNT-06 (UI grid) | Grid renders one contiguous run for an overnight shift in both SLOT and SHIFT modes | manual (backstop) — no frontend test runner | `npx tsc -b` (compile gate only) + manual UAT per UI-SPEC's backstop table | ❌ — no automated behavioral test possible on this surface; `browser_evaluate` geometry checks per project memory (Playwright screenshots do not settle on this app) |
| OVNT-07 | Violation DTO carries structured `businessDate`/`calendarDate`; both parsers re-pointed; label text then discloses the span | unit + format-pinning | `./gradlew test --tests "com.wfm.service.ScheduleOutputServiceShiftReportingTest"` | ✅ (exists, extend) |
| D-01/D-03/D-04 | Day-start control surfaces all 5 refusals; disabled render when ACCEPTED exists | unit (backend) + manual (frontend, no runner) | `./gradlew test --tests "com.wfm.service.DeskServiceDayStartTest"` (20 tests already, extend for D-03) | ✅ |
| D-05 | Non-blocking tiling advisory fires only when live timeslots exist and don't tile | unit | `./gradlew test --tests "com.wfm.service.DeskServiceDayStartTest"` or a new dedicated test | ✅ (extend) |

### Sampling Rate

- **Per task commit:** targeted `./gradlew test --tests "<TouchedClass>"` for backend changes;
  `cd frontend && npx tsc -b` for frontend changes (this is the only available frontend gate).
- **Per wave merge:** full `./gradlew test` (budget ~13 minutes per 19/20-VERIFICATION.md's measured
  times) plus the two registry guard tests (`MidnightBoundaryScenarioRegistryTest`,
  `BusinessDateJoinGuardTest` if touched) explicitly named, not assumed covered by the full run alone.
- **Phase gate:** Full suite green, plus `MidnightBoundaryScenarioRegistryTest` green with **zero**
  remaining registry entries (D-18's contract — the file's registry section should be empty after
  this phase, mirroring Plan 20-05's precedent of removing a registry row in the same commit that
  flips its assertion) before `/gsd-verify-work`.

### Wave 0 Gaps

- [ ] A new backend test class for the D-06/D-09 shared containment predicate (suggested name:
      `ShiftEnvelopeContainmentTest` or similar) — covers REQ OVNT-05; no such test exists yet since
      the predicate itself does not exist yet.
- [ ] `frontend/src/utils/dayWindow.ts` has no existing test file and, per D-15, is not expected to
      gain one (no frontend test runner) — its correctness rests entirely on the compiler-forced
      design plus manual verification against the UI-SPEC's backstop table. This is an accepted,
      documented gap, not an oversight — do not add a frontend test framework to cover it (see
      Deferred Ideas).
- [ ] No existing test drives `DeskManagement.tsx`'s newly-editable day-start control end-to-end
      (frontend has no runner); plan for explicit manual UAT steps per the UI-SPEC's "Resolved —
      backstop" table instead.

*(Framework install: none needed — `./gradlew test` already covers the backend; the frontend
deliberately stays on `tsc -b` only, per this phase's own locked decision D-15.)*

## Security Domain

`security_enforcement` is not set to `false` in `.planning/config.json` (absent = enabled).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Unchanged by this phase — no auth surface touched |
| V3 Session Management | No | Unchanged |
| V4 Access Control | Yes | `DeskService.setDayStart` already tenant-scopes via `TenantContext.getTenantId()` and `deskRepository.findByIdAndTenantId` `[VERIFIED: src/main/java/com/wfm/service/DeskService.java]` — D-01's new UI control must call the existing endpoint, not a new unscoped one. `TimeslotController.generateTimeslots` was fixed in Phase 20 to resolve the desk tenant-scoped rather than trusting a bare id (`20-VERIFICATION.md` SC6) — the same discipline applies to any new endpoint this phase might add. |
| V5 Input Validation | Yes | All new validation (D-02, D-03, D-06, D-08) extends the existing `IllegalArgumentException`/`ConflictException`/`PreSolveValidationException` pattern in `ShiftTemplateService`/`DeskService`/`ShiftLibraryValidationService` — no new validation framework, no hand-rolled regex/parsing beyond what `LocalTime`/`DayWindow` already provide |
| V6 Cryptography | No | Not touched by this phase |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cross-tenant desk access via an unscoped day-start/template endpoint | Elevation of Privilege | Every service method this phase touches already resolves the desk through `TenantContext.getTenantId()` + a tenant-scoped repository finder (`findByIdAndTenantId`/`findByTenantIdAndDeskId...`) — confirmed in `DeskService.setDayStart`, and the identical pattern was the actual fix for Phase 20's SC6 gap (`TimeslotControllerDeskAnchorTest` explicitly proves a cross-tenant desk id throws `EntityNotFoundException` before any row persists). Any new endpoint this phase adds (e.g., if D-03's refusal needs new surface) must follow the same tenant-scoping call shape. |
| Information disclosure via overly specific error messages | Information Disclosure | This codebase's existing pattern is to name specific blocking resources in refusal messages (e.g., D-04's schedule id + period) **to the authenticated operator of that tenant** — this is already the established UX pattern (switchSchedulingMode names uncovered demand windows) and is not a new disclosure risk since the data belongs to the same tenant viewing it. No change needed, but new messages this phase adds (D-02, D-03, D-08's max-span refusal) should follow the same "name the real desk-scoped resource, nothing cross-tenant" shape. |
| Denial of service via unbounded shift-template span | — (not a STRIDE category in the traditional sense, but a resource/consistency hazard) | D-08's fixed 16-hour cap exists specifically to prevent a fat-fingered 22-24h "shift" from later making Phase 22's minimum-rest constraint structurally unsatisfiable — this is the standard mitigation already decided; no additional control needed beyond implementing D-08 as specified. |

## Sources

### Primary (HIGH confidence — direct code read this session)

- `src/main/java/com/wfm/util/DayWindow.java` — full file read; confirms `anchoredDurationMinutes`
  does not throw on an overnight interval (BDAY-04 behaviour), and the nine midnight-implicit statics
  are `private`.
- `src/main/java/com/wfm/service/ShiftTemplateService.java` (validate, validateBands,
  validateGridAlignment, validateIdentityAndNonOverlap) — confirms D-02/D-06/D-08's exact predicate
  shapes and message text.
- `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` (grep of all `bounds.` accessor
  calls, `requireShiftModeReady` body, the "It will still save" advisory text) — confirms D-06's
  "endTime() is dead" claim and D-09's shared-predicate structure.
- `src/main/java/com/wfm/model/ShiftTemplate.java` (`getNetHours`), `AgentShiftAssignment.java` (full
  file) — confirms OVNT-01's arithmetic and resolves the "does `agent_shift_assignment` need its own
  `business_date` column" open decision (no — `date` IS the derived business date already).
- `src/main/java/com/wfm/service/ScheduleOutputService.java` (label concatenation at two sites,
  business-date grouping at three sites), `ScheduleExportService.java` (writeRoster, shiftCode,
  writeAllocationSheet, slotStarts, unfilledSeatsByDateAndSlot parser) — confirms D-11, D-12, D-13,
  D-14's exact current-state claims.
- `src/main/java/com/wfm/service/DeskService.java` (setDayStart full body),
  `TimeslotGeneratorService.java` (getLiveBounds, requireDayStartTiles, increment allowlist) —
  confirms D-01/D-03/D-04/D-05's precise refusal set and ordering, and surfaced the UI-SPEC copy
  discrepancy (Pitfall 1).
- `frontend/src/pages/DeskManagement.tsx` (day-start cells), `ScheduleResults.tsx` (timeDiffMinutes,
  the full-day-slot loop, the envelope-containment lexical check, the h4 header) — confirms D-01's
  and D-15's exact defect sites.
- `frontend/src/api/client.ts` (Desk, ScheduleDetail interfaces) — confirms the frontend TS types
  lack `dayStart` today.
- `src/main/java/com/wfm/dto/ScheduleDetailResponse.java`, `ScheduleSummary.java` — confirms the Java
  `dayStart` field already exists on the detail DTO but not the summary record (correction to
  CONTEXT.md's framing).
- `src/test/resources/midnight-boundary-scenarios.md`, `bday-join-guard.md`,
  `bday-02-write-paths.md` — confirms D-18's registry contract verbatim and the Erlang
  range-delete-callers finding verbatim.
- `.planning/phases/19-daywindow-re-anchoring/19-VERIFICATION.md`,
  `.planning/phases/20-solver-business-date-correctness/20-VERIFICATION.md` — both phases verified
  `passed` with 0 open gaps; used to confirm what landed before this phase starts.
- `./gradlew compileJava compileTestJava` — run this session, exits clean (0).

### Secondary (MEDIUM confidence)

- `.planning/REQUIREMENTS.md` item "What research established" #5 (`agent_day_hours` keyed by
  `DayOfWeek`) — cited from the project's own requirements doc, not independently re-read against
  `AgentDayHours.java` this session (see Assumption A1).

### Tertiary (LOW confidence)

- None — every substantive claim in this document was either verified by direct file read this
  session or is explicitly tagged `[ASSUMED]` in the Assumptions Log.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependency, nothing to verify beyond confirming the existing pins
  are untouched
- Architecture: HIGH — every pattern cited was read directly from the current tree this session, not
  inferred from CONTEXT.md's prose alone
- Pitfalls: HIGH — all five pitfalls are grounded in a verbatim code quote obtained this session,
  including one (Pitfall 1) that CONTEXT.md/UI-SPEC did not themselves catch

**Research date:** 2026-10-02
**Valid until:** This research is tied to the exact commit state verified this session
(`./gradlew compileJava compileTestJava` clean, Phase 19 and 20 both `passed` with 0 gaps). Re-verify
any quoted line numbers if significant unrelated work lands on this branch before planning completes
— the underlying architecture (DayWindow, business-date joins) is stable and unlikely to drift, but
exact line numbers in actively-edited files (`ScheduleResults.tsx`, `ScheduleOutputService.java`) may
shift.
