import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { pool } from '../db.js';
import { logBackendError } from './errorLogger.js';

const execFileAsync = promisify(execFile);

async function probeAgentHealth(agentUrl) {
  const secret = (process.env.NODE_AGENT_SECRET || '').trim();
  const startedAt = Date.now();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 4500);
  try {
    const res = await fetch(`${agentUrl.replace(/\/+$/, '')}/health`, {
      headers: secret ? { 'x-agent-secret': secret } : {},
      signal: controller.signal
    });
    if (!res.ok) {
      return { ok: false, latencyMs: null, activePeers: 0, cpuUsage: 0, ramUsage: 0, rxBytes: 0, txBytes: 0 };
    }
    const data = await res.json();
    return {
      ok: Boolean(data.ok),
      latencyMs: Math.max(1, Date.now() - startedAt),
      activePeers: Number(data.activePeers || 0),
      cpuUsage: Number(data.cpuUsage || 0),
      ramUsage: Number(data.ramUsage || 0),
      rxBytes: Number(data.rxBytes || 0),
      txBytes: Number(data.txBytes || 0)
    };
  } catch {
    return { ok: false, latencyMs: null, activePeers: 0, cpuUsage: 0, ramUsage: 0, rxBytes: 0, txBytes: 0 };
  } finally {
    clearTimeout(timer);
  }
}

async function probeLocalWireGuardInterface() {
  const iface = process.env.WG_INTERFACE;
  if (!iface) return null;
  try {
    const { stdout } = await execFileAsync('wg', ['show', iface, 'dump']);
    const lines = stdout.trim().split('\n').filter(Boolean);
    const peerLines = lines.slice(1);
    let rxBytes = 0;
    let txBytes = 0;
    for (const line of peerLines) {
      const parts = line.split('\t');
      rxBytes += Number(parts[5] || 0);
      txBytes += Number(parts[6] || 0);
    }
    return { ok: true, activePeers: peerLines.length, rxBytes, txBytes };
  } catch {
    return { ok: false, activePeers: 0, rxBytes: 0, txBytes: 0 };
  }
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
         SET health_status = 'pending_vps',
             latency_ms = NULL,
             last_health_check_at = NOW()
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
             cpu_usage = $5,
             ram_usage = $6,
             network_rx_bytes = GREATEST(network_rx_bytes, $7),
             network_tx_bytes = GREATEST(network_tx_bytes, $8),
             last_health_check_at = NOW()
         WHERE id = $1`,
        [
          node.id,
          nextStatus,
          probe.latencyMs,
          probe.activePeers,
          probe.cpuUsage,
          probe.ramUsage,
          probe.rxBytes,
          probe.txBytes
        ]
      );
      if (!probe.ok && node.health_status !== 'offline') {
        await logBackendError({
          level: 'error',
          source: 'node-monitor',
          nodeCode: node.node_code,
          message: `GCC WireGuard node ${node.node_code} (${node.endpoint}) failed WireGuard Node Agent health probe.`
        });
      }
    } else if (process.env.WG_INTERFACE) {
      const localWg = await probeLocalWireGuardInterface();
      const nextStatus = localWg?.ok ? 'online' : 'offline';
      await pool.query(
        `UPDATE vpn_servers
         SET health_status = $2,
             active_peers_reported = $3,
             network_rx_bytes = GREATEST(network_rx_bytes, $4),
             network_tx_bytes = GREATEST(network_tx_bytes, $5),
             last_health_check_at = NOW()
         WHERE id = $1`,
        [node.id, nextStatus, localWg?.activePeers || 0, localWg?.rxBytes || 0, localWg?.txBytes || 0]
      );
    } else {
      // No live WireGuard agent_url or local WG_INTERFACE configured; cannot verify live tunnel readiness.
      await pool.query(
        `UPDATE vpn_servers
         SET health_status = 'offline',
             last_health_check_at = NOW()
         WHERE id = $1`,
        [node.id]
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
