# All Set — Decisões Técnicas de Stack e Dependências

> Documento de referência para a equipe de desenvolvimento.  
> Contexto: backend da plataforma All Set, marketplace de serviços autônomos.

---

## Configuração Base

### Spring Boot 3.5.11

Escolha da linha **3.x** em detrimento da 4.0.3 por razões de maturidade de ecossistema.

O Spring Boot 4.0 introduz Spring Framework 7, lançado em novembro de 2025. Apesar de ser uma versão GA (sem sufixo SNAPSHOT ou Milestone), o ecossistema de bibliotecas de terceiros — drivers de banco, SDKs de gateways de pagamento, integrações com Redis e afins — ainda está em processo de adaptação para essa versão principal.

Para um projeto que integra múltiplos serviços externos críticos (pagamento via escrow, Redis, PostgreSQL, WebSocket), estabilidade de dependências transitivas é um requisito não-funcional tão importante quanto as features da linguagem. A linha **3.5.x é a LTS ativa** com o maior ecossistema consolidado hoje.

### Java 21

Java 21 é a versão **LTS atual** (Long-Term Support), com suporte garantido pela Oracle e pela comunidade até 2031.

Principais recursos relevantes para o All Set:

- **Virtual Threads (Project Loom)**: reduzem drasticamente o custo de threads bloqueantes. Crítico para um backend com WebSocket (chat em tempo real), chamadas síncronas a APIs externas de pagamento e consultas ao banco — tudo isso concorrentemente.
- **Records**: modelagem imutável de DTOs e value objects com menos boilerplate.
- **Sealed Classes + Pattern Matching**: modelagem de domínio mais expressiva (ex: estados de um pedido — `Pending`, `InProgress`, `Completed`, `Disputed`).

Evitar Java 17 (LTS anterior) significa não abrir mão de recursos que melhoram diretamente a qualidade do código de produção.

### Maven

Gerenciador de build com convenção sobre configuração, repositório central consolidado (Maven Central) e excelente suporte no ecossistema Spring (o próprio Spring Initializr e a documentação oficial são orientados a Maven). Para equipes menores e projetos em fase de MVP, a verbosidade do `pom.xml` é compensada pela previsibilidade e facilidade de integração com pipelines CI/CD.

### Packaging: JAR

O JAR com Tomcat embarcado é o padrão para deploy em containers Docker. Elimina dependência de servidor de aplicação externo, simplifica o processo de build e é compatível com qualquer orquestrador (Kubernetes, ECS, Railway, Render, etc.).

### Configuração: YAML

O formato YAML é superior ao `.properties` para configurações com estrutura hierárquica. Perfis de ambiente (`spring.profiles`), configuração de datasource, JWT, Redis e integrações externas ficam significativamente mais legíveis em YAML. Exemplo direto:

```yaml
# YAML — legível
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/allset
    username: ${DB_USER}
    password: ${DB_PASS}

# .properties — equivalente, mais verboso
spring.datasource.url=jdbc:postgresql://localhost:5432/allset
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASS}
```

---

## Dependências

### Spring Web

**Categoria:** Web

Núcleo do projeto. Habilita Spring MVC com suporte a REST (via `@RestController`, `@RequestMapping`, etc.) e usa Apache Tomcat como servidor embarcado padrão.

Toda a API do All Set — cadastro de usuários, listagem de serviços, contratação, avaliações — é exposta via REST sobre essa dependência.

---

### Spring Data JPA

**Categoria:** SQL / Persistência

Abstração sobre JPA (Java Persistence API) com Hibernate como provider padrão. Habilita:

- Repositórios com queries derivadas do nome do método (`findByStatusAndClientId`)
- Suporte a transações declarativas (`@Transactional`)
- Mapeamento objeto-relacional com `@Entity`, `@OneToMany`, `@ManyToOne`, etc.

Domínio do All Set é fortemente relacional: `User`, `ServiceProvider`, `ServiceOrder`, `Payment`, `Review`, `Chat` — todos com relacionamentos bem definidos que se beneficiam de um ORM maduro.

**Trade-off consciente:** JPA é bloqueante por natureza. Para o MVP isso é adequado. Se no futuro o volume de requisições justificar reatividade completa, a migração seria para Spring Data R2DBC — uma mudança arquitetural significativa que não faz sentido antecipar.

---

### PostgreSQL Driver

**Categoria:** SQL / Persistência

Driver JDBC para conexão com PostgreSQL. O PostgreSQL é a escolha correta para o All Set por:

- **Suporte transacional robusto (ACID)**: indispensável para operações financeiras como escrow e split de pagamento.
- **JSONB**: útil para armazenar dados semi-estruturados (metadados de serviços, configurações de profissionais) sem overhead de tabelas extras.
- **Maturidade e custo zero**: amplamente suportado em todos os provedores de nuvem (AWS RDS, Supabase, Neon, Railway).

O driver cobre também R2DBC (reativo), mas no contexto desta stack com JPA, apenas o JDBC será utilizado.

---

### Flyway Migration

**Categoria:** SQL / Migrations

Controle de versão do schema de banco de dados. Cada alteração estrutural no banco (criação de tabela, adição de coluna, índice novo) é representada como um arquivo SQL versionado (`V1__create_users.sql`, `V2__create_service_orders.sql`, etc.).

Para o All Set, isso é crítico porque:

- O schema vai evoluir continuamente (MVP → features completas).
- Múltiplos desenvolvedores trabalham no mesmo banco.
- Ambientes de staging e produção precisam estar sempre sincronizados com o código.

Sem Flyway (ou Liquibase), migrações viram processos manuais propensos a erro — especialmente perigoso em tabelas que envolvem dados financeiros.

---

### Spring Data Redis (Access + Driver)

**Categoria:** NoSQL / Cache

Integração com Redis via Lettuce (cliente assíncrono e thread-safe). Casos de uso diretos no All Set:

- **Cache de sessão / tokens JWT**: evita consultas repetidas ao banco para validação de autenticação.
- **Rate limiting**: limitar tentativas de login, envio de mensagens ou criação de pedidos por IP/usuário.
- **Pub/Sub para chat**: o Redis pode atuar como message broker entre instâncias do servidor para o sistema de chat em tempo real (necessário quando há mais de uma instância rodando).
- **Cache de dados quentes**: categorias de serviço, configurações de planos — dados que mudam raramente mas são consultados com alta frequência.

---

### Spring Security

**Categoria:** Segurança

Framework de autenticação e autorização do ecossistema Spring. Base para:

- Proteção de endpoints com regras de acesso por perfil (`CLIENT`, `PROVIDER`, `ADMIN`).
- Integração com JWT (via OAuth2 Resource Server).
- CSRF protection, CORS, password encoding com BCrypt.

No All Set, a separação de perfis é um requisito funcional central: clientes e prestadores têm jornadas completamente distintas na plataforma.

---

### OAuth2 Resource Server

**Categoria:** Segurança

Habilita o Spring Security para atuar como **Resource Server** no fluxo OAuth2, validando JWTs recebidos nas requisições.

Para uma plataforma mobile-first como o All Set, autenticação **stateless via JWT** é a abordagem correta — o token carrega as claims do usuário (id, perfil, permissões), elimina a necessidade de sessão no servidor e escala horizontalmente sem fricção.

Essa dependência provê o filtro que intercepta o header `Authorization: Bearer <token>` e valida assinatura, expiração e claims automaticamente.

---

### Validation

**Categoria:** I/O

Bean Validation (JSR-380) com Hibernate Validator como implementação de referência. Habilita anotações como `@NotNull`, `@Email`, `@Size`, `@Pattern` diretamente nos DTOs de entrada.

No All Set, validação de entrada é especialmente relevante em cadastros (CPF, dados bancários, documentos de verificação) e em criação de pedidos de serviço (valores, datas, categorias). Centralizar essa validação na camada de DTO, antes de qualquer lógica de negócio, é uma boa prática que reduz código defensivo espalhado pela aplicação.

---

### Spring Boot Actuator

**Categoria:** Operacional / Observabilidade

Expõe endpoints de gestão e monitoramento da aplicação:

- `/actuator/health` — verificação de saúde (usado por load balancers e Kubernetes liveness/readiness probes).
- `/actuator/metrics` — métricas de JVM, HTTP, banco e cache (integrável com Prometheus + Grafana).
- `/actuator/info` — informações da build e versão deployada.

Para infraestrutura em nuvem com múltiplas instâncias, Actuator deixa de ser opcional e passa a ser requisito operacional. Configure os endpoints com segurança — nunca os exponha publicamente sem autenticação.

---

### Lombok

**Categoria:** Developer Tools

Processador de anotações que elimina boilerplate em tempo de compilação. No contexto do All Set:

- `@Getter` / `@Setter` / `@ToString` / `@EqualsAndHashCode` — em entidades e DTOs.
- `@Builder` — para construção fluente de objetos de domínio.
- `@RequiredArgsConstructor` — injeção de dependência via construtor sem código manual.
- `@Slf4j` — instância de logger sem declaração manual.

**Atenção:** Lombok funciona via annotation processing. Certifique-se de que o plugin está instalado na IDE (IntelliJ: `Lombok Plugin`; habilitar annotation processing nas configurações do projeto).

---

### WebSocket

**Categoria:** Messaging

Habilita comunicação bidirecional em tempo real com suporte a **STOMP** (Simple Text Oriented Messaging Protocol) e **SockJS** (fallback para ambientes que não suportam WebSocket nativo).

No All Set, é a base técnica do **chat interno** — funcionalidade com regras de negócio específicas: o canal só é liberado após confirmação de pagamento (custódia). A stack STOMP + Spring Message Broker permite:

- Tópicos por conversa (`/topic/chat/{orderId}`)
- Controle de autorização por canal
- Integração com Redis Pub/Sub para escalar o chat entre múltiplas instâncias do servidor

"Servlet-based" indica compatibilidade com a stack Spring MVC + Tomcat desta aplicação (em oposição ao WebFlux reativo).

---

### Testcontainers

**Categoria:** Testes

Sobe instâncias reais de PostgreSQL e Redis em containers Docker durante a execução dos testes de integração. Elimina o problema de mocks frágeis para a camada de persistência — você testa contra o banco real, com o schema real aplicado pelo Flyway.

Para o All Set, testes de integração são especialmente importantes nas operações financeiras (criação de escrow, split, liberação de pagamento) onde o comportamento transacional do banco precisa ser verificado de ponta a ponta.

**Pré-requisito:** Docker instalado e rodando na máquina de desenvolvimento e no ambiente de CI.

---

## Dependências fora do Initializr (adicionar manualmente ao `pom.xml`)

As integrações abaixo não estão no Initializr mas serão necessárias conforme o projeto evolui:

| Dependência | Finalidade |
|---|---|
| SDK do gateway de pagamento (Pagar.me, Asaas, Stripe) | Escrow e split automático |
| `java-jwt` ou `nimbus-jose-jwt` | Geração e assinatura de JWTs |
| `spring-kafka` ou `spring-amqp` | Processamento assíncrono de eventos (notificações, liberação de escrow) |
| `micrometer-registry-prometheus` | Exportação de métricas para Prometheus |

---

## Resumo das Escolhas

| Decisão | Escolha | Alternativa Descartada | Motivo |
|---|---|---|---|
| Spring Boot | 3.5.11 | 4.0.3 | Ecossistema mais maduro para integrações externas |
| Java | 21 | 17 | LTS atual, Virtual Threads, records |
| Build | Maven | Gradle | Convenção consolidada, CI/CD simples |
| Banco | PostgreSQL | MySQL | ACID robusto, JSONB, suporte transacional |
| Auth | JWT stateless | Session-based | Compatibilidade mobile-first |
| Migrations | Flyway | Liquibase | Mais simples para equipes menores |
| WebSocket stack | STOMP + SockJS | SSE / Long Polling | Bidirecional, escalável com Redis Pub/Sub |

---

*Documento gerado em março de 2026 — All Set / Projeto Integrador 1 — UNIFOR*