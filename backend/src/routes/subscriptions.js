import { Router } from 'express';
import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import { pool } from '../db.js';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/asyncHandler.js';
import { logBackendError } from '../services/errorLogger.js';

const router = Router();

export const PRODUCT_TO_TIER = {
  nexora_pro_monthly: { tier: 'pro_monthly', days: 30 },
  nexora_pro_annual: { tier: 'pro_annual', days: 365 },
  nexora_pro_premium: { tier: 'pro_premium', days: 365 }
};

async function loadServiceAccount() {
  const raw = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON || process.env.GOOGLE_PLAY_SERVICE_ACCOUNT;
  if (!raw || !raw.trim()) return null;
  if (raw.trim().startsWith('{')) {
    return JSON.parse(raw);
  }
  const content = await fs.readFile(raw.trim(), 'utf8');
  return JSON.parse(content);
}

async function getGooglePlayAccessToken(sa) {
  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT' })).toString('base64url');
  const claims = Buffer.from(JSON.stringify({
    iss: sa.client_email,
    scope: 'https://www.googleapis.com/auth/androidpublisher',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600
  })).toString('base64url');

  const signer = crypto.createSign('RSA-SHA256');
  signer.update(`${header}.${claims}`);
  const signature = signer.sign(sa.private_key, 'base64url');
  const assertion = `${header}.${claims}.${signature}`;

  const tokenRes = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion
    })
  });
  if (!tokenRes.ok) {
    throw new Error(`Google OAuth token exchange failed (HTTP ${tokenRes.status})`);
  }
  const data = await tokenRes.json();
  return data.access_token;
}

async function verifyWithGooglePlayApi({ packageName, purchaseToken }) {
  const sa = await loadServiceAccount();
  if (!sa) return null;

  const accessToken = await getGooglePlayAccessToken(sa);
  const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}` }
  });
  if (!res.ok) {
    const errText = await res.text();
    throw new Error(`Google Play verification rejected token (HTTP ${res.status}): ${errText.slice(0, 200)}`);
  }
  const sub = await res.json();
  const state = sub.subscriptionState;
  const activeStates = new Set([
    'SUBSCRIPTION_STATE_ACTIVE',
    'SUBSCRIPTION_STATE_IN_GRACE_PERIOD'
  ]);
  const lineItem = sub.lineItems?.[0] || {};
  return {
    isActive: activeStates.has(state),
    state,
    productId: lineItem.productId || null,
    expiryTime: lineItem.expiryTime || null
  };
}

router.get('/me', requireAuth, asyncHandler(async (req, res) => {
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

router.post('/verify', requireAuth, asyncHandler(async (req, res) => {
  const { productId, purchaseToken, packageName } = req.body || {};
  const mapped = PRODUCT_TO_TIER[productId];
  if (!mapped || !purchaseToken) {
    return res.status(400).json({ error: 'Valid productId and purchaseToken required' });
  }

  const expectedPkg = process.env.GOOGLE_PLAY_PACKAGE_NAME || 'com.aistudio.nexoravpn.nxrk7q';
  if (packageName && packageName !== expectedPkg) {
    return res.status(403).json({ error: 'Package name mismatch' });
  }

  try {
    const playResult = await verifyWithGooglePlayApi({
      packageName: expectedPkg,
      purchaseToken
    });

    if (playResult === null) {
      await logBackendError({
        level: 'warn',
        source: 'billing',
        endpoint: '/api/subscriptions/verify',
        message: 'GOOGLE_PLAY_SERVICE_ACCOUNT is not configured; cannot verify Play purchase server-side.',
        userId: req.auth.userId
      });
      return res.status(503).json({
        error: 'Google Play Service Account JSON not configured on backend for server-side purchase verification.'
      });
    }

    if (!playResult.isActive) {
      return res.status(402).json({
        error: `Google Play subscription is not active (${playResult.state || 'UNKNOWN'})`
      });
    }

    const resolvedProductId = playResult.productId || productId;
    const resolvedPlan = PRODUCT_TO_TIER[resolvedProductId] || mapped;
    const expiresAt = playResult.expiryTime
      ? new Date(playResult.expiryTime)
      : new Date(Date.now() + resolvedPlan.days * 86400 * 1000);

    await pool.query(
      `INSERT INTO subscriptions (user_id, plan_tier, status, play_product_id, play_purchase_token, expires_at, updated_at)
       VALUES ($1, $2, 'active', $3, $4, $5, NOW())
       ON CONFLICT (user_id)
       DO UPDATE SET plan_tier = EXCLUDED.plan_tier,
                     status = 'active',
                     play_product_id = EXCLUDED.play_product_id,
                     play_purchase_token = EXCLUDED.play_purchase_token,
                     expires_at = EXCLUDED.expires_at,
                     updated_at = NOW()`,
      [req.auth.userId, resolvedPlan.tier, resolvedProductId, purchaseToken, expiresAt]
    );

    return res.json({
      ok: true,
      planTier: resolvedPlan.tier,
      status: 'active',
      expiresAt: expiresAt.toISOString()
    });
  } catch (err) {
    await logBackendError({
      level: 'error',
      source: 'billing',
      endpoint: '/api/subscriptions/verify',
      message: err.message,
      userId: req.auth.userId
    });
    return res.status(502).json({ error: err.message || 'Play verification failed' });
  }
}));

export default router;
