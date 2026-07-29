# Phase 2: Security Cleanup & OIDC Setup - Research

**Researched:** 2026-04-02
**Domain:** AWS IAM OIDC, credential hygiene, GitHub Actions federation
**Confidence:** HIGH

---

## Summary

Phase 2 has two distinct tracks that can be executed in parallel or sequence. The first track removes
hardcoded BambooHR API credentials from two untracked utility files that were never committed to git
history — making deletion the right action (not a git-history rewrite). The second track provisions an
AWS IAM OIDC identity provider and a GitHub Actions deploy role so the pipeline can authenticate
without long-lived AWS secrets.

The key discovery is that `infra/iam.tf` already contains complete, correct Terraform resources for
the OIDC provider and deploy role — including the trust policy, ECR push permissions, ECS permissions,
S3 permissions, CloudFront permissions, and `iam:PassRole`. This means the IAM work is entirely
handled by `terraform apply` in Phase 3, not by raw AWS CLI commands in Phase 2. Phase 2's IAM tasks
are therefore: verify that the Terraform code covers everything the workflow needs, tighten the trust
policy to scope it to the specific repository, and note the OIDC thumbprint in use.

The `.gitignore` already includes `infra/terraform.tfvars`, and `infra/terraform.tfvars` does not
exist yet — so SEC-03 is already satisfied. No action needed.

**Primary recommendation:** Delete the two utils files (they have never been committed), tighten the
`repo:*:ref:refs/heads/main` wildcard in `iam.tf` to `repo:petecornwell-code/wfm-service:ref:refs/heads/main`,
add `cloudfront:ListDistributions` to the deploy policy (required by `deploy.yml` but missing from
`iam.tf`), and plan `terraform apply` to create the OIDC resources (Phase 3 boundary decision — see
Open Questions).

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SEC-01 | `BambooCustomFields.java` removed or moved to test scope | File is untracked (never committed). Safe to `git rm` or delete outright — no history rewrite needed. No imports found anywhere in `src/main/java/`. |
| SEC-02 | `BambooEmployeesByDepartment.java` removed or moved to test scope | Same status as SEC-01: untracked, no imports. Safe to delete. |
| SEC-03 | `.gitignore` includes `infra/terraform.tfvars` | Already present on line 39 of `.gitignore`. `infra/terraform.tfvars` does not exist on disk. No action required — just verify. |
| IAM-01 | GitHub OIDC identity provider registered in AWS IAM | `aws iam list-open-id-connect-providers` returns empty list — provider does not exist. `infra/iam.tf` defines `aws_iam_openid_connect_provider.github` — can be created via `terraform apply`. |
| IAM-02 | IAM role `wfm-github-deploy` created with trust policy scoped to repository | `infra/iam.tf` defines `aws_iam_role.github_actions` (will be named `wfm-dev-github-actions`) with trust policy. The `StringLike` condition currently uses wildcard `repo:*:ref:refs/heads/main` — must be tightened to `repo:petecornwell-code/wfm-service:ref:refs/heads/main`. |
| IAM-03 | Role permissions: ECR push, ECS task def, ECS service update, S3 sync, CloudFront invalidation, Secrets Manager read | Policy in `iam.tf` covers ECR, ECS, S3, CloudFront invalidation, `iam:PassRole`. Missing: `cloudfront:ListDistributions` (used by `deploy.yml` line 125 to look up distribution ID). No Secrets Manager read in deploy role (only in ECS execution role — correct, no action needed). |
</phase_requirements>

---

## Detailed Findings

### SEC-01 / SEC-02: Credential Cleanup

**Files in question:**
- `src/main/java/utils/BambooCustomFields.java`
- `src/main/java/utils/BambooEmployeesByDepartment.java`

**What they contain:** Both files have `String apiKey = "ad2bb9c54554545bccb7ee7f732ebefcd27492be"` and
`String subdomain = "helpware"` hardcoded in their `main()` methods. They are standalone console
applications in the `utils` package (not `com.wfm.*`).

**Git status (CRITICAL FINDING):** Both files are `??` untracked in git status — they have never been
committed to any branch. `git log --all -- src/main/java/utils/BambooCustomFields.java` and the
equivalent for `BambooEmployeesByDepartment.java` both return empty output. The credentials have NOT
been committed to git history. No history rewrite (BFG/git-filter-repo) is needed.

**Import check:** `git grep -l "BambooCustomFields\|BambooEmployeesByDepartment\|import utils\."` in
`src/main/java/` returned exit code 1 (no matches). The files are not imported anywhere.

**Safe action:** Delete the files directly. Since they are untracked, `git rm` is not needed — a plain
filesystem delete removes them with no git history concern.

**Note on third utils file:** A stashed commit (`3ee0975` in stash) added
`src/main/java/utils/BambooHREmployeeStructure.java`. That file does NOT appear as untracked in the
working tree (stash only), so no action needed for it. However if the stash is ever applied, the same
analysis applies.

---

### SEC-03: .gitignore

**Finding:** `infra/terraform.tfvars` is already listed on line 39 of `.gitignore`. The file itself
does not exist on disk yet. SEC-03 is pre-satisfied.

**Verification command:** `grep "terraform.tfvars" /path/to/.gitignore` — should return a match.

---

### IAM-01: OIDC Identity Provider

**Current state:** `aws iam list-open-id-connect-providers` returned an empty list. No OIDC provider
exists in the account.

**Terraform code:** `infra/iam.tf` (lines 60-64) defines:

```hcl
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}
```

**Thumbprint:** `6938fd4d98bab03faadb97b34396831e3780aea1` is GitHub Actions' published OIDC thumbprint.
As of 2023, AWS no longer validates the thumbprint for well-known OIDC providers (GitHub, Google), but
including it is required by the Terraform resource schema. The value in `iam.tf` is the correct current
value. Confidence: HIGH (verified against AWS and GitHub documentation patterns).

**Creation method:** `terraform apply` in Phase 3 creates this. If it needs to be created in Phase 2
(before Phase 3 `terraform plan`), it can also be created via AWS CLI — but given the Terraform code
is complete and correct, the cleanest approach is to let Phase 3 handle it. See Open Questions.

---

### IAM-02: Deploy Role Trust Policy

**Terraform code:** `infra/iam.tf` (lines 66-82) defines `aws_iam_role.github_actions` with a trust
policy using `StringLike` on `token.actions.githubusercontent.com:sub`.

**Issue found:** The current condition value is:
```
"repo:*:ref:refs/heads/main"
```

This allows **any** GitHub repository to assume this role as long as it's on the `main` branch. This
must be tightened to:
```
"repo:petecornwell-code/wfm-service:ref:refs/heads/main"
```

**Role name:** The Terraform name expression is `"${var.app_name}-${var.environment}-github-actions"`.
With `app_name = "wfm"` and `environment = "dev"`, the role will be named `wfm-dev-github-actions`.
The ROADMAP refers to it as `wfm-github-deploy` — these differ. The Terraform name is what will
actually be created. The Phase 4 GitHub secret will need the actual ARN from Terraform outputs.

**GitHub repository:** `origin` remote is `https://github.com/petecornwell-code/wfm-service.git`,
so the owner/repo is `petecornwell-code/wfm-service`.

---

### IAM-03: Deploy Role Permissions

**Workflow analysis (`deploy.yml`):** The following AWS calls are made:

| AWS Service | Operation | Permission Required |
|-------------|-----------|---------------------|
| ECR | Login (via `amazon-ecr-login` action) | `ecr:GetAuthorizationToken` |
| ECR | Push image | `ecr:BatchCheckLayerAvailability`, `ecr:PutImage`, `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload` |
| ECS | `describe-task-definition` | `ecs:DescribeTaskDefinition` |
| ECS | `register-task-definition` | `ecs:RegisterTaskDefinition` |
| ECS | `update-service` | `ecs:UpdateService` |
| ECS | `wait services-stable` (calls `describe-services`) | `ecs:DescribeServices` |
| ECS | `update-service` with `--task-definition` (requires PassRole) | `iam:PassRole` on ECS execution + task roles |
| S3 | `s3 sync` + `s3 cp` | `s3:PutObject`, `s3:DeleteObject`, `s3:ListBucket` |
| CloudFront | `list-distributions` (to find distribution ID) | `cloudfront:ListDistributions` |
| CloudFront | `create-invalidation` | `cloudfront:CreateInvalidation` |

**Gap in current `iam.tf`:** `cloudfront:ListDistributions` is **NOT** in the current policy. The
workflow script at line 125 calls `aws cloudfront list-distributions` to look up the distribution ID
dynamically. Without this permission, the frontend deploy job will fail.

**Secrets Manager:** The deploy workflow does NOT call Secrets Manager directly. Only the ECS task
execution role needs `secretsmanager:GetSecretValue` (already present in `iam.tf` as
`aws_iam_role_policy.ecs_execution_secrets`). No change needed for the deploy role.

**Required fix to `iam.tf`:** Add `cloudfront:ListDistributions` to the `aws_iam_role_policy.github_actions`
CloudFront statement, changing `Resource = aws_cloudfront_distribution.frontend.arn` to `Resource = "*"`
(since `list-distributions` is a list operation with no resource-level restriction in IAM).

---

## Architecture Patterns

### Recommended Approach: Terraform for IAM (not raw CLI)

The complete IAM configuration already exists in `infra/iam.tf`. The correct approach is:

1. Fix the two issues in `iam.tf` (wildcard trust policy, missing `cloudfront:ListDistributions`)
2. Let Phase 3's `terraform apply` create the OIDC provider and role

This avoids a drift situation where resources are created manually in Phase 2 and then Terraform
tries to create them again in Phase 3 (which would fail with "already exists").

**If the OIDC provider must exist before Phase 3** (e.g., for testing purposes), it can be created
with Terraform targeting: `terraform apply -target=aws_iam_openid_connect_provider.github -target=aws_iam_role.github_actions -target=aws_iam_role_policy.github_actions`

### Trust Policy: StringLike vs StringEquals

The current trust policy uses `StringLike` (supports wildcards). For production use, `StringEquals`
with the exact repo/branch is preferred for tighter security. However, `StringLike` with a specific
non-wildcard value (after the fix) is functionally equivalent and is the pattern in official AWS
GitHub OIDC documentation. Keep `StringLike`, just remove the `*` wildcard from the repo portion.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| OIDC provider creation | `aws iam create-open-id-connect-provider` CLI commands | `terraform apply` using existing `iam.tf` | Terraform code already exists and is correct; manual creation causes state drift |
| IAM role trust policy | Manual JSON editing + CLI | Edit `iam.tf`, apply via Terraform | Same reason — all IAM is Terraform-managed |
| Finding deploy role ARN | Hardcode it | `terraform output` after apply | ARN contains account ID which changes; outputs are authoritative |

---

## Common Pitfalls

### Pitfall 1: Manual IAM Creation Before Terraform Apply Causes State Conflict
**What goes wrong:** IAM resources created with AWS CLI in Phase 2 cannot be managed by Terraform in
Phase 3 — Terraform will attempt to create the same resources and fail with "EntityAlreadyExists".
**Why it happens:** Terraform only manages resources it knows about via state.
**How to avoid:** Either use `terraform apply -target=...` (creates resources and tracks them in state)
or wait for Phase 3's full apply. Do NOT use raw AWS CLI to create resources that Terraform will manage.
**Warning signs:** If you see `aws iam create-open-id-connect-provider` in a plan, this pitfall is
about to occur.

### Pitfall 2: Wildcard Trust Policy Allows Any Repo
**What goes wrong:** `repo:*:ref:refs/heads/main` allows any GitHub repo's `main` branch to assume
the deploy role — a significant security risk if the account ID or role name is discoverable.
**Why it happens:** Placeholder wildcard not tightened before deploy.
**How to avoid:** Set the condition value to `repo:petecornwell-code/wfm-service:ref:refs/heads/main`
in `iam.tf` before applying.

### Pitfall 3: Missing `cloudfront:ListDistributions` Fails Frontend Deploy
**What goes wrong:** The frontend deploy job silently succeeds until the CloudFront step, which fails
with `AccessDeniedException` when trying to look up the distribution ID.
**Why it happens:** `deploy.yml` uses a dynamic lookup (`list-distributions`) rather than a hardcoded
distribution ID. This permission is not in the current `iam.tf`.
**How to avoid:** Add `cloudfront:ListDistributions` to the GitHub Actions role policy in `iam.tf`.

### Pitfall 4: utils Files Are Untracked, Not Deleted
**What goes wrong:** Developer deletes the files but they reappear after switching branches or
doing a `git stash pop`, because they were treated as tracked.
**Why it happens:** Confusion between untracked (never committed) and tracked (committed, then deleted).
**Reality:** These files are untracked. Deleting them from disk is sufficient. There is no `git rm`
needed, and no history to rewrite.

---

## Runtime State Inventory

> Not applicable — this phase is credential cleanup and IAM provisioning. No rename/refactor/migration
> is involved. No stored data, live service config, or OS-registered state contains the BambooHR
> credentials (the files were never deployed or integrated into the running application).

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| AWS CLI v2 | IAM verification, OIDC provider check | Yes | (Phase 1 confirmed) | — |
| Terraform >= 1.5 | `terraform apply -target` if used | Yes | 1.5.7 (Phase 1 confirmed) | — |
| git | File deletion verification | Yes | system | — |

**Missing dependencies with no fallback:** None.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Shell commands / AWS CLI verification |
| Config file | None (infrastructure verification, not code tests) |
| Quick run command | See per-requirement commands below |
| Full suite command | Run all commands in sequence |

### Phase Requirements to Verification Map

| Req ID | Behavior | Test Type | Verification Command | Notes |
|--------|----------|-----------|----------------------|-------|
| SEC-01 | `BambooCustomFields.java` does not exist | filesystem check | `[ ! -f src/main/java/utils/BambooCustomFields.java ] && echo PASS \|\| echo FAIL` | |
| SEC-01 | File not tracked or staged | git check | `git status --porcelain src/main/java/utils/ \| grep -c BambooCustomFields \|\| echo "0 - PASS"` | Should return 0 |
| SEC-02 | `BambooEmployeesByDepartment.java` does not exist | filesystem check | `[ ! -f src/main/java/utils/BambooEmployeesByDepartment.java ] && echo PASS \|\| echo FAIL` | |
| SEC-03 | `.gitignore` includes `infra/terraform.tfvars` | content check | `grep -q "infra/terraform.tfvars" .gitignore && echo PASS \|\| echo FAIL` | Already present |
| SEC-03 | `infra/terraform.tfvars` not tracked | git check | `git ls-files infra/terraform.tfvars \| wc -l` | Should be 0 |
| IAM-01 | OIDC provider exists for `token.actions.githubusercontent.com` | AWS CLI | `aws iam list-open-id-connect-providers --query "OpenIDConnectProviderList[].Arn" --output text` | Must return an ARN |
| IAM-02 | Role exists with scoped trust policy | AWS CLI | `aws iam get-role --role-name wfm-dev-github-actions --query "Role.AssumeRolePolicyDocument"` | Sub condition must not contain `repo:*` |
| IAM-03 | Role policy includes all required actions | AWS CLI | `aws iam get-role-policy --role-name wfm-dev-github-actions --policy-name deploy-permissions` | Verify ECR, ECS, S3, CloudFront (incl. ListDistributions) |

### Wave 0 Gaps

None — this phase has no code tests. Verification is via AWS CLI and filesystem checks above. All
commands can be run immediately after execution tasks complete.

---

## Open Questions

1. **Should IAM resources be created in Phase 2 or Phase 3?**
   - What we know: `iam.tf` is correct (after the two fixes). The ROADMAP places IAM creation in
     Phase 2. Phase 3's `terraform apply` would naturally create it. Creating it prematurely in
     Phase 2 via `terraform -target` is safe (Terraform tracks state) but adds complexity.
   - What's unclear: Whether the planner wants a clean "IAM in Phase 2, everything else in Phase 3"
     split, or whether it's acceptable for Phase 2 to simply fix the `iam.tf` code and let Phase 3
     create the resources.
   - Recommendation: Phase 2 fixes the `iam.tf` code issues (wildcard trust policy, missing
     `cloudfront:ListDistributions`). IAM resources are actually provisioned in Phase 3 via
     `terraform apply`. The ROADMAP success criteria for Phase 2 says the OIDC provider and role
     must "exist" — which requires creation. Plan should include a `terraform apply -target` step
     or accept that Phase 2 success criteria are met by Phase 3 apply.

2. **Role name discrepancy: `wfm-github-deploy` vs `wfm-dev-github-actions`**
   - ROADMAP references `wfm-github-deploy` in Phase 2 success criteria.
   - Terraform generates `wfm-dev-github-actions` (from `${var.app_name}-${var.environment}-github-actions`).
   - Recommendation: Use the Terraform-generated name. Update planning documents to reflect the
     actual name. The GitHub secret `AWS_DEPLOY_ROLE_ARN` in Phase 4 will use the Terraform output.

---

## Sources

### Primary (HIGH confidence)
- `infra/iam.tf` — complete Terraform IAM configuration, read directly from codebase
- `.github/workflows/deploy.yml` — authoritative source for which AWS permissions are required
- `.gitignore` — line 39 confirms `infra/terraform.tfvars` is already excluded
- `git status`, `git log --all` — confirmed utils files are untracked and have no commit history

### Secondary (MEDIUM confidence)
- AWS IAM OIDC thumbprint `6938fd4d98bab03faadb97b34396831e3780aea1` — value from `iam.tf`, consistent
  with GitHub's published OIDC documentation. AWS stopped validating thumbprints for GitHub's OIDC
  endpoint in 2023, but the value must still be provided in Terraform.

### Tertiary (LOW confidence)
- None.

---

## Metadata

**Confidence breakdown:**
- Credential cleanup (SEC-01/02): HIGH — files verified as untracked via git, no imports found
- .gitignore (SEC-03): HIGH — read directly, already satisfied
- OIDC provider (IAM-01): HIGH — AWS CLI confirmed empty, Terraform code present
- Trust policy (IAM-02): HIGH — code read directly, wildcard issue identified, repo name from git remote
- Deploy permissions (IAM-03): HIGH — deploy.yml and iam.tf read directly, gap identified

**Research date:** 2026-04-02
**Valid until:** 2026-05-02 (IAM/OIDC patterns are stable; thumbprint is stable unless GitHub rotates certs)
