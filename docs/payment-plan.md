# Plano de Implementacao — Modulo `payment`

> Modulo de pagamentos integrado ao Asaas para escrow, cobrancas, liberacao com fee e reembolso.
> Segue os padroes do projeto: `controller / service / repository / domain / mapper / dto / exception`.

---

## 1. Visao Geral da Arquitetura

### Fluxo de Pagamento no Ciclo de Vida do Pedido

```
Cliente escolhe proposta (client-respond)
    |
    v
[1] Cria Payment (status: PENDING)
    |
    v
[2] Cria cobranca no Asaas (PIX/cartao/boleto)
    |
    v
[3] Webhook Asaas confirma pagamento
    |
    v
[4] Payment -> CONFIRMED (escrow retido)
    |
    v
    ... servico eh executado ...
    |
    v
[5] Cliente confirma conclusao (confirm)
    |
    v
[6] Libera escrow no Asaas (total - 20% fee)
    |
    v
[7] Payment -> RELEASED
```

### Fluxos Alternativos

```
Cancelamento pelo cliente (< 24h da contratacao):
    Payment -> REFUNDED_PARTIAL (50% devolvido, 50% retido como taxa)

Cancelamento pelo cliente (> 24h da contratacao):
    Payment -> REFUNDED (100% devolvido)

Cancelamento pelo profissional (qualquer momento):
    Payment -> REFUNDED (100% devolvido ao cliente)

Disputa aberta:
    Payment -> HELD (escrow congelado ate resolucao admin)

Disputa resolvida a favor do cliente:
    Payment -> REFUNDED

Disputa resolvida a favor do profissional:
    Payment -> RELEASED
```

---

## 2. Entidades de Dominio

### 2.1 `Payment` (entidade principal)

| Campo | Tipo | Descricao |
|---|---|---|
| *herda PostgresEntity* | UUID, Instant x3 | id, createdAt, updatedAt, deletedAt |
| `orderId` | UUID (FK orders) | Pedido associado (1:1) |
| `payerUserId` | UUID (FK users) | Cliente que paga |
| `receiverProfessionalId` | UUID (FK professionals) | Profissional que recebe |
| `status` | PaymentStatus (PG enum) | Estado atual do pagamento |
| `method` | PaymentMethod (PG enum) | PIX, credit_card, boleto |
| `grossAmount` | NUMERIC(10,2) | Valor total cobrado do cliente (base + urgency) |
| `platformFee` | NUMERIC(10,2) | Taxa da plataforma (20% do grossAmount) |
| `netAmount` | NUMERIC(10,2) | Valor repassado ao profissional (gross - fee) |
| `refundAmount` | NUMERIC(10,2) | Valor efetivamente devolvido (parcial ou total) |
| `asaasPaymentId` | VARCHAR(100) | ID da cobranca no Asaas |
| `asaasTransferId` | VARCHAR(100) | ID da transferencia/liberacao no Asaas |
| `pixCopyPaste` | TEXT | Codigo copia-e-cola do PIX |
| `pixQrCodeUrl` | TEXT | URL da imagem QR Code PIX |
| `invoiceUrl` | TEXT | URL do boleto ou fatura |
| `paidAt` | Instant | Quando o pagamento foi confirmado |
| `releasedAt` | Instant | Quando o escrow foi liberado |
| `refundedAt` | Instant | Quando o reembolso foi processado |
| `failureReason` | TEXT | Motivo da falha (se houver) |

### 2.2 `PaymentTransaction` (operacoes financeiras individuais)

Cada operacao financeira com o Asaas eh registrada como uma transaction separada. Um Payment pode ter multiplas transactions (cobranca + reembolso, cobranca + transferencia, etc).

| Campo | Tipo | Descricao |
|---|---|---|
| id | UUID PK | gen_random_uuid() |
| `paymentId` | UUID (FK payments) | Pagamento pai |
| `type` | TransactionType (PG enum) | charge, refund, transfer |
| `status` | TransactionStatus (PG enum) | pending, confirmed, failed |
| `amount` | NUMERIC(10,2) | Valor da operacao |
| `asaasId` | VARCHAR(100) | ID da operacao na API do Asaas |
| `failureReason` | TEXT | Motivo da falha (se houver) |
| `processedAt` | Instant | Quando foi processada/confirmada |
| `createdAt` | Instant | Quando foi criada |

**Exemplos de ciclo de vida:**

Fluxo feliz (pedido concluido):
```
Payment (gross = R$200)
  ├── Transaction: charge    R$200  confirmed  (cliente pagou via PIX)
  └── Transaction: transfer  R$160  confirmed  (liberacao ao pro, -20% fee)
```

Cancelamento pelo cliente < 24h:
```
Payment (gross = R$200)
  ├── Transaction: charge    R$200  confirmed  (cliente pagou)
  └── Transaction: refund    R$100  confirmed  (50% devolvido)
```

Cancelamento antes do pagamento:
```
Payment (gross = R$200)
  └── Transaction: charge    R$200  failed     (cobranca cancelada)
```

Disputa resolvida a favor do cliente:
```
Payment (gross = R$200)
  ├── Transaction: charge    R$200  confirmed  (cliente pagou)
  └── Transaction: refund    R$200  confirmed  (reembolso total por disputa)
```

### 2.3 `PaymentStatusHistory` (audit log imutavel)

| Campo | Tipo | Descricao |
|---|---|---|
| id | UUID PK | gen_random_uuid() |
| `paymentId` | UUID (FK payments) | Pagamento associado |
| `fromStatus` | PaymentStatus | Estado anterior (null na criacao) |
| `toStatus` | PaymentStatus | Novo estado |
| `reason` | TEXT | Motivo da transicao |
| `changedBy` | UUID (FK users) | Quem mudou (null = sistema/webhook) |
| `createdAt` | Instant | Timestamp da transicao |

### 2.4 Enums

**PaymentStatus:**
```
pending          -> cobranca criada, aguardando pagamento
confirmed        -> pagamento confirmado, escrow retido
released         -> escrow liberado ao profissional
refunded         -> reembolso total processado
refunded_partial -> reembolso parcial (cancelamento < 24h)
failed           -> falha no pagamento (cartao recusado, etc)
held             -> congelado por disputa
cancelled        -> cobranca cancelada antes de pagar
```

**PaymentMethod:**
```
pix
credit_card
boleto
```

**TransactionType:**
```
charge           -> cobranca ao cliente
refund           -> estorno ao cliente (total ou parcial)
transfer         -> liberacao do escrow ao profissional
```

**TransactionStatus:**
```
pending          -> operacao criada no Asaas, aguardando confirmacao
confirmed        -> operacao concluida com sucesso
failed           -> operacao falhou
```

### 2.5 Transicoes Validas de Status

```
pending          -> confirmed, failed, cancelled
confirmed        -> released, refunded, refunded_partial, held
held             -> released, refunded
```

---

## 3. Integracao com Asaas

### 3.1 `AsaasClient` (em `integration/asaas/`)

Client HTTP dedicado para comunicacao com a API do Asaas. O service de pagamento nunca chama a API diretamente.

**Operacoes:**

| Metodo | Descricao |
|---|---|
| `createCustomer(name, cpf, email)` | Cria/busca cliente no Asaas |
| `createCharge(customerId, amount, method, description)` | Cria cobranca (PIX/cartao/boleto) |
| `getCharge(asaasPaymentId)` | Consulta status da cobranca |
| `createTransfer(professionalAsaasId, amount)` | Transfere valor ao profissional (liberacao do escrow) |
| `refundCharge(asaasPaymentId, amount)` | Estorna cobranca (total ou parcial) |
| `cancelCharge(asaasPaymentId)` | Cancela cobranca pendente |

**Configuracao (AppProperties):**

| Variavel | Obrigatoria | Descricao |
|---|---|---|
| `ASAAS_API_KEY` | Sim | API key do Asaas |
| `ASAAS_BASE_URL` | Nao | Padrao: `https://sandbox.asaas.com/api/v3` |
| `ASAAS_WEBHOOK_TOKEN` | Sim | Token para validar webhooks |

### 3.2 DTOs de integracao (em `integration/asaas/dto/`)

**Request DTOs:**
- `AsaasCreateCustomerRequest` — name, cpfCnpj, email
- `AsaasCreateChargeRequest` — customer, billingType, value, description, externalReference
- `AsaasTransferRequest` — walletId, value
- `AsaasRefundRequest` — value, description

**Response DTOs:**
- `AsaasCustomerResponse` — id, name, cpfCnpj
- `AsaasChargeResponse` — id, status, billingType, value, pixQrCodeUrl, pixCopyPaste, invoiceUrl
- `AsaasTransferResponse` — id, status, value
- `AsaasWebhookEvent` — event, payment (nested object com id, status, value, externalReference)

### 3.3 Webhook Controller (`/api/v1/payments/webhook/asaas`)

- **Publico** (sem JWT) — autenticado via token no header `asaas-access-token`
- Valida token antes de processar
- Eventos tratados:
  - `PAYMENT_CONFIRMED` / `PAYMENT_RECEIVED` -> transiciona payment para `confirmed`
  - `PAYMENT_OVERDUE` / `PAYMENT_DELETED` -> transiciona payment para `failed`
  - `PAYMENT_REFUNDED` -> transiciona payment para `refunded`

---

## 4. Endpoints REST

### 4.1 Pagamento (fluxo principal)

| Metodo | Caminho | Acesso | Descricao |
|---|---|---|---|
| `POST /api/v1/orders/{orderId}/payment` | client | Cria cobranca para o pedido aceito |
| `GET /api/v1/orders/{orderId}/payment` | client, professional, admin | Consulta pagamento do pedido |
| `GET /api/v1/payments/{id}` | client, professional, admin | Consulta pagamento por ID |
| `GET /api/v1/payments` | admin | Lista pagamentos (paginado, filtros) |

### 4.2 Webhook (integracao Asaas)

| Metodo | Caminho | Acesso | Descricao |
|---|---|---|---|
| `POST /api/v1/payments/webhook/asaas` | publico (token) | Recebe eventos do Asaas |

### 4.3 Admin (operacoes manuais)

| Metodo | Caminho | Acesso | Descricao |
|---|---|---|---|
| `POST /api/v1/payments/{id}/release` | admin | Libera escrow manualmente (pos-disputa) |
| `POST /api/v1/payments/{id}/refund` | admin | Reembolsa manualmente (pos-disputa) |

---

## 5. DTOs da API

### Request DTOs

**`CreatePaymentRequest`**
```java
public record CreatePaymentRequest(
    @NotNull PaymentMethod method
) {}
```

**`AdminReleaseRequest`**
```java
public record AdminReleaseRequest(
    String reason  // motivo da liberacao manual
) {}
```

**`AdminRefundRequest`**
```java
public record AdminRefundRequest(
    @NotNull BigDecimal amount,  // valor a devolver
    @NotBlank String reason
) {}
```

### Response DTOs

**`PaymentResponse`**
```java
public record PaymentResponse(
    UUID id,
    UUID orderId,
    UUID payerUserId,
    UUID receiverProfessionalId,
    PaymentStatus status,
    PaymentMethod method,
    BigDecimal grossAmount,
    BigDecimal platformFee,
    BigDecimal netAmount,
    BigDecimal refundAmount,
    String pixCopyPaste,
    String pixQrCodeUrl,
    String invoiceUrl,
    Instant paidAt,
    Instant releasedAt,
    Instant refundedAt,
    String failureReason,
    List<PaymentTransactionResponse> transactions,
    Instant createdAt,
    Instant updatedAt
) {}
```

**`PaymentTransactionResponse`**
```java
public record PaymentTransactionResponse(
    UUID id,
    TransactionType type,
    TransactionStatus status,
    BigDecimal amount,
    String asaasId,
    String failureReason,
    Instant processedAt,
    Instant createdAt
) {}
```

**`WebhookAckResponse`**
```java
public record WebhookAckResponse(
    boolean received
) {}
```

---

## 6. Excecoes Customizadas

| Excecao | HTTP | Quando |
|---|---|---|
| `PaymentNotFoundException` | 404 | Pagamento nao encontrado |
| `PaymentAlreadyExistsException` | 409 | Pedido ja tem pagamento ativo |
| `PaymentStatusTransitionException` | 400 | Transicao de status invalida |
| `PaymentProcessingException` | 502 | Falha na comunicacao com Asaas |
| `InvalidWebhookSignatureException` | 401 | Token do webhook invalido |

Todas registradas no `GlobalExceptionHandler`.

---

## 7. Migration SQL — `V16__create_payments.sql`

```sql
-- Enums
CREATE TYPE payment_status AS ENUM (
    'pending',
    'confirmed',
    'released',
    'refunded',
    'refunded_partial',
    'failed',
    'held',
    'cancelled'
);

CREATE TYPE payment_method AS ENUM (
    'pix',
    'credit_card',
    'boleto'
);

CREATE TYPE transaction_type AS ENUM (
    'charge',
    'refund',
    'transfer'
);

CREATE TYPE transaction_status AS ENUM (
    'pending',
    'confirmed',
    'failed'
);

-- Tabela principal
CREATE TABLE payments (
    id                         UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                   UUID            NOT NULL REFERENCES orders(id),
    payer_user_id              UUID            NOT NULL REFERENCES users(id),
    receiver_professional_id   UUID            NOT NULL REFERENCES professionals(id),
    status                     payment_status  NOT NULL DEFAULT 'pending',
    method                     payment_method  NOT NULL,
    gross_amount               NUMERIC(10,2)   NOT NULL,
    platform_fee               NUMERIC(10,2)   NOT NULL,
    net_amount                 NUMERIC(10,2)   NOT NULL,
    refund_amount              NUMERIC(10,2),
    asaas_payment_id           VARCHAR(100),
    asaas_transfer_id          VARCHAR(100),
    pix_copy_paste             TEXT,
    pix_qr_code_url            TEXT,
    invoice_url                TEXT,
    paid_at                    TIMESTAMPTZ,
    released_at                TIMESTAMPTZ,
    refunded_at                TIMESTAMPTZ,
    failure_reason             TEXT,
    created_at                 TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at                 TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_payments_order_id ON payments(order_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_payments_payer_user_id ON payments(payer_user_id);
CREATE INDEX idx_payments_receiver_professional_id ON payments(receiver_professional_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_asaas_payment_id ON payments(asaas_payment_id);

COMMENT ON TABLE  payments                             IS 'Pagamentos via escrow Asaas. Um pagamento por pedido.';
COMMENT ON COLUMN payments.gross_amount                IS 'Valor cobrado do cliente (base_amount + urgency_fee do pedido).';
COMMENT ON COLUMN payments.platform_fee                IS 'Taxa da plataforma: 20% do gross_amount, descontada na liberacao.';
COMMENT ON COLUMN payments.net_amount                  IS 'Valor repassado ao profissional: gross_amount - platform_fee.';
COMMENT ON COLUMN payments.refund_amount               IS 'Valor efetivamente devolvido. Cancelamento < 24h = 50%.';
COMMENT ON COLUMN payments.asaas_payment_id            IS 'ID da cobranca na API do Asaas.';
COMMENT ON COLUMN payments.asaas_transfer_id           IS 'ID da transferencia de liberacao no Asaas.';

-- Transacoes financeiras individuais
CREATE TABLE payment_transactions (
    id              UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      UUID                NOT NULL REFERENCES payments(id),
    type            transaction_type    NOT NULL,
    status          transaction_status  NOT NULL DEFAULT 'pending',
    amount          NUMERIC(10,2)       NOT NULL,
    asaas_id        VARCHAR(100),
    failure_reason  TEXT,
    processed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_transactions_payment_id ON payment_transactions(payment_id);
CREATE INDEX idx_payment_transactions_asaas_id ON payment_transactions(asaas_id);
CREATE INDEX idx_payment_transactions_type_status ON payment_transactions(type, status);

COMMENT ON TABLE  payment_transactions              IS 'Cada operacao financeira com o Asaas: cobranca, estorno, transferencia. Um pagamento pode ter multiplas transacoes.';
COMMENT ON COLUMN payment_transactions.type         IS 'charge = cobranca ao cliente | refund = estorno | transfer = liberacao ao profissional.';
COMMENT ON COLUMN payment_transactions.asaas_id     IS 'ID da operacao retornado pela API do Asaas.';
COMMENT ON COLUMN payment_transactions.processed_at IS 'Quando a operacao foi confirmada/processada pelo Asaas.';

-- Historico de status (audit log imutavel)
CREATE TABLE payment_status_history (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id  UUID            NOT NULL REFERENCES payments(id),
    from_status payment_status,
    to_status   payment_status  NOT NULL,
    reason      TEXT,
    changed_by  UUID            REFERENCES users(id),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_status_history_payment_id ON payment_status_history(payment_id);

COMMENT ON TABLE  payment_status_history             IS 'Auditoria imutavel de transicoes de pagamento. Nunca deletar.';
COMMENT ON COLUMN payment_status_history.from_status IS 'null na criacao do pagamento.';
COMMENT ON COLUMN payment_status_history.changed_by  IS 'null = transicao automatica (webhook, scheduler).';
```

---

## 8. Integracao com Modulo `order`

O modulo `order` ja possui TODOs onde o pagamento deve ser integrado:

### 8.1 `clientRespond()` — apos cliente escolher proposta

**Linha 386 de `OrderServiceImpl.java`:**
```java
// TODO: iniciar cobranca via modulo payment
```

**Acao:** Chamar `paymentService.createPaymentForOrder(order)` para criar o Payment com status `pending` e disparar a cobranca no Asaas.

### 8.2 `confirmCompletion()` — apos cliente confirmar conclusao

**Linha 483 de `OrderServiceImpl.java`:**
```java
// TODO: liberar escrow via modulo payment
```

**Acao:** Chamar `paymentService.releaseEscrow(orderId)` para liberar o valor ao profissional (menos 20% fee).

### 8.3 `cancelOrder()` — cancelamento

**Acao:** Adicionar logica de reembolso:
- Se payment.status == `pending` (cliente nao pagou) -> cancelar cobranca no Asaas
- Se payment.status == `confirmed` (escrow retido):
  - Cancelamento < 24h da contratacao -> reembolso de 50%
  - Cancelamento >= 24h ou pelo profissional -> reembolso de 100%

---

## 9. Arquivos a Criar

### Modulo `payment` (19 arquivos)

```
src/main/java/com/allset/api/payment/
    controller/
        PaymentController.java          # Endpoints REST
        AsaasWebhookController.java     # Webhook publico
    service/
        PaymentService.java             # Interface
        PaymentServiceImpl.java         # Implementacao
    repository/
        PaymentRepository.java
        PaymentTransactionRepository.java
        PaymentStatusHistoryRepository.java
    domain/
        Payment.java                    # Entidade JPA
        PaymentTransaction.java         # Operacoes financeiras individuais
        PaymentStatusHistory.java       # Audit log
        PaymentStatus.java              # Enum
        PaymentMethod.java              # Enum
        TransactionType.java            # Enum (charge, refund, transfer)
        TransactionStatus.java          # Enum (pending, confirmed, failed)
    mapper/
        PaymentMapper.java
    dto/
        CreatePaymentRequest.java
        PaymentResponse.java
        PaymentTransactionResponse.java # Response da transacao individual
        AdminReleaseRequest.java
        AdminRefundRequest.java
        WebhookAckResponse.java
    exception/
        PaymentNotFoundException.java
        PaymentAlreadyExistsException.java
        PaymentStatusTransitionException.java
        PaymentProcessingException.java
        InvalidWebhookSignatureException.java
```

### Integracao Asaas (6 arquivos)

```
src/main/java/com/allset/api/integration/asaas/
    AsaasClient.java                    # HTTP client (RestClient)
    AsaasProperties.java                # Config (opcional, pode ficar em AppProperties)
    dto/
        AsaasCreateCustomerRequest.java
        AsaasCreateChargeRequest.java
        AsaasChargeResponse.java
        AsaasWebhookEvent.java
```

### Migration

```
src/main/resources/db/migration/
    V16__create_payments.sql
```

---

## 10. Arquivos a Modificar

| Arquivo | Modificacao |
|---|---|
| `AppProperties.java` | Adicionar `asaasApiKey`, `asaasBaseUrl`, `asaasWebhookToken` |
| `SecurityConfig.java` | Adicionar `/api/v1/payments/webhook/**` em `permitAll()` |
| `GlobalExceptionHandler.java` | Registrar 5 novas excecoes |
| `OrderServiceImpl.java` | Substituir TODOs por chamadas ao PaymentService |
| `application.yml` | Adicionar variaveis Asaas |

---

## 11. Plano de Implementacao — Passos Ordenados

### Fase 1: Fundacao (sem dependencia externa) ✅

**Passo 1 — Migration e Enums** ✅
- Criar `V16__create_payments.sql` (3 tabelas: `payments`, `payment_transactions`, `payment_status_history`)
- Criar `PaymentStatus.java`, `PaymentMethod.java`, `TransactionType.java`, `TransactionStatus.java`

**Passo 2 — Entidades JPA** ✅
- Criar `Payment.java` (extends PostgresEntity, @Entity, @Version para optimistic locking)
- Criar `PaymentTransaction.java` (entidade independente, sem soft delete)
- Criar `PaymentStatusHistory.java` (audit log imutavel)

**Passo 3 — Repositories** ✅
- Criar `PaymentRepository.java`
  - Queries: `findByOrderIdAndDeletedAtIsNull`, `findByAsaasPaymentId`
- Criar `PaymentTransactionRepository.java`
  - Queries: `findAllByPaymentId`, `findByAsaasId`
- Criar `PaymentStatusHistoryRepository.java`

**Passo 4 — DTOs e Mapper** ✅
- Criar todos os DTOs de request e response
- Criar `PaymentMapper.java`

**Passo 5 — Excecoes** ✅
- Criar as 5 excecoes
- Registrar no `GlobalExceptionHandler`

### Fase 2: Integracao Asaas ✅

**Passo 6 — Configuracao** ✅
- Adicionar variaveis Asaas em `AppProperties.java`
- Adicionar variaveis em `application.yml`
- Adicionar `.env.example` com as novas variaveis

**Passo 7 — AsaasClient** ✅
- Criar `integration/asaas/AsaasClient.java` com `RestClient`
- Criar DTOs de integracao (`AsaasCreateChargeRequest`, `AsaasChargeResponse`, `AsaasWebhookEvent`)
- Implementar: `createCharge`, `refundCharge`, `cancelCharge`, `createTransfer`
- Retry com backoff exponencial para falhas transientes

### Fase 3: Logica de Negocio ✅

**Passo 8 — PaymentService (interface + impl)** ✅
- `createPaymentForOrder(Order order, PaymentMethod method)` — cria Payment + Transaction(charge) + cobranca Asaas
- `handleWebhookEvent(AsaasWebhookEvent event)` — processa eventos do Asaas, atualiza Transaction correspondente e transiciona Payment
- `releaseEscrow(UUID orderId)` — cria Transaction(transfer) + transfere no Asaas (gross - 20%)
- `refundPayment(UUID paymentId, BigDecimal amount, String reason)` — cria Transaction(refund) + estorna no Asaas
- `cancelPayment(UUID orderId)` — cancela cobranca pendente, marca Transaction(charge) como failed
- `processOrderCancellation(Order order, UUID requesterId)` — decide reembolso parcial/total e cria Transaction(refund)
- Audit trail em todas as transicoes via `PaymentStatusHistory`
- Cada chamada ao Asaas resulta em uma `PaymentTransaction` rastreavel

**Passo 9 — Controllers** ✅
- `PaymentController.java` — endpoints de consulta e criacao
- `AsaasWebhookController.java` — webhook publico com validacao de token
- Atualizar `SecurityConfig.java` para liberar rota do webhook

### Fase 4: Integracao com Order ✅

**Passo 10 — Hook no OrderServiceImpl** ✅
- Injetar `PaymentService` no `OrderServiceImpl`
- `clientRespond()`: apos aceitar proposta, chamar `paymentService.createPaymentForOrder()`
- `confirmCompletion()`: apos confirmar conclusao, chamar `paymentService.releaseEscrow()`
- `cancelOrder()`: chamar `paymentService.processOrderCancellation()` para decidir reembolso

### Fase 5: Testes ✅

**Passo 11 — Testes unitarios** ✅
- `PaymentServiceImplTest` — transicoes de status, calculo de fee, logica de reembolso
- `AsaasWebhookControllerTest` — validacao de token, processamento de eventos
- `PaymentControllerTest` — endpoints REST com MockMvc

**Passo 12 — Testes de integracao** ✅
- `PaymentIntegrationTest` — fluxo completo com Testcontainers (Postgres + Redis)
- Mock do AsaasClient para testes sem dependencia externa

---

## 12. Observacoes de Implementacao

### Idempotencia do Webhook
O Asaas pode enviar o mesmo evento mais de uma vez. O handler deve ser idempotente: verificar se o pagamento ja esta no status destino antes de transicionar.

### Concorrencia
- Usar `@Version` (optimistic locking) na entidade Payment, assim como no Order
- Webhook e scheduler podem tentar transicionar ao mesmo tempo

### Calculo de Fee
```
grossAmount  = order.totalAmount  (base + urgency)
platformFee  = grossAmount * 0.20
netAmount    = grossAmount - platformFee
```

### Reembolso por Cancelamento
```
Se cancelamento por profissional:
    refundAmount = grossAmount (100%)

Se cancelamento por cliente:
    Se (now - order.createdAt) < 24h:
        refundAmount = grossAmount * 0.50
    Senao:
        refundAmount = grossAmount (100%)
```

### Seguranca do Webhook
- Endpoint publico (sem JWT)
- Validar header `asaas-access-token` contra `ASAAS_WEBHOOK_TOKEN`
- Retornar 200 mesmo em caso de erro interno (evitar retries infinitos do Asaas)
- Logar warnings para eventos nao reconhecidos

---

*Plano gerado em 2026-04-14 — All Set / Modulo de Pagamentos*
