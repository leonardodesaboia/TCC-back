ALTER TABLE orders ADD COLUMN estimated_duration_minutes INTEGER;

COMMENT ON COLUMN orders.estimated_duration_minutes IS
    'Duração estimada em minutos para serviços por hora. Null para serviços de preço fixo sem duração definida.';
