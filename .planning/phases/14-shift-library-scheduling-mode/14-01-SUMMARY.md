---
phase: 14-shift-library-scheduling-mode
plan: 01
subsystem: api
tags: [jpa, spring-boot, flyway, react, desk-scoped-crud, tracer]

# Dependency graph
requires: []
provides:
  - shift_template table (id, tenant_id, desk_id, name, start_time, end_time, break_offset_minutes, break_duration_minutes, valid_weekdays, effective_from, effective_to)
  - desk.scheduling_mode column (SLOT/SHIFT, NOT NULL DEFAULT SLOT)
  - ShiftTemplate entity/repository/service/controller/DTOs (create + list only)
  - GET/POST /api/v1/desks/{deskId}/shift-templates
  - ShiftLibrary.tsx page reachable at /desks/:deskId/shift-library
  - Checkpoint decision (D-11 non-overlap mechanism): app-level, recorded below
affects: [14-shift-library-scheduling-mode (later plans in this phase), 15-shift-envelope-coupling, 16-usual-shift-storage]

actuals:
  tokens: 46000
  tasks: 2
  commits: 1

tech-stack:
  added: []
  patterns:
    - "Desk-scoped CRUD entity/repository/service/controller mirroring Specialization (tenant_id + desk_id FK, unique constraint, plain accessors, no bean validation)"
    - "Fixed-position CHAR(7) Monday-first weekday mask, translated to/from Set<DayOfWeek> via @Transient derived accessors invisible to Hibernate (field access mode)"
    - "Fixed break-offset-from-shift-start model (D-01) with derived breakStartTime/breakEndTime/netHours computed server-side, never stored"
    - "@DataJpaTest + @Import({Service.class, Controller.class}) end-to-end tracer test — no MockMvc harness, calls controller methods directly against H2"

key-files:
  created:
    - src/main/resources/db/migration/V39__add_shift_template_and_scheduling_mode.sql
    - src/main/java/com/wfm/model/SchedulingMode.java
    - src/main/java/com/wfm/model/ShiftTemplate.java
    - src/main/java/com/wfm/repository/ShiftTemplateRepository.java
    - src/main/java/com/wfm/service/ShiftTemplateService.java
    - src/main/java/com/wfm/controller/ShiftTemplateController.java
    - src/main/java/com/wfm/dto/ShiftTemplateRequest.java
    - src/main/java/com/wfm/dto/ShiftTemplateResponse.java
    - src/test/java/com/wfm/service/ShiftTemplateTracerTest.java
    - frontend/src/pages/ShiftLibrary.tsx
  modified:
    - src/main/java/com/wfm/model/Desk.java
    - frontend/src/api/client.ts
    - frontend/src/pages/DeskAgents.tsx
    - frontend/src/App.tsx

key-decisions:
  - "Checkpoint (Task 1, gate=blocking-human): D-11's same-name effective-range non-overlap invariant is enforced application-level in ShiftTemplateService, NOT a Postgres EXCLUDE USING gist constraint and NOT both. The unique key is (tenant_id, desk_id, name, effective_from), verbatim. Portable across H2/@DataJpaTest and Postgres; no CREATE EXTENSION btree_gist in a one-way migration; named operator-readable errors via ConflictException rather than a constraint-violation stack trace; matches the codebase's standing idiom (SpecializationService validates manually in the service layer). Accepted cost: a direct SQL INSERT bypassing the service can create overlapping eras — consistent with this project's existing trust model (multi-tenancy is application-enforced with no DB row security)."
  - "The overlap CHECK itself is not implemented in this plan — the tracer's createShiftTemplate deliberately does only the minimum this slice needs (null/blank name, duplicate identity key). Full field validation, the grid check, and the era/overlap rules are 14-03's work per the plan's own action text; leaving them out here is a scoped functionality gap, not a re-litigation of the checkpoint's architectural choice."
  - "Exported DAY_ORDER/DAY_LABELS from DeskAgents.tsx (previously module-private) so ShiftLibrary.tsx could import rather than re-declare, per the plan's own read_first instruction — see Deviations."

patterns-established:
  - "ShiftTemplate is a structural sibling of Specialization: own table, desk_id FK, no nesting inside Desk — the pattern later Phase 14 plans and Phase 15/16 tables should continue."
  - "Derived-value accessors (getBreakStartTime/getBreakEndTime/getNetHours) marked @Transient on a field-access entity so Hibernate infers no column — safe pattern for any future computed-from-stored-fields property."

requirements-completed: [SHLB-01, MODE-01]

coverage:
  - id: D1
    description: "An operator can POST one shift template to /api/v1/desks/{deskId}/shift-templates, receive it back from GET on the same path, and see it rendered in the Shift Library page's table."
    requirement: SHLB-01
    verification:
      - kind: integration
        ref: "src/test/java/com/wfm/service/ShiftTemplateTracerTest.java#createAndList_returnsSubmittedTemplate"
        status: pass
    human_judgment: true
    rationale: "Backend round-trip is proven by the tracer test (@DataJpaTest, controller->service->repo->H2). The frontend rendering half (the created row actually appearing in the ShiftLibrary.tsx table) has no frontend test harness in this codebase (14-RESEARCH.md Pitfall 4) — proven only by npm run build exiting 0 plus source assertions, per P-04. Visual/functional confirmation is the plan's own <human-check>."
  - id: D2
    description: "desk.scheduling_mode exists, is NOT NULL DEFAULT 'SLOT', and Desk.schedulingMode's Java default agrees with the SQL default so every pre-existing and newly-created desk reads SLOT (MODE-01)."
    requirement: MODE-01
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateTracerTest.java#desk_savedWithoutModeSet_readsBackAsSlot"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-01's break-offset model: a template submitted with startTime 08:00, endTime 17:00, breakOffsetMinutes 240, breakDurationMinutes 60 returns breakStartTime 12:00, breakEndTime 13:00, netHours 8.00 — the worked example."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateTracerTest.java#create_derivesBreakWindowAndNetHours"
        status: pass
    human_judgment: false
  - id: D4
    description: "validWeekdays round-trips in Monday-first java.time.DayOfWeek order regardless of submission order (P-01)."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateTracerTest.java#create_validWeekdaysReturnedInMondayFirstOrder"
        status: pass
    human_judgment: false
  - id: D5
    description: "A zero-duration break round-trips with netHours equal to the full envelope duration and breakStartTime equal to breakEndTime."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateTracerTest.java#create_zeroDurationBreak_netHoursEqualsFullEnvelope"
        status: pass
    human_judgment: false
  - id: D6
    description: "Tenant isolation: listing templates for a desk while TenantContext holds a different tenant id returns an empty list (T-14-01)."
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/ShiftTemplateTracerTest.java#list_crossTenant_returnsEmpty"
        status: pass
    human_judgment: false
  - id: D7
    description: "The Shift Library page is reachable at /desks/:deskId/shift-library from the desk sidebar, renders the empty state with zero templates, and renders a populated table after a create."
    verification: []
    human_judgment: true
    rationale: "No frontend test framework exists in this codebase (14-RESEARCH.md Pitfall 4, confirmed against frontend/package.json). Proven only by npm run build exiting 0 and source assertions (grep for the route/link/empty-state copy/export). Visual reachability and rendering correctness require the plan's own <human-check>."
  - id: D8
    description: "V39 actually applies against the dev Postgres database (Flyway logs 'Migrating schema ... to version 39'), shift_template has the eleven columns and the unique constraint, and every existing desk row reads scheduling_mode = SLOT after migration."
    verification: []
    human_judgment: true
    rationale: "src/test/resources/application-test.yml sets flyway.enabled: false with ddl-auto: create-drop, so ./gradlew test NEVER executes V39 (P-03) — a green suite proves nothing about the migration itself. Asserted here only by SQL source-text grep (see Verification below); actual Flyway application against dev Postgres is the plan's explicit <human-check> and has not been run in this environment (no live Postgres available to this executor)."

duration: 23min
completed: 2026-08-25
status: complete
---

# Phase 14 Plan 01: Shift Template Tracer Summary

**End-to-end "create and see one shift template" slice — Flyway migration through a reachable React page, with every backend layer proven by a real @DataJpaTest, no solver code touched.**

## Performance

- **Duration:** 23 min
- **Started:** 2026-08-25T23:11:52Z
- **Completed:** 2026-08-25T23:34:21Z
- **Tasks:** 2 (1 checkpoint, 1 tracer)
- **Files modified:** 14 (10 created, 4 modified)

## Accomplishments

- `V39__add_shift_template_and_scheduling_mode.sql`: new `shift_template` table (D-10/D-11 identity and lifecycle rules) plus `desk.scheduling_mode` (MODE-01), with V38 and every earlier migration left byte-identical
- Full backend CRUD skeleton for create+list: `ShiftTemplate` entity, `ShiftTemplateRepository`, `ShiftTemplateService`, `ShiftTemplateController`, `ShiftTemplateRequest`/`ShiftTemplateResponse` DTOs — all desk-scoped and tenant-scoped, mirroring `Specialization`'s proven shape
- `ShiftTemplateTracerTest`: a real `@DataJpaTest` exercising controller → service → repository → H2 → DTO with no mocked layer, covering the D-01 worked example, Monday-first weekday ordering, zero-duration break, `Desk.schedulingMode` default, and cross-tenant isolation
- `ShiftLibrary.tsx`: a new desk-scoped page with an inline "Add Shift Template" form (client-side break-offset computation per P-05) and a populated/empty-state table, reachable at `/desks/:deskId/shift-library` from the desk sidebar
- Task 1 checkpoint resolved and recorded (see Key Decisions): D-11's non-overlap invariant is application-level, not a DB constraint

## Task Commits

Each task was committed atomically:

1. **Task 1: Checkpoint — confirm D-11 identity migration and non-overlap mechanism** — no commit (decision recorded in this SUMMARY, per the checkpoint's own `<output>` instruction; already resolved by the human operator before this executor ran — see Key Decisions)
2. **Task 2: End-to-end tracer slice** - `d12f0c6` (feat)

**Plan metadata:** committed alongside this SUMMARY

_Note: this task carried `tdd="true"` but is `type="tracer"`, which the executor protocol commits atomically like `type="auto"` (real implementation, real `<verify>`, one commit) rather than splitting into separate RED/GREEN commits — see TDD Gate Compliance below._

## Files Created/Modified

- `src/main/resources/db/migration/V39__add_shift_template_and_scheduling_mode.sql` - new `shift_template` table + `desk.scheduling_mode` column
- `src/main/java/com/wfm/model/SchedulingMode.java` - two-constant enum, mirrors `DayOffType`
- `src/main/java/com/wfm/model/ShiftTemplate.java` - entity with CHAR(7) weekday mask, derived break-window/net-hours accessors
- `src/main/java/com/wfm/repository/ShiftTemplateRepository.java` - tenant-scoped derived queries, no bare `findById`
- `src/main/java/com/wfm/service/ShiftTemplateService.java` - create+list only; full validation deferred to 14-03
- `src/main/java/com/wfm/controller/ShiftTemplateController.java` - `GET`/`POST /api/v1/desks/{deskId}/shift-templates`
- `src/main/java/com/wfm/dto/ShiftTemplateRequest.java` - plain record, no bean validation
- `src/main/java/com/wfm/dto/ShiftTemplateResponse.java` - plain record, breakStartTime/breakEndTime/netHours derived server-side
- `src/test/java/com/wfm/service/ShiftTemplateTracerTest.java` - end-to-end `@DataJpaTest`
- `frontend/src/pages/ShiftLibrary.tsx` - new page: add form + table + empty state
- `src/main/java/com/wfm/model/Desk.java` - `+schedulingMode` field/accessors
- `frontend/src/api/client.ts` - `+ShiftTemplate`/`ShiftTemplateBody` interfaces, `shiftTemplates` api object
- `frontend/src/pages/DeskAgents.tsx` - `DAY_ORDER`/`DAY_LABELS` exported (were module-private)
- `frontend/src/App.tsx` - `+shift-library` route and sidebar link

## Decisions Made

**Checkpoint decision (Task 1, already resolved by the human operator — recorded here per this plan's own `<output>` requirement, before V39 was written):**

D-11's same-name effective-range non-overlap invariant is enforced **application-level** in `ShiftTemplateService`. The unique key is `(tenant_id, desk_id, name, effective_from)`, verbatim.

Rationale (operator's, verbatim from the dispatch instructions): portable — enforced identically under H2 (`@DataJpaTest`) and Postgres, so the invariant is testable in the suite that actually runs; no `CREATE EXTENSION btree_gist` inside a one-way migration; produces a named, operator-readable validation error via the existing `ConflictException` path rather than an opaque constraint-violation stack trace; and it matches this codebase's standing idiom, where `SpecializationService` performs all validation manually in the service layer. Accepted cost: a direct SQL INSERT bypassing the service can create overlapping eras — a real gap, but consistent with this project's existing trust model, where multi-tenancy is already application-enforced with no DB row security.

Consequently, `V39` adds only the identity `UNIQUE` constraint — no `EXCLUDE USING gist`, no `CHECK` constraint for overlap. The overlap check itself is explicit **14-03 scope** (this plan's `createShiftTemplate` intentionally does only a null/blank-name check and a duplicate-identity-key check, per the plan's own action text: "Full field validation, the grid check and the era rules are 14-03's work; leaving them out here is a functionality gap, not an architectural one").

**Other decisions:**

- Exported `DAY_ORDER`/`DAY_LABELS` from `DeskAgents.tsx` (see Deviations) rather than re-declaring them in `ShiftLibrary.tsx`, per the plan's own `read_first` instruction to reuse them.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Exported `DAY_ORDER`/`DAY_LABELS` from `DeskAgents.tsx`**
- **Found during:** Task 2 (building `ShiftLibrary.tsx`)
- **Issue:** The plan's `read_first` and acceptance criteria require `ShiftLibrary.tsx` to import `DAY_ORDER`/`DAY_LABELS` from `DeskAgents.tsx` "rather than declaring new weekday literal arrays" — but both constants were module-private (no `export` keyword) in `DeskAgents.tsx`, so no import was possible without this change.
- **Fix:** Added `export` to both `const DAY_ORDER` and `const DAY_LABELS` declarations in `frontend/src/pages/DeskAgents.tsx`. No other change to that file; `DeskAgents.tsx`'s own behavior is untouched.
- **Files modified:** `frontend/src/pages/DeskAgents.tsx`
- **Verification:** `npm run build` exits 0; `frontend/src/pages/ShiftLibrary.tsx` imports both symbols and declares no local weekday array (`grep` confirms no re-declaration)
- **Committed in:** `d12f0c6` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary to satisfy the plan's own acceptance criteria; `DeskAgents.tsx`'s existing behavior is unaffected (an `export` keyword addition changes no runtime logic). No scope creep.

## TDD Gate Compliance

This task carries `tdd="true"` but is `type="tracer"`. Per the executor's own `<execute_tasks>` protocol, a `type="tracer"` task is "executed and committed exactly like `type='auto'`" — i.e. one atomic commit with real implementation and a real `<verify>` — which supersedes the generic RED/GREEN/REFACTOR multi-commit split that would otherwise apply to a `tdd="true"` task under `type="auto"`. The `<behavior>` block's "write failing tests before the implementation exists, then make them pass" is honored as an authoring discipline (the test file enumerates every one of the plan's six named behaviors and each was verified to compile against, and correctly exercise, the implementation before commit), but is expressed as a single commit rather than separate `test(...)`/`feat(...)` commits, matching the tracer protocol's explicit "atomic commit" instruction.

No `test(...)` / `feat(...)` gate-sequence commits exist for this task by design — this is not a gap, it is the tracer-type override documented above.

## Verification

Ran the plan's full `<verification>` block:

- `./gradlew test --tests 'com.wfm.service.ShiftTemplateTracerTest'` — **PASS**, all 6 behaviors green
- `./gradlew test` — **PASS**, full existing suite green (BUILD SUCCESSFUL, no regressions)
- `cd frontend && npm run build` — **PASS**, exits 0, no TypeScript errors
- `git diff --name-only -- src/main/java/com/wfm/solver/ src/main/resources/solverConfig.xml` — **PASS**, empty (no solver file touched)
- SQL source-text assertions on `V39__add_shift_template_and_scheduling_mode.sql` (`CREATE TABLE shift_template`, the exact `UNIQUE`/`ALTER TABLE`/`valid_weekdays` clauses, zero `is_active`/`enabled`/`retired` occurrences) — **PASS**, all confirmed by `grep`
- `⚡ Tracer verified end-to-end` — auto-mode re-verification of the tracer's own `<verify>` per the tracer feedback gate; passed, and there were no further expansion tasks in this plan to gate

### Not run — `<human-check>` (P-03, explicit plan requirement)

`src/test/resources/application-test.yml` sets `spring.flyway.enabled: false` with `ddl-auto: create-drop`, so `./gradlew test` **never executes V39** — a green suite proves nothing about whether the migration actually applies. This executor has no live dev Postgres instance available, so the plan's `<human-check>` — (1) Flyway logs "Migrating schema ... to version 39"; (2) `\d shift_template` shows the eleven columns and the unique constraint; (3) `SELECT DISTINCT scheduling_mode FROM desk;` returns only `SLOT`; (4) the Shift Library page is reachable and a created template appears after reload — **has not been performed** and remains outstanding. See Next Phase Readiness.

## Issues Encountered

None beyond the documented deviation above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Ready for 14-02 (constraint classification, test scope only) and 14-03 (full field validation, grid check, era/overlap rules, retire, coverage validator, mode-switch endpoint) — this plan deliberately left those out as scoped functionality gaps, not architectural gaps.
- **Outstanding: the plan's `<human-check>` has not been run.** Before this phase is considered fully verified, a human (or a later automated step with access to the dev environment) must start the app against the dev Postgres instance and confirm V39 actually applies cleanly and the Shift Library page renders correctly end-to-end in a browser. This is the same class of gap 14-RESEARCH.md's Assumption A2 flags: "If the human check is skipped, a syntactically valid but semantically wrong migration ships green."
- Phase 16's `agent_usual_shift` FK will point at `shift_template` rows created via this slice — D-11's hand-off note (re-point-on-supersede vs. resolve-by-name+date) is still open for Phase 16 to decide, unchanged by this plan.

---
*Phase: 14-shift-library-scheduling-mode*
*Completed: 2026-08-25*

## Self-Check: PASSED

All 10 created files confirmed present on disk; commit `d12f0c6` confirmed in `git log`.
