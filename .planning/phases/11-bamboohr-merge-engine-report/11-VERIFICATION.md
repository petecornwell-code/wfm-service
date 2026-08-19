---
phase: 11-bamboohr-merge-engine-report
verified: 2026-08-19T00:00:00Z
status: human_needed
score: 6/6 must-haves verified
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Upload a spreadsheet that overrides several identity fields and inspect the Merge Report table in the browser (not just the JSON payload)."
    expected: "Table renders BambooHR ID / Agent / Field / BambooHR value / Sheet value / Outcome columns inside the 760px modal; amber 'BambooHR override' pill and blue 'Gap-filled by spreadsheet' pill render as specified; long job-title/email values wrap rather than overflow the modal (UI-SPEC E1/long-text, backstop)."
    why_human: "Visual wrapping/overflow at a specific pixel width cannot be confirmed by grep or a unit test; UI-SPEC explicitly marks this a backstop item requiring a held-out visual check with realistic long-value fixture data."
  - test: "Upload a sheet supplying a full week for an agent with a pathologically long name, whose working pattern BambooHR does not know."
    expected: "Green 'Newly eligible for scheduling' callout renders above the Merge Report table (not merged into it), and the long agent name wraps inside the 760px modal rather than overflowing."
    why_human: "Same backstop class as above (UI-SPEC E2/long-text) — rendering/wrapping is a browser-only observable."
  - test: "Trigger a BambooHR sync failure during upload (e.g. force a 503/timeout) and observe the toast."
    expected: "Toast.tsx renders the full lengthened MRG-07 message ('BambooHR sync failed (...) — no changes were made. Retry the upload once BambooHR is available.') without truncating the load-bearing 'no changes were made' clause or overflowing the viewport."
    why_human: "UI-SPEC flags both the toast's overflow behavior and its long-text truncation behavior as unconfirmed backstop items — Toast.tsx has a 400px maxWidth and no explicit wrapping rule was verified against this specific message length."
  - test: "Run two uploads concurrently against workbooks that touch the same desk (MRG-04/concurrency, backstop) and separately verify non-ASCII BambooHR values (e.g. a Georgian agent name) round-trip through the merge report and the sync-failure message without mojibake (MRG-07/encoding, backstop)."
    expected: "Each upload returns its own merge report with no cross-contamination; non-ASCII text renders correctly end-to-end."
    why_human: "Both are explicitly declared `verification: backstop` in the plan frontmatter — concurrency races and encoding round-trips are not exercised by the existing unit-test suite (all fixtures use ASCII names)."
  - test: "Confirm against a live BambooHR account (Company Settings → Field Alias) that custom field 4517 is actually returned under the JSON key `customWorkingdays` by the `/reports/custom` response."
    expected: "The key `customWorkingdays` is populated for real employees whose working-days pattern is set in BambooHR."
    why_human: "Code review IN-03: the report request asks for field id `4517` but the parser reads back the key `customWorkingdays` — if no field alias exists on the tenant's BambooHR account, this value is always null in production, meaning MRG-03's window arbitration and MRG-06's gap-fill/replace reporting would silently never activate for BambooHR-sourced patterns even though every unit test (which hand-constructs `BambooEmployee` fixtures) passes. This is pre-existing code, not modified by this phase, but phase 11 makes it far more load-bearing than before."
---

# Phase 11: BambooHR Merge Engine & Report Verification Report

**Phase Goal:** Every upload runs a fresh BambooHR sync and merges spreadsheet data against it using
documented per-field precedence — BambooHR authoritative where populated, spreadsheet filling gaps —
and the operator can see exactly which value came from which source.

**Verified:** 2026-08-19
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Uploading triggers a fresh BambooHR sync before any merge decision | ✓ VERIFIED | `DeskAssignmentUploadService.uploadDeskAssignments` calls `agentMergeService.fetchSnapshot(tenantId)` as the first statement (line 92), before the workbook is opened and before `transactionTemplate.executeWithoutResult` (line 216); `@Transactional` removed, `ensureCachePopulatedForUpload` removed (grep counts = 0). `UploadFreshSyncTest` asserts, via Mockito `InOrder`, that `listEmployees`/`listTimeOff` each ran exactly once and both preceded `executeWithoutResult`. Ran directly: `./gradlew test --tests "com.wfm.integration.UploadFreshSyncTest"` → BUILD SUCCESSFUL. |
| 2 | BambooHR's value used wherever it has data; spreadsheet fills gaps only | ✓ VERIFIED | `AgentMergeService.mergeIdentityFields` implements the D-06/D-07 rule exactly (`bambooHasData ? bambooValue : sheetValue`, entry only on genuine divergence/gap-fill); called for all six D-08 fields in `DeskAssignmentUploadService` (lines 386-409); both-blank correctly leaves the stored value untouched via the `hasData(merged)` guard (lines 414-418). `MergePrecedenceTest` covers populated/blank/whitespace-only combinations including `department_bambooWhitespaceOnly_...`, `email_sheetDiffersOnlyBySurroundingWhitespace_...`. Ran directly and passed. |
| 3 | BambooHR's dated PTO blocks the dates it covers; spreadsheet's recurring weekly PTO applies only to dates BambooHR has no record for | ✓ VERIFIED | `SolverService.arbitratePtoAgainstBambooWindow` drops every recurring PTO fact whose date lies inside the closed `[windowFrom, windowTo]` interval (both bounds inclusive), keeps facts outside it, never touches MANDATORY. Wired into `solve` before `buildAgentDaysOffMap`/`runPreSolveValidation`/`setAgentDaysOff` (lines 187-203). `PtoArbitrationTest` explicitly covers both exact boundary dates (`ptoExactlyOnTheWindowsFirstDayIsInsideAndDropped`, `...LastDayIsInsideAndDropped`) plus one day on each side. Ran directly and passed. |
| 4 | Operator sees a merge report in the Upload Results modal, per field, showing source and overrides | ✓ VERIFIED | `DeskAssignmentUploadResult.mergeReport` returned from the backend; `ClientManagement.tsx` renders a `Merge Report (N fields across M agents)` table (lines 530-580) with columns BambooHR ID/Agent/Field/BambooHR value/Sheet value/Outcome, guarded by a length check, inside the widened 760px modal. `MergeReportTest` proves silent agreement emits nothing, fixed field ordering, at-most-one-per-field, and byte-identical repeat-upload arrays. Ran directly and passed. See WARNING (WR-02) below for a minor rendering inconsistency in this same table. |
| 5 | Agent whose pattern BambooHR doesn't know but spreadsheet supplies becomes solver-eligible | ✓ VERIFIED | `DeskAssignmentUploadService` captures the pre-upload `workingDaysKnown` flag, sets it true and `workingDaysSource = SPREADSHEET` after all skip gates (lines 491-498); `newlyEligibleAgents` only gains a name on a false→true transition. `WorkingDaysKnownTest` feeds the captured `Agent` through the real `SolverService.filterEligible` and proves eligibility, plus three negative cases (inactive / disallowed job title / null specialization) still exclude the agent despite the flag. `WorkingDaysSourceGuardTest` proves a later BambooHR refresh with blank/`Variable` `customWorkingdays` cannot downgrade a spreadsheet-sourced agent (`shouldDowngradeWorkingDaysKnown`), closing the UAT 2026-08-12 hazard. Ran directly and passed. |
| 6 | BambooHR sync failure during upload gives a clear message and writes nothing | ✓ VERIFIED | `AgentMergeService.fetchSnapshot` wraps both fetches in one try; every catch rethrows (`BambooHRRateLimitedException` re-wrapped with the upload sentence, any other `RuntimeException` becomes `BambooHRSyncFailedException`), both carrying the fixed "no changes were made" clause and a "no detail available" fallback for a null/blank upstream reason. `GlobalExceptionHandler.handleBambooHRSyncFailed` maps to 503/`BAMBOOHR_SYNC_FAILED` (existing handler covers the rate-limit case at 503/`BAMBOOHR_RATE_LIMITED`). `UploadSyncFailureTest` verifies zero interactions with the agent repository, agent-day-hours repository and transaction template across four failure modes (rate-limited listEmployees, listTimeOff-after-listEmployees-succeeds, generic RuntimeException, blank upstream reason). Frontend's existing `showToast('error', getErrorMessage(err))` catch path (unchanged) surfaces `ex.getMessage()` verbatim. Ran directly and passed. |

**Score:** 6/6 truths verified (0 present-but-behavior-unverified)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/integration/AgentMergeService.java` | Pre-transaction snapshot fetch + per-field merge | ✓ VERIFIED | 219 lines, `class AgentMergeService`, `record BambooSnapshot`, `fetchSnapshot`, `mergeIdentityFields`, `mergeWorkingPattern` all present and wired |
| `src/main/java/com/wfm/dto/MergeReportEntry.java` | Ephemeral report row | ✓ VERIFIED | `public record MergeReportEntry(bamboohrId, agentName, field, bambooValue, sheetValue, outcome)` |
| `src/main/java/com/wfm/exception/BambooHRSyncFailedException.java` | Non-rate-limit sync failure | ✓ VERIFIED | `class BambooHRSyncFailedException extends RuntimeException`, message+cause constructor |
| `src/test/java/com/wfm/integration/UploadFreshSyncTest.java` | Fetch-precedes-transaction proof | ✓ VERIFIED | Present, passes |
| `src/test/java/com/wfm/integration/MergePrecedenceTest.java` | Field-by-field precedence coverage | ✓ VERIFIED | Present, passes |
| `src/test/java/com/wfm/integration/MergeReportTest.java` | Report shape/ordering/suppression | ✓ VERIFIED | Present, passes |
| `src/test/java/com/wfm/service/UploadSyncFailureTest.java` | Zero-write + message coverage | ✓ VERIFIED | Present, passes |
| `src/main/resources/db/migration/V36__add_agent_working_days_source.sql` | D-15 provenance column | ✓ VERIFIED | `working_days_source VARCHAR(20) NOT NULL DEFAULT 'BAMBOOHR'` |
| `src/main/java/com/wfm/model/WorkingDaysSource.java` | Provenance enum | ✓ VERIFIED | `enum WorkingDaysSource { BAMBOOHR, SPREADSHEET }` |
| `src/test/java/com/wfm/service/WorkingDaysKnownTest.java` | MRG-06 upload-side proof | ✓ VERIFIED | Present, passes, exercises real `filterEligible` |
| `src/test/java/com/wfm/integration/WorkingDaysSourceGuardTest.java` | D-15 refresh-side proof | ✓ VERIFIED | Present, passes |
| `src/test/java/com/wfm/service/PtoArbitrationTest.java` | MRG-03 window arbitration | ✓ VERIFIED | Present, passes, both boundary dates covered |
| `src/test/java/com/wfm/service/SheetPatternUnblockTest.java` | D-05 un-block proof | ✓ VERIFIED | Present, passes |
| `src/test/java/com/wfm/integration/WorkingPatternMergeTest.java` | Working-pattern report row | ✓ VERIFIED | Present, passes |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `DeskAssignmentUploadService` | `AgentMergeService` | `agentMergeService.fetchSnapshot` before the workbook opens | ✓ WIRED | Line 92 |
| `DeskAssignmentUploadService` | `TransactionTemplate` | sheet loop + writes run inside `executeWithoutResult` | ✓ WIRED | Line 216 |
| `ClientManagement.tsx` | `client.ts` | `uploadResult.mergeReport` typed `MergeReportEntry[]` | ✓ WIRED | Rendered lines 530-580 |
| `DeskAssignmentUploadService` | `WorkingDaysSource` | `WorkingDaysSource.SPREADSHEET` recorded on import | ✓ WIRED | Line 498 |
| `BambooRefreshService` | `Agent` | `shouldDowngradeWorkingDaysKnown` guards the downgrade | ✓ WIRED | Lines 93-95, 293 |
| `SolverService` | `SolverService.buildRecurringDaysOff` output | recurring PTO passes through `arbitratePtoAgainstBambooWindow` | ✓ WIRED | Lines 189-203, before `buildAgentDaysOffMap`/`setAgentDaysOff` |
| `SolverService` | `agent_day_hours` rows | `unblockSheetWorkedDays` un-blocks sheet-worked weekdays | ✓ WIRED | Lines 166-168, runs before recurring facts are added |
| `ClientManagement.tsx` | `client.ts` | `uploadResult.newlyEligibleAgents` | ✓ WIRED | Rendered lines 517-528 |

### Requirements Coverage

| Requirement | Source Plan | Status | Evidence |
|-------------|-------------|--------|----------|
| MRG-01 | 11-01 | ✓ SATISFIED | Truth 1 |
| MRG-02 | 11-01 | ✓ SATISFIED | Truth 2 |
| MRG-03 | 11-02 | ✓ SATISFIED | Truth 3 |
| MRG-04 | 11-01/11-02 | ✓ SATISFIED | Truth 4 |
| MRG-05 | 11-01 | ✓ SATISFIED | Truth 4, `MergeReportTest` ordering/dedup assertions |
| MRG-06 | 11-02 | ✓ SATISFIED | Truth 5 |
| MRG-07 | 11-01 | ✓ SATISFIED | Truth 6 |

All 7 requirement IDs (MRG-01…MRG-07) declared across `11-01-PLAN.md`/`11-02-PLAN.md` frontmatter match REQUIREMENTS.md's "Complete" status for Phase 11 exactly. No orphaned requirements.

### Anti-Patterns Found

No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers found in any file touched by this phase
(checked all 15 production files across both plans). No stub returns, no hardcoded-empty data flows to
rendering. `git status` clean at HEAD except unrelated `.gsd/` and research-cache scratch directories.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| All phase-11 targeted unit test classes pass | `./gradlew test --tests "com.wfm.integration.UploadFreshSyncTest" --tests "com.wfm.integration.MergePrecedenceTest" --tests "com.wfm.integration.MergeReportTest" --tests "com.wfm.service.UploadSyncFailureTest" --tests "com.wfm.service.WorkingDaysKnownTest" --tests "com.wfm.integration.WorkingDaysSourceGuardTest" --tests "com.wfm.service.PtoArbitrationTest" --tests "com.wfm.service.SheetPatternUnblockTest" --tests "com.wfm.integration.WorkingPatternMergeTest"` | BUILD SUCCESSFUL, `:test` task executed (not cached) | ✓ PASS |
| Frontend builds clean | `npm --prefix frontend run build` | `tsc -b && vite build` exit 0 | ✓ PASS |
| Full Gradle suite green at HEAD | (established in execution notes, not re-run here — 456s full-suite cost) | BUILD SUCCESSFUL per plan-time and execution-note record | ✓ PASS (not independently re-run this session; targeted re-run above is corroborating evidence) |
| No `AgentDayOffRepository` dependency added to the upload path (D-03) | `grep -n "AgentDayOffRepository" src/main/java/com/wfm/service/DeskAssignmentUploadService.java` | no matches | ✓ PASS |

### Requirements/COVERAGE.md Gate

`COVERAGE.md` exists at the phase directory (flagged in both SUMMARY files as "owed before phase seal").
Content reviewed: full `BambooHRClient` surface tabulated (`listEmployees`/`listTimeOff`/`getEmployee`),
all six read capabilities marked INTEGRATE, ten write/unused capabilities marked OPT-OUT with specific
reasons (write-back forbidden by D-03, PII minimization for benefits/compensation, etc.), plus phase-specific
notes on call-frequency change, the new HTTP timeout, and the pre-existing unrotated-API-key backlog item.
This satisfies the seal-time gate — **the "follow-up owed" item from both SUMMARY files is now closed.**

## Warnings (non-blocking, recommended follow-up)

These come from `11-REVIEW.md` (0 critical / 7 warning / 3 info) and my own independent reading of the
diff. None of them fail a ROADMAP success criterion or a plan must-have truth, but they are real defects
worth tracking:

1. **WR-01 (SolverService, real bug):** `allDaysOff.addAll(arbitratedRecurringDaysOff)` (line 203) has no
   deduplication against the persisted list. When BambooHR and the spreadsheet *agree* an off-day is
   MANDATORY (the common case), the solver ends up with two distinct `AgentDayOff` facts for the same
   `(agent, date)` — one persisted (BambooHR-origin), one freshly synthesized by `buildRecurringDaysOff`.
   Both flow into `schedule.setAgentDaysOff(...)` and the hard "Agent day off" constraint, roughly doubling
   that constraint's reported violation count/penalty for the affected agent-dates. Confirmed by reading
   `SolverService.java:149-203`; does not flip feasible↔infeasible, but does inflate the penalty numbers
   an operator sees on the schedule detail page. None of the phase's must-have truths assert deduplication,
   so this is not a phase-goal failure, but it is a correctness gap directly inside this phase's new code.

2. **WR-02 (AgentMergeService, cosmetic):** `mergeIdentityFields`'s gap-fill branch stores the raw
   (possibly `null`) `bambooValue` in `MergeReportEntry`, while `mergeWorkingPattern`'s gap-fill branch
   explicitly substitutes `"not stated"`. The frontend's `MergeReportEntry.bambooValue` TS type is
   non-nullable `string`; a `null` renders as an empty cell (React drops null children) rather than an
   explicit placeholder. Functionally harmless (the row still correctly shows "Gap-filled by spreadsheet"
   and the sheet's value) but inconsistent with the sibling working-pattern row's convention, and untested
   (`MergeReportTest.gapFilledField_emitsGapFilledOutcome_notOverride` never asserts `bambooValue()`).

3. **WR-03/WR-04 (diagnostics gap):** Neither `handleBambooHRRateLimited` nor the new
   `handleBambooHRSyncFailed` logs server-side (`log.warn`/`log.error`) before returning the 503. The
   operator-facing message is still correct (satisfies truth 6), but a BambooHR outage produces zero
   server-log signal to correlate against a spike of failed-upload reports.

4. **IN-03 (pre-existing, now more load-bearing):** `HttpBambooHRClient` requests custom field `"4517"`
   but parses the response by the JSON key `"customWorkingdays"`. If the tenant's live BambooHR account has
   no field alias mapping `4517` → `customWorkingdays`, this value is silently always `null` in production —
   meaning MRG-03's window arbitration would have nothing BambooHR-side to arbitrate against for the
   working-pattern field, and MRG-06's gap-fill/replace reporting would always read as "gap-filled," never
   "replaced" or silently agreeing. All of this phase's unit tests construct `BambooEmployee` fixtures by
   hand, so they cannot catch a live field-alias mismatch. Not part of this diff, but phase 11 is the first
   phase to make this value central to solver eligibility and PTO precedence rather than just a display
   field, so it deserves explicit confirmation now rather than being carried silently forward.

## Human Verification Required

See frontmatter `human_verification` — five items: three UI-SPEC "backstop" visual checks (Merge Report
long-value wrapping, eligibility-callout long-name wrapping, sync-failure toast overflow/truncation), one
concurrency+encoding backstop pair (MRG-04/concurrency, MRG-07/encoding — both explicitly declared
`verification: backstop` in the 11-01-PLAN.md frontmatter and not exercised by any existing test, whose
fixtures are all ASCII and sequential), and one production-data confirmation (IN-03's BambooHR field-alias
check). None of these were fabricated by this phase's own planning as an oversight — they are the plan's
own explicitly-declared backstop items (UI-SPEC marks 4 backstop rows; 11-01-PLAN.md marks 2 more) that
were deliberately deferred past automated coverage and never subsequently closed by a documented manual
check in either SUMMARY.md.

## Gaps Summary

No blocking gaps. Every ROADMAP success criterion and every plan must-have truth checked against the
actual codebase (not SUMMARY.md claims) resolves to VERIFIED, backed by code that matches the design and
targeted tests that were re-run directly in this verification session and passed. All 7 requirement IDs
are accounted for with no orphans. COVERAGE.md — flagged by both plans as "owed before phase seal" — exists
and is substantively complete, closing that follow-up.

The phase is **functionally complete** but carries a defined stack of backstop items (declared by the
plan itself as needing a browser/manual check, never subsequently closed) and four code-review warnings
that don't fail any must-have but are worth a human decision on whether to fix now or file as follow-up
work before this phase is considered fully closed out.

---

_Verified: 2026-08-19_
_Verifier: Claude (gsd-verifier)_
