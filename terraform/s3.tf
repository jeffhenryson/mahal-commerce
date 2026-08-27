resource "aws_s3_bucket" "avatars" {
  bucket        = "${local.name_prefix}-avatars"
  force_destroy = var.avatar_bucket_force_destroy

  tags = { Name = "${local.name_prefix}-avatars" }
}

resource "aws_s3_bucket_versioning" "avatars" {
  bucket = aws_s3_bucket.avatars.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "avatars" {
  bucket = aws_s3_bucket.avatars.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "avatars" {
  bucket = aws_s3_bucket.avatars.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "avatars" {
  bucket = aws_s3_bucket.avatars.id

  rule {
    id     = "abort-incomplete-multipart"
    status = "Enabled"

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

# CloudFront distribution for serving avatars publicly via CDN
resource "aws_cloudfront_origin_access_control" "avatars" {
  name                              = "${local.name_prefix}-avatars-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "avatars" {
  origin {
    domain_name              = aws_s3_bucket.avatars.bucket_regional_domain_name
    origin_id                = "s3-avatars"
    origin_access_control_id = aws_cloudfront_origin_access_control.avatars.id
  }

  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = ""
  comment             = "CDN for ${local.name_prefix} avatars"

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "s3-avatars"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    forwarded_values {
      query_string = false
      cookies { forward = "none" }
    }

    min_ttl     = 0
    default_ttl = 86400
    max_ttl     = 31536000
  }

  price_class = "PriceClass_100"

  restrictions {
    geo_restriction { restriction_type = "none" }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = { Name = "${local.name_prefix}-avatars-cdn" }
}

# Grant CloudFront OAC read access to the S3 bucket
resource "aws_s3_bucket_policy" "avatars" {
  bucket = aws_s3_bucket.avatars.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowCloudFrontServicePrincipal"
        Effect = "Allow"
        Principal = {
          Service = "cloudfront.amazonaws.com"
        }
        Action   = "s3:GetObject"
        Resource = "${aws_s3_bucket.avatars.arn}/*"
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.avatars.arn
          }
        }
      }
    ]
  })
}
