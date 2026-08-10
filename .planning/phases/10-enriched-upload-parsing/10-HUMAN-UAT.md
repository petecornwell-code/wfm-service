---
status: testing
phase: 10-enriched-upload-parsing
source: [10-VERIFICATION.md]
started: 2026-07-31
updated: 2026-08-10
---

## Current Test

number: 1
name: Template download + Upload Results modal
expected: |
  See Tests section below.
awaiting: user response

## Tests

### 1. Template download + Upload Results modal
expected: Clicking "Download template" downloads a workbook with one worksheet per desk, each pre-seeded with the current roster's identity columns (BambooHR ID, first/last name, job title, email, department, active) and the 7 Mon–Sun day cells + Specialty columns left blank. After uploading a mixed-validity workbook, the Upload Results modal renders: a per-sheet rollup (e.g. "Billing: 12 imported, 2 skipped"), per-row skip reasons, non-blocking amber clamp warnings (>24 → 24), and unmatched-sheet notices.
result: [pending]
note: Expectation revised 2026-08-10 — seeding is now restricted to active agents (and, when an allowlist is configured, to matching job titles). Original wording said "the current roster" unconditionally.

### 2. Job Title Allowlist configuration UI
expected: Configuration page shows a "Job Title Allowlist" section. While empty it displays an amber warning that the allowlist is inactive and every job title is included. Typing "Customer Support Representative" and clicking Add (or pressing Enter) adds it to the list; the warning disappears. Remove deletes it and the warning returns. Re-adding the same text does not create a duplicate row.
result: [pending]

### 3. Allowlist + active filtering in the template
expected: With "Customer Support Representative" configured, downloading the template seeds only active agents whose job title contains that phrase — including variants like "Senior Customer Support Representative". Inactive agents and other titles (e.g. "Team Lead") are absent, with no blank gaps left mid-roster.
result: [pending]

### 4. Allowlist + active enforcement on upload
expected: Uploading a workbook containing an inactive agent and a non-allowlisted agent imports neither. The Upload Results modal reports each with its own reason — "Agent is not active" and "Agent job title is not in the configured allowlist: Team Lead" — while a valid active, allowlisted agent on the same sheet still imports.
result: [pending]

### 5. Empty allowlist is inactive (backwards compatibility)
expected: With all allowlist entries removed, template seeding and upload behave exactly as before the change — every job title is included, and only the active filter applies.
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
