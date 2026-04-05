CREATE TYPE doc_type AS ENUM ('rg', 'cnh', 'proof_of_address', 'profile_photo');

CREATE TABLE professional_documents (
    id              UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    professional_id UUID      NOT NULL REFERENCES professionals(id),
    doc_type        doc_type  NOT NULL,
    file_url        TEXT      NOT NULL,
    uploaded_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    verified        BOOLEAN   NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_professional_documents_professional_id ON professional_documents(professional_id);

COMMENT ON TABLE  professional_documents          IS 'Documentos enviados no cadastro. Armazenados no S3 e processados pelo IDwall.';
COMMENT ON COLUMN professional_documents.verified IS 'false = aguardando IDwall | true = IDwall confirmou via webhook.';
COMMENT ON COLUMN professional_documents.file_url IS 'URL no S3.';
