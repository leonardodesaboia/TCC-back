-- Permite que servicos com pricing_type = hourly usem o valor/hora da especialidade
-- quando price for NULL. Servicos com pricing_type = fixed continuam exigindo price.
ALTER TABLE professional_services ALTER COLUMN price DROP NOT NULL;

COMMENT ON COLUMN professional_services.price IS 'Valor por hora se hourly | valor total se fixed. Quando NULL em hourly, usa hourly_rate da especialidade (professional_specialties).';
