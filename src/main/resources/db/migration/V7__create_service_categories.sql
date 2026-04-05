CREATE TABLE service_categories (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    area_id    UUID        NOT NULL REFERENCES service_areas(id),
    name       VARCHAR(80) NOT NULL,
    icon_url   TEXT,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_service_categories_area_id ON service_categories(area_id);

COMMENT ON TABLE  service_categories            IS 'Nível 2 do catálogo. Ex: Eletricista, Encanador. Profissional se cadastra em categorias.';
COMMENT ON COLUMN service_categories.is_active  IS 'false = oculto no app mas não deletado.';
COMMENT ON COLUMN service_categories.deleted_at IS 'Soft delete.';
