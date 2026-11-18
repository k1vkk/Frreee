#!/usr/bin/env bash
set -euo pipefail

# Generate a WireGuard Curve25519 server keypair with strict 0600 permissions.
OUT_DIR="${1:-/etc/wireguard}"
mkdir -p "${OUT_DIR}"
chmod 700 "${OUT_DIR}"

(
  umask 077
  wg genkey | tee "${OUT_DIR}/server_private.key" | wg pubkey > "${OUT_DIR}/server_public.key"
)

echo "Generated keys in ${OUT_DIR}:"
echo "  Private key (keep strictly on VPS): ${OUT_DIR}/server_private.key"
echo "  Public key  (share with API):      $(cat "${OUT_DIR}/server_public.key")"
