# NEXORA VPN Backend, GCC VPS Nodes & React Admin Dashboard

## 1. Core API Endpoints

- `GET /api/v1/health` — Live PostgreSQL & GCC node status
- `POST /api/v1/auth/register` | `/login` | `/forgot-password` | `/reset-password` | `GET /me` | `POST /logout`
- `GET /api/v1/servers` | `GET /api/v1/servers/best` — Online GCC WireGuard VPS nodes only
- `POST /api/v1/vpn/connect` | `/disconnect` | `GET /status` | `GET/POST /config`
- `GET /api/v1/subscriptions/me` | `POST /api/v1/subscriptions/verify` — Real Google Play Billing `subscriptionsv2` verification
- `POST /api/v1/admin/login` | `GET /overview` | `GET/PATCH /users` | `GET/POST/DELETE /servers` | `GET /errors`
- **Admin Dashboard UI**: Served automatically at `https://<your-domain>/admin` once `admin-dashboard` is built into `backend/public-admin`.

## 2. External HTTPS Hosting Requirements

1. **Control Plane Server** (Ubuntu/Debian VPS with Docker + Caddy SSL, or Render / Fly.io `dxb` / Railway) + **PostgreSQL 16**.
2. **Environment Variables** (`backend/.env` from `backend/.env.example`):
   - `DATABASE_URL`, `DATABASE_SSL=true`, `API_DOMAIN`, `API_PUBLIC_URL`, `ENFORCE_HTTPS=true`
   - `JWT_SECRET`, `ADMIN_API_KEY`, `NODE_AGENT_SECRET`
   - `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` (for Play Console receipt validation)
   - `RESEND_API_KEY` or `SMTP_WEBHOOK_URL` (for Password Reset OTP email delivery)
3. **8 Real GCC WireGuard VPS Servers** (`SA01`, `SA02`, `AE01`, `AE02`, `BH01`, `QA01`, `KW01`, `OM01`):
   - Ubuntu 22.04+/Debian 12 with IPv4 + IPv6, UDP `51820`, TCP `51821` (restricted to Control Plane IP).
   - Run `scripts/setup-wg-vps.sh` on each VPS (automatically enables BBR tuning, IPv4+IPv6 NAT, firewall, systemd `nexora-node-agent`, and registers the node with PostgreSQL).

## 3. Build React Admin Dashboard & Deploy

```bash
npm --prefix admin-dashboard install && npm --prefix admin-dashboard run build
cd backend
sudo API_DOMAIN=api.yourdomain.com bash scripts/deploy-control-plane.sh
```
