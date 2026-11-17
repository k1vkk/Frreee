import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import dotenv from 'dotenv';
import { initializeDatabase, pool } from './db.js';
import authRoutes from './routes/auth.js';
import serversRoutes from './routes/servers.js';
import vpnRoutes from './routes/vpn.js';
import wireguardRoutes from './routes/wireguard.js';
import subscriptionsRoutes from './routes/subscriptions.js';
import networkRoutes from './routes/network.js';
import adminRoutes from './routes/admin.js';
import { startNodeMonitorLoop } from './services/nodeMonitor.js';
import { getJwtSecret, requireAuth } from './middleware/auth.js';
import { createRateLimiter } from './middleware/rateLimit.js';
import { logBackendError } from './services/errorLogger.js';

dotenv.config();

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
app.set('trust proxy', process.env.TRUST_PROXY !== 'false');

app.use(
  helmet({
    contentSecurityPolicy: false,
    hsts: process.env.NODE_ENV === 'production'
      ? { maxAge: 31536000, includeSubDomains: true, preload: true }
      : false
  })
);

const allowedOrigins = (process.env.CORS_ORIGIN || '*')
  .split(',')
  .map((o) => o.trim())
  .filter(Boolean);

app.use(
  cors({
    origin: allowedOrigins.includes('*') ? '*' : allowedOrigins
  })
);
app.use(express.json({ limit: '1mb' }));

const authLimiter = createRateLimiter({ windowMs: 60_000, max: 20, keyPrefix: 'auth' });
const apiLimiter = createRateLimiter({ windowMs: 60_000, max: 180, keyPrefix: 'api' });
app.use('/api/v1', apiLimiter);

// Enforce HTTPS behind reverse proxy when enabled
if (process.env.ENFORCE_HTTPS === 'true') {
  app.use((req, res, next) => {
    if (req.path === '/api/v1/health') return next();
    const proto = req.headers['x-forwarded-proto'] || req.protocol;
    if (proto !== 'https') {
      return res.status(403).json({ error: 'HTTPS required in production' });
    }
    return next();
  });
}

app.get('/api/v1/health', async (_req, res) => {
  try {
    await pool.query('SELECT 1');
    const nodesRes = await pool.query(
      `SELECT COUNT(*) FILTER (WHERE health_status = 'online')::int AS online_nodes,
              COUNT(*)::int AS total_slots
       FROM vpn_servers`
    );
    res.json({
      ok: true,
      service: 'NEXORA VPN Backend',
      environment: process.env.NODE_ENV || 'production',
      database: 'postgresql',
      publicUrl: process.env.API_PUBLIC_URL || null,
      onlineNodes: nodesRes.rows[0].online_nodes,
      totalSlots: nodesRes.rows[0].total_slots,
      timestamp: new Date().toISOString()
    });
  } catch (err) {
    res.status(500).json({ ok: false, error: 'Database unreachable' });
  }
});

app.use('/api/v1/auth', authLimiter, authRoutes);
app.use('/api/v1/servers', serversRoutes);
app.use('/api/v1/vpn', vpnRoutes);
app.use('/api/v1/wg', wireguardRoutes);
app.use('/api/v1/subscriptions', subscriptionsRoutes);
app.use('/api/v1/network', networkRoutes);
app.use('/api/v1/admin/login', authLimiter);
app.use('/api/v1/admin', adminRoutes);

app.post('/api/v1/telemetry/error', requireAuth, async (req, res) => {
  const { level, source, nodeCode, message, details } = req.body || {};
  if (!message) return res.status(400).json({ error: 'Error message required' });
  await logBackendError({
    level: ['warn', 'error', 'critical'].includes(level) ? level : 'error',
    source: source || 'android-client',
    nodeCode: nodeCode || null,
    message,
    details,
    userId: req.auth.userId
  });
  return res.status(201).json({ ok: true });
});

// Serve built React Admin Dashboard at /admin when bundled
const adminDistDir = path.resolve(__dirname, '../public-admin');
if (fs.existsSync(adminDistDir)) {
  app.use('/admin', express.static(adminDistDir));
  app.get('/admin/*', (_req, res) => {
    res.sendFile(path.join(adminDistDir, 'index.html'));
  });
}

app.use('/api', (_req, res) => {
  res.status(404).json({ error: 'Endpoint not found' });
});

app.use(async (err, req, res, _next) => {
  await logBackendError({
    level: 'critical',
    source: 'express',
    endpoint: req.originalUrl,
    message: err.message,
    details: err.stack
  });
  res.status(500).json({ error: 'Internal server error' });
});

async function bootstrap() {
  getJwtSecret();
  if (!process.env.DATABASE_URL) {
    throw new Error('DATABASE_URL environment variable is required');
  }
  await initializeDatabase();
  const port = Number(process.env.PORT || 4000);
  app.listen(port, '0.0.0.0', () => {
    console.log(`NEXORA VPN API listening on port ${port} (${process.env.NODE_ENV || 'production'})`);
    startNodeMonitorLoop(30000);
  });
}

bootstrap().catch((err) => {
  console.error('Failed to start NEXORA VPN Backend:', err.message);
  process.exit(1);
});
