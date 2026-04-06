# All Set API

Backend da plataforma **All Set** — marketplace de serviços autônomos.
API REST construída com Spring Boot 3.5, Java 21 e PostgreSQL.

---

## Stack

| Camada | Tecnologia |
|---|---|
| Runtime | Java 21 (Virtual Threads) |
| Framework | Spring Boot 3.5.11 |
| Build | Maven 3.9 |
| Banco de dados | PostgreSQL 16 |
| Cache | Redis 7 |
| Migrations | Flyway |
| Auth | JWT (HMAC-SHA256) via OAuth2 Resource Server |
| Docs | SpringDoc OpenAPI / Swagger UI |
| Containerização | Docker + Docker Compose |

---

## Pré-requisitos

- Java 21
- Docker e Docker Compose
- Maven 3.9+ (ou usar `./mvnw`)

---

## Rodando localmente

**1. Copie o arquivo de variáveis de ambiente:**

```bash
cp .env.example .env
```

Preencha os valores em `.env` antes de continuar.

**2. Suba os serviços de infra:**

```bash
docker compose up -d postgres redis
```

**3. Rode a aplicação:**

```bash
./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`.
Swagger UI disponível em `http://localhost:8080/swagger-ui.html` (apenas no perfil `dev`).

**Rodando tudo via Docker Compose:**

```bash
docker compose up --build
```

---

## Variáveis de ambiente

| Variável | Obrigatória | Padrão | Descrição |
|---|---|---|---|
| `JWT_SECRET` | Sim | — | Segredo HMAC-SHA256 para assinar JWTs |
| `CPF_ENCRYPTION_KEY` | Sim | — | Chave AES-256 — 64 caracteres hex (32 bytes) |
| `DATABASE_URL` | Sim | — | URL JDBC do PostgreSQL (ex: `jdbc:postgresql://localhost:5432/allset`) |
| `DB_USER` | Sim | — | Usuário do banco |
| `DB_PASS` | Sim | — | Senha do banco |
| `RESEND_API_KEY` | Sim | — | API key do Resend para envio de e-mails |
| `EMAIL_FROM` | Sim | — | Endereço de origem dos e-mails (ex: `noreply@allset.com.br`) |
| `REDIS_HOST` | Não | `localhost` | Host do Redis |
| `REDIS_PORT` | Não | `6379` | Porta do Redis |
| `PORT` | Não | `8080` | Porta da aplicação |
| `SPRING_PROFILES_ACTIVE` | Não | `dev` | Perfil ativo: `dev` ou `prod` |
| `USER_PURGE_CRON` | Não | `0 0 2 * * *` | Cron do job de purga de usuários deletados |
| `SUBSCRIPTION_EXPIRATION_CRON` | Não | `0 */30 * * * *` | Cron do job de expiração de assinaturas |
| `ACCESS_TOKEN_TTL_MINUTES` | Não | `15` | TTL do access token (minutos) |
| `REFRESH_TOKEN_TTL_DAYS` | Não | `7` | TTL do refresh token (dias) |
| `RESET_CODE_TTL_MINUTES` | Não | `10` | TTL do código de recuperação de senha (minutos) |
| `EXPRESS_PRO_TIMEOUT_MINUTES` | Não | `10` | Prazo para profissional responder no Express (minutos) |
| `EXPRESS_CLIENT_WINDOW_MINUTES` | Não | `30` | Janela para cliente escolher proposta após a primeira recebida (minutos) |
| `EXPRESS_SEARCH_RADIUS_KM` | Não | `15` | Raio inicial de busca de profissionais no Express (km) |
| `EXPRESS_MAX_QUEUE_SIZE` | Não | `10` | Máximo de profissionais notificados por rodada |
| `EXPRESS_MAX_SEARCH_ATTEMPTS` | Não | `3` | Máximo de expansões de raio antes de cancelar |
| `EXPRESS_MAX_RADIUS_KM` | Não | `50` | Raio máximo de busca após expansões (km) |

---

## Estrutura do projeto

```
src/main/java/com/allset/api/
├── AllsetApiApplication.java
├── boilerplate/
│   └── domain/PostgresEntity.java        # Base entity: UUID, auditoria, soft delete
├── config/
│   ├── AppProperties.java                # Configurações tipadas via @ConfigurationProperties
│   ├── JpaConfig.java
│   ├── SecurityConfig.java               # JWT, BCrypt, CORS, regras de acesso
│   ├── OpenApiConfig.java
│   └── StartupLogger.java
├── shared/
│   ├── annotation/CurrentUser.java       # @CurrentUser — extrai UUID do JWT sub
│   ├── crypto/CpfConverter.java          # Conversor JPA AES-256/CBC para CPF
│   ├── validation/ValidCPF.java + CpfValidator.java
│   └── exception/
│       ├── ApiError.java
│       └── GlobalExceptionHandler.java
├── auth/                                 # Login, JWT, refresh token, recuperação de senha
├── user/                                 # Usuários (client, professional, admin)
│   └── scheduler/UserPurgeScheduler.java
├── address/                              # Endereços salvos por usuário
├── professional/                         # Perfil profissional, KYC, geolocalização
├── document/                             # Documentos do profissional (S3 + IDwall)
├── offering/                             # Serviços oferecidos pelo profissional
├── catalog/                              # Áreas e categorias de serviço (admin)
├── subscription/                         # Planos de assinatura para profissionais
│   └── scheduler/SubscriptionExpirationScheduler.java
├── calendar/                             # Calendário de disponibilidade do profissional
└── order/                                # Pedidos Express — ciclo completo
    ├── controller/OrderController.java
    ├── service/OrderServiceImpl.java
    ├── repository/
    │   ├── OrderRepository.java
    │   ├── ExpressQueueRepository.java   # Haversine, bulk-reject, timeout queries
    │   ├── OrderStatusHistoryRepository.java
    │   └── OrderPhotoRepository.java
    ├── domain/                           # Order, ExpressQueueEntry, OrderStatusHistory, OrderPhoto + enums
    ├── mapper/OrderMapper.java
    ├── dto/                              # CreateExpressOrderRequest, ProRespondRequest,
    │                                     # ClientRespondRequest, OrderResponse, ExpressProposalResponse, ...
    ├── exception/                        # OrderNotFoundException, OrderStatusTransitionException,
    │                                     # ExpressQueueViolationException, NoProfessionalsAvailableException
    └── scheduler/ExpressTimeoutScheduler.java

src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__init.sql
    ├── V2__create_users.sql
    ├── V3__create_saved_addresses.sql
    ├── V4__alter_saved_addresses_state_to_varchar.sql
    ├── V5__create_subscription_plans.sql
    ├── V6__create_service_areas.sql
    ├── V7__create_service_categories.sql
    ├── V8__create_professionals.sql
    ├── V9__create_professional_documents.sql
    ├── V10__create_professional_services.sql
    ├── V11__create_blocked_periods.sql
    └── V12__create_orders.sql
```

---

## Módulos implementados

### Infraestrutura base

#### Base Entity — `PostgresEntity`

Todas as entidades do projeto estendem `PostgresEntity`, que fornece os campos de infraestrutura comuns:

| Campo | Tipo | Comportamento |
|---|---|---|
| `id` | `UUID` | Gerado automaticamente (`gen_random_uuid()`) |
| `createdAt` | `Instant` | Preenchido automaticamente por `@CreatedDate`, imutável |
| `updatedAt` | `Instant` | Atualizado automaticamente por `@LastModifiedDate` |
| `deletedAt` | `Instant` | `null` = ativo; preenchido = soft delete |

A auditoria (`createdAt`/`updatedAt`) é gerenciada pelo `AuditingEntityListener` do Spring Data, habilitado via `@EnableJpaAuditing` em `JpaConfig`.

---

#### Configurações tipadas — `AppProperties`

`AppProperties` é um record anotado com `@ConfigurationProperties` que centraliza e valida todas as variáveis de ambiente na inicialização.

Se uma variável obrigatória estiver ausente ou inválida, a aplicação falha rapidamente com mensagem clara — sem erros em runtime.

Campos validados:
- `jwtSecret`: não pode ser vazio
- `cpfEncryptionKey`: exatamente 64 caracteres hex (32 bytes para AES-256)
- `databaseUrl`, `dbUser`, `dbPass`: obrigatórios
- `redisHost`, `redisPort`, `port`: com valores padrão
- `userPurgeCron`: expressão cron válida (padrão: `0 0 2 * * *`)

---

#### Segurança — JWT com HMAC-SHA256

Configurado em `SecurityConfig`:

- **Algoritmo:** HS256 (HMAC-SHA256)
- **Decodificador:** `NimbusJwtDecoder.withSecretKey()` — valida assinatura, expiração e claims automaticamente
- **Codificador:** `NimbusJwtEncoder` com `ImmutableSecret` — usado para gerar tokens no módulo de auth (a implementar)
- **Extração de roles:** `JwtAuthenticationConverter` com conversor customizado que lê a claim `"role"` do token e cria `GrantedAuthority` **sem prefixo** — permite usar `hasAuthority('admin')` diretamente nas anotações
- **Sub:** A claim `sub` do JWT deve conter o UUID do usuário — usado nas regras de acesso self (`#id.toString() == authentication.name`)
- **Senha:** `BCryptPasswordEncoder` (fator 10)
- **Sessão:** `STATELESS` — sem cookies de sessão, sem CSRF

---

#### OpenAPI / Swagger

Configurado em `OpenApiConfig` com o schema de segurança `bearerAuth` (JWT Bearer):

- Swagger UI: `http://localhost:8080/swagger-ui.html` (desabilitado em `prod`)
- Todos os endpoints têm `@SecurityRequirement(name = "bearerAuth")` para aparecerem como protegidos no Swagger

---

#### Tratamento de erros — `GlobalExceptionHandler`

`@RestControllerAdvice` centralizado que captura todas as exceções e retorna um `ApiError` padronizado:

```json
{
  "status": 404,
  "message": "Usuário não encontrado: 3fa85f64-...",
  "fields": {},
  "timestamp": "2026-03-25T14:00:00Z"
}
```

Mapeamentos:

| Exceção | Status HTTP |
|---|---|
| `MethodArgumentNotValidException` | 400 — com mapa de campos inválidos |
| `IllegalArgumentException` | 400 |
| `InvalidResetCodeException` | 400 |
| `InvalidCredentialsException` / `InvalidTokenException` | 401 |
| `AccessDeniedException` / `UserBannedException` | 403 |
| `*NotFoundException` (todas) | 404 |
| `EmailAlreadyExistsException` / `CpfAlreadyExistsException` / `ProfessionalAlreadyExistsException` / `ServiceAreaNameAlreadyExistsException` / `SubscriptionPlan*AlreadyExists*` | 409 |
| `UserPendingDeletionException` | 423 (Locked) — com `scheduledDeletionAt` |
| `OrderStatusTransitionException` / `ExpressQueueViolationException` | 400 |
| `NoProfessionalsAvailableException` | 422 |
| `Exception` (catch-all) | 500 |

---

### Módulo de Usuários

#### Entidade `User`

Estende `PostgresEntity`. Campos específicos:

| Campo | Tipo / Restrição | Detalhe |
|---|---|---|
| `name` | `VARCHAR(150)` NOT NULL | Nome do usuário |
| `cpf` | `VARCHAR(255)` UNIQUE | Criptografado com AES-256/CBC via `CpfConverter` |
| `cpfHash` | `VARCHAR(64)` UNIQUE | SHA-256 do CPF em texto puro — usado para queries de unicidade |
| `email` | `VARCHAR(150)` UNIQUE NOT NULL | |
| `phone` | `VARCHAR(20)` NOT NULL | Formato E.164 |
| `password` | `VARCHAR(255)` NOT NULL | Hash BCrypt |
| `role` | `user_role` (enum PG) | `client`, `professional`, `admin` |
| `avatarUrl` | `TEXT` nullable | |
| `active` | `BOOLEAN` DEFAULT TRUE | `false` = conta banida |
| `banReason` | `TEXT` nullable | Preenchido ao banir |

---

#### Criptografia do CPF — `CpfConverter`

Conversor JPA transparente (`AttributeConverter`) que criptografa o CPF ao persistir e descriptografa ao carregar.

- **Algoritmo:** AES-256/CBC com padding PKCS5
- **IV:** 16 bytes aleatórios gerados a cada criptografia — garante que o mesmo CPF produz ciphertexts diferentes em cada escrita
- **Formato no banco:** Base64(IV[16 bytes] || ciphertext)
- **Chave:** Lida de `AppProperties.cpfEncryptionKey` (64 chars hex → 32 bytes)
- **Queries de unicidade:** Usam a coluna `cpf_hash` (SHA-256 do CPF cru), pois o campo `cpf` criptografado não pode ser consultado diretamente

---

#### Validação de CPF — `@ValidCPF`

Anotação customizada Bean Validation (`@ValidCPF`) com implementação em `CpfValidator`:

1. Remove caracteres não-numéricos (aceita `123.456.789-09` e `12345678909`)
2. Rejeita se não tiver exatamente 11 dígitos
3. Rejeita se todos os dígitos forem iguais (`11111111111`, etc.)
4. Valida o primeiro dígito verificador (módulo 11 com pesos 10..2)
5. Valida o segundo dígito verificador (módulo 11 com pesos 11..2)

---

#### Endpoints — `UserController` (`/api/users`)

| Método | Caminho | Descrição | Acesso |
|---|---|---|---|
| `POST` | `/api/users` | Criar usuário | Admin |
| `GET` | `/api/users` | Listar usuários | Admin |
| `GET` | `/api/users/{id}` | Buscar por ID | Admin ou o próprio usuário |
| `PUT` | `/api/users/{id}` | Atualizar dados | Admin ou o próprio usuário |
| `DELETE` | `/api/users/{id}` | Soft delete (grace period 30 dias) | Admin ou o próprio usuário |
| `PATCH` | `/api/users/{id}/reactivate` | Reativar conta no período de carência | Admin ou o próprio usuário |
| `PATCH` | `/api/users/{id}/ban` | Banir usuário | Admin |
| `PATCH` | `/api/users/{id}/activate` | Desbanir usuário | Admin |

**Query params de listagem (`GET /api/users`):**
- `?deleted=true` — lista contas em período de exclusão
- `?banned=true` — lista contas banidas
- Padrão: lista contas ativas (não deletadas, não banidas)
- Paginação com tamanho padrão de 20 registros

**Controle de acesso self:**
`hasAuthority('admin') or #id.toString() == authentication.name`
O claim `sub` do JWT deve ser o UUID do usuário.

---

#### Regras de negócio — `UserServiceImpl`

**Criação de usuário:**
- Verifica duplicidade de e-mail e CPF apenas em contas ativas
- Se o e-mail ou CPF existir em uma conta **soft-deleted**, lança `UserPendingDeletionException` (HTTP 423) com a data limite para reativação — impede criar uma conta nova enquanto a antiga ainda está no período de carência
- Computa `cpfHash = SHA-256(cpf)` antes de salvar
- Criptografa a senha com BCrypt
- O CPF é criptografado automaticamente pelo `CpfConverter` ao persistir

**Soft delete e grace period de 30 dias:**
- `DELETE /api/users/{id}` define `deletedAt = now()` sem remover o registro
- A resposta inclui `scheduledDeletionAt = deletedAt + 30 dias`
- Durante esse período a conta não aparece nas listagens normais
- O usuário (ou admin) pode cancelar a exclusão via `PATCH /reactivate` (define `deletedAt = null`)

**Banimento:**
- `PATCH /ban`: define `active = false` + `banReason`
- `PATCH /activate`: define `active = true` + limpa `banReason`
- Usuário banido continua existindo — apenas não consegue se autenticar (a verificar no módulo de auth)

**Atualização parcial:**
- Apenas campos não-nulos no corpo da requisição são atualizados
- Mudança de e-mail verifica unicidade antes de aplicar
- CPF e senha não podem ser alterados via `PUT /api/users/{id}`

---

#### Scheduler de purga — `UserPurgeScheduler`

Job agendado via `@Scheduled(cron = "${user-purge-cron}")` (padrão: todo dia às 2h):

1. Busca todos os usuários com `deletedAt != null AND deletedAt < (now - 30 dias)`
2. Remove fisicamente do banco (`deleteAll`)
3. Loga `event=user_purge count={n} cutoff={Instant}`

O cron é configurável via variável de ambiente `USER_PURGE_CRON`.

---

### Módulo de Endereços Salvos

#### Entidade `SavedAddress`

Estende `PostgresEntity`. Campos:

| Campo | Tipo / Restrição | Detalhe |
|---|---|---|
| `userId` | `UUID` NOT NULL | FK para `users.id` (ON DELETE CASCADE), imutável após criação |
| `label` | `VARCHAR(60)` nullable | Ex: "Casa", "Trabalho" |
| `street` | `VARCHAR(200)` NOT NULL | Logradouro |
| `number` | `VARCHAR(20)` nullable | Número |
| `complement` | `VARCHAR(80)` nullable | Ex: "Apto 3B" |
| `district` | `VARCHAR(80)` nullable | Bairro |
| `city` | `VARCHAR(80)` NOT NULL | Cidade |
| `state` | `VARCHAR(2)` NOT NULL | Sigla do estado (ex: "CE") |
| `zipCode` | `VARCHAR(9)` NOT NULL | CEP no formato `99999-999` ou `99999999` |
| `lat` | `NUMERIC(9,6)` nullable | Latitude |
| `lng` | `NUMERIC(9,6)` nullable | Longitude |
| `isDefault` | `BOOLEAN` DEFAULT FALSE | Invariante: apenas um endereço default por usuário |

Dois índices no banco: `idx_saved_addresses_user_id` e `idx_saved_addresses_user_default(user_id, is_default)`.

---

#### Endpoints — `SavedAddressController` (`/api/users/{userId}/addresses`)

| Método | Caminho | Descrição | Acesso | Status |
|---|---|---|---|---|
| `POST` | `/api/users/{userId}/addresses` | Criar endereço | Admin ou o próprio usuário | 201 |
| `GET` | `/api/users/{userId}/addresses` | Listar endereços | Admin ou o próprio usuário | 200 |
| `GET` | `/api/users/{userId}/addresses/{id}` | Buscar por ID | Admin ou o próprio usuário | 200 |
| `PUT` | `/api/users/{userId}/addresses/{id}` | Atualizar endereço | Admin ou o próprio usuário | 200 |
| `DELETE` | `/api/users/{userId}/addresses/{id}` | Deletar endereço | Admin ou o próprio usuário | 204 |
| `PATCH` | `/api/users/{userId}/addresses/{id}/set-default` | Definir como padrão | Admin ou o próprio usuário | 200 |

---

#### Regras de negócio — `SavedAddressServiceImpl`

**Verificação de ownership:**
`findOwnedAddress(userId, id)` usa `findByIdAndUserId` — uma query só que verifica ID e pertencimento simultaneamente. Retorna o mesmo 404 para "não encontrado" e "pertence a outro usuário", evitando enumeration.

**Endereço default:**
Invariante: no máximo um endereço `isDefault = true` por usuário. Ao definir um endereço como default (via `set-default` ou ao criar/atualizar com `isDefault = true`), uma query `UPDATE` em lote seta `is_default = false` para todos os endereços do usuário antes de ativar o novo.

**Validação de usuário:**
Criação e listagem verificam se o `userId` existe antes de operar — lança `UserNotFoundException` (404) se não existir.

**Deleção:**
Física — não usa soft delete para endereços.

---

---

### Módulo de Pedidos Express

#### Fluxo Express

```
[Cliente cria pedido]
  → Busca profissionais aprovados no raio de 15km (Haversine)
  → Todos notificados simultaneamente (broadcast)

[Cada profissional]
  → Aceita (informa preço) ou recusa dentro de 10min
  → Timeout sem resposta = marcado como 'timeout'

[Primeira proposta recebida]
  → Janela de 30min aberta para o cliente escolher

[Cliente escolhe uma proposta]
  → As demais propostas são recusadas automaticamente (bulk UPDATE)
  → Order status → accepted
  → Profissional atribuído, valores calculados

[Sem propostas após timeout]
  → Expande raio (15km → até 50km, máx. 3 tentativas por interpolação linear)
  → Se nenhum profissional disponível após todas as tentativas → cancelled

[Cliente não escolhe dentro de 30min]
  → Order status → cancelled automaticamente

[accepted → profissional executa serviço]
  → Profissional envia foto comprobatória → completed_by_pro
  → Cliente confirma → completed (escrow liberado, -20% fee ao profissional)
  → Cliente pode abrir disputa em até 24h após completed_by_pro
```

#### Entidade `Order`

| Campo | Detalhe |
|---|---|
| `version` | `@Version` — optimistic locking para transições de status concorrentes |
| `areaId` | Área de serviço escolhida pelo cliente (ex: Elétrica) |
| `categoryId` | Categoria específica (ex: Eletricista) — define quais profissionais são buscados |
| `addressSnapshot` | JSONB — cópia imutável do endereço no momento do pedido |
| `expiresAt` | Fase de propostas: criação + 10min; Fase de escolha: 1ª proposta + 30min |
| `searchRadiusKm` | Raio atual em km — cresce a cada tentativa sem propostas |
| `searchAttempts` | Contador de rodadas (máximo configurável via `EXPRESS_MAX_SEARCH_ATTEMPTS`) |
| `baseAmount` | Valor proposto pelo profissional escolhido |
| `platformFee` | 20% de `baseAmount` — descontado do repasse ao profissional (não cobrado do cliente) |
| `totalAmount` | `baseAmount + urgencyFee` — o que o cliente paga |

#### Entidade `ExpressQueueEntry`

Cada linha representa um profissional notificado para um pedido. Todos os da mesma rodada recebem `notifiedAt = NOW()` (broadcast simultâneo). O campo `queue_position` é usado apenas para desempate na exibição.

| `proResponse` | Significado |
|---|---|
| `null` | Ainda não respondeu |
| `accepted` | Enviou proposta com preço |
| `rejected` | Recusou explicitamente |
| `timeout` | Não respondeu no prazo — marcado pelo scheduler |

#### Endpoints — `OrderController` (`/api/v1/orders`)

| Método | Caminho | Descrição | Acesso |
|---|---|---|---|
| `POST` | `/express` | Criar pedido Express | Client |
| `GET` | `/{id}` | Buscar pedido por ID | Client dono, professional atribuído/na fila, Admin |
| `GET` | `/` | Listar meus pedidos (filtro por status) | Client, Professional |
| `GET` | `/{id}/express/proposals` | Ver propostas recebidas | Client dono, Admin |
| `POST` | `/{id}/express/pro-respond` | Profissional aceita (com preço) ou recusa | Professional |
| `POST` | `/{id}/express/client-respond` | Cliente escolhe proposta pelo ID do profissional | Client |
| `POST` | `/{id}/complete` | Profissional marca como concluído + foto | Professional |
| `POST` | `/{id}/confirm` | Cliente confirma conclusão | Client |
| `POST` | `/{id}/cancel` | Cancelar pedido | Client, Professional |

#### Scheduler — `ExpressTimeoutScheduler`

Executa a cada 60 segundos via `processExpiredWindows()`:
1. Busca entries sem resposta com `notifiedAt ≤ now - 10min` → marca como `timeout`
2. Para pedidos afetados sem propostas e sem pendentes: chama `expandSearchOrCancel`
3. Busca pedidos `pending + express + expiresAt < now`: cancela os com propostas (cliente não escolheu), expande os sem propostas

---

## Banco de dados (Flyway)

| Migration | Descrição |
|---|---|
| `V1__init.sql` | Placeholder vazio |
| `V2__create_users.sql` | Tipo `user_role` (enum PG) e tabela `users` |
| `V3__create_saved_addresses.sql` | Tabela `saved_addresses` com índices |
| `V4__alter_saved_addresses_state_to_varchar.sql` | Altera `state` de `CHAR(2)` para `VARCHAR(2)` — compatibilidade com Hibernate |
| `V5__create_subscription_plans.sql` | Tabela `subscription_plans` |
| `V6__create_service_areas.sql` | Tabela `service_areas` |
| `V7__create_service_categories.sql` | Tabela `service_categories` |
| `V8__create_professionals.sql` | Tabela `professionals` com campos de geo e subscription |
| `V9__create_professional_documents.sql` | Tabela `professional_documents` |
| `V10__create_professional_services.sql` | Tabela `professional_services` |
| `V11__create_blocked_periods.sql` | Tabela `blocked_periods` (calendário) |
| `V12__create_orders.sql` | Enums e tabelas: `orders`, `order_status_history`, `order_photos`, `express_queue` |

---

## Docker

### `Dockerfile` (multi-stage)

- **Stage 1 (build):** `maven:3.9-eclipse-temurin-21` — compila o JAR com dependências em cache
- **Stage 2 (runtime):** `eclipse-temurin:21-jre-alpine` — imagem mínima com só o JRE
- Perfil `prod` ativado automaticamente no container

### `docker-compose.yml`

| Serviço | Imagem | Porta | Healthcheck |
|---|---|---|---|
| `postgres` | `postgres:16-alpine` | `5432` | `pg_isready` |
| `redis` | `redis:7-alpine` | `6379` | `redis-cli ping` |
| `app` | build local | `${PORT:-8080}` | depende de postgres e redis healthy |

---

## Dependências adicionadas manualmente

Dependências fora do Spring Initializr incluídas no `pom.xml`:

| Dependência | Versão | Finalidade |
|---|---|---|
| `spring-dotenv` | 4.0.0 | Carrega automaticamente o arquivo `.env` como propriedades Spring |
| `springdoc-openapi-starter-webmvc-ui` | 2.8.3 | Swagger UI e geração do contrato OpenAPI |

---

## Perfis de execução

| Perfil | Ativação | Diferenças |
|---|---|---|
| `dev` | padrão | `show-sql: true`, Swagger UI ativo |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | `show-sql: false`, Swagger UI desabilitado |

---


Diagrama do Banco de dados: 

// ============================================================
// ALL SET — Database Schema (DBML)
// Marketplace de Serviços sob Demanda com Escrow
// v4.0 — Produto Final
//
// FLUXO ON DEMAND:
//   pending → paid → accepted → completed_by_pro → completed
//                ↘ cancelled (profissional recusou / timeout / cliente cancelou)
//                                                 ↘ disputed (24h após completed_by_pro)
//
// FLUXO EXPRESS:
//   pending → [profissionais enviam propostas] → accepted (cliente escolhe) → completed_by_pro → completed
//          ↘ cancelled (sem propostas após expansão máxima de raio / cliente não escolheu no prazo)
//                                                                            ↘ disputed (24h após completed_by_pro)
//
// ESCROW:
//   Cliente paga ao criar pedido (on_demand) ou ao aceitar proposta (express)
//   Valor fica retido até completed → liberado ao profissional (- 20% fee)
//   Cancelamento antes de accepted → reembolso automático imediato
// ============================================================

// ─────────────────────────────────────────
// ENUMS
// ─────────────────────────────────────────

Enum user_role {
  client
  professional
  admin
}

Enum verification_status {
  pending
  approved
  rejected
}

Enum pricing_type {
  hourly
  fixed
}

Enum order_mode {
  express
  on_demand
}

Enum order_status {
  pending          // on_demand: criado, aguardando pagamento | express: criado, aguardando propostas
  paid             // on_demand apenas: pagamento confirmado, aguardando aceite do profissional
  accepted         // profissional aceitou — chat liberado para ambos
  completed_by_pro // profissional marcou como concluído + enviou foto comprobatória
  completed        // cliente confirmou conclusão — escrow liberado ao profissional
  cancelled        // profissional recusou | timeout (4h antes do scheduled_at) | cliente cancelou
  disputed         // cliente abriu disputa em até 24h após completed_by_pro
}

Enum transaction_type {
  charge           // cobrança ao cliente (escrow hold)
  escrow_hold      // valor retido aguardando conclusão
  release          // liberação ao profissional após completed (- platform_fee)
  refund_full      // reembolso total ao cliente (cancelamento ou disputa)
  refund_partial   // reembolso parcial ao cliente (disputa com resolução parcial)
  platform_fee     // registro da taxa da plataforma (20%)
}

Enum transaction_status {
  pending
  confirmed
  failed
  refunded
}

Enum dispute_status {
  open
  under_review
  resolved
}

Enum dispute_resolution {
  refund_full      // 100% devolvido ao cliente
  refund_partial   // split entre cliente e profissional definido pelo admin
  release_to_pro   // 100% liberado ao profissional
}

Enum payment_method_type {
  pix
  credit_card
  debit_card
}

Enum account_type {
  bank_account
  pix_key
}

Enum msg_type {
  text
  image
}

Enum photo_type {
  request           // foto opcional do problema enviada pelo cliente ao criar pedido
  completion_proof  // foto obrigatória enviada pelo profissional ao marcar como concluído
}

Enum pro_response {
  accepted  // profissional aceitou e propôs preço — proposta visível ao cliente
  rejected  // profissional recusou explicitamente
  timeout   // não respondeu no prazo — marcado automaticamente pelo scheduler
}

Enum client_response {
  accepted  // cliente escolheu esta proposta — as demais são automaticamente rejected
  rejected  // descartada ao cliente escolher outra proposta
}

Enum notification_type {
  new_request
  request_accepted
  request_rejected
  request_status_update
  new_message
  payment_released
  dispute_opened
  dispute_resolved
  verification_result
}

Enum platform {
  android
  ios
}

// criminal_record removido — antecedentes criminais verificados automaticamente via IDwall (idwall_result)
Enum doc_type {
  rg
  cnh
  proof_of_address
  profile_photo
}

Enum evidence_type {
  text
  photo
}

Enum block_type {
  recurring      // bloqueia um dia da semana (ex: todo domingo)
  specific_date  // bloqueia uma data específica (ex: 26/03)
  order          // bloqueado automaticamente ao aceitar pedido on_demand
}

// ─────────────────────────────────────────
// USUÁRIOS
// ─────────────────────────────────────────

Table users {
  id                    uuid         [pk, default: `gen_random_uuid()`]
  name                  varchar(150) [not null]
  cpf                   varchar(255) [not null, unique, note: 'criptografado via AES-256/CBC — nunca armazenar em texto puro']
  cpf_hash              varchar(64)  [not null, unique, note: 'SHA-256 do CPF cru — única forma de consultar unicidade no banco']
  email                 varchar(150) [not null, unique]
  phone                 varchar(20)  [not null]
  password              varchar(255) [not null, note: 'hash BCrypt fator 10 — nunca armazenar em texto puro']
  birth_date            date         [not null]
  role                  user_role    [not null, note: 'client | professional | admin']
  avatar_url            text         [note: 'URL no S3']
  ban_reason            text         [note: 'null = conta ativa | preenchido = conta banida. Não usar is_active — ban_reason já é o sinalizador']
  notifications_enabled boolean      [not null, default: true, note: 'false = usuário desativou todas as notificações push']
  created_at            timestamp    [not null, default: `now()`]
  updated_at            timestamp    [not null, default: `now()`]
  deleted_at            timestamp    [note: 'soft delete — grace period 30 dias. Durante o período a conta não aparece em listagens mas pode ser reativada (LGPD)']

  indexes {
    cpf_hash [name: 'idx_users_cpf_hash']
    email    [name: 'idx_users_email']
  }

  Note: 'Entidade base. ban_reason não-nulo = banida. deleted_at não-nulo = em grace period. CPF criptografado, unicidade via cpf_hash.'
}

// ─────────────────────────────────────────
// PROFISSIONAIS
// ─────────────────────────────────────────

Table professionals {
  id                      uuid                [pk, default: `gen_random_uuid()`]
  user_id                 uuid                [not null, unique, ref: > users.id]
  bio                     text
  years_of_experience     smallint
  base_hourly_rate        numeric(10,2)       [note: 'valor sugerido ao criar novo serviço com pricing_type=hourly — não é usado diretamente em pedidos']
  verification_status     verification_status [not null, default: 'pending', note: 'pending = aguardando IDwall | approved = pode receber pedidos | rejected = reprovado']
  idwall_token            varchar(255)        [note: 'token retornado pelo IDwall SDK após envio dos documentos — usado para consultar status da verificação']
  idwall_result           jsonb               [note: 'payload completo do IDwall — inclui resultado de antecedentes criminais, biometria e documentos']
  rejection_reason        text                [note: 'preenchido quando verification_status = rejected — exibido ao profissional']
  geo_lat                 numeric(9,6)        [note: 'última latitude conhecida — atualizada via heartbeat do app quando geo_active = true']
  geo_lng                 numeric(9,6)        [note: 'última longitude conhecida — atualizada via heartbeat do app quando geo_active = true']
  geo_active              boolean             [not null, default: false, note: 'toggle manual do profissional — true = disponível para receber pedidos Express']
  subscription_plan_id    uuid                [ref: > subscription_plans.id, note: 'null = sem plano (free tier)']
  subscription_expires_at timestamp           [note: 'null se sem plano — quando expirar, subscription_plan_id deve ser setado para null']
  created_at              timestamp           [not null, default: `now()`]
  updated_at              timestamp           [not null, default: `now()`]
  deleted_at              timestamp           [note: 'soft delete — profissional deletado não aparece em buscas']

  indexes {
    verification_status [name: 'idx_professionals_verification_status']
    geo_active          [name: 'idx_professionals_geo_active']
    user_id             [name: 'idx_professionals_user_id']
  }

  Note: 'Perfil estendido do profissional. 1:1 com users. Só recebe pedidos quando verification_status = approved.'
}

Table professional_documents {
  id              uuid      [pk, default: `gen_random_uuid()`]
  professional_id uuid      [not null, ref: > professionals.id]
  doc_type        doc_type  [not null, note: 'rg | cnh | proof_of_address | profile_photo']
  file_url        text      [not null, note: 'URL no S3 — arquivo enviado no cadastro']
  uploaded_at     timestamp [not null, default: `now()`]
  verified        boolean   [not null, default: false, note: 'false = aguardando processamento IDwall | true = IDwall confirmou via webhook callback']

  indexes {
    professional_id [name: 'idx_professional_documents_professional_id']
  }

  Note: 'Documentos enviados no cadastro. Armazenados no S3 e processados pelo IDwall. verified vira true após webhook de confirmação.'
}

// ─────────────────────────────────────────
// CATÁLOGO — Hierárquico: Área → Categoria → Serviço
// ─────────────────────────────────────────

Table service_areas {
  id          uuid        [pk, default: `gen_random_uuid()`]
  name        varchar(80) [not null, unique]
  icon_url    text
  is_active   boolean     [not null, default: true, note: 'false = oculto no app mas não deletado']
  created_at  timestamp   [not null, default: `now()`]
  updated_at  timestamp   [not null, default: `now()`]

  Note: 'Nível 1 do catálogo. Ex: Elétrica, Hidráulica, Limpeza. Gerenciado pelo admin (RF-67).'
}

Table service_categories {
  id          uuid        [pk, default: `gen_random_uuid()`]
  area_id     uuid        [not null, ref: > service_areas.id]
  name        varchar(80) [not null]
  icon_url    text
  is_active   boolean     [not null, default: true, note: 'false = oculto no app mas não deletado']
  created_at  timestamp   [not null, default: `now()`]
  updated_at  timestamp   [not null, default: `now()`]

  indexes {
    area_id [name: 'idx_service_categories_area_id']
  }

  Note: 'Nível 2 do catálogo. Ex: Eletricista, Encanador. Profissional se cadastra em categorias, não em áreas diretamente.'
}

Table professional_services {
  id                         uuid          [pk, default: `gen_random_uuid()`]
  professional_id            uuid          [not null, ref: > professionals.id]
  category_id                uuid          [not null, ref: > service_categories.id]
  title                      varchar(100)  [not null]
  description                text
  pricing_type               pricing_type  [not null, note: 'hourly = cobra por hora | fixed = preço fechado pelo serviço']
  price                      numeric(10,2) [not null, note: 'valor por hora se hourly | valor total se fixed']
  estimated_duration_minutes int           [note: 'estimativa de duração — usado principalmente com pricing_type=fixed']
  is_active                  boolean       [not null, default: true]
  created_at                 timestamp     [not null, default: `now()`]
  updated_at                 timestamp     [not null, default: `now()`]
  deleted_at                 timestamp     [note: 'soft delete — serviço com pedidos históricos não pode ser deletado fisicamente']

  indexes {
    (professional_id, category_id) [name: 'idx_professional_services_pro_category']
    is_active                      [name: 'idx_professional_services_active']
  }

  Note: 'Nível 3 do catálogo — serviço específico de um profissional. Usado no On Demand. No Express o cliente escolhe só a categoria.'
}

// ─────────────────────────────────────────
// ENDEREÇOS
// ─────────────────────────────────────────

Table saved_addresses {
  id          uuid         [pk, default: `gen_random_uuid()`]
  user_id     uuid         [not null, ref: > users.id]
  label       varchar(60)  [note: 'ex: Casa, Trabalho — label livre definido pelo usuário']
  street      varchar(200) [not null]
  number      varchar(20)
  complement  varchar(80)
  district    varchar(80)
  city        varchar(80)  [not null]
  state       varchar(2)   [not null, note: 'VARCHAR(2) — CHAR(2) causa problema com Hibernate']
  zip_code    varchar(9)   [not null]
  lat         numeric(9,6)
  lng         numeric(9,6)
  is_default  boolean      [not null, default: false, note: 'invariante: apenas um default por usuário — service garante UPDATE em lote antes de ativar novo']
  created_at  timestamp    [not null, default: `now()`]

  indexes {
    user_id               [name: 'idx_saved_addresses_user_id']
    (user_id, is_default) [name: 'idx_saved_addresses_user_default']
  }

  Note: 'Endereços salvos. Obrigatório ao criar pedido — cliente pode salvar na hora do checkout. Endereço deletado não afeta pedidos históricos (address_snapshot em orders).'
}

// ─────────────────────────────────────────
// FAVORITOS
// ─────────────────────────────────────────

Table favorite_professionals {
  id              uuid      [pk, default: `gen_random_uuid()`]
  client_id       uuid      [not null, ref: > users.id]
  professional_id uuid      [not null, ref: > professionals.id]
  created_at      timestamp [not null, default: `now()`]

  indexes {
    (client_id, professional_id) [unique, name: 'idx_favorites_client_professional']
    client_id                    [name: 'idx_favorites_client_id']
  }

  Note: 'Cliente pode favoritar profissionais para acesso rápido. Sem soft delete — desfavoritar deleta fisicamente.'
}

// ─────────────────────────────────────────
// PEDIDOS
// ─────────────────────────────────────────

Table orders {
  id                uuid          [pk, default: `gen_random_uuid()`]
  version           int           [not null, default: 1, note: 'optimistic locking — incrementado a cada mudança de status para prevenir race conditions']
  client_id         uuid          [not null, ref: > users.id]
  professional_id   uuid          [ref: > professionals.id, note: 'null até: aceite no on_demand | cliente aceitar proposta no express']
  service_id        uuid          [ref: > professional_services.id, note: 'null no express — cliente escolhe categoria, não serviço específico']
  area_id           uuid          [ref: > service_areas.id, note: 'área de serviço escolhida pelo cliente no express (ex: Elétrica)']
  category_id       uuid          [not null, ref: > service_categories.id, note: 'obrigatório em ambos os modos — define quais profissionais são buscados']
  mode              order_mode    [not null, note: 'express | on_demand']
  status            order_status  [not null, default: 'pending']
  description       text          [not null, note: 'descrição do problema/serviço — obrigatório em ambos os modos']
  address_id        uuid          [not null, ref: > saved_addresses.id, note: 'sempre obrigatório — cliente pode salvar endereço na hora']
  address_snapshot  jsonb         [not null, note: 'cópia imutável do endereço no momento do pedido — garante histórico mesmo se endereço for editado ou deletado depois']
  scheduled_at      timestamp     [note: 'obrigatório para on_demand | null para express']
  expires_at        timestamp     [not null, note: 'express fase de propostas: criação + 10min | express fase de escolha: 1ª proposta + 30min | on_demand: scheduled_at - 4h']
  urgency_fee       numeric(10,2) [note: 'taxa adicional express — opcional']
  base_amount       numeric(10,2) [note: 'on_demand = preço do serviço escolhido | express = valor proposto pelo profissional escolhido']
  platform_fee      numeric(10,2) [note: '20% de base_amount — descontado do repasse ao profissional na liberação do escrow, não cobrado do cliente']
  total_amount      numeric(10,2) [note: 'base_amount + urgency_fee — o que o cliente paga']
  search_radius_km  numeric(5,2)  [not null, default: 15, note: 'express: raio atual de busca — aumentado a cada tentativa sem propostas (15km → até 50km)']
  search_attempts   smallint      [not null, default: 1, note: 'express: número de rodadas de busca realizadas — máximo em EXPRESS_MAX_SEARCH_ATTEMPTS']
  pro_completed_at  timestamp     [note: 'quando profissional marcou como concluído + enviou foto comprobatória']
  dispute_deadline  timestamp     [note: 'pro_completed_at + 24h — após esse prazo não é possível abrir disputa']
  completed_at      timestamp     [note: 'quando cliente confirmou conclusão — dispara liberação do escrow']
  cancelled_at      timestamp
  cancel_reason     text          [note: 'preenchido em qualquer cancelamento — automático (timeout/expansão esgotada) ou manual']
  created_at        timestamp     [not null, default: `now()`]
  updated_at        timestamp     [not null, default: `now()`]

  indexes {
    status          [name: 'idx_orders_status']
    client_id       [name: 'idx_orders_client_id']
    professional_id [name: 'idx_orders_professional_id']
    category_id     [name: 'idx_orders_category_id']
    mode            [name: 'idx_orders_mode']
    expires_at      [name: 'idx_orders_expires_at']
    scheduled_at    [name: 'idx_orders_scheduled_at']
  }

  Note: 'Entidade central. Express: cliente escolhe área+categoria+endereço+descrição → todos os profissionais próximos notificados simultaneamente → cada um propõe preço → cliente escolhe proposta → aceite → conclusão dupla. On Demand (futuro): cliente escolhe serviço específico + data → profissional aceita → conclusão.'
}

Table order_status_history {
  id          uuid         [pk, default: `gen_random_uuid()`]
  order_id    uuid         [not null, ref: > orders.id]
  from_status order_status [note: 'null na primeira transição (criação do pedido)']
  to_status   order_status [not null]
  reason      text
  changed_by  uuid         [ref: > users.id, note: 'null = transição automática do sistema (timeout, webhook de pagamento, etc.)']
  created_at  timestamp    [not null, default: `now()`]

  indexes {
    order_id [name: 'idx_order_status_history_order_id']
  }

  Note: 'Auditoria imutável de todas as transições de status. Nunca deletar ou atualizar registros desta tabela.'
}

Table order_photos {
  id          uuid       [pk, default: `gen_random_uuid()`]
  order_id    uuid       [not null, ref: > orders.id]
  uploader_id uuid       [not null, ref: > users.id]
  photo_type  photo_type [not null, note: 'request = foto opcional do problema (cliente) | completion_proof = foto obrigatória de conclusão (profissional)']
  url         text       [not null, note: 'URL no S3']
  uploaded_at timestamp  [not null, default: `now()`]

  indexes {
    order_id [name: 'idx_order_photos_order_id']
  }

  Note: 'Fotos do pedido. completion_proof obrigatória para profissional transitar para completed_by_pro. Máx. 5MB, JPG/JPEG/PNG.'
}

Table express_queue {
  id                  uuid            [pk, default: `gen_random_uuid()`]
  order_id            uuid            [not null, ref: > orders.id]
  professional_id     uuid            [not null, ref: > professionals.id]
  proposed_amount     numeric(10,2)   [note: 'preço proposto pelo profissional ao aceitar — null se pro_response = rejected ou timeout']
  notified_at         timestamp       [not null, default: `now()`]
  responded_at        timestamp       [note: 'quando profissional respondeu (accepted/rejected)']
  pro_response        pro_response    [note: 'null = ainda não respondeu | accepted = aceitou e propôs preço | rejected = recusou explicitamente | timeout = não respondeu no prazo']
  client_response     client_response [note: 'null = cliente ainda não escolheu | accepted = cliente escolheu esta proposta | rejected = descartada ao cliente escolher outra — só preenchido quando pro_response = accepted']
  client_responded_at timestamp       [note: 'quando cliente respondeu à proposta']
  queue_position      smallint        [not null, note: 'ordem de notificação — profissional com posição 1 é notificado primeiro']

  indexes {
    order_id                   [name: 'idx_express_queue_order_id']
    professional_id            [name: 'idx_express_queue_professional_id']
    (order_id, queue_position) [unique, name: 'idx_express_queue_order_position']
  }

  Note: 'Broadcast do Express. Todos os profissionais da rodada são notificados simultaneamente. Cada um propõe seu preço independentemente. Cliente vê todas as propostas recebidas e escolhe uma — as demais são recusadas automaticamente. Prioridade por proximidade e subscription_plan.express_priority.'
}

// ─────────────────────────────────────────
// CALENDÁRIO
// Padrão: todos os dias livres.
// Profissional só registra bloqueios.
// ─────────────────────────────────────────

Table blocked_periods {
  id              uuid       [pk, default: `gen_random_uuid()`]
  professional_id uuid       [not null, ref: > professionals.id]
  block_type      block_type [not null]

  // recurring: bloqueia dia da semana (ex: todo domingo)
  weekday         smallint   [note: '0=Dom, 1=Seg … 6=Sáb | obrigatório se block_type = recurring']

  // specific_date: bloqueia data específica (ex: 26/03)
  specific_date   date       [note: 'obrigatório se block_type = specific_date']

  // recurring + specific_date: horário do bloqueio — null = dia inteiro
  starts_at       time       [note: 'horário de início | null = dia inteiro bloqueado']
  ends_at         time       [note: 'horário de fim | null = dia inteiro bloqueado']

  // order: gerado automaticamente ao aceitar pedido on_demand
  order_id        uuid       [ref: > orders.id, note: 'obrigatório se block_type = order']
  order_starts_at timestamp  [note: 'início do bloqueio por pedido | obrigatório se block_type = order']
  order_ends_at   timestamp  [note: 'fim do bloqueio por pedido | obrigatório se block_type = order']

  reason          text
  created_at      timestamp  [not null, default: `now()`]

  indexes {
    professional_id                  [name: 'idx_blocked_periods_professional_id']
    (professional_id, weekday)       [name: 'idx_blocked_periods_recurring']
    (professional_id, specific_date) [name: 'idx_blocked_periods_specific']
    order_id                         [name: 'idx_blocked_periods_order_id']
  }

  Note: 'Calendário invertido — padrão livre, profissional registra bloqueios. Validação de campos obrigatórios por block_type fica no BlockedPeriodService.'
}

// ─────────────────────────────────────────
// PAGAMENTOS E ESCROW
// ─────────────────────────────────────────

Table payment_methods {
  id            uuid                [pk, default: `gen_random_uuid()`]
  user_id       uuid                [not null, ref: > users.id]
  method_type   payment_method_type [not null, note: 'pix | credit_card | debit_card']
  asaas_id      varchar(100)        [note: 'ID do método no Asaas — dados sensíveis (número do cartão etc.) ficam só no Asaas']
  display_label varchar(80)         [note: 'label mascarado para exibição — ex: Pix *234, Visa ****1234']
  is_default    boolean             [not null, default: false, note: 'invariante: apenas um default por usuário — service garante UPDATE em lote antes de ativar novo']
  created_at    timestamp           [not null, default: `now()`]

  indexes {
    user_id               [name: 'idx_payment_methods_user_id']
    (user_id, is_default) [name: 'idx_payment_methods_user_default']
  }

  Note: 'Métodos de pagamento do cliente. Dados sensíveis ficam no Asaas. Múltiplos por usuário, um default. Usado no checkout — default pré-selecionado mas pode ser trocado.'
}

Table payout_accounts {
  id              uuid         [pk, default: `gen_random_uuid()`]
  professional_id uuid         [not null, ref: > professionals.id]
  account_type    account_type [not null, note: 'bank_account | pix_key']
  pix_key         varchar(255) [note: 'criptografado via AES-256/CBC — nunca armazenar em texto puro']
  pix_key_hash    varchar(64)  [note: 'SHA-256 da chave Pix cru — usado em queries de unicidade']
  bank_code       varchar(10)
  branch          varchar(10)
  account_number  varchar(20)
  asaas_id        varchar(100) [note: 'ID da conta no Asaas — usado para processar transferências']
  is_default      boolean      [not null, default: false, note: 'invariante: apenas um default por profissional — service garante UPDATE em lote antes de ativar novo']
  created_at      timestamp    [not null, default: `now()`]
  updated_at      timestamp    [not null, default: `now()`]
  deleted_at      timestamp    [note: 'soft delete — preserva vínculo histórico com transactions.payout_account_id']

  indexes {
    professional_id               [name: 'idx_payout_accounts_professional_id']
    (professional_id, is_default) [name: 'idx_payout_accounts_professional_default']
    pix_key_hash                  [name: 'idx_payout_accounts_pix_key_hash']
  }

  Note: 'Contas de recebimento do profissional. Múltiplas por profissional, uma default. pix_key criptografada, unicidade via pix_key_hash.'
}

Table transactions {
  id                   uuid               [pk, default: `gen_random_uuid()`]
  order_id             uuid               [not null, ref: > orders.id]
  asaas_charge_id      varchar(100)       [unique, note: 'ID da cobrança no Asaas — usado para consultar status e processar webhooks']
  asaas_transfer_id    varchar(100)       [note: 'ID da transferência ao profissional no Asaas']
  payment_method_id    uuid               [ref: > payment_methods.id, note: 'método usado no checkout — pré-preenchido com default, cliente pode trocar antes de pagar']
  payout_account_id    uuid               [ref: > payout_accounts.id, note: 'conta de recebimento do profissional no momento da liberação']
  type                 transaction_type   [not null]
  amount               numeric(10,2)      [not null]
  status               transaction_status [not null, default: 'pending']
  pix_qr_code          text               [note: 'QR code base64 gerado pelo Asaas — exibido ao cliente para pagamento']
  pix_copy_paste       text               [note: 'linha digitável Pix — alternativa ao QR code']
  pix_expiration       timestamp          [note: 'expiração do QR code — após expirar, novo QR deve ser gerado']
  transfer_retry_count int                [not null, default: 0, note: 'número de tentativas de transferência ao profissional — resetado a cada sucesso']
  transfer_last_error  text               [note: 'última mensagem de erro da transferência — útil para debug e suporte']
  processed_at         timestamp          [note: 'quando a transação foi processada pelo Asaas (via webhook)']
  created_at           timestamp          [not null, default: `now()`]
  updated_at           timestamp          [not null, default: `now()`]

  indexes {
    order_id          [name: 'idx_transactions_order_id']
    status            [name: 'idx_transactions_status']
    asaas_charge_id   [name: 'idx_transactions_asaas_charge_id']
    asaas_transfer_id [name: 'idx_transactions_asaas_transfer_id']
  }

  Note: 'Auditoria financeira imutável. Cada evento financeiro gera uma linha (charge, hold, release, refund). transfer_retry_count para resiliência de falhas na transferência ao profissional.'
}

// ─────────────────────────────────────────
// CHAT
// Liberado apenas quando status = accepted
// ─────────────────────────────────────────

Table conversations {
  id         uuid      [pk, default: `gen_random_uuid()`]
  order_id   uuid      [not null, unique, ref: > orders.id]
  created_at timestamp [not null, default: `now()`]

  Note: 'Criada automaticamente quando order.status transita para accepted. 1:1 com orders. Histórico mantido permanentemente — pode ser usado como evidência em disputas.'
}

Table messages {
  id              uuid      [pk, default: `gen_random_uuid()`]
  conversation_id uuid      [not null, ref: > conversations.id]
  sender_id       uuid      [not null, ref: > users.id]
  content         text      [note: 'obrigatório se msg_type = text']
  attachment_url  text      [note: 'URL no S3 — obrigatório se msg_type = image | máx. 5MB, JPG/JPEG/PNG']
  msg_type        msg_type  [not null, default: 'text']
  sent_at         timestamp [not null, default: `now()`]
  delivered_at    timestamp [note: 'preenchido quando destinatário recebeu via WebSocket']
  read_at         timestamp [note: 'preenchido quando destinatário abriu a conversa']

  indexes {
    conversation_id [name: 'idx_messages_conversation_id']
    sender_id       [name: 'idx_messages_sender_id']
  }

  Note: 'Mensagens em tempo real via WebSocket. Persistidas no banco para histórico e evidência em disputas.'
}

// ─────────────────────────────────────────
// AVALIAÇÕES
// Double-blind: nenhum vê a avaliação do outro até ambos avaliarem
// ─────────────────────────────────────────

Table reviews {
  id           uuid      [pk, default: `gen_random_uuid()`]
  order_id     uuid      [not null, ref: > orders.id]
  reviewer_id  uuid      [not null, ref: > users.id]
  reviewee_id  uuid      [not null, ref: > users.id]
  rating       smallint  [not null, note: '1 a 5']
  comment      text
  submitted_at timestamp [not null, default: `now()`]
  published_at timestamp [note: 'null = ainda não publicada | preenchido quando: ambos avaliaram OU orders.completed_at + 7 dias expirou']

  indexes {
    (order_id, reviewer_id) [unique, name: 'idx_reviews_order_reviewer']
    reviewee_id             [name: 'idx_reviews_reviewee_id']
    published_at            [name: 'idx_reviews_published_at']
  }

  Note: 'Double-blind: published_at null até ambas as partes avaliarem ou 7 dias após orders.completed_at expirarem. Imutável após publicação — nunca permitir edição.'
}

// ─────────────────────────────────────────
// DISPUTAS
// Janela: 24h após pro_completed_at
// Resolução exclusiva pelo admin
// ─────────────────────────────────────────

Table disputes {
  id                   uuid               [pk, default: `gen_random_uuid()`]
  order_id             uuid               [not null, unique, ref: > orders.id]
  opened_by            uuid               [not null, ref: > users.id]
  reason               text               [not null]
  status               dispute_status     [not null, default: 'open']
  resolution           dispute_resolution [note: 'refund_full | refund_partial | release_to_pro — preenchido pelo admin ao resolver']
  client_refund_amount numeric(10,2)      [note: 'valor devolvido ao cliente — obrigatório se resolution = refund_full ou refund_partial']
  professional_amount  numeric(10,2)      [note: 'valor liberado ao profissional — obrigatório se resolution = release_to_pro ou refund_partial | invariante: client_refund_amount + professional_amount = orders.total_amount']
  resolved_by          uuid               [ref: > users.id, note: 'UUID do admin que resolveu']
  resolved_at          timestamp
  opened_at            timestamp          [not null, default: `now()`]
  updated_at           timestamp          [not null, default: `now()`]

  indexes {
    status   [name: 'idx_disputes_status']
    order_id [name: 'idx_disputes_order_id']
  }

  Note: 'Pode ser aberta em até 24h após orders.pro_completed_at. Resolvida exclusivamente pelo admin. client_refund_amount + professional_amount deve sempre igualar orders.total_amount.'
}

Table dispute_evidences {
  id            uuid          [pk, default: `gen_random_uuid()`]
  dispute_id    uuid          [not null, ref: > disputes.id]
  sender_id     uuid          [not null, ref: > users.id]
  evidence_type evidence_type [not null, note: 'text | photo']
  content       text          [note: 'obrigatório se evidence_type = text']
  file_url      text          [note: 'URL no S3 — obrigatório se evidence_type = photo | máx. 5MB, JPG/JPEG/PNG']
  sent_at       timestamp     [not null, default: `now()`]

  indexes {
    dispute_id [name: 'idx_dispute_evidences_dispute_id']
  }

  Note: 'Evidências enviadas por cliente e profissional. Imutáveis após envio.'
}

// ─────────────────────────────────────────
// NOTIFICAÇÕES
// Preferência global em users.notifications_enabled
// ─────────────────────────────────────────

Table push_tokens {
  id         uuid      [pk, default: `gen_random_uuid()`]
  user_id    uuid      [not null, ref: > users.id]
  expo_token text      [not null, unique]
  platform   platform  [not null, note: 'android | ios']
  created_at timestamp [not null, default: `now()`]
  last_seen  timestamp [note: 'atualizado a cada abertura do app — tokens com last_seen > 90 dias podem ser invalidados']

  indexes {
    user_id    [name: 'idx_push_tokens_user_id']
    expo_token [name: 'idx_push_tokens_expo_token']
  }

  Note: 'Tokens FCM/Expo por dispositivo. Um usuário pode ter múltiplos tokens (múltiplos dispositivos). Verificar users.notifications_enabled antes de enviar.'
}

Table notifications {
  id         uuid              [pk, default: `gen_random_uuid()`]
  user_id    uuid              [not null, ref: > users.id]
  type       notification_type [not null]
  title      varchar(120)
  body       text
  data       jsonb             [note: 'payload extra para deep link — ex: {"order_id": "...", "dispute_id": "..."}']
  sent_at    timestamp         [note: 'null = ainda não enviada (na fila)']
  read_at    timestamp         [note: 'null = não lida']
  created_at timestamp         [not null, default: `now()`]

  indexes {
    user_id [name: 'idx_notifications_user_id']
    read_at [name: 'idx_notifications_read_at']
  }

  Note: 'Log de todas as notificações. Só enviar push se users.notifications_enabled = true. sent_at null indica notificação ainda na fila de envio.'
}

// ─────────────────────────────────────────
// PLANOS DE ASSINATURA
// ─────────────────────────────────────────

Table subscription_plans {
  id                  uuid          [pk, default: `gen_random_uuid()`]
  name                varchar(60)   [not null]
  price_monthly       numeric(10,2) [not null]
  highlight_in_search boolean       [not null, default: false, note: 'true = profissional aparece destacado nos resultados de busca']
  express_priority    boolean       [not null, default: false, note: 'true = profissional recebe prioridade na fila do Express']
  badge_label         varchar(30)   [note: 'ex: Pro, Verificado Plus — exibido no perfil do profissional']
  is_active           boolean       [not null, default: true]
  created_at          timestamp     [not null, default: `now()`]
  updated_at          timestamp     [not null, default: `now()`]

  Note: 'Planos opcionais para profissionais. Gerenciado pelo admin. professionals.subscription_plan_id null = free tier sem benefícios.'
}

// ─────────────────────────────────────────
// LGPD
// ─────────────────────────────────────────

Table data_export_requests {
  id           uuid      [pk, default: `gen_random_uuid()`]
  user_id      uuid      [not null, ref: > users.id]
  requested_at timestamp [not null, default: `now()`]
  fulfilled_at timestamp [note: 'null = ainda processando']
  download_url text      [note: 'URL no S3 com o arquivo de exportação — preenchido quando fulfilled']
  expires_at   timestamp [note: 'link expira após período determinado — após expirar download_url não funciona mais']

  indexes {
    user_id [name: 'idx_data_export_requests_user_id']
  }

  Note: 'Direito de portabilidade de dados (LGPD art. 18). Prazo de atendimento: 15 dias.'
}

Table consent_logs {
  id           uuid        [pk, default: `gen_random_uuid()`]
  user_id      uuid        [not null, ref: > users.id]
  consent_type varchar(60) [not null, note: 'terms_of_use | privacy_policy']
  version      varchar(20) [not null, note: 'versão do documento aceito — ex: 1.0, 2.1']
  accepted     boolean     [not null, note: 'false = usuário recusou (raro mas possível)']
  ip_address   varchar(45) [note: 'IPv4 ou IPv6 — registrado para comprovação legal']
  accepted_at  timestamp   [not null, default: `now()`]

  indexes {
    user_id [name: 'idx_consent_logs_user_id']
  }

  Note: 'Registro de aceite de termos (LGPD art. 7 e 8). Imutável — nunca deletar ou atualizar. Novo aceite = nova linha.'
}

*All Set — Projeto Integrador 1 — UNIFOR — 2026*
