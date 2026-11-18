import { Router } from 'express';
import crypto from 'node:crypto';
import { createRateLimiter } from '../middleware/rateLimit.js';

const router = Router();
const speedLimiter = createRateLimiter({ windowMs: 60_000, max: 30, keyPrefix: 'speed' });

router.get('/ip', (req, res) => {
  const forwarded = req.headers['x-forwarded-for'];
  const rawIp = typeof forwarded === 'string'
    ? forwarded.split(',')[0].trim()
    : (req.socket.remoteAddress || '');
  const cleanIp = rawIp.replace(/^::ffff:/, '');
  res.json({ ip: cleanIp });
});

router.get('/speedtest/download', speedLimiter, (req, res) => {
  const bytesRequested = Math.min(10 * 1024 * 1024, Math.max(64 * 1024, Number(req.query.bytes) || 2 * 1024 * 1024));
  res.setHeader('Content-Type', 'application/octet-stream');
  res.setHeader('Content-Length', String(bytesRequested));
  res.setHeader('Cache-Control', 'no-store');

  const chunkSize = 64 * 1024;
  const chunk = crypto.randomBytes(chunkSize);
  let sent = 0;

  function pushNext() {
    while (sent < bytesRequested) {
      const remaining = bytesRequested - sent;
      const slice = remaining >= chunkSize ? chunk : chunk.subarray(0, remaining);
      sent += slice.length;
      if (!res.write(slice)) {
        res.once('drain', pushNext);
        return;
      }
    }
    res.end();
  }
  pushNext();
});

router.post('/speedtest/upload', speedLimiter, (req, res) => {
  let receivedBytes = 0;
  const maxBytes = 10 * 1024 * 1024;
  const startedAt = Date.now();
  req.on('data', (chunk) => {
    receivedBytes += chunk.length;
    if (receivedBytes > maxBytes) {
      req.destroy();
    }
  });
  req.on('end', () => {
    res.json({
      receivedBytes,
      elapsedMs: Math.max(1, Date.now() - startedAt)
    });
  });
});

export default router;
