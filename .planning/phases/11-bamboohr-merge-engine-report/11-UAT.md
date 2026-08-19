---
status: testing
phase: 11-bamboohr-merge-engine-report
source: [11-VERIFICATION.md]
started: 2026-08-19T00:00:00Z
updated: 2026-08-19T00:00:00Z
---

## Current Test

number: 1
name: Merge Report table renders correctly in the 760px Upload Results modal
expected: |
  Table renders BambooHR ID / Agent / Field / BambooHR value / Sheet value / Outcome
  columns inside the 760px modal; amber "BambooHR override" pill and blue "Gap-filled by
  spreadsheet" pill render as specified; long job-title/email values wrap rather than
  overflow the modal (UI-SPEC E1/long-text, backstop).
awaiting: user response

## Tests

### 1. Merge Report table renders correctly in the 760px Upload Results modal
expected: Upload a spreadsheet that overrides several identity fields and inspect the Merge Report table in the browser (not just the JSON payload). Table renders BambooHR ID / Agent / Field / BambooHR value / Sheet value / Outcome columns inside the 760px modal; amber "BambooHR override" pill and blue "Gap-filled by spreadsheet" pill render as specified; long job-title/email values wrap rather than overflow the modal (UI-SPEC E1/long-text, backstop).
result: [pending]

### 2. Eligibility callout renders and wraps long agent names
expected: Upload a sheet supplying a full week for an agent with a pathologically long name, whose working pattern BambooHR does not know. Green "Newly eligible for scheduling" callout renders above the Merge Report table (not merged into it), and the long agent name wraps inside the 760px modal rather than overflowing (UI-SPEC E2/long-text, backstop).
result: [pending]

### 3. Sync-failure toast shows the full MRG-07 message without truncation
expected: Trigger a BambooHR sync failure during upload (e.g. force a 503/timeout) and observe the toast. Toast renders the full lengthened MRG-07 message ("BambooHR sync failed (...) — no changes were made. Retry the upload once BambooHR is available.") without truncating the load-bearing "no changes were made" clause or overflowing the viewport. Toast.tsx has a 400px maxWidth and no explicit wrapping rule was verified against this message length (UI-SPEC E4, backstop).
result: [pending]

### 4. Concurrent-upload isolation and non-ASCII round-tripping
expected: Run two uploads concurrently against workbooks that touch the same desk (MRG-04/concurrency, backstop), and separately verify non-ASCII BambooHR values (e.g. a Georgian agent name) round-trip through the merge report and the sync-failure message without mojibake (MRG-07/encoding, backstop). Each upload returns its own merge report with no cross-contamination; non-ASCII text renders correctly end-to-end. Both are declared `verification: backstop` in the plan frontmatter and are not exercised by the unit suite, whose fixtures are all ASCII and sequential.
result: [pending]

### 5. Live BambooHR field alias for custom field 4517
expected: Confirm against a live BambooHR account (Company Settings → Field Alias) that custom field 4517 is actually returned under the JSON key `customWorkingdays` by the `/reports/custom` response, and that the key is populated for real employees whose working-days pattern is set in BambooHR. Code review IN-03: the request asks for field id `4517` but the parser reads back the key `customWorkingdays`. If no field alias exists on the tenant's account this value is always null in production, meaning MRG-03's window arbitration and MRG-06's gap-fill/replace reporting would silently never activate for BambooHR-sourced patterns — even though every unit test passes, because those hand-construct `BambooEmployee` fixtures. Pre-existing code, but Phase 11 makes it far more load-bearing.
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
