# Phase 21: Overnight Shift Templates - Pattern Map

**Mapped:** 2026-10-02
**Files analyzed:** 17 (13 modified, 2 new, 2 modified-with-caveats)
**Analogs found:** 17 / 17 (every file is either self-analogous via an existing sibling pattern in
the same file, or has a named precedent elsewhere in the tree — this phase is almost entirely
in-place edits, not new-file scaffolding, per RESEARCH.md's "wiring existing primitives" framing)

**Note on method:** CONTEXT.md and RESEARCH.md already did unusually deep measurement — exact line
numbers, exact predicate shapes, exact message strings — for every file this phase touches. This
document does not re-derive that; it restates it in the planner-facing shape (file → analog →
excerpt) and adds the two precedents RESEARCH.md referenced by name but did not quote in full
(`switchSchedulingMode`, the `StaffingTab`/`PtoTab` disabled-input precedent, `DeskServiceDayStartTest`'s
shape, `client.ts`'s mutation-call pattern).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/java/com/wfm/service/ShiftTemplateService.java` (`validate`) | service | request-response (validation) | itself — `anchoredIsForwardWithinDay` check immediately above the edit point | exact (in-place edit) |
| `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` (`requireShiftModeReady`, new containment predicate) | service | request-response (validation gate) | itself — `ShiftLibraryValidationService.covers` / existing `validate`→`requireShiftModeReady` two-caller pattern | exact |
| `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` (`generateSuggestion`) | service | request-response (report, read-only) | itself — existing `findAllLiveByDesk` + FTE filter shape | role-match (different call shape per Pitfall 5) |
| `src/main/java/com/wfm/service/DeskService.java` (`setDayStart`, 5th refusal) | service | request-response (CRUD guard) | itself — the 4 existing named refusals in the same method; `switchSchedulingMode` (sibling method, same class) | exact |
| `src/main/java/com/wfm/service/TimeslotGeneratorService.java` (`getLiveBounds` read, advisory) | service | request-response (advisory read) | itself — `requireDayStartTiles`'s existing increment-allowlist check | exact |
| `src/main/java/com/wfm/service/ScheduleExportService.java` (`writeRoster`/`shiftCode`, D-12) | service | batch (file I/O, Excel) | itself — `shiftCode`'s existing `anchoredEndMinute` handling | exact |
| `src/main/java/com/wfm/service/ScheduleExportService.java` (`writeAllocationSheet`/`slotStarts`, D-13) | service | batch (file I/O, Excel) | itself — `slotStarts`'s existing `anchoredStartMinute`/`anchoredEndMinute` usage (only the final `TreeSet` ordering is unanchored) | exact |
| `src/main/java/com/wfm/service/ScheduleExportService.java` (`unfilledSeatsByDateAndSlot`, D-14 parser) | service | transform (string→data parse) | `ScheduleResults.tsx`'s parallel parser (cross-stack sibling, must move together) | exact (paired) |
| `src/main/java/com/wfm/service/ScheduleOutputService.java` (label fields, D-14) | service | transform (DTO construction) | itself — the two duplicated concatenation sites at `:669-672` and `:771` | exact |
| `src/main/java/com/wfm/dto/TimeslotBoundsResponse.java` | model (DTO) | CRUD (no change — already has `endTime()`) | itself — record is complete; D-06 only adds a new reader | exact (no-op structurally) |
| `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` | model (DTO) | CRUD | itself — already carries `dayStart` (verified present, Phase 19) | exact (no-op) |
| `src/main/java/com/wfm/dto/ScheduleSummary.java` | model (DTO) | CRUD | `ScheduleDetailResponse.dayStart` field (sibling DTO, same concept) | exact |
| `frontend/src/utils/dayWindow.ts` (**new file**) | utility | transform (branded-type arithmetic) | `src/main/java/com/wfm/util/DayWindow.java`'s `anchoredAt`/`anchoredStartMinute`/`anchoredDurationMinutes` instance methods (cross-language port of an existing, exhaustively-tested pattern) | exact (ported, not invented) |
| `frontend/src/pages/ScheduleResults.tsx` (7 sites, D-15/D-16/D-17) | component | transform (grid rendering) | itself — each site is a local edit; the branded-type migration pattern is `19-CONTEXT D-04/D-06`'s removal of midnight-implicit `DayWindow` overloads | exact (established idiom) |
| `frontend/src/pages/DeskManagement.tsx` (day-start cell, D-01/D-04) | component | request-response (inline edit) | `StaffingRequirements.tsx:364-368`'s `readOnly disabled` input pattern; `DeskAgents.tsx`'s `disabled={savingCell}` loading-state precedent | exact |
| `frontend/src/api/client.ts` (`desks.setDayStart`, `Desk`/`ScheduleDetail` types) | provider (API client) | request-response | itself — `desks.setSchedulingMode` (immediately adjacent export, identical call shape) | exact |
| `src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java` + `midnight-boundary-scenarios.md` (D-18 registry flip) | test | event-driven (build-time registry assertion) | itself — Plan 20-05's precedent (SOLV-04 registry row removed in the same commit that flipped its assertion) | exact |

## Pattern Assignments

### `src/main/java/com/wfm/service/ShiftTemplateService.java` — `validate()` (D-02, D-06, D-08, D-09)

**Analog:** itself (lines immediately preceding the edit point) + `ShiftLibraryValidationService`'s shared-predicate pattern.

**Core pattern to extend — the existing anchor seam and refusal shape:**
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
**What changes (D-02):** only the thrown message text, to name `window`'s anchor:
`"Shift template end time must be after its start time — this desk's business day starts at
{dayStart}, so {startTime}–{endTime} would span two business days. Give the desk an overnight day
start to allow this."` (verbatim, UI-SPEC Copywriting Contract).

**What's added (D-08, max-span, NOT inside `DayWindow` per 19-CONTEXT D-11):** one more `if` block,
same shape as the one above, using `anchoredDurationMinutes` and a `private static final int
MAX_SPAN_MINUTES = 16 * 60;` constant — follow the existing `IllegalArgumentException` throw
convention, do not introduce a new exception type.

**What's added (D-06/D-09, containment):** a predicate method shared with
`ShiftLibraryValidationService.requireShiftModeReady` (see next section) — call it from here as a
third `if` block, reading `window`'s `[bounds.startTime(), bounds.endTime()]` via `getLiveBounds`,
same throw style. Empty-bounds (no generated timeslots) must not refuse — UI-SPEC's explicit
"Library validation / mode gate — empty" row.

---

### `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` — `requireShiftModeReady` + new containment predicate (D-06, D-09)

**Analog:** itself — the existing one-computation-two-callers shape, already used for demand
coverage and contracted-hours mismatch.

**Core pattern (the shape the new containment check must follow verbatim):**
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
**Pattern to apply:** add one more `ErrorDetail`-producing `if` block for D-06's containment finding,
inside the `else` branch alongside `uncoveredWindows`/`unsatisfiableWeekdays`. Put the actual
containment predicate (`[bounds.startTime(), bounds.endTime()]` test) in a method both this class and
`ShiftTemplateService.validate` call — "the report and the refusal can never disagree" (this class's
own javadoc, quoted in RESEARCH.md). D-07's advisory/blocking split (overnight = block, same-day =
advisory) is one branch inside this shared predicate, not two predicates.

**Dead field being activated (D-06):**
```java
// Source: src/main/java/com/wfm/dto/TimeslotBoundsResponse.java (full file)
public record TimeslotBoundsResponse(
        LocalDate periodStart, LocalDate periodEnd,
        LocalTime startTime, LocalTime endTime, int incrementMinutes
) {}
```
`bounds.endTime()` has zero call sites in this file today (confirmed by grep) — this predicate is
the first real consumer.

---

### `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` — `generateSuggestion` (D-10)

**Analog:** itself, but note the shape difference per Pitfall 5 — this is NOT a thin wrapper call.

```java
// Source: src/main/java/com/wfm/service/ShiftLibraryGenerationService.java:126-141
// generateSuggestion independently calls staffingRequirementRepository.findAllLiveByDesk
// and filters sr.getRequiredFTEs() > 0 — read-only, no shared validate() call today.
```
**Pattern to apply:** thread the D-06/D-09 shared predicate into the point where this method
constructs candidate envelope start/end times (clamp or filter candidates), not as a single gate at
the method's top — this method proposes times rather than validating a caller-supplied pair.

---

### `src/main/java/com/wfm/service/DeskService.java` — `setDayStart` (D-01, D-03, D-04)

**Analog:** itself — the four existing refusals already in this method, in order — plus
`switchSchedulingMode` (same class) as the worked precedent for "refuse and name the blocker."

**Core pattern — the refusal ordering and style to extend with a 5th refusal (D-03):**
Refusal order confirmed by CONTEXT.md D-01: (1) sub-minute precision, (2) non-15-minute boundary,
(3) unconditional ACCEPTED-schedule refusal naming id+period, (4) [generation-time,
`requireDayStartTiles`, different method]. D-03 adds a 5th: refuse when re-anchoring would strand an
already-stored template, naming it — same exception style (`IllegalArgumentException` /
`ConflictException`) and same "name the real desk-scoped resource" convention as refusal (3):
```java
// Source: src/main/java/com/wfm/service/DeskService.java (inside setDayStart), existing refusal (3)
throw new ConflictException("Desk has an accepted schedule (" + blocking.getId()
        + ", " + blocking.getPeriodStartDate() + " to " + blocking.getPeriodEndDate() + ")");
```
**IMPORTANT (Pitfall 1 / D-04 copy):** this is the ACTUAL string shape — note it is NOT the
UI-SPEC's quoted `"... with period {start}–{end}"` text. Any frontend Toast must render
`getErrorMessage(err)` (the caught backend message), never a hand-typed literal matching the
UI-SPEC's example string.

---

### `src/main/java/com/wfm/service/TimeslotGeneratorService.java` — D-05 advisory read

**Analog:** itself — `requireDayStartTiles`'s existing modulus check is the shape; `getLiveBounds`
already returns `incrementMinutes`, so no new repository read is needed.

```java
// Existing pattern, src/main/java/com/wfm/service/TimeslotGeneratorService.java:220-236 area
// requireDayStartTiles checks `dayStart % incrementMinutes` and refuses (blocking).
```
**Pattern to apply (D-05):** at save time in `DeskService.setDayStart`, call `getLiveBounds(deskId)`;
if non-null and `dayStart % incrementMinutes != 0`, surface a non-blocking warning (never a second
gate — save must still succeed). Do not touch `setDayStart`'s own fixed `% 15` modulus (Pitfall 4) —
that check is deliberately increment-independent.

---

### `src/main/java/com/wfm/service/ScheduleExportService.java` — Roster (D-12), Allocation (D-13), parser (D-14)

**Analog:** itself in all three cases — each change extends an existing, already-anchor-aware
sibling function in the same file.

**D-12, extend `shiftCode`'s existing anchor handling:**
```java
// shiftCode already uses window.anchoredEndMinute(ad.endTime()) — extend its overnight branch to
// render "Sun 22:00-Mon 06:00" per the locked UI-SPEC format, rather than inventing a new helper.
```
Column width change: `writeRoster:350-352` currently sets `16 * 256`; raise to `22 * 256` POI units.
Legend restructure: horizontal row of 8 cells → vertical, one pair per row (UI-SPEC explicit flag —
this is a layout change, not an append).

**D-13, reorder `slotStarts`/`writeAllocationSheet`'s existing TreeSet:**
```java
// Source: src/main/java/com/wfm/service/ScheduleExportService.java:703-712
Set<LocalTime> slotSet = new TreeSet<>(unfilledPerSlot.keySet());
...
List<LocalTime> slots = new ArrayList<>(slotSet);
```
Change: order `slots` by `window.anchoredStartMinute(...)` instead of relying on `TreeSet`'s natural
(clock) order. `slotStarts` itself already does per-break expansion correctly via
`anchoredStartMinute`/`anchoredEndMinute`/`anchoredToLocalTime` — only the final column **ordering**
is unanchored.

**D-14, the parser pair that must move together (backend + frontend, same commit sequencing):**
```java
// Source: src/main/java/com/wfm/service/ScheduleExportService.java:826-836
int space = label.indexOf(' ');
if (space < 0) continue;
String datePart = label.substring(0, space);
String timePart = label.substring(space + 1).trim();
```
```typescript
// Source: frontend/src/pages/ScheduleResults.tsx:386-391 — the SAME string, parsed independently
const spaceIdx = v.timeslotLabel.indexOf(' ')
if (spaceIdx < 0) continue
const vDate = v.timeslotLabel.substring(0, spaceIdx)
```
**Sequencing (Pattern 2, additive-first):** add `businessDate`/`calendarDate` fields to the violation
DTO, re-point BOTH parsers to the structured fields, confirm both pass, THEN change the label text
shape. Never land the text change in the same commit as the field addition.

---

### `src/main/java/com/wfm/service/ScheduleOutputService.java` — label construction (D-14)

**Analog:** itself — the two duplicated concatenation sites.
```java
// Source: src/main/java/com/wfm/service/ScheduleOutputService.java:771 (duplicated at :669-672)
String timeslotLabel = ts.getDate() + " " + ts.getStartTime() + "-" + ts.getEndTime();
```
Add `businessDate`/`calendarDate` as explicit fields on the violation DTO at both sites before
touching this concatenation's text.

---

### `src/main/java/com/wfm/dto/ScheduleSummary.java` — optional `dayStart` field (D-15, Claude's Discretion)

**Analog:** `ScheduleDetailResponse.java:23,241-242`'s existing `dayStart` field (already present,
Phase 19) — same type (`LocalTime`), same getter/setter shape, if added here too.
```java
// Source: src/main/java/com/wfm/dto/ScheduleDetailResponse.java:23,241-242 (pattern to mirror)
private LocalTime dayStart;
// ...getter/setter...
```

---

### `frontend/src/utils/dayWindow.ts` (NEW FILE) — D-15 branded `DayOffset` type

**Analog:** `src/main/java/com/wfm/util/DayWindow.java`'s instance methods — this is a deliberate
cross-language port of an existing, exhaustively-tested (62 tests, `DayWindowTest`) pattern, not a
new design.

```java
// Source: src/main/java/com/wfm/util/DayWindow.java:126-137 — the arithmetic being ported
public int anchoredDurationMinutes(LocalTime start, LocalTime end) {
    int startOffset = startMinuteFromDayStart(dayStart, start);
    int endOffset = endMinuteFromDayStart(dayStart, end);
    int raw = endOffset - startOffset;
    if (raw > 0) return raw;
    if (raw == 0) return 0;
    return raw + MINUTES_PER_DAY;
}
```
```typescript
// Source: frontend/src/pages/ScheduleResults.tsx:884-885 — the defect this file forecloses
function timeDiffMinutes(start: string, end: string): number {
  const [sh, sm] = start.split(':').map(Number)
  const [eh, em] = end.split(':').map(Number)
  return (eh * 60 + em) - (sh * 60 + sm)
}
// For start="22:00", end="06:00" this returns -960, not 480.
```
**Pattern to apply:** expose functions returning a branded `DayOffset` (e.g. `type DayOffset = number
& { __brand: 'DayOffset' }`) so `slot >= s` on a raw string becomes a compile error. Mirror
`DayWindow`'s `anchoredAt`/`anchoredStartMinute`/`anchoredEndMinute`/`anchoredDurationMinutes` method
names one-for-one (Claude's Discretion leans toward narrower surface covering only the 7 sites, but
name-mirroring keeps the Java/TS mental model aligned for future maintainers). This is the established
**compiler-forced migration** idiom — see 19-CONTEXT D-04/D-06's removal of midnight-implicit
`DayWindow` static overloads, which made every unsafe call site a build error the same way.

---

### `frontend/src/pages/ScheduleResults.tsx` — 7 sites (D-15), D-16, D-17

**Analog:** each site is a local, independent edit; the migration idiom itself is the analog
(19-CONTEXT D-04/D-06).

**The 7 confirmed sites (from RESEARCH.md, verified this session) — convert each through
`dayWindow.ts`, do not add a bespoke local fix to any one:**
```
:884  timeDiffMinutes            — returns -960 for 22:00→06:00
:668  `t < dayEnd` loop          — zero columns on a wrapping window
:441,:523,:741  `while (t < toHHMM(b.endTime))`  — break band loops zero times across anchor
:455,:672  `[...slots].sort()`   — lexical sort fragments 00:00–05:00 before 21:00
:681  `slot >= s && slot < e`    — overnight envelope matches nothing, renders grey
:653  `localeCompare` on starts  — shift groups order by clock, not anchor offset
```
**D-16:** apply anchored ordering to BOTH the SLOT and SHIFT render branches unconditionally — no
mode gate. SOLV-04 already proved SLOT mode can place an agent across midnight.

**D-17 header disclosure — analog is `<h4>{date}</h4>`'s existing (unstyled) usage, now requiring
explicit `fontWeight: 600` per UI-SPEC Typography rule (no browser-default-bold additions):**
```
"{date} (business day: {startAbbrev} {dayStart}–{endAbbrev} {dayStart})"
```
Suppressed entirely at a `00:00` anchor (render bare `{date}`, byte-identical to today).

---

### `frontend/src/pages/DeskManagement.tsx` — day-start cell (D-01, D-04)

**Analog:** `StaffingRequirements.tsx:364-368`'s disabled-input precedent, and the existing
`DeskManagement.tsx` per-row edit-mode Save/Toast wiring already used for every other mutation on
this page.

```tsx
// Source: frontend/src/pages/StaffingRequirements.tsx:364-368 — disabled read-only input precedent
<label>Period Start<input type="date" value={periodStart} readOnly disabled /></label>
<label>Period End<input type="date" value={periodEnd} readOnly disabled /></label>
<label>Start Time<input type="time" value={startTime} readOnly disabled /></label>
```
**Pattern to apply:** `DeskManagement.tsx:112`/`:124`'s read-only day-start cells become
`<input type="time" step="900" />` in edit mode (editable, not `readOnly disabled`) UNLESS an
ACCEPTED schedule blocks it (D-04), in which case render exactly the `StaffingRequirements.tsx` shape
— `readOnly disabled` plus the muted-grey (`#6b7280`, 13px) explanation text beneath it, per UI-SPEC's
locked copy: `"Locked — accepted schedule blocks day start. {scheduleId} ({periodStart}–{periodEnd})."`

**Error/Toast pattern — reuse, do not invent:**
Every existing `DeskManagement.tsx` mutation (create/update/delete) already routes failures through
the file's established `Toast('error', getErrorMessage(err))` call — D-01/D-03/D-04's refusals use
the identical call, never a hand-typed string (see Pitfall 1).

---

### `frontend/src/api/client.ts` — `desks.setDayStart`, type additions

**Analog:** `desks.setSchedulingMode` — an adjacent export in the same `desks` object, identical
shape (single-field PUT body).

```typescript
// Source: frontend/src/api/client.ts (desks export block, existing)
setSchedulingMode: (id: string, mode: 'SLOT' | 'SHIFT') =>
  request<Desk>(`/desks/${id}/scheduling-mode`, { method: 'PUT', body: JSON.stringify({ mode }) }),
setDayStart: (id: string, dayStart: string) =>
  request<Desk>(`/desks/${id}/day-start`, { method: 'PUT', body: JSON.stringify({ dayStart }) }),
```
`setDayStart` already exists in `client.ts` — confirm this call shape stays unchanged when D-03 adds
a 5th backend refusal (no client-side change needed, the generic `request<>` error path already
surfaces any thrown message).

**Type additions (D-15):** `Desk` interface (`client.ts:331` area) and `ScheduleDetail extends
ScheduleSummary` both need `dayStart`. Mirror the existing field style exactly — plain, un-nested,
same line as siblings:
```typescript
// Source: frontend/src/api/client.ts (existing Desk interface, pattern to extend)
export interface Desk { id: string; name: string; description?: string; defaultContractedHoursPerDay: number; schedulingMode: 'SLOT' | 'SHIFT'; dayStart: string }
```
`Desk.dayStart` already exists here — confirm during implementation; the gap is `ScheduleDetail`/
`ScheduleSummary`, not `Desk`.

---

### `src/test/java/com/wfm/support/MidnightBoundaryScenarioRegistryTest.java` + `midnight-boundary-scenarios.md` (D-18)

**Analog:** Plan 20-05 — SOLV-04's registry row was removed in the exact commit that flipped its
assertion, with the before-picture preserved in that plan's SUMMARY rather than left as a stale
registry row.

**Pattern to apply:** for each of the three rows at `midnight-boundary-scenarios.md:85-87`
(`PtoOnAdjacentCalendarDate#attributionIsPerCalendarDateOnly`,
`ContractedHoursStartingWeekdayOnly#twoCalendarDatesDrawFromTwoIndependentWeekdayRows`,
`ShiftCrossingMidnight#durationMinutesThrowsAndTheSavePathRefuses`): flip the test's assertion AND
delete its registry row in the same commit. `MidnightBoundaryScenarioRegistryTest` fails the build on
either a flip-without-deregistration or a stale entry (set-equality, both directions) — never land
these as two separate commits.

---

## Shared Patterns

### One-computation-two-callers validation (D-06, D-09)
**Source:** `ShiftLibraryValidationService.requireShiftModeReady` (lines 163-184, quoted above) +
`ShiftTemplateService.validate`
**Apply to:** the new containment predicate — one method, called from both; never duplicate the
`[bounds.startTime(), bounds.endTime()]` check.

### Additive-first, consume-second (D-14, D-15)
**Source:** Phase 18 (`ScheduleConfig` field), Phase 19 (`DayWindow` anchored methods alongside the
frozen oracle), Phase 20 (`Schedule.dayStart` one-commit consume)
**Apply to:** the violation DTO's `businessDate`/`calendarDate` fields (land + re-point both parsers
before changing label text) and `ScheduleDetailResponse`/`ScheduleSummary.dayStart` (land the field
before `ScheduleResults.tsx` reads it).

### Compiler-forced migration on a bounded surface (D-15)
**Source:** 19-CONTEXT D-04/D-06 — removing the midnight-implicit `DayWindow` static overloads made
every unsafe call site a build error.
**Apply to:** `dayWindow.ts`'s branded `DayOffset` type across `ScheduleResults.tsx`'s 7 sites — the
only structural guard available on a frontend with zero test infrastructure.

### Named refusal, never silent disagreement (D-02, D-03, D-04, D-08)
**Source:** `switchSchedulingMode` (existing precedent — refuses and names the uncovered demand
windows) and `DeskService.setDayStart`'s existing ACCEPTED-schedule refusal (names schedule id +
period).
**Apply to:** every new refusal this phase adds — D-02's anchor-naming message, D-03's
stranded-template refusal, D-08's max-span refusal. Always name the actual desk-scoped resource
causing the refusal; never leave the operator to guess.

### Toast as the sole error channel, rendered from the caught message (Pitfall 1)
**Source:** `DeskManagement.tsx`'s existing `Toast('error', getErrorMessage(err))` calls on every
current mutation.
**Apply to:** all five `setDayStart` refusals and the D-05 warning Toast. Never hardcode a literal
string matching the UI-SPEC's quoted example — render the backend's actual message.

### No-op-at-00:00 as the safety argument for shared rendering/ordering code (D-13, D-16)
**Source:** D-13's Excel allocation reorder, D-16's frontend ordering — both provable no-ops at a
`00:00` anchor because offset equals clock minute there.
**Apply to:** any new ordering logic this phase introduces; prefer "unconditional application,
provably identical at `00:00`" over a conditional branch gated on anchor value (D-16 explicitly
rejected the gated alternative).

## No Analog Found

None. Every file in CONTEXT.md's "Code this phase changes" list and RESEARCH.md's "Recommended
Project Structure" either already contains the pattern it needs to extend (in-place edit) or has a
named, quoted precedent elsewhere in the tree (new file: `dayWindow.ts`). The only two genuinely new
constructs — the D-06/D-09 shared containment predicate and the `dayWindow.ts` branded type — are
each explicitly instructed by CONTEXT.md to reuse an existing structural pattern rather than invent
one (D-09's class javadoc; D-15's cross-language port of `DayWindow`).

## Metadata

**Analog search scope:** `src/main/java/com/wfm/service/`, `src/main/java/com/wfm/dto/`,
`src/main/java/com/wfm/util/`, `frontend/src/pages/`, `frontend/src/api/`,
`src/test/java/com/wfm/`, `src/test/resources/` — all scoped by CONTEXT.md's canonical-refs
"Code this phase changes" list rather than a blind codebase scan, since that list was itself produced
by direct measurement during `/gsd-discuss-phase` and independently re-verified during
`/gsd-research-phase`.
**Files scanned:** 17 (all explicitly named in CONTEXT.md/RESEARCH.md) + 3 additional precedent files
confirmed this session (`StaffingRequirements.tsx`, `ShiftLibraryValidationService.java`'s
`switchSchedulingMode`, `client.ts`'s `desks` export block) that RESEARCH.md referenced by name
without quoting.
**Pattern extraction date:** 2026-10-02
