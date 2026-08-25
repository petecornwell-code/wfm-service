---
status: complete
phase: 10-enriched-upload-parsing
source: [10-VERIFICATION.md]
started: 2026-07-31
updated: 2026-08-21
---

## Current Test

[testing complete]

<!--
Rewritten 2026-08-12. The original single test described a template "pre-seeded with the current
roster" and a separate non-schedulable checkbox list. Neither is accurate any more: the job-title
allowlist became the SINGLE control for schedulability (solver, desk assignment, upload, template),
the checkbox UI was removed, and V32 seeded the allowlist with "Customer Support Representative".
Tests below describe what is actually deployed.

Live: https://d2bbtcc80peap7.cloudfront.net
Current state: 24 of 836 synced job titles match the seeded pattern.
-->

## Tests

### 1. Schedulable Job Titles configuration UI
expected: Configuration page shows a single "Schedulable Job Titles" section containing one entry, "Customer Support Representative" (seeded). Typing a new entry and clicking Add (or pressing Enter) adds it to the list. Re-adding the same text does not create a duplicate. Remove deletes an entry. Removing the last entry shows an amber warning that the restriction is inactive and every job title is currently schedulable. There is no checkbox list anywhere on the page.
result: pass

### 2. Template contains only schedulable agents
expected: On Client Management, "Download template" produces a workbook with one worksheet per desk. Each sheet is pre-seeded ONLY with agents who are active AND whose job title contains "Customer Support Representative" — including variants such as "Senior Customer Support Representative", "Customer Support Representative (German)" and "Customer Support Representative Tier 2". Inactive agents and unrelated titles (e.g. "Accountant", "Team Lead") are absent, with no blank gaps mid-roster. Identity columns are filled; the 7 Mon–Sun day cells and Specialty columns are blank.
result: pass

### 3. Upload Results modal — rollup, skip reasons, clamp warnings
expected: Uploading a mixed-validity workbook opens the Upload Results modal showing a per-sheet rollup (e.g. "Billing: 12 imported, 2 skipped"), a per-row reason for each skipped row, non-blocking amber clamp warnings where a day cell above 24 was clamped to 24, and a notice for any sheet whose name matches no configured desk.
result: pass

### 4. Upload enforces active + schedulable
expected: A workbook containing an inactive agent and an agent whose title is not schedulable imports neither. The modal reports each with its own reason — "Agent is not active" and "Agent job title is not schedulable: <title>" — while a valid active, schedulable agent on the same sheet still imports.
result: pass

### 5. Day cell values behave as documented
expected: A day cell accepts a number 0–24 (fractions preserved, e.g. 7.5), or the words MANDATORY or PTO (case-insensitive, both stored as 0 hours). A value above 24 clamps to 24 and raises an amber warning but still imports. A blank cell, a negative number, or any other word skips the WHOLE row with a specific reason naming the day and the problem.
result: pass

### 6. Solver only considers schedulable agents
expected: Building a schedule for a desk considers only agents who are active, have a job title on the allowlist, have a primary specialization, and have known working days. Agents excluded solely by job title no longer appear in the resulting schedule. (This is a behaviour change as of 2026-08-12 — desks staffed by non-CSR titles will now find no eligible agents.)
result: pass

### 7. Desk assignment blocked for non-schedulable titles
expected: On Client Management, assigning an employee whose job title is not on the allowlist is rejected with a clear message naming the agent and the title ("... has a job title that is not schedulable: <title>"), rather than silently assigning them.
result: pass

### 8. Empty allowlist falls back to permitting everything
expected: With every allowlist entry removed, the amber warning appears and behaviour reverts to pre-restriction: all job titles are schedulable, the template seeds all active agents, and upload accepts any active agent. (Deliberate fail-open, so the system cannot be locked into a state where nothing is schedulable. Re-add "Customer Support Representative" afterwards.)
result: pass

## Summary

total: 8
passed: 8
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

<!--
Resolved during this UAT session (2026-08-12), recorded for traceability. These were found while
diagnosing a single failing checkbox click and are all fixed and verified live:

- gap_id: G-10-A
  truth: "API errors reach the browser with their real status code"
  status: resolved
  reason: "CloudFront custom_error_response rewrote every API 403/404 into 200 + index.html, so
    the client failed at response.json() with 'not valid JSON' and the real status was lost.
    Distribution-wide setting that could not be scoped to a cache behavior."
  resolved_by: "bc4f1dc — replaced with a viewer-request CloudFront Function on the S3 behavior only"

- gap_id: G-10-B
  truth: "The job title toggle works from a browser"
  status: resolved
  reason: "CorsConfig.allowedMethods omitted PATCH. Browsers send Origin on same-origin non-GET
    requests, so Spring rejected every PATCH with 403. Invisible to curl, which sends no Origin.
    The feature had never worked from the UI."
  resolved_by: "8e802c1 — added PATCH and HEAD, plus a regression test"

- gap_id: G-10-C
  truth: "terraform apply does not damage running infrastructure"
  status: resolved
  reason: "State held ECS task definition revision 1 while live ran 16, and the config image tag
    ':latest' is never pushed by CI — applying would have taken the site down. RDS had auto-upgraded
    16.6 -> 16.13, so Terraform proposed an impossible downgrade."
  resolved_by: "a841c59 — ignore_changes on the ECS service, RDS pinned to 16.13"
-->
