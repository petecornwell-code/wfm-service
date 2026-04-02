# ---------- ALB Security Group ----------

resource "aws_security_group" "alb" {
  name_prefix = "${var.app_name}-alb-"
  vpc_id      = aws_vpc.main.id
  description = "ALB security group"

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.app_name}-alb-sg" }

  lifecycle { create_before_destroy = true }
}

# ---------- ECS Security Group ----------

resource "aws_security_group" "ecs" {
  name_prefix = "${var.app_name}-ecs-"
  vpc_id      = aws_vpc.main.id
  description = "ECS tasks security group"

  ingress {
    description     = "From ALB"
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.app_name}-ecs-sg" }

  lifecycle { create_before_destroy = true }
}

# ---------- RDS Security Group ----------

resource "aws_security_group" "rds" {
  name_prefix = "${var.app_name}-rds-"
  vpc_id      = aws_vpc.main.id
  description = "RDS security group"

  ingress {
    description     = "PostgreSQL from ECS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.app_name}-rds-sg" }

  lifecycle { create_before_destroy = true }
}
