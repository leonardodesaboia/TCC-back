# Fluxos de teste da API

Este documento complementa a collection em [docs/insomnia/allset-local-insomnia.json](/home/leonardodesaboia/Documents/TCC-back/docs/insomnia/allset-local-insomnia.json) com uma sequencia pratica de testes, bodies prontos e os dados minimos para subir o fluxo Express localmente.

## Regras antes de testar

- API local: `http://localhost:8080`
- Como ha constraints unicas, altere e-mails e CPFs se repetir os testes.
- Senha que passa na validacao do projeto: `Senha@2025!`
- Roles aceitas: `client`, `professional`, `admin`
- CPFs validos para teste:
  - `529.982.247-25`
  - `111.444.777-35`
  - `123.456.789-09`

## Datasets recomendados

Use 3 usuarios para testar bem o projeto.

### Cliente

```json
{
  "name": "Cliente Teste",
  "cpf": "529.982.247-25",
  "email": "cliente.teste@example.com",
  "phone": "+5585999990001",
  "password": "Senha@2025!",
  "role": "client"
}
```

### Profissional

```json
{
  "name": "Profissional Teste",
  "cpf": "111.444.777-35",
  "email": "profissional.teste@example.com",
  "phone": "+5585999990002",
  "password": "Senha@2025!",
  "role": "professional"
}
```

### Admin

```json
{
  "name": "Admin Teste",
  "cpf": "123.456.789-09",
  "email": "admin.teste@example.com",
  "phone": "+5585999990003",
  "password": "Senha@2025!",
  "role": "admin"
}
```

## Variaveis que voce deve guardar

Salve os valores abaixo ao longo do fluxo:

- `client_user_id`
- `professional_user_id`
- `admin_user_id`
- `client_access_token`
- `professional_access_token`
- `admin_access_token`
- `address_id`
- `professional_id`
- `area_id`
- `category_id`
- `subscription_plan_id`
- `document_id`
- `offering_id`
- `blocked_period_id`
- `order_id`
- `conversation_id`

## Fluxo 1: criar usuarios e autenticar

### 1. Criar cliente

- Endpoint: `POST /api/users`
- Token: nenhum

```json
{
  "name": "Cliente Teste",
  "cpf": "529.982.247-25",
  "email": "cliente.teste@example.com",
  "phone": "+5585999990001",
  "password": "Senha@2025!",
  "role": "client"
}
```

Guarde o `id` retornado como `client_user_id`.

### 2. Criar profissional

- Endpoint: `POST /api/users`
- Token: nenhum

```json
{
  "name": "Profissional Teste",
  "cpf": "111.444.777-35",
  "email": "profissional.teste@example.com",
  "phone": "+5585999990002",
  "password": "Senha@2025!",
  "role": "professional"
}
```

Guarde o `id` retornado como `professional_user_id`.

### 3. Criar admin

- Endpoint: `POST /api/users`
- Token: nenhum

```json
{
  "name": "Admin Teste",
  "cpf": "123.456.789-09",
  "email": "admin.teste@example.com",
  "phone": "+5585999990003",
  "password": "Senha@2025!",
  "role": "admin"
}
```

Guarde o `id` retornado como `admin_user_id`.

### 4. Login do cliente

- Endpoint: `POST /api/auth/login`

```json
{
  "email": "cliente.teste@example.com",
  "password": "Senha@2025!"
}
```

Guarde `accessToken` como `client_access_token`.

### 5. Login do profissional

- Endpoint: `POST /api/auth/login`

```json
{
  "email": "profissional.teste@example.com",
  "password": "Senha@2025!"
}
```

Guarde `accessToken` como `professional_access_token`.

### 6. Login do admin

- Endpoint: `POST /api/auth/login`

```json
{
  "email": "admin.teste@example.com",
  "password": "Senha@2025!"
}
```

Guarde `accessToken` como `admin_access_token`.

## Fluxo 2: criar catalogo e plano

Use preferencialmente o token do admin, embora hoje o projeto aceite qualquer autenticado em varias dessas rotas.

### 1. Criar area de servico

- Endpoint: `POST /api/v1/service-areas`
- Token: `admin_access_token`

```json
{
  "name": "Eletrica",
  "iconUrl": "https://cdn.example.com/icons/eletrica.png"
}
```

Guarde `id` como `area_id`.

### 2. Criar categoria de servico

- Endpoint: `POST /api/v1/service-categories`
- Token: `admin_access_token`

```json
{
  "areaId": "{{area_id}}",
  "name": "Eletricista",
  "iconUrl": "https://cdn.example.com/icons/eletricista.png"
}
```

Guarde `id` como `category_id`.

### 3. Criar plano de assinatura

- Endpoint: `POST /api/v1/subscription-plans`
- Token: `admin_access_token`

```json
{
  "name": "Plano Pro",
  "priceMonthly": 49.90,
  "highlightInSearch": true,
  "expressPriority": true,
  "badgeLabel": "Pro",
  "active": true
}
```

Guarde `id` como `subscription_plan_id`.

## Fluxo 3: onboarding do profissional

### 1. Criar perfil profissional

- Endpoint: `POST /api/v1/professionals`
- Token: nenhum

```json
{
  "userId": "{{professional_user_id}}",
  "bio": "Eletricista residencial e comercial",
  "yearsOfExperience": 7,
  "baseHourlyRate": 150.00
}
```

Guarde `id` como `professional_id`.

### 2. Enviar documento

- Endpoint: `POST /api/v1/professionals/{{professional_id}}/documents`
- Token: `professional_access_token`

```json
{
  "docType": "rg",
  "fileUrl": "https://cdn.example.com/documents/rg-frente.jpg"
}
```

Guarde `id` como `document_id`.

### 3. Aplicar plano de assinatura

- Endpoint: `PUT /api/v1/professionals/{{professional_id}}/subscription`
- Token: `professional_access_token`

```json
{
  "subscriptionPlanId": "{{subscription_plan_id}}"
}
```

### 4. Ativar geolocalizacao para Express

- Endpoint: `PATCH /api/v1/professionals/{{professional_id}}/geo`
- Token: `professional_access_token`

```json
{
  "geoActive": true,
  "geoLat": -3.731862,
  "geoLng": -38.526669
}
```

### 5. Aprovar verificacao do profissional

- Endpoint: `PATCH /api/v1/professionals/{{professional_id}}/verify`
- Token: `admin_access_token`

```json
{
  "status": "approved",
  "rejectionReason": null
}
```

### 6. Criar servico do profissional

- Endpoint: `POST /api/v1/professionals/{{professional_id}}/services`
- Token: `professional_access_token`

```json
{
  "categoryId": "{{category_id}}",
  "title": "Instalacao de tomadas",
  "description": "Instalacao e troca de tomadas e interruptores",
  "pricingType": "fixed",
  "price": 180.00,
  "estimatedDurationMinutes": 90
}
```

Guarde `id` como `offering_id`.

### 7. Criar bloqueio de agenda

- Endpoint: `POST /api/v1/professionals/{{professional_id}}/calendar/blocks`
- Token: `professional_access_token`

```json
{
  "blockType": "specific_date",
  "specificDate": "2026-04-20",
  "startsAt": "08:00:00",
  "endsAt": "12:00:00",
  "reason": "Atendimento externo"
}
```

Guarde `id` como `blocked_period_id`.

## Fluxo 4: preparar o cliente

### 1. Criar endereco do cliente com coordenadas

- Endpoint: `POST /api/users/{{client_user_id}}/addresses`
- Token: `client_access_token`

```json
{
  "label": "Casa",
  "street": "Rua das Flores",
  "number": "42",
  "complement": "Apto 301",
  "district": "Centro",
  "city": "Fortaleza",
  "state": "CE",
  "zipCode": "60000-000",
  "lat": -3.731862,
  "lng": -38.526669,
  "isDefault": true
}
```

Guarde `id` como `address_id`.

## Fluxo 5: pedido Express completo

Este e o fluxo principal do backend atual.

### 1. Cliente cria pedido Express

- Endpoint: `POST /api/v1/orders/express`
- Token: `client_access_token`

```json
{
  "areaId": "{{area_id}}",
  "categoryId": "{{category_id}}",
  "description": "Tomada queimada na sala principal",
  "addressId": "{{address_id}}",
  "photoUrl": "https://cdn.example.com/orders/problema.jpg",
  "urgencyFee": 20.00
}
```

Guarde `id` como `order_id`.

### 2. Profissional responde ao pedido

- Endpoint: `POST /api/v1/orders/{{order_id}}/express/pro-respond`
- Token: `professional_access_token`

```json
{
  "response": "accepted",
  "proposedAmount": 180.00
}
```

### 3. Cliente lista propostas recebidas

- Endpoint: `GET /api/v1/orders/{{order_id}}/express/proposals`
- Token: `client_access_token`

Sem body.

### 4. Cliente escolhe a proposta

- Endpoint: `POST /api/v1/orders/{{order_id}}/express/client-respond`
- Token: `client_access_token`

```json
{
  "selectedProfessionalId": "{{professional_id}}"
}
```

### 5. Profissional marca como concluido

- Endpoint: `POST /api/v1/orders/{{order_id}}/complete`
- Token: `professional_access_token`

```json
{
  "photoUrl": "https://cdn.example.com/orders/conclusao.jpg"
}
```

### 6. Cliente confirma conclusao

- Endpoint: `POST /api/v1/orders/{{order_id}}/confirm`
- Token: `client_access_token`

Sem body.

### 7. Validar estado final

- Endpoint: `GET /api/v1/orders/{{order_id}}`
- Token: `client_access_token`

Espera-se algo proximo de:

- `status = completed`
- `professionalId = {{professional_id}}`
- `baseAmount = 180.00`
- `urgencyFee = 20.00`
- `platformFee = 36.00`
- `totalAmount = 200.00`

## Fluxo 6: cancelamento de pedido

### Cancelar antes da conclusao

- Endpoint: `POST /api/v1/orders/{{order_id}}/cancel`
- Token: `client_access_token`

```json
{
  "reason": "Cliente desistiu do atendimento"
}
```

Observacao:

- O cancelamento por cliente funciona.
- O cancelamento por profissional tem inconsistencia no service atual e pode retornar `404`.

## Fluxo 7: recuperacao de senha

### 1. Solicitar codigo

- Endpoint: `POST /api/auth/forgot-password`

```json
{
  "email": "cliente.teste@example.com"
}
```

### 2. Redefinir senha

- Endpoint: `POST /api/auth/reset-password`

```json
{
  "email": "cliente.teste@example.com",
  "code": "1234",
  "newPassword": "NovaSenha@2025!"
}
```

Observacao:

- O codigo real vem por e-mail.
- `1234` e apenas placeholder para teste manual do body.

## Fluxo 8: consultas uteis de verificacao

### Consultar usuario

- `GET /api/users/{{client_user_id}}`

### Consultar enderecos do cliente

- `GET /api/users/{{client_user_id}}/addresses`

### Consultar profissional

- `GET /api/v1/professionals/{{professional_id}}`

### Consultar documentos

- `GET /api/v1/professionals/{{professional_id}}/documents`

### Consultar servicos do profissional

- `GET /api/v1/professionals/{{professional_id}}/services?includeInactive=false&page=0&size=20`

### Consultar assinatura atual

- `GET /api/v1/professionals/{{professional_id}}/subscription`

### Consultar pedidos do cliente

- `GET /api/v1/orders?page=0&size=20`

## Casos de erro recomendados

### 1. Criar usuario com e-mail repetido

Espera:

- `409 Conflict`

### 2. Criar pedido Express com endereco sem `lat` e `lng`

Espera:

- `400 Bad Request`

### 3. Profissional aceitar pedido sem `proposedAmount`

Body:

```json
{
  "response": "accepted"
}
```

Espera:

- `400 Bad Request`

### 4. Bloqueio recorrente sem `weekday`

Body:

```json
{
  "blockType": "recurring",
  "startsAt": "09:00:00",
  "endsAt": "11:00:00",
  "reason": "Bloqueio invalido"
}
```

Espera:

- `400 Bad Request`

### 5. Verificacao rejeitada sem motivo

Body:

```json
{
  "status": "rejected"
}
```

Espera:

- `400 Bad Request`

## Ordem minima recomendada no Insomnia

1. `Create User` para cliente, profissional e admin
2. `Login` de cada usuario
3. `Create Service Area`
4. `Create Service Category`
5. `Create Subscription Plan`
6. `Create Professional`
7. `Create Professional Document`
8. `Assign Subscription Plan`
9. `Update Professional Geo`
10. `Verify Professional`
11. `Create Professional Service`
12. `Create Address`
13. `Create Express Order`
14. `Professional Respond To Express Order`
15. `Get Express Proposals`
16. `Client Choose Proposal`
17. `Professional Complete Order`
18. `Client Confirm Completion`

## Fluxo 9: chat

**Pre-requisito:** executar o Fluxo 5 (pedido Express completo) ate o passo 4 (cliente escolhe proposta). A conversa e criada automaticamente nesse momento — nao ha endpoint de criacao manual.

### Parte A — REST (Insomnia)

#### 1. Verificar que a conversa foi criada

Apos o `client-respond`, a conversa ja existe. Liste as conversas do cliente:

- Endpoint: `GET /api/v1/conversations`
- Token: `client_access_token`

Espera-se:
- `200 OK`
- `content` com 1 conversa
- `lastMessage.msgType = "system"` (mensagem automatica de boas-vindas)
- `lastMessage.content = "Pedido aceito. Vocês podem conversar por aqui."`
- `unreadCount = 1` (cliente ainda nao leu a mensagem de sistema)

Guarde o `id` da conversa como `conversation_id`.

---

#### 2. Buscar detalhes da conversa

- Endpoint: `GET /api/v1/conversations/{{conversation_id}}`
- Token: `client_access_token`

Espera-se:
- `200 OK`
- `orderId` igual ao `order_id` do fluxo anterior
- `clientId` igual ao `client_user_id`
- `professionalUserId` igual ao `professional_user_id`

---

#### 3. Listar historico de mensagens

- Endpoint: `GET /api/v1/conversations/{{conversation_id}}/messages?sort=sentAt,desc`
- Token: `client_access_token`

Espera-se:
- `200 OK`
- 1 mensagem com `msgType = "system"`, `senderId = null`, `content = "Pedido aceito. Vocês podem conversar por aqui."`

---

#### 4. Profissional envia mensagem

- Endpoint: `POST /api/v1/conversations/{{conversation_id}}/messages`
- Token: `professional_access_token`

```json
{
  "content": "Oi! Estou a caminho, chego em 15 minutos."
}
```

Espera-se:
- `201 Created`
- `msgType = "text"`
- `senderId = {{professional_user_id}}`
- `deliveredAt = null` (cliente nao esta conectado via WebSocket)
- `readAt = null`

---

#### 5. Cliente envia mensagem

- Endpoint: `POST /api/v1/conversations/{{conversation_id}}/messages`
- Token: `client_access_token`

```json
{
  "content": "Ok, estarei aqui esperando!"
}
```

Espera-se:
- `201 Created`

---

#### 6. Listar mensagens novamente e verificar estado

- Endpoint: `GET /api/v1/conversations/{{conversation_id}}/messages?sort=sentAt,asc`
- Token: `client_access_token`

Espera-se 3 mensagens em ordem:
1. `msgType = "system"` — mensagem automatica
2. `msgType = "text"`, `senderId = professional_user_id`
3. `msgType = "text"`, `senderId = client_user_id`

---

#### 7. Cliente marca mensagens como lidas

- Endpoint: `PATCH /api/v1/conversations/{{conversation_id}}/read`
- Token: `client_access_token`

Espera-se:
- `200 OK`
- `eventType = "READ_RECEIPT"`
- `affectedCount >= 1` (pelo menos a mensagem do profissional)
- `readerUserId = {{client_user_id}}`

---

#### 8. Verificar unreadCount zerado

- Endpoint: `GET /api/v1/conversations`
- Token: `client_access_token`

Espera-se:
- `unreadCount = 0`
- `lastMessage.readAt` preenchido

---

### Parte B — WebSocket (STOMP via wscat)

`wscat` testa a conexao WebSocket diretamente no terminal. Instalar com:

```bash
npm install -g wscat
```

#### 1. Conectar com SockJS raw (mais simples para teste manual)

O SockJS expoe um endpoint HTTP para testes sem o handshake SockJS completo:

```bash
wscat -c "ws://localhost:8080/ws/websocket"
```

Apos conectar, enviar o frame CONNECT com o token do cliente:

```
CONNECT
Authorization:Bearer {{client_access_token}}
accept-version:1.2
heart-beat:0,0

^@
```

> O `^@` e o caractere null (ASCII 0) — terminador de frame STOMP. No `wscat` digitar `Ctrl+@` ou `Ctrl+Space`.

Espera-se receber:
```
CONNECTED
version:1.2
```

Se o token for invalido, expirado ou sem `role`, voce recebe um frame `ERROR` e a conexao fecha.

---

#### 2. Inscrever no topico da conversa

```
SUBSCRIBE
id:sub-0
destination:/topic/conversations/{{conversation_id}}

^@
```

Se o usuario nao for participante, voce recebe `ERROR`. Se for participante, nao recebe confirmacao visivel — a inscricao e silenciosa ate chegar uma mensagem.

---

#### 3. Enviar mensagem via REST e observar o broadcast

Com a conexao WebSocket aberta e inscrita no topico, abra outra aba/terminal e envie uma mensagem via REST (passo A.4 ou A.5 acima).

Voce deve receber no WebSocket automaticamente:

```json
{
  "id": "uuid",
  "conversationId": "uuid",
  "senderId": "uuid",
  "msgType": "text",
  "content": "Oi! Estou a caminho, chego em 15 minutos.",
  "attachmentUrl": null,
  "sentAt": "2026-04-09T14:00:00Z",
  "deliveredAt": null,
  "readAt": null
}
```

Se o destinatario estiver conectado (voce), logo em seguida deve chegar tambem o `DeliveryReceiptEvent`:

```json
{
  "eventType": "DELIVERY_RECEIPT",
  "conversationId": "uuid",
  "receiverUserId": "{{client_user_id}}",
  "deliveredAt": "2026-04-09T14:00:00Z",
  "affectedCount": 1
}
```

---

#### 4. Enviar PATCH /read e observar o ReadReceiptEvent

Com o WebSocket ainda aberto (como cliente), abra outro terminal e chame o `PATCH /read` com o token do profissional.

O frame recebido no topico deve ser:

```json
{
  "eventType": "READ_RECEIPT",
  "conversationId": "uuid",
  "readerUserId": "{{professional_user_id}}",
  "readAt": "2026-04-09T14:05:00Z",
  "affectedCount": 1
}
```

---

### Parte C — Casos de erro

#### Terceiro usuario tenta acessar a conversa (REST)

- Endpoint: `GET /api/v1/conversations/{{conversation_id}}`
- Token: `admin_access_token` (ou qualquer token de usuario nao participante)

Espera-se:
- `404 Not Found` — nao `403`. Segue o padrao de ownership leak prevention do projeto.

---

#### Terceiro usuario tenta se inscrever no topico (WebSocket)

Conectar com o token do admin e tentar:

```
SUBSCRIBE
id:sub-0
destination:/topic/conversations/{{conversation_id}}

^@
```

Espera-se frame `ERROR`:
```
ERROR
message:Acesso negado à conversa
```

---

#### Mensagem vazia

- Endpoint: `POST /api/v1/conversations/{{conversation_id}}/messages`
- Token: `client_access_token`

```json
{
  "content": ""
}
```

Espera-se:
- `400 Bad Request`
- `fields.content = "Conteúdo é obrigatório"`

---

#### Mensagem acima do limite

- Endpoint: `POST /api/v1/conversations/{{conversation_id}}/messages`
- Token: `client_access_token`

```json
{
  "content": "a..."  // string com 4001+ caracteres
}
```

Espera-se:
- `400 Bad Request`
- `fields.content = "Mensagem não pode exceder 4000 caracteres"`

---

#### CONNECT sem token

```
CONNECT
accept-version:1.2

^@
```

Espera-se frame `ERROR`:
```
ERROR
message:Authorization header ausente no CONNECT
```

---

#### CONNECT com token de refresh (nao deve ser aceito como access token)

Usar o `refreshToken` retornado pelo login no header `Authorization`. Espera-se frame `ERROR` porque o resource server rejeita refresh tokens como access tokens.

---

### Resumo do que validar

| Cenario | Esperado |
|---|---|
| `GET /conversations` apos `client-respond` | 1 conversa com system message |
| `GET /messages` | system message com `senderId = null` |
| `POST /messages` participante | `201` + payload completo |
| `POST /messages` nao participante | `404` |
| `PATCH /read` | `200` + `affectedCount >= 1` |
| `GET /conversations` apos read | `unreadCount = 0` |
| WebSocket CONNECT com JWT valido | frame `CONNECTED` |
| WebSocket CONNECT sem JWT | frame `ERROR` |
| WebSocket SUBSCRIBE conversa propria | sucesso silencioso |
| WebSocket SUBSCRIBE conversa alheia | frame `ERROR` |
| POST /messages via REST com WS aberto | mensagem chega no topico em tempo real |
| Destinatario online ao receber mensagem | `DeliveryReceiptEvent` no topico |
| PATCH /read com WS aberto | `ReadReceiptEvent` no topico |

---

## Observacoes importantes do estado atual

- Varios endpoints documentados como admin/owner-only hoje estao apenas como autenticados.
- `POST /api/v1/professionals` esta publico.
- `POST /api/users` permite criar usuario com role `admin`.
- O endpoint de cancelamento de assinatura responde, mas hoje nao persiste cancelamento no banco.
- O fluxo implementado por controller e o Express; `on_demand` ainda nao esta exposto por rota.
