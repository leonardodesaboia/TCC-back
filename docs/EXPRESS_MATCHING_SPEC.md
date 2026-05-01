# Especificação de Implementação

## Express por proximidade com propostas de profissionais

### Objetivo

Definir o comportamento esperado do fluxo de pedidos Express baseado em proximidade geográfica, com múltiplos endereços do cliente, profissionais habilitados para receber pedidos e expansão progressiva do raio de busca ao longo do tempo.

Este documento serve como referência funcional para a implementação posterior no backend e no frontend.

---

## Resumo do fluxo

1. O cliente cadastra um ou mais endereços no aplicativo.
2. No cadastro do endereço, o sistema geocodifica o endereço por uma API externa e salva a geolocalização do local.
3. Ao abrir um pedido Express, o cliente escolhe um dos endereços cadastrados.
4. A localização desse endereço é usada para encontrar profissionais próximos.
5. Apenas profissionais habilitados para receber pedidos Express e compartilhando localização com o app podem participar.
6. O pedido é distribuído em etapas, com expansão progressiva do raio de busca.
7. Os profissionais encontrados podem enviar propostas ao cliente.
8. O cliente escolhe apenas uma proposta.
9. Se ninguém aceitar dentro da janela total, o cliente é informado de que não há profissionais disponíveis no momento.

---

## Regras de negócio

### Arquitetura de localização recomendada

O fluxo deve separar duas origens de localização:

- **Endereço do cliente**:
  origem textual digitada/selecionada pelo usuário e convertida em coordenadas por uma API externa de geocodificação.
- **Localização do profissional**:
  origem no próprio dispositivo móvel do profissional, com consentimento explícito, usando GPS/Wi-Fi/rede do celular.

Essa separação é a abordagem mais correta porque:

- geocodificação de endereço e rastreamento de dispositivo são problemas diferentes;
- a posição do profissional muda com o tempo e não deve depender de endereço fixo;
- para busca por raio em tempo real, a localização do profissional precisa vir do celular;
- a API externa é ideal para transformar endereços do cliente em coordenadas confiáveis.

### API externa recomendada para geocodificação

#### Recomendação principal

Usar **Google Maps Geocoding API** para converter endereços do cliente em latitude e longitude.

Motivos da recomendação:

- alta maturidade de produto;
- cobertura ampla e consistente;
- suporte consolidado para geocoding e reverse geocoding;
- boa adequação para fluxo de cadastro de endereço em aplicativo móvel;
- documentação e operação estáveis para produção.

Uso esperado:

- **forward geocoding**:
  converter endereço textual do cliente em coordenadas;
- **reverse geocoding**:
  opcionalmente confirmar ou enriquecer a exibição do endereço a partir das coordenadas.

#### Alternativa viável

**Mapbox Geocoding API** é uma alternativa forte, especialmente se o projeto quiser evoluir depois para recursos mais avançados de busca, contexto geográfico ou entrada/porta de edifício.

#### O que não usar como solução principal de produção

Não usar o serviço público do **Nominatim/OpenStreetMap** como base principal de produção para esse fluxo.

Motivo:

- a própria política pública do serviço impõe limite muito baixo de uso e não é adequada para carga normal de app em produção.

---

### Endereços do cliente

- O cliente pode cadastrar vários endereços.
- Cada endereço deve possuir geolocalização salva no momento do cadastro.
- A geolocalização deve ser obtida por geocodificação do endereço via API externa.
- Na criação do pedido Express, o cliente escolhe um endereço já cadastrado.
- A geolocalização do endereço escolhido é a base da busca por profissionais.
- O backend deve salvar pelo menos:
  - latitude;
  - longitude;
  - endereço formatado;
  - identificador externo quando aplicável;
  - timestamp da geocodificação;
  - nível de confiança/qualidade, se a API retornar esse dado.

### Elegibilidade do profissional para Express

O profissional só pode receber pedidos Express se atender a todos os critérios abaixo:

- possuir perfil profissional aprovado;
- estar com o modo Express habilitado;
- estar compartilhando a localização com o aplicativo;
- possuir geolocalização válida no perfil;
- possuir localização recente o suficiente para ser considerada confiável;
- estar dentro do raio atual da busca;
- possuir a especialidade/categoria compatível com o pedido do cliente.

### Como a localização do profissional deve ser obtida

#### Recomendação principal

A melhor forma de obter a localização do profissional é **pelo próprio celular**.

O app do profissional deve:

1. pedir permissão de localização ao sistema operacional;
2. permitir ao profissional habilitar manualmente o modo “Disponível para Express”;
3. ao habilitar esse modo, capturar a posição atual do aparelho;
4. atualizar periodicamente a localização enquanto o profissional estiver disponível;
5. desabilitar automaticamente a elegibilidade do Express quando a localização estiver indisponível ou velha demais.

#### Motivo técnico

Para busca por raio, a localização do profissional precisa refletir a posição real e atual dele.

Usar:

- endereço cadastrado do profissional;
- localização estimada só por IP;
- geolocalização gerada por API externa sem usar o dispositivo;

não é suficiente para o objetivo do Express, porque isso não representa a posição real de atendimento no momento do pedido.

#### Estratégia recomendada de captura

##### MVP seguro

- capturar localização do celular quando o profissional abrir o app;
- atualizar quando ele ativar “Disponível para Express”;
- atualizar periodicamente enquanto a tela estiver ativa;
- marcar a localização com `capturedAt`;
- se a localização ficar velha demais, remover o profissional da elegibilidade do Express.

##### Evolução recomendada

- usar atualização em foreground enquanto o app está aberto;
- usar atualização em background apenas quando o profissional estiver explicitamente disponível para Express;
- mostrar no app se a localização está:
  - ativa;
  - desatualizada;
  - negada;
  - indisponível.

### Política de validade da localização do profissional

O sistema deve considerar a localização do profissional inválida quando:

- não houver latitude/longitude;
- a permissão de localização estiver negada;
- o profissional desativar a disponibilidade Express;
- a última atualização for antiga demais para operação segura.

Recomendação de regra operacional:

- guardar `geoLat`, `geoLng`, `accuracyMeters`, `capturedAt` e `source`;
- considerar a localização “fresca” por uma janela curta;
- se `capturedAt` ultrapassar a janela definida, o profissional sai temporariamente da busca Express.

Sugestão inicial de política:

- até `2 a 5 minutos`: localização válida para Express;
- acima disso: marcar como desatualizada e não usar em novas buscas até atualizar.

Esse valor pode ser ajustado depois de testes reais.

### Propostas

- Todos os profissionais elegíveis dentro do raio atual podem receber o pedido.
- Cada profissional notificado pode aceitar ou recusar.
- Ao aceitar, o profissional envia uma proposta ao cliente.
- O cliente pode visualizar as propostas recebidas.
- O cliente aceita apenas uma proposta.
- Após a escolha do cliente, o pedido passa a pertencer ao profissional escolhido.

### Encerramento sem profissional

- Se nenhum profissional aceitar dentro do tempo total da busca, o cliente deve receber a informação de indisponibilidade.
- Mensagem esperada:
  `Não há profissionais disponíveis no momento.`

---

## Busca por proximidade

### Etapas da busca

A busca deve ocorrer em ondas sucessivas:

1. Início do pedido:
   buscar profissionais em um raio de até `100 metros`.

2. Após `5 minutos`, se ninguém aceitar:
   expandir a busca para até `200 metros`.

3. Após `10 minutos`, se ninguém aceitar:
   expandir a busca para até `300 metros`.

4. Após `15 minutos`, se ainda não houver aceite:
   encerrar a busca e informar indisponibilidade ao cliente.

### Janela temporal consolidada

- `0 a 5 min`: raio de `100 m`
- `5 a 10 min`: raio de `200 m`
- `10 a 15 min`: raio de `300 m`
- `após 15 min`: cancelar busca por indisponibilidade

### Regra de expansão

- A expansão só ocorre se não houver aceite válido até o fim da etapa atual.
- Profissionais já notificados em etapas anteriores não devem ser notificados novamente.
- Cada nova etapa deve incluir apenas novos profissionais elegíveis dentro do novo raio.
- A localização usada para decidir elegibilidade do profissional deve ser a última localização válida capturada pelo celular.

---

## Comportamento esperado no frontend

### Cliente

#### Cadastro de endereços

- Deve permitir múltiplos endereços.
- Cada endereço precisa salvar latitude e longitude.

#### Criação do pedido Express

- O cliente escolhe a categoria do serviço.
- O cliente escolhe um dos endereços cadastrados.
- O sistema usa a geolocalização desse endereço para iniciar a busca.

#### Acompanhamento do pedido

- O cliente deve ver que o pedido está buscando profissionais.
- O cliente deve receber as propostas enviadas.
- O cliente deve poder aceitar apenas uma proposta.
- Se o tempo total expirar sem aceite, deve receber a mensagem de indisponibilidade.

#### Compatibilidade entre modos no frontend

O frontend deve tratar `express` e `on_demand` como fluxos distintos.

Regras obrigatórias:

- pedidos `express` podem consultar propostas;
- pedidos `on_demand` não podem consultar propostas Express;
- queries dependentes do modo do pedido só podem ser disparadas depois que o pedido base tiver sido carregado;
- o frontend não deve inferir comportamento de `express` quando `mode` ainda estiver indefinido;
- o primeiro render não pode habilitar chamadas incompatíveis com o modo real do pedido.

### Profissional

#### Perfil e disponibilidade

- Deve existir uma forma de habilitar ou desabilitar o recebimento de pedidos Express.
- Deve existir suporte para compartilhamento de localização com o app.
- O profissional não pode entrar na busca Express sem localização válida.
- O app deve mostrar claramente:
  - se a permissão foi concedida;
  - se a localização atual foi capturada;
  - se a localização está desatualizada;
  - se o modo Express está ligado ou desligado.

#### Fluxo recomendado de ativação Express

1. O profissional ativa o toggle “Disponível para Express”.
2. O app solicita permissão de localização se ainda não tiver.
3. O app obtém a posição atual do celular.
4. O backend salva a posição com timestamp.
5. O profissional passa a ser elegível para pedidos dentro do raio.
6. Se a localização não puder ser obtida, o modo Express não deve ser ativado.

#### Comportamento quando a permissão não existir

- O app deve bloquear a ativação do Express.
- O app deve explicar por que a localização é necessária.
- O app deve oferecer atalho para abrir as configurações do sistema quando aplicável.

#### Recebimento de pedidos

Os pedidos Express notificados ao profissional devem aparecer:

- no dashboard;
- na lista de pedidos;
- na área de notificações.

#### Resposta ao pedido

- O profissional deve conseguir abrir o pedido notificado.
- Deve conseguir aceitar com proposta.
- Deve conseguir recusar.

---

## Comportamento esperado no backend

### Fonte da geolocalização do cliente

- A latitude e longitude usadas na busca vêm do endereço escolhido no pedido.
- O backend não deve depender de coordenadas enviadas manualmente na criação do pedido se o endereço já está persistido.
- O backend deve confiar nas coordenadas previamente geocodificadas e salvas no cadastro do endereço.
- Se o endereço ainda não tiver coordenadas válidas, o pedido Express não deve ser criado.

### Fonte da elegibilidade do profissional

- O Express deve considerar a especialidade/categoria do profissional.
- O Express não deve exigir serviço publicado de On Demand para incluir o profissional na fila.
- A elegibilidade deve ser baseada nas especialidades profissionais, não em serviços publicados específicos.
- A localização do profissional deve vir do dispositivo móvel e não de endereço textual fixo.
- O backend deve usar a última localização válida do profissional com controle de recência.

### Distribuição do pedido

- Ao criar o pedido, o backend deve executar a primeira onda de busca em `100 m`.
- Se não houver aceite em `5 min`, executar a segunda onda em `200 m`.
- Se não houver aceite em `10 min`, executar a terceira onda em `300 m`.
- Se não houver aceite em `15 min`, encerrar por indisponibilidade.

### Compatibilidade entre modos no backend

O backend deve manter contratos e regras coerentes entre `express` e `on_demand`.

Regras obrigatórias:

- apenas pedidos `express` possuem fila de profissionais;
- apenas pedidos `express` possuem propostas concorrentes;
- pedidos `on_demand` não podem aceitar consulta de propostas Express;
- endpoints de propostas devem rejeitar pedidos que não sejam `express`;
- o contrato retornado ao frontend deve deixar claro quais campos são obrigatórios ou opcionais por modo.

### Contrato de `areaId`

O campo `areaId` precisa ter definição explícita para evitar inconsistências entre backend e frontend.

O sistema deve escolher e documentar exatamente uma destas abordagens:

1. **Abordagem A**
   O backend preenche `areaId` também em pedidos `on_demand`.

2. **Abordagem B**
   O backend pode retornar `areaId = null` em `on_demand`, e o frontend deve tratar esse campo como opcional para esse modo.

Regras importantes:

- não pode existir divergência entre backend e frontend sobre obrigatoriedade de `areaId`;
- se `areaId` não for garantido em `on_demand`, o frontend não deve tipá-lo como obrigatório nesse modo;
- a UI não deve mascarar silenciosamente contrato inconsistente sem decisão arquitetural explícita.

### Fila de profissionais

- O backend deve registrar quais profissionais foram notificados em cada etapa.
- Não deve duplicar notificação para o mesmo profissional no mesmo pedido.
- Deve permitir identificar se um profissional foi ou não notificado para determinado pedido.

### Escolha da proposta

- Quando o cliente aceitar uma proposta:
  - o pedido deve ser atribuído ao profissional escolhido;
  - as demais propostas devem ser invalidadas;
  - o profissional escolhido passa a ser o dono efetivo do pedido;
  - o chat e o fluxo operacional podem ser liberados a partir disso.

---

## Problemas já identificados no estado atual

### 1. Dependência incorreta de serviço publicado

No fluxo Express, o profissional não deve precisar ter serviço publicado de On Demand para receber pedidos.

Regra correta:

- Express depende de especialidade/categoria do profissional.
- On Demand depende de serviço publicado.

### 2. Ausência de exibição de pedidos apenas notificados

Mesmo antes de o cliente escolher uma proposta, o profissional precisa conseguir visualizar os pedidos Express para os quais foi notificado.

Isso precisa aparecer:

- no dashboard;
- na lista de pedidos;
- nas notificações.

### 3. Ausência de controle de disponibilidade Express no app profissional

O frontend profissional precisa expor:

- status de disponibilidade Express;
- status de compartilhamento/localização;
- eventual bloqueio quando não houver coordenadas.

---

## Requisitos funcionais

### RF01

O sistema deve permitir ao cliente cadastrar múltiplos endereços com geolocalização obtida por API externa.

### RF02

O sistema deve permitir ao cliente escolher um endereço salvo ao criar um pedido Express.

### RF03

O sistema deve buscar profissionais próximos com base na geolocalização do endereço escolhido.

### RF04

O sistema deve limitar a busca a profissionais aprovados, habilitados para Express e compartilhando localização.

### RF04A

O sistema deve capturar a localização do profissional pelo próprio dispositivo móvel, mediante permissão do usuário.

### RF04B

O sistema deve impedir a ativação do modo Express quando o profissional não fornecer localização válida.

### RF05

O sistema deve fazer a busca por etapas:

- até 100 m no início;
- até 200 m após 5 min sem aceite;
- até 300 m após 10 min sem aceite.

### RF06

O sistema deve encerrar a busca após 15 min sem aceite e informar indisponibilidade ao cliente.

### RF07

O profissional deve conseguir ver pedidos Express para os quais foi notificado antes da atribuição final.

### RF08

O profissional deve poder enviar proposta ou recusar o pedido.

### RF09

O cliente deve visualizar múltiplas propostas e aceitar apenas uma.

### RF10

Ao aceitar uma proposta, o sistema deve vincular o pedido ao profissional escolhido e encerrar a disputa entre propostas concorrentes.

### RF11

O sistema deve impedir consulta de propostas Express para pedidos `on_demand`.

### RF12

O sistema deve definir explicitamente o contrato de campos compartilhados entre modos, incluindo `areaId`.

### RF13

O sistema deve exibir timeline e estados visuais diferentes para `express` e `on_demand`, conforme a semântica de cada fluxo.

---

## Requisitos não funcionais

### RNF01

A expansão de raio deve ser automatizada por tempo, sem depender de ação manual do cliente.

### RNF02

O sistema deve evitar notificação duplicada do mesmo profissional para o mesmo pedido.

### RNF03

O rastreio de quem foi notificado, quando foi notificado e em qual etapa deve ficar persistido.

### RNF04

O fluxo deve ser compatível com futura implementação real de geolocalização contínua do profissional no app.

### RNF05

A solução de geocodificação de endereços deve usar API externa apropriada para produção.

### RNF06

A solução de localização do profissional deve minimizar consumo de bateria e respeitar consentimento explícito.

---

## Critérios de aceite

### Cenário 1: primeira onda

- Dado um cliente com endereço geolocalizado
- E um profissional elegível a até 100 m
- E esse profissional com localização válida capturada do próprio celular
- Quando o cliente criar um pedido Express
- Então esse profissional deve receber o pedido imediatamente

### Cenário 2: segunda onda

- Dado que ninguém aceitou na primeira onda em 5 min
- Quando a segunda etapa iniciar
- Então profissionais elegíveis entre 100 m e 200 m também devem ser notificados

### Cenário 3: terceira onda

- Dado que ninguém aceitou até 10 min
- Quando a terceira etapa iniciar
- Então profissionais elegíveis entre 200 m e 300 m também devem ser notificados

### Cenário 4: indisponibilidade

- Dado que ninguém aceitou até 15 min
- Então o cliente deve ser informado de que não há profissionais disponíveis no momento

### Cenário 5: proposta

- Dado que dois ou mais profissionais enviaram proposta
- Quando o cliente aceitar uma delas
- Então apenas o profissional escolhido deve permanecer vinculado ao pedido

### Cenário 6: visibilidade do profissional

- Dado que um profissional foi notificado para um pedido Express
- Então ele deve conseguir visualizar esse pedido no app mesmo antes de ser escolhido pelo cliente

### Cenário 7: proteção de modo no frontend

- Dado um pedido `on_demand`
- Quando a tela de detalhe for aberta
- Então o frontend não deve consultar propostas Express antes nem depois do carregamento

### Cenário 8: contrato de `areaId`

- Dado um pedido `on_demand`
- Quando o backend retornar o payload do pedido
- Então o contrato de `areaId` deve seguir exatamente a definição oficial do sistema
- E o frontend deve refletir essa definição sem tipagem divergente

### Cenário 9: timeline por modo

- Dado um pedido `on_demand`
- Quando a interface renderizar a timeline
- Então as etapas exibidas devem representar corretamente o fluxo `on_demand`
- E não devem reutilizar heurísticas genéricas do fluxo `express`

---

## Escopo de implementação futura

### Backend

- integrar geocodificação externa para endereços do cliente;
- corrigir a base de elegibilidade do Express para usar especialidades do profissional;
- implementar busca por ondas de distância e tempo;
- implementar expansão de raio sem duplicar profissionais;
- persistir recência e qualidade da localização do profissional;
- expor listagem de pedidos Express notificados ao profissional;
- manter o vínculo final do pedido apenas após a escolha do cliente.

### Frontend cliente

- integrar fluxo de cadastro de endereço com geocodificação externa;
- garantir seleção de endereço geolocalizado;
- exibir estados da busca por etapa/tempo;
- exibir propostas recebidas;
- tratar indisponibilidade ao final da janela total.
- bloquear consulta de propostas quando o pedido for `on_demand`;
- habilitar queries dependentes apenas após carregamento do pedido base;
- definir timeline visual específica por modo;
- alinhar consumo de `areaId` ao contrato real do backend.

### Frontend profissional

- adicionar controles de disponibilidade Express;
- adicionar captura de localização do próprio celular;
- adicionar suporte visual para compartilhamento/localização;
- exibir pedidos Express notificados no dashboard;
- exibir pedidos Express notificados na lista de pedidos;
- exibir pedidos Express notificados em notificações;
- permitir envio de proposta ou recusa.

---

## Observação importante

Enquanto a geolocalização do profissional ainda não estiver implementada no app, o fluxo real por raio não estará completo em produção.

Mesmo assim, esta especificação já define o comportamento final esperado e deve guiar a implementação correta, evitando acoplamento indevido com serviços publicados de On Demand.

---

## Recomendação técnica consolidada

### Endereço do cliente

Usar uma **API externa de geocodificação**.

Recomendação principal:

- **Google Maps Geocoding API**

Alternativa forte:

- **Mapbox Geocoding API**

### Localização do profissional

Usar a **localização nativa do próprio celular**, com permissão explícita do sistema operacional.

Em app móvel com Expo/React Native, a abordagem recomendada é:

- permissão foreground para captura inicial;
- atualização contínua em foreground enquanto o profissional estiver usando o app;
- background location apenas se o produto realmente precisar manter disponibilidade Express com o app em segundo plano;
- bloqueio do Express se não houver localização atual confiável.

### O que não fazer

- não usar IP como fonte principal de localização do profissional;
- não usar endereço residencial fixo do profissional para o raio do Express;
- não depender de serviço público Nominatim como base principal de produção;
- não exigir serviço publicado On Demand para elegibilidade do Express.

---

## Referências consultadas

- Expo Location:
  https://docs.expo.dev/versions/latest/sdk/location/
- Google Maps Geocoding API:
  https://developers.google.com/maps/documentation/geocoding
- Google Geolocation API:
  https://developers.google.com/maps/documentation/geolocation/overview
- Nominatim Usage Policy:
  https://operations.osmfoundation.org/policies/nominatim/
