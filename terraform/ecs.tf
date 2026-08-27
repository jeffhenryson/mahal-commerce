data "aws_caller_identity" "current" {}

resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = { Name = "${local.name_prefix}-cluster" }
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name       = aws_ecs_cluster.main.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
    base              = 1
  }
}

# ── Task Definition ───────────────────────────────────────────────────────────

locals {
  db_endpoint = aws_db_instance.main.endpoint
  redis_host  = aws_elasticache_replication_group.main.primary_endpoint_address

  # Secrets injected as environment variables via Secrets Manager ARNs.
  # The ECS agent fetches the value at task start; no secret lands in the task definition JSON.
  app_secrets = [
    { name = "JWT_SECRET",                   valueFrom = aws_secretsmanager_secret.app["jwt_secret"].arn },
    { name = "JWT_ISSUER",                   valueFrom = aws_secretsmanager_secret.app["jwt_issuer"].arn },
    { name = "JWT_AUDIENCE",                 valueFrom = aws_secretsmanager_secret.app["jwt_audience"].arn },
    { name = "TOTP_ENCRYPTION_KEY",          valueFrom = aws_secretsmanager_secret.app["totp_encryption_key"].arn },
    { name = "RESEND_API_KEY",               valueFrom = aws_secretsmanager_secret.app["resend_api_key"].arn },
    { name = "CORS_ALLOWED_ORIGINS",         valueFrom = aws_secretsmanager_secret.app["cors_allowed_origins"].arn },
    { name = "PASSWORD_RESET_FRONTEND_URL",  valueFrom = aws_secretsmanager_secret.app["password_reset_url"].arn },
    { name = "AVATAR_BASE_URL",              valueFrom = aws_secretsmanager_secret.app["avatar_base_url"].arn },
    { name = "REDIS_PASSWORD",               valueFrom = aws_secretsmanager_secret.redis_password.arn },
  ]
}

resource "aws_ecs_task_definition" "app" {
  family                   = local.name_prefix
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.app_cpu
  memory                   = var.app_memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([
    {
      name      = var.app_name
      image     = "${aws_ecr_repository.app.repository_url}:${var.ecr_image_tag}"
      essential = true

      portMappings = [
        { containerPort = 8080, protocol = "tcp" }
      ]

      environment = [
        { name = "SPRING_PROFILES_ACTIVE",    value = "prod" },
        { name = "DB_URL",                    value = "jdbc:postgresql://${local.db_endpoint}/${var.db_name}" },
        { name = "DB_USERNAME",               value = var.db_username },
        { name = "REDIS_HOST",                value = local.redis_host },
        { name = "REDIS_PORT",                value = "6379" },
        { name = "REDIS_SSL",                 value = "true" },
        { name = "AVATAR_STORAGE_TYPE",       value = "s3" },
        { name = "AVATAR_S3_BUCKET",          value = aws_s3_bucket.avatars.id },
        { name = "AVATAR_S3_REGION",          value = var.aws_region },
        { name = "AVATAR_S3_PUBLIC_URL_BASE", value = "https://${aws_cloudfront_distribution.avatars.domain_name}" },
        { name = "RESEND_FROM",               value = "noreply@yourdomain.com" },
        { name = "JAVA_OPTS",                 value = "-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom" },
      ]

      secrets = local.app_secrets

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.app.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health/liveness | grep -q UP || exit 1"]
        interval    = 30
        timeout     = 10
        retries     = 3
        startPeriod = 90
      }

      readonlyRootFilesystem = false
      user                   = "10001"
    }
  ])

  tags = { Name = "${local.name_prefix}-task" }
}

# ── ECS Service ───────────────────────────────────────────────────────────────

resource "aws_ecs_service" "app" {
  name            = "${local.name_prefix}-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.app_desired_count

  launch_type = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = var.app_name
    container_port   = 8080
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  deployment_controller {
    type = "ECS"
  }

  # Rolling deployment: keep at least 1 task healthy while deploying
  deployment_minimum_healthy_percent = 50
  deployment_maximum_percent         = 200

  health_check_grace_period_seconds = 120

  depends_on = [
    aws_lb_listener.http,
    aws_iam_role_policy_attachment.ecs_task_execution_managed,
  ]

  tags = { Name = "${local.name_prefix}-service" }

  lifecycle {
    # Allow external deploys (CI/CD) to update the task definition without Terraform drift
    ignore_changes = [task_definition, desired_count]
  }
}
