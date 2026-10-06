ALTER TABLE orders ADD COLUMN pending_service_id UUID REFERENCES professional_services(id);
ALTER TABLE orders ADD COLUMN pending_service_reason TEXT;
ALTER TABLE orders ADD COLUMN pending_service_estimated_duration_minutes INT;
ALTER TABLE orders ADD COLUMN pending_service_proposed_at TIMESTAMPTZ;
