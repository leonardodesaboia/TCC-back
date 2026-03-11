# All Set — Estrutura de Pastas

> Arquitetura Layered organizada por módulo/feature.
> Cada domínio (ex: `user`, `order`, `payment`) replica a estrutura do `boilerplate`.

---

## Visão Geral

```
src/main/java/com/allset/api/
│
├── AllsetApiApplication.java       # Entry point — @SpringBootApplication
│
├── config/                         # Configurações globais do Spring
│   └── SecurityConfig.java
│
├── shared/                         # Código compartilhado entre módulos
│   └── exception/
│       ├── ApiError.java           # Record com o formato padrão de erro da API
│       └── GlobalExceptionHandler.java
│
└── {modulo}/                       # Um pacote por domínio (ver seção abaixo)
    ├── controller/
    ├── service/
    ├── repository/
    ├── domain/
    └── dto/

src/main/resources/
├── application.yml                 # Configuração da aplicação (perfis dev e prod)
└── db/migration/                   # Migrations do Flyway (SQL versionado)
    └── V1__init.sql
```

---

## Módulos de Domínio

Cada feature do sistema vive em seu próprio pacote, seguindo sempre a mesma estrutura interna:

```
{modulo}/
├── controller/     Recebe requisições HTTP (@RestController)
├── service/        Contém a lógica de negócio (@Service)
├── repository/     Acesso ao banco de dados (Spring Data JPA)
├── domain/         Entidade JPA (@Entity)
└── dto/            Objetos de entrada e saída da API (Records)
```

**Exemplo — módulo `user`:**

```
user/
├── controller/UserController.java
├── service/UserService.java
├── repository/UserRepository.java
├── domain/User.java
└── dto/
    ├── CreateUserRequest.java
    └── UserResponse.java
```

O pacote `boilerplate/` dentro do projeto serve como referência visual dessa estrutura — não contém lógica de negócio.

---

## config/

Configurações de infraestrutura do Spring que se aplicam à aplicação inteira.

| Arquivo | Responsabilidade |
|---|---|
| `SecurityConfig.java` | Spring Security: CORS, CSRF, JWT, regras de acesso por endpoint |

Adicionar aqui conforme necessário: `RedisConfig.java`, `WebSocketConfig.java`, etc.

---

## shared/

Código utilitário reutilizado por mais de um módulo. **Não contém lógica de negócio.**

| Arquivo | Responsabilidade |
|---|---|
| `exception/ApiError.java` | Record com o contrato de resposta de erro (`status`, `message`, `fields`, `timestamp`) |
| `exception/GlobalExceptionHandler.java` | `@RestControllerAdvice` — captura exceções e retorna `ApiError` padronizado |

---

## resources/

### application.yml

Configuração centralizada com dois perfis:

| Perfil | Ativação | Uso |
|---|---|---|
| `dev` | `-Dspring.profiles.active=dev` | Desenvolvimento local — variáveis com fallback (`:postgres`, `:localhost`) |
| `prod` | `-Dspring.profiles.active=prod` | Produção — todas as variáveis obrigatórias via environment |

### db/migration/

Migrations do Flyway em SQL puro. Nomenclatura obrigatória: `V{n}__{descricao}.sql`.

```
V1__init.sql
V2__create_users.sql
V3__create_service_orders.sql
```

Cada arquivo representa uma alteração incremental e irreversível no schema. Nunca edite uma migration já aplicada.

---

## Fluxo de uma requisição

```
HTTP Request
    └── Controller          (recebe e valida o DTO de entrada)
        └── Service         (executa a lógica de negócio)
            └── Repository  (lê/escreve no banco via JPA)
                └── Domain  (entidade mapeada para a tabela)
    └── DTO de saída        (retornado pelo Controller)
```

Erros em qualquer camada são capturados pelo `GlobalExceptionHandler` e retornam um `ApiError` padronizado.
