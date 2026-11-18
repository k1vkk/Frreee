-- NEXORA VPN Phase 1 Safe Backward-Compatible Migration
-- Target: Supabase / PostgreSQL existing databases
-- Preserves all existing tables and user/ticket data.

ALTER TABLE support_tickets
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE support_tickets
DROP CONSTRAINT IF EXISTS support_tickets_category_check;

ALTER TABLE support_tickets
ADD CONSTRAINT support_tickets_category_check
CHECK (category IN ('connectivity', 'connection', 'tunnel', 'billing', 'account', 'speed', 'general'));
