# All Set API: analise funcional completa

## Resumo executivo

- Stack: Spring Boot 3.5, Java 21, PostgreSQL, Redis, Flyway, JWT HS256.
- Endpoints mapeados no projeto atual: 64 rotas de controllers + 2 rotas utilitarias de infra (`/actuator/health` e `/v3/api-docs` no perfil `dev`).
- Base paths reais:
  - `/api/auth`
  - `/api/users`
  - `/api/v1/**`
  - `/actuator/health`
  - `/v3/api-docs`
- O backend esta organizado por modulo: `auth`, `user`, `address`, `professional`, `document`, `offering`, `catalog`, `subscription`, `calendar` e `order`.

## Seguranca efetiva

- Publico:
  - `POST /api/auth/login`
  - `POST /api/auth/refresh`
  - `POST /api/auth/logout`
  - `POST /api/auth/forgot-password`
  - `POST /api/auth/reset-password`
  - `POST /api/users`
  - `POST /api/v1/professionals`
  - `GET /actuator/health`
  - `GET /v3/api-docs`
  - `GET /swagger-ui.html`
- Todos os demais endpoints exigem JWT Bearer.
- O access token usa:
  - `sub`: UUID do usuario
  - `role`: `client`, `professional` ou `admin`
- O refresh token:
  - e JWT separado com claim `type=refresh`
  - fica salvo no Redis por usuario
  - e invalidado na rotacao e no reset de senha
- Roles realmente aplicadas no codigo atual:
  - `client`: criar pedido Express, escolher proposta, confirmar conclusao
  - `professional`: responder pedido Express, concluir pedido
  - `client|professional`: cancelar pedido
  - `client|admin`: listar propostas Express
- Observacao importante:
  - Em `user`, `address`, `professional`, `document`, `offering`, `catalog`, `subscription` e `calendar`, varias restricoes `@PreAuthorize` estao comentadas. Na pratica, essas rotas estao apenas como "autenticadas", mesmo quando a documentacao diz "admin only" ou "owner only".

## Mapa de dados

### Relacoes principais

- `users`
  - raiz de autenticacao
  - guarda `role`, `password`, `is_active`, `ban_reason`, `deleted_at`
  - CPF fica criptografado e tambem indexado por `cpf_hash`
- `saved_addresses`
  - `user_id -> users.id`
  - um usuario pode ter varios enderecos
  - o Express depende de `lat` e `lng`
- `professionals`
  - `user_id -> users.id` em relacao 1:1
  - guarda KYC, geo localizacao e assinatura atual
- `professional_documents`
  - `professional_id -> professionals.id`
  - KYC documental
- `service_areas`
  - nivel 1 do catalogo
- `service_categories`
  - `area_id -> service_areas.id`
  - nivel 2 do catalogo
- `professional_services`
  - `professional_id -> professionals.id`
  - `category_id -> service_categories.id`
  - nivel 3 do catalogo
- `subscription_plans`
  - planos disponiveis para profissionais
- `blocked_periods`
  - `professional_id -> professionals.id`
  - bloqueios recorrentes, por data ou por pedido
- `orders`
  - `client_id -> users.id`
  - `professional_id -> professionals.id` quando a proposta e escolhida
  - `area_id -> service_areas.id`
  - `category_id -> service_categories.id`
  - `address_id -> saved_addresses.id`
  - `service_id` existe, mas o fluxo implementado hoje e so Express
- `order_status_history`
  - trilha de auditoria das transicoes
- `order_photos`
  - foto do problema e foto de conclusao
- `express_queue`
  - fila broadcast de profissionais notificados no Express

### Enums aceitos pela API

- `UserRole`: `client`, `professional`, `admin`
- `VerificationStatus`: `pending`, `approved`, `rejected`
- `DocType`: `rg`, `cnh`, `proof_of_address`, `profile_photo`
- `PricingType`: `hourly`, `fixed`
- `BlockType`: `recurring`, `specific_date`, `order`
- `OrderMode`: `express`, `on_demand`
- `OrderStatus`: `pending`, `accepted`, `completed_by_pro`, `completed`, `cancelled`, `disputed`
- `ProResponse`: `accepted`, `rejected`, `timeout`
- `ClientResponse`: `accepted`, `rejected`

## Fluxos de negocio

### 1. Autenticacao e sessao

1. Usuario faz login com e-mail e senha.
2. API valida `deleted_at`, `is_active` e senha.
3. API emite `accessToken` e `refreshToken`.
4. Refresh token antigo e invalidado ao fazer refresh.
5. Reset de senha invalida sessoes ativas removendo o refresh salvo no Redis.

### 2. Ciclo de vida do usuario

1. `POST /api/users` cria usuario com role.
2. `DELETE /api/users/{id}` faz soft delete com janela de 30 dias.
3. `PATCH /api/users/{id}/reactivate` restaura a conta nesse periodo.
4. `PATCH /api/users/{id}/ban` desativa por banimento.
5. `PATCH /api/users/{id}/activate` reativa usuario banido.

### 3. Onboarding do profissional

1. Criar usuario com role `professional`.
2. Criar perfil em `POST /api/v1/professionals`.
3. Enviar documentos em `POST /api/v1/professionals/{professionalId}/documents`.
4. Opcionalmente contratar plano em `PUT /api/v1/professionals/{professionalId}/subscription`.
5. Atualizar geo e ativar Express em `PATCH /api/v1/professionals/{professionalId}/geo`.
6. Admin aprova ou rejeita KYC em `PATCH /api/v1/professionals/{professionalId}/verify`.

### 4. Catalogo e servicos

1. Admin cria area.
2. Admin cria categoria vinculada a area.
3. Profissional cria servicos vinculados a categorias.
4. O pedido Express usa `areaId` e `categoryId`, mas a busca operacional depende da categoria.

### 5. Assinaturas

1. Admin cadastra planos em `/api/v1/subscription-plans`.
2. Profissional aplica um plano em `/api/v1/professionals/{professionalId}/subscription`.
3. Assinatura ativa fica em `professionals.subscription_plan_id` e `subscription_expires_at`.
4. Scheduler de expiracao limpa assinaturas expiradas.

### 6. Pedido Express

1. Cliente cria pedido em `POST /api/v1/orders/express`.
2. API valida categoria ativa e endereco do cliente com coordenadas.
3. API localiza profissionais proximos por categoria, geo ativa e prioridade de assinatura.
4. API cria:
   - `orders`
   - `order_status_history`
   - `order_photos` se `photoUrl` vier
   - `express_queue` para toda a rodada
5. Profissionais respondem em `POST /api/v1/orders/{id}/express/pro-respond`.
6. A primeira proposta aceita abre a janela do cliente.
7. Cliente escolhe uma proposta em `POST /api/v1/orders/{id}/express/client-respond`.
8. API define:
   - `professional_id`
   - `base_amount`
   - `platform_fee`
   - `total_amount`
   - `status=accepted`
9. Profissional conclui em `POST /api/v1/orders/{id}/complete` com foto obrigatoria.
10. Cliente confirma em `POST /api/v1/orders/{id}/confirm`.
11. Scheduler:
   - marca `timeout` em profissionais sem resposta
   - expande raio de busca em novas rodadas
   - cancela pedido sem proposta apos atingir maximo de tentativas
   - cancela pedido se cliente nao escolher proposta dentro da janela

### 7. O que ainda nao esta exposto por rota

- O enum `OrderMode` tem `on_demand`, mas nao existe controller para esse fluxo.
- Existe estrutura para disputa e escrow, mas nao ha endpoints publicados no projeto atual.
- A parte de notificacao, chat, pagamento e integracao externa ainda esta marcada como `TODO`.

## Inventario completo de rotas

### Utilitarios

- `GET /actuator/health`
  - acesso: publico
  - uso: health check
- `GET /v3/api-docs`
  - acesso: publico no perfil `dev`
  - uso: OpenAPI JSON

### Auth

- `POST /api/auth/login`
  - acesso: publico
  - body: `email`, `password`
- `POST /api/auth/refresh`
  - acesso: publico
  - body: `refreshToken`
- `POST /api/auth/logout`
  - acesso: publico
  - body: `refreshToken`
- `POST /api/auth/forgot-password`
  - acesso: publico
  - body: `email`
- `POST /api/auth/reset-password`
  - acesso: publico
  - body: `email`, `code`, `newPassword`

### Users

- `POST /api/users`
  - acesso: publico
  - body: `name`, `cpf`, `email`, `phone`, `password`, `role`
- `GET /api/users?banned=false&deleted=false&page=0&size=20`
  - acesso efetivo: autenticado
  - filtros: `banned`, `deleted`, `page`, `size`, `sort`
- `GET /api/users/{id}`
  - acesso efetivo: autenticado
- `PUT /api/users/{id}`
  - acesso efetivo: autenticado
  - body: `name`, `email`, `phone`, `avatarUrl`
- `DELETE /api/users/{id}`
  - acesso efetivo: autenticado
- `PATCH /api/users/{id}/reactivate`
  - acesso efetivo: autenticado
- `PATCH /api/users/{id}/ban`
  - acesso efetivo: autenticado
  - body: `reason`
- `PATCH /api/users/{id}/activate`
  - acesso efetivo: autenticado

### Saved Addresses

- `POST /api/users/{userId}/addresses`
  - acesso efetivo: autenticado
  - body: `label`, `street`, `number`, `complement`, `district`, `city`, `state`, `zipCode`, `lat`, `lng`, `isDefault`
- `GET /api/users/{userId}/addresses`
  - acesso efetivo: autenticado
- `GET /api/users/{userId}/addresses/{id}`
  - acesso efetivo: autenticado
- `PUT /api/users/{userId}/addresses/{id}`
  - acesso efetivo: autenticado
  - body: mesmos campos da criacao, todos opcionais
- `DELETE /api/users/{userId}/addresses/{id}`
  - acesso efetivo: autenticado
- `PATCH /api/users/{userId}/addresses/{id}/set-default`
  - acesso efetivo: autenticado

### Professionals

- `POST /api/v1/professionals`
  - acesso: publico
  - body: `userId`, `bio`, `yearsOfExperience`, `baseHourlyRate`
- `GET /api/v1/professionals?status=&geoActive=false&page=0&size=20`
  - acesso efetivo: autenticado
  - filtros: `status`, `geoActive`, `page`, `size`, `sort`
- `GET /api/v1/professionals/{id}`
  - acesso efetivo: autenticado
- `PUT /api/v1/professionals/{id}`
  - acesso efetivo: autenticado
  - body: `bio`, `yearsOfExperience`, `baseHourlyRate`
- `PATCH /api/v1/professionals/{id}/geo`
  - acesso efetivo: autenticado
  - body: `geoActive`, `geoLat`, `geoLng`
- `PATCH /api/v1/professionals/{id}/verify`
  - acesso efetivo: autenticado
  - body: `status`, `rejectionReason`
- `DELETE /api/v1/professionals/{id}`
  - acesso efetivo: autenticado

### Professional Documents

- `POST /api/v1/professionals/{professionalId}/documents`
  - acesso efetivo: autenticado
  - body: `docType`, `fileUrl`
- `GET /api/v1/professionals/{professionalId}/documents`
  - acesso efetivo: autenticado
- `DELETE /api/v1/professionals/{professionalId}/documents/{id}`
  - acesso efetivo: autenticado

### Professional Services

- `POST /api/v1/professionals/{professionalId}/services`
  - acesso efetivo: autenticado
  - body: `categoryId`, `title`, `description`, `pricingType`, `price`, `estimatedDurationMinutes`
- `GET /api/v1/professionals/{professionalId}/services?includeInactive=false&page=0&size=20`
  - acesso efetivo: autenticado
- `GET /api/v1/professionals/{professionalId}/services/{id}`
  - acesso efetivo: autenticado
- `PUT /api/v1/professionals/{professionalId}/services/{id}`
  - acesso efetivo: autenticado
  - body: `title`, `description`, `pricingType`, `price`, `estimatedDurationMinutes`, `active`
- `DELETE /api/v1/professionals/{professionalId}/services/{id}`
  - acesso efetivo: autenticado

### Calendar Blocks

- `POST /api/v1/professionals/{professionalId}/calendar/blocks`
  - acesso efetivo: autenticado
  - body:
    - `blockType=recurring`: exige `weekday`
    - `blockType=specific_date`: exige `specificDate`
    - `blockType=order`: exige `orderId`, `orderStartsAt`, `orderEndsAt`
- `GET /api/v1/professionals/{professionalId}/calendar/blocks`
  - acesso efetivo: autenticado
- `DELETE /api/v1/professionals/{professionalId}/calendar/blocks/{id}`
  - acesso efetivo: autenticado

### Service Areas

- `POST /api/v1/service-areas`
  - acesso efetivo: autenticado
  - body: `name`, `iconUrl`
- `GET /api/v1/service-areas?includeInactive=false&page=0&size=20`
  - acesso efetivo: autenticado
- `GET /api/v1/service-areas/{id}`
  - acesso efetivo: autenticado
- `PUT /api/v1/service-areas/{id}`
  - acesso efetivo: autenticado
  - body: `name`, `iconUrl`, `active`
- `DELETE /api/v1/service-areas/{id}`
  - acesso efetivo: autenticado

### Service Categories

- `POST /api/v1/service-categories`
  - acesso efetivo: autenticado
  - body: `areaId`, `name`, `iconUrl`
- `GET /api/v1/service-categories?areaId=&includeInactive=false&page=0&size=20`
  - acesso efetivo: autenticado
- `GET /api/v1/service-categories/{id}`
  - acesso efetivo: autenticado
- `PUT /api/v1/service-categories/{id}`
  - acesso efetivo: autenticado
  - body: `name`, `iconUrl`, `active`
- `DELETE /api/v1/service-categories/{id}`
  - acesso efetivo: autenticado

### Subscription Plans

- `POST /api/v1/subscription-plans`
  - acesso efetivo: autenticado
  - body: `name`, `priceMonthly`, `highlightInSearch`, `expressPriority`, `badgeLabel`, `active`
- `GET /api/v1/subscription-plans?includeInactive=false&page=0&size=20`
  - acesso efetivo: autenticado
- `GET /api/v1/subscription-plans/{id}`
  - acesso efetivo: autenticado
- `PUT /api/v1/subscription-plans/{id}`
  - acesso efetivo: autenticado
  - body: `name`, `priceMonthly`, `highlightInSearch`, `expressPriority`, `badgeLabel`, `active`
- `DELETE /api/v1/subscription-plans/{id}`
  - acesso efetivo: autenticado

### Professional Subscription

- `GET /api/v1/professionals/{professionalId}/subscription`
  - acesso efetivo: autenticado
- `PUT /api/v1/professionals/{professionalId}/subscription`
  - acesso efetivo: autenticado
  - body: `subscriptionPlanId`
- `POST /api/v1/professionals/{professionalId}/subscription/cancel`
  - acesso efetivo: autenticado

### Orders

- `POST /api/v1/orders/express`
  - acesso: `client`
  - body: `areaId`, `categoryId`, `description`, `addressId`, `photoUrl`, `urgencyFee`
- `GET /api/v1/orders/{id}`
  - acesso: autenticado
  - regra: admin, cliente dono, profissional escolhido ou profissional notificado na fila
- `GET /api/v1/orders?status=&page=0&size=20`
  - acesso: autenticado
  - regra: cliente ve pedidos criados; profissional ve pedidos atribuidos
- `GET /api/v1/orders/{id}/express/proposals`
  - acesso: `client|admin`
- `POST /api/v1/orders/{id}/express/pro-respond`
  - acesso: `professional`
  - body: `response`, `proposedAmount`
- `POST /api/v1/orders/{id}/express/client-respond`
  - acesso: `client`
  - body: `selectedProfessionalId`
- `POST /api/v1/orders/{id}/complete`
  - acesso: `professional`
  - body: `photoUrl`
- `POST /api/v1/orders/{id}/confirm`
  - acesso: `client`
- `POST /api/v1/orders/{id}/cancel`
  - acesso declarado: `client|professional`
  - body: `reason`

## Pontos de atencao encontrados

### 1. Restricoes de ownership/admin ainda nao estao valendo

- As anotacoes de autorizacao mais finas estao comentadas em muitos controllers.
- Resultado atual:
  - qualquer usuario autenticado consegue acessar rotas documentadas como "admin only"
  - qualquer usuario autenticado consegue consultar e alterar recursos que deveriam ser do proprio dono

### 2. Cancelamento por profissional no pedido Express esta inconsistente

- No endpoint `POST /api/v1/orders/{id}/cancel`, o service compara o `requesterId` do JWT com `order.professionalId`.
- O JWT carrega `userId`, enquanto `order.professionalId` e o ID do perfil profissional.
- Efeito pratico:
  - cliente consegue cancelar
  - profissional tende a receber `404` mesmo quando e o responsavel pelo pedido

### 3. Cancelamento de assinatura nao persiste estado

- `POST /api/v1/professionals/{professionalId}/subscription/cancel` apenas retorna um DTO.
- O service atual nao grava nenhum flag ou data de cancelamento.
- Na pratica, o endpoint comunica cancelamento, mas nao altera a assinatura.

### 4. `areaId` do pedido Express nao e validado contra a categoria

- `createExpressOrder` valida que a categoria existe e esta ativa.
- Mas nao verifica se `request.areaId` corresponde a `category.areaId`.
- O pedido pode salvar uma area incoerente com a categoria escolhida.

### 5. Documentacao do pedido usa estado que nao existe

- O controller menciona avancar para `IN_PROGRESS`.
- O enum real nao possui esse status.
- O service grava `accepted`.

## Sequencia minima para testar o backend localmente

1. Criar um usuario cliente em `POST /api/users`.
2. Criar um usuario professional em `POST /api/users`.
3. Fazer login com cada um em `POST /api/auth/login`.
4. Criar um perfil profissional em `POST /api/v1/professionals`.
5. Criar area e categoria.
6. Criar endereco do cliente com latitude e longitude.
7. Ativar geo do profissional.
8. Criar pedido Express.
9. Responder como profissional.
10. Escolher proposta como cliente.
11. Concluir como profissional.
12. Confirmar como cliente.
