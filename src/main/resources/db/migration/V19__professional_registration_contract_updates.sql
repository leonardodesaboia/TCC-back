ALTER TABLE users
    ADD COLUMN birth_date DATE;

UPDATE users
SET birth_date = DATE '2000-01-01'
WHERE birth_date IS NULL;

ALTER TABLE users
    ALTER COLUMN birth_date SET NOT NULL;

CREATE TYPE document_side AS ENUM ('front', 'back');

ALTER TABLE professional_documents
    ADD COLUMN doc_side document_side;

UPDATE professional_documents
SET doc_side = 'front'
WHERE doc_side IS NULL;

ALTER TABLE professional_documents
    ALTER COLUMN doc_side SET NOT NULL;

DROP INDEX IF EXISTS idx_professional_documents_professional_id;
CREATE INDEX idx_professional_documents_professional_id ON professional_documents(professional_id);
CREATE UNIQUE INDEX uq_professional_documents_identity_side
    ON professional_documents(professional_id, doc_type, doc_side);

ALTER TABLE professional_specialties
    ADD COLUMN hourly_rate NUMERIC(10,2);

COMMENT ON COLUMN users.birth_date IS 'Data de nascimento do usuário.';
COMMENT ON COLUMN professional_documents.doc_side IS 'Lado do documento de identificação: frente ou verso.';
COMMENT ON COLUMN professional_specialties.hourly_rate IS 'Valor/hora opcional da profissão específica.';
