# Requirements: WFM Service AWS Deployment

**Defined:** 2026-04-02
**Core Value:** Internal users can access the WFM scheduling tool via a stable cloud URL

## v1 Requirements

### Local Tooling

- [x] **TOOL-01**: AWS CLI v2 installed on developer machine
- [x] **TOOL-02**: AWS CLI configured with credentials for the target AWS account
- [x] **TOOL-03**: Terraform >= 1.5 installed on developer machine

### Terraform State Bootstrap

- [x] **BOOT-01**: S3 bucket `wfm-terraform-state` created in `eu-west-2` with versioning and encryption enabled
- [x] **BOOT-02**: DynamoDB table `wfm-terraform-locks` created for Terraform state locking
- [x] **BOOT-03**: `infra/main.tf` backend block uncommented and configured to use the bootstrap bucket/table
- [x] **BOOT-04**: `terraform init` completes successfully with remote state backend

### AWS OIDC & IAM

- [ ] **IAM-01**: GitHub OIDC identity provider registered in AWS IAM
- [ ] **IAM-02**: IAM role `wfm-github-deploy` created with trust policy scoped to the repository
- [ ] **IAM-03**: Role has permissions to: push to ECR, update ECS service, register task definitions, sync S3, invalidate CloudFront, read Secrets Manager

### Infrastructure Provisioning

- [ ] **INFRA-01**: `terraform plan` produces a valid plan with no errors
- [ ] **INFRA-02**: `terraform apply` provisions all resources: VPC, subnets, security groups, ECR, RDS PostgreSQL, ECS cluster + service, ALB, S3 bucket, CloudFront distribution
- [ ] **INFRA-03**: RDS instance is reachable from ECS tasks (security group allows port 5432)
- [ ] **INFRA-04**: Database password stored in AWS Secrets Manager and injected into ECS task definition
- [ ] **INFRA-05**: Flyway migrations run automatically on first ECS task start

### CI/CD Pipeline

- [ ] **CICD-01**: GitHub secret `AWS_DEPLOY_ROLE_ARN` set to the IAM role ARN
- [ ] **CICD-02**: Push to `main` triggers `deploy.yml` successfully
- [ ] **CICD-03**: Docker image built and pushed to ECR
- [ ] **CICD-04**: ECS service updated and stabilises (new task running, health check passing)
- [ ] **CICD-05**: Frontend built and synced to S3; CloudFront invalidation created

### Application Verification

- [ ] **APP-01**: CloudFront URL returns the React SPA (HTTP 200)
- [ ] **APP-02**: API health endpoint (`/actuator/health`) returns `{"status":"UP"}`
- [ ] **APP-03**: API responds to a request with `X-Tenant-ID` header (not 400/500)
- [ ] **APP-04**: Local IntelliJ run (`./gradlew bootRun`) still works against local PostgreSQL

### Pre-deployment Security Cleanup

- [x] **SEC-01**: `src/main/java/utils/BambooCustomFields.java` removed or moved to test scope
- [x] **SEC-02**: `src/main/java/utils/BambooEmployeesByDepartment.java` removed or moved to test scope
- [x] **SEC-03**: Ensure `.gitignore` includes `infra/terraform.tfvars`

## v2 Requirements

### Auth & Access Control

- **AUTH-01**: API key or Cognito-based authentication added to the API
- **AUTH-02**: CloudFront restricted to known IP ranges or VPN

### Observability

- **OBS-01**: CloudWatch dashboard for ECS CPU/memory and RDS connections
- **OBS-02**: Alerting on ECS task failures

### Custom Domain

- **DNS-01**: Route 53 hosted zone with HTTPS certificate via ACM
- **DNS-02**: CloudFront and ALB configured with custom domain

## Out of Scope

| Feature | Reason |
|---------|--------|
| API authentication | Internal use only for now — network-level isolation sufficient |
| Multi-environment (staging) | Single environment keeps cost and complexity low |
| Custom domain / SSL | Using AWS default URLs; no Route53 setup needed |
| Monitoring dashboards | Out of scope for initial deployment |
| Auto-scaling | Single ECS task sufficient for internal use |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| TOOL-01 | Phase 1 | Complete |
| TOOL-02 | Phase 1 | Complete |
| TOOL-03 | Phase 1 | Complete |
| BOOT-01 | Phase 1 | Complete |
| BOOT-02 | Phase 1 | Complete |
| BOOT-03 | Phase 1 | Complete |
| BOOT-04 | Phase 1 | Complete |
| IAM-01 | Phase 2 | Pending |
| IAM-02 | Phase 2 | Pending |
| IAM-03 | Phase 2 | Pending |
| SEC-01 | Phase 2 | Complete |
| SEC-02 | Phase 2 | Complete |
| SEC-03 | Phase 2 | Complete |
| INFRA-01 | Phase 3 | Pending |
| INFRA-02 | Phase 3 | Pending |
| INFRA-03 | Phase 3 | Pending |
| INFRA-04 | Phase 3 | Pending |
| INFRA-05 | Phase 3 | Pending |
| CICD-01 | Phase 4 | Pending |
| CICD-02 | Phase 4 | Pending |
| CICD-03 | Phase 4 | Pending |
| CICD-04 | Phase 4 | Pending |
| CICD-05 | Phase 4 | Pending |
| APP-01 | Phase 4 | Pending |
| APP-02 | Phase 4 | Pending |
| APP-03 | Phase 4 | Pending |
| APP-04 | Phase 4 | Pending |

**Coverage:**
- v1 requirements: 27 total
- Mapped to phases: 27
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-02*
*Last updated: 2026-04-02 after initial definition*
