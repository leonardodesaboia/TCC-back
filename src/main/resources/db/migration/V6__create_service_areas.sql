CREATE TABLE service_areas (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(80) NOT NULL UNIQUE,
    icon_url   TEXT,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

COMMENT ON TABLE  service_areas           IS 'Nível 1 do catálogo. Ex: Elétrica, Hidráulica, Limpeza. Gerenciado pelo admin.';
COMMENT ON COLUMN service_areas.is_active IS 'false = oculto no app mas não deletado.';
COMMENT ON COLUMN service_areas.deleted_at IS 'Soft delete.';
