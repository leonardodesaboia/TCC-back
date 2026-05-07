-- ─────────────────────────────────────────
-- ENUMS
-- ─────────────────────────────────────────

CREATE TYPE dispute_status     AS ENUM ('open', 'under_review', 'resolved');
CREATE TYPE dispute_resolution AS ENUM ('refund_full', 'refund_partial', 'release_to_pro');
CREATE TYPE evidence_type      AS ENUM ('text', 'photo');

-- ─────────────────────────────────────────
-- DISPUTAS
-- ─────────────────────────────────────────

CREATE TABLE disputes (
    id                   UUID               PRIMARY KEY DEFAULT gen_random_uuid(),
    version              INT                NOT NULL DEFAULT 1,
    order_id             UUID               NOT NULL UNIQUE REFERENCES orders(id),
    opened_by            UUID               NOT NULL REFERENCES users(id),
    reason               TEXT               NOT NULL,
    status               dispute_status     NOT NULL DEFAULT 'open',
    resolution           dispute_resolution,
    admin_notes          TEXT,
    client_refund_amount NUMERIC(10,2),
    professional_amount  NUMERIC(10,2),
    resolved_by          UUID               REFERENCES users(id),
    resolved_at          TIMESTAMPTZ,
    opened_at            TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    created_at           TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    deleted_at           TIMESTAMPTZ
);

CREATE INDEX idx_disputes_status   ON disputes(status);
CREATE INDEX idx_disputes_order_id ON disputes(order_id);

COMMENT ON TABLE  disputes                       IS 'Disputas abertas pelo cliente em ate 24h apos pro_completed_at. Resolvidas exclusivamente pelo admin.';
COMMENT ON COLUMN disputes.version               IS 'Optimistic locking — previne dois admins resolvendo simultaneamente.';
COMMENT ON COLUMN disputes.resolution            IS 'Preenchido pelo admin: refund_full (100%% cliente), refund_partial (split), release_to_pro (100%% profissional).';
COMMENT ON COLUMN disputes.admin_notes           IS 'Notas internas do admin — nao exibidas ao cliente/profissional.';
COMMENT ON COLUMN disputes.client_refund_amount  IS 'Valor devolvido ao cliente. Invariante: client_refund_amount + professional_amount = orders.total_amount.';
COMMENT ON COLUMN disputes.professional_amount   IS 'Valor liberado ao profissional na resolucao da disputa.';

-- ─────────────────────────────────────────
-- EVIDENCIAS DE DISPUTA
-- ─────────────────────────────────────────

CREATE TABLE dispute_evidences (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id    UUID          NOT NULL REFERENCES disputes(id),
    sender_id     UUID          NOT NULL REFERENCES users(id),
    evidence_type evidence_type NOT NULL,
    content       TEXT,
    file_key      TEXT,
    file_size_bytes BIGINT,
    file_mime_type  VARCHAR(64),
    sent_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ,

    CONSTRAINT evidence_payload_check CHECK (
        (evidence_type = 'text'  AND content  IS NOT NULL) OR
        (evidence_type = 'photo' AND file_key IS NOT NULL)
    )
);

CREATE INDEX idx_dispute_evidences_dispute_id ON dispute_evidences(dispute_id);

COMMENT ON TABLE  dispute_evidences                IS 'Evidencias de disputa enviadas por cliente, profissional ou admin. Imutaveis apos envio.';
COMMENT ON COLUMN dispute_evidences.evidence_type  IS 'text = descricao textual | photo = foto como prova (upload via storage).';
COMMENT ON COLUMN dispute_evidences.content        IS 'Obrigatorio se evidence_type = text. Opcional como legenda se evidence_type = photo.';
COMMENT ON COLUMN dispute_evidences.file_key       IS 'Chave do objeto no bucket dispute-evidences (MinIO/S3). Obrigatorio se evidence_type = photo.';
COMMENT ON COLUMN dispute_evidences.file_size_bytes IS 'Tamanho do arquivo em bytes — preenchido a partir do StoredObject retornado pelo storage.';
COMMENT ON COLUMN dispute_evidences.file_mime_type  IS 'MIME type do arquivo — image/jpeg ou image/png.';
