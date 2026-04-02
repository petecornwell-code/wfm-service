# Roadmap: WFM Service — AWS Deployment

## Overview

The codebase is working and the infrastructure code is already written. This roadmap covers the four sequential steps required to turn that code into a live cloud deployment: get local tooling in place and bootstrap Terraform state, create the AWS identity resources that let GitHub Actions deploy without stored credentials, provision all cloud infrastructure via Terraform, then wire up the CI/CD pipeline and verify the application is reachable. Each phase completes a self-contained gate — nothing in a later phase can succeed until the prior one is done.

## Phases

- [ ] **Phase 1: Local Tooling & State Bootstrap** - AWS CLI + Terraform installed/configured; Terraform remote state bucket and lock table created
- [ ] **Phase 2: Security Cleanup & OIDC Setup** - Hardcoded credentials removed; GitHub OIDC identity provider and deploy role provisioned in AWS IAM
- [ ] **Phase 3: Infrastructure Provisioning** - All AWS resources created via Terraform (VPC, ECS, RDS, ECR, ALB, S3, CloudFront)
- [ ] **Phase 4: CI/CD Pipeline & Go-Live** - GitHub secret set, pipeline triggered, application live and verified

## Phase Details

### Phase 1: Local Tooling & State Bootstrap
**Goal**: Pete can run AWS CLI commands and Terraform against the target account, and Terraform's remote state backend exists and is configured
**Depends on**: Nothing (first phase)
**Requirements**: TOOL-01, TOOL-02, TOOL-03, BOOT-01, BOOT-02, BOOT-03, BOOT-04
**Success Criteria** (what must be TRUE):
  1. `aws sts get-caller-identity` returns the correct AWS account ID and IAM user/role
  2. `terraform -version` reports >= 1.5 on the developer machine
  3. S3 bucket `wfm-terraform-state` exists in `eu-west-2` with versioning and server-side encryption enabled
  4. DynamoDB table `wfm-terraform-locks` exists in `eu-west-2` with `LockID` as the partition key
  5. `terraform init` inside `infra/` completes with "Successfully configured the backend" and no errors (backend block uncommented)
**Plans**: 2 plans

Plans:
- [ ] 01-01-PLAN.md — Install AWS CLI, create IAM admin user, configure credentials, verify Terraform
- [ ] 01-02-PLAN.md — Create S3 bucket + DynamoDB table, update main.tf backend block, run terraform init

**Background for planning:**
- AWS CLI v2 install: https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html (macOS: download `.pkg` or use Homebrew `brew install awscli`)
- Terraform install: https://developer.hashicorp.com/terraform/install (macOS: `brew install terraform` or download binary >= 1.5)
- AWS CLI configuration uses `aws configure` — requires Access Key ID and Secret Access Key for an IAM user, or a named profile
- The S3 bucket and DynamoDB table must be created BEFORE uncommenting the backend block in `infra/main.tf`, because Terraform needs them to exist before it can initialise against them
- The backend block to uncomment is in `infra/main.tf`; set `bucket = "wfm-terraform-state"`, `key = "wfm/dev/terraform.tfstate"`, `region = "eu-west-2"`, `dynamodb_table = "wfm-terraform-locks"`, `encrypt = true`
- Bootstrap resources can be created with the AWS CLI directly (no Terraform needed for this step)

### Phase 2: Security Cleanup & OIDC Setup
**Goal**: Hardcoded BambooHR credentials are removed from the codebase, and an AWS IAM OIDC identity provider plus deploy role exist so GitHub Actions can authenticate without stored AWS keys
**Depends on**: Phase 1
**Requirements**: SEC-01, SEC-02, SEC-03, IAM-01, IAM-02, IAM-03
**Success Criteria** (what must be TRUE):
  1. `src/main/java/utils/BambooCustomFields.java` and `BambooEmployeesByDepartment.java` are deleted or moved so they do not appear in `git diff main` as files containing secrets
  2. `infra/terraform.tfvars` is listed in `.gitignore`
  3. An OIDC identity provider for `token.actions.githubusercontent.com` exists in AWS IAM (visible in IAM console under Identity Providers)
  4. IAM role `wfm-github-deploy` exists with a trust policy scoped to the correct GitHub repository and `main` branch
  5. The role's permissions include: ECR push, ECS task definition registration, ECS service update, S3 sync, CloudFront invalidation, and Secrets Manager read
**Plans**: TBD

**Background for planning:**
- The two files in `src/main/java/utils/` are outside the main package (`com/wfm/`) and appear to be standalone utilities not imported by the main application — confirm with `git grep` before deleting
- OIDC setup in AWS: IAM console → Identity Providers → Add Provider → OpenID Connect, URL `https://token.actions.githubusercontent.com`, audience `sts.amazonaws.com`
- The trust policy for the role should use condition `StringLike` on `token.actions.githubusercontent.com:sub` with value `repo:OWNER/REPO:ref:refs/heads/main`
- Permissions can be attached as an inline policy or managed policy; the deploy workflow in `.github/workflows/deploy.yml` documents exactly which AWS calls it makes
- The IAM role ARN will be needed in Phase 4 — note it down after creation

### Phase 3: Infrastructure Provisioning
**Goal**: All AWS infrastructure resources are provisioned and healthy — ECS cluster, RDS instance, ECR repository, ALB, S3 bucket, and CloudFront distribution all exist in `eu-west-2`
**Depends on**: Phase 2
**Requirements**: INFRA-01, INFRA-02, INFRA-03, INFRA-04, INFRA-05
**Success Criteria** (what must be TRUE):
  1. `terraform plan` inside `infra/` exits 0 with a valid plan and zero errors
  2. `terraform apply` completes successfully and all resources appear in the AWS console
  3. RDS PostgreSQL instance is in `available` state; ECS tasks in the cluster can reach it on port 5432 (security group allows traffic from ECS security group to RDS security group)
  4. The database password is stored as a secret in AWS Secrets Manager and the ECS task definition references it (no plaintext password in task def)
  5. On first ECS task start, CloudWatch Logs for the task show Flyway migration output completing all 24 scripts without error
**Plans**: TBD

**Background for planning:**
- Run `terraform plan` first and read the output carefully before applying — it will show every resource that will be created
- `terraform apply` for a full environment (VPC, RDS, ECS, CloudFront) can take 15-30 minutes; RDS and CloudFront are the slowest
- If `terraform apply` fails mid-way, the state file tracks what was created — fix the error and re-run `apply`; do not run `terraform destroy` unless absolutely necessary
- Flyway migrations run automatically at Spring Boot startup — check ECS task logs in CloudWatch (log group `/ecs/wfm-*`) to confirm all 24 migrations applied
- The pgvector extension (V24) requires that the RDS instance has `pgvector` available — the Terraform RDS config should handle this; verify the extension was enabled in logs
- After `apply`, run `terraform output` to capture any output values (ALB DNS, CloudFront domain, ECR URL) — these will be referenced in Phase 4

### Phase 4: CI/CD Pipeline & Go-Live
**Goal**: The GitHub Actions deploy pipeline runs successfully on push to `main`, the application is reachable at the CloudFront URL, the API health endpoint is up, and local development continues to work
**Depends on**: Phase 3
**Requirements**: CICD-01, CICD-02, CICD-03, CICD-04, CICD-05, APP-01, APP-02, APP-03, APP-04
**Success Criteria** (what must be TRUE):
  1. GitHub secret `AWS_DEPLOY_ROLE_ARN` is set in the repository to the IAM role ARN from Phase 2
  2. A push to `main` (or manual trigger) causes the `deploy.yml` GitHub Actions workflow to pass all jobs with green checks
  3. The Docker image is visible in ECR tagged with the commit SHA
  4. The ECS service shows a running task that has passed its health check (`/actuator/health`)
  5. The CloudFront URL returns the React SPA (HTTP 200, HTML content)
  6. `curl -H "X-Tenant-ID: 1" https://<cloudfront-url>/api/actuator/health` returns `{"status":"UP"}`
  7. `./gradlew bootRun` on the developer machine still starts the application against local PostgreSQL without errors
**Plans**: TBD

**Background for planning:**
- Set the GitHub secret via: Repository → Settings → Secrets and variables → Actions → New repository secret
- The `AWS_DEPLOY_ROLE_ARN` value is the full ARN of the `wfm-github-deploy` role, e.g. `arn:aws:iam::123456789012:role/wfm-github-deploy`
- To trigger the pipeline: push a trivial commit to `main` (e.g., update a comment), or use GitHub Actions UI "Run workflow" if `workflow_dispatch` is configured
- First deploy will be slow (~10 min) because Docker build cache is cold in CI; subsequent deploys are faster
- ECS task health check: in the ECS console, click the service → Tasks tab → click the task → check health status and logs
- If the ECS task fails to start, check CloudWatch Logs for the task — common causes: wrong DB connection string, Flyway migration error, missing env var
- The ALB routes `/api/*` to ECS and all other paths to S3/CloudFront; the CloudFront URL is the public entry point for both frontend and API
- APP-04 verification: simply run `./gradlew bootRun` locally after all cloud work is done and confirm it starts — the local config in `application.yml` points to `localhost:5432` and is unaffected by AWS changes

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Local Tooling & State Bootstrap | 0/2 | In progress | - |
| 2. Security Cleanup & OIDC Setup | 0/TBD | Not started | - |
| 3. Infrastructure Provisioning | 0/TBD | Not started | - |
| 4. CI/CD Pipeline & Go-Live | 0/TBD | Not started | - |
