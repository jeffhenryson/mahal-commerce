output "app_url" {
  description = "Application URL — HTTP on port 8080"
  value       = "http://${aws_eip.app.public_ip}:8080"
}

output "ec2_ssh_command" {
  description = "SSH into the instance"
  value       = "ssh -i ~/.ssh/id_rsa ec2-user@${aws_eip.app.public_ip}"
}

output "ec2_public_ip" {
  description = "Elastic IP (stable across stop/start)"
  value       = aws_eip.app.public_ip
}

output "ecr_repository_url" {
  description = "ECR repository URL"
  value       = aws_ecr_repository.app.repository_url
}

output "ecr_push_commands" {
  description = "Commands to build and push the Docker image"
  value       = <<-EOT
    # 1. Authenticate
    aws ecr get-login-password --region ${var.aws_region} | \
      docker login --username AWS --password-stdin ${aws_ecr_repository.app.repository_url}

    # 2. Build (run from repo root)
    docker build -t ${var.app_name} ../

    # 3. Tag & push
    docker tag ${var.app_name}:latest ${aws_ecr_repository.app.repository_url}:latest
    docker push ${aws_ecr_repository.app.repository_url}:latest

    # 4. Deploy on EC2
    ssh ec2-user@${aws_eip.app.public_ip} '/opt/app/deploy.sh'
  EOT
}

output "rds_endpoint" {
  description = "RDS endpoint (internal VPC only)"
  value       = aws_db_instance.main.address
}

output "cloudfront_domain" {
  description = "CloudFront domain for avatar URLs"
  value       = aws_cloudfront_distribution.avatars.domain_name
}
