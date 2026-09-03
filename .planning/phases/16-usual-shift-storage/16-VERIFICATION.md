---
phase: 16-usual-shift-storage
verified: 2026-09-03T18:24:20Z
status: human_needed
score: 5/5 ROADMAP success criteria verified in code; 3 outstanding human-verification items (all pre-declared "backstop" truths, none newly discovered)
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Excel open-and-inspect of a generated per-desk template (16-03 backstop, RESEARCH.md Pitfall 5)"
    expected: "Downloading a per-desk template for a desk with a live shift library opens in real Excel (not LibreOffice alone) with no repair prompt; clicking a Usual Shift cell shows a working dropdown of that desk's live template names."
    why_human: "A POI round-trip test re-reads the file with the same library that wrote it and structurally cannot detect Excel-side corruption from the 255-character explicit-list data-validation limit. No human with Excel access performed this check during execution (16-03-SUMMARY.md records it as the one unresolved D7 item)."
  - test: "Roster tile three-state visual QA (16-05 backstop, five-point manual QA)"
    expected: "On a desk with a live shift library: set one weekday's usual shift → tile turns accent-blue bold; leave another unset → light-gray en dash; retire the template behind a third → italic muted gray with 'retired'; set a fourth weekday's hours to MANDATORY → italic muted 'not worked'; hover a clipped long name → tooltip shows the full value."
    why_human: "This repository has no frontend test framework (Phase 13 P-11, reconfirmed against frontend/package.json). The execution environment had no browser/screenshot tooling (no chromium/Playwright). 16-05-SUMMARY.md explicitly records all five checks as 'not observed' — proven only by source assertion (correct color tokens, correct branch structure), not by visual rendering."
  - test: "XCUT-01 roster-vs-export end-to-end trace (16-05 backstop human-check)"
    expected: "Set a usual shift inline in the roster, then export the desk to Excel, and confirm the same template name appears in that weekday's Usual Shift export column."
    why_human: "This specific roster-inline-write → export round trip was never exercised in a live browser session; the backend tracer test (UsualShiftTracerTest) proves store → roster-read → export for the choke-point write path, but does not exercise the frontend <select> commit. 16-05-SUMMARY.md records this human-check as unresolved."
---

# Phase 16: Usual Shift Storage Verification Report

**Phase Goal:** Each agent's usual shift per weekday is stored, settable via the per-desk upload
template or inline roster editing, and visible everywhere agent scheduling data is displayed.

**Verified:** 2026-09-03T18:24:20Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Summary

All five ROADMAP.md success criteria are **verified against the running codebase**, not merely
claimed by the five plan SUMMARYs. I re-ran the load-bearing test classes myself (not trusting the
SUMMARY.md-reported counts) and every one passed with the exact test counts the SUMMARYs claim:
`UsualShiftTracerTest` (10/10), `DeskAgentServiceUsualShiftTest` (20/20), `UsualShiftResolutionServiceTest`
(6/6), `DeskAssignmentUploadUsualShiftTest` (11/11), `DeskAssignmentTemplateServiceUsualShiftTest`
(10/10), `ShiftTemplateServiceTest` (37/37), `UsualShiftWritePathGuardTest` (6/6),
`UsualShiftWritePathTest` (5/5), `AgentUsualShiftPostgresTest` (5/5, real Testcontainers Postgres).
Frontend `npm run build` (tsc -b && vite build) is clean.

The phase is **not** blocked by any FAILED truth or missing artifact. It is `human_needed` solely
because three visual/manual claims — explicitly pre-declared `verification: backstop` by the plans
themselves, not newly discovered gaps — were never exercised in a live browser or Excel session
during execution (no browser/Excel tooling was available). This matches what both 16-03-SUMMARY.md
and 16-05-SUMMARY.md already honestly record; nothing here contradicts the plans' own accounting.

## Goal Achievement

### ROADMAP Success Criteria

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | Stored usual shift per weekday, referencing a valid active template; no-stored-shift is representable without penalty-inducing substitution | ✓ VERIFIED | `AgentUsualShift` entity + `V47` migration (real FK, `UNIQUE(agent_id, day_of_week)`); `UsualShiftService.setUsualShift` rejects an ineffective template (P-03) and a weekday-mask violation (D-03); `UsualShiftResolutionService.resolve(null, date)` and an absent row both yield `Optional.empty()` → `NOT_SET`/null name, never a substitute. Proven by `UsualShiftTracerTest` methods 2, 4–8 and `UsualShiftResolutionServiceTest` case 3 (re-run, 6/6 green). |
| 2 | Bulk set via a column in the per-desk upload template, resolved through the shared `EnrichedColumnLayout` | ✓ VERIFIED | Seven `Usual Shift {Day}` columns added to template, parser and export, all via `EnrichedColumnLayout.usualShiftHeader(DayOfWeek)` — confirmed by direct grep: exactly 4 main-source files reference it (`EnrichedColumnLayout`, `DeskAgentExportService`, `DeskAssignmentTemplateService`, `DeskAssignmentUploadService`), no fifth. `DeskAssignmentUploadUsualShiftTest` (11/11, re-run green) proves D-06/D-07/D-08/D-03/P-12 cell semantics and the D-09/D-11 round-trip no-op. |
| 3 | Inline set/correct through a single choke-point write, mirroring `setDayHours`'s shape | ✓ VERIFIED | `UsualShiftService.setUsualShift` (read directly: T-13-05 agent resolution before any repository call, cross-desk template guard, reject-not-clamp, upsert-or-create, explicit `flush()`) is the ONLY production writer reached by `DeskAgentController`'s `PUT .../usual-shift/{day}` endpoint. The upload path (16-03) deliberately writes `AgentUsualShiftRepository` directly (P-13, by design, not a second choke point for USHF-03's inline case) — this is explicitly scoped and recorded as a second table row in the D-14 guard, not hidden. |
| 4 | Every write path enumerated in a table, verified against real code, not assumed | ✓ VERIFIED | `src/test/resources/ushf-05-write-paths.md` has exactly 9 rows (confirmed by direct file read). `UsualShiftWritePathGuardTest` (read in full; re-run 6/6 green) performs a dual independent source-scan derivation (repository-type, entity-type with word-boundary exclusion) asserted with `containsExactlyInAnyOrderElementsOf` — **confirmed by direct code inspection: no `isSubsetOf`/`contains(` shortcut exists anywhere in the assertions.** The "proven red twice" claim is substantiated, not merely asserted: (a) a code-level test-of-the-test (`deliberatelyBrokenAllowlist_isDetectedAsAMismatch`) exists and passes; (b) the SUMMARY captures a **verbatim** real deliberate-break failure message naming the exact injected class (`UsualShiftResolutionService`) and directing the reader to add a table row — this is concrete, checkable evidence, not a bare claim. |
| 5 | Visible everywhere, end-to-end traced store → roster → export, not "the model is done" | ✓ VERIFIED | Read `UsualShiftTracerTest#happyPath_storeRosterExport_endToEnd` in full: **one test method** calls `setUsualShift` → reads `getDeskAgentResponse` → reads `listDeskAgentResponses` → calls `exportDeskAgentsToExcel` → re-opens the byte array with `XSSFWorkbook` and asserts header/cell positions — genuinely one continuous trace across all three layers, not three separate per-layer tests. Re-run: 10/10 green including this method. |

### Deferred / Not-Applicable

None. No gap identified in this phase matches a later-phase success criterion — the roadmap's own
Phase 16 boundary explicitly excludes the solver reading this table (Phase 17's job), and that
exclusion is correctly honored (see Cross-Cutting Discipline below), not a gap.

## Cross-Cutting Discipline (verification_context items a–f)

### (a) XCUT-02 / D-14 structural guard — verified, not assumed

Directly read `src/test/java/com/wfm/service/UsualShiftWritePathGuardTest.java` in full (787 lines
including javadoc). Confirmed:
- Two independent derivations over a live `Files.walk(src/main/java)`: a literal scan for
  `AgentUsualShiftRepository` (Set A) and a word-boundary regex `AgentUsualShift(?!Repository)`
  (Set B) — genuinely independent, not one a strict subset check of the other.
- Both assertions use `containsExactlyInAnyOrderElementsOf` exclusively — grepped the file myself
  for `isSubsetOf|containsAnyOf|\.contains\(` and found zero hits in assertion code.
- `theTableHasExactlyNineDataRows_withNoBlankRequiredCells` and
  `everyProvingTestNamedInTheTable_resolvesToAnExistingClass` both exist and pass.
- `deliberatelyBrokenAllowlist_isDetectedAsAMismatch` (the test-of-the-test) exists, removes one
  entry from a live-derived allowlist copy, and asserts the equality check throws — passed on
  re-run.
- The SUMMARY's captured real deliberate-break output (a field temporarily added to
  `UsualShiftResolutionService.java`, a class that legitimately holds an entity reference but not a
  repository reference) is internally consistent with the actual Set A/Set B allowlists I read in
  `ushf-05-write-paths.md` — the class named in the failure message is exactly the class absent
  from Set A but present in Set B, which is the correct failure shape for that specific injection.

**Verdict: the "proven red twice" claim holds.** This is the strongest single deliverable in the
phase and it is honestly delivered.

### (b) D-05 advisory-only — both halves confirmed in code

- **Write side never gated:** read `UsualShiftService.setUsualShift` in full — it contains no
  reference to `getNetHours`, `hoursAdvisory`, or any hours comparison. `grep -c
  'getNetHours\|hoursAdvisory' src/main/java/com/wfm/service/UsualShiftService.java` → 0.
- **Read side computes and surfaces the advisory:** `DeskAgentService.toResponse` →
  `usualShiftEntry` → `hoursAdvisory` helper reuses `ShiftTemplate.getNetHours(int)` (never
  `Duration.between`), exact-equality via `BigDecimals.normalize(...).compareTo(...)`, any-band
  quantifier matching `ShiftLibraryValidationService`'s own rule.
- **A later-introduced mismatch surfaces:** `DeskAgentServiceUsualShiftTest#d05_surfacesAMismatchIntroducedAfterTheWrite_byALaterContractedHoursEdit`
  exists (confirmed present by grep) and is part of the 20/20 passing re-run.
- **Frontend renders it:** `frontend/src/pages/DeskAgents.tsx` `UsualShiftLine` — confirmed by
  direct read — branches on `hasAdvisory` (derived from `entry.hoursAdvisory`) and renders an amber
  `#92400e` marker whose `title` becomes the advisory sentence, additive to (never replacing) the
  state's own text/color.

**Verdict: both halves of D-05 genuinely hold.**

### (c) XCUT-01 / success criterion 5 — the tracer genuinely crosses three layers in one test

Covered above in the Success Criteria table. Directly confirmed by reading the test source, not
trusting the SUMMARY's description of it.

**Residual gap (not a code gap):** the *frontend* half of the roster→export trace (an inline
`<select>` commit reflected in a subsequently-downloaded export) was never exercised end-to-end in
a live session — this is exactly the third human-verification item listed above, already flagged
by the plan itself.

### (d) D-09/D-11 co-dependency — both shipped, round-trip genuinely proven

- `DeskAssignmentTemplateService` pre-fill (D-09) confirmed present (task 1, plan 16-03, verified
  by 10 passing `DeskAssignmentTemplateServiceUsualShiftTest` methods, re-run green).
- `DeskAssignmentUploadService.clearDesk` calls `usualShiftService.clearUsualShifts(agent.getId())`
  — read directly, confirmed present, alongside the pre-existing `agentDayHoursRepository.deleteByAgent_Id`
  call.
- `DeskAssignmentUploadUsualShiftTest#downloadThenReupload_isANoOp_forStoredUsualShifts` exists and
  is part of the 11/11 passing re-run — genuinely generates a template via
  `DeskAssignmentTemplateService`, feeds the bytes back into the upload, and asserts zero row-count
  change. This is real round-trip evidence, not a design-intent assertion.
- Blank-cell-means-clear (D-07) is separately proven by
  `DeskAssignmentUploadUsualShiftTest#blankCell_writesNoRow_noWarning_restOfRowImports`.

**Verdict: the co-dependency is genuinely discharged together, as CONTEXT.md required.**

### (e) D-16 three states / D-01/D-02 era semantics — confirmed by direct code read

Read `UsualShiftResolutionService.resolve` in full: resolves by template NAME
(`stored.getShiftTemplate().getName()`), filters live eras on `isEffectiveOn(date)`, sorts by
`effectiveFrom` descending then `id` ascending, `findFirst()`. An unmatched name (retired outright)
returns `Optional.empty()` — identical to unset at that layer; the stored row is never mutated by
the resolver. `DeskAgentService.toResponse`'s `usualShiftEntry` helper (read directly) evaluates
RETIRED before NOT_WORKED (P-07), confirmed by the passing
`DeskAgentServiceUsualShiftTest#precedence_retiredAndPto_reportsRetiredNotNotWorked`. All four
reachable combinations (`NOT_SET`, `LIVE`, `STORED_INACTIVE`/`RETIRED`,
`STORED_INACTIVE`/`NOT_WORKED`) proven reachable in one agent/one response by
`allFourStates_reachableOnOneAgentInOneResponse` — re-confirmed passing.

### (f) No Phase 17 scope leakage; `AgentShiftAssignment` untouched

`grep -rIn "ConsistencyConstraint\|toleranceBand\|DriftReport\|CONS-0\|DRFT-0" src/main/java/`
returns exactly one hit — a **javadoc comment** in `ScheduleConstraintProvider.java` noting that
Phase 17's CONS-05 will later use `preferredStartTime`, not any actual Phase-17 implementation.
`git log` on `src/main/java/com/wfm/model/AgentShiftAssignment.java` shows its last modifying
commits are all Phase 15 (`81117e3`, `b2dd702`, `ba0c3f0`, `16440e2`) — **zero commits from Phase
16's date range touch this file.** `solverConfig.xml` contains no "consistency" reference.

**Verdict: no scope leak. Target (`agent_usual_shift`) and result (`agent_shift_assignment`) stay
distinct, exactly as CONTEXT.md required, and this is proven — not merely asserted — by
`UsualShiftWritePathTest`'s solver row (a real bounded solve leaves stored rows field-identical) and
the guard's structural proof that no solver-package class references either type.**

## Requirements Coverage

| Requirement | Status | Evidence |
|---|---|---|
| USHF-01 (stored per weekday, valid active template) | ✓ SATISFIED | `AgentUsualShift`/V47/`UsualShiftService` + `UsualShiftResolutionServiceTest` (6/6) |
| USHF-02 (bulk set via upload template column) | ✓ SATISFIED | `DeskAssignmentTemplateServiceUsualShiftTest` (10/10), `DeskAssignmentUploadUsualShiftTest` (11/11) |
| USHF-03 (inline set/correct in roster) | ✓ SATISFIED (backend) / mostly ✓ (frontend, pending manual QA) | `UsualShiftService.setUsualShift` choke point (backend proven); frontend `<select>` wired and source-verified but not click-tested live (see human-check #2/#3) |
| USHF-04 (no stored shift ⇒ no penalty) | ✓ SATISFIED for this phase's scope | `resolve` returns `Optional.empty()`, never a substitute; the *penalty* half is explicitly and correctly Phase 17's scope (solver does not read this table yet, proven by `UsualShiftWritePathTest`'s solver row) — not a Phase 16 gap, consistent with 16-02's own flagged-assumption reasoning, which this verification endorses as correctly scoped |
| USHF-05 (every write path documented+verified) | ✓ SATISFIED | 9-row table + `UsualShiftWritePathGuardTest` (6/6) + `UsualShiftWritePathTest` (5/5), all re-run green |
| USHF-06 (visible in roster + export) | ✓ SATISFIED (backend+export proven; roster visual rendering pending human QA) | `DeskAgentExportServiceTest`, `UsualShiftTracerTest` header/cell assertions; frontend `UsualShiftLine` source-verified, not visually confirmed live |
| XCUT-01 (display verified in every surface) | ✓ SATISFIED for backend/export; ⚠ pending human confirmation for the live roster-render and roster→export browser round trip | See human-check items #2, #3 |
| XCUT-02 (every reachable write path verified) | ✓ SATISFIED | See (a) above |

No orphaned requirements found in `.planning/REQUIREMENTS.md`'s Phase 16 section beyond the ones
declared across the five plans' `requirements:` frontmatter fields.

**Documentation staleness note (not a code gap):** `.planning/REQUIREMENTS.md`'s Traceability table
(near the bottom of the file) still reads "USHF-01…06 | Phase 16 | **Pending**" and "XCUT-01/02 |
Phases 16, 17 | **Pending**", even though the checkbox list at the top of the same file already
marks USHF-01…06, XCUT-01 and XCUT-02 `[x]`. This is a stale second copy inside REQUIREMENTS.md
itself — worth a follow-up edit before Phase 17 planning reads that table, but it is a documentation
artifact, not a code or test gap, and does not affect this verification's verdict.

## Anti-Pattern Scan

Scanned all 16 production files this phase created or modified (backend + frontend) for
`TBD|FIXME|XXX|HACK|PLACEHOLDER|not yet implemented|coming soon`. **Zero hits.** No debt markers,
no stub returns, no hardcoded-empty-data patterns found in any phase-touched production file.

## Behavioral Re-Verification (executed by this verifier, not merely read from SUMMARYs)

| Test class | Claimed count | Re-run result |
|---|---|---|
| `UsualShiftWritePathGuardTest` | 6 | ✓ 6/6, 0 failures |
| `UsualShiftTracerTest` | 10 | ✓ 10/10, 0 failures |
| `DeskAgentServiceUsualShiftTest` | 20 | ✓ 20/20, 0 failures |
| `DeskAssignmentUploadUsualShiftTest` | 11 | ✓ 11/11, 0 failures |
| `DeskAssignmentTemplateServiceUsualShiftTest` | 9+ | ✓ 10/10, 0 failures |
| `UsualShiftResolutionServiceTest` | 6+ | ✓ 6/6, 0 failures |
| `ShiftTemplateServiceTest` | — | ✓ 37/37, 0 failures |
| `UsualShiftWritePathTest` | 4+ | ✓ 5/5, 0 failures |
| `AgentUsualShiftPostgresTest` | 5 | ✓ 5/5, 0 failures (real Testcontainers Postgres) |
| `cd frontend && npm run build` | clean | ✓ clean, 925ms, zero TS errors |

## Human Verification Required

Three items, all pre-declared `verification: backstop` by the executing plans themselves (16-03,
16-05) — not new findings from this verification, but confirmed still outstanding:

### 1. Excel open-and-inspect of the generated per-desk template

**Test:** Download a per-desk template for a desk with a live shift library, open it in real Excel
(not LibreOffice alone), confirm no repair prompt, click a Usual Shift cell, confirm the dropdown
lists that desk's live template names.
**Expected:** Clean open, working dropdown, correct names, no other desk's names present.
**Why human:** A POI round-trip test re-reads the file with the library that wrote it and
structurally cannot detect Excel-side corruption from the 255-character explicit-list limit.

### 2. Roster tile three-state visual QA

**Test:** On a desk with a live shift library: set one weekday's usual shift, leave another unset,
retire the template behind a third, set a fourth weekday's hours to MANDATORY, expand the agent row,
hover a clipped long name.
**Expected:** Four tiles read differently (accent-blue bold / light-gray en dash / italic muted
"retired" / italic muted "not worked"); the tooltip shows the full un-truncated name on hover.
**Why human:** No frontend test framework exists in this repository (Phase 13 P-11); no
browser/screenshot tooling was available during execution. Source assertion confirms the correct
color tokens and branch logic exist, but none of it was visually rendered and observed.

### 3. XCUT-01 roster-vs-export end-to-end trace (frontend leg)

**Test:** Set a usual shift inline via the roster's `<select>`, then export the desk to Excel, and
confirm the same template name appears in that weekday's Usual Shift export column.
**Expected:** The two surfaces agree on the one written value.
**Why human:** The backend tracer test proves store → roster-read → export for the API-level choke
point, but the actual browser interaction (clicking the `<select>`, confirming the PUT round-trips
into the subsequently-downloaded export) was never exercised live.

## Gaps Summary

**No gaps found.** Every ROADMAP success criterion, every USHF/XCUT requirement, and every
verification-context scrutiny point (a–f) is backed by code I read directly and tests I re-ran
myself with matching pass counts. The only reason this phase is not `passed` is the three
pre-declared, honestly-recorded human-verification items above — none of which represents a
discovered defect, an unimplemented feature, or a discrepancy between what the SUMMARYs claimed and
what the codebase contains. This is a well-executed phase whose only debt is the manual-QA class of
check this project has repeatedly and correctly identified it cannot automate.

---

_Verified: 2026-09-03T18:24:20Z_
_Verifier: Claude (gsd-verifier)_
