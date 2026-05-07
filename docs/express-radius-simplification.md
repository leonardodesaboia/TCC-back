# Express — Simplificação do Match por Raio (V22)

> Este documento descreve as mudanças implementadas no módulo `order` referentes ao plano `7_express_radius_simplification_plan.md`, mergeado em `develop` em 2026-05-01.
>
> **Supera:** `EXPRESS_MATCHING_SPEC.md` e `EXPRESS_MATCHING_IMPLEMENTATION_PLAN.md` (ambos refletem a spec antiga de 3 ondas progressivas e estão desatualizados).

---

## O que mudou

### Antes

O fluxo Express operava com **expansão progressiva de raio em quilômetros**:

- Raio inicial: `0.1 km`, expandindo linearmente até `0.3 km` em até 3 tentativas.
- Cada profissional tinha um timeout individual de `5 minutos` para responder.
- Ao chegar a **primeira proposta**, o `expiresAt` do pedido era reescrito para `now + 30 min` (janela do cliente).
- Não havia registro da distância entre o cliente e cada profissional.

### Depois

O fluxo Express passa a operar em **escala de bairro, raio único**:

- **Raio fixo de 300 metros** (configurável em `EXPRESS_SEARCH_RADIUS_METERS`), sem expansão.
- **Janela em duas fases** sob um único status `pending`:
  - **Fase 1 (0–15 min):** profissionais podem enviar propostas; cliente já pode escolher a qualquer momento.
  - **Fase 2 (15–45 min):** novas propostas são bloqueadas; cliente ainda pode escolher entre as já recebidas.
  - **45 min:** cancelamento automático se o cliente não escolheu nenhuma proposta.
- **Snapshot de distância** armazenado em `express_queue.distance_meters` no momento da notificação — imutável.
- **Faixa de distância** exibida ao cliente em cada proposta (`DistanceBand`): o raio é dividido em 3 partes iguais (ex.: `0-100m`, `100-200m`, `200-300m` para raio de 300 m). A distância exata nunca é exposta (privacidade do profissional).

---

## Mudanças técnicas resumidas

| Área | O que mudou |
|---|---|
| **Migration V22** | `DROP COLUMN search_radius_km`, `DROP COLUMN search_attempts`; `ADD COLUMN proposal_deadline TIMESTAMPTZ NOT NULL`; `ADD COLUMN distance_meters INTEGER NOT NULL` em `express_queue` |
| **`AppProperties`** | Removidas 4 envs (`EXPRESS_PRO_TIMEOUT_MINUTES`, `EXPRESS_SEARCH_RADIUS_KM`, `EXPRESS_MAX_SEARCH_ATTEMPTS`, `EXPRESS_MAX_RADIUS_KM`); adicionadas `EXPRESS_SEARCH_RADIUS_METERS` (padrão 300) e `EXPRESS_PROPOSAL_WINDOW_MINUTES` (padrão 15) |
| **`Order`** | Removidos campos `searchRadiusKm` e `searchAttempts`; adicionado `proposalDeadline` |
| **`ExpressQueueEntry`** | Adicionado campo `distanceMeters` |
| **`ExpressQueueRepository`** | Nova query `findNearbyProfessionals` retorna pares `(professionalId, distanceMeters)` em metros; adicionado `markPendingEntriesAsTimeout`; removidos métodos mortos |
| **`OrderRepository`** | Adicionados `findExpressIdsWithExpiredProposalWindow` e `findExpressIdsToExpire`; removida query antiga |
| **`ExpressWindowProcessor`** | Reescrito: apenas `closeProposalWindow` (marca timeouts em lote) e `cancelExpiredOrder` |
| **`OrderServiceImpl`** | `createExpressOrder` usa metros e define ambas as datas; `proRespond` bloqueia propostas após `proposalDeadline`; `processExpiredWindows` usa as duas novas queries |
| **`OrderMapper`** | Injeta `AppProperties`; novo método `toProposalResponse` e utilitário estático `computeDistanceBand` |
| **`ExpressProposalResponse`** | Novo campo `distanceBand` (`DistanceBand`) |
| **`OrderResponse`** | Removidos `searchRadiusKm`/`searchAttempts`; adicionado `proposalDeadline` |
| **`ProposalWindowExpiredException`** | Nova exceção — 409 Conflict — lançada quando profissional tenta propor após `proposalDeadline` |
| **Migration V21** | Renumeração de `V18__create_favorite_professionals.sql` para resolver conflito de versão do Flyway |

---

## Variáveis de ambiente relevantes

| Variável | Padrão | Descrição |
|---|---|---|
| `EXPRESS_SEARCH_RADIUS_METERS` | `300` | Raio de busca em metros. Mínimo: 1. Máximo: 5000. |
| `EXPRESS_PROPOSAL_WINDOW_MINUTES` | `15` | Minutos a partir da criação do pedido em que profissionais podem enviar propostas. |
| `EXPRESS_CLIENT_WINDOW_MINUTES` | `30` | Minutos adicionais (após o fim das propostas) para o cliente escolher. Total: 45 min. |
| `EXPRESS_MAX_QUEUE_SIZE` | `10` | Número máximo de profissionais notificados por pedido. |

> **Atenção para quem tem `.env` local:** remover as envs antigas (`EXPRESS_PRO_TIMEOUT_MINUTES`, `EXPRESS_SEARCH_RADIUS_KM`, `EXPRESS_MAX_SEARCH_ATTEMPTS`, `EXPRESS_MAX_RADIUS_KM`) e adicionar as novas. A aplicação falha no startup com mensagem clara se houver envs obrigatórias ausentes.

---

## Como testar manualmente no Swagger

### Pré-requisitos

1. Infra local rodando: `docker compose up -d postgres redis`
2. Aplicação no perfil `dev`: `./mvnw spring-boot:run`
3. Swagger UI: `http://localhost:8080/swagger-ui.html`
4. Seed habilitado (`SEED_ENABLED=true`) **ou** criar os dados manualmente conforme os passos abaixo.

---

### Cenário 1 — Pedido Express criado, proposta recebida, cliente escolhe

**Objetivo:** verificar que `proposalDeadline` e `expiresAt` são calculados corretamente e que `distanceBand` aparece na proposta.

1. Autentique-se como **cliente** e obtenha o Bearer token.
2. Crie um endereço com `lat`/`lng` próximos à localização de um profissional cadastrado (ex: Reitoria UNIFOR: `-3.7327, -38.5267`).
3. `POST /api/v1/orders/express` com `categoryId` de uma categoria que o profissional tem especialidade.
4. Na resposta, verifique:
   - `proposalDeadline` ≈ `now + 15 min`
   - `expiresAt` ≈ `now + 45 min`
   - `status = pending`
5. Autentique-se como **profissional** e `POST /api/v1/orders/{id}/proposals` com `response=accepted` e `proposedAmount`.
6. Volte como **cliente** e `GET /api/v1/orders/{id}/proposals`:
   - A proposta deve conter `distanceBand` com `label`, `minMeters` e `maxMeters` dentro do intervalo `[0, 300]`.
7. `POST /api/v1/orders/{id}/choose` com o `professionalId` da proposta → pedido avança para `accepted`.

---

### Cenário 2 — Proposta bloqueada após `proposalDeadline`

**Objetivo:** verificar que o fast-fail de `ProposalWindowExpiredException` funciona.

1. Crie um pedido Express conforme o Cenário 1.
2. Aguarde 15 minutos (ou, em ambiente de teste, ajuste `EXPRESS_PROPOSAL_WINDOW_MINUTES=0` no `.env` e reinicie).
3. Tente `POST /api/v1/orders/{id}/proposals` como profissional.
4. Resposta esperada: **409 Conflict** com mensagem `"Prazo para envio de propostas encerrado para o pedido: {id}"`.

---

### Cenário 3 — Cancelamento automático por expiração total (sem propostas)

**Objetivo:** verificar que o scheduler cancela o pedido com motivo correto quando ninguém propõe.

1. Ajuste as envs para janelas curtas: `EXPRESS_PROPOSAL_WINDOW_MINUTES=1`, `EXPRESS_CLIENT_WINDOW_MINUTES=1`.
2. Crie um pedido Express em uma categoria sem profissionais próximos **ou** garanta que nenhum profissional responda.
3. Aguarde ~2 minutos (o scheduler roda a cada 60 s).
4. `GET /api/v1/orders/{id}` → `status = cancelled`, `cancelReason = "Nenhum profissional aceitou o pedido no prazo"`.

---

### Cenário 4 — Cancelamento automático com propostas (cliente não escolheu)

**Objetivo:** verificar que o scheduler cancela com motivo diferente quando havia propostas mas o cliente não escolheu.

1. Mesmas envs do Cenário 3.
2. Crie o pedido, faça o profissional aceitar com proposta.
3. Aguarde ~2 minutos sem o cliente escolher.
4. `GET /api/v1/orders/{id}` → `status = cancelled`, `cancelReason = "Prazo para escolha de proposta expirado"`.

---

### Cenário 5 — Nenhum profissional no raio

**Objetivo:** verificar que `NoProfessionalsAvailableException` é retornada quando o raio não cobre nenhum profissional.

1. Defina `EXPRESS_SEARCH_RADIUS_METERS=1` no `.env` (raio mínimo).
2. Crie um pedido Express.
3. Resposta esperada: **422 Unprocessable Entity** com mensagem indicando ausência de profissionais.

---

### Verificação da faixa de distância (`computeDistanceBand`)

A tabela abaixo mostra os valores esperados para raio padrão de 300 m:

| `distanceMeters` | `label` | `minMeters` | `maxMeters` |
|---|---|---|---|
| 0 | `0-100m` | 0 | 100 |
| 99 | `0-100m` | 0 | 100 |
| 100 | `100-200m` | 100 | 200 |
| 199 | `100-200m` | 100 | 200 |
| 200 | `200-300m` | 200 | 300 |
| 300 | `200-300m` | 200 | 300 |

Para raio de 500 m (`bandSize = ceil(500/3) = 167`):

| `distanceMeters` | `label` |
|---|---|
| 0–166 | `0-167m` |
| 167–333 | `167-334m` |
| 334–500 | `334-500m` |

---

## Resolução do conflito Flyway V18

Se ao subir a aplicação você ver o erro `Found more than one migration with version 18`, significa que o seu ambiente já aplicou uma das duas migrações V18 antes da renumeração. Execute:

```bash
# Conecte ao banco e verifique qual foi aplicada
SELECT version, description, installed_on
FROM flyway_schema_history
WHERE version = '18'
ORDER BY installed_rank;

# Se necessário, corrija o histórico do Flyway
./mvnw flyway:repair
```

Após o repair, o Flyway reconhecerá `V21__create_favorite_professionals.sql` como a versão correta e prosseguirá normalmente.
