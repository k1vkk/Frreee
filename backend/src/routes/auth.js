import { Router } from 'express';
import crypto from 'node:crypto';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { pool } from '../db.js';
import { getJwtSecret, requireAuth } from '../middleware/auth.js';
import { logBackendError } from '../services/errorLogger.js';

const router = Router();
const JWT_EXPIRES_IN = () => process.env.JWT_EXPIRES_IN || '30d';

function hashResetCode(code) {
  return crypto.createHash('sha256').update(String(code).trim()).digest('hex');
}

async function dispatchPasswordResetEmail(toEmail, displayName, code) {
  const resendKey = process.env.RESEND_API_KEY;
  const fromEmail = process.env.MAIL_FROM || 'NEXORA VPN <security@nexoravpn.com>';
  if (resendKey) {
    const res = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${resendKey}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        from: fromEmail,
        to: [toEmail],
        subject: 'NEXORA VPN Password Reset Verification Code',
        text: `Hello ${displayName},\n\nYour NEXORA VPN password reset code is: ${code}\n\nThis code expires in 15 minutes.`
      })
    });
    if (!res.ok) {
      throw new Error(`Email delivery failed (HTTP ${res.status})`);
    }
    return true;
  }

  const webhookUrl = process.env.SMTP_WEBHOOK_URL;
  if (webhookUrl) {
    const res = await fetch(webhookUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ to: toEmail, displayName, code, purpose: 'password_reset' })
    });
    if (!res.ok) {
      throw new Error(`Mail webhook failed (HTTP ${res.status})`);
    }
    return true;
  }

  return false;
}

router.get('/', (_req, res) => {
  res.json({
    ok: true,
    endpoints: ['/register', '/login', '/forgot-password', '/reset-password', '/me', '/logout', '/sessions/:sessionId']
  });
});

router.post('/register', async (req, res) => {
  const { email, password, displayName, deviceLabel } = req.body || {};
  if (!email || !password || password.length < 8) {
    return res.status(400).json({ error: 'Valid email and password (min 8 chars) required' });
  }
  const normalizedEmail = String(email).trim().toLowerCase();
  const safeName = String(displayName || normalizedEmail.split('@')[0]).trim();
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const existing = await client.query('SELECT id FROM users WHERE email = $1', [normalizedEmail]);
    if (existing.rowCount > 0) {
      await client.query('ROLLBACK');
      return res.status(409).json({ error: 'Email already registered' });
    }
    const passwordHash = await bcrypt.hash(password, 12);
    const countRes = await client.query('SELECT COUNT(*)::int AS cnt FROM users');
    const assignedRole = countRes.rows[0].cnt === 0 ? 'admin' : 'user';

    const userRes = await client.query(
      'INSERT INTO users (email, password_hash, display_name, role) VALUES ($1, $2, $3, $4) RETURNING id, email, display_name, role, created_at',
      [normalizedEmail, passwordHash, safeName, assignedRole]
    );
    const user = userRes.rows[0];
    await client.query(
      'INSERT INTO subscriptions (user_id, plan_tier, status) VALUES ($1, $2, $3)',
      [user.id, 'free', 'active']
    );
    const sessionRes = await client.query(
      'INSERT INTO auth_sessions (user_id, device_label, ip_address) VALUES ($1, $2, $3) RETURNING id',
      [user.id, deviceLabel || 'Android Device', req.ip]
    );
    await client.query('COMMIT');
    const token = jwt.sign(
      { sub: user.id, sid: sessionRes.rows[0].id, email: user.email, role: user.role },
      getJwtSecret(),
      { expiresIn: JWT_EXPIRES_IN() }
    );
    return res.status(201).json({
      token,
      user: {
        id: user.id,
        email: user.email,
        displayName: user.display_name,
        role: user.role,
        planTier: 'free',
        subscriptionStatus: 'active'
      }
    });
  } catch (err) {
    await client.query('ROLLBACK');
    await logBackendError({ source: 'auth', endpoint: '/register', message: err.message });
    return res.status(500).json({ error: 'Registration failed' });
  } finally {
    client.release();
  }
});

router.post('/login', async (req, res) => {
  const { email, password, deviceLabel } = req.body || {};
  if (!email || !password) {
    return res.status(400).json({ error: 'Email and password required' });
  }
  const normalizedEmail = String(email).trim().toLowerCase();
  const userRes = await pool.query(
    `SELECT u.id, u.email, u.password_hash, u.display_name, u.role, u.is_suspended,
            s.plan_tier, s.status AS sub_status, s.expires_at
     FROM users u
     LEFT JOIN subscriptions s ON s.user_id = u.id
     WHERE u.email = $1`,
    [normalizedEmail]
  );
  if (userRes.rowCount === 0) {
    return res.status(401).json({ error: 'Invalid credentials' });
  }
  const user = userRes.rows[0];
  if (user.is_suspended) {
    return res.status(403).json({ error: 'Account suspended by administrator' });
  }
  const valid = await bcrypt.compare(password, user.password_hash);
  if (!valid) {
    return res.status(401).json({ error: 'Invalid credentials' });
  }
  const sessionRes = await pool.query(
    'INSERT INTO auth_sessions (user_id, device_label, ip_address) VALUES ($1, $2, $3) RETURNING id',
    [user.id, deviceLabel || 'Android Device', req.ip]
  );
  const token = jwt.sign(
    { sub: user.id, sid: sessionRes.rows[0].id, email: user.email, role: user.role },
    getJwtSecret(),
    { expiresIn: JWT_EXPIRES_IN() }
  );
  return res.json({
    token,
    user: {
      id: user.id,
      email: user.email,
      displayName: user.display_name,
      role: user.role,
      planTier: user.plan_tier || 'free',
      subscriptionStatus: user.sub_status || 'active',
      expiresAt: user.expires_at
    }
  });
});

router.post('/forgot-password', async (req, res) => {
  const { email } = req.body || {};
  if (!email) {
    return res.status(400).json({ error: 'Email address required' });
  }
  const normalizedEmail = String(email).trim().toLowerCase();
  const userRes = await pool.query(
    'SELECT id, email, display_name, is_suspended FROM users WHERE email = $1',
    [normalizedEmail]
  );

  if (userRes.rowCount === 0) {
    // Do not enumerate accounts
    return res.json({ ok: true, message: 'If this email exists, a reset code has been dispatched.' });
  }
  const user = userRes.rows[0];
  if (user.is_suspended) {
    return res.status(403).json({ error: 'Account is suspended' });
  }

  const code = String(crypto.randomInt(100000, 999999));
  const codeHash = hashResetCode(code);
  const expiresAt = new Date(Date.now() + 15 * 60 * 1000);

  await pool.query('UPDATE password_reset_tokens SET used = TRUE WHERE user_id = $1 AND used = FALSE', [user.id]);
  await pool.query(
    'INSERT INTO password_reset_tokens (user_id, code_hash, expires_at) VALUES ($1, $2, $3)',
    [user.id, codeHash, expiresAt]
  );

  try {
    const delivered = await dispatchPasswordResetEmail(user.email, user.display_name, code);
    if (!delivered) {
      return res.status(503).json({
        error: 'Email delivery provider (RESEND_API_KEY or SMTP_WEBHOOK_URL) is not configured on backend. Ask an administrator to reset your password in the Admin Dashboard.'
      });
    }
    return res.json({ ok: true, message: 'Verification code sent to your email address.' });
  } catch (err) {
    await logBackendError({
      source: 'auth',
      endpoint: '/forgot-password',
      message: err.message,
      userId: user.id
    });
    return res.status(502).json({ error: 'Unable to deliver reset email right now.' });
  }
});

router.post('/reset-password', async (req, res) => {
  const { email, code, newPassword } = req.body || {};
  if (!email || !code || !newPassword || String(newPassword).length < 8) {
    return res.status(400).json({ error: 'Valid email, 6-digit code, and new password (min 8 chars) required' });
  }
  const normalizedEmail = String(email).trim().toLowerCase();
  const codeHash = hashResetCode(code);

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const tokenRes = await client.query(
      `SELECT prt.id AS token_id, u.id AS user_id
       FROM password_reset_tokens prt
       JOIN users u ON u.id = prt.user_id
       WHERE u.email = $1
         AND prt.code_hash = $2
         AND prt.used = FALSE
         AND prt.expires_at > NOW()
       ORDER BY prt.created_at DESC
       LIMIT 1`,
      [normalizedEmail, codeHash]
    );
    if (tokenRes.rowCount === 0) {
      await client.query('ROLLBACK');
      return res.status(400).json({ error: 'Invalid or expired reset verification code' });
    }
    const { token_id: tokenId, user_id: userId } = tokenRes.rows[0];
    const passwordHash = await bcrypt.hash(String(newPassword), 12);
    await client.query('UPDATE users SET password_hash = $2 WHERE id = $1', [userId, passwordHash]);
    await client.query('UPDATE password_reset_tokens SET used = TRUE WHERE id = $1', [tokenId]);
    await client.query('UPDATE auth_sessions SET revoked = TRUE WHERE user_id = $1', [userId]);
    await client.query('COMMIT');
    return res.json({ ok: true, message: 'Password updated. Please sign in with your new password.' });
  } catch (err) {
    await client.query('ROLLBACK');
    await logBackendError({ source: 'auth', endpoint: '/reset-password', message: err.message });
    return res.status(500).json({ error: 'Failed to reset password' });
  } finally {
    client.release();
  }
});

router.get('/me', requireAuth, async (req, res) => {
  const userRes = await pool.query(
    `SELECT u.id, u.email, u.display_name, u.role, s.plan_tier, s.status AS sub_status, s.expires_at
     FROM users u
     LEFT JOIN subscriptions s ON s.user_id = u.id
     WHERE u.id = $1`,
    [req.auth.userId]
  );
  if (userRes.rowCount === 0) {
    return res.status(404).json({ error: 'User not found' });
  }
  const u = userRes.rows[0];
  const effectiveTier = (u.expires_at && new Date(u.expires_at) < new Date() && u.plan_tier !== 'free')
    ? 'free'
    : (u.plan_tier || 'free');
  const effectiveStatus = (u.expires_at && new Date(u.expires_at) < new Date() && u.plan_tier !== 'free')
    ? 'expired'
    : (u.sub_status || 'active');

  const sessionsRes = await pool.query(
    `SELECT id, device_label, ip_address, created_at, last_active_at, (id = $2) AS is_current
     FROM auth_sessions
     WHERE user_id = $1 AND revoked = FALSE
     ORDER BY last_active_at DESC`,
    [req.auth.userId, req.auth.sessionId]
  );
  return res.json({
    user: {
      id: u.id,
      email: u.email,
      displayName: u.display_name,
      role: u.role || 'user',
      planTier: effectiveTier,
      subscriptionStatus: effectiveStatus,
      expiresAt: u.expires_at
    },
    sessions: sessionsRes.rows.map((s) => ({
      id: s.id,
      deviceLabel: s.device_label,
      ipAddress: s.ip_address || '',
      createdAt: s.created_at,
      lastActiveAt: s.last_active_at,
      isCurrent: s.is_current
    }))
  });
});

router.delete('/sessions/:sessionId', requireAuth, async (req, res) => {
  await pool.query(
    'UPDATE auth_sessions SET revoked = TRUE WHERE id = $1 AND user_id = $2',
    [req.params.sessionId, req.auth.userId]
  );
  return res.json({ ok: true });
});

router.post('/logout', requireAuth, async (req, res) => {
  await pool.query('UPDATE auth_sessions SET revoked = TRUE WHERE id = $1', [req.auth.sessionId]);
  return res.json({ ok: true });
});

export default router;
