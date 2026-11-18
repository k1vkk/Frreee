import { closePool, initializeDatabase, initPool, loadBackendEnv } from './db.js';

async function migrate() {
  loadBackendEnv();
  if (!String(process.env.DATABASE_URL || '').trim()) {
    throw new Error('DATABASE_URL environment variable is required to run migrations');
  }
  initPool(process.env);
  await initializeDatabase();
  console.log('NEXORA VPN PostgreSQL schema, migrations & GCC topology slots initialized.');
  await closePool();
}

migrate().catch(async (err) => {
  console.error('Migration failed:', err.message || err);
  await closePool().catch(() => {});
  process.exit(1);
});
