import { Router } from 'express';
import crypto from 'node:crypto';

const router = Router();

router.get('/ip', (req, res) => {
  const forwarded = req.headers['x-forwarded-for'];
  const rawIp = typeof forwarded === 'string'
    ? forwarded.split(',')[0].trim()
    : (req.socket.remoteAddress || '');
  const cleanIp = rawIp.replace(/^::ffff:/, '');
  res.json({ ip: cleanIp });
});

router.get('/speedtest/download', (req, res) => {
  const bytesRequested = Math.min(25 * 1024 * 1024, Math.max(64 * 1024, Number(req.query.bytes) || 4 * 1024 * 1024));
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

router.post('/speedtest/upload', (req, res) => {
  let receivedBytes = 0;
  const startedAt = Date.now();
  req.on('data', (chunk) => {
    receivedBytes += chunk.length;
  });
  req.on('end', () => {
    res.json({
      receivedBytes,
      elapsedMs: Math.max(1, Date.now() - startedAt)
    });
  });
});

export default router;
