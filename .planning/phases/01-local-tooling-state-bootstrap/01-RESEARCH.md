# Phase 1: Local Tooling & State Bootstrap - Research

**Researched:** 2026-04-02
**Domain:** AWS CLI v2, Terraform, S3/DynamoDB backend bootstrap
**Confidence:** HIGH

---

## Summary

Phase 1 has one important pre-existing advantage: **Terraform v1.5.7 is already installed** on this machine (satisfies TOOL-03 with a version that meets the `>= 1.5` requirement). The only tooling gap is AWS CLI v2, which is not installed but is available via `brew install awscli` (current stable: 2.34.22).

The bootstrap sequence has a hard ordering constraint: S3 bucket and DynamoDB table must be created with AWS CLI commands before the `backend "s3"` block in `infra/main.tf` can be populated. The current `main.tf` has an empty `backend "s3" {}` block with all values commented out — Terraform will fail with "required field is not set" errors if you run `terraform init` in this state. The fix is to uncomment and populate the values, then run `terraform init`.

IAM credential setup requires care: using root account access keys is explicitly against AWS best practices and must not be done. Pete must create an IAM user with AdministratorAccess, generate access keys for that user, and configure them with `aws configure`. This is the only prerequisite that requires action in the AWS Console before any CLI work.

**Primary recommendation:** Install AWS CLI via Homebrew, create an IAM admin user in the AWS Console, configure credentials with `aws configure`, bootstrap S3+DynamoDB via AWS CLI, uncomment and populate the backend block in `main.tf`, then run `terraform init`.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TOOL-01 | AWS CLI v2 installed on developer machine | `brew install awscli` installs v2.34.22; verified available in Homebrew |
| TOOL-02 | AWS CLI configured with credentials for the target AWS account | `aws configure` with IAM user access keys; root credentials must NOT be used |
| TOOL-03 | Terraform >= 1.5 installed on developer machine | Already satisfied: Terraform v1.5.7 is installed at `/opt/homebrew/bin/terraform` |
| BOOT-01 | S3 bucket `wfm-terraform-state` created in `eu-west-2` with versioning and encryption | Three sequential AWS CLI commands; eu-west-2 requires LocationConstraint parameter |
| BOOT-02 | DynamoDB table `wfm-terraform-locks` created for Terraform state locking | Single AWS CLI command with PAY_PER_REQUEST billing; `LockID` as partition key |
| BOOT-03 | `infra/main.tf` backend block uncommented and configured | Edit the backend block: uncomment 5 values, use key `wfm/dev/terraform.tfstate` |
| BOOT-04 | `terraform init` completes successfully with remote state backend | Run after BOOT-01/02/03 complete; use `-migrate-state` flag since empty backend was previously initialized |
</phase_requirements>

---

## Environment Availability (Verified)

| Dependency | Required By | Available | Version | Notes |
|------------|------------|-----------|---------|-------|
| Homebrew | Installing AWS CLI | Yes | 5.1.3 | At `/opt/homebrew/bin/brew` |
| Terraform | TOOL-03, BOOT-04 | Yes | 1.5.7 | At `/opt/homebrew/bin/terraform`; satisfies >= 1.5 |
| AWS CLI | TOOL-01, TOOL-02, BOOT-01, BOOT-02 | **No** | — | `brew install awscli` installs v2.34.22 |
| AWS Account | All BOOT requirements | Assumed yes | — | Pete confirmed he has an AWS account |

**Missing dependencies with no fallback:**
- AWS CLI v2 — must install before any AWS commands can run

**Pre-existing satisfactions:**
- TOOL-03 is already met. No Terraform installation task needed.

---

## Standard Stack

### Core
| Tool | Version | Purpose | Notes |
|------|---------|---------|-------|
| awscli | 2.34.22 (Homebrew) | Bootstrap S3+DynamoDB, verify credentials | Only install path needed |
| terraform | 1.5.7 (already installed) | Initialize remote backend | No action needed for install |

### AWS Resources to Bootstrap
| Resource | Service | Purpose |
|----------|---------|---------|
| `wfm-terraform-state` S3 bucket | S3 | Stores Terraform state file |
| `wfm-terraform-locks` DynamoDB table | DynamoDB | Prevents concurrent state writes |

**Installation:**
```bash
# Only AWS CLI needs installing — Terraform is already present
brew install awscli
```

**Version verification (run after install):**
```bash
aws --version      # expect: aws-cli/2.x.x
terraform version  # expect: Terraform v1.5.7 (already confirmed)
```

---

## Architecture Patterns

### Bootstrap Sequence (mandatory order)

```
1. Install AWS CLI (brew install awscli)
2. Create IAM admin user in AWS Console → download access keys
3. aws configure (enter Access Key ID, Secret, region=eu-west-2, format=json)
4. aws sts get-caller-identity  ← verify credentials work
5. Create S3 bucket (3 CLI commands)
6. Create DynamoDB table (1 CLI command)
7. Edit infra/main.tf — uncomment backend block values
8. cd infra && terraform init -migrate-state
```

Steps 1-4 must precede 5-6. Steps 5-6 must precede 7-8. This ordering is non-negotiable.

### Pattern 1: IAM Admin User (not root)

**What:** Create a dedicated IAM user with AdministratorAccess, generate access keys for CLI use.

**Why:** AWS explicitly prohibits using root account access keys for CLI/programmatic access. Root credentials have unrestricted access to the account including billing.

**Steps in AWS Console:**
1. Sign in to AWS Console as root
2. IAM → Users → Create user (`pete-admin` or similar)
3. Attach policy: AdministratorAccess (managed policy)
4. Security credentials tab → Create access key → use case: "Command Line Interface (CLI)"
5. Download CSV (shown once only — save securely)

### Pattern 2: AWS CLI Configuration

**What:** `aws configure` stores credentials in `~/.aws/credentials` and region in `~/.aws/config`.

**Command:**
```bash
aws configure
# AWS Access Key ID [None]: AKIA...
# AWS Secret Access Key [None]: <secret>
# Default region name [None]: eu-west-2
# Default output format [None]: json
```

**Verify:**
```bash
aws sts get-caller-identity
# Returns: Account ID, UserId, ARN of the configured IAM user
```

### Pattern 3: S3 Backend Bootstrap via AWS CLI

**What:** Create S3 bucket and DynamoDB table using AWS CLI before Terraform can use them as a backend.

**Critical gotcha — eu-west-2 LocationConstraint:** All non-us-east-1 buckets require `--create-bucket-configuration LocationConstraint=<region>`. Omitting this flag causes an `IllegalLocationConstraintException` error.

**Commands:**
```bash
# 1. Create S3 bucket
aws s3api create-bucket \
  --bucket wfm-terraform-state \
  --region eu-west-2 \
  --create-bucket-configuration LocationConstraint=eu-west-2

# 2. Enable versioning
aws s3api put-bucket-versioning \
  --bucket wfm-terraform-state \
  --versioning-configuration Status=Enabled

# 3. Enable server-side encryption (AES256)
aws s3api put-bucket-encryption \
  --bucket wfm-terraform-state \
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": {
        "SSEAlgorithm": "AES256"
      }
    }]
  }'

# 4. Create DynamoDB table (PAY_PER_REQUEST = no capacity planning needed)
aws dynamodb create-table \
  --table-name wfm-terraform-locks \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region eu-west-2
```

### Pattern 4: Uncommenting the Backend Block in infra/main.tf

**Current state** (infra/main.tf lines 15-22):
```hcl
backend "s3" {
  # Configure per environment:
  #   bucket         = "wfm-terraform-state"
  #   key            = "env/prod/terraform.tfstate"
  #   region         = "eu-west-2"
  #   dynamodb_table = "wfm-terraform-locks"
  #   encrypt        = true
}
```

**Target state** (the key value differs from the comment — use `wfm/dev/terraform.tfstate` per ROADMAP):
```hcl
backend "s3" {
  bucket         = "wfm-terraform-state"
  key            = "wfm/dev/terraform.tfstate"
  region         = "eu-west-2"
  dynamodb_table = "wfm-terraform-locks"
  encrypt        = true
}
```

Note: The comment in main.tf says `env/prod/terraform.tfstate` but the ROADMAP specifies `wfm/dev/terraform.tfstate`. Use the ROADMAP value.

### Pattern 5: terraform init with -migrate-state

**Why -migrate-state is needed:** The `backend "s3" {}` empty block was already initialized at some point (Terraform tracks this in `.terraform/`). When the backend block is populated with real values, Terraform detects a configuration change. The `-migrate-state` flag tells Terraform to copy any existing local state to the new remote backend.

**Command:**
```bash
cd /Users/pete/IdeaProjects/wfm-service/infra
terraform init -migrate-state
# Terraform will prompt: "Do you want to copy existing state to the new backend?"
# Answer: yes
```

If there is no existing local state to migrate (clean project), `-migrate-state` is harmless. Alternatively, `-reconfigure` can be used if migration is unwanted, but `-migrate-state` is the safer default for this scenario.

### Anti-Patterns to Avoid

- **Using root access keys:** Never generate or use root account access keys. Root has unlimited access including billing and account deletion. Create an IAM user instead.
- **Creating S3 bucket without LocationConstraint:** Will fail with `IllegalLocationConstraintException` for eu-west-2. The `--create-bucket-configuration LocationConstraint=eu-west-2` flag is mandatory.
- **Running terraform init before S3 bucket exists:** Will fail with `NoSuchBucket` error. Bootstrap resources must exist first.
- **Using the key value from the comment** (`env/prod/terraform.tfstate`): Use `wfm/dev/terraform.tfstate` per ROADMAP.
- **Committing access keys to git:** The `terraform.tfvars` file must be in `.gitignore` (SEC-03 in Phase 2 will verify this). AWS credentials live in `~/.aws/credentials`, never in the repo.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| State locking | Custom file-based lock | DynamoDB table with LockID key | Race conditions, atomic operations, TTL handling |
| State encryption | Custom encryption layer | S3 SSE + `encrypt = true` in backend | AWS-managed keys, no key rotation burden |
| Credential rotation | Manual key tracking | IAM Identity Center / temporary credentials | Long-term keys are a security risk; use for initial bootstrap only |

**Key insight:** The S3+DynamoDB backend pattern is battle-tested and handles concurrent access, encryption at rest, and state history via S3 versioning — none of which are trivial to implement correctly.

---

## Common Pitfalls

### Pitfall 1: LocationConstraint Missing for eu-west-2
**What goes wrong:** `aws s3api create-bucket --bucket wfm-terraform-state --region eu-west-2` fails with `IllegalLocationConstraintException`.
**Why it happens:** S3 requires explicit LocationConstraint for all regions except `us-east-1`. This is a unique AWS quirk where the default region is treated differently.
**How to avoid:** Always include `--create-bucket-configuration LocationConstraint=eu-west-2` in the create-bucket command.
**Warning signs:** Error message contains `IllegalLocationConstraintException`.

### Pitfall 2: terraform init Before Bootstrap Resources Exist
**What goes wrong:** `terraform init` fails with `NoSuchBucket: The specified bucket does not exist`.
**Why it happens:** Terraform immediately tries to connect to S3 when the backend is configured.
**How to avoid:** Run all 4 AWS CLI bootstrap commands and verify resources exist before editing `main.tf`.
**Warning signs:** Any error during `terraform init` mentioning S3 or bucket names.

### Pitfall 3: Empty Backend Block Causes Silent Local State
**What goes wrong:** With `backend "s3" {}` (no values), `terraform init` may silently use local state or fail with "required field is not set" depending on Terraform version.
**Why it happens:** An empty backend block is a "partial configuration" — Terraform needs values to actually connect to S3.
**How to avoid:** Only run `terraform init` after the backend block has all 5 values populated.

### Pitfall 4: Key Path Mismatch Between Comment and ROADMAP
**What goes wrong:** Developer copies the key from the comment in `main.tf` (`env/prod/terraform.tfstate`) instead of using the correct path (`wfm/dev/terraform.tfstate`).
**Why it happens:** The comment in the file uses a generic template key, not the project-specific one.
**How to avoid:** Use `wfm/dev/terraform.tfstate` as specified in the ROADMAP. The S3 key can be any string, but consistency matters for identifying state files later.

### Pitfall 5: IAM User Has No Permissions
**What goes wrong:** `aws sts get-caller-identity` succeeds but AWS CLI commands for S3/DynamoDB fail with `AccessDenied`.
**Why it happens:** IAM user created without attaching a policy.
**How to avoid:** Attach `AdministratorAccess` managed policy to the IAM user when creating it. This is appropriate for a personal admin account bootstrapping infrastructure.

---

## Code Examples

### Verify Credentials
```bash
# Source: AWS CLI documentation
aws sts get-caller-identity
# Expected output:
# {
#     "UserId": "AIDA...",
#     "Account": "123456789012",
#     "Arn": "arn:aws:iam::123456789012:user/pete-admin"
# }
```

### Verify S3 Bucket State After Creation
```bash
# Check versioning is enabled
aws s3api get-bucket-versioning --bucket wfm-terraform-state
# Expected: {"Status": "Enabled"}

# Check encryption is enabled
aws s3api get-bucket-encryption --bucket wfm-terraform-state
# Expected: SSEAlgorithm: AES256
```

### Verify DynamoDB Table
```bash
aws dynamodb describe-table --table-name wfm-terraform-locks --region eu-west-2 \
  --query 'Table.{Status:TableStatus, Key:KeySchema, Billing:BillingModeSummary.BillingMode}'
# Expected: Status=ACTIVE, Key=[{AttributeName: LockID, KeyType: HASH}], Billing=PAY_PER_REQUEST
```

### Complete terraform init
```bash
cd /Users/pete/IdeaProjects/wfm-service/infra
terraform init -migrate-state
# Expected final line: "Terraform has been successfully initialized!"
# or: "Successfully configured the backend"
```

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Shell commands (no automated test runner — this phase is pure infrastructure/tooling setup) |
| Config file | None — verification is ad-hoc CLI commands |
| Quick run command | See per-requirement commands below |
| Full suite command | Run all 5 success-criteria checks in sequence |

### Phase Requirements → Verification Map

| Req ID | Behavior | Test Type | Automated Command | Notes |
|--------|----------|-----------|-------------------|-------|
| TOOL-01 | AWS CLI v2 installed | smoke | `aws --version` | Expect output containing `aws-cli/2.` |
| TOOL-02 | AWS CLI configured for target account | smoke | `aws sts get-caller-identity` | Expect JSON with Account, UserId, Arn |
| TOOL-03 | Terraform >= 1.5 installed | smoke | `terraform version` | Already passes: v1.5.7 installed |
| BOOT-01 | S3 bucket exists with versioning + encryption | integration | See below | Two separate API calls needed |
| BOOT-02 | DynamoDB table exists with LockID key | integration | See below | Single describe-table call |
| BOOT-03 | backend block uncommented in main.tf | static | `grep 'bucket\s*=' infra/main.tf` | Expect match (not commented out) |
| BOOT-04 | terraform init succeeds | integration | `cd infra && terraform init -migrate-state` | Expect "Successfully initialized" |

**BOOT-01 verification commands:**
```bash
# Bucket exists
aws s3api head-bucket --bucket wfm-terraform-state

# Versioning enabled
aws s3api get-bucket-versioning --bucket wfm-terraform-state \
  --query 'Status' --output text
# Expected: Enabled

# Encryption enabled
aws s3api get-bucket-encryption --bucket wfm-terraform-state \
  --query 'ServerSideEncryptionConfiguration.Rules[0].ApplyServerSideEncryptionByDefault.SSEAlgorithm' \
  --output text
# Expected: AES256
```

**BOOT-02 verification command:**
```bash
aws dynamodb describe-table --table-name wfm-terraform-locks --region eu-west-2 \
  --query 'Table.TableStatus' --output text
# Expected: ACTIVE
```

### Sampling Rate
- **Per task:** Run the smoke command for that task's requirement immediately after completing the task
- **Phase gate:** Run all 5 success-criteria checks before declaring Phase 1 complete

### Wave 0 Gaps
None — this phase has no application tests. All verification is via AWS CLI and Terraform CLI commands that exist once the tools are installed.

---

## State of the Art

| Old Approach | Current Approach | Impact for This Phase |
|--------------|------------------|----------------------|
| Long-lived IAM user access keys indefinitely | IAM Identity Center with temporary credentials | For single-person bootstrap, IAM user keys are acceptable; rotate after Phase 2 OIDC is set up |
| Manual S3 bucket creation | Terragrunt / atmos automatic backend provisioning | Not applicable here — project uses plain Terraform; CLI bootstrap is the right approach |
| S3 backend without encryption | S3 backend with SSE + versioning | Already required by BOOT-01; just follow the commands |

**Deprecated/outdated:**
- `aws s3 mb` command: Older bucket creation command that does not support LocationConstraint in the same way as `aws s3api create-bucket`. Use `s3api` for the bootstrap.

---

## Open Questions

1. **AWS Account ID**
   - What we know: Pete has an AWS account; account ID is needed for verification output
   - What's unclear: The specific account ID (shown after `aws sts get-caller-identity`)
   - Recommendation: No action needed in planning; account ID is discovered during execution

2. **Existing .terraform/ directory in infra/**
   - What we know: The current git state shows no `.terraform/` tracked; empty backend block was never successfully initialized
   - What's unclear: Whether `.terraform/` exists locally (untracked)
   - Recommendation: If `.terraform/` exists with local state, `-migrate-state` handles it. If not, plain `terraform init` would also work, but `-migrate-state` is safe either way.

3. **Terraform version — update or keep 1.5.7?**
   - What we know: v1.5.7 satisfies `>= 1.5` requirement; latest is 1.14.8
   - What's unclear: Whether any infra/ code uses features requiring a newer version
   - Recommendation: 1.5.7 satisfies the phase requirement. Do not update in this phase — it would be a scope change. Document as a future improvement.

---

## Sources

### Primary (HIGH confidence)
- Homebrew `brew info awscli` output — verified awscli 2.34.22 available, not installed
- `terraform version` local command output — v1.5.7 confirmed installed
- `infra/main.tf` direct file read — empty backend block contents confirmed
- `.planning/ROADMAP.md` direct file read — canonical key path `wfm/dev/terraform.tfstate`

### Secondary (MEDIUM confidence)
- [AWS S3 LocationConstraint API docs](https://docs.aws.amazon.com/AmazonS3/latest/API/API_CreateBucketConfiguration.html) — eu-west-2 requires LocationConstraint
- [AWS IAM root user best practices](https://docs.aws.amazon.com/IAM/latest/UserGuide/root-user-best-practices.html) — do not use root access keys
- [Terraform S3 Backend docs](https://developer.hashicorp.com/terraform/language/backend/s3) — required fields: bucket, key, region
- [Terraform init -migrate-state](https://support.hashicorp.com/hc/en-us/articles/44027197997587-How-to-Migrate-Terraform-State-Between-Different-Backends-using-init-migrate-state) — use when changing backend configuration

### Tertiary (LOW confidence — WebSearch only)
- Multiple blog posts confirming `billing-mode PAY_PER_REQUEST` for DynamoDB lock tables as standard practice

---

## Metadata

**Confidence breakdown:**
- Environment availability: HIGH — verified by running commands locally
- AWS CLI install: HIGH — verified via `brew info awscli`
- Terraform install: HIGH — verified by running `terraform version`
- S3/DynamoDB bootstrap commands: HIGH — LocationConstraint requirement verified via official AWS docs
- IAM best practices: HIGH — verified via official AWS IAM docs
- terraform init flags: MEDIUM — behavior with empty vs populated backend confirmed via multiple sources

**Research date:** 2026-04-02
**Valid until:** 2026-07-02 (stable AWS CLI/Terraform APIs; LocationConstraint behavior is long-standing)
