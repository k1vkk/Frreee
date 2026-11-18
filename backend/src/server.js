import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import dotenv from 'dotenv';
import {
  checkDatabaseHealth,
  initializeDatabase,
  pool,
  validateServerDatabaseSecurity
} from './db.js';
import authRoutes from './routes/auth.js';
import serversRoutes from './routes/servers.js';
import vpnRoutes from './routes/vpn.js';
import wireguardRoutes from './routes/wireguard.js';
import subscriptionsRoutes from './routes/subscriptions.js';
import networkRoutes from './routes/network.js';
import speedtestRoutes from './routes/speedtest.js';
import userRoutes from './routes/user.js';
import supportRoutes from './routes/support.js';
import adminRoutes from './routes/admin.js';
import { startNodeMonitorLoop } from './services/nodeMonitor.js';
import { getJwtSecret, requireAuth } from './middleware/auth.js';
import { asyncHandler } from './middleware/asyncHandler.js';
import { createRateLimiter } from './middleware/rateLimit.js';
import { logBackendError } from './services/errorLogger.js';

dotenv.config();

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export function createApp() {
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
  app.use('/api', apiLimiter);

  if (process.env.ENFORCE_HTTPS === 'true') {
    app.use((req, res, next) => {
      if (req.path === '/api/health' || req.path === '/api/v1/health') return next();
      const proto = req.headers['x-forwarded-proto'] || req.protocol;
      if (proto !== 'https') {
        return res.status(403).json({ error: 'HTTPS required in production' });
      }
      return next();
    });
  }

  const healthHandler = async (_req, res) => {
    try {
      await checkDatabaseHealth();
      const nodesRes = await pool.query(
        `SELECT COUNT(*) FILTER (WHERE health_status = 'online' AND is_active = TRUE)::int AS online_nodes,
                COUNT(*)::int AS total_slots
         FROM vpn_servers`
      );
      res.json({
        ok: true,
        service: 'NEXORA VPN Backend',
        environment: process.env.NODE_ENV || 'production',
        database: 'postgresql',
        publicUrl: process.env.API_PUBLIC_URL || process.env.NEXORA_API_BASE_URL || null,
        onlineNodes: nodesRes.rows[0].online_nodes,
        totalSlots: nodesRes.rows[0].total_slots,
        timestamp: new Date().toISOString()
      });
    } catch (_err) {
      res.status(500).json({ ok: false, error: 'Database unreachable' });
    }
  };

  const telemetryErrorHandler = asyncHandler(async (req, res) => {
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

  // Mount both /api/* and /api/v1/* cleanly
  for (const prefix of ['/api', '/api/v1']) {
    app.get(`${prefix}/health`, asyncHandler(healthHandler));
    app.use(`${prefix}/auth`, authLimiter, authRoutes);
    app.use(`${prefix}/servers`, serversRoutes);
    app.use(`${prefix}/vpn`, vpnRoutes);
    app.use(`${prefix}/wg`, wireguardRoutes);
    app.use(`${prefix}/speedtest`, speedtestRoutes);
    app.use(`${prefix}/user`, userRoutes);
    app.use(`${prefix}/support`, supportRoutes);
    app.use(`${prefix}/subscriptions`, subscriptionsRoutes);
    app.use(`${prefix}/network`, networkRoutes);
    app.use(`${prefix}/admin/login`, authLimiter);
    app.use(`${prefix}/admin`, adminRoutes);
    app.post(`${prefix}/telemetry/error`, requireAuth, telemetryErrorHandler);
  }

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

  app.use(async (err, req, res, next) => {
    if (res.headersSent) {
      return next(err);
    }
    const rawStatus = Number(err?.statusCode || err?.status || 500);
    const statusCode = rawStatus >= 400 && rawStatus < 600 ? rawStatus : 500;
    if (statusCode >= 500) {
      try {
        await logBackendError({
          level: 'critical',
          source: 'express',
          endpoint: req.originalUrl,
          message: err?.message || 'Internal server error',
          details: err?.stack || null
        });
      } catch {
        // Fallback already handled inside logBackendError
      }
    }
    return res.status(statusCode).json({
      error: statusCode >= 500 ? 'Internal server error' : (err?.message || 'Bad request')
    });
  });

  return app;
}

export async function bootstrap() {
  validateServerDatabaseSecurity();
  getJwtSecret();
  if (!process.env.DATABASE_URL) {
    throw new Error('DATABASE_URL environment variable is required');
  }
  await initializeDatabase();
  const app = createApp();
  const port = Number(process.env.PORT || 4000);
  app.listen(port, '0.0.0.0', () => {
    console.log(`NEXORA VPN API listening on port ${port} (${process.env.NODE_ENV || 'production'})`);
    startNodeMonitorLoop(30000);
  });
}

const isEntrypoint = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isEntrypoint) {
  bootstrap().catch((err) => {
    console.error('Failed to start NEXORA VPN Backend:', err.message);
    process.exit(1);
  });
}
