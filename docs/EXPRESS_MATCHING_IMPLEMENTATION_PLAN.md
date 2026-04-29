# Plano de Implementação

## Express por proximidade

Documento base:

- [EXPRESS_MATCHING_SPEC.md](/home/leonardodesaboia/Documents/TCC/TCC-back/docs/EXPRESS_MATCHING_SPEC.md:1)

Objetivo deste plano:

- transformar a especificação em um roteiro executável;
- definir ordem de implementação;
- reduzir retrabalho entre backend, frontend cliente e frontend profissional;
- explicitar contratos, dependências e critérios de aceite técnico.

---

## Visão geral

O trabalho deve ser dividido em 6 trilhas:

1. Contrato e decisões de domínio
2. Backend de geocodificação de endereços
3. Backend de disponibilidade/localização do profissional
4. Backend de matching Express por ondas
5. Frontend cliente
6. Frontend profissional

Dependência principal:

- o matching Express só fica funcional de ponta a ponta depois que cliente e profissional tiverem coordenadas válidas.

Dependência crítica de contrato:

- `express` e `on_demand` precisam ter contratos e comportamentos claramente separados antes de expandir a UI.

---

## Decisões obrigatórias antes de começar

Estas decisões devem ser fechadas primeiro.

### D1. Contrato de `areaId` no `on_demand`

Escolher uma opção:

1. Backend sempre preenche `areaId` em `on_demand`
2. `areaId` é opcional em `on_demand`

Recomendação:

- **Opção 1**

Motivo:

- reduz condicionais no frontend;
- mantém contrato mais uniforme;
- evita fallback visual ambíguo.

### D2. Estratégia de geocodificação de endereço

Escolher fornecedor principal:

1. Google Maps Geocoding API
2. Mapbox Geocoding API

Recomendação:

- **Google Maps Geocoding API**

Motivo:

- menor risco de produto para cadastro de endereço;
- boa documentação e operação;
- fit melhor para geocodificação tradicional de endereço.

### D3. Estratégia de localização do profissional

Escolher escopo do MVP:

1. foreground only
2. foreground + background

Recomendação:

- **MVP com foreground first**

Motivo:

- menor complexidade operacional;
- menor risco com permissões;
- permite testar o fluxo Express sem entrar cedo em problemas de bateria e políticas de sistema.

### D4. Regra de recência da localização

Definir uma janela inicial.

Recomendação:

- localização válida por até `5 minutos`

Motivo:

- simples para MVP;
- coerente com o ciclo inicial de expansão do pedido;
- fácil de observar em logs e ajustar depois.

---

## Fases

## Fase 0. Saneamento do contrato atual

### Objetivo

Remover inconsistências já conhecidas entre `express` e `on_demand` antes de expandir o fluxo.

### Escopo

- impedir consulta de propostas Express para `on_demand` no frontend;
- alinhar contrato de `areaId`;
- corrigir timeline visual por modo;
- garantir que regras dependentes do `mode` só rodem após carregar o pedido base.

### Backend

- decidir e implementar o contrato final de `areaId`;
- ajustar `OrderMapper` e `OrderServiceImpl` se necessário;
- documentar no contrato quais campos são obrigatórios por modo.

### Frontend

- corrigir gating de `useOrderProposals`;
- tornar `areaId` coerente com o contrato definido;
- separar timeline de `express` e `on_demand`.

### Arquivos prováveis

Backend:

- `TCC-back/src/main/java/com/allset/api/order/service/OrderServiceImpl.java`
- `TCC-back/src/main/java/com/allset/api/order/mapper/OrderMapper.java`
- `TCC-back/src/main/java/com/allset/api/order/dto/OrderResponse.java`

Frontend:

- `TCC-front/src/app/(client)/(orders)/[orderId].tsx`
- `TCC-front/src/types/order.ts`
- `TCC-front/src/lib/hooks/useOrders.ts`

### Critério de saída

- nenhum pedido `on_demand` tenta consultar propostas Express;
- contrato `areaId` fica consistente entre backend e frontend;
- timeline do cliente reflete corretamente o modo do pedido.

---

## Fase 1. Geocodificação do endereço do cliente

### Objetivo

Salvar latitude e longitude de cada endereço do cliente usando API externa.

### Escopo funcional

- ao criar endereço, o backend geocodifica e salva coordenadas;
- ao editar endereço, se campos relevantes mudarem, re-geocodifica;
- se a geocodificação falhar, o endereço não pode ser elegível para Express.

### Backend

#### 1. Criar abstração de geocodificação

Criar interface, por exemplo:

- `GeocodingService`

Métodos esperados:

- `geocodeAddress(...)`
- `reverseGeocode(...)` opcional

#### 2. Criar implementação do provedor

Exemplo:

- `GoogleGeocodingService`

Responsabilidades:

- montar request externo;
- normalizar resposta;
- expor coordenadas, endereço formatado, provider id e metadados.

#### 3. Persistir metadados de geocodificação

Avaliar extensão do modelo de endereço para salvar:

- `lat`
- `lng`
- `geocodedFormattedAddress`
- `geocodingProvider`
- `geocodingProviderPlaceId`
- `geocodedAt`
- `geocodingQuality`

Se o modelo atual não comportar isso, criar migration.

#### 4. Integrar no fluxo de endereço

Ao criar/editar endereço:

- geocodificar no backend;
- persistir coordenadas;
- retornar estado pronto para uso no Express.

### Frontend cliente

- manter o cadastro simples;
- exibir erro amigável se a geocodificação falhar;
- diferenciar endereço salvo de endereço apto para Express.

### Arquivos prováveis

Backend:

- `address/service/*`
- `address/dto/*`
- `address/controller/*`
- `src/main/resources/application.yml`
- nova migration em `db/migration/`

Frontend:

- `TCC-front/src/app/(client)/(profile)/addresses/new.tsx`
- `TCC-front/src/app/(client)/(profile)/addresses/index.tsx`
- `TCC-front/src/lib/api/addresses.ts`
- `TCC-front/src/lib/hooks/useAddresses.ts`

### Testes

- teste unitário do `GeocodingService`;
- teste de falha do provedor externo;
- teste de criação de endereço com sucesso;
- teste de endereço sem coordenadas não elegível para Express.

### Critério de saída

- endereços novos saem com coordenadas válidas ou com erro explícito;
- pedidos Express passam a depender apenas do endereço salvo.

---

## Fase 2. Disponibilidade Express e localização do profissional

### Objetivo

Permitir que o profissional habilite Express e envie a localização do próprio celular.

### Escopo funcional

- o profissional ativa/desativa “Disponível para Express”;
- o app solicita permissão;
- o app coleta localização atual do celular;
- o backend salva coordenadas e timestamp;
- sem localização válida, Express não ativa.

### Backend

#### 1. Evoluir o modelo do profissional

Salvar no mínimo:

- `geoLat`
- `geoLng`
- `geoActive`
- `geoCapturedAt`
- `geoAccuracyMeters`
- `geoSource`

Se ainda não existir tudo isso, criar migration.

#### 2. Ajustar contrato de atualização geo

Expandir `UpdateGeoRequest` e resposta correspondente.

Campos recomendados:

- `geoActive`
- `geoLat`
- `geoLng`
- `accuracyMeters`
- `capturedAt`
- `source`

#### 3. Validar recência

Implementar regra de elegibilidade:

- se `geoActive = true` mas `capturedAt` expirou, o profissional não entra na busca.

### Frontend profissional

#### 1. Adicionar toggle de disponibilidade Express

Local provável:

- perfil profissional
- settings profissional

#### 2. Integrar captura de localização

Usar `expo-location`.

Fluxo:

1. pedir foreground permission;
2. capturar posição atual;
3. enviar ao backend;
4. se falhar, não ativar o toggle.

#### 3. Exibir estado da localização

Mostrar:

- ativo;
- desativado;
- permissão negada;
- localização desatualizada;
- localização indisponível.

### Arquivos prováveis

Backend:

- `professional/dto/UpdateGeoRequest.java`
- `professional/service/ProfessionalServiceImpl.java`
- `professional/controller/ProfessionalController.java`
- nova migration em `db/migration/`

Frontend:

- `TCC-front/src/app/(professional)/(profile)/edit.tsx`
- `TCC-front/src/lib/api/professional-management.ts`
- `TCC-front/src/lib/hooks/useProfessionalManagement.ts`
- `TCC-front/src/types/professional-management.ts`
- `app.json` se houver configuração adicional de permissão

### Testes

- ativação sem permissão deve falhar;
- ativação sem lat/lng deve falhar;
- ativação com lat/lng salva `capturedAt`;
- localização expirada torna profissional inelegível.

### Critério de saída

- profissional consegue ativar/desativar Express;
- backend tem localização atualizável e auditável.

---

## Fase 3. Correção da elegibilidade do Express

### Objetivo

Fazer o matching Express usar `professional_specialties`, não `professional_services`.

### Escopo

- substituir a dependência de serviço publicado por especialidade;
- manter filtro por aprovação, geo ativa e recência;
- preservar deduplicação por profissional.

### Backend

#### 1. Corrigir a query de matching

Hoje a busca usa tabela errada.

A nova query deve:

- usar `professional_specialties`;
- filtrar por `category_id`;
- ignorar exigência de `professional_services`.

#### 2. Aplicar recência da localização

Adicionar condição equivalente a:

- `geo_captured_at >= now - window`

#### 3. Manter expansão sem duplicidade

Na query de expansão:

- excluir profissionais já presentes na fila;
- reutilizar mesma regra de elegibilidade.

### Arquivos prováveis

- `TCC-back/src/main/java/com/allset/api/order/repository/ExpressQueueRepository.java`
- `TCC-back/src/main/java/com/allset/api/order/service/OrderServiceImpl.java`
- `TCC-back/src/main/java/com/allset/api/order/service/ExpressWindowProcessor.java`

### Testes

- profissional com especialidade entra na fila mesmo sem serviço publicado;
- profissional sem especialidade não entra;
- profissional com geo vencida não entra;
- profissional já notificado não reaparece em expansão.

### Critério de saída

- Express não depende mais de `professional_services`.

---

## Fase 4. Matching por ondas de 100m / 200m / 300m

### Objetivo

Implementar o envio progressivo do pedido por distância e tempo.

### Escopo funcional

- onda 1: `0-5 min`, `100 m`
- onda 2: `5-10 min`, `200 m`
- onda 3: `10-15 min`, `300 m`
- timeout final: indisponibilidade

### Backend

#### 1. Modelar a etapa atual de busca

Alternativas:

1. derivar pela combinação `searchAttempts + expiresAt`
2. adicionar campo explícito de fase/etapa

Recomendação:

- manter derivação via estado existente no MVP, se o modelo já suportar;
- adicionar campo explícito só se a lógica ficar opaca.

#### 2. Atualizar criação do pedido

Na criação:

- iniciar com `searchRadiusKm = 0.1`
- `searchAttempts = 1`
- `expiresAt = now + 5 min`

#### 3. Atualizar processor/scheduler

No vencimento:

- se nenhuma proposta aceita, expandir para `200 m`;
- depois para `300 m`;
- por fim cancelar por indisponibilidade.

#### 4. Persistir notificação por onda

Manter rastreio de:

- quem foi notificado;
- em qual rodada;
- quando.

### Arquivos prováveis

- `TCC-back/src/main/java/com/allset/api/order/service/OrderServiceImpl.java`
- `TCC-back/src/main/java/com/allset/api/order/service/ExpressWindowProcessor.java`
- `TCC-back/src/main/java/com/allset/api/order/scheduler/ExpressTimeoutScheduler.java`
- possivelmente migration se precisar registrar etapa explicitamente

### Testes

- criação inicia com 100 m;
- sem aceite expande para 200 m;
- sem aceite expande para 300 m;
- sem aceite final cancela com motivo correto;
- aceite interrompe expansão.

### Critério de saída

- fluxo de expansão por ondas funciona sem duplicar notificações.

---

## Fase 5. Exposição de pedidos Express notificados ao profissional

### Objetivo

Permitir que o profissional veja pedidos para os quais foi notificado antes de ser escolhido.

### Escopo

- dashboard;
- lista de pedidos;
- notificações;
- detalhe do pedido.

### Backend

#### 1. Criar listagem para profissional notificado

Opções:

1. estender `GET /api/v1/orders`
2. criar endpoint específico para inbox Express

Recomendação:

- **endpoint específico para pedidos Express notificados**

Motivo:

- evita misturar pedidos atribuídos com pedidos apenas notificados;
- deixa semântica da API mais clara.

Exemplo:

- `GET /api/v1/orders/express/inbox`

Deve retornar:

- pedidos Express pendentes;
- nos quais o profissional está na fila;
- ainda sem resposta do profissional ou com estado relevante para a inbox.

#### 2. Criar endpoint de detalhe elegível ao profissional notificado

O detalhe do pedido Express deve poder ser acessado por:

- cliente dono;
- admin;
- profissional atribuído;
- profissional presente na fila.

### Frontend profissional

#### 1. Dashboard

- mostrar contador de pedidos Express disponíveis;
- listar parte da inbox.

#### 2. Lista de pedidos

Separar visualmente:

- pedidos atribuídos;
- pedidos Express disponíveis para resposta.

#### 3. Notificações

- abrir lista de notificações;
- deep link para o detalhe do pedido.

### Arquivos prováveis

Backend:

- `OrderController.java`
- `OrderService.java`
- `OrderServiceImpl.java`
- `ExpressQueueRepository.java`

Frontend:

- `TCC-front/src/app/(professional)/(dashboard)/index.tsx`
- `TCC-front/src/app/(professional)/(orders)/index.tsx`
- nova tela de notificações profissional, se necessário
- hooks/integrations profissionais

### Testes

- profissional notificado vê pedido na inbox;
- profissional não notificado não vê;
- pedido some da inbox após vínculo final ou expiração;
- deep link via notificação abre detalhe correto.

### Critério de saída

- o profissional consegue enxergar e responder pedidos Express antes da atribuição.

---

## Fase 6. Fluxo de proposta do profissional

### Objetivo

Fechar o loop de aceite/recusa/proposta até a escolha do cliente.

### Escopo

- detalhe do pedido Express para o profissional;
- envio de proposta;
- recusa;
- atualização do cliente em tempo real ou polling.

### Frontend profissional

- tela de detalhe do pedido notificado;
- CTA para recusar;
- CTA para enviar proposta;
- feedback de resposta já enviada.

### Frontend cliente

- atualizar tela de propostas e estado do pedido;
- exibir contagem/status da busca;
- informar indisponibilidade ao final do ciclo.

### Backend

- validar que só profissional notificado pode responder;
- garantir que resposta dupla não seja aceita;
- garantir integridade quando o cliente escolher uma proposta.

### Testes

- profissional notificado aceita e envia valor;
- profissional não notificado recebe erro;
- cliente vê proposta recebida;
- cliente escolhe apenas uma proposta.

### Critério de saída

- ciclo completo de proposta funciona de ponta a ponta.

---

## Ordem recomendada de execução

1. Fase 0
2. Fase 1
3. Fase 2
4. Fase 3
5. Fase 4
6. Fase 5
7. Fase 6

Motivo:

- primeiro saneia o contrato;
- depois garante coordenadas do cliente;
- depois torna profissional elegível;
- depois corrige o matching;
- depois expande o comportamento por ondas;
- por fim expõe e fecha a UX profissional.

---

## Estratégia de rollout

### MVP interno

Entregar primeiro:

- geocodificação de endereço;
- toggle Express profissional;
- captura de localização em foreground;
- elegibilidade por especialidade;
- inbox Express simples;
- expansão por ondas;
- proposta manual com polling.

### Pós-MVP

Depois evoluir para:

- background location opcional;
- atualização mais inteligente por movimento;
- geofencing;
- push notifications reais;
- observabilidade e métricas operacionais.

---

## Observabilidade

Adicionar logs estruturados para:

- geocodificação bem-sucedida e falha;
- ativação/desativação do Express;
- localização expirada;
- criação da onda 1/2/3;
- profissionais encontrados por onda;
- proposta enviada;
- pedido encerrado por indisponibilidade.

Adicionar métricas para:

- taxa de endereços geocodificados com sucesso;
- quantidade média de profissionais encontrados por onda;
- tempo até primeira proposta;
- taxa de indisponibilidade;
- taxa de profissionais com geo válida;
- idade média da localização do profissional no momento do matching.

---

## Testes por camada

### Unitários

- geocoding service;
- regra de recência;
- elegibilidade por especialidade;
- expansão de raio.

### Integração backend

- criação de endereço com geocodificação;
- ativação de geo profissional;
- criação de pedido Express;
- expansão 100/200/300;
- encerramento em 15 min;
- proposta e escolha.

### Frontend

- gating de queries por `mode`;
- toggle Express com permissão;
- lista de inbox Express;
- timeline distinta por modo;
- tratamento de indisponibilidade.

### E2E manual

1. cliente cria endereço;
2. profissional ativa Express;
3. cliente abre pedido Express;
4. profissional recebe inbox;
5. profissional envia proposta;
6. cliente aceita;
7. pedido vira atribuído.

---

## Riscos e mitigação

### R1. Geocodificação com custo/limite

Mitigação:

- geocodificar apenas no backend;
- evitar chamada por keystroke;
- reusar coordenadas persistidas.

### R2. Permissões de localização no app profissional

Mitigação:

- começar com foreground only;
- explicar claramente o motivo da permissão;
- bloquear ativação do Express sem localização.

### R3. Bateria e background

Mitigação:

- não começar com tracking agressivo;
- usar recência curta e atualização sob demanda no MVP.

### R4. Mistura entre `express` e `on_demand`

Mitigação:

- contratos separados;
- endpoints separados quando necessário;
- testes específicos por modo.

### R5. Corridas de concorrência

Mitigação:

- manter locking pessimista e validações idempotentes nas respostas e escolha de proposta.

---

## Entregáveis

### Documento

- especificação funcional
- plano de implementação

### Backend

- migration(s)
- geocoding service
- ajustes de endereço
- ajustes de profissional geo
- matching corrigido
- expansão por ondas
- inbox Express

### Frontend cliente

- cadastro de endereço apto para Express
- detalhe de pedido com gating correto
- timeline por modo
- proposals apenas em Express

### Frontend profissional

- toggle Express
- captura de geo
- inbox de pedidos Express
- dashboard com pedidos disponíveis
- resposta com proposta

---

## Definição de pronto

Uma entrega estará pronta quando:

- compilar;
- passar em typecheck/testes relevantes;
- respeitar a separação `express` vs `on_demand`;
- permitir pedido Express com matching por raio;
- permitir profissional notificado visualizar e responder;
- permitir cliente escolher proposta;
- encerrar corretamente por indisponibilidade quando necessário.
