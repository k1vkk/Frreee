import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import pg from 'pg';
import dotenv from 'dotenv';

dotenv.config();

const { Pool } = pg;
const __dirname = path.dirname(fileURLToPath(import.meta.url));

function resolveSslConfig() {
  const dbUrl = process.env.DATABASE_URL || '';
  const sslEnv = String(process.env.DATABASE_SSL || '').toLowerCase();
  if (sslEnv === 'true' || dbUrl.includes('sslmode=require')) {
    return { rejectUnauthorized: false };
  }
  return undefined;
}

export const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: resolveSslConfig(),
  max: 20,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 8000
});

export async function initializeDatabase() {
  const schemaPath = path.resolve(__dirname, '../db/schema.sql');
  const seedPath = path.resolve(__dirname, '../db/seed_gcc_topology.sql');
  const schemaSql = await fs.readFile(schemaPath, 'utf8');
  const seedSql = await fs.readFile(seedPath, 'utf8');
  await pool.query(schemaSql);
  await pool.query(seedSql);
}
