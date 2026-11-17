import { Router } from 'express';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { pool } from '../db.js';
import { getJwtSecret, requireAdmin } from '../middleware/auth.js';
import { checkAllProvisionedNodes } from '../services/nodeMonitor.js';
import { logBackendError } from '../services/errorLogger.js';

const router = Router();

function isValidWireGuardKey(key) {
  return typeof key === 'string' && /^[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]=$/.test(key.trim());
}

// POST /api/v1/admin/login - Authenticate Admin Dashboard user via PostgreSQL role='admin'
router.post('/login', async (req, res) => {
  const { email, password } = req.body || {};
  if (!email || !password) {
    return res.status(400).json({ error: 'Admin email and password required' });
  }
  const normalizedEmail = String(email).trim().toLowerCase();
  const userRes = await pool.query(
    'SELECT id, email, password_hash, display_name, role, is_suspended FROM users WHERE email = $1',
    [normalizedEmail]
  );
  if (userRes.rowCount === 0) {
    return res.status(401).json({ error: 'Invalid admin credentials' });
  }
  const user = userRes.rows[0];
  if (user.is_suspended || user.role !== 'admin') {
    return res.status(403).json({ error: 'Administrator privileges required' });
  }
  const valid = await bcrypt.compare(password, user.password_hash);
  if (!valid) {
    return res.status(401).json({ error: 'Invalid admin credentials' });
  }
  const sessionRes = await pool.query(
    'INSERT INTO auth_sessions (user_id, device_label, ip_address) VALUES ($1, $2, $3) RETURNING id',
    [user.id, 'Admin Dashboard', req.ip]
  );
  const token = jwt.sign(
    { sub: user.id, sid: sessionRes.rows[0].id, email: user.email, role: 'admin' },
    getJwtSecret(),
    { expiresIn: '12h' }
  );
  return res.json({
    token,
    admin: {
      id: user.id,
      email: user.email,
      displayName: user.display_name,
      role: user.role
    }
  });
});

// GET /api/v1/admin/overview - Real-time metrics from PostgreSQL
router.get('/overview', requireAdmin, async (_req, res) => {
  const [usersAgg, peersAgg, serversAgg, subsAgg, errorsAgg] = await Promise.all([
    pool.query(
      `SELECT COUNT(*)::int AS total_users,
              COUNT(*) FILTER (WHERE is_suspended = TRUE)::int AS suspended_users,
              COUNT(*) FILTER (WHERE role = 'admin')::int AS admin_users
       FROM users`
    ),
    pool.query(
      `SELECT COUNT(*) FILTER (WHERE is_connected = TRUE)::int AS connected_peers,
              COUNT(DISTINCT user_id) FILTER (WHERE is_connected = TRUE)::int AS connected_users,
              COALESCE(SUM(rx_bytes), 0)::bigint AS total_rx_bytes,
              COALESCE(SUM(tx_bytes), 0)::bigint AS total_tx_bytes
       FROM wireguard_peers`
    ),
    pool.query(
      `SELECT COUNT(*)::int AS total_servers,
              COUNT(*) FILTER (WHERE health_status = 'online')::int AS online_servers,
              COUNT(*) FILTER (WHERE health_status = 'offline')::int AS offline_servers,
              COUNT(*) FILTER (WHERE health_status = 'pending_vps')::int AS pending_servers,
              COALESCE(SUM(max_peers) FILTER (WHERE health_status = 'online'), 0)::int AS online_capacity
       FROM vpn_servers`
    ),
    pool.query(
      `SELECT COUNT(*) FILTER (WHERE plan_tier != 'free' AND status = 'active')::int AS pro_subscriptions,
              COUNT(*) FILTER (WHERE plan_tier = 'pro_monthly' AND status = 'active')::int AS monthly_subs,
              COUNT(*) FILTER (WHERE plan_tier = 'pro_annual' AND status = 'active')::int AS annual_subs
       FROM subscriptions`
    ),
    pool.query(
      `SELECT COUNT(*) FILTER (WHERE resolved = FALSE)::int AS unresolved_errors,
              COUNT(*)::int AS total_errors
       FROM error_logs`
    )
  ]);

  const u = usersAgg.rows[0];
  const p = peersAgg.rows[0];
  const s = serversAgg.rows[0];
  const sub = subsAgg.rows[0];
  const err = errorsAgg.rows[0];
  const avgLoadPercent = s.online_capacity > 0
    ? Math.min(100, Math.round((p.connected_peers / s.online_capacity) * 100))
    : 0;

  return res.json({
    users: {
      total: u.total_users,
      connected: p.connected_users,
      suspended: u.suspended_users,
      admins: u.admin_users
    },
    servers: {
      total: s.total_servers,
      online: s.online_servers,
      offline: s.offline_servers,
      pending: s.pending_servers,
      avgLoadPercent
    },
    network: {
      connectedPeers: p.connected_peers,
      totalRxBytes: Number(p.total_rx_bytes),
      totalTxBytes: Number(p.total_tx_bytes)
    },
    subscriptions: {
      proActive: sub.pro_subscriptions,
      monthly: sub.monthly_subs,
      annual: sub.annual_subs
    },
    errors: {
      unresolved: err.unresolved_errors,
      total: err.total_errors
    },
    timestamp: new Date().toISOString()
  });
});

// GET /api/v1/admin/users
router.get('/users', requireAdmin, async (req, res) => {
  const q = typeof req.query.q === 'string' ? req.query.q.trim().toLowerCase() : '';
  const result = await pool.query(
    `SELECT u.id, u.email, u.display_name, u.role, u.is_suspended, u.created_at,
            s.plan_tier, s.status AS sub_status, s.expires_at,
            COUNT(p.id) FILTER (WHERE p.is_connected = TRUE)::int AS active_connections,
            COALESCE(SUM(p.rx_bytes), 0)::bigint AS rx_bytes,
            COALESCE(SUM(p.tx_bytes), 0)::bigint AS tx_bytes
     FROM users u
     LEFT JOIN subscriptions s ON s.user_id = u.id
     LEFT JOIN wireguard_peers p ON p.user_id = u.id
     WHERE ($1 = '' OR LOWER(u.email) LIKE '%' || $1 || '%' OR LOWER(u.display_name) LIKE '%' || $1 || '%')
     GROUP BY u.id, s.plan_tier, s.status, s.expires_at
     ORDER BY u.created_at DESC
     LIMIT 200`,
    [q]
  );
  return res.json({
    users: result.rows.map((r) => ({
      id: r.id,
      email: r.email,
      displayName: r.display_name,
      role: r.role,
      isSuspended: r.is_suspended,
      planTier: r.plan_tier || 'free',
      subscriptionStatus: r.sub_status || 'active',
      expiresAt: r.expires_at,
      activeConnections: r.active_connections,
      rxBytes: Number(r.rx_bytes),
      txBytes: Number(r.tx_bytes),
      createdAt: r.created_at
    }))
  });
});

// PATCH /api/v1/admin/users/:userId - Manage role, suspension, subscription tier, or password reset
router.patch('/users/:userId', requireAdmin, async (req, res) => {
  const { userId } = req.params;
  const { role, isSuspended, planTier, subscriptionStatus, newPassword } = req.body || {};

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    if (role !== undefined || isSuspended !== undefined) {
      await client.query(
        `UPDATE users
         SET role = COALESCE($2, role),
             is_suspended = COALESCE($3, is_suspended)
         WHERE id = $1`,
        [userId, role || null, typeof isSuspended === 'boolean' ? isSuspended : null]
      );
      if (isSuspended === true) {
        await client.query('UPDATE auth_sessions SET revoked = TRUE WHERE user_id = $1', [userId]);
      }
    }

    if (newPassword && String(newPassword).length >= 8) {
      const hash = await bcrypt.hash(String(newPassword), 12);
      await client.query('UPDATE users SET password_hash = $2 WHERE id = $1', [userId, hash]);
      await client.query('UPDATE auth_sessions SET revoked = TRUE WHERE user_id = $1', [userId]);
    }

    if (planTier !== undefined || subscriptionStatus !== undefined) {
      const resolvedTier = planTier || 'free';
      const resolvedStatus = subscriptionStatus || 'active';
      const expiresAt = resolvedTier === 'free'
        ? null
        : new Date(Date.now() + (resolvedTier === 'pro_annual' ? 365 : 30) * 86400 * 1000);
      await client.query(
        `INSERT INTO subscriptions (user_id, plan_tier, status, expires_at, updated_at)
         VALUES ($1, $2, $3, $4, NOW())
         ON CONFLICT (user_id)
         DO UPDATE SET plan_tier = EXCLUDED.plan_tier,
                       status = EXCLUDED.status,
                       expires_at = EXCLUDED.expires_at,
                       updated_at = NOW()`,
        [userId, resolvedTier, resolvedStatus, expiresAt]
      );
    }

    await client.query('COMMIT');
    return res.json({ ok: true });
  } catch (err) {
    await client.query('ROLLBACK');
    await logBackendError({ source: 'admin', endpoint: `/users/${userId}`, message: err.message });
    return res.status(500).json({ error: err.message });
  } finally {
    client.release();
  }
});

// GET /api/v1/admin/servers - Full GCC & custom servers telemetry + load
router.get('/servers', requireAdmin, async (_req, res) => {
  const result = await pool.query(
    `SELECT s.*,
            COUNT(p.id) FILTER (WHERE p.is_connected = TRUE)::int AS connected_peers,
            COALESCE(SUM(p.rx_bytes), 0)::bigint AS total_rx_bytes,
            COALESCE(SUM(p.tx_bytes), 0)::bigint AS total_tx_bytes
     FROM vpn_servers s
     LEFT JOIN wireguard_peers p ON p.server_id = s.id
     GROUP BY s.id
     ORDER BY s.country_code ASC, s.node_code ASC`
  );
  return res.json({
    servers: result.rows.map((s) => {
      const activePeers = Math.max(s.active_peers_reported || 0, s.connected_peers || 0);
      return {
        id: s.id,
        nodeCode: s.node_code,
        name: s.name,
        countryName: s.country_name,
        countryCode: s.country_code,
        city: s.city,
        endpoint: s.endpoint,
        serverPublicKey: s.server_public_key,
        agentUrl: s.agent_url,
        dnsServers: s.dns_servers,
        ipv4Subnet: s.ipv4_subnet,
        ipv6Subnet: s.ipv6_subnet,
        tier: s.tier,
        maxPeers: s.max_peers,
        connectedPeers: activePeers,
        loadPercent: Math.min(100, Math.round((activePeers / Math.max(1, s.max_peers)) * 100)),
        healthStatus: s.health_status,
        latencyMs: s.latency_ms,
        totalRxBytes: Number(s.total_rx_bytes),
        totalTxBytes: Number(s.total_tx_bytes),
        lastHealthCheckAt: s.last_health_check_at
      };
    })
  });
});

// POST /api/v1/admin/servers - Add a new real WireGuard VPS server
router.post('/servers', requireAdmin, async (req, res) => {
  const {
    nodeCode,
    name,
    countryName,
    countryCode,
    city,
    endpoint,
    serverPublicKey,
    agentUrl,
    ipv4Subnet,
    ipv6Subnet,
    tier,
    maxPeers
  } = req.body || {};

  if (!nodeCode || !name || !countryName || !countryCode || !city) {
    return res.status(400).json({ error: 'nodeCode, name, countryName, countryCode, and city are required' });
  }
  if (serverPublicKey && !isValidWireGuardKey(serverPublicKey)) {
    return res.status(400).json({ error: 'Invalid WireGuard Curve25519 serverPublicKey format' });
  }

  try {
    const cleanCode = String(nodeCode).trim().toUpperCase();
    const hasLiveConfig = Boolean(endpoint && serverPublicKey);
    const insertRes = await pool.query(
      `INSERT INTO vpn_servers
       (node_code, name, country_name, country_code, city, endpoint, server_public_key, agent_url,
        ipv4_subnet, ipv6_subnet, tier, max_peers, health_status)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
       RETURNING id, node_code`,
      [
        cleanCode,
        String(name).trim(),
        String(countryName).trim(),
        String(countryCode).trim().toUpperCase().slice(0, 2),
        String(city).trim(),
        endpoint ? String(endpoint).trim() : null,
        serverPublicKey ? String(serverPublicKey).trim() : null,
        agentUrl ? String(agentUrl).trim() : null,
        ipv4Subnet || '10.8.20.0/24',
        ipv6Subnet || 'fd80:4e58:5220::/64',
        tier === 'pro' ? 'pro' : 'free',
        Number(maxPeers || 250),
        hasLiveConfig ? 'online' : 'pending_vps'
      ]
    );
    await checkAllProvisionedNodes();
    return res.status(201).json({ ok: true, server: insertRes.rows[0] });
  } catch (err) {
    await logBackendError({ source: 'admin', endpoint: 'POST /servers', message: err.message });
    return res.status(400).json({ error: err.message });
  }
});

// DELETE /api/v1/admin/servers/:id
router.delete('/servers/:id', requireAdmin, async (req, res) => {
  const result = await pool.query('DELETE FROM vpn_servers WHERE id = $1 RETURNING node_code', [req.params.id]);
  if (result.rowCount === 0) {
    return res.status(404).json({ error: 'Server not found' });
  }
  return res.json({ ok: true, deletedNodeCode: result.rows[0].node_code });
});

// Auto-registration endpoint called by scripts/setup-wg-vps.sh
router.post('/nodes/:nodeCode/activate', requireAdmin, async (req, res) => {
  const nodeCode = req.params.nodeCode.toUpperCase();
  const { endpoint, serverPublicKey, agentUrl, tier, dnsServers } = req.body || {};
  if (!endpoint || !endpoint.includes(':') || !isValidWireGuardKey(serverPublicKey)) {
    return res.status(400).json({
      error: 'Valid endpoint (host:51820) and WireGuard Curve25519 serverPublicKey required'
    });
  }

  const updateRes = await pool.query(
    `UPDATE vpn_servers
     SET endpoint = $2,
         server_public_key = $3,
         agent_url = COALESCE($4, agent_url),
         tier = COALESCE($5, tier),
         dns_servers = COALESCE($6, dns_servers),
         health_status = 'online',
         last_health_check_at = NOW()
     WHERE node_code = $1
     RETURNING node_code, name, endpoint, health_status`,
    [nodeCode, endpoint.trim(), serverPublicKey.trim(), agentUrl || null, tier || null, dnsServers || null]
  );

  if (updateRes.rowCount === 0) {
    return res.status(404).json({ error: `Unknown node_code ${nodeCode}` });
  }

  await checkAllProvisionedNodes();
  const refreshed = await pool.query(
    'SELECT node_code, name, endpoint, health_status, latency_ms FROM vpn_servers WHERE node_code = $1',
    [nodeCode]
  );
  return res.json({ node: refreshed.rows[0] });
});

// GET /api/v1/admin/errors
router.get('/errors', requireAdmin, async (_req, res) => {
  const result = await pool.query(
    `SELECT e.id, e.level, e.source, e.endpoint, e.message, e.details, e.node_code, e.resolved, e.created_at,
            u.email AS user_email
     FROM error_logs e
     LEFT JOIN users u ON u.id = e.user_id
     ORDER BY e.created_at DESC
     LIMIT 150`
  );
  return res.json({
    errors: result.rows.map((r) => ({
      id: r.id,
      level: r.level,
      source: r.source,
      endpoint: r.endpoint,
      message: r.message,
      details: r.details,
      nodeCode: r.node_code,
      userEmail: r.user_email,
      resolved: r.resolved,
      createdAt: r.created_at
    }))
  });
});

// PATCH /api/v1/admin/errors/:id/resolve
router.patch('/errors/:id/resolve', requireAdmin, async (req, res) => {
  await pool.query('UPDATE error_logs SET resolved = TRUE WHERE id = $1', [req.params.id]);
  return res.json({ ok: true });
});

export default router;
