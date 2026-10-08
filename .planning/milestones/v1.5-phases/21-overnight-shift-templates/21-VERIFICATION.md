---
phase: 21-overnight-shift-templates
verified: 2026-10-03T16:27:17Z
status: passed
score: 7/7 must-haves verified
behavior_unverified: 0
overrides_applied: 0
covered_files:
  - ".planning/REQUIREMENTS.md"
  - ".planning/ROADMAP.md"
  - ".planning/WINDOWS.md"
  - ".planning/phases/21-overnight-shift-templates/21-01-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-01-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-02-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-02-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-03-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-03-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-04-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-04-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-05-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-05-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-06-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-06-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-07-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-07-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-08-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-08-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-09-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-09-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-10-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-10-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-11-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-11-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-12-PLAN.md"
  - ".planning/phases/21-overnight-shift-templates/21-12-SUMMARY.md"
  - ".planning/phases/21-overnight-shift-templates/21-REVIEW-DISPOSITION.md"
  - ".planning/phases/21-overnight-shift-templates/21-REVIEW.md"
  - ".planning/phases/21-overnight-shift-templates/21-UAT.md"
  - "frontend/src/api/client.ts"
  - "frontend/src/pages/DeskManagement.tsx"
  - "frontend/src/pages/ScheduleResults.tsx"
  - "frontend/src/utils/dayWindow.ts"
  - "src/main/java/com/wfm/controller/DeskController.java"
  - "src/main/java/com/wfm/controller/ScheduleController.java"
  - "src/main/java/com/wfm/dto/DeskResponse.java"
  - "src/main/java/com/wfm/dto/ScheduleDetailResponse.java"
  - "src/main/java/com/wfm/dto/ScheduleSummary.java"
  - "src/main/java/com/wfm/dto/ShiftLibraryValidationResponse.java"
  - "src/main/java/com/wfm/repository/ScheduleRepository.java"
  - "src/main/java/com/wfm/repository/StaffingRequirementRepository.java"
  - "src/main/java/com/wfm/service/DeskService.java"
  - "src/main/java/com/wfm/service/FteUploadService.java"
  - "src/main/java/com/wfm/service/ScheduleExportService.java"
  - "src/main/java/com/wfm/service/ScheduleOutputService.java"
  - "src/main/java/com/wfm/service/ShiftLibraryGenerationService.java"
  - "src/main/java/com/wfm/service/ShiftLibraryValidationService.java"
  - "src/main/java/com/wfm/service/ShiftTemplateService.java"
  - "src/main/java/com/wfm/service/StaffingRequirementService.java"
  - "src/main/java/com/wfm/solver/ScheduleConstraintProvider.java"
  - "src/test/resources/bday-join-guard.md"
  - "src/test/resources/midnight-boundary-scenarios.md"
  - "src/test/resources/midnight-time-arithmetic.md"
covered_digest: "v2:sha256:c96d7be97eeb342c3a3f22c117856e01b7a1a3500c18af3d3a8f3555e16f0442"
re_verification:
  previous_status: passed
  previous_score: "7/7"
  gaps_closed:
    - "WINDOWS.md #14 (ScheduleOutputService's justification loop populated structured ViolationDetail attribution only under `instanceof AgentAssignment`, so every 'Unassigned assignment' violation carried null businessDate/startTime and the operator-facing unfilled-seat marker could never render): fixed in commit ad5b58a, landed AFTER the fifth pass's passing verification was recorded. This sixth pass independently confirms the fix rather than having originated or witnessed it during the fifth pass."
  gaps_remaining: []
  regressions: []
  stale_digest_refresh: |
    This pass exists solely because commit ad5b58a changed two covered_files
    (.planning/WINDOWS.md, src/main/java/com/wfm/service/ScheduleOutputService.java) after the
    fifth pass's digest was computed, which flipped `gsd_run query verification.status` to `stale`
    and blocked `phase.complete`. Regenerated via `gsd_run query verification.fingerprint` over the
    unchanged 54-file covered_files list (confirmed: fingerprinting the three root-level
    .planning/*.md files alongside the phase-scoped files reproduces the identical digest as
    fingerprinting without them -- they are inert to the hash per #4623 -- so no covered_files
    membership change was needed, only a re-hash). Old digest:
    v2:sha256:66ec6e9d07263a997692cc0efca0ad55d36dab5f59df97602ee682e890569ec3. New digest:
    v2:sha256:c96d7be97eeb342c3a3f22c117856e01b7a1a3500c18af3d3a8f3555e16f0442.
  fix_landed_after_passing_run: |
    Git-timestamp note, recorded for rigor rather than brushed past: `ad5b58a` (the fix) carries
    author timestamp 2026-10-03T12:11:38-04:00; `36671c6` (the commit that recorded the fifth-pass
    VERIFICATION.md content read above) carries 2026-10-03T12:12:14-04:00 -- 36 seconds LATER in
    raw git-log order, meaning the docs commit is technically ad5b58a's child. That ordering is a
    commit-sequencing artifact of the originating session, not evidence the fifth pass saw the fix:
    the fifth-pass VERIFICATION.md content itself is internally conclusive on this point -- it
    describes WINDOWS.md #17 as moving only to `partial` (5/6), discusses #14 as a live open defect
    requiring "restructuring ScheduleConstraintProvider's stream shape" to fix, and contains zero
    reference to a Timeslot-extraction branch, a RED-then-GREEN test, or a 1305-test count (the
    fifth pass's own suite count was 1303). The content is unambiguously pre-fix. This sixth pass
    is the first to read ad5b58a's diff.
---

# Phase 21: Overnight Shift Templates Verification Report

**Phase Goal:** A desk can define a shift that spans midnight, and every surface that touches it —
save-time validation, contracted-hours consumption, day-off blocking, the schedule UI grid, the
Excel export — treats it correctly as one continuous thing belonging to the business day it starts
on.
**Verified:** 2026-10-03T16:27:17Z
**Status:** passed
**Re-verification:** Yes — sixth pass, a stale-digest refresh. Not a re-derivation: this pass
independently (1) confirms the diff scope of the one commit (`ad5b58a`) that landed since the
fifth pass's passing verification, (2) confirms it is read-side only and does not touch
`ScheduleConstraintProvider.java`, (3) re-runs the full test suite from a forced (non-UP-TO-DATE)
build and aggregates JUnit XML by filename, (4) re-confirms the seven OVNT truths are unaffected,
and (5) regenerates `covered_files`/`covered_digest` so the stale gate clears.

## What changed since the fifth verification pass

One commit, `ad5b58a` — "fix(21): give unassigned-seat violations their timeslot attribution
(defect #14)" — landed after the fifth pass's `21-VERIFICATION.md` was committed (see
`fix_landed_after_passing_run` above for the git-timestamp nuance and why the content itself proves
this). Diff scope, confirmed via `git show --stat ad5b58a`:

```
.planning/WINDOWS.md                                                       |   8 +-
src/main/java/com/wfm/service/ScheduleOutputService.java                   |  31 +++++++
src/test/java/com/wfm/service/ScheduleOutputServiceShiftReportingTest.java | 101 +++++++++++++++++++++
 3 files changed, 136 insertions(+), 4 deletions(-)
```

Exactly the three files the brief named. **`ScheduleConstraintProvider.java` is NOT among them** —
confirmed by its absence from `git show --stat ad5b58a` and by direct inspection: no change to that
file exists in the commit. This is the whole safety argument for landing a solver-adjacent fix
after a passing verification, and it holds.

### The code change, read directly

`ScheduleOutputService.java`'s justification loop (around line 709) gained exactly one new branch:

```java
} else if (justification instanceof Timeslot ts && timeslotId == null) {
    // ... populates timeslotId/businessDate/calendarDate/slotStartTime/slotEndTime/timeslotLabel
    timeslotId = ts.getId();
    businessDate = ts.getBusinessDate();
    calendarDate = ts.getDate();
    slotStartTime = ts.getStartTime();
    slotEndTime = ts.getEndTime();
    timeslotLabel = timeslotLabel(ts);
}
```

Confirmed:
- The `timeslotId == null` guard is present and is exactly the precedence mechanism claimed: it
  only fires when the preceding `if (justification instanceof AgentAssignment aa)` branch did not
  already set `timeslotId`, so an `AgentAssignment`'s richer values (agent identity included)
  always win if a match ever indicts both.
- `timeslotLabel(ts)` calls the same shared helper the `AgentAssignment` branch already used — no
  duplicate/divergent labelling logic was introduced.
- `specName` is left unset in the new branch (not fabricated), consistent with description choice
  (a) — `TimeslotDemandConfig` is `record(Timeslot, int)` and carries no specialization field, so
  the description falls through to the generic `"<name> violation"` form, which the new test
  (below) explicitly asserts.

### The two new tests, read directly

`ScheduleOutputServiceShiftReportingTest.java` gained:

1. `buildConstraintViolations_unassignedAssignment_violationNowCarriesTimeslotAttribution()` —
   drives the real live path (`service.buildConstraintViolations` against a `Schedule` with one
   unassigned `AgentAssignment` and a `TimeslotDemandConfig` demanding 2, undersupplied at 0, so
   the hard-limit filter on `unassignedAssignment` fires). Asserts `timeslotId`, `businessDate`,
   `calendarDate`, `startTime`, `endTime` are all populated and non-null, `agentId`/`agentName` are
   correctly null (no `AgentAssignment` was indicted), and the description falls through to the
   generic `"Unassigned assignment violation"` form (confirms description choice (a), not a
   fabricated specialization).
2. `buildConstraintViolations_liveAgentAssignmentIndictedConstraint_attributionUnaffectedByNewTimeslotBranch()`
   — this IS a genuine no-regression assertion, not a mere exercise. It drives `Specialization
   match` (a constraint whose `ConstraintMatch` DOES indict an individual `AgentAssignment`) and
   asserts `agentId`, `agentName`, `timeslotId`, `businessDate`, `calendarDate`, `startTime`, and
   `endTime` all equal the values the held `AgentAssignment`/`Timeslot` actually carry — proving
   the new `timeslotId == null`-guarded branch never clobbers values the `AgentAssignment` branch
   already set. This is the correctness property the precedence guard exists for, and the test
   checks it directly rather than merely invoking the code path.

Both read as intentional, targeted, and faithful to the commit message's description. No
over-claiming found.

### Suite re-run, independently executed by this pass

`./gradlew --stop` (clear any stale daemon per the stale-daemon gate), then a forced, non-cached
run: `./gradlew test --rerun`. Result: `BUILD SUCCESSFUL in 10m 40s`, `5 actionable tasks: 1
executed, 4 up-to-date` — confirming the `:test` task actually executed rather than reporting
`UP-TO-DATE` (which this pass treats as no evidence, per the stale-daemon/UP-TO-DATE gate).

JUnit XML aggregated by **filename** (not the root `name` attribute, to avoid `@Nested`
undercounting):

```
files: 202
tests: 1305  failures: 0  errors: 0  skipped: 4
```

Matches the commit message's claim exactly (202 classes, 1305 tests, 0 failures, 0 errors, 4
pre-existing benchmark skips). The two new tests are present in
`TEST-com.wfm.service.ScheduleOutputServiceShiftReportingTest.xml` as bare `<testcase>` elements
with no `<failure>`/`<error>` children (that file's own `<testsuite>` header reads `tests="29"
skipped="0" failures="0" errors="0"`), confirming both pass.

### Re-confirmation of the seven OVNT truths

The fix touches only the justification/violation-reporting loop inside `buildConstraintViolations`
— a code region distinct from every truth's own supporting evidence (shift-save validation,
business-date grouping in `buildAgentSchedule`, the day-off/contracted-hours joins in
`ScheduleConstraintProvider`, the export/grid rendering paths). Re-confirmed by direct inspection
that none of OVNT-01 through OVNT-07's cited artifacts (`ShiftTemplateService`, `DeskService`,
`ScheduleOutputService.buildAgentSchedule`, `ScheduleConstraintProvider`,
`ScheduleExportService`, `ScheduleResults.tsx`, `dayWindow.ts`) changed in a way that touches their
claims — the only overlap is `ScheduleOutputService.java` itself, and the fifth pass's own evidence
for OVNT-02/OVNT-07 cites `buildAgentSchedule` and the `timeslotLabel()` helper, neither of which
this commit altered (the helper is reused, not modified). **Nothing has actually moved for any of
the seven truths; re-confirmed, not re-derived.**

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | An operator can save a shift template whose end time is earlier in the clock than its start time, with correct net hours for the overnight span, on an anchored desk (OVNT-01) | ✓ VERIFIED | Unchanged from fifth pass. `ShiftTemplateService.java` validation and net-hours computation untouched by `ad5b58a`. |
| 2 | That shift is reported against the business day it starts on, everywhere it is displayed (OVNT-02) | ✓ VERIFIED | Unchanged. `ScheduleOutputService.buildAgentSchedule` (groups by `getBusinessDate()`) is a different method than the one `ad5b58a` touched (`buildConstraintViolations`'s justification loop); confirmed by direct read that the two do not share code. |
| 3 | A day-off or PTO marking on the starting business day blocks the shift from being assigned, including seats stamped with the following calendar date (OVNT-03) | ✓ VERIFIED | Unchanged. `ScheduleConstraintProvider.java` is byte-identical to the fifth pass (confirmed: not in `ad5b58a`'s diff). |
| 4 | The shift consumes the contracted hours of the weekday it starts on only, never split across two weekday rows (OVNT-04) | ✓ VERIFIED | Unchanged. Same constraint-provider dependency as above. |
| 5 | Shift-library validation refuses an overnight template whose envelope does not fit inside its desk's business day (OVNT-05) | ✓ VERIFIED | Unchanged. `ShiftLibraryValidationService`/`ShiftTemplateService` untouched by `ad5b58a`. |
| 6 (amended) | The business-day-keyed schedule UI grid/roster render an overnight shift as one block carrying calendar-span disclosure; per-date slot surfaces render it as one continuous run of cells (OVNT-06) | ✓ VERIFIED | Unchanged. `ScheduleExportService`, `ScheduleResults.tsx`, `dayWindow.ts` all untouched by `ad5b58a`. |
| 7 | The shift is labelled with the calendar dates it spans wherever displayed (OVNT-07) | ✓ VERIFIED | Unchanged. `ad5b58a` reuses the existing shared `timeslotLabel()` helper rather than modifying it; `ScheduleExportService.shiftCode`/`crossingAwareCode` and `ScheduleResults.tsx`'s `sectionHeading` are untouched. |

**Score:** 7/7 truths verified (0 present, behavior-unverified) — unchanged from the fifth pass.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/service/ScheduleOutputService.java` | Structured violation fields, shared `timeslotLabel` helper | ✓ VERIFIED | **Changed this pass's covered window** (by `ad5b58a`): gained a `Timeslot`-extraction branch, read-side only, guarded by `timeslotId == null` so `AgentAssignment` precedence is preserved. Confirmed by direct read and by the new no-regression test. |
| All other artifacts in the fifth-pass table | — | ✓ VERIFIED | Unchanged; not re-listed here for brevity — see fifth-pass table, none of these files appear in `ad5b58a`'s diff. |

### Key Link Verification

Unchanged. The fix does not add, remove, or redirect any component/service/constraint wiring — it
adds a new `else if` branch inside an existing loop that reads an already-indicted object
(`Timeslot`) the solver was never withholding. All links remain ✓ WIRED.

### Behavioral Spot-Checks / Probe Execution

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full suite actually re-executes (not cached) | `./gradlew --stop && ./gradlew test --rerun` | `BUILD SUCCESSFUL in 10m 40s`, `1 executed, 4 up-to-date` | ✓ PASS |
| Suite-wide pass count matches commit claim | JUnit XML aggregated by filename across `build/test-results/test/*.xml` | `files: 202, tests: 1305, failures: 0, errors: 0, skipped: 4` | ✓ PASS |
| New RED→GREEN live-path test passes | `<testcase name="buildConstraintViolations_unassignedAssignment_violationNowCarriesTimeslotAttribution()">` present, no failure/error child | testsuite header `tests="29" failures="0" errors="0"` | ✓ PASS |
| New no-regression test passes | `<testcase name="buildConstraintViolations_liveAgentAssignmentIndictedConstraint_attributionUnaffectedByNewTimeslotBranch()">` present, no failure/error child | same testsuite, 0 failures/errors | ✓ PASS |

### Requirements Coverage

Unchanged. All seven OVNT-01..07 requirements remain ✓ SATISFIED, independent of this fix (which
does not touch any of their supporting code paths other than reusing, unmodified, the shared
`timeslotLabel()` helper already credited to OVNT-07).

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `frontend/src/pages/ScheduleResults.tsx:695-698` | `isEnvelopeReached` | Unguarded `RangeError`; no `ErrorBoundary` anywhere | ⚠️ Warning (pre-recorded, WR-03, disposition: open) | Unaffected |
| `src/main/java/com/wfm/service/ShiftTemplateService.java:412-424` | `isAligned` | END-position reinterpretation edge case (WR-01, disposition: open) | ⚠️ Warning (pre-recorded) | Unaffected |
| `src/main/java/com/wfm/service/DeskService.java:298-321` | `setDayStart`'s stranding check | Does not exclude retired template eras (WR-02, disposition: open) | ⚠️ Warning (pre-recorded) | Unaffected |
| `frontend/src/pages/DeskManagement.tsx:109,172-175` | Cancel button | Stays enabled during in-flight save (IN-01, disposition: open) | ℹ️ Info (pre-recorded) | Unaffected |
| `src/main/java/com/wfm/service/ScheduleOutputService.java:683-740` | justification loop | Previously: only `instanceof AgentAssignment` populated structured fields, so "Unassigned assignment" violations carried null attribution | ✅ **RESOLVED this pass's covered commit** (WINDOWS.md #14, status now `resolved`) — a `Timeslot`-extraction branch now populates `timeslotId`/`businessDate`/`calendarDate`/`startTime`/`endTime`/`timeslotLabel` for this constraint; proven RED-then-GREEN. No longer an open finding. | Previously affected only the unfilled-seat marker for this one constraint; now fixed |

No new anti-patterns found in the three changed files (`WINDOWS.md`, `ScheduleOutputService.java`,
the test file) — no `TODO`/`FIXME`/`XXX`/`HACK`/`PLACEHOLDER` markers, no empty-return stubs, no
hardcoded-empty data flowing to output.

**Previously recorded (second pass), independently confirmed pre-existing, unaffected:**

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `frontend/src/pages/ScheduleResults.tsx` (Agent column `th`/`td`, multiple sites) | `position: 'sticky', left: 0` | Sticky column never actually pins | ℹ️ Info (WINDOWS.md #15, disposition: open) | Pre-existing; not introduced by Phase 21 or this commit |

### Code Review Finding CR-01 (Blocker, disposition: fixed)

Unchanged — unaffected by this pass's commit. Disposition unchanged: `open: 4 / total: 5` (WR-01,
WR-02, WR-03, IN-01 remain open by operator decision; CR-01 fixed).

### WINDOWS.md Ledger — Carried-Forward Defect List, Updated This Pass

| # | Item | Prior status | Status as of this pass | Evidence |
|---|------|--------------|-------------------------|----------|
| 11-13 | Backstop geometry checks | resolved | resolved (unchanged) | No change |
| **14** | **"Unassigned assignment" violations carried null businessDate/startTime attribution, blocking the unfilled-seat marker from ever rendering** | open | **resolved** | `ad5b58a` added the `Timeslot`-extraction branch; proven RED-then-GREEN by a new live-path test; no-regression test confirms `AgentAssignment`-indicted constraints are unaffected. Confirmed directly in `.planning/WINDOWS.md` row 14's current text and in the resolved-status JSON block. **Phase 21 no longer ships with this defect open.** |
| 15 | Sticky Agent column never pins (pre-existing, not Phase 21's doing) | open | open (unchanged) | Separately filed, unaffected by this commit |
| 16 | Excel Roster legend collision | waived | waived (unchanged) | Operator decision, unaffected |
| 17 | Shift-mode branch six sub-checks | partial (5/6) | **partial (5/6), updated note** | #14's fix makes the 6th sub-check (unfilled-seat marker rendering) *reachable* — the prior structural block is gone — but it remains **not yet observed**: no live solve + DOM read of the marker has been performed in any session. WINDOWS.md's own row 17 text is explicit on this point ("Do not read this update as the marker having been observed rendering — it has not been"). This pass does not upgrade it further; it is not a phase-blocking item (none of the seven OVNT truths depend on it, as established across the fourth and fifth passes), and per the operator's explicit instruction for this pass the human-verification list stays empty — this is not promoted to a human-verification item. |

### Human Verification Required

None. Unchanged from the fifth pass — the human-verification list was empty then and this change
(additive, read-side, test-covered) adds nothing to it. The #17 sixth sub-check remains an
explicitly-tracked, non-blocking ledger item (reachable-but-not-observed), not a human-verification
item, per the operator's instruction for this pass.

### Stale Verification Digest

**This phase's own digest:** refreshed this pass using `gsd_run query verification.fingerprint`
over the current contents of the same 54 `covered_files` the fifth pass used (no membership
change — confirmed that including vs. excluding the three root-level `.planning/*.md` files
produces an identical digest, i.e. they are inert to the hash per #4623, so there was no reason to
alter the list). Old digest: `v2:sha256:66ec6e9d07263a997692cc0efca0ad55d36dab5f59df97602ee682e890569ec3`.
**New digest: `v2:sha256:c96d7be97eeb342c3a3f22c117856e01b7a1a3500c18af3d3a8f3555e16f0442`.** This
re-covers `.planning/WINDOWS.md` (entry #14 flipped to `resolved`, entry #17's note updated) and
`src/main/java/com/wfm/service/ScheduleOutputService.java` (the `Timeslot`-extraction branch), both
changed by `ad5b58a` after the fifth pass's digest was computed.

`gsd_run query verification.status` confirmed `stale` before this refresh and should now report
`verified` against the digest above.

**Still not this phase's to fix, carried forward as before:** the Phase 18/19/20 stale-digest item
(plan 21-11 touched files that appear in those phases' own `covered_files`) remains a milestone-
level bookkeeping item, unaffected by this pass's refresh of Phase 21's own digest.

### Gaps Summary

No must-have truth failed, no required artifact is missing or a stub, no key link is unwired, and
all seven OVNT requirements remain independently confirmed true in the current codebase. The one
commit landed since the fifth pass (`ad5b58a`) is additive and read-side, confirmed not to touch
`ScheduleConstraintProvider.java` or any of the seven truths' supporting artifacts, and closes the
phase's one previously-open defect finding (WINDOWS.md #14) with test-proven evidence (RED-then-
GREEN plus a genuine no-regression assertion), independently re-confirmed by this pass's own test
run (1305 tests, 0 failures, 0 errors — matching the commit's claim) rather than taken on the
commit message's word.

Phase 21 now ships with ONE item open by explicit, documented, pre-existing decision (down from
two as of the fifth pass):

1. **WINDOWS.md #15** (pre-existing, not Phase 21's doing, git-verified) — the Allocation grid's
   Agent column declares `position: sticky` but never actually pins. Unaffected by this pass.

WINDOWS.md #14 is now **resolved** and no longer carried forward as an open defect.

One item remains waived by explicit operator decision: the Excel Roster-sheet legend-collision
backstop (WINDOWS.md #16) — unaffected by this pass.

One item remains a tracked, non-blocking, reachable-but-not-observed ledger note: WINDOWS.md #17's
sixth sub-check (unfilled-seat marker rendering) — reachable as of #14's fix, not yet live-observed,
not required by any of the seven OVNT truths, not promoted to human verification per this pass's
explicit instruction.

Four further findings remain open by prior explicit operator decision and are unaffected by this
pass: WR-01, WR-02, WR-03, IN-01 (code review, disposition `open: 4 / total: 5`).

**Recommendation:** Phase 21 remains complete against its own stated goal and all seven OVNT
requirements, now with its one prior open defect (WINDOWS.md #14) resolved and test-proven, and its
verification digest refreshed so `gsd_run query verification.status` clears `stale` and
`phase.complete` can proceed.

---

_Verified: 2026-10-03T16:27:17Z_
_Verifier: Claude (gsd-verifier)_
