import { Router } from 'express';
import { pool } from '../db.js';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/asyncHandler.js';
import { isValidUuid } from '../utils/validators.js';

const router = Router();

function isConfiguredValue(val, placeholder) {
  const s = (val || '').trim();
  return Boolean(s) && s !== placeholder && !s.includes('YOUR_') && !s.includes('yourdomain.com');
}

export function buildPublicSpeedTestConfig(env = process.env) {
  const apiKey = (env.SPEEDOFME_API_KEY || '').trim();
  const domain = (env.SPEEDOFME_DOMAIN || '').trim();
  const speedOfMeReady = isConfiguredValue(apiKey, 'YOUR_SPEEDOFME_API_KEY') &&
    isConfiguredValue(domain, 'YOUR_REGISTERED_DOMAIN');

  return {
    ok: true,
    provider: speedOfMeReady ? 'speedofme' : 'cloudflare_http',
    speedOfMeConfigured: speedOfMeReady,
    apiKey: speedOfMeReady ? apiKey : null,
    domain: speedOfMeReady ? domain : null
  };
}

// GET /api/speedtest/config - Returns public SpeedOf.Me settings if configured, else fallback indicator
router.get('/config', (_req, res) => {
  res.json(buildPublicSpeedTestConfig());
});

// POST /api/speedtest/results - Save authenticated speed test run to PostgreSQL
router.post('/results', requireAuth, asyncHandler(async (req, res) => {
  const {
    serverId,
    provider,
    vpnActive,
    serverLabel,
    pingMs,
    jitterMs,
    downloadMbps,
    uploadMbps,
    maxDownloadMbps,
    maxUploadMbps,
    dataUsedBytes,
    testServer,
    publicIp
  } = req.body || {};

  if (serverId && !isValidUuid(serverId)) {
    return res.status(400).json({ error: 'Invalid serverId UUID' });
  }
  const dl = Number(downloadMbps);
  const ul = Number(uploadMbps);
  if (!Number.isFinite(dl) || dl < 0 || !Number.isFinite(ul) || ul < 0) {
    return res.status(400).json({ error: 'Valid non-negative downloadMbps and uploadMbps required' });
  }

  const inserted = await pool.query(
    `INSERT INTO speedtest_results
     (user_id, server_id, provider, vpn_active, server_label, ping_ms, jitter_ms,
      download_mbps, upload_mbps, max_download_mbps, max_upload_mbps, data_used_bytes, test_server, public_ip)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)
     RETURNING id, created_at`,
    [
      req.auth.userId,
      serverId || null,
      String(provider || 'speedofme').slice(0, 40),
      Boolean(vpnActive),
      String(serverLabel || 'Direct ISP').slice(0, 120),
      Math.max(0, Math.round(Number(pingMs || 0))),
      Math.max(0, Math.round(Number(jitterMs || 0))),
      dl,
      ul,
      Math.max(dl, Number(maxDownloadMbps || 0)),
      Math.max(ul, Number(maxUploadMbps || 0)),
      Math.max(0, Math.round(Number(dataUsedBytes || 0))),
      testServer ? String(testServer).slice(0, 120) : null,
      publicIp ? String(publicIp).slice(0, 64) : null
    ]
  );

  return res.status(201).json({
    ok: true,
    id: inserted.rows[0].id,
    createdAt: inserted.rows[0].created_at
  });
}));

// GET /api/speedtest/results - Retrieve recent speed test results for the signed-in user
router.get('/results', requireAuth, asyncHandler(async (req, res) => {
  const result = await pool.query(
    `SELECT id, server_id, provider, vpn_active, server_label, ping_ms, jitter_ms,
            download_mbps, upload_mbps, max_download_mbps, max_upload_mbps,
            data_used_bytes, test_server, public_ip, created_at
     FROM speedtest_results
     WHERE user_id = $1
     ORDER BY created_at DESC
     LIMIT 50`,
    [req.auth.userId]
  );
  return res.json({
    results: result.rows.map((r) => ({
      id: r.id,
      serverId: r.server_id,
      provider: r.provider,
      vpnActive: r.vpn_active,
      serverLabel: r.server_label,
      pingMs: r.ping_ms,
      jitterMs: r.jitter_ms,
      downloadMbps: Number(r.download_mbps),
      uploadMbps: Number(r.upload_mbps),
      maxDownloadMbps: Number(r.max_download_mbps),
      maxUploadMbps: Number(r.max_upload_mbps),
      dataUsedBytes: Number(r.data_used_bytes),
      testServer: r.test_server,
      publicIp: r.public_ip,
      createdAt: r.created_at
    }))
  });
}));

export default router;
