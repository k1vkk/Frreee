#!/usr/bin/env bash
set -euo pipefail

# Deploy NEXORA VPN Control Plane + PostgreSQL + Automatic Let's Encrypt HTTPS on Ubuntu/Debian
# Usage:
#   sudo API_DOMAIN=api.yourdomain.com bash scripts/deploy-control-plane.sh

API_DOMAIN="${API_DOMAIN:?Set API_DOMAIN (e.g. api.yourdomain.com) pointing to this server IP}"

if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi

if [ ! -f .env ]; then
  cp .env.example .env
  POSTGRES_PW="$(openssl rand -hex 24)"
  JWT_SEC="$(openssl rand -hex 32)"
  ADMIN_KEY="$(openssl rand -hex 32)"
  AGENT_SEC="$(openssl rand -hex 32)"

  sed -i "s|API_DOMAIN=.*|API_DOMAIN=${API_DOMAIN}|" .env
  sed -i "s|API_PUBLIC_URL=.*|API_PUBLIC_URL=https://${API_DOMAIN}|" .env
  sed -i "s|CORS_ORIGIN=.*|CORS_ORIGIN=https://${API_DOMAIN}|" .env
  sed -i "s|POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=${POSTGRES_PW}|" .env
  sed -i "s|JWT_SECRET=.*|JWT_SECRET=${JWT_SEC}|" .env
  sed -i "s|ADMIN_API_KEY=.*|ADMIN_API_KEY=${ADMIN_KEY}|" .env
  sed -i "s|NODE_AGENT_SECRET=.*|NODE_AGENT_SECRET=${AGENT_SEC}|" .env
  echo "Generated production .env with strong cryptographic keys."
fi

docker compose up -d --build
echo "================================================================"
echo "NEXORA VPN Control Plane deployed!"
echo "HTTPS API URL: https://${API_DOMAIN}/api/v1/health"
echo "================================================================"
