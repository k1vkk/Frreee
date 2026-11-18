import { Router } from 'express';
import { pool } from '../db.js';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/asyncHandler.js';

const router = Router();

// GET /api/user/profile
router.get('/profile', requireAuth, asyncHandler(async (req, res) => {
  const userRes = await pool.query(
    `SELECT u.id, u.email, u.display_name, u.role, u.created_at,
            s.plan_tier, s.status AS sub_status, s.expires_at
     FROM users u
     LEFT JOIN subscriptions s ON s.user_id = u.id
     WHERE u.id = $1`,
    [req.auth.userId]
  );
  if (userRes.rowCount === 0) {
    return res.status(404).json({ error: 'User not found' });
  }
  const u = userRes.rows[0];
  const expired = u.expires_at && new Date(u.expires_at) < new Date() && u.plan_tier !== 'free';
  return res.json({
    user: {
      id: u.id,
      email: u.email,
      displayName: u.display_name,
      role: u.role || 'user',
      planTier: expired ? 'free' : (u.plan_tier || 'free'),
      subscriptionStatus: expired ? 'expired' : (u.sub_status || 'active'),
      expiresAt: u.expires_at,
      createdAt: u.created_at
    }
  });
}));

// GET /api/user/subscription
router.get('/subscription', requireAuth, asyncHandler(async (req, res) => {
  const subRes = await pool.query(
    'SELECT plan_tier, status, play_product_id, expires_at, updated_at FROM subscriptions WHERE user_id = $1',
    [req.auth.userId]
  );
  if (subRes.rowCount === 0) {
    return res.json({ planTier: 'free', status: 'active', expiresAt: null });
  }
  const row = subRes.rows[0];
  let planTier = row.plan_tier;
  let status = row.status;
  if (row.expires_at && new Date(row.expires_at) < new Date() && planTier !== 'free') {
    planTier = 'free';
    status = 'expired';
    await pool.query(
      "UPDATE subscriptions SET plan_tier = 'free', status = 'expired', updated_at = NOW() WHERE user_id = $1",
      [req.auth.userId]
    );
  }
  return res.json({
    planTier,
    status,
    productId: row.play_product_id,
    expiresAt: row.expires_at,
    updatedAt: row.updated_at
  });
}));

export default router;
