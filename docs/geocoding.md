# Geocoding — Documentação Técnica

Módulo responsável por converter endereços escritos em coordenadas geográficas (`lat`/`lng`), e o caminho inverso.
Usado como **sugestão** no cadastro de endereços do cliente; a coordenada que vale é sempre a confirmada por quem cadastra.

> Para o passo a passo do fluxo nas duas pontas (app e API), caso a caso, ver **`fluxo-localizacao.md`**.

---

## Índice

1. [Visão geral](#1-visão-geral)
2. [Providers — Nominatim resolve, BrasilAPI enriquece](#2-providers--nominatim-resolve-brasilapi-enriquece)
3. [Arquitetura](#3-arquitetura)
4. [Endpoints](#4-endpoints)
5. [Fluxo do front](#5-fluxo-do-front)
6. [Cache e limites operacionais](#6-cache-e-limites-operacionais)
7. [Tratamento de falhas](#7-tratamento-de-falhas)
8. [Variáveis de ambiente](#8-variáveis-de-ambiente)
9. [Estrutura de pacotes](#9-estrutura-de-pacotes)
10. [Decisões de design](#10-decisões-de-design)
11. [Privacidade e LGPD](#11-privacidade-e-lgpd)
12. [Roadmap pós-MVP](#12-roadmap-pós-mvp)

---

## 1. Visão geral

O módulo tem **dois endpoints, os dois só de consulta**:

- **Lookup** (`POST /api/v1/geocoding/lookup`) — endereço escrito para coordenadas. Serve para **sugerir** onde abrir o pin.
- **Reverse** (`POST /api/v1/geocoding/reverse`) — coordenadas para endereço escrito. Serve para preencher rua e bairro depois que a pessoa move o pin.

**Nenhum dos dois persiste nada, e o save de endereço não chama geocoding.** Essa é a decisão central do módulo e vale explicar o porquê.

Até a V24, `POST /api/users/{userId}/addresses` geocodificava sozinho quando o request chegava sem `lat`/`lng`. Duas coisas estavam erradas nisso:

1. **O ponto que interessa não é o do CEP, é o do atendimento.** O portão do condomínio, a entrada dos fundos, o bloco C. Nenhum provider sabe disso; só quem está lá sabe. E o Express notifica profissionais num raio de **300 metros** em volta desse ponto — a diferença entre o meio da rua e o portão certo decide se alguém é notificado.
2. **Chamada HTTP externa dentro de transação JPA.** Cada save segurava uma conexão do pool por até ~14 s no pior caso (3 tentativas de 5 s de read timeout), e ainda podia gravar endereço sem coordenada em silêncio quando o provider caía.

Hoje a coordenada chega pronta do cliente, junto com a **procedência** dela — ver [§4](#4-endpoints).

```
Front → POST /api/v1/geocoding/lookup  → GeocodingService → cache Redis ↔ providers
Front → POST /api/v1/geocoding/reverse →       (sugestão, nunca persistida)
                          ↓
Front → POST /api/users/{id}/addresses  { ...campos, lat, lng, coordinateSource }
                          ↓
                  grava exatamente o que veio
```

---

## 2. Providers — Nominatim resolve, BrasilAPI enriquece

### Por quê uma chain

A versão anterior deste documento afirmava que o **Nominatim tem dados esparsos para endereços brasileiros**, e por isso a BrasilAPI vinha primeiro. Medição posterior mostrou que a conclusão estava errada, e por um motivo constrangedor: **a aplicação nunca conseguiu falar direito com o Nominatim.**

A URI era montada já codificada e entregue ao `RestClient` como `String`. O `RestClient` trata String como *template* e codifica de novo: o espaço virava `%2520`. O Nominatim respondia **200 com lista vazia** para todo endereço que tivesse espaço no nome — ou seja, todos. Só o `/reverse` escapava, porque manda apenas números.

Com a URI corrigida (`URI` em vez de `String`), os mesmos 20 endereços reais de Fortaleza que a auditoria usou resolveram **20 de 20**, sendo 8 com precisão de edifício. A tabela abaixo continua válida como panorama de fornecedores, mas a conclusão que ela sustentava não era.

Ainda assim a chain se justifica — só que com os papéis trocados: o Nominatim resolve a coordenada, e a BrasilAPI entra antes para **enriquecer a busca** com rua e bairro do CEP, informação que o usuário costuma não digitar.

| Critério | BrasilAPI v2 | Nominatim | Google | OpenCage |
|---|---|---|---|---|
| Custo | Gratuito | Gratuito | Pago após free tier | 2.500/dia grátis |
| Chave de API | Não | Não | Sim | Sim |
| Cobertura BR | Excelente — agrega ViaCEP/OpenCEP/Postmon/WideNet | Limitada (OSM) | Excelente | Boa |
| Aceita endereço sem CEP | Não | Sim | Sim | Sim |
| Geocoding por número | Não — precisão de CEP | Sim, quando OSM tem o dado | Sim | Sim |

### Estratégia

```
              BrasilAPI v2 (CEP) — só para enriquecer rua e bairro
                              │
                              ▼
                   Nominatim (cascata de 3 tentativas)
                              │
                ┌─────────────┴──────────────┐
                ▼                            ▼
           achou → retorna          vazio ou fora do ar
                                             ▼
                              coordenada do CEP, marcada CITY
```

| Caso | Quem responde | Confiança |
|---|---|---|
| Nominatim achou | Nominatim | `ROOFTOP` ou `INTERPOLATED`, conforme o tipo do resultado |
| Nominatim achou, mas fora do bairro pedido | Nominatim (busca livre) | idem — ver *Refinamento por bairro* |
| Nominatim vazio, CEP tem coordenada | BrasilAPI | sempre `CITY` |
| Nominatim fora do ar, CEP tem coordenada | BrasilAPI | sempre `CITY` |
| Ninguém tem nada | — | 422 |
| Toda a chain fora do ar | — | 503 |

**Por que a coordenada do CEP vai sempre marcada `CITY`:** ela é, na melhor das hipóteses, o meio da via — e na pior, o centro do município. Em 20 endereços reais de Fortaleza, **16 voltaram no mesmo ponto** (`-3.717220, -38.543060`, o centroide municipal devolvido pelo backend `open-cep`), com erro de 2 a 11 km. Marcar `CITY` é dizer a verdade sobre o que aquele ponto é — e é essa marcação que faz o Express recusá-lo sem confirmação humana.

Falhas operacionais (timeout, 5xx, 429) de um provider não derrubam o lookup — caem no próximo. `GeocodingProviderUnavailableException` só é lançada quando **toda a chain** falhou.

### Refinamento por bairro

A busca estruturada do Nominatim aceita `street`, `city`, `state` e `postalcode` — **não aceita bairro**. Como ela costuma responder na primeira tentativa, o bairro que a pessoa digitou nunca chegava ao provider. Numa avenida que atravessa a cidade, o ponto caía em qualquer trecho dela.

Medido nos 20 endereços de Fortaleza: **8 caíam no bairro errado**, um deles a 3,6 km do trecho certo (Av. Osório de Paiva pedida em Parangaba, entregue em Parque São José).

A busca livre manda o bairro junto. Comparando as duas no mesmo conjunto:

| | Bairro correto |
|---|---|
| Estruturada sozinha | 12 de 20 |
| Busca livre | 17 de 20 |

Onde as duas concordam, concordam no mesmo metro — a distância mediana entre elas é **0 m**. Por isso a regra é conservadora: quando o resultado estruturado **não** é `ROOFTOP` e a pessoa informou bairro, roda também a busca livre e só troca se ela cair no bairro pedido.

Custo: uma requisição a mais em cerca de 60% dos lookups. Como a sugestão dispara em segundo plano, a latência extra não aparece para o usuário.

**Por que isso importa mais do que parece.** Resultado impreciso faz o app exigir que a pessoa toque no mapa — e o toque promove o ponto a `user_pin`, que o Express aceita sem discutir. Abrir o mapa no trecho errado é justamente o que transforma uma sugestão ruim em coordenada confiável. O refinamento fecha esse caminho; o aviso de divergência do front (ver `fluxo-localizacao.md`) é a rede que sobra.

### Limites de sanidade

Resultado fora da caixa delimitadora `GEOCODING_BOUNDING_BOX` é descartado como se o endereço não existisse (422). Existe por um caso concreto: o CEP inexistente `99999-999` devolvia **200 com um ponto em Sarandi/PR**. O padrão cobre todo o Ceará — largo o bastante para nunca recusar endereço legítimo da região atendida. String vazia desliga a checagem.

### Sobre cada provider

**[BrasilAPI v2](https://brasilapi.com.br/docs#tag/CEP-V2)** — `GET /api/cep/v2/{cep}`:

- gratuito, sem chave, sem rate limit explícito;
- agrega 4 fontes brasileiras (ViaCEP, OpenCEP, Postmon, WideNet) — cobertura muito alta;
- devolve rua, bairro, cidade, estado e (geralmente) coordenadas;
- precisão é a do CEP — não localiza por número da casa;
- quando a fonte interna é ViaCEP, pode não vir `location` — nesse caso o composite cai pro Nominatim.

**[Nominatim](https://nominatim.openstreetmap.org)** — `GET /search`:

- gratuito, sem chave;
- política exige `User-Agent` identificador com contato (`GEOCODING_USER_AGENT`);
- limite de 1 req/s por IP — garantido pelo `NominatimRateLimiter` antes de cada requisição, e aliviado pelo cache Redis de 30 dias. Não é etiqueta: durante a auditoria, 20 buscas em 7 segundos bloquearam o IP do container por mais de 25 minutos, e cada busca dispara até 3 requisições em cascata;
- usado em três modalidades em cascata: busca estruturada (mais precisa quando OSM tem o dado), busca livre (mais tolerante) e busca livre street-level sem número/CEP (cobre prédios não mapeados no OSM, devolvendo o centroide da rua).

### Endpoints usados

```
# BrasilAPI v2 (primário quando há CEP)
GET https://brasilapi.com.br/api/cep/v2/{cep}

# Nominatim — busca estruturada (1ª tentativa)
GET https://nominatim.openstreetmap.org/search
  ?format=jsonv2
  &addressdetails=1
  &limit=1
  &countrycodes=br
  &street={number} {street}
  &city={city}
  &state={state}
  &postalcode={cep sem máscara}

# Nominatim — busca livre (2ª tentativa, fallback)
GET https://nominatim.openstreetmap.org/search
  ?format=jsonv2
  &addressdetails=1
  &limit=1
  &countrycodes=br
  &q={street}, {number}, {district}, {city}, {state}, {cep}

# Nominatim — busca livre street-level (3ª tentativa, sem número e sem CEP)
# Cobre prédios não mapeados no OSM (apartamentos), devolvendo o centroide da rua.
GET https://nominatim.openstreetmap.org/search
  ?format=jsonv2
  &addressdetails=1
  &limit=1
  &countrycodes=br
  &q={street}, {district}, {city}, {state}
```

```
# Nominatim — reverse geocoding (ponto para endereço)
GET https://nominatim.openstreetmap.org/reverse
  ?format=jsonv2
  &addressdetails=1
  &zoom=18
  &lat={lat}
  &lon={lng}
```

Timeouts: connect 2s, read 5s para ambos providers.

> **Atenção ao mexer nas URIs:** os builders devolvem `java.net.URI`, não `String`. Entregar String ao `RestClient` faz ele codificar de novo o que já veio codificado — foi exatamente esse o defeito descrito acima. `NominatimGeocodingProviderTest` sobe um Nominatim falso local e inspeciona a query que realmente sai, justamente para travar isso.

---

## 3. Arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│                  GeocodingController                        │
│      POST /api/v1/geocoding/lookup    (endereço → ponto)    │
│      POST /api/v1/geocoding/reverse   (ponto → endereço)    │
└─────────────────────────┬───────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                  GeocodingService                           │
│   1. kill-switch GEOCODING_ENABLED → 503 se off             │
│   2. cache Redis (chave SHA-256; falha do Redis = miss)     │
│   3. miss → provider                                        │
│   4. GeocodingBounds descarta ponto fora da área            │
│   5. resultado positivo → cache 30d                         │
│   6. resultado vazio → marker NOT_FOUND no cache + 422      │
└─────────────────────────┬───────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────┐
│        GeocodingProvider (SPI) — @Primary                   │
│                                                             │
│        CompositeGeocodingProvider                           │
│            ├─ BrasilApiCepProvider    (enriquece pelo CEP)  │
│            └─ NominatimGeocodingProvider  (resolve o ponto) │
│                       └─ NominatimRateLimiter (1 req/s)     │
│                                                             │
│        Falha de um provider cai no próximo.                 │
│        Só lança 503 quando TODOS falharam.                  │
└─────────────────────────────────────────────────────────────┘
```

**O cache é otimização, não dependência.** Toda operação no Redis é tolerante a falha: com o Redis fora do ar, o lookup vira cache miss e segue direto para o provider. Antes desta versão a chamada ficava **60 segundos pendurada** e terminava em 500, porque o Lettuce não tinha timeout configurado.

A SPI `GeocodingProvider` permite encadear ou trocar fornecedores sem tocar nos consumidores. O `CompositeGeocodingProvider` é o `@Primary` — o `GeocodingService` injeta apenas a SPI. Outros providers (Google, OpenCage, self-host) entram como `@Component` adicional e podem ser plugados na chain.

---

## 4. Endpoints

### `POST /api/v1/geocoding/lookup`

Converte um endereço em coordenadas **sem persistir nada**. Use antes de criar um endereço para confirmar o pin no mapa.

**Auth:** qualquer usuário autenticado.

**Request body:**

```json
{
  "zipCode": "60160-230",
  "street": "Av. Dom Luís",
  "number": "1233",
  "complement": "Sala 501",
  "district": "Aldeota",
  "city": "Fortaleza",
  "state": "CE"
}
```

Validações:

| Campo | Regra |
|---|---|
| `zipCode` | Obrigatório. Formato `99999-999` ou `99999999` |
| `street` | Obrigatório. Máx. 200 caracteres |
| `number` | Opcional. Máx. 20 caracteres |
| `complement` | Opcional. Máx. 80 caracteres. **Não influencia o geocoding** — só fica disponível como conveniência se você quiser reaproveitar o mesmo objeto |
| `district` | Opcional. Máx. 80 caracteres |
| `city` | Obrigatório. Máx. 80 caracteres |
| `state` | Obrigatório. Exatamente 2 letras maiúsculas |

**Response 200:**

```json
{
  "lat": -3.731862,
  "lng": -38.526669,
  "displayName": "Avenida Dom Luís, 1233, Aldeota, Fortaleza, CE, 60160-230, Brasil",
  "normalizedAddress": {
    "street": "Avenida Dom Luís",
    "number": "1233",
    "district": "Aldeota",
    "city": "Fortaleza",
    "state": "CE",
    "zipCode": "60160-230"
  },
  "confidence": "ROOFTOP",
  "provider": "brasilapi"
}
```

> O campo `provider` indica qual provider da chain respondeu. `brasilapi` só aparece quando o Nominatim não achou nada ou estava fora — e nesse caso `confidence` é sempre `CITY`.

**Possíveis status:**

| Status | Quando | Ação do front |
|---|---|---|
| 200 | Endereço localizado | Mostrar pin no mapa, deixar usuário confirmar |
| 400 | Validação falhou | Exibir erros de campo de `ApiError.fields` |
| 401 | Token ausente/inválido | Redirecionar para login |
| 422 | `ADDRESS_NOT_GEOCODABLE` — endereço não localizável | Pedir para revisar o endereço (CEP errado? rua não encontrada?) |
| 429 | `GEOCODING_RATE_LIMITED` — limite externo atingido | Mostrar "tente em alguns segundos" |
| 503 | `GEOCODING_UNAVAILABLE` — provider offline ou kill-switch ativo | Permitir avançar sem o pin (cadastrar mesmo assim — ver Fluxo 5.B) |

### `confidence` — como interpretar

| Valor | Significado | UX sugerida |
|---|---|---|
| `ROOFTOP` | Coordenada precisa do edifício/casa | Aceita pelo Express se salva com `coordinateSource: geocoded` |
| `INTERPOLATED` | Coordenada estimada ao longo da rua | "localização aproximada" — o Express recusa sem ajuste no pin |
| `CITY` | Caiu no bairro, no município, ou veio do CEP | "não achamos o ponto exato — marque no mapa"; o Express recusa |
| `NOT_FOUND` | (Nunca aparece no 200, só na exceção 422) | — |

### `POST /api/v1/geocoding/reverse`

Converte um ponto em endereço escrito. Serve para preencher rua e bairro depois que a pessoa arrasta o pin.

**Request:** `{ "lat": -3.734080, "lng": -38.494210 }` — os dois obrigatórios.

**Resposta:** mesmo `GeocodeResponse` do lookup. Uma garantia importante: **`lat`/`lng` na resposta são exatamente os que foram enviados.** O endereço encontrado nunca reposiciona o pin do usuário — corrigir o ponto por conta própria seria mover o local do atendimento sem avisar.

A chave de cache arredonda para 5 casas decimais (~1 metro), para que arrastar o pin um pixel não gere entrada nova.

### `POST /api/users/{userId}/addresses` — contrato de procedência

O save **não geocodifica**. Em compensação, ele passou a exigir que quem manda uma coordenada diga de onde ela veio. Campos novos (V25):

| Campo | Regra |
|---|---|
| `coordinateSource` | `device_gps`, `user_pin` ou `geocoded`. **Obrigatório quando `lat`/`lng` são enviados**, proibido quando não são. `legacy` é interno e o cliente não pode enviar |
| `coordinateAccuracyMeters` | Só com `coordinateSource: device_gps` |
| `coordinateConfidence` | Só com `coordinateSource: geocoded` — repasse o `confidence` que o lookup devolveu |

Violar qualquer uma dessas combinações é **400**, com o campo apontado em `ApiError.fields`.

A resposta traz de volta os três campos, mais `coordinateConfirmedAt` e **`expressReady`** — um booleano derivado que diz se aquela coordenada é aceita pelo Express. O front deve usar `expressReady`, não recalcular a regra.

### `PUT /api/users/{userId}/addresses/{id}`

Também não geocodifica. Duas regras:

- **Coordenada e origem viajam juntas.** Mandar `lat`/`lng` sem `coordinateSource` é 400.
- **Editar o endereço escrito sem reenviar o pin rebaixa a procedência para `legacy`.** A coordenada continua gravada (serve de ponto de partida no mapa), mas o endereço sai do Express até alguém reconfirmar. Sem isso, trocar a rua de um endereço deixaria um pin confirmado apontando para outro lugar, em silêncio.

### Portão do Express

`POST /api/v1/orders/express` recusa endereço cuja coordenada não seja confiável:

| Origem | Express |
|---|---|
| `device_gps` | aceita |
| `user_pin` | aceita |
| `geocoded` | aceita só com `confidence: ROOFTOP` |
| `legacy` | recusa |
| sem coordenada | recusa |

Recusa devolve **422** com código estável em `ApiError.fields.code`:

| Código | Significado |
|---|---|
| `ADDRESS_COORDINATE_MISSING` | o endereço nunca teve ponto no mapa |
| `ADDRESS_COORDINATE_NOT_TRUSTED` | tem ponto, mas de procedência insuficiente |

Nos dois casos a saída para o usuário é a mesma: abrir o endereço e confirmar o pin. A regra vive em `CoordinateTrust`, no domínio, porque a listagem de endereços precisa responder a mesma pergunta para sinalizar o que exige reconfirmação.

---

## 5. Fluxo do front

Existe **um** fluxo. A pessoa confirma o ponto no mapa antes de salvar; não há caminho que grave endereço sem alguém ter olhado onde o pin caiu.

```
1. Pessoa preenche o formulário

2. Formulário completo → o app chama o lookup SOZINHO (debounce de 900 ms,
   uma vez por endereço distinto) e abre o mapa no resultado

   ROOFTOP        → pin aceso, já salvável (coordinateSource: geocoded)
   qualquer outro → mapa abre na rua certa, pin APAGADO: "toque para confirmar"

3. A pessoa fecha o ponto por um dos caminhos:

   a) "Usar minha localização"  → GPS do aparelho
      → coordinateSource: device_gps + coordinateAccuracyMeters

   b) toca ou arrasta o pin     → coordinateSource: user_pin
      → POST /api/v1/geocoding/reverse preenche rua e bairro vazios

   c) "Sugerir pelo endereço"   → pede a sugestão manualmente
      → disponível bem antes do formulário completo: basta CEP,
        ou rua + cidade (a mesma regra do `isUsable()` do GeocodeRequest)

4. POST /api/users/{userId}/addresses
   { ...campos, lat, lng, coordinateSource, [accuracy | confidence] }

5. Backend grava exatamente o que veio. Nenhuma chamada externa.
```

**A busca automática não é autocomplete.** Dispara uma vez por endereço distinto, só com o formulário completo, e nunca por cima de um ponto que a pessoa já escolheu pelo GPS ou pelo dedo. Erro nela é silencioso — ela não pediu essa consulta.

Quatro detalhes que parecem pequenos e não são:

**A tela mostra a mesma régua que o servidor aplica.** Sugestão só conta como ponto escolhido com precisão `ROOFTOP`, que é exatamente quando `CoordinateTrust` aceita origem `geocoded`. Assim ninguém salva um endereço que vai ser recusado depois, na hora do pedido.

**Abrir o mapa não é escolher o ponto.** O mapa precisa de um centro para abrir, e o centro de Fortaleza serve quando não há sugestão. Mas esse ponto de partida não pode contar como escolha — antes, abrir o mapa e salvar sem tocar em nada gravava o centro da cidade como se fosse o endereço da pessoa.

**Nunca sobrescrever o que a pessoa digitou.** O reverse e o lookup preenchem apenas campos vazios. Divergência entre o digitado e o encontrado vira **aviso**, não correção automática — ela conhece o endereço melhor que o OpenStreetMap.

**Sugestão aproximada precisa parecer aproximada.** Com `confidence` diferente de `ROOFTOP`, o marcador fica apagado e o texto diz o que falta: "achamos a rua, mas não o ponto exato". O trabalho que sobra para a pessoa é aproximar o pin alguns metros, não caçar o endereço no mapa.

> Consequência prática: hoje o app só envia `coordinateSource: geocoded` quando a confiança é `ROOFTOP`. A API continua aceitando as outras (e o Express continua recusando) — o contrato é mais geral que o cliente de propósito, para não travar outro consumidor.

### Endereços anteriores à V25

A migration marcou como `legacy` toda coordenada que já existia. Elas continuam no banco e abrem o mapa no ponto antigo, mas `expressReady` vem `false`: a listagem sinaliza, e o fluxo Express leva para a tela de edição em vez de deixar abrir o pedido.

### Signup

`POST /api/users` não cria endereço. O fluxo continua encadeado:

```
1. POST /api/users           (cria a conta)
2. POST /api/auth/login      (obtém token)
3. POST /api/users/{id}/addresses   (primeiro endereço, com pin confirmado)
```

`User` não tem endereço próprio — endereços vivem em `saved_addresses` (1:N).

---

## 6. Cache e limites operacionais

### Cache Redis

| TTL | Quando |
|---|---|
| 30 dias (`GEOCODING_CACHE_TTL_SECONDS=2592000`) | Resultado positivo |
| 5 minutos (`GEOCODING_NEGATIVE_CACHE_TTL_SECONDS=300`) | Resultado `NOT_FOUND` (endereço inexistente) |

**Chave do lookup:** `geocode:<sha256(zipCode + street + number + district + city + state, normalizados)>`

- Inclui CEP — endereços com mesmo nome de rua mas em CEPs distintos não colidem;
- Inclui **bairro** — endereços que só diferem nele são endereços diferentes. Antes o bairro ficava de fora, e "Aldeota" recebia a coordenada cacheada de "Meireles";
- Normalização remove acento, caixa e espaço repetido — "Av. Dom Luís" e "av. dom luis" são a mesma consulta e não devem bater duas vezes no provider;
- `complement` e `label` **não** entram na chave (não influenciam o resultado).

**Chave do reverse:** `geocode:rev:<lat>,<lng>` arredondados a 5 casas (~1 metro). Sem o arredondamento, cada pixel arrastado no mapa viraria chave nova e o cache não serviria para nada.

### Rate limiter

O `NominatimRateLimiter` espaça as chamadas em `GEOCODING_MIN_INTERVAL_MS` (padrão 1100 ms). Quem chega quando a fila passaria de `GEOCODING_MAX_WAIT_MS` recebe 429 em vez de ficar pendurado — melhor devolver "tente de novo" rápido do que segurar a thread do request.

O limitador é **por instância**. Com várias réplicas atrás do mesmo IP de saída seria preciso um contador distribuído; para o cenário atual, de instância única, o custo extra não se paga.

### Por que TTL longo no positivo?

Coordenadas de um endereço raramente mudam. 30 dias é suficiente para reuso entre múltiplos cadastros do mesmo endereço (família, condomínio, profissional cadastrando endereço de cliente).

### Por que TTL curto no negativo?

Se o usuário digitou o endereço errado e corrigiu, não queremos punir 30 dias. 1h é suficiente para evitar martelar o Nominatim com tentativas idênticas.

---

## 7. Tratamento de falhas

### No backend

| Cenário | Comportamento | Status |
|---|---|---|
| Endereço localizado | Sucesso | 200 |
| Endereço não existe | Marker `NOT_FOUND` no cache | 422 |
| Ponto fora da bounding box | Descartado como inexistente | 422 |
| Toda a chain em timeout / 5xx | Log warn | 503 |
| Provider 429, ou fila local estourada | Log warn | 429 |
| `GEOCODING_ENABLED=false` | Kill-switch | 503 |
| **Redis fora do ar** | Log warn, vira cache miss | 200 (mais lento) |

O save de endereço não aparece nesta tabela de propósito: ele não chama o provider, então nenhuma falha de geocoding pode afetá-lo. Provider fora do ar hoje significa apenas que o botão de sugestão não funciona — a pessoa marca o pin no mapa e salva normalmente.

### Formato de erro

Sempre o `ApiError` padrão da API:

```json
{
  "status": 422,
  "message": "Endereço não localizável",
  "fields": null,
  "timestamp": "2026-05-04T22:50:00Z"
}
```

---

## 8. Variáveis de ambiente

| Variável | Obrigatória | Padrão | Descrição |
|---|---|---|---|
| `GEOCODING_BASE_URL` | Não | `https://nominatim.openstreetmap.org` | Base URL do provider |
| `GEOCODING_USER_AGENT` | **Sim** | — | User-Agent identificador exigido pelo Nominatim. Ex: `AllSet-API/1.0 (contato@allset.com.br)` |
| `GEOCODING_CACHE_TTL_SECONDS` | Não | `2592000` | TTL do cache positivo (30 dias) |
| `GEOCODING_NEGATIVE_CACHE_TTL_SECONDS` | Não | `300` | TTL do cache negativo (5 minutos) |
| `GEOCODING_ENABLED` | Não | `true` | Kill-switch operacional. `false` faz lookup e reverse devolverem 503 |
| `GEOCODING_MIN_INTERVAL_MS` | Não | `1100` | Intervalo mínimo entre chamadas ao Nominatim (política: 1 req/s) |
| `GEOCODING_MAX_WAIT_MS` | Não | `3000` | Espera máxima na fila do rate limiter antes de devolver 429 |
| `GEOCODING_BOUNDING_BOX` | Não | `-7.9,-41.5,-2.7,-37.2` | `minLat,minLng,maxLat,maxLng`. Resultado fora é descartado. Vazio desliga |
| `REDIS_TIMEOUT` / `REDIS_CONNECT_TIMEOUT` | Não | `2s` | Timeouts do Lettuce. Sem eles, Redis fora do ar pendurava o lookup por 60 s |

A aplicação **não inicia** se `GEOCODING_USER_AGENT` não estiver setado.

---

## 9. Estrutura de pacotes

```
src/main/java/com/allset/api/geocoding/
├── controller/GeocodingController.java          # POST /lookup e POST /reverse
├── service/
│   ├── GeocodingService.java                    # interface alto nível
│   ├── GeocodingServiceImpl.java                # cache Redis + chain de providers
│   └── GeocodingBounds.java                     # descarta ponto fora da área
├── provider/
│   ├── GeocodingProvider.java                   # SPI agnóstica do fornecedor
│   ├── CompositeGeocodingProvider.java          # @Primary — Nominatim resolve, CEP enriquece
│   ├── BrasilApiCepProvider.java                # rua/bairro pelo CEP; coordenada só como último recurso
│   ├── NominatimGeocodingProvider.java          # resolve o ponto (cascata de 3) + reverse
│   ├── NominatimRateLimiter.java                # 1 req/s por IP
│   └── dto/
│       ├── BrasilApiCepResponse.java            # mapping da resposta BrasilAPI v2
│       └── NominatimResponse.java               # mapping cru da resposta OSM
├── dto/
│   ├── GeocodeRequest.java                      # entrada do lookup
│   ├── ReverseGeocodeRequest.java               # entrada do reverse
│   ├── GeocodeResponse.java                     # saída dos dois
│   ├── NormalizedAddress.java                   # rua, bairro, cidade, estado, CEP normalizados
│   └── GeocodeConfidence.java                   # ROOFTOP, INTERPOLATED, CITY, NOT_FOUND
└── exception/
    ├── AddressNotGeocodableException.java       # 422
    ├── GeocodingProviderUnavailableException.java   # 503
    └── GeocodingRateLimitException.java         # 429
```

Convenção do `CLAUDE.md` respeitada (`controller/service/dto/exception`) + subpacote `provider/` para a SPI.

---

## 10. Decisões de design

### Não tocar em `POST /api/users`

`User` não possui endereço próprio — endereços vivem em `saved_addresses` (1:N). Embutir endereço no `User` quebraria essa invariante e duplicaria informação. O front é responsável por encadear "criar usuário → criar primeiro endereço".

### Coordenadas vs. endereço escrito

Ambos são gravados em `saved_addresses`. O backend **não** sobrescreve o endereço escrito pelo usuário com o `normalizedAddress` do provider — Nominatim normaliza para "Avenida Dom Luís", mas o cliente pode ter digitado "Av. Dom Luís" e queremos respeitar isso. A normalização fica disponível na resposta do `lookup` para o front exibir/sugerir.

### Schema do banco

As colunas `saved_addresses.lat` e `saved_addresses.lng` (`NUMERIC(9,6)` nullable) existem desde a V3. A **V25** acrescentou a procedência:

| Coluna | Tipo | Papel |
|---|---|---|
| `coordinate_source` | `VARCHAR(20)` | `device_gps`, `user_pin`, `geocoded`, `legacy` |
| `coordinate_accuracy_meters` | `NUMERIC(7,2)` | acurácia do GPS |
| `coordinate_confidence` | `VARCHAR(20)` | confiança do provider, quando origem `geocoded` |
| `coordinate_confirmed_at` | `TIMESTAMPTZ` | momento da confirmação |

Três CHECK constraints garantem no banco o que a validação garante na borda: origem só com valor do enum, acurácia só com `device_gps`, e origem só com coordenada presente. O backfill marcou como `legacy` tudo que já tinha coordenada.

A mesma migration corrigiu `professionals.geo_captured_at` de `TIMESTAMP` para `TIMESTAMPTZ` — a V23 tinha contrariado a convenção do `CLAUDE.md` de usar `Instant`/UTC em toda timestamp.

### Coordenada sem procedência não existe para o Express

Antes, a checagem do Express era "tem `lat`/`lng`?". O centroide do município passava nela — e o resultado era uma busca de 300 metros centrada a quilômetros do cliente, que não encontrava ninguém e cancelava o pedido sozinho. Ter coordenada e ter coordenada boa são perguntas diferentes; `CoordinateTrust` responde a segunda.

### Nenhuma chamada externa no caminho de escrita

Salvar endereço virou só salvar endereço. Além do argumento de produto (o ponto certo é o do atendimento, e só o usuário sabe qual é), some o HTTP externo de dentro da transação JPA e some o cenário de endereço gravado com coordenada nula por falha de provedor.

Provider fora do ar hoje degrada apenas a **sugestão** — o cadastro segue pelo mapa.

---

## 11. Privacidade e LGPD

- **Coordenadas exatas nunca são expostas a terceiros**: regra do produto. No fluxo Express, o cliente vê apenas a quantidade de profissionais no raio e a faixa de distância (terço do raio configurado) por proposta — nunca lat/lng nem distância em metros do profissional. Mesma regra vale na direção oposta.
- **`GET /api/users/{userId}/addresses/{id}`** retorna coords apenas ao próprio dono ou a admin (regra `@PreAuthorize("hasAuthority('admin') or #userId.toString() == authentication.name")`).
- **Cache de geocoding** armazena apenas o resultado, não vincula à identidade do usuário. Mesmo CEP+rua de dois usuários diferentes compartilha a mesma entrada.
- **Dados enviados ao Nominatim**: CEP, rua, número, cidade, estado. Não enviamos nome, e-mail, CPF ou identificador de usuário. A política de uso pública do OSM se aplica — equivalente ao que qualquer aplicação de mapas envia.

---

## 12. Roadmap pós-MVP

- **Self-host de Nominatim** em VPS própria quando ultrapassar ~50k geocodings/mês — libera o limite de 1 req/s e remove dependência do serviço público. Vale registrar o que já foi medido: o extrato Nordeste da Geofabrik tem 418 MB e a imagem `mediagis/nominatim` 358 MB, então é gratuito em licença e dados e custa apenas infraestrutura. **Não melhora a precisão** — são os mesmos dados do OSM;
- **Métricas Prometheus** — contadores de hit/miss de cache, latência do provider, taxa de `NOT_FOUND`, e uso do rate limiter;
- **Rate limiter distribuído** — trocar o contador em memória por Redis quando houver mais de uma réplica atrás do mesmo IP;
- **Provider secundário com failover** — Google ou OpenCage quando o Nominatim cair, no lugar de degradar para a coordenada `CITY` do CEP;
- **Filtro de recência no matching Express** — a V23 preparou `geo_captured_at` e a V25 corrigiu o tipo, mas a query ainda filtra só por `geo_active`.
