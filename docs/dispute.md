# Disputas — Documentacao Tecnica

Modulo de protecao ao consumidor que permite ao cliente contestar a conclusao de um servico antes que o escrow seja liberado ao profissional.

---

## Indice

1. [Visao geral](#1-visao-geral)
2. [Quando uma disputa pode ser aberta](#2-quando-uma-disputa-pode-ser-aberta)
3. [Fluxo completo](#3-fluxo-completo)
4. [Modelo de dados](#4-modelo-de-dados)
5. [Regras de negocio criticas](#5-regras-de-negocio-criticas)
6. [Endpoints REST](#6-endpoints-rest)
7. [Resolucao e invariante financeira](#7-resolucao-e-invariante-financeira)
8. [Integracao com chat e notificacoes](#8-integracao-com-chat-e-notificacoes)
9. [Estrutura de pacotes](#9-estrutura-de-pacotes)
10. [Mudancas em modulos existentes](#10-mudancas-em-modulos-existentes)
11. [Migration](#11-migration)
12. [Escopo fora deste plano](#12-escopo-fora-deste-plano)

---

## 1. Visao geral

Quando o profissional marca um servico como concluido (`completed_by_pro`), o cliente recebe uma janela de **24 horas** para:

- **Confirmar** a conclusao — escrow liberado ao profissional (menos fee de 20%).
- **Abrir uma disputa** — escrow congelado, resolucao transferida ao admin.

Se o cliente nao fizer nada em 24 horas, o comportamento e definido por uma fase futura (scheduler de auto-complete).

```
completed_by_pro
    ├── [cliente confirma] ──────────────────> completed (escrow liberado)
    └── [cliente contesta dentro de 24h] ──── disputed (escrow congelado)
                                                   └── admin resolve
```

---

## 2. Quando uma disputa pode ser aberta

| Condicao | Status |
|---|---|
| Pedido existe | obrigatorio |
| `order.status == completed_by_pro` | obrigatorio — `400` caso contrario |
| `Instant.now() <= order.dispute_deadline` | obrigatorio — `400` se janela expirada |
| Solicitante e o `order.client_id` | obrigatorio — `404` caso contrario (ownership) |
| Nao existe disputa para o pedido | obrigatorio — `409` se ja existe |

`order.dispute_deadline` e preenchido automaticamente em `OrderServiceImpl.completeByPro` como `pro_completed_at + 24h`.

---

## 3. Fluxo completo

```
1. Profissional -> POST /api/v1/orders/{id}/complete
       order.status = completed_by_pro
       order.pro_completed_at = now()
       order.dispute_deadline = now() + 24h

2. Cliente -> POST /api/v1/orders/{orderId}/disputes
       dispute.status = open
       order.status = disputed
       Mensagem de sistema no chat: "Disputa aberta pelo cliente. Motivo: ..."
       Notificacao ao profissional: dispute_opened

3. Ambas as partes enviam evidencias
       POST /api/v1/disputes/{id}/evidences
       evidence_type: text (descricao) ou photo (URL de arquivo)

4. Admin assume o caso
       PATCH /api/v1/disputes/{id}/under-review
       dispute.status = under_review
       Notificacoes a ambas as partes
       Mensagem de sistema no chat

5. Admin resolve
       POST /api/v1/disputes/{id}/resolve
       dispute.status = resolved
       dispute.resolution = refund_full | refund_partial | release_to_pro
       Valores calculados (soma = order.total_amount)
       Notificacoes a ambas as partes
       Mensagem de sistema no chat

6. (TODO modulo payment) processamento financeiro conforme resolucao
```

---

## 4. Modelo de dados

### Tabela `disputes`

| Coluna | Tipo | Descricao |
|---|---|---|
| `id` | `UUID` | PK gerada pelo banco |
| `version` | `INT` | Optimistic locking — previne dois admins resolvendo simultaneamente |
| `order_id` | `UUID` | FK para `orders(id)` — UNIQUE (1 disputa por pedido) |
| `opened_by` | `UUID` | FK para `users(id)` — sempre o cliente |
| `reason` | `TEXT` | Motivo da disputa (max 2000 chars) |
| `status` | `dispute_status` | `open` / `under_review` / `resolved` |
| `resolution` | `dispute_resolution` | `refund_full` / `refund_partial` / `release_to_pro` — preenchido na resolucao |
| `admin_notes` | `TEXT` | Notas internas do admin — nunca exibidas ao cliente/profissional |
| `client_refund_amount` | `NUMERIC(10,2)` | Valor a ser devolvido ao cliente |
| `professional_amount` | `NUMERIC(10,2)` | Valor a ser liberado ao profissional |
| `resolved_by` | `UUID` | FK para `users(id)` — admin que resolveu |
| `resolved_at` | `TIMESTAMPTZ` | Momento da resolucao |
| `opened_at` | `TIMESTAMPTZ` | Dado de negocio — momento da abertura |

### Tabela `dispute_evidences`

| Coluna | Tipo | Descricao |
|---|---|---|
| `id` | `UUID` | PK gerada pelo banco |
| `dispute_id` | `UUID` | FK para `disputes(id)` |
| `sender_id` | `UUID` | FK para `users(id)` — cliente, profissional ou admin |
| `evidence_type` | `evidence_type` | `text` ou `photo` |
| `content` | `TEXT` | Descricao textual (obrigatorio para `text`, opcional como legenda em `photo`) |
| `file_url` | `TEXT` | URL do arquivo (obrigatorio para `photo`, fase 1 aceita string; fase 2 usa StorageService) |
| `sent_at` | `TIMESTAMPTZ` | Imutavel |

**CHECK constraint:**
```sql
(evidence_type = 'text'  AND content IS NOT NULL) OR
(evidence_type = 'photo' AND file_url IS NOT NULL)
```

---

## 5. Regras de negocio criticas

### Quem pode fazer o que

| Acao | Permissao |
|---|---|
| Abrir disputa | Somente o cliente do pedido |
| Adicionar evidencias | Cliente, profissional do pedido, ou admin |
| Marcar `under_review` | Somente admin |
| Resolver | Somente admin |
| Consultar disputa | Participantes do pedido ou admin |
| Listar todas as disputas | Somente admin |

### Ownership e 404

Seguindo o padrao do projeto, qualquer acesso indevido (disputa inexistente OU usuario sem acesso) retorna **404**, nunca 403 — para nao vazar existencia do recurso.

### Transicao de status

| De | Para | Quem |
|---|---|---|
| — | `open` | Cliente (abertura) |
| `open` | `under_review` | Admin |
| `open` ou `under_review` | `resolved` | Admin |
| `resolved` | qualquer | Bloqueado — disputa encerrada |

Evidencias so podem ser adicionadas enquanto `status != resolved`.

---

## 6. Endpoints REST

Prefixo base: `/api/v1`. Todos requerem autenticacao Bearer.

| Metodo | Rota | Acesso | Descricao |
|---|---|---|---|
| `POST` | `/api/v1/orders/{orderId}/disputes` | Client | Abre disputa |
| `GET` | `/api/v1/orders/{orderId}/disputes` | Participante ou Admin | Busca disputa pelo pedido |
| `GET` | `/api/v1/disputes/{id}` | Participante ou Admin | Detalhes da disputa |
| `GET` | `/api/v1/disputes` | Admin | Lista paginada (filtro por `?status=`) |
| `PATCH` | `/api/v1/disputes/{id}/under-review` | Admin | Marca como em analise |
| `POST` | `/api/v1/disputes/{id}/resolve` | Admin | Resolve a disputa |
| `POST` | `/api/v1/disputes/{id}/evidences` | Participante ou Admin | Adiciona evidencia |
| `GET` | `/api/v1/disputes/{id}/evidences` | Participante ou Admin | Lista evidencias |

### `POST /api/v1/orders/{orderId}/disputes` — Request body

```json
{
  "reason": "Servico nao foi realizado conforme combinado"
}
```

### `POST /api/v1/disputes/{id}/evidences` — Request body

```json
{
  "evidenceType": "text",
  "content": "O acabamento ficou com defeito visivel."
}
```

Ou para foto (fase 1 — URL externa):

```json
{
  "evidenceType": "photo",
  "fileUrl": "https://cdn.example.com/evidencia.jpg",
  "content": "Foto do defeito"
}
```

### `POST /api/v1/disputes/{id}/resolve` — Request body

```json
{
  "resolution": "refund_partial",
  "clientRefundAmount": 60.00,
  "professionalAmount": 40.00,
  "adminNotes": "Servico parcialmente concluido conforme evidencias."
}
```

### `DisputeResponse`

```json
{
  "id": "uuid",
  "orderId": "uuid",
  "openedBy": "uuid",
  "reason": "Servico nao foi realizado conforme combinado",
  "status": "resolved",
  "resolution": "refund_partial",
  "clientRefundAmount": 60.00,
  "professionalAmount": 40.00,
  "resolvedBy": "uuid",
  "resolvedAt": "2026-04-25T15:00:00Z",
  "openedAt": "2026-04-25T10:00:00Z",
  "adminNotes": null
}
```

> `adminNotes` so e exibido quando o requester e admin. Para outros, sempre `null`.

---

## 7. Resolucao e invariante financeira

O admin informa `resolution` + os valores. O service valida que a soma bate com `order.total_amount`:

| Resolution | Comportamento do service |
|---|---|
| `refund_full` | `clientRefundAmount = totalAmount`, `professionalAmount = 0` (calculado automaticamente) |
| `release_to_pro` | `clientRefundAmount = 0`, `professionalAmount = totalAmount` (calculado automaticamente) |
| `refund_partial` | Admin informa ambos; service valida `clientRefundAmount + professionalAmount == totalAmount` |

O processamento financeiro real (via Asaas) e um TODO do modulo `payment` — a `Dispute` armazena os valores para quando esse modulo existir.

---

## 8. Integracao com chat e notificacoes

### Mensagens de sistema no chat

| Evento | Mensagem |
|---|---|
| Disputa aberta | `"Disputa aberta pelo cliente. Motivo: {reason}"` |
| Marcada como under_review | `"Um administrador esta analisando a disputa."` |
| Resolvida | `"Disputa resolvida. {descricao da resolucao}"` |

### Notificacoes (tipos ja existentes em `NotificationType`)

| Evento | Destinatario | Tipo |
|---|---|---|
| Abertura | Profissional | `dispute_opened` |
| Under review | Cliente e profissional | `request_status_update` |
| Resolucao | Cliente e profissional | `dispute_resolved` |

---

## 9. Estrutura de pacotes

```
dispute/
├── controller/
│   └── DisputeController.java             # REST /api/v1/disputes e /api/v1/orders/{id}/disputes
├── service/
│   ├── DisputeService.java                # Interface
│   └── DisputeServiceImpl.java            # openDispute, markUnderReview, resolve, addEvidence, list...
├── repository/
│   ├── DisputeRepository.java
│   └── DisputeEvidenceRepository.java
├── domain/
│   ├── Dispute.java                       # extends PostgresEntity + @Version para optimistic locking
│   ├── DisputeEvidence.java               # extends PostgresEntity
│   ├── DisputeStatus.java                 # enum { open, under_review, resolved }
│   ├── DisputeResolution.java             # enum { refund_full, refund_partial, release_to_pro }
│   └── EvidenceType.java                  # enum { text, photo }
├── dto/
│   ├── OpenDisputeRequest.java            # reason (max 2000 chars)
│   ├── AddEvidenceRequest.java            # evidenceType, content, fileUrl
│   ├── ResolveDisputeRequest.java         # resolution, clientRefundAmount, professionalAmount, adminNotes
│   ├── UpdateDisputeStatusRequest.java    # transicao para under_review
│   ├── DisputeResponse.java               # todos os campos (adminNotes = null para nao-admins)
│   └── DisputeEvidenceResponse.java
├── mapper/
│   ├── DisputeMapper.java                 # @Component manual — recebe boolean isAdmin
│   └── DisputeEvidenceMapper.java
└── exception/
    ├── DisputeNotFoundException.java       # 404
    ├── DisputeAlreadyExistsException.java  # 409
    ├── DisputeWindowExpiredException.java  # 400 — janela de 24h expirada
    └── DisputeStatusTransitionException.java  # 400 — transicao invalida
```

---

## 10. Mudancas em modulos existentes

### `GlobalExceptionHandler`

| Exception | HTTP |
|---|---|
| `DisputeNotFoundException` | 404 |
| `DisputeAlreadyExistsException` | 409 |
| `DisputeWindowExpiredException` | 400 |
| `DisputeStatusTransitionException` | 400 |

### Por que `DisputeService` acessa `OrderRepository` diretamente

Ao abrir uma disputa, e necessario transitar `order.status` para `disputed` e registrar em `order_status_history`. O `DisputeService` acessa `OrderRepository` e `OrderStatusHistoryRepository` diretamente em vez de chamar `OrderService` para evitar acoplamento circular — o `OrderService` nao expoe um metodo genericamente chamavel de fora sem contexto semantico proprio.

---

## 11. Migration

**`V17__create_disputes.sql`** — cria os enums `dispute_status`, `dispute_resolution`, `evidence_type` e as tabelas `disputes` e `dispute_evidences`.

Nota: `orders.dispute_deadline` e `orders.pro_completed_at` ja existem na tabela (adicionados em `V12__create_orders.sql`). O enum `order_status` ja inclui `disputed`. Nenhuma alteracao nas tabelas existentes e necessaria.

---

## 12. Escopo fora deste plano

| Item | Pre-requisito |
|---|---|
| Processamento financeiro na resolucao (Asaas) | Modulo `payment` |
| Auto-complete apos 24h sem acao do cliente | Decisao de produto + modulo `payment` |
| Upload de fotos de evidencia via MinIO | Plano 6 (storage) |
| Admin como terceiro no chat da disputa | Chat fase 2 |
| Escalacao automatica apos N horas | Decisao de produto |

---

*All Set — Projeto Integrador 1 — UNIFOR — 2026*
