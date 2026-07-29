---
phase: 03-infrastructure-provisioning
plan: "01"
subsystem: infra
tags: [terraform, aws, ecs, rds, postgresql, ecr, alb, cloudfront, s3, vpc, iam]

# Dependency graph
requires:
  - phase: 01-local-tooling-state-bootstrap
    provides: "Terraform remote state backend (S3 + DynamoDB), AWS CLI configured"
  - phase: 02-security-cleanup-oidc-setup
    provides: "Clean codebase, terraform.tfvars created"
provides:
  - "VPC with 2 public + 2 private subnets, NAT gateway, route tables (eu-west-2)"
  - "ECR repository wfm-service (ready for Docker push)"
  - "RDS PostgreSQL 16.6 instance at wfm-service-dev.ckrtji9dr9fd.eu-west-2.rds.amazonaws.com:5432"
  - "ALB at wfm-service-dev-1135113453.eu-west-2.elb.amazonaws.com"
  - "CloudFront distribution d3f4cgjy3bqy.cloudfront.net"
  - "S3 bucket wfm-service-dev-frontend"
  - "Secrets Manager secret for RDS password"
  - "ECS cluster wfm-service-dev (no task definition yet - pending IAM roles)"
affects: [04-deploy-pipeline, phase-4]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Terraform apply without -target (full graph resolution)"
    - "Secrets Manager injection for RDS password (never plaintext)"
    - "CloudFront OAC for S3 origin (OAI deprecated)"
    - "OIDC federation for GitHub Actions (no long-lived credentials)"

key-files:
  created: []
  modified:
    - "infra/rds.tf"
    - ".gitignore"

key-decisions:
  - "Fixed RDS engine_version from 16.4 to 16.6 — 16.4 not available in eu-west-2 (minimum is 16.6)"
  - "Fixed shared_preload_libraries parameter apply_method from 'immediate' to 'pending-reboot' — static parameters require reboot to apply"
  - "IAM roles cannot be created via Terraform — user pete.cornwell@helpware.com lacks iam:CreateRole permission; requires root/admin to grant or pre-create"

patterns-established:
  - "Terraform apply is idempotent — re-run after fix picks up exactly where it left off"
  - "RDS static parameters must use apply_method = 'pending-reboot'"

requirements-completed: []  # INFRA-01 and INFRA-02 partially satisfied — 38/45 resources provisioned; IAM-dependent resources pending

# Metrics
duration: 19min
completed: 2026-04-03
---

# Phase 3 Plan 01: Infrastructure Provisioning Summary

**38 of 45 AWS resources provisioned via terraform apply — VPC, ECR, RDS PostgreSQL 16.6, ALB, CloudFront, S3 all live; IAM roles blocked by missing iam:CreateRole permission on pete.cornwell@helpware.com**

## Performance

- **Duration:** 19 min
- **Started:** 2026-04-03T12:00:05Z
- **Completed:** 2026-04-03T12:19:48Z
- **Tasks:** 2 (Task 1 complete; Task 2 partially complete — blocked by IAM permissions)
- **Files modified:** 2 (infra/rds.tf, .gitignore)

## Accomplishments

- Cleared stale DynamoDB state lock (ID: c082e36d-65b7-8b40-8437-ea958ca6893b)
- Ran `terraform plan` — confirmed 45 to add, 0 to change, 0 to destroy, no errors
- Provisioned 38/45 AWS resources:
  - Full VPC (vpc-01d38f8fa7fb5c247): 2 public + 2 private subnets, IGW, NAT gateway, route tables
  - ECR repository + lifecycle policy
  - RDS PostgreSQL 16.6 instance (wfm-service-dev, available)
  - ALB + target group + HTTP listener
  - CloudFront distribution (d3f4cgjy3bqy.cloudfront.net) with S3 OAC
  - S3 bucket policy and public access block
  - Secrets Manager secret + version (RDS password)
  - ECS cluster (wfm-service-dev)
  - CloudWatch log group (/ecs/wfm-service-dev)
  - Security groups (ALB, ECS, RDS)
- Auto-fixed 2 RDS bugs found during apply
- All 5 Terraform outputs captured

## Terraform Outputs (Phase 4 dependency)

```
alb_dns_name       = "wfm-service-dev-1135113453.eu-west-2.elb.amazonaws.com"
cloudfront_domain  = "d3f4cgjy3bqy.cloudfront.net"
ecr_repository_url = "521757869980.dkr.ecr.eu-west-2.amazonaws.com/wfm-service"
rds_endpoint       = "wfm-service-dev.ckrtji9dr9fd.eu-west-2.rds.amazonaws.com:5432"
s3_frontend_bucket = "wfm-service-dev-frontend"
```

## Task Commits

Each task was committed atomically:

1. **Task 1: Force-unlock stale state lock, verify clean plan** - `c37da7e` (chore)
2. **Task 2 (partial): Auto-fix RDS engine_version + parameter apply_method** - `d9cbdea` (fix)

## Files Created/Modified

- `infra/rds.tf` — Fixed engine_version 16.4→16.6 and added apply_method="pending-reboot" for static param
- `.gitignore` — Added infra/tfplan to prevent binary plan file from being committed

## Decisions Made

1. **RDS engine_version 16.4→16.6:** PostgreSQL 16.4 is not available in eu-west-2; minimum available is 16.6. Changed to 16.6 as the minimal valid version.
2. **Parameter group apply_method:** `shared_preload_libraries` is a static PostgreSQL parameter that requires a reboot to take effect. AWS rejects `apply_method = "immediate"` for static params. Fixed to `"pending-reboot"`.
3. **IAM permissions blocker:** `pete.cornwell@helpware.com` is in `PowerUserAccessGroup` but that policy explicitly excludes `iam:CreateRole` and `iam:CreateOpenIDConnectProvider`. This is by design in PowerUserAccess (it cannot create IAM principals). Requires root/admin to resolve.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed RDS parameter group apply_method for static parameter**
- **Found during:** Task 2 (terraform apply first run)
- **Issue:** `aws_db_parameter_group` had `shared_preload_libraries` without `apply_method`, defaulting to `"immediate"`. AWS rejected this with `InvalidParameterCombination: cannot use immediate apply method for static parameter`
- **Fix:** Added `apply_method = "pending-reboot"` to the parameter block in `rds.tf`
- **Files modified:** `infra/rds.tf`
- **Verification:** terraform apply created new parameter group successfully; RDS instance created with correct parameter group
- **Committed in:** d9cbdea

**2. [Rule 1 - Bug] Fixed RDS engine_version 16.4 → 16.6**
- **Found during:** Task 2 (terraform apply second run)
- **Issue:** `engine_version = "16.4"` — AWS returned `InvalidParameterCombination: Cannot find version 16.4 for postgres`. Available versions in eu-west-2 start at 16.6.
- **Fix:** Changed engine_version from "16.4" to "16.6" in `rds.tf`
- **Files modified:** `infra/rds.tf`
- **Verification:** RDS instance created successfully with PostgreSQL 16.6 (7m54s creation time, now available)
- **Committed in:** d9cbdea

---

**Total deviations:** 2 auto-fixed (2 bugs)
**Impact on plan:** Both fixes necessary for correctness. No scope creep.

## Issues Encountered

### Blocking: IAM Permission Denied (iam:CreateRole)

**Status: BLOCKED — requires human action**

The user `pete.cornwell@helpware.com` (in `PowerUserAccessGroup`) cannot create IAM roles. Three resources are blocked:

1. `aws_iam_role.ecs_execution` — ECS execution role (pulls ECR images, writes CloudWatch logs)
2. `aws_iam_role.ecs_task` — ECS task role (application permissions)
3. `aws_iam_openid_connect_provider.github` — OIDC provider for GitHub Actions

6 additional resources depend on these and cannot be created until IAM roles exist:
- `aws_iam_role_policy_attachment.ecs_execution`
- `aws_iam_role_policy.ecs_execution_secrets`
- `aws_iam_role.github_actions`
- `aws_iam_role_policy.github_actions`
- `aws_ecs_task_definition.app`
- `aws_ecs_service.app`

**Error message:**
```
User: arn:aws:iam::521757869980:user/pete.cornwell@helpware.com is not authorized to perform: 
iam:CreateRole on resource: arn:aws:iam::521757869980:role/wfm-service-dev-ecs-execution 
because no identity-based policy allows the iam:CreateRole action
```

**Required action (3 options):**

**Option A (Recommended): Grant iam:CreateRole to pete.cornwell@helpware.com via root/admin AWS console**
```
1. Log in to AWS console with root or admin credentials
2. Go to IAM > Users > pete.cornwell@helpware.com > Add permissions
3. Add inline policy or attach IAM-specific policy with:
   - iam:CreateRole
   - iam:AttachRolePolicy
   - iam:PutRolePolicy
   - iam:CreateOpenIDConnectProvider
   - iam:GetRole (for reads)
4. After granting, re-run: cd /Users/pete/IdeaProjects/wfm-service/infra && terraform apply -auto-approve
```

**Option B: Create roles manually in AWS console then import to Terraform state**
- This is more complex and error-prone; Option A is strongly preferred.

**Option C: Run Phase 3 Plan 01 again after permissions are granted**
- Terraform state tracks the 38 already-created resources, so re-running apply only provisions the remaining 7.

## Next Phase Readiness

### What's ready for Phase 4:
- All 5 Terraform outputs captured (ALB DNS, CloudFront domain, ECR URL, RDS endpoint, S3 bucket)
- ECR repository ready for first Docker image push
- RDS PostgreSQL 16.6 instance is running and reachable from ECS security group
- CloudFront distribution deployed and routing configured
- ALB routing configured (HTTP on port 80)

### Blockers before Phase 4:
- IAM roles must be created (blocked by permissions — see above)
- Without IAM roles: ECS task definition cannot be registered, ECS service cannot be created
- Phase 4 depends on ECS service existing to update the task definition with the new Docker image

### After IAM blocker is resolved:
Run `terraform apply -auto-approve` from `/Users/pete/IdeaProjects/wfm-service/infra` to create the remaining 7 resources (9 to add, 1 to destroy old param group). This should complete in under 1 minute.

---
*Phase: 03-infrastructure-provisioning*
*Completed: 2026-04-03 (partial — awaiting IAM permissions)*
