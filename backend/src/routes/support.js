import { Router } from 'express';
import { pool } from '../db.js';
import { requireAuth } from '../middleware/auth.js';
import { asyncHandler } from '../middleware/asyncHandler.js';

const router = Router();

export const ALLOWED_SUPPORT_CATEGORIES = new Set([
  'connectivity',
  'connection',
  'tunnel',
  'billing',
  'account',
  'speed',
  'general'
]);

export function normalizeSupportCategory(category) {
  const raw = String(category || '').trim().toLowerCase();
  if (!raw) return 'connectivity';
  return ALLOWED_SUPPORT_CATEGORIES.has(raw) ? raw : 'general';
}

// GET /api/support/tickets - List authenticated user's support tickets
router.get('/tickets', requireAuth, asyncHandler(async (req, res) => {
  const result = await pool.query(
    `SELECT id, category, subject, message, status, created_at, updated_at
     FROM support_tickets
     WHERE user_id = $1
     ORDER BY created_at DESC
     LIMIT 50`,
    [req.auth.userId]
  );
  return res.json({
    tickets: result.rows.map((r) => ({
      id: r.id,
      category: r.category,
      subject: r.subject,
      message: r.message,
      status: r.status,
      createdAt: r.created_at,
      updatedAt: r.updated_at
    }))
  });
}));

// POST /api/support/tickets - Submit a real support ticket persisted in PostgreSQL
router.post('/tickets', requireAuth, asyncHandler(async (req, res) => {
  const { category, subject, message, diagnosticsJson } = req.body || {};
  const cleanSubject = String(subject || '').trim();
  const cleanMessage = String(message || '').trim();

  if (cleanSubject.length < 3 || cleanMessage.length < 10) {
    return res.status(400).json({
      error: 'Subject (min 3 chars) and message (min 10 chars) are required'
    });
  }

  const cleanCategory = normalizeSupportCategory(category);

  let safeDiagnostics = null;
  if (diagnosticsJson && typeof diagnosticsJson === 'object') {
    safeDiagnostics = diagnosticsJson;
  }

  const inserted = await pool.query(
    `INSERT INTO support_tickets (user_id, category, subject, message, diagnostics_json)
     VALUES ($1, $2, $3, $4, $5)
     RETURNING id, category, subject, status, created_at, updated_at`,
    [
      req.auth.userId,
      cleanCategory,
      cleanSubject.slice(0, 160),
      cleanMessage.slice(0, 4000),
      safeDiagnostics ? JSON.stringify(safeDiagnostics) : null
    ]
  );

  return res.status(201).json({
    ok: true,
    ticket: {
      id: inserted.rows[0].id,
      category: inserted.rows[0].category,
      subject: inserted.rows[0].subject,
      status: inserted.rows[0].status,
      createdAt: inserted.rows[0].created_at,
      updatedAt: inserted.rows[0].updated_at
    }
  });
}));

export default router;
