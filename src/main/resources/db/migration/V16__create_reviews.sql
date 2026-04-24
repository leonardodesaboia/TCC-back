CREATE TABLE reviews (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID        NOT NULL REFERENCES orders(id),
    reviewer_id   UUID        NOT NULL REFERENCES users(id),
    reviewee_id   UUID        NOT NULL REFERENCES users(id),
    rating        SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment       TEXT,
    submitted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_reviews_order_reviewer ON reviews(order_id, reviewer_id);
CREATE INDEX idx_reviews_reviewee_id ON reviews(reviewee_id);
CREATE INDEX idx_reviews_published_at ON reviews(published_at);
CREATE INDEX idx_reviews_order_id ON reviews(order_id);

COMMENT ON TABLE reviews IS 'Avaliacoes double-blind entre cliente e profissional. Publicadas quando ambos avaliarem ou apos 7 dias da conclusao.';
COMMENT ON COLUMN reviews.comment IS 'Obrigatorio para cliente -> profissional. Profissional -> cliente nao aceita comentario.';
COMMENT ON COLUMN reviews.published_at IS 'Null ate ambas as partes avaliarem ou o prazo de 7 dias expirar.';
