# Geocoding — Documentação Técnica

Módulo responsável por converter endereços escritos em coordenadas geográficas (`lat`/`lng`).
Usado no cadastro de endereços salvos do cliente e como base para o matching Express por proximidade.

---

## Índice

1. [Visão geral](#1-visão-geral)
2. [Provider escolhido — Nominatim/OSM](#2-provider-escolhido--nominatimosm)
3. [Arquitetura](#3-arquitetura)
4. [Endpoints](#4-endpoints)
5. [Fluxo recomendado para o front](#5-fluxo-recomendado-para-o-front)
6. [Cache e limites operacionais](#6-cache-e-limites-operacionais)
7. [Tratamento de falhas](#7-tratamento-de-falhas)
8. [Variáveis de ambiente](#8-variáveis-de-ambiente)
9. [Estrutura de pacotes](#9-estrutura-de-pacotes)
10. [Decisões de design](#10-decisões-de-design)
11. [Privacidade e LGPD](#11-privacidade-e-lgpd)
12. [Roadmap pós-MVP](#12-roadmap-pós-mvp)

---

## 1. Visão geral

O front envia o endereço escrito (CEP, rua, número, bairro, cidade, estado, complemento). O backend resolve isso em coordenadas usando uma API externa de geocoding e devolve `lat`/`lng` + endereço normalizado.

Há dois pontos de entrada:

- **Lookup explícito** (`POST /api/v1/geocoding/lookup`) — front pede coordenadas, recebe a resposta, exibe o pin no mapa, usuário confirma. **Não persiste nada.**
- **Enriquecimento automático no save** (`POST /api/users/{userId}/addresses`) — se o request chegar sem `lat`/`lng`, o backend chama o provider antes de gravar. Se chegar com `lat`/`lng` (front já confirmou via lookup), respeita o que veio.

```
Front → POST /api/v1/geocoding/lookup → GeocodingService → cache Redis ↔ Nominatim
                                                             ↓
                                                    GeocodeResponse (lat, lng, normalized)
```

---

## 2. Providers — chain BrasilAPI + Nominatim

### Por quê uma chain

Testes em produção com endereços de Fortaleza/CE mostraram que o **Nominatim/OSM tem dados esparsos para endereços brasileiros** — em particular, CEPs e numeração de rua raramente estão indexados, então CEP `60130-160` (Joaquim Távora) caía em `confidence=CITY` no centro de Fortaleza em vez de retornar a rua.

A solução foi encadear dois providers gratuitos:

| Critério | BrasilAPI v2 | Nominatim | Google | OpenCage |
|---|---|---|---|---|
| Custo | Gratuito | Gratuito | Pago após free tier | 2.500/dia grátis |
| Chave de API | Não | Não | Sim | Sim |
| Cobertura BR | Excelente — agrega ViaCEP/OpenCEP/Postmon/WideNet | Limitada (OSM) | Excelente | Boa |
| Aceita endereço sem CEP | Não | Sim | Sim | Sim |
| Geocoding por número | Não — precisão de CEP | Sim, quando OSM tem o dado | Sim | Sim |

### Estratégia

```
                      query.zipCode != null?
                              │
                ┌─────────────┴──────────────┐
                ▼                            ▼
       BrasilAPI v2 (CEP)              Nominatim (livre)
       ↓ tem coords?
       ├── sim  → retorna
       └── não  → fallback Nominatim
```

| Caso | Tentativa 1 | Tentativa 2 |
|---|---|---|
| Request com CEP | BrasilAPI v2 | Nominatim livre |
| BrasilAPI sem coords (fonte interna ViaCEP) | — | Nominatim livre |
| BrasilAPI offline / 5xx | — | Nominatim livre |
| Request sem CEP (só rua + cidade) | — | Nominatim livre |

Falhas operacionais (timeout, 5xx, 429) de um provider não derrubam o lookup — caem no próximo. `GeocodingProviderUnavailableException` só é lançada quando **toda a chain** falhou.

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
- limite de 1 req/s por IP — mitigado pelo cache Redis de 30 dias;
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

Timeouts: connect 2s, read 5s para ambos providers.

---

## 3. Arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│                  GeocodingController                        │
│              POST /api/v1/geocoding/lookup                  │
└─────────────────────────┬───────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                  GeocodingService                           │
│   1. kill-switch GEOCODING_ENABLED → 503 se off             │
│   2. cache Redis (chave SHA-256)                            │
│   3. miss → provider                                        │
│   4. resultado positivo → cache 30d                         │
│   5. resultado vazio → marker NOT_FOUND no cache 1h + 422   │
└─────────────────────────┬───────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────────┐
│        GeocodingProvider (SPI) — @Primary                   │
│                                                             │
│        CompositeGeocodingProvider                           │
│            ├─ BrasilApiCepProvider   (CEP brasileiro)       │
│            └─ NominatimGeocodingProvider  (fallback)        │
│                                                             │
│        Falha de um provider cai no próximo.                 │
│        Só lança 503 quando TODOS falharam.                  │
└─────────────────────────────────────────────────────────────┘
```

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

> O campo `provider` indica qual provider da chain respondeu (`brasilapi` ou `nominatim`).

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
| `ROOFTOP` | Coordenada precisa do edifício/casa | Pin verde, "endereço confirmado" |
| `INTERPOLATED` | Coordenada estimada ao longo da rua | Pin amarelo, "localização aproximada" — pedir confirmação |
| `CITY` | Caiu só no bairro/cidade (não achou número) | Pin vermelho, "não achamos o número exato — confirme no mapa" |
| `NOT_FOUND` | (Nunca aparece no 200, só na exceção 422) | — |

### `POST /api/users/{userId}/addresses` (alterado)

O contrato existente continua igual. **Mudança comportamental:**

- Se o request chegar com `lat`/`lng` → grava o que veio (não chama o provider).
- Se chegar **sem** `lat`/`lng` → backend chama o Nominatim antes de gravar.
- Se o Nominatim disser que o endereço não existe → **422** (não cria o endereço).
- Se o Nominatim estiver fora (timeout, 5xx) → **201** com `lat=null` / `lng=null` + log warn no servidor. Cadastro **não é bloqueado** por instabilidade externa.

### `PUT /api/users/{userId}/addresses/{id}` (alterado)

Re-geocodifica automaticamente **somente se** algum dos campos `street`, `number`, `zipCode`, `city`, `state` mudou **e** o request não trouxe `lat`/`lng` novos. Mudar só `label` ou `complement` não chama o provider.

---

## 5. Fluxo recomendado para o front

Existem dois fluxos válidos. Escolha um conforme a UX do produto.

### 5.A. Fluxo com confirmação no mapa (recomendado para web/desktop e celular com mapa)

```
1. Usuário preenche o formulário de endereço
2. Front → POST /api/v1/geocoding/lookup
3. Front exibe pin no mapa com base em lat/lng + confidence
4. Usuário confirma (ou arrasta o pin manualmente)
5. Front → POST /api/users/{userId}/addresses
   { ...campos, "lat": <confirmado>, "lng": <confirmado> }
6. Backend grava direto sem chamar o provider (respeita lat/lng do request)
```

Vantagens:

- usuário valida a localização antes de salvar;
- backend não chama o Nominatim duas vezes para o mesmo endereço (cache cobre, mas ainda assim economiza);
- `confidence` permite o front insistir em confirmação manual quando o resultado é fraco.

### 5.B. Fluxo direto (UX rápida, sem mapa)

```
1. Usuário preenche o formulário de endereço
2. Front → POST /api/users/{userId}/addresses
   { ...campos } SEM lat/lng
3. Backend geocodifica internamente e grava
4. Resposta 201 inclui lat/lng (se localizado)
   OU lat=null/lng=null (se Nominatim estiver offline)
```

Quando usar: signup mobile rápido, fluxos onde mostrar mapa custa caro.

### 5.C. Fluxo de signup completo

`POST /api/users` **não** cria endereço. O fluxo é encadeado:

```
1. Front → POST /api/users           (cria a conta)
2. Front → POST /api/auth/login      (obtém token)
3. Front → POST /api/users/{id}/addresses   (cria o primeiro endereço,
                                             geocodificado automaticamente)
```

Decisão consciente: `User` não tem endereço próprio — endereços vivem em `saved_addresses` (1:N). Embutir endereço no `User` quebraria essa invariante.

### 5.D. Recuperação quando lat/lng vieram nulos

Se o save retornou `lat=null` / `lng=null` (provider estava fora no momento), o front pode reexecutar:

```
1. Front → POST /api/v1/geocoding/lookup    (com os mesmos campos)
2. Se 200: Front → PUT /api/users/{userId}/addresses/{id}
                   { "lat": <novo>, "lng": <novo> }
3. Se 503/422: continuar oferecendo "atualizar localização" depois
```

---

## 6. Cache e limites operacionais

### Cache Redis

| TTL | Quando |
|---|---|
| 30 dias (`GEOCODING_CACHE_TTL_SECONDS=2592000`) | Resultado positivo |
| 5 minutos (`GEOCODING_NEGATIVE_CACHE_TTL_SECONDS=300`) | Resultado `NOT_FOUND` (endereço inexistente) |

**Chave do cache:** `geocode:<sha256(zipCode + street + number + city + state, normalizados)>`

- Inclui CEP — endereços com mesmo nome de rua mas em CEPs distintos não colidem;
- Lowercase + trim — variação de capitalização compartilha entrada;
- `complement`, `district` e `label` **não** entram na chave (não influenciam o resultado do provider).

### Por que TTL longo no positivo?

Coordenadas de um endereço raramente mudam. 30 dias é suficiente para reuso entre múltiplos cadastros do mesmo endereço (família, condomínio, profissional cadastrando endereço de cliente).

### Por que TTL curto no negativo?

Se o usuário digitou o endereço errado e corrigiu, não queremos punir 30 dias. 1h é suficiente para evitar martelar o Nominatim com tentativas idênticas.

---

## 7. Tratamento de falhas

### No backend

| Cenário | Comportamento | Status no `lookup` | Status no `save` |
|---|---|---|---|
| Endereço localizado | Sucesso | 200 | 201 |
| Endereço não existe | Marker `NOT_FOUND` no cache | 422 | 422 |
| Provider timeout / 5xx | Log warn | 503 | 201 com `lat=null` / `lng=null` |
| Provider 429 | Log warn | 429 | 201 com `lat=null` / `lng=null` |
| `GEOCODING_ENABLED=false` | Kill-switch | 503 | 201 com `lat=null` / `lng=null` |

A política assimétrica (lookup retorna 503, save grava sem coords) é intencional:

- no **lookup**, o front precisa saber que falhou para tentar de novo;
- no **save**, bloquear cadastro por instabilidade externa quebra UX e inflaciona dependência operacional.

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
| `GEOCODING_ENABLED` | Não | `true` | Kill-switch operacional. `false` desabilita lookup e enriquecimento |

A aplicação **não inicia** se `GEOCODING_USER_AGENT` não estiver setado.

---

## 9. Estrutura de pacotes

```
src/main/java/com/allset/api/geocoding/
├── controller/GeocodingController.java          # POST /api/v1/geocoding/lookup
├── service/
│   ├── GeocodingService.java                    # interface alto nível
│   └── GeocodingServiceImpl.java                # cache Redis + chain de providers
├── provider/
│   ├── GeocodingProvider.java                   # SPI agnóstica do fornecedor
│   ├── CompositeGeocodingProvider.java          # @Primary — chain BrasilAPI → Nominatim
│   ├── BrasilApiCepProvider.java                # primário p/ CEP brasileiro
│   ├── NominatimGeocodingProvider.java          # fallback p/ endereço livre
│   └── dto/
│       ├── BrasilApiCepResponse.java            # mapping da resposta BrasilAPI v2
│       └── NominatimResponse.java               # mapping cru da resposta OSM
├── dto/
│   ├── GeocodeRequest.java                      # entrada do lookup
│   ├── GeocodeResponse.java                     # saída do lookup
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

Não há migration nova para este módulo. As colunas `saved_addresses.lat` e `saved_addresses.lng` (`NUMERIC(9,6)` nullable) já existem desde a V3.

### Tolerância a falha no save

Quando o Nominatim está offline, o save grava com `lat`/`lng` nulos em vez de retornar erro. Justificativa:

- não bloqueia a jornada do usuário por instabilidade externa;
- coordenadas nulas já são suportadas pelo schema;
- o front pode oferecer "atualizar localização" depois via `lookup` + `PUT`.

A consequência é que **o consumidor das coordenadas precisa lidar com `null`** — em particular, o matching Express (`order` module) já trata isso filtrando endereços sem coords.

---

## 11. Privacidade e LGPD

- **Coordenadas exatas nunca são expostas a terceiros**: regra do produto. No fluxo Express, o cliente vê apenas a quantidade de profissionais no raio e a faixa de distância (terço do raio configurado) por proposta — nunca lat/lng nem distância em metros do profissional. Mesma regra vale na direção oposta.
- **`GET /api/users/{userId}/addresses/{id}`** retorna coords apenas ao próprio dono ou a admin (regra `@PreAuthorize("hasAuthority('admin') or #userId.toString() == authentication.name")`).
- **Cache de geocoding** armazena apenas o resultado, não vincula à identidade do usuário. Mesmo CEP+rua de dois usuários diferentes compartilha a mesma entrada.
- **Dados enviados ao Nominatim**: CEP, rua, número, cidade, estado. Não enviamos nome, e-mail, CPF ou identificador de usuário. A política de uso pública do OSM se aplica — equivalente ao que qualquer aplicação de mapas envia.

---

## 12. Roadmap pós-MVP

- **Self-host de Nominatim** em VPS própria quando ultrapassar ~50k geocodings/mês — libera o limite de 1 req/s e remove dependência do serviço público;
- **Fallback BrasilAPI v2** para autocompletar rua/bairro/cidade a partir só do CEP (front pode chamar antes do `lookup`, reduz fricção em mobile);
- **Reverse geocoding** (`lat,lng` → endereço) para o fluxo Express — cliente arrasta pin no mapa em vez de digitar;
- **Métricas Prometheus** — contadores de hit/miss de cache, latência do provider, taxa de `NOT_FOUND`;
- **Provider secundário com failover** — Google ou OpenCage como fallback automático quando o Nominatim cair (hoje cai em `lat=null`).
