-- Enums
CREATE TYPE payment_status AS ENUM (
    'pending',
    'confirmed',
    'released',
    'refunded',
    'refunded_partial',
    'failed',
    'held',
    'cancelled'
);

CREATE TYPE payment_method AS ENUM (
    'pix',
    'credit_card',
    'boleto'
);

CREATE TYPE transaction_type AS ENUM (
    'charge',
    'refund',
    'transfer'
);

CREATE TYPE transaction_status AS ENUM (
    'pending',
    'confirmed',
    'failed'
);

-- Tabela principal
CREATE TABLE payments (
    id                         UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                   UUID            NOT NULL REFERENCES orders(id),
    payer_user_id              UUID            NOT NULL REFERENCES users(id),
    receiver_professional_id   UUID            NOT NULL REFERENCES professionals(id),
    status                     payment_status  NOT NULL DEFAULT 'pending',
    method                     payment_method  NOT NULL,
    gross_amount               NUMERIC(10,2)   NOT NULL,
    platform_fee               NUMERIC(10,2)   NOT NULL,
    net_amount                 NUMERIC(10,2)   NOT NULL,
    refund_amount              NUMERIC(10,2),
    asaas_payment_id           VARCHAR(100),
    asaas_transfer_id          VARCHAR(100),
    pix_copy_paste             TEXT,
    pix_qr_code_url            TEXT,
    invoice_url                TEXT,
    paid_at                    TIMESTAMPTZ,
    released_at                TIMESTAMPTZ,
    refunded_at                TIMESTAMPTZ,
    failure_reason             TEXT,
    version                    INT             NOT NULL DEFAULT 1,
    created_at                 TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at                 TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_payments_order_id ON payments(order_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_payments_payer_user_id ON payments(payer_user_id);
CREATE INDEX idx_payments_receiver_professional_id ON payments(receiver_professional_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_asaas_payment_id ON payments(asaas_payment_id);

COMMENT ON TABLE  payments                             IS 'Pagamentos via escrow Asaas. Um pagamento por pedido.';
COMMENT ON COLUMN payments.gross_amount                IS 'Valor cobrado do cliente (base_amount + urgency_fee do pedido).';
COMMENT ON COLUMN payments.platform_fee                IS 'Taxa da plataforma: 20% do gross_amount, descontada na liberacao.';
COMMENT ON COLUMN payments.net_amount                  IS 'Valor repassado ao profissional: gross_amount - platform_fee.';
COMMENT ON COLUMN payments.refund_amount               IS 'Valor efetivamente devolvido. Cancelamento < 24h = 50%.';
COMMENT ON COLUMN payments.asaas_payment_id            IS 'ID da cobranca na API do Asaas.';
COMMENT ON COLUMN payments.asaas_transfer_id           IS 'ID da transferencia de liberacao no Asaas.';

-- Transacoes financeiras individuais
CREATE TABLE payment_transactions (
    id              UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      UUID                NOT NULL REFERENCES payments(id),
    type            transaction_type    NOT NULL,
    status          transaction_status  NOT NULL DEFAULT 'pending',
    amount          NUMERIC(10,2)       NOT NULL,
    asaas_id        VARCHAR(100),
    failure_reason  TEXT,
    processed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_transactions_payment_id ON payment_transactions(payment_id);
CREATE INDEX idx_payment_transactions_asaas_id ON payment_transactions(asaas_id);
CREATE INDEX idx_payment_transactions_type_status ON payment_transactions(type, status);

COMMENT ON TABLE  payment_transactions              IS 'Cada operacao financeira com o Asaas: cobranca, estorno, transferencia. Um pagamento pode ter multiplas transacoes.';
COMMENT ON COLUMN payment_transactions.type         IS 'charge = cobranca ao cliente | refund = estorno | transfer = liberacao ao profissional.';
COMMENT ON COLUMN payment_transactions.asaas_id     IS 'ID da operacao retornado pela API do Asaas.';
COMMENT ON COLUMN payment_transactions.processed_at IS 'Quando a operacao foi confirmada/processada pelo Asaas.';

-- Historico de status (audit log imutavel)
CREATE TABLE payment_status_history (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id  UUID            NOT NULL REFERENCES payments(id),
    from_status payment_status,
    to_status   payment_status  NOT NULL,
    reason      TEXT,
    changed_by  UUID            REFERENCES users(id),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_status_history_payment_id ON payment_status_history(payment_id);

COMMENT ON TABLE  payment_status_history             IS 'Auditoria imutavel de transicoes de pagamento. Nunca deletar.';
COMMENT ON COLUMN payment_status_history.from_status IS 'null na criacao do pagamento.';
COMMENT ON COLUMN payment_status_history.changed_by  IS 'null = transicao automatica (webhook, scheduler).';
