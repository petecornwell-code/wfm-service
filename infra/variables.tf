variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "eu-west-2"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
}

variable "app_name" {
  description = "Application name"
  type        = string
  default     = "wfm-service"
}

# ---------- Networking ----------

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "AZs to use (minimum 2 for RDS subnet group)"
  type        = list(string)
  default     = ["eu-west-2a", "eu-west-2b"]
}

# ---------- RDS ----------

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.medium"
}

variable "db_name" {
  description = "Database name"
  type        = string
  default     = "wfm"
}

variable "db_username" {
  description = "Database master username"
  type        = string
  default     = "wfm"
}

variable "db_multi_az" {
  description = "Enable Multi-AZ for RDS"
  type        = bool
  default     = false
}

# ---------- ECS ----------

variable "ecs_cpu" {
  description = "ECS task CPU units (1024 = 1 vCPU)"
  type        = number
  default     = 2048
}

variable "ecs_memory" {
  description = "ECS task memory in MiB"
  type        = number
  default     = 4096
}

variable "ecs_desired_count" {
  description = "Number of ECS tasks to run"
  type        = number
  default     = 1
}

variable "container_port" {
  description = "Port the container listens on"
  type        = number
  default     = 8080
}

# ---------- Frontend ----------

variable "frontend_domain" {
  description = "Custom domain for CloudFront (optional, leave empty to use CloudFront default)"
  type        = string
  default     = ""
}

# ---------- App Config ----------

variable "solver_time_limit" {
  description = "Timefold solver time limit (ISO 8601 duration)"
  type        = string
  default     = "PT5M"
}
