ALTER TABLE orders ADD COLUMN pending_price_amount NUMERIC(10,2);
ALTER TABLE orders ADD COLUMN pending_price_reason TEXT;
ALTER TABLE orders ADD COLUMN pending_price_proposed_at TIMESTAMPTZ;
