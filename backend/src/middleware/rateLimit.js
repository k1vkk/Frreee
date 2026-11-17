export function createRateLimiter({ windowMs = 60_000, max = 60, keyPrefix = 'api' } = {}) {
  const buckets = new Map();

  // Prune expired buckets periodically to avoid unbounded memory growth
  const cleanup = setInterval(() => {
    const now = Date.now();
    for (const [key, entry] of buckets.entries()) {
      if (entry.resetAt <= now) buckets.delete(key);
    }
  }, Math.max(windowMs, 30_000));
  if (typeof cleanup.unref === 'function') cleanup.unref();

  return (req, res, next) => {
    const ip = (req.headers['x-forwarded-for'] || req.ip || req.socket.remoteAddress || 'unknown')
      .toString()
      .split(',')[0]
      .trim();
    const key = `${keyPrefix}:${ip}`;
    const now = Date.now();
    let entry = buckets.get(key);
    if (!entry || entry.resetAt <= now) {
      entry = { count: 0, resetAt: now + windowMs };
      buckets.set(key, entry);
    }
    entry.count += 1;

    res.setHeader('X-RateLimit-Limit', String(max));
    res.setHeader('X-RateLimit-Remaining', String(Math.max(0, max - entry.count)));

    if (entry.count > max) {
      return res.status(429).json({
        error: 'Too many requests. Please wait before retrying.'
      });
    }
    return next();
  };
}
