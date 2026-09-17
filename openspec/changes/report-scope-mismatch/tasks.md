## 1. Banco de dados

- [x] 1.1 Criar `src/main/resources/db/migration/V25__add_scope_mismatch_to_orders.sql` com
      `ALTER TABLE orders ADD COLUMN scope_mismatch BOOLEAN NOT NULL DEFAULT false;` e
      verificar que a aplicação sobe sem erro de migration (`./mvnw spring-boot:run` ou
      `FlywaySchemaSmokeTest`)

## 2. Scaffolding (DTO, domínio, contrato de service)

- [x] 2.1 Criar `order/dto/ReportScopeMismatchRequest.java` — record com
      `String description` validado com `@NotBlank`, `@Size(max = 500)`, `@NoHtml`
      (mesmo padrão de `CancelOrderRequest.reason`) e verificar que compila
- [x] 2.2 Adicionar campo `private boolean scopeMismatch` (com getter/setter via Lombok já
      usado na classe) em `order/domain/Order.java`, mapeado para a coluna `scope_mismatch`,
      e verificar que compila
- [x] 2.3 Adicionar a assinatura `OrderResponse reportScopeMismatch(UUID orderId, UUID
      professionalUserId, ReportScopeMismatchRequest request)` em `order/service/OrderService.java`
      e verificar que o projeto compila com um método stub temporário (ex:
      `throw new UnsupportedOperationException()`) em `OrderServiceImpl`

## 3. Testes (escrever antes da implementação — devem falhar/vermelhos nesta etapa)

- [x] 3.1 Em `src/test/java/com/allset/api/order/service/OrderServiceImplTest.java`,
      adicionar teste do caminho feliz: pedido em `accepted` atribuído ao profissional
      autenticado → após `reportScopeMismatch`, `order.getStatus() == cancelled`,
      `order.isScopeMismatch() == true`, `recordTransition`/`historyRepository.save`
      chamado, e `notificationService.notifyUser` chamado para o cliente com
      `NotificationType.request_status_update` — teste deve falhar (stub lança
      `UnsupportedOperationException`)
- [x] 3.2 Adicionar teste: pedido em status diferente de `accepted` (ex: `pending`) →
      `reportScopeMismatch` lança `OrderStatusTransitionException`, sem chamar `save`
- [x] 3.3 Adicionar teste: requisitante não é o profissional designado do pedido →
      `reportScopeMismatch` lança `OrderNotFoundException`, sem chamar `save`
- [x] 3.4 Rodar `./mvnw test -Dtest=OrderServiceImplTest` e confirmar que os 3 testes novos
      falham por causa do stub (vermelho, antes da implementação real)

## 4. Implementação do service

- [x] 4.1 Em `OrderServiceImpl`, extrair o helper privado `isAssignedProfessional(Order
      order, UUID requesterId)` a partir da lógica hoje inline em `cancelOrder` (linhas
      ~780-783), e atualizar `cancelOrder` para usar o helper — verificar que os testes
      existentes de `cancelOrder`/fluxo de cancelamento continuam passando
- [x] 4.2 Implementar `reportScopeMismatch`: `findActive(orderId)`, guard de status
      (`accepted`, senão `OrderStatusTransitionException`), guard de ownership via o helper
      do passo 4.1 (senão `OrderNotFoundException`), setar `cancelledAt`, `cancelReason =
      request.description()`, `status = cancelled`, `scopeMismatch = true`, salvar,
      `recordTransition`, notificar cliente (`NotificationType.request_status_update`) —
      verificar que os 3 testes do passo 3 agora passam (verde)

## 5. Endpoint

- [x] 5.1 Adicionar `POST /api/v1/orders/{id}/report-scope-mismatch` em
      `OrderController.java`, `@PreAuthorize("hasAuthority('professional')")`, seguindo o
      mesmo estilo de `@Operation`/`@ApiResponses` do endpoint `cancel` vizinho, e verificar
      que o Swagger (`/swagger-ui.html` em `dev`) lista o novo endpoint corretamente

## 6. Exposição na resposta

- [x] 6.1 Adicionar o campo `scopeMismatch` em `order/dto/OrderResponse.java` e no método
      `toResponse(...)` de `order/mapper/OrderMapper.java` (mapper hand-written deste
      módulo — seguir o padrão existente do arquivo, não introduzir MapStruct aqui) e
      verificar que `OrderMapper` compila e que um teste existente de mapper/response
      cobre o novo campo (adicionar um assert se não houver teste de mapper para o módulo)

## 7. Verificação final

- [x] 7.1 Rodar a suíte completa (`./mvnw test`) — `OrderServiceImplTest` (unit, o módulo
      tocado por esta change) passa 100%. A suíte completa tem 2 bugs pré-existentes e
      não relacionados a esta change, confirmados via `git stash` comparando com a baseline:
      (1) `AppProperties.geocodingUserAgent` é `@NotBlank` mas nenhum dos 9 testes
      `@SpringBootTest` define esse valor nem há `GEOCODING_USER_AGENT` no ambiente, travando
      o carregamento do ApplicationContext de todo teste de integração; (2) com esse valor
      suprido manualmente, `createUser(...)` nos testes de integração não preenche
      `birth_date`, que é `NOT NULL` no schema — quebra o insert. Nenhum dos dois envolve o
      módulo `order` nem este código; reportado ao usuário para decisão separada
- [x] 7.2 Rodar `./mvnw clean package -DskipTests` para checagem final de compilação

## 8. Banco de dados (renegociação de preço no Express)

- [x] 8.1 Criar `src/main/resources/db/migration/V26__add_pending_price_proposal_to_orders.sql`
      com as 3 colunas nullable (`pending_price_amount numeric(10,2)`,
      `pending_price_reason text`, `pending_price_proposed_at timestamptz`) e verificar que a
      aplicação sobe sem erro de migration

## 9. Scaffolding (DTOs, domínio, contrato de service)

- [x] 9.1 Criar `order/dto/ProposeNewPriceRequest.java` — record com `BigDecimal newAmount`
      (`@NotNull @Positive`) e `String reason` (`@NotBlank @Size(max=500) @NoHtml`) e
      verificar que compila
- [x] 9.2 Criar `order/dto/RespondNewPriceRequest.java` — record com `boolean accepted` e
      verificar que compila
- [x] 9.3 Adicionar `pendingPriceAmount` (`BigDecimal`), `pendingPriceReason` (`String`),
      `pendingPriceProposedAt` (`Instant`) — todos nullable — em `order/domain/Order.java`,
      mapeados pras 3 colunas novas, e verificar que compila
- [x] 9.4 Adicionar as assinaturas `OrderResponse proposeNewPrice(UUID orderId, UUID
      professionalUserId, ProposeNewPriceRequest request)` e `OrderResponse
      respondNewPrice(UUID orderId, UUID clientId, RespondNewPriceRequest request)` em
      `order/service/OrderService.java`, com stubs temporários em `OrderServiceImpl` e
      verificar que compila

## 10. Testes — proposeNewPrice (escrever antes da implementação, vermelhos nesta etapa)

- [x] 10.1 Teste do caminho feliz: pedido Express `accepted` sem proposta pendente,
      atribuído ao profissional autenticado → após `proposeNewPrice`,
      `order.getPendingPriceAmount()`/`getPendingPriceReason()`/`getPendingPriceProposedAt()`
      preenchidos, `order.getStatus()` continua `accepted`, `notificationService.notifyUser`
      chamado pro cliente
- [x] 10.2 Teste: já existe proposta pendente → lança exceção, sem alterar a proposta
      existente
- [x] 10.3 Teste: pedido em modo `on_demand` → lança exceção
- [x] 10.4 Teste: pedido fora de `accepted`, e teste: requisitante não é o profissional
      designado → mesmas exceções do `reportScopeMismatch` (`OrderStatusTransitionException`
      / `OrderNotFoundException`)
- [x] 10.5 Rodar `./mvnw test -Dtest=OrderServiceImplTest` e confirmar que os testes novos
      falham por causa do stub (vermelho)

## 11. Implementação — proposeNewPrice

- [x] 11.1 Implementar `proposeNewPrice` em `OrderServiceImpl` seguindo os guards do
      requirement da spec (ownership, status, mode, proposta já pendente), gravar os 3
      campos, notificar cliente — verificar que os testes do passo 10 passam (verde)

## 12. Testes — respondNewPrice e guard do completeByPro (vermelhos nesta etapa)

- [x] 12.1 Teste: cliente aceita proposta pendente → `baseAmount`/`platformFee`/`totalAmount`
      recalculados com a fórmula de `PLATFORM_FEE_RATE` já usada em `clientRespond`, os 3
      campos pendentes voltam a `null`, `order.getStatus()` continua `accepted`,
      profissional notificado
- [x] 12.2 Teste: cliente recusa proposta pendente → pedido `cancelled`,
      `scopeMismatch == true`, campos pendentes voltam a `null`, profissional notificado
- [x] 12.3 Teste: sem proposta pendente, e teste: requisitante não é o cliente do pedido →
      exceções apropriadas, sem alterar o pedido
- [x] 12.4 Teste: `completeByPro` com proposta de preço pendente → lança exceção, sem
      alterar o status do pedido
- [x] 12.5 Rodar `./mvnw test -Dtest=OrderServiceImplTest` e confirmar vermelho nos testes
      novos (respondNewPrice ainda stub; guard do completeByPro ainda não existe)

## 13. Implementação — respondNewPrice e guard do completeByPro

- [x] 13.1 Implementar `respondNewPrice`: aceitar recalcula valores e limpa os campos
      pendentes; recusar reaproveita o mesmo bloco de cancelamento de
      `reportScopeMismatch` (extrair um helper privado se necessário para evitar duplicar);
      limpar os campos pendentes em ambos os casos
- [x] 13.2 Adicionar guard em `completeByPro`: rejeitar se `pendingPriceAmount != null`
- [x] 13.3 Rodar `./mvnw test -Dtest=OrderServiceImplTest` e confirmar que todos os testes
      dos passos 10 e 12 passam (verde)

## 14. Endpoints

- [x] 14.1 Adicionar `POST /api/v1/orders/{id}/express/propose-new-price`
      (`@PreAuthorize("hasAuthority('professional')")`) e
      `POST /api/v1/orders/{id}/express/respond-new-price`
      (`@PreAuthorize("hasAuthority('client')")`) em `OrderController.java`, seguindo o
      mesmo estilo de `@Operation`/`@ApiResponses` dos endpoints vizinhos, e verificar que o
      Swagger lista os dois endpoints corretamente

## 15. Exposição na resposta

- [x] 15.1 Adicionar `pendingPriceAmount`, `pendingPriceReason`, `pendingPriceProposedAt` em
      `order/dto/OrderResponse.java` e no método `toResponse(...)` de
      `order/mapper/OrderMapper.java`, e atualizar o helper `toResponse` de teste — verificar
      que compila

## 16. Verificação final

- [x] 16.1 Rodar a suíte completa (`./mvnw test`) — 19/21 em `OrderServiceImplTest`; as 2
      falhas são as mesmas pré-existentes já confirmadas antes desta change (via `git
      stash`, não relacionadas ao módulo `order` nem a este código): mojibake de encoding em
      `createExpressOrderShouldPersistQueueAndNotifyProfessionals` e um bug de mock
      pré-existente em `clientRespondShouldChooseProposalCreateConversationAndNotifyParticipants`.
      Todos os 13 testes novos desta change (reportScopeMismatch, proposeNewPrice,
      respondNewPrice, guard do completeByPro) passam
- [x] 16.2 Rodar `./mvnw clean package -DskipTests` para checagem final de compilação

## 17. Correção pós-review — proposta pendente sobrevivendo a cancelamento

Achado em code review (alta severidade): `cancelOrder`/`reportScopeMismatch` não limpavam
`pendingPrice*`, e `respondNewPrice` não revalidava `mode`/`status` — uma proposta pendente
podia ser respondida depois do pedido já ter sido cancelado por outra via.

- [x] 17.1 Escrever testes que reproduzem o bug (vermelhos): `respondNewPrice` com pedido
      já `cancelled` mas `pendingPriceAmount` ainda setado; `respondNewPrice` com pedido
      `on_demand` mas `pendingPriceAmount` ainda setado; `cancelOrder` não limpando
      `pendingPrice*` — confirmar que os 3 falham antes da correção
- [x] 17.2 Adicionar guard `mode == express && status == accepted` em `respondNewPrice`
      antes de checar `pendingPriceAmount`; adicionar `clearPendingPriceProposal(order)` em
      `cancelOrder`; extrair helper privado `cancelDueToScopeMismatch` reaproveitado por
      `reportScopeMismatch` e pela recusa em `respondNewPrice` (elimina a duplicação
      apontada e garante a limpeza em todo caminho de cancelamento por escopo divergente) —
      verificar que os 3 testes do passo 17.1 passam (verde)
- [x] 17.3 Adicionar `@Digits(integer = 8, fraction = 2)` em `ProposeNewPriceRequest.newAmount`,
      igual ao padrão já usado em `ResolveDisputeRequest`/`CreateSubscriptionPlanRequest`
- [x] 17.4 Rodar `./mvnw test -Dtest=OrderServiceImplTest` e `./mvnw clean package -DskipTests`
      e confirmar que nada regrediu
