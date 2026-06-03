#!/bin/bash
# Runs once at first EC2 boot.
# Terraform injects static values at apply time; secrets are pulled from SSM at runtime.
set -euo pipefail
exec > >(tee /var/log/user-data.log | logger -t user-data) 2>&1

# ── Terraform-injected static values ─────────────────────────────────────────
NAME_PREFIX="${name_prefix}"
AWS_REGION="${aws_region}"
ECR_REGISTRY="${ecr_registry}"
APP_NAME="${app_name}"
IMAGE_TAG="${ecr_image_tag}"
DB_URL="${db_url}"
DB_USERNAME="${db_username}"
S3_BUCKET="${s3_bucket}"
AVATAR_CF_URL="${avatar_cf_url}"
JWT_ISSUER="${jwt_issuer}"
JWT_AUDIENCE="${jwt_audience}"
RESEND_FROM="${resend_from}"
CORS_ORIGINS="${cors_allowed_origins}"
RESET_URL="${password_reset_frontend_url}"

# ── System setup ──────────────────────────────────────────────────────────────
dnf update -y
dnf install -y docker

systemctl enable --now docker
usermod -aG docker ec2-user

# Docker Compose v2 plugin
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL \
  "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 1 GB swap — prevents OOM kills on t2.micro (1 GB RAM + Redis + Spring Boot)
fallocate -l 1G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# ── Pull secrets from SSM Parameter Store ────────────────────────────────────
ssm() {
  aws ssm get-parameter \
    --region "$AWS_REGION" \
    --name "/$NAME_PREFIX/$1" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text
}

DB_PASSWORD=$(ssm db-password)
JWT_SECRET=$(ssm jwt-secret)
TOTP_KEY=$(ssm totp-encryption-key)
RESEND_KEY=$(ssm resend-api-key)
REDIS_PASSWORD=$(ssm redis-password)

# ── App directory ─────────────────────────────────────────────────────────────
mkdir -p /opt/app
chown ec2-user:docker /opt/app
chmod 750 /opt/app

# .env — only the 5 secrets that must not appear in docker-compose.yml
cat > /opt/app/.env <<EOF
DB_PASSWORD=$DB_PASSWORD
JWT_SECRET=$JWT_SECRET
TOTP_ENCRYPTION_KEY=$TOTP_KEY
RESEND_API_KEY=$RESEND_KEY
REDIS_PASSWORD=$REDIS_PASSWORD
EOF
chmod 600 /opt/app/.env
chown ec2-user:docker /opt/app/.env

# docker-compose.yml — non-secret env vars are embedded directly;
# secrets come from env_file so they don't appear in the compose file.
cat > /opt/app/docker-compose.yml <<EOF
version: "3.9"

services:
  redis:
    image: redis:7-alpine
    restart: always
    command: >
      redis-server
      --requirepass $REDIS_PASSWORD
      --maxmemory 64mb
      --maxmemory-policy allkeys-lru
      --save 60 1
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "$REDIS_PASSWORD", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - app

  app:
    image: $ECR_REGISTRY/$APP_NAME:$IMAGE_TAG
    restart: always
    mem_limit: 600m
    depends_on:
      redis:
        condition: service_healthy
    ports:
      - "8080:8080"
    env_file: /opt/app/.env
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: "$DB_URL"
      DB_USERNAME: "$DB_USERNAME"
      REDIS_HOST: redis
      REDIS_PORT: "6379"
      REDIS_SSL: "false"
      AVATAR_STORAGE_TYPE: s3
      AVATAR_S3_BUCKET: "$S3_BUCKET"
      AVATAR_S3_REGION: "$AWS_REGION"
      AVATAR_S3_PUBLIC_URL_BASE: "$AVATAR_CF_URL"
      JWT_ISSUER: "$JWT_ISSUER"
      JWT_AUDIENCE: "$JWT_AUDIENCE"
      RESEND_FROM: "$RESEND_FROM"
      CORS_ALLOWED_ORIGINS: "$CORS_ORIGINS"
      PASSWORD_RESET_FRONTEND_URL: "$RESET_URL"
      JAVA_OPTS: >-
        -XX:MaxRAMPercentage=65.0
        -XX:+UseContainerSupport
        -XX:+UseG1GC
        -Djava.security.egd=file:/dev/./urandom
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health/liveness | grep -q UP || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 90s
    networks:
      - app

volumes:
  redis_data:

networks:
  app:
EOF
chmod 640 /opt/app/docker-compose.yml
chown ec2-user:docker /opt/app/docker-compose.yml

# ── Deploy script — run after pushing a new image to ECR ─────────────────────
cat > /opt/app/deploy.sh <<DEPLOY
#!/bin/bash
set -euo pipefail
cd /opt/app
aws ecr get-login-password --region $AWS_REGION \\
  | docker login --username AWS --password-stdin $ECR_REGISTRY
docker compose pull app
docker compose up -d --no-deps app
echo "Deploy complete: \$(date)"
DEPLOY
chmod +x /opt/app/deploy.sh
chown ec2-user:docker /opt/app/deploy.sh

# ── Systemd unit — auto-start on reboot ──────────────────────────────────────
cat > /etc/systemd/system/app.service <<SERVICE
[Unit]
Description=Security Spring (Docker Compose)
After=docker.service network-online.target
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/app
ExecStart=/usr/bin/docker compose up -d
ExecStop=/usr/bin/docker compose down
TimeoutStartSec=300
User=ec2-user

[Install]
WantedBy=multi-user.target
SERVICE

systemctl daemon-reload
systemctl enable app

# ── Start if image already exists in ECR ──────────────────────────────────────
if aws ecr describe-images \
    --repository-name "$APP_NAME" \
    --region "$AWS_REGION" \
    --image-ids imageTag="$IMAGE_TAG" \
    > /dev/null 2>&1; then
  aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "$ECR_REGISTRY"
  cd /opt/app && docker compose up -d
  echo "App started."
else
  echo "Image $APP_NAME:$IMAGE_TAG not found in ECR yet."
  echo "After pushing your image, run: /opt/app/deploy.sh"
fi
