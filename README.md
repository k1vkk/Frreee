# NEXORA VPN — Production Architecture & Repository Guide

NEXORA VPN is a complete, genuine WireGuard VPN platform built without fake tunnels, mock servers, simulated pings, or hardcoded credentials.

## Architecture Overview

1. **Android Client (`app/`)**
   - **UI & Architecture**: Kotlin, Jetpack Compose, Material 3, MVVM (`StateFlow`), Room v4 + SQL migrations, DataStore + Android Keystore AES-256-GCM encryption for JWT and WireGuard private keys, Bilingual Arabic (RTL) & English (LTR) switching.
   - **Real VPN Engine**: Official `com.wireguard.android:tunnel` (`GoBackend`) with `VpnService`, Curve25519 key generation/rotation, 12-second real handshake & RX traffic verification, IPv4 + IPv6 dual-stack routing, strict DNS leak protection, Kill Switch / Always-On VPN support, Per-App Split Tunneling, network-switch Auto-Reconnect, Auto-Failover, and Battery optimization telemetry.
   - **Diagnostics**: Multi-sample TCP/ICMP Ping + Jitter + Packet Loss probe (`NetworkDiagnostics`) + Speed Test (`SpeedOf.Me` official JS API via WebView bridge, with automatic `Cloudflare Edge HTTPS` real throughput fallback when SpeedOf.Me keys are not configured).
   - **10 Screens**: Home Dashboard, VPN Server Selection, Real-Time Connection Status, Speed Test, Ping Test, Connection History, Subscription Plans (Google Play Billing: `nexora_pro_monthly`, `nexora_pro_annual`, `nexora_pro_premium`), User Account, Settings, Support & Diagnostics Center.

2. **Control Plane Backend (`backend/`)**
   - Node.js + Express + PostgreSQL + JWT Auth + RBAC + Google Play Billing Developer API receipt verifier + Rate Limiting + Helmet + Telemetry & Support Tickets.
   - Hosts the 8 GCC server infrastructure slots (`SA01`, `SA02`, `AE01`, `AE02`, `BH01`, `QA01`, `KW01`, `OM01`) cleanly initialized as `pending_vps` until provisioned by real VPS agents.

3. **WireGuard VPS Node Agent (`backend/node-agent/` & `backend/scripts/`)**
   - Automated Ubuntu/Debian WireGuard provisioning (`setup-wg-vps.sh`), Curve25519 keygen (`generate-keys.sh`), and live node agent (`agent.js`) that configures real Linux `wg0` peers and pushes CPU, RAM, active peers, and traffic telemetry to the control plane.

4. **React Admin Dashboard (`admin-dashboard/`)**
   - Built with Vite + React (`npm run build` outputs directly to `backend/public-admin/`). Manage users, subscriptions, GCC server inventory (`isActive` toggles, add/delete), real-time CPU/RAM/load telemetry, connection logs, and system error logs.

---

See **[DEPLOYMENT.md](./DEPLOYMENT.md)** for full step-by-step instructions to deploy PostgreSQL, the HTTPS Node.js backend, real GCC VPS nodes, Google Play Billing, SpeedOf.Me, and Android APK/AAB builds.
