import bcrypt from 'bcryptjs';
import { initializeDatabase, pool } from '../src/db.js';

const email = (process.env.ADMIN_EMAIL || process.argv[2] || '').trim().toLowerCase();
const password = process.env.ADMIN_PASSWORD || process.argv[3] || '';
const displayName = (process.env.ADMIN_NAME || process.argv[4] || 'NEXORA Admin').trim();

if (!email || !password || password.length < 10) {
  console.error('Usage: node scripts/create-admin.js <admin@domain.com> <password_min_10_chars> [displayName]');
  process.exit(1);
}

await initializeDatabase();
const passwordHash = await bcrypt.hash(password, 12);
const res = await pool.query(
  `INSERT INTO users (email, password_hash, display_name, role)
   VALUES ($1, $2, $3, 'admin')
   ON CONFLICT (email)
   DO UPDATE SET password_hash = EXCLUDED.password_hash, role = 'admin', is_suspended = FALSE
   RETURNING id, email, role`,
  [email, passwordHash, displayName]
);
await pool.query(
  `INSERT INTO subscriptions (user_id, plan_tier, status)
   VALUES ($1, 'pro_premium', 'active')
   ON CONFLICT (user_id) DO NOTHING`,
  [res.rows[0].id]
);
console.log('Administrator account ready:', res.rows[0]);
await pool.end();
