import net from 'node:net';
import { pool } from '../db.js';
import { logBackendError } from './errorLogger.js';

function parseEndpoint(endpoint) {
  if (!endpoint || !endpoint.includes(':')) return null;
  const idx = endpoint.lastIndexOf(':');
  const host = endpoint.slice(0, idx).replace(/^\[|\]$/g, '');
  const port = Number(endpoint.slice(idx + 1));
  return { host, port };
}

async function probeAgentHealth(agentUrl) {
  const secret = process.env.NODE_AGENT_SECRET || '';
  const startedAt = Date.now();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 4000);
  try {
    const res = await fetch(`${agentUrl.replace(/\/+$/, '')}/health`, {
      headers: secret ? { 'x-agent-secret': secret } : {},
      signal: controller.signal
    });
    if (!res.ok) return { ok: false, latencyMs: null, activePeers: 0 };
    const data = await res.json();
    return {
      ok: Boolean(data.ok),
      latencyMs: Math.max(1, Date.now() - startedAt),
      activePeers: Number(data.activePeers || 0)
    };
  } catch {
    return { ok: false, latencyMs: null, activePeers: 0 };
  } finally {
    clearTimeout(timer);
  }
}

function probeTcpReachability(host, port = 22) {
  return new Promise((resolve) => {
    const startedAt = Date.now();
    const socket = new net.Socket();
    socket.setTimeout(3000);
    socket.once('connect', () => {
      const latencyMs = Math.max(1, Date.now() - startedAt);
      socket.destroy();
      resolve({ ok: true, latencyMs });
    });
    socket.once('timeout', () => {
      socket.destroy();
      resolve({ ok: false, latencyMs: null });
    });
    socket.once('error', () => {
      socket.destroy();
      resolve({ ok: false, latencyMs: null });
    });
    socket.connect(port, host);
  });
}

export async function checkAllProvisionedNodes() {
  const res = await pool.query(
    `SELECT id, node_code, endpoint, server_public_key, agent_url, health_status
     FROM vpn_servers
     WHERE is_active = TRUE`
  );

  for (const node of res.rows) {
    if (!node.endpoint || !node.server_public_key) {
      await pool.query(
        `UPDATE vpn_servers
         SET health_status = 'pending_vps', last_health_check_at = NOW()
         WHERE id = $1`,
        [node.id]
      );
      continue;
    }

    if (node.agent_url) {
      const probe = await probeAgentHealth(node.agent_url);
      const nextStatus = probe.ok ? 'online' : 'offline';
      await pool.query(
        `UPDATE vpn_servers
         SET health_status = $2,
             latency_ms = $3,
             active_peers_reported = $4,
             last_health_check_at = NOW()
         WHERE id = $1`,
        [node.id, nextStatus, probe.latencyMs, probe.activePeers]
      );
      if (!probe.ok && node.health_status !== 'offline') {
        await logBackendError({
          level: 'error',
          source: 'node-monitor',
          nodeCode: node.node_code,
          message: `GCC WireGuard node ${node.node_code} (${node.endpoint}) failed health probe and went offline.`
        });
      }
    } else {
      const parsed = parseEndpoint(node.endpoint);
      const tcpProbe = parsed ? await probeTcpReachability(parsed.host, 443) : { ok: false, latencyMs: null };
      const nextStatus = tcpProbe.ok ? 'online' : 'offline';
      await pool.query(
        `UPDATE vpn_servers
         SET health_status = $2,
             latency_ms = $3,
             last_health_check_at = NOW()
         WHERE id = $1`,
        [node.id, nextStatus, tcpProbe.latencyMs]
      );
    }
  }
}

export function startNodeMonitorLoop(intervalMs = 30000) {
  checkAllProvisionedNodes().catch((err) => console.error('Initial node monitor error:', err));
  return setInterval(() => {
    checkAllProvisionedNodes().catch((err) => console.error('Node monitor error:', err));
  }, intervalMs);
}
