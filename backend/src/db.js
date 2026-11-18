import fs from 'node:fs/promises';
import fsSync from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import pg from 'pg';
import dotenv from 'dotenv';

const { Pool } = pg;
const __dirname = path.dirname(fileURLToPath(import.meta.url));
let envLoaded = false;
let activePool = null;

export function loadBackendEnv({ cwd = process.cwd(), force = false } = {}) {
  if (envLoaded && !force) return;
  const candidatePaths = Array.from(
    new Set([
      path.resolve(__dirname, '../.env'),
      path.resolve(cwd, '.env'),
      path.resolve(__dirname, '../../.env')
    ])
  );
  for (const envPath of candidatePaths) {
    if (fsSync.existsSync(envPath)) {
      dotenv.config({ path: envPath, override: false });
    }
  }
  envLoaded = true;
}

const TRANSIENT_PG_CODES = new Set([
  '08000', // connection_exception
  '08001', // sqlclient_unable_to_establish_sqlconnection
  '08003', // connection_does_not_exist
  '08004', // sqlserver_rejected_establishment_of_sqlconnection
  '08006', // connection_failure
  '40001', // serialization_failure
  '40P01', // deadlock_detected
  '57P01', // admin_shutdown
  '57P02', // crash_shutdown
  '57P03', // cannot_connect_now
  'ECONNRESET',
  'ECONNREFUSED',
  'ETIMEDOUT',
  'EPIPE',
  'ENOTFOUND',
  'EAI_AGAIN'
]);

export function isSupabaseDatabaseUrl(dbUrl = '') {
  const lower = String(dbUrl || '').toLowerCase();
  return lower.includes('.supabase.co') || lower.includes('.supabase.com') || lower.includes('pooler.supabase.');
}

export function loadCaCertificate(caInput) {
  const raw = String(caInput || '').trim();
  if (!raw) return undefined;
  const normalized = raw.replace(/\\n/g, '\n');
  if (normalized.includes('-----BEGIN CERTIFICATE-----')) {
    return normalized;
  }
  const resolvedPath = path.isAbsolute(raw) ? raw : path.resolve(process.cwd(), raw);
  const fileContent = fsSync.readFileSync(resolvedPath, 'utf8').trim();
  if (!fileContent.includes('-----BEGIN CERTIFICATE-----')) {
    throw new Error('DATABASE_CA_CERT file does not contain a valid PEM certificate');
  }
  return fileContent;
}

export function resolveSslConfig(env = process.env) {
  const dbUrl = String(env.DATABASE_URL || '').trim();
  const sslEnv = String(env.DATABASE_SSL || '').trim().toLowerCase();
  const caCertRaw = String(env.DATABASE_CA_CERT || '').trim();
  const isProd = String(env.NODE_ENV || '').trim().toLowerCase() === 'production';
  const supabaseHost = isSupabaseDatabaseUrl(dbUrl);

  if (sslEnv === 'false') {
    if (isProd && supabaseHost) {
      throw new Error('Security violation: DATABASE_SSL=false is prohibited for Supabase connections in production.');
    }
    return undefined;
  }

  const urlRequiresSsl = /sslmode=(require|verify-ca|verify-full)/i.test(dbUrl);
  const sslEnabled = Boolean(caCertRaw) || sslEnv === 'true' || urlRequiresSsl || supabaseHost;
  if (!sslEnabled) {
    return undefined;
  }

  const ca = loadCaCertificate(caCertRaw);
  const rejectEnv = env.DATABASE_SSL_REJECT_UNAUTHORIZED;
  const hasExplicitReject = rejectEnv !== undefined && String(rejectEnv).trim() !== '';

  let rejectUnauthorized = true;
  if (hasExplicitReject) {
    const normalizedReject = String(rejectEnv).trim().toLowerCase();
    if (normalizedReject === 'false') {
      if (isProd) {
        throw new Error('Security violation: DATABASE_SSL_REJECT_UNAUTHORIZED=false is prohibited in production.');
      }
      rejectUnauthorized = false;
    } else if (normalizedReject === 'true') {
      rejectUnauthorized = true;
    } else {
      throw new Error('Invalid DATABASE_SSL_REJECT_UNAUTHORIZED value; expected "true" or "false".');
    }
  }

  return ca ? { ca, rejectUnauthorized } : { rejectUnauthorized };
}

export function sanitizeConnectionString(dbUrl, sslConfig) {
  const raw = String(dbUrl || '').trim();
  if (!raw || !sslConfig) return raw || undefined;
  try {
    const parsed = new URL(raw);
    if (parsed.searchParams.has('sslmode')) {
      parsed.searchParams.delete('sslmode');
    }
    return parsed.toString();
  } catch {
    return raw;
  }
}

export function validateServerDatabaseSecurity(env = process.env) {
  const forbiddenFrontendVars = [
    'VITE_DATABASE_URL',
    'VITE_DATABASE_CA_CERT',
    'VITE_POSTGRES_PASSWORD',
    'VITE_SUPABASE_SERVICE_ROLE_KEY',
    'NEXT_PUBLIC_DATABASE_URL',
    'NEXT_PUBLIC_SUPABASE_SERVICE_ROLE_KEY'
  ];
  for (const key of forbiddenFrontendVars) {
    if (env[key] && String(env[key]).trim()) {
      throw new Error(`Security violation: ${key} must never be set as a frontend-exposed variable.`);
    }
  }
}

export function isTransientDbError(err) {
  if (!err) return false;
  if (err.code && TRANSIENT_PG_CODES.has(String(err.code))) return true;
  const msg = String(err.message || '').toLowerCase();
  return msg.includes('connection terminated unexpectedly')
    || msg.includes('timeout exceeded when trying to connect')
    || msg.includes('connection reset by peer')
    || msg.includes('cannot acquire a connection');
}

export async function withDbRetry(operation, {
  maxAttempts = 3,
  baseDelayMs = 250,
  shouldRetry = isTransientDbError
} = {}) {
  let lastError;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      return await operation();
    } catch (err) {
      lastError = err;
      if (attempt >= maxAttempts || !shouldRetry(err)) {
        throw err;
      }
      const delayMs = baseDelayMs * (2 ** (attempt - 1));
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
  }
  throw lastError;
}

export function createPoolConfig(env = process.env) {
  validateServerDatabaseSecurity(env);
  const sslConfig = resolveSslConfig(env);
  const rawMax = Number(env.DATABASE_POOL_MAX || 10);
  const poolMax = Number.isFinite(rawMax) ? Math.max(1, Math.min(50, rawMax)) : 10;

  return {
    connectionString: sanitizeConnectionString(env.DATABASE_URL, sslConfig),
    ssl: sslConfig,
    max: poolMax,
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 10000,
    keepAlive: true,
    keepAliveInitialDelayMillis: 10000
  };
}

export function initPool(env = process.env) {
  loadBackendEnv();
  const config = createPoolConfig(env);
  const nextPool = new Pool(config);
  nextPool.on('error', (err) => {
    console.error('[NEXORA DB] Unexpected idle PostgreSQL client error:', err.message);
  });
  activePool = nextPool;
  return activePool;
}

export function getPool() {
  if (!activePool) {
    return initPool(process.env);
  }
  return activePool;
}

export async function closePool() {
  if (activePool) {
    const closing = activePool;
    activePool = null;
    await closing.end();
  }
}

export const pool = new Proxy(
  {},
  {
    get(_target, prop) {
      if (prop === 'end') {
        return () => closePool();
      }
      const instance = getPool();
      const value = instance[prop];
      return typeof value === 'function' ? value.bind(instance) : value;
    }
  }
);

export async function checkDatabaseHealth() {
  await withDbRetry(() => getPool().query('SELECT 1'), { maxAttempts: 2, baseDelayMs: 150 });
  return { ok: true };
}

export async function initializeDatabase() {
  const schemaPath = path.resolve(__dirname, '../db/schema.sql');
  const migration1Path = path.resolve(__dirname, '../db/migrations/001_phase1_support_tickets.sql');
  const migration2Path = path.resolve(__dirname, '../db/migrations/002_phase2_supabase_rls.sql');
  const primarySeedPath = path.resolve(__dirname, '../db/seed.sql');
  const fallbackSeedPath = path.resolve(__dirname, '../db/seed_gcc_topology.sql');

  const schemaSql = await fs.readFile(schemaPath, 'utf8');
  const migration1Sql = fsSync.existsSync(migration1Path) ? await fs.readFile(migration1Path, 'utf8') : '';
  const migration2Sql = fsSync.existsSync(migration2Path) ? await fs.readFile(migration2Path, 'utf8') : '';
  const seedPath = fsSync.existsSync(primarySeedPath) ? primarySeedPath : fallbackSeedPath;
  const seedSql = await fs.readFile(seedPath, 'utf8');

  await withDbRetry(async () => {
    const db = getPool();
    await db.query(schemaSql);
    if (migration1Sql) await db.query(migration1Sql);
    if (migration2Sql) await db.query(migration2Sql);
    await db.query(seedSql);
  }, { maxAttempts: 3, baseDelayMs: 500 });
}
