data "aws_caller_identity" "current" {}

data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }
}

resource "aws_ecr_repository" "app" {
  name                 = var.app_name
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }

  tags = { Name = var.app_name }
}

# Keep only the 3 most recent images to stay within the 500 MB ECR free tier
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 3 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 3
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_key_pair" "app" {
  key_name   = "${local.name_prefix}-key"
  public_key = var.ssh_public_key
}

locals {
  ecr_registry = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
  db_url       = "jdbc:postgresql://${aws_db_instance.main.address}:5432/${var.db_name}"
  avatar_cf_url = "https://${aws_cloudfront_distribution.avatars.domain_name}"
}

resource "aws_instance" "app" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = var.ec2_instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.ec2.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name
  key_name               = aws_key_pair.app.key_name

  user_data = templatefile("${path.module}/user_data.sh", {
    name_prefix                 = local.name_prefix
    aws_region                  = var.aws_region
    ecr_registry                = local.ecr_registry
    app_name                    = var.app_name
    ecr_image_tag               = var.ecr_image_tag
    db_url                      = local.db_url
    db_username                 = var.db_username
    s3_bucket                   = aws_s3_bucket.avatars.id
    avatar_cf_url               = local.avatar_cf_url
    jwt_issuer                  = var.jwt_issuer
    jwt_audience                = var.jwt_audience
    resend_from                 = var.resend_from
    cors_allowed_origins        = var.cors_allowed_origins
    password_reset_frontend_url = var.password_reset_frontend_url
  })

  root_block_device {
    volume_size = 8
    volume_type = "gp2"
  }

  tags = { Name = "${local.name_prefix}-ec2" }

  depends_on = [aws_db_instance.main]
}

# Elastic IP so the address survives stop/start
resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"
  tags     = { Name = "${local.name_prefix}-eip" }
}
