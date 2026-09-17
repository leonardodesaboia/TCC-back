## Why

No fluxo Express, o profissional propõe preço vendo apenas a descrição em texto livre do
cliente, a categoria/área e a faixa de distância — sem poder trocar mensagens antes, já que
o chat só abre quando o pedido vira `accepted` (decisão deliberada do produto para evitar
contratação por fora do app). Isso gera casos em que o profissional só descobre que o
escopo real do serviço é diferente do descrito depois de já ter aceitado e chegado no
local. Hoje a única saída é o cancelamento genérico (`POST /orders/{id}/cancel`), que trata
esse caso exatamente igual a um cliente desistindo por capricho — sem nenhum sinal
estruturado que distinga as duas situações.

## What Changes

- Novo endpoint `POST /api/v1/orders/{id}/report-scope-mismatch`, exclusivo do profissional
  designado no pedido, para sinalizar que o escopo descoberto no local diverge do que foi
  descrito na criação do pedido.
- Só permitido quando o pedido está em `accepted` (depois do aceite, antes de qualquer
  conclusão). Fora dessa janela, retorna erro de transição de status — igual ao padrão já
  usado pelas demais operações do módulo.
- Efeito: cancela o pedido imediatamente, sem custo para nenhuma das partes, e marca o
  motivo do cancelamento de forma estruturada (novo campo booleano `scopeMismatch` na
  entidade `Order`) — para que o futuro módulo `payment` possa isentar esse cancelamento da
  multa de 24h/50% (regra de negócio #7 do CLAUDE.md), já que a culpa não é do cliente.
- Cliente é notificado explicando que o profissional identificou que o serviço é diferente
  do descrito e que o pedido foi cancelado sem custo.
- **Só no modo Express**, o profissional ganha uma segunda opção além de cancelar direto:
  propor um novo preço para o escopo real encontrado, em vez de cancelar de cara.
  - Novo endpoint `POST /api/v1/orders/{id}/express/propose-new-price` (profissional,
    `accepted`, só `mode == express`) — grava uma proposta pendente no próprio pedido
    (não cria pedido novo, não muda o status).
  - Novo endpoint `POST /api/v1/orders/{id}/express/respond-new-price` (cliente) — aceita
    (recalcula o valor do pedido e ele continua `accepted`) ou recusa (cancela sem custo,
    pelo mesmo caminho estruturado do `report-scope-mismatch`).
  - `completeByPro` passa a rejeitar se houver proposta de preço pendente não resolvida.
- Não altera a regra de negócio #3 (janela Express) nem #1 (escrow obrigatório) — apenas
  adiciona saídas estruturadas ao ciclo de vida do pedido já aceito.

## Capabilities

### New Capabilities

- `order`: ciclo de vida do pedido (Express e On Demand) — criação, aceite,
  conclusão, cancelamento e, com esta change, sinalização de escopo divergente
  pós-aceite. Não existe spec de `order` hoje; esta é a spec inicial da capability,
  cobrindo apenas o comportamento novo (o restante do ciclo de vida permanece
  implícito no código até uma change futura documentá-lo).

### Modified Capabilities

_Nenhuma — não existe spec anterior para `order` a modificar._

## Fora de escopo

- Qualquer amarração entre o pedido cancelado e a criação de um novo pedido/serviço — o
  profissional pode já ter o serviço correto publicado ou publicar um novo depois, usando o
  módulo `offering` e o fluxo normal de criação de pedido On Demand que já existem. Nenhuma
  ligação nova entre as duas ordens é criada por esta change.
- Qualquer mudança no comportamento do chat — continua abrindo apenas quando o pedido vira
  `accepted`.
- Qualquer lógica real de multa/fee — o módulo `payment` ainda não existe; o campo
  `scopeMismatch` só prepara o terreno para quando essa lógica for implementada.
- Renegociação de preço **no On Demand** — lá o preço vem de um serviço já publicado; a
  única saída pra escopo divergente continua sendo cancelar (o profissional republica um
  serviço com o escopo certo depois, se quiser, usando o módulo `offering` que já existe).
- Múltiplas propostas de preço simultâneas no mesmo pedido Express — só uma proposta pode
  estar pendente por vez; uma nova só pode ser feita depois que a anterior for resolvida
  (aceita ou recusada).

## Impact

- **Banco**: migration `orders.scope_mismatch boolean not null default false` (já aplicada,
  V25) + nova migration V26 adicionando `pending_price_amount numeric`,
  `pending_price_reason text`, `pending_price_proposed_at timestamptz` (todas nullable).
- **Código**: `order/domain/Order.java` (campos novos), `order/dto/` (`ReportScopeMismatchRequest`
  já existente + novos `ProposeNewPriceRequest`, `RespondNewPriceRequest`),
  `order/service/OrderService.java` + `OrderServiceImpl.java` (`reportScopeMismatch` já
  existente + novos `proposeNewPrice`, `respondNewPrice`, guard novo em `completeByPro`),
  `order/controller/OrderController.java` (endpoint já existente + 2 novos),
  `order/mapper/OrderMapper.java` e `order/dto/OrderResponse.java` (expor os campos novos).
- **Reaproveitado sem alteração**: `OrderStatusTransitionException`, `recordTransition`,
  `notifyClient`/`notifyProfessional` (helpers já existentes em `OrderServiceImpl`),
  `NotificationType.request_status_update`, `PLATFORM_FEE_RATE` (fórmula de fee de 20% já
  usada em `clientRespond`), e o próprio caminho de cancelamento de `reportScopeMismatch`
  (reutilizado quando o cliente recusa a proposta de preço).
- Nenhuma API externa nem outro módulo é afetado.
