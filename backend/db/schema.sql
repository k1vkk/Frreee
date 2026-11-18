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
  plan_tier TEXT NOT NULL DEFAULT 'free',
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'expired', 'canceled')),
  play_product_id TEXT,
  play_purchase_token TEXT,
  expires_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS subscriptions_plan_tier_check;
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_plan_tier_check
  CHECK (plan_tier IN ('free', 'pro_monthly', 'pro_annual', 'pro_premium'));

CREATE TABLE IF NOT EXISTS vpn_servers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  node_code TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  country_name TEXT NOT NULL,
  country_code CHAR(2) NOT NULL,
  city TEXT NOT NULL,
  public_ip TEXT,
  server_port INTEGER NOT NULL DEFAULT 51820,
  endpoint TEXT,
  server_public_key TEXT,
  agent_url TEXT,
  dns_servers TEXT NOT NULL DEFAULT '1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001',
  ipv4_subnet TEXT NOT NULL DEFAULT '10.8.0.0/24',
  ipv6_subnet TEXT NOT NULL DEFAULT 'fd80:4e58:5200::/64',
  tier TEXT NOT NULL DEFAULT 'free' CHECK (tier IN ('free', 'pro')),
  max_peers INTEGER NOT NULL DEFAULT 250,
  active_peers_reported INTEGER NOT NULL DEFAULT 0,
  cpu_usage REAL NOT NULL DEFAULT 0,
  ram_usage REAL NOT NULL DEFAULT 0,
  network_rx_bytes BIGINT NOT NULL DEFAULT 0,
  network_tx_bytes BIGINT NOT NULL DEFAULT 0,
  packet_loss_percent REAL NOT NULL DEFAULT 0,
  jitter_ms INTEGER,
  health_status TEXT NOT NULL DEFAULT 'pending_vps' CHECK (health_status IN ('pending_vps', 'online', 'degraded', 'offline')),
  latency_ms INTEGER,
  last_health_check_at TIMESTAMPTZ,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE vpn_servers ADD COLUMN IF NOT EXISTS public_ip TEXT;
ALTER TABLE vpn_servers ADD COLUMN IF NOT EXISTS server_port INTEGER NOT NULL DEFAULT 51820;
ALTER TABLE vpn_servers ADD COLUMN IF NOT EXISTS cpu_usage REAL NOT NULL DEFAULT 0;
ALTER TABLE vpn_servers ADD COLUMN IF NOT EXISTS ram_usage REAL NOT NULL DEFAULT 0;
ALTER TABLE vpn_servers ADD COLUMN IF NOT EXISTS network_rx_bytes BIGINT NOT NULL DEFAULT 0;
ALTER TABLE vpn_servers ADD COLUMN IF NOT EXISTS network_tx_bytes BIGINT NOT NULL DEFAULT 0;
ALTER TABLE vpn_servers ADD COLUMN IF NOT EXISTS packet_loss_percent REAL NOT NULL DEFAULT 0;
ALTER TABLE vpn_servers ADD COLUMN IF NOT EXISTS jitter_ms INTEGER;

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

CREATE TABLE IF NOT EXISTS connection_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  server_id UUID NOT NULL REFERENCES vpn_servers(id) ON DELETE CASCADE,
  node_code TEXT NOT NULL,
  client_public_key TEXT NOT NULL,
  assigned_ipv4 TEXT,
  assigned_ipv6 TEXT,
  connected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  disconnected_at TIMESTAMPTZ,
  duration_seconds INTEGER NOT NULL DEFAULT 0,
  rx_bytes BIGINT NOT NULL DEFAULT 0,
  tx_bytes BIGINT NOT NULL DEFAULT 0,
  disconnect_reason TEXT
);

CREATE TABLE IF NOT EXISTS speedtest_results (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  server_id UUID REFERENCES vpn_servers(id) ON DELETE SET NULL,
  provider TEXT NOT NULL DEFAULT 'speedofme',
  vpn_active BOOLEAN NOT NULL DEFAULT FALSE,
  server_label TEXT NOT NULL DEFAULT 'Direct ISP',
  ping_ms INTEGER NOT NULL DEFAULT 0,
  jitter_ms INTEGER NOT NULL DEFAULT 0,
  download_mbps REAL NOT NULL DEFAULT 0,
  upload_mbps REAL NOT NULL DEFAULT 0,
  max_download_mbps REAL NOT NULL DEFAULT 0,
  max_upload_mbps REAL NOT NULL DEFAULT 0,
  data_used_bytes BIGINT NOT NULL DEFAULT 0,
  test_server TEXT,
  public_ip TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS support_tickets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  category TEXT NOT NULL DEFAULT 'connectivity' CHECK (category IN ('connectivity', 'connection', 'tunnel', 'billing', 'account', 'speed', 'general')),
  subject TEXT NOT NULL,
  message TEXT NOT NULL,
  diagnostics_json JSONB,
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'in_progress', 'resolved')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE support_tickets
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE support_tickets
DROP CONSTRAINT IF EXISTS support_tickets_category_check;

ALTER TABLE support_tickets
ADD CONSTRAINT support_tickets_category_check
  CHECK (category IN ('connectivity', 'connection', 'tunnel', 'billing', 'account', 'speed', 'general'));

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

CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_id ON auth_sessions(user_id, revoked);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_id ON password_reset_tokens(user_id, used, expires_at);
CREATE INDEX IF NOT EXISTS idx_connection_logs_user_id ON connection_logs(user_id, connected_at DESC);
CREATE INDEX IF NOT EXISTS idx_speedtest_results_user_id ON speedtest_results(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_support_tickets_user_id ON support_tickets(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_error_logs_resolved ON error_logs(resolved, created_at DESC);

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE auth_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_reset_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE vpn_servers ENABLE ROW LEVEL SECURITY;
ALTER TABLE wireguard_peers ENABLE ROW LEVEL SECURITY;
ALTER TABLE connection_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE speedtest_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE support_tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE error_logs ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
  tbl TEXT;
  tables TEXT[] := ARRAY[
    'users',
    'auth_sessions',
    'password_reset_tokens',
    'subscriptions',
    'vpn_servers',
    'wireguard_peers',
    'connection_logs',
    'speedtest_results',
    'support_tickets',
    'error_logs'
  ];
BEGIN
  FOREACH tbl IN ARRAY tables LOOP
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
      EXECUTE format('REVOKE ALL ON TABLE public.%I FROM anon', tbl);
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
      EXECUTE format('REVOKE ALL ON TABLE public.%I FROM authenticated', tbl);
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
      EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', 'nexora_service_role_all_' || tbl, tbl);
      EXECUTE format(
        'CREATE POLICY %I ON public.%I FOR ALL TO service_role USING (true) WITH CHECK (true)',
        'nexora_service_role_all_' || tbl,
        tbl
      );
    END IF;
  END LOOP;
END
$$;
