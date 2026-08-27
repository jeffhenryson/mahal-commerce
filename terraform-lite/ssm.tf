resource "random_password" "redis" {
  length  = 32
  special = false
}

# Sensitive app secrets — stored as SecureString (standard tier is free)
locals {
  app_secrets = {
    "jwt-secret"          = { value = var.jwt_secret,          description = "JWT signing secret" }
    "totp-encryption-key" = { value = var.totp_encryption_key, description = "TOTP AES encryption key" }
    "resend-api-key"      = { value = var.resend_api_key,      description = "Resend transactional email API key" }
    "redis-password"      = { value = random_password.redis.result, description = "Redis auth token" }
  }
}

resource "aws_ssm_parameter" "app" {
  for_each    = local.app_secrets
  name        = "/${local.name_prefix}/${each.key}"
  description = each.value.description
  type        = "SecureString"
  value       = each.value.value

  lifecycle {
    ignore_changes = [value] # Allow rotating secrets out-of-band
  }

  tags = { Name = "${local.name_prefix}-${each.key}" }
}
