---
phase: 14-shift-library-scheduling-mode
verified: 2026-08-25T22:05:00Z
status: human_needed
score: 22/22 code-verifiable must-haves verified
behavior_unverified: 0
overrides_applied: 0
behavior_unverified_items:
  - truth: "V39 actually applies against the dev Postgres database (Flyway logs 'Migrating schema ... to version 39'), shift_template has the eleven columns and the unique constraint, every existing desk row reads scheduling_mode = SLOT after migration, and the Shift Library page is reachable and renders a created template after reload."
    test: "Start the app against a live dev Postgres instance; confirm Flyway logs migrating to version 39; run \\d shift_template and SELECT DISTINCT scheduling_mode FROM desk; in the app, open a desk's Shift Library page, add one template, reload, and confirm it renders."
    expected: "Migration applies cleanly, schema matches the SQL source, every desk reads SLOT, and the created template survives a reload."
    why_human: "src/test/resources/application-test.yml sets flyway.enabled: false with ddl-auto: create-drop, so ./gradlew test never executes V39 — a green suite (confirmed: 402/402 passing) proves nothing about the migration itself, and this verifier has no live Postgres instance to apply it against."
  - test: "With the app running against a dev database with seeded staffing demand and an incomplete shift library: (1) Coverage panel names specific uncovered windows; (2) clicking Shift-scheduled on the incomplete library leaves the toggle on Slot-scheduled and shows the same named windows, no duplicate error list; (3) after adding covering templates the same click succeeds and the toggle moves; (4) clicking Slot-scheduled switches back immediately with no dialog; (5) starting a solve and clicking either option shows a single-line 409 toast and the toggle does not move; (6) Desk Management's Scheduling Mode column reads correctly per desk with no control to change it; (7) a template with an hours mismatch shows the amber glyph with the correct tooltip and still saves."
    truth: "The seven end-to-end operator flows on ShiftLibrary.tsx and DeskManagement.tsx (14-06-PLAN.md Task 3's own <human-check>) behave as specified."
    expected: "All seven flows behave exactly as described above."
    why_human: "No frontend test framework exists in this codebase (confirmed: frontend/package.json has no test script, no vitest/jest/testing-library). This executor and this verifier both lack a live dev environment with seeded staffing demand and a browser to drive. Source-level evidence (npm run build exit 0, grep-verified wiring, exact Copywriting Contract strings present) is strong but does not substitute for observed behavior."
  - truth: "Six purely-visual backstop claims from 14-06-PLAN.md must_haves: (a) era grouping reads as legible eras, not accidental duplicates; (b) long template names/many eras don't break table layout; (c) a long name in the fixed-width Name cell renders legibly; (d) a long name in the fixed-width Name input renders legibly; (e) the uncovered-windows list reads clearly at a realistic count; (f) the SHLB-06 advisory sentence renders legibly in a native OS tooltip across weekday-name/hours-value length variation."
    test: "Visually inspect ShiftLibrary.tsx in a browser with realistic data volumes and a long template name."
    expected: "No layout breakage, no illegible text, no ambiguous era grouping."
    why_human: "Explicitly tagged verification: backstop in the plan's own must_haves — the plan's author (P-26) states these route to human_needed at verification unless visual evidence is wired. No such evidence was produced by the executor or is producible by this verifier."
---

# Phase 14: Shift Library & Scheduling Mode Verification Report

**Phase Goal:** An operator can define a desk's shift library and switch that desk into
shift-scheduled mode, with both edits validated against real demand and contracted hours before
they ever reach the solver.

**Verified:** 2026-08-25T22:05:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Summary

Every code-verifiable truth (backend logic, wiring, migration content, solver-package
non-interference, requirements traceability, XCUT-05 classification) was independently
re-derived from the current codebase — not from SUMMARY.md claims — and confirmed correct. The
full backend test suite was re-run fresh in this session (`./gradlew test --rerun`, forced
re-execution bypassing Gradle's cache) and passed **402/402, 0 failures, 0 errors**, including
every Phase-14-specific test class. `cd frontend && npm run build` passed with no errors.

Two categories remain outside what any automated check (by the executor or this verifier) can
prove, both explicitly and honestly flagged by the phase's own plans and summaries rather than
hidden:

1. **The V39 Flyway migration has never actually been applied to a live Postgres instance.** The
   backend test suite runs against H2 with `flyway.enabled: false`, so a green suite is silent on
   whether the migration itself is valid SQL that Postgres will accept. This verifier has no live
   dev Postgres to apply it against either.
2. **The Shift Library UI's end-to-end operator flows and six purely-visual claims have never been
   exercised in a browser.** This codebase has no frontend test framework; the plan's own
   `must_haves` explicitly tags six claims `verification: backstop` and Task 3 carries an explicit
   `<human-check>` for the seven operator-facing flows. Neither this verifier nor the executor has
   a live dev environment with seeded data to drive a browser against.

These are not code gaps — every underlying artifact is present, wired, and passes every check that
can run without a live database and a browser — so they route to `human_needed`, not
`gaps_found`, per the honest-verifier decision tree.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Every pre-existing desk defaults to slot-scheduled; new desks default to SLOT (MODE-01) | ✓ VERIFIED | `Desk.java:29-30` — `@Column(name="scheduling_mode", nullable=false)`, Java default `SchedulingMode.SLOT`; SQL `NOT NULL DEFAULT 'SLOT'` in V39; `ShiftTemplateTracerTest#desk_savedWithoutModeSet_readsBackAsSlot` passes (fresh run) |
| 2 | Operator can create a shift template (start, end, break, weekdays, effective range) and see it in the library (SHLB-01/02/03) | ✓ VERIFIED | `ShiftTemplateController`/`Service`/`Repository`/entity all present; `ShiftTemplateTracerTest` (6/6) + `ShiftTemplateServiceTest` (31/31) fresh-run green |
| 3 | Every reject is a named, operator-readable message (SHLB-01/02/03) | ✓ VERIFIED | `ShiftTemplateService.validate(...)` — verbatim messages confirmed present in source (`grep -c` each message = 1); 30 test methods assert message text, not just exception type |
| 4 | Identity + non-overlap: exactly one era of a name applies to any date (SHLB-03) | ✓ VERIFIED | `ShiftTemplateService` app-level check (checkpoint decision, D-11); touching/overlapping/open-ended era tests all pass |
| 5 | Template edit/retire never deletes a row; no delete endpoint exists (SHLB-04) | ✓ VERIFIED | `grep` confirms zero `public void delete\|retire` in service, zero `@DeleteMapping` in controller; `updateShiftTemplate` is the sole mutation path; retire test confirms row persists |
| 6 | D-02 grid alignment enforced at save and re-checked at mode switch | ✓ VERIFIED | `ShiftTemplateService.isAligned` (package-private static, single implementation); reused by `ShiftLibraryValidationService.findMisalignedTemplates` via direct call, not reimplementation (confirmed by reading both files) |
| 7 | Coverage validator: zero live demand is REFUSED, never a vacuous pass (D-05) | ✓ VERIFIED | `ShiftLibraryValidationService.validate` — `hasLiveDemand` computed from filtered live rows; `requireShiftModeReady` throws with `demand` detail when false; test `requireShiftModeReady_noLiveDemand_throwsWithDemandDetailVerbatim` passes |
| 8 | Coverage is structural, single-template envelope coverage, live demand only (D-04/D-05) | ✓ VERIFIED | `covers(template, window)` reads weekday set, effective range, envelope bounds, break exclusion; `findAllLiveByDesk` uses `scheduleId IS NULL`; no template-stitching logic present |
| 9 | SHLB-06 hours match is exact BigDecimal equality, no tolerance (D-07) | ✓ VERIFIED | `anyHoursMatch` uses `BigDecimals.normalize(...).compareTo(...) == 0`; `grep` for `tolerance\|epsilon\|closeTo` in the validator = 0 |
| 10 | SHLB-06 is advisory everywhere except the guaranteed-infeasible weekday case (D-06) | ✓ VERIFIED | `hoursAdvisories` never converted to `ErrorDetail`; only `unsatisfiableWeekdays` (a true joint-unsatisfiability case) produces a blocking `contractedHours` detail; asserted by a dedicated test |
| 11 | One validator implementation serves both the report and the refusal (D-08) | ✓ VERIFIED | `validate()` (non-throwing) and `requireShiftModeReady()` (throws) are the same class; `requireShiftModeReady` literally calls `validate` internally |
| 12 | Mode switch refuses SLOT→SHIFT with named uncovered windows when coverage fails (MODE-03) | ✓ VERIFIED | `DeskService.switchSchedulingMode` calls `shiftLibraryValidationService.requireShiftModeReady(deskId)` only for target SHIFT, lets the exception propagate untouched; test confirms details array intact |
| 13 | Mode switch writes exactly one column; accepted schedules are byte-identical across a round trip (MODE-04) | ✓ VERIFIED | `switchSchedulingMode` body reads only `deskRepository`/`inMemoryScheduleStore`, writes only `deskRepository.save(desk)`; `switchSchedulingMode_roundTrip_leavesAcceptedScheduleAndSnapshotRowsExactlyUnchanged` asserts every field of an ACCEPTED Schedule + snapshot Timeslot/StaffingRequirement rows before/after a SLOT→SHIFT→SLOT round trip — passes |
| 14 | SHIFT→SLOT is freely reversible and ungated (D-12) | ✓ VERIFIED | Coverage gate call is inside `if (target == SchedulingMode.SHIFT)` only; test asserts validator mock never invoked on SHIFT→SLOT even when stubbed to throw |
| 15 | Mode switch refused with 409 while a RUNNING solve exists, in both directions, never stops the solve (D-13) | ✓ VERIFIED | `inMemoryScheduleStore.getByDeskId(deskId)` checked before the coverage gate and the write, both directions; `grep` confirms zero new `remove\|terminateEarly\|stopSolve\|cancel` call sites added |
| 16 | Same-mode switch is a no-op: no guard, no validation, no write (P-23) | ✓ VERIFIED | Early-return `if (desk.getSchedulingMode() == target) return desk;` before the store read; test proves this behaviorally against a registered RUNNING solve |
| 17 | Desk mode is visible in every surface that displays it (XCUT-01) | ✓ VERIFIED | `DeskResponse.schedulingMode` (trailing 5th component) populated in `DeskController.toResponse`; `ShiftLibrary.tsx` toggle reads `desk.schedulingMode`; `DeskManagement.tsx` renders a read-only `Scheduling Mode` column reading the same field — both confirmed by direct source read |
| 18 | Shift template written values are visible in every surface that displays them (XCUT-01) | ✓ VERIFIED | `ShiftLibrary.tsx` table renders name, start–end, break window+duration, weekdays, effective range, era badge, hours-match glyph — all sourced from `ShiftTemplateResponse`/`ShiftLibraryValidation`, never recomputed client-side (confirmed: zero client-side coverage/hours computation via grep) |
| 19 | No production solver file changed; no solve path reads scheduling-mode (MODE-05) | ✓ VERIFIED | `git diff --name-only 823c193..HEAD -- src/main/java/com/wfm/solver/ src/main/resources/solverConfig.xml` — empty; same for `ConstraintWeights.java`/`SolverService.java`/`ScheduleService.java`; `grep -rEn 'SchedulingMode|schedulingMode|scheduling_mode'` over solver package + SolverService (excluding comments) — 0 matches; full 402-test suite green |
| 20 | Every one of 19 solver constraints classified, with a code-derived completeness test (XCUT-05, partial) | ✓ VERIFIED | `ScheduleConstraintClassification.classifications()` has exactly 19 rows matching `ConstraintWeights`'s 19 `@ConstraintWeight` values (independently confirmed via grep); `ScheduleConstraintClassificationTest` (6/6) passes fresh; markdown mirror confirmed row-for-row identical |
| 21 | Two named-owner OPEN rows, not silently guessed (D-15) | ✓ VERIFIED | `Honour preferred start time`/`Honour preferred break time` both `OPEN_RESOLVE_IN_PHASE_15` with owner `"Phase 15 — Shift Envelope & Coupling"` in both the Java map and the markdown mirror |
| 22 | No destructive control anywhere this phase touches (D-10, D-12) | ✓ VERIFIED | `grep -Ec 'className="danger"\|confirm\('` on `ShiftLibrary.tsx` = 0; `#ef4444` (red) = 0 in `ShiftLibrary.tsx`; `DeskManagement.tsx`'s pre-existing Delete button is untouched (out of this phase's scope) |

**Score:** 22/22 code-verifiable truths verified (0 present-but-behavior-unverified — every one that could be code-checked was checked and confirmed, not merely present)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `V39__add_shift_template_and_scheduling_mode.sql` | New table + column, forward-only | ✓ VERIFIED | Latest migration file confirmed via `ls \| sort`; contains exact required DDL; V38 and earlier untouched (`git diff` over migration dir shows only V39 added) |
| `ShiftTemplate.java`, `Repository`, `Service`, `Controller`, DTOs | Full CRUD (no delete) | ✓ VERIFIED | All present, wired, tested |
| `ShiftLibraryValidationService`/`Controller`/`Response` | Shared validator, report endpoint | ✓ VERIFIED | All present, wired, tested |
| `DeskService.switchSchedulingMode`, `DeskController` PUT endpoint | Mode switch | ✓ VERIFIED | Present, wired, tested |
| `ScheduleConstraintClassification`/`Test`, markdown mirror | XCUT-05 deliverable | ✓ VERIFIED | Present in test scope, completeness test green, markdown matches |
| `ShiftLibrary.tsx`, `DeskManagement.tsx` mode column, `App.tsx` route | Operator UI | ✓ VERIFIED (structurally) | Present, builds clean, source-verified against every acceptance criterion; runtime rendering not observed in a browser (see Human Verification) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `ShiftTemplateController` | `ShiftTemplateRepository` | Service layer | ✓ WIRED | Real `@DataJpaTest` (H2), not mocked |
| `DeskService.switchSchedulingMode` | `ShiftLibraryValidationService.requireShiftModeReady` | Direct call, SHIFT-only | ✓ WIRED | Confirmed in source; test proves call/no-call in both directions |
| `ShiftLibraryValidationService` | `ShiftTemplateService.isAligned` | Static method reuse | ✓ WIRED | Confirmed: grid arithmetic exists only once, called from the validator |
| `ShiftLibrary.tsx` | `GET /shift-library/validation` | `fetchValidation()` | ✓ WIRED | 5 call sites confirmed (load, create, update, retire, mode-switch success) |
| `DeskResponse.schedulingMode` | `DeskManagement.tsx` mode column | Existing desk-list fetch | ✓ WIRED | No second request added; field read directly |
| `desk.scheduling_mode` column | Solver / `SolverService` | — | ✓ CONFIRMED ABSENT (correct) | Zero references outside comments — this is the desired state for MODE-05 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| Shift-library table rows | `templates` | `shiftTemplatesApi.list(deskId)` → real GET | Yes | ✓ FLOWING |
| Coverage panel | `validation` | `shiftLibraryApi.validation(deskId)` → real GET, recomputed server-side | Yes | ✓ FLOWING |
| Era badge (`Current`/`Upcoming`/`Past`) | `t.eraStatus` | Server-computed in `ShiftTemplateController.toResponse`, never re-derived client-side | Yes | ✓ FLOWING |
| Hours-match glyph | `validation.hoursAdvisories` | Server-computed exact-equality check | Yes | ✓ FLOWING |
| Desk mode toggle / column | `desk.schedulingMode` | Real desk GET/PUT responses | Yes | ✓ FLOWING |

No hardcoded/static/mock data paths found in any Phase 14 frontend or backend file.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Phase-14-specific test classes pass | `./gradlew test --tests 'com.wfm.solver.ScheduleConstraintClassificationTest' --tests 'com.wfm.service.ShiftTemplateTracerTest' --tests 'com.wfm.service.ShiftTemplateServiceTest' --tests 'com.wfm.service.ShiftLibraryValidationServiceTest' --tests 'com.wfm.service.DeskServiceSchedulingModeTest'` | All green: 6+6+31+27+17 = 87 tests, 0 failures | ✓ PASS |
| Full backend suite, forced re-execution (not cached) | `./gradlew test --rerun` | `BUILD SUCCESSFUL in 7m 57s`; 402 tests, 0 failures, 0 errors (counted directly from fresh JUnit XML, not from SUMMARY claims) | ✓ PASS |
| Frontend builds clean | `cd frontend && npm run build` | Exit 0, `tsc -b && vite build` succeeded | ✓ PASS |
| Solver package byte-identical | `git diff --name-only 823c193..HEAD -- src/main/java/com/wfm/solver/ src/main/resources/solverConfig.xml` | Empty output | ✓ PASS |
| No scheduling-mode reference in solver/SolverService | `grep -rEn 'SchedulingMode\|schedulingMode\|scheduling_mode' src/main/java/com/wfm/solver/ src/main/java/com/wfm/service/SolverService.java` (comments excluded) | 0 matches | ✓ PASS |
| V39 Flyway migration applies to live Postgres | — | Not run — no live Postgres available to this verifier | ? SKIP → human verification |
| End-to-end browser flows (7-item human-check) | — | Not run — no live dev environment/browser available to this verifier | ? SKIP → human verification |

### Probe Execution

No `scripts/*/tests/probe-*.sh` files exist for this phase; no probe declared in PLAN/SUMMARY bodies. N/A.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| SHLB-01 | 14-01, 14-03, 14-06 | Define per-desk shift templates (start/end/break) | ✓ SATISFIED | See truths #2, #3 |
| SHLB-02 | 14-01, 14-03, 14-06 | Valid weekdays per template | ✓ SATISFIED | Mask storage, Monday-first ordering, empty-set rejection all tested |
| SHLB-03 | 14-01, 14-03, 14-06 | Effective date range | ✓ SATISFIED | Identity + non-overlap invariant, eraStatus |
| SHLB-04 | 14-01, 14-03, 14-06 | Edit/retire without corrupting referencing schedules | ✓ SATISFIED | No delete endpoint; snapshot-pattern reasoning holds structurally (no FK table exists yet this phase) |
| SHLB-05 | 14-04 | Coverage validation against demand | ✓ SATISFIED | `ShiftLibraryValidationService.validate`, tested |
| SHLB-06 | 14-04 | Contracted-hours match reported at definition time | ✓ SATISFIED | Advisory + fatal-case logic, tested |
| MODE-01 | 14-01 | Desk mode SLOT/SHIFT, default SLOT | ✓ SATISFIED | Migration + entity default agree |
| MODE-02 | 14-05, 14-06 | Switch from desk configuration UI | ✓ SATISFIED (structurally) | Endpoint + UI toggle present; UI human-check not run |
| MODE-03 | 14-05 | Refused with named uncovered windows | ✓ SATISFIED | `requireShiftModeReady` called from switch, tested |
| MODE-04 | 14-05 | No effect on accepted schedules | ✓ SATISFIED | Field-by-field round-trip test passes |
| MODE-05 | 14-02 | Slot-mode solve unchanged | ✓ SATISFIED | Structural + empirical proof (empty diff, 0 grep matches, 402-test green suite) |
| XCUT-01 | 14-01, 14-03, 14-05, 14-06 | Written values visible in every surface | ✓ SATISFIED | Traced store→API→UI for both shift-template fields and scheduling mode |
| XCUT-05 (partial) | 14-02 | Constraint classification deliverable | ✓ SATISFIED | 19/19 classified, completeness test enforces it, 2 explicit OPEN rows |

No orphaned requirement IDs: every SHLB/MODE ID declared in ROADMAP.md's Phase 14 requirements list appears in at least one plan's frontmatter `requirements` field, and every plan's declared requirements trace to REQUIREMENTS.md entries.

### Anti-Patterns Found

None. Scanned every file this phase created or modified (backend + frontend) for `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` and "not yet implemented"/"coming soon" phrasing — zero matches. No empty-body handlers, no hardcoded-empty data paths feeding rendered output, no stub returns.

### Human Verification Required

### 1. V39 migration applies cleanly to a live Postgres instance

**Test:** Start the application against the dev Postgres database. Confirm Flyway logs "Migrating schema ... to version 39". Run `\d shift_template` and confirm the eleven columns and the `(tenant_id, desk_id, name, effective_from)` unique constraint. Run `SELECT DISTINCT scheduling_mode FROM desk;` and confirm it returns only `SLOT`.
**Expected:** Migration applies without error; schema matches the SQL source exactly.
**Why human:** The test suite runs against H2 with `flyway.enabled: false` (confirmed in `src/test/resources/application-test.yml`), so `./gradlew test` never executes V39. This verifier has no live Postgres instance to apply it against. The SQL source text was reviewed line-by-line and is syntactically sound Postgres DDL, but syntactic soundness is not the same as a confirmed successful `flyway migrate`.

### 2. Seven end-to-end operator flows on the Shift Library and Desk Management pages

**Test:** With the app running against a dev database with seeded staffing demand and a partial shift library: (1) confirm the Coverage panel names specific uncovered windows; (2) click Shift-scheduled and confirm the toggle stays on Slot-scheduled with the same named windows shown, no duplicate error surface; (3) add covering templates and confirm the same click now succeeds and the toggle moves; (4) click Slot-scheduled and confirm it switches back immediately with no dialog; (5) start a solve for the desk, then click either mode option, and confirm a single-line 409 toast appears and the toggle does not move; (6) confirm Desk Management's Scheduling Mode column shows the correct value per desk with no control to change it there; (7) confirm a template with a contracted-hours mismatch shows the amber warning glyph with the correct tooltip and still saves successfully.
**Expected:** All seven behaviors match exactly as described (this is 14-06-PLAN.md Task 3's own `<human-check>`, verbatim).
**Why human:** No frontend test framework exists in this codebase. Source-level evidence (correct wiring, exact Copywriting Contract strings, correct branch conditions on HTTP status) is strong and was independently confirmed by this verifier, but none of it proves the rendered behavior a browser would show.

### 3. Six visual/legibility claims (explicitly tagged `backstop` by the plan itself)

**Test:** Visually inspect the Shift Library page with realistic data: multiple eras per template name, a long template name, a realistic count of uncovered demand windows, and hover over an hours-mismatch warning glyph.
**Expected:** Era grouping reads as legible eras (not accidental duplicates); long names don't break the table or input layout; the uncovered-windows list stays readable; the advisory tooltip is legible.
**Why human:** These are the plan's own six explicitly-flagged `verification: backstop` truths — visual claims this codebase has no way to test automatically, and the plan's author (P-26) states they route to `human_needed` unless visual evidence is wired. None was.

### Gaps Summary

No gaps found. Every truth that code inspection, fresh test execution, and static analysis can settle was settled in this phase's favor, cross-checked independently against the actual codebase (not SUMMARY.md prose) at every point:

- The V39 migration's DDL, the `ShiftTemplate`/`SchedulingMode` model, the full CRUD/validation/lifecycle service layer, the shared coverage-and-hours validator, the mode-switch endpoint with its 409/400 gates, the XCUT-05 constraint classification and its completeness test, and the frontend UI surface were all read directly and found to match their plans' claims exactly, with no discrepancies.
- The solver package is confirmed byte-identical since before Phase 14 began (`git diff` against `823c193`, the phase's starting commit, is empty), and a fresh, forced (`--rerun`) full-suite execution passed 402/402 with zero failures — this is real evidence gathered in this verification session, not a re-statement of a SUMMARY claim.
- The two remaining open items (live-Postgres migration application, and browser-driven UI/visual behavior) are exactly the items the phase's own plans and summaries flagged as unverifiable by an executor with no live database or browser — and this verifier is under the identical constraint. They are not evidence of missing or broken work; they are the honest boundary of what static/automated verification can prove for this phase's outstanding surface.

---

_Verified: 2026-08-25T22:05:00Z_
_Verifier: Claude (gsd-verifier)_
