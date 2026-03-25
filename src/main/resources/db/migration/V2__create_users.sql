CREATE TYPE user_role AS ENUM ('client', 'professional', 'admin');

CREATE TABLE users (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(150) NOT NULL,
    cpf        VARCHAR(255) NOT NULL UNIQUE,
    cpf_hash   VARCHAR(64)  NOT NULL UNIQUE,
    email      VARCHAR(150) NOT NULL UNIQUE,
    phone      VARCHAR(20)  NOT NULL,
    password   VARCHAR(255) NOT NULL,
    role       user_role    NOT NULL,
    avatar_url TEXT,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    ban_reason TEXT,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

COMMENT ON TABLE  users            IS 'Entidade base de autenticação. role determina o tipo de ator.';
COMMENT ON COLUMN users.cpf        IS 'Armazenado criptografado com AES-256/CBC.';
COMMENT ON COLUMN users.cpf_hash   IS 'SHA-256 do CPF em texto puro — usado para verificação de unicidade sem descriptografar.';
COMMENT ON COLUMN users.deleted_at IS 'Soft delete — LGPD.';
