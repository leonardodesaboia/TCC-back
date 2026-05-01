CREATE TABLE favorite_professionals (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       UUID        NOT NULL REFERENCES users(id),
    professional_id UUID        NOT NULL REFERENCES professionals(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT idx_favorites_client_professional UNIQUE (client_id, professional_id)
);

CREATE INDEX idx_favorites_client_id ON favorite_professionals(client_id);

COMMENT ON TABLE favorite_professionals IS 'Clientes podem favoritar profissionais para acesso rapido. Sem soft delete.';
COMMENT ON COLUMN favorite_professionals.client_id IS 'Usuario cliente que favoritou o profissional.';
COMMENT ON COLUMN favorite_professionals.professional_id IS 'Perfil profissional favoritado pelo cliente.';
