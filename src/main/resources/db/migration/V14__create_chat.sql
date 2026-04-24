-- Enum de tipo de mensagem (segue padrão lowercase do order_status)
CREATE TYPE msg_type AS ENUM ('text', 'image', 'system');

-- ─────────────────────────────────────────
-- CONVERSATIONS
-- ─────────────────────────────────────────

CREATE TABLE conversations (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id             UUID NOT NULL UNIQUE REFERENCES orders(id),
    client_id            UUID NOT NULL REFERENCES users(id),
    professional_user_id UUID NOT NULL REFERENCES users(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at           TIMESTAMPTZ
);

CREATE INDEX idx_conversations_client_id            ON conversations(client_id);
CREATE INDEX idx_conversations_professional_user_id ON conversations(professional_user_id);

COMMENT ON TABLE  conversations                      IS 'Conversa 1:1 entre cliente e profissional de um pedido aceito. Criada por OrderServiceImpl.clientRespond quando order transita para accepted.';
COMMENT ON COLUMN conversations.professional_user_id IS 'users.id do profissional (resolvido a partir de professionals.user_id no momento da criação).';

-- ─────────────────────────────────────────
-- MESSAGES
-- ─────────────────────────────────────────

CREATE TABLE messages (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id       UUID NOT NULL REFERENCES conversations(id),
    sender_id             UUID REFERENCES users(id),             -- NULL para system
    msg_type              msg_type NOT NULL DEFAULT 'text',
    content               TEXT,
    attachment_url        TEXT,
    attachment_size_bytes INT,
    attachment_mime_type  VARCHAR(64),
    sent_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at          TIMESTAMPTZ,
    read_at               TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at            TIMESTAMPTZ,

    CONSTRAINT messages_payload_check CHECK (
        (msg_type = 'text'   AND content IS NOT NULL AND sender_id IS NOT NULL) OR
        (msg_type = 'system' AND content IS NOT NULL AND sender_id IS NULL)     OR
        (msg_type = 'image'  AND attachment_url IS NOT NULL AND sender_id IS NOT NULL)
    )
);

CREATE INDEX idx_messages_conversation_sent_at
    ON messages(conversation_id, sent_at DESC);

COMMENT ON TABLE  messages              IS 'Mensagens em tempo real via WebSocket. Persistidas para histórico e evidência em disputas. Nunca são apagadas (deleted_at sempre NULL).';
COMMENT ON COLUMN messages.sender_id    IS 'NULL para mensagens do sistema (transições de pedido, avisos).';
COMMENT ON COLUMN messages.delivered_at IS 'Preenchido por bulk UPDATE quando o destinatário recebe via WebSocket.';
COMMENT ON COLUMN messages.read_at      IS 'Preenchido por bulk UPDATE quando o destinatário chama PATCH /read.';
