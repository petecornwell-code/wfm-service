---
status: complete
phase: 13-per-day-hours-visibility
source: [13-VERIFICATION.md]
started: 2026-08-24T13:15:24Z
updated: 2026-08-25T15:45:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Live-desk visual walkthrough of the collapsed summary muted colour and tooltip
expected: Zero-row agent renders light-grey italic with 'Not set — using schedule default' tooltip; any agent with >=1 stored row renders in default body colour with no tooltip
why_human: Visual colour/tooltip rendering in a real browser against real data — WINDOWS.md item 1
result: pass
tested_against: https://d2bbtcc80peap7.cloudfront.net/desks/6170be17-3bee-41da-9d81-62ddd50c786f/agents (deploy b2c0082, bundle index-BbMpAUx_.js)
note: |
  FULLY OBSERVED on the second run (both branches rendered side by side).

  First run was a PARTIAL and is recorded here rather than overwritten: all 28
  agents on the sole dev desk (Stubhub (EN)) had a full 7-row agent_day_hours
  set, so no agent was in the not-set state and the muted branch rendered for
  nobody. Only the ">=1 stored row -> default body colour, no tooltip" half was
  exercised. Bundle-string presence (#9ca3af, 'Not set (default)', 'using
  schedule default') was NOT accepted as proof of render — that is the exact
  anti-pattern .continue-here.md records for this phase.

  Second run: agent Adaeze Dawari (8b16335d-07dc-43ea-8955-6f29ba2f096e) had all
  seven weekday rows cleared via PUT .../day-hours/{day} {"clearRow":true} (7x
  HTTP 200), confirmed by API read as hasRow=false on all seven with
  effectiveHours=8 resolved from the schedule default. Blast radius checked: the
  other 27 agents were byte-identical afterwards (0 affected) — which also
  re-confirms the I-3 single-weekday-write isolation property.
  User then observed the muted/not-set rendering against unmuted peers in the
  same table and passed. Both branches of the truth are now visually confirmed.
  Agent state restored from backup afterwards (see test 2 note).

### 2. Live-desk walkthrough of the per-cell editor seeding the 'Not set (default)' literal and the expanded 7-day grid's five display states
expected: Clicking a not-set cell opens the editor pre-filled with 'Not set (default)'; all 5 weekday states (MAND, PTO, explicit-zero, worked, not-set) are visually distinct
why_human: Requires a live DB/BambooHR-configured environment — WINDOWS.md items 2 and 5
result: pass
tested_against: https://d2bbtcc80peap7.cloudfront.net/desks/6170be17-3bee-41da-9d81-62ddd50c786f/agents (deploy b2c0082)
note: |
  All five states were STAGED to make this observable — the dev dataset could not
  produce them naturally. Across all 28 agents x 7 days (196 cells) the live data
  held only MANDATORY (52), PTO (6) and worked (138): explicit-zero and not-set
  were entirely absent, so 2 of the 5 states had no representative cell.

  Staged on agent Adaeze Dawari (8b16335d-07dc-43ea-8955-6f29ba2f096e) via the
  per-cell endpoint (4x HTTP 200), leaving Fri/Sat/Sun cleared so a not-set cell
  remained clickable for the seed-value half:
    MON MANDATORY | TUE PTO | WED explicit-0 | THU 8h worked | FRI/SAT/SUN not-set
  API read-back confirmed 5/5 distinct states in one row; 0 other agents affected.

  Discriminator note: MON/TUE/WED all resolve to effectiveHours=0 by three
  different routes (label MANDATORY, label PTO, stored 0). Visual distinctness
  between them is the substance of this test — identical rendering would make the
  row ambiguous despite correct numbers. User confirmed distinct.

  Seed-value half (UI-SPEC E4, the gap 208d5c0 closed): user clicked a not-set
  cell and confirmed the editor pre-filled with the literal 'Not set (default)'
  rather than opening blank.
  Agent restored to its pre-test profile immediately afterwards.

  *** SUPERSEDED 2026-08-25, THEN RE-VERIFIED THE SAME DAY ***
  This pass was judged against the pre-amendment contract, where the editor opened
  PRE-FILLED with 'Not set (default)'. Resolving gap G-13-DD deliberately changed
  that: the editor now opens EMPTY with the value as placeholder ghost text, because
  a seeded field collapses the native datalist's option list and made the picklist
  unusable. The seed-value half of this test therefore no longer describes shipped
  behaviour. The five-state-distinctness half is unaffected and still holds.
  Re-verify against 13-UI-SPEC.md's E4-empty amendment.

  RE-VERIFIED 2026-08-25 against the amended contract (deploy 7fc7168). Confirmed:
  editor opens EMPTY with the current value as placeholder ghost text; the full
  100-entry picklist is browsable with PTO / MANDATORY / Not set (default) first;
  and — the safety-critical path — clicking away without typing leaves the value
  unchanged, so the cellDirtyRef guard holds.
  That last confirmation also retro-resolves the Saturday-row question below: with
  the guard demonstrably working, the single row lost during the test 7/8 session
  is attributable to 'Not set (default)' being selected while its clipping was
  inspected, which is the designed clearRow path, not data loss. Row was restored;
  desk returned to 196/196.

### 3. Live-desk walkthrough of the bulk "Set all days to…" range guard and confirmation dialog
expected: 1000/-1 rejected via toast before confirm()/network; 24 and 0 accepted; label-count confirm() fires only when >=1 weekday carries MANDATORY/PTO
why_human: End-to-end browser behaviour with no frontend test framework in this repo — WINDOWS.md item 6
result: pass
tested_against: https://d2bbtcc80peap7.cloudfront.net/desks/6170be17-3bee-41da-9d81-62ddd50c786f/agents (deploy b2c0082)
note: |
  Covers the WR-01 client-side range guard shipped in 974dc25 (the UX half; the
  authoritative 0-24 rejection is server-side in setContractedHours, ab0af9d).
  Out-of-range values (1000 / -1) are rejected via toast BEFORE confirm() and
  before any network call, so no write occurs on the reject path.
  Tested on agent Adaeze Dawari (8b16335d-07dc-43ea-8955-6f29ba2f096e), chosen
  because a verified on-disk backup existed and she carried 2 MANDATORY labels
  (MON/WED) — the precondition for the label-count confirm() dialog to fire.
  NOTE: a successful bulk apply is destructive by design — setContractedHours
  deletes and recreates all seven rows with dayOffType unset, wiping
  MANDATORY/PTO labels. That is audit finding I-3's surviving behaviour, which
  the confirm() dialog exists to warn about, not a defect introduced here.

### 4. PUT .../day-hours/{day} HTTP-level dispatch for a malformed {day} segment
expected: 400 (not 500), body names only the 'day' parameter, no rejected token or internal type leaked; valid/out-of-range day-hours paths and the bulk contracted-hours 400 message all unchanged; a genuine unexpected failure still returns 500
why_human: No @WebMvcTest/MockMvc harness exists for DeskAgentController in this codebase, so Spring's actual dispatch to GlobalExceptionHandler.handleTypeMismatch for a real HTTP request is unit-tested but not integration-tested — WINDOWS.md item 7 (P-17 residual gap, declared not discovered)
result: pass
source: automated
tested_against: https://d2bbtcc80peap7.cloudfront.net (deploy b2c0082) on 2026-08-25
evidence: |
  PUT /api/v1/desks/{deskId}/agents/{agentId}/day-hours/notaday
    headers: X-Tenant-ID: 1, Content-Type: application/json
    -> HTTP 400
    -> {"error":{"code":"VALIDATION_FAILED",
                 "message":"Invalid value for path parameter 'day'","details":[]}}
  Control: same path with a VALID day segment (MONDAY) -> HTTP 200, so the 400 is
  type-specific and not a blanket rejection.
  Body names only the 'day' parameter; the rejected token "notaday" and the
  required type are both absent -> ASVS V5 no-leak constraint holds over real HTTP.
  This CLOSES P-17: Spring's actual dispatch to handleTypeMismatch is now proven
  end-to-end through CloudFront -> ALB -> ECS, which the unit test could not do.
correction: |
  A FIRST attempt at this probe omitted the X-Tenant-ID header and returned a
  400 from TenantFilter with Spring's DEFAULT error shape
  ({"timestamp","status","error","path"}) — NOT from handleTypeMismatch. That
  result was briefly reported as proof and was invalid: the request never reached
  a controller. Distinguishing marker: a TenantFilter rejection has no
  "error.code" field, the real handler response has code VALIDATION_FAILED.
  Re-run with the header produced the evidence recorded above.

### 5. E1 overflow (backstop): the collapsed summary's range output still fits the existing dense 13-column row after the muted-colour change
expected: No wrap, no horizontal-scroll regression at the widest range value
why_human: insufficient_spec — visual layout claim, abstain per honest-verifier contract
result: pass
tested_against: https://d2bbtcc80peap7.cloudfront.net/desks/6170be17-3bee-41da-9d81-62ddd50c786f/agents (deploy 7fc7168)
re_run: |
  Initially SKIPPED (user redirected mid-test). Re-run 2026-08-25 at the true worst
  case: Adaeze staged MON=0.25, all other days 23.75, rendering the collapsed
  summary "0.25-23.75" — 10 characters, the widest legal output. User confirmed no
  wrap, no column squeeze, no horizontal-scroll regression in the 13-column row.
  The LAYOUT is fine; the stated 5-character BOUND was still wrong (see finding).
finding: |
  Independently verified while staging this test (NOT a user report): the E1 truth
  recorded in 13-UI-SPEC.md / 13-VERIFICATION.md claims the collapsed summary is
  "a numeric closed vocabulary of at most 5 characters and cannot truncate".
  That is FALSE for the range branch. formatHoursSummary renders min-max, and with
  the enforced 0-24 bound at 2dp a legal value pair yields "0.25-23.75" = 10
  characters, double the asserted bound. Demonstrated on the live deployment using
  only in-range values through the supported endpoint (no bypass).
  13-VERIFICATION.md currently reclassifies this truth from coincidental_reliance
  to "hardened, no longer coincidental" on the strength of ab0af9d's 0-24 range
  check. That reclassification does not hold: the bound constrains VALUES, not
  RANGE WIDTH, and "0.25-23.75" satisfies the bound while doubling the character
  budget. See ## Gaps entry G-13-5.

### 6. E3 populated (backstop): mixed-state weekday row rendering matches the CONTEXT.md mockup
expected: All 5 states visually distinct in the expanded grid
why_human: insufficient_spec — abstain per honest-verifier contract
result: pass
tested_against: https://d2bbtcc80peap7.cloudfront.net/desks/6170be17-3bee-41da-9d81-62ddd50c786f/agents (deploy 071b777)
note: |
  Judged against the ACCEPTED MOCKUP from 13-CONTEXT.md, reproduced byte-for-byte
  in live data on agent Adaeze Dawari (7x HTTP 200, verified by API read-back):
      MOCKUP: Mon=8 Tue=8 Wed=MAND Thu=8 Fri=4 Sat=PTO Sun=PTO
      LIVE  : Mon=8 Tue=8 Wed=MAND Thu=8 Fri=4 Sat=PTO Sun=PTO   (exact match)
  Fri=4 is the load-bearing cell: the only non-8 numeric, so it proves the grid
  renders per-day values rather than repeating one figure.
  Collapsed summary read "0-8" as expected (MAND/PTO resolve to 0, pulling the
  low end down) — consistent with 13-UI-SPEC.md Section 1.
caveat: |
  WORDING DEFECT in this test, not in the code. The expectation says "all 5 states
  visually distinct", but the CONTEXT.md accepted mockup that E3 populated actually
  specifies contains only THREE state kinds — worked (8, 4), MANDATORY and PTO.
  Explicit-zero and not-set do not appear in it, so mockup fidelity cannot
  demonstrate five states. Two different checks were written into one line.
  What this pass covers: mockup fidelity (the E3 populated backstop).
  The five-state distinctness claim was separately covered by test 2, where all
  five were deliberately staged in one row.

### 7. E3 overflow (backstop): horizontal-scroll behaviour of the expanded 7-day grid
expected: No layout break at the widest content
why_human: insufficient_spec — abstain per honest-verifier contract
result: pass
tested_against: https://d2bbtcc80peap7.cloudfront.net/desks/6170be17-3bee-41da-9d81-62ddd50c786f/agents (deploy 7fc7168)
note: |
  Judged at the genuine worst case: agent Adaeze Dawari staged with 23.75 in all
  seven weekday cells (7x HTTP 200, API-verified). 23.75 is 5 characters — wider
  than the MAND (4) and PTO (3) badges — so this is the widest content the 7-day
  mini-column grid can hold.
  Confirms the E3 overflow claim that the expanded row 'adds no new columns (D-01)
  and inherits the existing 13-column table's horizontal-scroll behavior
  unmodified'.
  Interaction note: a uniform week hits formatHoursSummary's min===max branch, so
  the collapsed summary stayed a short "23.75" (5 chars) even with every expanded
  cell at max width. E1-collapsed and E3-expanded overflow peak under OPPOSITE
  conditions — E1 needs a varying week (the 0.25-23.75 range of skipped test 5),
  E3 needs a uniform wide one — so they cannot be exercised by the same data and
  are correctly separate tests.
  Data integrity re-checked after the deploy that landed mid-test: 196/196 rows
  present across all 28 agents, none lost.

### 8. E4 overflow (re-run vs amended spec): 'Not set (default)' clips at the 90px input width but stays selectable and unambiguous
expected: |
  Opening the per-cell editor shows the full datalist. The 'Not set (default)'
  entry is visibly TRUNCATED (the popup renders at the input's 90px width — this
  is now the asserted behaviour, not a defect). Despite the truncation the entry
  is still selectable, and no other option in the list begins with 'Not set', so
  which entry it is remains unambiguous.
why_human: explicit (13-UI-SPEC.md E4 overflow, corrected 2026-08-25) — was `backstop`/insufficient_spec before the amendment; a verifier now ASSERTS the clipping rather than abstaining on it
result: pass
tested_against: https://d2bbtcc80peap7.cloudfront.net/desks/6170be17-3bee-41da-9d81-62ddd50c786f/agents (deploy 7fc7168)
note: |
  Re-run 2026-08-25 against the AMENDED E4 overflow contract (13-UI-SPEC.md,
  corrected by 19f8c5d). User confirmed the shipped behaviour: the entry is
  truncated at the 90px input width, and is still selectable and unambiguous.
  NO CODE CHANGED between the failing first run and this pass — what changed is
  the contract being judged. The first run's report was correct and is preserved
  verbatim below rather than overwritten; it is what caused the spec correction.
  A reader who wants the defect history should read `superseded_run` and G-13-8,
  not treat this `pass` as evidence the clipping was fixed. It was not fixed; it
  was accepted, asserted, and then verified as asserted.
superseded_run: |
  FIRST RUN 2026-08-25 (deploy 7fc7168) — result: issue, severity: cosmetic,
  reported verbatim: "text is clipped".
  That verdict was judged against the PRE-amendment expectation, which read
  "text is not clipped, since the datalist popup is not constrained by [the cell
  width]". The report was correct and the SPEC was wrong: a native <datalist>
  popup renders at its <input>'s width, fixed at 90px (DeskAgents.tsx:634), and
  'Not set (default)' is 17 chars at 0.85rem. Commit 19f8c5d retracted the false
  reasoning, moved the E4 overflow row backstop -> explicit, and recorded the
  clipping as an accepted limitation of the native control (G-13-8, status_final:
  accepted — both remedies judged worse than the defect).
  This re-run therefore judges the SHIPPED contract, not the retracted one. Same
  handling as test 2, which was superseded by the E4-empty amendment and re-verified
  the same day. No code changed between the two runs.
  This was also the first run in which the test was observable at all: before the
  G-13-DD fix the editor opened seeded, which filtered 'Not set (default)' out of
  the datalist entirely, so the text could never be seen — clipped or otherwise.

## Summary

total: 8
passed: 8
issues: 0
pending: 0
skipped: 0
blocked: 0

# READ THIS BEFORE READING "8 passed" AS "8 clean first-time passes".
# Three of the eight passed only on a RE-RUN, and two of those re-runs judged an
# AMENDED contract rather than the one originally written:
#   test 2 — re-verified against the E4-EMPTY amendment (editor opens empty, not seeded)
#   test 5 — initially skipped; re-run at the true worst case "0.25-23.75"
#   test 8 — first run reported `issue` ("text is clipped") and that report is what
#            forced the E4-OVERFLOW correction; re-run passed against the corrected
#            contract with NO intervening code change. See its `superseded_run`.
# The ## Gaps section carries THREE entries, which is not a mismatch:
#   G-13-5  <- test 5 (raised by orchestrator during staging, not a user report;
#                      test itself later re-run and PASSED). accepted.
#   G-13-8  <- test 8's first run. Accepted, NOT fixed — the clipping still ships;
#              it is now asserted by the spec instead of contradicted by it.
#   G-13-DD <- reported during test 7 but not a verdict on test 7; fixed in code
#              (7fc7168) and re-verified via test 2's re-run.
# All three are dispositioned. None requires a gap-closure fix plan.

## Gaps

- gap_id: G-13-8
  truth: "E4 overflow: 'Not set (default)' is not clipped by the weekday mini-column width, because the native datalist popup is not constrained by it (13-UI-SPEC.md E4)"
  status: failed
  reason: "User reported: text is clipped"
  severity: cosmetic
  test: 8
  root_cause: |
    The E4 overflow row's STATED REASONING is factually wrong, and that is the
    substantive finding here — not the pixels. It asserts the popup 'is not
    constrained by' the cell width. In practice a native <datalist> popup is
    rendered at the width of its <input>, and that input is fixed at 90px
    (DeskAgents.tsx:634, `style={{ width: '90px', fontSize: '0.85rem' }}`).
    'Not set (default)' is 17 characters at 0.85rem and does not fit in 90px, so
    it truncates. The backstop was resolved on an assumption about browser
    behaviour that does not hold.
  artifacts:
    - path: "frontend/src/pages/DeskAgents.tsx"
      issue: "Per-cell editor input fixed at width:90px (line 634); the native datalist popup inherits that width and clips the 17-character 'Not set (default)' entry."
    - path: ".planning/phases/13-per-day-hours-visibility/13-UI-SPEC.md"
      issue: "E4 overflow row resolves the backstop with the reasoning 'the datalist popup is not constrained by [the cell width]', which is disproven by observation."
  missing:
    - "Correct the E4 overflow reasoning in 13-UI-SPEC.md — the popup IS width-constrained by the input."
    - "Decide the remedy: widen the per-cell input (trades against E3 expanded-row width, which test 7 passed at 90px x7), shorten the picklist literal (ripples into D-04 and the gap-2 fix), or accept the clipping as a recorded native-control limitation."
  status_final: accepted
  resolved: |
    ACCEPTED 2026-08-25 (operator decision) — clipping is NOT fixed; the false
    reasoning IS corrected. 13-UI-SPEC.md's E4 overflow row now states that the
    native datalist popup renders at its input's width (90px) and therefore does
    clip the 17-character literal, and the row moves backstop -> explicit so a
    future verifier asserts the clipping rather than abstaining on it.
    Both alternative remedies were judged worse than the defect: widening the input
    to ~140px would take the expanded grid ~678px -> ~1028px and trade away the E3
    overflow behaviour test 7 just verified; shortening the literal would change the
    exact string saveDayHours matches for clearRow and re-open phase-13 gap 2.
    No code change. No re-deploy required.
  note: |
    Functional impact is low: the entry remains selectable and nothing else in the
    list starts with 'Not set', so the truncated text is still unambiguous. Logged
    cosmetic on that basis. The spec-accuracy defect is the part worth fixing.
  re_verified: |
    2026-08-25 — test 8 re-run against the CORRECTED E4 overflow contract and passed.
    To be explicit about what that pass does and does not mean: the clipping is still
    present in the shipped build and no code was changed. The pass records that the
    spec now describes reality (clipped, selectable, unambiguous) and that reality was
    observed to match. This gap is closed by ACCEPTANCE + SPEC CORRECTION, not by a fix.
    If the 90px per-cell input is ever widened, or the 'Not set (default)' literal
    shortened, the E4 overflow row in 13-UI-SPEC.md must be revisited again.

- gap_id: G-13-DD
  truth: "The per-cell combo's picklist is pickable — an operator can open the dropdown and choose a value (13-UI-SPEC.md D-03 amendment, 2026-08-25)"
  status: failed
  reason: "User reported: cant modify dropdown now for hours. Root cause identified by orchestrator during UAT, not inferred from the report alone."
  severity: major
  test: null
  reported_during: 7
  root_cause: |
    startEditCell (DeskAgents.tsx:278) seeds editCellValue with the cell's CURRENT
    value via seedValueForEntry. A native <datalist> filters its <option> set to
    those matching the input's current text. With the input pre-seeded (e.g.
    "23.75", or "MANDATORY", or "Not set (default)"), the list collapses to the
    single self-matching entry — so there is nothing to pick without first
    manually clearing the field.
    The 97 numeric options added in 071b777 are therefore unreachable in exactly
    the case they exist for: editing a cell that already has a value.
    NOTE this is a pre-existing interaction, not new in 071b777 — the three label
    options were always filtered the same way. Adding the numeric range is what
    made the defect observable, and what made it matter.
  artifacts:
    - path: "frontend/src/pages/DeskAgents.tsx"
      issue: "startEditCell:278 seeds editCellValue with the current value; the datalist at :405 then filters 97+3 options down to ~1. Seed (E4 requirement) and pick-affordance (D-03 amendment) are in direct conflict."
  missing:
    - "Decide between: (a) clear the field on focus so the full list shows, with a guard so a blur-without-typing restores the seed rather than firing clearRow; (b) revert the 97 numeric options as undeliverable via native datalist; (c) replace the control with a real <select> plus separate free-text entry."
  blocked_on: "resolved 2026-08-25 — operator chose clear-on-focus + clearRow guard"
  resolution: |
    Implemented: startEditCell now opens the editor EMPTY (unfiltered datalist =
    full 100-option picklist browsable) with the previous seed moved to the input's
    placeholder so the stored value stays visible.
    Guard: cellDirtyRef gates saveDayHours — an untouched blur returns early with NO
    network call, so an empty field only means clearRow when the operator actually
    typed. Without this, opening the editor and clicking away would silently delete
    the row.
    Out-of-range revert also now clears to empty rather than re-seeding, so the
    picklist stays browsable while the operator corrects a rejected value.
    Amends 13-UI-SPEC.md E4 'empty' + the Section 3 resolution table. Partially
    REVERSES original phase-13 gap 2 (editor opening blank) — deliberately, and
    differing from the original defect in that the value is preserved as placeholder
    rather than lost. UAT test 2 marked for re-verification.

- gap_id: G-13-5
  truth: "E1 long-text: the collapsed summary is a numeric closed vocabulary of at most 5 characters and cannot truncate (13-UI-SPEC.md E1)"
  status: failed
  reason: "Verified by orchestrator during UAT staging, not user-reported: formatHoursSummary's min-max range branch renders 10 characters ('0.25-23.75') from legal in-range values, double the asserted 5-character bound."
  severity: minor
  test: 5
  artifacts:
    - path: "frontend/src/pages/DeskAgents.tsx"
      issue: "formatHoursSummary returns `${formatHours(min)}-${formatHours(max)}`; with 2dp values in 0-24 this reaches 10 chars. The <=5 char claim only ever held for the min===max single-value branch."
    - path: ".planning/phases/13-per-day-hours-visibility/13-VERIFICATION.md"
      issue: "coincidental_reliance_items reclassified this truth as hardened by ab0af9d's 0-24 check; the bound constrains values, not range width, so the reclassification is unsound."
  missing:
    - "Correct the E1 long-text truth to state the real bound (range branch reaches 10 chars), OR constrain the summary rendering (e.g. round the range endpoints for display) so <=5 actually holds."
    - "Re-judge the E1 overflow layout at the widest value — skipped test 5, currently unknown."
