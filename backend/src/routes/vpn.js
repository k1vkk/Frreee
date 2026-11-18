import { Router } from 'express';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { pool } from '../db.js';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/asyncHandler.js';
import { isValidUuid, isValidWireGuardKey } from '../utils/validators.js';

const execFileAsync = promisify(execFile);
const router = Router();

export { isValidWireGuardKey };

async function callVpsAgent(agentUrl, path, method = 'GET', body = null) {
  const secret = (process.env.NODE_AGENT_SECRET || '').trim();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 5000);
  try {
    const response = await fetch(`${agentUrl.replace(/\/+$/, '')}${path}`, {
      method,
      headers: {
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(secret ? { 'x-agent-secret': secret } : {})
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
      signal: controller.signal
    });
    const text = await response.text();
    let json = {};
    try {
      json = text ? JSON.parse(text) : {};
    } catch {
      json = {};
    }
    return { ok: response.ok, status: response.status, data: json };
  } finally {
    clearTimeout(timer);
  }
}

async function syncPeerOnWireGuardNode(server, clientPublicKey, allowedOnVps) {
  if (server.agent_url) {
    const res = await callVpsAgent(server.agent_url, '/peers', 'POST', {
      publicKey: clientPublicKey,
      allowedIps: allowedOnVps
    });
    if (!res.ok) {
      throw new Error(`VPS node agent rejected peer sync (HTTP ${res.status})`);
    }
  } else if (process.env.WG_INTERFACE) {
    await execFileAsync('wg', [
      'set',
      process.env.WG_INTERFACE,
      'peer',
      clientPublicKey,
      'allowed-ips',
      allowedOnVps
    ]);
  } else {
    throw new Error('Selected server has no live WireGuard Node Agent configured');
  }
}

async function removePeerFromWireGuardNode(server, clientPublicKey) {
  const encodedKey = encodeURIComponent(clientPublicKey);
  if (server.agent_url) {
    await callVpsAgent(server.agent_url, `/peers/${encodedKey}`, 'DELETE');
  } else if (process.env.WG_INTERFACE) {
    await execFileAsync('wg', [
      'set',
      process.env.WG_INTERFACE,
      'peer',
      clientPublicKey,
      'remove'
    ]);
  }
}

export function buildWgQuickConfig({ assignedIpv4, assignedIpv6, dnsServers, mtu, serverPublicKey, endpoint, allowedIps }) {
  return [
    '[Interface]',
    '# PrivateKey stays strictly on client device (never stored on server)',
    `Address = ${assignedIpv4}, ${assignedIpv6}`,
    `DNS = ${dnsServers}`,
    `MTU = ${mtu}`,
    '',
    '[Peer]',
    `PublicKey = ${serverPublicKey}`,
    `AllowedIPs = ${allowedIps}`,
    `Endpoint = ${endpoint}`,
    'PersistentKeepalive = 25'
  ].join('\n');
}

export async function provisionAndSyncPeer({ userId, serverId, clientPublicKey, markConnected }) {
  if (!isValidUuid(serverId)) {
    const err = new Error('Invalid serverId UUID');
    err.statusCode = 400;
    throw err;
  }
  const cleanKey = clientPublicKey.trim();
  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // Lock server row to eliminate subnet IP allocation race conditions
    const serverRes = await client.query(
      `SELECT * FROM vpn_servers
       WHERE id = $1 AND is_active = TRUE AND health_status = 'online'
         AND endpoint IS NOT NULL AND server_public_key IS NOT NULL
       FOR UPDATE`,
      [serverId]
    );
    if (serverRes.rowCount === 0) {
      await client.query('ROLLBACK');
      const err = new Error('Online WireGuard VPS node not found or not provisioned');
      err.statusCode = 404;
      throw err;
    }
    const server = serverRes.rows[0];

    if (server.tier === 'pro') {
      const subRes = await client.query(
        'SELECT plan_tier, status, expires_at FROM subscriptions WHERE user_id = $1',
        [userId]
      );
      const sub = subRes.rows[0];
      const expired = sub?.expires_at && new Date(sub.expires_at) < new Date();
      if (!sub || sub.status !== 'active' || sub.plan_tier === 'free' || expired) {
        await client.query('ROLLBACK');
        const err = new Error('Active NEXORA Pro subscription required for this node');
        err.statusCode = 403;
        throw err;
      }
    }

    const existingPeer = await client.query(
      `SELECT host(assigned_ipv4) AS ipv4, host(assigned_ipv6) AS ipv6
       FROM wireguard_peers
       WHERE server_id = $1 AND client_public_key = $2`,
      [serverId, cleanKey]
    );

    let assignedIpv4;
    let assignedIpv6;
    if (existingPeer.rowCount > 0) {
      assignedIpv4 = `${existingPeer.rows[0].ipv4}/32`;
      assignedIpv6 = `${existingPeer.rows[0].ipv6}/128`;
      await client.query(
        `UPDATE wireguard_peers
         SET is_connected = COALESCE($3, is_connected),
             last_connected_at = CASE WHEN $3 = TRUE THEN NOW() ELSE last_connected_at END,
             disconnected_at = CASE WHEN $3 = TRUE THEN NULL ELSE disconnected_at END
         WHERE server_id = $1 AND client_public_key = $2`,
        [serverId, cleanKey, markConnected]
      );
    } else {
      const usedRes = await client.query(
        'SELECT host(assigned_ipv4) AS ip FROM wireguard_peers WHERE server_id = $1',
        [serverId]
      );
      const usedOctets = new Set(usedRes.rows.map((r) => Number(r.ip.split('.')[3])));
      let nextOctet = 2;
      while (usedOctets.has(nextOctet) && nextOctet <= 253) nextOctet++;
      if (nextOctet > 253) {
        await client.query('ROLLBACK');
        const err = new Error('Server subnet capacity reached');
        err.statusCode = 503;
        throw err;
      }
      const v4Prefix = server.ipv4_subnet.split('.').slice(0, 3).join('.');
      const v6Prefix = server.ipv6_subnet.split('::')[0];
      assignedIpv4 = `${v4Prefix}.${nextOctet}/32`;
      assignedIpv6 = `${v6Prefix}::${nextOctet}/128`;

      await client.query(
        `INSERT INTO wireguard_peers (user_id, server_id, client_public_key, assigned_ipv4, assigned_ipv6, is_connected)
         VALUES ($1, $2, $3, $4, $5, $6)`,
        [userId, serverId, cleanKey, assignedIpv4, assignedIpv6, Boolean(markConnected)]
      );
    }

    const allowedOnVps = `${assignedIpv4},${assignedIpv6}`;
    await syncPeerOnWireGuardNode(server, cleanKey, allowedOnVps);

    if (markConnected === true) {
      await client.query(
        `INSERT INTO connection_logs (user_id, server_id, node_code, client_public_key, assigned_ipv4, assigned_ipv6)
         VALUES ($1, $2, $3, $4, $5, $6)`,
        [userId, server.id, server.node_code, cleanKey, assignedIpv4, assignedIpv6]
      );
    }

    await client.query('COMMIT');

    const allowedIps = '0.0.0.0/0, ::/0';
    const mtu = 1420;
    const wgQuickConfig = buildWgQuickConfig({
      assignedIpv4,
      assignedIpv6,
      dnsServers: server.dns_servers,
      mtu,
      serverPublicKey: server.server_public_key,
      endpoint: server.endpoint,
      allowedIps
    });

    return {
      ok: true,
      serverId: server.id,
      nodeCode: server.node_code,
      serverName: server.name,
      countryName: server.country_name,
      assignedAddress: `${assignedIpv4}, ${assignedIpv6}`,
      assignedIpv4,
      assignedIpv6,
      serverPublicKey: server.server_public_key,
      endpoint: server.endpoint,
      dnsServers: server.dns_servers,
      allowedIps,
      mtu,
      persistentKeepalive: 25,
      wgQuickConfig
    };
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

async function resolveServerId({ serverId, nodeCode }) {
  if (serverId) return serverId;
  if (nodeCode) {
    const lookup = await pool.query('SELECT id FROM vpn_servers WHERE node_code = $1', [String(nodeCode).toUpperCase()]);
    if (lookup.rowCount === 0) return null;
    return lookup.rows[0].id;
  }
  return null;
}

// POST /api/vpn/connect
router.post('/connect', requireAuth, asyncHandler(async (req, res) => {
  const { serverId, nodeCode, clientPublicKey } = req.body || {};
  if (!isValidWireGuardKey(clientPublicKey) || (!serverId && !nodeCode)) {
    return res.status(400).json({ error: 'Valid serverId (or nodeCode) and WireGuard Curve25519 clientPublicKey required' });
  }

  try {
    const targetServerId = await resolveServerId({ serverId, nodeCode });
    if (!targetServerId) return res.status(404).json({ error: 'Unknown GCC nodeCode' });

    const result = await provisionAndSyncPeer({
      userId: req.auth.userId,
      serverId: targetServerId,
      clientPublicKey,
      markConnected: true
    });
    return res.json(result);
  } catch (err) {
    return res.status(err.statusCode || 500).json({ error: err.message || 'VPN connect failed' });
  }
}));

// POST /api/vpn/register-peer - Registers client WireGuard public key without marking active tunnel session
router.post('/register-peer', requireAuth, asyncHandler(async (req, res) => {
  const { serverId, nodeCode, clientPublicKey } = req.body || {};
  if (!isValidWireGuardKey(clientPublicKey) || (!serverId && !nodeCode)) {
    return res.status(400).json({ error: 'Valid serverId (or nodeCode) and WireGuard Curve25519 clientPublicKey required' });
  }

  try {
    const targetServerId = await resolveServerId({ serverId, nodeCode });
    if (!targetServerId) return res.status(404).json({ error: 'Unknown GCC nodeCode' });

    const result = await provisionAndSyncPeer({
      userId: req.auth.userId,
      serverId: targetServerId,
      clientPublicKey,
      markConnected: false
    });
    return res.status(201).json(result);
  } catch (err) {
    return res.status(err.statusCode || 500).json({ error: err.message || 'Peer registration failed' });
  }
}));

// POST /api/vpn/disconnect
router.post('/disconnect', requireAuth, asyncHandler(async (req, res) => {
  const { serverId, clientPublicKey, rxBytes, txBytes, reason } = req.body || {};
  if (serverId && !isValidUuid(serverId)) {
    return res.status(400).json({ error: 'Invalid serverId UUID' });
  }
  try {
    const peersRes = await pool.query(
      `SELECT p.id, p.server_id, p.client_public_key, p.last_connected_at, s.agent_url, s.node_code
       FROM wireguard_peers p
       JOIN vpn_servers s ON s.id = p.server_id
       WHERE p.user_id = $1
         AND ($2::uuid IS NULL OR p.server_id = $2::uuid)
         AND ($3::text IS NULL OR p.client_public_key = $3::text)
         AND p.is_connected = TRUE`,
      [req.auth.userId, serverId || null, clientPublicKey ? clientPublicKey.trim() : null]
    );

    const safeRx = Number.isFinite(Number(rxBytes)) ? Number(rxBytes) : 0;
    const safeTx = Number.isFinite(Number(txBytes)) ? Number(txBytes) : 0;

    for (const peer of peersRes.rows) {
      try {
        await removePeerFromWireGuardNode(peer, peer.client_public_key);
      } catch (agentErr) {
        console.warn(`Agent disconnect warning for ${peer.node_code}:`, agentErr.message);
      }
      await pool.query(
        `UPDATE wireguard_peers
         SET is_connected = FALSE,
             disconnected_at = NOW(),
             rx_bytes = GREATEST(rx_bytes, $2::bigint),
             tx_bytes = GREATEST(tx_bytes, $3::bigint)
         WHERE id = $1`,
        [peer.id, safeRx, safeTx]
      );
      const dur = peer.last_connected_at
        ? Math.max(0, Math.round((Date.now() - new Date(peer.last_connected_at).getTime()) / 1000))
        : 0;
      await pool.query(
        `UPDATE connection_logs
         SET disconnected_at = NOW(),
             duration_seconds = $4,
             rx_bytes = GREATEST(rx_bytes, $5::bigint),
             tx_bytes = GREATEST(tx_bytes, $6::bigint),
             disconnect_reason = COALESCE($7, 'user_disconnect')
         WHERE id = (
           SELECT id FROM connection_logs
           WHERE user_id = $1 AND server_id = $2 AND client_public_key = $3 AND disconnected_at IS NULL
           ORDER BY connected_at DESC
           LIMIT 1
         )`,
        [req.auth.userId, peer.server_id, peer.client_public_key, dur, safeRx, safeTx, reason || 'user_disconnect']
      );
    }

    return res.json({
      ok: true,
      disconnected: true,
      peersClosed: peersRes.rowCount,
      timestamp: new Date().toISOString()
    });
  } catch (err) {
    return res.status(500).json({ error: err.message || 'VPN disconnect failed' });
  }
}));

// GET /api/vpn/status
router.get('/status', requireAuth, asyncHandler(async (req, res) => {
  const clientPublicKey = typeof req.query.clientPublicKey === 'string' ? req.query.clientPublicKey.trim() : null;
  const serverId = typeof req.query.serverId === 'string' ? req.query.serverId.trim() : null;
  if (serverId && !isValidUuid(serverId)) {
    return res.status(400).json({ error: 'Invalid serverId UUID' });
  }

  try {
    const peerRes = await pool.query(
      `SELECT p.server_id, p.client_public_key, host(p.assigned_ipv4) AS ipv4, host(p.assigned_ipv6) AS ipv6,
              p.is_connected, p.rx_bytes, p.tx_bytes, p.last_handshake_at, p.last_connected_at,
              s.node_code, s.name AS server_name, s.country_name, s.endpoint, s.agent_url,
              s.health_status, s.latency_ms
       FROM wireguard_peers p
       JOIN vpn_servers s ON s.id = p.server_id
       WHERE p.user_id = $1
         AND ($2::uuid IS NULL OR p.server_id = $2::uuid)
         AND ($3::text IS NULL OR p.client_public_key = $3::text)
       ORDER BY p.is_connected DESC, p.last_connected_at DESC
       LIMIT 1`,
      [req.auth.userId, serverId || null, clientPublicKey || null]
    );

    if (peerRes.rowCount === 0) {
      return res.json({ connected: false, status: 'disconnected' });
    }

    const row = peerRes.rows[0];
    let liveHandshakeAt = row.last_handshake_at;
    let rxBytes = Number(row.rx_bytes || 0);
    let txBytes = Number(row.tx_bytes || 0);
    let agentPeerPresent = false;

    if (row.agent_url && row.is_connected) {
      try {
        const probe = await callVpsAgent(row.agent_url, `/peers/${encodeURIComponent(row.client_public_key)}`, 'GET');
        if (probe.ok && probe.data?.peer) {
          agentPeerPresent = true;
          liveHandshakeAt = probe.data.peer.latestHandshakeAt || liveHandshakeAt;
          rxBytes = Math.max(rxBytes, Number(probe.data.peer.rxBytes || 0));
          txBytes = Math.max(txBytes, Number(probe.data.peer.txBytes || 0));
          await pool.query(
            `UPDATE wireguard_peers
             SET last_handshake_at = COALESCE($2::timestamptz, last_handshake_at),
                 rx_bytes = $3,
                 tx_bytes = $4
             WHERE user_id = $1 AND server_id = $5 AND client_public_key = $6`,
            [req.auth.userId, liveHandshakeAt, rxBytes, txBytes, row.server_id, row.client_public_key]
          );
        }
      } catch {
        // Agent unreachable; report database state accurately
      }
    }

    return res.json({
      connected: Boolean(row.is_connected && row.health_status === 'online'),
      status: row.is_connected ? (row.health_status === 'online' ? 'connected' : 'degraded') : 'disconnected',
      agentPeerPresent,
      serverId: row.server_id,
      nodeCode: row.node_code,
      serverName: row.server_name,
      countryName: row.country_name,
      endpoint: row.endpoint,
      assignedIpv4: `${row.ipv4}/32`,
      assignedIpv6: `${row.ipv6}/128`,
      lastHandshakeAt: liveHandshakeAt,
      lastConnectedAt: row.last_connected_at,
      rxBytes,
      txBytes,
      healthStatus: row.health_status,
      latencyMs: row.latency_ms
    });
  } catch (err) {
    return res.status(500).json({ error: err.message || 'Failed to read VPN status' });
  }
}));

// GET & POST /api/vpn/config
async function handleConfigRequest(req, res) {
  const source = req.method === 'GET' ? req.query : req.body;
  const { serverId, nodeCode, clientPublicKey, format } = source || {};

  if (!isValidWireGuardKey(clientPublicKey) || (!serverId && !nodeCode)) {
    return res.status(400).json({ error: 'Valid serverId (or nodeCode) and WireGuard Curve25519 clientPublicKey required' });
  }

  try {
    const targetServerId = await resolveServerId({ serverId, nodeCode });
    if (!targetServerId) return res.status(404).json({ error: 'Unknown GCC nodeCode' });

    const result = await provisionAndSyncPeer({
      userId: req.auth.userId,
      serverId: targetServerId,
      clientPublicKey,
      markConnected: null
    });

    if (format === 'conf') {
      res.setHeader('Content-Type', 'text/plain; charset=utf-8');
      res.setHeader('Content-Disposition', `attachment; filename="nexora-${result.nodeCode.toLowerCase()}.conf"`);
      return res.send(result.wgQuickConfig);
    }

    return res.json(result);
  } catch (err) {
    return res.status(err.statusCode || 500).json({ error: err.message || 'Failed to generate WireGuard config' });
  }
}

router.get('/config', requireAuth, asyncHandler(handleConfigRequest));
router.post('/config', requireAuth, asyncHandler(handleConfigRequest));

export default router;
