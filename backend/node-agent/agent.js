import http from 'node:http';
import os from 'node:os';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);
const PORT = Number(process.env.NODE_AGENT_PORT || 51821);
const SECRET = (process.env.NODE_AGENT_SECRET || '').trim();
const WG_IFACE = process.env.WG_INTERFACE || 'wg0';

if (!SECRET || SECRET.startsWith('REPLACE_WITH_')) {
  console.error('FATAL: NODE_AGENT_SECRET must be configured before starting nexora-node-agent.');
  process.exit(1);
}

export function isValidWgKey(key) {
  return typeof key === 'string' && /^[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]=$/.test(key.trim());
}

function sendJson(res, status, data) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(data));
}

async function readBody(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  return JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
}

function sampleCpuPercent() {
  const cpus = os.cpus() || [];
  if (cpus.length === 0) return 0;
  const load1m = os.loadavg()[0] || 0;
  return Math.min(100, Math.round(((load1m / cpus.length) * 100) * 10) / 10);
}

function sampleRamPercent() {
  const total = os.totalmem() || 1;
  const free = os.freemem();
  return Math.min(100, Math.round(((total - free) / total) * 1000) / 10);
}

export function parseWgDump(stdout) {
  const lines = stdout.trim().split('\n').filter(Boolean);
  const ifaceParts = (lines[0] || '').split('\t');
  let totalRxBytes = 0;
  let totalTxBytes = 0;
  const peers = lines.slice(1).map((line) => {
    const parts = line.split('\t');
    const latestHandshakeSec = Number(parts[4] || 0);
    const rxBytes = Number(parts[5] || 0);
    const txBytes = Number(parts[6] || 0);
    totalRxBytes += rxBytes;
    totalTxBytes += txBytes;
    return {
      publicKey: parts[0] || '',
      endpoint: parts[2] && parts[2] !== '(none)' ? parts[2] : null,
      allowedIps: parts[3] || '',
      latestHandshakeAt: latestHandshakeSec > 0 ? new Date(latestHandshakeSec * 1000).toISOString() : null,
      rxBytes,
      txBytes
    };
  });
  return {
    publicKey: ifaceParts[1] || '',
    listenPort: Number(ifaceParts[2] || 51820),
    activePeers: peers.length,
    totalRxBytes,
    totalTxBytes,
    peers
  };
}

const server = http.createServer(async (req, res) => {
  if (req.headers['x-agent-secret'] !== SECRET) {
    return sendJson(res, 403, { error: 'Forbidden' });
  }

  try {
    if (req.method === 'GET' && req.url === '/health') {
      const { stdout } = await execFileAsync('wg', ['show', WG_IFACE, 'dump']);
      const parsed = parseWgDump(stdout);
      return sendJson(res, 200, {
        ok: true,
        interface: WG_IFACE,
        publicKey: parsed.publicKey,
        listenPort: parsed.listenPort,
        activePeers: parsed.activePeers,
        rxBytes: parsed.totalRxBytes,
        txBytes: parsed.totalTxBytes,
        cpuUsage: sampleCpuPercent(),
        ramUsage: sampleRamPercent(),
        uptimeSeconds: Math.round(os.uptime())
      });
    }

    if (req.method === 'GET' && req.url?.startsWith('/peers/')) {
      const key = decodeURIComponent(req.url.slice('/peers/'.length));
      if (!isValidWgKey(key)) return sendJson(res, 400, { error: 'Invalid publicKey' });
      const { stdout } = await execFileAsync('wg', ['show', WG_IFACE, 'dump']);
      const parsed = parseWgDump(stdout);
      const peer = parsed.peers.find((p) => p.publicKey === key.trim());
      if (!peer) {
        return sendJson(res, 404, { ok: false, present: false, error: 'Peer not registered on interface' });
      }
      return sendJson(res, 200, { ok: true, present: true, peer });
    }

    if (req.method === 'POST' && req.url === '/peers') {
      const body = await readBody(req);
      if (!isValidWgKey(body.publicKey) || !body.allowedIps) {
        return sendJson(res, 400, { error: 'Invalid publicKey or allowedIps' });
      }
      await execFileAsync('wg', [
        'set',
        WG_IFACE,
        'peer',
        body.publicKey.trim(),
        'allowed-ips',
        String(body.allowedIps).trim()
      ]);
      return sendJson(res, 200, { ok: true });
    }

    if (req.method === 'DELETE' && req.url?.startsWith('/peers/')) {
      const key = decodeURIComponent(req.url.slice('/peers/'.length));
      if (!isValidWgKey(key)) return sendJson(res, 400, { error: 'Invalid publicKey' });
      await execFileAsync('wg', ['set', WG_IFACE, 'peer', key.trim(), 'remove']);
      return sendJson(res, 200, { ok: true });
    }

    return sendJson(res, 404, { error: 'Not found' });
  } catch (err) {
    return sendJson(res, 500, { error: err.message });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`NEXORA WireGuard Node Agent listening on :${PORT} for ${WG_IFACE}`);
});
