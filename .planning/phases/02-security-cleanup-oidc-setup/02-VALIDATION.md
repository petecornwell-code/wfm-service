---
phase: 2
slug: security-cleanup-oidc-setup
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-02
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Shell commands + AWS CLI (no test framework — infra/cleanup phase) |
| **Quick run command** | `ls src/main/java/utils/ 2>/dev/null && echo "files still exist" || echo "clean"` |
| **Full suite command** | See verification commands below |
| **Estimated runtime** | ~10 seconds |

---

## Verification Commands

### SEC-01: BambooCustomFields.java deleted
```bash
test ! -f src/main/java/utils/BambooCustomFields.java && echo "PASS" || echo "FAIL"
```

### SEC-02: BambooEmployeesByDepartment.java deleted
```bash
test ! -f src/main/java/utils/BambooEmployeesByDepartment.java && echo "PASS" || echo "FAIL"
```

### SEC-03: terraform.tfvars in .gitignore (pre-satisfied)
```bash
grep 'terraform.tfvars' .gitignore && echo "PASS" || echo "FAIL"
```

### IAM-01: OIDC identity provider exists
```bash
aws iam list-open-id-connect-providers --query 'OpenIDConnectProviderList[*].Arn' --output text | grep 'token.actions.githubusercontent.com' && echo "PASS" || echo "FAIL"
```

### IAM-02: Deploy role exists with correct trust policy
```bash
aws iam get-role --role-name wfm-dev-github-actions --query 'Role.RoleName' --output text
# Expected: wfm-dev-github-actions
aws iam get-role --role-name wfm-dev-github-actions --query 'Role.AssumeRolePolicyDocument' --output json | grep 'petecornwell-code/wfm-service'
# Expected: line containing the repo reference
```

### IAM-03: Deploy role has required permissions
```bash
aws iam list-role-policies --role-name wfm-dev-github-actions
# Expected: at least one policy listed
aws iam get-role-policy --role-name wfm-dev-github-actions --policy-name wfm-dev-github-actions-deploy-policy --query 'PolicyDocument' --output json | grep -E 'ecr:|ecs:|s3:|cloudfront:|secretsmanager:'
# Expected: all service prefixes present
```

---

## Pass Criteria

All 6 requirements verified:
- [ ] SEC-01: `BambooCustomFields.java` deleted from filesystem
- [ ] SEC-02: `BambooEmployeesByDepartment.java` deleted from filesystem
- [ ] SEC-03: `terraform.tfvars` in `.gitignore` ✓ (pre-existing)
- [ ] IAM-01: OIDC provider for `token.actions.githubusercontent.com` exists in AWS
- [ ] IAM-02: Role `wfm-dev-github-actions` exists with repo-scoped trust policy
- [ ] IAM-03: Role policy includes ECR, ECS, S3, CloudFront, Secrets Manager permissions
