# ---------- Application Load Balancer ----------

resource "aws_lb" "main" {
  name               = "${var.app_name}-${var.environment}"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  tags = { Name = "${var.app_name}-alb" }
}

# ---------- Target Group ----------

resource "aws_lb_target_group" "app" {
  name        = "${var.app_name}-${var.environment}"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    enabled             = true
    path                = "/actuator/health"
    port                = "traffic-port"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }

  deregistration_delay = 30
}

# ---------- Listener ----------

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  # For production, add an HTTPS listener with ACM cert and redirect HTTP → HTTPS
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}
