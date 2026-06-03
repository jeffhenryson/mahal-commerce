variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "dev"
}

variable "app_name" {
  description = "Application name"
  type        = string
  default     = "security-spring"
}

# ── Networking ────────────────────────────────────────────────────────────────

variable "vpc_cidr" {
  type    = string
  default = "10.1.0.0/16"
}

variable "availability_zones" {
  type    = list(string)
  default = ["us-east-1a", "us-east-1b"]
}

# ── EC2 ───────────────────────────────────────────────────────────────────────

variable "ec2_instance_type" {
  description = "t2.micro and t3.micro are free-tier eligible (750 h/month)"
  type        = string
  default     = "t2.micro"
}

variable "ssh_public_key" {
  description = "Contents of your public key file (~/.ssh/id_rsa.pub)"
  type        = string
}

variable "allowed_ssh_cidr" {
  description = "IP allowed to SSH — use YOUR_IP/32, not 0.0.0.0/0"
  type        = string
}

# ── ECR / App ─────────────────────────────────────────────────────────────────

variable "ecr_image_tag" {
  description = "Docker image tag to deploy"
  type        = string
  default     = "latest"
}

# ── RDS ───────────────────────────────────────────────────────────────────────

variable "db_instance_class" {
  description = "db.t3.micro is free-tier eligible (750 h/month, 20 GB)"
  type        = string
  default     = "db.t3.micro"
}

variable "db_name" {
  type    = string
  default = "security"
}

variable "db_username" {
  type    = string
  default = "appuser"
}

variable "db_backup_retention_days" {
  description = "0 disables automated backups; 1 is the minimum that enables them"
  type        = number
  default     = 1
}

# ── S3 ────────────────────────────────────────────────────────────────────────

variable "avatar_bucket_force_destroy" {
  description = "Allow Terraform to delete the bucket even with objects inside"
  type        = bool
  default     = true
}

# ── App secrets (sensitive — set in terraform.tfvars, never commit) ──────────

variable "jwt_secret" {
  description = "JWT signing secret (min 256-bit / 32 chars)"
  type        = string
  sensitive   = true
}

variable "totp_encryption_key" {
  description = "AES key for TOTP encryption (32-byte hex string)"
  type        = string
  sensitive   = true
}

variable "resend_api_key" {
  description = "Resend transactional email API key"
  type        = string
  sensitive   = true
  default     = "CHANGE_ME"
}

# ── App config (non-sensitive — safe in tfvars) ───────────────────────────────

variable "jwt_issuer" {
  type    = string
  default = "security-spring"
}

variable "jwt_audience" {
  type    = string
  default = "security-spring-users"
}

variable "resend_from" {
  type    = string
  default = "noreply@yourdomain.com"
}

variable "cors_allowed_origins" {
  type    = string
  default = "http://localhost:4200"
}

variable "password_reset_frontend_url" {
  type    = string
  default = "http://localhost:4200/reset-password"
}
