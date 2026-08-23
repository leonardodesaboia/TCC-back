-- V25 — procedência da coordenada do endereço salvo
--
-- Até aqui a API só sabia SE havia lat/lng, nunca DE ONDE vieram. Isso permitia
-- que um centroide de município (devolvido pelo provider de CEP) fosse tratado
-- como coordenada válida e disparasse o matching Express de raio curto.
--
-- Espelha o que a V23 fez em professionals, agora do lado do cliente.

ALTER TABLE saved_addresses
  ADD COLUMN coordinate_source          VARCHAR(20),
  ADD COLUMN coordinate_accuracy_meters NUMERIC(7,2),
  ADD COLUMN coordinate_confidence      VARCHAR(20),
  ADD COLUMN coordinate_confirmed_at    TIMESTAMPTZ;

COMMENT ON COLUMN saved_addresses.coordinate_source IS
  'Origem da coordenada: device_gps | user_pin | geocoded | legacy. NULL quando não há coordenada.';
COMMENT ON COLUMN saved_addresses.coordinate_accuracy_meters IS
  'Acurácia reportada pelo dispositivo (metros). Só faz sentido com coordinate_source = device_gps.';
COMMENT ON COLUMN saved_addresses.coordinate_confidence IS
  'Confiança do provider quando coordinate_source = geocoded: ROOFTOP | INTERPOLATED | CITY.';
COMMENT ON COLUMN saved_addresses.coordinate_confirmed_at IS
  'Momento em que a coordenada foi confirmada pelo usuário ou capturada pelo dispositivo (UTC).';

-- Backfill: tudo que já tem coordenada foi gravado sob as regras antigas e não é
-- confiável para Express. Registros sem coordenada continuam com origem NULL —
-- não há coordenada para desconfiar.
UPDATE saved_addresses
   SET coordinate_source = 'legacy'
 WHERE lat IS NOT NULL
   AND lng IS NOT NULL;

ALTER TABLE saved_addresses
  ADD CONSTRAINT chk_saved_addresses_coordinate_source
  CHECK (coordinate_source IS NULL
         OR coordinate_source IN ('device_gps', 'user_pin', 'geocoded', 'legacy'));

-- Acurácia só existe quando a coordenada veio do GPS do aparelho.
ALTER TABLE saved_addresses
  ADD CONSTRAINT chk_saved_addresses_coordinate_accuracy
  CHECK (coordinate_accuracy_meters IS NULL
         OR coordinate_source = 'device_gps');

-- Coordenada e origem andam juntas: origem preenchida exige lat/lng.
ALTER TABLE saved_addresses
  ADD CONSTRAINT chk_saved_addresses_coordinate_pair
  CHECK (coordinate_source IS NULL
         OR (lat IS NOT NULL AND lng IS NOT NULL));

CREATE INDEX idx_saved_addresses_coordinate_source
  ON saved_addresses (coordinate_source);

-- Correção de convenção herdada da V23: geo_captured_at nasceu como TIMESTAMP,
-- mas o CLAUDE.md exige Instant/UTC em toda timestamp. Os valores existentes já
-- foram gravados em UTC pela aplicação, então a conversão é direta.
ALTER TABLE professionals
  ALTER COLUMN geo_captured_at TYPE TIMESTAMPTZ
  USING geo_captured_at AT TIME ZONE 'UTC';
