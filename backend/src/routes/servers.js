import { Router } from 'express';
import { pool } from '../db.js';

const router = Router();

function formatServerRow(row) {
  return {
    id: row.id,
    nodeCode: row.node_code,
    name: row.name,
    countryName: row.country_name,
    countryCode: row.country_code,
    city: row.city,
    endpoint: row.endpoint,
    serverPublicKey: row.server_public_key,
    dnsServers: row.dns_servers,
    tier: row.tier,
    healthStatus: row.health_status,
    latencyMs: row.latency_ms,
    lastHealthCheckAt: row.last_health_check_at,
    loadPercent: Math.min(100, Math.round((row.active_peers / Math.max(1, row.max_peers)) * 100))
  };
}

// Strictly return ONLY provisioned and currently online VPS servers
router.get('/', async (_req, res) => {
  const result = await pool.query(
    `SELECT s.id, s.node_code, s.name, s.country_name, s.country_code, s.city, s.endpoint,
            s.server_public_key, s.dns_servers, s.tier, s.max_peers, s.health_status,
            s.latency_ms, s.last_health_check_at,
            GREATEST(s.active_peers_reported, COUNT(p.id) FILTER (WHERE p.is_connected = TRUE)::int) AS active_peers
     FROM vpn_servers s
     LEFT JOIN wireguard_peers p ON p.server_id = s.id
     WHERE s.is_active = TRUE
       AND s.health_status = 'online'
       AND s.endpoint IS NOT NULL
       AND s.server_public_key IS NOT NULL
     GROUP BY s.id
     ORDER BY s.latency_ms ASC NULLS LAST, s.tier ASC, s.country_name ASC, s.node_code ASC`
  );
  res.json({ servers: result.rows.map(formatServerRow) });
});

// Select best online server ordered by measured latency + load
router.get('/best', async (_req, res) => {
  const result = await pool.query(
    `SELECT s.id, s.node_code, s.name, s.country_name, s.country_code, s.city, s.endpoint,
            s.server_public_key, s.dns_servers, s.tier, s.max_peers, s.health_status,
            s.latency_ms, s.last_health_check_at,
            GREATEST(s.active_peers_reported, COUNT(p.id) FILTER (WHERE p.is_connected = TRUE)::int) AS active_peers
     FROM vpn_servers s
     LEFT JOIN wireguard_peers p ON p.server_id = s.id
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
});

export default router;
