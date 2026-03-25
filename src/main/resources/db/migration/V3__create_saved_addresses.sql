CREATE TABLE saved_addresses (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label      VARCHAR(60),
    street     VARCHAR(200)  NOT NULL,
    number     VARCHAR(20),
    complement VARCHAR(80),
    district   VARCHAR(80),
    city       VARCHAR(80)   NOT NULL,
    state      CHAR(2)       NOT NULL,
    zip_code   VARCHAR(9)    NOT NULL,
    lat        NUMERIC(9,6),
    lng        NUMERIC(9,6),
    is_default BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP     NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_saved_addresses_user_id      ON saved_addresses(user_id);
CREATE INDEX idx_saved_addresses_user_default ON saved_addresses(user_id, is_default);

COMMENT ON TABLE  saved_addresses            IS 'Endereços salvos pelo contratante (RF-48).';
COMMENT ON COLUMN saved_addresses.label      IS 'Rótulo livre do endereço. Ex: Casa, Trabalho.';
COMMENT ON COLUMN saved_addresses.state      IS 'Sigla do estado com 2 caracteres (ex: SP, RJ).';
COMMENT ON COLUMN saved_addresses.is_default IS 'Apenas um endereço por usuário pode ter is_default=TRUE.';
COMMENT ON COLUMN saved_addresses.deleted_at IS 'Coluna herdada de PostgresEntity — não utilizada por endereços.';
