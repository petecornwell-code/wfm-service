---
phase: 3
slug: infrastructure-provisioning
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-02
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | AWS CLI + Terraform CLI (infrastructure validation — no unit test framework) |
| **Config file** | `infra/` — Terraform root module |
| **Quick run command** | `cd infra && terraform validate` |
| **Full suite command** | `cd infra && terraform plan -lock=false` |
| **Estimated runtime** | ~30 seconds (validate), ~2 minutes (plan) |

---

## Sampling Rate

- **After every task commit:** Run `cd infra && terraform validate`
- **After every plan wave:** Run `cd infra && terraform plan -lock=false`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 3-01-01 | 01 | 0 | INFRA-01 | infra | `terraform force-unlock -force <lock-id>` | ✅ | ⬜ pending |
| 3-01-02 | 01 | 1 | INFRA-01 | infra | `terraform plan -lock=false \| grep "45 to add"` | ✅ | ⬜ pending |
| 3-01-03 | 01 | 1 | INFRA-01 | infra | `terraform apply -auto-approve` | ✅ | ⬜ pending |
| 3-02-01 | 02 | 2 | INFRA-02 | infra | `aws ecs describe-clusters --clusters wfm-dev --query 'clusters[0].status'` | ✅ | ⬜ pending |
| 3-02-02 | 02 | 2 | INFRA-03 | infra | `aws rds describe-db-instances --query 'DBInstances[0].DBInstanceStatus'` | ✅ | ⬜ pending |
| 3-02-03 | 02 | 2 | INFRA-03 | infra | `aws ec2 describe-security-groups --filters Name=group-name,Values=wfm-dev-rds-sg` | ✅ | ⬜ pending |
| 3-02-04 | 02 | 2 | INFRA-04 | infra | `aws secretsmanager get-secret-value --secret-id wfm-dev-db-password` | ✅ | ⬜ pending |
| 3-03-01 | 03 | 3 | INFRA-02 | infra | `terraform output` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `terraform force-unlock` — clear stale DynamoDB state lock before any apply

*This is a prerequisite action, not a test file. Must complete before Wave 1.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Flyway runs 24 migrations on ECS start | INFRA-05 | ECS task needs Docker image (Phase 4) | Check CloudWatch log group `/ecs/wfm-*` after Phase 4 deploys image |
| CloudFront distribution reaches healthy state | INFRA-02 | Deployment takes 15+ minutes | Check AWS console Distribution status = Deployed |
| pgvector extension enabled in RDS | INFRA-05 | Requires DB connection | Check CloudWatch logs for `pgvector` after first ECS task starts in Phase 4 |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
