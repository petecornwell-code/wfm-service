# Milestones

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
