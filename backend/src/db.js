import fs from 'node:fs/promises';
import fsSync from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import pg from 'pg';
import dotenv from 'dotenv';

dotenv.config();

const { Pool } = pg;
const __dirname = path.dirname(fileURLToPath(import.meta.url));

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

  if (sslEnv === 'false') {
    return undefined;
  }

  const urlRequiresSsl = /sslmode=(require|verify-ca|verify-full)/i.test(dbUrl);
  const supabaseHost = isSupabaseDatabaseUrl(dbUrl);
  const sslEnabled = Boolean(caCertRaw) || sslEnv === 'true' || urlRequiresSsl || supabaseHost;
  if (!sslEnabled) {
    return undefined;
  }

  const ca = loadCaCertificate(caCertRaw);
  const rejectEnv = env.DATABASE_SSL_REJECT_UNAUTHORIZED;
  const hasExplicitReject = rejectEnv !== undefined && String(rejectEnv).trim() !== '';

  let rejectUnauthorized;
  if (ca) {
    rejectUnauthorized = hasExplicitReject
      ? String(rejectEnv).trim().toLowerCase() === 'true'
      : true;
  } else if (hasExplicitReject) {
    rejectUnauthorized = String(rejectEnv).trim().toLowerCase() === 'true';
  } else {
    // Supabase PgBouncer/Session pooler uses an intermediate CA; default to encrypted TLS
    // without failing verification unless DATABASE_CA_CERT or explicit true is provided.
    rejectUnauthorized = !supabaseHost;
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

const sslConfig = resolveSslConfig(process.env);
const poolMax = Math.max(1, Math.min(50, Number(process.env.DATABASE_POOL_MAX || 10)));

export const pool = new Pool({
  connectionString: sanitizeConnectionString(process.env.DATABASE_URL, sslConfig),
  ssl: sslConfig,
  max: Number.isFinite(poolMax) ? poolMax : 10,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 10000,
  keepAlive: true,
  keepAliveInitialDelayMillis: 10000
});

pool.on('error', (err) => {
  console.error('[NEXORA DB] Unexpected idle PostgreSQL client error:', err.message);
});

export async function checkDatabaseHealth() {
  await withDbRetry(() => pool.query('SELECT 1'), { maxAttempts: 2, baseDelayMs: 150 });
  return { ok: true };
}

export async function initializeDatabase() {
  const schemaPath = path.resolve(__dirname, '../db/schema.sql');
  const primarySeedPath = path.resolve(__dirname, '../db/seed.sql');
  const fallbackSeedPath = path.resolve(__dirname, '../db/seed_gcc_topology.sql');

  const schemaSql = await fs.readFile(schemaPath, 'utf8');
  const seedPath = fsSync.existsSync(primarySeedPath) ? primarySeedPath : fallbackSeedPath;
  const seedSql = await fs.readFile(seedPath, 'utf8');

  await withDbRetry(async () => {
    await pool.query(schemaSql);
    await pool.query(seedSql);
  }, { maxAttempts: 3, baseDelayMs: 500 });
}
