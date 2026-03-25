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

| Variável | Obrigatória | Descrição |
|---|---|---|
| `JWT_SECRET` | Sim | Segredo HMAC-SHA256 para assinar JWTs |
| `CPF_ENCRYPTION_KEY` | Sim | Chave AES-256 — 64 caracteres hex (32 bytes) |
| `DATABASE_URL` | Sim | URL JDBC do PostgreSQL (ex: `jdbc:postgresql://localhost:5432/allset`) |
| `DB_USER` | Sim | Usuário do banco |
| `DB_PASS` | Sim | Senha do banco |
| `REDIS_HOST` | Não | Host do Redis (padrão: `localhost`) |
| `REDIS_PORT` | Não | Porta do Redis (padrão: `6379`) |
| `PORT` | Não | Porta da aplicação (padrão: `8080`) |
| `USER_PURGE_CRON` | Não | Cron do job de purga (padrão: `0 0 2 * * *`) |
| `SPRING_PROFILES_ACTIVE` | Não | Perfil ativo: `dev` ou `prod` (padrão: `dev`) |

---

## Estrutura do projeto

```
src/main/java/com/allset/api/
├── AllsetApiApplication.java        # Entry point
├── boilerplate/
│   └── domain/PostgresEntity.java   # Base entity: UUID, auditoria, soft delete
├── config/
│   ├── AppProperties.java           # Configurações tipadas via @ConfigurationProperties
│   ├── JpaConfig.java               # @EnableJpaAuditing
│   ├── SecurityConfig.java          # JWT, BCrypt, CORS, regras de acesso
│   ├── OpenApiConfig.java           # Swagger UI com bearerAuth
│   └── StartupLogger.java           # Log de URLs na inicialização
├── shared/
│   ├── crypto/CpfConverter.java     # Conversor JPA AES-256/CBC para CPF
│   ├── validation/
│   │   ├── ValidCPF.java            # Anotação @ValidCPF
│   │   └── CpfValidator.java        # Algoritmo de dígito verificador
│   └── exception/
│       ├── ApiError.java            # Contrato de resposta de erro
│       └── GlobalExceptionHandler.java
├── user/                            # Módulo de usuários
│   ├── controller/UserController.java
│   ├── service/UserServiceImpl.java
│   ├── repository/UserRepository.java
│   ├── domain/User.java
│   ├── domain/UserRole.java
│   ├── mapper/UserMapper.java
│   ├── dto/                         # CreateUserRequest, UpdateUserRequest, UserResponse, BanUserRequest
│   ├── exception/                   # 5 exceções de domínio
│   └── scheduler/UserPurgeScheduler.java
└── address/                         # Módulo de endereços salvos
    ├── controller/SavedAddressController.java
    ├── service/SavedAddressServiceImpl.java
    ├── repository/SavedAddressRepository.java
    ├── domain/SavedAddress.java
    ├── mapper/SavedAddressMapper.java
    ├── dto/                         # CreateSavedAddressRequest, UpdateSavedAddressRequest, SavedAddressResponse
    └── exception/SavedAddressNotFoundException.java

src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__init.sql
    ├── V2__create_users.sql
    ├── V3__create_saved_addresses.sql
    └── V4__alter_saved_addresses_state_to_varchar.sql
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
| `UserNotFoundException` | 404 |
| `SavedAddressNotFoundException` | 404 |
| `EmailAlreadyExistsException` | 409 |
| `CpfAlreadyExistsException` | 409 |
| `UserPendingDeletionException` | 423 (Locked) — com `scheduledDeletionAt` |
| `UserBannedException` | 403 |
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

## Banco de dados (Flyway)

| Migration | Descrição |
|---|---|
| `V1__init.sql` | Placeholder vazio |
| `V2__create_users.sql` | Cria tipo `user_role` (enum PG) e tabela `users` |
| `V3__create_saved_addresses.sql` | Cria tabela `saved_addresses` com índices |
| `V4__alter_saved_addresses_state_to_varchar.sql` | Altera coluna `state` de `CHAR(2)` para `VARCHAR(2)` — necessário para compatibilidade com Hibernate |

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

*All Set — Projeto Integrador 1 — UNIFOR — 2026*
