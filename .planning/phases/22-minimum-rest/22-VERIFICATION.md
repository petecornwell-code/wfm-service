---
phase: 22-minimum-rest
verified: 2026-10-04T04:51:29Z
status: gaps_found
score: 4/5 roadmap success criteria verified (1 failed: REST-07)
behavior_unverified: 0
overrides_applied: 0
covered_files: [".planning/phases/22-minimum-rest/22-01-PLAN.md", ".planning/phases/22-minimum-rest/22-01-SUMMARY.md", ".planning/phases/22-minimum-rest/22-02-PLAN.md", ".planning/phases/22-minimum-rest/22-02-SUMMARY.md", ".planning/phases/22-minimum-rest/22-03-PLAN.md", ".planning/phases/22-minimum-rest/22-03-SUMMARY.md", ".planning/phases/22-minimum-rest/22-04-PLAN.md", ".planning/phases/22-minimum-rest/22-04-SUMMARY.md", ".planning/phases/22-minimum-rest/22-05-PLAN.md", ".planning/phases/22-minimum-rest/22-05-SUMMARY.md", ".planning/phases/22-minimum-rest/22-06-PLAN.md", ".planning/phases/22-minimum-rest/22-06-SUMMARY.md", ".planning/phases/22-minimum-rest/22-07-PLAN.md", ".planning/phases/22-minimum-rest/22-07-SUMMARY.md", ".planning/phases/22-minimum-rest/22-08-PLAN.md", ".planning/phases/22-minimum-rest/22-08-SUMMARY.md", ".planning/phases/22-minimum-rest/22-09-PLAN.md", ".planning/phases/22-minimum-rest/22-09-SUMMARY.md", ".planning/phases/22-minimum-rest/22-10-PLAN.md", ".planning/phases/22-minimum-rest/22-10-SUMMARY.md", "frontend/src/api/client.ts", "frontend/src/pages/AgentExceptions.tsx", "frontend/src/pages/DeskManagement.tsx", "frontend/src/pages/ScheduleResults.tsx", "src/main/java/com/wfm/controller/DeskAgentController.java", "src/main/java/com/wfm/controller/DeskController.java", "src/main/java/com/wfm/controller/ScheduleController.java", "src/main/java/com/wfm/dto/DeskResponse.java", "src/main/java/com/wfm/dto/MinimumRestRequest.java", "src/main/java/com/wfm/dto/RestWaiverResponse.java", "src/main/java/com/wfm/dto/ScheduleDetailResponse.java", "src/main/java/com/wfm/dto/ScheduleSummary.java", "src/main/java/com/wfm/model/AgentRestWaiver.java", "src/main/java/com/wfm/model/ConstraintWeights.java", "src/main/java/com/wfm/model/Desk.java", "src/main/java/com/wfm/model/RestSpan.java", "src/main/java/com/wfm/model/RestWaiverLookup.java", "src/main/java/com/wfm/model/Schedule.java", "src/main/java/com/wfm/model/ScheduleConfig.java", "src/main/java/com/wfm/repository/AgentAssignmentRepository.java", "src/main/java/com/wfm/repository/AgentRestWaiverRepository.java", "src/main/java/com/wfm/repository/AgentShiftAssignmentRepository.java", "src/main/java/com/wfm/service/DeskService.java", "src/main/java/com/wfm/service/RestPredecessorService.java", "src/main/java/com/wfm/service/RestWaiverService.java", "src/main/java/com/wfm/service/ScheduleOutputService.java", "src/main/java/com/wfm/service/ScheduleService.java", "src/main/java/com/wfm/service/SolverService.java", "src/main/java/com/wfm/solver/ScheduleConstraintProvider.java", "src/main/resources/db/migration/V55__add_minimum_rest.sql", "src/test/java/com/wfm/service/DeskServiceMinimumRestTest.java", "src/test/java/com/wfm/service/RestFeasibilityRefusalTest.java", "src/test/java/com/wfm/service/RestPredecessorServiceTest.java", "src/test/java/com/wfm/service/RestWaiverDisclosureTest.java", "src/test/java/com/wfm/service/RestWaiverPredicateGuardTest.java", "src/test/java/com/wfm/service/RestWaiverServiceTest.java", "src/test/java/com/wfm/solver/ConstraintMatchCountNonVacuityTest.java", "src/test/java/com/wfm/solver/MinimumRestShiftConstraintTest.java", "src/test/java/com/wfm/solver/MinimumRestSlotConstraintTest.java", "src/test/java/com/wfm/solver/RestHorizonEdgeTest.java", "src/test/java/com/wfm/solver/ScheduleConstraintClassification.java", "src/test/resources/rest-waiver-predicate-guard.md"]
covered_digest: "v2:sha256:7e1efd6f547fe11109b78208e8a747c8cfc7591aeb78d7df63df7f31e6b3182f"
gaps:
  - truth: "REST-07: Waived rest violations are visible in the solved schedule's output, so a waiver cannot silently hide a roster problem (ROADMAP Success Criterion 5)"
    status: failed
    reason: >
      Two independent, confirmed defects make the disclosure invisible or wrong for the schedule
      views an operator actually uses, outside a narrow RUNNING-poll window.
      (a) Backend DB-fallback paths. ScheduleService.listSchedules and ScheduleService.getScheduleSummary
      fetch an ACCEPTED schedule straight from scheduleRepository and pass it to toSummary ->
      buildRestWaiverDisclosure WITHOUT ever calling loadSnapshotData. Schedule.agentRestWaivers,
      .priorRestSpans, .assignments and .shiftAssignments are all @Transient fields defaulting to
      empty lists; only loadSnapshotData (called from getScheduleDetail, and from nowhere else)
      populates them for a DB-fetched Schedule. Schedule.minimumRestMinutes IS a real mapped @Column,
      so buildRestWaiverDisclosure's early-return-on-null guard does NOT fire; it proceeds to walk the
      (empty) transient collections and silently returns 0 applied / 0 unused regardless of the
      schedule's true waiver state. This was already flagged in 22-08-SUMMARY.md, re-flagged in
      22-10-SUMMARY.md, and is confirmed here by direct inspection of ScheduleService.java (lines
      ~80-129, ~483-546, ~677-718) and Schedule.java (lines ~156-197, carrying the @Transient
      annotations).
      (b) Frontend detail-response contract gap (newly identified by this verification, not
      previously documented in SUMMARY/REVIEW/DISPOSITION). ScheduleDetailResponse.java carries
      ONLY a `restWaiverDisclosure` field (the full applied/unused lists) -- it has no
      appliedRestWaiverCount/unusedRestWaiverCount fields at all; those two count fields exist only
      on ScheduleSummary (returned by the /summary endpoint). ScheduleResults.tsx's header badge
      (line 253) and the Rest Waivers tab's `configured` gate (line 1441) both key off
      `schedule.appliedRestWaiverCount != null || schedule.unusedRestWaiverCount != null`. These two
      fields are populated on the `schedule` state object ONLY while tickSummary's 2-second
      /summary poll is active, which runs ONLY while status === 'RUNNING' (ScheduleResults.tsx line
      90). The moment a solve stops, `tickSummary` calls `loadDetail(false)` which does
      `setSchedule(data)` -- a full state REPLACE with the detail response, wiping the two count
      fields back to undefined because ScheduleDetailResponse never carries them. The same
      overwrite happens on every 30-second periodic detail refresh even while still RUNNING. Net
      effect: for every schedule that is not, at this exact instant, being polled mid-solve -- i.e.
      every finished solve the moment it finishes, and every previously-ACCEPTED schedule reopened
      from history -- the header badge is hidden and the Rest Waivers tab renders "Minimum rest is
      not configured for this desk.", even when rest IS configured and `restWaiverDisclosure.applied`/
      `.unused` ARE correctly populated in that same detail response. This directly contradicts the
      UI-SPEC's and the code's own comment ("their presence is the configured-or-not signal on both
      the poll-merged and full-detail schedule object") -- that claim is false for the full-detail
      object, confirmed by reading ScheduleDetailResponse.java's field list and ScheduleResults.tsx's
      loadDetail/tickSummary functions directly.
      Both defects independently violate REST-07's stated purpose ("a waiver cannot silently hide a
      roster problem") -- (a) misreports a false "nothing to see" for the list/summary surface, and
      (b) hides the fully-correct detail-response disclosure behind "not configured" for the surface
      an operator would actually open to check it.
    artifacts:
      - path: "src/main/java/com/wfm/service/ScheduleService.java"
        issue: "listSchedules (line ~80) and getScheduleSummary (line ~677) build ScheduleSummary via toSummary() for a DB-fetched Schedule without calling loadSnapshotData first, so buildRestWaiverDisclosure computes over empty transient collections and returns a false 0/0"
      - path: "src/main/java/com/wfm/dto/ScheduleDetailResponse.java"
        issue: "Carries no appliedRestWaiverCount/unusedRestWaiverCount fields, so the frontend's configured-or-not signal is never true on the detail response"
      - path: "frontend/src/pages/ScheduleResults.tsx"
        issue: "Header badge (line 253) and RestWaiversTab's `configured` check (line 1441) depend on fields the detail response never sends; loadDetail's setSchedule(data) (line 48) replaces, rather than merges, state on every detail refresh, wiping any counts the live poll previously merged in"
    missing:
      - "Backend: have listSchedules/getScheduleSummary call loadSnapshotData (or an equivalent targeted load of agentRestWaivers/priorRestSpans/assignments/shiftAssignments) before computing a summary for a DB-fetched (non-in-memory) schedule, OR persist the disclosure counts at accept time instead of recomputing them transiently."
      - "Frontend/backend contract: add appliedRestWaiverCount/unusedRestWaiverCount to ScheduleDetailResponse (mirroring ScheduleSummary), or change the frontend's configured-or-not signal to derive from the detail response's own restWaiverDisclosure field (e.g. a dedicated snapshotted-minimum-rest-is-non-null signal) so the badge/tab do not depend on fields that only exist on a different DTO reached only during RUNNING polling."
      - "A test that opens an ALREADY-ACCEPTED or ALREADY-COMPLETED schedule's detail response directly (not via the RUNNING-poll path) and asserts the header badge/tab would show non-hidden content when rest is configured with real waiver data -- none of RestWaiverDisclosureTest's assertions target ScheduleDetailResponse's actual field set or the frontend's reliance on it, which is why this went undetected through 1437 passing backend tests and a clean tsc build."
---

# Phase 22: Minimum Rest Verification Report

**Phase Goal:** An operator can require a minimum gap between an agent's consecutive shifts,
enforced as a hard constraint the solver cannot silently violate, with a pre-solve refusal for the
structurally unavoidable cases and a per-agent, per-date waiver for the genuinely exceptional ones.

**Verified:** 2026-10-04T04:51:29Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | An operator can set a minimum rest period per desk via the desk config UI, and a desk that sets none solves exactly as today (REST-01, REST-04) | ✓ VERIFIED | `DeskManagement.tsx` "Min Rest (hrs)" column (read/edit, em-dash for null, hours↔minutes conversion); `DeskService.setMinimumRest` enforces `0 ≤ x < 1440`, null clears to SQL NULL, equal-value early return, no ACCEPTED-schedule lock (D-14); `ScheduleConstraintProvider.minimumRestShift`/`minimumRestSlot` both lead with `filter(cfg -> cfg.minimumRestMinutes() != null ...)` so a NULL desk produces zero tuples at the first stream node (not a zero-weight penalty); `ConstraintMatchCountNonVacuityTest` asserts match count 0 for both constraints on the no-rest baseline fixture |
| 2 | The solver treats insufficient rest as a hard violation, measured between actual end/start instants, with tested behaviour at both horizon edges (REST-02, REST-05) | ✓ VERIFIED | `minimumRestShift`/`minimumRestSlot` constraints penalize via `RestSpan.gapMinutes` on an indexed self-join keyed on `(agentId, businessDate+1)`; `RestPredecessorService.resolvePriorSpans` looks back exactly one business date (`periodStartDate.minusDays(1)`, confirmed at line 107) sourced from ACCEPTED history only; `RestHorizonEdgeTest` proves first-day-constrained-with-predecessor, first-day-unconstrained-without-one, and last-day-deliberately-unconstrained (D-11) in both modes |
| 3 | A rest violation that is structurally unavoidable is refused before the solve runs, naming the agent and both shifts, by a mechanism separate from the in-solve constraint (REST-03) | ✓ VERIFIED | `SolverService.requireRestFeasibility` (confirmed present, called from `buildSchedule` at line ~499) is a distinct pre-solve method, separate from the constraint-stream mechanism, reasoning over the cross-date product of eligible pairs; `RestFeasibilityRefusalTest` exists covering refusal/non-refusal/pre-horizon/waived-occurrence/both-mode cases per plan 22-07's must_haves |
| 4 | An operator can waive minimum rest for one agent/date with a reason through the existing per-agent exception mechanism; the solver treats a waived pair as legal, and a waived occurrence does not trigger the pre-solve refusal (REST-06) | ✓ VERIFIED | `AgentRestWaiver`/`RestWaiverService`/`DeskAgentController` (`/rest-waivers` endpoints) confirmed; `AgentExceptions.tsx` "Rest Waivers" section with immediate add/delete wired to `restWaivers.save/delete/list` in `client.ts`; **both** `minimumRestShift` and `minimumRestSlot` call `RestWaiverLookup.waives(...)` via `.ifNotExists(AgentRestWaiver.class, filtering(...))`, placed after the gap filter — confirmed by direct code read at `ScheduleConstraintProvider.java` lines 1080-1082 and 1171-1173; `requireRestFeasibility` also calls the same predicate via `isAgentDayWaived`/`RestWaiverLookup.isWaived` (line 2119) before accumulating an `ErrorDetail`; `RestWaiverPredicateGuardTest` + `rest-waiver-predicate-guard.md` registry confirm exactly ONE implementation (`RestWaiverLookup`) and exactly the three expected call sites (`ScheduleConstraintProvider` ×2, `SolverService` ×1), with the third (pre-solve refusal) correctly recorded as landed rather than "expected, not yet landed" |
| 5 | A waived rest violation is visible in the solved schedule's output, so a waiver cannot silently hide a roster problem (REST-07) | ✗ FAILED | See Gaps Summary and the `gaps:` frontmatter entry. Two independent, confirmed defects: (a) `listSchedules`/`getScheduleSummary`'s DB-fallback path reports a false `0 applied / 0 unused` for an ACCEPTED schedule's transient (unhydrated) waiver collections — already known, re-flagged by 22-08/22-10; (b) `ScheduleDetailResponse` carries no count fields at all, so the frontend header badge and Rest Waivers tab fall back to hidden/"not configured" for any schedule not actively mid-poll during a RUNNING solve — i.e. every finished or reopened schedule — newly identified in this verification pass |

### Claim-Specific Adjudications (per the dispatch brief)

**1. Is REST-07 genuinely satisfied?** No. Beyond the already-documented DB-fallback false-zero
(backend), this verification independently confirmed a second, more severe defect: the frontend's
only signal for "is rest configured" on the schedule detail page is a pair of count fields
(`appliedRestWaiverCount`/`unusedRestWaiverCount`) that **do not exist on `ScheduleDetailResponse`
at all** — they exist only on `ScheduleSummary`, reached only via the `/summary` endpoint, polled
only while `status === 'RUNNING'`. The moment a solve finishes (or whenever an already-accepted
schedule is reopened), the page's `loadDetail` fully replaces `schedule` state with the detail
response, which never carries those fields — so the header badge disappears and the Rest Waivers
tab reports "not configured", even though `restWaiverDisclosure.applied`/`.unused` are correctly
populated in that very same response. A `gaps_found` verdict is correct and recommended.

**2. REST-02's "unless waived" clause — wired into BOTH mode-gated constraints?** Yes, confirmed.
Direct code inspection shows `minimumRestShift` (lines 1080-1082) and `minimumRestSlot` (lines
1171-1173) both call `RestWaiverLookup.waives` through an identically-shaped
`.ifNotExists(AgentRestWaiver.class, filtering(...))` clause placed after the gap filter. Plan
22-05 (which lands this) has a completed SUMMARY, so the clause genuinely holds at HEAD, not just
nominally per REQUIREMENTS.md's `Complete` marker.

**3. Deliberate narrowings — implemented as decided, not as accidents?** Yes, confirmed for all
four:
- **D-05 one-business-date lookback:** `RestPredecessorService` reads `periodStartDate.minusDays(1)` exactly once, with the javadoc explicitly invoking D-05's sub-24-hour bound as the proof a single step suffices.
- **D-11 last-day unconstrained:** `RestHorizonEdgeTest` contains `lastDayUnconstrained_noForwardLookahead_zeroMatches_deliberate`, a named test pinning the decision rather than leaving it an untested accident.
- **D-04 NULL → zero tuples:** Both rest constraints lead with a filtered `forEach(ScheduleConfig.class)` gate (`cfg.minimumRestMinutes() != null`), and `ConstraintMatchCountNonVacuityTest` asserts a match count of 0 (not merely a zero score) for both "Minimum rest (shift)" and "Minimum rest (slot)" on the no-rest baseline — structural inertness, not a coincidentally-zero penalty.
- **D-08 single predicate implementation:** `RestWaiverLookup` is the only implementation; `RestWaiverPredicateGuardTest` performs set-equality checks against `rest-waiver-predicate-guard.md`'s two allowlists (entity references, call sites) plus a textual second-implementation scan, and the registry's three-row call-site table correctly shows the pre-solve refusal's row as landed (not the "expected, not yet landed" placeholder), confirming the guard was updated in the same commit series as plan 22-07, per its own must_haves.

### Required Artifacts (representative sample; full list in `files_modified` across 10 plans)

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/wfm/model/RestSpan.java` | Shared gap/span implementation | ✓ VERIFIED | 131 lines, `gapMinutes`/`ofShift`/`ofSlots` present, used by both constraints, the pre-solve refusal, and the disclosure builder |
| `src/main/java/com/wfm/model/RestWaiverLookup.java` | Single waived-pair predicate | ✓ VERIFIED | 65 lines, `waives`/`isWaived`, used by exactly 3 call sites per the structural guard |
| `src/main/java/com/wfm/model/AgentRestWaiver.java` | Waiver entity | ✓ VERIFIED | 68 lines, substantive |
| `src/main/java/com/wfm/service/RestWaiverService.java` | Waiver CRUD | ✓ VERIFIED | 124 lines, substantive |
| `src/main/java/com/wfm/service/RestPredecessorService.java` | Horizon lookback | ✓ VERIFIED | 211 lines, one-day lookback confirmed |
| `src/main/java/com/wfm/solver/ScheduleConstraintProvider.java` (minimumRestShift/minimumRestSlot) | Both mode-gated hard constraints | ✓ VERIFIED | Both present, both waiver-excluding, both registered in `defineConstraints` |
| `src/main/java/com/wfm/service/SolverService.java` (requireRestFeasibility) | Pre-solve refusal | ✓ VERIFIED | Present, separate mechanism, waiver-aware |
| `src/main/java/com/wfm/service/ScheduleOutputService.java` (buildRestWaiverDisclosure) | Disclosure computation | ✓ VERIFIED (compute logic), ⚠️ see gap | Correctly computes from solution rows when its inputs are hydrated; the gap is in what hydrates/reaches it, not this method's own logic |
| `src/main/java/com/wfm/dto/ScheduleDetailResponse.java` | Disclosure + count fields | ⚠️ PARTIAL (see gap) | Carries `restWaiverDisclosure` correctly but is MISSING `appliedRestWaiverCount`/`unusedRestWaiverCount`, which the frontend badge/tab require |
| `frontend/src/pages/DeskManagement.tsx` | Min Rest column | ✓ VERIFIED | Header, read/edit cells, conditional PUT, em-dash-for-null all present |
| `frontend/src/pages/AgentExceptions.tsx` | Rest Waivers section | ✓ VERIFIED | Heading, immediate add/delete, Date/Reason/Actions table all present |
| `frontend/src/pages/ScheduleResults.tsx` | Header badge + Rest Waivers tab | ✗ WIRED BUT DATA-STARVED | Code is present and structurally correct, but depends on fields never sent by the endpoint it actually reads for 95%+ of real views (see gap) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `DeskManagement.tsx` | `client.ts` → `DeskController` | `desks.setMinimumRest` → `PUT /desks/{id}/minimum-rest` | ✓ WIRED | Confirmed end to end |
| `ScheduleConstraintProvider.minimumRestShift`/`minimumRestSlot` | `RestWaiverLookup` | `.ifNotExists(..., filtering(RestWaiverLookup.waives))` | ✓ WIRED | Confirmed in both constraints |
| `SolverService.requireRestFeasibility` | `RestWaiverLookup` | `isAgentDayWaived` → `RestWaiverLookup.isWaived` | ✓ WIRED | Confirmed |
| `ScheduleService.loadSnapshotData` | `AgentRestWaiverRepository`/`RestPredecessorService` | Populates `agentRestWaivers`/`priorRestSpans` for the **detail** path | ✓ WIRED (detail path only) | Confirmed — but this method is NOT called from `listSchedules`/`getScheduleSummary`, which is the root of gap (a) |
| `ScheduleResults.tsx` header badge / Rest Waivers tab | `ScheduleDetailResponse` | Reads `appliedRestWaiverCount`/`unusedRestWaiverCount` | ✗ NOT WIRED | These fields do not exist on `ScheduleDetailResponse`; the read always resolves to `undefined` outside the RUNNING-poll merge window — root of gap (b) |

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|---|---|---|---|---|
| REST-01 | 22-01, 22-04 | Operator can set minimum rest per desk | ✓ SATISFIED | API + UI confirmed |
| REST-02 | 22-01, 22-02, 22-05 | Solver treats insufficient rest as hard violation, unless waived | ✓ SATISFIED | Both mode-gated constraints confirmed, waiver exclusion confirmed in both |
| REST-03 | 22-07 | Pre-solve refusal, mechanism separate from in-solve constraint | ✓ SATISFIED | `requireRestFeasibility` confirmed |
| REST-04 | 22-01, 22-02, 22-04 | Desk with no minimum rest solves exactly as before | ✓ SATISFIED | Structural zero-tuple gates + non-vacuity test confirmed |
| REST-05 | 22-06 | Defined, tested behaviour at both horizon edges | ✓ SATISFIED | `RestHorizonEdgeTest` confirmed |
| REST-06 | 22-03, 22-05, 22-09 | Operator can waive; solver treats waived pair as legal | ✓ SATISFIED | End-to-end waiver path + constraint exclusion confirmed |
| REST-07 | 22-08, 22-10 | Waived rest violations visible in solved schedule's output | ✗ BLOCKED | Two confirmed defects — see Gaps Summary |

No orphaned requirements found — REQUIREMENTS.md's Phase 22 mapping (REST-01 through REST-07) matches exactly the set declared across the 10 plans' `requirements:` frontmatter.

### Anti-Patterns Found

No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers found in any of the 28 production/test files modified by this phase.

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `src/main/java/com/wfm/service/ScheduleService.java` | ~677-718 | DB-fallback summary path silently returns false 0/0 for waiver counts | 🛑 Blocker | Root cause of gap (a) — see REST-07 gap |
| `frontend/src/pages/ScheduleResults.tsx` | 253, 1441, 48 | Badge/tab visibility keyed on fields absent from the detail response; `setSchedule(data)` full-replace wipes previously-merged counts | 🛑 Blocker | Root cause of gap (b) — see REST-07 gap |
| `src/main/java/com/wfm/service/ScheduleOutputService.java` | WR-01 (code review) | `buildRestWaiverDisclosure` reads `schedule.getDayStart()` without the codebase's null-coalescing anchor fallback | ⚠️ Warning | Open per 22-REVIEW-DISPOSITION.md; reviewer judged currently unreachable (V54 precedes V55) but inconsistent with the phase's own defensive convention |
| `ScheduleService.toSummary` / `ScheduleController.toSummary` | WR-02 (code review) | Rest-waiver summary counts computed independently in two places, protected only by a parity test, not a structural guard | ⚠️ Warning | Open per 22-REVIEW-DISPOSITION.md; a real duplication risk the reviewer flagged as inconsistent with this same phase's own structural-guard precedent (D-08) |
| `frontend/src/pages/DeskManagement.tsx` | IN-01 (code review) | `hoursStringToMinutes`/`handleUpdate` can yield `NaN`, serialized by `JSON.stringify` as `null`, silently clearing a configured minimum rest | ℹ️ Info | Open; reviewer judged likely unreachable through a browser numeric input but unguarded in code |

### Human Verification Required

None triggered by this pass — the REST-07 failures are deterministically confirmed by direct code
reading (DTO field lists, call graphs, polling lifecycle), not left as an uncertain judgment call.
The phase's own accumulated end-of-phase human-check items (20 steps across 22-04/22-09/22-10,
covering narrow-viewport reflow, visual color reads, and the live round-trip of the badge/tab) are
recorded in those plans' SUMMARY.md files and remain outstanding UAT items independent of this
verification's gaps_found verdict; they should still be run once the REST-07 gap is closed, since a
human UAT pass today would mostly be exercising the broken fallback path.

### Gaps Summary

Six of seven requirements (REST-01 through REST-06) are genuinely implemented, correctly wired, and
behaviorally tested — the solver-side hard constraint, the pre-solve refusal, the waiver mechanism,
the shared predicate and its structural guard, and the desk-configuration UI all hold up under
direct code inspection, not just under SUMMARY.md's claims.

REST-07 — "waived rest violations are visible in the solved schedule's output, so a waiver cannot
silently hide a roster problem" — does NOT hold at HEAD for either the solved-schedule's long-term
record (the list/summary surfaces fall back to a false 0/0) or, more importantly, for the actual
schedule detail page an operator would check it from, outside the few seconds a solve is actively
running. This is not a cosmetic UI gap: it is the exact failure mode REST-07 exists to prevent — a
waiver's effect becoming invisible — occurring on the primary surface built to disclose it. The
phase's own SUMMARY.md files disclosed gap (a) honestly; this verification additionally identified
gap (b), a distinct and more impactful defect in the same area that had not previously been
reported.

Recommended next step: a closure plan (`/gsd-plan-phase 22 --gaps`) addressing both defects —
hydrating the transient waiver/pre-horizon collections before computing a DB-fallback summary (or
persisting the disclosure counts at accept time), and giving `ScheduleDetailResponse` its own
`appliedRestWaiverCount`/`unusedRestWaiverCount` fields (or an equivalent configured-signal) so the
frontend's badge and tab do not depend on fields that only exist on a sibling DTO reached only
during live polling.

---

_Verified: 2026-10-04T04:51:29Z_
_Verifier: Claude (gsd-verifier)_
