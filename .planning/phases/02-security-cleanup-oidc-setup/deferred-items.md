# Deferred Items — Phase 02

## Pre-existing Security Issue (Out of Scope for 02-01)

**File:** `src/main/resources/sample-data/pete.cornwell@helpware.com_accessKeys.csv`

**Issue:** AWS IAM access key CSV file (with key ID AKIAXS6ZRI6OIYJEOT5L) is sitting untracked in the working tree under `src/main/resources/sample-data/`. This file was not introduced by plan 02-01 tasks and is pre-existing.

**Risk:** If this file is ever staged and committed, live AWS credentials would be pushed to the repository.

**Recommended Action:**
1. Delete the file from disk: `rm src/main/resources/sample-data/pete.cornwell@helpware.com_accessKeys.csv`
2. Add a gitignore pattern to prevent accidental commit: `src/main/resources/sample-data/*accessKeys*.csv`
3. Rotate/invalidate the access key in AWS IAM if it is still active.

**Discovered during:** 02-01 post-task git status check (2026-04-03)
