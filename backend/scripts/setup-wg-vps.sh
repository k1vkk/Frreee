#!/usr/bin/env bash
set -euo pipefail

# NEXORA VPN - Real WireGuard IPv4+IPv6 VPS Provisioning Script (Ubuntu / Debian)
# Includes TCP BBR throughput optimization, firewall rules, and systemd node-agent service.
# Usage:
#   sudo NODE_CODE=SA01 SUBNET_V4=10.8.1.1/24 SUBNET_V6=fd80:4e58:5201::1/64 \
#     CONTROL_PLANE_URL=https://api.yourdomain.com ADMIN_API_KEY=your_admin_key \
#     NODE_AGENT_SECRET=your_agent_secret bash setup-wg-vps.sh

NODE_CODE="${NODE_CODE:?Set NODE_CODE (e.g. SA01, SA02, AE01, AE02, BH01, QA01, KW01, OM01)}"
SUBNET_V4="${SUBNET_V4:-10.8.1.1/24}"
SUBNET_V6="${SUBNET_V6:-fd80:4e58:5201::1/64}"
WG_PORT="${WG_PORT:-51820}"
AGENT_PORT="${AGENT_PORT:-51821}"
NODE_AGENT_SECRET="${NODE_AGENT_SECRET:?Set NODE_AGENT_SECRET}"
CONTROL_PLANE_IP="${CONTROL_PLANE_IP:-}"
PUBLIC_IP="$(curl -4 -s https://api.ipify.org)"
WAN_IFACE="$(ip route show default | awk '/default/ {print $5; exit}')"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

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

if [ ! -f /etc/wireguard/privatekey ]; then
  wg genkey | tee /etc/wireguard/privatekey | wg pubkey > /etc/wireguard/publickey
  chmod 600 /etc/wireguard/privatekey
fi

PRIV_KEY="$(cat /etc/wireguard/privatekey)"
PUB_KEY="$(cat /etc/wireguard/publickey)"

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

systemctl enable wg-quick@wg0
systemctl restart wg-quick@wg0

# Configure firewall
ufw allow 22/tcp >/dev/null || true
ufw allow "${WG_PORT}/udp" >/dev/null || true
if [ -n "${CONTROL_PLANE_IP}" ]; then
  ufw allow from "${CONTROL_PLANE_IP}" to any port "${AGENT_PORT}" proto tcp >/dev/null || true
else
  ufw allow "${AGENT_PORT}/tcp" >/dev/null || true
fi
ufw --force enable >/dev/null || true

# Install persistent systemd service for WireGuard Node Agent
cat > /etc/systemd/system/nexora-node-agent.service <<EOF
[Unit]
Description=NEXORA VPN WireGuard Node Agent (${NODE_CODE})
After=network-online.target wg-quick@wg0.service

[Service]
Type=simple
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

if [ -n "${CONTROL_PLANE_URL:-}" ] && [ -n "${ADMIN_API_KEY:-}" ]; then
  curl -fsS -X POST "${CONTROL_PLANE_URL%/}/api/v1/admin/nodes/${NODE_CODE}/activate" \
    -H "Content-Type: application/json" \
    -H "x-admin-key: ${ADMIN_API_KEY}" \
    -d "{\"endpoint\":\"${PUBLIC_IP}:${WG_PORT}\",\"serverPublicKey\":\"${PUB_KEY}\",\"agentUrl\":\"http://${PUBLIC_IP}:${AGENT_PORT}\"}"
fi

echo "Provisioned ${NODE_CODE} at ${PUBLIC_IP}:${WG_PORT} (BBR + IPv4/IPv6 + systemd node-agent active)"
