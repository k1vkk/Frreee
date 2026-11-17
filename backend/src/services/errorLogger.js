import { pool } from '../db.js';

export async function logBackendError({
  level = 'error',
  source = 'api',
  endpoint = null,
  message,
  details = null,
  userId = null,
  nodeCode = null
}) {
  try {
    await pool.query(
      `INSERT INTO error_logs (level, source, endpoint, message, details, user_id, node_code)
       VALUES ($1, $2, $3, $4, $5, $6, $7)`,
      [
        level,
        source,
        endpoint,
        String(message || 'Unknown error').slice(0, 500),
        details ? String(details).slice(0, 2000) : null,
        userId || null,
        nodeCode || null
      ]
    );
  } catch (err) {
    console.error('Error logger fallback:', err.message);
  }
}
