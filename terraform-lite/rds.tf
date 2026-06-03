resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_ssm_parameter" "db_password" {
  name        = "/${local.name_prefix}/db-password"
  description = "RDS master password"
  type        = "SecureString"
  value       = random_password.db.result

  tags = { Name = "${local.name_prefix}-db-password" }
}

resource "aws_db_subnet_group" "main" {
  name       = "${local.name_prefix}-db"
  subnet_ids = aws_subnet.public[*].id
  tags       = { Name = "${local.name_prefix}-db" }
}

resource "aws_db_parameter_group" "postgres" {
  name   = "${local.name_prefix}-pg16"
  family = "postgres16"
  tags   = { Name = "${local.name_prefix}-pg16" }
}

resource "aws_db_instance" "main" {
  identifier     = "${local.name_prefix}-postgres"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.postgres.name

  publicly_accessible = false
  multi_az            = false
  deletion_protection = false

  storage_type      = "gp2"
  allocated_storage = 20 # Free tier: up to 20 GB

  storage_encrypted = true

  backup_retention_period = var.db_backup_retention_days
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:00-sun:05:00"

  performance_insights_enabled = false # Adds cost — disable for free tier

  skip_final_snapshot = true # Change to false + add final_snapshot_identifier for production

  tags = { Name = "${local.name_prefix}-postgres" }
}
