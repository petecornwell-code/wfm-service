# Phase 13: Per-Day Hours Visibility - Research

**Researched:** 2026-08-21
**Domain:** Internal codebase integration fix (Java/Spring Boot backend + React frontend) — no new external technology
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Placement — where per-day hours appear**

- **D-01:** The roster (`frontend/src/pages/DeskAgents.tsx`) gets an **expandable row per agent**
  showing the seven weekdays inline beneath them. Chosen over a side panel, tooltip, or separate
  view because the operator's task is a post-upload verification glance, and an expandable row
  lets several agents be compared without navigation. The table is already 13 columns wide, so
  seven additional top-level columns were rejected outright.
  — **Reversibility:** reversible — a self-contained UI affordance on one page.

- **D-02:** The **Excel export gains seven Mon–Sun columns** mirroring the upload template's shape
  and header order, sourced from `EnrichedColumnLayout` (which already exposes `dayHeader(day)`).
  This makes export → fill → re-upload a true round-trip, completing the intent behind Phase 10
  D-14's pre-seeded template. Rejected: locally-defined export headers (reintroduces exactly the
  drift UPL-09 exists to prevent) and a single summary column (breaks round-tripping).
  — **Reversibility:** costly — the export shape is an operator-facing file format; once
  operators round-trip against it, changing the column set breaks their saved workbooks.

**Cell rendering and editing**

- **D-03:** Each weekday cell is a **type-or-pick combo**: the operator can type any value the
  upload accepts (**0–24**, quarter-hour steps) **or** pick `PTO`, `MANDATORY`, or `Not set
  (default)` from the list. A plain integer dropdown was rejected on evidence — the upload clamps
  at **24** (`DeskAssignmentUploadService.java:693-695`, so `24.00` is reachable) and preserves
  fractions (`hours` is `precision=5, scale=2`; parser does `setScale(2, HALF_UP)` at `:697`),
  and the existing inline editor already uses `step="0.25"`. An integer list could not display an
  agent whose upload set `7.5`.
  — **Reversibility:** reversible — control shape only; the stored values are unchanged.

- **D-04:** The combo must expose **five distinct states**, because the model distinguishes them:
  a number (worked), `0` (explicitly not worked), `MANDATORY` (contractual day off), `PTO`
  (recurring weekly PTO), and **not set** (no row — falls back to the default per Phase 9 D-04).
  `Not set` is not a synonym for `0`; selecting a value must be what creates a row, so merely
  opening the combo never writes one.
  — **Reversibility:** reversible.

- **D-05:** Edits **save per cell**. One change writes exactly one `AgentDayHours` row and cannot
  disturb the other six. This is the structural property finding I-3 was missing — I-3 is closed
  by construction here, not by adding a guard to a multi-row write.
  — **Reversibility:** reversible.

**Default resolution**

- **D-06:** "Not set" resolves against **`Schedule.defaultContractedHoursPerDay`** — the same
  fallback `SolverService.resolveEffectiveHours` uses (`SolverService.java:925`) — **not**
  `Desk.defaultContractedHoursPerDay`, which is what the roster reads today
  (`DeskAgentService.java:49-50`). These are different entities holding different values, so
  keeping the desk fallback would leave a smaller version of the very bug this phase exists to
  fix. **Exactly one fallback rule survives this phase.**
  — **Reversibility:** costly — the point of the phase is that screen and solver agree; undoing
  this reintroduces a second, divergent resolution rule that a future audit would have to
  rediscover.

- **OPEN — for research:** the roster is scoped to a **desk**, not a schedule, so something must
  decide *which* schedule's default to read, and what to do when a desk has zero or several
  schedules. Deliberately left to the researcher/planner to resolve against the code rather than
  guessed at in discussion. Whatever is chosen must not introduce a second fallback rule (D-06).
  **→ Resolved by this research below, see "Resolving the D-06 open question."**

**Fan-out edit (Phase 9 D-10)**

- **D-07:** The existing single-value fan-out **survives as an explicit, labelled "set all days"
  bulk action** — no longer the only way to edit, and it **warns before overwriting** any
  `MANDATORY` / `PTO` labels. Phase 9's D-10 required this fan-out so operator edits keep reaching
  the solver; that intent is preserved rather than superseded, and setting a uniform 8h week stays
  a one-click operation. Rejected: removing it (makes a uniform week seven edits, supersedes D-10)
  and leaving it unchanged (leaves I-3 open).
  — **Reversibility:** reversible.

**Hygiene**

- **D-08:** `DeskAssignmentTemplateService.java:31-32` hardcodes `"Specialty 1"` / `"Specialty 2"`.
  Source them from `EnrichedColumnLayout`, which today exposes only the `specialtyIndex` detection
  regex (`:61`) and has no header constant or factory — so a small addition there is required.
  Closes audit finding I-4 (UPL-09 drift point).
  — **Reversibility:** reversible.

### Claude's Discretion

- Exact expand/collapse affordance, and whether the expanded row reuses the existing PTO-dates
  sub-table pattern already present in `DeskAgents.tsx`.
- Whether the summary in the collapsed `Hours/Day` cell is a range (`6–8`), a word, or a total —
  the accepted mockup showed a range with a single number when all days match, but the precise
  rendering is a planner/UI call.
- Response DTO shape for per-day hours. Phase 9 **D-12 deliberately deferred this** ("adding it
  now risks locking an API shape prematurely") — the shape is genuinely open and this phase is the
  first real consumer.
- Whether the "set all days" warning is a confirm dialog or an inline notice.

### Deferred Ideas (OUT OF SCOPE)

- **I-2 — manual "Refresh from BambooHR" bypasses the merge engine.** `BambooRefreshService`
  (`:224-234`) overwrites seven identity fields with no precedence rule and no `MergeReportEntry`;
  it holds zero references to `AgentMergeService`. Not a defect but a scoping decision: either
  route it through the merge engine, or document that MRG's provenance guarantee covers the upload
  path only. Needs its own discussion.
- **Retiring the `Agent.contractedHoursPerDay` scalar.** Once nothing reads it, removing it becomes
  tractable — but that is a migration touching five write sites, and Phase 9 D-05 deliberately kept
  it. A future cleanup phase.

</user_constraints>

<phase_requirements>
## Phase Requirements

No new requirement IDs. This phase closes v1.2 milestone-audit gaps I-1/F-1 (critical), I-3
(medium) and I-4 (low) against requirements that were already marked `Complete`:

| ID | Description | Research Support |
|----|-------------|------------------|
| MDL-02 | Agent stores contracted hours per day of week, replacing the single scalar; effective hours resolve per date from per-day values | Read-path fix: `DeskAgentService.toResponse` and `DeskAgentExportService` must resolve from `AgentDayHoursRepository`, not `Agent.getContractedHoursPerDay()`. See "The defect, verified" and "Reusable resolution logic" below. |
| UPL-03 | Numeric day cell `>= 0` is contracted hours, `0` = not worked | Verified parser semantics (`DeskAssignmentUploadService.java:687-697`) inform the five-state combo (D-04) and the export/roster rendering rules. |
| UPL-04 | Day cell `MANDATORY` marks a mandatory day off | `DayOffType.MANDATORY` (verified, `DayOffType.java:3-6`) must become visible in the roster and preserved by the per-cell edit path (D-05 closes I-3). |
| UPL-05 | Day cell `PTO` marks recurring weekly PTO | Same as UPL-04 — `DayOffType.PTO` must be visible and preserved. |
| UPL-09 | Template, parser, export share one column-layout definition (`EnrichedColumnLayout`) | I-4 closure: add a specialty-header factory to `EnrichedColumnLayout` and source both `DeskAssignmentTemplateService` and any new export columns from it, per D-08. |

</phase_requirements>

## Summary

This is not a greenfield feature — it is a **read-path integration fix plus one new edit path**,
entirely inside the existing Java/Spring Boot + React stack. No new library, framework, or
external service is introduced. All findings below come from reading the actual source files this
session (`DeskAgentService.java`, `SolverService.java`, `AgentDayHours.java`,
`AgentDayHoursRepository.java`, `DeskAgentResponse.java`, `DeskAgentExportService.java`,
`EnrichedColumnLayout.java`, `DeskAssignmentTemplateService.java`, `DeskAssignmentUploadService.java`,
`Schedule.java`, `Desk.java`, `ScheduleRepository.java`, `DayOffType.java`, `DeskAgentController.java`,
`DeskAgents.tsx`, `ClientManagement.tsx`, and the existing test suite) — not from training memory
or web search, because the defect and its fix are fully internal to this repository.

The root cause (I-1/F-1) is exactly as the audit describes: `DeskAgentService.toResponse`
computes `effective = a.getContractedHoursPerDay() != null ? a.getContractedHoursPerDay() :
deskDefault` and never touches `AgentDayHoursRepository`, even though that repository is already
injected into the class and used by the write path. The fix is mechanical: bulk-fetch
`AgentDayHours` rows per desk (mirroring the `pendingPtoRows` bulk-fetch already in
`listDeskAgentResponses`, and mirroring `SolverService`'s own `agentDayHoursMap` construction
pattern) and resolve each weekday from that map, falling back to a schedule default — never the
scalar and never `Desk.defaultContractedHoursPerDay`.

The genuinely open design work is: (1) the DTO shape for exposing per-day data (deliberately
deferred at Phase 9 D-12 — this phase is the first consumer), and (2) **which schedule's default
applies when a desk has zero, one, or many `Schedule` rows** (the CONTEXT.md "OPEN — for research"
item). This research resolves (2) below with a concrete, code-grounded recommendation. A third,
smaller decision — the new per-cell edit endpoint's validation range — is proposed as well since
CONTEXT.md's D-05 mandates the *shape* of the edit (one row, one cell) but not the endpoint's
input validation.

**Primary recommendation:** Bulk-fetch `AgentDayHours` per desk in `DeskAgentService`, resolve
each weekday against a schedule-derived default (never the scalar), and extend `DeskAgentResponse`
with a `Map<DayOfWeek, DayHoursEntry>`-shaped field that preserves the "no row = not set"
distinction so the frontend combo can render all five D-04 states without a second query.

## Resolving the D-06 open question: which schedule supplies the default?

**Verified from code this session** (`ScheduleRepository.java:1-29`):
```java
public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {
    List<Schedule> findByTenantIdAndDeskIdOrderByCreatedAtDesc(long tenantId, UUID deskId, Pageable pageable);
    Optional<Schedule> findByIdAndTenantIdAndDeskId(UUID id, long tenantId, UUID deskId);
    @Query("SELECT s FROM Schedule s WHERE s.tenantId = :tenantId AND s.deskId = :deskId " +
           "AND s.periodStartDate <= :endDate AND s.periodEndDate >= :startDate")
    List<Schedule> findOverlapping(long tenantId, UUID deskId, LocalDate startDate, LocalDate endDate);
    boolean existsByTenantIdAndDeskIdAndStatus(long tenantId, UUID deskId, com.wfm.model.ScheduleStatus status);
    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);
}
```
[VERIFIED: src/main/java/com/wfm/repository/ScheduleRepository.java:17-27]

And `Schedule` (`Schedule.java:34-35`, `:69`): `@Column(name = "desk_id", nullable = false) private
UUID deskId;` … `private BigDecimal defaultContractedHoursPerDay = new BigDecimal("8.00");`
[VERIFIED: src/main/java/com/wfm/model/Schedule.java:34-35,69]. `ScheduleStatus` (verified,
`ScheduleStatus.java:1-7`): `public enum ScheduleStatus { RUNNING, COMPLETED, STOPPED, FAILED,
ACCEPTED }` [VERIFIED: src/main/java/com/wfm/model/ScheduleStatus.java:1-7].

**There is no 1:1 desk↔schedule relationship in this codebase.** A desk can have zero, one, or
many `Schedule` rows (each a distinct solve run over its own period), confirmed by
`ScheduleService.listSchedules` (`ScheduleService.java:70-77`) which lists *all* schedules for a
desk ordered by `createdAt desc`, and additionally merges in an **unsaved, in-memory** schedule
from `InMemoryScheduleStore` that has no DB row at all until accepted
[VERIFIED: src/main/java/com/wfm/service/ScheduleService.java:78-88, quoted:
`inMemoryStore.getByDeskId(deskId).ifPresent(s -> { if (s.getTenantId() == tenantId) { merged.add(s); } })`].
So "the schedule for this desk" is not a well-defined singular concept anywhere else in the
codebase either — CONTEXT.md's "OPEN" flag is correct that no existing convention answers this.

**Recommendation for the planner:**
1. Query `scheduleRepository.findByTenantIdAndDeskIdOrderByCreatedAtDesc(tenantId, deskId,
   PageRequest.of(0, 1))` to get the desk's single most-recently-created **persisted** schedule
   (any status) and read its `defaultContractedHoursPerDay`.
2. Do **not** wire `InMemoryScheduleStore` into `DeskAgentService` — it has no dependency on that
   store today, and pulling it in couples a read-heavy roster listing endpoint to solve-in-progress
   state. This is a scope-narrowing choice, not a correctness guarantee: an unsaved/uncommitted
   solve's default will not be reflected on the roster until the schedule is persisted. Flag this
   residual gap to the operator/user rather than silently accepting it — **tagged `[ASSUMED]`**.
3. If the desk has **zero** persisted schedules, fall back to a **hardcoded `new
   BigDecimal("8.00")`** literal — the same value the `Schedule` entity's own field default uses
   — rather than `Desk.getDefaultContractedHoursPerDay()`. Falling back to the desk scalar would
   silently reintroduce the second, divergent fallback rule D-06 explicitly forbids ("exactly one
   fallback rule survives this phase").

This keeps D-06's guarantee intact for the common case (a schedule exists) while making the one
residual edge case (unsaved in-memory schedule) an explicit, documented limitation rather than a
silently-wrong number. **This entire "which schedule" strategy is a recommendation, not a decision
already locked in CONTEXT.md — it must be confirmed with the user or explicitly adopted by the
planner**, since CONTEXT.md deliberately left it open.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Resolve effective per-day hours for display | API / Backend (`DeskAgentService`) | — | Must match the solver's resolution rule (D-06); resolution logic belongs server-side, not duplicated in the browser. |
| Persist per-cell hours/label edit | API / Backend (new `DeskAgentService` method + `AgentDayHoursRepository`) | Database / Storage | D-05 requires exactly one row touched per edit — a backend transaction boundary, not a client-side concern. |
| Render expandable per-weekday row | Browser / Client (`DeskAgents.tsx`) | — | Pure UI affordance (D-01); no new server round trip needed beyond the already-extended roster response. |
| Excel column generation (Mon–Sun) | API / Backend (`DeskAgentExportService`) | — | POI workbook generation is server-side; header text sourced from `EnrichedColumnLayout` (shared with template/parser per D-13). |
| Combo-box state validation (0–24, quarter-hour, PTO/MANDATORY/not-set) | Browser / Client (input affordance) | API / Backend (authoritative validation) | Client renders D-03/D-04's five states; backend must re-validate range/precision on the new per-cell endpoint — never trust client-only validation for a persisted value. |
| Specialty header sourcing | API / Backend (`EnrichedColumnLayout` + `DeskAssignmentTemplateService`) | — | Compile-time-shared constant, not a runtime/UI concern (I-4/D-08). |

## Standard Stack

No new libraries are introduced by this phase. All work uses the existing, already-verified
project stack.

### Core (already in use — verified from `build.gradle` and `frontend/package.json`)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.4.2 | Backend framework | [VERIFIED: build.gradle:3] `id 'org.springframework.boot' version '3.4.2'` — already the project's pinned version. |
| Java toolchain | 21 | Language/runtime | [VERIFIED: build.gradle:10-14] `languageVersion = JavaLanguageVersion.of(21)`. |
| Apache POI (poi-ooxml) | 5.3.0 | Excel generation (template + export) | [VERIFIED: build.gradle:40] `implementation 'org.apache.poi:poi-ooxml:5.3.0'` — already used by `DeskAgentExportService` and `DeskAssignmentTemplateService`; the new Mon–Sun export columns use the same `XSSFWorkbook`/`Row`/`Cell` API already in both files. |
| React | ^19.0.0 | Frontend framework | [VERIFIED: frontend/package.json:8] — `DeskAgents.tsx` already uses hooks-only React 19 with no component library; new UI must match this convention (inline `style={{...}}` objects, no CSS framework). |
| react-router-dom | ^7.1.0 | Routing | [VERIFIED: frontend/package.json:10] — already used for `useParams`, no change needed. |
| TypeScript | ~5.7.0 | Frontend types | [VERIFIED: frontend/package.json:19] |
| Vite | ^6.1.0 | Frontend build | [VERIFIED: frontend/package.json:20] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 5 + AssertJ + Spring Boot Test | via `spring-boot-starter-test` | Backend tests | [VERIFIED: build.gradle:43] `testImplementation 'org.springframework.boot:spring-boot-starter-test'` — existing `@DataJpaTest` + `@Import` pattern (see Code Examples) is the established convention for service-level tests touching `AgentDayHoursRepository`. |
| H2 (in-memory) | via test scope | Test database | [VERIFIED: src/test/resources/application-test.yml:1-11] `url: jdbc:h2:mem:testdb`, `ddl-auto: create-drop`, `flyway.enabled: false` — schema is Hibernate-generated from entities in tests, not from Flyway migrations. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Server-resolved effective hours per weekday | Client-side resolution (frontend re-implements D-06's fallback logic) | Rejected — duplicates the solver's resolution rule in two places (JS and Java), which is precisely the class of drift this phase exists to eliminate. Server must be the single source of truth. |
| Bulk per-desk `AgentDayHours` fetch | Per-agent N+1 fetch inside the response-mapping loop | Rejected — `listDeskAgentResponses` already establishes the bulk-fetch-then-map convention for `AgentDayOff` (pendingPtoRows); `SolverService` does the same for `AgentDayHours` itself. Breaking this convention here would be inconsistent and slow at scale. |

**Installation:** None — no new dependencies. Confirm via:
```bash
./gradlew dependencies --configuration compileClasspath | grep -i poi
cd frontend && npm ls react react-router-dom
```

## Package Legitimacy Audit

**Not applicable.** This phase installs zero new external packages in any ecosystem (npm, Maven).
Every library referenced above is already declared in `build.gradle` / `frontend/package.json` and
was in production use before this phase began. No `package-legitimacy check` run was needed.

**Packages removed due to [SLOP] verdict:** none — no packages evaluated.
**Packages flagged as suspicious [SUS]:** none.

If a future planning pass introduces a genuinely new dependency (e.g., a combo-box/select
component library instead of a hand-rolled `<select>`), the Package Legitimacy Gate must be run
against it before it appears in a plan — see `<package_legitimacy_protocol>`.

## Architecture Patterns

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Browser (DeskAgents.tsx)                                                 │
│                                                                            │
│  Roster row (collapsed)                Roster row (expanded, D-01)       │
│  ┌──────────────────────┐              ┌──────────────────────────────┐ │
│  │ Hours/Day: "6-8" ▾    │──click──────▶│ Mon Tue Wed Thu Fri Sat Sun   │ │
│  └──────────────────────┘              │  8   8  MAND 8   4  PTO PTO   │ │
│                                          │  [combo per cell, D-03/D-04] │ │
│                                          │  [Set all days to: __] (D-07)│ │
│                                          └──────────────────────────────┘ │
└───────────────┬───────────────────────────────────┬──────────────────────┘
                │ GET /desks/{id}/agents             │ PUT .../day-hours/{day} (NEW, D-05)
                │ GET /desks/{id}/agents/export       │ PUT .../contracted-hours (existing fan-out, D-07)
                ▼                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ API / Backend                                                            │
│                                                                            │
│  DeskAgentController ──▶ DeskAgentService.listDeskAgentResponses          │
│                             │                                             │
│                             ├─ bulk fetch: agentDayHoursRepository        │
│                             │   .findByTenantIdAndDeskId(tenantId, deskId)│
│                             │   (mirrors SolverService's own bulk fetch)  │
│                             │                                             │
│                             ├─ resolve schedule default (D-06, see above) │
│                             │   scheduleRepository                       │
│                             │     .findByTenantIdAndDeskIdOrderByCreated  │
│                             │      DateDesc(tenantId, deskId, top-1)     │
│                             │                                             │
│                             └─ toResponse(): per-weekday map, NOT the     │
│                                 retired Agent.contractedHoursPerDay scalar│
│                                                                            │
│  DeskAgentController ──▶ DeskAgentExportService                          │
│                             └─ 7 new Mon–Sun columns, headers from        │
│                                EnrichedColumnLayout.dayHeader(day) (D-02) │
│                                                                            │
│  DeskAgentController ──▶ DeskAgentService.setDayHours (NEW, D-05)         │
│                             └─ upsert exactly ONE AgentDayHours row       │
│                                (find-by-agent+day, else create)           │
└─────────────────────────────────────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Database                                                                  │
│  agent_day_hours (agent_id, day_of_week, hours, day_off_type nullable)   │
│  — unchanged schema; already written by Phase 10's upload parser         │
└─────────────────────────────────────────────────────────────────────────┘
```

A reader can trace the primary use case (operator uploads → checks roster) by following: upload
writes `agent_day_hours` (Phase 10, unchanged) → roster GET bulk-fetches those rows → resolves
against the schedule default (not the scalar) → renders the five D-04 states in the expanded row.

### Recommended Project Structure

No new files/directories — this phase edits existing files in place:
```
src/main/java/com/wfm/
├── service/
│   ├── DeskAgentService.java          # toResponse fix (I-1) + new setDayHours (D-05) + setContractedHours warning (D-07)
│   ├── DeskAgentExportService.java    # +7 Mon–Sun columns (D-02)
│   └── DeskAssignmentTemplateService.java  # specialty headers sourced from EnrichedColumnLayout (D-08)
├── util/
│   └── EnrichedColumnLayout.java      # + specialty header factory (D-08)
├── dto/
│   └── DeskAgentResponse.java         # + per-weekday field (DTO shape, Claude's discretion)
├── repository/
│   └── AgentDayHoursRepository.java   # + findByAgent_IdAndDayOfWeek (new, for per-cell upsert)
└── controller/
    └── DeskAgentController.java       # + PUT .../day-hours/{day} endpoint (D-05)

frontend/src/
├── pages/DeskAgents.tsx               # expandable row (D-01), combo cells (D-03/D-04), set-all warning (D-07)
└── api/client.ts                      # DeskAgent type extension + new endpoint call
```

### Pattern 1: Bulk per-desk fetch + group-by-agent map (avoid N+1)

**What:** Fetch all `AgentDayHours` rows for a desk in one query, then group into a
`Map<UUID, Map<DayOfWeek, AgentDayHours>>` before the per-agent response-mapping loop.
**When to use:** Any time `DeskAgentService` needs per-agent per-day data across a whole desk
listing (roster load, export).
**Example (verified pattern, adapted from `SolverService`'s existing map-building code and
`DeskAgentService`'s existing `pendingPtoRows` bulk fetch):**
```java
// Source: src/main/java/com/wfm/service/SolverService.java:161, 249-253 (verified this session)
// — the existing convention for turning a bulk List<AgentDayHours> into a per-agent lookup map.
List<AgentDayHours> agentDayHours = agentDayHoursRepository.findByTenantIdAndDeskId(tenantId, deskId);
Map<UUID, Map<DayOfWeek, AgentDayHours>> agentDayHoursMap = new HashMap<>();
for (AgentDayHours h : agentDayHours) {
    agentDayHoursMap.computeIfAbsent(h.getAgent().getId(), k -> new HashMap<>())
            .put(h.getDayOfWeek(), h);   // keep the whole row (hours + dayOffType), not just BigDecimal
}
```
`DeskAgentService.listDeskAgentResponses` already does exactly this shape of bulk-fetch for
`AgentDayOff` at lines 55-63 [VERIFIED: src/main/java/com/wfm/service/DeskAgentService.java:55-63,
quoted: `List<AgentDayOff> pendingPtoRows = agentDayOffRepository.findByAgentDeskIdAndTypeAndStatusAndDateGreaterThanEqual(deskId, DayOffType.PTO, DayOffStatus.REQUESTED, LocalDate.now());`] —
the new day-hours fetch should sit alongside it, not replace it.

### Pattern 2: Reuse `SolverService.resolveEffectiveHours` — but only where a concrete date exists

**What:** `SolverService.resolveEffectiveHours` is `static` with **package-private** visibility
(no access modifier before `static`) [VERIFIED: src/main/java/com/wfm/service/SolverService.java:913-925,
quoted: `static BigDecimal resolveEffectiveHours(Map<LocalDate, BigDecimal> exceptionMap, Map<DayOfWeek, BigDecimal> dayHoursMap, LocalDate date, BigDecimal scheduleDefaultHours) { if (exceptionMap.containsKey(date)) { return exceptionMap.get(date); } DayOfWeek dow = date.getDayOfWeek(); if (dayHoursMap.containsKey(dow)) { return dayHoursMap.get(dow); } return scheduleDefaultHours; }`].
Because `DeskAgentService` lives in the same `com.wfm.service` package, it can call this method
directly with **no code change to `SolverService`** — confirmed by package co-location, not
assumed.

**When to use / when NOT to:** `resolveEffectiveHours` takes a concrete `LocalDate` and an
`exceptionMap` (`AgentException` rows, which are **date-scoped**, not weekday-scoped). The roster
and export views show a generic Mon–Sun *weekday* summary with **no concrete date or schedule
period** — there is no meaningful `AgentException` to look up for "next Wednesday" in the
abstract. **Recommendation: do NOT try to fold `AgentException` into the roster/export weekday
view.** Read the day-hours map directly per `DayOfWeek`, falling back to the schedule default:

```java
// Recommended pattern for the roster/export weekday summary — NOT a date-based resolution,
// so AgentException (date-scoped) is deliberately not consulted here.
BigDecimal resolveWeekdayForDisplay(Map<DayOfWeek, AgentDayHours> dayMap, DayOfWeek day, BigDecimal scheduleDefault) {
    AgentDayHours row = dayMap.get(day);
    return row != null ? row.getHours() : scheduleDefault;   // absent row = "not set" (D-04)
}
```
Reserve an actual call to `resolveEffectiveHours` for any future *date-based* view (there is none
in this phase's scope).

### Pattern 3: `@DataJpaTest` + `@Import` for service-level tests touching `AgentDayHoursRepository`

**What:** The existing test convention for testing a `@Service` against real (H2) repositories
without booting the full Spring context.
**When to use:** Any new backend test for `toResponse`'s fixed resolution, or the new
`setDayHours` per-cell edit method.
**Example (verified, existing file):**
```java
// Source: src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java:32-48 (verified this session)
@DataJpaTest
@Import(DeskAgentService.class)
@ActiveProfiles("test")
class DeskAgentServiceContractedHoursTest {
    @Autowired private DeskAgentService deskAgentService;
    @Autowired private AgentRepository agentRepository;
    @Autowired private DeskRepository deskRepository;
    @Autowired private AgentDayHoursRepository agentDayHoursRepository;
    private static final long TENANT_ID = 1L;
    // ... TenantContext.setTenantId(TENANT_ID) in @BeforeEach, .clear() in @AfterEach
}
```
A new test for the D-06 schedule-default resolution will additionally need `@Import` to include
`ScheduleRepository`-backed setup (create a `Schedule` row with a known
`defaultContractedHoursPerDay` and assert the roster response reflects it, not the desk default).

### Pattern 4: Amber warning-notice block (Phase 10 D-11 precedent) — for the D-07 "set all days" warning

**What:** The existing non-blocking warning UI already used for upload clamp/skip notices.
**Example (verified, existing file):**
```tsx
// Source: frontend/src/pages/ClientManagement.tsx:590-601 (verified this session)
{(uploadResult.warnings.length > 0 || uploadResult.skippedSheets.length > 0) && (
  <div style={{ marginTop: '0.75rem', padding: '0.5rem', background: '#fffbeb', border: '1px solid #fde68a', borderRadius: '4px' }}>
    <div style={{ fontWeight: 600, fontSize: '0.85rem', color: '#92400e', marginBottom: '0.25rem' }}>Warnings</div>
    <ul style={{ fontSize: '0.85rem', color: '#92400e', margin: 0, paddingLeft: '1.25rem' }}>
      {uploadResult.warnings.map((warning, idx) => <li key={`warning-${idx}`}>{warning}</li>)}
    </ul>
  </div>
)}
```
D-07's "set all days" bulk action should warn using this same `#fffbeb`/`#fde68a`/`#92400e` amber
palette before overwriting any `MANDATORY`/`PTO` label — reuse the palette values verbatim so the
new warning reads as the same UI language, not a new one.

### Correction to a CONTEXT.md claim (verified this session)

CONTEXT.md's `<code_context>` section states: *"PTO-dates sub-table in `DeskAgents.tsx` (`~:454`)
— an existing in-row detail pattern the expandable row can follow."* Having read the file this
session, **this is not accurate**: the days-off table at `DeskAgents.tsx:444-466` is a **fixed-position
modal overlay** (`position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)'`
[VERIFIED: frontend/src/pages/DeskAgents.tsx:446]), triggered by a "Days Off" button, not an
in-row expansion. There is **no existing in-row expandable pattern anywhere in this file** — D-01's
expandable row is a genuinely new UI affordance for this codebase, not a reuse of an existing one.
The planner should treat the modal as a *styling* reference (rounded card, `#fff` background) at
best, not a structural one. **This correction is itself `[VERIFIED: frontend/src/pages/DeskAgents.tsx:444-466]`,
overriding the un-verified claim in CONTEXT.md's code_context section.**

### Anti-Patterns to Avoid

- **Reintroducing `Desk.getDefaultContractedHoursPerDay()` as any part of the new resolution
  path.** D-06 is explicit that exactly one fallback rule (the schedule's) may survive. Even using
  it as a "fallback of the fallback" (e.g., when a desk has zero schedules) violates this — use a
  hardcoded literal matching the `Schedule` entity's own default instead (see resolution above).
- **Trusting `Agent.getContractedHoursPerDay()` to be null after an upload.** Five sites still
  write this scalar (`BambooRefreshService:244`, `DeskAgentService:143`/`:198`,
  `DeskAssignmentUploadService:561`, `DeskService:147`, all [VERIFIED] this session) — after a
  fresh BambooHR refresh it can be **non-null** again (`BambooRefreshService.java:244`:
  `agent.setContractedHoursPerDay(desk.getDefaultContractedHoursPerDay())` when it "is missing"
  [VERIFIED: src/main/java/com/wfm/integration/BambooRefreshService.java:244]). The fixed read
  path must never branch on this field's null-ness as a signal of anything.
- **A multi-row write for a single-cell edit.** D-05 requires exactly one `AgentDayHours` row
  touched. Do not reuse `setContractedHours`'s `deleteByAgent_Id` + recreate-all-seven pattern
  (`DeskAgentService.java:206-217`) for the new per-cell endpoint — that pattern is exactly what
  causes I-3 and must not be extended, only kept for the explicit D-07 bulk action.
- **Client-side resolution of the "not set" default.** The five-state combo must ask the server
  what the resolved value is (or the server must ship the resolved value alongside the raw row) —
  computing the schedule default in the browser duplicates D-06's rule in a second place.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Weekday → effective-hours fallback logic | A second Java or TypeScript implementation of "exception → per-day → schedule default" | `SolverService.resolveEffectiveHours` (reuse directly, same package) for any date-based need; a simple `dayMap.getOrDefault` read for the weekday-summary need (see Pattern 2) | ROADMAP-level constraint: "prefer reusing it over a second implementation." A second implementation is precisely the class of drift this phase exists to close. |
| Excel column header text for Mon–Sun | Local string literals `"Monday"`..`"Sunday"` in `DeskAgentExportService` | `EnrichedColumnLayout.dayHeader(day)` — already exists, already used by the parser's clamp-warning message | D-02/D-13: template, parser, and export must share one column-layout definition; this is the exact class of drift I-4 was raised against for specialty headers. |
| Formula/CSV injection guard for the new export columns | A new sanitizer for the Mon–Sun cell values | None needed — the new columns are `BigDecimal`/enum-derived values written by the export code itself, never raw operator-supplied strings, so `FormulaInjectionSanitizer` (already used for name/email/department/etc.) does not apply to them. Confirm during implementation that no string field is smuggled into these columns unsanitized. | Existing sanitizer already covers every operator-controlled string field (`DeskAgentExportService.java:82-92`, verified) — the day-hours columns are numeric/enum, a different risk class entirely. |

**Key insight:** Every "don't hand-roll" item in this phase is about **not duplicating a rule that
already exists once in this codebase** (the resolution rule, the column-header source, the
sanitizer) — there is no external library gap to fill.

## Common Pitfalls

### Pitfall 1: Forgetting `AgentDayHours` rows can be **present with `hours=0.00` and `dayOffType=null`** — a real, distinct state from "not set"
**What goes wrong:** Treating any row with `hours == 0` as "not set" and falling back to the
schedule default, silently turning an operator's explicit "does not work Tuesdays" into a default
8-hour day.
**Why it happens:** The upload parser writes a `0.00` row (not a missing row) for a bare numeric
`0` cell with no `MANDATORY`/`PTO` keyword [VERIFIED: src/main/java/com/wfm/service/DeskAssignmentUploadService.java:687-697,
quoted: `if (value.signum() < 0) { ... } if (value.compareTo(new BigDecimal("24")) > 0) { ... } return DayCellOutcome.ok(new DayCellResult(value.setScale(2, RoundingMode.HALF_UP), null, null));` —
a plain `0` follows this branch with `type=null`, producing a persisted `hours=0.00, dayOffType=null` row]. The model doc itself warns about this precisely: "absent = no row, not-worked = a `0.00` row" (Phase 9 D-09, cited in CONTEXT.md canonical refs).
**How to avoid:** Use `Map.containsKey(day)` / `dayMap.get(day) != null` to distinguish "no row"
from "row present with value 0.00" — never a null-or-zero check on the `hours` field itself.
**Warning signs:** A roster showing 8h for an agent whose upload explicitly zeroed a weekday.

### Pitfall 2: `MANDATORY` and `PTO` are also stored as `hours=0.00`, not `hours=null`
**What goes wrong:** Rendering the combo's "hours" sub-value for a `MANDATORY`/`PTO` cell as `0`
when the operator expects the label alone, or accidentally treating the `0.00` as a normal worked
value when computing a collapsed-row summary/range.
**Why it happens:** The parser stores `BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)` for both
keywords [VERIFIED: src/main/java/com/wfm/service/DeskAssignmentUploadService.java:673-680, quoted:
`if (raw.equalsIgnoreCase("MANDATORY")) { return DayCellOutcome.ok(new DayCellResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), DayOffType.MANDATORY, null)); } if (raw.equalsIgnoreCase("PTO")) { return DayCellOutcome.ok(new DayCellResult(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), DayOffType.PTO, null)); }`].
**How to avoid:** Always branch on `dayOffType` first, and only fall through to the numeric hours
display when `dayOffType == null`. This is exactly what `AgentDayHours.java`'s own Javadoc
documents: `dayOffType` is "Reporting/label metadata only" that layers on top of `hours`
[VERIFIED: src/main/java/com/wfm/model/AgentDayHours.java:32-40].
**Warning signs:** A collapsed-row summary computing "range 0–8" instead of recognizing the 0 as a
labelled day off.

### Pitfall 3: The scalar `Agent.contractedHoursPerDay` is a five-writer field — do not assume its state
**What goes wrong:** Any new code path that reads or branches on `Agent.getContractedHoursPerDay()`
(even indirectly, e.g., "if it's non-null, something else already handled hours") will observe
inconsistent state depending on upload-vs-refresh history.
**Why it happens:** Five call sites still write it and none were touched by Phase 9:
`BambooRefreshService.java:244` (sets to desk default when missing, quoted above),
`DeskAgentService.java:143` (`removeDeskAgent`, sets `null`),
`DeskAgentService.java:198` (`setContractedHours`, sets the new scalar value — kept for backward
compatibility / display of the "last uniform set" value),
`DeskAssignmentUploadService.java:561` (`clearDesk`, sets `null` on every re-import),
`DeskService.java:147` (desk-delete cleanup, sets `null`) — all [VERIFIED] this session by direct
line read.
**How to avoid:** This phase's new read path must derive effective hours **exclusively** from
`AgentDayHours` + the schedule default (per D-06) and must not consult the scalar at all for
*computing* the response — even though `DeskAgentResponse.contractedHoursPerDay()` may continue to
echo the raw scalar field for backward-compatible display (it already does today, see
`DeskAgentResponse.java:16`), that echoed value must not feed into `effectiveContractedHoursPerDay`
or the new per-weekday field.
**Warning signs:** A unit test that seeds only the scalar (no `AgentDayHours` rows) and expects the
new endpoint to still resolve hours from it — that test itself would be asserting the bug.

### Pitfall 4: `setContractedHours`'s existing fan-out silently destroys `dayOffType` labels — do not let the new "set all days" (D-07) inherit that silently
**What goes wrong:** D-07 requires the existing fan-out to survive as a labelled bulk action that
*warns* before overwriting `MANDATORY`/`PTO`. If the warning check is implemented as "any existing
rows present" rather than "any existing rows with `dayOffType != null`," every edit — even to an
all-numeric week — triggers a false warning, training operators to click through it and defeating
the point.
**Why it happens:** The current fan-out already always recreates all seven rows unconditionally
(`DeskAgentService.java:206-217`, verified) with `dayOffType` left `null`; adding a warning
requires a **new** pre-check the current code has no equivalent for.
**How to avoid:** Before performing the fan-out, query the agent's existing 7 rows and check
`.anyMatch(row -> row.getDayOffType() != null)` — only then surface the warning (per D-07,
confirm-dialog-or-inline-notice is Claude's discretion).
**Warning signs:** UAT feedback that the warning appears "every time," or that it never appears
even when a MANDATORY label existed.

### Pitfall 5: Precision mismatch between the new edit endpoint's input and the column's `precision=5, scale=2`
**What goes wrong:** An operator-typed value like `7.567` (typo, or a client rounding bug) getting
silently truncated or throwing a `DataException` at flush time instead of a clean 400 at the
controller boundary.
**Why it happens:** `AgentDayHours.hours` is `@Column(nullable = false, precision = 5, scale = 2)`
[VERIFIED: src/main/java/com/wfm/model/AgentDayHours.java:29-30] — same as `setContractedHours`'s
existing `BigDecimals.normalize()` call [VERIFIED: src/main/java/com/wfm/util/BigDecimals.java:14-19,
quoted: `public static BigDecimal normalize(BigDecimal value) { if (value == null) { return null; } return value.setScale(2, RoundingMode.HALF_UP); }`].
**How to avoid:** The new per-cell edit endpoint must call `BigDecimals.normalize()` (same utility,
already shared) before persisting, and must explicitly reject (400, not silently clamp) values
`< 0` or `> 24` — see the "New per-cell edit endpoint validation" recommendation below. Do not
silently clamp to 24 the way the *upload parser* does (that clamp exists because a bulk file
import can't practically reject one bad cell out of thousands without a warnings channel already
built for it — the single-cell edit path has no equivalent bulk-warnings UI and should just
reject). **This clamp-vs-reject choice is `[ASSUMED]`, not decided in CONTEXT.md — confirm with
the user or record explicitly as a planner decision.**
**Warning signs:** A 500 error from a `DataIntegrityViolationException` reaching the browser
instead of a clean validation message.

## Code Examples

### New per-cell edit endpoint — recommended shape

```java
// Recommended addition to AgentDayHoursRepository (mirrors existing deleteByAgent_Id convention
// at AgentDayHoursRepository.java:36, verified — a new finder for the upsert path):
Optional<AgentDayHours> findByAgent_IdAndDayOfWeek(UUID agentId, DayOfWeek dayOfWeek);
```

```java
// Recommended DeskAgentService method (D-05: exactly one row touched per edit).
// "value" carries either a BigDecimal (worked hours, incl. 0) or a DayOffType — the five-state
// combo (D-04) maps to: number -> hours w/ type=null; PTO/MANDATORY -> hours=0.00 w/ that type
// (mirrors the parser's own encoding at DeskAssignmentUploadService.java:673-680, verified);
// "not set" -> delete the row entirely rather than writing one (D-04: "selecting a value must be
// what creates a row").
@Transactional
public DeskAgentResponse setDayHours(UUID deskId, UUID agentId, DayOfWeek day,
                                      BigDecimal hours, DayOffType dayOffType, boolean clearRow) {
    // ... resolve tenant/agent as existing methods do (findByIdAndTenantIdAndDeskId) ...
    if (clearRow) {
        agentDayHoursRepository.findByAgent_IdAndDayOfWeek(agentId, day)
                .ifPresent(agentDayHoursRepository::delete);
    } else {
        BigDecimal normalized = BigDecimals.normalize(hours);
        if (normalized == null || normalized.signum() < 0 || normalized.compareTo(new BigDecimal("24")) > 0) {
            throw new IllegalArgumentException("Hours must be between 0 and 24");
        }
        AgentDayHours row = agentDayHoursRepository.findByAgent_IdAndDayOfWeek(agentId, day)
                .orElseGet(AgentDayHours::new);
        row.setTenantId(tenantId);
        row.setAgent(agent);
        row.setDayOfWeek(day);
        row.setHours(normalized);
        row.setDayOffType(dayOffType);   // null for a plain number, MANDATORY/PTO otherwise
        agentDayHoursRepository.save(row);
    }
    return toResponse(agent, resolvedScheduleDefault, List.of());
}
```

### `EnrichedColumnLayout` addition for I-4/D-08

```java
// Recommended addition, following the existing dayHeader(DayOfWeek) convention verbatim
// (EnrichedColumnLayout.java:46-49, verified):
public static String specialtyHeader(int index) {
    return "Specialty " + index;
}
```
`DeskAssignmentTemplateService.buildHeaders()` (`:117-125`, verified) would then call
`EnrichedColumnLayout.specialtyHeader(1)` / `specialtyHeader(2)` instead of its local
`SPECIALTY_1_HEADER`/`SPECIALTY_2_HEADER` constants.

## State of the Art

Not applicable — this is a same-session internal defect fix, not a technology migration. There is
no "old approach → current approach" industry timeline to document; the only "old approach" is
this codebase's own retired scalar, already covered under Common Pitfalls above.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | "Which schedule's default" should be the desk's single most-recently-created **persisted** `Schedule` (any status), excluding `InMemoryScheduleStore`, with a hardcoded `8.00` fallback when zero schedules exist. | Resolving the D-06 open question | If wrong, the roster could show a different default than an in-progress (unsaved) solve would use, or than an older ACCEPTED schedule the operator considers authoritative — CONTEXT.md deliberately left this open and it was not confirmed with the user in this research pass. |
| A2 | The new per-cell edit endpoint should **reject** (400) values `< 0` or `> 24` rather than silently clamping to 24 the way the bulk upload parser does. | Pitfall 5 | If the user actually wants clamp-with-warning parity with the upload path, rejecting instead will surface as "my edit didn't save" with no visible warning UI to explain why (the single-cell path has no warnings channel built for this phase). |
| A3 | `DeskAgentResponse` should carry a `Map<DayOfWeek, DayHoursEntry>`-shaped field (raw rows, absent key = not set) *and* a separately-resolved effective-value map, rather than one merged structure. | Recommended DTO shape (throughout) | This is explicitly "Claude's Discretion" per CONTEXT.md (D-12 deferred the shape) — a different shape (e.g., a flat 7-element array, or resolving everything server-side with no raw/effective split) would also satisfy the requirements; this is a design recommendation, not a verified requirement. |
| A4 | The "set all days" (D-07) warning trigger should check `dayOffType != null` on any of the agent's existing 7 rows, not merely "rows exist." | Pitfall 4 | If implemented as "rows exist," the warning will fire on every edit (false-positive), training operators to ignore it — defeating D-07's purpose. |

**None of these are compliance/security/retention claims** — they are internal design
recommendations flagged because CONTEXT.md explicitly deferred them to "Claude's Discretion" or
marked them "OPEN — for research." The planner should either adopt them explicitly (recording the
adoption as a plan-level decision) or route back through `/gsd-discuss-phase` if the user wants to
weigh in before locking.

## Open Questions

1. **Does the new per-cell edit endpoint need a distinct HTTP verb/path from the existing
   `PUT .../contracted-hours` fan-out, or should both live under one controller method with a
   discriminator?**
   - What we know: D-05 (per-cell) and D-07 (bulk fan-out, renamed/labelled) are two structurally
     different operations — one row vs. seven rows — that must both remain reachable.
   - What's unclear: Whether the planner wants two REST endpoints (e.g., `PUT
     .../day-hours/{day}` new, `PUT .../contracted-hours` kept as the explicit "set all days"
     action) or a single endpoint with a request-body discriminator.
   - Recommendation: Two endpoints — it keeps the "exactly one row" (D-05) and "warns, touches
     seven rows" (D-07) operations structurally distinct in the API surface, mirroring how they're
     already structurally distinct in the UI (single cell vs. bulk action button).

2. **Should `Agent.contractedHoursPerDay` still be updated as a side effect of the new per-cell
   edit, for backward-compat display purposes?**
   - What we know: `setContractedHours` (the existing fan-out) sets both the scalar and the seven
     rows. `DeskAgentResponse.contractedHoursPerDay()` still echoes the scalar today.
   - What's unclear: A single per-cell edit has no single "the contracted hours" value to write
     into a scalar field (the whole point is the days can now differ) — writing something there
     would be a guess (e.g., "most recent edited value" or "Monday's value").
   - Recommendation: Leave the scalar untouched by the new per-cell endpoint entirely. It already
     diverges from reality after every upload (I-1's root cause) — this phase's job is to stop
     *reading* it, not to keep it artificially in sync with a model it can no longer represent.

## Environment Availability

**Skipped** — this phase is entirely code/config changes within the existing, already-installed
project stack (Spring Boot 3.4.2 / Java 21 / POI 5.3.0 backend; React 19 / Vite 6.1 frontend, all
[VERIFIED] from `build.gradle` and `frontend/package.json` this session). No new external tool,
service, or runtime dependency is introduced.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Backend framework | JUnit 5 + AssertJ, via `spring-boot-starter-test` [VERIFIED: build.gradle:43] |
| Backend config | `src/test/resources/application-test.yml` — H2 in-memory, `ddl-auto: create-drop`, Flyway disabled [VERIFIED: src/test/resources/application-test.yml:1-11] |
| Backend quick run | `./gradlew test --tests "com.wfm.service.DeskAgentService*"` |
| Backend full suite | `./gradlew test` |
| Frontend framework | **None installed.** [VERIFIED: frontend/package.json:1-21 — no `vitest`, `jest`, `@testing-library/*`, or `test` script present in `scripts` or `devDependencies`.] Confirmed by `find frontend -iname "*.test.*"` returning zero results this session. |

### Phase Requirement → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MDL-02 / I-1 | Roster response resolves effective hours from `AgentDayHours`, not the scalar | unit (backend) | `./gradlew test --tests "com.wfm.service.DeskAgentServiceReadPathTest"` | ❌ Wave 0 — new test file needed |
| MDL-02 / I-1 (export) | Excel export's Mon–Sun columns and effective-hours column resolve from `AgentDayHours` | unit (backend) | `./gradlew test --tests "com.wfm.service.DeskAgentExportServiceTest"` | ❌ Wave 0 — no `DeskAgentExportServiceTest.java` found in `src/test` this session |
| UPL-04/UPL-05 / I-3 | Per-cell edit (D-05) touches exactly one row and never wipes the other six | unit (backend) | `./gradlew test --tests "com.wfm.service.DeskAgentServiceDayHoursTest"` | ❌ Wave 0 — new test file needed |
| D-07 | "Set all days" warns before overwriting MANDATORY/PTO, still fans out on confirm | unit (backend) | extend `DeskAgentServiceContractedHoursTest.java` (existing) | ✅ existing file, needs new test methods |
| UPL-09 / I-4 | Specialty headers sourced from `EnrichedColumnLayout`, not hardcoded | unit (backend) | `./gradlew test --tests "com.wfm.service.DeskAssignmentTemplateServiceTest"` | ✅ existing file (168 lines, verified present) — extend, don't replace |
| D-01/D-03/D-04 (frontend rendering) | Expandable row renders 5 states correctly | manual-only (no frontend test framework) | N/A — see Wave 0 gap below | ❌ |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "com.wfm.service.DeskAgent*"`
- **Per wave merge:** `./gradlew test` (full backend suite — 54 test files as of this research,
  [VERIFIED: `find src/test -name "*.java" | wc -l` = 54 this session])
- **Phase gate:** Full backend suite green before `/gsd-verify-work`; frontend changes verified via
  manual UAT walkthrough (upload → roster → expand row → edit cell → re-check) since no frontend
  test framework exists.

### Wave 0 Gaps
- [ ] `src/test/java/com/wfm/service/DeskAgentServiceReadPathTest.java` (or extend
  `DeskAgentServiceContractedHoursTest.java`) — covers MDL-02/I-1's read-path fix, asserting the
  roster resolves from `AgentDayHours` even when the scalar is null/stale/non-null-but-wrong.
- [ ] `src/test/java/com/wfm/service/DeskAgentExportServiceTest.java` — **does not exist today**
  [VERIFIED: no match for `find src/test -iname "*DeskAgentExport*"` this session] — needed to
  cover the new Mon–Sun export columns and the effective-hours export column fix.
- [ ] `src/test/java/com/wfm/service/DeskAgentServiceDayHoursTest.java` — covers the new per-cell
  edit endpoint (D-05): single-row upsert, "not set" deletes the row, range/precision validation.
- [ ] **Frontend test framework decision.** No `vitest`/`jest`/`@testing-library` exists in this
  project at all. This phase's frontend surface (D-01 expandable row, D-03/D-04 five-state combo)
  is the first UI in this codebase complex enough to plausibly warrant one — but installing a
  frontend test framework is itself a `[SUS]`-worthy new-dependency decision (see Package
  Legitimacy Gate) that the planner must weigh: either (a) install `vitest` +
  `@testing-library/react` and add component tests, or (b) rely on manual UAT only, matching every
  other page in this codebase today. **Recommendation: (b)** — matches the existing project
  convention (zero frontend tests anywhere), keeps this phase's blast radius to the stated scope,
  and a UI-safety-gate manual walkthrough is already part of this project's workflow
  (`ui_safety_gate: true` in `.planning/config.json`, verified). Revisit only if the user wants to
  change the project-wide convention, which is out of scope for this phase.

## Security Domain

`security_enforcement` is absent from `.planning/config.json` — treated as enabled per protocol.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Unchanged by this phase — existing `X-Tenant-ID` header / `TenantContext` mechanism is out of scope. |
| V3 Session Management | No | Unchanged. |
| V4 Access Control | Yes | New `setDayHours` method must use the same `findByIdAndTenantIdAndDeskId(...).orElseThrow(EntityNotFoundException)` tenant-scoping pattern already used by every other write method in `DeskAgentService` (`setContractedHours`, `setSpecializations`, `removeDeskAgent` — all verified this session). No new access-control mechanism needed; just don't skip the existing one. |
| V5 Input Validation | Yes | New per-cell endpoint must validate hours range (0–24) and precision (scale 2, `BigDecimals.normalize`) server-side — never trust the client combo's `step="0.25"`/dropdown constraint alone (see Pitfall 5). `DayOffType` must be validated against the enum (Jackson will already reject an invalid string for an `@Enumerated(EnumType.STRING)` field via 400, but confirm the controller returns a clean error, not a raw stack trace). |
| V6 Cryptography | No | Not touched by this phase. |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cross-tenant data leak via a missing tenant filter on the new `findByAgent_IdAndDayOfWeek` repository call | Information Disclosure | The repository method itself is not tenant-scoped (mirrors the existing `deleteByAgent_Id` at `AgentDayHoursRepository.java:36`, which is also not tenant-scoped) — the caller (`DeskAgentService`) MUST resolve the `Agent` via a tenant-scoped lookup first (`findByIdAndTenantIdAndDeskId`) and only then use the already-verified `agentId`, exactly as `setContractedHours` does today (`DeskAgentService.java:191`, verified). Do not call `findByAgent_IdAndDayOfWeek` with a client-supplied `agentId` that hasn't passed through a tenant-scoped resolve first. |
| Formula/CSV injection in the new Excel export columns | Tampering | Not applicable to the new Mon–Sun columns themselves (numeric/enum-derived values, not operator strings) — see "Don't Hand-Roll" table above. Confirm during implementation no string concatenation smuggles raw operator input into these cells. |
| Over-permissive hours value (e.g., negative, or absurdly large before scale normalization) causing a `DataIntegrityViolationException` (500) instead of a clean validation error | Denial of Service (minor) / Information Disclosure (stack trace leak) | Explicit range check (0–24) and `BigDecimals.normalize()` call before `save()`, mirroring `setContractedHours`'s existing `signum() < 0` check (`DeskAgentService.java:194-197`, verified) — extend that same pattern to also reject `> 24` rather than clamp (Pitfall 5 / Assumption A2). |

## Sources

### Primary (HIGH confidence — direct file reads this session, all [VERIFIED] tags above cite exact paths/lines)
- `src/main/java/com/wfm/service/DeskAgentService.java` (full file, 222 lines) — the I-1 defect site and existing fan-out/edit patterns.
- `src/main/java/com/wfm/service/SolverService.java` (targeted reads: lines 130-260, 560-600, 720-850, 895-935) — `resolveEffectiveHours`, the bulk-fetch + map-building convention, and confirmation of its package-private visibility.
- `src/main/java/com/wfm/model/AgentDayHours.java`, `DayOffType.java`, `Schedule.java`, `Desk.java`, `ScheduleStatus.java` (full files) — schema/entity ground truth.
- `src/main/java/com/wfm/repository/AgentDayHoursRepository.java`, `ScheduleRepository.java` (full files) — existing query surface, confirming no "current schedule for desk" convention exists elsewhere.
- `src/main/java/com/wfm/dto/DeskAgentResponse.java` (full file) — current DTO shape, confirming no per-day field exists yet.
- `src/main/java/com/wfm/service/DeskAgentExportService.java`, `DeskAssignmentTemplateService.java` (full files) — export/template column-generation patterns and the I-4 hardcoded-header site.
- `src/main/java/com/wfm/util/EnrichedColumnLayout.java`, `BigDecimals.java` (full files) — shared column-layout source and the normalization utility to reuse for the new endpoint.
- `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` (targeted reads: lines 500-600, 637-701) — `parseDayCell`'s exact encoding of numeric/MANDATORY/PTO/clamp semantics, and `clearDesk`'s scalar-nulling behavior.
- `src/main/java/com/wfm/controller/DeskAgentController.java` (full file) — existing endpoint surface to extend.
- `src/main/java/com/wfm/service/ScheduleService.java` (targeted read: lines 1-110) — confirmed no 1:1 desk↔schedule relationship, and the existence of `InMemoryScheduleStore` as an unsaved-schedule source.
- `src/main/java/com/wfm/integration/BambooRefreshService.java`, `src/main/java/com/wfm/service/DeskService.java` (targeted reads around the cited line numbers) — the remaining scalar writers.
- `frontend/src/pages/DeskAgents.tsx` (full file, 469 lines) — current roster UI, the modal-not-inline days-off pattern (correcting CONTEXT.md), and the existing inline-edit conventions.
- `frontend/src/pages/ClientManagement.tsx` (targeted reads around lines 230-630) — the existing amber warning-notice pattern to reuse for D-07.
- `frontend/src/api/client.ts` (targeted reads around lines 140-300) — current `DeskAgent` TS interface and API call shapes.
- `frontend/package.json`, `build.gradle` (full files) — stack version verification and confirmation of zero frontend test tooling.
- `src/test/java/com/wfm/service/DeskAgentServiceContractedHoursTest.java`, `DeskAssignmentTemplateServiceTest.java` (existence + content) — existing test conventions to extend.
- `src/test/resources/application-test.yml` — test database configuration.
- `.planning/phases/13-per-day-hours-visibility/13-CONTEXT.md`, `.planning/REQUIREMENTS.md`, `.planning/STATE.md`, `.planning/v1.2-MILESTONE-AUDIT.md`, `.planning/config.json` — upstream planning artifacts (all read in full this session).

### Secondary (MEDIUM confidence)
- None — this phase required no external documentation lookup; every claim traces to a source-of-truth file read this session.

### Tertiary (LOW confidence)
- None.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; every version claim verified against `build.gradle`/`package.json` this session.
- Architecture: HIGH — the defect, the resolution logic to reuse, and the existing bulk-fetch pattern are all directly read from source, not inferred.
- Pitfalls: HIGH — every pitfall traces to a specific verified line range and, where relevant, a verbatim quote of the parser logic that produces the edge case.
- The D-06 "which schedule" resolution: MEDIUM — the mechanism (query + fallback literal) is grounded in verified repository/entity code, but the *choice* of "most-recently-created, DB-persisted only" is a recommendation (tagged `[ASSUMED]`, Assumption A1), not a value read from an existing convention, because no such convention exists in this codebase to read.

**Research date:** 2026-08-21
**Valid until:** 2026-09-20 (30 days — stable internal codebase, no fast-moving external dependency)
