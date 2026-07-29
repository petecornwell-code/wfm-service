---
phase: 1
slug: local-tooling-state-bootstrap
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-02
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Shell commands (no test framework — infra/tooling phase) |
| **Config file** | none |
| **Quick run command** | `aws sts get-caller-identity && terraform -version` |
| **Full suite command** | See verification commands below |
| **Estimated runtime** | ~5 seconds |

---

## Sampling Rate

- **After every task commit:** Run `aws sts get-caller-identity && terraform -version`
- **After Wave 1 (tooling installed):** Run full tooling verification
- **After Wave 2 (bootstrap complete):** Run full AWS resource verification

---

## Verification Commands

### TOOL-01: AWS CLI installed
```bash
aws --version
# Expected: aws-cli/2.x.x ...
```

### TOOL-02: AWS CLI configured
```bash
aws sts get-caller-identity
# Expected: JSON with Account, UserId, Arn fields (no error)
```

### TOOL-03: Terraform installed (ALREADY SATISFIED — v1.5.7 present)
```bash
terraform -version
# Expected: Terraform v1.5.x or higher
```

### BOOT-01: S3 bucket exists with versioning + encryption
```bash
aws s3api head-bucket --bucket wfm-terraform-state --region eu-west-2
aws s3api get-bucket-versioning --bucket wfm-terraform-state --region eu-west-2
# Expected: Status: "Enabled"
aws s3api get-bucket-encryption --bucket wfm-terraform-state --region eu-west-2
# Expected: SSEAlgorithm: "aws:kms" or "AES256"
```

### BOOT-02: DynamoDB table exists
```bash
aws dynamodb describe-table --table-name wfm-terraform-locks --region eu-west-2
# Expected: TableStatus: "ACTIVE", KeySchema has AttributeName: "LockID"
```

### BOOT-03: Backend block configured in main.tf
```bash
grep -A 6 'backend "s3"' infra/main.tf
# Expected: bucket, key, region, dynamodb_table, encrypt all present (not commented)
```

### BOOT-04: terraform init succeeds
```bash
cd infra && terraform init
# Expected: "Successfully configured the backend" in output, exit code 0
```

---

## Pass Criteria

All 7 requirements verified:
- [ ] TOOL-01: `aws --version` exits 0 and shows v2.x
- [ ] TOOL-02: `aws sts get-caller-identity` returns valid account JSON
- [ ] TOOL-03: `terraform -version` shows >= 1.5 ✓ (pre-existing)
- [ ] BOOT-01: S3 bucket exists with versioning=Enabled and SSE configured
- [ ] BOOT-02: DynamoDB table exists with ACTIVE status and LockID key
- [ ] BOOT-03: `infra/main.tf` backend block has all 5 required fields uncommented
- [ ] BOOT-04: `terraform init` exits 0 with "Successfully configured the backend"
