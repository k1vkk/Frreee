CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  display_name TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'moderator', 'admin')),
  is_suspended BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'user';
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_suspended BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS auth_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_label TEXT NOT NULL DEFAULT 'Android Device',
  ip_address TEXT,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_active_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code_hash TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  used BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  plan_tier TEXT NOT NULL DEFAULT 'free' CHECK (plan_tier IN ('free', 'pro_monthly', 'pro_annual')),
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'expired', 'canceled')),
  play_product_id TEXT,
  play_purchase_token TEXT,
  expires_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS vpn_servers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  node_code TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  country_name TEXT NOT NULL,
  country_code CHAR(2) NOT NULL,
  city TEXT NOT NULL,
  endpoint TEXT,
  server_public_key TEXT,
  agent_url TEXT,
  dns_servers TEXT NOT NULL DEFAULT '1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001',
  ipv4_subnet TEXT NOT NULL DEFAULT '10.8.0.0/24',
  ipv6_subnet TEXT NOT NULL DEFAULT 'fd80:4e58:5200::/64',
  tier TEXT NOT NULL DEFAULT 'free' CHECK (tier IN ('free', 'pro')),
  max_peers INTEGER NOT NULL DEFAULT 250,
  active_peers_reported INTEGER NOT NULL DEFAULT 0,
  health_status TEXT NOT NULL DEFAULT 'pending_vps' CHECK (health_status IN ('pending_vps', 'online', 'degraded', 'offline')),
  latency_ms INTEGER,
  last_health_check_at TIMESTAMPTZ,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS wireguard_peers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  server_id UUID NOT NULL REFERENCES vpn_servers(id) ON DELETE CASCADE,
  client_public_key TEXT NOT NULL,
  assigned_ipv4 INET NOT NULL,
  assigned_ipv6 INET NOT NULL,
  is_connected BOOLEAN NOT NULL DEFAULT FALSE,
  rx_bytes BIGINT NOT NULL DEFAULT 0,
  tx_bytes BIGINT NOT NULL DEFAULT 0,
  last_handshake_at TIMESTAMPTZ,
  last_connected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  disconnected_at TIMESTAMPTZ,
  UNIQUE (server_id, client_public_key),
  UNIQUE (server_id, assigned_ipv4),
  UNIQUE (server_id, assigned_ipv6)
);

ALTER TABLE wireguard_peers ADD COLUMN IF NOT EXISTS is_connected BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE wireguard_peers ADD COLUMN IF NOT EXISTS disconnected_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS error_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  level TEXT NOT NULL DEFAULT 'error' CHECK (level IN ('warn', 'error', 'critical')),
  source TEXT NOT NULL DEFAULT 'api',
  endpoint TEXT,
  message TEXT NOT NULL,
  details TEXT,
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  node_code TEXT,
  resolved BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
