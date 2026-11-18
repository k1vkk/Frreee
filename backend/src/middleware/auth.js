import jwt from 'jsonwebtoken';
import { pool } from '../db.js';

const PREDICTABLE_SECRETS = new Set([
  'secret',
  'changeme',
  'password',
  'admin',
  'admin123',
  'default',
  'jwt_secret',
  'nexora',
  'nexora-ephemeral-local-secret-do-not-use-in-prod',
  '12345678901234567890123456789012'
]);

export function isWeakOrPredictableSecret(rawSecret, { minLength = 32 } = {}) {
  const s = String(rawSecret || '').trim();
  if (!s || s.length < minLength) return true;
  const lower = s.toLowerCase();
  if (
    lower.startsWith('replace_with_') ||
    lower.startsWith('your_') ||
    lower.startsWith('example_') ||
    lower.includes('nexora-ephemeral') ||
    lower.includes('do-not-use-in-prod') ||
    PREDICTABLE_SECRETS.has(lower)
  ) {
    return true;
  }
  if (/^(.)\1+$/.test(s)) return true;
  return false;
}

export function getJwtSecret(env = process.env) {
  const secret = String(env.JWT_SECRET || '').trim();
  if (isWeakOrPredictableSecret(secret, { minLength: 32 })) {
    throw new Error('JWT_SECRET must be set to a strong random secret (minimum 32 characters)');
  }
  return secret;
}

export function getConfiguredAdminSecret(env = process.env) {
  const secret = String(env.ADMIN_SECRET || env.ADMIN_API_KEY || '').trim();
  if (isWeakOrPredictableSecret(secret, { minLength: 16 })) {
    return '';
  }
  return secret;
}

export async function requireAuth(req, res, next) {
  const authHeader = req.headers.authorization || '';
  if (!authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Missing Bearer token' });
  }
  const token = authHeader.slice(7);
  try {
    const payload = jwt.verify(token, getJwtSecret());
    const sessionRes = await pool.query(
      `SELECT s.id, s.user_id, s.revoked, u.email, u.role, u.is_suspended
       FROM auth_sessions s
       JOIN users u ON u.id = s.user_id
       WHERE s.id = $1 AND s.user_id = $2`,
      [payload.sid, payload.sub]
    );
    if (sessionRes.rowCount === 0 || sessionRes.rows[0].revoked) {
      return res.status(401).json({ error: 'Session revoked or expired' });
    }
    const row = sessionRes.rows[0];
    if (row.is_suspended) {
      return res.status(403).json({ error: 'Account suspended by administrator' });
    }
    await pool.query('UPDATE auth_sessions SET last_active_at = NOW() WHERE id = $1', [payload.sid]);
    req.auth = {
      userId: row.user_id,
      sessionId: row.id,
      email: row.email,
      role: row.role || 'user'
    };
    return next();
  } catch {
    return res.status(401).json({ error: 'Invalid or expired JWT' });
  }
}

export async function requireAdmin(req, res, next) {
  const configuredKey = getConfiguredAdminSecret();
  const headerKey = (req.headers['x-admin-key'] || req.headers['x-admin-secret'] || '').toString().trim();
  if (configuredKey && headerKey && headerKey === configuredKey) {
    req.admin = { via: 'admin_secret' };
    return next();
  }

  const authHeader = req.headers.authorization || '';
  if (!authHeader.startsWith('Bearer ')) {
    return res.status(403).json({ error: 'Admin authentication required' });
  }
  try {
    const payload = jwt.verify(authHeader.slice(7), getJwtSecret());
    const sessionRes = await pool.query(
      `SELECT s.id, s.user_id, s.revoked, u.email, u.role, u.is_suspended
       FROM auth_sessions s
       JOIN users u ON u.id = s.user_id
       WHERE s.id = $1 AND s.user_id = $2`,
      [payload.sid, payload.sub]
    );
    if (sessionRes.rowCount === 0 || sessionRes.rows[0].revoked) {
      return res.status(401).json({ error: 'Session expired' });
    }
    const row = sessionRes.rows[0];
    if (row.is_suspended || row.role !== 'admin') {
      return res.status(403).json({ error: 'Administrator role required' });
    }
    req.admin = { via: 'jwt', userId: row.user_id, email: row.email };
    return next();
  } catch {
    return res.status(401).json({ error: 'Invalid admin token' });
  }
}
