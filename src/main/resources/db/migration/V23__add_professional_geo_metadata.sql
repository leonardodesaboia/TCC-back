-- V23 — metadados de geolocalização do profissional
-- Habilita filtro de recência (geo_captured_at) na matching query do Express,
-- que hoje filtra apenas por geo_active. A query será atualizada em trabalho
-- futuro; esta migration prepara o terreno (coluna + índice parcial).

ALTER TABLE professionals
  ADD COLUMN geo_captured_at      TIMESTAMP,
  ADD COLUMN geo_accuracy_meters  NUMERIC(7,2),
  ADD COLUMN geo_source           VARCHAR(20);

COMMENT ON COLUMN professionals.geo_captured_at     IS 'Timestamp da captura de geo_lat/geo_lng. Usado para filtro de recência no matching Express.';
COMMENT ON COLUMN professionals.geo_accuracy_meters IS 'Acurácia reportada pelo dispositivo na captura (metros). Informativo.';
COMMENT ON COLUMN professionals.geo_source          IS 'Origem da localização. Ex: device-gps. Informativo.';

CREATE INDEX idx_professionals_geo_active_captured
  ON professionals (geo_active, geo_captured_at)
  WHERE geo_active = true;
