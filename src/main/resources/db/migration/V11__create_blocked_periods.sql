CREATE TYPE block_type AS ENUM ('recurring', 'specific_date', 'order');

CREATE TABLE blocked_periods (
    id              UUID       PRIMARY KEY DEFAULT gen_random_uuid(),
    professional_id UUID       NOT NULL REFERENCES professionals(id),
    block_type      block_type NOT NULL,
    weekday         SMALLINT,
    specific_date   DATE,
    starts_at       TIME,
    ends_at         TIME,
    order_id        UUID,
    order_starts_at TIMESTAMP,
    order_ends_at   TIMESTAMP,
    reason          TEXT,
    created_at      TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_blocked_periods_professional_id ON blocked_periods(professional_id);
CREATE INDEX idx_blocked_periods_recurring        ON blocked_periods(professional_id, weekday);
CREATE INDEX idx_blocked_periods_specific         ON blocked_periods(professional_id, specific_date);
CREATE INDEX idx_blocked_periods_order_id         ON blocked_periods(order_id);

COMMENT ON TABLE  blocked_periods                IS 'Calendário invertido — padrão livre, profissional registra bloqueios.';
COMMENT ON COLUMN blocked_periods.weekday        IS '0=Dom, 1=Seg … 6=Sáb | obrigatório se block_type = recurring.';
COMMENT ON COLUMN blocked_periods.specific_date  IS 'Obrigatório se block_type = specific_date.';
COMMENT ON COLUMN blocked_periods.starts_at      IS 'Horário de início | null = dia inteiro bloqueado.';
COMMENT ON COLUMN blocked_periods.ends_at        IS 'Horário de fim | null = dia inteiro bloqueado.';
COMMENT ON COLUMN blocked_periods.order_id       IS 'Obrigatório se block_type = order — gerado automaticamente ao aceitar pedido on_demand.';
COMMENT ON COLUMN blocked_periods.order_starts_at IS 'Início do bloqueio por pedido | obrigatório se block_type = order.';
COMMENT ON COLUMN blocked_periods.order_ends_at   IS 'Fim do bloqueio por pedido | obrigatório se block_type = order.';
