---
created: 2026-08-14T00:00:00Z
title: Terraform state diverges from live RDS password and publicly_accessible
area: infra

# resolves_phase intentionally unset: no phase currently owns this. Set it to the

# phase that actually takes it on, so close_phase_todos does not sweep it away first.

raised_during_phase: 12
files:

  - infra/rds.tf
  - infra/main.tf

severity: latent — no current impact, fails silently when triggered
audit_acknowledged:
  milestone: v1.2
  at: 2026-08-25
---

## What is drifted (as of 2026-08-14)

Terraform state and reality disagree on two properties of `aws_db_instance.main`
(`wfm-service-dev`), and **only one of them is visible to `terraform plan`.**

| Property | Terraform state / config | Live | Visible to `plan`? |
|---|---|---|---|
| `publicly_accessible` | `false` (`infra/rds.tf:56`) | `true` | **yes** |
| RDS master password | `random_password.db.result` (generated 2026-04-30) | `thisisanewpassword` | **no** |
| Secrets Manager `AWSCURRENT` | version `terraform-20260430151110581600000004` | version `abe72040-…` | **no** |

`terraform plan` on 2026-08-14 reported exactly one change:
`~ publicly_accessible = true -> false`, `Plan: 0 to add, 1 to change, 0 to destroy`.

## Why the password drift is invisible

`infra/rds.tf` has Terraform own both sides from one generated value:

```hcl
resource "random_password" "db" { length = 32, special = false }

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_string = random_password.db.result      # rds.tf:24
}

resource "aws_db_instance" "main" {
  password = random_password.db.result           # rds.tf:45
}
```

AWS never returns a DB password, so Terraform cannot refresh it — it compares config
against state, sees the same value on both sides, and reports clean. The
`aws_secretsmanager_secret_version` resource tracks a specific version id, and the old
version still exists, so that reports clean too.

## The failure mode

Any `apply` that causes `random_password.db` to be regenerated (a `-replace`, a taint,
a change to its arguments, or state loss) rewrites **both** RDS and the secret to a new
value. That is self-consistent and would be fine. The dangerous case is the inverse: an
apply that rewrites only one side, or a restore of state that reasserts the April
password against an RDS instance holding a different one. The app then fails at Flyway
init with:

```
FATAL: password authentication failed for user "wfm"   (SQL State 28P01)
```

Spring never starts, ECS crashloops, the healthy task is drained, and the site 502s.

## How this arose

On 2026-08-13 at 14:10 UTC an out-of-band `ModifyDBInstance` reset the RDS master
password (and enabled public access) without updating Secrets Manager. Nothing broke at
the time — the running container held an authenticated connection pool and kept serving.
It surfaced ~70 minutes later when a routine deploy started a fresh task, which produced
a ~25 minute outage. Resolved by setting both RDS and the secret to the same value, which
is what created the current divergence from Terraform state.

CloudTrail recorded that change. **A future recurrence triggered by `terraform apply`
will have no equivalent trail**, which is what makes this worth writing down.

## Decision

Left as-is deliberately on 2026-08-14: the colleague who reset the password is no longer
using this database, so there is no coordination cost to leaving it, and the environment
is healthy. This note exists so the drift is discoverable rather than surprising.

## Options when someone picks this up

1. **Reconcile via Terraform** — `terraform apply -replace=random_password.db` regenerates
   a strong password and writes it to RDS *and* a new secret version in one apply, so all
   three agree by construction. Requires an ECS redeploy to pick up the new secret, and
   the same apply closes `publicly_accessible`. Cleanest.

2. **Reconcile by hand** — set RDS back to the value currently in Secrets Manager. Restores
   the invariant without a password change, but leaves Terraform state still holding the
   April value, so the drift is only half fixed.

3. **Stop Terraform owning the password** — switch to `manage_master_user_password` (RDS-
   managed rotation in Secrets Manager) and drop `random_password.db`. Removes the class of
   bug entirely rather than resetting this instance of it.

## Related

- `publicly_accessible` is `true` on the live instance while config says `false`. The
  security group still restricts access (a direct `psql` from outside timed out), but the
  current master password is a dictionary phrase — acceptable only while this stays a dev
  sandbox with SG restrictions intact.
