CREATE TYPE pricing_type AS ENUM ('hourly', 'fixed');

CREATE TABLE professional_services (
    id                         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    professional_id            UUID          NOT NULL REFERENCES professionals(id),
    category_id                UUID          NOT NULL REFERENCES service_categories(id),
    title                      VARCHAR(100)  NOT NULL,
    description                TEXT,
    pricing_type               pricing_type  NOT NULL,
    price                      NUMERIC(10,2) NOT NULL,
    estimated_duration_minutes INT,
    is_active                  BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at                 TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMP     NOT NULL DEFAULT NOW(),
    deleted_at                 TIMESTAMP
);

CREATE INDEX idx_professional_services_pro_category ON professional_services(professional_id, category_id);
CREATE INDEX idx_professional_services_active        ON professional_services(is_active);

COMMENT ON TABLE  professional_services                              IS 'Nível 3 do catálogo — serviço específico de um profissional. Usado no On Demand.';
COMMENT ON COLUMN professional_services.pricing_type                IS 'hourly = cobra por hora | fixed = preço fechado pelo serviço.';
COMMENT ON COLUMN professional_services.price                       IS 'Valor por hora se hourly | valor total se fixed.';
COMMENT ON COLUMN professional_services.estimated_duration_minutes  IS 'Estimativa de duração — usado principalmente com pricing_type = fixed.';
COMMENT ON COLUMN professional_services.deleted_at                  IS 'Soft delete — serviço com pedidos históricos não pode ser deletado fisicamente.';
