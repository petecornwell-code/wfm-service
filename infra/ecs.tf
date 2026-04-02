# ---------- ECS Cluster ----------

resource "aws_ecs_cluster" "main" {
  name = "${var.app_name}-${var.environment}"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

# ---------- CloudWatch Log Group ----------

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.app_name}-${var.environment}"
  retention_in_days = 30
}

# ---------- Task Definition ----------

resource "aws_ecs_task_definition" "app" {
  family                   = "${var.app_name}-${var.environment}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.ecs_cpu
  memory                   = var.ecs_memory
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name  = var.app_name
    image = "${aws_ecr_repository.app.repository_url}:latest"

    essential = true

    portMappings = [{
      containerPort = var.container_port
      protocol      = "tcp"
    }]

    environment = [
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.main.endpoint}/${var.db_name}" },
      { name = "SPRING_DATASOURCE_USERNAME", value = var.db_username },
      { name = "CORS_ALLOWED_ORIGINS", value = var.cors_allowed_origins },
      { name = "SOLVER_TIME_LIMIT", value = var.solver_time_limit },
      { name = "SPRING_PROFILES_ACTIVE", value = var.environment },
      { name = "JAVA_OPTS", value = "-XX:MaxRAMPercentage=75.0" },
    ]

    secrets = [
      {
        name      = "SPRING_DATASOURCE_PASSWORD"
        valueFrom = aws_secretsmanager_secret.db_password.arn
      }
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.app.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:${var.container_port}/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
  }])
}

# ---------- ECS Service ----------

resource "aws_ecs_service" "app" {
  name            = var.app_name
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.ecs_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = var.app_name
    container_port   = var.container_port
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  depends_on = [aws_lb_listener.http]
}
