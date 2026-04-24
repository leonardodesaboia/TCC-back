# Chat — Documentação Técnica

Módulo de comunicação em tempo real entre cliente e profissional dentro de um pedido aceito.

---

## Índice

1. [Visão geral](#1-visão-geral)
2. [Quando o chat é criado](#2-quando-o-chat-é-criado)
3. [Modelo de dados](#3-modelo-de-dados)
4. [Schema no banco de dados](#4-schema-no-banco-de-dados)
5. [Arquitetura híbrida: REST + WebSocket](#5-arquitetura-híbrida-rest--websocket)
6. [Autenticação e autorização](#6-autenticação-e-autorização)
7. [Fluxo de envio de mensagem](#7-fluxo-de-envio-de-mensagem)
8. [Delivery receipt e read receipt](#8-delivery-receipt-e-read-receipt)
9. [Mensagens de sistema](#9-mensagens-de-sistema)
10. [Endpoints REST](#10-endpoints-rest)
11. [Protocolo WebSocket (STOMP)](#11-protocolo-websocket-stomp)
12. [Estrutura de pacotes](#12-estrutura-de-pacotes)
13. [Variáveis de ambiente](#13-variáveis-de-ambiente)
14. [Limitações e decisões de design](#14-limitações-e-decisões-de-design)
15. [Fases futuras](#15-fases-futuras)

---

## 1. Visão geral

Cada pedido aceito (`order.status = accepted`) gera automaticamente **uma única conversa** entre o cliente e o profissional. A conversa persiste para sempre — ela serve como histórico e como evidência em caso de disputa.

```
Order (accepted)
    └── Conversation (1:1)
            └── Message[]
```

Regras fundamentais:

- Uma conversa só existe se houver um pedido aceito associado — não é possível criar conversas avulsas.
- Somente os dois participantes (cliente e profissional) podem ler e escrever na conversa.
- Mensagens **nunca são deletadas fisicamente** — `deleted_at` existe na coluna mas sempre permanece `NULL`.
- O histórico é acessível para sempre, mesmo após a conclusão ou cancelamento do pedido.

---

## 2. Quando o chat é criado

A criação ocorre **automaticamente** dentro do fluxo Express, no método `OrderServiceImpl.clientRespond` — o momento em que o cliente escolhe a proposta de um profissional e o pedido transita para `accepted`.

```
POST /api/v1/orders/{id}/client-respond
    └── OrderServiceImpl.clientRespond()
            ├── order.status = accepted
            ├── ConversationService.createForOrder(order)   ← cria a conversa
            └── MessageService.sendSystemMessage(...)        ← envia mensagem inicial
```

A mensagem inicial enviada é: _"Pedido aceito. Vocês podem conversar por aqui."_

Não existe endpoint `POST /api/v1/conversations` — a criação é exclusivamente interna.

---

## 3. Modelo de dados

### Tabela `conversations`

| Coluna                | Tipo        | Descrição |
|-----------------------|-------------|-----------|
| `id`                  | `UUID`      | PK gerada pelo banco |
| `order_id`            | `UUID`      | FK para `orders(id)` — UNIQUE (1 conversa por pedido) |
| `client_id`           | `UUID`      | FK para `users(id)` — ID do usuário cliente |
| `professional_user_id`| `UUID`      | FK para `users(id)` — `users.id` do profissional (não `professionals.id`) |
| `created_at`          | `TIMESTAMPTZ` | Gerado automaticamente |
| `updated_at`          | `TIMESTAMPTZ` | Gerado automaticamente |
| `deleted_at`          | `TIMESTAMPTZ` | Sempre `NULL` (coluna de convenção) |

> **Por que `professional_user_id` e não `professional_id`?**
> `orders.professional_id` aponta para `professionals(id)`. Para autorização, é necessário o `users.id` do profissional. Resolver esse join a cada request seria custoso e acoplaria o módulo de chat ao schema interno do módulo professional. Por isso, o `users.id` é **denormalizado** no momento da criação da conversa.

### Tabela `messages`

| Coluna                  | Tipo        | Descrição |
|-------------------------|-------------|-----------|
| `id`                    | `UUID`      | PK gerada pelo banco |
| `conversation_id`       | `UUID`      | FK para `conversations(id)` |
| `sender_id`             | `UUID`      | FK para `users(id)` — **nullable** (NULL = mensagem de sistema) |
| `msg_type`              | `msg_type`  | Enum: `text`, `image`, `system` |
| `content`               | `TEXT`      | Conteúdo textual (obrigatório para `text` e `system`) |
| `attachment_url`        | `TEXT`      | URL do anexo (fase 2 — `image`) |
| `attachment_size_bytes` | `INT`       | Tamanho do anexo em bytes (fase 2) |
| `attachment_mime_type`  | `VARCHAR(64)` | MIME type do anexo (fase 2) |
| `sent_at`               | `TIMESTAMPTZ` | Momento do envio — imutável |
| `delivered_at`          | `TIMESTAMPTZ` | Preenchido quando o destinatário recebe via WebSocket |
| `read_at`               | `TIMESTAMPTZ` | Preenchido quando o destinatário chama `PATCH /read` |
| `created_at`            | `TIMESTAMPTZ` | Gerado automaticamente |
| `updated_at`            | `TIMESTAMPTZ` | Gerado automaticamente |
| `deleted_at`            | `TIMESTAMPTZ` | Sempre `NULL` |

### CHECK constraint de coerência de payload

O banco garante que o payload é coerente com o tipo da mensagem:

```sql
CONSTRAINT messages_payload_check CHECK (
    (msg_type = 'text'   AND content IS NOT NULL AND sender_id IS NOT NULL) OR
    (msg_type = 'system' AND content IS NOT NULL AND sender_id IS NULL)     OR
    (msg_type = 'image'  AND attachment_url IS NOT NULL AND sender_id IS NOT NULL)
)
```

Isso é a última barreira — impede inconsistências mesmo se a validação da camada de serviço falhar.

### Índices

```sql
-- Listagem de conversas de um usuário
idx_conversations_client_id
idx_conversations_professional_user_id

-- Histórico paginado de mensagens (query dominante)
idx_messages_conversation_sent_at ON messages(conversation_id, sent_at DESC)
```

---

## 4. Schema no banco de dados

Migration aplicada: **`V14__create_chat.sql`**

### Enum `msg_type`

```sql
CREATE TYPE msg_type AS ENUM ('text', 'image', 'system');
```

Segue o padrão lowercase do projeto (igual a `order_status`, `verification_status`, etc.).

---

### Tabela `conversations`

```sql
CREATE TABLE conversations (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id             UUID NOT NULL UNIQUE REFERENCES orders(id),
    client_id            UUID NOT NULL REFERENCES users(id),
    professional_user_id UUID NOT NULL REFERENCES users(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at           TIMESTAMPTZ
);
```

**Chaves estrangeiras:**

| Coluna | Referencia | Constraint |
|---|---|---|
| `order_id` | `orders(id)` | `NOT NULL UNIQUE` — garante no banco que só existe 1 conversa por pedido |
| `client_id` | `users(id)` | `NOT NULL` |
| `professional_user_id` | `users(id)` | `NOT NULL` — armazena `users.id`, não `professionals.id` |

**Índices:**

```sql
CREATE INDEX idx_conversations_client_id            ON conversations(client_id);
CREATE INDEX idx_conversations_professional_user_id ON conversations(professional_user_id);
```

Otimizam a query mais frequente: `GET /conversations` (busca todas as conversas onde o usuário é cliente ou profissional).

---

### Tabela `messages`

```sql
CREATE TABLE messages (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id       UUID NOT NULL REFERENCES conversations(id),
    sender_id             UUID REFERENCES users(id),
    msg_type              msg_type NOT NULL DEFAULT 'text',
    content               TEXT,
    attachment_url        TEXT,
    attachment_size_bytes INT,
    attachment_mime_type  VARCHAR(64),
    sent_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at          TIMESTAMPTZ,
    read_at               TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at            TIMESTAMPTZ,

    CONSTRAINT messages_payload_check CHECK (
        (msg_type = 'text'   AND content IS NOT NULL AND sender_id IS NOT NULL) OR
        (msg_type = 'system' AND content IS NOT NULL AND sender_id IS NULL)     OR
        (msg_type = 'image'  AND attachment_url IS NOT NULL AND sender_id IS NOT NULL)
    )
);
```

**Chaves estrangeiras:**

| Coluna | Referencia | Constraint |
|---|---|---|
| `conversation_id` | `conversations(id)` | `NOT NULL` |
| `sender_id` | `users(id)` | nullable — `NULL` para mensagens de sistema |

**Colunas notáveis:**

| Coluna | Obrigatoriedade | Observação |
|---|---|---|
| `sender_id` | Nullable | `NULL` exclusivamente para `msg_type = 'system'` |
| `content` | Condicional | Obrigatório para `text` e `system`; `NULL` para `image` |
| `attachment_url` | Condicional | Obrigatório apenas para `image` (fase 2) |
| `attachment_size_bytes` | Opcional | Para auditoria de uploads (moderação, debug) |
| `attachment_mime_type` | Opcional | Valor esperado na fase 2: `image/jpeg`, `image/png` |
| `sent_at` | `NOT NULL` | Imutável — definido no insert, nunca alterado |
| `delivered_at` | Nullable | Preenchido por bulk `UPDATE` via `MessageRepository.markAllAsDelivered` |
| `read_at` | Nullable | Preenchido por bulk `UPDATE` via `MessageRepository.markAllAsRead` |
| `deleted_at` | Nullable | Sempre `NULL` — mensagens nunca são apagadas (evidência em disputas) |

**CHECK constraint `messages_payload_check`:**

Garante coerência entre `msg_type` e o payload no nível do banco — a última barreira caso a validação da camada de serviço falhe:

| `msg_type` | `content` | `sender_id` | `attachment_url` |
|---|---|---|---|
| `text` | `NOT NULL` | `NOT NULL` | qualquer |
| `system` | `NOT NULL` | `NULL` | qualquer |
| `image` | qualquer | `NOT NULL` | `NOT NULL` |

**Índice:**

```sql
CREATE INDEX idx_messages_conversation_sent_at
    ON messages(conversation_id, sent_at DESC);
```

Índice composto que cobre a query dominante — histórico paginado de uma conversa ordenado por `sent_at DESC` — sem `filesort`.

---

### Diagrama de relacionamentos

```
users ──────────────────────────────────────────────┐
  │                                                  │
  │ client_id                  professional_user_id  │
  ▼                                                  ▼
conversations ◄──────────────────────────────────── orders
  │  (order_id UNIQUE)
  │
  │ conversation_id
  ▼
messages
  │
  │ sender_id (nullable)
  ▼
users
```

---

## 5. Arquitetura híbrida: REST + WebSocket

O módulo usa uma arquitetura **híbrida intencional**:

```
Cliente                       Servidor
  │                               │
  │── POST /messages ────────────>│  (HTTP — escrita)
  │                               │  1. Valida e persiste a mensagem (@Transactional)
  │                               │  2. Publica MessageSentEvent (dentro da tx)
  │<── 201 MessageResponse ───────│  3. Retorna ao caller
  │                               │
  │                               │  (AFTER_COMMIT — fora da tx)
  │                               │  4. MessageBroadcastListener.handle()
  │<── /topic/conversations/{id} ─│  5. Broadcast via SimpMessagingTemplate
```

**Por que não enviar diretamente via WebSocket (STOMP `@MessageMapping`)?**

- Validação, autorização e regras de negócio ficam no fluxo HTTP — bem testado e com tratamento de erro padronizado (`ApiError`).
- O `@TransactionalEventListener(phase = AFTER_COMMIT)` garante que o broadcast só acontece após commit bem-sucedido. Se a transação der rollback (ex: constraint violation), nenhuma mensagem fantasma é publicada via WebSocket.
- STOMP vira **pipe de leitura** para o cliente — sem lógica de escrita no protocolo WebSocket.

---

## 6. Autenticação e autorização

### REST

Usa o mesmo JWT Bearer da API (`Authorization: Bearer <token>`). O `@CurrentUser UUID currentUserId` nos controllers extrai o `sub` do JWT. Todos os endpoints de chat requerem autenticação.

### WebSocket (STOMP)

O JWT é enviado no **header STOMP do frame `CONNECT`**, não em query string (query strings ficam em logs de proxy/balancer):

```
CONNECT
Authorization:Bearer eyJhbGci...
```

O `StompAuthChannelInterceptor` intercepta o `CONNECT`, valida o JWT via `JwtDecoder` (mesmo bean do Spring Security), e popula o `Principal` da sessão WebSocket com o `UUID` do usuário.

### Autorização por conversa

`StompSubscriptionInterceptor` intercepta cada `SUBSCRIBE` para `/topic/conversations/{id}` e verifica via banco se o usuário conectado é participante daquela conversa. Se não for, envia um frame STOMP `ERROR` e rejeita a inscrição.

**Padrão de ownership:** seguindo o padrão do projeto, qualquer acesso inválido (conversa inexistente OU usuário não participante) retorna **404** — nunca 403. Isso evita que um atacante confirme a existência de uma conversa apenas testando o código de resposta.

---

## 7. Fluxo de envio de mensagem

### Sequência completa

```
[Cliente] POST /api/v1/conversations/{id}/messages
    │
    ▼
ConversationController.sendMessage()
    │
    ▼
MessageServiceImpl.sendText()
    ├── conversationService.requireParticipant()  → 404 se não for participante
    ├── Message.builder()...build()
    ├── messageRepository.save(message)           → @Transactional
    └── eventPublisher.publishEvent(MessageSentEvent)
    │
    ▼
← 201 MessageResponse (imediato, antes do broadcast)
    │
    ▼  (AFTER_COMMIT — nova transação REQUIRES_NEW)
MessageBroadcastListener.handle(MessageSentEvent)
    ├── messageRepository.findById(messageId)
    ├── conversationRepository.findById(conversationId)
    ├── broadcaster.publishMessage()
    │       └── SimpMessagingTemplate.convertAndSend("/topic/conversations/{id}", messageResponse)
    └── [se destinatário online via SimpUserRegistry]
            ├── messageRepository.markAllAsDelivered()
            └── broadcaster.publishDeliveryReceipt()
```

### Por que `REQUIRES_NEW` no listener?

O listener precisa da mensagem já **commitada** no banco para buscá-la. A transação original já fechou (AFTER_COMMIT), então o listener abre uma nova transação. Se o broadcast falhar (ex: WebSocket caiu), a mensagem continua persistida — o cliente consegue recuperá-la via `GET /messages` no próximo request.

---

## 8. Delivery receipt e read receipt

### Delivery receipt (entrega)

Indica que a mensagem chegou ao dispositivo do destinatário.

**Quando é marcado:** logo após o broadcast, se o destinatário estiver com WebSocket conectado (verificado via `SimpUserRegistry.getUser(userId)`). Um bulk `UPDATE` marca todas as mensagens não entregues da conversa de uma vez.

**Payload enviado no tópico:**
```json
{
  "eventType": "DELIVERY_RECEIPT",
  "conversationId": "uuid",
  "receiverUserId": "uuid",
  "deliveredAt": "2026-04-09T14:00:00Z",
  "affectedCount": 3
}
```

### Read receipt (leitura)

Indica que o usuário efetivamente leu as mensagens.

**Quando é marcado:** chamada explícita do cliente ao endpoint `PATCH /api/v1/conversations/{id}/read`.

**Payload enviado no tópico:**
```json
{
  "eventType": "READ_RECEIPT",
  "conversationId": "uuid",
  "readerUserId": "uuid",
  "readAt": "2026-04-09T14:05:00Z",
  "affectedCount": 5
}
```

O campo `eventType` permite que o frontend diferencie `MessageResponse` de receipts — todos trafegam no mesmo tópico `/topic/conversations/{id}`.

---

## 9. Mensagens de sistema

Mensagens de sistema são geradas pelo servidor em transições de estado do pedido. Elas aparecem inline no histórico da conversa, sem remetente.

| `sender_id` | `msg_type` | Caso de uso |
|-------------|------------|-------------|
| `NULL`      | `system`   | Transições de pedido, avisos automáticos |
| UUID        | `text`     | Mensagem enviada pelo usuário |
| UUID        | `image`    | Foto enviada pelo usuário (fase 2) |

**Mensagens de sistema implementadas:**

| Evento | Mensagem |
|--------|----------|
| Order → `accepted` | "Pedido aceito. Vocês podem conversar por aqui." |

**Mensagens de sistema planejadas (fases futuras):**

| Evento | Mensagem sugerida |
|--------|-------------------|
| Order → `completed_by_pro` | "Profissional marcou o serviço como concluído." |
| Order → `completed` | "Serviço concluído. Obrigado por usar o All Set!" |
| Order → `cancelled` | "Pedido cancelado." |
| Order → `disputed` | "Disputa aberta. A equipe All Set irá analisar." |

---

## 10. Endpoints REST

Prefixo: `/api/v1/conversations`. Todos os endpoints exigem autenticação.

### `GET /api/v1/conversations`

Lista paginada de conversas do usuário autenticado.

**Query params:** `page`, `size`, `sort` (padrão: `createdAt,desc`)

**Response `200`:**
```json
{
  "content": [
    {
      "id": "uuid",
      "orderId": "uuid",
      "otherParticipantId": "uuid",
      "lastMessage": {
        "id": "uuid",
        "conversationId": "uuid",
        "senderId": "uuid",
        "msgType": "text",
        "content": "Chego em 10 minutos",
        "attachmentUrl": null,
        "sentAt": "2026-04-09T14:00:00Z",
        "deliveredAt": "2026-04-09T14:00:01Z",
        "readAt": "2026-04-09T14:00:05Z"
      },
      "unreadCount": 2
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

---

### `GET /api/v1/conversations/{id}`

Retorna dados de uma conversa específica. Retorna `404` se não existir ou se o usuário não for participante.

**Response `200`:**
```json
{
  "id": "uuid",
  "orderId": "uuid",
  "clientId": "uuid",
  "professionalUserId": "uuid",
  "createdAt": "2026-04-09T13:55:00Z"
}
```

---

### `GET /api/v1/conversations/{id}/messages`

Histórico paginado de mensagens.

**Query params:** `page`, `size` (padrão: `50`), `sort` (padrão: `sentAt,desc`)

**Response `200`:**
```json
{
  "content": [
    {
      "id": "uuid",
      "conversationId": "uuid",
      "senderId": null,
      "msgType": "system",
      "content": "Pedido aceito. Vocês podem conversar por aqui.",
      "attachmentUrl": null,
      "sentAt": "2026-04-09T13:55:00Z",
      "deliveredAt": null,
      "readAt": null
    }
  ]
}
```

---

### `POST /api/v1/conversations/{id}/messages`

Envia uma mensagem de texto.

**Request body:**
```json
{
  "content": "Chego em 10 minutos"
}
```

**Validações:**
- `content`: obrigatório, máx. `CHAT_MESSAGE_MAX_LENGTH` caracteres (padrão: 4000)

**Response `201`:** `MessageResponse` (ver estrutura acima)

Após o commit, a mensagem é publicada automaticamente em `/topic/conversations/{id}`.

---

### `PATCH /api/v1/conversations/{id}/read`

Marca como lidas todas as mensagens do outro participante ainda não lidas.

**Response `200`:**
```json
{
  "eventType": "READ_RECEIPT",
  "conversationId": "uuid",
  "readerUserId": "uuid",
  "readAt": "2026-04-09T14:05:00Z",
  "affectedCount": 3
}
```

O `ReadReceiptEvent` também é publicado no tópico STOMP da conversa.

---

## 11. Protocolo WebSocket (STOMP)

### Conexão

**Endpoint HTTP do handshake:** `ws://host/ws` (com fallback SockJS em `http://host/ws`)

```
CONNECT
host:localhost
Authorization:Bearer eyJhbGci...
```

Em caso de token ausente, inválido, expirado, ou sem claim `role`, o servidor retorna um frame `ERROR` e encerra a conexão.

### Inscrição em uma conversa

```
SUBSCRIBE
id:sub-0
destination:/topic/conversations/{conversationId}
```

O servidor valida que o usuário autenticado é participante da conversa. Se não for, retorna frame `ERROR`.

### Tópico da conversa

Todos os eventos de uma conversa trafegam no mesmo tópico: `/topic/conversations/{conversationId}`.

O campo `eventType` (ou a ausência dele) diferencia os tipos de payload:

| Payload recebido | `eventType` | Quando ocorre |
|------------------|-------------|---------------|
| `MessageResponse` | ausente | Nova mensagem enviada |
| `ReadReceiptEvent` | `"READ_RECEIPT"` | Outro participante leu as mensagens |
| `DeliveryReceiptEvent` | `"DELIVERY_RECEIPT"` | Mensagens entregues ao dispositivo |

### Exemplo de mensagem recebida no tópico

```json
{
  "id": "uuid",
  "conversationId": "uuid",
  "senderId": "uuid",
  "msgType": "text",
  "content": "Chego em 10 minutos",
  "attachmentUrl": null,
  "sentAt": "2026-04-09T14:00:00Z",
  "deliveredAt": null,
  "readAt": null
}
```

### Fila privada (reservada)

`/user/queue/notifications` — reservada para notificações futuras (ex: "nova mensagem" quando o usuário não está inscrito no tópico da conversa). Não implementada na fase atual.

---

## 12. Estrutura de pacotes

```
chat/
├── controller/
│   └── ConversationController.java        # REST /api/v1/conversations
├── service/
│   ├── ConversationService.java           # Interface — exposta para o módulo order
│   ├── ConversationServiceImpl.java
│   ├── MessageService.java                # Interface — sendSystemMessage exposta para order
│   ├── MessageServiceImpl.java
│   ├── ChatBroadcaster.java               # Encapsula SimpMessagingTemplate
│   └── MessageBroadcastListener.java      # @TransactionalEventListener(AFTER_COMMIT)
├── repository/
│   ├── ConversationRepository.java
│   └── MessageRepository.java
├── domain/
│   ├── Conversation.java
│   ├── Message.java
│   └── MessageType.java                   # enum { text, image, system }
├── dto/
│   ├── SendMessageRequest.java
│   ├── MessageResponse.java
│   ├── ConversationResponse.java
│   ├── ConversationSummaryResponse.java
│   ├── ReadReceiptEvent.java
│   └── DeliveryReceiptEvent.java
├── event/
│   └── MessageSentEvent.java              # Record — domain event para AFTER_COMMIT
├── mapper/
│   ├── ConversationMapper.java
│   └── MessageMapper.java
├── websocket/
│   ├── StompAuthChannelInterceptor.java   # Autentica CONNECT via JWT
│   ├── StompSubscriptionInterceptor.java  # Valida SUBSCRIBE por conversa
│   └── ChatPresenceListener.java          # Logging de connect/disconnect
└── exception/
    ├── ConversationNotFoundException.java  # → 404
    └── MessageContentInvalidException.java # → 400

config/
└── WebSocketConfig.java                   # @EnableWebSocketMessageBroker
```

---

## 13. Variáveis de ambiente

| Variável | Obrigatória | Padrão | Descrição |
|---|---|---|---|
| `FRONTEND_URL` | Não | `*` | Origem permitida para conexões WebSocket. **Definir com domínio exato em produção.** Usar `*` apenas em desenvolvimento. |
| `CHAT_MESSAGE_MAX_LENGTH` | Não | `4000` | Tamanho máximo do conteúdo de uma mensagem de texto |
| `CHAT_MESSAGE_PAGE_SIZE` | Não | `50` | Tamanho padrão da página no histórico de mensagens |

---

## 14. Limitações e decisões de design

### Broker em memória (MVP)

O broker STOMP atual é `enableSimpleBroker` — funciona apenas em instância única. Em um deploy multi-instância (horizontal scaling), mensagens enviadas em uma instância não chegam a clientes conectados em outra.

Para escalar, substituir por `enableStompBrokerRelay` apontando para RabbitMQ ou Redis pub/sub. A mudança é **exclusivamente de configuração** (`WebSocketConfig.java`) — nenhum código de domínio muda.

### JWT não re-validado em sessões longas

O JWT é validado uma única vez no frame `CONNECT`. Uma sessão WebSocket aberta permanece ativa mesmo após o token expirar. Mitigação: o access token tem TTL curto (15 min por padrão); expirar a sessão via disconnect do lado do cliente ao renovar o token.

### Imagens (fase 2)

O tipo `image` está definido no enum e na migration, mas o endpoint de upload (`POST /messages/image`) não foi implementado — depende de `StorageService` + bucket S3. A coluna `attachment_url` existe no banco. Nenhum rework de schema necessário para implementar.

---

## 15. Fases futuras

| Item | Pré-requisito |
|---|---|
| Upload de imagem (`msg_type = image`) | `StorageService` + S3 |
| Mensagens de sistema nas demais transições (`completed_by_pro`, `completed`, `cancelled`, `disputed`) | Nenhum |
| Notificação push quando destinatário está offline | Módulo `notification` + FCM |
| Admin como terceiro participante em disputas | Módulo `dispute` |
| Broker externo (RabbitMQ) para múltiplas instâncias | Decisão de infra |
| Paginação cursor-based no histórico (scroll infinito eficiente) | Offset/limit suficiente até ~10k mensagens por conversa |

---

*All Set — Projeto Integrador 1 — UNIFOR — 2026*
