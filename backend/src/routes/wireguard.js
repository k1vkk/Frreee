import { Router } from 'express';
import { requireAuth } from '../middleware/auth.js';
import { isValidWireGuardKey, provisionAndSyncPeer } from './vpn.js';

const router = Router();

// Backward-compatible alias delegating to the unified VPN WireGuard provisioning engine
router.post('/provision', requireAuth, async (req, res) => {
  const { serverId, clientPublicKey } = req.body || {};
  if (!serverId || !isValidWireGuardKey(clientPublicKey)) {
    return res.status(400).json({ error: 'Valid serverId and WireGuard Curve25519 public key required' });
  }
  try {
    const result = await provisionAndSyncPeer({
      userId: req.auth.userId,
      serverId,
      clientPublicKey,
      markConnected: true
    });
    return res.json(result);
  } catch (err) {
    return res.status(err.statusCode || 500).json({ error: err.message || 'Failed to provision WireGuard peer' });
  }
});

export default router;
