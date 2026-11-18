#!/usr/bin/env bash
set -euo pipefail

# NEXORA VPN - Real WireGuard IPv4+IPv6 VPS Provisioning Script (Ubuntu / Debian)
# Includes umask 077 key generation, TCP BBR tuning, firewall rules, TLS/private node-agent transport, and systemd service.
# Usage (Recommended HTTPS mode with Caddy TLS in front of localhost node-agent):
#   sudo NODE_CODE=SA01 SUBNET_V4=10.8.1.1/24 SUBNET_V6=fd80:4e58:5201::1/64 \
#     AGENT_DOMAIN=sa01-agent.yourdomain.com \
#     CONTROL_PLANE_URL=https://api.yourdomain.com ADMIN_SECRET=your_admin_secret \
#     NODE_AGENT_SECRET=your_agent_secret bash setup-wg-vps.sh
# Usage (Private WireGuard/Tailscale management network mode):
#   sudo NODE_CODE=SA01 PRIVATE_AGENT_HOST=100.64.0.10 PRIVATE_AGENT_URL=http://100.64.0.10:51821 \
#     CONTROL_PLANE_URL=https://api.yourdomain.com ADMIN_SECRET=your_admin_secret \
#     NODE_AGENT_SECRET=your_agent_secret bash setup-wg-vps.sh

NODE_CODE="${NODE_CODE:?Set NODE_CODE (e.g. SA01, SA02, AE01, AE02, BH01, QA01, KW01, OM01)}"
SUBNET_V4="${SUBNET_V4:-10.8.1.1/24}"
SUBNET_V6="${SUBNET_V6:-fd80:4e58:5201::1/64}"
WG_PORT="${WG_PORT:-51820}"
AGENT_PORT="${AGENT_PORT:-51821}"
AGENT_DOMAIN="${AGENT_DOMAIN:-}"
PRIVATE_AGENT_HOST="${PRIVATE_AGENT_HOST:-}"
PRIVATE_AGENT_URL="${PRIVATE_AGENT_URL:-}"
ALLOW_INSECURE_HTTP_AGENT="${ALLOW_INSECURE_HTTP_AGENT:-false}"
NODE_AGENT_SECRET="${NODE_AGENT_SECRET:?Set NODE_AGENT_SECRET}"
ADMIN_SECRET="${ADMIN_SECRET:-${ADMIN_API_KEY:-}}"
CONTROL_PLANE_IP="${CONTROL_PLANE_IP:-}"
PUBLIC_IP="$(curl -4 -s https://api.ipify.org)"
WAN_IFACE="$(ip route show default | awk '/default/ {print $5; exit}')"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [ -n "${AGENT_DOMAIN}" ]; then
  AGENT_BIND_HOST="127.0.0.1"
  REGISTERED_AGENT_URL="https://${AGENT_DOMAIN}"
elif [ -n "${PRIVATE_AGENT_URL}" ]; then
  AGENT_BIND_HOST="${PRIVATE_AGENT_HOST:-127.0.0.1}"
  REGISTERED_AGENT_URL="${PRIVATE_AGENT_URL}"
elif [ "${ALLOW_INSECURE_HTTP_AGENT}" = "true" ]; then
  echo "WARNING: Registering unencrypted HTTP node agent on public IP; use only with IP-restricted firewall." >&2
  AGENT_BIND_HOST="0.0.0.0"
  REGISTERED_AGENT_URL="http://${PUBLIC_IP}:${AGENT_PORT}"
else
  echo "ERROR: Refusing unencrypted public HTTP node-agent registration." >&2
  echo "Set AGENT_DOMAIN=node.yourdomain.com (recommended HTTPS via Caddy) or PRIVATE_AGENT_URL=http://<private-mesh-ip>:51821." >&2
  exit 1
fi

apt-get update && apt-get install -y wireguard iptables curl nodejs ufw

# Kernel throughput, stability & dual-stack forwarding optimizations
cat > /etc/sysctl.d/99-nexora-wg.conf <<EOF
net.ipv4.ip_forward = 1
net.ipv6.conf.all.forwarding = 1
net.core.default_qdisc = fq
net.ipv4.tcp_congestion_control = bbr
net.core.rmem_max = 16777216
net.core.wmem_max = 16777216
EOF
sysctl --system

mkdir -p /etc/wireguard
chmod 700 /etc/wireguard

if [ ! -f /etc/wireguard/server_private.key ]; then
  (
    umask 077
    wg genkey | tee /etc/wireguard/server_private.key | wg pubkey > /etc/wireguard/server_public.key
  )
  chmod 600 /etc/wireguard/server_private.key
fi

PRIV_KEY="$(cat /etc/wireguard/server_private.key)"
PUB_KEY="$(cat /etc/wireguard/server_public.key)"

cat > /etc/wireguard/wg0.conf <<EOF
[Interface]
PrivateKey = ${PRIV_KEY}
Address = ${SUBNET_V4}, ${SUBNET_V6}
ListenPort = ${WG_PORT}
MTU = 1420
SaveConfig = false
PostUp = iptables -A FORWARD -i %i -j ACCEPT; iptables -t nat -A POSTROUTING -o ${WAN_IFACE} -j MASQUERADE; ip6tables -A FORWARD -i %i -j ACCEPT; ip6tables -t nat -A POSTROUTING -o ${WAN_IFACE} -j MASQUERADE
PostDown = iptables -D FORWARD -i %i -j ACCEPT; iptables -t nat -D POSTROUTING -o ${WAN_IFACE} -j MASQUERADE; ip6tables -D FORWARD -i %i -j ACCEPT; ip6tables -t nat -D POSTROUTING -o ${WAN_IFACE} -j MASQUERADE
EOF
chmod 600 /etc/wireguard/wg0.conf

systemctl enable wg-quick@wg0
systemctl restart wg-quick@wg0

# Configure firewall
ufw allow 22/tcp >/dev/null || true
ufw allow "${WG_PORT}/udp" >/dev/null || true
if [ -n "${AGENT_DOMAIN}" ]; then
  apt-get install -y caddy
  cat > /etc/caddy/Caddyfile <<EOF
${AGENT_DOMAIN} {
  reverse_proxy 127.0.0.1:${AGENT_PORT}
}
EOF
  systemctl enable caddy
  systemctl restart caddy
  ufw allow 80/tcp >/dev/null || true
  ufw allow 443/tcp >/dev/null || true
elif [ -n "${CONTROL_PLANE_IP}" ]; then
  ufw allow from "${CONTROL_PLANE_IP}" to any port "${AGENT_PORT}" proto tcp >/dev/null || true
fi
ufw --force enable >/dev/null || true

# Install persistent systemd service for WireGuard Node Agent
cat > /etc/systemd/system/nexora-node-agent.service <<EOF
[Unit]
Description=NEXORA VPN WireGuard Node Agent (${NODE_CODE})
After=network-online.target wg-quick@wg0.service

[Service]
Type=simple
Environment=NODE_AGENT_HOST=${AGENT_BIND_HOST}
Environment=NODE_AGENT_PORT=${AGENT_PORT}
Environment=NODE_AGENT_SECRET=${NODE_AGENT_SECRET}
Environment=WG_INTERFACE=wg0
ExecStart=/usr/bin/node ${BACKEND_DIR}/node-agent/agent.js
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable nexora-node-agent
systemctl restart nexora-node-agent

if [ -n "${CONTROL_PLANE_URL:-}" ] && [ -n "${ADMIN_SECRET}" ]; then
  curl -fsS -X POST "${CONTROL_PLANE_URL%/}/api/admin/nodes/${NODE_CODE}/activate" \
    -H "Content-Type: application/json" \
    -H "x-admin-key: ${ADMIN_SECRET}" \
    -d "{\"publicIp\":\"${PUBLIC_IP}\",\"serverPort\":${WG_PORT},\"endpoint\":\"${PUBLIC_IP}:${WG_PORT}\",\"serverPublicKey\":\"${PUB_KEY}\",\"agentUrl\":\"${REGISTERED_AGENT_URL}\"}"
fi

echo "Provisioned ${NODE_CODE} at ${PUBLIC_IP}:${WG_PORT}"
echo "Agent URL: ${REGISTERED_AGENT_URL}"
echo "Server Public Key: ${PUB_KEY}"
