-- Enums
CREATE TYPE order_mode AS ENUM ('express', 'on_demand');

CREATE TYPE order_status AS ENUM (
    'pending',
    'accepted',
    'completed_by_pro',
    'completed',
    'cancelled',
    'disputed'
);

CREATE TYPE pro_response    AS ENUM ('accepted', 'rejected', 'timeout');
CREATE TYPE client_response AS ENUM ('accepted', 'rejected');
CREATE TYPE photo_type      AS ENUM ('request', 'completion_proof');

-- ─────────────────────────────────────────
-- PEDIDOS
-- ─────────────────────────────────────────

CREATE TABLE orders (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    version           INT           NOT NULL DEFAULT 1,
    client_id         UUID          NOT NULL REFERENCES users(id),
    professional_id   UUID          REFERENCES professionals(id),
    service_id        UUID          REFERENCES professional_services(id),
    area_id           UUID          REFERENCES service_areas(id),
    category_id       UUID          NOT NULL REFERENCES service_categories(id),
    mode              order_mode    NOT NULL,
    status            order_status  NOT NULL DEFAULT 'pending',
    description       TEXT          NOT NULL,
    address_id        UUID          NOT NULL REFERENCES saved_addresses(id),
    address_snapshot  JSONB         NOT NULL,
    scheduled_at      TIMESTAMPTZ,
    expires_at        TIMESTAMPTZ   NOT NULL,
    urgency_fee       NUMERIC(10,2),
    base_amount       NUMERIC(10,2),
    platform_fee      NUMERIC(10,2),
    total_amount      NUMERIC(10,2),
    search_radius_km  NUMERIC(5,2)  NOT NULL DEFAULT 15,
    search_attempts   SMALLINT      NOT NULL DEFAULT 1,
    pro_completed_at  TIMESTAMPTZ,
    dispute_deadline  TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    cancelled_at      TIMESTAMPTZ,
    cancel_reason     TEXT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ
);

CREATE INDEX idx_orders_status          ON orders(status);
CREATE INDEX idx_orders_client_id       ON orders(client_id);
CREATE INDEX idx_orders_professional_id ON orders(professional_id);
CREATE INDEX idx_orders_category_id     ON orders(category_id);
CREATE INDEX idx_orders_mode            ON orders(mode);
CREATE INDEX idx_orders_expires_at      ON orders(expires_at);
CREATE INDEX idx_orders_scheduled_at    ON orders(scheduled_at);

COMMENT ON TABLE  orders                  IS 'Entidade central. Express: cliente escolhe área+categoria → todos os profissionais próximos são notificados → cliente escolhe proposta → conclusão.';
COMMENT ON COLUMN orders.version          IS 'Optimistic locking — previne race conditions em transições de status.';
COMMENT ON COLUMN orders.area_id          IS 'Área de serviço escolhida pelo cliente (ex: Elétrica). Implica a categoria.';
COMMENT ON COLUMN orders.address_snapshot IS 'Cópia imutável do endereço no momento do pedido — preserva histórico.';
COMMENT ON COLUMN orders.expires_at       IS 'Prazo da fase atual: janela de resposta dos profissionais (10min) ou janela de escolha do cliente (30min desde 1ª proposta).';
COMMENT ON COLUMN orders.search_radius_km IS 'Raio atual de busca — aumentado a cada tentativa sem propostas.';
COMMENT ON COLUMN orders.search_attempts  IS 'Número de rodadas de busca realizadas. Máximo definido em AppProperties.';
COMMENT ON COLUMN orders.dispute_deadline IS 'pro_completed_at + 24h — após esse prazo não é possível abrir disputa.';

-- ─────────────────────────────────────────
-- HISTÓRICO DE STATUS (audit log imutável)
-- ─────────────────────────────────────────

CREATE TABLE order_status_history (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID         NOT NULL REFERENCES orders(id),
    from_status order_status,
    to_status   order_status NOT NULL,
    reason      TEXT,
    changed_by  UUID         REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_status_history_order_id ON order_status_history(order_id);

COMMENT ON TABLE  order_status_history             IS 'Auditoria imutável. Nunca deletar ou atualizar registros desta tabela.';
COMMENT ON COLUMN order_status_history.from_status IS 'null na primeira transição (criação do pedido).';
COMMENT ON COLUMN order_status_history.changed_by  IS 'null = transição automática do sistema (timeout, scheduler, etc.).';

-- ─────────────────────────────────────────
-- FOTOS DO PEDIDO
-- ─────────────────────────────────────────

CREATE TABLE order_photos (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID        NOT NULL REFERENCES orders(id),
    uploader_id UUID        NOT NULL REFERENCES users(id),
    photo_type  photo_type  NOT NULL,
    url         TEXT        NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_photos_order_id ON order_photos(order_id);

COMMENT ON TABLE  order_photos            IS 'Fotos do pedido. request = foto do problema (cliente, opcional) | completion_proof = foto de conclusão (profissional, obrigatória).';
COMMENT ON COLUMN order_photos.photo_type IS 'request = enviada na criação | completion_proof = obrigatória para transitar para completed_by_pro.';

-- ─────────────────────────────────────────
-- FILA EXPRESS (broadcast — todos notificados ao mesmo tempo)
-- ─────────────────────────────────────────

CREATE TABLE express_queue (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID            NOT NULL REFERENCES orders(id),
    professional_id     UUID            NOT NULL REFERENCES professionals(id),
    proposed_amount     NUMERIC(10,2),
    notified_at         TIMESTAMPTZ     NOT NULL,
    responded_at        TIMESTAMPTZ,
    pro_response        pro_response,
    client_response     client_response,
    client_responded_at TIMESTAMPTZ,
    queue_position      SMALLINT        NOT NULL
);

CREATE INDEX idx_express_queue_order_id         ON express_queue(order_id);
CREATE INDEX idx_express_queue_professional_id  ON express_queue(professional_id);
CREATE UNIQUE INDEX idx_express_queue_order_position ON express_queue(order_id, queue_position);

COMMENT ON TABLE  express_queue                 IS 'Broadcast: todos os profissionais da rodada são notificados simultaneamente. Cada um propõe seu preço. Cliente escolhe a proposta preferida.';
COMMENT ON COLUMN express_queue.notified_at     IS 'Quando o profissional foi notificado. Todos da mesma rodada recebem NOW() ao criar o pedido.';
COMMENT ON COLUMN express_queue.queue_position  IS 'Ordem de prioridade para desempate (menor = maior prioridade: assinante pro + proximidade).';
COMMENT ON COLUMN express_queue.proposed_amount IS 'Preço proposto. Null se rejeitou ou timeout.';
