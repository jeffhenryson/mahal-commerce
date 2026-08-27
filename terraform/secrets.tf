# All application secrets are stored in Secrets Manager.
# The ECS task role has read access; no static credentials are needed in the container.

locals {
  secret_names = {
    jwt_secret          = "${local.name_prefix}/jwt-secret"
    jwt_issuer          = "${local.name_prefix}/jwt-issuer"
    jwt_audience        = "${local.name_prefix}/jwt-audience"
    totp_encryption_key = "${local.name_prefix}/totp-encryption-key"
    resend_api_key      = "${local.name_prefix}/resend-api-key"
    cors_allowed_origins = "${local.name_prefix}/cors-allowed-origins"
    password_reset_url  = "${local.name_prefix}/password-reset-frontend-url"
    avatar_base_url     = "${local.name_prefix}/avatar-base-url"
  }
}

resource "aws_secretsmanager_secret" "app" {
  for_each                = local.secret_names
  name                    = each.value
  description             = "App secret: ${each.key}"
  recovery_window_in_days = 7

  tags = { Name = each.value }
}

# Placeholder versions — set the real values with:
#   aws secretsmanager put-secret-value --secret-id <name> --secret-string '<value>'
# or via the AWS Console before the first deploy.
resource "aws_secretsmanager_secret_version" "app" {
  for_each  = aws_secretsmanager_secret.app
  secret_id = each.value.id

  # Placeholder so Terraform creates the version; overwrite before deploying
  secret_string = "CHANGE_ME"

  lifecycle {
    ignore_changes = [secret_string]
  }
}
