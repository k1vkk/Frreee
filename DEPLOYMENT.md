# NEXORA VPN — Complete Production Deployment Guide

Follow these steps to deploy the PostgreSQL database, Node.js HTTPS control plane, real WireGuard GCC VPS servers, Google Play Billing, SpeedOf.Me, and the Android app.

---

## 1. Prerequisites & Required Accounts
- **PostgreSQL 15+** database (Railway, Render, Neon, Supabase, or self-hosted).
- **Node.js 20/22** hosting with HTTPS domain (Railway, Render, Fly.io, or Ubuntu VPS + Caddy).
- **Linux VPS servers** (Ubuntu 22.04/24.04 with root SSH & public IPv4/IPv6) in target GCC regions (`SA01`, `SA02`, `AE01`, `AE02`, `BH01`, `QA01`, `KW01`, `OM01`).
- **Google Play Console** account (for Google Play Billing product IDs and service account JSON).
- **SpeedOf.Me API account** (API Key + registered domain) — optional if you prefer automatic Cloudflare Edge HTTPS throughput probing.

---

## 2. Database Setup (PostgreSQL)
Apply the schema and seed the 8 GCC server slots (`SA01`..`OM01`, initially `pending_vps`):

```bash
psql "$DATABASE_URL" -f backend/db/schema.sql
psql "$DATABASE_URL" -f backend/db/seed.sql
```

---

## 3. Deploy the Control Plane Backend (HTTPS)

### Option A: Railway / Render / Fly.io
Deployment manifests are included in `backend/railway.json`, `backend/render.yaml`, `backend/fly.toml`, and `backend/Dockerfile`.

Set these environment variables on your host:
- `DATABASE_URL` — PostgreSQL connection string (`postgres://...`)
- `JWT_SECRET` — strong random 64-byte hex string (`openssl rand -hex 32`)
- `ADMIN_SECRET` — admin dashboard API key
- `NODE_AGENT_SECRET` — shared secret used by WireGuard VPS agents to push telemetry & receive peer commands
- `PORT` — `8080`
- `PUBLIC_API_URL` — e.g. `https://api.your-nexora-domain.com`
- `GOOGLE_PLAY_SERVICE_ACCOUNT` — raw JSON or path of your Play Console service account (for subscription verification)
- `SPEEDOFME_API_KEY` & `SPEEDOFME_DOMAIN` — optional backend-delivered SpeedOf.Me credentials
- `RESEND_API_KEY` & `EMAIL_FROM` — optional transactional email for password reset OTPs

### Option B: Ubuntu VPS + Docker + Caddy Automatic HTTPS
1. Copy `backend/.env.example` to `backend/.env` and fill all secrets.
2. Update your domain in `backend/Caddyfile`.
3. Run:
   ```bash
   cd backend
   docker compose up -d --build
   ```
4. Create an initial admin account:
   ```bash
   node backend/scripts/create-admin.js admin@yourdomain.com "StrongPassword123!"
   ```
5. Verify your real HTTPS URL responds:
   ```bash
   curl https://api.your-nexora-domain.com/api/health
   ```

---

## 4. Provision Real WireGuard GCC VPS Nodes
Do **NOT** invent server IPs or keys. On each real Linux VPS you rent:

1. Open firewall ports: **UDP 51820** (WireGuard) and **TCP 8787** (restricted to your backend IP for node agent).
2. Run the automated WireGuard + Node Agent installer:
   ```bash
   sudo bash backend/scripts/setup-wg-vps.sh \
     --node-code SA01 \
     --api-url https://api.your-nexora-domain.com \
     --agent-secret "<YOUR_NODE_AGENT_SECRET>"
   ```
3. The script installs `wireguard`, generates Curve25519 keys via `wg genkey`/`wg pubkey`, enables IPv4/IPv6 NAT forwarding (`sysctl` + `iptables`/`ip6tables`), starts `wg0`, and runs `backend/node-agent/agent.js` as a systemd service.
4. The node automatically registers its real public IP, WireGuard public key, and telemetry into PostgreSQL, switching its status from `pending_vps` to `online`.

---

## 5. Admin Dashboard (React SPA)
Build the admin dashboard (already bundled into `backend/public-admin/`):
```bash
cd admin-dashboard
npm install
npm run build
```
Visit `https://api.your-nexora-domain.com/admin` and sign in using your `ADMIN_SECRET` (or admin JWT).

---

## 6. Configure Android Secrets & URL
1. Copy `.env.example` to `.env` locally (or configure in **AI Studio Secrets** / GitHub Secrets).
2. Set:
   - `NEXORA_API_BASE_URL=https://api.your-nexora-domain.com`
   - `SPEEDOFME_API_KEY=...` (if using SpeedOf.Me)
   - `SPEEDOFME_DOMAIN=...`
   *(Users can also change `NEXORA_API_BASE_URL` live at runtime inside the Android app under **Settings -> Backend API Base URL**.)*

---

## 7. Google Play Billing Setup
Create three subscription products in **Google Play Console -> Monetize -> Subscriptions** matching `BillingManager.kt`:
- `nexora_pro_monthly`
- `nexora_pro_annual`
- `nexora_pro_premium`

Attach a Google Cloud Service Account JSON with **Android Publisher** permissions and paste it into `GOOGLE_PLAY_SERVICE_ACCOUNT` on the backend.

---

## 8. Building Android APK & Release Bundle
- **Debug APK & Tests**:
  ```bash
  gradle :app:testDebugUnitTest :app:assembleDebug
  ```
- **Signed Release APK & Play Store AAB**:
  ```bash
  export KEYSTORE_PATH=/path/to/upload-key.jks
  export STORE_PASSWORD=...
  export KEY_PASSWORD=...
  gradle :app:assembleRelease :app:bundleRelease
  ```
- Automated CI/CD pipelines are ready under `.github/workflows/android-build.yml` and `.github/workflows/backend-deploy.yml`.
