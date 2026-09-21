# Milestones

## v1.3 Shift-Based Scheduling & Consistency (Shipped: 2026-09-21)

**Phases completed:** 4 phases, 36 plans, 91 tasks
**Requirements:** 43/43 satisfied (7 SHLB, 5 MODE, 10 ENVL, 6 USHF, 6 CONS, 4 DRFT, 5 XCUT)
**Milestone audit:** `tech_debt` — zero integration gaps across six cross-phase seams and four E2E
flows; 15 tech-debt items recorded. See `milestones/v1.3-MILESTONE-AUDIT.md`.
**Closeout type:** `override_closeout`
**Known verification overrides:** 10 newly acknowledged, 3 carried forward from a prior close (see
STATE.md Deferred Items). The 10 are 3 debug sessions still at `status: diagnosed` and 7 deferred
items — including two functional gaps deferred by operator ruling OR-2: blocked-break-hours has no
enforcement point in SHIFT mode, and a template's envelope is never validated against the desk's
operating window at save time.
**Nyquist coverage:** 1 compliant (17), 1 partial (16), 2 not-validated (14, 15).
**Codebase:** +31,010 / −293 across 169 files over 370 commits (2026-08-25 → 2026-09-21); backend
suite grew 402 → 723 tests; schema head V38 → V49.

**Three planning documents were corrected during the close**, each having asserted something a
later event had already falsified: `14-VERIFICATION.md` (still `human_needed` three weeks after UAT
discharged all three of its items), `REQUIREMENTS.md` (Phase 16 shown as `human_needed`; requirement
count 34 against an actual 38), and `STATE.md` (75% / 2-of-4 against its own 100% frontmatter).

**Key accomplishments:**

- End-to-end "create and see one shift template" slice — Flyway migration through a reachable React page, with every backend layer proven by a real @DataJpaTest, no solver code touched.
- `ShiftTemplateService` gains full save-time validation (name/time/break/weekday/effective-range checks, D-02 grid alignment, D-11 identity + non-overlap), `updateShiftTemplate` as the sole edit/retire path, server-computed `eraStatus`, and a name-grouped era-descending list order — every reject now carries a specific, asserted, operator-readable message.
- `ShiftLibraryValidationService` — one computation serving both the shift-library editor's coverage panel and the mode-switch refusal (D-08): structural envelope coverage over live demand only (D-04/D-05), a D-02 grid re-check reusing `ShiftTemplateService.isAligned`, and an exact-equality contracted-hours match (D-06/D-07) with a single fatal case, exposed read-only at `GET /api/v1/desks/{deskId}/shift-library/validation`.
- `DeskService.switchSchedulingMode` — a `PUT /api/v1/desks/{deskId}/scheduling-mode` endpoint that gates SLOT-to-SHIFT with the shared `ShiftLibraryValidationService` coverage gate (D-08's second caller), leaves SHIFT-to-SLOT freely reversible (D-12), refuses both directions with 409 while a solve is RUNNING by reusing the existing `ConflictException`/`InMemoryScheduleStore` idiom (D-13, P-21), and writes exactly one column — proven field-by-field never to touch an accepted schedule (MODE-04).
- Completed `ShiftLibrary.tsx` against the full 14-UI-SPEC.md: per-row inline edit and retire, era-legible rows, an always-visible coverage validation panel, the SHLB-06 hours-match advisory glyph, and the Scheduling Mode segmented-control toggle — plus a read-only `Scheduling Mode` column on `DeskManagement.tsx` — closing out Phase 14's operator-facing surface with zero client-side re-derivation of any server-computed verdict.
- Promoted a shift template's break from Phase 14's single fixed offset to N break bands end-to-end (V40 migration, entity, service validation, DTOs, controller), generalised `covers()` to any-band coverage with a proven one-band-identical invariant, added a capacity-shortfall advisory, and closed the G-14-1 migration-vs-entity blind spot with a dependency-free guard.
- Stateless `GET /shift-library/suggestion` endpoint that enumerates candidate shift envelopes from a desk's live demand and contracted hours, then greedy-then-verify covers the demand using `ShiftLibraryValidationService.covers()` as the single coverage predicate — returning an editable draft plus any still-uncovered windows in the exact `ErrorDetail` shape the coverage report already emits.
- The solver's second `@PlanningEntity` (`AgentShiftAssignment`) is coupled to seat assignment by a genuine hard `ConstraintStream` constraint (`shiftEnvelopeCompliance`), with an entity-level value range filtered by contracted hours, a two-phase shifts-first construction heuristic, and a test that actually builds a `SolverFactory` from the real `solverConfig.xml`.
- A reusable, deterministic shift-mode fixture builder and an independent envelope-membership walker that agrees with `solverConfig.xml`'s reported score on a clean solve and disagrees, correctly, on six deliberately corrupted ones — the exact check `SPIKE-COUPLING.md`'s Option C would have failed.
- `ShiftLibrary.tsx` gained a repeatable break-band editor (`BandEditor`, shared by the Add/Edit form and N Suggested Library draft rows), a band-aware Break column, a capacity-advisory glyph column, and a stateless Suggested Library draft panel that saves rows through the existing create endpoint — closing the display-verification half of ENVL-08 and SHLB-07 that XCUT-01 exists to guard against.
- Six pre-existing constraints mode-gated off for shift desks via an arity-preserving `ifExists` substitute for Timefold's missing Penta stream, `Break clustering` given a real cross-agent body that measurably starves a single-band library's mid-shift timeslot, and `Band capacity` shipped as a hard cap with a pre-solve refusal that reuses the shift-library report's own computation — closing XCUT-05 with 21 constraints, 21 classified, zero open.
- An accepted shift-mode schedule denormalises what each agent actually worked onto the accepted row (immune to later template edits), one builder exposes that envelope through `schedulingMode` and a per-entry `ShiftDescriptor` on the schedule detail response, and the Agent Allocation view groups agents under their assigned shift while a slot-scheduled desk renders byte-identical to before.
- A seeded, step-count-terminated A/B benchmark recovered from Phase 12's withdrawn harness measures the shift model against the slot model honestly: PASS, driven by a clean 5/5-vs-1/5 feasibility-convergence gap — not by the comparative medians, which the plan's own noise rule correctly disqualifies as too small relative to the slot arm's own spread — plus a "no measurable difference" answer to the D-08 construction-heuristic-ordering question.
- `SolverService.expandMinimumStaffingSeats` now branches on `SchedulingMode`: a SHIFT desk suppresses filler seats at timeslots no live shift envelope reaches (operator ruling OR-1) and guarantees enough seats at covered zero-demand timeslots for every working agent-day, while `ShiftModeFixtures` was widened to actually be able to construct that out-of-envelope shape.
- The Agent Schedule table, preference-report break KPIs, and Excel export now read the authoritative shift envelope and band-derived break the solver already resolved — instead of silently redrawing the envelope around whatever seats the solver happened to place — and a coupled out-of-envelope-seat / unworked-legal-slot divergence is surfaced as first-class data on every shift-mode agent-day.
- In-envelope seat supply is now a CHECKED PRECONDITION of every shift-mode solve — a shortfall or a hours/library mismatch refuses before any solving occurs, naming the date, the shortfall, and every operator lever, instead of degrading into an irreducible hard score mislabelled as "Shift envelope compliance".
- The Agent Schedule table and Agent Allocation grid now render the same authoritative shift envelope plan 15-10 exposed on the backend, and surface envelope breaches and deliberately-unstaffed hours as visible, legended marks instead of a silently redrawn span.
- A shift desk built to the live defect's exact SHAPE (staggered multi-template envelopes, two hours genuinely outside every envelope, thin/zero edge demand, several consecutive days) now either reaches zero hard with observable ENVL-04 contiguity or is cleanly refused before solving — closing the gap the phase's existing suite could not detect at any scale — while all four characterising-test files are accounted for and the round's two deliberately-deferred defects are filed for the next planner.
- An ungated, default-suite guard on solver quality: a live-library-shaped synthetic desk solved five times (seeded, step-count terminated) through the shipped `solverConfig.xml`, asserting zero split shifts, zero edge breaks, and full edge-hour coverage on every seed via independent structural walkers, plus a median-violation-count trip-wire and pinned shipped-default weights — never a raw hard-score assertion.
- Seven new tests prove `SolverQualityGuardTest`'s three structural walkers can each go red on exactly their own injected defect, mechanically demonstrate that a weight change blinds the violation-count table to a defect the walker still sees, and the comparison rule G-15-29 demanded is now written down with measured evidence (including a new third live run) — both gaps closed in `15-UAT.md` with an explicit, honest statement of what was and was not back-tested.
- The accepted-path violation report now reads the persisted snapshot instead of mis-explaining an accepted schedule (closing the constant-1104 misreport, G-15-32), and a wrong HTTP verb answers 405 with a named-methods Allow header instead of 500 (G-15-26) — both proven with red-proofs, not just green tests.
- End-to-end usual-shift tracer: `agent_usual_shift` table with a real FK to `shift_template`, a single choke-point write endpoint, a resolve-by-name three-state roster discriminator, and seven Excel export columns — proven in one runnable test with no mocked layer, plus a real-Flyway-and-Postgres migration proof.
- Deleting a referenced shift template is now refused (T-16-09 closed), the roster computes all four D-16 states including the P-08 not-worked rule and P-07's RETIRED-first precedence, D-05's read-side hours advisory reuses `ShiftTemplate.getNetHours` with a bulk-loaded band map, and removing an agent from a desk clears their usual shifts through the same `UsualShiftService.clearUsualShifts` helper `clearDesk` will use.
- The per-desk upload template now pre-fills and shows a dropdown of each agent's stored usual shift (D-09/D-10), the parser reads all seven columns back with cell-level skip-and-warn semantics (D-07/D-08/D-03/P-12), and `clearDesk` wipes usual shifts the same way `removeDeskAgent` already does (D-11) — proven together as a download-then-re-upload no-op.
- Nine-row USHF-05 write-path table plus a static-source-scan structural completeness guard (dual independent derivation, set-equality-only, red-proven by both a code-level test-of-the-test and a real deliberate-break manual check) — closing the exact shape of v1.2 audit finding I-2, where a guarantee held on one write path and stayed open across two consecutive audits.
- A second line inside each of the roster's seven day tiles now shows an agent's usual shift — accent-blue bold when live, light-gray en dash when never set, italic muted with a reason when stored-but-inactive, plus an amber D-05 advisory marker — and clicking it opens a native `<select>` that sets, changes or clears the value in place via `setUsualShift`.
- Benchmark-gated V49 migration shipping `consistentStartWeight=2`/`preferredStartShiftModeWeight=1` — identical to the incumbent values, now backed by redone per-agent-day arithmetic, an honestly-reported null-result A/B, and a documented 12%-over-ceiling worst-case risk instead of V38's unexamined per-agent sizing.
- Drift Report tab (per-agent-date table + Most-Subscribed Usual Shifts ranking) and three new Constraint Weights rows including the page's first non-score field, both wired to the backend contract shipped by plans 17-01 through 17-04.

---

## v1.2 Unified Agent Provisioning (Shipped: 2026-08-25)

**Phases completed:** 5 phases, 23 plans, 53 tasks
**Timeline:** 2026-07-30 → 2026-08-25 (26 days, 252 commits)
**Code:** 105 files changed, +10,233 / −857 (src/ + frontend/)
**Requirements:** 19/19 checked off
**Closeout type:** `override_closeout`
**Known verification overrides:** 3 newly acknowledged, 0 carried forward from a prior close (see STATE.md Deferred Items)

**Delivered.** Mon–Sun contracted hours became a first-class data model. An operator downloads a
per-desk template, fills in seven day cells per agent (a number, `MANDATORY`, or `PTO`), uploads it,
and the system syncs BambooHR, merges by explicit precedence, reports what came from where — and
then shows the result back in the roster and the Excel export, resolved from the authoritative
`agent_day_hours` model rather than the retired scalar.

That last clause is the whole reason Phase 13 exists. The 2026-08-21 audit found the milestone had
built the storage and the parser but never migrated the *display*, so an operator verifying their
own upload saw a flat desk default. Phase 13 closed it.

### Known Gaps

Closed under override with these accepted, documented gaps. Full analysis in
`.planning/milestones/v1.2-MILESTONE-AUDIT.md`.

| ID | Severity | Gap |
|----|----------|-----|
| I-2 | high | Manual "Refresh from BambooHR" bypasses the Phase 11 merge engine entirely — overwrites spreadsheet-sourced identity data with no precedence rule and emits no merge report. Open across two consecutive audits; never in any phase's scope. Affects all seven MRG requirements in practice. |
| MRG-02 | partial | The one MRG requirement whose wording is not scoped to the upload event, and therefore the one genuinely violated as written by the Refresh path. Root cause I-2. |
| I-3 | medium | Bulk "Set all days to…" still deletes and recreates all seven rows with `dayOffType` unset, destroying MANDATORY/PTO labels. Mitigated by a `confirm()` naming the count at risk, and by a genuinely safe single-cell edit path — but the destructive write itself is unchanged. |
| NEW-1 | warning | The legacy `contractedHoursPerDay` scalar is still exported as its own column and can silently disagree with the per-day columns after any single-cell edit. |
| — | — | Phase 12 never reached `verification_status: passed` — deliberately withdrawn, not unverified. Phase 9 has no SECURITY.md. Phases 10 and 13 have `VALIDATION.md` at `status: draft`. |

**Phase 12 (Atomic Shift Move) was withdrawn, not shipped.** All three plans executed, but the
seeded benchmark put the move's effect (+0.25h median) inside the baseline's own 5.00h noise
spread, and it was inert at realistic 130% over-allocation. Code fully reverted in `299c42c`; the
planning artifacts are retained as the record. The goal is explicitly not claimed. Successor work
(cross-agent seat displacement) is filed as a todo — the 130% data indicates seat capacity, not
move granularity, is the binding constraint.

**Key accomplishments:**

- AgentNameSplitter utility implementing the D-06 first-whitespace split rule, plus Agent.firstName/lastName JPA columns, both proven by passing tests.
- AgentDayHours JPA entity and tenant-scoped Spring Data repository establishing the D-09 per-day-hours storage contract, TDD RED/GREEN verified against H2.
- Extracted `SolverService.resolveEffectiveHours` static resolver implementing exception-over-per-day-over-schedule-default precedence, threaded through all 3 former `getEffectiveHours` call sites, with a behaviour-equivalence unit test pinning Success Criterion 4.
- BambooHR refresh and desk-upload now populate firstName/lastName via the shared AgentNameSplitter, and desk-clear deletes stale per-day-hours rows — keeping the new agent data model coherent across every live write-path, not just the one-time migration.
- AgentResponse/DeskAgentResponse and the Excel export now surface firstName/lastName (D-08/D-12), and DeskAgentService.setContractedHours fans an operator hours edit out to all 7 agent_day_hours rows so the solver keeps honouring it post-migration (D-10).
- V29 Flyway migration SQL written and committed (name-split backfill + agent_day_hours fan-out); plan is PAUSED at a mandatory manual data-integrity checkpoint that has not yet been run.
- Nullable `day_off_type` column added to `agent_day_hours` (Flyway V30) plus a reflection-based structural test proving `BambooRefreshService` can never touch it — the refresh-safe storage foundation the Phase 10 parser (plan 03) writes MANDATORY/PTO into.
- Shared `EnrichedColumnLayout` utility (identity headers, day headers, unbounded Specialty-N detection, normalize, retired-shape markers) that closes the D-13 header-drift design tension across parser/template/export.
- Rewrote DeskAssignmentUploadService into a per-desk-sheet, EnrichedColumnLayout-driven parser: fractional-hours-safe day-cell parsing (hours/MANDATORY/PTO with non-silent >24 clamping), BambooHR-ID-only agent matching, unbounded Specialty N columns, and file-wide rejection of both retired upload shapes.
- Pre-seeded per-desk `.xlsx` template download (one sheet per desk, roster identity filled, schedule blank) sharing `EnrichedColumnLayout` with the parser and export, with server-side formula-injection sanitization.
- JUnit/Mockito/POI regression suite covering every rewritten-parser requirement (UPL-01..07), including the fractional-hours truncation regression and a reflection-based guard proving the parser can never delete BambooHR MANDATORY blocks
- Extended the Client Management Upload Results modal to render the backend's per-sheet rollup, clamp warnings, and unmatched-sheet notices, and added a pre-seeded per-desk template download button.
- Roster and its API now resolve every contracted-hours figure from `agent_day_hours` (schedule-default fallback, D-06), replacing the retired `Agent.contractedHoursPerDay`/`Desk.defaultContractedHoursPerDay` read path, and the roster UI gains a collapsed min-max summary plus an expandable per-weekday detail row with 5 distinct display states.
- New `PUT .../day-hours/{day}` endpoint and `DeskAgentService.setDayHours` upsert a single `agent_day_hours` row at a time — provably leaving the other six untouched — closing audit finding I-3 by construction, while the surviving seven-row bulk fan-out (`setContractedHours`) is pinned as transactional and explicitly label-destructive.
- The desk-agent Excel export now carries seven Mon–Sun columns resolved from `agent_day_hours` (not the retired scalar), and both specialty header strings in the upload template are sourced from a new `EnrichedColumnLayout.specialtyHeader(int)` factory instead of local literals.
- Every weekday in the roster's expanded row is now directly editable through a native-datalist type-or-pick combo covering all five stored states, and the destructive seven-row fan-out survives only as an explicitly labelled "Set all days to…" bulk action that warns before overwriting any MANDATORY/PTO label.
- Closed both `status: failed` UI-SPEC truths from 13-VERIFICATION.md — a shared `isEveryDayNotSet` predicate now mutes the collapsed roster cell when nothing was uploaded, `seedValueForEntry` now seeds the "Not set (default)" picklist literal instead of a blank input, and a client-side 0-24 range guard now blocks out-of-range bulk values before the destructive confirm() dialog.
- Inclusive 0-24 upper bound on the bulk contracted-hours endpoint, a `MethodArgumentTypeMismatchException` handler for malformed path segments, and a `@MockitoSpyBean`-injected mid-loop failure test proving the bulk fan-out's transactional rollback.

---

## v1.1 Schedule Quality & Reporting (Shipped: 2026-07-29)

**Status:** ⚠ Closed early — re-scoped. 2 of 4 planned phases delivered.

**Delivered:** Phases 5–6 (8 plans) | **Deferred:** Phases 7–8 (never planned)
**Requirements:** 4 of 16 shipped (25%)
**Timeline:** 2026-05-07 → 2026-07-29 (83 days, 74 commits, 99 files, +14,238/−144 LOC)

**Known deferred items at close:** 12 unshipped requirements (see STATE.md Deferred Items and ROADMAP.md Backlog 999.4–999.6)

### Key accomplishments

1. **BambooHR agent data enrichment** — sync now pulls `employmentHistoryStatus` (full-time/part-time) and job title onto `Agent`; operators can filter the agent list by employment type. (DATA-02)
2. **Non-schedulable job titles** — `JobTitleConfig` lets operators mark job titles as non-schedulable; those agents are excluded from both solver runs and desk allocation via `AgentEligibilityService`. (DATA-03)
3. **Desk bulk assignment upload** — header-based shape detection (6-col legacy and 16-col enriched), structured per-row failure reporting in an Upload Results modal; manual per-agent assignment retained. (DATA-01)
4. **Mandatory day-off import from BambooHR** — the long-dead `MANDATORY` code path was made real: field 4517 (`customWorkingdays`) added to the bulk `/reports/custom` fetch, a tolerant `WorkingDaysParser` handles the free-text live formats (wrapping ranges, "to" form, comma lists, annotations, spelling variants) without throwing, and recurring `MANDATORY` `AgentDayOff` rows are generated idempotently across the schedule horizon and honoured as hard solver blocks. (QUAL-01)
5. **Data-gap exclusion** — V28 migration adds `Agent.working_days_known`; agents with blank or `Variable` working-days patterns are excluded from solving rather than silently mis-scheduled, with outlier patterns logged.
6. **PTO correctness** — only `APPROVED` PTO creates hard blocks; `REQUESTED` is visible-only. The dead `"MANDATORY".equalsIgnoreCase(type)` string match was removed. BambooHR 503/429 rate limits now surface a human-readable retry message.

### Requirements outcome

| Shipped | Not shipped |
|---|---|
| DATA-01, DATA-02, DATA-03 (Phase 5) | QUAL-02, QUAL-03 — deferred out of Phase 6, never re-homed |
| QUAL-01 (Phase 6, re-scoped) | QUAL-04, RPT-01, RPT-02, DIAG-01, DIAG-02 — Phase 7 never planned |
| | QUAL-05, RPT-03, RPT-04, RPT-05, RPT-06 — Phase 8 never planned |

### Key decisions

| Decision | Rationale | Outcome |
|---|---|---|
| QUAL-01 re-scoped: solver *respects* fixed BambooHR weekends rather than *choosing* 2 contiguous days off | Discovered each employee has a fixed weekly pattern in BambooHR field 4517 — choosing would override real contracts | ✓ Good |
| Phase 6 narrowed to QUAL-01 only | The data foundation had to land before fairness/consistency constraints could be meaningful | ✓ Good — but QUAL-02/03 were never re-homed |
| Pull working days from BambooHR API, not the desk-upload spreadsheet | Automated sync, no manual upload dependency | ✓ Good |
| Timefold pinned at 1.33.0 | `ScoreAnalysis` moves to paid tier in 2.0 | ✓ Good |
| Fairness constraints soft-score only; quadratic penalties for hours consistency | Hard fairness makes schedules infeasible; linear penalties create score traps | — Pending (unbuilt) |
| `Agent.working_days_known` DEFAULT TRUE kept permanently | Avoids retro-flagging pre-existing agents as data gaps | ✓ Good |
| BambooHR key rotation gate bypassed | Operator directive 2026-07-29 — accepted risk | ⚠ Revisit — unresolved |

### Known gaps and technical debt

- **⚠ SECURITY — BambooHR API key never rotated.** `06-VERIFICATION.md` truth 7 FAILED; accepted via operator override on 2026-07-29. The exposed key (prefix `ad2bb…2be`) is still present in tracked planning docs in a **public** repository, and BambooHR-integration code has since been deployed to the sole live environment. Remediation is operator-owned and still outstanding.
- **BambooHR field 4517 is incomplete at source** — ~45% populated company-wide, ~24% parseable. Unparseable agents are silently excluded from solving. The desk-scale exclusion proportion on live desks (StubHub-GE, Vinted-UA) was never measured.
- **Data-gap and outlier surfacing is CloudWatch `log.warn` only** — the operator-facing UI was explicitly deferred to Phase 7 DIAG, which never ran.
- **`BambooRefreshServiceTest` idempotency test uses a hand-copied replica** of `persistRefreshData`'s generation loop rather than the real private method — future edits could diverge without test failure.
- **Live pixel-level confirmation** of MANDATORY red cells in the ScheduleResults PTO tab was approved by operator response, not independently driven; rendering code was verified statically.

---

## v1.0 AWS Deployment (Shipped: 2026-04-21)

**Status:** ⚠ Partially shipped — IAM blocker.

Phases 1–4. Phase 1 complete; Phases 2–4 partially delivered or deferred. 38 of 45 AWS resources provisioned (VPC, ECR, RDS PostgreSQL 16.6, ALB, CloudFront, S3 all live). IAM roles blocked by missing `iam:CreateRole` on `pete.cornwell@helpware.com` (PowerUserAccess excludes IAM). Deferred work tracked as Backlog 999.1–999.3.

Full details: `.planning/milestones/v1.0-ROADMAP.md`

---
