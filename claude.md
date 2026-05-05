# AllSet API — Backend

Marketplace de serviços autônomos que conecta clientes a profissionais verificados em Fortaleza/CE.
Dois modos de contratação: **Agendado** (data/hora escolhida pelo cliente, futuro) e **Express** (broadcast para profissionais próximos, cada um propõe preço, cliente escolhe).

Modelo de negócio: taxa de 20% descontada no momento da liberação do escrow. Pagamento retido via Asaas e liberado somente após conclusão confirmada por ambas as partes.

---

## Stack

| Camada | Tecnologia |
|---|---|
| Runtime | Java 21 (Virtual Threads habilitado) |
| Framework | Spring Boot 3.5.x |
| Build | Maven 3.9 (`./mvnw` disponível) |
| Banco | PostgreSQL 16 |
| Cache | Redis 7 |
| Migrations | Flyway |
| Auth | JWT HS256 via Spring OAuth2 Resource Server (`NimbusJwtDecoder`) |
| Docs | SpringDoc OpenAPI 2.8 / Swagger UI |
| Container | Docker + Docker Compose (multi-stage build) |
| Integrações | Asaas (pagamentos/escrow), IDwall SDK (KYC), AWS S3, FCM (push), WebSocket (chat) |

---

## Comandos

```bash
# Infra local (Postgres + Redis)
docker compose up -d postgres redis

# Rodar a aplicação (perfil dev por padrão)
./mvnw spring-boot:run

# Build da imagem completa
docker compose up --build

# Build do JAR sem testes (ainda não implementados)
./mvnw clean package -DskipTests
```

Swagger UI disponível em `http://localhost:8080/swagger-ui.html` apenas no perfil `dev`.

---

## Variáveis de ambiente

Copiar `.env.example` → `.env` e preencher antes de subir. A aplicação falha rápido na inicialização se qualquer variável obrigatória estiver ausente ou inválida — via `AppProperties` (`@ConfigurationProperties`). Não acessar `System.getenv()` diretamente; sempre usar `AppProperties`.

| Variável | Obrigatória | Detalhe |
|---|---|---|
| `JWT_SECRET` | Sim | Segredo HMAC-SHA256 para assinar JWTs |
| `CPF_ENCRYPTION_KEY` | Sim | Chave AES-256 — exatamente 64 chars hex (32 bytes) |
| `DATABASE_URL` | Sim | JDBC URL do PostgreSQL |
| `DB_USER` / `DB_PASS` | Sim | Credenciais do banco |
| `REDIS_HOST` / `REDIS_PORT` | Não | Padrão: `localhost:6379` |
| `PORT` | Não | Padrão: `8080` |
| `USER_PURGE_CRON` | Não | Cron do job de purga (padrão: `0 0 2 * * *`) |
| `RESEND_API_KEY` | Sim | API key do Resend para envio de e-mails |
| `EMAIL_FROM` | Sim | Endereço de origem dos e-mails (ex: `noreply@allset.com.br`) |
| `SPRING_PROFILES_ACTIVE` | Não | `dev` ou `prod` (padrão: `dev`) |
| `ACCESS_TOKEN_TTL_MINUTES` | Não | TTL do access token (padrão: `15`) |
| `REFRESH_TOKEN_TTL_DAYS` | Não | TTL do refresh token (padrão: `7`) |
| `RESET_CODE_TTL_MINUTES` | Não | TTL do código de recuperação de senha (padrão: `10`) |
| `SUBSCRIPTION_EXPIRATION_CRON` | Não | Cron do job de expiração de assinaturas (padrão: `0 */30 * * * *`) |
| `EXPRESS_SEARCH_RADIUS_METERS` | Não | Raio único de busca Express, em metros (padrão: `300`) |
| `EXPRESS_PROPOSAL_WINDOW_MINUTES` | Não | Janela em que profissionais podem propor no Express (padrão: `15`) |
| `EXPRESS_CLIENT_WINDOW_MINUTES` | Não | Janela após o fim das propostas para o cliente escolher (padrão: `30`) |
| `EXPRESS_MAX_QUEUE_SIZE` | Não | Máximo de profissionais notificados (padrão: `10`) |
| `GEOCODING_BASE_URL` | Não | Base URL do provider (padrão: `https://nominatim.openstreetmap.org`) |
| `GEOCODING_USER_AGENT` | Sim | User-Agent identificador exigido pela política do Nominatim (ex: `AllSet-API/1.0 (contato@allset.com.br)`) |
| `GEOCODING_CACHE_TTL_SECONDS` | Não | TTL do cache de resultados positivos (padrão: `2592000` = 30 dias) |
| `GEOCODING_NEGATIVE_CACHE_TTL_SECONDS` | Não | TTL do cache de "não localizado" (padrão: `300` = 5min) |
| `GEOCODING_ENABLED` | Não | Kill-switch — `false` desabilita lookup e enriquecimento (padrão: `true`) |

---

## Estrutura de pacotes

```
src/main/java/com/allset/api/
├── AllsetApiApplication.java
├── boilerplate/domain/PostgresEntity.java   # Base entity — UUID, auditoria, soft delete
├── config/
│   ├── AppProperties.java                   # @ConfigurationProperties — valida envs na startup
│   ├── JpaConfig.java                       # @EnableJpaAuditing
│   ├── SecurityConfig.java                  # JWT, BCrypt, CORS, regras de acesso
│   ├── OpenApiConfig.java                   # bearerAuth no Swagger
│   └── StartupLogger.java
├── shared/
│   ├── crypto/CpfConverter.java             # AttributeConverter AES-256/CBC transparente
│   ├── validation/ValidCPF.java + CpfValidator.java
│   └── exception/
│       ├── ApiError.java                    # Contrato de resposta de erro
│       └── GlobalExceptionHandler.java      # @RestControllerAdvice centralizado
├── auth/                                    # Login, JWT, refresh token, recuperação de senha
├── user/                                    # Módulo de usuários (clientes, profissionais, admins)
│   └── scheduler/UserPurgeScheduler.java
├── address/                                 # Endereços salvos por usuário
├── professional/                            # Perfil profissional, KYC, geolocalização
├── document/                                # Documentos do profissional (S3 + IDwall)
├── offering/                                # Serviços oferecidos pelo profissional
├── catalog/                                 # Áreas e categorias de serviço (admin)
├── subscription/                            # Planos de assinatura para profissionais
│   └── scheduler/SubscriptionExpirationScheduler.java
├── calendar/                                # Calendário de disponibilidade do profissional
└── order/                                   # Pedidos Express — ciclo completo
    ├── controller/OrderController.java
    ├── service/OrderServiceImpl.java
    ├── repository/
    │   ├── OrderRepository.java
    │   ├── ExpressQueueRepository.java      # Haversine, bulk-reject, timeout queries
    │   ├── OrderStatusHistoryRepository.java
    │   └── OrderPhotoRepository.java
    ├── domain/                              # Order, ExpressQueueEntry, OrderStatusHistory, OrderPhoto + enums
    ├── mapper/OrderMapper.java
    ├── dto/                                 # CreateExpressOrderRequest, ProRespondRequest,
    │                                        # ClientRespondRequest, OrderResponse, ExpressProposalResponse,
    │                                        # DistanceBand (faixa de distância por proposta), ...
    ├── exception/                           # OrderNotFoundException, OrderStatusTransitionException,
    │                                        # ExpressQueueViolationException, NoProfessionalsAvailableException,
    │                                        # ProposalWindowExpiredException
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

Cada módulo futuro **deve** seguir essa estrutura: `controller / service / repository / domain / mapper / dto / exception`.

---

## Infraestrutura base

### `PostgresEntity` — base de todas as entidades

Todas as entidades **devem** estender `PostgresEntity`. Nunca criar entidade sem ela.

| Campo | Tipo | Comportamento |
|---|---|---|
| `id` | `UUID` | Gerado pelo banco via `gen_random_uuid()` — nunca setar manualmente em Java |
| `createdAt` | `Instant` | `@CreatedDate`, imutável após inserção |
| `updatedAt` | `Instant` | `@LastModifiedDate`, atualizado automaticamente |
| `deletedAt` | `Instant` | `null` = ativo; não-nulo = soft deleted |

### Segurança — JWT HS256

- **Algoritmo:** HS256 (`NimbusJwtDecoder.withSecretKey()` + `NimbusJwtEncoder` com `ImmutableSecret`)
- **Sessão:** `STATELESS` — sem cookie, sem CSRF
- **Roles:** claim `"role"` no token, **sem** prefixo `ROLE_` — usar `hasAuthority('admin')` nas anotações, nunca `hasRole()`
- **Sub:** claim `sub` = UUID do usuário como String — regras self: `#id.toString() == authentication.name`
- **BCrypt:** fator 10
- Geração de tokens implementada no módulo `auth` (a criar). Access token curto + refresh token longo (persistir refresh no Redis com TTL)

### Tratamento de erros

`GlobalExceptionHandler` centralizado retorna sempre `ApiError`:

```json
{
  "status": 404,
  "message": "Usuário não encontrado: 3fa85f64-...",
  "fields": {},
  "timestamp": "2026-03-25T14:00:00Z"
}
```

| Exceção | HTTP |
|---|---|
| `MethodArgumentNotValidException` | 400 + mapa de campos inválidos |
| `*NotFoundException` | 404 |
| `EmailAlreadyExistsException` / `CpfAlreadyExistsException` | 409 |
| `UserPendingDeletionException` | 423 Locked + `scheduledDeletionAt` |
| `UserBannedException` | 403 |
| `Exception` (catch-all) | 500 |

Ao criar exceções novas em módulos futuros, **sempre** registrá-las no `GlobalExceptionHandler` — controllers nunca retornam `ResponseEntity` diretamente para erros.

---

## Módulos implementados

### Usuários (`/api/users`)

**Entidade `User`** — estende `PostgresEntity`. Campos relevantes:

| Campo | Detalhe |
|---|---|
| `cpf` | Criptografado em repouso via `CpfConverter` (AES-256/CBC, IV aleatório por escrita) |
| `cpfHash` | SHA-256 do CPF cru — usado em queries de unicidade (campo criptografado não é consultável) |
| `role` | Enum PG `user_role`: `client`, `professional`, `admin` |
| `active` | `false` = conta banida |
| `deletedAt` | Soft delete com grace period de 30 dias |

**Endpoints:**

| Método | Caminho | Acesso |
|---|---|---|
| `POST /api/users` | Criar usuário | Admin |
| `GET /api/users` | Listar paginado (20/página) | Admin |
| `GET /api/users/{id}` | Buscar por ID | Admin ou próprio |
| `PUT /api/users/{id}` | Atualizar (apenas campos não-nulos) | Admin ou próprio |
| `DELETE /api/users/{id}` | Soft delete — grace period 30 dias | Admin ou próprio |
| `PATCH /api/users/{id}/reactivate` | Cancelar exclusão dentro do grace period | Admin ou próprio |
| `PATCH /api/users/{id}/ban` | Banir (`active=false` + `banReason`) | Admin |
| `PATCH /api/users/{id}/activate` | Desbanir | Admin |

`GET /api/users` aceita `?deleted=true` e `?banned=true`. Padrão: apenas ativas.

**Regras de negócio:**
- Duplicidade de e-mail/CPF verificada apenas contra contas **ativas** — conta soft-deleted com mesmo e-mail/CPF retorna `UserPendingDeletionException` (423), não 409
- CPF e senha são **imutáveis** após criação — `PUT` ignora esses campos
- Atualização de e-mail verifica unicidade antes de aplicar
- `UserPurgeScheduler`: cron diário (2h) — remove fisicamente registros com `deletedAt < (now - 30 dias)`

### Endereços Salvos (`/api/users/{userId}/addresses`)

**Invariante crítica:** no máximo um `isDefault = true` por usuário. Ao definir default, executar `UPDATE` em lote zerando os outros **antes** de ativar o novo — nunca em dois passos separados.

`findOwnedAddress(userId, id)` usa `findByIdAndUserId` — query única que verifica ID e ownership simultaneamente. Retorna 404 para "não encontrado" E "pertence a outro usuário" — não vazar informação de ownership por timing ou mensagem diferente.

Deleção é **física** — não usa soft delete, diferente das entidades principais.

---

## Domínios a implementar

| Módulo | Responsabilidade |
|---|---|
| `payment` | Asaas — criação de cobrança, escrow, liberação com fee 20%, reembolso, webhook Asaas |
| ~~`geocoding`~~ | ~~Conversão de endereço escrito em coordenadas via provider externo (Nominatim/OSM) com cache Redis~~ — implementado |
| `chat` | WebSocket em tempo real, persistência de mensagens, histórico acessível pós-conclusão |
| `review` | Avaliação bilateral double-blind — publica quando ambos submetem ou 7 dias expiram |
| `dispute` | Abertura em até 24h pós-conclusão, evidências (S3), resolução exclusiva por admin |
| `notification` | Push via FCM, persistência, preferências do usuário |
| `admin` | Moderação, métricas, resolução de disputas |

---

## Convenções

### IDs, banco e datas
- IDs sempre `UUID` gerados pelo banco — nunca `Long` sequencial, nunca gerar em Java
- Timestamps sempre `Instant` (UTC) — nunca `LocalDateTime`
- Soft delete com `deletedAt` nullable em todas as entidades principais
- Migrations Flyway numeradas sequencialmente: `V{n}__{descricao_snake_case}.sql`
- **Nunca** usar `ddl-auto=update` ou `create-drop` fora de testes

### API
- Prefixo `/api/v1/` nos próximos módulos
- `ApiError` centralizado — controllers não retornam `ResponseEntity` para erros
- Paginação: offset/limit, tamanho padrão 20
- Mensagens de validação e erro em **português**
- Códigos de erro padronizados (`ORDER_EXPIRED`, `PAYMENT_FAILED`, `PROFESSIONAL_NOT_VERIFIED`, etc.)

### DTOs e Mappers
- Sempre `RequestDTO` separado de `ResponseDTO` — nunca serializar entidade JPA diretamente
- Conversões exclusivamente via **MapStruct** — nunca setar campos manualmente
- Atualização parcial: campos nullable no `UpdateRequest` — service aplica só os não-nulos

### Segurança
- CPF sempre criptografado via `CpfConverter`; unicidade via `cpf_hash`
- Senhas: BCrypt fator 10 — nunca logar, nunca comparar sem `passwordEncoder.matches()`
- Não diferenciar "não encontrado" de "sem permissão" em queries de ownership — sempre 404

### Integrações externas
Clients para Asaas, IDwall, S3 e FCM ficam em `integration/`. Services nunca chamam APIs externas diretamente. Tratar falhas com retry + fallback. Webhooks externos (ex: Asaas) validar assinatura antes de processar.

---

## Regras de negócio críticas (produto)

1. **Escrow obrigatório** — cliente paga ao criar o pedido; valor nunca vai direto ao profissional
2. **Conclusão dupla** — pedido só fecha quando AMBOS confirmam; profissional obriga envio de foto comprobatória
3. **Express — broadcast hiper-local** — todos os profissionais aprovados em um raio fixo de **300 metros** (configurável em `EXPRESS_SEARCH_RADIUS_METERS`) são notificados simultaneamente via Haversine. Cada um envia sua proposta de preço **dentro de 15 minutos** (`EXPRESS_PROPOSAL_WINDOW_MINUTES`). Após esse prazo, novas propostas são bloqueadas; o cliente tem **mais 30 minutos** (`EXPRESS_CLIENT_WINDOW_MINUTES`) para escolher entre as propostas recebidas (45 min totais). Se ninguém propõe nos primeiros 15 min ou se o cliente não escolhe em 45 min, o pedido é cancelado automaticamente. **Não há expansão de raio.** Prioridade na fila: assinantes Pro primeiro, depois proximidade. O status permanece `pending` durante toda a janela de 45 min; a discriminação entre "fase de propostas" e "fase de escolha" é feita comparando `now()` com `proposalDeadline` e `expiresAt`.
4. **Localização nunca exposta** — exibir apenas a quantidade de profissionais no raio e a **faixa de distância** (terço do raio configurado) por proposta; nunca coordenadas exatas nem distância numérica em metros ao cliente
5. **Double-blind** — avaliações só ficam visíveis após ambas as partes submeterem ou 7 dias expirarem
6. **Janela de disputa** — 24h após conclusão; resolvida exclusivamente por admin
7. **Taxa de cancelamento** — 50% do valor cobrado se cancelado dentro de 24h da contratação
8. **Fee da plataforma** — 20% descontado na liberação do escrow, não no pagamento inicial
9. **KYC automático** — verificação de documentos via IDwall SDK no cadastro do profissional, sem aprovação manual
10. **Uploads** — máx. 5MB, formatos JPG/JPEG/PNG (fotos de conclusão, evidências de disputa, avatar)
11. **Geocoding de endereços** — endereços salvos sem `lat`/`lng` são geocodificados automaticamente via Nominatim no `POST /api/users/{userId}/addresses`. Falha do provider (timeout, 5xx, kill-switch) **não bloqueia** o cadastro — endereço é gravado com coordenadas nulas e o front pode reexecutar lookup via `POST /api/v1/geocoding/lookup` e atualizar via `PUT`. Endereço não localizável (provider devolve vazio) retorna 422.

---

## Perfis

| Perfil | `show-sql` | Swagger UI |
|---|---|---|
| `dev` (padrão) | `true` | `/swagger-ui.html` ativo |
| `prod` | `false` | desabilitado |

---

## Referências internas

- Schema completo do banco: `docs/schema.dbml`
- Requisitos funcionais e regras de negócio: `docs/requisitos.pdf`
- Decisões de arquitetura: `docs/adr/`

---

*All Set — Projeto Integrador 1 — UNIFOR — 2026*