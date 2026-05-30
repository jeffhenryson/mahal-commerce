variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (prod, staging)"
  type        = string
  default     = "prod"
}

variable "app_name" {
  description = "Application name — rename when using this as a template"
  type        = string
  default     = "security-spring"
}

# ── Networking ────────────────────────────────────────────────────────────────

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "List of AZs to use (min 2 for RDS Multi-AZ and ALB)"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

# ── ECS / App ─────────────────────────────────────────────────────────────────

variable "ecr_image_tag" {
  description = "Docker image tag to deploy (e.g. commit SHA or 'latest')"
  type        = string
  default     = "latest"
}

variable "app_cpu" {
  description = "ECS task CPU units (256=0.25vCPU, 512=0.5vCPU, 1024=1vCPU)"
  type        = number
  default     = 512
}

variable "app_memory" {
  description = "ECS task memory in MiB"
  type        = number
  default     = 1024
}

variable "app_desired_count" {
  description = "Desired number of ECS tasks"
  type        = number
  default     = 2
}

variable "app_min_count" {
  description = "Minimum number of ECS tasks for auto scaling"
  type        = number
  default     = 1
}

variable "app_max_count" {
  description = "Maximum number of ECS tasks for auto scaling"
  type        = number
  default     = 5
}

# ── RDS ───────────────────────────────────────────────────────────────────────

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "db_name" {
  description = "PostgreSQL database name"
  type        = string
  default     = "security"
}

variable "db_username" {
  description = "PostgreSQL master username"
  type        = string
  default     = "appuser"
}

variable "db_multi_az" {
  description = "Enable Multi-AZ for RDS"
  type        = bool
  default     = false
}

variable "db_backup_retention_days" {
  description = "RDS automated backup retention period in days"
  type        = number
  default     = 7
}

# ── ElastiCache ───────────────────────────────────────────────────────────────

variable "redis_node_type" {
  description = "ElastiCache Redis node type"
  type        = string
  default     = "cache.t3.micro"
}

# ── ALB / HTTPS ───────────────────────────────────────────────────────────────

variable "acm_certificate_arn" {
  description = "ARN of the ACM certificate for HTTPS. Leave empty to use HTTP only (not recommended for production)."
  type        = string
  default     = ""
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to reach the ALB (default: open internet)"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

# ── S3 ────────────────────────────────────────────────────────────────────────

variable "avatar_bucket_force_destroy" {
  description = "Allow destroying the avatar S3 bucket even if it contains objects (dev/staging only)"
  type        = bool
  default     = false
}
