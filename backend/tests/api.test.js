import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import express from 'express';
import { isValidWireGuardKey, isValidUuid, isValidEmail, parseEndpoint } from '../src/utils/validators.js';
import { getJwtSecret, getConfiguredAdminSecret } from '../src/middleware/auth.js';
import { asyncHandler } from '../src/middleware/asyncHandler.js';
import { createRateLimiter } from '../src/middleware/rateLimit.js';
import { buildPublicSpeedTestConfig } from '../src/routes/speedtest.js';
import { formatServerRow } from '../src/routes/servers.js';
import { buildWgQuickConfig } from '../src/routes/vpn.js';
import { PRODUCT_TO_TIER } from '../src/routes/subscriptions.js';
import { ALLOWED_SUPPORT_CATEGORIES, normalizeSupportCategory } from '../src/routes/support.js';
import { resolveAdminSubscriptionUpdate } from '../src/routes/admin.js';
import {
  createPoolConfig,
  isTransientDbError,
  resolveSslConfig,
  sanitizeConnectionString,
  validateServerDatabaseSecurity,
  withDbRetry
} from '../src/db.js';
import { createApp, parseAllowedOrigins, validateProductionConfig } from '../src/server.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

test('validates WireGuard Curve25519 keys accurately', () => {
  // Valid 44-char base64 Curve25519 key ending in '='
  assert.equal(isValidWireGuardKey('u7R6gA6+2v2jE3oD0x8rV2pK5qN9mW1yZ4cB8hL3jFE='), true);
  assert.equal(isValidWireGuardKey('short-key'), false);
  assert.equal(isValidWireGuardKey(''), false);
});

test('validates UUIDs, emails, and endpoints accurately', () => {
  assert.equal(isValidUuid('550e8400-e29b-41d4-a716-446655440000'), true);
  assert.equal(isValidUuid('not-a-uuid'), false);
  assert.equal(isValidEmail('user@nexoravpn.com'), true);
  assert.equal(isValidEmail('bad-email'), false);
  assert.deepEqual(parseEndpoint('203.0.113.10:51820'), { host: '203.0.113.10', port: 51820 });
  assert.equal(parseEndpoint('203.0.113.10:99999'), null);
});

test('getJwtSecret rejects unset, placeholder, short, and predictable fallback secrets regardless of NODE_ENV', () => {
  const prevEnv = process.env.NODE_ENV;
  const prevSecret = process.env.JWT_SECRET;
  try {
    for (const nodeEnv of ['production', 'development', 'test']) {
      process.env.NODE_ENV = nodeEnv;
      delete process.env.JWT_SECRET;
      assert.throws(() => getJwtSecret(), /JWT_SECRET must be set/);

      process.env.JWT_SECRET = 'REPLACE_WITH_64_CHAR_HEX_JWT_SECRET';
      assert.throws(() => getJwtSecret(), /JWT_SECRET must be set/);

      process.env.JWT_SECRET = 'nexora-ephemeral-local-secret-do-not-use-in-prod';
      assert.throws(() => getJwtSecret(), /JWT_SECRET must be set/);

      process.env.JWT_SECRET = 'too-short-secret';
      assert.throws(() => getJwtSecret(), /JWT_SECRET must be set/);
    }

    const strong = '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08';
    process.env.JWT_SECRET = strong;
    assert.equal(getJwtSecret(), strong);
  } finally {
    process.env.NODE_ENV = prevEnv;
    if (prevSecret === undefined) delete process.env.JWT_SECRET;
    else process.env.JWT_SECRET = prevSecret;
  }
});

test('getConfiguredAdminSecret accepts strong secrets and rejects placeholders or predictable values', () => {
  const prev = process.env.ADMIN_SECRET;
  try {
    process.env.ADMIN_SECRET = 'REPLACE_WITH_64_CHAR_HEX_ADMIN_SECRET';
    assert.equal(getConfiguredAdminSecret(), '');
    process.env.ADMIN_SECRET = 'admin123';
    assert.equal(getConfiguredAdminSecret(), '');
    process.env.ADMIN_SECRET = 'a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6';
    assert.equal(getConfiguredAdminSecret(), 'a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6');
  } finally {
    if (prev === undefined) delete process.env.ADMIN_SECRET;
    else process.env.ADMIN_SECRET = prev;
  }
});

test('buildPublicSpeedTestConfig falls back cleanly without placeholders', () => {
  const unset = buildPublicSpeedTestConfig({
    SPEEDOFME_API_KEY: 'YOUR_SPEEDOFME_API_KEY',
    SPEEDOFME_DOMAIN: 'YOUR_REGISTERED_DOMAIN'
  });
  assert.equal(unset.speedOfMeConfigured, false);
  assert.equal(unset.provider, 'cloudflare_http');
  assert.equal(unset.apiKey, null);

  const configured = buildPublicSpeedTestConfig({
    SPEEDOFME_API_KEY: 'SOM_REAL_ACCOUNT_KEY',
    SPEEDOFME_DOMAIN: 'nexoravpn.example.com'
  });
  assert.equal(configured.speedOfMeConfigured, true);
  assert.equal(configured.provider, 'speedofme');
  assert.equal(configured.apiKey, 'SOM_REAL_ACCOUNT_KEY');
});

test('buildWgQuickConfig enforces dual-stack IPv4+IPv6 without leaking private key', () => {
  const conf = buildWgQuickConfig({
    assignedIpv4: '10.8.1.2/32',
    assignedIpv6: 'fd80:4e58:5201::2/128',
    dnsServers: '1.1.1.1, 2606:4700:4700::1111',
    mtu: 1420,
    serverPublicKey: 'u7R6gA6+2v2jE3oD0x8rV2pK5qN9mW1yZ4cB8hL3jFE=',
    endpoint: '203.0.113.10:51820',
    allowedIps: '0.0.0.0/0, ::/0'
  });
  assert.match(conf, /Address = 10\.8\.1\.2\/32, fd80:4e58:5201::2\/128/);
  assert.match(conf, /AllowedIPs = 0\.0\.0\.0\/0, ::\/0/);
  assert.doesNotMatch(conf, /PrivateKey =/);
});

test('formatServerRow maps CPU, RAM, traffic, port, and load metrics', () => {
  const row = formatServerRow({
    id: '550e8400-e29b-41d4-a716-446655440000',
    node_code: 'SA01',
    name: 'Riyadh SA01',
    country_name: 'Saudi Arabia',
    country_code: 'SA',
    city: 'Riyadh',
    public_ip: '203.0.113.10',
    server_port: 51820,
    endpoint: '203.0.113.10:51820',
    server_public_key: 'u7R6gA6+2v2jE3oD0x8rV2pK5qN9mW1yZ4cB8hL3jFE=',
    dns_servers: '1.1.1.1',
    tier: 'free',
    health_status: 'online',
    latency_ms: 34,
    jitter_ms: 4,
    packet_loss_percent: 0,
    cpu_usage: 21.5,
    ram_usage: 48.2,
    network_rx_bytes: 1000,
    network_tx_bytes: 2000,
    active_peers: 25,
    max_peers: 250
  });
  assert.equal(row.cpuUsage, 21.5);
  assert.equal(row.ramUsage, 48.2);
  assert.equal(row.networkTrafficBytes, 3000);
  assert.equal(row.loadPercent, 10);
  assert.equal(row.serverPort, 51820);
});

test('subscriptions support monthly, annual, and premium product mappings', () => {
  assert.equal(PRODUCT_TO_TIER.nexora_pro_monthly.tier, 'pro_monthly');
  assert.equal(PRODUCT_TO_TIER.nexora_pro_annual.tier, 'pro_annual');
  assert.equal(PRODUCT_TO_TIER.nexora_pro_premium.tier, 'pro_premium');
});

test('rate limiter blocks excess requests with HTTP 429', () => {
  const limiter = createRateLimiter({ windowMs: 60_000, max: 2, keyPrefix: 'test' });
  const headers = {};
  const fakeReq = { headers: {}, ip: '198.51.100.1', socket: { remoteAddress: '198.51.100.1' } };
  let statusCode = 200;
  const fakeRes = {
    setHeader(k, v) { headers[k] = v; },
    status(c) { statusCode = c; return this; },
    json(b) { return b; }
  };
  let nextCount = 0;
  limiter(fakeReq, fakeRes, () => { nextCount++; });
  limiter(fakeReq, fakeRes, () => { nextCount++; });
  limiter(fakeReq, fakeRes, () => { nextCount++; });
  assert.equal(nextCount, 2);
  assert.equal(statusCode, 429);
});

test('Express app mounts protected routes and speedtest config without DB', async () => {
  const app = createApp({ NODE_ENV: 'test', ENFORCE_HTTPS: 'false' });
  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    const port = server.address().port;
    const cfgRes = await fetch(`http://127.0.0.1:${port}/api/speedtest/config`);
    assert.equal(cfgRes.status, 200);
    const cfg = await cfgRes.json();
    assert.equal(cfg.ok, true);

    const profileRes = await fetch(`http://127.0.0.1:${port}/api/user/profile`);
    assert.equal(profileRes.status, 401);

    const vpnRes = await fetch(`http://127.0.0.1:${port}/api/vpn/connect`, { method: 'POST' });
    assert.equal(vpnRes.status, 401);

    const adminRes = await fetch(`http://127.0.0.1:${port}/api/admin/overview`);
    assert.equal(adminRes.status, 403);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});

test('asyncHandler forwards rejected promises to Express 4 error middleware', async () => {
  const testApp = express();
  testApp.get('/boom', asyncHandler(async () => {
    throw new Error('Simulated async failure');
  }));
  testApp.use((err, _req, res, _next) => {
    res.status(500).json({ error: err.message });
  });

  const server = http.createServer(testApp);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    const port = server.address().port;
    const res = await fetch(`http://127.0.0.1:${port}/boom`);
    assert.equal(res.status, 500);
    const body = await res.json();
    assert.equal(body.error, 'Simulated async failure');
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});

test('support ticket category normalization aligns with schema and Android categories', async () => {
  for (const cat of ['connectivity', 'connection', 'tunnel', 'billing', 'account', 'speed', 'general']) {
    assert.equal(ALLOWED_SUPPORT_CATEGORIES.has(cat), true);
    assert.equal(normalizeSupportCategory(cat), cat);
  }
  assert.equal(normalizeSupportCategory('TUNNEL'), 'tunnel');
  assert.equal(normalizeSupportCategory(''), 'connectivity');
  assert.equal(normalizeSupportCategory(undefined), 'connectivity');
  assert.equal(normalizeSupportCategory('unknown_cat'), 'general');

  const schemaSql = await fs.readFile(path.resolve(__dirname, '../db/schema.sql'), 'utf8');
  assert.match(schemaSql, /CREATE TABLE IF NOT EXISTS support_tickets[\s\S]*updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW\(\)/);
  assert.match(schemaSql, /ALTER TABLE support_tickets[\s\S]*ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW\(\)/);
  assert.match(schemaSql, /support_tickets_category_check/);
});

test('resolveAdminSubscriptionUpdate preserves existing paid tier when updating status only', () => {
  const existingExpiry = '2027-06-01T00:00:00.000Z';
  const now = new Date('2026-11-18T00:00:00.000Z');

  // Updating subscriptionStatus alone must NOT downgrade pro_annual -> free
  const canceled = resolveAdminSubscriptionUpdate({
    existingSub: { plan_tier: 'pro_annual', status: 'active', expires_at: existingExpiry },
    planTier: undefined,
    subscriptionStatus: 'canceled',
    now
  });
  assert.equal(canceled.planTier, 'pro_annual');
  assert.equal(canceled.status, 'canceled');
  assert.equal(canceled.expiresAt.toISOString(), existingExpiry);

  // Explicit downgrade to free clears expiresAt
  const downgraded = resolveAdminSubscriptionUpdate({
    existingSub: { plan_tier: 'pro_annual', status: 'active', expires_at: existingExpiry },
    planTier: 'free',
    subscriptionStatus: 'active',
    now
  });
  assert.equal(downgraded.planTier, 'free');
  assert.equal(downgraded.status, 'active');
  assert.equal(downgraded.expiresAt, null);

  // Upgrading from free to pro_monthly sets a 30-day expiry
  const upgraded = resolveAdminSubscriptionUpdate({
    existingSub: { plan_tier: 'free', status: 'active', expires_at: null },
    planTier: 'pro_monthly',
    subscriptionStatus: undefined,
    now
  });
  assert.equal(upgraded.planTier, 'pro_monthly');
  assert.equal(upgraded.status, 'active');
  assert.equal(upgraded.expiresAt.toISOString(), new Date(now.getTime() + 30 * 86400 * 1000).toISOString());
});

test('resolveSslConfig enforces TLS verification for Supabase and production connections', () => {
  const devDisabled = resolveSslConfig({
    NODE_ENV: 'test',
    DATABASE_SSL: 'false',
    DATABASE_URL: 'postgres://u:p@db.supabase.co:5432/postgres'
  });
  assert.equal(devDisabled, undefined);

  const dockerInternal = resolveSslConfig({
    NODE_ENV: 'production',
    DATABASE_SSL: 'false',
    DATABASE_URL: 'postgres://nexora:pw@postgres:5432/nexora_vpn'
  });
  assert.equal(dockerInternal, undefined);

  assert.throws(
    () => resolveSslConfig({
      NODE_ENV: 'production',
      DATABASE_SSL: 'false',
      DATABASE_URL: 'postgres://u:p@db.supabase.co:5432/postgres'
    }),
    /DATABASE_SSL=false is prohibited for Supabase/
  );

  const supabaseDefault = resolveSslConfig({
    NODE_ENV: 'production',
    DATABASE_URL: 'postgres://postgres.abc:pw@aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require'
  });
  assert.deepEqual(supabaseDefault, { rejectUnauthorized: true });

  assert.throws(
    () => resolveSslConfig({
      NODE_ENV: 'production',
      DATABASE_SSL_REJECT_UNAUTHORIZED: 'false',
      DATABASE_URL: 'postgres://postgres.abc:pw@aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require'
    }),
    /DATABASE_SSL_REJECT_UNAUTHORIZED=false is prohibited in production/
  );

  const withCa = resolveSslConfig({
    NODE_ENV: 'production',
    DATABASE_URL: 'postgres://postgres.abc:pw@aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require',
    DATABASE_CA_CERT: '-----BEGIN CERTIFICATE-----\\nMIIB...\\n-----END CERTIFICATE-----'
  });
  assert.equal(withCa.rejectUnauthorized, true);
  assert.match(withCa.ca, /-----BEGIN CERTIFICATE-----\nMIIB\.\.\.\n-----END CERTIFICATE-----/);

  const cleanedUrl = sanitizeConnectionString(
    'postgres://postgres.abc:pw@aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require',
    withCa
  );
  assert.equal(cleanedUrl.includes('sslmode='), false);
});

test('withDbRetry retries transient database errors and skips non-transient SQL errors', async () => {
  let attempts = 0;
  const result = await withDbRetry(async () => {
    attempts++;
    if (attempts < 3) {
      const err = new Error('Connection terminated unexpectedly');
      err.code = 'ECONNRESET';
      throw err;
    }
    return 'recovered';
  }, { maxAttempts: 3, baseDelayMs: 5 });
  assert.equal(result, 'recovered');
  assert.equal(attempts, 3);

  assert.equal(isTransientDbError({ code: '57P01' }), true);
  assert.equal(isTransientDbError({ code: '23505' }), false);

  let nonTransientAttempts = 0;
  await assert.rejects(
    () => withDbRetry(async () => {
      nonTransientAttempts++;
      const err = new Error('duplicate key');
      err.code = '23505';
      throw err;
    }, { maxAttempts: 3, baseDelayMs: 5 }),
    /duplicate key/
  );
  assert.equal(nonTransientAttempts, 1);
});

test('validateServerDatabaseSecurity rejects frontend-exposed database credentials', () => {
  assert.doesNotThrow(() => validateServerDatabaseSecurity({ DATABASE_URL: 'postgres://server-only' }));
  assert.throws(
    () => validateServerDatabaseSecurity({ VITE_SUPABASE_SERVICE_ROLE_KEY: 'secret' }),
    /Security violation/
  );
  assert.throws(
    () => validateServerDatabaseSecurity({ VITE_DATABASE_URL: 'postgres://leak' }),
    /Security violation/
  );
});

test('Phase 2 RLS migration and schema enable RLS on all sensitive tables without public policies', async () => {
  const rlsSql = await fs.readFile(path.resolve(__dirname, '../db/migrations/002_phase2_supabase_rls.sql'), 'utf8');
  const sensitiveTables = [
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
  for (const tbl of sensitiveTables) {
    assert.match(rlsSql, new RegExp(`ALTER TABLE public\\.${tbl} ENABLE ROW LEVEL SECURITY;`));
  }
  assert.match(rlsSql, /REVOKE ALL ON TABLE public\.%I FROM anon/);
  assert.match(rlsSql, /REVOKE ALL ON TABLE public\.%I FROM authenticated/);
  assert.match(rlsSql, /FOR ALL TO service_role USING \(true\) WITH CHECK \(true\)/);
  assert.doesNotMatch(rlsSql, /TO\s+(public|anon|authenticated)\s+USING/i);
  assert.doesNotMatch(rlsSql, /DISABLE ROW LEVEL SECURITY/i);
});

test('createPoolConfig validates frontend security before returning Pool config', () => {
  assert.throws(
    () => createPoolConfig({
      DATABASE_URL: 'postgresql://nexora:pw@db.internal:5432/nexora_vpn',
      VITE_DATABASE_URL: 'postgresql://leaked'
    }),
    /Security violation/
  );

  const cfg = createPoolConfig({
    NODE_ENV: 'production',
    DATABASE_URL: 'postgresql://postgres.abc:pw@aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require',
    DATABASE_POOL_MAX: '25'
  });
  assert.equal(cfg.max, 25);
  assert.deepEqual(cfg.ssl, { rejectUnauthorized: true });
  assert.equal(cfg.connectionString.includes('sslmode='), false);
});

test('validateProductionConfig enforces NODE_ENV, strong secrets, explicit CORS_ORIGIN, and ENFORCE_HTTPS', () => {
  const validProdEnv = {
    NODE_ENV: 'production',
    DATABASE_URL: 'postgresql://nexora:pw@db.internal:5432/nexora_vpn',
    DATABASE_SSL: 'true',
    JWT_SECRET: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
    ADMIN_SECRET: 'fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210',
    NODE_AGENT_SECRET: 'abcdef0123456789abcdef0123456789',
    CORS_ORIGIN: 'https://admin.nexoravpn.net',
    ENFORCE_HTTPS: 'true'
  };

  assert.doesNotThrow(() => validateProductionConfig(validProdEnv));

  assert.throws(
    () => validateProductionConfig({ ...validProdEnv, NODE_ENV: 'development' }),
    /NODE_ENV must be set to "production"/
  );
  assert.throws(
    () => validateProductionConfig({ ...validProdEnv, CORS_ORIGIN: '*' }),
    /CORS_ORIGIN must be explicitly configured/
  );
  assert.throws(
    () => validateProductionConfig({ ...validProdEnv, CORS_ORIGIN: '' }),
    /CORS_ORIGIN must be explicitly configured/
  );
  assert.throws(
    () => validateProductionConfig({ ...validProdEnv, ENFORCE_HTTPS: 'false' }),
    /ENFORCE_HTTPS=true is required/
  );
  assert.throws(
    () => validateProductionConfig({ ...validProdEnv, ADMIN_SECRET: 'REPLACE_WITH_SECRET', ADMIN_API_KEY: '' }),
    /ADMIN_SECRET or ADMIN_API_KEY/
  );
  assert.throws(
    () => validateProductionConfig({ ...validProdEnv, NODE_AGENT_SECRET: 'short' }),
    /NODE_AGENT_SECRET/
  );
  assert.deepEqual(parseAllowedOrigins('https://a.example.net, https://b.example.net'), [
    'https://a.example.net',
    'https://b.example.net'
  ]);
});

test('createApp blocks wildcard CORS and enforces HTTPS in production while keeping health open', async () => {
  const app = createApp({
    NODE_ENV: 'production',
    ENFORCE_HTTPS: 'true',
    CORS_ORIGIN: 'https://admin.nexoravpn.net'
  });
  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    const port = server.address().port;

    const httpDenied = await fetch(`http://127.0.0.1:${port}/api/speedtest/config`);
    assert.equal(httpDenied.status, 403);

    const httpsAllowed = await fetch(`http://127.0.0.1:${port}/api/speedtest/config`, {
      headers: {
        'x-forwarded-proto': 'https',
        Origin: 'https://evil.attacker.test'
      }
    });
    assert.equal(httpsAllowed.status, 200);
    assert.equal(httpsAllowed.headers.get('access-control-allow-origin'), null);

    const validOriginRes = await fetch(`http://127.0.0.1:${port}/api/speedtest/config`, {
      headers: {
        'x-forwarded-proto': 'https',
        Origin: 'https://admin.nexoravpn.net'
      }
    });
    assert.equal(validOriginRes.status, 200);
    assert.equal(validOriginRes.headers.get('access-control-allow-origin'), 'https://admin.nexoravpn.net');
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});

test('bootstrap separates migrations from startup and backend/.env.example documents required variables', async () => {
  const serverSrc = await fs.readFile(path.resolve(__dirname, '../src/server.js'), 'utf8');
  assert.doesNotMatch(serverSrc, /initializeDatabase\s*\(/);

  const envExample = await fs.readFile(path.resolve(__dirname, '../.env.example'), 'utf8');
  for (const key of [
    'NODE_ENV=',
    'PORT=',
    'ENFORCE_HTTPS=',
    'CORS_ORIGIN=',
    'API_PUBLIC_URL=',
    'DATABASE_URL=',
    'DATABASE_SSL=',
    'DATABASE_SSL_REJECT_UNAUTHORIZED=',
    'JWT_SECRET=',
    'ADMIN_SECRET=',
    'NODE_AGENT_SECRET=',
    'MAIL_FROM='
  ]) {
    assert.ok(envExample.includes(key), `Missing ${key} in backend/.env.example`);
  }
});


