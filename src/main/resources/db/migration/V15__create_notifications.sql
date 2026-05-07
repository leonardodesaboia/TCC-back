ALTER TABLE users
    ADD COLUMN notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN users.notifications_enabled
    IS 'false = usuário desativou o envio de notificações push.';

CREATE TYPE notification_type AS ENUM (
    'new_request',
    'request_accepted',
    'request_rejected',
    'request_status_update',
    'new_message',
    'payment_released',
    'dispute_opened',
    'dispute_resolved',
    'verification_result'
);

CREATE TYPE platform AS ENUM ('android', 'ios');

CREATE TABLE push_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id),
    expo_token TEXT NOT NULL UNIQUE,
    platform   platform NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_push_tokens_user_id    ON push_tokens(user_id);
CREATE INDEX idx_push_tokens_expo_token ON push_tokens(expo_token);

COMMENT ON TABLE  push_tokens            IS 'Tokens de push por dispositivo. Um usuário pode ter múltiplos tokens.';
COMMENT ON COLUMN push_tokens.last_seen  IS 'Atualizado quando o app registra novamente o token. Tokens antigos podem ser removidos por scheduler.';

CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id),
    type       notification_type NOT NULL,
    title      VARCHAR(120),
    body       TEXT,
    data       JSONB,
    sent_at    TIMESTAMPTZ,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_read_at ON notifications(read_at);

COMMENT ON TABLE  notifications         IS 'Histórico e fila lógica de notificações. sent_at null = ainda não despachada.';
COMMENT ON COLUMN notifications.data    IS 'Payload extra para deep link. Ex.: {"orderId":"...","conversationId":"..."}';
