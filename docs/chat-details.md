# Chat — Conceitos, Tecnologias e Integração Frontend

Documento conceitual que explica **como** e **por que** o chat do AllSet funciona da forma que funciona, quais tecnologias estão envolvidas e o que o frontend precisa fazer para se integrar.

> Para referência de endpoints, payloads e schema do banco, veja [`docs/chat.md`](./chat.md).

---

## Indice

1. [O problema que o chat resolve](#1-o-problema-que-o-chat-resolve)
2. [WebSocket — o que e e por que usar](#2-websocket--o-que-e-e-por-que-usar)
3. [STOMP — protocolo sobre WebSocket](#3-stomp--protocolo-sobre-websocket)
4. [SockJS — fallback para ambientes restritivos](#4-sockjs--fallback-para-ambientes-restritivos)
5. [Arquitetura hibrida: REST + WebSocket](#5-arquitetura-hibrida-rest--websocket)
6. [Pub/Sub e topicos](#6-pubsub-e-topicos)
7. [Autenticacao no WebSocket](#7-autenticacao-no-websocket)
8. [Ciclo de vida de uma mensagem](#8-ciclo-de-vida-de-uma-mensagem)
9. [Receipts: delivered e read](#9-receipts-delivered-e-read)
10. [Mensagens de sistema](#10-mensagens-de-sistema)
11. [Guia de integracao frontend](#11-guia-de-integracao-frontend)
12. [Tratamento de erros e reconexao](#12-tratamento-de-erros-e-reconexao)
13. [Limitacoes atuais e evolucao futura](#13-limitacoes-atuais-e-evolucao-futura)

---

## 1. O problema que o chat resolve

Quando um cliente aceita a proposta de um profissional num pedido Express, ambos precisam se comunicar: combinar detalhes, tirar duvidas, enviar fotos. Essa comunicacao precisa ser:

- **Em tempo real** — ninguem quer ficar recarregando a pagina para ver mensagens novas.
- **Persistente** — as mensagens ficam salvas no banco como historico e servem de evidencia em caso de disputa.
- **Segura** — apenas o cliente e o profissional daquele pedido podem ler e enviar mensagens na conversa.

A solucao classica de "fazer polling a cada X segundos" (o frontend fica perguntando "tem mensagem nova? tem mensagem nova?") desperdiça banda, gera latencia e sobrecarrega o servidor. Por isso usamos **WebSocket**.

---

## 2. WebSocket — o que e e por que usar

### HTTP tradicional: request-response

No modelo HTTP classico, a comunicacao e sempre iniciada pelo cliente:

```
Cliente  →  "GET /mensagens"  →  Servidor
Cliente  ←  [lista de mensagens]  ←  Servidor
```

O servidor **nunca** pode enviar dados por conta propria — ele so responde quando perguntado. Para um chat, isso significa que o frontend teria que ficar perguntando repetidamente se ha mensagens novas (polling), o que e ineficiente.

### WebSocket: conexao bidirecional persistente

WebSocket e um protocolo (RFC 6455) que estabelece uma **conexao persistente e bidirecional** entre cliente e servidor. Apos um handshake HTTP inicial (upgrade), a conexao fica aberta e ambos os lados podem enviar dados a qualquer momento:

```
Cliente  ←→  Conexao persistente  ←→  Servidor

(ambos podem enviar dados a qualquer momento)
```

**Vantagens para chat:**
- **Baixa latencia** — mensagens chegam instantaneamente, sem esperar o proximo ciclo de polling.
- **Eficiencia** — uma unica conexao TCP aberta em vez de dezenas de requests HTTP por minuto.
- **Push do servidor** — o servidor envia a mensagem no momento em que ela e salva, sem o cliente precisar pedir.

**Como funciona o handshake:**

1. O cliente faz uma requisicao HTTP com o header `Upgrade: websocket`.
2. O servidor responde com `101 Switching Protocols`.
3. A partir desse momento, a conexao TCP deixa de falar HTTP e passa a falar o protocolo WebSocket.
4. A conexao fica aberta ate que um dos lados a feche (ou a rede caia).

---

## 3. STOMP — protocolo sobre WebSocket

WebSocket por si so e um "tubo" de bytes — ele nao define como estruturar mensagens, como se inscrever em canais ou como rotear mensagens para destinatarios especificos. E como ter um telefone que permite falar, mas sem nenhuma convencao de como iniciar ou encerrar a conversa.

**STOMP** (Simple Text-Oriented Messaging Protocol) e um protocolo de mensageria que roda **sobre** WebSocket e adiciona essa estrutura. Ele define:

- **Frames** com comandos: `CONNECT`, `SUBSCRIBE`, `SEND`, `MESSAGE`, `DISCONNECT`, etc.
- **Destinos (destinations):** strings como `/topic/conversations/abc-123` que funcionam como "canais".
- **Headers:** metadados em cada frame (tipo content-type, ID da subscription, etc.).

### Por que STOMP e nao WebSocket puro?

| Aspecto | WebSocket puro | WebSocket + STOMP |
|---|---|---|
| Roteamento | Voce implementa na mao | Destinations e subscriptions prontos |
| Broadcast | Voce implementa na mao | O broker distribui para todos os inscritos |
| Integracao Spring | Baixo nivel (WebSocketHandler) | `@MessageMapping`, `SimpMessagingTemplate`, interceptors |
| Bibliotecas frontend | Voce parseia bytes | `@stomp/stompjs` faz tudo |

O Spring Boot tem suporte nativo a STOMP sobre WebSocket via `@EnableWebSocketMessageBroker`. Isso nos da um mini message broker embutido que gerencia subscriptions e distribui mensagens para os clientes conectados.

### Anatomia de um frame STOMP

```
COMMAND
header1:value1
header2:value2

body^@
```

Exemplo de um frame `MESSAGE` que o servidor envia ao frontend:

```
MESSAGE
destination:/topic/conversations/550e8400-e29b-41d4-a716-446655440000
content-type:application/json

{"id":"...","senderId":"...","content":"Ola!","msgType":"text","sentAt":"2026-04-12T10:30:00Z"}^@
```

---

## 4. SockJS — fallback para ambientes restritivos

Nem todo ambiente suporta WebSocket nativamente. Proxies corporativos, firewalls antigos e algumas CDNs podem bloquear conexoes WebSocket ou cortar conexoes idle.

**SockJS** e uma biblioteca que tenta conectar via WebSocket primeiro e, se falhar, cai automaticamente para transports alternativos (HTTP streaming, long-polling). Para o codigo do frontend, a API e a mesma — a troca de transport e transparente.

No AllSet, o endpoint de handshake (`/ws`) tem SockJS habilitado. Na pratica:

1. O cliente tenta WebSocket nativo em `ws://servidor/ws`.
2. Se falhar, SockJS tenta `xhr-streaming`, `xhr-polling`, etc.
3. O codigo frontend nao muda — a biblioteca `@stomp/stompjs` com `SockJS` gerencia isso.

> **Nota:** Com SockJS, a URL de conexao e `http://servidor/ws` (e nao `ws://`) — a biblioteca cuida do upgrade.

---

## 5. Arquitetura hibrida: REST + WebSocket

O AllSet usa uma abordagem **hibrida** onde nem tudo passa pelo WebSocket:

| Operacao | Canal | Por que |
|---|---|---|
| Enviar mensagem | **REST** (POST) | Garante persistencia, validacao, resposta com status HTTP, headers de Location |
| Receber mensagem em tempo real | **WebSocket** (push) | O servidor envia para todos os inscritos no topico da conversa |
| Listar historico de mensagens | **REST** (GET) | Paginacao, cache, caching headers — coisas que HTTP faz bem |
| Listar conversas | **REST** (GET) | Idem |
| Marcar como lida | **REST** (PATCH) | Operacao idempotente, resposta confirmada |
| Receber notificacao de "lida" | **WebSocket** (push) | O outro participante ve em tempo real que suas mensagens foram lidas |

### Por que nao enviar mensagens pelo WebSocket tambem?

Enviar pelo REST traz beneficios importantes:

1. **Confirmacao explicita** — o frontend recebe um `201 Created` com o ID da mensagem. Pelo WebSocket, seria preciso inventar um mecanismo de ACK proprio.
2. **Validacao padrao** — Bean Validation (`@Valid`), tratamento de erros centralizado (`GlobalExceptionHandler`), tudo funciona normalmente.
3. **Idempotencia** — em caso de retry, o frontend sabe exatamente se a mensagem foi salva ou nao.
4. **Simplicidade** — o WebSocket so precisa lidar com push (servidor → cliente), nao com recebimento (cliente → servidor).

---

## 6. Pub/Sub e topicos

O modelo de comunicacao do chat e **Publish/Subscribe (Pub/Sub)**:

- Cada conversa tem um **topico**: `/topic/conversations/{conversationId}`.
- Quando o frontend abre uma conversa, ele se **inscreve** (subscribe) nesse topico.
- Quando uma mensagem e salva, o servidor **publica** (publish) nesse topico.
- Todos os clientes inscritos recebem a mensagem automaticamente.

```
                    /topic/conversations/abc-123
                              |
         ┌────────────────────┼────────────────────┐
         |                    |                     |
    [Cliente A]          [Broker]             [Profissional B]
    (subscriber)        (distribui)           (subscriber)
```

O broker e o componente que recebe publicacoes e as distribui para todos os inscritos. No AllSet, o broker atual e o **SimpleBroker** do Spring — um broker em memoria que vive dentro da JVM. Ele e suficiente para uma unica instancia do servidor, mas nao suporta multiplas instancias (escalabilidade horizontal). Isso e uma limitacao conhecida e endereçada nas fases futuras.

---

## 7. Autenticacao no WebSocket

WebSocket nao tem o conceito de "headers HTTP por mensagem" como REST. A autenticacao acontece em dois momentos:

### Momento 1: CONNECT (autenticacao)

Quando o frontend abre a conexao STOMP, envia um frame `CONNECT` com o JWT no header:

```
CONNECT
Authorization:Bearer eyJhbGciOiJIUzI1NiJ9...
accept-version:1.2
heart-beat:10000,10000

^@
```

O servidor intercepta esse frame (`StompAuthChannelInterceptor`), valida o JWT usando o mesmo decoder do REST, extrai o `userId` e a `role`, e associa ao `Principal` da sessao WebSocket. Se o token for invalido ou expirado, a conexao e rejeitada imediatamente.

> **Importante:** o JWT e validado **apenas no CONNECT**. Depois disso, a sessao WebSocket fica aberta ate desconectar. Se o token expirar durante a sessao, ela continua ativa. Isso e uma trade-off consciente: re-validar a cada frame seria custoso demais. A mitigacao e usar tokens de curta duracao (15 min por padrao).

### Momento 2: SUBSCRIBE (autorizacao)

Apos conectado, quando o frontend tenta se inscrever em `/topic/conversations/{id}`, outro interceptor (`StompSubscriptionInterceptor`) verifica no banco se o usuario autenticado e participante daquela conversa (cliente ou profissional). Se nao for, a inscricao e rejeitada.

Isso impede que um usuario mal-intencionado se inscreva no topico de uma conversa alheia, mesmo que tenha um JWT valido.

```
Fluxo:
1. CONNECT + JWT → valida token → associa userId à sessao
2. SUBSCRIBE /topic/conversations/abc → verifica no banco → usuario e participante? → permite
3. A partir de agora, qualquer MESSAGE publicada nesse topico chega ao frontend
```

---

## 8. Ciclo de vida de uma mensagem

Entender o fluxo completo de uma mensagem e fundamental para a integracao frontend:

```
1. Frontend faz POST /api/v1/conversations/{id}/messages
   Body: { "content": "Ola, que horas voce chega?" }

2. Servidor:
   a. Valida JWT (Spring Security)
   b. Verifica se usuario e participante da conversa
   c. Valida o body (conteudo nao vazio, max 4000 chars)
   d. Cria a entidade Message (tipo: text, sender: userId, sentAt: agora)
   e. Salva no banco (dentro de uma transacao)
   f. Publica um evento interno (MessageSentEvent)
   g. Retorna 201 Created + Location header + MessageResponse

3. APOS o commit da transacao (AFTER_COMMIT):
   a. Um listener captura o evento
   b. Busca a mensagem do banco (ja commitada)
   c. Publica no topico /topic/conversations/{id} via STOMP
   d. Se o destinatario estiver online (conectado ao WebSocket):
      - Marca mensagens nao-entregues como "delivered"
      - Publica um DeliveryReceiptEvent no mesmo topico

4. Frontend do remetente:
   - Ja tem a mensagem pela resposta do POST (passo 2g)
   - Tambem recebe pelo WebSocket (passo 3c) — pode ignorar duplicata pelo ID

5. Frontend do destinatario:
   - Recebe a mensagem pelo WebSocket (passo 3c)
   - Se estiver na conversa, exibe imediatamente
```

### Por que o broadcast acontece APOS o commit?

Se o broadcast acontecesse **antes** do commit, poderia ocorrer:
- O destinatario recebe a mensagem pelo WebSocket.
- A transacao falha (erro de banco, constraint, etc.).
- A mensagem nunca foi salva, mas o destinatario ja a viu.

Usando `@TransactionalEventListener(phase = AFTER_COMMIT)`, garantimos que so publicamos mensagens que **realmente foram persistidas**.

---

## 9. Receipts: delivered e read

O chat implementa dois tipos de confirmacao de recebimento:

### Delivery Receipt (entregue)

Indica que a mensagem chegou ao dispositivo do destinatario (ele estava online quando a mensagem foi publicada).

- **Quando:** automaticamente, no momento do broadcast, se o destinatario estiver conectado ao WebSocket.
- **Como o servidor sabe:** usa o `SimpUserRegistry` do Spring, que rastreia quais usuarios tem sessoes WebSocket ativas.
- **O que acontece:** o campo `deliveredAt` da mensagem e preenchido no banco, e um `DeliveryReceiptEvent` e publicado no topico.

### Read Receipt (lida)

Indica que o destinatario efetivamente abriu/visualizou a conversa.

- **Quando:** o frontend chama `PATCH /api/v1/conversations/{id}/read`.
- **O que acontece:** todas as mensagens nao lidas do outro participante sao marcadas com `readAt` no banco. Um `ReadReceiptEvent` e publicado no topico.

### Como o frontend diferencia mensagens de receipts?

Tudo chega pelo mesmo topico (`/topic/conversations/{id}`). A diferenciacao e feita pelo campo `eventType` no payload:

| Payload | Como identificar |
|---|---|
| Mensagem normal | Tem campo `id`, `msgType`, `content`, etc. Nao tem `eventType`. |
| Read receipt | Tem `eventType: "READ_RECEIPT"` + `affectedCount` |
| Delivery receipt | Tem `eventType: "DELIVERY_RECEIPT"` + `affectedCount` |

O frontend deve verificar se o payload tem `eventType` antes de tentar renderizar como mensagem.

---

## 10. Mensagens de sistema

Alem de mensagens enviadas por usuarios, o chat tem **mensagens de sistema** — geradas automaticamente pelo servidor em resposta a eventos do pedido.

Caracteristicas:
- `senderId` e `null` (nao foram enviadas por nenhum usuario).
- `msgType` e `system`.
- O frontend deve renderiza-las de forma diferente (centralizada, cor diferente, sem avatar, etc.).

**Evento atual que gera mensagem de sistema:**
- Pedido aceito → "Pedido aceito! Voces ja podem combinar os detalhes do servico."

**Eventos planejados para o futuro:**
- Pedido concluido
- Pedido cancelado
- Disputa aberta
- Disputa resolvida

---

## 11. Guia de integracao frontend

### Bibliotecas necessarias

```bash
npm install @stomp/stompjs sockjs-client
# ou
yarn add @stomp/stompjs sockjs-client
```

- `@stomp/stompjs` — cliente STOMP que funciona no browser e Node.
- `sockjs-client` — transport com fallback automatico.

### 11.1 Estabelecer conexao WebSocket

```javascript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const stompClient = new Client({
  // Usar factory SockJS em vez de URL direta
  webSocketFactory: () => new SockJS('http://localhost:8080/ws'),

  // Headers enviados no frame CONNECT
  connectHeaders: {
    Authorization: `Bearer ${accessToken}`,
  },

  // Heartbeat: cliente envia ping a cada 10s, espera pong a cada 10s
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,

  // Callbacks
  onConnect: (frame) => {
    console.log('Conectado ao WebSocket');
    // Agora pode se inscrever em topicos
  },

  onStompError: (frame) => {
    console.error('Erro STOMP:', frame.headers['message']);
    // Token invalido, expirado, etc.
  },

  onWebSocketClose: (event) => {
    console.warn('WebSocket fechou:', event.reason);
    // A biblioteca tenta reconectar automaticamente
  },
});

stompClient.activate(); // Inicia a conexao
```

### 11.2 Inscrever-se em uma conversa

```javascript
function subscribeToConversation(conversationId) {
  const subscription = stompClient.subscribe(
    `/topic/conversations/${conversationId}`,
    (message) => {
      const payload = JSON.parse(message.body);

      if (payload.eventType === 'READ_RECEIPT') {
        handleReadReceipt(payload);
      } else if (payload.eventType === 'DELIVERY_RECEIPT') {
        handleDeliveryReceipt(payload);
      } else {
        // E uma mensagem normal ou de sistema
        handleNewMessage(payload);
      }
    }
  );

  // Guardar referencia para desinscrever depois
  return subscription;
}

// Quando sair da tela da conversa:
subscription.unsubscribe();
```

### 11.3 Enviar mensagem (via REST)

```javascript
async function sendMessage(conversationId, content) {
  const response = await fetch(
    `/api/v1/conversations/${conversationId}/messages`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({ content }),
    }
  );

  if (response.status === 201) {
    const message = await response.json();
    // Adicionar mensagem na UI imediatamente (optimistic update)
    // A mesma mensagem chegara pelo WebSocket — deduplicar pelo ID
    return message;
  }
}
```

### 11.4 Marcar como lida

```javascript
async function markAsRead(conversationId) {
  const response = await fetch(
    `/api/v1/conversations/${conversationId}/read`,
    {
      method: 'PATCH',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    }
  );

  if (response.ok) {
    const receipt = await response.json();
    // receipt.affectedCount = numero de mensagens marcadas
  }
}
```

### 11.5 Carregar historico (paginado)

```javascript
async function loadMessages(conversationId, page = 0) {
  const response = await fetch(
    `/api/v1/conversations/${conversationId}/messages?page=${page}&size=50&sort=sentAt,desc`,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
    }
  );

  const data = await response.json();
  // data.content = array de mensagens (mais recentes primeiro)
  // data.totalPages, data.totalElements para controle de paginacao
  return data;
}
```

### 11.6 Listar conversas do usuario

```javascript
async function listConversations(page = 0) {
  const response = await fetch(
    `/api/v1/conversations?page=${page}&size=20&sort=createdAt,desc`,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
    }
  );

  const data = await response.json();
  // data.content = array de ConversationSummaryResponse
  // Cada item tem: id, orderId, otherParticipantId, lastMessage, unreadCount
  return data;
}
```

### 11.7 Fluxo tipico de tela de conversas

```
1. Usuario abre o app
2. Frontend carrega lista de conversas (GET /api/v1/conversations)
   → Exibe lista com lastMessage e unreadCount de cada conversa

3. Usuario toca em uma conversa
4. Frontend:
   a. Carrega historico (GET /conversations/{id}/messages?sort=sentAt,desc)
   b. Se inscreve no topico WebSocket (/topic/conversations/{id})
   c. Marca como lida (PATCH /conversations/{id}/read)
   → Exibe mensagens, scroll para o final

5. Mensagem nova chega pelo WebSocket
   → Adiciona na lista, marca como lida se a conversa estiver aberta

6. Usuario digita e envia mensagem
   a. POST /conversations/{id}/messages
   b. Exibe imediatamente (optimistic update)
   c. WebSocket entrega para o outro participante

7. Usuario sai da conversa
   → Desinscreve do topico WebSocket (subscription.unsubscribe())
```

### 11.8 Deduplicacao de mensagens

Quando o remetente envia uma mensagem:
1. Ele recebe a `MessageResponse` na resposta do POST.
2. Ele tambem recebe a mesma mensagem pelo WebSocket (pois esta inscrito no topico).

O frontend **deve** deduplicar pelo `id` da mensagem. Estrategia simples:

```javascript
const messageIds = new Set();

function handleNewMessage(msg) {
  if (messageIds.has(msg.id)) return; // Duplicata — ignorar
  messageIds.add(msg.id);
  addToUI(msg);
}
```

---

## 12. Tratamento de erros e reconexao

### Reconexao automatica

A biblioteca `@stomp/stompjs` tem reconexao automatica embutida. Basta configurar:

```javascript
const stompClient = new Client({
  // ... configuracao anterior ...

  reconnectDelay: 5000, // Tenta reconectar a cada 5 segundos
});
```

Quando a conexao cai (rede instavel, servidor reinicia, etc.), a biblioteca:
1. Detecta a desconexao.
2. Espera `reconnectDelay` ms.
3. Tenta reconectar.
4. Repete ate conseguir.

**Cuidado:** apos reconectar, o frontend precisa se **reinscrever** nos topicos. O callback `onConnect` e chamado a cada reconexao, entao coloque a logica de subscription la dentro.

### Token expirado durante sessao

O JWT e validado apenas no CONNECT. Se o token expirar durante uma sessao ativa:
- A sessao WebSocket **continua funcionando** (o servidor nao re-valida).
- Se a conexao cair e tentar reconectar, o CONNECT vai falhar com token expirado.
- O frontend deve renovar o token (refresh token) **antes** de reconectar.

Estrategia recomendada:

```javascript
onStompError: (frame) => {
  if (frame.headers['message']?.includes('token')) {
    // Token expirado — renovar antes de reconectar
    refreshAccessToken().then((newToken) => {
      stompClient.connectHeaders.Authorization = `Bearer ${newToken}`;
      stompClient.activate();
    });
  }
}
```

### Mensagens perdidas durante desconexao

Se o frontend estiver desconectado quando uma mensagem e enviada, ele nao recebera o push WebSocket. Para recuperar:

- Ao reconectar e abrir uma conversa, **sempre** carregue o historico via REST.
- Compare com o estado local e adicione mensagens que faltam.
- O campo `unreadCount` na listagem de conversas tambem ajuda a sinalizar conversas com mensagens novas.

---

## 13. Limitacoes atuais e evolucao futura

### Limitacoes do MVP

| Limitacao | Impacto | Mitigacao |
|---|---|---|
| **Broker em memoria (SimpleBroker)** | Funciona apenas com uma instancia do servidor. Se escalar para multiplas instancias, mensagens WebSocket nao sao compartilhadas. | Substituir por `StompBrokerRelay` com RabbitMQ ou Redis Pub/Sub quando necessario. A troca e transparente — nenhum codigo de servico muda. |
| **JWT nao re-validado apos CONNECT** | Se um usuario for banido durante uma sessao ativa, ele continua recebendo mensagens ate desconectar. | Tokens com TTL curto (15 min). Em fases futuras, validar sessao via Redis a cada N minutos. |
| **Sem envio de imagens (fase 2)** | Apenas mensagens de texto e sistema. | A entidade `Message` ja tem campos `attachmentUrl`, `attachmentSizeBytes`, `attachmentMimeType`. O banco ja suporta tipo `image`. Falta implementar upload S3 + endpoint. |
| **Sem notificacoes push** | Se o usuario nao estiver com o app aberto, ele nao sabe que recebeu mensagem. | Integracao com FCM (Firebase Cloud Messaging) planejada no modulo `notification`. |
| **Sem indicador de "digitando"** | O outro participante nao ve quando alguem esta escrevendo. | Pode ser implementado com um frame STOMP custom (`/app/conversations/{id}/typing`) sem persistencia. |
| **Sem paginacao em tempo real** | Se o usuario estiver na pagina 2 do historico e uma mensagem nova chegar, pode haver inconsistencia. | Frontend deve sempre inserir mensagens novas (do WebSocket) no topo da lista local, independente da pagina carregada via REST. |

### Fases futuras planejadas

1. **Broker externo** — migrar para RabbitMQ com `StompBrokerRelay` para suportar multiplas instancias.
2. **Envio de imagens** — upload via presigned URL do S3, mensagem tipo `image` com `attachmentUrl`.
3. **Notificacoes push** — FCM para mensagens recebidas quando offline.
4. **Admin como terceiro participante** — em caso de disputa, admin entra na conversa.
5. **Indicador de digitando** — evento efemero via STOMP, sem persistencia.
6. **Validacao periodica de sessao** — Redis check a cada X minutos para revogar sessoes de usuarios banidos.

---

## Glossario

| Termo | Definicao |
|---|---|
| **WebSocket** | Protocolo de comunicacao bidirecional persistente sobre TCP (RFC 6455). |
| **STOMP** | Simple Text-Oriented Messaging Protocol — protocolo de mensageria baseado em texto que roda sobre WebSocket. |
| **SockJS** | Biblioteca que fornece fallback automatico quando WebSocket nao esta disponivel. |
| **Broker** | Componente que recebe mensagens publicadas e as distribui para os inscritos. |
| **Topic** | Canal nomeado onde mensagens sao publicadas. Inscritos recebem tudo que for publicado ali. |
| **Frame** | Unidade de dados no STOMP — composto por comando, headers e body. |
| **Pub/Sub** | Padrao de mensageria onde publicadores enviam para topicos e assinantes recebem desses topicos, sem conhecer um ao outro. |
| **Handshake** | Processo inicial de troca de informacoes para estabelecer a conexao WebSocket. |
| **Heartbeat** | Pings periodicos para manter a conexao ativa e detectar desconexoes. |
| **Receipt** | Confirmacao de que uma mensagem foi entregue (delivery) ou lida (read). |
| **Optimistic update** | Tecnica de UI onde a acao e refletida imediatamente antes da confirmacao do servidor. |
