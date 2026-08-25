# Milestones

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
