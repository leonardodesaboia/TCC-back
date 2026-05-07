ALTER TABLE professionals
    ADD COLUMN subscription_cancelled_at TIMESTAMP;

COMMENT ON COLUMN professionals.subscription_cancelled_at
    IS 'Quando preenchido, a renovacao automatica foi cancelada e os beneficios seguem apenas ate subscription_expires_at.';
