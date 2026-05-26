#!/bin/bash
# ============================================
# Test Data Generator - Quick Deploy Script
# For Alibaba Cloud ECS (Ubuntu/CentOS)
# ============================================

set -e

echo "=== Test Data Generator Deployment ==="

# Check Docker
if ! command -v docker &> /dev/null; then
    echo "[ERROR] Docker not found. Install with:"
    echo "  sudo yum install -y yum-utils"
    echo "  sudo yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo"
    echo "  sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin"
    echo "  sudo systemctl enable docker && sudo systemctl start docker"
    exit 1
fi

if ! docker compose version &> /dev/null 2>&1 && ! command -v docker-compose &> /dev/null; then
    echo "[ERROR] Docker Compose not found. Please install docker-compose-plugin:"
    echo "  sudo yum install -y docker-compose-plugin"
    exit 1
fi

# Check .env file
if [ ! -f .env ]; then
    echo "[ERROR] .env file not found. Copy .env.example to .env and fill in values."
    echo "  cp .env.example .env"
    exit 1
fi

# Build and start
echo "[1/3] Building application image..."
docker compose build --no-cache

echo "[2/3] Starting services..."
docker compose up -d

echo "[3/3] Waiting for services to be healthy..."
sleep 10

# Check status
echo ""
echo "=== Service Status ==="
docker compose ps

echo ""
echo "=== Deployment Complete ==="
echo "Access: http://<your-server-ip>"
echo "Logs:   docker compose logs -f app"
