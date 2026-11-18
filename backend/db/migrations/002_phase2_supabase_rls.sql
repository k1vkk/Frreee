-- NEXORA VPN Phase 2 Migration: Supabase Row Level Security (RLS) & Performance Indexes
-- IMPORTANT:
-- - Enables RLS on ALL sensitive tables in public schema.
-- - Revokes all table privileges from Supabase 'anon' and 'authenticated' client API roles.
-- - Grants access ONLY to server-side 'service_role' (and table owner / backend PostgreSQL role).
-- - Creates ZERO permissive public/anon/authenticated policies.
-- - Safe and idempotent on both Supabase PostgreSQL and standard PostgreSQL 15/16.

CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_id ON auth_sessions(user_id, revoked);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_id ON password_reset_tokens(user_id, used, expires_at);
CREATE INDEX IF NOT EXISTS idx_connection_logs_user_id ON connection_logs(user_id, connected_at DESC);
CREATE INDEX IF NOT EXISTS idx_speedtest_results_user_id ON speedtest_results(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_support_tickets_user_id ON support_tickets(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_error_logs_resolved ON error_logs(resolved, created_at DESC);

ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.auth_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.password_reset_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vpn_servers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wireguard_peers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.connection_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.speedtest_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.error_logs ENABLE ROW LEVEL SECURITY;

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
