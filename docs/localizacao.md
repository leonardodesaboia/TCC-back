# O que muda na localização dos endereços

*Documento para leitura do grupo. Sem detalhe técnico — a ideia é todo mundo entender o problema e concordar (ou não) com a solução antes de mexer no código.*

> **Status: implementado em 23/08/2026, nos dois repositórios.**
> Este documento continua valendo como explicação do problema e da decisão. Duas coisas que só apareceram na hora de codar:
>
> **1. A culpa não era só do serviço externo.** Descobrimos que a nossa API montava o endereço da consulta de um jeito errado antes de enviar — os espaços entre as palavras iam codificados duas vezes. O serviço bom (OpenStreetMap) recebia algo ilegível e respondia "não achei" para *todo* endereço. A API então caía no serviço ruim, que devolvia o centro da cidade. Ou seja: a fonte boa estava lá o tempo todo, a gente é que nunca conseguiu falar com ela direito.
>
> **Depois da correção, os mesmos 20 endereços de Fortaleza resolveram todos os 20** — 8 deles com precisão de prédio, o resto com precisão de rua. Nenhum caiu no centro da cidade.
>
> **2. Nada disso muda a decisão principal.** O pin confirmado pela pessoa continua sendo o que vale. A sugestão automática ficou boa, mas continua sendo sugestão — o ponto do atendimento é o portão do prédio, e isso nenhum mapa sabe.
>
> Como o fluxo ficou na prática, caso a caso: **`fluxo-localizacao.md`**.



---

## O problema em uma frase

Quando o cliente cadastra um endereço, o sistema precisa saber **exatamente** onde aquilo fica no mapa — porque o Express avisa só os profissionais num raio de 300 metros. Hoje, esse "exatamente onde" não é confiável.

## O que a gente descobriu

Testamos 20 endereços reais de Fortaleza contra a nossa API. Em **16 deles, a resposta foi o mesmo ponto**: o centro da cidade.

Endereços na Aldeota, no Cocó, em Mondubim, na Barra do Ceará — todos vieram com a mesma coordenada. Os erros variaram de 2 a 11 quilômetros.

Com um raio de 300 metros, um erro de 5 quilômetros significa uma de duas coisas: ou não encontra ninguém, ou avisa profissionais do bairro errado.

A causa é o serviço externo que a API consulta para converter endereço em coordenada. Ele responde "não sei exatamente, toma o centro de Fortaleza" — e a nossa API aceita essa resposta como se fosse precisa.

## Por que ninguém percebeu isso ainda

**Porque o app não usa essa coordenada.**

A tela de cadastro de endereço obriga a pessoa a marcar o ponto no mapa antes de salvar. É esse ponto marcado que vai para o banco — a resposta da API é só uma sugestão que aparece no mapa, e a pessoa ajusta por cima.

Ou seja: **o app está cobrindo um problema da API.** Funciona hoje. Mas funciona porque a tela foi feita assim, não porque alguma regra garante isso. Se alguém mexer nessa tela sem saber dessa dependência, o erro de quilômetros volta — e ninguém vai ser avisado. Nem o usuário, nem o log, nem nós.

## O que a gente propõe mudar

São quatro coisas. Nenhuma delas é reescrever o que já existe.

### 1. Usar o GPS do celular

Hoje, quem cadastra "Casa" estando em casa precisa arrastar o mapa desde o centro de Fortaleza até achar a própria rua. É trabalhoso, e quem tem pressa marca "mais ou menos ali".

Se o app perguntar *"quer usar sua localização atual?"*, o pin já nasce no lugar certo. O celular acerta com 5 a 20 metros de margem — melhor que qualquer serviço de mapa que a gente possa consultar.

A pessoa continua podendo ajustar. Só que aí ajustar vira conferir, em vez de procurar.

> A biblioteca de GPS já está instalada no projeto e já é usada do lado do profissional. É reaproveitar o que existe.

### 2. Guardar de onde veio a coordenada

Hoje a API recebe só dois números — latitude e longitude — e não tem como saber se aquilo é:

- um pin marcado com cuidado em cima da casa,
- o centro de Fortaleza, porque a pessoa abriu o mapa e salvou sem mover,
- ou a sugestão da API aceita sem ninguém conferir.

Os três chegam iguais.

Se a gente guardar **a origem** (GPS, pin manual ou sugestão), o Express passa a poder recusar um ponto ruim *antes* de criar o pedido, em vez de criar um pedido que não encontra ninguém.

> Isso também já existe do lado do profissional, que envia a origem e a precisão da localização. É copiar o mesmo modelo para o endereço do cliente.

### 3. Tirar o alçapão do centro da cidade

Hoje, se a pessoa toca em "Escolher no mapa" e salva sem mover nada, ela grava o centro de Fortaleza — sem perceber que fez isso.

É exatamente o mesmo erro que a API comete, só que agora com a assinatura do usuário em cima. Precisa sair: se a pessoa não marcou um ponto de verdade, o botão de salvar não deveria liberar.

### 4. A API passar a exigir o que o app já entrega

Hoje a regra "endereço precisa de pin" mora dentro de uma condição na tela. Deveria morar na API.

Assim a regra vale para qualquer tela nova, qualquer refatoração e qualquer integração futura — em vez de depender de todo mundo lembrar dela.

## O que isso custa

Sendo honesto sobre o outro lado:

- **Um passo a mais no cadastro.** Confirmar o pin é fricção real — uma vez por endereço, mas existe.
- **Mais uma permissão para pedir.** Localização é permissão sensível; pedir na hora errada aumenta a chance de recusa.
- **Mudança no banco e no contrato da API.** Campos novos, migração, ajuste nos dois repositórios de forma coordenada.
- **Endereços já cadastrados precisam ser reconfirmados**, o que aparece para quem já tinha cadastro pronto.

## O que isso não resolve

- **Cadastrar endereço de outra pessoa.** "Casa da minha mãe" — o cliente não está lá, o GPS não ajuda. Continua no pin manual.
- **Permissão negada.** Tem gente que vai recusar, e o caminho alternativo precisa funcionar bem de verdade.
- **A pessoa ainda pode marcar errado.** A gente troca "erro sistemático da API" por "erro humano pontual". É melhor, não é infalível.

## Resumo

O trabalho pesado já está feito — o app já tem mapa, pin arrastável e obrigatoriedade de marcar o ponto.

O que falta é: **um botão de GPS no app, dois campos a mais no contrato da API, uma verificação no Express e um valor padrão ruim a remover.**

Se o grupo concordar com o diagnóstico, os planos de execução estão em:

- `plano-implementacao-front-localizacao.md`
- `plano-implementacao-back-localizacao.md`

---

## Como chegamos nesses números

A API foi testada rodando em Docker, com 20 endereços reais de Fortaleza espalhados por bairros diferentes. Cada resultado foi comparado com duas fontes independentes para confirmar o erro.

O front foi analisado na branch `main`, que é a mais atual — nenhuma outra branch tem commit à frente dela.

O que **não** foi verificado: ninguém rodou o app para conferir na prática, e não temos medida de quanto o passo extra de confirmação custa em desistência de cadastro. Isso só sai com uso real.
