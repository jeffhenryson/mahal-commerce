output "alb_dns_name" {
  description = "ALB DNS name — point your domain's CNAME/ALIAS here"
  value       = aws_lb.main.dns_name
}

output "ecr_repository_url" {
  description = "ECR repository URL for docker push"
  value       = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  description = "ECS service name"
  value       = aws_ecs_service.app.name
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint"
  value       = aws_db_instance.main.endpoint
  sensitive   = true
}

output "redis_endpoint" {
  description = "ElastiCache Redis primary endpoint"
  value       = aws_elasticache_replication_group.main.primary_endpoint_address
  sensitive   = true
}

output "avatar_bucket_name" {
  description = "S3 bucket name for avatars"
  value       = aws_s3_bucket.avatars.id
}

output "avatar_cdn_url" {
  description = "CloudFront CDN URL for avatar bucket (use as AVATAR_BASE_URL)"
  value       = "https://${aws_cloudfront_distribution.avatars.domain_name}"
}

output "secrets_to_populate" {
  description = "Secrets Manager secret names that must be populated before the first deploy"
  value       = { for k, v in aws_secretsmanager_secret.app : k => v.name }
}
