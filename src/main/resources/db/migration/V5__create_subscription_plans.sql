CREATE TABLE subscription_plans (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(60)   NOT NULL,
    price_monthly       NUMERIC(10,2) NOT NULL,
    highlight_in_search BOOLEAN       NOT NULL DEFAULT FALSE,
    express_priority    BOOLEAN       NOT NULL DEFAULT FALSE,
    badge_label         VARCHAR(30),
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP
);

COMMENT ON TABLE  subscription_plans                     IS 'Planos de assinatura opcionais para profissionais. null em professionals.subscription_plan_id = free tier.';
COMMENT ON COLUMN subscription_plans.highlight_in_search IS 'true = profissional aparece destacado nos resultados de busca.';
COMMENT ON COLUMN subscription_plans.express_priority    IS 'true = profissional recebe prioridade na fila do Express.';
COMMENT ON COLUMN subscription_plans.badge_label         IS 'Rótulo exibido no perfil do profissional. Ex: Pro, Verificado Plus.';
COMMENT ON COLUMN subscription_plans.is_active           IS 'false = plano oculto mas não deletado.';
COMMENT ON COLUMN subscription_plans.deleted_at          IS 'Soft delete.';
