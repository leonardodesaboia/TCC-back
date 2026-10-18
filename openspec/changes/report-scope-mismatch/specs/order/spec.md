## Purpose

Define o ciclo de vida do pedido (Order) na AllSet — criação, aceite, execução e
encerramento, nos modos Express e On Demand. Esta spec inicial cobre apenas os ajustes
estruturados pós-aceite: sinalização de escopo divergente (ambos os modos), renegociação de
valor (só Express) e troca de serviço (só On Demand). O restante do ciclo de vida permanece
implícito no código até uma change futura documentá-lo.

## ADDED Requirements

### Requirement: Profissional pode sinalizar escopo divergente em pedido aceito
O sistema SHALL permitir que o profissional designado de um pedido em status `accepted`
sinale que o escopo descoberto no local diverge do que o cliente descreveu na criação do
pedido. Ao sinalizar, o sistema SHALL cancelar o pedido imediatamente, sem custo para
nenhuma das partes, e SHALL marcar o cancelamento como decorrente de escopo divergente de
forma estruturada e distinguível de um cancelamento comum.

#### Scenario: Profissional sinaliza escopo divergente com sucesso
- **GIVEN** um pedido em status `accepted`, atribuído ao profissional autenticado
- **WHEN** o profissional envia uma descrição da divergência encontrada
- **THEN** o pedido passa para o status `cancelled`, sem multa ou custo associado
- **THEN** o motivo do cancelamento fica marcado de forma estruturada como escopo
  divergente, distinto de um cancelamento comum
- **THEN** o cliente recebe uma notificação explicando que o profissional identificou
  divergência de escopo e que o pedido foi cancelado sem custo

#### Scenario: Rejeitado fora do status accepted
- **GIVEN** um pedido que não está em status `accepted` (ex: `pending`, `completed_by_pro`,
  `completed`, `cancelled` ou `disputed`)
- **WHEN** o profissional tenta sinalizar escopo divergente
- **THEN** o sistema rejeita a operação com um erro de transição de status inválida,
  sem alterar o pedido

#### Scenario: Rejeitado quando requisitante não é o profissional do pedido
- **GIVEN** um pedido em status `accepted` atribuído a um profissional específico
- **WHEN** um usuário autenticado que não é esse profissional (outro profissional, o
  próprio cliente, ou um terceiro) tenta sinalizar escopo divergente
- **THEN** o sistema rejeita a operação como se o pedido não existisse para esse
  requisitante, sem vazar se o pedido existe ou pertence a outra pessoa

#### Scenario: Descrição da divergência é obrigatória
- **GIVEN** um pedido em status `accepted`, atribuído ao profissional autenticado
- **WHEN** o profissional tenta sinalizar escopo divergente sem descrição, com descrição
  vazia, ou com descrição contendo HTML/script
- **THEN** o sistema rejeita a requisição com erro de validação, sem alterar o pedido

### Requirement: Profissional pode propor novo preço em pedido Express aceito
O sistema SHALL permitir que o profissional designado de um pedido Express em status
`accepted` proponha um novo valor para o serviço, como alternativa a cancelar direto quando
encontra escopo divergente no local. A proposta SHALL ficar pendente no próprio pedido, sem
alterar seu status, até o cliente responder. Só uma proposta SHALL poder estar pendente por
vez em um mesmo pedido.

#### Scenario: Profissional propõe novo preço com sucesso
- **GIVEN** um pedido Express em status `accepted`, atribuído ao profissional autenticado,
  sem proposta de preço pendente
- **WHEN** o profissional envia um novo valor positivo e um motivo
- **THEN** o pedido registra a proposta pendente (valor, motivo, data) e permanece em
  status `accepted`
- **THEN** o cliente recebe uma notificação sobre a nova proposta de preço

#### Scenario: Rejeitado quando já existe proposta pendente
- **GIVEN** um pedido Express em status `accepted` com uma proposta de preço já pendente
- **WHEN** o profissional tenta propor um novo preço novamente
- **THEN** o sistema rejeita a operação, sem alterar a proposta pendente existente

#### Scenario: Rejeitado em pedido On Demand
- **GIVEN** um pedido em modo On Demand, em qualquer status
- **WHEN** o profissional designado tenta propor um novo preço
- **THEN** o sistema rejeita a operação — este mecanismo só existe para pedidos Express

#### Scenario: Rejeitado fora do status accepted ou quando requisitante não é o profissional
- **GIVEN** um pedido Express que não está em `accepted`, OU um pedido Express `accepted`
  cujo requisitante não é o profissional designado
- **WHEN** a proposta de novo preço é enviada
- **THEN** o sistema rejeita a operação, seguindo as mesmas regras de status e ownership do
  requirement de sinalização de escopo divergente

### Requirement: Cliente responde à proposta de novo preço
O sistema SHALL permitir que o cliente aceite ou recuse uma proposta de preço pendente em
seu pedido Express. Ao aceitar, o sistema SHALL recalcular os valores do pedido (base, fee
da plataforma, total) com o novo preço e o pedido SHALL permanecer `accepted`. Ao recusar,
o sistema SHALL cancelar o pedido sem custo para nenhuma das partes, pelo mesmo mecanismo
estruturado usado quando o profissional cancela direto por escopo divergente.

#### Scenario: Cliente aceita o novo preço
- **GIVEN** um pedido Express `accepted` com uma proposta de preço pendente, pertencente ao
  cliente autenticado
- **WHEN** o cliente aceita a proposta
- **THEN** o valor base, a taxa da plataforma e o total do pedido são recalculados a partir
  do novo valor
- **THEN** a proposta pendente é resolvida (deixa de existir) e o pedido permanece
  `accepted`
- **THEN** o profissional recebe uma notificação de que o novo preço foi aceito

#### Scenario: Cliente recusa o novo preço
- **GIVEN** um pedido Express `accepted` com uma proposta de preço pendente, pertencente ao
  cliente autenticado
- **WHEN** o cliente recusa a proposta
- **THEN** o pedido é cancelado sem custo para nenhuma das partes, marcado como decorrente
  de escopo divergente (mesmo sinal estruturado do cancelamento direto)
- **THEN** a proposta pendente é resolvida (deixa de existir)
- **THEN** o profissional recebe uma notificação de que o pedido foi cancelado

#### Scenario: Rejeitado quando não há proposta pendente ou requisitante não é o cliente do pedido
- **GIVEN** um pedido Express sem proposta de preço pendente, OU cujo requisitante não é o
  cliente dono do pedido
- **WHEN** uma resposta à proposta é enviada
- **THEN** o sistema rejeita a operação, sem alterar o pedido

#### Scenario: Rejeitado quando o pedido não está mais accepted/Express, mesmo com proposta pendente
- **GIVEN** um pedido que tinha uma proposta de preço pendente mas deixou de estar em
  `accepted` (ex: foi cancelado por outra via) ou não é mais Express
- **WHEN** o cliente responde à proposta
- **THEN** o sistema rejeita a operação, sem alterar valores nem status do pedido — a
  proposta pendente por si só não é suficiente, o pedido precisa estar `accepted` e Express
  no momento da resposta

### Requirement: Profissional pode propor troca de serviço em pedido On Demand aceito
O sistema SHALL permitir que o profissional designado de um pedido On Demand em status
`accepted` proponha substituir o serviço contratado por outro serviço (`ProfessionalOffering`)
já publicado e ativo por ele mesmo, como alternativa a cancelar direto quando encontra
escopo divergente no local. A proposta SHALL ficar pendente no próprio pedido, sem alterar
seu status nem os valores atuais, até o cliente responder. Só uma proposta de troca de
serviço SHALL poder estar pendente por vez em um mesmo pedido.

#### Scenario: Profissional propõe troca de serviço com sucesso
- **GIVEN** um pedido On Demand em status `accepted`, atribuído ao profissional
  autenticado, sem proposta de troca de serviço pendente
- **WHEN** o profissional informa o ID de outro serviço seu, ativo, e um motivo
- **THEN** o pedido registra a proposta pendente (serviço, motivo, data) e permanece em
  status `accepted`, com os valores e o serviço atuais inalterados
- **THEN** o cliente recebe uma notificação sobre a nova proposta de troca de serviço

#### Scenario: Rejeitado quando o novo serviço não existe, não está ativo ou não pertence ao profissional designado
- **GIVEN** um pedido On Demand `accepted` atribuído ao profissional autenticado
- **WHEN** o profissional propõe um serviço inexistente, inativo/removido, ou pertencente a
  outro profissional
- **THEN** o sistema rejeita a requisição com erro de validação, sem alterar o pedido

#### Scenario: Rejeitado quando o novo serviço é por hora sem duração definida nem informada
- **GIVEN** um pedido On Demand `accepted` atribuído ao profissional autenticado
- **WHEN** o profissional propõe um serviço de precificação por hora que não tem duração
  própria definida, sem informar `estimatedDurationMinutes` na proposta, ou informando um
  valor que não é múltiplo de 30 minutos
- **THEN** o sistema rejeita a requisição com erro de validação, sem alterar o pedido

#### Scenario: Rejeitado quando já existe proposta de troca de serviço pendente
- **GIVEN** um pedido On Demand em status `accepted` com uma proposta de troca de serviço
  já pendente
- **WHEN** o profissional tenta propor outra troca de serviço
- **THEN** o sistema rejeita a operação, sem alterar a proposta pendente existente

#### Scenario: Rejeitado em pedido Express
- **GIVEN** um pedido em modo Express, em qualquer status
- **WHEN** o profissional designado tenta propor uma troca de serviço
- **THEN** o sistema rejeita a operação — este mecanismo só existe para pedidos On Demand;
  o único ajuste disponível no Express continua sendo a renegociação de valor

#### Scenario: Rejeitado fora do status accepted ou quando requisitante não é o profissional
- **GIVEN** um pedido On Demand que não está em `accepted`, OU um pedido On Demand
  `accepted` cujo requisitante não é o profissional designado
- **WHEN** a proposta de troca de serviço é enviada
- **THEN** o sistema rejeita a operação, seguindo as mesmas regras de status e ownership do
  requirement de sinalização de escopo divergente

### Requirement: Cliente responde à proposta de troca de serviço
O sistema SHALL permitir que o cliente aceite ou recuse uma proposta de troca de serviço
pendente em seu pedido On Demand. Ao aceitar, o sistema SHALL substituir o serviço, a
categoria, a área e a duração estimada do pedido pelos do novo serviço, SHALL recalcular os
valores do pedido (base, fee da plataforma, total) a partir dele, e o pedido SHALL
permanecer `accepted`. Ao recusar, o sistema SHALL cancelar o pedido sem custo para nenhuma
das partes, pelo mesmo mecanismo estruturado usado quando o profissional cancela direto por
escopo divergente.

#### Scenario: Cliente aceita a troca de serviço
- **GIVEN** um pedido On Demand `accepted` com uma proposta de troca de serviço pendente,
  pertencente ao cliente autenticado
- **WHEN** o cliente aceita a proposta
- **THEN** o serviço, a categoria, a área, a duração estimada, o valor base, a taxa da
  plataforma e o total do pedido são atualizados a partir do novo serviço
- **THEN** a proposta pendente é resolvida (deixa de existir) e o pedido permanece
  `accepted`
- **THEN** o profissional recebe uma notificação de que a troca de serviço foi aceita

#### Scenario: Cliente recusa a troca de serviço
- **GIVEN** um pedido On Demand `accepted` com uma proposta de troca de serviço pendente,
  pertencente ao cliente autenticado
- **WHEN** o cliente recusa a proposta
- **THEN** o pedido é cancelado sem custo para nenhuma das partes, marcado como decorrente
  de escopo divergente (mesmo sinal estruturado do cancelamento direto)
- **THEN** a proposta pendente é resolvida (deixa de existir), sem alterar o serviço
  original do pedido
- **THEN** o profissional recebe uma notificação de que o pedido foi cancelado

#### Scenario: Rejeitado quando não há proposta pendente ou requisitante não é o cliente do pedido
- **GIVEN** um pedido On Demand sem proposta de troca de serviço pendente, OU cujo
  requisitante não é o cliente dono do pedido
- **WHEN** uma resposta à proposta é enviada
- **THEN** o sistema rejeita a operação, sem alterar o pedido

#### Scenario: Rejeitado quando o pedido não está mais accepted/On Demand, mesmo com proposta pendente
- **GIVEN** um pedido que tinha uma proposta de troca de serviço pendente mas deixou de
  estar em `accepted` (ex: foi cancelado por outra via) ou não é mais On Demand
- **WHEN** o cliente responde à proposta
- **THEN** o sistema rejeita a operação, sem alterar o serviço nem o status do pedido — a
  proposta pendente por si só não é suficiente, o pedido precisa estar `accepted` e On
  Demand no momento da resposta

### Requirement: Cancelamento de pedido descarta qualquer proposta pendente
O sistema SHALL limpar qualquer proposta pendente — de preço (Express) ou de troca de
serviço (On Demand) — sempre que um pedido for cancelado, por qualquer via (cancelamento
genérico ou sinalização de escopo divergente). Uma proposta não pode sobreviver ao
encerramento do pedido a que pertence.

#### Scenario: Cancelamento genérico limpa proposta de preço pendente
- **GIVEN** um pedido Express `accepted` com uma proposta de preço pendente
- **WHEN** o cliente ou o profissional cancela o pedido pelo cancelamento genérico
- **THEN** o pedido é cancelado normalmente e a proposta de preço pendente deixa de existir

#### Scenario: Cancelamento genérico limpa proposta de troca de serviço pendente
- **GIVEN** um pedido On Demand `accepted` com uma proposta de troca de serviço pendente
- **WHEN** o cliente ou o profissional cancela o pedido pelo cancelamento genérico
- **THEN** o pedido é cancelado normalmente, sem trocar o serviço, e a proposta de troca de
  serviço pendente deixa de existir

### Requirement: Conclusão do serviço bloqueada com proposta pendente
O sistema SHALL impedir que o profissional marque um pedido como concluído
(`completed_by_pro`) enquanto houver uma proposta pendente sem resposta do cliente — de
preço (Express) ou de troca de serviço (On Demand) — já que o escopo e o valor do serviço
precisam estar acordados antes da conclusão.

#### Scenario: Conclusão rejeitada com proposta de preço pendente
- **GIVEN** um pedido Express `accepted` com uma proposta de preço pendente
- **WHEN** o profissional tenta marcar o pedido como concluído
- **THEN** o sistema rejeita a operação, sem alterar o status do pedido

#### Scenario: Conclusão rejeitada com proposta de troca de serviço pendente
- **GIVEN** um pedido On Demand `accepted` com uma proposta de troca de serviço pendente
- **WHEN** o profissional tenta marcar o pedido como concluído
- **THEN** o sistema rejeita a operação, sem alterar o status do pedido
