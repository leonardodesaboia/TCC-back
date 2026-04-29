# Módulo Profissional No Backend

Este documento registra as regras de negócio atuais do fluxo de profissional e como elas estão refletidas no backend.

Legenda:

- `[x]` feito
- `[~]` parcial
- `[ ]` não feito

## Objetivo Do Fluxo

O profissional:

1. cria a conta
2. envia os dados obrigatórios do cadastro profissional
3. fica com status `pending`
4. passa por verificação automatizada futura via IDwall
5. só depois de aprovado pode operar normalmente

Ponto importante:

- [~] a automação com IDwall ainda não foi implementada
- [x] o backend já deixa o fluxo preparado para isso

## Regras De Negócio Confirmadas

### Cadastro Inicial

Campos obrigatórios:

- [x] nome
- [x] cpf
- [x] email
- [x] telefone
- [x] data de nascimento
- [x] senha
- [x] profissões/categorias escolhidas
- [x] experiência por profissão
- [x] tipo de documento de identificação
- [x] documento frente
- [x] documento verso

Campos opcionais:

- [x] descrição profissional
- [x] foto de perfil (via endpoint de avatar do usuário — `PUT /api/v1/users/{id}/avatar` — o frontend integra como passo do onboarding)

### Profissões E Áreas

- [x] o profissional pode selecionar profissões de áreas diferentes
- [x] o limite atual é de `3` profissões
- [x] esse limite deve ser fácil de alterar
- [x] cada profissão precisa ter experiência própria
- [x] não existe mais regra de manter experiência global como fonte principal de verdade

### Valor/Hora

- [x] o valor/hora é tratado por profissão
- [x] ele pode existir por profissão específica
- [x] o campo pode ficar nulo
- [x] um serviço pode usar:
  - [x] preço próprio
  - [x] ou valor/hora da profissão

### Documentos

- [x] o profissional escolhe o tipo do documento de identificação
- [x] hoje os tipos relevantes para esse fluxo são `rg` e `cnh`
- [x] o backend salva frente e verso separadamente
- [x] depois que os documentos são enviados e a verificação fica `pending`, eles não podem ser alterados
- [x] se a verificação for `rejected`, o profissional pode reenviar

### Status E Permissões

- [x] ao concluir o cadastro, o profissional fica `pending`
- [x] enquanto estiver `pending`, ele:
  - [x] não pode editar perfil
  - [x] não pode cadastrar/editar serviços
  - [x] não pode receber pedidos
- [x] se for `rejected`, ele pode corrigir e reenviar documentos
- [x] se for `approved`, ele passa a operar normalmente

### Express

- [x] o profissional só pode receber pedidos Express quando:
  - [x] estiver aprovado
  - [x] ativar disponibilidade
  - [x] compartilhar localização atual

### On Demand

- [x] o cliente escolhe um serviço publicado pelo profissional
- [x] os serviços são cadastrados depois do onboarding inicial
- [x] cada serviço deve ficar vinculado a uma profissão/categoria
- [x] todo serviço precisa ter preço (serviço `fixed` exige preço explícito; `hourly` pode herdar `hourlyRate` da especialidade — `effectivePrice` sempre resolve)

### Chat E Avaliação

- [x] o chat só abre depois que o pedido foi aceito
- [x] cliente e profissional podem se avaliar
- [x] a avaliação só pode acontecer em pedido realmente realizado entre aquelas partes

## Estrutura Atual Do Backend

O profissional não vive em uma tabela única. O modelo está distribuído em blocos:

- `users`
- `professionals`
- `professional_specialties`
- `professional_documents`
- `professional_services`
- `blocked_periods`
- `subscription_plans`
- `orders`
- `express_queue`
- `reviews`
- `conversations`
- `messages`

## Tabelas Principais

### `users`

Responsabilidade:
conta base de autenticação.

Campos relevantes:

- `id`
- `name`
- `cpf`
- `cpf_hash`
- `email`
- `phone`
- `birth_date`
- `password`
- `role`
- `avatar_url`
- `is_active`
- `notifications_enabled`
- `ban_reason`
- `created_at`
- `updated_at`
- `deleted_at`

Observações:

- `birth_date` agora faz parte do contrato de criação
- `role=professional` continua sendo definido aqui
- `avatar_url` existe na conta e pode ser usado futuramente para foto de perfil

### `professionals`

Responsabilidade:
perfil profissional estendido.

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

Observações:

- `verification_status` começa em `pending`
- `geo_active` começa em `false`
- `bio` funciona como descrição profissional opcional
- `years_of_experience` e `base_hourly_rate` continuam existindo no schema por compatibilidade, mas a fonte principal do onboarding agora é `professional_specialties`

### `professional_specialties`

Responsabilidade:
guardar as profissões/categorias escolhidas pelo profissional.

Campos:

- `id`
- `professional_id`
- `category_id`
- `years_of_experience`
- `hourly_rate`
- `created_at`
- `updated_at`
- `deleted_at`

Regras:

- limite atual de `3` especialidades no cadastro
- não pode repetir a mesma categoria para o mesmo profissional
- cada item representa uma profissão/categoria específica
- a área é inferida por `service_categories.area_id`

Uso prático:

- seleção de profissões no cadastro
- experiência por profissão
- valor/hora por profissão
- vínculo futuro dos serviços publicados

### `professional_documents`

Responsabilidade:
documentos do profissional usados no KYC.

Campos:

- `id`
- `professional_id`
- `doc_type`
- `doc_side`
- `file_url`
- `uploaded_at`
- `verified`

Tipos relevantes para o fluxo atual:

- `rg`
- `cnh`

Lados:

- `front`
- `back`

Regras atuais:

- o upload é feito por `doc_type + doc_side`
- existe unicidade por profissional + tipo + lado
- se já existir documento daquele tipo/lado:
  - com profissional `rejected`, pode substituir
  - com profissional `pending` ou `approved`, não pode alterar
- o backend bloqueia tanto substituição quanto exclusão de documentos enquanto o profissional não estiver `rejected`

### `professional_services`

Responsabilidade:
serviços publicados pelo profissional depois do cadastro.

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

Regras de produto:

- serviço vem depois do cadastro
- serviço deve ficar vinculado a uma profissão/categoria
- serviço `fixed` precisa de preço
- serviço `hourly` pode usar preço próprio ou herdar o valor/hora da especialidade

### `blocked_periods`

Responsabilidade:
agenda do profissional.

Observação:

- agenda continua sendo configurada depois do cadastro
- o profissional pode existir aprovado sem agenda configurada

## Fluxo Técnico Atual

Hoje o cadastro profissional é composto por chamadas separadas:

1. `POST /api/users`
2. `POST /api/v1/professionals`
3. autenticação
4. `POST /api/v1/professionals/{professionalId}/documents`

### `POST /api/users`

Cria a conta base com:

- `name`
- `cpf`
- `email`
- `phone`
- `birthDate`
- `password`
- `role`

### `POST /api/v1/professionals`

Cria o perfil profissional com:

- `userId`
- `bio` opcional
- `specialties[]`

Cada item de `specialties[]` possui:

- `categoryId`
- `yearsOfExperience`
- `hourlyRate` opcional

### `POST /api/v1/professionals/{professionalId}/documents`

Envia o documento com:

- `docType`
- `docSide`
- `file`

## O Que Já Está Implementado

### Feito

- [x] `birth_date` em `users`
- [x] `professional_specialties` com experiência por categoria
- [x] `hourly_rate` por profissão
- [x] `doc_side` em `professional_documents`
- [x] bloqueio de alteração e exclusão de documento quando o profissional não está `rejected`
- [x] data de nascimento no cadastro profissional
- [x] seleção de até 3 profissões
- [x] experiência por profissão
- [x] tipo de documento
- [x] upload de frente e verso

### Parcial

- [~] automação real de verificação via IDwall
- [x] travas completas de edição/operação enquanto `pending`
- [x] foto de perfil opcional dentro do cadastro (endpoint de avatar já existente, integrado pelo frontend no onboarding)
- [x] reaproveitar a mesma regra de `birthDate` também no fluxo de cliente de ponta a ponta
- [x] validações funcionais completas no fluxo de aprovação/rejeição (verifica documentos >= 2 e pelo menos 1 especialidade)
- [x] serviços vinculados explicitamente à profissão escolhida no onboarding

### Não Feito

- [ ] integração efetiva com um provedor de verificação em tempo real
- [x] regra centralizada de bloqueio por status para perfil, serviços e pedidos (`requireApproved()` em `ProfessionalServiceImpl` e `ProfessionalOfferingServiceImpl`; validação de `approved` em `OrderServiceImpl`)
- [x] onboarding com foto de perfil opcional (backend pronto via `PUT /api/v1/users/{id}/avatar`, orquestrado pelo frontend)
- [x] fluxo completo de pedido On Demand a partir de um serviço publicado (`POST /on-demand`, `POST /{id}/on-demand/respond`)

## Fonte De Verdade Atual

Para o cadastro profissional, a referência de negócio agora é:

- `users.birth_date`
- `professionals.verification_status`
- `professional_specialties`
- `professional_documents` com `doc_type + doc_side`

O campo global de experiência do perfil não deve ser tratado como a modelagem principal do produto. A modelagem principal é por profissão.

## Observações Importantes

- o backend impõe `approved` para editar perfil, cadastrar/editar serviços e operar no Express e On Demand
- as permissões por dono/admin nas rotas HTTP ainda não estão completamente fechadas
- a aprovação valida que existem ao menos 2 documentos e pelo menos 1 especialidade antes de aprovar
- a foto de perfil é gerenciada pelo endpoint de avatar do usuário (`PUT /api/v1/users/{id}/avatar`); o frontend orquestra como passo do onboarding
