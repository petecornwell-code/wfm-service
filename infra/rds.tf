# ---------- RDS Subnet Group ----------

resource "aws_db_subnet_group" "main" {
  name       = "${var.app_name}-${var.environment}"
  subnet_ids = aws_subnet.private[*].id

  tags = { Name = "${var.app_name}-db-subnet-group" }
}

# ---------- RDS Password ----------

resource "aws_secretsmanager_secret" "db_password" {
  name                    = "${var.app_name}/${var.environment}/db-password"
  recovery_window_in_days = 7
}

resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db.result
}

# ---------- RDS Instance ----------

resource "aws_db_instance" "main" {
  identifier = "${var.app_name}-${var.environment}"

  engine = "postgres"
  # Pinned to the minor version actually running. AWS applies minor upgrades automatically, so
  # this drifts upward over time — live had moved 16.6 -> 16.13 by 2026-08-11 while the config
  # still said 16.6, and Terraform proposed "upgrading" back down. RDS cannot downgrade an engine
  # version, so that apply would have failed or damaged the instance.
  #
  # ignore_changes below stops the next auto-upgrade re-creating the same trap. Set this value
  # deliberately and remove the ignore only when performing an intentional version change.
  engine_version = "16.13"
  instance_class = var.db_instance_class

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  multi_az               = var.db_multi_az
  publicly_accessible    = false

  backup_retention_period = 7
  skip_final_snapshot     = var.environment != "prod"
  final_snapshot_identifier = var.environment == "prod" ? "${var.app_name}-final-snapshot" : null
  deletion_protection       = var.environment == "prod"

  # Enable pgvector — available natively on RDS PostgreSQL 16
  parameter_group_name = aws_db_parameter_group.main.name

  tags = { Name = "${var.app_name}-${var.environment}" }

  # AWS auto-applies minor engine upgrades, which otherwise shows up as Terraform trying to
  # downgrade on the next plan. See the engine_version comment above.
  lifecycle {
    ignore_changes = [engine_version]
  }
}

resource "aws_db_parameter_group" "main" {
  name_prefix = "${var.app_name}-pg16-"
  family      = "postgres16"
  description = "Custom parameter group for ${var.app_name}"

  # pgvector is loaded as a shared library
  # apply_method must be "pending-reboot" for static parameters like shared_preload_libraries
  parameter {
    name         = "shared_preload_libraries"
    value        = "pg_stat_statements"
    apply_method = "pending-reboot"
  }

  lifecycle { create_before_destroy = true }
}
