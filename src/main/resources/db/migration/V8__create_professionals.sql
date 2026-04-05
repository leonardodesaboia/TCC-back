CREATE TYPE verification_status AS ENUM ('pending', 'approved', 'rejected');

CREATE TABLE professionals (
    id                      UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID                NOT NULL UNIQUE REFERENCES users(id),
    bio                     TEXT,
    years_of_experience     SMALLINT,
    base_hourly_rate        NUMERIC(10,2),
    verification_status     verification_status NOT NULL DEFAULT 'pending',
    idwall_token            VARCHAR(255),
    idwall_result           JSONB,
    rejection_reason        TEXT,
    geo_lat                 NUMERIC(9,6),
    geo_lng                 NUMERIC(9,6),
    geo_active              BOOLEAN             NOT NULL DEFAULT FALSE,
    subscription_plan_id    UUID                REFERENCES subscription_plans(id),
    subscription_expires_at TIMESTAMP,
    created_at              TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP           NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMP
);

CREATE INDEX idx_professionals_verification_status ON professionals(verification_status);
CREATE INDEX idx_professionals_geo_active          ON professionals(geo_active);
CREATE INDEX idx_professionals_user_id             ON professionals(user_id);

COMMENT ON TABLE  professionals                         IS 'Perfil estendido do profissional. 1:1 com users. Só recebe pedidos quando verification_status = approved.';
COMMENT ON COLUMN professionals.idwall_token            IS 'Token retornado pelo IDwall SDK após envio dos documentos.';
COMMENT ON COLUMN professionals.idwall_result           IS 'Payload completo do IDwall — inclui antecedentes criminais, biometria e documentos.';
COMMENT ON COLUMN professionals.geo_lat                 IS 'Última latitude conhecida — atualizada via heartbeat quando geo_active = true.';
COMMENT ON COLUMN professionals.geo_lng                 IS 'Última longitude conhecida — atualizada via heartbeat quando geo_active = true.';
COMMENT ON COLUMN professionals.geo_active              IS 'true = disponível para receber pedidos Express.';
COMMENT ON COLUMN professionals.subscription_plan_id    IS 'null = free tier sem benefícios.';
COMMENT ON COLUMN professionals.subscription_expires_at IS 'null se sem plano — ao expirar, subscription_plan_id deve ser setado para null.';
COMMENT ON COLUMN professionals.deleted_at              IS 'Soft delete — profissional deletado não aparece em buscas.';
