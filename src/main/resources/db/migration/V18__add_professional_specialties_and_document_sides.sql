CREATE TABLE professional_specialties (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    professional_id     UUID         NOT NULL REFERENCES professionals(id),
    category_id         UUID         NOT NULL REFERENCES service_categories(id),
    years_of_experience SMALLINT     NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_professional_specialties_pro_category
    ON professional_specialties(professional_id, category_id);

CREATE INDEX idx_professional_specialties_professional_id
    ON professional_specialties(professional_id);

CREATE INDEX idx_professional_specialties_category_id
    ON professional_specialties(category_id);

COMMENT ON TABLE professional_specialties
    IS 'Especialidades do profissional por categoria, com experiência específica para cada uma.';
COMMENT ON COLUMN professional_specialties.years_of_experience
    IS 'Experiência declarada para a categoria específica.';

ALTER TYPE doc_type ADD VALUE IF NOT EXISTS 'document_front';
ALTER TYPE doc_type ADD VALUE IF NOT EXISTS 'document_back';
