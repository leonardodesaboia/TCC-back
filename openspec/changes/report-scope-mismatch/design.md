## Context

`order/service/OrderServiceImpl.java` já tem um cancelamento genérico (`cancelOrder`,
linha ~776) usado por cliente ou profissional, com motivo em texto livre e sem nenhuma
distinção estruturada de "de quem é a responsabilidade" pelo cancelamento — hoje isso não
importa porque a multa de 24h/50% descrita no CLAUDE.md não está implementada em lugar
nenhum do código (não existe módulo `payment` para cobrá-la). Ver proposal.md - Why para a
motivação do produto.

## Goals / Non-Goals

**Goals:**
- Endpoint dedicado e estreito, só para o profissional designado do pedido, para o único
  caso descrito na proposal.
- Sinal estruturado (`scopeMismatch: boolean`) e não apenas mais um texto livre, para que o
  futuro `payment` consiga isentar a multa programaticamente sem parsing de string.

**Non-Goals:**
- Generalizar `cancelOrder` com uma taxonomia de motivos de cancelamento — não foi pedido e
  criaria superfície nova sem necessidade (YAGNI). Ver proposal.md - Fora de escopo para a
  lista completa do que não muda.

## Decisions

**Renegociação de preço no Express: campos pendentes no `Order`, sem novo `OrderStatus`.**
A alternativa seria um status intermediário (ex: `price_renegotiation_pending`). Descartada
porque o projeto não tem máquina de estados centralizada — vários métodos espalhados pelo
módulo (`cancelOrder`, `completeByPro`, `confirmCompletion`, o scheduler de timeout) fazem
guards inline comparando `OrderStatus`, e um valor novo no enum exigiria revisitar todos
eles para decidir se tratam esse status como "ainda accepted" ou não. Em vez disso, a
proposta pendente vive em 3 colunas nullable (`pendingPriceAmount`, `pendingPriceReason`,
`pendingPriceProposedAt`) enquanto o pedido continua `accepted` — semanticamente "accepted
com uma proposta em aberto" é só um sub-estado observável por esses campos serem não-nulos,
não uma transição de status real. O único guard novo necessário é em `completeByPro`
(ver requirement dedicado na spec), verificando `pendingPriceAmount != null`.

**Reaproveitar o caminho de cancelamento de `reportScopeMismatch` quando o cliente recusa —
via helper privado `cancelDueToScopeMismatch(Order, String, UUID)`.** Na primeira versão
desta change esse bloco (cancelar, marcar `scopeMismatch`, gravar histórico) tinha sido
literalmente duplicado entre `reportScopeMismatch` e a recusa em `respondNewPrice`, em vez
de reutilizado como este documento já dizia que aconteceria. Revisão de código pegou a
divergência e, mais importante, uma consequência real dela: nenhum dos dois caminhos
limpava `pendingPrice*`, então uma proposta pendente sobrevivia ao cancelamento do pedido —
se o pedido fosse cancelado por qualquer outra via enquanto uma proposta estava pendente,
`respondNewPrice` continuava aceitando resposta a essa proposta "fantasma" (recalculando
valores de um pedido já cancelado, ou gravando uma transição de histórico
`accepted → cancelled` mentirosa num pedido que já não estava `accepted`). O helper agora
extraído resolve as duas coisas de uma vez: elimina a duplicação E garante que
`clearPendingPriceProposal` roda em todo cancelamento por escopo divergente. `cancelOrder`
(o cancelamento genérico) também passou a chamar `clearPendingPriceProposal` diretamente,
e `respondNewPrice` ganhou um guard explícito de `mode == express && status == accepted`
antes de sequer olhar pra `pendingPriceAmount` — defesa em profundidade, não depende de
nenhum outro método "lembrar" de limpar o campo.

**`ProposeNewPriceRequest.newAmount` valida `@Digits(integer = 8, fraction = 2)`, igual a
`ResolveDisputeRequest` e `CreateSubscriptionPlanRequest`.** A coluna é `NUMERIC(10,2)`;
sem essa anotação, um valor com mais casas decimais ou dígitos inteiros do que a coluna
comporta chegava ao Hibernate e estourava no flush como 500, em vez de retornar 400 de
validação — revisão de código apontou a inconsistência com o padrão já usado em outros
DTOs monetários do projeto.

**Recálculo de valores usa a mesma fórmula de fee já existente.** `clientRespond` (linha
~608-611) já calcula `platformFee = base * PLATFORM_FEE_RATE` (20%) e `total = base +
urgencyFee`. `respondNewPrice(accepted=true)` reaplica exatamente essa fórmula com o novo
valor no lugar do `base` original — nenhuma constante ou regra de negócio nova.

**Endpoint dedicado em vez de estender `CancelOrderRequest`.** A alternativa seria
adicionar um campo `category`/`reason enum` ao cancelamento genérico existente. Descartada
porque introduziria uma taxonomia de motivos que ninguém pediu (só existe um caso concreto
hoje) e misturaria uma regra de autorização estreita (só o profissional designado, só a
partir de `accepted`) dentro de um endpoint que hoje aceita tanto cliente quanto
profissional em qualquer status pré-terminal. Um endpoint dedicado mantém a regra de
autorização e a transição de status isoladas e óbvias de ler.

**Sem lock pessimista — segue o mesmo padrão do `cancelOrder` vizinho.** Diferente do hotspot
de concorrência do Express (múltiplos profissionais disputando a mesma fila), aqui só o
profissional já designado no pedido pode agir, e ele age uma única vez sobre um recurso que
ele mesmo possui. `cancelOrder` (linha 776) já resolve isso com `findActive()` simples,
confiando no `@Version` otimista da entidade — o novo método segue a mesma escolha por
consistência com o método irmão mais próximo, não com o padrão de lock pessimista do Express.

**Notificação inline, não via `@TransactionalEventListener(AFTER_COMMIT)`.** O padrão de
evento pós-commit documentado no contexto do projeto existe para broadcast de chat e limpeza
de storage — não é como este módulo notifica hoje. `cancelOrder` e `respondOnDemand`
chamam `notifyClient`/`notifyProfessional` diretamente dentro do método `@Transactional` de
classe. Seguir o padrão do arquivo em que o código vive tem prioridade sobre aplicar a regra
geral fora de contexto; introduzir eventos só aqui deixaria o módulo inconsistente consigo
mesmo.

**Novo campo booleano, não um enum de categorias.** Existe exatamente uma distinção binária
necessária hoje (`scopeMismatch` sim/não) — um enum de motivos de cancelamento seria
especular sobre necessidades futuras do módulo `payment` que ainda não existe.

**Reaproveitar `OrderStatusTransitionException`** (já registrada no `GlobalExceptionHandler`,
mapeada para 400) para o guard de status — nenhuma exceção nova é necessária.

**Extrair um pequeno helper privado para a checagem de ownership do profissional.** A lógica
`professionalRepository.findByUserIdAndDeletedAtIsNull(requesterId).map(Professional::getId)
.map(id -> id.equals(order.getProfessionalId())).orElse(false)` já existe inline dentro de
`cancelOrder`. Duplicá-la literalmente pela segunda vez no novo método é o gatilho certo para
extrair um método privado `isAssignedProfessional(Order, UUID)` reutilizado pelos dois
métodos — uma melhoria pequena e localizada ao arquivo que já está sendo tocado, não uma
refatoração à parte.

## Risks / Trade-offs

- **Campo `scopeMismatch` fica sem nenhum consumidor até o módulo `payment` existir** →
  aceitável: é uma coluna booleana, custo de manutenção zero, e o propósito já está
  documentado no comentário da migration e no `openspec/config.yaml`.
- **Nenhuma evidência é exigida do profissional (ex: foto) para sinalizar divergência** — um
  profissional mal-intencionado poderia cancelar pedidos aceitos sem custo arbitrariamente →
  fora de escopo desta change (ver proposal.md); registrado como pergunta em aberto abaixo
  para decisão de produto futura, não bloqueia esta implementação.
- **Sem limite de uso do endpoint** → mitigado naturalmente: cada pedido só pode ser
  sinalizado uma vez (a transição de status sai de `accepted` e não pode ser repetida no
  mesmo pedido).
- **Profissional poderia propor um valor abusivamente alto esperando o cliente recusar (e
  cancelar sem custo, evitando o serviço combinado)** → mesma categoria de risco do
  cancelamento direto (nenhuma evidência é exigida hoje); mitigada da mesma forma — fora de
  escopo desta change, mesma pergunta em aberto abaixo.

## Migration Plan

`V25__add_scope_mismatch_to_orders.sql` (já aplicada):
```sql
ALTER TABLE orders ADD COLUMN scope_mismatch BOOLEAN NOT NULL DEFAULT false;
```

`V26__add_pending_price_proposal_to_orders.sql` (nova):
```sql
ALTER TABLE orders ADD COLUMN pending_price_amount NUMERIC(10,2);
ALTER TABLE orders ADD COLUMN pending_price_reason TEXT;
ALTER TABLE orders ADD COLUMN pending_price_proposed_at TIMESTAMPTZ;
```
Ambas aditivas e compatíveis com dados existentes (colunas nullable, sem default
obrigatório). Sem passo de rollback especial — reverter é remover as colunas, e nenhum
código depende delas até este código ser implantado.

## Open Questions

- Deve haver alguma exigência de evidência (ex: foto) ou limite de uso por profissional para
  coibir abuso do "escopo divergente" como forma de cancelar pedidos sem custo? Não muda a
  spec, a abordagem ou o breakdown de tasks desta change — fica para uma decisão de produto
  futura, possivelmente junto do módulo `payment`.
