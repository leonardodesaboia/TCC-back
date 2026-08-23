# Plano de implementação — Front (localização)

**Repositório:** `TCC-front` · branch base `main` (`8108b8b`)
**Contexto:** ver `localizacao-o-que-muda.md`

> **Status: implementado em 23/08/2026.**
> As cinco fases abaixo estão no código, junto com a contraparte do back. Diferenças em relação ao plano:
>
> - em vez de duplicar a lógica nas duas telas, o estado do pin e sua procedência vivem em `useAddressPin`, e a UI em `AddressPinSection`;
> - `PinLocationPicker` ganhou a prop `confirmed`, que apaga o marcador enquanto ninguém escolheu o ponto — sem isso, um mapa recém-aberto parece já ter um pin;
> - a sinalização de endereço não confiável usa `expressReady`, que a API passou a devolver, em vez de o front recalcular a regra;
> - a Fase 4 foi além do plano: com o formulário completo, o lookup dispara **sozinho** e abre o mapa no lugar certo, para a pessoa só aproximar o pin. O botão manual continua existindo, e ficou disponível mais cedo — basta CEP, ou rua + cidade. Sugestão só conta como ponto escolhido com precisão de edifício.
>
> Validação: `npm run typecheck` limpo e 33 testes passando (15 novos).


As fases 1 e 2 são independentes do back e entregam a maior parte do ganho. As fases 3 a 5 dependem de entregas do back — cada uma indica qual.

---

## Fase 1 — Botão "Usar minha localização"

**Depende de:** nada
**Entrega:** o pin passa a nascer no lugar certo em vez de no centro da cidade

### Tarefas

**1.1 Extrair um hook de captura de posição**

Criar `src/lib/hooks/useCurrentPosition.ts`.

A lógica de permissão e captura já existe em `src/lib/availability/ExpressAvailabilityProvider.tsx` (`requestForegroundPermissionsAsync` + `getCurrentPositionAsync`). Extrair o essencial para um hook reutilizável, sem mexer no provider do profissional — ele tem watch contínuo e regras de flush que não interessam aqui.

O hook deve devolver:

- `lat`, `lng`
- `accuracyMeters` (vem de `position.coords.accuracy`)
- `capturedAt`
- estado: `idle` / `requesting-permission` / `capturing` / `granted` / `denied` / `error`

Usar `Location.Accuracy.High` aqui (o provider usa `Balanced` porque roda contínuo; no cadastro é captura única e vale gastar precisão).

**1.2 Adicionar o botão nas duas telas**

- `src/app/(client)/(profile)/addresses/new.tsx`
- `src/app/(client)/(profile)/addresses/[addressId].tsx`

O botão deve ser a **ação primária** da seção de mapa. Hoje "Escolher no mapa" é `secondary` e "Usar API" é `ghost` — o novo botão entra como `secondary` e os outros dois descem um nível de destaque.

Ao capturar com sucesso: seta o pin, abre o mapa já naquele ponto, e a pessoa confirma ou ajusta.

**1.3 Tratar permissão negada**

Sem travar nada: mensagem curta explicando que dá para marcar no mapa manualmente, e o fluxo segue com os botões que já existem. Não insistir, não abrir configurações automaticamente.

### Pronto quando

Dá para cadastrar um endereço sem tocar no mapa, quando o GPS acerta. Com permissão negada, o cadastro continua possível pelo caminho antigo.

### Testes

Seguir o padrão de `src/lib/availability/__tests__/ExpressAvailabilityProvider.test.tsx`, que já mocka `expo-location`. Casos: permissão concedida, permissão negada, erro na captura.

---

## Fase 2 — Fechar o alçapão do centro da cidade

**Depende de:** nada (mas fica melhor depois da Fase 1)
**Entrega:** impossível salvar um pin que a pessoa nunca marcou

### Tarefas

**2.1 Rastrear a origem do pin na tela**

Novo estado nas duas telas: `pinSource: 'gps' | 'lookup' | 'manual' | null`.

- GPS capturado → `'gps'`
- Botão "Usar API" → `'lookup'`
- Pessoa tocou ou arrastou o pin → `'manual'` (sobrescreve qualquer um dos anteriores)

**2.2 Exigir origem para salvar**

`canSave` passa a exigir `pinSource !== null`, além do que já exige hoje.

**2.3 Neutralizar o `DEFAULT_PIN`**

Hoje `handleChooseOnMap` faz `setPin(current ?? DEFAULT_PIN)` com `DEFAULT_PIN` fixo no centro de Fortaleza — e isso vira uma coordenada salvável sem nenhuma interação.

Mudar para: abrir o mapa nesse ponto de referência **sem** considerá-lo escolhido. O pin só conta depois que a pessoa toca ou arrasta.

**2.4 Ajustar os textos**

A tela hoje não diz para que serve o pin. Deixar explícito que aquele ponto define quais profissionais são notificados num raio de 300 metros — a pessoa precisa saber o padrão de precisão que se espera dela.

### Pronto quando

Abrir o mapa e tocar em salvar sem interagir com ele é impossível. O botão fica desabilitado com indicação do motivo.

---

## Fase 3 — Enviar origem e precisão para a API

**Depende de:** back Fase 1 (campos novos no contrato)
**Entrega:** a API passa a saber a qualidade do que recebeu

### Tarefas

**3.1 Tipos**

Em `src/types/address.ts`, acrescentar aos DTOs de create/update e à leitura:

- `coordinateSource`
- `coordinateAccuracyMeters`
- `coordinateConfirmedAt`

**3.2 Camada de API**

Em `src/lib/api/addresses.ts`: repassar os campos novos no `create` e no `update`, e ler de volta em `mapAddress`.

**3.3 Telas**

Passar o `pinSource` da Fase 2 e a acurácia (quando a origem for GPS) no payload de salvar.

### Pronto quando

Um endereço criado pelo GPS chega no banco com origem e precisão preenchidas; um criado pelo pin manual chega com origem `user_pin` e precisão nula.

---

## Fase 4 — Preencher o endereço ao mover o pin

**Depende de:** back Fase 4 (endpoint de reverse geocoding)
**Entrega:** pin e endereço escrito param de divergir em silêncio

### Tarefas

**4.1 Aproveitar o que já vem e é descartado**

O `lookup` já devolve `normalizedAddress` (rua, bairro, cidade, CEP normalizados) e `confidence`. A camada de API mapeia os dois em `mapGeocodedAddress`, mas **as telas ignoram** — só usam `lat`, `lng` e `displayName`.

Passar a preencher os campos vazios do formulário com o `normalizedAddress`.

**4.2 Reverse geocoding ao soltar o pin**

Chamar o endpoint novo com debounce quando a pessoa terminar de mover o pin, e preencher rua e bairro.

**Regra importante:** nunca sobrescrever campo que a pessoa digitou. Só preencher o que está vazio. Se o resultado divergir do que ela escreveu, mostrar um aviso — não corrigir por conta própria.

**4.3 Mostrar a confiança da sugestão**

Quando a origem for `lookup`, indicar se o resultado é preciso ou aproximado, usando o `confidence` que já vem na resposta.

### Pronto quando

Arrastar o pin preenche rua e bairro vazios, e divergências entre o digitado e o marcado geram aviso visível.

---

## Fase 5 — Reconfirmação de endereços antigos

**Depende de:** back Fase 2 (marcação dos registros existentes)
**Entrega:** o problema antigo não sobrevive à correção

### Tarefas

**5.1 Sinalizar na listagem**

Em `src/app/(client)/(profile)/addresses/index.tsx`, marcar visualmente os endereços com coordenada não confiável, com ação direta para revisar.

**5.2 Pedir confirmação no fluxo Express**

Em `src/app/(client)/(express)/create.tsx`, o aviso hoje aparece só quando não há coordenada. Estender para coordenada não confiável, com o mesmo tratamento: bloqueia e leva para a tela de edição.

### Pronto quando

Um endereço cadastrado antes desta mudança não consegue abrir pedido Express sem passar por uma confirmação de pin.

---

## Ordem sugerida

| Ordem | Fase | Bloqueio |
|---|---|---|
| 1 | Front Fase 1 — GPS | nenhum |
| 2 | Front Fase 2 — alçapão | nenhum |
| 3 | Front Fase 3 — origem e precisão | espera back Fase 1 |
| 4 | Front Fase 5 — reconfirmação | espera back Fase 2 |
| 5 | Front Fase 4 — reverse geocoding | espera back Fase 4 |

As duas primeiras podem começar hoje, em paralelo com qualquer coisa do back.

---

## Observação fora do plano

Os tiles do mapa vêm do servidor público do OpenStreetMap (`tile.openstreetmap.org`), que tem política de uso restritiva para aplicações. Serve para a apresentação; para produção real precisaria de um fornecedor de tiles. Não bloqueia nada agora, mas vale registrar como dívida conhecida.
