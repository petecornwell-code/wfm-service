---
phase: 06-solver-quality-constraints
verified: 2026-07-29T00:00:00Z
status: gaps_found
score: 6/7 must-haves verified
overrides_applied: 0
gaps:
  - truth: "BambooHR API key exposed in chat (2026-06-02) is rotated and stored in a secret manager before any deploy"
    status: failed
    reason: >
      This is an explicit must_have truth in 06-01-PLAN.md frontmatter, guarded by a
      `checkpoint:human-verify gate="blocking-human"` Task 0. The SUMMARY records the task
      as "BYPASSED (operator directive)" — the old key (prefix ad2bb...2be) was never
      rotated, and no new key was placed in a secret manager. `git grep -i ad2bb` still
      returns matches in `.planning/codebase/CONCERNS.md`, `02-01-PLAN.md`, `02-RESEARCH.md`,
      and the 06-01 planning docs (confirmed by direct grep during this verification — not
      just cited from SUMMARY). Meanwhile Phase 6 code that touches the BambooHR
      integration (BambooEmployee, HttpBambooHRClient, BambooRefreshService) has already
      been deployed to the service's only live environment (deploy run 27364497136,
      commit f173c51 per 06-03-SUMMARY.md; confirmed live per project_aws_deployment
      memory — the "dev" environment at d2bbtcc80peap7.cloudfront.net is the sole running
      instance, not a separate throwaway sandbox). The must-have's own wording — "rotated
      ... before any deploy" — is therefore violated by the deploy that has already
      happened.
    artifacts:
      - path: ".planning/phases/06-solver-quality-constraints/06-01-SUMMARY.md"
        issue: "Records Task 0 bypass by operator directive; key rotation acceptance criteria explicitly NOT met by design"
      - path: ".planning/codebase/CONCERNS.md"
        issue: "Still contains the full exposed API key value in a tracked planning file"
    missing:
      - "Rotate the BambooHR API key for the helpware tenant (old prefix ad2bb...2be) in the BambooHR admin console"
      - "Store the new key only in AWS Secrets Manager / deploy env config, never in chat or committed files"
      - "Confirm `git grep -i ad2bb` is clean, or scrub the value from tracked planning docs if it cannot be fully removed from history"
deferred: []
human_verification:
  - test: "Desk-scale data-gap proportion for live scheduled desks (e.g. StubHub-GE, Vinted-UA)"
    expected: >
      Trigger a BambooHR refresh for each live scheduled desk and tail
      `/ecs/wfm-service-dev` (filter `customWorkingdays`) for the
      "{N} agent(s) on desk {deskId} have blank/Variable customWorkingdays..." log.warn
      line; compare N against the desk's total agent count. A high proportion means D-07
      is silently excluding a large fraction of the real agent pool from the solver.
    why_human: >
      Requires live BambooHR data and a live refresh trigger; cannot be measured from
      source code alone. This is 06-03 Task 3's acceptance criterion 3, which the operator
      approved the checkpoint without reporting (06-03-SUMMARY.md "Issues / Outstanding
      Verification Debt" item 1). Per orchestrator instruction this is a KNOWN, DISCLOSED
      item, not newly discovered — included here only so it is not lost from the
      human-verification queue, not as a new finding.
  - test: "Live visual confirmation of MANDATORY red cells in the ScheduleResults PTO tab after a real BambooHR refresh + solve"
    expected: "Recurring MANDATORY weekend cells render red within the schedule window, label/legend reads clearly as 'Mandatory'"
    why_human: >
      The executor did not independently drive the frontend; the operator's "Yes approved"
      response is the only live-session evidence. This verifier independently confirmed the
      rendering CODE exists and is correct (frontend/src/pages/ScheduleResults.tsx:815-816,
      851 — MANDATORY mapped to #fef2f2/#dc2626 with a "Mandatory" legend swatch), which is
      stronger evidence than trusting the SUMMARY claim alone, but a live pixel-level check
      was not re-run by this verifier.
---

# Phase 6: Solver Quality Constraints — PTO & Weekends (QUAL-01) Verification Report

**Phase Goal:** Every scheduled agent's BambooHR-sourced fixed weekly days off are imported as recurring MANDATORY blocks and honoured as hard constraints by the solver, with data-gap agents excluded and outliers surfaced.
**Re-scope note (from ROADMAP.md):** Phase 6 was re-scoped to QUAL-01 only. QUAL-02 (weekend-position fairness) and QUAL-03 (day-to-day hours consistency) were explicitly deferred to a follow-on phase per 06-CONTEXT.md — their absence here is NOT a gap in this phase.
**Verified:** 2026-07-29
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Each scheduled agent with a parseable field-4517 pattern receives recurring MANDATORY `AgentDayOff` rows for their off-days across the schedule horizon, honoured as hard solver blocks | VERIFIED | `BambooRefreshService.java:255-303` generates `DayOffType.MANDATORY`/`DayOffStatus.APPROVED` rows for every off-day date in `[from,to]`; `SolverService.java:953-961` (`buildAgentDaysOffMap`) treats `MANDATORY` as always-blocking, unchanged code (D-09) — confirmed by direct read, not just SUMMARY claim |
| 2 | The free-text parser handles every live format (ranges incl. week-wrap, "to" form, comma lists, trailing annotations, spelling variants) and never throws | VERIFIED | `WorkingDaysParser.java` — wrapped in try/catch returning `Optional.empty()`, guards null/blank/Variable first; `WorkingDaysParserTest` — 14/14 green, actual XML test report confirms `tests="14" skipped="0" failures="0" errors="0"` (re-ran during this verification, not just cited) |
| 3 | Agents with Variable/blank working days are treated as a data gap — excluded from scheduling and surfaced to the operator | VERIFIED (with caveat) | `BambooRefreshService.java:268-274` sets `workingDaysKnown=false` on data-gap; `SolverService.filterEligible` (`:975-982`) has exactly 4 filters, 4th is `Agent::isWorkingDaysKnown`, confirmed by direct read; `SolverServiceEligibilityFilterTest` 8/8 green (re-ran, XML confirms). **Caveat:** "surfaced to the operator" is a `log.warn` CloudWatch line only (`BambooRefreshService.java:338-342`) — no UI diagnostic exists yet. This was an explicit, pre-authorized scope decision in 06-03-PLAN.md's own `<interfaces>` section ("Claude's Discretion, D-05/D-07 ... Do NOT extend BambooSyncEvent this phase") formally deferring the UI surface to Phase 7 DIAG — not a hidden shortcut. |
| 4 | Outlier patterns (≠2 contiguous off-days, or 0 off-days) are flagged to the operator; MANDATORY weekends render in the ScheduleResults PTO tab within the schedule window | VERIFIED (code-level) | Outlier flag: `BambooRefreshService.java:282-286` `log.warn` (same log-only caveat as truth 3). PTO tab: `frontend/src/pages/ScheduleResults.tsx:815-816` maps `type === 'MANDATORY'` to red (`#fef2f2`/`#dc2626`), legend at `:848-852` reads "Mandatory" — confirmed by direct grep/read of the frontend file, independent of the operator's "Yes approved" checkpoint response. Live desk-scale visual confirmation still pending — see human_verification. |
| 5 | PTO behaviour is unchanged (APPROVED blocks, REQUESTED visible-only); the dead `"MANDATORY".equalsIgnoreCase(type)` match is removed | VERIFIED | `grep -n "MANDATORY.*equalsIgnoreCase" BambooRefreshService.java` → 0 matches (confirmed live, not cited); `SolverServicePtoFilterTest` 6/6 green (XML confirmed); PTO priority logic at `BambooRefreshService.java:309-335` unchanged apart from the removed dead branch |
| 6 | MANDATORY rows are idempotent across re-refreshes (delete-then-reinsert in the same window) | VERIFIED | `BambooRefreshService.java:250-253` — `deleteByAgent_IdAndDateBetween` + `flush()` runs before the new MANDATORY-generation block on every refresh, so regeneration cannot duplicate rows. Note: `BambooRefreshServiceTest`'s idempotency test (`mandatoryGeneration_isIdempotent_...`) exercises a **hand-copied replica** of the generation loop (`invokeMandatoryGeneration`), not the real `persistRefreshData` (private method, would need full Spring context/repo mocking per the test file's own header comment). This verifier independently read the real production method and confirms the replica matches it line-for-line in intent; flagging this as a test-realism gap, not a functional gap. |
| 7 | BambooHR API key exposed in chat (2026-06-02) is rotated and stored in a secret manager before any deploy | **FAILED** | Task 0 in 06-01-PLAN.md is a `checkpoint:human-verify gate="blocking-human"`. 06-01-SUMMARY.md records it "BYPASSED (operator directive)". `git grep -i ad2bb` (re-run during this verification) still returns the full key value in `.planning/codebase/CONCERNS.md` and multiple planning docs. Code touching this integration has already been deployed to the service's only live environment. See Gaps below. |

**Score:** 6/7 truths verified (1 FAILED)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/integration/WorkingDaysParser.java` | Tolerant parser, `parseWorkingDays`/`offDaysFrom`/`isStandardTwoContiguousDaysOff` | VERIFIED | Package-private final, private ctor, static-only; matches interface contract exactly |
| `src/test/java/com/wfm/integration/WorkingDaysParserTest.java` | Parameterized coverage of full live catalog | VERIFIED | 14 rows, all live formats + 3 data-gap cases + 1 garbage row; 14/14 pass |
| `src/main/java/com/wfm/integration/BambooEmployee.java` | `customWorkingdays` record component | VERIFIED | Inserted in correct position (between `employmentHistoryStatus` and `wfmTenantId`) |
| `src/main/java/com/wfm/integration/HttpBambooHRClient.java` | Field 4517 in bulk request; reads `customWorkingdays` | VERIFIED | `"4517"` present in fields array (line 136); `emp.path("customWorkingdays").asText(null)` at line 169; both `listEmployees` and `getEmployee` call-sites updated |
| `src/main/java/com/wfm/integration/MockBambooHRClient.java` | Varied `customWorkingdays` incl. data-gap + outlier | VERIFIED | `i % 5` switch across all 3 construction sites; includes `"Variable"` and `"Mon. to Thurs."` |
| `src/main/resources/db/migration/V28__add_agent_working_days_known.sql` | `working_days_known BOOLEAN NOT NULL DEFAULT TRUE`, default kept permanently | VERIFIED | Exact DDL matches; comment explains why default is not dropped (D-07) |
| `src/main/java/com/wfm/model/Agent.java` | `workingDaysKnown` field + accessors | VERIFIED | `@Column(name="working_days_known")`, `isWorkingDaysKnown()`/`setWorkingDaysKnown()` present |
| `src/main/java/com/wfm/integration/BambooRefreshService.java` | MANDATORY generation, dead-match removal, data-gap flagging | VERIFIED | Full logic read and traced end-to-end (see Truths 1, 3, 5, 6) |
| `src/main/java/com/wfm/service/SolverService.java` | 4th `filterEligible` criterion | VERIFIED | Exactly 4 `.filter(...)` calls, 4th is `Agent::isWorkingDaysKnown`, signature unchanged |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `WorkingDaysParserTest` | `WorkingDaysParser.parseWorkingDays` | same-package static call | WIRED | No reflection; direct static call confirmed in test source |
| `HttpBambooHRClient.listEmployees` | `BambooEmployee.customWorkingdays` | `emp.path("customWorkingdays").asText(null)` -> positional ctor arg | WIRED | Confirmed at HttpBambooHRClient.java:169-176 |
| `BambooRefreshService.persistRefreshData` | `WorkingDaysParser.parseWorkingDays` | per-agent parse of `emp.customWorkingdays()` | WIRED | Confirmed at BambooRefreshService.java:265-266 |
| `BambooRefreshService` MANDATORY loop | `dedupedDaysOff` map | `agentId + "|" + date` key, MANDATORY before PTO loop, `putIfAbsent` | WIRED | Confirmed at BambooRefreshService.java:298-299, 309-335 (PTO loop honours the same priority via explicit type check) |
| `SolverService.filterEligible` | `Agent.isWorkingDaysKnown` | `.filter(Agent::isWorkingDaysKnown)` | WIRED | Confirmed at SolverService.java:981 |

### Data-Flow Trace (Level 4)

Not applicable in the React-dashboard sense — this phase is a backend data pipeline (BambooHR → parser → persisted `AgentDayOff` rows → solver constraint map → existing, unchanged frontend PTO tab). The chain was traced end-to-end at the source level above (Truths 1–6) rather than via a rendered-component trace, since there is no new frontend component in this phase (D-10 is verify-not-build against pre-existing UI).

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| WorkingDaysParser full catalog | `./gradlew test --tests "com.wfm.integration.WorkingDaysParserTest"` | 14/14 pass (XML: `tests="14" skipped="0" failures="0" errors="0"`) | PASS |
| MANDATORY generation + data-gap + idempotency | `./gradlew test --tests "com.wfm.integration.BambooRefreshServiceTest"` | 12/12 pass | PASS |
| Solver eligibility exclusion (D-07) | `./gradlew test --tests "com.wfm.service.SolverServiceEligibilityFilterTest"` | 8/8 pass | PASS |
| MANDATORY always blocks (solver consumer unchanged) | `./gradlew test --tests "com.wfm.service.SolverServicePtoFilterTest"` | 6/6 pass | PASS |
| Dead code removal | `grep -n "MANDATORY.*equalsIgnoreCase" BambooRefreshService.java` | 0 matches | PASS |

All four targeted suites were re-run live during this verification (not merely cited from SUMMARY/orchestrator context) and their JUnit XML result files were inspected directly to confirm actual pass counts rather than trusting console tail output alone.

### Probe Execution

Step 7c: SKIPPED (no `scripts/*/tests/probe-*.sh` conventional probes exist in this repository, and neither PLAN nor SUMMARY for this phase declares any probe path).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|--------------|-------------|--------------|--------|----------|
| QUAL-01 | 06-01, 06-02, 06-03 | Solver ensures every agent receives exactly 2 contiguous days off per week ("weekend") via BambooHR-sourced MANDATORY data | SATISFIED (implementation) / gap on deploy-gate | Truths 1–6 verified; REQUIREMENTS.md already marks QUAL-01 "Complete" — consistent with code evidence, but the phase's own Task-0 security gate (truth 7) is unmet, so "Complete" should be read as "functionally complete, pending the key-rotation gate" |

**Orphaned requirements check:** REQUIREMENTS.md's traceability table lists QUAL-02 and QUAL-03 as `Phase 6 | Pending` (lines 68-69), but no Phase 6 plan (`06-01`, `06-02`, `06-03`) declares `QUAL-02` or `QUAL-03` in its `requirements:` frontmatter — all three declare only `[QUAL-01]`. This matches ROADMAP.md's explicit re-scope note ("Re-scoped to QUAL-01 only... QUAL-02/QUAL-03 DEFERRED to a follow-on phase (6b/7)"), so this is a stale row in REQUIREMENTS.md's phase-mapping column, not an unimplemented requirement silently dropped by this phase. Recommend updating REQUIREMENTS.md to point QUAL-02/QUAL-03 at whichever phase number ends up carrying them, but this is a documentation nit, not a BLOCKER.

### Anti-Patterns Found

None. Scanned all 11 files modified across the three plans (`WorkingDaysParser.java`, `WorkingDaysParserTest.java`, `BambooEmployee.java`, `HttpBambooHRClient.java`, `MockBambooHRClient.java`, `V28__add_agent_working_days_known.sql`, `Agent.java`, `BambooRefreshService.java`, `SolverService.java`, `BambooRefreshServiceTest.java`, `SolverServiceEligibilityFilterTest.java`) for `TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER|placeholder|coming soon|not yet implemented|not available` — zero matches.

One quality note (not a blocker): `BambooRefreshServiceTest`'s MANDATORY-generation tests exercise a hand-copied replica of the real generation loop (`invokeMandatoryGeneration`) rather than the actual private `persistRefreshData` method, because the latter needs a full Spring context and repository mocking. This verifier independently read the real method and confirmed the replica's logic matches, but this is a maintainability risk: future edits to `persistRefreshData` could silently diverge from the test's copy without the test catching it.

### Human Verification Required

See `human_verification` in the frontmatter above. Both items are already known/disclosed per 06-03-SUMMARY.md's "Issues / Outstanding Verification Debt" section and are carried forward here only so they remain in the verification queue, not because they were newly discovered by this verifier.

### Gaps Summary

**One BLOCKER (newly surfaced by this verification, not previously listed in a VERIFICATION.md since none existed):** the BambooHR API key rotation gate (Task 0 of 06-01-PLAN.md) was explicitly declared a blocking pre-deploy security requirement in the plan's own frontmatter (`must_haves.truths`) and threat model (T-6-SC / Elevation of Privilege), was bypassed rather than completed, and code that depends on the affected integration has since been deployed to the service's only live environment. This is disclosed candidly in 06-01-SUMMARY.md and 06-03-SUMMARY.md — the disclosure is good practice, but disclosure in a SUMMARY does not satisfy a must-have truth or substitute for a recorded override. If the actual human operator has knowingly and deliberately accepted this risk (the SUMMARY's "operator directive" language suggests they may have), the correct path is to add an explicit `overrides:` entry to this VERIFICATION.md's frontmatter recording that decision — see the suggested block below — rather than to leave it silently unresolved.

**This looks intentional (operator directive).** To accept this deviation, add to VERIFICATION.md frontmatter:

```yaml
overrides:
  - must_have: "BambooHR API key exposed in chat (2026-06-02) is rotated and stored in a secret manager before any deploy"
    reason: "Operator explicitly directed the key rotation task to be bypassed for Phase 6; accepted risk pending future rotation"
    accepted_by: "<name>"
    accepted_at: "<ISO timestamp>"
```

**Judgment call on the D-07 data-gap proportion (explicitly requested by the orchestrator):** The Phase 6 *implementation* is complete and correct — the parser, MANDATORY generation, idempotency, solver exclusion, outlier/data-gap logging, and dead-code removal all work exactly as the roadmap's success criteria describe, verified directly against source and passing tests, not merely asserted by SUMMARY.md. The unmeasured (and now known-nonzero, per the operator's post-approval finding that field 4517 is incomplete) D-07 exclusion proportion is **not** a phase-goal implementation gap — it is a production-readiness / source-data-quality concern that sits downstream of what this phase was scoped to build. The phase's job was "consume field 4517 correctly and exclude/flag what it cannot parse," and it does that. Whether field 4517 itself contains enough real data to safely schedule live desks is a separate question that the operator has already identified as needing new functionality in a future milestone. I classify this as **production-readiness debt for the next milestone**, not a Phase 6 blocker — but it is real, disclosed, unresolved risk and is carried forward under `human_verification` above so it is not lost.

---

_Verified: 2026-07-29_
_Verifier: Claude (gsd-verifier)_
