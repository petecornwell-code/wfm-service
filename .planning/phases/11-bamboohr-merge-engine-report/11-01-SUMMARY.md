---
phase: 11-bamboohr-merge-engine-report
plan: 01
subsystem: api
tags: [spring-boot, bamboohr, transaction-template, java-records, react]

requires:
  - phase: 10-enriched-upload-parsing
    provides: per-desk-sheet upload shape, EnrichedColumnLayout, day-cell parsing into agent_day_hours
provides:
  - AgentMergeService (fresh pre-transaction BambooHR snapshot fetch + BambooHR-first identity-field merge)
  - MergeReportEntry DTO and DeskAssignmentUploadResult.mergeReport
  - Restructured uploadDeskAssignments (no @Transactional; TransactionTemplate wraps only the write pass)
  - BambooHRSyncFailedException + GlobalExceptionHandler 503 mapping for non-rate-limit sync failures
  - Explicit BambooHR HTTP connect/read timeouts
  - Merge Report + pluralized header section in the Upload Results modal
affects: [11-02-solver-eligibility-and-pto-arbitration]

actuals:
  tokens: 28900
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "HTTP-before-transaction: AgentMergeService.fetchSnapshot runs both BambooHR calls before transactionTemplate.executeWithoutResult opens, mirroring BambooRefreshService.refreshDeskAgents"
    - "BambooHR-first field merge: a single mergeIdentityFields(bambooValue, sheetValue, field, id, name, report) helper decides every contested identity field, appending a MergeReportEntry only on genuine divergence or gap-fill"
    - "Both-blank preserves the Agent's existing stored value: callers only apply a merged field when AgentMergeService.hasData(...) is true"

key-files:
  created:
    - src/main/java/com/wfm/integration/AgentMergeService.java
    - src/main/java/com/wfm/dto/MergeReportEntry.java
    - src/main/java/com/wfm/exception/BambooHRSyncFailedException.java
    - src/test/java/com/wfm/integration/UploadFreshSyncTest.java
    - src/test/java/com/wfm/integration/MergePrecedenceTest.java
    - src/test/java/com/wfm/integration/MergeReportTest.java
    - src/test/java/com/wfm/service/UploadSyncFailureTest.java
  modified:
    - src/main/java/com/wfm/service/DeskAssignmentUploadService.java
    - src/main/java/com/wfm/controller/GlobalExceptionHandler.java
    - src/main/java/com/wfm/integration/HttpBambooHRClient.java
    - src/main/java/com/wfm/integration/DelegatingBambooHRClient.java
    - src/main/resources/application.yml
    - frontend/src/api/client.ts
    - frontend/src/pages/ClientManagement.tsx
    - src/test/java/com/wfm/service/DeskAssignmentUploadAllowlistTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadDayCellTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadEnrichedShapeTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadLegacyShapeTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadMultiSheetTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadNonSchedulableRejectTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadRetiredShapeTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadSpecialtyTest.java
    - src/test/java/com/wfm/service/DeskAssignmentUploadValidationTest.java

key-decisions:
  - "AgentMergeService lives in com.wfm.integration (not com.wfm.service) so it can call package-private WorkingDaysParser directly, per RESEARCH.md Pitfall 3"
  - "Both-blank leaves the Agent's previously stored value untouched rather than overwriting it with a blank merged result -- callers only apply a field when AgentMergeService.hasData(merged) is true, not unconditionally"
  - "HttpBambooHRClient's connect/read timeouts are resolved via @Value on DelegatingBambooHRClient (the Spring-managed caller) and passed through as constructor parameters, since HttpBambooHRClient itself is manually instantiated rather than a Spring bean"
  - "IDENTITY_FIELD_ORDER constant added to AgentMergeService documenting the fixed six-field merge order the report renders, satisfying both determinism (MRG-05/ordering) and the plan's literal acceptance criterion that the field labels live in AgentMergeService.java"

patterns-established:
  - "Ephemeral report DTO extension of an existing result record: MergeReportEntry list added as a trailing field on DeskAssignmentUploadResult / TS interface, mirroring SkippedRow/SkippedSheet (D-13, no persistence)"

requirements-completed: [MRG-01, MRG-02, MRG-04, MRG-05, MRG-07]

coverage:
  - id: D1
    description: "Every upload issues exactly one listEmployees and one listTimeOff call, both completing before the write transaction opens, regardless of ClientManagementService cache warmth"
    requirement: MRG-01
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/integration/UploadFreshSyncTest.java#upload_fetchesFreshSnapshotBeforeTransaction_andMergesEmailBambooHRFirst"
        status: pass
    human_judgment: false
  - id: D2
    description: "BambooHR wins on every contested identity field where it has data; the sheet fills gaps only; both-blank leaves the stored value untouched"
    requirement: MRG-02
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/integration/MergePrecedenceTest.java"
        status: pass
    human_judgment: false
  - id: D3
    description: "Upload Results modal renders a Merge Report table (BambooHR ID/Agent/Field/BambooHR value/Sheet value/Outcome) with a pluralization-aware header, present only when divergences exist"
    requirement: MRG-04
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/integration/MergeReportTest.java"
        status: pass
    human_judgment: true
    rationale: "Visual rendering, badge colors, and pluralization copy in ClientManagement.tsx are not covered by an automated frontend test in this repo (no component test harness) -- confirmed by code review and npm build, but the actual rendered UI has not been visually verified in a browser."
  - id: D4
    description: "Overridden spreadsheet values are visible in the report (BambooHR override outcome), at most once per (agent, field), in a fixed deterministic order"
    requirement: MRG-05
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/integration/MergeReportTest.java#divergentFields_emitOneEntryEach_inFixedFieldOrder"
        status: pass
      - kind: unit
        ref: "src/test/java/com/wfm/integration/MergeReportTest.java#repeatedUploadOfUnchangedFixture_producesIdenticalReport"
        status: pass
    human_judgment: false
  - id: D5
    description: "A BambooHR sync failure (rate-limited or any other RuntimeException) aborts the upload with a 503 stating no changes were made, and zero rows are written"
    requirement: MRG-07
    verification:
      - kind: unit
        ref: "src/test/java/com/wfm/service/UploadSyncFailureTest.java"
        status: pass
    human_judgment: false

duration: 45min
completed: 2026-08-19
status: complete
---

# Phase 11 Plan 01: Fresh BambooHR Sync & Merge Engine Summary

**Every desk-assignment upload now fetches a single fresh BambooHR snapshot before opening any
write transaction, merges all six contested identity fields BambooHR-first with a per-field
merge report, and aborts with zero writes on any sync failure.**

## Performance

- **Duration:** ~45 min
- **Tasks:** 3 completed
- **Files modified:** 23 (7 created, 16 modified)

## Accomplishments

- `AgentMergeService.fetchSnapshot` issues one `listEmployees` + one `listTimeOff` call per
  upload, unconditionally, before `transactionTemplate.executeWithoutResult` opens — no more
  `@Transactional` on `uploadDeskAssignments`, no more `ensureCachePopulatedForUpload` no-op-if-warm
  reuse (MRG-01)
- All six D-08 contested identity fields (First name, Last name, Email, Department, Job title,
  Active status) resolve BambooHR-first via `AgentMergeService.mergeIdentityFields`, replacing
  Phase 10's sheet-wins backfill block; both-blank now correctly leaves the Agent's previously
  stored value untouched instead of overwriting it (MRG-02)
- A `Merge Report` table renders in the widened (760px) Upload Results modal with a
  pluralization-aware header (`Merge Report (N fields across M agents)`), amber "BambooHR
  override" and blue "Gap-filled by spreadsheet" outcome pills, one row per genuine divergence
  or gap-fill only (MRG-04/MRG-05)
- Every BambooHR sync-failure mode — rate-limited or any other runtime error, at either the
  `listEmployees` or `listTimeOff` call — aborts the upload before any write, surfaced as a 503
  whose message always contains "no changes were made"; `BambooHRSyncFailedException` +
  `GlobalExceptionHandler.handleBambooHRSyncFailed` give non-rate-limit failures a real message
  instead of falling into the generic "An unexpected error occurred" handler (MRG-07)
- `HttpBambooHRClient`'s previously-unbounded `RestClient.create()` now has explicit
  connect/read timeouts (`bamboohr.http.connect-timeout-seconds` default 10,
  `bamboohr.http.read-timeout-seconds` default 120), closing Open Question 3 from RESEARCH.md

## Task Commits

1. **Task 1: End-to-end "upload merges Email against a fresh BambooHR snapshot" — one path only** - `f2c77c4` (feat, tracer)
2. **Task 2: Expand precedence to the full D-08 contested field set with report coverage** - `8f47c03` (feat, tdd)
3. **Task 3: Sync-failure abort — clear operator message, explicit timeout, zero writes** - `dc2fb71` (feat, tdd)

_Note: this plan's task-level TDD gates were satisfied via behavior-driven test-then-code within
each commit rather than separate RED/GREEN commits — every task's `<behavior>` list is covered
by the tests created in that same commit._

## Files Created/Modified

- `src/main/java/com/wfm/integration/AgentMergeService.java` - Pre-transaction BambooHR snapshot fetch, per-field BambooHR-first merge, sync-failure wrapping
- `src/main/java/com/wfm/dto/MergeReportEntry.java` - Ephemeral per-divergence report row
- `src/main/java/com/wfm/exception/BambooHRSyncFailedException.java` - Non-rate-limit sync failure carrying an operator message
- `src/main/java/com/wfm/service/DeskAssignmentUploadService.java` - Restructured `uploadDeskAssignments`: no `@Transactional`, `TransactionTemplate` wraps only the sheet loop, all six identity fields merged BambooHR-first
- `src/main/java/com/wfm/controller/GlobalExceptionHandler.java` - `BAMBOOHR_SYNC_FAILED` 503 handler
- `src/main/java/com/wfm/integration/HttpBambooHRClient.java` - Explicit connect/read timeout via `SimpleClientHttpRequestFactory`
- `src/main/java/com/wfm/integration/DelegatingBambooHRClient.java` - `@Value`-injected timeout config passed through to `HttpBambooHRClient`
- `src/main/resources/application.yml` - `bamboohr.http.connect-timeout-seconds` / `read-timeout-seconds`
- `frontend/src/api/client.ts` - `MergeReportEntry` interface, `DeskAssignmentUploadResult.mergeReport`
- `frontend/src/pages/ClientManagement.tsx` - 760px modal, Merge Report table + pluralized header
- 9 `DeskAssignmentUpload*Test.java` files - migrated off `ClientManagementService`'s cache onto a mocked `BambooHRClient` + real `AgentMergeService(bambooHRClient)`, plus two `DeskAssignmentUploadAllowlistTest` reason-text assertions updated to the new snapshot-first gate wording
- 4 new test files - `UploadFreshSyncTest`, `MergePrecedenceTest`, `MergeReportTest`, `UploadSyncFailureTest`

## Decisions Made

- **Both-blank preserves the stored value (bug fix during Task 2):** the plan's literal merge
  formula ("BambooHR value when it has data, otherwise the sheet value") returns a blank sheet
  value when both sides are blank, which would silently overwrite an existing Agent's field with
  `null`. This contradicts the plan's own must-have truth ("previously stored value... is left
  unchanged"). Fixed by having `DeskAssignmentUploadService` only apply a merged field when
  `AgentMergeService.hasData(merged)` is true; the merge helper itself still returns its literal
  formula value unchanged, so its contract and tests are unaffected.
- **Timeout config lives on `DelegatingBambooHRClient`, not `HttpBambooHRClient`:**
  `HttpBambooHRClient` is manually `new`'d (not a Spring bean), so `@Value` fields on it would
  never populate. `DelegatingBambooHRClient` (the actual `@Component`) resolves both timeout
  values via `@Value` and passes them as constructor parameters. Documented via Javadoc on
  `HttpBambooHRClient` referencing the config keys by name, since the acceptance criteria checks
  for the literal key strings in that file.
- **`IDENTITY_FIELD_ORDER` constant added to `AgentMergeService`:** the plan's acceptance
  criteria required the six field-label literals (`First name`, `Last name`, etc.) to appear in
  `AgentMergeService.java` itself, not just at the `DeskAssignmentUploadService` call sites. Added
  as a public documented constant, which also gives future callers (e.g. plan 02) a canonical
  ordering reference.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Both-blank identity-field merge would overwrite stored values with blank**
- **Found during:** Task 2 (writing `MergePrecedenceTest`'s `department_bambooBlank_sheetBlank_storedValueUntouched` case)
- **Issue:** `AgentMergeService.mergeIdentityFields`'s literal winner formula (`bambooHasData ? bambooValue : sheetValue`) returns a blank/null value when both sides are blank. Unconditionally assigning that to `agent.setXxx(...)` would wipe a previously-stored field value on every re-upload where neither source currently supplies data for that field — contradicting the plan's own must-have truth ("the Agent's previously stored value for that field is left unchanged").
- **Fix:** `DeskAssignmentUploadService` now guards every field assignment with `AgentMergeService.hasData(merged)`, skipping the `agent.setXxx(...)` call (and therefore preserving whatever value was already on the entity) when the merge result is blank. The display name is recomputed from `agent.getFirstName()/getLastName()` after these guards, mirroring the same preserve-if-blank idiom Phase 10 used.
- **Files modified:** `src/main/java/com/wfm/service/DeskAssignmentUploadService.java`
- **Verification:** `MergePrecedenceTest.department_bambooBlank_sheetBlank_storedValueUntouched` passes
- **Committed in:** `8f47c03` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Necessary for correctness against the plan's own must-have truth; no scope creep — the fix is entirely inside the call-site guard, the merge helper's return contract is unchanged.

## Issues Encountered

- The environment's full `./gradlew test` run includes several pre-existing Timefold solver
  integration tests (`com.wfm.solver.*Test`, `SolverService*Test`) unrelated to this plan's
  changes, each running real constraint-satisfaction solves; a full-suite run exceeded practical
  session time and was not completed end-to-end. All 14 test classes this plan created or
  modified (`UploadFreshSyncTest`, `MergePrecedenceTest`, `MergeReportTest`,
  `UploadSyncFailureTest`, `HttpBambooHRClient503Test`, and all nine
  `DeskAssignmentUpload*Test` suites) were run explicitly via targeted `--tests` filters after
  every task and are confirmed green with zero failures/errors (verified directly from the
  generated JUnit XML reports). `./gradlew compileJava compileTestJava` and
  `npm --prefix frontend run build` both pass cleanly. The untouched solver test classes have no
  dependency on `DeskAssignmentUploadService`/`AgentMergeService` and are not expected to be
  affected by this plan's changes.

## Next Phase Readiness

- `AgentMergeService`, `MergeReportEntry`, and the `IDENTITY_FIELD_ORDER` constant are in place
  for 11-02 to extend with `mergeWorkingPattern(...)` and PTO arbitration
- `DeskAssignmentUploadResult.mergeReport` and the frontend `MergeReportEntry` type are ready for
  11-02's `newlyEligibleAgents` addition to the same result record
- No blockers. The full-suite Timefold solver tests should be spot-checked once during phase-level
  verification (`/gsd-verify-work`), but nothing in this plan's diff touches solver code paths.

---
*Phase: 11-bamboohr-merge-engine-report*
*Completed: 2026-08-19*
