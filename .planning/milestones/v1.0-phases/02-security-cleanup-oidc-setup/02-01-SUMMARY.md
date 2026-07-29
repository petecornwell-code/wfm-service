---
phase: 02-security-cleanup-oidc-setup
plan: "01"
subsystem: security
tags: [security, cleanup, credentials, gitignore]
dependency_graph:
  requires: []
  provides: [SEC-01, SEC-02, SEC-03]
  affects: [deployment-readiness]
tech_stack:
  added: []
  patterns: []
key_files:
  created: []
  modified: []
  deleted:
    - src/main/java/utils/BambooCustomFields.java
    - src/main/java/utils/BambooEmployeesByDepartment.java
decisions:
  - "Deleted untracked files via plain rm (not git rm) — files had no git history, no staging or commit required"
  - "SEC-03 verified as pre-existing on .gitignore line 39 — no file modification needed"
metrics:
  duration: "~1 minute"
  completed: "2026-04-03"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 0
---

# Phase 02 Plan 01: Delete Hardcoded-Credential Files and Verify gitignore Summary

**One-liner:** Deleted two untracked BambooHR API key files (never committed) and confirmed terraform.tfvars is gitignored on line 39.

## What Was Done

### Task 1: Delete hardcoded-credential utility files (SEC-01, SEC-02)

Pre-execution check confirmed both files were untracked (`??` in git status) with no git history whatsoever. This matched the research finding — plain `rm` was the correct approach.

- Deleted `src/main/java/utils/BambooCustomFields.java`
- Deleted `src/main/java/utils/BambooEmployeesByDepartment.java`
- Removed now-empty `src/main/java/utils/` directory

Since the files were never committed, no `git add` or `git commit` was needed — they simply ceased to exist on the filesystem.

### Task 2: Verify .gitignore covers terraform.tfvars (SEC-03)

`grep -n "terraform.tfvars" .gitignore` returned line 39: `infra/terraform.tfvars` — exactly as documented in the plan. No modification to `.gitignore` was required.

Additional checks confirmed:
- `infra/terraform.tfvars` does not exist on disk
- `git ls-files infra/terraform.tfvars` returns 0 (not tracked)

## Verification Output

```
=== SEC-01 ===
PASS

=== SEC-02 ===
PASS

=== SEC-03 ===
infra/terraform.tfvars
PASS
```

## Deviations from Plan

None — plan executed exactly as written.

The plan correctly predicted:
- Both files were untracked with no git history
- .gitignore line 39 already contained `infra/terraform.tfvars`
- No commits were needed

## Known Stubs

None.

## Out-of-Scope Discovery (Deferred)

During post-task `git status` check, an untracked file was found:

`src/main/resources/sample-data/pete.cornwell@helpware.com_accessKeys.csv`

This file contains an AWS IAM access key and is pre-existing (not introduced by this plan). It is untracked and therefore not in git, but its presence on disk is a risk if ever accidentally staged. Logged to `deferred-items.md` for follow-up action:
1. Delete the file from disk
2. Add a gitignore pattern (e.g., `*accessKeys*.csv`)
3. Rotate the key in AWS IAM if still active

## Self-Check: PASSED

- BambooCustomFields.java: MISSING from filesystem (correct — deleted)
- BambooEmployeesByDepartment.java: MISSING from filesystem (correct — deleted)
- .gitignore line 39 contains `infra/terraform.tfvars`: CONFIRMED
- No commits made (correct — untracked files require no git operation)
