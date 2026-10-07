# Phase 24: Close gap N-1/N-2 — calendar-date keys in shift-library validation and allocation rows - Context

**Gathered:** 2026-10-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Every surface that decides which business day a timeslot belongs to agrees with the solver
(`SolverService.java:2433`, which reads `ts.getBusinessDate()`). Concretely:

- **N-1:** `ShiftLibraryValidationService` keys its windows, `covers()` weekday/effectivity checks,
  uncovered-window list and both advisory bucketings on the business date, so a weekday-restricted
  overnight template validates against the hours the solver can actually give it.
- **N-2:** the Agent Allocation Required / Over-under rows (`ScheduleResults.tsx`) look demand up by
  business date and load the final business day's post-midnight demand.
- **Same-class sites found during scouting** (D-06): the post-solve repair services, which key on
  the calendar date where their own maps are keyed on the business date.
- The structural guard that should have caught N-1 is extended so this class cannot recur silently.

Only desks whose day start is not `00:00` are affected; `00:00` desks must remain bit-for-bit
unchanged. Out of scope: changing the `date` response field, any new UI, a frontend test runner.

</domain>

<decisions>
## Implementation Decisions

All decisions below were taken as Claude's recommended defaults at the operator's instruction
("recommended decisions") — none was individually debated.

### N-1 — validator date keys
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

### N-2 — allocation-row demand
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

### Same-class sweep and guard
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

### Verification
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Gap definitions
- `.planning/v1.5-MILESTONE-AUDIT.md` — N-1, N-2 (integration block, lines ~45-63), flows F-1..F-3
- `.planning/ROADMAP.md` §"Phase 24" — goal, file/line pointers, "why no test caught it"

### Prior decisions this phase must honour
- `.planning/phases/20-solver-business-date-correctness/20-CONTEXT.md` — D-08 (join guard scope),
  D-10 (calendar date on operator-facing labels / the `date` response field)
- `.planning/phases/21-overnight-shift-templates/21-CONTEXT.md` — validator and grid surfaces (OVNT-05..07)
- `.planning/REQUIREMENTS.md` — OVNT-05, OVNT-06, OVNT-07, SOLV-07, BDAY-02, BDAY-05, BDAY-08

### Code
- `src/main/java/com/wfm/service/ShiftLibraryValidationService.java` — N-1 sites
- `src/main/java/com/wfm/service/SolverService.java:2425-2440` — the reference behaviour
- `src/main/java/com/wfm/service/ShiftLibraryGenerationService.java` — shares `covers()`
- `src/main/java/com/wfm/service/StaffingRequirementService.java:390-400` — `toResponseItem`, D-10 comment
- `src/main/java/com/wfm/controller/StaffingRequirementController.java` — list endpoint params
- `src/main/java/com/wfm/service/BusinessDayPeriodLoader.java:75-85` — business-range filter pattern
- `src/main/java/com/wfm/service/ScheduleEnvelopeRepairService.java`, `ScheduleConsistencyRepairService.java` — D-06
- `frontend/src/pages/ScheduleResults.tsx:398-420, 672, 686, 962, 976` — N-2
- `frontend/src/api/client.ts:407` — `StaffingRequirement` type
- `src/test/java/com/wfm/service/BusinessDateJoinGuardTest.java` + `src/test/resources/bday-join-guard.md` — D-07

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DayWindow.businessDateOf(dayStart, calendarDate, timeOfDay)` (`util/DayWindow.java:423`) — the
  one derivation; server-side filtering should go through stored `getBusinessDate()` or this.
- `BusinessDateJoinGuardTest` — explicit-file-list guard with a two-direction allowlist; extend it.
- `BusinessDateWritePathGuardTest`, `MidnightTimeArithmeticGuardTest` — guard technique precedents.

### Established Patterns
- Additive-then-consume for API changes (Phase 19 D-10 / Phase 20): add the field, then switch the
  consumer; never repurpose an existing field.
- Red-first regression tests for every defect; a `00:00` control alongside each non-`00:00` case.
- Guard tests prove they can go red, not just that they are green.

### Integration Points
- `covers()` is called by both validator and generator — one change point.
- Allocation rows join demand (`staffingRequirements.list`) to the grid (`ScheduleOutputService`,
  business-date keyed at `:173, :323`).

</code_context>

<specifics>
## Specific Ideas

- The non-`00:00` fixture should mirror the Phil-US Overnight shape (06:00 anchor, 21:00-06:00
  template) with at least one weekday-restricted template, so the tests exercise the exact F-2 flow.

</specifics>

<deferred>
## Deferred Ideas

- Frontend test runner (Phase 22 WR-04) — would let N-2 have a unit test; its own phase.

### Reviewed Todos (not folded)
- `2026-08-13-cross-agent-seat-displacement.md` — solver move work; keyword match only.
- `2026-07-30-blank-upload-template-one-sheet-per-desk.md` — upload UX; unrelated.
- `2026-08-14-terraform-db-password-drift.md` — infra; unrelated.

</deferred>

---

*Phase: 24-close-gap-n-1-n-2-calendar-date-keys-in-shift-library-valida*
*Context gathered: 2026-10-07*
