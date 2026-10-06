## Why

No fluxo Express, o profissional propõe preço vendo apenas a descrição em texto livre do
cliente, a categoria/área e a faixa de distância — sem poder trocar mensagens antes, já que
o chat só abre quando o pedido vira `accepted` (decisão deliberada do produto para evitar
contratação por fora do app). Isso gera casos em que o profissional só descobre que o
escopo real do serviço é diferente do descrito depois de já ter aceitado e chegado no
local. Hoje a única saída é o cancelamento genérico (`POST /orders/{id}/cancel`), que trata
esse caso exatamente igual a um cliente desistindo por capricho — sem nenhum sinal
estruturado que distinga as duas situações.

No fluxo On Demand, o mesmo problema de fundo existe, mas a causa é diferente: o cliente
contrata um `ProfessionalOffering` específico do catálogo do profissional, e o preço do
pedido vem desse serviço publicado, não de uma proposta livre. Quando o escopo real
encontrado no local corresponde a *outro* serviço que o mesmo profissional já oferece (ou
precisa cadastrar na hora), a única saída hoje é cancelar e esperar o cliente abrir um
pedido novo — mesmo com o profissional já no local, disponível, e o ajuste sendo só uma
troca de serviço. A primeira versão desta change já previu esse caso e o listou
deliberadamente em "Fora de escopo", delegando para o fluxo normal de criação de pedido.
Esta revisão fecha essa lacuna: adiciona ao On Demand um mecanismo irmão do Express — só
que trocando o **serviço** (e, por consequência, o preço e a duração), não um valor
isolado.

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
- **Só no modo On Demand**, o profissional ganha o mecanismo equivalente para troca de
  **serviço** (em vez de valor), como terceira alternativa a cancelar direto por escopo
  divergente:
  - Novo endpoint `POST /api/v1/orders/{id}/on-demand/propose-service-change`
    (profissional, `accepted`, só `mode == on_demand`) — referencia um
    `ProfessionalOffering` existente, ativo e do mesmo profissional já designado no pedido
    (o profissional cria esse serviço antes, se ainda não existir, pelo fluxo normal do
    módulo `offering` que já existe — `POST /api/v1/professionals/{id}/offerings`). Grava
    uma proposta pendente no próprio pedido (não cria pedido novo, não muda o status, não
    reatribui o profissional).
  - Novo endpoint `POST /api/v1/orders/{id}/on-demand/respond-service-change` (cliente) —
    aceita (recalcula serviço, área, categoria, duração e valores do pedido a partir do
    novo serviço, e ele continua `accepted`) ou recusa (cancela sem custo, pelo mesmo
    caminho estruturado do `report-scope-mismatch`).
  - `completeByPro` passa a rejeitar também se houver proposta de troca de serviço pendente
    não resolvida (mesmo guard já existente para proposta de preço, agora cobrindo as duas).
  - Campos `serviceId`, `areaId`, `categoryId` e `estimatedDurationMinutes` em `Order`
    deixam de ser imutáveis (`updatable = false`) — passam a poder mudar exatamente uma vez
    por resposta aceita a essa proposta, nunca por nenhum outro caminho.

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

- Qualquer mudança no comportamento do chat — continua abrindo apenas quando o pedido vira
  `accepted`.
- Qualquer lógica real de multa/fee — o módulo `payment` ainda não existe; o campo
  `scopeMismatch` só prepara o terreno para quando essa lógica for implementada.
- Múltiplas propostas (de preço no Express, ou de troca de serviço no On Demand)
  simultâneas no mesmo pedido — só uma proposta pode estar pendente por vez em cada
  mecanismo; uma nova só pode ser feita depois que a anterior for resolvida (aceita ou
  recusada).
- Troca de **serviço** no Express — lá não existe `ProfessionalOffering` associado ao
  pedido (o preço nasce de uma proposta livre, não de um catálogo publicado); o único ajuste
  possível no Express continua sendo de **valor**, via `propose-new-price`/`respond-new-price`
  já existentes. Esta revisão não adiciona nada novo ao Express.
- Criar o `ProfessionalOffering` novo **dentro** do endpoint de troca de serviço — o
  profissional primeiro cadastra o serviço pelo fluxo normal do módulo `offering`
  (`POST /api/v1/professionals/{id}/offerings`, já existente) e só então referencia o
  `id` resultante em `propose-service-change`. Nenhuma lógica de criação de oferta é
  duplicada dentro do módulo `order`.
- Trocar o **profissional** designado no pedido — a troca de serviço só é permitida entre
  ofertas do mesmo profissional já atribuído; não existe reatribuição de pedido a outro
  profissional por este mecanismo.
- Exigir que o novo serviço pertença à mesma categoria/área do pedido original — o
  propósito do mecanismo é justamente cobrir o caso em que o escopo real é de outra
  categoria do catálogo do mesmo profissional.

## Impact

- **Banco**: migration `orders.scope_mismatch boolean not null default false` (já aplicada,
  V25) + migration `pending_price_amount/reason/proposed_at` (já aplicada, V26) + **nova
  migration V27** adicionando `pending_service_id uuid references professional_services(id)`,
  `pending_service_reason text`, `pending_service_estimated_duration_minutes integer`,
  `pending_service_proposed_at timestamptz` (todas nullable) a `orders`.
- **Código (já implementado)**: `order/domain/Order.java` (campos de escopo divergente e
  preço pendente), `order/dto/` (`ReportScopeMismatchRequest`, `ProposeNewPriceRequest`,
  `RespondNewPriceRequest`), `order/service/OrderService.java` + `OrderServiceImpl.java`
  (`reportScopeMismatch`, `proposeNewPrice`, `respondNewPrice`, guard em `completeByPro`),
  `order/controller/OrderController.java` (3 endpoints), `order/mapper/OrderMapper.java` e
  `order/dto/OrderResponse.java`.
- **Código (novo nesta revisão)**: `order/domain/Order.java` (4 campos `pendingService*` +
  remoção de `updatable = false` de `serviceId`/`areaId`/`categoryId`/
  `estimatedDurationMinutes`), novos DTOs `ProposeServiceChangeRequest` (`newServiceId`,
  `reason`, `estimatedDurationMinutes` opcional) e `RespondServiceChangeRequest`
  (`accepted`), `order/service/OrderService.java` + `OrderServiceImpl.java`
  (`proposeServiceChange`, `respondServiceChange`, helper privado
  `resolveOrderPricing(ProfessionalOffering, Integer)` extraído de `createOnDemandOrder`
  para reaproveitar a lógica de preço/duração hourly-vs-fixed, guard adicional em
  `completeByPro`, generalização de `clearPendingPriceProposal` para
  `clearPendingProposals` cobrindo preço e serviço), `order/controller/OrderController.java`
  (2 endpoints novos), `order/mapper/OrderMapper.java` + `order/dto/OrderResponse.java`
  (expor os 4 campos novos).
- **Reaproveitado sem alteração**: `OrderStatusTransitionException`, `OrderNotFoundException`,
  `IllegalArgumentException` (mesma categoria de erro já usada por `createOnDemandOrder`
  para "serviço não encontrado/inativo/sem preço"), `recordTransition`,
  `notifyClient`/`notifyProfessional`, `NotificationType.request_status_update`,
  `PLATFORM_FEE_RATE`, `isAssignedProfessional`, `cancelDueToScopeMismatch` (passa a limpar
  também a proposta de serviço pendente), `offeringRepository`/`categoryRepository`/
  `specialtyRepository` já injetados em `OrderServiceImpl` para `createOnDemandOrder` —
  reutilizados tal qual, nenhuma dependência nova no módulo `order`.
- Nenhuma API externa nem outro módulo é afetado. Nenhuma lógica de criação/edição de
  `ProfessionalOffering` é duplicada — o módulo `offering` permanece o único responsável por
  isso.
