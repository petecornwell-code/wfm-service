---
status: testing
phase: 13-per-day-hours-visibility
source: [13-VERIFICATION.md]
started: 2026-08-24T13:15:24Z
updated: 2026-08-25T13:24:41Z
---

## Current Test

number: 5
name: E1 overflow (backstop): the collapsed summary's range output still fits the existing dense 13-column row after the muted-colour change
expected: |
  No wrap, no horizontal-scroll regression at the widest range value
awaiting: user response

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
result: [pending]

### 6. E3 populated (backstop): mixed-state weekday row rendering matches the CONTEXT.md mockup
expected: All 5 states visually distinct in the expanded grid
why_human: insufficient_spec — abstain per honest-verifier contract
result: [pending]

### 7. E3 overflow (backstop): horizontal-scroll behaviour of the expanded 7-day grid
expected: No layout break at the widest content
why_human: insufficient_spec — abstain per honest-verifier contract
result: [pending]

### 8. E4 overflow (backstop): the seeded 'Not set (default)' text in the browser's native datalist dropdown
expected: Text is not clipped by the weekday mini-column width, since the datalist popup is not constrained by it
why_human: insufficient_spec — abstain per honest-verifier contract
result: [pending]

## Summary

total: 8
passed: 4
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
