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

### Option A: Fly.io (`dxb` Recommended) + External Supabase PostgreSQL
Deployment manifests are included in `backend/fly.toml`, `backend/railway.json`, `backend/render.yaml`, and `backend/Dockerfile`.

Set these environment variables / secrets on your host:
- `NODE_ENV` — `production` (required in production)
- `PORT` — `4000`
- `ENFORCE_HTTPS` — `true` (required in production)
- `CORS_ORIGIN` — explicit HTTPS origin(s), e.g. `https://nexora-vpn-api.fly.dev` (never `*` in production)
- `DATABASE_URL` — Supabase **Session Pooler** IPv4 connection string (`postgresql://...pooler.supabase.com:5432/postgres`)
- `DATABASE_SSL` — `true` (required for external/Supabase PostgreSQL; use `false` only for private Docker internal network)
- `DATABASE_SSL_REJECT_UNAUTHORIZED` — `true` (certificate verification required in production)
- `DATABASE_CA_CERT` — inline PEM certificate or file path for managed/Supabase PostgreSQL TLS verification
- `JWT_SECRET` — strong random 64-char hex string (`openssl rand -hex 32`)
- `ADMIN_SECRET` / `ADMIN_API_KEY` — strong admin dashboard secret (`openssl rand -hex 32`)
- `NODE_AGENT_SECRET` — shared secret used by WireGuard VPS agents (`openssl rand -hex 32`)
- `API_PUBLIC_URL` — e.g. `https://nexora-vpn-api.fly.dev`
- `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` — raw JSON or path of your Play Console service account (for subscription verification)
- `SPEEDOFME_API_KEY` & `SPEEDOFME_DOMAIN` — optional backend-delivered SpeedOf.Me credentials
- `RESEND_API_KEY` & `MAIL_FROM` — optional transactional email for password reset OTPs

#### Deploying on Fly.io (`primary_region = "dxb"`)
```bash
cd backend
fly apps create nexora-vpn-api   # or use your own app name in fly.toml
fly secrets set \
  DATABASE_URL="postgresql://..." \
  DATABASE_CA_CERT="$(cat supabase-ca.crt)" \
  JWT_SECRET="$(openssl rand -hex 32)" \
  ADMIN_SECRET="$(openssl rand -hex 32)" \
  NODE_AGENT_SECRET="$(openssl rand -hex 32)" \
  CORS_ORIGIN="https://nexora-vpn-api.fly.dev" \
  API_PUBLIC_URL="https://nexora-vpn-api.fly.dev"
fly deploy
```
`backend/fly.toml` executes `release_command = "npm run migrate"` automatically before routing traffic.

#### Deploying on Railway or Render
- **Railway**: Set Service **Root Directory** to `backend` (`backend/railway.json` runs `preDeployCommand: ["npm run migrate"]` and builds `backend/Dockerfile`). Configure the same required environment variables above in Railway Variables.
- **Render**: Set Service **Root Directory** to `backend` (`backend/render.yaml` uses external Supabase `DATABASE_URL` via `sync: false`, `preDeployCommand: npm run migrate`, and prompts for `CORS_ORIGIN`, `API_PUBLIC_URL`, and `DATABASE_CA_CERT`).

For manual migration checks before starting:
```bash
cd backend && npm run migrate
```

### Option B: Ubuntu VPS + Docker + Caddy Automatic HTTPS
1. Copy `backend/.env.example` to `backend/.env` and fill all secrets (including explicit HTTPS `CORS_ORIGIN`).
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
   curl https://api.your-nexora-domain.com/api/v1/health
   ```

---

## 4. Provision Real WireGuard GCC VPS Nodes
Do **NOT** invent server IPs or keys. On each real Linux VPS you rent:

1. Open firewall ports: **UDP 51820** (WireGuard) and **TCP 443 / 80** (for Caddy HTTPS TLS termination in front of localhost node agent on **TCP 51821**), OR communicate over a private WireGuard/Tailscale mesh (`TCP 51821` bound to the private tunnel IP).
2. Run the automated WireGuard + Node Agent installer using environment variables (recommended TLS mode via `AGENT_DOMAIN`):
   ```bash
   sudo NODE_CODE=SA01 \
     SUBNET_V4=10.8.1.1/24 \
     SUBNET_V6=fd80:4e58:5201::1/64 \
     AGENT_DOMAIN=sa01-agent.your-nexora-domain.com \
     CONTROL_PLANE_URL=https://api.your-nexora-domain.com \
     ADMIN_SECRET="<YOUR_ADMIN_SECRET>" \
     NODE_AGENT_SECRET="<YOUR_NODE_AGENT_SECRET>" \
     bash backend/scripts/setup-wg-vps.sh
   ```
3. The script installs `wireguard`, generates Curve25519 keys via `wg genkey`/`wg pubkey`, enables IPv4/IPv6 NAT forwarding (`sysctl` + `iptables`/`ip6tables`), starts `wg0`, binds `backend/node-agent/agent.js` on `127.0.0.1:51821` behind Caddy HTTPS (`https://<AGENT_DOMAIN>`), and keeps `NODE_AGENT_SECRET` encrypted in transit.
4. The node registers its real public IP, WireGuard public key, and HTTPS `agentUrl` into PostgreSQL, switching its status from `pending_vps` to `online` once verified by the control plane. *(Live multi-VPS TLS connectivity must be verified after provisioning your first real Ubuntu VPS.)*

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
