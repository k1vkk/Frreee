import { initializeDatabase, pool } from './db.js';

async function migrate() {
  await initializeDatabase();
  console.log('NEXORA VPN PostgreSQL schema & GCC topology slots initialized.');
  await pool.end();
}

migrate().catch((err) => {
  console.error('Migration failed:', err);
  process.exit(1);
});
