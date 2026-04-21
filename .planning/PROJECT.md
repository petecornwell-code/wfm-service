# WFM Service — AWS Deployment

## What This Is

A workforce management scheduling service (Spring Boot + Timefold Solver + React SPA) that needs to be deployed to AWS for internal team use. The codebase already contains Terraform infrastructure definitions and GitHub Actions pipelines — the goal is to provision and wire everything so the app runs live in the cloud while development continues locally in IntelliJ.

## Core Value

Internal users can access the WFM scheduling tool via a stable cloud URL without needing to run it locally.

## Requirements

### Validated

- ✓ AWS CLI installed and configured locally — v1.0
- ✓ Terraform installed locally — v1.0
- ✓ Terraform remote state backend bootstrapped (S3 + DynamoDB) — v1.0
- ✓ Hardcoded BambooHR credentials removed from codebase — v1.0
- ✓ Core AWS infrastructure provisioned (VPC, ECR, RDS, ALB, CloudFront, S3) — v1.0 partial

### Active (Next Milestone)

- [ ] AWS OIDC role provisioned for GitHub Actions deployment (blocked: iam:CreateRole needed)
- [ ] Remaining 9 Terraform resources provisioned (ECS roles, task def, service)
- [ ] GitHub secrets configured (AWS_DEPLOY_ROLE_ARN)
- [ ] CI/CD pipeline triggers successfully on push to main
- [ ] Application live on AWS with working frontend and API
- [ ] Local IntelliJ dev environment continues to work unchanged

### Out of Scope

- API authentication / authorization — deferred, internal use for now
- Custom domain / DNS — using AWS default URLs (CloudFront + ALB)
- Multi-environment staging setup — single environment only
- Monitoring dashboards / alerting — beyond basic CloudWatch logs

## Context

**Existing infra code:** `infra/` contains Terraform for VPC, ECS Fargate, RDS PostgreSQL 16, ECR, ALB, S3 + CloudFront, IAM, security groups. Targets `eu-west-2`.

**Existing CI/CD:** `.github/workflows/ci.yml` runs tests + Docker build; `.github/workflows/deploy.yml` deploys backend to ECS and frontend to S3 + CloudFront on push to main.

**Terraform state backend:** Defined but commented out in `infra/main.tf` — needs S3 bucket (`wfm-terraform-state`) and DynamoDB table (`wfm-terraform-locks`) bootstrapped before `terraform init` can use it.

**AWS CLI:** Pete has an AWS account but the CLI is not yet installed or configured. This is the first blocker.

**GitHub:** Project is already on GitHub. OIDC role ARN must be set as GitHub secret `AWS_DEPLOY_ROLE_ARN`.

**Security:** No authentication layer on the API — by design for now; service should not be publicly exposed without network-level access control (e.g., security group restrictions or VPN).

**Hardcoded credentials:** `src/main/java/utils/BambooCustomFields.java` and `BambooEmployeesByDepartment.java` contain a BambooHR API key — should be removed before first deployment (security concern, not a blocker for infra provisioning).

## Constraints

- **Region**: `eu-west-2` (London) — already hardcoded in Terraform and deploy pipeline
- **Runtime**: ECS Fargate, 2 vCPU / 4 GB — sufficient for Timefold solver with 1 desk
- **Database**: RDS PostgreSQL 16, `db.t4g.medium` — single AZ for cost
- **Terraform**: >= 1.5 required
- **AWS CLI**: v2 recommended

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Keep existing Terraform as-is | Infra code is complete and correct | ✓ Good |
| Single environment ("dev") | Internal use, cost control | ✓ Good |
| OIDC for GitHub Actions auth | No long-lived AWS credentials in CI | ⚠ Revisit — blocked by IAM permissions |
| AWS default URLs, no custom domain | Simplest path to working deployment | ✓ Good |
| S3 bucket suffix wfm-terraform-state-521757869980 | Original name taken; account-ID suffix is best practice | ✓ Good |
| Use existing IAM user pete.cornwell@helpware.com | iam:CreateUser not available; root inaccessible | ⚠ Revisit — PowerUserAccess blocks iam:CreateRole |
| Fixed RDS engine 16.4→16.6 | 16.4 not available in eu-west-2 | ✓ Good |

---
*Last updated: 2026-04-21 after v1.0 milestone archive (partially shipped — IAM blocker)*
