import jwt from 'jsonwebtoken';
import { pool } from '../db.js';

export function getJwtSecret() {
  const secret = process.env.JWT_SECRET;
  if (!secret || secret.startsWith('replace_with_') || secret.startsWith('REPLACE_WITH_')) {
    if (process.env.NODE_ENV === 'production') {
      throw new Error('JWT_SECRET must be set to a strong random secret in production');
    }
    return 'nexora-ephemeral-local-secret-do-not-use-in-prod';
  }
  return secret;
}

export function getConfiguredAdminSecret() {
  const secret = (process.env.ADMIN_SECRET || process.env.ADMIN_API_KEY || '').trim();
  if (!secret || secret.startsWith('replace_with_') || secret.startsWith('REPLACE_WITH_')) {
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
