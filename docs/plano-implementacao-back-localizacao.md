# Plano de implementação — Back (localização)

**Repositório:** `AllSet` · branch base `main` (`aeb3625`)
**Contexto:** ver `localizacao-o-que-muda.md`

> **Status: implementado em 23/08/2026.**
> As cinco fases abaixo estão no código. O que mudou em relação ao plano original:
>
> - a **Fase 5.1** revelou a causa real do problema — não era a ordem dos providers, era uma URI codificada duas vezes que fazia o Nominatim responder vazio para todo endereço com espaço no nome. A inversão continuou valendo, mas o defeito de fundo era outro;
> - a **Fase 1** ganhou uma coluna a mais, `coordinate_confidence`, sem a qual a regra da Fase 3 ("`geocoded` só com `ROOFTOP`") não teria como ser aplicada;
> - a **V25** também corrigiu o `TIMESTAMP` da V23 para `TIMESTAMPTZ` — decisão que estava em aberto;
> - a resposta de endereço passou a trazer `expressReady`, para o front não precisar reimplementar a regra de confiança.
>
> Validação: 20 endereços reais de Fortaleza resolvidos 20/20 pelo Docker (antes: 16 no mesmo ponto errado),
> 21/21 checagens de contrato via HTTP, e 41 testes automatizados novos.


Última migration existente: **V24**. As novas começam em **V25**.

---

## Fase 1 — Contrato de procedência da coordenada

**Depende de:** nada
**Desbloqueia:** front Fase 3
**Entrega:** a API passa a saber de onde veio cada coordenada

### Tarefas

**1.1 Migration `V25__add_coordinate_metadata_to_saved_addresses.sql`**

Espelhar o que a `V23` fez em `professionals`, agora em `saved_addresses`:

- `coordinate_source VARCHAR(20)` — valores: `device_gps`, `user_pin`, `geocoded`, `legacy`
- `coordinate_accuracy_meters NUMERIC(7,2)` — nulo quando não vier de GPS
- `coordinate_confirmed_at TIMESTAMPTZ`

Usar **`TIMESTAMPTZ`**, não `TIMESTAMP`. A `V23` usou `TIMESTAMP` em `geo_captured_at`, o que contraria a convenção do `CLAUDE.md` ("timestamps sempre `Instant`/UTC"). Vale corrigir a V23 na mesma leva, mas é decisão à parte.

Backfill no mesmo arquivo: todos os registros existentes recebem `coordinate_source = 'legacy'`. Eles foram criados sob as regras antigas e não são confiáveis.

**1.2 Entidade e DTOs**

- `SavedAddress` — campos novos
- `CreateSavedAddressRequest` / `UpdateSavedAddressRequest` — aceitar origem e precisão
- `SavedAddressResponse` — devolver os dois, para o front conseguir sinalizar
- `SavedAddressMapper` — MapStruct, sem set manual

**1.3 Validação**

Origem só aceita os valores do enum. Precisão só faz sentido com origem `device_gps` — validar a combinação, não só os campos isolados.

### Pronto quando

Um endereço criado com origem e precisão persiste os dois e devolve na resposta. Registros antigos aparecem como `legacy`.

---

## Fase 2 — Tirar o geocoding do caminho de escrita

**Depende de:** Fase 1
**Desbloqueia:** front Fase 5
**Entrega:** salvar endereço vira só salvar endereço

### Tarefas

**2.1 Remover o geocoding automático**

Em `SavedAddressServiceImpl`:

- `create` — tirar a chamada a `tryGeocode` quando `lat`/`lng` vêm nulos
- `update` — tirar a re-geocodificação quando campos de endereço mudam

Guardar o que veio. Se vier sem coordenada, grava sem coordenada — o Express já sabe recusar.

Isso também resolve dois problemas de infraestrutura de brinde: sai o HTTP externo de dentro da transação JPA (hoje segura conexão do pool por até ~14s no pior caso) e some o cenário de endereço salvo com coordenada nula por falha de provedor.

**2.2 Ajustar os testes existentes**

`src/test/java/com/allset/api/address/service/SavedAddressServiceImplTest.java` cobre o comportamento atual, incluindo o geocoding automático. Reescrever os casos afetados.

### Pronto quando

Criar endereço não dispara nenhuma chamada externa. O teste que garantia o geocoding automático foi substituído por um que garante o contrário.

---

## Fase 3 — Express exigir qualidade, não presença

**Depende de:** Fase 1
**Entrega:** a regra dos 300 metros deixa de rodar sobre coordenada ruim

### Tarefas

**3.1 Trocar a checagem**

`OrderServiceImpl` hoje verifica apenas se `lat`/`lng` são nulos — o centro de Fortaleza passa nesse teste.

Passar a verificar a procedência:

| Origem | Express |
|---|---|
| `device_gps` | aceita |
| `user_pin` | aceita |
| `geocoded` | aceita só com confiança `ROOFTOP` |
| `legacy` | recusa |
| nulo | recusa |

**3.2 Exceção e código de erro**

Nova `AddressCoordinateNotTrustedException`, registrada no `GlobalExceptionHandler` como 422, com código padronizado (ex: `ADDRESS_COORDINATE_NOT_TRUSTED`) para o front distinguir de "endereço sem coordenada".

A mensagem precisa dizer o que fazer: confirmar o pin do endereço, não apenas que falhou.

### Pronto quando

Pedido Express sobre endereço `legacy` retorna 422 com código próprio, e o front consegue levar a pessoa para a tela certa.

---

## Fase 4 — Endpoint de reverse geocoding

**Depende de:** nada (mas só é consumido depois)
**Desbloqueia:** front Fase 4
**Entrega:** mover o pin passa a poder preencher o endereço

### Tarefas

**4.1 `POST /api/v1/geocoding/reverse`**

Recebe `lat`/`lng`, devolve o endereço normalizado. Mesma estrutura do `lookup` que já existe: cache no Redis, kill-switch, tratamento de erro pelo `GlobalExceptionHandler`.

Reaproveitar `NormalizedAddress`, que já é o DTO de saída do lookup.

**4.2 Chave de cache por precisão**

Arredondar as coordenadas antes de montar a chave (5 casas decimais ≈ 1 metro). Sem isso, cada pixel arrastado no mapa vira uma chave nova e o cache não serve para nada.

### Pronto quando

Enviar um par de coordenadas de Fortaleza devolve rua e bairro, e a segunda chamada com o mesmo ponto responde do cache.

---

## Fase 5 — Consertar o geocoding em si

**Depende de:** nada
**Entrega:** a sugestão passa a ser uma sugestão útil

Mesmo rebaixado a sugestão, o botão "Usar API" continua existindo no app. Uma sugestão 5 km fora é pior que sugestão nenhuma, porque parece certa.

### Tarefas, por ordem de impacto

**5.1 Inverter a ordem dos provedores**

Em `CompositeGeocodingProvider`, o curto-circuito em `hasCoords()` faz a BrasilAPI ganhar da Nominatim sempre que tiver *alguma* coordenada — e o backend `open-cep` devolve o centro do município como preenchimento.

Inverter: Nominatim primeiro; BrasilAPI só para enriquecer rua e bairro quando o usuário não preencheu.

Na medição, a Nominatim resolveu 20 de 20 endereços, 8 deles com precisão de prédio.

**5.2 Rejeitar coordenada de município**

Descartar resultado que caia no centroide municipal conhecido ou fora de uma caixa delimitadora de Fortaleza. Hoje um CEP inexistente devolveu 200 com um ponto no Paraná.

**5.3 Limitar a taxa de chamadas à Nominatim**

A política é 1 requisição por segundo e cada busca dispara até 3. Durante os testes, 20 buscas em 7 segundos bloquearam o IP por mais de 25 minutos.

Implementar limitador local antes de inverter a ordem — depois da inversão, todo o tráfego passa a ir para lá.

**5.4 Timeout e degradação no Redis**

Com o Redis fora do ar, o lookup ficou pendurado 60 segundos e devolveu 500. O cache é dependência dura hoje. Definir timeout curto e seguir sem cache quando ele falhar.

**5.5 Chave de cache**

Incluir o bairro (hoje fica de fora, então bairros diferentes colidem) e normalizar acentos (hoje "Luís" e "Luis" geram chaves distintas e batem no provedor duas vezes).

**5.6 Testes**

Hoje o módulo de geocoding tem **zero testes**. Cobrir no mínimo: cascata de provedores, cache positivo e negativo, kill-switch, e o caso de rejeição de coordenada municipal.

### Pronto quando

Os 20 endereços de teste voltam com coordenadas distintas e plausíveis, e existe teste que trava a regressão.

---

## Ordem sugerida

| Ordem | Fase | Justificativa |
|---|---|---|
| 1 | Back 5.3 e 5.4 | infraestrutura; barato e evita dor nas fases seguintes |
| 2 | Back Fase 1 | desbloqueia o front mais cedo |
| 3 | Back 5.1 e 5.2 | conserta a sugestão; depende do limitador estar de pé |
| 4 | Back Fase 2 | precisa da Fase 1 |
| 5 | Back Fase 3 | precisa da Fase 1 |
| 6 | Back Fase 4 | menor urgência |
| 7 | Back 5.5 e 5.6 | acabamento e proteção contra regressão |

A Fase 1 é a que o front está esperando — vale priorizar para os dois lados andarem em paralelo.

---

## Como validar

O ambiente de teste usado no diagnóstico está reproduzível: Docker Compose completo, 20 endereços reais de Fortaleza e comparação contra duas fontes independentes. Os scripts estão em `.geo-test/` (pasta não versionada).

Rodar a mesma bateria depois da Fase 5 é a forma mais direta de confirmar que o erro saiu — e vira material de resultado para o TCC.
