# Módulo Profissional No Backend

Este documento resume como a parte de profissional está modelada hoje no backend: quais tabelas existem, quais dados cada uma salva, como o cadastro acontece e quais lacunas ainda existem para o front.

## Visão Geral

Hoje o profissional não é uma entidade única isolada. O backend distribui os dados em blocos:

- `users`: conta base do usuário
- `professionals`: perfil profissional estendido
- `professional_specialties`: categorias que o profissional atende com experiência por categoria
- `professional_documents`: documentos e foto de perfil
- `professional_services`: serviços que o profissional oferece
- `blocked_periods`: agenda e bloqueios
- `subscription_plans`: catálogo de planos
- `orders`: pedidos vinculados ao profissional
- `express_queue`: propostas no fluxo Express
- `reviews`: avaliações recebidas e feitas
- `conversations` e `messages`: chat por pedido

## Fluxo Atual De Cadastro

O cadastro do profissional acontece em 2 chamadas:

1. Criar o usuário em `POST /api/users`
2. Criar o perfil profissional em `POST /api/v1/professionals`
3. Após autenticar, enviar documentos em `POST /api/v1/professionals/{professionalId}/documents`

### 1. Usuário Base

Tabela: `users`

Campos relevantes:

- `id`
- `name`
- `cpf`
- `cpf_hash`
- `email`
- `phone`
- `password`
- `role`
- `avatar_url`
- `is_active`
- `notifications_enabled`
- `ban_reason`
- `created_at`
- `updated_at`
- `deleted_at`

Campos obrigatórios no create:

- `name`
- `cpf`
- `email`
- `phone`
- `password`
- `role`

Para profissional, `role` deve ser `professional`.

### 2. Perfil Profissional

Tabela: `professionals`

Campos relevantes:

- `id`
- `user_id`
- `bio`
- `years_of_experience`
- `base_hourly_rate`
- `verification_status`
- `idwall_token`
- `idwall_result`
- `rejection_reason`
- `geo_lat`
- `geo_lng`
- `geo_active`
- `subscription_plan_id`
- `subscription_expires_at`
- `subscription_cancelled_at`
- `created_at`
- `updated_at`
- `deleted_at`

Campos obrigatórios no create hoje:

- `userId`
- `specialties`

Campos opcionais no create hoje:

- `bio`
- `yearsOfExperience`
- `baseHourlyRate`

Regras importantes:

- `specialties` agora é obrigatório no contrato de criação
- cada item de `specialties` precisa ter:
  - `categoryId`
  - `yearsOfExperience`
- `yearsOfExperience` global do perfil pode ser enviado, mas se vier vazio o backend deriva esse valor pelo maior tempo de experiência entre as especialidades

### 3. Documentos Do Cadastro

Tabela: `professional_documents`

No fluxo atual do front, o cadastro profissional também envia:

- `document_front`
- `document_back`

Esses uploads usam o endpoint de documentos depois que o usuário já foi criado, o perfil profissional já existe e o login já foi concluído.

## Tabelas Do Módulo Profissional

### `users`

Responsabilidade:
Conta principal do sistema. Todo profissional começa aqui.

Relação com profissional:

- `users.id` <-> `professionals.user_id`

Observações:

- `cpf` é persistido com conversor de criptografia
- `cpf_hash` existe para busca e unicidade
- `avatar_url` é da conta, não do perfil profissional

### `professionals`

Responsabilidade:
Perfil estendido do profissional.

Dados salvos:

- apresentação (`bio`)
- experiência (`years_of_experience`)
- preço base (`base_hourly_rate`)
- status de verificação (`verification_status`)
- dados de KYC/IDwall
- geolocalização e disponibilidade express
- plano ativo e datas da assinatura
- auditoria e soft delete

Regras implícitas:

- 1 usuário pode ter no máximo 1 perfil profissional
- `verification_status` começa em `pending`
- `geo_active` começa em `false`

### `professional_specialties`

Responsabilidade:
Registrar em quais categorias profissionais o usuário atua e quantos anos de experiência ele tem em cada uma.

Campos:

- `id`
- `professional_id`
- `category_id`
- `years_of_experience`
- `created_at`
- `updated_at`
- `deleted_at`

Regras:

- um profissional pode ter várias especialidades
- uma categoria não pode ser repetida para o mesmo profissional
- a área é inferida pela `service_categories.area_id`

Uso prático:

- suportar seleção de área + profissão no cadastro
- guardar experiência específica por profissão
- alimentar busca, perfil e filtros futuros

### `professional_documents`

Responsabilidade:
Documentos enviados pelo profissional.

Campos:

- `id`
- `professional_id`
- `doc_type`
- `file_url`
- `uploaded_at`
- `verified`

Tipos de documento hoje:

- `rg`
- `cnh`
- `proof_of_address`
- `profile_photo`
- `document_front`
- `document_back`

Uso prático:

- KYC
- comprovação de endereço
- foto de perfil documental
- frente e verso do documento principal do cadastro

Observação:
O backend hoje faz replace por `doc_type`. Se o profissional reenviar `document_front` ou `document_back`, o registro anterior daquele mesmo tipo é substituído.

### `professional_services`

Responsabilidade:
Serviços oferecidos pelo profissional.

Campos:

- `id`
- `professional_id`
- `category_id`
- `title`
- `description`
- `pricing_type`
- `price`
- `estimated_duration_minutes`
- `is_active`
- `created_at`
- `updated_at`
- `deleted_at`

Tipos de preço:

- `hourly`
- `fixed`

Uso prático:

- catálogo próprio do profissional
- base para pedidos `on_demand`

Observação:
Não existe vínculo direto desta tabela com `service_areas`. O vínculo é com `service_categories`.

### `blocked_periods`

Responsabilidade:
Agenda invertida do profissional. Em vez de salvar horários disponíveis, o sistema salva bloqueios.

Campos:

- `id`
- `professional_id`
- `block_type`
- `weekday`
- `specific_date`
- `starts_at`
- `ends_at`
- `order_id`
- `order_starts_at`
- `order_ends_at`
- `reason`
- `created_at`

Tipos de bloqueio:

- `recurring`
- `specific_date`
- `order`

Uso prático:

- bloqueio recorrente semanal
- indisponibilidade em data específica
- reserva automática por pedido aceito

### `subscription_plans`

Responsabilidade:
Catálogo de planos que podem ser atribuídos ao profissional.

Campos:

- `id`
- `name`
- `price_monthly`
- `highlight_in_search`
- `express_priority`
- `badge_label`
- `is_active`
- `created_at`
- `updated_at`
- `deleted_at`

Observação importante:
Não existe uma tabela `professional_subscriptions`.
O vínculo atual do profissional com assinatura fica salvo na própria tabela `professionals`:

- `subscription_plan_id`
- `subscription_expires_at`
- `subscription_cancelled_at`

## Tabelas Operacionais Que Envolvem O Profissional

### `orders`

Responsabilidade:
Pedido principal do sistema.

Campos relacionados ao profissional:

- `professional_id`
- `service_id`
- `area_id`
- `category_id`
- `mode`
- `status`
- `base_amount`
- `platform_fee`
- `total_amount`
- `pro_completed_at`
- `dispute_deadline`
- `completed_at`
- `cancelled_at`
- `cancel_reason`

Uso prático:

- `on_demand`: pedido de um serviço específico do profissional
- `express`: pedido distribuído para profissionais elegíveis

### `express_queue`

Responsabilidade:
Fila de profissionais notificados no fluxo Express.

Campos:

- `id`
- `order_id`
- `professional_id`
- `proposed_amount`
- `notified_at`
- `responded_at`
- `pro_response`
- `client_response`
- `client_responded_at`
- `queue_position`

Uso prático:

- controlar quais profissionais receberam a oferta
- guardar proposta de valor
- registrar aceite, recusa ou timeout
- registrar escolha final do cliente

### `reviews`

Responsabilidade:
Avaliações vinculadas a pedidos.

Campos:

- `id`
- `order_id`
- `reviewer_id`
- `reviewee_id`
- `rating`
- `comment`
- `submitted_at`
- `published_at`

Uso prático:

- média do profissional
- contagem de avaliações
- reputação no app

### `conversations`

Responsabilidade:
Conversa principal por pedido.

Campos:

- `id`
- `order_id`
- `client_id`
- `professional_user_id`
- `created_at`
- `updated_at`
- `deleted_at`

Observação:
O chat usa o `user_id` do profissional, não o `professional.id`.

### `messages`

Responsabilidade:
Mensagens da conversa.

Campos:

- `id`
- `conversation_id`
- `sender_id`
- `msg_type`
- `content`
- `attachment_url`
- `attachment_size_bytes`
- `attachment_mime_type`
- `sent_at`
- `delivered_at`
- `read_at`
- `created_at`
- `updated_at`
- `deleted_at`

## Auditoria E Soft Delete

Várias tabelas estendem `PostgresEntity`, então herdam:

- `id`
- `created_at`
- `updated_at`
- `deleted_at`

Isso vale para:

- `users`
- `professionals`
- `professional_services`
- `subscription_plans`
- `orders`
- `conversations`
- `messages`

Nem todas as tabelas seguem esse padrão. Exemplos:

- `professional_documents` tem `uploaded_at`, mas não usa `deleted_at`
- `blocked_periods` tem `created_at`, mas não usa `updated_at` nem `deleted_at`
- `reviews` tem `submitted_at` e `published_at`

## O Que O Backend Já Consegue Salvar Para Um Profissional

Hoje já existe persistência para:

- conta do usuário
- perfil profissional
- documentos
- serviços oferecidos
- disponibilidade e bloqueios
- plano atual de assinatura
- localização e modo express
- pedidos e propostas
- fotos de conclusão de pedido
- avaliações
- conversas e mensagens

## O Que Ainda Não Está Modelado Como Estrutura Própria

Hoje não existe:

- tabela de áreas atendidas por profissional
- tabela de onboarding do profissional
- histórico de assinatura em tabela própria
- vínculo direto `professional -> service_area`
- endpoint transacional único de cadastro que persiste tudo de uma vez

Na prática, o modelo atual sugere este onboarding:

1. criar conta base
2. criar perfil profissional
3. vincular especialidades por categoria com experiência
4. autenticar
5. enviar documentos
6. cadastrar serviços
7. configurar agenda
8. ativar geolocalização/express
9. opcionalmente contratar plano

## Impacto Para O Front

Se o front quiser ficar 100% alinhado ao backend atual, o cadastro inicial do profissional pode ser dividido em:

- etapa obrigatória mínima:
  - `name`
  - `cpf`
  - `email`
  - `phone`
  - `password`
  - `role=professional`
  - `userId`
  - `specialties[]`

- onboarding posterior:
  - `bio`
  - `baseHourlyRate`
  - `yearsOfExperience` global, se quiser sobrescrever o valor derivado
  - serviços
  - agenda
  - geo
  - assinatura

No estado atual:

- `specialties` já faz parte do contrato obrigatório do backend
- `document_front` e `document_back` já são suportados
- o upload de documentos ainda acontece em uma chamada separada após login

Se a regra de produto exigir atomicidade total no cadastro, o próximo passo seria criar um endpoint composto de registro profissional.
