# Fluxo de localização — como funciona hoje, nos dois repos

*Referência de como o endereço vira um ponto no mapa, e o que cada caso produz. Cobre `AllSet` (back) e `TCC-front` (app).*

Para o **porquê** da mudança, ver `localizacao.md`. Para o detalhe técnico do módulo de geocoding, ver `geocoding.md`.

---

## A regra que explica tudo o resto

O modo Express avisa profissionais num raio de **300 metros** em volta do endereço. Nesse raio, a diferença entre o meio da rua e o portão certo decide se alguém é notificado.

Daí vêm as duas decisões que governam o fluxo:

1. **A API não geocodifica ao salvar.** Ela grava o ponto que o app mandar.
2. **Todo ponto viaja com a procedência.** De onde ele veio importa tanto quanto o valor.

O geocoding continua existindo — só que rebaixado ao papel que ele consegue cumprir: **sugerir** onde abrir o mapa.

---

## Visão geral

```
┌─ TCC-front ────────────────────────────────────────────────────────┐
│                                                                    │
│  Formulário de endereço                                            │
│         │                                                          │
│         ├── completo ──────────────► lookup automático (900 ms)    │
│         │                                    │                     │
│         ├── "Usar minha localização" ── GPS ─┤                     │
│         ├── "Sugerir pelo endereço" ─────────┤                     │
│         └── "Escolher no mapa" ──────────────┤                     │
│                                              ▼                     │
│                                    mapa abre num ponto             │
│                                              │                     │
│                              ┌───────────────┴───────────────┐     │
│                              ▼                               ▼     │
│                     pin ACESO (escolhido)          pin APAGADO     │
│                              │                    (só referência)  │
│                              │                               │     │
│                              │           toca ou arrasta ────┘     │
│                              ▼                                     │
│                    Salvar { lat, lng, coordinateSource }           │
└──────────────────────────────┬─────────────────────────────────────┘
                               ▼
┌─ AllSet ───────────────────────────────────────────────────────────┐
│  POST /api/users/{id}/addresses                                    │
│    valida a coerência (origem ↔ coordenada ↔ acurácia)             │
│    grava exatamente o que veio — nenhuma chamada externa           │
│    devolve expressReady                                            │
│                                                                    │
│  POST /api/v1/orders/express                                       │
│    CoordinateTrust decide se aquele ponto serve                    │
└────────────────────────────────────────────────────────────────────┘
```

---

## As quatro origens de coordenada

| Origem | Como aparece | Express | Quem grava |
|---|---|---|---|
| `device_gps` | botão "Usar minha localização" | aceita | app |
| `user_pin` | tocou ou arrastou o pin | aceita | app |
| `geocoded` | sugestão aceita sem ajuste | **só com `ROOFTOP`** | app |
| `legacy` | gravado antes desta versão, ou invalidado por edição | recusa | migration / API |

`legacy` é o único que o app **não pode enviar** — a API rejeita com 400. É marcação interna.

A regra vive em `CoordinateTrust` (back) e a API a devolve pronta no campo `expressReady`. O app **não recalcula** — se recalculasse, viraria uma segunda fonte de verdade fadada a divergir.

---

## Caso a caso

### 1. Cadastrando a própria casa, estando nela

O caminho curto.

1. Preenche o formulário. Ao completar, o app já busca sozinho e abre o mapa.
2. Toca em **"Usar minha localização"** — ação de maior destaque da seção.
3. Permissão concedida, GPS lido em precisão alta, mapa reabre no ponto.
4. Confere. Caiu no meio da rua em vez do portão? Arrasta.
5. Salva.

**Vai para a API:** `coordinateSource: device_gps` + `coordinateAccuracyMeters` (o que o aparelho reportou). Se arrastou, vira `user_pin` e a acurácia é descartada — ela descrevia a leitura do GPS, não o dedo.

**Resultado:** endereço nasce pronto para o Express.

### 2. Cadastrando a casa de outra pessoa

Aqui o GPS é armadilha, não atalho: marcaria onde *ela* está, não onde o serviço acontece.

1. Preenche o formulário.
2. Ao completar, o lookup dispara sozinho e o mapa abre. Duas saídas:

   **Achou o prédio (`ROOFTOP`)** — pin aceso, já salvável. Texto: *"Encontramos X. Ajuste o pin se o atendimento for em outra entrada."*

   **Achou só a rua ou o bairro** — mapa abre na rua certa, pin **apagado**. Texto em vermelho: *"Achamos a rua (X), mas não o ponto exato. Toque ou arraste o pin até o local do atendimento."* O botão de salvar fica desabilitado, com o motivo escrito embaixo.

3. Toca no ponto certo → vira `user_pin`.
4. Setecentos milissegundos depois, o app pergunta à API que endereço fica ali e preenche os campos vazios.
5. Salva.

**O trabalho que sobrou:** aproximar o pin alguns metros. Antes desta versão era caçar a casa no mapa a partir do centro de Fortaleza.

### 3. Só tem o CEP em mãos

O botão **"Sugerir pelo endereço"** habilita bem antes do formulário completo — basta CEP, **ou** rua + cidade. É a mesma regra do `isUsable()` no `GeocodeRequest`.

Serve para ver onde fica antes de digitar o resto. O resultado preenche rua, bairro, cidade e estado (campos vazios) e abre o mapa.

Depois, quando o formulário completar, a busca automática **não** repete a consulta se o endereço resultante for o mesmo — a chave é comparada antes de disparar.

### 4. Editando um endereço já salvo

Abrir a tela **não** dispara busca nenhuma: a chave é semeada com o endereço carregado. Visitar não é digitar.

O que acontece depende do que a pessoa mexe:

| Ação | Resultado |
|---|---|
| Muda só o apelido ou complemento | nada acontece com o ponto |
| Muda rua, número, CEP, bairro, cidade ou estado | nova busca automática, mapa reabre no lugar novo |
| Já tinha marcado ponto pelo GPS ou pelo dedo | busca automática **não** roda — sugestão não passa por cima de decisão |
| Salva sem reenviar o pin | **a API rebaixa a procedência para `legacy`** |

Essa última linha é a proteção contra divergência silenciosa: trocar a rua de um endereço deixaria um pin confirmado apontando para outro lugar. A coordenada continua gravada (serve de ponto de partida no mapa), mas o endereço sai do Express até alguém reconfirmar.

Na prática o app sempre reenvia o pin, porque `canSave` exige um. A regra protege quem usar a API direto.

### 5. Endereço cadastrado antes desta versão

A migration V25 marcou como `legacy` toda coordenada que já existia — todas foram gravadas sob as regras antigas e não são confiáveis.

**Na listagem:** *"Ponto aproximado — confirme no mapa para usar no Express."*

**Na tela de edição:** um aviso no topo explicando que o endereço pode estar quilômetros fora, e o pin abre apagado no ponto antigo.

**No fluxo Express:** o texto de aviso é tocável e leva direto para a edição.

Um toque no mapa resolve.

### 6. Permissão de GPS negada

Nada trava. Aparece uma linha: *"Sem acesso à localização. Sem problema: marque o ponto no mapa ou use a sugestão pelo endereço."*

Não insiste, não abre configurações sozinho. Os outros dois caminhos continuam lá.

### 7. Provider de geocoding fora do ar

**Busca automática:** falha em silêncio. A pessoa não pediu essa consulta, então não leva toast — o mapa continua disponível para marcação manual.

**Botão manual:** mostra o erro, porque aí ela pediu.

**O cadastro não é afetado.** Como a API não geocodifica ao salvar, provider fora do ar significa apenas que a sugestão não funciona. Marca no mapa e salva.

Antes desta versão, provider instável gravava endereço com coordenada nula em silêncio.

### 8. Redis fora do ar

O cache é otimização, não dependência: vira cache miss e a consulta segue direto ao provider, mais lenta.

Antes, o lookup ficava **60 segundos pendurado** e terminava em 500, porque o Lettuce não tinha timeout configurado.

---

## O que cada chamada faz no back

| Endpoint | Persiste? | O que faz |
|---|---|---|
| `POST /api/v1/geocoding/lookup` | não | endereço escrito → coordenada + endereço normalizado + confiança |
| `POST /api/v1/geocoding/reverse` | não | coordenada → endereço escrito. **Devolve a coordenada enviada, intacta** |
| `POST /api/users/{id}/addresses` | sim | grava o que veio, valida a coerência da procedência |
| `PUT /api/users/{id}/addresses/{id}` | sim | idem, e rebaixa para `legacy` se o texto mudou sem pin novo |
| `POST /api/v1/orders/express` | sim | recusa 422 se a coordenada não for confiável |
| `POST /api/v1/orders/on-demand` | sim | **não passa pelo portão** — não depende de raio |

O reverse nunca reposiciona o pin. Corrigir o ponto por conta própria seria mover o local do atendimento sem avisar.

### Erros que o app precisa distinguir

| Situação | HTTP | Como identificar |
|---|---|---|
| Endereço sem ponto no mapa | 422 | `fields.code = ADDRESS_COORDINATE_MISSING` |
| Ponto de procedência insuficiente | 422 | `fields.code = ADDRESS_COORDINATE_NOT_TRUSTED` |
| Origem incoerente com a coordenada | 400 | campo apontado em `fields` |
| Endereço não localizável | 422 | sem `code` |
| Provider fora do ar / kill-switch | 503 | — |
| Rate limit (externo ou fila local) | 429 | — |

Nos dois 422 de coordenada a saída para o usuário é a mesma: abrir o endereço e confirmar o pin.

---

## Onde vive cada peça

### `AllSet` (back)

```
address/domain/CoordinateSource.java        # o enum das quatro origens
address/domain/CoordinateTrust.java         # a régua: esse ponto serve para o Express?
address/dto/CoordinateProvenanceRules.java  # coerência origem ↔ coordenada ↔ acurácia
address/exception/AddressCoordinateNotTrustedException.java
address/service/SavedAddressServiceImpl.java  # grava o que veio; rebaixa em edição

geocoding/provider/CompositeGeocodingProvider.java  # Nominatim resolve, CEP enriquece
geocoding/provider/NominatimGeocodingProvider.java  # cascata de 3 + reverse
geocoding/provider/NominatimRateLimiter.java        # 1 req/s por IP
geocoding/service/GeocodingBounds.java              # descarta ponto fora da área
geocoding/service/GeocodingServiceImpl.java         # cache tolerante a falha

order/service/OrderServiceImpl.java          # o portão do Express
db/migration/V25__add_coordinate_provenance_to_saved_addresses.sql
```

### `TCC-front` (app)

```
lib/hooks/useCurrentPosition.ts      # captura única do GPS
lib/hooks/useAddressPin.ts           # estado do pin + procedência; o coração do fluxo
lib/utils/address-fill.ts            # preenche vazios, nunca sobrescreve
components/maps/AddressPinSection.tsx  # a UI compartilhada pelas duas telas
components/maps/PinLocationPicker.tsx  # o mapa; prop `confirmed` apaga o marcador

app/(client)/(profile)/addresses/new.tsx         # cadastro
app/(client)/(profile)/addresses/[addressId].tsx # edição
app/(client)/(profile)/addresses/index.tsx       # listagem sinalizada
app/(client)/(express)/create.tsx                # bloqueio antes do pedido
```

O estado do pin mora em `useAddressPin` e a UI em `AddressPinSection` porque as duas telas precisam responder igual — divergência entre elas foi exatamente como o problema antigo entrava no banco.

---

## Detalhes que parecem pequenos e não são

**Abrir o mapa não é escolher o ponto.** O mapa precisa de um centro, e o centro de Fortaleza serve quando não há sugestão. Mas esse ponto de partida não conta como escolha: o marcador fica apagado e o botão de salvar, desabilitado. Antes, abrir o mapa e salvar sem tocar em nada gravava o centro da cidade como se fosse o endereço da pessoa.

**A tela mostra a mesma régua que o servidor aplica.** Sugestão só vira ponto escolhido com `ROOFTOP` — exatamente quando `CoordinateTrust` aceita origem `geocoded`. Ninguém salva um endereço que vai ser recusado depois, no meio do pedido.

**Nunca sobrescrever o que a pessoa digitou.** Reverse e lookup preenchem apenas campos vazios. Divergência vira aviso — *"O ponto marcado fica em bairro diferente do que você escreveu"* — não correção automática. Ela conhece o endereço melhor que o OpenStreetMap.

**A busca automática não é autocomplete.** Dispara uma vez por endereço distinto, só com o formulário completo, com 900 ms de debounce, e nunca por cima de um ponto já escolhido. A política do Nominatim desaconselha consulta enquanto o usuário digita; um disparo único ao completar não é isso.

---

## Limites conhecidos

**Precisão do OpenStreetMap.** Dos 20 endereços de Fortaleza testados, 8 resolveram no prédio e 12 na rua. Prédio não mapeado no OSM não tem como virar `ROOFTOP` — nesses casos a pessoa sempre vai precisar aproximar o pin. Self-host do Nominatim não mudaria isso: são os mesmos dados.

**Trecho da avenida.** Medido depois do refinamento por bairro: o pin abre na rua pedida em 18 de 20 (as 2 restantes são o mesmo lugar com outro nome), e na rua **e** no bairro em 15 de 20. Os 5 que sobram são avenidas longas em bairro periférico, onde o OSM tem menos dado — e são exatamente os lugares com menos referência visual no mapa para a pessoa acertar o toque. Os dois problemas se somam no mesmo usuário.

**O toque promove o ponto.** Quando a pessoa toca no mapa, o ponto vira `user_pin` e o Express passa a aceitá-lo. Se o mapa abriu no trecho errado e ela não reparou, um ponto ruim entra com carimbo de confiável. O refinamento por bairro reduz a chance de isso acontecer; o aviso de divergência (caso 2) é a rede que sobra depois do toque.

**Rate limiter é por instância.** Com mais de uma réplica atrás do mesmo IP de saída, seria preciso um contador distribuído no Redis.

**Tiles do mapa vêm do servidor público do OpenStreetMap**, que tem política de uso restritiva para aplicações. Serve para a apresentação; produção real precisaria de um fornecedor de tiles.

**Bounding box é regional.** O padrão cobre o Ceará. Atender outro estado exige ajustar `GEOCODING_BOUNDING_BOX` — ou o endereço volta como "não localizável".
