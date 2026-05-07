-- 1. Remover colunas mortas de orders (expansão de raio não existe mais)
ALTER TABLE orders DROP COLUMN IF EXISTS search_radius_km;
ALTER TABLE orders DROP COLUMN IF EXISTS search_attempts;

-- 2. Adicionar marco da fase 1 (propostas abertas até esse instante)
ALTER TABLE orders ADD COLUMN proposal_deadline TIMESTAMPTZ;

-- Backfill para pedidos express existentes: created_at + 15 min,
-- o que naturalmente vence no próximo scheduler run para qualquer pedido antigo.
UPDATE orders SET proposal_deadline = created_at + interval '15 minutes'
WHERE mode = 'express' AND proposal_deadline IS NULL;

-- Para pedidos não-express (agendado), usar created_at como placeholder — nunca é lido fora do fluxo express.
UPDATE orders SET proposal_deadline = created_at
WHERE proposal_deadline IS NULL;

ALTER TABLE orders ALTER COLUMN proposal_deadline SET NOT NULL;

-- 3. Snapshot de distância na fila (calculado pelo Haversine no momento da notificação)
ALTER TABLE express_queue ADD COLUMN distance_meters INTEGER;

-- Backfill: entries antigas pertencem a pedidos já encerrados e nunca serão re-listadas.
-- Popular com 0 para satisfazer o NOT NULL.
UPDATE express_queue SET distance_meters = 0 WHERE distance_meters IS NULL;

ALTER TABLE express_queue ALTER COLUMN distance_meters SET NOT NULL;

CREATE INDEX idx_express_queue_distance ON express_queue (order_id, distance_meters);
