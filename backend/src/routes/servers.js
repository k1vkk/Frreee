import { Router } from 'express';
import { pool } from '../db.js';
import { asyncHandler } from '../middleware/asyncHandler.js';
import { isValidUuid } from '../utils/validators.js';

const router = Router();

export function formatServerRow(row) {
  const activeUsers = Number(row.active_peers || 0);
  const maxPeers = Math.max(1, Number(row.max_peers || 250));
  const rxBytes = Number(row.network_rx_bytes || row.peer_rx_bytes || 0);
  const txBytes = Number(row.network_tx_bytes || row.peer_tx_bytes || 0);
  return {
    id: row.id,
    nodeCode: row.node_code,
    name: row.name,
    countryName: row.country_name,
    countryCode: row.country_code,
    city: row.city,
    publicIp: row.public_ip || (row.endpoint ? row.endpoint.split(':')[0] : null),
    serverPort: Number(row.server_port || 51820),
    endpoint: row.endpoint,
    wireguardEndpoint: row.endpoint,
    serverPublicKey: row.server_public_key,
    wireguardPublicKey: row.server_public_key,
    dnsServers: row.dns_servers,
    tier: row.tier,
    healthStatus: row.health_status,
    status: row.health_status,
    latencyMs: row.latency_ms,
    jitterMs: row.jitter_ms,
    packetLossPercent: Number(row.packet_loss_percent || 0),
    cpuUsage: Number(row.cpu_usage || 0),
    ramUsage: Number(row.ram_usage || 0),
    networkTrafficBytes: rxBytes + txBytes,
    networkRxBytes: rxBytes,
    networkTxBytes: txBytes,
    activeUsers,
    maxPeers,
    lastHealthCheckAt: row.last_health_check_at,
    loadPercent: Math.min(100, Math.round((activeUsers / maxPeers) * 100))
  };
}

const SERVER_SELECT_SQL = `
  SELECT s.*,
         GREATEST(s.active_peers_reported, COUNT(p.id) FILTER (WHERE p.is_connected = TRUE)::int) AS active_peers,
         COALESCE(SUM(p.rx_bytes), 0)::bigint AS peer_rx_bytes,
         COALESCE(SUM(p.tx_bytes), 0)::bigint AS peer_tx_bytes
  FROM vpn_servers s
  LEFT JOIN wireguard_peers p ON p.server_id = s.id
`;

// GET /api/servers - Strictly return ONLY provisioned and currently online VPS servers
router.get('/', asyncHandler(async (_req, res) => {
  const result = await pool.query(
    `${SERVER_SELECT_SQL}
     WHERE s.is_active = TRUE
       AND s.health_status = 'online'
       AND s.endpoint IS NOT NULL
       AND s.server_public_key IS NOT NULL
     GROUP BY s.id
     ORDER BY s.latency_ms ASC NULLS LAST, s.tier ASC, s.country_name ASC, s.node_code ASC`
  );
  res.json({ servers: result.rows.map(formatServerRow) });
}));

// GET /api/servers/health - Real-time health summary of all GCC deployment slots (must be defined BEFORE /:id)
router.get('/health', asyncHandler(async (_req, res) => {
  const result = await pool.query(
    `${SERVER_SELECT_SQL}
     GROUP BY s.id
     ORDER BY s.country_code ASC, s.node_code ASC`
  );
  const nodes = result.rows.map(formatServerRow);
  const onlineCount = nodes.filter((n) => n.status === 'online').length;
  const pendingCount = nodes.filter((n) => n.status === 'pending_vps').length;
  const offlineCount = nodes.filter((n) => n.status === 'offline' || n.status === 'degraded').length;
  res.json({
    ok: true,
    summary: {
      total: nodes.length,
      online: onlineCount,
      pendingVps: pendingCount,
      offline: offlineCount
    },
    servers: nodes
  });
}));

// GET /api/servers/best - Best online server ordered by measured latency + load
router.get('/best', asyncHandler(async (_req, res) => {
  const result = await pool.query(
    `${SERVER_SELECT_SQL}
     WHERE s.is_active = TRUE
       AND s.health_status = 'online'
       AND s.endpoint IS NOT NULL
       AND s.server_public_key IS NOT NULL
     GROUP BY s.id
     ORDER BY s.latency_ms ASC NULLS LAST, active_peers ASC
     LIMIT 1`
  );
  if (result.rowCount === 0) {
    return res.status(404).json({ error: 'No online WireGuard VPS nodes available right now' });
  }
  return res.json({ server: formatServerRow(result.rows[0]) });
}));

// GET /api/servers/:id - Specific server metadata & telemetry
router.get('/:id', asyncHandler(async (req, res) => {
  if (!isValidUuid(req.params.id)) {
    return res.status(400).json({ error: 'Invalid server UUID' });
  }
  const result = await pool.query(
    `${SERVER_SELECT_SQL}
     WHERE s.id = $1
     GROUP BY s.id`,
    [req.params.id]
  );
  if (result.rowCount === 0) {
    return res.status(404).json({ error: 'Server not found' });
  }
  return res.json({ server: formatServerRow(result.rows[0]) });
}));

export default router;
