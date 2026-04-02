---
phase: 01-local-tooling-state-bootstrap
plan: 01
subsystem: infra
tags: [aws-cli, terraform, iam, aws]

# Dependency graph
requires: []
provides:
  - AWS CLI v2 installed and authenticated against account 521757869980
  - AWS CLI default profile configured for eu-west-2 region with json output
  - Terraform v1.5.7 confirmed on PATH
  - Working credentials in ~/.aws/credentials and ~/.aws/config
affects:
  - 01-02 (state backend bootstrap — needs authenticated AWS CLI)
  - 02-docker-ecr (needs AWS CLI for ECR push)
  - all subsequent infra phases

# Tech tracking
tech-stack:
  added: [aws-cli/2, terraform/1.5.7]
  patterns: [non-root IAM user for all CLI operations]

key-files:
  created: []
  modified:
    - ~/.aws/credentials
    - ~/.aws/config

key-decisions:
  - "Used existing IAM user pete.cornwell@helpware.com instead of creating pete-admin — iam:CreateUser permission not available and root credentials not accessible"
  - "AWS region set to eu-west-2 for all subsequent CLI operations"

patterns-established:
  - "Non-root IAM credentials always used for CLI access — never root account keys"

requirements-completed:
  - TOOL-01
  - TOOL-02
  - TOOL-03

# Metrics
duration: multi-session
completed: 2026-04-02
---

# Phase 01 Plan 01: Local Tooling State Bootstrap Summary

**AWS CLI v2 installed via Homebrew and authenticated against account 521757869980 in eu-west-2; Terraform v1.5.7 confirmed on PATH**

## Performance

- **Duration:** Multi-session (Tasks 1-3 in prior session, Task 4 in current session)
- **Started:** 2026-04-02
- **Completed:** 2026-04-02
- **Tasks:** 4
- **Files modified:** 2 (~/.aws/credentials, ~/.aws/config)

## Accomplishments

- AWS CLI v2 installed and verified (`aws-cli/2.x` on PATH)
- AWS CLI default profile configured with credentials for account 521757869980, region eu-west-2, output json
- `aws sts get-caller-identity` verified returning correct Account and Arn
- Terraform v1.5.7 confirmed installed and accessible on PATH (darwin_arm64)

## Task Commits

Each task was committed atomically:

1. **Task 1: Install AWS CLI v2 via Homebrew** - `9947d85` (chore)
2. **Task 2: Create IAM admin user** - human-action checkpoint; existing user pete.cornwell@helpware.com used
3. **Task 3: Configure AWS CLI credentials** - completed in prior session (no separate commit — part of bootstrap sequence)
4. **Task 4: Verify Terraform installation** - `9e3658f` (chore)

## Files Created/Modified

- `~/.aws/credentials` — default profile with aws_access_key_id and aws_secret_access_key
- `~/.aws/config` — default profile with region = eu-west-2 and output = json

## Decisions Made

- Used existing IAM user `pete.cornwell@helpware.com` rather than creating a new `pete-admin` user. The existing user lacked `iam:CreateUser` permission and root credentials were not available, making it impossible to follow the plan's Task 2 instructions exactly. The existing user has AdministratorAccess and is functionally equivalent for all subsequent phases.
- Region set to `eu-west-2` as specified — consistent with infra/main.tf and all deployment targets.

## Deviations from Plan

### Human-action Deviation

**1. [Task 2 - IAM User] Used existing user instead of creating pete-admin**
- **Found during:** Task 2 (Create IAM admin user in AWS Console)
- **Issue:** Plan required creating a new IAM user `pete-admin`, but the existing IAM user lacked `iam:CreateUser` permission. Root credentials were not available to grant that permission.
- **Resolution:** Used existing IAM user `pete.cornwell@helpware.com` which already has AdministratorAccess attached.
- **Impact on acceptance criteria:** The `Arn` field from `aws sts get-caller-identity` ends with `user/pete.cornwell@helpware.com` instead of `user/pete-admin`. All other criteria are met. Functionally equivalent for all subsequent plans.
- **Committed in:** Part of Task 3 configuration (credentials stored in ~/.aws/credentials)

---

**Total deviations:** 1 (human constraint — existing permissions prevented IAM user creation)
**Impact on plan:** No functional impact. AWS CLI is authenticated with AdministratorAccess to the correct account and region.

## Issues Encountered

- `iam:CreateUser` permission not available on the existing IAM user, preventing creation of the `pete-admin` user as specified. Resolved by using the existing user which already held AdministratorAccess.

## User Setup Required

None - AWS credentials are configured locally in ~/.aws/. No additional environment variables or dashboard steps required.

## Next Phase Readiness

- AWS CLI authenticated and functional — Plan 01-02 (state backend bootstrap: S3 + DynamoDB) can proceed immediately
- Terraform confirmed at v1.5.7 — satisfies `>= 1.5` constraint in infra/main.tf
- Known blocker remains: `src/main/java/utils/` files contain hardcoded BambooHR API key — must be removed before Phase 2 deploy

---
*Phase: 01-local-tooling-state-bootstrap*
*Completed: 2026-04-02*
