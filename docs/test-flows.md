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

## Observacoes importantes do estado atual

- Varios endpoints documentados como admin/owner-only hoje estao apenas como autenticados.
- `POST /api/v1/professionals` esta publico.
- `POST /api/users` permite criar usuario com role `admin`.
- O endpoint de cancelamento de assinatura responde, mas hoje nao persiste cancelamento no banco.
- O fluxo implementado por controller e o Express; `on_demand` ainda nao esta exposto por rota.
